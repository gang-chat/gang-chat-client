import { app, ipcMain, Notification, shell, type BrowserWindow } from 'electron';
import { execFile, spawn } from 'node:child_process';

import { IpcChannels } from '../shared/ipc';
import { getMainWindow } from './window';

/** Notifications, external links and child processes. */

let activeNotifications = new Set<Notification>();

export function registerSystem(): void {
  ipcMain.handle(
    IpcChannels.notificationShow,
    (event, options: { id: number; title: string; body: string; silent?: boolean; tag?: string }) => {
      if (!Notification.isSupported()) return false;
      const notification = new Notification({
        title: options.title,
        body: options.body,
        silent: options.silent ?? true,
      });
      activeNotifications.add(notification);
      notification.on('click', () => {
        const window: BrowserWindow | null = getMainWindow();
        if (window) {
          window.setSkipTaskbar(false);
          window.show();
          window.focus();
        }
        if (!event.sender.isDestroyed()) event.sender.send(IpcChannels.notificationClicked, options.id);
      });
      notification.on('close', () => activeNotifications.delete(notification));
      notification.show();
      return true;
    },
  );

  ipcMain.handle(IpcChannels.badgeSet, (_event, count: number) => {
    if (process.platform === 'darwin') {
      app.dock?.setBadge(count > 0 ? String(count > 99 ? '99+' : count) : '');
    } else {
      app.setBadgeCount(count);
    }
  });

  ipcMain.handle(IpcChannels.openExternal, async (_event, url: string) => {
    if (!/^(https?|mailto):/i.test(url)) return false;
    try {
      await shell.openExternal(url);
      return true;
    } catch {
      return false;
    }
  });

  ipcMain.handle(IpcChannels.processInvoke, (_event, op: string, executable: string, args: string[], options?: { timeoutMs?: number }) => {
    if (op === 'spawnDetached') {
      const child = spawn(executable, args, { detached: true, stdio: 'ignore', windowsHide: false });
      child.unref();
      return null;
    }
    if (op === 'run') {
      return new Promise((resolve) => {
        execFile(
          executable,
          args,
          { windowsHide: true, timeout: options?.timeoutMs ?? 60_000, maxBuffer: 16 * 1024 * 1024 },
          (error, stdout, stderr) => {
            const exitCode = error ? (typeof error.code === 'number' ? error.code : 1) : 0;
            resolve({ exitCode, stdout: String(stdout), stderr: String(stderr) });
          },
        );
      });
    }
    throw new Error(`Unknown process operation: ${op}`);
  });

  ipcMain.on(IpcChannels.log, (_event, level: string, message: string) => {
    const line = `[renderer:${level}] ${message}`;
    if (level === 'error') console.error(line);
    else console.log(line);
  });
}

export function clearNotifications(): void {
  for (const notification of activeNotifications) notification.close();
  activeNotifications = new Set();
}
