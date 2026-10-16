// In-memory state plus the serializers that turn it into the JSON shapes the
// client's `fromJson` factories read (lib/src/protocol/models.dart). Field
// names here are the contract: keep them snake_case and in sync with Dart.
import { forbidden, notFound } from './http.mjs';
import { DAY, iso, nextId, randomToken, unixSeconds } from './util.mjs';

const ADMIN_ROLES = new Set(['owner', 'admin']);
const ROLE_RANK = { owner: 0, admin: 1, member: 2 };
export const REFRESH_TTL_MS = 30 * DAY;

export class Store {
  constructor({ publicUrl, tokenTtlMs }) {
    this.publicUrl = publicUrl;
    this.tokenTtlMs = tokenTtlMs;
    /** Replaced by the app so presence reflects open SSE streams. */
    this.connectedUserIds = () => new Set();

    this.users = new Map();
    this.sessions = new Map();
    this.accessTokens = new Map();
    this.assets = new Map();
    this.rooms = new Map();
    this.invites = new Map();
    this.joinRequests = new Map();
    this.notifications = new Map();
    this.stickerPacks = new Map();
    this.playlists = new Map();
    this.memberVolumes = new Map();
    this.audioSettings = new Map();
    this.emailChallenges = new Map();
    this.resetChallenges = new Map();
    this.verificationTokens = new Map();
    this.resetTokens = new Map();
    this.pushDevices = new Map();
    /** `Idempotency-Key` replays: `${userId}:${key}` -> created entity id. */
    this.idempotencyKeys = new Map();
  }

  // -------------------------------------------------------------------------
  // Users

  addUser(spec) {
    const now = Date.now();
    const user = {
      uid: String(100000 + this.users.size),
      bio: '',
      gender: 'secret',
      email: null,
      emailVerified: false,
      emailPublic: false,
      phoneNumber: null,
      phoneNumberPublic: false,
      avatarAssetId: null,
      defaultAvatarKey: 'blue-3',
      isSuperuser: false,
      isBot: false,
      language: 'zh-Hans',
      status: 'active',
      online: false,
      password: 'demo1234',
      createdAt: now - 120 * DAY,
      usernameUpdatedAt: null,
      ...spec,
    };
    user.displayName ??= user.username;
    this.users.set(user.id, user);
    return user;
  }

  findUserByLogin(login) {
    const needle = String(login ?? '').trim().toLowerCase();
    for (const user of this.users.values()) {
      if (user.status === 'deleted' || user.isBot) continue;
      if (user.username.toLowerCase() === needle || user.email?.toLowerCase() === needle) return user;
    }
    return null;
  }

  requireUser(userId) {
    const user = this.users.get(userId);
    if (!user || user.status === 'deleted') throw notFound('user not found');
    return user;
  }

  isOnline(user) {
    return user.online || this.connectedUserIds().has(user.id);
  }

  userAvatarUrl(user) {
    return user.avatarAssetId ? this.assetUrl(this.assets.get(user.avatarAssetId)) : null;
  }

  /** `UserSummary.fromJson`. Pass `room` to include the user's room nickname and role. */
  userSummary(user, { room = null, commonRoomsFor = null } = {}) {
    const json = {
      id: user.id,
      uid: user.uid,
      username: user.username,
      display_name: user.displayName,
      avatar_url: this.userAvatarUrl(user),
      default_avatar_key: user.defaultAvatarKey,
      bio: user.bio,
      gender: user.gender,
      email: user.emailPublic ? user.email : null,
      email_public: user.emailPublic,
      phone_number: user.phoneNumberPublic ? user.phoneNumber : null,
      phone_number_public: user.phoneNumberPublic,
      is_superuser: user.isSuperuser,
      is_online: this.isOnline(user),
      is_bot: user.isBot,
      is_deleted: user.status === 'deleted',
      is_suspended: user.status === 'suspended',
    };
    const membership = room?.members.get(user.id);
    if (membership) {
      json.room_display_name = membership.roomDisplayName;
      json.room_role = membership.role;
    }
    if (commonRoomsFor) json.common_rooms = this.commonRooms(commonRoomsFor, user.id);
    return json;
  }

