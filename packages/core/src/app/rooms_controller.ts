import { asInt, asList, asMap, asString, mapList } from '../lib/json';
import type { JsonMap } from '../lib/json';
import type { DownloadedFile, GangApi } from '../protocol/api_client';
import { LiveState, RoomCard, RoomDetail, UserSummary } from '../protocol/models';
import type {
  JoinRequest,
  JoinRoomResult,
  LiveParticipant,
  Message,
  PublicRoom,
  RoomApplication,
  RoomBlacklistEntry,
  RoomEventNotification,
  RoomInvite,
  RoomMember,
  RoomMemberPage,
  RoomMemberProfile,
  Sticker,
  StickerPack,
  UploadedAsset,
} from '../protocol/models';
import { userFacingErrorMessage } from './error_display';
import * as room_display from './room_display';
import * as room_join_requests from './room_join_requests';
import * as room_live_state from './room_live_state';

export class RoomOpenSnapshot {
  constructor(opts: { detail: RoomDetail; messages: Message[]; live: LiveState }) {
    this.detail = opts.detail;
    this.messages = opts.messages;
    this.live = opts.live;
  }

  readonly detail: RoomDetail;
  readonly messages: Message[];
  readonly live: LiveState;
}

export class RoomListLoadPatch {
  constructor(opts: { rooms: RoomCard[]; loading: boolean; error: string | null }) {
    this.rooms = opts.rooms;
    this.loading = opts.loading;
    this.error = opts.error;
  }

  readonly rooms: RoomCard[];
  readonly loading: boolean;
  readonly error: string | null;
}

export class RoomCardsPatch {
  constructor(opts: { rooms: RoomCard[] }) {
    this.rooms = opts.rooms;
  }

  readonly rooms: RoomCard[];
}

export class RoomUpdatedPatch {
  constructor(opts: { rooms: RoomCard[]; selectedRoom: RoomDetail | null; shouldReloadMembers: boolean }) {
    this.rooms = opts.rooms;
    this.selectedRoom = opts.selectedRoom;
    this.shouldReloadMembers = opts.shouldReloadMembers;
  }

  readonly rooms: RoomCard[];
  readonly selectedRoom: RoomDetail | null;
  readonly shouldReloadMembers: boolean;
}

export class RoomOpenStatePatch {
  constructor(opts: {
    settingsOpen: boolean;
    selectedRoomId: string | null;
    selectedRoom: RoomDetail | null;
    loadingRoom: boolean;
    error: string | null;
    selectedRoomHasPendingJoinRequests: boolean;
    messages: Message[];
    live: LiveState | null;
    livePanelOpen: boolean;
  }) {
    this.settingsOpen = opts.settingsOpen;
    this.selectedRoomId = opts.selectedRoomId;
    this.selectedRoom = opts.selectedRoom;
    this.loadingRoom = opts.loadingRoom;
    this.error = opts.error;
    this.selectedRoomHasPendingJoinRequests = opts.selectedRoomHasPendingJoinRequests;
    this.messages = opts.messages;
    this.live = opts.live;
    this.livePanelOpen = opts.livePanelOpen;
  }

  readonly settingsOpen: boolean;
  readonly selectedRoomId: string | null;
  readonly selectedRoom: RoomDetail | null;
  readonly loadingRoom: boolean;
  readonly error: string | null;
  readonly selectedRoomHasPendingJoinRequests: boolean;
  readonly messages: Message[];
  readonly live: LiveState | null;
  readonly livePanelOpen: boolean;
}

export class RoomOpenSuccessEffects {
  constructor(opts: { joinRequestBadgeRoom: RoomDetail; joinLiveSource: string | null }) {
    this.joinRequestBadgeRoom = opts.joinRequestBadgeRoom;
    this.joinLiveSource = opts.joinLiveSource;
  }

  readonly joinRequestBadgeRoom: RoomDetail;
  readonly joinLiveSource: string | null;
}

export class RoomLiveSnapshotPatch {
  constructor(opts: { roomId: string; rooms: RoomCard[]; selectedLive: LiveState | null }) {
    this.roomId = opts.roomId;
    this.rooms = opts.rooms;
    this.selectedLive = opts.selectedLive;
  }

  readonly roomId: string;
  readonly rooms: RoomCard[];
  readonly selectedLive: LiveState | null;
}

export class RoomDeletedPatch {
  constructor(opts: {
    roomId: string;
    rooms: RoomCard[];
    selectedRoomId: string | null;
    selectedRoom: RoomDetail | null;
    selectedRoomHasPendingJoinRequests: boolean;
    messages: Message[];
    live: LiveState | null;
    livePanelOpen: boolean;
    settingsOpen: boolean;
    joinedLiveRoomId: string | null;
    wasSelected: boolean;
    shouldDisconnectLive: boolean;
  }) {
    this.roomId = opts.roomId;
    this.rooms = opts.rooms;
    this.selectedRoomId = opts.selectedRoomId;
    this.selectedRoom = opts.selectedRoom;
    this.selectedRoomHasPendingJoinRequests = opts.selectedRoomHasPendingJoinRequests;
    this.messages = opts.messages;
    this.live = opts.live;
    this.livePanelOpen = opts.livePanelOpen;
    this.settingsOpen = opts.settingsOpen;
    this.joinedLiveRoomId = opts.joinedLiveRoomId;
    this.wasSelected = opts.wasSelected;
    this.shouldDisconnectLive = opts.shouldDisconnectLive;
  }

  readonly roomId: string;
  readonly rooms: RoomCard[];
  readonly selectedRoomId: string | null;
  readonly selectedRoom: RoomDetail | null;
  readonly selectedRoomHasPendingJoinRequests: boolean;
  readonly messages: Message[];
  readonly live: LiveState | null;
  readonly livePanelOpen: boolean;
  readonly settingsOpen: boolean;
  readonly joinedLiveRoomId: string | null;
  readonly wasSelected: boolean;
  readonly shouldDisconnectLive: boolean;
}

export class RoomSelectedDetailPatch {
  constructor(opts: { selectedRoom: RoomDetail; rooms: RoomCard[] }) {
    this.selectedRoom = opts.selectedRoom;
    this.rooms = opts.rooms;
  }

  readonly selectedRoom: RoomDetail;
  readonly rooms: RoomCard[];
}

export class RoomRoleChangedPatch {
  constructor(opts: { roomId: string; selectedRoom: RoomDetail }) {
    this.roomId = opts.roomId;
    this.selectedRoom = opts.selectedRoom;
  }

  readonly roomId: string;
  readonly selectedRoom: RoomDetail;
}

export class RoomLiveRefreshPatch {
  constructor(opts: { live: LiveState }) {
    this.live = opts.live;
  }

  readonly live: LiveState;
}

