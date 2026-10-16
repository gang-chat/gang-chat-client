import { expect, test } from 'vitest';

import {
  canOpenRoomMemberManagementFromProfile,
  canStartRoomMemberAction,
  filteredRoomMembers,
  orderRoomMembersByUserIds,
  removeRoomMemberConfirmBody,
  removeRoomMemberConfirmLabel,
  removeRoomMemberConfirmTitle,
  removeRoomMemberSuccessNotice,
  replaceRoomMember,
  RoomMemberPresence,
  RoomMemberPresenceFilter,
  RoomMemberRoleFilter,
  roomMemberActionBusy,
  roomMemberFilterCounts,
  roomMemberManagementActionStarted,
  roomMemberPermissionState,
  roomMemberPresence,
  roomMemberPresenceFilterChanged,
  roomMemberPresenceFilterLabel,
  roomMemberPresenceGroups,
  roomMemberPresenceLabel,
  roomMemberRemovedSucceeded,
  roomMemberRemoveFailed,
  roomMemberRoleFilterChanged,
  roomMemberRoleFilterLabel,
  roomMemberRoleUpdateConfirmBody,
  roomMemberRoleUpdateConfirmLabel,
  roomMemberRoleUpdateConfirmTitle,
  roomMemberRoleUpdateFailed,
  roomMemberRoleUpdateNotice,
  roomMemberRoleUpdateSucceeded,
  roomMembersLoadFailed,
  roomMembersLoadStarted,
  roomMembersLoadSucceeded,
  roomMemberSearchQueryChanged,
  roomMembersWithCurrentUserPresence,
  roomMemberWithRole,
  transferCreatorConfirmBody,
  transferCreatorConfirmLabel,
  transferCreatorDialogTitle,
  transferCreatorFailed,
  transferCreatorSucceeded,
  transferCreatorSuccessNotice,
  visibleRoomMembers,
} from '../../src/app/room_members_filter';
import {
  CurrentUser,
  LiveParticipant,
  LiveState,
  RoomDetail,
  RoomMember,
  RoomMembership,
  UserSummary,
} from '../../src/protocol/models';

const _june4 = new Date(Date.UTC(2026, 5, 4));
const _all = { presenceFilter: RoomMemberPresenceFilter.all, roleFilter: RoomMemberRoleFilter.all };
const ids = (members: readonly RoomMember[]) => members.map((member) => member.user.id);

test('roomMemberPresenceLabel exposes reusable display labels', () => {
  expect(roomMemberPresenceLabel(RoomMemberPresence.live)).toBe('语音');
  expect(roomMemberPresenceLabel(RoomMemberPresence.online)).toBe('在线');
  expect(roomMemberPresenceLabel(RoomMemberPresence.offline)).toBe('离线');
});

test('room member filter counts feed presence and role labels', () => {
  const counts = roomMemberFilterCounts({
    members: [
      _member('owner', { isOnline: false }),
      _member('admin', { role: 'admin', isOnline: true }),
      _member('member', { isOnline: false }),
      _member('live_member', { isOnline: false }),
    ],
    live: _live(['live_member']),
    ownerUserId: 'owner',
  });

  expect(counts.allPresence).toBe(4);
  expect(counts.live).toBe(1);
  expect(counts.online).toBe(2);
  expect(counts.offline).toBe(2);
  expect(counts.allRoles).toBe(4);
  expect(counts.roleMembers).toBe(2);
  expect(counts.admins).toBe(2);
  expect(counts.creators).toBe(1);
  expect(roomMemberPresenceFilterLabel(RoomMemberPresenceFilter.all, counts)).toBe('全部 4');
  expect(roomMemberPresenceFilterLabel(RoomMemberPresenceFilter.live, counts)).toBe('语音 1');
  expect(roomMemberPresenceFilterLabel(RoomMemberPresenceFilter.online, counts)).toBe('在线 2');
  expect(roomMemberPresenceFilterLabel(RoomMemberPresenceFilter.offline, counts)).toBe('离线 2');
  expect(roomMemberRoleFilterLabel(RoomMemberRoleFilter.all, counts)).toBe('所有身份 4');
  expect(roomMemberRoleFilterLabel(RoomMemberRoleFilter.member, counts)).toBe('成员 2');
  expect(roomMemberRoleFilterLabel(RoomMemberRoleFilter.admin, counts)).toBe('管理员 2');
  expect(roomMemberRoleFilterLabel(RoomMemberRoleFilter.creator, counts)).toBe('创建者 1');
});

