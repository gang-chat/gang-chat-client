import {
  AppUpdatePlatform,
  AvailableAppUpdate,
  isNewerAppVersion,
  latestReleaseAssetForPlatform,
  parseReleaseAssetsFromS3List,
  releaseAssetUrl,
} from '../app/app_update';
import { HostPlatform } from '../config/host_platform';
import { Completer, withTimeout } from '../lib/async';
import { JsonFormatException } from '../lib/json';
import { HttpClientHelpers, responseBodyText } from '../platform/http';
import type { HttpClient } from '../platform/http';
import { platform } from '../platform/platform';
import type { FileSystemBridge } from '../platform/platform';

export type ReleaseDownloadProgress = (progress: { receivedBytes: number; totalBytes: number | null }) => void;

/** Port of `dart:io` `ProcessResult` (the fields the installer launcher reads). */
export interface ReleaseInstallerProcessResult {
  exitCode: number;
  stdout: string;
  stderr: string;
}

export type ReleaseInstallerProcessRunner = (
  executable: string,
  args: string[],
) => Promise<ReleaseInstallerProcessResult>;

/** Resolves the temporary directory path (`getTemporaryDirectory`). */
export type ReleaseTemporaryDirectoryProvider = () => Promise<string>;
export type ReleaseAndroidInstallerLauncher = (path: string) => Promise<void>;

export class ReleaseDownloadCancelledException extends Error {
  constructor() {
    super('版本下载已取消');
    this.name = 'ReleaseDownloadCancelledException';
  }

  override toString(): string {
    return '版本下载已取消';
  }
}

export class ReleaseInstallerException extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ReleaseInstallerException';
  }

  override toString(): string {
    return this.message;
  }
}

/** Port of `dart:io` `HttpException`. */
export class HttpException extends Error {
  constructor(
    message: string,
    readonly uri: string | null = null,
  ) {
    super(message);
    this.name = 'HttpException';
  }

  override toString(): string {
    return this.uri != null ? `HttpException: ${this.message}, uri = ${this.uri}` : `HttpException: ${this.message}`;
  }
}

/** Port of `dart:io` `ProcessException`. */
export class ProcessException extends Error {
  constructor(
    readonly executable: string,
    readonly args: string[],
    message = '',
    readonly errorCode = 0,
  ) {
    super(message);
    this.name = 'ProcessException';
  }

  override toString(): string {
    const command = `${this.executable} ${this.args.join(' ')}`;
    return `ProcessException: ${this.message}\n  Command: ${command}`;
  }
}

export class ReleaseDownloadCancellationToken {
  private _cancelled = false;
  private _active = false;
  private _abort: AbortController | null = null;
  private _partialFile: string | null = null;
  private _files: FileSystemBridge | null = null;
  private _completion: Completer<void> | null = null;

  get isCancelled(): boolean {
    return this._cancelled;
  }

  cancel(): void {
    this._cancelled = true;
    this._abort?.abort();
  }

  async cancelAndDeletePartialFile(): Promise<void> {
    this.cancel();
    const completion = this._completion;
    if (this._active && completion != null && !completion.isCompleted) {
      try {
        await withTimeout(completion.promise, 2000);
      } catch {
        // A still-open file can fail deletion on Windows; try anyway below.
      }
    }
    await _deleteFileQuietly(this._files, this._partialFile);
  }

  /** @internal Binds the in-flight download (Dart's library-private `_bind`). */
  _bind(abort: AbortController, partialFile: string, files: FileSystemBridge): void {
    this._abort = abort;
    this._partialFile = partialFile;
    this._files = files;
    this._active = true;
    this._completion = new Completer<void>();
    if (this._cancelled) abort.abort();
  }

  /** @internal */
  _throwIfCancelled(): void {
    if (this._cancelled) throw new ReleaseDownloadCancelledException();
  }

  /** @internal */
  _complete(): void {
    this._abort = null;
    this._active = false;
    const completion = this._completion;
    if (completion != null && !completion.isCompleted) {
      completion.complete();
    }
  }
}

/** Downloaded bytes are flushed to disk in blocks of this size. */
const _writeBufferBytes = 1 << 20;

