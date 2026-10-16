import type {
  PublicRoom,
  RoomApplication,
  RoomEventNotification,
  RoomInvite,
  UserSummary,
} from '../protocol/models';
import * as room_display from './room_display';

export enum RoomNotificationFilter {
  all = 'all',
  invites = 'invites',
  applications = 'applications',
  roomNotifications = 'roomNotifications',
}

export class RoomNotificationDateRange {
  /**
   * Port of the Dart factory constructor: both ends are normalized to their
   * local calendar day, and [endDate] must not fall before [startDate].
   */
  constructor(opts: { startDate: Date; endDate: Date }) {
    const normalizedStart = _localDateOnly(opts.startDate);
    const normalizedEnd = _localDateOnly(opts.endDate);
    if (normalizedEnd.getTime() < normalizedStart.getTime()) {
      throw new RangeError(`Invalid argument (endDate): must not be before startDate: ${opts.endDate.toString()}`);
    }
    this.startDate = normalizedStart;
    this.endDate = normalizedEnd;
  }

  static defaultFor(opts: { accountCreatedAt: Date | null; today: Date }): RoomNotificationDateRange {
    const normalizedToday = _localDateOnly(opts.today);
    const accountDate = opts.accountCreatedAt == null ? _localDate(1970, 0, 1) : _localDateOnly(opts.accountCreatedAt);
    return new RoomNotificationDateRange({
      startDate: accountDate.getTime() > normalizedToday.getTime() ? normalizedToday : accountDate,
      endDate: normalizedToday,
    });
  }

  readonly startDate: Date;
  readonly endDate: Date;

  includes(value: Date): boolean {
    const date = _localDateOnly(value).getTime();
    return !(date < this.startDate.getTime()) && !(date > this.endDate.getTime());
  }

  /** Port of `operator ==`. */
  equals(other: unknown): boolean {
    return (
      other instanceof RoomNotificationDateRange &&
      other.startDate.getTime() === this.startDate.getTime() &&
      other.endDate.getTime() === this.endDate.getTime()
    );
  }
}

export enum RoomNotificationItemType {
  invite = 'invite',
  applicationRequested = 'applicationRequested',
  applicationReviewed = 'applicationReviewed',
  roomEvent = 'roomEvent',
}

export const roomNotificationDeletionInvite = 'invite';
export const roomNotificationDeletionApplicationRequested = 'application_requested';
export const roomNotificationDeletionApplicationReviewed = 'application_reviewed';
export const roomNotificationDeletionRoomEvent = 'room_event';

export const kRoomEventNotificationMemberRemoved = 'member_removed';
export const kRoomEventNotificationRolePromoted = 'role_promoted';
export const kRoomEventNotificationRoleDemoted = 'role_demoted';
export const kRoomEventNotificationCreatorTransferDemoted = 'creator_transfer_demoted';
export const kRoomEventNotificationMentioned = 'mentioned';

export const missingRoomNotificationRoomLabel = '房间已删除';
export const missingRoomNotificationRoomAvatarLabel = '';
export const missingRoomNotificationRoomAvatarKey = 'graphite-2';
export const missingRoomNotificationUserLabel = '用户已注销';
export const missingRoomNotificationUserAvatarLabel = '';
export const missingRoomNotificationUserAvatarKey = 'graphite-2';

export class RoomNotificationItem {
  private constructor(opts: {
    type: RoomNotificationItemType;
    id: string;
    time: Date;
    pending: boolean;
    newItem: boolean;
    invite?: RoomInvite | null;
    application?: RoomApplication | null;
    roomEvent?: RoomEventNotification | null;
  }) {
    this.type = opts.type;
    this.id = opts.id;
    this.time = opts.time;
    this.pending = opts.pending;
    this.newItem = opts.newItem;
    this.invite = opts.invite ?? null;
    this.application = opts.application ?? null;
    this.roomEvent = opts.roomEvent ?? null;
  }

  static invite(invite: RoomInvite): RoomNotificationItem {
    return new RoomNotificationItem({
      type: RoomNotificationItemType.invite,
      id: `invite:${invite.id}`,
      time: invite.createdAt,
      pending: isActionablePendingRoomInvite(invite),
      newItem: isActionablePendingRoomInvite(invite),
      invite,
    });
  }

