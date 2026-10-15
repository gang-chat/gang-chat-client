@TestOn('browser')
library;

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/live/live_session.dart';

// Run with: flutter test --platform chrome test/unit/live/web_screen_share_support_test.dart
void main() {
  tearDown(() => debugDefaultTargetPlatformOverride = null);

  test('desktop browsers share through the browser picker', () {
    debugDefaultTargetPlatformOverride = TargetPlatform.macOS;
    expect(supportsBrowserScreenShare, isTrue);
    expect(supportsLocalScreenShare, isTrue);
    // The native desktop source picker is never used in a browser.
    expect(supportsDesktopScreenShare, isFalse);

    debugDefaultTargetPlatformOverride = TargetPlatform.windows;
    expect(supportsLocalScreenShare, isTrue);
  });

  test('mobile browsers cannot share a screen', () {
    for (final platform in [TargetPlatform.android, TargetPlatform.iOS]) {
      debugDefaultTargetPlatformOverride = platform;
      expect(supportsBrowserScreenShare, isFalse, reason: '$platform');
      expect(supportsLocalScreenShare, isFalse, reason: '$platform');
    }
  });
}
