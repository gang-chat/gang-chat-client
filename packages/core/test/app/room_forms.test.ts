import { expect, test } from 'vitest';

import {
  canStartCreateRoom,
  canStartRoomDeletion,
  canStartRoomInfoSave,
  canStartRoomLeave,
  canStartRoomProfileSave,
  createRoomDraftFromForm,
  createRoomSubmitFailed,
  createRoomSubmitFinished,
  createRoomSubmitStarted,
  roomAvatarAssetIdForSave,
  roomInfoManagementBusy,
  roomInfoUpdateDraftFromForm,
  RoomManagementSection,
  roomManagementAvatarUploadFailed,
  roomManagementAvatarUploadStarted,
  roomManagementAvatarUploadSucceeded,
  roomManagementDeletionFailed,
  roomManagementDeletionStarted,
  roomManagementInfoDraftInvalid,
  roomManagementInfoSaveFailed,
  roomManagementInfoSaveStarted,
  roomManagementInfoSaveSucceeded,
  roomManagementJoinPolicyChanged,
  roomManagementSectionChanged,
  roomManagementUsePresetAvatar,
  roomManagementVisibilityChanged,
  roomProfileAvatarAssetIdForSave,
  roomProfileAvatarUploadFailed,
  roomProfileAvatarUploadStarted,
  roomProfileAvatarUploadSucceeded,
  roomProfileCopyFailed,
  roomProfileCopySucceeded,
  roomProfileLeaveFailed,
  roomProfileLeaveStarted,
  roomProfileNotificationPolicyChanged,
  roomProfileSaveFailed,
  roomProfileSaveStarted,
  roomProfileUpdateDraftFromForm,
  roomProfileUseGlobalProfile,
  roomProfileUsePresetAvatar,
} from '../../src/app/room_forms';
import { LiveState, RoomDetail, RoomMembership } from '../../src/protocol/models';

const failure = (message: string) => new Error(message);

test('createRoomDraftFromForm validates and trims the room name', () => {
  expect(createRoomDraftFromForm({ name: ' ' }).error).toBe('房间名不能为空');

  const draft = createRoomDraftFromForm({ name: '  General  ' });

  expect(draft.isValid).toBe(true);
  expect(draft.name).toBe('General');
});

test('create room submit patches preserve dialog busy and error state', () => {
  expect(canStartCreateRoom({ busy: false })).toBe(true);
  expect(canStartCreateRoom({ busy: true })).toBe(false);

  const started = createRoomSubmitStarted();
  expect(started.busy).toBe(true);
  expect(started.error).toBeNull();

  const failed = createRoomSubmitFailed({ failure: 'create failed' });
  expect(failed.busy).toBe(true);
  expect(failed.error).toBe('操作失败，请稍后重试');

  const finished = createRoomSubmitFinished({ error: failed.error });
  expect(finished.busy).toBe(false);
  expect(finished.error).toBe('操作失败，请稍后重试');
});

test('roomProfileUpdateDraftFromForm trims room profile fields', () => {
  const draft = roomProfileUpdateDraftFromForm({
    remarkName: '  Ops  ',
    notificationPolicy: 'mention_only',
    usingGlobalProfile: false,
    roomDisplayName: '  Room Logan  ',
    pendingAvatarAssetId: 'asset_1',
    usingProfilePresetAvatar: false,
    defaultAvatarKey: 'green-2',
  });

  expect(draft.remarkName).toBe('Ops');
  expect(draft.notificationPolicy).toBe('silent');
  expect(draft.roomDisplayName).toBe('Room Logan');
  expect(draft.avatarAssetId).toBe('asset_1');
  expect(draft.defaultAvatarKey).toBe('green-2');
});

test('room profile draft preserves clear semantics for global and preset', () => {
  const global = roomProfileUpdateDraftFromForm({
    remarkName: '',
    notificationPolicy: 'all',
    usingGlobalProfile: true,
    roomDisplayName: 'Room Logan',
    pendingAvatarAssetId: 'asset_1',
    usingProfilePresetAvatar: false,
    defaultAvatarKey: 'green-2',
  });

  expect(global.roomDisplayName).toBe('');
  expect(global.avatarAssetId).toBe('');
  expect(global.defaultAvatarKey).toBe('');

  expect(roomProfileAvatarAssetIdForSave({ usingGlobalProfile: false, usingProfilePresetAvatar: true })).toBe('');
  expect(roomProfileAvatarAssetIdForSave({ usingGlobalProfile: false, usingProfilePresetAvatar: false })).toBeNull();
});

