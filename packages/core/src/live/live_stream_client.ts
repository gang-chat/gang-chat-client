/**
 * Port of `lib/src/live/live_stream_client.dart`: the `/me/stream` SSE
 * connection, carried over the [HttpClient] abstraction.
 */
import { Timer, TimeoutException, unawaited, withTimeout } from '../lib/async';
import { ChangeNotifier } from '../lib/change_notifier';
import { StreamController, type EventStream } from '../lib/event_stream';
import { isJsonMap, type JsonMap } from '../lib/json';
import { ClientException, type HttpClient, type StreamedResponse } from '../platform/http';
import { platform } from '../platform/platform';
import type { AccessTokenProvider } from '../protocol/api_client';

/** A single server-sent event delivered over the `/me/stream` connection. */
export class LiveEvent {
  constructor(opts: { type: string; data: JsonMap }) {
    this.type = opts.type;
    this.data = opts.data;
  }

  readonly type: string;
  readonly data: JsonMap;
}

export enum StreamStatus {
  connecting = 'connecting',
  connected = 'connected',
  reconnecting = 'reconnecting',
  offline = 'offline',
}

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
export class LiveStreamClient extends ChangeNotifier {
  constructor(opts: { apiBaseUrl: string; accessTokenProvider: AccessTokenProvider; httpClient?: HttpClient }) {
    super();
    this.apiBaseUrl = opts.apiBaseUrl;
    this.accessTokenProvider = opts.accessTokenProvider;
    // PORT: Dart created its own `http.Client()` when none was injected and
    // closed it in dispose. The default here is the app-wide platform client,
    // which must outlive this stream, so only an injected client is closed.
    this._httpClient = opts.httpClient ?? platform().http;
    this._closesHttpClient = opts.httpClient != null;
  }

  readonly apiBaseUrl: string;
  readonly accessTokenProvider: AccessTokenProvider;
  private readonly _httpClient: HttpClient;
  private readonly _closesHttpClient: boolean;

  static readonly _watchdogTimeout = 35_000;
  static readonly _connectTimeout = 10_000;
  static readonly _maxBackoff = 30_000;

  private readonly _controller = new StreamController<LiveEvent>();

  /**
   * Invoked after the stream (re)connects, including the very first connect.
   * Use it to re-pull snapshots so a gap in delivery while offline heals.
   */
  onReconnect: (() => void) | null = null;

  get events(): EventStream<LiveEvent> {
    return this._controller.stream;
  }

  get status(): StreamStatus {
    return this._status;
  }

  private _status = StreamStatus.offline;

  private _running = false;
  private _attempt = 0;
  private _retriedAuthThisCycle = false;

  private _lineSub: _LineSubscription | null = null;
  private _watchdog: Timer | null = null;
  private _reconnectTimer: Timer | null = null;
  /** Aborts the in-flight request / response body of the current attempt. */
  private _activeRequest: AbortController | null = null;

  /** Begins connecting. Idempotent: a second call while running is a no-op. */
  async start(): Promise<void> {
    if (this._running) return;
    this._running = true;
    this._attempt = 0;
    await this._connect();
  }

  /**
   * Stops the stream and releases the connection. The client can be
   * restarted with [start].
   */
  async stop(): Promise<void> {
    this._running = false;
    this._reconnectTimer?.cancel();
    this._reconnectTimer = null;
    await this._teardownConnection();
    this._setStatus(StreamStatus.offline);
  }

  override dispose(): void {
    this._running = false;
    this._reconnectTimer?.cancel();
    unawaited(this._teardownConnection());
    if (this._closesHttpClient) this._httpClient.close?.();
    this._controller.close();
    super.dispose();
  }

