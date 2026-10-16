import { expect, test } from 'vitest';

import {
  canReviewNotificationInvite,
  canWithdrawNotificationApplication,
  isActionablePendingRoomInvite,
  isInvalidPendingRoomInvite,
  kRoomEventNotificationMemberRemoved,
  kRoomEventNotificationMentioned,
  kRoomEventNotificationRoleDemoted,
  kRoomEventNotificationRolePromoted,
  markUnreadRoomEventNotificationsRead,
  pendingRoomInviteCount,
  pendingRoomNotificationCount,
  roomApplicationReviewActionLabel,
  roomApplicationStatusLabel,
  roomInviteAcceptRequiresApplication,
  roomInviteDecisionLabel,
  roomInviteNotificationsForView,
  roomInviteRoleLabel,
  roomInviteTimestampLabel,
  RoomNotificationDateRange,
  RoomNotificationFilter,
  RoomNotificationItem,
  RoomNotificationItemType,
  roomNotificationCopyText,
  roomNotificationDeletionId,
  roomNotificationDeletionType,
  roomNotificationRoomAvatarKey,
  roomNotificationRoomAvatarLabel,
  roomNotificationRoomAvatarUrl,
  roomNotificationRoomCardEnabled,
  roomNotificationRoomLabel,
  roomNotificationsForView,
  roomNotificationUserAvatarKey,
  roomNotificationUserAvatarLabel,
  roomNotificationUserAvatarUrl,
  roomNotificationUserLabel,
} from '../../src/app/room_notifications';
import {
  PublicRoom,
  RoomApplication,
  RoomEventNotification,
  RoomInvite,
  UserCommonRoom,
  UserSummary,
} from '../../src/protocol/models';

const utc = (y: number, m: number, d: number, h = 0, min = 0) => new Date(Date.UTC(y, m - 1, d, h, min));
const local = (y: number, m: number, d: number, h = 0, min = 0) => new Date(y, m - 1, d, h, min);

test('room invite notifications sort pending first then newest first', () => {
  const accepted = _invite('accepted_old', { status: 'accepted', createdAt: utc(2026, 6, 1, 8) });
  const pendingOld = _invite('pending_old', { status: 'pending', createdAt: utc(2026, 6, 2, 8) });
  const rejectedNew = _invite('rejected_new', { status: 'rejected', createdAt: utc(2026, 6, 3, 8) });
  const invalidNew = _invite('invalid_new', {
    status: 'pending',
    createdAt: utc(2026, 6, 5, 8),
    invalidReason: 'inviter_left',
    inviter: _user('left_inviter', { roomRole: 'left' }),
  });
  const pendingNew = _invite('pending_new', { status: 'pending', createdAt: utc(2026, 6, 4, 8) });

  const visible = roomInviteNotificationsForView({
    invites: [accepted, pendingOld, rejectedNew, invalidNew, pendingNew],
    query: '',
    filter: RoomNotificationFilter.all,
  });

  expect(visible.map((invite) => invite.id)).toEqual([
    'pending_new',
    'pending_old',
    'invalid_new',
    'rejected_new',
    'accepted_old',
  ]);
  expect(pendingRoomInviteCount(visible)).toBe(2);
});

test('room invite notification search includes room and inviter fields', () => {
  const invite = _invite('invite_1', {
    roomName: 'Design Lab',
    roomRid: 'R-2026',
    inviter: _user('user_1', {
      uid: '100001',
      username: 'mira',
      displayName: 'Mira Chen',
      roomRole: 'admin',
      roomDisplayName: 'Mira in Design',
      commonRooms: [new UserCommonRoom({ id: 'shared_room', rid: 'S-1', name: 'Shared Room', remarkName: 'Shared Remark' })],
    }),
  });
  const view = (query: string, filter: RoomNotificationFilter) =>
    roomInviteNotificationsForView({ invites: [invite], query, filter });

  expect(view('design lab', RoomNotificationFilter.invites)).toEqual([invite]);
  expect(view('管理员', RoomNotificationFilter.all)).toEqual([invite]);
  expect(view('Shared Remark', RoomNotificationFilter.all)).toEqual([invite]);
  expect(view('missing', RoomNotificationFilter.all)).toEqual([]);
  expect(view('', RoomNotificationFilter.roomNotifications)).toEqual([]);
});

