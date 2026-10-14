import 'package:client/src/home/home_room_navigation.dart';
import 'package:client/src/protocol/models.dart';
import 'package:client/src/ui/ui.dart' as ui;
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  for (final compact in [false, true]) {
    testWidgets(
      'room draft replaces subtitle without replacing last message time '
      '(${compact ? 'compact' : 'wide'})',
      (tester) async {
        tester.view.devicePixelRatio = 1;
        tester.view.physicalSize = const Size(900, 700);
        addTearDown(tester.view.reset);
        final lastMessageAt = DateTime.now();
        final expectedTime =
            '${lastMessageAt.hour.toString().padLeft(2, '0')}:'
            '${lastMessageAt.minute.toString().padLeft(2, '0')}';
        final room = RoomCard(
          id: 'room_1',
          name: 'Draft room',
          avatarUrl: null,
          defaultAvatarKey: 'room-1',
          memberCount: 2,
          liveParticipantCount: 0,
          liveAvatarPreview: const [],
          lastMessage: LastMessagePreview(
            id: 'message_1',
            senderDisplayName: 'Logan',
            bodyPreview: 'latest message',
            createdAt: lastMessageAt,
          ),
          unreadCount: 0,
          updatedAt: lastMessageAt,
        );

        await tester.pumpWidget(
          MaterialApp(
            theme: ui.uiTheme(),
            home: Scaffold(
              body: HomeLobby(
                currentUser: _currentUser,
                rooms: [room],
                timestampNow: lastMessageAt,
                roomDrafts: const {'room_1': 'draft\ncontent'},
                selectedRoomId: null,
                joinedLiveRoomId: null,
                loading: false,
                error: null,
                compact: compact,
                onRoomSelected: (_) {},
                onCreateRoom: () {},
              ),
            ),
          ),
        );

        expect(
          find.byKey(const ValueKey('home-sidebar-room-draft-room_1')),
          findsOneWidget,
        );
        expect(find.textContaining('[草稿] draft content'), findsOneWidget);
        expect(find.textContaining('latest message'), findsNothing);
        expect(find.text(expectedTime), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('lobby counts rooms, live rooms and unread messages', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(1100, 700);
    addTearDown(tester.view.reset);
    final created = <String>[];
    final selected = <String>[];
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Scaffold(
          body: HomeLobby(
            currentUser: _currentUser,
            rooms: [
              _room('a', liveParticipantCount: 2, unreadCount: 3),
              _room('b', unreadCount: 4),
              _room('c'),
            ],
            timestampNow: DateTime.utc(2026, 7, 30),
            roomDrafts: const {},
            selectedRoomId: null,
            joinedLiveRoomId: null,
            loading: false,
            error: null,
            onRoomSelected: (room) => selected.add(room.id),
            onCreateRoom: () => created.add('create'),
          ),
        ),
      ),
    );

    expect(find.text('03'), findsOneWidget);
    expect(find.text('01'), findsOneWidget);
    expect(find.text('07'), findsOneWidget);
    expect(find.text('欢迎回来，Logan'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('home-lobby-room-live-a')),
      findsOneWidget,
    );

    await tester.tap(find.byKey(const ValueKey('home-lobby-room-b')));
    await tester.tap(find.byKey(const ValueKey('home-lobby-create-room')));
    await tester.pump();

    expect(selected, ['b']);
    expect(created, ['create']);
    expect(tester.takeException(), isNull);
  });

  testWidgets('latency signal shows the request latency when tapped', (
    tester,
  ) async {
    await _pumpLatencyBadge(tester);

    expect(find.byKey(const ValueKey('latency-signal-badge')), findsOneWidget);
    expect(_latencyBarColor(tester, 1), ui.UiColors.success);

    await tester.tap(find.byKey(const ValueKey('latency-signal-badge')));
    await tester.pumpAndSettle();

    expect(find.text('96 ms'), findsOneWidget);

    await tester.tapAt(const Offset(5, 5));
    await tester.pumpAndSettle();

    expect(find.text('96 ms'), findsNothing);
  });

  testWidgets('latency card hover can be pinned until an outside tap', (
    tester,
  ) async {
    await _pumpLatencyBadge(tester);

    final badge = find.byKey(const ValueKey('latency-signal-badge'));
    final hover = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await hover.addPointer(location: Offset.zero);
    addTearDown(hover.removePointer);
    await hover.moveTo(tester.getCenter(badge));
    await tester.pump(const Duration(milliseconds: 350));

    expect(find.text('96 ms'), findsOneWidget);

    await tester.tap(badge, kind: PointerDeviceKind.mouse);
    await tester.pumpAndSettle();
    await hover.moveTo(const Offset(5, 5));
    await tester.pump(const Duration(milliseconds: 150));

    expect(find.text('96 ms'), findsOneWidget);

    await tester.tapAt(const Offset(5, 5));
    await tester.pumpAndSettle();

    expect(find.text('96 ms'), findsNothing);
  });

  testWidgets('Android room scroll drag does not select a room on release', (
    tester,
  ) async {
    final rooms = List<RoomCard>.generate(8, (index) => _room('$index'));
    final selectedRoomIds = <String>[];
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
        home: Scaffold(
          body: SizedBox(
            width: 320,
            height: 430,
            child: HomeLobby(
              currentUser: _currentUser,
              rooms: rooms,
              timestampNow: DateTime.utc(2026, 7, 30),
              roomDrafts: const {},
              selectedRoomId: null,
              joinedLiveRoomId: null,
              loading: false,
              error: null,
              compact: true,
              onRoomSelected: (room) => selectedRoomIds.add(room.id),
              onCreateRoom: () {},
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final firstRoom = find.byKey(const ValueKey<String>('home-lobby-room-0'));
    final gesture = await tester.startGesture(
      tester.getCenter(firstRoom),
      kind: PointerDeviceKind.touch,
    );
    await gesture.moveBy(const Offset(0, -30));
    await tester.pump();
    await gesture.up();
    await tester.pumpAndSettle();

    expect(selectedRoomIds, isEmpty);

    await tester.tap(firstRoom, kind: PointerDeviceKind.touch);
    await tester.pumpAndSettle();
    expect(selectedRoomIds, ['0']);
    expect(tester.takeException(), isNull);
  });
}

RoomCard _room(
  String id, {
  int liveParticipantCount = 0,
  int unreadCount = 0,
}) {
  return RoomCard(
    id: id,
    name: 'Room $id',
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
    memberCount: 2,
    liveParticipantCount: liveParticipantCount,
    liveAvatarPreview: const [],
    lastMessage: null,
    unreadCount: unreadCount,
    updatedAt: DateTime.utc(2026, 7, 30),
  );
}

Future<void> _pumpLatencyBadge(WidgetTester tester) {
  return tester.pumpWidget(
    MaterialApp(
      theme: ui.uiTheme(),
      home: const Scaffold(
        body: Center(
          child: ui.LatencySignalBadge(
            activeBars: 3,
            activeColor: ui.UiColors.success,
            tooltip: '96 ms',
          ),
        ),
      ),
    ),
  );
}

Color? _latencyBarColor(WidgetTester tester, int index) {
  final bar = tester.widget<DecoratedBox>(
    find.byKey(ValueKey('latency-signal-bar-$index')),
  );
  return (bar.decoration as BoxDecoration).color;
}

final _currentUser = CurrentUser(
  id: 'user_1',
  uid: '10000001',
  username: 'logan',
  displayName: 'Logan',
  bio: '',
  gender: 'secret',
  email: null,
  emailPublic: false,
  phoneNumber: null,
  phoneNumberPublic: false,
  avatarUrl: null,
  defaultAvatarKey: 'blue-3',
  isSuperuser: false,
  createdAt: DateTime.utc(2026, 6, 4),
);