  private async _connect(): Promise<void> {
    if (!this._running) return;
    this._setStatus(this._attempt === 0 ? StreamStatus.connecting : StreamStatus.reconnecting);

    const client = this._httpClient;
    const request = new AbortController();
    this._activeRequest?.abort();
    this._activeRequest = request;
    try {
      const token = await this.accessTokenProvider();
      const streamed = await withTimeout(
        client.send({
          method: 'GET',
          url: this._streamUri(),
          headers: {
            Accept: 'text/event-stream',
            Authorization: `Bearer ${token}`,
          },
          signal: request.signal,
        }),
        LiveStreamClient._connectTimeout,
      );

      if (streamed.statusCode === 401 && !this._retriedAuthThisCycle) {
        // Token may have just expired; force a refresh and retry immediately
        // once before falling back to backoff.
        this._retriedAuthThisCycle = true;
        request.abort();
        await this.accessTokenProvider({ forceRefresh: true });
        await this._connect();
        return;
      }
      if (streamed.statusCode !== 200) {
        throw new ClientException(`stream returned ${streamed.statusCode}`);
      }
      if (!this._running || this._activeRequest !== request) {
        request.abort();
        return;
      }

      // Connected successfully.
      this._attempt = 0;
      this._retriedAuthThisCycle = false;
      this._setStatus(StreamStatus.connected);
      this._resetWatchdog();
      this._listen(streamed, request);
      // Pull a fresh snapshot to align with current server state — both on the
      // first connect and after any reconnect, since we may have missed events.
      this.onReconnect?.();
    } catch (error) {
      if (error instanceof TimeoutException) request.abort();
      this._scheduleReconnect();
    }
  }

  private _listen(streamed: StreamedResponse, request: AbortController): void {
    let currentEvent = 'message';
    let dataBuffer = '';

    const onLine = (line: string): void => {
      this._resetWatchdog();
      if (line.length === 0) {
        // Blank line terminates an event.
        if (dataBuffer.length > 0) {
          this._emit(currentEvent, dataBuffer);
        }
        currentEvent = 'message';
        dataBuffer = '';
        return;
      }
      if (line.startsWith(':')) return; // comment / heartbeat
      if (line.startsWith('event:')) {
        currentEvent = line.substring(6).trim();
      } else if (line.startsWith('data:')) {
        if (dataBuffer.length > 0) dataBuffer += '\n';
        dataBuffer += line.substring(5).trim();
      }
    };

    this._lineSub = _listenLines(streamed.stream, request, {
      onLine,
      onError: () => this._scheduleReconnect(),
      onDone: () => this._scheduleReconnect(),
    });
  }

