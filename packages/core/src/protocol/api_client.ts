import { Completer, delay } from '../lib/async';
import {
  asInt,
  asList,
  asString,
  isJsonMap,
  JsonFormatException,
  mapList,
  requireBool,
  requireInt,
  requireMap,
  type JsonMap,
} from '../lib/json';
import {
  buildMultipartBody,
  ClientException,
  HttpClientHelpers,
  responseFromStream,
  type HttpClient,
  type HttpHeaders,
  type HttpResponse,
  type MultipartFile,
} from '../platform/http';
import { platform } from '../platform/platform';
import { localizedServerErrorMessage } from './error_messages';
import {
  AppVersionInfo,
  CurrentUser,
  GlobalSearchResults,
  JoinRequest,
  JoinRoomResult,
  LiveJoinResult,
  LiveMemberVolume,
  LiveParticipant,
  LiveState,
  Message,
  MessageAttachment,
  MessageRecallRequest,
  MessageRecallResult,
  MusicBoxActiveSourceType,
  musicBoxActiveSourceTypeValue,
  MusicBoxSearchResult,
  MusicBoxState,
  PersonalMusicPlaylist,
  PersonalMusicPlaylistBatchAddResult,
  PersonalMusicPlaylistItem,
  PersonalMusicPlaylistItemsPage,
  PersonalMusicPlaylistMergeResult,
  PersonalMusicPlaylistPage,
  PublicRoom,
  RoomApplication,
  RoomBlacklistEntry,
  RoomCard,
  RoomDetail,
  RoomEventNotification,
  RoomInvite,
  RoomMember,
  RoomMemberPage,
  RoomMemberProfile,
  ScreenAudioToken,
  Sticker,
  StickerPack,
  UploadedAsset,
  UserAudioSettings,
  UserSearchPage,
  UserSession,
  UserSummary,
} from './models';
import { gangServerTimeHeader } from './server_time_header';
import { decodeJsonBody, encodeJsonBody, jsonAcceptHeader, jsonUtf8ContentType } from './utf8_json';

/** Dart `Future<String> Function({bool forceRefresh})`. */
export type AccessTokenProvider = (opts?: { forceRefresh?: boolean }) => Promise<string>;
export type ServerTimeCallback = (value: string | null) => void;
/** Receives the request round trip in milliseconds (Dart `Duration`). */
export type RequestLatencyCallback = (value: number) => void;
export type UploadProgressCallback = (opts: { sentBytes: number; totalBytes: number }) => void;

export class DownloadedFile {
  readonly bytes: Uint8Array;
  readonly filename: string;
  readonly mimeType: string;

  constructor(opts: { bytes: Uint8Array; filename: string; mimeType: string }) {
    this.bytes = opts.bytes;
    this.filename = opts.filename;
    this.mimeType = opts.mimeType;
  }
}

export class UploadTransferController {
  private _resumeCompleter: Completer<void> | null = null;
  private _cancelled = false;

  get isPaused(): boolean {
    return this._resumeCompleter != null;
  }

  get isCancelled(): boolean {
    return this._cancelled;
  }

  pause(): void {
    if (this._cancelled || this._resumeCompleter != null) return;
    this._resumeCompleter = new Completer<void>();
  }

  resume(): void {
    const completer = this._resumeCompleter;
    if (completer == null) return;
    this._resumeCompleter = null;
    if (!completer.isCompleted) completer.complete();
  }

  cancel(): void {
    if (this._cancelled) return;
    this._cancelled = true;
    this.resume();
  }

  async waitIfPaused(): Promise<void> {
    while (!this._cancelled) {
      const completer = this._resumeCompleter;
      if (completer == null) return;
      await completer.promise;
    }
  }
}

export class UploadCancelledException extends Error {
  constructor() {
    super('上传已取消');
    this.name = 'UploadCancelledException';
  }

  override toString(): string {
    return '上传已取消';
  }
}

export interface GangApi {
  getAppVersion(): Promise<AppVersionInfo>;

  me(): Promise<CurrentUser>;

  upsertPushDevice(opts: { provider: string; installationId: string; token: string; enabled: boolean }): Promise<void>;

  updatePushDeviceEnabled(opts: { provider: string; installationId: string; enabled: boolean }): Promise<void>;

  deletePushDevice(opts: { provider: string; installationId: string }): Promise<void>;

  updateAccount(opts?: {
    username?: string | null;
    email?: string | null;
    emailVerificationToken?: string | null;
    emailPublic?: boolean | null;
    phoneNumber?: string | null;
    phoneNumberPublic?: boolean | null;
    language?: string | null;
  }): Promise<CurrentUser>;

  updateProfile(opts?: {
    displayName?: string | null;
    bio?: string | null;
    gender?: string | null;
    avatarAssetId?: string | null;
    defaultAvatarKey?: string | null;
  }): Promise<CurrentUser>;

  uploadImageAsset(opts: { bytes: Uint8Array; filename: string; purpose?: string }): Promise<UploadedAsset>;

  uploadFileAsset(opts: {
    bytes: Uint8Array;
    filename: string;
    purpose?: string;
    onProgress?: UploadProgressCallback | null;
    controller?: UploadTransferController | null;
  }): Promise<UploadedAsset>;

  listStickerPacks(opts?: { scope?: string; roomId?: string | null; ownerUserId?: string | null }): Promise<StickerPack[]>;

  createStickerPack(opts: {
    name: string;
    scope?: string;
    roomId?: string | null;
    ownerUserId?: string | null;
    sortOrder?: number | null;
  }): Promise<StickerPack>;

  updateStickerPack(opts: { packId: string; name?: string | null; sortOrder?: number | null }): Promise<StickerPack>;

  deleteStickerPack(packId: string): Promise<void>;

  addSticker(opts: {
    packId: string;
    assetId: string;
    name: string;
    sortOrder?: number | null;
    idempotencyKey?: string | null;
    scope?: string;
    roomId?: string | null;
    ownerUserId?: string | null;
  }): Promise<void>;

  deleteSticker(opts: {
    packId: string;
    stickerId: string;
    scope?: string;
    roomId?: string | null;
    ownerUserId?: string | null;
  }): Promise<void>;

  updateSticker(opts: { packId: string; stickerId: string; name?: string | null; sortOrder?: number | null }): Promise<Sticker>;

  reorderStickers(opts: { packId: string; stickerIds: string[] }): Promise<StickerPack>;

  downloadStickers(opts: { stickerIds: string[] }): Promise<DownloadedFile>;

  saveSticker(opts: {
    roomId: string;
    stickerId: string;
    sourceMessageId?: string | null;
    targetScope?: string;
    targetPackId?: string | null;
    name?: string | null;
    sortOrder?: number | null;
  }): Promise<StickerPack>;

  changePassword(opts: { currentPassword: string; newPassword: string; revokeOtherSessions?: boolean }): Promise<void>;

  listSessions(): Promise<UserSession[]>;

  deleteMyAccount(opts: { confirm: boolean }): Promise<void>;

  listRooms(opts?: { limit?: number; cursor?: string | null }): Promise<RoomPage>;

  createRoom(opts: {
    name: string;
    description?: string | null;
    visibility?: string | null;
    joinPolicy?: string | null;
    aiVoiceAnnouncementsEnabled?: boolean | null;
    avatarAssetId?: string | null;
    defaultAvatarKey?: string | null;
    idempotencyKey?: string | null;
  }): Promise<RoomDetail>;

  getRoom(roomId: string): Promise<RoomDetail>;

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
  }): Promise<RoomDetail>;

  updateMyRoomSettings(opts: {
    roomId: string;
    remarkName?: string | null;
    notificationPolicy?: string | null;
    roomDisplayName?: string | null;
    isPinned?: boolean | null;
    aiVoiceAnnouncementsEnabled?: boolean | null;
  }): Promise<RoomDetail>;

  leaveRoom(opts: { roomId: string; confirmDeleteIfEmpty?: boolean }): Promise<void>;

  deleteRoom(opts: { roomId: string; confirmName: string }): Promise<void>;

  updateRoomMemberRole(opts: { roomId: string; userId: string; role: string }): Promise<RoomMember>;

  updateRoomMemberRoomDisplayName(opts: { roomId: string; userId: string; roomDisplayName: string }): Promise<RoomMember>;

  removeRoomMember(opts: { roomId: string; userId: string }): Promise<void>;

  transferRoomCreator(opts: { roomId: string; userId: string }): Promise<RoomDetail>;

  searchRooms(opts: { query: string; limit?: number }): Promise<PublicRoom[]>;

  search(opts: {
    query: string;
    limit?: number;
    categories?: Iterable<string> | null;
    myRoomsCursor?: string | null;
    publicRoomsCursor?: string | null;
    messagesCursor?: string | null;
    filesCursor?: string | null;
  }): Promise<GlobalSearchResults>;

  joinRoom(roomId: string, opts?: { reason?: string | null }): Promise<JoinRoomResult>;

  listRoomMembers(roomId: string, opts?: { limit?: number; cursor?: string | null }): Promise<RoomMemberPage>;

  getRoomMemberProfile(opts: { roomId: string; userId: string }): Promise<RoomMemberProfile>;

  getUserProfile(userId: string): Promise<UserSummary>;

  inviteMember(opts: { roomId: string; userId: string }): Promise<RoomInvite>;

  listRoomBlacklist(roomId: string): Promise<RoomBlacklistEntry[]>;

  blockRoomUser(opts: { roomId: string; userId: string }): Promise<RoomBlacklistEntry>;

  unblockRoomUser(opts: { roomId: string; userId: string }): Promise<void>;

  listRoomInvites(opts?: { status?: string }): Promise<RoomInvite[]>;

  reviewRoomInvite(opts: { inviteId: string; accept: boolean; reason?: string | null }): Promise<JoinRoomResult>;

  listRoomApplications(opts?: { status?: string }): Promise<RoomApplication[]>;

  withdrawRoomApplication(opts: { requestId: string }): Promise<RoomApplication>;

  listRoomNotifications(): Promise<RoomEventNotification[]>;

  deleteRoomNotification(opts: { notificationType: string; notificationId: string }): Promise<void>;

  markRoomNotificationsRead(): Promise<void>;

  searchUsers(opts: { query: string; limit?: number }): Promise<UserSummary[]>;

  searchUsersPage(opts: {
    query: string;
    limit?: number;
    cursor?: string | null;
    includeSuspended?: boolean;
  }): Promise<UserSearchPage>;

  getForcedUserSettings(userId: string): Promise<CurrentUser>;

  listForcedUserSessions(userId: string): Promise<UserSession[]>;

  forceDeleteUserAccount(userId: string): Promise<void>;

  updateForcedUserSettings(opts: {
    userId: string;
    username?: string | null;
    email?: string | null;
    emailVerified?: boolean | null;
    emailPublic?: boolean | null;
    phoneNumber?: string | null;
    phoneNumberPublic?: boolean | null;
    displayName?: string | null;
    bio?: string | null;
    gender?: string | null;
    avatarAssetId?: string | null;
    defaultAvatarKey?: string | null;
    language?: string | null;
    status?: string | null;
  }): Promise<CurrentUser>;

  getForcedUserAudioSettings(userId: string): Promise<UserAudioSettings>;

  updateForcedUserAudioSettings(opts: { userId: string; settings: UserAudioSettings }): Promise<UserAudioSettings>;

  forceResetUserPassword(opts: { userId: string; newPassword: string }): Promise<void>;

  listJoinRequests(roomId: string, opts?: { status?: string }): Promise<JoinRequest[]>;

  reviewJoinRequest(opts: { roomId: string; requestId: string; approve: boolean }): Promise<void>;

  listMessages(opts: { roomId: string; limit?: number; before?: string | null }): Promise<MessagePage>;

  listMessageHistory(opts: {
    roomId: string;
    query?: string;
    category?: string;
    senderUserId?: string | null;
    startAt?: Date | null;
    endAt?: Date | null;
    limit?: number;
    before?: string | null;
  }): Promise<MessagePage>;

  /**
   * Hides message records only for the current account's room-history view.
   * It does not recall or force-delete the underlying messages.
   */
  hideMessageHistory(opts: { roomId: string; messageIds: string[] }): Promise<number>;

  sendMessage(opts: {
    roomId: string;
    clientMessageId: string;
    body: string;
    type?: string;
    attachments?: readonly MessageAttachment[];
    mentions?: readonly JsonMap[];
    quoteMessageId?: string | null;
    quoteMessageIds?: readonly string[];
    idempotencyKey?: string | null;
  }): Promise<Message>;

  markRead(opts: { roomId: string; lastReadMessageId: string }): Promise<number>;

  /**
   * Recalls a message. Succeeds immediately for the sender (within the room's
   * recall policy/window) or an admin; under `admin_approval` a non-admin's
   * request is queued for review instead (see [MessageRecallResult.isPending]).
   */
  recallMessage(opts: { roomId: string; messageId: string; reason?: string | null }): Promise<MessageRecallResult>;

  /** Lists pending message-recall requests awaiting admin review. */
  listMessageRecallRequests(opts: { roomId: string; status?: string }): Promise<MessageRecallRequest[]>;

  /** Approves or rejects a pending message-recall request (admin only). */
  reviewMessageRecallRequest(opts: {
    roomId: string;
    requestId: string;
    approve: boolean;
    reason?: string | null;
  }): Promise<void>;

  /**
   * Force-deletes a message as a moderation action (admin only). Distinct from
   * a recall; requires explicit confirmation server-side.
   */
  forceDeleteMessage(opts: { roomId: string; messageId: string; reason?: string | null }): Promise<Message>;

  /** Lists the caller's own per-member volume overrides for a live room. */
  listMyLiveMemberVolumes(roomId: string): Promise<LiveMemberVolume[]>;

  /**
   * Sets how loudly the caller hears [targetUserId] in [roomId]'s live
   * session. [volume] is clamped 0–100 server-side.
   */
  updateMyLiveMemberVolume(opts: { roomId: string; targetUserId: string; volume: number }): Promise<LiveMemberVolume>;

  /**
   * Applies an admin moderation action to a live participant. [action] is one
   * of `kick`, `mute_mic`, `block_voice`, `restore_voice`,
   * `restore_headphones`.
   */
  moderateLiveParticipant(opts: { roomId: string; userId: string; action: string; reason?: string | null }): Promise<void>;

  getLiveState(roomId: string): Promise<LiveState>;

  joinLive(opts: {
    roomId: string;
    clientLiveSessionId: string;
    source: string;
    micMuted: boolean;
    headphonesMuted: boolean;
    idempotencyKey?: string | null;
  }): Promise<LiveJoinResult>;

  updateMyLiveState(opts: {
    roomId: string;
    micMuted?: boolean | null;
    headphonesMuted?: boolean | null;
    cameraOn?: boolean | null;
    cameraMirrored?: boolean | null;
    screenSharing?: boolean | null;
    connectionState?: string | null;
  }): Promise<LiveParticipant>;

  updateMyLiveScreenView(opts: { roomId: string; broadcasterUserId?: string | null }): Promise<void>;

  /**
   * Issues a publish-only LiveKit token for the caller's hidden screen-audio
   * aux participant (identity `<userId>--screen-audio`). Fetched on demand when
   * the user starts screen-share-with-audio, so the token is always fresh
   * regardless of how long they have been in the call.
   */
  issueScreenAudioToken(opts: { roomId: string }): Promise<ScreenAudioToken>;

  getMusicBoxState(roomId: string): Promise<MusicBoxState>;

  searchMusicBox(opts: {
    roomId: string;
    keyword: string;
    source?: string | null;
    count?: number | null;
    page?: number | null;
  }): Promise<MusicBoxSearchResult[]>;

  queueMusicBoxTrack(opts: {
    roomId: string;
    trackId: string;
    title: string;
    source?: string | null;
    artist?: string | null;
    durationMs?: number | null;
    idempotencyKey?: string | null;
  }): Promise<MusicBoxState>;

  removeMusicBoxItem(opts: { roomId: string; itemId: string }): Promise<MusicBoxState>;

  controlMusicBox(opts: {
    roomId: string;
    action: string;
    itemId?: string | null;
    mode?: string | null;
    commandId?: string | null;
    expectedRevision?: number | null;
  }): Promise<MusicBoxState>;

  activateMusicBoxPlaylist(opts: {
    roomId: string;
    sourceType: MusicBoxActiveSourceType;
    playlistId?: string | null;
    startPlay?: boolean;
    startItemId?: string | null;
  }): Promise<MusicBoxState>;

  close(): void;
}

