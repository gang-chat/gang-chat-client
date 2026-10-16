import { expect, test } from 'vitest';

import {
  canStartRoomInvite,
  RoomInviteCandidate,
  RoomInviteSearchBodyState,
  reviewedRoomInvites,
  roomInviteCandidates,
  roomInviteSearchBodyState,
  roomInvitesEnabled,
  roomMemberInviteActionFailed,
  roomMemberInviteActionStarted,
  roomMemberInviteActionSucceeded,
  roomMemberInviteFailed,
  roomMemberInviteSearchCleared,
  roomMemberInviteSearchFailed,
  roomMemberInviteSearchQueryChanged,
  roomMemberInviteSearchStarted,
  roomMemberInviteSearchSucceeded,
  roomMemberInviteStarted,
  roomMemberInviteSucceeded,
} from '../../src/app/room_invites';
import {
  JoinRoomResult,
  PublicRoom,
  RoomInvite,
  RoomMember,
  UserCommonRoom,
  UserSummary,
} from '../../src/protocol/models';

test('roomInviteCandidates combines search results and matching members', () => {
  const candidates = roomInviteCandidates({
    searchResults: [_user('external', { displayName: 'Alpha External' }), _user('member', { displayName: 'Alpha Member' })],
    members: [_member('member', { uid: '1001' }), _member('remark', { remarkName: 'Alpha note' })],
    query: 'alpha',
  });

  expect(candidates.map((item) => item.user.id)).toEqual(['external', 'member', 'remark']);
  expect(candidates[0]!.existing).toBe(false);
  expect(candidates[1]!.existing).toBe(true);
  expect(candidates[2]!.existing).toBe(true);
});

test('roomInviteCandidates ignores room names in search results and members', () => {
  const candidates = roomInviteCandidates({
    searchResults: [
      _user('room_context', {
        roomDisplayName: 'Alpha Room Alias',
        commonRooms: [
          new UserCommonRoom({
            id: 'room_1',
            rid: 'R10001',
            name: 'Alpha Common Room',
            roomDisplayName: 'Alpha Shared Name',
          }),
        ],
      }),
      _user('display_match', { displayName: 'Alpha Person' }),
    ],
    members: [_member('room_member', { roomDisplayName: 'Alpha Current Room Name' })],
    query: 'alpha',
  });

  expect(candidates.map((item) => item.user.id)).toEqual(['display_match']);
});

test('roomInviteCandidates marks pending and busy invite states', () => {
  const candidates = roomInviteCandidates({
    searchResults: [_user('pending'), _user('busy')],
    members: [],
    query: 'user',
    pendingInviteUserIds: ['pending'],
    busyUserIds: ['busy'],
  });

  expect(candidates[0]!.pending).toBe(true);
  expect(candidates[0]!.inviteActionEnabled).toBe(false);
  expect(candidates[0]!.canStartInvite).toBe(false);
  expect(candidates[1]!.busy).toBe(true);
  expect(candidates[1]!.inviteActionEnabled).toBe(true);
  expect(candidates[1]!.canStartInvite).toBe(false);
});

test('roomInviteCandidates filters out superusers', () => {
  const candidates = roomInviteCandidates({
    searchResults: [_user('super', { isSuperuser: true }), _user('normal')],
    members: [_member('member_super', { isSuperuser: true }), _member('member')],
    query: 'user',
  });

  expect(candidates.map((item) => item.user.id)).toEqual(['normal', 'member']);
});

test('roomInviteSearchBodyState describes loading prompt empty and results', () => {
  const candidate = new RoomInviteCandidate({
    user: _user('user_1'),
    existing: false,
    pending: false,
    busy: false,
  });

  expect(roomInviteSearchBodyState({ searching: true, query: '', candidates: [] })).toBe(
    RoomInviteSearchBodyState.loading,
  );
  expect(roomInviteSearchBodyState({ searching: false, query: '   ', candidates: [candidate] })).toBe(
    RoomInviteSearchBodyState.prompt,
  );
  expect(roomInviteSearchBodyState({ searching: false, query: 'logan', candidates: [] })).toBe(
    RoomInviteSearchBodyState.empty,
  );
  expect(roomInviteSearchBodyState({ searching: false, query: 'logan', candidates: [candidate] })).toBe(
    RoomInviteSearchBodyState.results,
  );
});

test('canStartRoomInvite rejects existing pending and busy users', () => {
  const members = [_member('existing')];

  expect(roomInvitesEnabled('open')).toBe(true);
  expect(roomInvitesEnabled('approval_required')).toBe(true);
  expect(roomInvitesEnabled('closed')).toBe(false);
  expect(canStartRoomInvite({ userId: 'existing', members })).toBe(false);
  expect(canStartRoomInvite({ userId: 'pending', members, pendingInviteUserIds: ['pending'] })).toBe(false);
  expect(canStartRoomInvite({ userId: 'busy', members, busyUserIds: ['busy'] })).toBe(false);
  expect(canStartRoomInvite({ userId: 'new', members })).toBe(true);
  expect(canStartRoomInvite({ userId: 'new', members, invitesEnabled: false })).toBe(false);
  expect(canStartRoomInvite({ userId: 'super', members, isSuperuser: true })).toBe(false);
});

