/**
 * The native capabilities the shared code needs from its host app.
 *
 * This replaces the Flutter plugins the Dart client called directly
 * (flutter_secure_storage, shared_preferences, file_selector, window_manager,
 * path_provider, Process.run, ...). `@gang-chat/core` and `@gang-chat/ui`
 * only ever reach native code through [platform()]; the Electron app installs
 * a bridge backed by its preload script, a React Native app would install one
 * backed by native modules, and tests install fakes.
 *
 * Optional members are `null` when the host has no such capability (for
 * example `window` on mobile, `screenCapture` on the web).
 */
import type { HttpClient } from './http';

export type HostOs = 'windows' | 'macos' | 'linux' | 'android' | 'ios' | 'web';

export interface SecureStorage {
  read(key: string): Promise<string | null>;
  write(key: string, value: string): Promise<void>;
  delete(key: string): Promise<void>;
}

/**
 * Port of `SharedPreferences`: non-sensitive key/value preferences. Reads are
 * synchronous after [load] resolves, matching how the Dart code used the
 * cached `SharedPreferences.getInstance()` result.
 */
export interface PreferencesStore {
  getString(key: string): string | null;
  getBool(key: string): boolean | null;
  getInt(key: string): number | null;
  getDouble(key: string): number | null;
  getStringList(key: string): string[] | null;
  containsKey(key: string): boolean;
  getKeys(): string[];
  setString(key: string, value: string): Promise<void>;
  setBool(key: string, value: boolean): Promise<void>;
  setInt(key: string, value: number): Promise<void>;
  setDouble(key: string, value: number): Promise<void>;
  setStringList(key: string, value: string[]): Promise<void>;
  remove(key: string): Promise<void>;
}

export interface PlatformFileTypeGroup {
  label?: string;
  extensions?: string[];
  mimeTypes?: string[];
}

/** A file the user picked, dropped or pasted. Bytes are read lazily. */
export interface PlatformFile {
  name: string;
  /** Absolute path when the host exposes one (desktop); null on the web. */
  path: string | null;
  mimeType: string | null;
  size: number;
  readAsBytes(): Promise<Uint8Array>;
}

export interface FileSystemBridge {
  openFile(options: { acceptedTypeGroups?: PlatformFileTypeGroup[]; confirmButtonText?: string }): Promise<PlatformFile | null>;
  openFiles(options: { acceptedTypeGroups?: PlatformFileTypeGroup[]; confirmButtonText?: string }): Promise<PlatformFile[]>;
  /** Asks for a save location; returns the chosen absolute path or null when cancelled. */
  getSaveLocation(options: {
    suggestedName: string;
    acceptedTypeGroups?: PlatformFileTypeGroup[];
    confirmButtonText?: string;
  }): Promise<string | null>;
  filesFromPaths(paths: string[]): Promise<PlatformFile[]>;
  writeFile(path: string, bytes: Uint8Array): Promise<void>;
  appendFile(path: string, bytes: Uint8Array): Promise<void>;
  readFile(path: string): Promise<Uint8Array>;
  /**
   * Streams a file in chunks (`File.openRead()`), for copies that report
   * progress and can pause/cancel without loading the whole file. Hosts
   * without it fall back to [readFile].
   */
  openRead?(path: string): AsyncIterable<Uint8Array>;
  deleteFile(path: string): Promise<void>;
  rename(from: string, to: string): Promise<void>;
  exists(path: string): Promise<boolean>;
  fileSize(path: string): Promise<number | null>;
  listDirectory(path: string): Promise<Array<{ name: string; path: string; isDirectory: boolean; size: number; modifiedMs: number }>>;
  ensureDirectory(path: string): Promise<void>;
  /** Well-known directories (path_provider equivalents). */
  directories(): Promise<{
    temporary: string;
    applicationSupport: string;
    applicationCache: string;
    downloads: string | null;
    /**
     * Windows `%APPDATA%` (roaming); null/absent elsewhere. Used by
     * `LocalPreferencesMigration` to find the Flutter-era preferences file.
     */
    roamingAppData?: string | null;
  }>;
  /** Sets a file's modification time (`File.setLastModified`); LRU caches use it. */
  setLastModified?(path: string, modifiedMs: number): Promise<void>;
  joinPath(...segments: string[]): string;
  /** Opens a file with the default application. */
  openPath(path: string): Promise<boolean>;
  /** Reveals a file in Finder / Explorer. */
  showInFolder(path: string): Promise<void>;
  /** Hands bytes to the host's download flow (web) — desktop writes to a path instead. */
  saveAsDownload?(options: { bytes: Uint8Array; filename: string; mimeType?: string }): Promise<void>;
}

export interface ClipboardImage {
  bytes: Uint8Array;
  mimeType: string;
  filename: string;
}