export class RoomMembersSnapshot {
  constructor(opts: {
    members: RoomMember[];
    live: LiveState;
    joinRequests: JoinRequest[];
    joinRequestsError?: string | null;
  }) {
    this.members = opts.members;
    this.live = opts.live;
    this.joinRequests = opts.joinRequests;
    this.joinRequestsError = opts.joinRequestsError ?? null;
  }

  readonly members: RoomMember[];
  readonly live: LiveState;
  readonly joinRequests: JoinRequest[];
  readonly joinRequestsError: string | null;
}

export class RoomMembersLiveSnapshot {
  constructor(opts: { members: RoomMember[]; live: LiveState }) {
    this.members = opts.members;
    this.live = opts.live;
  }

  readonly members: RoomMember[];
  readonly live: LiveState;
}

export class RoomMembersDialogStatePatch {
  constructor(opts: {
    members: RoomMember[];
    requests: JoinRequest[];
    live: LiveState | null;
    loading: boolean;
    changed: boolean;
    error: string | null;
    requestError: string | null;
    busyRequestIds: Set<string>;
  }) {
    this.members = opts.members;
    this.requests = opts.requests;
    this.live = opts.live;
    this.loading = opts.loading;
    this.changed = opts.changed;
    this.error = opts.error;
    this.requestError = opts.requestError;
    this.busyRequestIds = opts.busyRequestIds;
  }

  readonly members: RoomMember[];
  readonly requests: JoinRequest[];
  readonly live: LiveState | null;
  readonly loading: boolean;
  readonly changed: boolean;
  readonly error: string | null;
  readonly requestError: string | null;
  readonly busyRequestIds: Set<string>;

  get hasPendingRequests(): boolean {
    return this.requests.length > 0;
  }

  get shouldNotifyPendingRequests(): boolean {
    return this.requestError == null;
  }
}

export function shouldSkipRoomOpenRequest(opts: {
  loadingRoom: boolean;
  selectedRoomId: string | null;
  roomId: string;
}): boolean {
  return opts.loadingRoom && opts.selectedRoomId === opts.roomId;
}

export function canApplyRoomOpenResult(opts: { requestedRoomId: string; selectedRoomId: string | null }): boolean {
  return opts.selectedRoomId === opts.requestedRoomId;
}

export function shouldShowOptimisticRoomOpenRefreshFailure(opts: {
  hasOptimisticDetail: boolean;
  requestedRoomId: string;
  selectedRoomId: string | null;
}): boolean {
  return (
    opts.hasOptimisticDetail &&
    canApplyRoomOpenResult({
      requestedRoomId: opts.requestedRoomId,
      selectedRoomId: opts.selectedRoomId,
    })
  );
}

export function shouldFinishRoomOpenLoading(opts: { requestedRoomId: string; selectedRoomId: string | null }): boolean {
  return canApplyRoomOpenResult({
    requestedRoomId: opts.requestedRoomId,
    selectedRoomId: opts.selectedRoomId,
  });
}

export function canApplyLiveRefresh(opts: { live: LiveState; selectedRoomId: string | null }): boolean {
  return opts.live.roomId === opts.selectedRoomId;
}

export function roomOpenSucceededEffects(opts: { snapshot: RoomOpenSnapshot; joinLive: boolean }): RoomOpenSuccessEffects {
  return new RoomOpenSuccessEffects({
    joinRequestBadgeRoom: opts.snapshot.detail,
    joinLiveSource: opts.joinLive ? 'room_card_speaker' : null,
  });
}

export class RoomsController {
  constructor(opts: { api: GangApi }) {
    this.api = opts.api;
  }

  readonly api: GangApi;

  async loadRooms(): Promise<RoomCard[]> {
    const page = await this.api.listRooms();
    return this.orderedRoomCards(page.rooms);
  }

  patchRoomListLoadStarted(opts: { rooms: RoomCard[] }): RoomListLoadPatch {
    return new RoomListLoadPatch({ rooms: opts.rooms, loading: true, error: null });
  }

  patchRoomListLoadSucceeded(opts: { rooms: RoomCard[] }): RoomListLoadPatch {
    return new RoomListLoadPatch({
      rooms: this.orderedRoomCards(opts.rooms),
      loading: false,
      error: null,
    });
  }

  patchRoomListLoadFailed(opts: { rooms: RoomCard[]; failure: unknown }): RoomListLoadPatch {
    return new RoomListLoadPatch({
      rooms: opts.rooms,
      loading: false,
      error: userFacingErrorMessage(opts.failure),
    });
  }

  async openRoom(roomId: string): Promise<RoomOpenSnapshot> {
    const detail = await this.api.getRoom(roomId);
    const messagePage = await this.api.listMessages({ roomId });
    const live = await this.api.getLiveState(roomId);
    return new RoomOpenSnapshot({
      detail,
      messages: messagePage.messages,
      live,
    });
  }

  patchRoomOpenStarted(opts: {
    roomId: string;
    currentSelectedRoom: RoomDetail | null;
    currentMessages: Message[];
    currentLive: LiveState | null;
    currentLivePanelOpen: boolean;
    joinLive: boolean;
    optimisticDetail?: RoomDetail | null;
  }): RoomOpenStatePatch {
    const optimisticDetail = opts.optimisticDetail ?? null;
    return new RoomOpenStatePatch({
      settingsOpen: false,
      selectedRoomId: opts.roomId,
      selectedRoom: optimisticDetail ?? opts.currentSelectedRoom,
      loadingRoom: true,
      error: null,
      selectedRoomHasPendingJoinRequests: false,
      messages: optimisticDetail == null ? opts.currentMessages : [],
      live: optimisticDetail?.live ?? opts.currentLive,
      livePanelOpen: opts.joinLive ? opts.currentLivePanelOpen : false,
    });
  }

  patchRoomOpenSucceeded(opts: {
    currentSettingsOpen: boolean;
    currentLoadingRoom: boolean;
    currentError: string | null;
    currentSelectedRoomHasPendingJoinRequests: boolean;
    snapshot: RoomOpenSnapshot;
    joinLive: boolean;
  }): RoomOpenStatePatch {
    const { snapshot } = opts;
    return new RoomOpenStatePatch({
      settingsOpen: opts.currentSettingsOpen,
      selectedRoomId: snapshot.detail.id,
      selectedRoom: snapshot.detail,
      loadingRoom: opts.currentLoadingRoom,
      error: opts.currentError,
      selectedRoomHasPendingJoinRequests: opts.currentSelectedRoomHasPendingJoinRequests,
      messages: snapshot.messages,
      live: snapshot.live,
      livePanelOpen: opts.joinLive,
    });
  }

