import type { BootSnapshot } from '../../../shared/ipc';

/** Typed view of `window.gangDesktop` (exposed by `src/preload/index.ts`). */
export interface DesktopBridge {
  readonly boot: BootSnapshot;
  invoke(channel: string, ...args: unknown[]): Promise<unknown>;
  send(channel: string, ...args: unknown[]): void;
  on(channel: string, listener: (...args: unknown[]) => void): () => void;
  getPathForFile(file: File): string;
}

declare global {
  interface Window {
    gangDesktop?: DesktopBridge;
  }
}

export function desktopBridge(): DesktopBridge {
  const bridge = window.gangDesktop;
  if (!bridge) throw new Error('gangDesktop bridge is missing: the renderer must run inside the Electron app.');
  return bridge;
}
