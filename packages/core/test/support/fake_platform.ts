/**
 * In-memory [Platform] for unit tests (the stand-in for Flutter's mock method
 * channels, `SharedPreferences.setMockInitialValues`, temp directories, ...).
 *
 *   const fake = installFakePlatform({ os: 'windows', preferences: { 'gang.language': 'en' } });
 *   fake.window.calls        // recorded WindowBridge calls
 *   fake.files.files         // in-memory file system
 *
 * Every bridge records the calls it receives. Pass `window: null` (etc.) to
 * model a host without that capability.
 */
import { setPlatform } from '../../src/platform/platform';
import type {
  AppInfo,
  ClipboardBridge,
  ClipboardImage,
  FileSystemBridge,
  HostOs,
  MediaAccessBridge,
  NotificationBridge,
  Platform,
  PlatformFile,
  PlatformFileTypeGroup,
  PreferencesStore,
  ProcessBridge,
  ScreenCaptureBridge,
  SecureStorage,
  SpeechBridge,
  SystemNotificationOptions,
  UpdaterBridge,
  WindowBridge,
  WindowEvent,
} from '../../src/platform/platform';
import type { HttpClient } from '../../src/platform/http';
import { MockClient } from './fake_http_client';

export interface RecordedCall {
  method: string;
  args: unknown[];
}

// ---------------------------------------------------------------------------
// Preferences / secure storage

export type FakePreferenceValue = string | number | boolean | string[];

export class FakePreferencesStore implements PreferencesStore {
  readonly values = new Map<string, FakePreferenceValue>();

  constructor(initial: Record<string, FakePreferenceValue> = {}) {
    for (const [key, value] of Object.entries(initial)) this.values.set(key, value);
  }

  /** `SharedPreferences.setMockInitialValues`. */
  reset(initial: Record<string, FakePreferenceValue> = {}): void {
    this.values.clear();
    for (const [key, value] of Object.entries(initial)) this.values.set(key, value);
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
    return typeof value === 'number' && Number.isInteger(value) ? value : null;
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

  async setString(key: string, value: string): Promise<void> {
    this.values.set(key, value);
  }

  async setBool(key: string, value: boolean): Promise<void> {
    this.values.set(key, value);
  }

  async setInt(key: string, value: number): Promise<void> {
    this.values.set(key, Math.trunc(value));
  }

  async setDouble(key: string, value: number): Promise<void> {
    this.values.set(key, value);
  }

  async setStringList(key: string, value: string[]): Promise<void> {
    this.values.set(key, [...value]);
  }

  async remove(key: string): Promise<void> {
    this.values.delete(key);
  }
}

export class FakeSecureStorage implements SecureStorage {
  readonly values = new Map<string, string>();

  async read(key: string): Promise<string | null> {
    return this.values.get(key) ?? null;
  }

  async write(key: string, value: string): Promise<void> {
    this.values.set(key, value);
  }

  async delete(key: string): Promise<void> {
    this.values.delete(key);
  }
}

// ---------------------------------------------------------------------------
// Files

export interface FakeFileEntry {
  bytes: Uint8Array;
  modifiedMs: number;
}

/** POSIX-style in-memory file system (`/` separators). */
export class FakeFileSystem implements FileSystemBridge {
  readonly files = new Map<string, FakeFileEntry>();
  readonly directoriesCreated = new Set<string>(['/']);
  readonly calls: RecordedCall[] = [];
  /** Paths whose deletion throws (a "locked" file). */
  readonly lockedPaths = new Set<string>();
  dirs = {
    temporary: '/tmp',
    applicationSupport: '/support',
    applicationCache: '/cache',
    downloads: '/downloads' as string | null,
    roamingAppData: null as string | null,
  };
  /** Clock for modification times; tests may advance it. */
  now = 1_000;

  /** Queued answers for the pickers. */
  nextOpenFile: PlatformFile | null = null;
  nextOpenFiles: PlatformFile[] = [];
  nextSaveLocation: string | null = null;
  downloads: Array<{ bytes: Uint8Array; filename: string; mimeType?: string }> = [];

