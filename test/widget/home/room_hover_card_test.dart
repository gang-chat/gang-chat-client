import 'package:client/src/app/room_notifications.dart';
import 'package:client/src/home/chat_image_preview.dart';
import 'package:client/src/home/room_profile_card.dart';
import 'package:client/src/home/home_notifications.dart';
import 'package:client/src/protocol/models.dart';
import 'package:client/src/ui/ui.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

const _currentUser = CurrentUser(
  id: 'u_current',
  uid: '1000002',
  username: 'current',
  displayName: 'Current User',
  bio: '',
  gender: 'secret',
  email: null,
  emailPublic: false,
  phoneNumber: null,
  phoneNumberPublic: false,
  avatarUrl: null,
  defaultAvatarKey: 'green-2',
  isSuperuser: false,
  createdAt: null,
);

const _creator = UserSummary(
  id: 'u_creator',
  username: 'creator',
  displayName: 'Room Creator',
  avatarUrl: null,
  defaultAvatarKey: 'blue-3',
);

const _superuser = UserSummary(
  id: 'u_superuser',
  username: 'GANG',
  displayName: 'GANG',
  avatarUrl: null,
  defaultAvatarKey: 'blue-3',
  roomRole: 'superuser',
  isSuperuser: true,
);

final _joinedRoom = PublicRoom(
  id: 'room_1',
  rid: 'R10001',
  name: 'Launch Room',
  description: '用于发布前沟通的语音频道。',
  avatarUrl: null,
  defaultAvatarKey: 'room-2',
  visibility: 'private',
  joinPolicy: 'closed',
  memberCount: 12,
  onlineMemberCount: 3,
  liveParticipantCount: 1,
  joined: true,
  joinState: 'joined',
  createdBy: _creator,
  personalProfile: RoomPersonalProfile(displayName: 'Current In Room'),
  myMembership: RoomMembership(
    joinedAt: DateTime.utc(2026, 6, 1),
    role: 'admin',
  ),
);

Widget _host(
  Widget child, {
  ChatImagePreviewActions? imagePreviewActions,
  TargetPlatform? platform,
  TextScaler? textScaler,
  Size? layoutSize,
}) {
  final scaffold = Scaffold(body: Center(child: child));
  final scopedScaffold = imagePreviewActions == null
      ? scaffold
      : ChatImagePreviewActionsScope(
          actions: imagePreviewActions,
          child: scaffold,
        );
  final effectiveLayoutSize =
      layoutSize ??
      (platform == TargetPlatform.android ? const Size(360, 740) : null);
  return MaterialApp(
    theme: platform == null
        ? uiTheme()
        : uiTheme().copyWith(platform: platform),
    builder: effectiveLayoutSize == null && textScaler == null
        ? null
        : (context, appChild) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(size: effectiveLayoutSize, textScaler: textScaler),
            child: appChild!,
          ),
    home: scopedScaffold,
  );
}

