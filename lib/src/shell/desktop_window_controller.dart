import 'dart:async';
import 'dart:io' show Platform, exit;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:window_manager/window_manager.dart';

import '../lifecycle/shutdown_hooks.dart';

const appWindowBackground = Color(0xFF14171D);

// The authenticated desktop shell can use the same compact navigation as a
// phone, so its minimum width should match that layout instead of the old
// desktop-only floor.
const _responsiveAppWindowMinSize = Size(360, 600);
const _appWindowSize = Size(1180, 760);
const _unboundedWindowSize = Size(100000, 100000);
const _minimumWindowSize = Size(1, 1);
const _authWidgetWidth = 430.0;
const _authBottomBreathingRoom = 24.0;
const _authBrandTitleExtraHeight = 53.0;
const _loginWidgetHeight =
    291.0 + _authBrandTitleExtraHeight + _authBottomBreathingRoom;
const _registerWidgetHeight =
    359.0 + _authBrandTitleExtraHeight + _authBottomBreathingRoom;
const _loginWidgetSize = Size(_authWidgetWidth, _loginWidgetHeight);
const _registerWidgetSize = Size(_authWidgetWidth, _registerWidgetHeight);
const _loginWindowSize = _loginWidgetSize;
const _registerWindowSize = _registerWidgetSize;

typedef AppCloseRequestHandler = Future<bool> Function();
typedef AppTrayExitHandler = Future<void> Function();

class DesktopWindowController {
  DesktopWindowController({MethodChannel? trayChannel})
    : _trayChannel = trayChannel ?? const MethodChannel('gang_chat/tray') {
    _trayChannel.setMethodCallHandler(_handleTrayMethod);
  }

  static const _shutdownBudget = Duration(milliseconds: 1200);

  final MethodChannel _trayChannel;
  bool _skipNextAuthWindowLock = false;
  bool _trayInitialized = false;
  bool _terminating = false;
  AppCloseRequestHandler? _closeRequestHandler;
  AppTrayExitHandler? _trayExitHandler;

  bool get supportsWindowManagement =>
      !kIsWeb &&
      !Platform.environment.containsKey('FLUTTER_TEST') &&
      (Platform.isMacOS || Platform.isWindows || Platform.isLinux);

  bool get supportsNativeTray => supportsWindowManagement && Platform.isWindows;

  bool get supportsWindowsPowerRequests =>
      supportsWindowManagement && Platform.isWindows;

  bool get supportsWindowsCaptureExclusion =>
      supportsWindowManagement && Platform.isWindows;

  void setCloseRequestHandler(AppCloseRequestHandler? handler) {
    _closeRequestHandler = handler;
  }

  void setTrayExitHandler(AppTrayExitHandler? handler) {
    _trayExitHandler = handler;
  }

  Size authWidgetSize(bool registering, {bool showingError = false}) {
    return registering ? _registerWidgetSize : _loginWidgetSize;
  }

  Future<void> prepareForLaunch({required bool authenticated}) async {
    if (!supportsWindowManagement) return;

    await windowManager.ensureInitialized();
    _skipNextAuthWindowLock = false;
    await windowManager.waitUntilReadyToShow(
      _initialWindowOptions(authenticated: authenticated),
      () async {},
    );
    if (authenticated) {
      await _prepareAuthenticatedInitialWindow();
    } else {
      await _prepareInitialWindow();
    }
    await windowManager.setPreventClose(true);
    windowManager.addListener(_AppWindowListener(this));
  }

  Future<void> waitUntilFirstFrameRasterized(WidgetsBinding binding) async {
    if (!supportsWindowManagement) return;
    await binding.waitUntilFirstFrameRasterized;
  }

  Future<void> showInitialWindow() {
    return _configure(() async {
      if (Platform.isMacOS) {
        await windowManager.setOpacity(1);
        return;
      }
      await windowManager.show();
      await windowManager.focus();
    });
  }

