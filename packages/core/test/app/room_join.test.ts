import { expect, test } from 'vitest';

import {
  canStartPublicRoomAction,
  canStartRoomInviteDecision,
  hasRoomSearchQuery,
  joinRequestReasonValue,
  pendingRoomIdsAfterJoinResult,
  pendingRoomInviteSubtitle,
  publicRoomJoinCandidates,
  publicRoomJoinSubtitle,
  roomJoinInviteDecisionFailed,
  roomJoinInviteDecisionFinished,
  roomJoinInviteDecisionStarted,
  roomJoinInviteDecisionSucceeded,
  roomJoinInvitesLoadFailed,
  roomJoinInvitesLoadStarted,
  roomJoinInvitesLoadSucceeded,
  roomJoinPublicActionFailed,
  roomJoinPublicActionFinished,
  roomJoinPublicActionPending,
  roomJoinPublicActionStarted,
  roomJoinSearchCleared,
  roomJoinSearchFailed,
  roomJoinSearchQueryChanged,
  roomJoinSearchStarted,
  roomJoinSearchSucceeded,
  shouldShowRoomInviteSection,
} from '../../src/app/room_join';
import {
  JoinRoomResult,
  LiveState,
  PublicRoom,
  RoomDetail,
  RoomInvite,
  RoomMembership,
  UserSummary,
} from '../../src/protocol/models';

test('publicRoomJoinCandidates marks pending from server and local state', () => {
  const candidates = publicRoomJoinCandidates({
    rooms: [
      _room('joined', { joined: true }),
      _room('server_pending', { joinState: 'pending' }),
      _room('local_pending'),
      _room('open'),
    ],
    pendingRoomIds: ['local_pending'],
  });

  expect(candidates.map((item) => item.pending)).toEqual([false, true, true, false]);
  expect(candidates[0]!.opensJoinedRoom).toBe(true);
  expect(candidates[1]!.actionable).toBe(false);
  expect(candidates[3]!.actionable).toBe(true);
});

test('publicRoomJoinCandidates disables only the busy room action', () => {
  const candidates = publicRoomJoinCandidates({
    rooms: [_room('busy'), _room('idle')],
    busyRoomId: 'busy',
  });

  expect(candidates[0]!.busy).toBe(true);
  expect(candidates[0]!.actionEnabled).toBe(false);
  expect(candidates[1]!.busy).toBe(false);
  expect(candidates[1]!.actionEnabled).toBe(true);
});

test('pendingRoomIdsAfterJoinResult tracks approval-required joins', () => {
  expect(
    pendingRoomIdsAfterJoinResult({
      pendingRoomIds: ['existing'],
      room: _room('room_1'),
      result: new JoinRoomResult({ pending: true }),
    }),
  ).toEqual(new Set(['existing', 'room_1']));
  expect(
    pendingRoomIdsAfterJoinResult({
      pendingRoomIds: ['existing'],
      room: _room('room_1'),
      result: new JoinRoomResult({ room: _roomDetail() }),
    }),
  ).toEqual(new Set(['existing']));
});

test('joinRequestReasonValue trims optional application text', () => {
  expect(joinRequestReasonValue('  hello team  ')).toBe('hello team');
  expect(joinRequestReasonValue('   ')).toBeNull();
});

