import 'dart:async';

import 'package:client/src/protocol/models.dart';
import 'package:client/src/ui/ui.dart';
import 'package:client/src/home/chat_pane.dart';
import 'package:client/src/home/room_profile_card.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

const _user = UserSummary(
  id: 'u1',
  username: 'logan',
  displayName: '加一',
  avatarUrl: null,
  defaultAvatarKey: 'blue-3',
  uid: '10001',
  bio: '随便写点什么',
  gender: 'male',
  roomRole: 'admin',
  isOnline: true,
  commonRooms: [
    UserCommonRoom(id: 'r1', rid: 'R1', name: '摸鱼大队'),
    UserCommonRoom(id: 'r2', rid: 'R2', name: '技术交流'),
  ],
);

const _currentUser = CurrentUser(
  id: 'u1',
  uid: '10001',
  username: 'logan',
  displayName: '加一',
  bio: '',
  gender: 'secret',
  email: null,
  emailPublic: false,
  phoneNumber: null,
  phoneNumberPublic: false,
  avatarUrl: null,
  defaultAvatarKey: 'blue-3',
  isSuperuser: false,
  createdAt: null,
);

Widget _host(
  Widget child, {
  ChatImagePreviewActions? imagePreviewActions,
  TargetPlatform? platform,
}) {
  final scaffold = Scaffold(body: Center(child: child));
  return MaterialApp(
    theme: platform == null ? null : ThemeData(platform: platform),
    home: imagePreviewActions == null
        ? scaffold
        : ChatImagePreviewActionsScope(
            actions: imagePreviewActions,
            child: scaffold,
          ),
  );
}

