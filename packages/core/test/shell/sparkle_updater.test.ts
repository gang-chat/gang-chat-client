import { describe, expect, it } from 'vitest';

import type { SparkleBridge } from '../../src/platform/platform';
import { SparkleUpdater } from '../../src/shell/sparkle_updater';
import { installFakePlatform } from '../support/fake_platform';

function recordingSparkle(available = true): SparkleBridge & { checks: Array<{ feedUrl: string | null; background: boolean }> } {
  const checks: Array<{ feedUrl: string | null; background: boolean }> = [];
  return {
    checks,
    isAvailable: async () => available,
    checkForUpdates: async (opts) => {
      checks.push(opts);
    },
  };
}

describe('SparkleUpdater', () => {
  it('is available on macOS when the host has Sparkle', async () => {
    installFakePlatform({ os: 'macos', sparkle: recordingSparkle() });
    expect(await new SparkleUpdater().isAvailable()).toBe(true);
  });

  it('is unavailable without the bridge, on other platforms, or when Sparkle is not configured', async () => {
    installFakePlatform({ os: 'macos' });
    expect(await new SparkleUpdater().isAvailable()).toBe(false);
    installFakePlatform({ os: 'windows', sparkle: recordingSparkle() });
    expect(await new SparkleUpdater().isAvailable()).toBe(false);
    installFakePlatform({ os: 'macos', sparkle: recordingSparkle(false) });
    expect(await new SparkleUpdater().isAvailable()).toBe(false);
  });

  it('checks the release bucket appcast', async () => {
    const sparkle = recordingSparkle();
    installFakePlatform({ os: 'macos', sparkle });
    await new SparkleUpdater().checkForUpdates({ bucketUrl: 'https://bucket.test/gang-chat', background: true });
    await new SparkleUpdater().checkForUpdates({ bucketUrl: 'https://bucket.test/gang-chat' });
    expect(sparkle.checks).toEqual([
      { feedUrl: 'https://bucket.test/gang-chat/releases/appcast.xml', background: true },
      { feedUrl: 'https://bucket.test/gang-chat/releases/appcast.xml', background: false },
    ]);
  });

  it('refuses to check without Sparkle, so callers keep the in-app update flow', async () => {
    installFakePlatform({ os: 'macos' });
    await expect(new SparkleUpdater().checkForUpdates({ bucketUrl: 'https://bucket.test' })).rejects.toThrow(
      'Sparkle updater is not available on this platform.',
    );
  });
});
