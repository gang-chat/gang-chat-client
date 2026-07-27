import 'dart:ui' show PointerDeviceKind;

import 'package:client/src/config/app_config.dart';
import 'package:client/src/home/live_channel_pane.dart';
import 'package:client/src/live/live_session.dart';
import 'package:client/src/live/live_video_track_view.dart';
import 'package:client/src/protocol/models.dart';
import 'package:client/src/ui/app_config_scope.dart';
import 'package:client/src/ui/ui.dart' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show LogicalKeyboardKey;
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

      await tester.pumpWidget(
        _host(
          searchController: searchController,
          live: live,
          height: 600,
          platform: TargetPlatform.android,
          participantVoiceVolume: (_) => 0.4,
          onParticipantVoiceVolumeChanged: (_, _) {},
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

      await tester.longPress(volumeButton);
      await tester.pump();

      expect(volumeSlider, findsOneWidget);
      expect(volumeToggles, isEmpty);
      expect(find.byTooltip('静音Phabe音量'), findsNothing);

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
            live: live,
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

  testWidgets('local screen share stage only shows the exit control', (
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
      ),
    );
    await tester.pump();

    final exitButton = find.byKey(const ValueKey<String>('live-stage:exit'));
    expect(exitButton, findsOneWidget);
    expect(
      find.byKey(const ValueKey<String>('live-stage:fullscreen')),
      findsNothing,
    );
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
    final exitRect = tester.getRect(exitButton);
    expect(exitRect.right, closeTo(rendererRect.right - 8, 0.01));
  });

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

    final hover = await tester.createGesture(kind: PointerDeviceKind.mouse);
    await hover.addPointer(location: tester.getCenter(labelReveal));
    await tester.pump(const Duration(seconds: 3));
    await tester.pump(const Duration(milliseconds: 180));
    expect(revealOpacity(labelReveal).opacity, 0);
    expect(revealOpacity(viewersReveal).opacity, 0);
    expect(revealOpacity(exitReveal).opacity, 0);
    expect(revealOpacity(volumeReveal).opacity, 0);

    await hover.moveTo(tester.getCenter(viewersReveal));
    await tester.pump();
    expect(revealOpacity(labelReveal).opacity, 1);
    expect(revealOpacity(viewersReveal).opacity, 1);
    expect(revealOpacity(exitReveal).opacity, 1);
    expect(revealOpacity(volumeReveal).opacity, 1);

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
          ),
        ),
      );
      await tester.pump();

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

      final touch = await tester.startGesture(
        tester.getCenter(find.byType(LiveFullScreenStage)),
        kind: PointerDeviceKind.touch,
      );
      await tester.pump();
      expect(opacity(), 1);

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
  bool joined = true,
  bool joining = false,
  bool micMuted = false,
  bool headphonesMuted = false,
  TargetPlatform? platform,
  VoidCallback? onJoin,
  VoidCallback? onToggleMic,
  VoidCallback? onToggleHeadphones,
  VoidCallback? onToggleCamera,
  VoidCallback? onToggleShare,
  bool screenShareSupported = true,
  ValueChanged<LiveStageSelection?>? onStageSelectionChanged,
  LiveStageSelection? stageSelection,
  List<LiveVideoTrack> videoTracks = const [],
  bool suspendStageVideo = false,
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
        body: SizedBox(
          width: width,
          height: height,
          child: LiveChannelPane(
            title: 'Test room',
            avatarUrl: null,
            live: live,
            currentUser: _currentUser,
            loading: false,
            joined: joined,
            joining: joining,
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
            onEnterFullScreen: (_) {},
            onBackToChat: () {},
            onJoin: onJoin ?? () {},
            onLeave: () {},
            onToggleMic: onToggleMic ?? () {},
            onToggleHeadphones: onToggleHeadphones ?? () {},
            onToggleCamera: onToggleCamera ?? () {},
            onToggleShare: screenShareSupported ? onToggleShare ?? () {} : null,
            musicBox: null,
            musicBoxOpen: false,
            musicBoxSearchController: searchController,
            musicBoxSearchResults: const [],
            musicBoxSearching: false,
            musicBoxSearchError: null,
            musicBoxSource: 'netease',
            onToggleMusicBox: () {},
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
  );
}

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

  expect(card.height, closeTo(cardRect.width, 0.01));
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
