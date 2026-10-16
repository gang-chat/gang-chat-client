import { HostPlatform } from '../config/host_platform';
import { delay, unawaited } from '../lib/async';
import type { Milliseconds } from '../lib/async';
import { ShutdownHooks } from '../lifecycle/shutdown_hooks';
import { hasPlatform, platform } from '../platform/platform';
import type { WindowBridge, WindowEvent } from '../platform/platform';

/** Port of `dart:ui` `Size` (logical pixels). */
export interface Size {
  readonly width: number;
  readonly height: number;
}

/** `Color(0xFF14171D)`: the native window background behind the first frame. */
export const appWindowBackground = '#14171D';

/**
 * Smallest window the authenticated desktop app accepts.
 *
 * PORT: the Flutter shell allowed 360×600 because desktop windows could fall
 * back to the compact phone-width layout. The desktop app keeps the wide
 * layout instead and is never narrower than this; only the web build still
 * adapts to narrow screens (phone browsers), see `usesNarrowHomeLayout`.
 */
export const appWindowMinSize: Size = { width: 960, height: 600 };
const _appWindowSize: Size = { width: 1180, height: 760 };
const _authWidgetWidth = 430.0;
const _authBottomBreathingRoom = 24.0;
const _authBrandTitleExtraHeight = 53.0;
const _loginWidgetHeight = 291.0 + _authBrandTitleExtraHeight + _authBottomBreathingRoom;
const _registerWidgetHeight = 359.0 + _authBrandTitleExtraHeight + _authBottomBreathingRoom;
const _loginWidgetSize: Size = { width: _authWidgetWidth, height: _loginWidgetHeight };
const _registerWidgetSize: Size = { width: _authWidgetWidth, height: _registerWidgetHeight };
const _loginWindowSize = _loginWidgetSize;
const _registerWindowSize = _registerWidgetSize;
// PORT: `_unboundedWindowSize` (100000×100000) and `_minimumWindowSize` (1×1)
// were window_manager min/max juggling around `setSize`; the host's
// `setLayout` applies the final constraints in one step.

/** Answers whether the app handled a close request itself (true = keep the window). */
export type AppCloseRequestHandler = () => Promise<boolean>;
export type AppTrayExitHandler = () => Promise<void>;

/**
 * Desktop window chrome, tray and shutdown, over `platform().window`.
 *
 * PORT notes (Flutter window_manager → Electron `WindowBridge`):
 * - The host creates the BrowserWindow hidden, frameless (hidden title bar,
 *   macOS traffic lights visible) with [appWindowBackground]; the Flutter
 *   `WindowOptions` / `waitUntilReadyToShow` step has no equivalent here.
 * - Title-bar style, maximizable and shadow calls are folded into
 *   `setLayout({kind: 'auth' | 'app'})`, which the host implements as the
 *   matching window_manager sequence (exit full screen / maximize, fix or free
 *   the size constraints, resize, optionally center). `kind: 'app'` with
 *   `restore: true` comes from [restoreAppWindow]; `restore: false` is the
 *   initial authenticated launch layout.
 * - The tray channel (`gang_chat/tray`) becomes `WindowBridge` calls and the
 *   `tray-open-requested` / `tray-exit-requested` window events; the host's
 *   `quit()` disposes the tray icon and the window.
 * - Flutter frame waits (`waitUntilFirstFrameRasterized`, `endOfFrame`) are
 *   no-ops; React commits synchronously before the awaited body resolves.
 * - [startDragging] is a no-op: Electron drags frameless windows through the
 *   `-webkit-app-region: drag` CSS region set by the window-controls view.
 */
export class DesktopWindowController {
  constructor(opts: { window?: WindowBridge | null } = {}) {
    this._window = opts.window !== undefined ? opts.window : hasPlatform() ? platform().window : null;
    this._unsubscribe = this._window?.onEvent((event) => this._handleWindowEvent(event)) ?? null;
  }