export interface PersonalMusicPlaylistApi {
  listPersonalMusicPlaylists(opts?: { page?: number; pageSize?: number }): Promise<PersonalMusicPlaylistPage>;

  createPersonalMusicPlaylist(opts: { name: string }): Promise<PersonalMusicPlaylist>;

  renamePersonalMusicPlaylist(opts: { playlistId: string; name: string }): Promise<PersonalMusicPlaylist>;

  deletePersonalMusicPlaylist(playlistId: string): Promise<void>;

  pinPersonalMusicPlaylists(opts: { playlistIds: string[] }): Promise<void>;

  movePersonalMusicPlaylist(opts: { playlistId: string; direction: string }): Promise<void>;

  getPersonalMusicPlaylist(opts: {
    playlistId: string;
    page?: number;
    pageSize?: number;
    keyword?: string | null;
    source?: string | null;
  }): Promise<PersonalMusicPlaylistItemsPage>;

  searchPersonalMusicPlaylistTracks(opts: {
    keyword: string;
    source?: string | null;
    count?: number;
    page?: number;
  }): Promise<MusicBoxSearchResult[]>;

  addPersonalMusicPlaylistItem(opts: {
    playlistId: string;
    track: MusicBoxSearchResult;
    durationMs?: number | null;
  }): Promise<PersonalMusicPlaylistItem>;

  deletePersonalMusicPlaylistItem(opts: { playlistId: string; itemId: string }): Promise<void>;

  deletePersonalMusicPlaylistItems(opts: { playlistId: string; itemIds: string[] }): Promise<void>;

  movePersonalMusicPlaylistItem(opts: { playlistId: string; itemId: string; direction: string }): Promise<void>;

  reorderPersonalMusicPlaylistItems(opts: { playlistId: string; itemIds: string[] }): Promise<void>;
}

/**
 * Optional extension for atomically merging selected personal playlists into
 * a new playlist while consuming the merged source prefixes.
 */
export interface PersonalMusicPlaylistMergeApi {
  mergePersonalMusicPlaylists(opts: { name: string; playlistIds: string[] }): Promise<PersonalMusicPlaylistMergeResult>;
}

/**
 * Optional extension for atomically copying selected saved songs into
 * another personal playlist without mutating the source playlist.
 */
export interface PersonalMusicPlaylistBatchAddApi {
  batchAddPersonalMusicPlaylistItems(opts: {
    sourcePlaylistId: string;
    targetPlaylistId: string;
    itemIds: string[];
  }): Promise<PersonalMusicPlaylistBatchAddResult>;
}

/**
 * Authenticated transport for an explicitly initiated local song preview.
 * This is separate from [GangApi] so older fakes and read-only playlist
 * adapters do not gain a required method when preview support is unavailable.
 */
export interface MusicTrackPreviewApi {
  downloadMusicTrackPreview(opts: { source: string; trackId: string }): Promise<DownloadedFile>;
}

/**
 * Clones the immutable saved-playlist snapshot that is currently active in a
 * room. Kept separate from [GangApi] so older fakes remain source-compatible.
 */
export interface MusicBoxActivePlaylistCloneApi {
  cloneActiveMusicBoxPlaylist(opts: {
    roomId: string;
    playlistId: string;
    snapshotId: string;
  }): Promise<PersonalMusicPlaylist>;
}

export interface RoomMusicPlaylistApi {
  listRoomMusicPlaylists(opts: { roomId: string; page?: number; pageSize?: number }): Promise<PersonalMusicPlaylistPage>;

  createRoomMusicPlaylist(opts: { roomId: string; name: string }): Promise<PersonalMusicPlaylist>;

  renameRoomMusicPlaylist(opts: { roomId: string; playlistId: string; name: string }): Promise<PersonalMusicPlaylist>;

  deleteRoomMusicPlaylist(opts: { roomId: string; playlistId: string }): Promise<void>;

  pinRoomMusicPlaylists(opts: { roomId: string; playlistIds: string[] }): Promise<void>;

  moveRoomMusicPlaylist(opts: { roomId: string; playlistId: string; direction: string }): Promise<void>;

  getRoomMusicPlaylist(opts: {
    roomId: string;
    playlistId: string;
    page?: number;
    pageSize?: number;
    keyword?: string | null;
    source?: string | null;
  }): Promise<PersonalMusicPlaylistItemsPage>;

  addRoomMusicPlaylistItem(opts: {
    roomId: string;
    playlistId: string;
    track: MusicBoxSearchResult;
    durationMs?: number | null;
  }): Promise<PersonalMusicPlaylistItem>;

  deleteRoomMusicPlaylistItem(opts: { roomId: string; playlistId: string; itemId: string }): Promise<void>;

  deleteRoomMusicPlaylistItems(opts: { roomId: string; playlistId: string; itemIds: string[] }): Promise<void>;

  moveRoomMusicPlaylistItem(opts: {
    roomId: string;
    playlistId: string;
    itemId: string;
    direction: string;
  }): Promise<void>;

  reorderRoomMusicPlaylistItems(opts: { roomId: string; playlistId: string; itemIds: string[] }): Promise<void>;
}

/** Optional room-scoped counterpart of [PersonalMusicPlaylistMergeApi]. */
export interface RoomMusicPlaylistMergeApi {
  mergeRoomMusicPlaylists(opts: {
    roomId: string;
    name: string;
    playlistIds: string[];
  }): Promise<PersonalMusicPlaylistMergeResult>;
}

/** Room-scoped counterpart of [PersonalMusicPlaylistBatchAddApi]. */
export interface RoomMusicPlaylistBatchAddApi {
  batchAddRoomMusicPlaylistItems(opts: {
    roomId: string;
    sourcePlaylistId: string;
    targetPlaylistId: string;
    itemIds: string[];
  }): Promise<PersonalMusicPlaylistBatchAddResult>;
}

/**
 * Optional room-playlist extension that atomically imports one of the
 * authenticated user's personal playlists while creating the room playlist.
 * Kept separate so existing room playlist fakes and older adapters remain
 * source-compatible.
 */
export interface RoomMusicPlaylistImportApi {
  createRoomMusicPlaylistFromPersonal(opts: {
    roomId: string;
    name: string;
    importPlaylistId: string;
  }): Promise<PersonalMusicPlaylist>;
}

/**
 * Optional extension for atomically cloning one room playlist into the
 * authenticated user's personal library.
 */
export interface RoomMusicPlaylistCloneApi {
  cloneRoomMusicPlaylistToPersonal(opts: { roomId: string; playlistId: string }): Promise<PersonalMusicPlaylist>;
}

/**
 * Optional extension for atomically cloning an immutable playlist embedded in
 * a room message into the authenticated user's personal library.
 */
export interface SharedMusicPlaylistCloneApi {
  cloneSharedMusicPlaylistToPersonal(opts: { roomId: string; messageId: string }): Promise<PersonalMusicPlaylist>;
}

type _AuthorizedSend = (accessToken: string) => Promise<HttpResponse>;

/**
 * The shared host transport, resolved lazily. It has no `close`, so closing a
 * client that fell back to it never shuts the app-wide transport down.
 */
function _sharedPlatformHttpClient(): HttpClient {
  return { send: (request) => platform().http.send(request) };
}

function _now(): number {
  return typeof performance !== 'undefined' ? performance.now() : Date.now();
}

