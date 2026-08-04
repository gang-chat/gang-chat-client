import 'dart:ui' show PointerDeviceKind;

import 'package:client/src/ui/ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('horizontal slider reaches both visual endpoints', (
    tester,
  ) async {
    Future<void> pump(double value) {
      return tester.pumpWidget(
        MaterialApp(
          home: Center(
            child: SizedBox(
              width: 100,
              child: UiSlider(value: value, onChanged: (_) {}),
            ),
          ),
        ),
      );
    }

    double handleLeft() {
      return tester
          .widgetList<Positioned>(
            find.descendant(
              of: find.byType(UiSlider),
              matching: find.byType(Positioned),
            ),
          )
          .singleWhere(
            (positioned) => positioned.width == 4 && positioned.height == 14,
          )
          .left!;
    }

    await pump(0);
    expect(handleLeft(), 0);

    await pump(1);
    expect(handleLeft(), 96);
  });

  testWidgets('slider shows hover percentage above its thumb', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Center(
          child: SizedBox(
            width: 100,
            child: UiSlider(value: 0.46, hoverLabel: '46%', onChanged: (_) {}),
          ),
        ),
      ),
    );

    expect(
      find.byKey(const ValueKey<String>('ui-slider-hover-label')),
      findsNothing,
    );

    final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
    addTearDown(mouse.removePointer);
    await mouse.addPointer(location: Offset.zero);
    await mouse.moveTo(tester.getCenter(find.byType(UiSlider)));
    await tester.pump();

    final label = find.byKey(const ValueKey<String>('ui-slider-hover-label'));
    expect(find.text('46%'), findsOneWidget);
    expect(label, findsOneWidget);

    final handle = tester
        .widgetList<Positioned>(
          find.descendant(
            of: find.byType(UiSlider),
            matching: find.byType(Positioned),
          ),
        )
        .singleWhere(
          (positioned) => positioned.width == 4 && positioned.height == 14,
        );
    final handleRect = Rect.fromLTWH(
      handle.left!,
      handle.top!,
      handle.width!,
      handle.height!,
    );
    final labelRect = tester.getRect(label);
    final sliderRect = tester.getRect(find.byType(UiSlider));
    expect(labelRect.bottom, lessThan(sliderRect.top));
    expect(
      labelRect.center.dx,
      closeTo(sliderRect.left + handleRect.center.dx, 0.01),
    );
  });
}
