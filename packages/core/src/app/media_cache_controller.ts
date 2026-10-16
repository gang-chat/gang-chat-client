import { HostPlatform } from '../config/host_platform';
import type { HttpClient } from '../platform/http';
import { platform } from '../platform/platform';
import { fnv1a64Signed } from './fnv_hash';
import { type FileTransferState, _AbortableHttpClient, _ChunkBuffer } from './file_transfer_state';

const _mediaCacheVersion = 'v1';

/**
 * The web has no file system, so recently used media is kept in memory, up
 * to this many bytes.
 */
const _webMemoryCacheMaxBytes = 64 * 1024 * 1024;

/** Chunk size used when a host cannot stream a file (`File.openRead()`'s 64 KiB). */
const _readChunkBytes = 64 * 1024;

/** Bytes buffered before each append to a cache/destination file. */
const _writeChunkBytes = 1024 * 1024;

/** Resolves the absolute path of the directory that holds the media cache. */
export type MediaCacheDirectoryProvider = () => Promise<string>;
export type MediaCacheProgressHandler = (progress: { sentBytes: number; totalBytes: number }) => void;

export class MediaCacheCancelledException extends Error {
  constructor() {
    super('下载已取消');
    this.name = 'MediaCacheCancelledException';
  }

  override toString(): string {
    return '下载已取消';
  }
}

/**
 * A file on disk, the stand-in for the `dart:io` `File` the Dart controller
 * returned. I/O goes through `platform().files`.
 */
export class MediaCacheFile {
  constructor(readonly path: string) {}

  exists(): Promise<boolean> {
    return platform().files.exists(this.path);
  }

  async length(): Promise<number> {
    const size = await platform().files.fileSize(this.path);
    if (size == null) throw new Error(`文件不存在: ${this.path}`);
    return size;
  }

  readAsBytes(): Promise<Uint8Array> {
    return platform().files.readFile(this.path);
  }

  delete(): Promise<void> {
    return platform().files.deleteFile(this.path);
  }

  /** Copies this file to [path] and returns the copy. */
  async copy(path: string): Promise<MediaCacheFile> {
    const files = platform().files;
    await files.writeFile(path, new Uint8Array(0));
    for await (const chunk of _openRead(this.path)) {
      await files.appendFile(path, chunk);
    }
    return new MediaCacheFile(path);
  }

  async rename(newPath: string): Promise<MediaCacheFile> {
    await platform().files.rename(this.path, newPath);
    return new MediaCacheFile(newPath);
  }
}

export class MediaCacheRequest {
  private constructor(opts: { uri: string; cacheKey: string; cacheFilename: string; expectedBytes?: number | null }) {
    this.uri = opts.uri;
    this.cacheKey = opts.cacheKey;
    this.cacheFilename = opts.cacheFilename;
    this.expectedBytes = opts.expectedBytes ?? null;
  }

  readonly uri: string;
  readonly cacheKey: string;
  readonly cacheFilename: string;
  readonly expectedBytes: number | null;

  static tryFromUrl(opts: {
    url: string | null | undefined;
    filename?: string | null;
    mimeType?: string | null;
    expectedBytes?: number | null;
    namespace?: string;
  }): MediaCacheRequest | null {
    const { filename, mimeType, expectedBytes, namespace = 'asset' } = opts;
    const trimmed = opts.url?.trim();
    if (trimmed == null || trimmed.length === 0) return null;
    let uri: URL;
    try {
      uri = new URL(trimmed);
    } catch {
      return null;
    }
    if (!_isHttpUri(uri)) return null;

    const sourceName = _firstNonEmpty([filename, _basenameFromUriPath(uri.pathname), 'asset'])!;
    const extension = _extensionFor({ filename: sourceName, uriPath: uri.pathname, mimeType });
    const stem = _safeStem(sourceName, { extension });
    // `URL.href` normalizes like Dart's `Uri.toString()` (percent-encoding,
    // lower-case scheme/host, default port dropped).
    const key = `${namespace}:${uri.href}`;
    const hash = _fnv1a64Hex(key);
    return new MediaCacheRequest({
      uri: uri.href,
      cacheKey: hash,
      cacheFilename: `${stem}-${hash}${extension}`,
      expectedBytes,
    });
  }
}