test('roomInviteCandidates disables invite actions when room is closed', () => {
  const candidates = roomInviteCandidates({
    searchResults: [_user('new')],
    members: [],
    query: 'user',
    invitesEnabled: false,
  });

  expect(candidates).toHaveLength(1);
  expect(candidates[0]!.existing).toBe(true);
  expect(candidates[0]!.inviteActionEnabled).toBe(false);
  expect(candidates[0]!.canStartInvite).toBe(false);
});

test('room member invite action reducers update pending and busy ids', () => {
  const started = roomMemberInviteActionStarted({
    userId: 'new',
    pendingInviteUserIds: ['pending'],
    busyUserIds: ['other_busy'],
  });
  expect(started.pendingInviteUserIds).toEqual(new Set(['pending']));
  expect(started.busyUserIds).toEqual(new Set(['other_busy', 'new']));

  const succeeded = roomMemberInviteActionSucceeded({
    userId: 'new',
    pendingInviteUserIds: started.pendingInviteUserIds,
    busyUserIds: started.busyUserIds,
  });
  expect(succeeded.pendingInviteUserIds).toEqual(new Set(['pending', 'new']));
  expect(succeeded.busyUserIds).toEqual(new Set(['other_busy']));

  const failed = roomMemberInviteActionFailed({
    userId: 'new',
    pendingInviteUserIds: started.pendingInviteUserIds,
    busyUserIds: started.busyUserIds,
  });
  expect(failed.pendingInviteUserIds).toEqual(new Set(['pending']));
  expect(failed.busyUserIds).toEqual(new Set(['other_busy']));
});

test('room member invite dialog search patches preserve ui state', () => {
  const existingResults = [_user('existing')];
  const ids = { pendingInviteUserIds: ['pending'], busyUserIds: ['busy'] };

  const cleared = roomMemberInviteSearchCleared(ids);
  expect(cleared.searchResults).toEqual([]);
  expect(cleared.searching).toBe(false);
  expect(cleared.error).toBeNull();
  expect(cleared.pendingInviteUserIds).toEqual(new Set(['pending']));
  expect(cleared.busyUserIds).toEqual(new Set(['busy']));

  const started = roomMemberInviteSearchStarted({ searchResults: existingResults, ...ids });
  expect(started.searchResults).toEqual(existingResults);
  expect(started.searching).toBe(true);
  expect(started.error).toBeNull();
  expect(started.pendingInviteUserIds).toEqual(new Set(['pending']));
  expect(started.busyUserIds).toEqual(new Set(['busy']));

  const inputStarted = roomMemberInviteSearchQueryChanged({
    rawQuery: '  user  ',
    searchResults: existingResults,
    ...ids,
  });
  expect(inputStarted.query).toBe('user');
  expect(inputStarted.shouldSearch).toBe(true);
  expect(inputStarted.shouldCancelInFlightSearch).toBe(true);
  expect(inputStarted.dialog.searchResults).toEqual(existingResults);
  expect(inputStarted.dialog.searching).toBe(true);
  expect(inputStarted.dialog.pendingInviteUserIds).toEqual(new Set(['pending']));
  expect(inputStarted.dialog.busyUserIds).toEqual(new Set(['busy']));

  const inputCleared = roomMemberInviteSearchQueryChanged({
    rawQuery: '   ',
    searchResults: existingResults,
    ...ids,
  });
  expect(inputCleared.query).toBe('');
  expect(inputCleared.shouldSearch).toBe(false);
  expect(inputCleared.shouldCancelInFlightSearch).toBe(true);
  expect(inputCleared.dialog.searchResults).toEqual([]);
  expect(inputCleared.dialog.searching).toBe(false);
  expect(inputCleared.dialog.error).toBeNull();
  expect(inputCleared.dialog.pendingInviteUserIds).toEqual(new Set(['pending']));
  expect(inputCleared.dialog.busyUserIds).toEqual(new Set(['busy']));

  const succeeded = roomMemberInviteSearchSucceeded({ searchResults: [_user('result')], ...ids });
  expect(succeeded.searchResults.map((user) => user.id)).toEqual(['result']);
  expect(succeeded.searching).toBe(false);
  expect(succeeded.error).toBeNull();
  expect(succeeded.pendingInviteUserIds).toEqual(new Set(['pending']));
  expect(succeeded.busyUserIds).toEqual(new Set(['busy']));

  const failed = roomMemberInviteSearchFailed({
    searchResults: existingResults,
    ...ids,
    failure: new Error('search failed'),
  });
  expect(failed.searchResults).toEqual(existingResults);
  expect(failed.searching).toBe(false);
  expect(failed.error).toBe('操作失败，请稍后重试');
  expect(failed.pendingInviteUserIds).toEqual(new Set(['pending']));
  expect(failed.busyUserIds).toEqual(new Set(['busy']));
});