test('roomInfoUpdateDraftFromForm validates trims and normalizes fields', () => {
  expect(
    roomInfoUpdateDraftFromForm({
      name: '',
      description: '',
      visibility: 'public',
      joinPolicy: 'open',
      usingPresetAvatar: false,
      defaultAvatarKey: 'room-1',
    }).error,
  ).toBe('房间名不能为空');

  const draft = roomInfoUpdateDraftFromForm({
    name: '  General  ',
    description: '  Team room  ',
    visibility: 'PRIVATE',
    joinPolicy: 'allow_anyone',
    pendingAvatarAssetId: 'asset_2',
    usingPresetAvatar: false,
    defaultAvatarKey: 'room-2',
  });

  expect(draft.isValid).toBe(true);
  expect(draft.name).toBe('General');
  expect(draft.description).toBe('Team room');
  expect(draft.visibility).toBe('private');
  expect(draft.joinPolicy).toBe('open');
  expect(draft.avatarAssetId).toBe('asset_2');
  expect(draft.defaultAvatarKey).toBe('room-2');
});

test('room avatar draft clears uploaded avatar only when preset is selected', () => {
  expect(roomAvatarAssetIdForSave({ pendingAvatarAssetId: 'asset_3', usingPresetAvatar: true })).toBe('asset_3');
  expect(roomAvatarAssetIdForSave({ usingPresetAvatar: true })).toBe('');
  expect(roomAvatarAssetIdForSave({ usingPresetAvatar: false })).toBeNull();
});

test('room action gates share busy semantics across profile and management', () => {
  expect(canStartRoomProfileSave({ saving: false, leaving: false })).toBe(true);
  expect(canStartRoomProfileSave({ saving: true, leaving: false })).toBe(false);
  expect(canStartRoomLeave({ saving: false, leaving: true })).toBe(false);

  expect(roomInfoManagementBusy({ saving: false, deleting: false })).toBe(false);
  expect(roomInfoManagementBusy({ saving: true, deleting: false })).toBe(true);
  expect(canStartRoomInfoSave({ saving: false, deleting: false })).toBe(true);
  expect(canStartRoomInfoSave({ saving: false, deleting: true })).toBe(false);

  expect(canStartRoomDeletion({ canDeleteRoom: true, saving: false, deleting: false })).toBe(true);
  expect(canStartRoomDeletion({ canDeleteRoom: false, saving: false, deleting: false })).toBe(false);
  expect(canStartRoomDeletion({ canDeleteRoom: true, saving: true, deleting: false })).toBe(false);
});

const _profileBase = {
  pendingAvatarAssetId: 'asset_1',
  pendingAvatarUrl: '/avatar.png',
  usingGlobalProfile: false,
  usingProfilePresetAvatar: false,
  defaultAvatarKey: 'blue-1',
};

test('room profile dialog patches copy and global profile state', () => {
  const copied = roomProfileCopySucceeded({
    ..._profileBase,
    saving: false,
    leaving: false,
    uploadingAvatar: false,
    label: 'RID',
  });
  expect(copied.pendingAvatarAssetId).toBe('asset_1');
  expect(copied.pendingAvatarUrl).toBe('/avatar.png');
  expect(copied.usingGlobalProfile).toBe(false);
  expect(copied.usingProfilePresetAvatar).toBe(false);
  expect(copied.defaultAvatarKey).toBe('blue-1');
  expect(copied.saving).toBe(false);
  expect(copied.leaving).toBe(false);
  expect(copied.uploadingAvatar).toBe(false);
  expect(copied.error).toBeNull();
  expect(copied.notice).toBe('RID 已复制');

  const copyFailed = roomProfileCopyFailed({
    ..._profileBase,
    saving: false,
    leaving: false,
    uploadingAvatar: false,
    notice: 'kept',
    failure: failure('clipboard'),
  });
  expect(copyFailed.notice).toBe('kept');
  expect(copyFailed.error).toBe('无法复制');

  const global = roomProfileUseGlobalProfile({
    currentUserDefaultAvatarKey: 'green-2',
    saving: false,
    leaving: false,
    uploadingAvatar: false,
  });
  expect(global.pendingAvatarAssetId).toBeNull();
  expect(global.pendingAvatarUrl).toBeNull();
  expect(global.usingGlobalProfile).toBe(true);
  expect(global.usingProfilePresetAvatar).toBe(false);
  expect(global.defaultAvatarKey).toBe('green-2');
  expect(global.error).toBeNull();
  expect(global.notice).toBe('保存后将使用全局默认用户名和默认头像');

  const preset = roomProfileUsePresetAvatar({
    defaultAvatarKey: 'purple-2',
    saving: false,
    leaving: false,
    uploadingAvatar: false,
    error: 'kept error',
    notice: 'kept notice',
  });
  expect(preset.pendingAvatarAssetId).toBeNull();
  expect(preset.pendingAvatarUrl).toBeNull();
  expect(preset.usingGlobalProfile).toBe(false);
  expect(preset.usingProfilePresetAvatar).toBe(true);
  expect(preset.defaultAvatarKey).toBe('purple-2');
  expect(preset.error).toBe('kept error');
  expect(preset.notice).toBe('kept notice');
});

