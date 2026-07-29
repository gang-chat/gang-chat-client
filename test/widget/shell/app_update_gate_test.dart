import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/app_update.dart';
import 'package:client/src/app/settings_about.dart';
import 'package:client/src/shell/app_update_gate.dart';
import 'package:client/src/shell/desktop_window_controller.dart';
import 'package:client/src/shell/release_update_service.dart';
import 'package:client/src/ui/ui.dart' as ui;

void main() {
  testWidgets('update gate reports update and keeps home visible', (
    tester,
  ) async {
    final update = AvailableAppUpdate(
      currentVersion: '0.5.0',
      latestVersion: '0.5.1',
      asset: ReleaseAsset(
        key: 'releases/GangChat_v0.5.1.exe',
        version: '0.5.1',
        platform: AppUpdatePlatform.windows,
        releasedAt: DateTime.utc(2026, 7, 8, 1, 2),
      ),
      downloadUrl: Uri.parse(
        'https://os.example.test/gang-chat/releases/GangChat_v0.5.1.exe',
      ),
    );
    final reportedUpdates = <AvailableAppUpdate>[];
    final updateService = _FakeReleaseUpdateService(update);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: AppUpdateGate(
          releaseBucketUrl: 'https://os.example.test/gang-chat',
          currentVersion: '0.5.0',
          autoUpdatePromptStore: const _FakeAutoUpdatePromptStore(true),
          updateService: updateService,
          platformOverride: AppUpdatePlatform.windows,
          windowController: DesktopWindowController(),
          onUpdateAvailable: reportedUpdates.add,
          child: const Scaffold(body: Text('Home is still available')),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Home is still available'), findsOneWidget);
    expect(find.text('发现新版本'), findsNothing);
    expect(reportedUpdates, [same(update)]);
    expect(updateService.requestedPlatform, AppUpdatePlatform.windows);
  });

  testWidgets('update gate reports Android APK updates in the desktop form', (
    tester,
  ) async {
    final update = AvailableAppUpdate(
      currentVersion: '1.0.0',
      latestVersion: '1.0.1',
      asset: const ReleaseAsset(
        key: 'releases/GangChat_v1.0.1.apk',
        version: '1.0.1',
        platform: AppUpdatePlatform.android,
      ),
      downloadUrl: Uri.parse(
        'https://os.example.test/gang-chat/releases/GangChat_v1.0.1.apk',
      ),
    );
    final updateService = _FakeReleaseUpdateService(update);
    final reportedUpdates = <AvailableAppUpdate>[];

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
        home: AppUpdateGate(
          releaseBucketUrl: 'https://os.example.test/gang-chat',
          currentVersion: '1.0.0',
          autoUpdatePromptStore: const _FakeAutoUpdatePromptStore(true),
          updateService: updateService,
          platformOverride: AppUpdatePlatform.android,
          windowController: DesktopWindowController(),
          onUpdateAvailable: reportedUpdates.add,
          child: const Scaffold(body: Text('Android home stays available')),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Android home stays available'), findsOneWidget);
    expect(reportedUpdates, [same(update)]);
    expect(updateService.requestedPlatform, AppUpdatePlatform.android);
  });

  testWidgets('update page shows release details and settings actions', (
    tester,
  ) async {
    final update = AvailableAppUpdate(
      currentVersion: '0.5.0',
      latestVersion: '0.5.1',
      asset: ReleaseAsset(
        key: 'releases/GangChat_v0.5.1.exe',
        version: '0.5.1',
        platform: AppUpdatePlatform.windows,
        releasedAt: DateTime.utc(2026, 7, 8, 1, 2),
      ),
      downloadUrl: Uri.parse(
        'https://os.example.test/gang-chat/releases/GangChat_v0.5.1.exe',
      ),
    );
    var backCount = 0;
    var ignoreCount = 0;
    var downloadCount = 0;
    var refreshCount = 0;

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: AppUpdatePage(
          update: update,
          checking: false,
          downloading: false,
          downloadedBytes: 0,
          wrapInScaffold: true,
          onBack: () => backCount += 1,
          onRefresh: () => refreshCount += 1,
          onIgnoreVersion: () => ignoreCount += 1,
          onDownload: () => downloadCount += 1,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('发现新版本'), findsOneWidget);
    expect(find.text('v0.5.1'), findsWidgets);
    expect(find.text('发行时间'), findsOneWidget);
    expect(find.text('2026/07/08 09:02 UTC+08:00'), findsOneWidget);
    expect(find.text('版本日志'), findsOneWidget);
    expect(find.text('无'), findsOneWidget);
    expect(find.textContaining('安装包来自'), findsNothing);
    expect(find.text('English'), findsNothing);
    expect(find.byTooltip('重新检查'), findsOneWidget);
    expect(find.widgetWithText(ui.Button, '继续使用'), findsNothing);
    expect(find.widgetWithText(ui.Button, '忽略此版本'), findsOneWidget);
    expect(find.widgetWithText(ui.Button, '下载新版本'), findsOneWidget);

    await tester.tap(find.byTooltip('返回'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('重新检查'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ui.Button, '忽略此版本'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ui.Button, '下载新版本'));
    await tester.pumpAndSettle();

    expect(backCount, 1);
    expect(refreshCount, 1);
    expect(ignoreCount, 1);
    expect(downloadCount, 1);
  });

  testWidgets('narrow update page scales release time without ellipsis', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(320, 800);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);
    final update = AvailableAppUpdate(
      currentVersion: '1.0.0',
      latestVersion: '1.0.1',
      asset: ReleaseAsset(
        key: 'releases/GangChat_v1.0.1.apk',
        version: '1.0.1',
        platform: AppUpdatePlatform.android,
        releasedAt: DateTime.utc(2026, 7, 29, 9, 49),
      ),
      downloadUrl: Uri.parse(
        'https://os.example.test/gang-chat/releases/GangChat_v1.0.1.apk',
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
        home: AppUpdatePage(
          update: update,
          checking: false,
          downloading: false,
          downloadedBytes: 0,
          wrapInScaffold: true,
          onBack: () {},
          onRefresh: () {},
          onIgnoreVersion: () {},
          onDownload: () {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    final value = find.text('2026/07/29 17:49 UTC+08:00');
    final fitted = find.ancestor(of: value, matching: find.byType(FittedBox));
    final paragraph = tester.renderObject<RenderParagraph>(value);
    expect(value, findsOneWidget);
    expect(fitted, findsOneWidget);
    expect(paragraph.didExceedMaxLines, isFalse);
    expect(
      tester.getSize(value).width,
      greaterThan(tester.getSize(fitted).width),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'update gate skips ignored version until a higher version appears',
    (tester) async {
      final ignored = AvailableAppUpdate(
        currentVersion: '0.5.0',
        latestVersion: '0.5.1',
        asset: const ReleaseAsset(
          key: 'releases/GangChat_v0.5.1.exe',
          version: '0.5.1',
          platform: AppUpdatePlatform.windows,
        ),
        downloadUrl: Uri.parse(
          'https://os.example.test/gang-chat/releases/GangChat_v0.5.1.exe',
        ),
      );
      final higher = AvailableAppUpdate(
        currentVersion: '0.5.0',
        latestVersion: '0.5.2',
        asset: const ReleaseAsset(
          key: 'releases/GangChat_v0.5.2.exe',
          version: '0.5.2',
          platform: AppUpdatePlatform.windows,
        ),
        downloadUrl: Uri.parse(
          'https://os.example.test/gang-chat/releases/GangChat_v0.5.2.exe',
        ),
      );
      final store = _FakeAutoUpdatePromptStore(true, ignoredVersion: '0.5.1');
      final updateService = _MutableFakeReleaseUpdateService(ignored);
      final reportedUpdates = <AvailableAppUpdate>[];

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: AppUpdateGate(
            releaseBucketUrl: 'https://os.example.test/gang-chat',
            currentVersion: '0.5.0',
            autoUpdatePromptStore: store,
            updateService: updateService,
            platformOverride: AppUpdatePlatform.windows,
            windowController: DesktopWindowController(),
            onUpdateAvailable: reportedUpdates.add,
            child: const Scaffold(body: Text('Home is visible')),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(reportedUpdates, isEmpty);

      updateService.update = higher;
      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: AppUpdateGate(
            releaseBucketUrl: 'https://os.example.test/gang-chat?refresh=1',
            currentVersion: '0.5.0',
            autoUpdatePromptStore: store,
            updateService: updateService,
            platformOverride: AppUpdatePlatform.windows,
            windowController: DesktopWindowController(),
            onUpdateAvailable: reportedUpdates.add,
            child: const Scaffold(body: Text('Home is visible')),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(reportedUpdates, [same(higher)]);
    },
  );

  testWidgets('update gate keeps child when auto prompt is disabled', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: AppUpdateGate(
          releaseBucketUrl: 'https://os.example.test/gang-chat',
          currentVersion: '0.5.0',
          autoUpdatePromptStore: const _FakeAutoUpdatePromptStore(false),
          updateService: _FakeReleaseUpdateService(null),
          platformOverride: AppUpdatePlatform.windows,
          windowController: DesktopWindowController(),
          child: const Scaffold(body: Text('Home is visible')),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('发现新版本'), findsNothing);
    expect(find.text('Home is visible'), findsOneWidget);
  });
}

class _FakeReleaseUpdateService extends ReleaseUpdateService {
  _FakeReleaseUpdateService(this.update);

  final AvailableAppUpdate? update;
  AppUpdatePlatform? requestedPlatform;

  @override
  Future<AvailableAppUpdate?> checkForUpdate({
    required String bucketUrl,
    required String currentVersion,
    required AppUpdatePlatform platform,
  }) async {
    requestedPlatform = platform;
    return update;
  }
}

class _MutableFakeReleaseUpdateService extends ReleaseUpdateService {
  _MutableFakeReleaseUpdateService(this.update);

  AvailableAppUpdate? update;

  @override
  Future<AvailableAppUpdate?> checkForUpdate({
    required String bucketUrl,
    required String currentVersion,
    required AppUpdatePlatform platform,
  }) async {
    return update;
  }
}

class _FakeAutoUpdatePromptStore extends AutoUpdatePromptStore {
  const _FakeAutoUpdatePromptStore(this.enabled, {this.ignoredVersion});

  final bool enabled;
  final String? ignoredVersion;

  @override
  Future<bool> read() async => enabled;

  @override
  Future<void> write(bool enabled) async {}

  @override
  Future<String?> readIgnoredVersion() async => ignoredVersion;

  @override
  Future<void> writeIgnoredVersion(String? version) async {}
}