export class GangApiClient
  implements
    GangApi,
    PersonalMusicPlaylistApi,
    PersonalMusicPlaylistMergeApi,
    PersonalMusicPlaylistBatchAddApi,
    RoomMusicPlaylistApi,
    RoomMusicPlaylistMergeApi,
    RoomMusicPlaylistBatchAddApi,
    RoomMusicPlaylistImportApi,
    RoomMusicPlaylistCloneApi,
    SharedMusicPlaylistCloneApi,
    MusicTrackPreviewApi,
    MusicBoxActivePlaylistCloneApi
{
  readonly baseUrl: string;
  readonly accessTokenProvider: AccessTokenProvider;
  readonly onServerTime: ServerTimeCallback | null;
  readonly onRequestLatency: RequestLatencyCallback | null;
  private readonly _httpClient: HttpClient;
  private readonly _http: HttpClientHelpers;

  constructor(opts: {
    baseUrl: string;
    accessTokenProvider: AccessTokenProvider;
    httpClient?: HttpClient | null;
    onServerTime?: ServerTimeCallback | null;
    onRequestLatency?: RequestLatencyCallback | null;
  }) {
    this.baseUrl = opts.baseUrl;
    this.accessTokenProvider = opts.accessTokenProvider;
    this.onServerTime = opts.onServerTime ?? null;
    this.onRequestLatency = opts.onRequestLatency ?? null;
    // PORT: Dart created a private `http.Client()`; the port shares the host's
    // `platform().http` transport unless one is injected.
    this._httpClient = opts.httpClient ?? _sharedPlatformHttpClient();
    this._http = new HttpClientHelpers(this._httpClient);
  }

  async getAppVersion(): Promise<AppVersionInfo> {
    const decoded = await this._sendJson((token) => {
      return this._http.get(this._uri('/app/version'), { headers: this._headers(token) });
    });
    return AppVersionInfo.fromJson(decoded);
  }

  async me(): Promise<CurrentUser> {
    const decoded = await this._sendJson((token) => {
      return this._http.get(this._uri('/me'), { headers: this._headers(token) });
    });
    return CurrentUser.fromJson(decoded);
  }

  async upsertPushDevice(opts: {
    provider: string;
    installationId: string;
    token: string;
    enabled: boolean;
  }): Promise<void> {
    const { provider, installationId, token, enabled } = opts;
    const providerPath = encodeURIComponent(provider);
    const installationPath = encodeURIComponent(installationId);
    await this._sendJson((accessToken) => {
      return this._http.put(this._uri(`/me/push-devices/${providerPath}/${installationPath}`), {
        headers: this._headers(accessToken),
        body: encodeJsonBody({
          provider: provider,
          installation_id: installationId,
          token: token,
          platform: 'android',
          enabled: enabled,
        }),
      });
    });
  }

  async updatePushDeviceEnabled(opts: { provider: string; installationId: string; enabled: boolean }): Promise<void> {
    const { provider, installationId, enabled } = opts;
    const providerPath = encodeURIComponent(provider);
    const installationPath = encodeURIComponent(installationId);
    await this._sendJson((accessToken) => {
      return this._http.patch(this._uri(`/me/push-devices/${providerPath}/${installationPath}`), {
        headers: this._headers(accessToken),
        body: encodeJsonBody({ enabled: enabled }),
      });
    });
  }

  async deletePushDevice(opts: { provider: string; installationId: string }): Promise<void> {
    const { provider, installationId } = opts;
    const providerPath = encodeURIComponent(provider);
    const installationPath = encodeURIComponent(installationId);
    await this._sendJson((accessToken) => {
      return this._http.delete(this._uri(`/me/push-devices/${providerPath}/${installationPath}`), {
        headers: this._headers(accessToken),
      });
    });
  }

  async updateAccount(
    opts: {
      username?: string | null;
      email?: string | null;
      emailVerificationToken?: string | null;
      emailPublic?: boolean | null;
      phoneNumber?: string | null;
      phoneNumberPublic?: boolean | null;
      language?: string | null;
    } = {},
  ): Promise<CurrentUser> {
    const { username, email, emailVerificationToken, emailPublic, phoneNumber, phoneNumberPublic, language } = opts;
    const body: JsonMap = {};
    if (username != null) body['username'] = username;
    if (email != null) body['email'] = email;
    if (emailVerificationToken != null) {
      body['email_verification_token'] = emailVerificationToken;
    }
    if (emailPublic != null) body['email_public'] = emailPublic;
    if (phoneNumber != null) body['phone_number'] = phoneNumber;
    if (phoneNumberPublic != null) {
      body['phone_number_public'] = phoneNumberPublic;
    }
    if (language != null) body['language'] = language;
    const decoded = await this._sendJson((token) => {
      return this._http.patch(this._uri('/users/me/account'), {
        headers: this._headers(token),
        body: encodeJsonBody(body),
      });
    });
    return CurrentUser.fromJson(requireMap(decoded, 'user'));
  }

  async updateProfile(
    opts: {
      displayName?: string | null;
      bio?: string | null;
      gender?: string | null;
      avatarAssetId?: string | null;
      defaultAvatarKey?: string | null;
    } = {},
  ): Promise<CurrentUser> {
    const { displayName, bio, gender, avatarAssetId, defaultAvatarKey } = opts;
    const body: JsonMap = {};
    if (displayName != null) body['display_name'] = displayName;
    if (bio != null) body['bio'] = bio;
    if (gender != null) body['gender'] = gender;
    if (avatarAssetId != null) body['avatar_asset_id'] = avatarAssetId;
    if (defaultAvatarKey != null) body['default_avatar_key'] = defaultAvatarKey;
    const decoded = await this._sendJson(
      (token) => {
        return this._http.patch(this._uri('/users/me/profile'), {
          headers: this._headers(token),
          body: encodeJsonBody(body),
        });
      },
      { retryTransientFailures: true },
    );
    return CurrentUser.fromJson(requireMap(decoded, 'user'));
  }

  async uploadImageAsset(opts: { bytes: Uint8Array; filename: string; purpose?: string }): Promise<UploadedAsset> {
    const { bytes, filename, purpose = 'image' } = opts;
    return this._uploadAsset({
      path: '/uploads/images',
      bytes: bytes,
      filename: filename,
      purpose: purpose,
    });
  }

  async uploadFileAsset(opts: {
    bytes: Uint8Array;
    filename: string;
    purpose?: string;
    onProgress?: UploadProgressCallback | null;
    controller?: UploadTransferController | null;
  }): Promise<UploadedAsset> {
    const { bytes, filename, purpose = 'message_file', onProgress, controller } = opts;
    return this._uploadAsset({
      path: '/uploads/files',
      bytes: bytes,
      filename: filename,
      purpose: purpose,
      onProgress: onProgress,
      controller: controller,
    });
  }

  private async _uploadAsset(opts: {
    path: string;
    bytes: Uint8Array;
    filename: string;
    purpose: string;
    onProgress?: UploadProgressCallback | null;
    controller?: UploadTransferController | null;
  }): Promise<UploadedAsset> {
    const { path, bytes, filename, purpose, onProgress, controller } = opts;
    const decoded = await this._sendJson(
      async (token) => {
        // Port of `http.MultipartRequest`: a fresh body (and upload byte
        // stream) is built for every attempt, so retries resend from byte 0.
        const file: MultipartFile =
          onProgress == null && controller == null
            ? { field: 'file', filename: filename, bytes: bytes, length: bytes.length }
            : {
                field: 'file',
                filename: filename,
                stream: _uploadByteStream(bytes, { onProgress: onProgress, controller: controller }),
                length: bytes.length,
              };
        const multipart = buildMultipartBody({ purpose: purpose }, [file]);
        const streamed = await this._httpClient.send({
          method: 'POST',
          url: this._uri(path),
          headers: {
            authorization: `Bearer ${token}`,
            'content-type': multipart.contentType,
          },
          body: multipart.body,
          contentLength: multipart.contentLength,
        });
        return responseFromStream(streamed);
      },
      { retryTransientFailures: true },
    );
    return UploadedAsset.fromJson(requireMap(decoded, 'asset'));
  }

  async listStickerPacks(
    opts: { scope?: string; roomId?: string | null; ownerUserId?: string | null } = {},
  ): Promise<StickerPack[]> {
    const { scope = 'personal', roomId, ownerUserId } = opts;
    const query: Record<string, string> = { scope: scope };
    if (roomId != null) query['room_id'] = roomId;
    if (ownerUserId != null) query['owner_user_id'] = ownerUserId;
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri('/sticker-packs', query), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    return mapList(decoded['packs'], (json) => StickerPack.fromJson(json));
  }

  async createStickerPack(opts: {
    name: string;
    scope?: string;
    roomId?: string | null;
    ownerUserId?: string | null;
    sortOrder?: number | null;
  }): Promise<StickerPack> {
    const { name, scope = 'personal', roomId, ownerUserId, sortOrder } = opts;
    const body: JsonMap = { scope: scope, name: name };
    if (roomId != null) body['room_id'] = roomId;
    if (ownerUserId != null) body['owner_user_id'] = ownerUserId;
    if (sortOrder != null) body['sort_order'] = sortOrder;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri('/sticker-packs'), {
        headers: this._headers(token),
        body: encodeJsonBody(body),
      });
    });
    return StickerPack.fromJson(requireMap(decoded, 'pack'));
  }

  async updateStickerPack(opts: { packId: string; name?: string | null; sortOrder?: number | null }): Promise<StickerPack> {
    const { packId, name, sortOrder } = opts;
    const body: JsonMap = {};
    if (name != null) body['name'] = name;
    if (sortOrder != null) body['sort_order'] = sortOrder;
    const decoded = await this._sendJson((token) => {
      return this._http.patch(this._uri(`/sticker-packs/${packId}`), {
        headers: this._headers(token),
        body: encodeJsonBody(body),
      });
    });
    return StickerPack.fromJson(requireMap(decoded, 'pack'));
  }

  async deleteStickerPack(packId: string): Promise<void> {
    await this._sendJson((token) => {
      return this._http.delete(this._uri(`/sticker-packs/${packId}`), { headers: this._headers(token) });
    });
  }

  async addSticker(opts: {
    packId: string;
    assetId: string;
    name: string;
    sortOrder?: number | null;
    idempotencyKey?: string | null;
    scope?: string;
    roomId?: string | null;
    ownerUserId?: string | null;
  }): Promise<void> {
    const { packId, assetId, name, sortOrder, idempotencyKey, scope = 'personal', roomId, ownerUserId } = opts;
    const body: JsonMap = { asset_id: assetId, name: name };
    if (sortOrder != null) body['sort_order'] = sortOrder;
    const requestIdempotencyKey = idempotencyKey ?? newUuid();
    const send = async (): Promise<void> => {
      await this._sendJson((token) => {
        return this._http.post(this._uri(`/sticker-packs/${packId}/stickers`), {
          headers: this._headers(token, { idempotencyKey: requestIdempotencyKey }),
          body: encodeJsonBody(body),
        });
      });
    };

    const maxAttempts = 3;
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        await send();
        return;
      } catch (e) {
        if (e instanceof ApiException) {
          if (!this._isRetryableHttpFailure(e.statusCode)) throw e;
        } else if (e instanceof ClientException) {
          if (!this._isRetryableTransportFailure(e)) throw e;
        } else {
          throw e;
        }
        if (
          await this._stickerAssetAlreadyLinked({
            packId: packId,
            assetId: assetId,
            scope: scope,
            roomId: roomId,
            ownerUserId: ownerUserId,
          })
        ) {
          return;
        }
        if (attempt === maxAttempts) throw e;
        await delay(120 * attempt);
      }
    }
  }

  private async _stickerAssetAlreadyLinked(opts: {
    packId: string;
    assetId: string;
    scope: string;
    roomId?: string | null;
    ownerUserId?: string | null;
  }): Promise<boolean> {
    const { packId, assetId, scope, roomId, ownerUserId } = opts;
    try {
      const packs = await this.listStickerPacks({ scope: scope, roomId: roomId, ownerUserId: ownerUserId });
      for (const pack of packs) {
        if (pack.id !== packId) continue;
        for (const sticker of pack.stickers) {
          if (sticker.asset.id === assetId) return true;
        }
      }
    } catch {
      // If verification is also unavailable, let the caller retry once.
    }
    return false;
  }

  private async _stickerAlreadyDeleted(opts: {
    packId: string;
    stickerId: string;
    scope: string;
    roomId?: string | null;
    ownerUserId?: string | null;
  }): Promise<boolean> {
    const { packId, stickerId, scope, roomId, ownerUserId } = opts;
    try {
      const packs = await this.listStickerPacks({ scope: scope, roomId: roomId, ownerUserId: ownerUserId });
      for (const pack of packs) {
        if (pack.id !== packId) continue;
        return !pack.stickers.some((sticker) => sticker.id === stickerId);
      }
    } catch {
      // If verification also fails, let the delete path retry.
    }
    return false;
  }

  async deleteSticker(opts: {
    packId: string;
    stickerId: string;
    scope?: string;
    roomId?: string | null;
    ownerUserId?: string | null;
  }): Promise<void> {
    const { packId, stickerId, scope = 'personal', roomId, ownerUserId } = opts;
    const send = async (): Promise<void> => {
      await this._sendJson((token) => {
        return this._http.delete(this._uri(`/sticker-packs/${packId}/stickers/${stickerId}`), {
          headers: this._headers(token),
        });
      });
    };

    const maxAttempts = 3;
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        await send();
        return;
      } catch (e) {
        if (e instanceof ApiException) {
          if (e.statusCode === 404) return;
          if (!this._isRetryableHttpFailure(e.statusCode)) throw e;
        } else if (e instanceof ClientException) {
          if (!this._isRetryableTransportFailure(e)) throw e;
        } else {
          throw e;
        }
        if (
          await this._stickerAlreadyDeleted({
            packId: packId,
            stickerId: stickerId,
            scope: scope,
            roomId: roomId,
            ownerUserId: ownerUserId,
          })
        ) {
          return;
        }
        if (attempt === maxAttempts) throw e;
        await delay(120 * attempt);
      }
    }
  }

  async updateSticker(opts: {
    packId: string;
    stickerId: string;
    name?: string | null;
    sortOrder?: number | null;
  }): Promise<Sticker> {
    const { packId, stickerId, name, sortOrder } = opts;
    const body: JsonMap = {};
    if (name != null) body['name'] = name;
    if (sortOrder != null) body['sort_order'] = sortOrder;
    const decoded = await this._sendJson((token) => {
      return this._http.patch(this._uri(`/sticker-packs/${packId}/stickers/${stickerId}`), {
        headers: this._headers(token),
        body: encodeJsonBody(body),
      });
    });
    return Sticker.fromJson(requireMap(decoded, 'sticker'));
  }

  async reorderStickers(opts: { packId: string; stickerIds: string[] }): Promise<StickerPack> {
    const { packId, stickerIds } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/sticker-packs/${packId}/stickers/reorder`), {
        headers: this._headers(token),
        body: encodeJsonBody({ sticker_ids: stickerIds }),
      });
    });
    return StickerPack.fromJson(requireMap(decoded, 'pack'));
  }

  async downloadStickers(opts: { stickerIds: string[] }): Promise<DownloadedFile> {
    const { stickerIds } = opts;
    const response = await this._sendWithAuth((token) => {
      return this._http.get(this._uri('/stickers/download', { ids: stickerIds.join(',') }), {
        headers: { authorization: `Bearer ${token}` },
      });
    });
    this._throwIfFailed(response);
    return new DownloadedFile({
      bytes: response.bodyBytes,
      filename: this._downloadFilename(response),
      mimeType: response.headers['content-type'] ?? 'application/octet-stream',
    });
  }

  async saveSticker(opts: {
    roomId: string;
    stickerId: string;
    sourceMessageId?: string | null;
    targetScope?: string;
    targetPackId?: string | null;
    name?: string | null;
    sortOrder?: number | null;
  }): Promise<StickerPack> {
    const { roomId, stickerId, sourceMessageId, targetScope = 'personal', targetPackId, name, sortOrder } = opts;
    const body: JsonMap = {
      sticker_id: stickerId,
      target_scope: targetScope,
    };
    if (sourceMessageId != null) body['source_message_id'] = sourceMessageId;
    if (targetPackId != null) body['target_pack_id'] = targetPackId;
    if (name != null) body['name'] = name;
    if (sortOrder != null) body['sort_order'] = sortOrder;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/rooms/${roomId}/stickers/save`), {
        headers: this._headers(token),
        body: encodeJsonBody(body),
      });
    });
    return StickerPack.fromJson(requireMap(decoded, 'pack'));
  }

  async changePassword(opts: {
    currentPassword: string;
    newPassword: string;
    revokeOtherSessions?: boolean;
  }): Promise<void> {
    const { currentPassword, newPassword, revokeOtherSessions = true } = opts;
    await this._sendJson((token) => {
      return this._http.post(this._uri('/auth/password'), {
        headers: this._headers(token),
        body: encodeJsonBody({
          current_password: currentPassword,
          new_password: newPassword,
          revoke_other_sessions: revokeOtherSessions,
        }),
      });
    });
  }

  async listSessions(): Promise<UserSession[]> {
    const decoded = await this._sendJsonValue(
      (token) => {
        return this._http.get(this._uri('/auth/sessions'), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    return mapList(_sessionItems(decoded), (json) => UserSession.fromJson(json));
  }

  async deleteMyAccount(opts: { confirm: boolean }): Promise<void> {
    const { confirm } = opts;
    await this._sendJson((token) => {
      return this._http.delete(this._uri('/users/me/account'), {
        headers: this._headers(token),
        body: encodeJsonBody({ confirm: confirm }),
      });
    });
  }

  async listRooms(opts: { limit?: number; cursor?: string | null } = {}): Promise<RoomPage> {
    const { limit = 50, cursor } = opts;
    const query: Record<string, string> = { limit: `${limit}` };
    if (cursor != null) query['cursor'] = cursor;
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri('/rooms', query), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    return RoomPage.fromJson(decoded);
  }

  async createRoom(opts: {
    name: string;
    description?: string | null;
    visibility?: string | null;
    joinPolicy?: string | null;
    aiVoiceAnnouncementsEnabled?: boolean | null;
    avatarAssetId?: string | null;
    defaultAvatarKey?: string | null;
    idempotencyKey?: string | null;
  }): Promise<RoomDetail> {
    const {
      name,
      description,
      visibility,
      joinPolicy,
      aiVoiceAnnouncementsEnabled,
      avatarAssetId,
      defaultAvatarKey,
      idempotencyKey,
    } = opts;
    const decoded = await this._sendJson((token) => {
      const body: JsonMap = { name: name };
      if (description != null) body['description'] = description;
      if (visibility != null) body['visibility'] = visibility;
      if (joinPolicy != null) body['join_policy'] = joinPolicy;
      if (aiVoiceAnnouncementsEnabled != null) {
        body['ai_voice_announcements_enabled'] = aiVoiceAnnouncementsEnabled;
      }
      if (avatarAssetId != null) body['avatar_asset_id'] = avatarAssetId;
      if (defaultAvatarKey != null) {
        body['default_avatar_key'] = defaultAvatarKey;
      }
      return this._http.post(this._uri('/rooms'), {
        headers: this._headers(token, { idempotencyKey: idempotencyKey ?? newUuid() }),
        body: encodeJsonBody(body),
      });
    });
    return RoomDetail.fromJson(requireMap(decoded, 'room'));
  }

  async getRoom(roomId: string): Promise<RoomDetail> {
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri(`/rooms/${roomId}`), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    return RoomDetail.fromJson(requireMap(decoded, 'room'));
  }

  async updateRoom(opts: {
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
    const {
      roomId,
      name,
      description,
      visibility,
      joinPolicy,
      aiVoiceAnnouncementsEnabled,
      gangBotEnabled,
      avatarAssetId,
      defaultAvatarKey,
    } = opts;
    const body: JsonMap = {};
    if (name != null) body['name'] = name;
    if (description != null) body['description'] = description;
    if (visibility != null) body['visibility'] = visibility;
    if (joinPolicy != null) body['join_policy'] = joinPolicy;
    if (aiVoiceAnnouncementsEnabled != null) {
      body['ai_voice_announcements_enabled'] = aiVoiceAnnouncementsEnabled;
    }
    if (gangBotEnabled != null) body['gang_bot_enabled'] = gangBotEnabled;
    if (avatarAssetId != null) body['avatar_asset_id'] = avatarAssetId;
    if (defaultAvatarKey != null) body['default_avatar_key'] = defaultAvatarKey;
    const decoded = await this._sendJson(
      (token) => {
        return this._http.patch(this._uri(`/rooms/${roomId}`), {
          headers: this._headers(token),
          body: encodeJsonBody(body),
        });
      },
      { retryTransientFailures: true },
    );
    return RoomDetail.fromJson(requireMap(decoded, 'room'));
  }

  async updateMyRoomSettings(opts: {
    roomId: string;
    remarkName?: string | null;
    notificationPolicy?: string | null;
    roomDisplayName?: string | null;
    isPinned?: boolean | null;
    aiVoiceAnnouncementsEnabled?: boolean | null;
  }): Promise<RoomDetail> {
    const { roomId, remarkName, notificationPolicy, roomDisplayName, isPinned, aiVoiceAnnouncementsEnabled } = opts;
    const body: JsonMap = {};
    if (remarkName != null) body['remark_name'] = remarkName;
    if (notificationPolicy != null) {
      body['notification_policy'] = notificationPolicy;
    }
    if (roomDisplayName != null) body['room_display_name'] = roomDisplayName;
    if (isPinned != null) body['is_pinned'] = isPinned;
    if (aiVoiceAnnouncementsEnabled != null) {
      body['ai_voice_announcements_enabled'] = aiVoiceAnnouncementsEnabled;
    }
    const decoded = await this._sendJson(
      (token) => {
        return this._http.patch(this._uri(`/rooms/${roomId}/me`), {
          headers: this._headers(token),
          body: encodeJsonBody(body),
        });
      },
      { retryTransientFailures: true },
    );
    return RoomDetail.fromJson(requireMap(decoded, 'room'));
  }

  async leaveRoom(opts: { roomId: string; confirmDeleteIfEmpty?: boolean }): Promise<void> {
    const { roomId, confirmDeleteIfEmpty = false } = opts;
    await this._sendJson((token) => {
      return this._http.delete(this._uri(`/rooms/${roomId}/members/me`), {
        headers: this._headers(token),
        body: encodeJsonBody(confirmDeleteIfEmpty ? { confirm_delete_if_empty: true } : {}),
      });
    });
  }

  async deleteRoom(opts: { roomId: string; confirmName: string }): Promise<void> {
    const { roomId, confirmName } = opts;
    await this._sendJson((token) => {
      return this._http.delete(this._uri(`/rooms/${roomId}`), {
        headers: this._headers(token),
        body: encodeJsonBody({ confirm_name: confirmName }),
      });
    });
  }

  async updateRoomMemberRole(opts: { roomId: string; userId: string; role: string }): Promise<RoomMember> {
    const { roomId, userId, role } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.patch(this._uri(`/rooms/${roomId}/members/${userId}`), {
        headers: this._headers(token),
        body: encodeJsonBody({ role: role }),
      });
    });
    return RoomMember.fromJson(requireMap(decoded, 'member'));
  }

  async updateRoomMemberRoomDisplayName(opts: {
    roomId: string;
    userId: string;
    roomDisplayName: string;
  }): Promise<RoomMember> {
    const { roomId, userId, roomDisplayName } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.patch(this._uri(`/rooms/${roomId}/members/${userId}`), {
        headers: this._headers(token),
        body: encodeJsonBody({ room_display_name: roomDisplayName }),
      });
    });
    return RoomMember.fromJson(requireMap(decoded, 'member'));
  }

  async removeRoomMember(opts: { roomId: string; userId: string }): Promise<void> {
    const { roomId, userId } = opts;
    await this._sendJson((token) => {
      return this._http.delete(this._uri(`/rooms/${roomId}/members/${userId}`), {
        headers: this._headers(token),
      });
    });
  }

  async transferRoomCreator(opts: { roomId: string; userId: string }): Promise<RoomDetail> {
    const { roomId, userId } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.patch(this._uri(`/rooms/${roomId}/creator`), {
        headers: this._headers(token),
        body: encodeJsonBody({ user_id: userId }),
      });
    });
    return RoomDetail.fromJson(requireMap(decoded, 'room'));
  }

  async searchRooms(opts: { query: string; limit?: number }): Promise<PublicRoom[]> {
    const { query, limit = 20 } = opts;
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri('/rooms/search', { q: query, limit: `${limit}` }), {
          headers: this._headers(token),
        });
      },
      { retryTransientFailures: true },
    );
    return mapList(decoded['rooms'], (json) => PublicRoom.fromJson(json));
  }

  async search(opts: {
    query: string;
    limit?: number;
    categories?: Iterable<string> | null;
    myRoomsCursor?: string | null;
    publicRoomsCursor?: string | null;
    messagesCursor?: string | null;
    filesCursor?: string | null;
  }): Promise<GlobalSearchResults> {
    const { query, limit = 8, categories, myRoomsCursor, publicRoomsCursor, messagesCursor, filesCursor } = opts;
    const queryParameters: Record<string, string> = { q: query, limit: `${limit}` };
    if (categories != null) {
      const encodedCategories = Array.from(categories)
        .filter((category) => category.length > 0)
        .join(',');
      if (encodedCategories.length > 0) {
        queryParameters['categories'] = encodedCategories;
      }
    }
    if (myRoomsCursor != null) {
      queryParameters['my_rooms_cursor'] = myRoomsCursor;
    }
    if (publicRoomsCursor != null) {
      queryParameters['public_rooms_cursor'] = publicRoomsCursor;
    }
    if (messagesCursor != null) {
      queryParameters['messages_cursor'] = messagesCursor;
    }
    if (filesCursor != null) {
      queryParameters['files_cursor'] = filesCursor;
    }

    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri('/search', queryParameters), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    return GlobalSearchResults.fromJson(decoded);
  }

  async joinRoom(roomId: string, opts: { reason?: string | null } = {}): Promise<JoinRoomResult> {
    const { reason } = opts;
    const body: JsonMap = {};
    const trimmedReason = reason?.trim();
    if (trimmedReason != null && trimmedReason.length > 0) {
      body['reason'] = trimmedReason;
    }
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/rooms/${roomId}/join`), {
        headers: this._headers(token, { idempotencyKey: newUuid() }),
        body: Object.keys(body).length === 0 ? undefined : encodeJsonBody(body),
      });
    });
    const roomJson = asMapOrNull(decoded['room']);
    if (roomJson != null) {
      return new JoinRoomResult({ room: RoomDetail.fromJson(roomJson) });
    }
    // approval_required path: server returns {"join_request": {...}} with 202.
    return new JoinRoomResult({ pending: true });
  }

  async listRoomMembers(roomId: string, opts: { limit?: number; cursor?: string | null } = {}): Promise<RoomMemberPage> {
    const { limit = 100, cursor } = opts;
    const query: Record<string, string> = { limit: `${limit}` };
    if (cursor != null) query['cursor'] = cursor;
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri(`/rooms/${roomId}/members`, query), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    return RoomMemberPage.fromJson(decoded);
  }

  async getRoomMemberProfile(opts: { roomId: string; userId: string }): Promise<RoomMemberProfile> {
    const { roomId, userId } = opts;
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri(`/rooms/${roomId}/members/${userId}/profile`), {
          headers: this._headers(token),
        });
      },
      { retryTransientFailures: true },
    );
    return RoomMemberProfile.fromJson(requireMap(decoded, 'profile'));
  }

  async getUserProfile(userId: string): Promise<UserSummary> {
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri(`/users/${userId}/profile`), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    const profile = requireMap(decoded, 'profile');
    return UserSummary.fromJson(requireMap(profile, 'user'));
  }

  async inviteMember(opts: { roomId: string; userId: string }): Promise<RoomInvite> {
    const { roomId, userId } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/rooms/${roomId}/invites`), {
        headers: this._headers(token, { idempotencyKey: newUuid() }),
        body: encodeJsonBody({ user_id: userId }),
      });
    });
    return RoomInvite.fromJson(requireMap(decoded, 'invite'));
  }

  async listRoomBlacklist(roomId: string): Promise<RoomBlacklistEntry[]> {
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri(`/rooms/${roomId}/blacklist`), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    return mapList(decoded['blacklist'] ?? decoded['items'], (json) => RoomBlacklistEntry.fromJson(json));
  }

  async blockRoomUser(opts: { roomId: string; userId: string }): Promise<RoomBlacklistEntry> {
    const { roomId, userId } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/rooms/${roomId}/blacklist`), {
        headers: this._headers(token, { idempotencyKey: newUuid() }),
        body: encodeJsonBody({ user_id: userId }),
      });
    });
    return RoomBlacklistEntry.fromJson(requireMap(decoded, 'entry'));
  }

  async unblockRoomUser(opts: { roomId: string; userId: string }): Promise<void> {
    const { roomId, userId } = opts;
    await this._sendJson((token) => {
      return this._http.delete(this._uri(`/rooms/${roomId}/blacklist/${userId}`), {
        headers: this._headers(token),
      });
    });
  }

  async listRoomInvites(opts: { status?: string } = {}): Promise<RoomInvite[]> {
    const { status = 'pending' } = opts;
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri('/room-invites', { status: status }), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    return mapList(decoded['invites'], (json) => RoomInvite.fromJson(json));
  }

  async reviewRoomInvite(opts: { inviteId: string; accept: boolean; reason?: string | null }): Promise<JoinRoomResult> {
    const { inviteId, accept, reason } = opts;
    const body: JsonMap = { decision: accept ? 'accept' : 'reject' };
    const trimmedReason = reason?.trim();
    if (trimmedReason != null && trimmedReason.length > 0) {
      body['reason'] = trimmedReason;
    }
    const decoded = await this._sendJson((token) => {
      return this._http.patch(this._uri(`/room-invites/${inviteId}`), {
        headers: this._headers(token),
        body: encodeJsonBody(body),
      });
    });
    const roomJson = asMapOrNull(decoded['room']);
    if (roomJson != null) {
      return new JoinRoomResult({ room: RoomDetail.fromJson(roomJson) });
    }
    if (decoded['join_request'] != null) {
      return new JoinRoomResult({ pending: true });
    }
    return new JoinRoomResult({});
  }

  async listRoomApplications(opts: { status?: string } = {}): Promise<RoomApplication[]> {
    const { status = 'pending' } = opts;
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri('/room-applications', { status: status }), {
          headers: this._headers(token),
        });
      },
      { retryTransientFailures: true },
    );
    return mapList(decoded['applications'], (json) => RoomApplication.fromJson(json));
  }

  async withdrawRoomApplication(opts: { requestId: string }): Promise<RoomApplication> {
    const { requestId } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.patch(this._uri(`/room-applications/${requestId}`), {
        headers: this._headers(token),
        body: encodeJsonBody({ decision: 'withdraw' }),
      });
    });
    return RoomApplication.fromJson(requireMap(decoded, 'application'));
  }

  async listRoomNotifications(): Promise<RoomEventNotification[]> {
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri('/room-notifications'), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    return mapList(decoded['notifications'], (json) => RoomEventNotification.fromJson(json));
  }

  async deleteRoomNotification(opts: { notificationType: string; notificationId: string }): Promise<void> {
    const { notificationType, notificationId } = opts;
    await this._sendJson((token) => {
      return this._http.delete(this._uri(`/room-notifications/${notificationType}/${notificationId}`), {
        headers: this._headers(token),
      });
    });
  }

  async markRoomNotificationsRead(): Promise<void> {
    await this._sendJson((token) => {
      return this._http.post(this._uri('/room-notifications/read'), { headers: this._headers(token) });
    });
  }

  async searchUsers(opts: { query: string; limit?: number }): Promise<UserSummary[]> {
    const { query, limit = 20 } = opts;
    const page = await this.searchUsersPage({ query: query, limit: limit });
    return page.users;
  }

  async searchUsersPage(opts: {
    query: string;
    limit?: number;
    cursor?: string | null;
    includeSuspended?: boolean;
  }): Promise<UserSearchPage> {
    const { query, limit = 20, cursor, includeSuspended = false } = opts;
    const parameters: Record<string, string> = { q: query, limit: `${limit}` };
    if (cursor != null) parameters['cursor'] = cursor;
    if (includeSuspended) parameters['include_suspended'] = 'true';
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri('/users/search', parameters), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    return UserSearchPage.fromJson(decoded);
  }

  async getForcedUserSettings(userId: string): Promise<CurrentUser> {
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri(`/users/${userId}/settings`), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    return CurrentUser.fromJson(requireMap(decoded, 'user'));
  }

  async listForcedUserSessions(userId: string): Promise<UserSession[]> {
    const decoded = await this._sendJsonValue(
      (token) => {
        return this._http.get(this._uri(`/users/${userId}/sessions`), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    return mapList(_sessionItems(decoded), (json) => UserSession.fromJson(json));
  }

  async forceDeleteUserAccount(userId: string): Promise<void> {
    await this._sendJson((token) => {
      return this._http.delete(this._uri(`/users/${userId}/account`), {
        headers: this._headers(token),
        body: encodeJsonBody({ confirm: true }),
      });
    });
  }

  async updateForcedUserSettings(opts: {
    userId: string;
    username?: string | null;
    email?: string | null;
    emailVerified?: boolean | null;
    emailPublic?: boolean | null;
    phoneNumber?: string | null;
    phoneNumberPublic?: boolean | null;
    displayName?: string | null;
    bio?: string | null;
    gender?: string | null;
    avatarAssetId?: string | null;
    defaultAvatarKey?: string | null;
    language?: string | null;
    status?: string | null;
  }): Promise<CurrentUser> {
    const { userId } = opts;
    const body: JsonMap = {};
    const put = (key: string, value: unknown): void => {
      if (value != null) body[key] = value;
    };

    put('username', opts.username);
    put('email', opts.email);
    put('email_verified', opts.emailVerified);
    put('email_public', opts.emailPublic);
    put('phone_number', opts.phoneNumber);
    put('phone_number_public', opts.phoneNumberPublic);
    put('display_name', opts.displayName);
    put('bio', opts.bio);
    put('gender', opts.gender);
    put('avatar_asset_id', opts.avatarAssetId);
    put('default_avatar_key', opts.defaultAvatarKey);
    put('language', opts.language);
    put('status', opts.status);
    const decoded = await this._sendJson((token) => {
      return this._http.patch(this._uri(`/users/${userId}/settings`), {
        headers: this._headers(token),
        body: encodeJsonBody(body),
      });
    });
    return CurrentUser.fromJson(requireMap(decoded, 'user'));
  }

  async getForcedUserAudioSettings(userId: string): Promise<UserAudioSettings> {
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri(`/users/${userId}/audio-settings`), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    return UserAudioSettings.fromJson(requireMap(decoded, 'audio_settings'));
  }

  async updateForcedUserAudioSettings(opts: { userId: string; settings: UserAudioSettings }): Promise<UserAudioSettings> {
    const { userId, settings } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.patch(this._uri(`/users/${userId}/audio-settings`), {
        headers: this._headers(token),
        body: encodeJsonBody({
          default_audio_input_volume: settings.defaultAudioInputVolume,
          default_audio_output_volume: settings.defaultAudioOutputVolume,
          live_mic_input_volume: settings.liveMicInputVolume,
          live_voice_output_volume: settings.liveVoiceOutputVolume,
          live_screen_share_output_volume: settings.liveScreenShareOutputVolume,
          live_music_output_volume: settings.liveMusicOutputVolume,
        }),
      });
    });
    return UserAudioSettings.fromJson(requireMap(decoded, 'audio_settings'));
  }

  async forceResetUserPassword(opts: { userId: string; newPassword: string }): Promise<void> {
    const { userId, newPassword } = opts;
    await this._sendJson((token) => {
      return this._http.post(this._uri(`/users/${userId}/password`), {
        headers: this._headers(token),
        body: encodeJsonBody({ new_password: newPassword }),
      });
    });
  }

  async listJoinRequests(roomId: string, opts: { status?: string } = {}): Promise<JoinRequest[]> {
    const { status = 'pending' } = opts;
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri(`/rooms/${roomId}/join-requests`, { status: status }), {
          headers: this._headers(token),
        });
      },
      { retryTransientFailures: true },
    );
    return mapList(decoded['requests'], (json) => JoinRequest.fromJson(json));
  }

  async reviewJoinRequest(opts: { roomId: string; requestId: string; approve: boolean }): Promise<void> {
    const { roomId, requestId, approve } = opts;
    await this._sendJson((token) => {
      return this._http.patch(this._uri(`/rooms/${roomId}/join-requests/${requestId}`), {
        headers: this._headers(token),
        body: encodeJsonBody({ decision: approve ? 'approve' : 'reject' }),
      });
    });
  }

  async listMessages(opts: { roomId: string; limit?: number; before?: string | null }): Promise<MessagePage> {
    const { roomId, limit = 50, before } = opts;
    const query: Record<string, string> = { limit: `${limit}` };
    if (before != null) query['before'] = before;
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri(`/rooms/${roomId}/messages`, query), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    return MessagePage.fromJson(decoded);
  }

  async listMessageHistory(opts: {
    roomId: string;
    query?: string;
    category?: string;
    senderUserId?: string | null;
    startAt?: Date | null;
    endAt?: Date | null;
    limit?: number;
    before?: string | null;
  }): Promise<MessagePage> {
    const { roomId, query = '', category = 'all', senderUserId, startAt, endAt, limit = 50, before } = opts;
    const parameters: Record<string, string> = {
      limit: `${limit}`,
      category: category,
    };
    const trimmedQuery = query.trim();
    if (trimmedQuery.length > 0) parameters['query'] = trimmedQuery;
    const trimmedSender = senderUserId?.trim();
    if (trimmedSender != null && trimmedSender.length > 0) {
      parameters['sender_user_id'] = trimmedSender;
    }
    if (startAt != null) {
      parameters['start_at'] = startAt.toISOString();
    }
    if (endAt != null) {
      parameters['end_at'] = endAt.toISOString();
    }
    if (before != null && before.trim().length > 0) {
      parameters['before'] = before.trim();
    }
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri(`/rooms/${roomId}/message-history`, parameters), {
          headers: this._headers(token),
        });
      },
      { retryTransientFailures: true },
    );
    return MessagePage.fromJson(decoded);
  }

  async hideMessageHistory(opts: { roomId: string; messageIds: string[] }): Promise<number> {
    const { roomId, messageIds } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/rooms/${roomId}/message-history/hide`), {
        headers: this._headers(token),
        body: encodeJsonBody({ message_ids: messageIds, confirm: true }),
      });
    });
    return asInt(decoded['deleted_count']) ?? 0;
  }

  async sendMessage(opts: {
    roomId: string;
    clientMessageId: string;
    body: string;
    type?: string;
    attachments?: readonly MessageAttachment[];
    mentions?: readonly JsonMap[];
    quoteMessageId?: string | null;
    quoteMessageIds?: readonly string[];
    idempotencyKey?: string | null;
  }): Promise<Message> {
    const {
      roomId,
      clientMessageId,
      body,
      type = 'text',
      attachments = [],
      mentions = [],
      quoteMessageId,
      quoteMessageIds = [],
      idempotencyKey,
    } = opts;
    const requestIdempotencyKey = idempotencyKey ?? newUuid();
    const decoded = await this._sendJson(
      (token) => {
        const requestBody: JsonMap = {
          client_message_id: clientMessageId,
          body: body,
        };
        if (type !== 'text') requestBody['type'] = type;
        if (attachments.length > 0) {
          requestBody['attachments'] = attachments.map((attachment) => attachment.toJson());
        }
        if (mentions.length > 0) {
          requestBody['mentions'] = mentions;
        }
        if (quoteMessageId != null && quoteMessageId.length > 0) {
          requestBody['quote_message_id'] = quoteMessageId;
        }
        if (quoteMessageIds.length > 0) {
          requestBody['quote_message_ids'] = quoteMessageIds;
          requestBody['quote_message_id'] = quoteMessageIds[0];
        }
        return this._http.post(this._uri(`/rooms/${roomId}/messages`), {
          headers: this._headers(token, { idempotencyKey: requestIdempotencyKey }),
          body: encodeJsonBody(requestBody),
        });
      },
      { retryTransientFailures: true },
    );
    return Message.fromJson(requireMap(decoded, 'message'));
  }

  async markRead(opts: { roomId: string; lastReadMessageId: string }): Promise<number> {
    const { roomId, lastReadMessageId } = opts;
    const decoded = await this._sendJson(
      (token) => {
        return this._http.post(this._uri(`/rooms/${roomId}/read`), {
          headers: this._headers(token),
          body: encodeJsonBody({ last_read_message_id: lastReadMessageId }),
        });
      },
      { retryTransientFailures: true },
    );
    return requireInt(decoded, 'unread_count');
  }

  async recallMessage(opts: { roomId: string; messageId: string; reason?: string | null }): Promise<MessageRecallResult> {
    const { roomId, messageId, reason } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/rooms/${roomId}/messages/${messageId}/recall`), {
        headers: this._headers(token),
        body: encodeJsonBody(reason != null ? { reason: reason } : {}),
      });
    });
    return MessageRecallResult.fromJson(decoded);
  }

  async listMessageRecallRequests(opts: { roomId: string; status?: string }): Promise<MessageRecallRequest[]> {
    const { roomId, status = 'pending' } = opts;
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri(`/rooms/${roomId}/message-recall-requests`, { status: status }), {
          headers: this._headers(token),
        });
      },
      { retryTransientFailures: true },
    );
    return mapList(decoded['requests'], (json) => MessageRecallRequest.fromJson(json));
  }

  async reviewMessageRecallRequest(opts: {
    roomId: string;
    requestId: string;
    approve: boolean;
    reason?: string | null;
  }): Promise<void> {
    const { roomId, requestId, approve, reason } = opts;
    const body: JsonMap = { decision: approve ? 'approve' : 'reject' };
    if (reason != null) body['reason'] = reason;
    await this._sendJson((token) => {
      return this._http.patch(this._uri(`/rooms/${roomId}/message-recall-requests/${requestId}`), {
        headers: this._headers(token),
        body: encodeJsonBody(body),
      });
    });
  }

  async forceDeleteMessage(opts: { roomId: string; messageId: string; reason?: string | null }): Promise<Message> {
    const { roomId, messageId, reason } = opts;
    const body: JsonMap = { confirm: true };
    if (reason != null) body['reason'] = reason;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/rooms/${roomId}/messages/${messageId}/force-delete`), {
        headers: this._headers(token),
        body: encodeJsonBody(body),
      });
    });
    return Message.fromJson(requireMap(decoded, 'message'));
  }

  async listMyLiveMemberVolumes(roomId: string): Promise<LiveMemberVolume[]> {
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri(`/rooms/${roomId}/live/me/member-volumes`), {
          headers: this._headers(token),
        });
      },
      { retryTransientFailures: true },
    );
    return mapList(decoded['member_volumes'], (json) => LiveMemberVolume.fromJson(json));
  }

  async updateMyLiveMemberVolume(opts: {
    roomId: string;
    targetUserId: string;
    volume: number;
  }): Promise<LiveMemberVolume> {
    const { roomId, targetUserId, volume } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.patch(this._uri(`/rooms/${roomId}/live/me/member-volumes/${targetUserId}`), {
        headers: this._headers(token),
        body: encodeJsonBody({ volume: volume }),
      });
    });
    return LiveMemberVolume.fromJson(requireMap(decoded, 'member_volume'));
  }

  async moderateLiveParticipant(opts: {
    roomId: string;
    userId: string;
    action: string;
    reason?: string | null;
  }): Promise<void> {
    const { roomId, userId, action, reason } = opts;
    const body: JsonMap = { action: action };
    if (reason != null) body['reason'] = reason;
    await this._sendJson((token) => {
      return this._http.post(this._uri(`/rooms/${roomId}/live/participants/${userId}/moderation`), {
        headers: this._headers(token),
        body: encodeJsonBody(body),
      });
    });
  }

  async getLiveState(roomId: string): Promise<LiveState> {
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri(`/rooms/${roomId}/live`), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    return LiveState.fromJson(requireMap(decoded, 'live'));
  }

  joinLive(opts: {
    roomId: string;
    clientLiveSessionId: string;
    source: string;
    micMuted: boolean;
    headphonesMuted: boolean;
    idempotencyKey?: string | null;
  }): Promise<LiveJoinResult> {
    const { roomId, clientLiveSessionId, source, micMuted, headphonesMuted, idempotencyKey } = opts;
    const requestIdempotencyKey = idempotencyKey ?? newUuid();
    return this._sendJson(
      (token) => {
        return this._http.post(this._uri(`/rooms/${roomId}/live/join`), {
          headers: this._headers(token, { idempotencyKey: requestIdempotencyKey }),
          body: encodeJsonBody({
            client_live_session_id: clientLiveSessionId,
            source: source,
            mic_muted: micMuted,
            headphones_muted: headphonesMuted,
          }),
        });
      },
      { retryTransientFailures: true },
    ).then((decoded) => LiveJoinResult.fromJson(decoded));
  }

  issueScreenAudioToken(opts: { roomId: string }): Promise<ScreenAudioToken> {
    const { roomId } = opts;
    return this._sendJson(
      (token) => {
        return this._http.post(this._uri(`/rooms/${roomId}/live/screen-audio-token`), {
          headers: this._headers(token),
        });
      },
      { retryTransientFailures: true },
    ).then((decoded) => ScreenAudioToken.fromJson(decoded));
  }

  async updateMyLiveState(opts: {
    roomId: string;
    micMuted?: boolean | null;
    headphonesMuted?: boolean | null;
    cameraOn?: boolean | null;
    cameraMirrored?: boolean | null;
    screenSharing?: boolean | null;
    connectionState?: string | null;
  }): Promise<LiveParticipant> {
    const { roomId, micMuted, headphonesMuted, cameraOn, cameraMirrored, screenSharing, connectionState } = opts;
    const body: JsonMap = {};
    if (micMuted != null) body['mic_muted'] = micMuted;
    if (headphonesMuted != null) body['headphones_muted'] = headphonesMuted;
    if (cameraOn != null) body['camera_on'] = cameraOn;
    if (cameraMirrored != null) body['camera_mirrored'] = cameraMirrored;
    if (screenSharing != null) body['screen_sharing'] = screenSharing;
    if (connectionState != null) body['connection_state'] = connectionState;
    const decoded = await this._sendJson((token) => {
      return this._http.patch(this._uri(`/rooms/${roomId}/live/me`), {
        headers: this._headers(token),
        body: encodeJsonBody(body),
      });
    });
    return LiveParticipant.fromJson(requireMap(decoded, 'participant'));
  }

  async updateMyLiveScreenView(opts: { roomId: string; broadcasterUserId?: string | null }): Promise<void> {
    const { roomId, broadcasterUserId } = opts;
    await this._sendJson((token) => {
      return this._http.patch(this._uri(`/rooms/${roomId}/live/me/screen-view`), {
        headers: this._headers(token),
        body: encodeJsonBody({ broadcaster_user_id: broadcasterUserId ?? '' }),
      });
    });
  }

  async getMusicBoxState(roomId: string): Promise<MusicBoxState> {
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri(`/rooms/${roomId}/music-box/state`), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    return MusicBoxState.fromJson(decoded);
  }

  async searchMusicBox(opts: {
    roomId: string;
    keyword: string;
    source?: string | null;
    count?: number | null;
    page?: number | null;
  }): Promise<MusicBoxSearchResult[]> {
    const { roomId, keyword, source, count, page } = opts;
    const query: Record<string, string> = { keyword: keyword };
    if (source != null && source.length > 0) query['source'] = source;
    if (count != null) query['count'] = `${count}`;
    if (page != null) query['page'] = `${page}`;
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri(`/rooms/${roomId}/music-box/search`, query), {
          headers: this._headers(token),
        });
      },
      { retryTransientFailures: true },
    );
    return mapList(decoded['results'], (json) => MusicBoxSearchResult.fromJson(json));
  }

  async listPersonalMusicPlaylists(opts: { page?: number; pageSize?: number } = {}): Promise<PersonalMusicPlaylistPage> {
    const { page = 1, pageSize = 50 } = opts;
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(
          this._uri('/me/music-box/playlists', {
            page: `${page}`,
            page_size: `${pageSize}`,
          }),
          { headers: this._headers(token) },
        );
      },
      { retryTransientFailures: true },
    );
    return PersonalMusicPlaylistPage.fromJson(decoded);
  }

  async createPersonalMusicPlaylist(opts: { name: string }): Promise<PersonalMusicPlaylist> {
    const { name } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri('/me/music-box/playlists'), {
        headers: this._headers(token),
        body: encodeJsonBody({ name: name }),
      });
    });
    return PersonalMusicPlaylist.fromJson(requireMap(decoded, 'playlist'));
  }

  async mergePersonalMusicPlaylists(opts: {
    name: string;
    playlistIds: string[];
  }): Promise<PersonalMusicPlaylistMergeResult> {
    const { name, playlistIds } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri('/me/music-box/playlists/merge'), {
        headers: this._headers(token),
        body: encodeJsonBody({ name: name, playlist_ids: playlistIds }),
      });
    });
    return PersonalMusicPlaylistMergeResult.fromJson(decoded);
  }

  async renamePersonalMusicPlaylist(opts: { playlistId: string; name: string }): Promise<PersonalMusicPlaylist> {
    const { playlistId, name } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.patch(this._uri(`/me/music-box/playlists/${playlistId}`), {
        headers: this._headers(token),
        body: encodeJsonBody({ name: name }),
      });
    });
    return PersonalMusicPlaylist.fromJson(requireMap(decoded, 'playlist'));
  }

  async deletePersonalMusicPlaylist(playlistId: string): Promise<void> {
    await this._sendJson((token) => {
      return this._http.delete(this._uri(`/me/music-box/playlists/${playlistId}`), {
        headers: this._headers(token),
      });
    });
  }

  async pinPersonalMusicPlaylists(opts: { playlistIds: string[] }): Promise<void> {
    const { playlistIds } = opts;
    await this._sendJson((token) => {
      return this._http.patch(this._uri('/me/music-box/playlists/order'), {
        headers: this._headers(token),
        body: encodeJsonBody({ playlist_ids: playlistIds }),
      });
    });
  }

  async movePersonalMusicPlaylist(opts: { playlistId: string; direction: string }): Promise<void> {
    const { playlistId, direction } = opts;
    await this._sendJson((token) => {
      return this._http.patch(this._uri('/me/music-box/playlists/order'), {
        headers: this._headers(token),
        body: encodeJsonBody({
          playlist_id: playlistId,
          direction: direction,
        }),
      });
    });
  }

  async getPersonalMusicPlaylist(opts: {
    playlistId: string;
    page?: number;
    pageSize?: number;
    keyword?: string | null;
    source?: string | null;
  }): Promise<PersonalMusicPlaylistItemsPage> {
    const { playlistId, page = 1, pageSize = 50, keyword, source } = opts;
    const query: Record<string, string> = { page: `${page}`, page_size: `${pageSize}` };
    if (keyword != null && keyword.trim().length > 0) {
      query['keyword'] = keyword.trim();
    }
    if (source != null && source.trim().length > 0) {
      query['source'] = source.trim();
    }
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri(`/me/music-box/playlists/${playlistId}`, query), {
          headers: this._headers(token),
        });
      },
      { retryTransientFailures: true },
    );
    return PersonalMusicPlaylistItemsPage.fromJson(decoded);
  }

  async searchPersonalMusicPlaylistTracks(opts: {
    keyword: string;
    source?: string | null;
    count?: number;
    page?: number;
  }): Promise<MusicBoxSearchResult[]> {
    const { keyword, source, count = 20, page = 1 } = opts;
    const query: Record<string, string> = {
      keyword: keyword,
      count: `${count}`,
      page: `${page}`,
    };
    if (source != null && source.trim().length > 0) {
      query['source'] = source.trim();
    }
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri('/me/music-box/search', query), { headers: this._headers(token) });
      },
      { retryTransientFailures: true },
    );
    return mapList(decoded['results'], (json) => MusicBoxSearchResult.fromJson(json));
  }

  async downloadMusicTrackPreview(opts: { source: string; trackId: string }): Promise<DownloadedFile> {
    const { source, trackId } = opts;
    const response = await this._sendWithAuth((token) => {
      return this._http.post(this._uri('/me/music-box/preview'), {
        headers: this._headers(token),
        body: encodeJsonBody({ source: source, track_id: trackId }),
      });
    });
    this._throwIfFailed(response);
    return new DownloadedFile({
      bytes: response.bodyBytes,
      filename: this._downloadFilename(response),
      mimeType: response.headers['content-type'] ?? 'audio/mp4',
    });
  }

  async addPersonalMusicPlaylistItem(opts: {
    playlistId: string;
    track: MusicBoxSearchResult;
    durationMs?: number | null;
  }): Promise<PersonalMusicPlaylistItem> {
    const { playlistId, track, durationMs } = opts;
    const body: JsonMap = {
      track_id: track.trackId,
      source: track.source,
      title: track.name,
      artists: track.artists,
    };
    if (durationMs != null) body['duration_ms'] = durationMs;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/me/music-box/playlists/${playlistId}/items`), {
        headers: this._headers(token),
        body: encodeJsonBody(body),
      });
    });
    return PersonalMusicPlaylistItem.fromJson(requireMap(decoded, 'item'));
  }

  async batchAddPersonalMusicPlaylistItems(opts: {
    sourcePlaylistId: string;
    targetPlaylistId: string;
    itemIds: string[];
  }): Promise<PersonalMusicPlaylistBatchAddResult> {
    const { sourcePlaylistId, targetPlaylistId, itemIds } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/me/music-box/playlists/${targetPlaylistId}/items/batch-add`), {
        headers: this._headers(token),
        body: encodeJsonBody({
          source_playlist_id: sourcePlaylistId,
          item_ids: itemIds,
        }),
      });
    });
    return PersonalMusicPlaylistBatchAddResult.fromJson(decoded);
  }

  async deletePersonalMusicPlaylistItem(opts: { playlistId: string; itemId: string }): Promise<void> {
    const { playlistId, itemId } = opts;
    await this._sendJson((token) => {
      return this._http.delete(this._uri(`/me/music-box/playlists/${playlistId}/items/${itemId}`), {
        headers: this._headers(token),
      });
    });
  }

  async deletePersonalMusicPlaylistItems(opts: { playlistId: string; itemIds: string[] }): Promise<void> {
    const { playlistId, itemIds } = opts;
    await this._sendJson((token) => {
      return this._http.delete(this._uri(`/me/music-box/playlists/${playlistId}/items`), {
        headers: this._headers(token),
        body: encodeJsonBody({ item_ids: itemIds }),
      });
    });
  }

  async movePersonalMusicPlaylistItem(opts: { playlistId: string; itemId: string; direction: string }): Promise<void> {
    const { playlistId, itemId, direction } = opts;
    await this._sendJson((token) => {
      return this._http.patch(this._uri(`/me/music-box/playlists/${playlistId}/items/order`), {
        headers: this._headers(token),
        body: encodeJsonBody({ item_id: itemId, direction: direction }),
      });
    });
  }

  async reorderPersonalMusicPlaylistItems(opts: { playlistId: string; itemIds: string[] }): Promise<void> {
    const { playlistId, itemIds } = opts;
    await this._sendJson((token) => {
      return this._http.patch(this._uri(`/me/music-box/playlists/${playlistId}/items/order`), {
        headers: this._headers(token),
        body: encodeJsonBody({ item_ids: itemIds }),
      });
    });
  }

  async listRoomMusicPlaylists(opts: {
    roomId: string;
    page?: number;
    pageSize?: number;
  }): Promise<PersonalMusicPlaylistPage> {
    const { roomId, page = 1, pageSize = 50 } = opts;
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(
          this._uri(`/rooms/${roomId}/music-box/playlists`, {
            page: `${page}`,
            page_size: `${pageSize}`,
          }),
          { headers: this._headers(token) },
        );
      },
      { retryTransientFailures: true },
    );
    return PersonalMusicPlaylistPage.fromJson(decoded);
  }

  async createRoomMusicPlaylist(opts: { roomId: string; name: string }): Promise<PersonalMusicPlaylist> {
    const { roomId, name } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/rooms/${roomId}/music-box/playlists`), {
        headers: this._headers(token),
        body: encodeJsonBody({ name: name }),
      });
    });
    return PersonalMusicPlaylist.fromJson(requireMap(decoded, 'playlist'));
  }

  async mergeRoomMusicPlaylists(opts: {
    roomId: string;
    name: string;
    playlistIds: string[];
  }): Promise<PersonalMusicPlaylistMergeResult> {
    const { roomId, name, playlistIds } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/rooms/${roomId}/music-box/playlists/merge`), {
        headers: this._headers(token),
        body: encodeJsonBody({ name: name, playlist_ids: playlistIds }),
      });
    });
    return PersonalMusicPlaylistMergeResult.fromJson(decoded);
  }

  async createRoomMusicPlaylistFromPersonal(opts: {
    roomId: string;
    name: string;
    importPlaylistId: string;
  }): Promise<PersonalMusicPlaylist> {
    const { roomId, name, importPlaylistId } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/rooms/${roomId}/music-box/playlists`), {
        headers: this._headers(token),
        body: encodeJsonBody({
          name: name,
          import_playlist_id: importPlaylistId,
        }),
      });
    });
    return PersonalMusicPlaylist.fromJson(requireMap(decoded, 'playlist'));
  }

  async cloneRoomMusicPlaylistToPersonal(opts: { roomId: string; playlistId: string }): Promise<PersonalMusicPlaylist> {
    const { roomId, playlistId } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/rooms/${roomId}/music-box/playlists/${playlistId}/clone-to-me`), {
        headers: this._headers(token),
      });
    });
    return PersonalMusicPlaylist.fromJson(requireMap(decoded, 'playlist'));
  }

  async cloneSharedMusicPlaylistToPersonal(opts: { roomId: string; messageId: string }): Promise<PersonalMusicPlaylist> {
    const { roomId, messageId } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/rooms/${roomId}/messages/${messageId}/playlist/clone-to-me`), {
        headers: this._headers(token),
      });
    });
    return PersonalMusicPlaylist.fromJson(requireMap(decoded, 'playlist'));
  }

  async renameRoomMusicPlaylist(opts: {
    roomId: string;
    playlistId: string;
    name: string;
  }): Promise<PersonalMusicPlaylist> {
    const { roomId, playlistId, name } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.patch(this._uri(`/rooms/${roomId}/music-box/playlists/${playlistId}`), {
        headers: this._headers(token),
        body: encodeJsonBody({ name: name }),
      });
    });
    return PersonalMusicPlaylist.fromJson(requireMap(decoded, 'playlist'));
  }

  async deleteRoomMusicPlaylist(opts: { roomId: string; playlistId: string }): Promise<void> {
    const { roomId, playlistId } = opts;
    await this._sendJson((token) => {
      return this._http.delete(this._uri(`/rooms/${roomId}/music-box/playlists/${playlistId}`), {
        headers: this._headers(token),
      });
    });
  }

  async pinRoomMusicPlaylists(opts: { roomId: string; playlistIds: string[] }): Promise<void> {
    const { roomId, playlistIds } = opts;
    await this._sendJson((token) => {
      return this._http.patch(this._uri(`/rooms/${roomId}/music-box/playlists/order`), {
        headers: this._headers(token),
        body: encodeJsonBody({ playlist_ids: playlistIds }),
      });
    });
  }

  async moveRoomMusicPlaylist(opts: { roomId: string; playlistId: string; direction: string }): Promise<void> {
    const { roomId, playlistId, direction } = opts;
    await this._sendJson((token) => {
      return this._http.patch(this._uri(`/rooms/${roomId}/music-box/playlists/order`), {
        headers: this._headers(token),
        body: encodeJsonBody({
          playlist_id: playlistId,
          direction: direction,
        }),
      });
    });
  }

  async getRoomMusicPlaylist(opts: {
    roomId: string;
    playlistId: string;
    page?: number;
    pageSize?: number;
    keyword?: string | null;
    source?: string | null;
  }): Promise<PersonalMusicPlaylistItemsPage> {
    const { roomId, playlistId, page = 1, pageSize = 50, keyword, source } = opts;
    const query: Record<string, string> = { page: `${page}`, page_size: `${pageSize}` };
    if (keyword != null && keyword.trim().length > 0) {
      query['keyword'] = keyword.trim();
    }
    if (source != null && source.trim().length > 0) {
      query['source'] = source.trim();
    }
    const decoded = await this._sendJson(
      (token) => {
        return this._http.get(this._uri(`/rooms/${roomId}/music-box/playlists/${playlistId}`, query), {
          headers: this._headers(token),
        });
      },
      { retryTransientFailures: true },
    );
    return PersonalMusicPlaylistItemsPage.fromJson(decoded);
  }

  async addRoomMusicPlaylistItem(opts: {
    roomId: string;
    playlistId: string;
    track: MusicBoxSearchResult;
    durationMs?: number | null;
  }): Promise<PersonalMusicPlaylistItem> {
    const { roomId, playlistId, track, durationMs } = opts;
    const body: JsonMap = {
      track_id: track.trackId,
      source: track.source,
      title: track.name,
      artists: track.artists,
    };
    if (durationMs != null) body['duration_ms'] = durationMs;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/rooms/${roomId}/music-box/playlists/${playlistId}/items`), {
        headers: this._headers(token),
        body: encodeJsonBody(body),
      });
    });
    return PersonalMusicPlaylistItem.fromJson(requireMap(decoded, 'item'));
  }

  async batchAddRoomMusicPlaylistItems(opts: {
    roomId: string;
    sourcePlaylistId: string;
    targetPlaylistId: string;
    itemIds: string[];
  }): Promise<PersonalMusicPlaylistBatchAddResult> {
    const { roomId, sourcePlaylistId, targetPlaylistId, itemIds } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.post(
        this._uri(`/rooms/${roomId}/music-box/playlists/` + `${targetPlaylistId}/items/batch-add`),
        {
          headers: this._headers(token),
          body: encodeJsonBody({
            source_playlist_id: sourcePlaylistId,
            item_ids: itemIds,
          }),
        },
      );
    });
    return PersonalMusicPlaylistBatchAddResult.fromJson(decoded);
  }

  async deleteRoomMusicPlaylistItem(opts: { roomId: string; playlistId: string; itemId: string }): Promise<void> {
    const { roomId, playlistId, itemId } = opts;
    await this._sendJson((token) => {
      return this._http.delete(this._uri(`/rooms/${roomId}/music-box/playlists/${playlistId}/items/${itemId}`), {
        headers: this._headers(token),
      });
    });
  }

  async deleteRoomMusicPlaylistItems(opts: { roomId: string; playlistId: string; itemIds: string[] }): Promise<void> {
    const { roomId, playlistId, itemIds } = opts;
    await this._sendJson((token) => {
      return this._http.delete(this._uri(`/rooms/${roomId}/music-box/playlists/${playlistId}/items`), {
        headers: this._headers(token),
        body: encodeJsonBody({ item_ids: itemIds }),
      });
    });
  }

  async moveRoomMusicPlaylistItem(opts: {
    roomId: string;
    playlistId: string;
    itemId: string;
    direction: string;
  }): Promise<void> {
    const { roomId, playlistId, itemId, direction } = opts;
    await this._sendJson((token) => {
      return this._http.patch(this._uri(`/rooms/${roomId}/music-box/playlists/${playlistId}/items/order`), {
        headers: this._headers(token),
        body: encodeJsonBody({ item_id: itemId, direction: direction }),
      });
    });
  }

  async reorderRoomMusicPlaylistItems(opts: { roomId: string; playlistId: string; itemIds: string[] }): Promise<void> {
    const { roomId, playlistId, itemIds } = opts;
    await this._sendJson((token) => {
      return this._http.patch(this._uri(`/rooms/${roomId}/music-box/playlists/${playlistId}/items/order`), {
        headers: this._headers(token),
        body: encodeJsonBody({ item_ids: itemIds }),
      });
    });
  }

  async queueMusicBoxTrack(opts: {
    roomId: string;
    trackId: string;
    title: string;
    source?: string | null;
    artist?: string | null;
    durationMs?: number | null;
    idempotencyKey?: string | null;
  }): Promise<MusicBoxState> {
    const { roomId, trackId, title, source, artist, durationMs, idempotencyKey } = opts;
    const body: JsonMap = { track_id: trackId, title: title };
    if (source != null && source.length > 0) body['source'] = source;
    if (artist != null && artist.length > 0) body['artist'] = artist;
    if (durationMs != null) body['duration_ms'] = durationMs;
    const requestIdempotencyKey = idempotencyKey ?? newUuid();
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/rooms/${roomId}/music-box/queue`), {
        headers: this._headers(token, { idempotencyKey: requestIdempotencyKey }),
        body: encodeJsonBody(body),
      });
    });
    return MusicBoxState.fromJson(decoded);
  }

  async removeMusicBoxItem(opts: { roomId: string; itemId: string }): Promise<MusicBoxState> {
    const { roomId, itemId } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.delete(this._uri(`/rooms/${roomId}/music-box/queue/${itemId}`), {
        headers: this._headers(token),
      });
    });
    return MusicBoxState.fromJson(decoded);
  }

  async controlMusicBox(opts: {
    roomId: string;
    action: string;
    itemId?: string | null;
    mode?: string | null;
    commandId?: string | null;
    expectedRevision?: number | null;
  }): Promise<MusicBoxState> {
    const { roomId, action, itemId, mode, commandId, expectedRevision } = opts;
    const decoded = await this._sendJson((token) => {
      const body: JsonMap = { action: action };
      if (itemId != null && itemId.trim().length > 0) {
        body['item_id'] = itemId.trim();
      }
      if (mode != null && mode.trim().length > 0) {
        body['mode'] = mode.trim();
      }
      if (commandId != null && commandId.trim().length > 0) {
        body['command_id'] = commandId.trim();
      }
      if (expectedRevision != null) {
        body['expected_revision'] = expectedRevision;
      }
      return this._http.post(this._uri(`/rooms/${roomId}/music-box/control`), {
        headers: this._headers(token),
        body: encodeJsonBody(body),
      });
    });
    return MusicBoxState.fromJson(decoded);
  }

  async activateMusicBoxPlaylist(opts: {
    roomId: string;
    sourceType: MusicBoxActiveSourceType;
    playlistId?: string | null;
    startPlay?: boolean;
    startItemId?: string | null;
  }): Promise<MusicBoxState> {
    const { roomId, sourceType, playlistId, startPlay = true, startItemId } = opts;
    const body: JsonMap = {
      source_type: musicBoxActiveSourceTypeValue(sourceType),
      start_play: startPlay,
    };
    if (playlistId != null && playlistId.trim().length > 0) {
      body['playlist_id'] = playlistId.trim();
    }
    if (startItemId != null && startItemId.trim().length > 0) {
      body['start_item_id'] = startItemId.trim();
    }
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/rooms/${roomId}/music-box/activate`), {
        headers: this._headers(token),
        body: encodeJsonBody(body),
      });
    });
    return MusicBoxState.fromJson(decoded);
  }

  async cloneActiveMusicBoxPlaylist(opts: {
    roomId: string;
    playlistId: string;
    snapshotId: string;
  }): Promise<PersonalMusicPlaylist> {
    const { roomId, playlistId, snapshotId } = opts;
    const decoded = await this._sendJson((token) => {
      return this._http.post(this._uri(`/rooms/${roomId}/music-box/active-playlist/clone`), {
        headers: this._headers(token),
        body: encodeJsonBody({
          playlist_id: playlistId,
          snapshot_id: snapshotId,
        }),
      });
    });
    return PersonalMusicPlaylist.fromJson(requireMap(decoded, 'playlist'));
  }

  private async _sendJson(
    send: _AuthorizedSend,
    opts: { retryTransientFailures?: boolean } = {},
  ): Promise<JsonMap> {
    const decoded = await this._sendJsonValue(send, opts);
    // Dart: `decoded as Map<String, Object?>`.
    if (!isJsonMap(decoded)) {
      throw new TypeError('Expected a JSON object response body');
    }
    return decoded;
  }

  private async _sendJsonValue(send: _AuthorizedSend, opts: { retryTransientFailures?: boolean } = {}): Promise<unknown> {
    const { retryTransientFailures = false } = opts;
    let response: HttpResponse;
    try {
      response = await this._sendWithAuth(send);
    } catch (e) {
      if (!(e instanceof ClientException) || !retryTransientFailures || !this._isRetryableTransportFailure(e)) {
        throw e;
      }
      response = await this._sendWithAuth(send);
    }
    if (retryTransientFailures && this._isRetryableHttpFailure(response.statusCode)) {
      response = await this._sendWithAuth(send);
    }
    this._throwIfFailed(response);
    const decoded = decodeJsonBody(response);
    return decoded ?? {};
  }

  private async _sendWithAuth(send: _AuthorizedSend): Promise<HttpResponse> {
    let token = await this.accessTokenProvider();
    const startedAt = _now();
    let response = await send(token);
    if (response.statusCode === 401) {
      token = await this.accessTokenProvider({ forceRefresh: true });
      response = await send(token);
    }
    const elapsed = _now() - startedAt;
    this.onRequestLatency?.(elapsed);
    this.onServerTime?.(response.headers[gangServerTimeHeader] ?? null);
    return response;
  }

  private _isRetryableTransportFailure(error: ClientException): boolean {
    const message = error.message.toLowerCase();
    return (
      message.includes('connection closed') ||
      message.includes('connection reset') ||
      message.includes('broken pipe') ||
      message.includes('write failed') ||
      message.includes('connection abort') ||
      message.includes('errno = 10053') ||
      message.includes('errno=10053')
    );
  }

  private _isRetryableHttpFailure(statusCode: number): boolean {
    return statusCode === 502 || statusCode === 503 || statusCode === 504;
  }

  private _throwIfFailed(response: HttpResponse): void {
    if (response.statusCode >= 200 && response.statusCode < 300) return;
    throw ApiException.fromResponse(response);
  }

  private _uri(path: string, query?: Record<string, string> | null): string {
    const normalizedBase = this.baseUrl.endsWith('/') ? this.baseUrl.substring(0, this.baseUrl.length - 1) : this.baseUrl;
    const uri = new URL(`${normalizedBase}${path}`);
    if (query == null || Object.keys(query).length === 0) return uri.toString();
    // Dart `uri.replace(queryParameters: query)`: the map replaces the query.
    uri.search = '';
    for (const [key, value] of Object.entries(query)) {
      uri.searchParams.append(key, value);
    }
    return uri.toString();
  }

  private _headers(accessToken: string, opts: { idempotencyKey?: string | null } = {}): HttpHeaders {
    const { idempotencyKey } = opts;
    const headers: HttpHeaders = {
      authorization: `Bearer ${accessToken}`,
      accept: jsonAcceptHeader,
      'content-type': jsonUtf8ContentType,
    };
    if (idempotencyKey != null) headers['idempotency-key'] = idempotencyKey;
    return headers;
  }

  private _downloadFilename(response: HttpResponse): string {
    const disposition = response.headers['content-disposition'] ?? '';
    const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
    if (encoded != null) {
      return decodeURIComponent(encoded[1]!);
    }
    const quoted = /filename="([^"]+)"/.exec(disposition);
    if (quoted != null) return quoted[1]!;
    const unquoted = /filename=([^;]+)/.exec(disposition);
    if (unquoted != null) return unquoted[1]!.trim();
    const mimeType = response.headers['content-type'] ?? '';
    return mimeType.includes('zip') ? 'stickers.zip' : 'sticker';
  }

  close(): void {
    this._httpClient.close?.();
  }
}