  static applicationRequested(application: RoomApplication): RoomNotificationItem {
    return new RoomNotificationItem({
      type: RoomNotificationItemType.applicationRequested,
      id: `application-requested:${application.id}`,
      time: application.createdAt,
      pending: isPendingRoomApplication(application),
      newItem: isPendingRoomApplication(application),
      application,
    });
  }

  static applicationReviewed(application: RoomApplication): RoomNotificationItem {
    return new RoomNotificationItem({
      type: RoomNotificationItemType.applicationReviewed,
      id: `application-reviewed:${application.id}`,
      time: application.reviewedAt ?? application.updatedAt,
      pending: false,
      newItem: false,
      application,
    });
  }

  static roomEvent(notification: RoomEventNotification): RoomNotificationItem {
    return new RoomNotificationItem({
      type: RoomNotificationItemType.roomEvent,
      id: `room-event:${notification.id}`,
      time: notification.createdAt,
      pending: false,
      newItem: notification.isUnread,
      roomEvent: notification,
    });
  }

  readonly type: RoomNotificationItemType;
  readonly id: string;
  readonly time: Date;
  readonly pending: boolean;
  readonly newItem: boolean;
  readonly invite: RoomInvite | null;
  readonly application: RoomApplication | null;
  readonly roomEvent: RoomEventNotification | null;
}

export function roomNotificationDeletionType(item: RoomNotificationItem): string {
  switch (item.type) {
    case RoomNotificationItemType.invite:
      return roomNotificationDeletionInvite;
    case RoomNotificationItemType.applicationRequested:
      return roomNotificationDeletionApplicationRequested;
    case RoomNotificationItemType.applicationReviewed:
      return roomNotificationDeletionApplicationReviewed;
    case RoomNotificationItemType.roomEvent:
      return roomNotificationDeletionRoomEvent;
  }
}

export function roomNotificationDeletionId(item: RoomNotificationItem): string {
  switch (item.type) {
    case RoomNotificationItemType.invite:
      return item.invite!.id;
    case RoomNotificationItemType.applicationRequested:
    case RoomNotificationItemType.applicationReviewed:
      return item.application!.id;
    case RoomNotificationItemType.roomEvent:
      return item.roomEvent!.id;
  }
}

export function isPendingRoomInvite(invite: RoomInvite): boolean {
  return invite.status.toLowerCase() === 'pending';
}

export function isAcceptedRoomInvite(invite: RoomInvite): boolean {
  return invite.status.toLowerCase() === 'accepted';
}

export function isRejectedRoomInvite(invite: RoomInvite): boolean {
  return invite.status.toLowerCase() === 'rejected';
}

export function isInvalidPendingRoomInvite(invite: RoomInvite): boolean {
  if (!isPendingRoomInvite(invite)) return false;
  const reason = invite.invalidReason?.trim();
  if (reason != null && reason.length > 0) return true;
  return !invite.roomExists || !invite.inviterExists || _isLeftRoomRole(invite.inviter.roomRole);
}

export function isActionablePendingRoomInvite(invite: RoomInvite): boolean {
  return isPendingRoomInvite(invite) && !isInvalidPendingRoomInvite(invite);
}

export function isPendingRoomApplication(application: RoomApplication): boolean {
  return application.status.toLowerCase() === 'pending';
}

export function isApprovedRoomApplication(application: RoomApplication): boolean {
  return application.status.toLowerCase() === 'approved';
}

export function isRejectedRoomApplication(application: RoomApplication): boolean {
  return application.status.toLowerCase() === 'rejected';
}

export function isWithdrawnRoomApplication(application: RoomApplication): boolean {
  return application.status.toLowerCase() === 'withdrawn';
}

export function isReviewedRoomApplication(application: RoomApplication): boolean {
  return isApprovedRoomApplication(application) || isRejectedRoomApplication(application);
}

export function canReviewNotificationInvite(opts: { invite: RoomInvite; busyInviteId: string | null }): boolean {
  const { invite } = opts;
  return isPendingRoomInvite(invite) && !isInvalidPendingRoomInvite(invite) && opts.busyInviteId == null;
}

export function canWithdrawNotificationApplication(opts: {
  application: RoomApplication;
  busyApplicationId: string | null;
}): boolean {
  return isPendingRoomApplication(opts.application) && opts.busyApplicationId == null;
}

