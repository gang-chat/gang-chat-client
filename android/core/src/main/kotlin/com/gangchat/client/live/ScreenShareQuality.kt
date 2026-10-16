package com.gangchat.client.live

import java.util.Objects
import kotlin.math.abs

/*
 * Pure helpers for configurable screen-share output quality.
 *
 * Capture constraints are only hints in upstream flutter_webrtc. The vendored
 * Windows and macOS capture paths therefore adapt desktop frames before they
 * enter the encoder, while Android retains its MediaProjection session and
 * applies publisher caps. A desktop live switch rebuilds only the capture
 * track for the already-selected source, so it does not reopen the picker.
 */

/** Selectable target heights, in pixels. */
val screenShareHeightOptions: List<Int> = listOf(480, 720, 1080)

/** The default when nothing is stored. */
const val defaultScreenShareMaxHeight: Int = 720

/** Selectable publisher frame-rate caps. */
val screenShareFrameRateOptions: List<Int> = listOf(15, 30, 60)

/** The balanced default on every supported platform. */
const val defaultScreenShareFrameRate: Int = 30

/** Maximum capture/output rate supported by the Android screen capturer. */
const val androidScreenShareFrameRateCap: Int = 30

/** Maximum capture/output rate requested on Windows and macOS. */
const val desktopScreenShareFrameRateCap: Int = 60

/**
 * Coerce an arbitrary stored/in-flight value to one of the supported options,
 * falling back to [defaultScreenShareMaxHeight] for anything unrecognised.
 */
fun normalizedScreenShareMaxHeight(height: Int?): Int {
    if (height == null) return defaultScreenShareMaxHeight
    return if (screenShareHeightOptions.contains(height)) height else defaultScreenShareMaxHeight
}

/** Normalize a stored/selected frame rate and respect a platform's ceiling. */
fun normalizedScreenShareFrameRate(
    frameRate: Int?,
    maxFrameRate: Int = desktopScreenShareFrameRateCap,
): Int {
    val fallback =
        if (defaultScreenShareFrameRate > maxFrameRate) maxFrameRate else defaultScreenShareFrameRate
    val selected =
        if (frameRate != null && screenShareFrameRateOptions.contains(frameRate)) frameRate else fallback
    return if (selected > maxFrameRate) maxFrameRate else selected
}

/** A plain 16:9 target resolution with an even width. */
class ScreenShareResolution(val width: Int, val height: Int) {
    override fun equals(other: Any?): Boolean =
        other is ScreenShareResolution &&
            other.width == width &&
            other.height == height

    override fun hashCode(): Int = Objects.hash(width, height)

    override fun toString(): String = "ScreenShareResolution($width x $height)"
}

/**
 * One screen-share output profile.
 *
 * [maxBitrate] is a ceiling rather than a reservation. Static screen content
 * normally uses much less bandwidth, while motion can use the full budget.
 */
class ScreenShareQuality(
    val maxHeight: Int,
    val maxFrameRate: Int,
    val maxBitrate: Int,
) {
    val resolution: ScreenShareResolution get() = screenShareResolutionForHeight(maxHeight)

    override fun equals(other: Any?): Boolean =
        other is ScreenShareQuality &&
            other.maxHeight == maxHeight &&
            other.maxFrameRate == maxFrameRate &&
            other.maxBitrate == maxBitrate

    override fun hashCode(): Int = Objects.hash(maxHeight, maxFrameRate, maxBitrate)
}

/**
 * Result of checking a quality update against live outbound statistics.
 *
 * WebRTC can intentionally stop producing outbound frames while a local
 * screen-share track has no subscribers. That is not an apply failure: the
 * sender parameters are ready and verification must resume when a viewer
 * subscribes.
 */
enum class ScreenShareQualityCheckResult { verified, awaitingOutboundFrames, retry }

fun screenShareQualityCheckResult(
    parametersReady: Boolean,
    hasOutboundFrames: Boolean,
    resolutionVerified: Boolean,
    frameRateCapVerified: Boolean,
): ScreenShareQualityCheckResult {
    if (!hasOutboundFrames) {
        return if (parametersReady) {
            ScreenShareQualityCheckResult.awaitingOutboundFrames
        } else {
            ScreenShareQualityCheckResult.retry
        }
    }
    return if (parametersReady && resolutionVerified && frameRateCapVerified) {
        ScreenShareQualityCheckResult.verified
    } else {
        ScreenShareQualityCheckResult.retry
    }
}

/**
 * Resolves which sender scale most likely produced an outbound sample.
 *
 * Native Windows stats can omit both RID and SSRC. In that case, prefer the
 * smallest reconstructed source that can still satisfy the selected target.
 * This correctly identifies a 540p low simulcast layer from a 1080p source
 * while avoiding the inverse mistake of treating a 1080p high layer as a 4K
 * source. A known RID/SSRC always wins over this fallback.
 */
