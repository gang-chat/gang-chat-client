import { ClientException, type HttpClient, type HttpRequest, type StreamedResponse } from '../platform/http';
import { UploadTransferController } from '../protocol/api_client';
import { userFacingErrorMessage } from './error_display';

export enum FileTransferDirection {
  upload = 'upload',
  download = 'download',
}

export class FileTransferState {
  private constructor(opts: {
    direction: FileTransferDirection;
    controller: UploadTransferController;
    totalBytes: number;
    destinationPath: string | null;
  }) {
    this.direction = opts.direction;
    this.controller = opts.controller;
    this.totalBytes = opts.totalBytes;
    this.destinationPath = opts.destinationPath;
  }

  static upload(opts: { controller: UploadTransferController; totalBytes: number }): FileTransferState {
    return new FileTransferState({
      direction: FileTransferDirection.upload,
      controller: opts.controller,
      totalBytes: opts.totalBytes,
      destinationPath: null,
    });
  }

  static download(opts: {
    controller: UploadTransferController;
    totalBytes: number;
    destinationPath: string | null;
  }): FileTransferState {
    return new FileTransferState({
      direction: FileTransferDirection.download,
      controller: opts.controller,
      totalBytes: opts.totalBytes,
      destinationPath: opts.destinationPath,
    });
  }

  readonly direction: FileTransferDirection;
  readonly controller: UploadTransferController;
  readonly destinationPath: string | null;
  sentBytes = 0;
  totalBytes: number;
  bytesPerSecond = 0;
  /**
   * The client streaming the download. Closing it aborts the response stream,
   * which is how [cancelTransfer] interrupts a download in flight.
   */
  downloadClient: HttpClient | null = null;
  wroteDestination = false;
  sendingMessage = false;
  failed = false;
  error: string | null = null;
  private _speedSampleAt: number | null = null;
  private _speedSampleBytes = 0;

  get isDownload(): boolean {
    return this.direction === FileTransferDirection.download;
  }

  get paused(): boolean {
    return this.controller.isPaused;
  }

  get cancelled(): boolean {
    return this.controller.isCancelled;
  }

  get active(): boolean {
    return !this.failed && !this.cancelled && !this.sendingMessage;
  }

  get hasKnownTotal(): boolean {
    return this.totalBytes > 0;
  }

  get progress(): number {
    if (this.totalBytes <= 0) return 0;
    return Math.min(Math.max(this.sentBytes / this.totalBytes, 0.0), 1.0);
  }

  updateProgress(opts: { sentBytes: number; totalBytes: number }): void {
    const { sentBytes, totalBytes } = opts;
    const now = Date.now();
    if (this._speedSampleAt == null) {
      this._speedSampleAt = now;
      this._speedSampleBytes = this.sentBytes;
    } else {
      const elapsed = now - this._speedSampleAt;
      if (elapsed >= 400 || (sentBytes >= totalBytes && totalBytes > 0)) {
        const deltaBytes = sentBytes - this._speedSampleBytes;
        this.bytesPerSecond = elapsed > 0 ? (deltaBytes * 1000) / elapsed : 0;
        this._speedSampleAt = now;
        this._speedSampleBytes = sentBytes;
      }
    }

    this.sentBytes = sentBytes;
    this.totalBytes = totalBytes;
  }

  markSendingMessage(): void {
    this.sendingMessage = true;
    this.updateProgress({ sentBytes: this.totalBytes, totalBytes: this.totalBytes });
  }

  markFailed(failure: unknown, opts: { stopTransferSpeed?: boolean } = {}): void {
    const { stopTransferSpeed = false } = opts;
    this.failed = true;
    this.sendingMessage = false;
    this.error = userFacingErrorMessage(failure, { fallback: '文件传输失败' });
    if (stopTransferSpeed) this.stopSpeed();
  }

  pauseTransfer(): boolean {
    if (!this.active || this.paused) return false;
    this.controller.pause();
    this.stopSpeed();
    return true;
  }

  resumeTransfer(): boolean {
    if (!this.paused) return false;
    this.controller.resume();
    return true;
  }

  cancelTransfer(): boolean {
    if (!this.active) return false;
    this.controller.cancel();
    this.downloadClient?.close?.();
    return true;
  }

  stopSpeed(): void {
    this.bytesPerSecond = 0;
    this._speedSampleAt = null;
    this._speedSampleBytes = this.sentBytes;
  }
}

/**
 * A per-download view of a shared [HttpClient] whose [close] aborts only the
 * requests sent through it — what `http.Client()` + `client.close()` gave the
 * Dart controllers. The shared `platform().http` client itself is never
 * closed.
 */
export class _AbortableHttpClient implements HttpClient {
  constructor(private readonly _inner: HttpClient) {}

  private readonly _abort = new AbortController();

  async send(request: HttpRequest): Promise<StreamedResponse> {
    if (this._abort.signal.aborted) throw new ClientException('closed', request.url);
    const signal = this._abort.signal;
    if (request.signal != null) {
      const outer = request.signal;
      if (outer.aborted) this._abort.abort();
      else outer.addEventListener('abort', () => this._abort.abort(), { once: true });
    }
    return this._inner.send({ ...request, signal });
  }

  close(): void {
    this._abort.abort();
  }
}

/** Collects streamed chunks so file appends go out in larger writes. */
export class _ChunkBuffer {
  private _chunks: Uint8Array[] = [];
  private _length = 0;

  get length(): number {
    return this._length;
  }

  add(chunk: Uint8Array): void {
    this._chunks.push(chunk);
    this._length += chunk.length;
  }

  /** Returns the collected bytes and clears the buffer. */
  take(): Uint8Array {
    const chunks = this._chunks;
    const length = this._length;
    this._chunks = [];
    this._length = 0;
    if (chunks.length === 1) return chunks[0]!;
    const out = new Uint8Array(length);
    let offset = 0;
    for (const chunk of chunks) {
      out.set(chunk, offset);
      offset += chunk.length;
    }
    return out;
  }
}