  private _record(method: string, ...args: unknown[]): void {
    this.calls.push({ method, args });
  }

  /** Seeds a file (creating its parent directories). */
  put(path: string, content: string | Uint8Array | readonly number[], modifiedMs?: number): void {
    const bytes =
      typeof content === 'string'
        ? new TextEncoder().encode(content)
        : content instanceof Uint8Array
          ? Uint8Array.from(content)
          : Uint8Array.from(content);
    this._ensureParents(path);
    this.files.set(path, { bytes, modifiedMs: modifiedMs ?? this._tick() });
  }

  text(path: string): string | null {
    const entry = this.files.get(path);
    return entry == null ? null : new TextDecoder().decode(entry.bytes);
  }

  private _tick(): number {
    this.now += 1;
    return this.now;
  }

  private _ensureParents(path: string): void {
    const parts = path.split('/').filter((part) => part.length > 0);
    let current = '';
    for (let index = 0; index < parts.length - 1; index++) {
      current += `/${parts[index]}`;
      this.directoriesCreated.add(current);
    }
  }

  private _platformFile(path: string): PlatformFile {
    const entry = this.files.get(path);
    return {
      name: path.slice(path.lastIndexOf('/') + 1),
      path,
      mimeType: null,
      size: entry?.bytes.length ?? 0,
      readAsBytes: async () => Uint8Array.from(this.files.get(path)?.bytes ?? new Uint8Array(0)),
    };
  }

  async openFile(options: { acceptedTypeGroups?: PlatformFileTypeGroup[]; confirmButtonText?: string }): Promise<PlatformFile | null> {
    this._record('openFile', options);
    return this.nextOpenFile;
  }

  async openFiles(options: { acceptedTypeGroups?: PlatformFileTypeGroup[]; confirmButtonText?: string }): Promise<PlatformFile[]> {
    this._record('openFiles', options);
    return this.nextOpenFiles;
  }

  async getSaveLocation(options: {
    suggestedName: string;
    acceptedTypeGroups?: PlatformFileTypeGroup[];
    confirmButtonText?: string;
  }): Promise<string | null> {
    this._record('getSaveLocation', options);
    return this.nextSaveLocation;
  }

  async filesFromPaths(paths: string[]): Promise<PlatformFile[]> {
    return paths.map((path) => this._platformFile(path));
  }

  async writeFile(path: string, bytes: Uint8Array): Promise<void> {
    this._record('writeFile', path, bytes.length);
    this._ensureParents(path);
    this.files.set(path, { bytes: Uint8Array.from(bytes), modifiedMs: this._tick() });
  }

  async appendFile(path: string, bytes: Uint8Array): Promise<void> {
    this._record('appendFile', path, bytes.length);
    const existing = this.files.get(path)?.bytes ?? new Uint8Array(0);
    const next = new Uint8Array(existing.length + bytes.length);
    next.set(existing);
    next.set(bytes, existing.length);
    this._ensureParents(path);
    this.files.set(path, { bytes: next, modifiedMs: this._tick() });
  }

  async readFile(path: string): Promise<Uint8Array> {
    const entry = this.files.get(path);
    if (entry == null) throw new Error(`ENOENT: ${path}`);
    return Uint8Array.from(entry.bytes);
  }

  async *openRead(path: string): AsyncIterable<Uint8Array> {
    yield await this.readFile(path);
  }

  async deleteFile(path: string): Promise<void> {
    this._record('deleteFile', path);
    if (this.lockedPaths.has(path)) throw new Error(`EBUSY: ${path}`);
    if (!this.files.delete(path)) throw new Error(`ENOENT: ${path}`);
  }

  async rename(from: string, to: string): Promise<void> {
    this._record('rename', from, to);
    const entry = this.files.get(from);
    if (entry == null) throw new Error(`ENOENT: ${from}`);
    this.files.delete(from);
    this.files.set(to, entry);
  }

  async exists(path: string): Promise<boolean> {
    return this.files.has(path) || this.directoriesCreated.has(path);
  }

