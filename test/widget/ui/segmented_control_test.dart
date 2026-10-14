import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/ui/ui.dart' as ui;

void main() {
  testWidgets(
    'segmented control badge follows content instead of segment edge',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(640, 120);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: Scaffold(
            body: Center(
              child: SizedBox(
                width: 600,
                child: ui.SegmentedControl<int>(
                  expanded: true,
                  value: 1,
                  onChanged: (_) {},
                  segments: const [
                    ui.Segment(value: 0, label: 'Members'),
                    ui.Segment(
                      value: 1,
                      label: 'New Members',
                      icon: Icons.person_add_alt_1,
                      showBadge: true,
                      badgeKey: ValueKey('new-members-tab-badge'),
                    ),
                    ui.Segment(value: 2, label: 'Blacklist'),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pump();

      final badgeRect = tester.getRect(
        find.byKey(const ValueKey('new-members-tab-badge')),
      );
      final labelRect = tester.getRect(find.text('New Members'));
      final segmentRect = tester.getRect(
        find.ancestor(
          of: find.text('New Members'),
          matching: find.byType(GestureDetector),
        ),
      );

      expect(badgeRect.center.dx, greaterThan(labelRect.right - 2));
      expect(badgeRect.center.dx, lessThan(labelRect.right + 12));
      expect(badgeRect.center.dy, lessThan(labelRect.center.dy));
      expect(segmentRect.right - badgeRect.right, greaterThan(6));
    },
  );

  testWidgets('segmented control fills the row with content-adaptive widths', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(640, 120);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 480,
              child: ui.SegmentedControl<int>(
                expanded: true,
                value: 0,
                onChanged: (_) {},
                segments: const [
                  ui.Segment(value: 0, label: 'A'),
                  ui.Segment(value: 1, label: 'Medium'),
                  ui.Segment(value: 2, label: 'Much Longer Label'),
                ],
              ),
            ),
          ),
        ),
      ),
    );

    final segmentRects = [
      for (final label in ['A', 'Medium', 'Much Longer Label'])
        tester.getRect(
          find.ancestor(
            of: find.text(label),
            matching: find.byType(GestureDetector),
          ),
        ),
    ];
    final controlRect = tester.getRect(find.byType(ui.SegmentedControl<int>));

    expect(segmentRects[0].width, lessThan(segmentRects[1].width));
    expect(segmentRects[1].width, lessThan(segmentRects[2].width));
    expect(segmentRects.first.left, closeTo(controlRect.left, 0.01));
    expect(segmentRects.last.right, closeTo(controlRect.right, 0.01));
    for (final label in ['A', 'Medium', 'Much Longer Label']) {
      expect(
        tester
            .getRect(
              find.ancestor(
                of: find.text(label),
                matching: find.byType(GestureDetector),
              ),
            )
            .width,
        greaterThan(tester.getRect(find.text(label)).width),
      );
    }
    expect(find.byTooltip('选择选项'), findsNothing);
  });

  testWidgets(
    'segmented control avoids fractional overflow at its minimum width',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(640, 120);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);

      const segments = [
        ui.Segment(value: 0, label: '全部 16'),
        ui.Segment(value: 1, label: '语音 0'),
        ui.Segment(value: 2, label: '在线 2'),
        ui.Segment(value: 3, label: '离线 14'),
      ];
      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: Scaffold(
            body: Center(
              child: Builder(
                builder: (context) {
                  return SizedBox(
                    width: ui.SegmentedControl.minimumWidthFor(
                      context,
                      segments,
                    ),
                    child: ui.SegmentedControl<int>(
                      expanded: true,
                      value: 0,
                      onChanged: (_) {},
                      segments: segments,
                    ),
                  );
                },
              ),
            ),
          ),
        ),
      );
      await tester.pump();

      expect(
        find.byKey(const ValueKey('segmented-control-scroll-view')),
        findsNothing,
      );
      for (final segment in segments) {
        expect(find.text(segment.label), findsOneWidget);
      }
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'segmented control uses hidden horizontal overflow controls when needed',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(260, 160);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      var selected = 'info';

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: Scaffold(
            body: Center(
              child: SizedBox(
                width: 220,
                child: StatefulBuilder(
                  builder: (context, setState) {
                    return ui.SegmentedControl<String>(
                      expanded: true,
                      value: selected,
                      onChanged: (value) {
                        setState(() => selected = value);
                      },
                      segments: const [
                        ui.Segment(
                          value: 'info',
                          label: '房间信息',
                          icon: Icons.info_outline,
                        ),
                        ui.Segment(
                          value: 'profile',
                          label: '个性化设置',
                          icon: Icons.tune,
                        ),
                        ui.Segment(
                          value: 'messages',
                          label: '消息记录',
                          icon: Icons.history,
                        ),
                        ui.Segment(
                          value: 'stickers',
                          label: '表情包管理',
                          icon: Icons.emoji_emotions_outlined,
                        ),
                      ],
                    );
                  },
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pump();

      final scrollView = find.byKey(
        const ValueKey('segmented-control-scroll-view'),
      );
      expect(scrollView, findsOneWidget);
      expect(find.byType(Scrollbar), findsNothing);
      expect(find.byTooltip('选择选项'), findsOneWidget);
      expect(find.byTooltip('回到开头'), findsNothing);
      for (final label in ['房间信息', '个性化设置', '消息记录', '表情包管理']) {
        final text = tester.widget<Text>(find.text(label));
        expect(text.overflow, TextOverflow.visible);
      }

      await tester.drag(scrollView, const Offset(-120, 0));
      await tester.pump();

      expect(find.byTooltip('回到开头'), findsOneWidget);

      await tester.tap(find.byTooltip('选择选项'));
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('当前'), findsNothing);
      final selectedMenuLabel = tester.widget<Text>(find.text('房间信息').last);
      expect(selectedMenuLabel.style?.color, ui.UiColors.accent);
      await tester.tap(find.text('消息记录').last);
      await tester.pumpAndSettle();

      expect(selected, 'messages');
      final selectedSegment = find.ancestor(
        of: find.text('消息记录'),
        matching: find.byType(GestureDetector),
      );
      expect(
        tester.getRect(selectedSegment).center.dx,
        closeTo(tester.getRect(scrollView).center.dx, 1),
      );

      await tester.tap(find.byTooltip('选择选项'));
      await tester.pump(const Duration(milliseconds: 100));
      await tester.tap(find.text('表情包管理').last);
      await tester.pumpAndSettle();

      expect(selected, 'stickers');
      final scrollable = find.descendant(
        of: scrollView,
        matching: find.byType(Scrollable),
      );
      final position = tester.state<ScrollableState>(scrollable).position;
      expect(position.pixels, closeTo(position.maxScrollExtent, 1));

      await tester.tap(find.byTooltip('回到开头'));
      await tester.pumpAndSettle();

      expect(find.byTooltip('回到开头'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('overflow control supports primary-button mouse dragging', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(260, 120);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 220,
              child: ui.SegmentedControl<int>(
                expanded: true,
                value: 0,
                onChanged: (_) {},
                segments: const [
                  ui.Segment(value: 0, label: '用户资料', icon: Icons.badge),
                  ui.Segment(value: 1, label: '偏好设置', icon: Icons.tune),
                  ui.Segment(value: 2, label: '隐私和安全', icon: Icons.shield),
                  ui.Segment(value: 3, label: '语音和视频', icon: Icons.graphic_eq),
                ],
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pump();

    final scrollView = find.byKey(
      const ValueKey('segmented-control-scroll-view'),
    );
    final scrollable = find.descendant(
      of: scrollView,
      matching: find.byType(Scrollable),
    );
    final position = tester.state<ScrollableState>(scrollable).position;
    expect(position.pixels, 0);

    await tester.drag(
      scrollView,
      const Offset(-100, 0),
      kind: PointerDeviceKind.mouse,
    );
    await tester.pumpAndSettle();

    expect(position.pixels, greaterThan(0));
    expect(find.byTooltip('回到开头'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'overflow control centers the initial selection or reaches its edge',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(260, 120);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: Scaffold(
            body: Center(
              child: SizedBox(
                width: 220,
                child: ui.SegmentedControl<int>(
                  expanded: true,
                  value: 3,
                  onChanged: (_) {},
                  segments: const [
                    ui.Segment(value: 0, label: '房间信息', icon: Icons.info),
                    ui.Segment(value: 1, label: '个性设置', icon: Icons.tune),
                    ui.Segment(value: 2, label: '消息记录', icon: Icons.history),
                    ui.Segment(
                      value: 3,
                      label: '表情管理',
                      icon: Icons.emoji_emotions,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      final scrollView = find.byKey(
        const ValueKey('segmented-control-scroll-view'),
      );
      final scrollable = find.descendant(
        of: scrollView,
        matching: find.byType(Scrollable),
      );
      final position = tester.state<ScrollableState>(scrollable).position;
      expect(position.pixels, closeTo(position.maxScrollExtent, 1));
      expect(find.byTooltip('回到开头'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'keyed segmented controls do not inherit previous thumb position',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(640, 120);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);

      Widget buildControl(Key key, int value) {
        return MaterialApp(
          theme: ui.uiTheme(),
          home: Scaffold(
            body: Center(
              child: SizedBox(
                width: 300,
                child: ui.SegmentedControl<int>(
                  key: key,
                  expanded: true,
                  value: value,
                  onChanged: (_) {},
                  segments: const [
                    ui.Segment(value: 0, label: 'One'),
                    ui.Segment(value: 1, label: 'Two'),
                    ui.Segment(value: 2, label: 'Three'),
                  ],
                ),
              ),
            ),
          ),
        );
      }

      await tester.pumpWidget(
        buildControl(const ValueKey('first-segmented-setting'), 0),
      );
      await tester.pump();

      await tester.pumpWidget(
        buildControl(const ValueKey('second-segmented-setting'), 2),
      );

      final selectedThumb = find.byWidgetPredicate((widget) {
        if (widget is! DecoratedBox) return false;
        final decoration = widget.decoration;
        return decoration is BoxDecoration &&
            decoration.color == ui.UiColors.inverse;
      });
      final selectedThumbRect = tester.getRect(selectedThumb);
      final thirdSegmentRect = tester.getRect(
        find.ancestor(
          of: find.text('Three'),
          matching: find.byType(GestureDetector),
        ),
      );

      expect(thirdSegmentRect.contains(selectedThumbRect.center), isTrue);
    },
  );
}