export class MediaCacheController {
  /**
   * [httpClientFactory] returns the client for one download; it is closed
   * when the download finishes. The default is a per-download view of
   * `platform().http`. [cacheDirectoryProvider] defaults to the host's
   * application cache directory.
   */
  constructor(
    opts: {
      httpClientFactory?: () => HttpClient;
      cacheDirectoryProvider?: MediaCacheDirectoryProvider;
    } = {},
  ) {
    this._httpClientFactory = opts.httpClientFactory ?? (() => new _AbortableHttpClient(platform().http));
    this._cacheDirectoryProvider =
      opts.cacheDirectoryProvider ?? (async () => (await platform().files.directories()).applicationCache);
  }

  private readonly _httpClientFactory: () => HttpClient;
  private readonly _cacheDirectoryProvider: MediaCacheDirectoryProvider;
  private readonly _inFlight = new Map<string, Promise<MediaCacheFile>>();
  // Web only: insertion order doubles as recency (oldest first).
  private readonly _webMemory = new Map<string, Uint8Array>();
  private _webMemoryBytes = 0;
  private readonly _webInFlight = new Map<string, Promise<Uint8Array>>();

  async cachedFile(request: MediaCacheRequest): Promise<MediaCacheFile | null> {
    const file = await this._cacheFile(request);
    try {
      if (!(await file.exists())) return null;
      if ((await file.length()) <= 0) return null;
      return file;
    } catch {
      return null;
    }
  }

  async getOrDownload(opts: {
    request: MediaCacheRequest;
    transfer?: FileTransferState | null;
    onProgress?: MediaCacheProgressHandler | null;
  }): Promise<MediaCacheFile> {
    const { request } = opts;
    const existing = await this.cachedFile(request);
    if (existing != null) return existing;

    const active = this._inFlight.get(request.cacheKey);
    if (active != null) return active;

    const future = this._downloadToCache(opts);
    this._inFlight.set(request.cacheKey, future);
    try {
      return await future;
    } finally {
      if (this._inFlight.get(request.cacheKey) === future) {
        this._inFlight.delete(request.cacheKey);
      }
    }
  }

  readBytes(opts: { request: MediaCacheRequest }): Promise<Uint8Array> {
    return this.getOrDownloadBytes({ request: opts.request });
  }

  /**
   * The media's bytes. Native builds read them through the on-disk cache;
   * the web has no file system, so it downloads into a bounded in-memory
   * cache instead. Use this rather than [getOrDownload] in code that also
   * runs on the web.
   *
   * [transfer] and [onProgress] report progress and allow pausing and
   * cancelling, as for [getOrDownload].
   */
  async getOrDownloadBytes(opts: {
    request: MediaCacheRequest;
    transfer?: FileTransferState | null;
    onProgress?: MediaCacheProgressHandler | null;
  }): Promise<Uint8Array> {
    const { request } = opts;
    if (!HostPlatform.isWeb) {
      const file = await this.getOrDownload(opts);
      return file.readAsBytes();
    }
    const key = request.cacheKey;
    const cached = this._webMemory.get(key);
    if (cached != null) {
      this._webMemory.delete(key);
      this._webMemory.set(key, cached);
      return cached;
    }
    const active = this._webInFlight.get(key);
    if (active != null) return active;

    const future = this._downloadBytes(request, opts);
    this._webInFlight.set(key, future);
    try {
      const bytes = await future;
      this._rememberWebBytes(key, bytes);
      return bytes;
    } finally {
      if (this._webInFlight.get(key) === future) this._webInFlight.delete(key);
    }
  }