/**
 * Release discovery (S3 bucket listing), installer download and launch.
 *
 * PORT: `dart:io` `File`s become absolute path strings handled through
 * `platform().files`; `http.Client` is the injectable [HttpClient] (default
 * `platform().http`, which is shared and therefore never closed here), and
 * cancellation aborts the request through an `AbortSignal` instead of closing
 * the client. Process launches go through `platform().process`. The default
 * Android installer launcher (the `gang_chat/app_update` channel) is gone
 * because Android stays on Flutter; inject one to use the Android path.
 */
export class ReleaseUpdateService {
  constructor(
    opts: {
      httpClient?: HttpClient;
      processRunner?: ReleaseInstallerProcessRunner;
      temporaryDirectoryProvider?: ReleaseTemporaryDirectoryProvider;
      androidInstallerLauncher?: ReleaseAndroidInstallerLauncher;
      files?: FileSystemBridge;
    } = {},
  ) {
    this._httpClient = opts.httpClient ?? null;
    this._processRunner = opts.processRunner ?? null;
    this._temporaryDirectoryProvider = opts.temporaryDirectoryProvider ?? null;
    this._androidInstallerLauncher = opts.androidInstallerLauncher ?? null;
    this._filesOverride = opts.files ?? null;
  }

  private readonly _httpClient: HttpClient | null;
  private readonly _processRunner: ReleaseInstallerProcessRunner | null;
  private readonly _temporaryDirectoryProvider: ReleaseTemporaryDirectoryProvider | null;
  private readonly _androidInstallerLauncher: ReleaseAndroidInstallerLauncher | null;
  private readonly _filesOverride: FileSystemBridge | null;

  private get _client(): HttpClient {
    return this._httpClient ?? platform().http;
  }

  private get _files(): FileSystemBridge {
    return this._filesOverride ?? platform().files;
  }

  async checkForUpdate(opts: {
    bucketUrl: string;
    currentVersion: string;
    platform: AppUpdatePlatform;
  }): Promise<AvailableAppUpdate | null> {
    const { bucketUrl, currentVersion } = opts;
    const listUri = this._listUri(bucketUrl);
    const response = await new HttpClientHelpers(this._client).get(listUri);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw new HttpException(`读取版本列表失败（状态码 ${response.statusCode}）`, listUri);
    }

    const assets = parseReleaseAssetsFromS3List(responseBodyText(response));
    const asset = latestReleaseAssetForPlatform(assets, opts.platform);
    if (asset == null || !isNewerAppVersion({ currentVersion, latestVersion: asset.version })) {
      return null;
    }