  /** `UserCommonRoom.fromJson` entries: rooms both users belong to. */
  commonRooms(viewerId, userId) {
    return [...this.rooms.values()]
      .filter((room) => room.members.has(viewerId) && room.members.has(userId))
      .map((room) => {
        const membership = room.members.get(userId);
        return {
          id: room.id,
          rid: room.rid,
          name: room.name,
          visibility: room.visibility,
          remark_name: room.members.get(viewerId)?.remarkName ?? null,
          avatar_url: this.roomAvatarUrl(room),
          default_avatar_key: room.defaultAvatarKey,
          room_display_name: membership.roomDisplayName,
          room_role: membership.role,
        };
      });
  }

  /** `CurrentUser.fromJson` (GET /me and the `user` of auth/profile responses). */
  currentUser(user) {
    return {
      id: user.id,
      uid: user.uid,
      username: user.username,
      display_name: user.displayName,
      bio: user.bio,
      gender: user.gender,
      email: user.email,
      email_verified: user.emailVerified,
      email_public: user.emailPublic,
      phone_number: user.phoneNumber,
      phone_number_public: user.phoneNumberPublic,
      avatar_url: this.userAvatarUrl(user),
      default_avatar_key: user.defaultAvatarKey,
      is_superuser: user.isSuperuser,
      language: user.language,
      username_updated_at: user.usernameUpdatedAt ? iso(user.usernameUpdatedAt) : null,
      can_change_username_at: user.usernameUpdatedAt ? iso(user.usernameUpdatedAt + DAY) : null,
      created_at: iso(user.createdAt),
      status: user.status,
    };
  }

  // -------------------------------------------------------------------------
  // Sessions and tokens

  createSession(userId, { userAgent = null, ip = null } = {}) {
    const now = Date.now();
    const session = {
      id: nextId('ses'),
      userId,
      refreshToken: randomToken('mock-rt'),
      previousRefreshToken: null,
      userAgent,
      ip,
      location: '本机（mock server）',
      createdAt: now,
      lastUsedAt: now,
      expiresAt: now + REFRESH_TTL_MS,
      revokedAt: null,
    };
    this.sessions.set(session.id, session);
    return session;
  }

  issueAccessToken(session) {
    const token = randomToken('mock-at');
    const expiresAt = Date.now() + this.tokenTtlMs;
    this.accessTokens.set(token, { sessionId: session.id, userId: session.userId, expiresAt });
    session.lastUsedAt = Date.now();
    return { token, expiresAt };
  }

  /** `AuthSession.fromJson`: tokens plus the current user. */
  authSession(session) {
    const { token, expiresAt } = this.issueAccessToken(session);
    return {
      access_token: token,
      refresh_token: session.refreshToken,
      token_type: 'Bearer',
      expires_in: Math.round((expiresAt - Date.now()) / 1000),
      access_token_expires_at: iso(expiresAt),
      refresh_token_expires_at: iso(session.expiresAt),
      session_id: session.id,
      user: this.currentUser(this.users.get(session.userId)),
    };
  }

  /** Resolves a bearer token to `{ user, session }`, or null when invalid/expired. */
  authenticate(token) {
    const entry = token ? this.accessTokens.get(token) : null;
    if (!entry) return null;
    if (entry.expiresAt <= Date.now()) {
      this.accessTokens.delete(token);
      return null;
    }
    const session = this.sessions.get(entry.sessionId);
    const user = this.users.get(entry.userId);
    if (!session || session.revokedAt || !user || user.status === 'deleted') return null;
    session.lastUsedAt = Date.now();
    return { user, session };
  }

  /** Rotates the refresh token; the previous one stays valid once (concurrent refreshes). */
  refreshSession(refreshToken) {
    for (const session of this.sessions.values()) {
      if (session.revokedAt || session.expiresAt <= Date.now()) continue;
      if (session.refreshToken !== refreshToken && session.previousRefreshToken !== refreshToken) continue;
      if (session.refreshToken === refreshToken) {
        session.previousRefreshToken = session.refreshToken;
        session.refreshToken = randomToken('mock-rt');
      }
      return session;
    }
    return null;
  }

