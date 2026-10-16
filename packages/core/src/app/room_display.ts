import {
  PublicRoom,
  UserCommonRoom,
  type CurrentUser,
  type LastMessagePreview,
  type RoomCard,
  type RoomDetail,
  type RoomMemberProfile,
  type UserSummary,
} from '../protocol/models';
import { LivePresenceAnnouncement, type LivePresenceAnnouncementAction } from './live_presence_announcement';

export class RoomManagementPermissionState {
  constructor(opts: { canEditCreatorOnly: boolean; canDeleteRoom: boolean }) {
    this.canEditCreatorOnly = opts.canEditCreatorOnly;
    this.canDeleteRoom = opts.canDeleteRoom;
  }

  readonly canEditCreatorOnly: boolean;
  readonly canDeleteRoom: boolean;
}

export class RoomAccessState {
  constructor(opts: { canManageRoom: boolean; canReviewJoinRequests: boolean }) {
    this.canManageRoom = opts.canManageRoom;
    this.canReviewJoinRequests = opts.canReviewJoinRequests;
  }

  readonly canManageRoom: boolean;
  readonly canReviewJoinRequests: boolean;

  showJoinRequestBadge(hasPendingRequests: boolean): boolean {
    return this.canReviewJoinRequests && hasPendingRequests;
  }
}

export class RoomLeaveConfirmationSpec {
  constructor(opts: {
    confirmDeleteIfEmpty: boolean;
    title: string;
    body: string;
    confirmLabel: string;
    expectedText?: string | null;
  }) {
    this.confirmDeleteIfEmpty = opts.confirmDeleteIfEmpty;
    this.title = opts.title;
    this.body = opts.body;
    this.confirmLabel = opts.confirmLabel;
    this.expectedText = opts.expectedText ?? null;
  }

  readonly confirmDeleteIfEmpty: boolean;
  readonly title: string;
  readonly body: string;
  readonly confirmLabel: string;
  readonly expectedText: string | null;

  get requiresStrongConfirmation(): boolean {
    return this.expectedText != null;
  }
}

export class RoomDeletionConfirmationSpec {
  constructor(opts: { title: string; body: string; confirmLabel: string; expectedText: string }) {
    this.title = opts.title;
    this.body = opts.body;
    this.confirmLabel = opts.confirmLabel;
    this.expectedText = opts.expectedText;
  }

  readonly title: string;
  readonly body: string;
  readonly confirmLabel: string;
  readonly expectedText: string;
}

export function roomSubtitle(room: RoomCard): string {
  const live = room.liveParticipantCount;
  const online = room.onlineMemberCount;
  const last = room.lastMessage;
  if (last != null) {
    const sender = _nonEmpty(last.senderDisplayName) ?? '用户';
    const body = _nonEmpty(last.bodyPreview) ?? '消息';
    return `${sender} · ${body}`;
  }
  return `${room.memberCount} 名成员 · ${online} 人在线 · ${live} 人语音`;
}

export function roomSidebarSubtitle(room: RoomCard): string {
  const last = room.lastMessage;
  if (last != null) {
    if (last.type === 'system') {
      return `[系统] ${_systemLastMessagePreview(last)}`;
    }
    const sender = _nonEmpty(last.senderDisplayName) ?? '用户';
    const body = _nonEmpty(last.bodyPreview) ?? '消息';
    return `${sender} · ${body}`;
  }

  const parts = [`${room.memberCount} 名成员`];
  if (room.liveParticipantCount > 0) {
    parts.push(`${room.liveParticipantCount} 人语音`);
  }
  return parts.join(' · ');
}

export function roomDraftPreview(draft: string | null | undefined): string | null {
  const normalized = draft == null ? null : _collapseWhitespace(draft);
  if (normalized == null || normalized.length === 0) return null;
  return normalized;
}

export function roomAttachmentDraftPreview(opts: { filename: string; mimeType: string | null }): string {
  const normalized = _collapseWhitespace(opts.filename);
  const label = (opts.mimeType ?? '').toLowerCase().startsWith('image/') ? '[图片]' : '[文件]';
  if (normalized.length === 0) return label;
  return `${label} ${normalized}`;
}