export function roomInviteAcceptRequiresApplication(
  invite: RoomInvite,
  opts: { roomInvites?: Iterable<RoomInvite> } = {},
): boolean {
  if (invite.room.joinPolicy !== 'approval_required') return false;
  const relatedInvites = [
    invite,
    ...[...(opts.roomInvites ?? [])].filter(
      (other) =>
        other.id !== invite.id &&
        other.room.id === invite.room.id &&
        isPendingRoomInvite(other) &&
        !isInvalidPendingRoomInvite(other),
    ),
  ];
  return !relatedInvites.some((item) => _isPrivilegedInviter(item.inviter));
}

function _isPrivilegedInviter(inviter: UserSummary): boolean {
  if (inviter.isSuperuser) return true;
  const role = inviter.roomRole?.trim().toLowerCase();
  switch (role) {
    case 'owner':
    case 'creator':
    case 'admin':
    case 'administrator':
    case 'superuser':
      return true;
    default:
      return false;
  }
}

export function pendingRoomInviteCount(invites: Iterable<RoomInvite>): number {
  return [...invites].filter(isActionablePendingRoomInvite).length;
}

export function pendingRoomNotificationCount(opts: {
  invites: Iterable<RoomInvite>;
  applications: Iterable<RoomApplication>;
  roomEvents?: Iterable<RoomEventNotification>;
}): number {
  return (
    pendingRoomInviteCount(opts.invites) +
    [...opts.applications].filter(isPendingRoomApplication).length +
    unreadRoomEventNotificationCount(opts.roomEvents ?? [])
  );
}

export function unreadRoomEventNotificationCount(notifications: Iterable<RoomEventNotification>): number {
  return [...notifications].filter((notification) => notification.isUnread).length;
}

export function markUnreadRoomEventNotificationsRead(opts: {
  notifications: Iterable<RoomEventNotification>;
  readAt: Date;
  notificationIds?: Iterable<string> | null;
}): RoomEventNotification[] {
  const ids = opts.notificationIds == null ? null : new Set(opts.notificationIds);
  return [...opts.notifications].map((notification) =>
    notification.isUnread && (ids == null || ids.has(notification.id)) ? notification.markRead(opts.readAt) : notification,
  );
}

export function roomInviteDecisionLabel(invite: RoomInvite): string {
  if (isInvalidPendingRoomInvite(invite)) return '已失效';
  if (isAcceptedRoomInvite(invite)) return '已接受';
  if (isRejectedRoomInvite(invite)) return '已拒绝';
  return '';
}

export function roomApplicationStatusLabel(application: RoomApplication): string {
  if (isApprovedRoomApplication(application)) return '已批准';
  if (isRejectedRoomApplication(application)) return '已拒绝';
  if (isWithdrawnRoomApplication(application)) return '已撤回';
  return '';
}

export function roomApplicationReviewActionLabel(application: RoomApplication): string {
  if (isApprovedRoomApplication(application)) return '批准了您的申请';
  if (isRejectedRoomApplication(application)) return '拒绝了您的申请';
  return '';
}

export function roomInviteRoleLabel(inviter: UserSummary): string {
  const role = inviter.roomRole?.trim().toLowerCase() ?? null;
  switch (role) {
    case 'owner':
    case 'creator':
      return '创建者';
    case 'admin':
    case 'administrator':
      return '管理员';
    case 'member':
      return '成员';
    case 'superuser':
      return '超级用户';
    case 'left':
    case 'left_room':
    case 'departed':
      return '已离开';
    case null:
    case '':
      return '成员';
    default:
      return inviter.roomRole!.trim();
  }
}

export function roomNotificationRoleLabel(role: string | null): string {
  switch (role?.trim().toLowerCase() ?? null) {
    case 'owner':
    case 'creator':
      return '创建者';
    case 'admin':
    case 'administrator':
      return '管理员';
    case 'member':
      return '成员';
    case 'superuser':
      return '超级用户';
    case 'left':
    case 'left_room':
    case 'departed':
      return '已离开';
    case null:
    case '':
      return '成员';
    default:
      return role!.trim();
  }
}

export function roomEventNotificationRoleActionLabel(notification: RoomEventNotification): string {
  switch (notification.type) {
    case kRoomEventNotificationRolePromoted:
      return '晋升为';
    default:
      return '降职为';
  }
}