export class RoomPage {
  readonly rooms: RoomCard[];
  readonly nextCursor: string | null;

  constructor(opts: { rooms: RoomCard[]; nextCursor: string | null }) {
    this.rooms = opts.rooms;
    this.nextCursor = opts.nextCursor;
  }

  static fromJson(json: JsonMap): RoomPage {
    return new RoomPage({
      rooms: mapList(_requireList(json, 'rooms'), (item) => RoomCard.fromJson(item)),
      nextCursor: asString(json['next_cursor']),
    });
  }
}

export class MessagePage {
  readonly messages: Message[];
  readonly hasMore: boolean;
  readonly nextBefore: string | null;

  constructor(opts: { messages: Message[]; hasMore: boolean; nextBefore: string | null }) {
    this.messages = opts.messages;
    this.hasMore = opts.hasMore;
    this.nextBefore = opts.nextBefore;
  }

  static fromJson(json: JsonMap): MessagePage {
    return new MessagePage({
      messages: mapList(_requireList(json, 'messages'), (item) => Message.fromJson(item)),
      hasMore: requireBool(json, 'has_more'),
      nextBefore: asString(json['next_before']),
    });
  }
}

export class ApiException extends Error {
  readonly statusCode: number;
  readonly code: string;
  readonly requestId: string | null;