export function roomDraftPreviewText(opts: {
  text: string | null;
  attachmentFilename: string | null;
  attachmentMimeType: string | null;
  hasQuote?: boolean;
}): string | null {
  const { text, attachmentFilename, attachmentMimeType, hasQuote = false } = opts;
  let preview: string | null;
  if (attachmentFilename != null) {
    preview = roomAttachmentDraftPreview({
      filename: attachmentFilename,
      mimeType: attachmentMimeType,
    });
  } else {
    preview = roomDraftPreview(text);
  }
  if (!hasQuote) return preview;
  return preview == null ? '[引用]' : `[引用] ${preview}`;
}

function _systemLastMessagePreview(last: LastMessagePreview): string {
  const sender = _nonEmpty(last.senderDisplayName);
  const body = _nonEmpty(last.bodyPreview);
  const roomProfilePreview = _roomProfileSystemLastMessagePreview({ sender, body });
  if (roomProfilePreview != null) return roomProfilePreview;
  const parts = [sender, body].filter((part): part is string => part != null);
  if (parts.length === 0) return '系统消息';
  return parts.join(' ');
}

function _roomProfileSystemLastMessagePreview(opts: { sender: string | null; body: string | null }): string | null {
  const { sender, body } = opts;
  if (body == null) return null;
  if (
    body.startsWith('房间名称 被 ') ||
    body.startsWith('房间简介 被 ') ||
    body.startsWith('房间可见性 被 ') ||
    body.startsWith('房间加入方式 被 ')
  ) {
    return body;
  }

  const namePrefix = '房间名称修改为';
  const descriptionPrefix = '房间简介修改为';
  const visibilityPrefix = '房间可见性修改为';
  const joinPolicyPrefix = '房间加入方式修改为';
  if (body.startsWith(namePrefix)) {
    return _roomProfileChangedPreview({
      subject: '房间名称',
      sender,
      value: body.substring(namePrefix.length),
    });
  }
  if (body.startsWith(descriptionPrefix)) {
    return _roomProfileChangedPreview({
      subject: '房间简介',
      sender,
      value: body.substring(descriptionPrefix.length),
    });
  }
  if (body.startsWith(visibilityPrefix)) {
    return _roomProfileChangedPreview({
      subject: '房间可见性',
      sender,
      value: body.substring(visibilityPrefix.length),
    });
  }
  if (body.startsWith(joinPolicyPrefix)) {
    return _roomProfileChangedPreview({
      subject: '房间加入方式',
      sender,
      value: body.substring(joinPolicyPrefix.length),
    });
  }
  return null;
}

function _roomProfileChangedPreview(opts: { subject: string; sender: string | null; value: string }): string {
  const { subject, sender, value } = opts;
  const normalizedValue = _normalizeSystemChangedValue(value);
  if (sender == null) return `${subject} 修改为 ${normalizedValue}`;
  return `${subject} 被 ${sender} 修改为 ${normalizedValue}`;
}

function _normalizeSystemChangedValue(value: string): string {
  const normalized = _collapseWhitespace(value);
  if (normalized.length === 0) return '（空）';
  return normalized;
}

export function roomSidebarLastMessageTime(room: RoomCard, opts: { now?: Date | null } = {}): string {
  const last = room.lastMessage;
  if (last == null) return '';
  return roomSidebarTimestamp(last.createdAt, { now: opts.now });
}

export function roomSidebarTimestamp(value: Date, opts: { now?: Date | null } = {}): string {
  // JS `Date` getters already read the local wall clock (Dart `toLocal()`).
  const local = value;
  const localNow = opts.now ?? new Date();
  const today = _localDate(localNow.getFullYear(), localNow.getMonth(), localNow.getDate());
  const date = _localDate(local.getFullYear(), local.getMonth(), local.getDate());
  const dayDelta = _inDays(today, date);
  const time = _formatClock(local);

  if (dayDelta === 0) return time;
  if (dayDelta === 1) return `昨天 ${time}`;
  if (dayDelta === 2) return `前天 ${time}`;
  if (dayDelta >= 3 && dayDelta < 7) return _weekdayLabel(_dartWeekday(local));
  return `${local.getFullYear()}/${_twoDigits(local.getMonth() + 1)}/${_twoDigits(local.getDate())}`;
}

