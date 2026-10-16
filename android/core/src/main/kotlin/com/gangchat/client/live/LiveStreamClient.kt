package com.gangchat.client.live

import com.gangchat.client.foundation.ChangeNotifier
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.StreamSubscription
import com.gangchat.client.foundation.Timer
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.asJsonMapOrNull
import com.gangchat.client.foundation.http.ClientException
import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.foundation.http.HttpRequest
import com.gangchat.client.foundation.http.StreamedResponse
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.foundation.timeout
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.foundation.utf8Lines
import com.gangchat.client.protocol.AccessTokenProvider
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** A single server-sent event delivered over the `/me/stream` connection. */
class LiveEvent(val type: String, val data: JsonMap)

enum class StreamStatus { connecting, connected, reconnecting, offline }

/**
 * Maintains one long-lived SSE connection to `GET /api/v1/me/stream` and
 * exposes the decoded events as a broadcast stream.
 *
 * Responsibilities:
 * - stream-parse the SSE wire format into [LiveEvent]s,
 * - a watchdog that forces a reconnect if no bytes (not even a heartbeat
 *   comment) arrive within [_watchdogTimeout],
 * - exponential backoff with jitter on reconnect,
 * - refresh the access token before each (re)connect via
 *   [accessTokenProvider],
 * - fire [onReconnect] after a successful reconnect so the UI can pull a
 *   fresh snapshot and heal anything missed while disconnected.
 */
