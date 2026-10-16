import { beforeEach, describe, expect, test } from 'vitest';

import { DownloadCancelledException, FileDownloadsController } from '../../src/app/file_downloads_controller';
import { FileTransferState } from '../../src/app/file_transfer_state';
import { ClientException, type HttpClient, type HttpRequest, type StreamedResponse } from '../../src/platform/http';
import { UploadTransferController } from '../../src/protocol/api_client';
import { type FakePlatform, installFakePlatform } from '../support/fake_platform';

/** Port of the Dart test's `_StreamClient`: streams fixed chunks. */
class _StreamClient implements HttpClient {
  constructor(
    private readonly chunks: number[][],
    private readonly contentLength: number,
    private readonly statusCode = 200,
  ) {}

  closed = false;

  async send(request: HttpRequest): Promise<StreamedResponse> {
    if (this.closed) throw new ClientException('closed', request.url);
    const chunks = this.chunks;
    return {
      statusCode: this.statusCode,
      headers: {},
      contentLength: this.contentLength,
      stream: (async function* () {
        for (const chunk of chunks) yield Uint8Array.from(chunk);
      })(),
    };
  }

  close(): void {
    this.closed = true;
  }
}

let fake: FakePlatform;

beforeEach(() => {
  fake = installFakePlatform();
});

describe('FileDownloadsController.downloadToFile', () => {
  test('streams bytes and reports progress', async () => {
    const destination = '/tmp/gang_download_test/report.txt';
    let client: _StreamClient | null = null;
    const controller = new FileDownloadsController({
      httpClientFactory: () => (client = new _StreamClient([[1, 2], [3, 4]], 4)),
    });
    const transfer = controller.createDownload({ totalBytes: 0, destinationPath: destination });
    const progress: number[] = [];

    await controller.downloadToFile({
      uri: 'http://example.test/report.txt',
      transfer,
      onProgress: ({ sentBytes, totalBytes }) => {
        expect(totalBytes).toBe(4);
        progress.push(sentBytes);
      },
    });

    expect([...(await fake.files.readFile(destination))]).toEqual([1, 2, 3, 4]);
    expect(progress).toEqual([0, 2, 4]);
    expect(transfer.downloadClient).toBeNull();
    expect(client!.closed).toBe(true);
  });

  test('deletes partial file after cancellation', async () => {
    const destination = '/tmp/gang_download_test/partial.bin';
    const controller = new FileDownloadsController({
      httpClientFactory: () => new _StreamClient([[1, 2, 3]], 3),
    });
    const transfer = controller.createDownload({ totalBytes: 0, destinationPath: destination });

    await expect(
      controller.downloadToFile({
        uri: 'http://example.test/partial.bin',
        transfer,
        onProgress: ({ sentBytes }) => {
          if (sentBytes > 0) controller.cancel(transfer);
        },
      }),
    ).rejects.toBeInstanceOf(DownloadCancelledException);

    expect(await fake.files.exists(destination)).toBe(false);
    expect(transfer.downloadClient).toBeNull();
  });

  test('rejects HTTP failures without touching the destination', async () => {
    const destination = '/tmp/gang_download_test/missing.bin';
    const controller = new FileDownloadsController({
      httpClientFactory: () => new _StreamClient([], 0, 404),
    });
    const transfer = controller.createDownload({ totalBytes: 0, destinationPath: destination });

    await expect(controller.downloadToFile({ uri: 'http://example.test/missing.bin', transfer })).rejects.toThrow(
      '下载失败 (404)',
    );
    expect(transfer.wroteDestination).toBe(false);
    expect(await fake.files.exists(destination)).toBe(false);
  });

  test('the default client streams through platform().http', async () => {
    fake = installFakePlatform({ http: new _StreamClient([[7], [8, 9]], 3) });
    const destination = '/tmp/gang_download_test/default.bin';
    const controller = new FileDownloadsController();
    const transfer = controller.createDownload({ totalBytes: 0, destinationPath: destination });

    await controller.downloadToFile({ uri: 'http://example.test/default.bin', transfer });

    expect([...(await fake.files.readFile(destination))]).toEqual([7, 8, 9]);
    expect((fake.http as _StreamClient).closed).toBe(false);
  });
});