export function roomCopySuccessNotice(label: string): string {
  return `${label} 已复制`;
}

export function roomCopyFailureMessage(error: unknown): string {
  return '无法复制';
}

export function userUidCopySuccessNotice(): string {
  return 'UID 已复制';
}

export function userUidCopyFailureMessage(error: unknown): string {
  return '无法复制 UID';
}

export function roomOpenFailureMessage(error: unknown): string {
  return '无法打开房间';
}

export function roomOptimisticOpenRefreshFailureNotice(): string {
  return '房间刷新失败，已先打开当前房间';
}

export function roomUseGlobalProfileNotice(): string {
  return '保存后将使用全局默认用户名和默认头像';
}

export function roomInfoSavedNotice(): string {
  return '房间信息已保存';
}

export function roomPersonalPreferencesSavedNotice(): string {
  return '个人偏好已保存';
}

export function roomLeaveConfirmationSpec(opts: { room: RoomDetail; isInLive: boolean }): RoomLeaveConfirmationSpec {
  const { room, isInLive } = opts;
  const confirmDeleteIfEmpty = room.isCreator && room.memberCount <= 1;
  if (confirmDeleteIfEmpty) {
    return new RoomLeaveConfirmationSpec({
      confirmDeleteIfEmpty: true,
      title: '退出并删除房间',
      body: '这是房间里的最后一位成员。退出会删除房间和所有房间内数据，请输入房间名确认。',
      confirmLabel: '退出并删除',
      expectedText: room.name,
    });
  }
  return new RoomLeaveConfirmationSpec({
    confirmDeleteIfEmpty: false,
    title: '退出房间',
    body: isInLive ? '退出后会离开当前房间，并同时离开 Live Channel。' : '退出后你会从房间成员中移除，房间会从列表中消失。',
    confirmLabel: '退出',
  });
}

export function roomDeletionConfirmationSpec(room: RoomDetail): RoomDeletionConfirmationSpec {
  return new RoomDeletionConfirmationSpec({
    title: '删除房间',
    body: '将清空房间所有数据。这个动作不可恢复，请输入房间名确认。',
    confirmLabel: '删除房间',
    expectedText: room.name,
  });
}

export function roomIdentifier(room: RoomDetail): string {
  return _nonEmpty(room.rid) ?? room.id;
}

export function roomCreatedAtLabel(value: Date): string {
  const local = value;
  return (
    `${String(local.getFullYear()).padStart(4, '0')}/` +
    `${_twoDigits(local.getMonth() + 1)}/` +
    `${_twoDigits(local.getDate())} ` +
    `${_twoDigits(local.getHours())}:` +
    `${_twoDigits(local.getMinutes())}`
  );
}

export function roomDescriptionValue(room: RoomDetail): string | null {
  return _nonEmpty(room.description);
}

export function roomDescriptionText(room: RoomDetail): string {
  return roomDescriptionValue(room) ?? '暂无介绍';
}

export function roomDisplayName(room: RoomDetail): string {
  const remark = _nonEmpty(room.remarkName);
  if (remark == null) return room.name;
  return remark;
}

export function roomAvatarLabel(room: RoomDetail): string {
  return _nonEmpty(room.name) ?? room.rid;
}

export function roomCardAvatarLabel(room: RoomCard): string {
  return _nonEmpty(room.name) ?? room.rid;
}

export function publicRoomAvatarLabel(room: PublicRoom): string {
  return _nonEmpty(room.avatarLabel) ?? _nonEmpty(room.name) ?? room.rid;
}

