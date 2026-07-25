import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:client/src/app/rooms_controller.dart';
import 'package:client/src/protocol/api_client.dart';
import 'package:client/src/protocol/models.dart';

void main() {
  test(
    'loadRoomMembersSnapshot paginates and isolates request errors',
    () async {
      final seen = <Uri>[];
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          seen.add(request.url);
          if (request.url.path == '/api/v1/rooms/room_1/members') {
            final cursor = request.url.queryParameters['cursor'];
            if (cursor == null) {
              return _jsonResponse({
                'members': [_memberJson('user_1')],
                'next_cursor': 'page_2',
              });
            }
            expect(cursor, 'page_2');
            return _jsonResponse({
              'members': [_memberJson('user_2')],
              'next_cursor': '',
            });
          }
          if (request.url.path == '/api/v1/rooms/room_1/live') {
            return _jsonResponse({'live': _liveJson(participantCount: 2)});
          }
          if (request.url.path == '/api/v1/rooms/room_1/join-requests') {
            return http.Response('forbidden', 403);
          }
          fail('Unexpected request: ${request.url}');
        }),
      );
      addTearDown(api.close);

      final snapshot = await RoomsController(api: api).loadRoomMembersSnapshot(
        roomId: 'room_1',
        fallbackLive: _liveState(participantCount: 0),
        includeJoinRequests: true,
      );

      expect(snapshot.members.map((member) => member.user.id), [
        'user_1',
        'user_2',
      ]);
      expect(snapshot.live.participantCount, 2);
      expect(snapshot.joinRequests, isEmpty);
      expect(snapshot.joinRequestsError, isNotNull);
      expect(seen.map((uri) => uri.path), [
        '/api/v1/rooms/room_1/members',
        '/api/v1/rooms/room_1/members',
        '/api/v1/rooms/room_1/live',
        '/api/v1/rooms/room_1/join-requests',
      ]);
    },
  );

  test('loadRoomMembersAndLive falls back when live snapshot fails', () async {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        if (request.url.path == '/api/v1/rooms/room_1/members') {
          return _jsonResponse({
            'members': [_memberJson('user_1')],
            'next_cursor': null,
          });
        }
        if (request.url.path == '/api/v1/rooms/room_1/live') {
          return http.Response('unavailable', 503);
        }
        fail('Unexpected request: ${request.url}');
      }),
    );
    addTearDown(api.close);

    final fallback = _liveState(participantCount: 9);
    final snapshot = await RoomsController(
      api: api,
    ).loadRoomMembersAndLive(roomId: 'room_1', fallbackLive: fallback);

    expect(snapshot.members.single.user.id, 'user_1');
    expect(snapshot.live, same(fallback));
  });

  test('hasPendingJoinRequests uses caller permission gate', () async {
    var requestedJoinRequests = false;
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        requestedJoinRequests = true;
        expect(request.url.path, '/api/v1/rooms/room_1/join-requests');
        return _jsonResponse({
          'requests': [
            {
              'id': 'request_1',
              'status': 'pending',
              'user': _userJson('user_1'),
              'created_at': '2026-06-04T00:00:00Z',
            },
          ],
        });
      }),
    );
    addTearDown(api.close);
    final controller = RoomsController(api: api);

    expect(
      await controller.hasPendingJoinRequests(
        _roomDetail('room_1'),
        canReviewJoinRequests: false,
      ),
      isFalse,
    );
    expect(requestedJoinRequests, isFalse);

    expect(
      await controller.hasPendingJoinRequests(
        _roomDetail('room_1'),
        canReviewJoinRequests: true,
      ),
      isTrue,
    );
    expect(requestedJoinRequests, isTrue);
  });

  test('room list reducers remove cards invites and join requests by id', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Reducer test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = RoomsController(api: api);

    expect(
      controller
          .removeRoomCard([_roomCard('room_1'), _roomCard('room_2')], 'room_1')
          .map((room) => room.id),
      ['room_2'],
    );
    expect(
      controller
          .patchRoomCardsRefreshed(rooms: [_roomCard('room_3')])
          .rooms
          .map((room) => room.id),
      ['room_3'],
    );
    expect(
      controller
          .patchRoomCardUpserted(
            rooms: [_roomCard('room_1'), _roomCard('room_2')],
            room: _roomCard('room_2', name: 'updated room'),
          )
          .rooms
          .map((room) => room.name),
      ['room_1', 'updated room'],
    );
    expect(
      controller
          .patchRoomCardUpdated(
            rooms: [_roomCard('room_1', unreadCount: 7)],
            incoming: _roomCard('room_1', name: 'updated room'),
          )
          .rooms
          .single
          .unreadCount,
      7,
    );
    expect(
      controller
          .removeRoomInvite([
            _roomInvite('invite_1'),
            _roomInvite('invite_2'),
          ], 'invite_2')
          .map((invite) => invite.id),
      ['invite_1'],
    );
    expect(
      controller
          .removeJoinRequest([
            _joinRequest('request_1'),
            _joinRequest('request_2'),
          ], 'request_1')
          .map((request) => request.id),
      ['request_2'],
    );
  });

  test('room detail and live refresh patches guard selected room state', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Reducer test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = RoomsController(api: api);
    final detail = _roomDetail('room_1');

    final detailPatch = controller.patchRoomDetailApplied(
      rooms: [_roomCard('room_2')],
      detail: detail,
    );
    expect(detailPatch.selectedRoom, same(detail));
    expect(detailPatch.rooms.map((room) => room.id), ['room_1', 'room_2']);

    expect(
      controller.patchSelectedRoomDetailRefreshed(
        rooms: const [],
        selectedRoomId: 'room_2',
        detail: detail,
      ),
      isNull,
    );
    final selectedDetailPatch = controller.patchSelectedRoomDetailRefreshed(
      rooms: const [],
      selectedRoomId: 'room_1',
      detail: detail,
    );
    expect(selectedDetailPatch?.selectedRoom, same(detail));
    expect(selectedDetailPatch?.rooms.single.id, 'room_1');

    final live = _liveState(participantCount: 2);
    final livePatch = controller.patchSelectedLiveRefreshed(
      live: live,
      selectedRoomId: 'room_1',
    );
    expect(livePatch?.live, same(live));
    expect(
      controller.patchSelectedLiveRefreshed(
        live: live,
        selectedRoomId: 'room_2',
      ),
      isNull,
    );
    final newerLive = LiveState.fromJson({
      ..._liveJson(participantCount: 3),
      'updated_at': '2026-06-04T00:00:02Z',
    });
    expect(
      controller.patchSelectedLiveRefreshed(
        live: live,
        selectedRoomId: 'room_1',
        currentLive: newerLive,
      ),
      isNull,
    );
  });

  test(
    'room deleted reducer clears selected room state only when selected',
    () {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          fail('Reducer test should not call the API: ${request.url}');
        }),
      );
      addTearDown(api.close);
      final controller = RoomsController(api: api);
      final selectedRoom = _roomDetail('room_1');
      final messages = [_message('message_1', roomId: 'room_1')];
      final live = _liveState(participantCount: 2);

      final selectedPatch = controller.patchRoomDeleted(
        rooms: [_roomCard('room_1'), _roomCard('room_2')],
        selectedRoomId: 'room_1',
        selectedRoom: selectedRoom,
        selectedRoomHasPendingJoinRequests: true,
        messages: messages,
        live: live,
        livePanelOpen: true,
        settingsOpen: true,
        joinedLiveRoomId: 'room_1',
        data: const {'room_id': 'room_1'},
      )!;

      expect(selectedPatch.rooms.map((room) => room.id), ['room_2']);
      expect(selectedPatch.selectedRoomId, isNull);
      expect(selectedPatch.selectedRoom, isNull);
      expect(selectedPatch.selectedRoomHasPendingJoinRequests, isFalse);
      expect(selectedPatch.messages, isEmpty);
      expect(selectedPatch.live, isNull);
      expect(selectedPatch.livePanelOpen, isFalse);
      expect(selectedPatch.settingsOpen, isFalse);
      expect(selectedPatch.joinedLiveRoomId, isNull);
      expect(selectedPatch.wasSelected, isTrue);
      expect(selectedPatch.shouldDisconnectLive, isTrue);

      final retainedRoom = _roomDetail('room_2');
      final retainedPatch = controller.patchRoomDeleted(
        rooms: [_roomCard('room_1'), _roomCard('room_2')],
        selectedRoomId: 'room_2',
        selectedRoom: retainedRoom,
        selectedRoomHasPendingJoinRequests: true,
        messages: messages,
        live: live,
        livePanelOpen: true,
        settingsOpen: true,
        joinedLiveRoomId: 'room_2',
        data: const {'room_id': 'room_1'},
      )!;

      expect(retainedPatch.rooms.map((room) => room.id), ['room_2']);
      expect(retainedPatch.selectedRoomId, 'room_2');
      expect(retainedPatch.selectedRoom, same(retainedRoom));
      expect(retainedPatch.selectedRoomHasPendingJoinRequests, isTrue);
      expect(retainedPatch.messages, same(messages));
      expect(retainedPatch.live, same(live));
      expect(retainedPatch.livePanelOpen, isTrue);
      expect(retainedPatch.settingsOpen, isTrue);
      expect(retainedPatch.joinedLiveRoomId, 'room_2');
      expect(retainedPatch.wasSelected, isFalse);
      expect(retainedPatch.shouldDisconnectLive, isFalse);
      expect(
        controller.patchRoomDeleted(
          rooms: const [],
          selectedRoomId: null,
          selectedRoom: null,
          selectedRoomHasPendingJoinRequests: false,
          messages: const [],
          live: null,
          livePanelOpen: false,
          settingsOpen: false,
          joinedLiveRoomId: null,
          data: const {},
        ),
        isNull,
      );
    },
  );

  test('room role changed reducer patches only the selected room', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Reducer test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = RoomsController(api: api);
    final selectedRoom = _roomDetail('room_1');

    final patch = controller.patchRoomRoleChanged(
      selectedRoom: selectedRoom,
      data: const {'room_id': 'room_1', 'role': 'admin'},
    )!;

    expect(patch.roomId, 'room_1');
    expect(patch.selectedRoom.id, 'room_1');
    expect(patch.selectedRoom.myMembership.role, 'admin');
    expect(selectedRoom.myMembership.role, 'member');

    expect(
      controller.patchRoomRoleChanged(
        selectedRoom: selectedRoom,
        data: const {'room_id': 'room_2', 'role': 'admin'},
      ),
      isNull,
    );
    expect(
      controller.patchRoomRoleChanged(
        selectedRoom: selectedRoom,
        data: const {'room_id': 'room_1'},
      ),
      isNull,
    );
    expect(
      controller.patchRoomRoleChanged(
        selectedRoom: null,
        data: const {'room_id': 'room_1', 'role': 'admin'},
      ),
      isNull,
    );
  });

  test('room open guards reject duplicate and stale async work', () {
    expect(
      shouldSkipRoomOpenRequest(
        loadingRoom: true,
        selectedRoomId: 'room_1',
        roomId: 'room_1',
      ),
      isTrue,
    );
    expect(
      shouldSkipRoomOpenRequest(
        loadingRoom: true,
        selectedRoomId: 'room_2',
        roomId: 'room_1',
      ),
      isFalse,
    );
    expect(
      shouldSkipRoomOpenRequest(
        loadingRoom: false,
        selectedRoomId: 'room_1',
        roomId: 'room_1',
      ),
      isFalse,
    );

    expect(
      canApplyRoomOpenResult(
        requestedRoomId: 'room_1',
        selectedRoomId: 'room_1',
      ),
      isTrue,
    );
    expect(
      canApplyRoomOpenResult(
        requestedRoomId: 'room_1',
        selectedRoomId: 'room_2',
      ),
      isFalse,
    );

    expect(
      shouldShowOptimisticRoomOpenRefreshFailure(
        hasOptimisticDetail: true,
        requestedRoomId: 'room_1',
        selectedRoomId: 'room_1',
      ),
      isTrue,
    );
    expect(
      shouldShowOptimisticRoomOpenRefreshFailure(
        hasOptimisticDetail: false,
        requestedRoomId: 'room_1',
        selectedRoomId: 'room_1',
      ),
      isFalse,
    );
    expect(
      shouldShowOptimisticRoomOpenRefreshFailure(
        hasOptimisticDetail: true,
        requestedRoomId: 'room_1',
        selectedRoomId: 'room_2',
      ),
      isFalse,
    );

    expect(
      shouldFinishRoomOpenLoading(
        requestedRoomId: 'room_1',
        selectedRoomId: 'room_1',
      ),
      isTrue,
    );
    expect(
      shouldFinishRoomOpenLoading(
        requestedRoomId: 'room_1',
        selectedRoomId: null,
      ),
      isFalse,
    );

    expect(
      canApplyLiveRefresh(
        live: _liveState(participantCount: 2),
        selectedRoomId: 'room_1',
      ),
      isTrue,
    );
    expect(
      canApplyLiveRefresh(
        live: _liveState(participantCount: 2),
        selectedRoomId: 'room_2',
      ),
      isFalse,
    );
  });

  test('room list load patches preserve loading semantics', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Reducer test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = RoomsController(api: api);
    final existingRooms = [_roomCard('room_1')];
    final loadedRooms = [_roomCard('room_2')];

    final started = controller.patchRoomListLoadStarted(rooms: existingRooms);
    expect(started.rooms, same(existingRooms));
    expect(started.loading, isTrue);
    expect(started.error, isNull);

    final succeeded = controller.patchRoomListLoadSucceeded(rooms: loadedRooms);
    expect(succeeded.rooms.map((room) => room.id), ['room_2']);
    expect(succeeded.loading, isFalse);
    expect(succeeded.error, isNull);

    final failed = controller.patchRoomListLoadFailed(
      rooms: existingRooms,
      failure: 'load failed',
    );
    expect(failed.rooms, same(existingRooms));
    expect(failed.loading, isFalse);
    expect(failed.error, '操作失败，请稍后重试');
  });

  test('room open state patches preserve Home open-room semantics', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Reducer test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = RoomsController(api: api);
    final currentRoom = _roomDetail('room_1');
    final optimisticRoom = _roomDetail('room_2');
    final currentMessages = [_message('message_1', roomId: 'room_1')];
    final currentLive = _liveState(participantCount: 2);

    final loadingPatch = controller.patchRoomOpenStarted(
      roomId: 'room_2',
      currentSelectedRoom: currentRoom,
      currentMessages: currentMessages,
      currentLive: currentLive,
      currentLivePanelOpen: true,
      joinLive: false,
    );
    expect(loadingPatch.settingsOpen, isFalse);
    expect(loadingPatch.selectedRoomId, 'room_2');
    expect(loadingPatch.selectedRoom, same(currentRoom));
    expect(loadingPatch.loadingRoom, isTrue);
    expect(loadingPatch.error, isNull);
    expect(loadingPatch.selectedRoomHasPendingJoinRequests, isFalse);
    expect(loadingPatch.messages, same(currentMessages));
    expect(loadingPatch.live, same(currentLive));
    expect(loadingPatch.livePanelOpen, isFalse);

    final optimisticPatch = controller.patchRoomOpenStarted(
      roomId: 'room_2',
      currentSelectedRoom: currentRoom,
      currentMessages: currentMessages,
      currentLive: currentLive,
      currentLivePanelOpen: true,
      joinLive: true,
      optimisticDetail: optimisticRoom,
    );
    expect(optimisticPatch.selectedRoom, same(optimisticRoom));
    expect(optimisticPatch.messages, isEmpty);
    expect(optimisticPatch.live, same(optimisticRoom.live));
    expect(optimisticPatch.livePanelOpen, isTrue);

    final snapshot = RoomOpenSnapshot(
      detail: optimisticRoom,
      messages: [_message('message_2', roomId: 'room_2')],
      live: _liveState(participantCount: 4),
    );
    final noJoinEffects = roomOpenSucceededEffects(
      snapshot: snapshot,
      joinLive: false,
    );
    expect(noJoinEffects.joinRequestBadgeRoom, same(optimisticRoom));
    expect(noJoinEffects.joinLiveSource, isNull);

    final joinEffects = roomOpenSucceededEffects(
      snapshot: snapshot,
      joinLive: true,
    );
    expect(joinEffects.joinRequestBadgeRoom, same(optimisticRoom));
    expect(joinEffects.joinLiveSource, 'room_card_speaker');

    final successPatch = controller.patchRoomOpenSucceeded(
      currentSettingsOpen: true,
      currentLoadingRoom: true,
      currentError: 'kept',
      currentSelectedRoomHasPendingJoinRequests: true,
      snapshot: snapshot,
      joinLive: true,
    );
    expect(successPatch.settingsOpen, isTrue);
    expect(successPatch.selectedRoomId, 'room_2');
    expect(successPatch.selectedRoom, same(optimisticRoom));
    expect(successPatch.loadingRoom, isTrue);
    expect(successPatch.error, 'kept');
    expect(successPatch.selectedRoomHasPendingJoinRequests, isTrue);
    expect(successPatch.messages, same(snapshot.messages));
    expect(successPatch.live, same(snapshot.live));
    expect(successPatch.livePanelOpen, isTrue);

    final failedPatch = controller.patchRoomOpenFailed(
      settingsOpen: true,
      selectedRoomId: 'room_2',
      selectedRoom: optimisticRoom,
      loadingRoom: true,
      selectedRoomHasPendingJoinRequests: true,
      messages: snapshot.messages,
      live: snapshot.live,
      livePanelOpen: true,
      failure: 'open failed',
    );
    expect(failedPatch.settingsOpen, isTrue);
    expect(failedPatch.selectedRoomId, 'room_2');
    expect(failedPatch.selectedRoom, same(optimisticRoom));
    expect(failedPatch.loadingRoom, isTrue);
    expect(failedPatch.error, '操作失败，请稍后重试');
    expect(failedPatch.selectedRoomHasPendingJoinRequests, isTrue);
    expect(failedPatch.messages, same(snapshot.messages));
    expect(failedPatch.live, same(snapshot.live));
    expect(failedPatch.livePanelOpen, isTrue);

    final finishedPatch = controller.patchRoomOpenFinished(
      settingsOpen: true,
      selectedRoomId: 'room_2',
      selectedRoom: optimisticRoom,
      error: 'stale error',
      selectedRoomHasPendingJoinRequests: true,
      messages: snapshot.messages,
      live: snapshot.live,
      livePanelOpen: true,
    );
    expect(finishedPatch.settingsOpen, isTrue);
    expect(finishedPatch.selectedRoomId, 'room_2');
    expect(finishedPatch.selectedRoom, same(optimisticRoom));
    expect(finishedPatch.loadingRoom, isFalse);
    expect(finishedPatch.error, 'stale error');
    expect(finishedPatch.selectedRoomHasPendingJoinRequests, isTrue);
    expect(finishedPatch.messages, same(snapshot.messages));
    expect(finishedPatch.live, same(snapshot.live));
    expect(finishedPatch.livePanelOpen, isTrue);
  });

  test('room members dialog load patches preserve dialog state semantics', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Patch test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = RoomsController(api: api);
    final members = [_roomMember('user_1')];
    final requests = [_joinRequest('request_1')];
    final live = _liveState(participantCount: 1);

    final started = controller.patchRoomMembersLoadStarted(
      members: members,
      requests: requests,
      live: live,
      changed: true,
      busyRequestIds: const {'request_1'},
    );
    expect(started.members, same(members));
    expect(started.requests, same(requests));
    expect(started.live, same(live));
    expect(started.loading, isTrue);
    expect(started.changed, isTrue);
    expect(started.error, isNull);
    expect(started.requestError, isNull);
    expect(started.busyRequestIds, {'request_1'});

    final snapshot = RoomMembersSnapshot(
      members: [_roomMember('user_2')],
      live: _liveState(participantCount: 2),
      joinRequests: [_joinRequest('request_2')],
      joinRequestsError: 'forbidden',
    );
    final succeeded = controller.patchRoomMembersLoadSucceeded(
      snapshot: snapshot,
      changed: false,
      busyRequestIds: const {'request_2'},
    );
    expect(succeeded.members, same(snapshot.members));
    expect(succeeded.requests, same(snapshot.joinRequests));
    expect(succeeded.live, same(snapshot.live));
    expect(succeeded.loading, isFalse);
    expect(succeeded.changed, isFalse);
    expect(succeeded.error, isNull);
    expect(succeeded.requestError, 'forbidden');
    expect(succeeded.hasPendingRequests, isTrue);
    expect(succeeded.shouldNotifyPendingRequests, isFalse);

    final failed = controller.patchRoomMembersLoadFailed(
      members: members,
      requests: requests,
      live: live,
      changed: true,
      busyRequestIds: const {'request_1'},
      failure: Exception('load failed'),
    );
    expect(failed.members, same(members));
    expect(failed.requests, same(requests));
    expect(failed.live, same(live));
    expect(failed.loading, isFalse);
    expect(failed.changed, isTrue);
    expect(failed.error, '操作失败，请稍后重试');
    expect(failed.requestError, isNull);
    expect(failed.busyRequestIds, {'request_1'});
  });

  test(
    'room members dialog reload patches isolate member and request errors',
    () {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          fail('Patch test should not call the API: ${request.url}');
        }),
      );
      addTearDown(api.close);
      final controller = RoomsController(api: api);
      final members = [_roomMember('user_1')];
      final requests = [_joinRequest('request_1')];
      final live = _liveState(participantCount: 1);
      final liveSnapshot = RoomMembersLiveSnapshot(
        members: [_roomMember('user_2')],
        live: _liveState(participantCount: 3),
      );

      final membersReloaded = controller.patchRoomMembersAndLiveReloadSucceeded(
        snapshot: liveSnapshot,
        requests: requests,
        loading: true,
        changed: true,
        requestError: 'request error',
        busyRequestIds: const {'request_1'},
      );
      expect(membersReloaded.members, same(liveSnapshot.members));
      expect(membersReloaded.requests, same(requests));
      expect(membersReloaded.live, same(liveSnapshot.live));
      expect(membersReloaded.loading, isTrue);
      expect(membersReloaded.changed, isTrue);
      expect(membersReloaded.error, isNull);
      expect(membersReloaded.requestError, 'request error');
      expect(membersReloaded.busyRequestIds, {'request_1'});

      final membersFailed = controller.patchRoomMembersAndLiveReloadFailed(
        members: members,
        requests: requests,
        live: live,
        loading: false,
        changed: true,
        requestError: 'request error',
        busyRequestIds: const {'request_1'},
        failure: Exception('members failed'),
      );
      expect(membersFailed.members, same(members));
      expect(membersFailed.requests, same(requests));
      expect(membersFailed.live, same(live));
      expect(membersFailed.loading, isFalse);
      expect(membersFailed.changed, isTrue);
      expect(membersFailed.error, '操作失败，请稍后重试');
      expect(membersFailed.requestError, 'request error');

      final requestsReloaded = controller.patchRoomJoinRequestsReloadSucceeded(
        members: members,
        live: live,
        requests: [_joinRequest('request_2')],
        loading: false,
        changed: false,
        busyRequestIds: const {'request_1'},
      );
      expect(requestsReloaded.members, same(members));
      expect(requestsReloaded.requests.map((request) => request.id), [
        'request_2',
      ]);
      expect(requestsReloaded.live, same(live));
      expect(requestsReloaded.loading, isFalse);
      expect(requestsReloaded.changed, isFalse);
      expect(requestsReloaded.error, isNull);
      expect(requestsReloaded.requestError, isNull);
      expect(requestsReloaded.hasPendingRequests, isTrue);
      expect(requestsReloaded.shouldNotifyPendingRequests, isTrue);

      final requestsFailed = controller.patchRoomJoinRequestsReloadFailed(
        members: members,
        requests: requests,
        live: live,
        loading: false,
        changed: false,
        busyRequestIds: const {'request_1'},
        failure: Exception('requests failed'),
      );
      expect(requestsFailed.members, same(members));
      expect(requestsFailed.requests, same(requests));
      expect(requestsFailed.live, same(live));
      expect(requestsFailed.error, isNull);
      expect(requestsFailed.requestError, '操作失败，请稍后重试');
      expect(requestsFailed.busyRequestIds, {'request_1'});
    },
  );

  test('room members dialog review patches update busy ids and requests', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Patch test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = RoomsController(api: api);
    final members = [_roomMember('user_1')];
    final requests = [_joinRequest('request_1'), _joinRequest('request_2')];
    final live = _liveState(participantCount: 1);

    final started = controller.patchJoinRequestReviewStarted(
      members: members,
      requests: requests,
      live: live,
      loading: false,
      changed: false,
      requestId: 'request_1',
      busyRequestIds: const {'request_2'},
    );
    expect(started.members, same(members));
    expect(started.requests, requests);
    expect(started.live, same(live));
    expect(started.loading, isFalse);
    expect(started.changed, isFalse);
    expect(started.error, isNull);
    expect(started.requestError, isNull);
    expect(started.busyRequestIds, {'request_1', 'request_2'});

    final succeeded = controller.patchJoinRequestReviewSucceeded(
      members: members,
      requests: started.requests,
      live: live,
      loading: false,
      changed: false,
      error: 'kept',
      requestError: null,
      requestId: 'request_1',
      busyRequestIds: started.busyRequestIds,
    );
    expect(succeeded.members, same(members));
    expect(succeeded.requests.map((request) => request.id), ['request_2']);
    expect(succeeded.live, same(live));
    expect(succeeded.loading, isFalse);
    expect(succeeded.changed, isTrue);
    expect(succeeded.error, 'kept');
    expect(succeeded.requestError, isNull);
    expect(succeeded.busyRequestIds, {'request_2'});
    expect(succeeded.hasPendingRequests, isTrue);

    final failed = controller.patchJoinRequestReviewFailed(
      members: members,
      requests: started.requests,
      live: live,
      loading: false,
      changed: true,
      error: 'kept',
      requestId: 'request_1',
      busyRequestIds: started.busyRequestIds,
      failure: Exception('review failed'),
    );
    expect(failed.members, same(members));
    expect(failed.requests, started.requests);
    expect(failed.live, same(live));
    expect(failed.loading, isFalse);
    expect(failed.changed, isTrue);
    expect(failed.error, 'kept');
    expect(failed.requestError, '操作失败，请稍后重试');
    expect(failed.busyRequestIds, {'request_2'});
  });

  test(
    'patchLiveSnapshot re-inserts self when a stale snapshot drops the local '
    'participant for the joined room',
    () async {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          fail('Patch test should not call the API: ${request.url}');
        }),
      );
      addTearDown(api.close);
      final controller = RoomsController(api: api);

      final previousLive = LiveState.fromJson(
        _liveJsonWithParticipants(['me', 'other']),
      );

      // Snapshot computed before we joined: it only lists "other".
      final patch = controller.patchLiveSnapshot(
        rooms: [_roomCard('room_1')],
        selectedRoomId: 'room_1',
        data: _liveSnapshotData(['other']),
        joinedLiveRoomId: 'room_1',
        currentUserId: 'me',
        previousLive: previousLive,
      );

      expect(patch, isNotNull);
      final ids = patch!.selectedLive!.participants
          .map((participant) => participant.user.id)
          .toList();
      expect(ids, containsAll(<String>['me', 'other']));
      expect(patch.selectedLive!.participantCount, 2);
    },
  );

  test(
    'patchLiveSnapshot does not re-insert self when not joined to the room',
    () async {
      final api = GangApiClient(
        baseUrl: 'http://example.test/api/v1',
        accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
        httpClient: MockClient((request) async {
          fail('Patch test should not call the API: ${request.url}');
        }),
      );
      addTearDown(api.close);
      final controller = RoomsController(api: api);

      final previousLive = LiveState.fromJson(
        _liveJsonWithParticipants(['me', 'other']),
      );

      final patch = controller.patchLiveSnapshot(
        rooms: [_roomCard('room_1')],
        selectedRoomId: 'room_1',
        data: _liveSnapshotData(['other']),
        joinedLiveRoomId: null,
        currentUserId: 'me',
        previousLive: previousLive,
      );

      expect(patch, isNotNull);
      final ids = patch!.selectedLive!.participants
          .map((participant) => participant.user.id)
          .toList();
      expect(ids, ['other']);
    },
  );

  test('patchLiveSnapshot preserves participant room display names', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Patch test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = RoomsController(api: api);
    final previousJson = _liveJsonWithParticipants(['me']);
    final participants = previousJson['participants']! as List<Object?>;
    (participants.single! as Map<String, Object?>)['room_display_name'] =
        'Room Me';
    final previousLive = LiveState.fromJson(previousJson);

    final patch = controller.patchLiveSnapshot(
      rooms: [_roomCard('room_1')],
      selectedRoomId: 'room_1',
      data: _liveSnapshotData(['me']),
      previousLive: previousLive,
    );

    expect(patch, isNotNull);
    expect(
      patch!.selectedLive!.participants.single.user.roomDisplayName,
      'Room Me',
    );
  });

  test('patchLiveSnapshot ignores an older selected-room live snapshot', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Patch test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = RoomsController(api: api);
    final previousLive = LiveState.fromJson({
      ..._liveJsonWithParticipants(['me', 'other']),
      'updated_at': '2026-06-04T00:00:02Z',
    });
    final staleData = _liveSnapshotData(['other']);

    final patch = controller.patchLiveSnapshot(
      rooms: [_roomCard('room_1', liveParticipantCount: 2)],
      selectedRoomId: 'room_1',
      data: staleData,
      joinedLiveRoomId: 'room_1',
      currentUserId: 'me',
      previousLive: previousLive,
    );

    expect(patch, isNotNull);
    expect(patch!.selectedLive, isNull);
    expect(patch.rooms.single.liveParticipantCount, 2);
  });

  test('patchRoomUpdated updates selected room counts and reload hint', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Patch test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = RoomsController(api: api);

    final patch = controller.patchRoomUpdated(
      rooms: [_roomCard('room_1', unreadCount: 5, onlineMemberCount: 1)],
      incoming: _roomCard(
        'room_1',
        name: 'Renamed',
        memberCount: 4,
        onlineMemberCount: 2,
        aiVoiceAnnouncementsEnabled: false,
      ),
      selectedRoom: _roomDetail('room_1', onlineMemberCount: 1),
    );

    expect(patch.rooms.single.unreadCount, 5);
    expect(patch.rooms.single.onlineMemberCount, 2);
    expect(patch.selectedRoom?.name, 'Renamed');
    expect(patch.selectedRoom?.memberCount, 4);
    expect(patch.selectedRoom?.onlineMemberCount, 2);
    expect(patch.rooms.single.aiVoiceAnnouncementsEnabled, isFalse);
    expect(patch.selectedRoom?.aiVoiceAnnouncementsEnabled, isFalse);
    expect(patch.shouldReloadMembers, isTrue);
  });

  test('patchRoomUpdated keeps selected room fresh messages unread', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Patch test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = RoomsController(api: api);

    final patch = controller.patchRoomUpdated(
      rooms: [_roomCard('room_1', unreadCount: 0, lastMessageId: 'msg_1')],
      incoming: _roomCard('room_1', lastMessageId: 'msg_2'),
      selectedRoom: _roomDetail('room_1'),
    );

    expect(patch.rooms.single.unreadCount, 1);
  });

  test('patchRoomUpdated increments unread for fresh messages off-room', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Patch test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = RoomsController(api: api);

    final patch = controller.patchRoomUpdated(
      rooms: [_roomCard('room_1', unreadCount: 5, lastMessageId: 'msg_1')],
      incoming: _roomCard('room_1', lastMessageId: 'msg_2'),
      selectedRoom: _roomDetail('room_2'),
    );

    expect(patch.rooms.single.unreadCount, 6);

    final sameLastMessage = controller.patchRoomUpdated(
      rooms: patch.rooms,
      incoming: _roomCard('room_1', lastMessageId: 'msg_2'),
      selectedRoom: _roomDetail('room_2'),
    );

    expect(sameLastMessage.rooms.single.unreadCount, 6);
  });

  test('patchRoomUpdated prefers server unread count when present', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Patch test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = RoomsController(api: api);

    final patch = controller.patchRoomUpdated(
      rooms: [_roomCard('room_1', unreadCount: 5, lastMessageId: 'msg_1')],
      incoming: _roomCard(
        'room_1',
        unreadCount: 7,
        hasUnreadCount: true,
        lastMessageId: 'msg_2',
      ),
      selectedRoom: _roomDetail('room_2'),
    );

    expect(patch.rooms.single.unreadCount, 7);
  });

  test('patchRoomUpdated reorders rooms by latest activity time', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Patch test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = RoomsController(api: api);

    final patch = controller.patchRoomUpdated(
      rooms: [
        _roomCard(
          'room_old',
          unreadCount: 1,
          lastMessageId: 'msg_old',
          lastMessageAt: DateTime.utc(2026, 6, 4, 10),
        ),
        _roomCard(
          'room_target',
          unreadCount: 1,
          lastMessageId: 'msg_previous',
          lastMessageAt: DateTime.utc(2026, 6, 4, 9),
        ),
      ],
      incoming: _roomCard(
        'room_target',
        lastMessageId: 'msg_new',
        lastMessageAt: DateTime.utc(2026, 6, 4, 11),
      ),
      selectedRoom: _roomDetail('room_other'),
    );

    expect(patch.rooms.map((room) => room.id), ['room_target', 'room_old']);
    expect(patch.rooms.first.unreadCount, 2);
  });

  test('patchRoomUnreadCleared clears a single room count', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Patch test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = RoomsController(api: api);

    final patch = controller.patchRoomUnreadCleared(
      rooms: [
        _roomCard('room_1', unreadCount: 5),
        _roomCard('room_2', unreadCount: 2),
      ],
      roomId: 'room_1',
    );

    expect(patch.rooms.map((room) => room.id), ['room_2', 'room_1']);
    expect(patch.rooms[0].unreadCount, 2);
    expect(patch.rooms[1].unreadCount, 0);
  });

  test('room ordering puts pinned first and red unread before gray unread', () {
    final api = GangApiClient(
      baseUrl: 'http://example.test/api/v1',
      accessTokenProvider: ({bool forceRefresh = false}) async => 'token',
      httpClient: MockClient((request) async {
        fail('Ordering test should not call the API: ${request.url}');
      }),
    );
    addTearDown(api.close);
    final controller = RoomsController(api: api);

    final ordered = controller
        .patchRoomCardsRefreshed(
          rooms: [
            _roomCard('normal'),
            _roomCard('gray', unreadCount: 2, notificationPolicy: 'silent'),
            _roomCard('red', unreadCount: 1),
            _roomCard('pinned', isPinned: true),
          ],
        )
        .rooms;

    expect(ordered.map((room) => room.id), ['pinned', 'red', 'gray', 'normal']);
  });
}

