import { app, ipcMain, type WebContents } from 'electron';
import { autoUpdater, CancellationToken, type UpdateInfo } from 'electron-updater';

import { IpcChannels } from '../shared/ipc';
import { markQuitting } from './window';

declare const __GANG_MAC_UPDATES_SIGNED__: boolean;

/**
 * In-app updates through electron-updater (built on Electron's autoUpdater).
 *
 * The feed is the release bucket's `releases/` folder, where the release
 * workflow uploads the installers next to electron-builder's `latest.yml` /
 * `latest-mac.yml`. Windows installs the NSIS package (differential download
 * via blockmaps); macOS installs through Squirrel.Mac, which only accepts
 * updates for a code-signed app — unsigned macOS builds report the updater
 * unavailable and keep the Flutter client's "download the DMG" flow.
 */

let configured = false;
let lastInfo: UpdateInfo | null = null;
let downloadToken: CancellationToken | null = null;

function available(): boolean {
  if (!app.isPackaged) return false;
  if (process.platform === 'win32') return true;
  if (process.platform === 'darwin') return __GANG_MAC_UPDATES_SIGNED__;
  return false;
}

function configure(feedBaseUrl: string): void {
  if (configured) return;
  configured = true;
  autoUpdater.autoDownload = false;
  autoUpdater.autoInstallOnAppQuit = false;
  autoUpdater.allowDowngrade = false;
  autoUpdater.allowPrerelease = false;
  autoUpdater.setFeedURL({ provider: 'generic', url: feedBaseUrl.replace(/\/$/, '') + '/releases' });
}

function notesText(info: UpdateInfo): string | null {
  const notes = info.releaseNotes;
  if (notes == null) return null;
  if (typeof notes === 'string') return notes;
  return notes.map((n) => n.note ?? '').join('\n').trim() || null;
}

export function registerUpdater(releaseBucketUrl: string): void {
  ipcMain.handle(IpcChannels.updaterInvoke, async (event, op: string) => {
    switch (op) {
      case 'isAvailable':
        return available();
      case 'checkForUpdate': {
        if (!available()) return null;
        configure(releaseBucketUrl);
        const result = await autoUpdater.checkForUpdates();
        if (!result || !result.isUpdateAvailable) {
          lastInfo = null;
          return null;
        }
        lastInfo = result.updateInfo;
        return {
          version: result.updateInfo.version,
          releaseDate: result.updateInfo.releaseDate ?? null,
          releaseNotes: notesText(result.updateInfo),
        };
      }
      case 'downloadUpdate':
        return download(event.sender);
      case 'cancelDownload':
        downloadToken?.cancel();
        return null;
      case 'quitAndInstall':
        // The renderer has already run its shutdown hooks (left the voice
        // channel); skip the close interception and let the installer run.
        markQuitting();
        setImmediate(() => autoUpdater.quitAndInstall(false, true));
        return null;
      default:
        throw new Error(`Unknown updater operation: ${op}`);
    }
  });
}

async function download(sender: WebContents): Promise<void> {
  if (!lastInfo) throw new Error('No update has been found to download.');
  downloadToken?.cancel();
  const token = new CancellationToken();
  downloadToken = token;
  const onProgress = (progress: { transferred: number; total: number }) => {
    if (sender.isDestroyed()) return;
    sender.send(IpcChannels.updaterProgress, {
      receivedBytes: progress.transferred,
      totalBytes: progress.total > 0 ? progress.total : null,
    });
  };
  autoUpdater.on('download-progress', onProgress);
  try {
    await autoUpdater.downloadUpdate(token);
  } finally {
    autoUpdater.removeListener('download-progress', onProgress);
    if (downloadToken === token) downloadToken = null;
  }
}
