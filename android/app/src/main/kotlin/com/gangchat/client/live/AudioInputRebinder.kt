package com.gangchat.client.live

import com.gangchat.client.app.AudioDeviceStore
import com.gangchat.client.app.audioDeviceInfoGroupId
import com.gangchat.client.app.audioDeviceInfoId
import com.gangchat.client.app.audioDeviceInfoKind
import com.gangchat.client.app.audioDeviceInfoLabel
import com.gangchat.client.app.preferredStoredAudioDeviceFrom
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamSubscription
import com.gangchat.client.foundation.Timer
import com.gangchat.client.foundation.unawaited
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Recovers the local microphone publisher after desktop audio input hotplug.
 *
 * Bluetooth headsets can remove and recreate their input endpoint when the
 * battery dies, reconnects, or flips between output-only and call profiles.
 * WebRTC may keep the old capture endpoint attached to the published mic
 * track, which then surfaces as publisher reconnect failures. This helper is
 * deliberately best-effort: it coalesces the burst of device-change events,
 * resolves the preferred live input device, and asks the session to restart
 * the local mic capture without reconnecting to the room.
 */
class AudioInputRebinder(
    deviceChanges: Stream<Unit>,
    currentInputDeviceId: suspend () -> String?,
    rebindInput: suspend (deviceId: String?) -> Unit,
    debounce: Duration = 300.milliseconds,
) {
    private val _deviceChanges = deviceChanges
    private val _currentInputDeviceId = currentInputDeviceId
    private val _rebindInput = rebindInput
    private val _debounce = debounce

    private var _subscription: StreamSubscription<Unit>? = null
    private var _debounceTimer: Timer? = null
    private var _rebinding = false
    private var _rebindQueued = false
    private var _stopped = false

    /**
     * Begins observing device changes. Safe to call once; a second call is a
     * no-op while already started.
     */
    fun start() {
        if (_subscription != null || _stopped) return
        _subscription =
            _deviceChanges.listen(
                { _scheduleRebind() },
                onError = { },
            )
    }

    /**
     * Stops observing and cancels any pending rebind. After this the rebinder is
     * inert and cannot be restarted.
     */
    suspend fun stop() {
        _stopped = true
        _debounceTimer?.cancel()
        _debounceTimer = null
        val sub = _subscription
        _subscription = null
        sub?.cancel()
    }

    private fun _scheduleRebind() {
        if (_stopped) return
        // A single Bluetooth reconnect can emit several add/remove/default-change
        // events. Wait for the device list to settle before restarting capture.
        _debounceTimer?.cancel()
        _debounceTimer =
            Timer(_debounce) {
                unawaited { _rebind() }
            }
    }

    private suspend fun _rebind() {
        if (_stopped) return
        if (_rebinding) {
            _rebindQueued = true
            return
        }
        _rebinding = true
        try {
            val deviceId = _currentInputDeviceId()
            if (_stopped) return
            _rebindInput(deviceId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Best-effort recovery: the next hardware event gets another attempt.
        } finally {
            _rebinding = false
            if (_rebindQueued && !_stopped) {
                _rebindQueued = false
                _scheduleRebind()
            }
        }
    }
}

suspend fun preferredLiveInputDeviceId(
    audioDeviceStore: AudioDeviceStore?,
    audioDevices: LiveAudioDeviceService,
    systemAudio: SystemAudioDevices,
): String? {
    val systemDefaultInputId = systemAudio.currentInputDeviceId()
    if (audioDeviceStore == null) return systemDefaultInputId
    return try {
        val stored = audioDeviceStore.read()
        val devices = audioDevices.enumerateDevices()
        val storedInput =
            preferredStoredAudioDeviceFrom(
                devices,
                kind = "audioinput",
                storedDeviceId = stored.inputDeviceId,
                storedDeviceLabel = stored.inputDeviceLabel,
                storedDeviceGroupId = stored.inputDeviceGroupId,
                kindOf = ::audioDeviceInfoKind,
                deviceIdOf = ::audioDeviceInfoId,
                labelOf = ::audioDeviceInfoLabel,
                groupIdOf = ::audioDeviceInfoGroupId,
                systemDefaultDeviceId = systemDefaultInputId,
            )
        storedInput?.deviceId ?: systemDefaultInputId
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        systemDefaultInputId
    }
}