  Future<void> startDragging() {
    return _configure(() => windowManager.startDragging());
  }

  Future<void> minimizeWindow() {
    return _configure(() => windowManager.minimize());
  }

  Future<void> toggleMaximizeWindow() {
    return _configure(() async {
      if (await windowManager.isMaximized()) {
        await windowManager.unmaximize();
      } else {
        await windowManager.maximize();
      }
    });
  }

  Future<bool> isMaximizedWindow() async {
    if (!supportsWindowManagement) return false;
    try {
      return await windowManager.isMaximized();
    } catch (_) {
      return false;
    }
  }

  Future<void> closeWindow() {
    return _configure(() => windowManager.close());
  }

  Future<void> minimizeToTray() {
    return _configure(() async {
      if (supportsNativeTray && await _ensureTrayIcon()) {
        await windowManager.setSkipTaskbar(true);
        await windowManager.hide();
        return;
      }
      await windowManager.minimize();
    });
  }

  Future<void> restoreHiddenAppWindow() {
    return _configure(() async {
      await windowManager.setSkipTaskbar(false);
      await windowManager.show();
      await windowManager.focus();
    });
  }

  /// Requests the native Windows attention indicator without changing focus.
  ///
  /// The native implementation keeps the taskbar highlighted and the tray
  /// icon blinking until the window becomes foreground. It must not be paired
  /// with synchronous work in a window-focus callback because that can
  /// interfere with Flutter's child-window input routing.
  Future<void> requestMessageAttention() {
    return _configure(() async {
      if (!supportsNativeTray) return;
      await _trayChannel.invokeMethod<void>('requestAttention');
    });
  }

  /// Prevents Windows from entering its idle display/system sleep path while
  /// full-screen live media is actively being watched.
  ///
  /// Display power transitions can invalidate the texture device underneath a
  /// long-running WebRTC renderer. The request is scoped to full-screen media
  /// and is always released when that view closes.
  Future<void> setFullScreenMediaPlaybackActive(bool active) {
    return _configure(() async {
      if (!supportsWindowsPowerRequests) return;
      await _trayChannel.invokeMethod<void>('setMediaPlaybackActive', active);
    });
  }

  /// Excludes the Gang Chat top-level window from Windows screen capture.
  ///
  /// This is used only while previewing a local screen share full screen. A
  /// near-1:1 preview of the captured window otherwise creates an unbounded
  /// video feedback loop that can exhaust the renderer and encoder. Returning
  /// false lets the caller safely refuse that preview on unsupported Windows
  /// versions instead of entering an unstable recursive capture.
  Future<bool> setWindowCaptureExcluded(bool excluded) async {
    if (!supportsWindowsCaptureExclusion) return false;
    try {
      return await _trayChannel.invokeMethod<bool>(
            'setWindowCaptureExcluded',
            excluded,
          ) ??
          false;
    } catch (_) {
      return false;
    }
  }

  Future<void> hideAppWindowForExit() {
    return _configure(() async {
      await windowManager.hide();
    });
  }

  Future<void> terminateApplication() async {
    if (_terminating) return;
    _terminating = true;

    try {
      if (supportsWindowManagement) {
        await windowManager.hide();
      }
    } catch (_) {}

    try {
      await Future.any([
        ShutdownHooks.runAll(),
        Future<void>.delayed(_shutdownBudget),
      ]);
    } catch (_) {
      // Local process shutdown should not be blocked by cleanup errors.
    }

    await _disposeTrayIcon();

    try {
      if (supportsWindowManagement) {
        await windowManager.destroy();
      }
    } catch (_) {}
    exit(0);
  }

