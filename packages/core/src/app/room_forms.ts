import type { RoomDetail } from '../protocol/models';
import { userFacingErrorMessage } from './error_display';
import {
  normalizeRoomJoinPolicy,
  normalizeRoomNotificationPolicy,
  normalizeRoomVisibility,
  roomCopyFailureMessage,
  roomCopySuccessNotice,
  roomInfoSavedNotice,
  roomUseGlobalProfileNotice,
} from './room_display';

export class CreateRoomDraft {
  private constructor(opts: { name?: string | null; error?: string | null }) {
    this.name = opts.name ?? null;
    this.error = opts.error ?? null;
  }

  static invalid(error: string): CreateRoomDraft {
    return new CreateRoomDraft({ error });
  }

  static valid(opts: { name: string }): CreateRoomDraft {
    return new CreateRoomDraft({ name: opts.name });
  }

  readonly name: string | null;
  readonly error: string | null;

  get isValid(): boolean {
    return this.error == null;
  }
}

export class CreateRoomDialogPatch {
  constructor(opts: { busy: boolean; error: string | null }) {
    this.busy = opts.busy;
    this.error = opts.error;
  }

  readonly busy: boolean;
  readonly error: string | null;
}

export class RoomProfileUpdateDraft {
  constructor(opts: {
    remarkName: string;
    notificationPolicy: string;
    roomDisplayName: string;
    avatarAssetId: string | null;
    defaultAvatarKey: string;
  }) {
    this.remarkName = opts.remarkName;
    this.notificationPolicy = opts.notificationPolicy;
    this.roomDisplayName = opts.roomDisplayName;
    this.avatarAssetId = opts.avatarAssetId;
    this.defaultAvatarKey = opts.defaultAvatarKey;
  }

  readonly remarkName: string;
  readonly notificationPolicy: string;
  readonly roomDisplayName: string;
  readonly avatarAssetId: string | null;
  readonly defaultAvatarKey: string;
}

export class RoomProfileDialogStatePatch {
  constructor(opts: {
    pendingAvatarAssetId: string | null;
    pendingAvatarUrl: string | null;
    usingGlobalProfile: boolean;
    usingProfilePresetAvatar: boolean;
    defaultAvatarKey: string;
    saving: boolean;
    leaving: boolean;
    uploadingAvatar: boolean;
    error: string | null;
    notice: string | null;
  }) {
    this.pendingAvatarAssetId = opts.pendingAvatarAssetId;
    this.pendingAvatarUrl = opts.pendingAvatarUrl;
    this.usingGlobalProfile = opts.usingGlobalProfile;
    this.usingProfilePresetAvatar = opts.usingProfilePresetAvatar;
    this.defaultAvatarKey = opts.defaultAvatarKey;
    this.saving = opts.saving;
    this.leaving = opts.leaving;
    this.uploadingAvatar = opts.uploadingAvatar;
    this.error = opts.error;
    this.notice = opts.notice;
  }

  readonly pendingAvatarAssetId: string | null;
  readonly pendingAvatarUrl: string | null;
  readonly usingGlobalProfile: boolean;
  readonly usingProfilePresetAvatar: boolean;
  readonly defaultAvatarKey: string;
  readonly saving: boolean;
  readonly leaving: boolean;
  readonly uploadingAvatar: boolean;
  readonly error: string | null;
  readonly notice: string | null;
}

export class RoomProfileNotificationPolicyPatch {
  constructor(opts: { notificationPolicy: string }) {
    this.notificationPolicy = opts.notificationPolicy;
  }

  readonly notificationPolicy: string;
}

export class RoomInfoUpdateDraft {
  private constructor(opts: {
    name?: string | null;
    description?: string | null;
    visibility?: string | null;
    joinPolicy?: string | null;
    avatarAssetId?: string | null;
    defaultAvatarKey?: string | null;
    error?: string | null;
  }) {
    this.name = opts.name ?? null;
    this.description = opts.description ?? null;
    this.visibility = opts.visibility ?? null;
    this.joinPolicy = opts.joinPolicy ?? null;
    this.avatarAssetId = opts.avatarAssetId ?? null;
    this.defaultAvatarKey = opts.defaultAvatarKey ?? null;
    this.error = opts.error ?? null;
  }

