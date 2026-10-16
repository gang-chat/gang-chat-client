import { expect, test } from 'vitest';
import { SparkleUpdater } from '../../src/shell/sparkle_updater';
import { FakeUpdater, installFakePlatform } from '../support/fake_platform';

test('is unavailable when the native updater is missing', async () => {
  installFakePlatform({ os: 'macos', updater: null });
  expect(await new SparkleUpdater().isAvailable()).toBe(false);
});

test('is unavailable when the native side reports an error', async () => {
  const updater = new FakeUpdater();
  updater.failAvailability = Object.assign(new Error('updater_unavailable'), { code: 'updater_unavailable' });
  installFakePlatform({ os: 'macos', updater });
  expect(await new SparkleUpdater().isAvailable()).toBe(false);
});

test('reports the native answer on macOS only', async () => {
  const updater = new FakeUpdater();
  installFakePlatform({ os: 'macos', updater });
  expect(await new SparkleUpdater().isAvailable()).toBe(true);

  installFakePlatform({ os: 'windows', updater });
  expect(await new SparkleUpdater().isAvailable()).toBe(false);
});

test('checks the appcast next to the configured installers', async () => {
  const updater = new FakeUpdater();
  installFakePlatform({ os: 'macos', updater });
  await new SparkleUpdater().checkForUpdates({ bucketUrl: 'https://os.ky-z.com:9000/gang-chat/', background: true });
  expect(updater.checks).toEqual([
    { feedUrl: 'https://os.ky-z.com:9000/gang-chat/releases/appcast.xml', background: true },
  ]);
});
