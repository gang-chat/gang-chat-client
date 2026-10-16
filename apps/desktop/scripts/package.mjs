// Packages the desktop app: `node scripts/package.mjs --mac | --win | --dir`.
//
// Builds with electron-vite, then runs electron-builder. macOS builds first
// prepare Sparkle (scripts/prepare_sparkle.mjs) and set CFBundleVersion to the
// version code Sparkle compares: major * 1000000 + minor * 1000 + patch, the
// same number the Flutter macOS app and the Android build use, so releases
// keep growing across both clients.
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const desktopDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
const buildsMac = args.includes('--mac') || (args.includes('--dir') && process.platform === 'darwin');

export function versionCode(version) {
  const match = /^(\d+)\.(\d+)\.(\d+)$/.exec(version);
  if (!match) throw new Error(`version must be major.minor.patch: ${version}`);
  const [major, minor, patch] = match.slice(1).map(Number);
  if (minor > 999 || patch > 999) throw new Error(`version components are too large for a version code: ${version}`);
  return major * 1_000_000 + minor * 1_000 + patch;
}

function run(command, commandArgs) {
  const result = spawnSync(command, commandArgs, {
    cwd: desktopDir,
    stdio: 'inherit',
    shell: process.platform === 'win32',
  });
  if (result.status !== 0) process.exit(result.status ?? 1);
}

const { version } = JSON.parse(readFileSync(join(desktopDir, 'package.json'), 'utf8'));
if (buildsMac) run(process.execPath, [join(desktopDir, 'scripts', 'prepare_sparkle.mjs')]);
run('npx', ['electron-vite', 'build']);
run('npx', [
  'electron-builder',
  ...args,
  '--publish',
  'never',
  ...(buildsMac ? [`-c.buildVersion=${versionCode(version)}`] : []),
]);
