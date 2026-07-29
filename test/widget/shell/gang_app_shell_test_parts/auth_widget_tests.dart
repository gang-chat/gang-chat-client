part of '../gang_app_shell_test.dart';

void registerShellAuthWidgetTests() {
  testWidgets(
    'android auth keeps the registration field group centered in both modes',
    (WidgetTester tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(360, 720);
      addTearDown(tester.view.reset);

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
          home: LoginPage(
            sizeForMode: (registering, {showingError = false}) =>
                Size(430, registering ? 436 : 368),
            consumeInitialWindowLock: () => true,
            lockAuthWindow:
                ({
                  bool registering = false,
                  bool moveWindow = false,
                  bool centerWindow = false,
                  Size? size,
                }) async {},
            onSubmit: (_, {required rememberPassword}) async {},
          ),
        ),
      );
      await tester.pump();

      final loginSurface = tester.getRect(
        find.byKey(const ValueKey('auth-surface')),
      );
      final loginFirstInput = tester.getRect(find.byType(ui.Input).first);
      final scaffold = tester.widget<Scaffold>(find.byType(Scaffold));
      final systemUi = tester.widget<AnnotatedRegion<SystemUiOverlayStyle>>(
        find.byType(AnnotatedRegion<SystemUiOverlayStyle>),
      );

      expect(loginSurface.height, 436);
      expect(scaffold.backgroundColor, ui.UiColors.surfaceLow);
      expect(systemUi.value.statusBarColor, ui.UiColors.surfaceLow);
      expect(systemUi.value.systemNavigationBarColor, ui.UiColors.surfaceLow);

      await tester.tap(find.text('注册'));
      await tester.pump();

      final inputs = find.byType(ui.Input);
      expect(inputs, findsNWidgets(4));
      final firstInput = tester.getRect(inputs.at(0));
      final lastInput = tester.getRect(inputs.at(3));
      expect(firstInput.top, closeTo(loginFirstInput.top, 0.01));
      expect(
        (firstInput.top + lastInput.bottom) / 2,
        closeTo(tester.view.physicalSize.height / 2, 1.01),
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('desktop auth keeps its original mode-specific geometry', (
    WidgetTester tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(800, 700);
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
        home: LoginPage(
          sizeForMode: (registering, {showingError = false}) =>
              Size(430, registering ? 436 : 368),
          consumeInitialWindowLock: () => true,
          lockAuthWindow:
              ({
                bool registering = false,
                bool moveWindow = false,
                bool centerWindow = false,
                Size? size,
              }) async {},
          onSubmit: (_, {required rememberPassword}) async {},
        ),
      ),
    );
    await tester.pump();

    var surface = tester.getRect(find.byKey(const ValueKey('auth-surface')));
    expect(surface, const Rect.fromLTWH(185, 0, 430, 368));
    expect(find.byType(AnnotatedRegion<SystemUiOverlayStyle>), findsNothing);

    await tester.tap(find.text('注册'));
    await tester.pump();

    surface = tester.getRect(find.byKey(const ValueKey('auth-surface')));
    expect(surface, const Rect.fromLTWH(185, 0, 430, 436));
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'android auth submit does not overwrite authenticated avatar metadata',
    (WidgetTester tester) async {
      final store = _MemoryLoginAccountHistoryStore([
        LoginAccountRecord(
          login: 'kai',
          password: 'old-password',
          useCount: 2,
          updatedAt: DateTime.utc(2026, 1, 1),
        ),
      ]);

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
          home: LoginPage(
            sizeForMode: (_, {showingError = false}) => const Size(430, 436),
            consumeInitialWindowLock: () => true,
            lockAuthWindow:
                ({
                  bool registering = false,
                  bool moveWindow = false,
                  bool centerWindow = false,
                  Size? size,
                }) async {},
            accountHistoryStore: store,
            submitPersistsAccountHistory: true,
            onSubmit: (request, {required rememberPassword}) async {
              store.records = rememberLoginAccount(
                records: store.records,
                login: request.login,
                password: request.password,
                rememberPassword: rememberPassword,
                avatarUrl: '/assets/avatar-kai/custom.png',
                defaultAvatarKey: 'green-2',
                updateAvatarMetadata: true,
                now: DateTime.utc(2026, 7, 23),
              );
            },
          ),
        ),
      );
      await tester.pump();
      await tester.pump();

      await tester.enterText(_textFieldWithHint('登录用户名或邮箱地址'), 'kai');
      await tester.enterText(_textFieldWithHint('密码'), 'new-password');
      await tester.tap(find.widgetWithText(ui.Button, '登录'));
      await tester.pump();

      final record = findLoginAccountRecord(store.records, 'kai')!;
      expect(record.avatarUrl, '/assets/avatar-kai/custom.png');
      expect(record.defaultAvatarKey, 'green-2');
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('auth surface fits a narrow mobile viewport', (
    WidgetTester tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(320, 480);
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: LoginPage(
          sizeForMode: (_, {showingError = false}) => const Size(430, 500),
          consumeInitialWindowLock: () => true,
          lockAuthWindow:
              ({
                bool registering = false,
                bool moveWindow = false,
                bool centerWindow = false,
                Size? size,
              }) async {},
          onSubmit: (_, {required rememberPassword}) async {},
        ),
      ),
    );
    await tester.pump();

    final surfaceRect = tester.getRect(
      find.byKey(const ValueKey('auth-surface')),
    );
    expect(surfaceRect, const Rect.fromLTWH(0, 0, 320, 480));
    expect(tester.getRect(find.byType(ui.Input).first).left, greaterThan(0));
    expect(tester.getRect(find.byType(ui.Input).first).right, lessThan(320));
    expect(tester.takeException(), isNull);
  });

  testWidgets('app renders login entrypoint on real auth gate', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(GangApp(tokenStore: _MemoryTokenStore()));
    await tester.pump();

    expect(find.text('Gang Chat'), findsOneWidget);
    expect(find.text('登录用户名或邮箱地址'), findsOneWidget);
    expect(find.text('密码'), findsOneWidget);
    expect(find.widgetWithText(ui.Button, '登录'), findsOneWidget);
    expect(find.text('注册'), findsOneWidget);
    expect(find.text('记住密码'), findsOneWidget);
    expect(find.text('忘记密码？'), findsOneWidget);
    expect(find.byType(ui.UiCheckbox), findsOneWidget);
    expect(find.byType(ui.UiSwitch), findsNothing);
    expect(find.byTooltip('显示密码'), findsOneWidget);

    final authSurfaceRect = tester.getRect(
      find.byKey(const ValueKey('auth-surface')),
    );
    final modeSwitchRect = tester.getRect(
      find.byWidgetPredicate((widget) => widget is ui.SegmentedControl),
    );
    final titleRect = tester.getRect(find.text('Gang Chat'));
    expect(titleRect.height, greaterThanOrEqualTo(20));
    expect(titleRect.height, lessThanOrEqualTo(28));
    expect(find.byKey(const ValueKey('auth-brand-icon')), findsOneWidget);
    expect(
      tester
          .widgetList<MouseRegion>(
            find.ancestor(
              of: find.text('Gang Chat'),
              matching: find.byType(MouseRegion),
            ),
          )
          .map((region) => region.cursor),
      contains(SystemMouseCursors.basic),
    );
    expect(modeSwitchRect.top - authSurfaceRect.top, closeTo(82, 0.01));
    _expectSubmitButtonFullWidth(tester, submitLabel: '登录');
    expect(find.text('请输入账号和密码后继续'), findsNothing);
    final normalSurfaceHeight = tester
        .getSize(find.byKey(const ValueKey('auth-surface')))
        .height;
    final normalBottomGap = _submitBottomGap(tester, submitLabel: '登录');
    final normalSubmitRect = tester.getRect(
      find.widgetWithText(ui.Button, '登录'),
    );
    final normalRememberRect = tester.getRect(find.text('记住密码'));
    expect(normalBottomGap, greaterThanOrEqualTo(34));

    await tester.tap(find.text('忘记密码？'));
    await tester.pump();

    await tester.tap(find.widgetWithText(ui.Button, '登录'));
    await tester.pump();

    expect(find.text('请输入账号和密码后继续'), findsOneWidget);
    expect(
      tester.getSize(find.byKey(const ValueKey('auth-surface'))).height,
      closeTo(normalSurfaceHeight, 0.01),
    );
    expect(
      _submitBottomGap(tester, submitLabel: '登录'),
      closeTo(normalBottomGap, 0.01),
    );
    _expectRectCloseTo(
      tester.getRect(find.widgetWithText(ui.Button, '登录')),
      normalSubmitRect,
    );
    _expectRectCloseTo(tester.getRect(find.text('记住密码')), normalRememberRect);
    final passwordRect = tester.getRect(_textFieldWithHint('密码'));
    final errorRect = tester.getRect(find.text('请输入账号和密码后继续'));
    final rememberRect = tester.getRect(find.text('记住密码'));
    expect(errorRect.top, greaterThan(passwordRect.bottom));
    expect(errorRect.bottom, lessThan(rememberRect.top));
    expect(tester.takeException(), isNull);
  });

  testWidgets('auth login error layout stays scrollable across retries', (
    WidgetTester tester,
  ) async {
    var attempts = 0;

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: LoginPage(
          sizeForMode: (_, {showingError = false}) => const Size(416, 368),
          consumeInitialWindowLock: () => true,
          lockAuthWindow:
              ({
                bool registering = false,
                bool moveWindow = false,
                bool centerWindow = false,
                Size? size,
              }) async {},
          onSubmit: (_, {required rememberPassword}) async {
            attempts += 1;
            throw Exception('登录失败');
          },
        ),
      ),
    );
    await tester.pump();

    await tester.enterText(_textFieldWithHint('登录用户名或邮箱地址'), 'kai');
    await tester.enterText(_textFieldWithHint('密码'), 'secret123');
    await tester.tap(find.widgetWithText(ui.Button, '登录'));
    await tester.pumpAndSettle();

    expect(attempts, 1);
    expect(tester.takeException(), isNull);

    await tester.ensureVisible(find.widgetWithText(ui.Button, '登录'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ui.Button, '登录'));
    await tester.pumpAndSettle();

    expect(attempts, 2);
    expect(tester.takeException(), isNull);
  });

  testWidgets('forgot password verifies email and opens reset dialog', (
    WidgetTester tester,
  ) async {
    final requestedPaths = <String>[];
    final controller = PasswordResetController(
      apiBaseUrl: 'https://api.example.test/api/v1',
      authClientFactory: (baseUrl) => AuthClient(
        baseUrl: baseUrl,
        httpClient: MockClient((request) async {
          requestedPaths.add(request.url.path);
          switch (request.url.path) {
            case '/api/v1/auth/password-reset/inspect':
              return _jsonResponse({
                'can_send': true,
                'masked_email': 'k***@example.test',
                'retry_after': 0,
              });
            case '/api/v1/auth/password-reset/start':
              return _jsonResponse({
                'challenge_id': 'challenge-1',
                'masked_email': 'k***@example.test',
                'retry_after': 60,
              });
            case '/api/v1/auth/password-reset/verify':
              return _jsonResponse({'reset_token': 'reset-token'});
            case '/api/v1/auth/password-reset/complete':
              return _jsonResponse({'ok': true});
          }
          return http.Response('unexpected request', 404);
        }),
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: LoginPage(
          sizeForMode: (_, {showingError = false}) => const Size(430, 368),
          consumeInitialWindowLock: () => true,
          lockAuthWindow:
              ({
                bool registering = false,
                bool moveWindow = false,
                bool centerWindow = false,
                Size? size,
              }) async {},
          passwordResetController: controller,
          onSubmit: (_, {required rememberPassword}) async {},
        ),
      ),
    );
    await tester.enterText(_textFieldWithHint('登录用户名或邮箱地址'), 'kai');
    await tester.tap(find.text('忘记密码？'));
    await tester.pumpAndSettle();

    expect(find.text('邮箱验证'), findsOneWidget);
    expect(find.text('已发送验证码到该账号绑定的邮箱 k***@example.test'), findsOneWidget);
    expect(find.text('重新发送(60)'), findsOneWidget);

    await tester.enterText(
      find.byKey(const ValueKey('password-reset-code-input')),
      '123456',
    );
    await tester.tap(
      find.byKey(const ValueKey('password-reset-verify-button')),
    );
    await tester.pumpAndSettle();

    expect(find.text('重置密码'), findsWidgets);
    await tester.enterText(
      find.byKey(const ValueKey('password-reset-new-password')),
      'new-password',
    );
    await tester.enterText(
      find.byKey(const ValueKey('password-reset-confirm-password')),
      'new-password',
    );
    await tester.tap(
      find.byKey(const ValueKey('password-reset-submit-button')),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(requestedPaths, [
      '/api/v1/auth/password-reset/inspect',
      '/api/v1/auth/password-reset/start',
      '/api/v1/auth/password-reset/verify',
      '/api/v1/auth/password-reset/complete',
    ]);
    expect(tester.takeException(), isNull);
  });

  testWidgets('forgot password inspects and inherits an existing cooldown', (
    WidgetTester tester,
  ) async {
    final requestedPaths = <String>[];
    final controller = PasswordResetController(
      apiBaseUrl: 'https://api.example.test/api/v1',
      authClientFactory: (baseUrl) => AuthClient(
        baseUrl: baseUrl,
        httpClient: MockClient((request) async {
          requestedPaths.add(request.url.path);
          if (request.url.path == '/api/v1/auth/password-reset/inspect') {
            return _jsonResponse({
              'can_send': false,
              'challenge_id': 'challenge-existing',
              'masked_email': 'k***@example.test',
              'retry_after': 42,
            });
          }
          return http.Response('must not send during cooldown', 409);
        }),
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: LoginPage(
          sizeForMode: (_, {showingError = false}) => const Size(430, 368),
          consumeInitialWindowLock: () => true,
          lockAuthWindow:
              ({
                bool registering = false,
                bool moveWindow = false,
                bool centerWindow = false,
                Size? size,
              }) async {},
          passwordResetController: controller,
          onSubmit: (_, {required rememberPassword}) async {},
        ),
      ),
    );
    await tester.enterText(_textFieldWithHint('登录用户名或邮箱地址'), 'kai');
    await tester.tap(find.text('忘记密码？'));
    await tester.pumpAndSettle();

    expect(find.text('邮箱验证'), findsOneWidget);
    expect(find.text('重新发送(42)'), findsOneWidget);
    expect(requestedPaths, ['/api/v1/auth/password-reset/inspect']);

    await tester.tap(find.widgetWithText(ui.Button, '取消'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'forgot password opens verification when cooldown inspection omits challenge',
    (WidgetTester tester) async {
      final requestedPaths = <String>[];
      final controller = PasswordResetController(
        apiBaseUrl: 'https://api.example.test/api/v1',
        authClientFactory: (baseUrl) => AuthClient(
          baseUrl: baseUrl,
          httpClient: MockClient((request) async {
            requestedPaths.add(request.url.path);
            if (request.url.path == '/api/v1/auth/password-reset/inspect') {
              return _jsonResponse({
                'can_send': false,
                'masked_email': 'k***@example.test',
                'retry_after': 42,
              });
            }
            if (request.url.path == '/api/v1/auth/password-reset/start') {
              return _jsonResponse({
                'challenge_id': 'challenge-existing',
                'masked_email': 'k***@example.test',
                'retry_after': 42,
              });
            }
            return http.Response('unexpected request', 404);
          }),
        ),
      );

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: LoginPage(
            sizeForMode: (_, {showingError = false}) => const Size(430, 368),
            consumeInitialWindowLock: () => true,
            lockAuthWindow:
                ({
                  bool registering = false,
                  bool moveWindow = false,
                  bool centerWindow = false,
                  Size? size,
                }) async {},
            passwordResetController: controller,
            onSubmit: (_, {required rememberPassword}) async {},
          ),
        ),
      );
      await tester.enterText(_textFieldWithHint('登录用户名或邮箱地址'), 'kai');
      await tester.tap(find.text('忘记密码？'));
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('password-reset-code-input')),
        findsOneWidget,
      );
      expect(find.text('重新发送(42)'), findsOneWidget);
      expect(find.text('验证码已发送，请在 42 秒后重试'), findsNothing);
      expect(requestedPaths, ['/api/v1/auth/password-reset/inspect']);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'settings email verification hides current password for the session',
    (WidgetTester tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(900, 900);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);

      final requestedPaths = <String>[];
      final controller = PasswordResetController(
        apiBaseUrl: 'https://api.example.test/api/v1',
        accessTokenProvider: () async => 'access-token',
        authClientFactory: (baseUrl) => AuthClient(
          baseUrl: baseUrl,
          httpClient: MockClient((request) async {
            requestedPaths.add(request.url.path);
            switch (request.url.path) {
              case '/api/v1/auth/password-reset/inspect':
                return _jsonResponse({
                  'can_send': true,
                  'masked_email': 'k***@example.com',
                  'retry_after': 0,
                });
              case '/api/v1/auth/password-reset/start':
                return _jsonResponse({
                  'challenge_id': 'challenge-1',
                  'masked_email': 'k***@example.com',
                  'retry_after': 60,
                });
              case '/api/v1/auth/password-reset/verify':
                return _jsonResponse({'reset_token': 'reset-token'});
              case '/api/v1/auth/password-reset/claim':
                expect(request.headers['authorization'], 'Bearer access-token');
                return _jsonResponse({'ok': true});
            }
            return http.Response('unexpected request', 404);
          }),
        ),
      );
      final settingsApi = GangApiClient(
        baseUrl: 'https://api.example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async =>
            'access-token',
        httpClient: MockClient((request) async {
          if (request.url.path == '/api/v1/me') {
            return _jsonResponse(_currentUserJson);
          }
          if (request.url.path == '/api/v1/auth/sessions') {
            return _jsonResponse([]);
          }
          return http.Response('unexpected settings request', 404);
        }),
      );

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: SettingsPage(
            isSubWindow: true,
            initialSection: SettingsSection.security,
            currentUser: CurrentUser.fromJson(_currentUserJson),
            api: settingsApi,
            passwordResetController: controller,
            systemAudioDevices: SystemAudioDevices(supported: false),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('当前密码'), findsOneWidget);
      await tester.ensureVisible(find.widgetWithText(ui.Button, '忘记密码'));
      await tester.tap(find.widgetWithText(ui.Button, '忘记密码'));
      await _pumpUntilFound(
        tester,
        find.byKey(const ValueKey('password-reset-code-input')),
      );
      await tester.enterText(
        find.byKey(const ValueKey('password-reset-code-input')),
        '123456',
      );
      await tester.tap(
        find.byKey(const ValueKey('password-reset-verify-button')),
      );
      await tester.pumpAndSettle();

      expect(find.text('当前密码'), findsNothing);
      expect(find.text('新密码'), findsOneWidget);
      expect(find.text('确认新密码'), findsOneWidget);
      expect(find.text('重置密码'), findsOneWidget);
      expect(requestedPaths, [
        '/api/v1/auth/password-reset/inspect',
        '/api/v1/auth/password-reset/start',
        '/api/v1/auth/password-reset/verify',
        '/api/v1/auth/password-reset/claim',
      ]);

      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: SettingsPage(
            isSubWindow: true,
            initialSection: SettingsSection.security,
            currentUser: CurrentUser.fromJson(_currentUserJson),
            api: settingsApi,
            passwordResetController: controller,
            systemAudioDevices: SystemAudioDevices(supported: false),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('当前密码'), findsNothing);

      controller.clearCurrentSessionAuthorization();
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: SettingsPage(
            isSubWindow: true,
            initialSection: SettingsSection.security,
            currentUser: CurrentUser.fromJson(_currentUserJson),
            api: settingsApi,
            passwordResetController: controller,
            systemAudioDevices: SystemAudioDevices(supported: false),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('当前密码'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('windows auth page shows compact custom window controls', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
        home: LoginPage(
          sizeForMode: (_, {showingError = false}) => const Size(430, 368),
          consumeInitialWindowLock: () => true,
          lockAuthWindow:
              ({
                bool registering = false,
                bool moveWindow = false,
                bool centerWindow = false,
                Size? size,
              }) async {},
          windowController: DesktopWindowController(),
          onSubmit: (_, {required rememberPassword}) async {},
        ),
      ),
    );
    await tester.pump();

    expect(find.byTooltip('最小化'), findsOneWidget);
    expect(find.byTooltip('最大化'), findsNothing);
    expect(find.byTooltip('还原'), findsNothing);
    expect(find.byTooltip('关闭'), findsOneWidget);
  });

  testWidgets('auth account history expands, selects and deletes records', (
    WidgetTester tester,
  ) async {
    final store = _MemoryLoginAccountHistoryStore([
      LoginAccountRecord(
        login: 'kai',
        password: 'secret123',
        avatarUrl: '/assets/avatar-kai/custom.png',
        defaultAvatarKey: 'green-2',
        useCount: 3,
        updatedAt: DateTime.utc(2026, 1, 1),
      ),
      LoginAccountRecord(
        login: 'morgan',
        useCount: 1,
        updatedAt: DateTime.utc(2026, 1, 2),
      ),
    ]);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: LoginPage(
          sizeForMode: (_, {showingError = false}) => const Size(430, 368),
          consumeInitialWindowLock: () => true,
          lockAuthWindow:
              ({
                bool registering = false,
                bool moveWindow = false,
                bool centerWindow = false,
                Size? size,
              }) async {},
          accountHistoryStore: store,
          onSubmit: (_, {required rememberPassword}) async {},
        ),
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(_textFieldWithHint('登录用户名或邮箱地址'), findsOneWidget);
    expect(
      tester
          .widget<TextField>(_textFieldWithHint('登录用户名或邮箱地址'))
          .controller!
          .text,
      'morgan',
    );
    expect(
      tester.widget<TextField>(_textFieldWithHint('密码')).controller!.text,
      isEmpty,
    );
    expect(
      tester.widget<ui.UiCheckbox>(find.byType(ui.UiCheckbox)).value,
      isFalse,
    );
    expect(find.byTooltip('清除账号'), findsOneWidget);

    await tester.tap(find.byTooltip('展开账号记录'));
    await tester.pump();

    expect(find.text('kai'), findsOneWidget);
    expect(find.text('morgan'), findsWidgets);
    expect(find.byType(ui.Avatar), findsNWidgets(2));
    expect(
      tester.widget<ui.Avatar>(find.byType(ui.Avatar).first).defaultAvatarKey,
      'green-2',
    );
    expect(
      tester.widget<ui.Avatar>(find.byType(ui.Avatar).first).imageUrl,
      'http://127.0.0.1:21116/assets/avatar-kai/custom.png',
    );
    expect(
      tester.getTopLeft(find.text('kai')).dy,
      lessThan(tester.getTopLeft(find.text('morgan').last).dy),
    );
    expect(find.byTooltip('删除账号记录'), findsNWidgets(2));

    await tester.tapAt(const Offset(8, 8));
    await tester.pump();

    expect(find.byTooltip('收起账号记录'), findsNothing);
    expect(find.byTooltip('展开账号记录'), findsOneWidget);

    await tester.tap(find.byTooltip('清除账号'));
    await tester.pump();

    expect(
      tester
          .widget<TextField>(_textFieldWithHint('登录用户名或邮箱地址'))
          .controller!
          .text,
      isEmpty,
    );
    expect(
      tester.widget<TextField>(_textFieldWithHint('密码')).controller!.text,
      isEmpty,
    );
    expect(
      tester.widget<ui.UiCheckbox>(find.byType(ui.UiCheckbox)).value,
      isFalse,
    );
    expect(find.byTooltip('清除账号'), findsNothing);

    await tester.tap(find.byTooltip('展开账号记录'));
    await tester.pump();

    await tester.tap(find.text('kai'));
    await tester.pump();

    expect(
      tester
          .widget<TextField>(_textFieldWithHint('登录用户名或邮箱地址'))
          .controller!
          .text,
      'kai',
    );
    expect(
      tester.widget<TextField>(_textFieldWithHint('密码')).controller!.text,
      'secret123',
    );
    expect(find.byTooltip('收起账号记录'), findsNothing);

    await tester.tap(find.byTooltip('展开账号记录'));
    await tester.pump();
    await tester.tap(find.byTooltip('删除账号记录').last);
    await tester.pump();

    expect(find.text('morgan'), findsNothing);
    expect(store.records.map((record) => record.login), ['kai']);
  });

  testWidgets(
    'auth account history overlays password field and scrolls itself',
    (WidgetTester tester) async {
      final store = _MemoryLoginAccountHistoryStore([
        for (var index = 0; index < 6; index++)
          LoginAccountRecord(
            login: 'account-$index',
            useCount: index,
            updatedAt: DateTime.utc(2026, 1, index + 1),
          ),
      ]);

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: LoginPage(
            sizeForMode: (_, {showingError = false}) => const Size(430, 368),
            consumeInitialWindowLock: () => true,
            lockAuthWindow:
                ({
                  bool registering = false,
                  bool moveWindow = false,
                  bool centerWindow = false,
                  Size? size,
                }) async {},
            accountHistoryStore: store,
            onSubmit: (_, {required rememberPassword}) async {},
          ),
        ),
      );
      await tester.pump();
      await tester.pump();

      final passwordTopBefore = tester.getTopLeft(_textFieldWithHint('密码')).dy;

      await tester.tap(find.byTooltip('展开账号记录'));
      await tester.pump();

      final passwordRect = tester.getRect(_textFieldWithHint('密码'));
      final dropdownFinder = find.byKey(
        const ValueKey('auth-account-history-dropdown'),
      );
      final dropdownRect = tester.getRect(dropdownFinder);
      final scrollbar = tester.widget<Scrollbar>(
        find.descendant(of: dropdownFinder, matching: find.byType(Scrollbar)),
      );

      expect(
        tester.getTopLeft(_textFieldWithHint('密码')).dy,
        closeTo(passwordTopBefore, 0.01),
      );
      expect(dropdownRect.top, lessThanOrEqualTo(passwordRect.top + 0.01));
      expect(dropdownRect.bottom, greaterThan(passwordRect.bottom));
      expect(dropdownRect.height, closeTo(4 * 38, 0.01));
      expect(scrollbar.thumbVisibility, isTrue);
      expect(scrollbar.trackVisibility, isFalse);

      final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
      await gesture.addPointer(location: Offset.zero);
      addTearDown(gesture.removePointer);
      await gesture.moveTo(tester.getCenter(find.byTooltip('删除账号记录').first));
      await tester.pump(const Duration(milliseconds: 700));

      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('auth fills remembered password for latest login', (
    WidgetTester tester,
  ) async {
    final store = _MemoryLoginAccountHistoryStore([
      LoginAccountRecord(
        login: 'kai',
        useCount: 4,
        updatedAt: DateTime.utc(2026, 1, 1),
      ),
      LoginAccountRecord(
        login: 'morgan',
        password: 'moonbase',
        useCount: 1,
        updatedAt: DateTime.utc(2026, 1, 2),
      ),
    ]);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: LoginPage(
          sizeForMode: (_, {showingError = false}) => const Size(430, 368),
          consumeInitialWindowLock: () => true,
          lockAuthWindow:
              ({
                bool registering = false,
                bool moveWindow = false,
                bool centerWindow = false,
                Size? size,
              }) async {},
          accountHistoryStore: store,
          onSubmit: (_, {required rememberPassword}) async {},
        ),
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(
      tester
          .widget<TextField>(_textFieldWithHint('登录用户名或邮箱地址'))
          .controller!
          .text,
      'morgan',
    );
    expect(
      tester.widget<TextField>(_textFieldWithHint('密码')).controller!.text,
      'moonbase',
    );
    expect(
      tester.widget<ui.UiCheckbox>(find.byType(ui.UiCheckbox)).value,
      isTrue,
    );
  });

  testWidgets('auth keeps password when editing the login field', (
    WidgetTester tester,
  ) async {
    final store = _MemoryLoginAccountHistoryStore([
      LoginAccountRecord(
        login: 'morgan',
        password: 'moonbase',
        useCount: 1,
        updatedAt: DateTime.utc(2026, 1, 2),
      ),
    ]);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: LoginPage(
          sizeForMode: (_, {showingError = false}) => const Size(430, 368),
          consumeInitialWindowLock: () => true,
          lockAuthWindow:
              ({
                bool registering = false,
                bool moveWindow = false,
                bool centerWindow = false,
                Size? size,
              }) async {},
          accountHistoryStore: store,
          onSubmit: (_, {required rememberPassword}) async {},
        ),
      ),
    );
    await tester.pump();
    await tester.pump();

    await tester.enterText(
      _textFieldWithHint('登录用户名或邮箱地址'),
      'morgan@mail.test',
    );
    await tester.pump();

    expect(
      tester.widget<TextField>(_textFieldWithHint('密码')).controller!.text,
      'moonbase',
    );
    expect(
      tester.widget<ui.UiCheckbox>(find.byType(ui.UiCheckbox)).value,
      isFalse,
    );
  });

  testWidgets('remember password stores successful login locally', (
    WidgetTester tester,
  ) async {
    final store = _MemoryLoginAccountHistoryStore();
    bool? submittedRememberPassword;

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: LoginPage(
          sizeForMode: (_, {showingError = false}) => const Size(430, 368),
          consumeInitialWindowLock: () => true,
          lockAuthWindow:
              ({
                bool registering = false,
                bool moveWindow = false,
                bool centerWindow = false,
                Size? size,
              }) async {},
          accountHistoryStore: store,
          onSubmit: (_, {required rememberPassword}) async {
            submittedRememberPassword = rememberPassword;
          },
        ),
      ),
    );
    await tester.pump();

    await tester.enterText(_textFieldWithHint('登录用户名或邮箱地址'), 'kai');
    await tester.enterText(_textFieldWithHint('密码'), 'secret123');
    await tester.tap(
      find.byKey(const ValueKey('auth-remember-password-hot-zone')),
    );
    await tester.pump();
    await tester.tap(find.widgetWithText(ui.Button, '登录'));
    await tester.pumpAndSettle();

    expect(submittedRememberPassword, isTrue);
    expect(store.records, hasLength(1));
    expect(store.records.single.login, 'kai');
    expect(store.records.single.password, 'secret123');
  });

  testWidgets(
    'unchecked remember password stays visually unchecked while busy',
    (WidgetTester tester) async {
      final submitCompleter = Completer<void>();

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: LoginPage(
            sizeForMode: (_, {showingError = false}) => const Size(430, 368),
            consumeInitialWindowLock: () => true,
            lockAuthWindow:
                ({
                  bool registering = false,
                  bool moveWindow = false,
                  bool centerWindow = false,
                  Size? size,
                }) async {},
            onSubmit: (_, {required rememberPassword}) {
              expect(rememberPassword, isFalse);
              return submitCompleter.future;
            },
          ),
        ),
      );
      await tester.pump();

      await tester.enterText(_textFieldWithHint('登录用户名或邮箱地址'), 'kai');
      await tester.enterText(_textFieldWithHint('密码'), 'secret123');
      await tester.tap(find.widgetWithText(ui.Button, '登录'));
      await tester.pump();

      expect(
        tester.widget<ui.UiCheckbox>(find.byType(ui.UiCheckbox)).value,
        isFalse,
      );
      expect(_rememberPasswordCheckIcon(tester).color, Colors.transparent);

      submitCompleter.complete();
      await tester.pumpAndSettle();

      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('register mode exposes full auth form', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(GangApp(tokenStore: _MemoryTokenStore()));
    await tester.pump();

    final loginBottomGap = _submitBottomGap(tester, submitLabel: '登录');
    expect(loginBottomGap, greaterThanOrEqualTo(34));

    await tester.tap(find.text('注册'));
    await tester.pump();

    expect(find.text('登录用户名'), findsOneWidget);
    expect(find.text('邮箱地址'), findsOneWidget);
    expect(find.text('密码'), findsOneWidget);
    expect(find.text('确认密码'), findsOneWidget);
    expect(find.byTooltip('显示密码'), findsNWidgets(2));
    expect(find.widgetWithText(ui.Button, '创建账号'), findsOneWidget);
    expect(
      _submitBottomGap(tester, submitLabel: '创建账号'),
      greaterThanOrEqualTo(34),
    );
    _expectSubmitButtonFullWidth(tester, submitLabel: '创建账号');
    expect(find.text('请输入账号和密码后继续'), findsNothing);
    final normalSurfaceHeight = tester
        .getSize(find.byKey(const ValueKey('auth-surface')))
        .height;

    await tester.tap(find.byTooltip('显示密码').first);
    await tester.pump();

    expect(find.byTooltip('隐藏密码'), findsOneWidget);
    expect(find.byTooltip('显示密码'), findsOneWidget);

    await tester.tap(find.widgetWithText(ui.Button, '创建账号'));
    await tester.pump();

    expect(find.text('请输入账号和密码后继续'), findsOneWidget);
    expect(
      tester.getSize(find.byKey(const ValueKey('auth-surface'))).height,
      closeTo(normalSurfaceHeight, 0.01),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'auth inputs support selection cursor placement and context menus',
    (WidgetTester tester) async {
      final emailVerification = _FakeEmailVerificationController();
      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: LoginPage(
            sizeForMode: (_, {showingError = false}) => const Size(430, 500),
            consumeInitialWindowLock: () => true,
            lockAuthWindow:
                ({
                  bool registering = false,
                  bool moveWindow = false,
                  bool centerWindow = false,
                  Size? size,
                }) async {},
            checkUsernameAvailability: (_) async => true,
            checkEmailAvailability: (_) async => true,
            emailVerificationController: emailVerification,
            onSubmit: (_, {required rememberPassword}) async {},
          ),
        ),
      );
      await tester.pump();

      expect(
        find.byKey(const ValueKey('auth-selection-disabled')),
        findsNothing,
      );
      expect(
        tester
            .widgetList<TextField>(find.byType(TextField))
            .every((field) => field.enableInteractiveSelection),
        isTrue,
      );
      final loginField = _textFieldWithHint('登录用户名或邮箱地址');
      final loginFieldTop = tester.getTopLeft(loginField).dy;
      const loginText = 'logan@example.test';
      await tester.enterText(loginField, loginText);
      await tester.tap(loginField);
      await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
      await tester.sendKeyEvent(LogicalKeyboardKey.keyA);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
      final selected = tester
          .widget<TextField>(loginField)
          .controller!
          .selection;
      expect(selected.start, 0);
      expect(selected.end, loginText.length);

      final loginRect = tester.getRect(loginField);
      await tester.tapAt(Offset(loginRect.left + 80, loginRect.center.dy));
      await tester.pump();
      final repositioned = tester
          .widget<TextField>(loginField)
          .controller!
          .selection;
      expect(repositioned.isCollapsed, isTrue);
      expect(repositioned.baseOffset, lessThan(loginText.length));

      final inputSecondaryClick = await tester.startGesture(
        tester.getCenter(loginField),
        kind: PointerDeviceKind.mouse,
        buttons: kSecondaryMouseButton,
      );
      await inputSecondaryClick.up();
      await tester.pumpAndSettle();
      expect(find.text('全选'), findsOneWidget);

      await tester.tap(find.text('记住密码'));
      await tester.pump();
      expect(find.byTooltip('记住密码'), findsNothing);
      expect(
        tester.widget<ui.UiCheckbox>(find.byType(ui.UiCheckbox)).value,
        isTrue,
      );

      final authSecondaryClick = await tester.startGesture(
        tester.getCenter(find.text('Gang Chat').last),
        kind: PointerDeviceKind.mouse,
        buttons: kSecondaryMouseButton,
      );
      await authSecondaryClick.up();
      await tester.pump();
      expect(find.text('Select all'), findsNothing);
      expect(find.text('全选'), findsNothing);

      await tester.tap(find.text('注册'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widgetList<TextField>(find.byType(TextField))
            .every((field) => field.enableInteractiveSelection),
        isTrue,
      );
      var emailVerificationAction = find.byKey(
        const ValueKey('auth-email-verification-button'),
      );
      expect(emailVerificationAction, findsNothing);
      expect(find.text('验证'), findsNothing);
      final emailField = _textFieldWithHint('邮箱地址');
      await tester.enterText(emailField, 'register@example.test');
      await tester.pump();
      emailVerificationAction = find.byKey(
        const ValueKey('auth-email-verification-button'),
      );
      expect(emailVerificationAction, findsOneWidget);
      expect(find.text('验证'), findsOneWidget);
      final emailFieldRect = tester.getRect(emailField);
      final emailActionRect = tester.getRect(emailVerificationAction);
      expect(emailActionRect.center.dx, greaterThan(emailFieldRect.center.dx));
      expect(emailActionRect.right, lessThanOrEqualTo(emailFieldRect.right));
      expect(
        find.ancestor(
          of: emailVerificationAction,
          matching: find.byType(ui.Button),
        ),
        findsNothing,
      );
      expect(
        tester.widget<GestureDetector>(emailVerificationAction).onTap,
        isNotNull,
      );
      final registerUsernameField = _textFieldWithHint('登录用户名');
      expect(
        tester.getTopLeft(registerUsernameField).dy,
        closeTo(loginFieldTop, 0.01),
      );
      expect(
        tester.widget<TextField>(registerUsernameField).controller!.text,
        loginText,
      );

      await tester.enterText(registerUsernameField, 'logan.test');
      await tester.pump();
      expect(
        find.byKey(const ValueKey<String>('auth-username-invalid')),
        findsOneWidget,
      );
      expect(
        find.byTooltip('登录用户名需为 3-32 位，只能包含英文字母、数字、下划线或连字符'),
        findsOneWidget,
      );

      await tester.enterText(emailField, 'invalid-email');
      await tester.pump();
      expect(
        tester.widget<GestureDetector>(emailVerificationAction).onTap,
        isNotNull,
      );
      await tester.tap(emailVerificationAction);
      await tester.pump();
      expect(find.text('请输入有效的邮箱地址'), findsOneWidget);
      expect(find.text('邮箱验证'), findsNothing);

      await tester.enterText(_textFieldWithHint('登录用户名'), 'logan_01');
      await tester.enterText(emailField, 'logan@example.test');
      await tester.pump();
      expect(
        find.byKey(const ValueKey<String>('auth-username-checking')),
        findsOneWidget,
      );
      expect(
        tester.widget<GestureDetector>(emailVerificationAction).onTap,
        isNotNull,
      );

      await tester.tap(emailVerificationAction);
      await tester.pumpAndSettle();
      expect(find.text('邮箱验证'), findsOneWidget);
      expect(find.text('已发送验证码到您的邮箱 logan@example.test'), findsOneWidget);
      final sendCodeAction = find.byKey(
        const ValueKey('auth-email-send-code-button'),
      );
      expect(find.text('重新发送(60)'), findsOneWidget);
      expect(tester.widget<GestureDetector>(sendCodeAction).onTap, isNull);
      expect(emailVerification.calls, [
        'inspect:logan@example.test',
        'start:logan@example.test',
      ]);

      final verificationCodeField = _textFieldWithHint('请输入验证码');
      expect(
        tester
            .widget<TextField>(verificationCodeField)
            .enableInteractiveSelection,
        isTrue,
      );
      await tester.enterText(verificationCodeField, '123456');
      final codeSecondaryClick = await tester.startGesture(
        tester.getCenter(verificationCodeField),
        kind: PointerDeviceKind.mouse,
        buttons: kSecondaryMouseButton,
      );
      await codeSecondaryClick.up();
      await tester.pumpAndSettle();
      expect(find.text('全选'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('email verification reports a duplicate before opening dialog', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: LoginPage(
          sizeForMode: (_, {showingError = false}) => const Size(430, 500),
          consumeInitialWindowLock: () => true,
          lockAuthWindow:
              ({
                bool registering = false,
                bool moveWindow = false,
                bool centerWindow = false,
                Size? size,
              }) async {},
          checkUsernameAvailability: (_) async => true,
          checkEmailAvailability: (_) async => false,
          onSubmit: (_, {required rememberPassword}) async {},
        ),
      ),
    );
    await tester.pump();
    await tester.tap(find.text('注册'));
    await tester.pump();
    await tester.enterText(_textFieldWithHint('邮箱地址'), 'taken@example.test');
    await tester.pump();

    await tester.tap(
      find.byKey(const ValueKey('auth-email-verification-button')),
    );
    await tester.pump();

    expect(find.text('该邮箱已被其他用户使用'), findsOneWidget);
    expect(find.text('邮箱验证'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'email verification shows checking icon while request is pending',
    (WidgetTester tester) async {
      final availability = Completer<bool>();
      final emailVerification = _FakeEmailVerificationController();
      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: LoginPage(
            sizeForMode: (_, {showingError = false}) => const Size(430, 500),
            consumeInitialWindowLock: () => true,
            lockAuthWindow:
                ({
                  bool registering = false,
                  bool moveWindow = false,
                  bool centerWindow = false,
                  Size? size,
                }) async {},
            checkUsernameAvailability: (_) async => true,
            checkEmailAvailability: (_) => availability.future,
            emailVerificationController: emailVerification,
            onSubmit: (_, {required rememberPassword}) async {},
          ),
        ),
      );
      await tester.pump();
      await tester.tap(find.text('注册'));
      await tester.pumpAndSettle();
      await tester.enterText(_textFieldWithHint('邮箱地址'), 'logan@example.test');
      await tester.pump();

      await tester.tap(
        find.byKey(const ValueKey('auth-email-verification-button')),
      );
      await tester.pump();

      expect(find.byKey(const ValueKey('auth-email-checking')), findsOneWidget);
      expect(find.byTooltip('正在检测邮箱是否可用'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('auth-email-verification-button')),
        findsNothing,
      );

      availability.complete(true);
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('auth-email-checking')), findsNothing);
      expect(find.text('邮箱验证'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('email verification inspects and inherits server cooldown', (
    WidgetTester tester,
  ) async {
    final emailVerification = _FakeEmailVerificationController(
      onInspect: (email) async => const EmailVerificationInspection(
        canSend: false,
        challengeId: 'existing-challenge',
        retryAfterSeconds: 42,
      ),
    );
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: LoginPage(
          sizeForMode: (_, {showingError = false}) => const Size(430, 500),
          consumeInitialWindowLock: () => true,
          lockAuthWindow:
              ({
                bool registering = false,
                bool moveWindow = false,
                bool centerWindow = false,
                Size? size,
              }) async {},
          checkUsernameAvailability: (_) async => true,
          checkEmailAvailability: (_) async => true,
          emailVerificationController: emailVerification,
          onSubmit: (_, {required rememberPassword}) async {},
        ),
      ),
    );
    await tester.pump();
    await tester.tap(find.text('注册'));
    await tester.pumpAndSettle();
    await tester.enterText(_textFieldWithHint('邮箱地址'), 'first@example.test');
    await tester.pump();
    final verifyAction = find.byKey(
      const ValueKey('auth-email-verification-button'),
    );
    await tester.tap(verifyAction);
    await tester.pumpAndSettle();
    expect(find.text('重新发送(42)'), findsOneWidget);
    expect(emailVerification.calls, ['inspect:first@example.test']);
    expect(find.text('已发送验证码到您的邮箱 first@example.test'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'email verification opens when cooldown inspection omits challenge',
    (WidgetTester tester) async {
      final emailVerification = _FakeEmailVerificationController(
        onInspect: (email) async => const EmailVerificationInspection(
          canSend: false,
          retryAfterSeconds: 42,
        ),
        onStart: (email) async => const EmailVerificationChallenge(
          id: 'existing-challenge',
          retryAfterSeconds: 42,
        ),
      );
      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: LoginPage(
            sizeForMode: (_, {showingError = false}) => const Size(430, 500),
            consumeInitialWindowLock: () => true,
            lockAuthWindow:
                ({
                  bool registering = false,
                  bool moveWindow = false,
                  bool centerWindow = false,
                  Size? size,
                }) async {},
            checkUsernameAvailability: (_) async => true,
            checkEmailAvailability: (_) async => true,
            emailVerificationController: emailVerification,
            onSubmit: (_, {required rememberPassword}) async {},
          ),
        ),
      );
      await tester.pump();
      await tester.tap(find.text('注册'));
      await tester.pumpAndSettle();
      await tester.enterText(_textFieldWithHint('邮箱地址'), 'first@example.test');
      await tester.pump();
      await tester.tap(
        find.byKey(const ValueKey('auth-email-verification-button')),
      );
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('auth-email-verification-code')),
        findsOneWidget,
      );
      expect(find.text('重新发送(42)'), findsOneWidget);
      expect(emailVerification.calls, [
        'inspect:first@example.test',
        'start:first@example.test',
      ]);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('register checks email verification before username submit', (
    WidgetTester tester,
  ) async {
    final checkedUsernames = <String>[];
    var submissions = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: LoginPage(
          sizeForMode: (_, {showingError = false}) => const Size(430, 500),
          consumeInitialWindowLock: () => true,
          lockAuthWindow:
              ({
                bool registering = false,
                bool moveWindow = false,
                bool centerWindow = false,
                Size? size,
              }) async {},
          checkUsernameAvailability: (username) async {
            checkedUsernames.add(username);
            return username != 'taken_name';
          },
          onSubmit: (_, {required rememberPassword}) async {
            submissions += 1;
          },
        ),
      ),
    );
    await tester.pump();
    await tester.tap(find.text('注册'));
    await tester.pump();

    await tester.enterText(_textFieldWithHint('登录用户名'), 'taken_name');
    await tester.pump(const Duration(milliseconds: 350));
    await tester.pump();
    expect(find.byTooltip('该登录用户名已被其他用户使用'), findsOneWidget);

    await tester.enterText(_textFieldWithHint('邮箱地址'), 'taken@example.test');
    await tester.enterText(_textFieldWithHint('密码'), 'secret123');
    await tester.enterText(_textFieldWithHint('确认密码'), 'secret123');
    await tester.tap(find.widgetWithText(ui.Button, '创建账号'));
    await tester.pumpAndSettle();
    expect(submissions, 0);
    expect(find.text('请先验证邮箱'), findsOneWidget);

    await tester.enterText(_textFieldWithHint('登录用户名'), 'available_name');
    await tester.pump(const Duration(milliseconds: 350));
    await tester.pump();
    expect(
      find.byKey(const ValueKey<String>('auth-username-valid')),
      findsOneWidget,
    );
    await tester.tap(find.widgetWithText(ui.Button, '创建账号'));
    await tester.pumpAndSettle();
    expect(submissions, 0);
    expect(find.text('请先验证邮箱'), findsOneWidget);
    expect(checkedUsernames, containsAll(['taken_name', 'available_name']));
    expect(tester.takeException(), isNull);
  });

  testWidgets('register submits the server email verification token', (
    WidgetTester tester,
  ) async {
    final emailVerification = _FakeEmailVerificationController();
    AuthRequest? submitted;
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: LoginPage(
          sizeForMode: (_, {showingError = false}) => const Size(430, 500),
          consumeInitialWindowLock: () => true,
          lockAuthWindow:
              ({
                bool registering = false,
                bool moveWindow = false,
                bool centerWindow = false,
                Size? size,
              }) async {},
          checkUsernameAvailability: (_) async => true,
          checkEmailAvailability: (_) async => true,
          emailVerificationController: emailVerification,
          onSubmit: (request, {required rememberPassword}) async {
            submitted = request;
          },
        ),
      ),
    );
    await tester.pump();
    await tester.tap(find.text('注册'));
    await tester.pumpAndSettle();
    await tester.enterText(_textFieldWithHint('登录用户名'), 'verified_user');
    await tester.enterText(_textFieldWithHint('邮箱地址'), 'verified@example.test');
    await tester.pump();
    await tester.enterText(_textFieldWithHint('密码'), 'secret123');
    await tester.enterText(_textFieldWithHint('确认密码'), 'secret123');

    await tester.tap(
      find.byKey(const ValueKey('auth-email-verification-button')),
    );
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('auth-email-verification-code')),
      '123456',
    );
    await tester.tap(find.widgetWithText(ui.Button, '验证'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('auth-email-verified')), findsOneWidget);
    expect(find.byTooltip('邮箱已验证'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('auth-email-verification-button')),
      findsNothing,
    );
    await tester.tap(find.widgetWithText(ui.Button, '创建账号'));
    await tester.pumpAndSettle();

    expect(submitted?.emailVerificationToken, 'email-verification-token');
    expect(emailVerification.calls, [
      'inspect:verified@example.test',
      'start:verified@example.test',
      'verify:email-challenge:123456',
    ]);
    expect(tester.takeException(), isNull);
  });

  testWidgets('editing a verified registration email restores verify action', (
    WidgetTester tester,
  ) async {
    final emailVerification = _FakeEmailVerificationController();
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: LoginPage(
          sizeForMode: (_, {showingError = false}) => const Size(430, 500),
          consumeInitialWindowLock: () => true,
          lockAuthWindow:
              ({
                bool registering = false,
                bool moveWindow = false,
                bool centerWindow = false,
                Size? size,
              }) async {},
          checkUsernameAvailability: (_) async => true,
          checkEmailAvailability: (_) async => true,
          emailVerificationController: emailVerification,
          onSubmit: (_, {required rememberPassword}) async {},
        ),
      ),
    );
    await tester.pump();
    await tester.tap(find.text('注册'));
    await tester.pumpAndSettle();
    final emailField = _textFieldWithHint('邮箱地址');
    await tester.enterText(emailField, 'verified@example.test');
    await tester.pump();
    await tester.tap(
      find.byKey(const ValueKey('auth-email-verification-button')),
    );
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('auth-email-verification-code')),
      '123456',
    );
    await tester.tap(find.widgetWithText(ui.Button, '验证'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('auth-email-verified')), findsOneWidget);

    await tester.enterText(emailField, 'changed@example.test');
    await tester.pump();

    expect(find.byKey(const ValueKey('auth-email-verified')), findsNothing);
    expect(
      find.byKey(const ValueKey('auth-email-verification-button')),
      findsOneWidget,
    );

    await tester.enterText(emailField, '');
    await tester.pump();
    expect(
      find.byKey(const ValueKey('auth-email-verification-button')),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });
}