  static invalid(error: string): RoomInfoUpdateDraft {
    return new RoomInfoUpdateDraft({ error });
  }

  static valid(opts: {
    name: string | null;
    description: string | null;
    visibility: string | null;
    joinPolicy: string | null;
    avatarAssetId: string | null;
    defaultAvatarKey: string | null;
  }): RoomInfoUpdateDraft {
    return new RoomInfoUpdateDraft({ ...opts, error: null });
  }

  readonly name: string | null;
  readonly description: string | null;
  readonly visibility: string | null;
  readonly joinPolicy: string | null;
  readonly avatarAssetId: string | null;
  readonly defaultAvatarKey: string | null;
  readonly error: string | null;

  get isValid(): boolean {
    return this.error == null;
  }
}

export enum RoomManagementSection {
  info = 'info',
  stickers = 'stickers',
}

export class RoomManagementDialogStatePatch {
  constructor(opts: {
    section: RoomManagementSection;
    room: RoomDetail;
    pendingAvatarAssetId: string | null;
    pendingAvatarUrl: string | null;
    usingPresetAvatar: boolean;
    defaultAvatarKey: string;
    uploadingAvatar: boolean;
    saving: boolean;
    deleting: boolean;
    changed: boolean;
    error: string | null;
    notice: string | null;
  }) {
    this.section = opts.section;
    this.room = opts.room;
    this.pendingAvatarAssetId = opts.pendingAvatarAssetId;
    this.pendingAvatarUrl = opts.pendingAvatarUrl;
    this.usingPresetAvatar = opts.usingPresetAvatar;
    this.defaultAvatarKey = opts.defaultAvatarKey;
    this.uploadingAvatar = opts.uploadingAvatar;
    this.saving = opts.saving;
    this.deleting = opts.deleting;
    this.changed = opts.changed;
    this.error = opts.error;
    this.notice = opts.notice;
  }

  readonly section: RoomManagementSection;
  readonly room: RoomDetail;
  readonly pendingAvatarAssetId: string | null;
  readonly pendingAvatarUrl: string | null;
  readonly usingPresetAvatar: boolean;
  readonly defaultAvatarKey: string;
  readonly uploadingAvatar: boolean;
  readonly saving: boolean;
  readonly deleting: boolean;
  readonly changed: boolean;
  readonly error: string | null;
  readonly notice: string | null;
}

export class RoomManagementInfoFieldsPatch {
  constructor(opts: { visibility: string; joinPolicy: string }) {
    this.visibility = opts.visibility;
    this.joinPolicy = opts.joinPolicy;
  }

  readonly visibility: string;
  readonly joinPolicy: string;
}

export function canStartRoomProfileSave(opts: { saving: boolean; leaving: boolean }): boolean {
  return !opts.saving && !opts.leaving;
}

export function canStartCreateRoom(opts: { busy: boolean }): boolean {
  return !opts.busy;
}

export function createRoomSubmitStarted(): CreateRoomDialogPatch {
  return new CreateRoomDialogPatch({ busy: true, error: null });
}

export function createRoomSubmitFailed(opts: { failure: unknown }): CreateRoomDialogPatch {
  return new CreateRoomDialogPatch({
    busy: true,
    error: userFacingErrorMessage(opts.failure),
  });
}

export function createRoomSubmitFinished(opts: { error: string | null }): CreateRoomDialogPatch {
  return new CreateRoomDialogPatch({ busy: false, error: opts.error });
}

export function canStartRoomLeave(opts: { saving: boolean; leaving: boolean }): boolean {
  return !opts.saving && !opts.leaving;
}

export function roomProfileCopySucceeded(opts: {
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingGlobalProfile: boolean;
  usingProfilePresetAvatar: boolean;
  defaultAvatarKey: string;
  saving: boolean;
  leaving: boolean;
  uploadingAvatar: boolean;
  label: string;
}): RoomProfileDialogStatePatch {
  return new RoomProfileDialogStatePatch({
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingGlobalProfile: opts.usingGlobalProfile,
    usingProfilePresetAvatar: opts.usingProfilePresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    saving: opts.saving,
    leaving: opts.leaving,
    uploadingAvatar: opts.uploadingAvatar,
    error: null,
    notice: roomCopySuccessNotice(opts.label),
  });
}

