import 'package:flutter/services.dart';

import '../app/app_update.dart';
import '../config/host_platform.dart';

/// macOS in-app updates through Sparkle (macos/Runner/AppDelegate.swift).
///
/// Sparkle downloads, verifies and installs updates with its own XPC
/// installer, outside the App Sandbox, so the installed app is never marked
/// "created by a sandboxed app". Builds without an update-signing key report
/// unavailable and keep the release bucket flow.
class SparkleUpdater {
  const SparkleUpdater({
    MethodChannel channel = const MethodChannel('gang_chat/updater'),
  }) : _channel = channel;

  final MethodChannel _channel;

  Future<bool> isAvailable() async {
    if (!HostPlatform.isMacOS) return false;
    try {
      return await _channel.invokeMethod<bool>('isAvailable') ?? false;
    } on MissingPluginException {
      return false;
    } on PlatformException {
      return false;
    }
  }

  /// A background check shows Sparkle's window only when an update exists; a
  /// user-initiated check always reports back, including "up to date".
  Future<void> checkForUpdates({
    required String bucketUrl,
    bool background = false,
  }) {
    return _channel.invokeMethod<void>('checkForUpdates', <String, Object?>{
      'feedUrl': sparkleAppcastUrl(bucketUrl),
      'background': background,
    });
  }
}
