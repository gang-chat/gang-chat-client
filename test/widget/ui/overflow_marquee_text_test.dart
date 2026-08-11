import 'package:client/src/ui/overflow_marquee_text.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets(
    'marquee reaches both ends with inherited typography and text scaling',
    (tester) async {
      const text = '张顺飞 - 绝不认输 feat.侯顺玉 MV正式发布';
      const viewportKey = ValueKey<String>('marquee-viewport');
      const trackKey = ValueKey<String>('marquee-track');

      await tester.pumpWidget(
        MaterialApp(
          home: MediaQuery(
            data: const MediaQueryData(textScaler: TextScaler.linear(1.2)),
            child: Scaffold(
              body: Center(
                child: DefaultTextStyle(
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 1.5,
                  ),
                  child: const SizedBox(
                    key: viewportKey,
                    width: 150,
                    child: OverflowMarqueeText(
                      text: text,
                      style: TextStyle(color: Colors.white),
                      trackKey: trackKey,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pump();

      final viewport = tester.getRect(find.byKey(viewportKey));
      expect(
        tester.getRect(find.text(text)).left,
        closeTo(viewport.left, 0.01),
      );

      // Wait through the initial pause and the maximum configured travel time.
      await tester.pump(const Duration(milliseconds: 851));
      await tester.pump(const Duration(milliseconds: 10001));

      final atTrailingBoundary = tester.getRect(find.text(text));
      expect(atTrailingBoundary.right, lessThanOrEqualTo(viewport.right));
      expect(atTrailingBoundary.right, closeTo(viewport.right, 1.01));
      expect(tester.takeException(), isNull);
    },
  );
}
