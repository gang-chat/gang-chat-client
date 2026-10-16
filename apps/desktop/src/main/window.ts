import {
  app,
  BrowserWindow,
  ipcMain,
  Menu,
  nativeImage,
  powerSaveBlocker,
  screen,
  Tray,
  type NativeImage,
} from 'electron';
import path from 'node:path';

import { IpcChannels, type WindowEventPayload } from '../shared/ipc';
import { flushPreferences } from './storage';

/**
 * The main window, tray and close handling — the equivalent of the Flutter
 * runners (`windows/runner/flutter_window.cpp`, `MainFlutterWindow.swift`)
 * plus `window_manager`.
 */

// Same native canvas colour as the Flutter runner (`appWindowBackground`).
export const appWindowBackground = '#14171D';

// macOS: the traffic lights sit centred in the 44px title-bar band, inset by
// the same gap on the left (MainFlutterWindow.repositionTrafficLights).
const kTitleBarHeight = 44;
const kTrafficLightSize = 14;
const kTrafficLightInset = (kTitleBarHeight - kTrafficLightSize) / 2;

let mainWindow: BrowserWindow | null = null;
let allowClose = false;
let quitting = false;
let tray: Tray | null = null;
let trayIcon: NativeImage | null = null;
let blinkTimer: NodeJS.Timeout | null = null;
let powerBlockerId: number | null = null;

export function getMainWindow(): BrowserWindow | null {
  return mainWindow;
}

function resourcePath(...segments: string[]): string {
  return app.isPackaged
    ? path.join(process.resourcesPath, ...segments)
    : path.join(__dirname, '../../build', ...segments);
}

function appIcon(): NativeImage {
  return nativeImage.createFromPath(resourcePath(process.platform === 'win32' ? 'icon.ico' : 'icon.png'));
}

function emit(event: WindowEventPayload): void {
  if (!mainWindow || mainWindow.isDestroyed()) return;
  mainWindow.webContents.send(IpcChannels.windowEvent, event);
}

export function createMainWindow(options: { authenticated: boolean; preload: string; loadUrl?: string; loadFile?: string }): BrowserWindow {
  const isMac = process.platform === 'darwin';
  const window = new BrowserWindow({
    width: 1180,
    height: 760,
    minWidth: 1,
    minHeight: 1,
    show: false,
    backgroundColor: appWindowBackground,
    title: 'Gang Chat',
    icon: process.platform === 'darwin' ? undefined : appIcon(),
    // Windows/Linux: the app draws its own window controls, like the Flutter
    // shell. macOS keeps the native traffic lights over a hidden title bar.
    frame: isMac,
    titleBarStyle: isMac ? 'hidden' : 'default',
    trafficLightPosition: isMac ? { x: kTrafficLightInset, y: kTrafficLightInset } : undefined,
    thickFrame: true,
    autoHideMenuBar: true,
    webPreferences: {
      preload: options.preload,
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
      spellcheck: false,
      backgroundThrottling: false,
      // Remote voices, the music box and notification tones must play
      // without a click first, like the native Flutter app.
      autoplayPolicy: 'no-user-gesture-required',
    },
  });
  mainWindow = window;
  if (!isMac) window.removeMenu();

  window.on('close', (event) => {
    if (allowClose || quitting) return;
    event.preventDefault();
    emit({ type: 'close-requested' });
  });
  window.on('closed', () => {
    mainWindow = null;
  });
  window.on('maximize', () => emit({ type: 'maximized-changed', maximized: true }));
  window.on('unmaximize', () => emit({ type: 'maximized-changed', maximized: false }));
  window.on('enter-full-screen', () => emit({ type: 'fullscreen-changed', fullScreen: true }));
  window.on('leave-full-screen', () => emit({ type: 'fullscreen-changed', fullScreen: false }));
  window.on('focus', () => {
    stopAttention();
    emit({ type: 'focus-changed', focused: true });
  });
  window.on('blur', () => emit({ type: 'focus-changed', focused: false }));
  window.on('show', () => emit({ type: 'visibility-changed', visible: true }));
  window.on('hide', () => emit({ type: 'visibility-changed', visible: false }));

  // External links never open inside the app window.
  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  window.webContents.on('will-navigate', (event, url) => {
    if (url !== window.webContents.getURL()) event.preventDefault();
  });

  if (options.loadUrl) void window.loadURL(options.loadUrl);
  else if (options.loadFile) void window.loadFile(options.loadFile);
  return window;
}