export function publicRoomFromRoomDetail(room: RoomDetail): PublicRoom {
  return new PublicRoom({
    id: room.id,
    rid: room.rid,
    name: roomDisplayName(room),
    avatarLabel: roomAvatarLabel(room),
    avatarUrl: room.avatarUrl,
    defaultAvatarKey: room.defaultAvatarKey,
    visibility: room.visibility,
    joinPolicy: room.joinPolicy,
    description: room.description,
    memberCount: room.memberCount,
    onlineMemberCount: room.onlineMemberCount,
    liveParticipantCount: room.live.participantCount,
    joined: true,
    joinState: 'joined',
    createdBy: room.createdBy,
    personalProfile: room.personalProfile,
    myMembership: room.myMembership,
  });
}

export function roomMemberSummary(room: RoomDetail): string {
  return `${room.memberCount} 名成员 · ${room.onlineMemberCount} 人在线`;
}

export function userPrimaryName(user: UserSummary): string {
  return _nonEmpty(user.roomDisplayName) ?? _nonEmpty(user.displayName) ?? user.username;
}

export function userAvatarLabel(user: UserSummary): string {
  return _nonEmpty(user.displayName) ?? user.username;
}

export function userUidLabel(user: UserSummary): string {
  return user.uid ?? user.id;
}

export function userUsernameLabel(user: UserSummary): string {
  return `@${user.username}`;
}

export function userIdentityMeta(user: UserSummary): string {
  return `${userUidLabel(user)} · ${userUsernameLabel(user)}`;
}

export function userSignatureText(user: UserSummary): string | null {
  return _nonEmpty(user.bio);
}

export function userRoomsSectionTitle(opts: { user: UserSummary; currentUser: CurrentUser }): string | null {
  const { user, currentUser } = opts;
  if (user.isSuperuser) return null;
  if (currentUser.isSuperuser || user.id === currentUser.id) {
    return '所有房间';
  }
  return '共同房间';
}

export function roomRoleLabel(user: UserSummary, opts: { ownerUserId?: string | null } = {}): string {
  const ownerUserId = opts.ownerUserId ?? null;
  const role = _nonEmpty(user.roomRole)?.toLowerCase() ?? null;
  if (role === 'pending') return '待审批';
  if (user.isSuperuser || role === 'superuser') return '超级用户';
  if (user.id === ownerUserId || role === 'owner' || role === 'creator') {
    return '创建者';
  }
  if (role === 'admin' || role === 'administrator') return '管理员';
  return '成员';
}

export function livePresenceAnnouncementForUser(opts: {
  user: UserSummary;
  action: LivePresenceAnnouncementAction;
  ownerUserId?: string | null;
}): LivePresenceAnnouncement {
  const { user, action } = opts;
  const roomDisplayName =
    _nonEmpty(user.roomDisplayName) ?? _nonEmpty(user.displayName) ?? _nonEmpty(user.username) ?? '用户';
  return new LivePresenceAnnouncement({
    roleLabel: roomRoleLabel(user, { ownerUserId: opts.ownerUserId }),
    roomDisplayName,
    action,
  });
}

export function commonRoomTitle(room: UserCommonRoom): string {
  const rid = _nonEmpty(room.rid);
  const name = commonRoomDisplayName(room);
  if (rid == null) return name;
  return `${name} · ${rid}`;
}

export function commonRoomDisplayName(room: UserCommonRoom): string {
  const remark = _nonEmpty(room.remarkName);
  if (remark == null) return room.name;
  return remark;
}

export function userWithLocalCommonRoomNames(opts: { user: UserSummary; rooms: Iterable<RoomCard> }): UserSummary {
  const { user, rooms } = opts;
  return user.copyWith({
    commonRooms: commonRoomsWithLocalRoomNames({
      commonRooms: user.commonRooms,
      rooms,
    }),
  });
}