describe('FileDownloadsController state patches', () => {
  test('download state patches cover started completed failed and removed', () => {
    const controller = new FileDownloadsController();
    const transfer = controller.createDownload({ totalBytes: 100, destinationPath: '/tmp/report.pdf' });

    expect(controller.canStartDownload({ downloads: new Map(), downloadKey: 'message_1:file' })).toBe(true);

    let patch = controller.patchStartedDownload({ downloads: new Map(), downloadKey: 'message_1:file', transfer });
    expect(patch.downloads.get('message_1:file')).toBe(transfer);
    expect(controller.canStartDownload({ downloads: patch.downloads, downloadKey: 'message_1:file' })).toBe(false);
    expect(
      controller.shouldApplyDownloadProgress({ downloads: patch.downloads, downloadKey: 'message_1:file', transfer }),
    ).toBe(true);
    const progressPatch = controller.patchDownloadProgress({
      downloads: patch.downloads,
      downloadKey: 'message_1:file',
      transfer,
    });
    expect(progressPatch).not.toBeNull();
    expect(progressPatch!.downloads).toBe(patch.downloads);

    patch = controller.patchFailedDownload({ downloads: patch.downloads, transfer, failure: 'network failed' });
    expect(transfer.failed).toBe(true);
    expect(transfer.error).toBe('网络连接失败，请检查网络后重试');

    patch = controller.patchRemovedDownload({ downloads: patch.downloads, downloadKey: 'message_1:file' });
    expect(patch.downloads.size).toBe(0);
  });

  test('download completion and cleanup guards reject stale transfers', () => {
    const controller = new FileDownloadsController();
    const active = controller.createDownload({ totalBytes: 100, destinationPath: '/tmp/active.pdf' });
    const stale = controller.createDownload({ totalBytes: 100, destinationPath: '/tmp/stale.pdf' });
    const downloads = new Map([['download', active]]);

    expect(controller.canCompleteDownload({ downloads, downloadKey: 'download', transfer: active })).toBe(true);
    expect(controller.canCompleteDownload({ downloads, downloadKey: 'download', transfer: stale })).toBe(false);

    active.wroteDestination = true;
    expect(controller.partialDownloadPathToDelete(active)).toBe('/tmp/active.pdf');
    expect(controller.partialDownloadPathToDelete(stale)).toBeNull();

    active.cancelTransfer();
    expect(controller.canCompleteDownload({ downloads, downloadKey: 'download', transfer: active })).toBe(false);
  });

  test('download controls pause resume and cancel only download transfers', () => {
    const controller = new FileDownloadsController();
    const download = controller.createDownload({ totalBytes: 100, destinationPath: '/tmp/report.pdf' });
    const upload = FileTransferState.upload({ controller: new UploadTransferController(), totalBytes: 100 });
    const downloads = new Map([
      ['download', download],
      ['upload', upload],
    ]);

    expect(controller.patchPausedDownload({ downloads, downloadKey: 'download' })).not.toBeNull();
    expect(download.paused).toBe(true);
    expect(controller.patchPausedDownload({ downloads, downloadKey: 'download' })).toBeNull();

    expect(controller.patchResumedDownload({ downloads, downloadKey: 'download' })).not.toBeNull();
    expect(download.paused).toBe(false);
    expect(controller.patchResumedDownload({ downloads, downloadKey: 'download' })).toBeNull();

    expect(controller.pauseDownload({ downloads, downloadKey: 'upload' })).toBe(false);
    expect(controller.cancelDownload({ downloads, downloadKey: 'upload' })).toBe(false);
    expect(controller.cancelDownload({ downloads, downloadKey: 'missing' })).toBe(false);

    expect(controller.cancelDownload({ downloads, downloadKey: 'download' })).toBe(true);
    expect(download.cancelled).toBe(true);
    expect(controller.cancelDownload({ downloads, downloadKey: 'download' })).toBe(false);
  });
});