  private _emit(type: string, dataJson: string): void {
    if (type === 'ready') return; // connection handshake, not a UI event
    if (dataJson.length === 0) return;
    let decoded: JsonMap | null = null;
    try {
      const parsed: unknown = JSON.parse(dataJson);
      if (isJsonMap(parsed)) decoded = parsed;
    } catch {
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
    const data = decoded['data'];
    const payload: JsonMap = {};
    const roomId = decoded['room_id'];
    if (roomId != null) payload['room_id'] = roomId;
    if (isJsonMap(data)) {
      Object.assign(payload, data);
    } else if (data != null) {
      payload['data'] = data;
    } else {
      // No nested `data`: the envelope itself is the payload (minus the
      // routing fields we've already captured).
      for (const [key, value] of Object.entries(decoded)) {
        if (key === 'type' || key === 'room_id') continue;
        payload[key] = value;
      }
    }
    if (!this._controller.isClosed) {
      this._controller.add(new LiveEvent({ type, data: payload }));
    }
  }

  private _resetWatchdog(): void {
    this._watchdog?.cancel();
    this._watchdog = new Timer(LiveStreamClient._watchdogTimeout, () => {
      // No bytes for too long: treat the connection as dead and reconnect.
      this._scheduleReconnect();
    });
  }

  private _scheduleReconnect(): void {
    if (!this._running) return;
    if (this._reconnectTimer != null) return; // already scheduled
    unawaited(this._teardownConnection());
    this._setStatus(StreamStatus.reconnecting);

    const backoffMs = Math.min(LiveStreamClient._maxBackoff, (1 << Math.min(this._attempt, 5)) * 1000);
    const jitterMs = Math.floor(Math.random() * 1000);
    this._attempt++;
    this._reconnectTimer = new Timer(backoffMs + jitterMs, () => {
      this._reconnectTimer = null;
      unawaited(this._connect());
    });
  }

  private async _teardownConnection(): Promise<void> {
    this._watchdog?.cancel();
    this._watchdog = null;
    const sub = this._lineSub;
    this._lineSub = null;
    sub?.cancel();
    // Release the response body of the dropped attempt.
    const active = this._activeRequest;
    this._activeRequest = null;
    active?.abort();
  }

  private _setStatus(next: StreamStatus): void {
    if (this._status === next) return;
    this._status = next;
    this.notifyListeners();
  }

  private _streamUri(): string {
    const base = this.apiBaseUrl.endsWith('/') ? this.apiBaseUrl.substring(0, this.apiBaseUrl.length - 1) : this.apiBaseUrl;
    return `${base}/me/stream`;
  }
}

interface _LineSubscription {
  cancel(): void;
}

/**
 * `stream.transform(utf8.decoder).transform(const LineSplitter()).listen(...)`
 * with `cancelOnError: true`: UTF-8 decoding that tolerates multi-byte
 * characters split across chunks, and `\n` / `\r\n` / `\r` line breaks that
 * may themselves be split across chunks. A cancelled subscription never calls
 * back again.
 */
function _listenLines(
  stream: AsyncIterable<Uint8Array>,
  request: AbortController,
  handlers: { onLine: (line: string) => void; onError: (error: unknown) => void; onDone: () => void },
): _LineSubscription {
  let cancelled = false;
  const decoder = new TextDecoder('utf-8', { fatal: true });
  const splitter = new LineSplitter();

  const run = async (): Promise<void> => {
    try {
      for await (const chunk of stream) {
        if (cancelled) return;
        for (const line of splitter.add(decoder.decode(chunk, { stream: true }))) {
          if (cancelled) return;
          handlers.onLine(line);
        }
      }
      if (cancelled) return;
      for (const line of splitter.add(decoder.decode())) {
        if (cancelled) return;
        handlers.onLine(line);
      }
      for (const line of splitter.close()) {
        if (cancelled) return;
        handlers.onLine(line);
      }
      if (!cancelled) handlers.onDone();
    } catch (error) {
      if (!cancelled) {
        cancelled = true;
        handlers.onError(error);
      }
    }
  };
  unawaited(run());

  return {
    cancel: () => {
      cancelled = true;
      request.abort();
    },
  };
}

/**
 * Incremental port of Dart's `LineSplitter`: lines end at `\n`, `\r\n` or a
 * lone `\r`; the terminators are not included. [close] flushes a trailing
 * line without a terminator.
 */
export class LineSplitter {
  private _carry = '';
  private _skipLeadingLf = false;

  add(text: string): string[] {
    const lines: string[] = [];
    let start = 0;
    let index = 0;
    if (this._skipLeadingLf && text.length > 0) {
      this._skipLeadingLf = false;
      if (text.charCodeAt(0) === 0x0a) {
        start = 1;
        index = 1;
      }
    }
    for (; index < text.length; index++) {
      const code = text.charCodeAt(index);
      if (code !== 0x0a && code !== 0x0d) continue;
      lines.push(this._carry + text.substring(start, index));
      this._carry = '';
      if (code === 0x0d) {
        if (index + 1 < text.length) {
          if (text.charCodeAt(index + 1) === 0x0a) index++;
        } else {
          // A `\r` at the end of a chunk may be the first half of `\r\n`.
          this._skipLeadingLf = true;
        }
      }
      start = index + 1;
    }
    this._carry += text.substring(start);
    return lines;
  }

  close(): string[] {
    const carry = this._carry;
    this._carry = '';
    this._skipLeadingLf = false;
    return carry.length > 0 ? [carry] : [];
  }
}
