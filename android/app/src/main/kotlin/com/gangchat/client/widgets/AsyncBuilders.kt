package com.gangchat.client.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import com.gangchat.client.foundation.Stream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred

/** Flutter's `ConnectionState`. */
enum class ConnectionState { none, waiting, active, done }

/** Flutter's `AsyncSnapshot<T>`. */
class AsyncSnapshot<T> private constructor(
    val connectionState: ConnectionState,
    val data: T?,
    val error: Throwable?,
) {
    val hasData: Boolean get() = data != null
    val hasError: Boolean get() = error != null

    /** Flutter's `inState`: keeps the data and error, changes the state. */
    fun inState(state: ConnectionState): AsyncSnapshot<T> = AsyncSnapshot(state, data, error)

    companion object {
        fun <T> nothing(): AsyncSnapshot<T> = AsyncSnapshot(ConnectionState.none, null, null)

        fun <T> waiting(): AsyncSnapshot<T> = AsyncSnapshot(ConnectionState.waiting, null, null)

        fun <T> withData(state: ConnectionState, data: T?): AsyncSnapshot<T> = AsyncSnapshot(state, data, null)

        fun <T> withError(state: ConnectionState, error: Throwable): AsyncSnapshot<T> = AsyncSnapshot(state, null, error)
    }
}

private class _AsyncBuilderHolder<T>(var source: Any?, var snapshot: AsyncSnapshot<T>) {
    val tick = mutableIntStateOf(0)

    fun publish(next: AsyncSnapshot<T>) {
        snapshot = next
        tick.intValue += 1
    }
}

private val _unset = Any()

/**
 * Flutter's `FutureBuilder`: builds with the latest snapshot of [future].
 * Like Flutter, the result is always delivered after the current frame (a
 * completed future still shows one `waiting` frame), and switching to a new
 * future keeps the previous data/error in a `waiting` snapshot until it
 * completes.
 */
@Composable
fun <T> FutureBuilder(
    future: Deferred<T>?,
    initialData: T? = null,
    builder: @Composable (AsyncSnapshot<T>) -> Unit,
) {
    val holder = remember { _AsyncBuilderHolder<T>(_unset, AsyncSnapshot.withData(ConnectionState.none, initialData)) }
    if (holder.source !== future) {
        val state = if (future == null) ConnectionState.none else ConnectionState.waiting
        holder.snapshot = holder.snapshot.inState(state)
        holder.source = future
    }
    LaunchedEffect(future) {
        if (future == null) return@LaunchedEffect
        val next: AsyncSnapshot<T> =
            try {
                AsyncSnapshot.withData(ConnectionState.done, future.await())
            } catch (error: Throwable) {
                // The effect itself being cancelled (a new future, or leaving
                // the composition) must not publish anything.
                if (error is CancellationException && !future.isCancelled) throw error
                AsyncSnapshot.withError(ConnectionState.done, error)
            }
        if (holder.source === future) holder.publish(next)
    }
    holder.tick.intValue
    builder(holder.snapshot)
}

/** Flutter's `StreamBuilder` over a ported [Stream]. */
@Composable
fun <T> StreamBuilder(
    stream: Stream<T>?,
    initialData: T? = null,
    builder: @Composable (AsyncSnapshot<T>) -> Unit,
) {
    val holder = remember { _AsyncBuilderHolder<T>(_unset, AsyncSnapshot.withData(ConnectionState.none, initialData)) }
    if (holder.source !== stream) {
        val state = if (stream == null) ConnectionState.none else ConnectionState.waiting
        holder.snapshot = holder.snapshot.inState(state)
        holder.source = stream
    }
    DisposableEffect(stream) {
        val subscription =
            stream?.listen(
                onData = { value -> if (holder.source === stream) holder.publish(AsyncSnapshot.withData(ConnectionState.active, value)) },
                onError = { error -> if (holder.source === stream) holder.publish(AsyncSnapshot.withError(ConnectionState.active, error)) },
                onDone = { if (holder.source === stream) holder.publish(holder.snapshot.inState(ConnectionState.done)) },
            )
        onDispose { subscription?.cancel() }
    }
    holder.tick.intValue
    builder(holder.snapshot)
}