test('room member filter patches preserve the other filter dimension', () => {
  const presencePatch = roomMemberPresenceFilterChanged({
    searchQuery: 'alice',
    presenceFilter: RoomMemberPresenceFilter.online,
    roleFilter: RoomMemberRoleFilter.admin,
  });

  expect(presencePatch.searchQuery).toBe('alice');
  expect(presencePatch.presenceFilter).toBe(RoomMemberPresenceFilter.online);
  expect(presencePatch.roleFilter).toBe(RoomMemberRoleFilter.admin);

  const rolePatch = roomMemberRoleFilterChanged({
    searchQuery: 'bob',
    presenceFilter: RoomMemberPresenceFilter.offline,
    roleFilter: RoomMemberRoleFilter.member,
  });

  expect(rolePatch.searchQuery).toBe('bob');
  expect(rolePatch.presenceFilter).toBe(RoomMemberPresenceFilter.offline);
  expect(rolePatch.roleFilter).toBe(RoomMemberRoleFilter.member);

  const searchPatch = roomMemberSearchQueryChanged({ searchQuery: 'carol', ..._all });
  expect(searchPatch.searchQuery).toBe('carol');
  expect(searchPatch.presenceFilter).toBe(RoomMemberPresenceFilter.all);
  expect(searchPatch.roleFilter).toBe(RoomMemberRoleFilter.all);
});

test('visibleRoomMembers sorts by live presence before online state', () => {
  const members = [
    _member('offline_owner', { role: 'member', isOnline: false }),
    _member('online_member', { isOnline: true }),
    _member('live_member', { isOnline: false }),
  ];

  const visible = visibleRoomMembers({
    members,
    live: _live(['live_member']),
    ..._all,
    query: '',
    ownerUserId: 'offline_owner',
  });

  expect(ids(visible)).toEqual(['live_member', 'online_member', 'offline_owner']);
});

test('visibleRoomMembers sorts members with equal presence by room role', () => {
  const members = [
    _member('member'),
    _member('admin', { role: 'admin' }),
    _member('owner'),
    _member('superuser', { isSuperuser: true }),
  ];

  const visible = visibleRoomMembers({ members, live: _live(), ..._all, query: '', ownerUserId: 'owner' });

  expect(ids(visible)).toEqual(['superuser', 'owner', 'admin', 'member']);
});

test('ordered visible members keep their prior order after role patches', () => {
  const members = [
    _member('admin_a', { role: 'admin', displayName: 'Admin A' }),
    _member('member_b', { displayName: 'Member B' }),
    _member('target_c', { displayName: 'Target C' }),
  ];

  const initialOrder = ids(visibleRoomMembers({ members, live: _live(), ..._all, query: '' }));
  expect(initialOrder).toEqual(['admin_a', 'member_b', 'target_c']);

  const updatedMembers = [members[0]!, members[1]!, roomMemberWithRole(members[2]!, 'admin')];
  expect(ids(visibleRoomMembers({ members: updatedMembers, live: _live(), ..._all, query: '' }))).toEqual([
    'admin_a',
    'target_c',
    'member_b',
  ]);

  const filtered = filteredRoomMembers({ members: updatedMembers, live: _live(), ..._all, query: '' });
  expect(ids(orderRoomMembersByUserIds({ members: filtered, orderedUserIds: initialOrder }))).toEqual([
    'admin_a',
    'member_b',
    'target_c',
  ]);
});

