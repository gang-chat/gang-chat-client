import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/ui/ui.dart' as ui;

void main() {
  const cancelKey = ValueKey('cancel-action');
  const downloadKey = ValueKey('download-action');

  List<ui.ResponsiveDialogAction> actions() => [
    ui.ResponsiveDialogAction(
      buttonKey: cancelKey,
      label: '取消',
      onPressed: () {},
    ),
    ui.ResponsiveDialogAction(
      buttonKey: downloadKey,
      label: '下载新版本',
      icon: Icons.download_outlined,
      tone: ui.ButtonTone.primary,
      onPressed: () {},
    ),
  ];

  Future<void> pumpBar(
    WidgetTester tester, {
    required TargetPlatform platform,
    required double width,
  }) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(600, 400);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: platform),
        home: Scaffold(
          body: Align(
            alignment: Alignment.topLeft,
            child: SizedBox(
              width: width,
              child: ui.ResponsiveDialogActionBar(
                expanded: true,
                actions: actions(),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pump();
  }

  testWidgets(
    'Android removes icons and keeps complete labels in one row when text fits',
    (tester) async {
      await pumpBar(tester, platform: TargetPlatform.android, width: 180);

      final cancelRect = tester.getRect(find.byKey(cancelKey));
      final downloadRect = tester.getRect(find.byKey(downloadKey));
      final downloadParagraph = tester.renderObject<RenderParagraph>(
        find.text('下载新版本'),
      );

      expect(cancelRect.center.dy, downloadRect.center.dy);
      expect(find.byIcon(Icons.download_outlined), findsNothing);
      expect(downloadParagraph.didExceedMaxLines, isFalse);
      expect(cancelRect.width + downloadRect.width + 10, closeTo(180, 0.01));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('Android stacks only after complete labels no longer fit', (
    tester,
  ) async {
    await pumpBar(tester, platform: TargetPlatform.android, width: 140);

    final cancelRect = tester.getRect(find.byKey(cancelKey));
    final downloadRect = tester.getRect(find.byKey(downloadKey));
    final downloadParagraph = tester.renderObject<RenderParagraph>(
      find.text('下载新版本'),
    );

    expect(downloadRect.top, greaterThan(cancelRect.bottom));
    expect(cancelRect.width, 140);
    expect(downloadRect.width, 140);
    expect(downloadParagraph.didExceedMaxLines, isFalse);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'Android reset and confirm actions remove icons before stacking',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(600, 400);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
          home: Scaffold(
            body: Align(
              alignment: Alignment.topLeft,
              child: SizedBox(
                width: 220,
                child: ui.ResponsiveDialogActionBar(
                  expanded: true,
                  actions: [
                    ui.ResponsiveDialogAction(
                      label: '重置',
                      icon: Icons.restart_alt,
                      onPressed: () {},
                    ),
                    ui.ResponsiveDialogAction(label: '取消', onPressed: () {}),
                    ui.ResponsiveDialogAction(
                      label: '确认',
                      icon: Icons.check,
                      tone: ui.ButtonTone.primary,
                      onPressed: () {},
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pump();

      expect(
        tester.getCenter(find.text('重置')).dy,
        tester.getCenter(find.text('取消')).dy,
      );
      expect(
        tester.getCenter(find.text('取消')).dy,
        tester.getCenter(find.text('确认')).dy,
      );
      expect(find.byIcon(Icons.restart_alt), findsNothing);
      expect(find.byIcon(Icons.check), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('Windows keeps complete labels in one row when their sum fits', (
    tester,
  ) async {
    await pumpBar(tester, platform: TargetPlatform.windows, width: 180);

    final cancelRect = tester.getRect(find.byKey(cancelKey));
    final downloadRect = tester.getRect(find.byKey(downloadKey));
    final downloadParagraph = tester.renderObject<RenderParagraph>(
      find.text('下载新版本'),
    );

    expect(cancelRect.center.dy, downloadRect.center.dy);
    expect(find.byIcon(Icons.download_outlined), findsNothing);
    expect(downloadParagraph.didExceedMaxLines, isFalse);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Windows stacks only after complete labels no longer fit', (
    tester,
  ) async {
    await pumpBar(tester, platform: TargetPlatform.windows, width: 140);

    final cancelRect = tester.getRect(find.byKey(cancelKey));
    final downloadRect = tester.getRect(find.byKey(downloadKey));
    expect(downloadRect.top, greaterThan(cancelRect.bottom));
    expect(cancelRect.width, 140);
    expect(downloadRect.width, 140);
    expect(tester.takeException(), isNull);
  });

  testWidgets('DialogFrame adapts standard actions on Android and Windows', (
    tester,
  ) async {
    Future<void> pumpFrame(TargetPlatform platform) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(600, 500);
      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: platform),
          home: Scaffold(
            body: ui.DialogFrame(
              title: '下载新版本',
              maxWidth: 300,
              adaptiveActions: actions(),
              child: const Text('确认下载？'),
            ),
          ),
        ),
      );
      await tester.pump();
    }

    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);

    await pumpFrame(TargetPlatform.android);
    var cancelRect = tester.getRect(find.byKey(cancelKey));
    var downloadRect = tester.getRect(find.byKey(downloadKey));
    expect(cancelRect.center.dy, downloadRect.center.dy);

    await pumpFrame(TargetPlatform.windows);
    cancelRect = tester.getRect(find.byKey(cancelKey));
    downloadRect = tester.getRect(find.byKey(downloadKey));
    expect(cancelRect.center.dy, downloadRect.center.dy);
    expect(tester.takeException(), isNull);
  });
}