  patchRoomOpenFailed(opts: {
    settingsOpen: boolean;
    selectedRoomId: string | null;
    selectedRoom: RoomDetail | null;
    loadingRoom: boolean;
    selectedRoomHasPendingJoinRequests: boolean;
    messages: Message[];
    live: LiveState | null;
    livePanelOpen: boolean;
    failure: unknown;
  }): RoomOpenStatePatch {
    return new RoomOpenStatePatch({
      settingsOpen: opts.settingsOpen,
      selectedRoomId: opts.selectedRoomId,
      selectedRoom: opts.selectedRoom,
      loadingRoom: opts.loadingRoom,
      error: userFacingErrorMessage(opts.failure),
      selectedRoomHasPendingJoinRequests: opts.selectedRoomHasPendingJoinRequests,
      messages: opts.messages,
      live: opts.live,
      livePanelOpen: opts.livePanelOpen,
    });
  }

  patchRoomOpenFinished(opts: {
    settingsOpen: boolean;
    selectedRoomId: string | null;
    selectedRoom: RoomDetail | null;
    error: string | null;
    selectedRoomHasPendingJoinRequests: boolean;
    messages: Message[];
    live: LiveState | null;
    livePanelOpen: boolean;
  }): RoomOpenStatePatch {
    return new RoomOpenStatePatch({
      settingsOpen: opts.settingsOpen,
      selectedRoomId: opts.selectedRoomId,
      selectedRoom: opts.selectedRoom,
      loadingRoom: false,
      error: opts.error,
      selectedRoomHasPendingJoinRequests: opts.selectedRoomHasPendingJoinRequests,
      messages: opts.messages,
      live: opts.live,
      livePanelOpen: opts.livePanelOpen,
    });
  }

  patchRoomMembersLoadStarted(opts: {
    members: RoomMember[];
    requests: JoinRequest[];
    live: LiveState | null;
    changed: boolean;
    busyRequestIds: Iterable<string>;
  }): RoomMembersDialogStatePatch {
    return new RoomMembersDialogStatePatch({
      members: opts.members,
      requests: opts.requests,
      live: opts.live,
      loading: true,
      changed: opts.changed,
      error: null,
      requestError: null,
      busyRequestIds: new Set(opts.busyRequestIds),
    });
  }

  patchRoomMembersLoadSucceeded(opts: {
    snapshot: RoomMembersSnapshot;
    changed: boolean;
    busyRequestIds: Iterable<string>;
  }): RoomMembersDialogStatePatch {
    const { snapshot } = opts;
    return new RoomMembersDialogStatePatch({
      members: snapshot.members,
      requests: snapshot.joinRequests,
      live: snapshot.live,
      loading: false,
      changed: opts.changed,
      error: null,
      requestError: snapshot.joinRequestsError,
      busyRequestIds: new Set(opts.busyRequestIds),
    });
  }

  patchRoomMembersLoadFailed(opts: {
    members: RoomMember[];
    requests: JoinRequest[];
    live: LiveState | null;
    changed: boolean;
    busyRequestIds: Iterable<string>;
    failure: unknown;
  }): RoomMembersDialogStatePatch {
    return new RoomMembersDialogStatePatch({
      members: opts.members,
      requests: opts.requests,
      live: opts.live,
      loading: false,
      changed: opts.changed,
      error: userFacingErrorMessage(opts.failure),
      requestError: null,
      busyRequestIds: new Set(opts.busyRequestIds),
    });
  }

  patchRoomMembersAndLiveReloadSucceeded(opts: {
    snapshot: RoomMembersLiveSnapshot;
    requests: JoinRequest[];
    loading: boolean;
    changed: boolean;
    requestError: string | null;
    busyRequestIds: Iterable<string>;
  }): RoomMembersDialogStatePatch {
    return new RoomMembersDialogStatePatch({
      members: opts.snapshot.members,
      requests: opts.requests,
      live: opts.snapshot.live,
      loading: opts.loading,
      changed: opts.changed,
      error: null,
      requestError: opts.requestError,
      busyRequestIds: new Set(opts.busyRequestIds),
    });
  }

  patchRoomMembersAndLiveReloadFailed(opts: {
    members: RoomMember[];
    requests: JoinRequest[];
    live: LiveState | null;
    loading: boolean;
    changed: boolean;
    requestError: string | null;
    busyRequestIds: Iterable<string>;
    failure: unknown;
  }): RoomMembersDialogStatePatch {
    return new RoomMembersDialogStatePatch({
      members: opts.members,
      requests: opts.requests,
      live: opts.live,
      loading: opts.loading,
      changed: opts.changed,
      error: userFacingErrorMessage(opts.failure),
      requestError: opts.requestError,
      busyRequestIds: new Set(opts.busyRequestIds),
    });
  }

  patchRoomJoinRequestsReloadSucceeded(opts: {
    members: RoomMember[];
    live: LiveState | null;
    requests: JoinRequest[];
    loading: boolean;
    changed: boolean;
    busyRequestIds: Iterable<string>;
  }): RoomMembersDialogStatePatch {
    return new RoomMembersDialogStatePatch({
      members: opts.members,
      requests: opts.requests,
      live: opts.live,
      loading: opts.loading,
      changed: opts.changed,
      error: null,
      requestError: null,
      busyRequestIds: new Set(opts.busyRequestIds),
    });
  }

  patchRoomJoinRequestsReloadFailed(opts: {
    members: RoomMember[];
    requests: JoinRequest[];
    live: LiveState | null;
    loading: boolean;
    changed: boolean;
    busyRequestIds: Iterable<string>;
    failure: unknown;
  }): RoomMembersDialogStatePatch {
    return new RoomMembersDialogStatePatch({
      members: opts.members,
      requests: opts.requests,
      live: opts.live,
      loading: opts.loading,
      changed: opts.changed,
      error: null,
      requestError: userFacingErrorMessage(opts.failure),
      busyRequestIds: new Set(opts.busyRequestIds),
    });
  }

  patchJoinRequestReviewStarted(opts: {
    members: RoomMember[];
    requests: JoinRequest[];
    live: LiveState | null;
    loading: boolean;
    changed: boolean;
    requestId: string;
    busyRequestIds: Iterable<string>;
  }): RoomMembersDialogStatePatch {
    const started = room_join_requests.joinRequestReviewStarted({
      requests: opts.requests,
      requestId: opts.requestId,
      busyRequestIds: opts.busyRequestIds,
    });
    return new RoomMembersDialogStatePatch({
      members: opts.members,
      requests: started.requests,
      live: opts.live,
      loading: opts.loading,
      changed: opts.changed,
      error: null,
      requestError: null,
      busyRequestIds: started.busyRequestIds,
    });
  }

