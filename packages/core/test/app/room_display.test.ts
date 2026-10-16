import { expect, test } from 'vitest';

import {
  commonRoomAvatarLabel,
  commonRoomDisplayName,
  commonRoomMeta,
  commonRoomsWithLocalRoomNames,
  commonRoomTitle,
  currentUserPresenceLabel,
  normalizeRoomJoinPolicy,
  normalizeRoomNotificationPolicy,
  normalizeRoomVisibility,
  publicRoomJoinActionable,
  publicRoomJoinActionLabel,
  publicRoomJoinRequiresApplication,
  resolvedRoomMemberProfileUser,
  roomAccessState,
  roomCopyFailureMessage,
  roomCopySuccessNotice,
  roomCreatedAtLabel,
  roomDeletionConfirmationSpec,
  roomDescriptionText,
  roomDescriptionValue,
  roomDraftPreview,
  roomDraftPreviewText,
  roomIdentifier,
  roomInfoSavedNotice,
  roomJoinPolicyLabel,
  roomLeaveConfirmationSpec,
  roomManagementAvatarPath,
  roomManagementPermissionState,
  roomManagementUploadedAvatarSelected,
  roomOpenFailureMessage,
  roomOptimisticOpenRefreshFailureNotice,
  roomProfileAvatarPath,
  roomProfileDisplayName,
  roomProfilePresetAvatarSelected,
  roomProfileUploadedAvatarSelected,
  roomRoleLabel,
  roomSidebarLastMessageTime,
  roomSidebarSubtitle,
  roomSidebarTimestamp,
  roomSubtitle,
  roomUseGlobalProfileNotice,
  roomUserInfoCommonRooms,
  roomUserInfoProfile,
  userAvatarLabel,
  userIdentityMeta,
  userPresenceLabel,
  userPrimaryName,
  userRoomsSectionTitle,
  userSignatureText,
  userUidCopyFailureMessage,
  userUidCopySuccessNotice,
  userUidLabel,
  userUsernameLabel,
  visibilityLabel,
} from '../../src/app/room_display';
import {
  CurrentUser,
  LastMessagePreview,
  LiveState,
  PublicRoom,
  RoomCard,
  RoomDetail,
  RoomMemberProfile,
  RoomMembership,
  UserCommonRoom,
  UserSummary,
} from '../../src/protocol/models';

const _june4 = new Date(Date.UTC(2026, 5, 4));

function _preview(opts: { id: string; type?: string; senderDisplayName: string; bodyPreview: string; createdAt?: Date }) {
  return new LastMessagePreview({
    id: opts.id,
    type: opts.type,
    senderDisplayName: opts.senderDisplayName,
    bodyPreview: opts.bodyPreview,
    createdAt: opts.createdAt ?? _june4,
  });
}

test('roomSubtitle includes last message preview when present', () => {
  expect(roomSubtitle(_roomCard())).toBe('8 名成员 · 3 人在线 · 2 人语音');

  expect(
    roomSubtitle(_roomCard({ lastMessage: _preview({ id: 'message_1', senderDisplayName: 'Logan', bodyPreview: 'hello' }) })),
  ).toBe('Logan · hello');
  expect(roomSidebarSubtitle(_roomCard())).toBe('8 名成员 · 2 人语音');
  expect(
    roomSidebarSubtitle(
      _roomCard({ lastMessage: _preview({ id: 'message_1', senderDisplayName: 'Logan', bodyPreview: 'hello' }) }),
    ),
  ).toBe('Logan · hello');
  const system = (id: string, senderDisplayName: string, bodyPreview: string) =>
    roomSidebarSubtitle(_roomCard({ lastMessage: _preview({ id, type: 'system', senderDisplayName, bodyPreview }) }));
  expect(system('message_2', 'Logan', '加入了房间')).toBe('[系统] Logan 加入了房间');
  expect(system('message_3', '丁', '房间名称修改为123345')).toBe('[系统] 房间名称 被 丁 修改为 123345');
  expect(system('message_4', '', '房间简介 被 丁 修改为 新简介')).toBe('[系统] 房间简介 被 丁 修改为 新简介');
  expect(system('message_5', '丁', '房间可见性修改为私密')).toBe('[系统] 房间可见性 被 丁 修改为 私密');
  expect(system('message_6', '丁', '房间加入方式修改为关闭')).toBe('[系统] 房间加入方式 被 丁 修改为 关闭');
});

