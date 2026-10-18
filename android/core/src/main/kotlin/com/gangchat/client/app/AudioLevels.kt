package com.gangchat.client.app

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sqrt

fun normalizedAudioVolume(volume: Double): Double {
    return _clamp(volume, 0.0, 1.0)
}

fun audioVolumePercentText(volume: Double): String {
    return "${(normalizedAudioVolume(volume) * 100).roundToLong()}%"
}

/** The 增强 switch on the input and output volume multiplies the gain by this. */
const val audioVolumeBoostFactor = 1.5

/** The gain for a 0..1 [volume] slider with the 增强 switch at [boost]. */
fun boostedAudioVolume(volume: Double, boost: Boolean): Double {
    val normalized = normalizedAudioVolume(volume)
    return if (boost) normalized * audioVolumeBoostFactor else normalized
}

fun boostedAudioVolumePercentText(volume: Double, boost: Boolean): String {
    return "${(boostedAudioVolume(volume, boost) * 100).roundToLong()}%"
}

const val defaultParticipantVoiceVolume = 1.0
const val maxParticipantVoiceVolume = 2.0

fun normalizedParticipantVoiceVolume(volume: Double): Double {
    return _clamp(volume, 0.0, maxParticipantVoiceVolume)
}

fun participantVoiceVolumePercentText(volume: Double): String {
    return "${(normalizedParticipantVoiceVolume(volume) * 100).roundToLong()}%"
}

fun audioLevelSegmentCount(
    availableWidth: Double,
    segmentWidth: Double = 12.0,
    minSegments: Int = 24,
    maxSegments: Int = 56,
): Int {
    val segments = availableWidth / segmentWidth
    // Dart's `double.floor()` returns an int and throws for NaN and infinity.
    if (!segments.isFinite()) throw UnsupportedOperationException("Infinity or NaN toInt")
    return floor(segments)
        .toLong()
        .coerceIn(minSegments.toLong(), maxSegments.toLong())
        .toInt()
}

fun activeAudioLevelSegmentCount(
    level: Double,
    active: Boolean,
    segmentCount: Int,
): Int {
    if (!active) return 0
    return ceil(normalizedAudioVolume(level) * segmentCount)
        .toLong()
        .coerceIn(0L, segmentCount.toLong())
        .toInt()
}

fun audioLevelFromVisualizerBands(bands: Iterable<Any?>): Double {
    val noiseFloor = 0.04
    val displayGain = 1.45
    var peak = 0.0
    var squareSum = 0.0
    var count = 0

    for (value in bands) {
        if (value !is Number) continue
        val raw = value.toDouble()
        if (!raw.isFinite()) continue
        val clamped = _clamp(raw, 0.0, 1.0)
        val sample =
            if (clamped <= noiseFloor) {
                0.0
            } else {
                (clamped - noiseFloor) / (1 - noiseFloor)
            }
        if (sample > peak) peak = sample
        squareSum += sample * sample
        count++
    }

    if (count == 0 || peak <= 0) return 0.0
    val rms = sqrt(squareSum / count)
    val energy = (rms * 0.82) + (peak * 0.18)
    return _clamp(energy * displayGain, 0.0, 1.0)
}

/**
 * Dart's `double.clamp(lower, upper)`. It compares with `compareTo`, so NaN
 * clamps to [upperLimit] (Dart orders NaN above every number) and `-0.0` to
 * `0.0`, where Kotlin's `coerceIn` would pass both through.
 */
private fun _clamp(value: Double, lowerLimit: Double, upperLimit: Double): Double {
    if (value.compareTo(lowerLimit) < 0) return lowerLimit
    if (value.compareTo(upperLimit) > 0) return upperLimit
    return value
}