RoomCard _roomCard(
  String id, {
  String? name,
  int unreadCount = 0,
  int memberCount = 3,
  int onlineMemberCount = 0,
  String? lastMessageId,
  String notificationPolicy = 'all',
  bool isPinned = false,
  bool hasUnreadCount = false,
  int liveParticipantCount = 0,
  DateTime? lastMessageAt,
  DateTime? updatedAt,
  bool aiVoiceAnnouncementsEnabled = false,
}) {
  return RoomCard(
    id: id,
    name: name ?? id,
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    notificationPolicy: notificationPolicy,
    isPinned: isPinned,
    memberCount: memberCount,
    onlineMemberCount: onlineMemberCount,
    liveParticipantCount: liveParticipantCount,
    liveAvatarPreview: const [],
    lastMessage: lastMessageId == null
        ? null
        : LastMessagePreview(
            id: lastMessageId,
            type: 'text',
            senderDisplayName: 'Alice',
            bodyPreview: 'hello',
            createdAt: lastMessageAt ?? DateTime.utc(2026, 6, 4),
          ),
    unreadCount: unreadCount,
    hasUnreadCount: hasUnreadCount,
    aiVoiceAnnouncementsEnabled: aiVoiceAnnouncementsEnabled,
    updatedAt: updatedAt ?? DateTime.utc(2026, 6, 4),
  );
}