  revokeSession(session) {
    session.revokedAt = Date.now();
    for (const [token, entry] of this.accessTokens) if (entry.sessionId === session.id) this.accessTokens.delete(token);
  }

  /** Drops every access token so the next request gets 401 and refreshes. */
  expireAllAccessTokens() {
    const count = this.accessTokens.size;
    this.accessTokens.clear();
    return count;
  }

  /** `UserSession.fromJson` (timestamps are unix seconds). */
  sessionJson(session, currentSessionId) {
    return {
      id: session.id,
      user_agent: session.userAgent,
      ip_address: session.ip,
      location: session.location,
      created_at: unixSeconds(session.createdAt),
      last_used_at: unixSeconds(session.lastUsedAt),
      expires_at: unixSeconds(session.expiresAt),
      revoked_at: session.revokedAt ? unixSeconds(session.revokedAt) : null,
      is_current: session.id === currentSessionId,
    };
  }

  // -------------------------------------------------------------------------
  // Assets

  /**
   * Registers an asset. Supply `bytes`, a lazy `generate()` producing bytes,
   * or an `externalUrl` (served by someone else, e.g. picsum.photos).
   */
  addAsset({ id = nextId('ast'), generate = null, bytes = null, ...rest }) {
    const asset = {
      id,
      mimeType: 'application/octet-stream',
      filename: null,
      externalUrl: null,
      sizeBytes: null,
      width: null,
      height: null,
      ownerUserId: null,
      createdAt: Date.now(),
      ...rest,
      bytes: bytes ?? (generate ? generate() : null),
    };
    if (asset.bytes) asset.sizeBytes = asset.bytes.length;
    this.assets.set(asset.id, asset);
    return asset;
  }

  assetUrl(asset) {
    if (!asset) return null;
    if (asset.externalUrl) return asset.externalUrl;
    const name = asset.filename ? `/${encodeURIComponent(asset.filename)}` : '';
    return `${this.publicUrl}/mock-assets/${asset.id}${name}`;
  }

  /** `UploadedAsset.fromJson`. */
  assetJson(asset) {
    const url = this.assetUrl(asset);
    return {
      id: asset.id,
      url,
      thumbnail_url: asset.mimeType.startsWith('image/') ? url : null,
      mime_type: asset.mimeType,
      filename: asset.filename,
      size_bytes: asset.sizeBytes,
      width: asset.width,
      height: asset.height,
      created_at: iso(asset.createdAt),
    };
  }

  // -------------------------------------------------------------------------
  // Rooms and memberships

  addRoom(spec) {
    const now = Date.now();
    const room = {
      rid: String(800000 + this.rooms.size + 1),
      description: '',
      visibility: 'public',
      joinPolicy: 'approval_required',
      avatarAssetId: null,
      defaultAvatarKey: 'blue-3',
      aiVoiceAnnouncementsEnabled: false,
      gangBotEnabled: true,
      messageRecallPolicy: 'sender_only',
      messageRecallWindowSeconds: null,
      createdAt: now - 90 * DAY,
      ...spec,
      updatedAt: spec.updatedAt ?? now,
      members: new Map(),
      messages: [],
      seq: 0,
      live: { participants: new Map(), updatedAt: now },
      musicBox: null,
      blacklist: new Map(),
      recallRequests: [],
      hiddenMessageIds: new Map(),
    };
    this.rooms.set(room.id, room);
    return room;
  }

  addMember(room, userId, { role = 'member', joinedAt = Date.now(), ...rest } = {}) {
    const membership = {
      userId,
      role,
      joinedAt,
      roomDisplayName: null,
      remarkName: null,
      notificationPolicy: 'all',
      isPinned: false,
      aiVoiceAnnouncementsEnabled: false,
      lastReadSeq: room.seq,
      textMutedUntil: null,
      ...rest,
    };
    room.members.set(userId, membership);
    return membership;
  }

  requireRoom(roomId) {
    const room = this.rooms.get(roomId);
    if (!room) throw notFound('room not found');
    return room;
  }

