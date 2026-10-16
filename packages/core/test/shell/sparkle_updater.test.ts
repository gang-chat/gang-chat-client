import { describe, expect, it } from 'vitest';

import { SparkleUpdater } from '../../src/shell/sparkle_updater';

describe('SparkleUpdater', () => {
  it('is never available in the Electron client', async () => {
    expect(await new SparkleUpdater().isAvailable()).toBe(false);
  });

  it('refuses to check, so callers keep the in-app update flow', async () => {
    await expect(new SparkleUpdater().checkForUpdates({ bucketUrl: 'https://bucket.test' })).rejects.toThrow(
      'Sparkle updater is not available on this platform.',
    );
  });
});