test('room member invite dialog action patches update invite state', () => {
  const results = [_user('new')];

  const started = roomMemberInviteStarted({
    searchResults: results,
    searching: false,
    userId: 'new',
    pendingInviteUserIds: ['pending'],
    busyUserIds: ['other_busy'],
  });
  expect(started.searchResults).toEqual(results);
  expect(started.searching).toBe(false);
  expect(started.error).toBeNull();
  expect(started.pendingInviteUserIds).toEqual(new Set(['pending']));
  expect(started.busyUserIds).toEqual(new Set(['other_busy', 'new']));

  const succeeded = roomMemberInviteSucceeded({
    searchResults: results,
    searching: true,
    error: 'kept',
    userId: 'new',
    pendingInviteUserIds: started.pendingInviteUserIds,
    busyUserIds: started.busyUserIds,
  });
  expect(succeeded.searchResults).toEqual(results);
  expect(succeeded.searching).toBe(true);
  expect(succeeded.error).toBe('kept');
  expect(succeeded.pendingInviteUserIds).toEqual(new Set(['pending', 'new']));
  expect(succeeded.busyUserIds).toEqual(new Set(['other_busy']));

  const failed = roomMemberInviteFailed({
    searchResults: results,
    searching: true,
    userId: 'new',
    pendingInviteUserIds: started.pendingInviteUserIds,
    busyUserIds: started.busyUserIds,
    failure: new Error('invite failed'),
  });
  expect(failed.searchResults).toEqual(results);
  expect(failed.searching).toBe(true);
  expect(failed.error).toBe('操作失败，请稍后重试');
  expect(failed.pendingInviteUserIds).toEqual(new Set(['pending']));
  expect(failed.busyUserIds).toEqual(new Set(['other_busy']));
});

test('reviewedRoomInvites removes invite and tracks pending room ids', () => {
  const invite = _invite('invite_1', { roomId: 'room_1' });
  const relatedInvite = _invite('invite_3', { roomId: 'room_1' });
  const state = reviewedRoomInvites({
    invites: [invite, _invite('invite_2', { roomId: 'room_2' }), relatedInvite],
    pendingRoomIds: ['existing_room'],
    invite,
    accept: true,
    result: new JoinRoomResult({ pending: true }),
  });

  expect(state.invites.map((item) => item.id)).toEqual(['invite_2']);
  expect(state.pendingRoomIds).toEqual(new Set(['existing_room', 'room_1']));
  expect(state.hasPendingInvites).toBe(true);

  const rejected = reviewedRoomInvites({
    invites: [invite, relatedInvite],
    pendingRoomIds: [],
    invite,
    accept: false,
    result: new JoinRoomResult(),
  });

  expect(rejected.invites.map((item) => item.id)).toEqual(['invite_3']);
  expect(rejected.pendingRoomIds.size).toBe(0);
  expect(rejected.hasPendingInvites).toBe(true);
});

function _member(
  id: string,
  opts: { uid?: string | null; remarkName?: string | null; roomDisplayName?: string | null; isSuperuser?: boolean } = {},
): RoomMember {
  return new RoomMember({
    user: _user(id, { uid: opts.uid, isSuperuser: opts.isSuperuser }),
    role: 'member',
    joinedAt: new Date(Date.UTC(2026, 5, 5)),
    roomDisplayName: opts.roomDisplayName ?? null,
    remarkName: opts.remarkName ?? null,
  });
}

function _user(
  id: string,
  opts: {
    uid?: string | null;
    displayName?: string | null;
    roomDisplayName?: string | null;
    commonRooms?: UserCommonRoom[];
    isSuperuser?: boolean;
  } = {},
): UserSummary {
  return new UserSummary({
    id,
    username: `user_${id}`,
    displayName: opts.displayName ?? `User ${id}`,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
    uid: opts.uid ?? null,
    roomDisplayName: opts.roomDisplayName ?? null,
    commonRooms: opts.commonRooms ?? [],
    isSuperuser: opts.isSuperuser ?? false,
  });
}

function _invite(id: string, opts: { roomId: string }): RoomInvite {
  const { roomId } = opts;
  return new RoomInvite({
    id,
    status: 'pending',
    room: new PublicRoom({
      id: roomId,
      rid: roomId,
      name: roomId,
      avatarUrl: null,
      defaultAvatarKey: 'room-1',
      visibility: 'public',
      joinPolicy: 'approval_required',
      memberCount: 3,
      liveParticipantCount: 0,
      joined: false,
      joinState: 'none',
    }),
    inviter: _user(`inviter_${id}`),
    createdAt: new Date(Date.UTC(2026, 5, 5)),
  });
}
