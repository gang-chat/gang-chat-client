package com.gangchat.client.app

import com.gangchat.client.live.defaultScreenShareFrameRate
import com.gangchat.client.live.defaultScreenShareMaxHeight
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/** Default music-box and screen-share listening volume. */
const val defaultAudioVolume: Double = 0.5

/**
 * Default microphone and playback gain: unity, the slider at its end. The
 * Flutter client started both at [defaultAudioVolume], which halved the voice
 * that was sent and everything that was heard.
 */
const val defaultInputVolume: Double = 1.0
const val defaultOutputVolume: Double = 1.0

class StoredAudioDevices(
    val inputDeviceId: String? = null,
    val inputDeviceLabel: String? = null,
    val inputDeviceGroupId: String? = null,
    val outputDeviceId: String? = null,
    val outputDeviceLabel: String? = null,
    val outputDeviceGroupId: String? = null,
    val inputVolume: Double = defaultInputVolume,
    val outputVolume: Double = defaultOutputVolume,
    /** The 增强 switches: the gain is the volume × [audioVolumeBoostFactor]. */
    val inputVolumeBoost: Boolean = false,
    val outputVolumeBoost: Boolean = false,
    /**
     * Local listening volume for the music box bot's audio track. Independent of
     * [outputVolume] — it scales only the `__musicbox__` participant.
     */
    val musicBoxVolume: Double = defaultAudioVolume,
    /**
     * Local listening volume for remote screen-share audio. Independent of
     * [outputVolume], which scales ordinary voice tracks.
     */
    val screenShareVolume: Double = defaultAudioVolume,
    /**
     * Target max height (px) for the local screen share — one of
     * [screenShareHeightOptions]. [defaultScreenShareMaxHeight] is the balanced
     * resolution; lower values cap what we publish to save bandwidth.
     */
    val screenShareMaxHeight: Int = defaultScreenShareMaxHeight,
    /**
     * Selected publisher frame-rate cap. Platforms may clamp this to their
     * capture ceiling (Android currently caps it at 30 FPS).
     */
    val screenShareFrameRate: Int = defaultScreenShareFrameRate,
) {
    val isEmpty: Boolean
        get() =
            (inputDeviceId == null || inputDeviceId.isEmpty()) &&
                (inputDeviceLabel == null || inputDeviceLabel.isEmpty()) &&
                (inputDeviceGroupId == null || inputDeviceGroupId.isEmpty()) &&
                (outputDeviceId == null || outputDeviceId.isEmpty()) &&
                (outputDeviceLabel == null || outputDeviceLabel.isEmpty()) &&
                (outputDeviceGroupId == null || outputDeviceGroupId.isEmpty()) &&
                inputVolume == defaultInputVolume &&
                outputVolume == defaultOutputVolume &&
                !inputVolumeBoost &&
                !outputVolumeBoost &&
                musicBoxVolume == defaultAudioVolume &&
                screenShareVolume == defaultAudioVolume &&
                screenShareMaxHeight == defaultScreenShareMaxHeight &&
                screenShareFrameRate == defaultScreenShareFrameRate
}

class RestoredAudioDevices<T>(val input: T? = null, val output: T? = null)

fun <T> preferredStoredAudioDeviceFrom(
    devices: List<T>,
    kind: String,
    storedDeviceId: String?,
    kindOf: AudioDeviceKindOf<T>,
    deviceIdOf: AudioDeviceIdOf<T>,
    storedDeviceLabel: String? = null,
    storedDeviceGroupId: String? = null,
    labelOf: AudioDeviceLabelOf<T>? = null,
    groupIdOf: AudioDeviceGroupIdOf<T>? = null,
    systemDefaultDeviceId: String? = null,
): T? {
    val storedDevice =
        storedPreferredAudioDeviceFrom(
            devices,
            kind = kind,
            storedDeviceId = storedDeviceId,
            storedDeviceLabel = storedDeviceLabel,
            storedDeviceGroupId = storedDeviceGroupId,
            kindOf = kindOf,
            deviceIdOf = deviceIdOf,
            labelOf = labelOf,
            groupIdOf = groupIdOf,
        )
    if (storedDevice != null) return storedDevice

    // macOS never enumerates a synthetic "default" device, so the OS-selected
    // device is identified by an explicit id from the native channel. Prefer it
    // when there is no local preference yet so the picker follows the system.
    val systemDefault =
        storedAudioDeviceFrom(
            devices,
            kind = kind,
            deviceId = systemDefaultDeviceId,
            kindOf = kindOf,
            deviceIdOf = deviceIdOf,
        )
    if (systemDefault != null) return systemDefault

    // WebRTC exposes the OS-selected device as the synthetic "default" device
    // on Windows. Use it whenever there is no local preference yet, or when the
    // saved device is temporarily unavailable.
    return storedAudioDeviceFrom(
        devices,
        kind = kind,
        deviceId = "default",
        kindOf = kindOf,
        deviceIdOf = deviceIdOf,
    )
}

