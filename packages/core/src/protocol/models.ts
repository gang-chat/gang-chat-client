import {
  type JsonMap,
  asBool,
  asDate,
  asInt,
  asList,
  asMap,
  asString,
  isJsonMap,
  mapList,
  requireBool,
  requireDate,
  requireInt,
  requireMap,
  requireString,
  toIsoString,
} from '../lib/json';

export const defaultUserLanguage = 'zh-Hans';

export class AppVersionInfo {
  readonly latestVersion: string;
  readonly minimumSupportedVersion: string | null;
  readonly releaseNotes: string | null;
  readonly downloadUrl: string | null;

  constructor(opts: {
    latestVersion: string;
    minimumSupportedVersion?: string | null;
    releaseNotes?: string | null;
    downloadUrl?: string | null;
  }) {
    this.latestVersion = opts.latestVersion;
    this.minimumSupportedVersion = opts.minimumSupportedVersion ?? null;
    this.releaseNotes = opts.releaseNotes ?? null;
    this.downloadUrl = opts.downloadUrl ?? null;
  }

  static fromJson(json: JsonMap): AppVersionInfo {
    return new AppVersionInfo({
      latestVersion: _stringFromJson(json, ['latest_version', 'version']) ?? '',
      minimumSupportedVersion: _stringFromJson(json, ['minimum_supported_version', 'minimum_version']),
      releaseNotes: _stringFromJson(json, ['release_notes', 'notes']),
      downloadUrl: _stringFromJson(json, ['download_url']),
    });
  }
}

export class UserCommonRoom {
  readonly id: string;
  readonly rid: string;
  readonly name: string;
  readonly visibility: string;
  readonly remarkName: string | null;
  readonly avatarUrl: string | null;
  readonly defaultAvatarKey: string;
  readonly roomDisplayName: string | null;
  readonly roomRole: string | null;

  constructor(opts: {
    id: string;
    rid: string;
    name: string;
    visibility?: string;
    remarkName?: string | null;
    avatarUrl?: string | null;
    defaultAvatarKey?: string;
    roomDisplayName?: string | null;
    roomRole?: string | null;
  }) {
    this.id = opts.id;
    this.rid = opts.rid;
    this.name = opts.name;
    this.visibility = opts.visibility ?? 'private';
    this.remarkName = opts.remarkName ?? null;
    this.avatarUrl = opts.avatarUrl ?? null;
    this.defaultAvatarKey = opts.defaultAvatarKey ?? 'blue-3';
    this.roomDisplayName = opts.roomDisplayName ?? null;
    this.roomRole = opts.roomRole ?? null;
  }

  static fromJson(json: JsonMap): UserCommonRoom {
    return new UserCommonRoom({
      id: _stringFromJson(json, ['id', 'room_id']) ?? '',
      rid: _stringFromJson(json, ['rid', 'room_rid']) ?? '',
      name: _stringFromJson(json, ['name', 'room_name']) ?? '',
      visibility: _stringFromJson(json, ['visibility', 'room_visibility']) ?? 'private',
      remarkName: _stringFromJson(json, ['remark_name', 'room_remark', 'room_remark_name']),
      avatarUrl: _stringFromJson(json, ['avatar_url', 'room_avatar_url']),
      defaultAvatarKey: _stringFromJson(json, ['default_avatar_key', 'room_default_avatar_key']) ?? 'blue-3',
      roomDisplayName: _stringFromJson(json, [
        'room_display_name',
        'room_username',
        'room_nickname',
        'room_remark',
        'member_display_name',
      ]),
      roomRole:
        _stringFromJson(json, ['room_role', 'membership_role']) ??
        _stringFromJson(_nullableMap(json['membership']), ['role']),
    });
  }

  get isUsable(): boolean {
    return this.id.trim().length > 0 && this.name.trim().length > 0;
  }
}

export class UserSummary {
  readonly id: string;
  readonly username: string;
  readonly displayName: string;
  readonly avatarUrl: string | null;
  readonly defaultAvatarKey: string;
  readonly uid: string | null;
  readonly bio: string | null;
  readonly gender: string | null;
  readonly email: string | null;
  readonly emailPublic: boolean | null;
  readonly phoneNumber: string | null;
  readonly phoneNumberPublic: boolean | null;
  readonly roomDisplayName: string | null;
  readonly roomRole: string | null;
  readonly isSuperuser: boolean;
  readonly isOnline: boolean | null;
  readonly commonRooms: UserCommonRoom[];
  readonly isDeleted: boolean;
  readonly isSuspended: boolean;

  /**
   * gang-bot, the room AI. It is not a user account: there is no profile to
   * open, and its messages carry an AI badge.
   */
  readonly isBot: boolean;

  constructor(opts: {
    id: string;
    username: string;
    displayName: string;
    avatarUrl: string | null;
    defaultAvatarKey: string;
    uid?: string | null;
    bio?: string | null;
    gender?: string | null;
    email?: string | null;
    emailPublic?: boolean | null;
    phoneNumber?: string | null;
    phoneNumberPublic?: boolean | null;
    roomDisplayName?: string | null;
    roomRole?: string | null;
    isSuperuser?: boolean;
    isOnline?: boolean | null;
    commonRooms?: UserCommonRoom[];
    isDeleted?: boolean;
    isSuspended?: boolean;
    isBot?: boolean;
  }) {
    this.id = opts.id;
    this.username = opts.username;
    this.displayName = opts.displayName;
    this.avatarUrl = opts.avatarUrl;
    this.defaultAvatarKey = opts.defaultAvatarKey;
    this.uid = opts.uid ?? null;
    this.bio = opts.bio ?? null;
    this.gender = opts.gender ?? null;
    this.email = opts.email ?? null;
    this.emailPublic = opts.emailPublic ?? null;
    this.phoneNumber = opts.phoneNumber ?? null;
    this.phoneNumberPublic = opts.phoneNumberPublic ?? null;
    this.roomDisplayName = opts.roomDisplayName ?? null;
    this.roomRole = opts.roomRole ?? null;
    this.isSuperuser = opts.isSuperuser ?? false;
    this.isOnline = opts.isOnline ?? null;
    this.commonRooms = opts.commonRooms ?? [];
    this.isDeleted = opts.isDeleted ?? false;
    this.isSuspended = opts.isSuspended ?? false;
    this.isBot = opts.isBot ?? false;
  }

  static fromJson(json: JsonMap): UserSummary {
    const username = requireString(json, 'username');
    return new UserSummary({
      id: requireString(json, 'id'),
      username,
      displayName: asString(json['display_name']) ?? username,
      avatarUrl: asString(json['avatar_url']),
      defaultAvatarKey: asString(json['default_avatar_key']) ?? 'blue-3',
      uid: _stringFromJson(json, ['uid', 'user_uid']),
      bio: _stringFromJson(json, ['bio', 'signature']),
      gender: _stringFromJson(json, ['gender']),
      email: _stringFromJson(json, ['email']),
      emailPublic: _boolFromJson(json, ['email_public']),
      phoneNumber: _stringFromJson(json, ['phone_number', 'phone']),
      phoneNumberPublic: _boolFromJson(json, ['phone_number_public', 'phone_public']),
      roomDisplayName: _stringFromJson(json, [
        'room_display_name',
        'room_username',
        'room_nickname',
        'room_remark',
        'remark_name',
        'remark',
      ]),
      roomRole:
        _stringFromJson(json, ['room_role', 'membership_role']) ??
        _stringFromJson(_nullableMap(json['membership']), ['role']) ??
        _stringFromJson(json, ['role']),
      isSuperuser: asBool(json['is_superuser']) ?? false,
      isOnline:
        _boolFromJson(json, ['is_online', 'online']) ??
        _onlineFromStatus(_stringFromJson(json, ['presence', 'presence_status', 'status', 'connection_state'])),
      commonRooms: _listOfMaps(json['common_rooms'])
        .map((room) => UserCommonRoom.fromJson(room))
        .filter((room) => room.isUsable),
      isDeleted: asBool(json['is_deleted']) ?? false,
      isSuspended: asBool(json['is_suspended']) ?? _stringFromJson(json, ['account_status']) === 'suspended',
      isBot: asBool(json['is_bot']) ?? false,
    });
  }

  copyWith(
    opts: {
      displayName?: string | null;
      avatarUrl?: string | null;
      defaultAvatarKey?: string | null;
      uid?: string | null;
      bio?: string | null;
      gender?: string | null;
      email?: string | null;
      emailPublic?: boolean | null;
      phoneNumber?: string | null;
      phoneNumberPublic?: boolean | null;
      roomDisplayName?: string | null;
      roomRole?: string | null;
      isSuperuser?: boolean | null;
      isOnline?: boolean | null;
      commonRooms?: UserCommonRoom[] | null;
      isDeleted?: boolean | null;
      isSuspended?: boolean | null;
      isBot?: boolean | null;
    } = {},
  ): UserSummary {
    return new UserSummary({
      id: this.id,
      username: this.username,
      displayName: opts.displayName ?? this.displayName,
      avatarUrl: opts.avatarUrl ?? this.avatarUrl,
      defaultAvatarKey: opts.defaultAvatarKey ?? this.defaultAvatarKey,
      uid: opts.uid ?? this.uid,
      bio: opts.bio ?? this.bio,
      gender: opts.gender ?? this.gender,
      email: opts.email ?? this.email,
      emailPublic: opts.emailPublic ?? this.emailPublic,
      phoneNumber: opts.phoneNumber ?? this.phoneNumber,
      phoneNumberPublic: opts.phoneNumberPublic ?? this.phoneNumberPublic,
      roomDisplayName: opts.roomDisplayName ?? this.roomDisplayName,
      roomRole: opts.roomRole ?? this.roomRole,
      isSuperuser: opts.isSuperuser ?? this.isSuperuser,
      isOnline: opts.isOnline ?? this.isOnline,
      commonRooms: opts.commonRooms ?? this.commonRooms,
      isDeleted: opts.isDeleted ?? this.isDeleted,
      isSuspended: opts.isSuspended ?? this.isSuspended,
      isBot: opts.isBot ?? this.isBot,
    });
  }

  mergeMissing(fallback: UserSummary): UserSummary {
    if (this.isDeleted) return this;
    if (fallback.isDeleted) return fallback;
    const nonEmptyOrFallback = (value: string | null, fallbackValue: string | null): string | null => {
      if (value != null && value.trim().length > 0) return value;
      return fallbackValue;
    };

    return new UserSummary({
      id: this.id,
      username: this.username,
      displayName: this.displayName.trim().length === 0 ? fallback.displayName : this.displayName,
      avatarUrl: this.avatarUrl ?? fallback.avatarUrl,
      defaultAvatarKey: this.defaultAvatarKey,
      uid: nonEmptyOrFallback(this.uid, fallback.uid),
      bio: nonEmptyOrFallback(this.bio, fallback.bio),
      gender: nonEmptyOrFallback(this.gender, fallback.gender),
      email: nonEmptyOrFallback(this.email, fallback.email),
      emailPublic: this.emailPublic ?? fallback.emailPublic,
      phoneNumber: nonEmptyOrFallback(this.phoneNumber, fallback.phoneNumber),
      phoneNumberPublic: this.phoneNumberPublic ?? fallback.phoneNumberPublic,
      roomDisplayName: nonEmptyOrFallback(this.roomDisplayName, fallback.roomDisplayName),
      roomRole: nonEmptyOrFallback(this.roomRole, fallback.roomRole),
      isSuperuser: this.isSuperuser || fallback.isSuperuser,
      isOnline: this.isOnline ?? fallback.isOnline,
      commonRooms: this.commonRooms.length > 0 ? this.commonRooms : fallback.commonRooms,
      isDeleted: false,
      isSuspended: this.isSuspended || fallback.isSuspended,
      isBot: this.isBot || fallback.isBot,
    });
  }
}

export class CurrentUser {
  readonly id: string;
  readonly uid: string;
  readonly username: string;
  readonly displayName: string;
  readonly bio: string;
  readonly gender: string;
  readonly email: string | null;
  readonly emailVerified: boolean;
  readonly emailPublic: boolean;
  readonly phoneNumber: string | null;
  readonly phoneNumberPublic: boolean;
  readonly avatarUrl: string | null;
  readonly defaultAvatarKey: string;
  readonly isSuperuser: boolean;
  readonly language: string;
  readonly usernameUpdatedAt: Date | null;
  readonly canChangeUsernameAt: Date | null;
  readonly createdAt: Date | null;
  readonly status: string | null;

  constructor(opts: {
    id: string;
    uid: string;
    username: string;
    displayName: string;
    bio: string;
    gender: string;
    email: string | null;
    emailVerified?: boolean;
    emailPublic: boolean;
    phoneNumber: string | null;
    phoneNumberPublic: boolean;
    avatarUrl: string | null;
    defaultAvatarKey: string;
    isSuperuser: boolean;
    createdAt: Date | null;
    language?: string;
    usernameUpdatedAt?: Date | null;
    canChangeUsernameAt?: Date | null;
    status?: string | null;
  }) {
    this.id = opts.id;
    this.uid = opts.uid;
    this.username = opts.username;
    this.displayName = opts.displayName;
    this.bio = opts.bio;
    this.gender = opts.gender;
    this.email = opts.email;
    this.emailVerified = opts.emailVerified ?? false;
    this.emailPublic = opts.emailPublic;
    this.phoneNumber = opts.phoneNumber;
    this.phoneNumberPublic = opts.phoneNumberPublic;
    this.avatarUrl = opts.avatarUrl;
    this.defaultAvatarKey = opts.defaultAvatarKey;
    this.isSuperuser = opts.isSuperuser;
    this.createdAt = opts.createdAt;
    this.language = opts.language ?? defaultUserLanguage;
    this.usernameUpdatedAt = opts.usernameUpdatedAt ?? null;
    this.canChangeUsernameAt = opts.canChangeUsernameAt ?? null;
    this.status = opts.status ?? null;
  }

  static fromJson(json: JsonMap): CurrentUser {
    const username = requireString(json, 'username');
    const id = requireString(json, 'id');
    return new CurrentUser({
      id,
      uid: asString(json['uid']) ?? id,
      username,
      displayName: asString(json['display_name']) ?? username,
      bio: asString(json['bio']) ?? '',
      gender: asString(json['gender']) ?? 'secret',
      email: asString(json['email']),
      emailVerified: asBool(json['email_verified']) ?? false,
      emailPublic: asBool(json['email_public']) ?? false,
      phoneNumber: asString(json['phone_number']),
      phoneNumberPublic: asBool(json['phone_number_public']) ?? false,
      avatarUrl: asString(json['avatar_url']),
      defaultAvatarKey: asString(json['default_avatar_key']) ?? 'blue-3',
      isSuperuser: asBool(json['is_superuser']) ?? false,
      language: _stringFromJson(json, ['language', 'locale']) ?? defaultUserLanguage,
      usernameUpdatedAt: _parseDateTime(json['username_updated_at']),
      canChangeUsernameAt: _parseDateTime(json['can_change_username_at']),
      createdAt: _parseDateTime(json['created_at']),
      status: asString(json['status']),
    });
  }

  toSummary(): UserSummary {
    return new UserSummary({
      id: this.id,
      uid: this.uid,
      username: this.username,
      displayName: this.displayName,
      bio: this.bio,
      gender: this.gender,
      email: this.email,
      emailPublic: this.emailPublic,
      phoneNumber: this.phoneNumber,
      phoneNumberPublic: this.phoneNumberPublic,
      avatarUrl: this.avatarUrl,
      defaultAvatarKey: this.defaultAvatarKey,
      isSuperuser: this.isSuperuser,
      isOnline: _onlineFromStatus(this.status) ?? true,
      isSuspended: this.status?.trim().toLowerCase() === 'suspended',
    });
  }
}

export class UserSearchPage {
  readonly users: UserSummary[];
  readonly nextCursor: string | null;
  readonly totalCount: number | null;

  constructor(opts: { users: UserSummary[]; nextCursor?: string | null; totalCount?: number | null }) {
    this.users = opts.users;
    this.nextCursor = opts.nextCursor ?? null;
    this.totalCount = opts.totalCount ?? null;
  }

  static fromJson(json: JsonMap): UserSearchPage {
    return new UserSearchPage({
      users: _listOfMaps(json['users']).map((user) => UserSummary.fromJson(user)),
      nextCursor: asString(json['next_cursor']),
      totalCount: _nullableInt(json['total_count']),
    });
  }
}

export class UserAudioSettings {
  readonly defaultAudioInputVolume: number;
  readonly defaultAudioOutputVolume: number;
  readonly liveMicInputVolume: number;
  readonly liveVoiceOutputVolume: number;
  readonly liveScreenShareOutputVolume: number;
  readonly liveMusicOutputVolume: number;
  readonly updatedAt: Date | null;

