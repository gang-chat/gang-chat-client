import { beforeEach, describe, expect, test } from 'vitest';

import { FileDownloadsController } from '../../src/app/file_downloads_controller';
import { FileTransferState } from '../../src/app/file_transfer_state';
import {
  MediaCacheCancelledException,
  MediaCacheController,
  MediaCacheFile,
  MediaCacheRequest,
} from '../../src/app/media_cache_controller';
import { ClientException, type HttpClient, type HttpRequest, type StreamedResponse } from '../../src/platform/http';
import { UploadTransferController } from '../../src/protocol/api_client';
import { type FakePlatform, installFakePlatform } from '../support/fake_platform';

/** Streams the body one chunk per entry (the Dart test streamed one byte per chunk). */
class _BytesClient implements HttpClient {
  constructor(
    private readonly chunks: () => number[][],
    private readonly opts: { statusCode?: number; contentLength?: number | null } = {},
  ) {}

  closed = false;

  async send(request: HttpRequest): Promise<StreamedResponse> {
    if (this.closed) throw new ClientException('closed', request.url);
    const chunks = this.chunks();
    const length = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
    return {
      statusCode: this.opts.statusCode ?? 200,
      headers: {},
      contentLength: this.opts.contentLength === undefined ? length : this.opts.contentLength,
      stream: (async function* () {
        for (const chunk of chunks) yield Uint8Array.from(chunk);
      })(),
    };
  }

  close(): void {
    this.closed = true;
  }
}

const bytewise = (bytes: number[]): number[][] => bytes.map((byte) => [byte]);

let fake: FakePlatform;

describe('MediaCacheController (disk cache)', () => {
  beforeEach(() => {
    fake = installFakePlatform({ os: 'linux' });
  });

  test('getOrDownload stores bytes and reuses cached file', async () => {
    let requests = 0;
    const controller = new MediaCacheController({
      cacheDirectoryProvider: async () => '/tmp/gang_media_cache_test',
      httpClientFactory: () =>
        new _BytesClient(() => {
          requests += 1;
          return bytewise([1, 2, 3, 4]);
        }),
    });
    const request = MediaCacheRequest.tryFromUrl({
      url: 'https://assets.example.test/files/report.pdf',
      filename: 'report.pdf',
      mimeType: 'application/pdf',
      expectedBytes: 4,
    })!;

    const first = await controller.getOrDownload({ request });
    expect([...(await first.readAsBytes())]).toEqual([1, 2, 3, 4]);
    const second = await controller.getOrDownload({ request });

    expect(second.path).toBe(first.path);
    expect(first.path.startsWith('/tmp/gang_media_cache_test/media-assets/v1/report-')).toBe(true);
    expect(first.path.endsWith('.pdf')).toBe(true);
    expect(requests).toBe(1);
  });

  test('concurrent getOrDownload calls share one download', async () => {
    let requests = 0;
    const controller = new MediaCacheController({
      cacheDirectoryProvider: async () => '/cache',
      httpClientFactory: () =>
        new _BytesClient(() => {
          requests += 1;
          return [[5, 6]];
        }),
    });
    const request = MediaCacheRequest.tryFromUrl({ url: 'https://assets.example.test/a.png' })!;

    const [a, b] = await Promise.all([controller.getOrDownload({ request }), controller.getOrDownload({ request })]);

    expect(a.path).toBe(b.path);
    expect(requests).toBe(1);
    expect([...(await controller.readBytes({ request }))]).toEqual([5, 6]);
  });

  test('copyFileToPath writes cached bytes atomically', async () => {
    fake.files.put('/tmp/gang_media_cache_test/source.bin', [5, 6, 7]);
    const source = new MediaCacheFile('/tmp/gang_media_cache_test/source.bin');
    const destination = '/tmp/gang_media_cache_test/nested/destination.bin';
    const controller = new MediaCacheController({ cacheDirectoryProvider: async () => '/tmp/gang_media_cache_test' });
    const transfer = FileTransferState.download({
      controller: new UploadTransferController(),
      totalBytes: 0,
      destinationPath: destination,
    });
    const progress: number[] = [];

    await controller.copyFileToPath({
      source,
      destinationPath: destination,
      transfer,
      onProgress: ({ sentBytes, totalBytes }) => {
        expect(totalBytes).toBe(3);
        progress.push(sentBytes);
      },
    });

    expect([...(await fake.files.readFile(destination))]).toEqual([5, 6, 7]);
    expect(transfer.wroteDestination).toBe(true);
    expect(progress).toEqual([0, 3]);
    expect([...fake.files.files.keys()].some((path) => path.includes('.gangtmp-'))).toBe(false);
  });

  test('getOrDownload deletes partial cache file after cancellation', async () => {
    const controller = new MediaCacheController({
      cacheDirectoryProvider: async () => '/tmp/gang_media_cache_test',
      httpClientFactory: () => new _BytesClient(() => bytewise([8, 9, 10])),
    });
    const request = MediaCacheRequest.tryFromUrl({
      url: 'https://assets.example.test/files/cancel.bin',
      filename: 'cancel.bin',
      expectedBytes: 3,
    })!;
    const transfer = FileTransferState.download({
      controller: new UploadTransferController(),
      totalBytes: 0,
      destinationPath: '/tmp/gang_media_cache_test/cancel.bin',
    });

    await expect(
      controller.getOrDownload({
        request,
        transfer,
        onProgress: ({ sentBytes }) => {
          if (sentBytes > 0) transfer.cancelTransfer();
        },
      }),
    ).rejects.toBeInstanceOf(MediaCacheCancelledException);

    expect(await controller.cachedFile(request)).toBeNull();
    expect([...fake.files.files.keys()].some((path) => path.includes('.tmp-'))).toBe(false);
  });

  test('an incomplete body is rejected and not cached', async () => {
    const controller = new MediaCacheController({
      cacheDirectoryProvider: async () => '/cache',
      httpClientFactory: () => new _BytesClient(() => [[1, 2]], { contentLength: 4 }),
    });
    const request = MediaCacheRequest.tryFromUrl({ url: 'https://assets.example.test/short.bin' })!;

    await expect(controller.getOrDownload({ request })).rejects.toThrow('下载不完整');
    expect(await controller.cachedFile(request)).toBeNull();
  });
});

