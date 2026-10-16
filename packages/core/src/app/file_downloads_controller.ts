import type { HttpClient } from '../platform/http';
import { platform } from '../platform/platform';
import { UploadTransferController } from '../protocol/api_client';
import { FileTransferState, _AbortableHttpClient, _ChunkBuffer } from './file_transfer_state';

export type FileDownloadProgressHandler = (progress: { sentBytes: number; totalBytes: number }) => void;

export class DownloadCancelledException extends Error {
  constructor() {
    super('下载已取消');
    this.name = 'DownloadCancelledException';
  }

  override toString(): string {
    return '下载已取消';
  }
}

export class FileDownloadStatePatch {
  constructor(opts: { downloads: Map<string, FileTransferState> }) {
    this.downloads = opts.downloads;
  }

  readonly downloads: Map<string, FileTransferState>;
}

/** Bytes buffered before each append to the destination file. */
const _writeChunkBytes = 1024 * 1024;

export class FileDownloadsController {
  /**
   * [httpClientFactory] returns the client for one download; it is closed
   * when the download finishes (closing aborts the response stream). The
   * default is a per-download view of `platform().http`.
   */
  constructor(opts: { httpClientFactory?: () => HttpClient } = {}) {
    this._httpClientFactory = opts.httpClientFactory ?? (() => new _AbortableHttpClient(platform().http));
  }

  private readonly _httpClientFactory: () => HttpClient;

  createDownload(opts: { totalBytes: number; destinationPath: string }): FileTransferState {
    return FileTransferState.download({
      controller: new UploadTransferController(),
      totalBytes: opts.totalBytes,
      destinationPath: opts.destinationPath,
    });
  }

  canStartDownload(opts: { downloads: Map<string, FileTransferState>; downloadKey: string }): boolean {
    return !opts.downloads.has(opts.downloadKey);
  }

  patchStartedDownload(opts: {
    downloads: Map<string, FileTransferState>;
    downloadKey: string;
    transfer: FileTransferState;
  }): FileDownloadStatePatch {
    return new FileDownloadStatePatch({
      downloads: new Map(opts.downloads).set(opts.downloadKey, opts.transfer),
    });
  }

  shouldApplyDownloadProgress(opts: {
    downloads: Map<string, FileTransferState>;
    downloadKey: string;
    transfer: FileTransferState;
  }): boolean {
    return opts.downloads.get(opts.downloadKey) === opts.transfer;
  }

  patchDownloadProgress(opts: {
    downloads: Map<string, FileTransferState>;
    downloadKey: string;
    transfer: FileTransferState;
  }): FileDownloadStatePatch | null {
    if (!this.shouldApplyDownloadProgress(opts)) {
      return null;
    }
    return new FileDownloadStatePatch({ downloads: opts.downloads });
  }

  canCompleteDownload(opts: {
    downloads: Map<string, FileTransferState>;
    downloadKey: string;
    transfer: FileTransferState;
  }): boolean {
    return !opts.transfer.cancelled && this.shouldApplyDownloadProgress(opts);
  }

  patchCompletedDownload(opts: { downloads: Map<string, FileTransferState>; downloadKey: string }): FileDownloadStatePatch {
    return this.patchRemovedDownload(opts);
  }

  patchFailedDownload(opts: {
    downloads: Map<string, FileTransferState>;
    transfer: FileTransferState;
    failure: unknown;
  }): FileDownloadStatePatch {
    opts.transfer.markFailed(opts.failure, { stopTransferSpeed: true });
    return new FileDownloadStatePatch({ downloads: opts.downloads });
  }

  patchRemovedDownload(opts: { downloads: Map<string, FileTransferState>; downloadKey: string }): FileDownloadStatePatch {
    const next = new Map(opts.downloads);
    next.delete(opts.downloadKey);
    return new FileDownloadStatePatch({ downloads: next });
  }

  partialDownloadPathToDelete(transfer: FileTransferState): string | null {
    const destinationPath = transfer.destinationPath;
    if (destinationPath == null || !transfer.wroteDestination) return null;
    return destinationPath;
  }