test('room profile notification policy patch normalizes values', () => {
  const mentions = roomProfileNotificationPolicyChanged({ notificationPolicy: 'mention_only' });
  const fallback = roomProfileNotificationPolicyChanged({ notificationPolicy: 'unexpected' });

  expect(mentions.notificationPolicy).toBe('silent');
  expect(fallback.notificationPolicy).toBe('all');
});

test('room profile dialog patches avatar upload lifecycle', () => {
  const started = roomProfileAvatarUploadStarted({
    pendingAvatarAssetId: null,
    pendingAvatarUrl: null,
    usingGlobalProfile: true,
    usingProfilePresetAvatar: false,
    defaultAvatarKey: 'blue-1',
    saving: false,
    leaving: false,
  });
  expect(started.uploadingAvatar).toBe(true);
  expect(started.error).toBeNull();
  expect(started.notice).toBeNull();
  expect(started.usingGlobalProfile).toBe(true);

  const succeeded = roomProfileAvatarUploadSucceeded({
    assetId: 'asset_1',
    assetUrl: '/asset.png',
    defaultAvatarKey: 'blue-1',
    saving: false,
    leaving: false,
  });
  expect(succeeded.pendingAvatarAssetId).toBe('asset_1');
  expect(succeeded.pendingAvatarUrl).toBe('/asset.png');
  expect(succeeded.usingGlobalProfile).toBe(false);
  expect(succeeded.usingProfilePresetAvatar).toBe(false);
  expect(succeeded.uploadingAvatar).toBe(false);
  expect(succeeded.error).toBeNull();

  const failed = roomProfileAvatarUploadFailed({
    pendingAvatarAssetId: 'old_asset',
    pendingAvatarUrl: '/old.png',
    usingGlobalProfile: false,
    usingProfilePresetAvatar: true,
    defaultAvatarKey: 'blue-1',
    saving: false,
    leaving: false,
    failure: failure('upload failed'),
  });
  expect(failed.pendingAvatarAssetId).toBe('old_asset');
  expect(failed.pendingAvatarUrl).toBe('/old.png');
  expect(failed.usingProfilePresetAvatar).toBe(true);
  expect(failed.uploadingAvatar).toBe(false);
  expect(failed.error).toBe('操作失败，请稍后重试');
  expect(failed.notice).toBeNull();
});

test('room profile dialog patches save and leave failures', () => {
  const saveStarted = roomProfileSaveStarted({ ..._profileBase, leaving: false, uploadingAvatar: false });
  expect(saveStarted.saving).toBe(true);
  expect(saveStarted.leaving).toBe(false);
  expect(saveStarted.error).toBeNull();
  expect(saveStarted.notice).toBeNull();

  const saveFailed = roomProfileSaveFailed({
    ..._profileBase,
    leaving: false,
    uploadingAvatar: false,
    failure: failure('save failed'),
  });
  expect(saveFailed.saving).toBe(false);
  expect(saveFailed.error).toBe('操作失败，请稍后重试');

  const leaveStarted = roomProfileLeaveStarted({ ..._profileBase, saving: false, uploadingAvatar: false });
  expect(leaveStarted.saving).toBe(false);
  expect(leaveStarted.leaving).toBe(true);
  expect(leaveStarted.error).toBeNull();
  expect(leaveStarted.notice).toBeNull();

  const leaveFailed = roomProfileLeaveFailed({
    ..._profileBase,
    saving: false,
    uploadingAvatar: false,
    failure: failure('leave failed'),
  });
  expect(leaveFailed.leaving).toBe(false);
  expect(leaveFailed.error).toBe('操作失败，请稍后重试');
  expect(leaveFailed.notice).toBeNull();
});

