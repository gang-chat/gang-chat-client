import { sparkleAppcastUrl } from '../app/app_update';
import { HostPlatform } from '../config/host_platform';
import { hasPlatform, platform } from '../platform/platform';

/**
 * Port of `lib/src/shell/sparkle_updater.dart`: macOS in-app updates through
 * Sparkle (`platform().sparkle`, `apps/desktop/src/main/sparkle.ts`).
 *
 * Sparkle downloads, verifies and installs updates itself, from the same
 * appcast and with the same signing key as the Flutter macOS app. Builds
 * without Sparkle (development runs, other hosts) report unavailable and keep
 * the release bucket flow.
 */
export class SparkleUpdater {
  async isAvailable(): Promise<boolean> {
    if (!HostPlatform.isMacOS) return false;
    const sparkle = hasPlatform() ? platform().sparkle : null;
    if (sparkle == null) return false;
    try {
      return await sparkle.isAvailable();
    } catch {
      return false;
    }
  }

  /**
   * A background check shows Sparkle's window only when an update exists; a
   * user-initiated check always reports back, including "up to date".
   */
  async checkForUpdates(opts: { bucketUrl: string; background?: boolean }): Promise<void> {
    const sparkle = hasPlatform() ? platform().sparkle : null;
    if (sparkle == null) throw new Error('Sparkle updater is not available on this platform.');
    await sparkle.checkForUpdates({ feedUrl: sparkleAppcastUrl(opts.bucketUrl), background: opts.background ?? false });
  }
}
