import 'package:client/src/ui/ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Widget testApp({
    required TargetPlatform platform,
    Axis scrollDirection = Axis.vertical,
    ScrollBehavior? scrollBehavior,
  }) {
    return MaterialApp(
      theme: ThemeData(platform: platform),
      scrollBehavior: scrollBehavior ?? const GangScrollBehavior(),
      home: SizedBox(
        width: 240,
        height: 240,
        child: ListView.builder(
          key: const ValueKey('scrollable-list'),
          scrollDirection: scrollDirection,
          itemCount: 20,
          itemBuilder: (context, index) =>
              SizedBox(width: 80, height: 80, child: Text('$index')),
        ),
      ),
    );
  }

  Finder desktopVerticalGutter() {
    return find.byWidgetPredicate(
      (widget) =>
          widget is Padding &&
          widget.padding ==
              const EdgeInsets.only(
                right: GangScrollBehavior.verticalScrollbarGutter,
              ),
    );
  }

  for (final platform in <TargetPlatform>[
    TargetPlatform.windows,
    TargetPlatform.macOS,
  ]) {
    testWidgets('$platform keeps its vertical scrollbar outside list content', (
      tester,
    ) async {
      await tester.pumpWidget(testApp(platform: platform));

      expect(find.byType(Scrollbar), findsOneWidget);
      expect(desktopVerticalGutter(), findsOneWidget);

      final gutterRect = tester.getRect(desktopVerticalGutter());
      final viewportRect = tester.getRect(
        find.descendant(
          of: desktopVerticalGutter(),
          matching: find.byType(Viewport),
        ),
      );
      expect(
        gutterRect.right - viewportRect.right,
        GangScrollBehavior.verticalScrollbarGutter,
      );
    });
  }

  testWidgets('Android keeps native vertical list width unchanged', (
    tester,
  ) async {
    await tester.pumpWidget(testApp(platform: TargetPlatform.android));

    expect(find.byType(Scrollbar), findsNothing);
    expect(desktopVerticalGutter(), findsNothing);

    final listRect = tester.getRect(
      find.byKey(const ValueKey('scrollable-list')),
    );
    final appRect = tester.getRect(find.byType(MaterialApp));
    expect(listRect.right, appRect.right);
  });

  testWidgets('desktop horizontal lists do not reserve a vertical gutter', (
    tester,
  ) async {
    await tester.pumpWidget(
      testApp(
        platform: TargetPlatform.windows,
        scrollDirection: Axis.horizontal,
      ),
    );

    expect(desktopVerticalGutter(), findsNothing);
  });

  testWidgets('lists with custom scrollbars can disable the automatic gutter', (
    tester,
  ) async {
    final baseBehavior = const GangScrollBehavior();
    await tester.pumpWidget(
      testApp(
        platform: TargetPlatform.windows,
        scrollBehavior: baseBehavior.copyWith(scrollbars: false),
      ),
    );

    expect(find.byType(Scrollbar), findsNothing);
    expect(desktopVerticalGutter(), findsNothing);
  });
}