  constructor(opts: {
    defaultAudioInputVolume: number;
    defaultAudioOutputVolume: number;
    liveMicInputVolume: number;
    liveVoiceOutputVolume: number;
    liveScreenShareOutputVolume: number;
    liveMusicOutputVolume: number;
    updatedAt?: Date | null;
  }) {
    this.defaultAudioInputVolume = opts.defaultAudioInputVolume;
    this.defaultAudioOutputVolume = opts.defaultAudioOutputVolume;
    this.liveMicInputVolume = opts.liveMicInputVolume;
    this.liveVoiceOutputVolume = opts.liveVoiceOutputVolume;
    this.liveScreenShareOutputVolume = opts.liveScreenShareOutputVolume;
    this.liveMusicOutputVolume = opts.liveMusicOutputVolume;
    this.updatedAt = opts.updatedAt ?? null;
  }

  static fromJson(json: JsonMap): UserAudioSettings {
    const volume = (key: string): number => _nullableInt(json[key]) ?? 100;
    return new UserAudioSettings({
      defaultAudioInputVolume: volume('default_audio_input_volume'),
      defaultAudioOutputVolume: volume('default_audio_output_volume'),
      liveMicInputVolume: volume('live_mic_input_volume'),
      liveVoiceOutputVolume: volume('live_voice_output_volume'),
      liveScreenShareOutputVolume: volume('live_screen_share_output_volume'),
      liveMusicOutputVolume: volume('live_music_output_volume'),
      updatedAt: _parseDateTime(json['updated_at']),
    });
  }
}

export class UserSession {
  readonly id: string;
  readonly userAgent: string | null;
  readonly ipAddress: string | null;
  readonly location: string;
  readonly createdAt: Date;
  readonly lastUsedAt: Date;
  readonly expiresAt: Date;
  readonly revokedAt: Date | null;
  readonly isCurrent: boolean;

  constructor(opts: {
    id: string;
    userAgent: string | null;
    ipAddress: string | null;
    location: string;
    createdAt: Date;
    lastUsedAt: Date;
    expiresAt: Date;
    revokedAt: Date | null;
    isCurrent: boolean;
  }) {
    this.id = opts.id;
    this.userAgent = opts.userAgent;
    this.ipAddress = opts.ipAddress;
    this.location = opts.location;
    this.createdAt = opts.createdAt;
    this.lastUsedAt = opts.lastUsedAt;
    this.expiresAt = opts.expiresAt;
    this.revokedAt = opts.revokedAt;
    this.isCurrent = opts.isCurrent;
  }

  static fromJson(json: JsonMap): UserSession {
    return new UserSession({
      id: requireString(json, 'id'),
      userAgent: asString(json['user_agent']),
      ipAddress: asString(json['ip_address']),
      location: asString(json['location']) ?? '未知地点',
      createdAt: _fromUnixSeconds(requireInt(json, 'created_at')),
      lastUsedAt: _fromUnixSeconds(requireInt(json, 'last_used_at')),
      expiresAt: _fromUnixSeconds(requireInt(json, 'expires_at')),
      revokedAt: _nullableUnixSeconds(json['revoked_at']),
      isCurrent: requireBool(json, 'is_current'),
    });
  }

  get isActive(): boolean {
    return this.revokedAt == null && this.expiresAt.getTime() > Date.now();
  }
}

export class UploadedAsset {
  readonly id: string;
  readonly url: string;
  readonly thumbnailUrl: string | null;
  readonly mimeType: string;
  readonly filename: string | null;
  readonly sizeBytes: number | null;
  readonly width: number | null;
  readonly height: number | null;
  readonly createdAt: Date | null;

  constructor(opts: {
    id: string;
    url: string;
    thumbnailUrl: string | null;
    mimeType: string;
    filename?: string | null;
    sizeBytes?: number | null;
    width?: number | null;
    height?: number | null;
    createdAt?: Date | null;
  }) {
    this.id = opts.id;
    this.url = opts.url;
    this.thumbnailUrl = opts.thumbnailUrl;
    this.mimeType = opts.mimeType;
    this.filename = opts.filename ?? null;
    this.sizeBytes = opts.sizeBytes ?? null;
    this.width = opts.width ?? null;
    this.height = opts.height ?? null;
    this.createdAt = opts.createdAt ?? null;
  }

  static fromJson(json: JsonMap): UploadedAsset {
    return new UploadedAsset({
      id: requireString(json, 'id'),
      url: requireString(json, 'url'),
      thumbnailUrl: asString(json['thumbnail_url']),
      mimeType: asString(json['mime_type']) ?? 'application/octet-stream',
      filename: asString(json['filename']),
      sizeBytes: _nullableInt(json['size_bytes']),
      width: _nullableInt(json['width']),
      height: _nullableInt(json['height']),
      createdAt: _parseDateTime(json['created_at']),
    });
  }

  toJson(): JsonMap {
    const json: JsonMap = {
      id: this.id,
      url: this.url,
      thumbnail_url: this.thumbnailUrl,
      mime_type: this.mimeType,
    };
    if (this.filename != null) json['filename'] = this.filename;
    if (this.sizeBytes != null) json['size_bytes'] = this.sizeBytes;
    json['width'] = this.width;
    json['height'] = this.height;
    json['created_at'] = this.createdAt == null ? null : toIsoString(this.createdAt);
    return json;
  }
}

export class StickerPack {
  readonly id: string;
  readonly scope: string;
  readonly roomId: string | null;
  readonly name: string;
  readonly stickers: Sticker[];
  readonly sortOrder: number;
  readonly updatedAt: Date | null;

  constructor(opts: {
    id: string;
    scope: string;
    roomId: string | null;
    name: string;
    stickers: Sticker[];
    sortOrder: number;
    updatedAt: Date | null;
  }) {
    this.id = opts.id;
    this.scope = opts.scope;
    this.roomId = opts.roomId;
    this.name = opts.name;
    this.stickers = opts.stickers;
    this.sortOrder = opts.sortOrder;
    this.updatedAt = opts.updatedAt;
  }

  static fromJson(json: JsonMap): StickerPack {
    return new StickerPack({
      id: requireString(json, 'id'),
      scope: asString(json['scope']) ?? 'personal',
      roomId: asString(json['room_id']),
      name: asString(json['name']) ?? 'Stickers',
      stickers: _listOfMaps(json['stickers']).map((sticker) => Sticker.fromJson(sticker)),
      sortOrder: _nullableInt(json['sort_order']) ?? 10,
      updatedAt: _parseDateTime(json['updated_at']),
    });
  }

  toJson(): JsonMap {
    return {
      id: this.id,
      scope: this.scope,
      room_id: this.roomId,
      name: this.name,
      stickers: this.stickers.map((sticker) => sticker.toJson()),
      sort_order: this.sortOrder,
      updated_at: this.updatedAt == null ? null : toIsoString(this.updatedAt),
    };
  }
}

export class Sticker {
  readonly id: string;
  readonly name: string;
  readonly sortOrder: number;
  readonly asset: UploadedAsset;

  constructor(opts: { id: string; name: string; sortOrder: number; asset: UploadedAsset }) {
    this.id = opts.id;
    this.name = opts.name;
    this.sortOrder = opts.sortOrder;
    this.asset = opts.asset;
  }

  static fromJson(json: JsonMap): Sticker {
    return new Sticker({
      id: requireString(json, 'id'),
      name: asString(json['name']) ?? 'sticker',
      sortOrder: _nullableInt(json['sort_order']) ?? 10,
      asset: UploadedAsset.fromJson(requireMap(json, 'asset')),
    });
  }

  toJson(): JsonMap {
    return {
      id: this.id,
      name: this.name,
      sort_order: this.sortOrder,
      asset: this.asset.toJson(),
    };
  }
}

export class MessageAttachment {
  readonly type: string;
  readonly playlistId: string | null;
  readonly playlistScope: string | null;
  readonly sourceRoomId: string | null;
  readonly sourceMessageId: string | null;
  readonly itemId: string | null;
  readonly playlist: SharedMusicPlaylist | null;
  readonly track: SharedMusicTrack | null;
  readonly stickerId: string | null;
  readonly name: string | null;
  readonly asset: UploadedAsset | null;
  readonly durationMs: number | null;
  readonly event: string | null;
  readonly user: UserSummary | null;
  readonly actor: UserSummary | null;
  readonly target: UserSummary | null;
  readonly fromRole: string | null;
  readonly toRole: string | null;
  readonly oldValue: string | null;
  readonly newValue: string | null;

  constructor(opts: {
    type: string;
    playlistId?: string | null;
    playlistScope?: string | null;
    sourceRoomId?: string | null;
    sourceMessageId?: string | null;
    itemId?: string | null;
    playlist?: SharedMusicPlaylist | null;
    track?: SharedMusicTrack | null;
    stickerId?: string | null;
    name?: string | null;
    asset?: UploadedAsset | null;
    durationMs?: number | null;
    event?: string | null;
    user?: UserSummary | null;
    actor?: UserSummary | null;
    target?: UserSummary | null;
    fromRole?: string | null;
    toRole?: string | null;
    oldValue?: string | null;
    newValue?: string | null;
  }) {
    this.type = opts.type;
    this.playlistId = opts.playlistId ?? null;
    this.playlistScope = opts.playlistScope ?? null;
    this.sourceRoomId = opts.sourceRoomId ?? null;
    this.sourceMessageId = opts.sourceMessageId ?? null;
    this.itemId = opts.itemId ?? null;
    this.playlist = opts.playlist ?? null;
    this.track = opts.track ?? null;
    this.stickerId = opts.stickerId ?? null;
    this.name = opts.name ?? null;
    this.asset = opts.asset ?? null;
    this.durationMs = opts.durationMs ?? null;
    this.event = opts.event ?? null;
    this.user = opts.user ?? null;
    this.actor = opts.actor ?? null;
    this.target = opts.target ?? null;
    this.fromRole = opts.fromRole ?? null;
    this.toRole = opts.toRole ?? null;
    this.oldValue = opts.oldValue ?? null;
    this.newValue = opts.newValue ?? null;
  }

  static fromJson(json: JsonMap): MessageAttachment {
    const assetJson = _nullableMap(json['asset']);
    const userJson = _nullableMap(json['user']);
    const actorJson = _nullableMap(json['actor']);
    const targetJson = _nullableMap(json['target']);
    const playlistJson = _nullableMap(json['playlist']);
    const trackJson = _nullableMap(json['track']);
    return new MessageAttachment({
      type: asString(json['type']) ?? 'file',
      playlistId: _stringFromJson(json, ['playlist_id']),
      playlistScope: _stringFromJson(json, ['playlist_scope']),
      sourceRoomId: _stringFromJson(json, ['source_room_id']),
      sourceMessageId: _stringFromJson(json, ['source_message_id']),
      itemId: _stringFromJson(json, ['item_id']),
      playlist: playlistJson == null ? null : SharedMusicPlaylist.fromJson(playlistJson),
      track: trackJson == null ? null : SharedMusicTrack.fromJson(trackJson),
      stickerId: asString(json['sticker_id']),
      name: asString(json['name']),
      asset: assetJson == null ? null : UploadedAsset.fromJson(assetJson),
      durationMs: _nullableInt(json['duration_ms']),
      event: _stringFromJson(json, ['event', 'system_event']),
      user: userJson == null ? null : UserSummary.fromJson(userJson),
      actor: actorJson == null ? null : UserSummary.fromJson(actorJson),
      target: targetJson == null ? null : UserSummary.fromJson(targetJson),
      fromRole: _stringFromJson(json, ['from_role', 'previous_role']),
      toRole: _stringFromJson(json, ['to_role', 'role']),
      oldValue: _stringFromJson(json, ['old_value', 'previous_value']),
      newValue: _stringFromJson(json, ['new_value', 'value']),
    });
  }

  toJson(): JsonMap {
    const json: JsonMap = { type: this.type };
    if (this.playlistId != null) json['playlist_id'] = this.playlistId;
    if (this.playlistScope != null) json['playlist_scope'] = this.playlistScope;
    if (this.sourceRoomId != null) json['source_room_id'] = this.sourceRoomId;
    if (this.sourceMessageId != null) json['source_message_id'] = this.sourceMessageId;
    if (this.itemId != null) json['item_id'] = this.itemId;
    if (this.playlist != null) json['playlist'] = this.playlist.toJson();
    if (this.track != null) json['track'] = this.track.toJson();
    if (this.stickerId != null) json['sticker_id'] = this.stickerId;
    if (this.name != null) json['name'] = this.name;
    if (this.asset != null) json['asset'] = this.asset.toJson();
    if (this.durationMs != null) json['duration_ms'] = this.durationMs;
    if (this.event != null) json['event'] = this.event;
    if (this.user != null) json['user'] = _userSummaryToJson(this.user);
    if (this.actor != null) json['actor'] = _userSummaryToJson(this.actor);
    if (this.target != null) json['target'] = _userSummaryToJson(this.target);
    if (this.fromRole != null) json['from_role'] = this.fromRole;
    if (this.toRole != null) json['to_role'] = this.toRole;
    if (this.oldValue != null) json['old_value'] = this.oldValue;
    if (this.newValue != null) json['new_value'] = this.newValue;
    return json;
  }
}

/** Immutable song metadata embedded in a chat message at send time. */
export class SharedMusicTrack {
  readonly id: string;
  readonly playlistId: string;
  readonly trackId: string;
  readonly source: string;
  readonly title: string;
  readonly artists: string[];
  readonly durationMs: number;

  constructor(opts: {
    id: string;
    playlistId: string;
    trackId: string;
    source: string;
    title: string;
    artists: string[];
    durationMs: number;
  }) {
    this.id = opts.id;
    this.playlistId = opts.playlistId;
    this.trackId = opts.trackId;
    this.source = opts.source;
    this.title = opts.title;
    this.artists = opts.artists;
    this.durationMs = opts.durationMs;
  }

  static fromJson(json: JsonMap): SharedMusicTrack {
    return new SharedMusicTrack({
      id: _stringFromJson(json, ['id']) ?? '',
      playlistId: _stringFromJson(json, ['playlist_id']) ?? '',
      trackId: _stringFromJson(json, ['track_id']) ?? '',
      source: _stringFromJson(json, ['source']) ?? '',
      title: _stringFromJson(json, ['title', 'name']) ?? '',
      artists: _artistsFromJson(json['artists']),
      durationMs: _intFromJson(json, ['duration_ms']) ?? 0,
    });
  }

  toJson(): JsonMap {
    return {
      id: this.id,
      playlist_id: this.playlistId,
      track_id: this.trackId,
      source: this.source,
      title: this.title,
      artists: this.artists,
      duration_ms: this.durationMs,
    };
  }
}

/**
 * Immutable playlist data embedded in a chat message at send time.
 *
 * It is intentionally independent from the current user's playlist APIs: the
 * source playlist may later be renamed or deleted, while message history must
 * remain viewable and must never expose another user's management endpoint.
 */
export class SharedMusicPlaylist {
  readonly id: string;
  readonly name: string;
  readonly description: string;
  readonly itemCount: number;
  readonly createdAt: Date | null;
  readonly creator: UserSummary;
  readonly items: PersonalMusicPlaylistItem[];

  constructor(opts: {
    id: string;
    name: string;
    description: string;
    itemCount: number;
    createdAt: Date | null;
    creator: UserSummary;
    items: PersonalMusicPlaylistItem[];
  }) {
    this.id = opts.id;
    this.name = opts.name;
    this.description = opts.description;
    this.itemCount = opts.itemCount;
    this.createdAt = opts.createdAt;
    this.creator = opts.creator;
    this.items = opts.items;
  }

  static fromJson(json: JsonMap): SharedMusicPlaylist {
    return new SharedMusicPlaylist({
      id: _stringFromJson(json, ['id']) ?? '',
      name: _stringFromJson(json, ['name']) ?? '',
      description: _dartToString(json['description']) ?? '',
      itemCount: _intFromJson(json, ['item_count']) ?? 0,
      createdAt: _parseDateTime(json['created_at']),
      creator: UserSummary.fromJson(
        _nullableMap(json['creator']) ?? {
          id: '',
          username: 'unknown',
          display_name: '未知用户',
          avatar_url: null,
          default_avatar_key: 'blue-3',
        },
      ),
      items: _listOfMaps(json['items']).map((item) => PersonalMusicPlaylistItem.fromJson(item)),
    });
  }

  toJson(): JsonMap {
    return {
      id: this.id,
      name: this.name,
      description: this.description,
      item_count: this.itemCount,
      created_at: this.createdAt == null ? null : toIsoString(this.createdAt),
      creator: _userSummaryToJson(this.creator),
      items: this.items.map(
        (item): JsonMap => ({
          id: item.id,
          playlist_id: item.playlistId,
          track_id: item.trackId,
          source: item.source,
          title: item.title,
          artists: item.artists,
          duration_ms: item.durationMs,
          sort_order: item.sortOrder,
          created_at: item.createdAt == null ? null : toIsoString(item.createdAt),
        }),
      ),
    };
  }
}

function _userSummaryToJson(user: UserSummary): JsonMap {
  const json: JsonMap = { id: user.id };
  if (user.uid != null) json['uid'] = user.uid;
  json['username'] = user.username;
  json['display_name'] = user.displayName;
  json['avatar_url'] = user.avatarUrl;
  json['default_avatar_key'] = user.defaultAvatarKey;
  if (user.roomDisplayName != null) json['room_display_name'] = user.roomDisplayName;
  if (user.roomRole != null) json['room_role'] = user.roomRole;
  if (user.isSuperuser) json['is_superuser'] = true;
  if (user.isOnline != null) json['is_online'] = user.isOnline;
  return json;
}

