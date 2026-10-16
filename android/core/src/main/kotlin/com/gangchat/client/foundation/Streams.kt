package com.gangchat.client.foundation

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Dart's `StreamSubscription`. */
interface StreamSubscription<T> {
    /** Stops delivery. Returns immediately (Dart's `cancel()` future is moot). */
    fun cancel()
}

/**
 * Dart's `Stream<T>`, reduced to the listen-based API the app uses. Ports keep
 * `stream.listen(onData, onError = ..., onDone = ...)`; `await for` becomes
 * [forEach] (or `asFlow().collect`), `stream.first` becomes [first].
 */
interface Stream<T> {
    fun listen(
        onData: (T) -> Unit,
        onError: ((Throwable) -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        cancelOnError: Boolean = false,
    ): StreamSubscription<T>

    companion object {
        /** Dart's `Stream.empty()`. */
        fun <T> empty(): Stream<T> = EmptyStream()

        /** Dart's `Stream.fromIterable(values)`. */
        fun <T> fromIterable(values: Iterable<T>): Stream<T> {
            val controller = StreamController<T>()
            for (value in values) controller.add(value)
            controller.close()
            return controller.stream
        }
    }
}

/**
 * Dart's `StreamController<T>`.
 *
 * - [broadcast] controllers deliver each event to every current listener and
 *   drop events while nobody listens (Dart `StreamController.broadcast()`).
 * - Single-subscription controllers (the default constructor) buffer events
 *   until their one listener subscribes (Dart `StreamController()`).
 *
 * Delivery is asynchronous (posted to the event loop, in order) unless
 * [sync] is true, matching Dart. `close()` delivers `onDone` after any
 * pending events.
 */
class StreamController<T> private constructor(
    val isBroadcast: Boolean,
    private val sync: Boolean,
    private val onListen: (() -> Unit)?,
    private val onCancel: (() -> Unit)?,
) {
    constructor(sync: Boolean = false, onListen: (() -> Unit)? = null, onCancel: (() -> Unit)? = null) :
        this(false, sync, onListen, onCancel)

    private inner class Sub(
        val onData: (T) -> Unit,
        val onError: ((Throwable) -> Unit)?,
        val onDone: (() -> Unit)?,
        val cancelOnError: Boolean,
    ) : StreamSubscription<T> {
        var active = true

        override fun cancel() {
            if (!active) return
            active = false
            subscribers.remove(this)
            if (subscribers.isEmpty()) onCancel?.invoke()
        }
    }

    private sealed interface Event<out T> {
        class Data<T>(val value: T) : Event<T>

        class Error(val error: Throwable) : Event<Nothing>

        data object Done : Event<Nothing>
    }

    private val subscribers = mutableListOf<Sub>()
    private val pending = ArrayDeque<Event<T>>()
    private var hadListener = false
    private var closedFlag = false
    private var doneDelivered = false

    val isClosed: Boolean get() = closedFlag

    val hasListener: Boolean get() = subscribers.isNotEmpty()

    val stream: Stream<T> =
        object : Stream<T> {
            override fun listen(
                onData: (T) -> Unit,
                onError: ((Throwable) -> Unit)?,
                onDone: (() -> Unit)?,
                cancelOnError: Boolean,
            ): StreamSubscription<T> {
                if (!isBroadcast) {
                    check(!hadListener) { "Stream has already been listened to." }
                }
                hadListener = true
                val sub = Sub(onData, onError, onDone, cancelOnError)
                if (isBroadcast && doneDelivered) {
                    // Dart: listening to a closed broadcast stream only gets onDone.
                    sub.active = false
                    deliverLater { onDone?.invoke() }
                    return sub
                }
                subscribers.add(sub)
                if (subscribers.size == 1) onListen?.invoke()
                if (!isBroadcast && pending.isNotEmpty()) {
                    val buffered = pending.toList()
                    pending.clear()
                    for (event in buffered) dispatch(event)
                }
                return sub
            }
        }

    fun add(event: T) {
        check(!closedFlag) { "Cannot add event after closing" }
        enqueue(Event.Data(event))
    }

    fun addError(error: Throwable) {
        check(!closedFlag) { "Cannot add event after closing" }
        enqueue(Event.Error(error))
    }

    fun close() {
        if (closedFlag) return
        closedFlag = true
        enqueue(Event.Done)
    }

    private fun enqueue(event: Event<T>) {
        if (!isBroadcast && !hadListener) {
            pending.addLast(event)
            return
        }
        if (isBroadcast && subscribers.isEmpty()) {
            if (event is Event.Done) doneDelivered = true
            return
        }
        dispatch(event)
    }

    private fun dispatch(event: Event<T>) {
        val targets = subscribers.toList()
        if (event is Event.Done) doneDelivered = true
        val deliver = {
            for (sub in targets) {
                if (!sub.active) continue
                when (event) {
                    is Event.Data -> runGuarded { sub.onData(event.value) }
                    is Event.Error -> {
                        val handler = sub.onError
                        if (handler != null) {
                            runGuarded { handler(event.error) }
                        } else {
                            AppCoroutines.uncaughtErrorHandler(event.error)
                        }
                        if (sub.cancelOnError) sub.cancel()
                    }
                    Event.Done -> {
                        sub.active = false
                        subscribers.remove(sub)
                        runGuarded { sub.onDone?.invoke() }
                    }
                }
            }
        }
        if (sync) deliver() else deliverLater(deliver)
    }

    private fun deliverLater(block: () -> Unit) {
        AppCoroutines.scope.launch(AppCoroutines.loop) { block() }
    }

    private inline fun runGuarded(block: () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            AppCoroutines.uncaughtErrorHandler(error)
        }
    }