export function roomInviteTimestampLabel(value: Date): string {
  // JS `Date` getters already read the local wall clock (Dart `toLocal()`).
  const local = value;
  return (
    `${String(local.getFullYear()).padStart(4, '0')}/` +
    `${String(local.getMonth() + 1).padStart(2, '0')}/` +
    `${String(local.getDate()).padStart(2, '0')} ` +
    `${String(local.getHours()).padStart(2, '0')}:` +
    `${String(local.getMinutes()).padStart(2, '0')}`
  );
}

export function roomNotificationCopyText(item: RoomNotificationItem): string {
  switch (item.type) {
    case RoomNotificationItemType.invite: {
      const invite = item.invite!;
      return _notificationCopyLines([
        _notificationCopySentence([
          roomNotificationUserLabel(invite.inviter, { userExists: invite.inviterExists }),
          '邀请您加入',
          roomNotificationRoomLabel(invite.room, { roomExists: invite.roomExists }),
        ]),
        roomInviteDecisionLabel(invite),
      ]);
    }
    case RoomNotificationItemType.applicationRequested: {
      const application = item.application!;
      return _notificationCopyLines([
        _notificationCopySentence(['您已申请加入', roomNotificationRoomLabel(application.room, { roomExists: true })]),
        roomApplicationStatusLabel(application),
      ]);
    }
    case RoomNotificationItemType.applicationReviewed: {
      const application = item.application!;
      const reviewer = application.reviewer;
      const reviewerName =
        reviewer == null ? '' : roomNotificationUserLabel(reviewer, { userExists: application.reviewerExists });
      return _notificationCopyLines([
        _notificationCopySentence([
          reviewerName,
          roomApplicationReviewActionLabel(application),
          roomNotificationRoomLabel(application.room, { roomExists: true }),
        ]),
      ]);
    }
    case RoomNotificationItemType.roomEvent: {
      const notification = item.roomEvent!;
      const room = roomNotificationRoomLabel(notification.room, { roomExists: notification.roomExists });
      const actor = notification.actor;
      const actorName =
        actor == null ? '' : roomNotificationUserLabel(actor, { userExists: notification.actorExists });
      let body: string;
      switch (notification.type) {
        case kRoomEventNotificationMemberRemoved:
          body = _notificationCopySentence(['您被', actorName, '踢出了', room]);
          break;
        case kRoomEventNotificationRolePromoted:
        case kRoomEventNotificationRoleDemoted:
          body = _notificationCopySentence([
            '您在',
            room,
            '中被',
            actorName,
            roomEventNotificationRoleActionLabel(notification),
            roomNotificationRoleLabel(notification.toRole),
          ]);
          break;
        case kRoomEventNotificationCreatorTransferDemoted:
          body = _notificationCopySentence(['您在', room, '中降职为', roomNotificationRoleLabel(notification.toRole)]);
          break;
        case kRoomEventNotificationMentioned:
          body = _notificationCopySentence(['您在', room, '中被', actorName, '提及']);
          break;
        default:
          body = _notificationCopySentence(['您在', room, '收到了房间通知']);
          break;
      }
      return _notificationCopyLines([body]);
    }
  }
}

function _notificationCopyLines(lines: Iterable<string>): string {
  return [...lines]
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .join('\n');
}

function _notificationCopySentence(parts: Iterable<string>): string {
  return [...parts]
    .map((part) => part.trim())
    .filter((part) => part.length > 0)
    .join(' ');
}

export function roomNotificationDateLabel(value: Date): string {
  const local = value;
  return (
    `${String(local.getFullYear()).padStart(4, '0')}/` +
    `${String(local.getMonth() + 1).padStart(2, '0')}/` +
    `${String(local.getDate()).padStart(2, '0')}`
  );
}

export function roomNotificationRoomLabel(room: PublicRoom, opts: { roomExists: boolean }): string {
  return opts.roomExists ? room.name : missingRoomNotificationRoomLabel;
}

export function roomNotificationRoomAvatarLabel(room: PublicRoom, opts: { roomExists: boolean }): string {
  return opts.roomExists ? room.name : missingRoomNotificationRoomAvatarLabel;
}

export function roomNotificationRoomAvatarUrl(room: PublicRoom, opts: { roomExists: boolean }): string | null {
  return opts.roomExists ? room.avatarUrl : null;
}