  /** Returns the caller's membership (superusers get a synthetic one) or throws 403. */
  requireMember(room, user) {
    const membership = room.members.get(user.id);
    if (membership) return membership;
    if (user.isSuperuser) return { userId: user.id, role: 'superuser', joinedAt: Date.now(), lastReadSeq: room.seq };
    throw forbidden('not a room member');
  }

  requireAdmin(room, user) {
    const membership = this.requireMember(room, user);
    if (!this.isAdminRole(membership.role)) throw forbidden('admin required');
    return membership;
  }

  isAdminRole(role) {
    return ADMIN_ROLES.has(role) || role === 'superuser';
  }

  ownerOf(room) {
    for (const membership of room.members.values()) if (membership.role === 'owner') return membership;
    return null;
  }

  roomAvatarUrl(room) {
    return room.avatarAssetId ? this.assetUrl(this.assets.get(room.avatarAssetId)) : null;
  }

  onlineMemberCount(room) {
    let count = 0;
    for (const userId of room.members.keys()) if (this.isOnline(this.users.get(userId))) count += 1;
    return count;
  }

  pendingJoinRequests(room) {
    return [...this.joinRequests.values()].filter((request) => request.roomId === room.id && request.status === 'pending');
  }

  sortedMembers(room) {
    return [...room.members.values()].sort((a, b) => {
      const rank = (ROLE_RANK[a.role] ?? 3) - (ROLE_RANK[b.role] ?? 3);
      if (rank !== 0) return rank;
      const online = Number(this.isOnline(this.users.get(b.userId))) - Number(this.isOnline(this.users.get(a.userId)));
      return online !== 0 ? online : a.joinedAt - b.joinedAt;
    });
  }

  /** `RoomCard.fromJson`, account-scoped for `userId` (unread, pin, remark, policy). */
  roomCard(room, userId) {
    const membership = room.members.get(userId);
    const isAdmin = membership ? this.isAdminRole(membership.role) : false;
    const pending = isAdmin ? this.pendingJoinRequests(room).length : 0;
    const unread = membership ? this.unreadFor(room, userId) : { count: 0, mentions: 0 };
    return {
      id: room.id,
      rid: room.rid,
      name: room.name,
      visibility: room.visibility,
      join_policy: room.joinPolicy,
      description: room.description,
      remark_name: membership?.remarkName ?? null,
      notification_policy: membership?.notificationPolicy ?? 'all',
      is_pinned: membership?.isPinned ?? false,
      avatar_url: this.roomAvatarUrl(room),
      default_avatar_key: room.defaultAvatarKey,
      member_count: room.members.size,
      online_member_count: this.onlineMemberCount(room),
      live_participant_count: room.live.participants.size,
      live_avatar_preview: this.livePreview(room),
      last_message: this.lastMessagePreview(room, userId),
      unread_count: unread.count,
      unread_mention_count: unread.mentions,
      has_pending_join_requests: pending > 0,
      pending_join_request_count: pending,
      ai_voice_announcements_enabled: membership?.aiVoiceAnnouncementsEnabled ?? false,
      gang_bot_available: true,
      gang_bot_enabled: room.gangBotEnabled,
      updated_at: iso(room.updatedAt),
    };
  }