  async fileSize(path: string): Promise<number | null> {
    return this.files.get(path)?.bytes.length ?? null;
  }

  async listDirectory(
    path: string,
  ): Promise<Array<{ name: string; path: string; isDirectory: boolean; size: number; modifiedMs: number }>> {
    const prefix = path.endsWith('/') ? path : `${path}/`;
    const result: Array<{ name: string; path: string; isDirectory: boolean; size: number; modifiedMs: number }> = [];
    for (const [filePath, entry] of this.files) {
      if (!filePath.startsWith(prefix)) continue;
      const name = filePath.slice(prefix.length);
      if (name.includes('/')) continue;
      result.push({ name, path: filePath, isDirectory: false, size: entry.bytes.length, modifiedMs: entry.modifiedMs });
    }
    for (const dir of this.directoriesCreated) {
      if (!dir.startsWith(prefix)) continue;
      const name = dir.slice(prefix.length);
      if (name.length === 0 || name.includes('/')) continue;
      result.push({ name, path: dir, isDirectory: true, size: 0, modifiedMs: 0 });
    }
    return result;
  }

  async ensureDirectory(path: string): Promise<void> {
    this._ensureParents(`${path}/x`);
    this.directoriesCreated.add(path);
  }

  async directories(): Promise<{
    temporary: string;
    applicationSupport: string;
    applicationCache: string;
    downloads: string | null;
    roamingAppData?: string | null;
  }> {
    return { ...this.dirs };
  }

  async setLastModified(path: string, modifiedMs: number): Promise<void> {
    const entry = this.files.get(path);
    if (entry == null) throw new Error(`ENOENT: ${path}`);
    entry.modifiedMs = modifiedMs;
  }

  joinPath(...segments: string[]): string {
    return segments
      .map((segment, index) => (index === 0 ? segment.replace(/\/+$/, '') : segment.replace(/^\/+|\/+$/g, '')))
      .filter((segment, index) => index === 0 || segment.length > 0)
      .join('/');
  }

  async openPath(path: string): Promise<boolean> {
    this._record('openPath', path);
    return true;
  }

  async showInFolder(path: string): Promise<void> {
    this._record('showInFolder', path);
  }

  async saveAsDownload(options: { bytes: Uint8Array; filename: string; mimeType?: string }): Promise<void> {
    this.downloads.push(options);
  }
}

// ---------------------------------------------------------------------------
// Clipboard / window / process / speech / notifications / updater

export class FakeClipboard implements ClipboardBridge {
  text: string | null = null;
  filePaths: string[] = [];
  image: ClipboardImage | null = null;
  writeResult = true;
  readonly calls: RecordedCall[] = [];

  async readText(): Promise<string | null> {
    return this.text;
  }

  async writeText(text: string): Promise<void> {
    this.calls.push({ method: 'writeText', args: [text] });
    this.text = text;
  }

  async readFilePaths(): Promise<string[]> {
    return [...this.filePaths];
  }

  async writeFilePaths(paths: string[]): Promise<boolean> {
    this.calls.push({ method: 'writeFilePaths', args: [paths] });
    if (this.writeResult) this.filePaths = [...paths];
    return this.writeResult;
  }

  async readImage(): Promise<ClipboardImage | null> {
    return this.image;
  }

  async writeImage(bytes: Uint8Array, mimeType?: string): Promise<boolean> {
    this.calls.push({ method: 'writeImage', args: [bytes, mimeType] });
    return this.writeResult;
  }
}

/**
 * Records every [WindowBridge] call. Set [failures] to make a method throw
 * (like a `PlatformException` from the native channel) and [results] to
 * change a method's answer.
 */
export class FakeWindow implements WindowBridge {
  readonly calls: RecordedCall[] = [];
  readonly failures = new Set<string>();
  readonly results = new Map<string, unknown>();
  private readonly _listeners = new Set<(event: WindowEvent) => void>();

  get methods(): string[] {
    return this.calls.map((call) => call.method);
  }

  /** Delivers a native window event to the subscribed listeners. */
  emit(event: WindowEvent): void {
    for (const listener of [...this._listeners]) listener(event);
  }

