import { sparkleAppcastUrl } from '../app/app_update';
import { HostPlatform } from '../config/host_platform';
import { hasPlatform, platform } from '../platform/platform';
import type { UpdaterBridge } from '../platform/platform';

/**
 * macOS in-app updates through Sparkle.
 *
 * Sparkle downloads, verifies and installs updates with its own XPC
 * installer, outside the App Sandbox, so the installed app is never marked
 * "created by a sandboxed app". Builds without an update-signing key report
 * unavailable and keep the release bucket flow.
 *
 * PORT: the `gang_chat/updater` method channel (macos/Runner/AppDelegate.swift)
 * becomes `platform().updater`; the desktop host implements it (Sparkle via a
 * native addon, or leaves it null so the release-bucket flow is used).
 */
export class SparkleUpdater {
  constructor(opts: { updater?: UpdaterBridge | null } = {}) {
    this._updater = opts.updater;
  }

  /** `undefined` = resolve `platform().updater` lazily. */
  private readonly _updater: UpdaterBridge | null | undefined;

  private get _bridge(): UpdaterBridge | null {
    if (this._updater !== undefined) return this._updater;
    return hasPlatform() ? (platform().updater ?? null) : null;
  }

  async isAvailable(): Promise<boolean> {
    if (!HostPlatform.isMacOS) return false;
    const updater = this._bridge;
    if (updater == null) return false;
    try {
      return (await updater.isAvailable()) ?? false;
    } catch {
      return false;
    }
  }

  /**
   * A background check shows Sparkle's window only when an update exists; a
   * user-initiated check always reports back, including "up to date".
   */
  async checkForUpdates(opts: { bucketUrl: string; background?: boolean }): Promise<void> {
    const { bucketUrl, background = false } = opts;
    const updater = this._bridge;
    if (updater == null) {
      // Dart: MissingPluginException from the absent channel.
      throw new Error('Sparkle updater is not available on this platform.');
    }
    await updater.checkForUpdates({ feedUrl: sparkleAppcastUrl(bucketUrl), background });
  }
}