  /** `RoomDetail.fromJson` for `userId`. */
  roomDetail(room, userId) {
    const user = this.users.get(userId);
    const membership = room.members.get(userId) ?? this.requireMember(room, user);
    const creator = this.users.get(room.createdBy);
    return {
      id: room.id,
      rid: room.rid,
      name: room.name,
      visibility: room.visibility,
      description: room.description,
      join_policy: room.joinPolicy,
      remark_name: membership.remarkName ?? null,
      notification_policy: membership.notificationPolicy ?? 'all',
      is_pinned: membership.isPinned ?? false,
      personal_profile: { display_name: membership.roomDisplayName ?? null },
      ai_voice_announcements_enabled: membership.aiVoiceAnnouncementsEnabled ?? false,
      message_recall_policy: room.messageRecallPolicy,
      message_recall_window_seconds: room.messageRecallWindowSeconds,
      can_delete_room: membership.role === 'owner' || user.isSuperuser,
      gang_bot_available: true,
      gang_bot_enabled: room.gangBotEnabled,
      avatar_url: this.roomAvatarUrl(room),
      default_avatar_key: room.defaultAvatarKey,
      member_count: room.members.size,
      online_member_count: this.onlineMemberCount(room),
      created_by: creator ? this.userSummary(creator) : null,
      my_membership: {
        joined_at: iso(membership.joinedAt),
        role: membership.role,
        ai_voice_announcements_enabled: membership.aiVoiceAnnouncementsEnabled ?? false,
        is_pinned: membership.isPinned ?? false,
      },
      live: this.liveState(room),
      created_at: iso(room.createdAt),
      updated_at: iso(room.updatedAt),
    };
  }

  /** `JoinRequest` states for a viewer that is not (yet) a member. */
  joinState(room, userId) {
    if (room.members.has(userId)) return 'joined';
    const pending = [...this.joinRequests.values()].some(
      (request) => request.roomId === room.id && request.userId === userId && request.status === 'pending',
    );
    return pending ? 'pending' : 'none';
  }

  /** `PublicRoom.fromJson` (search results, invites, applications, notifications). */
  publicRoom(room, viewerId) {
    const membership = room.members.get(viewerId);
    const creator = this.users.get(room.createdBy);
    return {
      id: room.id,
      rid: room.rid,
      name: room.name,
      avatar_url: this.roomAvatarUrl(room),
      default_avatar_key: room.defaultAvatarKey,
      visibility: room.visibility,
      join_policy: room.joinPolicy,
      description: room.description,
      member_count: room.members.size,
      online_member_count: this.onlineMemberCount(room),
      live_participant_count: room.live.participants.size,
      joined: Boolean(membership),
      join_state: this.joinState(room, viewerId),
      created_by: creator ? this.userSummary(creator) : null,
      personal_profile: { display_name: membership?.roomDisplayName ?? null },
      my_membership: membership
        ? { joined_at: iso(membership.joinedAt), role: membership.role, ai_voice_announcements_enabled: false }
        : null,
      is_deleted: Boolean(room.deleted),
    };
  }

  /** `SearchRoomContext.fromJson`. */
  searchRoomContext(room, viewerId) {
    return {
      id: room.id,
      rid: room.rid,
      name: room.name,
      remark_name: room.members.get(viewerId)?.remarkName ?? null,
      avatar_url: this.roomAvatarUrl(room),
      default_avatar_key: room.defaultAvatarKey,
    };
  }

  /** `RoomMember.fromJson`. */
  memberJson(room, membership) {
    const user = this.users.get(membership.userId);
    return {
      user: this.userSummary(user),
      role: membership.role,
      joined_at: iso(membership.joinedAt),
      room_display_name: membership.roomDisplayName,
      text_muted_until: membership.textMutedUntil,
      is_online: this.isOnline(user),
    };
  }

  // -------------------------------------------------------------------------
  // Messages

  /** Appends a message. The sender implicitly reads up to it. */
  addMessage(room, spec) {
    room.seq += 1;
    const message = {
      id: nextId('msg'),
      roomId: room.id,
      seq: room.seq,
      type: 'text',
      body: '',
      attachments: [],
      mentions: [],
      quotes: [],
      clientMessageId: null,
      createdAt: Date.now(),
      recalledAt: null,
      recalledBy: null,
      forceDeletedAt: null,
      forceDeletedBy: null,
      ...spec,
    };
    message.clientMessageId ??= `srv_${message.id}`;
    room.messages.push(message);
    room.updatedAt = Math.max(room.updatedAt, message.createdAt);
    const senderMembership = room.members.get(message.senderId);
    if (senderMembership) senderMembership.lastReadSeq = message.seq;
    return message;
  }

  findMessage(room, messageId) {
    const message = room.messages.find((candidate) => candidate.id === messageId);
    if (!message) throw notFound('message not found');
    return message;
  }

