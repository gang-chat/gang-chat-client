import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/ui/platform_gestures.dart';

void main() {
  testWidgets('UiPointerTapRegion fires once for a primary mouse click', (
    tester,
  ) async {
    var taps = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Center(
          child: UiPointerTapRegion(
            onTap: () => taps += 1,
            child: const SizedBox(width: 240, height: 80),
          ),
        ),
      ),
    );

    final target = find.byType(UiPointerTapRegion);
    final mouse = await tester.createGesture(
      pointer: 31,
      kind: PointerDeviceKind.mouse,
    );
    addTearDown(mouse.removePointer);
    final center = tester.getCenter(target);
    await mouse.addPointer(location: center);
    await mouse.down(center);
    await mouse.up();
    await tester.pump();

    expect(taps, 1);
  });

  testWidgets('UiPointerTapRegion rejects a touch drag', (tester) async {
    var taps = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Center(
          child: UiPointerTapRegion(
            onTap: () => taps += 1,
            child: const SizedBox(width: 240, height: 80),
          ),
        ),
      ),
    );

    final center = tester.getCenter(find.byType(UiPointerTapRegion));
    final touch = await tester.startGesture(center);
    await touch.moveBy(Offset(kTouchSlop + 4, 0));
    await touch.up();
    await tester.pump();

    expect(taps, 0);
  });

  testWidgets('UiPointerTapRegion rejects a touch long press', (tester) async {
    var taps = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Center(
          child: UiPointerTapRegion(
            onTap: () => taps += 1,
            child: const SizedBox(width: 240, height: 80),
          ),
        ),
      ),
    );

    final center = tester.getCenter(find.byType(UiPointerTapRegion));
    final touch = await tester.startGesture(center);
    await tester.pump(kLongPressTimeout);
    await touch.up();
    await tester.pump();

    expect(taps, 0);
  });
}