class LiveStreamClient(
    val apiBaseUrl: String,
    val accessTokenProvider: AccessTokenProvider,
    httpClient: HttpClient? = null,
) : ChangeNotifier() {
    private val _httpClient: HttpClient = httpClient ?: HttpClient()

    private val _controller: StreamController<LiveEvent> = StreamController.broadcast()

    /**
     * Invoked after the stream (re)connects, including the very first connect.
     * Use it to re-pull snapshots so a gap in delivery while offline heals.
     */
    var onReconnect: (() -> Unit)? = null

    val events: Stream<LiveEvent> get() = _controller.stream

    val status: StreamStatus get() = _status
    private var _status: StreamStatus = StreamStatus.offline

    private var _running = false
    private var _attempt = 0
    private var _retriedAuthThisCycle = false
    private val _random: Random = Random.Default

    private var _lineSub: StreamSubscription<String>? = null
    private var _watchdog: Timer? = null
    private var _reconnectTimer: Timer? = null
    private var _activeClient: HttpClient? = null

    /** Begins connecting. Idempotent: a second call while running is a no-op. */
    suspend fun start() {
        if (_running) return
        _running = true
        _attempt = 0
        _connect()
    }

    /**
     * Stops the stream and releases the connection. The client can be
     * restarted with [start].
     */
    suspend fun stop() {
        _running = false
        _reconnectTimer?.cancel()
        _reconnectTimer = null
        _teardownConnection()
        _setStatus(StreamStatus.offline)
    }

    override fun dispose() {
        _running = false
        _reconnectTimer?.cancel()
        unawaited { _teardownConnection() }
        _httpClient.close()
        _controller.close()
        super.dispose()
    }

    private suspend fun _connect() {
        if (!_running) return
        _setStatus(if (_attempt == 0) StreamStatus.connecting else StreamStatus.reconnecting)

        val client = _httpClient
        try {
            val token = accessTokenProvider()
            val req = HttpRequest("GET", _streamUri())
            req.headers["Accept"] = "text/event-stream"
            req.headers["Authorization"] = "Bearer $token"
            req.persistentConnection = true

            val streamed = timeout(_connectTimeout) { client.send(req) }

            if (streamed.statusCode == 401 && !_retriedAuthThisCycle) {
                // Token may have just expired; force a refresh and retry immediately
                // once before falling back to backoff.
                _retriedAuthThisCycle = true
                accessTokenProvider(forceRefresh = true)
                _connect()
                return
            }
            if (streamed.statusCode != 200) {
                throw ClientException("stream returned ${streamed.statusCode}")
            }

            // Connected successfully.
            _attempt = 0
            _retriedAuthThisCycle = false
            _activeClient = client
            _setStatus(StreamStatus.connected)
            _resetWatchdog()
            _listen(streamed)
            // Pull a fresh snapshot to align with current server state — both on the
            // first connect and after any reconnect, since we may have missed events.
            onReconnect?.invoke()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _scheduleReconnect()
        }
    }

    private fun _listen(streamed: StreamedResponse) {
        var currentEvent = "message"
        val dataBuffer = StringBuilder()

        _lineSub =
            streamed.stream
                .utf8Lines()
                .listen(
                    { line ->
                        _resetWatchdog()
                        if (line.isEmpty()) {
                            // Blank line terminates an event.
                            if (dataBuffer.isNotEmpty()) {
                                _emit(currentEvent, dataBuffer.toString())
                            }
                            currentEvent = "message"
                            dataBuffer.setLength(0)
                            return@listen
                        }
                        if (line.startsWith(":")) return@listen // comment / heartbeat
                        if (line.startsWith("event:")) {
                            currentEvent = line.substring(6).trim()
                        } else if (line.startsWith("data:")) {
                            if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                            dataBuffer.append(line.substring(5).trim())
                        }
                    },
                    onError = { _scheduleReconnect() },
                    onDone = ::_scheduleReconnect,
                    cancelOnError = true,
                )
    }

    private fun _emit(type: String, dataJson: String) {
        if (type == "ready") return // connection handshake, not a UI event
        if (dataJson.isEmpty()) return
        var decoded: JsonMap? = null
        try {
            val parsed = jsonDecode(dataJson)
            if (parsed is Map<*, *>) decoded = parsed.asJsonMapOrNull()
        } catch (e: Exception) {
            return
        }
        if (decoded == null) return
        // Server wraps payloads as the eventbus.Event {type, room_id, data}. The
        // top-level room_id is authoritative for room scoping, while `data` carries
        // the event-specific payload. Build a single map that preserves both:
        //   - start from the envelope's room_id (so room scoping always survives),
        //   - merge the data payload when it's an object,
        //   - otherwise keep the raw payload under 'data' so non-object payloads
        //     (lists/scalars) aren't silently dropped.
        val data = decoded["data"]
        val payload = LinkedHashMap<String, Any?>()
        val roomId = decoded["room_id"]
        if (roomId != null) payload["room_id"] = roomId
        if (data is Map<*, *>) {
            payload.putAll(data.asJsonMapOrNull()!!)
        } else if (data != null) {
            payload["data"] = data
        } else {
            // No nested `data`: the envelope itself is the payload (minus the
            // routing fields we've already captured).
            for ((key, value) in decoded) {
                if (key == "type" || key == "room_id") continue
                payload[key] = value
            }
        }
        if (!_controller.isClosed) {
            _controller.add(LiveEvent(type = type, data = payload))
        }
    }

    private fun _resetWatchdog() {
        _watchdog?.cancel()
        _watchdog =
            Timer(_watchdogTimeout) {
                // No bytes for too long: treat the connection as dead and reconnect.
                _scheduleReconnect()
            }
    }

    private fun _scheduleReconnect() {
        if (!_running) return
        if (_reconnectTimer != null) return // already scheduled
        unawaited { _teardownConnection() }
        _setStatus(StreamStatus.reconnecting)

        val backoffMs =
            min(
                _maxBackoff.inWholeMilliseconds,
                (1L shl min(_attempt, 5)) * 1000,
            )
        val jitterMs = _random.nextInt(1000)
        _attempt++
        _reconnectTimer =
            Timer((backoffMs + jitterMs).milliseconds) {
                _reconnectTimer = null
                unawaited { _connect() }
            }
    }

    private suspend fun _teardownConnection() {
        _watchdog?.cancel()
        _watchdog = null
        val sub = _lineSub
        _lineSub = null
        sub?.cancel()
        // Close only ad-hoc clients; the shared _httpClient is closed in dispose.
        val active = _activeClient
        _activeClient = null
        if (active != null && active !== _httpClient) {
            active.close()
        }
    }

    private fun _setStatus(next: StreamStatus) {
        if (_status == next) return
        _status = next
        notifyListeners()
    }

    private fun _streamUri(): Uri {
        val base =
            if (apiBaseUrl.endsWith("/")) apiBaseUrl.substring(0, apiBaseUrl.length - 1) else apiBaseUrl
        return Uri.parse("$base/me/stream")
    }

    companion object {
        private val _watchdogTimeout: Duration = 35.seconds
        private val _connectTimeout: Duration = 10.seconds
        private val _maxBackoff: Duration = 30.seconds
    }
}
