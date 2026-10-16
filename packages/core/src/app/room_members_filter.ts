import { RoomMember, type CurrentUser, type LiveState, type RoomDetail, type UserSummary } from '../protocol/models';
import { userFacingErrorMessage } from './error_display';

export enum RoomMemberPresence {
  live = 'live',
  online = 'online',
  offline = 'offline',
}

export enum RoomMemberPresenceFilter {
  all = 'all',
  live = 'live',
  online = 'online',
  offline = 'offline',
}

export enum RoomMemberRoleFilter {
  all = 'all',
  member = 'member',
  admin = 'admin',
  creator = 'creator',
}

export class RoomMemberPermissionState {
  constructor(opts: {
    isSuperuser: boolean;
    isOwner: boolean;
    isAdmin: boolean;
    canEditRoomDisplayName: boolean;
    canRoleEdit: boolean;
    canRemoveMember: boolean;
  }) {
    this.isSuperuser = opts.isSuperuser;
    this.isOwner = opts.isOwner;
    this.isAdmin = opts.isAdmin;
    this.canEditRoomDisplayName = opts.canEditRoomDisplayName;
    this.canRoleEdit = opts.canRoleEdit;
    this.canRemoveMember = opts.canRemoveMember;
  }

  readonly isSuperuser: boolean;
  readonly isOwner: boolean;
  readonly isAdmin: boolean;
  readonly canEditRoomDisplayName: boolean;
  readonly canRoleEdit: boolean;
  readonly canRemoveMember: boolean;

  get canSetAdmin(): boolean {
    return this.canRoleEdit && !this.isAdmin;
  }

  get canUnsetAdmin(): boolean {
    return this.canRoleEdit && this.isAdmin;
  }

  get canTransferCreator(): boolean {
    return this.canRoleEdit;
  }

  get adminActionLabel(): string {
    return this.isAdmin ? '移除管理员' : '设为管理员';
  }
}

export class RoomMemberManagementPatch {
  constructor(opts: {
    room: RoomDetail;
    members: RoomMember[];
    busyMemberIds: Set<string>;
    changed: boolean;
    error: string | null;
    notice: string | null;
  }) {
    this.room = opts.room;
    this.members = opts.members;
    this.busyMemberIds = opts.busyMemberIds;
    this.changed = opts.changed;
    this.error = opts.error;
    this.notice = opts.notice;
  }

  readonly room: RoomDetail;
  readonly members: RoomMember[];
  readonly busyMemberIds: Set<string>;
  readonly changed: boolean;
  readonly error: string | null;
  readonly notice: string | null;
}

export class RoomMemberLoadPatch {
  constructor(opts: { members: RoomMember[]; loading: boolean; error: string | null }) {
    this.members = opts.members;
    this.loading = opts.loading;
    this.error = opts.error;
  }

  readonly members: RoomMember[];
  readonly loading: boolean;
  readonly error: string | null;
}

export class RoomMemberFilterPatch {
  constructor(opts: {
    searchQuery: string;
    presenceFilter: RoomMemberPresenceFilter;
    roleFilter: RoomMemberRoleFilter;
  }) {
    this.searchQuery = opts.searchQuery;
    this.presenceFilter = opts.presenceFilter;
    this.roleFilter = opts.roleFilter;
  }

  readonly searchQuery: string;
  readonly presenceFilter: RoomMemberPresenceFilter;
  readonly roleFilter: RoomMemberRoleFilter;
}

export class RoomMemberPresenceGroup {
  constructor(opts: { presence: RoomMemberPresence; members: readonly RoomMember[] }) {
    this.presence = opts.presence;
    this.members = opts.members;
  }

  readonly presence: RoomMemberPresence;
  readonly members: readonly RoomMember[];

  get count(): number {
    return this.members.length;
  }
}

export class RoomMemberFilterCounts {
  constructor(opts: {
    allPresence: number;
    live: number;
    online: number;
    offline: number;
    allRoles: number;
    roleMembers: number;
    admins: number;
    creators: number;
  }) {
    this.allPresence = opts.allPresence;
    this.live = opts.live;
    this.online = opts.online;
    this.offline = opts.offline;
    this.allRoles = opts.allRoles;
    this.roleMembers = opts.roleMembers;
    this.admins = opts.admins;
    this.creators = opts.creators;
  }

