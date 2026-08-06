const defaultUserLanguage = 'zh-Hans';

class AppVersionInfo {
  const AppVersionInfo({
    required this.latestVersion,
    this.minimumSupportedVersion,
    this.releaseNotes,
    this.downloadUrl,
  });

  final String latestVersion;
  final String? minimumSupportedVersion;
  final String? releaseNotes;
  final String? downloadUrl;

  factory AppVersionInfo.fromJson(Map<String, Object?> json) {
    return AppVersionInfo(
      latestVersion:
          _stringFromJson(json, const ['latest_version', 'version']) ?? '',
      minimumSupportedVersion: _stringFromJson(json, const [
        'minimum_supported_version',
        'minimum_version',
      ]),
      releaseNotes: _stringFromJson(json, const ['release_notes', 'notes']),
      downloadUrl: _stringFromJson(json, const ['download_url']),
    );
  }
}

class UserCommonRoom {
  const UserCommonRoom({
    required this.id,
    required this.rid,
    required this.name,
    this.visibility = 'private',
    this.remarkName,
    this.avatarUrl,
    this.defaultAvatarKey = 'blue-3',
    this.roomDisplayName,
    this.roomRole,
  });

  final String id;
  final String rid;
  final String name;
  final String visibility;
  final String? remarkName;
  final String? avatarUrl;
  final String defaultAvatarKey;
  final String? roomDisplayName;
  final String? roomRole;

  factory UserCommonRoom.fromJson(Map<String, Object?> json) {
    return UserCommonRoom(
      id: _stringFromJson(json, const ['id', 'room_id']) ?? '',
      rid: _stringFromJson(json, const ['rid', 'room_rid']) ?? '',
      name: _stringFromJson(json, const ['name', 'room_name']) ?? '',
      visibility:
          _stringFromJson(json, const ['visibility', 'room_visibility']) ??
          'private',
      remarkName: _stringFromJson(json, const [
        'remark_name',
        'room_remark',
        'room_remark_name',
      ]),
      avatarUrl: _stringFromJson(json, const ['avatar_url', 'room_avatar_url']),
      defaultAvatarKey:
          _stringFromJson(json, const [
            'default_avatar_key',
            'room_default_avatar_key',
          ]) ??
          'blue-3',
      roomDisplayName: _stringFromJson(json, const [
        'room_display_name',
        'room_username',
        'room_nickname',
        'room_remark',
        'member_display_name',
      ]),
      roomRole:
          _stringFromJson(json, const ['room_role', 'membership_role']) ??
          _stringFromJson(_nullableMap(json['membership']), const ['role']),
    );
  }

  bool get isUsable => id.trim().isNotEmpty && name.trim().isNotEmpty;
}

class UserSummary {
  const UserSummary({
    required this.id,
    required this.username,
    required this.displayName,
    required this.avatarUrl,
    required this.defaultAvatarKey,
    this.uid,
    this.bio,
    this.gender,
    this.email,
    this.emailPublic,
    this.phoneNumber,
    this.phoneNumberPublic,
    this.roomDisplayName,
    this.roomRole,
    this.isSuperuser = false,
    this.isOnline,
    this.commonRooms = const [],
    this.isDeleted = false,
    this.isSuspended = false,
  });

  final String id;
  final String username;
  final String displayName;
  final String? avatarUrl;
  final String defaultAvatarKey;
  final String? uid;
  final String? bio;
  final String? gender;
  final String? email;
  final bool? emailPublic;
  final String? phoneNumber;
  final bool? phoneNumberPublic;
  final String? roomDisplayName;
  final String? roomRole;
  final bool isSuperuser;
  final bool? isOnline;
  final List<UserCommonRoom> commonRooms;
  final bool isDeleted;
  final bool isSuspended;

  factory UserSummary.fromJson(Map<String, Object?> json) {
    final username = json['username']! as String;
    return UserSummary(
      id: json['id']! as String,
      username: username,
      displayName: json['display_name'] as String? ?? username,
      avatarUrl: json['avatar_url'] as String?,
      defaultAvatarKey: json['default_avatar_key'] as String? ?? 'blue-3',
      uid: _stringFromJson(json, const ['uid', 'user_uid']),
      bio: _stringFromJson(json, const ['bio', 'signature']),
      gender: _stringFromJson(json, const ['gender']),
      email: _stringFromJson(json, const ['email']),
      emailPublic: _boolFromJson(json, const ['email_public']),
      phoneNumber: _stringFromJson(json, const ['phone_number', 'phone']),
      phoneNumberPublic: _boolFromJson(json, const [
        'phone_number_public',
        'phone_public',
      ]),
      roomDisplayName: _stringFromJson(json, const [
        'room_display_name',
        'room_username',
        'room_nickname',
        'room_remark',
        'remark_name',
        'remark',
      ]),
      roomRole:
          _stringFromJson(json, const ['room_role', 'membership_role']) ??
          _stringFromJson(_nullableMap(json['membership']), const ['role']) ??
          _stringFromJson(json, const ['role']),
      isSuperuser: json['is_superuser'] as bool? ?? false,
      isOnline:
          _boolFromJson(json, const ['is_online', 'online']) ??
          _onlineFromStatus(
            _stringFromJson(json, const [
              'presence',
              'presence_status',
              'status',
              'connection_state',
            ]),
          ),
      commonRooms: _listOfMaps(
        json['common_rooms'],
      ).map(UserCommonRoom.fromJson).where((room) => room.isUsable).toList(),
      isDeleted: json['is_deleted'] as bool? ?? false,
      isSuspended:
          json['is_suspended'] as bool? ??
          _stringFromJson(json, const ['account_status']) == 'suspended',
    );
  }

  UserSummary copyWith({
    String? displayName,
    String? avatarUrl,
    String? defaultAvatarKey,
    String? uid,
    String? bio,
    String? gender,
    String? email,
    bool? emailPublic,
    String? phoneNumber,
    bool? phoneNumberPublic,
    String? roomDisplayName,
    String? roomRole,
    bool? isSuperuser,
    bool? isOnline,
    List<UserCommonRoom>? commonRooms,
    bool? isDeleted,
    bool? isSuspended,
  }) {
    return UserSummary(
      id: id,
      username: username,
      displayName: displayName ?? this.displayName,
      avatarUrl: avatarUrl ?? this.avatarUrl,
      defaultAvatarKey: defaultAvatarKey ?? this.defaultAvatarKey,
      uid: uid ?? this.uid,
      bio: bio ?? this.bio,
      gender: gender ?? this.gender,
      email: email ?? this.email,
      emailPublic: emailPublic ?? this.emailPublic,
      phoneNumber: phoneNumber ?? this.phoneNumber,
      phoneNumberPublic: phoneNumberPublic ?? this.phoneNumberPublic,
      roomDisplayName: roomDisplayName ?? this.roomDisplayName,
      roomRole: roomRole ?? this.roomRole,
      isSuperuser: isSuperuser ?? this.isSuperuser,
      isOnline: isOnline ?? this.isOnline,
      commonRooms: commonRooms ?? this.commonRooms,
      isDeleted: isDeleted ?? this.isDeleted,
      isSuspended: isSuspended ?? this.isSuspended,
    );
  }

  UserSummary mergeMissing(UserSummary fallback) {
    if (isDeleted) return this;
    if (fallback.isDeleted) return fallback;
    String? nonEmptyOrFallback(String? value, String? fallback) {
      if (value != null && value.trim().isNotEmpty) return value;
      return fallback;
    }

    return UserSummary(
      id: id,
      username: username,
      displayName: displayName.trim().isEmpty
          ? fallback.displayName
          : displayName,
      avatarUrl: avatarUrl ?? fallback.avatarUrl,
      defaultAvatarKey: defaultAvatarKey,
      uid: nonEmptyOrFallback(uid, fallback.uid),
      bio: nonEmptyOrFallback(bio, fallback.bio),
      gender: nonEmptyOrFallback(gender, fallback.gender),
      email: nonEmptyOrFallback(email, fallback.email),
      emailPublic: emailPublic ?? fallback.emailPublic,
      phoneNumber: nonEmptyOrFallback(phoneNumber, fallback.phoneNumber),
      phoneNumberPublic: phoneNumberPublic ?? fallback.phoneNumberPublic,
      roomDisplayName: nonEmptyOrFallback(
        roomDisplayName,
        fallback.roomDisplayName,
      ),
      roomRole: nonEmptyOrFallback(roomRole, fallback.roomRole),
      isSuperuser: isSuperuser || fallback.isSuperuser,
      isOnline: isOnline ?? fallback.isOnline,
      commonRooms: commonRooms.isNotEmpty ? commonRooms : fallback.commonRooms,
      isDeleted: false,
      isSuspended: isSuspended || fallback.isSuspended,
    );
  }
}

class CurrentUser {
  const CurrentUser({
    required this.id,
    required this.uid,
    required this.username,
    required this.displayName,
    required this.bio,
    required this.gender,
    required this.email,
    this.emailVerified = false,
    required this.emailPublic,
    required this.phoneNumber,
    required this.phoneNumberPublic,
    required this.avatarUrl,
    required this.defaultAvatarKey,
    required this.isSuperuser,
    required this.createdAt,
    this.language = defaultUserLanguage,
    this.usernameUpdatedAt,
    this.canChangeUsernameAt,
    this.status,
  });

  final String id;
  final String uid;
  final String username;
  final String displayName;
  final String bio;
  final String gender;
  final String? email;
  final bool emailVerified;
  final bool emailPublic;
  final String? phoneNumber;
  final bool phoneNumberPublic;
  final String? avatarUrl;
  final String defaultAvatarKey;
  final bool isSuperuser;
  final String language;
  final DateTime? usernameUpdatedAt;
  final DateTime? canChangeUsernameAt;
  final DateTime? createdAt;
  final String? status;

  factory CurrentUser.fromJson(Map<String, Object?> json) {
    final username = json['username']! as String;
    final id = json['id']! as String;
    return CurrentUser(
      id: id,
      uid: json['uid'] as String? ?? id,
      username: username,
      displayName: json['display_name'] as String? ?? username,
      bio: json['bio'] as String? ?? '',
      gender: json['gender'] as String? ?? 'secret',
      email: json['email'] as String?,
      emailVerified: json['email_verified'] as bool? ?? false,
      emailPublic: json['email_public'] as bool? ?? false,
      phoneNumber: json['phone_number'] as String?,
      phoneNumberPublic: json['phone_number_public'] as bool? ?? false,
      avatarUrl: json['avatar_url'] as String?,
      defaultAvatarKey: json['default_avatar_key'] as String? ?? 'blue-3',
      isSuperuser: json['is_superuser'] as bool? ?? false,
      language:
          _stringFromJson(json, const ['language', 'locale']) ??
          defaultUserLanguage,
      usernameUpdatedAt: _parseDateTime(json['username_updated_at']),
      canChangeUsernameAt: _parseDateTime(json['can_change_username_at']),
      createdAt: _parseDateTime(json['created_at']),
      status: json['status'] as String?,
    );
  }

  UserSummary toSummary() {
    return UserSummary(
      id: id,
      uid: uid,
      username: username,
      displayName: displayName,
      bio: bio,
      gender: gender,
      email: email,
      emailPublic: emailPublic,
      phoneNumber: phoneNumber,
      phoneNumberPublic: phoneNumberPublic,
      avatarUrl: avatarUrl,
      defaultAvatarKey: defaultAvatarKey,
      isSuperuser: isSuperuser,
      isOnline: _onlineFromStatus(status) ?? true,
      isSuspended: status?.trim().toLowerCase() == 'suspended',
    );
  }
}

class UserSearchPage {
  const UserSearchPage({required this.users, this.nextCursor, this.totalCount});

  final List<UserSummary> users;
  final String? nextCursor;
  final int? totalCount;