  constructor(message: string, opts: { statusCode: number; code: string; requestId: string | null }) {
    super(message);
    this.name = 'ApiException';
    this.statusCode = opts.statusCode;
    this.code = opts.code;
    this.requestId = opts.requestId;
  }

  static fromResponse(response: HttpResponse): ApiException {
    const headerRequestId = response.headers['x-request-id'] ?? null;
    try {
      const decoded = _castMap(decodeJsonBody(response));
      const error = _castNullableMap(decoded['error']);
      const message = _castNullableString(error?.['message']);
      const code = _castNullableString(error?.['code']);
      const requestId = _castNullableString(error?.['request_id']) ?? headerRequestId;
      if (message != null && message.length > 0) {
        return new ApiException(
          localizedServerErrorMessage({
            code: code ?? 'request_failed',
            statusCode: response.statusCode,
            message: message,
          }),
          {
            statusCode: response.statusCode,
            code: code ?? 'request_failed',
            requestId: requestId,
          },
        );
      }
    } catch {
      // Fall through to the status-based message.
    }
    return new ApiException(`请求失败 (${response.statusCode})`, {
      statusCode: response.statusCode,
      code: 'request_failed',
      requestId: headerRequestId,
    });
  }

  get isUnauthorized(): boolean {
    return this.statusCode === 401;
  }