test('notification search does not match displayed timestamps', () => {
  const createdAt = local(2026, 6, 5, 12);
  const visible = roomNotificationsForView({
    invites: [_invite('dated_invite', { createdAt })],
    applications: [_application('dated_application', { createdAt })],
    roomEvents: [_roomEvent('dated_room_event', { createdAt })],
    query: '2026/06/05 12:00',
    filter: RoomNotificationFilter.all,
  });

  expect(visible).toEqual([]);
});

test('notification date range composes with text and type filters', () => {
  const application = _application('application_in_range', {
    status: 'approved',
    roomName: 'Date Room',
    createdAt: local(2026, 6, 5, 23, 59),
    reviewedAt: local(2026, 6, 7, 8),
    updatedAt: local(2026, 6, 7, 8),
    reviewer: _user('date_reviewer'),
  });
  const range = new RoomNotificationDateRange({ startDate: local(2026, 6, 5), endDate: local(2026, 6, 6) });

  const visible = roomNotificationsForView({
    invites: [_invite('invite_before_range', { createdAt: local(2026, 6, 4, 23, 59) })],
    applications: [application],
    roomEvents: [_roomEvent('room_event_on_end_date', { roomName: 'Date Room', createdAt: local(2026, 6, 6, 23, 59) })],
    query: 'Date Room',
    filter: RoomNotificationFilter.all,
    dateRange: range,
  });
  const applicationsOnly = roomNotificationsForView({
    invites: [],
    applications: [application],
    roomEvents: [],
    query: 'Date Room',
    filter: RoomNotificationFilter.applications,
    dateRange: range,
  });

  expect(visible.map((item) => item.id)).toEqual([
    'room-event:room_event_on_end_date',
    'application-requested:application_in_range',
  ]);
  expect(applicationsOnly.map((item) => item.id)).toEqual(['application-requested:application_in_range']);
});

test('default notification date range uses account date through today', () => {
  const range = RoomNotificationDateRange.defaultFor({
    accountCreatedAt: local(2025, 12, 3, 18, 30),
    today: local(2026, 7, 10, 23, 59),
  });

  expect(range.startDate).toEqual(local(2025, 12, 3));
  expect(range.endDate).toEqual(local(2026, 7, 10));
  expect(range.includes(local(2025, 12, 3))).toBe(true);
  expect(range.includes(local(2026, 7, 10, 23, 59))).toBe(true);
  expect(range.includes(local(2025, 12, 2, 23, 59))).toBe(false);
  expect(
    range.equals(new RoomNotificationDateRange({ startDate: local(2025, 12, 3, 9), endDate: local(2026, 7, 10, 1) })),
  ).toBe(true);
  expect(() => new RoomNotificationDateRange({ startDate: local(2026, 7, 10), endDate: local(2026, 7, 9) })).toThrow();
});