export class LastMessagePreview {
  readonly id: string;
  readonly type: string;
  readonly senderDisplayName: string;
  readonly bodyPreview: string;
  readonly createdAt: Date;

  constructor(opts: { id: string; senderDisplayName: string; bodyPreview: string; createdAt: Date; type?: string }) {
    this.id = opts.id;
    this.type = opts.type ?? 'text';
    this.senderDisplayName = opts.senderDisplayName;
    this.bodyPreview = opts.bodyPreview;
    this.createdAt = opts.createdAt;
  }

  static fromJson(json: JsonMap): LastMessagePreview {
    return new LastMessagePreview({
      id: requireString(json, 'id'),
      type: asString(json['type']) ?? 'text',
      senderDisplayName: requireString(json, 'sender_display_name'),
      bodyPreview: requireString(json, 'body_preview'),
      createdAt: requireDate(json, 'created_at'),
    });
  }
}

export class RoomCard {
  readonly id: string;
  readonly name: string;
  readonly rid: string;
  readonly visibility: string;
  readonly remarkName: string | null;
  readonly description: string;
  readonly notificationPolicy: string;
  readonly isPinned: boolean;
  readonly avatarUrl: string | null;
  readonly defaultAvatarKey: string;
  readonly memberCount: number;
  readonly onlineMemberCount: number;
  readonly liveParticipantCount: number;
  readonly liveAvatarPreview: UserSummary[];
  readonly lastMessage: LastMessagePreview | null;
  readonly unreadCount: number;
  readonly hasUnreadCount: boolean;
  readonly unreadMentionCount: number;
  readonly hasUnreadMentionCount: boolean;
  readonly hasPendingJoinRequests: boolean;
  readonly aiVoiceAnnouncementsEnabled: boolean;

  /** Whether this server runs gang-bot, and whether this room has it on. */
  readonly gangBotAvailable: boolean;
  readonly gangBotEnabled: boolean;
  readonly updatedAt: Date;

  constructor(opts: {
    id: string;
    name: string;
    avatarUrl: string | null;
    defaultAvatarKey: string;
    memberCount: number;
    liveParticipantCount: number;
    liveAvatarPreview: UserSummary[];
    lastMessage: LastMessagePreview | null;
    unreadCount: number;
    updatedAt: Date;
    rid?: string;
    visibility?: string;
    remarkName?: string | null;
    description?: string;
    notificationPolicy?: string;
    isPinned?: boolean;
    onlineMemberCount?: number;
    hasUnreadCount?: boolean;
    unreadMentionCount?: number;
    hasUnreadMentionCount?: boolean;
    hasPendingJoinRequests?: boolean;
    aiVoiceAnnouncementsEnabled?: boolean;
    gangBotAvailable?: boolean;
    gangBotEnabled?: boolean;
  }) {
    this.id = opts.id;
    this.name = opts.name;
    this.rid = opts.rid ?? '';
    this.visibility = opts.visibility ?? 'private';
    this.remarkName = opts.remarkName ?? null;
    this.description = opts.description ?? '';
    this.notificationPolicy = opts.notificationPolicy ?? 'all';
    this.isPinned = opts.isPinned ?? false;
    this.avatarUrl = opts.avatarUrl;
    this.defaultAvatarKey = opts.defaultAvatarKey;
    this.memberCount = opts.memberCount;
    this.onlineMemberCount = opts.onlineMemberCount ?? 0;
    this.liveParticipantCount = opts.liveParticipantCount;
    this.liveAvatarPreview = opts.liveAvatarPreview;
    this.lastMessage = opts.lastMessage;
    this.unreadCount = opts.unreadCount;
    this.hasUnreadCount = opts.hasUnreadCount ?? false;
    this.unreadMentionCount = opts.unreadMentionCount ?? 0;
    this.hasUnreadMentionCount = opts.hasUnreadMentionCount ?? false;
    this.hasPendingJoinRequests = opts.hasPendingJoinRequests ?? false;
    this.aiVoiceAnnouncementsEnabled = opts.aiVoiceAnnouncementsEnabled ?? false;
    this.gangBotAvailable = opts.gangBotAvailable ?? false;
    this.gangBotEnabled = opts.gangBotEnabled ?? true;
    this.updatedAt = opts.updatedAt;
  }

  get gangBotActive(): boolean {
    return this.gangBotAvailable && this.gangBotEnabled;
  }

  static fromJson(json: JsonMap): RoomCard {
    const hasUnreadCount = _containsKey(json, 'unread_count');
    const hasUnreadMentionCount = _containsKey(json, 'unread_mention_count');
    const pendingJoinRequestCount = _intFromJson(json, ['pending_join_request_count', 'pending_join_requests_count']);
    const hasPendingJoinRequests =
      _boolFromJson(json, ['has_pending_join_requests', 'pending_join_requests']) ??
      (pendingJoinRequestCount != null && pendingJoinRequestCount > 0);
    const lastMessageJson = _nullableMap(json['last_message']);
    return new RoomCard({
      id: requireString(json, 'id'),
      name: requireString(json, 'name'),
      rid: asString(json['rid']) ?? '',
      visibility: asString(json['visibility']) ?? 'private',
      remarkName: _stringFromJson(json, ['remark_name', 'room_remark']),
      description: _stringFromJson(json, ['description', 'intro', 'bio']) ?? '',
      notificationPolicy: _stringFromJson(json, ['notification_policy', 'notification_level']) ?? 'all',
      isPinned: _boolFromJson(json, ['is_pinned', 'pinned']) ?? false,
      avatarUrl: asString(json['avatar_url']),
      defaultAvatarKey: asString(json['default_avatar_key']) ?? 'blue-3',
      memberCount: requireInt(json, 'member_count'),
      onlineMemberCount: _intFromJson(json, ['online_member_count', 'online_count']) ?? 0,
      liveParticipantCount: asInt(json['live_participant_count']) ?? 0,
      liveAvatarPreview: _listOfMaps(json['live_avatar_preview']).map((user) => UserSummary.fromJson(user)),
      lastMessage: lastMessageJson == null ? null : LastMessagePreview.fromJson(lastMessageJson),
      unreadCount: asInt(json['unread_count']) ?? 0,
      hasUnreadCount,
      unreadMentionCount: asInt(json['unread_mention_count']) ?? 0,
      hasUnreadMentionCount,
      hasPendingJoinRequests,
      aiVoiceAnnouncementsEnabled:
        _boolFromJson(json, ['ai_voice_announcements_enabled', 'ai_voice_announce_enabled']) ?? false,
      gangBotAvailable: asBool(json['gang_bot_available']) ?? false,
      gangBotEnabled: asBool(json['gang_bot_enabled']) ?? true,
      updatedAt: requireDate(json, 'updated_at'),
    });
  }

  get displayName(): string {
    const remark = _nonEmptyString(this.remarkName);
    if (remark == null) return this.name;
    return remark;
  }

  /**
   * Returns a copy with selected fields overridden. Used when merging a
   * server-pushed public snapshot (room_updated) over an existing card: the
   * snapshot carries no per-user fields, so the caller re-supplies the local
   * [unreadCount] to keep it from being reset to 0.
   */
  copyWith(
    opts: {
      unreadCount?: number | null;
      unreadMentionCount?: number | null;
      isPinned?: boolean | null;
      hasUnreadCount?: boolean | null;
      hasUnreadMentionCount?: boolean | null;
      hasPendingJoinRequests?: boolean | null;
    } = {},
  ): RoomCard {
    return new RoomCard({
      id: this.id,
      name: this.name,
      rid: this.rid,
      visibility: this.visibility,
      remarkName: this.remarkName,
      description: this.description,
      notificationPolicy: this.notificationPolicy,
      isPinned: opts.isPinned ?? this.isPinned,
      avatarUrl: this.avatarUrl,
      defaultAvatarKey: this.defaultAvatarKey,
      memberCount: this.memberCount,
      onlineMemberCount: this.onlineMemberCount,
      liveParticipantCount: this.liveParticipantCount,
      liveAvatarPreview: this.liveAvatarPreview,
      lastMessage: this.lastMessage,
      unreadCount: opts.unreadCount ?? this.unreadCount,
      hasUnreadCount: opts.hasUnreadCount ?? this.hasUnreadCount,
      unreadMentionCount: opts.unreadMentionCount ?? this.unreadMentionCount,
      hasUnreadMentionCount: opts.hasUnreadMentionCount ?? this.hasUnreadMentionCount,
      hasPendingJoinRequests: opts.hasPendingJoinRequests ?? this.hasPendingJoinRequests,
      aiVoiceAnnouncementsEnabled: this.aiVoiceAnnouncementsEnabled,
      gangBotAvailable: this.gangBotAvailable,
      gangBotEnabled: this.gangBotEnabled,
      updatedAt: this.updatedAt,
    });
  }
}

/**
 * A room as seen from search results — the viewer may or may not already be
 * a member. [joinState] is one of `none`, `pending`, `joined` and drives the
 * join button's affordance.
 */
export class PublicRoom {
  readonly id: string;
  readonly rid: string;
  readonly name: string;
  readonly avatarLabel: string | null;
  readonly avatarUrl: string | null;
  readonly defaultAvatarKey: string;
  readonly visibility: string;
  readonly joinPolicy: string;
  readonly description: string;
  readonly memberCount: number;
  readonly onlineMemberCount: number;
  readonly liveParticipantCount: number;
  readonly joined: boolean;
  readonly joinState: string;
  readonly createdBy: UserSummary | null;
  readonly personalProfile: RoomPersonalProfile;
  readonly myMembership: RoomMembership | null;
  readonly isDeleted: boolean;

  constructor(opts: {
    id: string;
    rid: string;
    name: string;
    avatarUrl: string | null;
    defaultAvatarKey: string;
    visibility: string;
    joinPolicy: string;
    memberCount: number;
    liveParticipantCount: number;
    joined: boolean;
    joinState: string;
    avatarLabel?: string | null;
    onlineMemberCount?: number;
    description?: string;
    createdBy?: UserSummary | null;
    personalProfile?: RoomPersonalProfile;
    myMembership?: RoomMembership | null;
    isDeleted?: boolean;
  }) {
    this.id = opts.id;
    this.rid = opts.rid;
    this.name = opts.name;
    this.avatarLabel = opts.avatarLabel ?? null;
    this.avatarUrl = opts.avatarUrl;
    this.defaultAvatarKey = opts.defaultAvatarKey;
    this.visibility = opts.visibility;
    this.joinPolicy = opts.joinPolicy;
    this.description = opts.description ?? '';
    this.memberCount = opts.memberCount;
    this.onlineMemberCount = opts.onlineMemberCount ?? 0;
    this.liveParticipantCount = opts.liveParticipantCount;
    this.joined = opts.joined;
    this.joinState = opts.joinState;
    this.createdBy = opts.createdBy ?? null;
    this.personalProfile = opts.personalProfile ?? new RoomPersonalProfile();
    this.myMembership = opts.myMembership ?? null;
    this.isDeleted = opts.isDeleted ?? false;
  }

  static fromJson(json: JsonMap): PublicRoom {
    const createdByJson = _nullableMap(json['created_by']);
    const myMembershipJson = _nullableMap(json['my_membership']);
    return new PublicRoom({
      id: requireString(json, 'id'),
      rid: asString(json['rid']) ?? '',
      name: requireString(json, 'name'),
      avatarUrl: asString(json['avatar_url']),
      defaultAvatarKey: asString(json['default_avatar_key']) ?? 'blue-3',
      visibility: asString(json['visibility']) ?? 'public',
      joinPolicy: asString(json['join_policy']) ?? 'approval_required',
      description: _stringFromJson(json, ['description', 'intro', 'bio']) ?? '',
      memberCount: asInt(json['member_count']) ?? 0,
      onlineMemberCount: _intFromJson(json, ['online_member_count', 'online_count']) ?? 0,
      liveParticipantCount: asInt(json['live_participant_count']) ?? 0,
      joined: asBool(json['joined']) ?? false,
      joinState: asString(json['join_state']) ?? 'none',
      createdBy: createdByJson == null ? null : UserSummary.fromJson(createdByJson),
      avatarLabel: _stringFromJson(json, ['avatar_label']),
      personalProfile: RoomPersonalProfile.fromJson(
        _nullableMap(json['personal_profile']) ??
          _nullableMap(json['my_room_profile']) ??
          _nullableMap(json['room_profile']),
      ),
      myMembership: myMembershipJson == null ? null : RoomMembership.fromJson(myMembershipJson),
      isDeleted: asBool(json['is_deleted']) ?? false,
    });
  }

  copyWith(
    opts: {
      joined?: boolean | null;
      joinState?: string | null;
      memberCount?: number | null;
      onlineMemberCount?: number | null;
      liveParticipantCount?: number | null;
      isDeleted?: boolean | null;
    } = {},
  ): PublicRoom {
    return new PublicRoom({
      id: this.id,
      rid: this.rid,
      name: this.name,
      avatarLabel: this.avatarLabel,
      avatarUrl: this.avatarUrl,
      defaultAvatarKey: this.defaultAvatarKey,
      visibility: this.visibility,
      joinPolicy: this.joinPolicy,
      description: this.description,
      memberCount: opts.memberCount ?? this.memberCount,
      onlineMemberCount: opts.onlineMemberCount ?? this.onlineMemberCount,
      liveParticipantCount: opts.liveParticipantCount ?? this.liveParticipantCount,
      joined: opts.joined ?? this.joined,
      joinState: opts.joinState ?? this.joinState,
      createdBy: this.createdBy,
      personalProfile: this.personalProfile,
      myMembership: this.myMembership,
      isDeleted: opts.isDeleted ?? this.isDeleted,
    });
  }
}

export class SearchRoomContext {
  readonly id: string;
  readonly rid: string;
  readonly name: string;
  readonly remarkName: string | null;
  readonly avatarUrl: string | null;
  readonly defaultAvatarKey: string;

  constructor(opts: {
    id: string;
    rid: string;
    name: string;
    avatarUrl: string | null;
    defaultAvatarKey: string;
    remarkName?: string | null;
  }) {
    this.id = opts.id;
    this.rid = opts.rid;
    this.name = opts.name;
    this.remarkName = opts.remarkName ?? null;
    this.avatarUrl = opts.avatarUrl;
    this.defaultAvatarKey = opts.defaultAvatarKey;
  }

  static fromJson(json: JsonMap): SearchRoomContext {
    return new SearchRoomContext({
      id: requireString(json, 'id'),
      rid: asString(json['rid']) ?? '',
      name: requireString(json, 'name'),
      remarkName: _stringFromJson(json, ['remark_name', 'room_remark']),
      avatarUrl: asString(json['avatar_url']),
      defaultAvatarKey: asString(json['default_avatar_key']) ?? 'blue-3',
    });
  }

  get displayName(): string {
    const remark = _nonEmptyString(this.remarkName);
    if (remark == null) return this.name;
    return remark;
  }
}

export class MessageSearchResult {
  readonly room: SearchRoomContext;
  readonly message: Message;

  constructor(opts: { room: SearchRoomContext; message: Message }) {
    this.room = opts.room;
    this.message = opts.message;
  }

  static fromJson(json: JsonMap): MessageSearchResult {
    return new MessageSearchResult({
      room: SearchRoomContext.fromJson(requireMap(json, 'room')),
      message: Message.fromJson(requireMap(json, 'message')),
    });
  }
}

export class GlobalSearchCursors {
  readonly myRooms: string | null;
  readonly publicRooms: string | null;
  readonly userSettings: string | null;
  readonly messages: string | null;
  readonly files: string | null;

  constructor(
    opts: {
      myRooms?: string | null;
      publicRooms?: string | null;
      userSettings?: string | null;
      messages?: string | null;
      files?: string | null;
    } = {},
  ) {
    this.myRooms = opts.myRooms ?? null;
    this.publicRooms = opts.publicRooms ?? null;
    this.userSettings = opts.userSettings ?? null;
    this.messages = opts.messages ?? null;
    this.files = opts.files ?? null;
  }

  static fromJson(json: JsonMap | null): GlobalSearchCursors {
    if (json == null) return new GlobalSearchCursors();
    return new GlobalSearchCursors({
      myRooms: asString(json['my_rooms']),
      publicRooms: asString(json['public_rooms']),
      userSettings: asString(json['user_settings']),
      messages: asString(json['messages']),
      files: asString(json['files']),
    });
  }
}

export class GlobalSearchCounts {
  readonly myRooms: number | null;
  readonly publicRooms: number | null;
  readonly userSettings: number | null;
  readonly messages: number | null;
  readonly files: number | null;

  constructor(
    opts: {
      myRooms?: number | null;
      publicRooms?: number | null;
      userSettings?: number | null;
      messages?: number | null;
      files?: number | null;
    } = {},
  ) {
    this.myRooms = opts.myRooms ?? null;
    this.publicRooms = opts.publicRooms ?? null;
    this.userSettings = opts.userSettings ?? null;
    this.messages = opts.messages ?? null;
    this.files = opts.files ?? null;
  }

