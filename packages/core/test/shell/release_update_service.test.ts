import { beforeEach, expect, test } from 'vitest';
import { AppUpdatePlatform, AvailableAppUpdate, ReleaseAsset } from '../../src/app/app_update';
import type { HttpClient, HttpRequest, StreamedResponse } from '../../src/platform/http';
import {
  ProcessException,
  ReleaseDownloadCancellationToken,
  ReleaseDownloadCancelledException,
  ReleaseInstallerException,
  ReleaseUpdateService,
  appUpdatePlatformForOperatingSystem,
} from '../../src/shell/release_update_service';
import { ShutdownHooks } from '../../src/lifecycle/shutdown_hooks';
import type { NativeUpdateInfo, UpdaterBridge } from '../../src/platform/platform';
import { MockClient, mockBytesResponse, mockResponse } from '../support/fake_http_client';
import { installFakePlatform } from '../support/fake_platform';
import type { FakePlatform } from '../support/fake_platform';

let fake: FakePlatform;

beforeEach(() => {
  fake = installFakePlatform({ os: 'windows' });
});

function update(key: string, platform: AppUpdatePlatform): AvailableAppUpdate {
  return new AvailableAppUpdate({
    currentVersion: '0.5.0',
    latestVersion: '0.5.1',
    asset: new ReleaseAsset({ key, version: '0.5.1', platform }),
    downloadUrl: `https://os.example.test/gang-chat/${key}`,
  });
}

class ChunkedDownloadClient implements HttpClient {
  constructor(private readonly chunks: string[]) {}

  async send(request: HttpRequest): Promise<StreamedResponse> {
    const chunks = this.chunks;
    const signal = request.signal;
    async function* stream(): AsyncIterable<Uint8Array> {
      for (const chunk of chunks) {
        if (signal?.aborted) throw new Error('aborted');
        yield new TextEncoder().encode(chunk);
      }
    }
    return { statusCode: 200, headers: {}, contentLength: null, stream: stream() };
  }
}

test('operating system mapping includes Android without changing desktop', () => {
  expect(appUpdatePlatformForOperatingSystem('windows')).toBe(AppUpdatePlatform.windows);
  expect(appUpdatePlatformForOperatingSystem('macos')).toBe(AppUpdatePlatform.macos);
  expect(appUpdatePlatformForOperatingSystem('android')).toBe(AppUpdatePlatform.android);
  expect(appUpdatePlatformForOperatingSystem('linux')).toBeNull();
});

test('checkForUpdate returns platform-specific latest release', async () => {
  const service = new ReleaseUpdateService({
    httpClient: new MockClient((request) => {
      expect(request.method).toBe('GET');
      expect(request.queryParameters['list-type']).toBe('2');
      expect(request.queryParameters['prefix']).toBe('releases/');
      return mockResponse(
        `
          <ListBucketResult>
            <Contents><Key>releases/GangChat_v0.5.0.exe</Key></Contents>
            <Contents>
              <Key>releases/GangChat_v0.5.1.exe</Key>
              <LastModified>2026-07-08T02:03:04.000Z</LastModified>
            </Contents>
            <Contents><Key>releases/GangChat_v0.5.1.dmg</Key></Contents>
          </ListBucketResult>
        `,
        200,
      );
    }),
  });

  const result = await service.checkForUpdate({
    bucketUrl: 'https://os.example.test/gang-chat',
    currentVersion: '0.5.0',
    platform: AppUpdatePlatform.windows,
  });

  expect(result?.latestVersion).toBe('0.5.1');
  expect(result?.asset.releasedAt?.getTime()).toBe(Date.UTC(2026, 6, 8, 2, 3, 4));
  expect(result?.downloadUrl).toBe('https://os.example.test/gang-chat/releases/GangChat_v0.5.1.exe');
});

test('checkForUpdate returns null when current version is latest', async () => {
  const service = new ReleaseUpdateService({
    httpClient: new MockClient(() =>
      mockResponse('<ListBucketResult><Contents><Key>releases/GangChat_v0.5.1.exe</Key></Contents></ListBucketResult>', 200),
    ),
  });

  const result = await service.checkForUpdate({
    bucketUrl: 'https://os.example.test/gang-chat',
    currentVersion: '0.5.1',
    platform: AppUpdatePlatform.windows,
  });

  expect(result).toBeNull();
});

test('checkForUpdate surfaces listing failures', async () => {
  const service = new ReleaseUpdateService({ httpClient: new MockClient(() => mockResponse('', 503)) });
  await expect(
    service.checkForUpdate({ bucketUrl: 'https://os.example.test/b', currentVersion: '0.5.0', platform: AppUpdatePlatform.windows }),
  ).rejects.toThrow('读取版本列表失败（状态码 503）');
});

