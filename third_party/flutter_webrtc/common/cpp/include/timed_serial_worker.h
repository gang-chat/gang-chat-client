#ifndef FLUTTER_WEBRTC_TIMED_SERIAL_WORKER_H_
#define FLUTTER_WEBRTC_TIMED_SERIAL_WORKER_H_

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstddef>
#include <deque>
#include <functional>
#include <memory>
#include <mutex>
#include <thread>
#include <utility>
#include <vector>

namespace flutter_webrtc_plugin {

// Runs potentially blocking native device operations away from Flutter's
// platform thread. Operations are serialized because WebRTC's audio-device
// module is not safe to enumerate/select concurrently.
//
// A watchdog completes timed-out requests and poisons the worker so callers
// fail fast instead of growing an unbounded queue. Native APIs cannot always be
// cancelled safely; if one does not return during shutdown, its thread is
// detached with only shared operation state (never an owning plugin pointer).
class TimedSerialWorker {
 public:
  enum class FailureReason {
    kTimedOut,
    kStopped,
    kUnavailable,
    kFailed,
  };

  using Completion = std::function<void()>;
  using Work = std::function<Completion()>;
  using Failure = std::function<void(FailureReason)>;

  explicit TimedSerialWorker(
      std::chrono::milliseconds operation_timeout =
          std::chrono::milliseconds(5000),
      std::size_t max_queued_operations = 16,
      std::chrono::milliseconds shutdown_grace =
          std::chrono::milliseconds(500))
      : state_(std::make_shared<State>(operation_timeout,
                                       max_queued_operations)),
        shutdown_grace_(shutdown_grace),
        worker_([state = state_]() { WorkerLoop(state); }),
        watchdog_([state = state_]() { WatchdogLoop(state); }) {}

  ~TimedSerialWorker() { Stop(); }

  TimedSerialWorker(const TimedSerialWorker&) = delete;
  TimedSerialWorker& operator=(const TimedSerialWorker&) = delete;

  bool Submit(Work work, Failure failure) {
    auto operation = std::make_shared<Operation>(
        std::move(work), std::move(failure));
    Failure rejected;
    {
      std::lock_guard<std::mutex> lock(state_->mutex);
      if (state_->stopping || state_->poisoned ||
          state_->queue.size() >= state_->max_queued_operations) {
        if (Claim(operation)) {
          rejected = operation->failure;
        }
      } else {
        state_->queue.push_back(operation);
        state_->condition.notify_all();
        return true;
      }
    }
    InvokeFailure(rejected, FailureReason::kUnavailable);
    return false;
  }

  // Returns false only when native work ignored the shutdown grace period and
  // had to be abandoned. Callers that own a native runtime can then preserve
  // that runtime instead of tearing it down underneath the blocked call.
  bool Stop() {
    bool expected = false;
    if (!stop_started_.compare_exchange_strong(expected, true)) {
      while (!stop_finished_.load()) {
        std::this_thread::yield();
      }
      return stopped_cleanly_.load();
    }

    std::vector<Failure> stopped;
    {
      std::lock_guard<std::mutex> lock(state_->mutex);
      state_->stopping = true;
      if (state_->current && Claim(state_->current)) {
        stopped.push_back(state_->current->failure);
      }
      while (!state_->queue.empty()) {
        auto operation = state_->queue.front();
        state_->queue.pop_front();
        if (Claim(operation)) {
          stopped.push_back(operation->failure);
        }
      }
      state_->condition.notify_all();
    }
    for (const auto& failure : stopped) {
      InvokeFailure(failure, FailureReason::kStopped);
    }

    if (watchdog_.joinable()) {
      watchdog_.join();
    }

    bool worker_done = false;
    {
      std::unique_lock<std::mutex> lock(state_->mutex);
      worker_done = state_->condition.wait_for(
          lock, shutdown_grace_, [state = state_]() {
            return state->worker_done;
          });
    }
    if (worker_.joinable()) {
      if (worker_done) {
        worker_.join();
      } else {
        stopped_cleanly_.store(false);
        worker_.detach();
      }
    }
    stop_finished_.store(true);
    return stopped_cleanly_.load();
  }

 private:
  struct Operation {
    Operation(Work operation_work, Failure operation_failure)
        : work(std::move(operation_work)),
          failure(std::move(operation_failure)) {}

