import { useEffect, useState } from 'react';

import { TokenStore } from '@gang-chat/core/auth/token_store';
import { AppConfig } from '@gang-chat/core/config/app_config';
import { DesktopWindowController } from '@gang-chat/core/shell/desktop_window_controller';
import { LocalPreferencesMigration } from '@gang-chat/core/shell/local_preferences_migration';
import { FileDropTarget } from '@gang-chat/ui/shell/file_drop_target';
import { GangApp } from '@gang-chat/ui/shell/gang_app';

import type { BootSnapshot } from '../../shared/ipc';
import { desktopBridge } from './platform/desktop_bridge';

interface _LaunchResult {
  config: AppConfig;
  tokenStore: TokenStore;
  startsAuthenticated: boolean;
  windowController: DesktopWindowController;
}

let _launch: Promise<_LaunchResult> | null = null;

/**
 * Port of `main()` in lib/main.dart, up to `runApp`.
 *
 * Runs once per renderer (a module-level promise, so a remount does not
 * repeat the migration or the window preparation).
 *
 * PORT notes:
 * - `installWindowsAltKeyEventGuard()`, the orientation controller and the
 *   WebRTC audio initialization were Flutter engine/plugin concerns with no
 *   counterpart in the renderer.
 * - `_installConfiguredHostProxyBypass(config)` (DIRECT for the configured
 *   hosts) belongs to the main process, which owns all HTTP.
 * - The stored-session probe comes from the boot snapshot
 *   (`boot.hasStoredSession`, read by the main process before the window was
 *   created) instead of a renderer-side `tokenStore.readRefreshToken()`.
 */
function _prepareLaunch(boot: BootSnapshot): Promise<_LaunchResult> {
  _launch ??= (async () => {
    // Run before AppConfig, auth history, and audio settings read
    // SharedPreferences. This is a one-time, conflict-aware migration from the
    // historical Windows ProductName (`client`) to the stable Gang Chat domain.
    try {
      await new LocalPreferencesMigration().migrate();
    } catch {
      // Local migration is best-effort and must never block application launch.
    }

    const config = AppConfig.load(boot.appConfig, boot.configOverrides);

    // The stored refresh token sizes the initial window correctly (login vs
    // full app) before it ever renders. Without this, an already-logged-in
    // user briefly sees the small login-sized window before it grows to the
    // app size, which looks like a layout flicker.
    const tokenStore = new TokenStore();
    const startsAuthenticated = boot.hasStoredSession;
    const windowController = new DesktopWindowController();

    await windowController.prepareForLaunch({ authenticated: startsAuthenticated });
    return { config, tokenStore, startsAuthenticated, windowController };
  })();
  return _launch;
}

/**
 * The renderer root: `runApp(GangApp(...))`.
 *
 * GangApp itself provides AppConfigScope > AppNotificationHost > OverlayHost
 * (MaterialApp's builder and navigator overlay). [FileDropTarget] stands in
 * for the native runner's window-wide drop view, so it wraps the whole app.
 * The window is created hidden by the main process; it is shown by the auth
 * gate after it has decided which screen to render. That avoids both
 * login/app resize flicker and pre-restore home flashes.
 */
export function DesktopRoot() {
  const [launch, setLaunch] = useState<_LaunchResult | null>(null);

  useEffect(() => {
    let active = true;
    void _prepareLaunch(desktopBridge().boot).then((result) => {
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
