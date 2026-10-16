import { resolve } from 'node:path';
import { defineConfig, externalizeDepsPlugin } from 'electron-vite';
import react from '@vitejs/plugin-react';

const repoRoot = resolve(__dirname, '../..');
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
    define: { __GANG_CONFIG_OVERRIDES__: JSON.stringify(configOverrides) },
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
      alias: [
        // The shared UI is written against React Native; Electron renders it
        // through react-native-web.
        { find: /^react-native$/, replacement: 'react-native-web' },
        ...sharedAliases,
      ],
      // Platform files: `foo.web.tsx` wins over the native `foo.tsx`.
      extensions: ['.web.tsx', '.web.ts', '.web.jsx', '.web.js', '.tsx', '.ts', '.jsx', '.mjs', '.js', '.json'],
    },
    define: {
      __DEV__: JSON.stringify(!isProduction),
      __GANG_CONFIG_OVERRIDES__: JSON.stringify(configOverrides),
      'process.env.EXPO_OS': JSON.stringify('web'),
    },
    optimizeDeps: {
      esbuildOptions: {
        resolveExtensions: ['.web.tsx', '.web.ts', '.web.js', '.tsx', '.ts', '.js'],
        loader: { '.js': 'jsx' },
      },
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