  factory UserSearchPage.fromJson(Map<String, Object?> json) {
    return UserSearchPage(
      users: _listOfMaps(json['users']).map(UserSummary.fromJson).toList(),
      nextCursor: json['next_cursor'] as String?,
      totalCount: _nullableInt(json['total_count']),
    );
  }
}

class UserAudioSettings {
  const UserAudioSettings({
    required this.defaultAudioInputVolume,
    required this.defaultAudioOutputVolume,
    required this.liveMicInputVolume,
    required this.liveVoiceOutputVolume,
    required this.liveScreenShareOutputVolume,
    required this.liveMusicOutputVolume,
    this.updatedAt,
  });

  final int defaultAudioInputVolume;
  final int defaultAudioOutputVolume;
  final int liveMicInputVolume;
  final int liveVoiceOutputVolume;
  final int liveScreenShareOutputVolume;
  final int liveMusicOutputVolume;
  final DateTime? updatedAt;

  factory UserAudioSettings.fromJson(Map<String, Object?> json) {
    int volume(String key) => _nullableInt(json[key]) ?? 100;
    return UserAudioSettings(
      defaultAudioInputVolume: volume('default_audio_input_volume'),
      defaultAudioOutputVolume: volume('default_audio_output_volume'),
      liveMicInputVolume: volume('live_mic_input_volume'),
      liveVoiceOutputVolume: volume('live_voice_output_volume'),
      liveScreenShareOutputVolume: volume('live_screen_share_output_volume'),
      liveMusicOutputVolume: volume('live_music_output_volume'),
      updatedAt: _parseDateTime(json['updated_at']),
    );
  }
}

class UserSession {
  const UserSession({
    required this.id,
    required this.userAgent,
    required this.ipAddress,
    required this.location,
    required this.createdAt,
    required this.lastUsedAt,
    required this.expiresAt,
    required this.revokedAt,
    required this.isCurrent,
  });

  final String id;
  final String? userAgent;
  final String? ipAddress;
  final String location;
  final DateTime createdAt;
  final DateTime lastUsedAt;
  final DateTime expiresAt;
  final DateTime? revokedAt;
  final bool isCurrent;

  factory UserSession.fromJson(Map<String, Object?> json) {
    return UserSession(
      id: json['id']! as String,
      userAgent: json['user_agent'] as String?,
      ipAddress: json['ip_address'] as String?,
      location: json['location'] as String? ?? '未知地点',
      createdAt: _fromUnixSeconds(json['created_at']! as int),
      lastUsedAt: _fromUnixSeconds(json['last_used_at']! as int),
      expiresAt: _fromUnixSeconds(json['expires_at']! as int),
      revokedAt: _nullableUnixSeconds(json['revoked_at']),
      isCurrent: json['is_current']! as bool,
    );
  }

  bool get isActive => revokedAt == null && expiresAt.isAfter(DateTime.now());
}

class UploadedAsset {
  const UploadedAsset({
    required this.id,
    required this.url,
    required this.thumbnailUrl,
    required this.mimeType,
    this.filename,
    this.sizeBytes,
    this.width,
    this.height,
    this.createdAt,
  });

  final String id;
  final String url;
  final String? thumbnailUrl;
  final String mimeType;
  final String? filename;
  final int? sizeBytes;
  final int? width;
  final int? height;
  final DateTime? createdAt;

  factory UploadedAsset.fromJson(Map<String, Object?> json) {
    return UploadedAsset(
      id: json['id']! as String,
      url: json['url']! as String,
      thumbnailUrl: json['thumbnail_url'] as String?,
      mimeType: json['mime_type'] as String? ?? 'application/octet-stream',
      filename: json['filename'] as String?,
      sizeBytes: _nullableInt(json['size_bytes']),
      width: _nullableInt(json['width']),
      height: _nullableInt(json['height']),
      createdAt: _parseDateTime(json['created_at']),
    );
  }

  Map<String, Object?> toJson() {
    return {
      'id': id,
      'url': url,
      'thumbnail_url': thumbnailUrl,
      'mime_type': mimeType,
      if (filename != null) 'filename': filename,
      if (sizeBytes != null) 'size_bytes': sizeBytes,
      'width': width,
      'height': height,
      'created_at': createdAt?.toIso8601String(),
    };
  }
}

class StickerPack {
  const StickerPack({
    required this.id,
    required this.scope,
    required this.roomId,
    required this.name,
    required this.stickers,
    required this.sortOrder,
    required this.updatedAt,
  });

  final String id;
  final String scope;
  final String? roomId;
  final String name;
  final List<Sticker> stickers;
  final int sortOrder;
  final DateTime? updatedAt;

  factory StickerPack.fromJson(Map<String, Object?> json) {
    return StickerPack(
      id: json['id']! as String,
      scope: json['scope'] as String? ?? 'personal',
      roomId: json['room_id'] as String?,
      name: json['name'] as String? ?? 'Stickers',
      stickers: _listOfMaps(json['stickers']).map(Sticker.fromJson).toList(),
      sortOrder: _nullableInt(json['sort_order']) ?? 10,
      updatedAt: _parseDateTime(json['updated_at']),
    );
  }

  Map<String, Object?> toJson() {
    return {
      'id': id,
      'scope': scope,
      'room_id': roomId,
      'name': name,
      'stickers': stickers.map((sticker) => sticker.toJson()).toList(),
      'sort_order': sortOrder,
      'updated_at': updatedAt?.toIso8601String(),
    };
  }
}

class Sticker {
  const Sticker({
    required this.id,
    required this.name,
    required this.sortOrder,
    required this.asset,
  });

  final String id;
  final String name;
  final int sortOrder;
  final UploadedAsset asset;

  factory Sticker.fromJson(Map<String, Object?> json) {
    return Sticker(
      id: json['id']! as String,
      name: json['name'] as String? ?? 'sticker',
      sortOrder: _nullableInt(json['sort_order']) ?? 10,
      asset: UploadedAsset.fromJson(json['asset']! as Map<String, Object?>),
    );
  }

  Map<String, Object?> toJson() {
    return {
      'id': id,
      'name': name,
      'sort_order': sortOrder,
      'asset': asset.toJson(),
    };
  }
}

class MessageAttachment {
  const MessageAttachment({
    required this.type,
    this.stickerId,
    this.name,
    this.asset,
    this.durationMs,
    this.event,
    this.user,
    this.actor,
    this.target,
    this.fromRole,
    this.toRole,
    this.oldValue,
    this.newValue,
  });

  final String type;
  final String? stickerId;
  final String? name;
  final UploadedAsset? asset;
  final int? durationMs;
  final String? event;
  final UserSummary? user;
  final UserSummary? actor;
  final UserSummary? target;
  final String? fromRole;
  final String? toRole;
  final String? oldValue;
  final String? newValue;

  factory MessageAttachment.fromJson(Map<String, Object?> json) {
    final assetJson = _nullableMap(json['asset']);
    final userJson = _nullableMap(json['user']);
    final actorJson = _nullableMap(json['actor']);
    final targetJson = _nullableMap(json['target']);
    return MessageAttachment(
      type: json['type'] as String? ?? 'file',
      stickerId: json['sticker_id'] as String?,
      name: json['name'] as String?,
      asset: assetJson == null ? null : UploadedAsset.fromJson(assetJson),
      durationMs: _nullableInt(json['duration_ms']),
      event: _stringFromJson(json, const ['event', 'system_event']),
      user: userJson == null ? null : UserSummary.fromJson(userJson),
      actor: actorJson == null ? null : UserSummary.fromJson(actorJson),
      target: targetJson == null ? null : UserSummary.fromJson(targetJson),
      fromRole: _stringFromJson(json, const ['from_role', 'previous_role']),
      toRole: _stringFromJson(json, const ['to_role', 'role']),
      oldValue: _stringFromJson(json, const ['old_value', 'previous_value']),
      newValue: _stringFromJson(json, const ['new_value', 'value']),
    );
  }

  Map<String, Object?> toJson() {
    return {
      'type': type,
      if (stickerId != null) 'sticker_id': stickerId,
      if (name != null) 'name': name,
      if (asset != null) 'asset': asset!.toJson(),
      if (durationMs != null) 'duration_ms': durationMs,
      if (event != null) 'event': event,
      if (user != null) 'user': _userSummaryToJson(user!),
      if (actor != null) 'actor': _userSummaryToJson(actor!),
      if (target != null) 'target': _userSummaryToJson(target!),
      if (fromRole != null) 'from_role': fromRole,
      if (toRole != null) 'to_role': toRole,
      if (oldValue != null) 'old_value': oldValue,
      if (newValue != null) 'new_value': newValue,
    };
  }
}

Map<String, Object?> _userSummaryToJson(UserSummary user) {
  return {
    'id': user.id,
    if (user.uid != null) 'uid': user.uid,
    'username': user.username,
    'display_name': user.displayName,
    'avatar_url': user.avatarUrl,
    'default_avatar_key': user.defaultAvatarKey,
    if (user.roomDisplayName != null) 'room_display_name': user.roomDisplayName,
    if (user.roomRole != null) 'room_role': user.roomRole,
    if (user.isSuperuser) 'is_superuser': true,
    if (user.isOnline != null) 'is_online': user.isOnline,
  };
}

class LastMessagePreview {
  const LastMessagePreview({
    required this.id,
    required this.senderDisplayName,
    required this.bodyPreview,
    required this.createdAt,
    this.type = 'text',
  });

  final String id;
  final String type;
  final String senderDisplayName;
  final String bodyPreview;
  final DateTime createdAt;

  factory LastMessagePreview.fromJson(Map<String, Object?> json) {
    return LastMessagePreview(
      id: json['id']! as String,
      type: json['type'] as String? ?? 'text',
      senderDisplayName: json['sender_display_name']! as String,
      bodyPreview: json['body_preview']! as String,
      createdAt: DateTime.parse(json['created_at']! as String),
    );
  }
}

class RoomCard {
  const RoomCard({
    required this.id,
    required this.name,
    required this.avatarUrl,
    required this.defaultAvatarKey,
    required this.memberCount,
    required this.liveParticipantCount,
    required this.liveAvatarPreview,
    required this.lastMessage,
    required this.unreadCount,
    required this.updatedAt,
    this.rid = '',
    this.visibility = 'private',
    this.remarkName,
    this.description = '',
    this.notificationPolicy = 'all',
    this.isPinned = false,
    this.onlineMemberCount = 0,
    this.hasUnreadCount = false,
    this.unreadMentionCount = 0,
    this.hasUnreadMentionCount = false,
    this.hasPendingJoinRequests = false,
    this.aiVoiceAnnouncementsEnabled = false,
  });

  final String id;
  final String name;
  final String rid;
  final String visibility;
  final String? remarkName;
  final String description;
  final String notificationPolicy;
  final bool isPinned;
  final String? avatarUrl;
  final String defaultAvatarKey;
  final int memberCount;
  final int onlineMemberCount;
  final int liveParticipantCount;
  final List<UserSummary> liveAvatarPreview;
  final LastMessagePreview? lastMessage;
  final int unreadCount;
  final bool hasUnreadCount;
  final int unreadMentionCount;
  final bool hasUnreadMentionCount;
  final bool hasPendingJoinRequests;
  final bool aiVoiceAnnouncementsEnabled;
  final DateTime updatedAt;