  static fromJson(json: JsonMap | null): GlobalSearchCounts {
    if (json == null) return new GlobalSearchCounts();
    return new GlobalSearchCounts({
      myRooms: _nullableInt(json['my_rooms']),
      publicRooms: _nullableInt(json['public_rooms']),
      userSettings: _nullableInt(json['user_settings']),
      messages: _nullableInt(json['messages']),
      files: _nullableInt(json['files']),
    });
  }
}

export class GlobalSearchResults {
  readonly myRooms: RoomCard[];
  readonly publicRooms: PublicRoom[];
  readonly userSettings: UserSummary[];
  readonly messages: MessageSearchResult[];
  readonly files: MessageSearchResult[];
  readonly nextCursors: GlobalSearchCursors;
  readonly totalCounts: GlobalSearchCounts;

  constructor(opts: {
    myRooms: RoomCard[];
    publicRooms: PublicRoom[];
    userSettings?: UserSummary[];
    messages: MessageSearchResult[];
    files: MessageSearchResult[];
    nextCursors?: GlobalSearchCursors;
    totalCounts?: GlobalSearchCounts;
  }) {
    this.myRooms = opts.myRooms;
    this.publicRooms = opts.publicRooms;
    this.userSettings = opts.userSettings ?? [];
    this.messages = opts.messages;
    this.files = opts.files;
    this.nextCursors = opts.nextCursors ?? new GlobalSearchCursors();
    this.totalCounts = opts.totalCounts ?? new GlobalSearchCounts();
  }

  static fromJson(json: JsonMap): GlobalSearchResults {
    return new GlobalSearchResults({
      myRooms: _listOfMaps(json['my_rooms']).map((room) => RoomCard.fromJson(room)),
      publicRooms: _listOfMaps(json['public_rooms']).map((room) => PublicRoom.fromJson(room)),
      userSettings: _listOfMaps(json['user_settings']).map((user) => UserSummary.fromJson(user)),
      messages: _listOfMaps(json['messages']).map((result) => MessageSearchResult.fromJson(result)),
      files: _listOfMaps(json['files']).map((result) => MessageSearchResult.fromJson(result)),
      nextCursors: GlobalSearchCursors.fromJson(_nullableMap(json['next_cursors'])),
      totalCounts: GlobalSearchCounts.fromJson(_nullableMap(json['total_counts'])),
    });
  }
}

/**
 * Result of POST /rooms/:id/join. Either we joined and got the full room
 * detail back, or the room needs approval and we got a pending request.
 */
export class JoinRoomResult {
  readonly room: RoomDetail | null;
  readonly pending: boolean;

  constructor(opts: { room?: RoomDetail | null; pending?: boolean } = {}) {
    this.room = opts.room ?? null;
    this.pending = opts.pending ?? false;
  }

  get joined(): boolean {
    return this.room != null;
  }
}

export class RoomMembership {
  readonly joinedAt: Date;
  readonly role: string;
  readonly aiVoiceAnnouncementsEnabled: boolean;

  constructor(opts: { joinedAt: Date; role: string; aiVoiceAnnouncementsEnabled?: boolean }) {
    this.joinedAt = opts.joinedAt;
    this.role = opts.role;
    this.aiVoiceAnnouncementsEnabled = opts.aiVoiceAnnouncementsEnabled ?? false;
  }

  static fromJson(json: JsonMap): RoomMembership {
    return new RoomMembership({
      joinedAt: requireDate(json, 'joined_at'),
      role: asString(json['role']) ?? 'member',
      aiVoiceAnnouncementsEnabled: _boolFromJson(json, ['ai_voice_announcements_enabled']) ?? false,
    });
  }
}

export class RoomPersonalProfile {
  readonly displayName: string | null;

  constructor(opts: { displayName?: string | null } = {}) {
    this.displayName = opts.displayName ?? null;
  }

  static fromJson(json: JsonMap | null): RoomPersonalProfile {
    if (json == null) return new RoomPersonalProfile();
    return new RoomPersonalProfile({
      displayName: _stringFromJson(json, ['display_name', 'room_display_name', 'room_username', 'room_nickname']),
    });
  }

  get isEmpty(): boolean {
    return _nonEmptyString(this.displayName) == null;
  }
}

/**
 * A pending request to join an approval-required room, as seen by an admin
 * reviewing the room's join queue.
 */
export class JoinRequest {
  readonly id: string;
  readonly status: string;
  readonly reason: string;
  readonly source: string;
  readonly inviters: UserSummary[];
  readonly user: UserSummary;
  readonly createdAt: Date;

  constructor(opts: {
    id: string;
    status: string;
    user: UserSummary;
    createdAt: Date;
    reason?: string;
    source?: string;
    inviters?: UserSummary[];
  }) {
    this.id = opts.id;
    this.status = opts.status;
    this.reason = opts.reason ?? '';
    this.source = opts.source ?? 'public_search';
    this.inviters = opts.inviters ?? [];
    this.user = opts.user;
    this.createdAt = opts.createdAt;
  }

  static fromJson(json: JsonMap): JoinRequest {
    const inviters = _listOfMaps(json['inviters']).map((user) => UserSummary.fromJson(user));
    return new JoinRequest({
      id: requireString(json, 'id'),
      status: asString(json['status']) ?? 'pending',
      reason: _stringFromJson(json, ['reason', 'description']) ?? '',
      source:
        _stringFromJson(json, ['source', 'request_source']) ?? (inviters.length === 0 ? 'public_search' : 'invitation'),
      inviters,
      user: UserSummary.fromJson(requireMap(json, 'user')),
      createdAt: requireDate(json, 'created_at'),
    });
  }
}

export class RoomMemberPage {
  readonly members: RoomMember[];
  readonly nextCursor: string | null;

  constructor(opts: { members: RoomMember[]; nextCursor: string | null }) {
    this.members = opts.members;
    this.nextCursor = opts.nextCursor;
  }

  static fromJson(json: JsonMap): RoomMemberPage {
    return new RoomMemberPage({
      members: _listOfMaps(json['members']).map((member) => RoomMember.fromJson(member)),
      nextCursor: _stringFromJson(json, ['next_cursor']),
    });
  }
}

export class RoomMember {
  readonly user: UserSummary;
  readonly role: string;
  readonly joinedAt: Date;
  readonly roomDisplayName: string | null;
  readonly remarkName: string | null;
  readonly textMutedUntil: string | null;
  readonly isOnline: boolean | null;

  constructor(opts: {
    user: UserSummary;
    role: string;
    joinedAt: Date;
    roomDisplayName?: string | null;
    remarkName?: string | null;
    textMutedUntil?: string | null;
    isOnline?: boolean | null;
  }) {
    this.user = opts.user;
    this.role = opts.role;
    this.joinedAt = opts.joinedAt;
    this.roomDisplayName = opts.roomDisplayName ?? null;
    this.remarkName = opts.remarkName ?? null;
    this.textMutedUntil = opts.textMutedUntil ?? null;
    this.isOnline = opts.isOnline ?? null;
  }

  static fromJson(json: JsonMap): RoomMember {
    const userJson = requireMap(json, 'user');
    const baseUser = UserSummary.fromJson(userJson);
    const membership = _nullableMap(json['membership']);
    const role =
      _stringFromJson(json, ['role', 'room_role', 'membership_role']) ??
      _stringFromJson(membership, ['role']) ??
      baseUser.roomRole ??
      'member';
    const roomDisplayName =
      _stringFromJson(json, ['room_display_name', 'room_username', 'room_nickname', 'member_display_name']) ??
      _stringFromJson(membership, ['room_display_name', 'room_username', 'room_nickname', 'member_display_name']) ??
      baseUser.roomDisplayName;
    const remarkName =
      _stringFromJson(json, ['remark_name', 'remark']) ?? _stringFromJson(membership, ['remark_name', 'remark']);
    const textMutedUntil =
      _stringFromJson(json, ['text_muted_until']) ?? _stringFromJson(membership, ['text_muted_until']);
    const isOnline =
      _boolFromJson(json, ['is_online', 'online']) ??
      _boolFromJson(_nullableMap(json['presence']), ['is_online', 'online']) ??
      _onlineFromStatus(
        _stringFromJson(json, ['presence', 'presence_status', 'status', 'connection_state']) ??
          _stringFromJson(_nullableMap(json['presence']), ['status', 'connection_state']),
      ) ??
      baseUser.isOnline;
    const user = baseUser.copyWith({ roomDisplayName, roomRole: role, isOnline });
    return new RoomMember({
      user,
      role,
      joinedAt: _parseDateTime(json['joined_at']) ?? _parseDateTime(membership?.['joined_at']) ?? new Date(0),
      roomDisplayName,
      remarkName,
      textMutedUntil,
      isOnline,
    });
  }
}

export class RoomMemberProfile {
  readonly user: UserSummary;
  readonly role: string;
  readonly joinedAt: Date;
  readonly roomDisplayName: string | null;
  readonly textMutedUntil: string | null;

  constructor(opts: {
    user: UserSummary;
    role: string;
    joinedAt: Date;
    roomDisplayName?: string | null;
    textMutedUntil?: string | null;
  }) {
    this.user = opts.user;
    this.role = opts.role;
    this.joinedAt = opts.joinedAt;
    this.roomDisplayName = opts.roomDisplayName ?? null;
    this.textMutedUntil = opts.textMutedUntil ?? null;
  }

  static fromJson(json: JsonMap): RoomMemberProfile {
    const baseUser = UserSummary.fromJson(requireMap(json, 'user'));
    const role = _stringFromJson(json, ['role', 'room_role', 'membership_role']) ?? baseUser.roomRole ?? 'member';
    const roomDisplayName = _stringFromJson(json, [
      'room_display_name',
      'room_username',
      'room_nickname',
      'member_display_name',
    ]);
    const isOnline =
      baseUser.isOnline ??
      _boolFromJson(json, ['is_online', 'online']) ??
      _onlineFromStatus(_stringFromJson(json, ['presence', 'presence_status', 'status', 'connection_state']));
    const user = baseUser.copyWith({ roomDisplayName, roomRole: role, isOnline });
    return new RoomMemberProfile({
      user,
      role,
      joinedAt: _parseDateTime(json['joined_at']) ?? new Date(0),
      roomDisplayName,
      textMutedUntil: _stringFromJson(json, ['text_muted_until']),
    });
  }
}

export class RoomInvite {
  readonly id: string;
  readonly status: string;
  readonly room: PublicRoom;
  readonly inviter: UserSummary;
  readonly createdAt: Date;
  readonly roomExists: boolean;
  readonly inviterExists: boolean;
  readonly invalidReason: string | null;
  readonly updatedAt: Date | null;

  constructor(opts: {
    id: string;
    status: string;
    room: PublicRoom;
    inviter: UserSummary;
    createdAt: Date;
    roomExists?: boolean;
    inviterExists?: boolean;
    invalidReason?: string | null;
    updatedAt?: Date | null;
  }) {
    this.id = opts.id;
    this.status = opts.status;
    this.room = opts.room;
    this.inviter = opts.inviter;
    this.createdAt = opts.createdAt;
    this.roomExists = opts.roomExists ?? true;
    this.inviterExists = opts.inviterExists ?? true;
    this.invalidReason = opts.invalidReason ?? null;
    this.updatedAt = opts.updatedAt ?? null;
  }

  static fromJson(json: JsonMap): RoomInvite {
    const roomExists = asBool(json['room_exists']) ?? true;
    const inviterExists = asBool(json['inviter_exists']) ?? true;
    return new RoomInvite({
      id: requireString(json, 'id'),
      status: asString(json['status']) ?? 'pending',
      room: PublicRoom.fromJson(requireMap(json, 'room')).copyWith({ isDeleted: !roomExists }),
      inviter: UserSummary.fromJson(requireMap(json, 'inviter')).copyWith({ isDeleted: !inviterExists }),
      createdAt: _parseDateTime(json['created_at']) ?? new Date(0),
      roomExists,
      inviterExists,
      invalidReason: _stringFromJson(json, ['invalid_reason']),
      updatedAt: _parseDateTime(json['updated_at']),
    });
  }
}

export class RoomBlacklistEntry {
  readonly user: UserSummary;
  readonly createdAt: Date;
  readonly blockedBy: UserSummary | null;

  constructor(opts: { user: UserSummary; createdAt: Date; blockedBy?: UserSummary | null }) {
    this.user = opts.user;
    this.createdAt = opts.createdAt;
    this.blockedBy = opts.blockedBy ?? null;
  }

  static fromJson(json: JsonMap): RoomBlacklistEntry {
    const blockedByJson = _nullableMap(json['blocked_by']);
    return new RoomBlacklistEntry({
      user: UserSummary.fromJson(requireMap(json, 'user')),
      createdAt: _parseDateTime(json['created_at']) ?? new Date(0),
      blockedBy: blockedByJson == null ? null : UserSummary.fromJson(blockedByJson),
    });
  }
}

export class RoomApplication {
  readonly id: string;
  readonly status: string;
  readonly reason: string;
  readonly room: PublicRoom;
  readonly createdAt: Date;
  readonly updatedAt: Date;
  readonly reviewedAt: Date | null;
  readonly reviewer: UserSummary | null;
  readonly reviewerExists: boolean;
  readonly requestNotificationDeleted: boolean;
  readonly reviewNotificationDeleted: boolean;

  constructor(opts: {
    id: string;
    status: string;
    room: PublicRoom;
    createdAt: Date;
    updatedAt: Date;
    reason?: string;
    reviewedAt?: Date | null;
    reviewer?: UserSummary | null;
    reviewerExists?: boolean;
    requestNotificationDeleted?: boolean;
    reviewNotificationDeleted?: boolean;
  }) {
    this.id = opts.id;
    this.status = opts.status;
    this.reason = opts.reason ?? '';
    this.room = opts.room;
    this.createdAt = opts.createdAt;
    this.updatedAt = opts.updatedAt;
    this.reviewedAt = opts.reviewedAt ?? null;
    this.reviewer = opts.reviewer ?? null;
    this.reviewerExists = opts.reviewerExists ?? true;
    this.requestNotificationDeleted = opts.requestNotificationDeleted ?? false;
    this.reviewNotificationDeleted = opts.reviewNotificationDeleted ?? false;
  }

  static fromJson(json: JsonMap): RoomApplication {
    const reviewerExists = asBool(json['reviewer_exists']) ?? true;
    const reviewerJson = _nullableMap(json['reviewer']);
    return new RoomApplication({
      id: requireString(json, 'id'),
      status: asString(json['status']) ?? 'pending',
      reason: _stringFromJson(json, ['reason', 'description']) ?? '',
      room: PublicRoom.fromJson(requireMap(json, 'room')),
      createdAt: _parseDateTime(json['created_at']) ?? new Date(0),
      updatedAt: _parseDateTime(json['updated_at']) ?? new Date(0),
      reviewedAt: _parseDateTime(json['reviewed_at']),
      reviewer:
        reviewerJson == null ? null : UserSummary.fromJson(reviewerJson).copyWith({ isDeleted: !reviewerExists }),
      reviewerExists,
      requestNotificationDeleted: asBool(json['request_notification_deleted']) ?? false,
      reviewNotificationDeleted: asBool(json['review_notification_deleted']) ?? false,
    });
  }
}

export class RoomEventNotification {
  readonly id: string;
  readonly type: string;
  readonly room: PublicRoom;
  readonly createdAt: Date;
  readonly roomExists: boolean;
  readonly actor: UserSummary | null;
  readonly actorExists: boolean;
  readonly fromRole: string | null;
  readonly toRole: string | null;
  readonly messageId: string | null;
  readonly messagePreview: string | null;
  readonly readAt: Date | null;

  constructor(opts: {
    id: string;
    type: string;
    room: PublicRoom;
    createdAt: Date;
    roomExists?: boolean;
    actor?: UserSummary | null;
    actorExists?: boolean;
    fromRole?: string | null;
    toRole?: string | null;
    messageId?: string | null;
    messagePreview?: string | null;
    readAt?: Date | null;
  }) {
    this.id = opts.id;
    this.type = opts.type;
    this.room = opts.room;
    this.createdAt = opts.createdAt;
    this.roomExists = opts.roomExists ?? true;
    this.actor = opts.actor ?? null;
    this.actorExists = opts.actorExists ?? true;
    this.fromRole = opts.fromRole ?? null;
    this.toRole = opts.toRole ?? null;
    this.messageId = opts.messageId ?? null;
    this.messagePreview = opts.messagePreview ?? null;
    this.readAt = opts.readAt ?? null;
  }

  get isUnread(): boolean {
    return this.readAt == null;
  }

  markRead(readAt: Date): RoomEventNotification {
    return new RoomEventNotification({
      id: this.id,
      type: this.type,
      room: this.room,
      createdAt: this.createdAt,
      roomExists: this.roomExists,
      actor: this.actor,
      actorExists: this.actorExists,
      fromRole: this.fromRole,
      toRole: this.toRole,
      messageId: this.messageId,
      messagePreview: this.messagePreview,
      readAt,
    });
  }