  patchJoinRequestReviewSucceeded(opts: {
    members: RoomMember[];
    requests: JoinRequest[];
    live: LiveState | null;
    loading: boolean;
    changed: boolean;
    error: string | null;
    requestError: string | null;
    requestId: string;
    busyRequestIds: Iterable<string>;
  }): RoomMembersDialogStatePatch {
    const succeeded = room_join_requests.joinRequestReviewSucceeded({
      requests: opts.requests,
      requestId: opts.requestId,
      busyRequestIds: opts.busyRequestIds,
    });
    return new RoomMembersDialogStatePatch({
      members: opts.members,
      requests: succeeded.requests,
      live: opts.live,
      loading: opts.loading,
      changed: opts.changed || succeeded.changed,
      error: opts.error,
      requestError: opts.requestError,
      busyRequestIds: succeeded.busyRequestIds,
    });
  }

  patchJoinRequestReviewFailed(opts: {
    members: RoomMember[];
    requests: JoinRequest[];
    live: LiveState | null;
    loading: boolean;
    changed: boolean;
    error: string | null;
    requestId: string;
    busyRequestIds: Iterable<string>;
    failure: unknown;
  }): RoomMembersDialogStatePatch {
    const failed = room_join_requests.joinRequestReviewFailed({
      requests: opts.requests,
      requestId: opts.requestId,
      busyRequestIds: opts.busyRequestIds,
    });
    return new RoomMembersDialogStatePatch({
      members: opts.members,
      requests: failed.requests,
      live: opts.live,
      loading: opts.loading,
      changed: opts.changed,
      error: opts.error,
      requestError: userFacingErrorMessage(opts.failure),
      busyRequestIds: failed.busyRequestIds,
    });
  }

  getRoom(roomId: string): Promise<RoomDetail> {
    return this.api.getRoom(roomId);
  }

  getRoomMemberProfile(opts: { roomId: string; userId: string }): Promise<RoomMemberProfile> {
    return this.api.getRoomMemberProfile({ roomId: opts.roomId, userId: opts.userId });
  }

  getUserProfile(userId: string): Promise<UserSummary> {
    return this.api.getUserProfile(userId);
  }

  createRoom(opts: {
    name: string;
    description?: string | null;
    visibility?: string | null;
    joinPolicy?: string | null;
    aiVoiceAnnouncementsEnabled?: boolean | null;
    avatarAssetId?: string | null;
    defaultAvatarKey?: string | null;
  }): Promise<RoomDetail> {
    return this.api.createRoom({
      name: opts.name,
      description: opts.description ?? undefined,
      visibility: opts.visibility ?? undefined,
      joinPolicy: opts.joinPolicy ?? undefined,
      aiVoiceAnnouncementsEnabled: opts.aiVoiceAnnouncementsEnabled ?? undefined,
      avatarAssetId: opts.avatarAssetId ?? undefined,
      defaultAvatarKey: opts.defaultAvatarKey ?? undefined,
    });
  }

  uploadImageAsset(opts: { bytes: Uint8Array; filename: string; purpose?: string }): Promise<UploadedAsset> {
    const { bytes, filename, purpose = 'avatar' } = opts;
    return this.api.uploadImageAsset({ bytes, filename, purpose });
  }

  updateMyRoomSettings(opts: {
    roomId: string;
    remarkName?: string | null;
    notificationPolicy?: string | null;
    roomDisplayName?: string | null;
    isPinned?: boolean | null;
    aiVoiceAnnouncementsEnabled?: boolean | null;
  }): Promise<RoomDetail> {
    return this.api.updateMyRoomSettings({
      roomId: opts.roomId,
      remarkName: opts.remarkName ?? undefined,
      notificationPolicy: opts.notificationPolicy ?? undefined,
      roomDisplayName: opts.roomDisplayName ?? undefined,
      isPinned: opts.isPinned ?? undefined,
      aiVoiceAnnouncementsEnabled: opts.aiVoiceAnnouncementsEnabled ?? undefined,
    });
  }

  leaveRoom(opts: { roomId: string; confirmDeleteIfEmpty?: boolean }): Promise<void> {
    const { roomId, confirmDeleteIfEmpty = false } = opts;
    return this.api.leaveRoom({ roomId, confirmDeleteIfEmpty });
  }

  updateRoom(opts: {
    roomId: string;
    name?: string | null;
    description?: string | null;
    visibility?: string | null;
    joinPolicy?: string | null;
    aiVoiceAnnouncementsEnabled?: boolean | null;
    gangBotEnabled?: boolean | null;
    avatarAssetId?: string | null;
    defaultAvatarKey?: string | null;
  }): Promise<RoomDetail> {
    return this.api.updateRoom({
      roomId: opts.roomId,
      name: opts.name ?? undefined,
      description: opts.description ?? undefined,
      visibility: opts.visibility ?? undefined,
      joinPolicy: opts.joinPolicy ?? undefined,
      aiVoiceAnnouncementsEnabled: opts.aiVoiceAnnouncementsEnabled ?? undefined,
      gangBotEnabled: opts.gangBotEnabled ?? undefined,
      avatarAssetId: opts.avatarAssetId ?? undefined,
      defaultAvatarKey: opts.defaultAvatarKey ?? undefined,
    });
  }

  deleteRoom(opts: { roomId: string; confirmName: string }): Promise<void> {
    return this.api.deleteRoom({ roomId: opts.roomId, confirmName: opts.confirmName });
  }

  updateRoomMemberRole(opts: { roomId: string; userId: string; role: string }): Promise<RoomMember> {
    return this.api.updateRoomMemberRole({ roomId: opts.roomId, userId: opts.userId, role: opts.role });
  }

  updateRoomMemberRoomDisplayName(opts: {
    roomId: string;
    userId: string;
    roomDisplayName: string;
  }): Promise<RoomMember> {
    return this.api.updateRoomMemberRoomDisplayName({
      roomId: opts.roomId,
      userId: opts.userId,
      roomDisplayName: opts.roomDisplayName,
    });
  }

  removeRoomMember(opts: { roomId: string; userId: string }): Promise<void> {
    return this.api.removeRoomMember({ roomId: opts.roomId, userId: opts.userId });
  }

  transferRoomCreator(opts: { roomId: string; userId: string }): Promise<RoomDetail> {
    return this.api.transferRoomCreator({ roomId: opts.roomId, userId: opts.userId });
  }

  listRoomStickerPacks(roomId: string): Promise<StickerPack[]> {
    return this.api.listStickerPacks({ scope: 'room', roomId });
  }

  createRoomStickerPack(opts: { roomId: string; name: string; sortOrder?: number | null }): Promise<StickerPack> {
    return this.api.createStickerPack({
      name: opts.name,
      scope: 'room',
      roomId: opts.roomId,
      sortOrder: opts.sortOrder ?? undefined,
    });
  }

  addRoomSticker(opts: {
    roomId: string;
    packId: string;
    assetId: string;
    name: string;
    sortOrder?: number | null;
  }): Promise<void> {
    return this.api.addSticker({
      packId: opts.packId,
      assetId: opts.assetId,
      name: opts.name,
      sortOrder: opts.sortOrder ?? undefined,
      scope: 'room',
      roomId: opts.roomId,
    });
  }

