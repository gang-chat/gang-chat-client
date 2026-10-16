import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { defineConfig, externalizeDepsPlugin } from 'electron-vite';
import react from '@vitejs/plugin-react';

const repoRoot = resolve(__dirname, '../..');
const appVersion = (JSON.parse(readFileSync(resolve(__dirname, 'package.json'), 'utf8')) as { version: string }).version;
const isProduction = process.env.NODE_ENV === 'production';

// Build-time server overrides, the equivalent of the Flutter
// `--dart-define=GANG_API_BASE_URL=...` flags.
const configOverrides = {
  apiBaseUrl: process.env.GANG_API_BASE_URL ?? '',
  assetBaseUrl: process.env.GANG_ASSET_BASE_URL ?? '',
  releaseBucketUrl: process.env.GANG_RELEASE_BUCKET_URL ?? '',
};

const sharedAliases = [
  { find: /^@gang-chat\/core\/(.*)$/, replacement: `${repoRoot}/packages/core/src/$1` },
  { find: /^@gang-chat\/ui\/(.*)$/, replacement: `${repoRoot}/packages/ui/src/$1` },
  { find: '@repo-assets', replacement: `${repoRoot}/assets` },
];

export default defineConfig({
  main: {
    plugins: [externalizeDepsPlugin()],
    resolve: { alias: sharedAliases },
    define: {
      __GANG_CONFIG_OVERRIDES__: JSON.stringify(configOverrides),
      // Set by the release workflow when the macOS build is Developer ID
      // signed; Squirrel.Mac refuses to install updates for unsigned apps.
      __GANG_MAC_UPDATES_SIGNED__: JSON.stringify(process.env.GANG_MAC_UPDATES_SIGNED === '1'),
    },
    build: { outDir: 'out/main' },
  },
  preload: {
    plugins: [externalizeDepsPlugin()],
    build: { outDir: 'out/preload' },
  },
  renderer: {
    root: 'src/renderer',
    plugins: [react()],
    resolve: {
      alias: sharedAliases,
      // Platform files in @gang-chat/core: the browser implementation
      // `foo.web.ts` wins over the React Native one `foo.ts`.
      extensions: ['.web.tsx', '.web.ts', '.web.jsx', '.web.js', '.tsx', '.ts', '.jsx', '.mjs', '.js', '.json'],
    },
    define: {
      __DEV__: JSON.stringify(!isProduction),
      __GANG_CONFIG_OVERRIDES__: JSON.stringify(configOverrides),
      // `gangChatClientVersion` (Flutter: --dart-define=GANG_CHAT_VERSION).
      __GANG_CHAT_VERSION__: JSON.stringify(appVersion),
    },
    server: {
      fs: { allow: [repoRoot] },
    },
    build: {
      outDir: 'out/renderer',
      chunkSizeWarningLimit: 4096,
      rollupOptions: { input: resolve(__dirname, 'src/renderer/index.html') },
    },
  },
});