  static fromJson(json: JsonMap): RoomEventNotification {
    const actorJson = _nullableMap(json['actor']);
    const roomExists = asBool(json['room_exists']) ?? true;
    const actorExists = asBool(json['actor_exists']) ?? actorJson != null;
    const actor = actorJson == null ? null : UserSummary.fromJson(actorJson).copyWith({ isDeleted: !actorExists });
    return new RoomEventNotification({
      id: requireString(json, 'id'),
      type: asString(json['type']) ?? '',
      room: PublicRoom.fromJson(requireMap(json, 'room')).copyWith({ isDeleted: !roomExists }),
      createdAt: _parseDateTime(json['created_at']) ?? new Date(0),
      roomExists,
      actor,
      actorExists,
      fromRole: _stringFromJson(json, ['from_role']),
      toRole: _stringFromJson(json, ['to_role']),
      messageId: _stringFromJson(json, ['message_id']),
      messagePreview: _stringFromJson(json, ['message_preview']),
      readAt: _parseDateTime(json['read_at']),
    });
  }
}

export class RoomDetail {
  readonly id: string;
  readonly name: string;
  readonly rid: string;
  readonly visibility: string;
  readonly description: string;
  readonly joinPolicy: string;
  readonly remarkName: string | null;
  readonly notificationPolicy: string;
  readonly isPinned: boolean;
  readonly personalProfile: RoomPersonalProfile;
  readonly aiVoiceAnnouncementsEnabled: boolean;

  /**
   * Who may recall messages and within what window. Mirrors the server's
   * `message_recall_policy` (e.g. `sender_only`, `admins`, `disabled`) and
   * `message_recall_window_seconds` (null = unlimited). Drives whether the
   * recall action is offered and for how long after sending.
   */
  readonly messageRecallPolicy: string;
  readonly messageRecallWindowSeconds: number | null;

  readonly canDeleteRoom: boolean | null;

  /** Whether this server runs gang-bot, and whether this room has it on. */
  readonly gangBotAvailable: boolean;
  readonly gangBotEnabled: boolean;

  get gangBotActive(): boolean {
    return this.gangBotAvailable && this.gangBotEnabled;
  }

  readonly avatarUrl: string | null;
  readonly defaultAvatarKey: string;
  readonly memberCount: number;
  readonly onlineMemberCount: number;
  readonly createdBy: UserSummary | null;
  readonly myMembership: RoomMembership;
  readonly live: LiveState;
  readonly createdAt: Date;
  readonly updatedAt: Date;

  constructor(opts: {
    id: string;
    name: string;
    avatarUrl: string | null;
    defaultAvatarKey: string;
    memberCount: number;
    myMembership: RoomMembership;
    live: LiveState;
    createdAt: Date;
    updatedAt: Date;
    createdBy?: UserSummary | null;
    rid?: string;
    visibility?: string;
    description?: string;
    joinPolicy?: string;
    remarkName?: string | null;
    notificationPolicy?: string;
    isPinned?: boolean;
    personalProfile?: RoomPersonalProfile;
    aiVoiceAnnouncementsEnabled?: boolean;
    messageRecallPolicy?: string;
    messageRecallWindowSeconds?: number | null;
    canDeleteRoom?: boolean | null;
    onlineMemberCount?: number;
    gangBotAvailable?: boolean;
    gangBotEnabled?: boolean;
  }) {
    this.id = opts.id;
    this.name = opts.name;
    this.rid = opts.rid ?? '';
    this.visibility = opts.visibility ?? 'private';
    this.description = opts.description ?? '';
    this.joinPolicy = opts.joinPolicy ?? 'approval_required';
    this.remarkName = opts.remarkName ?? null;
    this.notificationPolicy = opts.notificationPolicy ?? 'all';
    this.isPinned = opts.isPinned ?? false;
    this.personalProfile = opts.personalProfile ?? new RoomPersonalProfile();
    this.aiVoiceAnnouncementsEnabled = opts.aiVoiceAnnouncementsEnabled ?? false;
    this.messageRecallPolicy = opts.messageRecallPolicy ?? 'sender_only';
    this.messageRecallWindowSeconds = opts.messageRecallWindowSeconds ?? null;
    this.canDeleteRoom = opts.canDeleteRoom ?? null;
    this.gangBotAvailable = opts.gangBotAvailable ?? false;
    this.gangBotEnabled = opts.gangBotEnabled ?? true;
    this.avatarUrl = opts.avatarUrl;
    this.defaultAvatarKey = opts.defaultAvatarKey;
    this.memberCount = opts.memberCount;
    this.onlineMemberCount = opts.onlineMemberCount ?? 0;
    this.createdBy = opts.createdBy ?? null;
    this.myMembership = opts.myMembership;
    this.live = opts.live;
    this.createdAt = opts.createdAt;
    this.updatedAt = opts.updatedAt;
  }

  static fromJson(json: JsonMap): RoomDetail {
    const createdByJson = _nullableMap(json['created_by']);
    return new RoomDetail({
      id: requireString(json, 'id'),
      name: requireString(json, 'name'),
      rid: asString(json['rid']) ?? '',
      visibility: asString(json['visibility']) ?? 'private',
      description: _stringFromJson(json, ['description', 'intro', 'bio']) ?? '',
      joinPolicy: asString(json['join_policy']) ?? 'approval_required',
      remarkName: _stringFromJson(json, ['remark_name', 'room_remark']),
      notificationPolicy: _stringFromJson(json, ['notification_policy', 'notification_level']) ?? 'all',
      isPinned:
        _boolFromJson(json, ['is_pinned', 'pinned']) ??
        _boolFromJson(_nullableMap(json['my_membership']), ['is_pinned', 'pinned']) ??
        false,
      personalProfile: RoomPersonalProfile.fromJson(
        _nullableMap(json['personal_profile']) ??
          _nullableMap(json['my_room_profile']) ??
          _nullableMap(json['room_profile']),
      ),
      aiVoiceAnnouncementsEnabled:
        _boolFromJson(json, ['ai_voice_announcements_enabled', 'ai_voice_auto_broadcast']) ??
        _boolFromJson(_nullableMap(json['my_membership']), ['ai_voice_announcements_enabled']) ??
        false,
      messageRecallPolicy: _stringFromJson(json, ['message_recall_policy']) ?? 'sender_only',
      messageRecallWindowSeconds: _intFromJson(json, ['message_recall_window_seconds']),
      canDeleteRoom: _boolFromJson(json, ['can_delete_room', 'can_delete']),
      gangBotAvailable: asBool(json['gang_bot_available']) ?? false,
      gangBotEnabled: asBool(json['gang_bot_enabled']) ?? true,
      avatarUrl: asString(json['avatar_url']),
      defaultAvatarKey: asString(json['default_avatar_key']) ?? 'blue-3',
      memberCount: requireInt(json, 'member_count'),
      onlineMemberCount: _intFromJson(json, ['online_member_count', 'online_count']) ?? 0,
      createdBy: createdByJson == null ? null : UserSummary.fromJson(createdByJson),
      myMembership: RoomMembership.fromJson(requireMap(json, 'my_membership')),
      live: LiveState.fromJson(requireMap(json, 'live')),
      createdAt: requireDate(json, 'created_at'),
      updatedAt: requireDate(json, 'updated_at'),
    });
  }

  /**
   * Whether the current user can administer this room (review join requests,
   * etc.). Mirrors the server's admin check: owner, admin, or superuser.
   */
  get isAdmin(): boolean {
    const role = this.myMembership.role.toLowerCase();
    return (
      role === 'owner' || role === 'creator' || role === 'admin' || role === 'administrator' || role === 'superuser'
    );
  }

  get isCreator(): boolean {
    const role = this.myMembership.role.toLowerCase();
    return role === 'owner' || role === 'creator';
  }

  get isSuperuser(): boolean {
    return this.myMembership.role.toLowerCase() === 'superuser';
  }

  get canDelete(): boolean {
    return this.canDeleteRoom ?? (this.isCreator || this.isSuperuser);
  }

  /**
   * Returns a copy with the current user's membership role replaced. Used to
   * apply a `room_role_changed` SSE event (promote/demote) without re-fetching
   * the whole room detail.
   */
  copyWithRole(role: string): RoomDetail {
    return new RoomDetail({
      id: this.id,
      name: this.name,
      rid: this.rid,
      visibility: this.visibility,
      description: this.description,
      joinPolicy: this.joinPolicy,
      remarkName: this.remarkName,
      notificationPolicy: this.notificationPolicy,
      isPinned: this.isPinned,
      personalProfile: this.personalProfile,
      aiVoiceAnnouncementsEnabled: this.aiVoiceAnnouncementsEnabled,
      messageRecallPolicy: this.messageRecallPolicy,
      messageRecallWindowSeconds: this.messageRecallWindowSeconds,
      canDeleteRoom: this.canDeleteRoom,
      gangBotAvailable: this.gangBotAvailable,
      gangBotEnabled: this.gangBotEnabled,
      avatarUrl: this.avatarUrl,
      defaultAvatarKey: this.defaultAvatarKey,
      memberCount: this.memberCount,
      onlineMemberCount: this.onlineMemberCount,
      createdBy: this.createdBy,
      myMembership: new RoomMembership({
        joinedAt: this.myMembership.joinedAt,
        role,
        aiVoiceAnnouncementsEnabled: this.myMembership.aiVoiceAnnouncementsEnabled,
      }),
      live: this.live,
      createdAt: this.createdAt,
      updatedAt: this.updatedAt,
    });
  }

  toCard(): RoomCard {
    return new RoomCard({
      id: this.id,
      name: this.name,
      rid: this.rid,
      visibility: this.visibility,
      remarkName: this.remarkName,
      description: this.description,
      notificationPolicy: this.notificationPolicy,
      isPinned: this.isPinned,
      avatarUrl: this.avatarUrl,
      defaultAvatarKey: this.defaultAvatarKey,
      memberCount: this.memberCount,
      onlineMemberCount: this.onlineMemberCount,
      liveParticipantCount: this.live.participantCount,
      liveAvatarPreview: this.live.participants.map((p) => p.user).slice(0, 5),
      aiVoiceAnnouncementsEnabled: this.aiVoiceAnnouncementsEnabled,
      gangBotAvailable: this.gangBotAvailable,
      gangBotEnabled: this.gangBotEnabled,
      lastMessage: null,
      unreadCount: 0,
      updatedAt: this.updatedAt,
    });
  }
}

export class MessageQuote {
  readonly messageId: string;
  readonly senderDisplayName: string;
  readonly body: string;
  readonly createdAt: Date;
  readonly previewAttachment: MessageAttachment | null;

  constructor(opts: {
    messageId: string;
    senderDisplayName: string;
    body: string;
    createdAt: Date;
    previewAttachment?: MessageAttachment | null;
  }) {
    this.messageId = opts.messageId;
    this.senderDisplayName = opts.senderDisplayName;
    this.body = opts.body;
    this.createdAt = opts.createdAt;
    this.previewAttachment = opts.previewAttachment ?? null;
  }

  static fromJson(json: JsonMap): MessageQuote {
    const previewAttachmentJson = _nullableMap(json['preview_attachment']);
    return new MessageQuote({
      messageId: requireString(json, 'message_id'),
      senderDisplayName: asString(json['sender_display_name']) ?? '用户',
      body: asString(json['body']) ?? '',
      createdAt: requireDate(json, 'created_at'),
      previewAttachment: previewAttachmentJson == null ? null : MessageAttachment.fromJson(previewAttachmentJson),
    });
  }
}

export class Message {
  readonly id: string;
  readonly roomId: string;
  readonly sender: UserSummary;
  readonly clientMessageId: string;
  readonly type: string;
  readonly body: string;
  readonly createdAt: Date;
  readonly attachments: MessageAttachment[];
  readonly quote: MessageQuote | null;
  readonly quotes: MessageQuote[];

  get effectiveQuotes(): MessageQuote[] {
    if (this.quotes.length > 0) return this.quotes;
    const legacy = this.quote;
    return legacy == null ? [] : [legacy];
  }

  /**
   * Raw mention descriptors as sent by the server (opaque maps; shape is
   * `{user_id, ...}`). Kept as-is so the renderer can highlight @mentions.
   */
  readonly mentions: JsonMap[];

  /**
   * Set when the sender (or an admin within policy) recalled the message.
   * The body/attachments are tombstoned server-side; the client shows a
   * "message recalled" placeholder.
   */
  readonly isRecalled: boolean;
  readonly recalledAt: Date | null;
  readonly recalledBy: UserSummary | null;

  /**
   * Set when an admin force-deleted the message. Distinct from a recall:
   * it's a moderation action, not the author retracting their own message.
   */
  readonly isForceDeleted: boolean;
  readonly forceDeletedAt: Date | null;
  readonly forceDeletedBy: UserSummary | null;

  readonly pending: boolean;
  readonly failed: boolean;

  constructor(opts: {
    id: string;
    roomId: string;
    sender: UserSummary;
    clientMessageId: string;
    type?: string;
    body: string;
    createdAt: Date;
    attachments?: MessageAttachment[];
    mentions?: JsonMap[];
    quote?: MessageQuote | null;
    quotes?: MessageQuote[];
    isRecalled?: boolean;
    recalledAt?: Date | null;
    recalledBy?: UserSummary | null;
    isForceDeleted?: boolean;
    forceDeletedAt?: Date | null;
    forceDeletedBy?: UserSummary | null;
    pending?: boolean;
    failed?: boolean;
  }) {
    this.id = opts.id;
    this.roomId = opts.roomId;
    this.sender = opts.sender;
    this.clientMessageId = opts.clientMessageId;
    this.type = opts.type ?? 'text';
    this.body = opts.body;
    this.createdAt = opts.createdAt;
    this.attachments = opts.attachments ?? [];
    this.mentions = opts.mentions ?? [];
    this.quote = opts.quote ?? null;
    this.quotes = opts.quotes ?? [];
    this.isRecalled = opts.isRecalled ?? false;
    this.recalledAt = opts.recalledAt ?? null;
    this.recalledBy = opts.recalledBy ?? null;
    this.isForceDeleted = opts.isForceDeleted ?? false;
    this.forceDeletedAt = opts.forceDeletedAt ?? null;
    this.forceDeletedBy = opts.forceDeletedBy ?? null;
    this.pending = opts.pending ?? false;
    this.failed = opts.failed ?? false;
  }

  /**
   * Whether the message content has been removed (recalled or force-deleted)
   * and should render as a placeholder rather than its original body.
   */
  get isRemoved(): boolean {
    return this.isRecalled || this.isForceDeleted;
  }

  get stickerAttachment(): MessageAttachment | null {
    if (this.type !== 'sticker') return null;
    for (const attachment of this.attachments) {
      if (attachment.type === 'sticker' && attachment.asset != null) {
        return attachment;
      }
    }
    return null;
  }

  get fileAttachments(): MessageAttachment[] {
    return this.attachments.filter((attachment) => attachment.type === 'file');
  }

  get playlistAttachment(): MessageAttachment | null {
    if (this.type !== 'playlist') return null;
    for (const attachment of this.attachments) {
      if (attachment.type === 'playlist' && attachment.playlist != null) {
        return attachment;
      }
    }
    return null;
  }

  get musicTrackAttachment(): MessageAttachment | null {
    if (this.type !== 'music_track') return null;
    for (const attachment of this.attachments) {
      if (attachment.type === 'music_track' && attachment.track != null) {
        return attachment;
      }
    }
    return null;
  }

  static fromJson(json: JsonMap): Message {
    const senderCommonRooms = _listOfMaps(json['sender_common_rooms'])
      .map((room) => UserCommonRoom.fromJson(room))
      .filter((room) => room.isUsable);
    const sender = UserSummary.fromJson(requireMap(json, 'sender')).copyWith({
      roomDisplayName: _stringFromJson(json, [
        'sender_room_display_name',
        'sender_room_username',
        'sender_room_nickname',
        'sender_room_remark',
      ]),
      roomRole: _stringFromJson(json, ['sender_room_role', 'sender_membership_role']),
      commonRooms: senderCommonRooms.length === 0 ? null : senderCommonRooms,
    });
    const recalledByJson = _nullableMap(json['recalled_by']);
    const forceDeletedByJson = _nullableMap(json['force_deleted_by']);
    const quoteJson = _nullableMap(json['quote']);
    const parsedQuotes = _listOfMaps(json['quotes']).map((quote) => MessageQuote.fromJson(quote));
    const legacyQuote = quoteJson == null ? null : MessageQuote.fromJson(quoteJson);
    return new Message({
      id: requireString(json, 'id'),
      roomId: requireString(json, 'room_id'),
      sender,
      clientMessageId: requireString(json, 'client_message_id'),
      type: asString(json['type']) ?? 'text',
      body: asString(json['body']) ?? '',
      attachments: _listOfMaps(json['attachments']).map((attachment) => MessageAttachment.fromJson(attachment)),
      mentions: _listOfMaps(json['mentions']),
      quote: legacyQuote ?? (parsedQuotes.length === 0 ? null : parsedQuotes[0]!),
      quotes: parsedQuotes.length > 0 ? parsedQuotes : legacyQuote == null ? [] : [legacyQuote],
      isRecalled: _boolFromJson(json, ['is_recalled']) ?? false,
      recalledAt: _parseDateTime(json['recalled_at']),
      recalledBy: recalledByJson == null ? null : UserSummary.fromJson(recalledByJson),
      isForceDeleted: _boolFromJson(json, ['is_force_deleted']) ?? false,
      forceDeletedAt: _parseDateTime(json['force_deleted_at']),
      forceDeletedBy: forceDeletedByJson == null ? null : UserSummary.fromJson(forceDeletedByJson),
      createdAt: requireDate(json, 'created_at'),
    });
  }

