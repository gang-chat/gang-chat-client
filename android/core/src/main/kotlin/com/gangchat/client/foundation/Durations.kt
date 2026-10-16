package com.gangchat.client.foundation

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Dart's `Duration(days:, hours:, minutes:, seconds:, milliseconds:,
 * microseconds:)` constructor. Prefer `5.seconds` for a single unit.
 */
fun dartDuration(
    days: Long = 0,
    hours: Long = 0,
    minutes: Long = 0,
    seconds: Long = 0,
    milliseconds: Long = 0,
    microseconds: Long = 0,
): Duration =
    days.days + hours.hours + minutes.minutes + seconds.seconds +
        milliseconds.milliseconds + microseconds.microseconds

/** Dart's `Duration.toString()`: `H:MM:SS.ffffff`, e.g. `0:00:05.000000`. */
fun Duration.toDartString(): String {
    var micros = inWholeMicroseconds
    val sign = if (micros < 0) "-" else ""
    if (micros < 0) micros = -micros
    val hours = micros / 3_600_000_000L
    micros %= 3_600_000_000L
    val minutes = micros / 60_000_000L
    micros %= 60_000_000L
    val seconds = micros / 1_000_000L
    micros %= 1_000_000L
    return sign + hours + ":" + minutes.toString().padStart(2, '0') + ":" +
        seconds.toString().padStart(2, '0') + "." + micros.toString().padStart(6, '0')
}

/** Dart's `Stopwatch`. */
class Stopwatch {
    private var startedAtNanos: Long? = null
    private var accumulatedNanos = 0L

    val isRunning: Boolean get() = startedAtNanos != null

    fun start(): Stopwatch {
        if (startedAtNanos == null) startedAtNanos = clock()
        return this
    }

    fun stop() {
        val started = startedAtNanos ?: return
        accumulatedNanos += clock() - started
        startedAtNanos = null
    }

    fun reset() {
        accumulatedNanos = 0
        if (startedAtNanos != null) startedAtNanos = clock()
    }

    val elapsed: Duration
        get() {
            val running = startedAtNanos?.let { clock() - it } ?: 0L
            return (accumulatedNanos + running).nanoseconds
        }

    val elapsedMilliseconds: Long get() = elapsed.inWholeMilliseconds

    val elapsedMicroseconds: Long get() = elapsed.inWholeMicroseconds

    companion object {
        /** Overridable for tests that need a deterministic stopwatch. */
        @Volatile
        var clock: () -> Long = System::nanoTime
    }

    private fun clock(): Long = Stopwatch.clock()
}
