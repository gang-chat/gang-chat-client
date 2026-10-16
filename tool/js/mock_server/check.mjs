#!/usr/bin/env node
// Self-check for the mock server: spawns server.mjs on a random port and walks
// the client flow (login -> rooms -> messages -> SSE ready -> send -> SSE
// event, plus live, uploads, refresh, ...). Response shapes are validated
// against the fields lib/src/protocol/models.dart reads with non-null casts.
//
//   node tool/js/mock_server/check.mjs
//
// Exits non-zero on the first failure and prints the server log.
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { encodePng } from './lib/media.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const WAIT_MS = 10_000;

// ---------------------------------------------------------------------------
// Shape specs. 'type' = required non-null; '?type' = may be null/absent;
// nested objects are required; opt(spec) makes one optional; [spec] = array.

const opt = (spec) => ({ __optional: spec });
const USER_SUMMARY = { id: 'string', username: 'string', display_name: '?string', avatar_url: '?string', default_avatar_key: '?string', is_online: '?bool' };
const CURRENT_USER = { id: 'string', username: 'string', display_name: '?string', email_verified: '?bool', created_at: '?date', is_superuser: '?bool' };
const AUTH_SESSION = { access_token: 'string', refresh_token: 'string', access_token_expires_at: 'date', user: CURRENT_USER };
const LAST_MESSAGE = { id: 'string', sender_display_name: 'string', body_preview: 'string', created_at: 'date' };
const ROOM_CARD = { id: 'string', name: 'string', member_count: 'int', unread_count: '?int', live_participant_count: '?int', live_avatar_preview: [USER_SUMMARY], last_message: opt(LAST_MESSAGE), updated_at: 'date' };
const LIVE_PARTICIPANT = { live_session_id: 'string', user: USER_SUMMARY, joined_at: 'date', mic_muted: 'bool', camera_on: 'bool', screen_sharing: 'bool', connection_state: 'string', screen_viewers: [USER_SUMMARY] };
const LIVE_STATE = { room_id: 'string', participant_count: 'int', participants: [LIVE_PARTICIPANT], updated_at: 'date' };
const ROOM_DETAIL = { id: 'string', name: 'string', member_count: 'int', my_membership: { joined_at: 'date', role: 'string' }, live: LIVE_STATE, created_at: 'date', updated_at: 'date' };
const ASSET = { id: 'string', url: 'string', mime_type: '?string', size_bytes: '?int' };
const ATTACHMENT = { type: '?string', asset: opt(ASSET), user: opt(USER_SUMMARY), actor: opt(USER_SUMMARY), target: opt(USER_SUMMARY) };
const QUOTE = { message_id: 'string', created_at: 'date', preview_attachment: opt(ATTACHMENT) };
const MESSAGE = { id: 'string', room_id: 'string', sender: USER_SUMMARY, client_message_id: 'string', type: '?string', body: '?string', created_at: 'date', attachments: [ATTACHMENT], mentions: 'array', quotes: [QUOTE], quote: opt(QUOTE) };
const MESSAGE_PAGE = { messages: [MESSAGE], has_more: 'bool', next_before: '?string' };
const PUBLIC_ROOM = { id: 'string', name: 'string', join_state: '?string' };
const MEMBER = { user: USER_SUMMARY, role: '?string', joined_at: '?date' };
const LIVE_JOIN = { livekit: { server_url: 'string', token: 'string', token_expires_at: 'date', room_name: 'string' }, participant: LIVE_PARTICIPANT, live: LIVE_STATE };
const SESSION = { id: 'string', created_at: 'int', last_used_at: 'int', expires_at: 'int', is_current: 'bool' };
const STICKER_PACK = { id: 'string', stickers: [{ id: 'string', asset: ASSET }] };
const MUSIC_BOX = { enabled: 'bool', revision: 'int', playback: { state: 'string', current_item_id: 'string', position_ms: 'int' }, queue: [{ id: 'string', title: 'string', duration_ms: 'int' }] };
const ERROR = { error: { code: 'string', message: 'string', request_id: 'string' } };

const SCALARS = {
  string: (v) => typeof v === 'string',
  int: (v) => Number.isInteger(v),
  bool: (v) => typeof v === 'boolean',
  date: (v) => typeof v === 'string' && !Number.isNaN(Date.parse(v)),
  array: (v) => Array.isArray(v),
  object: (v) => v !== null && typeof v === 'object',
};