test('room sidebar latest message time follows compact chat rules', () => {
  const now = new Date(2026, 5, 12, 10);
  expect(roomSidebarTimestamp(new Date(2026, 5, 12, 7, 5), { now })).toBe('07:05');
  expect(roomSidebarTimestamp(new Date(2026, 5, 11, 7, 5), { now })).toBe('昨天 07:05');
  expect(roomSidebarTimestamp(new Date(2026, 5, 10, 7, 5), { now })).toBe('前天 07:05');
  expect(roomSidebarTimestamp(new Date(2026, 5, 9, 7, 5), { now })).toBe('星期二');
  expect(roomSidebarTimestamp(new Date(2026, 4, 31, 7, 5), { now })).toBe('2026/05/31');
  expect(roomSidebarTimestamp(new Date(2025, 11, 31, 7, 5), { now })).toBe('2025/12/31');
  expect(roomSidebarLastMessageTime(_roomCard(), { now })).toBe('');
  expect(
    roomSidebarLastMessageTime(
      _roomCard({
        lastMessage: _preview({
          id: 'message_1',
          senderDisplayName: 'Logan',
          bodyPreview: 'hello',
          createdAt: new Date(2026, 5, 12, 7, 5),
        }),
      }),
      { now },
    ),
  ).toBe('07:05');
});

test('room draft preview normalizes visible draft text', () => {
  expect(roomDraftPreview(null)).toBeNull();
  expect(roomDraftPreview('   ')).toBeNull();
  expect(roomDraftPreview(' hello\n  draft\tworld ')).toBe('hello draft world');
  expect(
    roomDraftPreviewText({
      text: 'typed text',
      attachmentFilename: ' report final.pdf ',
      attachmentMimeType: 'application/pdf',
    }),
  ).toBe('[文件] report final.pdf');
  expect(
    roomDraftPreviewText({ text: null, attachmentFilename: 'cat picture.png', attachmentMimeType: 'image/png' }),
  ).toBe('[图片] cat picture.png');
  expect(
    roomDraftPreviewText({ text: 'reply text', attachmentFilename: null, attachmentMimeType: null, hasQuote: true }),
  ).toBe('[引用] reply text');
  expect(roomDraftPreviewText({ text: '', attachmentFilename: null, attachmentMimeType: null, hasQuote: true })).toBe(
    '[引用]',
  );
});

test('room identity display helpers provide stable fallbacks', () => {
  expect(roomIdentifier(_roomDetail({ rid: 'R001' }))).toBe('R001');
  expect(roomIdentifier(_roomDetail())).toBe('room_1');
  expect(roomCreatedAtLabel(new Date(2026, 5, 4, 9, 5))).toBe('2026/06/04 09:05');
  expect(roomDescriptionValue(_roomDetail({ description: '  Launch room  ' }))).toBe('Launch room');
  expect(roomDescriptionValue(_roomDetail({ description: ' ' }))).toBeNull();
  expect(roomDescriptionText(_roomDetail({ description: '  Launch room  ' }))).toBe('Launch room');
  expect(roomDescriptionText(_roomDetail({ description: ' ' }))).toBe('暂无介绍');
});

test('room leave confirmation spec separates normal live and delete exits', () => {
  const normal = roomLeaveConfirmationSpec({ room: _roomDetail({ memberCount: 3 }), isInLive: false });
  expect(normal.confirmDeleteIfEmpty).toBe(false);
  expect(normal.requiresStrongConfirmation).toBe(false);
  expect(normal.title).toBe('退出房间');
  expect(normal.body).toContain('房间成员中移除');
  expect(normal.confirmLabel).toBe('退出');
  expect(normal.expectedText).toBeNull();

  const live = roomLeaveConfirmationSpec({ room: _roomDetail({ memberCount: 3 }), isInLive: true });
  expect(live.body).toContain('Live Channel');

  const finalCreator = roomLeaveConfirmationSpec({
    room: _roomDetail({ role: 'creator', memberCount: 1 }),
    isInLive: true,
  });
  expect(finalCreator.confirmDeleteIfEmpty).toBe(true);
  expect(finalCreator.requiresStrongConfirmation).toBe(true);
  expect(finalCreator.title).toBe('退出并删除房间');
  expect(finalCreator.confirmLabel).toBe('退出并删除');
  expect(finalCreator.expectedText).toBe('Room');
});

