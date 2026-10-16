# Gang Chat mock API server

A self-contained fake of the Gang Chat backend (`/api/v1/...` plus the
`/me/stream` SSE channel) for end-to-end smoke tests of the Electron desktop
client when the real server is not reachable. It speaks the protocol the
Flutter client defines: endpoints from `lib/src/protocol/api_client.dart` and
`lib/src/auth/auth_client.dart`, JSON shapes from the `fromJson` factories in
`lib/src/protocol/models.dart`, and realtime events from
`lib/src/live/live_stream_client.dart` / `lib/src/home/home_shell_realtime.dart`.

Plain Node.js ESM, no dependencies (Node 22+). State is in memory and reseeded
on every start.

## Run it

```sh
node tool/js/mock_server/server.mjs              # http://127.0.0.1:21116
node tool/js/mock_server/server.mjs --port 0     # random free port
```

Then build or run the desktop app against it. The committed
`assets/config/app_config.json` points at production, so the overrides are
required:

```sh
GANG_API_BASE_URL=http://127.0.0.1:21116/api/v1 \
GANG_ASSET_BASE_URL=http://127.0.0.1:21116 \
npm run build -w @gang-chat/desktop

# or, for a dev session with hot reload:
GANG_API_BASE_URL=http://127.0.0.1:21116/api/v1 \
GANG_ASSET_BASE_URL=http://127.0.0.1:21116 \
npm run dev -w @gang-chat/desktop
```

| Option | Default | Meaning |
| --- | --- | --- |
| `--port <n>` | `21116` (`MOCK_PORT` env) | Listen port; `0` picks a free one |
| `--host <addr>` | `127.0.0.1` | Bind address |
| `--public-url <url>` | `http://<host>:<port>` | Origin written into asset URLs (set it when the client reaches the mock through another host name) |
| `--livekit-url <url>` | `ws://127.0.0.1:7880` | LiveKit URL returned by `POST /rooms/:id/live/join` |
| `--token-ttl <s>` | `900` | Access-token lifetime; lower it (e.g. `90`; the client refreshes 60s before expiry) to exercise refresh |
| `--chatter-interval <s>` | `20` | Simulated incoming message cadence; `0` disables |
| `--heartbeat <s>` | `15` | SSE `: ping` interval |
| `--latency <ms>` | `0` | Artificial delay on every API response (loading states) |
| `--latest-version <v>` | `1.0.0` | Version reported by `GET /app/version` |
| `--quiet` | off | Suppress the per-request log |

## Accounts

- `demo` / `demo1234`: the main persona. Every seeded username accepts `demo1234`
  (`alice`, `bob`, `carol`, `dave`, ...). `admin` / `demo1234` is a superuser,
  for the forced user-settings screens and the `user_settings` search category.
- Any email-shaped login works with any password. Unknown emails get a new
  account, which joins four public rooms so the home screen is not empty.
- Registration, email verification and password reset always use code
  **`123456`**. The server log prints it too.

## Seeded world

All rooms have roughly 60 messages (fewer for the rooms Demo has not joined) spread over the
last three days. Between them they cover text, links, generated PNG images
plus two `picsum.photos` images (those need internet), files (PDF, Markdown,
TXT, CSV, ZIP, JSON), voice clips (generated WAV with `duration_ms`),
stickers, quotes, `@user` / `@所有人` mentions, a recalled message, system
events (joined, role changed, live joined), shared songs, a shared playlist,
and a `gang-bot` exchange.

| Room | Demo's role | What it exercises |
| --- | --- | --- |
| Gang 总部 `room_hq` | owner, **pinned** | 3 people **in live** (one screen sharing), 2 pending join requests, a blacklisted user, room sticker pack, 120s recall window. **Receives the simulated incoming messages.** |
| 开发频道 `room_dev` | admin | **7 unread including an @Demo mention** |
| 音乐电台 `room_music` | member | Music box **playing** a 5-song queue, room playlists, a shared playlist message |
| 游戏开黑 `room_games` | member | 2 people in live (camera on, muted), 2 unread |
| 设计评审 `room_design` | member | Notifications `silent`, remark name, `admin_approval` recall policy |
| 闲聊水群 `room_random` | admin | Everyone's in it, 3 unread |
| Flutter 交流 / 摄影爱好者 / 读书会 / 旧项目组 | not a member | Search results, open join, a pending application, a pending invite, a rejected application |

Demo also has 2 personal sticker packs, 3 personal playlists and
notifications (a mention, a role promotion, a removal).

## Realtime (`GET /api/v1/me/stream`)

`text/event-stream`. Each connection opens with `event: ready`, then sends a `: ping`
comment every 15s. Events are `event: <type>` and
`data: {"type", "room_id", "data"}`, which is what the client's SSE parser
expects. The mock emits:

- `room_updated` (account-scoped `RoomCard` + `update_reason`) for new messages,
  recalls, reads, settings and member changes. **There is no
  `message.created` event in this protocol.** A new message arrives as
  `room_updated` with `update_reason: "message_created"` and a new
  `last_message`, and the client then reloads the room's messages. The
  sender's own sessions get `"message_sent"` instead. The client plays its
  new-message sound for every `message_created`, so this avoids a sound for
  the user's own messages.
- `room_added`, `room_deleted`, `room_role_changed`, `room_member_profile_changed`,
  `room_join_requests_updated`, `room_invites_updated`,
  `room_applications_updated`, `room_notifications_updated`, `account_suspended`.
- `live_participant_joined|updated|left|moderated` with `{ live, participant_count, preview }`.
- `music_box_changed` (full `MusicBoxState`) and, while a room is playing,
  `music_box_progress` every second. Tracks advance on the server clock.
  Like the real server, room-wide snapshots carry all-false capabilities.
  The client refetches its own state after each one.

Every 20s another room member posts into `room_hq`. Every 5th post is an image,
and every 7th post mentions Demo.

## Voice

`POST /rooms/:id/live/join` returns a valid `LiveJoinResult` with a fake,
JWT-shaped token for `ws://127.0.0.1:7880`. Nothing listens there, so the
client's media connection fails. That is on purpose: it exercises the voice
panel's error path. The roster, mute/camera/screen toggles
(`PATCH /live/me`), member volumes, screen-view, moderation and
`connection_state: "left"` all go through the mock and broadcast live
snapshots. Pass `--livekit-url` to point at a real LiveKit dev server instead.
The mock's tokens are not signed, so a real LiveKit server will still reject them.

## What is stubbed

- **Media:** no audio is streamed. The music box only keeps the clock, and
  `POST /me/music-box/preview` returns a 15s synthesized WAV for any track.
- **Search:** music search uses a small built-in catalog. Unknown keywords get
  synthesized results.
- **Email and push:** no email is sent; codes are always `123456`. Push-device
  endpoints accept and ignore their input.
- **gang-bot:** answers `@gang-bot` mentions with a canned reply.
- **Permissions:** simplified (owner > admin > member; superusers bypass).
  `text_muted_until` is never set.
- **Persistence:** none. Restarting resets everything, including uploads.

## Dev controls (not part of the real API)

```sh
curl -X POST localhost:21116/__mock/expire-tokens   # every token 401s -> client refreshes
curl -X POST localhost:21116/__mock/chatter         # post an incoming message now
curl -X POST localhost:21116/__mock/emit -H 'content-type: application/json' \
     -d '{"type":"account_suspended"}'              # push any SSE event to connected users
curl localhost:21116/__mock/state                   # counts + open streams
```

## Self-check

```sh
node tool/js/mock_server/check.mjs
```

This spawns the server on a random port and walks the client flow:

1. Auth: CORS preflight, 401 without a token, login (demo and an email
   account), the `x-gang-server-time` header.
2. Rooms: the room list and its paging, room detail.
3. Messages: every room's messages, including older pages, plus served
   assets with HTTP ranges.
4. Realtime: SSE `ready`; sending a message and seeing its `room_updated`;
   an idempotent retry; a simulated incoming message; heartbeats.
5. Everything else: mark read, members, search, live join/mute/leave,
   upload round trip, stickers, music, notifications, recall, token
   expiry and refresh, logout.

Responses are checked against the fields the Dart models read with non-null
casts. The script exits non-zero on the first failure and prints the server log.

## Layout

```
server.mjs            CLI entry (argument parsing, startup banner)
check.mjs             self-check
lib/app.mjs           server wiring: route table, auth, body parsing, errors
lib/router.mjs        method + `:param` path router
lib/http.mjs          ApiError, result helpers, multipart, CORS/time headers, ranges
lib/store.mjs         in-memory entities + serializers (the JSON contract)
lib/seed.mjs          the seeded demo world
lib/events.mjs        message posting, system messages, notifications, SSE fan-out
lib/room_ops.mjs      join / leave / remove / delete room, leave live
lib/music_box.mjs     catalog, playback state machine, MusicBoxState serializer
lib/playlists.mjs     saved playlists (personal and room)
lib/simulation.mjs    incoming-message chatter and music ticks
lib/sse.mjs           SSE hub
lib/media.mjs         PNG / WAV / ZIP / PDF generation, MIME + image-size sniffing
routes/*.mjs          route tables per area (auth, users, rooms, members, messages,
                      notifications, live, music, stickers, assets, stream, mock_control)
```

To add an endpoint, add a `route(method, path, handler)` entry to the
matching `routes/*.mjs` table. Handlers get `c` (`c.user`, `c.params`,
`c.query`, `c.body`, `c.store`, `c.hub`). They return a plain object for
200 JSON, use `created()` or `json(status, body)` for other statuses, and
throw `ApiError` to send an error.