export function commonRoomsWithLocalRoomNames(opts: {
  commonRooms: UserCommonRoom[];
  rooms: Iterable<RoomCard>;
}): UserCommonRoom[] {
  const { commonRooms, rooms } = opts;
  if (commonRooms.length === 0) return commonRooms;
  const localRoomsById = new Map<string, RoomCard>();
  for (const room of rooms) {
    if (room.id.trim().length > 0) localRoomsById.set(room.id, room);
  }
  if (localRoomsById.size === 0) return commonRooms;

  let changed = false;
  const resolved: UserCommonRoom[] = [];
  for (const commonRoom of commonRooms) {
    const localRoom = localRoomsById.get(commonRoom.id);
    if (localRoom == null) {
      resolved.push(commonRoom);
      continue;
    }
    changed = true;
    resolved.push(
      new UserCommonRoom({
        id: commonRoom.id,
        rid: localRoom.rid,
        name: localRoom.name,
        visibility: localRoom.visibility,
        remarkName: localRoom.remarkName,
        avatarUrl: localRoom.avatarUrl,
        defaultAvatarKey: localRoom.defaultAvatarKey,
        roomDisplayName: commonRoom.roomDisplayName,
        roomRole: commonRoom.roomRole,
      }),
    );
  }
  return changed ? resolved : commonRooms;
}

export function commonRoomAvatarLabel(room: UserCommonRoom): string {
  return _nonEmpty(room.name) ?? room.rid;
}

export function visibilityLabel(value: string): string {
  switch (value.toLowerCase()) {
    case 'public':
      return '公开';
    default:
      return '私有';
  }
}

export function roomJoinPolicyLabel(value: string): string {
  switch (normalizeRoomJoinPolicy(value)) {
    case 'open':
      return '开放';
    case 'closed':
      return '关闭';
    default:
      return '需审批';
  }
}

export function commonRoomMeta(room: UserCommonRoom): string | null {
  const roomDisplayName = _nonEmpty(room.roomDisplayName);
  const roleLabel = roomRoleLabelFromValue(room.roomRole);
  const parts = [roomDisplayName, roleLabel].filter((part): part is string => part != null);
  if (parts.length === 0) return null;
  return parts.join(' · ');
}

export function userPresenceLabel(user: UserSummary): string | null {
  const isOnline = user.isOnline;
  if (isOnline == null) return null;
  return isOnline ? '在线' : '离线';
}

export function currentUserPresenceLabel(
  user: CurrentUser,
  opts: { inLive: boolean; reconnecting?: boolean },
): string {
  const { inLive, reconnecting = false } = opts;
  if (reconnecting) return '重连中';
  if (inLive) return '语音';
  return userPresenceLabel(user.toSummary()) ?? '在线';
}

export function roomUserInfoProfile(opts: {
  user: UserSummary;
  room: RoomDetail;
  currentUser: CurrentUser;
}): UserSummary {
  const { user, room, currentUser } = opts;
  if (user.isDeleted) return user;
  let profile = user;
  const createdBy = room.createdBy;
  if (createdBy != null && user.id === createdBy.id) {
    profile = profile.mergeMissing(createdBy);
  }
  if (user.id === currentUser.id) {
    profile = profile.mergeMissing(currentUser.toSummary());
  }
  const roomRole =
    profile.roomRole ??
    (createdBy != null && user.id === createdBy.id
      ? 'owner'
      : user.id === currentUser.id
        ? room.myMembership.role
        : null);
  return profile.copyWith({ roomRole });
}

/**
 * Merges a room profile with a lightweight list/message snapshot only while
 * the user is still a room member. A former member's room-scoped name, role,
 * and avatar snapshot must not override the current default account profile.
 */
export function resolvedRoomMemberProfileUser(opts: {
  profile: RoomMemberProfile;
  fallback: UserSummary;
}): UserSummary {
  const { profile, fallback } = opts;
  const role = profile.role.trim().toLowerCase();
  if (profile.user.isDeleted || role === 'left' || role === 'deleted') {
    return profile.user;
  }
  return profile.user.mergeMissing(fallback);
}

