import { expect, test } from 'vitest';

import {
  canApplyJoinRequestBadgeRefresh,
  canStartJoinRequestReview,
  JoinRequestListBodyState,
  joinRequestBadgeRefreshTarget,
  joinRequestCandidates,
  joinRequestDetailReasonText,
  joinRequestFromInvitation,
  joinRequestListBodyState,
  joinRequestReasonText,
  joinRequestReviewFailed,
  joinRequestReviewStarted,
  joinRequestReviewSucceeded,
  joinRequestSourceText,
  shouldRefreshJoinRequestBadgeForEvent,
} from '../../src/app/room_join_requests';
import { JoinRequest, LiveState, RoomDetail, RoomMembership, UserSummary } from '../../src/protocol/models';

test('joinRequestCandidates marks busy requests and pending info role', () => {
  const candidates = joinRequestCandidates({
    requests: [_request('a'), _request('b')],
    busyRequestIds: ['b'],
  });

  expect(candidates.map((item) => item.request.id)).toEqual(['a', 'b']);
  expect(candidates[0]!.busy).toBe(false);
  expect(candidates[0]!.canReview).toBe(true);
  expect(candidates[0]!.userForInfo.roomRole).toBe('pending');
  expect(candidates[1]!.busy).toBe(true);
  expect(candidates[1]!.canReview).toBe(false);
});

test('canStartJoinRequestReview rejects busy request ids', () => {
  expect(canStartJoinRequestReview({ requestId: 'busy', busyRequestIds: ['busy'] })).toBe(false);
  expect(canStartJoinRequestReview({ requestId: 'idle' })).toBe(true);
});

test('joinRequestReasonText returns trimmed reason only when present', () => {
  expect(joinRequestReasonText(_request('with_reason', { reason: '  hello  ' }))).toBe('hello');
  expect(joinRequestReasonText(_request('blank_reason', { reason: '  ' }))).toBeNull();
  expect(joinRequestDetailReasonText(_request('blank_reason'))).toBe('未填写');
});

test('join request source helpers describe public search and invitations', () => {
  const publicSearch = _request('public');
  expect(joinRequestFromInvitation(publicSearch)).toBe(false);
  expect(joinRequestSourceText(publicSearch)).toBe('公开房间搜索');

  const invited = _request('invited', {
    source: 'invitation',
    inviters: [_user('inviter_1', { displayName: 'Inviter One' })],
  });
  expect(joinRequestFromInvitation(invited)).toBe(true);
  expect(joinRequestSourceText(invited)).toBe('Inviter One 的邀请');
});

test('join request badge refresh gate follows room and permission', () => {
  expect(
    shouldRefreshJoinRequestBadgeForEvent({
      data: { room_id: 'room_1' },
      selectedRoomId: 'room_1',
      canReviewJoinRequests: true,
    }),
  ).toBe(true);
  expect(
    shouldRefreshJoinRequestBadgeForEvent({
      data: { room_id: 'room_2' },
      selectedRoomId: 'room_1',
      canReviewJoinRequests: true,
    }),
  ).toBe(false);
  expect(
    shouldRefreshJoinRequestBadgeForEvent({
      data: { room_id: 'room_1' },
      selectedRoomId: 'room_1',
      canReviewJoinRequests: false,
    }),
  ).toBe(false);
  expect(
    shouldRefreshJoinRequestBadgeForEvent({
      data: {},
      selectedRoomId: 'room_1',
      canReviewJoinRequests: true,
    }),
  ).toBe(false);
});

test('join request badge refresh target follows room role', () => {
  const adminRoom = _room('room_1', { role: 'admin' });
  const memberRoom = _room('room_2', { role: 'member' });

  expect(joinRequestBadgeRefreshTarget({ room: adminRoom, canReviewJoinRequests: true })).toBe(adminRoom);
  expect(joinRequestBadgeRefreshTarget({ room: memberRoom, canReviewJoinRequests: false })).toBeNull();
  expect(joinRequestBadgeRefreshTarget({ room: memberRoom, canReviewJoinRequests: true })).toBe(memberRoom);
  expect(joinRequestBadgeRefreshTarget({ room: null, canReviewJoinRequests: true })).toBeNull();

  expect(canApplyJoinRequestBadgeRefresh({ targetRoomId: 'room_1', selectedRoomId: 'room_1' })).toBe(true);
  expect(canApplyJoinRequestBadgeRefresh({ targetRoomId: 'room_1', selectedRoomId: 'room_2' })).toBe(false);
});

test('join request review reducers update requests and busy ids', () => {
  const requests = [_request('a'), _request('b')];

  const started = joinRequestReviewStarted({ requests, requestId: 'a', busyRequestIds: ['other'] });
  expect(started.requests.map((request) => request.id)).toEqual(['a', 'b']);
  expect(started.busyRequestIds).toEqual(new Set(['other', 'a']));
  expect(started.changed).toBe(false);
  expect(started.hasPendingRequests).toBe(true);

  const succeeded = joinRequestReviewSucceeded({
    requests: started.requests,
    requestId: 'a',
    busyRequestIds: started.busyRequestIds,
  });
  expect(succeeded.requests.map((request) => request.id)).toEqual(['b']);
  expect(succeeded.busyRequestIds).toEqual(new Set(['other']));
  expect(succeeded.changed).toBe(true);
  expect(succeeded.hasPendingRequests).toBe(true);

  const failed = joinRequestReviewFailed({
    requests: started.requests,
    requestId: 'a',
    busyRequestIds: started.busyRequestIds,
  });
  expect(failed.requests.map((request) => request.id)).toEqual(['a', 'b']);
  expect(failed.busyRequestIds).toEqual(new Set(['other']));
  expect(failed.changed).toBe(false);
});

test('joinRequestListBodyState separates empty and result lists', () => {
  expect(joinRequestListBodyState([])).toBe(JoinRequestListBodyState.empty);

  expect(joinRequestListBodyState(joinRequestCandidates({ requests: [_request('a')] }))).toBe(
    JoinRequestListBodyState.results,
  );
});

function _request(
  id: string,
  opts: { user?: UserSummary | null; reason?: string; source?: string; inviters?: UserSummary[] } = {},
): JoinRequest {
  return new JoinRequest({
    id,
    status: 'pending',
    reason: opts.reason ?? '',
    source: opts.source ?? 'public_search',
    inviters: opts.inviters ?? [],
    user: opts.user ?? _user(`user_${id}`),
    createdAt: new Date(Date.UTC(2026, 5, 5)),
  });
}

function _user(id: string, opts: { displayName?: string | null } = {}): UserSummary {
  return new UserSummary({
    id,
    username: id,
    displayName: opts.displayName ?? `User ${id}`,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
  });
}

function _room(id: string, opts: { role: string }): RoomDetail {
  return new RoomDetail({
    id,
    name: id,
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    memberCount: 1,
    myMembership: new RoomMembership({
      joinedAt: new Date(Date.UTC(2026, 5, 5)),
      role: opts.role,
    }),
    live: new LiveState({
      roomId: id,
      participantCount: 0,
      participants: [],
      updatedAt: new Date(Date.UTC(2026, 5, 5)),
    }),
    createdAt: new Date(Date.UTC(2026, 5, 5)),
    updatedAt: new Date(Date.UTC(2026, 5, 5)),
  });
}
