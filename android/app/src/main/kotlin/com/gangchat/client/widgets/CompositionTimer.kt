package com.gangchat.client.widgets

import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * A Dart `Timer` for widget code, running on a composition's coroutine scope
 * (`rememberCoroutineScope()`). Unlike `foundation.Timer`, it follows the
 * composition's clock, so Compose UI tests drive it with `mainClock` the way
 * `tester.pump(duration)` drives Flutter timers. It is cancelled when the
 * scope leaves the composition.
 */
class CompositionTimer internal constructor(private val job: Job) {
    val isActive: Boolean get() = job.isActive

    fun cancel() = job.cancel()
}

/** Starts a one-shot [CompositionTimer] (Dart's `Timer(duration, callback)`). */
fun CoroutineScope.startTimer(duration: Duration, callback: () -> Unit): CompositionTimer =
    CompositionTimer(
        launch(start = CoroutineStart.DEFAULT) {
            if (duration > Duration.ZERO) delay(duration) else yield()
            callback()
        },
    )
