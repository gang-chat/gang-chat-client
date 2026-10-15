import 'dart:io' as io show Platform;

import 'package:flutter/foundation.dart' show kIsWeb;

/// The operating system the app runs on, safe to query from code shared with
/// the web build.
///
/// `dart:io`'s `Platform` throws `Unsupported operation:
/// Platform._operatingSystem` in a browser. Through here every `is*` check is
/// false on the web and the other values fall back to neutral defaults, while
/// native builds see exactly what `Platform` reports.
abstract final class HostPlatform {
  static bool get isWindows => !kIsWeb && io.Platform.isWindows;
  static bool get isMacOS => !kIsWeb && io.Platform.isMacOS;
  static bool get isLinux => !kIsWeb && io.Platform.isLinux;
  static bool get isAndroid => !kIsWeb && io.Platform.isAndroid;

  /// `'web'` in a browser, which no platform-specific lookup recognises.
  static String get operatingSystem =>
      kIsWeb ? 'web' : io.Platform.operatingSystem;

  static String get pathSeparator => kIsWeb ? '/' : io.Platform.pathSeparator;

  static Map<String, String> get environment =>
      kIsWeb ? const {} : io.Platform.environment;

  static String get resolvedExecutable =>
      kIsWeb ? '' : io.Platform.resolvedExecutable;
}