  factory RoomCard.fromJson(Map<String, Object?> json) {
    final hasUnreadCount = json.containsKey('unread_count');
    final hasUnreadMentionCount = json.containsKey('unread_mention_count');
    final pendingJoinRequestCount = _intFromJson(json, const [
      'pending_join_request_count',
      'pending_join_requests_count',
    ]);
    final hasPendingJoinRequests =
        _boolFromJson(json, const [
          'has_pending_join_requests',
          'pending_join_requests',
        ]) ??
        (pendingJoinRequestCount != null && pendingJoinRequestCount > 0);
    return RoomCard(
      id: json['id']! as String,
      name: json['name']! as String,
      rid: json['rid'] as String? ?? '',
      visibility: json['visibility'] as String? ?? 'private',
      remarkName: _stringFromJson(json, const ['remark_name', 'room_remark']),
      description:
          _stringFromJson(json, const ['description', 'intro', 'bio']) ?? '',
      notificationPolicy:
          _stringFromJson(json, const [
            'notification_policy',
            'notification_level',
          ]) ??
          'all',
      isPinned: _boolFromJson(json, const ['is_pinned', 'pinned']) ?? false,
      avatarUrl: json['avatar_url'] as String?,
      defaultAvatarKey: json['default_avatar_key'] as String? ?? 'blue-3',
      memberCount: json['member_count']! as int,
      onlineMemberCount:
          _intFromJson(json, const ['online_member_count', 'online_count']) ??
          0,
      liveParticipantCount: json['live_participant_count'] as int? ?? 0,
      liveAvatarPreview: _listOfMaps(
        json['live_avatar_preview'],
      ).map(UserSummary.fromJson).toList(),
      lastMessage: _nullableMap(json['last_message']) == null
          ? null
          : LastMessagePreview.fromJson(_nullableMap(json['last_message'])!),
      unreadCount: json['unread_count'] as int? ?? 0,
      hasUnreadCount: hasUnreadCount,
      unreadMentionCount: json['unread_mention_count'] as int? ?? 0,
      hasUnreadMentionCount: hasUnreadMentionCount,
      hasPendingJoinRequests: hasPendingJoinRequests,
      aiVoiceAnnouncementsEnabled:
          _boolFromJson(json, const [
            'ai_voice_announcements_enabled',
            'ai_voice_announce_enabled',
          ]) ??
          false,
      updatedAt: DateTime.parse(json['updated_at']! as String),
    );
  }

  String get displayName {
    final remark = _nonEmptyString(remarkName);
    if (remark == null) return name;
    return remark;
  }

  /// Returns a copy with selected fields overridden. Used when merging a
  /// server-pushed public snapshot (room_updated) over an existing card: the
  /// snapshot carries no per-user fields, so the caller re-supplies the local
  /// [unreadCount] to keep it from being reset to 0.
  RoomCard copyWith({
    int? unreadCount,
    int? unreadMentionCount,
    bool? isPinned,
    bool? hasUnreadCount,
    bool? hasUnreadMentionCount,
    bool? hasPendingJoinRequests,
  }) {
    return RoomCard(
      id: id,
      name: name,
      rid: rid,
      visibility: visibility,
      remarkName: remarkName,
      description: description,
      notificationPolicy: notificationPolicy,
      isPinned: isPinned ?? this.isPinned,
      avatarUrl: avatarUrl,
      defaultAvatarKey: defaultAvatarKey,
      memberCount: memberCount,
      onlineMemberCount: onlineMemberCount,
      liveParticipantCount: liveParticipantCount,
      liveAvatarPreview: liveAvatarPreview,
      lastMessage: lastMessage,
      unreadCount: unreadCount ?? this.unreadCount,
      hasUnreadCount: hasUnreadCount ?? this.hasUnreadCount,
      unreadMentionCount: unreadMentionCount ?? this.unreadMentionCount,
      hasUnreadMentionCount:
          hasUnreadMentionCount ?? this.hasUnreadMentionCount,
      hasPendingJoinRequests:
          hasPendingJoinRequests ?? this.hasPendingJoinRequests,
      aiVoiceAnnouncementsEnabled: aiVoiceAnnouncementsEnabled,
      updatedAt: updatedAt,
    );
  }
}

/// A room as seen from search results — the viewer may or may not already be
/// a member. [joinState] is one of `none`, `pending`, `joined` and drives the
/// join button's affordance.
class PublicRoom {
  const PublicRoom({
    required this.id,
    required this.rid,
    required this.name,
    required this.avatarUrl,
    required this.defaultAvatarKey,
    required this.visibility,
    required this.joinPolicy,
    required this.memberCount,
    required this.liveParticipantCount,
    required this.joined,
    required this.joinState,
    this.avatarLabel,
    this.onlineMemberCount = 0,
    this.description = '',
    this.createdBy,
    this.personalProfile = const RoomPersonalProfile(),
    this.myMembership,
    this.isDeleted = false,
  });

  final String id;
  final String rid;
  final String name;
  final String? avatarLabel;
  final String? avatarUrl;
  final String defaultAvatarKey;
  final String visibility;
  final String joinPolicy;
  final String description;
  final int memberCount;
  final int onlineMemberCount;
  final int liveParticipantCount;
  final bool joined;
  final String joinState;
  final UserSummary? createdBy;
  final RoomPersonalProfile personalProfile;
  final RoomMembership? myMembership;
  final bool isDeleted;

  factory PublicRoom.fromJson(Map<String, Object?> json) {
    return PublicRoom(
      id: json['id']! as String,
      rid: json['rid'] as String? ?? '',
      name: json['name']! as String,
      avatarUrl: json['avatar_url'] as String?,
      defaultAvatarKey: json['default_avatar_key'] as String? ?? 'blue-3',
      visibility: json['visibility'] as String? ?? 'public',
      joinPolicy: json['join_policy'] as String? ?? 'approval_required',
      description:
          _stringFromJson(json, const ['description', 'intro', 'bio']) ?? '',
      memberCount: json['member_count'] as int? ?? 0,
      onlineMemberCount:
          _intFromJson(json, const ['online_member_count', 'online_count']) ??
          0,
      liveParticipantCount: json['live_participant_count'] as int? ?? 0,
      joined: json['joined'] as bool? ?? false,
      joinState: json['join_state'] as String? ?? 'none',
      createdBy: _nullableMap(json['created_by']) == null
          ? null
          : UserSummary.fromJson(_nullableMap(json['created_by'])!),
      avatarLabel: _stringFromJson(json, const ['avatar_label']),
      personalProfile: RoomPersonalProfile.fromJson(
        _nullableMap(json['personal_profile']) ??
            _nullableMap(json['my_room_profile']) ??
            _nullableMap(json['room_profile']),
      ),
      myMembership: _nullableMap(json['my_membership']) == null
          ? null
          : RoomMembership.fromJson(_nullableMap(json['my_membership'])!),
      isDeleted: json['is_deleted'] as bool? ?? false,
    );
  }

  PublicRoom copyWith({
    bool? joined,
    String? joinState,
    int? memberCount,
    int? onlineMemberCount,
    int? liveParticipantCount,
    bool? isDeleted,
  }) {
    return PublicRoom(
      id: id,
      rid: rid,
      name: name,
      avatarLabel: avatarLabel,
      avatarUrl: avatarUrl,
      defaultAvatarKey: defaultAvatarKey,
      visibility: visibility,
      joinPolicy: joinPolicy,
      description: description,
      memberCount: memberCount ?? this.memberCount,
      onlineMemberCount: onlineMemberCount ?? this.onlineMemberCount,
      liveParticipantCount: liveParticipantCount ?? this.liveParticipantCount,
      joined: joined ?? this.joined,
      joinState: joinState ?? this.joinState,
      createdBy: createdBy,
      personalProfile: personalProfile,
      myMembership: myMembership,
      isDeleted: isDeleted ?? this.isDeleted,
    );
  }
}

class SearchRoomContext {
  const SearchRoomContext({
    required this.id,
    required this.rid,
    required this.name,
    required this.avatarUrl,
    required this.defaultAvatarKey,
    this.remarkName,
  });

  final String id;
  final String rid;
  final String name;
  final String? remarkName;
  final String? avatarUrl;
  final String defaultAvatarKey;

  factory SearchRoomContext.fromJson(Map<String, Object?> json) {
    return SearchRoomContext(
      id: json['id']! as String,
      rid: json['rid'] as String? ?? '',
      name: json['name']! as String,
      remarkName: _stringFromJson(json, const ['remark_name', 'room_remark']),
      avatarUrl: json['avatar_url'] as String?,
      defaultAvatarKey: json['default_avatar_key'] as String? ?? 'blue-3',
    );
  }

  String get displayName {
    final remark = _nonEmptyString(remarkName);
    if (remark == null) return name;
    return remark;
  }
}

class MessageSearchResult {
  const MessageSearchResult({required this.room, required this.message});

  final SearchRoomContext room;
  final Message message;

  factory MessageSearchResult.fromJson(Map<String, Object?> json) {
    return MessageSearchResult(
      room: SearchRoomContext.fromJson(json['room']! as Map<String, Object?>),
      message: Message.fromJson(json['message']! as Map<String, Object?>),
    );
  }
}

class GlobalSearchCursors {
  const GlobalSearchCursors({
    this.myRooms,
    this.publicRooms,
    this.userSettings,
    this.messages,
    this.files,
  });

  final String? myRooms;
  final String? publicRooms;
  final String? userSettings;
  final String? messages;
  final String? files;

  factory GlobalSearchCursors.fromJson(Map<String, Object?>? json) {
    if (json == null) return const GlobalSearchCursors();
    return GlobalSearchCursors(
      myRooms: json['my_rooms'] as String?,
      publicRooms: json['public_rooms'] as String?,
      userSettings: json['user_settings'] as String?,
      messages: json['messages'] as String?,
      files: json['files'] as String?,
    );
  }
}

class GlobalSearchCounts {
  const GlobalSearchCounts({
    this.myRooms,
    this.publicRooms,
    this.userSettings,
    this.messages,
    this.files,
  });

  final int? myRooms;
  final int? publicRooms;
  final int? userSettings;
  final int? messages;
  final int? files;

  factory GlobalSearchCounts.fromJson(Map<String, Object?>? json) {
    if (json == null) return const GlobalSearchCounts();
    return GlobalSearchCounts(
      myRooms: _nullableInt(json['my_rooms']),
      publicRooms: _nullableInt(json['public_rooms']),
      userSettings: _nullableInt(json['user_settings']),
      messages: _nullableInt(json['messages']),
      files: _nullableInt(json['files']),
    );
  }
}

class GlobalSearchResults {
  const GlobalSearchResults({
    required this.myRooms,
    required this.publicRooms,
    this.userSettings = const [],
    required this.messages,
    required this.files,
    this.nextCursors = const GlobalSearchCursors(),
    this.totalCounts = const GlobalSearchCounts(),
  });

  final List<RoomCard> myRooms;
  final List<PublicRoom> publicRooms;
  final List<UserSummary> userSettings;
  final List<MessageSearchResult> messages;
  final List<MessageSearchResult> files;
  final GlobalSearchCursors nextCursors;
  final GlobalSearchCounts totalCounts;

  factory GlobalSearchResults.fromJson(Map<String, Object?> json) {
    return GlobalSearchResults(
      myRooms: _listOfMaps(json['my_rooms']).map(RoomCard.fromJson).toList(),
      publicRooms: _listOfMaps(
        json['public_rooms'],
      ).map(PublicRoom.fromJson).toList(),
      userSettings: _listOfMaps(
        json['user_settings'],
      ).map(UserSummary.fromJson).toList(),
      messages: _listOfMaps(
        json['messages'],
      ).map(MessageSearchResult.fromJson).toList(),
      files: _listOfMaps(
        json['files'],
      ).map(MessageSearchResult.fromJson).toList(),
      nextCursors: GlobalSearchCursors.fromJson(
        _nullableMap(json['next_cursors']),
      ),
      totalCounts: GlobalSearchCounts.fromJson(
        _nullableMap(json['total_counts']),
      ),
    );
  }
}

/// Result of POST /rooms/:id/join. Either we joined and got the full room
/// detail back, or the room needs approval and we got a pending request.
class JoinRoomResult {
  const JoinRoomResult({this.room, this.pending = false});