export function roomNotificationRoomAvatarKey(room: PublicRoom, opts: { roomExists: boolean }): string {
  return opts.roomExists ? room.defaultAvatarKey : missingRoomNotificationRoomAvatarKey;
}

export function roomNotificationRoomCardEnabled(opts: { roomExists: boolean }): boolean {
  return true;
}

export function roomNotificationUserLabel(user: UserSummary, opts: { userExists: boolean }): string {
  if (!opts.userExists) return missingRoomNotificationUserLabel;
  const roomName = user.roomDisplayName?.trim();
  if (roomName != null && roomName.length > 0) return roomName;
  const displayName = user.displayName.trim();
  if (displayName.length > 0) return displayName;
  return user.username;
}

export function roomNotificationUserAvatarLabel(user: UserSummary, opts: { userExists: boolean }): string {
  return opts.userExists ? room_display.userAvatarLabel(user) : missingRoomNotificationUserAvatarLabel;
}

export function roomNotificationUserAvatarUrl(user: UserSummary, opts: { userExists: boolean }): string | null {
  return opts.userExists ? user.avatarUrl : null;
}

export function roomNotificationUserAvatarKey(user: UserSummary, opts: { userExists: boolean }): string {
  return opts.userExists ? user.defaultAvatarKey : missingRoomNotificationUserAvatarKey;
}

export function roomNotificationsForView(opts: {
  invites: Iterable<RoomInvite>;
  applications: Iterable<RoomApplication>;
  roomEvents?: Iterable<RoomEventNotification>;
  query: string;
  filter: RoomNotificationFilter;
  dateRange?: RoomNotificationDateRange | null;
}): RoomNotificationItem[] {
  const { filter } = opts;
  const dateRange = opts.dateRange ?? null;
  const normalizedQuery = opts.query.trim().toLowerCase();
  const items: RoomNotificationItem[] = [];

  if (filter === RoomNotificationFilter.all || filter === RoomNotificationFilter.invites) {
    for (const invite of opts.invites) {
      const item = RoomNotificationItem.invite(invite);
      if (_matchesRoomInvite(invite, normalizedQuery) && _isInRoomNotificationDateRange(item, dateRange)) {
        items.push(item);
      }
    }
  }

  if (filter === RoomNotificationFilter.all || filter === RoomNotificationFilter.applications) {
    for (const application of opts.applications) {
      if (!_matchesRoomApplication(application, normalizedQuery)) {
        continue;
      }
      const requested = RoomNotificationItem.applicationRequested(application);
      if (!application.requestNotificationDeleted && _isInRoomNotificationDateRange(requested, dateRange)) {
        items.push(requested);
      }
      if (
        isReviewedRoomApplication(application) &&
        application.reviewedAt != null &&
        application.reviewer != null &&
        !application.reviewNotificationDeleted
      ) {
        const reviewed = RoomNotificationItem.applicationReviewed(application);
        if (_isInRoomNotificationDateRange(reviewed, dateRange)) {
          items.push(reviewed);
        }
      }
    }
  }

  if (filter === RoomNotificationFilter.all || filter === RoomNotificationFilter.roomNotifications) {
    for (const notification of opts.roomEvents ?? []) {
      const item = RoomNotificationItem.roomEvent(notification);
      if (
        _matchesRoomEventNotification(notification, normalizedQuery) &&
        _isInRoomNotificationDateRange(item, dateRange)
      ) {
        items.push(item);
      }
    }
  }

  items.sort(compareRoomNotificationItems);
  return items;
}

export function roomInviteNotificationsForView(opts: {
  invites: Iterable<RoomInvite>;
  query: string;
  filter: RoomNotificationFilter;
  dateRange?: RoomNotificationDateRange | null;
}): RoomInvite[] {
  const { filter } = opts;
  const dateRange = opts.dateRange ?? null;
  if (filter !== RoomNotificationFilter.all && filter !== RoomNotificationFilter.invites) {
    return [];
  }

  const normalizedQuery = opts.query.trim().toLowerCase();
  const filtered = [...opts.invites].filter(
    (invite) => _matchesRoomInvite(invite, normalizedQuery) && (dateRange?.includes(invite.createdAt) ?? true),
  );
  filtered.sort(compareRoomInviteNotifications);
  return filtered;
}

