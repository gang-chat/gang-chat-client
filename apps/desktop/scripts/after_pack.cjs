// electron-builder afterPack hook: ad-hoc signs the macOS app.
//
// Without a Developer ID, electron-builder leaves the package unsigned apart
// from the linker signature of its main binary, which macOS reports as invalid
// ("code has no resources but signature indicates they must be present").
// Sparkle refuses to install an update whose code signature is invalid, so the
// whole bundle gets an ad-hoc signature and Sparkle relies on its EdDSA
// signature, like the Flutter macOS app. No hardened runtime here: it would
// stop the ad-hoc app from loading Sparkle.framework. When a Developer ID is
// configured, electron-builder signs again on top, with the hardened runtime.
const { execFileSync } = require('node:child_process');
const { readdirSync } = require('node:fs');
const path = require('node:path');

exports.default = async function afterPack(context) {
  if (context.electronPlatformName !== 'darwin') return;
  // The single-arch halves of a universal build are merged first; the merged
  // app comes back through this hook.
  if (context.appOutDir.endsWith('-temp')) return;
  for (const name of readdirSync(context.appOutDir)) {
    if (!name.endsWith('.app')) continue;
    execFileSync('codesign', ['--force', '--deep', '--sign', '-', '--timestamp=none', path.join(context.appOutDir, name)], {
      stdio: 'inherit',
    });
  }
};
