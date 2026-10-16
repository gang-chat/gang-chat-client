package com.gangchat.client.live

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

/**
 * Debounces and serializes camera-topology refreshes.
 *
 * Windows reports camera arrival/removal through the runner because
 * libwebrtc's desktop callback only observes audio endpoints. macOS and
 * Android continue to use their native WebRTC device-change callbacks.
 */
class CameraDeviceReconciler(
    deviceChanges: Stream<Unit>,
    refreshDevices: suspend () -> Unit,
    debounce: Duration = 300.milliseconds,
    operationTimeout: Duration = 7.seconds,
) {
    private val _deviceChanges: Stream<Unit> = deviceChanges
    private val _refreshDevices: suspend () -> Unit = refreshDevices
    private val _debounce: Duration = debounce
    private val _operationTimeout: Duration = operationTimeout

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
            // Stopping deliberately invalidates the in-flight refresh.
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

    private suspend fun _refresh() {
        if (_stopped) return
        if (_refreshing) {
            _refreshQueued = true
            return
        }
        _refreshing = true
        try {
            // Dart's `Future.any([Future.sync(refresh).timeout(t), stopSignal])`:
            // the timeout and the stop signal only end the wait; like a Dart
            // future, the refresh itself keeps running if it outlives them.
            val work = future { _refreshDevices() }
            timeout(_operationTimeout) {
                select<Unit> {
                    work.onAwait { }
                    _stopSignal.future.onAwait { }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // A later topology event receives a fresh bounded attempt.
        } finally {
            _refreshing = false
            if (_refreshQueued && !_stopped) {
                _refreshQueued = false
                _scheduleRefresh()
            }
        }
    }
}