  deleteRoomSticker(opts: { roomId: string; packId: string; stickerId: string }): Promise<void> {
    return this.api.deleteSticker({
      packId: opts.packId,
      stickerId: opts.stickerId,
      scope: 'room',
      roomId: opts.roomId,
    });
  }

  updateSticker(opts: {
    packId: string;
    stickerId: string;
    name?: string | null;
    sortOrder?: number | null;
  }): Promise<Sticker> {
    return this.api.updateSticker({
      packId: opts.packId,
      stickerId: opts.stickerId,
      name: opts.name ?? undefined,
      sortOrder: opts.sortOrder ?? undefined,
    });
  }

  reorderStickers(opts: { packId: string; stickerIds: string[] }): Promise<StickerPack> {
    return this.api.reorderStickers({ packId: opts.packId, stickerIds: opts.stickerIds });
  }

  downloadStickers(opts: { stickerIds: string[] }): Promise<DownloadedFile> {
    return this.api.downloadStickers({ stickerIds: opts.stickerIds });
  }

  searchRooms(opts: { query: string }): Promise<PublicRoom[]> {
    return this.api.searchRooms({ query: opts.query });
  }

  joinRoom(roomId: string, opts: { reason?: string | null } = {}): Promise<JoinRoomResult> {
    return this.api.joinRoom(roomId, { reason: opts.reason ?? undefined });
  }

  listRoomInvites(opts: { status?: string } = {}): Promise<RoomInvite[]> {
    const { status = 'pending' } = opts;
    return this.api.listRoomInvites({ status });
  }

  listRoomApplications(opts: { status?: string } = {}): Promise<RoomApplication[]> {
    const { status = 'pending' } = opts;
    return this.api.listRoomApplications({ status });
  }

  listRoomNotifications(): Promise<RoomEventNotification[]> {
    return this.api.listRoomNotifications();
  }

  deleteRoomNotification(opts: { notificationType: string; notificationId: string }): Promise<void> {
    return this.api.deleteRoomNotification({
      notificationType: opts.notificationType,
      notificationId: opts.notificationId,
    });
  }

  markRoomNotificationsRead(): Promise<void> {
    return this.api.markRoomNotificationsRead();
  }

  listRoomMembers(roomId: string, opts: { limit?: number; cursor?: string | null } = {}): Promise<RoomMemberPage> {
    const { limit = 100, cursor } = opts;
    return this.api.listRoomMembers(roomId, { limit, cursor: cursor ?? undefined });
  }

  async loadAllRoomMembers(roomId: string, opts: { pageLimit?: number; maxPages?: number } = {}): Promise<RoomMember[]> {
    const { pageLimit = 100, maxPages = 50 } = opts;
    const members: RoomMember[] = [];
    let cursor: string | null = null;
    let pageCount = 0;
    do {
      const page: RoomMemberPage = await this.api.listRoomMembers(roomId, {
        limit: pageLimit,
        cursor: cursor ?? undefined,
      });
      members.push(...page.members);
      cursor = _nonEmpty(page.nextCursor);
      pageCount += 1;
    } while (cursor != null && pageCount < maxPages);
    return members;
  }

  async loadRoomMembersAndLive(opts: { roomId: string; fallbackLive: LiveState }): Promise<RoomMembersLiveSnapshot> {
    const members = await this.loadAllRoomMembers(opts.roomId);
    const live = await this.getLiveStateOr(opts.roomId, opts.fallbackLive);
    return new RoomMembersLiveSnapshot({ members, live });
  }

  async loadRoomMembersSnapshot(opts: {
    roomId: string;
    fallbackLive: LiveState;
    includeJoinRequests: boolean;
  }): Promise<RoomMembersSnapshot> {
    const membersAndLive = await this.loadRoomMembersAndLive({
      roomId: opts.roomId,
      fallbackLive: opts.fallbackLive,
    });

    let joinRequests: JoinRequest[] = [];
    let joinRequestsError: string | null = null;
    if (opts.includeJoinRequests) {
      try {
        joinRequests = await this.listJoinRequests(opts.roomId);
      } catch (e) {
        joinRequestsError = String(e);
      }
    }

    return new RoomMembersSnapshot({
      members: membersAndLive.members,
      live: membersAndLive.live,
      joinRequests,
      joinRequestsError,
    });
  }

  inviteMember(opts: { roomId: string; userId: string }): Promise<RoomInvite> {
    return this.api.inviteMember({ roomId: opts.roomId, userId: opts.userId });
  }

  listRoomBlacklist(roomId: string): Promise<RoomBlacklistEntry[]> {
    return this.api.listRoomBlacklist(roomId);
  }

  blockRoomUser(opts: { roomId: string; userId: string }): Promise<RoomBlacklistEntry> {
    return this.api.blockRoomUser({ roomId: opts.roomId, userId: opts.userId });
  }

  unblockRoomUser(opts: { roomId: string; userId: string }): Promise<void> {
    return this.api.unblockRoomUser({ roomId: opts.roomId, userId: opts.userId });
  }

  searchUsers(opts: { query: string; limit?: number }): Promise<UserSummary[]> {
    const { query, limit = 20 } = opts;
    return this.api.searchUsers({ query, limit });
  }

  listJoinRequests(roomId: string, opts: { status?: string } = {}): Promise<JoinRequest[]> {
    const { status = 'pending' } = opts;
    return this.api.listJoinRequests(roomId, { status });
  }

  reviewRoomInvite(opts: { inviteId: string; accept: boolean; reason?: string | null }): Promise<JoinRoomResult> {
    return this.api.reviewRoomInvite({
      inviteId: opts.inviteId,
      accept: opts.accept,
      reason: opts.reason ?? undefined,
    });
  }

  withdrawRoomApplication(opts: { requestId: string }): Promise<RoomApplication> {
    return this.api.withdrawRoomApplication({ requestId: opts.requestId });
  }

  reviewJoinRequest(opts: { roomId: string; requestId: string; approve: boolean }): Promise<void> {
    return this.api.reviewJoinRequest({
      roomId: opts.roomId,
      requestId: opts.requestId,
      approve: opts.approve,
    });
  }

  async hasPendingRoomInvites(): Promise<boolean> {
    const invites = await this.api.listRoomInvites();
    return invites.length > 0;
  }

  async hasPendingJoinRequests(room: RoomDetail, opts: { canReviewJoinRequests: boolean }): Promise<boolean> {
    if (!opts.canReviewJoinRequests) return false;
    const requests = await this.api.listJoinRequests(room.id);
    return requests.length > 0;
  }

  getLiveState(roomId: string): Promise<LiveState> {
    return this.api.getLiveState(roomId);
  }

  async getLiveStateOr(roomId: string, fallbackLive: LiveState): Promise<LiveState> {
    try {
      return await this.api.getLiveState(roomId);
    } catch {
      return fallbackLive;
    }
  }