  get listenerCount(): number {
    return this._listeners.size;
  }

  private async _call<T>(method: string, args: unknown[], fallback: T): Promise<T> {
    this.calls.push({ method, args });
    if (this.failures.has(method)) throw Object.assign(new Error(`${method} unavailable`), { code: 'unavailable' });
    return this.results.has(method) ? (this.results.get(method) as T) : fallback;
  }

  show(): Promise<void> {
    return this._call('show', [], undefined);
  }
  hide(): Promise<void> {
    return this._call('hide', [], undefined);
  }
  focus(): Promise<void> {
    return this._call('focus', [], undefined);
  }
  minimize(): Promise<void> {
    return this._call('minimize', [], undefined);
  }
  toggleMaximize(): Promise<void> {
    return this._call('toggleMaximize', [], undefined);
  }
  isMaximized(): Promise<boolean> {
    return this._call('isMaximized', [], false);
  }
  isVisible(): Promise<boolean> {
    return this._call('isVisible', [], true);
  }
  isFocused(): Promise<boolean> {
    return this._call('isFocused', [], true);
  }
  setFullScreen(fullScreen: boolean): Promise<void> {
    return this._call('setFullScreen', [fullScreen], undefined);
  }
  isFullScreen(): Promise<boolean> {
    return this._call('isFullScreen', [], false);
  }
  close(): Promise<void> {
    return this._call('close', [], undefined);
  }
  minimizeToTray(): Promise<void> {
    return this._call('minimizeToTray', [], undefined);
  }
  setLayout(layout: Parameters<WindowBridge['setLayout']>[0]): Promise<void> {
    return this._call('setLayout', [layout], undefined);
  }
  setMinimumSize(width: number, height: number): Promise<void> {
    return this._call('setMinimumSize', [width, height], undefined);
  }
  setOpacity(opacity: number): Promise<void> {
    return this._call('setOpacity', [opacity], undefined);
  }
  setSkipTaskbar(skip: boolean): Promise<void> {
    return this._call('setSkipTaskbar', [skip], undefined);
  }
  setResizable(resizable: boolean): Promise<void> {
    return this._call('setResizable', [resizable], undefined);
  }
  requestAttention(): Promise<void> {
    return this._call('requestAttention', [], undefined);
  }
  setPreventSleep(active: boolean): Promise<void> {
    return this._call('setPreventSleep', [active], undefined);
  }
  setContentProtection(excluded: boolean): Promise<boolean> {
    return this._call('setContentProtection', [excluded], true);
  }
  setTrayTooltip(tooltip: string): Promise<void> {
    return this._call('setTrayTooltip', [tooltip], undefined);
  }
  onEvent(listener: (event: WindowEvent) => void): () => void {
    this._listeners.add(listener);
    return () => this._listeners.delete(listener);
  }
  respondToClose(allow: boolean): Promise<void> {
    return this._call('respondToClose', [allow], undefined);
  }
  quit(): Promise<void> {
    return this._call('quit', [], undefined);
  }
}

export interface FakeProcessResult {
  exitCode: number;
  stdout: string;
  stderr: string;
}

export class FakeProcess implements ProcessBridge {
  readonly runs: Array<{ executable: string; args: string[] }> = [];
  readonly spawned: Array<{ executable: string; args: string[] }> = [];
  handler: (executable: string, args: string[]) => FakeProcessResult | Promise<FakeProcessResult> = () => ({
    exitCode: 0,
    stdout: '',
    stderr: '',
  });

  async spawnDetached(executable: string, args: string[]): Promise<void> {
    this.spawned.push({ executable, args });
  }

  async run(executable: string, args: string[]): Promise<FakeProcessResult> {
    this.runs.push({ executable, args });
    return this.handler(executable, args);
  }
}

export class FakeSpeech implements SpeechBridge {
  readonly spoken: Array<{ text: string; volume: number; language?: string }> = [];
  cancelled = 0;
  /** Throw from [speak] when set. */
  failWith: Error | null = null;