test('roomMemberPresenceGroups buckets members in display order', () => {
  const members = [
    _member('offline', { isOnline: false }),
    _member('online', { isOnline: true }),
    _member('live', { isOnline: false }),
    _member('also_live', { isOnline: true }),
  ];

  const groups = roomMemberPresenceGroups({ members, live: _live(['live', 'also_live']) });

  expect(groups.map((group) => group.presence)).toEqual([
    RoomMemberPresence.live,
    RoomMemberPresence.online,
    RoomMemberPresence.offline,
  ]);
  expect(groups.map((group) => group.count)).toEqual([2, 1, 1]);
  expect(ids(groups[0]!.members)).toEqual(['live', 'also_live']);
  expect(ids(groups[1]!.members)).toEqual(['online']);
  expect(ids(groups[2]!.members)).toEqual(['offline']);
});

test('current user presence fallback keeps the signed-in member online', () => {
  const self = _member('self', { isOnline: false });
  const other = _member('other', { isOnline: false });

  const members = roomMembersWithCurrentUserPresence([self, other], { currentUserId: 'self' });

  expect(members[0]!.user.id).toBe('self');
  expect(members[0]!.isOnline).toBe(true);
  expect(members[0]!.user.isOnline).toBe(true);
  expect(members[1]).toBe(other);
  expect(roomMemberPresence(members[0]!, { live: _live() })).toBe(RoomMemberPresence.online);

  const online = visibleRoomMembers({
    members,
    live: _live(),
    presenceFilter: RoomMemberPresenceFilter.online,
    roleFilter: RoomMemberRoleFilter.all,
    query: '',
  });
  expect(ids(online)).toEqual(['self']);
});

test('visibleRoomMembers filters presence and role consistently', () => {
  const members = [
    _member('live_admin', { role: 'admin', isOnline: false }),
    _member('online_member', { isOnline: true }),
    _member('offline_member', { isOnline: false }),
    _member('owner', { isOnline: false }),
  ];
  const visible = (presenceFilter: RoomMemberPresenceFilter, roleFilter: RoomMemberRoleFilter) =>
    ids(
      visibleRoomMembers({
        members,
        live: _live(['live_admin']),
        presenceFilter,
        roleFilter,
        query: '',
        ownerUserId: 'owner',
      }),
    );

  expect(visible(RoomMemberPresenceFilter.live, RoomMemberRoleFilter.all)).toEqual(['live_admin']);
  expect(visible(RoomMemberPresenceFilter.online, RoomMemberRoleFilter.all)).toEqual(['live_admin', 'online_member']);
  expect(visible(RoomMemberPresenceFilter.offline, RoomMemberRoleFilter.member)).toEqual(['offline_member']);
  expect(visible(RoomMemberPresenceFilter.all, RoomMemberRoleFilter.admin)).toEqual(['live_admin', 'owner']);
  expect(visible(RoomMemberPresenceFilter.all, RoomMemberRoleFilter.creator)).toEqual(['owner']);
});

test('search rank prefers uid then display name then remark', () => {
  const members = [
    _member('remark_match', { remarkName: 'alpha note' }),
    _member('name_match', { displayName: 'Alpha Name' }),
    _member('uid_match', { uid: 'alpha-001' }),
    _member('miss'),
  ];

  const visible = visibleRoomMembers({ members, live: _live(), ..._all, query: 'alpha' });

  expect(ids(visible)).toEqual(['uid_match', 'name_match', 'remark_match']);
});

test('member search ignores room display names from any context', () => {
  const members = [
    _member('shared_context', { displayName: 'Shared Context', userRoomDisplayName: 'Alpha From Another Room' }),
    _member('current_room_name', { displayName: 'Current User', roomDisplayName: 'Alpha Current' }),
    _member('profile_name', { displayName: 'Alpha Profile' }),
  ];
  const search = (query: string) => visibleRoomMembers({ members, live: _live(), ..._all, query });

  expect(search('another room')).toEqual([]);
  expect(search('alpha current')).toEqual([]);
  expect(ids(search('alpha profile'))).toEqual(['profile_name']);
});

test('replaceRoomMember swaps the member with the same user id', () => {
  const members = [_member('a'), _member('b'), _member('c')];
  const updated = _member('b', { role: 'admin' });

  const next = replaceRoomMember(members, updated);

  expect(ids(next)).toEqual(['a', 'b', 'c']);
  expect(next[1]!.role).toBe('admin');
  expect(next[0]).toBe(members[0]);
  expect(next[2]).toBe(members[2]);
});