  readonly allPresence: number;
  readonly live: number;
  readonly online: number;
  readonly offline: number;
  readonly allRoles: number;
  readonly roleMembers: number;
  readonly admins: number;
  readonly creators: number;
}

export function canStartRoomMemberAction(opts: { userId: string; busyMemberIds: Iterable<string> }): boolean {
  return !_iterableContains(opts.busyMemberIds, opts.userId);
}

export function canOpenRoomMemberManagementFromProfile(opts: {
  room: RoomDetail;
  currentUser: CurrentUser;
  target: UserSummary;
}): boolean {
  const { room, currentUser, target } = opts;
  const uid = target.uid?.trim();
  if (uid == null || uid.length === 0 || target.id === currentUser.id) {
    return false;
  }
  const role = target.roomRole ?? (target.id === room.createdBy?.id ? 'owner' : 'member');
  const permission = roomMemberPermissionState({
    member: new RoomMember({
      user: target,
      role,
      joinedAt: new Date(0),
    }),
    currentUser,
    canEditCreatorOnly: room.isCreator || room.isSuperuser || currentUser.isSuperuser,
    canManageMembers: room.isAdmin || currentUser.isSuperuser,
    ownerUserId: room.createdBy?.id ?? null,
  });
  return permission.canRoleEdit || permission.canRemoveMember;
}

export function roomMemberActionBusy(opts: { member: RoomMember; busyMemberIds: Iterable<string> }): boolean {
  return _iterableContains(opts.busyMemberIds, opts.member.user.id);
}

export function roomMemberRoleUpdateNotice(role: string): string {
  return role === 'admin' ? '已授予管理员身份' : '已移除管理员身份';
}

export function roomMemberRoleUpdateConfirmTitle(role: string): string {
  return role === 'admin' ? '设为管理员' : '移除管理员';
}

export function roomMemberRoleUpdateConfirmBody(member: RoomMember, role: string): string {
  const name = roomMemberDisplayName(member);
  return role === 'admin' ? `确定要将 ${name} 设为管理员吗？` : `确定要移除 ${name} 的管理员身份吗？`;
}

export function roomMemberRoleUpdateConfirmLabel(role: string): string {
  return role === 'admin' ? '设为管理员' : '移除';
}

export function transferCreatorDialogTitle(): string {
  return '转让创建者';
}

export function transferCreatorConfirmBody(member: RoomMember, opts: { currentUserIsCreator: boolean }): string {
  const demotedCreator = opts.currentUserIsCreator ? '你' : '原创建者';
  return `创建者身份会转让给 ${roomMemberDisplayName(member)}，${demotedCreator}将成为管理员。`;
}

export function transferCreatorConfirmLabel(): string {
  return '转让';
}

export function transferCreatorSuccessNotice(): string {
  return '创建者已转让';
}

export function removeRoomMemberConfirmTitle(): string {
  return '踢出此用户';
}

export function removeRoomMemberConfirmBody(member: RoomMember): string {
  return `确定要将 ${roomMemberDisplayName(member)} 从房间中移除吗？`;
}

export function removeRoomMemberConfirmLabel(): string {
  return '踢出';
}

export function removeRoomMemberSuccessNotice(member: RoomMember): string {
  return `已踢出 ${roomMemberDisplayName(member)}`;
}

export function roomMembersLoadStarted(opts: { members: Iterable<RoomMember> }): RoomMemberLoadPatch {
  return new RoomMemberLoadPatch({
    members: [...opts.members],
    loading: true,
    error: null,
  });
}

export function roomMembersLoadSucceeded(opts: { members: Iterable<RoomMember> }): RoomMemberLoadPatch {
  return new RoomMemberLoadPatch({
    members: [...opts.members],
    loading: false,
    error: null,
  });
}

export function roomMembersLoadFailed(opts: { members: Iterable<RoomMember>; failure: unknown }): RoomMemberLoadPatch {
  return new RoomMemberLoadPatch({
    members: [...opts.members],
    loading: false,
    error: userFacingErrorMessage(opts.failure),
  });
}