function validate(value, spec, where) {
  if (spec?.__optional) {
    if (value == null) return;
    return validate(value, spec.__optional, where);
  }
  if (typeof spec === 'string') {
    const optional = spec.startsWith('?');
    const type = optional ? spec.slice(1) : spec;
    if (value == null) {
      if (optional) return;
      throw new Error(`${where}: required ${type} is missing`);
    }
    if (!SCALARS[type](value)) throw new Error(`${where}: expected ${type}, got ${JSON.stringify(value)?.slice(0, 80)}`);
    return;
  }
  if (Array.isArray(spec)) {
    if (!Array.isArray(value)) throw new Error(`${where}: expected array`);
    value.forEach((item, index) => validate(item, spec[0], `${where}[${index}]`));
    return;
  }
  if (value == null || typeof value !== 'object') throw new Error(`${where}: expected object, got ${JSON.stringify(value)}`);
  for (const [key, child] of Object.entries(spec)) validate(value[key], child, `${where}.${key}`);
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

// ---------------------------------------------------------------------------
// Server process, HTTP and SSE helpers

let serverLog = '';

function startServer() {
  const child = spawn(
    process.execPath,
    [path.join(here, 'server.mjs'), '--port', '0', '--chatter-interval', '2', '--heartbeat', '1'],
    { stdio: ['ignore', 'pipe', 'pipe'] },
  );
  const origin = new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('server did not start within 20s')), 20_000);
    child.stdout.on('data', (chunk) => {
      serverLog += chunk;
      const match = /listening on (http:\/\/\S+)/.exec(serverLog);
      if (match) {
        clearTimeout(timer);
        resolve(match[1]);
      }
    });
    child.stderr.on('data', (chunk) => {
      serverLog += chunk;
    });
    child.on('exit', (code) => {
      clearTimeout(timer);
      reject(new Error(`server exited with code ${code}`));
    });
  });
  return { child, origin };
}

function makeApi(apiBase) {
  return async function api(method, route, { token, body, expect, raw = false, headers = {} } = {}) {
    const init = { method, headers: { accept: 'application/json', ...headers } };
    if (token) init.headers.authorization = `Bearer ${token}`;
    if (body instanceof FormData) init.body = body;
    else if (body !== undefined) {
      init.headers['content-type'] = 'application/json; charset=utf-8';
      init.body = JSON.stringify(body);
    }
    const response = await fetch(`${apiBase}${route}`, init);
    if (raw) return response;
    const text = await response.text();
    const json = text ? JSON.parse(text) : null;
    const ok = expect === undefined ? response.ok : response.status === expect;
    if (!ok) throw new Error(`${method} ${route}: HTTP ${response.status} ${text.slice(0, 300)}`);
    return { status: response.status, json, headers: response.headers };
  };
}

/** Parses the stream exactly like lib/src/live/live_stream_client.dart. */
class SseClient {
  events = [];
  pings = 0;
  #waiters = new Set();
  #abort = new AbortController();

  async open(url, token) {
    const response = await fetch(url, {
      headers: { accept: 'text/event-stream', authorization: `Bearer ${token}` },
      signal: this.#abort.signal,
    });
    assert(response.status === 200, `stream returned ${response.status}`);
    assert(response.headers.get('content-type')?.startsWith('text/event-stream'), 'stream content-type is not text/event-stream');
    this.#pump(response.body.getReader()).catch(() => {});
  }

  async #pump(reader) {
    const decoder = new TextDecoder();
    let buffer = '';
    let type = 'message';
    let data = '';
    for (;;) {
      const { value, done } = await reader.read();
      if (done) return;
      buffer += decoder.decode(value, { stream: true });
      let newline;
      while ((newline = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, newline).replace(/\r$/, '');
        buffer = buffer.slice(newline + 1);
        if (line === '') {
          if (data) this.#emit(type, data);
          type = 'message';
          data = '';
        } else if (line.startsWith(':')) {
          this.pings += 1;
        } else if (line.startsWith('event:')) {
          type = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          data += (data ? '\n' : '') + line.slice(5).trim();
        }
      }
    }
  }

  #emit(type, json) {
    const envelope = JSON.parse(json);
    // Same flattening as LiveStreamClient._emit: envelope room_id + data fields.
    const payload = { ...(envelope.room_id ? { room_id: envelope.room_id } : {}), ...(envelope.data ?? {}) };
    const event = { type, envelope, payload };
    this.events.push(event);
    for (const waiter of this.#waiters) waiter(event);
  }

  /** Resolves with the first event (already received or upcoming) matching `predicate`. */
  waitFor(description, predicate, timeoutMs = WAIT_MS) {
    const seen = this.events.find(predicate);
    if (seen) return Promise.resolve(seen);
    return new Promise((resolve, reject) => {
      const waiter = (event) => {
        if (!predicate(event)) return;
        cleanup();
        resolve(event);
      };
      const timer = setTimeout(() => {
        cleanup();
        reject(new Error(`no SSE event "${description}" within ${timeoutMs}ms (got: ${this.events.map((e) => e.type).join(', ')})`));
      }, timeoutMs);
      const cleanup = () => {
        clearTimeout(timer);
        this.#waiters.delete(waiter);
      };
      this.#waiters.add(waiter);
    });
  }

  close() {
    this.#abort.abort();
  }
}

