// Prepares Sparkle for the macOS package (see native/sparkle/sparkle.mm):
//   build/sparkle/Sparkle.framework  copied into the app's Contents/Frameworks
//   build/sparkle/sparkle.node       the Node-API module the main process loads
//   build/sparkle/bin/sign_update    signs the DMG and appcast in the release
//
// The framework comes from Sparkle's official release archive, pinned by
// version and checksum: the version the Flutter macOS app gets from CocoaPods
// (macos/Podfile.lock), so both clients install updates the same way. Does
// nothing on other platforms.
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const sparkleVersion = '2.9.6';
const archiveSha256 = '52bf9e88cdd972fc0c81501377a880e90d47031bd8ca5462488f843e2609e192';
const archiveUrl = `https://github.com/sparkle-project/Sparkle/releases/download/${sparkleVersion}/Sparkle-${sparkleVersion}.tar.xz`;

if (process.platform !== 'darwin') process.exit(0);

const desktopDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const outDir = join(desktopDir, 'build', 'sparkle');
const cacheDir = join(desktopDir, 'node_modules', '.cache', 'sparkle');
const archive = join(cacheDir, `Sparkle-${sparkleVersion}.tar.xz`);
const source = join(desktopDir, 'native', 'sparkle', 'sparkle.mm');

function sha256(file) {
  return createHash('sha256').update(readFileSync(file)).digest('hex');
}

if (!existsSync(archive) || sha256(archive) !== archiveSha256) {
  mkdirSync(cacheDir, { recursive: true });
  console.log(`Downloading Sparkle ${sparkleVersion}`);
  const response = await fetch(archiveUrl);
  if (!response.ok) throw new Error(`Sparkle download failed: ${response.status} ${archiveUrl}`);
  writeFileSync(archive, Buffer.from(await response.arrayBuffer()));
  const actual = sha256(archive);
  if (actual !== archiveSha256) {
    rmSync(archive);
    throw new Error(`Sparkle archive checksum mismatch: expected ${archiveSha256}, got ${actual}`);
  }
}

const stamp = join(outDir, '.version');
const built = existsSync(stamp) && readFileSync(stamp, 'utf8') === `${sparkleVersion}\n${sha256(source)}\n`;
if (!built) {
  rmSync(outDir, { recursive: true, force: true });
  const extracted = join(outDir, 'archive');
  mkdirSync(extracted, { recursive: true });
  execFileSync('tar', ['-xJf', archive, '-C', extracted]);
  // ditto keeps the framework's symlinks (Versions/Current, …) intact.
  execFileSync('ditto', [join(extracted, 'Sparkle.framework'), join(outDir, 'Sparkle.framework')]);
  mkdirSync(join(outDir, 'bin'), { recursive: true });
  execFileSync('ditto', [join(extracted, 'bin', 'sign_update'), join(outDir, 'bin', 'sign_update')]);
  rmSync(extracted, { recursive: true, force: true });

  const nodeInclude = resolve(dirname(process.execPath), '..', 'include', 'node');
  if (!existsSync(join(nodeInclude, 'node_api.h'))) {
    throw new Error(`Node-API headers not found at ${nodeInclude}`);
  }
  // Node-API is ABI-stable, so the module builds against this Node's headers
  // and resolves the napi_* symbols from Electron at load time.
  execFileSync(
    'clang++',
    [
      '-x', 'objective-c++',
      '-std=c++17',
      '-fobjc-arc',
      '-bundle',
      '-undefined', 'dynamic_lookup',
      '-arch', 'arm64',
      '-arch', 'x86_64',
      '-mmacosx-version-min=12.0',
      '-I', nodeInclude,
      '-F', outDir,
      '-framework', 'Sparkle',
      '-framework', 'AppKit',
      '-framework', 'Foundation',
      // GangChat.app/Contents/MacOS/GangChat -> Contents/Frameworks/Sparkle.framework
      '-Wl,-rpath,@executable_path/../Frameworks',
      '-o', join(outDir, 'sparkle.node'),
      source,
    ],
    { stdio: 'inherit' },
  );
  writeFileSync(stamp, `${sparkleVersion}\n${sha256(source)}\n`);
}
console.log(`Sparkle ${sparkleVersion} ready in ${outDir}`);
