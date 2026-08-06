import 'dart:async';
import 'dart:math';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

import 'error_messages.dart';
import 'models.dart';
import 'server_time_header.dart';
import 'utf8_json.dart';

typedef AccessTokenProvider = Future<String> Function({bool forceRefresh});
typedef ServerTimeCallback = void Function(String? value);
typedef RequestLatencyCallback = void Function(Duration value);
typedef UploadProgressCallback =
    void Function({required int sentBytes, required int totalBytes});

class DownloadedFile {
  const DownloadedFile({
    required this.bytes,
    required this.filename,
    required this.mimeType,
  });

  final Uint8List bytes;
  final String filename;
  final String mimeType;
}

class UploadTransferController {
  Completer<void>? _resumeCompleter;
  bool _cancelled = false;

  bool get isPaused => _resumeCompleter != null;
  bool get isCancelled => _cancelled;

  void pause() {
    if (_cancelled || _resumeCompleter != null) return;
    _resumeCompleter = Completer<void>();
  }

  void resume() {
    final completer = _resumeCompleter;
    if (completer == null) return;
    _resumeCompleter = null;
    if (!completer.isCompleted) completer.complete();
  }

  void cancel() {
    if (_cancelled) return;
    _cancelled = true;
    resume();
  }

  Future<void> waitIfPaused() async {
    while (!_cancelled) {
      final completer = _resumeCompleter;
      if (completer == null) return;
      await completer.future;
    }
  }
}

class UploadCancelledException implements Exception {
  const UploadCancelledException();

  @override
  String toString() => '上传已取消';
}

abstract interface class GangApi {
  Future<AppVersionInfo> getAppVersion();

  Future<CurrentUser> me();

  Future<void> upsertPushDevice({
    required String provider,
    required String installationId,
    required String token,
    required bool enabled,
  });

  Future<void> updatePushDeviceEnabled({
    required String provider,
    required String installationId,
    required bool enabled,
  });

  Future<void> deletePushDevice({
    required String provider,
    required String installationId,
  });

  Future<CurrentUser> updateAccount({
    String? username,
    String? email,
    String? emailVerificationToken,
    bool? emailPublic,
    String? phoneNumber,
    bool? phoneNumberPublic,
    String? language,
  });

  Future<CurrentUser> updateProfile({
    String? displayName,
    String? bio,
    String? gender,
    String? avatarAssetId,
    String? defaultAvatarKey,
  });

  Future<UploadedAsset> uploadImageAsset({
    required Uint8List bytes,
    required String filename,
    String purpose = 'image',
  });

  Future<UploadedAsset> uploadFileAsset({
    required Uint8List bytes,
    required String filename,
    String purpose = 'message_file',
    UploadProgressCallback? onProgress,
    UploadTransferController? controller,
  });

  Future<List<StickerPack>> listStickerPacks({
    String scope = 'personal',
    String? roomId,
    String? ownerUserId,
  });

  Future<StickerPack> createStickerPack({
    required String name,
    String scope = 'personal',
    String? roomId,
    String? ownerUserId,
    int? sortOrder,
  });

  Future<StickerPack> updateStickerPack({
    required String packId,
    String? name,
    int? sortOrder,
  });

  Future<void> deleteStickerPack(String packId);

  Future<void> addSticker({
    required String packId,
    required String assetId,
    required String name,
    int? sortOrder,
    String? idempotencyKey,
    String scope = 'personal',
    String? roomId,
    String? ownerUserId,
  });

  Future<void> deleteSticker({
    required String packId,
    required String stickerId,
    String scope = 'personal',
    String? roomId,
    String? ownerUserId,
  });

  Future<Sticker> updateSticker({
    required String packId,
    required String stickerId,
    String? name,
    int? sortOrder,
  });

  Future<StickerPack> reorderStickers({
    required String packId,
    required List<String> stickerIds,
  });

  Future<DownloadedFile> downloadStickers({required List<String> stickerIds});

  Future<StickerPack> saveSticker({
    required String roomId,
    required String stickerId,
    String? sourceMessageId,
    String targetScope = 'personal',
    String? targetPackId,
    String? name,
    int? sortOrder,
  });

  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
    bool revokeOtherSessions = true,
  });

  Future<List<UserSession>> listSessions();

  Future<void> deleteMyAccount({required bool confirm});

  Future<RoomPage> listRooms({int limit = 50, String? cursor});

  Future<RoomDetail> createRoom({
    required String name,
    String? description,
    String? visibility,
    String? joinPolicy,
    bool? aiVoiceAnnouncementsEnabled,
    String? avatarAssetId,
    String? defaultAvatarKey,
    String? idempotencyKey,
  });

  Future<RoomDetail> getRoom(String roomId);

  Future<RoomDetail> updateRoom({
    required String roomId,
    String? name,
    String? description,
    String? visibility,
    String? joinPolicy,
    bool? aiVoiceAnnouncementsEnabled,
    String? avatarAssetId,
    String? defaultAvatarKey,
  });

  Future<RoomDetail> updateMyRoomSettings({
    required String roomId,
    String? remarkName,
    String? notificationPolicy,
    String? roomDisplayName,
    bool? isPinned,
    bool? aiVoiceAnnouncementsEnabled,
  });

  Future<void> leaveRoom({
    required String roomId,
    bool confirmDeleteIfEmpty = false,
  });

  Future<void> deleteRoom({
    required String roomId,
    required String confirmName,
  });

  Future<RoomMember> updateRoomMemberRole({
    required String roomId,
    required String userId,
    required String role,
  });

  Future<RoomMember> updateRoomMemberRoomDisplayName({
    required String roomId,
    required String userId,
    required String roomDisplayName,
  });

  Future<void> removeRoomMember({
    required String roomId,
    required String userId,
  });

  Future<RoomDetail> transferRoomCreator({
    required String roomId,
    required String userId,
  });

  Future<List<PublicRoom>> searchRooms({required String query, int limit = 20});

  Future<GlobalSearchResults> search({
    required String query,
    int limit = 8,
    Iterable<String>? categories,
    String? myRoomsCursor,
    String? publicRoomsCursor,
    String? messagesCursor,
    String? filesCursor,
  });

  Future<JoinRoomResult> joinRoom(String roomId, {String? reason});

  Future<RoomMemberPage> listRoomMembers(
    String roomId, {
    int limit = 100,
    String? cursor,
  });

  Future<RoomMemberProfile> getRoomMemberProfile({
    required String roomId,
    required String userId,
  });

  Future<UserSummary> getUserProfile(String userId);

  Future<RoomInvite> inviteMember({
    required String roomId,
    required String userId,
  });

  Future<List<RoomBlacklistEntry>> listRoomBlacklist(String roomId);

  Future<RoomBlacklistEntry> blockRoomUser({
    required String roomId,
    required String userId,
  });

  Future<void> unblockRoomUser({
    required String roomId,
    required String userId,
  });

  Future<List<RoomInvite>> listRoomInvites({String status = 'pending'});

  Future<JoinRoomResult> reviewRoomInvite({
    required String inviteId,
    required bool accept,
    String? reason,
  });

  Future<List<RoomApplication>> listRoomApplications({
    String status = 'pending',
  });

  Future<RoomApplication> withdrawRoomApplication({required String requestId});

  Future<List<RoomEventNotification>> listRoomNotifications();

  Future<void> deleteRoomNotification({
    required String notificationType,
    required String notificationId,
  });

  Future<void> markRoomNotificationsRead();

  Future<List<UserSummary>> searchUsers({
    required String query,
    int limit = 20,
  });

  Future<UserSearchPage> searchUsersPage({
    required String query,
    int limit = 20,
    String? cursor,
    bool includeSuspended = false,
  });

  Future<CurrentUser> getForcedUserSettings(String userId);

  Future<List<UserSession>> listForcedUserSessions(String userId);

  Future<void> forceDeleteUserAccount(String userId);

  Future<CurrentUser> updateForcedUserSettings({
    required String userId,
    String? username,
    String? email,
    bool? emailVerified,
    bool? emailPublic,
    String? phoneNumber,
    bool? phoneNumberPublic,
    String? displayName,
    String? bio,
    String? gender,
    String? avatarAssetId,
    String? defaultAvatarKey,
    String? language,
    String? status,
  });

  Future<UserAudioSettings> getForcedUserAudioSettings(String userId);

  Future<UserAudioSettings> updateForcedUserAudioSettings({
    required String userId,
    required UserAudioSettings settings,
  });

  Future<void> forceResetUserPassword({
    required String userId,
    required String newPassword,
  });

  Future<List<JoinRequest>> listJoinRequests(
    String roomId, {
    String status = 'pending',
  });

  Future<void> reviewJoinRequest({
    required String roomId,
    required String requestId,
    required bool approve,
  });

  Future<MessagePage> listMessages({
    required String roomId,
    int limit = 50,
    String? before,
  });

  Future<MessagePage> listMessageHistory({
    required String roomId,
    String query = '',
    String category = 'all',
    String? senderUserId,
    DateTime? startAt,
    DateTime? endAt,
    int limit = 50,
    String? before,
  });

  /// Hides message records only for the current account's room-history view.
  /// It does not recall or force-delete the underlying messages.
  Future<int> hideMessageHistory({
    required String roomId,
    required List<String> messageIds,
  });

  Future<Message> sendMessage({
    required String roomId,
    required String clientMessageId,
    required String body,
    String type = 'text',
    List<MessageAttachment> attachments = const [],
    List<Map<String, Object?>> mentions = const [],
    String? quoteMessageId,
    List<String> quoteMessageIds = const [],
    String? idempotencyKey,
  });

  Future<int> markRead({
    required String roomId,
    required String lastReadMessageId,
  });

  /// Recalls a message. Succeeds immediately for the sender (within the room's
  /// recall policy/window) or an admin; under `admin_approval` a non-admin's
  /// request is queued for review instead (see [MessageRecallResult.isPending]).
  Future<MessageRecallResult> recallMessage({
    required String roomId,
    required String messageId,
    String? reason,
  });

  /// Lists pending message-recall requests awaiting admin review.
  Future<List<MessageRecallRequest>> listMessageRecallRequests({
    required String roomId,
    String status = 'pending',
  });

  /// Approves or rejects a pending message-recall request (admin only).
  Future<void> reviewMessageRecallRequest({
    required String roomId,
    required String requestId,
    required bool approve,
    String? reason,
  });

  /// Force-deletes a message as a moderation action (admin only). Distinct from
  /// a recall; requires explicit confirmation server-side.
  Future<Message> forceDeleteMessage({
    required String roomId,
    required String messageId,
    String? reason,
  });

  /// Lists the caller's own per-member volume overrides for a live room.
  Future<List<LiveMemberVolume>> listMyLiveMemberVolumes(String roomId);

  /// Sets how loudly the caller hears [targetUserId] in [roomId]'s live
  /// session. [volume] is clamped 0–100 server-side.
  Future<LiveMemberVolume> updateMyLiveMemberVolume({
    required String roomId,
    required String targetUserId,
    required int volume,
  });

  /// Applies an admin moderation action to a live participant. [action] is one
  /// of `kick`, `mute_mic`, `block_voice`, `restore_voice`,
  /// `restore_headphones`.
  Future<void> moderateLiveParticipant({
    required String roomId,
    required String userId,
    required String action,
    String? reason,
  });

  Future<LiveState> getLiveState(String roomId);

  Future<LiveJoinResult> joinLive({
    required String roomId,
    required String clientLiveSessionId,
    required String source,
    required bool micMuted,
    required bool headphonesMuted,
    String? idempotencyKey,
  });

  Future<LiveParticipant> updateMyLiveState({
    required String roomId,
    bool? micMuted,
    bool? headphonesMuted,
    bool? cameraOn,
    bool? cameraMirrored,
    bool? screenSharing,
    String? connectionState,
  });

  Future<void> updateMyLiveScreenView({
    required String roomId,
    String? broadcasterUserId,
  });

  /// Issues a publish-only LiveKit token for the caller's hidden screen-audio
  /// aux participant (identity `<userId>--screen-audio`). Fetched on demand when
  /// the user starts screen-share-with-audio, so the token is always fresh
  /// regardless of how long they have been in the call.
  Future<ScreenAudioToken> issueScreenAudioToken({required String roomId});

  Future<MusicBoxState> getMusicBoxState(String roomId);

  Future<List<MusicBoxSearchResult>> searchMusicBox({
    required String roomId,
    required String keyword,
    String? source,
    int? count,
    int? page,
  });

  Future<MusicBoxState> queueMusicBoxTrack({
    required String roomId,
    required String trackId,
    required String title,
    String? source,
    String? artist,
    int? durationMs,
    String? idempotencyKey,
  });

  Future<MusicBoxState> removeMusicBoxItem({
    required String roomId,
    required String itemId,
  });

  Future<MusicBoxState> controlMusicBox({
    required String roomId,
    required String action,
    String? itemId,
    String? mode,
    String? commandId,
    int? expectedRevision,
  });

  Future<MusicBoxState> activateMusicBoxPlaylist({
    required String roomId,
    required MusicBoxActiveSourceType sourceType,
    String? playlistId,
    bool startPlay = true,
  });

  void close();
}