  roomCardFromSnapshot(data: JsonMap): RoomCard | null {
    if (typeof data['id'] !== 'string') return null;
    try {
      return RoomCard.fromJson(data);
    } catch {
      return null;
    }
  }

  upsertRoomCard(rooms: RoomCard[], room: RoomCard): RoomCard[] {
    const idx = rooms.findIndex((item) => item.id === room.id);
    if (idx < 0) return this.orderedRoomCards([room, ...rooms]);
    const next = [...rooms];
    next[idx] = room;
    return this.orderedRoomCards(next);
  }

  orderedRoomCards(rooms: readonly RoomCard[]): RoomCard[] {
    const indexed = rooms.map((room, index) => ({ index, room }));
    indexed.sort((a, b) => {
      const pinned = _compare(this._roomPinnedRank(a.room), this._roomPinnedRank(b.room));
      if (pinned !== 0) return pinned;

      const unread = _compare(this._roomUnreadRank(a.room), this._roomUnreadRank(b.room));
      if (unread !== 0) return unread;

      const live = _compare(this._roomLiveRank(a.room), this._roomLiveRank(b.room));
      if (live !== 0) return live;

      const activity = _compare(this._roomActivityTime(b.room).getTime(), this._roomActivityTime(a.room).getTime());
      if (activity !== 0) return activity;

      return _compare(a.index, b.index);
    });
    return indexed.map((item) => item.room);
  }

  private _roomPinnedRank(room: RoomCard): number {
    return room.isPinned ? 0 : 1;
  }

  private _roomUnreadRank(room: RoomCard): number {
    if (room.unreadCount <= 0) return 2;
    const policy = room_display.normalizeRoomNotificationPolicy(room.notificationPolicy);
    if (policy === 'silent') return 1;
    if (policy === 'blocked') return 2;
    return 0;
  }

  private _roomLiveRank(room: RoomCard): number {
    return room.liveParticipantCount > 0 ? 0 : 1;
  }

  private _roomActivityTime(room: RoomCard): Date {
    if (room_display.normalizeRoomNotificationPolicy(room.notificationPolicy) === 'blocked') {
      return room.updatedAt;
    }
    return room.lastMessage?.createdAt ?? room.updatedAt;
  }

  patchRoomCardsRefreshed(opts: { rooms: RoomCard[] }): RoomCardsPatch {
    return new RoomCardsPatch({ rooms: this.orderedRoomCards(opts.rooms) });
  }

  patchRoomCardUpserted(opts: { rooms: RoomCard[]; room: RoomCard }): RoomCardsPatch {
    return new RoomCardsPatch({ rooms: this.upsertRoomCard(opts.rooms, opts.room) });
  }

  patchRoomCardUpdated(opts: { rooms: RoomCard[]; incoming: RoomCard; clearUnread?: boolean }): RoomCardsPatch {
    const { rooms, incoming, clearUnread = false } = opts;
    return new RoomCardsPatch({
      rooms: this.mergeRoomUpdated(rooms, incoming, { clearUnread }),
    });
  }

  patchRoomUpdated(opts: { rooms: RoomCard[]; incoming: RoomCard; selectedRoom: RoomDetail | null }): RoomUpdatedPatch {
    const { rooms, incoming } = opts;
    const nextRooms = this.patchRoomCardUpdated({ rooms, incoming }).rooms;
    const current = opts.selectedRoom;
    if (current == null || current.id !== incoming.id) {
      return new RoomUpdatedPatch({
        rooms: nextRooms,
        selectedRoom: current,
        shouldReloadMembers: false,
      });
    }
    return new RoomUpdatedPatch({
      rooms: nextRooms,
      selectedRoom: this._mergeSelectedRoomSnapshot(current, incoming),
      shouldReloadMembers:
        current.memberCount !== incoming.memberCount || current.onlineMemberCount !== incoming.onlineMemberCount,
    });
  }

  patchRoomDetailApplied(opts: { rooms: RoomCard[]; detail: RoomDetail }): RoomSelectedDetailPatch {
    const { rooms, detail } = opts;
    const existingIndex = rooms.findIndex((room) => room.id === detail.id);
    const existing = existingIndex < 0 ? null : rooms[existingIndex]!;
    const card = detail.toCard();
    return new RoomSelectedDetailPatch({
      selectedRoom: detail,
      rooms: this.upsertRoomCard(
        rooms,
        existing == null
          ? card
          : new RoomCard({
              id: card.id,
              name: card.name,
              rid: card.rid,
              visibility: card.visibility,
              remarkName: card.remarkName ?? undefined,
              description: card.description,
              notificationPolicy: card.notificationPolicy,
              isPinned: card.isPinned,
              avatarUrl: card.avatarUrl,
              defaultAvatarKey: card.defaultAvatarKey,
              memberCount: card.memberCount,
              onlineMemberCount: card.onlineMemberCount,
              liveParticipantCount: card.liveParticipantCount,
              liveAvatarPreview: card.liveAvatarPreview,
              lastMessage: existing.lastMessage,
              unreadCount: existing.unreadCount,
              hasUnreadCount: existing.hasUnreadCount,
              unreadMentionCount: existing.unreadMentionCount,
              hasUnreadMentionCount: existing.hasUnreadMentionCount,
              hasPendingJoinRequests: existing.hasPendingJoinRequests,
              aiVoiceAnnouncementsEnabled: card.aiVoiceAnnouncementsEnabled,
              updatedAt: card.updatedAt,
            }),
      ),
    });
  }

  patchRoomUnreadCleared(opts: { rooms: RoomCard[]; roomId: string }): RoomCardsPatch {
    const { rooms, roomId } = opts;
    const idx = rooms.findIndex((room) => room.id === roomId);
    if (idx < 0 || (rooms[idx]!.unreadCount === 0 && rooms[idx]!.unreadMentionCount === 0)) {
      return new RoomCardsPatch({ rooms });
    }
    const next = [...rooms];
    next[idx] = next[idx]!.copyWith({ unreadCount: 0, unreadMentionCount: 0 });
    return new RoomCardsPatch({ rooms: this.orderedRoomCards(next) });
  }

  patchSelectedRoomDetailRefreshed(opts: {
    rooms: RoomCard[];
    selectedRoomId: string | null;
    detail: RoomDetail;
  }): RoomSelectedDetailPatch | null {
    if (opts.selectedRoomId !== opts.detail.id) return null;
    return this.patchRoomDetailApplied({ rooms: opts.rooms, detail: opts.detail });
  }

  patchSelectedLiveRefreshed(opts: {
    live: LiveState;
    selectedRoomId: string | null;
    currentLive?: LiveState | null;
  }): RoomLiveRefreshPatch | null {
    const { live, selectedRoomId } = opts;
    if (!canApplyLiveRefresh({ live, selectedRoomId })) {
      return null;
    }
    if (this._isOlderLiveSnapshot(live, opts.currentLive ?? null)) return null;
    return new RoomLiveRefreshPatch({ live });
  }