export interface ClipboardBridge {
  readText(): Promise<string | null>;
  writeText(text: string): Promise<void>;
  /** File paths copied in Finder / Explorer. */
  readFilePaths(): Promise<string[]>;
  writeFilePaths(paths: string[]): Promise<boolean>;
  readImage(): Promise<ClipboardImage | null>;
  /** Writes a PNG (other formats are converted by the host). */
  writeImage(bytes: Uint8Array, mimeType?: string): Promise<boolean>;
}

export type WindowEvent =
  | { type: 'close-requested' }
  | { type: 'tray-exit-requested' }
  /** The tray icon's "open" item / click: `DesktopWindowController.restoreHiddenAppWindow`. */
  | { type: 'tray-open-requested' }
  | { type: 'maximized-changed'; maximized: boolean }
  | { type: 'fullscreen-changed'; fullScreen: boolean }
  | { type: 'focus-changed'; focused: boolean }
  | { type: 'visibility-changed'; visible: boolean };

/** Desktop window management (window_manager + the native runner code). */
export interface WindowBridge {
  show(): Promise<void>;
  hide(): Promise<void>;
  focus(): Promise<void>;
  minimize(): Promise<void>;
  toggleMaximize(): Promise<void>;
  isMaximized(): Promise<boolean>;
  isVisible(): Promise<boolean>;
  isFocused(): Promise<boolean>;
  setFullScreen(fullScreen: boolean): Promise<void>;
  isFullScreen(): Promise<boolean>;
  /** Requests the window close (goes through the close-requested handler). */
  close(): Promise<void>;
  /** Hides to the tray (Windows) / hides the window (macOS). */
  minimizeToTray(): Promise<void>;
  /** Lays the window out for the login card or for the main app. */
  setLayout(
    layout:
      | { kind: 'auth'; width: number; height: number; center: boolean }
      | {
          kind: 'app';
          restore: boolean;
          /** App window size / minimum size from `DesktopWindowController` (1180×760, min 960×600). */
          width?: number;
          height?: number;
          minWidth?: number;
          minHeight?: number;
        },
  ): Promise<void>;
  setMinimumSize(width: number, height: number): Promise<void>;
  /**
   * Window opacity 0..1 (window_manager `setOpacity`). Used to hide the
   * window while it is resized between the auth card and the app layout.
   */
  setOpacity?(opacity: number): Promise<void>;
  /** Removes / restores the taskbar entry (window_manager `setSkipTaskbar`). */
  setSkipTaskbar?(skip: boolean): Promise<void>;
  setResizable(resizable: boolean): Promise<void>;
  /** Taskbar flash / dock bounce for a new message. */
  requestAttention(): Promise<void>;
  /** Keeps the screen awake while full-screen media plays. */
  setPreventSleep(active: boolean): Promise<void>;
  /** Excludes the window from screen capture (screen-share of own window). */
  setContentProtection(excluded: boolean): Promise<boolean>;
  setTrayTooltip?(tooltip: string): Promise<void>;
  /** Intercept the native close button / Cmd+Q: the host waits for [handled]. */
  onEvent(listener: (event: WindowEvent) => void): () => void;
  /** Answers a close-requested event; `allow=true` lets the window close. */
  respondToClose(allow: boolean): Promise<void>;
  /** Quits the application for real (after the app cleaned up). */
  quit(): Promise<void>;
}

export interface SystemNotificationOptions {
  title: string;
  body: string;
  silent?: boolean;
  tag?: string;
  /** Icon as an http(s)/file URL; the host may ignore it. */
  iconUrl?: string | null;
}

export interface NotificationBridge {
  isSupported(): boolean;
  show(options: SystemNotificationOptions, onClick?: () => void): Promise<void>;
  /** Unread badge (dock / taskbar overlay). */
  setBadgeCount?(count: number): Promise<void>;
}

export interface ScreenCaptureSource {
  id: string;
  name: string;
  isWindow: boolean;
  /** PNG data URL thumbnail. */
  thumbnailDataUrl: string | null;
  appIconDataUrl?: string | null;
  displayId?: string | null;
}

/** Desktop screen/window enumeration + selection for `getDisplayMedia`. */
export interface ScreenCaptureBridge {
  listSources(options: { thumbnailWidth: number; thumbnailHeight: number }): Promise<ScreenCaptureSource[]>;
  /**
   * Chooses the source the next `navigator.mediaDevices.getDisplayMedia`
   * call resolves to, and whether system audio (loopback) is attached.
   */
  selectSource(sourceId: string, options: { audio: boolean }): Promise<void>;
  /** Whether the host can capture system audio with a screen share. */
  supportsSystemAudio(): Promise<boolean>;
  /** macOS screen-recording permission; always 'granted' elsewhere. */
  permissionStatus(): Promise<'granted' | 'denied' | 'restricted' | 'not-determined' | 'unknown'>;
  openPermissionSettings?(): Promise<void>;
}

