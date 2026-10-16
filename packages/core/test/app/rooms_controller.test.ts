import { describe, expect, it } from 'vitest';

import {
  RoomMembersLiveSnapshot,
  RoomMembersSnapshot,
  RoomOpenSnapshot,
  RoomsController,
  canApplyLiveRefresh,
  canApplyRoomOpenResult,
  roomOpenSucceededEffects,
  shouldFinishRoomOpenLoading,
  shouldShowOptimisticRoomOpenRefreshFailure,
  shouldSkipRoomOpenRequest,
} from '../../src/app/rooms_controller';
import type { JsonMap } from '../../src/lib/json';
import { GangApiClient } from '../../src/protocol/api_client';
import {
  JoinRequest,
  LastMessagePreview,
  LiveState,
  Message,
  PublicRoom,
  RoomCard,
  RoomDetail,
  RoomInvite,
  RoomMember,
  RoomMembership,
  UserSummary,
} from '../../src/protocol/models';
import { MockClient, jsonResponse, mockResponse, type MockHandler } from '../support/fake_http_client';

function makeApi(handler: MockHandler): GangApiClient {
  return new GangApiClient({
    baseUrl: 'http://example.test/api/v1',
    accessTokenProvider: async () => 'token',
    httpClient: new MockClient(handler),
  });
}

function reducerController(): RoomsController {
  return new RoomsController({
    api: makeApi((request) => {
      throw new Error(`Reducer test should not call the API: ${request.url}`);
    }),
  });
}

