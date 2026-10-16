import { contextBridge, ipcRenderer, webUtils } from 'electron';

import { IpcChannels, type BootSnapshot } from '../shared/ipc';

/**
 * The only bridge between the sandboxed renderer and the main process.
 * Channels are allow-listed; the renderer's platform layer builds the
 * `Platform` interface of `@gang-chat/core` on top of this.
 */
const allowedChannels = new Set<string>(Object.values(IpcChannels));

function assertChannel(channel: string): void {
  if (!allowedChannels.has(channel)) throw new Error(`IPC channel not allowed: ${channel}`);
}

const boot = ipcRenderer.sendSync(IpcChannels.bootSnapshot) as BootSnapshot;

const api = {
  boot,
  invoke(channel: string, ...args: unknown[]): Promise<unknown> {
    assertChannel(channel);
    return ipcRenderer.invoke(channel, ...args);
  },
  send(channel: string, ...args: unknown[]): void {
    assertChannel(channel);
    ipcRenderer.send(channel, ...args);
  },
  on(channel: string, listener: (...args: unknown[]) => void): () => void {
    assertChannel(channel);
    const wrapped = (_event: Electron.IpcRendererEvent, ...args: unknown[]) => listener(...args);
    ipcRenderer.on(channel, wrapped);
    return () => ipcRenderer.removeListener(channel, wrapped);
  },
  /** Absolute path of a dropped / picked DOM File (File.path was removed in Electron 32). */
  getPathForFile(file: File): string {
    try {
      return webUtils.getPathForFile(file);
    } catch {
      return '';
    }
  },
};

export type GangDesktopApi = typeof api;

contextBridge.exposeInMainWorld('gangDesktop', api);