test('room invite notification display helpers format labels', () => {
  const pending = _invite('pending', { status: 'pending' });
  const invalid = _invite('invalid', {
    status: 'pending',
    invalidReason: 'inviter_left',
    inviter: _user('left_inviter', { roomRole: 'left' }),
  });
  const deletedInviterWithoutReason = _invite('deleted_inviter_without_reason', {
    status: 'pending',
    inviterExists: false,
  });
  const accepted = _invite('accepted', { status: 'accepted' });
  const rejected = _invite('rejected', { status: 'rejected' });

  expect(canReviewNotificationInvite({ invite: pending, busyInviteId: null })).toBe(true);
  expect(canReviewNotificationInvite({ invite: pending, busyInviteId: 'pending' })).toBe(false);
  expect(canReviewNotificationInvite({ invite: accepted, busyInviteId: null })).toBe(false);
  expect(canReviewNotificationInvite({ invite: invalid, busyInviteId: null })).toBe(false);
  expect(canReviewNotificationInvite({ invite: deletedInviterWithoutReason, busyInviteId: null })).toBe(false);
  expect(isInvalidPendingRoomInvite(invalid)).toBe(true);
  expect(isInvalidPendingRoomInvite(deletedInviterWithoutReason)).toBe(true);
  expect(isActionablePendingRoomInvite(pending)).toBe(true);
  expect(isActionablePendingRoomInvite(invalid)).toBe(false);
  expect(isActionablePendingRoomInvite(deletedInviterWithoutReason)).toBe(false);
  expect(roomInviteDecisionLabel(invalid)).toBe('已失效');
  expect(roomInviteDecisionLabel(deletedInviterWithoutReason)).toBe('已失效');
  expect(roomInviteDecisionLabel(accepted)).toBe('已接受');
  expect(roomInviteDecisionLabel(rejected)).toBe('已拒绝');
  expect(roomInviteRoleLabel(_user('owner', { roomRole: 'owner' }))).toBe('创建者');
  expect(roomInviteRoleLabel(_user('admin', { roomRole: 'admin' }))).toBe('管理员');
  expect(roomInviteRoleLabel(_user('member', { roomRole: 'member' }))).toBe('成员');
  expect(roomInviteRoleLabel(_user('left', { roomRole: 'left' }))).toBe('已离开');
  expect(pendingRoomInviteCount([pending, invalid])).toBe(1);
  expect(pendingRoomInviteCount([pending, deletedInviterWithoutReason])).toBe(1);
  expect(roomInviteTimestampLabel(local(2026, 6, 9, 8, 5))).toBe('2026/06/09 08:05');
});

test('deleted notification rooms display as deleted room targets', () => {
  const deleted = _invite('deleted', { roomName: 'Deleted Room', roomExists: false, invalidReason: 'room_missing' });
  const roomExists = deleted.roomExists;

  expect(roomNotificationRoomLabel(deleted.room, { roomExists })).toBe('房间已删除');
  expect(roomNotificationRoomAvatarLabel(deleted.room, { roomExists })).toBe('');
  expect(roomNotificationRoomAvatarUrl(deleted.room, { roomExists })).toBeNull();
  expect(roomNotificationRoomAvatarKey(deleted.room, { roomExists })).toBe('graphite-2');
  expect(roomNotificationRoomCardEnabled({ roomExists })).toBe(true);
  const view = (query: string) =>
    roomInviteNotificationsForView({ invites: [deleted], query, filter: RoomNotificationFilter.all });
  expect(view('房间已删除')).toEqual([deleted]);
  expect(view('不存在')).toEqual([]);
  expect(view('Invite Room')).toEqual([]);
  expect(view('user_inviter_deleted_room')).toEqual([]);
});

test('deleted notification users display as deactivated user targets', () => {
  const deletedInviter = _invite('deleted_inviter', {
    inviter: _user('deleted_inviter', { displayName: 'Deleted Inviter', roomRole: 'left' }),
    inviterExists: false,
    invalidReason: 'inviter_deleted',
  });
  const deletedReviewer = _application('deleted_reviewer', {
    status: 'approved',
    reviewedAt: utc(2026, 6, 7, 8),
    reviewer: _user('deleted_reviewer', { displayName: 'Deleted Reviewer', roomRole: 'owner' }),
    reviewerExists: false,
  });
  const userExists = deletedInviter.inviterExists;

  expect(roomNotificationUserLabel(deletedInviter.inviter, { userExists })).toBe('用户已注销');
  expect(roomNotificationUserAvatarLabel(deletedInviter.inviter, { userExists })).toBe('');
  expect(roomNotificationUserAvatarUrl(deletedInviter.inviter, { userExists })).toBeNull();
  expect(roomNotificationUserAvatarKey(deletedInviter.inviter, { userExists })).toBe('graphite-2');
  const view = (query: string) =>
    roomInviteNotificationsForView({ invites: [deletedInviter], query, filter: RoomNotificationFilter.all });
  expect(view('Deleted Inviter')).toEqual([]);
  expect(view('用户已注销')).toEqual([deletedInviter]);
  expect(
    roomNotificationsForView({
      invites: [],
      applications: [deletedReviewer],
      query: '用户已注销',
      filter: RoomNotificationFilter.all,
    }).map((item) => item.id),
  ).toEqual(['application-reviewed:deleted_reviewer', 'application-requested:deleted_reviewer']);
});