export function roomMemberPresenceFilterChanged(opts: {
  searchQuery: string;
  roleFilter: RoomMemberRoleFilter;
  presenceFilter: RoomMemberPresenceFilter;
}): RoomMemberFilterPatch {
  return new RoomMemberFilterPatch({
    searchQuery: opts.searchQuery,
    presenceFilter: opts.presenceFilter,
    roleFilter: opts.roleFilter,
  });
}

export function roomMemberRoleFilterChanged(opts: {
  searchQuery: string;
  presenceFilter: RoomMemberPresenceFilter;
  roleFilter: RoomMemberRoleFilter;
}): RoomMemberFilterPatch {
  return new RoomMemberFilterPatch({
    searchQuery: opts.searchQuery,
    presenceFilter: opts.presenceFilter,
    roleFilter: opts.roleFilter,
  });
}

export function roomMemberSearchQueryChanged(opts: {
  searchQuery: string;
  presenceFilter: RoomMemberPresenceFilter;
  roleFilter: RoomMemberRoleFilter;
}): RoomMemberFilterPatch {
  return new RoomMemberFilterPatch({
    searchQuery: opts.searchQuery,
    presenceFilter: opts.presenceFilter,
    roleFilter: opts.roleFilter,
  });
}

export function roomMemberManagementActionStarted(opts: {
  room: RoomDetail;
  members: Iterable<RoomMember>;
  changed: boolean;
  userId: string;
  busyMemberIds: Iterable<string>;
}): RoomMemberManagementPatch {
  return new RoomMemberManagementPatch({
    room: opts.room,
    members: [...opts.members],
    busyMemberIds: new Set([...opts.busyMemberIds, opts.userId]),
    changed: opts.changed,
    error: null,
    notice: null,
  });
}

export function roomMemberRoleUpdateSucceeded(opts: {
  room: RoomDetail;
  members: Iterable<RoomMember>;
  updated: RoomMember;
  role: string;
  busyMemberIds: Iterable<string>;
}): RoomMemberManagementPatch {
  return new RoomMemberManagementPatch({
    room: opts.room,
    members: replaceRoomMember(opts.members, opts.updated),
    busyMemberIds: _setWithout(opts.busyMemberIds, opts.updated.user.id),
    changed: true,
    error: null,
    notice: roomMemberRoleUpdateNotice(opts.role),
  });
}

export function roomMemberRoleUpdateFailed(opts: {
  room: RoomDetail;
  members: Iterable<RoomMember>;
  changed: boolean;
  userId: string;
  busyMemberIds: Iterable<string>;
  failure: unknown;
}): RoomMemberManagementPatch {
  return new RoomMemberManagementPatch({
    room: opts.room,
    members: [...opts.members],
    busyMemberIds: _setWithout(opts.busyMemberIds, opts.userId),
    changed: opts.changed,
    error: userFacingErrorMessage(opts.failure),
    notice: null,
  });
}

export function roomMemberRemovedSucceeded(opts: {
  room: RoomDetail;
  members: Iterable<RoomMember>;
  removed: RoomMember;
  busyMemberIds: Iterable<string>;
}): RoomMemberManagementPatch {
  const { removed } = opts;
  return new RoomMemberManagementPatch({
    room: opts.room,
    members: [...opts.members].filter((member) => member.user.id !== removed.user.id),
    busyMemberIds: _setWithout(opts.busyMemberIds, removed.user.id),
    changed: true,
    error: null,
    notice: removeRoomMemberSuccessNotice(removed),
  });
}

export function roomMemberRemoveFailed(opts: {
  room: RoomDetail;
  members: Iterable<RoomMember>;
  changed: boolean;
  userId: string;
  busyMemberIds: Iterable<string>;
  failure: unknown;
}): RoomMemberManagementPatch {
  return new RoomMemberManagementPatch({
    room: opts.room,
    members: [...opts.members],
    busyMemberIds: _setWithout(opts.busyMemberIds, opts.userId),
    changed: opts.changed,
    error: userFacingErrorMessage(opts.failure),
    notice: null,
  });
}

export function transferCreatorSucceeded(opts: {
  updatedRoom: RoomDetail;
  members: Iterable<RoomMember>;
  userId: string;
  busyMemberIds: Iterable<string>;
}): RoomMemberManagementPatch {
  return new RoomMemberManagementPatch({
    room: opts.updatedRoom,
    members: [...opts.members],
    busyMemberIds: _setWithout(opts.busyMemberIds, opts.userId),
    changed: true,
    error: null,
    notice: transferCreatorSuccessNotice(),
  });
}

