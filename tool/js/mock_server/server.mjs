#!/usr/bin/env node
// Gang Chat mock API server for end-to-end smoke tests of the desktop client.
//
//   node tool/js/mock_server/server.mjs [--port 21116] [options]
//
// See README.md in this directory for accounts, options and how to point the
// desktop build at it. No dependencies: Node 22+ built-ins only.
import { parseArgs } from 'node:util';

import { DEFAULT_OPTIONS, createMockServer } from './lib/app.mjs';

const USAGE = `Usage: node tool/js/mock_server/server.mjs [options]

  --port <n>               Port to listen on (default ${DEFAULT_OPTIONS.port}; 0 = random)
  --host <addr>            Interface to bind (default ${DEFAULT_OPTIONS.host})
  --public-url <url>       Origin used in asset URLs (default http://<host>:<port>)
  --livekit-url <url>      LiveKit URL handed out by live/join (default ${DEFAULT_OPTIONS.livekitUrl})
  --token-ttl <seconds>    Access-token lifetime (default ${DEFAULT_OPTIONS.tokenTtlMs / 1000})
  --chatter-interval <s>   Simulated incoming message interval, 0 disables (default ${DEFAULT_OPTIONS.chatterIntervalMs / 1000})
  --heartbeat <seconds>    SSE ": ping" interval (default ${DEFAULT_OPTIONS.heartbeatMs / 1000})
  --latency <ms>           Artificial delay added to every API response (default 0)
  --latest-version <v>     Version reported by GET /app/version (default ${DEFAULT_OPTIONS.latestVersion})
  --quiet                  Only print the startup banner and errors
  -h, --help               Show this help`;

function parseCli(argv) {
  const { values } = parseArgs({
    args: argv,
    options: {
      port: { type: 'string' },
      host: { type: 'string' },
      'public-url': { type: 'string' },
      'livekit-url': { type: 'string' },
      'token-ttl': { type: 'string' },
      'chatter-interval': { type: 'string' },
      heartbeat: { type: 'string' },
      latency: { type: 'string' },
      'latest-version': { type: 'string' },
      quiet: { type: 'boolean' },
      help: { type: 'boolean', short: 'h' },
    },
  });
  if (values.help) {
    console.log(USAGE);
    process.exit(0);
  }
  const number = (name, value, fallback) => {
    if (value === undefined) return fallback;
    const parsed = Number(value);
    if (!Number.isFinite(parsed) || parsed < 0) {
      console.error(`Invalid --${name}: ${value}\n\n${USAGE}`);
      process.exit(2);
    }
    return parsed;
  };
  const options = {
    port: number('port', values.port ?? process.env.MOCK_PORT, DEFAULT_OPTIONS.port),
    host: values.host ?? DEFAULT_OPTIONS.host,
    publicUrl: values['public-url'] ?? null,
    livekitUrl: values['livekit-url'] ?? DEFAULT_OPTIONS.livekitUrl,
    tokenTtlMs: number('token-ttl', values['token-ttl'], DEFAULT_OPTIONS.tokenTtlMs / 1000) * 1000,
    chatterIntervalMs: number('chatter-interval', values['chatter-interval'], DEFAULT_OPTIONS.chatterIntervalMs / 1000) * 1000,
    heartbeatMs: number('heartbeat', values.heartbeat, DEFAULT_OPTIONS.heartbeatMs / 1000) * 1000,
    latencyMs: number('latency', values.latency, 0),
    latestVersion: values['latest-version'] ?? DEFAULT_OPTIONS.latestVersion,
    quiet: Boolean(values.quiet),
  };
  if (options.tokenTtlMs < 1000 || options.heartbeatMs < 1000) {
    console.error('--token-ttl and --heartbeat must be at least 1 second');
    process.exit(2);
  }
  return options;
}

const options = parseCli(process.argv.slice(2));
const mock = createMockServer(options);
try {
  const { url, apiBaseUrl } = await mock.listen();
  // check.mjs parses the first line; keep its shape stable.
  console.log(`[mock] listening on ${url}`);
  console.log(`[mock] GANG_API_BASE_URL=${apiBaseUrl} GANG_ASSET_BASE_URL=${url}`);
  console.log('[mock] login: demo / demo1234 (or any email + any password); verification code 123456');
} catch (error) {
  console.error(`[mock] failed to start: ${error.message}`);
  process.exit(1);
}

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, async () => {
    await mock.close();
    process.exit(0);
  });
}