  removeRoomCard(rooms: readonly RoomCard[], roomId: string): RoomCard[] {
    return rooms.filter((room) => room.id !== roomId);
  }

  patchRoomDeleted(opts: {
    rooms: RoomCard[];
    selectedRoomId: string | null;
    selectedRoom: RoomDetail | null;
    selectedRoomHasPendingJoinRequests: boolean;
    messages: Message[];
    live: LiveState | null;
    livePanelOpen: boolean;
    settingsOpen: boolean;
    joinedLiveRoomId: string | null;
    data: JsonMap;
  }): RoomDeletedPatch | null {
    const roomId = asString(opts.data['room_id']);
    if (roomId == null) return null;

    const wasSelected = opts.selectedRoomId === roomId;
    const shouldDisconnectLive = opts.joinedLiveRoomId === roomId;
    return new RoomDeletedPatch({
      roomId,
      rooms: this.removeRoomCard(opts.rooms, roomId),
      selectedRoomId: wasSelected ? null : opts.selectedRoomId,
      selectedRoom: wasSelected ? null : opts.selectedRoom,
      selectedRoomHasPendingJoinRequests: wasSelected ? false : opts.selectedRoomHasPendingJoinRequests,
      messages: wasSelected ? [] : opts.messages,
      live: wasSelected ? null : opts.live,
      livePanelOpen: wasSelected ? false : opts.livePanelOpen,
      settingsOpen: wasSelected ? false : opts.settingsOpen,
      joinedLiveRoomId: shouldDisconnectLive ? null : opts.joinedLiveRoomId,
      wasSelected,
      shouldDisconnectLive,
    });
  }

  patchRoomRoleChanged(opts: { selectedRoom: RoomDetail | null; data: JsonMap }): RoomRoleChangedPatch | null {
    const roomId = asString(opts.data['room_id']);
    const role = asString(opts.data['role']);
    const current = opts.selectedRoom;
    if (roomId == null || role == null || current == null) return null;
    if (current.id !== roomId) return null;
    return new RoomRoleChangedPatch({
      roomId,
      selectedRoom: current.copyWithRole(role),
    });
  }

  removeRoomInvite(invites: readonly RoomInvite[], inviteId: string): RoomInvite[] {
    return invites.filter((invite) => invite.id !== inviteId);
  }

  removeJoinRequest(requests: readonly JoinRequest[], requestId: string): JoinRequest[] {
    return requests.filter((request) => request.id !== requestId);
  }

  mergeRoomUpdated(rooms: RoomCard[], incoming: RoomCard, opts: { clearUnread?: boolean } = {}): RoomCard[] {
    const { clearUnread = false } = opts;
    const idx = rooms.findIndex((room) => room.id === incoming.id);
    if (idx < 0) return this.upsertRoomCard(rooms, incoming);

    const existing = rooms[idx]!;
    const next = [...rooms];
    // Public room snapshots do not carry local per-user fields, so keep the
    // local unread count, but bump it when a fresh last_message arrives for a
    // room the user is not currently reading.
    const unreadCount = clearUnread ? 0 : this._mergedUnreadCount({ existing, incoming });
    const unreadMentionCount = clearUnread ? 0 : this._mergedUnreadMentionCount({ existing, incoming });
    next[idx] = this._mergeRoomCard(existing, incoming, { unreadCount, unreadMentionCount });
    return this.orderedRoomCards(next);
  }

  private _mergedUnreadCount(opts: { existing: RoomCard; incoming: RoomCard }): number {
    const { existing, incoming } = opts;
    if (incoming.hasUnreadCount) return incoming.unreadCount;
    const incomingLastMessage = incoming.lastMessage;
    if (incomingLastMessage == null) return existing.unreadCount;
    if (room_display.normalizeRoomNotificationPolicy(existing.notificationPolicy) === 'blocked') {
      return 0;
    }
    if (existing.lastMessage?.id === incomingLastMessage.id) {
      return existing.unreadCount;
    }
    return existing.unreadCount + 1;
  }

  private _mergedUnreadMentionCount(opts: { existing: RoomCard; incoming: RoomCard }): number {
    const { existing, incoming } = opts;
    if (incoming.hasUnreadMentionCount) return incoming.unreadMentionCount;
    return existing.unreadMentionCount;
  }

  private _mergeRoomCard(
    existing: RoomCard,
    incoming: RoomCard,
    opts: { unreadCount: number; unreadMentionCount: number },
  ): RoomCard {
    return new RoomCard({
      id: incoming.id,
      name: incoming.name,
      rid: incoming.rid,
      visibility: incoming.visibility,
      remarkName: existing.remarkName ?? undefined,
      description: incoming.description,
      notificationPolicy: existing.notificationPolicy,
      isPinned: existing.isPinned,
      avatarUrl: incoming.avatarUrl,
      defaultAvatarKey: incoming.defaultAvatarKey,
      memberCount: incoming.memberCount,
      onlineMemberCount: incoming.onlineMemberCount,
      liveParticipantCount: incoming.liveParticipantCount,
      liveAvatarPreview: incoming.liveAvatarPreview,
      lastMessage: incoming.lastMessage,
      unreadCount: opts.unreadCount,
      hasUnreadCount: true,
      unreadMentionCount: opts.unreadMentionCount,
      hasUnreadMentionCount: true,
      hasPendingJoinRequests: incoming.hasPendingJoinRequests,
      aiVoiceAnnouncementsEnabled: incoming.aiVoiceAnnouncementsEnabled,
      gangBotAvailable: incoming.gangBotAvailable,
      gangBotEnabled: incoming.gangBotEnabled,
      updatedAt: incoming.updatedAt,
    });
  }

  private _mergeSelectedRoomSnapshot(room: RoomDetail, incoming: RoomCard): RoomDetail {
    return new RoomDetail({
      id: room.id,
      name: incoming.name,
      rid: incoming.rid,
      visibility: incoming.visibility,
      description: incoming.description,
      joinPolicy: room.joinPolicy,
      remarkName: room.remarkName ?? undefined,
      notificationPolicy: room.notificationPolicy,
      isPinned: room.isPinned,
      personalProfile: room.personalProfile,
      aiVoiceAnnouncementsEnabled: incoming.aiVoiceAnnouncementsEnabled,
      messageRecallPolicy: room.messageRecallPolicy,
      messageRecallWindowSeconds: room.messageRecallWindowSeconds ?? undefined,
      canDeleteRoom: room.canDeleteRoom ?? undefined,
      gangBotAvailable: incoming.gangBotAvailable,
      gangBotEnabled: incoming.gangBotEnabled,
      avatarUrl: incoming.avatarUrl,
      defaultAvatarKey: incoming.defaultAvatarKey,
      memberCount: incoming.memberCount,
      onlineMemberCount: incoming.onlineMemberCount,
      createdBy: room.createdBy ?? undefined,
      myMembership: room.myMembership,
      live: room.live,
      createdAt: room.createdAt,
      updatedAt: incoming.updatedAt,
    });
  }