  async speak(text: string, options: { volume: number; language?: string }): Promise<void> {
    if (this.failWith) throw this.failWith;
    this.spoken.push({ text, ...options });
  }

  async cancel(): Promise<void> {
    this.cancelled += 1;
  }
}

export class FakeNotifications implements NotificationBridge {
  readonly shown: SystemNotificationOptions[] = [];
  readonly badges: number[] = [];
  supported = true;

  isSupported(): boolean {
    return this.supported;
  }

  async show(options: SystemNotificationOptions): Promise<void> {
    this.shown.push(options);
  }

  async setBadgeCount(count: number): Promise<void> {
    this.badges.push(count);
  }
}

export class FakeUpdater implements UpdaterBridge {
  available = true;
  failAvailability: Error | null = null;
  readonly checks: Array<{ feedUrl: string; background: boolean }> = [];

  async isAvailable(): Promise<boolean> {
    if (this.failAvailability) throw this.failAvailability;
    return this.available;
  }

  async checkForUpdates(options: { feedUrl: string; background: boolean }): Promise<void> {
    this.checks.push(options);
  }
}

// ---------------------------------------------------------------------------
// The platform

export interface FakePlatformOptions {
  os?: HostOs;
  app?: Partial<AppInfo>;
  http?: HttpClient;
  preferences?: Record<string, FakePreferenceValue>;
  /** `null` = host without the capability. */
  window?: FakeWindow | null;
  process?: FakeProcess | null;
  speech?: FakeSpeech | null;
  updater?: FakeUpdater | null;
  screenCapture?: ScreenCaptureBridge | null;
  mediaAccess?: MediaAccessBridge | null;
  /** Answer of [Platform.openExternal]. */
  openExternalResult?: boolean;
}

export interface FakePlatform extends Platform {
  readonly preferences: FakePreferencesStore;
  readonly secureStorage: FakeSecureStorage;
  readonly files: FakeFileSystem;
  readonly clipboard: FakeClipboard;
  readonly notifications: FakeNotifications;
  readonly window: FakeWindow | null;
  readonly process: FakeProcess | null;
  readonly speech: FakeSpeech | null;
  readonly updater: FakeUpdater | null;
  /** URLs passed to [openExternal]. */
  readonly openedUrls: string[];
  openExternalResult: boolean;
}

export function createFakePlatform(options: FakePlatformOptions = {}): FakePlatform {
  const os = options.os ?? 'linux';
  const isDesktop = os === 'windows' || os === 'macos' || os === 'linux';
  const openedUrls: string[] = [];
  const fake: FakePlatform = {
    os,
    isDesktop,
    isMobile: os === 'android' || os === 'ios',
    isWeb: os === 'web',
    app: {
      name: 'Gang Chat',
      version: '1.0.0',
      buildNumber: '1',
      installedAt: null,
      isPackaged: false,
      ...options.app,
    },
    http: options.http ?? new MockClient(() => ({ statusCode: 404, body: '' })),
    secureStorage: new FakeSecureStorage(),
    preferences: new FakePreferencesStore(options.preferences),
    files: new FakeFileSystem(),
    clipboard: new FakeClipboard(),
    notifications: new FakeNotifications(),
    window: options.window !== undefined ? options.window : isDesktop ? new FakeWindow() : null,
    screenCapture: options.screenCapture ?? null,
    process: options.process !== undefined ? options.process : isDesktop ? new FakeProcess() : null,
    speech: options.speech !== undefined ? options.speech : new FakeSpeech(),
    updater: options.updater ?? null,
    mediaAccess: options.mediaAccess ?? null,
    openedUrls,
    openExternalResult: options.openExternalResult ?? true,
    async openExternal(url: string): Promise<boolean> {
      openedUrls.push(url);
      return fake.openExternalResult;
    },
    cachedMediaUrl(url: string): string {
      return url;
    },
  };
  return fake;
}

/** Creates a [FakePlatform] and installs it with `setPlatform`. */
export function installFakePlatform(options: FakePlatformOptions = {}): FakePlatform {
  const fake = createFakePlatform(options);
  setPlatform(fake);
  return fake;
}