  private async _downloadBytes(
    request: MediaCacheRequest,
    opts: { transfer?: FileTransferState | null; onProgress?: MediaCacheProgressHandler | null },
  ): Promise<Uint8Array> {
    const transfer = opts.transfer ?? null;
    const onProgress = opts.onProgress ?? null;
    const client = this._httpClientFactory();
    if (transfer != null) transfer.downloadClient = client;
    try {
      const response = await client.send({ method: 'GET', url: request.uri });
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw new Error(`下载失败 (${response.statusCode})`);
      }
      // Browsers return the decoded body, so Content-Length (the encoded
      // size) can differ; only the size recorded for the file is reliable.
      const expected = request.expectedBytes;
      const total = expected ?? response.contentLength ?? 0;
      transfer?.updateProgress({ sentBytes: 0, totalBytes: total });
      onProgress?.({ sentBytes: 0, totalBytes: total });

      const builder = new _ChunkBuffer();
      for await (const chunk of response.stream) {
        await MediaCacheController._waitIfPaused(transfer);
        if (transfer?.cancelled === true) {
          throw new MediaCacheCancelledException();
        }
        builder.add(chunk);
        transfer?.updateProgress({ sentBytes: builder.length, totalBytes: total });
        onProgress?.({ sentBytes: builder.length, totalBytes: total });
      }
      if (transfer?.cancelled === true) {
        throw new MediaCacheCancelledException();
      }
      const bytes = builder.take();
      if (expected != null && expected > 0 && expected !== bytes.length) {
        throw new Error('下载不完整');
      }
      return bytes;
    } catch (error) {
      // Cancelling closes the client, which surfaces as a transport error.
      if (transfer?.cancelled === true) {
        throw new MediaCacheCancelledException();
      }
      throw error;
    } finally {
      if (transfer != null && transfer.downloadClient === client) {
        transfer.downloadClient = null;
      }
      client.close?.();
    }
  }

  private _rememberWebBytes(key: string, bytes: Uint8Array): void {
    // A single file larger than a quarter of the budget would evict most of
    // the cache; serve it without keeping it.
    if (bytes.length > Math.trunc(_webMemoryCacheMaxBytes / 4)) return;
    const previous = this._webMemory.get(key);
    if (previous != null) {
      this._webMemory.delete(key);
      this._webMemoryBytes -= previous.length;
    }
    this._webMemory.set(key, bytes);
    this._webMemoryBytes += bytes.length;
    while (this._webMemoryBytes > _webMemoryCacheMaxBytes && this._webMemory.size > 0) {
      const oldest = this._webMemory.keys().next().value as string;
      this._webMemoryBytes -= this._webMemory.get(oldest)!.length;
      this._webMemory.delete(oldest);
    }
  }

  async copyFileToPath(opts: {
    source: MediaCacheFile;
    destinationPath: string;
    transfer?: FileTransferState | null;
    onProgress?: MediaCacheProgressHandler | null;
  }): Promise<void> {
    const { source, destinationPath } = opts;
    const transfer = opts.transfer ?? null;
    const onProgress = opts.onProgress ?? null;
    if (source.path === destinationPath) {
      const total = await source.length();
      transfer?.updateProgress({ sentBytes: total, totalBytes: total });
      onProgress?.({ sentBytes: total, totalBytes: total });
      return;
    }

    const files = platform().files;
    const destination = new MediaCacheFile(destinationPath);
    await files.ensureDirectory(_parentPath(destinationPath));
    const temp = new MediaCacheFile(`${destinationPath}.gangtmp-${_microsecondsSinceEpoch()}`);
    let received = 0;
    const total = await source.length();
    try {
      await files.writeFile(temp.path, new Uint8Array(0));
      transfer?.updateProgress({ sentBytes: 0, totalBytes: total });
      onProgress?.({ sentBytes: 0, totalBytes: total });
      const pending = new _ChunkBuffer();
      for await (const chunk of _openRead(source.path)) {
        await MediaCacheController._waitIfPaused(transfer);
        if (transfer?.cancelled === true) {
          throw new MediaCacheCancelledException();
        }
        pending.add(chunk);
        if (pending.length >= _writeChunkBytes) await files.appendFile(temp.path, pending.take());
        received += chunk.length;
        transfer?.updateProgress({ sentBytes: received, totalBytes: total });
        onProgress?.({ sentBytes: received, totalBytes: total });
      }
      if (pending.length > 0) await files.appendFile(temp.path, pending.take());

      if (transfer?.cancelled === true) {
        throw new MediaCacheCancelledException();
      }
      if (await destination.exists()) await destination.delete();
      await temp.rename(destinationPath);
      if (transfer != null) transfer.wroteDestination = true;
    } catch (error) {
      await MediaCacheController._deleteQuietly(temp);
      throw error;
    }
  }

  private async _downloadToCache(opts: {
    request: MediaCacheRequest;
    transfer?: FileTransferState | null;
    onProgress?: MediaCacheProgressHandler | null;
  }): Promise<MediaCacheFile> {
    const { request } = opts;
    const transfer = opts.transfer ?? null;
    const onProgress = opts.onProgress ?? null;
    const files = platform().files;
    const file = await this._cacheFile(request);
    await files.ensureDirectory(_parentPath(file.path));
    const temp = new MediaCacheFile(`${file.path}.tmp-${_microsecondsSinceEpoch()}`);

    let client: HttpClient | null = null;
    let received = 0;
    try {
      client = this._httpClientFactory();
      if (transfer != null) transfer.downloadClient = client;
      const response = await client.send({ method: 'GET', url: request.uri });
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw new Error(`下载失败 (${response.statusCode})`);
      }
      const total = response.contentLength ?? request.expectedBytes ?? 0;
      transfer?.updateProgress({ sentBytes: 0, totalBytes: total });
      onProgress?.({ sentBytes: 0, totalBytes: total });

      await files.writeFile(temp.path, new Uint8Array(0));
      const pending = new _ChunkBuffer();
      for await (const chunk of response.stream) {
        await MediaCacheController._waitIfPaused(transfer);
        if (transfer?.cancelled === true) {
          throw new MediaCacheCancelledException();
        }
        pending.add(chunk);
        if (pending.length >= _writeChunkBytes) await files.appendFile(temp.path, pending.take());
        received += chunk.length;
        transfer?.updateProgress({ sentBytes: received, totalBytes: total });
        onProgress?.({ sentBytes: received, totalBytes: total });
      }
      if (pending.length > 0) await files.appendFile(temp.path, pending.take());

      if (transfer?.cancelled === true) {
        throw new MediaCacheCancelledException();
      }
      const expectedLength = response.contentLength ?? request.expectedBytes;
      if (expectedLength != null && expectedLength > 0 && expectedLength !== received) {
        throw new Error('下载不完整');
      }
      if (await file.exists()) await file.delete();
      return await temp.rename(file.path);
    } catch (error) {
      await MediaCacheController._deleteQuietly(temp);
      throw error;
    } finally {
      if (transfer != null && transfer.downloadClient === client) {
        transfer.downloadClient = null;
      }
      client?.close?.();
    }
  }

  private async _cacheFile(request: MediaCacheRequest): Promise<MediaCacheFile> {
    const root = await this._cacheDirectoryProvider();
    const separator = HostPlatform.pathSeparator;
    return new MediaCacheFile(
      `${root}${separator}media-assets${separator}${_mediaCacheVersion}${separator}${request.cacheFilename}`,
    );
  }

  private static _waitIfPaused(transfer: FileTransferState | null | undefined): Promise<void> {
    const controller = transfer?.controller;
    if (controller == null) return Promise.resolve();
    return controller.waitIfPaused();
  }

  private static async _deleteQuietly(file: MediaCacheFile): Promise<void> {
    try {
      if (await file.exists()) await file.delete();
    } catch {
      // ignore
    }
  }
}

