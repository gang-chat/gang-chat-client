part of '../gang_app_shell_test.dart';

void registerShellAuthSmokeWidgetTests() {
  testWidgets('auth login layout is stable across rebuilds', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(GangApp(tokenStore: _MemoryTokenStore()));
    await tester.pump();

    final regularSurfaceRect = tester.getRect(
      find.byKey(const ValueKey('auth-surface')),
    );
    final regularModeSwitchRect = tester.getRect(
      find.byWidgetPredicate((widget) => widget is ui.SegmentedControl),
    );
    final regularInputRect = tester.getRect(find.byType(ui.Input).first);
    final regularSubmitRect = tester.getRect(
      find.widgetWithText(ui.Button, '登录'),
    );
    final regularCursorColor = Theme.of(
      tester.element(find.byKey(const ValueKey('auth-surface'))),
    ).textSelectionTheme.cursorColor;

    await tester.pumpWidget(GangApp(tokenStore: _MemoryTokenStore()));
    await tester.pump();

    _expectRectCloseTo(
      tester.getRect(find.byKey(const ValueKey('auth-surface'))),
      regularSurfaceRect,
    );
    _expectRectCloseTo(
      tester.getRect(
        find.byWidgetPredicate((widget) => widget is ui.SegmentedControl),
      ),
      regularModeSwitchRect,
    );
    _expectRectCloseTo(
      tester.getRect(find.byType(ui.Input).first),
      regularInputRect,
    );
    _expectRectCloseTo(
      tester.getRect(find.widgetWithText(ui.Button, '登录')),
      regularSubmitRect,
    );
    expect(
      Theme.of(
        tester.element(find.byKey(const ValueKey('auth-surface'))),
      ).textSelectionTheme.cursorColor,
      regularCursorColor,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('app renders auth entrypoint', (WidgetTester tester) async {
    await tester.pumpWidget(GangApp(tokenStore: _MemoryTokenStore()));
    await tester.pump();

    expect(find.text('Gang Chat'), findsAtLeastNWidgets(1));
    expect(find.text('登录用户名或邮箱地址'), findsOneWidget);
    expect(find.text('密码'), findsOneWidget);
    expect(find.byTooltip('显示密码'), findsOneWidget);
    expect(find.widgetWithText(ui.Button, '登录'), findsOneWidget);
    expect(find.byType(SelectionArea), findsNothing);

    await tester.tap(find.byTooltip('显示密码'));
    await tester.pump();

    expect(find.byTooltip('隐藏密码'), findsOneWidget);
  });

  testWidgets('switching to register reveals additional fields', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(GangApp(tokenStore: _MemoryTokenStore()));
    await tester.pump();

    await tester.tap(find.text('注册'));
    await tester.pump();

    expect(find.text('登录用户名'), findsOneWidget);
    expect(find.text('邮箱地址'), findsOneWidget);
    expect(find.text('确认密码'), findsOneWidget);
    expect(find.byTooltip('显示密码'), findsNWidgets(2));
    expect(find.widgetWithText(ui.Button, '创建账号'), findsOneWidget);

    await tester.tap(find.byTooltip('显示密码').first);
    await tester.pump();

    expect(find.byTooltip('隐藏密码'), findsOneWidget);
    expect(find.byTooltip('显示密码'), findsOneWidget);
  });

  testWidgets('submitting empty form surfaces inline error', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(GangApp(tokenStore: _MemoryTokenStore()));
    await tester.pump();

    await tester.tap(find.widgetWithText(ui.Button, '登录'));
    await tester.pump();

    expect(find.text('请输入账号和密码后继续'), findsOneWidget);
  });
}