test('room management dialog patches avatar upload lifecycle', () => {
  const room = _room('room_1');

  const started = roomManagementAvatarUploadStarted({
    section: RoomManagementSection.info,
    room,
    pendingAvatarAssetId: null,
    pendingAvatarUrl: null,
    usingPresetAvatar: true,
    defaultAvatarKey: 'room-1',
    saving: false,
    deleting: false,
    changed: false,
  });
  expect(started.section).toBe(RoomManagementSection.info);
  expect(started.room).toBe(room);
  expect(started.uploadingAvatar).toBe(true);
  expect(started.usingPresetAvatar).toBe(true);
  expect(started.error).toBeNull();
  expect(started.notice).toBeNull();

  const succeeded = roomManagementAvatarUploadSucceeded({
    section: RoomManagementSection.info,
    room,
    assetId: 'asset_1',
    assetUrl: '/room.png',
    defaultAvatarKey: 'room-1',
    saving: false,
    deleting: false,
    changed: true,
  });
  expect(succeeded.pendingAvatarAssetId).toBe('asset_1');
  expect(succeeded.pendingAvatarUrl).toBe('/room.png');
  expect(succeeded.usingPresetAvatar).toBe(false);
  expect(succeeded.uploadingAvatar).toBe(false);
  expect(succeeded.changed).toBe(true);
  expect(succeeded.error).toBeNull();

  const failed = roomManagementAvatarUploadFailed({
    section: RoomManagementSection.info,
    room,
    pendingAvatarAssetId: 'old_asset',
    pendingAvatarUrl: '/old.png',
    usingPresetAvatar: false,
    defaultAvatarKey: 'room-1',
    saving: false,
    deleting: false,
    changed: false,
    failure: failure('upload failed'),
  });
  expect(failed.pendingAvatarAssetId).toBe('old_asset');
  expect(failed.pendingAvatarUrl).toBe('/old.png');
  expect(failed.usingPresetAvatar).toBe(false);
  expect(failed.uploadingAvatar).toBe(false);
  expect(failed.changed).toBe(false);
  expect(failed.error).toBe('操作失败，请稍后重试');
  expect(failed.notice).toBeNull();

  const preset = roomManagementUsePresetAvatar({
    section: RoomManagementSection.info,
    room,
    defaultAvatarKey: 'room-3',
    uploadingAvatar: false,
    saving: false,
    deleting: false,
    changed: true,
    error: 'kept error',
    notice: 'kept notice',
  });
  expect(preset.room).toBe(room);
  expect(preset.pendingAvatarAssetId).toBeNull();
  expect(preset.pendingAvatarUrl).toBeNull();
  expect(preset.usingPresetAvatar).toBe(true);
  expect(preset.defaultAvatarKey).toBe('room-3');
  expect(preset.changed).toBe(true);
  expect(preset.error).toBe('kept error');
  expect(preset.notice).toBe('kept notice');
});

test('room management section changes can clear transient messages', () => {
  const room = _room('room_1');
  const base = {
    room,
    pendingAvatarAssetId: 'asset_1',
    pendingAvatarUrl: '/room.png',
    usingPresetAvatar: false,
    defaultAvatarKey: 'room-1',
    uploadingAvatar: false,
    saving: false,
    deleting: false,
    changed: true,
  };

  const kept = roomManagementSectionChanged({
    ...base,
    section: RoomManagementSection.info,
    error: 'kept error',
    notice: 'kept notice',
  });

  expect(kept.section).toBe(RoomManagementSection.info);
  expect(kept.room).toBe(room);
  expect(kept.pendingAvatarAssetId).toBe('asset_1');
  expect(kept.pendingAvatarUrl).toBe('/room.png');
  expect(kept.usingPresetAvatar).toBe(false);
  expect(kept.changed).toBe(true);
  expect(kept.error).toBe('kept error');
  expect(kept.notice).toBe('kept notice');

  const cleared = roomManagementSectionChanged({
    ...base,
    section: RoomManagementSection.stickers,
    error: 'old error',
    notice: 'old notice',
  });

  expect(cleared.section).toBe(RoomManagementSection.stickers);
  expect(cleared.pendingAvatarAssetId).toBe('asset_1');
  expect(cleared.pendingAvatarUrl).toBe('/room.png');
  expect(cleared.changed).toBe(true);
  expect(cleared.error).toBeNull();
  expect(cleared.notice).toBeNull();
});