export function compareRoomNotificationItems(a: RoomNotificationItem, b: RoomNotificationItem): number {
  const pendingRank = _itemPendingRank(a) - _itemPendingRank(b);
  if (pendingRank !== 0) return pendingRank;
  const timeRank = _compareNumbers(b.time.getTime(), a.time.getTime());
  if (timeRank !== 0) return timeRank;
  return _compareStrings(b.id, a.id);
}

export function compareRoomInviteNotifications(a: RoomInvite, b: RoomInvite): number {
  const pendingRank = _invitePendingRank(a) - _invitePendingRank(b);
  if (pendingRank !== 0) return pendingRank;
  const createdRank = _compareNumbers(b.createdAt.getTime(), a.createdAt.getTime());
  if (createdRank !== 0) return createdRank;
  return _compareStrings(b.id, a.id);
}

function _matchesRoomInvite(invite: RoomInvite, normalizedQuery: string): boolean {
  if (normalizedQuery.length === 0) return true;
  return _roomInviteSearchText(invite).includes(normalizedQuery);
}

function _matchesRoomApplication(application: RoomApplication, normalizedQuery: string): boolean {
  if (normalizedQuery.length === 0) return true;
  return _roomApplicationSearchText(application).includes(normalizedQuery);
}

function _matchesRoomEventNotification(notification: RoomEventNotification, normalizedQuery: string): boolean {
  if (normalizedQuery.length === 0) return true;
  return _roomEventNotificationSearchText(notification).includes(normalizedQuery);
}

function _isInRoomNotificationDateRange(
  item: RoomNotificationItem,
  dateRange: RoomNotificationDateRange | null,
): boolean {
  return dateRange?.includes(item.time) ?? true;
}

function _itemPendingRank(item: RoomNotificationItem): number {
  return item.pending ? 0 : 1;
}

function _invitePendingRank(invite: RoomInvite): number {
  return isActionablePendingRoomInvite(invite) ? 0 : 1;
}

function _roomInviteSearchText(invite: RoomInvite): string {
  const values: string[] = [];
  if (invite.roomExists) {
    _addRoomSearchValues(values, invite.room);
  }
  if (invite.inviterExists) {
    _addUserSearchValues(values, invite.inviter);
  }
  _addSearchValue(values, invite.id);
  _addSearchValue(values, invite.status);
  if (invite.roomExists) {
    _addSearchValue(values, invite.room.joined ? '已加入 joined' : '未加入 not joined');
  }
  if (invite.inviterExists) {
    _addSearchValue(values, roomInviteRoleLabel(invite.inviter));
  }
  _addSearchValue(
    values,
    invite.inviterExists ? null : `${missingRoomNotificationUserLabel} 已注销 user deactivated deleted`,
  );
  _addSearchValue(values, invite.invalidReason);
  _addSearchValue(values, isInvalidPendingRoomInvite(invite) ? '已失效 invalid' : null);
  _addSearchValue(values, invite.roomExists ? null : `${missingRoomNotificationRoomLabel} 已删除 room deleted`);
  _addSearchValue(values, roomInviteDecisionLabel(invite));
  return values.join('\n');
}

function _isLeftRoomRole(value: string | null): boolean {
  const role = value?.trim().toLowerCase();
  return role === 'left' || role === 'left_room' || role === 'departed';
}

function _roomApplicationSearchText(application: RoomApplication): string {
  const values: string[] = [];
  _addRoomSearchValues(values, application.room);
  _addSearchValue(values, application.id);
  _addSearchValue(values, application.status);
  _addSearchValue(values, application.reason);
  _addSearchValue(values, roomApplicationStatusLabel(application));
  _addSearchValue(values, roomApplicationReviewActionLabel(application));
  const reviewer = application.reviewer;
  if (reviewer != null) {
    if (application.reviewerExists) {
      _addUserSearchValues(values, reviewer);
      _addSearchValue(values, roomInviteRoleLabel(reviewer));
    }
    _addSearchValue(
      values,
      application.reviewerExists ? null : `${missingRoomNotificationUserLabel} 已注销 user deactivated deleted`,
    );
  }
  return values.join('\n');
}