void main() {
  testWidgets('Android room card opens on tap but not on hold', (tester) async {
    await tester.pumpWidget(
      _host(
        RoomHoverCardForTest(room: _joinedRoom, currentUser: _currentUser),
        platform: TargetPlatform.android,
      ),
    );

    final avatar = find.byType(Avatar).first;
    await tester.longPress(avatar);
    await tester.pumpAndSettle();
    expect(find.text('RID: R10001'), findsNothing);

    await tester.tap(avatar);
    await tester.pumpAndSettle();
    expect(find.text('RID: R10001'), findsOneWidget);
  });

  testWidgets('hover over a room avatar reveals the room profile card', (
    tester,
  ) async {
    PublicRoom? openedRoom;
    await tester.pumpWidget(
      _host(
        RoomHoverCardForTest(
          room: _joinedRoom,
          currentUser: _currentUser,
          onEnterRoom: (room) => openedRoom = room,
        ),
      ),
    );

    expect(find.text('RID: R10001'), findsNothing);

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(tester.getCenter(find.byType(Avatar).first));
    await tester.pumpAndSettle();

    expect(find.text('Launch Room'), findsWidgets);
    expect(find.text('12 名成员'), findsOneWidget);
    expect(find.text('用于发布前沟通的语音频道。'), findsOneWidget);
    expect(find.text('创建者'), findsOneWidget);
    expect(find.text('Room Creator'), findsOneWidget);
    expect(find.text('我的房间内信息'), findsOneWidget);
    expect(find.text('Current In Room'), findsOneWidget);
    expect(find.text('管理员'), findsOneWidget);
    expect(find.text('关闭'), findsOneWidget);
    expect(find.text('RID: R10001'), findsOneWidget);
    expect(
      tester.getTopLeft(find.text('进入房间')).dy,
      greaterThan(tester.getTopLeft(find.text('RID: R10001')).dy),
    );

    await tester.tap(find.text('进入房间'));
    await tester.pumpAndSettle();
    expect(openedRoom?.id, 'room_1');
  });

  testWidgets('tap pins a room profile card until an outside tap', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(RoomHoverCardForTest(room: _joinedRoom, currentUser: _currentUser)),
    );

    await tester.tap(find.byType(Avatar).first);
    await tester.pumpAndSettle();
    expect(find.text('RID: R10001'), findsOneWidget);

    await tester.tapAt(const Offset(5, 5));
    await tester.pumpAndSettle();
    expect(find.text('RID: R10001'), findsNothing);
  });

  testWidgets('room profile card identity text can be selected and copied', (
    tester,
  ) async {
    final clipboardWrites = <String>[];
    _mockClipboard(clipboardWrites);

    await tester.pumpWidget(
      _host(RoomHoverCardForTest(room: _joinedRoom, currentUser: _currentUser)),
    );

    await _ensureRoomProfileCardOpen(tester, settle: false);
    await _copyReadOnlyField(
      tester,
      _joinedRoom.name,
      clipboardWrites: clipboardWrites,
    );
    await _ensureRoomProfileCardOpen(tester, settle: false);
    await _copyReadOnlyField(
      tester,
      _joinedRoom.description,
      clipboardWrites: clipboardWrites,
    );
    await _ensureRoomProfileCardOpen(tester, settle: false);
    await _copyReadOnlyField(
      tester,
      'RID: ${_joinedRoom.rid}',
      copyStartOffset: 'RID: '.length,
      expectedCopy: _joinedRoom.rid,
      clipboardWrites: clipboardWrites,
    );

    expect(clipboardWrites, [
      _joinedRoom.name,
      _joinedRoom.description,
      _joinedRoom.rid,
    ]);
  });

  testWidgets(
    'Android room card stays open while dragging a selection handle',
    (tester) async {
      await tester.pumpWidget(
        _host(
          RoomHoverCardForTest(room: _joinedRoom, currentUser: _currentUser),
          platform: TargetPlatform.android,
        ),
      );

      await _ensureRoomProfileCardOpen(tester);
      final editable = _readOnlyFieldWithText(_joinedRoom.name);
      final editableState = tester.state<EditableTextState>(editable);
      editableState.userUpdateTextEditingValue(
        editableState.textEditingValue.copyWith(
          selection: const TextSelection(baseOffset: 1, extentOffset: 5),
        ),
        SelectionChangedCause.doubleTap,
      );
      editableState.showToolbar();
      await tester.pumpAndSettle();

      expect(_selectionHandleFades(), findsNWidgets(2));
      final endpoint = editableState.renderEditable
          .getEndpointsForSelection(editableState.textEditingValue.selection)
          .last;
      final handlePosition =
          editableState.renderEditable.localToGlobal(endpoint.point) +
          const Offset(0, 12);
      final gesture = await tester.startGesture(handlePosition);
      await tester.pump();
      expect(find.text('RID: R10001'), findsOneWidget);
      await gesture.moveBy(const Offset(18, 0));
      await tester.pump();
      expect(find.text('RID: R10001'), findsOneWidget);
      await gesture.up();
      await tester.pumpAndSettle();
      expect(find.text('RID: R10001'), findsOneWidget);
    },
  );

  testWidgets('preset room profile icon does not open image preview', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(RoomHoverCardForTest(room: _joinedRoom, currentUser: _currentUser)),
    );

    await _ensureRoomProfileCardOpen(tester);
    await tester.tap(_roomProfileIcon());
    await tester.pump(const Duration(milliseconds: 200));

    expect(
      find.byKey(const ValueKey('chat-image-preview-url-image')),
      findsNothing,
    );
  });

  testWidgets('uploaded room profile icon opens the shared image preview', (
    tester,
  ) async {
    final roomWithIcon = PublicRoom(
      id: _joinedRoom.id,
      rid: _joinedRoom.rid,
      name: _joinedRoom.name,
      description: _joinedRoom.description,
      avatarUrl: 'https://example.com/room.png',
      defaultAvatarKey: _joinedRoom.defaultAvatarKey,
      visibility: _joinedRoom.visibility,
      joinPolicy: _joinedRoom.joinPolicy,
      memberCount: _joinedRoom.memberCount,
      onlineMemberCount: _joinedRoom.onlineMemberCount,
      liveParticipantCount: _joinedRoom.liveParticipantCount,
      joined: _joinedRoom.joined,
      joinState: _joinedRoom.joinState,
      createdBy: _joinedRoom.createdBy,
      personalProfile: _joinedRoom.personalProfile,
      myMembership: _joinedRoom.myMembership,
    );
    await tester.pumpWidget(
      _host(
        RoomHoverCardForTest(room: roomWithIcon, currentUser: _currentUser),
        imagePreviewActions: _imagePreviewActions(),
      ),
    );

    await _ensureRoomProfileCardOpen(tester, settle: false);
    await tester.tap(_roomProfileIcon());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(
      find.byKey(const ValueKey('chat-image-preview-url-image')),
      findsOneWidget,
    );
    expect(find.byIcon(Icons.download_rounded), findsOneWidget);
    expect(find.byIcon(Icons.save_alt_rounded), findsOneWidget);
    expect(find.byIcon(Icons.copy_rounded), findsOneWidget);
  });

  testWidgets('room profile creator avatar opens a user profile card', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(RoomHoverCardForTest(room: _joinedRoom, currentUser: _currentUser)),
    );

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);

    await gesture.moveTo(tester.getCenter(find.byType(Avatar).first));
    await tester.pumpAndSettle();
    expect(find.text('RID: R10001'), findsOneWidget);

    final creatorAvatar = find.byWidgetPredicate(
      (widget) => widget is Avatar && widget.label == 'Room Creator',
    );
    expect(creatorAvatar, findsOneWidget);

    await gesture.moveTo(tester.getCenter(creatorAvatar));
    await tester.pumpAndSettle();

    expect(find.text('@creator'), findsOneWidget);
    expect(find.text('成员'), findsNothing);
  });

  testWidgets('room profile creator card waits for the latest user profile', (
    tester,
  ) async {
    var resolveCalls = 0;
    await tester.pumpWidget(
      _host(
        RoomHoverCardForTest(
          room: _joinedRoom,
          currentUser: _currentUser,
          onResolveUserProfile: (user) async {
            resolveCalls += 1;
            return user.copyWith(
              displayName: 'Fresh Creator',
              bio: 'Latest status',
              isOnline: true,
            );
          },
        ),
      ),
    );

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);

    await gesture.moveTo(tester.getCenter(find.byType(Avatar).first));
    await tester.pumpAndSettle();

    final creatorAvatar = find.byWidgetPredicate(
      (widget) => widget is Avatar && widget.label == 'Room Creator',
    );
    await gesture.moveTo(tester.getCenter(creatorAvatar));
    await tester.pumpAndSettle();

    expect(resolveCalls, 1);
    expect(find.text('Fresh Creator'), findsOneWidget);
    expect(find.text('Latest status'), findsOneWidget);
    expect(find.text('在线'), findsOneWidget);
    expect(find.text('成员'), findsNothing);
  });

  testWidgets('refreshes the resolved room profile each time the card opens', (
    tester,
  ) async {
    var resolveCalls = 0;
    await tester.pumpWidget(
      _host(
        RoomHoverCardForTest(
          room: _joinedRoom,
          currentUser: _currentUser,
          onResolveRoom: (room) async {
            resolveCalls += 1;
            return PublicRoom(
              id: room.id,
              rid: room.rid,
              name: 'Fresh Room $resolveCalls',
              description: 'Fresh description $resolveCalls',
              avatarUrl: null,
              defaultAvatarKey: room.defaultAvatarKey,
              visibility: room.visibility,
              joinPolicy: room.joinPolicy,
              memberCount: 12 + resolveCalls,
              onlineMemberCount: 3,
              liveParticipantCount: 1,
              joined: true,
              joinState: 'joined',
              createdBy: room.createdBy,
              personalProfile: room.personalProfile,
              myMembership: room.myMembership,
            );
          },
        ),
      ),
    );

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);

    final avatarCenter = tester.getCenter(find.byType(Avatar).first);
    await gesture.moveTo(avatarCenter);
    await tester.pumpAndSettle();

    expect(resolveCalls, 1);
    expect(find.text('Fresh Room 1'), findsOneWidget);
    expect(find.text('Fresh description 1'), findsOneWidget);
    expect(find.text('13 名成员'), findsOneWidget);

    await gesture.moveTo(const Offset(5, 5));
    await tester.pump(const Duration(milliseconds: 200));
    await tester.pumpAndSettle();
    expect(find.text('Fresh Room 1'), findsNothing);

    await gesture.moveTo(avatarCenter);
    await tester.pumpAndSettle();

    expect(resolveCalls, 2);
    expect(find.text('Fresh Room 1'), findsNothing);
    expect(find.text('Fresh Room 2'), findsOneWidget);
    expect(find.text('Fresh description 2'), findsOneWidget);
    expect(find.text('14 名成员'), findsOneWidget);
  });

  testWidgets('notification user avatar opens a user profile card', (
    tester,
  ) async {
    final invite = RoomInvite(
      id: 'invite_user_card',
      status: 'accepted',
      room: PublicRoom(
        id: 'room_1',
        rid: 'R100',
        name: 'Invite Room',
        avatarUrl: null,
        defaultAvatarKey: 'room-2',
        visibility: 'private',
        joinPolicy: 'closed',
        memberCount: 2,
        onlineMemberCount: 0,
        liveParticipantCount: 0,
        joined: true,
        joinState: 'joined',
      ),
      inviter: _creator,
      createdAt: DateTime.utc(2026, 6, 1),
    );

    await tester.pumpWidget(
      _host(
        HomeNotificationsPane(
          invites: [invite],
          applications: const [],
          roomNotifications: const [],
          loading: false,
          error: null,
          busyInviteId: null,
          busyApplicationId: null,
          currentUser: _currentUser,
          onClose: () {},
          onRefresh: () {},
          onReviewInvite: (_, _) async {},
          onWithdrawApplication: (_) async {},
          onOpenRoom: (_) {},
          onOpenRoomEvent: (_) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey('home-notifications-select-button')),
    );
    await tester.pumpAndSettle();
    final selectionFinder = find.byKey(
      const ValueKey('notification-selectbox-invite:invite_user_card'),
    );
    expect(selectionFinder, findsOneWidget);

    final avatarFinder = find.byKey(
      const ValueKey('notification-inviter-avatar-invite_user_card'),
    );
    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(tester.getCenter(avatarFinder));
    await tester.pumpAndSettle();

    expect(find.text('@creator'), findsOneWidget);
    await tester.tap(avatarFinder);
    await tester.pumpAndSettle();
    expect(tester.widget<UiCheckbox>(selectionFinder).value, isFalse);
  });

  testWidgets('notification date filter sits to the right of search', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(2000, 1000));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    final invite = RoomInvite(
      id: 'invite_date_filter',
      status: 'accepted',
      room: _joinedRoom,
      inviter: _creator,
      createdAt: DateTime(2026, 6, 1),
    );

    await tester.pumpWidget(
      _host(
        HomeNotificationsPane(
          invites: [invite],
          applications: const [],
          roomNotifications: const [],
          loading: false,
          error: null,
          busyInviteId: null,
          busyApplicationId: null,
          currentUser: _currentUser,
          onClose: () {},
          onRefresh: () {},
          onReviewInvite: (_, _) async {},
          onWithdrawApplication: (_) async {},
          onOpenRoom: (_) {},
          onOpenRoomEvent: (_) {},
        ),
        platform: TargetPlatform.windows,
        layoutSize: const Size(2000, 1000),
      ),
    );
    await tester.enterText(find.byType(TextField).first, 'Launch');
    await tester.pump();

    final searchRect = tester.getRect(find.byType(Input).first);
    final dateRect = tester.getRect(find.byTooltip('筛选通知日期'));
    expect(searchRect.right, lessThan(dateRect.left));
    expect(
      tester
          .widget<HighlightedText>(
            find.byKey(const ValueKey('notification-time-invite_date_filter')),
          )
          .query,
      isEmpty,
    );

    await tester.tap(find.byTooltip('筛选通知日期'));
    await tester.pumpAndSettle();

    expect(find.text('筛选通知日期'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('notification-start-date-field')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('notification-end-date-field')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('notification-date-reset-button')),
      findsOneWidget,
    );
    expect(find.textContaining('当前区间：'), findsNothing);

    final rangeDialogRect = tester.getRect(find.byType(DialogFrame));
    final resetRect = tester.getRect(
      find.byKey(const ValueKey('notification-date-reset-button')),
    );
    final applyRect = tester.getRect(
      find.byKey(const ValueKey('notification-date-confirm-button')),
    );
    expect(resetRect.center.dx, lessThan(rangeDialogRect.center.dx));
    expect(applyRect.center.dx, greaterThan(rangeDialogRect.center.dx));

    await tester.tap(
      find.descendant(
        of: find.byKey(const ValueKey('notification-start-date-field')),
        matching: find.byType(PressableSurface),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('选择开始日期'), findsOneWidget);
    expect(find.text('1970 年 1 月'), findsOneWidget);
    expect(find.text('一'), findsOneWidget);
    expect(find.text('日'), findsOneWidget);
    expect(find.text('SELECT DATE'), findsNothing);
    expect(find.text('Cancel'), findsNothing);
    expect(find.text('OK'), findsNothing);
    expect(
      find.byKey(const ValueKey('notification-calendar-inline-date-input')),
      findsNothing,
    );
    expect(find.byTooltip('切换到手动输入'), findsOneWidget);
    expect(find.byTooltip('展开年份选择'), findsOneWidget);
    for (var week = 0; week < 5; week++) {
      expect(
        find.byKey(ValueKey('notification-calendar-week-$week')),
        findsOneWidget,
      );
    }
    expect(
      find.byKey(const ValueKey('notification-calendar-week-5')),
      findsNothing,
    );

    final daySize = tester.getSize(
      find.byKey(const ValueKey('notification-date-day-1970-1-2')),
    );
    expect(daySize.width, daySize.height);

    await tester.tap(
      find.byKey(const ValueKey('notification-calendar-year-picker-toggle')),
    );
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('notification-calendar-year-picker')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('notification-calendar-year-1970')),
      findsOneWidget,
    );

    await tester.tap(
      find.byKey(const ValueKey('notification-calendar-input-mode-toggle')),
    );
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('notification-calendar-inline-date-input')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('notification-calendar-year-picker')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('notification-calendar-month-grid')),
      findsOneWidget,
    );
    expect(
      tester
          .widget<ButtonIcon>(
            find.byKey(
              const ValueKey('notification-calendar-input-mode-toggle'),
            ),
          )
          .selected,
      isTrue,
    );

    final inlineDateInput = find.descendant(
      of: find.byKey(const ValueKey('notification-calendar-inline-date-input')),
      matching: find.byType(TextField),
    );
    final dateField = tester.widget<TextField>(inlineDateInput);
    expect(dateField.focusNode!.hasFocus, isTrue);
    expect(dateField.keyboardType, TextInputType.datetime);
    expect(dateField.textAlign, TextAlign.center);

    await tester.enterText(inlineDateInput, '1971-01-01');
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('notification-calendar-inline-date-input')),
      findsNothing,
    );
    expect(find.text('1971 年 1 月'), findsOneWidget);

    await tester.tap(
      find.byKey(const ValueKey('notification-calendar-input-mode-toggle')),
    );
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('notification-calendar-inline-date-input')),
      findsOneWidget,
    );

    final invalidInlineDateInput = find.descendant(
      of: find.byKey(const ValueKey('notification-calendar-inline-date-input')),
      matching: find.byType(TextField),
    );

    await tester.enterText(invalidInlineDateInput, '1971-02-30');
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pump();
    expect(find.text('请输入如 2026-07-10 的有效日期'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('notification-calendar-inline-date-input')),
      findsOneWidget,
    );

    await tester.enterText(invalidInlineDateInput, '1971-02-02');
    await tester.tap(
      find.byKey(const ValueKey('notification-calendar-weekday-header')),
    );
    await tester.pump();
    expect(
      find.byKey(const ValueKey('notification-calendar-inline-date-input')),
      findsNothing,
    );
    expect(find.text('1971 年 2 月'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('notification-date-day-1971-2-2')),
      findsOneWidget,
    );

    await tester.tap(
      find.byKey(const ValueKey('notification-calendar-input-mode-toggle')),
    );
    await tester.pumpAndSettle();
    final secondInlineDateInput = find.descendant(
      of: find.byKey(const ValueKey('notification-calendar-inline-date-input')),
      matching: find.byType(TextField),
    );
    await tester.enterText(secondInlineDateInput, '1971-03-03');
    await tester.tap(
      find.byKey(const ValueKey('notification-calendar-input-mode-toggle')),
    );
    await tester.pumpAndSettle();
    expect(find.text('1971 年 3 月'), findsOneWidget);

    await tester.tap(
      find.byKey(const ValueKey('notification-calendar-confirm-button')),
    );
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey('notification-date-confirm-button')),
    );
    await tester.pumpAndSettle();

    expect(
      tester
          .widget<ButtonIcon>(
            find.byKey(const ValueKey('home-notifications-date-filter-button')),
          )
          .selected,
      isTrue,
    );

    await tester.tap(find.byTooltip('筛选通知日期'));
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey('notification-date-reset-button')),
    );
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('notification-start-date-field')),
      findsNothing,
    );
    expect(
      tester
          .widget<ButtonIcon>(
            find.byKey(const ValueKey('home-notifications-date-filter-button')),
          )
          .selected,
      isFalse,
    );
  });

  testWidgets('android notification date filters fit narrow screens', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(360, 740));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    addTearDown(tester.view.resetViewInsets);
    final invite = RoomInvite(
      id: 'invite_android_date_filter',
      status: 'accepted',
      room: _joinedRoom,
      inviter: _creator,
      createdAt: DateTime(2026, 6, 1),
    );

    await tester.pumpWidget(
      _host(
        HomeNotificationsPane(
          invites: [invite],
          applications: const [],
          roomNotifications: const [],
          loading: false,
          error: null,
          busyInviteId: null,
          busyApplicationId: null,
          currentUser: _currentUser,
          onClose: () {},
          onRefresh: () {},
          onReviewInvite: (_, _) async {},
          onWithdrawApplication: (_) async {},
          onOpenRoom: (_) {},
          onOpenRoomEvent: (_) {},
        ),
        platform: TargetPlatform.android,
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('筛选通知日期'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);

    await tester.tap(
      find.descendant(
        of: find.byKey(const ValueKey('notification-start-date-field')),
        matching: find.byType(PressableSurface),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('选择开始日期'), findsOneWidget);
    expect(find.text('1970/01'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('notification-calendar-previous-year')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('notification-calendar-next-year')),
      findsNothing,
    );

    await tester.tap(
      find.byKey(const ValueKey('notification-calendar-input-mode-toggle')),
    );
    await tester.pumpAndSettle();

    final inlineInput = find.descendant(
      of: find.byKey(const ValueKey('notification-calendar-inline-date-input')),
      matching: find.byType(TextField),
    );
    final field = tester.widget<TextField>(inlineInput);
    expect(field.controller!.text, '1970-01-01');
    expect(field.focusNode!.hasFocus, isTrue);
    expect(
      field.controller!.selection,
      const TextSelection(baseOffset: 0, extentOffset: 10),
    );
    expect(tester.testTextInput.hasAnyClients, isTrue);
    expect(tester.testTextInput.isVisible, isTrue);
    tester.view.viewInsets = const FakeViewPadding(bottom: 300);
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('notification-calendar-month-grid')),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
    await tester.tap(
      find.byKey(const ValueKey('notification-calendar-input-mode-toggle')),
    );
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('notification-calendar-inline-date-input')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('notification-calendar-month-grid')),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
    tester.view.resetViewInsets();
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('notification-calendar-month-grid')),
      findsOneWidget,
    );

    await tester.tap(
      find.byKey(const ValueKey('notification-calendar-confirm-button')),
    );
    await tester.pumpAndSettle();
    await tester.tap(
      find.descendant(
        of: find.byKey(const ValueKey('notification-end-date-field')),
        matching: find.byType(PressableSurface),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('notification-calendar-input-mode-toggle')),
    );
    await tester.pumpAndSettle();

    final endInlineInput = find.descendant(
      of: find.byKey(const ValueKey('notification-calendar-inline-date-input')),
      matching: find.byType(TextField),
    );
    final endField = tester.widget<TextField>(endInlineInput);
    expect(endField.focusNode!.hasFocus, isTrue);
    expect(
      endField.controller!.selection,
      TextSelection(
        baseOffset: 0,
        extentOffset: endField.controller!.text.length,
      ),
    );
    expect(tester.testTextInput.hasAnyClients, isTrue);
    expect(tester.testTextInput.isVisible, isTrue);
    tester.view.viewInsets = const FakeViewPadding(bottom: 300);
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('notification-calendar-month-grid')),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'android notification user targets keep full badges and correct avatars',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(360, 740));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final invite = RoomInvite(
        id: 'invite_android_superuser',
        status: 'accepted',
        room: _joinedRoom,
        inviter: _superuser,
        createdAt: DateTime.utc(2026, 7, 20),
      );
      final application = RoomApplication(
        id: 'application_android_superuser',
        status: 'approved',
        room: _joinedRoom,
        createdAt: DateTime.utc(2026, 7, 20),
        updatedAt: DateTime.utc(2026, 7, 21),
        reviewedAt: DateTime.utc(2026, 7, 21),
        reviewer: _superuser,
      );
      final notification = RoomEventNotification(
        id: 'event_android_superuser',
        type: kRoomEventNotificationRolePromoted,
        room: _joinedRoom,
        actor: _superuser,
        createdAt: DateTime.utc(2026, 7, 22),
        fromRole: 'member',
        toRole: 'superuser',
      );

      await tester.pumpWidget(
        _host(
          HomeNotificationsPane(
            invites: [invite],
            applications: [application],
            roomNotifications: [notification],
            loading: false,
            error: null,
            busyInviteId: null,
            busyApplicationId: null,
            currentUser: _currentUser,
            onClose: () {},
            onRefresh: () {},
            onReviewInvite: (_, _) async {},
            onWithdrawApplication: (_) async {},
            onOpenRoom: (_) {},
            onOpenRoomEvent: (_) {},
          ),
          platform: TargetPlatform.android,
          textScaler: const TextScaler.linear(1.25),
        ),
      );
      await tester.pumpAndSettle();

      for (final key in const [
        'notification-inviter-role-invite_android_superuser',
        'notification-application-reviewer-role-application_android_superuser',
        'notification-room-event-actor-role-event_android_superuser',
        'notification-room-event-target-role-event_android_superuser',
      ]) {
        final roleBadge = find.byKey(ValueKey(key));
        final roleText = find.descendant(
          of: roleBadge,
          matching: find.text('超级用户'),
        );
        final text = tester.widget<Text>(roleText);
        final textPainter = TextPainter(
          text: TextSpan(text: text.data, style: text.style),
          textDirection: TextDirection.ltr,
          textScaler: MediaQuery.textScalerOf(tester.element(roleText)),
          maxLines: 1,
        )..layout();
        expect(
          tester.getSize(roleText).width,
          greaterThanOrEqualTo(textPainter.width - 0.01),
          reason: '$key should not ellipsize 超级用户',
        );
      }

      for (final key in const [
        'notification-inviter-avatar-invite_android_superuser',
        'notification-application-reviewer-avatar-application_android_superuser',
        'notification-room-event-actor-avatar-event_android_superuser',
      ]) {
        final avatar = find.byKey(ValueKey(key));
        expect(tester.getSize(avatar), const Size.square(18));
        final brandImage = tester.widget<Image>(
          find.descendant(of: avatar, matching: find.byType(Image)),
        );
        expect(
          (brandImage.image as AssetImage).assetName,
          'assets/branding/auth_brand_icon.png',
        );
      }

      final notificationRows = find.byWidgetPredicate(
        (widget) =>
            widget.key is ValueKey<String> &&
            (widget.key! as ValueKey<String>).value.startsWith(
              'notification-row-surface-',
            ),
      );
      final presetAvatars = find.descendant(
        of: notificationRows,
        matching: find.byType(Avatar),
      );
      expect(presetAvatars, findsWidgets);
      for (final avatar in tester.widgetList<Avatar>(presetAvatars)) {
        expect(avatar.size, 18);
      }
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('windows superuser notifications use desktop brand avatars', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(2000, 1000));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    final invite = RoomInvite(
      id: 'invite_windows_superuser',
      status: 'accepted',
      room: _joinedRoom,
      inviter: _superuser,
      createdAt: DateTime.utc(2026, 7, 20),
    );
    final application = RoomApplication(
      id: 'application_windows_superuser',
      status: 'approved',
      room: _joinedRoom,
      createdAt: DateTime.utc(2026, 7, 20),
      updatedAt: DateTime.utc(2026, 7, 21),
      reviewedAt: DateTime.utc(2026, 7, 21),
      reviewer: _superuser,
    );
    final notification = RoomEventNotification(
      id: 'event_windows_superuser',
      type: kRoomEventNotificationRolePromoted,
      room: _joinedRoom,
      actor: _superuser,
      createdAt: DateTime.utc(2026, 7, 22),
      fromRole: 'member',
      toRole: 'superuser',
    );

    await tester.pumpWidget(
      _host(
        HomeNotificationsPane(
          invites: [invite],
          applications: [application],
          roomNotifications: [notification],
          loading: false,
          error: null,
          busyInviteId: null,
          busyApplicationId: null,
          currentUser: _currentUser,
          onClose: () {},
          onRefresh: () {},
          onReviewInvite: (_, _) async {},
          onWithdrawApplication: (_) async {},
          onOpenRoom: (_) {},
          onOpenRoomEvent: (_) {},
        ),
        platform: TargetPlatform.windows,
        layoutSize: const Size(2000, 1000),
      ),
    );
    await tester.pumpAndSettle();

    for (final key in const [
      'notification-inviter-avatar-invite_windows_superuser',
      'notification-application-reviewer-avatar-application_windows_superuser',
      'notification-room-event-actor-avatar-event_windows_superuser',
    ]) {
      final avatar = find.byKey(ValueKey(key));
      expect(tester.getSize(avatar), const Size.square(34));
      final brandImage = tester.widget<Image>(
        find.descendant(of: avatar, matching: find.byType(Image)),
      );
      expect(
        (brandImage.image as AssetImage).assetName,
        'assets/branding/auth_brand_icon.png',
      );
    }
    expect(
      find.descendant(
        of: find.byKey(
          const ValueKey('notification-inviter-role-invite_windows_superuser'),
        ),
        matching: find.text('超级用户'),
      ),
      findsOneWidget,
    );
    final roomAvatars = find.byWidgetPredicate(
      (widget) =>
          widget is Avatar &&
          widget.key is ValueKey<String> &&
          (widget.key! as ValueKey<String>).value.startsWith(
            'notification-room-avatar-',
          ),
    );
    expect(roomAvatars, findsWidgets);
    for (final avatar in tester.widgetList<Avatar>(roomAvatars)) {
      expect(avatar.size, 34);
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('Android and Windows superusers prefer a custom avatar', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(2000, 1000));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    const customAvatar =
        'data:image/png;base64,'
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=';
    final superuserWithAvatar = UserSummary(
      id: _superuser.id,
      username: _superuser.username,
      displayName: _superuser.displayName,
      avatarUrl: customAvatar,
      defaultAvatarKey: _superuser.defaultAvatarKey,
      roomRole: _superuser.roomRole,
      isSuperuser: true,
    );
    final invite = RoomInvite(
      id: 'invite_superuser_custom_avatar',
      status: 'accepted',
      room: _joinedRoom,
      inviter: superuserWithAvatar,
      createdAt: DateTime.utc(2026, 7, 20),
    );

    for (final (platform, size) in const [
      (TargetPlatform.android, 18.0),
      (TargetPlatform.windows, 34.0),
    ]) {
      await tester.pumpWidget(
        _host(
          HomeNotificationsPane(
            invites: [invite],
            applications: const [],
            roomNotifications: const [],
            loading: false,
            error: null,
            busyInviteId: null,
            busyApplicationId: null,
            currentUser: _currentUser,
            onClose: () {},
            onRefresh: () {},
            onReviewInvite: (_, _) async {},
            onWithdrawApplication: (_) async {},
            onOpenRoom: (_) {},
            onOpenRoomEvent: (_) {},
          ),
          platform: platform,
          layoutSize: platform == TargetPlatform.windows
              ? const Size(2000, 1000)
              : null,
        ),
      );
      await tester.pumpAndSettle();

      final avatar = tester.widget<Avatar>(
        find.byKey(
          const ValueKey(
            'notification-inviter-avatar-invite_superuser_custom_avatar',
          ),
        ),
      );
      expect(avatar.imageUrl, customAvatar);
      expect(avatar.size, size);
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('room event notification avatars open profile cards', (
    tester,
  ) async {
    final openedEvents = <String>[];
    final notification = RoomEventNotification(
      id: 'event_user_card',
      type: kRoomEventNotificationRolePromoted,
      room: PublicRoom(
        id: 'room_event',
        rid: 'R200',
        name: 'Event Room',
        avatarUrl: null,
        defaultAvatarKey: 'room-2',
        visibility: 'private',
        joinPolicy: 'closed',
        memberCount: 2,
        onlineMemberCount: 0,
        liveParticipantCount: 0,
        joined: true,
        joinState: 'joined',
      ),
      actor: _creator,
      createdAt: DateTime.utc(2026, 6, 1),
      fromRole: 'member',
      toRole: 'admin',
    );

    await tester.pumpWidget(
      _host(
        HomeNotificationsPane(
          invites: const [],
          applications: const [],
          roomNotifications: [notification],
          loading: false,
          error: null,
          busyInviteId: null,
          busyApplicationId: null,
          currentUser: _currentUser,
          onClose: () {},
          onRefresh: () {},
          onReviewInvite: (_, _) async {},
          onWithdrawApplication: (_) async {},
          onOpenRoom: (_) {},
          onOpenRoomEvent: (event) => openedEvents.add(event.id),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(BadgeDot), findsOneWidget);
    await tester.tap(
      find.byKey(const ValueKey('home-notifications-select-button')),
    );
    await tester.pumpAndSettle();
    final selectionFinder = find.byKey(
      const ValueKey('notification-selectbox-room-event:event_user_card'),
    );
    expect(selectionFinder, findsOneWidget);

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(
      tester.getCenter(
        find.byKey(
          const ValueKey(
            'notification-room-event-actor-avatar-event_user_card',
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('@creator'), findsOneWidget);

    await gesture.moveTo(
      tester.getCenter(
        find.byKey(
          const ValueKey('notification-room-avatar-room-event-event_user_card'),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('RID: R200'), findsOneWidget);

    await tester.tap(find.byIcon(Icons.location_on_rounded));
    await tester.pumpAndSettle();
    expect(openedEvents, ['event_user_card']);
    expect(tester.widget<UiCheckbox>(selectionFinder).value, isFalse);
  });

  testWidgets(
    'mention room event notification shows content from info button',
    (tester) async {
      const preview = '@Current User 请看一下这里';
      final notification = RoomEventNotification(
        id: 'event_mention_info',
        type: kRoomEventNotificationMentioned,
        room: PublicRoom(
          id: 'room_event_mention',
          rid: 'R201',
          name: 'Mention Room',
          avatarUrl: null,
          defaultAvatarKey: 'room-2',
          visibility: 'private',
          joinPolicy: 'closed',
          memberCount: 2,
          onlineMemberCount: 0,
          liveParticipantCount: 0,
          joined: true,
          joinState: 'joined',
        ),
        actor: _creator,
        createdAt: DateTime.utc(2026, 6, 1),
        messageId: 'msg_mention_info',
        messagePreview: preview,
      );

      await tester.pumpWidget(
        _host(
          HomeNotificationsPane(
            invites: const [],
            applications: const [],
            roomNotifications: [notification],
            loading: false,
            error: null,
            busyInviteId: null,
            busyApplicationId: null,
            currentUser: _currentUser,
            onClose: () {},
            onRefresh: () {},
            onReviewInvite: (_, _) async {},
            onWithdrawApplication: (_) async {},
            onOpenRoom: (_) {},
            onOpenRoomEvent: (_) {},
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('提及'), findsOneWidget);
      expect(find.text(preview), findsNothing);
      await tester.tap(
        find.byKey(const ValueKey('home-notifications-select-button')),
      );
      await tester.pumpAndSettle();
      final selectionFinder = find.byKey(
        const ValueKey('notification-selectbox-room-event:event_mention_info'),
      );
      expect(selectionFinder, findsOneWidget);

      final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
      await gesture.addPointer(location: Offset.zero);
      addTearDown(gesture.removePointer);
      await gesture.moveTo(tester.getCenter(find.byIcon(Icons.info_outline)));
      await tester.pumpAndSettle();

      expect(find.text(preview), findsOneWidget);
      await tester.tap(find.byIcon(Icons.info_outline));
      await tester.pumpAndSettle();
      expect(tester.widget<UiCheckbox>(selectionFinder).value, isFalse);
    },
  );

  testWidgets('deleted room card skips resolving and hides all room actions', (
    tester,
  ) async {
    var resolveCalls = 0;
    var enterCalls = 0;
    final deletedRoom = _joinedRoom.copyWith(isDeleted: true);
    await tester.pumpWidget(
      _host(
        RoomHoverCardForTest(
          room: deletedRoom,
          currentUser: _currentUser,
          onResolveRoom: (room) async {
            resolveCalls += 1;
            return room;
          },
          onEnterRoom: (_) => enterCalls += 1,
        ),
      ),
    );

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(tester.getCenter(find.byType(Avatar).first));
    await tester.pumpAndSettle();

    expect(resolveCalls, 0);
    expect(enterCalls, 0);
    expect(find.text('房间已删除'), findsOneWidget);
    final tombstone = find.byKey(const ValueKey('deleted-room-profile-card'));
    expect(tombstone, findsOneWidget);
    expect(
      find.descendant(of: tombstone, matching: find.byType(StatusBadge)),
      findsNothing,
    );
    expect(
      find.descendant(of: tombstone, matching: find.byType(Icon)),
      findsNothing,
    );
    expect(find.text('Launch Room'), findsNothing);
    expect(find.text('12 名成员'), findsNothing);
    expect(find.text('创建者'), findsNothing);
    expect(find.text('我的房间内信息'), findsNothing);
    expect(find.text('进入房间'), findsNothing);
    expect(find.text('RID: R10001'), findsNothing);
  });

  testWidgets('deleted notification rooms open only a tombstone card', (
    tester,
  ) async {
    final invite = RoomInvite(
      id: 'invite_deleted',
      status: 'pending',
      room: PublicRoom(
        id: 'room_deleted',
        rid: 'R404',
        name: 'Deleted Room',
        description: 'This should not be visible.',
        avatarUrl: '/deleted.png',
        defaultAvatarKey: 'room-2',
        visibility: 'private',
        joinPolicy: 'closed',
        memberCount: 0,
        onlineMemberCount: 0,
        liveParticipantCount: 0,
        joined: false,
        joinState: 'none',
        createdBy: _creator,
      ),
      inviter: _creator,
      createdAt: DateTime.utc(2026, 6, 1),
      roomExists: false,
      invalidReason: 'room_missing',
    );

    await tester.pumpWidget(
      _host(
        HomeNotificationsPane(
          invites: [invite],
          applications: const [],
          roomNotifications: const [],
          loading: false,
          error: null,
          busyInviteId: null,
          busyApplicationId: null,
          currentUser: _currentUser,
          onClose: () {},
          onRefresh: () {},
          onReviewInvite: (_, _) async {},
          onWithdrawApplication: (_) async {},
          onOpenRoom: (_) {},
          onOpenRoomEvent: (_) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('房间已删除'), findsOneWidget);
    expect(find.text('空'), findsNothing);
    expect(find.text('Deleted Room'), findsNothing);

    final avatarFinder = find.byKey(
      const ValueKey('notification-room-avatar-invite_deleted'),
    );
    final avatar = tester.widget<Avatar>(avatarFinder);
    expect(avatar.label, '');
    expect(avatar.showFallbackText, isFalse);
    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(tester.getCenter(avatarFinder));
    await tester.pumpAndSettle();
    expect(find.text('房间已删除'), findsNWidgets(2));
    expect(find.text('Deleted Room'), findsNothing);
    expect(find.text('RID: R404'), findsNothing);

    await tester.tap(avatarFinder);
    await tester.pumpAndSettle();
    expect(find.text('Deleted Room'), findsNothing);
    expect(find.text('RID: R404'), findsNothing);
  });

  testWidgets('deleted notification users display missing user placeholders', (
    tester,
  ) async {
    final invite = RoomInvite(
      id: 'invite_deleted_user',
      status: 'pending',
      room: PublicRoom(
        id: 'room_1',
        rid: 'R100',
        name: 'Invite Room',
        avatarUrl: null,
        defaultAvatarKey: 'room-2',
        visibility: 'private',
        joinPolicy: 'closed',
        memberCount: 2,
        onlineMemberCount: 0,
        liveParticipantCount: 0,
        joined: false,
        joinState: 'none',
      ),
      inviter: const UserSummary(
        id: 'user_deleted',
        username: 'deleted',
        displayName: 'Deleted User',
        avatarUrl: '/deleted-user.png',
        defaultAvatarKey: 'blue-3',
        roomRole: 'left',
      ),
      createdAt: DateTime.utc(2026, 6, 1),
      inviterExists: false,
      invalidReason: 'inviter_deleted',
    );

    await tester.pumpWidget(
      _host(
        HomeNotificationsPane(
          invites: [invite],
          applications: const [],
          roomNotifications: const [],
          loading: false,
          error: null,
          busyInviteId: null,
          busyApplicationId: null,
          currentUser: _currentUser,
          onClose: () {},
          onRefresh: () {},
          onReviewInvite: (_, _) async {},
          onWithdrawApplication: (_) async {},
          onOpenRoom: (_) {},
          onOpenRoomEvent: (_) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('用户已注销'), findsOneWidget);
    expect(find.text('空'), findsNothing);
    expect(find.text('Deleted User'), findsNothing);
    final avatar = tester.widget<Avatar>(
      find.byKey(
        const ValueKey('notification-inviter-avatar-invite_deleted_user'),
      ),
    );
    expect(avatar.label, '');
    expect(avatar.showFallbackText, isFalse);
    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(
      tester.getCenter(
        find.byKey(
          const ValueKey('notification-inviter-avatar-invite_deleted_user'),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('用户已注销'), findsNWidgets(2));
    expect(find.text('@已注销'), findsNothing);
    expect(find.text('Deleted User'), findsNothing);
  });

  testWidgets('notification context menu copies text and confirms deletion', (
    tester,
  ) async {
    final invite = RoomInvite(
      id: 'invite_context_menu',
      status: 'accepted',
      room: _joinedRoom,
      inviter: _creator,
      createdAt: DateTime.utc(2026, 6, 1),
    );
    final copiedItems = <String>[];
    final deletedItems = <String>[];

    await tester.pumpWidget(
      _host(
        HomeNotificationsPane(
          invites: [invite],
          applications: const [],
          roomNotifications: const [],
          loading: false,
          error: null,
          busyInviteId: null,
          busyApplicationId: null,
          currentUser: _currentUser,
          onClose: () {},
          onRefresh: () {},
          onReviewInvite: (_, _) async {},
          onWithdrawApplication: (_) async {},
          onOpenRoom: (_) {},
          onOpenRoomEvent: (_) {},
          onCopyNotification: (item) async => copiedItems.add(item.id),
          onDeleteNotification: (item) async => deletedItems.add(item.id),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final row = find.byKey(
      const ValueKey('notification-context-row-invite:invite_context_menu'),
    );
    await tester.tap(row, buttons: kSecondaryMouseButton);
    await tester.pump();
    final surface = tester.widget<AnimatedContainer>(
      find.byKey(
        const ValueKey('notification-row-surface-invite:invite_context_menu'),
      ),
    );
    final decoration = surface.decoration as BoxDecoration;
    expect(decoration.color, UiColors.selected);
    expect((decoration.border! as Border).top.color, UiColors.selectedBorder);
    expect(find.text('复制'), findsOneWidget);
    expect(find.text('删除'), findsOneWidget);

    await tester.tap(find.text('复制'));
    await tester.pumpAndSettle();
    expect(copiedItems, ['invite:invite_context_menu']);

    await tester.tap(row, buttons: kSecondaryMouseButton);
    await tester.pumpAndSettle();
    await tester.tap(find.text('删除'));
    await tester.pumpAndSettle();
    expect(find.text('删除通知'), findsOneWidget);
    expect(find.text('删除后将无法恢复'), findsOneWidget);

    await tester.tap(find.text('删除'));
    await tester.pumpAndSettle();
    expect(deletedItems, ['invite:invite_context_menu']);
  });

  testWidgets('Android notification hold opens the desktop context menu', (
    tester,
  ) async {
    final invite = RoomInvite(
      id: 'invite_android_context_menu',
      status: 'accepted',
      room: _joinedRoom,
      inviter: _creator,
      createdAt: DateTime.utc(2026, 6, 1),
    );

    await tester.pumpWidget(
      _host(
        HomeNotificationsPane(
          invites: [invite],
          applications: const [],
          roomNotifications: const [],
          loading: false,
          error: null,
          busyInviteId: null,
          busyApplicationId: null,
          currentUser: _currentUser,
          onClose: () {},
          onRefresh: () {},
          onReviewInvite: (_, _) async {},
          onWithdrawApplication: (_) async {},
          onOpenRoom: (_) {},
          onOpenRoomEvent: (_) {},
          onCopyNotification: (_) async {},
          onDeleteNotification: (_) async {},
        ),
        platform: TargetPlatform.android,
      ),
    );
    await tester.pumpAndSettle();

    final row = find.byKey(
      const ValueKey(
        'notification-context-row-invite:invite_android_context_menu',
      ),
    );
    await tester.longPress(row);
    await tester.pumpAndSettle();

    expect(find.text('复制'), findsOneWidget);
    expect(find.text('删除'), findsOneWidget);
  });

  testWidgets(
    'batch management selects notifications and confirms bulk delete',
    (tester) async {
      final first = RoomInvite(
        id: 'invite_batch_first',
        status: 'accepted',
        room: _joinedRoom,
        inviter: _creator,
        createdAt: DateTime.utc(2026, 6, 1),
      );
      final second = RoomInvite(
        id: 'invite_batch_second',
        status: 'accepted',
        room: _joinedRoom,
        inviter: _creator,
        createdAt: DateTime.utc(2026, 6, 2),
      );
      final deletedBatches = <List<String>>[];

      await tester.pumpWidget(
        _host(
          HomeNotificationsPane(
            invites: [first, second],
            applications: const [],
            roomNotifications: const [],
            loading: false,
            error: null,
            busyInviteId: null,
            busyApplicationId: null,
            currentUser: _currentUser,
            onClose: () {},
            onRefresh: () {},
            onReviewInvite: (_, _) async {},
            onWithdrawApplication: (_) async {},
            onOpenRoom: (_) {},
            onOpenRoomEvent: (_) {},
            onCopyNotification: (_) async {},
            onDeleteNotifications: (items) async {
              deletedBatches.add(items.map((item) => item.id).toList());
            },
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(
        find.byKey(const ValueKey('home-notifications-select-button')),
      );
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('home-notifications-select-all')),
        findsOneWidget,
      );
      expect(find.byTooltip('全选当前通知'), findsOneWidget);
      expect(
        find.byKey(
          const ValueKey('notification-selectbox-invite:invite_batch_first'),
        ),
        findsOneWidget,
      );

      await tester.tap(
        find.byKey(
          const ValueKey('notification-selectbox-invite:invite_batch_first'),
        ),
      );
      await tester.pump();
      await tester.tap(
        find.byKey(
          const ValueKey('notification-context-row-invite:invite_batch_second'),
        ),
      );
      await tester.pump();

      await tester.tap(
        find.byKey(
          const ValueKey('notification-context-row-invite:invite_batch_second'),
        ),
        buttons: kSecondaryMouseButton,
      );
      await tester.pumpAndSettle();
      expect(find.text('复制'), findsNothing);
      expect(find.text('删除'), findsOneWidget);

      await tester.tap(find.text('删除'));
      await tester.pumpAndSettle();
      expect(find.text('删除选中的通知'), findsOneWidget);
      expect(find.text('确定删除所有选中的2条通知？'), findsOneWidget);

      await tester.tap(find.text('删除'));
      await tester.pumpAndSettle();
      expect(deletedBatches, [
        ['invite:invite_batch_second', 'invite:invite_batch_first'],
      ]);
      expect(find.text('已删除 2 条通知'), findsOneWidget);
    },
  );
}

Future<void> _ensureRoomProfileCardOpen(
  WidgetTester tester, {
  bool settle = true,
}) async {
  final marker = find.text('RID: ${_joinedRoom.rid}');
  if (marker.evaluate().isEmpty) {
    await tester.tap(find.byType(Avatar).first);
    if (settle) {
      await tester.pumpAndSettle();
    } else {
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));
    }
  }
  expect(marker, findsOneWidget);
  expect(find.byType(ReadOnlySelectableText), findsWidgets);
}

Finder _roomProfileIcon() {
  return find.byWidgetPredicate(
    (widget) => widget is Avatar && widget.size == 48,
  );
}

ChatImagePreviewActions _imagePreviewActions() {
  return ChatImagePreviewActions(
    onDownload: (_, _) async {},
    onSaveAs: (_, _) async {},
    onCopyToClipboard: (_) async {},
  );
}

Finder _readOnlyFieldWithText(String text) {
  return find.byWidgetPredicate(
    (widget) => widget is EditableText && widget.controller.text == text,
  );
}

Finder _selectionHandleFades() {
  return find.descendant(
    of: find.byWidgetPredicate(
      (widget) => '${widget.runtimeType}' == '_SelectionHandleOverlay',
    ),
    matching: find.byType(FadeTransition),
  );
}

Future<void> _copyReadOnlyField(
  WidgetTester tester,
  String text, {
  int copyStartOffset = 0,
  String? expectedCopy,
  required List<String> clipboardWrites,
}) async {
  final editableFinder = _readOnlyFieldWithText(text);
  final editableValues = find
      .byType(EditableText)
      .evaluate()
      .map((element) => (element.widget as EditableText).controller.text)
      .toList();
  final textFieldValues = find
      .byType(TextField)
      .evaluate()
      .map((element) => (element.widget as TextField).controller?.text)
      .toList();
  final readOnlyCount = find.byType(ReadOnlySelectableText).evaluate().length;
  expect(
    editableFinder,
    findsOneWidget,
    reason:
        'ReadOnlySelectableText count: $readOnlyCount, '
        'TextField values: $textFieldValues, '
        'EditableText values: $editableValues',
  );

  await tester.tap(editableFinder, buttons: kSecondaryMouseButton);
  await tester.pumpAndSettle();
  final editableTextState = tester.state<EditableTextState>(editableFinder);
  expect(
    editableTextState.textEditingValue.selection,
    TextSelection(baseOffset: copyStartOffset, extentOffset: text.length),
  );
  expect(find.text('Ctrl+C'), findsOneWidget);
  expect(find.text('Ctrl+A'), findsNothing);

  final menuGesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
  await menuGesture.addPointer(location: tester.getCenter(editableFinder));
  await menuGesture.moveTo(tester.getCenter(find.text('Ctrl+C')));
  await tester.pump(const Duration(milliseconds: 200));
  expect(editableFinder, findsOneWidget);
  await menuGesture.removePointer();

  await tester.tap(find.text('Ctrl+C'));
  await tester.pumpAndSettle();
  expect(clipboardWrites.last, expectedCopy ?? text);
}

void _mockClipboard(List<String> writes) {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'Clipboard.setData') {
          writes.add(
            (call.arguments as Map<Object?, Object?>)['text']! as String,
          );
          return null;
        }
        if (call.method == 'Clipboard.hasStrings') {
          return const <String, dynamic>{'value': false};
        }
        return null;
      });
  addTearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, null);
  });
}