fun <T> storedPreferredAudioDeviceFrom(
    devices: List<T>,
    kind: String,
    storedDeviceId: String?,
    storedDeviceLabel: String?,
    storedDeviceGroupId: String?,
    kindOf: AudioDeviceKindOf<T>,
    deviceIdOf: AudioDeviceIdOf<T>,
    labelOf: AudioDeviceLabelOf<T>?,
    groupIdOf: AudioDeviceGroupIdOf<T>?,
): T? {
    val storedDevice =
        storedAudioDeviceFrom(
            devices,
            kind = kind,
            deviceId = storedDeviceId,
            kindOf = kindOf,
            deviceIdOf = deviceIdOf,
        )
    if (storedDevice != null) return storedDevice

    return storedAudioDeviceBySignatureFrom(
        devices,
        kind = kind,
        label = storedDeviceLabel,
        groupId = storedDeviceGroupId,
        kindOf = kindOf,
        labelOf = labelOf,
        groupIdOf = groupIdOf,
    )
}

fun <T> storedAudioDeviceFrom(
    devices: List<T>,
    kind: String,
    deviceId: String?,
    kindOf: AudioDeviceKindOf<T>,
    deviceIdOf: AudioDeviceIdOf<T>,
): T? {
    if (deviceId == null || deviceId.isEmpty()) return null
    for (device in devices) {
        if (kindOf(device) == kind && deviceIdOf(device) == deviceId) {
            return device
        }
    }
    return null
}

fun <T> storedAudioDeviceBySignatureFrom(
    devices: List<T>,
    kind: String,
    label: String?,
    groupId: String?,
    kindOf: AudioDeviceKindOf<T>,
    labelOf: AudioDeviceLabelOf<T>?,
    groupIdOf: AudioDeviceGroupIdOf<T>?,
): T? {
    val storedLabel = _normalizedAudioDeviceSignaturePart(label)
    val storedGroupId = _normalizedAudioDeviceSignaturePart(groupId)
    if (storedLabel == null && storedGroupId == null) return null
    if (storedLabel != null && labelOf == null) return null
    if (storedGroupId != null && groupIdOf == null) return null

    val matches = mutableListOf<T>()
    val uniqueLabelFallbacks = mutableListOf<T>()
    for (device in devices) {
        if (kindOf(device) != kind) continue
        val deviceLabel =
            if (labelOf == null) {
                null
            } else {
                _normalizedAudioDeviceSignaturePart(labelOf(device))
            }
        val deviceGroupId =
            if (groupIdOf == null) {
                null
            } else {
                _normalizedAudioDeviceSignaturePart(groupIdOf(device))
            }
        val labelMatches = storedLabel != null && deviceLabel == storedLabel
        val groupMatches =
            storedGroupId != null && deviceGroupId == storedGroupId

        if (labelMatches) {
            uniqueLabelFallbacks.add(device)
        }
        if (storedGroupId != null) {
            if (groupMatches && (storedLabel == null || labelMatches)) {
                matches.add(device)
            }
        } else if (labelMatches) {
            matches.add(device)
        }
    }
    if (matches.size == 1) return matches.single()
    // Some platforms change endpoint ids/group ids across Bluetooth profile
    // transitions or driver reinstalls. A unique label is safe to restore; an
    // ambiguous label still falls back to the system default.
    if (matches.isEmpty() &&
        storedGroupId != null &&
        uniqueLabelFallbacks.size == 1
    ) {
        return uniqueLabelFallbacks.single()
    }
    return null
}

suspend fun <T> selectStoredAudioDeviceIfChanged(
    selected: T?,
    device: T,
    select: suspend (device: T) -> Unit,
    kindOf: AudioDeviceKindOf<T>,
    deviceIdOf: AudioDeviceIdOf<T>,
): T? {
    if (isSameAudioDevice(
            selected,
            device,
            kindOf = kindOf,
            deviceIdOf = deviceIdOf,
        )
    ) {
        return device
    }
    return try {
        select(device)
        device
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }
}

fun storedAudioDeviceIdFromStorageValue(value: String?): String? {
    val trimmed = value?.trim()
    return if (trimmed == null || trimmed.isEmpty()) null else trimmed
}

fun storedAudioDeviceSignatureFromStorageValue(value: String?): String? {
    val trimmed = value?.trim()
    return if (trimmed == null || trimmed.isEmpty()) null else trimmed
}

fun storedAudioVolumeFromStorageValue(value: String?): Double {
    val parsed = _dartDoubleTryParse(value ?: "") ?: return 1.0
    return normalizedAudioVolume(parsed)
}

fun audioVolumeStorageString(volume: Double): String {
    return String.format(Locale.ROOT, "%.3f", normalizedAudioVolume(volume))
}

private fun _normalizedAudioDeviceSignaturePart(value: String?): String? {
    val trimmed = value?.trim()
    if (trimmed == null || trimmed.isEmpty()) return null
    return trimmed.lowercase()
}

private val _dartDoubleLiteral =
    Regex("[+-]?(?:NaN|Infinity|(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)")

/**
 * Dart's `double.tryParse(source)`: surrounding whitespace is ignored and
 * `NaN` / `Infinity` are accepted, but Java's extras (`1.0d`, `1f`, hex
 * floats) are not.
 */
private fun _dartDoubleTryParse(source: String): Double? {
    val text = source.trim()
    if (!_dartDoubleLiteral.matches(text)) return null
    return when (text.trimStart('+', '-')) {
        "NaN" -> Double.NaN
        "Infinity" -> if (text.startsWith("-")) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        else -> text.toDouble()
    }
}
