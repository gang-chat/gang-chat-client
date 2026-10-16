import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const appDir = fileURLToPath(new URL('.', import.meta.url));
const repoRoot = fileURLToPath(new URL('../..', import.meta.url));
const isProduction = process.env.NODE_ENV === 'production';

// Served from https://ky-z.com/gang-chat/app/ (the Flutter `--base-href`).
const base = process.env.GANG_WEB_BASE_HREF ?? '/gang-chat/app/';
if (!/^\/(.*\/)?$/.test(base)) {
  throw new Error(`GANG_WEB_BASE_HREF must start and end with '/': ${base}`);
}

// Build-time server overrides, the equivalent of the Flutter
// `--dart-define=GANG_API_BASE_URL=...` flags.
const configOverrides = {
  apiBaseUrl: process.env.GANG_API_BASE_URL ?? '',
  assetBaseUrl: process.env.GANG_ASSET_BASE_URL ?? '',
  releaseBucketUrl: process.env.GANG_RELEASE_BUCKET_URL ?? '',
};

export default defineConfig({
  root: appDir,
  base,
  plugins: [react()],
  resolve: {
    alias: [
      { find: /^@gang-chat\/core\/(.*)$/, replacement: `${repoRoot}packages/core/src/$1` },
      { find: /^@gang-chat\/ui\/(.*)$/, replacement: `${repoRoot}packages/ui/src/$1` },
      { find: '@repo-assets', replacement: `${repoRoot}assets` },
    ],
    // Platform files in @gang-chat/core: the browser implementation
    // `foo.web.ts` wins over the React Native one `foo.ts`.
    extensions: ['.web.tsx', '.web.ts', '.web.jsx', '.web.js', '.tsx', '.ts', '.jsx', '.mjs', '.js', '.json'],
  },
  define: {
    __DEV__: JSON.stringify(!isProduction),
    __GANG_CONFIG_OVERRIDES__: JSON.stringify(configOverrides),
    // `gangChatClientVersion` (Flutter: --dart-define=GANG_CHAT_VERSION).
    __GANG_CHAT_VERSION__: JSON.stringify(process.env.GANG_CHAT_VERSION ?? ''),
    // `gangChatClientReleaseTimestamp`, set by the release workflow.
    __GANG_CHAT_RELEASED_AT__: JSON.stringify(process.env.GANG_CHAT_RELEASED_AT ?? ''),
  },
  server: {
    port: 4176,
    fs: { allow: [repoRoot] },
  },
  preview: {
    port: 4176,
  },
  build: {
    // tool/build_web.sh and tool/deploy_web.sh use build/web, like the
    // Flutter build did.
    outDir: `${repoRoot}build/web`,
    emptyOutDir: true,
    chunkSizeWarningLimit: 4096,
  },
});