RoomInvite _roomInvite(String id) {
  return RoomInvite(
    id: id,
    status: 'pending',
    room: _publicRoom('room_$id'),
    inviter: _user('inviter_$id'),
    createdAt: DateTime.utc(2026, 6, 4),
  );
}

JoinRequest _joinRequest(String id) {
  return JoinRequest(
    id: id,
    status: 'pending',
    user: _user('user_$id'),
    createdAt: DateTime.utc(2026, 6, 4),
  );
}

RoomMember _roomMember(String userId) {
  return RoomMember(
    user: _user(userId),
    role: 'member',
    joinedAt: DateTime.utc(2026, 6, 4),
    isOnline: true,
  );
}

RoomDetail _roomDetail(
  String id, {
  String role = 'member',
  int onlineMemberCount = 0,
}) {
  return RoomDetail(
    id: id,
    name: id,
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    memberCount: 3,
    onlineMemberCount: onlineMemberCount,
    myMembership: RoomMembership(
      joinedAt: DateTime.utc(2026, 6, 4),
      role: role,
    ),
    live: _liveState(participantCount: 0),
    createdAt: DateTime.utc(2026, 6, 4),
    updatedAt: DateTime.utc(2026, 6, 4),
  );
}

Message _message(String id, {required String roomId}) {
  return Message(
    id: id,
    roomId: roomId,
    sender: _user('sender'),
    clientMessageId: 'client_$id',
    body: 'hello',
    createdAt: DateTime.utc(2026, 6, 4),
  );
}