  final RoomDetail? room;
  final bool pending;

  bool get joined => room != null;
}

class RoomMembership {
  const RoomMembership({
    required this.joinedAt,
    required this.role,
    this.aiVoiceAnnouncementsEnabled = false,
  });

  final DateTime joinedAt;
  final String role;
  final bool aiVoiceAnnouncementsEnabled;

  factory RoomMembership.fromJson(Map<String, Object?> json) {
    return RoomMembership(
      joinedAt: DateTime.parse(json['joined_at']! as String),
      role: json['role'] as String? ?? 'member',
      aiVoiceAnnouncementsEnabled:
          _boolFromJson(json, const ['ai_voice_announcements_enabled']) ??
          false,
    );
  }
}

class RoomPersonalProfile {
  const RoomPersonalProfile({this.displayName});

  final String? displayName;

  factory RoomPersonalProfile.fromJson(Map<String, Object?>? json) {
    if (json == null) return const RoomPersonalProfile();
    return RoomPersonalProfile(
      displayName: _stringFromJson(json, const [
        'display_name',
        'room_display_name',
        'room_username',
        'room_nickname',
      ]),
    );
  }

  bool get isEmpty => _nonEmptyString(displayName) == null;
}

/// A pending request to join an approval-required room, as seen by an admin
/// reviewing the room's join queue.
class JoinRequest {
  const JoinRequest({
    required this.id,
    required this.status,
    required this.user,
    required this.createdAt,
    this.reason = '',
    this.source = 'public_search',
    this.inviters = const [],
  });

  final String id;
  final String status;
  final String reason;
  final String source;
  final List<UserSummary> inviters;
  final UserSummary user;
  final DateTime createdAt;

  factory JoinRequest.fromJson(Map<String, Object?> json) {
    final inviters = _listOfMaps(
      json['inviters'],
    ).map(UserSummary.fromJson).toList();
    return JoinRequest(
      id: json['id']! as String,
      status: json['status'] as String? ?? 'pending',
      reason: _stringFromJson(json, const ['reason', 'description']) ?? '',
      source:
          _stringFromJson(json, const ['source', 'request_source']) ??
          (inviters.isEmpty ? 'public_search' : 'invitation'),
      inviters: inviters,
      user: UserSummary.fromJson(json['user']! as Map<String, Object?>),
      createdAt: DateTime.parse(json['created_at']! as String),
    );
  }
}

class RoomMemberPage {
  const RoomMemberPage({required this.members, required this.nextCursor});

  final List<RoomMember> members;
  final String? nextCursor;

  factory RoomMemberPage.fromJson(Map<String, Object?> json) {
    return RoomMemberPage(
      members: _listOfMaps(json['members']).map(RoomMember.fromJson).toList(),
      nextCursor: _stringFromJson(json, const ['next_cursor']),
    );
  }
}

class RoomMember {
  const RoomMember({
    required this.user,
    required this.role,
    required this.joinedAt,
    this.roomDisplayName,
    this.remarkName,
    this.textMutedUntil,
    this.isOnline,
  });

  final UserSummary user;
  final String role;
  final DateTime joinedAt;
  final String? roomDisplayName;
  final String? remarkName;
  final String? textMutedUntil;
  final bool? isOnline;

  factory RoomMember.fromJson(Map<String, Object?> json) {
    final userJson = json['user']! as Map<String, Object?>;
    final baseUser = UserSummary.fromJson(userJson);
    final membership = _nullableMap(json['membership']);
    final role =
        _stringFromJson(json, const ['role', 'room_role', 'membership_role']) ??
        _stringFromJson(membership, const ['role']) ??
        baseUser.roomRole ??
        'member';
    final roomDisplayName =
        _stringFromJson(json, const [
          'room_display_name',
          'room_username',
          'room_nickname',
          'member_display_name',
        ]) ??
        _stringFromJson(membership, const [
          'room_display_name',
          'room_username',
          'room_nickname',
          'member_display_name',
        ]) ??
        baseUser.roomDisplayName;
    final remarkName =
        _stringFromJson(json, const ['remark_name', 'remark']) ??
        _stringFromJson(membership, const ['remark_name', 'remark']);
    final textMutedUntil =
        _stringFromJson(json, const ['text_muted_until']) ??
        _stringFromJson(membership, const ['text_muted_until']);
    final isOnline =
        _boolFromJson(json, const ['is_online', 'online']) ??
        _boolFromJson(_nullableMap(json['presence']), const [
          'is_online',
          'online',
        ]) ??
        _onlineFromStatus(
          _stringFromJson(json, const [
                'presence',
                'presence_status',
                'status',
                'connection_state',
              ]) ??
              _stringFromJson(_nullableMap(json['presence']), const [
                'status',
                'connection_state',
              ]),
        ) ??
        baseUser.isOnline;
    final user = baseUser.copyWith(
      roomDisplayName: roomDisplayName,
      roomRole: role,
      isOnline: isOnline,
    );
    return RoomMember(
      user: user,
      role: role,
      joinedAt:
          _parseDateTime(json['joined_at']) ??
          _parseDateTime(membership?['joined_at']) ??
          DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
      roomDisplayName: roomDisplayName,
      remarkName: remarkName,
      textMutedUntil: textMutedUntil,
      isOnline: isOnline,
    );
  }
}

class RoomMemberProfile {
  const RoomMemberProfile({
    required this.user,
    required this.role,
    required this.joinedAt,
    this.roomDisplayName,
    this.textMutedUntil,
  });

  final UserSummary user;
  final String role;
  final DateTime joinedAt;
  final String? roomDisplayName;
  final String? textMutedUntil;

  factory RoomMemberProfile.fromJson(Map<String, Object?> json) {
    final baseUser = UserSummary.fromJson(
      json['user']! as Map<String, Object?>,
    );
    final role =
        _stringFromJson(json, const ['role', 'room_role', 'membership_role']) ??
        baseUser.roomRole ??
        'member';
    final roomDisplayName = _stringFromJson(json, const [
      'room_display_name',
      'room_username',
      'room_nickname',
      'member_display_name',
    ]);
    final isOnline =
        baseUser.isOnline ??
        _boolFromJson(json, const ['is_online', 'online']) ??
        _onlineFromStatus(
          _stringFromJson(json, const [
            'presence',
            'presence_status',
            'status',
            'connection_state',
          ]),
        );
    final user = baseUser.copyWith(
      roomDisplayName: roomDisplayName,
      roomRole: role,
      isOnline: isOnline,
    );
    return RoomMemberProfile(
      user: user,
      role: role,
      joinedAt:
          _parseDateTime(json['joined_at']) ??
          DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
      roomDisplayName: roomDisplayName,
      textMutedUntil: _stringFromJson(json, const ['text_muted_until']),
    );
  }
}

class RoomInvite {
  const RoomInvite({
    required this.id,
    required this.status,
    required this.room,
    required this.inviter,
    required this.createdAt,
    this.roomExists = true,
    this.inviterExists = true,
    this.invalidReason,
    this.updatedAt,
  });

  final String id;
  final String status;
  final PublicRoom room;
  final UserSummary inviter;
  final DateTime createdAt;
  final bool roomExists;
  final bool inviterExists;
  final String? invalidReason;
  final DateTime? updatedAt;

  factory RoomInvite.fromJson(Map<String, Object?> json) {
    final roomExists = json['room_exists'] as bool? ?? true;
    final inviterExists = json['inviter_exists'] as bool? ?? true;
    return RoomInvite(
      id: json['id']! as String,
      status: json['status'] as String? ?? 'pending',
      room: PublicRoom.fromJson(
        json['room']! as Map<String, Object?>,
      ).copyWith(isDeleted: !roomExists),
      inviter: UserSummary.fromJson(
        json['inviter']! as Map<String, Object?>,
      ).copyWith(isDeleted: !inviterExists),
      createdAt:
          _parseDateTime(json['created_at']) ??
          DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
      roomExists: roomExists,
      inviterExists: inviterExists,
      invalidReason: _stringFromJson(json, const ['invalid_reason']),
      updatedAt: _parseDateTime(json['updated_at']),
    );
  }
}

class RoomBlacklistEntry {
  const RoomBlacklistEntry({
    required this.user,
    required this.createdAt,
    this.blockedBy,
  });

  final UserSummary user;
  final DateTime createdAt;
  final UserSummary? blockedBy;

  factory RoomBlacklistEntry.fromJson(Map<String, Object?> json) {
    return RoomBlacklistEntry(
      user: UserSummary.fromJson(json['user']! as Map<String, Object?>),
      createdAt:
          _parseDateTime(json['created_at']) ??
          DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
      blockedBy: _nullableMap(json['blocked_by']) == null
          ? null
          : UserSummary.fromJson(_nullableMap(json['blocked_by'])!),
    );
  }
}

class RoomApplication {
  const RoomApplication({
    required this.id,
    required this.status,
    required this.room,
    required this.createdAt,
    required this.updatedAt,
    this.reason = '',
    this.reviewedAt,
    this.reviewer,
    this.reviewerExists = true,
    this.requestNotificationDeleted = false,
    this.reviewNotificationDeleted = false,
  });

  final String id;
  final String status;
  final String reason;
  final PublicRoom room;
  final DateTime createdAt;
  final DateTime updatedAt;
  final DateTime? reviewedAt;
  final UserSummary? reviewer;
  final bool reviewerExists;
  final bool requestNotificationDeleted;
  final bool reviewNotificationDeleted;

  factory RoomApplication.fromJson(Map<String, Object?> json) {
    final reviewerExists = json['reviewer_exists'] as bool? ?? true;
    final reviewerJson = _nullableMap(json['reviewer']);
    return RoomApplication(
      id: json['id']! as String,
      status: json['status'] as String? ?? 'pending',
      reason: _stringFromJson(json, const ['reason', 'description']) ?? '',
      room: PublicRoom.fromJson(json['room']! as Map<String, Object?>),
      createdAt:
          _parseDateTime(json['created_at']) ??
          DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
      updatedAt:
          _parseDateTime(json['updated_at']) ??
          DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
      reviewedAt: _parseDateTime(json['reviewed_at']),
      reviewer: reviewerJson == null
          ? null
          : UserSummary.fromJson(
              reviewerJson,
            ).copyWith(isDeleted: !reviewerExists),
      reviewerExists: reviewerExists,
      requestNotificationDeleted:
          json['request_notification_deleted'] as bool? ?? false,
      reviewNotificationDeleted:
          json['review_notification_deleted'] as bool? ?? false,
    );
  }
}

class RoomEventNotification {
  const RoomEventNotification({
    required this.id,
    required this.type,
    required this.room,
    required this.createdAt,
    this.roomExists = true,
    this.actor,
    this.actorExists = true,
    this.fromRole,
    this.toRole,
    this.messageId,
    this.messagePreview,
    this.readAt,
  });

  final String id;
  final String type;
  final PublicRoom room;
  final DateTime createdAt;
  final bool roomExists;
  final UserSummary? actor;
  final bool actorExists;
  final String? fromRole;
  final String? toRole;
  final String? messageId;
  final String? messagePreview;
  final DateTime? readAt;

  bool get isUnread => readAt == null;

  RoomEventNotification markRead(DateTime readAt) {
    return RoomEventNotification(
      id: id,
      type: type,
      room: room,
      createdAt: createdAt,
      roomExists: roomExists,
      actor: actor,
      actorExists: actorExists,
      fromRole: fromRole,
      toRole: toRole,
      messageId: messageId,
      messagePreview: messagePreview,
      readAt: readAt,
    );
  }

