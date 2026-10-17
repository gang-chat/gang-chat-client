package com.gangchat.client.live

import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.debugPrint
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.platform.webrtc.WebRtc
import java.util.Objects
import kotlin.coroutines.cancellation.CancellationException

/** livekit_client's `MediaDevice`. */
class LkMediaDevice(
    val deviceId: String,
    val label: String,
    val kind: String,
    val groupId: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is LkMediaDevice &&
            other.deviceId == deviceId &&
            other.kind == kind &&
            other.label == label &&
            other.groupId == groupId
    }

    override fun hashCode(): Int = Objects.hash(deviceId, kind, label)

    override fun toString(): String = "MediaDevice{deviceId: $deviceId, label: $label, kind: $kind, groupId: $groupId}"
}

/**
 * livekit_client's `Hardware` singleton (with the Gang Chat fork's Android
 * changes: output and input selection are supported on Android, and the
 * selected devices are reconciled against every refreshed topology), over
 * the Android device layer of flutter_webrtc ([WebRtc]): cameras,
 * microphones from the AudioManager and the AudioSwitch outputs
 * (`speaker`, `earpiece`, `wired-headset`, `bluetooth`).
 */
class LiveKitHardware private constructor() {
    private val _onDeviceChange: StreamController<List<LkMediaDevice>> = StreamController.broadcast()

    /** Device topology changes (the reconciled device list after each change). */
    val onDeviceChange: StreamController<List<LkMediaDevice>>
        get() {
            _ensureInitialized()
            return _onDeviceChange
        }

    var selectedAudioInput: LkMediaDevice? = null

    var selectedAudioOutput: LkMediaDevice? = null

    var selectedVideoInput: LkMediaDevice? = null

    private var _initialized = false

    /** The constructor's work, deferred to first use (no Android context at class load). */
    private fun _ensureInitialized() {
        if (_initialized) return
        _initialized = true
        try {
            WebRtc.deviceChanges.listen({ unawaited { _handleDeviceChange() } })
        } catch (e: Exception) {
            debugPrint("Media-device change listener unavailable: $e")
        }
        unawaited {
            try {
                _refreshSelectedDevices()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                debugPrint("Initial media-device enumeration failed: $error")
            }
        }
    }

    suspend fun enumerateDevices(type: String? = null): List<LkMediaDevice> {
        _ensureInitialized()
        val infos = WebRtc.enumerateDevices()
        var devices = infos.map { e -> LkMediaDevice(e.deviceId, e.label, e.kind, e.groupId) }
        if (type != null && type.isNotEmpty()) {
            devices = devices.filter { d -> d.kind == type }
        }
        return devices
    }

    suspend fun audioInputs(): List<LkMediaDevice> = enumerateDevices(type = "audioinput")

    suspend fun audioOutputs(): List<LkMediaDevice> = enumerateDevices(type = "audiooutput")

    suspend fun videoInputs(): List<LkMediaDevice> = enumerateDevices(type = "videoinput")

    suspend fun selectAudioOutput(device: LkMediaDevice) {
        _ensureInitialized()
        selectedAudioOutput = device
        WebRtc.selectAudioOutput(device.deviceId)
    }

    suspend fun selectAudioInput(device: LkMediaDevice) {
        _ensureInitialized()
        selectedAudioInput = device
        WebRtc.selectAudioInput(device.deviceId)
    }

    /**
     * [enable] sets speakerphone on or off; by default wired/bluetooth
     * headsets are still prioritized even if set to true.
     */
    suspend fun setSpeakerphoneOn(enable: Boolean) {
        _ensureInitialized()
        WebRtc.setSpeakerphoneOn(enable)
    }

    private fun _reconciledSelection(
        selected: LkMediaDevice?,
        devices: List<LkMediaDevice>,
        kind: String,
    ): LkMediaDevice? {
        val matching = devices.filter { device -> device.kind == kind }
        if (selected != null) {
            val refreshed = matching.firstOrNull { device -> device.deviceId == selected.deviceId }
            if (refreshed != null) return refreshed
        }
        return matching.firstOrNull()
    }

    private suspend fun _refreshSelectedDevices(): List<LkMediaDevice> {
        val devices = enumerateDevices()
        selectedAudioInput = _reconciledSelection(selectedAudioInput, devices, "audioinput")
        selectedAudioOutput = _reconciledSelection(selectedAudioOutput, devices, "audiooutput")
        selectedVideoInput = _reconciledSelection(selectedVideoInput, devices, "videoinput")
        return devices
    }

    /** Re-enumerates device topology and emits the reconciled selections. */
    suspend fun refreshDevices(): List<LkMediaDevice> {
        val devices = _refreshSelectedDevices()
        _onDeviceChange.add(devices)
        return devices
    }

    private suspend fun _handleDeviceChange() {
        try {
            refreshDevices()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            debugPrint("Media-device refresh failed: $error")
            // Still emit a topology signal so platform-specific recovery can run.
            _onDeviceChange.add(emptyList())
        }
    }

    companion object {
        val instance: LiveKitHardware by lazy { LiveKitHardware() }
    }
}