function ensureTray(): boolean {
  // Like the Flutter shell, only Windows minimizes to a tray icon.
  if (process.platform !== 'win32') return false;
  if (tray) return true;
  trayIcon = appIcon().resize({ width: 16, height: 16 });
  tray = new Tray(trayIcon);
  tray.setToolTip('Gang Chat');
  tray.setContextMenu(
    Menu.buildFromTemplate([
      { label: '打开 Gang Chat', click: () => openFromTray() },
      { type: 'separator' },
      { label: '退出', click: () => emit({ type: 'tray-exit-requested' }) },
    ]),
  );
  tray.on('click', () => openFromTray());
  tray.on('double-click', () => openFromTray());
  return true;
}

/** Restores natively (works even if the renderer is busy) and tells the app. */
function openFromTray() {
  restoreFromTray();
  emit({ type: 'tray-open-requested' });
}

function restoreFromTray() {
  const window = mainWindow;
  if (!window) return;
  window.setSkipTaskbar(false);
  window.show();
  window.focus();
}

function stopAttention() {
  if (blinkTimer) {
    clearInterval(blinkTimer);
    blinkTimer = null;
  }
  if (tray && trayIcon) tray.setImage(trayIcon);
  mainWindow?.flashFrame(false);
}

function requestAttention() {
  const window = mainWindow;
  if (!window || window.isFocused()) return;
  if (process.platform === 'darwin') {
    app.dock?.bounce('informational');
    return;
  }
  window.flashFrame(true);
  // The Flutter runner kept the tray icon blinking until the window became
  // foreground.
  if (tray && trayIcon && !blinkTimer) {
    const empty = nativeImage.createEmpty();
    let visible = true;
    blinkTimer = setInterval(() => {
      visible = !visible;
      tray?.setImage(visible ? trayIcon! : empty);
    }, 500);
  }
}

export function destroyTray(): void {
  stopAttention();
  tray?.destroy();
  tray = null;
}

export function quitApplication(): void {
  quitting = true;
  allowClose = true;
  flushPreferences();
  destroyTray();
  app.quit();
}

export function markQuitting(): void {
  quitting = true;
}

export function isQuitting(): boolean {
  return quitting;
}

/** Asks the renderer to exit through its own cleanup path; false when no window can answer. */
export function requestExitFromRenderer(): boolean {
  if (!mainWindow || mainWindow.isDestroyed() || mainWindow.webContents.isCrashed()) return false;
  emit({ type: 'tray-exit-requested' });
  return true;
}

type WindowHandler = (window: BrowserWindow, ...args: never[]) => unknown;