  factory RoomEventNotification.fromJson(Map<String, Object?> json) {
    final actorJson = _nullableMap(json['actor']);
    final roomExists = json['room_exists'] as bool? ?? true;
    final actorExists = json['actor_exists'] as bool? ?? actorJson != null;
    final actor = actorJson == null
        ? null
        : UserSummary.fromJson(actorJson).copyWith(isDeleted: !actorExists);
    return RoomEventNotification(
      id: json['id']! as String,
      type: json['type'] as String? ?? '',
      room: PublicRoom.fromJson(
        json['room']! as Map<String, Object?>,
      ).copyWith(isDeleted: !roomExists),
      createdAt:
          _parseDateTime(json['created_at']) ??
          DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
      roomExists: roomExists,
      actor: actor,
      actorExists: actorExists,
      fromRole: _stringFromJson(json, const ['from_role']),
      toRole: _stringFromJson(json, const ['to_role']),
      messageId: _stringFromJson(json, const ['message_id']),
      messagePreview: _stringFromJson(json, const ['message_preview']),
      readAt: _parseDateTime(json['read_at']),
    );
  }
}

class RoomDetail {
  const RoomDetail({
    required this.id,
    required this.name,
    required this.avatarUrl,
    required this.defaultAvatarKey,
    required this.memberCount,
    required this.myMembership,
    required this.live,
    required this.createdAt,
    required this.updatedAt,
    this.createdBy,
    this.rid = '',
    this.visibility = 'private',
    this.description = '',
    this.joinPolicy = 'approval_required',
    this.remarkName,
    this.notificationPolicy = 'all',
    this.isPinned = false,
    this.personalProfile = const RoomPersonalProfile(),
    this.aiVoiceAnnouncementsEnabled = false,
    this.messageRecallPolicy = 'sender_only',
    this.messageRecallWindowSeconds,
    this.canDeleteRoom,
    this.onlineMemberCount = 0,
  });

  final String id;
  final String name;
  final String rid;
  final String visibility;
  final String description;
  final String joinPolicy;
  final String? remarkName;
  final String notificationPolicy;
  final bool isPinned;
  final RoomPersonalProfile personalProfile;
  final bool aiVoiceAnnouncementsEnabled;

  /// Who may recall messages and within what window. Mirrors the server's
  /// `message_recall_policy` (e.g. `sender_only`, `admins`, `disabled`) and
  /// `message_recall_window_seconds` (null = unlimited). Drives whether the
  /// recall action is offered and for how long after sending.
  final String messageRecallPolicy;
  final int? messageRecallWindowSeconds;

  final bool? canDeleteRoom;
  final String? avatarUrl;
  final String defaultAvatarKey;
  final int memberCount;
  final int onlineMemberCount;
  final UserSummary? createdBy;
  final RoomMembership myMembership;
  final LiveState live;
  final DateTime createdAt;
  final DateTime updatedAt;

  factory RoomDetail.fromJson(Map<String, Object?> json) {
    return RoomDetail(
      id: json['id']! as String,
      name: json['name']! as String,
      rid: json['rid'] as String? ?? '',
      visibility: json['visibility'] as String? ?? 'private',
      description:
          _stringFromJson(json, const ['description', 'intro', 'bio']) ?? '',
      joinPolicy: json['join_policy'] as String? ?? 'approval_required',
      remarkName: _stringFromJson(json, const ['remark_name', 'room_remark']),
      notificationPolicy:
          _stringFromJson(json, const [
            'notification_policy',
            'notification_level',
          ]) ??
          'all',
      isPinned:
          _boolFromJson(json, const ['is_pinned', 'pinned']) ??
          _boolFromJson(_nullableMap(json['my_membership']), const [
            'is_pinned',
            'pinned',
          ]) ??
          false,
      personalProfile: RoomPersonalProfile.fromJson(
        _nullableMap(json['personal_profile']) ??
            _nullableMap(json['my_room_profile']) ??
            _nullableMap(json['room_profile']),
      ),
      aiVoiceAnnouncementsEnabled:
          _boolFromJson(json, const [
            'ai_voice_announcements_enabled',
            'ai_voice_auto_broadcast',
          ]) ??
          _boolFromJson(_nullableMap(json['my_membership']), const [
            'ai_voice_announcements_enabled',
          ]) ??
          false,
      messageRecallPolicy:
          _stringFromJson(json, const ['message_recall_policy']) ??
          'sender_only',
      messageRecallWindowSeconds: _intFromJson(json, const [
        'message_recall_window_seconds',
      ]),
      canDeleteRoom: _boolFromJson(json, const [
        'can_delete_room',
        'can_delete',
      ]),
      avatarUrl: json['avatar_url'] as String?,
      defaultAvatarKey: json['default_avatar_key'] as String? ?? 'blue-3',
      memberCount: json['member_count']! as int,
      onlineMemberCount:
          _intFromJson(json, const ['online_member_count', 'online_count']) ??
          0,
      createdBy: _nullableMap(json['created_by']) == null
          ? null
          : UserSummary.fromJson(_nullableMap(json['created_by'])!),
      myMembership: RoomMembership.fromJson(
        json['my_membership']! as Map<String, Object?>,
      ),
      live: LiveState.fromJson(json['live']! as Map<String, Object?>),
      createdAt: DateTime.parse(json['created_at']! as String),
      updatedAt: DateTime.parse(json['updated_at']! as String),
    );
  }

  /// Whether the current user can administer this room (review join requests,
  /// etc.). Mirrors the server's admin check: owner, admin, or superuser.
  bool get isAdmin {
    final role = myMembership.role.toLowerCase();
    return role == 'owner' ||
        role == 'creator' ||
        role == 'admin' ||
        role == 'administrator' ||
        role == 'superuser';
  }

  bool get isCreator {
    final role = myMembership.role.toLowerCase();
    return role == 'owner' || role == 'creator';
  }

  bool get isSuperuser => myMembership.role.toLowerCase() == 'superuser';

  bool get canDelete => canDeleteRoom ?? (isCreator || isSuperuser);

  /// Returns a copy with the current user's membership role replaced. Used to
  /// apply a `room_role_changed` SSE event (promote/demote) without re-fetching
  /// the whole room detail.
  RoomDetail copyWithRole(String role) {
    return RoomDetail(
      id: id,
      name: name,
      rid: rid,
      visibility: visibility,
      description: description,
      joinPolicy: joinPolicy,
      remarkName: remarkName,
      notificationPolicy: notificationPolicy,
      isPinned: isPinned,
      personalProfile: personalProfile,
      aiVoiceAnnouncementsEnabled: aiVoiceAnnouncementsEnabled,
      messageRecallPolicy: messageRecallPolicy,
      messageRecallWindowSeconds: messageRecallWindowSeconds,
      canDeleteRoom: canDeleteRoom,
      avatarUrl: avatarUrl,
      defaultAvatarKey: defaultAvatarKey,
      memberCount: memberCount,
      onlineMemberCount: onlineMemberCount,
      createdBy: createdBy,
      myMembership: RoomMembership(
        joinedAt: myMembership.joinedAt,
        role: role,
        aiVoiceAnnouncementsEnabled: myMembership.aiVoiceAnnouncementsEnabled,
      ),
      live: live,
      createdAt: createdAt,
      updatedAt: updatedAt,
    );
  }

  RoomCard toCard() {
    return RoomCard(
      id: id,
      name: name,
      rid: rid,
      visibility: visibility,
      remarkName: remarkName,
      description: description,
      notificationPolicy: notificationPolicy,
      isPinned: isPinned,
      avatarUrl: avatarUrl,
      defaultAvatarKey: defaultAvatarKey,
      memberCount: memberCount,
      onlineMemberCount: onlineMemberCount,
      liveParticipantCount: live.participantCount,
      liveAvatarPreview: live.participants.map((p) => p.user).take(5).toList(),
      aiVoiceAnnouncementsEnabled: aiVoiceAnnouncementsEnabled,
      lastMessage: null,
      unreadCount: 0,
      updatedAt: updatedAt,
    );
  }
}

class MessageQuote {
  const MessageQuote({
    required this.messageId,
    required this.senderDisplayName,
    required this.body,
    required this.createdAt,
    this.previewAttachment,
  });

  final String messageId;
  final String senderDisplayName;
  final String body;
  final DateTime createdAt;
  final MessageAttachment? previewAttachment;

  factory MessageQuote.fromJson(Map<String, Object?> json) {
    final previewAttachmentJson = _nullableMap(json['preview_attachment']);
    return MessageQuote(
      messageId: json['message_id']! as String,
      senderDisplayName: json['sender_display_name'] as String? ?? '用户',
      body: json['body'] as String? ?? '',
      createdAt: DateTime.parse(json['created_at']! as String),
      previewAttachment: previewAttachmentJson == null
          ? null
          : MessageAttachment.fromJson(previewAttachmentJson),
    );
  }
}

class Message {
  const Message({
    required this.id,
    required this.roomId,
    required this.sender,
    required this.clientMessageId,
    this.type = 'text',
    required this.body,
    required this.createdAt,
    this.attachments = const [],
    this.mentions = const [],
    this.quote,
    this.quotes = const [],
    this.isRecalled = false,
    this.recalledAt,
    this.recalledBy,
    this.isForceDeleted = false,
    this.forceDeletedAt,
    this.forceDeletedBy,
    this.pending = false,
    this.failed = false,
  });

  final String id;
  final String roomId;
  final UserSummary sender;
  final String clientMessageId;
  final String type;
  final String body;
  final DateTime createdAt;
  final List<MessageAttachment> attachments;
  final MessageQuote? quote;
  final List<MessageQuote> quotes;

  List<MessageQuote> get effectiveQuotes {
    if (quotes.isNotEmpty) return quotes;
    final legacy = quote;
    return legacy == null ? const [] : [legacy];
  }

  /// Raw mention descriptors as sent by the server (opaque maps; shape is
  /// `{user_id, ...}`). Kept as-is so the renderer can highlight @mentions.
  final List<Map<String, Object?>> mentions;

  /// Set when the sender (or an admin within policy) recalled the message.
  /// The body/attachments are tombstoned server-side; the client shows a
  /// "message recalled" placeholder.
  final bool isRecalled;
  final DateTime? recalledAt;
  final UserSummary? recalledBy;

  /// Set when an admin force-deleted the message. Distinct from a recall:
  /// it's a moderation action, not the author retracting their own message.
  final bool isForceDeleted;
  final DateTime? forceDeletedAt;
  final UserSummary? forceDeletedBy;

  final bool pending;
  final bool failed;

  /// Whether the message content has been removed (recalled or force-deleted)
  /// and should render as a placeholder rather than its original body.
  bool get isRemoved => isRecalled || isForceDeleted;

  MessageAttachment? get stickerAttachment {
    if (type != 'sticker') return null;
    for (final attachment in attachments) {
      if (attachment.type == 'sticker' && attachment.asset != null) {
        return attachment;
      }
    }
    return null;
  }

  Iterable<MessageAttachment> get fileAttachments {
    return attachments.where((attachment) => attachment.type == 'file');
  }