describe('RoomsController', () => {
  it('loadRoomMembersSnapshot paginates and isolates request errors', async () => {
    const seen: URL[] = [];
    const api = makeApi((request) => {
      seen.push(request.uri);
      if (request.uri.pathname === '/api/v1/rooms/room_1/members') {
        const cursor = request.queryParameters['cursor'];
        if (cursor == null) {
          return jsonResponse({ members: [_memberJson('user_1')], next_cursor: 'page_2' });
        }
        expect(cursor).toBe('page_2');
        return jsonResponse({ members: [_memberJson('user_2')], next_cursor: '' });
      }
      if (request.uri.pathname === '/api/v1/rooms/room_1/live') {
        return jsonResponse({ live: _liveJson({ participantCount: 2 }) });
      }
      if (request.uri.pathname === '/api/v1/rooms/room_1/join-requests') {
        return mockResponse('forbidden', 403);
      }
      throw new Error(`Unexpected request: ${request.url}`);
    });

    const snapshot = await new RoomsController({ api }).loadRoomMembersSnapshot({
      roomId: 'room_1',
      fallbackLive: _liveState({ participantCount: 0 }),
      includeJoinRequests: true,
    });

    expect(snapshot.members.map((member) => member.user.id)).toEqual(['user_1', 'user_2']);
    expect(snapshot.live.participantCount).toBe(2);
    expect(snapshot.joinRequests).toEqual([]);
    expect(snapshot.joinRequestsError).not.toBeNull();
    expect(seen.map((uri) => uri.pathname)).toEqual([
      '/api/v1/rooms/room_1/members',
      '/api/v1/rooms/room_1/members',
      '/api/v1/rooms/room_1/live',
      '/api/v1/rooms/room_1/join-requests',
    ]);
  });

  it('loadRoomMembersAndLive falls back when live snapshot fails', async () => {
    const api = makeApi((request) => {
      if (request.uri.pathname === '/api/v1/rooms/room_1/members') {
        return jsonResponse({ members: [_memberJson('user_1')], next_cursor: null });
      }
      if (request.uri.pathname === '/api/v1/rooms/room_1/live') {
        return mockResponse('unavailable', 503);
      }
      throw new Error(`Unexpected request: ${request.url}`);
    });

    const fallback = _liveState({ participantCount: 9 });
    const snapshot = await new RoomsController({ api }).loadRoomMembersAndLive({
      roomId: 'room_1',
      fallbackLive: fallback,
    });

    expect(snapshot.members[0]!.user.id).toBe('user_1');
    expect(snapshot.live).toBe(fallback);
  });

  it('hasPendingJoinRequests uses caller permission gate', async () => {
    let requestedJoinRequests = false;
    const api = makeApi((request) => {
      requestedJoinRequests = true;
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/join-requests');
      return jsonResponse({
        requests: [
          {
            id: 'request_1',
            status: 'pending',
            user: _userJson('user_1'),
            created_at: '2026-06-04T00:00:00Z',
          },
        ],
      });
    });
    const controller = new RoomsController({ api });

    expect(await controller.hasPendingJoinRequests(_roomDetail('room_1'), { canReviewJoinRequests: false })).toBe(
      false,
    );
    expect(requestedJoinRequests).toBe(false);

    expect(await controller.hasPendingJoinRequests(_roomDetail('room_1'), { canReviewJoinRequests: true })).toBe(true);
    expect(requestedJoinRequests).toBe(true);
  });

  it('room list reducers remove cards invites and join requests by id', () => {
    const controller = reducerController();

    expect(controller.removeRoomCard([_roomCard('room_1'), _roomCard('room_2')], 'room_1').map((room) => room.id)).toEqual(
      ['room_2'],
    );
    expect(controller.patchRoomCardsRefreshed({ rooms: [_roomCard('room_3')] }).rooms.map((room) => room.id)).toEqual([
      'room_3',
    ]);
    expect(
      controller
        .patchRoomCardUpserted({
          rooms: [_roomCard('room_1'), _roomCard('room_2')],
          room: _roomCard('room_2', { name: 'updated room' }),
        })
        .rooms.map((room) => room.name),
    ).toEqual(['room_1', 'updated room']);
    expect(
      controller.patchRoomCardUpdated({
        rooms: [_roomCard('room_1', { unreadCount: 7 })],
        incoming: _roomCard('room_1', { name: 'updated room' }),
      }).rooms[0]!.unreadCount,
    ).toBe(7);
    expect(
      controller.removeRoomInvite([_roomInvite('invite_1'), _roomInvite('invite_2')], 'invite_2').map((invite) => invite.id),
    ).toEqual(['invite_1']);
    expect(
      controller
        .removeJoinRequest([_joinRequest('request_1'), _joinRequest('request_2')], 'request_1')
        .map((request) => request.id),
    ).toEqual(['request_2']);
  });

  it('room detail and live refresh patches guard selected room state', () => {
    const controller = reducerController();
    const detail = _roomDetail('room_1');

    const detailPatch = controller.patchRoomDetailApplied({ rooms: [_roomCard('room_2')], detail });
    expect(detailPatch.selectedRoom).toBe(detail);
    expect(detailPatch.rooms.map((room) => room.id)).toEqual(['room_1', 'room_2']);

    expect(controller.patchSelectedRoomDetailRefreshed({ rooms: [], selectedRoomId: 'room_2', detail })).toBeNull();
    const selectedDetailPatch = controller.patchSelectedRoomDetailRefreshed({
      rooms: [],
      selectedRoomId: 'room_1',
      detail,
    });
    expect(selectedDetailPatch?.selectedRoom).toBe(detail);
    expect(selectedDetailPatch?.rooms[0]!.id).toBe('room_1');

    const live = _liveState({ participantCount: 2 });
    const livePatch = controller.patchSelectedLiveRefreshed({ live, selectedRoomId: 'room_1' });
    expect(livePatch?.live).toBe(live);
    expect(controller.patchSelectedLiveRefreshed({ live, selectedRoomId: 'room_2' })).toBeNull();
    const newerLive = LiveState.fromJson({
      ..._liveJson({ participantCount: 3 }),
      updated_at: '2026-06-04T00:00:02Z',
    });
    expect(
      controller.patchSelectedLiveRefreshed({ live, selectedRoomId: 'room_1', currentLive: newerLive }),
    ).toBeNull();
  });

  it('room deleted reducer clears selected room state only when selected', () => {
    const controller = reducerController();
    const selectedRoom = _roomDetail('room_1');
    const messages = [_message('message_1', { roomId: 'room_1' })];
    const live = _liveState({ participantCount: 2 });

    const selectedPatch = controller.patchRoomDeleted({
      rooms: [_roomCard('room_1'), _roomCard('room_2')],
      selectedRoomId: 'room_1',
      selectedRoom,
      selectedRoomHasPendingJoinRequests: true,
      messages,
      live,
      livePanelOpen: true,
      settingsOpen: true,
      joinedLiveRoomId: 'room_1',
      data: { room_id: 'room_1' },
    })!;

    expect(selectedPatch.rooms.map((room) => room.id)).toEqual(['room_2']);
    expect(selectedPatch.selectedRoomId).toBeNull();
    expect(selectedPatch.selectedRoom).toBeNull();
    expect(selectedPatch.selectedRoomHasPendingJoinRequests).toBe(false);
    expect(selectedPatch.messages).toEqual([]);
    expect(selectedPatch.live).toBeNull();
    expect(selectedPatch.livePanelOpen).toBe(false);
    expect(selectedPatch.settingsOpen).toBe(false);
    expect(selectedPatch.joinedLiveRoomId).toBeNull();
    expect(selectedPatch.wasSelected).toBe(true);
    expect(selectedPatch.shouldDisconnectLive).toBe(true);

    const retainedRoom = _roomDetail('room_2');
    const retainedPatch = controller.patchRoomDeleted({
      rooms: [_roomCard('room_1'), _roomCard('room_2')],
      selectedRoomId: 'room_2',
      selectedRoom: retainedRoom,
      selectedRoomHasPendingJoinRequests: true,
      messages,
      live,
      livePanelOpen: true,
      settingsOpen: true,
      joinedLiveRoomId: 'room_2',
      data: { room_id: 'room_1' },
    })!;

    expect(retainedPatch.rooms.map((room) => room.id)).toEqual(['room_2']);
    expect(retainedPatch.selectedRoomId).toBe('room_2');
    expect(retainedPatch.selectedRoom).toBe(retainedRoom);
    expect(retainedPatch.selectedRoomHasPendingJoinRequests).toBe(true);
    expect(retainedPatch.messages).toBe(messages);
    expect(retainedPatch.live).toBe(live);
    expect(retainedPatch.livePanelOpen).toBe(true);
    expect(retainedPatch.settingsOpen).toBe(true);
    expect(retainedPatch.joinedLiveRoomId).toBe('room_2');
    expect(retainedPatch.wasSelected).toBe(false);
    expect(retainedPatch.shouldDisconnectLive).toBe(false);
    expect(
      controller.patchRoomDeleted({
        rooms: [],
        selectedRoomId: null,
        selectedRoom: null,
        selectedRoomHasPendingJoinRequests: false,
        messages: [],
        live: null,
        livePanelOpen: false,
        settingsOpen: false,
        joinedLiveRoomId: null,
        data: {},
      }),
    ).toBeNull();
  });

  it('room role changed reducer patches only the selected room', () => {
    const controller = reducerController();
    const selectedRoom = _roomDetail('room_1');

    const patch = controller.patchRoomRoleChanged({
      selectedRoom,
      data: { room_id: 'room_1', role: 'admin' },
    })!;

    expect(patch.roomId).toBe('room_1');
    expect(patch.selectedRoom.id).toBe('room_1');
    expect(patch.selectedRoom.myMembership.role).toBe('admin');
    expect(selectedRoom.myMembership.role).toBe('member');

    expect(controller.patchRoomRoleChanged({ selectedRoom, data: { room_id: 'room_2', role: 'admin' } })).toBeNull();
    expect(controller.patchRoomRoleChanged({ selectedRoom, data: { room_id: 'room_1' } })).toBeNull();
    expect(controller.patchRoomRoleChanged({ selectedRoom: null, data: { room_id: 'room_1', role: 'admin' } })).toBeNull();
  });

  it('room open guards reject duplicate and stale async work', () => {
    expect(shouldSkipRoomOpenRequest({ loadingRoom: true, selectedRoomId: 'room_1', roomId: 'room_1' })).toBe(true);
    expect(shouldSkipRoomOpenRequest({ loadingRoom: true, selectedRoomId: 'room_2', roomId: 'room_1' })).toBe(false);
    expect(shouldSkipRoomOpenRequest({ loadingRoom: false, selectedRoomId: 'room_1', roomId: 'room_1' })).toBe(false);

    expect(canApplyRoomOpenResult({ requestedRoomId: 'room_1', selectedRoomId: 'room_1' })).toBe(true);
    expect(canApplyRoomOpenResult({ requestedRoomId: 'room_1', selectedRoomId: 'room_2' })).toBe(false);

    expect(
      shouldShowOptimisticRoomOpenRefreshFailure({
        hasOptimisticDetail: true,
        requestedRoomId: 'room_1',
        selectedRoomId: 'room_1',
      }),
    ).toBe(true);
    expect(
      shouldShowOptimisticRoomOpenRefreshFailure({
        hasOptimisticDetail: false,
        requestedRoomId: 'room_1',
        selectedRoomId: 'room_1',
      }),
    ).toBe(false);
    expect(
      shouldShowOptimisticRoomOpenRefreshFailure({
        hasOptimisticDetail: true,
        requestedRoomId: 'room_1',
        selectedRoomId: 'room_2',
      }),
    ).toBe(false);

    expect(shouldFinishRoomOpenLoading({ requestedRoomId: 'room_1', selectedRoomId: 'room_1' })).toBe(true);
    expect(shouldFinishRoomOpenLoading({ requestedRoomId: 'room_1', selectedRoomId: null })).toBe(false);

    expect(canApplyLiveRefresh({ live: _liveState({ participantCount: 2 }), selectedRoomId: 'room_1' })).toBe(true);
    expect(canApplyLiveRefresh({ live: _liveState({ participantCount: 2 }), selectedRoomId: 'room_2' })).toBe(false);
  });

  it('room list load patches preserve loading semantics', () => {
    const controller = reducerController();
    const existingRooms = [_roomCard('room_1')];
    const loadedRooms = [_roomCard('room_2')];

    const started = controller.patchRoomListLoadStarted({ rooms: existingRooms });
    expect(started.rooms).toBe(existingRooms);
    expect(started.loading).toBe(true);
    expect(started.error).toBeNull();

    const succeeded = controller.patchRoomListLoadSucceeded({ rooms: loadedRooms });
    expect(succeeded.rooms.map((room) => room.id)).toEqual(['room_2']);
    expect(succeeded.loading).toBe(false);
    expect(succeeded.error).toBeNull();

    const failed = controller.patchRoomListLoadFailed({ rooms: existingRooms, failure: 'load failed' });
    expect(failed.rooms).toBe(existingRooms);
    expect(failed.loading).toBe(false);
    expect(failed.error).toBe('操作失败，请稍后重试');
  });

  it('room open state patches preserve Home open-room semantics', () => {
    const controller = reducerController();
    const currentRoom = _roomDetail('room_1');
    const optimisticRoom = _roomDetail('room_2');
    const currentMessages = [_message('message_1', { roomId: 'room_1' })];
    const currentLive = _liveState({ participantCount: 2 });

    const loadingPatch = controller.patchRoomOpenStarted({
      roomId: 'room_2',
      currentSelectedRoom: currentRoom,
      currentMessages,
      currentLive,
      currentLivePanelOpen: true,
      joinLive: false,
    });
    expect(loadingPatch.settingsOpen).toBe(false);
    expect(loadingPatch.selectedRoomId).toBe('room_2');
    expect(loadingPatch.selectedRoom).toBe(currentRoom);
    expect(loadingPatch.loadingRoom).toBe(true);
    expect(loadingPatch.error).toBeNull();
    expect(loadingPatch.selectedRoomHasPendingJoinRequests).toBe(false);
    expect(loadingPatch.messages).toBe(currentMessages);
    expect(loadingPatch.live).toBe(currentLive);
    expect(loadingPatch.livePanelOpen).toBe(false);

    const optimisticPatch = controller.patchRoomOpenStarted({
      roomId: 'room_2',
      currentSelectedRoom: currentRoom,
      currentMessages,
      currentLive,
      currentLivePanelOpen: true,
      joinLive: true,
      optimisticDetail: optimisticRoom,
    });
    expect(optimisticPatch.selectedRoom).toBe(optimisticRoom);
    expect(optimisticPatch.messages).toEqual([]);
    expect(optimisticPatch.live).toBe(optimisticRoom.live);
    expect(optimisticPatch.livePanelOpen).toBe(true);

    const snapshot = new RoomOpenSnapshot({
      detail: optimisticRoom,
      messages: [_message('message_2', { roomId: 'room_2' })],
      live: _liveState({ participantCount: 4 }),
    });
    const noJoinEffects = roomOpenSucceededEffects({ snapshot, joinLive: false });
    expect(noJoinEffects.joinRequestBadgeRoom).toBe(optimisticRoom);
    expect(noJoinEffects.joinLiveSource).toBeNull();

    const joinEffects = roomOpenSucceededEffects({ snapshot, joinLive: true });
    expect(joinEffects.joinRequestBadgeRoom).toBe(optimisticRoom);
    expect(joinEffects.joinLiveSource).toBe('room_card_speaker');

    const successPatch = controller.patchRoomOpenSucceeded({
      currentSettingsOpen: true,
      currentLoadingRoom: true,
      currentError: 'kept',
      currentSelectedRoomHasPendingJoinRequests: true,
      snapshot,
      joinLive: true,
    });
    expect(successPatch.settingsOpen).toBe(true);
    expect(successPatch.selectedRoomId).toBe('room_2');
    expect(successPatch.selectedRoom).toBe(optimisticRoom);
    expect(successPatch.loadingRoom).toBe(true);
    expect(successPatch.error).toBe('kept');
    expect(successPatch.selectedRoomHasPendingJoinRequests).toBe(true);
    expect(successPatch.messages).toBe(snapshot.messages);
    expect(successPatch.live).toBe(snapshot.live);
    expect(successPatch.livePanelOpen).toBe(true);

    const failedPatch = controller.patchRoomOpenFailed({
      settingsOpen: true,
      selectedRoomId: 'room_2',
      selectedRoom: optimisticRoom,
      loadingRoom: true,
      selectedRoomHasPendingJoinRequests: true,
      messages: snapshot.messages,
      live: snapshot.live,
      livePanelOpen: true,
      failure: 'open failed',
    });
    expect(failedPatch.selectedRoom).toBe(optimisticRoom);
    expect(failedPatch.loadingRoom).toBe(true);
    expect(failedPatch.error).toBe('操作失败，请稍后重试');
    expect(failedPatch.messages).toBe(snapshot.messages);
    expect(failedPatch.live).toBe(snapshot.live);

    const finishedPatch = controller.patchRoomOpenFinished({
      settingsOpen: true,
      selectedRoomId: 'room_2',
      selectedRoom: optimisticRoom,
      error: 'stale error',
      selectedRoomHasPendingJoinRequests: true,
      messages: snapshot.messages,
      live: snapshot.live,
      livePanelOpen: true,
    });
    expect(finishedPatch.settingsOpen).toBe(true);
    expect(finishedPatch.loadingRoom).toBe(false);
    expect(finishedPatch.error).toBe('stale error');
    expect(finishedPatch.livePanelOpen).toBe(true);
  });

  it('room members dialog load patches preserve dialog state semantics', () => {
    const controller = reducerController();
    const members = [_roomMember('user_1')];
    const requests = [_joinRequest('request_1')];
    const live = _liveState({ participantCount: 1 });

    const started = controller.patchRoomMembersLoadStarted({
      members,
      requests,
      live,
      changed: true,
      busyRequestIds: new Set(['request_1']),
    });
    expect(started.members).toBe(members);
    expect(started.requests).toBe(requests);
    expect(started.live).toBe(live);
    expect(started.loading).toBe(true);
    expect(started.changed).toBe(true);
    expect(started.error).toBeNull();
    expect(started.requestError).toBeNull();
    expect(started.busyRequestIds).toEqual(new Set(['request_1']));

    const snapshot = new RoomMembersSnapshot({
      members: [_roomMember('user_2')],
      live: _liveState({ participantCount: 2 }),
      joinRequests: [_joinRequest('request_2')],
      joinRequestsError: 'forbidden',
    });
    const succeeded = controller.patchRoomMembersLoadSucceeded({
      snapshot,
      changed: false,
      busyRequestIds: new Set(['request_2']),
    });
    expect(succeeded.members).toBe(snapshot.members);
    expect(succeeded.requests).toBe(snapshot.joinRequests);
    expect(succeeded.live).toBe(snapshot.live);
    expect(succeeded.loading).toBe(false);
    expect(succeeded.requestError).toBe('forbidden');
    expect(succeeded.hasPendingRequests).toBe(true);
    expect(succeeded.shouldNotifyPendingRequests).toBe(false);

    const failed = controller.patchRoomMembersLoadFailed({
      members,
      requests,
      live,
      changed: true,
      busyRequestIds: new Set(['request_1']),
      failure: new Error('load failed'),
    });
    expect(failed.members).toBe(members);
    expect(failed.loading).toBe(false);
    expect(failed.changed).toBe(true);
    expect(failed.error).toBe('操作失败，请稍后重试');
    expect(failed.requestError).toBeNull();
    expect(failed.busyRequestIds).toEqual(new Set(['request_1']));
  });

  it('room members dialog reload patches isolate member and request errors', () => {
    const controller = reducerController();
    const members = [_roomMember('user_1')];
    const requests = [_joinRequest('request_1')];
    const live = _liveState({ participantCount: 1 });
    const liveSnapshot = new RoomMembersLiveSnapshot({
      members: [_roomMember('user_2')],
      live: _liveState({ participantCount: 3 }),
    });

    const membersReloaded = controller.patchRoomMembersAndLiveReloadSucceeded({
      snapshot: liveSnapshot,
      requests,
      loading: true,
      changed: true,
      requestError: 'request error',
      busyRequestIds: new Set(['request_1']),
    });
    expect(membersReloaded.members).toBe(liveSnapshot.members);
    expect(membersReloaded.requests).toBe(requests);
    expect(membersReloaded.live).toBe(liveSnapshot.live);
    expect(membersReloaded.loading).toBe(true);
    expect(membersReloaded.error).toBeNull();
    expect(membersReloaded.requestError).toBe('request error');

    const membersFailed = controller.patchRoomMembersAndLiveReloadFailed({
      members,
      requests,
      live,
      loading: false,
      changed: true,
      requestError: 'request error',
      busyRequestIds: new Set(['request_1']),
      failure: new Error('members failed'),
    });
    expect(membersFailed.members).toBe(members);
    expect(membersFailed.error).toBe('操作失败，请稍后重试');
    expect(membersFailed.requestError).toBe('request error');

    const requestsReloaded = controller.patchRoomJoinRequestsReloadSucceeded({
      members,
      live,
      requests: [_joinRequest('request_2')],
      loading: false,
      changed: false,
      busyRequestIds: new Set(['request_1']),
    });
    expect(requestsReloaded.requests.map((request) => request.id)).toEqual(['request_2']);
    expect(requestsReloaded.error).toBeNull();
    expect(requestsReloaded.requestError).toBeNull();
    expect(requestsReloaded.hasPendingRequests).toBe(true);
    expect(requestsReloaded.shouldNotifyPendingRequests).toBe(true);

    const requestsFailed = controller.patchRoomJoinRequestsReloadFailed({
      members,
      requests,
      live,
      loading: false,
      changed: false,
      busyRequestIds: new Set(['request_1']),
      failure: new Error('requests failed'),
    });
    expect(requestsFailed.requests).toBe(requests);
    expect(requestsFailed.error).toBeNull();
    expect(requestsFailed.requestError).toBe('操作失败，请稍后重试');
    expect(requestsFailed.busyRequestIds).toEqual(new Set(['request_1']));
  });

  it('room members dialog review patches update busy ids and requests', () => {
    const controller = reducerController();
    const members = [_roomMember('user_1')];
    const requests = [_joinRequest('request_1'), _joinRequest('request_2')];
    const live = _liveState({ participantCount: 1 });

    const started = controller.patchJoinRequestReviewStarted({
      members,
      requests,
      live,
      loading: false,
      changed: false,
      requestId: 'request_1',
      busyRequestIds: new Set(['request_2']),
    });
    expect(started.members).toBe(members);
    expect(started.requests).toEqual(requests);
    expect(started.error).toBeNull();
    expect(started.busyRequestIds).toEqual(new Set(['request_1', 'request_2']));

    const succeeded = controller.patchJoinRequestReviewSucceeded({
      members,
      requests: started.requests,
      live,
      loading: false,
      changed: false,
      error: 'kept',
      requestError: null,
      requestId: 'request_1',
      busyRequestIds: started.busyRequestIds,
    });
    expect(succeeded.requests.map((request) => request.id)).toEqual(['request_2']);
    expect(succeeded.changed).toBe(true);
    expect(succeeded.error).toBe('kept');
    expect(succeeded.requestError).toBeNull();
    expect(succeeded.busyRequestIds).toEqual(new Set(['request_2']));
    expect(succeeded.hasPendingRequests).toBe(true);

    const failed = controller.patchJoinRequestReviewFailed({
      members,
      requests: started.requests,
      live,
      loading: false,
      changed: true,
      error: 'kept',
      requestId: 'request_1',
      busyRequestIds: started.busyRequestIds,
      failure: new Error('review failed'),
    });
    expect(failed.requests).toEqual(started.requests);
    expect(failed.changed).toBe(true);
    expect(failed.error).toBe('kept');
    expect(failed.requestError).toBe('操作失败，请稍后重试');
    expect(failed.busyRequestIds).toEqual(new Set(['request_2']));
  });

  it('patchLiveSnapshot re-inserts self when a stale snapshot drops the local participant for the joined room', () => {
    const controller = reducerController();
    const previousLive = LiveState.fromJson(_liveJsonWithParticipants(['me', 'other']));

    // Snapshot computed before we joined: it only lists "other".
    const patch = controller.patchLiveSnapshot({
      rooms: [_roomCard('room_1')],
      selectedRoomId: 'room_1',
      data: _liveSnapshotData(['other']),
      joinedLiveRoomId: 'room_1',
      currentUserId: 'me',
      previousLive,
      localParticipantConnected: true,
    });

    expect(patch).not.toBeNull();
    const ids = patch!.selectedLive!.participants.map((participant) => participant.user.id);
    expect(ids).toEqual(expect.arrayContaining(['me', 'other']));
    expect(patch!.selectedLive!.participantCount).toBe(2);
    expect(patch!.rooms[0]!.liveParticipantCount).toBe(2);
    expect(patch!.rooms[0]!.liveAvatarPreview.map((user) => user.id)).toEqual(expect.arrayContaining(['me', 'other']));
  });

  it('patchLiveSnapshot drops self after the local media session detached', () => {
    const controller = reducerController();
    const previousLive = LiveState.fromJson(_liveJsonWithParticipants(['me', 'other']));

    const patch = controller.patchLiveSnapshot({
      rooms: [_roomCard('room_1', { liveParticipantCount: 2 })],
      selectedRoomId: 'room_1',
      data: _liveSnapshotData(['other']),
      joinedLiveRoomId: 'room_1',
      currentUserId: 'me',
      previousLive,
      localParticipantConnected: false,
    });

    expect(patch!.selectedLive!.participants.map((participant) => participant.user.id)).toEqual(['other']);
    expect(patch!.rooms[0]!.liveParticipantCount).toBe(1);
  });

  it('patchLiveSnapshot does not re-insert self when not joined to the room', () => {
    const controller = reducerController();
    const previousLive = LiveState.fromJson(_liveJsonWithParticipants(['me', 'other']));

    const patch = controller.patchLiveSnapshot({
      rooms: [_roomCard('room_1')],
      selectedRoomId: 'room_1',
      data: _liveSnapshotData(['other']),
      joinedLiveRoomId: null,
      currentUserId: 'me',
      previousLive,
    });

    expect(patch).not.toBeNull();
    expect(patch!.selectedLive!.participants.map((participant) => participant.user.id)).toEqual(['other']);
  });

  it('patchLiveSnapshot preserves participant room display names', () => {
    const controller = reducerController();
    const previousJson = _liveJsonWithParticipants(['me']);
    const participants = previousJson['participants'] as JsonMap[];
    participants[0]!['room_display_name'] = 'Room Me';
    const previousLive = LiveState.fromJson(previousJson);

    const patch = controller.patchLiveSnapshot({
      rooms: [_roomCard('room_1')],
      selectedRoomId: 'room_1',
      data: _liveSnapshotData(['me']),
      previousLive,
    });

    expect(patch).not.toBeNull();
    expect(patch!.selectedLive!.participants[0]!.user.roomDisplayName).toBe('Room Me');
  });

  it('patchLiveSnapshot ignores an older selected-room live snapshot', () => {
    const controller = reducerController();
    const previousLive = LiveState.fromJson({
      ..._liveJsonWithParticipants(['me', 'other']),
      updated_at: '2026-06-04T00:00:02Z',
    });

    const patch = controller.patchLiveSnapshot({
      rooms: [_roomCard('room_1', { liveParticipantCount: 2 })],
      selectedRoomId: 'room_1',
      data: _liveSnapshotData(['other']),
      joinedLiveRoomId: 'room_1',
      currentUserId: 'me',
      previousLive,
    });

    expect(patch).not.toBeNull();
    expect(patch!.selectedLive).toBeNull();
    expect(patch!.rooms[0]!.liveParticipantCount).toBe(2);
  });

  it('patchRoomUpdated updates selected room counts and reload hint', () => {
    const controller = reducerController();

    const patch = controller.patchRoomUpdated({
      rooms: [_roomCard('room_1', { unreadCount: 5, onlineMemberCount: 1 })],
      incoming: _roomCard('room_1', {
        name: 'Renamed',
        memberCount: 4,
        onlineMemberCount: 2,
        aiVoiceAnnouncementsEnabled: false,
      }),
      selectedRoom: _roomDetail('room_1', { onlineMemberCount: 1 }),
    });

    expect(patch.rooms[0]!.unreadCount).toBe(5);
    expect(patch.rooms[0]!.onlineMemberCount).toBe(2);
    expect(patch.selectedRoom?.name).toBe('Renamed');
    expect(patch.selectedRoom?.memberCount).toBe(4);
    expect(patch.selectedRoom?.onlineMemberCount).toBe(2);
    expect(patch.rooms[0]!.aiVoiceAnnouncementsEnabled).toBe(false);
    expect(patch.selectedRoom?.aiVoiceAnnouncementsEnabled).toBe(false);
    expect(patch.shouldReloadMembers).toBe(true);
  });

  it('patchRoomUpdated keeps selected room fresh messages unread', () => {
    const controller = reducerController();

    const patch = controller.patchRoomUpdated({
      rooms: [_roomCard('room_1', { unreadCount: 0, lastMessageId: 'msg_1' })],
      incoming: _roomCard('room_1', { lastMessageId: 'msg_2' }),
      selectedRoom: _roomDetail('room_1'),
    });

    expect(patch.rooms[0]!.unreadCount).toBe(1);
  });

  it('patchRoomUpdated increments unread for fresh messages off-room', () => {
    const controller = reducerController();

    const patch = controller.patchRoomUpdated({
      rooms: [_roomCard('room_1', { unreadCount: 5, lastMessageId: 'msg_1' })],
      incoming: _roomCard('room_1', { lastMessageId: 'msg_2' }),
      selectedRoom: _roomDetail('room_2'),
    });

    expect(patch.rooms[0]!.unreadCount).toBe(6);

    const sameLastMessage = controller.patchRoomUpdated({
      rooms: patch.rooms,
      incoming: _roomCard('room_1', { lastMessageId: 'msg_2' }),
      selectedRoom: _roomDetail('room_2'),
    });

    expect(sameLastMessage.rooms[0]!.unreadCount).toBe(6);
  });

  it('patchRoomUpdated prefers server unread count when present', () => {
    const controller = reducerController();

    const patch = controller.patchRoomUpdated({
      rooms: [_roomCard('room_1', { unreadCount: 5, lastMessageId: 'msg_1' })],
      incoming: _roomCard('room_1', { unreadCount: 7, hasUnreadCount: true, lastMessageId: 'msg_2' }),
      selectedRoom: _roomDetail('room_2'),
    });

    expect(patch.rooms[0]!.unreadCount).toBe(7);
  });

  it('patchRoomUpdated reorders rooms by latest activity time', () => {
    const controller = reducerController();

    const patch = controller.patchRoomUpdated({
      rooms: [
        _roomCard('room_old', {
          unreadCount: 1,
          lastMessageId: 'msg_old',
          lastMessageAt: new Date(Date.UTC(2026, 5, 4, 10)),
        }),
        _roomCard('room_target', {
          unreadCount: 1,
          lastMessageId: 'msg_previous',
          lastMessageAt: new Date(Date.UTC(2026, 5, 4, 9)),
        }),
      ],
      incoming: _roomCard('room_target', {
        lastMessageId: 'msg_new',
        lastMessageAt: new Date(Date.UTC(2026, 5, 4, 11)),
      }),
      selectedRoom: _roomDetail('room_other'),
    });

    expect(patch.rooms.map((room) => room.id)).toEqual(['room_target', 'room_old']);
    expect(patch.rooms[0]!.unreadCount).toBe(2);
  });

  it('patchRoomUnreadCleared clears a single room count', () => {
    const controller = reducerController();

    const patch = controller.patchRoomUnreadCleared({
      rooms: [_roomCard('room_1', { unreadCount: 5 }), _roomCard('room_2', { unreadCount: 2 })],
      roomId: 'room_1',
    });

    expect(patch.rooms.map((room) => room.id)).toEqual(['room_2', 'room_1']);
    expect(patch.rooms[0]!.unreadCount).toBe(2);
    expect(patch.rooms[1]!.unreadCount).toBe(0);
  });

  it('room ordering puts pinned first and red unread before gray unread', () => {
    const controller = reducerController();

    const ordered = controller.patchRoomCardsRefreshed({
      rooms: [
        _roomCard('normal'),
        _roomCard('gray', { unreadCount: 2, notificationPolicy: 'silent' }),
        _roomCard('red', { unreadCount: 1 }),
        _roomCard('pinned', { isPinned: true }),
      ],
    }).rooms;

    expect(ordered.map((room) => room.id)).toEqual(['pinned', 'red', 'gray', 'normal']);
  });

  it('roomCardFromSnapshot rejects snapshots without a string id', () => {
    const controller = reducerController();
    expect(controller.roomCardFromSnapshot({ id: 42 })).toBeNull();
    expect(controller.roomCardFromSnapshot({ id: 'room_1' })).toBeNull();
  });
});