/** `File.openRead()`: the host's chunk stream, or [readFile] cut into chunks. */
async function* _openRead(path: string): AsyncIterable<Uint8Array> {
  const files = platform().files;
  if (files.openRead != null) {
    yield* files.openRead(path);
    return;
  }
  const bytes = await files.readFile(path);
  for (let offset = 0; offset < bytes.length; offset += _readChunkBytes) {
    yield bytes.subarray(offset, Math.min(offset + _readChunkBytes, bytes.length));
  }
}

function _parentPath(path: string): string {
  const index = _lastSeparatorIndex(path);
  if (index < 0) return '.';
  if (index === 0) return path.substring(0, 1);
  return path.substring(0, index);
}

/** `DateTime.now().microsecondsSinceEpoch`, for unique temp file names. */
function _microsecondsSinceEpoch(): string {
  return `${Date.now()}${Math.floor(Math.random() * 1000)
    .toString()
    .padStart(3, '0')}`;
}

function _isHttpUri(uri: URL): boolean {
  return uri.protocol === 'http:' || uri.protocol === 'https:';
}

function _firstNonEmpty(values: Iterable<string | null | undefined>): string | null {
  for (const value of values) {
    const trimmed = value?.trim();
    if (trimmed != null && trimmed.length > 0) return trimmed;
  }
  return null;
}