test('room operation notices and delete confirmation stay outside UI', () => {
  expect(roomCopySuccessNotice('RID')).toBe('RID 已复制');
  expect(roomCopyFailureMessage('denied')).toBe('无法复制');
  expect(userUidCopySuccessNotice()).toBe('UID 已复制');
  expect(userUidCopyFailureMessage('denied')).toBe('无法复制 UID');
  expect(roomOpenFailureMessage('missing')).toBe('无法打开房间');
  expect(roomOptimisticOpenRefreshFailureNotice()).toBe('房间刷新失败，已先打开当前房间');
  expect(roomUseGlobalProfileNotice()).toBe('保存后将使用全局默认用户名和默认头像');
  expect(roomInfoSavedNotice()).toBe('房间信息已保存');

  const deletion = roomDeletionConfirmationSpec(_roomDetail());
  expect(deletion.title).toBe('删除房间');
  expect(deletion.body).toBe('将清空房间所有数据。这个动作不可恢复，请输入房间名确认。');
  expect(deletion.confirmLabel).toBe('删除房间');
  expect(deletion.expectedText).toBe('Room');
});

test('userPrimaryName prefers room display name then global display name', () => {
  expect(userPrimaryName(_user({ roomDisplayName: ' Room Logan ', displayName: '' }))).toBe('Room Logan');
  expect(userPrimaryName(_user({ displayName: ' Global Logan ' }))).toBe('Global Logan');
  expect(userPrimaryName(_user({ displayName: '' }))).toBe('logan');
});

test('userAvatarLabel ignores the room display name', () => {
  expect(userAvatarLabel(_user({ username: 'testxxxx', displayName: 'Test Example', roomDisplayName: 'J' }))).toBe(
    'Test Example',
  );
  expect(userAvatarLabel(_user({ username: 'testxxxx', displayName: '', roomDisplayName: 'J' }))).toBe('testxxxx');
});

test('user identity labels prefer uid and include username handle', () => {
  expect(userUidLabel(_user({ uid: '1001' }))).toBe('1001');
  expect(userUidLabel(_user({ id: 'user_fallback', uid: null }))).toBe('user_fallback');
  expect(userUsernameLabel(_user({ username: 'logan' }))).toBe('@logan');
  expect(userIdentityMeta(_user({ id: 'user_fallback' }))).toBe('user_fallback · @logan');
  expect(userIdentityMeta(_user({ uid: '1001' }))).toBe('1001 · @logan');
  expect(userSignatureText(_user({ bio: '  Hello  ' }))).toBe('Hello');
  expect(userSignatureText(_user({ bio: '   ' }))).toBeNull();
  expect(userPresenceLabel(_user({ isOnline: true }))).toBe('在线');
  expect(userPresenceLabel(_user({ isOnline: false }))).toBe('离线');
  expect(userPresenceLabel(_user())).toBeNull();
  expect(currentUserPresenceLabel(_currentUser({ status: 'active' }), { inLive: false })).toBe('在线');
  expect(currentUserPresenceLabel(_currentUser({ status: 'offline' }), { inLive: false })).toBe('离线');
  expect(currentUserPresenceLabel(_currentUser({ status: 'active' }), { inLive: true })).toBe('语音');
  expect(currentUserPresenceLabel(_currentUser({ status: 'active' }), { inLive: true, reconnecting: true })).toBe(
    '重连中',
  );
});

test('roomRoleLabel handles pending superuser owner admin and member roles', () => {
  expect(roomRoleLabel(_user({ roomRole: 'pending' }))).toBe('待审批');
  expect(roomRoleLabel(_user({ isSuperuser: true }))).toBe('超级用户');
  expect(roomRoleLabel(_user({ id: 'owner' }), { ownerUserId: 'owner' })).toBe('创建者');
  expect(roomRoleLabel(_user({ roomRole: 'administrator' }))).toBe('管理员');
  expect(roomRoleLabel(_user({ roomRole: 'member' }))).toBe('成员');
});

test('common room labels include rid visibility display name and role', () => {
  const room = new UserCommonRoom({
    id: 'room_1',
    rid: '1001',
    name: 'General',
    remarkName: 'Ops',
    visibility: 'public',
    roomDisplayName: 'Room Logan',
    roomRole: 'admin',
  });

  expect(commonRoomTitle(room)).toBe('Ops · 1001');
  expect(commonRoomAvatarLabel(room)).toBe('General');
  expect(visibilityLabel(room.visibility)).toBe('公开');
  expect(commonRoomMeta(room)).toBe('Room Logan · 管理员');
  expect(commonRoomTitle(new UserCommonRoom({ id: 'room_2', rid: '', name: 'Private' }))).toBe('Private');
});