abstract interface class PersonalMusicPlaylistApi {
  Future<PersonalMusicPlaylistPage> listPersonalMusicPlaylists({
    int page = 1,
    int pageSize = 50,
  });

  Future<PersonalMusicPlaylist> createPersonalMusicPlaylist({
    required String name,
  });

  Future<PersonalMusicPlaylist> renamePersonalMusicPlaylist({
    required String playlistId,
    required String name,
  });

  Future<void> deletePersonalMusicPlaylist(String playlistId);

  Future<void> pinPersonalMusicPlaylists({required List<String> playlistIds});

  Future<void> movePersonalMusicPlaylist({
    required String playlistId,
    required String direction,
  });

  Future<PersonalMusicPlaylistItemsPage> getPersonalMusicPlaylist({
    required String playlistId,
    int page = 1,
    int pageSize = 50,
    String? keyword,
    String? source,
  });

  Future<List<MusicBoxSearchResult>> searchPersonalMusicPlaylistTracks({
    required String keyword,
    String? source,
    int count = 20,
    int page = 1,
  });

  Future<PersonalMusicPlaylistItem> addPersonalMusicPlaylistItem({
    required String playlistId,
    required MusicBoxSearchResult track,
    int? durationMs,
  });

  Future<void> deletePersonalMusicPlaylistItem({
    required String playlistId,
    required String itemId,
  });

  Future<void> deletePersonalMusicPlaylistItems({
    required String playlistId,
    required List<String> itemIds,
  });

  Future<void> movePersonalMusicPlaylistItem({
    required String playlistId,
    required String itemId,
    required String direction,
  });

  Future<void> reorderPersonalMusicPlaylistItems({
    required String playlistId,
    required List<String> itemIds,
  });
}

/// Optional extension for atomically merging selected personal playlists into
/// a new playlist while consuming the merged source prefixes.
abstract interface class PersonalMusicPlaylistMergeApi {
  Future<PersonalMusicPlaylistMergeResult> mergePersonalMusicPlaylists({
    required String name,
    required List<String> playlistIds,
  });
}

/// Authenticated transport for an explicitly initiated local song preview.
/// This is separate from [GangApi] so older fakes and read-only playlist
/// adapters do not gain a required method when preview support is unavailable.
abstract interface class MusicTrackPreviewApi {
  Future<DownloadedFile> downloadMusicTrackPreview({
    required String source,
    required String trackId,
  });
}

/// Clones the immutable saved-playlist snapshot that is currently active in a
/// room. Kept separate from [GangApi] so older fakes remain source-compatible.
abstract interface class MusicBoxActivePlaylistCloneApi {
  Future<PersonalMusicPlaylist> cloneActiveMusicBoxPlaylist({
    required String roomId,
    required String playlistId,
    required String snapshotId,
  });
}

abstract interface class RoomMusicPlaylistApi {
  Future<PersonalMusicPlaylistPage> listRoomMusicPlaylists({
    required String roomId,
    int page = 1,
    int pageSize = 50,
  });

  Future<PersonalMusicPlaylist> createRoomMusicPlaylist({
    required String roomId,
    required String name,
  });

  Future<PersonalMusicPlaylist> renameRoomMusicPlaylist({
    required String roomId,
    required String playlistId,
    required String name,
  });

  Future<void> deleteRoomMusicPlaylist({
    required String roomId,
    required String playlistId,
  });

  Future<void> pinRoomMusicPlaylists({
    required String roomId,
    required List<String> playlistIds,
  });

  Future<void> moveRoomMusicPlaylist({
    required String roomId,
    required String playlistId,
    required String direction,
  });

  Future<PersonalMusicPlaylistItemsPage> getRoomMusicPlaylist({
    required String roomId,
    required String playlistId,
    int page = 1,
    int pageSize = 50,
    String? keyword,
    String? source,
  });

  Future<PersonalMusicPlaylistItem> addRoomMusicPlaylistItem({
    required String roomId,
    required String playlistId,
    required MusicBoxSearchResult track,
    int? durationMs,
  });

  Future<void> deleteRoomMusicPlaylistItem({
    required String roomId,
    required String playlistId,
    required String itemId,
  });

  Future<void> deleteRoomMusicPlaylistItems({
    required String roomId,
    required String playlistId,
    required List<String> itemIds,
  });

  Future<void> moveRoomMusicPlaylistItem({
    required String roomId,
    required String playlistId,
    required String itemId,
    required String direction,
  });

  Future<void> reorderRoomMusicPlaylistItems({
    required String roomId,
    required String playlistId,
    required List<String> itemIds,
  });
}

/// Optional room-scoped counterpart of [PersonalMusicPlaylistMergeApi].
abstract interface class RoomMusicPlaylistMergeApi {
  Future<PersonalMusicPlaylistMergeResult> mergeRoomMusicPlaylists({
    required String roomId,
    required String name,
    required List<String> playlistIds,
  });
}

/// Optional room-playlist extension that atomically imports one of the
/// authenticated user's personal playlists while creating the room playlist.
/// Kept separate so existing room playlist fakes and older adapters remain
/// source-compatible.
abstract interface class RoomMusicPlaylistImportApi {
  Future<PersonalMusicPlaylist> createRoomMusicPlaylistFromPersonal({
    required String roomId,
    required String name,
    required String importPlaylistId,
  });
}

/// Optional extension for atomically cloning one room playlist into the
/// authenticated user's personal library.
abstract interface class RoomMusicPlaylistCloneApi {
  Future<PersonalMusicPlaylist> cloneRoomMusicPlaylistToPersonal({
    required String roomId,
    required String playlistId,
  });
}

