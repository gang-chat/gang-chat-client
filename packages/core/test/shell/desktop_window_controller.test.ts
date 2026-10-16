import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { ShutdownHooks } from '../../src/lifecycle/shutdown_hooks';
import { DesktopWindowController, appWindowBackground } from '../../src/shell/desktop_window_controller';
import { FakeWindow, installFakePlatform } from '../support/fake_platform';

class SupportedDesktopWindowController extends DesktopWindowController {
  override get supportsWindowManagement(): boolean {
    return true;
  }

  override get supportsNativeTray(): boolean {
    return true;
  }

  override get supportsWindowsPowerRequests(): boolean {
    return true;
  }

  override get supportsWindowsCaptureExclusion(): boolean {
    return true;
  }
}

const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('DesktopWindowController (Dart parity)', () => {
  let window: FakeWindow;

  beforeEach(() => {
    // Linux: window management on, but none of the Windows-only features.
    window = new FakeWindow();
    installFakePlatform({ os: 'linux', window });
  });

  test('Windows message attention uses the native window bridge', async () => {
    const controller = new SupportedDesktopWindowController({ window });

    await controller.requestMessageAttention();

    expect(window.calls).toEqual([{ method: 'requestAttention', args: [] }]);
  });

  test('message attention ignores native failures', async () => {
    window.failures.add('requestAttention');
    const controller = new SupportedDesktopWindowController({ window });

    await expect(controller.requestMessageAttention()).resolves.toBeUndefined();
  });

  test('message attention stays disabled outside supported desktop', async () => {
    const controller = new DesktopWindowController({ window });

    await controller.requestMessageAttention();

    expect(window.calls).toEqual([]);
  });

  test('full-screen media brackets the Windows power request', async () => {
    const controller = new SupportedDesktopWindowController({ window });

    await controller.setFullScreenMediaPlaybackActive(true);
    await controller.setFullScreenMediaPlaybackActive(false);

    expect(window.calls).toEqual([
      { method: 'setPreventSleep', args: [true] },
      { method: 'setPreventSleep', args: [false] },
    ]);
  });

  test('full-screen media power request ignores failures', async () => {
    window.failures.add('setPreventSleep');
    const controller = new SupportedDesktopWindowController({ window });

    await expect(controller.setFullScreenMediaPlaybackActive(true)).resolves.toBeUndefined();
  });

  test('Windows capture exclusion uses the native window bridge', async () => {
    const controller = new SupportedDesktopWindowController({ window });

    expect(await controller.setWindowCaptureExcluded(true)).toBe(true);
    expect(await controller.setWindowCaptureExcluded(false)).toBe(true);

    expect(window.calls).toEqual([
      { method: 'setContentProtection', args: [true] },
      { method: 'setContentProtection', args: [false] },
    ]);
  });

  test('Windows capture exclusion reports native failure', async () => {
    window.failures.add('setContentProtection');
    const controller = new SupportedDesktopWindowController({ window });

    expect(await controller.setWindowCaptureExcluded(true)).toBe(false);
  });

  test('capture exclusion stays disabled outside supported Windows', async () => {
    const controller = new DesktopWindowController({ window });

    expect(await controller.setWindowCaptureExcluded(true)).toBe(false);
    expect(window.calls).toEqual([]);
  });
});

