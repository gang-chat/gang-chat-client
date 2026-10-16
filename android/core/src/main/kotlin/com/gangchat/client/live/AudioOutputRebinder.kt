package com.gangchat.client.live

import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamSubscription
import com.gangchat.client.foundation.Timer
import com.gangchat.client.foundation.unawaited
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Recovers WebRTC audio output after macOS swaps the default output endpoint
 * underneath a live room.
 *
 * The prime case is a Bluetooth headset flipping between its A2DP (stereo
 * "music" profile, output only) and HFP (mono "call" profile, with mic). When
 * the mic opens, macOS forces the headset into HFP; when it closes, the
 * headset flips back to A2DP. Each flip tears down the old CoreAudio output
 * endpoint and builds a new one, but WebRTC's CoreAudio audio device module
 * keeps rendering into the dead endpoint — so playback (e.g. the music box)
 * degrades to noise until the playout unit is rebound to the live endpoint.
 *
 * macOS posts a device-change event on each flip. On that signal this rebinds
 * the output device (re-selecting forces the ADM to rebuild the playout unit
 * against the current endpoint, even when the device id is unchanged) and
 * re-applies track volumes. Everything is best-effort: a failed rebind leaves
 * the existing routing untouched rather than throwing into the caller.
 */
class AudioOutputRebinder(
    deviceChanges: Stream<Unit>,
    currentOutputDeviceId: suspend () -> String?,
    selectOutput: suspend (deviceId: String) -> Unit,
    onRebound: suspend () -> Unit,
    debounce: Duration = 300.milliseconds,
) {
    private val _deviceChanges: Stream<Unit> = deviceChanges
    private val _currentOutputDeviceId: suspend () -> String? = currentOutputDeviceId
    private val _selectOutput: suspend (deviceId: String) -> Unit = selectOutput
    private val _onRebound: suspend () -> Unit = onRebound
    private val _debounce: Duration = debounce

    private var _subscription: StreamSubscription<Unit>? = null
    private var _debounceTimer: Timer? = null
    private var _rebinding = false
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
        // Coalesce the burst of events a single profile flip emits, and give the
        // OS a beat to finish building the new endpoint before we bind to it.
        _debounceTimer?.cancel()
        _debounceTimer =
            Timer(_debounce) {
                unawaited { _rebind() }
            }
    }

    private suspend fun _rebind() {
        if (_stopped || _rebinding) return
        _rebinding = true
        try {
            val deviceId = _currentOutputDeviceId()
            if (_stopped) return
            if (deviceId != null && deviceId.isNotEmpty()) {
                _selectOutput(deviceId)
            }
            if (_stopped) return
            _onRebound()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Best-effort recovery: keep the existing routing on any failure.
        } finally {
            _rebinding = false
        }
    }
}