describe('MediaCacheController (web memory cache)', () => {
  beforeEach(() => {
    fake = installFakePlatform({ os: 'web' });
  });

  const request = (expectedBytes?: number) =>
    MediaCacheRequest.tryFromUrl({
      url: 'https://os.example.test/gang-chat/avatars/u1.png',
      filename: 'u1.png',
      mimeType: 'image/png',
      expectedBytes,
    })!;

  test('downloads once and serves repeat and concurrent requests from memory', async () => {
    let requests = 0;
    const controller = new MediaCacheController({
      httpClientFactory: () =>
        new _BytesClient(() => {
          requests += 1;
          return [[1, 2, 3, 4]];
        }),
    });

    const concurrent = await Promise.all([
      controller.getOrDownloadBytes({ request: request(4) }),
      controller.getOrDownloadBytes({ request: request(4) }),
    ]);
    const later = await controller.readBytes({ request: request(4) });

    expect([...concurrent[0]!]).toEqual([1, 2, 3, 4]);
    expect([...concurrent[1]!]).toEqual([1, 2, 3, 4]);
    expect([...later]).toEqual([1, 2, 3, 4]);
    expect(requests).toBe(1);
    expect(fake.files.files.size).toBe(0);
  });

  test('a failed download is reported and retried next time', async () => {
    let status = 404;
    let requests = 0;
    const controller = new MediaCacheController({
      httpClientFactory: () =>
        new _BytesClient(
          () => {
            requests += 1;
            return [[9]];
          },
          {
            get statusCode() {
              return status;
            },
          },
        ),
    });

    await expect(controller.getOrDownloadBytes({ request: request() })).rejects.toThrow('下载失败 (404)');
    status = 200;
    expect([...(await controller.getOrDownloadBytes({ request: request() }))]).toEqual([9]);
    expect(requests).toBe(2);
  });

  test('rejects a body that does not match the recorded file size', async () => {
    const controller = new MediaCacheController({ httpClientFactory: () => new _BytesClient(() => [[1, 2]]) });

    await expect(controller.getOrDownloadBytes({ request: request(4) })).rejects.toThrow('下载不完整');
  });

  test('reports download progress to the transfer', async () => {
    const controller = new MediaCacheController({
      httpClientFactory: () => new _BytesClient(() => [[1, 2], [3, 4]], { contentLength: null }),
    });
    const transfer = new FileDownloadsController().createDownload({ totalBytes: 4, destinationPath: 'report.pdf' });
    const progress: number[] = [];

    const bytes = await controller.getOrDownloadBytes({
      request: request(4),
      transfer,
      onProgress: ({ sentBytes }) => progress.push(sentBytes),
    });

    expect([...bytes]).toEqual([1, 2, 3, 4]);
    expect(progress).toEqual([0, 2, 4]);
    expect(transfer.sentBytes).toBe(4);
    expect(transfer.downloadClient).toBeNull();
  });

  test('cancelling a download reports cancellation, not a failure', async () => {
    const controller = new MediaCacheController({ httpClientFactory: () => new _BytesClient(() => [[1, 2], [3, 4]]) });
    const downloads = new FileDownloadsController();
    const transfer = downloads.createDownload({ totalBytes: 4, destinationPath: 'report.pdf' });

    await expect(
      controller.getOrDownloadBytes({
        request: request(4),
        transfer,
        onProgress: ({ sentBytes }) => {
          if (sentBytes > 0) downloads.cancel(transfer);
        },
      }),
    ).rejects.toBeInstanceOf(MediaCacheCancelledException);
    expect(transfer.cancelled).toBe(true);
  });
});

describe('MediaCacheRequest', () => {
  test('tryFromUrl accepts http(s) URLs only and derives a stable name', () => {
    expect(MediaCacheRequest.tryFromUrl({ url: null })).toBeNull();
    expect(MediaCacheRequest.tryFromUrl({ url: '  ' })).toBeNull();
    expect(MediaCacheRequest.tryFromUrl({ url: 'file:///tmp/a.png' })).toBeNull();
    expect(MediaCacheRequest.tryFromUrl({ url: 'not a url' })).toBeNull();

    const a = MediaCacheRequest.tryFromUrl({ url: 'https://x.test/path/My%20Photo.JPG' })!;
    const b = MediaCacheRequest.tryFromUrl({ url: 'https://x.test/path/My%20Photo.JPG' })!;
    const other = MediaCacheRequest.tryFromUrl({ url: 'https://x.test/path/My%20Photo.JPG', namespace: 'clipboard' })!;
    expect(a.cacheKey).toBe(b.cacheKey);
    expect(a.cacheKey).not.toBe(other.cacheKey);
    expect(a.cacheKey).toMatch(/^-?[0-9a-f]{15,16}$/);
    expect(a.cacheFilename).toBe(`My-Photo-${a.cacheKey}.jpg`);

    const fromMime = MediaCacheRequest.tryFromUrl({ url: 'https://x.test/blob', mimeType: 'image/webp' })!;
    expect(fromMime.cacheFilename).toBe(`blob-${fromMime.cacheKey}.webp`);
  });
});
