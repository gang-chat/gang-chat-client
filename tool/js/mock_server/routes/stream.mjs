// GET /me/stream — the realtime SSE connection (see lib/sse.mjs for the format).
import { HANDLED } from '../lib/http.mjs';
import { route } from '../lib/router.mjs';

function openStream(c) {
  c.hub.attach(c.req, c.res, { userId: c.user.id, sessionId: c.session.id });
  return HANDLED;
}

export const streamRoutes = [route('GET', '/me/stream', openStream)];