  visibleMessages(room, userId) {
    const hidden = room.hiddenMessageIds.get(userId);
    return hidden ? room.messages.filter((message) => !hidden.has(message.id)) : room.messages;
  }

  isRemoved(message) {
    return Boolean(message.recalledAt || message.forceDeletedAt);
  }

  mentionsUser(room, message, userId) {
    const membership = room.members.get(userId);
    return message.mentions.some(
      (mention) =>
        mention.type === 'all' ||
        (mention.type === 'user' && mention.user_id === userId) ||
        (mention.type === 'admins' && membership && this.isAdminRole(membership.role)),
    );
  }

  unreadFor(room, userId) {
    const membership = room.members.get(userId);
    let count = 0;
    let mentions = 0;
    for (const message of room.messages) {
      if (message.seq <= membership.lastReadSeq) continue;
      if (message.senderId === userId || message.type === 'system' || this.isRemoved(message)) continue;
      count += 1;
      if (this.mentionsUser(room, message, userId)) mentions += 1;
    }
    return { count, mentions };
  }

  senderLabel(room, userId) {
    const user = this.users.get(userId);
    return room.members.get(userId)?.roomDisplayName || user?.displayName || user?.username || '用户';
  }

  /** The short text the room list shows for a message. */
  messagePreviewText(room, message) {
    if (message.recalledAt) return '消息已撤回';
    if (message.forceDeletedAt) return '消息已被管理员删除';
    switch (message.type) {
      case 'sticker':
        return `[表情] ${message.attachments[0]?.name ?? ''}`.trim();
      case 'audio':
        return '[语音]';
      case 'music_track':
        return `[歌曲] ${message.attachments[0]?.track?.title ?? ''}`.trim();
      case 'playlist':
        return `[歌单] ${message.attachments[0]?.playlist?.name ?? ''}`.trim();
      case 'system':
        return message.body;
      case 'file': {
        const first = message.attachments[0];
        const asset = first?.assetId ? this.assets.get(first.assetId) : null;
        const label = asset?.mimeType.startsWith('image/') ? '[图片]' : `[文件] ${first?.name ?? ''}`.trim();
        return message.body ? `${label} ${message.body}` : label;
      }
      default:
        return message.body.slice(0, 120);
    }
  }

  /** `LastMessagePreview.fromJson`. */
  lastMessagePreview(room, userId) {
    const messages = this.visibleMessages(room, userId);
    const message = messages[messages.length - 1];
    if (!message) return null;
    return {
      id: message.id,
      type: message.type,
      sender_display_name: message.type === 'system' ? '' : this.senderLabel(room, message.senderId),
      body_preview: this.messagePreviewText(room, message),
      created_at: iso(message.createdAt),
    };
  }

  /** `MessageAttachment.fromJson`. Specs reference assets/users by id so URLs stay current. */
  attachmentJson(spec) {
    const json = { type: spec.type };
    const copy = {
      name: 'name',
      stickerId: 'sticker_id',
      durationMs: 'duration_ms',
      event: 'event',
      fromRole: 'from_role',
      toRole: 'to_role',
      oldValue: 'old_value',
      newValue: 'new_value',
      playlistId: 'playlist_id',
      playlistScope: 'playlist_scope',
      sourceRoomId: 'source_room_id',
      sourceMessageId: 'source_message_id',
      itemId: 'item_id',
      track: 'track',
      playlist: 'playlist',
    };
    for (const [key, field] of Object.entries(copy)) if (spec[key] != null) json[field] = spec[key];
    const asset = spec.assetId ? this.assets.get(spec.assetId) : null;
    if (asset) json.asset = this.assetJson(asset);
    else if (spec.asset) json.asset = spec.asset;
    for (const key of ['user', 'actor', 'target']) {
      const user = spec[`${key}Id`] ? this.users.get(spec[`${key}Id`]) : null;
      if (user) json[key] = this.userSummary(user);
    }
    return json;
  }