test('roomMemberWithRole updates member and embedded user role', () => {
  const member = _member('target', { roomDisplayName: 'Room Target', remarkName: 'Pinned', isOnline: false });

  const updated = roomMemberWithRole(member, 'admin');

  expect(updated.role).toBe('admin');
  expect(updated.user.roomRole).toBe('admin');
  expect(updated.roomDisplayName).toBe('Room Target');
  expect(updated.remarkName).toBe('Pinned');
  expect(updated.isOnline).toBe(false);
});

test('room member action helpers guard busy members', () => {
  const member = _member('busy');
  const busyMemberIds = new Set(['busy']);

  expect(canStartRoomMemberAction({ userId: 'busy', busyMemberIds })).toBe(false);
  expect(canStartRoomMemberAction({ userId: 'idle', busyMemberIds })).toBe(true);
  expect(roomMemberActionBusy({ member, busyMemberIds })).toBe(true);
});

test('room member action helpers provide role and transfer copy', () => {
  const member = _member('target', { roomDisplayName: 'Room Target' });

  expect(roomMemberRoleUpdateNotice('admin')).toBe('已授予管理员身份');
  expect(roomMemberRoleUpdateNotice('member')).toBe('已移除管理员身份');
  expect(roomMemberRoleUpdateConfirmTitle('admin')).toBe('设为管理员');
  expect(roomMemberRoleUpdateConfirmTitle('member')).toBe('移除管理员');
  expect(roomMemberRoleUpdateConfirmBody(member, 'admin')).toBe('确定要将 Room Target 设为管理员吗？');
  expect(roomMemberRoleUpdateConfirmBody(member, 'member')).toBe('确定要移除 Room Target 的管理员身份吗？');
  expect(roomMemberRoleUpdateConfirmLabel('admin')).toBe('设为管理员');
  expect(roomMemberRoleUpdateConfirmLabel('member')).toBe('移除');
  expect(transferCreatorDialogTitle()).toBe('转让创建者');
  expect(transferCreatorConfirmBody(member, { currentUserIsCreator: true })).toBe(
    '创建者身份会转让给 Room Target，你将成为管理员。',
  );
  expect(transferCreatorConfirmBody(member, { currentUserIsCreator: false })).toBe(
    '创建者身份会转让给 Room Target，原创建者将成为管理员。',
  );
  expect(transferCreatorConfirmLabel()).toBe('转让');
  expect(transferCreatorSuccessNotice()).toBe('创建者已转让');
  expect(removeRoomMemberConfirmTitle()).toBe('踢出此用户');
  expect(removeRoomMemberConfirmBody(member)).toBe('确定要将 Room Target 从房间中移除吗？');
  expect(removeRoomMemberConfirmLabel()).toBe('踢出');
  expect(removeRoomMemberSuccessNotice(member)).toBe('已踢出 Room Target');
});