test('room notifications combine invites and applications for view', () => {
  const pendingInvite = _invite('pending_invite', { status: 'pending', createdAt: utc(2026, 6, 5, 8) });
  const pendingApplication = _application('pending_application', { status: 'pending', createdAt: utc(2026, 6, 6, 8) });
  const approvedApplication = _application('approved_application', {
    status: 'approved',
    createdAt: utc(2026, 6, 1, 8),
    updatedAt: utc(2026, 6, 7, 8),
    reviewedAt: utc(2026, 6, 7, 8),
    reviewer: _user('reviewer_1', { displayName: 'Robin Reviewer', roomRole: 'admin' }),
  });

  const visible = roomNotificationsForView({
    invites: [pendingInvite],
    applications: [approvedApplication, pendingApplication],
    query: '',
    filter: RoomNotificationFilter.all,
  });

  expect(visible.map((item) => item.id)).toEqual([
    'application-requested:pending_application',
    'invite:pending_invite',
    'application-reviewed:approved_application',
    'application-requested:approved_application',
  ]);
  expect(
    pendingRoomNotificationCount({
      invites: [pendingInvite],
      applications: [pendingApplication, approvedApplication],
      roomEvents: [_roomEvent('unread_room_event'), _roomEvent('read_room_event', { readAt: utc(2026, 6, 8, 9) })],
    }),
  ).toBe(3);
});

test('room event notifications can be marked read locally', () => {
  const alreadyReadAt = utc(2026, 6, 8, 9);
  const readAt = utc(2026, 6, 8, 10);
  const updated = markUnreadRoomEventNotificationsRead({
    notifications: [_roomEvent('unread_room_event'), _roomEvent('read_room_event', { readAt: alreadyReadAt })],
    readAt,
  });

  expect(updated[0]!.isUnread).toBe(false);
  expect(updated[0]!.readAt).toEqual(readAt);
  expect(updated[1]!.isUnread).toBe(false);
  expect(updated[1]!.readAt).toEqual(alreadyReadAt);
});

test('room event notification visual read can be limited to known ids', () => {
  const readAt = utc(2026, 6, 8, 10);
  const updated = markUnreadRoomEventNotificationsRead({
    notifications: [_roomEvent('old_room_event'), _roomEvent('fresh_room_event')],
    readAt,
    notificationIds: new Set(['old_room_event']),
  });

  expect(updated[0]!.isUnread).toBe(false);
  expect(updated[0]!.readAt).toEqual(readAt);
  expect(updated[1]!.isUnread).toBe(true);
  expect(updated[1]!.readAt).toBeNull();
});

