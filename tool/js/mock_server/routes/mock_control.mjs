// Dev-only control endpoints at the server root (not part of the real API):
//   GET  /                         server info and seeded accounts
//   GET  /__mock/state             entity counts and open SSE streams
//   POST /__mock/expire-tokens     drop every access token -> next call 401s and refreshes
//   POST /__mock/chatter           post one simulated incoming message now
//   POST /__mock/emit              push a raw SSE event: { type, user_id?, room_id?, data? }
import { postChatter } from '../lib/simulation.mjs';
import { badRequest } from '../lib/http.mjs';
import { route } from '../lib/router.mjs';

function info(c) {
  return {
    name: 'Gang Chat mock server',
    api_base_url: `${c.config.publicUrl}/api/v1`,
    asset_base_url: c.config.publicUrl,
    accounts: {
      demo: 'demo / demo1234 (every seeded username accepts demo1234)',
      superuser: 'admin / demo1234',
      email: 'any email-shaped login with any password (account created on first login)',
      verification_code: '123456',
    },
    control: ['GET /__mock/state', 'POST /__mock/expire-tokens', 'POST /__mock/chatter', 'POST /__mock/emit'],
  };
}

function state(c) {
  const { store, hub } = c;
  return {
    users: store.users.size,
    rooms: store.rooms.size,
    messages: [...store.rooms.values()].reduce((sum, room) => sum + room.messages.length, 0),
    assets: store.assets.size,
    active_sessions: [...store.sessions.values()].filter((session) => !session.revokedAt).length,
    access_tokens: store.accessTokens.size,
    sse_connections: hub.connectionCount,
    sse_users: [...hub.connectedUserIds()],
  };
}

function expireTokens(c) {
  return { expired: c.store.expireAllAccessTokens() };
}

function chatter(c) {
  const message = postChatter(c.app, c.body.room_id || undefined);
  return { message_id: message?.id ?? null };
}

function emit(c) {
  const type = String(c.body.type ?? '').trim();
  if (!type) throw badRequest('type is required');
  const userIds = c.body.user_id ? [c.body.user_id] : [...c.hub.connectedUserIds()];
  const delivered = c.hub.sendToUsers(userIds, type, c.body.room_id ?? null, c.body.data ?? {});
  return { delivered };
}

const open = { public: true };

export const mockControlRoutes = [
  route('GET', '/', info, open),
  route('GET', '/__mock/state', state, open),
  route('POST', '/__mock/expire-tokens', expireTokens, open),
  route('POST', '/__mock/chatter', chatter, open),
  route('POST', '/__mock/emit', emit, open),
];