function _roomCard(
  id: string,
  opts: {
    name?: string;
    unreadCount?: number;
    memberCount?: number;
    onlineMemberCount?: number;
    lastMessageId?: string;
    notificationPolicy?: string;
    isPinned?: boolean;
    hasUnreadCount?: boolean;
    liveParticipantCount?: number;
    lastMessageAt?: Date;
    updatedAt?: Date;
    aiVoiceAnnouncementsEnabled?: boolean;
  } = {},
): RoomCard {
  return new RoomCard({
    id,
    name: opts.name ?? id,
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    notificationPolicy: opts.notificationPolicy ?? 'all',
    isPinned: opts.isPinned ?? false,
    memberCount: opts.memberCount ?? 3,
    onlineMemberCount: opts.onlineMemberCount ?? 0,
    liveParticipantCount: opts.liveParticipantCount ?? 0,
    liveAvatarPreview: [],
    lastMessage:
      opts.lastMessageId == null
        ? null
        : new LastMessagePreview({
            id: opts.lastMessageId,
            type: 'text',
            senderDisplayName: 'Alice',
            bodyPreview: 'hello',
            createdAt: opts.lastMessageAt ?? new Date(Date.UTC(2026, 5, 4)),
          }),
    unreadCount: opts.unreadCount ?? 0,
    hasUnreadCount: opts.hasUnreadCount ?? false,
    aiVoiceAnnouncementsEnabled: opts.aiVoiceAnnouncementsEnabled ?? false,
    updatedAt: opts.updatedAt ?? new Date(Date.UTC(2026, 5, 4)),
  });
}

