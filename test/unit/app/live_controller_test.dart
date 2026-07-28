import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:client/src/app/live_controller.dart';
import 'package:client/src/app/rooms_controller.dart';
import 'package:client/src/protocol/api_client.dart';
import 'package:client/src/protocol/models.dart';

void main() {
  test('live state request queue preserves mutation order', () async {
    final queue = LiveStateRequestQueue();
    final firstGate = Completer<void>();
    final started = <int>[];

    final first = queue.run(() async {
      started.add(1);
      await firstGate.future;
      return 'first';
    });
    final second = queue.run(() async {
      started.add(2);
      return 'second';
    });

    await Future<void>.delayed(Duration.zero);
    expect(started, [1]);
    expect(queue.isIdle, isFalse);

    firstGate.complete();
    expect(await first, 'first');
    expect(await second, 'second');
    expect(started, [1, 2]);
    expect(queue.isIdle, isTrue);
  });

  test('live state request queue continues after a failed mutation', () async {
    final queue = LiveStateRequestQueue();

    final failed = queue.run<void>(() async => throw StateError('failed'));
    final recovered = queue.run(() async => 2);

    await expectLater(failed, throwsStateError);
    expect(await recovered, 2);
    expect(queue.isIdle, isTrue);
  });

  test(
    'live room guards keep room switching and stale async work explicit',
    () {
      expect(
        joinedLiveRoomToDisconnectBeforeJoin(
          joinedLiveRoomId: null,
          targetRoomId: 'room_1',
        ),
        isNull,
      );
      expect(
        joinedLiveRoomToDisconnectBeforeJoin(
          joinedLiveRoomId: 'room_1',
          targetRoomId: 'room_1',
        ),
        isNull,
      );
      expect(
        joinedLiveRoomToDisconnectBeforeJoin(
          joinedLiveRoomId: 'room_1',
          targetRoomId: 'room_2',
        ),
        'room_1',
      );

      expect(
        canPatchSelectedLiveState(
          joinedLiveRoomId: 'room_1',
          selectedRoomId: 'room_1',
        ),
        isTrue,
      );
      expect(
        canPatchSelectedLiveState(
          joinedLiveRoomId: null,
          selectedRoomId: 'room_1',
        ),
        isFalse,
      );
      expect(
        canPatchSelectedLiveState(
          joinedLiveRoomId: 'room_2',
          selectedRoomId: 'room_1',
        ),
        isFalse,
      );

      expect(
        canApplyPickedScreenShareSource(
          pickedForRoomId: 'room_1',
          joinedLiveRoomId: 'room_1',
          selectedRoomId: 'room_1',
        ),
        isTrue,
      );
      expect(
        canApplyPickedScreenShareSource(
          pickedForRoomId: 'room_1',
          joinedLiveRoomId: 'room_2',
          selectedRoomId: 'room_1',
        ),
        isFalse,
      );
      expect(
        canApplyPickedScreenShareSource(
          pickedForRoomId: 'room_1',
          joinedLiveRoomId: 'room_1',
          selectedRoomId: 'room_2',
        ),
        isFalse,
      );
    },
  );

  test('409 live state patch errors are treated as benign departure races', () {
    expect(
      isBenignGoneLiveStatePatch(
        ApiException(
          'gone',
          statusCode: 409,
          code: 'participant_not_found',
          requestId: null,
        ),
      ),
      isTrue,
    );
    expect(
      isBenignGoneLiveStatePatch(
        ApiException(
          'server error',
          statusCode: 500,
          code: 'request_failed',
          requestId: null,
        ),
      ),
      isFalse,
    );
    expect(isBenignGoneLiveStatePatch(StateError('boom')), isFalse);
  });

  test(
    'live participant JSON keeps mirror and screen viewers backward compatible',
    () {
      final legacy = LiveParticipant.fromJson(
        _participantJson(user: _userJson('alice')),
      );
      final withViewer = LiveParticipant.fromJson({
        ..._participantJson(user: _userJson('alice')),
        'camera_mirrored': true,
        'screen_sharing': true,
        'screen_viewers': [_userJson('bob')],
      });

      expect(legacy.screenViewers, isEmpty);
      expect(legacy.cameraMirrored, isFalse);
      expect(withViewer.cameraMirrored, isTrue);
      expect(withViewer.screenViewers.map((user) => user.id), ['bob']);
    },
  );

  test('join state patches keep LiveKit side effects out of UI flags', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Reducer test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = LiveController(api: api);
    final alice = _user('alice');
    final bob = _user('bob');

    final started = controller.patchJoinStarted(joinedLiveRoomId: 'room_0');
    expect(started.joinedLiveRoomId, 'room_0');
    expect(started.joiningLive, isTrue);
    expect(started.livePanelOpen, isTrue);
    expect(started.error, isNull);

    final disconnected = controller.patchJoinPreviousRoomDisconnected(
      live: _live('room_0', [_participant(alice), _participant(bob)]),
      rooms: [
        _roomCard('room_0', liveCount: 2, livePreview: [alice, bob]),
      ],
      previousRoomId: 'room_0',
      userId: 'alice',
      livePanelOpen: false,
      error: 'keep existing pane error',
    );
    expect(disconnected.live?.participants.map((item) => item.user.id), [
      'bob',
    ]);
    expect(disconnected.rooms.single.liveParticipantCount, 1);
    expect(disconnected.rooms.single.liveAvatarPreview.map((user) => user.id), [
      'bob',
    ]);
    expect(disconnected.joinedLiveRoomId, isNull);
    expect(disconnected.joiningLive, isTrue);
    expect(disconnected.livePanelOpen, isFalse);
    expect(disconnected.error, 'keep existing pane error');

    final connected = controller.patchJoinConnected(
      roomId: 'room_1',
      livePanelOpen: true,
      error: 'preserved',
    );
    expect(connected.joinedLiveRoomId, 'room_1');
    expect(connected.joiningLive, isTrue);
    expect(connected.livePanelOpen, isTrue);
    expect(connected.error, 'preserved');

    final finished = controller.patchJoinFinished(
      joinedLiveRoomId: 'room_1',
      livePanelOpen: true,
      error: null,
    );
    expect(finished.joinedLiveRoomId, 'room_1');
    expect(finished.joiningLive, isFalse);
    expect(finished.livePanelOpen, isTrue);
    expect(finished.error, isNull);
  });

  test('removeUserFromLive updates selected live state and room preview', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Reducer test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = LiveController(api: api);

    final alice = _user('alice');
    final bob = _user('bob');
    final patch = controller.removeUserFromLive(
      live: _live('room_1', [_participant(alice), _participant(bob)]),
      rooms: [
        _roomCard('room_1', liveCount: 2, livePreview: [alice, bob]),
        _roomCard('room_2', liveCount: 1, livePreview: [alice]),
      ],
      roomId: 'room_1',
      userId: 'alice',
    );

    expect(patch.live?.participantCount, 1);
    expect(patch.live?.participants.map((item) => item.user.id), ['bob']);
    expect(patch.rooms[0].liveParticipantCount, 1);
    expect(patch.rooms[0].liveAvatarPreview.map((user) => user.id), ['bob']);
    expect(patch.rooms[1].liveParticipantCount, 1);
    expect(patch.rooms[1].liveAvatarPreview.map((user) => user.id), ['alice']);
  });

  test('patchLocalDeparture clears joined local state and removes self', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Reducer test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = LiveController(api: api);
    final alice = _user('alice');
    final bob = _user('bob');

    final patch = controller.patchLocalDeparture(
      live: _live('room_1', [_participant(alice), _participant(bob)]),
      rooms: [
        _roomCard('room_1', liveCount: 2, livePreview: [alice, bob]),
      ],
      joinedLiveRoomId: 'room_1',
      userId: 'alice',
      joiningLive: true,
    );

    expect(patch.joinedLiveRoomId, isNull);
    expect(patch.joiningLive, isTrue);
    expect(patch.cameraOn, isFalse);
    expect(patch.screenSharing, isFalse);
    expect(patch.voiceBlocked, isFalse);
    expect(patch.live?.participants.map((item) => item.user.id), ['bob']);
    expect(patch.rooms.single.liveParticipantCount, 1);
    expect(patch.rooms.single.liveAvatarPreview.map((user) => user.id), [
      'bob',
    ]);

    final live = _live('room_2', [_participant(alice)]);
    final rooms = [
      _roomCard('room_2', liveCount: 1, livePreview: [alice]),
    ];
    final noJoinedRoomPatch = controller.patchLocalDeparture(
      live: live,
      rooms: rooms,
      joinedLiveRoomId: null,
      userId: 'alice',
      joiningLive: false,
    );
    expect(noJoinedRoomPatch.live, same(live));
    expect(noJoinedRoomPatch.rooms, same(rooms));
    expect(noJoinedRoomPatch.joinedLiveRoomId, isNull);
    expect(noJoinedRoomPatch.joiningLive, isFalse);
  });

  test('patchPublishPermission mirrors LiveKit publish permission', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Reducer test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = LiveController(api: api);

    var patch = controller.patchPublishPermission(
      canPublish: false,
      micMuted: false,
    );
    expect(patch.voiceBlocked, isTrue);
    expect(patch.micMuted, isTrue);

    patch = controller.patchPublishPermission(canPublish: true, micMuted: true);
    expect(patch.voiceBlocked, isFalse);
    expect(patch.micMuted, isTrue);
  });

  test('live output mute toggle flips local headphones state', () {
    expect(
      liveOutputMuteToggled(headphonesMuted: false).headphonesMuted,
      isTrue,
    );
    expect(
      liveOutputMuteToggled(headphonesMuted: true).headphonesMuted,
      isFalse,
    );
  });

  test('leaveLive reports a left connection state to the server', () async {
    Map<String, Object?>? body;
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'PATCH');
        expect(request.url.path, '/api/v1/rooms/room_1/live/me');
        body =
            jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>;
        return http.Response(
          jsonEncode({
            'participant': _participantJson(
              user: _userJson('alice'),
              connectionState: 'left',
            ),
          }),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );
    addTearDown(api.close);

    final participant = await LiveController(
      api: api,
    ).leaveLive(roomId: 'room_1');

    expect(body, {'connection_state': 'left'});
    expect(participant.connectionState, 'left');
  });

  test('camera mirror updates use the existing live state endpoint', () async {
    Map<String, Object?>? body;
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'PATCH');
        expect(request.url.path, '/api/v1/rooms/room_1/live/me');
        body =
            jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>;
        return http.Response(
          jsonEncode({
            'participant': {
              ..._participantJson(user: _userJson('alice')),
              'camera_on': true,
              'camera_mirrored': true,
            },
          }),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );
    addTearDown(api.close);

    final participant = await LiveController(
      api: api,
    ).updateMyState(roomId: 'room_1', cameraMirrored: true);

    expect(body, {'camera_mirrored': true});
    expect(participant.cameraMirrored, isTrue);
  });

  test(
    'updateMyLiveScreenView sends the watched broadcaster or clears it',
    () async {
      final bodies = <Map<String, Object?>>[];
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          expect(request.method, 'PATCH');
          expect(request.url.path, '/api/v1/rooms/room_1/live/me/screen-view');
          bodies.add(
            jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>,
          );
          return http.Response(
            jsonEncode({'broadcaster_user_id': null}),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );
      addTearDown(api.close);
      final controller = LiveController(api: api);

      await controller.updateMyLiveScreenView(
        roomId: 'room_1',
        broadcasterUserId: 'alice',
      );
      await controller.updateMyLiveScreenView(roomId: 'room_1');

      expect(bodies, [
        {'broadcaster_user_id': 'alice'},
        {'broadcaster_user_id': ''},
      ]);
    },
  );

  test('kickParticipant uses live moderation kick action', () async {
    Map<String, Object?>? body;
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        expect(request.method, 'POST');
        expect(
          request.url.path,
          '/api/v1/rooms/room_1/live/participants/bob/moderation',
        );
        body =
            jsonDecode(utf8.decode(request.bodyBytes)) as Map<String, Object?>;
        return http.Response(
          jsonEncode({'ok': true}),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );
    addTearDown(api.close);

    await LiveController(
      api: api,
    ).kickParticipant(roomId: 'room_1', userId: 'bob');

    expect(body, {'action': 'kick'});
  });

  test('patchJoinResult mirrors participant flags and room live preview', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Reducer test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = LiveController(api: api);
    final alice = _user('alice');
    final bob = _user('bob');
    final participant = _participant(
      alice,
      micMuted: true,
      cameraOn: true,
      screenSharing: true,
      voiceBlocked: true,
    );
    final live = _live('room_1', [participant, _participant(bob)]);

    final patch = controller.patchJoinResult(
      rooms: [
        _roomCard('room_1', liveCount: 0),
        _roomCard('room_2', liveCount: 3),
      ],
      result: _liveJoinResult(participant: participant, live: live),
    );

    expect(patch.micMuted, isTrue);
    expect(patch.headphonesMuted, isFalse);
    expect(patch.cameraOn, isFalse);
    expect(patch.screenSharing, isTrue);
    expect(patch.voiceBlocked, isTrue);
    expect(patch.live.participants.first.cameraOn, isFalse);
    expect(patch.live.participants.first.screenSharing, isTrue);
    expect(patch.rooms[0].liveParticipantCount, 2);
    expect(patch.rooms[0].liveAvatarPreview.map((user) => user.id), [
      'alice',
      'bob',
    ]);
    expect(patch.rooms[1].liveParticipantCount, 3);
  });

  test('patchJoinResult can show the joined mic unmuted when allowed', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Reducer test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = LiveController(api: api);
    final alice = _user('alice');
    final participant = _participant(alice, micMuted: true);
    final live = _live('room_1', [participant]);

    final patch = controller.patchJoinResult(
      rooms: [_roomCard('room_1', liveCount: 0)],
      result: _liveJoinResult(participant: participant, live: live),
      showMicUnmutedWhenAllowed: true,
    );

    expect(patch.micMuted, isFalse);
    expect(patch.live.participants.single.micMuted, isFalse);

    final blocked = _participant(
      alice,
      micMuted: true,
      micBlocked: true,
      voiceBlocked: true,
    );
    final blockedPatch = controller.patchJoinResult(
      rooms: [_roomCard('room_1', liveCount: 0)],
      result: _liveJoinResult(
        participant: blocked,
        live: _live('room_1', [blocked]),
      ),
      showMicUnmutedWhenAllowed: true,
    );

    expect(blockedPatch.micMuted, isTrue);
    expect(blockedPatch.live.participants.single.micMuted, isTrue);
  });

  test('patchStateUpdate mirrors participant flags and merges live roster', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Reducer test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = LiveController(api: api);
    final alice = _participant(_user('alice'));
    final bob = _participant(
      _user('bob'),
      micMuted: true,
      voiceBlocked: true,
      cameraOn: true,
      screenSharing: true,
    );

    final patch = controller.patchStateUpdate(
      live: _live('room_1', [alice]),
      participant: bob,
    );

    expect(patch.micMuted, isTrue);
    expect(patch.headphonesMuted, isFalse);
    expect(patch.voiceBlocked, isTrue);
    expect(patch.cameraOn, isFalse);
    expect(patch.screenSharing, isTrue);
    expect(patch.live?.participantCount, 2);
    expect(patch.live?.participants.map((item) => item.user.id), [
      'alice',
      'bob',
    ]);
  });

  test(
    'patchStateUpdate preserves room display names from the live roster',
    () {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          fail('Reducer test should not call the API: ${request.url}');
        }),
      );
      addTearDown(api.close);
      final controller = LiveController(api: api);
      final existing = _participant(
        _user('alice').copyWith(roomDisplayName: 'Room Alice'),
      );
      final incoming = _participant(_user('alice'), micMuted: true);

      final patch = controller.patchStateUpdate(
        live: _live('room_1', [existing]),
        participant: incoming,
      );

      expect(
        patch.live?.participants.single.user.roomDisplayName,
        'Room Alice',
      );
      expect(patch.live?.participants.single.micMuted, isTrue);
    },
  );

  test(
    'patchModeratedParticipant keeps mic and headphones actions separate',
    () {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          fail('Reducer test should not call the API: ${request.url}');
        }),
      );
      addTearDown(api.close);
      final controller = LiveController(api: api);
      final alice = _user('alice');
      final initial = _participant(alice);

      final headphonesMuted = controller
          .patchModeratedParticipant(
            live: _live('room_1', [initial]),
            participant: initial,
            action: LiveModerationAction.blockVoice,
          )!
          .participants
          .single;
      expect(headphonesMuted.micMuted, isFalse);
      expect(headphonesMuted.micBlocked, isFalse);
      expect(headphonesMuted.voiceBlocked, isFalse);
      expect(headphonesMuted.headphonesMuted, isTrue);
      expect(headphonesMuted.headphonesBlocked, isTrue);
      expect(headphonesMuted.headphonesListening, isFalse);

      final micRestored = controller
          .patchModeratedParticipant(
            live: _live('room_1', [headphonesMuted]),
            participant: headphonesMuted.copyWith(
              micMuted: true,
              micBlocked: true,
              voiceBlocked: true,
            ),
            action: LiveModerationAction.restoreVoice,
          )!
          .participants
          .single;
      expect(micRestored.micMuted, isFalse);
      expect(micRestored.micBlocked, isFalse);
      expect(micRestored.voiceBlocked, isFalse);
      expect(micRestored.headphonesMuted, isTrue);
      expect(micRestored.headphonesBlocked, isTrue);

      final headphonesRestored = controller
          .patchModeratedParticipant(
            live: _live('room_1', [micRestored]),
            participant: micRestored.copyWith(
              micMuted: true,
              micBlocked: true,
              voiceBlocked: true,
            ),
            action: LiveModerationAction.restoreHeadphones,
          )!
          .participants
          .single;
      expect(headphonesRestored.micMuted, isTrue);
      expect(headphonesRestored.micBlocked, isTrue);
      expect(headphonesRestored.voiceBlocked, isTrue);
      expect(headphonesRestored.headphonesMuted, isFalse);
      expect(headphonesRestored.headphonesBlocked, isFalse);
      expect(headphonesRestored.headphonesListening, isTrue);
    },
  );

  test('live kit mic sync is needed only when server changed mic state', () {
    expect(
      shouldSyncLiveKitMicAfterServerPatch(
        requestedMicMuted: null,
        serverMicMuted: true,
      ),
      isFalse,
    );
    expect(
      shouldSyncLiveKitMicAfterServerPatch(
        requestedMicMuted: false,
        serverMicMuted: false,
      ),
      isFalse,
    );
    expect(
      shouldSyncLiveKitMicAfterServerPatch(
        requestedMicMuted: false,
        serverMicMuted: true,
      ),
      isTrue,
    );
  });

  test('removeUserFromLive leaves unrelated state untouched', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Reducer test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = LiveController(api: api);
    final alice = _user('alice');
    final live = _live('room_2', [_participant(alice)]);
    final rooms = [_roomCard('room_1', liveCount: 0)];

    final patch = controller.removeUserFromLive(
      live: live,
      rooms: rooms,
      roomId: 'room_1',
      userId: 'alice',
    );

    expect(patch.live, same(live));
    expect(patch.rooms, same(rooms));
  });

  test('mergeParticipant replaces existing session or appends new one', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Reducer test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = LiveController(api: api);
    final alice = _participant(_user('alice'));
    final bob = _participant(_user('bob'));
    final live = _live('room_1', [alice]);

    final replaced = controller.mergeParticipant(
      live,
      _participant(_user('alice'), micMuted: true),
    )!;
    expect(replaced.participantCount, 1);
    expect(replaced.participants.single.user.id, 'alice');
    expect(replaced.participants.single.micMuted, isTrue);
    expect(replaced.updatedAt, live.updatedAt);

    final appended = controller.mergeParticipant(live, bob)!;
    expect(appended.participantCount, 2);
    expect(appended.participants.map((item) => item.user.id), ['alice', 'bob']);
    expect(appended.updatedAt, live.updatedAt);

    expect(controller.mergeParticipant(null, bob), isNull);
  });

  test('local participant patch does not reject newer reconnect snapshot', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Reducer test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final liveController = LiveController(api: api);
    final roomsController = RoomsController(api: api);
    final initial = _live('room_1', [
      _participant(_user('me')),
      _participant(_user('other'), micMuted: true, headphonesMuted: false),
    ]);

    final locallyPatched = liveController.mergeParticipant(
      initial,
      _participant(_user('me'), micMuted: true),
    )!;
    final reconnectSnapshot = LiveState(
      roomId: initial.roomId,
      participantCount: initial.participantCount,
      participants: [
        _participant(_user('me'), micMuted: true),
        _participant(
          _user('other'),
          micMuted: false,
          headphonesMuted: true,
          headphonesListening: false,
          cameraOn: true,
        ).copyWith(
          cameraMirrored: true,
          connectionState: 'online',
          screenViewers: [_user('viewer')],
        ),
      ],
      updatedAt: initial.updatedAt.add(const Duration(milliseconds: 1)),
    );

    final patch = roomsController.patchSelectedLiveRefreshed(
      live: reconnectSnapshot,
      selectedRoomId: 'room_1',
      currentLive: locallyPatched,
    );

    expect(patch, isNotNull);
    final other = patch!.live.participants.singleWhere(
      (participant) => participant.user.id == 'other',
    );
    expect(other.micMuted, isFalse);
    expect(other.headphonesMuted, isTrue);
    expect(other.headphonesListening, isFalse);
    expect(other.cameraOn, isTrue);
    expect(other.cameraMirrored, isTrue);
    expect(other.screenSharing, isFalse);
    expect(other.connectionState, 'online');
    expect(other.screenViewers.map((viewer) => viewer.id), ['viewer']);
  });
}

