import { describe, expect, it } from 'vitest';

import {
  LiveController,
  LiveModerationAction,
  LiveStateRequestQueue,
  canApplyPickedScreenShareSource,
  canPatchSelectedLiveState,
  isBenignGoneLiveStatePatch,
  joinedLiveRoomToDisconnectBeforeJoin,
  liveOutputMuteToggled,
  shouldSyncLiveKitMicAfterServerPatch,
} from '../../src/app/live_controller';
import { RoomsController } from '../../src/app/rooms_controller';
import { Completer, delay } from '../../src/lib/async';
import type { JsonMap } from '../../src/lib/json';
import { ApiException, GangApiClient } from '../../src/protocol/api_client';
import {
  LiveJoinResult,
  LiveKitConnectionInfo,
  LiveParticipant,
  LiveState,
  RoomCard,
  UserSummary,
} from '../../src/protocol/models';
import { MockClient, jsonResponse, type MockHandler } from '../support/fake_http_client';

function makeApi(handler: MockHandler): GangApiClient {
  return new GangApiClient({
    baseUrl: 'http://example.test/api/v1',
    accessTokenProvider: async () => 'token',
    httpClient: new MockClient(handler),
  });
}

function reducerApi(): GangApiClient {
  return makeApi((request) => {
    throw new Error(`Reducer test should not call the API: ${request.url}`);
  });
}

describe('LiveStateRequestQueue', () => {
  it('preserves mutation order', async () => {
    const queue = new LiveStateRequestQueue();
    const firstGate = new Completer<void>();
    const started: number[] = [];

    const first = queue.run(async () => {
      started.push(1);
      await firstGate.promise;
      return 'first';
    });
    const second = queue.run(async () => {
      started.push(2);
      return 'second';
    });

    await delay(0);
    expect(started).toEqual([1]);
    expect(queue.isIdle).toBe(false);

    firstGate.complete();
    expect(await first).toBe('first');
    expect(await second).toBe('second');
    expect(started).toEqual([1, 2]);
    expect(queue.isIdle).toBe(true);
  });

  it('continues after a failed mutation', async () => {
    const queue = new LiveStateRequestQueue();

    const failed = queue.run<void>(async () => {
      throw new Error('failed');
    });
    const recovered = queue.run(async () => 2);

    await expect(failed).rejects.toThrow('failed');
    expect(await recovered).toBe(2);
    expect(queue.isIdle).toBe(true);
  });
});

