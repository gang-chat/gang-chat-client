package com.gangchat.client.live

import com.gangchat.client.app.AudioDeviceStore
import com.gangchat.client.app.StoredAudioDevices
import com.gangchat.client.app.audioDeviceInfoGroupId
import com.gangchat.client.app.audioDeviceInfoId
import com.gangchat.client.app.audioDeviceInfoKind
import com.gangchat.client.app.audioDeviceInfoLabel
import com.gangchat.client.app.preferredStoredAudioDeviceFrom
import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamSubscription
import com.gangchat.client.foundation.Timer
import com.gangchat.client.foundation.complete
import com.gangchat.client.foundation.future
import com.gangchat.client.foundation.timeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.selects.select

/** The input/output choice resolved from one device-list snapshot. */
class PreferredLiveAudioDevices(
    val inputDeviceId: String? = null,
    val outputDeviceId: String? = null,
)

/**
 * Resolves both preferred endpoints together.
 *
 * In particular, [audioDevices]' `enumerateDevices` is invoked exactly once.
 * This prevents an input and output change from racing two native device
 * enumerations against the same WebRTC audio-device module.
 */
suspend fun preferredLiveAudioDevices(
    audioDeviceStore: AudioDeviceStore?,
    audioDevices: LiveAudioDeviceService,
    systemAudio: SystemAudioDevices,
): PreferredLiveAudioDevices {
    val inputDefaultFuture = future { systemAudio.currentInputDeviceId() }
    val outputDefaultFuture = future { systemAudio.currentOutputDeviceId() }
    val storedFuture = future { _readStoredAudioDevices(audioDeviceStore) }

    // Do not turn a native timeout into an empty topology. Keeping the existing
    // endpoints is safer than accidentally rebinding both directions to a
    // default while the native audio stack is still recovering.
    val devices = audioDevices.enumerateDevices()

    val defaults = listOf(inputDefaultFuture.await(), outputDefaultFuture.await())
    val stored = storedFuture.await()

    val input =
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
            systemDefaultDeviceId = defaults[0],
        )
    val output =
        preferredStoredAudioDeviceFrom(
            devices,
            kind = "audiooutput",
            storedDeviceId = stored.outputDeviceId,
            storedDeviceLabel = stored.outputDeviceLabel,
            storedDeviceGroupId = stored.outputDeviceGroupId,
            kindOf = ::audioDeviceInfoKind,
            deviceIdOf = ::audioDeviceInfoId,
            labelOf = ::audioDeviceInfoLabel,
            groupIdOf = ::audioDeviceInfoGroupId,
            systemDefaultDeviceId = defaults[1],
        )

    return PreferredLiveAudioDevices(
        inputDeviceId = input?.deviceId ?: defaults[0],
        outputDeviceId = output?.deviceId ?: defaults[1],
    )
}

private suspend fun _readStoredAudioDevices(store: AudioDeviceStore?): StoredAudioDevices {
    if (store == null) return StoredAudioDevices()
    return try {
        store.read()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        StoredAudioDevices()
    }
}

/**
 * Coalesces desktop input/output changes into one serialized refresh.
 *
 * A device transition can emit several notifications and can emit another
 * notification while a rebind is still running. At most one refresh runs at
 * once; one trailing refresh is retained so the final settled topology is
 * always applied.
 */
class AudioDeviceRebinder(
    deviceChanges: Stream<Unit>,
    resolvePreferredDevices: suspend () -> PreferredLiveAudioDevices,
    rebindInput: suspend (deviceId: String?) -> Unit,
    selectOutput: suspend (deviceId: String) -> Unit,
    onOutputRebound: suspend () -> Unit,
    debounce: Duration = 300.milliseconds,
    operationTimeout: Duration = 7.seconds,
) {
    private val _deviceChanges = deviceChanges
    private val _resolvePreferredDevices = resolvePreferredDevices
    private val _rebindInput = rebindInput
    private val _selectOutput = selectOutput
    private val _onOutputRebound = onOutputRebound
    private val _debounce = debounce
    private val _operationTimeout = operationTimeout

    private var _subscription: StreamSubscription<Unit>? = null
    private var _debounceTimer: Timer? = null
    private val _stopSignal = Completer<Unit>()
    private var _activeRefresh: Deferred<Unit>? = null
    private var _refreshing = false
    private var _refreshQueued = false
    private var _stopped = false

    fun start() {
        if (_subscription != null || _stopped) return
        _subscription =
            _deviceChanges.listen(
                { _scheduleRefresh() },
                onError = { },
            )
    }

    suspend fun stop() {
        if (_stopped) {
            _activeRefresh?.await()
            return
        }
        _stopped = true
        if (!_stopSignal.isCompleted) _stopSignal.complete()
        _debounceTimer?.cancel()
        _debounceTimer = null
        val subscription = _subscription
        _subscription = null
        subscription?.cancel()
        try {
            _activeRefresh?.await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Stopping deliberately cancels/invalidates the in-flight refresh.
        }
    }

    private fun _scheduleRefresh() {
        if (_stopped) return
        _debounceTimer?.cancel()
        _debounceTimer =
            Timer(_debounce) {
                val refresh = future { _refresh() }
                _activeRefresh = refresh
                refresh.invokeOnCompletion {
                    if (_activeRefresh === refresh) _activeRefresh = null
                }
            }
    }

    /**
     * Dart's `Future.any([Future.sync(operation).timeout(t), stopSignal.then(throw)])`:
     * the timeout and the stop signal only end the wait; like a Dart future,
     * the operation itself keeps running if it outlives them.
     */
    private suspend fun <T> _runStage(operation: suspend () -> T): T {
        val stage = future { operation() }
        return timeout(_operationTimeout) {
            select {
                stage.onAwait { it }
                _stopSignal.future.onAwait { throw _RebindCancelled() }
            }
        }
    }

    private suspend fun _refresh() {
        if (_stopped) return
        if (_refreshing) {
            _refreshQueued = true
            return
        }

        _refreshing = true
        try {
            val preferred = _runStage(_resolvePreferredDevices)
            if (_stopped) return

            // Keep input and output native operations serialized too. Some
            // WebRTC audio device modules use one device thread for both directions.
            try {
                _runStage { _rebindInput(preferred.inputDeviceId) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Input recovery is best-effort; still give output a chance to recover.
            }
            if (_stopped) return

            val outputDeviceId = preferred.outputDeviceId
            if (outputDeviceId != null && outputDeviceId.isNotEmpty()) {
                try {
                    _runStage { _selectOutput(outputDeviceId) }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // The OS may already have moved playback to its new default. Always
                    // reapply routing/volume even when explicit selection is unavailable.
                }
            }
            if (_stopped) return
            _runStage(_onOutputRebound)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // The next device event gets another attempt without disconnecting room.
        } finally {
            _refreshing = false
            if (_refreshQueued && !_stopped) {
                _refreshQueued = false
                _scheduleRefresh()
            }
        }
    }
}

private class _RebindCancelled : Exception()