test('join dialog search and invite list patches preserve state semantics', () => {
  const existingResults = [_room('existing')];
  const invites = [_invite('invite_1')];

  const cleared = roomJoinSearchCleared();
  expect(cleared.results).toEqual([]);
  expect(cleared.searching).toBe(false);
  expect(cleared.error).toBeNull();

  const searchStarted = roomJoinSearchStarted({ results: existingResults, error: 'stale error' });
  expect(searchStarted.results.map((room) => room.id)).toEqual(['existing']);
  expect(searchStarted.searching).toBe(true);
  expect(searchStarted.error).toBe('stale error');

  const inputStarted = roomJoinSearchQueryChanged({ rawQuery: '  room  ', results: existingResults, error: 'stale error' });
  expect(inputStarted.query).toBe('room');
  expect(inputStarted.shouldSearch).toBe(true);
  expect(inputStarted.shouldCancelInFlightSearch).toBe(true);
  expect(inputStarted.search.results.map((room) => room.id)).toEqual(['existing']);
  expect(inputStarted.search.searching).toBe(true);
  expect(inputStarted.search.error).toBe('stale error');

  const inputCleared = roomJoinSearchQueryChanged({ rawQuery: '   ', results: existingResults, error: 'stale error' });
  expect(inputCleared.query).toBe('');
  expect(inputCleared.shouldSearch).toBe(false);
  expect(inputCleared.shouldCancelInFlightSearch).toBe(true);
  expect(inputCleared.search.results).toEqual([]);
  expect(inputCleared.search.searching).toBe(false);
  expect(inputCleared.search.error).toBeNull();

  const searchSucceeded = roomJoinSearchSucceeded({ results: [_room('room_2')] });
  expect(searchSucceeded.results.map((room) => room.id)).toEqual(['room_2']);
  expect(searchSucceeded.searching).toBe(false);
  expect(searchSucceeded.error).toBeNull();

  const searchFailed = roomJoinSearchFailed({ results: existingResults, failure: 'search failed' });
  expect(searchFailed.results.map((room) => room.id)).toEqual(['existing']);
  expect(searchFailed.searching).toBe(false);
  expect(searchFailed.error).toBe('操作失败，请稍后重试');

  const invitesStarted = roomJoinInvitesLoadStarted({ invites });
  expect(invitesStarted.invites.map((invite) => invite.id)).toEqual(['invite_1']);
  expect(invitesStarted.loading).toBe(true);
  expect(invitesStarted.error).toBeNull();

  const invitesSucceeded = roomJoinInvitesLoadSucceeded({ invites });
  expect(invitesSucceeded.invites.map((invite) => invite.id)).toEqual(['invite_1']);
  expect(invitesSucceeded.loading).toBe(false);
  expect(invitesSucceeded.error).toBeNull();

  const invitesFailed = roomJoinInvitesLoadFailed({ invites, failure: 'load failed' });
  expect(invitesFailed.invites.map((invite) => invite.id)).toEqual(['invite_1']);
  expect(invitesFailed.loading).toBe(false);
  expect(invitesFailed.error).toBe('操作失败，请稍后重试');
});

test('join dialog public room action patches track busy and pending state', () => {
  const started = roomJoinPublicActionStarted({ roomId: 'room_1', pendingRoomIds: ['existing'] });
  expect(started.busyRoomId).toBe('room_1');
  expect(started.error).toBeNull();
  expect(started.pendingRoomIds).toEqual(new Set(['existing']));

  const pending = roomJoinPublicActionPending({
    busyRoomId: 'room_1',
    error: null,
    pendingRoomIds: started.pendingRoomIds,
    room: _room('room_2'),
    result: new JoinRoomResult({ pending: true }),
  });
  expect(pending.busyRoomId).toBe('room_1');
  expect(pending.error).toBeNull();
  expect(pending.pendingRoomIds).toEqual(new Set(['existing', 'room_2']));

  const failed = roomJoinPublicActionFailed({
    busyRoomId: 'room_1',
    pendingRoomIds: pending.pendingRoomIds,
    failure: 'join failed',
  });
  expect(failed.busyRoomId).toBe('room_1');
  expect(failed.error).toBe('操作失败，请稍后重试');
  expect(failed.pendingRoomIds).toEqual(new Set(['existing', 'room_2']));

  const finished = roomJoinPublicActionFinished({ error: failed.error, pendingRoomIds: failed.pendingRoomIds });
  expect(finished.busyRoomId).toBeNull();
  expect(finished.error).toBe('操作失败，请稍后重试');
  expect(finished.pendingRoomIds).toEqual(new Set(['existing', 'room_2']));
});