test('room event notifications filter and search room events', () => {
  const promoted = _roomEvent('promoted', {
    type: kRoomEventNotificationRolePromoted,
    roomName: 'Launch Room',
    actor: _user('actor_promoted', { displayName: 'Morgan Admin', roomRole: 'owner' }),
    toRole: 'admin',
  });
  const removed = _roomEvent('removed', { type: kRoomEventNotificationMemberRemoved, createdAt: utc(2026, 6, 8, 8) });
  const mentioned = _roomEvent('mentioned', {
    type: kRoomEventNotificationMentioned,
    roomName: 'Mention Room',
    actor: _user('actor_mentioned', { displayName: 'Casey Admin', roomRole: 'admin' }),
    messageId: 'msg-mentioned',
    messagePreview: '@Morgan Admin hello',
  });

  const roomEventsOnly = roomNotificationsForView({
    invites: [_invite('invite_1')],
    applications: [_application('application_1')],
    roomEvents: [promoted, removed],
    query: '',
    filter: RoomNotificationFilter.roomNotifications,
  });
  expect(roomEventsOnly.map((item) => item.id)).toEqual(['room-event:removed', 'room-event:promoted']);
  expect(roomEventsOnly.every((item) => item.invite == null)).toBe(true);
  expect(roomEventsOnly.every((item) => item.application == null)).toBe(true);
  expect(roomEventsOnly[0]!.newItem).toBe(true);

  const single = (event: RoomEventNotification, query: string) => {
    const items = roomNotificationsForView({
      invites: [],
      applications: [],
      roomEvents: [event],
      query,
      filter: RoomNotificationFilter.all,
    });
    expect(items).toHaveLength(1);
    return items[0]!;
  };
  expect(single(promoted, 'Morgan Admin').roomEvent).toBe(promoted);
  expect(single(promoted, '管理员').roomEvent).toBe(promoted);
  expect(single(mentioned, '提及').roomEvent).toBe(mentioned);
  expect(
    roomNotificationsForView({
      invites: [],
      applications: [],
      roomEvents: [promoted],
      query: 'missing',
      filter: RoomNotificationFilter.all,
    }),
  ).toEqual([]);

  const readEvent = _roomEvent('read', { readAt: utc(2026, 6, 8, 9) });
  expect(single(readEvent, '').newItem).toBe(false);
});

test('room application notifications filter and search reviewer fields', () => {
  const application = _application('application_1', {
    status: 'rejected',
    roomName: 'Launch Room',
    reviewedAt: utc(2026, 6, 7, 8),
    reviewer: _user('reviewer_1', {
      username: 'robin',
      displayName: 'Robin Reviewer',
      roomRole: 'owner',
      roomDisplayName: 'Room Robin',
    }),
  });

  const applicationsOnly = roomNotificationsForView({
    invites: [_invite('invite_1')],
    applications: [application],
    query: '',
    filter: RoomNotificationFilter.applications,
  });
  expect(applicationsOnly.map((item) => item.type)).toEqual([
    RoomNotificationItemType.applicationReviewed,
    RoomNotificationItemType.applicationRequested,
  ]);

  expect(
    roomNotificationsForView({ invites: [], applications: [application], query: 'Room Robin', filter: RoomNotificationFilter.all })
      .length,
  ).toBeGreaterThan(0);
  expect(
    roomNotificationsForView({ invites: [], applications: [application], query: 'missing', filter: RoomNotificationFilter.all }),
  ).toEqual([]);
});

test('room application notification display helpers format labels', () => {
  const pending = _application('pending', { status: 'pending' });
  const approved = _application('approved', { status: 'approved' });
  const rejected = _application('rejected', { status: 'rejected' });
  const withdrawn = _application('withdrawn', { status: 'withdrawn' });

  expect(canWithdrawNotificationApplication({ application: pending, busyApplicationId: null })).toBe(true);
  expect(canWithdrawNotificationApplication({ application: pending, busyApplicationId: 'pending' })).toBe(false);
  expect(canWithdrawNotificationApplication({ application: approved, busyApplicationId: null })).toBe(false);
  expect(roomApplicationStatusLabel(approved)).toBe('已批准');
  expect(roomApplicationStatusLabel(rejected)).toBe('已拒绝');
  expect(roomApplicationStatusLabel(withdrawn)).toBe('已撤回');
  expect(roomApplicationReviewActionLabel(approved)).toBe('批准了您的申请');
  expect(roomApplicationReviewActionLabel(rejected)).toBe('拒绝了您的申请');
});

test('deleted application notification variants stay independently hidden', () => {
  const application = _application('application_deleted_variants', {
    status: 'approved',
    reviewedAt: utc(2026, 6, 8, 9),
    reviewer: _user('reviewer_deleted_variants'),
    requestNotificationDeleted: true,
  });

  expect(
    roomNotificationsForView({
      invites: [],
      applications: [application],
      query: '',
      filter: RoomNotificationFilter.applications,
    }).map((item) => item.type),
  ).toEqual([RoomNotificationItemType.applicationReviewed]);

  const reviewDeleted = _application('application_review_deleted', {
    status: 'approved',
    reviewedAt: utc(2026, 6, 8, 9),
    reviewer: _user('reviewer_review_deleted'),
    reviewNotificationDeleted: true,
  });
  expect(
    roomNotificationsForView({
      invites: [],
      applications: [reviewDeleted],
      query: '',
      filter: RoomNotificationFilter.applications,
    }).map((item) => item.type),
  ).toEqual([RoomNotificationItemType.applicationRequested]);
});

