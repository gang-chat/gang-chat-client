package com.gangchat.client.live

import com.gangchat.client.app.AudioDeviceInfo
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.waitAll
import com.gangchat.client.foundation.future

/**
 * Desktop system audio-device access (the native `gang_chat/audio_devices`
 * channel of the macOS and Windows runners).
 *
 * macOS needs CoreAudio enumeration because WebRTC lists no audio devices
 * before a room is joined, and Windows uses it as the authoritative device
 * source. Neither applies on Android: there is no native channel, so — as in
 * the Flutter Android build — this is unsupported, enumerates nothing and
 * reports no system default. Android devices come from WebRTC/AudioSwitch
 * through [LiveKitHardware]. The class stays so the shared live code (and
 * its tests, which subclass it) keep the Dart shape.
 */
open class SystemAudioDevices(
    supported: Boolean? = null,
    @Suppress("UNUSED_PARAMETER") sharedEvents: Boolean = true,
) {
    private val _supportedOverride: Boolean? = supported
    private val _inputChanges = StreamController.broadcast<String?>()
    private val _outputChanges = StreamController.broadcast<String?>()
    private val _videoChanges = StreamController.broadcast<Unit>()
    private val _changes = StreamController.broadcast<String?>()
    private var _disposed = false

    private val _supported: Boolean
        get() = _supportedOverride ?: false

    /** Native input devices, or an empty list when unsupported/unavailable. */
    open suspend fun enumerateInputs(): List<AudioDeviceInfo> {
        return _enumerate("audioinput", "麦克风")
    }

    /** Native output devices, or an empty list when unsupported/unavailable. */
    open suspend fun enumerateOutputs(): List<AudioDeviceInfo> {
        return _enumerate("audiooutput", "扬声器")
    }

    /**
     * Captures one logical input/output snapshot.
     *
     * Both directions are requested together so callers never perform two
     * independent full refreshes for the same native device-change burst.
     */
    open suspend fun enumerateDevices(): List<AudioDeviceInfo> {
        val devices =
            waitAll(
                listOf(
                    future { enumerateInputs() },
                    future { enumerateOutputs() },
                ),
            )
        return devices[0] + devices[1]
    }

    /**
     * Captures a native input/output snapshot without converting a platform
     * failure into an empty topology.
     *
     * Device pickers intentionally use [enumerateDevices] so they can remain
     * usable when a platform integration is unavailable. Automatic rebinding
     * must use this strict variant: treating a timeout as "no devices" would
     * incorrectly switch both endpoints to their defaults.
     */
    open suspend fun enumerateDevicesOrThrow(): List<AudioDeviceInfo> {
        if (!_supported) return emptyList()
        return enumerateDevices()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun _enumerate(kind: String, fallbackLabel: String): List<AudioDeviceInfo> {
        // No native device channel exists on Android.
        return emptyList()
    }

    open suspend fun currentInputDeviceId(): String? {
        return _currentDeviceId()
    }

    open suspend fun currentOutputDeviceId(): String? {
        return _currentDeviceId()
    }

    /** Backwards-compatible alias for the default input device id. */
    open suspend fun currentDeviceId(): String? = currentInputDeviceId()

    private fun _currentDeviceId(): String? {
        // The picker degrades to the enumerated/WebRTC list when the native
        // default is unavailable (always on Android).
        return null
    }

    open val inputChanges: Stream<String?> get() = _inputChanges.stream

    open val outputChanges: Stream<String?> get() = _outputChanges.stream

    /**
     * Windows device-tree notifications used to reconcile camera selection.
     * Never emits on Android.
     */
    open val videoChanges: Stream<Unit> get() = _videoChanges.stream

    /**
     * Any input/output default or endpoint-topology change. Never emits on
     * Android (route changes arrive through [LiveKitHardware.onDeviceChange]).
     */
    open val changes: Stream<String?> get() = _changes.stream

    open suspend fun dispose() {
        if (_disposed) return
        _disposed = true
        _inputChanges.close()
        _outputChanges.close()
        _videoChanges.close()
        _changes.close()
    }
}