test('join dialog invite decision patches update invites and pending rooms', () => {
  const invites = [_invite('invite_1'), _invite('invite_2')];

  const started = roomJoinInviteDecisionStarted({ invites, pendingRoomIds: ['existing'], inviteId: 'invite_1' });
  expect(started.invites.map((invite) => invite.id)).toEqual(['invite_1', 'invite_2']);
  expect(started.pendingRoomIds).toEqual(new Set(['existing']));
  expect(started.busyInviteId).toBe('invite_1');
  expect(started.error).toBeNull();

  const succeeded = roomJoinInviteDecisionSucceeded({
    invites: started.invites,
    pendingRoomIds: started.pendingRoomIds,
    invite: invites[0]!,
    accept: true,
    result: new JoinRoomResult({ pending: true }),
  });
  expect(succeeded.invites.map((invite) => invite.id)).toEqual(['invite_2']);
  expect(succeeded.pendingRoomIds).toEqual(new Set(['existing', invites[0]!.room.id]));
  expect(succeeded.busyInviteId).toBeNull();
  expect(succeeded.error).toBeNull();

  const failed = roomJoinInviteDecisionFailed({
    invites: started.invites,
    pendingRoomIds: started.pendingRoomIds,
    busyInviteId: started.busyInviteId,
    failure: 'review failed',
  });
  expect(failed.invites.map((invite) => invite.id)).toEqual(['invite_1', 'invite_2']);
  expect(failed.pendingRoomIds).toEqual(new Set(['existing']));
  expect(failed.busyInviteId).toBe('invite_1');
  expect(failed.error).toBe('操作失败，请稍后重试');

  const finished = roomJoinInviteDecisionFinished({
    invites: failed.invites,
    pendingRoomIds: failed.pendingRoomIds,
    error: failed.error,
  });
  expect(finished.invites.map((invite) => invite.id)).toEqual(['invite_1', 'invite_2']);
  expect(finished.pendingRoomIds).toEqual(new Set(['existing']));
  expect(finished.busyInviteId).toBeNull();
  expect(finished.error).toBe('操作失败，请稍后重试');
});

test('join dialog state helpers expose reusable gates', () => {
  expect(hasRoomSearchQuery('  room  ')).toBe(true);
  expect(hasRoomSearchQuery('   ')).toBe(false);
  expect(shouldShowRoomInviteSection({ loadingInvites: true, invites: [] })).toBe(true);
  expect(shouldShowRoomInviteSection({ loadingInvites: false, invites: [_invite('invite_1')] })).toBe(true);
  expect(shouldShowRoomInviteSection({ loadingInvites: false, invites: [] })).toBe(false);
  expect(canStartPublicRoomAction(null)).toBe(true);
  expect(canStartPublicRoomAction('room_1')).toBe(false);
  expect(canStartRoomInviteDecision({ busyInviteId: null, busyRoomId: null })).toBe(true);
  expect(canStartRoomInviteDecision({ busyInviteId: 'invite_1', busyRoomId: null })).toBe(false);
  expect(canStartRoomInviteDecision({ busyInviteId: null, busyRoomId: 'room_1' })).toBe(false);
});

test('join dialog display helpers format room and invite subtitles', () => {
  expect(publicRoomJoinSubtitle(_room('1001'))).toBe('1001 · 3 名成员');
  expect(publicRoomJoinSubtitle(_room('no_rid', { rid: '' }))).toBe('3 名成员');
  expect(publicRoomJoinSubtitle(_room('spaced', { rid: '  R002  ' }))).toBe('R002 · 3 名成员');

  expect(pendingRoomInviteSubtitle(_invite('invite_1', { rid: 'R100' }))).toBe('邀请你加入 · RID R100');
  expect(pendingRoomInviteSubtitle(_invite('invite_2', { rid: '' }))).toBe('邀请你加入');
});

function _room(
  id: string,
  opts: { rid?: string | null; joined?: boolean; joinPolicy?: string; joinState?: string } = {},
): PublicRoom {
  const { joined = false, joinPolicy = 'open', joinState = 'none' } = opts;
  return new PublicRoom({
    id,
    rid: opts.rid ?? id,
    name: `Room ${id}`,
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    visibility: 'public',
    joinPolicy,
    memberCount: 3,
    liveParticipantCount: 0,
    joined,
    joinState: joined ? 'joined' : joinState,
  });
}

function _roomDetail(): RoomDetail {
  return new RoomDetail({
    id: 'room_1',
    name: 'Room',
    rid: 'R001',
    visibility: 'public',
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    memberCount: 1,
    description: '',
    createdBy: null,
    myMembership: new RoomMembership({
      joinedAt: new Date(Date.UTC(2026, 5, 4)),
      role: 'member',
    }),
    live: new LiveState({
      roomId: 'room_1',
      participantCount: 0,
      participants: [],
      updatedAt: new Date(Date.UTC(2026, 5, 4)),
    }),
    createdAt: new Date(Date.UTC(2026, 5, 4)),
    updatedAt: new Date(Date.UTC(2026, 5, 4)),
  });
}

function _invite(id: string, opts: { rid?: string | null } = {}): RoomInvite {
  return new RoomInvite({
    id,
    status: 'pending',
    room: _room(`room_${id}`, { rid: opts.rid }),
    inviter: new UserSummary({
      id: 'inviter',
      username: 'inviter',
      displayName: 'Inviter',
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
    }),
    createdAt: new Date(Date.UTC(2026, 5, 4)),
  });
}