fun screenShareSourceScaleForSample(
    encodedHeight: Int,
    targetHeight: Int,
    encodingScales: Iterable<Double>,
    matchedScale: Double? = null,
    knownSourceHeight: Int? = null,
): Double {
    if (matchedScale != null && matchedScale >= 1) return matchedScale
    val scales =
        encodingScales.map { scale -> if (scale < 1) 1.0 else scale }.toSet().toMutableList()
    scales.sort()
    if (scales.isEmpty()) return 1.0
    if (knownSourceHeight != null && knownSourceHeight > 0) {
        return scales.reduce { best, candidate ->
            val bestDelta = abs(knownSourceHeight / best - encodedHeight)
            val candidateDelta = abs(knownSourceHeight / candidate - encodedHeight)
            if (candidateDelta < bestDelta) candidate else best
        }
    }
    for (scale in scales) {
        if (_dartRound(encodedHeight * scale) >= targetHeight) return scale
    }
    return scales.last()
}

/**
 * The actual output size for a selected height while preserving the source
 * aspect ratio and never scaling a smaller source up.
 */
fun screenShareCappedOutputResolution(
    sourceWidth: Int?,
    sourceHeight: Int?,
    targetHeight: Int,
): ScreenShareResolution {
    val target = normalizedScreenShareMaxHeight(targetHeight)
    if (sourceWidth == null ||
        sourceWidth <= 0 ||
        sourceHeight == null ||
        sourceHeight <= 0
    ) {
        return screenShareResolutionForHeight(target)
    }
    val outputHeight = if (sourceHeight < target) sourceHeight else target
    var outputWidth = _dartRound(sourceWidth.toDouble() * outputHeight / sourceHeight).toInt()
    if (outputWidth < 2) outputWidth = 2
    if (outputWidth % 2 != 0) outputWidth += 1
    return ScreenShareResolution(outputWidth, outputHeight)
}

private val _screenShareBitrateAt30Fps: Map<Int, Int> =
    mapOf(
        480 to 2000 * 1000,
        720 to 3000 * 1000,
        1080 to 4000 * 1000,
    )

/** Resolve the selected output profile and apply the platform frame-rate cap. */
fun screenShareQualityForHeight(
    height: Int,
    frameRate: Int = defaultScreenShareFrameRate,
    maxFrameRate: Int = desktopScreenShareFrameRateCap,
): ScreenShareQuality {
    val normalized = normalizedScreenShareMaxHeight(height)
    val normalizedFrameRate =
        normalizedScreenShareFrameRate(
            frameRate,
            maxFrameRate = maxFrameRate,
        )
    return ScreenShareQuality(
        maxHeight = normalized,
        maxFrameRate = normalizedFrameRate,
        // Scale the ceiling with frame rate so each resolution keeps a stable
        // bits-per-frame budget across the 15/30/60 FPS choices.
        maxBitrate =
            _dartRound(_screenShareBitrateAt30Fps.getValue(normalized).toDouble() * normalizedFrameRate / 30)
                .toInt(),
    )
}

/** The 16:9 target resolution for a given target [height]. */
fun screenShareResolutionForHeight(height: Int): ScreenShareResolution {
    val normalized = normalizedScreenShareMaxHeight(height)
    var width = _dartRound(normalized.toDouble() * 16 / 9).toInt()
    if (width % 2 != 0) width += 1
    return ScreenShareResolution(width, normalized)
}

/**
 * The publisher scaling factor needed to cap a source at [targetHeight].
 *
 * WebRTC only scales down, so this always returns at least 1.0.
 */
fun screenShareScaleDownBy(
    sourceHeight: Int,
    targetHeight: Int,
): Double {
    val target = normalizedScreenShareMaxHeight(targetHeight)
    if (sourceHeight <= 0 || sourceHeight <= target) return 1.0
    return sourceHeight.toDouble() / target
}

fun screenShareResolutionLabel(height: Int): String = "${normalizedScreenShareMaxHeight(height)}p"

fun screenShareFrameRateLabel(frameRate: Int): String = "${normalizedScreenShareFrameRate(frameRate)} FPS"

/** Dart's `double.round()`: the closest integer, halves rounded away from zero. */
private fun _dartRound(value: Double): Long {
    val truncated = kotlin.math.truncate(value)
    val fraction = value - truncated
    return when {
        fraction >= 0.5 -> truncated.toLong() + 1
        fraction <= -0.5 -> truncated.toLong() - 1
        else -> truncated.toLong()
    }
}