  override toString(): string {
    if (this.requestId == null) return this.message;
    return `${this.message}（请求编号：${this.requestId}）`;
  }
}

async function* _uploadByteStream(
  bytes: Uint8Array,
  opts: { onProgress?: UploadProgressCallback | null; controller?: UploadTransferController | null } = {},
): AsyncGenerator<Uint8Array> {
  const { onProgress, controller } = opts;
  const chunkSize = 64 * 1024;
  const total = bytes.length;
  onProgress?.({ sentBytes: 0, totalBytes: total });
  let sent = 0;
  while (sent < total) {
    if (controller?.isCancelled ?? false) {
      throw new UploadCancelledException();
    }
    await controller?.waitIfPaused();
    if (controller?.isCancelled ?? false) {
      throw new UploadCancelledException();
    }
    const end = sent + chunkSize > total ? total : sent + chunkSize;
    yield bytes.subarray(sent, end);
    sent = end;
    onProgress?.({ sentBytes: sent, totalBytes: total });
  }
}

export function newClientId(prefix: string): string {
  return `${prefix}_${newUuid()}`;
}

export function newUuid(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  bytes[6] = (bytes[6]! & 0x0f) | 0x40;
  bytes[8] = (bytes[8]! & 0x3f) | 0x80;

  const hex = (byte: number): string => byte.toString(16).padStart(2, '0');
  const value = Array.from(bytes, hex).join('');
  return [
    value.substring(0, 8),
    value.substring(8, 12),
    value.substring(12, 16),
    value.substring(16, 20),
    value.substring(20),
  ].join('-');
}

/** `/auth/sessions` answers either a bare list or `{items|sessions: [...]}`. */
function _sessionItems(decoded: unknown): unknown {
  if (Array.isArray(decoded)) return decoded;
  if (isJsonMap(decoded)) return decoded['items'] ?? decoded['sessions'] ?? [];
  return [];
}

function asMapOrNull(value: unknown): JsonMap | null {
  return isJsonMap(value) ? value : null;
}

/** `json[key]! as List<Object?>`. */
function _requireList(json: JsonMap, key: string): unknown[] {
  const list = asList(json[key]);
  if (list == null) throw new JsonFormatException(`Expected list for "${key}"`);
  return list;
}

// Strict Dart `as` casts, so a malformed error body falls back to the
// status-based message exactly like the Dart `catch (_)` does.
function _castMap(value: unknown): JsonMap {
  if (!isJsonMap(value)) throw new TypeError('Expected a JSON object');
  return value;
}

function _castNullableMap(value: unknown): JsonMap | null {
  if (value == null) return null;
  return _castMap(value);
}

function _castNullableString(value: unknown): string | null {
  if (value == null) return null;
  if (typeof value !== 'string') throw new TypeError('Expected a string');
  return value;
}
