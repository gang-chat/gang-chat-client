package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Stopwatch
import kotlin.time.Duration

// PORT: server_clock.dart re-exports `gangServerTimeHeader`; Kotlin has no
// re-exports, so import it from com.gangchat.client.protocol.

typealias ServerClockListener = () -> Unit

class ServerClock(
    localNow: (() -> DateTime)? = null,
    monotonicNow: (() -> Duration)? = null,
) {
    private val _localNow: () -> DateTime = localNow ?: { DateTime.now() }
    private val _monotonicNow: (() -> Duration)? = monotonicNow
    private val _stopwatch: Stopwatch = Stopwatch().apply { start() }
    private val _listeners: MutableSet<ServerClockListener> = LinkedHashSet()
    private var _serverBaseTime: DateTime? = null
    private var _monotonicBaseTime: Duration? = null
    private var _requestRoundTrip: Duration? = null

    fun now(): DateTime {
        val serverBaseTime = _serverBaseTime
        val monotonicBaseTime = _monotonicBaseTime
        if (serverBaseTime == null || monotonicBaseTime == null) {
            return _localNow()
        }
        return serverBaseTime.add(_elapsed() - monotonicBaseTime)
    }

    val requestRoundTrip: Duration? get() = _requestRoundTrip

    fun updateFromHeader(value: String?): Boolean {
        val serverTime = parseServerTimeHeader(value) ?: return false
        _serverBaseTime = serverTime
        _monotonicBaseTime = _elapsed()
        _notifyListeners()
        return true
    }

    fun updateRequestRoundTrip(value: Duration): Boolean {
        if (value < Duration.ZERO) return false
        _requestRoundTrip = value
        _notifyListeners()
        return true
    }

    private fun _notifyListeners() {
        for (listener in _listeners.toList()) {
            listener()
        }
    }

    fun addListener(listener: ServerClockListener) {
        _listeners.add(listener)
    }

    fun removeListener(listener: ServerClockListener) {
        _listeners.remove(listener)
    }

    fun dispose() {
        _listeners.clear()
        _stopwatch.stop()
    }

    private fun _elapsed(): Duration = _monotonicNow?.invoke() ?: _stopwatch.elapsed
}

fun parseServerTimeHeader(value: String?): DateTime? {
    val normalized = value?.trim()
    if (normalized == null || normalized.isEmpty()) return null
    return DateTime.tryParse(normalized)?.toUtc()
}