  static local(opts: {
    roomId: string;
    sender: UserSummary;
    clientMessageId: string;
    body: string;
    type?: string;
    attachments?: MessageAttachment[];
    mentions?: JsonMap[];
    quote?: MessageQuote | null;
    quotes?: MessageQuote[];
  }): Message {
    const { roomId, sender, clientMessageId, body, type = 'text', attachments = [], mentions = [], quote = null, quotes = [] } =
      opts;
    return new Message({
      id: clientMessageId,
      roomId,
      sender,
      clientMessageId,
      type,
      body,
      createdAt: new Date(),
      attachments,
      mentions,
      quote,
      quotes,
      pending: true,
    });
  }

  markFailed(): Message {
    return new Message({
      id: this.id,
      roomId: this.roomId,
      sender: this.sender,
      clientMessageId: this.clientMessageId,
      type: this.type,
      body: this.body,
      createdAt: this.createdAt,
      attachments: this.attachments,
      mentions: this.mentions,
      quote: this.quote,
      quotes: this.quotes,
      isRecalled: this.isRecalled,
      recalledAt: this.recalledAt,
      recalledBy: this.recalledBy,
      isForceDeleted: this.isForceDeleted,
      forceDeletedAt: this.forceDeletedAt,
      forceDeletedBy: this.forceDeletedBy,
      failed: true,
    });
  }
}

export class LiveParticipant {
  readonly liveSessionId: string;
  readonly user: UserSummary;
  readonly joinedAt: Date;
  readonly micMuted: boolean;
  readonly micBlocked: boolean;
  readonly headphonesMuted: boolean;
  readonly headphonesBlocked: boolean;

  /**
   * Whether the participant is actively listening (headphones engaged).
   * Inverse-ish of [headphonesMuted] but sent explicitly by the server;
   * defaults to true for servers that omit it.
   */
  readonly headphonesListening: boolean;

  /**
   * A persistent room-level microphone mute set by an admin (`mute_mic`). While
   * true the participant's LiveKit publish permission is revoked: the mic is
   * force-muted and self-unmute requests are rejected by the server. Survives
   * reconnects until an admin runs `restore_voice`. Defaults to false for
   * servers that don't send the field.
   */
  readonly voiceBlocked: boolean;
  readonly cameraOn: boolean;
  readonly cameraMirrored: boolean;
  readonly screenSharing: boolean;
  readonly screenViewers: UserSummary[];

  readonly connectionState: string;

  constructor(opts: {
    liveSessionId: string;
    user: UserSummary;
    joinedAt: Date;
    micMuted: boolean;
    micBlocked?: boolean;
    headphonesMuted: boolean;
    headphonesBlocked?: boolean;
    headphonesListening?: boolean;
    voiceBlocked: boolean;
    cameraOn: boolean;
    cameraMirrored?: boolean;
    screenSharing: boolean;
    screenViewers?: UserSummary[];
    connectionState: string;
  }) {
    this.liveSessionId = opts.liveSessionId;
    this.user = opts.user;
    this.joinedAt = opts.joinedAt;
    this.micMuted = opts.micMuted;
    this.micBlocked = opts.micBlocked ?? false;
    this.headphonesMuted = opts.headphonesMuted;
    this.headphonesBlocked = opts.headphonesBlocked ?? false;
    this.headphonesListening = opts.headphonesListening ?? true;
    this.voiceBlocked = opts.voiceBlocked;
    this.cameraOn = opts.cameraOn;
    this.cameraMirrored = opts.cameraMirrored ?? false;
    this.screenSharing = opts.screenSharing;
    this.screenViewers = opts.screenViewers ?? [];
    this.connectionState = opts.connectionState;
  }

  copyWith(
    opts: {
      liveSessionId?: string | null;
      user?: UserSummary | null;
      joinedAt?: Date | null;
      micMuted?: boolean | null;
      micBlocked?: boolean | null;
      headphonesMuted?: boolean | null;
      headphonesBlocked?: boolean | null;
      headphonesListening?: boolean | null;
      voiceBlocked?: boolean | null;
      cameraOn?: boolean | null;
      cameraMirrored?: boolean | null;
      screenSharing?: boolean | null;
      screenViewers?: UserSummary[] | null;
      connectionState?: string | null;
    } = {},
  ): LiveParticipant {
    return new LiveParticipant({
      liveSessionId: opts.liveSessionId ?? this.liveSessionId,
      user: opts.user ?? this.user,
      joinedAt: opts.joinedAt ?? this.joinedAt,
      micMuted: opts.micMuted ?? this.micMuted,
      micBlocked: opts.micBlocked ?? this.micBlocked,
      headphonesMuted: opts.headphonesMuted ?? this.headphonesMuted,
      headphonesBlocked: opts.headphonesBlocked ?? this.headphonesBlocked,
      headphonesListening: opts.headphonesListening ?? this.headphonesListening,
      voiceBlocked: opts.voiceBlocked ?? this.voiceBlocked,
      cameraOn: opts.cameraOn ?? this.cameraOn,
      cameraMirrored: opts.cameraMirrored ?? this.cameraMirrored,
      screenSharing: opts.screenSharing ?? this.screenSharing,
      screenViewers: opts.screenViewers ?? this.screenViewers,
      connectionState: opts.connectionState ?? this.connectionState,
    });
  }

  static fromJson(json: JsonMap): LiveParticipant {
    const commonRooms = _listOfMaps(json['common_rooms'])
      .map((room) => UserCommonRoom.fromJson(room))
      .filter((room) => room.isUsable);
    const user = UserSummary.fromJson(requireMap(json, 'user')).copyWith({
      roomDisplayName: _stringFromJson(json, ['room_display_name', 'room_username', 'room_nickname', 'room_remark']),
      roomRole: _stringFromJson(json, ['room_role', 'membership_role']),
      commonRooms: commonRooms.length === 0 ? null : commonRooms,
    });
    return new LiveParticipant({
      liveSessionId: requireString(json, 'live_session_id'),
      user,
      joinedAt: requireDate(json, 'joined_at'),
      micMuted: requireBool(json, 'mic_muted'),
      micBlocked: asBool(json['mic_blocked']) ?? false,
      headphonesMuted: asBool(json['headphones_muted']) ?? false,
      headphonesBlocked: asBool(json['headphones_blocked']) ?? false,
      headphonesListening: asBool(json['headphones_listening']) ?? true,
      voiceBlocked: asBool(json['voice_blocked']) ?? false,
      cameraOn: requireBool(json, 'camera_on'),
      cameraMirrored: asBool(json['camera_mirrored']) ?? false,
      screenSharing: requireBool(json, 'screen_sharing'),
      screenViewers: _listOfMaps(json['screen_viewers']).map((viewer) => UserSummary.fromJson(viewer)),
      connectionState: requireString(json, 'connection_state'),
    });
  }
}

/**
 * A per-listener volume override for one target speaker in a live room. Each
 * listener tunes how loudly they hear each other participant; the server keys
 * these by (room, listener, target) and only ever returns the caller's own
 * overrides.
 */
export class LiveMemberVolume {
  readonly roomId: string;
  readonly targetUser: UserSummary;

  /** 0–100, where 100 is the unattenuated default. */
  readonly volume: number;
  readonly updatedAt: Date;

  constructor(opts: { roomId: string; targetUser: UserSummary; volume: number; updatedAt: Date }) {
    this.roomId = opts.roomId;
    this.targetUser = opts.targetUser;
    this.volume = opts.volume;
    this.updatedAt = opts.updatedAt;
  }

  static fromJson(json: JsonMap): LiveMemberVolume {
    return new LiveMemberVolume({
      roomId: requireString(json, 'room_id'),
      targetUser: UserSummary.fromJson(requireMap(json, 'target_user')),
      volume: requireInt(json, 'volume'),
      updatedAt: requireDate(json, 'updated_at'),
    });
  }
}

/**
 * A pending request to recall a message, created when a room's recall policy
 * is `admin_approval` and a non-admin asks to recall their own message. Admins
 * list and approve/reject these.
 */
export class MessageRecallRequest {
  readonly id: string;
  readonly roomId: string;
  readonly messageId: string;
  readonly status: string;
  readonly createdAt: Date;
  readonly requestedByUserId: string | null;

  constructor(opts: {
    id: string;
    roomId: string;
    messageId: string;
    status: string;
    createdAt: Date;
    requestedByUserId?: string | null;
  }) {
    this.id = opts.id;
    this.roomId = opts.roomId;
    this.messageId = opts.messageId;
    this.status = opts.status;
    this.createdAt = opts.createdAt;
    this.requestedByUserId = opts.requestedByUserId ?? null;
  }

  static fromJson(json: JsonMap): MessageRecallRequest {
    return new MessageRecallRequest({
      id: requireString(json, 'id'),
      roomId: requireString(json, 'room_id'),
      messageId: requireString(json, 'message_id'),
      status: asString(json['status']) ?? 'pending',
      requestedByUserId: asString(json['requested_by_user_id']),
      createdAt: requireDate(json, 'created_at'),
    });
  }
}

/**
 * The outcome of a recall request. The server either recalls the message
 * immediately (sender within policy/window, or an admin) returning the updated
 * [message], or — under `admin_approval` for a non-admin — files a pending
 * [recallRequest] for an admin to review.
 */
export class MessageRecallResult {
  /** Set when the recall took effect immediately. */
  readonly message: Message | null;

  /** Set when the recall was queued for admin approval instead. */
  readonly recallRequest: MessageRecallRequest | null;

  constructor(opts: { message?: Message | null; recallRequest?: MessageRecallRequest | null } = {}) {
    this.message = opts.message ?? null;
    this.recallRequest = opts.recallRequest ?? null;
  }

  get isPending(): boolean {
    return this.recallRequest != null && this.message == null;
  }

  static fromJson(json: JsonMap): MessageRecallResult {
    const messageJson = _nullableMap(json['message']);
    const requestJson = _nullableMap(json['recall_request']);
    return new MessageRecallResult({
      message: messageJson == null ? null : Message.fromJson(messageJson),
      recallRequest: requestJson == null ? null : MessageRecallRequest.fromJson(requestJson),
    });
  }
}

export class LiveState {
  readonly roomId: string;
  readonly participantCount: number;
  readonly participants: LiveParticipant[];
  readonly updatedAt: Date;

  constructor(opts: { roomId: string; participantCount: number; participants: LiveParticipant[]; updatedAt: Date }) {
    this.roomId = opts.roomId;
    this.participantCount = opts.participantCount;
    this.participants = opts.participants;
    this.updatedAt = opts.updatedAt;
  }

  static fromJson(json: JsonMap): LiveState {
    return new LiveState({
      roomId: requireString(json, 'room_id'),
      participantCount: requireInt(json, 'participant_count'),
      participants: _listOfMaps(json['participants']).map((participant) => LiveParticipant.fromJson(participant)),
      updatedAt: requireDate(json, 'updated_at'),
    });
  }
}

export class LiveKitConnectionInfo {
  readonly serverUrl: string;
  readonly token: string;
  readonly tokenExpiresAt: Date;
  readonly roomName: string;

  constructor(opts: { serverUrl: string; token: string; tokenExpiresAt: Date; roomName: string }) {
    this.serverUrl = opts.serverUrl;
    this.token = opts.token;
    this.tokenExpiresAt = opts.tokenExpiresAt;
    this.roomName = opts.roomName;
  }

  static fromJson(json: JsonMap): LiveKitConnectionInfo {
    return new LiveKitConnectionInfo({
      serverUrl: requireString(json, 'server_url'),
      token: requireString(json, 'token'),
      tokenExpiresAt: requireDate(json, 'token_expires_at'),
      roomName: requireString(json, 'room_name'),
    });
  }
}

/**
 * A publish-only LiveKit token for the hidden screen-audio aux participant
 * (`<ownerId>--screen-audio`). The aux participant publishes the screen-share
 * audio track through an isolated WebRTC factory; it is hidden from the
 * receiver UI and never appears in the roster.
 */
export class ScreenAudioToken {
  readonly serverUrl: string;
  readonly token: string;
  readonly tokenExpiresAt: Date;
  readonly roomName: string;
  readonly identity: string;

  constructor(opts: { serverUrl: string; token: string; tokenExpiresAt: Date; roomName: string; identity: string }) {
    this.serverUrl = opts.serverUrl;
    this.token = opts.token;
    this.tokenExpiresAt = opts.tokenExpiresAt;
    this.roomName = opts.roomName;
    this.identity = opts.identity;
  }

  static fromJson(json: JsonMap): ScreenAudioToken {
    return new ScreenAudioToken({
      serverUrl: requireString(json, 'server_url'),
      token: requireString(json, 'token'),
      tokenExpiresAt: requireDate(json, 'token_expires_at'),
      roomName: requireString(json, 'room_name'),
      identity: requireString(json, 'identity'),
    });
  }
}

export class LiveJoinResult {
  readonly liveKit: LiveKitConnectionInfo;
  readonly participant: LiveParticipant;
  readonly live: LiveState;

  constructor(opts: { liveKit: LiveKitConnectionInfo; participant: LiveParticipant; live: LiveState }) {
    this.liveKit = opts.liveKit;
    this.participant = opts.participant;
    this.live = opts.live;
  }

  static fromJson(json: JsonMap): LiveJoinResult {
    return new LiveJoinResult({
      liveKit: LiveKitConnectionInfo.fromJson(requireMap(json, 'livekit')),
      participant: LiveParticipant.fromJson(requireMap(json, 'participant')),
      live: LiveState.fromJson(requireMap(json, 'live')),
    });
  }
}

/**
 * The fixed LiveKit identity of the server-side music box bot. It joins the
 * room's voice session and publishes a single audio track; filter it out of
 * participant lists so it isn't rendered as a real member.
 */
export const musicBoxBotIdentity = '__musicbox__';

export enum MusicBoxPlaybackState {
  stopped = 'stopped',
  playing = 'playing',
  paused = 'paused',
}

export enum MusicBoxPlaybackMode {
  sequential = 'sequential',
  repeatOne = 'repeatOne',
  repeatAll = 'repeatAll',
  shuffle = 'shuffle',
}

function _musicBoxPlaybackModeFrom(value: string | null): MusicBoxPlaybackMode {
  switch (value?.trim().toLowerCase()) {
    case 'repeat_one':
      return MusicBoxPlaybackMode.repeatOne;
    case 'repeat_all':
      return MusicBoxPlaybackMode.repeatAll;
    case 'shuffle':
      return MusicBoxPlaybackMode.shuffle;
    default:
      return MusicBoxPlaybackMode.sequential;
  }
}

export function musicBoxPlaybackModeValue(mode: MusicBoxPlaybackMode): string {
  switch (mode) {
    case MusicBoxPlaybackMode.sequential:
      return 'sequential';
    case MusicBoxPlaybackMode.repeatOne:
      return 'repeat_one';
    case MusicBoxPlaybackMode.repeatAll:
      return 'repeat_all';
    case MusicBoxPlaybackMode.shuffle:
      return 'shuffle';
  }
}

export enum MusicBoxActiveSourceType {
  temporary = 'temporary',
  roomPlaylist = 'roomPlaylist',
  userPlaylist = 'userPlaylist',
}

function _musicBoxActiveSourceTypeFrom(value: string | null): MusicBoxActiveSourceType {
  switch (value?.trim().toLowerCase()) {
    case 'room_playlist':
      return MusicBoxActiveSourceType.roomPlaylist;
    case 'user_playlist':
      return MusicBoxActiveSourceType.userPlaylist;
    default:
      return MusicBoxActiveSourceType.temporary;
  }
}

export function musicBoxActiveSourceTypeValue(type: MusicBoxActiveSourceType): string {
  switch (type) {
    case MusicBoxActiveSourceType.temporary:
      return 'temporary';
    case MusicBoxActiveSourceType.roomPlaylist:
      return 'room_playlist';
    case MusicBoxActiveSourceType.userPlaylist:
      return 'user_playlist';
  }
}

export class MusicBoxCapabilities {
  readonly canControl: boolean;
  readonly canChangeMode: boolean;
  readonly canReorder: boolean;
  readonly allowedModes: MusicBoxPlaybackMode[];

  constructor(
    opts: {
      canControl?: boolean;
      canChangeMode?: boolean;
      canReorder?: boolean;
      allowedModes?: MusicBoxPlaybackMode[];
    } = {},
  ) {
    this.canControl = opts.canControl ?? true;
    this.canChangeMode = opts.canChangeMode ?? true;
    this.canReorder = opts.canReorder ?? true;
    this.allowedModes = opts.allowedModes ?? [
      MusicBoxPlaybackMode.sequential,
      MusicBoxPlaybackMode.repeatOne,
      MusicBoxPlaybackMode.repeatAll,
      MusicBoxPlaybackMode.shuffle,
    ];
  }

