import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../protocol/api_client.dart';

/// A single server-sent event delivered over the `/me/stream` connection.
class LiveEvent {
  const LiveEvent({required this.type, required this.data});

  final String type;
  final Map<String, dynamic> data;
}

enum StreamStatus { connecting, connected, reconnecting, offline }

/// Maintains one long-lived SSE connection to `GET /api/v1/me/stream` and
/// exposes the decoded events as a broadcast stream.
///
/// Responsibilities:
/// - stream-parse the SSE wire format into [LiveEvent]s,
/// - a watchdog that forces a reconnect if no bytes (not even a heartbeat
///   comment) arrive within [_watchdogTimeout],
/// - exponential backoff with jitter on reconnect,
/// - refresh the access token before each (re)connect via
///   [accessTokenProvider],
/// - fire [onReconnect] after a successful reconnect so the UI can pull a
///   fresh snapshot and heal anything missed while disconnected.
class LiveStreamClient extends ChangeNotifier {
  LiveStreamClient({
    required this.apiBaseUrl,
    required this.accessTokenProvider,
    http.Client? httpClient,
  }) : _httpClient = httpClient ?? http.Client();

  final String apiBaseUrl;
  final AccessTokenProvider accessTokenProvider;
  final http.Client _httpClient;

  static const Duration _watchdogTimeout = Duration(seconds: 35);
  static const Duration _connectTimeout = Duration(seconds: 10);
  static const Duration _maxBackoff = Duration(seconds: 30);

  final StreamController<LiveEvent> _controller =
      StreamController<LiveEvent>.broadcast();

  /// Invoked after the stream (re)connects, including the very first connect.
  /// Use it to re-pull snapshots so a gap in delivery while offline heals.
  void Function()? onReconnect;

  Stream<LiveEvent> get events => _controller.stream;

  StreamStatus get status => _status;
  StreamStatus _status = StreamStatus.offline;

  bool _running = false;
  int _attempt = 0;
  bool _retriedAuthThisCycle = false;
  final Random _random = Random();

  StreamSubscription<String>? _lineSub;
  Timer? _watchdog;
  Timer? _reconnectTimer;
  http.Client? _activeClient;

  /// Begins connecting. Idempotent: a second call while running is a no-op.
  Future<void> start() async {
    if (_running) return;
    _running = true;
    _attempt = 0;
    await _connect();
  }

  /// Stops the stream and releases the connection. The client can be
  /// restarted with [start].
  Future<void> stop() async {
    _running = false;
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    await _teardownConnection();
    _setStatus(StreamStatus.offline);
  }

  @override
  void dispose() {
    _running = false;
    _reconnectTimer?.cancel();
    unawaited(_teardownConnection());
    _httpClient.close();
    _controller.close();
    super.dispose();
  }

  Future<void> _connect() async {
    if (!_running) return;
    _setStatus(_attempt == 0 ? StreamStatus.connecting : StreamStatus.reconnecting);

    final client = _httpClient;
    try {
      final token = await accessTokenProvider();
      final req = http.Request('GET', _streamUri());
      req.headers['Accept'] = 'text/event-stream';
      req.headers['Authorization'] = 'Bearer $token';
      req.persistentConnection = true;

      final streamed = await client.send(req).timeout(_connectTimeout);

      if (streamed.statusCode == 401 && !_retriedAuthThisCycle) {
        // Token may have just expired; force a refresh and retry immediately
        // once before falling back to backoff.
        _retriedAuthThisCycle = true;
        await accessTokenProvider(forceRefresh: true);
        await _connect();
        return;
      }
      if (streamed.statusCode != 200) {
        throw http.ClientException('stream returned ${streamed.statusCode}');
      }

      // Connected successfully.
      _attempt = 0;
      _retriedAuthThisCycle = false;
      _activeClient = client;
      _setStatus(StreamStatus.connected);
      _resetWatchdog();
      _listen(streamed);
      // Pull a fresh snapshot to align with current server state — both on the
      // first connect and after any reconnect, since we may have missed events.
      onReconnect?.call();
    } catch (_) {
      _scheduleReconnect();
    }
  }