  factory Message.fromJson(Map<String, Object?> json) {
    final senderCommonRooms = _listOfMaps(
      json['sender_common_rooms'],
    ).map(UserCommonRoom.fromJson).where((room) => room.isUsable).toList();
    final sender = UserSummary.fromJson(json['sender']! as Map<String, Object?>)
        .copyWith(
          roomDisplayName: _stringFromJson(json, const [
            'sender_room_display_name',
            'sender_room_username',
            'sender_room_nickname',
            'sender_room_remark',
          ]),
          roomRole: _stringFromJson(json, const [
            'sender_room_role',
            'sender_membership_role',
          ]),
          commonRooms: senderCommonRooms.isEmpty ? null : senderCommonRooms,
        );
    final recalledByJson = _nullableMap(json['recalled_by']);
    final forceDeletedByJson = _nullableMap(json['force_deleted_by']);
    final quoteJson = _nullableMap(json['quote']);
    final parsedQuotes = _listOfMaps(
      json['quotes'],
    ).map(MessageQuote.fromJson).toList(growable: false);
    final legacyQuote = quoteJson == null
        ? null
        : MessageQuote.fromJson(quoteJson);
    return Message(
      id: json['id']! as String,
      roomId: json['room_id']! as String,
      sender: sender,
      clientMessageId: json['client_message_id']! as String,
      type: json['type'] as String? ?? 'text',
      body: json['body'] as String? ?? '',
      attachments: _listOfMaps(
        json['attachments'],
      ).map(MessageAttachment.fromJson).toList(),
      mentions: _listOfMaps(json['mentions']),
      quote: legacyQuote ?? (parsedQuotes.isEmpty ? null : parsedQuotes.first),
      quotes: parsedQuotes.isNotEmpty
          ? parsedQuotes
          : legacyQuote == null
          ? const []
          : [legacyQuote],
      isRecalled: _boolFromJson(json, const ['is_recalled']) ?? false,
      recalledAt: _parseDateTime(json['recalled_at']),
      recalledBy: recalledByJson == null
          ? null
          : UserSummary.fromJson(recalledByJson),
      isForceDeleted: _boolFromJson(json, const ['is_force_deleted']) ?? false,
      forceDeletedAt: _parseDateTime(json['force_deleted_at']),
      forceDeletedBy: forceDeletedByJson == null
          ? null
          : UserSummary.fromJson(forceDeletedByJson),
      createdAt: DateTime.parse(json['created_at']! as String),
    );
  }

  factory Message.local({
    required String roomId,
    required UserSummary sender,
    required String clientMessageId,
    required String body,
    String type = 'text',
    List<MessageAttachment> attachments = const [],
    List<Map<String, Object?>> mentions = const [],
    MessageQuote? quote,
    List<MessageQuote> quotes = const [],
  }) {
    return Message(
      id: clientMessageId,
      roomId: roomId,
      sender: sender,
      clientMessageId: clientMessageId,
      type: type,
      body: body,
      createdAt: DateTime.now().toUtc(),
      attachments: attachments,
      mentions: mentions,
      quote: quote,
      quotes: quotes,
      pending: true,
    );
  }

  Message markFailed() {
    return Message(
      id: id,
      roomId: roomId,
      sender: sender,
      clientMessageId: clientMessageId,
      type: type,
      body: body,
      createdAt: createdAt,
      attachments: attachments,
      mentions: mentions,
      quote: quote,
      quotes: quotes,
      isRecalled: isRecalled,
      recalledAt: recalledAt,
      recalledBy: recalledBy,
      isForceDeleted: isForceDeleted,
      forceDeletedAt: forceDeletedAt,
      forceDeletedBy: forceDeletedBy,
      failed: true,
    );
  }
}

class LiveParticipant {
  const LiveParticipant({
    required this.liveSessionId,
    required this.user,
    required this.joinedAt,
    required this.micMuted,
    this.micBlocked = false,
    required this.headphonesMuted,
    this.headphonesBlocked = false,
    this.headphonesListening = true,
    required this.voiceBlocked,
    required this.cameraOn,
    this.cameraMirrored = false,
    required this.screenSharing,
    this.screenViewers = const <UserSummary>[],
    required this.connectionState,
  });

  final String liveSessionId;
  final UserSummary user;
  final DateTime joinedAt;
  final bool micMuted;
  final bool micBlocked;
  final bool headphonesMuted;
  final bool headphonesBlocked;

  /// Whether the participant is actively listening (headphones engaged).
  /// Inverse-ish of [headphonesMuted] but sent explicitly by the server;
  /// defaults to true for servers that omit it.
  final bool headphonesListening;

  /// A persistent room-level microphone mute set by an admin (`mute_mic`). While
  /// true the participant's LiveKit publish permission is revoked: the mic is
  /// force-muted and self-unmute requests are rejected by the server. Survives
  /// reconnects until an admin runs `restore_voice`. Defaults to false for
  /// servers that don't send the field.
  final bool voiceBlocked;
  final bool cameraOn;
  final bool cameraMirrored;
  final bool screenSharing;
  final List<UserSummary> screenViewers;

  final String connectionState;

  LiveParticipant copyWith({
    String? liveSessionId,
    UserSummary? user,
    DateTime? joinedAt,
    bool? micMuted,
    bool? micBlocked,
    bool? headphonesMuted,
    bool? headphonesBlocked,
    bool? headphonesListening,
    bool? voiceBlocked,
    bool? cameraOn,
    bool? cameraMirrored,
    bool? screenSharing,
    List<UserSummary>? screenViewers,
    String? connectionState,
  }) {
    return LiveParticipant(
      liveSessionId: liveSessionId ?? this.liveSessionId,
      user: user ?? this.user,
      joinedAt: joinedAt ?? this.joinedAt,
      micMuted: micMuted ?? this.micMuted,
      micBlocked: micBlocked ?? this.micBlocked,
      headphonesMuted: headphonesMuted ?? this.headphonesMuted,
      headphonesBlocked: headphonesBlocked ?? this.headphonesBlocked,
      headphonesListening: headphonesListening ?? this.headphonesListening,
      voiceBlocked: voiceBlocked ?? this.voiceBlocked,
      cameraOn: cameraOn ?? this.cameraOn,
      cameraMirrored: cameraMirrored ?? this.cameraMirrored,
      screenSharing: screenSharing ?? this.screenSharing,
      screenViewers: screenViewers ?? this.screenViewers,
      connectionState: connectionState ?? this.connectionState,
    );
  }

  factory LiveParticipant.fromJson(Map<String, Object?> json) {
    final commonRooms = _listOfMaps(
      json['common_rooms'],
    ).map(UserCommonRoom.fromJson).where((room) => room.isUsable).toList();
    final user = UserSummary.fromJson(json['user']! as Map<String, Object?>)
        .copyWith(
          roomDisplayName: _stringFromJson(json, const [
            'room_display_name',
            'room_username',
            'room_nickname',
            'room_remark',
          ]),
          roomRole: _stringFromJson(json, const [
            'room_role',
            'membership_role',
          ]),
          commonRooms: commonRooms.isEmpty ? null : commonRooms,
        );
    return LiveParticipant(
      liveSessionId: json['live_session_id']! as String,
      user: user,
      joinedAt: DateTime.parse(json['joined_at']! as String),
      micMuted: json['mic_muted']! as bool,
      micBlocked: json['mic_blocked'] as bool? ?? false,
      headphonesMuted: json['headphones_muted'] as bool? ?? false,
      headphonesBlocked: json['headphones_blocked'] as bool? ?? false,
      headphonesListening: json['headphones_listening'] as bool? ?? true,
      voiceBlocked: json['voice_blocked'] as bool? ?? false,
      cameraOn: json['camera_on']! as bool,
      cameraMirrored: json['camera_mirrored'] as bool? ?? false,
      screenSharing: json['screen_sharing']! as bool,
      screenViewers: _listOfMaps(
        json['screen_viewers'],
      ).map(UserSummary.fromJson).toList(growable: false),
      connectionState: json['connection_state']! as String,
    );
  }
}

/// A per-listener volume override for one target speaker in a live room. Each
/// listener tunes how loudly they hear each other participant; the server keys
/// these by (room, listener, target) and only ever returns the caller's own
/// overrides.
class LiveMemberVolume {
  const LiveMemberVolume({
    required this.roomId,
    required this.targetUser,
    required this.volume,
    required this.updatedAt,
  });

  final String roomId;
  final UserSummary targetUser;

  /// 0–100, where 100 is the unattenuated default.
  final int volume;
  final DateTime updatedAt;

  factory LiveMemberVolume.fromJson(Map<String, Object?> json) {
    return LiveMemberVolume(
      roomId: json['room_id']! as String,
      targetUser: UserSummary.fromJson(
        json['target_user']! as Map<String, Object?>,
      ),
      volume: json['volume']! as int,
      updatedAt: DateTime.parse(json['updated_at']! as String),
    );
  }
}

/// A pending request to recall a message, created when a room's recall policy
/// is `admin_approval` and a non-admin asks to recall their own message. Admins
/// list and approve/reject these.
class MessageRecallRequest {
  const MessageRecallRequest({
    required this.id,
    required this.roomId,
    required this.messageId,
    required this.status,
    required this.createdAt,
    this.requestedByUserId,
  });

  final String id;
  final String roomId;
  final String messageId;
  final String status;
  final DateTime createdAt;
  final String? requestedByUserId;

  factory MessageRecallRequest.fromJson(Map<String, Object?> json) {
    return MessageRecallRequest(
      id: json['id']! as String,
      roomId: json['room_id']! as String,
      messageId: json['message_id']! as String,
      status: json['status'] as String? ?? 'pending',
      requestedByUserId: json['requested_by_user_id'] as String?,
      createdAt: DateTime.parse(json['created_at']! as String),
    );
  }
}

/// The outcome of a recall request. The server either recalls the message
/// immediately (sender within policy/window, or an admin) returning the updated
/// [message], or — under `admin_approval` for a non-admin — files a pending
/// [recallRequest] for an admin to review.
class MessageRecallResult {
  const MessageRecallResult({this.message, this.recallRequest});

  /// Set when the recall took effect immediately.
  final Message? message;

  /// Set when the recall was queued for admin approval instead.
  final MessageRecallRequest? recallRequest;

  bool get isPending => recallRequest != null && message == null;

  factory MessageRecallResult.fromJson(Map<String, Object?> json) {
    final messageJson = _nullableMap(json['message']);
    final requestJson = _nullableMap(json['recall_request']);
    return MessageRecallResult(
      message: messageJson == null ? null : Message.fromJson(messageJson),
      recallRequest: requestJson == null
          ? null
          : MessageRecallRequest.fromJson(requestJson),
    );
  }
}

class LiveState {
  const LiveState({
    required this.roomId,
    required this.participantCount,
    required this.participants,
    required this.updatedAt,
  });

  final String roomId;
  final int participantCount;
  final List<LiveParticipant> participants;
  final DateTime updatedAt;

  factory LiveState.fromJson(Map<String, Object?> json) {
    return LiveState(
      roomId: json['room_id']! as String,
      participantCount: json['participant_count']! as int,
      participants: _listOfMaps(
        json['participants'],
      ).map(LiveParticipant.fromJson).toList(),
      updatedAt: DateTime.parse(json['updated_at']! as String),
    );
  }
}

class LiveKitConnectionInfo {
  const LiveKitConnectionInfo({
    required this.serverUrl,
    required this.token,
    required this.tokenExpiresAt,
    required this.roomName,
  });

  final String serverUrl;
  final String token;
  final DateTime tokenExpiresAt;
  final String roomName;

  factory LiveKitConnectionInfo.fromJson(Map<String, Object?> json) {
    return LiveKitConnectionInfo(
      serverUrl: json['server_url']! as String,
      token: json['token']! as String,
      tokenExpiresAt: DateTime.parse(json['token_expires_at']! as String),
      roomName: json['room_name']! as String,
    );
  }
}

/// A publish-only LiveKit token for the hidden screen-audio aux participant
/// (`<ownerId>--screen-audio`). The aux participant publishes the screen-share
/// audio track through an isolated WebRTC factory; it is hidden from the
/// receiver UI and never appears in the roster.
class ScreenAudioToken {
  const ScreenAudioToken({
    required this.serverUrl,
    required this.token,
    required this.tokenExpiresAt,
    required this.roomName,
    required this.identity,
  });

  final String serverUrl;
  final String token;
  final DateTime tokenExpiresAt;
  final String roomName;
  final String identity;

  factory ScreenAudioToken.fromJson(Map<String, Object?> json) {
    return ScreenAudioToken(
      serverUrl: json['server_url']! as String,
      token: json['token']! as String,
      tokenExpiresAt: DateTime.parse(json['token_expires_at']! as String),
      roomName: json['room_name']! as String,
      identity: json['identity']! as String,
    );
  }
}