    companion object {
        /** Dart's `StreamController<T>.broadcast(sync: ...)`. */
        fun <T> broadcast(
            sync: Boolean = false,
            onListen: (() -> Unit)? = null,
            onCancel: (() -> Unit)? = null,
        ): StreamController<T> = StreamController(true, sync, onListen, onCancel)
    }
}

private class EmptyStream<T> : Stream<T> {
    override fun listen(
        onData: (T) -> Unit,
        onError: ((Throwable) -> Unit)?,
        onDone: (() -> Unit)?,
        cancelOnError: Boolean,
    ): StreamSubscription<T> {
        val sub = object : StreamSubscription<T> {
            var cancelled = false

            override fun cancel() {
                cancelled = true
            }
        }
        AppCoroutines.scope.launch(AppCoroutines.loop) { if (!sub.cancelled) onDone?.invoke() }
        return sub
    }
}

private class TransformedStream<S, T>(
    private val source: Stream<S>,
    private val transform: (S, (T) -> Unit) -> Unit,
) : Stream<T> {
    override fun listen(
        onData: (T) -> Unit,
        onError: ((Throwable) -> Unit)?,
        onDone: (() -> Unit)?,
        cancelOnError: Boolean,
    ): StreamSubscription<T> {
        val upstream = source.listen(
            { value -> transform(value, onData) },
            onError,
            onDone,
            cancelOnError,
        )
        return object : StreamSubscription<T> {
            override fun cancel() = upstream.cancel()
        }
    }
}

/** Dart's `stream.where(test)`. */
fun <T> Stream<T>.where(test: (T) -> Boolean): Stream<T> =
    TransformedStream(this) { value, emit -> if (test(value)) emit(value) }

/** Dart's `stream.map(convert)`. */
fun <S, T> Stream<S>.map(convert: (S) -> T): Stream<T> =
    TransformedStream(this) { value, emit -> emit(convert(value)) }

/** Dart's `stream.expand(convert)`. */
fun <S, T> Stream<S>.expand(convert: (S) -> Iterable<T>): Stream<T> =
    TransformedStream(this) { value, emit -> for (item in convert(value)) emit(item) }

/** Dart's `stream.whereType<R>()`. */
inline fun <reified R> Stream<*>.whereType(): Stream<R> =
    @Suppress("UNCHECKED_CAST")
    (this as Stream<Any?>).where { it is R }.map { it as R }

/** Dart's `await stream.firstWhere(test)`. */
suspend fun <T> Stream<T>.firstWhere(test: (T) -> Boolean): T =
    suspendCancellableCoroutine { continuation ->
        var subscription: StreamSubscription<T>? = null
        var done = false
        subscription = listen(
            onData = { value ->
                if (!done && test(value)) {
                    done = true
                    subscription?.cancel()
                    continuation.resume(value)
                }
            },
            onError = { error ->
                if (!done) {
                    done = true
                    subscription?.cancel()
                    continuation.resumeWithException(error)
                }
            },
            onDone = {
                if (!done) {
                    done = true
                    continuation.resumeWithException(IllegalStateException("No element"))
                }
            },
        )
        continuation.invokeOnCancellation { subscription.cancel() }
    }

/** Dart's `await stream.first`. */
suspend fun <T> Stream<T>.first(): T = firstWhere { true }

/** Bridges to a cold [Flow]; collecting it listens, cancelling cancels. */
fun <T> Stream<T>.asFlow(): Flow<T> =
    callbackFlow {
        val subscription = listen(
            onData = { trySend(it) },
            onError = { close(it) },
            onDone = { close() },
        )
        awaitClose { subscription.cancel() }
    }

/** Dart's `await for (final event in stream) { ... }`. */
suspend fun <T> Stream<T>.forEach(action: suspend (T) -> Unit) {
    asFlow().collect { action(it) }
}