export function roomProfileNotificationPolicyChanged(opts: {
  notificationPolicy: string;
}): RoomProfileNotificationPolicyPatch {
  return new RoomProfileNotificationPolicyPatch({
    notificationPolicy: normalizeRoomNotificationPolicy(opts.notificationPolicy),
  });
}

export function roomProfileCopyFailed(opts: {
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingGlobalProfile: boolean;
  usingProfilePresetAvatar: boolean;
  defaultAvatarKey: string;
  saving: boolean;
  leaving: boolean;
  uploadingAvatar: boolean;
  notice: string | null;
  failure: unknown;
}): RoomProfileDialogStatePatch {
  return new RoomProfileDialogStatePatch({
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingGlobalProfile: opts.usingGlobalProfile,
    usingProfilePresetAvatar: opts.usingProfilePresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    saving: opts.saving,
    leaving: opts.leaving,
    uploadingAvatar: opts.uploadingAvatar,
    error: roomCopyFailureMessage(opts.failure),
    notice: opts.notice,
  });
}

export function roomProfileAvatarPickFailed(opts: {
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingGlobalProfile: boolean;
  usingProfilePresetAvatar: boolean;
  defaultAvatarKey: string;
  saving: boolean;
  leaving: boolean;
  uploadingAvatar: boolean;
  notice: string | null;
  failure: unknown;
}): RoomProfileDialogStatePatch {
  return new RoomProfileDialogStatePatch({
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingGlobalProfile: opts.usingGlobalProfile,
    usingProfilePresetAvatar: opts.usingProfilePresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    saving: opts.saving,
    leaving: opts.leaving,
    uploadingAvatar: opts.uploadingAvatar,
    error: userFacingErrorMessage(opts.failure),
    notice: opts.notice,
  });
}

export function roomProfileAvatarUploadStarted(opts: {
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingGlobalProfile: boolean;
  usingProfilePresetAvatar: boolean;
  defaultAvatarKey: string;
  saving: boolean;
  leaving: boolean;
}): RoomProfileDialogStatePatch {
  return new RoomProfileDialogStatePatch({
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingGlobalProfile: opts.usingGlobalProfile,
    usingProfilePresetAvatar: opts.usingProfilePresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    saving: opts.saving,
    leaving: opts.leaving,
    uploadingAvatar: true,
    error: null,
    notice: null,
  });
}

export function roomProfileAvatarUploadSucceeded(opts: {
  assetId: string;
  assetUrl: string;
  defaultAvatarKey: string;
  saving: boolean;
  leaving: boolean;
}): RoomProfileDialogStatePatch {
  return new RoomProfileDialogStatePatch({
    pendingAvatarAssetId: opts.assetId,
    pendingAvatarUrl: opts.assetUrl,
    usingGlobalProfile: false,
    usingProfilePresetAvatar: false,
    defaultAvatarKey: opts.defaultAvatarKey,
    saving: opts.saving,
    leaving: opts.leaving,
    uploadingAvatar: false,
    error: null,
    notice: null,
  });
}

export function roomProfileAvatarUploadFailed(opts: {
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingGlobalProfile: boolean;
  usingProfilePresetAvatar: boolean;
  defaultAvatarKey: string;
  saving: boolean;
  leaving: boolean;
  failure: unknown;
}): RoomProfileDialogStatePatch {
  return new RoomProfileDialogStatePatch({
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingGlobalProfile: opts.usingGlobalProfile,
    usingProfilePresetAvatar: opts.usingProfilePresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    saving: opts.saving,
    leaving: opts.leaving,
    uploadingAvatar: false,
    error: userFacingErrorMessage(opts.failure),
    notice: null,
  });
}

export function roomProfileUseGlobalProfile(opts: {
  currentUserDefaultAvatarKey: string;
  saving: boolean;
  leaving: boolean;
  uploadingAvatar: boolean;
}): RoomProfileDialogStatePatch {
  return new RoomProfileDialogStatePatch({
    pendingAvatarAssetId: null,
    pendingAvatarUrl: null,
    usingGlobalProfile: true,
    usingProfilePresetAvatar: false,
    defaultAvatarKey: opts.currentUserDefaultAvatarKey,
    saving: opts.saving,
    leaving: opts.leaving,
    uploadingAvatar: opts.uploadingAvatar,
    error: null,
    notice: roomUseGlobalProfileNotice(),
  });
}

