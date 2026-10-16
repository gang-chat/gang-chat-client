import { app, ipcMain } from 'electron';
import path from 'node:path';

import { IpcChannels } from '../shared/ipc';

/**
 * macOS in-app updates through Sparkle, as in the Flutter client
 * (macos/Runner/AppDelegate.swift): both apps read the bucket's
 * releases/appcast.xml and verify updates with the same EdDSA key, so Flutter
 * installs move to this app through the same feed that keeps it current.
 * Sparkle shows its own windows, downloads, verifies and installs the DMG, then
 * relaunches; it needs no Developer ID signature.
 *
 * `native/sparkle/sparkle.mm` (built by scripts/prepare_sparkle.mjs) drives
 * Sparkle.framework, which the package ships in Contents/Frameworks.
 * Development runs and other platforms report it unavailable, and the update
 * gate falls back to the release-bucket download.
 */

interface SparkleModule {
  isAvailable(): boolean;
  checkForUpdates(feedUrl: string | null, background: boolean): boolean;
}

let sparkleModule: SparkleModule | null | undefined;

function loadSparkle(): SparkleModule | null {
  if (sparkleModule !== undefined) return sparkleModule;
  sparkleModule = null;
  if (process.platform !== 'darwin' || !app.isPackaged) return sparkleModule;
  try {
    const module = { exports: {} as SparkleModule };
    process.dlopen(module, path.join(process.resourcesPath, 'sparkle', 'sparkle.node'));
    sparkleModule = module.exports;
  } catch (error) {
    console.error('[sparkle] could not load the updater', error);
  }
  return sparkleModule;
}

export function registerSparkle(): void {
  ipcMain.handle(IpcChannels.sparkleInvoke, (_event, op: string, ...args: unknown[]) => {
    const sparkle = loadSparkle();
    switch (op) {
      case 'isAvailable':
        return sparkle?.isAvailable() ?? false;
      case 'checkForUpdates': {
        const [feedUrl, background] = args as [string | null, boolean];
        if (!sparkle?.checkForUpdates(feedUrl ?? null, background === true)) {
          throw new Error('In-app updates are not configured for this build.');
        }
        return null;
      }
      default:
        throw new Error(`Unknown Sparkle operation: ${op}`);
    }
  });
}
