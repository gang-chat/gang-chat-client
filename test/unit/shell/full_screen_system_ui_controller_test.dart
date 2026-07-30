import 'package:client/src/shell/full_screen_system_ui_controller.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Android system bars follow full-screen media controls', () async {
    final requests = <({SystemUiMode mode, List<SystemUiOverlay>? overlays})>[];
    final controller = FullScreenSystemUiController(
      platform: TargetPlatform.android,
      setEnabledSystemUIMode: (mode, {overlays}) async {
        requests.add((mode: mode, overlays: overlays));
      },
    );

    await controller.setControlsVisible(true);
    await controller.setControlsVisible(false);
    await controller.restore();

    expect(requests, hasLength(3));
    expect(requests[0].mode, SystemUiMode.manual);
    expect(requests[0].overlays, SystemUiOverlay.values);
    expect(requests[1].mode, SystemUiMode.immersiveSticky);
    expect(requests[1].overlays, isNull);
    expect(requests[2].mode, SystemUiMode.manual);
    expect(requests[2].overlays, SystemUiOverlay.values);
  });

  test('system UI requests never reach non-Android platforms', () async {
    var calls = 0;
    final controller = FullScreenSystemUiController(
      platform: TargetPlatform.windows,
      setEnabledSystemUIMode: (mode, {overlays}) async {
        calls += 1;
      },
    );

    await controller.setControlsVisible(false);
    await controller.restore();

    expect(calls, 0);
  });

  test('restore still runs after an earlier platform failure', () async {
    final modes = <SystemUiMode>[];
    var calls = 0;
    final controller = FullScreenSystemUiController(
      platform: TargetPlatform.android,
      setEnabledSystemUIMode: (mode, {overlays}) async {
        calls += 1;
        modes.add(mode);
        if (calls == 1) throw PlatformException(code: 'unsupported');
      },
    );

    await expectLater(
      controller.setControlsVisible(false),
      throwsA(isA<PlatformException>()),
    );
    await controller.restore();

    expect(modes, <SystemUiMode>[
      SystemUiMode.immersiveSticky,
      SystemUiMode.manual,
    ]);
  });
}