class GangApiClient
    implements
        GangApi,
        PersonalMusicPlaylistApi,
        PersonalMusicPlaylistMergeApi,
        RoomMusicPlaylistApi,
        RoomMusicPlaylistMergeApi,
        RoomMusicPlaylistImportApi,
        RoomMusicPlaylistCloneApi,
        MusicTrackPreviewApi,
        MusicBoxActivePlaylistCloneApi {
  GangApiClient({
    required this.baseUrl,
    required this.accessTokenProvider,
    http.Client? httpClient,
    this.onServerTime,
    this.onRequestLatency,
  }) : _httpClient = httpClient ?? http.Client();

  final String baseUrl;
  final AccessTokenProvider accessTokenProvider;
  final ServerTimeCallback? onServerTime;
  final RequestLatencyCallback? onRequestLatency;
  final http.Client _httpClient;

  @override
  Future<AppVersionInfo> getAppVersion() async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(_uri('/app/version'), headers: _headers(token));
    });
    return AppVersionInfo.fromJson(decoded);
  }

  @override
  Future<CurrentUser> me() async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(_uri('/me'), headers: _headers(token));
    });
    return CurrentUser.fromJson(decoded);
  }

  @override
  Future<void> upsertPushDevice({
    required String provider,
    required String installationId,
    required String token,
    required bool enabled,
  }) async {
    final providerPath = Uri.encodeComponent(provider);
    final installationPath = Uri.encodeComponent(installationId);
    await _sendJson((accessToken) {
      return _httpClient.put(
        _uri('/me/push-devices/$providerPath/$installationPath'),
        headers: _headers(accessToken),
        body: encodeJsonBody({
          'provider': provider,
          'installation_id': installationId,
          'token': token,
          'platform': 'android',
          'enabled': enabled,
        }),
      );
    });
  }

  @override
  Future<void> updatePushDeviceEnabled({
    required String provider,
    required String installationId,
    required bool enabled,
  }) async {
    final providerPath = Uri.encodeComponent(provider);
    final installationPath = Uri.encodeComponent(installationId);
    await _sendJson((accessToken) {
      return _httpClient.patch(
        _uri('/me/push-devices/$providerPath/$installationPath'),
        headers: _headers(accessToken),
        body: encodeJsonBody({'enabled': enabled}),
      );
    });
  }

  @override
  Future<void> deletePushDevice({
    required String provider,
    required String installationId,
  }) async {
    final providerPath = Uri.encodeComponent(provider);
    final installationPath = Uri.encodeComponent(installationId);
    await _sendJson((accessToken) {
      return _httpClient.delete(
        _uri('/me/push-devices/$providerPath/$installationPath'),
        headers: _headers(accessToken),
      );
    });
  }

  @override
  Future<CurrentUser> updateAccount({
    String? username,
    String? email,
    String? emailVerificationToken,
    bool? emailPublic,
    String? phoneNumber,
    bool? phoneNumberPublic,
    String? language,
  }) async {
    final body = <String, Object?>{};
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
    final decoded = await _sendJson((token) {
      return _httpClient.patch(
        _uri('/users/me/account'),
        headers: _headers(token),
        body: encodeJsonBody(body),
      );
    });
    return CurrentUser.fromJson(decoded['user']! as Map<String, Object?>);
  }

  @override
  Future<CurrentUser> updateProfile({
    String? displayName,
    String? bio,
    String? gender,
    String? avatarAssetId,
    String? defaultAvatarKey,
  }) async {
    final body = <String, Object?>{};
    if (displayName != null) body['display_name'] = displayName;
    if (bio != null) body['bio'] = bio;
    if (gender != null) body['gender'] = gender;
    if (avatarAssetId != null) body['avatar_asset_id'] = avatarAssetId;
    if (defaultAvatarKey != null) body['default_avatar_key'] = defaultAvatarKey;
    final decoded = await _sendJson((token) {
      return _httpClient.patch(
        _uri('/users/me/profile'),
        headers: _headers(token),
        body: encodeJsonBody(body),
      );
    }, retryTransientFailures: true);
    return CurrentUser.fromJson(decoded['user']! as Map<String, Object?>);
  }

  @override
  Future<UploadedAsset> uploadImageAsset({
    required Uint8List bytes,
    required String filename,
    String purpose = 'image',
  }) async {
    return _uploadAsset(
      path: '/uploads/images',
      bytes: bytes,
      filename: filename,
      purpose: purpose,
    );
  }

  @override
  Future<UploadedAsset> uploadFileAsset({
    required Uint8List bytes,
    required String filename,
    String purpose = 'message_file',
    UploadProgressCallback? onProgress,
    UploadTransferController? controller,
  }) async {
    return _uploadAsset(
      path: '/uploads/files',
      bytes: bytes,
      filename: filename,
      purpose: purpose,
      onProgress: onProgress,
      controller: controller,
    );
  }

  Future<UploadedAsset> _uploadAsset({
    required String path,
    required Uint8List bytes,
    required String filename,
    required String purpose,
    UploadProgressCallback? onProgress,
    UploadTransferController? controller,
  }) async {
    final decoded = await _sendJson((token) async {
      final request = http.MultipartRequest('POST', _uri(path));
      request.headers['authorization'] = 'Bearer $token';
      request.fields['purpose'] = purpose;
      final file = onProgress == null && controller == null
          ? http.MultipartFile.fromBytes('file', bytes, filename: filename)
          : http.MultipartFile(
              'file',
              _uploadByteStream(
                bytes,
                onProgress: onProgress,
                controller: controller,
              ),
              bytes.length,
              filename: filename,
            );
      request.files.add(file);
      final streamed = await _httpClient.send(request);
      return http.Response.fromStream(streamed);
    }, retryTransientFailures: true);
    return UploadedAsset.fromJson(decoded['asset']! as Map<String, Object?>);
  }

  @override
  Future<List<StickerPack>> listStickerPacks({
    String scope = 'personal',
    String? roomId,
    String? ownerUserId,
  }) async {
    final query = {'scope': scope};
    if (roomId != null) query['room_id'] = roomId;
    if (ownerUserId != null) query['owner_user_id'] = ownerUserId;
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/sticker-packs', query),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return (decoded['packs'] as List<Object?>? ?? const [])
        .cast<Map<String, Object?>>()
        .map(StickerPack.fromJson)
        .toList();
  }

  @override
  Future<StickerPack> createStickerPack({
    required String name,
    String scope = 'personal',
    String? roomId,
    String? ownerUserId,
    int? sortOrder,
  }) async {
    final body = <String, Object?>{'scope': scope, 'name': name};
    if (roomId != null) body['room_id'] = roomId;
    if (ownerUserId != null) body['owner_user_id'] = ownerUserId;
    if (sortOrder != null) body['sort_order'] = sortOrder;
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/sticker-packs'),
        headers: _headers(token),
        body: encodeJsonBody(body),
      );
    });
    return StickerPack.fromJson(decoded['pack']! as Map<String, Object?>);
  }

  @override
  Future<StickerPack> updateStickerPack({
    required String packId,
    String? name,
    int? sortOrder,
  }) async {
    final body = <String, Object?>{};
    if (name != null) body['name'] = name;
    if (sortOrder != null) body['sort_order'] = sortOrder;
    final decoded = await _sendJson((token) {
      return _httpClient.patch(
        _uri('/sticker-packs/$packId'),
        headers: _headers(token),
        body: encodeJsonBody(body),
      );
    });
    return StickerPack.fromJson(decoded['pack']! as Map<String, Object?>);
  }

  @override
  Future<void> deleteStickerPack(String packId) async {
    await _sendJson((token) {
      return _httpClient.delete(
        _uri('/sticker-packs/$packId'),
        headers: _headers(token),
      );
    });
  }

  @override
  Future<void> addSticker({
    required String packId,
    required String assetId,
    required String name,
    int? sortOrder,
    String? idempotencyKey,
    String scope = 'personal',
    String? roomId,
    String? ownerUserId,
  }) async {
    final body = <String, Object?>{'asset_id': assetId, 'name': name};
    if (sortOrder != null) body['sort_order'] = sortOrder;
    final requestIdempotencyKey = idempotencyKey ?? newUuid();
    Future<void> send() {
      return _sendJson((token) {
        return _httpClient.post(
          _uri('/sticker-packs/$packId/stickers'),
          headers: _headers(token, idempotencyKey: requestIdempotencyKey),
          body: encodeJsonBody(body),
        );
      });
    }

    const maxAttempts = 3;
    for (var attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        await send();
        return;
      } on ApiException catch (e) {
        if (!_isRetryableHttpFailure(e.statusCode)) rethrow;
        if (await _stickerAssetAlreadyLinked(
          packId: packId,
          assetId: assetId,
          scope: scope,
          roomId: roomId,
          ownerUserId: ownerUserId,
        )) {
          return;
        }
        if (attempt == maxAttempts) rethrow;
        await Future<void>.delayed(Duration(milliseconds: 120 * attempt));
      } on http.ClientException catch (e) {
        if (!_isRetryableTransportFailure(e)) rethrow;
        if (await _stickerAssetAlreadyLinked(
          packId: packId,
          assetId: assetId,
          scope: scope,
          roomId: roomId,
          ownerUserId: ownerUserId,
        )) {
          return;
        }
        if (attempt == maxAttempts) rethrow;
        await Future<void>.delayed(Duration(milliseconds: 120 * attempt));
      }
    }
  }

  Future<bool> _stickerAssetAlreadyLinked({
    required String packId,
    required String assetId,
    required String scope,
    String? roomId,
    String? ownerUserId,
  }) async {
    try {
      final packs = await listStickerPacks(
        scope: scope,
        roomId: roomId,
        ownerUserId: ownerUserId,
      );
      for (final pack in packs) {
        if (pack.id != packId) continue;
        for (final sticker in pack.stickers) {
          if (sticker.asset.id == assetId) return true;
        }
      }
    } catch (_) {
      // If verification is also unavailable, let the caller retry once.
    }
    return false;
  }

  Future<bool> _stickerAlreadyDeleted({
    required String packId,
    required String stickerId,
    required String scope,
    String? roomId,
    String? ownerUserId,
  }) async {
    try {
      final packs = await listStickerPacks(
        scope: scope,
        roomId: roomId,
        ownerUserId: ownerUserId,
      );
      for (final pack in packs) {
        if (pack.id != packId) continue;
        return !pack.stickers.any((sticker) => sticker.id == stickerId);
      }
    } catch (_) {
      // If verification also fails, let the delete path retry.
    }
    return false;
  }

  @override
  Future<void> deleteSticker({
    required String packId,
    required String stickerId,
    String scope = 'personal',
    String? roomId,
    String? ownerUserId,
  }) async {
    Future<void> send() {
      return _sendJson((token) {
        return _httpClient.delete(
          _uri('/sticker-packs/$packId/stickers/$stickerId'),
          headers: _headers(token),
        );
      });
    }

    const maxAttempts = 3;
    for (var attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        await send();
        return;
      } on ApiException catch (e) {
        if (e.statusCode == 404) return;
        if (!_isRetryableHttpFailure(e.statusCode)) rethrow;
        if (await _stickerAlreadyDeleted(
          packId: packId,
          stickerId: stickerId,
          scope: scope,
          roomId: roomId,
          ownerUserId: ownerUserId,
        )) {
          return;
        }
        if (attempt == maxAttempts) rethrow;
        await Future<void>.delayed(Duration(milliseconds: 120 * attempt));
      } on http.ClientException catch (e) {
        if (!_isRetryableTransportFailure(e)) rethrow;
        if (await _stickerAlreadyDeleted(
          packId: packId,
          stickerId: stickerId,
          scope: scope,
          roomId: roomId,
          ownerUserId: ownerUserId,
        )) {
          return;
        }
        if (attempt == maxAttempts) rethrow;
        await Future<void>.delayed(Duration(milliseconds: 120 * attempt));
      }
    }
  }

  @override
  Future<Sticker> updateSticker({
    required String packId,
    required String stickerId,
    String? name,
    int? sortOrder,
  }) async {
    final body = <String, Object?>{};
    if (name != null) body['name'] = name;
    if (sortOrder != null) body['sort_order'] = sortOrder;
    final decoded = await _sendJson((token) {
      return _httpClient.patch(
        _uri('/sticker-packs/$packId/stickers/$stickerId'),
        headers: _headers(token),
        body: encodeJsonBody(body),
      );
    });
    return Sticker.fromJson(decoded['sticker']! as Map<String, Object?>);
  }

  @override
  Future<StickerPack> reorderStickers({
    required String packId,
    required List<String> stickerIds,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/sticker-packs/$packId/stickers/reorder'),
        headers: _headers(token),
        body: encodeJsonBody({'sticker_ids': stickerIds}),
      );
    });
    return StickerPack.fromJson(decoded['pack']! as Map<String, Object?>);
  }

  @override
  Future<DownloadedFile> downloadStickers({
    required List<String> stickerIds,
  }) async {
    final response = await _sendWithAuth((token) {
      return _httpClient.get(
        _uri('/stickers/download', {'ids': stickerIds.join(',')}),
        headers: {'authorization': 'Bearer $token'},
      );
    });
    _throwIfFailed(response);
    return DownloadedFile(
      bytes: response.bodyBytes,
      filename: _downloadFilename(response),
      mimeType: response.headers['content-type'] ?? 'application/octet-stream',
    );
  }

  @override
  Future<StickerPack> saveSticker({
    required String roomId,
    required String stickerId,
    String? sourceMessageId,
    String targetScope = 'personal',
    String? targetPackId,
    String? name,
    int? sortOrder,
  }) async {
    final body = <String, Object?>{
      'sticker_id': stickerId,
      'target_scope': targetScope,
    };
    if (sourceMessageId != null) body['source_message_id'] = sourceMessageId;
    if (targetPackId != null) body['target_pack_id'] = targetPackId;
    if (name != null) body['name'] = name;
    if (sortOrder != null) body['sort_order'] = sortOrder;
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/stickers/save'),
        headers: _headers(token),
        body: encodeJsonBody(body),
      );
    });
    return StickerPack.fromJson(decoded['pack']! as Map<String, Object?>);
  }

  @override
  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
    bool revokeOtherSessions = true,
  }) async {
    await _sendJson((token) {
      return _httpClient.post(
        _uri('/auth/password'),
        headers: _headers(token),
        body: encodeJsonBody({
          'current_password': currentPassword,
          'new_password': newPassword,
          'revoke_other_sessions': revokeOtherSessions,
        }),
      );
    });
  }

  @override
  Future<List<UserSession>> listSessions() async {
    final decoded = await _sendJsonValue((token) {
      return _httpClient.get(_uri('/auth/sessions'), headers: _headers(token));
    }, retryTransientFailures: true);
    final items = decoded is List
        ? decoded.cast<Object?>()
        : decoded is Map<String, Object?>
        ? decoded['items'] as List<Object?>? ??
              decoded['sessions'] as List<Object?>? ??
              const []
        : const [];
    return items
        .cast<Map<String, Object?>>()
        .map(UserSession.fromJson)
        .toList();
  }

  @override
  Future<void> deleteMyAccount({required bool confirm}) async {
    await _sendJson((token) {
      return _httpClient.delete(
        _uri('/users/me/account'),
        headers: _headers(token),
        body: encodeJsonBody({'confirm': confirm}),
      );
    });
  }

  @override
  Future<RoomPage> listRooms({int limit = 50, String? cursor}) async {
    final query = {'limit': '$limit'};
    if (cursor != null) query['cursor'] = cursor;
    final decoded = await _sendJson((token) {
      return _httpClient.get(_uri('/rooms', query), headers: _headers(token));
    }, retryTransientFailures: true);
    return RoomPage.fromJson(decoded);
  }

  @override
  Future<RoomDetail> createRoom({
    required String name,
    String? description,
    String? visibility,
    String? joinPolicy,
    bool? aiVoiceAnnouncementsEnabled,
    String? avatarAssetId,
    String? defaultAvatarKey,
    String? idempotencyKey,
  }) async {
    final decoded = await _sendJson((token) {
      final body = <String, Object?>{'name': name};
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
      return _httpClient.post(
        _uri('/rooms'),
        headers: _headers(token, idempotencyKey: idempotencyKey ?? newUuid()),
        body: encodeJsonBody(body),
      );
    });
    return RoomDetail.fromJson(decoded['room']! as Map<String, Object?>);
  }

  @override
  Future<RoomDetail> getRoom(String roomId) async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(_uri('/rooms/$roomId'), headers: _headers(token));
    }, retryTransientFailures: true);
    return RoomDetail.fromJson(decoded['room']! as Map<String, Object?>);
  }

  @override
  Future<RoomDetail> updateRoom({
    required String roomId,
    String? name,
    String? description,
    String? visibility,
    String? joinPolicy,
    bool? aiVoiceAnnouncementsEnabled,
    String? avatarAssetId,
    String? defaultAvatarKey,
  }) async {
    final body = <String, Object?>{};
    if (name != null) body['name'] = name;
    if (description != null) body['description'] = description;
    if (visibility != null) body['visibility'] = visibility;
    if (joinPolicy != null) body['join_policy'] = joinPolicy;
    if (aiVoiceAnnouncementsEnabled != null) {
      body['ai_voice_announcements_enabled'] = aiVoiceAnnouncementsEnabled;
    }
    if (avatarAssetId != null) body['avatar_asset_id'] = avatarAssetId;
    if (defaultAvatarKey != null) body['default_avatar_key'] = defaultAvatarKey;
    final decoded = await _sendJson((token) {
      return _httpClient.patch(
        _uri('/rooms/$roomId'),
        headers: _headers(token),
        body: encodeJsonBody(body),
      );
    }, retryTransientFailures: true);
    return RoomDetail.fromJson(decoded['room']! as Map<String, Object?>);
  }

  @override
  Future<RoomDetail> updateMyRoomSettings({
    required String roomId,
    String? remarkName,
    String? notificationPolicy,
    String? roomDisplayName,
    bool? isPinned,
    bool? aiVoiceAnnouncementsEnabled,
  }) async {
    final body = <String, Object?>{};
    if (remarkName != null) body['remark_name'] = remarkName;
    if (notificationPolicy != null) {
      body['notification_policy'] = notificationPolicy;
    }
    if (roomDisplayName != null) body['room_display_name'] = roomDisplayName;
    if (isPinned != null) body['is_pinned'] = isPinned;
    if (aiVoiceAnnouncementsEnabled != null) {
      body['ai_voice_announcements_enabled'] = aiVoiceAnnouncementsEnabled;
    }
    final decoded = await _sendJson((token) {
      return _httpClient.patch(
        _uri('/rooms/$roomId/me'),
        headers: _headers(token),
        body: encodeJsonBody(body),
      );
    }, retryTransientFailures: true);
    return RoomDetail.fromJson(decoded['room']! as Map<String, Object?>);
  }

  @override
  Future<void> leaveRoom({
    required String roomId,
    bool confirmDeleteIfEmpty = false,
  }) async {
    await _sendJson((token) {
      return _httpClient.delete(
        _uri('/rooms/$roomId/members/me'),
        headers: _headers(token),
        body: encodeJsonBody({
          if (confirmDeleteIfEmpty) 'confirm_delete_if_empty': true,
        }),
      );
    });
  }

  @override
  Future<void> deleteRoom({
    required String roomId,
    required String confirmName,
  }) async {
    await _sendJson((token) {
      return _httpClient.delete(
        _uri('/rooms/$roomId'),
        headers: _headers(token),
        body: encodeJsonBody({'confirm_name': confirmName}),
      );
    });
  }

  @override
  Future<RoomMember> updateRoomMemberRole({
    required String roomId,
    required String userId,
    required String role,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.patch(
        _uri('/rooms/$roomId/members/$userId'),
        headers: _headers(token),
        body: encodeJsonBody({'role': role}),
      );
    });
    return RoomMember.fromJson(decoded['member']! as Map<String, Object?>);
  }

  @override
  Future<RoomMember> updateRoomMemberRoomDisplayName({
    required String roomId,
    required String userId,
    required String roomDisplayName,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.patch(
        _uri('/rooms/$roomId/members/$userId'),
        headers: _headers(token),
        body: encodeJsonBody({'room_display_name': roomDisplayName}),
      );
    });
    return RoomMember.fromJson(decoded['member']! as Map<String, Object?>);
  }

  @override
  Future<void> removeRoomMember({
    required String roomId,
    required String userId,
  }) async {
    await _sendJson((token) {
      return _httpClient.delete(
        _uri('/rooms/$roomId/members/$userId'),
        headers: _headers(token),
      );
    });
  }

  @override
  Future<RoomDetail> transferRoomCreator({
    required String roomId,
    required String userId,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.patch(
        _uri('/rooms/$roomId/creator'),
        headers: _headers(token),
        body: encodeJsonBody({'user_id': userId}),
      );
    });
    return RoomDetail.fromJson(decoded['room']! as Map<String, Object?>);
  }

  @override
  Future<List<PublicRoom>> searchRooms({
    required String query,
    int limit = 20,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/rooms/search', {'q': query, 'limit': '$limit'}),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return (decoded['rooms'] as List<Object?>? ?? const [])
        .cast<Map<String, Object?>>()
        .map(PublicRoom.fromJson)
        .toList();
  }

  @override
  Future<GlobalSearchResults> search({
    required String query,
    int limit = 8,
    Iterable<String>? categories,
    String? myRoomsCursor,
    String? publicRoomsCursor,
    String? messagesCursor,
    String? filesCursor,
  }) async {
    final queryParameters = <String, String>{'q': query, 'limit': '$limit'};
    final categoryList = categories?.where((category) => category.isNotEmpty);
    if (categoryList != null) {
      final encodedCategories = categoryList.join(',');
      if (encodedCategories.isNotEmpty) {
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

    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/search', queryParameters),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return GlobalSearchResults.fromJson(decoded);
  }

  @override
  Future<JoinRoomResult> joinRoom(String roomId, {String? reason}) async {
    final body = <String, Object?>{};
    final trimmedReason = reason?.trim();
    if (trimmedReason != null && trimmedReason.isNotEmpty) {
      body['reason'] = trimmedReason;
    }
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/join'),
        headers: _headers(token, idempotencyKey: newUuid()),
        body: body.isEmpty ? null : encodeJsonBody(body),
      );
    });
    final roomJson = decoded['room'] as Map<String, Object?>?;
    if (roomJson != null) {
      return JoinRoomResult(room: RoomDetail.fromJson(roomJson));
    }
    // approval_required path: server returns {"join_request": {...}} with 202.
    return const JoinRoomResult(pending: true);
  }

  @override
  Future<RoomMemberPage> listRoomMembers(
    String roomId, {
    int limit = 100,
    String? cursor,
  }) async {
    final query = {'limit': '$limit'};
    if (cursor != null) query['cursor'] = cursor;
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/rooms/$roomId/members', query),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return RoomMemberPage.fromJson(decoded);
  }

  @override
  Future<RoomMemberProfile> getRoomMemberProfile({
    required String roomId,
    required String userId,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/rooms/$roomId/members/$userId/profile'),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return RoomMemberProfile.fromJson(
      decoded['profile']! as Map<String, Object?>,
    );
  }

  @override
  Future<UserSummary> getUserProfile(String userId) async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/users/$userId/profile'),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    final profile = decoded['profile']! as Map<String, Object?>;
    return UserSummary.fromJson(profile['user']! as Map<String, Object?>);
  }

  @override
  Future<RoomInvite> inviteMember({
    required String roomId,
    required String userId,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/invites'),
        headers: _headers(token, idempotencyKey: newUuid()),
        body: encodeJsonBody({'user_id': userId}),
      );
    });
    return RoomInvite.fromJson(decoded['invite']! as Map<String, Object?>);
  }

  @override
  Future<List<RoomBlacklistEntry>> listRoomBlacklist(String roomId) async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/rooms/$roomId/blacklist'),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return (decoded['blacklist'] as List<Object?>? ??
            decoded['items'] as List<Object?>? ??
            const [])
        .cast<Map<String, Object?>>()
        .map(RoomBlacklistEntry.fromJson)
        .toList();
  }

  @override
  Future<RoomBlacklistEntry> blockRoomUser({
    required String roomId,
    required String userId,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/blacklist'),
        headers: _headers(token, idempotencyKey: newUuid()),
        body: encodeJsonBody({'user_id': userId}),
      );
    });
    return RoomBlacklistEntry.fromJson(
      decoded['entry']! as Map<String, Object?>,
    );
  }

  @override
  Future<void> unblockRoomUser({
    required String roomId,
    required String userId,
  }) async {
    await _sendJson((token) {
      return _httpClient.delete(
        _uri('/rooms/$roomId/blacklist/$userId'),
        headers: _headers(token),
      );
    });
  }

  @override
  Future<List<RoomInvite>> listRoomInvites({String status = 'pending'}) async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/room-invites', {'status': status}),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return (decoded['invites'] as List<Object?>? ?? const [])
        .cast<Map<String, Object?>>()
        .map(RoomInvite.fromJson)
        .toList();
  }

  @override
  Future<JoinRoomResult> reviewRoomInvite({
    required String inviteId,
    required bool accept,
    String? reason,
  }) async {
    final body = <String, Object?>{'decision': accept ? 'accept' : 'reject'};
    final trimmedReason = reason?.trim();
    if (trimmedReason != null && trimmedReason.isNotEmpty) {
      body['reason'] = trimmedReason;
    }
    final decoded = await _sendJson((token) {
      return _httpClient.patch(
        _uri('/room-invites/$inviteId'),
        headers: _headers(token),
        body: encodeJsonBody(body),
      );
    });
    final roomJson = decoded['room'] as Map<String, Object?>?;
    if (roomJson != null) {
      return JoinRoomResult(room: RoomDetail.fromJson(roomJson));
    }
    if (decoded['join_request'] != null) {
      return const JoinRoomResult(pending: true);
    }
    return const JoinRoomResult();
  }

  @override
  Future<List<RoomApplication>> listRoomApplications({
    String status = 'pending',
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/room-applications', {'status': status}),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return (decoded['applications'] as List<Object?>? ?? const [])
        .cast<Map<String, Object?>>()
        .map(RoomApplication.fromJson)
        .toList();
  }

  @override
  Future<RoomApplication> withdrawRoomApplication({
    required String requestId,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.patch(
        _uri('/room-applications/$requestId'),
        headers: _headers(token),
        body: encodeJsonBody({'decision': 'withdraw'}),
      );
    });
    return RoomApplication.fromJson(
      decoded['application']! as Map<String, Object?>,
    );
  }

  @override
  Future<List<RoomEventNotification>> listRoomNotifications() async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/room-notifications'),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return (decoded['notifications'] as List<Object?>? ?? const [])
        .cast<Map<String, Object?>>()
        .map(RoomEventNotification.fromJson)
        .toList();
  }

  @override
  Future<void> deleteRoomNotification({
    required String notificationType,
    required String notificationId,
  }) async {
    await _sendJson((token) {
      return _httpClient.delete(
        _uri('/room-notifications/$notificationType/$notificationId'),
        headers: _headers(token),
      );
    });
  }

  @override
  Future<void> markRoomNotificationsRead() async {
    await _sendJson((token) {
      return _httpClient.post(
        _uri('/room-notifications/read'),
        headers: _headers(token),
      );
    });
  }

  @override
  Future<List<UserSummary>> searchUsers({
    required String query,
    int limit = 20,
  }) async {
    final page = await searchUsersPage(query: query, limit: limit);
    return page.users;
  }

  @override
  Future<UserSearchPage> searchUsersPage({
    required String query,
    int limit = 20,
    String? cursor,
    bool includeSuspended = false,
  }) async {
    final parameters = <String, String>{'q': query, 'limit': '$limit'};
    if (cursor != null) parameters['cursor'] = cursor;
    if (includeSuspended) parameters['include_suspended'] = 'true';
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/users/search', parameters),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return UserSearchPage.fromJson(decoded);
  }

  @override
  Future<CurrentUser> getForcedUserSettings(String userId) async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/users/$userId/settings'),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return CurrentUser.fromJson(decoded['user']! as Map<String, Object?>);
  }

  @override
  Future<List<UserSession>> listForcedUserSessions(String userId) async {
    final decoded = await _sendJsonValue((token) {
      return _httpClient.get(
        _uri('/users/$userId/sessions'),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    final items = decoded is List
        ? decoded.cast<Object?>()
        : decoded is Map<String, Object?>
        ? decoded['items'] as List<Object?>? ??
              decoded['sessions'] as List<Object?>? ??
              const []
        : const [];
    return items
        .cast<Map<String, Object?>>()
        .map(UserSession.fromJson)
        .toList();
  }

  @override
  Future<void> forceDeleteUserAccount(String userId) async {
    await _sendJson((token) {
      return _httpClient.delete(
        _uri('/users/$userId/account'),
        headers: _headers(token),
        body: encodeJsonBody({'confirm': true}),
      );
    });
  }

  @override
  Future<CurrentUser> updateForcedUserSettings({
    required String userId,
    String? username,
    String? email,
    bool? emailVerified,
    bool? emailPublic,
    String? phoneNumber,
    bool? phoneNumberPublic,
    String? displayName,
    String? bio,
    String? gender,
    String? avatarAssetId,
    String? defaultAvatarKey,
    String? language,
    String? status,
  }) async {
    final body = <String, Object?>{};
    void put(String key, Object? value) {
      if (value != null) body[key] = value;
    }

    put('username', username);
    put('email', email);
    put('email_verified', emailVerified);
    put('email_public', emailPublic);
    put('phone_number', phoneNumber);
    put('phone_number_public', phoneNumberPublic);
    put('display_name', displayName);
    put('bio', bio);
    put('gender', gender);
    put('avatar_asset_id', avatarAssetId);
    put('default_avatar_key', defaultAvatarKey);
    put('language', language);
    put('status', status);
    final decoded = await _sendJson((token) {
      return _httpClient.patch(
        _uri('/users/$userId/settings'),
        headers: _headers(token),
        body: encodeJsonBody(body),
      );
    });
    return CurrentUser.fromJson(decoded['user']! as Map<String, Object?>);
  }

  @override
  Future<UserAudioSettings> getForcedUserAudioSettings(String userId) async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/users/$userId/audio-settings'),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return UserAudioSettings.fromJson(
      decoded['audio_settings']! as Map<String, Object?>,
    );
  }

  @override
  Future<UserAudioSettings> updateForcedUserAudioSettings({
    required String userId,
    required UserAudioSettings settings,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.patch(
        _uri('/users/$userId/audio-settings'),
        headers: _headers(token),
        body: encodeJsonBody({
          'default_audio_input_volume': settings.defaultAudioInputVolume,
          'default_audio_output_volume': settings.defaultAudioOutputVolume,
          'live_mic_input_volume': settings.liveMicInputVolume,
          'live_voice_output_volume': settings.liveVoiceOutputVolume,
          'live_screen_share_output_volume':
              settings.liveScreenShareOutputVolume,
          'live_music_output_volume': settings.liveMusicOutputVolume,
        }),
      );
    });
    return UserAudioSettings.fromJson(
      decoded['audio_settings']! as Map<String, Object?>,
    );
  }

  @override
  Future<void> forceResetUserPassword({
    required String userId,
    required String newPassword,
  }) async {
    await _sendJson((token) {
      return _httpClient.post(
        _uri('/users/$userId/password'),
        headers: _headers(token),
        body: encodeJsonBody({'new_password': newPassword}),
      );
    });
  }

  @override
  Future<List<JoinRequest>> listJoinRequests(
    String roomId, {
    String status = 'pending',
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/rooms/$roomId/join-requests', {'status': status}),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return (decoded['requests'] as List<Object?>? ?? const [])
        .cast<Map<String, Object?>>()
        .map(JoinRequest.fromJson)
        .toList();
  }

  @override
  Future<void> reviewJoinRequest({
    required String roomId,
    required String requestId,
    required bool approve,
  }) async {
    await _sendJson((token) {
      return _httpClient.patch(
        _uri('/rooms/$roomId/join-requests/$requestId'),
        headers: _headers(token),
        body: encodeJsonBody({'decision': approve ? 'approve' : 'reject'}),
      );
    });
  }

  @override
  Future<MessagePage> listMessages({
    required String roomId,
    int limit = 50,
    String? before,
  }) async {
    final query = {'limit': '$limit'};
    if (before != null) query['before'] = before;
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/rooms/$roomId/messages', query),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return MessagePage.fromJson(decoded);
  }

  @override
  Future<MessagePage> listMessageHistory({
    required String roomId,
    String query = '',
    String category = 'all',
    String? senderUserId,
    DateTime? startAt,
    DateTime? endAt,
    int limit = 50,
    String? before,
  }) async {
    final parameters = <String, String>{
      'limit': '$limit',
      'category': category,
    };
    final trimmedQuery = query.trim();
    if (trimmedQuery.isNotEmpty) parameters['query'] = trimmedQuery;
    final trimmedSender = senderUserId?.trim();
    if (trimmedSender != null && trimmedSender.isNotEmpty) {
      parameters['sender_user_id'] = trimmedSender;
    }
    if (startAt != null) {
      parameters['start_at'] = startAt.toUtc().toIso8601String();
    }
    if (endAt != null) {
      parameters['end_at'] = endAt.toUtc().toIso8601String();
    }
    if (before != null && before.trim().isNotEmpty) {
      parameters['before'] = before.trim();
    }
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/rooms/$roomId/message-history', parameters),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return MessagePage.fromJson(decoded);
  }

  @override
  Future<int> hideMessageHistory({
    required String roomId,
    required List<String> messageIds,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/message-history/hide'),
        headers: _headers(token),
        body: encodeJsonBody({'message_ids': messageIds, 'confirm': true}),
      );
    });
    return decoded['deleted_count'] as int? ?? 0;
  }

  @override
  Future<Message> sendMessage({
    required String roomId,
    required String clientMessageId,
    required String body,
    String type = 'text',
    List<MessageAttachment> attachments = const [],
    List<Map<String, Object?>> mentions = const [],
    String? quoteMessageId,
    List<String> quoteMessageIds = const [],
    String? idempotencyKey,
  }) async {
    final requestIdempotencyKey = idempotencyKey ?? newUuid();
    final decoded = await _sendJson((token) {
      final requestBody = <String, Object?>{
        'client_message_id': clientMessageId,
        'body': body,
      };
      if (type != 'text') requestBody['type'] = type;
      if (attachments.isNotEmpty) {
        requestBody['attachments'] = attachments
            .map((attachment) => attachment.toJson())
            .toList();
      }
      if (mentions.isNotEmpty) {
        requestBody['mentions'] = mentions;
      }
      if (quoteMessageId != null && quoteMessageId.isNotEmpty) {
        requestBody['quote_message_id'] = quoteMessageId;
      }
      if (quoteMessageIds.isNotEmpty) {
        requestBody['quote_message_ids'] = quoteMessageIds;
        requestBody['quote_message_id'] = quoteMessageIds.first;
      }
      return _httpClient.post(
        _uri('/rooms/$roomId/messages'),
        headers: _headers(token, idempotencyKey: requestIdempotencyKey),
        body: encodeJsonBody(requestBody),
      );
    }, retryTransientFailures: true);
    return Message.fromJson(decoded['message']! as Map<String, Object?>);
  }

  @override
  Future<int> markRead({
    required String roomId,
    required String lastReadMessageId,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/read'),
        headers: _headers(token),
        body: encodeJsonBody({'last_read_message_id': lastReadMessageId}),
      );
    }, retryTransientFailures: true);
    return decoded['unread_count']! as int;
  }

  @override
  Future<MessageRecallResult> recallMessage({
    required String roomId,
    required String messageId,
    String? reason,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/messages/$messageId/recall'),
        headers: _headers(token),
        body: encodeJsonBody({'reason': ?reason}),
      );
    });
    return MessageRecallResult.fromJson(decoded);
  }

  @override
  Future<List<MessageRecallRequest>> listMessageRecallRequests({
    required String roomId,
    String status = 'pending',
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/rooms/$roomId/message-recall-requests', {'status': status}),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    final requests = decoded['requests'] as List<Object?>? ?? const [];
    return requests
        .cast<Map<String, Object?>>()
        .map(MessageRecallRequest.fromJson)
        .toList();
  }

  @override
  Future<void> reviewMessageRecallRequest({
    required String roomId,
    required String requestId,
    required bool approve,
    String? reason,
  }) async {
    await _sendJson((token) {
      return _httpClient.patch(
        _uri('/rooms/$roomId/message-recall-requests/$requestId'),
        headers: _headers(token),
        body: encodeJsonBody({
          'decision': approve ? 'approve' : 'reject',
          'reason': ?reason,
        }),
      );
    });
  }

  @override
  Future<Message> forceDeleteMessage({
    required String roomId,
    required String messageId,
    String? reason,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/messages/$messageId/force-delete'),
        headers: _headers(token),
        body: encodeJsonBody({'confirm': true, 'reason': ?reason}),
      );
    });
    return Message.fromJson(decoded['message']! as Map<String, Object?>);
  }

  @override
  Future<List<LiveMemberVolume>> listMyLiveMemberVolumes(String roomId) async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/rooms/$roomId/live/me/member-volumes'),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    final volumes = decoded['member_volumes'] as List<Object?>? ?? const [];
    return volumes
        .cast<Map<String, Object?>>()
        .map(LiveMemberVolume.fromJson)
        .toList();
  }

  @override
  Future<LiveMemberVolume> updateMyLiveMemberVolume({
    required String roomId,
    required String targetUserId,
    required int volume,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.patch(
        _uri('/rooms/$roomId/live/me/member-volumes/$targetUserId'),
        headers: _headers(token),
        body: encodeJsonBody({'volume': volume}),
      );
    });
    return LiveMemberVolume.fromJson(
      decoded['member_volume']! as Map<String, Object?>,
    );
  }

  @override
  Future<void> moderateLiveParticipant({
    required String roomId,
    required String userId,
    required String action,
    String? reason,
  }) async {
    await _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/live/participants/$userId/moderation'),
        headers: _headers(token),
        body: encodeJsonBody({'action': action, 'reason': ?reason}),
      );
    });
  }

  @override
  Future<LiveState> getLiveState(String roomId) async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/rooms/$roomId/live'),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return LiveState.fromJson(decoded['live']! as Map<String, Object?>);
  }

  @override
  Future<LiveJoinResult> joinLive({
    required String roomId,
    required String clientLiveSessionId,
    required String source,
    required bool micMuted,
    required bool headphonesMuted,
    String? idempotencyKey,
  }) {
    final requestIdempotencyKey = idempotencyKey ?? newUuid();
    return _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/live/join'),
        headers: _headers(token, idempotencyKey: requestIdempotencyKey),
        body: encodeJsonBody({
          'client_live_session_id': clientLiveSessionId,
          'source': source,
          'mic_muted': micMuted,
          'headphones_muted': headphonesMuted,
        }),
      );
    }, retryTransientFailures: true).then(LiveJoinResult.fromJson);
  }

  @override
  Future<ScreenAudioToken> issueScreenAudioToken({required String roomId}) {
    return _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/live/screen-audio-token'),
        headers: _headers(token),
      );
    }, retryTransientFailures: true).then(ScreenAudioToken.fromJson);
  }

  @override
  Future<LiveParticipant> updateMyLiveState({
    required String roomId,
    bool? micMuted,
    bool? headphonesMuted,
    bool? cameraOn,
    bool? cameraMirrored,
    bool? screenSharing,
    String? connectionState,
  }) async {
    final body = <String, Object?>{};
    if (micMuted != null) body['mic_muted'] = micMuted;
    if (headphonesMuted != null) body['headphones_muted'] = headphonesMuted;
    if (cameraOn != null) body['camera_on'] = cameraOn;
    if (cameraMirrored != null) body['camera_mirrored'] = cameraMirrored;
    if (screenSharing != null) body['screen_sharing'] = screenSharing;
    if (connectionState != null) body['connection_state'] = connectionState;
    final decoded = await _sendJson((token) {
      return _httpClient.patch(
        _uri('/rooms/$roomId/live/me'),
        headers: _headers(token),
        body: encodeJsonBody(body),
      );
    });
    return LiveParticipant.fromJson(
      decoded['participant']! as Map<String, Object?>,
    );
  }

  @override
  Future<void> updateMyLiveScreenView({
    required String roomId,
    String? broadcasterUserId,
  }) async {
    await _sendJson((token) {
      return _httpClient.patch(
        _uri('/rooms/$roomId/live/me/screen-view'),
        headers: _headers(token),
        body: encodeJsonBody({'broadcaster_user_id': broadcasterUserId ?? ''}),
      );
    });
  }

  @override
  Future<MusicBoxState> getMusicBoxState(String roomId) async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/rooms/$roomId/music-box/state'),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return MusicBoxState.fromJson(decoded);
  }

  @override
  Future<List<MusicBoxSearchResult>> searchMusicBox({
    required String roomId,
    required String keyword,
    String? source,
    int? count,
    int? page,
  }) async {
    final query = <String, String>{'keyword': keyword};
    if (source != null && source.isNotEmpty) query['source'] = source;
    if (count != null) query['count'] = '$count';
    if (page != null) query['page'] = '$page';
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/rooms/$roomId/music-box/search', query),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return (decoded['results'] as List<Object?>? ?? const [])
        .cast<Map<String, Object?>>()
        .map(MusicBoxSearchResult.fromJson)
        .toList();
  }

  @override
  Future<PersonalMusicPlaylistPage> listPersonalMusicPlaylists({
    int page = 1,
    int pageSize = 50,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/me/music-box/playlists', {
          'page': '$page',
          'page_size': '$pageSize',
        }),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return PersonalMusicPlaylistPage.fromJson(decoded);
  }

  @override
  Future<PersonalMusicPlaylist> createPersonalMusicPlaylist({
    required String name,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/me/music-box/playlists'),
        headers: _headers(token),
        body: encodeJsonBody({'name': name}),
      );
    });
    return PersonalMusicPlaylist.fromJson(
      decoded['playlist']! as Map<String, Object?>,
    );
  }

  @override
  Future<PersonalMusicPlaylistMergeResult> mergePersonalMusicPlaylists({
    required String name,
    required List<String> playlistIds,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/me/music-box/playlists/merge'),
        headers: _headers(token),
        body: encodeJsonBody({'name': name, 'playlist_ids': playlistIds}),
      );
    });
    return PersonalMusicPlaylistMergeResult.fromJson(decoded);
  }

  @override
  Future<PersonalMusicPlaylist> renamePersonalMusicPlaylist({
    required String playlistId,
    required String name,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.patch(
        _uri('/me/music-box/playlists/$playlistId'),
        headers: _headers(token),
        body: encodeJsonBody({'name': name}),
      );
    });
    return PersonalMusicPlaylist.fromJson(
      decoded['playlist']! as Map<String, Object?>,
    );
  }

  @override
  Future<void> deletePersonalMusicPlaylist(String playlistId) async {
    await _sendJson((token) {
      return _httpClient.delete(
        _uri('/me/music-box/playlists/$playlistId'),
        headers: _headers(token),
      );
    });
  }

  @override
  Future<void> pinPersonalMusicPlaylists({
    required List<String> playlistIds,
  }) async {
    await _sendJson((token) {
      return _httpClient.patch(
        _uri('/me/music-box/playlists/order'),
        headers: _headers(token),
        body: encodeJsonBody({'playlist_ids': playlistIds}),
      );
    });
  }

  @override
  Future<void> movePersonalMusicPlaylist({
    required String playlistId,
    required String direction,
  }) async {
    await _sendJson((token) {
      return _httpClient.patch(
        _uri('/me/music-box/playlists/order'),
        headers: _headers(token),
        body: encodeJsonBody({
          'playlist_id': playlistId,
          'direction': direction,
        }),
      );
    });
  }

  @override
  Future<PersonalMusicPlaylistItemsPage> getPersonalMusicPlaylist({
    required String playlistId,
    int page = 1,
    int pageSize = 50,
    String? keyword,
    String? source,
  }) async {
    final query = <String, String>{'page': '$page', 'page_size': '$pageSize'};
    if (keyword != null && keyword.trim().isNotEmpty) {
      query['keyword'] = keyword.trim();
    }
    if (source != null && source.trim().isNotEmpty) {
      query['source'] = source.trim();
    }
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/me/music-box/playlists/$playlistId', query),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return PersonalMusicPlaylistItemsPage.fromJson(decoded);
  }

  @override
  Future<List<MusicBoxSearchResult>> searchPersonalMusicPlaylistTracks({
    required String keyword,
    String? source,
    int count = 20,
    int page = 1,
  }) async {
    final query = <String, String>{
      'keyword': keyword,
      'count': '$count',
      'page': '$page',
    };
    if (source != null && source.trim().isNotEmpty) {
      query['source'] = source.trim();
    }
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/me/music-box/search', query),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return (decoded['results'] as List<Object?>? ?? const [])
        .cast<Map<String, Object?>>()
        .map(MusicBoxSearchResult.fromJson)
        .toList();
  }

  @override
  Future<DownloadedFile> downloadMusicTrackPreview({
    required String source,
    required String trackId,
  }) async {
    final response = await _sendWithAuth((token) {
      return _httpClient.post(
        _uri('/me/music-box/preview'),
        headers: _headers(token),
        body: encodeJsonBody({'source': source, 'track_id': trackId}),
      );
    });
    _throwIfFailed(response);
    return DownloadedFile(
      bytes: response.bodyBytes,
      filename: _downloadFilename(response),
      mimeType: response.headers['content-type'] ?? 'audio/mp4',
    );
  }

  @override
  Future<PersonalMusicPlaylistItem> addPersonalMusicPlaylistItem({
    required String playlistId,
    required MusicBoxSearchResult track,
    int? durationMs,
  }) async {
    final body = <String, Object?>{
      'track_id': track.trackId,
      'source': track.source,
      'title': track.name,
      'artists': track.artists,
    };
    if (durationMs != null) body['duration_ms'] = durationMs;
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/me/music-box/playlists/$playlistId/items'),
        headers: _headers(token),
        body: encodeJsonBody(body),
      );
    });
    return PersonalMusicPlaylistItem.fromJson(
      decoded['item']! as Map<String, Object?>,
    );
  }

  @override
  Future<void> deletePersonalMusicPlaylistItem({
    required String playlistId,
    required String itemId,
  }) async {
    await _sendJson((token) {
      return _httpClient.delete(
        _uri('/me/music-box/playlists/$playlistId/items/$itemId'),
        headers: _headers(token),
      );
    });
  }

  @override
  Future<void> deletePersonalMusicPlaylistItems({
    required String playlistId,
    required List<String> itemIds,
  }) async {
    await _sendJson((token) {
      return _httpClient.delete(
        _uri('/me/music-box/playlists/$playlistId/items'),
        headers: _headers(token),
        body: encodeJsonBody({'item_ids': itemIds}),
      );
    });
  }

  @override
  Future<void> movePersonalMusicPlaylistItem({
    required String playlistId,
    required String itemId,
    required String direction,
  }) async {
    await _sendJson((token) {
      return _httpClient.patch(
        _uri('/me/music-box/playlists/$playlistId/items/order'),
        headers: _headers(token),
        body: encodeJsonBody({'item_id': itemId, 'direction': direction}),
      );
    });
  }

  @override
  Future<void> reorderPersonalMusicPlaylistItems({
    required String playlistId,
    required List<String> itemIds,
  }) async {
    await _sendJson((token) {
      return _httpClient.patch(
        _uri('/me/music-box/playlists/$playlistId/items/order'),
        headers: _headers(token),
        body: encodeJsonBody({'item_ids': itemIds}),
      );
    });
  }

  @override
  Future<PersonalMusicPlaylistPage> listRoomMusicPlaylists({
    required String roomId,
    int page = 1,
    int pageSize = 50,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/rooms/$roomId/music-box/playlists', {
          'page': '$page',
          'page_size': '$pageSize',
        }),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return PersonalMusicPlaylistPage.fromJson(decoded);
  }

  @override
  Future<PersonalMusicPlaylist> createRoomMusicPlaylist({
    required String roomId,
    required String name,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/music-box/playlists'),
        headers: _headers(token),
        body: encodeJsonBody({'name': name}),
      );
    });
    return PersonalMusicPlaylist.fromJson(
      decoded['playlist']! as Map<String, Object?>,
    );
  }

  @override
  Future<PersonalMusicPlaylistMergeResult> mergeRoomMusicPlaylists({
    required String roomId,
    required String name,
    required List<String> playlistIds,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/music-box/playlists/merge'),
        headers: _headers(token),
        body: encodeJsonBody({'name': name, 'playlist_ids': playlistIds}),
      );
    });
    return PersonalMusicPlaylistMergeResult.fromJson(decoded);
  }

  @override
  Future<PersonalMusicPlaylist> createRoomMusicPlaylistFromPersonal({
    required String roomId,
    required String name,
    required String importPlaylistId,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/music-box/playlists'),
        headers: _headers(token),
        body: encodeJsonBody({
          'name': name,
          'import_playlist_id': importPlaylistId,
        }),
      );
    });
    return PersonalMusicPlaylist.fromJson(
      decoded['playlist']! as Map<String, Object?>,
    );
  }

  @override
  Future<PersonalMusicPlaylist> cloneRoomMusicPlaylistToPersonal({
    required String roomId,
    required String playlistId,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/music-box/playlists/$playlistId/clone-to-me'),
        headers: _headers(token),
      );
    });
    return PersonalMusicPlaylist.fromJson(
      decoded['playlist']! as Map<String, Object?>,
    );
  }

  @override
  Future<PersonalMusicPlaylist> renameRoomMusicPlaylist({
    required String roomId,
    required String playlistId,
    required String name,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.patch(
        _uri('/rooms/$roomId/music-box/playlists/$playlistId'),
        headers: _headers(token),
        body: encodeJsonBody({'name': name}),
      );
    });
    return PersonalMusicPlaylist.fromJson(
      decoded['playlist']! as Map<String, Object?>,
    );
  }

  @override
  Future<void> deleteRoomMusicPlaylist({
    required String roomId,
    required String playlistId,
  }) async {
    await _sendJson((token) {
      return _httpClient.delete(
        _uri('/rooms/$roomId/music-box/playlists/$playlistId'),
        headers: _headers(token),
      );
    });
  }

  @override
  Future<void> pinRoomMusicPlaylists({
    required String roomId,
    required List<String> playlistIds,
  }) async {
    await _sendJson((token) {
      return _httpClient.patch(
        _uri('/rooms/$roomId/music-box/playlists/order'),
        headers: _headers(token),
        body: encodeJsonBody({'playlist_ids': playlistIds}),
      );
    });
  }

  @override
  Future<void> moveRoomMusicPlaylist({
    required String roomId,
    required String playlistId,
    required String direction,
  }) async {
    await _sendJson((token) {
      return _httpClient.patch(
        _uri('/rooms/$roomId/music-box/playlists/order'),
        headers: _headers(token),
        body: encodeJsonBody({
          'playlist_id': playlistId,
          'direction': direction,
        }),
      );
    });
  }

  @override
  Future<PersonalMusicPlaylistItemsPage> getRoomMusicPlaylist({
    required String roomId,
    required String playlistId,
    int page = 1,
    int pageSize = 50,
    String? keyword,
    String? source,
  }) async {
    final query = <String, String>{'page': '$page', 'page_size': '$pageSize'};
    if (keyword != null && keyword.trim().isNotEmpty) {
      query['keyword'] = keyword.trim();
    }
    if (source != null && source.trim().isNotEmpty) {
      query['source'] = source.trim();
    }
    final decoded = await _sendJson((token) {
      return _httpClient.get(
        _uri('/rooms/$roomId/music-box/playlists/$playlistId', query),
        headers: _headers(token),
      );
    }, retryTransientFailures: true);
    return PersonalMusicPlaylistItemsPage.fromJson(decoded);
  }

  @override
  Future<PersonalMusicPlaylistItem> addRoomMusicPlaylistItem({
    required String roomId,
    required String playlistId,
    required MusicBoxSearchResult track,
    int? durationMs,
  }) async {
    final body = <String, Object?>{
      'track_id': track.trackId,
      'source': track.source,
      'title': track.name,
      'artists': track.artists,
    };
    if (durationMs != null) body['duration_ms'] = durationMs;
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/music-box/playlists/$playlistId/items'),
        headers: _headers(token),
        body: encodeJsonBody(body),
      );
    });
    return PersonalMusicPlaylistItem.fromJson(
      decoded['item']! as Map<String, Object?>,
    );
  }

  @override
  Future<void> deleteRoomMusicPlaylistItem({
    required String roomId,
    required String playlistId,
    required String itemId,
  }) async {
    await _sendJson((token) {
      return _httpClient.delete(
        _uri('/rooms/$roomId/music-box/playlists/$playlistId/items/$itemId'),
        headers: _headers(token),
      );
    });
  }

  @override
  Future<void> deleteRoomMusicPlaylistItems({
    required String roomId,
    required String playlistId,
    required List<String> itemIds,
  }) async {
    await _sendJson((token) {
      return _httpClient.delete(
        _uri('/rooms/$roomId/music-box/playlists/$playlistId/items'),
        headers: _headers(token),
        body: encodeJsonBody({'item_ids': itemIds}),
      );
    });
  }

  @override
  Future<void> moveRoomMusicPlaylistItem({
    required String roomId,
    required String playlistId,
    required String itemId,
    required String direction,
  }) async {
    await _sendJson((token) {
      return _httpClient.patch(
        _uri('/rooms/$roomId/music-box/playlists/$playlistId/items/order'),
        headers: _headers(token),
        body: encodeJsonBody({'item_id': itemId, 'direction': direction}),
      );
    });
  }

  @override
  Future<void> reorderRoomMusicPlaylistItems({
    required String roomId,
    required String playlistId,
    required List<String> itemIds,
  }) async {
    await _sendJson((token) {
      return _httpClient.patch(
        _uri('/rooms/$roomId/music-box/playlists/$playlistId/items/order'),
        headers: _headers(token),
        body: encodeJsonBody({'item_ids': itemIds}),
      );
    });
  }

  @override
  Future<MusicBoxState> queueMusicBoxTrack({
    required String roomId,
    required String trackId,
    required String title,
    String? source,
    String? artist,
    int? durationMs,
    String? idempotencyKey,
  }) async {
    final body = <String, Object?>{'track_id': trackId, 'title': title};
    if (source != null && source.isNotEmpty) body['source'] = source;
    if (artist != null && artist.isNotEmpty) body['artist'] = artist;
    if (durationMs != null) body['duration_ms'] = durationMs;
    final requestIdempotencyKey = idempotencyKey ?? newUuid();
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/music-box/queue'),
        headers: _headers(token, idempotencyKey: requestIdempotencyKey),
        body: encodeJsonBody(body),
      );
    });
    return MusicBoxState.fromJson(decoded);
  }

  @override
  Future<MusicBoxState> removeMusicBoxItem({
    required String roomId,
    required String itemId,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.delete(
        _uri('/rooms/$roomId/music-box/queue/$itemId'),
        headers: _headers(token),
      );
    });
    return MusicBoxState.fromJson(decoded);
  }

  @override
  Future<MusicBoxState> controlMusicBox({
    required String roomId,
    required String action,
    String? itemId,
    String? mode,
    String? commandId,
    int? expectedRevision,
  }) async {
    final decoded = await _sendJson((token) {
      final body = <String, Object?>{'action': action};
      if (itemId != null && itemId.trim().isNotEmpty) {
        body['item_id'] = itemId.trim();
      }
      if (mode != null && mode.trim().isNotEmpty) {
        body['mode'] = mode.trim();
      }
      if (commandId != null && commandId.trim().isNotEmpty) {
        body['command_id'] = commandId.trim();
      }
      if (expectedRevision != null) {
        body['expected_revision'] = expectedRevision;
      }
      return _httpClient.post(
        _uri('/rooms/$roomId/music-box/control'),
        headers: _headers(token),
        body: encodeJsonBody(body),
      );
    });
    return MusicBoxState.fromJson(decoded);
  }

  @override
  Future<MusicBoxState> activateMusicBoxPlaylist({
    required String roomId,
    required MusicBoxActiveSourceType sourceType,
    String? playlistId,
    bool startPlay = true,
  }) async {
    final body = <String, Object?>{
      'source_type': musicBoxActiveSourceTypeValue(sourceType),
      'start_play': startPlay,
    };
    if (playlistId != null && playlistId.trim().isNotEmpty) {
      body['playlist_id'] = playlistId.trim();
    }
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/music-box/activate'),
        headers: _headers(token),
        body: encodeJsonBody(body),
      );
    });
    return MusicBoxState.fromJson(decoded);
  }

  @override
  Future<PersonalMusicPlaylist> cloneActiveMusicBoxPlaylist({
    required String roomId,
    required String playlistId,
    required String snapshotId,
  }) async {
    final decoded = await _sendJson((token) {
      return _httpClient.post(
        _uri('/rooms/$roomId/music-box/active-playlist/clone'),
        headers: _headers(token),
        body: encodeJsonBody({
          'playlist_id': playlistId,
          'snapshot_id': snapshotId,
        }),
      );
    });
    return PersonalMusicPlaylist.fromJson(
      decoded['playlist']! as Map<String, Object?>,
    );
  }

  Future<Map<String, Object?>> _sendJson(
    Future<http.Response> Function(String accessToken) send, {
    bool retryTransientFailures = false,
  }) async {
    final decoded = await _sendJsonValue(
      send,
      retryTransientFailures: retryTransientFailures,
    );
    return decoded as Map<String, Object?>;
  }

  Future<Object?> _sendJsonValue(
    Future<http.Response> Function(String accessToken) send, {
    bool retryTransientFailures = false,
  }) async {
    http.Response response;
    try {
      response = await _sendWithAuth(send);
    } on http.ClientException catch (e) {
      if (!retryTransientFailures || !_isRetryableTransportFailure(e)) {
        rethrow;
      }
      response = await _sendWithAuth(send);
    }
    if (retryTransientFailures &&
        _isRetryableHttpFailure(response.statusCode)) {
      response = await _sendWithAuth(send);
    }
    _throwIfFailed(response);
    final decoded = decodeJsonBody(response);
    return decoded ?? {};
  }

  Future<http.Response> _sendWithAuth(
    Future<http.Response> Function(String accessToken) send,
  ) async {
    var token = await accessTokenProvider();
    final stopwatch = Stopwatch()..start();
    var response = await send(token);
    if (response.statusCode == 401) {
      token = await accessTokenProvider(forceRefresh: true);
      response = await send(token);
    }
    stopwatch.stop();
    onRequestLatency?.call(stopwatch.elapsed);
    onServerTime?.call(response.headers[gangServerTimeHeader]);
    return response;
  }

  bool _isRetryableTransportFailure(http.ClientException error) {
    final message = error.message.toLowerCase();
    return message.contains('connection closed') ||
        message.contains('connection reset') ||
        message.contains('broken pipe') ||
        message.contains('write failed') ||
        message.contains('connection abort') ||
        message.contains('errno = 10053') ||
        message.contains('errno=10053');
  }

  bool _isRetryableHttpFailure(int statusCode) {
    return statusCode == 502 || statusCode == 503 || statusCode == 504;
  }

  void _throwIfFailed(http.Response response) {
    if (response.statusCode >= 200 && response.statusCode < 300) return;
    throw ApiException.fromResponse(response);
  }

  Uri _uri(String path, [Map<String, String>? query]) {
    final normalizedBase = baseUrl.endsWith('/')
        ? baseUrl.substring(0, baseUrl.length - 1)
        : baseUrl;
    final uri = Uri.parse('$normalizedBase$path');
    if (query == null || query.isEmpty) return uri;
    return uri.replace(queryParameters: query);
  }

  Map<String, String> _headers(String accessToken, {String? idempotencyKey}) {
    final headers = {
      'authorization': 'Bearer $accessToken',
      'accept': jsonAcceptHeader,
      'content-type': jsonUtf8ContentType,
    };
    if (idempotencyKey != null) headers['idempotency-key'] = idempotencyKey;
    return headers;
  }

  String _downloadFilename(http.Response response) {
    final disposition = response.headers['content-disposition'] ?? '';
    final encoded = RegExp(
      r"filename\*=UTF-8''([^;]+)",
      caseSensitive: false,
    ).firstMatch(disposition);
    if (encoded != null) {
      return Uri.decodeComponent(encoded.group(1)!);
    }
    final quoted = RegExp(r'filename="([^"]+)"').firstMatch(disposition);
    if (quoted != null) return quoted.group(1)!;
    final unquoted = RegExp(r'filename=([^;]+)').firstMatch(disposition);
    if (unquoted != null) return unquoted.group(1)!.trim();
    final mimeType = response.headers['content-type'] ?? '';
    return mimeType.contains('zip') ? 'stickers.zip' : 'sticker';
  }

  @override
  void close() => _httpClient.close();
}

