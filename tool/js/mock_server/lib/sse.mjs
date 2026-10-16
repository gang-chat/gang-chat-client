// Server-sent events for `GET /api/v1/me/stream`.
//
// Wire format (what lib/src/live/live_stream_client.dart parses):
//   event: <type>
//   data: {"type":"<type>","room_id":"<id>","data":{...}}
//   <blank line>
// The `event:` line is what the client switches on; it merges the envelope's
// `room_id` into `data`. A `ready` event opens every connection and a `: ping`
// comment keeps the client's 35s watchdog fed.
import { nextId } from './util.mjs';

export class StreamHub {
  #clients = new Set();
  #heartbeat;

  constructor({ heartbeatMs = 15_000, log = () => {} } = {}) {
    this.log = log;
    this.heartbeatMs = heartbeatMs;
    this.#heartbeat = setInterval(() => this.#writeAll(': ping\n\n'), heartbeatMs);
    this.#heartbeat.unref();
  }

  /** Takes over `res` as an event stream for `userId`. */
  attach(req, res, { userId, sessionId }) {
    res.writeHead(200, {
      'content-type': 'text/event-stream; charset=utf-8',
      'cache-control': 'no-cache, no-transform',
      connection: 'keep-alive',
      'x-accel-buffering': 'no',
    });
    req.socket.setTimeout(0);
    req.socket.setNoDelay(true);
    req.socket.setKeepAlive(true);

    const client = { id: nextId('conn'), userId, sessionId, res };
    this.#clients.add(client);
    const detach = () => {
      if (this.#clients.delete(client)) this.log(`[sse] closed ${client.id} user=${userId}`);
    };
    req.on('close', detach);
    res.on('error', detach);

    this.#write(
      client,
      frame('ready', {
        type: 'ready',
        data: {
          connection_id: client.id,
          user_id: userId,
          server_time: new Date().toISOString(),
          heartbeat_seconds: Math.round(this.heartbeatMs / 1000),
        },
      }),
    );
    this.log(`[sse] open ${client.id} user=${userId}`);
    return client;
  }

  /** Sends one event to every open stream of `userId`. Returns the number of streams written. */
  sendToUser(userId, type, roomId, data) {
    const payload = frame(type, envelope(type, roomId, data));
    let delivered = 0;
    for (const client of this.#clients) {
      if (client.userId !== userId) continue;
      this.#write(client, payload);
      delivered += 1;
    }
    return delivered;
  }

  /**
   * Fans an event out to `userIds`. `data` may be a function of the user id
   * for account-scoped payloads (unread counts, pinned state, ...).
   */
  sendToUsers(userIds, type, roomId, data) {
    let delivered = 0;
    for (const userId of new Set(userIds)) {
      if (!this.isConnected(userId)) continue;
      delivered += this.sendToUser(userId, type, roomId, typeof data === 'function' ? data(userId) : data);
    }
    return delivered;
  }

  isConnected(userId) {
    for (const client of this.#clients) if (client.userId === userId) return true;
    return false;
  }

  connectedUserIds() {
    return new Set([...this.#clients].map((client) => client.userId));
  }

  get connectionCount() {
    return this.#clients.size;
  }

  /** Ends the streams opened with a session (logout / revoke). */
  disconnectSession(sessionId) {
    for (const client of [...this.#clients]) {
      if (client.sessionId !== sessionId) continue;
      this.#clients.delete(client);
      client.res.end();
    }
  }

  close() {
    clearInterval(this.#heartbeat);
    for (const client of this.#clients) client.res.end();
    this.#clients.clear();
  }

  #write(client, text) {
    if (client.res.writableEnded) {
      this.#clients.delete(client);
      return;
    }
    client.res.write(text);
  }

  #writeAll(text) {
    for (const client of [...this.#clients]) this.#write(client, text);
  }
}

function envelope(type, roomId, data) {
  const value = { type };
  if (roomId != null) value.room_id = roomId;
  value.data = data ?? {};
  return value;
}

function frame(type, value) {
  return `event: ${type}\ndata: ${JSON.stringify(value)}\n\n`;
}