test('notification copy text omits user identity badges', () => {
  const invite = _invite('copy_invite', {
    status: 'accepted',
    roomName: 'Copy Room',
    inviter: _user('copy_inviter', { displayName: 'Alex Inviter', roomRole: 'admin' }),
  });
  const inviteItem = RoomNotificationItem.invite(invite);

  expect(roomNotificationDeletionType(inviteItem)).toBe('invite');
  expect(roomNotificationDeletionId(inviteItem)).toBe(invite.id);
  expect(roomNotificationCopyText(inviteItem)).toBe('Alex Inviter 邀请您加入 Copy Room\n已接受');
  expect(roomNotificationCopyText(inviteItem)).not.toContain('2026/06/05');
  expect(roomNotificationCopyText(inviteItem)).not.toContain('管理员');

  const eventItem = RoomNotificationItem.roomEvent(
    _roomEvent('copy_event', {
      type: kRoomEventNotificationRolePromoted,
      roomName: 'Event Copy Room',
      actor: _user('copy_actor', { displayName: 'Morgan Actor', roomRole: 'owner' }),
      toRole: 'admin',
    }),
  );
  expect(roomNotificationDeletionType(eventItem)).toBe('room_event');
  expect(roomNotificationCopyText(eventItem)).toBe('您在 Event Copy Room 中被 Morgan Actor 晋升为 管理员');
  expect(roomNotificationCopyText(eventItem)).not.toContain('2026/06/05');
  expect(roomNotificationCopyText(eventItem)).not.toContain('创建者');
});

test('room invite application gate follows inviter role', () => {
  expect(
    roomInviteAcceptRequiresApplication(
      _invite('member', { joinPolicy: 'approval_required', inviter: _user('member', { roomRole: 'member' }) }),
    ),
  ).toBe(true);
  expect(
    roomInviteAcceptRequiresApplication(
      _invite('admin', { joinPolicy: 'approval_required', inviter: _user('admin', { roomRole: 'admin' }) }),
    ),
  ).toBe(false);
  const memberInvite = _invite('member-related', {
    roomId: 'room_shared',
    joinPolicy: 'approval_required',
    inviter: _user('member', { roomRole: 'member' }),
  });
  const adminInvite = _invite('admin-related', {
    roomId: 'room_shared',
    joinPolicy: 'approval_required',
    inviter: _user('admin', { roomRole: 'admin' }),
  });
  expect(roomInviteAcceptRequiresApplication(memberInvite, { roomInvites: [memberInvite, adminInvite] })).toBe(false);
  expect(
    roomInviteAcceptRequiresApplication(
      _invite('super', {
        joinPolicy: 'approval_required',
        inviter: _user('super', { roomRole: 'superuser', isSuperuser: true }),
      }),
    ),
  ).toBe(false);
  expect(roomInviteAcceptRequiresApplication(_invite('open', { joinPolicy: 'open' }))).toBe(false);
});

function _publicRoom(opts: { id: string; rid: string; name: string; joinPolicy: string; joinState: string }): PublicRoom {
  return new PublicRoom({
    id: opts.id,
    rid: opts.rid,
    name: opts.name,
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    visibility: 'private',
    joinPolicy: opts.joinPolicy,
    memberCount: 3,
    onlineMemberCount: 1,
    liveParticipantCount: 0,
    joined: false,
    joinState: opts.joinState,
  });
}

