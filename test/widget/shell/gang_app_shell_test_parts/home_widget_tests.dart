part of '../gang_app_shell_test.dart';

void registerShellHomeWidgetTests() {
  testWidgets('authenticated home shell renders server-only sidebar', (
    WidgetTester tester,
  ) async {
    final requestedPaths = <String>[];

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
        home: HomePage(
          app: _homeTestAppContext(
            requestedPaths: requestedPaths,
            pinAlphaRoom: true,
          ),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(requestedPaths, contains('/api/v1/rooms'));
    expect(find.byType(ui.Sidebar), findsNothing);
    expect(find.text('Gang Chat'), findsNothing);
    expect(find.byKey(const ValueKey('home-title-search')), findsOneWidget);
    expect(find.byTooltip('最小化'), findsOneWidget);
    expect(find.byTooltip('最大化'), findsOneWidget);
    expect(find.byTooltip('关闭'), findsOneWidget);
    expect(find.text('Workspace'), findsNothing);
    expect(find.text('Tools'), findsNothing);
    expect(find.text('Rooms'), findsNothing);
    expect(find.text('Activity'), findsNothing);
    expect(find.text('People'), findsNothing);
    expect(find.text('Files'), findsNothing);
    expect(find.text('设置'), findsNothing);
    expect(find.byTooltip('创建房间'), findsOneWidget);
    expect(find.byTooltip('通知'), findsOneWidget);
    expect(find.byTooltip('设置'), findsOneWidget);
    expect(find.byTooltip('退出登录'), findsOneWidget);
    expect(find.text('Kai'), findsOneWidget);
    expect(find.text('在线'), findsOneWidget);
    expect(find.text('@kai'), findsNothing);
    expect(find.text('Alpha Room'), findsOneWidget);
    expect(find.text('Beta Room'), findsOneWidget);
    expect(find.text('2 名成员 · 1 人语音'), findsOneWidget);
    expect(find.text('5 名成员'), findsOneWidget);
    expect(find.text('3'), findsOneWidget);
    final alphaLiveIndicator = find.byKey(
      const ValueKey('home-sidebar-room-live-server-alpha'),
    );
    final betaLiveIndicator = find.byKey(
      const ValueKey('home-sidebar-room-live-server-beta'),
    );
    expect(alphaLiveIndicator, findsOneWidget);
    expect(betaLiveIndicator, findsNothing);
    expect(tester.widget<Icon>(alphaLiveIndicator).color, Colors.white);

    final searchRect = tester.getRect(
      find.byKey(const ValueKey('home-title-search')),
    );
    expect(searchRect.center.dx, closeTo(400, 0.01));
    expect(searchRect.width, closeTo(520, 0.01));
    expect(searchRect.height, closeTo(30, 0.01));

    final userSummaryRect = tester.getRect(
      find.byKey(const ValueKey('home-sidebar-user-summary')),
    );
    final userSummaryAvatar = tester.widget<ui.Avatar>(
      find.descendant(
        of: find.byKey(const ValueKey('home-sidebar-user-summary')),
        matching: find.byType(ui.Avatar),
      ),
    );
    final displayNameRect = tester.getRect(find.text('Kai'));
    final statusRect = tester.getRect(find.text('在线'));
    final statusDotRect = tester.getRect(
      find.byKey(const ValueKey('home-sidebar-presence-dot')),
    );
    expect(userSummaryAvatar.showBorder, isFalse);
    expect(statusRect.top, greaterThan(displayNameRect.bottom));
    expect(statusRect.left, greaterThan(userSummaryRect.left));
    expect(statusRect.right, lessThan(userSummaryRect.right));
    expect(statusDotRect.width, closeTo(6, 0.01));
    expect(statusDotRect.height, closeTo(6, 0.01));
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('home-sidebar-user-summary')),
        matching: find.byType(ui.PresencePill),
      ),
      findsNothing,
    );

    final alphaCardRect = tester.getRect(
      find.ancestor(
        of: find.text('Alpha Room'),
        matching: find.byType(ui.PressableSurface),
      ),
    );
    final alphaPinnedRect = tester.getRect(
      find.byKey(const ValueKey('home-sidebar-room-pinned-server-alpha')),
    );
    final alphaAvatarRect = tester.getRect(
      find.descendant(
        of: find.byKey(const ValueKey('home-sidebar-room-server-alpha')),
        matching: find.byType(ui.Avatar),
      ),
    );
    expect(userSummaryRect.right - alphaCardRect.right, closeTo(0, 0.01));
    expect(alphaPinnedRect.left, lessThan(alphaAvatarRect.left));
    expect(alphaPinnedRect.left, closeTo(alphaCardRect.left + 2, 1));
    expect(alphaPinnedRect.top, lessThan(alphaAvatarRect.top));
    expect(alphaPinnedRect.top, lessThan(alphaCardRect.top + 8));

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();

    expect(requestedPaths, contains('/api/v1/rooms/server-alpha'));
    expect(requestedPaths, contains('/api/v1/rooms/server-alpha/messages'));
    expect(requestedPaths, contains('/api/v1/rooms/server-alpha/live'));
    expect(requestedPaths, contains('/api/v1/rooms/server-alpha/read'));
    expect(find.text('Hello from Morgan'), findsOneWidget);
    expect(find.text('Reply from Kai'), findsOneWidget);
    expect(find.text('未读消息'), findsOneWidget);
    expect(find.byKey(const ValueKey('chat-jump-to-first-new')), findsNothing);
    expect(find.text('3'), findsNothing);
    expect(find.byType(ui.ChatComposer), findsOneWidget);
    expect(
      tester
          .widget<ui.PressableSurface>(
            find.byKey(const ValueKey('home-sidebar-room-server-alpha')),
          )
          .selected,
      isTrue,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('detected app update opens settings update page in home shell', (
    WidgetTester tester,
  ) async {
    final update = AvailableAppUpdate(
      currentVersion: '0.4.0',
      latestVersion: '0.4.1',
      asset: ReleaseAsset(
        key: 'releases/GangChat_v0.4.1.exe',
        version: '0.4.1',
        platform: AppUpdatePlatform.windows,
        releasedAt: DateTime.utc(2026, 7, 8, 1, 2),
      ),
      downloadUrl: Uri.parse(
        'https://os.example.test/gang-chat/releases/GangChat_v0.4.1.exe',
      ),
    );
    var shownCount = 0;

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(),
          realtime: _NoopRealtimeService(),
          detectedAppUpdate: update,
          onDetectedAppUpdateShown: () => shownCount += 1,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(shownCount, 1);
    expect(find.text('发现新版本'), findsOneWidget);
    expect(find.text('v0.4.1'), findsWidgets);
    expect(find.text('版本日志'), findsOneWidget);
    expect(find.widgetWithText(ui.Button, '继续使用'), findsNothing);

    await tester.tap(find.byTooltip('返回'));
    await tester.pumpAndSettle();

    expect(find.text('发现新版本'), findsNothing);
    expect(find.text('设置'), findsOneWidget);
    expect(find.text('关于Gang Chat'), findsOneWidget);
    expect(find.text('版本信息'), findsOneWidget);
  });

  testWidgets(
    'authenticated home shell keeps search in every non-narrow title bar',
    (WidgetTester tester) async {
      Future<Size> pumpHomeAtWidth(double width) async {
        await tester.pumpWidget(
          MaterialApp(
            theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
            home: Center(
              child: SizedBox(
                width: width,
                height: 620,
                child: HomePage(
                  app: _homeTestAppContext(),
                  realtime: _NoopRealtimeService(),
                ),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        return tester.getSize(find.byKey(const ValueKey('home-title-search')));
      }

      final wideSize = await pumpHomeAtWidth(1180);
      final resizedSize = await pumpHomeAtWidth(900);

      expect(wideSize.width, closeTo(520, 0.01));
      expect(wideSize.height, closeTo(30, 0.01));
      expect(resizedSize.width, closeTo(wideSize.width, 0.01));
      expect(resizedSize.height, closeTo(wideSize.height, 0.01));

      final compactSize = await pumpHomeAtWidth(760);
      final compactSearchRect = tester.getRect(
        find.byKey(const ValueKey('home-title-search')),
      );
      final userSummaryRect = tester.getRect(
        find.byKey(const ValueKey('home-sidebar-user-summary')),
      );
      expect(compactSize.width, lessThanOrEqualTo(wideSize.width));
      expect(compactSize.width, greaterThanOrEqualTo(500));
      expect(compactSize.height, closeTo(wideSize.height, 0.01));
      expect(compactSearchRect.bottom, lessThan(userSummaryRect.top));
      expect(
        compactSearchRect.right,
        lessThan(tester.getRect(find.byTooltip('最小化')).left),
      );

      final compactSearchField = find.descendant(
        of: find.byKey(const ValueKey('home-title-search')),
        matching: find.byType(TextField),
      );
      await tester.enterText(compactSearchField, 'Beta');
      await tester.pump(const Duration(milliseconds: 320));
      await tester.pumpAndSettle();

      final searchResultsRect = tester.getRect(
        find.byKey(const ValueKey('home-title-search-results')),
      );
      expect(searchResultsRect.left, closeTo(compactSearchRect.left, 0.01));
      expect(searchResultsRect.width, closeTo(compactSearchRect.width, 0.01));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'authenticated home shell title live room module controls joined voice',
    (WidgetTester tester) async {
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      final requestedPaths = <String>[];
      final liveSession = _FakeLiveSession();
      final liveSessionController = _FakeLiveSessionController(
        session: liveSession,
      );
      final app = _homeTestAppContext(requestedPaths: requestedPaths);

      Future<void> pumpHomeAtWidth(double width) async {
        tester.view.physicalSize = Size(width, 620);
        await tester.pumpWidget(
          MaterialApp(
            theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
            home: SizedBox(
              width: width,
              height: 620,
              child: HomePage(
                app: app,
                audioDeviceStore: const _FakeAudioDeviceStore(),
                liveSessionController: liveSessionController,
                realtime: _NoopRealtimeService(),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
      }

      await pumpHomeAtWidth(1180);
      final idleDock = find.byKey(
        const ValueKey<String>('home-title-live-room'),
      );
      expect(idleDock, findsOneWidget);
      expect(
        find.descendant(of: idleDock, matching: find.text('未加入语音频道')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: idleDock, matching: find.byType(ui.Avatar)),
        findsNothing,
      );
      expect(
        find.descendant(of: idleDock, matching: find.byIcon(Icons.volume_up)),
        findsNothing,
      );
      expect(
        find.descendant(of: idleDock, matching: find.byIcon(Icons.call_end)),
        findsNothing,
      );

      await tester.tap(find.text('Alpha Room'));
      await tester.pumpAndSettle();
      await _openLiveChannelFromHeader(tester);
      await tester.tap(find.widgetWithText(ui.Button, '加入'));
      await tester.pumpAndSettle();

      final dock = find.byKey(const ValueKey<String>('home-title-live-room'));
      final search = find.byKey(const ValueKey('home-title-search'));
      final roomCard = find.byKey(
        const ValueKey('home-sidebar-room-server-alpha'),
      );
      expect(dock, findsOneWidget);
      final roomCardWidth = tester.getSize(roomCard).width;
      final expandedDockWidth = tester.getSize(dock).width;
      expect(expandedDockWidth, greaterThan(roomCardWidth));
      expect(tester.getSize(dock).height, closeTo(30, 0.01));
      expect(
        tester.getRect(dock).right,
        lessThanOrEqualTo(tester.getRect(search).left - 14),
      );
      expect(
        find.descendant(of: dock, matching: find.text('Alpha Room')),
        findsOneWidget,
      );
      final truncatedDockRoomName = find.descendant(
        of: dock,
        matching: find.byWidgetPredicate((widget) {
          if (widget is! Text) return false;
          final text = widget.data ?? '';
          return text.length > 3 && text.endsWith('...');
        }),
      );
      expect(truncatedDockRoomName, findsNothing);
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.volume_up)),
        findsOneWidget,
      );
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.mic)),
        findsOneWidget,
      );
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.headphones)),
        findsOneWidget,
      );
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.call_end)),
        findsOneWidget,
      );

      await tester.tap(
        find.byKey(const ValueKey<String>('home-title-live-room:mic')),
      );
      await tester.pumpAndSettle();

      expect(liveSession.inputVolumes.last, 0.0);
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.mic_off)),
        findsOneWidget,
      );

      await tester.tap(
        find.byKey(const ValueKey<String>('home-title-live-room:headphones')),
      );
      await tester.pumpAndSettle();

      expect(liveSession.outputVolumes.last, 0.0);
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.headset_off)),
        findsOneWidget,
      );

      await pumpHomeAtWidth(900);
      expect(dock, findsOneWidget);
      expect(tester.getSize(dock).width, closeTo(166, 0.01));
      expect(
        find.descendant(of: dock, matching: find.text('Alpha Room')),
        findsNothing,
      );
      expect(truncatedDockRoomName, findsNothing);
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.volume_up)),
        findsOneWidget,
      );
      expect(
        find.descendant(of: dock, matching: find.byType(ui.Avatar)),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: dock,
          matching: find.byKey(
            const ValueKey<String>('home-title-live-room:mic'),
          ),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: dock,
          matching: find.byKey(
            const ValueKey<String>('home-title-live-room:headphones'),
          ),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: dock,
          matching: find.byKey(
            const ValueKey<String>('home-title-live-room:leave'),
          ),
        ),
        findsOneWidget,
      );

      await pumpHomeAtWidth(850);
      expect(dock, findsOneWidget);
      expect(tester.getSize(dock).width, closeTo(141, 0.01));
      expect(
        find.descendant(of: dock, matching: find.text('Alpha Room')),
        findsNothing,
      );
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.volume_up)),
        findsOneWidget,
      );
      expect(
        find.descendant(of: dock, matching: find.byType(ui.Avatar)),
        findsOneWidget,
      );

      await pumpHomeAtWidth(760);
      expect(dock, findsOneWidget);
      expect(tester.getSize(dock).width, closeTo(80, 0.01));
      expect(
        find.descendant(of: dock, matching: find.text('Alpha Room')),
        findsNothing,
      );
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.volume_up)),
        findsNothing,
      );
      expect(
        find.descendant(of: dock, matching: find.byType(ui.Avatar)),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: dock,
          matching: find.byKey(
            const ValueKey<String>('home-title-live-room:leave'),
          ),
        ),
        findsOneWidget,
      );

      await pumpHomeAtWidth(680);
      expect(dock, findsOneWidget);
      expect(tester.getSize(dock).width, closeTo(expandedDockWidth, 0.01));
      expect(tester.getRect(dock).left, lessThan(20));
      expect(
        find.descendant(of: dock, matching: find.text('Alpha Room')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.volume_up)),
        findsOneWidget,
      );
      expect(
        tester.getRect(dock).right,
        lessThan(tester.getRect(find.byTooltip('最小化')).left),
      );

      await pumpHomeAtWidth(1180);
      expect(dock, findsOneWidget);
      expect(tester.getSize(dock).width, closeTo(expandedDockWidth, 0.01));
      expect(tester.getSize(dock).height, closeTo(30, 0.01));

      await tester.tap(find.text('Beta Room'));
      await tester.pumpAndSettle();
      expect(_liveControl('leave'), findsNothing);
      expect(find.byType(live_pane.LiveChannelPane), findsNothing);

      final alphaDetailsBeforeLeave = requestedPaths
          .where((path) => path == '/api/v1/rooms/server-alpha')
          .length;
      await tester.tap(
        find.byKey(const ValueKey<String>('home-title-live-room:leave')),
      );
      await tester.pumpAndSettle();

      expect(liveSession.disconnects, 1);
      expect(dock, findsOneWidget);
      expect(
        find.descendant(of: dock, matching: find.text('未加入语音频道')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: dock, matching: find.byType(ui.Avatar)),
        findsNothing,
      );
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.volume_up)),
        findsNothing,
      );
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.call_end)),
        findsNothing,
      );
      expect(_liveControl('leave'), findsNothing);
      expect(find.byType(live_pane.LiveChannelPane), findsNothing);
      expect(find.text('Beta Room'), findsWidgets);
      expect(
        requestedPaths
            .where((path) => path == '/api/v1/rooms/server-alpha')
            .length,
        alphaDetailsBeforeLeave,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('title live room default width shares the sidebar center', (
    WidgetTester tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(1180, 620);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);
    final liveSessionController = _FakeLiveSessionController(
      session: _FakeLiveSession(),
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
        home: SizedBox(
          width: 1180,
          height: 620,
          child: HomePage(
            app: _homeTestAppContext(alphaRoomName: 'A'),
            audioDeviceStore: const _FakeAudioDeviceStore(),
            liveSessionController: liveSessionController,
            realtime: _NoopRealtimeService(),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final roomCard = find.byKey(
      const ValueKey('home-sidebar-room-server-alpha'),
    );
    final roomCardWidth = tester.getSize(roomCard).width;
    await tester.tap(roomCard);
    await tester.pumpAndSettle();
    await _openLiveChannelFromHeader(tester);
    await tester.tap(find.widgetWithText(ui.Button, '加入'));
    await tester.pumpAndSettle();

    final dock = find.byKey(const ValueKey<String>('home-title-live-room'));
    final search = find.byKey(const ValueKey('home-title-search'));
    expect(tester.getSize(dock).width, greaterThan(roomCardWidth));
    expect(
      tester.getRect(dock).center.dx,
      closeTo(tester.getRect(roomCard).center.dx, 0.01),
    );
    expect(
      find.descendant(
        of: dock,
        matching: find.byWidgetPredicate(
          (widget) =>
              widget is Text &&
              widget.data == 'A' &&
              widget.overflow == TextOverflow.clip,
        ),
      ),
      findsOneWidget,
    );
    expect(
      tester.getRect(dock).right,
      lessThanOrEqualTo(tester.getRect(search).left - 14),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'persistent live dock configures audio before join and remembers it after leaving',
    (WidgetTester tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(1180, 620);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      final liveJoinRequests = <Map<String, Object?>>[];
      final liveStateUpdates = <Map<String, Object?>>[];
      final liveSession = _FakeLiveSession();

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
          home: HomePage(
            app: _homeTestAppContext(
              liveJoinRequests: liveJoinRequests,
              liveStateUpdates: liveStateUpdates,
            ),
            audioDeviceStore: const _FakeAudioDeviceStore(),
            liveSessionController: _FakeLiveSessionController(
              session: liveSession,
            ),
            realtime: _NoopRealtimeService(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      final dock = find.byKey(const ValueKey<String>('home-title-live-room'));
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.mic)),
        findsOneWidget,
      );
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.headphones)),
        findsOneWidget,
      );

      await tester.tap(
        find.byKey(const ValueKey<String>('home-title-live-room:headphones')),
      );
      await tester.pumpAndSettle();
      expect(liveStateUpdates, isEmpty);
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.mic_off)),
        findsOneWidget,
      );
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.headset_off)),
        findsOneWidget,
      );

      await tester.tap(find.text('Alpha Room'));
      await tester.pumpAndSettle();
      await _openLiveChannelFromHeader(tester);
      await tester.tap(find.widgetWithText(ui.Button, '加入'));
      await tester.pumpAndSettle();

      expect(liveJoinRequests.single['mic_muted'], true);
      expect(liveJoinRequests.single['headphones_muted'], true);
      expect(liveStateUpdates.last['mic_muted'], true);
      expect(liveStateUpdates.last['headphones_muted'], true);

      await tester.tap(_liveControl('leave'));
      await tester.pumpAndSettle();
      expect(
        find.descendant(of: dock, matching: find.text('未加入语音频道')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.mic_off)),
        findsOneWidget,
      );
      expect(
        find.descendant(of: dock, matching: find.byIcon(Icons.headset_off)),
        findsOneWidget,
      );

      await tester.tap(find.widgetWithText(ui.Button, '加入'));
      await tester.pumpAndSettle();
      expect(liveSession.connectAttempts, 2);
      expect(liveJoinRequests.last['mic_muted'], true);
      expect(liveJoinRequests.last['headphones_muted'], true);
      expect(liveStateUpdates.last['mic_muted'], true);
      expect(liveStateUpdates.last['headphones_muted'], true);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('logging out resets persistent live audio choices', (
    WidgetTester tester,
  ) async {
    var logoutCount = 0;
    final liveSession = _FakeLiveSession();
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
        home: HomePage(
          app: _homeTestAppContext(onLogout: () async => logoutCount++),
          audioDeviceStore: const _FakeAudioDeviceStore(),
          liveSessionController: _FakeLiveSessionController(
            session: liveSession,
          ),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey<String>('home-title-live-room:headphones')),
    );
    await tester.pumpAndSettle();
    expect(liveSession.micMutes.last, isTrue);
    expect(liveSession.outputMutes.last, isTrue);

    await tester.tap(find.byTooltip('退出登录'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ui.Button, '退出登录'));
    await tester.pumpAndSettle();

    expect(logoutCount, 1);
    expect(liveSession.micMutes.last, isFalse);
    expect(liveSession.outputMutes.last, isFalse);
    expect(liveSession.inputVolumes.last, greaterThan(0));
    expect(liveSession.outputVolumes.last, greaterThan(0));
    expect(tester.takeException(), isNull);
  });

  testWidgets('exiting the program resets persistent live audio choices', (
    WidgetTester tester,
  ) async {
    var exitSessionCount = 0;
    final windowEvents = <String>[];
    final windowController = _RecordingWindowController(windowEvents);
    final liveSession = _FakeLiveSession();
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
        home: HomePage(
          app: _homeTestAppContext(
            onExitSessionForAppExit: () async => exitSessionCount++,
          ),
          audioDeviceStore: const _FakeAudioDeviceStore(),
          liveSessionController: _FakeLiveSessionController(
            session: liveSession,
          ),
          realtime: _NoopRealtimeService(),
          closeBehaviorStore: const _FixedCloseBehaviorStore(
            CloseBehavior.exitProgram,
          ),
          windowController: windowController,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey<String>('home-title-live-room:headphones')),
    );
    await tester.pumpAndSettle();
    expect(liveSession.micMutes.last, isTrue);
    expect(liveSession.outputMutes.last, isTrue);

    final closeFuture = windowController.closeRequestHandler!();
    await tester.pumpAndSettle();
    expect(await closeFuture, isTrue);

    expect(exitSessionCount, 1);
    expect(liveSession.micMutes.last, isFalse);
    expect(liveSession.outputMutes.last, isFalse);
    expect(liveSession.inputVolumes.last, greaterThan(0));
    expect(liveSession.outputVolumes.last, greaterThan(0));
    expect(windowEvents, containsAllInOrder(['hide', 'terminate']));
    expect(tester.takeException(), isNull);
  });

  testWidgets('title live room leaves paint safety for a long room name', (
    WidgetTester tester,
  ) async {
    const roomName = '特别长长长长长长长长长长长的房间名';
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(1600, 620);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);
    final liveSessionController = _FakeLiveSessionController(
      session: _FakeLiveSession(),
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
        home: SizedBox(
          width: 1600,
          height: 620,
          child: HomePage(
            app: _homeTestAppContext(alphaRoomName: roomName),
            audioDeviceStore: const _FakeAudioDeviceStore(),
            liveSessionController: liveSessionController,
            realtime: _NoopRealtimeService(),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey('home-sidebar-room-server-alpha')),
    );
    await tester.pumpAndSettle();
    await _openLiveChannelFromHeader(tester);
    await tester.tap(find.widgetWithText(ui.Button, '加入'));
    await tester.pumpAndSettle();

    final dock = find.byKey(const ValueKey<String>('home-title-live-room'));
    final roomNameFinder = find.descendant(
      of: dock,
      matching: find.text(roomName),
    );
    final roomNameWidget = tester.widget<Text>(roomNameFinder);
    expect(roomNameWidget.style?.inherit, isFalse);
    final painter = TextPainter(
      text: TextSpan(text: roomName, style: roomNameWidget.style),
      maxLines: 1,
      textDirection: TextDirection.ltr,
    )..layout();
    expect(
      tester.getSize(roomNameFinder).width,
      greaterThanOrEqualTo(painter.width.ceilToDouble() + 2),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('title live room scrolls a constrained room name both ways', (
    WidgetTester tester,
  ) async {
    const roomName = '特别长长长长长长长长长长长的房间名';
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(930, 620);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
        home: HomePage(
          app: _homeTestAppContext(alphaRoomName: roomName),
          audioDeviceStore: const _FakeAudioDeviceStore(),
          liveSessionController: _FakeLiveSessionController(
            session: _FakeLiveSession(),
          ),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey('home-sidebar-room-server-alpha')),
    );
    await tester.pumpAndSettle();
    await _openLiveChannelFromHeader(tester);
    await tester.tap(find.widgetWithText(ui.Button, '加入'));
    await tester.pumpAndSettle();

    final dock = find.byKey(const ValueKey<String>('home-title-live-room'));
    expect(
      find.descendant(
        of: dock,
        matching: find.byKey(
          const ValueKey<String>('home-title-live-room:name'),
        ),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(of: dock, matching: find.text(roomName)),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: dock,
        matching: find.byWidgetPredicate(
          (widget) => widget is Text && (widget.data ?? '').endsWith('...'),
        ),
      ),
      findsNothing,
    );
    final marqueeTrack = find.byKey(
      const ValueKey<String>('home-title-live-room:marquee-track'),
    );
    expect(marqueeTrack, findsOneWidget);
    await tester.pump(const Duration(milliseconds: 900));
    await tester.pump(const Duration(milliseconds: 400));
    final firstOffset = tester.widget<Transform>(marqueeTrack).transform[12];
    await tester.pump(const Duration(milliseconds: 400));
    final secondOffset = tester.widget<Transform>(marqueeTrack).transform[12];
    expect(firstOffset, lessThan(0));
    expect(secondOffset, lessThan(firstOffset));
    await tester.pump(const Duration(seconds: 12));
    final completedOffset = tester
        .widget<Transform>(marqueeTrack)
        .transform[12];
    expect(completedOffset, lessThan(secondOffset));
    await tester.pump(const Duration(milliseconds: 900));
    await tester.pump(const Duration(milliseconds: 400));
    final reversingOffset = tester
        .widget<Transform>(marqueeTrack)
        .transform[12];
    expect(reversingOffset, greaterThan(completedOffset));
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'authenticated home shell title live room module waits for room before switching',
    (WidgetTester tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(1180, 620);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      final requestedPaths = <String>[];
      var holdAlphaDetail = false;
      final alphaDetailGate = Completer<void>();
      final liveSession = _FakeLiveSession();
      final liveSessionController = _FakeLiveSessionController(
        session: liveSession,
      );
      final app = _homeTestAppContext(
        requestedPaths: requestedPaths,
        beforeRoomDetailResponse: (roomId) async {
          if (roomId == 'server-alpha' && holdAlphaDetail) {
            await alphaDetailGate.future;
          }
        },
      );

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: SizedBox(
            width: 1180,
            height: 620,
            child: HomePage(
              app: app,
              audioDeviceStore: const _FakeAudioDeviceStore(),
              liveSessionController: liveSessionController,
              realtime: _NoopRealtimeService(),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Alpha Room'));
      await tester.pumpAndSettle();
      await _openLiveChannelFromHeader(tester);
      await tester.tap(find.widgetWithText(ui.Button, '加入'));
      await tester.pumpAndSettle();

      final dock = find.byKey(const ValueKey<String>('home-title-live-room'));
      expect(dock, findsOneWidget);

      await tester.tap(find.text('Beta Room'));
      await tester.pumpAndSettle();
      expect(_liveControl('leave'), findsNothing);
      expect(find.text('Beta Room'), findsWidgets);

      final alphaDetailsBeforeOpen = requestedPaths
          .where((path) => path == '/api/v1/rooms/server-alpha')
          .length;
      holdAlphaDetail = true;
      await tester.tap(dock);
      await tester.pump();

      expect(_liveControl('leave'), findsNothing);
      expect(find.text('Beta Room'), findsWidgets);

      alphaDetailGate.complete();
      await tester.pumpAndSettle();

      expect(_liveControl('leave'), findsOneWidget);
      expect(find.text('Morgan'), findsOneWidget);
      expect(
        requestedPaths
            .where((path) => path == '/api/v1/rooms/server-alpha')
            .length,
        greaterThan(alphaDetailsBeforeOpen),
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'authenticated macOS title live room module follows native controls',
    (WidgetTester tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(1180, 620);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      final liveSession = _FakeLiveSession();
      final liveSessionController = _FakeLiveSessionController(
        session: liveSession,
      );
      final app = _homeTestAppContext();

      Future<void> pumpHomeAtWidth(double width) async {
        tester.view.physicalSize = Size(width, 620);
        await tester.pumpWidget(
          MaterialApp(
            theme: ui.uiTheme().copyWith(platform: TargetPlatform.macOS),
            home: SizedBox(
              width: width,
              height: 620,
              child: HomePage(
                app: app,
                audioDeviceStore: const _FakeAudioDeviceStore(),
                liveSessionController: liveSessionController,
                realtime: _NoopRealtimeService(),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
      }

      await pumpHomeAtWidth(1180);

      await tester.tap(find.text('Alpha Room'));
      await tester.pumpAndSettle();
      await _openLiveChannelFromHeader(tester);
      await tester.tap(find.widgetWithText(ui.Button, '加入'));
      await tester.pumpAndSettle();

      final dock = find.byKey(const ValueKey<String>('home-title-live-room'));
      final search = find.byKey(const ValueKey('home-title-search'));
      expect(dock, findsOneWidget);
      final searchConstrainedDockWidth = tester.getSize(dock).width;
      expect(tester.getRect(dock).left, closeTo(81, 1));
      expect(
        tester.getRect(dock).right,
        closeTo(tester.getRect(search).left - 14, 0.01),
      );
      expect(find.byTooltip('最小化'), findsNothing);
      expect(find.byTooltip('最大化'), findsNothing);
      expect(find.byTooltip('关闭'), findsNothing);

      await pumpHomeAtWidth(600);
      expect(dock, findsOneWidget);
      expect(
        tester.getSize(dock).width,
        greaterThan(searchConstrainedDockWidth),
      );
      expect(tester.getRect(dock).left, closeTo(81, 1));
      expect(
        find.descendant(of: dock, matching: find.text('Alpha Room')),
        findsOneWidget,
      );
      expect(find.byTooltip('最小化'), findsNothing);
      expect(find.byTooltip('最大化'), findsNothing);
      expect(find.byTooltip('关闭'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'authenticated Android narrow room list places live room module in footer',
    (WidgetTester tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(1180, 620);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      final liveSession = _FakeLiveSession();
      final liveSessionController = _FakeLiveSessionController(
        session: liveSession,
      );
      final app = _homeTestAppContext();

      Future<void> pumpHomeAtWidth(double width) async {
        tester.view.physicalSize = Size(width, 620);
        await tester.pumpWidget(
          MaterialApp(
            theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
            home: SizedBox(
              width: width,
              height: 620,
              child: HomePage(
                app: app,
                audioDeviceStore: const _FakeAudioDeviceStore(),
                liveSessionController: liveSessionController,
                realtime: _NoopRealtimeService(),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
      }

      await pumpHomeAtWidth(1180);
      await tester.tap(find.text('Alpha Room'));
      await tester.pumpAndSettle();
      await _openLiveChannelFromHeader(tester);
      await tester.tap(find.widgetWithText(ui.Button, '加入'));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey<String>('home-title-live-room')),
        findsOneWidget,
      );

      await pumpHomeAtWidth(360);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();

      final dock = find.byKey(const ValueKey<String>('home-title-live-room'));
      final notifications = find.byKey(
        const ValueKey('home-sidebar-notifications-button'),
      );
      final settings = find.byKey(
        const ValueKey('home-sidebar-settings-button'),
      );
      expect(notifications, findsOneWidget);
      expect(settings, findsOneWidget);
      expect(dock, findsOneWidget);
      expect(
        tester.getRect(dock).left,
        greaterThan(tester.getRect(notifications).right),
      );
      expect(
        tester.getRect(dock).right,
        lessThan(tester.getRect(settings).left),
      );
      expect(
        tester.getRect(dock).left - tester.getRect(notifications).right,
        closeTo(8, 0.01),
      );
      expect(
        tester.getRect(settings).left - tester.getRect(dock).right,
        closeTo(8, 0.01),
      );
      expect(
        find.descendant(of: dock, matching: find.text('Alpha Room')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: dock, matching: find.byType(ui.Avatar)),
        findsOneWidget,
      );

      await tester.tap(
        find.byKey(const ValueKey<String>('home-title-live-room:mic')),
      );
      await tester.pumpAndSettle();
      expect(liveSession.inputVolumes.last, 0.0);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'authenticated home shell search category filters sidebar rooms',
    (WidgetTester tester) async {
      final requestedPaths = <String>[];

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: HomePage(
            app: _homeTestAppContext(requestedPaths: requestedPaths),
            realtime: _NoopRealtimeService(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      final searchField = find.descendant(
        of: find.byKey(const ValueKey('home-title-search')),
        matching: find.byType(TextField),
      );
      await tester.enterText(searchField, 'Beta');
      await tester.pump(const Duration(milliseconds: 320));
      await tester.pumpAndSettle();

      expect(requestedPaths, contains('/api/v1/search'));
      expect(
        find.byKey(const ValueKey('home-title-search-results')),
        findsOneWidget,
      );
      expect(find.text('我的房间 1'), findsWidgets);
      expect(find.text('公开房间 1'), findsWidgets);
      expect(find.text('聊天记录 1'), findsWidgets);
      expect(find.text('聊天文件 1'), findsWidgets);
      expect(
        find.byKey(const ValueKey('public-room-action-server-public')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('my-room-action-server-beta')),
        findsOneWidget,
      );
      expect(find.widgetWithText(ui.Button, '进入房间'), findsOneWidget);
      expect(find.widgetWithText(ui.Button, '加入房间'), findsOneWidget);
      expect(find.text('5 名成员'), findsWidgets);
      expect(find.text('2 名成员'), findsWidgets);
      expect(find.text('server-public - 2 名成员'), findsNothing);

      final searchPanel = find.byKey(
        const ValueKey('home-title-search-results'),
      );
      final myRoomAvatar = find.descendant(
        of: searchPanel,
        matching: find.byWidgetPredicate(
          (widget) => widget is ui.Avatar && widget.label == 'Beta Room',
        ),
      );
      expect(myRoomAvatar, findsWidgets);

      final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
      await gesture.addPointer(location: Offset.zero);
      addTearDown(gesture.removePointer);
      await gesture.moveTo(tester.getCenter(myRoomAvatar.first));
      await tester.pumpAndSettle();

      expect(requestedPaths, contains('/api/v1/rooms/server-beta'));
      final creatorAvatar = find.byWidgetPredicate(
        (widget) => widget is ui.Avatar && widget.label == 'Morgan',
      );
      expect(creatorAvatar, findsOneWidget);

      await tester.tap(creatorAvatar);
      await tester.pumpAndSettle();

      expect(
        requestedPaths,
        contains('/api/v1/rooms/server-beta/members/user-2/profile'),
      );
      expect(find.text('@morgan'), findsOneWidget);
      expect(find.text('Creator profile'), findsOneWidget);

      final sharedRoomAvatar = find.byWidgetPredicate(
        (widget) =>
            widget is ui.Avatar &&
            widget.label == 'Alpha Room' &&
            widget.size == 20,
      );
      expect(sharedRoomAvatar, findsOneWidget);

      await tester.tap(sharedRoomAvatar);
      await tester.pumpAndSettle();

      expect(requestedPaths, contains('/api/v1/rooms/server-alpha'));
      expect(find.text('RID: server-alpha'), findsOneWidget);

      final alphaRidRect = tester.getRect(find.text('RID: server-alpha'));
      final alphaEnterButtonElement = find
          .widgetWithText(ui.Button, '进入房间')
          .evaluate()
          .singleWhere((element) {
            final renderObject = element.renderObject;
            if (renderObject is! RenderBox || !renderObject.hasSize) {
              return false;
            }
            final buttonRect =
                renderObject.localToGlobal(Offset.zero) & renderObject.size;
            return buttonRect.top > alphaRidRect.bottom &&
                (buttonRect.center.dx - alphaRidRect.center.dx).abs() < 160;
          });
      await tester.tap(find.byWidget(alphaEnterButtonElement.widget));
      await tester.pumpAndSettle();

      expect(requestedPaths, contains('/api/v1/rooms/server-alpha/messages'));
      expect(
        find.byKey(const ValueKey('home-title-search-results')),
        findsOneWidget,
      );
      expect(find.text('创建者'), findsWidgets);

      await tester.tapAt(const Offset(740, 100));
      await tester.pumpAndSettle();
      await tester.tap(searchField);
      await tester.pumpAndSettle();

      await gesture.moveTo(const Offset(20, 520));
      await tester.pump(const Duration(milliseconds: 200));
      await tester.pumpAndSettle();

      final pathsBeforeSearchRowTap = List<String>.of(requestedPaths);
      await tester.tap(
        find
            .descendant(
              of: find.byKey(const ValueKey('home-title-search-results')),
              matching: find.text('Beta Public'),
            )
            .first,
      );
      await tester.pumpAndSettle();
      expect(requestedPaths, pathsBeforeSearchRowTap);

      await tester.tap(
        find.byKey(const ValueKey('public-room-action-server-public')),
      );
      await tester.pumpAndSettle();

      expect(requestedPaths, contains('/api/v1/rooms/server-public/join'));
      expect(requestedPaths, contains('/api/v1/rooms/server-public'));
      expect(requestedPaths, contains('/api/v1/rooms/server-public/messages'));
      expect(requestedPaths, contains('/api/v1/rooms/server-public/live'));
      expect(
        find.text('History visible immediately after join'),
        findsOneWidget,
      );

      // Selecting the 我的房间 category filters the sidebar to matching rooms.
      await tester.tap(find.byKey(const ValueKey('search-category-myRooms')));
      await tester.pumpAndSettle();

      expect(find.text('Beta Room'), findsWidgets);
      expect(find.text('Alpha Room'), findsNothing);

      // The active category persists across query edits.
      await tester.enterText(searchField, 'Beta R');
      await tester.pump(const Duration(milliseconds: 320));
      await tester.pumpAndSettle();

      expect(find.text('Beta Room'), findsWidgets);
      expect(find.text('Alpha Room'), findsNothing);

      // Re-tapping the active category clears it and restores the full sidebar.
      await tester.tap(find.byKey(const ValueKey('search-category-myRooms')));
      await tester.pumpAndSettle();

      expect(find.text('Alpha Room'), findsOneWidget);
      expect(find.text('Beta Room'), findsWidgets);

      // Closing the dropdown hides the results panel; reopening restores it.
      await tester.tapAt(const Offset(740, 100));
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('home-title-search-results')),
        findsNothing,
      );

      await tester.tap(searchField);
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('home-title-search-results')),
        findsOneWidget,
      );

      expect(find.text('Alpha Room'), findsOneWidget);
      expect(find.text('Beta Room'), findsWidgets);

      await tester.enterText(searchField, '1');
      await tester.pump(const Duration(milliseconds: 320));
      await tester.pumpAndSettle();

      expect(
        find.descendant(
          of: find.byKey(const ValueKey('home-title-search-results')),
          matching: find.byKey(const ValueKey('my-room-action-server-beta')),
        ),
        findsNothing,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'superuser global search replaces public rooms with user settings',
    (WidgetTester tester) async {
      final requestedUris = <Uri>[];
      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: HomePage(
            app: _homeTestAppContext(
              requestedUris: requestedUris,
              currentUserIsSuperuser: true,
            ),
            realtime: _NoopRealtimeService(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      final searchField = find.descendant(
        of: find.byKey(const ValueKey('home-title-search')),
        matching: find.byType(TextField),
      );
      await tester.enterText(searchField, 'ri');
      await tester.pump(const Duration(milliseconds: 320));
      await tester.pumpAndSettle();

      expect(
        requestedUris.any(
          (uri) =>
              uri.path == '/api/v1/search' &&
              uri.queryParameters['categories'] == 'my_rooms,messages,files',
        ),
        isTrue,
      );
      expect(
        requestedUris.any(
          (uri) =>
              uri.path == '/api/v1/users/search' &&
              uri.queryParameters['include_suspended'] == 'true',
        ),
        isTrue,
      );
      expect(find.text('用户设置 5'), findsWidgets);
      expect(find.textContaining('公开房间'), findsNothing);
      expect(find.widgetWithText(ui.Button, '进入设置'), findsNWidgets(5));
      expect(
        find.byKey(const ValueKey('open-user-settings-user-3')),
        findsOneWidget,
      );

      await tester.tap(find.byKey(const ValueKey('open-user-settings-user-3')));
      await tester.pumpAndSettle();

      expect(find.text('Riley 的用户设置'), findsOneWidget);
      final managedSettingsPage = find.byKey(
        const ValueKey('superuser-user-settings-user-3'),
      );
      final settingsNavigation = find
          .descendant(
            of: managedSettingsPage,
            matching: find.byType(ui.SegmentedControl<SettingsSection>),
          )
          .first;
      final selectionMenu = find.descendant(
        of: settingsNavigation,
        matching: find.byTooltip('选择选项'),
      );
      if (selectionMenu.evaluate().isNotEmpty) {
        await tester.tap(selectionMenu);
        await tester.pump(const Duration(milliseconds: 100));
        await tester.tap(find.text('隐私和安全').hitTestable());
      } else {
        await tester.tap(find.text('隐私和安全').hitTestable());
      }
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<ui.SegmentedControl<SettingsSection>>(settingsNavigation)
            .value,
        SettingsSection.security,
      );
      final verticalScrollable = find
          .descendant(
            of: managedSettingsPage,
            matching: find.byWidgetPredicate(
              (widget) =>
                  widget is Scrollable &&
                  axisDirectionToAxis(widget.axisDirection) == Axis.vertical,
            ),
          )
          .first;
      final verticalPosition = tester
          .state<ScrollableState>(verticalScrollable)
          .position;
      while (find.text('新密码').evaluate().isEmpty &&
          verticalPosition.pixels < verticalPosition.maxScrollExtent) {
        verticalPosition.jumpTo(
          (verticalPosition.pixels + 200)
              .clamp(
                verticalPosition.minScrollExtent,
                verticalPosition.maxScrollExtent,
              )
              .toDouble(),
        );
        await tester.pump();
      }
      await tester.pumpAndSettle();
      expect(find.text('当前密码'), findsNothing);
      expect(find.text('新密码'), findsOneWidget);
      expect(find.text('确认新密码'), findsOneWidget);
      final forgotPassword = find.widgetWithText(ui.Button, '忘记密码');
      expect(forgotPassword, findsOneWidget);
      expect(tester.widget<ui.Button>(forgotPassword).onPressed, isNull);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('narrow search result actions reduce horizontal width', (
    WidgetTester tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);

    Future<List<double>> roomActionWidths(double width) async {
      tester.view.physicalSize = Size(width, 620);
      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
          home: SizedBox(
            width: width,
            height: 620,
            child: HomePage(
              key: ValueKey('regular-search-$width'),
              app: _homeTestAppContext(),
              realtime: _NoopRealtimeService(),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      final searchField = find.descendant(
        of: find.byKey(const ValueKey('home-title-search')),
        matching: find.byType(TextField),
      );
      await tester.enterText(searchField, 'Beta');
      await tester.pump(const Duration(milliseconds: 320));
      await tester.pumpAndSettle();

      return [
        tester
            .getSize(find.byKey(const ValueKey('my-room-action-server-beta')))
            .width,
        tester
            .getSize(
              find.byKey(const ValueKey('public-room-action-server-public')),
            )
            .width,
      ];
    }

    Future<double> userSettingsActionWidth(double width) async {
      tester.view.physicalSize = Size(width, 620);
      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
          home: SizedBox(
            width: width,
            height: 620,
            child: HomePage(
              key: ValueKey('superuser-search-$width'),
              app: _homeTestAppContext(currentUserIsSuperuser: true),
              realtime: _NoopRealtimeService(),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      final searchField = find.descendant(
        of: find.byKey(const ValueKey('home-title-search')),
        matching: find.byType(TextField),
      );
      await tester.enterText(searchField, 'ri');
      await tester.pump(const Duration(milliseconds: 320));
      await tester.pumpAndSettle();
      return tester
          .getSize(find.byKey(const ValueKey('open-user-settings-user-3')))
          .width;
    }

    final wideRoomActions = await roomActionWidths(900);
    final narrowRoomActions = await roomActionWidths(360);
    expect(narrowRoomActions[0], lessThan(wideRoomActions[0]));
    expect(narrowRoomActions[1], lessThan(wideRoomActions[1]));

    final wideUserSettingsAction = await userSettingsActionWidth(900);
    final narrowUserSettingsAction = await userSettingsActionWidth(360);
    expect(narrowUserSettingsAction, lessThan(wideUserSettingsAction));
    expect(tester.takeException(), isNull);
  });

  testWidgets('authenticated home shell search scroll loads next cursor page', (
    WidgetTester tester,
  ) async {
    final requestedUris = <Uri>[];

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(requestedUris: requestedUris),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final searchField = find.descendant(
      of: find.byKey(const ValueKey('home-title-search')),
      matching: find.byType(TextField),
    );
    await tester.enterText(searchField, 'Page');
    await tester.pump(const Duration(milliseconds: 320));
    await tester.pumpAndSettle();

    expect(find.text('聊天记录 9'), findsWidgets);
    expect(find.text('聊天记录 8'), findsNothing);
    expect(_highlightedSearchText('Page result 9'), findsNothing);

    await tester.tap(find.byKey(const ValueKey('search-category-messages')));
    await tester.pumpAndSettle();

    final panel = find.byKey(const ValueKey('home-title-search-results'));
    final resultsList = find.descendant(
      of: panel,
      matching: find.byType(ListView),
    );
    expect(resultsList, findsOneWidget);

    await tester.drag(resultsList, const Offset(0, -360));
    await tester.pump();
    await tester.pumpAndSettle();

    final cursorRequests = requestedUris
        .where(
          (uri) =>
              uri.path == '/api/v1/search' &&
              uri.queryParameters['messages_cursor'] == 'message-cursor-8',
        )
        .toList();
    expect(cursorRequests, hasLength(1));
    final cursorRequest = cursorRequests.single;
    expect(cursorRequest.queryParameters['categories'], 'messages');
    expect(
      cursorRequest.queryParameters.containsKey('my_rooms_cursor'),
      isFalse,
    );
    expect(
      cursorRequest.queryParameters.containsKey('public_rooms_cursor'),
      isFalse,
    );
    expect(cursorRequest.queryParameters.containsKey('files_cursor'), isFalse);
    expect(find.text('聊天记录 9'), findsWidgets);
    await tester.drag(resultsList, const Offset(0, -120));
    await tester.pumpAndSettle();
    expect(
      requestedUris.where(
        (uri) =>
            uri.path == '/api/v1/search' &&
            uri.queryParameters['messages_cursor'] == 'message-cursor-8',
      ),
      hasLength(1),
    );
    expect(_highlightedSearchText('Page result 9'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('authenticated home shell creates room from footer template', (
    WidgetTester tester,
  ) async {
    final roomCreations = <Map<String, Object?>>[];

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(roomCreations: roomCreations),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('创建房间'));
    await tester.pumpAndSettle();

    expect(
      tester
          .widget<ui.ButtonIcon>(
            find.byKey(const ValueKey('home-sidebar-create-room-button')),
          )
          .selected,
      isTrue,
    );
    expect(find.text('创建房间'), findsOneWidget);
    await tester.tap(find.byTooltip('创建房间'));
    await tester.pumpAndSettle();

    expect(find.text('创建房间'), findsNothing);
    expect(
      tester
          .widget<ui.ButtonIcon>(
            find.byKey(const ValueKey('home-sidebar-create-room-button')),
          )
          .selected,
      isFalse,
    );

    await tester.tap(find.byTooltip('创建房间'));
    await tester.pumpAndSettle();

    expect(find.text('创建房间'), findsOneWidget);
    expect(find.text('房间信息'), findsOneWidget);
    expect(find.byKey(const ValueKey('room-settings-rid')), findsNothing);
    expect(find.widgetWithText(ui.Button, '确定'), findsOneWidget);
    expect(find.text('保存房间设置'), findsNothing);
    expect(find.text('离开房间'), findsNothing);
    expect(find.text('删除房间'), findsNothing);

    expect(
      tester
          .widget<TextField>(_roomSettingsTextField('name'))
          .decoration
          ?.hintText,
      isEmpty,
    );
    expect(
      tester
          .widget<TextField>(_roomSettingsTextField('description'))
          .decoration
          ?.hintText,
      isEmpty,
    );

    await tester.enterText(_roomSettingsTextField('name'), 'Project Nest');
    await tester.enterText(
      _roomSettingsTextField('description'),
      'A focused room',
    );
    final confirmButton = find.widgetWithText(ui.Button, '确定');
    await tester.ensureVisible(confirmButton);
    await tester.pumpAndSettle();
    await tester.tapAt(tester.getTopLeft(confirmButton) + const Offset(24, 12));
    await tester.pumpAndSettle();

    expect(roomCreations, hasLength(1));
    final body = roomCreations.single;
    expect(body['name'], 'Project Nest');
    expect(body['description'], 'A focused room');
    expect(body['visibility'], 'public');
    expect(body['join_policy'], 'approval_required');
    expect(body.containsKey('ai_voice_announcements_enabled'), isFalse);
    expect(body['default_avatar_key'], 'blue-3');
    expect(find.text('Project Nest'), findsAtLeastNWidgets(1));
    expect(find.text('创建房间'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'authenticated home shell opens empty create room form from room settings',
    (WidgetTester tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: HomePage(
            app: _homeTestAppContext(),
            realtime: _NoopRealtimeService(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Alpha Room'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('房间设置'));
      await tester.pumpAndSettle();

      expect(
        tester
            .widget<TextField>(_roomSettingsTextField('name'))
            .controller
            ?.text,
        'Alpha Room',
      );

      await tester.tap(find.byTooltip('创建房间'));
      await tester.pumpAndSettle();

      expect(find.text('创建房间'), findsOneWidget);
      expect(
        tester
            .widget<TextField>(_roomSettingsTextField('name'))
            .controller
            ?.text,
        isEmpty,
      );
      expect(
        tester
            .widget<TextField>(_roomSettingsTextField('description'))
            .controller
            ?.text,
        isEmpty,
      );
      expect(find.byKey(const ValueKey('room-settings-rid')), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'authenticated home shell opens notifications and reviews invite',
    (WidgetTester tester) async {
      await tester.binding.setSurfaceSize(const Size(2000, 1000));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final requestedUris = <Uri>[];

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
          home: HomePage(
            app: _homeTestAppContext(requestedUris: requestedUris),
            realtime: _NoopRealtimeService(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byTooltip('通知'));
      await tester.pumpAndSettle();

      expect(
        tester
            .widget<ui.ButtonIcon>(
              find.byKey(const ValueKey('home-sidebar-notifications-button')),
            )
            .selected,
        isTrue,
      );
      expect(find.text('通知'), findsOneWidget);
      await tester.tap(find.byTooltip('通知'));
      await tester.pumpAndSettle();

      expect(find.text('通知'), findsNothing);
      expect(
        tester
            .widget<ui.ButtonIcon>(
              find.byKey(const ValueKey('home-sidebar-notifications-button')),
            )
            .selected,
        isFalse,
      );

      await tester.tap(find.byTooltip('通知'));
      await tester.pumpAndSettle();

      expect(find.text('通知'), findsOneWidget);
      expect(find.text('全部'), findsOneWidget);
      expect(find.text('邀请'), findsOneWidget);
      expect(find.text('申请'), findsOneWidget);
      expect(find.text('房间'), findsOneWidget);
      expect(
        find.ancestor(
          of: _textFieldWithHint('搜索通知'),
          matching: find.byType(ui.Input),
        ),
        findsOneWidget,
      );
      expect(find.text('邀请您加入'), findsAtLeastNWidgets(1));
      expect(find.text('已失效'), findsOneWidget);
      expect(find.text('您已申请加入'), findsAtLeastNWidgets(1));
      expect(find.text('批准了您的申请'), findsOneWidget);
      expect(find.textContaining('Morgan Member'), findsOneWidget);
      expect(find.textContaining('成员'), findsWidgets);
      expect(
        find.byKey(const ValueKey('notification-inviter-avatar-invite-alpha')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('notification-room-avatar-invite-alpha')),
        findsOneWidget,
      );
      expect(
        tester
            .widget<ui.Avatar>(
              find.byKey(
                const ValueKey('notification-inviter-avatar-invite-alpha'),
              ),
            )
            .size,
        34,
      );
      for (final key in const [
        'notification-inviter-name-invite-alpha',
        'notification-application-reviewer-name-application-approved',
      ]) {
        expect(
          tester
              .widget<ui.HighlightedText>(find.byKey(ValueKey(key)))
              .style
              ?.fontSize,
          ui.UiTypography.label.fontSize,
        );
      }
      expect(
        find.byKey(const ValueKey('notification-mobile-body-invite-alpha')),
        findsNothing,
      );
      final notificationRects = [
        tester.getRect(
          find.byKey(const ValueKey('notification-time-invite-alpha')),
        ),
        tester.getRect(
          find.byKey(
            const ValueKey('notification-inviter-avatar-invite-alpha'),
          ),
        ),
        tester.getRect(
          find.byKey(const ValueKey('notification-inviter-name-invite-alpha')),
        ),
        tester.getRect(
          find.byKey(const ValueKey('notification-inviter-role-invite-alpha')),
        ),
        tester.getRect(
          find.byKey(const ValueKey('notification-invite-action-invite-alpha')),
        ),
        tester.getRect(
          find.byKey(const ValueKey('notification-room-avatar-invite-alpha')),
        ),
        tester.getRect(
          find.byKey(const ValueKey('notification-room-name-invite-alpha')),
        ),
        tester.getRect(find.byTooltip('接受邀请')),
      ];
      for (var index = 1; index < notificationRects.length; index++) {
        expect(
          notificationRects[index].left,
          greaterThan(notificationRects[index - 1].left),
        );
      }
      expect(
        requestedUris.any(
          (uri) =>
              uri.path == '/api/v1/room-invites' &&
              uri.queryParameters['status'] == 'all',
        ),
        isTrue,
      );
      expect(
        requestedUris.any(
          (uri) =>
              uri.path == '/api/v1/room-applications' &&
              uri.queryParameters['status'] == 'all',
        ),
        isTrue,
      );

      await tester.tap(find.byTooltip('撤回申请'));
      await tester.pumpAndSettle();

      expect(
        requestedUris.any(
          (uri) => uri.path == '/api/v1/room-applications/application-alpha',
        ),
        isTrue,
      );

      await tester.tap(find.byTooltip('接受邀请'));
      await tester.tap(find.byTooltip('接受邀请'), warnIfMissed: false);
      await _pumpUntilFound(tester, find.byType(Dialog));

      expect(find.text('申请加入'), findsOneWidget);
      expect(find.text('您需要等待'), findsOneWidget);
      await tester.tap(find.byTooltip('关闭').last);
      await tester.pumpAndSettle();

      expect(find.text('申请加入'), findsNothing);

      await tester.tap(find.byTooltip('接受邀请'));
      await _pumpUntilFound(tester, find.byType(Dialog));

      expect(find.text('申请加入'), findsOneWidget);
      await tester.tap(find.widgetWithText(ui.Button, '取消'));
      await tester.pumpAndSettle();

      expect(find.text('申请加入'), findsNothing);

      await tester.tap(find.byTooltip('接受邀请'));
      await _pumpUntilFound(tester, find.byType(Dialog));

      expect(find.text('申请加入'), findsOneWidget);
      await tester.enterText(_textFieldWithHint('申请说明'), 'I was invited');
      await tester.tap(find.widgetWithText(ui.Button, '发送申请'));
      await tester.pumpAndSettle();

      expect(
        requestedUris.any(
          (uri) => uri.path == '/api/v1/room-invites/invite-alpha',
        ),
        isTrue,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('room notification row dot clears on notification refresh', (
    WidgetTester tester,
  ) async {
    final requestedPaths = <String>[];

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(
            requestedPaths: requestedPaths,
            includeUnreadRoomNotification: true,
            includeFreshRoomNotificationOnRefresh: true,
          ),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey('home-sidebar-notifications-button')),
    );
    await tester.pumpAndSettle();

    const markerKey = ValueKey('notification-room-event-new-room-event-alpha');
    const freshMarkerKey = ValueKey(
      'notification-room-event-new-room-event-fresh',
    );
    expect(find.byKey(markerKey), findsOneWidget);
    expect(find.byKey(freshMarkerKey), findsNothing);
    expect(requestedPaths, contains('/api/v1/room-notifications/read'));

    await tester.tap(
      find.byKey(const ValueKey('home-notifications-refresh-button')),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(markerKey), findsNothing);
    expect(find.byKey(freshMarkerKey), findsOneWidget);
    expect(
      requestedPaths
          .where((path) => path == '/api/v1/room-notifications')
          .length,
      greaterThanOrEqualTo(2),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('android notifications keep every inline avatar in desktop order', (
    WidgetTester tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(360, 740));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
        home: HomePage(
          app: _homeTestAppContext(includeUnreadRoomNotification: true),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('通知'));
    await tester.pumpAndSettle();

    const bodyKey = ValueKey(
      'notification-mobile-body-room-event-room-event-alpha',
    );
    const timeKey = ValueKey('notification-room-event-time-room-event-alpha');
    expect(find.byKey(bodyKey), findsOneWidget);
    expect(tester.getSize(find.byKey(timeKey)).width, lessThan(82));
    final timeTexts = find.descendant(
      of: find.byKey(timeKey),
      matching: find.byType(Text),
    );
    expect(tester.widgetList<Text>(timeTexts).map((text) => text.data), [
      '2026/06/07',
      '16:00',
    ]);
    final timeParagraphs = tester.renderObjectList<RenderParagraph>(timeTexts);
    expect(
      timeParagraphs.every((paragraph) => !paragraph.didExceedMaxLines),
      isTrue,
    );
    final roomEventCard = find.byKey(
      const ValueKey('notification-row-surface-room-event:room-event-alpha'),
    );
    expect(tester.getRect(roomEventCard).left, lessThan(22));
    final jumpButton = find.descendant(
      of: roomEventCard,
      matching: find.byType(ui.ButtonIcon),
    );
    List<String> avatarKeysIn(Finder card) => tester
        .widgetList<ui.Avatar>(
          find.descendant(of: card, matching: find.byType(ui.Avatar)),
        )
        .map((avatar) => (avatar.key! as ValueKey<String>).value)
        .toList();

    expect(
      avatarKeysIn(
        find.byKey(
          const ValueKey('notification-row-surface-invite:invite-alpha'),
        ),
      ),
      [
        'notification-inviter-avatar-invite-alpha',
        'notification-room-avatar-invite-alpha',
      ],
    );
    expect(
      avatarKeysIn(
        find.byKey(
          const ValueKey(
            'notification-row-surface-application-requested:application-alpha',
          ),
        ),
      ),
      ['notification-room-avatar-application-application-alpha'],
    );
    expect(
      avatarKeysIn(
        find.byKey(
          const ValueKey(
            'notification-row-surface-application-reviewed:application-approved',
          ),
        ),
      ),
      [
        'notification-application-reviewer-avatar-application-approved',
        'notification-room-avatar-application-reviewed-application-approved',
      ],
    );
    expect(avatarKeysIn(roomEventCard), [
      'notification-room-avatar-room-event-room-event-alpha',
      'notification-room-event-actor-avatar-room-event-alpha',
    ]);
    for (final key in const [
      'notification-inviter-name-invite-alpha',
      'notification-application-reviewer-name-application-approved',
      'notification-room-event-actor-name-room-event-alpha',
    ]) {
      expect(
        tester
            .widget<ui.HighlightedText>(find.byKey(ValueKey(key)))
            .style
            ?.fontSize,
        ui.UiTypography.label.fontSize,
      );
    }
    final firstMessageAvatar = find.byKey(
      const ValueKey('notification-room-avatar-room-event-room-event-alpha'),
    );
    expect(
      tester
          .getRect(find.byKey(bodyKey))
          .contains(tester.getRect(firstMessageAvatar).center),
      isTrue,
    );
    final notificationTimeLines = tester.widgetList<FittedBox>(
      find.descendant(
        of: find.byKey(timeKey),
        matching: find.byType(FittedBox),
      ),
    );
    expect(notificationTimeLines, hasLength(1));
    for (final line in notificationTimeLines) {
      expect(line.alignment, Alignment.center);
    }
    final notificationRows = find.byWidgetPredicate(
      (widget) =>
          widget.key is ValueKey<String> &&
          (widget.key! as ValueKey<String>).value.startsWith(
            'notification-row-surface-',
          ),
    );
    final notificationAvatars = find.descendant(
      of: notificationRows,
      matching: find.byType(ui.Avatar),
    );
    expect(notificationAvatars, findsWidgets);
    for (final avatar in tester.widgetList<ui.Avatar>(notificationAvatars)) {
      expect(avatar.size, 18);
    }
    final cardCenterY = tester.getRect(roomEventCard).center.dy;
    expect(
      tester.getRect(find.byKey(timeKey)).center.dy,
      closeTo(cardCenterY, 1),
    );
    expect(tester.getRect(jumpButton).center.dy, closeTo(cardCenterY, 1));
    expect(tester.takeException(), isNull);
  });

  testWidgets('desktop narrow notifications keep time on exactly two lines', (
    WidgetTester tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(433, 760));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    for (final platform in const [
      TargetPlatform.windows,
      TargetPlatform.macOS,
    ]) {
      await tester.pumpWidget(
        MaterialApp(
          key: ValueKey(platform),
          theme: ui.uiTheme().copyWith(platform: platform),
          home: HomePage(
            app: _homeTestAppContext(includeUnreadRoomNotification: true),
            realtime: _NoopRealtimeService(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byTooltip('通知'));
      await tester.pumpAndSettle();

      const timeKey = ValueKey('notification-room-event-time-room-event-alpha');
      final timeTexts = find.descendant(
        of: find.byKey(timeKey),
        matching: find.byType(Text),
      );
      expect(
        tester.widgetList<Text>(timeTexts).map((text) => text.data),
        ['2026/06/07', '16:00'],
        reason: '$platform should preserve both compact timestamp lines',
      );
      expect(
        tester
            .renderObjectList<RenderParagraph>(timeTexts)
            .every((paragraph) => !paragraph.didExceedMaxLines),
        isTrue,
        reason: '$platform should not truncate compact timestamps',
      );
      expect(
        tester
            .getRect(
              find.byKey(
                const ValueKey(
                  'notification-row-surface-room-event:room-event-alpha',
                ),
              ),
            )
            .left,
        lessThan(22),
        reason: '$platform should use the reduced compact list inset',
      );
      expect(tester.takeException(), isNull);
    }
  });

  testWidgets(
    'notifications switch to compact rows before the desktop shell breakpoint',
    (WidgetTester tester) async {
      await tester.binding.setSurfaceSize(const Size(2000, 900));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
          home: HomePage(
            app: _homeTestAppContext(includeUnreadRoomNotification: true),
            realtime: _NoopRealtimeService(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byTooltip('通知'));
      await tester.pumpAndSettle();

      const compactBodyKey = ValueKey(
        'notification-mobile-body-room-event-room-event-alpha',
      );
      expect(find.byKey(compactBodyKey), findsNothing);
      expect(tester.takeException(), isNull);

      for (final width in const [
        1800.0,
        1600.0,
        1400.0,
        1200.0,
        1000.0,
        900.0,
        760.0,
      ]) {
        await tester.binding.setSurfaceSize(Size(width, 900));
        await tester.pump();
        await tester.pumpAndSettle();

        expect(
          tester.takeException(),
          isNull,
          reason: 'resizing to $width should not overflow notification rows',
        );
        if (width == 1200) {
          expect(
            find.byKey(compactBodyKey),
            findsOneWidget,
            reason:
                'the complex room event should wrap instead of truncating at '
                '1200px',
          );
          for (final bodyKey in const [
            ValueKey('notification-mobile-body-invite-alpha'),
            ValueKey('notification-mobile-body-application-application-alpha'),
            ValueKey('notification-mobile-body-reviewed-application-approved'),
            ValueKey('notification-mobile-body-room-event-room-event-alpha'),
          ]) {
            expect(
              find.byKey(bodyKey),
              findsOneWidget,
              reason:
                  'one wrapped notification should synchronize compact '
                  'layout across the list',
            );
          }
          for (final timeKey in const [
            ValueKey('notification-time-invite-alpha'),
            ValueKey('notification-application-time-application-alpha'),
            ValueKey(
              'notification-application-review-time-application-approved',
            ),
            ValueKey('notification-room-event-time-room-event-alpha'),
          ]) {
            final timeLines = find.descendant(
              of: find.byKey(timeKey),
              matching: find.byType(Text),
            );
            expect(
              timeLines,
              findsNWidgets(2),
              reason:
                  'all synchronized compact timestamps should use the same '
                  'two-line format',
            );
          }
          final allNotificationRows = find.byWidgetPredicate(
            (widget) =>
                widget.key is ValueKey<String> &&
                (widget.key! as ValueKey<String>).value.startsWith(
                  'notification-row-surface-',
                ),
          );
          final allNotificationAvatars = tester.widgetList<ui.Avatar>(
            find.descendant(
              of: allNotificationRows,
              matching: find.byType(ui.Avatar),
            ),
          );
          expect(allNotificationAvatars, isNotEmpty);
          expect(
            allNotificationAvatars.every((avatar) => avatar.size == 18),
            isTrue,
            reason:
                'all notification avatars should share the compact size '
                'after any row wraps',
          );
          final roomEventCard = find.byKey(
            const ValueKey(
              'notification-row-surface-room-event:room-event-alpha',
            ),
          );
          final roomEventAvatars = tester.widgetList<ui.Avatar>(
            find.descendant(
              of: roomEventCard,
              matching: find.byType(ui.Avatar),
            ),
          );
          expect(roomEventAvatars, isNotEmpty);
          expect(
            roomEventAvatars.every((avatar) => avatar.size == 18),
            isTrue,
            reason: 'wrapped notification rows should use compact avatars',
          );
          expect(
            tester
                .renderObjectList<RenderParagraph>(
                  find.descendant(
                    of: roomEventCard,
                    matching: find.byType(RichText),
                  ),
                )
                .every((paragraph) => !paragraph.didExceedMaxLines),
            isTrue,
            reason: 'wrapped notification content should remain complete',
          );
          final jumpButton = find.descendant(
            of: roomEventCard,
            matching: find.byType(ui.ButtonIcon),
          );
          expect(
            tester.getRect(jumpButton).center.dy,
            closeTo(tester.getRect(roomEventCard).center.dy, 1),
            reason: 'the trailing action should stay vertically centered',
          );
        }
      }
      expect(
        find.byKey(compactBodyKey),
        findsOneWidget,
        reason:
            'notification rows should become compact before the shell reaches '
            'its 720px breakpoint',
      );

      await tester.binding.setSurfaceSize(const Size(2000, 900));
      await tester.pumpAndSettle();
      expect(
        find.byKey(compactBodyKey),
        findsNothing,
        reason:
            'the synchronized list should return to wide rows after the '
            'window expands',
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('room notification row dot clears after leaving notifications', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(includeUnreadRoomNotification: true),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey('home-sidebar-notifications-button')),
    );
    await tester.pumpAndSettle();

    const markerKey = ValueKey('notification-room-event-new-room-event-alpha');
    expect(find.byKey(markerKey), findsOneWidget);

    await tester.tap(find.text('Beta Room'));
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey('home-sidebar-notifications-button')),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(markerKey), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('room list shows pending join request dot without unread count', (
    WidgetTester tester,
  ) async {
    const badgeKey = ValueKey(
      'home-sidebar-room-pending-join-requests-server-alpha',
    );
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(
            alphaRoomUnreadCount: 0,
            alphaRoomHasPendingJoinRequests: true,
          ),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(badgeKey), findsOneWidget);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(
            alphaRoomUnreadCount: 2,
            alphaRoomHasPendingJoinRequests: true,
          ),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(badgeKey), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('authenticated home shell sends messages through real API path', (
    WidgetTester tester,
  ) async {
    final requestedPaths = <String>[];

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(requestedPaths: requestedPaths),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.descendant(
        of: find.byType(ui.ChatComposer),
        matching: find.byType(TextField),
      ),
      'Fresh message',
    );
    await tester.tap(find.byTooltip('发送'));
    await tester.pumpAndSettle();

    expect(requestedPaths, contains('/api/v1/rooms/server-alpha/messages'));
    expect(find.text('Fresh message'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('authenticated home shell adds a message quote from its menu', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();

    final messageBody = find.text('Hello from Morgan');
    expect(messageBody, findsOneWidget);
    final secondaryClick = await tester.startGesture(
      tester.getCenter(messageBody),
      kind: PointerDeviceKind.mouse,
      buttons: kSecondaryMouseButton,
    );
    await secondaryClick.up();
    await tester.pumpAndSettle();

    await tester.tap(find.text('引用'));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('composer-quote-close-msg-1')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('shared song card lists personal and current room playlists', (
    WidgetTester tester,
  ) async {
    final requestedPaths = <String>[];
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(
            requestedPaths: requestedPaths,
            includeSharedMusicTrackMessage: true,
          ),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('分享歌曲完整名称'));
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey<String>('music-track-card-add-to-playlist')),
    );
    await tester.pumpAndSettle();

    expect(find.text('Kai 的个人歌单'), findsOneWidget);
    expect(find.text('Alpha 房间歌单'), findsOneWidget);
    expect(requestedPaths, contains('/api/v1/me/music-box/playlists'));
    expect(
      requestedPaths,
      contains('/api/v1/rooms/server-alpha/music-box/playlists'),
    );

    await tester.tap(
      find.byKey(
        const ValueKey<String>(
          'music-track-playlist-target:room:server-alpha:mbp-room-alpha',
        ),
      ),
    );
    await tester.pump();
    await tester.tap(
      find.byKey(const ValueKey<String>('music-track-playlist-target-confirm')),
    );
    await tester.pumpAndSettle();

    expect(
      requestedPaths,
      contains(
        '/api/v1/rooms/server-alpha/music-box/playlists/'
        'mbp-room-alpha/items',
      ),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('copied music message pastes as a removable component', (
    WidgetTester tester,
  ) async {
    String? clipboardText;
    final sentMessageRequests = <Map<String, Object?>>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          final arguments = call.arguments as Map<Object?, Object?>;
          clipboardText = arguments['text'] as String?;
          return null;
        }
        if (call.method == 'Clipboard.getData') {
          final value = clipboardText;
          return <String, dynamic>{
            'text': value == null
                ? null
                : '${value.replaceAll('\n', '\r\n')}\r\n',
          };
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(
            includeSharedMusicTrackMessage: true,
            sentMessageRequests: sentMessageRequests,
          ),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();
    final title = find.text('分享歌曲完整名称');
    final secondaryClick = await tester.startGesture(
      tester.getCenter(title),
      kind: PointerDeviceKind.mouse,
      buttons: kSecondaryMouseButton,
    );
    await secondaryClick.up();
    await tester.pumpAndSettle();
    await tester.tap(find.text('复制'));
    await tester.pumpAndSettle();
    expect(clipboardText, contains('[歌曲] 分享歌曲完整名称'));

    final composerField = find.descendant(
      of: find.byType(ui.ChatComposer),
      matching: find.byType(TextField),
    );
    await tester.tap(composerField);
    await tester.pump();
    await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.keyV);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.keyV);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('composer-message-component')),
      findsOneWidget,
    );
    expect(tester.widget<TextField>(composerField).controller?.text, isEmpty);

    await tester.tap(
      find.byKey(const ValueKey('remove-composer-message-component')),
    );
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('composer-message-component')),
      findsNothing,
    );

    await tester.tap(composerField);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.keyV);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.keyV);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
    await tester.pumpAndSettle();
    await tester.enterText(composerField, '紧接着发送的说明');
    await tester.tap(find.byTooltip('发送'));
    await tester.pumpAndSettle();

    expect(sentMessageRequests, hasLength(2));
    expect(sentMessageRequests.first['type'], 'music_track');
    final componentAttachments =
        sentMessageRequests.first['attachments']! as List<Object?>;
    expect(componentAttachments, hasLength(1));
    expect(
      (componentAttachments.single!
          as Map<String, Object?>)['source_message_id'],
      'msg-music-track',
    );
    expect(sentMessageRequests.last['type'] ?? 'text', 'text');
    expect(sentMessageRequests.last['body'], '紧接着发送的说明');
    expect(
      find.byKey(const ValueKey('composer-message-component')),
      findsNothing,
    );
    expect(tester.widget<TextField>(composerField).controller?.text, isEmpty);

    expect(tester.takeException(), isNull);
  });

  testWidgets('authenticated home shell opens live channel pane', (
    WidgetTester tester,
  ) async {
    final requestedPaths = <String>[];
    final liveModerationActions = <String>[];
    final liveSession = _FakeLiveSession();
    final liveSessionController = _FakeLiveSessionController(
      session: liveSession,
    );
    final presenceSounds = _RecordingLivePresenceSoundPlayer();
    final presenceSpeech = _RecordingLivePresenceSpeechPlayer();
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(
            requestedPaths: requestedPaths,
            liveModerationActions: liveModerationActions,
            alphaRoomAiVoiceAnnouncementsEnabled: true,
          ),
          audioDeviceStore: const _FakeAudioDeviceStore(),
          liveSessionController: liveSessionController,
          livePresenceSoundPlayer: presenceSounds,
          livePresenceSpeechPlayer: presenceSpeech,
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();

    await _openLiveChannelFromHeader(tester);

    expect(find.text('Morgan'), findsOneWidget);
    expect(find.widgetWithText(ui.Button, '加入'), findsOneWidget);
    expect(_liveControl('collapse'), findsOneWidget);
    expect(find.byTooltip('收起语音频道'), findsOneWidget);
    expect(find.byTooltip('已加入语音'), findsNothing);

    await tester.tap(find.widgetWithText(ui.Button, '加入'));
    await tester.pumpAndSettle();

    expect(requestedPaths, contains('/api/v1/rooms/server-alpha/live/join'));
    expect(liveSession.connectAttempts, 1);
    expect(presenceSounds.sounds, [LivePresenceSound.joined]);
    expect(presenceSounds.volumes.single, closeTo(0.75, 0.001));
    expect(presenceSpeech.announcements, isEmpty);

    liveSession.emitParticipantJoined();
    for (var index = 0; index < 8; index += 1) {
      await tester.pump(const Duration(milliseconds: 500));
    }
    await tester.pumpAndSettle();
    expect(presenceSounds.sounds, [
      LivePresenceSound.joined,
      LivePresenceSound.joined,
    ]);
    expect(presenceSpeech.announcements.map((item) => item.segments).toList(), [
      ['成员', 'Morgan', '进入了语音频道'],
    ]);
    final selfLiveMemberCard = find.ancestor(
      of: find.byKey(const ValueKey<String>('live-member-status:mic:user-1')),
      matching: find.byType(ui.PressableSurface),
    );
    final selfLiveName = tester.widget<Text>(
      find.descendant(of: selfLiveMemberCard, matching: find.text('Kai')),
    );
    expect(selfLiveName.style?.color, ui.UiColors.accent);
    expect(find.text('Kai (you)'), findsNothing);
    expect(find.text('Morgan'), findsOneWidget);
    expect(find.widgetWithText(ui.Button, '加入'), findsNothing);
    expect(_liveControl('mic'), findsOneWidget);
    expect(_liveControl('leave'), findsOneWidget);
    expect(find.byTooltip('静音'), findsNothing);
    expect(find.byTooltip('麦克风静音'), findsOneWidget);
    expect(find.byTooltip('耳机静音'), findsOneWidget);
    expect(find.byTooltip('关闭麦克风'), findsNWidgets(2));
    expect(find.byTooltip('关闭耳机'), findsNWidgets(2));
    expect(find.byTooltip('共享屏幕'), findsOneWidget);
    expect(find.byTooltip('开启摄像头'), findsOneWidget);
    expect(find.byTooltip('离开'), findsNothing);
    expect(find.byTooltip('离开语音频道'), findsNWidgets(2));
    expect(find.byTooltip('已加入语音'), findsNothing);
    expect(
      tester
          .widget<Icon>(
            find.byKey(const ValueKey('home-sidebar-room-live-server-alpha')),
          )
          .color,
      ui.UiColors.accent,
    );
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('home-sidebar-user-summary')),
        matching: find.text('语音'),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('home-sidebar-user-summary')),
        matching: find.text('active'),
      ),
      findsNothing,
    );
    final selfVoiceVolumeButton = find.byKey(
      const ValueKey<String>('live-member-status:voice-volume:user-1'),
    );
    final selfKickButton = find.byKey(
      const ValueKey<String>('live-member-status:kick:user-1'),
    );
    final remoteVoiceVolumeButton = find.byKey(
      const ValueKey<String>('live-member-status:voice-volume:user-2'),
    );
    final remoteMicButton = find.byKey(
      const ValueKey<String>('live-member-status:mic:user-2'),
    );
    final remoteHeadphonesButton = find.byKey(
      const ValueKey<String>('live-member-status:headphones:user-2'),
    );
    final remoteKickButton = find.byKey(
      const ValueKey<String>('live-member-status:kick:user-2'),
    );
    expect(selfVoiceVolumeButton, findsNothing);
    expect(selfKickButton, findsNothing);
    expect(remoteVoiceVolumeButton, findsOneWidget);
    expect(remoteMicButton, findsOneWidget);
    expect(remoteHeadphonesButton, findsOneWidget);
    expect(remoteKickButton, findsOneWidget);

    final memberHover = await tester.createGesture(
      kind: PointerDeviceKind.mouse,
    );
    await memberHover.addPointer(
      location: tester.getCenter(remoteVoiceVolumeButton),
    );
    await tester.pump();
    final memberVolumeSlider = find.byKey(
      const ValueKey<String>('live-volume-slider:Morgan语音音量'),
    );
    expect(memberVolumeSlider, findsOneWidget);
    await tester.tapAt(
      tester.getRect(memberVolumeSlider).bottomCenter - const Offset(0, 1),
    );
    await tester.pump();
    expect(liveSession.participantVoiceVolumeWrites.last, 'user-2:0.00');

    await memberHover.removePointer();
    await tester.pumpAndSettle();
    await tester.tap(remoteMicButton);
    await tester.pumpAndSettle();
    expect(find.text('麦克风静音此用户'), findsOneWidget);
    await tester.tap(find.widgetWithText(ui.Button, '麦克风静音'));
    await tester.pumpAndSettle();
    expect(liveModerationActions.last, 'mute_mic');

    await tester.tap(remoteHeadphonesButton);
    await tester.pumpAndSettle();
    expect(find.text('耳机静音此用户'), findsOneWidget);
    await tester.tap(find.widgetWithText(ui.Button, '耳机静音'));
    await tester.pumpAndSettle();
    expect(liveModerationActions.last, 'block_voice');

    await tester.tap(remoteHeadphonesButton);
    await tester.pumpAndSettle();
    expect(find.widgetWithText(ui.Button, '取消耳机静音'), findsOneWidget);
    await tester.tap(find.widgetWithText(ui.Button, '取消耳机静音'));
    await tester.pumpAndSettle();
    expect(liveModerationActions.last, 'restore_headphones');

    await tester.tap(remoteMicButton);
    await tester.pumpAndSettle();
    expect(find.widgetWithText(ui.Button, '取消麦克风静音'), findsOneWidget);
    await tester.tap(find.widgetWithText(ui.Button, '取消麦克风静音'));
    await tester.pumpAndSettle();
    expect(liveModerationActions.last, 'restore_voice');

    await tester.tap(remoteKickButton);
    await tester.pumpAndSettle();
    expect(find.text('踢出语音频道'), findsOneWidget);
    await tester.tap(find.widgetWithText(ui.Button, '踢出'));
    await tester.pumpAndSettle();
    expect(liveModerationActions.last, 'kick');
    expect(
      requestedPaths,
      contains(
        '/api/v1/rooms/server-alpha/live/participants/user-2/moderation',
      ),
    );
    expect(find.text('Morgan'), findsNothing);
    liveSession.emitParticipantLeft(removed: true);
    for (var index = 0; index < 3; index += 1) {
      await tester.pump(const Duration(milliseconds: 500));
    }
    await tester.pumpAndSettle();
    expect(presenceSpeech.announcements.last.segments, [
      '成员',
      'Morgan',
      '被踢出了语音频道',
    ]);

    await tester.tap(_liveControl('mic'));
    await tester.pumpAndSettle();
    expect(liveSession.inputVolumes.last, 0.0);
    expect(
      find.descendant(
        of: _liveControl('mic'),
        matching: find.byIcon(Icons.mic_off),
      ),
      findsOneWidget,
    );

    await tester.tap(_liveControl('mic'));
    await tester.pumpAndSettle();
    expect(liveSession.inputVolumes.last, closeTo(0.35, 1e-9));
    expect(
      find.descendant(
        of: _liveControl('mic'),
        matching: find.byIcon(Icons.mic),
      ),
      findsOneWidget,
    );

    await tester.tap(_liveControl('headphones'));
    await tester.pumpAndSettle();
    expect(liveSession.outputVolumes.last, 0.0);
    expect(liveSession.inputVolumes.last, 0.0);
    expect(liveSession.micMutes, contains(true));
    expect(
      find.descendant(
        of: _liveControl('headphones'),
        matching: find.byIcon(Icons.headset_off),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: _liveControl('mic'),
        matching: find.byIcon(Icons.mic_off),
      ),
      findsOneWidget,
    );

    await tester.tap(_liveControl('headphones'));
    await tester.pumpAndSettle();
    expect(liveSession.outputVolumes.last, closeTo(0.75, 1e-9));
    expect(
      find.descendant(
        of: _liveControl('headphones'),
        matching: find.byIcon(Icons.headphones),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: _liveControl('mic'),
        matching: find.byIcon(Icons.mic_off),
      ),
      findsOneWidget,
    );

    final hover = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await hover.addPointer(location: tester.getCenter(_liveControl('mic')));
    await tester.pump();
    final micVolumeSlider = find.byKey(
      const ValueKey<String>('live-volume-slider:麦克风输入音量'),
    );
    final micVolumeThumb = find.byKey(
      const ValueKey<String>('live-volume-thumb:麦克风输入音量'),
    );
    final micVolumeFill = find.byKey(
      const ValueKey<String>('live-volume-fill:麦克风输入音量'),
    );
    final micVolumePanel = find.byKey(
      const ValueKey<String>('live-volume-panel:麦克风输入音量'),
    );
    expect(micVolumeSlider, findsOneWidget);
    expect(
      tester.getSize(micVolumePanel).width,
      tester.getSize(_liveControl('mic')).width,
    );
    expect(tester.getSize(micVolumePanel).height, lessThan(174));
    expect(
      tester.getRect(micVolumePanel).bottom,
      lessThan(tester.getRect(_liveControl('mic')).top),
    );
    expect(tester.getSize(micVolumeFill).width, greaterThanOrEqualTo(7));
    final fillDecoration =
        tester.widget<DecoratedBox>(micVolumeFill).decoration as BoxDecoration;
    final fillRadius = fillDecoration.borderRadius as BorderRadius;
    expect(fillRadius.topLeft, Radius.zero);
    expect(fillRadius.topRight, Radius.zero);
    expect(tester.getSize(micVolumeThumb).width, greaterThanOrEqualTo(26));
    expect(tester.getSize(micVolumeThumb).height, greaterThanOrEqualTo(7));
    expect(
      (tester.getRect(micVolumeFill).top -
              tester.getRect(micVolumeThumb).bottom)
          .abs(),
      lessThanOrEqualTo(0.5),
    );
    await hover.moveTo(tester.getCenter(micVolumeThumb));
    await tester.pump();
    expect(
      find.byKey(const ValueKey<String>('live-volume-percent:麦克风输入音量')),
      findsOneWidget,
    );
    final micVolumePercent = find.byKey(
      const ValueKey<String>('live-volume-percent:麦克风输入音量'),
    );
    expect(
      tester.getRect(micVolumePercent).left,
      greaterThan(tester.getRect(micVolumeThumb).right),
    );
    final percentDecoration =
        tester.widget<DecoratedBox>(micVolumePercent).decoration
            as BoxDecoration;
    expect(percentDecoration.color, Colors.white);
    expect(percentDecoration.border, isNull);
    var volumeSliderRect = tester.getRect(micVolumeSlider);
    await tester.tapAt(volumeSliderRect.bottomCenter - const Offset(0, 1));
    await tester.pumpAndSettle();
    expect(liveSession.inputVolumes, contains(0.0));
    expect(liveSession.micMutes, contains(true));
    expect(
      find.descendant(
        of: _liveControl('mic'),
        matching: find.byIcon(Icons.mic_off),
      ),
      findsOneWidget,
    );

    await tester.tap(_liveControl('mic'));
    await tester.pumpAndSettle();
    expect(liveSession.inputVolumes.last, closeTo(0.5, 1e-9));
    expect(
      find.descendant(
        of: _liveControl('mic'),
        matching: find.byIcon(Icons.mic),
      ),
      findsOneWidget,
    );

    await hover.moveTo(tester.getCenter(_liveControl('headphones')));
    await tester.pump();
    final outputVolumeSlider = find.byKey(
      const ValueKey<String>('live-volume-slider:语音输出音量'),
    );
    expect(outputVolumeSlider, findsOneWidget);
    volumeSliderRect = tester.getRect(outputVolumeSlider);
    await tester.tapAt(volumeSliderRect.bottomCenter - const Offset(0, 1));
    await tester.pumpAndSettle();
    expect(liveSession.outputVolumes, contains(0.0));
    expect(liveSession.inputVolumes, contains(0.0));
    expect(liveSession.micMutes, contains(true));
    expect(liveSession.outputMutes, containsAll([true, false]));
    expect(
      find.descendant(
        of: _liveControl('headphones'),
        matching: find.byIcon(Icons.headset_off),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: _liveControl('mic'),
        matching: find.byIcon(Icons.mic_off),
      ),
      findsOneWidget,
    );

    await tester.tap(_liveControl('headphones'));
    await tester.pumpAndSettle();
    expect(liveSession.outputVolumes.last, closeTo(0.75, 1e-9));
    expect(
      find.descendant(
        of: _liveControl('headphones'),
        matching: find.byIcon(Icons.headphones),
      ),
      findsOneWidget,
    );

    await hover.moveTo(tester.getCenter(_liveControl('screen-share')));
    await tester.pump(const Duration(milliseconds: 120));
    expect(
      find.byKey(const ValueKey<String>('live-volume-slider:共享屏幕输出音量')),
      findsNothing,
    );
    await hover.removePointer();
    await tester.pump();

    await tester.tap(_liveControl('camera'));
    await tester.pumpAndSettle();
    await tester.tap(_liveControl('screen-share'));
    await tester.pumpAndSettle();
    expect(find.text('Primary Display'), findsOneWidget);
    await tester.tap(find.text('Primary Display'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ui.Button, '共享'));
    await tester.pumpAndSettle();

    expect(liveSession.micMutes, contains(true));
    expect(liveSession.outputMutes, containsAll([true, false]));
    expect(liveSession.cameraEnables, [true, false]);
    expect(liveSession.screenShareEnables, [true]);
    expect(liveSession.screenShareSourceIds, ['screen-primary']);
    expect(
      requestedPaths
          .where((path) => path == '/api/v1/rooms/server-alpha/live/me')
          .length,
      greaterThanOrEqualTo(3),
    );

    final queuedPresencePlayback = Completer<void>();
    presenceSounds.nextPlaybackCompletion = queuedPresencePlayback;
    liveSession.emitParticipantJoined();
    await tester.pump();
    expect(presenceSounds.sounds.last, LivePresenceSound.joined);
    final soundCountBeforeExit = presenceSounds.sounds.length;
    liveSession.emitParticipantLeft();
    await tester.pump();
    expect(presenceSounds.sounds, hasLength(soundCountBeforeExit));

    await tester.tap(_liveControl('leave'));
    await tester.pump();
    queuedPresencePlayback.complete();
    for (var index = 0; index < 3; index += 1) {
      await tester.pump(const Duration(milliseconds: 500));
    }
    await tester.pumpAndSettle();

    expect(liveSession.disconnects, 1);
    expect(presenceSounds.sounds, hasLength(soundCountBeforeExit + 1));
    expect(presenceSounds.sounds.last, LivePresenceSound.left);
    expect(presenceSpeech.announcements, hasLength(3));
    expect(find.widgetWithText(ui.Button, '加入'), findsOneWidget);
    expect(find.byTooltip('已加入语音'), findsNothing);

    await tester.tap(_liveControl('collapse'));
    await tester.pumpAndSettle();

    expect(find.text('Kai (you)'), findsNothing);
    expect(find.text('Hello from Morgan'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'transient music box load retries automatically and restores its controls',
    (WidgetTester tester) async {
      final requestedPaths = <String>[];
      final liveSession = _FakeLiveSession();
      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
          home: HomePage(
            app: _homeTestAppContext(
              requestedPaths: requestedPaths,
              musicBoxEnabled: true,
              // Fail the initial room-open load. The room-scoped timer must
              // restore the module without requiring another lifecycle action.
              musicBoxStateTransientFailures: 1,
            ),
            audioDeviceStore: const _FakeAudioDeviceStore(),
            liveSessionController: _FakeLiveSessionController(
              session: liveSession,
            ),
            realtime: _NoopRealtimeService(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Alpha Room'));
      await tester.pumpAndSettle();
      await tester.pump(const Duration(seconds: 2));
      await tester.pumpAndSettle();
      await _openLiveChannelFromHeader(tester);

      expect(
        requestedPaths
            .where(
              (path) => path == '/api/v1/rooms/server-alpha/music-box/state',
            )
            .length,
        2,
      );
      expect(
        find.byKey(const ValueKey<String>('live-control:music-inline')),
        findsNothing,
      );

      await tester.tap(find.widgetWithText(ui.Button, '加入'));
      await tester.pumpAndSettle();

      expect(
        requestedPaths
            .where(
              (path) => path == '/api/v1/rooms/server-alpha/music-box/state',
            )
            .length,
        2,
      );
      expect(
        find.byKey(const ValueKey<String>('live-control:music-inline')),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('participant departure waits for authoritative reconciliation', (
    WidgetTester tester,
  ) async {
    final realtime = _FakeRealtimeService();
    final liveSession = _FakeLiveSession();
    final liveSessionController = _FakeLiveSessionController(
      session: liveSession,
    );
    final presenceSounds = _RecordingLivePresenceSoundPlayer();
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(),
          audioDeviceStore: const _FakeAudioDeviceStore(),
          liveSessionController: liveSessionController,
          livePresenceSoundPlayer: presenceSounds,
          livePresenceSpeechPlayer: _RecordingLivePresenceSpeechPlayer(),
          realtime: realtime,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();
    await _openLiveChannelFromHeader(tester);
    await tester.tap(find.widgetWithText(ui.Button, '加入'));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey<String>('live-member-status:mic:user-2')),
      findsOneWidget,
    );
    final soundCountBeforeDisconnect = presenceSounds.sounds.length;

    liveSession.emitParticipantLeft();
    await tester.pump();

    expect(
      find.byKey(const ValueKey<String>('live-member-status:mic:user-2')),
      findsOneWidget,
    );
    expect(presenceSounds.sounds, hasLength(soundCountBeforeDisconnect));

    realtime.add(
      RealtimeEvent(
        type: 'live_participant_reconnecting',
        data: {
          'room_id': 'server-alpha',
          'live': _liveStateJson(
            roomId: 'server-alpha',
            participantCount: 2,
            updatedAt: '2026-06-05T08:01:00Z',
            participants: [
              _liveParticipantJson(
                user: _currentUserJson,
                liveSessionId: 'live-session-joined',
              ),
              _liveParticipantJson(
                user: _userJson(
                  id: 'user-2',
                  username: 'morgan',
                  displayName: 'Morgan',
                ),
                liveSessionId: 'live-session-morgan',
                connectionState: 'reconnecting',
              ),
            ],
          ),
        },
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey<String>('live-member-status:mic:user-2')),
      findsOneWidget,
    );
    expect(presenceSounds.sounds, hasLength(soundCountBeforeDisconnect));

    realtime.add(
      RealtimeEvent(
        type: 'live_participants_reconciled',
        data: {
          'room_id': 'server-alpha',
          'live': _liveStateJson(
            roomId: 'server-alpha',
            participantCount: 1,
            updatedAt: '2026-06-05T08:02:00Z',
            participants: [
              _liveParticipantJson(
                user: _currentUserJson,
                liveSessionId: 'live-session-joined',
              ),
            ],
          ),
        },
      ),
    );
    for (var index = 0; index < 4; index += 1) {
      await tester.pump(const Duration(milliseconds: 500));
    }
    await tester.pumpAndSettle();

    expect(find.text('Morgan'), findsNothing);
    expect(presenceSounds.sounds, hasLength(soundCountBeforeDisconnect + 1));
    expect(presenceSounds.sounds.last, LivePresenceSound.left);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'disabling personal AI announcements keeps participant join and leave cues',
    (WidgetTester tester) async {
      final realtime = _FakeRealtimeService();
      final liveSession = _FakeLiveSession();
      final liveSessionController = _FakeLiveSessionController(
        session: liveSession,
      );
      final presenceSounds = _RecordingLivePresenceSoundPlayer();
      final presenceSpeech = _RecordingLivePresenceSpeechPlayer();

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: HomePage(
            app: _homeTestAppContext(),
            audioDeviceStore: const _FakeAudioDeviceStore(),
            liveSessionController: liveSessionController,
            livePresenceSoundPlayer: presenceSounds,
            livePresenceSpeechPlayer: presenceSpeech,
            realtime: realtime,
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Alpha Room'));
      await tester.pumpAndSettle();
      await _openLiveChannelFromHeader(tester);
      await tester.tap(find.widgetWithText(ui.Button, '加入'));
      await tester.pumpAndSettle();

      realtime.add(
        RealtimeEvent(
          type: 'room_updated',
          data: {
            ..._roomCardJson(
              id: 'server-alpha',
              name: 'Alpha Room',
              memberCount: 2,
              liveParticipantCount: 2,
            ),
            'ai_voice_announcements_enabled': false,
          },
        ),
      );
      await tester.pumpAndSettle();

      final initialCueCount = presenceSounds.sounds.length;
      liveSession.emitParticipantJoined();
      liveSession.emitParticipantLeft();
      realtime.add(
        RealtimeEvent(
          type: 'live_participants_reconciled',
          data: {
            'room_id': 'server-alpha',
            'live': _liveStateJson(
              roomId: 'server-alpha',
              participantCount: 1,
              updatedAt: '2026-06-05T08:01:00Z',
              participants: [
                _liveParticipantJson(
                  user: _currentUserJson,
                  liveSessionId: 'live-session-joined',
                ),
              ],
            ),
          },
        ),
      );
      for (var index = 0; index < 4; index += 1) {
        await tester.pump(const Duration(milliseconds: 500));
      }
      await tester.pumpAndSettle();

      expect(presenceSounds.sounds.skip(initialCueCount), [
        LivePresenceSound.joined,
        LivePresenceSound.left,
      ]);
      expect(presenceSpeech.announcements, isEmpty);
      expect(tester.takeException(), isNull);
    },
  );
}