describe('DesktopWindowController (bridge mapping)', () => {
  let window: FakeWindow;

  beforeEach(() => {
    window = new FakeWindow();
    installFakePlatform({ os: 'windows', window });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  test('keeps the Flutter window background and auth sizes', () => {
    const controller = new DesktopWindowController();
    expect(appWindowBackground).toBe('#14171D');
    expect(controller.authWidgetSize(false)).toEqual({ width: 430, height: 368 });
    expect(controller.authWidgetSize(true)).toEqual({ width: 430, height: 436 });
  });

  test('without a window bridge nothing is supported', async () => {
    const controller = new DesktopWindowController({ window: null });
    expect(controller.supportsWindowManagement).toBe(false);
    let ran = false;
    await controller.runWithHiddenWindow(async () => {
      ran = true;
    });
    expect(ran).toBe(true);
    expect(await controller.isMaximizedWindow()).toBe(false);
  });

  test('launch layouts and auth window lock map to setLayout', async () => {
    const controller = new DesktopWindowController();

    await controller.prepareForLaunch({ authenticated: false });
    await controller.prepareForLaunch({ authenticated: true });
    await controller.lockAuthWindow({ registering: true, centerWindow: true });
    await controller.lockAuthWindow({ size: { width: 500, height: 400 } });
    await controller.restoreAppWindow();

    expect(window.calls.filter((call) => call.method === 'setLayout').map((call) => call.args[0])).toEqual([
      { kind: 'auth', width: 430, height: 368, center: true },
      { kind: 'app', restore: false, width: 1180, height: 760, minWidth: 360, minHeight: 600 },
      { kind: 'auth', width: 430, height: 436, center: true },
      { kind: 'auth', width: 500, height: 400, center: false },
      { kind: 'app', restore: true, width: 1180, height: 760, minWidth: 360, minHeight: 600 },
    ]);
  });

  test('macOS hides the pre-render frame with opacity until shown', async () => {
    installFakePlatform({ os: 'macos', window });
    const controller = new DesktopWindowController();

    await controller.prepareForLaunch({ authenticated: false });
    await controller.showInitialWindow();

    expect(window.methods).toEqual(['setLayout', 'setOpacity', 'setOpacity', 'show', 'focus']);
    expect(window.calls[1]!.args).toEqual([0]);
    expect(window.calls[2]!.args).toEqual([1]);
  });

  test('runWithHiddenWindow restores opacity even when the body throws', async () => {
    vi.useFakeTimers();
    const controller = new DesktopWindowController();

    const run = controller.runWithHiddenWindow(async () => {
      await window.setLayout({ kind: 'app', restore: true });
    });
    await vi.advanceTimersByTimeAsync(500);
    await run;
    expect(window.methods).toEqual(['setOpacity', 'setLayout', 'setOpacity']);

    window.calls.length = 0;
    await expect(
      controller.runWithHiddenWindow(async () => {
        throw new Error('boom');
      }),
    ).rejects.toThrow('boom');
    expect(window.calls).toEqual([
      { method: 'setOpacity', args: [0] },
      { method: 'setOpacity', args: [1] },
    ]);
  });

  test('minimizeToTray falls back to minimize when no tray can be created', async () => {
    const controller = new DesktopWindowController();
    await controller.minimizeToTray();
    expect(window.methods).toEqual(['minimizeToTray']);

    window.calls.length = 0;
    window.failures.add('minimizeToTray');
    await controller.minimizeToTray();
    expect(window.methods).toEqual(['minimizeToTray', 'minimize']);
  });

  test('tray open restores the hidden window', async () => {
    const controller = new DesktopWindowController();
    window.emit({ type: 'tray-open-requested' });
    await flush();
    expect(window.calls).toEqual([
      { method: 'setSkipTaskbar', args: [false] },
      { method: 'show', args: [] },
      { method: 'focus', args: [] },
    ]);
    controller.dispose();
    expect(window.listenerCount).toBe(0);
  });

  test('tray exit uses the exit handler when one is installed', async () => {
    const controller = new DesktopWindowController();
    let exits = 0;
    controller.setTrayExitHandler(async () => {
      exits += 1;
    });
    window.emit({ type: 'tray-exit-requested' });
    await flush();
    expect(exits).toBe(1);
    expect(window.methods).toEqual([]);
  });

  test('a handled close request keeps the window open', async () => {
    const controller = new DesktopWindowController();
    await controller.prepareForLaunch({ authenticated: true });
    window.calls.length = 0;
    controller.setCloseRequestHandler(async () => true);

    window.emit({ type: 'close-requested' });
    await flush();

    expect(window.calls).toEqual([{ method: 'respondToClose', args: [false] }]);
  });

  test('an unhandled close runs shutdown hooks and quits once', async () => {
    const controller = new DesktopWindowController();
    await controller.prepareForLaunch({ authenticated: true });
    window.calls.length = 0;
    const order: string[] = [];
    const token = ShutdownHooks.register(async () => {
      order.push(`hook after ${window.methods.join(',')}`);
    });
    controller.setCloseRequestHandler(async () => {
      throw new Error('handler failure counts as unhandled');
    });

    window.emit({ type: 'close-requested' });
    await flush();
    await flush();
    ShutdownHooks.unregister(token);

    expect(order).toEqual(['hook after respondToClose,hide']);
    expect(window.methods).toEqual(['respondToClose', 'hide', 'quit']);

    await controller.terminateApplication();
    expect(window.methods).toEqual(['respondToClose', 'hide', 'quit']);
  });

  test('close requests before launch prep are allowed through', async () => {
    new DesktopWindowController();
    window.emit({ type: 'close-requested' });
    await flush();
    expect(window.calls).toEqual([{ method: 'respondToClose', args: [true] }]);
  });

  test('consumeSkipNextAuthWindowLock is false after launch', async () => {
    const controller = new DesktopWindowController();
    await controller.prepareForLaunch({ authenticated: false });
    expect(controller.consumeSkipNextAuthWindowLock()).toBe(false);
  });
});