export function transferCreatorFailed(opts: {
  room: RoomDetail;
  members: Iterable<RoomMember>;
  changed: boolean;
  userId: string;
  busyMemberIds: Iterable<string>;
  failure: unknown;
}): RoomMemberManagementPatch {
  return new RoomMemberManagementPatch({
    room: opts.room,
    members: [...opts.members],
    busyMemberIds: _setWithout(opts.busyMemberIds, opts.userId),
    changed: opts.changed,
    error: userFacingErrorMessage(opts.failure),
    notice: null,
  });
}

export function roomMemberPresenceLabel(presence: RoomMemberPresence): string {
  switch (presence) {
    case RoomMemberPresence.live:
      return '语音';
    case RoomMemberPresence.online:
      return '在线';
    case RoomMemberPresence.offline:
      return '离线';
  }
}

export function roomMemberFilterCounts(opts: {
  members: Iterable<RoomMember>;
  live: LiveState;
  ownerUserId?: string | null;
}): RoomMemberFilterCounts {
  const { members, live } = opts;
  const ownerUserId = opts.ownerUserId ?? null;
  let allPresence = 0;
  let liveCount = 0;
  let online = 0;
  let offline = 0;
  let allRoles = 0;
  let roleMembers = 0;
  let admins = 0;
  let creators = 0;

  for (const member of members) {
    allPresence += 1;
    allRoles += 1;

    const presence = roomMemberPresence(member, { live });
    switch (presence) {
      case RoomMemberPresence.live:
        liveCount += 1;
        online += 1;
        break;
      case RoomMemberPresence.online:
        online += 1;
        break;
      case RoomMemberPresence.offline:
        offline += 1;
        break;
    }

    if (_matchesRoleFilter(member, RoomMemberRoleFilter.member, { ownerUserId })) {
      roleMembers += 1;
    }
    if (_matchesRoleFilter(member, RoomMemberRoleFilter.admin, { ownerUserId })) {
      admins += 1;
    }
    if (_matchesRoleFilter(member, RoomMemberRoleFilter.creator, { ownerUserId })) {
      creators += 1;
    }
  }

  return new RoomMemberFilterCounts({
    allPresence,
    live: liveCount,
    online,
    offline,
    allRoles,
    roleMembers,
    admins,
    creators,
  });
}

export function roomMemberPresenceFilterLabel(
  filter: RoomMemberPresenceFilter,
  counts: RoomMemberFilterCounts,
): string {
  let label: string;
  let count: number;
  switch (filter) {
    case RoomMemberPresenceFilter.all:
      [label, count] = ['全部', counts.allPresence];
      break;
    case RoomMemberPresenceFilter.live:
      [label, count] = ['语音', counts.live];
      break;
    case RoomMemberPresenceFilter.online:
      [label, count] = ['在线', counts.online];
      break;
    case RoomMemberPresenceFilter.offline:
      [label, count] = ['离线', counts.offline];
      break;
  }
  return `${label} ${count}`;
}

export function roomMemberRoleFilterLabel(filter: RoomMemberRoleFilter, counts: RoomMemberFilterCounts): string {
  let label: string;
  let count: number;
  switch (filter) {
    case RoomMemberRoleFilter.all:
      [label, count] = ['所有身份', counts.allRoles];
      break;
    case RoomMemberRoleFilter.member:
      [label, count] = ['成员', counts.roleMembers];
      break;
    case RoomMemberRoleFilter.admin:
      [label, count] = ['管理员', counts.admins];
      break;
    case RoomMemberRoleFilter.creator:
      [label, count] = ['创建者', counts.creators];
      break;
  }
  return `${label} ${count}`;
}

export function visibleRoomMembers(opts: {
  members: Iterable<RoomMember>;
  live: LiveState;
  presenceFilter: RoomMemberPresenceFilter;
  roleFilter: RoomMemberRoleFilter;
  query: string;
  ownerUserId?: string | null;
}): RoomMember[] {
  const { live, query } = opts;
  const ownerUserId = opts.ownerUserId ?? null;
  const visible = filteredRoomMembers(opts);
  const normalizedQuery = query.trim().toLowerCase();
  visible.sort((a, b) =>
    compareRoomMembers(a, b, {
      live,
      query: normalizedQuery,
      ownerUserId,
    }),
  );
  return visible;
}