  patchLiveSnapshot(opts: {
    rooms: RoomCard[];
    selectedRoomId: string | null;
    data: JsonMap;
    joinedLiveRoomId?: string | null;
    currentUserId?: string | null;
    previousLive?: LiveState | null;
    localParticipantConnected?: boolean;
  }): RoomLiveSnapshotPatch | null {
    const { rooms, selectedRoomId, data, localParticipantConnected = false } = opts;
    const joinedLiveRoomId = opts.joinedLiveRoomId ?? null;
    const currentUserId = opts.currentUserId ?? null;
    const previousLive = opts.previousLive ?? null;

    const roomId = asString(data['room_id']);
    if (roomId == null) return null;

    const liveJson = asMap(data['live']);
    const count = asInt(data['participant_count']);
    const preview =
      asList(data['preview']) == null ? null : mapList(data['preview'], (json) => UserSummary.fromJson(json));
    const parsedSelectedLive = selectedRoomId === roomId && liveJson != null ? LiveState.fromJson(liveJson) : null;
    const staleSelectedLive = parsedSelectedLive != null && this._isOlderLiveSnapshot(parsedSelectedLive, previousLive);

    let nextRooms = rooms;
    const idx = rooms.findIndex((room) => room.id === roomId);
    if (idx >= 0 && count != null) {
      const existing = rooms[idx]!;
      const effectiveCount = staleSelectedLive ? previousLive!.participantCount : count;
      nextRooms = [...rooms];
      nextRooms[idx] = new RoomCard({
        id: existing.id,
        name: existing.name,
        rid: existing.rid,
        visibility: existing.visibility,
        remarkName: existing.remarkName ?? undefined,
        description: existing.description,
        notificationPolicy: existing.notificationPolicy,
        isPinned: existing.isPinned,
        avatarUrl: existing.avatarUrl,
        defaultAvatarKey: existing.defaultAvatarKey,
        memberCount: existing.memberCount,
        onlineMemberCount: existing.onlineMemberCount,
        liveParticipantCount: effectiveCount,
        liveAvatarPreview: staleSelectedLive ? existing.liveAvatarPreview : (preview ?? existing.liveAvatarPreview),
        lastMessage: existing.lastMessage,
        unreadCount: existing.unreadCount,
        hasUnreadCount: existing.hasUnreadCount,
        unreadMentionCount: existing.unreadMentionCount,
        hasUnreadMentionCount: existing.hasUnreadMentionCount,
        hasPendingJoinRequests: existing.hasPendingJoinRequests,
        aiVoiceAnnouncementsEnabled: existing.aiVoiceAnnouncementsEnabled,
        updatedAt: existing.updatedAt,
      });
      nextRooms = this.orderedRoomCards(nextRooms);
    }

    let selectedLive = staleSelectedLive ? null : parsedSelectedLive;
    if (selectedLive != null && previousLive?.roomId === roomId) {
      selectedLive = this._mergeLiveParticipantUsers(selectedLive, { previousLive: previousLive! });
    }

    // Self-preservation: a snapshot computed server-side before our own join
    // (SSE delivery and the LiveKit webhook can lag/reorder) can arrive right
    // after we join and drop us from the roster — making us briefly invisible
    // in our own channel. While we're joined to this room, if the snapshot
    // omits us but our prior state had us, re-insert our own participant from
    // that prior state. A later, correct snapshot reconciles.
    if (selectedLive != null && joinedLiveRoomId === roomId && currentUserId != null && localParticipantConnected) {
      const present = selectedLive.participants.some((p) => p.user.id === currentUserId);
      if (!present) {
        let self: LiveParticipant | null = null;
        if (previousLive?.roomId === roomId) {
          for (const p of previousLive!.participants) {
            if (p.user.id === currentUserId) {
              self = p;
              break;
            }
          }
        }
        if (self != null) {
          selectedLive = new LiveState({
            roomId: selectedLive.roomId,
            participantCount: selectedLive.participantCount + 1,
            participants: [...selectedLive.participants, self],
            updatedAt: selectedLive.updatedAt,
          });
        }
      }
    }

    // The selected-room roster may have been reconciled above (most notably
    // by retaining a locally connected participant while an older server
    // snapshot is in flight). Derive the room-card count and preview from that
    // exact same snapshot so the outside and inside views cannot diverge.
    if (selectedLive != null) {
      nextRooms = room_live_state.patchRoomLiveCount({
        rooms: nextRooms,
        roomId,
        live: selectedLive,
      });
    }

    return new RoomLiveSnapshotPatch({
      roomId,
      rooms: nextRooms,
      selectedLive,
    });
  }

  private _isOlderLiveSnapshot(incoming: LiveState, current: LiveState | null): boolean {
    return current?.roomId === incoming.roomId && incoming.updatedAt.getTime() < current!.updatedAt.getTime();
  }

  private _mergeLiveParticipantUsers(live: LiveState, opts: { previousLive: LiveState }): LiveState {
    const previousByUserId = new Map<string, LiveParticipant>();
    for (const participant of opts.previousLive.participants) {
      previousByUserId.set(participant.user.id, participant);
    }
    let changed = false;
    const participants = live.participants.map((participant) => {
      const previous = previousByUserId.get(participant.user.id);
      if (previous == null) return participant;
      return this._mergeLiveParticipantUser(participant, previous, {
        changed: () => {
          changed = true;
        },
      });
    });
    if (!changed) return live;
    return new LiveState({
      roomId: live.roomId,
      participantCount: live.participantCount,
      participants,
      updatedAt: live.updatedAt,
    });
  }

  private _mergeLiveParticipantUser(
    participant: LiveParticipant,
    previous: LiveParticipant,
    opts: { changed: () => void },
  ): LiveParticipant {
    const mergedUser = participant.user.mergeMissing(previous.user);
    if (mergedUser === participant.user) return participant;
    opts.changed();
    return participant.copyWith({ user: mergedUser });
  }

  patchRoomLiveCount(rooms: RoomCard[], roomId: string, live: LiveState): RoomCard[] {
    return room_live_state.patchRoomLiveCount({ rooms, roomId, live });
  }

  withLive(room: RoomCard, live: LiveState): RoomCard {
    return room_live_state.roomCardWithLive(room, live);
  }
}

function _nonEmpty(value: string | null | undefined): string | null {
  const text = value?.trim();
  if (text == null || text.length === 0) return null;
  return text;
}

function _compare(a: number, b: number): number {
  return a < b ? -1 : a > b ? 1 : 0;
}