export function roomProfileUsePresetAvatar(opts: {
  defaultAvatarKey: string;
  saving: boolean;
  leaving: boolean;
  uploadingAvatar: boolean;
  error: string | null;
  notice: string | null;
}): RoomProfileDialogStatePatch {
  return new RoomProfileDialogStatePatch({
    pendingAvatarAssetId: null,
    pendingAvatarUrl: null,
    usingGlobalProfile: false,
    usingProfilePresetAvatar: true,
    defaultAvatarKey: opts.defaultAvatarKey,
    saving: opts.saving,
    leaving: opts.leaving,
    uploadingAvatar: opts.uploadingAvatar,
    error: opts.error,
    notice: opts.notice,
  });
}

export function roomProfileSaveStarted(opts: {
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingGlobalProfile: boolean;
  usingProfilePresetAvatar: boolean;
  defaultAvatarKey: string;
  leaving: boolean;
  uploadingAvatar: boolean;
}): RoomProfileDialogStatePatch {
  return new RoomProfileDialogStatePatch({
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingGlobalProfile: opts.usingGlobalProfile,
    usingProfilePresetAvatar: opts.usingProfilePresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    saving: true,
    leaving: opts.leaving,
    uploadingAvatar: opts.uploadingAvatar,
    error: null,
    notice: null,
  });
}

export function roomProfileSaveFailed(opts: {
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingGlobalProfile: boolean;
  usingProfilePresetAvatar: boolean;
  defaultAvatarKey: string;
  leaving: boolean;
  uploadingAvatar: boolean;
  failure: unknown;
}): RoomProfileDialogStatePatch {
  return new RoomProfileDialogStatePatch({
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingGlobalProfile: opts.usingGlobalProfile,
    usingProfilePresetAvatar: opts.usingProfilePresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    saving: false,
    leaving: opts.leaving,
    uploadingAvatar: opts.uploadingAvatar,
    error: userFacingErrorMessage(opts.failure),
    notice: null,
  });
}

export function roomProfileLeaveStarted(opts: {
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingGlobalProfile: boolean;
  usingProfilePresetAvatar: boolean;
  defaultAvatarKey: string;
  saving: boolean;
  uploadingAvatar: boolean;
}): RoomProfileDialogStatePatch {
  return new RoomProfileDialogStatePatch({
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingGlobalProfile: opts.usingGlobalProfile,
    usingProfilePresetAvatar: opts.usingProfilePresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    saving: opts.saving,
    leaving: true,
    uploadingAvatar: opts.uploadingAvatar,
    error: null,
    notice: null,
  });
}

export function roomProfileLeaveFailed(opts: {
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingGlobalProfile: boolean;
  usingProfilePresetAvatar: boolean;
  defaultAvatarKey: string;
  saving: boolean;
  uploadingAvatar: boolean;
  failure: unknown;
}): RoomProfileDialogStatePatch {
  return new RoomProfileDialogStatePatch({
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingGlobalProfile: opts.usingGlobalProfile,
    usingProfilePresetAvatar: opts.usingProfilePresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    saving: opts.saving,
    leaving: false,
    uploadingAvatar: opts.uploadingAvatar,
    error: userFacingErrorMessage(opts.failure),
    notice: null,
  });
}

export function roomInfoManagementBusy(opts: { saving: boolean; deleting: boolean }): boolean {
  return opts.saving || opts.deleting;
}

export function canStartRoomInfoSave(opts: { saving: boolean; deleting: boolean }): boolean {
  return !roomInfoManagementBusy(opts);
}

export function canStartRoomDeletion(opts: { canDeleteRoom: boolean; saving: boolean; deleting: boolean }): boolean {
  return opts.canDeleteRoom && !roomInfoManagementBusy({ saving: opts.saving, deleting: opts.deleting });
}

