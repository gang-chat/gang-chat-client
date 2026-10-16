import { afterEach, describe, expect, test, vi } from 'vitest';

import { delay } from '../../src/lib/async';
import { utf8Encode, type HttpClient, type HttpRequest, type StreamedResponse } from '../../src/platform/http';
import { LineSplitter, LiveEvent, LiveStreamClient, StreamStatus } from '../../src/live/live_stream_client';
import { waitFor } from './live_test_helpers';

/** A response body the test feeds chunk by chunk; ends early when the request is aborted. */
class ControlledBody {
  private readonly _chunks: Uint8Array[] = [];
  private _done = false;
  private _error: unknown = null;
  private _wake: (() => void) | null = null;

  constructor(private readonly _signal: AbortSignal | undefined) {
    _signal?.addEventListener('abort', () => this.end());
  }

  push(chunk: string | Uint8Array): void {
    this._chunks.push(typeof chunk === 'string' ? utf8Encode(chunk) : chunk);
    this._wake?.();
  }

  end(): void {
    this._done = true;
    this._wake?.();
  }

  fail(error: unknown): void {
    this._error = error;
    this._wake?.();
  }

  get aborted(): boolean {
    return this._signal?.aborted ?? false;
  }

  async *stream(): AsyncIterable<Uint8Array> {
    for (;;) {
      if (this._error != null) throw this._error;
      const chunk = this._chunks.shift();
      if (chunk != null) {
        yield chunk;
        continue;
      }
      if (this._done) return;
      await new Promise<void>((resolve) => (this._wake = resolve));
      this._wake = null;
    }
  }
}

class FakeSseClient implements HttpClient {
  readonly requests: HttpRequest[] = [];
  readonly bodies: ControlledBody[] = [];
  statusCodes: number[] = [];
  closed = false;

  async send(request: HttpRequest): Promise<StreamedResponse> {
    this.requests.push(request);
    const body = new ControlledBody(request.signal);
    this.bodies.push(body);
    const statusCode = this.statusCodes.shift() ?? 200;
    return { statusCode, headers: {}, contentLength: null, stream: body.stream() };
  }

  close(): void {
    this.closed = true;
  }
}

function newClient(http: FakeSseClient, tokens: Array<{ forceRefresh: boolean }> = []): LiveStreamClient {
  return new LiveStreamClient({
    apiBaseUrl: 'https://api.example.test/api/v1/',
    accessTokenProvider: async (opts) => {
      tokens.push({ forceRefresh: opts?.forceRefresh ?? false });
      return tokens.length === 1 ? 'token-1' : `token-${tokens.length}`;
    },
    httpClient: http,
  });
}

afterEach(() => {
  vi.useRealTimers();
});

describe('LineSplitter', () => {
  test('splits on LF, CRLF and CR, including terminators split across chunks', () => {
    const splitter = new LineSplitter();
    expect(splitter.add('a\nb\r')).toEqual(['a', 'b']);
    expect(splitter.add('\nc\rd')).toEqual(['c']);
    expect(splitter.add('e\r\n\r\n')).toEqual(['de', '']);
    expect(splitter.add('tail')).toEqual([]);
    expect(splitter.close()).toEqual(['tail']);
  });
});