export function filteredRoomMembers(opts: {
  members: Iterable<RoomMember>;
  live: LiveState;
  presenceFilter: RoomMemberPresenceFilter;
  roleFilter: RoomMemberRoleFilter;
  query: string;
  ownerUserId?: string | null;
}): RoomMember[] {
  const { members, live, presenceFilter, roleFilter, query } = opts;
  const ownerUserId = opts.ownerUserId ?? null;
  const normalizedQuery = query.trim().toLowerCase();
  return [...members].filter((member) => {
    const presence = roomMemberPresence(member, { live });
    if (!_matchesPresenceFilter(presence, presenceFilter)) {
      return false;
    }
    if (!_matchesRoleFilter(member, roleFilter, { ownerUserId })) {
      return false;
    }
    if (normalizedQuery.length === 0) return true;
    return roomMemberSearchRank(member, normalizedQuery) < 99;
  });
}

export function orderRoomMembersByUserIds(opts: {
  members: Iterable<RoomMember>;
  orderedUserIds: Iterable<string>;
}): RoomMember[] {
  const remaining = new Map<string, RoomMember>();
  for (const member of opts.members) remaining.set(member.user.id, member);
  const ordered: RoomMember[] = [];
  for (const userId of opts.orderedUserIds) {
    const member = remaining.get(userId);
    if (member != null) {
      remaining.delete(userId);
      ordered.push(member);
    }
  }
  ordered.push(...remaining.values());
  return ordered;
}

const _roomMemberPresenceValues: readonly RoomMemberPresence[] = [
  RoomMemberPresence.live,
  RoomMemberPresence.online,
  RoomMemberPresence.offline,
];

export function roomMemberPresenceGroups(opts: {
  members: Iterable<RoomMember>;
  live: LiveState;
}): RoomMemberPresenceGroup[] {
  const { members, live } = opts;
  const buckets = new Map<RoomMemberPresence, RoomMember[]>();
  for (const presence of _roomMemberPresenceValues) buckets.set(presence, []);
  for (const member of members) {
    buckets.get(roomMemberPresence(member, { live }))!.push(member);
  }
  const groups: RoomMemberPresenceGroup[] = [];
  for (const presence of _roomMemberPresenceValues) {
    const bucket = buckets.get(presence)!;
    if (bucket.length > 0) {
      groups.push(
        new RoomMemberPresenceGroup({
          presence,
          members: Object.freeze([...bucket]),
        }),
      );
    }
  }
  return groups;
}

function _matchesPresenceFilter(presence: RoomMemberPresence, filter: RoomMemberPresenceFilter): boolean {
  switch (filter) {
    case RoomMemberPresenceFilter.all:
      return true;
    case RoomMemberPresenceFilter.live:
      return presence === RoomMemberPresence.live;
    case RoomMemberPresenceFilter.online:
      return presence !== RoomMemberPresence.offline;
    case RoomMemberPresenceFilter.offline:
      return presence === RoomMemberPresence.offline;
  }
}

export function roomMembersWithCurrentUserPresence(
  members: Iterable<RoomMember>,
  opts: { currentUserId: string },
): RoomMember[] {
  return [...members].map((member) => roomMemberWithCurrentUserPresence(member, opts));
}

export function roomMemberWithCurrentUserPresence(member: RoomMember, opts: { currentUserId: string }): RoomMember {
  const { currentUserId } = opts;
  if (currentUserId.length === 0 || member.user.id !== currentUserId) {
    return member;
  }
  if (member.isOnline === true && member.user.isOnline === true) {
    return member;
  }
  return new RoomMember({
    user: member.user.copyWith({ isOnline: true }),
    role: member.role,
    joinedAt: member.joinedAt,
    roomDisplayName: member.roomDisplayName,
    remarkName: member.remarkName,
    textMutedUntil: member.textMutedUntil,
    isOnline: true,
  });
}