function _basenameFromUriPath(path: string): string | null {
  if (path.length === 0) return null;
  const segments = path.split('/').filter((segment) => segment.length > 0);
  if (segments.length === 0) return null;
  const last = segments[segments.length - 1]!;
  try {
    return decodeURIComponent(last);
  } catch {
    return last;
  }
}

function _extensionFor(opts: {
  filename?: string | null;
  uriPath?: string | null;
  mimeType?: string | null;
}): string {
  for (const candidate of [opts.filename, _basenameFromUriPath(opts.uriPath ?? '')]) {
    const ext = _extensionFromName(candidate);
    if (ext != null) return ext;
  }
  switch ((opts.mimeType ?? '').toLowerCase()) {
    case 'image/jpeg':
      return '.jpg';
    case 'image/png':
      return '.png';
    case 'image/gif':
      return '.gif';
    case 'image/webp':
      return '.webp';
    case 'application/pdf':
      return '.pdf';
    case 'text/plain':
      return '.txt';
    default:
      return '';
  }
}

function _extensionFromName(value: string | null | undefined): string | null {
  const name = value?.trim();
  if (name == null || name.length === 0) return null;
  const slash = _lastSeparatorIndex(name);
  const base = slash >= 0 ? name.substring(slash + 1) : name;
  const dot = base.lastIndexOf('.');
  if (dot <= 0 || dot === base.length - 1) return null;
  const ext = base.substring(dot).toLowerCase();
  if (ext.length > 12) return null;
  for (let i = 0; i < ext.length; i++) {
    const unit = ext.charCodeAt(i);
    const isDot = unit === 46;
    const isDigit = unit >= 48 && unit <= 57;
    const isLower = unit >= 97 && unit <= 122;
    if (!isDot && !isDigit && !isLower) return null;
  }
  return ext;
}

function _safeStem(value: string, opts: { extension: string }): string {
  const { extension } = opts;
  let base = value.replaceAll('\\', '/');
  const slash = base.lastIndexOf('/');
  if (slash >= 0) base = base.substring(slash + 1);
  base = base.trim();
  if (extension.length > 0 && base.toLowerCase().endsWith(extension)) {
    base = base.substring(0, base.length - extension.length);
  }
  let buffer = '';
  let lastDash = false;
  for (let i = 0; i < base.length; i++) {
    const unit = base.charCodeAt(i);
    if (_isSafeStemUnit(unit)) {
      buffer += String.fromCharCode(unit);
      lastDash = false;
    } else if (!lastDash) {
      buffer += '-';
      lastDash = true;
    }
  }
  const raw = buffer;
  let start = 0;
  let end = raw.length;
  while (start < end && _isTrimmedStemUnit(raw.charCodeAt(start))) {
    start++;
  }
  while (end > start && _isTrimmedStemUnit(raw.charCodeAt(end - 1))) {
    end--;
  }
  const stem = start === end ? 'asset' : raw.substring(start, end);
  return stem.length <= 64 ? stem : stem.substring(0, 64);
}

function _lastSeparatorIndex(value: string): number {
  const slash = value.lastIndexOf('/');
  const backslash = value.lastIndexOf('\\');
  return slash > backslash ? slash : backslash;
}

function _isSafeStemUnit(unit: number): boolean {
  return (
    unit === 45 ||
    unit === 46 ||
    unit === 95 ||
    (unit >= 48 && unit <= 57) ||
    (unit >= 65 && unit <= 90) ||
    (unit >= 97 && unit <= 122)
  );
}

function _isTrimmedStemUnit(unit: number): boolean {
  return unit === 45 || unit === 46;
}

function _fnv1a64Hex(value: string): string {
  // Signed, so keys match the files already cached on disk (some start
  // with '-').
  return fnv1a64Signed(value).toString(16).padStart(16, '0');
}