    return new AvailableAppUpdate({
      currentVersion,
      latestVersion: asset.version,
      asset,
      downloadUrl: releaseAssetUrl(bucketUrl, asset.key),
    });
  }

  /** Downloads the installer and resolves with its absolute path. */
  async downloadUpdate(
    update: AvailableAppUpdate,
    opts: { onProgress?: ReleaseDownloadProgress; cancellationToken?: ReleaseDownloadCancellationToken } = {},
  ): Promise<string> {
    const { onProgress, cancellationToken } = opts;
    const files = this._files;
    const abort = new AbortController();
    let file: string | null = null;
    try {
      file = await this._downloadTarget(update);
      cancellationToken?._bind(abort, file, files);
      cancellationToken?._throwIfCancelled();
      const response = await this._client.send({ method: 'GET', url: update.downloadUrl, signal: abort.signal });
      cancellationToken?._throwIfCancelled();
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw new HttpException(`版本下载失败（状态码 ${response.statusCode}）`, update.downloadUrl);
      }

      await files.writeFile(file, new Uint8Array(0));
      let received = 0;
      const contentLength = response.contentLength;
      const total = contentLength != null && contentLength >= 0 ? contentLength : null;
      onProgress?.({ receivedBytes: received, totalBytes: total });
      const buffer = new _ChunkBuffer();
      for await (const chunk of response.stream) {
        cancellationToken?._throwIfCancelled();
        received += chunk.length;
        buffer.add(chunk);
        if (buffer.length >= _writeBufferBytes) await files.appendFile(file, buffer.take());
        onProgress?.({ receivedBytes: received, totalBytes: total });
      }
      cancellationToken?._throwIfCancelled();
      if (buffer.length > 0) await files.appendFile(file, buffer.take());

      if (update.asset.platform === AppUpdatePlatform.android) {
        await _validateAndroidApkDownload(files, file, { receivedBytes: received, expectedBytes: total });
      }
      return file;
    } catch (error) {
      await _deleteFileQuietly(files, file);
      if (error instanceof ReleaseDownloadCancelledException || cancellationToken?.isCancelled === true) {
        throw new ReleaseDownloadCancelledException();
      }
      throw error;
    } finally {
      cancellationToken?._complete();
    }
  }

  async startInstaller(file: string, opts: { platform?: AppUpdatePlatform | null } = {}): Promise<void> {
    const resolvedPlatform = opts.platform ?? appUpdatePlatformForOperatingSystem(HostPlatform.operatingSystem);
    switch (resolvedPlatform) {
      case AppUpdatePlatform.windows:
        await this._startWindowsInstaller(file);
        return;
      case AppUpdatePlatform.macos: {
        const process = platform().process;
        if (process == null) throw new Error('Installing updates is not supported on this platform.');
        await process.spawnDetached('open', [file]);
        return;
      }
      case AppUpdatePlatform.android:
        await this._startAndroidInstaller(file);
        return;
      case null:
        throw new Error('Installing updates is not supported on this platform.');
    }
  }

  private async _startWindowsInstaller(file: string): Promise<void> {
    const args = _windowsElevatedInstallerArguments(file);
    const runner = this._processRunner ?? _runProcess;
    const result = await runner('powershell.exe', args);
    if (result.exitCode === 0) return;

    throw new ProcessException('powershell.exe', args, _processResultMessage(result), result.exitCode);
  }

  private async _startAndroidInstaller(file: string): Promise<void> {
    if (_pathExtension(file).toLowerCase() !== 'apk') {
      throw new ReleaseInstallerException('下载的文件不是 Android 安装包');
    }
    const launcher = this._androidInstallerLauncher ?? _invokeAndroidInstaller;
    try {
      await launcher(file);
    } catch (error) {
      const code = _platformExceptionCode(error);
      if (code == null) throw error;
      throw new ReleaseInstallerException(_androidInstallerErrorMessage(code));
    }
  }

  private _listUri(bucketUrl: string): string {
    const url = new URL(bucketUrl);
    url.searchParams.set('list-type', '2');
    url.searchParams.set('prefix', 'releases/');
    return url.toString();
  }

  private async _downloadTarget(update: AvailableAppUpdate): Promise<string> {
    const files = this._files;
    const directoryProvider = this._temporaryDirectoryProvider ?? (async () => (await files.directories()).temporary);
    const temporaryDirectory = await directoryProvider();
    const directory =
      update.asset.platform === AppUpdatePlatform.android
        ? files.joinPath(temporaryDirectory, 'release-updates')
        : temporaryDirectory;
    await files.ensureDirectory(directory);
    await this._cleanupDownloadedInstallers(directory, { platform: update.asset.platform });
    const extension = _installerExtension(update.asset.platform);
    return files.joinPath(directory, `GangChat_v${update.latestVersion}.${extension}`);
  }

  private async _cleanupDownloadedInstallers(directory: string, opts: { platform: AppUpdatePlatform }): Promise<void> {
    const files = this._files;
    if (!(await files.exists(directory))) return;

    const installerPattern =
      opts.platform === AppUpdatePlatform.android
        ? _downloadedAndroidInstallerPattern
        : _downloadedDesktopInstallerPattern;
    for (const entry of await files.listDirectory(directory)) {
      if (entry.isDirectory) continue;
      if (!installerPattern.test(_pathBasename(entry.path))) {
        continue;
      }
      try {
        await files.deleteFile(entry.path);
      } catch {
        // A currently running installer may be locked by Windows; skip it and
        // let the new download surface any conflict with its exact target file.
      }
    }
  }
}

class _ChunkBuffer {
  private _chunks: Uint8Array[] = [];
  length = 0;

  add(chunk: Uint8Array): void {
    this._chunks.push(chunk);
    this.length += chunk.length;
  }

  take(): Uint8Array {
    const out = new Uint8Array(this.length);
    let offset = 0;
    for (const chunk of this._chunks) {
      out.set(chunk, offset);
      offset += chunk.length;
    }
    this._chunks = [];
    this.length = 0;
    return out;
  }
}

function _installerExtension(platform: AppUpdatePlatform): string {
  switch (platform) {
    case AppUpdatePlatform.windows:
      return 'exe';
    case AppUpdatePlatform.macos:
      return 'dmg';
    case AppUpdatePlatform.android:
      return 'apk';
  }
}

