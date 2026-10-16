import { desktopCapturer, ipcMain, session, shell, systemPreferences } from 'electron';
import os from 'node:os';

import { IpcChannels, type ScreenSourceInfo } from '../shared/ipc';

/**
 * Screen / window capture for screen sharing.
 *
 * The Flutter client showed its own source picker (thumbnails from
 * flutter_webrtc's desktopCapturer) and then captured the chosen source. Here
 * the renderer shows the same picker over `desktopCapturer.getSources`, calls
 * `selectSource`, and then `navigator.mediaDevices.getDisplayMedia()`, which
 * the display-media handler below resolves to the pre-selected source —
 * Chromium's own picker is never shown.
 *
 * System audio:
 *  - Windows: `audio: 'loopback'` (WASAPI loopback), the same source the
 *    Flutter fork fed into its separate screen-audio participant.
 *  - macOS: Chromium's ScreenCaptureKit loopback is enabled with feature
 *    flags in `index.ts` (macOS 14.2+). Where it is unavailable the share
 *    simply has no audio track. PORT: the Flutter fork captured macOS audio
 *    with its own ScreenCaptureKit device.
 */

interface PendingSelection {
  sourceId: string;
  audio: boolean;
  expiresAt: number;
}

let pending: PendingSelection | null = null;

function macOSMajorVersion(): number {
  // Darwin 23 = macOS 14, Darwin 24 = macOS 15 ...
  const darwin = Number(os.release().split('.')[0]);
  return Number.isFinite(darwin) ? darwin - 9 : 0;
}

export function systemAudioSupported(): boolean {
  if (process.platform === 'win32') return true;
  if (process.platform === 'darwin') return macOSMajorVersion() >= 14;
  return false;
}

export function installDisplayMediaHandler(): void {
  session.defaultSession.setDisplayMediaRequestHandler(
    async (request, callback) => {
      const selection = pending;
      pending = null;
      if (!selection || selection.expiresAt < Date.now()) {
        // No source was chosen in our picker: deny rather than guess.
        callback({});
        return;
      }
      try {
        const sources = await desktopCapturer.getSources({ types: ['screen', 'window'], thumbnailSize: { width: 0, height: 0 } });
        const source = sources.find((s) => s.id === selection.sourceId);
        if (!source) {
          callback({});
          return;
        }
        const wantsAudio = selection.audio && request.audioRequested && systemAudioSupported();
        callback(wantsAudio ? { video: source, audio: 'loopback' } : { video: source });
      } catch {
        callback({});
      }
    },
    { useSystemPicker: false },
  );
}

const handlers: Record<string, (...args: never[]) => unknown> = {
  async listSources(options: { thumbnailWidth: number; thumbnailHeight: number }): Promise<ScreenSourceInfo[]> {
    const sources = await desktopCapturer.getSources({
      types: ['screen', 'window'],
      thumbnailSize: { width: options.thumbnailWidth, height: options.thumbnailHeight },
      fetchWindowIcons: true,
    });
    return sources.map((source) => ({
      id: source.id,
      name: source.name,
      isWindow: source.id.startsWith('window:'),
      thumbnailDataUrl: source.thumbnail.isEmpty() ? null : source.thumbnail.toDataURL(),
      appIconDataUrl: source.appIcon && !source.appIcon.isEmpty() ? source.appIcon.toDataURL() : null,
      displayId: source.display_id || null,
    }));
  },

  selectSource(sourceId: string, options: { audio: boolean }) {
    pending = { sourceId, audio: options.audio, expiresAt: Date.now() + 30_000 };
  },

  supportsSystemAudio() {
    return systemAudioSupported();
  },

  permissionStatus() {
    if (process.platform !== 'darwin') return 'granted';
    return systemPreferences.getMediaAccessStatus('screen');
  },

  async openPermissionSettings() {
    if (process.platform === 'darwin') {
      await shell.openExternal('x-apple.systempreferences:com.apple.preference.security?Privacy_ScreenCapture');
    }
  },
};

export function registerScreenCapture(): void {
  ipcMain.handle(IpcChannels.screenInvoke, async (_event, op: string, ...args: unknown[]) => {
    const handler = handlers[op];
    if (!handler) throw new Error(`Unknown screen operation: ${op}`);
    return (handler as (...rest: unknown[]) => unknown)(...args);
  });
}