  static fromJson(json: JsonMap | null): MusicBoxCapabilities {
    const values = [
      ...new Set((asList(json?.['allowed_modes']) ?? []).map((value) => _musicBoxPlaybackModeFrom(_dartToString(value)))),
    ];
    return new MusicBoxCapabilities({
      canControl: _boolFromJson(json, ['can_control']) ?? true,
      canChangeMode: _boolFromJson(json, ['can_change_mode']) ?? true,
      canReorder: _boolFromJson(json, ['can_reorder']) ?? true,
      allowedModes:
        values.length === 0
          ? [
              MusicBoxPlaybackMode.sequential,
              MusicBoxPlaybackMode.repeatOne,
              MusicBoxPlaybackMode.repeatAll,
              MusicBoxPlaybackMode.shuffle,
            ]
          : values,
    });
  }
}

export class MusicBoxActiveSource {
  readonly type: MusicBoxActiveSourceType;
  readonly id: string;
  readonly name: string;
  readonly ownerUserId: string;
  readonly snapshotId: string;
  readonly createdAt: Date | null;
  readonly owner: MusicBoxRequester | null;

  constructor(
    opts: {
      type?: MusicBoxActiveSourceType;
      id?: string;
      name?: string;
      ownerUserId?: string;
      snapshotId?: string;
      createdAt?: Date | null;
      owner?: MusicBoxRequester | null;
    } = {},
  ) {
    this.type = opts.type ?? MusicBoxActiveSourceType.temporary;
    this.id = opts.id ?? '';
    this.name = opts.name ?? '点歌队列';
    this.ownerUserId = opts.ownerUserId ?? '';
    this.snapshotId = opts.snapshotId ?? '';
    this.createdAt = opts.createdAt ?? null;
    this.owner = opts.owner ?? null;
  }

  static fromJson(json: JsonMap | null): MusicBoxActiveSource {
    const type = _musicBoxActiveSourceTypeFrom(_dartToString(json?.['type']));
    const parsedName = _stringFromJson(json, ['name']);
    return new MusicBoxActiveSource({
      type,
      id: _stringFromJson(json, ['id', 'playlist_id']) ?? '',
      name: type === MusicBoxActiveSourceType.temporary ? '点歌队列' : (parsedName ?? '未命名歌单'),
      ownerUserId: _stringFromJson(json, ['owner_user_id']) ?? '',
      snapshotId: _stringFromJson(json, ['snapshot_id']) ?? '',
      createdAt: _parseDateTime(json?.['created_at']),
      owner: MusicBoxRequester.fromJson(_nullableMap(json?.['owner'])),
    });
  }
}

function _musicBoxPlaybackStateFrom(value: string | null): MusicBoxPlaybackState {
  switch (value?.trim().toLowerCase()) {
    case 'playing':
      return MusicBoxPlaybackState.playing;
    case 'paused':
      return MusicBoxPlaybackState.paused;
    default:
      return MusicBoxPlaybackState.stopped;
  }
}

export enum MusicBoxQueueItemStatus {
  pending = 'pending',
  downloading = 'downloading',
  ready = 'ready',
  failed = 'failed',
}

function _musicBoxQueueItemStatusFrom(value: string | null): MusicBoxQueueItemStatus {
  switch (value?.trim().toLowerCase()) {
    case 'downloading':
      return MusicBoxQueueItemStatus.downloading;
    case 'ready':
      return MusicBoxQueueItemStatus.ready;
    case 'failed':
      return MusicBoxQueueItemStatus.failed;
    default:
      return MusicBoxQueueItemStatus.pending;
  }
}

/**
 * The room music box's current playback head. [positionMs] comes from the
 * server-authoritative snapshot stream; clients render it as reported instead
 * of advancing a separate local clock. See `music_box_display.dart`.
 */
export class MusicBoxPlayback {
  readonly state: MusicBoxPlaybackState;

  /**
   * The `id` of the playing queue item, matching [MusicBoxQueueItem.id].
   * Empty when nothing is current.
   */
  readonly currentItemId: string;
  readonly positionMs: number;
  readonly volume: number;
  readonly updatedAt: Date | null;
  readonly mode: MusicBoxPlaybackMode;
  readonly canPrevious: boolean;
  readonly canNext: boolean;
  readonly capabilities: MusicBoxCapabilities;

  constructor(opts: {
    state: MusicBoxPlaybackState;
    currentItemId: string;
    positionMs: number;
    volume: number;
    updatedAt: Date | null;
    mode?: MusicBoxPlaybackMode;
    canPrevious?: boolean;
    canNext?: boolean;
    capabilities?: MusicBoxCapabilities;
  }) {
    this.state = opts.state;
    this.currentItemId = opts.currentItemId;
    this.positionMs = opts.positionMs;
    this.volume = opts.volume;
    this.updatedAt = opts.updatedAt;
    this.mode = opts.mode ?? MusicBoxPlaybackMode.sequential;
    this.canPrevious = opts.canPrevious ?? false;
    this.canNext = opts.canNext ?? false;
    this.capabilities = opts.capabilities ?? new MusicBoxCapabilities();
  }

  get hasCurrent(): boolean {
    return this.currentItemId.length > 0;
  }

  static fromJson(json: JsonMap): MusicBoxPlayback {
    return new MusicBoxPlayback({
      state: _musicBoxPlaybackStateFrom(asString(json['state'])),
      currentItemId: _stringFromJson(json, ['current_item_id']) ?? '',
      positionMs: _intFromJson(json, ['position_ms']) ?? 0,
      volume: _intFromJson(json, ['volume']) ?? 100,
      updatedAt: _parseDateTime(json['updated_at']),
      mode: _musicBoxPlaybackModeFrom(_dartToString(json['mode'])),
      canPrevious: _boolFromJson(json, ['can_previous']) ?? false,
      canNext: _boolFromJson(json, ['can_next']) ?? false,
      capabilities: MusicBoxCapabilities.fromJson(_nullableMap(json['capabilities'])),
    });
  }

  copyWith(
    opts: {
      positionMs?: number | null;
      updatedAt?: Date | null;
      canPrevious?: boolean | null;
      canNext?: boolean | null;
    } = {},
  ): MusicBoxPlayback {
    return new MusicBoxPlayback({
      state: this.state,
      currentItemId: this.currentItemId,
      positionMs: opts.positionMs ?? this.positionMs,
      volume: this.volume,
      updatedAt: opts.updatedAt ?? this.updatedAt,
      mode: this.mode,
      canPrevious: opts.canPrevious ?? this.canPrevious,
      canNext: opts.canNext ?? this.canNext,
      capabilities: this.capabilities,
    });
  }
}

/**
 * A compact position-only SSE update. Structural changes continue to arrive
 * as a complete [MusicBoxState] snapshot.
 */
export class MusicBoxProgressEvent {
  readonly revision: number;
  readonly currentItemId: string;
  readonly positionMs: number;

  constructor(opts: { revision: number; currentItemId: string; positionMs: number }) {
    this.revision = opts.revision;
    this.currentItemId = opts.currentItemId;
    this.positionMs = opts.positionMs;
  }

  static fromJson(json: JsonMap): MusicBoxProgressEvent {
    return new MusicBoxProgressEvent({
      revision: _intFromJson(json, ['revision']) ?? 0,
      currentItemId: _stringFromJson(json, ['current_item_id']) ?? '',
      positionMs: _intFromJson(json, ['position_ms']) ?? 0,
    });
  }
}

/**
 * One track in the room music box queue. [status] tracks the server-side
 * download/transcode lifecycle; see [playable] for when the room can play it.
 */
export class MusicBoxQueueItem {
  readonly id: string;
  readonly source: string;
  readonly trackId: string;
  readonly title: string;
  readonly artist: string;
  readonly durationMs: number;
  readonly status: MusicBoxQueueItemStatus;
  readonly fileSizeBytes: number;

  /**
   * Failure reason, populated only when [status] is
   * [MusicBoxQueueItemStatus.failed]. Empty otherwise.
   */
  readonly error: string;
  readonly addedByUserId: string;
  readonly createdAt: Date | null;
  readonly canPlayNow: boolean;
  readonly canRemove: boolean;
  readonly requestedBy: MusicBoxRequester | null;
  private readonly _playable: boolean | null;

  constructor(opts: {
    id: string;
    source: string;
    trackId: string;
    title: string;
    artist: string;
    durationMs: number;
    status: MusicBoxQueueItemStatus;
    fileSizeBytes: number;
    error: string;
    addedByUserId: string;
    createdAt: Date | null;
    canPlayNow?: boolean;
    canRemove?: boolean;
    requestedBy?: MusicBoxRequester | null;
    playable?: boolean | null;
  }) {
    this.id = opts.id;
    this.source = opts.source;
    this.trackId = opts.trackId;
    this.title = opts.title;
    this.artist = opts.artist;
    this.durationMs = opts.durationMs;
    this.status = opts.status;
    this.fileSizeBytes = opts.fileSizeBytes;
    this.error = opts.error;
    this.addedByUserId = opts.addedByUserId;
    this.createdAt = opts.createdAt;
    this.canPlayNow = opts.canPlayNow ?? false;
    this.canRemove = opts.canRemove ?? true;
    this.requestedBy = opts.requestedBy ?? null;
    this._playable = opts.playable ?? null;
  }

  /**
   * Whether the room can play this item now: its audio is ready, or it is
   * still [MusicBoxQueueItemStatus.downloading] but the server is already
   * streaming the transcode. Servers that predate streaming don't send the
   * field; for them only ready items are playable.
   */
  get playable(): boolean {
    return this._playable ?? this.status === MusicBoxQueueItemStatus.ready;
  }

  static fromJson(json: JsonMap): MusicBoxQueueItem {
    const playable = json['playable'];
    return new MusicBoxQueueItem({
      id: _stringFromJson(json, ['id', 'item_id']) ?? '',
      source: _stringFromJson(json, ['source']) ?? '',
      trackId: _stringFromJson(json, ['track_id']) ?? '',
      title: _stringFromJson(json, ['title', 'name']) ?? '',
      artist: _stringFromJson(json, ['artist']) ?? '',
      durationMs: _intFromJson(json, ['duration_ms']) ?? 0,
      status: _musicBoxQueueItemStatusFrom(asString(json['status'])),
      fileSizeBytes: _intFromJson(json, ['file_size_bytes']) ?? 0,
      error: _stringFromJson(json, ['error']) ?? '',
      addedByUserId: _stringFromJson(json, ['added_by_user_id']) ?? '',
      createdAt: _parseDateTime(json['created_at']),
      canPlayNow: json['can_play_now'] === true,
      canRemove: _containsKey(json, 'can_remove') ? json['can_remove'] === true : true,
      requestedBy: MusicBoxRequester.fromJson(_nullableMap(json['requested_by'])),
      playable: typeof playable === 'boolean' ? playable : null,
    });
  }
}

export class MusicBoxRequester {
  readonly userId: string;
  readonly username: string;
  readonly displayName: string;
  readonly avatarLabel: string;
  readonly avatarUrl: string | null;
  readonly defaultAvatarKey: string;

  constructor(opts: {
    userId: string;
    displayName: string;
    username?: string | null;
    avatarLabel?: string | null;
    avatarUrl: string | null;
    defaultAvatarKey: string;
  }) {
    this.userId = opts.userId;
    this.displayName = opts.displayName;
    this.username = opts.username ?? opts.avatarLabel ?? opts.displayName;
    this.avatarLabel = opts.avatarLabel ?? opts.displayName;
    this.avatarUrl = opts.avatarUrl;
    this.defaultAvatarKey = opts.defaultAvatarKey;
  }

  static fromJson(json: JsonMap | null): MusicBoxRequester | null {
    if (json == null) return null;
    const userId = _stringFromJson(json, ['user_id', 'id']) ?? '';
    if (userId.length === 0) return null;
    return new MusicBoxRequester({
      userId,
      username: _stringFromJson(json, ['username']),
      displayName: _stringFromJson(json, ['display_name', 'username']) ?? userId,
      avatarLabel: _stringFromJson(json, ['avatar_label', 'username']) ?? userId,
      avatarUrl: _stringFromJson(json, ['avatar_url']),
      defaultAvatarKey: _stringFromJson(json, ['default_avatar_key']) ?? 'blue-3',
    });
  }
}

/**
 * The room music box's disk usage against its per-room limit. Useful for
 * warning the user as the room approaches its storage cap; over the limit,
 * new songs queue as `pending` rather than being rejected.
 */
export class MusicBoxUsage {
  readonly usedBytes: number;
  readonly limitBytes: number;

  constructor(opts: { usedBytes: number; limitBytes: number }) {
    this.usedBytes = opts.usedBytes;
    this.limitBytes = opts.limitBytes;
  }

  static fromJson(json: JsonMap | null): MusicBoxUsage {
    return new MusicBoxUsage({
      usedBytes: _intFromJson(json, ['used_bytes']) ?? 0,
      limitBytes: _intFromJson(json, ['limit_bytes']) ?? 0,
    });
  }
}

/**
 * The authoritative music box snapshot. Every write (queue/control/remove) and
 * the `music_box_changed` SSE event return this same shape; clients overwrite
 * local state wholesale rather than merging field by field.
 */
export class MusicBoxState {
  readonly enabled: boolean;
  readonly playback: MusicBoxPlayback;
  readonly queue: MusicBoxQueueItem[];
  readonly usage: MusicBoxUsage;
  readonly revision: number;
  readonly hasRevision: boolean;
  readonly activeSource: MusicBoxActiveSource;
  readonly temporaryQueuedCount: number;
  readonly temporaryQueue: MusicBoxQueueItem[];
  readonly canEnqueueTemporary: boolean;
  readonly canSwitchTemporary: boolean;
  readonly canReorderTemporary: boolean;
  readonly canClearTemporary: boolean;
  readonly canPlayNowTemporary: boolean;

  constructor(opts: {
    enabled: boolean;
    playback: MusicBoxPlayback;
    queue: MusicBoxQueueItem[];
    usage: MusicBoxUsage;
    revision?: number;
    hasRevision?: boolean;
    activeSource?: MusicBoxActiveSource;
    temporaryQueuedCount?: number;
    temporaryQueue?: MusicBoxQueueItem[];
    canEnqueueTemporary?: boolean;
    canSwitchTemporary?: boolean;
    canReorderTemporary?: boolean;
    canClearTemporary?: boolean;
    canPlayNowTemporary?: boolean;
  }) {
    this.enabled = opts.enabled;
    this.playback = opts.playback;
    this.queue = opts.queue;
    this.usage = opts.usage;
    this.revision = opts.revision ?? 0;
    this.hasRevision = opts.hasRevision ?? false;
    this.activeSource = opts.activeSource ?? new MusicBoxActiveSource();
    this.temporaryQueuedCount = opts.temporaryQueuedCount ?? 0;
    this.temporaryQueue = opts.temporaryQueue ?? [];
    this.canEnqueueTemporary = opts.canEnqueueTemporary ?? true;
    this.canSwitchTemporary = opts.canSwitchTemporary ?? true;
    this.canReorderTemporary = opts.canReorderTemporary ?? true;
    this.canClearTemporary = opts.canClearTemporary ?? true;
    this.canPlayNowTemporary = opts.canPlayNowTemporary ?? true;
  }

  /** The queue item currently playing, or null when nothing is current. */
  get currentItem(): MusicBoxQueueItem | null {
    if (!this.playback.hasCurrent) return null;
    for (const item of this.queue) {
      if (item.id === this.playback.currentItemId) return item;
    }
    return null;
  }

  copyWith(opts: { playback?: MusicBoxPlayback | null } = {}): MusicBoxState {
    return new MusicBoxState({
      enabled: this.enabled,
      playback: opts.playback ?? this.playback,
      queue: this.queue,
      usage: this.usage,
      revision: this.revision,
      hasRevision: this.hasRevision,
      activeSource: this.activeSource,
      temporaryQueuedCount: this.temporaryQueuedCount,
      temporaryQueue: this.temporaryQueue,
      canEnqueueTemporary: this.canEnqueueTemporary,
      canSwitchTemporary: this.canSwitchTemporary,
      canReorderTemporary: this.canReorderTemporary,
      canClearTemporary: this.canClearTemporary,
      canPlayNowTemporary: this.canPlayNowTemporary,
    });
  }

