package com.gangchat.client.foundation

import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * The single-threaded event loop the ported code runs on, standing in for the
 * Dart isolate.
 *
 * All controllers, services and UI state live on the Android main thread, the
 * same way the Flutter app ran everything on one isolate. Work that blocks
 * (network, disk) suspends on an I/O dispatcher inside the platform layer and
 * resumes here, so ported code never needs locks.
 *
 * Tests call `Dispatchers.setMain(testDispatcher)` and [reset] (see the
 * `GangTestRule` test fixture) so timers and futures run on virtual time.
 */
object AppCoroutines {
    @Volatile
    private var installedScope: CoroutineScope? = null

    /** Reports errors thrown by fire-and-forget work, like Dart's zone handler. */
    @Volatile
    var uncaughtErrorHandler: (Throwable) -> Unit = { error ->
        debugPrint("Unhandled async error: $error\n${error.stackTraceToString()}")
    }

    /** Scope for `unawaited` work and futures; never cancelled in production. */
    val scope: CoroutineScope
        get() = installedScope ?: synchronized(this) {
            installedScope ?: createScope().also { installedScope = it }
        }

    /**
     * Dispatcher that always posts to the event loop instead of running inline,
     * matching Dart timers and asynchronous stream delivery.
     */
    val loop: CoroutineDispatcher
        get() = Dispatchers.Main

    /** Drops the cached scope so the next access binds to the current Main. */
    fun reset() {
        synchronized(this) { installedScope = null }
    }

    /** Installs an explicit scope (tests, or a host that owns the lifecycle). */
    fun install(scope: CoroutineScope) {
        synchronized(this) { installedScope = scope }
    }

    private fun createScope(): CoroutineScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Main.immediate +
                CoroutineExceptionHandler { _, error -> uncaughtErrorHandler(error) },
        )
}

/**
 * Dart's `unawaited(future)`: starts [block] now and runs it synchronously up
 * to its first suspension point, exactly like calling an `async` Dart function
 * without awaiting it. Errors go to [AppCoroutines.uncaughtErrorHandler].
 */
fun unawaited(block: suspend CoroutineScope.() -> Unit): Job =
    AppCoroutines.scope.launch(start = CoroutineStart.UNDISPATCHED, block = block)

/**
 * Dart's eager `Future`: starts [block] now (synchronously up to the first
 * suspension) and returns a handle that can be stored, shared and awaited by
 * several callers. Use it where the Dart code keeps a `Future` in a field or
 * map; plain `async` methods become `suspend fun`.
 */
fun <T> future(block: suspend CoroutineScope.() -> T): Deferred<T> =
    AppCoroutines.scope.async(start = CoroutineStart.UNDISPATCHED, block = block)

/** Dart's `Future.value(value)` / `SynchronousFuture(value)`. */
fun <T> completedFuture(value: T): Deferred<T> = CompletableDeferred(value)

/** Dart's `Future.error(error)`. */
fun <T> failedFuture(error: Throwable): Deferred<T> =
    CompletableDeferred<T>().apply { completeExceptionally(error) }

/** Dart's `Future.wait(futures)`. */
suspend fun <T> waitAll(futures: Collection<Deferred<T>>): List<T> = futures.awaitAll()

/** Dart's `Future<void>.delayed(duration)` (and `Duration.zero` yields once). */
suspend fun delayed(duration: Duration) {
    if (duration <= Duration.ZERO) {
        kotlinx.coroutines.yield()
    } else {
        delay(duration)
    }
}

/** Dart's `Completer<T>`. */
class Completer<T> {
    private val deferred = CompletableDeferred<T>()

    val future: Deferred<T> get() = deferred

    val isCompleted: Boolean get() = deferred.isCompleted

    fun complete(value: T) {
        check(deferred.complete(value)) { "Future already completed" }
    }

    fun completeError(error: Throwable) {
        check(deferred.completeExceptionally(error)) { "Future already completed" }
    }
}

/** `Completer<void>.complete()`. */
fun Completer<Unit>.complete() = complete(Unit)

/**
 * Dart's `TimeoutException`. `toString()` keeps Dart's wording because some
 * error messages surface it verbatim.
 */
class TimeoutException(
    override val message: String?,
    val duration: Duration? = null,
) : Exception(message) {
    override fun toString(): String {
        val buffer = StringBuilder("TimeoutException")
        if (duration != null) buffer.append(" after ").append(duration.toDartString())
        if (message != null) buffer.append(": ").append(message)
        return buffer.toString()
    }
}

/**
 * Dart's `future.timeout(duration)`: throws [TimeoutException] when [block]
 * does not finish in time.
 */
suspend fun <T> timeout(duration: Duration, block: suspend CoroutineScope.() -> T): T =
    try {
        withTimeout(duration, block)
    } catch (error: TimeoutCancellationException) {
        throw TimeoutException("Future not completed", duration)
    }

/** Dart's `future.timeout(duration, onTimeout: ...)`. */
suspend fun <T> timeout(
    duration: Duration,
    onTimeout: suspend () -> T,
    block: suspend CoroutineScope.() -> T,
): T =
    try {
        withTimeout(duration, block)
    } catch (error: TimeoutCancellationException) {
        onTimeout()
    }

/**
 * Dart's `Timer` / `Timer.periodic`. Callbacks always run on the event loop
 * after at least one turn, even for `Duration.ZERO`, like Dart timers.
 */
class Timer private constructor(
    duration: Duration,
    private val periodic: Boolean,
    callback: (Timer) -> Unit,
) {
    constructor(duration: Duration, callback: () -> Unit) : this(duration, false, { _ -> callback() })

    private var cancelled = false
    private var finished = false

    /** Number of times a periodic timer has fired (Dart `tick`). */
    var tick: Int = 0
        private set

    val isActive: Boolean get() = !cancelled && !finished

    private val job: Job =
        AppCoroutines.scope.launch(AppCoroutines.loop) {
            val step = if (duration < Duration.ZERO) Duration.ZERO else duration
            if (!periodic) {
                if (step > Duration.ZERO) delay(step) else kotlinx.coroutines.yield()
                if (cancelled) return@launch
                finished = true
                callback(this@Timer)
            } else {
                while (!cancelled) {
                    if (step > Duration.ZERO) delay(step) else kotlinx.coroutines.yield()
                    if (cancelled) break
                    tick += 1
                    callback(this@Timer)
                }
            }
        }

    fun cancel() {
        if (cancelled) return
        cancelled = true
        job.cancel()
    }

    companion object {
        fun periodic(duration: Duration, callback: (Timer) -> Unit): Timer =
            Timer(duration, true, callback)

        /** Dart's `Timer.run(callback)`. */
        fun run(callback: () -> Unit): Timer = Timer(Duration.ZERO, callback)
    }
}

/** Dart's `scheduleMicrotask(callback)`. */
fun scheduleMicrotask(callback: () -> Unit) {
    AppCoroutines.scope.launch(AppCoroutines.loop) { callback() }
}