test('downloadUpdate removes old GangChat installers from temp first', async () => {
  const files = fake.files;
  files.put('/tmp/GangChat_v0.5.0.exe', 'old exe');
  files.put('/tmp/GangChat_v0.4.9.dmg', 'old dmg');
  files.put('/tmp/GangChat_notes.exe', 'keep me');
  files.put('/tmp/GangChat_v0.5.0.apk', 'keep Android separate');
  const progress: Array<[number, number | null]> = [];

  const service = new ReleaseUpdateService({
    httpClient: new MockClient(() => mockResponse('new installer', 200)),
    temporaryDirectoryProvider: async () => '/tmp',
  });

  const file = await service.downloadUpdate(update('releases/GangChat_v0.5.1.exe', AppUpdatePlatform.windows), {
    onProgress: ({ receivedBytes, totalBytes }) => progress.push([receivedBytes, totalBytes]),
  });

  expect(files.files.has('/tmp/GangChat_v0.5.0.exe')).toBe(false);
  expect(files.files.has('/tmp/GangChat_v0.4.9.dmg')).toBe(false);
  expect(files.files.has('/tmp/GangChat_notes.exe')).toBe(true);
  expect(files.files.has('/tmp/GangChat_v0.5.0.apk')).toBe(true);
  expect(file.endsWith('GangChat_v0.5.1.exe')).toBe(true);
  expect(files.text(file)).toBe('new installer');
  expect(progress).toEqual([
    [0, 13],
    [13, 13],
  ]);
});

test('downloadUpdate defaults to the platform temporary directory', async () => {
  const service = new ReleaseUpdateService({ httpClient: new MockClient(() => mockResponse('x', 200)) });
  expect(await service.downloadUpdate(update('releases/GangChat_v0.5.1.exe', AppUpdatePlatform.windows))).toBe(
    '/tmp/GangChat_v0.5.1.exe',
  );
});

test('downloadUpdate keeps Android APKs in the private release directory', async () => {
  const files = fake.files;
  files.put('/tmp/release-updates/GangChat_v0.5.0.apk', [0x50, 0x4b, 0x03, 0x04]);
  files.put('/tmp/release-updates/keep.txt', 'keep me');

  const service = new ReleaseUpdateService({
    httpClient: new MockClient(() => mockBytesResponse([0x50, 0x4b, 0x03, 0x04, 0x14, 0x00], 200)),
    temporaryDirectoryProvider: async () => '/tmp',
  });
  const file = await service.downloadUpdate(update('releases/GangChat_v0.5.1.apk', AppUpdatePlatform.android));

  expect(files.files.has('/tmp/release-updates/GangChat_v0.5.0.apk')).toBe(false);
  expect(files.files.has('/tmp/release-updates/keep.txt')).toBe(true);
  expect(file).toBe('/tmp/release-updates/GangChat_v0.5.1.apk');
  expect([...(await files.readFile(file))]).toEqual([0x50, 0x4b, 0x03, 0x04, 0x14, 0x00]);
});

test('downloadUpdate deletes an invalid Android APK response', async () => {
  const service = new ReleaseUpdateService({
    httpClient: new MockClient(() => mockResponse('not an apk', 200)),
    temporaryDirectoryProvider: async () => '/tmp',
  });

  await expect(service.downloadUpdate(update('releases/GangChat_v0.5.1.apk', AppUpdatePlatform.android))).rejects.toMatchObject({
    name: 'FormatException',
  });
  expect(fake.files.files.has('/tmp/release-updates/GangChat_v0.5.1.apk')).toBe(false);
});

test('downloadUpdate cancellation deletes partial installer', async () => {
  const token = new ReleaseDownloadCancellationToken();
  const service = new ReleaseUpdateService({
    httpClient: new ChunkedDownloadClient(['partial ', 'installer']),
    temporaryDirectoryProvider: async () => '/tmp',
  });

  await expect(
    service.downloadUpdate(update('releases/GangChat_v0.5.1.exe', AppUpdatePlatform.windows), {
      cancellationToken: token,
      onProgress: ({ receivedBytes }) => {
        if (receivedBytes > 0) token.cancel();
      },
    }),
  ).rejects.toBeInstanceOf(ReleaseDownloadCancelledException);

  expect(fake.files.files.has('/tmp/GangChat_v0.5.1.exe')).toBe(false);
  await token.cancelAndDeletePartialFile();
});

test('downloadUpdate surfaces a failed download status', async () => {
  const service = new ReleaseUpdateService({
    httpClient: new MockClient(() => mockResponse('', 404)),
    temporaryDirectoryProvider: async () => '/tmp',
  });
  await expect(service.downloadUpdate(update('releases/GangChat_v0.5.1.exe', AppUpdatePlatform.windows))).rejects.toThrow(
    '版本下载失败（状态码 404）',
  );
});