test('roomMemberPermissionState gates creator-only member role actions', () => {
  const currentUser = _currentUser('current');

  const member = roomMemberPermissionState({
    member: _member('member'),
    currentUser,
    canEditCreatorOnly: true,
    canManageMembers: true,
    ownerUserId: 'owner',
  });
  expect(member.canSetAdmin).toBe(true);
  expect(member.canUnsetAdmin).toBe(false);
  expect(member.canTransferCreator).toBe(true);
  expect(member.canRemoveMember).toBe(true);
  expect(member.canEditRoomDisplayName).toBe(true);
  expect(member.adminActionLabel).toBe('设为管理员');

  const admin = roomMemberPermissionState({
    member: _member('admin', { role: 'admin' }),
    currentUser,
    canEditCreatorOnly: true,
    canManageMembers: true,
    ownerUserId: 'owner',
  });
  expect(admin.canSetAdmin).toBe(false);
  expect(admin.canUnsetAdmin).toBe(true);
  expect(admin.canRemoveMember).toBe(true);
  expect(admin.canEditRoomDisplayName).toBe(true);
  expect(admin.adminActionLabel).toBe('移除管理员');

  const adminManagedByAdmin = roomMemberPermissionState({
    member: _member('admin_peer', { role: 'admin' }),
    currentUser,
    canEditCreatorOnly: false,
    canManageMembers: true,
    ownerUserId: 'owner',
  });
  expect(adminManagedByAdmin.canRoleEdit).toBe(false);
  expect(adminManagedByAdmin.canRemoveMember).toBe(false);
  expect(adminManagedByAdmin.canEditRoomDisplayName).toBe(false);

  const managedByAdmin = roomMemberPermissionState({
    member: _member('managed'),
    currentUser,
    canEditCreatorOnly: false,
    canManageMembers: true,
    ownerUserId: 'owner',
  });
  expect(managedByAdmin.canRoleEdit).toBe(false);
  expect(managedByAdmin.canRemoveMember).toBe(true);
  expect(managedByAdmin.canEditRoomDisplayName).toBe(true);
});

test('roomMemberPermissionState blocks self owner and superuser edits', () => {
  const currentUser = _currentUser('current');
  const states = [
    roomMemberPermissionState({ member: _member('current'), currentUser, canEditCreatorOnly: true, canManageMembers: true }),
    roomMemberPermissionState({
      member: _member('owner'),
      currentUser,
      canEditCreatorOnly: true,
      canManageMembers: true,
      ownerUserId: 'owner',
    }),
    roomMemberPermissionState({
      member: _member('superuser', { isSuperuser: true }),
      currentUser,
      canEditCreatorOnly: true,
      canManageMembers: true,
    }),
    roomMemberPermissionState({ member: _member('member'), currentUser, canEditCreatorOnly: false }),
  ];
  for (const state of states) {
    expect(state.canRoleEdit).toBe(false);
    expect(state.canRemoveMember).toBe(false);
    expect(state.canEditRoomDisplayName).toBe(false);
  }
});

test('roomMemberPermissionState lets superusers remove creators', () => {
  const currentUser = _currentUser('current', { isSuperuser: true });

  const creator = roomMemberPermissionState({
    member: _member('owner', { role: 'owner' }),
    currentUser,
    canEditCreatorOnly: true,
    canManageMembers: true,
    ownerUserId: 'owner',
  });
  expect(creator.canRoleEdit).toBe(false);
  expect(creator.canSetAdmin).toBe(false);
  expect(creator.canUnsetAdmin).toBe(false);
  expect(creator.canTransferCreator).toBe(false);
  expect(creator.canRemoveMember).toBe(true);
  expect(creator.canEditRoomDisplayName).toBe(true);

  const superuserCreator = roomMemberPermissionState({
    member: _member('owner', { role: 'owner', isSuperuser: true }),
    currentUser,
    canEditCreatorOnly: true,
    canManageMembers: true,
    ownerUserId: 'owner',
  });
  expect(superuserCreator.canRemoveMember).toBe(false);
  expect(superuserCreator.canEditRoomDisplayName).toBe(false);
});

test('profile management shortcut follows member management permissions', () => {
  const currentUser = _currentUser('current');
  const superuser = _currentUser('superuser', { isSuperuser: true });
  const ownerRoom = _room('room_1', { role: 'owner' });
  const adminRoom = _room('room_1', { role: 'admin' });

  expect(
    canOpenRoomMemberManagementFromProfile({
      room: ownerRoom,
      currentUser,
      target: _member('admin', { role: 'admin', uid: 'uid-admin' }).user,
    }),
  ).toBe(true);
  expect(
    canOpenRoomMemberManagementFromProfile({
      room: adminRoom,
      currentUser,
      target: _member('member', { uid: 'uid-member' }).user,
    }),
  ).toBe(true);
  expect(
    canOpenRoomMemberManagementFromProfile({
      room: adminRoom,
      currentUser,
      target: _member('admin', { role: 'admin', uid: 'uid-admin' }).user,
    }),
  ).toBe(false);
  expect(
    canOpenRoomMemberManagementFromProfile({
      room: ownerRoom,
      currentUser,
      target: _member('current', { uid: 'uid-current' }).user,
    }),
  ).toBe(false);
  expect(
    canOpenRoomMemberManagementFromProfile({ room: ownerRoom, currentUser, target: _member('member').user }),
  ).toBe(false);
  expect(
    canOpenRoomMemberManagementFromProfile({
      room: adminRoom,
      currentUser: superuser,
      target: _member('owner', { role: 'owner', uid: 'uid-owner' }).user,
    }),
  ).toBe(true);
});