  private static readonly _shutdownBudget: Milliseconds = 1200;
  /** Port of the `Future.delayed(500ms)` in [runWithHiddenWindow]. */
  private static readonly _hiddenWindowSettleDelay: Milliseconds = 500;

  private readonly _window: WindowBridge | null;
  private readonly _unsubscribe: (() => void) | null;
  private _skipNextAuthWindowLock = false;
  private _trayInitialized = false;
  private _terminating = false;
  private _closeListenerInstalled = false;
  private _handlingClose = false;
  private _closeRequestHandler: AppCloseRequestHandler | null = null;
  private _trayExitHandler: AppTrayExitHandler | null = null;

  get supportsWindowManagement(): boolean {
    return this._window != null && !HostPlatform.isWeb && (HostPlatform.isMacOS || HostPlatform.isWindows || HostPlatform.isLinux);
  }

  get supportsNativeTray(): boolean {
    return this.supportsWindowManagement && HostPlatform.isWindows;
  }

  get supportsWindowsPowerRequests(): boolean {
    return this.supportsWindowManagement && HostPlatform.isWindows;
  }

  get supportsWindowsCaptureExclusion(): boolean {
    return this.supportsWindowManagement && HostPlatform.isWindows;
  }

  setCloseRequestHandler(handler: AppCloseRequestHandler | null): void {
    this._closeRequestHandler = handler;
  }

  setTrayExitHandler(handler: AppTrayExitHandler | null): void {
    this._trayExitHandler = handler;
  }

  authWidgetSize(registering: boolean, _opts: { showingError?: boolean } = {}): Size {
    return registering ? _registerWidgetSize : _loginWidgetSize;
  }

  async prepareForLaunch(opts: { authenticated: boolean }): Promise<void> {
    if (!this.supportsWindowManagement) return;

    this._skipNextAuthWindowLock = false;
    if (opts.authenticated) {
      await this._prepareAuthenticatedInitialWindow();
    } else {
      await this._prepareInitialWindow();
    }
    // window_manager `setPreventClose(true)` + `addListener`: from here on the
    // host routes native close requests to [_handleClose].
    this._closeListenerInstalled = true;
  }

  /** PORT: no Flutter raster thread; resolves immediately. */
  async waitUntilFirstFrameRasterized(): Promise<void> {}

  showInitialWindow(): Promise<void> {
    return this._configure(async (window) => {
      if (HostPlatform.isMacOS) {
        await this._setWindowOpacity(window, 1);
      }
      // PORT: window_manager showed macOS windows immediately (hence the
      // opacity trick alone); the Electron window starts hidden everywhere.
      await window.show();
      await window.focus();
    });
  }

  /** PORT: Electron drags through `-webkit-app-region: drag`; nothing to do. */
  startDragging(): Promise<void> {
    return this._configure(async () => {});
  }

  minimizeWindow(): Promise<void> {
    return this._configure((window) => window.minimize());
  }

  toggleMaximizeWindow(): Promise<void> {
    return this._configure((window) => window.toggleMaximize());
  }

  async isMaximizedWindow(): Promise<boolean> {
    if (!this.supportsWindowManagement) return false;
    try {
      return await this._window!.isMaximized();
    } catch {
      return false;
    }
  }

  closeWindow(): Promise<void> {
    return this._configure((window) => window.close());
  }

  minimizeToTray(): Promise<void> {
    return this._configure(async (window) => {
      if (this.supportsNativeTray) {
        // The host creates the tray icon on first use, drops the taskbar
        // entry and hides; it throws when no tray icon can be created.
        try {
          await window.minimizeToTray();
          this._trayInitialized = true;
          return;
        } catch {
          // Fall back to an ordinary minimize, like `_ensureTrayIcon() == false`.
        }
      }
      await window.minimize();
    });
  }