test('common room names prefer the viewer local room remark', () => {
  const commonRooms = [
    new UserCommonRoom({
      id: 'room_1',
      rid: 'REMOTE',
      name: 'Official Old',
      remarkName: 'Other User Remark',
      visibility: 'public',
      avatarUrl: '/remote.png',
      defaultAvatarKey: 'remote-1',
      roomDisplayName: 'Room Logan',
      roomRole: 'admin',
    }),
    new UserCommonRoom({ id: 'room_2', rid: 'R002', name: 'Unmatched', remarkName: 'Keep Remote' }),
  ];
  const localRoom = (remarkName: string | null, avatarUrl: string | null) =>
    new RoomCard({
      id: 'room_1',
      rid: 'LOCAL',
      name: 'Official Local',
      visibility: 'private',
      remarkName,
      avatarUrl,
      defaultAvatarKey: 'local-1',
      memberCount: 3,
      onlineMemberCount: 1,
      liveParticipantCount: 0,
      liveAvatarPreview: [],
      lastMessage: null,
      unreadCount: 0,
      updatedAt: new Date(Date.UTC(2026, 6, 9)),
    });

  const resolved = commonRoomsWithLocalRoomNames({ commonRooms, rooms: [localRoom('Viewer Remark', '/local.png')] });

  const first = resolved[0]!;
  expect(first.rid).toBe('LOCAL');
  expect(first.name).toBe('Official Local');
  expect(first.visibility).toBe('private');
  expect(first.remarkName).toBe('Viewer Remark');
  expect(first.avatarUrl).toBe('/local.png');
  expect(first.defaultAvatarKey).toBe('local-1');
  expect(first.roomDisplayName).toBe('Room Logan');
  expect(first.roomRole).toBe('admin');
  expect(commonRoomDisplayName(first)).toBe('Viewer Remark');
  expect(commonRoomDisplayName(resolved[resolved.length - 1]!)).toBe('Keep Remote');

  const noRemark = commonRoomsWithLocalRoomNames({ commonRooms, rooms: [localRoom(null, null)] });

  expect(commonRoomDisplayName(noRemark[0]!)).toBe('Official Local');
});

test('room user info profile merges owner and current user fields', () => {
  const creator = _user({ id: 'creator', username: 'creator', displayName: 'Creator', uid: 'C001' });
  const creatorProfile = roomUserInfoProfile({
    user: _user({ id: 'creator', username: 'creator', displayName: '', uid: '' }),
    room: _roomDetail({ createdBy: creator }),
    currentUser: _currentUser(),
  });

  expect(creatorProfile.displayName).toBe('Creator');
  expect(creatorProfile.uid).toBe('C001');
  expect(creatorProfile.roomRole).toBe('owner');

  const currentUserProfile = roomUserInfoProfile({
    user: _user({ id: 'user_1', displayName: '', roomRole: null }),
    room: _roomDetail({ role: 'admin' }),
    currentUser: _currentUser(),
  });

  expect(currentUserProfile.displayName).toBe('Logan');
  expect(currentUserProfile.roomRole).toBe('admin');
});

test('former room member profile keeps current default account fields', () => {
  const currentDefault = new UserSummary({
    id: 'former',
    username: 'current_name',
    displayName: '当前默认名',
    avatarUrl: null,
    defaultAvatarKey: 'green-2',
    uid: '10002001',
    roomRole: 'left',
  });
  const historicalSnapshot = new UserSummary({
    id: 'former',
    username: 'old_name',
    displayName: '发送时默认名',
    avatarUrl: '/old-avatar.png',
    defaultAvatarKey: 'blue-1',
    uid: '10002001',
    roomDisplayName: '发送时房间名',
    roomRole: 'member',
  });

  const resolved = resolvedRoomMemberProfileUser({
    profile: new RoomMemberProfile({ user: currentDefault, role: 'left', joinedAt: new Date(0) }),
    fallback: historicalSnapshot,
  });

  expect(resolved.username).toBe('current_name');
  expect(resolved.displayName).toBe('当前默认名');
  expect(resolved.avatarUrl).toBeNull();
  expect(resolved.defaultAvatarKey).toBe('green-2');
  expect(resolved.roomDisplayName).toBeNull();
  expect(resolved.roomRole).toBe('left');
});

