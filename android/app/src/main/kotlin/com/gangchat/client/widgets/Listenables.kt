package com.gangchat.client.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import com.gangchat.client.foundation.Listenable
import com.gangchat.client.foundation.ValueListenable

/**
 * Subscribes the current composition scope to [listenable], so it recomposes
 * whenever the listenable notifies (the Compose side of Flutter's
 * `ListenableBuilder` / `addListener(() => setState(() {}))`).
 */
@Composable
fun <T : Listenable?> listen(listenable: T): T {
    val tick = remember { mutableIntStateOf(0) }
    DisposableEffect(listenable) {
        val listener: () -> Unit = { tick.intValue += 1 }
        listenable?.addListener(listener)
        onDispose { listenable?.removeListener(listener) }
    }
    tick.intValue
    return listenable
}

/** Flutter's `ListenableBuilder` / `AnimatedBuilder` over a listenable. */
@Composable
fun ListenableBuilder(listenable: Listenable, builder: @Composable () -> Unit) {
    listen(listenable)
    builder()
}

/** Flutter's `ValueListenableBuilder`. */
@Composable
fun <T> ValueListenableBuilder(valueListenable: ValueListenable<T>, builder: @Composable (T) -> Unit) {
    listen(valueListenable)
    builder(valueListenable.value)
}
