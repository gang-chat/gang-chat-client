package com.gangchat.client.live

import com.gangchat.client.app.AudioDeviceInfo
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.StreamSubscription
import com.gangchat.client.foundation.VisibleForTesting
import com.gangchat.client.foundation.unawaited
import kotlin.coroutines.cancellation.CancellationException

open class LiveAudioDeviceService(
    systemAudioOnly: Boolean? = null,
    systemAudio: SystemAudioDevices? = null,
) {
    private val _systemAudioOnlyOverride: Boolean? = systemAudioOnly
    private val _systemAudioOverride: SystemAudioDevices? = systemAudio

    private val _systemAudio: SystemAudioDevices
        get() = _systemAudioOverride ?: _sharedSystemAudio

    private val _systemAudioOnly: Boolean
        get() = _systemAudioOnlyOverride ?: systemAudioOnlyForPlatform(isWeb = false, isWindows = false)

    open val devicesChanged: Stream<List<AudioDeviceInfo>>
        get() {
            if (_systemAudioOnly) {
                // Windows listens to the OS endpoint channel directly.
                return _asyncMap(_systemAudio.changes) { _systemAudio.enumerateDevices() }
            }
            // Re-merge native devices on every hardware change so a hotplug doesn't
            // wipe devices that WebRTC can't see outside a room.
            return _asyncMap(LiveKitHardware.instance.onDeviceChange.stream) { devices ->
                _mergeNativeDevices(_audioDeviceInfos(devices))
            }
        }

    open suspend fun enumerateDevices(): List<AudioDeviceInfo> {
        if (_systemAudioOnly) {
            return _systemAudio.enumerateDevicesOrThrow()
        }
        val webrtc = _audioDeviceInfos(LiveKitHardware.instance.enumerateDevices())
        return _mergeNativeDevices(webrtc)
    }

    // Adds native input and output devices that WebRTC didn't report (matched
    // by deviceId). On Android there are no native devices, so the WebRTC list
    // is returned unchanged.
    private suspend fun _mergeNativeDevices(webrtc: List<AudioDeviceInfo>): List<AudioDeviceInfo> {
        val native = _systemAudio.enumerateInputs() + _systemAudio.enumerateOutputs()
        if (native.isEmpty()) return webrtc
        val seen = webrtc.map { d -> "${d.kind}:${d.deviceId}" }.toMutableSet()
        val merged = webrtc.toMutableList()
        for (device in native) {
            if (seen.add("${device.kind}:${device.deviceId}")) merged.add(device)
        }
        return merged
    }

    open val selectedAudioInput: AudioDeviceInfo?
        get() = _audioDeviceInfoOrNull(LiveKitHardware.instance.selectedAudioInput)

    open val selectedAudioOutput: AudioDeviceInfo?
        get() = _audioDeviceInfoOrNull(LiveKitHardware.instance.selectedAudioOutput)

    open suspend fun selectAudioInput(device: AudioDeviceInfo) {
        LiveKitHardware.instance.selectAudioInput(_mediaDevice(device))
    }

    open suspend fun selectAudioOutput(device: AudioDeviceInfo) {
        LiveKitHardware.instance.selectAudioOutput(_mediaDevice(device))
    }

    private companion object {
        // Desktop native audio access (unsupported on Android).
        val _sharedSystemAudio: SystemAudioDevices by lazy { SystemAudioDevices() }
    }
}

@VisibleForTesting
fun systemAudioOnlyForPlatform(isWeb: Boolean, isWindows: Boolean): Boolean {
    return !isWeb && isWindows
}

private fun _audioDeviceInfos(devices: List<LkMediaDevice>): List<AudioDeviceInfo> {
    return devices.map(::_audioDeviceInfo)
}

private fun _audioDeviceInfoOrNull(device: LkMediaDevice?): AudioDeviceInfo? {
    return if (device == null) null else _audioDeviceInfo(device)
}

private fun _audioDeviceInfo(device: LkMediaDevice): AudioDeviceInfo {
    return AudioDeviceInfo(
        deviceId = device.deviceId,
        label = device.label,
        kind = device.kind,
        groupId = device.groupId ?: "",
    )
}

private fun _mediaDevice(device: AudioDeviceInfo): LkMediaDevice {
    return LkMediaDevice(device.deviceId, device.label, device.kind, device.groupId)
}

/**
 * Dart's `stream.asyncMap(convert)`: events are converted one at a time, in
 * order; a failed conversion is delivered as an error.
 */
internal fun <S, T> _asyncMap(source: Stream<S>, convert: suspend (S) -> T): Stream<T> {
    var subscription: StreamSubscription<S>? = null
    lateinit var controller: StreamController<T>
    var tail: kotlinx.coroutines.Deferred<Unit> = com.gangchat.client.foundation.completedFuture(Unit)
    controller =
        StreamController.broadcast(
            onListen = {
                subscription =
                    source.listen(
                        { value ->
                            val previous = tail
                            tail =
                                com.gangchat.client.foundation.future {
                                    try {
                                        previous.await()
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                    }
                                    try {
                                        val converted = convert(value)
                                        if (!controller.isClosed) controller.add(converted)
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        if (!controller.isClosed) controller.addError(e)
                                    }
                                }
                        },
                        onError = { error -> if (!controller.isClosed) controller.addError(error) },
                        onDone = { unawaited { if (!controller.isClosed) controller.close() } },
                    )
            },
            onCancel = {
                subscription?.cancel()
                subscription = null
            },
        )
    return controller.stream
}