test('current room member profile can still fill omitted snapshot fields', () => {
  const resolved = resolvedRoomMemberProfileUser({
    profile: new RoomMemberProfile({
      user: _user({ id: 'member', bio: null, roomRole: 'member' }),
      role: 'member',
      joinedAt: new Date(Date.UTC(2026, 6, 20)),
    }),
    fallback: _user({ id: 'member', bio: '历史签名', roomDisplayName: '房间名', roomRole: 'member' }),
  });

  expect(resolved.bio).toBe('历史签名');
  expect(resolved.roomDisplayName).toBe('房间名');
});

test('room user info common rooms handles selected room visibility', () => {
  const existing = new UserCommonRoom({ id: 'room_2', rid: 'R002', name: 'Other', visibility: 'private' });
  const user = _user({
    id: 'other',
    commonRooms: [new UserCommonRoom({ id: 'room_1', rid: 'old', name: 'Duplicate' }), existing],
  });
  const selectedRoom = _roomDetail({ rid: 'R001', visibility: 'public' });

  expect(
    roomUserInfoCommonRooms({
      user,
      selectedRoom,
      currentUser: _currentUser(),
      includeSelectedRoom: false,
    }).map((room) => room.id),
  ).toEqual(['room_1', 'room_2']);

  const withSelected = roomUserInfoCommonRooms({
    user: user.copyWith({ roomDisplayName: 'Room Other', roomRole: 'member' }),
    selectedRoom,
    currentUser: _currentUser(),
    includeSelectedRoom: true,
  });

  expect(withSelected.map((room) => room.id)).toEqual(['room_1', 'room_2']);
  expect(withSelected[0]!.rid).toBe('R001');
  expect(withSelected[0]!.visibility).toBe('public');
  expect(withSelected[0]!.roomDisplayName).toBe('Room Other');
  expect(withSelected[0]!.roomRole).toBe('member');

  expect(
    roomUserInfoCommonRooms({
      user: _user({ id: 'user_1' }),
      selectedRoom,
      currentUser: _currentUser(),
      includeSelectedRoom: true,
    }).map((room) => room.id),
  ).toEqual(['room_1']);
  expect(
    roomUserInfoCommonRooms({
      user: _user({ id: 'super', isSuperuser: true }),
      selectedRoom,
      currentUser: _currentUser(),
      includeSelectedRoom: true,
    }),
  ).toEqual([]);
  expect(
    roomUserInfoCommonRooms({
      user: _user({ id: 'other', commonRooms: [] }),
      selectedRoom,
      currentUser: _currentUser({ isSuperuser: true }),
      includeSelectedRoom: false,
    }).map((room) => room.id),
  ).toEqual([]);
  expect(
    roomUserInfoCommonRooms({
      user: _user({ id: 'other', commonRooms: [existing] }),
      selectedRoom,
      currentUser: _currentUser({ isSuperuser: true }),
      includeSelectedRoom: false,
    }).map((room) => room.id),
  ).toEqual(['room_2']);
  expect(userRoomsSectionTitle({ user: _user({ id: 'super', isSuperuser: true }), currentUser: _currentUser() })).toBeNull();
  expect(userRoomsSectionTitle({ user: _user({ id: 'user_1' }), currentUser: _currentUser() })).toBe('所有房间');
  expect(userRoomsSectionTitle({ user, currentUser: _currentUser() })).toBe('共同房间');
});

test('room setting values are normalized for server payloads', () => {
  expect(normalizeRoomNotificationPolicy('mention_only')).toBe('silent');
  expect(normalizeRoomNotificationPolicy('dnd')).toBe('silent');
  expect(normalizeRoomNotificationPolicy('blocked')).toBe('blocked');
  expect(normalizeRoomNotificationPolicy('unknown')).toBe('all');

  expect(normalizeRoomVisibility('private')).toBe('private');
  expect(normalizeRoomVisibility('PUBLIC')).toBe('public');

  expect(normalizeRoomJoinPolicy('allow_anyone')).toBe('open');
  expect(normalizeRoomJoinPolicy('deny_all')).toBe('closed');
  expect(normalizeRoomJoinPolicy('unknown')).toBe('approval_required');
  expect(roomJoinPolicyLabel('open')).toBe('开放');
  expect(roomJoinPolicyLabel('approval_required')).toBe('需审批');
  expect(roomJoinPolicyLabel('closed')).toBe('关闭');
  expect(roomJoinPolicyLabel('unknown')).toBe('需审批');
});