test('room member management action patches busy state', () => {
  const room = _room('room_1');
  const members = [_member('a'), _member('b')];

  const patch = roomMemberManagementActionStarted({
    room,
    members,
    changed: true,
    userId: 'b',
    busyMemberIds: new Set(['other']),
  });

  expect(patch.room).toBe(room);
  expect(patch.members).toEqual(members);
  expect(patch.busyMemberIds).toEqual(new Set(['other', 'b']));
  expect(patch.changed).toBe(true);
  expect(patch.error).toBeNull();
  expect(patch.notice).toBeNull();
});

test('room member load patches loading members and error state', () => {
  const currentMembers = [_member('a')];

  const started = roomMembersLoadStarted({ members: currentMembers });
  expect(started.members).toEqual(currentMembers);
  expect(started.loading).toBe(true);
  expect(started.error).toBeNull();

  const loadedMembers = [_member('b'), _member('c')];
  const succeeded = roomMembersLoadSucceeded({ members: loadedMembers });
  expect(succeeded.members).toEqual(loadedMembers);
  expect(succeeded.loading).toBe(false);
  expect(succeeded.error).toBeNull();

  const failed = roomMembersLoadFailed({ members: currentMembers, failure: new Error('load failed') });
  expect(failed.members).toEqual(currentMembers);
  expect(failed.loading).toBe(false);
  expect(failed.error).toBe('操作失败，请稍后重试');
});

test('room member role update patches members busy ids and notice', () => {
  const room = _room('room_1');
  const members = [_member('a'), _member('b')];
  const updated = _member('b', { role: 'admin' });

  const succeeded = roomMemberRoleUpdateSucceeded({
    room,
    members,
    updated,
    role: 'admin',
    busyMemberIds: new Set(['b', 'other']),
  });
  expect(succeeded.room).toBe(room);
  expect(ids(succeeded.members)).toEqual(['a', 'b']);
  expect(succeeded.members[1]).toBe(updated);
  expect(succeeded.busyMemberIds).toEqual(new Set(['other']));
  expect(succeeded.changed).toBe(true);
  expect(succeeded.error).toBeNull();
  expect(succeeded.notice).toBe('已授予管理员身份');

  const failed = roomMemberRoleUpdateFailed({
    room,
    members,
    changed: false,
    userId: 'b',
    busyMemberIds: new Set(['b', 'other']),
    failure: new Error('role failed'),
  });
  expect(failed.room).toBe(room);
  expect(ids(failed.members)).toEqual(['a', 'b']);
  expect(failed.busyMemberIds).toEqual(new Set(['other']));
  expect(failed.changed).toBe(false);
  expect(failed.error).toBe('操作失败，请稍后重试');
  expect(failed.notice).toBeNull();
});

test('room member remove patches members busy ids and notice', () => {
  const room = _room('room_1');
  const removed = _member('b', { roomDisplayName: 'Removed Target' });
  const members = [_member('a'), removed, _member('c')];

  const succeeded = roomMemberRemovedSucceeded({ room, members, removed, busyMemberIds: new Set(['b', 'other']) });
  expect(succeeded.room).toBe(room);
  expect(ids(succeeded.members)).toEqual(['a', 'c']);
  expect(succeeded.busyMemberIds).toEqual(new Set(['other']));
  expect(succeeded.changed).toBe(true);
  expect(succeeded.error).toBeNull();
  expect(succeeded.notice).toBe('已踢出 Removed Target');

  const failed = roomMemberRemoveFailed({
    room,
    members,
    changed: true,
    userId: 'b',
    busyMemberIds: new Set(['b', 'other']),
    failure: new Error('remove failed'),
  });
  expect(failed.room).toBe(room);
  expect(failed.members).toEqual(members);
  expect(failed.busyMemberIds).toEqual(new Set(['other']));
  expect(failed.changed).toBe(true);
  expect(failed.error).toBe('操作失败，请稍后重试');
  expect(failed.notice).toBeNull();
});