function _roomInvite(id: string): RoomInvite {
  return new RoomInvite({
    id,
    status: 'pending',
    room: _publicRoom(`room_${id}`),
    inviter: _user(`inviter_${id}`),
    createdAt: new Date(Date.UTC(2026, 5, 4)),
  });
}

function _joinRequest(id: string): JoinRequest {
  return new JoinRequest({
    id,
    status: 'pending',
    user: _user(`user_${id}`),
    createdAt: new Date(Date.UTC(2026, 5, 4)),
  });
}

function _roomMember(userId: string): RoomMember {
  return new RoomMember({
    user: _user(userId),
    role: 'member',
    joinedAt: new Date(Date.UTC(2026, 5, 4)),
    isOnline: true,
  });
}

function _roomDetail(id: string, opts: { role?: string; onlineMemberCount?: number } = {}): RoomDetail {
  return new RoomDetail({
    id,
    name: id,
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    memberCount: 3,
    onlineMemberCount: opts.onlineMemberCount ?? 0,
    myMembership: new RoomMembership({
      joinedAt: new Date(Date.UTC(2026, 5, 4)),
      role: opts.role ?? 'member',
    }),
    live: _liveState({ participantCount: 0 }),
    createdAt: new Date(Date.UTC(2026, 5, 4)),
    updatedAt: new Date(Date.UTC(2026, 5, 4)),
  });
}