describe('live controller', () => {
  it('live room guards keep room switching and stale async work explicit', () => {
    expect(joinedLiveRoomToDisconnectBeforeJoin({ joinedLiveRoomId: null, targetRoomId: 'room_1' })).toBeNull();
    expect(joinedLiveRoomToDisconnectBeforeJoin({ joinedLiveRoomId: 'room_1', targetRoomId: 'room_1' })).toBeNull();
    expect(joinedLiveRoomToDisconnectBeforeJoin({ joinedLiveRoomId: 'room_1', targetRoomId: 'room_2' })).toBe('room_1');

    expect(canPatchSelectedLiveState({ joinedLiveRoomId: 'room_1', selectedRoomId: 'room_1' })).toBe(true);
    expect(canPatchSelectedLiveState({ joinedLiveRoomId: null, selectedRoomId: 'room_1' })).toBe(false);
    expect(canPatchSelectedLiveState({ joinedLiveRoomId: 'room_2', selectedRoomId: 'room_1' })).toBe(false);

    expect(
      canApplyPickedScreenShareSource({ pickedForRoomId: 'room_1', joinedLiveRoomId: 'room_1', selectedRoomId: 'room_1' }),
    ).toBe(true);
    expect(
      canApplyPickedScreenShareSource({ pickedForRoomId: 'room_1', joinedLiveRoomId: 'room_2', selectedRoomId: 'room_1' }),
    ).toBe(false);
    expect(
      canApplyPickedScreenShareSource({ pickedForRoomId: 'room_1', joinedLiveRoomId: 'room_1', selectedRoomId: 'room_2' }),
    ).toBe(false);
  });

  it('409 live state patch errors are treated as benign departure races', () => {
    expect(
      isBenignGoneLiveStatePatch(
        new ApiException('gone', { statusCode: 409, code: 'participant_not_found', requestId: null }),
      ),
    ).toBe(true);
    expect(
      isBenignGoneLiveStatePatch(new ApiException('server error', { statusCode: 500, code: 'request_failed', requestId: null })),
    ).toBe(false);
    expect(isBenignGoneLiveStatePatch(new Error('boom'))).toBe(false);
  });

  it('join state patches keep LiveKit side effects out of UI flags', () => {
    const controller = new LiveController({ api: reducerApi() });
    const alice = _user('alice');
    const bob = _user('bob');

    const started = controller.patchJoinStarted({ joinedLiveRoomId: 'room_0' });
    expect(started.joinedLiveRoomId).toBe('room_0');
    expect(started.joiningLive).toBe(true);
    expect(started.livePanelOpen).toBe(true);
    expect(started.error).toBeNull();

    const disconnected = controller.patchJoinPreviousRoomDisconnected({
      live: _live('room_0', [_participant(alice), _participant(bob)]),
      rooms: [_roomCard('room_0', { liveCount: 2, livePreview: [alice, bob] })],
      previousRoomId: 'room_0',
      userId: 'alice',
      livePanelOpen: false,
      error: 'keep existing pane error',
    });
    expect(disconnected.live?.participants.map((item) => item.user.id)).toEqual(['bob']);
    expect(disconnected.rooms[0]!.liveParticipantCount).toBe(1);
    expect(disconnected.rooms[0]!.liveAvatarPreview.map((user) => user.id)).toEqual(['bob']);
    expect(disconnected.joinedLiveRoomId).toBeNull();
    expect(disconnected.joiningLive).toBe(true);
    expect(disconnected.livePanelOpen).toBe(false);
    expect(disconnected.error).toBe('keep existing pane error');

    const connected = controller.patchJoinConnected({ roomId: 'room_1', livePanelOpen: true, error: 'preserved' });
    expect(connected.joinedLiveRoomId).toBe('room_1');
    expect(connected.joiningLive).toBe(true);
    expect(connected.error).toBe('preserved');

    const finished = controller.patchJoinFinished({ joinedLiveRoomId: 'room_1', livePanelOpen: true, error: null });
    expect(finished.joinedLiveRoomId).toBe('room_1');
    expect(finished.joiningLive).toBe(false);
    expect(finished.livePanelOpen).toBe(true);
    expect(finished.error).toBeNull();
  });

  it('removeUserFromLive updates selected live state and room preview', () => {
    const controller = new LiveController({ api: reducerApi() });
    const alice = _user('alice');
    const bob = _user('bob');

    const patch = controller.removeUserFromLive({
      live: _live('room_1', [_participant(alice), _participant(bob)]),
      rooms: [
        _roomCard('room_1', { liveCount: 2, livePreview: [alice, bob] }),
        _roomCard('room_2', { liveCount: 1, livePreview: [alice] }),
      ],
      roomId: 'room_1',
      userId: 'alice',
    });

    expect(patch.live?.participantCount).toBe(1);
    expect(patch.live?.participants.map((item) => item.user.id)).toEqual(['bob']);
    expect(patch.rooms[0]!.liveParticipantCount).toBe(1);
    expect(patch.rooms[0]!.liveAvatarPreview.map((user) => user.id)).toEqual(['bob']);
    expect(patch.rooms[1]!.liveParticipantCount).toBe(1);
    expect(patch.rooms[1]!.liveAvatarPreview.map((user) => user.id)).toEqual(['alice']);
  });

  it('patchLocalDeparture clears joined local state and removes self', () => {
    const controller = new LiveController({ api: reducerApi() });
    const alice = _user('alice');
    const bob = _user('bob');

    const patch = controller.patchLocalDeparture({
      live: _live('room_1', [_participant(alice), _participant(bob)]),
      rooms: [_roomCard('room_1', { liveCount: 2, livePreview: [alice, bob] })],
      joinedLiveRoomId: 'room_1',
      userId: 'alice',
      joiningLive: true,
    });

    expect(patch.joinedLiveRoomId).toBeNull();
    expect(patch.joiningLive).toBe(true);
    expect(patch.cameraOn).toBe(false);
    expect(patch.screenSharing).toBe(false);
    expect(patch.voiceBlocked).toBe(false);
    expect(patch.live?.participants.map((item) => item.user.id)).toEqual(['bob']);
    expect(patch.rooms[0]!.liveParticipantCount).toBe(1);

    const live = _live('room_2', [_participant(alice)]);
    const rooms = [_roomCard('room_2', { liveCount: 1, livePreview: [alice] })];
    const noJoinedRoomPatch = controller.patchLocalDeparture({
      live,
      rooms,
      joinedLiveRoomId: null,
      userId: 'alice',
      joiningLive: false,
    });
    expect(noJoinedRoomPatch.live).toBe(live);
    expect(noJoinedRoomPatch.rooms).toBe(rooms);
    expect(noJoinedRoomPatch.joinedLiveRoomId).toBeNull();
    expect(noJoinedRoomPatch.joiningLive).toBe(false);
  });

  it('patchPublishPermission mirrors LiveKit publish permission', () => {
    const controller = new LiveController({ api: reducerApi() });

    let patch = controller.patchPublishPermission({ canPublish: false, micMuted: false });
    expect(patch.voiceBlocked).toBe(true);
    expect(patch.micMuted).toBe(true);

    patch = controller.patchPublishPermission({ canPublish: true, micMuted: true });
    expect(patch.voiceBlocked).toBe(false);
    expect(patch.micMuted).toBe(true);
  });

  it('live output mute toggle flips local headphones state', () => {
    expect(liveOutputMuteToggled({ headphonesMuted: false }).headphonesMuted).toBe(true);
    expect(liveOutputMuteToggled({ headphonesMuted: true }).headphonesMuted).toBe(false);
  });

  it('leaveLive reports a left connection state to the server', async () => {
    let body: unknown = null;
    const api = makeApi((request) => {
      expect(request.method).toBe('PATCH');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/live/me');
      body = JSON.parse(request.body);
      return jsonResponse(
        { participant: _participantJson({ user: _userJson('alice'), connectionState: 'left' }) },
        200,
        { 'content-type': 'application/json; charset=utf-8' },
      );
    });

    const participant = await new LiveController({ api }).leaveLive({ roomId: 'room_1' });

    expect(body).toEqual({ connection_state: 'left' });
    expect(participant.connectionState).toBe('left');
  });

  it('camera mirror updates use the existing live state endpoint', async () => {
    let body: unknown = null;
    const api = makeApi((request) => {
      expect(request.method).toBe('PATCH');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/live/me');
      body = JSON.parse(request.body);
      return jsonResponse({
        participant: {
          ..._participantJson({ user: _userJson('alice') }),
          camera_on: true,
          camera_mirrored: true,
        },
      });
    });

    const participant = await new LiveController({ api }).updateMyState({ roomId: 'room_1', cameraMirrored: true });

    expect(body).toEqual({ camera_mirrored: true });
    expect(participant.cameraMirrored).toBe(true);
  });

  it('updateMyLiveScreenView sends the watched broadcaster or clears it', async () => {
    const bodies: unknown[] = [];
    const api = makeApi((request) => {
      expect(request.method).toBe('PATCH');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/live/me/screen-view');
      bodies.push(JSON.parse(request.body));
      return jsonResponse({ broadcaster_user_id: null });
    });
    const controller = new LiveController({ api });

    await controller.updateMyLiveScreenView({ roomId: 'room_1', broadcasterUserId: 'alice' });
    await controller.updateMyLiveScreenView({ roomId: 'room_1' });

    expect(bodies).toEqual([{ broadcaster_user_id: 'alice' }, { broadcaster_user_id: '' }]);
  });

  it('kickParticipant uses live moderation kick action', async () => {
    let body: unknown = null;
    const api = makeApi((request) => {
      expect(request.method).toBe('POST');
      expect(request.uri.pathname).toBe('/api/v1/rooms/room_1/live/participants/bob/moderation');
      body = JSON.parse(request.body);
      return jsonResponse({ ok: true });
    });

    await new LiveController({ api }).kickParticipant({ roomId: 'room_1', userId: 'bob' });

    expect(body).toEqual({ action: 'kick' });
  });

  it('patchJoinResult mirrors participant flags and room live preview', () => {
    const controller = new LiveController({ api: reducerApi() });
    const alice = _user('alice');
    const bob = _user('bob');
    const participant = _participant(alice, {
      micMuted: true,
      cameraOn: true,
      screenSharing: true,
      voiceBlocked: true,
    });
    const live = _live('room_1', [participant, _participant(bob)]);

    const patch = controller.patchJoinResult({
      rooms: [_roomCard('room_1', { liveCount: 0 }), _roomCard('room_2', { liveCount: 3 })],
      result: _liveJoinResult({ participant, live }),
    });

    expect(patch.micMuted).toBe(true);
    expect(patch.headphonesMuted).toBe(false);
    expect(patch.cameraOn).toBe(false);
    expect(patch.screenSharing).toBe(true);
    expect(patch.voiceBlocked).toBe(true);
    expect(patch.live.participants[0]!.cameraOn).toBe(false);
    expect(patch.live.participants[0]!.screenSharing).toBe(true);
    expect(patch.rooms[0]!.liveParticipantCount).toBe(2);
    expect(patch.rooms[0]!.liveAvatarPreview.map((user) => user.id)).toEqual(['alice', 'bob']);
    expect(patch.rooms[1]!.liveParticipantCount).toBe(3);
  });

  it('patchJoinResult can show the joined mic unmuted when allowed', () => {
    const controller = new LiveController({ api: reducerApi() });
    const alice = _user('alice');
    const participant = _participant(alice, { micMuted: true });
    const live = _live('room_1', [participant]);

    const patch = controller.patchJoinResult({
      rooms: [_roomCard('room_1', { liveCount: 0 })],
      result: _liveJoinResult({ participant, live }),
      showMicUnmutedWhenAllowed: true,
    });

    expect(patch.micMuted).toBe(false);
    expect(patch.live.participants[0]!.micMuted).toBe(false);

    const blocked = _participant(alice, { micMuted: true, micBlocked: true, voiceBlocked: true });
    const blockedPatch = controller.patchJoinResult({
      rooms: [_roomCard('room_1', { liveCount: 0 })],
      result: _liveJoinResult({ participant: blocked, live: _live('room_1', [blocked]) }),
      showMicUnmutedWhenAllowed: true,
    });

    expect(blockedPatch.micMuted).toBe(true);
    expect(blockedPatch.live.participants[0]!.micMuted).toBe(true);
  });

  it('patchStateUpdate mirrors participant flags and merges live roster', () => {
    const controller = new LiveController({ api: reducerApi() });
    const alice = _participant(_user('alice'));
    const bob = _participant(_user('bob'), {
      micMuted: true,
      voiceBlocked: true,
      cameraOn: true,
      screenSharing: true,
    });

    const patch = controller.patchStateUpdate({ live: _live('room_1', [alice]), participant: bob });

    expect(patch.micMuted).toBe(true);
    expect(patch.headphonesMuted).toBe(false);
    expect(patch.voiceBlocked).toBe(true);
    expect(patch.cameraOn).toBe(false);
    expect(patch.screenSharing).toBe(true);
    expect(patch.live?.participantCount).toBe(2);
    expect(patch.live?.participants.map((item) => item.user.id)).toEqual(['alice', 'bob']);
  });

  it('patchStateUpdate preserves room display names from the live roster', () => {
    const controller = new LiveController({ api: reducerApi() });
    const existing = _participant(_user('alice').copyWith({ roomDisplayName: 'Room Alice' }));
    const incoming = _participant(_user('alice'), { micMuted: true });

    const patch = controller.patchStateUpdate({ live: _live('room_1', [existing]), participant: incoming });

    expect(patch.live?.participants[0]!.user.roomDisplayName).toBe('Room Alice');
    expect(patch.live?.participants[0]!.micMuted).toBe(true);
  });

  it('patchModeratedParticipant keeps mic and headphones actions separate', () => {
    const controller = new LiveController({ api: reducerApi() });
    const alice = _user('alice');
    const initial = _participant(alice);

    const headphonesMuted = controller.patchModeratedParticipant({
      live: _live('room_1', [initial]),
      participant: initial,
      action: LiveModerationAction.blockVoice,
    })!.participants[0]!;
    expect(headphonesMuted.micMuted).toBe(false);
    expect(headphonesMuted.micBlocked).toBe(false);
    expect(headphonesMuted.voiceBlocked).toBe(false);
    expect(headphonesMuted.headphonesMuted).toBe(true);
    expect(headphonesMuted.headphonesBlocked).toBe(true);
    expect(headphonesMuted.headphonesListening).toBe(false);

    const micRestored = controller.patchModeratedParticipant({
      live: _live('room_1', [headphonesMuted]),
      participant: headphonesMuted.copyWith({ micMuted: true, micBlocked: true, voiceBlocked: true }),
      action: LiveModerationAction.restoreVoice,
    })!.participants[0]!;
    expect(micRestored.micMuted).toBe(false);
    expect(micRestored.micBlocked).toBe(false);
    expect(micRestored.voiceBlocked).toBe(false);
    expect(micRestored.headphonesMuted).toBe(true);
    expect(micRestored.headphonesBlocked).toBe(true);

    const headphonesRestored = controller.patchModeratedParticipant({
      live: _live('room_1', [micRestored]),
      participant: micRestored.copyWith({ micMuted: true, micBlocked: true, voiceBlocked: true }),
      action: LiveModerationAction.restoreHeadphones,
    })!.participants[0]!;
    expect(headphonesRestored.micMuted).toBe(true);
    expect(headphonesRestored.micBlocked).toBe(true);
    expect(headphonesRestored.voiceBlocked).toBe(true);
    expect(headphonesRestored.headphonesMuted).toBe(false);
    expect(headphonesRestored.headphonesBlocked).toBe(false);
    expect(headphonesRestored.headphonesListening).toBe(true);

    const live = _live('room_1', [initial]);
    expect(controller.patchModeratedParticipant({ live, participant: initial, action: LiveModerationAction.kick })).toBe(
      live,
    );
  });

  it('live kit mic sync is needed only when server changed mic state', () => {
    expect(shouldSyncLiveKitMicAfterServerPatch({ requestedMicMuted: null, serverMicMuted: true })).toBe(false);
    expect(shouldSyncLiveKitMicAfterServerPatch({ requestedMicMuted: false, serverMicMuted: false })).toBe(false);
    expect(shouldSyncLiveKitMicAfterServerPatch({ requestedMicMuted: false, serverMicMuted: true })).toBe(true);
  });

  it('removeUserFromLive leaves unrelated state untouched', () => {
    const controller = new LiveController({ api: reducerApi() });
    const alice = _user('alice');
    const live = _live('room_2', [_participant(alice)]);
    const rooms = [_roomCard('room_1', { liveCount: 0 })];

    const patch = controller.removeUserFromLive({ live, rooms, roomId: 'room_1', userId: 'alice' });

    expect(patch.live).toBe(live);
    expect(patch.rooms).toBe(rooms);
  });

  it('mergeParticipant replaces existing session or appends new one', () => {
    const controller = new LiveController({ api: reducerApi() });
    const alice = _participant(_user('alice'));
    const bob = _participant(_user('bob'));
    const live = _live('room_1', [alice]);

    const replaced = controller.mergeParticipant(live, _participant(_user('alice'), { micMuted: true }))!;
    expect(replaced.participantCount).toBe(1);
    expect(replaced.participants[0]!.user.id).toBe('alice');
    expect(replaced.participants[0]!.micMuted).toBe(true);
    expect(replaced.updatedAt).toEqual(live.updatedAt);

    const appended = controller.mergeParticipant(live, bob)!;
    expect(appended.participantCount).toBe(2);
    expect(appended.participants.map((item) => item.user.id)).toEqual(['alice', 'bob']);
    expect(appended.updatedAt).toEqual(live.updatedAt);

    expect(controller.mergeParticipant(null, bob)).toBeNull();
  });

  it('local participant patch does not reject newer reconnect snapshot', () => {
    const api = reducerApi();
    const liveController = new LiveController({ api });
    const roomsController = new RoomsController({ api });
    const initial = _live('room_1', [
      _participant(_user('me')),
      _participant(_user('other'), { micMuted: true, headphonesMuted: false }),
    ]);

    const locallyPatched = liveController.mergeParticipant(initial, _participant(_user('me'), { micMuted: true }))!;
    const reconnectSnapshot = new LiveState({
      roomId: initial.roomId,
      participantCount: initial.participantCount,
      participants: [
        _participant(_user('me'), { micMuted: true }),
        _participant(_user('other'), {
          micMuted: false,
          headphonesMuted: true,
          headphonesListening: false,
          cameraOn: true,
        }).copyWith({
          cameraMirrored: true,
          connectionState: 'online',
          screenViewers: [_user('viewer')],
        }),
      ],
      updatedAt: new Date(initial.updatedAt.getTime() + 1),
    });

    const patch = roomsController.patchSelectedLiveRefreshed({
      live: reconnectSnapshot,
      selectedRoomId: 'room_1',
      currentLive: locallyPatched,
    });

    expect(patch).not.toBeNull();
    const other = patch!.live.participants.find((participant) => participant.user.id === 'other')!;
    expect(other.micMuted).toBe(false);
    expect(other.headphonesMuted).toBe(true);
    expect(other.headphonesListening).toBe(false);
    expect(other.cameraOn).toBe(true);
    expect(other.cameraMirrored).toBe(true);
    expect(other.screenSharing).toBe(false);
    expect(other.connectionState).toBe('online');
    expect(other.screenViewers.map((viewer) => viewer.id)).toEqual(['viewer']);
  });

  it('moderation actions expose their wire values', () => {
    expect(LiveModerationAction.values.map((action) => action.apiValue)).toEqual([
      'kick',
      'mute_mic',
      'block_voice',
      'restore_voice',
      'restore_headphones',
    ]);
  });
});

