part of '../gang_app_shell_test.dart';

void registerShellRealtimeLiveWidgetTests() {
  testWidgets('narrow close prompt stacks behavior choices', (
    WidgetTester tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(360, 740);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);

    final windowController = _RecordingWindowController(<String>[]);
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
        home: HomePage(
          app: _homeTestAppContext(),
          realtime: _NoopRealtimeService(),
          closeBehaviorStore: const _FixedCloseBehaviorStore(
            CloseBehavior.askEveryTime,
          ),
          windowController: windowController,
        ),
      ),
    );
    await tester.pumpAndSettle();

    final handler = windowController.closeRequestHandler;
    expect(handler, isNotNull);
    final closeFuture = handler!();
    await tester.pumpAndSettle();

    final minimize = find.byKey(
      const ValueKey('close-behavior-minimize-button'),
    );
    final exit = find.byKey(const ValueKey('close-behavior-exit-button'));
    expect(tester.getRect(minimize).bottom, lessThan(tester.getRect(exit).top));
    for (final label in ['最小化到托盘', '退出程序']) {
      expect(
        tester
            .renderObject<RenderParagraph>(find.text(label))
            .didExceedMaxLines,
        isFalse,
      );
    }

    await tester.tap(find.widgetWithText(ui.Button, '取消'));
    await tester.pumpAndSettle();
    expect(await closeFuture, isTrue);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'only realtime all-policy message updates play and request attention',
    (WidgetTester tester) async {
      final realtime = _FakeRealtimeService();
      final sound = _RecordingMessageNotificationSoundPlayer();
      final windowEvents = <String>[];
      final windowController = _RecordingWindowController(windowEvents);

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: HomePage(
            app: _homeTestAppContext(alphaRoomUnreadCount: 7),
            realtime: realtime,
            messageNotificationSoundPlayer: sound,
            windowController: windowController,
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Loading pre-existing unread messages on sign-in never produces a cue.
      expect(sound.volumes, isEmpty);
      expect(windowEvents, isEmpty);

      Map<String, Object?> update({
        required String messageId,
        String notificationPolicy = 'all',
        String? reason = 'message_created',
      }) {
        return {
          ..._roomCardJson(
            id: 'server-alpha',
            name: 'Alpha Room',
            memberCount: 2,
            unreadCount: 8,
          ),
          'notification_policy': notificationPolicy,
          'last_message': {
            'id': messageId,
            'type': 'text',
            'sender_display_name': 'Morgan',
            'body_preview': 'Realtime message',
            'created_at': '2026-06-12T02:00:00Z',
          },
          'update_reason': ?reason,
          'updated_at': '2026-06-12T02:00:00Z',
        };
      }

      realtime.add(
        RealtimeEvent(
          type: 'room_updated',
          data: update(messageId: 'message-live-1'),
        ),
      );
      await tester.pumpAndSettle();

      expect(sound.volumes, hasLength(1));
      expect(sound.volumes.single, greaterThan(0));
      expect(windowEvents, ['message-attention']);

      // A duplicate delivery, a reconnect/history-style room snapshot and a
      // room outside the "all" policy must all remain silent.
      realtime.add(
        RealtimeEvent(
          type: 'room_updated',
          data: update(messageId: 'message-live-1'),
        ),
      );
      realtime.add(
        RealtimeEvent(
          type: 'room_updated',
          data: update(messageId: 'message-history', reason: null),
        ),
      );
      realtime.add(
        RealtimeEvent(
          type: 'room_updated',
          data: update(
            messageId: 'message-silent',
            notificationPolicy: 'silent',
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(sound.volumes, hasLength(1));
      expect(windowEvents, ['message-attention']);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('account suspension event immediately logs out every client', (
    WidgetTester tester,
  ) async {
    final realtime = _FakeRealtimeService();
    final liveSession = _FakeLiveSession();
    final liveSessionController = _FakeLiveSessionController(
      session: liveSession,
    );
    var logoutCalls = 0;

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(onLogout: () async => logoutCalls += 1),
          liveSessionController: liveSessionController,
          realtime: realtime,
        ),
      ),
    );
    await tester.pumpAndSettle();

    realtime.add(
      const RealtimeEvent(
        type: 'account_suspended',
        data: {'reason': '账号已被封禁'},
      ),
    );
    realtime.add(
      const RealtimeEvent(
        type: 'account_suspended',
        data: {'reason': '账号已被封禁'},
      ),
    );
    await tester.pumpAndSettle();

    expect(logoutCalls, 1);
    expect(liveSession.disconnects, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('authenticated home shell applies realtime live snapshots', (
    WidgetTester tester,
  ) async {
    final realtime = _FakeRealtimeService();

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(app: _homeTestAppContext(), realtime: realtime),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();
    final chatLiveButton = find.byKey(
      const ValueKey('chat-header-live-button'),
    );
    expect(chatLiveButton, findsOneWidget);
    expect(
      tester.widget<ui.PressableSurface>(chatLiveButton).tooltip,
      isNotNull,
    );
    expect(
      find.descendant(
        of: chatLiveButton,
        matching: find.byKey(const ValueKey('chat-header-room-title')),
      ),
      findsOneWidget,
    );
    final roomTitle = tester.widget<Text>(
      find.byKey(const ValueKey('chat-header-room-title')),
    );
    expect(roomTitle.data, 'Alpha Room');
    expect(roomTitle.style?.color, ui.UiColors.text);
    expect(
      tester
          .widget<Text>(find.byKey(const ValueKey('chat-header-room-meta')))
          .data,
      '2 名成员 · 1 人在线',
    );
    expect(
      find.descendant(of: chatLiveButton, matching: find.text('进入语音频道')),
      findsNothing,
    );

    await _openLiveChannelFromHeader(tester);

    expect(find.text('Riley'), findsNothing);
    expect(find.text('2 名成员 · 1 人语音'), findsOneWidget);

    realtime.add(
      const RealtimeEvent(
        type: 'live_participant_joined',
        data: {
          'room_id': 'server-alpha',
          'participant_count': 2,
          'preview': <Object?>[],
          'live': {
            'room_id': 'server-alpha',
            'participant_count': 2,
            'participants': [
              {
                'live_session_id': 'live-session-morgan',
                'user': {
                  'id': 'user-2',
                  'username': 'morgan',
                  'display_name': 'Morgan',
                  'avatar_url': null,
                  'default_avatar_key': 'blue-3',
                },
                'joined_at': '2026-06-05T08:00:00Z',
                'mic_muted': true,
                'headphones_muted': false,
                'voice_blocked': false,
                'camera_on': false,
                'screen_sharing': false,
                'connection_state': 'connected',
              },
              {
                'live_session_id': 'live-session-riley',
                'user': {
                  'id': 'user-3',
                  'username': 'riley',
                  'display_name': 'Riley',
                  'avatar_url': null,
                  'default_avatar_key': 'green-2',
                },
                'joined_at': '2026-06-05T08:00:00Z',
                'mic_muted': false,
                'headphones_muted': false,
                'voice_blocked': false,
                'camera_on': false,
                'screen_sharing': false,
                'connection_state': 'connected',
              },
            ],
            'updated_at': '2026-06-05T08:00:00Z',
          },
        },
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Riley'), findsOneWidget);
    expect(find.text('2 名成员 · 2 人语音'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Android joins voice when opening a remote screen share', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(420, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final realtime = _FakeRealtimeService();
    final liveJoinRequests = <Map<String, Object?>>[];
    final liveStateUpdates = <Map<String, Object?>>[];
    final liveScreenViewUpdates = <String?>[];
    final liveOperationLog = <String>[];
    final liveSession = _FakeLiveSession();
    liveSession.testVideoTracks = [
      _liveVideoTrack(identity: 'user-2', isScreenShare: true, isLocal: false),
    ];
    liveVideoTrackRendererForTest = (track, fit, mirrored) {
      return const ColoredBox(color: Colors.black);
    };
    addTearDown(resetLiveVideoTrackRendererForTest);
    final liveSessionController = _FakeLiveSessionController(
      session: liveSession,
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
        home: HomePage(
          app: _homeTestAppContext(
            liveJoinRequests: liveJoinRequests,
            liveStateUpdates: liveStateUpdates,
            liveScreenViewUpdates: liveScreenViewUpdates,
            liveOperationLog: liveOperationLog,
          ),
          liveSessionController: liveSessionController,
          realtime: realtime,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();
    await _openLiveChannelFromHeader(tester);

    realtime.add(
      RealtimeEvent(
        type: 'live_participant_updated',
        data: {
          'room_id': 'server-alpha',
          'participant_count': 1,
          'preview': <Object?>[],
          'live': _liveStateJson(
            roomId: 'server-alpha',
            participantCount: 1,
            participants: [
              _liveParticipantJson(
                user: _userJson(
                  id: 'user-2',
                  username: 'morgan',
                  displayName: 'Morgan',
                ),
                liveSessionId: 'live-session-morgan',
                micMuted: true,
                screenSharing: true,
              ),
            ],
          ),
        },
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey<String>('live-member:screen-share-thumbnail')),
    );
    await tester.pumpAndSettle();

    expect(liveSession.connectAttempts, 1);
    expect(liveJoinRequests.single['source'], 'live_panel');
    expect(liveSession.micMutes, contains(false));
    expect(liveStateUpdates.last['mic_muted'], false);
    expect(liveSession.watchedScreenShareIdentity, 'user-2');
    expect(liveScreenViewUpdates, ['user-2']);
    expect(liveOperationLog, ['join', 'state:online', 'screen-view:user-2']);

    final volumeButton = find.byKey(
      const ValueKey<String>('live-stage:screen-share-volume'),
    );
    expect(volumeButton, findsOneWidget);
    final initialScreenShareVolume = liveSession.screenShareVolume;
    expect(initialScreenShareVolume, greaterThan(0));
    await tester.tap(volumeButton);
    await tester.pump();
    expect(liveSession.screenShareVolumes.last, 0);
    expect(
      find.descendant(
        of: volumeButton,
        matching: find.byIcon(Icons.volume_off),
      ),
      findsOneWidget,
    );

    await tester.tap(volumeButton);
    await tester.pump();
    expect(liveSession.screenShareVolumes.last, initialScreenShareVolume);
    expect(
      find.descendant(
        of: volumeButton,
        matching: find.byWidgetPredicate(
          (widget) =>
              widget is Icon &&
              (widget.icon == Icons.volume_down ||
                  widget.icon == Icons.volume_up),
        ),
      ),
      findsOneWidget,
    );

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
    expect(liveSession.screenShareVolumes.last, closeTo(0, 0.02));
    expect(
      find.descendant(
        of: volumeButton,
        matching: find.byIcon(Icons.volume_off),
      ),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('leaving voice forgets the watched screen share before rejoin', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(420, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final realtime = _FakeRealtimeService();
    final liveScreenViewUpdates = <String?>[];
    final leaveStarted = Completer<void>();
    final releaseLeave = Completer<void>();
    final liveSession = _FakeLiveSession();
    liveSession.testVideoTracks = [
      _liveVideoTrack(identity: 'user-2', isScreenShare: true, isLocal: false),
    ];
    liveVideoTrackRendererForTest = (track, fit, mirrored) {
      return const ColoredBox(color: Colors.black);
    };
    addTearDown(resetLiveVideoTrackRendererForTest);

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
        home: HomePage(
          app: _homeTestAppContext(
            liveScreenViewUpdates: liveScreenViewUpdates,
            beforeLiveStateResponse: (update) async {
              if (update['connection_state'] != 'left') return;
              if (!leaveStarted.isCompleted) leaveStarted.complete();
              await releaseLeave.future;
            },
          ),
          liveSessionController: _FakeLiveSessionController(
            session: liveSession,
          ),
          realtime: realtime,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();
    await _openLiveChannelFromHeader(tester);
    realtime.add(
      RealtimeEvent(
        type: 'live_participant_updated',
        data: {
          'room_id': 'server-alpha',
          'participant_count': 1,
          'preview': <Object?>[],
          'live': _liveStateJson(
            roomId: 'server-alpha',
            participantCount: 1,
            participants: [
              _liveParticipantJson(
                user: _userJson(
                  id: 'user-2',
                  username: 'morgan',
                  displayName: 'Morgan',
                ),
                liveSessionId: 'live-session-morgan',
                micMuted: true,
                screenSharing: true,
              ),
            ],
          ),
        },
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey<String>('live-member:screen-share-thumbnail')),
    );
    await tester.pumpAndSettle();
    expect(liveSession.watchedScreenShareIdentity, 'user-2');
    expect(liveScreenViewUpdates, ['user-2']);

    await tester.tap(find.byKey(const ValueKey<String>('live-control:leave')));
    await tester.pump();
    await leaveStarted.future;

    final join = find.byKey(const ValueKey<String>('live-control:join'));
    final joinButton = tester.widget<ui.Button>(join);
    expect(joinButton.loading, isFalse);
    expect(joinButton.onPressed, isNull);
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

    releaseLeave.complete();
    await tester.pumpAndSettle();
    expect(liveSession.watchedScreenShareIdentity, isNull);
    expect(tester.widget<ui.Button>(join).onPressed, isNotNull);

    await tester.tap(join);
    await tester.pumpAndSettle();

    expect(liveSession.connectAttempts, 2);
    expect(liveSession.watchedScreenShareIdentity, isNull);
    expect(liveScreenViewUpdates, ['user-2', null]);
    expect(
      find.byKey(const ValueKey<String>('live-stage:screen-share-volume')),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('authoritative live snapshots reconcile local audio controls', (
    WidgetTester tester,
  ) async {
    final realtime = _FakeRealtimeService();
    final liveSession = _FakeLiveSession();
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
        home: HomePage(
          app: _homeTestAppContext(),
          liveSessionController: _FakeLiveSessionController(
            session: liveSession,
          ),
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
    await tester.tap(_liveControl('headphones'));
    await tester.pumpAndSettle();
    expect(liveSession.localMicMuted, isTrue);
    expect(liveSession.outputMutes.last, isTrue);

    realtime.add(
      RealtimeEvent(
        type: 'live_participant_updated',
        data: {
          'room_id': 'server-alpha',
          'participant_count': 2,
          'preview': <Object?>[],
          'live': {
            ..._liveStateJson(
              roomId: 'server-alpha',
              participantCount: 2,
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
                  micMuted: true,
                ),
              ],
            ),
            'updated_at': '2099-06-05T08:00:00Z',
          },
        },
      ),
    );
    await tester.pumpAndSettle();

    expect(liveSession.localMicMuted, isFalse);
    expect(liveSession.outputMutes.last, isFalse);
    final dock = find.byKey(const ValueKey<String>('home-title-live-room'));
    expect(
      find.descendant(of: dock, matching: find.byIcon(Icons.mic)),
      findsOneWidget,
    );
    expect(
      find.descendant(of: dock, matching: find.byIcon(Icons.headphones)),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'Android keeps live state muted when microphone publication fails',
    (WidgetTester tester) async {
      tester.view.physicalSize = const Size(420, 740);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final realtime = _FakeRealtimeService();
      final liveStateUpdates = <Map<String, Object?>>[];
      final liveSession = _FakeLiveSession(failMicUnmute: true);
      final liveSessionController = _FakeLiveSessionController(
        session: liveSession,
      );

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
          home: HomePage(
            app: _homeTestAppContext(liveStateUpdates: liveStateUpdates),
            liveSessionController: liveSessionController,
            realtime: realtime,
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Alpha Room'));
      await tester.pumpAndSettle();
      await _openLiveChannelFromHeader(tester);
      await tester.tap(find.text('加入'));
      await tester.pumpAndSettle();

      expect(liveSession.micMutes, contains(false));
      expect(liveSession.localMicMuted, isTrue);
      expect(liveStateUpdates.last['mic_muted'], true);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('chat header live preview follows realtime live snapshots', (
    WidgetTester tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(1180, 620);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);
    final realtime = _FakeRealtimeService();

    Map<String, Object?> participant({
      required String id,
      required String username,
      required String displayName,
      required String liveSessionId,
    }) {
      return _liveParticipantJson(
        user: _userJson(id: id, username: username, displayName: displayName),
        liveSessionId: liveSessionId,
      );
    }

    RealtimeEvent liveSnapshotEvent(List<Object?> participants) {
      return RealtimeEvent(
        type: 'live_participant_joined',
        data: {
          'room_id': 'server-alpha',
          'participant_count': participants.length,
          'preview': <Object?>[],
          'live': _liveStateJson(
            roomId: 'server-alpha',
            participantCount: participants.length,
            participants: participants,
          ),
        },
      );
    }

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: SizedBox(
          width: 1180,
          height: 620,
          child: HomePage(app: _homeTestAppContext(), realtime: realtime),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();

    final chatLiveButton = find.byKey(
      const ValueKey('chat-header-live-button'),
    );
    final livePreview = find.byKey(const ValueKey('chat-header-live-preview'));
    final livePreviewIcon = find.byKey(
      const ValueKey('chat-header-live-preview-icon'),
    );
    final livePreviewCount = find.byKey(
      const ValueKey('chat-header-live-preview-count'),
    );
    expect(chatLiveButton, findsOneWidget);
    expect(livePreview, findsOneWidget);
    expect(livePreviewIcon, findsOneWidget);
    expect(
      tester.getRect(chatLiveButton).right - tester.getRect(livePreview).right,
      closeTo(10, 1),
    );
    expect(
      find.descendant(of: livePreview, matching: find.byType(ui.Avatar)),
      findsOneWidget,
    );
    expect(
      tester.getRect(livePreviewIcon).right,
      lessThan(tester.getRect(livePreviewCount).left),
    );
    expect(tester.widget<Icon>(livePreviewIcon).color, ui.UiColors.accent);
    expect(tester.widget<Text>(livePreviewCount).data, contains('1'));
    expect(tester.widget<Text>(livePreviewCount).style?.color, Colors.white);

    realtime.add(
      liveSnapshotEvent([
        participant(
          id: 'user-2',
          username: 'morgan',
          displayName: 'Morgan',
          liveSessionId: 'live-session-morgan',
        ),
        participant(
          id: 'user-3',
          username: 'riley',
          displayName: 'Riley',
          liveSessionId: 'live-session-riley',
        ),
      ]),
    );
    await tester.pumpAndSettle();

    expect(
      find.descendant(of: livePreview, matching: find.byType(ui.Avatar)),
      findsNWidgets(2),
    );
    expect(
      tester.getRect(chatLiveButton).right - tester.getRect(livePreview).right,
      closeTo(10, 1),
    );
    expect(tester.widget<Text>(livePreviewCount).data, contains('2'));

    realtime.add(
      liveSnapshotEvent([
        participant(
          id: 'user-2',
          username: 'morgan',
          displayName: 'Morgan',
          liveSessionId: 'live-session-morgan',
        ),
        participant(
          id: 'user-3',
          username: 'riley',
          displayName: 'Riley',
          liveSessionId: 'live-session-riley',
        ),
        participant(
          id: 'user-4',
          username: 'ivy',
          displayName: 'Ivy',
          liveSessionId: 'live-session-ivy',
        ),
        participant(
          id: 'user-5',
          username: 'taylor',
          displayName: 'Taylor',
          liveSessionId: 'live-session-taylor',
        ),
        participant(
          id: 'user-6',
          username: 'noah',
          displayName: 'Noah',
          liveSessionId: 'live-session-noah',
        ),
        participant(
          id: 'user-7',
          username: 'mina',
          displayName: 'Mina',
          liveSessionId: 'live-session-mina',
        ),
      ]),
    );
    await tester.pumpAndSettle();

    expect(
      find.descendant(of: livePreview, matching: find.byType(ui.Avatar)),
      findsNWidgets(5),
    );
    expect(
      tester.getRect(chatLiveButton).right - tester.getRect(livePreview).right,
      closeTo(10, 1),
    );
    expect(tester.widget<Text>(livePreviewCount).data, contains('6'));
    expect(
      tester
          .widget<Text>(find.byKey(const ValueKey('chat-header-room-meta')))
          .data,
      '2 名成员 · 1 人在线',
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('authenticated home shell shows realtime reconnecting marker', (
    WidgetTester tester,
  ) async {
    final realtime = _FakeRealtimeService();

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(app: _homeTestAppContext(), realtime: realtime),
      ),
    );
    await tester.pumpAndSettle();

    final userSummary = find.byKey(const ValueKey('home-sidebar-user-summary'));
    expect(
      find.descendant(of: userSummary, matching: find.textContaining('重连中')),
      findsNothing,
    );

    realtime.setStatus(RealtimeConnectionStatus.reconnecting);
    await tester.pumpAndSettle();

    expect(
      find.descendant(of: userSummary, matching: find.text('重连中')),
      findsOneWidget,
    );
    expect(
      find.descendant(of: userSummary, matching: find.text('在线')),
      findsNothing,
    );

    realtime.setStatus(RealtimeConnectionStatus.connected);
    await tester.pumpAndSettle();

    expect(
      find.descendant(of: userSummary, matching: find.textContaining('重连中')),
      findsNothing,
    );
    expect(
      find.descendant(of: userSummary, matching: find.text('在线')),
      findsOneWidget,
    );
  });

  testWidgets(
    'authenticated home shell keeps live session on realtime reconnect',
    (WidgetTester tester) async {
      final realtime = _FakeRealtimeService();
      final liveJoinRequests = <Map<String, Object?>>[];
      final liveStateUpdates = <Map<String, Object?>>[];
      final liveSession = _FakeLiveSession();
      final liveSessionController = _FakeLiveSessionController(
        session: liveSession,
      );

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: HomePage(
            app: _homeTestAppContext(
              liveJoinRequests: liveJoinRequests,
              liveStateUpdates: liveStateUpdates,
            ),
            liveSessionController: liveSessionController,
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

      expect(liveSession.connectAttempts, 1);
      liveJoinRequests.clear();
      liveStateUpdates.clear();

      await tester.tap(_liveControl('mic'));
      await tester.pumpAndSettle();
      expect(liveStateUpdates.last['mic_muted'], true);
      liveStateUpdates.clear();

      realtime.setStatus(RealtimeConnectionStatus.reconnecting);
      await tester.pumpAndSettle();

      expect(
        find.descendant(
          of: find.byKey(const ValueKey('home-sidebar-user-summary')),
          matching: find.text('重连中'),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('home-sidebar-user-summary')),
          matching: find.text('语音'),
        ),
        findsNothing,
      );

      realtime.setStatus(RealtimeConnectionStatus.connected);
      realtime.emitReconnect();
      await tester.pumpAndSettle();

      expect(liveJoinRequests, isEmpty);
      expect(liveSession.connectAttempts, 1);
      expect(liveStateUpdates, isEmpty);
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('home-sidebar-user-summary')),
          matching: find.textContaining('重连中'),
        ),
        findsNothing,
      );
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('home-sidebar-user-summary')),
          matching: find.text('语音'),
        ),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'authenticated home shell restores live after LiveKit disconnect',
    (WidgetTester tester) async {
      final realtime = _FakeRealtimeService();
      final liveJoinRequests = <Map<String, Object?>>[];
      final liveStateUpdates = <Map<String, Object?>>[];
      final liveSession = _FakeLiveSession();
      final liveSessionController = _FakeLiveSessionController(
        session: liveSession,
      );

      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme(),
          home: HomePage(
            app: _homeTestAppContext(
              liveJoinRequests: liveJoinRequests,
              liveStateUpdates: liveStateUpdates,
            ),
            liveSessionController: liveSessionController,
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

      await tester.tap(_liveControl('mic'));
      await tester.pumpAndSettle();
      await tester.tap(_liveControl('camera'));
      await tester.pumpAndSettle();

      realtime.add(
        RealtimeEvent(
          type: 'live_participant_updated',
          data: {
            'room_id': 'server-alpha',
            'participant_count': 2,
            'preview': <Object?>[],
            'live': {
              ..._liveStateJson(
                roomId: 'server-alpha',
                participantCount: 2,
                participants: [
                  _liveParticipantJson(
                    user: _currentUserJson,
                    liveSessionId: 'live-session-joined',
                    micMuted: true,
                    cameraOn: true,
                    cameraMirrored: true,
                  ),
                  _liveParticipantJson(
                    user: _userJson(
                      id: 'user-2',
                      username: 'morgan',
                      displayName: 'Morgan',
                    ),
                    liveSessionId: 'live-session-morgan',
                    micMuted: true,
                  ),
                ],
              ),
              'updated_at': '2099-06-05T08:00:00Z',
            },
          },
        ),
      );
      await tester.pumpAndSettle();
      liveJoinRequests.clear();
      liveStateUpdates.clear();

      liveSession.emitUnexpectedDisconnect();
      await tester.pumpAndSettle();

      expect(
        liveJoinRequests.map((body) => body['source']),
        contains('reconnect'),
      );
      expect(liveSession.connectAttempts, 2);
      expect(liveStateUpdates.last['mic_muted'], true);
      expect(liveStateUpdates.last['headphones_muted'], false);
      expect(liveStateUpdates.last['camera_on'], true);
      expect(liveStateUpdates.last['camera_mirrored'], true);
      expect(liveStateUpdates.last['screen_sharing'], false);
      expect(liveSession.cameraEnables, contains(true));
    },
  );

  testWidgets('authenticated home shell footer opens settings and logs out', (
    WidgetTester tester,
  ) async {
    final accountUpdates = <Map<String, Object?>>[];
    var logoutCount = 0;

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: HomePage(
          app: _homeTestAppContext(
            onLogout: () async => logoutCount++,
            accountUpdates: accountUpdates,
          ),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final userSummaryRect = tester.getRect(
      find.byKey(const ValueKey('home-sidebar-user-summary')),
    );
    final createRect = tester.getRect(find.byTooltip('创建房间'));
    final notificationsRect = tester.getRect(find.byTooltip('通知'));
    final settingsRect = tester.getRect(find.byTooltip('设置'));
    final logoutRect = tester.getRect(find.byTooltip('退出登录'));
    expect(createRect.left, closeTo(userSummaryRect.left, 0.01));
    expect(notificationsRect.top, closeTo(createRect.top, 0.01));
    expect(notificationsRect.bottom, closeTo(createRect.bottom, 0.01));
    expect(settingsRect.top, closeTo(createRect.top, 0.01));
    expect(settingsRect.bottom, closeTo(createRect.bottom, 0.01));
    // The logout control now lives inline in the top user summary bar, anchored
    // to its right edge — not in the bottom footer alongside settings.
    expect(logoutRect.top, greaterThanOrEqualTo(userSummaryRect.top - 0.01));
    expect(logoutRect.bottom, lessThanOrEqualTo(userSummaryRect.bottom + 0.01));
    expect(logoutRect.bottom, lessThan(createRect.top));
    expect(logoutRect.right, lessThanOrEqualTo(userSummaryRect.right + 0.01));

    await tester.tap(find.byTooltip('设置'));
    await tester.pumpAndSettle();

    expect(find.text('设置'), findsOneWidget);
    expect(find.textContaining('设置 ·'), findsNothing);
    expect(find.text('用户资料'), findsOneWidget);
    expect(find.text('偏好设置'), findsOneWidget);
    expect(find.byTooltip('刷新设置'), findsOneWidget);
    final displayNameField = find.byWidgetPredicate(
      (widget) => widget is TextField && widget.controller?.text == 'Kai',
    );
    expect(displayNameField, findsOneWidget);
    expect(
      tester.widget<TextField>(displayNameField).decoration?.hintText,
      isEmpty,
    );
    expect(
      tester
          .widget<ui.PressableSurface>(
            find.ancestor(
              of: find.byTooltip('设置'),
              matching: find.byType(ui.PressableSurface),
            ),
          )
          .selected,
      isTrue,
    );

    final usernameField = find.byWidgetPredicate(
      (widget) => widget is TextField && widget.controller?.text == 'kai',
    );
    expect(usernameField, findsOneWidget);
    expect(find.widgetWithText(ui.Button, '保存登录用户名'), findsNothing);
    expect(find.text('合法'), findsNothing);

    await tester.enterText(usernameField, 'kai_new');
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();

    expect(find.text('合法'), findsOneWidget);

    await tester.tap(find.text('偏好设置'));
    await tester.pumpAndSettle();

    expect(find.text('语言切换'), findsOneWidget);
    expect(find.text('语言'), findsOneWidget);
    expect(find.text('简体中文'), findsOneWidget);
    expect(find.text('繁體中文'), findsOneWidget);
    expect(find.text('English'), findsOneWidget);
    expect(find.widgetWithText(ui.Button, '保存偏好设置'), findsOneWidget);
    expect(find.text('隐私和安全'), findsOneWidget);
    expect(find.text('用户资料'), findsOneWidget);

    await tester.tap(find.text('English'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ui.Button, '保存偏好设置'));
    await tester.pumpAndSettle();

    expect(accountUpdates, hasLength(1));
    expect(accountUpdates.single['language'], 'en');
    expect(find.text('偏好设置已保存'), findsOneWidget);

    await tester.tap(find.byTooltip('退出登录'));
    await tester.pumpAndSettle();

    expect(logoutCount, 0);
    expect(find.text('确认退出当前账号？'), findsOneWidget);
    final cancelLogout = find.widgetWithText(ui.Button, '取消');
    final confirmLogout = find.widgetWithText(ui.Button, '退出登录');
    expect(cancelLogout, findsOneWidget);
    expect(confirmLogout, findsOneWidget);
    final cancelLogoutRect = tester.getRect(cancelLogout);
    final confirmLogoutRect = tester.getRect(confirmLogout);
    expect(
      cancelLogoutRect.center.dy,
      closeTo(confirmLogoutRect.center.dy, 0.01),
    );
    expect(cancelLogoutRect.right, lessThan(confirmLogoutRect.left));
    expect(
      tester
          .widget<Icon>(
            find.descendant(
              of: find.byTooltip('退出登录'),
              matching: find.byIcon(Icons.logout),
            ),
          )
          .color,
      ui.UiColors.accent,
    );

    await tester.tap(find.widgetWithText(ui.Button, '取消'));
    await tester.pumpAndSettle();
    expect(logoutCount, 0);
    expect(find.text('确认退出当前账号？'), findsNothing);
    expect(
      tester
          .widget<Icon>(
            find.descendant(
              of: find.byTooltip('退出登录'),
              matching: find.byIcon(Icons.logout),
            ),
          )
          .color,
      ui.UiColors.textMuted,
    );

    await tester.tap(find.byTooltip('退出登录'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ui.Button, '退出登录'));
    await tester.pumpAndSettle();
    expect(logoutCount, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('authenticated home shell lets sidebar scrollbar auto-hide', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme(),
        home: Center(
          child: SizedBox(
            width: 360,
            height: 320,
            child: HomePage(
              app: _homeTestAppContext(),
              realtime: _NoopRealtimeService(),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final userSummaryRect = tester.getRect(
      find.byKey(const ValueKey('home-sidebar-user-summary')),
    );
    final alphaCardRect = tester.getRect(
      find.ancestor(
        of: find.text('Alpha Room'),
        matching: find.byType(ui.PressableSurface),
      ),
    );

    expect(userSummaryRect.right - alphaCardRect.right, closeTo(0, 0.01));
    expect(find.byType(Scrollbar), findsNothing);
    final rawScrollbarFinder = find.byType(RawScrollbar);
    expect(rawScrollbarFinder, findsOneWidget);
    final rawScrollbar = tester.widget<RawScrollbar>(rawScrollbarFinder);
    expect(rawScrollbar.radius, const Radius.circular(999));
    expect(rawScrollbar.controller, isNotNull);
    expect(rawScrollbar.controller!.hasClients, isTrue);
    expect(rawScrollbar.interactive, isTrue);
    expect(
      tester.getRect(rawScrollbarFinder).right - alphaCardRect.right,
      closeTo(8, 0.01),
    );
    await tester.dragFrom(
      tester.getRect(rawScrollbarFinder).topRight - const Offset(3, -30),
      const Offset(0, 30),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets('authenticated home shell keeps macOS sidebar below title bar', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.macOS),
        home: HomePage(
          app: _homeTestAppContext(),
          realtime: _NoopRealtimeService(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final userSummaryRect = tester.getRect(
      find.byKey(const ValueKey('home-sidebar-user-summary')),
    );
    final searchRect = tester.getRect(
      find.byKey(const ValueKey('home-title-search')),
    );
    final liveDockRect = tester.getRect(
      find.byKey(const ValueKey<String>('home-title-live-room')),
    );

    expect(find.text('Gang Chat'), findsNothing);
    expect(find.byTooltip('最小化'), findsNothing);
    expect(find.byTooltip('最大化'), findsNothing);
    expect(find.byTooltip('关闭'), findsNothing);
    expect(searchRect.center.dx, greaterThan(400));
    expect(liveDockRect.right, lessThanOrEqualTo(searchRect.left - 14));
    expect(userSummaryRect.top, closeTo(60, 0.01));
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'authenticated home shell uses separate narrow room list and chat screens',
    (WidgetTester tester) async {
      final requestedPaths = <String>[];
      await tester.pumpWidget(
        MaterialApp(
          theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
          home: Center(
            child: SizedBox(
              width: 420,
              height: 620,
              child: HomePage(
                app: _homeTestAppContext(requestedPaths: requestedPaths),
                realtime: _NoopRealtimeService(),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      final compactSearchRect = tester.getRect(
        find.byKey(const ValueKey('home-title-search')),
      );
      final userSummaryRect = tester.getRect(
        find.byKey(const ValueKey('home-sidebar-user-summary')),
      );
      final firstRoomCardRect = tester.getRect(
        find.ancestor(
          of: find.text('Alpha Room'),
          matching: find.byType(ui.PressableSurface),
        ),
      );
      final firstRoomCard = tester.widget<ui.PressableSurface>(
        find.byKey(const ValueKey('home-sidebar-room-server-alpha')),
      );
      final roomScrollbarFinder = find.byKey(
        const ValueKey('home-sidebar-room-scrollbar'),
      );
      final roomScrollbar = tester.widget<RawScrollbar>(roomScrollbarFinder);

      expect(compactSearchRect.width, closeTo(userSummaryRect.width, 0.01));
      expect(userSummaryRect.bottom, lessThan(compactSearchRect.top));
      expect(
        tester.getRect(roomScrollbarFinder).top,
        closeTo(firstRoomCardRect.top, 0.01),
      );
      expect(roomScrollbar.controller!.position.extentBefore, 0);
      expect(roomScrollbar.padding, EdgeInsets.zero);
      expect(firstRoomCard.hoverLift, 0);
      expect(find.text('Alpha Room'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('home-narrow-room-list')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('home-narrow-content')), findsNothing);
      expect(find.byTooltip('返回房间列表'), findsNothing);
      expect(find.byTooltip('Show servers'), findsNothing);
      expect(find.byTooltip('最小化'), findsNothing);
      expect(find.byTooltip('最大化'), findsNothing);
      expect(find.byTooltip('关闭'), findsNothing);

      final compactSearchField = find.descendant(
        of: find.byKey(const ValueKey('home-title-search')),
        matching: find.byType(TextField),
      );
      await tester.enterText(compactSearchField, 'Beta');
      await tester.pump(const Duration(milliseconds: 320));
      await tester.pumpAndSettle();

      expect(requestedPaths, contains('/api/v1/search'));
      expect(
        find.byKey(const ValueKey('home-title-search-results')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('home-sidebar-user-summary')),
        findsOneWidget,
      );

      await tester.enterText(compactSearchField, '');
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('home-title-search-results')),
        findsNothing,
      );
      expect(
        find.byKey(const ValueKey('home-sidebar-user-summary')),
        findsOneWidget,
      );

      await tester.tap(find.byTooltip('设置'));
      await tester.pumpAndSettle();

      expect(find.text('用户资料'), findsOneWidget);
      expect(find.byKey(const ValueKey('home-narrow-room-list')), findsNothing);

      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('home-narrow-room-list')),
        findsOneWidget,
      );
      expect(find.text('用户资料'), findsNothing);

      await tester.tap(find.text('Alpha Room'));
      await tester.pumpAndSettle();

      expect(find.text('Beta Room'), findsNothing);
      expect(find.byKey(const ValueKey('home-narrow-room-list')), findsNothing);
      expect(find.byKey(const ValueKey('home-narrow-content')), findsOneWidget);
      expect(find.byTooltip('Show servers'), findsNothing);
      expect(find.byTooltip('返回房间列表'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('chat-header-live-button')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('chat-header-room-title')),
        findsOneWidget,
      );
      expect(
        tester.getRect(find.byTooltip('返回房间列表')).left,
        greaterThanOrEqualTo(
          tester
              .getRect(find.byKey(const ValueKey('chat-header-live-button')))
              .left,
        ),
      );
      expect(
        tester.getRect(find.byTooltip('返回房间列表')).right,
        lessThan(
          tester
              .getRect(find.byKey(const ValueKey('chat-header-room-avatar')))
              .left,
        ),
      );
      expect(find.text('进入语音频道'), findsNothing);
      expect(find.text('Hello from Morgan'), findsOneWidget);

      await tester.tap(find.byTooltip('返回房间列表'));
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('home-narrow-room-list')),
        findsOneWidget,
      );
      expect(find.text('Beta Room'), findsOneWidget);
      expect(find.byKey(const ValueKey('home-narrow-content')), findsNothing);
      expect(find.byTooltip('返回房间列表'), findsNothing);

      for (final entry in ['创建房间', '通知', '设置']) {
        await tester.tap(find.byTooltip(entry));
        await tester.pumpAndSettle();

        expect(
          find.byKey(const ValueKey('home-narrow-room-list')),
          findsNothing,
        );
        expect(find.byTooltip('返回'), findsOneWidget);

        await tester.tap(find.byTooltip('返回'));
        await tester.pumpAndSettle();

        expect(
          find.byKey(const ValueKey('home-narrow-room-list')),
          findsOneWidget,
        );
        expect(find.byKey(const ValueKey('home-narrow-content')), findsNothing);
        expect(find.byTooltip('返回房间列表'), findsNothing);
      }

      await tester.tap(find.text('Alpha Room'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('房间成员'));
      await tester.pumpAndSettle();

      expect(find.byTooltip('返回'), findsOneWidget);
      expect(find.byTooltip('返回房间列表'), findsNothing);

      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();

      expect(find.byTooltip('返回房间列表'), findsOneWidget);
      expect(find.byTooltip('返回'), findsNothing);

      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('home-narrow-room-list')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('home-narrow-content')), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('Android tablet uses the persistent wide desktop layout', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(1280, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

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

    expect(find.byKey(const ValueKey('home-narrow-room-list')), findsNothing);
    expect(find.byKey(const ValueKey('home-narrow-content')), findsNothing);
    expect(find.text('Alpha Room'), findsOneWidget);
    expect(find.text('Beta Room'), findsOneWidget);

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();

    expect(find.text('Beta Room'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('chat-header-live-preview')),
      findsOneWidget,
    );
    expect(
      tester
          .widget<ui.PressableSurface>(
            find.byKey(const ValueKey('chat-header-live-button')),
          )
          .tooltip,
      isNotNull,
    );
    expect(find.byTooltip('鏈€灏忓寲'), findsNothing);
    expect(find.byTooltip('鏈€澶у寲'), findsNothing);
    expect(find.byTooltip('鍏抽棴'), findsNothing);
    expect(tester.takeException(), isNull);

    tester.view.physicalSize = const Size(600, 960);
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('home-narrow-room-list')), findsOneWidget);
    expect(find.text('Beta Room'), findsOneWidget);
    expect(tester.takeException(), isNull);

    tester.view.physicalSize = const Size(540, 720);
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('home-narrow-room-list')), findsOneWidget);
    expect(find.text('Beta Room'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('windows narrow shell uses adaptive room navigation', (
    WidgetTester tester,
  ) async {
    final requestedPaths = <String>[];
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.windows),
        home: Center(
          child: SizedBox(
            width: 360,
            height: 620,
            child: HomePage(
              app: _homeTestAppContext(requestedPaths: requestedPaths),
              realtime: _NoopRealtimeService(),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('android-shell-back-scope')),
      findsNothing,
    );
    expect(find.byKey(const ValueKey('home-narrow-room-list')), findsOneWidget);
    expect(find.byKey(const ValueKey('home-title-search')), findsOneWidget);
    expect(find.byTooltip('最小化'), findsOneWidget);
    expect(find.byTooltip('最大化'), findsOneWidget);
    expect(find.byTooltip('关闭'), findsOneWidget);

    final compactSearchField = find.descendant(
      of: find.byKey(const ValueKey('home-title-search')),
      matching: find.byType(TextField),
    );
    await tester.enterText(compactSearchField, 'Beta');
    await tester.pump(const Duration(milliseconds: 320));
    await tester.pumpAndSettle();
    await tester.tap(find.text('聊天记录 1').first);
    await tester.pumpAndSettle();

    expect(requestedPaths, contains('/api/v1/search'));
    final firstMessageResult = find.byKey(
      const ValueKey('search-message-result-msg-beta'),
    );
    final searchResults = find.byKey(
      const ValueKey('home-title-search-results'),
    );
    expect(firstMessageResult, findsOneWidget);
    expect(
      tester.getRect(firstMessageResult).top,
      greaterThanOrEqualTo(tester.getRect(searchResults).top),
    );
    final firstMessageSubtitle = tester.widget<ui.HighlightedText>(
      find.descendant(
        of: firstMessageResult,
        matching: find.byWidgetPredicate(
          (widget) =>
              widget is ui.HighlightedText && widget.text.contains('Morgan'),
        ),
      ),
    );
    expect(firstMessageSubtitle.style?.height, 1.35);

    await tester.enterText(compactSearchField, '');
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();

    expect(find.byTooltip('返回房间列表'), findsOneWidget);
    expect(find.byKey(const ValueKey('home-narrow-content')), findsOneWidget);
    expect(
      find.byKey(const ValueKey('chat-header-live-preview-count')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('chat-header-live-preview')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('macOS narrow shell uses adaptive room navigation', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.macOS),
        home: Center(
          child: SizedBox(
            width: 420,
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

    expect(find.byKey(const ValueKey('home-narrow-room-list')), findsOneWidget);
    expect(find.byKey(const ValueKey('home-title-search')), findsOneWidget);
    expect(find.byTooltip('最小化'), findsNothing);
    expect(find.byTooltip('最大化'), findsNothing);
    expect(find.byTooltip('关闭'), findsNothing);

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();

    expect(find.byTooltip('返回房间列表'), findsOneWidget);
    expect(find.byKey(const ValueKey('home-narrow-content')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('narrow live chat header keeps the room identity visible', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
        home: Center(
          child: SizedBox(
            width: 360,
            height: 740,
            child: HomePage(
              app: _homeTestAppContext(),
              realtime: _NoopRealtimeService(),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alpha Room'));
    await tester.pumpAndSettle();

    expect(find.byTooltip('返回房间列表'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('chat-header-room-title')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('chat-header-live-preview-count')),
      findsOneWidget,
    );
    expect(find.text('Alpha Room'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('android shell stays above the system navigation safe area', (
    WidgetTester tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(360, 740));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      MaterialApp(
        theme: ui.uiTheme().copyWith(platform: TargetPlatform.android),
        builder: (context, child) {
          final media = MediaQuery.of(context);
          return MediaQuery(
            data: media.copyWith(
              padding: const EdgeInsets.only(bottom: 32),
              viewPadding: const EdgeInsets.only(bottom: 32),
            ),
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

    expect(tester.getRect(find.byTooltip('设置')).bottom, lessThanOrEqualTo(708));
    expect(
      tester
          .getRect(
            find.byKey(const ValueKey('home-sidebar-notifications-button')),
          )
          .bottom,
      lessThanOrEqualTo(708),
    );
    expect(tester.takeException(), isNull);
  });
}
