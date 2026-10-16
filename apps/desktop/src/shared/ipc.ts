/**
 * IPC contract between the Electron main process and the renderer.
 *
 * The preload script exposes a deliberately small, generic surface
 * (`window.gangDesktop`): `invoke(channel, ...args)`, `send`, `on`, plus the
 * synchronous boot snapshot. The renderer's platform bridge
 * (`src/renderer/src/platform/`) turns it into the `Platform` interface from
 * `@gang-chat/core/platform/platform`.
 */

export const IpcChannels = {
  // HTTP streaming bridge (requests run in the main process).
  httpOpen: 'http:open',
  httpWrite: 'http:write',
  httpEnd: 'http:end',
  httpAbort: 'http:abort',
  httpResponse: 'http:response',
  httpData: 'http:data',
  httpDone: 'http:done',
  httpError: 'http:error',

  // Storage.
  secureRead: 'secure:read',
  secureWrite: 'secure:write',
  secureDelete: 'secure:delete',
  prefsSet: 'prefs:set',
  prefsRemove: 'prefs:remove',

  // Files & dialogs.
  filesInvoke: 'files:invoke',

  // Clipboard.
  clipboardInvoke: 'clipboard:invoke',

  // Window / tray.
  windowInvoke: 'window:invoke',
  windowEvent: 'window:event',

  // Notifications.
  notificationShow: 'notification:show',
  notificationClicked: 'notification:clicked',
  badgeSet: 'badge:set',

  // Screen capture.
  screenInvoke: 'screen:invoke',

  // OS camera / microphone access.
  mediaAccessInvoke: 'media-access:invoke',

  // In-app updates (electron-updater).
  updaterInvoke: 'updater:invoke',
  updaterProgress: 'updater:progress',

  // Processes & shell.
  processInvoke: 'process:invoke',
  openExternal: 'shell:open-external',

  // Boot.
  bootSnapshot: 'boot:snapshot',
  log: 'log',
} as const;

export type HostOsName = 'windows' | 'macos' | 'linux';

/** Delivered synchronously to the preload before the renderer boots. */
export interface BootSnapshot {
  os: HostOsName;
  appName: string;
  appVersion: string;
  buildNumber: string;
  isPackaged: boolean;
  installedAt: string | null;
  /** Non-sensitive preferences (SharedPreferences equivalent). */
  preferences: Record<string, PreferenceValue>;
  /** Whether a refresh token is stored, so the first window can be sized right. */
  hasStoredSession: boolean;
  /** Contents of assets/config/app_config.json. */
  appConfig: Record<string, unknown> | null;
  configOverrides: { apiBaseUrl: string; assetBaseUrl: string; releaseBucketUrl: string };
  paths: {
    temporary: string;
    applicationSupport: string;
    applicationCache: string;
    downloads: string | null;
    /** Windows %APPDATA% (roaming); null elsewhere. */
    roamingAppData: string | null;
  };
  /** Scheme of the media cache protocol (`gang-media`). */
  mediaScheme: string;
}

export type PreferenceValue = string | number | boolean | string[];

export interface HttpOpenRequest {
  method: string;
  url: string;
  headers: Record<string, string>;
  hasBody: boolean;
  contentLength: number | null;
}

export interface HttpResponseEvent {
  id: number;
  statusCode: number;
  headers: Record<string, string>;
  contentLength: number | null;
}

export interface FileEntry {
  name: string;
  path: string;
  isDirectory: boolean;
  size: number;
  modifiedMs: number;
}

export interface PickedFile {
  name: string;
  path: string;
  size: number;
  mimeType: string | null;
}

export interface ScreenSourceInfo {
  id: string;
  name: string;
  isWindow: boolean;
  thumbnailDataUrl: string | null;
  appIconDataUrl: string | null;
  displayId: string | null;
}

export type WindowEventPayload =
  | { type: 'close-requested' }
  | { type: 'tray-exit-requested' }
  | { type: 'tray-open-requested' }
  | { type: 'maximized-changed'; maximized: boolean }
  | { type: 'fullscreen-changed'; fullScreen: boolean }
  | { type: 'focus-changed'; focused: boolean }
  | { type: 'visibility-changed'; visible: boolean };

export const kMediaScheme = 'gang-media';