    Work work;
    Failure failure;
    std::atomic<bool> completed{false};
    std::chrono::steady_clock::time_point deadline;
  };

  struct State {
    State(std::chrono::milliseconds timeout, std::size_t max_queued)
        : operation_timeout(timeout),
          max_queued_operations(max_queued) {}

    std::mutex mutex;
    std::condition_variable condition;
    std::deque<std::shared_ptr<Operation>> queue;
    std::shared_ptr<Operation> current;
    const std::chrono::milliseconds operation_timeout;
    const std::size_t max_queued_operations;
    bool stopping = false;
    bool poisoned = false;
    bool worker_done = false;
    bool watchdog_done = false;
  };

  static bool Claim(const std::shared_ptr<Operation>& operation) {
    return operation && !operation->completed.exchange(true);
  }

  static void InvokeFailure(const Failure& failure, FailureReason reason) {
    if (!failure) {
      return;
    }
    try {
      failure(reason);
    } catch (...) {
      // A completion callback must never terminate the native worker.
    }
  }

  static void InvokeCompletion(const Completion& completion) {
    if (!completion) {
      return;
    }
    try {
      completion();
    } catch (...) {
      // A completion callback must never terminate the native worker.
    }
  }

  static void WorkerLoop(const std::shared_ptr<State>& state) {
    for (;;) {
      std::shared_ptr<Operation> operation;
      {
        std::unique_lock<std::mutex> lock(state->mutex);
        state->condition.wait(lock, [state]() {
          return state->stopping || state->poisoned ||
                 !state->queue.empty();
        });
        if (state->stopping || state->poisoned) {
          break;
        }
        operation = state->queue.front();
        state->queue.pop_front();
        operation->deadline =
            std::chrono::steady_clock::now() + state->operation_timeout;
        state->current = operation;
        state->condition.notify_all();
      }

      Completion completion;
      bool work_failed = false;
      try {
        completion = operation->work();
      } catch (...) {
        work_failed = true;
      }

      if (Claim(operation)) {
        if (work_failed) {
          InvokeFailure(operation->failure, FailureReason::kFailed);
        } else {
          InvokeCompletion(completion);
        }
      }

      {
        std::lock_guard<std::mutex> lock(state->mutex);
        if (state->current == operation) {
          state->current.reset();
        }
        state->condition.notify_all();
        if (state->poisoned || state->stopping) {
          break;
        }
      }
    }

    {
      std::lock_guard<std::mutex> lock(state->mutex);
      state->worker_done = true;
      state->condition.notify_all();
    }
  }

  static void WatchdogLoop(const std::shared_ptr<State>& state) {
    for (;;) {
      std::shared_ptr<Operation> operation;
      std::vector<Failure> rejected;
      {
        std::unique_lock<std::mutex> lock(state->mutex);
        state->condition.wait(lock, [state]() {
          return state->stopping || state->current != nullptr;
        });
        if (state->stopping) {
          break;
        }

        operation = state->current;
        const bool changed = state->condition.wait_until(
            lock, operation->deadline, [state, operation]() {
              return state->stopping || state->current != operation;
            });
        if (changed) {
          if (state->stopping) {
            break;
          }
          continue;
        }

        if (state->current != operation || !Claim(operation)) {
          continue;
        }

        state->poisoned = true;
        while (!state->queue.empty()) {
          auto queued = state->queue.front();
          state->queue.pop_front();
          if (Claim(queued)) {
            rejected.push_back(queued->failure);
          }
        }
        state->condition.notify_all();
      }

      InvokeFailure(operation->failure, FailureReason::kTimedOut);
      for (const auto& failure : rejected) {
        InvokeFailure(failure, FailureReason::kUnavailable);
      }
      break;
    }

    {
      std::lock_guard<std::mutex> lock(state->mutex);
      state->watchdog_done = true;
      state->condition.notify_all();
    }
  }

  const std::shared_ptr<State> state_;
  const std::chrono::milliseconds shutdown_grace_;
  std::atomic<bool> stop_started_{false};
  std::atomic<bool> stop_finished_{false};
  std::atomic<bool> stopped_cleanly_{true};
  std::thread worker_;
  std::thread watchdog_;
};

}  // namespace flutter_webrtc_plugin

#endif  // FLUTTER_WEBRTC_TIMED_SERIAL_WORKER_H_