  restoreHiddenAppWindow(): Promise<void> {
    return this._configure(async (window) => {
      await window.setSkipTaskbar?.(false);
      await window.show();
      await window.focus();
    });
  }

  /**
   * Requests the native Windows attention indicator without changing focus.
   *
   * The native implementation keeps the taskbar highlighted and the tray
   * icon blinking until the window becomes foreground.
   */
  requestMessageAttention(): Promise<void> {
    return this._configure(async (window) => {
      if (!this.supportsNativeTray) return;
      await window.requestAttention();
    });
  }

  /**
   * Prevents Windows from entering its idle display/system sleep path while
   * full-screen live media is actively being watched.
   *
   * Display power transitions can invalidate the texture device underneath a
   * long-running WebRTC renderer. The request is scoped to full-screen media
   * and is always released when that view closes.
   */
  setFullScreenMediaPlaybackActive(active: boolean): Promise<void> {
    return this._configure(async (window) => {
      if (!this.supportsWindowsPowerRequests) return;
      await window.setPreventSleep(active);
    });
  }

  /**
   * Excludes the Gang Chat top-level window from Windows screen capture.
   *
   * This is used only while previewing a local screen share full screen. A
   * near-1:1 preview of the captured window otherwise creates an unbounded
   * video feedback loop that can exhaust the renderer and encoder. Returning
   * false lets the caller safely refuse that preview on unsupported Windows
   * versions instead of entering an unstable recursive capture.
   */
  async setWindowCaptureExcluded(excluded: boolean): Promise<boolean> {
    if (!this.supportsWindowsCaptureExclusion) return false;
    try {
      return (await this._window!.setContentProtection(excluded)) ?? false;
    } catch {
      return false;
    }
  }

  hideAppWindowForExit(): Promise<void> {
    return this._configure((window) => window.hide());
  }

  async terminateApplication(): Promise<void> {
    if (this._terminating) return;
    this._terminating = true;

    try {
      if (this.supportsWindowManagement) {
        await this._window!.hide();
      }
    } catch {
      // ignore
    }

    try {
      await Promise.race([ShutdownHooks.runAll(), delay(DesktopWindowController._shutdownBudget)]);
    } catch {
      // Local process shutdown should not be blocked by cleanup errors.
    }

    await this._disposeTrayIcon();

    // PORT: `windowManager.destroy()` + `exit(0)` → the host's `quit()`, which
    // also tears down the tray icon.
    try {
      if (this.supportsWindowManagement) {
        await this._window!.quit();
      }
    } catch {
      // ignore
    }
  }

  lockAuthWindow(
    opts: { registering?: boolean; moveWindow?: boolean; centerWindow?: boolean; size?: Size | null } = {},
  ): Promise<void> {
    const { registering = false, centerWindow = false, size } = opts;
    // PORT: `moveWindow` only skipped window_manager's `setSize` when the
    // window already had the target size; `setLayout` resizes in place, so it
    // has no effect here.
    return this._configure(async (window) => {
      const targetSize = size ?? this._authWindowSize(registering);
      await window.setLayout({
        kind: 'auth',
        width: targetSize.width,
        height: targetSize.height,
        center: centerWindow,
      });
    });
  }

  restoreAppWindow(): Promise<void> {
    return this._configure((window) => window.setLayout(this._appLayout(true)));
  }

  async runWithHiddenWindow(body: () => Promise<void>): Promise<void> {
    if (!this.supportsWindowManagement) {
      await body();
      return;
    }
    await this._configure((window) => this._setWindowOpacity(window, 0));
    try {
      await body();
      // PORT: `WidgetsBinding.instance.endOfFrame` has no equivalent; the
      // settle delay below still gives the renderer time to lay out.
      await delay(DesktopWindowController._hiddenWindowSettleDelay);
    } finally {
      await this._configure((window) => this._setWindowOpacity(window, 1));
    }
  }