  Future<void> lockAuthWindow({
    bool registering = false,
    bool moveWindow = true,
    bool centerWindow = false,
    Size? size,
  }) {
    return _configure(() async {
      await _setAuthTitleBar();
      final targetSize = size ?? _authWindowSize(registering);
      final alreadySized = await _isAuthWindowSized(targetSize);
      if (await windowManager.isFullScreen()) {
        await windowManager.setFullScreen(false);
      }
      if (await windowManager.isMaximized()) {
        await windowManager.unmaximize();
      }
      await _setWindowMaximizable(false);
      await _setWindowShadow(true);
      await windowManager.setMaximumSize(_unboundedWindowSize);
      await windowManager.setMinimumSize(_minimumWindowSize);
      if (moveWindow || !alreadySized) {
        await windowManager.setSize(targetSize);
      }
      await windowManager.setMinimumSize(targetSize);
      await windowManager.setMaximumSize(targetSize);
      await windowManager.setResizable(false);
      if (centerWindow) {
        await windowManager.setAlignment(Alignment.center);
      }
    });
  }

  Future<void> restoreAppWindow() {
    return _configure(() async {
      await _setAppTitleBar();
      await windowManager.setResizable(true);
      await _setWindowMaximizable(true);
      await _setWindowShadow(true);
      if (await windowManager.isFullScreen()) {
        await windowManager.setFullScreen(false);
      }
      if (await windowManager.isMaximized()) {
        await windowManager.unmaximize();
      }
      await windowManager.setMaximumSize(_unboundedWindowSize);
      await windowManager.setMinimumSize(_appWindowMinSize);
      await windowManager.setSize(_appWindowSize);
      await windowManager.setAlignment(Alignment.center);
    });
  }

  Future<void> runWithHiddenWindow(Future<void> Function() body) async {
    if (!supportsWindowManagement) {
      await body();
      return;
    }
    await _setWindowOpacity(0);
    try {
      await body();
      await WidgetsBinding.instance.endOfFrame;
      await Future<void>.delayed(const Duration(milliseconds: 500));
    } finally {
      await _setWindowOpacity(1);
    }
  }

  bool consumeSkipNextAuthWindowLock() {
    if (!_skipNextAuthWindowLock) return false;
    _skipNextAuthWindowLock = false;
    return true;
  }

  WindowOptions _initialWindowOptions({bool authenticated = false}) {
    if (Platform.isMacOS) {
      if (authenticated) {
        return const WindowOptions(
          backgroundColor: appWindowBackground,
          title: 'Gang Chat',
          titleBarStyle: TitleBarStyle.hidden,
          windowButtonVisibility: true,
          center: true,
        );
      }
      return const WindowOptions(
        size: _loginWindowSize,
        minimumSize: _loginWindowSize,
        maximumSize: _loginWindowSize,
        backgroundColor: appWindowBackground,
        title: 'Gang Chat',
        titleBarStyle: TitleBarStyle.hidden,
        windowButtonVisibility: true,
        center: true,
      );
    }
    if (authenticated) {
      return WindowOptions(
        size: _appWindowSize,
        minimumSize: _appWindowMinSize,
        backgroundColor: appWindowBackground,
        title: 'Gang Chat',
        titleBarStyle: TitleBarStyle.hidden,
        windowButtonVisibility: false,
        center: true,
      );
    }
    return const WindowOptions(
      size: _loginWindowSize,
      minimumSize: _loginWindowSize,
      maximumSize: _loginWindowSize,
      backgroundColor: appWindowBackground,
      title: 'Gang Chat',
      titleBarStyle: TitleBarStyle.hidden,
      windowButtonVisibility: false,
      center: true,
    );
  }

  Future<void> _prepareInitialWindow() {
    return _configure(() async {
      await _setAuthTitleBar();
      await _setWindowMaximizable(false);
      await _setWindowShadow(true);
      await windowManager.setResizable(false);
      await windowManager.setAlignment(Alignment.center);
      if (Platform.isMacOS) {
        // macOS shows the window immediately; opaque-zero it until AuthGate is
        // ready so the pre-render frame stays hidden.
        await windowManager.setOpacity(0);
      }
    });
  }

