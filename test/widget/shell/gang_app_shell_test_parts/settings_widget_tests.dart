part of '../gang_app_shell_test.dart';

void registerShellSettingsWidgetTests() {
  testWidgets(
    'managed user settings reuse the ordinary page with target account data',
    (WidgetTester tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(980, 820);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);

      final requestedUris = <Uri>[];
      final targetUser = <String, Object?>{
        ..._currentUserJson,
        'id': 'target_user',
        'uid': '1000099',
        'username': 'target_name',
        'display_name': '目标用户',
        'email': 'target@example.test',
        'is_superuser': false,
      };
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          requestedUris.add(request.url);
          if (request.url.path == '/api/v1/users/target_user/settings') {
            return _jsonResponse({'user': targetUser});
          }
          if (request.url.path == '/api/v1/users/target_user/sessions') {
            return _jsonResponse([]);
          }
          if (request.url.path == '/api/v1/sticker-packs') {
            return _jsonResponse({
              'packs': [
                {
                  'id': 'target_pack',
                  'scope': 'personal',
                  'name': '目标表情包',
                  'sort_order': 10,
                  'stickers': const [
                    {
                      'id': 'target_sticker',
                      'name': '目标表情',
                      'sort_order': 10,
                      'asset': {
                        'id': 'target_asset',
                        'url':
                            'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
                        'thumbnail_url':
                            'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
                        'mime_type': 'image/png',
                      },
                    },
                  ],
                },
              ],
            });
          }
          return http.Response('unexpected request: ${request.url}', 404);
        }),
      );
      final stickerStore = _MemoryStickerPackStore();

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: SettingsPage(
            isSubWindow: true,
            initialSection: SettingsSection.security,
            api: api,
            apiBaseUrl: 'http://example.test/api/v1',
            controller: SettingsController(
              api: api,
              apiBaseUrl: 'http://example.test/api/v1',
              stickerPackStore: stickerStore,
              managedUserId: 'target_user',
            ),
            stickerPackStore: stickerStore,
            systemAudioDevices: SystemAudioDevices(supported: false),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('目标用户 的用户设置'), findsOneWidget);
      expect(find.text('用户资料'), findsOneWidget);
      expect(find.text('偏好设置'), findsOneWidget);
      expect(find.text('隐私和安全'), findsOneWidget);
      expect(find.text('语音和视频'), findsOneWidget);
      expect(find.text('我的表情包'), findsOneWidget);
      expect(find.text('关于Gang Chat'), findsOneWidget);
      expect(find.text('当前密码'), findsNothing);
      final forgot = find.widgetWithText(ui.Button, '忘记密码');
      expect(forgot, findsOneWidget);
      expect(tester.widget<ui.Button>(forgot).onPressed, isNull);

      await tester.tap(find.text('我的表情包').first);
      await tester.pumpAndSettle();

      expect(find.text('目标表情包'), findsNothing);
      expect(
        requestedUris.any(
          (uri) =>
              uri.path == '/api/v1/sticker-packs' &&
              uri.queryParameters['owner_user_id'] == 'target_user',
        ),
        isTrue,
      );
      expect(stickerStore.lastWrittenUserId, 'target_user');
      expect(stickerStore.packs!.single.stickers.single.name, '目标表情');
      expect(tester.takeException(), isNull);
      api.close();
    },
  );

  testWidgets(
    'managed user settings can suspend and restore the target account',
    (WidgetTester tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(980, 820);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);

      final statusUpdates = <String>[];
      final targetUser = <String, Object?>{
        ..._currentUserJson,
        'id': 'suspension_target',
        'uid': '1000100',
        'username': 'suspension_target',
        'display_name': '封禁测试用户',
        'email': 'suspension-target@example.test',
        'is_superuser': false,
        'status': 'active',
      };
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          if (request.url.path == '/api/v1/users/suspension_target/settings') {
            if (request.method == 'PATCH') {
              final body = jsonDecode(request.body) as Map<String, Object?>;
              final status = body['status']! as String;
              statusUpdates.add(status);
              targetUser['status'] = status;
            }
            return _jsonResponse({'user': targetUser});
          }
          if (request.url.path == '/api/v1/users/suspension_target/sessions') {
            return _jsonResponse([]);
          }
          return http.Response('unexpected request: ${request.url}', 404);
        }),
      );
      final stickerStore = _MemoryStickerPackStore();

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: SettingsPage(
            isSubWindow: true,
            initialSection: SettingsSection.security,
            api: api,
            apiBaseUrl: 'http://example.test/api/v1',
            controller: SettingsController(
              api: api,
              apiBaseUrl: 'http://example.test/api/v1',
              stickerPackStore: stickerStore,
              managedUserId: 'suspension_target',
            ),
            stickerPackStore: stickerStore,
            systemAudioDevices: SystemAudioDevices(supported: false),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.drag(find.byType(ListView).last, const Offset(0, -720));
      await tester.pumpAndSettle();
      expect(find.text('账号状态正常'), findsOneWidget);
      final suspendButton = find.widgetWithText(ui.Button, '封禁账号');
      await tester.ensureVisible(suspendButton);
      await tester.tap(suspendButton);
      await tester.pumpAndSettle();
      expect(find.text('确认封禁账号'), findsOneWidget);
      await tester.tap(find.widgetWithText(ui.Button, '封禁账号').last);
      await tester.pumpAndSettle();

      expect(statusUpdates, ['suspended']);
      expect(find.text('账号封禁中'), findsOneWidget);
      final suspendedLabel = tester.widget<Text>(find.text('账号封禁中'));
      expect(suspendedLabel.style?.color, ui.UiColors.danger);

      final restoreButton = find.widgetWithText(ui.Button, '解除封禁');
      await tester.ensureVisible(restoreButton);
      await tester.tap(restoreButton);
      await tester.pumpAndSettle();
      expect(find.text('确认解除封禁'), findsOneWidget);
      await tester.tap(find.widgetWithText(ui.Button, '解除封禁').last);
      await tester.pumpAndSettle();

      expect(statusUpdates, ['suspended', 'active']);
      expect(find.text('账号状态正常'), findsOneWidget);
      expect(tester.takeException(), isNull);
      api.close();
    },
  );

  testWidgets(
    'direct close exit keeps token while leaving live and going offline',
    (WidgetTester tester) async {
      final events = <String>[];
      final requestedPaths = <String>[];
      final liveSession = _FakeLiveSession();
      final liveSessionController = _FakeLiveSessionController(
        session: liveSession,
      );
      final realtime = _RecordingRealtimeService(events);
      final windowController = _RecordingWindowController(events);

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: HomePage(
            app: _homeTestAppContext(
              onLogout: () async => events.add('logout'),
              onExitSessionForAppExit: () async => events.add('exit-session'),
              requestedPaths: requestedPaths,
            ),
            audioDeviceStore: const _FakeAudioDeviceStore(),
            liveSessionController: liveSessionController,
            realtime: realtime,
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
      expect(liveSession.inputVolumes.last, 0);
      expect(liveSession.outputVolumes.last, 0);

      await tester.tap(
        find
            .ancestor(
              of: find.text('Alpha Room'),
              matching: find.byType(ui.PressableSurface),
            )
            .first,
      );
      await tester.pumpAndSettle();

      expect(requestedPaths, contains('/api/v1/rooms/server-alpha'));
      expect(requestedPaths, contains('/api/v1/rooms/server-alpha/live'));

      await _openLiveChannelFromHeader(tester);

      await tester.tap(find.widgetWithText(ui.Button, '加入'));
      await tester.pumpAndSettle();

      final handler = windowController.closeRequestHandler;
      expect(handler, isNotNull);
      expect(await handler!(), isTrue);

      expect(requestedPaths, contains('/api/v1/rooms/server-alpha/live/me'));
      expect(liveSession.disconnects, 1);
      expect(liveSession.micMutes.last, isFalse);
      expect(liveSession.outputMutes.last, isFalse);
      expect(liveSession.inputVolumes.last, greaterThan(0));
      expect(liveSession.outputVolumes.last, greaterThan(0));
      expect(events, ['hide', 'realtime-stop', 'exit-session', 'terminate']);
      expect(events, isNot(contains('logout')));
    },
  );

  testWidgets(
    'Android task removal leaves live before native process termination',
    (WidgetTester tester) async {
      final events = <String>[];
      final requestedPaths = <String>[];
      final liveSession = _FakeLiveSession();
      final liveSessionController = _FakeLiveSessionController(
        session: liveSession,
      );
      final androidSystemService = _TaskRemovalAndroidSystemService();
      addTearDown(androidSystemService.dispose);

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: HomePage(
            app: _homeTestAppContext(
              onExitSessionForAppExit: () async => events.add('exit-session'),
              requestedPaths: requestedPaths,
            ),
            audioDeviceStore: const _FakeAudioDeviceStore(),
            liveSessionController: liveSessionController,
            realtime: _RecordingRealtimeService(events),
            androidSystemService: androidSystemService,
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Alpha Room'));
      await tester.pumpAndSettle();
      await _openLiveChannelFromHeader(tester);
      await tester.tap(find.widgetWithText(ui.Button, '加入'));
      await tester.pumpAndSettle();

      final removal = androidSystemService.removeTask();
      await removal.completed;
      await tester.pumpAndSettle();

      expect(requestedPaths, contains('/api/v1/rooms/server-alpha/live/me'));
      expect(liveSession.disconnects, 1);
      expect(events, ['realtime-stop', 'exit-session']);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('settings audio volumes sync to live channel sliders', (
    WidgetTester tester,
  ) async {
    final liveSession = _FakeLiveSession();
    final liveSessionController = _FakeLiveSessionController(
      session: liveSession,
      audioDeviceStore: const _FakeAudioDeviceStore(
        inputVolume: 0.35,
        outputVolume: 0.75,
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(),
          audioDeviceStore: const _FakeAudioDeviceStore(
            inputVolume: 0.62,
            outputVolume: 0.27,
          ),
          liveSessionController: liveSessionController,
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();
    await _openLiveChannelFromHeader(tester);
    await tester.tap(find.widgetWithText(ui.Button, '加入'));
    await tester.pumpAndSettle();

    expect(liveSession.inputVolumes.last, closeTo(0.35, 1e-9));
    expect(liveSession.outputVolumes.last, closeTo(0.75, 1e-9));

    await tester.tap(find.byTooltip('设置'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('语音和视频').first);
    await tester.pumpAndSettle();

    expect(find.text('设置'), findsOneWidget);
    expect(find.text('输入音量'), findsOneWidget);
    expect(liveSession.inputVolumes.last, closeTo(0.62, 1e-9));
    expect(liveSession.outputVolumes.last, closeTo(0.27, 1e-9));
    expect(liveSession.micMutes, isNot(contains(true)));
    expect(liveSession.outputMutes, isEmpty);

    await tester.tap(find.byTooltip('设置'));
    await tester.pumpAndSettle();
    await _openLiveChannelFromHeader(tester);

    final hover = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await hover.addPointer(location: tester.getCenter(_liveControl('mic')));
    await tester.pump();
    _expectLiveVolumeFill(tester, '麦克风输入音量', 0.62);

    await hover.moveTo(tester.getCenter(_liveControl('headphones')));
    await tester.pump();
    _expectLiveVolumeFill(tester, '语音输出音量', 0.27);
    await hover.removePointer();
    await tester.pump();

    expect(tester.takeException(), isNull);
  });

  testWidgets('settings profile save submits changed login username', (
    WidgetTester tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(900, 900);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);

    final accountUpdates = <Map<String, Object?>>[];
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async =>
          'access-token',
      httpClient: MockClient((request) async {
        if (request.url.path == '/api/v1/me') {
          return _jsonResponse(_currentUserJson);
        }
        if (request.url.path == '/api/v1/users/search') {
          return _jsonResponse({'users': []});
        }
        if (request.url.path == '/api/v1/users/me/account') {
          final body =
              jsonDecode(utf8.decode(request.bodyBytes))
                  as Map<String, Object?>;
          accountUpdates.add(body);
          return _jsonResponse({
            'user': {..._currentUserJson, ...body},
          });
        }
        return http.Response('unexpected request: ${request.url}', 404);
      }),
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: SettingsPage(
          isSubWindow: true,
          currentUser: CurrentUser.fromJson(_currentUserJson),
          api: api,
          apiBaseUrl: 'http://example.test/api/v1',
          systemAudioDevices: SystemAudioDevices(supported: false),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.widgetWithText(ui.Button, '保存登录用户名'), findsNothing);
    expect(find.text('合法'), findsNothing);

    final usernameField = find.byWidgetPredicate(
      (widget) => widget is TextField && widget.controller?.text == 'kai',
    );
    await tester.enterText(usernameField, 'kai_new');
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();

    expect(find.text('合法'), findsOneWidget);
    final saveProfileButton = find.widgetWithText(ui.Button, '保存用户资料');
    if (saveProfileButton.evaluate().isEmpty) {
      await tester.drag(find.byType(ListView).last, const Offset(0, -520));
      await tester.pumpAndSettle();
    }
    expect(saveProfileButton, findsOneWidget);
    await tester.tap(saveProfileButton);
    await tester.pumpAndSettle();

    expect(accountUpdates, [
      {'username': 'kai_new'},
    ]);
    expect(find.text('合法'), findsNothing);
  });

  testWidgets('settings username availability marks duplicate invalid', (
    WidgetTester tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(900, 900);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);

    final accountUpdates = <Map<String, Object?>>[];
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async =>
          'access-token',
      httpClient: MockClient((request) async {
        if (request.url.path == '/api/v1/me') {
          return _jsonResponse(_currentUserJson);
        }
        if (request.url.path == '/api/v1/users/search') {
          return _jsonResponse({
            'users': [
              _userJson(
                id: 'user-2',
                username: 'taken_name',
                displayName: 'Taken',
              ),
            ],
          });
        }
        if (request.url.path == '/api/v1/users/me/account') {
          final body =
              jsonDecode(utf8.decode(request.bodyBytes))
                  as Map<String, Object?>;
          accountUpdates.add(body);
          return _jsonResponse({
            'user': {..._currentUserJson, ...body},
          });
        }
        return http.Response('unexpected request: ${request.url}', 404);
      }),
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: SettingsPage(
          isSubWindow: true,
          currentUser: CurrentUser.fromJson(_currentUserJson),
          api: api,
          apiBaseUrl: 'http://example.test/api/v1',
          systemAudioDevices: SystemAudioDevices(supported: false),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final usernameField = find.byWidgetPredicate(
      (widget) => widget is TextField && widget.controller?.text == 'kai',
    );
    await tester.enterText(usernameField, 'Taken_Name');
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();

    expect(find.text('不合法'), findsOneWidget);
    expect(find.byTooltip('该登录用户名已被其他用户使用'), findsOneWidget);

    final saveProfileButton = find.widgetWithText(ui.Button, '保存用户资料');
    if (saveProfileButton.evaluate().isEmpty) {
      await tester.drag(find.byType(ListView).last, const Offset(0, -520));
      await tester.pumpAndSettle();
    }
    await tester.tap(saveProfileButton);
    await tester.pumpAndSettle();

    expect(accountUpdates, isEmpty);
  });

  testWidgets(
    'settings email binding requires verification and keeps verified status',
    (WidgetTester tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(900, 900);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);

      final accountUpdates = <Map<String, Object?>>[];
      final emailVerification = _FakeEmailVerificationController();
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async =>
            'access-token',
        httpClient: MockClient((request) async {
          if (request.url.path == '/api/v1/me') {
            return _jsonResponse(_currentUserJson);
          }
          if (request.url.path == '/api/v1/auth/sessions') {
            return _jsonResponse([]);
          }
          if (request.url.path == '/api/v1/users/me/account') {
            final body =
                jsonDecode(utf8.decode(request.bodyBytes))
                    as Map<String, Object?>;
            accountUpdates.add(body);
            return _jsonResponse({
              'user': {
                ..._currentUserJson,
                'email': body['email'],
                'email_verified': true,
              },
            });
          }
          return http.Response('unexpected request: ${request.url}', 404);
        }),
      );

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: SettingsPage(
            isSubWindow: true,
            initialSection: SettingsSection.security,
            currentUser: CurrentUser.fromJson(_currentUserJson),
            api: api,
            apiBaseUrl: 'http://example.test/api/v1',
            emailVerificationController: emailVerification,
            systemAudioDevices: SystemAudioDevices(supported: false),
          ),
        ),
      );
      await tester.pumpAndSettle();

      final emailField = find.descendant(
        of: find.byKey(const ValueKey('settings-email-input')),
        matching: find.byType(TextField),
      );
      final verifyButton = find.byKey(
        const ValueKey('settings-email-verification-button'),
      );
      expect(
        find.byKey(const ValueKey('settings-email-verified')),
        findsOneWidget,
      );
      expect(verifyButton, findsNothing);

      await tester.enterText(emailField, '');
      await tester.pump();
      expect(
        find.byKey(const ValueKey('settings-email-verified')),
        findsNothing,
      );
      expect(verifyButton, findsNothing);

      await tester.enterText(emailField, 'new@example.test');
      await tester.pump();
      expect(verifyButton, findsOneWidget);

      final saveButton = find.widgetWithText(ui.Button, '保存绑定信息');
      await tester.ensureVisible(saveButton);
      await tester.tap(saveButton);
      await tester.pump();
      expect(find.text('请先验证邮箱'), findsOneWidget);
      expect(accountUpdates, isEmpty);

      await tester.ensureVisible(verifyButton);
      await tester.pumpAndSettle();
      await tester.tap(verifyButton);
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('auth-email-verification-code')),
        '123456',
      );
      await tester.tap(find.widgetWithText(ui.Button, '验证'));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('settings-email-verified')),
        findsOneWidget,
      );
      expect(verifyButton, findsNothing);

      await tester.ensureVisible(saveButton);
      await tester.tap(saveButton);
      await tester.pumpAndSettle();
      expect(accountUpdates, [
        {
          'email': 'new@example.test',
          'email_verification_token': 'email-verification-token',
        },
      ]);
      expect(
        find.byKey(const ValueKey('settings-email-verified')),
        findsOneWidget,
      );

      await tester.ensureVisible(emailField);
      await tester.pumpAndSettle();
      await tester.enterText(emailField, 'changed-again@example.test');
      await tester.pump();
      expect(
        find.byKey(const ValueKey('settings-email-verified')),
        findsNothing,
      );
      expect(verifyButton, findsOneWidget);
      expect(emailVerification.calls, [
        'available:new@example.test',
        'inspect:new@example.test',
        'start:new@example.test',
        'verify:email-challenge:123456',
      ]);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('settings about section checks updates and opens feedback mail', (
    WidgetTester tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(980, 760);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);

    final feedbackDrafts = <FeedbackMailDraft>[];
    final autoUpdateWrites = <bool>[];
    var updateCheckRequests = 0;
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async =>
          'access-token',
      httpClient: MockClient((request) async {
        if (request.url.path == '/api/v1/me') {
          return _jsonResponse(_currentUserJson);
        }
        if (request.url.path == '/api/v1/app/version') {
          return _jsonResponse({
            'latest_version': '0.4.0',
            'minimum_supported_version': '0.4.0',
          });
        }
        return http.Response('unexpected request: ${request.url}', 404);
      }),
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: SettingsPage(
          isSubWindow: true,
          currentUser: CurrentUser.fromJson(_currentUserJson),
          api: api,
          apiBaseUrl: 'http://example.test/api/v1',
          appVersion: '0.4.0',
          feedbackMailService: _FakeFeedbackMailService(feedbackDrafts),
          autoUpdatePromptStore: _FakeAutoUpdatePromptStore(
            autoUpdateWrites,
            initialValue: true,
          ),
          installInfoService: const _FakeInstallInfoService(
            '2026-07-01T12:34:56',
          ),
          releaseUpdateService: ReleaseUpdateService(
            httpClient: MockClient((request) async {
              updateCheckRequests += 1;
              return http.Response('''
                <ListBucketResult>
                  <Contents><Key>releases/GangChat_v0.4.0.exe</Key></Contents>
                </ListBucketResult>
              ''', 200);
            }),
          ),
          systemAudioDevices: SystemAudioDevices(supported: false),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('关于Gang Chat').first);
    await tester.pumpAndSettle();

    expect(find.text('版本信息'), findsOneWidget);
    expect(find.text('版本编号'), findsOneWidget);
    expect(find.text('0.4.0'), findsOneWidget);
    expect(find.text('发行时间'), findsOneWidget);
    expect(find.text('上次更新时间'), findsOneWidget);
    expect(
      find.text(officialVersionTimeLabel(gangChatClientReleaseTimestamp)),
      findsOneWidget,
    );
    expect(find.text('2026/07/01 12:34'), findsOneWidget);
    expect(find.text('自动提示更新'), findsOneWidget);
    expect(find.widgetWithText(ui.Button, '检查更新'), findsOneWidget);
    expect(find.widgetWithText(ui.Button, '意见反馈'), findsOneWidget);

    await tester.tap(find.byTooltip('刷新设置'));
    await tester.pumpAndSettle();

    expect(updateCheckRequests, 0);

    final autoUpdateSwitch = find.descendant(
      of: find.ancestor(of: find.text('自动提示更新'), matching: find.byType(Row)),
      matching: find.byType(ui.UiSwitch),
    );
    expect(autoUpdateSwitch, findsOneWidget);
    expect(tester.widget<ui.UiSwitch>(autoUpdateSwitch).value, isTrue);

    await tester.tap(autoUpdateSwitch);
    await tester.pumpAndSettle();

    expect(autoUpdateWrites, [false]);
    expect(tester.widget<ui.UiSwitch>(autoUpdateSwitch).value, isFalse);

    await tester.tap(find.widgetWithText(ui.Button, '检查更新'));
    await tester.pumpAndSettle();

    expect(updateCheckRequests, 1);
    expect(find.text('当前已是最新版本'), findsWidgets);

    await tester.tap(find.widgetWithText(ui.Button, '意见反馈'));
    await tester.pumpAndSettle();

    expect(feedbackDrafts, hasLength(1));
    expect(feedbackDrafts.single.from, 'kai@example.com');
    expect(feedbackDrafts.single.to, 'gang-chat@outlook.com');
    expect(feedbackDrafts.single.subject, contains('v0.4.0'));
  });

  testWidgets('settings about check opens update page for newer release', (
    WidgetTester tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(980, 760);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);
    final autoUpdateWrites = <bool>[];
    final autoUpdateStore = _FakeAutoUpdatePromptStore(
      autoUpdateWrites,
      initialValue: true,
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: SettingsPage(
          isSubWindow: true,
          currentUser: CurrentUser.fromJson(_currentUserJson),
          appVersion: '0.4.0',
          autoUpdatePromptStore: autoUpdateStore,
          installInfoService: const _FakeInstallInfoService('2026/07/01'),
          releaseUpdateService: ReleaseUpdateService(
            httpClient: MockClient((request) async {
              return http.Response('''
                <ListBucketResult>
                  <Contents>
                    <Key>releases/GangChat_v0.4.1.exe</Key>
                    <LastModified>2026-07-08T01:02:03.000Z</LastModified>
                  </Contents>
                </ListBucketResult>
              ''', 200);
            }),
          ),
          systemAudioDevices: SystemAudioDevices(supported: false),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('关于Gang Chat').first);
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ui.Button, '检查更新'));
    await tester.pumpAndSettle();

    expect(find.text('发现新版本'), findsOneWidget);
    expect(find.text('v0.4.1'), findsWidgets);
    expect(find.text('发行时间'), findsOneWidget);
    expect(find.text('版本日志'), findsOneWidget);
    expect(find.text('无'), findsOneWidget);
    expect(find.widgetWithText(ui.Button, '忽略此版本'), findsOneWidget);
    expect(find.widgetWithText(ui.Button, '下载新版本'), findsOneWidget);
    expect(find.widgetWithText(ui.Button, '继续使用'), findsNothing);
    expect(find.byTooltip('重新检查'), findsOneWidget);

    await tester.tap(find.widgetWithText(ui.Button, '下载新版本'));
    await tester.pumpAndSettle();

    expect(find.text('下载新版本'), findsWidgets);
    expect(find.textContaining('会退出当前程序并启动安装程序'), findsOneWidget);

    await tester.tap(find.widgetWithText(ui.Button, '取消'));
    await tester.pumpAndSettle();

    expect(find.text('发现新版本'), findsOneWidget);

    await tester.tap(find.widgetWithText(ui.Button, '忽略此版本'));
    await tester.pumpAndSettle();

    expect(find.text('忽略此版本'), findsWidgets);
    expect(find.textContaining('不会再主动提示该版本'), findsOneWidget);

    await tester.tap(find.widgetWithText(ui.Button, '取消'));
    await tester.pumpAndSettle();

    expect(find.text('发现新版本'), findsOneWidget);

    await tester.tap(find.widgetWithText(ui.Button, '忽略此版本'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ui.Button, '忽略此版本').last);
    await tester.pumpAndSettle();

    expect(find.text('发现新版本'), findsNothing);
    expect(find.text('版本信息'), findsOneWidget);
    expect(find.widgetWithText(ui.Button, '检查更新'), findsOneWidget);
    expect(autoUpdateStore.ignoredVersion, '0.4.1');
  });

  testWidgets('settings audio sliders apply live mute coupling rules', (
    WidgetTester tester,
  ) async {
    final volumeChanges = <String>[];

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: SettingsPage(
          isSubWindow: true,
          audioDeviceStore: const _FakeAudioDeviceStore(
            inputVolume: 0.62,
            outputVolume: 0,
          ),
          audioDeviceService: const _FakeSettingsAudioDeviceService(),
          systemAudioDevices: SystemAudioDevices(supported: false),
          onVolumeChanged: (kind, volume) {
            volumeChanges.add('$kind:${volume.toStringAsFixed(2)}');
          },
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('\u8bed\u97f3\u548c\u89c6\u9891').first);
    await tester.pumpAndSettle();

    final sliders = find.byType(Slider);
    double sliderValue(int index) {
      return tester.widget<Slider>(sliders.at(index)).value;
    }

    expect(sliders, findsNWidgets(2));
    expect(sliderValue(0), 0);
    expect(sliderValue(1), 0);
    expect(volumeChanges, contains('audioinput:0.00'));
    expect(volumeChanges, contains('audiooutput:0.00'));

    tester.widget<Slider>(sliders.at(0)).onChanged!(0.8);
    await tester.pump();

    expect(sliderValue(0), closeTo(0.8, 1e-9));
    expect(sliderValue(1), closeTo(0.5, 1e-9));
    expect(volumeChanges, contains('audioinput:0.80'));
    expect(volumeChanges, contains('audiooutput:0.50'));

    tester.widget<Slider>(sliders.at(1)).onChanged!(0);
    await tester.pump();

    expect(sliderValue(0), 0);
    expect(sliderValue(1), 0);
    expect(volumeChanges, contains('audioinput:0.00'));
    expect(volumeChanges, contains('audiooutput:0.00'));
    expect(tester.takeException(), isNull);
  });
  testWidgets('settings nonzero audio volumes clear live mute states', (
    WidgetTester tester,
  ) async {
    final liveStateUpdates = <Map<String, Object?>>[];
    final liveSession = _FakeLiveSession();
    final liveSessionController = _FakeLiveSessionController(
      session: liveSession,
      audioDeviceStore: const _FakeAudioDeviceStore(
        inputVolume: 0.35,
        outputVolume: 0.75,
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(liveStateUpdates: liveStateUpdates),
          audioDeviceStore: const _FakeAudioDeviceStore(
            inputVolume: 0.62,
            outputVolume: 0.27,
          ),
          liveSessionController: liveSessionController,
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();
    await _openLiveChannelFromHeader(tester);
    await tester.tap(find.widgetWithText(ui.Button, '加入'));
    await tester.pumpAndSettle();

    await tester.tap(_liveControl('headphones'));
    await tester.pumpAndSettle();
    expect(
      find.descendant(
        of: _liveControl('mic'),
        matching: find.byIcon(Icons.mic_off),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: _liveControl('headphones'),
        matching: find.byIcon(Icons.headset_off),
      ),
      findsOneWidget,
    );

    liveStateUpdates.clear();
    liveSession.micMutes.clear();
    liveSession.outputMutes.clear();

    await tester.tap(find.byTooltip('设置'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('语音和视频').first);
    await tester.pumpAndSettle();

    expect(liveSession.inputVolumes.last, closeTo(0.62, 1e-9));
    expect(liveSession.outputVolumes.last, closeTo(0.27, 1e-9));
    expect(liveSession.micMutes, contains(false));
    expect(liveSession.outputMutes, contains(false));
    expect(liveStateUpdates.any((body) => body['mic_muted'] == false), isTrue);
    expect(
      liveStateUpdates.any((body) => body['headphones_muted'] == false),
      isTrue,
    );

    await tester.tap(find.byTooltip('设置'));
    await tester.pumpAndSettle();
    await _openLiveChannelFromHeader(tester);

    expect(
      find.descendant(
        of: _liveControl('mic'),
        matching: find.byIcon(Icons.mic),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: _liveControl('headphones'),
        matching: find.byIcon(Icons.headphones),
      ),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('microphone unmute clears headphones mute', (
    WidgetTester tester,
  ) async {
    final liveStateUpdates = <Map<String, Object?>>[];
    final liveSession = _FakeLiveSession();
    final liveSessionController = _FakeLiveSessionController(
      session: liveSession,
      audioDeviceStore: const _FakeAudioDeviceStore(),
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(liveStateUpdates: liveStateUpdates),
          liveSessionController: liveSessionController,
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();
    await _openLiveChannelFromHeader(tester);
    await tester.tap(find.widgetWithText(ui.Button, '加入'));
    await tester.pumpAndSettle();

    await tester.tap(_liveControl('headphones'));
    await tester.pump();
    expect(liveSession.outputVolumes.last, 0.0);
    expect(liveSession.inputVolumes.last, 0.0);
    expect(liveSession.micMutes, contains(true));
    expect(
      find.descendant(
        of: _liveControl('mic'),
        matching: find.byIcon(Icons.mic_off),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: _liveControl('headphones'),
        matching: find.byIcon(Icons.headset_off),
      ),
      findsOneWidget,
    );
    await tester.pumpAndSettle();
    expect(liveStateUpdates.last['mic_muted'], true);
    expect(liveStateUpdates.last['headphones_muted'], true);

    liveStateUpdates.clear();
    liveSession.micMutes.clear();
    liveSession.outputMutes.clear();

    await tester.tap(_liveControl('mic'));
    await tester.pump();
    expect(liveSession.inputVolumes.last, closeTo(0.35, 1e-9));
    expect(liveSession.outputVolumes.last, closeTo(0.75, 1e-9));
    expect(liveSession.micMutes, contains(false));
    expect(liveSession.outputMutes, contains(false));
    expect(
      find.descendant(
        of: _liveControl('mic'),
        matching: find.byIcon(Icons.mic),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: _liveControl('headphones'),
        matching: find.byIcon(Icons.headphones),
      ),
      findsOneWidget,
    );
    await tester.pumpAndSettle();
    expect(liveStateUpdates.last['mic_muted'], false);
    expect(liveStateUpdates.last['headphones_muted'], false);
    expect(
      find.descendant(
        of: _liveControl('mic'),
        matching: find.byIcon(Icons.mic),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: _liveControl('headphones'),
        matching: find.byIcon(Icons.headphones),
      ),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });
}

class _MemoryStickerPackStore extends StickerPackStore {
  List<StickerPack>? packs;
  String? lastWrittenUserId;

  @override
  Future<List<StickerPack>?> readPersonalPacks({
    required String userId,
    required String apiBaseUrl,
  }) async => packs;

  @override
  Future<void> writePersonalPacks({
    required String userId,
    required String apiBaseUrl,
    required List<StickerPack> packs,
  }) async {
    lastWrittenUserId = userId;
    this.packs = packs;
  }
}

class _TaskRemovalAndroidSystemService extends AndroidSystemService {
  final StreamController<AndroidTaskRemovalRequest> _taskRemovals =
      StreamController<AndroidTaskRemovalRequest>.broadcast(sync: true);

  @override
  bool get isSupported => true;

  @override
  Stream<String> get selectedNotificationRoomIds => const Stream.empty();

  @override
  Stream<AndroidTaskRemovalRequest> get taskRemovalRequests =>
      _taskRemovals.stream;

  @override
  Stream<AndroidPushRegistration> get pushRegistrationChanges =>
      const Stream.empty();

  @override
  Future<AndroidPushRegistration?> pushRegistration() async => null;

  @override
  Future<String?> takeInitialNotificationRoomId() async => null;

  @override
  Future<bool> notificationPreferenceEnabled() async => false;

  @override
  Future<bool> requestBluetoothConnectPermission() async => true;

  @override
  Future<void> enterBackground() async {}

  @override
  Future<void> enterForeground() async {}

  AndroidTaskRemovalRequest removeTask() {
    final request = AndroidTaskRemovalRequest();
    _taskRemovals.add(request);
    return request;
  }

  Future<void> dispose() => _taskRemovals.close();
}