const handlers: Record<string, WindowHandler> = {
  show(window) {
    window.show();
  },
  hide(window) {
    window.hide();
  },
  focus(window) {
    window.focus();
  },
  minimize(window) {
    window.minimize();
  },
  toggleMaximize(window) {
    if (window.isMaximized()) window.unmaximize();
    else window.maximize();
  },
  maximize(window) {
    window.maximize();
  },
  unmaximize(window) {
    window.unmaximize();
  },
  isMaximized(window) {
    return window.isMaximized();
  },
  isMinimized(window) {
    return window.isMinimized();
  },
  isVisible(window) {
    return window.isVisible();
  },
  isFocused(window) {
    return window.isFocused();
  },
  setFullScreen(window, fullScreen: boolean) {
    window.setFullScreen(fullScreen);
  },
  isFullScreen(window) {
    return window.isFullScreen();
  },
  close(window) {
    window.close();
  },
  minimizeToTray(window) {
    // DesktopWindowController falls back to minimize() when this throws.
    if (!ensureTray()) throw new Error('No tray icon is available on this platform');
    window.setSkipTaskbar(true);
    window.hide();
  },
  restoreFromTray() {
    restoreFromTray();
  },
  getSize(window) {
    const [width, height] = window.getSize();
    return { width, height };
  },
  setSize(window, width: number, height: number, animate?: boolean) {
    window.setSize(Math.round(width), Math.round(height), animate === true);
  },
  setContentSize(window, width: number, height: number) {
    window.setContentSize(Math.round(width), Math.round(height));
  },
  setMinimumSize(window, width: number, height: number) {
    window.setMinimumSize(Math.round(width), Math.round(height));
  },
  setMaximumSize(window, width: number, height: number) {
    // Electron treats 0 as "unbounded".
    const clamp = (n: number) => (n >= 100000 ? 0 : Math.round(n));
    window.setMaximumSize(clamp(width), clamp(height));
  },
  setResizable(window, resizable: boolean) {
    window.setResizable(resizable);
  },
  setMaximizable(window, maximizable: boolean) {
    window.setMaximizable(maximizable);
  },
  setFullScreenable(window, fullScreenable: boolean) {
    window.setFullScreenable(fullScreenable);
  },
  center(window) {
    window.center();
  },
  setOpacity(window, opacity: number) {
    window.setOpacity(opacity);
  },
  setSkipTaskbar(window, skip: boolean) {
    window.setSkipTaskbar(skip);
  },
  setHasShadow(window, hasShadow: boolean) {
    window.setHasShadow(hasShadow);
  },
  setTitle(window, title: string) {
    window.setTitle(title);
  },
  /** macOS: show/hide the traffic lights (hidden on the login card). */
  setWindowButtonVisibility(window, visible: boolean) {
    if (process.platform === 'darwin') window.setWindowButtonVisibility(visible);
  },
  setAlwaysOnTop(window, onTop: boolean) {
    window.setAlwaysOnTop(onTop);
  },
  getBounds(window) {
    return window.getBounds();
  },
  setBounds(window, bounds: { x?: number; y?: number; width?: number; height?: number }) {
    window.setBounds(bounds);
  },
  workArea(window) {
    return screen.getDisplayMatching(window.getBounds()).workArea;
  },
  requestAttention() {
    requestAttention();
  },
  setPreventSleep(_window, active: boolean) {
    if (active && powerBlockerId == null) {
      powerBlockerId = powerSaveBlocker.start('prevent-display-sleep');
    } else if (!active && powerBlockerId != null) {
      powerSaveBlocker.stop(powerBlockerId);
      powerBlockerId = null;
    }
  },
  setContentProtection(window, excluded: boolean) {
    // WDA_EXCLUDEFROMCAPTURE on Windows 10 2004+, NSWindowSharingNone on macOS.
    window.setContentProtection(excluded);
    return process.platform === 'win32' || process.platform === 'darwin';
  },
  setTrayTooltip(_window, tooltip: string) {
    tray?.setToolTip(tooltip);
  },
  respondToClose(window, allow: boolean) {
    if (!allow) return;
    allowClose = true;
    window.close();
  },
  quit() {
    quitApplication();
  },
};

export function registerWindowIpc(): void {
  ipcMain.handle(IpcChannels.windowInvoke, (event, op: string, ...args: unknown[]) => {
    const window = BrowserWindow.fromWebContents(event.sender);
    const handler = handlers[op];
    if (!handler) throw new Error(`Unknown window operation: ${op}`);
    if (!window) return null;
    return (handler as (window: BrowserWindow, ...rest: unknown[]) => unknown)(window, ...args);
  });
}