class RoomPage {
  const RoomPage({required this.rooms, required this.nextCursor});

  final List<RoomCard> rooms;
  final String? nextCursor;

  factory RoomPage.fromJson(Map<String, Object?> json) {
    return RoomPage(
      rooms: (json['rooms']! as List<Object?>)
          .cast<Map<String, Object?>>()
          .map(RoomCard.fromJson)
          .toList(),
      nextCursor: json['next_cursor'] as String?,
    );
  }
}

class MessagePage {
  const MessagePage({
    required this.messages,
    required this.hasMore,
    required this.nextBefore,
  });

  final List<Message> messages;
  final bool hasMore;
  final String? nextBefore;

  factory MessagePage.fromJson(Map<String, Object?> json) {
    return MessagePage(
      messages: (json['messages']! as List<Object?>)
          .cast<Map<String, Object?>>()
          .map(Message.fromJson)
          .toList(),
      hasMore: json['has_more']! as bool,
      nextBefore: json['next_before'] as String?,
    );
  }
}

class ApiException implements Exception {
  ApiException(
    this.message, {
    required this.statusCode,
    required this.code,
    required this.requestId,
  });

  final String message;
  final int statusCode;
  final String code;
  final String? requestId;

  factory ApiException.fromResponse(http.Response response) {
    final headerRequestId = response.headers['x-request-id'];
    try {
      final decoded = decodeJsonBody(response) as Map<String, Object?>;
      final error = decoded['error'] as Map<String, Object?>?;
      final message = error?['message'] as String?;
      final code = error?['code'] as String?;
      final requestId = error?['request_id'] as String? ?? headerRequestId;
      if (message != null && message.isNotEmpty) {
        return ApiException(
          localizedServerErrorMessage(
            code: code ?? 'request_failed',
            statusCode: response.statusCode,
            message: message,
          ),
          statusCode: response.statusCode,
          code: code ?? 'request_failed',
          requestId: requestId,
        );
      }
    } catch (_) {
      // Fall through to the status-based message.
    }
    return ApiException(
      '请求失败 (${response.statusCode})',
      statusCode: response.statusCode,
      code: 'request_failed',
      requestId: headerRequestId,
    );
  }

