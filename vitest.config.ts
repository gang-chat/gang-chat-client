import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

const repoRoot = fileURLToPath(new URL('.', import.meta.url));

export default defineConfig({
  resolve: {
    // Same package aliases as tsconfig.base.json `paths` / electron.vite.config.ts.
    alias: [
      { find: /^@gang-chat\/core\/(.*)$/, replacement: `${repoRoot}packages/core/src/$1` },
      { find: /^@gang-chat\/ui\/(.*)$/, replacement: `${repoRoot}packages/ui/src/$1` },
    ],
  },
  test: {
    include: ['packages/*/test/**/*.test.ts', 'packages/*/test/**/*.test.tsx', 'apps/*/test/**/*.test.ts'],
    environment: 'node',
  },
});