function _message(id: string, opts: { roomId: string }): Message {
  return new Message({
    id,
    roomId: opts.roomId,
    sender: _user('sender'),
    clientMessageId: `client_${id}`,
    body: 'hello',
    createdAt: new Date(Date.UTC(2026, 5, 4)),
  });
}

function _publicRoom(id: string): PublicRoom {
  return new PublicRoom({
    id,
    rid: `R${id}`,
    name: id,
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    visibility: 'public',
    joinPolicy: 'approval_required',
    memberCount: 3,
    liveParticipantCount: 0,
    joined: false,
    joinState: 'none',
  });
}

function _user(id: string): UserSummary {
  return new UserSummary({
    id,
    username: id,
    displayName: id,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
  });
}

function _memberJson(userId: string): JsonMap {
  return {
    user: _userJson(userId),
    role: 'member',
    joined_at: '2026-06-04T00:00:00Z',
    is_online: true,
  };
}

function _userJson(userId: string): JsonMap {
  return {
    id: userId,
    username: userId,
    display_name: userId,
    avatar_url: null,
    default_avatar_key: 'blue-1',
  };
}

function _liveState(opts: { participantCount: number }): LiveState {
  return LiveState.fromJson(_liveJson(opts));
}

function _liveJson(opts: { participantCount: number }): JsonMap {
  return {
    room_id: 'room_1',
    participant_count: opts.participantCount,
    participants: [],
    updated_at: '2026-06-04T00:00:00Z',
  };
}

function _participantJson(userId: string): JsonMap {
  return {
    live_session_id: `session_${userId}`,
    user: {
      id: userId,
      username: userId,
      display_name: userId,
      avatar_url: null,
      default_avatar_key: 'blue-3',
    },
    joined_at: '2026-06-04T00:00:00Z',
    mic_muted: false,
    headphones_muted: false,
    voice_blocked: false,
    camera_on: false,
    screen_sharing: false,
    connection_state: 'connected',
  };
}

function _liveJsonWithParticipants(userIds: string[]): JsonMap {
  return {
    room_id: 'room_1',
    participant_count: userIds.length,
    participants: userIds.map(_participantJson),
    updated_at: '2026-06-04T00:00:00Z',
  };
}

function _liveSnapshotData(userIds: string[]): JsonMap {
  return {
    room_id: 'room_1',
    participant_count: userIds.length,
    live: _liveJsonWithParticipants(userIds),
  };
}
