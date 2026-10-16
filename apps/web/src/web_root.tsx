import { useEffect, useState } from 'react';

import { TokenStore } from '@gang-chat/core/auth/token_store';
import { AppConfig } from '@gang-chat/core/config/app_config';
import { DesktopWindowController } from '@gang-chat/core/shell/desktop_window_controller';
import { FileDropTarget } from '@gang-chat/ui/shell/file_drop_target';
import { GangApp } from '@gang-chat/ui/shell/gang_app';

import appConfigJson from '../../../assets/config/app_config.json';

interface _LaunchResult {
  config: AppConfig;
  tokenStore: TokenStore;
  startsAuthenticated: boolean;
  windowController: DesktopWindowController;
}

let _launch: Promise<_LaunchResult> | null = null;

/**
 * Port of `main()` in lib/main.dart, up to `runApp`, as it ran on the web.
 *
 * PORT notes: `LocalPreferencesMigration` and the window preparation are
 * no-ops in a browser (Windows-only migration, no window), so they are
 * skipped; `AppConfig` comes from the bundled `assets/config/app_config.json`
 * plus the build-time overrides.
 */
function _prepareLaunch(): Promise<_LaunchResult> {
  _launch ??= (async () => {
    const config = AppConfig.load(appConfigJson as Record<string, unknown>, __GANG_CONFIG_OVERRIDES__);
    const tokenStore = new TokenStore();
    let startsAuthenticated = false;
    try {
      startsAuthenticated = (await tokenStore.readRefreshToken()) != null;
    } catch {
      startsAuthenticated = false;
    }
    // No window on the web: every call on it is a no-op.
    const windowController = new DesktopWindowController();
    return { config, tokenStore, startsAuthenticated, windowController };
  })();
  return _launch;
}

/**
 * The page root: `runApp(GangApp(...))`. [FileDropTarget] stands in for the
 * Flutter web bridge's page-wide `dragover` / `drop` listeners (it also stops
 * the browser from opening a dropped file).
 */
export function WebRoot() {
  const [launch, setLaunch] = useState<_LaunchResult | null>(null);

  useEffect(() => {
    let active = true;
    void _prepareLaunch().then((result) => {
      if (active) setLaunch(result);
    });
    return () => {
      active = false;
    };
  }, []);

  if (launch == null) return null;
  return (
    <FileDropTarget>
      <GangApp
        config={launch.config}
        tokenStore={launch.tokenStore}
        startsAuthenticated={launch.startsAuthenticated}
        windowController={launch.windowController}
      />
    </FileDropTarget>
  );
}