test('startInstaller launches Windows installer through UAC shell', async () => {
  let executable: string | null = null;
  let args: string[] | null = null;
  const service = new ReleaseUpdateService({
    processRunner: async (process, processArgs) => {
      executable = process;
      args = processArgs;
      return { exitCode: 0, stdout: '', stderr: '' };
    },
  });

  await service.startInstaller("C:\\Users\\me\\AppData\\Local\\Temp\\Gang Chat's_v0.5.1.exe");

  expect(executable).toBe('powershell.exe');
  expect(args!.slice(0, 5)).toEqual(['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-Command']);
  const command = args!.at(-1)!;
  expect(command).toContain('Start-Process -FilePath');
  expect(command).toContain('-Verb RunAs');
  expect(command).toContain("'C:\\Users\\me\\AppData\\Local\\Temp\\Gang Chat''s_v0.5.1.exe'");
});

test('startInstaller uses platform().process by default', async () => {
  await new ReleaseUpdateService().startInstaller('C:\\Temp\\GangChat_v0.5.1.exe');
  expect(fake.process!.runs[0]!.executable).toBe('powershell.exe');

  await new ReleaseUpdateService().startInstaller('/tmp/GangChat_v0.5.1.dmg', { platform: AppUpdatePlatform.macos });
  expect(fake.process!.spawned).toEqual([{ executable: 'open', args: ['/tmp/GangChat_v0.5.1.dmg'] }]);
});

test('startInstaller surfaces failed Windows launcher', async () => {
  const service = new ReleaseUpdateService({
    processRunner: async () => ({ exitCode: 1, stdout: '', stderr: 'operation canceled' }),
  });

  const error = await service.startInstaller('C:\\Temp\\GangChat_v0.5.1.exe').catch((e: unknown) => e);
  expect(error).toBeInstanceOf(ProcessException);
  expect((error as ProcessException).errorCode).toBe(1);
  expect((error as ProcessException).message).toContain('operation canceled');
});

test('startInstaller delegates Android APKs to the native installer', async () => {
  let launchedPath: string | null = null;
  const service = new ReleaseUpdateService({
    androidInstallerLauncher: async (path) => {
      launchedPath = path;
    },
  });

  await service.startInstaller('/tmp/GangChat_v0.5.1.apk', { platform: AppUpdatePlatform.android });

  expect(launchedPath).toBe('/tmp/GangChat_v0.5.1.apk');
});

test('startInstaller localizes Android signature mismatch', async () => {
  const service = new ReleaseUpdateService({
    androidInstallerLauncher: async () => {
      throw Object.assign(new Error('PlatformException'), { code: 'signature_mismatch' });
    },
  });

  const error = await service
    .startInstaller('GangChat_v0.5.1.apk', { platform: AppUpdatePlatform.android })
    .catch((e: unknown) => e);
  expect(error).toBeInstanceOf(ReleaseInstallerException);
  expect((error as ReleaseInstallerException).message).toContain('签名');
});

test('startInstaller rejects non-APK files on Android and unsupported platforms', async () => {
  await expect(
    new ReleaseUpdateService().startInstaller('/tmp/x.exe', { platform: AppUpdatePlatform.android }),
  ).rejects.toThrow('下载的文件不是 Android 安装包');
  installFakePlatform({ os: 'linux' });
  await expect(new ReleaseUpdateService().startInstaller('/tmp/x')).rejects.toThrow(
    'Installing updates is not supported on this platform.',
  );
});

// ---------------------------------------------------------------------------
// Native updater (electron-updater) path.


class FakeUpdater implements UpdaterBridge {
  available = true;
  info: NativeUpdateInfo | null = { version: '0.6.0', releaseDate: '2026-10-01T08:00:00.000Z', releaseNotes: '修复' };
  installs = 0;
  events: string[] = [];
  downloadGate: Promise<void> = Promise.resolve();

  async isAvailable() {
    return this.available;
  }
  async checkForUpdate() {
    return this.info;
  }
  async downloadUpdate(options: {
    onProgress?: (p: { receivedBytes: number; totalBytes: number | null }) => void;
    signal?: AbortSignal;
  }) {
    options.onProgress?.({ receivedBytes: 50, totalBytes: 100 });
    await this.downloadGate;
    if (options.signal?.aborted) {
      const error = new Error('aborted');
      error.name = 'AbortError';
      throw error;
    }
    options.onProgress?.({ receivedBytes: 100, totalBytes: 100 });
  }
  async quitAndInstall() {
    this.events.push('install');
    this.installs += 1;
  }
}

