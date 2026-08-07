part of '../gang_app_shell_test.dart';

void registerShellRoomManagementWidgetTests() {
  testWidgets('authenticated home shell opens room management with real APIs', (
    WidgetTester tester,
  ) async {
    final requestedPaths = <String>[];
    final requestedUris = <Uri>[];
    final myRoomSettingsUpdates = <Map<String, Object?>>[];

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
        home: HomePage(
          app: _homeTestAppContext(
            requestedPaths: requestedPaths,
            requestedUris: requestedUris,
            myRoomSettingsUpdates: myRoomSettingsUpdates,
          ),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('room-members-entry-badge')),
      findsOneWidget,
    );

    await tester.tap(find.byIcon(Icons.groups_outlined));
    await tester.pumpAndSettle();

    expect(find.text('成员'), findsAtLeastNWidgets(1));
    expect(find.text('房间成员'), findsOneWidget);
    expect(find.text('新成员'), findsOneWidget);
    expect(find.text('黑名单'), findsOneWidget);
    expect(find.byKey(const ValueKey('new-members-tab-badge')), findsOneWidget);
    expect(find.text('邀请成员'), findsNothing);
    expect(find.text('语音 1'), findsOneWidget);
    expect(find.text('在线 2'), findsOneWidget);
    expect(find.text('管理员 1'), findsOneWidget);
    expect(find.text('创建者 1'), findsOneWidget);
    expect(
      tester
          .getRect(find.byKey(const ValueKey('member-presence-filter')))
          .bottom,
      lessThan(
        tester.getRect(find.byKey(const ValueKey('member-role-filter'))).top,
      ),
    );
    expect(
      tester.getRect(find.byKey(const ValueKey('room-members-list'))).height,
      greaterThan(220),
    );
    expect(find.text('@riley'), findsNothing);
    expect(find.text('10000001'), findsNothing);
    expect(find.text('Kai'), findsWidgets);
    expect(find.text('Morgan'), findsWidgets);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('member-information-user-2')),
        matching: find.byType(ui.AdaptiveHighlightedText),
      ),
      findsOneWidget,
    );
    expect(find.text('uid-1 · @kai'), findsNothing);
    expect(find.text('user-2 · @morgan'), findsNothing);
    expect(find.text('创建者'), findsWidgets);
    expect(requestedPaths, contains('/api/v1/rooms/server-alpha/members'));
    expect(
      requestedPaths,
      contains('/api/v1/rooms/server-alpha/join-requests'),
    );

    await tester.tap(find.text('新成员'));
    await tester.pumpAndSettle();

    expect(find.text('邀请成员'), findsOneWidget);
    expect(find.text('加入申请'), findsOneWidget);
    expect(find.byTooltip('详情'), findsOneWidget);
    await tester.ensureVisible(find.byTooltip('详情'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('详情'));
    await tester.pumpAndSettle();
    expect(
      tester.widget<ui.ButtonIcon>(_buttonIconWithTooltip('详情')).selected,
      isTrue,
    );
    expect(find.text('申请详情'), findsOneWidget);
    expect(find.text('来源'), findsOneWidget);
    expect(find.text('公开房间搜索'), findsOneWidget);
    expect(find.text('申请理由'), findsOneWidget);
    expect(find.text('Please approve my request'), findsOneWidget);
    await tester.tap(find.widgetWithText(ui.Button, '关闭'));
    await tester.pumpAndSettle();
    expect(
      tester.widget<ui.ButtonIcon>(_buttonIconWithTooltip('详情')).selected,
      isFalse,
    );

    await tester.ensureVisible(_textFieldWithHint('按用户名、昵称或 UID 搜索'));
    await tester.pumpAndSettle();
    await tester.enterText(_textFieldWithHint('按用户名、昵称或 UID 搜索'), 'mo');
    await tester.pump(const Duration(milliseconds: 320));
    await tester.pumpAndSettle();

    expect(find.textContaining('Morgan'), findsAtLeastNWidgets(1));
    expect(find.text('@morgan'), findsOneWidget);
    expect(find.widgetWithText(ui.Button, '在房间内'), findsOneWidget);

    await tester.enterText(_textFieldWithHint('按用户名、昵称或 UID 搜索'), '');
    await tester.pump(const Duration(milliseconds: 320));
    await tester.pumpAndSettle();

    await tester.tap(find.text('房间成员'));
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.byTooltip('设为管理员'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('设为管理员'));
    await tester.pumpAndSettle();

    expect(find.text('设为管理员'), findsWidgets);
    expect(find.textContaining('Morgan'), findsAtLeastNWidgets(1));
    await tester.tap(find.widgetWithText(ui.Button, '设为管理员'));
    await tester.pumpAndSettle();

    expect(
      requestedPaths,
      contains('/api/v1/rooms/server-alpha/members/user-2'),
    );

    await tester.ensureVisible(find.byTooltip('踢出此用户'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('踢出此用户'));
    await tester.pumpAndSettle();

    expect(find.text('踢出此用户'), findsOneWidget);
    await tester.tap(find.widgetWithText(ui.Button, '踢出'));
    await tester.pumpAndSettle();

    expect(
      requestedPaths
          .where((path) => path == '/api/v1/rooms/server-alpha/members/user-2')
          .length,
      2,
    );
    expect(find.text('Morgan'), findsNothing);

    await tester.tap(find.text('新成员'));
    await tester.pumpAndSettle();

    await tester.ensureVisible(_textFieldWithHint('按用户名、昵称或 UID 搜索'));
    await tester.pumpAndSettle();
    await tester.enterText(_textFieldWithHint('按用户名、昵称或 UID 搜索'), 'ri');
    await tester.pump(const Duration(milliseconds: 320));
    await tester.pumpAndSettle();

    expect(requestedPaths, contains('/api/v1/users/search'));
    expect(find.textContaining('Riley'), findsAtLeastNWidgets(1));
    expect(find.text('@riley'), findsOneWidget);
    expect(find.text('@river'), findsOneWidget);
    expect(find.text('@rina'), findsOneWidget);
    expect(find.text('@riko'), findsOneWidget);
    expect(find.text('@rita'), findsOneWidget);

    await tester.tap(find.widgetWithText(ui.Button, '邀请').first);
    await tester.pumpAndSettle();

    expect(requestedPaths, contains('/api/v1/rooms/server-alpha/invites'));
    expect(find.widgetWithText(ui.Button, '已邀请'), findsOneWidget);

    await tester.tap(find.byTooltip('返回').last);
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('房间设置'));
    await tester.pumpAndSettle();

    expect(find.text('房间设置'), findsOneWidget);
    expect(find.text('房间信息'), findsAtLeastNWidgets(1));
    expect(find.text('个人偏好'), findsOneWidget);
    expect(find.text('消息记录'), findsOneWidget);
    expect(find.text('表情包'), findsOneWidget);
    expect(find.text('歌单'), findsOneWidget);
    expect(
      tester.getCenter(find.text('表情包')).dx,
      lessThan(tester.getCenter(find.text('歌单')).dx),
    );
    expect(find.text('设置'), findsNothing);
    expect(find.byIcon(Icons.info_outline), findsOneWidget);
    expect(find.byType(ui.UiSwitch), findsNothing);
    expect(find.byType(Switch), findsNothing);
    expect(
      find.byWidgetPredicate(
        (widget) =>
            widget is ListView && widget.physics is ClampingScrollPhysics,
      ),
      findsAtLeastNWidgets(1),
    );
    final descriptionField = _roomSettingsTextField('description');
    expect(tester.widget<TextField>(descriptionField).maxLines, isNull);
    expect(
      tester
          .widget<TextField>(_roomSettingsTextField('name'))
          .decoration
          ?.hintText,
      isEmpty,
    );
    expect(
      tester.widget<TextField>(descriptionField).decoration?.hintText,
      isEmpty,
    );
    expect(find.text('房间 RID'), findsOneWidget);
    final ridText = tester.widget<TextField>(
      find.byKey(const ValueKey('room-settings-rid')),
    );
    expect(ridText.controller?.text, 'server-alpha');
    expect(find.text('创建时间'), findsOneWidget);
    final createdAtText = tester.widget<TextField>(
      find.byKey(const ValueKey('room-settings-created-at')),
    );
    expect(
      createdAtText.controller?.text,
      room_display.roomCreatedAtLabel(DateTime.parse('2026-06-01T00:00:00Z')),
    );
    expect(
      tester.getRect(find.byKey(const ValueKey('room-settings-rid'))).top,
      greaterThan(tester.getRect(descriptionField).bottom),
    );
    expect(
      tester
          .getRect(find.byKey(const ValueKey('room-settings-created-at')))
          .top,
      greaterThan(
        tester.getRect(find.byKey(const ValueKey('room-settings-rid'))).bottom,
      ),
    );
    final roomInfoSectionDecorations = tester
        .widgetList<DecoratedBox>(
          find.ancestor(
            of: find.text('房间信息'),
            matching: find.byType(DecoratedBox),
          ),
        )
        .where((box) => box.decoration is BoxDecoration)
        .map((box) => box.decoration as BoxDecoration);
    expect(
      roomInfoSectionDecorations.any(
        (decoration) => decoration.color == null && decoration.border is Border,
      ),
      isTrue,
    );

    await tester.enterText(_roomSettingsTextField('name'), 'Alpha Renamed');
    final saveButton = find.widgetWithText(ui.Button, '保存房间设置');
    tester.widget<ui.Button>(saveButton).onPressed?.call();
    await tester.pumpAndSettle();

    expect(requestedPaths, contains('/api/v1/rooms/server-alpha'));
    expect(find.text('房间信息已保存'), findsOneWidget);
    expect(find.textContaining('Alpha Renamed'), findsAtLeastNWidgets(1));

    await tester.tap(find.text('个人偏好').first);
    await tester.pumpAndSettle();

    expect(find.text('房间消息'), findsOneWidget);
    expect(find.text('全部'), findsOneWidget);
    expect(find.text('接收但不提醒'), findsOneWidget);
    expect(find.text('屏蔽'), findsOneWidget);
    expect(find.text('AI 语音播报'), findsOneWidget);
    expect(find.byType(ui.UiSwitch), findsNWidgets(2));
    expect(
      tester.getRect(find.text('AI 语音播报')).top,
      greaterThan(tester.getRect(find.text('置顶房间')).bottom),
    );
    await tester.tap(find.byType(ui.UiSwitch).last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('接收但不提醒'));
    await tester.pumpAndSettle();
    final savePreferencesButton = find.widgetWithText(ui.Button, '保存个人偏好');
    await tester.ensureVisible(savePreferencesButton);
    await tester.pumpAndSettle();
    await tester.tap(savePreferencesButton);
    await tester.pumpAndSettle();

    expect(requestedPaths, contains('/api/v1/rooms/server-alpha/me'));
    expect(myRoomSettingsUpdates, hasLength(1));
    expect(myRoomSettingsUpdates.single['notification_policy'], 'silent');
    expect(myRoomSettingsUpdates.single['is_pinned'], isFalse);
    expect(
      myRoomSettingsUpdates.single['ai_voice_announcements_enabled'],
      isTrue,
    );
    await tester.drag(find.byType(ListView).last, const Offset(0, 600));
    await tester.pumpAndSettle();
    expect(find.text('个人偏好已保存'), findsOneWidget);
    expect(
      tester.getRect(find.text('个人偏好已保存')).top,
      lessThan(
        tester
            .getRect(
              find.byKey(const ValueKey('room-settings-remark-name-input')),
            )
            .top,
      ),
    );

    await tester.tap(find.text('消息记录'));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('room-message-history-search')),
      findsOneWidget,
    );
    expect(find.text('Hello from Morgan'), findsOneWidget);
    expect(find.text('Morgan'), findsNWidgets(2));
    expect(
      find.byKey(const ValueKey('room-message-history-date-filter')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('room-message-history-member-filter')),
      findsOneWidget,
    );
    final historyRow = find.byKey(
      const ValueKey('room-message-history-row-msg-1'),
    );
    final historyTime = find.byKey(
      const ValueKey('room-message-history-time-msg-1'),
    );
    final historyAvatar = find.byKey(
      const ValueKey('room-message-history-avatar-msg-1'),
    );
    expect(tester.widget<ui.Avatar>(historyAvatar).label, 'Morgan Account');
    expect(tester.getRect(historyRow).height, greaterThanOrEqualTo(64));
    expect(
      find.ancestor(of: historyTime, matching: historyRow),
      findsOneWidget,
    );
    expect(
      tester.getRect(historyTime).right,
      lessThan(tester.getRect(historyAvatar).left),
    );
    expect(
      find.descendant(
        of: historyRow,
        matching: find.byType(ChatMessageContent),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: historyRow,
        matching: find.byKey(const ValueKey('message-bubble-surface-msg-1')),
      ),
      findsNothing,
    );
    expect(
      find.ancestor(of: historyAvatar, matching: find.byType(UserHoverCard)),
      findsOneWidget,
    );
    final historyContentPointer = tester.widget<IgnorePointer>(
      find.byKey(
        const ValueKey('room-message-history-content-interactions-msg-1'),
      ),
    );
    expect(historyContentPointer.ignoring, isFalse);
    final historySenderName = tester.widget<Text>(
      find.descendant(of: historyRow, matching: find.text('Morgan')),
    );
    expect(
      tester.getRect(historyAvatar).top,
      closeTo(
        tester
            .getRect(
              find.descendant(of: historyRow, matching: find.text('Morgan')),
            )
            .top,
        0.01,
      ),
    );
    expect(
      historySenderName.style?.color,
      ui.roleBadgeForegroundColorForLabel('成员'),
    );
    expect(find.text('语音'), findsOneWidget);
    expect(
      tester.getCenter(find.text('链接')).dx,
      lessThan(tester.getCenter(find.text('语音')).dx),
    );
    expect(
      tester.getCenter(find.text('语音')).dx,
      lessThan(tester.getCenter(find.text('表情')).dx),
    );
    final systemHistoryRow = find.byKey(
      const ValueKey('room-message-history-row-msg-system'),
    );
    expect(systemHistoryRow, findsOneWidget);
    expect(
      find.descendant(
        of: systemHistoryRow,
        matching: find.byType(ChatSystemMessageContent),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: systemHistoryRow,
        matching: find.byKey(
          const ValueKey('room-message-history-avatar-msg-system'),
        ),
      ),
      findsNothing,
    );
    expect(
      find.descendant(of: systemHistoryRow, matching: find.byType(ui.Avatar)),
      findsOneWidget,
    );
    final systemHistoryContent = find.descendant(
      of: systemHistoryRow,
      matching: find.byType(ChatSystemMessageContent),
    );
    expect(
      find.descendant(
        of: systemHistoryContent,
        matching: find.byType(AnimatedContainer),
      ),
      findsNothing,
    );
    final systemHistoryAvatar = find.descendant(
      of: systemHistoryContent,
      matching: find.byType(ui.Avatar),
    );
    expect(tester.getSize(historyAvatar), const Size.square(38));
    expect(tester.getSize(systemHistoryAvatar), const Size.square(18));
    expect(tester.widget<Text>(historyTime).data, isNot(contains('\n')));
    expect(
      tester.getRect(systemHistoryAvatar).left,
      closeTo(tester.getRect(historyAvatar).left, 0.01),
    );
    expect(
      tester.getCenter(systemHistoryAvatar).dy,
      closeTo(tester.getRect(systemHistoryRow).center.dy, 0.01),
    );
    final systemHistoryText = tester.widget<Text>(
      find.descendant(of: systemHistoryContent, matching: find.text('加入了房间')),
    );
    expect(systemHistoryText.style?.fontSize, ui.UiTypography.body.fontSize);
    final systemJumpButton = find.byKey(
      const ValueKey('room-message-history-jump-msg-system'),
    );
    expect(
      tester.getRect(systemJumpButton).top,
      greaterThanOrEqualTo(tester.getRect(systemHistoryRow).top),
    );
    expect(
      tester.getRect(systemJumpButton).bottom,
      lessThanOrEqualTo(tester.getRect(systemHistoryRow).bottom),
    );

    await tester.tap(find.text('图片'));
    await tester.pumpAndSettle();
    expect(
      requestedUris.any(
        (uri) =>
            uri.path == '/api/v1/rooms/server-alpha/message-history' &&
            uri.queryParameters['category'] == 'images',
      ),
      isTrue,
    );
    await tester.tap(find.text('文件'));
    await tester.pumpAndSettle();
    expect(
      requestedUris.any(
        (uri) =>
            uri.path == '/api/v1/rooms/server-alpha/message-history' &&
            uri.queryParameters['category'] == 'files',
      ),
      isTrue,
    );

    await tester.tap(
      find.byKey(const ValueKey('room-message-history-member-filter')),
    );
    await tester.pumpAndSettle();
    final memberOption = find.byKey(
      const ValueKey('message-history-member-user-2'),
    );
    expect(memberOption, findsOneWidget);
    expect(
      find.descendant(of: memberOption, matching: find.text('@morgan')),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: memberOption,
        matching: find.textContaining('10000021'),
      ),
      findsNothing,
    );
    final memberName = tester.widget<Text>(
      find.descendant(of: memberOption, matching: find.text('Morgan')),
    );
    expect(memberName.style?.fontWeight, FontWeight.w700);
    expect(
      find.descendant(
        of: memberOption,
        matching: find.byType(ui.AdaptiveHighlightedText),
      ),
      findsOneWidget,
    );
    final allMemberOption = find.byKey(
      const ValueKey('message-history-member-all'),
    );
    expect(tester.getSize(allMemberOption).height, greaterThanOrEqualTo(64));
    expect(
      find.descendant(of: allMemberOption, matching: find.byIcon(Icons.check)),
      findsNothing,
    );
    expect(
      tester
          .widget<Icon>(
            find.byKey(const ValueKey('message-history-member-radio-all')),
          )
          .icon,
      Icons.radio_button_checked,
    );
    expect(
      find.descendant(
        of: memberOption,
        matching: find.byType(ui.PressableSurface),
      ),
      findsNothing,
    );
    final memberRole = find.byKey(
      const ValueKey('message-history-member-role-user-2'),
    );
    expect(memberRole, findsOneWidget);
    final memberRadio = find.byKey(
      const ValueKey('message-history-member-radio-user-2'),
    );
    expect(memberRadio, findsOneWidget);
    expect(tester.widget<Icon>(memberRadio).icon, Icons.radio_button_off);
    expect(
      tester
          .widget<MouseRegion>(
            find.byKey(const ValueKey('message-history-member-hover-user-2')),
          )
          .cursor,
      MouseCursor.defer,
    );
    expect(
      tester.getRect(memberOption).right - tester.getRect(memberRadio).right,
      closeTo(15, 0.01),
    );
    expect(
      tester.getRect(memberRole).right,
      lessThan(tester.getRect(memberRadio).left),
    );
    expect(
      find.descendant(of: memberRole, matching: find.text('成员')),
      findsOneWidget,
    );
    expect(
      find.ancestor(
        of: find.byKey(const ValueKey('message-history-member-avatar-user-2')),
        matching: find.byType(UserHoverCard),
      ),
      findsOneWidget,
    );
    expect(
      tester
          .widget<ui.Avatar>(
            find.byKey(const ValueKey('message-history-member-avatar-user-2')),
          )
          .label,
      'Morgan Account',
    );
    expect(
      tester.getSize(
        find.byKey(const ValueKey('message-history-member-avatar-user-2')),
      ),
      const Size.square(36),
    );
    final memberAvatar = find.byKey(
      const ValueKey('message-history-member-avatar-user-2'),
    );
    await tester.tap(memberAvatar);
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('user-profile-card-avatar-preview')),
      findsOneWidget,
    );
    expect(tester.widget<Icon>(memberRadio).icon, Icons.radio_button_off);
    await tester.tap(memberAvatar);
    await tester.pumpAndSettle();

    final memberOptionRect = tester.getRect(memberOption);
    await tester.tapAt(
      Offset(memberOptionRect.center.dx, memberOptionRect.top + 2),
    );
    await tester.pumpAndSettle();
    expect(tester.widget<Icon>(memberRadio).icon, Icons.radio_button_checked);
    final memberScrollbar = find.byKey(
      const ValueKey('message-history-member-scrollbar'),
    );
    final memberList = tester.widget<ListView>(
      find.descendant(of: memberScrollbar, matching: find.byType(ListView)),
    );
    expect((memberList.padding! as EdgeInsets).right, greaterThan(0));
    await tester.tap(find.widgetWithText(ui.Button, '取消').last);
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey('room-message-history-batch-manage')),
    );
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<IgnorePointer>(
            find.byKey(
              const ValueKey('room-message-history-content-interactions-msg-1'),
            ),
          )
          .ignoring,
      isTrue,
    );
    final row = tester.getRect(
      find.byKey(const ValueKey('room-message-history-row-msg-1')),
    );
    final selectBox = tester.getRect(
      find.byKey(const ValueKey('room-message-history-select-msg-1')),
    );
    final jumpButton = tester.getRect(
      find.byKey(const ValueKey('room-message-history-jump-msg-1')),
    );
    expect(selectBox.center.dy, closeTo(row.center.dy, 0.01));
    expect(jumpButton.center.dy, closeTo(row.center.dy, 0.01));
    expect(
      tester
          .widget<ui.UiCheckbox>(
            find.byKey(const ValueKey('room-message-history-select-msg-1')),
          )
          .value,
      isFalse,
    );
    await tester.tap(historyAvatar);
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('user-profile-card-avatar-preview')),
      findsOneWidget,
    );
    expect(
      tester
          .widget<ui.UiCheckbox>(
            find.byKey(const ValueKey('room-message-history-select-msg-1')),
          )
          .value,
      isFalse,
    );
    await tester.tap(historyAvatar);
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('room-message-history-row-msg-1')),
    );
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<ui.UiCheckbox>(
            find.byKey(const ValueKey('room-message-history-select-msg-1')),
          )
          .value,
      isTrue,
    );
    final selectedHistoryDecoration =
        tester
                .widget<AnimatedContainer>(
                  find.byKey(const ValueKey('room-message-history-row-msg-1')),
                )
                .decoration
            as BoxDecoration;
    final selectedHistoryBorder = selectedHistoryDecoration.border! as Border;
    expect(selectedHistoryDecoration.color, ui.UiColors.selected);
    expect(selectedHistoryBorder.top.color, ui.UiColors.selectedBorder);
    expect(selectedHistoryBorder.top.width, 1);

    await tester.tap(find.text('歌单'));
    await tester.pumpAndSettle();
    expect(find.text('房间歌单'), findsOneWidget);
    expect(find.text('Alpha 房间歌单'), findsOneWidget);
    expect(
      requestedPaths,
      contains('/api/v1/rooms/server-alpha/music-box/playlists'),
    );
    expect(
      tester
          .widget<ui.Button>(
            find.byKey(const ValueKey('create-personal-music-playlist')),
          )
          .onPressed,
      isNotNull,
    );

    await tester.tap(find.byTooltip('返回').last);
    await tester.pumpAndSettle();

    expect(find.textContaining('Alpha Renamed'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'android room description input centers its first line vertically',
    (WidgetTester tester) async {
      await tester.binding.setSurfaceSize(const Size(360, 740));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
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

      final description = tester.widget<TextField>(
        _roomSettingsTextField('description'),
      );
      final padding = description.decoration!.contentPadding! as EdgeInsets;
      expect(description.maxLines, isNull);
      expect(description.textAlignVertical, TextAlignVertical.center);
      expect(padding.top, closeTo(padding.bottom, 0.01));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'android message history enlarges user avatars without changing system avatars',
    (WidgetTester tester) async {
      await tester.binding.setSurfaceSize(const Size(700, 1000));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
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
      await tester.tap(find.text('消息记录'));
      await tester.pumpAndSettle();

      final userAvatar = find.byKey(
        const ValueKey('room-message-history-avatar-msg-1'),
      );
      final systemContent = find.descendant(
        of: find.byKey(const ValueKey('room-message-history-row-msg-system')),
        matching: find.byType(ChatSystemMessageContent),
      );
      final systemAvatar = find.descendant(
        of: systemContent,
        matching: find.byType(ui.Avatar),
      );

      expect(tester.getSize(userAvatar), const Size.square(38));
      expect(tester.getSize(systemAvatar), const Size.square(18));
      expect(
        tester
            .getSize(
              find.byKey(const ValueKey('room-message-history-time-msg-1')),
            )
            .width,
        lessThan(82),
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'room settings confirms auto-reviewing pending applications before join policy change',
    (WidgetTester tester) async {
      final requestedPaths = <String>[];
      final roomSettingsUpdates = <Map<String, Object?>>[];

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: HomePage(
            app: _homeTestAppContext(
              requestedPaths: requestedPaths,
              roomSettingsUpdates: roomSettingsUpdates,
            ),
            realtime: _NoopRealtimeService(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Alpha Room'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('房间设置'));
      await tester.pumpAndSettle();

      final openJoinPolicy = find.text('开放').last;
      await tester.ensureVisible(openJoinPolicy);
      await tester.pumpAndSettle();
      await tester.tap(openJoinPolicy);
      await tester.pumpAndSettle();

      final saveButton = find.widgetWithText(ui.Button, '保存房间设置');
      tester.widget<ui.Button>(saveButton).onPressed?.call();
      await _pumpUntilFound(tester, find.byType(ui.DialogFrame));

      expect(
        requestedPaths,
        contains('/api/v1/rooms/server-alpha/join-requests'),
      );
      expect(find.text('确认修改加入方式？'), findsOneWidget);
      expect(find.textContaining('自动批准所有未处理申请'), findsOneWidget);
      expect(roomSettingsUpdates, isEmpty);

      await tester.tap(find.widgetWithText(ui.Button, '取消'));
      await tester.pumpAndSettle();
      expect(roomSettingsUpdates, isEmpty);

      tester.widget<ui.Button>(saveButton).onPressed?.call();
      await tester.pumpAndSettle();
      expect(roomSettingsUpdates, hasLength(1));
      expect(roomSettingsUpdates.single['join_policy'], 'approval_required');
      expect(find.text('确认修改加入方式？'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('room settings info fields are read-only for regular members', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(currentRoomRole: 'member'),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('房间设置'));
    await tester.pumpAndSettle();

    final nameField = tester.widget<TextField>(_roomSettingsTextField('name'));
    final descriptionField = tester.widget<TextField>(
      _roomSettingsTextField('description'),
    );

    expect(nameField.readOnly, isTrue);
    expect(nameField.enableInteractiveSelection, isTrue);
    expect(nameField.controller?.text, 'Alpha Room');
    expect(descriptionField.readOnly, isTrue);
    expect(descriptionField.enableInteractiveSelection, isTrue);
    expect(descriptionField.maxLines, isNull);
    expect(descriptionField.controller?.text, isEmpty);
    expect(
      tester
          .widget<ui.Button>(find.widgetWithText(ui.Button, '保存房间设置'))
          .onPressed,
      isNull,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'authenticated home shell hides member removal for regular users',
    (WidgetTester tester) async {
      final requestedPaths = <String>[];

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: HomePage(
            app: _homeTestAppContext(
              requestedPaths: requestedPaths,
              currentRoomRole: 'member',
              currentRoomJoinPolicy: 'closed',
            ),
            realtime: _NoopRealtimeService(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Alpha Room'));
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('room-members-entry-badge')),
        findsNothing,
      );

      await tester.tap(find.byTooltip('房间成员'));
      await tester.pumpAndSettle();

      expect(find.text('成员'), findsAtLeastNWidgets(1));
      expect(find.text('房间成员'), findsNothing);
      expect(find.text('新成员'), findsNothing);
      expect(find.text('黑名单'), findsNothing);
      expect(find.text('Morgan'), findsWidgets);
      expect(find.byTooltip('踢出此用户'), findsNothing);
      expect(find.byTooltip('设为管理员'), findsNothing);
      expect(find.byTooltip('转让创建者'), findsNothing);
      expect(requestedPaths, contains('/api/v1/rooms/server-alpha/members'));
      expect(
        requestedPaths,
        isNot(contains('/api/v1/rooms/server-alpha/join-requests')),
      );
      expect(
        requestedPaths,
        isNot(contains('/api/v1/rooms/server-alpha/blacklist')),
      );
    },
  );

  testWidgets(
    'narrow member management stacks filters and wraps member actions',
    (WidgetTester tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(360, 800);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
          home: HomePage(
            app: _homeTestAppContext(),
            realtime: _NoopRealtimeService(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Alpha Room'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('房间成员'));
      await tester.pumpAndSettle();

      final presenceFilter = find.byKey(
        const ValueKey('member-presence-filter'),
      );
      final roleFilter = find.byKey(const ValueKey('member-role-filter'));
      expect(presenceFilter, findsOneWidget);
      expect(roleFilter, findsOneWidget);
      final presenceRect = tester.getRect(presenceFilter);
      final roleRect = tester.getRect(roleFilter);
      expect(presenceRect.bottom, lessThan(roleRect.top));
      expect(presenceRect.left, closeTo(roleRect.left, 0.01));
      expect(presenceRect.right, closeTo(roleRect.right, 0.01));

      expect(find.byKey(const ValueKey('member-actions-user-1')), findsNothing);
      expect(find.byTooltip('成员设置'), findsNothing);
      final memberActions = find.byKey(
        const ValueKey('member-action-wrap-user-2'),
      );
      expect(memberActions, findsOneWidget);
      expect(find.byTooltip('修改房间内用户名'), findsOneWidget);
      expect(find.byTooltip('设为管理员'), findsOneWidget);
      expect(find.byTooltip('踢出此用户'), findsOneWidget);
      expect(find.byTooltip('转让创建者'), findsOneWidget);

      await tester.ensureVisible(memberActions);
      await tester.pumpAndSettle();
      expect(
        tester.getRect(memberActions).top,
        greaterThan(tester.getRect(find.text('Morgan').first).bottom),
      );
      expect(find.byIcon(Icons.edit_outlined), findsOneWidget);
      expect(find.byIcon(Icons.admin_panel_settings), findsOneWidget);
      expect(find.byIcon(Icons.person_remove_outlined), findsOneWidget);
      expect(find.byIcon(Icons.swap_horiz), findsOneWidget);

      await tester.tap(find.byTooltip('修改房间内用户名'));
      await tester.pumpAndSettle();

      expect(find.text('修改Morgan Account的房间内用户名'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'one long member name stacks every actionable member on wide layouts',
    (WidgetTester tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(900, 800);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      const longRoomDisplayName = 'Morgan 的较长且独占信息行时能完整显示的房间昵称';

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
          home: HomePage(
            app: _homeTestAppContext(
              secondaryMemberRoomDisplayName: longRoomDisplayName,
              includeActionComparisonMember: true,
            ),
            realtime: _NoopRealtimeService(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Alpha Room'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('房间成员'));
      await tester.pumpAndSettle();

      final longName = find.text(longRoomDisplayName);
      final morganActions = find.byKey(
        const ValueKey('member-action-wrap-user-2'),
      );
      final taylorActions = find.byKey(
        const ValueKey('member-action-wrap-user-5'),
      );
      final morganInformation = find.byKey(
        const ValueKey('member-information-user-2'),
      );
      final morganPresence = find.byKey(
        const ValueKey('member-presence-user-2'),
      );
      final morganRole = find.byKey(const ValueKey('member-role-user-2'));
      expect(longName, findsOneWidget);
      expect(morganActions, findsOneWidget);
      expect(taylorActions, findsOneWidget);
      expect(morganInformation, findsOneWidget);
      expect(morganPresence, findsOneWidget);
      expect(morganRole, findsOneWidget);
      expect(
        tester.renderObject<RenderParagraph>(longName).didExceedMaxLines,
        isFalse,
      );
      expect(
        tester.getRect(morganActions).top,
        greaterThan(tester.getRect(morganInformation).bottom),
      );
      expect(
        tester.getRect(taylorActions).top,
        greaterThan(tester.getRect(find.text('Taylor')).bottom),
      );
      final informationRect = tester.getRect(morganInformation);
      expect(
        tester.getCenter(morganPresence).dy,
        closeTo(informationRect.center.dy, 0.01),
      );
      expect(
        tester.getCenter(morganRole).dy,
        closeTo(informationRect.center.dy, 0.01),
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'member filters stack before either segmented control starts scrolling',
    (WidgetTester tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(720, 800);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
          builder: (context, child) {
            return MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: const TextScaler.linear(1.2)),
              child: child!,
            );
          },
          home: HomePage(
            app: _homeTestAppContext(),
            realtime: _NoopRealtimeService(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Alpha Room'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('房间成员'));
      await tester.pumpAndSettle();

      final presenceFilter = find.byKey(
        const ValueKey('member-presence-filter'),
      );
      final roleFilter = find.byKey(const ValueKey('member-role-filter'));
      expect(
        tester.getRect(presenceFilter).bottom,
        lessThan(tester.getRect(roleFilter).top),
      );
      for (final filter in [presenceFilter, roleFilter]) {
        expect(
          find.descendant(
            of: filter,
            matching: find.byKey(
              const ValueKey('segmented-control-scroll-view'),
            ),
          ),
          findsNothing,
          reason:
              'each filter should receive a full row before using overflow '
              'controls',
        );
        expect(
          tester
              .renderObjectList<RenderParagraph>(
                find.descendant(of: filter, matching: find.byType(RichText)),
              )
              .every((paragraph) => !paragraph.didExceedMaxLines),
          isTrue,
        );
      }
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('member names wrap in full only after every action row stacks', (
    WidgetTester tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(900, 800);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);
    const longRoomDisplayName = 'Morgan 的一个比较长但信息独占一行时仍然不能完整显示的房间成员昵称';

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
        home: HomePage(
          app: _homeTestAppContext(
            secondaryMemberRoomDisplayName: longRoomDisplayName,
            includeActionComparisonMember: true,
          ),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('房间成员'));
    await tester.pumpAndSettle();

    final longName = find.text(longRoomDisplayName);
    final morganInformation = find.byKey(
      const ValueKey('member-information-user-2'),
    );
    final morganActions = find.byKey(
      const ValueKey('member-action-wrap-user-2'),
    );
    final taylorInformation = find.byKey(
      const ValueKey('member-information-user-5'),
    );
    final taylorActions = find.byKey(
      const ValueKey('member-action-wrap-user-5'),
    );
    expect(tester.widget<Text>(longName).maxLines, isNull);
    expect(tester.widget<Text>(longName).overflow, isNull);
    expect(
      find.ancestor(
        of: longName,
        matching: find.byType(ui.AdaptiveHighlightedText),
      ),
      findsOneWidget,
    );
    expect(
      tester.renderObject<RenderParagraph>(longName).didExceedMaxLines,
      isFalse,
    );
    expect(tester.getRect(morganInformation).height, greaterThan(48));
    expect(
      tester.getRect(morganActions).top,
      greaterThan(tester.getRect(morganInformation).bottom),
    );
    expect(
      tester.getRect(taylorActions).top,
      greaterThan(tester.getRect(taylorInformation).bottom),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('authenticated home shell hides new members for closed rooms', (
    WidgetTester tester,
  ) async {
    final requestedPaths = <String>[];

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(
            requestedPaths: requestedPaths,
            currentRoomJoinPolicy: 'closed',
          ),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('room-members-entry-badge')),
      findsNothing,
    );

    await tester.tap(find.byTooltip('房间成员'));
    await tester.pumpAndSettle();

    expect(find.text('房间成员'), findsOneWidget);
    expect(find.text('新成员'), findsNothing);
    expect(find.text('黑名单'), findsOneWidget);
    expect(requestedPaths, contains('/api/v1/rooms/server-alpha/members'));
    expect(
      requestedPaths,
      isNot(contains('/api/v1/rooms/server-alpha/join-requests')),
    );
  });

  testWidgets('authenticated home shell lets superusers remove creators', (
    WidgetTester tester,
  ) async {
    final requestedPaths = <String>[];

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(
            requestedPaths: requestedPaths,
            currentRoomRole: 'member',
            currentUserIsSuperuser: true,
            secondaryMemberRole: 'owner',
          ),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('房间成员'));
    await tester.pumpAndSettle();

    expect(find.text('Morgan'), findsWidgets);
    expect(find.text('创建者'), findsWidgets);
    expect(find.byTooltip('踢出此用户'), findsOneWidget);
    expect(find.byTooltip('设为管理员'), findsNothing);
    expect(find.byTooltip('转让创建者'), findsNothing);
    expect(requestedPaths, contains('/api/v1/rooms/server-alpha/members'));
  });

  testWidgets('creator removal action aligns with member action group', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(
            currentRoomRole: 'member',
            currentUserIsSuperuser: true,
            secondaryMemberRole: 'owner',
            includeActionComparisonMember: true,
          ),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('房间成员'));
    await tester.pumpAndSettle();

    expect(find.text('Morgan'), findsWidgets);
    expect(find.text('Taylor'), findsWidgets);
    expect(_buttonIconWithTooltip('踢出此用户'), findsNWidgets(2));
    expect(_buttonIconWithTooltip('转让创建者'), findsOneWidget);

    final creatorRemoveRect = tester.getRect(
      find.descendant(
        of: find.byKey(const ValueKey('member-action-wrap-user-2')),
        matching: _buttonIconWithTooltip('踢出此用户'),
      ),
    );
    final memberTransferRect = tester.getRect(
      find.descendant(
        of: find.byKey(const ValueKey('member-action-wrap-user-5')),
        matching: _buttonIconWithTooltip('转让创建者'),
      ),
    );
    expect(creatorRemoveRect.right, closeTo(memberTransferRect.right, 0.01));
  });

  testWidgets('member management keeps row order after role updates', (
    WidgetTester tester,
  ) async {
    final requestedPaths = <String>[];
    final realtime = _FakeRealtimeService();

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(
            requestedPaths: requestedPaths,
            includeActionComparisonMember: true,
          ),
          realtime: realtime,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('房间成员'));
    await tester.pumpAndSettle();

    expect(find.text('Morgan'), findsWidgets);
    expect(find.text('Taylor'), findsOneWidget);
    final morganTopBefore = tester.getTopLeft(find.text('Morgan').first).dy;
    final taylorTopBefore = tester.getTopLeft(find.text('Taylor')).dy;
    expect(morganTopBefore, lessThan(taylorTopBefore));

    expect(_buttonIconWithTooltip('设为管理员'), findsNWidgets(2));
    await tester.tap(_buttonIconWithTooltip('设为管理员').last);
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(ui.Button, '设为管理员'));
    await tester.pumpAndSettle();

    expect(
      requestedPaths,
      contains('/api/v1/rooms/server-alpha/members/user-5'),
    );
    final morganTopAfter = tester.getTopLeft(find.text('Morgan').first).dy;
    final taylorTopAfter = tester.getTopLeft(find.text('Taylor')).dy;
    expect(morganTopAfter, lessThan(taylorTopAfter));

    realtime.add(
      RealtimeEvent(
        type: 'room_updated',
        data: {
          ..._roomCardJson(
            id: 'server-alpha',
            name: 'Alpha Room',
            memberCount: 3,
            liveParticipantCount: 1,
          ),
          'online_member_count': 3,
        },
      ),
    );
    await tester.pumpAndSettle();

    final morganTopAfterReload = tester
        .getTopLeft(find.text('Morgan').first)
        .dy;
    final taylorTopAfterReload = tester.getTopLeft(find.text('Taylor')).dy;
    expect(morganTopAfterReload, lessThan(taylorTopAfterReload));
    expect(tester.takeException(), isNull);
  });

  testWidgets('message profile can jump to member management by UID', (
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

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);

    final messageAvatar = find.descendant(
      of: find.byKey(const ValueKey('message-stage-server-alpha')),
      matching: find.byWidgetPredicate(
        (widget) => widget is ui.Avatar && widget.label == 'Morgan',
      ),
    );
    expect(messageAvatar, findsOneWidget);

    await gesture.moveTo(tester.getCenter(messageAvatar));
    await tester.pumpAndSettle();

    expect(find.widgetWithText(ui.Button, '管理成员'), findsOneWidget);
    await tester.tap(find.widgetWithText(ui.Button, '管理成员'));
    await tester.pumpAndSettle();

    expect(find.text('成员'), findsAtLeastNWidgets(1));
    expect(requestedPaths, contains('/api/v1/rooms/server-alpha/members'));
    final memberSearchField = tester.widget<TextField>(
      find.byWidgetPredicate(
        (widget) =>
            widget is TextField && widget.decoration?.hintText == '搜索成员',
      ),
    );
    expect(memberSearchField.controller?.text, 'uid-2');
    expect(find.textContaining('Morgan'), findsAtLeastNWidgets(1));
  });
}