describe('LiveStreamClient', () => {
  test('connects with bearer auth, reports connected and fires onReconnect', async () => {
    const http = new FakeSseClient();
    const client = newClient(http);
    let reconnects = 0;
    client.onReconnect = () => reconnects++;

    await client.start();

    expect(http.requests).toHaveLength(1);
    expect(http.requests[0]!.method).toBe('GET');
    expect(http.requests[0]!.url).toBe('https://api.example.test/api/v1/me/stream');
    expect(http.requests[0]!.headers).toMatchObject({
      Accept: 'text/event-stream',
      Authorization: 'Bearer token-1',
    });
    expect(client.status).toBe(StreamStatus.connected);
    expect(reconnects).toBe(1);
    await client.stop();
    client.dispose();
  });

  test('parses the event-stream format exactly like the Dart client', async () => {
    const http = new FakeSseClient();
    const client = newClient(http);
    const events: LiveEvent[] = [];
    client.events.listen((event) => events.push(event));
    await client.start();
    const body = http.bodies[0]!;

    // Handshake and heartbeat comments are not UI events.
    body.push('event: ready\ndata: {"ok":true}\n\n: ping\n\n');
    // Envelope with an object payload: room_id + data merged.
    body.push('event: message.created\r\ndata: {"type":"message.created","room_id":"r1",');
    body.push('"data":{"id":"m1","body":"你好"}}\r\n\r\n');
    // Non-object payloads stay under `data`.
    body.push('event: list\ndata: {"room_id":"r2","data":[1,2]}\n\n');
    // No nested data: the envelope minus routing fields.
    body.push('data: {"type":"x","room_id":"r3","user_id":"u1"}\n\n');
    // Multi-line data joins with \n (and each line is trimmed).
    body.push('event: multi\ndata: {"a":\ndata:   1}\n\n');
    // Invalid JSON and non-object JSON are dropped.
    body.push('event: bad\ndata: {nope\n\nevent: scalar\ndata: 42\n\n');
    // A multi-byte character split across chunks.
    const bytes = utf8Encode('event: split\ndata: {"t":"语"}\n\n');
    body.push(bytes.slice(0, 22));
    body.push(bytes.slice(22));

    await waitFor(() => events.length === 5);
    expect(events.map((e) => e.type)).toEqual(['message.created', 'list', 'message', 'multi', 'split']);
    expect(events[0]!.data).toEqual({ room_id: 'r1', id: 'm1', body: '你好' });
    expect(events[1]!.data).toEqual({ room_id: 'r2', data: [1, 2] });
    expect(events[2]!.data).toEqual({ room_id: 'r3', user_id: 'u1' });
    expect(events[3]!.data).toEqual({ a: 1 });
    expect(events[4]!.data).toEqual({ t: '语' });
    await client.stop();
    client.dispose();
  });

  test('a 401 forces one token refresh and retries immediately', async () => {
    const http = new FakeSseClient();
    http.statusCodes = [401, 200];
    const tokens: Array<{ forceRefresh: boolean }> = [];
    const client = newClient(http, tokens);

    await client.start();

    expect(tokens).toEqual([{ forceRefresh: false }, { forceRefresh: true }, { forceRefresh: false }]);
    expect(http.requests).toHaveLength(2);
    expect(http.bodies[0]!.aborted).toBe(true);
    expect(client.status).toBe(StreamStatus.connected);
    await client.stop();
    client.dispose();
  });

  test('a closed stream reconnects with backoff', async () => {
    vi.useFakeTimers();
    const http = new FakeSseClient();
    const client = newClient(http);
    let reconnects = 0;
    client.onReconnect = () => reconnects++;
    await client.start();

    http.bodies[0]!.end();
    await vi.advanceTimersByTimeAsync(0);
    expect(client.status).toBe(StreamStatus.reconnecting);
    expect(http.requests).toHaveLength(1);

    // First backoff is 1s plus up to 1s of jitter.
    await vi.advanceTimersByTimeAsync(2000);
    expect(http.requests).toHaveLength(2);
    expect(client.status).toBe(StreamStatus.connected);
    expect(reconnects).toBe(2);
    await client.stop();
    client.dispose();
  });

  test('the watchdog reconnects a silent stream', async () => {
    vi.useFakeTimers();
    const http = new FakeSseClient();
    const client = newClient(http);
    await client.start();

    await vi.advanceTimersByTimeAsync(34_000);
    http.bodies[0]!.push(': heartbeat\n');
    await vi.advanceTimersByTimeAsync(34_000);
    expect(client.status).toBe(StreamStatus.connected);

    await vi.advanceTimersByTimeAsync(1_500);
    expect(client.status).toBe(StreamStatus.reconnecting);
    expect(http.bodies[0]!.aborted).toBe(true);
    await client.stop();
    client.dispose();
  });

  test('stop goes offline and releases the response; dispose closes an injected client', async () => {
    const http = new FakeSseClient();
    const client = newClient(http);
    await client.start();

    await client.stop();
    await delay(0);

    expect(client.status).toBe(StreamStatus.offline);
    expect(http.bodies[0]!.aborted).toBe(true);
    client.dispose();
    expect(http.closed).toBe(true);
  });
});