void main() {
  testWidgets('hover over a message avatar reveals the profile card', (
    tester,
  ) async {
    await tester.pumpWidget(_host(const AvatarHoverCardForTest(user: _user)));

    // Card stays hidden until the pointer enters the avatar.
    expect(find.text('@logan'), findsNothing);

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(tester.getCenter(find.byType(Avatar).first));
    await tester.pumpAndSettle();

    expect(find.text('@logan'), findsOneWidget);
    expect(find.text('加一'), findsWidgets);
    expect(find.text('♂'), findsOneWidget);
    expect(find.text('在线'), findsOneWidget);
    expect(find.text('管理员'), findsOneWidget);
    expect(find.text('男'), findsNothing);
    expect(
      tester.widget<Text>(find.text('♂')).style?.color,
      genderMark('male')?.color,
    );
    expect(
      tester.widget<Text>(find.text('♂')).style?.fontWeight,
      FontWeight.w900,
    );
    expect(find.text('随便写点什么'), findsOneWidget);
    expect(find.text('2 个共同房间'), findsOneWidget);
    expect(find.text('摸鱼大队'), findsOneWidget);
    expect(find.text('技术交流'), findsOneWidget);
    expect(find.text('UID: 10001'), findsOneWidget);

    // Card disappears when the pointer leaves the avatar.
    await gesture.moveTo(const Offset(5, 5));
    await tester.pump(const Duration(milliseconds: 200));
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsNothing);
  });

  testWidgets(
    'narrow user cards wrap identity and common-room names without ellipses',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(190, 740);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      const longDisplayName = '这是一个需要完整换行显示的很长用户名';
      const longCommonRoomName = '这是一个同样需要完整换行显示的共同房间名称';
      const user = UserSummary(
        id: 'long-user',
        username: 'long_user_name_that_must_wrap',
        displayName: longDisplayName,
        avatarUrl: null,
        defaultAvatarKey: 'blue-3',
        commonRooms: [
          UserCommonRoom(
            id: 'long-room',
            rid: 'LONG',
            name: longCommonRoomName,
          ),
          UserCommonRoom(id: 'short-room', rid: 'SHORT', name: 'LOL'),
        ],
      );

      await tester.pumpWidget(_host(const AvatarHoverCardForTest(user: user)));
      await tester.tap(find.byType(Avatar).first);
      await tester.pumpAndSettle();

      final name = find.byWidgetPredicate(
        (widget) =>
            widget is ReadOnlySelectableText && widget.value == longDisplayName,
      );
      final avatar = find.byKey(
        const ValueKey('user-profile-card-avatar-preview'),
      );
      final commonRoomName = find.text(longCommonRoomName);
      final longRoomAvatar = find.byWidgetPredicate(
        (widget) => widget is Avatar && widget.size == 16,
      );
      final shortRoomAvatar = find.byWidgetPredicate(
        (widget) => widget is Avatar && widget.size == 20,
      );
      expect(name, findsOneWidget);
      expect(commonRoomName, findsOneWidget);
      expect(tester.getSize(name).height, greaterThan(20));
      expect(tester.getSize(commonRoomName).height, lessThan(20));
      expect(
        tester.getTopLeft(name).dx,
        greaterThan(tester.getTopRight(avatar).dx),
      );
      expect(
        tester.widget<Text>(commonRoomName).overflow,
        isNot(TextOverflow.ellipsis),
      );
      expect(longRoomAvatar, findsOneWidget);
      expect(shortRoomAvatar, findsOneWidget);
      expect(
        tester.getTopLeft(longRoomAvatar).dx,
        tester.getTopLeft(shortRoomAvatar).dx,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('hover card shows voice instead of online presence tag', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(const AvatarHoverCardForTest(user: _user, inLive: true)),
    );

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(tester.getCenter(find.byType(Avatar).first));
    await tester.pumpAndSettle();

    expect(find.text('在线'), findsNothing);
    expect(find.text('离线'), findsNothing);
    expect(find.text('语音'), findsOneWidget);
    final avatar = tester.widget<Avatar>(
      find.byWidgetPredicate((widget) => widget is Avatar && widget.size == 48),
    );
    expect(avatar.active, isTrue);
    expect(avatar.activeBorderColor, UiColors.presenceVoice);
    expect(avatar.paintBorderOnForeground, isTrue);
  });

  testWidgets('hover card treats current user as online without summary flag', (
    tester,
  ) async {
    const lightweightSelf = UserSummary(
      id: 'u1',
      username: 'logan',
      displayName: '加一',
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
    );

    await tester.pumpWidget(
      _host(
        const AvatarHoverCardForTest(
          user: lightweightSelf,
          currentUser: _currentUser,
        ),
      ),
    );

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(tester.getCenter(find.byType(Avatar).first));
    await tester.pumpAndSettle();

    expect(find.text('在线'), findsOneWidget);
    final avatar = tester.widget<Avatar>(
      find.byWidgetPredicate((widget) => widget is Avatar && widget.size == 48),
    );
    expect(avatar.active, isTrue);
    expect(avatar.activeBorderColor, isNull);
    expect(avatar.paintBorderOnForeground, isTrue);
  });

  testWidgets('hover card hides room role outside room context', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(const AvatarHoverCardForTest(user: _user, showRoomRole: false)),
    );

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(tester.getCenter(find.byType(Avatar).first));
    await tester.pumpAndSettle();

    expect(find.text('@logan'), findsOneWidget);
    expect(find.text('管理员'), findsNothing);
  });

  testWidgets('moving the cursor onto the card keeps it open', (tester) async {
    await tester.pumpWidget(_host(const AvatarHoverCardForTest(user: _user)));

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);

    await gesture.moveTo(tester.getCenter(find.byType(Avatar).first));
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsOneWidget);

    // Move onto the card itself — it should stay open past the close delay.
    await gesture.moveTo(tester.getCenter(find.text('@logan')));
    await tester.pump(const Duration(milliseconds: 200));
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsOneWidget);
  });

  testWidgets('profile card identity text can be selected and copied', (
    tester,
  ) async {
    final clipboardWrites = <String>[];
    _mockClipboard(clipboardWrites);

    await tester.pumpWidget(_host(const AvatarHoverCardForTest(user: _user)));

    await _ensureUserProfileCardOpen(tester, settle: false);
    await _copyReadOnlyField(
      tester,
      _user.displayName,
      clipboardWrites: clipboardWrites,
    );
    await _ensureUserProfileCardOpen(tester, settle: false);
    await _copyReadOnlyField(
      tester,
      '@${_user.username}',
      copyStartOffset: 1,
      expectedCopy: _user.username,
      clipboardWrites: clipboardWrites,
    );
    await _ensureUserProfileCardOpen(tester);
    await _copyReadOnlyField(
      tester,
      _user.bio!,
      clipboardWrites: clipboardWrites,
    );
    await _ensureUserProfileCardOpen(tester, settle: false);
    await _copyReadOnlyField(
      tester,
      'UID: ${_user.uid}',
      copyStartOffset: 'UID: '.length,
      expectedCopy: _user.uid!,
      clipboardWrites: clipboardWrites,
    );

    expect(clipboardWrites, [
      _user.displayName,
      _user.username,
      _user.bio,
      _user.uid,
    ]);
  });

  testWidgets('profile card preserves an existing selection on right click', (
    tester,
  ) async {
    final clipboardWrites = <String>[];
    _mockClipboard(clipboardWrites);

    await tester.pumpWidget(_host(const AvatarHoverCardForTest(user: _user)));

    await _ensureUserProfileCardOpen(tester, settle: false);
    await _copyReadOnlyField(
      tester,
      '@${_user.username}',
      initialSelection: const TextSelection(baseOffset: 1, extentOffset: 4),
      expectedSelection: const TextSelection(baseOffset: 1, extentOffset: 4),
      expectedCopy: 'log',
      clipboardWrites: clipboardWrites,
    );

    expect(clipboardWrites, ['log']);
  });

  testWidgets(
    'Android user card stays open while dragging a selection handle',
    (tester) async {
      await tester.pumpWidget(
        _host(
          const AvatarHoverCardForTest(user: _user),
          platform: TargetPlatform.android,
        ),
      );

      await _ensureUserProfileCardOpen(tester);
      final editable = _readOnlyFieldWithText('@${_user.username}');
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
      expect(find.text('@${_user.username}'), findsOneWidget);
      await gesture.moveBy(const Offset(18, 0));
      await tester.pump();
      expect(find.text('@${_user.username}'), findsOneWidget);
      await gesture.up();
      await tester.pumpAndSettle();
      expect(find.text('@${_user.username}'), findsOneWidget);

      await tester.tapAt(const Offset(5, 5));
      await tester.pumpAndSettle();
      expect(find.text('@${_user.username}'), findsNothing);
    },
  );

  testWidgets('preset profile card avatar does not open image preview', (
    tester,
  ) async {
    await tester.pumpWidget(_host(const AvatarHoverCardForTest(user: _user)));

    await _ensureUserProfileCardOpen(tester, settle: false);
    await tester.tap(_profileCardAvatar());
    await tester.pump(const Duration(milliseconds: 200));

    expect(
      find.byKey(const ValueKey('chat-image-preview-url-image')),
      findsNothing,
    );
  });

  testWidgets('uploaded profile card avatar opens the shared image preview', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        AvatarHoverCardForTest(
          user: _user.copyWith(avatarUrl: 'https://example.com/avatar.png'),
        ),
        imagePreviewActions: _imagePreviewActions(),
      ),
    );

    await _ensureUserProfileCardOpen(tester, settle: false);
    await tester.tap(_profileCardAvatar());
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

  testWidgets('common room avatar opens a room profile card', (tester) async {
    await tester.pumpWidget(_host(const AvatarHoverCardForTest(user: _user)));

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);

    await gesture.moveTo(tester.getCenter(find.byType(Avatar).first));
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsOneWidget);

    final commonRoomAvatar = find.byWidgetPredicate(
      (widget) => widget is Avatar && widget.label == '摸鱼大队',
    );
    expect(commonRoomAvatar, findsOneWidget);

    await gesture.moveTo(tester.getCenter(commonRoomAvatar));
    await tester.pumpAndSettle();

    expect(find.text('RID: R1'), findsOneWidget);
  });

  testWidgets('nested room card keeps the parent user card open', (
    tester,
  ) async {
    await tester.pumpWidget(_host(const AvatarHoverCardForTest(user: _user)));

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);

    await gesture.moveTo(tester.getCenter(find.byType(Avatar).first));
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsOneWidget);

    final commonRoomAvatar = find.byWidgetPredicate(
      (widget) => widget is Avatar && widget.label == '摸鱼大队',
    );
    await gesture.moveTo(tester.getCenter(commonRoomAvatar));
    await tester.pumpAndSettle();
    expect(find.text('RID: R1'), findsOneWidget);

    await gesture.moveTo(tester.getCenter(find.text('RID: R1')));
    await tester.pump(const Duration(milliseconds: 200));
    await tester.pumpAndSettle();

    expect(find.text('@logan'), findsOneWidget);
    expect(find.text('RID: R1'), findsOneWidget);
  });

  testWidgets('tapping the avatar again closes the profile card', (
    tester,
  ) async {
    await tester.pumpWidget(_host(const AvatarHoverCardForTest(user: _user)));

    await tester.tap(find.byType(Avatar).first);
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsOneWidget);

    await tester.tap(find.byType(Avatar).first);
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsNothing);
  });

  testWidgets('click pinning is independent from hover-opened cards', (
    tester,
  ) async {
    await tester.pumpWidget(_host(const AvatarHoverCardForTest(user: _user)));

    final avatar = find.byType(Avatar).first;
    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);

    await gesture.moveTo(tester.getCenter(avatar));
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsOneWidget);

    await tester.tap(avatar);
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsOneWidget);

    await gesture.moveTo(const Offset(5, 5));
    await tester.pump(const Duration(milliseconds: 200));
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsOneWidget);

    await tester.tap(avatar);
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsNothing);
  });

  testWidgets('nested room enter button invokes the common room callback', (
    tester,
  ) async {
    PublicRoom? openedRoom;
    await tester.pumpWidget(
      _host(
        AvatarHoverCardForTest(
          user: _user,
          onEnterCommonRoom: (room) => openedRoom = room,
        ),
      ),
    );

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);

    await gesture.moveTo(tester.getCenter(find.byType(Avatar).first));
    await tester.pumpAndSettle();

    final commonRoomAvatar = find.byWidgetPredicate(
      (widget) => widget is Avatar && widget.label == '摸鱼大队',
    );
    await gesture.moveTo(tester.getCenter(commonRoomAvatar));
    await tester.pumpAndSettle();

    await tester.tap(find.text('进入房间'));
    await tester.pumpAndSettle();

    expect(openedRoom?.id, 'r1');
  });

  testWidgets('opening another common room closes the previous room card', (
    tester,
  ) async {
    const siblingUser = UserSummary(
      id: 'u1',
      username: 'logan',
      displayName: 'Logan',
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
      commonRooms: [
        UserCommonRoom(id: 'r1', rid: 'R1', name: 'Room One'),
        UserCommonRoom(id: 'r2', rid: 'R2', name: 'Room Two'),
      ],
    );
    await tester.pumpWidget(
      _host(const AvatarHoverCardForTest(user: siblingUser)),
    );

    await tester.tap(find.byType(Avatar).first);
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsOneWidget);

    final firstCommonRoomAvatar = find.byWidgetPredicate(
      (widget) => widget is Avatar && widget.label == 'Room One',
    );
    final secondCommonRoomAvatar = find.byWidgetPredicate(
      (widget) => widget is Avatar && widget.label == 'Room Two',
    );
    expect(firstCommonRoomAvatar, findsOneWidget);
    expect(secondCommonRoomAvatar, findsOneWidget);

    await tester.tap(firstCommonRoomAvatar);
    await tester.pumpAndSettle();
    expect(find.text('RID: R1'), findsOneWidget);

    await tester.tap(secondCommonRoomAvatar);
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsOneWidget);
    expect(find.text('RID: R1'), findsNothing);
    expect(find.text('RID: R2'), findsOneWidget);
  });

  testWidgets('common rooms overflow scrolls instead of showing summary row', (
    tester,
  ) async {
    const manyRoomsUser = UserSummary(
      id: 'u1',
      username: 'logan',
      displayName: 'Logan',
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
      commonRooms: [
        UserCommonRoom(id: 'r1', rid: 'R1', name: 'Room One'),
        UserCommonRoom(id: 'r2', rid: 'R2', name: 'Room Two'),
        UserCommonRoom(id: 'r3', rid: 'R3', name: 'Room Three'),
        UserCommonRoom(id: 'r4', rid: 'R4', name: 'Room Four'),
        UserCommonRoom(id: 'r5', rid: 'R5', name: 'Room Five'),
        UserCommonRoom(id: 'r6', rid: 'R6', name: 'Room Six'),
      ],
    );

    await tester.pumpWidget(
      _host(const AvatarHoverCardForTest(user: manyRoomsUser)),
    );

    await tester.tap(find.byType(Avatar).first);
    await tester.pumpAndSettle();

    expect(find.text('6 个共同房间'), findsOneWidget);
    expect(find.text('等 6 个房间'), findsNothing);
    expect(find.byType(Scrollbar), findsOneWidget);

    await tester.scrollUntilVisible(
      find.text('Room Six'),
      80,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.pumpAndSettle();

    expect(find.text('Room Six'), findsOneWidget);
  });

  testWidgets('clicking an earlier card rolls back later nested cards', (
    tester,
  ) async {
    const creator = UserSummary(
      id: 'creator',
      username: 'creator',
      displayName: 'Creator',
      avatarUrl: null,
      defaultAvatarKey: 'green-2',
    );
    const rollbackUser = UserSummary(
      id: 'u1',
      username: 'logan',
      displayName: 'Logan',
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
      commonRooms: [UserCommonRoom(id: 'r1', rid: 'R1', name: 'Room One')],
    );

    await tester.pumpWidget(
      _host(
        AvatarHoverCardForTest(
          user: rollbackUser,
          onResolveRoomProfile: (room) async {
            return PublicRoom(
              id: room.id,
              rid: room.rid,
              name: room.name,
              avatarUrl: null,
              defaultAvatarKey: 'room-1',
              visibility: 'private',
              joinPolicy: 'closed',
              memberCount: 2,
              onlineMemberCount: 1,
              liveParticipantCount: 0,
              joined: true,
              joinState: 'joined',
              createdBy: creator,
            );
          },
          onResolveProfile: (user) async {
            if (user.id == creator.id) {
              return user.copyWith(bio: 'Creator bio', isOnline: true);
            }
            return user;
          },
        ),
      ),
    );

    await tester.tap(find.byType(Avatar).first);
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsOneWidget);

    final commonRoomAvatar = find.byWidgetPredicate(
      (widget) => widget is Avatar && widget.label == 'Room One',
    );
    await tester.tap(commonRoomAvatar);
    await tester.pumpAndSettle();
    expect(find.text('RID: R1'), findsOneWidget);

    final creatorAvatar = find.byWidgetPredicate(
      (widget) => widget is Avatar && widget.label == 'Creator',
    );
    await tester.tap(creatorAvatar);
    await tester.pumpAndSettle();
    expect(find.text('@creator'), findsOneWidget);
    expect(find.text('Creator bio'), findsOneWidget);

    final roomCardAvatar = find.byWidgetPredicate(
      (widget) =>
          widget is Avatar && widget.label == 'Room One' && widget.size == 48,
    );
    await tester.tap(roomCardAvatar);
    await tester.pumpAndSettle();
    expect(find.text('@creator'), findsNothing);
    expect(find.text('Creator bio'), findsNothing);
    expect(find.text('RID: R1'), findsOneWidget);
    expect(find.text('@logan'), findsOneWidget);

    final userCardAvatar = find.byWidgetPredicate(
      (widget) =>
          widget is Avatar && widget.label == 'Logan' && widget.size == 48,
    );
    await tester.tap(userCardAvatar);
    await tester.pumpAndSettle();
    expect(find.text('RID: R1'), findsNothing);
    expect(find.text('@logan'), findsOneWidget);

    await tester.tapAt(const Offset(5, 5));
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsNothing);
  });

  testWidgets('common room avatar waits for the latest room profile', (
    tester,
  ) async {
    var resolveCalls = 0;
    await tester.pumpWidget(
      _host(
        AvatarHoverCardForTest(
          user: _user,
          onResolveRoomProfile: (room) async {
            resolveCalls += 1;
            return PublicRoom(
              id: room.id,
              rid: room.rid,
              name: 'Fresh Room',
              avatarUrl: null,
              defaultAvatarKey: 'room-1',
              visibility: 'private',
              joinPolicy: 'closed',
              description: 'Latest room summary',
              memberCount: 9,
              onlineMemberCount: 3,
              liveParticipantCount: 0,
              joined: true,
              joinState: 'joined',
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

    final commonRoomAvatar = find.byWidgetPredicate(
      (widget) => widget is Avatar && widget.label == '摸鱼大队',
    );
    await gesture.moveTo(tester.getCenter(commonRoomAvatar));
    await tester.pumpAndSettle();

    expect(resolveCalls, 1);
    expect(find.text('Fresh Room'), findsOneWidget);
    expect(find.text('Latest room summary'), findsOneWidget);
    expect(find.text('9 名成员'), findsOneWidget);
  });

  testWidgets('tap opens the profile card until an outside tap', (
    tester,
  ) async {
    await tester.pumpWidget(_host(const AvatarHoverCardForTest(user: _user)));

    await tester.tap(find.byType(Avatar).first);
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsOneWidget);

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(tester.getCenter(find.byType(Avatar).first));
    await tester.pumpAndSettle();
    await gesture.moveTo(const Offset(5, 5));
    await tester.pump(const Duration(milliseconds: 200));
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsOneWidget);

    await tester.tapAt(const Offset(5, 5));
    await tester.pumpAndSettle();
    expect(find.text('@logan'), findsNothing);
  });

  testWidgets(
    'Android hover cards stay inside the system navigation safe area',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(360, 640);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);

      Widget safeAreaHost(TargetPlatform platform) {
        return MaterialApp(
          theme: uiTheme().copyWith(platform: platform),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(viewPadding: const EdgeInsets.fromLTRB(12, 24, 16, 48)),
            child: child!,
          ),
          home: const Scaffold(
            body: Align(
              alignment: Alignment.bottomCenter,
              child: AvatarHoverCardForTest(user: _user),
            ),
          ),
        );
      }

      await tester.pumpWidget(safeAreaHost(TargetPlatform.android));
      await tester.tap(find.byType(Avatar).first);
      await tester.pumpAndSettle();

      final androidCard = tester.getRect(find.byType(AnchoredPanel));
      expect(androidCard.left, greaterThanOrEqualTo(12));
      expect(androidCard.top, greaterThanOrEqualTo(24));
      expect(androidCard.right, lessThanOrEqualTo(344));
      expect(androidCard.bottom, lessThanOrEqualTo(592));

      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(safeAreaHost(TargetPlatform.windows));
      await tester.tap(find.byType(Avatar).first);
      await tester.pumpAndSettle();

      final windowsCard = tester.getRect(find.byType(AnchoredPanel));
      expect(windowsCard.left, closeTo(0, 0.01));
      expect(windowsCard.bottom, closeTo(640, 0.01));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'Android positioned user cards stay inside every system safe edge',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(360, 640);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      late BuildContext launchContext;

      await tester.pumpWidget(
        MaterialApp(
          theme: uiTheme().copyWith(platform: TargetPlatform.android),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(viewPadding: const EdgeInsets.fromLTRB(12, 24, 16, 48)),
            child: child!,
          ),
          home: Builder(
            builder: (context) {
              launchContext = context;
              return const Scaffold(body: SizedBox.expand());
            },
          ),
        ),
      );

      unawaited(
        showUserProfileCardAtPosition(
          launchContext,
          position: const Offset(350, 620),
          user: _user,
          resolveOnOpen: false,
        ),
      );
      await tester.pumpAndSettle();

      final card = tester.getRect(find.byType(AnchoredPanel));
      expect(card.left, greaterThanOrEqualTo(20));
      expect(card.top, greaterThanOrEqualTo(32));
      expect(card.right, lessThanOrEqualTo(336));
      expect(card.bottom, lessThanOrEqualTo(584));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('resolves gender and common rooms before opening', (
    tester,
  ) async {
    // The message summary lacks gender/common rooms; the resolver supplies them.
    const lightweight = UserSummary(
      id: 'u1',
      username: 'logan',
      displayName: '加一',
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
    );
    var calls = 0;
    Future<UserSummary> resolve(UserSummary sender) async {
      calls++;
      return _user;
    }

    await tester.pumpWidget(
      _host(
        AvatarHoverCardForTest(user: lightweight, onResolveProfile: resolve),
      ),
    );

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(tester.getCenter(find.byType(Avatar).first));
    await tester.pumpAndSettle();

    expect(calls, 1);
    expect(find.text('♂'), findsOneWidget);
    expect(find.text('男'), findsNothing);
    expect(find.text('2 个共同房间'), findsOneWidget);
    expect(find.text('摸鱼大队'), findsOneWidget);
  });

  testWidgets('refreshes the resolved profile each time the card opens', (
    tester,
  ) async {
    const lightweight = UserSummary(
      id: 'u1',
      username: 'logan',
      displayName: 'Initial User',
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
    );
    var calls = 0;
    Future<UserSummary> resolve(UserSummary sender) async {
      calls += 1;
      return sender.copyWith(
        displayName: 'Fresh User $calls',
        bio: 'Fresh bio $calls',
        isOnline: calls.isEven,
      );
    }

    await tester.pumpWidget(
      _host(
        AvatarHoverCardForTest(user: lightweight, onResolveProfile: resolve),
      ),
    );

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);

    final avatarCenter = tester.getCenter(find.byType(Avatar).first);
    await gesture.moveTo(avatarCenter);
    await tester.pumpAndSettle();

    expect(calls, 1);
    expect(find.text('Fresh User 1'), findsOneWidget);
    expect(find.text('Fresh bio 1'), findsOneWidget);

    await gesture.moveTo(const Offset(5, 5));
    await tester.pump(const Duration(milliseconds: 200));
    await tester.pumpAndSettle();
    expect(find.text('Fresh User 1'), findsNothing);

    await gesture.moveTo(avatarCenter);
    await tester.pumpAndSettle();

    expect(calls, 2);
    expect(find.text('Fresh User 1'), findsNothing);
    expect(find.text('Fresh User 2'), findsOneWidget);
    expect(find.text('Fresh bio 2'), findsOneWidget);
  });

  testWidgets('waits for resolved profile before showing the card', (
    tester,
  ) async {
    const lightweight = UserSummary(
      id: 'u1',
      username: 'logan',
      displayName: '加一',
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
      isOnline: false,
    );
    final completer = Completer<UserSummary>();
    var calls = 0;
    Future<UserSummary> resolve(UserSummary sender) {
      calls++;
      return completer.future;
    }

    await tester.pumpWidget(
      _host(
        AvatarHoverCardForTest(user: lightweight, onResolveProfile: resolve),
      ),
    );

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(tester.getCenter(find.byType(Avatar).first));
    await tester.pump();

    expect(calls, 1);
    expect(find.text('@logan'), findsNothing);
    expect(find.text('离线'), findsNothing);

    completer.complete(_user);
    await tester.pumpAndSettle();

    expect(find.text('@logan'), findsOneWidget);
    expect(find.text('在线'), findsOneWidget);
    expect(find.text('离线'), findsNothing);
  });

  testWidgets('deleted user card is a non-interactive tombstone', (
    tester,
  ) async {
    const deleted = UserSummary(
      id: 'user_deleted',
      uid: '1000999',
      username: 'historical_name',
      displayName: 'Historical User',
      bio: 'This must not be shown.',
      gender: 'male',
      avatarUrl: '/historical-avatar.png',
      defaultAvatarKey: 'blue-3',
      roomDisplayName: 'Historical Room Name',
      roomRole: 'admin',
      isOnline: true,
      isDeleted: true,
      commonRooms: [
        UserCommonRoom(
          id: 'room_old',
          rid: 'R100',
          name: 'Historical Room',
          avatarUrl: null,
          defaultAvatarKey: 'room-1',
        ),
      ],
    );
    var resolveCalls = 0;
    var actionBuilderCalls = 0;

    await tester.pumpWidget(
      _host(
        AvatarHoverCardForTest(
          user: deleted,
          onResolveProfile: (user) async {
            resolveCalls += 1;
            return user;
          },
          profileActionBuilder: (_) {
            actionBuilderCalls += 1;
            return UserProfileAction(
              label: '管理成员',
              icon: Icons.admin_panel_settings_outlined,
              onPressed: () {},
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

    expect(resolveCalls, 0);
    expect(actionBuilderCalls, 0);
    expect(find.text('用户已注销'), findsOneWidget);
    expect(find.text('@已注销'), findsNothing);
    final tombstone = find.byKey(const ValueKey('deleted-user-profile-card'));
    expect(tombstone, findsOneWidget);
    expect(
      find.descendant(of: tombstone, matching: find.byType(StatusBadge)),
      findsNothing,
    );
    expect(
      find.descendant(of: tombstone, matching: find.byType(Icon)),
      findsNothing,
    );
    expect(find.text('This must not be shown.'), findsNothing);
    expect(find.text('Historical Room'), findsNothing);
    expect(find.text('UID: 1000999'), findsNothing);
    expect(find.text('管理成员'), findsNothing);
  });

  testWidgets('suspended user keeps their profile with a red status pill', (
    tester,
  ) async {
    const suspended = UserSummary(
      id: 'user_suspended',
      uid: '1000888',
      username: 'suspended_user',
      displayName: '被封禁用户',
      bio: '资料仍然保留',
      avatarUrl: null,
      defaultAvatarKey: 'green-2',
      isOnline: true,
      isSuspended: true,
    );

    await tester.pumpWidget(
      _host(const AvatarHoverCardForTest(user: suspended, inLive: true)),
    );

    final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await gesture.addPointer(location: Offset.zero);
    addTearDown(gesture.removePointer);
    await gesture.moveTo(tester.getCenter(find.byType(Avatar).first));
    await tester.pumpAndSettle();

    expect(find.text('被封禁用户'), findsOneWidget);
    expect(find.text('@suspended_user'), findsOneWidget);
    expect(find.text('资料仍然保留'), findsOneWidget);
    expect(find.text('封禁中'), findsOneWidget);
    expect(find.text('在线'), findsNothing);
    expect(find.text('语音'), findsNothing);
    expect(tester.widget<Text>(find.text('封禁中')).style?.color, UiColors.danger);
  });
}

Future<void> _ensureUserProfileCardOpen(
  WidgetTester tester, {
  bool settle = true,
}) async {
  final marker = find.text('@${_user.username}');
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

Finder _profileCardAvatar() {
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
  TextSelection? initialSelection,
  TextSelection? expectedSelection,
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

  if (initialSelection != null) {
    final editableTextState = tester.state<EditableTextState>(editableFinder);
    editableTextState.userUpdateTextEditingValue(
      editableTextState.textEditingValue.copyWith(selection: initialSelection),
      SelectionChangedCause.toolbar,
    );
    await tester.pump();
  }

  await tester.tap(editableFinder, buttons: kSecondaryMouseButton);
  await tester.pumpAndSettle();
  final editableTextState = tester.state<EditableTextState>(editableFinder);
  expect(
    editableTextState.textEditingValue.selection,
    expectedSelection ??
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