export function roomMemberPresence(member: RoomMember, opts: { live: LiveState }): RoomMemberPresence {
  const inLive = opts.live.participants.some((p) => p.user.id === member.user.id);
  if (inLive) return RoomMemberPresence.live;
  if (member.isOnline ?? false) return RoomMemberPresence.online;
  return RoomMemberPresence.offline;
}

export function compareRoomMembers(
  a: RoomMember,
  b: RoomMember,
  opts: { live: LiveState; query: string; ownerUserId?: string | null },
): number {
  const { live, query } = opts;
  const ownerUserId = opts.ownerUserId ?? null;
  const presence =
    _presenceRank(roomMemberPresence(a, { live })) - _presenceRank(roomMemberPresence(b, { live }));
  if (presence !== 0) return presence;

  const role = roomMemberRoleRank(a, { ownerUserId }) - roomMemberRoleRank(b, { ownerUserId });
  if (role !== 0) return role;

  if (query.length > 0) {
    const search = roomMemberSearchRank(a, query) - roomMemberSearchRank(b, query);
    if (search !== 0) return search;
  }

  const name = _compareStrings(roomMemberDisplayName(a).toLowerCase(), roomMemberDisplayName(b).toLowerCase());
  if (name !== 0) return name;
  return _compareStrings(a.user.uid ?? a.user.id, b.user.uid ?? b.user.id);
}

export function roomMemberRoleRank(member: RoomMember, opts: { ownerUserId?: string | null } = {}): number {
  if (isSuperuserRoomMember(member)) return 0;
  if (isOwnerRoomMember(member, opts)) return 1;
  if (isAdminRoomMember(member)) return 2;
  return 3;
}

export function isSuperuserRoomMember(member: RoomMember): boolean {
  const role = member.role.toLowerCase();
  return member.user.isSuperuser || role === 'superuser';
}

export function isOwnerRoomMember(member: RoomMember, opts: { ownerUserId?: string | null } = {}): boolean {
  const ownerUserId = opts.ownerUserId ?? null;
  const role = member.role.toLowerCase();
  return member.user.id === ownerUserId || role === 'owner' || role === 'creator';
}

export function isAdminRoomMember(member: RoomMember): boolean {
  const role = member.role.toLowerCase();
  return role === 'admin' || role === 'administrator';
}

export function roomMemberPermissionState(opts: {
  member: RoomMember;
  currentUser: CurrentUser;
  canEditCreatorOnly: boolean;
  canManageMembers?: boolean;
  ownerUserId?: string | null;
}): RoomMemberPermissionState {
  const { member, currentUser, canEditCreatorOnly, canManageMembers = false } = opts;
  const ownerUserId = opts.ownerUserId ?? null;
  const isSuperuser = isSuperuserRoomMember(member);
  const isOwner = isOwnerRoomMember(member, { ownerUserId });
  const isAdmin = isAdminRoomMember(member);
  const currentUserIsSuperuser = currentUser.isSuperuser;
  const currentRank = currentUserPrivilegeRank({
    currentUser,
    canEditCreatorOnly,
    canManageMembers,
  });
  const targetRank = roomMemberPrivilegeRank(member, { ownerUserId });
  const canEditRoomDisplayName = canManageMembers && member.user.id !== currentUser.id && currentRank > targetRank;
  const canRoleEdit = canEditCreatorOnly && !isSuperuser && !isOwner && member.user.id !== currentUser.id;
  const canRemoveMember =
    canManageMembers &&
    !isSuperuser &&
    (!isOwner || currentUserIsSuperuser) &&
    member.user.id !== currentUser.id &&
    (canEditCreatorOnly || !isAdmin);
  return new RoomMemberPermissionState({
    isSuperuser,
    isOwner,
    isAdmin,
    canEditRoomDisplayName,
    canRoleEdit,
    canRemoveMember,
  });
}

export function currentUserPrivilegeRank(opts: {
  currentUser: CurrentUser;
  canEditCreatorOnly: boolean;
  canManageMembers: boolean;
}): number {
  if (opts.currentUser.isSuperuser) return 4;
  if (opts.canEditCreatorOnly) return 3;
  if (opts.canManageMembers) return 2;
  return 1;
}

export function roomMemberPrivilegeRank(member: RoomMember, opts: { ownerUserId?: string | null } = {}): number {
  if (isSuperuserRoomMember(member)) return 4;
  if (isOwnerRoomMember(member, opts)) return 3;
  if (isAdminRoomMember(member)) return 2;
  return 1;
}