  bool get isUnauthorized => statusCode == 401;

  @override
  String toString() {
    if (requestId == null) return message;
    return '$message（请求编号：$requestId）';
  }
}

Stream<List<int>> _uploadByteStream(
  Uint8List bytes, {
  UploadProgressCallback? onProgress,
  UploadTransferController? controller,
}) async* {
  const chunkSize = 64 * 1024;
  final total = bytes.length;
  onProgress?.call(sentBytes: 0, totalBytes: total);
  var sent = 0;
  while (sent < total) {
    if (controller?.isCancelled ?? false) {
      throw const UploadCancelledException();
    }
    await controller?.waitIfPaused();
    if (controller?.isCancelled ?? false) {
      throw const UploadCancelledException();
    }
    final end = (sent + chunkSize) > total ? total : sent + chunkSize;
    yield Uint8List.sublistView(bytes, sent, end);
    sent = end;
    onProgress?.call(sentBytes: sent, totalBytes: total);
  }
}

String newClientId(String prefix) => '${prefix}_${newUuid()}';

String newUuid() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;

  String hex(int byte) => byte.toRadixString(16).padLeft(2, '0');
  final value = bytes.map(hex).join();
  return [
    value.substring(0, 8),
    value.substring(8, 12),
    value.substring(12, 16),
    value.substring(16, 20),
    value.substring(20),
  ].join('-');
}