  pauseDownload(opts: { downloads: Map<string, FileTransferState>; downloadKey: string }): boolean {
    const transfer = opts.downloads.get(opts.downloadKey);
    if (transfer == null || !transfer.isDownload) return false;
    return transfer.pauseTransfer();
  }

  patchPausedDownload(opts: {
    downloads: Map<string, FileTransferState>;
    downloadKey: string;
  }): FileDownloadStatePatch | null {
    const changed = this.pauseDownload(opts);
    if (!changed) return null;
    return new FileDownloadStatePatch({ downloads: opts.downloads });
  }

  resumeDownload(opts: { downloads: Map<string, FileTransferState>; downloadKey: string }): boolean {
    const transfer = opts.downloads.get(opts.downloadKey);
    if (transfer == null || !transfer.isDownload) return false;
    return transfer.resumeTransfer();
  }

  patchResumedDownload(opts: {
    downloads: Map<string, FileTransferState>;
    downloadKey: string;
  }): FileDownloadStatePatch | null {
    const changed = this.resumeDownload(opts);
    if (!changed) return null;
    return new FileDownloadStatePatch({ downloads: opts.downloads });
  }

  cancelDownload(opts: { downloads: Map<string, FileTransferState>; downloadKey: string }): boolean {
    const transfer = opts.downloads.get(opts.downloadKey);
    if (transfer == null || !transfer.isDownload) return false;
    return transfer.cancelTransfer();
  }

  /** Streams [uri] into `transfer.destinationPath`. */
  async downloadToFile(opts: {
    uri: string;
    transfer: FileTransferState;
    onProgress?: FileDownloadProgressHandler | null;
  }): Promise<void> {
    const { uri, transfer, onProgress } = opts;
    const destinationPath = transfer.destinationPath;
    if (destinationPath == null) {
      throw new Error('下载位置不存在');
    }

    const files = platform().files;
    let client: HttpClient | null = null;
    try {
      client = this._httpClientFactory();
      transfer.downloadClient = client;
      const response = await client.send({ method: 'GET', url: uri });
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw new Error(`下载失败 (${response.statusCode})`);
      }

      const totalBytes = response.contentLength ?? transfer.totalBytes;
      let receivedBytes = 0;
      transfer.updateProgress({ sentBytes: 0, totalBytes });
      onProgress?.({ sentBytes: 0, totalBytes });

      // `File.openWrite()`: truncate, then append as the body streams in.
      await files.writeFile(destinationPath, new Uint8Array(0));
      transfer.wroteDestination = true;
      const pending = new _ChunkBuffer();
      for await (const chunk of response.stream) {
        await transfer.controller.waitIfPaused();
        if (transfer.cancelled) throw new DownloadCancelledException();

        pending.add(chunk);
        if (pending.length >= _writeChunkBytes) await files.appendFile(destinationPath, pending.take());
        receivedBytes += chunk.length;
        transfer.updateProgress({ sentBytes: receivedBytes, totalBytes });
        onProgress?.({ sentBytes: receivedBytes, totalBytes });
      }

      if (pending.length > 0) await files.appendFile(destinationPath, pending.take());

      if (transfer.cancelled) throw new DownloadCancelledException();
    } catch (error) {
      if (error instanceof DownloadCancelledException) {
        if (transfer.wroteDestination) {
          await this.deletePartialDownload(destinationPath);
        }
        throw error;
      }
      if (transfer.cancelled) {
        if (transfer.wroteDestination) {
          await this.deletePartialDownload(destinationPath);
        }
        throw new DownloadCancelledException();
      }
      throw error;
    } finally {
      client?.close?.();
      transfer.downloadClient = null;
    }
  }

  cancel(transfer: FileTransferState): void {
    transfer.cancelTransfer();
  }

  async deletePartialDownload(path: string): Promise<void> {
    try {
      const files = platform().files;
      if (await files.exists(path)) await files.deleteFile(path);
    } catch {
      // Best effort cleanup for a cancelled or failed partial download.
    }
  }
}