export function roomMemberDisplayName(member: RoomMember): string {
  return (
    _nonEmpty(member.roomDisplayName) ??
    _nonEmpty(member.user.roomDisplayName) ??
    _nonEmpty(member.user.displayName) ??
    member.user.username
  );
}

export function roomMemberDefaultDisplayName(member: RoomMember): string {
  return _nonEmpty(member.user.displayName) ?? member.user.username;
}

export function roomMemberRoomDisplayNameValue(member: RoomMember): string {
  return _nonEmpty(member.roomDisplayName) ?? _nonEmpty(member.user.roomDisplayName) ?? '';
}

export function roomMemberRoomDisplayNameOriginalLabel(member: RoomMember): string {
  return roomMemberDisplayName(member);
}

export function roomMemberRoomDisplayNameUpdatedNotice(member: RoomMember): string {
  return `已修改 ${roomMemberDefaultDisplayName(member)} 的房间内用户名`;
}

export function roomMemberSearchRank(member: RoomMember, query: string): number {
  const normalizedQuery = query.trim().toLowerCase();
  if (normalizedQuery.length === 0) return 0;

  const contains = (value: string | null | undefined): boolean => {
    const text = _nonEmpty(value)?.toLowerCase();
    return text != null && text.includes(normalizedQuery);
  };

  if (contains(member.user.uid) || contains(member.user.id)) return 0;
  if (contains(member.user.displayName) || contains(member.user.username)) {
    return 1;
  }
  if (contains(member.remarkName)) return 2;
  return 99;
}

export function roomMemberMeta(member: RoomMember): string {
  const uid = member.user.uid ?? member.user.id;
  const remark = _nonEmpty(member.remarkName);
  const parts: string[] = [uid, `@${member.user.username}`];
  if (remark != null) parts.push(`备注 ${remark}`);
  return parts.join(' · ');
}

export function replaceRoomMember(members: Iterable<RoomMember>, updated: RoomMember): RoomMember[] {
  return [...members].map((member) => (member.user.id === updated.user.id ? updated : member));
}

export function roomMemberWithRole(member: RoomMember, role: string): RoomMember {
  return new RoomMember({
    user: member.user.copyWith({ roomRole: role }),
    role,
    joinedAt: member.joinedAt,
    roomDisplayName: member.roomDisplayName,
    remarkName: member.remarkName,
    textMutedUntil: member.textMutedUntil,
    isOnline: member.isOnline,
  });
}

function _matchesRoleFilter(
  member: RoomMember,
  filter: RoomMemberRoleFilter,
  opts: { ownerUserId?: string | null } = {},
): boolean {
  switch (filter) {
    case RoomMemberRoleFilter.all:
      return true;
    case RoomMemberRoleFilter.member:
      return !isSuperuserRoomMember(member) && !isOwnerRoomMember(member, opts) && !isAdminRoomMember(member);
    case RoomMemberRoleFilter.admin:
      return isSuperuserRoomMember(member) || isOwnerRoomMember(member, opts) || isAdminRoomMember(member);
    case RoomMemberRoleFilter.creator:
      return !isSuperuserRoomMember(member) && isOwnerRoomMember(member, opts);
  }
}

function _presenceRank(value: RoomMemberPresence): number {
  switch (value) {
    case RoomMemberPresence.live:
      return 0;
    case RoomMemberPresence.online:
      return 1;
    case RoomMemberPresence.offline:
      return 2;
  }
}

function _nonEmpty(value: string | null | undefined): string | null {
  const text = value?.trim();
  if (text == null || text.length === 0) return null;
  return text;
}

/** Dart `String.compareTo`: UTF-16 code unit order. */
function _compareStrings(a: string, b: string): number {
  if (a === b) return 0;
  return a < b ? -1 : 1;
}

function _iterableContains<T>(values: Iterable<T>, value: T): boolean {
  for (const item of values) {
    if (item === value) return true;
  }
  return false;
}

function _setWithout(values: Iterable<string>, removed: string): Set<string> {
  const result = new Set<string>();
  for (const item of values) {
    if (item !== removed) result.add(item);
  }
  return result;
}