export function roomManagementAvatarPickFailed(opts: {
  section: RoomManagementSection;
  room: RoomDetail;
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingPresetAvatar: boolean;
  defaultAvatarKey: string;
  uploadingAvatar: boolean;
  saving: boolean;
  deleting: boolean;
  changed: boolean;
  notice: string | null;
  failure: unknown;
}): RoomManagementDialogStatePatch {
  return new RoomManagementDialogStatePatch({
    section: opts.section,
    room: opts.room,
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingPresetAvatar: opts.usingPresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    uploadingAvatar: opts.uploadingAvatar,
    saving: opts.saving,
    deleting: opts.deleting,
    changed: opts.changed,
    error: userFacingErrorMessage(opts.failure),
    notice: opts.notice,
  });
}

export function roomManagementAvatarUploadStarted(opts: {
  section: RoomManagementSection;
  room: RoomDetail;
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingPresetAvatar: boolean;
  defaultAvatarKey: string;
  saving: boolean;
  deleting: boolean;
  changed: boolean;
}): RoomManagementDialogStatePatch {
  return new RoomManagementDialogStatePatch({
    section: opts.section,
    room: opts.room,
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingPresetAvatar: opts.usingPresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    uploadingAvatar: true,
    saving: opts.saving,
    deleting: opts.deleting,
    changed: opts.changed,
    error: null,
    notice: null,
  });
}

export function roomManagementAvatarUploadSucceeded(opts: {
  section: RoomManagementSection;
  room: RoomDetail;
  assetId: string;
  assetUrl: string;
  defaultAvatarKey: string;
  saving: boolean;
  deleting: boolean;
  changed: boolean;
}): RoomManagementDialogStatePatch {
  return new RoomManagementDialogStatePatch({
    section: opts.section,
    room: opts.room,
    pendingAvatarAssetId: opts.assetId,
    pendingAvatarUrl: opts.assetUrl,
    usingPresetAvatar: false,
    defaultAvatarKey: opts.defaultAvatarKey,
    uploadingAvatar: false,
    saving: opts.saving,
    deleting: opts.deleting,
    changed: opts.changed,
    error: null,
    notice: null,
  });
}

export function roomManagementAvatarUploadFailed(opts: {
  section: RoomManagementSection;
  room: RoomDetail;
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingPresetAvatar: boolean;
  defaultAvatarKey: string;
  saving: boolean;
  deleting: boolean;
  changed: boolean;
  failure: unknown;
}): RoomManagementDialogStatePatch {
  return new RoomManagementDialogStatePatch({
    section: opts.section,
    room: opts.room,
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingPresetAvatar: opts.usingPresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    uploadingAvatar: false,
    saving: opts.saving,
    deleting: opts.deleting,
    changed: opts.changed,
    error: userFacingErrorMessage(opts.failure),
    notice: null,
  });
}

export function roomManagementUsePresetAvatar(opts: {
  section: RoomManagementSection;
  room: RoomDetail;
  defaultAvatarKey: string;
  uploadingAvatar: boolean;
  saving: boolean;
  deleting: boolean;
  changed: boolean;
  error: string | null;
  notice: string | null;
}): RoomManagementDialogStatePatch {
  return new RoomManagementDialogStatePatch({
    section: opts.section,
    room: opts.room,
    pendingAvatarAssetId: null,
    pendingAvatarUrl: null,
    usingPresetAvatar: true,
    defaultAvatarKey: opts.defaultAvatarKey,
    uploadingAvatar: opts.uploadingAvatar,
    saving: opts.saving,
    deleting: opts.deleting,
    changed: opts.changed,
    error: opts.error,
    notice: opts.notice,
  });
}

export function roomManagementSectionChanged(opts: {
  section: RoomManagementSection;
  room: RoomDetail;
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingPresetAvatar: boolean;
  defaultAvatarKey: string;
  uploadingAvatar: boolean;
  saving: boolean;
  deleting: boolean;
  changed: boolean;
  error: string | null;
  notice: string | null;
}): RoomManagementDialogStatePatch {
  const clearTransientMessages = opts.section === RoomManagementSection.stickers;
  return new RoomManagementDialogStatePatch({
    section: opts.section,
    room: opts.room,
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingPresetAvatar: opts.usingPresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    uploadingAvatar: opts.uploadingAvatar,
    saving: opts.saving,
    deleting: opts.deleting,
    changed: opts.changed,
    error: clearTransientMessages ? null : opts.error,
    notice: clearTransientMessages ? null : opts.notice,
  });
}