class LiveJoinResult {
  const LiveJoinResult({
    required this.liveKit,
    required this.participant,
    required this.live,
  });

  final LiveKitConnectionInfo liveKit;
  final LiveParticipant participant;
  final LiveState live;

  factory LiveJoinResult.fromJson(Map<String, Object?> json) {
    return LiveJoinResult(
      liveKit: LiveKitConnectionInfo.fromJson(
        json['livekit']! as Map<String, Object?>,
      ),
      participant: LiveParticipant.fromJson(
        json['participant']! as Map<String, Object?>,
      ),
      live: LiveState.fromJson(json['live']! as Map<String, Object?>),
    );
  }
}

/// The fixed LiveKit identity of the server-side music box bot. It joins the
/// room's voice session and publishes a single audio track; filter it out of
/// participant lists so it isn't rendered as a real member.
const musicBoxBotIdentity = '__musicbox__';

enum MusicBoxPlaybackState { stopped, playing, paused }

enum MusicBoxPlaybackMode { sequential, repeatOne, repeatAll, shuffle }

MusicBoxPlaybackMode _musicBoxPlaybackModeFrom(String? value) {
  return switch (value?.trim().toLowerCase()) {
    'repeat_one' => MusicBoxPlaybackMode.repeatOne,
    'repeat_all' => MusicBoxPlaybackMode.repeatAll,
    'shuffle' => MusicBoxPlaybackMode.shuffle,
    _ => MusicBoxPlaybackMode.sequential,
  };
}

String musicBoxPlaybackModeValue(MusicBoxPlaybackMode mode) {
  return switch (mode) {
    MusicBoxPlaybackMode.sequential => 'sequential',
    MusicBoxPlaybackMode.repeatOne => 'repeat_one',
    MusicBoxPlaybackMode.repeatAll => 'repeat_all',
    MusicBoxPlaybackMode.shuffle => 'shuffle',
  };
}

enum MusicBoxActiveSourceType { temporary, roomPlaylist, userPlaylist }

MusicBoxActiveSourceType _musicBoxActiveSourceTypeFrom(String? value) {
  return switch (value?.trim().toLowerCase()) {
    'room_playlist' => MusicBoxActiveSourceType.roomPlaylist,
    'user_playlist' => MusicBoxActiveSourceType.userPlaylist,
    _ => MusicBoxActiveSourceType.temporary,
  };
}

String musicBoxActiveSourceTypeValue(MusicBoxActiveSourceType type) {
  return switch (type) {
    MusicBoxActiveSourceType.temporary => 'temporary',
    MusicBoxActiveSourceType.roomPlaylist => 'room_playlist',
    MusicBoxActiveSourceType.userPlaylist => 'user_playlist',
  };
}

class MusicBoxCapabilities {
  const MusicBoxCapabilities({
    this.canControl = true,
    this.canChangeMode = true,
    this.allowedModes = const <MusicBoxPlaybackMode>[
      MusicBoxPlaybackMode.sequential,
      MusicBoxPlaybackMode.repeatOne,
      MusicBoxPlaybackMode.repeatAll,
      MusicBoxPlaybackMode.shuffle,
    ],
  });

  final bool canControl;
  final bool canChangeMode;
  final List<MusicBoxPlaybackMode> allowedModes;

  factory MusicBoxCapabilities.fromJson(Map<String, Object?>? json) {
    final values = (json?['allowed_modes'] as List<Object?>? ?? const [])
        .map((value) => _musicBoxPlaybackModeFrom(value?.toString()))
        .toSet()
        .toList();
    return MusicBoxCapabilities(
      canControl: _boolFromJson(json, const ['can_control']) ?? true,
      canChangeMode: _boolFromJson(json, const ['can_change_mode']) ?? true,
      allowedModes: values.isEmpty
          ? const <MusicBoxPlaybackMode>[
              MusicBoxPlaybackMode.sequential,
              MusicBoxPlaybackMode.repeatOne,
              MusicBoxPlaybackMode.repeatAll,
              MusicBoxPlaybackMode.shuffle,
            ]
          : values,
    );
  }
}

class MusicBoxActiveSource {
  const MusicBoxActiveSource({
    this.type = MusicBoxActiveSourceType.temporary,
    this.id = '',
    this.name = '点歌队列',
    this.ownerUserId = '',
    this.owner,
  });

  final MusicBoxActiveSourceType type;
  final String id;
  final String name;
  final String ownerUserId;
  final MusicBoxRequester? owner;

  factory MusicBoxActiveSource.fromJson(Map<String, Object?>? json) {
    final type = _musicBoxActiveSourceTypeFrom(json?['type']?.toString());
    final parsedName = _stringFromJson(json, const ['name']);
    return MusicBoxActiveSource(
      type: type,
      id: _stringFromJson(json, const ['id', 'playlist_id']) ?? '',
      name: type == MusicBoxActiveSourceType.temporary
          ? '点歌队列'
          : parsedName ?? '未命名歌单',
      ownerUserId: _stringFromJson(json, const ['owner_user_id']) ?? '',
      owner: MusicBoxRequester.fromJson(_nullableMap(json?['owner'])),
    );
  }
}

MusicBoxPlaybackState _musicBoxPlaybackStateFrom(String? value) {
  return switch (value?.trim().toLowerCase()) {
    'playing' => MusicBoxPlaybackState.playing,
    'paused' => MusicBoxPlaybackState.paused,
    _ => MusicBoxPlaybackState.stopped,
  };
}

enum MusicBoxQueueItemStatus { pending, downloading, ready, failed }

MusicBoxQueueItemStatus _musicBoxQueueItemStatusFrom(String? value) {
  return switch (value?.trim().toLowerCase()) {
    'downloading' => MusicBoxQueueItemStatus.downloading,
    'ready' => MusicBoxQueueItemStatus.ready,
    'failed' => MusicBoxQueueItemStatus.failed,
    _ => MusicBoxQueueItemStatus.pending,
  };
}

/// The room music box's current playback head. [positionMs] comes from the
/// server-authoritative snapshot stream; clients render it as reported instead
/// of advancing a separate local clock. See `music_box_display.dart`.
class MusicBoxPlayback {
  const MusicBoxPlayback({
    required this.state,
    required this.currentItemId,
    required this.positionMs,
    required this.volume,
    required this.updatedAt,
    this.mode = MusicBoxPlaybackMode.sequential,
    this.canPrevious = false,
    this.canNext = false,
    this.capabilities = const MusicBoxCapabilities(),
  });

  final MusicBoxPlaybackState state;

  /// The `id` of the playing queue item, matching [MusicBoxQueueItem.id].
  /// Empty when nothing is current.
  final String currentItemId;
  final int positionMs;
  final int volume;
  final DateTime? updatedAt;
  final MusicBoxPlaybackMode mode;
  final bool canPrevious;
  final bool canNext;
  final MusicBoxCapabilities capabilities;

  bool get hasCurrent => currentItemId.isNotEmpty;

  factory MusicBoxPlayback.fromJson(Map<String, Object?> json) {
    return MusicBoxPlayback(
      state: _musicBoxPlaybackStateFrom(json['state'] as String?),
      currentItemId: _stringFromJson(json, const ['current_item_id']) ?? '',
      positionMs: _intFromJson(json, const ['position_ms']) ?? 0,
      volume: _intFromJson(json, const ['volume']) ?? 100,
      updatedAt: _parseDateTime(json['updated_at']),
      mode: _musicBoxPlaybackModeFrom(json['mode']?.toString()),
      canPrevious: _boolFromJson(json, const ['can_previous']) ?? false,
      canNext: _boolFromJson(json, const ['can_next']) ?? false,
      capabilities: MusicBoxCapabilities.fromJson(
        _nullableMap(json['capabilities']),
      ),
    );
  }

  MusicBoxPlayback copyWith({bool? canPrevious, bool? canNext}) {
    return MusicBoxPlayback(
      state: state,
      currentItemId: currentItemId,
      positionMs: positionMs,
      volume: volume,
      updatedAt: updatedAt,
      mode: mode,
      canPrevious: canPrevious ?? this.canPrevious,
      canNext: canNext ?? this.canNext,
      capabilities: capabilities,
    );
  }
}

/// One track in the room music box queue. [status] tracks the server-side
/// download/transcode lifecycle; the track is only playable once it is
/// [MusicBoxQueueItemStatus.ready].
class MusicBoxQueueItem {
  const MusicBoxQueueItem({
    required this.id,
    required this.source,
    required this.trackId,
    required this.title,
    required this.artist,
    required this.durationMs,
    required this.status,
    required this.fileSizeBytes,
    required this.error,
    required this.addedByUserId,
    required this.createdAt,
    this.canPlayNow = false,
    this.requestedBy,
  });

  final String id;
  final String source;
  final String trackId;
  final String title;
  final String artist;
  final int durationMs;
  final MusicBoxQueueItemStatus status;
  final int fileSizeBytes;

  /// Failure reason, populated only when [status] is
  /// [MusicBoxQueueItemStatus.failed]. Empty otherwise.
  final String error;
  final String addedByUserId;
  final DateTime? createdAt;
  final bool canPlayNow;
  final MusicBoxRequester? requestedBy;

  factory MusicBoxQueueItem.fromJson(Map<String, Object?> json) {
    return MusicBoxQueueItem(
      id: _stringFromJson(json, const ['id', 'item_id']) ?? '',
      source: _stringFromJson(json, const ['source']) ?? '',
      trackId: _stringFromJson(json, const ['track_id']) ?? '',
      title: _stringFromJson(json, const ['title', 'name']) ?? '',
      artist: _stringFromJson(json, const ['artist']) ?? '',
      durationMs: _intFromJson(json, const ['duration_ms']) ?? 0,
      status: _musicBoxQueueItemStatusFrom(json['status'] as String?),
      fileSizeBytes: _intFromJson(json, const ['file_size_bytes']) ?? 0,
      error: _stringFromJson(json, const ['error']) ?? '',
      addedByUserId: _stringFromJson(json, const ['added_by_user_id']) ?? '',
      createdAt: _parseDateTime(json['created_at']),
      canPlayNow: json['can_play_now'] == true,
      requestedBy: MusicBoxRequester.fromJson(
        _nullableMap(json['requested_by']),
      ),
    );
  }
}

class MusicBoxRequester {
  const MusicBoxRequester({
    required this.userId,
    required this.displayName,
    String? username,
    String? avatarLabel,
    required this.avatarUrl,
    required this.defaultAvatarKey,
  }) : username = username ?? avatarLabel ?? displayName,
       avatarLabel = avatarLabel ?? displayName;

  final String userId;
  final String username;
  final String displayName;
  final String avatarLabel;
  final String? avatarUrl;
  final String defaultAvatarKey;

  static MusicBoxRequester? fromJson(Map<String, Object?>? json) {
    if (json == null) return null;
    final userId = _stringFromJson(json, const ['user_id', 'id']) ?? '';
    if (userId.isEmpty) return null;
    return MusicBoxRequester(
      userId: userId,
      username: _stringFromJson(json, const ['username']),
      displayName:
          _stringFromJson(json, const ['display_name', 'username']) ?? userId,
      avatarLabel:
          _stringFromJson(json, const ['avatar_label', 'username']) ?? userId,
      avatarUrl: _stringFromJson(json, const ['avatar_url']),
      defaultAvatarKey:
          _stringFromJson(json, const ['default_avatar_key']) ?? 'blue-3',
    );
  }
}

/// The room music box's disk usage against its per-room limit. Useful for
/// warning the user as the room approaches its storage cap; over the limit,
/// new songs queue as `pending` rather than being rejected.
class MusicBoxUsage {
  const MusicBoxUsage({required this.usedBytes, required this.limitBytes});