  static fromJson(json: JsonMap): MusicBoxState {
    const playbackJson = _nullableMap(json['playback']);
    const queue = _listOfMaps(json['queue']).map((item) => MusicBoxQueueItem.fromJson(item));
    const activeSource = MusicBoxActiveSource.fromJson(_nullableMap(json['active_source']));
    const parsedTemporaryQueue = _listOfMaps(json['temporary_queue']).map((item) => MusicBoxQueueItem.fromJson(item));
    const temporaryQueue = _containsKey(json, 'temporary_queue')
      ? parsedTemporaryQueue
      : activeSource.type === MusicBoxActiveSourceType.temporary
        ? queue
        : [];
    const temporaryCapabilities = _nullableMap(_nullableMap(json['temporary_playlist'])?.['capabilities']);
    let playback =
      playbackJson == null
        ? new MusicBoxPlayback({
            state: MusicBoxPlaybackState.stopped,
            currentItemId: '',
            positionMs: 0,
            volume: 100,
            updatedAt: null,
          })
        : MusicBoxPlayback.fromJson(playbackJson);
    if (
      playbackJson != null &&
      (!_containsKey(playbackJson, 'can_previous') || !_containsKey(playbackJson, 'can_next'))
    ) {
      playback = playback.copyWith({
        canPrevious: _containsKey(playbackJson, 'can_previous') ? null : playback.hasCurrent && queue.length > 0,
        canNext: _containsKey(playbackJson, 'can_next') ? null : queue.length > 0,
      });
    }
    return new MusicBoxState({
      enabled: _boolFromJson(json, ['enabled']) ?? false,
      playback,
      queue,
      usage: MusicBoxUsage.fromJson(_nullableMap(json['usage'])),
      revision: _intFromJson(json, ['revision']) ?? 0,
      hasRevision: _containsKey(json, 'revision'),
      activeSource,
      temporaryQueuedCount:
        _intFromJson(_nullableMap(json['temporary_playlist']), ['queued_count']) ?? temporaryQueue.length,
      temporaryQueue,
      canEnqueueTemporary: _boolFromJson(temporaryCapabilities, ['can_enqueue']) ?? true,
      canSwitchTemporary: _boolFromJson(temporaryCapabilities, ['can_switch']) ?? true,
      canReorderTemporary: _boolFromJson(temporaryCapabilities, ['can_reorder']) ?? true,
      canClearTemporary: _boolFromJson(temporaryCapabilities, ['can_clear']) ?? true,
      canPlayNowTemporary: _boolFromJson(temporaryCapabilities, ['can_play_now']) ?? true,
    });
  }
}

/**
 * A single hit from the music box search endpoint. [artists] is an array
 * because a track may credit multiple performers; join it for display and map
 * it onto the queue request's single `artist` string when adding.
 */
export class MusicBoxSearchResult {
  readonly trackId: string;
  readonly name: string;
  readonly artists: string[];
  readonly source: string;

  constructor(opts: { trackId: string; name: string; artists: string[]; source: string }) {
    this.trackId = opts.trackId;
    this.name = opts.name;
    this.artists = opts.artists;
    this.source = opts.source;
  }

  static fromJson(json: JsonMap): MusicBoxSearchResult {
    const artists = _artistsFromJson(json['artists']);
    return new MusicBoxSearchResult({
      trackId: _stringFromJson(json, ['track_id', 'id']) ?? '',
      name: _stringFromJson(json, ['name', 'title']) ?? '',
      artists,
      source: _stringFromJson(json, ['source']) ?? '',
    });
  }
}

export class PersonalMusicPlaylist {
  readonly id: string;
  readonly name: string;
  readonly description: string;
  readonly revision: number;
  readonly itemCount: number;
  readonly createdAt: Date | null;
  readonly updatedAt: Date | null;

  constructor(opts: {
    id: string;
    name: string;
    description: string;
    revision: number;
    itemCount: number;
    createdAt: Date | null;
    updatedAt: Date | null;
  }) {
    this.id = opts.id;
    this.name = opts.name;
    this.description = opts.description;
    this.revision = opts.revision;
    this.itemCount = opts.itemCount;
    this.createdAt = opts.createdAt;
    this.updatedAt = opts.updatedAt;
  }

  copyWith(opts: { itemCount?: number | null; revision?: number | null } = {}): PersonalMusicPlaylist {
    return new PersonalMusicPlaylist({
      id: this.id,
      name: this.name,
      description: this.description,
      revision: opts.revision ?? this.revision,
      itemCount: opts.itemCount ?? this.itemCount,
      createdAt: this.createdAt,
      updatedAt: this.updatedAt,
    });
  }

  static fromJson(json: JsonMap): PersonalMusicPlaylist {
    return new PersonalMusicPlaylist({
      id: _stringFromJson(json, ['id']) ?? '',
      name: _stringFromJson(json, ['name']) ?? '',
      description: _dartToString(json['description']) ?? '',
      revision: _intFromJson(json, ['revision']) ?? 0,
      itemCount: _intFromJson(json, ['item_count']) ?? 0,
      createdAt: _parseDateTime(json['created_at']),
      updatedAt: _parseDateTime(json['updated_at']),
    });
  }
}

export class PersonalMusicPlaylistMergeResult {
  readonly playlist: PersonalMusicPlaylist;
  readonly sourceItemCount: number;
  readonly uniqueItemCount: number;
  readonly duplicateCount: number;
  readonly itemCount: number;
  readonly omittedCount: number;
  readonly deletedPlaylistCount: number;
  readonly retainedPlaylistCount: number;
  readonly consumedSourceItemCount: number;
  readonly truncated: boolean;

  constructor(opts: {
    playlist: PersonalMusicPlaylist;
    sourceItemCount: number;
    uniqueItemCount: number;
    duplicateCount: number;
    itemCount: number;
    omittedCount: number;
    deletedPlaylistCount: number;
    retainedPlaylistCount: number;
    consumedSourceItemCount: number;
    truncated: boolean;
  }) {
    this.playlist = opts.playlist;
    this.sourceItemCount = opts.sourceItemCount;
    this.uniqueItemCount = opts.uniqueItemCount;
    this.duplicateCount = opts.duplicateCount;
    this.itemCount = opts.itemCount;
    this.omittedCount = opts.omittedCount;
    this.deletedPlaylistCount = opts.deletedPlaylistCount;
    this.retainedPlaylistCount = opts.retainedPlaylistCount;
    this.consumedSourceItemCount = opts.consumedSourceItemCount;
    this.truncated = opts.truncated;
  }

  static fromJson(json: JsonMap): PersonalMusicPlaylistMergeResult {
    const merge = _nullableMap(json['merge']);
    return new PersonalMusicPlaylistMergeResult({
      playlist: PersonalMusicPlaylist.fromJson(requireMap(json, 'playlist')),
      sourceItemCount: _intFromJson(merge, ['source_item_count']) ?? 0,
      uniqueItemCount: _intFromJson(merge, ['unique_item_count']) ?? 0,
      duplicateCount: _intFromJson(merge, ['duplicate_count']) ?? 0,
      itemCount: _intFromJson(merge, ['item_count']) ?? 0,
      omittedCount: _intFromJson(merge, ['omitted_count']) ?? 0,
      deletedPlaylistCount: _intFromJson(merge, ['deleted_playlist_count']) ?? 0,
      retainedPlaylistCount: _intFromJson(merge, ['retained_playlist_count']) ?? 0,
      consumedSourceItemCount: _intFromJson(merge, ['consumed_source_item_count']) ?? 0,
      truncated: _boolFromJson(merge, ['truncated']) ?? false,
    });
  }
}

export class PersonalMusicPlaylistBatchAddResult {
  readonly playlist: PersonalMusicPlaylist;
  readonly selectedItemCount: number;
  readonly uniqueItemCount: number;
  readonly duplicateCount: number;
  readonly alreadyPresentCount: number;
  readonly addedItemCount: number;
  readonly omittedCount: number;
  readonly truncated: boolean;

  constructor(opts: {
    playlist: PersonalMusicPlaylist;
    selectedItemCount: number;
    uniqueItemCount: number;
    duplicateCount: number;
    alreadyPresentCount: number;
    addedItemCount: number;
    omittedCount: number;
    truncated: boolean;
  }) {
    this.playlist = opts.playlist;
    this.selectedItemCount = opts.selectedItemCount;
    this.uniqueItemCount = opts.uniqueItemCount;
    this.duplicateCount = opts.duplicateCount;
    this.alreadyPresentCount = opts.alreadyPresentCount;
    this.addedItemCount = opts.addedItemCount;
    this.omittedCount = opts.omittedCount;
    this.truncated = opts.truncated;
  }

  static fromJson(json: JsonMap): PersonalMusicPlaylistBatchAddResult {
    const batchAdd = _nullableMap(json['batch_add']);
    return new PersonalMusicPlaylistBatchAddResult({
      playlist: PersonalMusicPlaylist.fromJson(requireMap(json, 'playlist')),
      selectedItemCount: _intFromJson(batchAdd, ['selected_item_count']) ?? 0,
      uniqueItemCount: _intFromJson(batchAdd, ['unique_item_count']) ?? 0,
      duplicateCount: _intFromJson(batchAdd, ['duplicate_count']) ?? 0,
      alreadyPresentCount: _intFromJson(batchAdd, ['already_present_count']) ?? 0,
      addedItemCount: _intFromJson(batchAdd, ['added_item_count']) ?? 0,
      omittedCount: _intFromJson(batchAdd, ['omitted_count']) ?? 0,
      truncated: _boolFromJson(batchAdd, ['truncated']) ?? false,
    });
  }
}

export class PersonalMusicPlaylistItem {
  readonly id: string;
  readonly playlistId: string;
  readonly trackId: string;
  readonly source: string;
  readonly title: string;
  readonly artists: string[];
  readonly durationMs: number;
  readonly sortOrder: number;
  readonly createdAt: Date | null;

  constructor(opts: {
    id: string;
    playlistId: string;
    trackId: string;
    source: string;
    title: string;
    artists: string[];
    durationMs: number;
    sortOrder: number;
    createdAt: Date | null;
  }) {
    this.id = opts.id;
    this.playlistId = opts.playlistId;
    this.trackId = opts.trackId;
    this.source = opts.source;
    this.title = opts.title;
    this.artists = opts.artists;
    this.durationMs = opts.durationMs;
    this.sortOrder = opts.sortOrder;
    this.createdAt = opts.createdAt;
  }

  static fromJson(json: JsonMap): PersonalMusicPlaylistItem {
    return new PersonalMusicPlaylistItem({
      id: _stringFromJson(json, ['id']) ?? '',
      playlistId: _stringFromJson(json, ['playlist_id']) ?? '',
      trackId: _stringFromJson(json, ['track_id']) ?? '',
      source: _stringFromJson(json, ['source']) ?? '',
      title: _stringFromJson(json, ['title', 'name']) ?? '',
      artists: _artistsFromJson(json['artists']),
      durationMs: _intFromJson(json, ['duration_ms']) ?? 0,
      sortOrder: _intFromJson(json, ['sort_order']) ?? 0,
      createdAt: _parseDateTime(json['created_at']),
    });
  }
}

export class PersonalMusicPlaylistPage {
  readonly playlists: PersonalMusicPlaylist[];
  readonly page: number;
  readonly pageSize: number;
  readonly total: number;
  readonly hasMore: boolean;
  readonly maxPlaylists: number;
  readonly maxPlaylistItems: number;

  constructor(opts: {
    playlists: PersonalMusicPlaylist[];
    page: number;
    pageSize: number;
    total: number;
    hasMore: boolean;
    maxPlaylists: number;
    maxPlaylistItems: number;
  }) {
    this.playlists = opts.playlists;
    this.page = opts.page;
    this.pageSize = opts.pageSize;
    this.total = opts.total;
    this.hasMore = opts.hasMore;
    this.maxPlaylists = opts.maxPlaylists;
    this.maxPlaylistItems = opts.maxPlaylistItems;
  }

  static fromJson(json: JsonMap): PersonalMusicPlaylistPage {
    const pagination = _nullableMap(json['pagination']);
    const limits = _nullableMap(json['limits']);
    return new PersonalMusicPlaylistPage({
      playlists: _listOfMaps(json['playlists']).map((playlist) => PersonalMusicPlaylist.fromJson(playlist)),
      page: _intFromJson(pagination, ['page']) ?? 1,
      pageSize: _intFromJson(pagination, ['page_size']) ?? 50,
      total: _intFromJson(pagination, ['total']) ?? 0,
      hasMore: _boolFromJson(pagination, ['has_more']) ?? false,
      maxPlaylists: _intFromJson(limits, ['max_playlists']) ?? 50,
      maxPlaylistItems: _intFromJson(limits, ['max_playlist_items']) ?? 500,
    });
  }
}

export class PersonalMusicPlaylistItemsPage {
  readonly playlist: PersonalMusicPlaylist;
  readonly items: PersonalMusicPlaylistItem[];
  readonly page: number;
  readonly pageSize: number;
  readonly total: number;
  readonly hasMore: boolean;

  constructor(opts: {
    playlist: PersonalMusicPlaylist;
    items: PersonalMusicPlaylistItem[];
    page: number;
    pageSize: number;
    total: number;
    hasMore: boolean;
  }) {
    this.playlist = opts.playlist;
    this.items = opts.items;
    this.page = opts.page;
    this.pageSize = opts.pageSize;
    this.total = opts.total;
    this.hasMore = opts.hasMore;
  }

  static fromJson(json: JsonMap): PersonalMusicPlaylistItemsPage {
    const pagination = _nullableMap(json['pagination']);
    return new PersonalMusicPlaylistItemsPage({
      playlist: PersonalMusicPlaylist.fromJson(requireMap(json, 'playlist')),
      items: _listOfMaps(json['items']).map((item) => PersonalMusicPlaylistItem.fromJson(item)),
      page: _intFromJson(pagination, ['page']) ?? 1,
      pageSize: _intFromJson(pagination, ['page_size']) ?? 50,
      total: _intFromJson(pagination, ['total']) ?? 0,
      hasMore: _boolFromJson(pagination, ['has_more']) ?? false,
    });
  }
}

// PORT: Dart's `(value as List?).cast<Map>()` throws on a non-list value or a
// non-map element; like `mapList` in lib/json this skips them instead.
function _listOfMaps(value: unknown): JsonMap[] {
  return mapList(value, (json) => json);
}

function _nullableMap(value: unknown): JsonMap | null {
  return asMap(value);
}

/** Dart `Map.containsKey` (own keys only, so `toString` etc. never match). */
function _containsKey(json: JsonMap, key: string): boolean {
  return Object.prototype.hasOwnProperty.call(json, key);
}

function _stringFromJson(json: JsonMap | null | undefined, keys: readonly string[]): string | null {
  if (json == null) return null;
  for (const key of keys) {
    const value = json[key];
    let text: string | null;
    if (typeof value === 'string') {
      text = value;
    } else if (typeof value === 'number' && Number.isInteger(value)) {
      text = value.toString();
    } else {
      text = null;
    }
    if (text != null && text.trim().length > 0) return text;
  }
  return null;
}

function _nonEmptyString(value: string | null): string | null {
  const trimmed = value?.trim();
  if (trimmed == null || trimmed.length === 0) return null;
  return trimmed;
}

function _boolFromJson(json: JsonMap | null | undefined, keys: readonly string[]): boolean | null {
  if (json == null) return null;
  for (const key of keys) {
    const value = json[key];
    if (typeof value === 'boolean') return value;
  }
  return null;
}

function _intFromJson(json: JsonMap | null | undefined, keys: readonly string[]): number | null {
  if (json == null) return null;
  for (const key of keys) {
    const value = _nullableInt(json[key]);
    if (value != null) return value;
  }
  return null;
}

function _onlineFromStatus(value: string | null): boolean | null {
  const status = value?.trim().toLowerCase();
  if (status == null || status.length === 0) return null;
  switch (status) {
    case 'online':
    case 'active':
    case 'connected':
    case 'joining':
    case 'joined':
    case '在线':
      return true;
    case 'offline':
    case 'inactive':
    case 'disconnected':
    case 'left':
    case '离线':
      return false;
    default:
      return null;
  }
}

function _parseDateTime(value: unknown): Date | null {
  return asDate(value);
}

function _nullableInt(value: unknown): number | null {
  if (typeof value === 'number') return Number.isFinite(value) ? Math.trunc(value) : null;
  if (typeof value === 'string') return _tryParseInt(value);
  return null;
}

/** Dart `int.tryParse`: optional sign, decimal or `0x` hex digits, surrounding whitespace ignored. */
function _tryParseInt(value: string): number | null {
  const text = value.trim();
  if (/^[+-]?\d+$/.test(text)) return Number.parseInt(text, 10);
  const hex = /^([+-]?)0x([0-9a-f]+)$/i.exec(text);
  if (hex != null) {
    const magnitude = Number.parseInt(hex[2]!, 16);
    return hex[1] === '-' ? -magnitude : magnitude;
  }
  return null;
}

function _fromUnixSeconds(value: number): Date {
  return new Date(value * 1000);
}

function _nullableUnixSeconds(value: unknown): Date | null {
  if (typeof value !== 'number' || !Number.isInteger(value)) return null;
  return _fromUnixSeconds(value);
}

/**
 * `(json['artists'] as List<Object?>? ?? const []).map((value) =>
 * value?.toString().trim() ?? '').where((value) => value.isNotEmpty)`.
 */
function _artistsFromJson(value: unknown): string[] {
  return (asList(value) ?? [])
    .map((artist) => _dartToString(artist)?.trim() ?? '')
    .filter((artist) => artist.length > 0);
}

/** Dart `value?.toString()` for a decoded JSON value. */
function _dartToString(value: unknown): string | null {
  if (value == null) return null;
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (Array.isArray(value)) return `[${value.map((item) => _dartToString(item) ?? 'null').join(', ')}]`;
  if (isJsonMap(value)) {
    return `{${Object.entries(value)
      .map(([key, item]) => `${key}: ${_dartToString(item) ?? 'null'}`)
      .join(', ')}}`;
  }
  return String(value);
}