export function roomManagementVisibilityChanged(opts: {
  visibility: string;
  joinPolicy: string;
}): RoomManagementInfoFieldsPatch {
  return new RoomManagementInfoFieldsPatch({
    visibility: normalizeRoomVisibility(opts.visibility),
    joinPolicy: opts.joinPolicy,
  });
}

export function roomManagementJoinPolicyChanged(opts: {
  visibility: string;
  joinPolicy: string;
}): RoomManagementInfoFieldsPatch {
  return new RoomManagementInfoFieldsPatch({
    visibility: opts.visibility,
    joinPolicy: normalizeRoomJoinPolicy(opts.joinPolicy),
  });
}

export function roomManagementInfoDraftInvalid(opts: {
  section: RoomManagementSection;
  room: RoomDetail;
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingPresetAvatar: boolean;
  defaultAvatarKey: string;
  uploadingAvatar: boolean;
  saving: boolean;
  deleting: boolean;
  changed: boolean;
  notice: string | null;
  error: string | null;
}): RoomManagementDialogStatePatch {
  return new RoomManagementDialogStatePatch({
    section: opts.section,
    room: opts.room,
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingPresetAvatar: opts.usingPresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    uploadingAvatar: opts.uploadingAvatar,
    saving: opts.saving,
    deleting: opts.deleting,
    changed: opts.changed,
    error: opts.error,
    notice: opts.notice,
  });
}

export function roomManagementInfoSaveStarted(opts: {
  section: RoomManagementSection;
  room: RoomDetail;
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingPresetAvatar: boolean;
  defaultAvatarKey: string;
  uploadingAvatar: boolean;
  deleting: boolean;
  changed: boolean;
}): RoomManagementDialogStatePatch {
  return new RoomManagementDialogStatePatch({
    section: opts.section,
    room: opts.room,
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingPresetAvatar: opts.usingPresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    uploadingAvatar: opts.uploadingAvatar,
    saving: true,
    deleting: opts.deleting,
    changed: opts.changed,
    error: null,
    notice: null,
  });
}

export function roomManagementInfoSaveSucceeded(opts: {
  section: RoomManagementSection;
  updatedRoom: RoomDetail;
  defaultAvatarKey: string;
  uploadingAvatar: boolean;
  deleting: boolean;
}): RoomManagementDialogStatePatch {
  return new RoomManagementDialogStatePatch({
    section: opts.section,
    room: opts.updatedRoom,
    pendingAvatarAssetId: null,
    pendingAvatarUrl: null,
    usingPresetAvatar: opts.updatedRoom.avatarUrl == null,
    defaultAvatarKey: opts.defaultAvatarKey,
    uploadingAvatar: opts.uploadingAvatar,
    saving: false,
    deleting: opts.deleting,
    changed: true,
    error: null,
    notice: roomInfoSavedNotice(),
  });
}

export function roomManagementInfoSaveFailed(opts: {
  section: RoomManagementSection;
  room: RoomDetail;
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingPresetAvatar: boolean;
  defaultAvatarKey: string;
  uploadingAvatar: boolean;
  deleting: boolean;
  changed: boolean;
  failure: unknown;
}): RoomManagementDialogStatePatch {
  return new RoomManagementDialogStatePatch({
    section: opts.section,
    room: opts.room,
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingPresetAvatar: opts.usingPresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    uploadingAvatar: opts.uploadingAvatar,
    saving: false,
    deleting: opts.deleting,
    changed: opts.changed,
    error: userFacingErrorMessage(opts.failure),
    notice: null,
  });
}

export function roomManagementDeletionStarted(opts: {
  section: RoomManagementSection;
  room: RoomDetail;
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingPresetAvatar: boolean;
  defaultAvatarKey: string;
  uploadingAvatar: boolean;
  saving: boolean;
  changed: boolean;
}): RoomManagementDialogStatePatch {
  return new RoomManagementDialogStatePatch({
    section: opts.section,
    room: opts.room,
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingPresetAvatar: opts.usingPresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    uploadingAvatar: opts.uploadingAvatar,
    saving: opts.saving,
    deleting: true,
    changed: opts.changed,
    error: null,
    notice: null,
  });
}