test('room management info field patches preserve sibling fields', () => {
  const visibility = roomManagementVisibilityChanged({ visibility: 'private', joinPolicy: 'open' });

  expect(visibility.visibility).toBe('private');
  expect(visibility.joinPolicy).toBe('open');

  const joinPolicy = roomManagementJoinPolicyChanged({ visibility: visibility.visibility, joinPolicy: 'unknown' });

  expect(joinPolicy.visibility).toBe('private');
  expect(joinPolicy.joinPolicy).toBe('approval_required');
});

test('room management dialog patches save invalid success and failure', () => {
  const room = _room('room_1');
  const updatedRoom = _room('room_1', { avatarUrl: '/saved.png' });
  const base = {
    section: RoomManagementSection.info,
    room,
    pendingAvatarAssetId: 'asset_1',
    pendingAvatarUrl: '/room.png',
    usingPresetAvatar: false,
    defaultAvatarKey: 'room-1',
    uploadingAvatar: false,
    deleting: false,
  };

  const invalid = roomManagementInfoDraftInvalid({
    ...base,
    saving: false,
    changed: false,
    notice: 'kept',
    error: '房间名不能为空',
  });
  expect(invalid.section).toBe(RoomManagementSection.info);
  expect(invalid.room).toBe(room);
  expect(invalid.pendingAvatarAssetId).toBe('asset_1');
  expect(invalid.saving).toBe(false);
  expect(invalid.error).toBe('房间名不能为空');
  expect(invalid.notice).toBe('kept');

  const started = roomManagementInfoSaveStarted({ ...base, changed: false });
  expect(started.saving).toBe(true);
  expect(started.deleting).toBe(false);
  expect(started.error).toBeNull();
  expect(started.notice).toBeNull();

  const succeeded = roomManagementInfoSaveSucceeded({
    section: RoomManagementSection.info,
    updatedRoom,
    defaultAvatarKey: 'room-1',
    uploadingAvatar: false,
    deleting: false,
  });
  expect(succeeded.room).toBe(updatedRoom);
  expect(succeeded.pendingAvatarAssetId).toBeNull();
  expect(succeeded.pendingAvatarUrl).toBeNull();
  expect(succeeded.usingPresetAvatar).toBe(false);
  expect(succeeded.saving).toBe(false);
  expect(succeeded.changed).toBe(true);
  expect(succeeded.error).toBeNull();
  expect(succeeded.notice).toBe('房间信息已保存');

  const failed = roomManagementInfoSaveFailed({ ...base, changed: true, failure: failure('save failed') });
  expect(failed.room).toBe(room);
  expect(failed.pendingAvatarAssetId).toBe('asset_1');
  expect(failed.saving).toBe(false);
  expect(failed.changed).toBe(true);
  expect(failed.error).toBe('操作失败，请稍后重试');
  expect(failed.notice).toBeNull();
});

test('room management dialog patches deletion lifecycle', () => {
  const room = _room('room_1');
  const base = {
    section: RoomManagementSection.info,
    room,
    pendingAvatarAssetId: 'asset_1',
    pendingAvatarUrl: '/room.png',
    usingPresetAvatar: false,
    defaultAvatarKey: 'room-1',
    uploadingAvatar: false,
    saving: false,
    changed: true,
  };

  const started = roomManagementDeletionStarted(base);
  expect(started.room).toBe(room);
  expect(started.deleting).toBe(true);
  expect(started.saving).toBe(false);
  expect(started.changed).toBe(true);
  expect(started.error).toBeNull();
  expect(started.notice).toBeNull();

  const failed = roomManagementDeletionFailed({ ...base, failure: failure('delete failed') });
  expect(failed.room).toBe(room);
  expect(failed.deleting).toBe(false);
  expect(failed.changed).toBe(true);
  expect(failed.error).toBe('操作失败，请稍后重试');
  expect(failed.notice).toBeNull();
});

function _room(id: string, opts: { avatarUrl?: string | null } = {}): RoomDetail {
  const june5 = new Date(Date.UTC(2026, 5, 5));
  return new RoomDetail({
    id,
    name: id,
    avatarUrl: opts.avatarUrl ?? null,
    defaultAvatarKey: 'room-1',
    memberCount: 3,
    myMembership: new RoomMembership({ joinedAt: june5, role: 'admin' }),
    live: new LiveState({ roomId: id, participantCount: 0, participants: [], updatedAt: june5 }),
    createdAt: june5,
    updatedAt: june5,
  });
}