  consumeSkipNextAuthWindowLock(): boolean {
    if (!this._skipNextAuthWindowLock) return false;
    this._skipNextAuthWindowLock = false;
    return true;
  }

  /** Unsubscribes from window events (not in the Dart original; tests use it). */
  dispose(): void {
    this._unsubscribe?.();
  }

  private _prepareInitialWindow(): Promise<void> {
    return this._configure(async (window) => {
      await window.setLayout({
        kind: 'auth',
        width: _loginWindowSize.width,
        height: _loginWindowSize.height,
        center: true,
      });
      if (HostPlatform.isMacOS) {
        // macOS shows the window immediately; opaque-zero it until AuthGate is
        // ready so the pre-render frame stays hidden.
        await this._setWindowOpacity(window, 0);
      }
    });
  }

  /**
   * Initial window prep when we already know the user is logged in: skip the
   * auth-window lock and jump straight to the app window's sizing/resizable
   * state so the home screen doesn't visibly resize after the auth refresh
   * completes.
   */
  private _prepareAuthenticatedInitialWindow(): Promise<void> {
    return this._configure(async (window) => {
      await window.setLayout(this._appLayout(false));
      if (HostPlatform.isMacOS) {
        await this._setWindowOpacity(window, 0);
      }
    });
  }

  private _appLayout(restore: boolean) {
    return {
      kind: 'app' as const,
      restore,
      width: _appWindowSize.width,
      height: _appWindowSize.height,
      minWidth: this._appWindowMinSize.width,
      minHeight: this._appWindowMinSize.height,
    };
  }

  private _authWindowSize(registering: boolean): Size {
    return registering ? _registerWindowSize : _loginWindowSize;
  }

  private get _appWindowMinSize(): Size {
    return appWindowMinSize;
  }

  private async _configure(configure: (window: WindowBridge) => Promise<void>): Promise<void> {
    if (!this.supportsWindowManagement) return;
    try {
      await configure(this._window!);
    } catch {
      // Window chrome is best-effort.
    }
  }

  private async _setWindowOpacity(window: WindowBridge, opacity: number): Promise<void> {
    await window.setOpacity?.(opacity);
  }

  private async _dispatchCloseRequest(): Promise<boolean> {
    const handler = this._closeRequestHandler;
    if (handler == null) return false;
    try {
      return await handler();
    } catch {
      return false;
    }
  }

  private async _disposeTrayIcon(): Promise<void> {
    // PORT: the tray icon is owned by the host and removed by `quit()`.
    this._trayInitialized = false;
  }

  private _handleWindowEvent(event: WindowEvent): void {
    switch (event.type) {
      case 'close-requested':
        unawaited(this._handleClose());
        return;
      case 'tray-open-requested':
        unawaited(this.restoreHiddenAppWindow());
        return;
      case 'tray-exit-requested':
        unawaited(this._handleTrayExit());
        return;
      default:
        return;
    }
  }

  private async _handleTrayExit(): Promise<void> {
    const handler = this._trayExitHandler;
    if (handler != null) {
      await handler();
    } else {
      await this.terminateApplication();
    }
  }

  /** Port of `_AppWindowListener.onWindowClose`. */
  private async _handleClose(): Promise<void> {
    const window = this._window;
    if (window == null) return;
    if (!this._closeListenerInstalled) {
      // Before [prepareForLaunch] the window was not prevent-close yet.
      await window.respondToClose(true).catch(() => {});
      return;
    }
    if (this._handlingClose) {
      await window.respondToClose(false).catch(() => {});
      return;
    }
    this._handlingClose = true;
    try {
      if (await this._dispatchCloseRequest()) {
        await window.respondToClose(false).catch(() => {});
        return;
      }
      // Keep the window until the shutdown hooks ran; `quit()` ends the app.
      await window.respondToClose(false).catch(() => {});
      await this.terminateApplication();
    } finally {
      this._handlingClose = false;
    }
  }
}
