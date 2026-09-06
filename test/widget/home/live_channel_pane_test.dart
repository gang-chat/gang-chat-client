import 'dart:ui' show PointerDeviceKind;

import 'package:client/src/config/app_config.dart';
import 'package:client/src/home/adaptive_layout.dart';
import 'package:client/src/home/home_keyboard_layout.dart';
import 'package:client/src/home/live_channel_pane.dart';
import 'package:client/src/live/live_session.dart';
import 'package:client/src/live/live_video_track_view.dart';
import 'package:client/src/protocol/models.dart';
import 'package:client/src/shell/full_screen_system_ui_controller.dart';
import 'package:client/src/ui/app_config_scope.dart';
import 'package:client/src/ui/ui.dart' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show LogicalKeyboardKey, SystemUiMode;
import 'package:flutter_test/flutter_test.dart';
import 'package:livekit_client/livekit_client.dart' as lk;

void main() {
  testWidgets('empty live channel uses Chinese empty copy without header tag', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);

    await tester.pumpWidget(
      _host(searchController: searchController, live: _liveState(const [])),
    );

    expect(find.text('语音频道里还没有人'), findsOneWidget);
    expect(find.text('No one is in live channel'), findsNothing);
    expect(find.text('Live Channel'), findsNothing);
  });

  testWidgets('screen-share control is hidden when unsupported', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: _liveState(const []),
        screenShareSupported: false,
      ),
    );

    expect(
      find.byKey(const ValueKey<String>('live-control:screen-share')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey<String>('live-control:camera')),
      findsOneWidget,
    );
  });

  testWidgets('narrow join and collapse actions are matching labeled rows', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: _liveState(const []),
        width: 360,
        joined: false,
        platform: TargetPlatform.android,
      ),
    );

    final join = find.byKey(const ValueKey<String>('live-control:join'));
    final collapse = find.byKey(
      const ValueKey<String>('live-control:collapse'),
    );
    final joinRect = tester.getRect(join);
    final collapseRect = tester.getRect(collapse);
    expect(
      find.descendant(of: join, matching: find.text('加入')),
      findsOneWidget,
    );
    expect(
      find.descendant(of: collapse, matching: find.text('收起')),
      findsOneWidget,
    );
    expect(collapseRect.width, closeTo(joinRect.width, 0.01));
    expect(collapseRect.left, closeTo(joinRect.left, 0.01));
    expect(collapseRect.top, greaterThan(joinRect.bottom));
    expect(tester.takeException(), isNull);
  });

  testWidgets('joining voice replaces the call icon with a spinner', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: _liveState(const []),
        width: 360,
        joined: false,
        joining: true,
        platform: TargetPlatform.android,
      ),
    );

    final join = find.byKey(const ValueKey<String>('live-control:join'));
    expect(
      find.descendant(of: join, matching: find.byIcon(Icons.call)),
      findsNothing,
    );
    expect(
      find.descendant(
        of: join,
        matching: find.byType(CircularProgressIndicator),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(of: join, matching: find.byType(Text)),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('room loading disables join without showing joining state', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: _liveState(const []),
        width: 360,
        loading: true,
        joined: false,
        joining: false,
        platform: TargetPlatform.android,
      ),
    );

    final join = find.byKey(const ValueKey<String>('live-control:join'));
    final button = tester.widget<ui.Button>(join);
    expect(button.loading, isFalse);
    expect(button.onPressed, isNull);
    expect(
      find.descendant(of: join, matching: find.byIcon(Icons.call)),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: join,
        matching: find.byType(CircularProgressIndicator),
      ),
      findsNothing,
    );
    expect(find.text('语音频道里还没有人'), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('leaving voice disables join without showing joining state', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: _liveState(const []),
        width: 360,
        joined: false,
        joining: false,
        leaving: true,
        platform: TargetPlatform.android,
      ),
    );

    final join = find.byKey(const ValueKey<String>('live-control:join'));
    final button = tester.widget<ui.Button>(join);
    expect(button.loading, isFalse);
    expect(button.onPressed, isNull);
    expect(
      find.descendant(of: join, matching: find.byIcon(Icons.call)),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: join,
        matching: find.byType(CircularProgressIndicator),
      ),
      findsNothing,
    );
    expect(find.text('语音频道里还没有人'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('wide join and collapse actions are matching labeled buttons', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: _liveState(const []),
        width: 960,
        joined: false,
        platform: TargetPlatform.windows,
      ),
    );

    final join = find.byKey(const ValueKey<String>('live-control:join'));
    final collapse = find.byKey(
      const ValueKey<String>('live-control:collapse'),
    );
    expect(
      find.descendant(of: join, matching: find.text('加入')),
      findsOneWidget,
    );
    expect(
      find.descendant(of: collapse, matching: find.text('收起')),
      findsOneWidget,
    );
    expect(
      tester.getSize(collapse).width,
      closeTo(tester.getSize(join).width, 0.01),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('narrow one-line joined controls keep collapse icon-only', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: _liveState(const []),
        width: 420,
        joined: true,
        platform: TargetPlatform.android,
      ),
    );

    final collapse = find.byKey(
      const ValueKey<String>('live-control:collapse'),
    );
    final collapseRect = tester.getRect(collapse);
    final cameraRect = tester.getRect(
      find.byKey(const ValueKey<String>('live-control:camera')),
    );
    expect(
      find.descendant(of: collapse, matching: find.text('收起')),
      findsNothing,
    );
    expect(collapseRect.size, cameraRect.size);
    expect(tester.takeException(), isNull);
  });

  testWidgets('narrow wrapped joined controls give collapse a labeled row', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: _liveState(const []),
        width: 360,
        joined: true,
        platform: TargetPlatform.android,
      ),
    );

    final collapse = find.byKey(
      const ValueKey<String>('live-control:collapse'),
    );
    final leave = find.byKey(const ValueKey<String>('live-control:leave'));
    final collapseRect = tester.getRect(collapse);
    expect(
      find.descendant(of: collapse, matching: find.text('收起')),
      findsOneWidget,
    );
    expect(collapseRect.width, greaterThan(44));
    expect(collapseRect.top, greaterThan(tester.getRect(leave).bottom));
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'current user live member card is square and shows connected statuses',
    (tester) async {
      final searchController = TextEditingController();
      addTearDown(searchController.dispose);
      final user = _currentUser.toSummary().copyWith(
        roomDisplayName: 'Room Me',
        roomRole: 'member',
      );
      final live = _liveState([
        _participant(id: 'live_self', user: user, headphonesMuted: true),
      ]);

      await tester.pumpWidget(
        _host(
          searchController: searchController,
          live: live,
          headphonesMuted: true,
          speakingUserIds: const {'current_user'},
        ),
      );

      final avatar = tester.widget<ui.Avatar>(
        find.byWidgetPredicate(
          (widget) =>
              widget is ui.Avatar && widget.label == 'Me' && widget.size == 42,
        ),
      );
      final cardFinder = find.ancestor(
        of: find.text('Room Me'),
        matching: find.byType(ui.PressableSurface),
      );
      final card = tester.widget<ui.PressableSurface>(cardFinder);
      final cardRect = tester.getRect(cardFinder);
      final avatarRect = tester.getRect(find.byWidget(avatar));
      final name = tester.widget<Text>(find.text('Room Me'));
      final nameRect = tester.getRect(find.text('Room Me'));
      final activityTag = find.byKey(
        const ValueKey<String>('live-member-activity:current_user'),
      );
      final activeTagRect = tester.getRect(activityTag);
      final micButtonRect = tester.getRect(
        find.byKey(
          const ValueKey<String>('live-member-status:mic:current_user'),
        ),
      );
      final headphonesButtonRect = tester.getRect(
        find.byKey(
          const ValueKey<String>('live-member-status:headphones:current_user'),
        ),
      );
      final cameraButtonFinder = find.byKey(
        const ValueKey<String>('live-member-status:camera:current_user'),
      );
      final shareButtonFinder = find.byKey(
        const ValueKey<String>('live-member-status:screen-share:current_user'),
      );
      final voiceVolumeButtonFinder = find.byKey(
        const ValueKey<String>('live-member-status:voice-volume:current_user'),
      );
      final kickButtonFinder = find.byKey(
        const ValueKey<String>('live-member-status:kick:current_user'),
      );

      expect(avatar.active, isFalse);
      expect(avatar.showBorder, isFalse);
      expect(card.height, closeTo(cardRect.width, 0.01));
      expect(avatarRect.center.dx, closeTo(cardRect.center.dx, 1));
      expect(name.textAlign, TextAlign.center);
      expect(name.style?.color, ui.UiColors.accent);
      expect(nameRect.top, greaterThan(avatarRect.bottom));
      expect(find.textContaining('(you)'), findsNothing);
      expect(find.text('正在说话'), findsNothing);
      expect(activityTag, findsOneWidget);
      expect(
        find.descendant(of: activityTag, matching: find.byIcon(Icons.mic)),
        findsOneWidget,
      );
      expect(
        find.descendant(of: activityTag, matching: find.byType(DecoratedBox)),
        findsNothing,
      );
      expect(activeTagRect.right, lessThanOrEqualTo(cardRect.right));
      expect(activeTagRect.top, lessThan(nameRect.top));
      expect(activeTagRect.bottom, lessThan(avatarRect.top));
      expect(cardRect.bottom - headphonesButtonRect.bottom, lessThan(14));
      expect(micButtonRect.width, closeTo(micButtonRect.height, 0.01));
      expect(
        headphonesButtonRect.width,
        closeTo(headphonesButtonRect.height, 0.01),
      );
      expect(micButtonRect.right, closeTo(headphonesButtonRect.left, 0.01));
      expect(cameraButtonFinder, findsNothing);
      expect(shareButtonFinder, findsNothing);
      expect(voiceVolumeButtonFinder, findsNothing);
      expect(kickButtonFinder, findsNothing);
      expect(
        find.descendant(of: cardFinder, matching: find.byType(ui.ButtonIcon)),
        findsNothing,
      );
      expect(
        find.descendant(
          of: find.byKey(
            const ValueKey<String>('live-member-status:mic:current_user'),
          ),
          matching: find.byIcon(Icons.mic),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: find.byKey(
            const ValueKey<String>(
              'live-member-status:headphones:current_user',
            ),
          ),
          matching: find.byIcon(Icons.headset_off),
        ),
        findsOneWidget,
      );
    },
  );

  testWidgets('narrow live member stage fits two square cards in one row', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    final currentUser = _currentUser.toSummary().copyWith(
      roomDisplayName: 'Room Me',
      roomRole: 'member',
    );
    final remoteUser = _user(
      'phabe',
      'Phabe',
      roomRole: 'member',
    ).copyWith(roomDisplayName: 'Room Phabe');

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: _liveState([
          _participant(id: 'live_self', user: currentUser),
          _participant(id: 'live_phabe', user: remoteUser),
        ]),
        speakingUserIds: const {'current_user'},
        width: 347,
        height: 720,
      ),
    );

    final currentCard = find.ancestor(
      of: find.text('Room Me'),
      matching: find.byType(ui.PressableSurface),
    );
    final remoteCard = find.ancestor(
      of: find.text('Room Phabe'),
      matching: find.byType(ui.PressableSurface),
    );
    final currentRect = tester.getRect(currentCard);
    final remoteRect = tester.getRect(remoteCard);
    final scale = currentRect.width / 154;
    final avatar = find.descendant(
      of: currentCard,
      matching: find.byWidgetPredicate(
        (widget) => widget is ui.Avatar && widget.label == 'Me',
      ),
    );
    final activityTag = find.descendant(
      of: currentCard,
      matching: find.byKey(
        const ValueKey<String>('live-member-activity:current_user'),
      ),
    );
    final micButton = find.descendant(
      of: currentCard,
      matching: find.byKey(
        const ValueKey<String>('live-member-status:mic:current_user'),
      ),
    );

    expect(currentRect.top, closeTo(remoteRect.top, 0.01));
    expect(currentRect.height, closeTo(162 * scale, 0.01));
    expect(remoteRect.height, closeTo(162 * scale, 0.01));
    expect(remoteRect.left - currentRect.right, closeTo(12, 0.01));
    expect(currentRect.width, lessThan(154));
    expect(tester.getRect(avatar).width, closeTo(42 * scale, 0.01));
    expect(tester.getRect(activityTag).width, closeTo(24 * scale, 0.01));
    expect(tester.getRect(micButton).width, closeTo(32.5 * scale, 0.01));
    expect(
      find.ancestor(of: currentCard, matching: find.byType(FittedBox)),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: _liveState([
          _participant(id: 'live_self', user: currentUser),
          _participant(id: 'live_phabe', user: remoteUser),
        ]),
        speakingUserIds: const {'current_user'},
        width: 365,
        height: 720,
      ),
    );
    await tester.pump(const Duration(milliseconds: 10));
    expect(tester.takeException(), isNull);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'current user member card uses local audio mute state before snapshot',
    (tester) async {
      final searchController = TextEditingController();
      addTearDown(searchController.dispose);
      final user = _currentUser.toSummary().copyWith(
        roomDisplayName: 'Room Me',
        roomRole: 'member',
      );
      final live = _liveState([_participant(id: 'live_self', user: user)]);

      await tester.pumpWidget(
        _host(
          searchController: searchController,
          live: live,
          micMuted: true,
          headphonesMuted: true,
          liveKitMicMutedByParticipantId: const {'current_user': false},
        ),
      );

      final micButton = find.byKey(
        const ValueKey<String>('live-member-status:mic:current_user'),
      );
      final headphonesButton = find.byKey(
        const ValueKey<String>('live-member-status:headphones:current_user'),
      );
      expect(
        find.descendant(of: micButton, matching: find.byIcon(Icons.mic_off)),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: headphonesButton,
          matching: find.byIcon(Icons.headset_off),
        ),
        findsOneWidget,
      );
    },
  );

  testWidgets('joining live channel hides local card until ready', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    final user = _currentUser.toSummary().copyWith(
      roomDisplayName: 'Room Me',
      roomRole: 'member',
    );
    final remoteUser = _user('phabe', 'Phabe', roomRole: 'member');
    final live = _liveState([
      _participant(id: 'live_self', user: user, micMuted: true),
      _participant(id: 'live_phabe', user: remoteUser),
    ]);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: live,
        joined: false,
        joining: true,
      ),
    );

    expect(find.text('Room Me'), findsNothing);
    expect(find.text('Phabe'), findsOneWidget);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: live,
        joined: true,
        joining: false,
      ),
    );

    expect(find.text('Room Me'), findsOneWidget);
    expect(find.text('Phabe'), findsOneWidget);
  });

  testWidgets('remote live member cards use participant avatar preset', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    final remoteUser = _user('phabe', 'Phabe', roomRole: 'member').copyWith(
      displayName: 'Global Phabe',
      roomDisplayName: 'Room Phabe',
      defaultAvatarKey: 'green-2',
    );
    final live = _liveState([
      _participant(id: 'live_phabe', user: remoteUser, micMuted: true),
    ]);

    await tester.pumpWidget(
      _host(searchController: searchController, live: live, height: 600),
    );

    final avatar = tester.widget<ui.Avatar>(
      find.byWidgetPredicate(
        (widget) =>
            widget is ui.Avatar &&
            widget.label == 'Global Phabe' &&
            widget.size == 42,
      ),
    );

    expect(avatar.defaultAvatarKey, 'green-2');
    expect(find.text('Room Phabe'), findsOneWidget);
    expect(find.text('Global Phabe'), findsNothing);
    final activityTag = find.byKey(
      const ValueKey<String>('live-member-activity:phabe'),
    );
    expect(find.text('正在收听'), findsNothing);
    expect(find.text('已静音'), findsNothing);
    expect(activityTag, findsNothing);
    expect(find.byTooltip('正在收听'), findsWidgets);
    expect(
      find.byKey(
        const ValueKey<String>('live-member-status:voice-volume:phabe'),
      ),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey<String>('live-member-status:kick:phabe')),
      findsNothing,
    );
  });

  testWidgets('connected muted member remains visible and shows muted mic', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    final remoteUser = _user('phabe', 'Phabe', roomRole: 'member');
    final live = _liveState([
      _participant(
        id: 'live_phabe',
        user: remoteUser,
        micMuted: true,
        connectionState: 'joining',
      ),
    ]);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: live,
        connectedParticipantIds: const {'phabe'},
        liveKitMicMutedByParticipantId: const {'phabe': false},
      ),
    );

    final micButton = find.byKey(
      const ValueKey<String>('live-member-status:mic:phabe'),
    );
    expect(find.text('Phabe'), findsOneWidget);
    expect(micButton, findsOneWidget);
    expect(
      find.descendant(of: micButton, matching: find.byIcon(Icons.mic_off)),
      findsOneWidget,
    );
    expect(
      find.descendant(of: micButton, matching: find.byIcon(Icons.mic)),
      findsNothing,
    );
  });

  testWidgets('joining open member ignores provisional LiveKit mic mute', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    final live = _liveState([
      _participant(
        id: 'live_phabe',
        user: _user('phabe', 'Phabe', roomRole: 'member'),
        connectionState: 'joining',
      ),
    ]);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: live,
        connectedParticipantIds: const {'phabe'},
        liveKitMicMutedByParticipantId: const {'phabe': true},
      ),
    );

    final micButton = find.byKey(
      const ValueKey<String>('live-member-status:mic:phabe'),
    );
    expect(find.text('Phabe'), findsOneWidget);
    expect(
      find.descendant(of: micButton, matching: find.byIcon(Icons.mic)),
      findsOneWidget,
    );
    expect(
      find.descendant(of: micButton, matching: find.byIcon(Icons.mic_off)),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('live member avatar opens a profile card on tap and hover', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    final resolvedIds = <String>[];
    final live = _liveState([
      _participant(
        id: 'live_phabe',
        user: _user('phabe', 'Phabe', roomRole: 'member'),
      ),
    ]);

    Future<UserSummary> resolveProfile(UserSummary user) async {
      resolvedIds.add(user.id);
      return UserSummary(
        id: user.id,
        username: 'resolved_phabe',
        displayName: 'Resolved Phabe',
        avatarUrl: user.avatarUrl,
        defaultAvatarKey: user.defaultAvatarKey,
        roomDisplayName: 'Resolved Room Phabe',
        roomRole: 'admin',
        uid: '20002',
        bio: 'Live card profile',
        isOnline: false,
      );
    }

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: live,
        width: 347,
        height: 720,
        onResolveParticipantProfile: resolveProfile,
      ),
    );

    final avatar = find.byWidgetPredicate(
      (widget) => widget is ui.Avatar && widget.label == 'Phabe',
    );
    expect(avatar, findsOneWidget);
    expect(find.text('@resolved_phabe'), findsNothing);

    await tester.tap(avatar);
    await tester.pumpAndSettle();

    expect(resolvedIds, ['phabe']);
    expect(find.text('@resolved_phabe'), findsOneWidget);
    expect(find.text('Resolved Room Phabe'), findsOneWidget);
    expect(find.text('Live card profile'), findsOneWidget);
    expect(find.text('语音'), findsOneWidget);
    expect(find.text('管理员'), findsOneWidget);

    await tester.tapAt(const Offset(4, 4));
    await tester.pumpAndSettle();
    expect(find.text('@resolved_phabe'), findsNothing);

    final hover = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await hover.addPointer(location: Offset.zero);
    addTearDown(hover.removePointer);
    await hover.moveTo(tester.getCenter(avatar));
    await tester.pumpAndSettle();

    expect(find.text('@resolved_phabe'), findsOneWidget);
  });

  testWidgets('remote live member controls adjust user volume and can kick', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    final remoteUser = _user('phabe', 'Phabe', roomRole: 'member');
    final live = _liveState([_participant(id: 'live_phabe', user: remoteUser)]);
    final volumeChanges = <double>[];
    final volumeToggles = <String>[];
    final removed = <LiveParticipant>[];

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: live,
        height: 600,
        participantVoiceVolume: (userId) => userId == 'phabe' ? 0.4 : 1,
        onParticipantVoiceVolumeChanged: (userId, volume) {
          if (userId == 'phabe') volumeChanges.add(volume);
        },
        onParticipantVoiceMuteToggled: volumeToggles.add,
        canRemoveParticipant: (_) => true,
        onRemoveParticipant: removed.add,
      ),
    );

    final volumeButton = find.byKey(
      const ValueKey<String>('live-member-status:voice-volume:phabe'),
    );
    final kickButton = find.byKey(
      const ValueKey<String>('live-member-status:kick:phabe'),
    );
    expect(volumeButton, findsOneWidget);
    expect(kickButton, findsOneWidget);
    expect(
      find.descendant(of: kickButton, matching: find.byIcon(Icons.exit_to_app)),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: volumeButton,
        matching: find.byIcon(Icons.volume_down),
      ),
      findsOneWidget,
    );
    _expectBelowTooltip(tester, '静音Phabe');
    expect(find.byTooltip('静音Phabe音量'), findsNothing);

    final hover = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await hover.addPointer(location: tester.getCenter(volumeButton));
    await tester.pump();
    final volumeSlider = find.byKey(
      const ValueKey<String>('live-volume-slider:Phabe语音音量'),
    );
    expect(volumeSlider, findsOneWidget);
    final volumePanel = find.byKey(
      const ValueKey<String>('live-volume-panel:Phabe语音音量'),
    );
    expect(tester.getSize(volumePanel).width, closeTo(32.5, 1e-9));
    expect(tester.getSize(volumePanel).height, closeTo(144 * 32.5 / 44, 1e-9));
    await tester.tapAt(
      tester.getRect(volumeSlider).bottomCenter - const Offset(0, 1),
    );
    await tester.pump();
    expect(volumeChanges.last, closeTo(0, 1e-9));
    await tester.tapAt(
      tester.getRect(volumeSlider).topCenter + const Offset(0, 1),
    );
    await tester.pump();
    expect(volumeChanges.last, closeTo(2, 0.05));
    await tester.tap(volumeButton);
    await tester.pump();
    expect(volumeToggles, ['phabe']);

    await hover.removePointer();
    await tester.pumpAndSettle();
    await tester.tap(kickButton);
    expect(removed.single.user.id, 'phabe');

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: live,
        height: 600,
        participantVoiceVolume: (userId) => userId == 'phabe' ? 0 : 1,
      ),
    );
    _expectBelowTooltip(tester, '取消静音Phabe');
    expect(find.byTooltip('取消静音Phabe音量'), findsNothing);
  });

  testWidgets('remote member volume icon follows the shared 50% threshold', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    final remoteUser = _user('phabe', 'Phabe', roomRole: 'member');
    final live = _liveState([_participant(id: 'live_phabe', user: remoteUser)]);

    Future<void> pumpVolume(double volume) {
      return tester.pumpWidget(
        _host(
          searchController: searchController,
          live: live,
          height: 600,
          participantVoiceVolume: (_) => volume,
        ),
      );
    }

    Finder volumeIcon(IconData icon) {
      final button = find.byKey(
        const ValueKey<String>('live-member-status:voice-volume:phabe'),
      );
      return find.descendant(of: button, matching: find.byIcon(icon));
    }

    await pumpVolume(0.49);
    expect(volumeIcon(Icons.volume_down), findsOneWidget);

    await pumpVolume(0.5);
    expect(volumeIcon(Icons.volume_up), findsOneWidget);

    await pumpVolume(1.5);
    expect(volumeIcon(Icons.volume_up), findsOneWidget);

    await pumpVolume(0);
    expect(volumeIcon(Icons.volume_off), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'Android hold opens remote volume without toggling the mute button',
    (tester) async {
      final searchController = TextEditingController();
      addTearDown(searchController.dispose);
      final remoteUser = _user('phabe', 'Phabe', roomRole: 'member');
      final live = _liveState([
        _participant(id: 'live_phabe', user: remoteUser),
      ]);
      final volumeToggles = <String>[];
      final volumeChanges = <double>[];

      await tester.pumpWidget(
        _host(
          searchController: searchController,
          live: live,
          width: 347,
          height: 720,
          platform: TargetPlatform.android,
          participantVoiceVolume: (_) => 0.4,
          onParticipantVoiceVolumeChanged: (_, volume) {
            volumeChanges.add(volume);
          },
          onParticipantVoiceMuteToggled: volumeToggles.add,
        ),
      );

      final volumeButton = find.byKey(
        const ValueKey<String>('live-member-status:voice-volume:phabe'),
      );
      final volumeSlider = find.byKey(
        const ValueKey<String>('live-volume-slider:Phabe语音音量'),
      );
      expect(volumeSlider, findsNothing);

      await tester.tap(volumeButton);
      await tester.pump();
      expect(volumeToggles, ['phabe']);
      volumeToggles.clear();

      await tester.longPress(volumeButton);
      await tester.pump();

      expect(volumeSlider, findsOneWidget);
      expect(volumeToggles, isEmpty);
      expect(find.byTooltip('静音Phabe音量'), findsNothing);
      final volumePanel = find.byKey(
        const ValueKey<String>('live-volume-panel:Phabe语音音量'),
      );
      final buttonRect = tester.getRect(volumeButton);
      final panelRect = tester.getRect(volumePanel);
      expect(panelRect.width, closeTo(buttonRect.width, 0.01));
      expect(panelRect.center.dx, closeTo(buttonRect.center.dx, 0.01));

      await tester.tapAt(
        tester.getRect(volumeSlider).bottomCenter - const Offset(0, 1),
      );
      await tester.pump();
      expect(volumeChanges.last, closeTo(0, 0.02));

      await tester.pump(const Duration(seconds: 3));
      await tester.pump(const Duration(milliseconds: 100));
      expect(volumeSlider, findsNothing);
    },
  );

  testWidgets('remote live member moderation controls use danger icons', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    final remoteUser = _user('phabe', 'Phabe', roomRole: 'member');
    final live = _liveState([
      _participant(
        id: 'live_phabe',
        user: remoteUser,
        micMuted: true,
        micBlocked: true,
        headphonesMuted: true,
        headphonesBlocked: true,
        voiceBlocked: true,
      ),
    ]);
    final micModerations = <LiveParticipant>[];
    final headphonesModerations = <LiveParticipant>[];

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: live,
        canModerateParticipant: (_) => true,
        onToggleParticipantMicModeration: micModerations.add,
        onToggleParticipantHeadphonesModeration: headphonesModerations.add,
      ),
    );

    final micButton = find.byKey(
      const ValueKey<String>('live-member-status:mic:phabe'),
    );
    final headphonesButton = find.byKey(
      const ValueKey<String>('live-member-status:headphones:phabe'),
    );
    expect(
      find.byKey(const ValueKey<String>('live-member-activity:phabe')),
      findsNothing,
    );
    expect(find.text('已被麦克风静音'), findsNothing);
    expect(find.text('已被耳机静音'), findsNothing);
    expect(
      tester
          .widget<Icon>(
            find.descendant(
              of: micButton,
              matching: find.byIcon(Icons.mic_off),
            ),
          )
          .color,
      ui.UiColors.danger,
    );
    expect(
      tester
          .widget<Icon>(
            find.descendant(
              of: headphonesButton,
              matching: find.byIcon(Icons.headset_off),
            ),
          )
          .color,
      ui.UiColors.danger,
    );

    await tester.tap(micButton);
    await tester.tap(headphonesButton);

    expect(micModerations.single.user.id, 'phabe');
    expect(headphonesModerations.single.user.id, 'phabe');
  });

  testWidgets(
    'live member media cards show top-left name and keep status controls',
    (tester) async {
      final searchController = TextEditingController();
      addTearDown(searchController.dispose);
      liveVideoTrackRendererForTest = (track, fit, mirrorLocal) {
        return ColoredBox(
          key: ValueKey<String>(
            'live-video-renderer:${track.identity}:${track.isScreenShare}',
          ),
          color: Colors.black,
        );
      };
      addTearDown(resetLiveVideoTrackRendererForTest);
      var stageSelections = 0;

      Future<void> pumpMediaCard({required bool screenShare}) async {
        final user = _currentUser.toSummary().copyWith(
          roomDisplayName: 'Room Me',
          roomRole: 'member',
        );
        final live = _liveState([
          _participant(
            id: 'live_self',
            user: user,
            cameraOn: !screenShare,
            screenSharing: screenShare,
          ),
        ]);

        await tester.pumpWidget(
          _host(
            searchController: searchController,
            platform: TargetPlatform.windows,
            live: live,
            width: 347,
            height: 720,
            videoTracks: [
              _liveVideoTrack(
                identity: 'current_user',
                isScreenShare: screenShare,
                isLocal: true,
              ),
            ],
            onStageSelectionChanged: (_) => stageSelections += 1,
          ),
        );
        await tester.pump();
      }

      await pumpMediaCard(screenShare: false);
      _expectMediaMemberCard(
        tester,
        activityIcon: Icons.videocam,
        stoppedThumbnailKey: null,
      );
      final cameraThumbnail = find.byKey(
        const ValueKey<String>(
          'live-member:video-thumbnail:current_user:false',
        ),
      );
      final cameraHover = find.byKey(
        const ValueKey<String>('live-member:video-hover:current_user:false'),
      );
      expect(tester.widget<AnimatedOpacity>(cameraHover).opacity, 0);
      final hover = await tester.createGesture(kind: PointerDeviceKind.mouse);
      await hover.addPointer(location: tester.getCenter(cameraThumbnail));
      await tester.pump(const Duration(milliseconds: 150));
      expect(tester.widget<AnimatedOpacity>(cameraHover).opacity, 1);
      expect(
        find.descendant(
          of: cameraThumbnail,
          matching: find.byIcon(Icons.search),
        ),
        findsOneWidget,
      );
      await hover.removePointer();
      await tester.tap(
        find.byKey(
          const ValueKey<String>('live-video-renderer:current_user:false'),
        ),
      );
      expect(stageSelections, 1);

      await pumpMediaCard(screenShare: true);
      _expectMediaMemberCard(
        tester,
        activityIcon: Icons.screen_share_outlined,
        stoppedThumbnailKey: 'live-member:screen-share-thumbnail',
      );
      await tester.tap(
        find.byKey(
          const ValueKey<String>('live-member:screen-share-thumbnail'),
        ),
      );
      expect(stageSelections, 2);
    },
  );

  testWidgets(
    'camera thumbnails follow shared mirror state without SDK auto mirror',
    (tester) async {
      final searchController = TextEditingController();
      addTearDown(searchController.dispose);
      final mirrorByIdentity = <String, bool>{};
      liveVideoTrackRendererForTest = (track, fit, shouldMirror) {
        mirrorByIdentity[track.identity] = shouldMirror;
        return const ColoredBox(color: Colors.black);
      };
      addTearDown(resetLiveVideoTrackRendererForTest);

      await tester.pumpWidget(
        _host(
          searchController: searchController,
          live: _liveState([
            _participant(
              id: 'live_self',
              user: _currentUser.toSummary(),
              cameraOn: true,
              cameraMirrored: true,
            ),
            _participant(
              id: 'live_phabe',
              user: _user('phabe', 'Phabe', roomRole: 'member'),
              cameraOn: true,
            ),
          ]),
          videoTracks: [
            _liveVideoTrack(
              identity: 'current_user',
              isScreenShare: false,
              isLocal: true,
            ),
            _liveVideoTrack(
              identity: 'phabe',
              isScreenShare: false,
              isLocal: false,
            ),
          ],
        ),
      );
      await tester.pump();

      expect(mirrorByIdentity['current_user'], isTrue);
      expect(mirrorByIdentity['phabe'], isFalse);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'Android local camera texture opens the same stage preview as Windows',
    (tester) async {
      final searchController = TextEditingController();
      addTearDown(searchController.dispose);
      liveVideoTrackRendererForTest = (track, fit, shouldMirror) {
        return const ColoredBox(color: Colors.black);
      };
      addTearDown(resetLiveVideoTrackRendererForTest);
      final selections = <LiveStageSelection?>[];

      await tester.pumpWidget(
        _host(
          searchController: searchController,
          platform: TargetPlatform.android,
          live: _liveState([
            _participant(
              id: 'live_self',
              user: _currentUser.toSummary(),
              cameraOn: true,
            ),
          ]),
          videoTracks: [
            _liveVideoTrack(
              identity: 'current_user',
              isScreenShare: false,
              isLocal: true,
            ),
          ],
          onStageSelectionChanged: selections.add,
        ),
      );
      await tester.pump();

      final androidTapTarget = find.byKey(
        const ValueKey<String>('live-member:android-local-camera-tap'),
      );
      expect(androidTapTarget, findsOneWidget);
      await tester.tap(androidTapTarget);

      expect(selections, hasLength(1));
      expect(selections.single?.identity, 'current_user');
      expect(selections.single?.isScreenShare, isFalse);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('local camera stage exposes flip and shared mirror controls', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    final renderedMirrors = <bool>[];
    liveVideoTrackRendererForTest = (track, fit, shouldMirror) {
      if (track.identity == 'current_user') {
        renderedMirrors.add(shouldMirror);
      }
      return const ColoredBox(color: Colors.black);
    };
    addTearDown(resetLiveVideoTrackRendererForTest);
    var flipCalls = 0;
    final sharedMirrorChanges = <bool>[];

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        platform: TargetPlatform.android,
        live: _liveState([
          _participant(
            id: 'live_self',
            user: _currentUser.toSummary(),
            cameraOn: true,
          ),
        ]),
        videoTracks: [
          _liveVideoTrack(
            identity: 'current_user',
            isScreenShare: false,
            isLocal: true,
          ),
        ],
        stageSelection: const LiveStageSelection.track(
          identity: 'current_user',
          isScreenShare: false,
        ),
        onFlipCamera: () async {
          flipCalls += 1;
          return true;
        },
        onSetLocalCameraMirrored: (mirrored) async {
          sharedMirrorChanges.add(mirrored);
          return true;
        },
      ),
    );
    await tester.pump();

    final flip = find.byKey(const ValueKey<String>('live-stage:flip-camera'));
    final mirror = find.byKey(
      const ValueKey<String>('live-stage:mirror-camera'),
    );
    expect(flip, findsOneWidget);
    expect(mirror, findsOneWidget);
    expect(renderedMirrors.last, isFalse);

    await tester.tap(flip);
    await tester.pump();
    expect(flipCalls, 1);

    await tester.tap(mirror);
    await tester.pump();
    expect(sharedMirrorChanges, [true]);
    expect(renderedMirrors.last, isTrue);
    expect(tester.takeException(), isNull);
  });

  testWidgets('local camera stage hides flip without camera capability', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    liveVideoTrackRendererForTest = (track, fit, shouldMirror) {
      return const ColoredBox(color: Colors.black);
    };
    addTearDown(resetLiveVideoTrackRendererForTest);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        platform: TargetPlatform.android,
        live: _liveState([
          _participant(
            id: 'live_self',
            user: _currentUser.toSummary(),
            cameraOn: true,
          ),
        ]),
        videoTracks: [
          _liveVideoTrack(
            identity: 'current_user',
            isScreenShare: false,
            isLocal: true,
          ),
        ],
        stageSelection: const LiveStageSelection.track(
          identity: 'current_user',
          isScreenShare: false,
        ),
      ),
    );
    await tester.pump();

    expect(
      find.byKey(const ValueKey<String>('live-stage:flip-camera')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey<String>('live-stage:mirror-camera')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'remote camera stage hides flip and mirrors only the local viewer',
    (tester) async {
      final searchController = TextEditingController();
      addTearDown(searchController.dispose);
      final renderedMirrors = <bool>[];
      liveVideoTrackRendererForTest = (track, fit, shouldMirror) {
        renderedMirrors.add(shouldMirror);
        return const ColoredBox(color: Colors.black);
      };
      addTearDown(resetLiveVideoTrackRendererForTest);
      final sharedMirrorChanges = <bool>[];

      await tester.pumpWidget(
        _host(
          searchController: searchController,
          platform: TargetPlatform.android,
          live: _liveState([
            _participant(
              id: 'live_phabe',
              user: _user('phabe', 'Phabe', roomRole: 'member'),
              cameraOn: true,
            ),
          ]),
          videoTracks: [
            _liveVideoTrack(
              identity: 'phabe',
              isScreenShare: false,
              isLocal: false,
            ),
          ],
          stageSelection: const LiveStageSelection.track(
            identity: 'phabe',
            isScreenShare: false,
          ),
          onFlipCamera: () async => true,
          onSetLocalCameraMirrored: (mirrored) async {
            sharedMirrorChanges.add(mirrored);
            return true;
          },
        ),
      );
      await tester.pump();

      expect(
        find.byKey(const ValueKey<String>('live-stage:flip-camera')),
        findsNothing,
      );
      final mirror = find.byKey(
        const ValueKey<String>('live-stage:mirror-camera'),
      );
      expect(mirror, findsOneWidget);
      expect(renderedMirrors.last, isFalse);

      await tester.tap(mirror);
      await tester.pump();
      expect(renderedMirrors.last, isTrue);
      expect(sharedMirrorChanges, isEmpty);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('screen-share stage has no camera transform controls', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    liveVideoTrackRendererForTest = (track, fit, shouldMirror) {
      return const ColoredBox(color: Colors.black);
    };
    addTearDown(resetLiveVideoTrackRendererForTest);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: _liveState([
          _participant(
            id: 'live_phabe',
            user: _user('phabe', 'Phabe', roomRole: 'member'),
            screenSharing: true,
          ),
        ]),
        videoTracks: [
          _liveVideoTrack(
            identity: 'phabe',
            isScreenShare: true,
            isLocal: false,
          ),
        ],
        stageSelection: const LiveStageSelection.track(
          identity: 'phabe',
          isScreenShare: true,
        ),
      ),
    );
    await tester.pump();

    expect(
      find.byKey(const ValueKey<String>('live-stage:flip-camera')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey<String>('live-stage:mirror-camera')),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('remote media thumbnail joins live room and selects stage', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    var joinCalls = 0;
    final selections = <LiveStageSelection?>[];

    Future<void> pumpRemoteThumbnail({required bool screenShare}) async {
      final user = _user('phabe', 'Room Phabe', roomRole: 'member');
      await tester.pumpWidget(
        _host(
          searchController: searchController,
          live: _liveState([
            _participant(
              id: 'live_phabe',
              user: user,
              cameraOn: !screenShare,
              screenSharing: screenShare,
            ),
          ]),
          joined: false,
          onJoin: () => joinCalls += 1,
          onStageSelectionChanged: selections.add,
        ),
      );
      await tester.pump();
    }

    await pumpRemoteThumbnail(screenShare: true);
    await tester.tap(
      find.byKey(const ValueKey<String>('live-member:screen-share-thumbnail')),
    );
    expect(joinCalls, 1);
    expect(selections.single?.identity, 'phabe');
    expect(selections.single?.isScreenShare, true);

    await pumpRemoteThumbnail(screenShare: false);
    await tester.tap(
      find.byKey(const ValueKey<String>('live-member:camera-thumbnail')),
    );
    expect(joinCalls, 2);
    expect(selections.last?.identity, 'phabe');
    expect(selections.last?.isScreenShare, false);
  });

  testWidgets('Android screen-share thumbnail joins voice and selects stage', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    var joinCalls = 0;
    final selections = <LiveStageSelection?>[];

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: _liveState([
          _participant(
            id: 'live_phabe',
            user: _user('phabe', 'Room Phabe', roomRole: 'member'),
            screenSharing: true,
          ),
        ]),
        platform: TargetPlatform.android,
        joined: false,
        onJoin: () => joinCalls += 1,
        onStageSelectionChanged: selections.add,
      ),
    );
    await tester.pump();

    await tester.tap(
      find.byKey(const ValueKey<String>('live-member:screen-share-thumbnail')),
    );

    expect(joinCalls, 1);
    expect(selections.single?.identity, 'phabe');
    expect(selections.single?.isScreenShare, true);
  });

  testWidgets(
    'Android screen-share thumbnail watches directly when already joined',
    (tester) async {
      final searchController = TextEditingController();
      addTearDown(searchController.dispose);
      var joinCalls = 0;
      final selections = <LiveStageSelection?>[];

      await tester.pumpWidget(
        _host(
          searchController: searchController,
          live: _liveState([
            _participant(
              id: 'live_phabe',
              user: _user('phabe', 'Room Phabe', roomRole: 'member'),
              screenSharing: true,
            ),
          ]),
          platform: TargetPlatform.android,
          joined: true,
          onJoin: () => joinCalls += 1,
          onStageSelectionChanged: selections.add,
        ),
      );
      await tester.pump();

      await tester.tap(
        find.byKey(
          const ValueKey<String>('live-member:screen-share-thumbnail'),
        ),
      );

      expect(joinCalls, 0);
      expect(selections.single?.identity, 'phabe');
      expect(selections.single?.isScreenShare, true);
    },
  );

  testWidgets('focused screen share leaves member card in avatar layout', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    liveVideoTrackRendererForTest = (track, fit, mirrorLocal) {
      return ColoredBox(
        key: ValueKey<String>(
          'live-video-renderer:${track.identity}:${track.isScreenShare}',
        ),
        color: Colors.black,
      );
    };
    addTearDown(resetLiveVideoTrackRendererForTest);
    final user = _user('phabe', 'Room Phabe', roomRole: 'member');
    final live = _liveState([
      _participant(id: 'live_phabe', user: user, screenSharing: true),
    ]);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: live,
        videoTracks: [
          _liveVideoTrack(
            identity: 'phabe',
            isScreenShare: true,
            isLocal: false,
          ),
        ],
        stageSelection: const LiveStageSelection.track(
          identity: 'phabe',
          isScreenShare: true,
        ),
      ),
    );
    await tester.pump();

    final cardFinder = find.ancestor(
      of: find.text('Room Phabe'),
      matching: find.byType(ui.PressableSurface),
    );
    expect(cardFinder, findsOneWidget);
    expect(
      find.descendant(
        of: cardFinder,
        matching: find.byKey(
          const ValueKey<String>('live-member:screen-share-thumbnail'),
        ),
      ),
      findsNothing,
    );
    expect(
      find.descendant(of: cardFinder, matching: find.byType(ui.Avatar)),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: cardFinder,
        matching: find.byType(LiveVideoTrackView),
      ),
      findsNothing,
    );
  });

  testWidgets('focused camera leaves member card in avatar layout', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    LiveVideoTrackFit? renderedFit;
    liveVideoTrackRendererForTest = (track, fit, mirrorLocal) {
      renderedFit = fit;
      return ColoredBox(
        key: ValueKey<String>(
          'live-video-renderer:${track.identity}:${track.isScreenShare}',
        ),
        color: Colors.black,
      );
    };
    addTearDown(resetLiveVideoTrackRendererForTest);
    final user = _user('phabe', 'Room Phabe', roomRole: 'member');
    final live = _liveState([
      _participant(id: 'live_phabe', user: user, cameraOn: true),
    ]);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: live,
        videoTracks: [
          _liveVideoTrack(
            identity: 'phabe',
            isScreenShare: false,
            isLocal: false,
          ),
        ],
        stageSelection: const LiveStageSelection.track(
          identity: 'phabe',
          isScreenShare: false,
        ),
      ),
    );
    await tester.pump();

    final cardFinder = find.ancestor(
      of: find.text('Room Phabe'),
      matching: find.byType(ui.PressableSurface),
    );
    expect(cardFinder, findsOneWidget);
    expect(
      find.descendant(
        of: cardFinder,
        matching: find.byKey(
          const ValueKey<String>('live-member:camera-thumbnail'),
        ),
      ),
      findsNothing,
    );
    expect(
      find.descendant(of: cardFinder, matching: find.byType(ui.Avatar)),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: cardFinder,
        matching: find.byType(LiveVideoTrackView),
      ),
      findsNothing,
    );
    expect(renderedFit, LiveVideoTrackFit.contain);
  });

  testWidgets('remote screen share stage exposes bottom-right mute control', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    liveVideoTrackRendererForTest = (track, fit, mirrorLocal) {
      return ColoredBox(
        key: ValueKey<String>(
          'live-video-renderer:${track.identity}:${track.isScreenShare}',
        ),
        color: Colors.black,
      );
    };
    addTearDown(resetLiveVideoTrackRendererForTest);
    var muteToggles = 0;
    final volumeChanges = <double>[];
    final live = _liveState([
      _participant(
        id: 'live_phabe',
        user: _user('phabe', 'Phabe', roomRole: 'member'),
        screenSharing: true,
        screenViewers: [
          _currentUser.toSummary().copyWith(roomDisplayName: 'Room Kai'),
        ],
      ),
    ]);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: live,
        height: 620,
        videoTracks: [
          _liveVideoTrack(
            identity: 'phabe',
            isScreenShare: true,
            isLocal: false,
          ),
        ],
        stageSelection: const LiveStageSelection.track(
          identity: 'phabe',
          isScreenShare: true,
        ),
        screenShareVolume: 0.75,
        onScreenShareVolumeChanged: volumeChanges.add,
        onScreenShareMuteToggled: () => muteToggles += 1,
      ),
    );
    await tester.pump();

    final muteButton = find.byKey(
      const ValueKey<String>('live-stage:screen-share-volume'),
    );
    expect(muteButton, findsOneWidget);
    expect(
      find.byKey(const ValueKey<String>('live-volume-slider:共享屏幕输出音量')),
      findsNothing,
    );

    final rendererRect = tester.getRect(
      find.byKey(const ValueKey<String>('live-video-renderer:phabe:true')),
    );
    final buttonRect = tester.getRect(muteButton);
    expect(buttonRect.right, closeTo(rendererRect.right - 8, 0.01));
    expect(buttonRect.bottom, closeTo(rendererRect.bottom - 8, 0.01));
    final viewerPreview = find.byKey(
      const ValueKey<String>('live-stage:screen-viewers'),
    );
    expect(viewerPreview, findsOneWidget);
    expect(
      tester
          .widget<ui.Avatar>(
            find.descendant(
              of: viewerPreview,
              matching: find.byType(ui.Avatar),
            ),
          )
          .label,
      'Me',
    );
    expect(find.text('共 1 人'), findsOneWidget);
    final viewerPreviewRect = tester.getRect(viewerPreview);
    expect(viewerPreviewRect.left, closeTo(rendererRect.left + 8, 0.01));
    expect(viewerPreviewRect.bottom, closeTo(rendererRect.bottom - 8, 0.01));

    final hover = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await hover.addPointer(location: tester.getCenter(muteButton));
    await tester.pump();

    final volumeSlider = find.byKey(
      const ValueKey<String>('live-volume-slider:共享屏幕输出音量'),
    );
    expect(volumeSlider, findsOneWidget);
    await tester.tapAt(
      tester.getRect(volumeSlider).bottomCenter - const Offset(0, 1),
    );
    await tester.pump();
    expect(volumeChanges.last, closeTo(0, 0.02));

    await tester.tap(muteButton);
    await tester.pump();
    expect(muteToggles, 1);

    await hover.removePointer();
  });

  testWidgets(
    'Android screen share volume toggles and reflects a zero slider value',
    (tester) async {
      final searchController = TextEditingController();
      addTearDown(searchController.dispose);
      liveVideoTrackRendererForTest = (track, fit, mirrorLocal) {
        return const ColoredBox(color: Colors.black);
      };
      addTearDown(resetLiveVideoTrackRendererForTest);
      final live = _liveState([
        _participant(
          id: 'live_phabe',
          user: _user('phabe', 'Phabe', roomRole: 'member'),
          screenSharing: true,
        ),
      ]);
      var screenShareVolume = 0.75;
      var rememberedVolume = screenShareVolume;

      await tester.pumpWidget(
        StatefulBuilder(
          builder: (context, setState) => _host(
            searchController: searchController,
            live: live,
            height: 620,
            platform: TargetPlatform.android,
            videoTracks: [
              _liveVideoTrack(
                identity: 'phabe',
                isScreenShare: true,
                isLocal: false,
              ),
            ],
            stageSelection: const LiveStageSelection.track(
              identity: 'phabe',
              isScreenShare: true,
            ),
            screenShareVolume: screenShareVolume,
            onScreenShareVolumeChanged: (volume) {
              setState(() {
                screenShareVolume = volume;
                if (volume > 0) rememberedVolume = volume;
              });
            },
            onScreenShareMuteToggled: () {
              setState(() {
                if (screenShareVolume <= 0) {
                  screenShareVolume = rememberedVolume;
                } else {
                  rememberedVolume = screenShareVolume;
                  screenShareVolume = 0;
                }
              });
            },
          ),
        ),
      );
      await tester.pump();

      final volumeButton = find.byKey(
        const ValueKey<String>('live-stage:screen-share-volume'),
      );
      expect(
        find.descendant(
          of: volumeButton,
          matching: find.byIcon(Icons.volume_up),
        ),
        findsOneWidget,
      );

      await tester.tap(volumeButton);
      await tester.pump();
      expect(screenShareVolume, 0);
      expect(
        find.descendant(
          of: volumeButton,
          matching: find.byIcon(Icons.volume_off),
        ),
        findsOneWidget,
      );

      await tester.tap(volumeButton);
      await tester.pump();
      expect(screenShareVolume, 0.75);

      await tester.longPress(volumeButton);
      await tester.pump();
      final volumeSlider = find.byKey(
        const ValueKey<String>('live-volume-slider:共享屏幕输出音量'),
      );
      expect(volumeSlider, findsOneWidget);
      await tester.tapAt(
        tester.getRect(volumeSlider).bottomCenter - const Offset(0, 1),
      );
      await tester.pump();
      expect(screenShareVolume, closeTo(0, 0.02));
      expect(
        find.descendant(
          of: volumeButton,
          matching: find.byIcon(Icons.volume_off),
        ),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'suspended stage releases its renderer while preserving the stage surface',
    (tester) async {
      final searchController = TextEditingController();
      addTearDown(searchController.dispose);
      liveVideoTrackRendererForTest = (track, fit, mirrorLocal) {
        return ColoredBox(
          key: ValueKey<String>(
            'live-video-renderer:${track.identity}:${track.isScreenShare}',
          ),
          color: Colors.black,
        );
      };
      addTearDown(resetLiveVideoTrackRendererForTest);

      await tester.pumpWidget(
        _host(
          searchController: searchController,
          live: _liveState(const []),
          height: 620,
          videoTracks: [
            _liveVideoTrack(
              identity: 'phabe',
              isScreenShare: true,
              isLocal: false,
            ),
          ],
          stageSelection: const LiveStageSelection.track(
            identity: 'phabe',
            isScreenShare: true,
          ),
          suspendStageVideo: true,
        ),
      );
      await tester.pump();

      expect(
        find.byKey(const ValueKey<String>('live-video-renderer:phabe:true')),
        findsNothing,
      );
      expect(
        find.byKey(const ValueKey<String>('live-stage:video-suspended')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey<String>('live-stage:fullscreen')),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'local screen share stage opens full screen without remote-only controls',
    (tester) async {
      final searchController = TextEditingController();
      addTearDown(searchController.dispose);
      liveVideoTrackRendererForTest = (track, fit, mirrorLocal) {
        return ColoredBox(
          key: ValueKey<String>(
            'live-video-renderer:${track.identity}:${track.isScreenShare}',
          ),
          color: Colors.black,
        );
      };
      addTearDown(resetLiveVideoTrackRendererForTest);
      final live = _liveState([
        _participant(
          id: 'live_self',
          user: _currentUser.toSummary().copyWith(
            roomDisplayName: 'Room Me',
            roomRole: 'member',
          ),
          screenSharing: true,
        ),
      ]);
      LiveVideoTrack? fullScreenTrack;

      await tester.pumpWidget(
        _host(
          searchController: searchController,
          live: live,
          height: 620,
          videoTracks: [
            _liveVideoTrack(
              identity: 'current_user',
              isScreenShare: true,
              isLocal: true,
            ),
          ],
          stageSelection: const LiveStageSelection.track(
            identity: 'current_user',
            isScreenShare: true,
          ),
          onEnterFullScreen: (track) => fullScreenTrack = track,
        ),
      );
      await tester.pump();

      final exitButton = find.byKey(const ValueKey<String>('live-stage:exit'));
      final fullScreenButton = find.byKey(
        const ValueKey<String>('live-stage:fullscreen'),
      );
      expect(exitButton, findsOneWidget);
      expect(fullScreenButton, findsOneWidget);
      expect(
        find.byKey(const ValueKey<String>('live-stage:screen-share-volume')),
        findsNothing,
      );
      expect(
        find.byKey(const ValueKey<String>('live-stage:screen-viewers')),
        findsNothing,
      );

      final rendererRect = tester.getRect(
        find.byKey(
          const ValueKey<String>('live-video-renderer:current_user:true'),
        ),
      );
      final fullScreenRect = tester.getRect(fullScreenButton);
      expect(fullScreenRect.right, closeTo(rendererRect.right - 8, 0.01));

      await tester.tap(fullScreenButton);
      await tester.pump();
      expect(fullScreenTrack?.identity, 'current_user');
      expect(fullScreenTrack?.isScreenShare, isTrue);
      expect(fullScreenTrack?.isLocal, isTrue);
    },
  );

  testWidgets('full screen live stage exits with Escape', (tester) async {
    liveVideoTrackRendererForTest = (track, fit, mirrorLocal) {
      return const ColoredBox(color: Colors.black);
    };
    addTearDown(resetLiveVideoTrackRendererForTest);
    var exitCount = 0;

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: MediaQuery(
          data: const MediaQueryData(
            size: Size(800, 600),
            viewPadding: EdgeInsets.fromLTRB(6, 20, 24, 30),
          ),
          child: LiveFullScreenStage(
            track: _liveVideoTrack(
              identity: 'phabe',
              isScreenShare: true,
              isLocal: false,
            ),
            label: 'Phabe 的屏幕共享',
            screenShareViewers: [_currentUser.toSummary()],
            screenShareVolume: 0.75,
            onScreenShareVolumeChanged: (_) {},
            onScreenShareMuteToggled: () {},
            onExit: () => exitCount++,
          ),
        ),
      ),
    );
    await tester.pump();

    final labelReveal = find.byKey(
      const ValueKey<String>('live-fullscreen-stage:label-reveal'),
    );
    final viewersReveal = find.byKey(
      const ValueKey<String>('live-fullscreen-stage:screen-viewers-reveal'),
    );
    final exitReveal = find.byKey(
      const ValueKey<String>('live-fullscreen-stage:exit-reveal'),
    );
    final volumeReveal = find.byKey(
      const ValueKey<String>('live-fullscreen-stage:volume-reveal'),
    );
    final pointerRegion = find.byKey(
      const ValueKey<String>('live-fullscreen-stage:pointer-region'),
    );
    MouseCursor pointerCursor() =>
        tester.widget<MouseRegion>(pointerRegion).cursor;
    AnimatedOpacity revealOpacity(Finder reveal) {
      return tester.widget<AnimatedOpacity>(
        find.descendant(of: reveal, matching: find.byType(AnimatedOpacity)),
      );
    }

    expect(labelReveal, findsOneWidget);
    expect(viewersReveal, findsOneWidget);
    expect(exitReveal, findsOneWidget);
    expect(volumeReveal, findsOneWidget);
    expect(tester.getTopLeft(labelReveal), const Offset(20, 34));
    expect(tester.getTopRight(exitReveal).dx, closeTo(762, 0.01));
    expect(tester.getBottomRight(volumeReveal).dx, closeTo(762, 0.01));
    expect(tester.getBottomRight(volumeReveal).dy, closeTo(556, 0.01));
    expect(revealOpacity(labelReveal).opacity, 1);
    expect(revealOpacity(viewersReveal).opacity, 1);
    expect(pointerCursor(), MouseCursor.defer);

    final hover = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await hover.addPointer(location: tester.getCenter(labelReveal));
    await tester.pump(const Duration(seconds: 3));
    await tester.pump(const Duration(milliseconds: 180));
    expect(revealOpacity(labelReveal).opacity, 0);
    expect(revealOpacity(viewersReveal).opacity, 0);
    expect(revealOpacity(exitReveal).opacity, 0);
    expect(revealOpacity(volumeReveal).opacity, 0);
    expect(pointerCursor(), SystemMouseCursors.none);

    await hover.moveTo(tester.getCenter(viewersReveal));
    await tester.pump();
    expect(revealOpacity(labelReveal).opacity, 1);
    expect(revealOpacity(viewersReveal).opacity, 1);
    expect(revealOpacity(exitReveal).opacity, 1);
    expect(revealOpacity(volumeReveal).opacity, 1);
    expect(pointerCursor(), MouseCursor.defer);

    await tester.pump(const Duration(seconds: 2));
    await hover.moveTo(tester.getCenter(labelReveal));
    await tester.pump(const Duration(seconds: 2));
    expect(revealOpacity(labelReveal).opacity, 1);

    await tester.sendKeyDownEvent(LogicalKeyboardKey.escape);
    await tester.pump();
    await tester.sendKeyUpEvent(LogicalKeyboardKey.escape);

    expect(exitCount, 1);
    await hover.removePointer();
  });

  testWidgets(
    'full screen controls appear immediately and restart timeout on touch',
    (tester) async {
      liveVideoTrackRendererForTest = (track, fit, mirrorLocal) {
        return const ColoredBox(color: Colors.black);
      };
      addTearDown(resetLiveVideoTrackRendererForTest);
      final systemUiModes = <SystemUiMode>[];
      final systemUiController = FullScreenSystemUiController(
        platform: TargetPlatform.android,
        setEnabledSystemUIMode: (mode, {overlays}) async {
          systemUiModes.add(mode);
        },
      );

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: LiveFullScreenStage(
            track: _liveVideoTrack(
              identity: 'phabe',
              isScreenShare: true,
              isLocal: false,
            ),
            label: 'Phabe 的屏幕共享',
            screenShareViewers: [_currentUser.toSummary()],
            screenShareVolume: 0.75,
            onScreenShareVolumeChanged: (_) {},
            onScreenShareMuteToggled: () {},
            onExit: () {},
            systemUiController: systemUiController,
          ),
        ),
      );
      await tester.pump();
      expect(systemUiModes.last, SystemUiMode.manual);

      final labelReveal = find.byKey(
        const ValueKey<String>('live-fullscreen-stage:label-reveal'),
      );
      double opacity() => tester
          .widget<AnimatedOpacity>(
            find.descendant(
              of: labelReveal,
              matching: find.byType(AnimatedOpacity),
            ),
          )
          .opacity;

      await tester.pump(const Duration(seconds: 3));
      await tester.pump(const Duration(milliseconds: 180));
      expect(opacity(), 0);
      expect(systemUiModes.last, SystemUiMode.immersiveSticky);

      final touch = await tester.startGesture(
        tester.getCenter(find.byType(LiveFullScreenStage)),
        kind: PointerDeviceKind.touch,
      );
      await tester.pump();
      expect(opacity(), 1);
      expect(systemUiModes.last, SystemUiMode.manual);

      await tester.pump(const Duration(milliseconds: 2500));
      await touch.moveBy(const Offset(12, 8));
      await tester.pump();
      expect(opacity(), 1);

      await tester.pump(const Duration(milliseconds: 2500));
      expect(opacity(), 1);

      await touch.up();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 2900));
      expect(opacity(), 1);
      await tester.pump(const Duration(milliseconds: 100));
      expect(opacity(), 0);
      expect(systemUiModes.last, SystemUiMode.immersiveSticky);

      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump();
      expect(systemUiModes.last, SystemUiMode.manual);
    },
  );

  testWidgets('full screen camera label and exit fully auto-hide', (
    tester,
  ) async {
    liveVideoTrackRendererForTest = (track, fit, mirrorLocal) {
      return const ColoredBox(color: Colors.black);
    };
    addTearDown(resetLiveVideoTrackRendererForTest);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: LiveFullScreenStage(
          track: _liveVideoTrack(
            identity: 'phabe',
            isScreenShare: false,
            isLocal: false,
          ),
          label: 'Phabe 的摄像头',
          screenShareVolume: 0.75,
          onScreenShareVolumeChanged: (_) {},
          onScreenShareMuteToggled: () {},
          onExit: () {},
        ),
      ),
    );
    await tester.pump();

    final labelReveal = find.byKey(
      const ValueKey<String>('live-fullscreen-stage:label-reveal'),
    );
    final exitReveal = find.byKey(
      const ValueKey<String>('live-fullscreen-stage:exit-reveal'),
    );
    double opacity(Finder reveal) => tester
        .widget<AnimatedOpacity>(
          find.descendant(of: reveal, matching: find.byType(AnimatedOpacity)),
        )
        .opacity;

    expect(labelReveal, findsOneWidget);
    expect(exitReveal, findsOneWidget);
    expect(
      find.byKey(
        const ValueKey<String>('live-fullscreen-stage:screen-viewers-reveal'),
      ),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey<String>('live-fullscreen-stage:volume-reveal')),
      findsNothing,
    );

    await tester.pump(const Duration(seconds: 3));
    await tester.pump(const Duration(milliseconds: 180));
    expect(opacity(labelReveal), 0);
    expect(opacity(exitReveal), 0);
  });

  testWidgets('live member names use self and room role colors', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    final live = _liveState([
      _participant(
        id: 'live_self',
        user: _currentUser.toSummary().copyWith(
          roomDisplayName: 'Room Self',
          roomRole: 'member',
        ),
      ),
      _participant(
        id: 'live_member',
        user: _user('member', 'Room Member', roomRole: 'member'),
      ),
      _participant(
        id: 'live_admin',
        user: _user('admin', 'Room Admin', roomRole: 'admin'),
      ),
      _participant(
        id: 'live_owner',
        user: _user('owner', 'Room Owner', roomRole: 'owner'),
      ),
      _participant(
        id: 'live_superuser',
        user: _user(
          'superuser',
          'Room Root',
          roomRole: 'member',
          isSuperuser: true,
        ),
      ),
    ]);

    await tester.pumpWidget(
      _host(searchController: searchController, live: live, height: 620),
    );

    expect(
      tester.widget<Text>(find.text('Room Self')).style?.color,
      ui.UiColors.accent,
    );
    expect(
      tester.widget<Text>(find.text('Room Member')).style?.color,
      ui.UiColors.roleMember,
    );
    expect(
      tester.widget<Text>(find.text('Room Admin')).style?.color,
      ui.UiColors.roleAdmin,
    );
    expect(
      tester.widget<Text>(find.text('Room Owner')).style?.color,
      ui.UiColors.roleCreator,
    );
    expect(
      tester.widget<Text>(find.text('Room Root')).style?.color,
      ui.UiColors.roleSuperuser,
    );
  });

  testWidgets('live buttons show hover info below their targets', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);
    final live = _liveState([
      _participant(
        id: 'live_self',
        user: _currentUser.toSummary().copyWith(
          roomDisplayName: 'Room Me',
          roomRole: 'member',
        ),
      ),
    ]);

    await tester.pumpWidget(
      _host(searchController: searchController, live: live),
    );

    _expectBelowTooltip(tester, '关闭麦克风');
    _expectBelowTooltip(tester, '关闭耳机');
    _expectBelowTooltip(tester, '开启摄像头');

    final hover = await tester.createGesture(kind: PointerDeviceKind.mouse);
    final cameraControl = find.byKey(
      const ValueKey<String>('live-control:camera'),
    );
    await hover.addPointer(location: tester.getCenter(cameraControl));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));

    final cameraInfoText = find.text('开启摄像头');
    expect(cameraInfoText, findsOneWidget);
    expect(
      tester.getRect(cameraInfoText).top,
      greaterThan(tester.getRect(cameraControl).bottom),
    );

    await hover.removePointer();
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
  });

  testWidgets('music box keeps one height and rises above wrapped live controls', (
    tester,
  ) async {
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);

    final cases =
        <({String label, TargetPlatform platform, bool compact, double width})>[
          (
            label: 'Android compact',
            platform: TargetPlatform.android,
            compact: true,
            width: 360,
          ),
          (
            label: 'Windows compact',
            platform: TargetPlatform.windows,
            compact: true,
            width: 360,
          ),
          (
            label: 'Windows wide',
            platform: TargetPlatform.windows,
            compact: false,
            width: 960,
          ),
          (
            label: 'macOS wide',
            platform: TargetPlatform.macOS,
            compact: false,
            width: 960,
          ),
        ];

    double? stablePanelHeight;
    Rect? androidCompactPanel;
    Rect? windowsWidePanel;
    var closeTaps = 0;
    for (final testCase in cases) {
      await tester.binding.setSurfaceSize(Size(testCase.width, 740));
      await tester.pumpWidget(
        HomeAdaptiveLayout(
          compact: testCase.compact,
          child: _host(
            searchController: searchController,
            live: _liveState(const []),
            width: testCase.width,
            height: 740,
            platform: testCase.platform,
            musicBox: _emptyMusicBoxState,
            musicBoxOpen: true,
            onToggleMusicBox: () => closeTaps += 1,
          ),
        ),
      );
      await tester.pump();

      final panel = find.byKey(const ValueKey<String>('live-music-box-panel'));
      final inlinePlayer = find.byKey(
        const ValueKey<String>('live-control:music-inline'),
      );
      final musicPanelToggle = find.byKey(
        const ValueKey<String>('live-control:music-queue'),
      );
      expect(panel, findsOneWidget);
      expect(inlinePlayer, findsOneWidget);
      expect(
        find.descendant(
          of: musicPanelToggle,
          matching: find.byIcon(Icons.library_music),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: musicPanelToggle,
          matching: find.byIcon(Icons.queue_music),
        ),
        findsNothing,
      );
      final panelRect = tester.getRect(panel);
      stablePanelHeight ??= panelRect.height;
      expect(
        panelRect.height,
        closeTo(stablePanelHeight, 0.01),
        reason: '${testCase.label} must keep the standard music box height',
      );
      expect(
        panelRect.height,
        greaterThanOrEqualTo(400),
        reason:
            '${testCase.label} music box should extend upward instead of scrolling',
      );
      expect(
        panelRect.top,
        greaterThanOrEqualTo(0),
        reason: '${testCase.label} must keep the raised panel interactive',
      );
      expect(
        panelRect.bottom,
        lessThanOrEqualTo(tester.getRect(inlinePlayer).top),
        reason: '${testCase.label} music box must not overlap its player',
      );
      if (testCase.label == 'Android compact') {
        androidCompactPanel = panelRect;
        await tester.tap(
          find.descendant(of: panel, matching: find.byIcon(Icons.close)),
        );
        await tester.pump();
        expect(closeTaps, 1);
      } else if (testCase.label == 'Windows wide') {
        windowsWidePanel = panelRect;
      }
      expect(tester.takeException(), isNull);
    }
    expect(androidCompactPanel, isNotNull);
    expect(windowsWidePanel, isNotNull);
    expect(
      androidCompactPanel!.top,
      lessThan(windowsWidePanel!.top),
      reason:
          'wrapped compact controls should move the fixed-height panel upward',
    );

    await tester.binding.setSurfaceSize(const Size(320, 740));
    await tester.pumpWidget(
      HomeAdaptiveLayout(
        compact: true,
        child: _host(
          searchController: searchController,
          live: _liveState(const []),
          width: 320,
          height: 740,
          platform: TargetPlatform.android,
          musicBox: _emptyMusicBoxState,
          musicBoxOpen: true,
        ),
      ),
    );
    await tester.pump();

    final limitedPanel = tester.getRect(
      find.byKey(const ValueKey<String>('live-music-box-panel')),
    );
    expect(limitedPanel.top, closeTo(0, 0.01));
    expect(
      limitedPanel.height,
      lessThan(stablePanelHeight!),
      reason: 'the panel should shrink only after it reaches the screen top',
    );
    expect(
      limitedPanel.bottom,
      lessThanOrEqualTo(
        tester
            .getRect(
              find.byKey(const ValueKey<String>('live-control:music-inline')),
            )
            .top,
      ),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('collapsed music panel keeps the music box toggle icon', (
    tester,
  ) async {
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: _liveState(const []),
        musicBox: _emptyMusicBoxState,
      ),
    );

    final musicPanelToggle = find.byKey(
      const ValueKey<String>('live-control:music-queue'),
    );
    expect(
      find.descendant(
        of: musicPanelToggle,
        matching: find.byIcon(Icons.library_music),
      ),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'inline music title moves when constrained and opens the existing song card',
    (tester) async {
      const longTitle = '一首需要在迷你播放模块中左右往返显示完整内容的特别长歌曲名称';
      final searchController = TextEditingController();
      addTearDown(searchController.dispose);
      const item = MusicBoxQueueItem(
        id: 'inline-current',
        source: 'netease',
        trackId: 'inline-track',
        title: longTitle,
        artist: '测试歌手',
        durationMs: 200000,
        status: MusicBoxQueueItemStatus.ready,
        fileSizeBytes: 0,
        error: '',
        addedByUserId: 'current_user',
        createdAt: null,
      );
      const musicBox = MusicBoxState(
        enabled: true,
        playback: MusicBoxPlayback(
          state: MusicBoxPlaybackState.playing,
          currentItemId: 'inline-current',
          positionMs: 30000,
          volume: 100,
          updatedAt: null,
        ),
        queue: [item],
        usage: MusicBoxUsage(usedBytes: 0, limitBytes: 200 * 1024 * 1024),
      );

      await tester.pumpWidget(
        _host(
          searchController: searchController,
          live: _liveState(const []),
          width: 960,
          musicBox: musicBox,
        ),
      );
      await tester.pump();

      final track = find.byKey(
        const ValueKey<String>('live-control:music-title-marquee-track'),
      );
      expect(track, findsOneWidget);
      await tester.pump(const Duration(milliseconds: 900));
      await tester.pump(const Duration(milliseconds: 400));
      expect(tester.widget<Transform>(track).transform[12], lessThan(0));

      await tester.tap(
        find.byKey(const ValueKey<String>('live-control:music-current-card')),
      );
      await tester.pump();
      await tester.pump();
      expect(
        find.byKey(
          const ValueKey<String>('music-box-song-card:inline-current'),
        ),
        findsOneWidget,
      );
      expect(find.text(longTitle), findsWidgets);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('Android docked music box search keeps focus when IME opens', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(360, 740);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetViewInsets);
    final searchController = TextEditingController();
    addTearDown(searchController.dispose);

    await tester.pumpWidget(
      _host(
        searchController: searchController,
        live: _liveState(const []),
        width: 360,
        height: 740,
        platform: TargetPlatform.android,
        musicBox: _emptyMusicBoxState,
        musicBoxOpen: true,
        resizeToAvoidBottomInset: false,
        preserveKeyboardViewport: true,
      ),
    );
    await tester.pump();

    await tester.tap(
      find.byKey(const ValueKey<String>('music-box-search-toggle')),
    );
    await tester.pump();

    final search = find.descendant(
      of: find.byKey(const ValueKey<String>('music-box-search-input')),
      matching: find.byType(TextField),
    );
    expect(search, findsOneWidget);
    await tester.tap(search);
    await tester.pump();
    expect(tester.widget<TextField>(search).focusNode!.hasFocus, isTrue);

    tester.view.viewInsets = const FakeViewPadding(bottom: 300);
    await tester.pump();

    expect(tester.widget<TextField>(search).focusNode!.hasFocus, isTrue);
    expect(tester.testTextInput.isVisible, isTrue);
    expect(tester.takeException(), isNull);
  });
}

void _expectBelowTooltip(WidgetTester tester, String message) {
  expect(find.byTooltip(message), findsOneWidget);
  final tooltip = find.byWidgetPredicate(
    (widget) => widget is Tooltip && widget.message == message,
  );
  expect(tooltip, findsOneWidget);
  expect(tester.widget<Tooltip>(tooltip).preferBelow, isTrue);
  expect(tester.widget<Tooltip>(tooltip).verticalOffset, 24);
}

const _currentUser = CurrentUser(
  id: 'current_user',
  uid: '1000000',
  username: 'me',
  displayName: 'Me',
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

Widget _host({
  required TextEditingController searchController,
  required LiveState live,
  Set<String> speakingUserIds = const {},
  Set<String> connectedParticipantIds = const {},
  Map<String, bool> liveKitMicMutedByParticipantId = const {},
  double width = 720,
  double height = 520,
  bool loading = false,
  bool joined = true,
  bool joining = false,
  bool leaving = false,
  bool micMuted = false,
  bool headphonesMuted = false,
  TargetPlatform? platform,
  VoidCallback? onJoin,
  VoidCallback? onToggleMic,
  VoidCallback? onToggleHeadphones,
  VoidCallback? onToggleCamera,
  Future<bool> Function()? onFlipCamera,
  Future<bool> Function(bool mirrored)? onSetLocalCameraMirrored,
  VoidCallback? onToggleShare,
  bool screenShareSupported = true,
  ValueChanged<LiveStageSelection?>? onStageSelectionChanged,
  ValueChanged<LiveVideoTrack>? onEnterFullScreen,
  LiveStageSelection? stageSelection,
  List<LiveVideoTrack> videoTracks = const [],
  bool suspendStageVideo = false,
  MusicBoxState? musicBox,
  bool musicBoxOpen = false,
  VoidCallback? onToggleMusicBox,
  bool resizeToAvoidBottomInset = true,
  bool preserveKeyboardViewport = false,
  double screenShareVolume = 1,
  ValueChanged<double>? onScreenShareVolumeChanged,
  VoidCallback? onScreenShareMuteToggled,
  double Function(String userId)? participantVoiceVolume,
  void Function(String userId, double volume)? onParticipantVoiceVolumeChanged,
  ValueChanged<String>? onParticipantVoiceMuteToggled,
  bool Function(LiveParticipant participant)? canModerateParticipant,
  ValueChanged<LiveParticipant>? onToggleParticipantMicModeration,
  ValueChanged<LiveParticipant>? onToggleParticipantHeadphonesModeration,
  bool Function(LiveParticipant participant)? canRemoveParticipant,
  ValueChanged<LiveParticipant>? onRemoveParticipant,
  Future<UserSummary> Function(UserSummary user)? onResolveParticipantProfile,
}) {
  return MaterialApp(
    theme: ui.uiTheme().copyWith(platform: platform),
    home: AppConfigScope(
      config: const AppConfig(
        apiBaseUrl: 'https://api.test/api/v1',
        assetBaseUrl: 'https://assets.test',
        releaseBucketUrl: 'https://releases.test/gang-chat',
      ),
      child: Scaffold(
        resizeToAvoidBottomInset: resizeToAvoidBottomInset,
        body: HomeKeyboardOverlayViewport(
          enabled: preserveKeyboardViewport,
          child: SizedBox(
            width: width,
            height: height,
            child: LiveChannelPane(
              title: 'Test room',
              avatarUrl: null,
              live: live,
              currentUser: _currentUser,
              loading: loading,
              joined: joined,
              joining: joining,
              leaving: leaving,
              micMuted: micMuted,
              headphonesMuted: headphonesMuted,
              voiceBlocked: false,
              cameraOn: false,
              screenSharing: false,
              speakingUserIds: speakingUserIds,
              connectedParticipantIds: connectedParticipantIds,
              liveKitMicMutedByParticipantId: liveKitMicMutedByParticipantId,
              videoTracks: videoTracks,
              stageSelection: stageSelection ?? const LiveStageSelection.none(),
              suspendStageVideo: suspendStageVideo,
              onStageSelectionChanged: onStageSelectionChanged ?? (_) {},
              onEnterFullScreen: onEnterFullScreen ?? (_) {},
              onBackToChat: () {},
              onJoin: onJoin ?? () {},
              onLeave: () {},
              onToggleMic: onToggleMic ?? () {},
              onToggleHeadphones: onToggleHeadphones ?? () {},
              onToggleCamera: onToggleCamera ?? () {},
              onFlipCamera: onFlipCamera,
              onSetLocalCameraMirrored:
                  onSetLocalCameraMirrored ?? (_) async => true,
              onToggleShare: screenShareSupported
                  ? onToggleShare ?? () {}
                  : null,
              musicBox: musicBox,
              musicBoxOpen: musicBoxOpen,
              musicBoxSearchController: searchController,
              musicBoxSearchResults: const [],
              musicBoxSearching: false,
              musicBoxSearchError: null,
              musicBoxSource: 'netease',
              onToggleMusicBox: onToggleMusicBox ?? () {},
              onMusicBoxTogglePlayback: () {},
              onMusicBoxSkip: () {},
              onMusicBoxQueueResult: (_) {},
              onMusicBoxRemoveItem: (_) {},
              onMusicBoxSourceChanged: (_) {},
              inputVolume: 1,
              outputVolume: 1,
              musicBoxVolume: 1,
              screenShareVolume: screenShareVolume,
              onInputVolumeChanged: (_) {},
              onOutputVolumeChanged: (_) {},
              onMusicBoxVolumeChanged: (_) {},
              onScreenShareVolumeChanged: onScreenShareVolumeChanged ?? (_) {},
              onScreenShareMuteToggled: onScreenShareMuteToggled ?? () {},
              participantVoiceVolume: participantVoiceVolume ?? ((_) => 1),
              onParticipantVoiceVolumeChanged:
                  onParticipantVoiceVolumeChanged ?? ((_, _) {}),
              onParticipantVoiceMuteToggled:
                  onParticipantVoiceMuteToggled ?? (_) {},
              canModerateParticipant: canModerateParticipant ?? ((_) => false),
              onToggleParticipantMicModeration:
                  onToggleParticipantMicModeration ?? (_) {},
              onToggleParticipantHeadphonesModeration:
                  onToggleParticipantHeadphonesModeration ?? (_) {},
              canRemoveParticipant: canRemoveParticipant ?? ((_) => false),
              onRemoveParticipant: onRemoveParticipant ?? ((_) {}),
              onResolveParticipantProfile: onResolveParticipantProfile,
            ),
          ),
        ),
      ),
    ),
  );
}

const _emptyMusicBoxState = MusicBoxState(
  enabled: true,
  playback: MusicBoxPlayback(
    state: MusicBoxPlaybackState.stopped,
    currentItemId: '',
    positionMs: 0,
    volume: 100,
    updatedAt: null,
  ),
  queue: <MusicBoxQueueItem>[],
  usage: MusicBoxUsage(usedBytes: 0, limitBytes: 200 * 1024 * 1024),
);

void _expectMediaMemberCard(
  WidgetTester tester, {
  required IconData activityIcon,
  String? stoppedThumbnailKey,
}) {
  final cardFinder = find.ancestor(
    of: find.text('Room Me'),
    matching: find.byType(ui.PressableSurface),
  );
  final card = tester.widget<ui.PressableSurface>(cardFinder);
  final cardRect = tester.getRect(cardFinder);
  final micButtonRect = tester.getRect(
    find.byKey(const ValueKey<String>('live-member-status:mic:current_user')),
  );
  final headphonesButtonRect = tester.getRect(
    find.byKey(
      const ValueKey<String>('live-member-status:headphones:current_user'),
    ),
  );
  final cameraButtonFinder = find.byKey(
    const ValueKey<String>('live-member-status:camera:current_user'),
  );
  final shareButtonFinder = find.byKey(
    const ValueKey<String>('live-member-status:screen-share:current_user'),
  );
  final voiceVolumeButtonFinder = find.byKey(
    const ValueKey<String>('live-member-status:voice-volume:current_user'),
  );
  final kickButtonFinder = find.byKey(
    const ValueKey<String>('live-member-status:kick:current_user'),
  );
  final nameFinder = find.descendant(
    of: cardFinder,
    matching: find.text('Room Me'),
  );
  final nameRect = tester.getRect(nameFinder);
  final activityTag = find.descendant(
    of: cardFinder,
    matching: find.byKey(
      const ValueKey<String>('live-member-activity:current_user'),
    ),
  );
  final tagRect = tester.getRect(activityTag);
  final liveVideoFinder = find.descendant(
    of: cardFinder,
    matching: find.byType(LiveVideoTrackView),
  );
  final Finder? stoppedThumbnailFinder = stoppedThumbnailKey == null
      ? null
      : find.descendant(
          of: cardFinder,
          matching: find.byKey(ValueKey<String>(stoppedThumbnailKey)),
        );
  final previewRect = stoppedThumbnailKey == null
      ? tester.getRect(liveVideoFinder)
      : tester.getRect(stoppedThumbnailFinder!);

  final scale = cardRect.width / card.height;
  expect(cardRect.height, closeTo(162 * scale, 0.01));
  expect(
    liveVideoFinder,
    stoppedThumbnailKey == null ? findsOneWidget : findsNothing,
  );
  if (stoppedThumbnailFinder != null) {
    expect(stoppedThumbnailFinder, findsOneWidget);
  }
  expect(
    find.descendant(of: cardFinder, matching: find.byType(ui.Avatar)),
    findsNothing,
  );
  expect(nameFinder, findsOneWidget);
  expect(activityTag, findsOneWidget);
  expect(
    find.descendant(of: activityTag, matching: find.byIcon(activityIcon)),
    findsOneWidget,
  );
  expect(
    find.descendant(of: activityTag, matching: find.byType(DecoratedBox)),
    findsNothing,
  );
  expect(cameraButtonFinder, findsNothing);
  expect(shareButtonFinder, findsNothing);
  expect(voiceVolumeButtonFinder, findsNothing);
  expect(kickButtonFinder, findsNothing);
  expect(nameRect.left, lessThan(tagRect.left));
  expect(nameRect.top, lessThan(previewRect.top));
  expect(nameRect.bottom, lessThan(micButtonRect.top));
  expect(tagRect.top, lessThan(micButtonRect.top));
  expect(tagRect.right, lessThanOrEqualTo(cardRect.right));
  expect(micButtonRect.right, closeTo(headphonesButtonRect.left, 0.01));
  expect(cardRect.bottom - headphonesButtonRect.bottom, lessThan(14));
}

LiveState _liveState(List<LiveParticipant> participants) {
  return LiveState(
    roomId: 'room_1',
    participantCount: participants.length,
    participants: participants,
    updatedAt: DateTime.utc(2026, 6, 11, 9),
  );
}

LiveParticipant _participant({
  required String id,
  required UserSummary user,
  bool micMuted = false,
  bool micBlocked = false,
  bool headphonesMuted = false,
  bool headphonesBlocked = false,
  bool voiceBlocked = false,
  bool cameraOn = false,
  bool cameraMirrored = false,
  bool screenSharing = false,
  List<UserSummary> screenViewers = const <UserSummary>[],
  String connectionState = 'connected',
}) {
  return LiveParticipant(
    liveSessionId: id,
    user: user,
    joinedAt: DateTime.utc(2026, 6, 11, 9),
    micMuted: micMuted,
    micBlocked: micBlocked,
    headphonesMuted: headphonesMuted,
    headphonesBlocked: headphonesBlocked,
    headphonesListening: !headphonesMuted && !headphonesBlocked,
    voiceBlocked: voiceBlocked,
    cameraOn: cameraOn,
    cameraMirrored: cameraMirrored,
    screenSharing: screenSharing,
    screenViewers: screenViewers,
    connectionState: connectionState,
  );
}

UserSummary _user(
  String id,
  String name, {
  required String roomRole,
  bool isSuperuser = false,
}) {
  return UserSummary(
    id: id,
    username: id,
    displayName: name,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
    roomDisplayName: name,
    roomRole: roomRole,
    isSuperuser: isSuperuser,
  );
}

LiveVideoTrack _liveVideoTrack({
  required String identity,
  required bool isScreenShare,
  required bool isLocal,
}) {
  return LiveVideoTrack(
    identity: identity,
    track: _FakeVideoTrack(),
    isScreenShare: isScreenShare,
    isLocal: isLocal,
  );
}

class _FakeVideoTrack implements lk.VideoTrack {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}
