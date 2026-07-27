import 'package:client/src/home/home_keyboard_layout.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Android music box overlays the keyboard without resizing home', () {
    expect(
      shouldResizeHomeForKeyboard(
        platform: TargetPlatform.android,
        liveMusicBoxVisible: true,
      ),
      isFalse,
    );
  });

  test('other Android inputs keep normal keyboard resizing', () {
    expect(
      shouldResizeHomeForKeyboard(
        platform: TargetPlatform.android,
        liveMusicBoxVisible: false,
      ),
      isTrue,
    );
  });

  test('desktop layout is unaffected by the music box state', () {
    for (final platform in [
      TargetPlatform.windows,
      TargetPlatform.macOS,
      TargetPlatform.linux,
    ]) {
      expect(
        shouldResizeHomeForKeyboard(
          platform: platform,
          liveMusicBoxVisible: true,
        ),
        isTrue,
      );
    }
  });

  testWidgets('keyboard animation keeps the enabled home viewport stable', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(360, 740);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);
    const contentKey = ValueKey<String>('home-keyboard-content');

    Widget host({required double height, required double keyboardInset}) {
      return MediaQuery(
        data: MediaQueryData(
          size: Size(360, height),
          viewInsets: EdgeInsets.only(bottom: keyboardInset),
        ),
        child: Directionality(
          textDirection: TextDirection.ltr,
          child: Align(
            alignment: Alignment.topLeft,
            child: SizedBox(
              width: 360,
              height: height,
              child: HomeKeyboardOverlayViewport(
                enabled: true,
                child: const SizedBox.expand(key: contentKey),
              ),
            ),
          ),
        ),
      );
    }

    await tester.pumpWidget(host(height: 740, keyboardInset: 0));
    expect(tester.getSize(find.byKey(contentKey)), const Size(360, 740));

    for (final frame in [
      (height: 660.0, inset: 80.0),
      (height: 560.0, inset: 180.0),
      (height: 440.0, inset: 300.0),
      (height: 560.0, inset: 180.0),
      (height: 660.0, inset: 80.0),
    ]) {
      await tester.pumpWidget(
        host(height: frame.height, keyboardInset: frame.inset),
      );
      expect(tester.getSize(find.byKey(contentKey)), const Size(360, 740));
      expect(tester.takeException(), isNull);
    }

    await tester.pumpWidget(host(height: 740, keyboardInset: 0));
    expect(tester.getSize(find.byKey(contentKey)), const Size(360, 740));
    expect(tester.takeException(), isNull);
  });

  testWidgets('disabled viewport follows ordinary keyboard constraints', (
    tester,
  ) async {
    const contentKey = ValueKey<String>('home-keyboard-content');

    await tester.pumpWidget(
      const MediaQuery(
        data: MediaQueryData(
          size: Size(360, 440),
          viewInsets: EdgeInsets.only(bottom: 300),
        ),
        child: Directionality(
          textDirection: TextDirection.ltr,
          child: Align(
            alignment: Alignment.topLeft,
            child: SizedBox(
              width: 360,
              height: 440,
              child: HomeKeyboardOverlayViewport(
                enabled: false,
                child: SizedBox.expand(key: contentKey),
              ),
            ),
          ),
        ),
      ),
    );

    expect(tester.getSize(find.byKey(contentKey)), const Size(360, 440));
    expect(tester.takeException(), isNull);
  });
}
