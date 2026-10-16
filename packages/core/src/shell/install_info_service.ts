import { gangChatClientInstallInfoFileName } from '../app/settings_about';
import { hasPlatform, platform } from '../platform/platform';

/**
 * Reads the install timestamp the Windows installer writes next to the
 * executable ([fileName]).
 *
 * PORT: the Flutter client read `<exe dir>/gang_chat_install_info.txt` with
 * `dart:io` (or asked the Android bridge). The renderer has no executable
 * path, so the host reads that same file and exposes it as
 * `platform().app.installedAt`; [fileName] is kept for API parity.
 */
export class InstallInfoService {
  constructor(opts: { fileName?: string } = {}) {
    this.fileName = opts.fileName ?? gangChatClientInstallInfoFileName;
  }

  readonly fileName: string;

  async readInstalledAt(): Promise<string | null> {
    try {
      if (!hasPlatform()) return null;
      return platform().app.installedAt ?? null;
    } catch {
      return null;
    }
  }
}