  final int usedBytes;
  final int limitBytes;

  factory MusicBoxUsage.fromJson(Map<String, Object?>? json) {
    return MusicBoxUsage(
      usedBytes: _intFromJson(json, const ['used_bytes']) ?? 0,
      limitBytes: _intFromJson(json, const ['limit_bytes']) ?? 0,
    );
  }
}

/// The authoritative music box snapshot. Every write (queue/control/remove) and
/// the `music_box_changed` SSE event return this same shape; clients overwrite
/// local state wholesale rather than merging field by field.
class MusicBoxState {
  const MusicBoxState({
    required this.enabled,
    required this.playback,
    required this.queue,
    required this.usage,
    this.revision = 0,
    this.hasRevision = false,
    this.activeSource = const MusicBoxActiveSource(),
    this.temporaryQueuedCount = 0,
    this.temporaryQueue = const <MusicBoxQueueItem>[],
  });

  final bool enabled;
  final MusicBoxPlayback playback;
  final List<MusicBoxQueueItem> queue;
  final MusicBoxUsage usage;
  final int revision;
  final bool hasRevision;
  final MusicBoxActiveSource activeSource;
  final int temporaryQueuedCount;
  final List<MusicBoxQueueItem> temporaryQueue;

  /// The queue item currently playing, or null when nothing is current.
  MusicBoxQueueItem? get currentItem {
    if (!playback.hasCurrent) return null;
    for (final item in queue) {
      if (item.id == playback.currentItemId) return item;
    }
    return null;
  }

  factory MusicBoxState.fromJson(Map<String, Object?> json) {
    final playbackJson = _nullableMap(json['playback']);
    final queue = _listOfMaps(
      json['queue'],
    ).map(MusicBoxQueueItem.fromJson).toList();
    final activeSource = MusicBoxActiveSource.fromJson(
      _nullableMap(json['active_source']),
    );
    final parsedTemporaryQueue = _listOfMaps(
      json['temporary_queue'],
    ).map(MusicBoxQueueItem.fromJson).toList();
    final temporaryQueue = json.containsKey('temporary_queue')
        ? parsedTemporaryQueue
        : activeSource.type == MusicBoxActiveSourceType.temporary
        ? queue
        : const <MusicBoxQueueItem>[];
    var playback = playbackJson == null
        ? const MusicBoxPlayback(
            state: MusicBoxPlaybackState.stopped,
            currentItemId: '',
            positionMs: 0,
            volume: 100,
            updatedAt: null,
          )
        : MusicBoxPlayback.fromJson(playbackJson);
    if (playbackJson != null &&
        (!playbackJson.containsKey('can_previous') ||
            !playbackJson.containsKey('can_next'))) {
      playback = playback.copyWith(
        canPrevious: playbackJson.containsKey('can_previous')
            ? null
            : playback.hasCurrent && queue.isNotEmpty,
        canNext: playbackJson.containsKey('can_next') ? null : queue.isNotEmpty,
      );
    }
    return MusicBoxState(
      enabled: _boolFromJson(json, const ['enabled']) ?? false,
      playback: playback,
      queue: queue,
      usage: MusicBoxUsage.fromJson(_nullableMap(json['usage'])),
      revision: _intFromJson(json, const ['revision']) ?? 0,
      hasRevision: json.containsKey('revision'),
      activeSource: activeSource,
      temporaryQueuedCount:
          _intFromJson(_nullableMap(json['temporary_playlist']), const [
            'queued_count',
          ]) ??
          temporaryQueue.length,
      temporaryQueue: temporaryQueue,
    );
  }
}

/// A single hit from the music box search endpoint. [artists] is an array
/// because a track may credit multiple performers; join it for display and map
/// it onto the queue request's single `artist` string when adding.
class MusicBoxSearchResult {
  const MusicBoxSearchResult({
    required this.trackId,
    required this.name,
    required this.artists,
    required this.source,
  });

  final String trackId;
  final String name;
  final List<String> artists;
  final String source;

  factory MusicBoxSearchResult.fromJson(Map<String, Object?> json) {
    final artists = (json['artists'] as List<Object?>? ?? const [])
        .map((value) => value?.toString().trim() ?? '')
        .where((value) => value.isNotEmpty)
        .toList();
    return MusicBoxSearchResult(
      trackId: _stringFromJson(json, const ['track_id', 'id']) ?? '',
      name: _stringFromJson(json, const ['name', 'title']) ?? '',
      artists: artists,
      source: _stringFromJson(json, const ['source']) ?? '',
    );
  }
}

class PersonalMusicPlaylist {
  const PersonalMusicPlaylist({
    required this.id,
    required this.name,
    required this.description,
    required this.revision,
    required this.itemCount,
    required this.createdAt,
    required this.updatedAt,
  });

  final String id;
  final String name;
  final String description;
  final int revision;
  final int itemCount;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  PersonalMusicPlaylist copyWith({int? itemCount, int? revision}) {
    return PersonalMusicPlaylist(
      id: id,
      name: name,
      description: description,
      revision: revision ?? this.revision,
      itemCount: itemCount ?? this.itemCount,
      createdAt: createdAt,
      updatedAt: updatedAt,
    );
  }

  factory PersonalMusicPlaylist.fromJson(Map<String, Object?> json) {
    return PersonalMusicPlaylist(
      id: _stringFromJson(json, const ['id']) ?? '',
      name: _stringFromJson(json, const ['name']) ?? '',
      description: json['description']?.toString() ?? '',
      revision: _intFromJson(json, const ['revision']) ?? 0,
      itemCount: _intFromJson(json, const ['item_count']) ?? 0,
      createdAt: _parseDateTime(json['created_at']),
      updatedAt: _parseDateTime(json['updated_at']),
    );
  }
}

class PersonalMusicPlaylistItem {
  const PersonalMusicPlaylistItem({
    required this.id,
    required this.playlistId,
    required this.trackId,
    required this.source,
    required this.title,
    required this.artists,
    required this.durationMs,
    required this.sortOrder,
    required this.createdAt,
  });

  final String id;
  final String playlistId;
  final String trackId;
  final String source;
  final String title;
  final List<String> artists;
  final int durationMs;
  final int sortOrder;
  final DateTime? createdAt;

  factory PersonalMusicPlaylistItem.fromJson(Map<String, Object?> json) {
    return PersonalMusicPlaylistItem(
      id: _stringFromJson(json, const ['id']) ?? '',
      playlistId: _stringFromJson(json, const ['playlist_id']) ?? '',
      trackId: _stringFromJson(json, const ['track_id']) ?? '',
      source: _stringFromJson(json, const ['source']) ?? '',
      title: _stringFromJson(json, const ['title', 'name']) ?? '',
      artists: (json['artists'] as List<Object?>? ?? const [])
          .map((value) => value?.toString().trim() ?? '')
          .where((value) => value.isNotEmpty)
          .toList(),
      durationMs: _intFromJson(json, const ['duration_ms']) ?? 0,
      sortOrder: _intFromJson(json, const ['sort_order']) ?? 0,
      createdAt: _parseDateTime(json['created_at']),
    );
  }
}

class PersonalMusicPlaylistPage {
  const PersonalMusicPlaylistPage({
    required this.playlists,
    required this.page,
    required this.pageSize,
    required this.total,
    required this.hasMore,
    required this.maxPlaylists,
    required this.maxPlaylistItems,
  });

  final List<PersonalMusicPlaylist> playlists;
  final int page;
  final int pageSize;
  final int total;
  final bool hasMore;
  final int maxPlaylists;
  final int maxPlaylistItems;

  factory PersonalMusicPlaylistPage.fromJson(Map<String, Object?> json) {
    final pagination = _nullableMap(json['pagination']);
    final limits = _nullableMap(json['limits']);
    return PersonalMusicPlaylistPage(
      playlists: _listOfMaps(
        json['playlists'],
      ).map(PersonalMusicPlaylist.fromJson).toList(),
      page: _intFromJson(pagination, const ['page']) ?? 1,
      pageSize: _intFromJson(pagination, const ['page_size']) ?? 50,
      total: _intFromJson(pagination, const ['total']) ?? 0,
      hasMore: _boolFromJson(pagination, const ['has_more']) ?? false,
      maxPlaylists: _intFromJson(limits, const ['max_playlists']) ?? 50,
      maxPlaylistItems:
          _intFromJson(limits, const ['max_playlist_items']) ?? 500,
    );
  }
}

class PersonalMusicPlaylistItemsPage {
  const PersonalMusicPlaylistItemsPage({
    required this.playlist,
    required this.items,
    required this.page,
    required this.pageSize,
    required this.total,
    required this.hasMore,
  });

  final PersonalMusicPlaylist playlist;
  final List<PersonalMusicPlaylistItem> items;
  final int page;
  final int pageSize;
  final int total;
  final bool hasMore;

  factory PersonalMusicPlaylistItemsPage.fromJson(Map<String, Object?> json) {
    final pagination = _nullableMap(json['pagination']);
    return PersonalMusicPlaylistItemsPage(
      playlist: PersonalMusicPlaylist.fromJson(
        json['playlist']! as Map<String, Object?>,
      ),
      items: _listOfMaps(
        json['items'],
      ).map(PersonalMusicPlaylistItem.fromJson).toList(),
      page: _intFromJson(pagination, const ['page']) ?? 1,
      pageSize: _intFromJson(pagination, const ['page_size']) ?? 50,
      total: _intFromJson(pagination, const ['total']) ?? 0,
      hasMore: _boolFromJson(pagination, const ['has_more']) ?? false,
    );
  }
}

List<Map<String, Object?>> _listOfMaps(Object? value) {
  return (value as List<Object?>? ?? const [])
      .cast<Map<String, Object?>>()
      .toList();
}

Map<String, Object?>? _nullableMap(Object? value) {
  if (value == null) return null;
  if (value is Map<String, Object?>) return value;
  if (value is Map) {
    return value.map((key, nestedValue) {
      return MapEntry(key.toString(), nestedValue);
    });
  }
  return null;
}

String? _stringFromJson(Map<String, Object?>? json, List<String> keys) {
  if (json == null) return null;
  for (final key in keys) {
    final value = json[key];
    final text = switch (value) {
      String text => text,
      int number => number.toString(),
      _ => null,
    };
    if (text != null && text.trim().isNotEmpty) return text;
  }
  return null;
}

String? _nonEmptyString(String? value) {
  final trimmed = value?.trim();
  if (trimmed == null || trimmed.isEmpty) return null;
  return trimmed;
}

bool? _boolFromJson(Map<String, Object?>? json, List<String> keys) {
  if (json == null) return null;
  for (final key in keys) {
    final value = json[key];
    if (value is bool) return value;
  }
  return null;
}

int? _intFromJson(Map<String, Object?>? json, List<String> keys) {
  if (json == null) return null;
  for (final key in keys) {
    final value = _nullableInt(json[key]);
    if (value != null) return value;
  }
  return null;
}

bool? _onlineFromStatus(String? value) {
  final status = value?.trim().toLowerCase();
  if (status == null || status.isEmpty) return null;
  return switch (status) {
    'online' ||
    'active' ||
    'connected' ||
    'joining' ||
    'joined' ||
    '在线' => true,
    'offline' || 'inactive' || 'disconnected' || 'left' || '离线' => false,
    _ => null,
  };
}

DateTime? _parseDateTime(Object? value) {
  if (value is! String || value.isEmpty) return null;
  return DateTime.tryParse(value);
}

int? _nullableInt(Object? value) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  if (value is String) return int.tryParse(value);
  return null;
}

DateTime _fromUnixSeconds(int value) {
  return DateTime.fromMillisecondsSinceEpoch(value * 1000);
}

DateTime? _nullableUnixSeconds(Object? value) {
  if (value is! int) return null;
  return _fromUnixSeconds(value);
}
