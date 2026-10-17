// Wires the store, SSE hub, route tables and background simulation into a
// node:http server. `createMockServer(options).listen()` is what server.mjs
// (and anything embedding the mock) calls.
import http from 'node:http';

import { assetRoutes, uploadRoutes } from '../routes/assets.mjs';
import { authRoutes } from '../routes/auth.mjs';
import { liveRoutes } from '../routes/live.mjs';
import { memberRoutes } from '../routes/members.mjs';
import { messageRoutes } from '../routes/messages.mjs';
import { mockControlRoutes } from '../routes/mock_control.mjs';
import { musicRoutes } from '../routes/music.mjs';
import { notificationRoutes } from '../routes/notifications.mjs';
import { roomRoutes } from '../routes/rooms.mjs';
import { stickerRoutes } from '../routes/stickers.mjs';
import { streamRoutes } from '../routes/stream.mjs';
import { userRoutes } from '../routes/users.mjs';
import {
  ApiError,
  applyCommonHeaders,
  newRequestId,
  notFound,
  parseJsonBody,
  parseMultipart,
  readBody,
  sendError,
  sendResult,
  unauthorized,
} from './http.mjs';
import { Router } from './router.mjs';
import { seedStore } from './seed.mjs';
import { startSimulation } from './simulation.mjs';
import { StreamHub } from './sse.mjs';
import { Store } from './store.mjs';
import { sleep } from './util.mjs';

export const API_PREFIX = '/api/v1';

export const DEFAULT_OPTIONS = {
  host: '127.0.0.1',
  port: 21116,
  /** Origin written into asset URLs; defaults to http://<host>:<port>. */
  publicUrl: null,
  livekitUrl: 'ws://127.0.0.1:7880',
  /** With both set, live tokens are signed for a real LiveKit server. */
  livekitApiKey: null,
  livekitApiSecret: null,
  tokenTtlMs: 15 * 60 * 1000,
  chatterIntervalMs: 20_000,
  heartbeatMs: 15_000,
  latencyMs: 0,
  latestVersion: '1.0.0',
  quiet: false,
};

export function buildRouter() {
  const router = new Router();
  router.addAll(API_PREFIX, [
    ...authRoutes,
    ...userRoutes,
    ...roomRoutes,
    ...memberRoutes,
    ...messageRoutes,
    ...notificationRoutes,
    ...liveRoutes,
    ...musicRoutes,
    ...stickerRoutes,
    ...uploadRoutes,
    ...streamRoutes,
  ]);
  router.addAll('', [...assetRoutes, ...mockControlRoutes]);
  return router;
}

function bearerToken(req) {
  const match = /^Bearer\s+(.+)$/i.exec(req.headers.authorization ?? '');
  return match ? match[1].trim() : null;
}

export function createMockServer(options = {}) {
  const config = { ...DEFAULT_OPTIONS, ...options };
  const log = config.quiet ? () => {} : (...args) => console.log(...args);
  const store = new Store({ publicUrl: config.publicUrl, tokenTtlMs: config.tokenTtlMs });
  const hub = new StreamHub({ heartbeatMs: config.heartbeatMs, log });
  store.connectedUserIds = () => hub.connectedUserIds();
  const app = { config, store, hub, log };
  const router = buildRouter();
  const server = http.createServer((req, res) => handleRequest(app, router, req, res));
  let simulation = null;

  return {
    app,
    server,
    router,
    /** Binds the port, then seeds data (asset URLs need the final origin) and starts the simulation. */
    async listen() {
      await new Promise((resolve, reject) => {
        server.once('error', reject);
        server.listen(config.port, config.host, () => {
          server.off('error', reject);
          resolve();
        });
      });
      config.port = server.address().port;
      const urlHost = ['0.0.0.0', '::', ''].includes(config.host) ? '127.0.0.1' : config.host;
      config.publicUrl = (config.publicUrl ?? `http://${urlHost}:${config.port}`).replace(/\/$/, '');
      store.publicUrl = config.publicUrl;
      seedStore(store);
      simulation = startSimulation(app);
      return { url: config.publicUrl, apiBaseUrl: `${config.publicUrl}${API_PREFIX}`, port: config.port };
    },
    async close() {
      simulation?.stop();
      hub.close();
      server.closeAllConnections();
      await new Promise((resolve) => server.close(() => resolve()));
    },
  };
}

async function handleRequest(app, router, req, res) {
  const startedAt = Date.now();
  const requestId = newRequestId();
  applyCommonHeaders(req, res, requestId);
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }
  const url = new URL(req.url, 'http://mock.invalid');
  const path = url.pathname;
  let streaming = false;
  try {
    const match = router.match(req.method, path);
    if (!match.route) {
      if (match.allowedMethods.length > 0) {
        throw new ApiError(405, 'method_not_allowed', `method ${req.method} not allowed for ${path}`);
      }
      throw notFound(`no route for ${req.method} ${path}`);
    }
    const c = {
      app,
      store: app.store,
      hub: app.hub,
      config: app.config,
      req,
      res,
      requestId,
      params: match.params,
      query: url.searchParams,
      body: {},
      multipart: null,
      user: null,
      session: null,
    };
    const token = bearerToken(req);
    const auth = token ? app.store.authenticate(token) : null;
    if (!match.route.options.public && !auth) throw unauthorized(token ? 'invalid token' : 'missing authorization');
    if (auth) Object.assign(c, auth);

    if (req.method !== 'GET' && req.method !== 'HEAD') {
      const raw = await readBody(req);
      const contentType = req.headers['content-type'] ?? '';
      if (contentType.toLowerCase().startsWith('multipart/form-data')) c.multipart = parseMultipart(raw, contentType);
      else c.body = parseJsonBody(raw);
    }
    if (app.config.latencyMs > 0 && path.startsWith(API_PREFIX)) await sleep(app.config.latencyMs);
    streaming = path === `${API_PREFIX}/me/stream`;
    sendResult(req, res, await match.route.handler(c));
  } catch (error) {
    if (error instanceof ApiError) {
      sendError(res, error, requestId);
    } else {
      console.error(`[mock] ${req.method} ${path} failed:`, error);
      sendError(res, new ApiError(500, 'internal_error', error?.message ?? 'internal error'), requestId);
    }
  } finally {
    const suffix = streaming ? ' (stream open)' : ` ${Date.now() - startedAt}ms`;
    if (!path.startsWith('/mock-assets/') || res.statusCode >= 400) {
      app.log(`[http] ${res.statusCode} ${req.method} ${path}${suffix}`);
    }
  }
}