test('public room join action helpers reflect state', () => {
  expect(publicRoomJoinActionLabel(_publicRoom({ joined: true }), { pending: false })).toBe('进入');
  expect(publicRoomJoinActionLabel(_publicRoom(), { pending: true })).toBe('待审批');
  expect(publicRoomJoinActionLabel(_publicRoom({ joinPolicy: 'approval_required' }), { pending: false })).toBe('加入');
  expect(publicRoomJoinActionLabel(_publicRoom({ joinPolicy: 'closed' }), { pending: false })).toBe('不可加入');
  expect(publicRoomJoinActionLabel(_publicRoom(), { pending: false })).toBe('加入');
  expect(publicRoomJoinActionable(_publicRoom(), { pending: true })).toBe(false);
  expect(publicRoomJoinActionable(_publicRoom({ joinPolicy: 'closed' }), { pending: false })).toBe(false);
  expect(publicRoomJoinRequiresApplication(_publicRoom({ joinPolicy: 'approval_required' }))).toBe(true);
  expect(publicRoomJoinActionable(_publicRoom({ joined: true }), { pending: true })).toBe(true);
});

test('roomManagementPermissionState reuses room and user privileges', () => {
  let access = roomAccessState({ room: _roomDetail({ role: 'admin' }), currentUser: _currentUser() });
  expect(access.canManageRoom).toBe(true);
  expect(access.canReviewJoinRequests).toBe(true);
  expect(access.showJoinRequestBadge(true)).toBe(true);
  expect(access.showJoinRequestBadge(false)).toBe(false);

  access = roomAccessState({ room: _roomDetail({ role: 'member' }), currentUser: _currentUser() });
  expect(access.canManageRoom).toBe(false);
  expect(access.canReviewJoinRequests).toBe(false);
  expect(access.showJoinRequestBadge(true)).toBe(false);

  access = roomAccessState({ room: _roomDetail({ role: 'member' }), currentUser: _currentUser({ isSuperuser: true }) });
  expect(access.canManageRoom).toBe(true);
  expect(access.canReviewJoinRequests).toBe(true);

  let permission = roomManagementPermissionState({
    room: _roomDetail({ role: 'admin', canDeleteRoom: false }),
    currentUser: _currentUser(),
  });
  expect(permission.canEditCreatorOnly).toBe(false);
  expect(permission.canDeleteRoom).toBe(false);

  permission = roomManagementPermissionState({ room: _roomDetail({ role: 'creator' }), currentUser: _currentUser() });
  expect(permission.canEditCreatorOnly).toBe(true);
  expect(permission.canDeleteRoom).toBe(true);

  permission = roomManagementPermissionState({
    room: _roomDetail({ role: 'member', canDeleteRoom: false }),
    currentUser: _currentUser({ isSuperuser: true }),
  });
  expect(permission.canEditCreatorOnly).toBe(true);
  expect(permission.canDeleteRoom).toBe(true);
});

test('room profile avatar helpers preserve global preset and upload states', () => {
  expect(
    roomProfileAvatarPath({
      usingGlobalProfile: true,
      currentUserAvatarUrl: '/global.png',
      pendingAvatarUrl: '/pending.png',
      usingPresetAvatar: false,
      personalAvatarUrl: '/personal.png',
    }),
  ).toBe('/global.png');
  expect(
    roomProfileAvatarPath({
      usingGlobalProfile: false,
      currentUserAvatarUrl: '/global.png',
      pendingAvatarUrl: '/pending.png',
      usingPresetAvatar: false,
      personalAvatarUrl: '/personal.png',
    }),
  ).toBe('/pending.png');
  expect(
    roomProfileAvatarPath({
      usingGlobalProfile: false,
      currentUserAvatarUrl: '/global.png',
      pendingAvatarUrl: null,
      usingPresetAvatar: true,
      personalAvatarUrl: '/personal.png',
    }),
  ).toBeNull();
  expect(
    roomProfileUploadedAvatarSelected({
      usingGlobalProfile: false,
      usingPresetAvatar: false,
      pendingAvatarUrl: null,
      personalAvatarUrl: '/personal.png',
    }),
  ).toBe(true);
  expect(roomProfilePresetAvatarSelected({ usingGlobalProfile: false, usingPresetAvatar: true })).toBe(true);
});

