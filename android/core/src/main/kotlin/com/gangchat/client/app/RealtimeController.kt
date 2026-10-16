package com.gangchat.client.app

import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.StreamSubscription
import com.gangchat.client.live.LiveEvent
import com.gangchat.client.live.LiveStreamClient
import com.gangchat.client.live.StreamStatus
import com.gangchat.client.protocol.AccessTokenProvider

class RealtimeEvent(val type: String, val data: JsonMap)

enum class RealtimeConnectionStatus { connecting, connected, reconnecting, offline }

/**
 * Dart's `abstract class RealtimeService` (only ever `implements`-ed), so an
 * interface; [onReconnect] is the Dart field.
 */
interface RealtimeService {
    var onReconnect: (() -> Unit)?

    val events: Stream<RealtimeEvent>

    val status: RealtimeConnectionStatus

    val statusChanges: Stream<RealtimeConnectionStatus>

    suspend fun start()

    suspend fun stop()

    fun dispose()
}

class RealtimeController(
    apiBaseUrl: String,
    accessTokenProvider: AccessTokenProvider,
) : RealtimeService {
    private val _client: LiveStreamClient =
        LiveStreamClient(
            apiBaseUrl = apiBaseUrl,
            accessTokenProvider = accessTokenProvider,
        )
    private val _events: StreamController<RealtimeEvent> = StreamController.broadcast()
    private val _statusChanges: StreamController<RealtimeConnectionStatus> = StreamController.broadcast()
    private val _sourceSubscription: StreamSubscription<LiveEvent>
    private var _status: RealtimeConnectionStatus

    override var onReconnect: (() -> Unit)? = null

    init {
        _status = _connectionStatusFromStreamStatus(_client.status)
        _client.addListener(::_handleClientStatusChanged)
        _client.onReconnect = { onReconnect?.invoke() }
        _sourceSubscription =
            _client.events.listen({ event ->
                if (_events.isClosed) return@listen
                _events.add(RealtimeEvent(type = event.type, data = event.data))
            })
    }

    override val events: Stream<RealtimeEvent> get() = _events.stream

    override val status: RealtimeConnectionStatus get() = _status

    override val statusChanges: Stream<RealtimeConnectionStatus> get() = _statusChanges.stream

    override suspend fun start() = _client.start()

    override suspend fun stop() = _client.stop()

    private fun _handleClientStatusChanged() {
        val next = _connectionStatusFromStreamStatus(_client.status)
        if (_status == next) return
        _status = next
        if (!_statusChanges.isClosed) _statusChanges.add(next)
    }

    override fun dispose() {
        _client.removeListener(::_handleClientStatusChanged)
        _sourceSubscription.cancel()
        _client.dispose()
        _events.close()
        _statusChanges.close()
    }
}

private fun _connectionStatusFromStreamStatus(status: StreamStatus): RealtimeConnectionStatus {
    return when (status) {
        StreamStatus.connecting -> RealtimeConnectionStatus.connecting
        StreamStatus.connected -> RealtimeConnectionStatus.connected
        StreamStatus.reconnecting -> RealtimeConnectionStatus.reconnecting
        StreamStatus.offline -> RealtimeConnectionStatus.offline
    }
}
