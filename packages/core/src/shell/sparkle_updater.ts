import { sparkleAppcastUrl } from '../app/app_update';

/**
 * macOS in-app updates through Sparkle in the Flutter client.
 *
 * PORT: the Electron app has no Sparkle. Its native updater
 * (`platform().updater`, electron-updater on top of Electron's autoUpdater)
 * serves Windows and macOS alike through `ReleaseUpdateService`, so this
 * class always reports unavailable and callers take the regular in-app
 * download / install path. The API is kept so the update gate reads like the
 * Dart original.
 */
export class SparkleUpdater {
  async isAvailable(): Promise<boolean> {
    return false;
  }

  async checkForUpdates(opts: { bucketUrl: string; background?: boolean }): Promise<void> {
    void sparkleAppcastUrl(opts.bucketUrl);
    throw new Error('Sparkle updater is not available on this platform.');
  }
}
