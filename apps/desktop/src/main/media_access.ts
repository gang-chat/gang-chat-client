import { ipcMain, systemPreferences } from 'electron';

import { IpcChannels } from '../shared/ipc';

/**
 * OS-level camera / microphone access.
 *
 * The renderer's own permission handler (`index.ts`) only answers Chromium.
 * macOS keeps a separate privacy switch per app, and an Electron process that
 * was never granted the camera gets a live `getUserMedia` track that delivers
 * no frames: the camera looks on while viewers see nothing. Asking here first
 * shows the system prompt for Gang Chat, and tells the renderer when access is
 * switched off so it can say so instead.
 *
 * PORT: the Flutter runner relied on the capture plugin's own prompt; there is
 * no Dart counterpart.
 */

type MediaKind = 'camera' | 'microphone';

async function requestAccess(kind: MediaKind): Promise<boolean> {
  if (process.platform === 'darwin') {
    const status = systemPreferences.getMediaAccessStatus(kind);
    if (status === 'granted') return true;
    if (status === 'not-determined') return systemPreferences.askForMediaAccess(kind);
    return false;
  }
  if (process.platform === 'win32') {
    // Settings > Privacy & security > Camera / Microphone; Windows has no prompt.
    return systemPreferences.getMediaAccessStatus(kind) !== 'denied';
  }
  return true;
}

const handlers: Record<string, (...args: never[]) => unknown> = {
  requestAccess(kind: MediaKind) {
    if (kind !== 'camera' && kind !== 'microphone') throw new Error(`Unknown media kind: ${String(kind)}`);
    return requestAccess(kind);
  },
};

export function registerMediaAccess(): void {
  ipcMain.handle(IpcChannels.mediaAccessInvoke, async (_event, op: string, ...args: unknown[]) => {
    const handler = handlers[op];
    if (!handler) throw new Error(`Unknown media access operation: ${op}`);
    return (handler as (...rest: unknown[]) => unknown)(...args);
  });
}