export function roomUserInfoCommonRooms(opts: {
  user: UserSummary;
  selectedRoom: RoomDetail;
  currentUser: CurrentUser;
  includeSelectedRoom: boolean;
}): UserCommonRoom[] {
  const { user, selectedRoom, currentUser, includeSelectedRoom } = opts;
  if (user.isDeleted) return [];
  if (user.isSuperuser) {
    return [];
  }
  if (currentUser.isSuperuser || user.id === currentUser.id) {
    return includeSelectedRoom ? commonRoomsWithSelectedRoom({ user, room: selectedRoom }) : user.commonRooms;
  }
  if (includeSelectedRoom) {
    return commonRoomsWithSelectedRoom({ user, room: selectedRoom });
  }
  return user.commonRooms;
}

export function commonRoomsWithSelectedRoom(opts: { user: UserSummary; room: RoomDetail }): UserCommonRoom[] {
  const { user, room } = opts;
  return [
    new UserCommonRoom({
      id: room.id,
      rid: room.rid,
      name: room.name,
      visibility: room.visibility,
      remarkName: room.remarkName,
      avatarUrl: room.avatarUrl,
      defaultAvatarKey: room.defaultAvatarKey,
      roomDisplayName: user.roomDisplayName,
      roomRole: user.roomRole,
    }),
    ...user.commonRooms.filter((commonRoom) => commonRoom.id !== room.id),
  ];
}

export function roomRoleLabelFromValue(value: string | null): string | null {
  switch (_nonEmpty(value)?.toLowerCase()) {
    case 'superuser':
      return '超级用户';
    case 'owner':
    case 'creator':
      return '创建者';
    case 'admin':
    case 'administrator':
      return '管理员';
    case 'member':
      return '成员';
    case 'pending':
      return '待审批';
    default:
      return null;
  }
}

export function publicRoomJoinActionLabel(room: PublicRoom, opts: { pending: boolean }): string {
  if (room.joined) return '进入';
  if (opts.pending) return '待审批';
  if (room.joinPolicy === 'closed') return '不可加入';
  return '加入';
}

export function publicRoomJoinActionable(room: PublicRoom, opts: { pending: boolean }): boolean {
  if (room.isDeleted) return false;
  if (room.joined) return true;
  if (opts.pending) return false;
  return room.joinPolicy !== 'closed';
}

export function publicRoomJoinRequiresApplication(room: PublicRoom): boolean {
  return !room.isDeleted && !room.joined && room.joinPolicy === 'approval_required';
}

export function roomAccessState(opts: { room: RoomDetail; currentUser: CurrentUser }): RoomAccessState {
  const { room, currentUser } = opts;
  const canManageRoom = room.isAdmin || currentUser.isSuperuser;
  return new RoomAccessState({
    canManageRoom,
    canReviewJoinRequests: canManageRoom,
  });
}

export function roomManagementPermissionState(opts: {
  room: RoomDetail;
  currentUser: CurrentUser;
}): RoomManagementPermissionState {
  const { room, currentUser } = opts;
  const currentUserIsSuperuser = currentUser.isSuperuser;
  return new RoomManagementPermissionState({
    canEditCreatorOnly: room.isCreator || room.isSuperuser || currentUserIsSuperuser,
    canDeleteRoom: room.canDelete || currentUserIsSuperuser,
  });
}

export function roomProfileAvatarPath(opts: {
  usingGlobalProfile: boolean;
  currentUserAvatarUrl: string | null;
  pendingAvatarUrl: string | null;
  usingPresetAvatar: boolean;
  personalAvatarUrl: string | null;
}): string | null {
  if (opts.usingGlobalProfile) return opts.currentUserAvatarUrl;
  if (opts.pendingAvatarUrl != null) return opts.pendingAvatarUrl;
  if (opts.usingPresetAvatar) return null;
  return opts.personalAvatarUrl ?? opts.currentUserAvatarUrl;
}

export function roomProfileUploadedAvatarSelected(opts: {
  usingGlobalProfile: boolean;
  usingPresetAvatar: boolean;
  pendingAvatarUrl: string | null;
  personalAvatarUrl: string | null;
}): boolean {
  return (
    !opts.usingGlobalProfile &&
    !opts.usingPresetAvatar &&
    (opts.pendingAvatarUrl != null || opts.personalAvatarUrl != null)
  );
}