  /// Initial window prep when we already know the user is logged in: skip the
  /// auth-window lock and jump straight to the app window's sizing/resizable
  /// state so the home screen doesn't visibly resize after the auth refresh
  /// completes.
  Future<void> _prepareAuthenticatedInitialWindow() {
    return _configure(() async {
      await _setAppTitleBar();
      await windowManager.setResizable(true);
      await _setWindowMaximizable(true);
      await _setWindowShadow(true);
      await windowManager.setMaximumSize(_unboundedWindowSize);
      await windowManager.setMinimumSize(_appWindowMinSize);
      await windowManager.setSize(_appWindowSize);
      await windowManager.setAlignment(Alignment.center);
      if (Platform.isMacOS) {
        await windowManager.setOpacity(0);
      }
    });
  }

  Size _authWindowSize(bool registering) =>
      registering ? _registerWindowSize : _loginWindowSize;

  Size get _appWindowMinSize => _responsiveAppWindowMinSize;

  Future<void> _configure(Future<void> Function() configure) async {
    if (!supportsWindowManagement) return;
    try {
      await configure();
    } catch (_) {}
  }

  Future<void> _setWindowMaximizable(bool isMaximizable) async {
    try {
      await windowManager.setMaximizable(isMaximizable);
    } catch (_) {}
  }

  Future<void> _setWindowShadow(bool hasShadow) async {
    try {
      await windowManager.setHasShadow(hasShadow);
    } catch (_) {}
  }

  Future<void> _setAuthTitleBar() {
    return _setTitleBar(windowButtonVisibility: Platform.isMacOS);
  }

  Future<void> _setAppTitleBar() {
    return _setTitleBar(windowButtonVisibility: Platform.isMacOS);
  }

  Future<void> _setTitleBar({required bool windowButtonVisibility}) async {
    try {
      await windowManager.setTitleBarStyle(
        TitleBarStyle.hidden,
        windowButtonVisibility: windowButtonVisibility,
      );
    } catch (_) {}
  }

  Future<void> _setWindowOpacity(double opacity) {
    return _configure(() => windowManager.setOpacity(opacity));
  }

  Future<bool> _dispatchCloseRequest() async {
    final handler = _closeRequestHandler;
    if (handler == null) return false;
    try {
      return await handler();
    } catch (_) {
      return false;
    }
  }

  Future<bool> _ensureTrayIcon() async {
    if (_trayInitialized) return true;
    try {
      final initialized = await _trayChannel.invokeMethod<bool>('initialize');
      _trayInitialized = initialized ?? true;
      return _trayInitialized;
    } catch (_) {
      return false;
    }
  }

  Future<void> _disposeTrayIcon() async {
    if (!_trayInitialized) return;
    try {
      await _trayChannel.invokeMethod<void>('dispose');
    } catch (_) {}
    _trayInitialized = false;
  }

  Future<void> _handleTrayMethod(MethodCall call) async {
    switch (call.method) {
      case 'open':
        await restoreHiddenAppWindow();
        break;
      case 'exit':
        final handler = _trayExitHandler;
        if (handler != null) {
          await handler();
        } else {
          await terminateApplication();
        }
        break;
    }
  }

  Future<bool> _isAuthWindowSized(Size targetSize) async {
    final size = await windowManager.getSize();
    return (size.width - targetSize.width).abs() < 1 &&
        (size.height - targetSize.height).abs() < 1;
  }
}

class _AppWindowListener extends WindowListener {
  _AppWindowListener(this._controller);

  final DesktopWindowController _controller;
  bool _handlingClose = false;

  @override
  void onWindowClose() {
    unawaited(_handleClose());
  }

  Future<void> _handleClose() async {
    if (_handlingClose) return;
    _handlingClose = true;
    try {
      if (await _controller._dispatchCloseRequest()) return;
      await _controller.terminateApplication();
    } finally {
      _handlingClose = false;
    }
  }
}