function _userJson(id: string): JsonMap {
  return {
    id,
    username: id,
    display_name: `User ${id}`,
    default_avatar_key: 'blue-3',
  };
}

function _participantJson(opts: { user: JsonMap; connectionState?: string }): JsonMap {
  return {
    live_session_id: `live_${String(opts.user['id'])}`,
    user: opts.user,
    joined_at: '2026-06-05T00:00:00Z',
    mic_muted: false,
    headphones_muted: false,
    voice_blocked: false,
    camera_on: false,
    screen_sharing: false,
    connection_state: opts.connectionState ?? 'connected',
  };
}

function _user(id: string): UserSummary {
  return new UserSummary({
    id,
    username: id,
    displayName: `User ${id}`,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
  });
}

function _participant(
  user: UserSummary,
  opts: {
    micMuted?: boolean;
    micBlocked?: boolean;
    headphonesMuted?: boolean;
    headphonesBlocked?: boolean;
    headphonesListening?: boolean;
    voiceBlocked?: boolean;
    cameraOn?: boolean;
    screenSharing?: boolean;
  } = {},
): LiveParticipant {
  return new LiveParticipant({
    liveSessionId: `live_${user.id}`,
    user,
    joinedAt: new Date(Date.UTC(2026, 5, 5)),
    micMuted: opts.micMuted ?? false,
    micBlocked: opts.micBlocked ?? false,
    headphonesMuted: opts.headphonesMuted ?? false,
    headphonesBlocked: opts.headphonesBlocked ?? false,
    headphonesListening: opts.headphonesListening ?? true,
    voiceBlocked: opts.voiceBlocked ?? false,
    cameraOn: opts.cameraOn ?? false,
    screenSharing: opts.screenSharing ?? false,
    connectionState: 'connected',
  });
}

function _liveJoinResult(opts: { participant: LiveParticipant; live: LiveState }): LiveJoinResult {
  return new LiveJoinResult({
    liveKit: new LiveKitConnectionInfo({
      serverUrl: 'wss://live.example.test',
      token: 'token',
      tokenExpiresAt: new Date(Date.UTC(2026, 5, 5, 1)),
      roomName: opts.live.roomId,
    }),
    participant: opts.participant,
    live: opts.live,
  });
}

function _live(roomId: string, participants: LiveParticipant[]): LiveState {
  return new LiveState({
    roomId,
    participantCount: participants.length,
    participants,
    updatedAt: new Date(Date.UTC(2026, 5, 5)),
  });
}

function _roomCard(id: string, opts: { liveCount?: number; livePreview?: UserSummary[] } = {}): RoomCard {
  return new RoomCard({
    id,
    name: `Room ${id}`,
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    memberCount: 2,
    liveParticipantCount: opts.liveCount ?? 0,
    liveAvatarPreview: opts.livePreview ?? [],
    lastMessage: null,
    unreadCount: 0,
    updatedAt: new Date(Date.UTC(2026, 5, 5)),
  });
}