export function roomProfilePresetAvatarSelected(opts: {
  usingGlobalProfile: boolean;
  usingPresetAvatar: boolean;
}): boolean {
  return !opts.usingGlobalProfile && opts.usingPresetAvatar;
}

export function roomProfileDisplayName(opts: {
  usingGlobalProfile: boolean;
  currentUserDisplayName: string;
  roomDisplayNameText: string;
}): string {
  if (opts.usingGlobalProfile) return opts.currentUserDisplayName;
  return _nonEmpty(opts.roomDisplayNameText) ?? opts.currentUserDisplayName;
}

export function roomManagementAvatarPath(opts: {
  usingPresetAvatar: boolean;
  pendingAvatarUrl: string | null;
  roomAvatarUrl: string | null;
}): string | null {
  if (opts.usingPresetAvatar) return null;
  return opts.pendingAvatarUrl ?? opts.roomAvatarUrl;
}

export function roomManagementUploadedAvatarSelected(opts: { usingPresetAvatar: boolean }): boolean {
  return !opts.usingPresetAvatar;
}

export function normalizeRoomNotificationPolicy(value: string): string {
  switch (value.trim().toLowerCase()) {
    case 'silent':
    case 'quiet':
    case 'no_alert':
    case 'no_alerts':
    case 'receive_silent':
    case 'mention':
    case 'mentions':
    case 'only_mentions':
    case 'mention_only':
    case 'mute':
    case 'muted':
    case 'do_not_disturb':
    case 'dnd':
      return 'silent';
    case 'block':
    case 'blocked':
    case 'ignore':
    case 'ignored':
      return 'blocked';
    default:
      return 'all';
  }
}

export function normalizeRoomVisibility(value: string): string {
  switch (value.trim().toLowerCase()) {
    case 'private':
      return 'private';
    default:
      return 'public';
  }
}

export function normalizeRoomJoinPolicy(value: string): string {
  switch (value.trim().toLowerCase()) {
    case 'open':
    case 'allow_anyone':
    case 'anyone':
      return 'open';
    case 'closed':
    case 'none':
    case 'deny_all':
    case 'no_one':
      return 'closed';
    default:
      return 'approval_required';
  }
}

function _nonEmpty(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  if (trimmed == null || trimmed.length === 0) return null;
  return trimmed;
}

/** Dart `value.trim().split(RegExp(r'\s+')).where(isNotEmpty).join(' ')`. */
function _collapseWhitespace(value: string): string {
  return value
    .trim()
    .split(/\s+/)
    .filter((part) => part.length > 0)
    .join(' ');
}

function _formatClock(value: Date): string {
  return `${_twoDigits(value.getHours())}:${_twoDigits(value.getMinutes())}`;
}

function _twoDigits(value: number): string {
  return String(value).padStart(2, '0');
}

/** Local-time `DateTime(year, month, day)`; [monthIndex] is 0-based like JS. */
function _localDate(year: number, monthIndex: number, day: number): Date {
  const date = new Date(2000, monthIndex, day);
  date.setFullYear(year, monthIndex, day);
  return date;
}

/** Dart `Duration.inDays` of `later.difference(earlier)` (truncates toward zero). */
function _inDays(later: Date, earlier: Date): number {
  return Math.trunc((later.getTime() - earlier.getTime()) / 86_400_000);
}

/** Dart `DateTime.weekday`: Monday = 1 … Sunday = 7. */
function _dartWeekday(value: Date): number {
  const day = value.getDay();
  return day === 0 ? 7 : day;
}

function _weekdayLabel(weekday: number): string {
  switch (weekday) {
    case 1:
      return '星期一';
    case 2:
      return '星期二';
    case 3:
      return '星期三';
    case 4:
      return '星期四';
    case 5:
      return '星期五';
    case 6:
      return '星期六';
    case 7:
      return '星期日';
    default:
      return '星期日';
  }
}