function _roomEventNotificationSearchText(notification: RoomEventNotification): string {
  const values: string[] = [];
  if (notification.roomExists) {
    _addRoomSearchValues(values, notification.room);
  }
  _addSearchValue(values, notification.id);
  _addSearchValue(values, notification.type);
  _addSearchValue(values, roomNotificationRoleLabel(notification.fromRole));
  _addSearchValue(values, roomNotificationRoleLabel(notification.toRole));
  _addSearchValue(values, notification.roomExists ? null : `${missingRoomNotificationRoomLabel} 已删除 room deleted`);
  const actor = notification.actor;
  if (actor != null) {
    if (notification.actorExists) {
      _addUserSearchValues(values, actor);
      _addSearchValue(values, roomInviteRoleLabel(actor));
    }
    _addSearchValue(
      values,
      notification.actorExists ? null : `${missingRoomNotificationUserLabel} 已注销 user deactivated deleted`,
    );
  }
  switch (notification.type) {
    case kRoomEventNotificationMemberRemoved:
      _addSearchValue(values, '踢出了 踢出房间 removed kicked');
      break;
    case kRoomEventNotificationRolePromoted:
      _addSearchValue(values, '晋升为 promotion promoted');
      break;
    case kRoomEventNotificationRoleDemoted:
      _addSearchValue(values, '降职为 demotion demoted');
      break;
    case kRoomEventNotificationCreatorTransferDemoted:
      _addSearchValue(values, '转让创建者 降职为 creator transferred');
      break;
    case kRoomEventNotificationMentioned:
      _addSearchValue(values, '提及 @ mentioned');
      break;
  }
  return values.join('\n');
}

function _addRoomSearchValues(values: string[], room: PublicRoom): void {
  _addSearchValue(values, room.id);
  _addSearchValue(values, room.rid);
  _addSearchValue(values, room.name);
  _addSearchValue(values, room.visibility);
  _addSearchValue(values, room.joinPolicy);
  _addSearchValue(values, room.memberCount);
  _addSearchValue(values, room.onlineMemberCount);
  _addSearchValue(values, room.liveParticipantCount);
  _addSearchValue(values, room.joinState);
}

function _addUserSearchValues(values: string[], user: UserSummary): void {
  _addSearchValue(values, user.id);
  _addSearchValue(values, user.uid);
  _addSearchValue(values, user.username);
  _addSearchValue(values, user.displayName);
  _addSearchValue(values, user.defaultAvatarKey);
  _addSearchValue(values, user.bio);
  _addSearchValue(values, user.gender);
  _addSearchValue(values, user.email);
  _addSearchValue(values, user.phoneNumber);
  _addSearchValue(values, user.roomDisplayName);
  _addSearchValue(values, user.roomRole);
  _addSearchValue(values, user.isSuperuser ? '超级用户 superuser' : null);
  _addSearchValue(values, user.isOnline == null ? null : user.isOnline ? '在线 online' : '离线 offline');
  for (const commonRoom of user.commonRooms) {
    _addSearchValue(values, commonRoom.id);
    _addSearchValue(values, commonRoom.rid);
    _addSearchValue(values, commonRoom.name);
    _addSearchValue(values, commonRoom.visibility);
    _addSearchValue(values, commonRoom.remarkName);
    _addSearchValue(values, commonRoom.defaultAvatarKey);
    _addSearchValue(values, commonRoom.roomDisplayName);
    _addSearchValue(values, commonRoom.roomRole);
  }
}

function _addSearchValue(values: string[], value: string | number | null | undefined): void {
  const text = value == null ? null : String(value).trim();
  if (text != null && text.length > 0) values.push(text.toLowerCase());
}

/** Local-time `DateTime(year, month, day)`; [monthIndex] is 0-based like JS. */
function _localDate(year: number, monthIndex: number, day: number): Date {
  const date = new Date(2000, monthIndex, day);
  date.setFullYear(year, monthIndex, day);
  return date;
}

function _localDateOnly(value: Date): Date {
  // JS `Date` getters already read the local wall clock (Dart `toLocal()`).
  return _localDate(value.getFullYear(), value.getMonth(), value.getDate());
}

function _compareNumbers(a: number, b: number): number {
  return a === b ? 0 : a < b ? -1 : 1;
}

/** Dart `String.compareTo`: UTF-16 code unit order. */
function _compareStrings(a: string, b: string): number {
  if (a === b) return 0;
  return a < b ? -1 : 1;
}