test('room profile and management display helpers trim fallback names', () => {
  expect(
    roomProfileDisplayName({ usingGlobalProfile: false, currentUserDisplayName: 'Global', roomDisplayNameText: ' Room ' }),
  ).toBe('Room');
  expect(
    roomProfileDisplayName({ usingGlobalProfile: false, currentUserDisplayName: 'Global', roomDisplayNameText: ' ' }),
  ).toBe('Global');
  expect(
    roomManagementAvatarPath({ usingPresetAvatar: false, pendingAvatarUrl: '/pending.png', roomAvatarUrl: '/room.png' }),
  ).toBe('/pending.png');
  expect(
    roomManagementAvatarPath({ usingPresetAvatar: true, pendingAvatarUrl: '/pending.png', roomAvatarUrl: '/room.png' }),
  ).toBeNull();
  expect(roomManagementUploadedAvatarSelected({ usingPresetAvatar: false })).toBe(true);
});

function _roomCard(opts: { lastMessage?: LastMessagePreview | null } = {}): RoomCard {
  return new RoomCard({
    id: 'room_1',
    name: 'General',
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    memberCount: 8,
    onlineMemberCount: 3,
    liveParticipantCount: 2,
    liveAvatarPreview: [],
    lastMessage: opts.lastMessage ?? null,
    unreadCount: 0,
    updatedAt: _june4,
  });
}

function _user(
  opts: {
    id?: string;
    username?: string;
    uid?: string | null;
    displayName?: string | null;
    roomDisplayName?: string | null;
    roomRole?: string | null;
    bio?: string | null;
    isSuperuser?: boolean;
    isOnline?: boolean | null;
    commonRooms?: UserCommonRoom[];
  } = {},
): UserSummary {
  const username = opts.username ?? 'logan';
  // Dart default `displayName = 'Logan'`; an explicit null falls back to the username.
  const displayName = 'displayName' in opts ? opts.displayName : 'Logan';
  return new UserSummary({
    id: opts.id ?? 'user_1',
    username,
    displayName: displayName ?? username,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
    uid: opts.uid ?? null,
    bio: opts.bio ?? null,
    roomDisplayName: opts.roomDisplayName ?? null,
    roomRole: opts.roomRole ?? null,
    isSuperuser: opts.isSuperuser ?? false,
    isOnline: opts.isOnline ?? null,
    commonRooms: opts.commonRooms ?? [],
  });
}

function _publicRoom(opts: { joined?: boolean; joinPolicy?: string } = {}): PublicRoom {
  const { joined = false, joinPolicy = 'open' } = opts;
  return new PublicRoom({
    id: 'room_1',
    rid: 'R001',
    name: 'Room',
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    visibility: 'public',
    joinPolicy,
    memberCount: 3,
    liveParticipantCount: 0,
    joined,
    joinState: joined ? 'joined' : 'none',
  });
}

function _roomDetail(
  opts: {
    role?: string;
    canDeleteRoom?: boolean | null;
    createdBy?: UserSummary | null;
    rid?: string;
    visibility?: string;
    description?: string;
    memberCount?: number;
  } = {},
): RoomDetail {
  return new RoomDetail({
    id: 'room_1',
    name: 'Room',
    rid: opts.rid ?? '',
    visibility: opts.visibility ?? 'private',
    avatarUrl: null,
    defaultAvatarKey: 'room-1',
    memberCount: opts.memberCount ?? 1,
    description: opts.description ?? '',
    createdBy: opts.createdBy ?? null,
    myMembership: new RoomMembership({ joinedAt: _june4, role: opts.role ?? 'member' }),
    live: new LiveState({ roomId: 'room_1', participantCount: 0, participants: [], updatedAt: _june4 }),
    createdAt: _june4,
    updatedAt: _june4,
    canDeleteRoom: opts.canDeleteRoom ?? null,
  });
}

function _currentUser(opts: { isSuperuser?: boolean; status?: string | null } = {}): CurrentUser {
  return new CurrentUser({
    id: 'user_1',
    uid: '1001',
    username: 'logan',
    displayName: 'Logan',
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
    status: opts.status ?? null,
  });
}