  /** Snapshot stored on a message that quotes `quoted` (quotes are immutable once sent). */
  quoteSpec(room, quoted) {
    const preview = quoted.attachments.find((attachment) => {
      if (attachment.type === 'sticker') return true;
      const asset = attachment.assetId ? this.assets.get(attachment.assetId) : null;
      return attachment.type === 'file' && asset?.mimeType.startsWith('image/');
    });
    return {
      messageId: quoted.id,
      senderDisplayName: quoted.type === 'system' ? '' : this.senderLabel(room, quoted.senderId),
      body: this.isRemoved(quoted) ? '' : quoted.type === 'text' ? quoted.body : this.messagePreviewText(room, quoted),
      createdAt: quoted.createdAt,
      previewAttachment: this.isRemoved(quoted) ? null : (preview ?? null),
    };
  }

  /** `Message.fromJson`. Removed messages are tombstoned like the real server does. */
  messageJson(room, message) {
    const sender = this.users.get(message.senderId);
    const membership = room.members.get(message.senderId);
    const removed = this.isRemoved(message);
    const quotes = removed
      ? []
      : message.quotes.map((quote) => ({
          message_id: quote.messageId,
          sender_display_name: quote.senderDisplayName,
          body: quote.body,
          created_at: iso(quote.createdAt),
          preview_attachment: quote.previewAttachment ? this.attachmentJson(quote.previewAttachment) : null,
        }));
    const userRef = (id) => (id ? this.userSummary(this.users.get(id)) : null);
    return {
      id: message.id,
      room_id: room.id,
      sender: this.userSummary(sender),
      client_message_id: message.clientMessageId,
      type: message.type,
      body: removed ? '' : message.body,
      created_at: iso(message.createdAt),
      attachments: removed ? [] : message.attachments.map((spec) => this.attachmentJson(spec)),
      mentions: removed ? [] : message.mentions,
      quote: quotes[0] ?? null,
      quotes,
      is_recalled: Boolean(message.recalledAt),
      recalled_at: message.recalledAt ? iso(message.recalledAt) : null,
      recalled_by: userRef(message.recalledBy),
      is_force_deleted: Boolean(message.forceDeletedAt),
      force_deleted_at: message.forceDeletedAt ? iso(message.forceDeletedAt) : null,
      force_deleted_by: userRef(message.forceDeletedBy),
      sender_room_display_name: membership?.roomDisplayName ?? null,
      sender_room_role: membership?.role ?? null,
    };
  }

  // -------------------------------------------------------------------------
  // Live (voice channel)

  /** `LiveParticipant.fromJson`. */
  participantJson(room, participant) {
    const user = this.users.get(participant.userId);
    const membership = room.members.get(participant.userId);
    return {
      live_session_id: participant.liveSessionId,
      user: this.userSummary(user),
      joined_at: iso(participant.joinedAt),
      mic_muted: participant.micMuted,
      mic_blocked: participant.micBlocked,
      headphones_muted: participant.headphonesMuted,
      headphones_blocked: participant.headphonesBlocked,
      headphones_listening: !participant.headphonesMuted,
      voice_blocked: participant.voiceBlocked,
      camera_on: participant.cameraOn,
      camera_mirrored: participant.cameraMirrored,
      screen_sharing: participant.screenSharing,
      screen_viewers: [...participant.screenViewerIds]
        .map((id) => this.users.get(id))
        .filter(Boolean)
        .map((viewer) => this.userSummary(viewer)),
      connection_state: participant.connectionState,
      room_display_name: membership?.roomDisplayName ?? null,
      room_role: membership?.role ?? null,
    };
  }

  /** `LiveState.fromJson`. */
  liveState(room) {
    const participants = [...room.live.participants.values()].sort((a, b) => a.joinedAt - b.joinedAt);
    return {
      room_id: room.id,
      participant_count: participants.length,
      participants: participants.map((participant) => this.participantJson(room, participant)),
      updated_at: iso(room.live.updatedAt),
    };
  }

  livePreview(room) {
    return [...room.live.participants.values()]
      .sort((a, b) => a.joinedAt - b.joinedAt)
      .slice(0, 5)
      .map((participant) => this.userSummary(this.users.get(participant.userId)));
  }

