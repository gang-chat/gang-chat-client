import type {
  AppInfo,
  ClipboardBridge,
  ClipboardImage,
  FileSystemBridge,
  MediaAccessBridge,
  NotificationBridge,
  Platform,
  PlatformFile,
  PreferencesStore,
  ProcessBridge,
  ScreenCaptureBridge,
  ScreenCaptureSource,
  SecureStorage,
  NativeUpdateInfo,
  SparkleBridge,
  SpeechBridge,
  SystemNotificationOptions,
  UpdaterBridge,
  WindowBridge,
  WindowEvent,
} from '@gang-chat/core/platform/platform';

import { createBrowserSpeech } from '@gang-chat/ui/platform/browser_speech';

import { IpcChannels, type FileEntry, type PickedFile, type PreferenceValue, type ScreenSourceInfo } from '../../../shared/ipc';
import type { DesktopBridge } from './desktop_bridge';
import { IpcHttpClient } from './ipc_http_client';

/**
 * The Electron implementation of `Platform` (`@gang-chat/core`). Everything
 * native goes through the preload bridge to the main process; browser-level
 * capabilities (speech synthesis) stay in the renderer.
 */
export function createDesktopPlatform(bridge: DesktopBridge): Platform {
  const boot = bridge.boot;
  const os = boot.os;

  const invokeFiles = <T>(op: string, ...args: unknown[]) => bridge.invoke(IpcChannels.filesInvoke, op, ...args) as Promise<T>;
  const invokeClipboard = <T>(op: string, ...args: unknown[]) => bridge.invoke(IpcChannels.clipboardInvoke, op, ...args) as Promise<T>;
  const invokeWindow = <T>(op: string, ...args: unknown[]) => bridge.invoke(IpcChannels.windowInvoke, op, ...args) as Promise<T>;
  const invokeScreen = <T>(op: string, ...args: unknown[]) => bridge.invoke(IpcChannels.screenInvoke, op, ...args) as Promise<T>;

  const toPlatformFile = (file: PickedFile): PlatformFile => ({
    name: file.name,
    path: file.path,
    mimeType: file.mimeType,
    size: file.size,
    readAsBytes: () => invokeFiles<Uint8Array>('readFile', file.path),
  });

  const app: AppInfo = {
    name: boot.appName,
    version: boot.appVersion,
    buildNumber: boot.buildNumber,
    installedAt: boot.installedAt,
    isPackaged: boot.isPackaged,
  };

  const secureStorage: SecureStorage = {
    read: (key) => bridge.invoke(IpcChannels.secureRead, key) as Promise<string | null>,
    write: async (key, value) => {
      await bridge.invoke(IpcChannels.secureWrite, key, value);
    },
    delete: async (key) => {
      await bridge.invoke(IpcChannels.secureDelete, key);
    },
  };

  const preferences = new DesktopPreferences(bridge, boot.preferences);

  const separator = os === 'windows' ? '\\' : '/';
  const files: FileSystemBridge = {
    async openFile(options) {
      const picked = await invokeFiles<PickedFile[]>('openFiles', { ...options, multiple: false });
      return picked[0] ? toPlatformFile(picked[0]) : null;
    },
    async openFiles(options) {
      const picked = await invokeFiles<PickedFile[]>('openFiles', { ...options, multiple: true });
      return picked.map(toPlatformFile);
    },
    getSaveLocation: (options) => invokeFiles<string | null>('getSaveLocation', options),
    async filesFromPaths(paths) {
      const picked = await invokeFiles<PickedFile[]>('filesFromPaths', paths);
      return picked.map(toPlatformFile);
    },
    writeFile: (path, bytes) => invokeFiles<void>('writeFile', path, bytes),
    appendFile: (path, bytes) => invokeFiles<void>('appendFile', path, bytes),
    readFile: (path) => invokeFiles<Uint8Array>('readFile', path),
    async *openRead(path: string): AsyncIterable<Uint8Array> {
      const chunkSize = 1024 * 1024;
      for (let offset = 0; ; offset += chunkSize) {
        const chunk = await invokeFiles<Uint8Array>('readFileChunk', path, offset, chunkSize);
        if (chunk.length === 0) return;
        yield chunk;
        if (chunk.length < chunkSize) return;
      }
    },
    setLastModified: (path, modifiedMs) => invokeFiles<void>('setLastModified', path, modifiedMs),
    deleteFile: (path) => invokeFiles<void>('deleteFile', path),
    rename: (from, to) => invokeFiles<void>('rename', from, to),
    exists: (path) => invokeFiles<boolean>('exists', path),
    fileSize: (path) => invokeFiles<number | null>('fileSize', path),
    listDirectory: (path) => invokeFiles<FileEntry[]>('listDirectory', path),
    ensureDirectory: (path) => invokeFiles<void>('ensureDirectory', path),
    directories: async () => boot.paths,
    joinPath: (...segments) =>
      segments
        .filter((s) => s.length > 0)
        .map((s, i) => (i === 0 ? s.replace(/[\\/]+$/, '') : s.replace(/^[\\/]+|[\\/]+$/g, '')))
        .join(separator),
    openPath: (path) => invokeFiles<boolean>('openPath', path),
    showInFolder: (path) => invokeFiles<void>('showInFolder', path),
  };

  const clipboard: ClipboardBridge = {
    readText: () => invokeClipboard<string | null>('readText'),
    writeText: (text) => invokeClipboard<void>('writeText', text),
    readFilePaths: () => invokeClipboard<string[]>('readFilePaths'),
    writeFilePaths: (paths) => invokeClipboard<boolean>('writeFilePaths', paths),
    readImage: () => invokeClipboard<ClipboardImage | null>('readImage'),
    writeImage: (bytes) => invokeClipboard<boolean>('writeImage', bytes),
  };

  let nextNotificationId = 1;
  const notificationClicks = new Map<number, () => void>();
  bridge.on(IpcChannels.notificationClicked, (id) => {
    const handler = notificationClicks.get(id as number);
    notificationClicks.delete(id as number);
    handler?.();
  });
  const notifications: NotificationBridge = {
    isSupported: () => true,
    async show(options: SystemNotificationOptions, onClick?: () => void) {
      const id = nextNotificationId++;
      if (onClick) notificationClicks.set(id, onClick);
      await bridge.invoke(IpcChannels.notificationShow, {
        id,
        title: options.title,
        body: options.body,
        silent: options.silent,
        tag: options.tag,
      });
    },
    setBadgeCount: async (count) => {
      await bridge.invoke(IpcChannels.badgeSet, count);
    },
  };

  const windowListeners = new Set<(event: WindowEvent) => void>();
  bridge.on(IpcChannels.windowEvent, (event) => {
    for (const listener of [...windowListeners]) listener(event as WindowEvent);
  });
  const windowBridge: WindowBridge & Record<string, unknown> = {
    show: () => invokeWindow('show'),
    hide: () => invokeWindow('hide'),
    focus: () => invokeWindow('focus'),
    minimize: () => invokeWindow('minimize'),
    toggleMaximize: () => invokeWindow('toggleMaximize'),
    isMaximized: () => invokeWindow<boolean>('isMaximized'),
    isVisible: () => invokeWindow<boolean>('isVisible'),
    isFocused: () => invokeWindow<boolean>('isFocused'),
    setFullScreen: (fullScreen) => invokeWindow('setFullScreen', fullScreen),
    isFullScreen: () => invokeWindow<boolean>('isFullScreen'),
    close: () => invokeWindow('close'),
    minimizeToTray: () => invokeWindow('minimizeToTray'),
    async setLayout(layout) {
      if (layout.kind === 'auth') {
        await invokeWindow('setWindowButtonVisibility', false);
        await invokeWindow('setMaximizable', false);
        await invokeWindow('setMinimumSize', layout.width, layout.height);
        await invokeWindow('setMaximumSize', layout.width, layout.height);
        await invokeWindow('setSize', layout.width, layout.height);
        await invokeWindow('setResizable', false);
        if (layout.center) await invokeWindow('center');
      } else {
        const width = layout.width ?? 1180;
        const height = layout.height ?? 760;
        await invokeWindow('setWindowButtonVisibility', true);
        await invokeWindow('setResizable', true);
        await invokeWindow('setMaximizable', true);
        await invokeWindow('setMaximumSize', 100000, 100000);
        await invokeWindow('setMinimumSize', layout.minWidth ?? 360, layout.minHeight ?? 600);
        if (layout.restore) {
          if (await invokeWindow<boolean>('isFullScreen')) await invokeWindow('setFullScreen', false);
          if (await invokeWindow<boolean>('isMaximized')) await invokeWindow('unmaximize');
          await invokeWindow('setSize', width, height);
          await invokeWindow('center');
        } else {
          // Initial logged-in layout: keep a size the user may have chosen,
          // only growing a window still at the login-card size.
          const size = await invokeWindow<{ width: number; height: number }>('getSize');
          if (size.width < 360 || size.height < 600) {
            await invokeWindow('setSize', width, height);
            await invokeWindow('center');
          }
        }
      }
    },
    setMinimumSize: (width, height) => invokeWindow('setMinimumSize', width, height),
    setResizable: (resizable) => invokeWindow('setResizable', resizable),
    requestAttention: () => invokeWindow('requestAttention'),
    setPreventSleep: (active) => invokeWindow('setPreventSleep', active),
    setContentProtection: (excluded) => invokeWindow<boolean>('setContentProtection', excluded),
    setTrayTooltip: (tooltip) => invokeWindow('setTrayTooltip', tooltip),
    onEvent(listener) {
      windowListeners.add(listener);
      return () => windowListeners.delete(listener);
    },
    respondToClose: (allow) => invokeWindow('respondToClose', allow),
    quit: () => invokeWindow('quit'),
    // window_manager-style extras used by DesktopWindowController.
    maximize: () => invokeWindow('maximize'),
    unmaximize: () => invokeWindow('unmaximize'),
    isMinimized: () => invokeWindow<boolean>('isMinimized'),
    restoreFromTray: () => invokeWindow('restoreFromTray'),
    getSize: () => invokeWindow<{ width: number; height: number }>('getSize'),
    setSize: (width: number, height: number) => invokeWindow('setSize', width, height),
    setMaximumSize: (width: number, height: number) => invokeWindow('setMaximumSize', width, height),
    setMaximizable: (maximizable: boolean) => invokeWindow('setMaximizable', maximizable),
    center: () => invokeWindow('center'),
    setOpacity: (opacity: number) => invokeWindow<void>('setOpacity', opacity),
    setSkipTaskbar: (skip: boolean) => invokeWindow<void>('setSkipTaskbar', skip),
    setHasShadow: (hasShadow: boolean) => invokeWindow('setHasShadow', hasShadow),
    setTitle: (title: string) => invokeWindow('setTitle', title),
    setWindowButtonVisibility: (visible: boolean) => invokeWindow('setWindowButtonVisibility', visible),
    setAlwaysOnTop: (onTop: boolean) => invokeWindow('setAlwaysOnTop', onTop),
  };

  const screenCapture: ScreenCaptureBridge = {
    async listSources(options) {
      const sources = await invokeScreen<ScreenSourceInfo[]>('listSources', options);
      return sources.map(
        (source): ScreenCaptureSource => ({
          id: source.id,
          name: source.name,
          isWindow: source.isWindow,
          thumbnailDataUrl: source.thumbnailDataUrl,
          appIconDataUrl: source.appIconDataUrl,
          displayId: source.displayId,
        }),
      );
    },
    selectSource: (sourceId, options) => invokeScreen('selectSource', sourceId, options),
    supportsSystemAudio: () => invokeScreen<boolean>('supportsSystemAudio'),
    permissionStatus: () => invokeScreen('permissionStatus'),
    openPermissionSettings: () => invokeScreen('openPermissionSettings'),
  };

  const process: ProcessBridge = {
    spawnDetached: async (executable, args) => {
      await bridge.invoke(IpcChannels.processInvoke, 'spawnDetached', executable, args);
    },
    run: (executable, args, options) =>
      bridge.invoke(IpcChannels.processInvoke, 'run', executable, args, options) as Promise<{
        exitCode: number;
        stdout: string;
        stderr: string;
      }>,
  };

  const speech: SpeechBridge = createBrowserSpeech();

  const invokeUpdater = <T>(op: string) => bridge.invoke(IpcChannels.updaterInvoke, op) as Promise<T>;
  const mediaAccess: MediaAccessBridge = {
    requestAccess: (kind) => bridge.invoke(IpcChannels.mediaAccessInvoke, 'requestAccess', kind) as Promise<boolean>,
  };

  const sparkle: SparkleBridge = {
    isAvailable: () => bridge.invoke(IpcChannels.sparkleInvoke, 'isAvailable') as Promise<boolean>,
    checkForUpdates: async ({ feedUrl, background }) => {
      await bridge.invoke(IpcChannels.sparkleInvoke, 'checkForUpdates', feedUrl, background);
    },
  };

  const updater: UpdaterBridge = {
    isAvailable: () => invokeUpdater<boolean>('isAvailable'),
    checkForUpdate: () => invokeUpdater<NativeUpdateInfo | null>('checkForUpdate'),
    async downloadUpdate({ onProgress, signal }) {
      if (signal?.aborted) throw abortError();
      const unsubscribe = bridge.on(IpcChannels.updaterProgress, (progress) => {
        onProgress?.(progress as { receivedBytes: number; totalBytes: number | null });
      });
      const onAbort = () => void invokeUpdater('cancelDownload');
      signal?.addEventListener('abort', onAbort, { once: true });
      try {
        await invokeUpdater<void>('downloadUpdate');
        if (signal?.aborted) throw abortError();
      } catch (error) {
        if (signal?.aborted) throw abortError();
        throw error;
      } finally {
        unsubscribe();
        signal?.removeEventListener('abort', onAbort);
      }
    },
    quitAndInstall: () => invokeUpdater<void>('quitAndInstall'),
  };

  return {
    os,
    isDesktop: true,
    isMobile: false,
    isWeb: false,
    app,
    http: new IpcHttpClient(bridge),
    secureStorage,
    preferences,
    files,
    clipboard,
    notifications,
    window: windowBridge,
    screenCapture,
    process,
    speech,
    updater,
    sparkle,
    mediaAccess,
    openExternal: (url) => bridge.invoke(IpcChannels.openExternal, url) as Promise<boolean>,
    cachedMediaUrl(url) {
      if (!/^https?:\/\//i.test(url)) return url;
      return `${boot.mediaScheme}://cache/?u=${encodeURIComponent(url)}`;
    },
  };
}

function abortError(): Error {
  const error = new Error('The update download was cancelled');
  error.name = 'AbortError';
  return error;
}

/** SharedPreferences equivalent: synchronous reads from the boot snapshot, write-through to main. */
class DesktopPreferences implements PreferencesStore {
  private readonly values: Map<string, PreferenceValue>;

  constructor(
    private readonly bridge: DesktopBridge,
    initial: Record<string, PreferenceValue>,
  ) {
    this.values = new Map(Object.entries(initial));
  }

  getString(key: string): string | null {
    const value = this.values.get(key);
    return typeof value === 'string' ? value : null;
  }
  getBool(key: string): boolean | null {
    const value = this.values.get(key);
    return typeof value === 'boolean' ? value : null;
  }
  getInt(key: string): number | null {
    const value = this.values.get(key);
    return typeof value === 'number' ? Math.trunc(value) : null;
  }
  getDouble(key: string): number | null {
    const value = this.values.get(key);
    return typeof value === 'number' ? value : null;
  }
  getStringList(key: string): string[] | null {
    const value = this.values.get(key);
    return Array.isArray(value) ? [...value] : null;
  }
  containsKey(key: string): boolean {
    return this.values.has(key);
  }
  getKeys(): string[] {
    return [...this.values.keys()];
  }
  private async set(key: string, value: PreferenceValue): Promise<void> {
    this.values.set(key, value);
    await this.bridge.invoke(IpcChannels.prefsSet, key, value);
  }
  setString(key: string, value: string) {
    return this.set(key, value);
  }
  setBool(key: string, value: boolean) {
    return this.set(key, value);
  }
  setInt(key: string, value: number) {
    return this.set(key, Math.trunc(value));
  }
  setDouble(key: string, value: number) {
    return this.set(key, value);
  }
  setStringList(key: string, value: string[]) {
    return this.set(key, [...value]);
  }
  async remove(key: string): Promise<void> {
    this.values.delete(key);
    await this.bridge.invoke(IpcChannels.prefsRemove, key);
  }
}