function _invite(
  id: string,
  opts: {
    status?: string;
    createdAt?: Date;
    roomName?: string;
    roomRid?: string;
    roomId?: string;
    joinPolicy?: string;
    inviter?: UserSummary;
    roomExists?: boolean;
    inviterExists?: boolean;
    invalidReason?: string | null;
  } = {},
): RoomInvite {
  return new RoomInvite({
    id,
    status: opts.status ?? 'pending',
    room: _publicRoom({
      id: opts.roomId ?? `room_${id}`,
      rid: opts.roomRid ?? 'R-1',
      name: opts.roomName ?? 'Invite Room',
      joinPolicy: opts.joinPolicy ?? 'closed',
      joinState: 'none',
    }),
    inviter: opts.inviter ?? _user(`inviter_${id}`, { roomRole: 'owner' }),
    createdAt: opts.createdAt ?? utc(2026, 6, 5, 12),
    roomExists: opts.roomExists ?? true,
    inviterExists: opts.inviterExists ?? true,
    invalidReason: opts.invalidReason ?? null,
  });
}

function _application(
  id: string,
  opts: {
    status?: string;
    createdAt?: Date;
    updatedAt?: Date;
    reviewedAt?: Date | null;
    roomName?: string;
    roomRid?: string;
    reviewer?: UserSummary | null;
    reviewerExists?: boolean;
    requestNotificationDeleted?: boolean;
    reviewNotificationDeleted?: boolean;
  } = {},
): RoomApplication {
  return new RoomApplication({
    id,
    status: opts.status ?? 'pending',
    room: _publicRoom({
      id: `room_${id}`,
      rid: opts.roomRid ?? 'A-1',
      name: opts.roomName ?? 'Application Room',
      joinPolicy: 'approval_required',
      joinState: 'pending',
    }),
    createdAt: opts.createdAt ?? utc(2026, 6, 5, 12),
    updatedAt: opts.updatedAt ?? utc(2026, 6, 5, 12),
    reviewedAt: opts.reviewedAt ?? null,
    reviewer: opts.reviewer ?? null,
    reviewerExists: opts.reviewerExists ?? true,
    requestNotificationDeleted: opts.requestNotificationDeleted ?? false,
    reviewNotificationDeleted: opts.reviewNotificationDeleted ?? false,
  });
}

function _roomEvent(
  id: string,
  opts: {
    type?: string;
    createdAt?: Date;
    roomName?: string;
    roomRid?: string;
    actor?: UserSummary;
    actorExists?: boolean;
    roomExists?: boolean;
    fromRole?: string;
    toRole?: string;
    messageId?: string | null;
    messagePreview?: string | null;
    readAt?: Date | null;
  } = {},
): RoomEventNotification {
  return new RoomEventNotification({
    id,
    type: opts.type ?? kRoomEventNotificationRoleDemoted,
    room: _publicRoom({
      id: `room_event_${id}`,
      rid: opts.roomRid ?? 'E-1',
      name: opts.roomName ?? 'Event Room',
      joinPolicy: 'approval_required',
      joinState: 'none',
    }),
    actor: opts.actor ?? _user(`actor_${id}`, { roomRole: 'owner' }),
    createdAt: opts.createdAt ?? utc(2026, 6, 5, 12),
    actorExists: opts.actorExists ?? true,
    roomExists: opts.roomExists ?? true,
    fromRole: opts.fromRole ?? 'owner',
    toRole: opts.toRole ?? 'admin',
    messageId: opts.messageId ?? null,
    messagePreview: opts.messagePreview ?? null,
    readAt: opts.readAt ?? null,
  });
}

function _user(
  id: string,
  opts: {
    uid?: string | null;
    username?: string;
    displayName?: string;
    roomRole?: string | null;
    roomDisplayName?: string | null;
    isSuperuser?: boolean;
    commonRooms?: UserCommonRoom[];
  } = {},
): UserSummary {
  return new UserSummary({
    id,
    uid: opts.uid ?? null,
    username: opts.username ?? `user_${id}`,
    displayName: opts.displayName ?? `User ${id}`,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
    roomRole: opts.roomRole ?? null,
    roomDisplayName: opts.roomDisplayName ?? null,
    isSuperuser: opts.isSuperuser ?? false,
    commonRooms: opts.commonRooms ?? [],
  });
}