export function roomManagementDeletionFailed(opts: {
  section: RoomManagementSection;
  room: RoomDetail;
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  usingPresetAvatar: boolean;
  defaultAvatarKey: string;
  uploadingAvatar: boolean;
  saving: boolean;
  changed: boolean;
  failure: unknown;
}): RoomManagementDialogStatePatch {
  return new RoomManagementDialogStatePatch({
    section: opts.section,
    room: opts.room,
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    usingPresetAvatar: opts.usingPresetAvatar,
    defaultAvatarKey: opts.defaultAvatarKey,
    uploadingAvatar: opts.uploadingAvatar,
    saving: opts.saving,
    deleting: false,
    changed: opts.changed,
    error: userFacingErrorMessage(opts.failure),
    notice: null,
  });
}

export function createRoomDraftFromForm(opts: { name: string }): CreateRoomDraft {
  const trimmedName = opts.name.trim();
  if (trimmedName.length === 0) {
    return CreateRoomDraft.invalid('房间名不能为空');
  }
  return CreateRoomDraft.valid({ name: trimmedName });
}

export function roomProfileUpdateDraftFromForm(opts: {
  remarkName: string;
  notificationPolicy: string;
  usingGlobalProfile: boolean;
  roomDisplayName: string;
  pendingAvatarAssetId?: string | null;
  usingProfilePresetAvatar: boolean;
  defaultAvatarKey: string;
}): RoomProfileUpdateDraft {
  const { usingGlobalProfile } = opts;
  return new RoomProfileUpdateDraft({
    remarkName: opts.remarkName.trim(),
    notificationPolicy: normalizeRoomNotificationPolicy(opts.notificationPolicy),
    roomDisplayName: usingGlobalProfile ? '' : opts.roomDisplayName.trim(),
    avatarAssetId: roomProfileAvatarAssetIdForSave({
      usingGlobalProfile,
      pendingAvatarAssetId: opts.pendingAvatarAssetId,
      usingProfilePresetAvatar: opts.usingProfilePresetAvatar,
    }),
    defaultAvatarKey: usingGlobalProfile ? '' : opts.defaultAvatarKey,
  });
}

export function roomProfileAvatarAssetIdForSave(opts: {
  usingGlobalProfile: boolean;
  pendingAvatarAssetId?: string | null;
  usingProfilePresetAvatar: boolean;
}): string | null {
  if (opts.usingGlobalProfile) return '';
  const pending = opts.pendingAvatarAssetId ?? null;
  if (pending != null) return pending;
  if (opts.usingProfilePresetAvatar) return '';
  return null;
}

export function roomInfoUpdateDraftFromForm(opts: {
  name: string;
  description: string;
  visibility: string;
  joinPolicy: string;
  pendingAvatarAssetId?: string | null;
  usingPresetAvatar: boolean;
  defaultAvatarKey: string;
}): RoomInfoUpdateDraft {
  const trimmedName = opts.name.trim();
  if (trimmedName.length === 0) {
    return RoomInfoUpdateDraft.invalid('房间名不能为空');
  }
  return RoomInfoUpdateDraft.valid({
    name: trimmedName,
    description: opts.description.trim(),
    visibility: normalizeRoomVisibility(opts.visibility),
    joinPolicy: normalizeRoomJoinPolicy(opts.joinPolicy),
    avatarAssetId: roomAvatarAssetIdForSave({
      pendingAvatarAssetId: opts.pendingAvatarAssetId,
      usingPresetAvatar: opts.usingPresetAvatar,
    }),
    defaultAvatarKey: opts.defaultAvatarKey,
  });
}

export function roomAvatarAssetIdForSave(opts: {
  pendingAvatarAssetId?: string | null;
  usingPresetAvatar: boolean;
}): string | null {
  const pending = opts.pendingAvatarAssetId ?? null;
  if (pending != null) return pending;
  if (opts.usingPresetAvatar) return '';
  return null;
}
