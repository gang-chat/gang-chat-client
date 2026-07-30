#if defined(_WIN32) && !defined(NOMINMAX)
#define NOMINMAX
#endif

#include "flutter_screen_capture.h"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <vector>

#if defined(_WIN32)
#include <windows.h>

#include <iostream>
#include <memory>
#endif

namespace flutter_webrtc_plugin {

namespace {

class DesktopFrameAdapter final
    : public RTCVideoRenderer<scoped_refptr<RTCVideoFrame>> {
 public:
  DesktopFrameAdapter(scoped_refptr<RTCDesktopCapturer> capturer,
                      scoped_refptr<RTCVideoTrack> input_track,
                      scoped_refptr<RTCVideoSource> output_source,
                      int target_width,
                      int target_height,
                      int max_frame_rate)
      : capturer_(std::move(capturer)),
        input_track_(std::move(input_track)),
        output_source_(std::move(output_source)),
        target_width_(std::max(2, target_width)),
        target_height_(std::max(2, target_height)),
        max_frame_rate_(std::max(1, max_frame_rate)) {}

  ~DesktopFrameAdapter() override { Stop(); }

  void Start() {
    scoped_refptr<RTCVideoTrack> input_track;
    {
      std::lock_guard<std::mutex> lock(mutex_);
      if (started_ || stopped_.load()) {
        return;
      }
      started_ = true;
      input_track = input_track_;
    }
    if (input_track != nullptr) {
      input_track->AddRenderer(this);
    }
  }

  void Stop() {
    if (stopped_.exchange(true)) {
      return;
    }

    scoped_refptr<RTCVideoTrack> input_track;
    scoped_refptr<RTCDesktopCapturer> capturer;
    bool was_started = false;
    {
      std::lock_guard<std::mutex> lock(mutex_);
      input_track = input_track_;
      capturer = capturer_;
      was_started = started_;
      started_ = false;
    }
    if (input_track != nullptr && was_started) {
      input_track->RemoveRenderer(this);
    }
    if (capturer != nullptr) {
      capturer->DeRegisterDesktopCapturerObserver();
      if (capturer->IsRunning()) {
        capturer->Stop();
      }
    }
    {
      std::lock_guard<std::mutex> lock(mutex_);
      output_source_ = nullptr;
      input_track_ = nullptr;
      capturer_ = nullptr;
    }
  }

  void OnFrame(scoped_refptr<RTCVideoFrame> frame) override {
    if (stopped_.load() || frame == nullptr) {
      return;
    }

    const auto now = std::chrono::steady_clock::now();
    const auto minimum_interval =
        std::chrono::microseconds(1000000 / max_frame_rate_);
    {
      std::lock_guard<std::mutex> lock(mutex_);
      if (stopped_.load() ||
          (last_frame_at_.time_since_epoch().count() != 0 &&
           now - last_frame_at_ < minimum_interval)) {
        return;
      }
      last_frame_at_ = now;
    }

    const int source_width = frame->width();
    const int source_height = frame->height();
    if (source_width <= 0 || source_height <= 0) {
      return;
    }

    const double scale = std::min(
        1.0, std::min(static_cast<double>(target_width_) / source_width,
                      static_cast<double>(target_height_) / source_height));
    int output_width =
        std::max(2, static_cast<int>(std::floor(source_width * scale)));
    int output_height =
        std::max(2, static_cast<int>(std::floor(source_height * scale)));
    output_width -= output_width % 2;
    output_height -= output_height % 2;

    scoped_refptr<RTCVideoSource> output_source;
    {
      std::lock_guard<std::mutex> lock(mutex_);
      if (stopped_.load()) {
        return;
      }
      output_source = output_source_;
    }
    if (output_source == nullptr) {
      return;
    }

    if (output_width == source_width && output_height == source_height) {
      output_source->OnCapturedFrame(frame);
      return;
    }

    const uint8_t* source_y = frame->DataY();
    const uint8_t* source_u = frame->DataU();
    const uint8_t* source_v = frame->DataV();
    if (source_y == nullptr || source_u == nullptr || source_v == nullptr) {
      return;
    }

    const int output_chroma_width = (output_width + 1) / 2;
    const int output_chroma_height = (output_height + 1) / 2;
    std::vector<uint8_t> output_y(output_width * output_height);
    std::vector<uint8_t> output_u(output_chroma_width * output_chroma_height);
    std::vector<uint8_t> output_v(output_chroma_width * output_chroma_height);
    ScalePlane(source_y, frame->StrideY(), source_width, source_height,
               output_y.data(), output_width, output_width, output_height);
    ScalePlane(source_u, frame->StrideU(), (source_width + 1) / 2,
               (source_height + 1) / 2, output_u.data(), output_chroma_width,
               output_chroma_width, output_chroma_height);
    ScalePlane(source_v, frame->StrideV(), (source_width + 1) / 2,
               (source_height + 1) / 2, output_v.data(), output_chroma_width,
               output_chroma_width, output_chroma_height);

    auto output_frame = RTCVideoFrame::Create(
        output_width, output_height, output_y.data(), output_width,
        output_u.data(), output_chroma_width, output_v.data(),
        output_chroma_width);
    if (output_frame != nullptr) {
      output_source->OnCapturedFrame(output_frame);
    }
  }

 private:
  static void ScalePlane(const uint8_t* source,
                         int source_stride,
                         int source_width,
                         int source_height,
                         uint8_t* destination,
                         int destination_stride,
                         int destination_width,
                         int destination_height) {
    if (source_width == destination_width &&
        source_height == destination_height) {
      for (int row = 0; row < source_height; ++row) {
        std::memcpy(destination + row * destination_stride,
                    source + row * source_stride, source_width);
      }
      return;
    }

    // Fixed-point bilinear scaling keeps desktop text readable while doing the
    // work once, before WebRTC creates simulcast layers. The target is never
    // larger than the source, so this cannot accidentally upscale capture.
    constexpr int kFractionBits = 14;
    constexpr int kFractionOne = 1 << kFractionBits;
    for (int destination_y = 0; destination_y < destination_height;
         ++destination_y) {
      const std::int64_t source_y_fixed =
          static_cast<std::int64_t>(destination_y) * (source_height - 1) *
          kFractionOne / std::max(1, destination_height - 1);
      const int source_y =
          static_cast<int>(source_y_fixed >> kFractionBits);
      const int next_source_y = std::min(source_height - 1, source_y + 1);
      const int y_fraction =
          static_cast<int>(source_y_fixed & (kFractionOne - 1));
      const uint8_t* top = source + source_y * source_stride;
      const uint8_t* bottom = source + next_source_y * source_stride;
      uint8_t* output = destination + destination_y * destination_stride;
      for (int destination_x = 0; destination_x < destination_width;
           ++destination_x) {
        const std::int64_t source_x_fixed =
            static_cast<std::int64_t>(destination_x) * (source_width - 1) *
            kFractionOne / std::max(1, destination_width - 1);
        const int source_x =
            static_cast<int>(source_x_fixed >> kFractionBits);
        const int next_source_x = std::min(source_width - 1, source_x + 1);
        const int x_fraction =
            static_cast<int>(source_x_fixed & (kFractionOne - 1));
        const int top_value =
            top[source_x] +
            ((top[next_source_x] - top[source_x]) * x_fraction >>
             kFractionBits);
        const int bottom_value =
            bottom[source_x] +
            ((bottom[next_source_x] - bottom[source_x]) * x_fraction >>
             kFractionBits);
        output[destination_x] = static_cast<uint8_t>(
            top_value +
            ((bottom_value - top_value) * y_fraction >> kFractionBits));
      }
    }
  }

  std::mutex mutex_;
  std::atomic<bool> stopped_{false};
  bool started_ = false;
  scoped_refptr<RTCDesktopCapturer> capturer_;
  scoped_refptr<RTCVideoTrack> input_track_;
  scoped_refptr<RTCVideoSource> output_source_;
  const int target_width_;
  const int target_height_;
  const int max_frame_rate_;
  std::chrono::steady_clock::time_point last_frame_at_;
};

bool WantsScreenAudio(const EncodableMap& constraints) {
  auto it = constraints.find(EncodableValue("audio"));
  if (it == constraints.end()) {
    return false;
  }
  if (TypeIs<bool>(it->second)) {
    return GetValue<bool>(it->second);
  }
  return TypeIs<EncodableMap>(it->second);
}

#if defined(_WIN32)
bool ResolveWindowProcessId(const std::string& source_id,
                            DWORD* process_id) {
  if (process_id == nullptr || source_id.empty()) {
    return false;
  }

  try {
    size_t parsed = 0;
    std::uint64_t value = std::stoull(source_id, &parsed, 0);
    if (parsed != source_id.size() || value == 0) {
      return false;
    }
    HWND window = reinterpret_cast<HWND>(static_cast<std::uintptr_t>(value));
    if (!IsWindow(window)) {
      return false;
    }
    DWORD window_process_id = 0;
    GetWindowThreadProcessId(window, &window_process_id);
    if (window_process_id == 0) {
      return false;
    }
    *process_id = window_process_id;
    return true;
  } catch (...) {
    return false;
  }
}
#endif

}  // namespace

FlutterScreenCapture::FlutterScreenCapture(FlutterWebRTCBase* base)
    : base_(base) {}

bool FlutterScreenCapture::BuildDesktopSourcesList(const EncodableList& types,
                                                   bool force_reload) {
  size_t size = types.size();
  sources_.clear();
  for (size_t i = 0; i < size; i++) {
    std::string type_str = GetValue<std::string>(types[i]);
    DesktopType desktop_type = DesktopType::kScreen;
    if (type_str == "screen") {
      desktop_type = DesktopType::kScreen;
    } else if (type_str == "window") {
      desktop_type = DesktopType::kWindow;
    } else {
      // std::cout << "Unknown type " << type_str << std::endl;
      return false;
    }
    scoped_refptr<RTCDesktopMediaList> source_list;
    auto it = medialist_.find(desktop_type);
    if (it != medialist_.end()) {
      source_list = (*it).second;
    } else {
      source_list = base_->desktop_device_->GetDesktopMediaList(desktop_type);
      source_list->RegisterMediaListObserver(this);
      medialist_[desktop_type] = source_list;
    }
    source_list->UpdateSourceList(force_reload);
    int count = source_list->GetSourceCount();
    for (int j = 0; j < count; j++) {
      sources_.push_back(source_list->GetSource(j));
    }
  }
  return true;
}

void FlutterScreenCapture::GetDesktopSources(
    const EncodableList& types,
    std::unique_ptr<MethodResultProxy> result) {
  if (!BuildDesktopSourcesList(types, true)) {
    result->Error("Bad Arguments", "Failed to get desktop sources");
    return;
  }

  EncodableList sources;
  for (auto source : sources_) {
    EncodableMap info;
    info[EncodableValue("id")] = EncodableValue(source->id().std_string());
    info[EncodableValue("name")] = EncodableValue(source->name().std_string());
    info[EncodableValue("type")] =
        EncodableValue(source->type() == kWindow ? "window" : "screen");
    // TODO "thumbnailSize"
    info[EncodableValue("thumbnailSize")] = EncodableMap{
        {EncodableValue("width"), EncodableValue(0)},
        {EncodableValue("height"), EncodableValue(0)},
    };
    sources.push_back(EncodableValue(info));
  }

  std::cout << " sources: " << sources.size() << std::endl;
  auto map = EncodableMap();
  map[EncodableValue("sources")] = sources;
  result->Success(EncodableValue(map));
}

void FlutterScreenCapture::UpdateDesktopSources(
    const EncodableList& types,
    std::unique_ptr<MethodResultProxy> result) {
  if (!BuildDesktopSourcesList(types, false)) {
    result->Error("Bad Arguments", "Failed to update desktop sources");
    return;
  }
  auto map = EncodableMap();
  map[EncodableValue("result")] = true;
  result->Success(EncodableValue(map));
}

void FlutterScreenCapture::OnMediaSourceAdded(
    scoped_refptr<MediaSource> source) {
  std::cout << " OnMediaSourceAdded: " << source->id().std_string()
            << std::endl;

  EncodableMap info;
  info[EncodableValue("event")] = "desktopSourceAdded";
  info[EncodableValue("id")] = EncodableValue(source->id().std_string());
  info[EncodableValue("name")] = EncodableValue(source->name().std_string());
  info[EncodableValue("type")] =
      EncodableValue(source->type() == kWindow ? "window" : "screen");
  // TODO "thumbnailSize"
  info[EncodableValue("thumbnailSize")] = EncodableMap{
      {EncodableValue("width"), EncodableValue(0)},
      {EncodableValue("height"), EncodableValue(0)},
  };
  base_->event_channel()->Success(EncodableValue(info));
}

void FlutterScreenCapture::OnMediaSourceRemoved(
    scoped_refptr<MediaSource> source) {
  std::cout << " OnMediaSourceRemoved: " << source->id().std_string()
            << std::endl;

  EncodableMap info;
  info[EncodableValue("event")] = "desktopSourceRemoved";
  info[EncodableValue("id")] = EncodableValue(source->id().std_string());
  base_->event_channel()->Success(EncodableValue(info));
}

void FlutterScreenCapture::OnMediaSourceNameChanged(
    scoped_refptr<MediaSource> source) {
  std::cout << " OnMediaSourceNameChanged: " << source->id().std_string()
            << std::endl;

  EncodableMap info;
  info[EncodableValue("event")] = "desktopSourceNameChanged";
  info[EncodableValue("id")] = EncodableValue(source->id().std_string());
  info[EncodableValue("name")] = EncodableValue(source->name().std_string());
  base_->event_channel()->Success(EncodableValue(info));
}

void FlutterScreenCapture::OnMediaSourceThumbnailChanged(
    scoped_refptr<MediaSource> source) {
  std::cout << " OnMediaSourceThumbnailChanged: " << source->id().std_string()
            << std::endl;

  EncodableMap info;
  info[EncodableValue("event")] = "desktopSourceThumbnailChanged";
  info[EncodableValue("id")] = EncodableValue(source->id().std_string());
  info[EncodableValue("thumbnail")] =
      EncodableValue(source->thumbnail().std_vector());
  base_->event_channel()->Success(EncodableValue(info));
}

void FlutterScreenCapture::OnStart(scoped_refptr<RTCDesktopCapturer> capturer) {
  // std::cout << " OnStart: " << capturer->source()->id().std_string()
  //          << std::endl;
}

void FlutterScreenCapture::OnPaused(
    scoped_refptr<RTCDesktopCapturer> capturer) {
  // std::cout << " OnPaused: " << capturer->source()->id().std_string()
  //          << std::endl;
}

void FlutterScreenCapture::OnStop(scoped_refptr<RTCDesktopCapturer> capturer) {
  // std::cout << " OnStop: " << capturer->source()->id().std_string()
  //          << std::endl;
}

void FlutterScreenCapture::OnError(scoped_refptr<RTCDesktopCapturer> capturer) {
  // std::cout << " OnError: " << capturer->source()->id().std_string()
  //          << std::endl;
}

void FlutterScreenCapture::GetDesktopSourceThumbnail(
    std::string source_id,
    int width,
    int height,
    std::unique_ptr<MethodResultProxy> result) {
  scoped_refptr<MediaSource> source;
  for (auto src : sources_) {
    if (src->id().std_string() == source_id) {
      source = src;
    }
  }
  if (source.get() == nullptr) {
    result->Error("Bad Arguments", "Failed to get desktop source thumbnail");
    return;
  }
  std::cout << " GetDesktopSourceThumbnail: " << source->id().std_string()
            << std::endl;
  source->UpdateThumbnail();
  result->Success(EncodableValue(source->thumbnail().std_vector()));
}

void FlutterScreenCapture::GetDisplayMedia(
    const EncodableMap& constraints,
    std::unique_ptr<MethodResultProxy> result) {
  std::string source_id = "0";
  // DesktopType source_type = kScreen;
  double fps = 30.0;

  const EncodableMap video = findMap(constraints, "video");
  if (video != EncodableMap()) {
    const EncodableMap deviceId = findMap(video, "deviceId");
    if (deviceId != EncodableMap()) {
      source_id = findString(deviceId, "exact");
      if (source_id.empty()) {
        result->Error("Bad Arguments", "Incorrect video->deviceId->exact");
        return;
      }
      if (source_id != "0") {
        // source_type = DesktopType::kWindow;
      }
    }
    const EncodableMap mandatory = findMap(video, "mandatory");
    if (mandatory != EncodableMap()) {
      double frameRate = findDouble(mandatory, "frameRate");
      if (frameRate != 0.0) {
        fps = frameRate;
      }
    }
  }

  std::string uuid = base_->GenerateUUID();

  scoped_refptr<RTCMediaStream> stream =
      base_->factory_->CreateStream(uuid.c_str());

  EncodableMap params;
  params[EncodableValue("streamId")] = EncodableValue(uuid);

  // VIDEO

  EncodableMap video_constraints;
  auto it = constraints.find(EncodableValue("video"));
  if (it != constraints.end() && TypeIs<EncodableMap>(it->second)) {
    video_constraints = GetValue<EncodableMap>(it->second);
  }

  scoped_refptr<MediaSource> source;
  for (auto src : sources_) {
    if (src->id().std_string() == source_id) {
      source = src;
    }
  }

  if (!source.get()) {
    result->Error("Bad Arguments", "source not found!");
    return;
  }

  scoped_refptr<RTCDesktopCapturer> desktop_capturer =
      base_->desktop_device_->CreateDesktopCapturer(source);

  if (!desktop_capturer.get()) {
    result->Error("Bad Arguments", "CreateDesktopCapturer failed!");
    return;
  }

  desktop_capturer->RegisterDesktopCapturerObserver(this);

  const char* video_source_label = "screen_capture_input";

  scoped_refptr<RTCVideoSource> desktop_video_source =
      base_->factory_->CreateDesktopSource(
          desktop_capturer, video_source_label,
          base_->ParseMediaConstraints(video_constraints));

  scoped_refptr<RTCVideoTrack> desktop_track =
      base_->factory_->CreateVideoTrack(desktop_video_source, uuid.c_str());
  const int target_width =
      std::max(2, toInt(findEncodableValue(video_constraints, "width"), 1920));
  const int target_height =
      std::max(2, toInt(findEncodableValue(video_constraints, "height"), 1080));
  scoped_refptr<RTCVideoSource> video_source =
      base_->factory_->CreateCustomVideoSource(
          "screen_capture_output",
          base_->ParseMediaConstraints(video_constraints));
  scoped_refptr<RTCVideoTrack> track =
      base_->factory_->CreateVideoTrack(video_source, uuid.c_str());
  if (desktop_track == nullptr || video_source == nullptr || track == nullptr) {
    desktop_capturer->DeRegisterDesktopCapturerObserver();
    result->Error("GetDisplayMediaFailed",
                  "Create scaled desktop capture track failed");
    return;
  }
  auto frame_adapter = std::make_shared<DesktopFrameAdapter>(
      desktop_capturer, desktop_track, video_source, target_width,
      target_height, static_cast<int>(fps));
  frame_adapter->Start();

  EncodableList videoTracks;
  EncodableMap info;
  info[EncodableValue("id")] = EncodableValue(track->id().std_string());
  info[EncodableValue("label")] = EncodableValue(track->id().std_string());
  info[EncodableValue("kind")] = EncodableValue(track->kind().std_string());
  info[EncodableValue("enabled")] = EncodableValue(track->enabled());
  videoTracks.push_back(EncodableValue(info));
  params[EncodableValue("videoTracks")] = EncodableValue(videoTracks);

  // AUDIO

#if defined(_WIN32)
  if (WantsScreenAudio(constraints)) {
    const DWORD current_process_id = GetCurrentProcessId();
    DWORD target_process_id = current_process_id;
    bool include_process_tree = false;
    bool allow_system_loopback_fallback = false;
    DWORD window_process_id = 0;
    if (source->type() == kWindow &&
        ResolveWindowProcessId(source->id().std_string(),
                               &window_process_id) &&
        window_process_id != current_process_id) {
      target_process_id = window_process_id;
      include_process_tree = true;
    }
    // Classic system loopback captures GangChat's own playout as well, which
    // feeds call audio back into the screen-share track. Require process
    // loopback so the current process can be excluded for full-screen sharing
    // and only the target process can be included for window sharing.
    base_->ConfigureScreenAudioCapture(true, target_process_id,
                                       include_process_tree,
                                       allow_system_loopback_fallback);
  } else {
    base_->ConfigureScreenAudioCapture(false, 0, false, false);
  }
#endif

  EncodableList audioTracks;
  params[EncodableValue("audioTracks")] = EncodableValue(audioTracks);

  stream->AddTrack(track);

  base_->local_tracks_[track->id().std_string()] = track;

  base_->local_streams_[uuid] = stream;

  base_->RegisterLocalTrackCleanup(
      track->id().std_string(),
      [frame_adapter]() { frame_adapter->Stop(); });
  desktop_capturer->Start(uint32_t(fps));

  result->Success(EncodableValue(params));
}

}  // namespace flutter_webrtc_plugin