PublicRoom _publicRoom(String id) {
  return PublicRoom(
    id: id,
    rid: 'R$id',
    name: id,
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    visibility: 'public',
    joinPolicy: 'approval_required',
    memberCount: 3,
    liveParticipantCount: 0,
    joined: false,
    joinState: 'none',
  );
}

UserSummary _user(String id) {
  return UserSummary(
    id: id,
    username: id,
    displayName: id,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
  );
}

http.Response _jsonResponse(Map<String, Object?> body) {
  return http.Response(
    jsonEncode(body),
    200,
    headers: {'content-type': 'application/json'},
  );
}

Map<String, Object?> _memberJson(String userId) {
  return {
    'user': _userJson(userId),
    'role': 'member',
    'joined_at': '2026-06-04T00:00:00Z',
    'is_online': true,
  };
}

Map<String, Object?> _userJson(String userId) {
  return {
    'id': userId,
    'username': userId,
    'display_name': userId,
    'avatar_url': null,
    'default_avatar_key': 'blue-1',
  };
}

LiveState _liveState({required int participantCount}) {
  return LiveState.fromJson(_liveJson(participantCount: participantCount));
}

Map<String, Object?> _liveJson({required int participantCount}) {
  return {
    'room_id': 'room_1',
    'participant_count': participantCount,
    'participants': const [],
    'updated_at': '2026-06-04T00:00:00Z',
  };
}

Map<String, Object?> _participantJson(String userId) {
  return {
    'live_session_id': 'session_$userId',
    'user': {
      'id': userId,
      'username': userId,
      'display_name': userId,
      'avatar_url': null,
      'default_avatar_key': 'blue-3',
    },
    'joined_at': '2026-06-04T00:00:00Z',
    'mic_muted': false,
    'headphones_muted': false,
    'voice_blocked': false,
    'camera_on': false,
    'screen_sharing': false,
    'connection_state': 'connected',
  };
}

Map<String, Object?> _liveJsonWithParticipants(List<String> userIds) {
  return {
    'room_id': 'room_1',
    'participant_count': userIds.length,
    'participants': userIds.map(_participantJson).toList(),
    'updated_at': '2026-06-04T00:00:00Z',
  };
}

Map<String, dynamic> _liveSnapshotData(List<String> userIds) {
  return {
    'room_id': 'room_1',
    'participant_count': userIds.length,
    'live': _liveJsonWithParticipants(userIds),
  };
}