test('checkForUpdate uses the native updater feed when it is available', async () => {
  const updater = new FakeUpdater();
  const client = new MockClient(async () => {
    throw new Error('the bucket listing must not be read');
  });
  const service = new ReleaseUpdateService({ httpClient: client, updater });

  const found = await service.checkForUpdate({
    bucketUrl: 'https://os.example.test/gang-chat',
    currentVersion: '0.5.0',
    platform: AppUpdatePlatform.windows,
  });

  expect(found?.latestVersion).toBe('0.6.0');
  expect(found?.asset.key).toBe('releases/GangChat_v0.6.0.exe');
  expect(found?.asset.releasedAt?.toISOString()).toBe('2026-10-01T08:00:00.000Z');
  expect(found?.releaseNotes).toBe('修复');
  expect(service.isNativeUpdate(found!)).toBe(true);
});

test('checkForUpdate reports no update when the native feed is not newer', async () => {
  const updater = new FakeUpdater();
  updater.info = { version: '0.5.0', releaseDate: null, releaseNotes: null };
  const service = new ReleaseUpdateService({ updater });

  expect(
    await service.checkForUpdate({ bucketUrl: 'https://os.example.test/gang-chat', currentVersion: '0.5.0', platform: AppUpdatePlatform.macos }),
  ).toBeNull();
});

test('an unavailable native updater falls back to the bucket listing', async () => {
  const updater = new FakeUpdater();
  updater.available = false;
  let listed = false;
  const client = new MockClient(async () => {
    listed = true;
    return mockResponse('<ListBucketResult><Contents><Key>releases/GangChat_v0.5.2.exe</Key></Contents></ListBucketResult>', 200);
  });
  const service = new ReleaseUpdateService({ httpClient: client, updater });

  const found = await service.checkForUpdate({
    bucketUrl: 'https://os.example.test/gang-chat',
    currentVersion: '0.5.0',
    platform: AppUpdatePlatform.windows,
  });

  expect(listed).toBe(true);
  expect(found?.latestVersion).toBe('0.5.2');
  expect(service.isNativeUpdate(found!)).toBe(false);
});

test('a feed without latest.yml falls back to the bucket listing', async () => {
  const updater = new FakeUpdater();
  updater.checkForUpdate = async () => {
    throw new Error('Cannot find latest.yml in the latest release artifacts');
  };
  const client = new MockClient(async () =>
    mockResponse('<ListBucketResult><Contents><Key>releases/GangChat_v0.5.2.exe</Key></Contents></ListBucketResult>', 200),
  );
  const service = new ReleaseUpdateService({ httpClient: client, updater });

  const found = await service.checkForUpdate({
    bucketUrl: 'https://os.example.test/gang-chat',
    currentVersion: '0.5.0',
    platform: AppUpdatePlatform.windows,
  });

  expect(found?.latestVersion).toBe('0.5.2');
  expect(found?.asset.key).toBe('releases/GangChat_v0.5.2.exe');
  expect(service.isNativeUpdate(found!)).toBe(false);
});

test('native download reports progress and installing quits through the updater after shutdown hooks', async () => {
  const updater = new FakeUpdater();
  const service = new ReleaseUpdateService({ updater });
  const found = (await service.checkForUpdate({
    bucketUrl: 'https://os.example.test/gang-chat',
    currentVersion: '0.5.0',
    platform: AppUpdatePlatform.windows,
  }))!;
  const progress: Array<[number, number | null]> = [];

  const file = await service.downloadUpdate(found, {
    onProgress: ({ receivedBytes, totalBytes }) => progress.push([receivedBytes, totalBytes]),
  });
  const token = ShutdownHooks.register(async () => {
    updater.events.push('hooks');
  });
  try {
    await service.startInstaller(file, { platform: AppUpdatePlatform.windows });
  } finally {
    ShutdownHooks.unregister(token);
  }

  expect(progress).toEqual([
    [0, null],
    [50, 100],
    [100, 100],
  ]);
  expect(updater.events).toEqual(['hooks', 'install']);
});

test('cancelling a native download raises ReleaseDownloadCancelledException', async () => {
  const updater = new FakeUpdater();
  let release!: () => void;
  updater.downloadGate = new Promise<void>((resolve) => {
    release = resolve;
  });
  const service = new ReleaseUpdateService({ updater });
  const found = (await service.checkForUpdate({
    bucketUrl: 'https://os.example.test/gang-chat',
    currentVersion: '0.5.0',
    platform: AppUpdatePlatform.macos,
  }))!;
  const token = new ReleaseDownloadCancellationToken();

  const download = service.downloadUpdate(found, { cancellationToken: token });
  token.cancel();
  release();

  await expect(download).rejects.toBeInstanceOf(ReleaseDownloadCancelledException);
  expect(updater.installs).toBe(0);
});