/**
 * macOS in-app updates through Sparkle, the Flutter runner's
 * `gang_chat/updater` channel. Sparkle shows its own windows and installs the
 * update itself.
 */
export interface SparkleBridge {
  /** Whether this build carries an update-signing key and Sparkle started. */
  isAvailable(): Promise<boolean>;
  /**
   * A background check shows Sparkle's window only when an update exists; a
   * user check always reports back, including "up to date". Rejects when
   * Sparkle is not configured.
   */
  checkForUpdates(opts: { feedUrl: string | null; background: boolean }): Promise<void>;
}

/** OS-level camera / microphone access (the macOS and Windows privacy switches). */
export interface MediaAccessBridge {
  /**
   * Whether capture of [kind] may start. Asks the user when they have never
   * decided (macOS shows the system prompt), and resolves false when access
   * is switched off.
   */
  requestAccess(kind: 'camera' | 'microphone'): Promise<boolean>;
}

export interface ProcessBridge {
  /** Launches an installer / helper process detached from the app. */
  spawnDetached(executable: string, args: string[]): Promise<void>;
  /** Runs a command and collects its output (PowerShell, `say`, ...). */
  run(executable: string, args: string[], options?: { timeoutMs?: number }): Promise<{ exitCode: number; stdout: string; stderr: string }>;
}

export interface SpeechBridge {
  /** Speaks [text] with the system voice; resolves when finished. */
  speak(text: string, options: { volume: number; language?: string }): Promise<void>;
  cancel(): Promise<void>;
}

/** What the native updater found in the release feed. */
export interface NativeUpdateInfo {
  version: string;
  /** ISO 8601 release time from the feed, when present. */
  releaseDate: string | null;
  releaseNotes: string | null;
}

/**
 * Native in-app updater. The Electron app implements it with electron-updater
 * (Electron's autoUpdater: NSIS differential updates on Windows, Squirrel.Mac
 * on signed macOS builds) reading the release bucket's `releases/` feed.
 * Hosts or builds that cannot self-update (development builds, unsigned macOS)
 * report unavailable, and `ReleaseUpdateService` falls back to the
 * bucket-listing download flow of the Flutter client.
 */
export interface UpdaterBridge {
  isAvailable(): Promise<boolean>;
  /** Checks the feed; null when the running version is current. */
  checkForUpdate(): Promise<NativeUpdateInfo | null>;
  /** Downloads the update found by the last [checkForUpdate]. Aborting rejects. */
  downloadUpdate(options: {
    onProgress?: (progress: { receivedBytes: number; totalBytes: number | null }) => void;
    signal?: AbortSignal;
  }): Promise<void>;
  /** Quits the app and installs the downloaded update (relaunching it afterwards). */
  quitAndInstall(): Promise<void>;
}

export interface AppInfo {
  name: string;
  version: string;
  buildNumber: string;
  /** Installer timestamp written by the Windows installer, when available. */
  installedAt: string | null;
  /** True when running a packaged build. */
  isPackaged: boolean;
}

export interface Platform {
  readonly os: HostOs;
  readonly isDesktop: boolean;
  readonly isMobile: boolean;
  readonly isWeb: boolean;
  readonly app: AppInfo;
  readonly http: HttpClient;
  readonly secureStorage: SecureStorage;
  readonly preferences: PreferencesStore;
  readonly files: FileSystemBridge;
  readonly clipboard: ClipboardBridge;
  readonly notifications: NotificationBridge;
  readonly window: WindowBridge | null;
  readonly screenCapture: ScreenCaptureBridge | null;
  readonly process: ProcessBridge | null;
  readonly speech: SpeechBridge | null;
  /** Native self-updater (electron-updater on Windows); absent/null elsewhere. */
  readonly updater?: UpdaterBridge | null;
  /** Sparkle on macOS; absent/null elsewhere. */
  readonly sparkle?: SparkleBridge | null;
  /** OS camera / microphone access; absent where the browser asks on its own. */
  readonly mediaAccess?: MediaAccessBridge | null;
  /** Opens a URL (http/https/mailto) with the system handler. */
  openExternal(url: string): Promise<boolean>;
  /**
   * Resolves an http(s) media URL to one the renderer can display from the
   * local disk cache (desktop: a `gang-media://` URL served by the main
   * process). Hosts without a cache return the URL unchanged.
   */
  cachedMediaUrl(url: string): string;
}

let current: Platform | null = null;

export function setPlatform(next: Platform): void {
  current = next;
}

export function platform(): Platform {
  if (current == null) {
    throw new Error('platform() called before setPlatform(); the host app must install its bridge first.');
  }
  return current;
}

export function hasPlatform(): boolean {
  return current != null;
}