test('transfer creator patches room busy ids and notice', () => {
  const room = _room('room_1');
  const updatedRoom = _room('room_1', { role: 'admin' });
  const members = [_member('a'), _member('b')];

  const succeeded = transferCreatorSucceeded({ updatedRoom, members, userId: 'b', busyMemberIds: new Set(['b', 'other']) });
  expect(succeeded.room).toBe(updatedRoom);
  expect(succeeded.members).toEqual(members);
  expect(succeeded.busyMemberIds).toEqual(new Set(['other']));
  expect(succeeded.changed).toBe(true);
  expect(succeeded.error).toBeNull();
  expect(succeeded.notice).toBe('创建者已转让');

  const failed = transferCreatorFailed({
    room,
    members,
    changed: true,
    userId: 'b',
    busyMemberIds: new Set(['b', 'other']),
    failure: new Error('transfer failed'),
  });
  expect(failed.room).toBe(room);
  expect(failed.members).toEqual(members);
  expect(failed.busyMemberIds).toEqual(new Set(['other']));
  expect(failed.changed).toBe(true);
  expect(failed.error).toBe('操作失败，请稍后重试');
  expect(failed.notice).toBeNull();
});

function _member(
  id: string,
  opts: {
    role?: string;
    uid?: string | null;
    displayName?: string | null;
    roomDisplayName?: string | null;
    userRoomDisplayName?: string | null;
    remarkName?: string | null;
    isOnline?: boolean;
    isSuperuser?: boolean;
  } = {},
): RoomMember {
  const { role = 'member', isOnline = true, isSuperuser = false } = opts;
  const user = new UserSummary({
    id,
    username: `user_${id}`,
    displayName: opts.displayName ?? `User ${id}`,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
    uid: opts.uid ?? null,
    roomDisplayName: opts.userRoomDisplayName ?? opts.roomDisplayName ?? null,
    roomRole: role,
    isSuperuser,
  });
  return new RoomMember({
    user,
    role,
    joinedAt: _june4,
    roomDisplayName: opts.roomDisplayName ?? null,
    remarkName: opts.remarkName ?? null,
    isOnline,
  });
}

function _live(participantIds: string[] = []): LiveState {
  return new LiveState({
    roomId: 'room_1',
    participantCount: participantIds.length,
    participants: participantIds.map(
      (id) =>
        new LiveParticipant({
          liveSessionId: `live_${id}`,
          user: _user(id),
          joinedAt: _june4,
          micMuted: true,
          headphonesMuted: false,
          voiceBlocked: false,
          cameraOn: false,
          screenSharing: false,
          connectionState: 'connected',
        }),
    ),
    updatedAt: _june4,
  });
}

function _user(id: string): UserSummary {
  return new UserSummary({
    id,
    username: `user_${id}`,
    displayName: `User ${id}`,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
  });
}

function _currentUser(id: string, opts: { isSuperuser?: boolean } = {}): CurrentUser {
  return new CurrentUser({
    id,
    uid: id,
    username: `user_${id}`,
    displayName: `User ${id}`,
    bio: '',
    gender: 'secret',
    email: null,
    emailPublic: false,
    phoneNumber: null,
    phoneNumberPublic: false,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
    isSuperuser: opts.isSuperuser ?? false,
    createdAt: _june4,
  });
}

function _room(id: string, opts: { role?: string } = {}): RoomDetail {
  return new RoomDetail({
    id,
    name: id,
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    memberCount: 2,
    myMembership: new RoomMembership({ joinedAt: _june4, role: opts.role ?? 'member' }),
    live: _live(),
    createdAt: _june4,
    updatedAt: _june4,
  });
}