  newParticipant(userId, overrides = {}) {
    return {
      liveSessionId: nextId('lvs'),
      userId,
      joinedAt: Date.now(),
      micMuted: false,
      micBlocked: false,
      headphonesMuted: false,
      headphonesBlocked: false,
      voiceBlocked: false,
      cameraOn: false,
      cameraMirrored: false,
      screenSharing: false,
      screenViewerIds: new Set(),
      watching: null,
      connectionState: 'connected',
      ...overrides,
    };
  }

  // -------------------------------------------------------------------------
  // Invites, join requests, notifications

  /** `RoomInvite.fromJson`. */
  inviteJson(invite) {
    const room = this.rooms.get(invite.roomId);
    const inviter = this.users.get(invite.inviterId);
    return {
      id: invite.id,
      status: invite.status,
      room: this.publicRoom(room, invite.inviteeId),
      inviter: this.userSummary(inviter),
      created_at: iso(invite.createdAt),
      updated_at: iso(invite.updatedAt),
      room_exists: !room.deleted,
      inviter_exists: inviter.status !== 'deleted',
      invalid_reason: null,
    };
  }

  /** `JoinRequest.fromJson` (the admin's review queue). */
  joinRequestJson(request) {
    return {
      id: request.id,
      status: request.status,
      reason: request.reason,
      source: request.source,
      inviters: request.inviterIds.map((id) => this.userSummary(this.users.get(id))),
      user: this.userSummary(this.users.get(request.userId)),
      created_at: iso(request.createdAt),
    };
  }

  /** `RoomApplication.fromJson` (the applicant's own view of a join request). */
  applicationJson(request) {
    const reviewer = request.reviewerId ? this.users.get(request.reviewerId) : null;
    return {
      id: request.id,
      status: request.status,
      reason: request.reason,
      room: this.publicRoom(this.rooms.get(request.roomId), request.userId),
      created_at: iso(request.createdAt),
      updated_at: iso(request.updatedAt),
      reviewed_at: request.reviewedAt ? iso(request.reviewedAt) : null,
      reviewer: reviewer ? this.userSummary(reviewer) : null,
      reviewer_exists: reviewer ? reviewer.status !== 'deleted' : true,
      request_notification_deleted: request.requestNotificationDeleted,
      review_notification_deleted: request.reviewNotificationDeleted,
    };
  }

  /** `RoomEventNotification.fromJson`. */
  notificationJson(notification) {
    const room = this.rooms.get(notification.roomId);
    const actor = notification.actorId ? this.users.get(notification.actorId) : null;
    return {
      id: notification.id,
      type: notification.type,
      room: this.publicRoom(room, notification.userId),
      room_exists: !room.deleted,
      actor: actor ? this.userSummary(actor) : null,
      actor_exists: actor ? actor.status !== 'deleted' : false,
      from_role: notification.fromRole ?? null,
      to_role: notification.toRole ?? null,
      message_id: notification.messageId ?? null,
      message_preview: notification.messagePreview ?? null,
      created_at: iso(notification.createdAt),
      read_at: notification.readAt ? iso(notification.readAt) : null,
    };
  }

  // -------------------------------------------------------------------------
  // Stickers

  /** `Sticker.fromJson`. */
  stickerJson(sticker) {
    return {
      id: sticker.id,
      name: sticker.name,
      sort_order: sticker.sortOrder,
      asset: this.assetJson(this.assets.get(sticker.assetId)),
    };
  }

  /** `StickerPack.fromJson`. */
  stickerPackJson(pack) {
    return {
      id: pack.id,
      scope: pack.scope,
      room_id: pack.roomId,
      owner_user_id: pack.ownerUserId,
      name: pack.name,
      sort_order: pack.sortOrder,
      updated_at: iso(pack.updatedAt),
      stickers: [...pack.stickers].sort((a, b) => a.sortOrder - b.sortOrder).map((sticker) => this.stickerJson(sticker)),
    };
  }

  findSticker(stickerId) {
    for (const pack of this.stickerPacks.values()) {
      const sticker = pack.stickers.find((candidate) => candidate.id === stickerId);
      if (sticker) return { pack, sticker };
    }
    return null;
  }
}