// ---------------------------------------------------------------------------
// The checks

async function runChecks(origin) {
  const apiBase = `${origin}/api/v1`;
  const api = makeApi(apiBase);
  const results = [];
  const step = async (name, fn) => {
    const startedAt = Date.now();
    try {
      const detail = await fn();
      results.push(name);
      console.log(`PASS  ${name}${detail ? ` — ${detail}` : ''} (${Date.now() - startedAt}ms)`);
    } catch (error) {
      console.log(`FAIL  ${name}\n      ${error.message}`);
      throw error;
    }
  };
  const ctx = {};
  const sse = new SseClient();

  try {
    await step('CORS preflight and auth required', async () => {
      const preflight = await api('OPTIONS', '/rooms', { raw: true, headers: { origin: 'http://localhost:5173', 'access-control-request-method': 'GET' } });
      assert(preflight.status === 204, `preflight status ${preflight.status}`);
      assert(preflight.headers.get('access-control-allow-origin') === 'http://localhost:5173', 'missing allow-origin');
      const { json } = await api('GET', '/me', { expect: 401 });
      validate(json, ERROR, 'error');
      return `401 ${json.error.code}`;
    });

    await step('login rejects a wrong password', async () => {
      const { json } = await api('POST', '/auth/login', { body: { login: 'demo', password: 'nope' }, expect: 401 });
      validate(json, ERROR, 'error');
      assert(json.error.code === 'invalid_credentials', `code ${json.error.code}`);
    });

    await step('login demo / demo1234', async () => {
      const { json, headers } = await api('POST', '/auth/login', { body: { login: 'demo', password: 'demo1234' } });
      validate(json, AUTH_SESSION, 'auth');
      const serverTime = headers.get('x-gang-server-time');
      assert(serverTime && !Number.isNaN(Date.parse(serverTime)), 'x-gang-server-time header missing or invalid');
      ctx.token = json.access_token;
      ctx.refreshToken = json.refresh_token;
      const me = (await api('GET', '/me', { token: ctx.token })).json;
      validate(me, CURRENT_USER, 'me');
      assert(me.username === 'demo', `me.username ${me.username}`);
      ctx.me = me;
      return `user ${me.id}, server time ${serverTime}`;
    });

    await step('email-shaped login creates an account', async () => {
      const { json } = await api('POST', '/auth/login', { body: { login: 'smoke.tester@example.com', password: 'anything' } });
      validate(json, AUTH_SESSION, 'auth');
      const rooms = (await api('GET', '/rooms', { token: json.access_token })).json.rooms;
      assert(rooms.length > 0, 'new account has no rooms');
      return `${json.user.username}, ${rooms.length} rooms`;
    });

    await step('room list', async () => {
      const { json } = await api('GET', '/rooms?limit=50', { token: ctx.token });
      validate(json, { rooms: [ROOM_CARD], next_cursor: '?string' }, 'rooms');
      const rooms = json.rooms;
      assert(rooms.length >= 6, `expected >= 6 rooms, got ${rooms.length}`);
      assert(rooms.some((room) => room.is_pinned), 'no pinned room');
      assert(rooms.some((room) => room.unread_count > 0), 'no room with unread messages');
      assert(rooms.some((room) => room.live_participant_count > 0), 'no room with live participants');
      const paged = (await api('GET', '/rooms?limit=2', { token: ctx.token })).json;
      assert(paged.rooms.length === 2 && paged.next_cursor, 'paging: expected next_cursor');
      const next = (await api('GET', `/rooms?limit=2&cursor=${paged.next_cursor}`, { token: ctx.token })).json;
      assert(next.rooms[0].id === rooms[2].id, 'paging: second page does not continue the first');
      ctx.rooms = rooms;
      ctx.roomId = rooms[0].id;
      return rooms.map((room) => `${room.name}${room.unread_count ? ` (${room.unread_count})` : ''}`).join(', ');
    });

    await step('room detail', async () => {
      const { json } = await api('GET', `/rooms/${ctx.roomId}`, { token: ctx.token });
      validate(json.room, ROOM_DETAIL, 'room');
      return `${json.room.name}: role ${json.room.my_membership.role}, ${json.room.live.participant_count} in live`;
    });

    await step('messages for every room (all kinds)', async () => {
      const kinds = new Set();
      let total = 0;
      for (const room of ctx.rooms) {
        const { json } = await api('GET', `/rooms/${room.id}/messages?limit=50`, { token: ctx.token });
        validate(json, MESSAGE_PAGE, `messages(${room.id})`);
        total += json.messages.length;
        for (const message of json.messages) {
          kinds.add(message.type);
          if (message.is_recalled) kinds.add('recalled');
          if (message.quotes.length) kinds.add('quote');
          if (message.mentions.length) kinds.add('mention');
          if (/https?:\/\//.test(message.body)) kinds.add('link');
          for (const attachment of message.attachments) {
            if (attachment.asset?.mime_type?.startsWith('image/')) kinds.add('image');
            ctx.imageUrl ??= attachment.asset?.mime_type === 'image/png' ? attachment.asset.url : undefined;
            ctx.voiceUrl ??= attachment.type === 'audio' ? attachment.asset.url : undefined;
          }
        }
      }
      for (const kind of ['text', 'file', 'image', 'audio', 'sticker', 'system', 'music_track', 'recalled', 'quote', 'mention', 'link']) {
        assert(kinds.has(kind), `no "${kind}" message in the first pages`);
      }
      const first = (await api('GET', `/rooms/${ctx.roomId}/messages?limit=50`, { token: ctx.token })).json;
      assert(first.has_more && first.next_before, 'first room should have more than 50 messages');
      const older = (await api('GET', `/rooms/${ctx.roomId}/messages?limit=50&before=${first.next_before}`, { token: ctx.token })).json;
      validate(older, MESSAGE_PAGE, 'older page');
      assert(older.messages.length > 0 && older.messages.at(-1).created_at <= first.messages[0].created_at, 'older page is not older');
      return `${total} messages; kinds: ${[...kinds].sort().join(', ')}`;
    });

    await step('generated assets are served (image + ranged voice)', async () => {
      const image = await fetch(ctx.imageUrl);
      assert(image.status === 200 && image.headers.get('content-type') === 'image/png', `image: ${image.status}`);
      const voice = await fetch(ctx.voiceUrl, { headers: { range: 'bytes=0-43' } });
      assert(voice.status === 206 && (await voice.arrayBuffer()).byteLength === 44, `voice range: ${voice.status}`);
      return 'png 200, wav 206';
    });

    await step('SSE stream sends ready', async () => {
      await sse.open(`${apiBase}/me/stream`, ctx.token);
      const ready = await sse.waitFor('ready', (event) => event.type === 'ready');
      return `connection ${ready.envelope.data.connection_id}`;
    });

    await step('send message -> echo + room_updated over SSE', async () => {
      const clientMessageId = `check_${Date.now()}`;
      const { status, json } = await api('POST', `/rooms/${ctx.roomId}/messages`, {
        token: ctx.token,
        body: { client_message_id: clientMessageId, body: 'hello from check.mjs' },
        headers: { 'idempotency-key': clientMessageId },
      });
      validate(json.message, MESSAGE, 'message');
      assert(status === 201 && json.message.body === 'hello from check.mjs', 'message not echoed');
      assert(json.message.sender.id === ctx.me.id, 'wrong sender');
      ctx.sent = json.message;
      const event = await sse.waitFor(
        'room_updated for the sent message',
        (e) => e.type === 'room_updated' && e.payload.last_message?.id === json.message.id,
      );
      validate(event.payload, ROOM_CARD, 'room_updated');
      // The sender's own sessions get `message_sent` so the client does not ding for its own message.
      assert(event.payload.room_id === ctx.roomId && event.payload.update_reason === 'message_sent', 'bad room_updated payload');
      const retry = await api('POST', `/rooms/${ctx.roomId}/messages`, { token: ctx.token, body: { client_message_id: clientMessageId, body: 'hello from check.mjs' } });
      assert(retry.status === 200 && retry.json.message.id === json.message.id, 'retry with same client_message_id was not idempotent');
      return `message ${json.message.id}`;
    });

    await step('incoming message from another user + heartbeat', async () => {
      const event = await sse.waitFor(
        'simulated incoming message',
        (e) => e.type === 'room_updated' && e.payload.update_reason === 'message_created' && e.payload.last_message?.id !== ctx.sent.id && e.payload.last_message?.sender_display_name !== ctx.me.display_name,
      );
      assert(sse.pings > 0, 'no ": ping" heartbeat comment received');
      return `"${event.payload.last_message.body_preview}" from ${event.payload.last_message.sender_display_name}; ${sse.pings} pings`;
    });

    await step('mark read clears unread', async () => {
      const unreadRoom = ctx.rooms.find((room) => room.unread_count > 0);
      const { json: page } = await api('GET', `/rooms/${unreadRoom.id}/messages?limit=1`, { token: ctx.token });
      const { json } = await api('POST', `/rooms/${unreadRoom.id}/read`, { token: ctx.token, body: { last_read_message_id: page.messages[0].id } });
      assert(json.unread_count === 0, `unread_count ${json.unread_count}`);
      return `${unreadRoom.name}: ${unreadRoom.unread_count} -> 0`;
    });

    await step('members, profile, history, search', async () => {
      const members = (await api('GET', `/rooms/${ctx.roomId}/members`, { token: ctx.token })).json;
      validate(members, { members: [MEMBER], next_cursor: '?string' }, 'members');
      const other = members.members.find((member) => member.user.id !== ctx.me.id);
      const profile = (await api('GET', `/rooms/${ctx.roomId}/members/${other.user.id}/profile`, { token: ctx.token })).json;
      validate(profile, { profile: { user: USER_SUMMARY, role: 'string' } }, 'member profile');
      const history = (await api('GET', `/rooms/${ctx.roomId}/message-history?category=images&limit=5`, { token: ctx.token })).json;
      validate(history, MESSAGE_PAGE, 'history');
      assert(history.messages.length > 0, 'image history is empty');
      const search = (await api('GET', `/search?q=${encodeURIComponent('总部')}&limit=8`, { token: ctx.token })).json;
      validate(search, { my_rooms: [ROOM_CARD], public_rooms: [PUBLIC_ROOM], messages: [{ room: { id: 'string', name: 'string' }, message: MESSAGE }], files: 'array' }, 'search');
      const publicRooms = (await api('GET', `/rooms/search?q=${encodeURIComponent('摄影')}`, { token: ctx.token })).json;
      validate(publicRooms, { rooms: [PUBLIC_ROOM] }, 'rooms/search');
      return `${members.members.length} members, ${search.my_rooms.length} room hits, ${search.messages.length} message hits`;
    });

    await step('live: state, join (fake LiveKit), mute, leave', async () => {
      const live = (await api('GET', `/rooms/${ctx.roomId}/live`, { token: ctx.token })).json.live;
      validate(live, LIVE_STATE, 'live');
      const join = (await api('POST', `/rooms/${ctx.roomId}/live/join`, {
        token: ctx.token,
        body: { client_live_session_id: `check-${Date.now()}`, source: 'desktop', mic_muted: false, headphones_muted: false },
      })).json;
      validate(join, LIVE_JOIN, 'live join');
      assert(join.live.participant_count === live.participant_count + 1, 'join did not add a participant');
      await sse.waitFor('live_participant_joined', (e) => e.type === 'live_participant_joined' && e.payload.live?.participants.some((p) => p.user.id === ctx.me.id));
      const muted = (await api('PATCH', `/rooms/${ctx.roomId}/live/me`, { token: ctx.token, body: { mic_muted: true } })).json;
      validate(muted.participant, LIVE_PARTICIPANT, 'participant');
      assert(muted.participant.mic_muted === true, 'mute not applied');
      await api('PATCH', `/rooms/${ctx.roomId}/live/me`, { token: ctx.token, body: { connection_state: 'left' } });
      const after = (await api('GET', `/rooms/${ctx.roomId}/live`, { token: ctx.token })).json.live;
      assert(after.participant_count === live.participant_count, 'leave did not remove the participant');
      return `${join.livekit.server_url}, ${live.participant_count} -> ${join.live.participant_count} -> ${after.participant_count}`;
    });

    await step('upload an image and fetch it back', async () => {
      const png = encodePng(4, 3, () => [255, 0, 0, 255]);
      const form = new FormData();
      form.append('purpose', 'message_image');
      form.append('file', new Blob([png], { type: 'application/octet-stream' }), '截图.png');
      const { json } = await api('POST', '/uploads/images', { token: ctx.token, body: form, expect: 201 });
      validate(json.asset, ASSET, 'asset');
      assert(json.asset.mime_type === 'image/png' && json.asset.width === 4 && json.asset.height === 3, 'mime/size not detected');
      const bytes = Buffer.from(await (await fetch(json.asset.url)).arrayBuffer());
      assert(bytes.equals(png), 'downloaded bytes differ');
      return json.asset.url;
    });

    await step('stickers, music box, playlists, notifications, sessions', async () => {
      const packs = (await api('GET', '/sticker-packs?scope=personal', { token: ctx.token })).json;
      validate(packs, { packs: [STICKER_PACK] }, 'sticker packs');
      const music = (await api('GET', '/rooms/room_music/music-box/state', { token: ctx.token })).json;
      validate(music, MUSIC_BOX, 'music box');
      assert(music.playback.state === 'playing', 'music box should be playing');
      const playlists = (await api('GET', '/me/music-box/playlists?page=1&page_size=50', { token: ctx.token })).json;
      validate(playlists, { playlists: [{ id: 'string', name: 'string', item_count: 'int' }], pagination: { page: 'int', total: 'int' } }, 'playlists');
      const items = (await api('GET', `/me/music-box/playlists/${playlists.playlists[0].id}`, { token: ctx.token })).json;
      validate(items, { playlist: { id: 'string' }, items: [{ id: 'string', track_id: 'string', title: 'string' }] }, 'playlist items');
      const notifications = (await api('GET', '/room-notifications', { token: ctx.token })).json;
      validate(notifications, { notifications: [{ id: 'string', type: 'string', room: PUBLIC_ROOM, created_at: 'date' }] }, 'notifications');
      const invites = (await api('GET', '/room-invites?status=pending', { token: ctx.token })).json;
      validate(invites, { invites: [{ id: 'string', room: PUBLIC_ROOM, inviter: USER_SUMMARY }] }, 'invites');
      const applications = (await api('GET', '/room-applications?status=pending', { token: ctx.token })).json;
      validate(applications, { applications: [{ id: 'string', room: PUBLIC_ROOM }] }, 'applications');
      const sessions = (await api('GET', '/auth/sessions', { token: ctx.token })).json;
      validate(sessions, [SESSION], 'sessions');
      const version = (await api('GET', '/app/version', { token: ctx.token })).json;
      validate(version, { latest_version: 'string' }, 'app version');
      return `${packs.packs.length} packs, ${music.queue.length} queued, ${playlists.playlists.length} playlists, ${notifications.notifications.length} notifications, ${invites.invites.length} invite, ${applications.applications.length} application`;
    });

    await step('recall my message', async () => {
      const { json } = await api('POST', `/rooms/${ctx.roomId}/messages/${ctx.sent.id}/recall`, { token: ctx.token, body: {} });
      validate(json.message, MESSAGE, 'recalled');
      assert(json.message.is_recalled === true && json.message.body === '', 'message not recalled');
    });

    await step('expired access token -> 401 -> refresh', async () => {
      await fetch(`${origin}/__mock/expire-tokens`, { method: 'POST' });
      await api('GET', '/me', { token: ctx.token, expect: 401 });
      const { json } = await api('POST', '/auth/refresh', { body: { refresh_token: ctx.refreshToken } });
      validate(json, AUTH_SESSION, 'refresh');
      assert(json.refresh_token !== ctx.refreshToken, 'refresh token was not rotated');
      await api('GET', '/me', { token: json.access_token });
      ctx.token = json.access_token;
      ctx.refreshToken = json.refresh_token;
    });

    await step('logout revokes the session', async () => {
      await api('POST', '/auth/logout', { token: ctx.token, body: { refresh_token: ctx.refreshToken } });
      await api('POST', '/auth/refresh', { body: { refresh_token: ctx.refreshToken }, expect: 401 });
      await api('GET', '/me', { token: ctx.token, expect: 401 });
    });
  } finally {
    sse.close();
  }
  return results.length;
}

const { child, origin } = startServer();
let exitCode = 0;
try {
  const url = await origin;
  console.log(`mock server at ${url}\n`);
  const passed = await runChecks(url);
  console.log(`\nAll ${passed} checks passed.`);
} catch (error) {
  exitCode = 1;
  console.log(`\nCheck failed: ${error.message}\n\n--- server log ---\n${serverLog.trim().split('\n').slice(-60).join('\n')}`);
} finally {
  child.kill('SIGTERM');
}
process.exit(exitCode);