async function _deleteFileQuietly(files: FileSystemBridge | null, file: string | null): Promise<void> {
  if (files == null || file == null) return;
  try {
    if (await files.exists(file)) await files.deleteFile(file);
  } catch {
    // ignore
  }
}

const _downloadedDesktopInstallerPattern = /^GangChat_v\d+\.\d+\.\d+\.(exe|dmg)$/;
const _downloadedAndroidInstallerPattern = /^GangChat_v\d+\.\d+\.\d+\.apk$/;

export function appUpdatePlatformForOperatingSystem(operatingSystem: string): AppUpdatePlatform | null {
  switch (operatingSystem.trim().toLowerCase()) {
    case 'windows':
      return AppUpdatePlatform.windows;
    case 'macos':
      return AppUpdatePlatform.macos;
    case 'android':
      return AppUpdatePlatform.android;
    default:
      return null;
  }
}

async function _validateAndroidApkDownload(
  files: FileSystemBridge,
  file: string,
  opts: { receivedBytes: number; expectedBytes: number | null },
): Promise<void> {
  if (opts.expectedBytes != null && opts.receivedBytes !== opts.expectedBytes) {
    throw new JsonFormatException('Android 安装包下载不完整');
  }
  const header = (await files.readFile(file)).subarray(0, 4);
  if (header.length !== 4 || header[0] !== 0x50 || header[1] !== 0x4b || header[2] !== 0x03 || header[3] !== 0x04) {
    throw new JsonFormatException('下载的文件不是有效的 Android 安装包');
  }
}

function _pathBasename(path: string): string {
  const slash = path.lastIndexOf('/');
  const backslash = path.lastIndexOf('\\');
  const index = slash > backslash ? slash : backslash;
  return index < 0 ? path : path.slice(index + 1);
}

function _pathExtension(path: string): string {
  const basename = _pathBasename(path);
  const index = basename.lastIndexOf('.');
  return index < 0 ? '' : basename.slice(index + 1);
}

function _runProcess(executable: string, args: string[]): Promise<ReleaseInstallerProcessResult> {
  const process = platform().process;
  if (process == null) {
    return Promise.reject(new ProcessException(executable, args, 'Processes are not supported on this platform.'));
  }
  return process.run(executable, args);
}

function _windowsElevatedInstallerArguments(installerPath: string): string[] {
  return [
    '-NoProfile',
    '-NonInteractive',
    '-ExecutionPolicy',
    'Bypass',
    '-Command',
    `Start-Process -FilePath ${_powershellSingleQuoted(installerPath)} -Verb RunAs`,
  ];
}

function _powershellSingleQuoted(value: string): string {
  return `'${value.replace(/'/g, "''")}'`;
}

function _processResultMessage(result: ReleaseInstallerProcessResult): string {
  const stderr = result.stderr?.toString().trim() ?? '';
  if (stderr.length > 0) return stderr;

  const stdout = result.stdout?.toString().trim() ?? '';
  if (stdout.length > 0) return stdout;

  return '安装程序启动失败';
}

async function _invokeAndroidInstaller(_path: string): Promise<void> {
  // PORT: `gang_chat/app_update` `installApk` lives in the Flutter Android app.
  throw new ReleaseInstallerException(_androidInstallerErrorMessage('installer_unavailable'));
}

/** Duck-typed `PlatformException.code` (an error carrying a string `code`). */
function _platformExceptionCode(error: unknown): string | null {
  if (typeof error !== 'object' || error === null || !('code' in error)) return null;
  const code = (error as { code: unknown }).code;
  return typeof code === 'string' ? code : null;
}

function _androidInstallerErrorMessage(code: string): string {
  switch (code) {
    case 'permission_denied':
      return '请允许 Gang Chat 安装未知应用后重试';
    case 'invalid_apk':
      return '下载的 Android 安装包无效，请重新下载';
    case 'invalid_package':
      return '下载的安装包不是 Gang Chat';
    case 'signature_mismatch':
      return '安装包签名与当前应用不一致，无法直接更新';
    case 'installer_unavailable':
      return '系统中没有可用的 Android 安装器';
    case 'install_in_progress':
      return '已有更新安装流程正在进行';
    case 'notification_permission_required':
      return '请开启通知权限，以便后台下载完成后提示安装';
    default:
      return '无法启动 Android 系统安装器';
  }
}