  void _listen(http.StreamedResponse streamed) {
    var currentEvent = 'message';
    final dataBuffer = StringBuffer();

    _lineSub = streamed.stream
        .transform(utf8.decoder)
        .transform(const LineSplitter())
        .listen(
      (line) {
        _resetWatchdog();
        if (line.isEmpty) {
          // Blank line terminates an event.
          if (dataBuffer.isNotEmpty) {
            _emit(currentEvent, dataBuffer.toString());
          }
          currentEvent = 'message';
          dataBuffer.clear();
          return;
        }
        if (line.startsWith(':')) return; // comment / heartbeat
        if (line.startsWith('event:')) {
          currentEvent = line.substring(6).trim();
        } else if (line.startsWith('data:')) {
          if (dataBuffer.isNotEmpty) dataBuffer.write('\n');
          dataBuffer.write(line.substring(5).trim());
        }
      },
      onError: (_) => _scheduleReconnect(),
      onDone: _scheduleReconnect,
      cancelOnError: true,
    );
  }

  void _emit(String type, String dataJson) {
    if (type == 'ready') return; // connection handshake, not a UI event
    if (dataJson.isEmpty) return;
    Map<String, dynamic>? decoded;
    try {
      final parsed = jsonDecode(dataJson);
      if (parsed is Map<String, dynamic>) decoded = parsed;
    } catch (_) {
      return;
    }
    if (decoded == null) return;
    // Server wraps payloads as the eventbus.Event {type, room_id, data}. The
    // top-level room_id is authoritative for room scoping, while `data` carries
    // the event-specific payload. Build a single map that preserves both:
    //   - start from the envelope's room_id (so room scoping always survives),
    //   - merge the data payload when it's an object,
    //   - otherwise keep the raw payload under 'data' so non-object payloads
    //     (lists/scalars) aren't silently dropped.
    final data = decoded['data'];
    final payload = <String, dynamic>{};
    final roomId = decoded['room_id'];
    if (roomId != null) payload['room_id'] = roomId;
    if (data is Map<String, dynamic>) {
      payload.addAll(data);
    } else if (data != null) {
      payload['data'] = data;
    } else {
      // No nested `data`: the envelope itself is the payload (minus the
      // routing fields we've already captured).
      for (final entry in decoded.entries) {
        if (entry.key == 'type' || entry.key == 'room_id') continue;
        payload[entry.key] = entry.value;
      }
    }
    if (!_controller.isClosed) {
      _controller.add(LiveEvent(type: type, data: payload));
    }
  }

  void _resetWatchdog() {
    _watchdog?.cancel();
    _watchdog = Timer(_watchdogTimeout, () {
      // No bytes for too long: treat the connection as dead and reconnect.
      _scheduleReconnect();
    });
  }

  void _scheduleReconnect() {
    if (!_running) return;
    if (_reconnectTimer != null) return; // already scheduled
    unawaited(_teardownConnection());
    _setStatus(StreamStatus.reconnecting);

    final backoffMs = min(
      _maxBackoff.inMilliseconds,
      (1 << min(_attempt, 5)) * 1000,
    );
    final jitterMs = _random.nextInt(1000);
    _attempt++;
    _reconnectTimer = Timer(Duration(milliseconds: backoffMs + jitterMs), () {
      _reconnectTimer = null;
      unawaited(_connect());
    });
  }

  Future<void> _teardownConnection() async {
    _watchdog?.cancel();
    _watchdog = null;
    final sub = _lineSub;
    _lineSub = null;
    await sub?.cancel();
    // Close only ad-hoc clients; the shared _httpClient is closed in dispose.
    final active = _activeClient;
    _activeClient = null;
    if (active != null && !identical(active, _httpClient)) {
      active.close();
    }
  }

  void _setStatus(StreamStatus next) {
    if (_status == next) return;
    _status = next;
    notifyListeners();
  }

  Uri _streamUri() {
    final base = apiBaseUrl.endsWith('/')
        ? apiBaseUrl.substring(0, apiBaseUrl.length - 1)
        : apiBaseUrl;
    return Uri.parse('$base/me/stream');
  }
}