Map<String, Object?> _userJson(String id) {
  return {
    'id': id,
    'username': id,
    'display_name': 'User $id',
    'default_avatar_key': 'blue-3',
  };
}

Map<String, Object?> _participantJson({
  required Map<String, Object?> user,
  String connectionState = 'connected',
}) {
  return {
    'live_session_id': 'live_${user['id']}',
    'user': user,
    'joined_at': '2026-06-05T00:00:00Z',
    'mic_muted': false,
    'headphones_muted': false,
    'voice_blocked': false,
    'camera_on': false,
    'screen_sharing': false,
    'connection_state': connectionState,
  };
}

UserSummary _user(String id) {
  return UserSummary(
    id: id,
    username: id,
    displayName: 'User $id',
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
  );
}

LiveParticipant _participant(
  UserSummary user, {
  bool micMuted = false,
  bool micBlocked = false,
  bool headphonesMuted = false,
  bool headphonesBlocked = false,
  bool headphonesListening = true,
  bool voiceBlocked = false,
  bool cameraOn = false,
  bool screenSharing = false,
}) {
  return LiveParticipant(
    liveSessionId: 'live_${user.id}',
    user: user,
    joinedAt: DateTime.utc(2026, 6, 5),
    micMuted: micMuted,
    micBlocked: micBlocked,
    headphonesMuted: headphonesMuted,
    headphonesBlocked: headphonesBlocked,
    headphonesListening: headphonesListening,
    voiceBlocked: voiceBlocked,
    cameraOn: cameraOn,
    screenSharing: screenSharing,
    connectionState: 'connected',
  );
}

LiveJoinResult _liveJoinResult({
  required LiveParticipant participant,
  required LiveState live,
}) {
  return LiveJoinResult(
    liveKit: LiveKitConnectionInfo(
      serverUrl: 'wss://live.example.test',
      token: 'token',
      tokenExpiresAt: DateTime.utc(2026, 6, 5, 1),
      roomName: live.roomId,
    ),
    participant: participant,
    live: live,
  );
}

LiveState _live(String roomId, List<LiveParticipant> participants) {
  return LiveState(
    roomId: roomId,
    participantCount: participants.length,
    participants: participants,
    updatedAt: DateTime.utc(2026, 6, 5),
  );
}

RoomCard _roomCard(
  String id, {
  int liveCount = 0,
  List<UserSummary> livePreview = const [],
}) {
  return RoomCard(
    id: id,
    name: 'Room $id',
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    memberCount: 2,
    liveParticipantCount: liveCount,
    liveAvatarPreview: livePreview,
    lastMessage: null,
    unreadCount: 0,
    updatedAt: DateTime.utc(2026, 6, 5),
  );
}
