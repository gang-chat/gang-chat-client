package com.gangchat.client.protocol

import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.Stopwatch
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.complete
import com.gangchat.client.foundation.delayed
import com.gangchat.client.foundation.http.ByteStream
import com.gangchat.client.foundation.http.ClientException
import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.http.MultipartFile
import com.gangchat.client.foundation.http.MultipartRequest
import com.gangchat.client.foundation.http.delete
import com.gangchat.client.foundation.http.get
import com.gangchat.client.foundation.http.patch
import com.gangchat.client.foundation.http.post
import com.gangchat.client.foundation.http.put
import java.security.SecureRandom
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.flow

/**
 * Dart's `typedef AccessTokenProvider = Future<String> Function({bool forceRefresh})`.
 *
 * Call it like the Dart function: `accessTokenProvider()` or
 * `accessTokenProvider(forceRefresh = true)`; build one from a lambda:
 * `AccessTokenProvider { forceRefresh -> token }`.
 */
fun interface AccessTokenProvider {
    suspend fun provide(forceRefresh: Boolean): String

    suspend operator fun invoke(forceRefresh: Boolean = false): String = provide(forceRefresh)
}

typealias ServerTimeCallback = (value: String?) -> Unit

typealias RequestLatencyCallback = (value: Duration) -> Unit

/**
 * Dart's `typedef UploadProgressCallback = void Function({required int
 * sentBytes, required int totalBytes})`; invoke with named arguments.
 */
fun interface UploadProgressCallback {
    operator fun invoke(sentBytes: Long, totalBytes: Long)
}

class DownloadedFile(
    val bytes: ByteArray,
    val filename: String,
    val mimeType: String,
)

class UploadTransferController {
    private var _resumeCompleter: Completer<Unit>? = null
    private var _cancelled = false

    val isPaused: Boolean get() = _resumeCompleter != null
    val isCancelled: Boolean get() = _cancelled

    fun pause() {
        if (_cancelled || _resumeCompleter != null) return
        _resumeCompleter = Completer()
    }

    fun resume() {
        val completer = _resumeCompleter ?: return
        _resumeCompleter = null
        if (!completer.isCompleted) completer.complete()
    }

    fun cancel() {
        if (_cancelled) return
        _cancelled = true
        resume()
    }

    suspend fun waitIfPaused() {
        while (!_cancelled) {
            val completer = _resumeCompleter ?: return
            completer.future.await()
        }
    }
}

class UploadCancelledException : Exception("上传已取消") {
    override fun toString(): String = "上传已取消"
}

interface GangApi {
    suspend fun getAppVersion(): AppVersionInfo

    suspend fun me(): CurrentUser

    suspend fun upsertPushDevice(
        provider: String,
        installationId: String,
        token: String,
        enabled: Boolean,
    )

    suspend fun updatePushDeviceEnabled(
        provider: String,
        installationId: String,
        enabled: Boolean,
    )

    suspend fun deletePushDevice(
        provider: String,
        installationId: String,
    )

    suspend fun updateAccount(
        username: String? = null,
        email: String? = null,
        emailVerificationToken: String? = null,
        emailPublic: Boolean? = null,
        phoneNumber: String? = null,
        phoneNumberPublic: Boolean? = null,
        language: String? = null,
    ): CurrentUser

    suspend fun updateProfile(
        displayName: String? = null,
        bio: String? = null,
        gender: String? = null,
        avatarAssetId: String? = null,
        defaultAvatarKey: String? = null,
    ): CurrentUser

    suspend fun uploadImageAsset(
        bytes: ByteArray,
        filename: String,
        purpose: String = "image",
    ): UploadedAsset

    suspend fun uploadFileAsset(
        bytes: ByteArray,
        filename: String,
        purpose: String = "message_file",
        onProgress: UploadProgressCallback? = null,
        controller: UploadTransferController? = null,
    ): UploadedAsset

    suspend fun listStickerPacks(
        scope: String = "personal",
        roomId: String? = null,
        ownerUserId: String? = null,
    ): List<StickerPack>

    suspend fun createStickerPack(
        name: String,
        scope: String = "personal",
        roomId: String? = null,
        ownerUserId: String? = null,
        sortOrder: Int? = null,
    ): StickerPack

    suspend fun updateStickerPack(
        packId: String,
        name: String? = null,
        sortOrder: Int? = null,
    ): StickerPack

    suspend fun deleteStickerPack(packId: String)

    suspend fun addSticker(
        packId: String,
        assetId: String,
        name: String,
        sortOrder: Int? = null,
        idempotencyKey: String? = null,
        scope: String = "personal",
        roomId: String? = null,
        ownerUserId: String? = null,
    )

    suspend fun deleteSticker(
        packId: String,
        stickerId: String,
        scope: String = "personal",
        roomId: String? = null,
        ownerUserId: String? = null,
    )

    suspend fun updateSticker(
        packId: String,
        stickerId: String,
        name: String? = null,
        sortOrder: Int? = null,
    ): Sticker

    suspend fun reorderStickers(
        packId: String,
        stickerIds: List<String>,
    ): StickerPack

    suspend fun downloadStickers(stickerIds: List<String>): DownloadedFile

    suspend fun saveSticker(
        roomId: String,
        stickerId: String,
        sourceMessageId: String? = null,
        targetScope: String = "personal",
        targetPackId: String? = null,
        name: String? = null,
        sortOrder: Int? = null,
    ): StickerPack

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        revokeOtherSessions: Boolean = true,
    )

    suspend fun listSessions(): List<UserSession>

    suspend fun deleteMyAccount(confirm: Boolean)

    suspend fun listRooms(limit: Int = 50, cursor: String? = null): RoomPage

    suspend fun createRoom(
        name: String,
        description: String? = null,
        visibility: String? = null,
        joinPolicy: String? = null,
        aiVoiceAnnouncementsEnabled: Boolean? = null,
        avatarAssetId: String? = null,
        defaultAvatarKey: String? = null,
        idempotencyKey: String? = null,
    ): RoomDetail

    suspend fun getRoom(roomId: String): RoomDetail

    suspend fun updateRoom(
        roomId: String,
        name: String? = null,
        description: String? = null,
        visibility: String? = null,
        joinPolicy: String? = null,
        aiVoiceAnnouncementsEnabled: Boolean? = null,
        gangBotEnabled: Boolean? = null,
        avatarAssetId: String? = null,
        defaultAvatarKey: String? = null,
    ): RoomDetail

    suspend fun updateMyRoomSettings(
        roomId: String,
        remarkName: String? = null,
        notificationPolicy: String? = null,
        roomDisplayName: String? = null,
        isPinned: Boolean? = null,
        aiVoiceAnnouncementsEnabled: Boolean? = null,
    ): RoomDetail

    suspend fun leaveRoom(
        roomId: String,
        confirmDeleteIfEmpty: Boolean = false,
    )

    suspend fun deleteRoom(
        roomId: String,
        confirmName: String,
    )

    suspend fun updateRoomMemberRole(
        roomId: String,
        userId: String,
        role: String,
    ): RoomMember

    suspend fun updateRoomMemberRoomDisplayName(
        roomId: String,
        userId: String,
        roomDisplayName: String,
    ): RoomMember

    suspend fun removeRoomMember(
        roomId: String,
        userId: String,
    )

    suspend fun transferRoomCreator(
        roomId: String,
        userId: String,
    ): RoomDetail

    suspend fun searchRooms(query: String, limit: Int = 20): List<PublicRoom>

    suspend fun search(
        query: String,
        limit: Int = 8,
        categories: Iterable<String>? = null,
        myRoomsCursor: String? = null,
        publicRoomsCursor: String? = null,
        messagesCursor: String? = null,
        filesCursor: String? = null,
    ): GlobalSearchResults

    suspend fun joinRoom(roomId: String, reason: String? = null): JoinRoomResult

    suspend fun listRoomMembers(
        roomId: String,
        limit: Int = 100,
        cursor: String? = null,
    ): RoomMemberPage

    suspend fun getRoomMemberProfile(
        roomId: String,
        userId: String,
    ): RoomMemberProfile

    suspend fun getUserProfile(userId: String): UserSummary

    suspend fun inviteMember(
        roomId: String,
        userId: String,
    ): RoomInvite

    suspend fun listRoomBlacklist(roomId: String): List<RoomBlacklistEntry>

    suspend fun blockRoomUser(
        roomId: String,
        userId: String,
    ): RoomBlacklistEntry

    suspend fun unblockRoomUser(
        roomId: String,
        userId: String,
    )

    suspend fun listRoomInvites(status: String = "pending"): List<RoomInvite>

    suspend fun reviewRoomInvite(
        inviteId: String,
        accept: Boolean,
        reason: String? = null,
    ): JoinRoomResult

    suspend fun listRoomApplications(
        status: String = "pending",
    ): List<RoomApplication>

    suspend fun withdrawRoomApplication(requestId: String): RoomApplication

    suspend fun listRoomNotifications(): List<RoomEventNotification>

    suspend fun deleteRoomNotification(
        notificationType: String,
        notificationId: String,
    )

    suspend fun markRoomNotificationsRead()

    suspend fun searchUsers(
        query: String,
        limit: Int = 20,
    ): List<UserSummary>

    suspend fun searchUsersPage(
        query: String,
        limit: Int = 20,
        cursor: String? = null,
        includeSuspended: Boolean = false,
    ): UserSearchPage

    suspend fun getForcedUserSettings(userId: String): CurrentUser

    suspend fun listForcedUserSessions(userId: String): List<UserSession>

    suspend fun forceDeleteUserAccount(userId: String)

    suspend fun updateForcedUserSettings(
        userId: String,
        username: String? = null,
        email: String? = null,
        emailVerified: Boolean? = null,
        emailPublic: Boolean? = null,
        phoneNumber: String? = null,
        phoneNumberPublic: Boolean? = null,
        displayName: String? = null,
        bio: String? = null,
        gender: String? = null,
        avatarAssetId: String? = null,
        defaultAvatarKey: String? = null,
        language: String? = null,
        status: String? = null,
    ): CurrentUser

    suspend fun getForcedUserAudioSettings(userId: String): UserAudioSettings

    suspend fun updateForcedUserAudioSettings(
        userId: String,
        settings: UserAudioSettings,
    ): UserAudioSettings

    suspend fun forceResetUserPassword(
        userId: String,
        newPassword: String,
    )

    suspend fun listJoinRequests(
        roomId: String,
        status: String = "pending",
    ): List<JoinRequest>

    suspend fun reviewJoinRequest(
        roomId: String,
        requestId: String,
        approve: Boolean,
    )

    suspend fun listMessages(
        roomId: String,
        limit: Int = 50,
        before: String? = null,
    ): MessagePage

    suspend fun listMessageHistory(
        roomId: String,
        query: String = "",
        category: String = "all",
        senderUserId: String? = null,
        startAt: DateTime? = null,
        endAt: DateTime? = null,
        limit: Int = 50,
        before: String? = null,
    ): MessagePage

    /**
     * Hides message records only for the current account's room-history view.
     * It does not recall or force-delete the underlying messages.
     */
    suspend fun hideMessageHistory(
        roomId: String,
        messageIds: List<String>,
    ): Int

    suspend fun sendMessage(
        roomId: String,
        clientMessageId: String,
        body: String,
        type: String = "text",
        attachments: List<MessageAttachment> = emptyList(),
        mentions: List<JsonMap> = emptyList(),
        quoteMessageId: String? = null,
        quoteMessageIds: List<String> = emptyList(),
        idempotencyKey: String? = null,
    ): Message

    suspend fun markRead(
        roomId: String,
        lastReadMessageId: String,
    ): Int

    /**
     * Recalls a message. Succeeds immediately for the sender (within the room's
     * recall policy/window) or an admin; under `admin_approval` a non-admin's
     * request is queued for review instead (see [MessageRecallResult.isPending]).
     */
    suspend fun recallMessage(
        roomId: String,
        messageId: String,
        reason: String? = null,
    ): MessageRecallResult

    /** Lists pending message-recall requests awaiting admin review. */
    suspend fun listMessageRecallRequests(
        roomId: String,
        status: String = "pending",
    ): List<MessageRecallRequest>

    /** Approves or rejects a pending message-recall request (admin only). */
    suspend fun reviewMessageRecallRequest(
        roomId: String,
        requestId: String,
        approve: Boolean,
        reason: String? = null,
    )

    /**
     * Force-deletes a message as a moderation action (admin only). Distinct from
     * a recall; requires explicit confirmation server-side.
     */
    suspend fun forceDeleteMessage(
        roomId: String,
        messageId: String,
        reason: String? = null,
    ): Message

    /** Lists the caller's own per-member volume overrides for a live room. */
    suspend fun listMyLiveMemberVolumes(roomId: String): List<LiveMemberVolume>

    /**
     * Sets how loudly the caller hears [targetUserId] in [roomId]'s live
     * session. [volume] is clamped 0–100 server-side.
     */
    suspend fun updateMyLiveMemberVolume(
        roomId: String,
        targetUserId: String,
        volume: Int,
    ): LiveMemberVolume

    /**
     * Applies an admin moderation action to a live participant. [action] is one
     * of `kick`, `mute_mic`, `block_voice`, `restore_voice`,
     * `restore_headphones`.
     */
    suspend fun moderateLiveParticipant(
        roomId: String,
        userId: String,
        action: String,
        reason: String? = null,
    )

    suspend fun getLiveState(roomId: String): LiveState

    suspend fun joinLive(
        roomId: String,
        clientLiveSessionId: String,
        source: String,
        micMuted: Boolean,
        headphonesMuted: Boolean,
        idempotencyKey: String? = null,
    ): LiveJoinResult

    suspend fun updateMyLiveState(
        roomId: String,
        micMuted: Boolean? = null,
        headphonesMuted: Boolean? = null,
        cameraOn: Boolean? = null,
        cameraMirrored: Boolean? = null,
        screenSharing: Boolean? = null,
        connectionState: String? = null,
    ): LiveParticipant

    suspend fun updateMyLiveScreenView(
        roomId: String,
        broadcasterUserId: String? = null,
    )

    /**
     * Issues a publish-only LiveKit token for the caller's hidden screen-audio
     * aux participant (identity `<userId>--screen-audio`). Fetched on demand when
     * the user starts screen-share-with-audio, so the token is always fresh
     * regardless of how long they have been in the call.
     */
    suspend fun issueScreenAudioToken(roomId: String): ScreenAudioToken

    suspend fun getMusicBoxState(roomId: String): MusicBoxState

    suspend fun searchMusicBox(
        roomId: String,
        keyword: String,
        source: String? = null,
        count: Int? = null,
        page: Int? = null,
    ): List<MusicBoxSearchResult>

    suspend fun queueMusicBoxTrack(
        roomId: String,
        trackId: String,
        title: String,
        source: String? = null,
        artist: String? = null,
        durationMs: Int? = null,
        idempotencyKey: String? = null,
    ): MusicBoxState

    suspend fun removeMusicBoxItem(
        roomId: String,
        itemId: String,
    ): MusicBoxState

    suspend fun controlMusicBox(
        roomId: String,
        action: String,
        itemId: String? = null,
        mode: String? = null,
        commandId: String? = null,
        expectedRevision: Int? = null,
    ): MusicBoxState

    suspend fun activateMusicBoxPlaylist(
        roomId: String,
        sourceType: MusicBoxActiveSourceType,
        playlistId: String? = null,
        startPlay: Boolean = true,
        startItemId: String? = null,
    ): MusicBoxState

    fun close()
}

interface PersonalMusicPlaylistApi {
    suspend fun listPersonalMusicPlaylists(
        page: Int = 1,
        pageSize: Int = 50,
    ): PersonalMusicPlaylistPage

    suspend fun createPersonalMusicPlaylist(name: String): PersonalMusicPlaylist

    suspend fun renamePersonalMusicPlaylist(
        playlistId: String,
        name: String,
    ): PersonalMusicPlaylist

    suspend fun deletePersonalMusicPlaylist(playlistId: String)

    suspend fun pinPersonalMusicPlaylists(playlistIds: List<String>)

    suspend fun movePersonalMusicPlaylist(
        playlistId: String,
        direction: String,
    )

    suspend fun getPersonalMusicPlaylist(
        playlistId: String,
        page: Int = 1,
        pageSize: Int = 50,
        keyword: String? = null,
        source: String? = null,
    ): PersonalMusicPlaylistItemsPage

    suspend fun searchPersonalMusicPlaylistTracks(
        keyword: String,
        source: String? = null,
        count: Int = 20,
        page: Int = 1,
    ): List<MusicBoxSearchResult>

    suspend fun addPersonalMusicPlaylistItem(
        playlistId: String,
        track: MusicBoxSearchResult,
        durationMs: Int? = null,
    ): PersonalMusicPlaylistItem

    suspend fun deletePersonalMusicPlaylistItem(
        playlistId: String,
        itemId: String,
    )

    suspend fun deletePersonalMusicPlaylistItems(
        playlistId: String,
        itemIds: List<String>,
    )

    suspend fun movePersonalMusicPlaylistItem(
        playlistId: String,
        itemId: String,
        direction: String,
    )

    suspend fun reorderPersonalMusicPlaylistItems(
        playlistId: String,
        itemIds: List<String>,
    )
}

/**
 * Optional extension for atomically merging selected personal playlists into
 * a new playlist while consuming the merged source prefixes.
 */
interface PersonalMusicPlaylistMergeApi {
    suspend fun mergePersonalMusicPlaylists(
        name: String,
        playlistIds: List<String>,
    ): PersonalMusicPlaylistMergeResult
}

/**
 * Optional extension for atomically copying selected saved songs into
 * another personal playlist without mutating the source playlist.
 */
interface PersonalMusicPlaylistBatchAddApi {
    suspend fun batchAddPersonalMusicPlaylistItems(
        sourcePlaylistId: String,
        targetPlaylistId: String,
        itemIds: List<String>,
    ): PersonalMusicPlaylistBatchAddResult
}

/**
 * Authenticated transport for an explicitly initiated local song preview.
 * This is separate from [GangApi] so older fakes and read-only playlist
 * adapters do not gain a required method when preview support is unavailable.
 */
interface MusicTrackPreviewApi {
    suspend fun downloadMusicTrackPreview(
        source: String,
        trackId: String,
    ): DownloadedFile
}

/**
 * Clones the immutable saved-playlist snapshot that is currently active in a
 * room. Kept separate from [GangApi] so older fakes remain source-compatible.
 */
interface MusicBoxActivePlaylistCloneApi {
    suspend fun cloneActiveMusicBoxPlaylist(
        roomId: String,
        playlistId: String,
        snapshotId: String,
    ): PersonalMusicPlaylist
}

interface RoomMusicPlaylistApi {
    suspend fun listRoomMusicPlaylists(
        roomId: String,
        page: Int = 1,
        pageSize: Int = 50,
    ): PersonalMusicPlaylistPage

    suspend fun createRoomMusicPlaylist(
        roomId: String,
        name: String,
    ): PersonalMusicPlaylist

    suspend fun renameRoomMusicPlaylist(
        roomId: String,
        playlistId: String,
        name: String,
    ): PersonalMusicPlaylist

    suspend fun deleteRoomMusicPlaylist(
        roomId: String,
        playlistId: String,
    )

    suspend fun pinRoomMusicPlaylists(
        roomId: String,
        playlistIds: List<String>,
    )

    suspend fun moveRoomMusicPlaylist(
        roomId: String,
        playlistId: String,
        direction: String,
    )

    suspend fun getRoomMusicPlaylist(
        roomId: String,
        playlistId: String,
        page: Int = 1,
        pageSize: Int = 50,
        keyword: String? = null,
        source: String? = null,
    ): PersonalMusicPlaylistItemsPage

    suspend fun addRoomMusicPlaylistItem(
        roomId: String,
        playlistId: String,
        track: MusicBoxSearchResult,
        durationMs: Int? = null,
    ): PersonalMusicPlaylistItem

    suspend fun deleteRoomMusicPlaylistItem(
        roomId: String,
        playlistId: String,
        itemId: String,
    )

    suspend fun deleteRoomMusicPlaylistItems(
        roomId: String,
        playlistId: String,
        itemIds: List<String>,
    )

    suspend fun moveRoomMusicPlaylistItem(
        roomId: String,
        playlistId: String,
        itemId: String,
        direction: String,
    )

    suspend fun reorderRoomMusicPlaylistItems(
        roomId: String,
        playlistId: String,
        itemIds: List<String>,
    )
}

/** Optional room-scoped counterpart of [PersonalMusicPlaylistMergeApi]. */
interface RoomMusicPlaylistMergeApi {
    suspend fun mergeRoomMusicPlaylists(
        roomId: String,
        name: String,
        playlistIds: List<String>,
    ): PersonalMusicPlaylistMergeResult
}

/** Room-scoped counterpart of [PersonalMusicPlaylistBatchAddApi]. */
interface RoomMusicPlaylistBatchAddApi {
    suspend fun batchAddRoomMusicPlaylistItems(
        roomId: String,
        sourcePlaylistId: String,
        targetPlaylistId: String,
        itemIds: List<String>,
    ): PersonalMusicPlaylistBatchAddResult
}

/**
 * Optional room-playlist extension that atomically imports one of the
 * authenticated user's personal playlists while creating the room playlist.
 * Kept separate so existing room playlist fakes and older adapters remain
 * source-compatible.
 */
interface RoomMusicPlaylistImportApi {
    suspend fun createRoomMusicPlaylistFromPersonal(
        roomId: String,
        name: String,
        importPlaylistId: String,
    ): PersonalMusicPlaylist
}

/**
 * Optional extension for atomically cloning one room playlist into the
 * authenticated user's personal library.
 */
interface RoomMusicPlaylistCloneApi {
    suspend fun cloneRoomMusicPlaylistToPersonal(
        roomId: String,
        playlistId: String,
    ): PersonalMusicPlaylist
}

/**
 * Optional extension for atomically cloning an immutable playlist embedded in
 * a room message into the authenticated user's personal library.
 */
interface SharedMusicPlaylistCloneApi {
    suspend fun cloneSharedMusicPlaylistToPersonal(
        roomId: String,
        messageId: String,
    ): PersonalMusicPlaylist
}

class GangApiClient(
    val baseUrl: String,
    val accessTokenProvider: AccessTokenProvider,
    httpClient: HttpClient? = null,
    val onServerTime: ServerTimeCallback? = null,
    val onRequestLatency: RequestLatencyCallback? = null,
) : GangApi,
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
    MusicBoxActivePlaylistCloneApi {
    private val _httpClient: HttpClient = httpClient ?: HttpClient()

    override suspend fun getAppVersion(): AppVersionInfo {
        val decoded =
            _sendJson { token ->
                _httpClient.get(_uri("/app/version"), headers = _headers(token))
            }
        return AppVersionInfo.fromJson(decoded)
    }

    override suspend fun me(): CurrentUser {
        val decoded =
            _sendJson { token ->
                _httpClient.get(_uri("/me"), headers = _headers(token))
            }
        return CurrentUser.fromJson(decoded)
    }

    override suspend fun upsertPushDevice(
        provider: String,
        installationId: String,
        token: String,
        enabled: Boolean,
    ) {
        val providerPath = Uri.encodeComponent(provider)
        val installationPath = Uri.encodeComponent(installationId)
        _sendJson { accessToken ->
            _httpClient.put(
                _uri("/me/push-devices/$providerPath/$installationPath"),
                headers = _headers(accessToken),
                body =
                    encodeJsonBody(
                        linkedMapOf(
                            "provider" to provider,
                            "installation_id" to installationId,
                            "token" to token,
                            "platform" to "android",
                            "enabled" to enabled,
                        ),
                    ),
            )
        }
    }

    override suspend fun updatePushDeviceEnabled(
        provider: String,
        installationId: String,
        enabled: Boolean,
    ) {
        val providerPath = Uri.encodeComponent(provider)
        val installationPath = Uri.encodeComponent(installationId)
        _sendJson { accessToken ->
            _httpClient.patch(
                _uri("/me/push-devices/$providerPath/$installationPath"),
                headers = _headers(accessToken),
                body = encodeJsonBody(mapOf("enabled" to enabled)),
            )
        }
    }

    override suspend fun deletePushDevice(
        provider: String,
        installationId: String,
    ) {
        val providerPath = Uri.encodeComponent(provider)
        val installationPath = Uri.encodeComponent(installationId)
        _sendJson { accessToken ->
            _httpClient.delete(
                _uri("/me/push-devices/$providerPath/$installationPath"),
                headers = _headers(accessToken),
            )
        }
    }

    override suspend fun updateAccount(
        username: String?,
        email: String?,
        emailVerificationToken: String?,
        emailPublic: Boolean?,
        phoneNumber: String?,
        phoneNumberPublic: Boolean?,
        language: String?,
    ): CurrentUser {
        val body = LinkedHashMap<String, Any?>()
        if (username != null) body["username"] = username
        if (email != null) body["email"] = email
        if (emailVerificationToken != null) {
            body["email_verification_token"] = emailVerificationToken
        }
        if (emailPublic != null) body["email_public"] = emailPublic
        if (phoneNumber != null) body["phone_number"] = phoneNumber
        if (phoneNumberPublic != null) {
            body["phone_number_public"] = phoneNumberPublic
        }
        if (language != null) body["language"] = language
        val decoded =
            _sendJson { token ->
                _httpClient.patch(
                    _uri("/users/me/account"),
                    headers = _headers(token),
                    body = encodeJsonBody(body),
                )
            }
        return CurrentUser.fromJson(_jsonMap(decoded["user"]))
    }

    override suspend fun updateProfile(
        displayName: String?,
        bio: String?,
        gender: String?,
        avatarAssetId: String?,
        defaultAvatarKey: String?,
    ): CurrentUser {
        val body = LinkedHashMap<String, Any?>()
        if (displayName != null) body["display_name"] = displayName
        if (bio != null) body["bio"] = bio
        if (gender != null) body["gender"] = gender
        if (avatarAssetId != null) body["avatar_asset_id"] = avatarAssetId
        if (defaultAvatarKey != null) body["default_avatar_key"] = defaultAvatarKey
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.patch(
                    _uri("/users/me/profile"),
                    headers = _headers(token),
                    body = encodeJsonBody(body),
                )
            }
        return CurrentUser.fromJson(_jsonMap(decoded["user"]))
    }

    override suspend fun uploadImageAsset(
        bytes: ByteArray,
        filename: String,
        purpose: String,
    ): UploadedAsset =
        _uploadAsset(
            path = "/uploads/images",
            bytes = bytes,
            filename = filename,
            purpose = purpose,
        )

    override suspend fun uploadFileAsset(
        bytes: ByteArray,
        filename: String,
        purpose: String,
        onProgress: UploadProgressCallback?,
        controller: UploadTransferController?,
    ): UploadedAsset =
        _uploadAsset(
            path = "/uploads/files",
            bytes = bytes,
            filename = filename,
            purpose = purpose,
            onProgress = onProgress,
            controller = controller,
        )

    private suspend fun _uploadAsset(
        path: String,
        bytes: ByteArray,
        filename: String,
        purpose: String,
        onProgress: UploadProgressCallback? = null,
        controller: UploadTransferController? = null,
    ): UploadedAsset {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                val request = MultipartRequest("POST", _uri(path))
                request.headers["authorization"] = "Bearer $token"
                request.fields["purpose"] = purpose
                val file =
                    if (onProgress == null && controller == null) {
                        MultipartFile.fromBytes("file", bytes, filename = filename)
                    } else {
                        MultipartFile(
                            "file",
                            _uploadByteStream(
                                bytes,
                                onProgress = onProgress,
                                controller = controller,
                            ),
                            bytes.size.toLong(),
                            filename = filename,
                        )
                    }
                request.files.add(file)
                val streamed = _httpClient.send(request)
                HttpResponse.fromStream(streamed)
            }
        return UploadedAsset.fromJson(_jsonMap(decoded["asset"]))
    }

    override suspend fun listStickerPacks(
        scope: String,
        roomId: String?,
        ownerUserId: String?,
    ): List<StickerPack> {
        val query = linkedMapOf("scope" to scope)
        if (roomId != null) query["room_id"] = roomId
        if (ownerUserId != null) query["owner_user_id"] = ownerUserId
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/sticker-packs", query),
                    headers = _headers(token),
                )
            }
        return _jsonMapList(decoded["packs"]).map { StickerPack.fromJson(it) }
    }

    override suspend fun createStickerPack(
        name: String,
        scope: String,
        roomId: String?,
        ownerUserId: String?,
        sortOrder: Int?,
    ): StickerPack {
        val body = linkedMapOf<String, Any?>("scope" to scope, "name" to name)
        if (roomId != null) body["room_id"] = roomId
        if (ownerUserId != null) body["owner_user_id"] = ownerUserId
        if (sortOrder != null) body["sort_order"] = sortOrder
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/sticker-packs"),
                    headers = _headers(token),
                    body = encodeJsonBody(body),
                )
            }
        return StickerPack.fromJson(_jsonMap(decoded["pack"]))
    }

    override suspend fun updateStickerPack(
        packId: String,
        name: String?,
        sortOrder: Int?,
    ): StickerPack {
        val body = LinkedHashMap<String, Any?>()
        if (name != null) body["name"] = name
        if (sortOrder != null) body["sort_order"] = sortOrder
        val decoded =
            _sendJson { token ->
                _httpClient.patch(
                    _uri("/sticker-packs/$packId"),
                    headers = _headers(token),
                    body = encodeJsonBody(body),
                )
            }
        return StickerPack.fromJson(_jsonMap(decoded["pack"]))
    }

    override suspend fun deleteStickerPack(packId: String) {
        _sendJson { token ->
            _httpClient.delete(
                _uri("/sticker-packs/$packId"),
                headers = _headers(token),
            )
        }
    }

    override suspend fun addSticker(
        packId: String,
        assetId: String,
        name: String,
        sortOrder: Int?,
        idempotencyKey: String?,
        scope: String,
        roomId: String?,
        ownerUserId: String?,
    ) {
        val body = linkedMapOf<String, Any?>("asset_id" to assetId, "name" to name)
        if (sortOrder != null) body["sort_order"] = sortOrder
        val requestIdempotencyKey = idempotencyKey ?: newUuid()
        suspend fun send() {
            _sendJson { token ->
                _httpClient.post(
                    _uri("/sticker-packs/$packId/stickers"),
                    headers = _headers(token, idempotencyKey = requestIdempotencyKey),
                    body = encodeJsonBody(body),
                )
            }
        }

        val maxAttempts = 3
        for (attempt in 1..maxAttempts) {
            try {
                send()
                return
            } catch (e: ApiException) {
                if (!_isRetryableHttpFailure(e.statusCode)) throw e
                if (_stickerAssetAlreadyLinked(
                        packId = packId,
                        assetId = assetId,
                        scope = scope,
                        roomId = roomId,
                        ownerUserId = ownerUserId,
                    )
                ) {
                    return
                }
                if (attempt == maxAttempts) throw e
                delayed((120 * attempt).milliseconds)
            } catch (e: ClientException) {
                if (!_isRetryableTransportFailure(e)) throw e
                if (_stickerAssetAlreadyLinked(
                        packId = packId,
                        assetId = assetId,
                        scope = scope,
                        roomId = roomId,
                        ownerUserId = ownerUserId,
                    )
                ) {
                    return
                }
                if (attempt == maxAttempts) throw e
                delayed((120 * attempt).milliseconds)
            }
        }
    }

    private suspend fun _stickerAssetAlreadyLinked(
        packId: String,
        assetId: String,
        scope: String,
        roomId: String? = null,
        ownerUserId: String? = null,
    ): Boolean {
        try {
            val packs =
                listStickerPacks(
                    scope = scope,
                    roomId = roomId,
                    ownerUserId = ownerUserId,
                )
            for (pack in packs) {
                if (pack.id != packId) continue
                for (sticker in pack.stickers) {
                    if (sticker.asset.id == assetId) return true
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // If verification is also unavailable, let the caller retry once.
        }
        return false
    }

    private suspend fun _stickerAlreadyDeleted(
        packId: String,
        stickerId: String,
        scope: String,
        roomId: String? = null,
        ownerUserId: String? = null,
    ): Boolean {
        try {
            val packs =
                listStickerPacks(
                    scope = scope,
                    roomId = roomId,
                    ownerUserId = ownerUserId,
                )
            for (pack in packs) {
                if (pack.id != packId) continue
                return !pack.stickers.any { sticker -> sticker.id == stickerId }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // If verification also fails, let the delete path retry.
        }
        return false
    }

    override suspend fun deleteSticker(
        packId: String,
        stickerId: String,
        scope: String,
        roomId: String?,
        ownerUserId: String?,
    ) {
        suspend fun send() {
            _sendJson { token ->
                _httpClient.delete(
                    _uri("/sticker-packs/$packId/stickers/$stickerId"),
                    headers = _headers(token),
                )
            }
        }

        val maxAttempts = 3
        for (attempt in 1..maxAttempts) {
            try {
                send()
                return
            } catch (e: ApiException) {
                if (e.statusCode == 404) return
                if (!_isRetryableHttpFailure(e.statusCode)) throw e
                if (_stickerAlreadyDeleted(
                        packId = packId,
                        stickerId = stickerId,
                        scope = scope,
                        roomId = roomId,
                        ownerUserId = ownerUserId,
                    )
                ) {
                    return
                }
                if (attempt == maxAttempts) throw e
                delayed((120 * attempt).milliseconds)
            } catch (e: ClientException) {
                if (!_isRetryableTransportFailure(e)) throw e
                if (_stickerAlreadyDeleted(
                        packId = packId,
                        stickerId = stickerId,
                        scope = scope,
                        roomId = roomId,
                        ownerUserId = ownerUserId,
                    )
                ) {
                    return
                }
                if (attempt == maxAttempts) throw e
                delayed((120 * attempt).milliseconds)
            }
        }
    }

    override suspend fun updateSticker(
        packId: String,
        stickerId: String,
        name: String?,
        sortOrder: Int?,
    ): Sticker {
        val body = LinkedHashMap<String, Any?>()
        if (name != null) body["name"] = name
        if (sortOrder != null) body["sort_order"] = sortOrder
        val decoded =
            _sendJson { token ->
                _httpClient.patch(
                    _uri("/sticker-packs/$packId/stickers/$stickerId"),
                    headers = _headers(token),
                    body = encodeJsonBody(body),
                )
            }
        return Sticker.fromJson(_jsonMap(decoded["sticker"]))
    }

    override suspend fun reorderStickers(
        packId: String,
        stickerIds: List<String>,
    ): StickerPack {
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/sticker-packs/$packId/stickers/reorder"),
                    headers = _headers(token),
                    body = encodeJsonBody(mapOf("sticker_ids" to stickerIds)),
                )
            }
        return StickerPack.fromJson(_jsonMap(decoded["pack"]))
    }

    override suspend fun downloadStickers(stickerIds: List<String>): DownloadedFile {
        val response =
            _sendWithAuth { token ->
                _httpClient.get(
                    _uri("/stickers/download", mapOf("ids" to stickerIds.joinToString(","))),
                    headers = mapOf("authorization" to "Bearer $token"),
                )
            }
        _throwIfFailed(response)
        return DownloadedFile(
            bytes = response.bodyBytes,
            filename = _downloadFilename(response),
            mimeType = response.headers["content-type"] ?: "application/octet-stream",
        )
    }

    override suspend fun saveSticker(
        roomId: String,
        stickerId: String,
        sourceMessageId: String?,
        targetScope: String,
        targetPackId: String?,
        name: String?,
        sortOrder: Int?,
    ): StickerPack {
        val body =
            linkedMapOf<String, Any?>(
                "sticker_id" to stickerId,
                "target_scope" to targetScope,
            )
        if (sourceMessageId != null) body["source_message_id"] = sourceMessageId
        if (targetPackId != null) body["target_pack_id"] = targetPackId
        if (name != null) body["name"] = name
        if (sortOrder != null) body["sort_order"] = sortOrder
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/stickers/save"),
                    headers = _headers(token),
                    body = encodeJsonBody(body),
                )
            }
        return StickerPack.fromJson(_jsonMap(decoded["pack"]))
    }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        revokeOtherSessions: Boolean,
    ) {
        _sendJson { token ->
            _httpClient.post(
                _uri("/auth/password"),
                headers = _headers(token),
                body =
                    encodeJsonBody(
                        linkedMapOf(
                            "current_password" to currentPassword,
                            "new_password" to newPassword,
                            "revoke_other_sessions" to revokeOtherSessions,
                        ),
                    ),
            )
        }
    }

    override suspend fun listSessions(): List<UserSession> {
        val decoded =
            _sendJsonValue(retryTransientFailures = true) { token ->
                _httpClient.get(_uri("/auth/sessions"), headers = _headers(token))
            }
        return _sessionItems(decoded).map { UserSession.fromJson(it) }
    }

    override suspend fun deleteMyAccount(confirm: Boolean) {
        _sendJson { token ->
            _httpClient.delete(
                _uri("/users/me/account"),
                headers = _headers(token),
                body = encodeJsonBody(mapOf("confirm" to confirm)),
            )
        }
    }

    override suspend fun listRooms(limit: Int, cursor: String?): RoomPage {
        val query = linkedMapOf("limit" to "$limit")
        if (cursor != null) query["cursor"] = cursor
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(_uri("/rooms", query), headers = _headers(token))
            }
        return RoomPage.fromJson(decoded)
    }

    override suspend fun createRoom(
        name: String,
        description: String?,
        visibility: String?,
        joinPolicy: String?,
        aiVoiceAnnouncementsEnabled: Boolean?,
        avatarAssetId: String?,
        defaultAvatarKey: String?,
        idempotencyKey: String?,
    ): RoomDetail {
        val decoded =
            _sendJson { token ->
                val body = linkedMapOf<String, Any?>("name" to name)
                if (description != null) body["description"] = description
                if (visibility != null) body["visibility"] = visibility
                if (joinPolicy != null) body["join_policy"] = joinPolicy
                if (aiVoiceAnnouncementsEnabled != null) {
                    body["ai_voice_announcements_enabled"] = aiVoiceAnnouncementsEnabled
                }
                if (avatarAssetId != null) body["avatar_asset_id"] = avatarAssetId
                if (defaultAvatarKey != null) {
                    body["default_avatar_key"] = defaultAvatarKey
                }
                _httpClient.post(
                    _uri("/rooms"),
                    headers = _headers(token, idempotencyKey = idempotencyKey ?: newUuid()),
                    body = encodeJsonBody(body),
                )
            }
        return RoomDetail.fromJson(_jsonMap(decoded["room"]))
    }

    override suspend fun getRoom(roomId: String): RoomDetail {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(_uri("/rooms/$roomId"), headers = _headers(token))
            }
        return RoomDetail.fromJson(_jsonMap(decoded["room"]))
    }

    override suspend fun updateRoom(
        roomId: String,
        name: String?,
        description: String?,
        visibility: String?,
        joinPolicy: String?,
        aiVoiceAnnouncementsEnabled: Boolean?,
        gangBotEnabled: Boolean?,
        avatarAssetId: String?,
        defaultAvatarKey: String?,
    ): RoomDetail {
        val body = LinkedHashMap<String, Any?>()
        if (name != null) body["name"] = name
        if (description != null) body["description"] = description
        if (visibility != null) body["visibility"] = visibility
        if (joinPolicy != null) body["join_policy"] = joinPolicy
        if (aiVoiceAnnouncementsEnabled != null) {
            body["ai_voice_announcements_enabled"] = aiVoiceAnnouncementsEnabled
        }
        if (gangBotEnabled != null) body["gang_bot_enabled"] = gangBotEnabled
        if (avatarAssetId != null) body["avatar_asset_id"] = avatarAssetId
        if (defaultAvatarKey != null) body["default_avatar_key"] = defaultAvatarKey
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.patch(
                    _uri("/rooms/$roomId"),
                    headers = _headers(token),
                    body = encodeJsonBody(body),
                )
            }
        return RoomDetail.fromJson(_jsonMap(decoded["room"]))
    }

    override suspend fun updateMyRoomSettings(
        roomId: String,
        remarkName: String?,
        notificationPolicy: String?,
        roomDisplayName: String?,
        isPinned: Boolean?,
        aiVoiceAnnouncementsEnabled: Boolean?,
    ): RoomDetail {
        val body = LinkedHashMap<String, Any?>()
        if (remarkName != null) body["remark_name"] = remarkName
        if (notificationPolicy != null) {
            body["notification_policy"] = notificationPolicy
        }
        if (roomDisplayName != null) body["room_display_name"] = roomDisplayName
        if (isPinned != null) body["is_pinned"] = isPinned
        if (aiVoiceAnnouncementsEnabled != null) {
            body["ai_voice_announcements_enabled"] = aiVoiceAnnouncementsEnabled
        }
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.patch(
                    _uri("/rooms/$roomId/me"),
                    headers = _headers(token),
                    body = encodeJsonBody(body),
                )
            }
        return RoomDetail.fromJson(_jsonMap(decoded["room"]))
    }

    override suspend fun leaveRoom(
        roomId: String,
        confirmDeleteIfEmpty: Boolean,
    ) {
        _sendJson { token ->
            _httpClient.delete(
                _uri("/rooms/$roomId/members/me"),
                headers = _headers(token),
                body =
                    encodeJsonBody(
                        buildMap<String, Any?> {
                            if (confirmDeleteIfEmpty) put("confirm_delete_if_empty", true)
                        },
                    ),
            )
        }
    }

    override suspend fun deleteRoom(
        roomId: String,
        confirmName: String,
    ) {
        _sendJson { token ->
            _httpClient.delete(
                _uri("/rooms/$roomId"),
                headers = _headers(token),
                body = encodeJsonBody(mapOf("confirm_name" to confirmName)),
            )
        }
    }

    override suspend fun updateRoomMemberRole(
        roomId: String,
        userId: String,
        role: String,
    ): RoomMember {
        val decoded =
            _sendJson { token ->
                _httpClient.patch(
                    _uri("/rooms/$roomId/members/$userId"),
                    headers = _headers(token),
                    body = encodeJsonBody(mapOf("role" to role)),
                )
            }
        return RoomMember.fromJson(_jsonMap(decoded["member"]))
    }

    override suspend fun updateRoomMemberRoomDisplayName(
        roomId: String,
        userId: String,
        roomDisplayName: String,
    ): RoomMember {
        val decoded =
            _sendJson { token ->
                _httpClient.patch(
                    _uri("/rooms/$roomId/members/$userId"),
                    headers = _headers(token),
                    body = encodeJsonBody(mapOf("room_display_name" to roomDisplayName)),
                )
            }
        return RoomMember.fromJson(_jsonMap(decoded["member"]))
    }

    override suspend fun removeRoomMember(
        roomId: String,
        userId: String,
    ) {
        _sendJson { token ->
            _httpClient.delete(
                _uri("/rooms/$roomId/members/$userId"),
                headers = _headers(token),
            )
        }
    }

    override suspend fun transferRoomCreator(
        roomId: String,
        userId: String,
    ): RoomDetail {
        val decoded =
            _sendJson { token ->
                _httpClient.patch(
                    _uri("/rooms/$roomId/creator"),
                    headers = _headers(token),
                    body = encodeJsonBody(mapOf("user_id" to userId)),
                )
            }
        return RoomDetail.fromJson(_jsonMap(decoded["room"]))
    }

    override suspend fun searchRooms(
        query: String,
        limit: Int,
    ): List<PublicRoom> {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/rooms/search", linkedMapOf("q" to query, "limit" to "$limit")),
                    headers = _headers(token),
                )
            }
        return _jsonMapList(decoded["rooms"]).map { PublicRoom.fromJson(it) }
    }

    override suspend fun search(
        query: String,
        limit: Int,
        categories: Iterable<String>?,
        myRoomsCursor: String?,
        publicRoomsCursor: String?,
        messagesCursor: String?,
        filesCursor: String?,
    ): GlobalSearchResults {
        val queryParameters = linkedMapOf("q" to query, "limit" to "$limit")
        val categoryList = categories?.filter { category -> category.isNotEmpty() }
        if (categoryList != null) {
            val encodedCategories = categoryList.joinToString(",")
            if (encodedCategories.isNotEmpty()) {
                queryParameters["categories"] = encodedCategories
            }
        }
        if (myRoomsCursor != null) {
            queryParameters["my_rooms_cursor"] = myRoomsCursor
        }
        if (publicRoomsCursor != null) {
            queryParameters["public_rooms_cursor"] = publicRoomsCursor
        }
        if (messagesCursor != null) {
            queryParameters["messages_cursor"] = messagesCursor
        }
        if (filesCursor != null) {
            queryParameters["files_cursor"] = filesCursor
        }

        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/search", queryParameters),
                    headers = _headers(token),
                )
            }
        return GlobalSearchResults.fromJson(decoded)
    }

    override suspend fun joinRoom(roomId: String, reason: String?): JoinRoomResult {
        val body = LinkedHashMap<String, Any?>()
        val trimmedReason = reason?.trim()
        if (trimmedReason != null && trimmedReason.isNotEmpty()) {
            body["reason"] = trimmedReason
        }
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/join"),
                    headers = _headers(token, idempotencyKey = newUuid()),
                    body = if (body.isEmpty()) null else encodeJsonBody(body),
                )
            }
        val roomJson = _jsonMapOrNull(decoded["room"])
        if (roomJson != null) {
            return JoinRoomResult(room = RoomDetail.fromJson(roomJson))
        }
        // approval_required path: server returns {"join_request": {...}} with 202.
        return JoinRoomResult(pending = true)
    }

    override suspend fun listRoomMembers(
        roomId: String,
        limit: Int,
        cursor: String?,
    ): RoomMemberPage {
        val query = linkedMapOf("limit" to "$limit")
        if (cursor != null) query["cursor"] = cursor
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/rooms/$roomId/members", query),
                    headers = _headers(token),
                )
            }
        return RoomMemberPage.fromJson(decoded)
    }

    override suspend fun getRoomMemberProfile(
        roomId: String,
        userId: String,
    ): RoomMemberProfile {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/rooms/$roomId/members/$userId/profile"),
                    headers = _headers(token),
                )
            }
        return RoomMemberProfile.fromJson(_jsonMap(decoded["profile"]))
    }

    override suspend fun getUserProfile(userId: String): UserSummary {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/users/$userId/profile"),
                    headers = _headers(token),
                )
            }
        val profile = _jsonMap(decoded["profile"])
        return UserSummary.fromJson(_jsonMap(profile["user"]))
    }

    override suspend fun inviteMember(
        roomId: String,
        userId: String,
    ): RoomInvite {
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/invites"),
                    headers = _headers(token, idempotencyKey = newUuid()),
                    body = encodeJsonBody(mapOf("user_id" to userId)),
                )
            }
        return RoomInvite.fromJson(_jsonMap(decoded["invite"]))
    }

    override suspend fun listRoomBlacklist(roomId: String): List<RoomBlacklistEntry> {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/rooms/$roomId/blacklist"),
                    headers = _headers(token),
                )
            }
        return _jsonMapList(decoded["blacklist"] as List<*>? ?: decoded["items"] as List<*>?)
            .map { RoomBlacklistEntry.fromJson(it) }
    }

    override suspend fun blockRoomUser(
        roomId: String,
        userId: String,
    ): RoomBlacklistEntry {
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/blacklist"),
                    headers = _headers(token, idempotencyKey = newUuid()),
                    body = encodeJsonBody(mapOf("user_id" to userId)),
                )
            }
        return RoomBlacklistEntry.fromJson(_jsonMap(decoded["entry"]))
    }

    override suspend fun unblockRoomUser(
        roomId: String,
        userId: String,
    ) {
        _sendJson { token ->
            _httpClient.delete(
                _uri("/rooms/$roomId/blacklist/$userId"),
                headers = _headers(token),
            )
        }
    }

    override suspend fun listRoomInvites(status: String): List<RoomInvite> {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/room-invites", mapOf("status" to status)),
                    headers = _headers(token),
                )
            }
        return _jsonMapList(decoded["invites"]).map { RoomInvite.fromJson(it) }
    }

    override suspend fun reviewRoomInvite(
        inviteId: String,
        accept: Boolean,
        reason: String?,
    ): JoinRoomResult {
        val body = linkedMapOf<String, Any?>("decision" to if (accept) "accept" else "reject")
        val trimmedReason = reason?.trim()
        if (trimmedReason != null && trimmedReason.isNotEmpty()) {
            body["reason"] = trimmedReason
        }
        val decoded =
            _sendJson { token ->
                _httpClient.patch(
                    _uri("/room-invites/$inviteId"),
                    headers = _headers(token),
                    body = encodeJsonBody(body),
                )
            }
        val roomJson = _jsonMapOrNull(decoded["room"])
        if (roomJson != null) {
            return JoinRoomResult(room = RoomDetail.fromJson(roomJson))
        }
        if (decoded["join_request"] != null) {
            return JoinRoomResult(pending = true)
        }
        return JoinRoomResult()
    }

    override suspend fun listRoomApplications(status: String): List<RoomApplication> {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/room-applications", mapOf("status" to status)),
                    headers = _headers(token),
                )
            }
        return _jsonMapList(decoded["applications"]).map { RoomApplication.fromJson(it) }
    }

    override suspend fun withdrawRoomApplication(requestId: String): RoomApplication {
        val decoded =
            _sendJson { token ->
                _httpClient.patch(
                    _uri("/room-applications/$requestId"),
                    headers = _headers(token),
                    body = encodeJsonBody(mapOf("decision" to "withdraw")),
                )
            }
        return RoomApplication.fromJson(_jsonMap(decoded["application"]))
    }

    override suspend fun listRoomNotifications(): List<RoomEventNotification> {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/room-notifications"),
                    headers = _headers(token),
                )
            }
        return _jsonMapList(decoded["notifications"]).map { RoomEventNotification.fromJson(it) }
    }

    override suspend fun deleteRoomNotification(
        notificationType: String,
        notificationId: String,
    ) {
        _sendJson { token ->
            _httpClient.delete(
                _uri("/room-notifications/$notificationType/$notificationId"),
                headers = _headers(token),
            )
        }
    }

    override suspend fun markRoomNotificationsRead() {
        _sendJson { token ->
            _httpClient.post(
                _uri("/room-notifications/read"),
                headers = _headers(token),
            )
        }
    }

    override suspend fun searchUsers(
        query: String,
        limit: Int,
    ): List<UserSummary> {
        val page = searchUsersPage(query = query, limit = limit)
        return page.users
    }

    override suspend fun searchUsersPage(
        query: String,
        limit: Int,
        cursor: String?,
        includeSuspended: Boolean,
    ): UserSearchPage {
        val parameters = linkedMapOf("q" to query, "limit" to "$limit")
        if (cursor != null) parameters["cursor"] = cursor
        if (includeSuspended) parameters["include_suspended"] = "true"
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/users/search", parameters),
                    headers = _headers(token),
                )
            }
        return UserSearchPage.fromJson(decoded)
    }

    override suspend fun getForcedUserSettings(userId: String): CurrentUser {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/users/$userId/settings"),
                    headers = _headers(token),
                )
            }
        return CurrentUser.fromJson(_jsonMap(decoded["user"]))
    }

    override suspend fun listForcedUserSessions(userId: String): List<UserSession> {
        val decoded =
            _sendJsonValue(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/users/$userId/sessions"),
                    headers = _headers(token),
                )
            }
        return _sessionItems(decoded).map { UserSession.fromJson(it) }
    }

    override suspend fun forceDeleteUserAccount(userId: String) {
        _sendJson { token ->
            _httpClient.delete(
                _uri("/users/$userId/account"),
                headers = _headers(token),
                body = encodeJsonBody(mapOf("confirm" to true)),
            )
        }
    }

    override suspend fun updateForcedUserSettings(
        userId: String,
        username: String?,
        email: String?,
        emailVerified: Boolean?,
        emailPublic: Boolean?,
        phoneNumber: String?,
        phoneNumberPublic: Boolean?,
        displayName: String?,
        bio: String?,
        gender: String?,
        avatarAssetId: String?,
        defaultAvatarKey: String?,
        language: String?,
        status: String?,
    ): CurrentUser {
        val body = LinkedHashMap<String, Any?>()
        fun put(key: String, value: Any?) {
            if (value != null) body[key] = value
        }

        put("username", username)
        put("email", email)
        put("email_verified", emailVerified)
        put("email_public", emailPublic)
        put("phone_number", phoneNumber)
        put("phone_number_public", phoneNumberPublic)
        put("display_name", displayName)
        put("bio", bio)
        put("gender", gender)
        put("avatar_asset_id", avatarAssetId)
        put("default_avatar_key", defaultAvatarKey)
        put("language", language)
        put("status", status)
        val decoded =
            _sendJson { token ->
                _httpClient.patch(
                    _uri("/users/$userId/settings"),
                    headers = _headers(token),
                    body = encodeJsonBody(body),
                )
            }
        return CurrentUser.fromJson(_jsonMap(decoded["user"]))
    }

    override suspend fun getForcedUserAudioSettings(userId: String): UserAudioSettings {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/users/$userId/audio-settings"),
                    headers = _headers(token),
                )
            }
        return UserAudioSettings.fromJson(_jsonMap(decoded["audio_settings"]))
    }

    override suspend fun updateForcedUserAudioSettings(
        userId: String,
        settings: UserAudioSettings,
    ): UserAudioSettings {
        val decoded =
            _sendJson { token ->
                _httpClient.patch(
                    _uri("/users/$userId/audio-settings"),
                    headers = _headers(token),
                    body =
                        encodeJsonBody(
                            linkedMapOf(
                                "default_audio_input_volume" to settings.defaultAudioInputVolume,
                                "default_audio_output_volume" to settings.defaultAudioOutputVolume,
                                "live_mic_input_volume" to settings.liveMicInputVolume,
                                "live_voice_output_volume" to settings.liveVoiceOutputVolume,
                                "live_screen_share_output_volume" to settings.liveScreenShareOutputVolume,
                                "live_music_output_volume" to settings.liveMusicOutputVolume,
                            ),
                        ),
                )
            }
        return UserAudioSettings.fromJson(_jsonMap(decoded["audio_settings"]))
    }

    override suspend fun forceResetUserPassword(
        userId: String,
        newPassword: String,
    ) {
        _sendJson { token ->
            _httpClient.post(
                _uri("/users/$userId/password"),
                headers = _headers(token),
                body = encodeJsonBody(mapOf("new_password" to newPassword)),
            )
        }
    }

    override suspend fun listJoinRequests(
        roomId: String,
        status: String,
    ): List<JoinRequest> {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/rooms/$roomId/join-requests", mapOf("status" to status)),
                    headers = _headers(token),
                )
            }
        return _jsonMapList(decoded["requests"]).map { JoinRequest.fromJson(it) }
    }

    override suspend fun reviewJoinRequest(
        roomId: String,
        requestId: String,
        approve: Boolean,
    ) {
        _sendJson { token ->
            _httpClient.patch(
                _uri("/rooms/$roomId/join-requests/$requestId"),
                headers = _headers(token),
                body = encodeJsonBody(mapOf("decision" to if (approve) "approve" else "reject")),
            )
        }
    }

    override suspend fun listMessages(
        roomId: String,
        limit: Int,
        before: String?,
    ): MessagePage {
        val query = linkedMapOf("limit" to "$limit")
        if (before != null) query["before"] = before
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/rooms/$roomId/messages", query),
                    headers = _headers(token),
                )
            }
        return MessagePage.fromJson(decoded)
    }

    override suspend fun listMessageHistory(
        roomId: String,
        query: String,
        category: String,
        senderUserId: String?,
        startAt: DateTime?,
        endAt: DateTime?,
        limit: Int,
        before: String?,
    ): MessagePage {
        val parameters =
            linkedMapOf(
                "limit" to "$limit",
                "category" to category,
            )
        val trimmedQuery = query.trim()
        if (trimmedQuery.isNotEmpty()) parameters["query"] = trimmedQuery
        val trimmedSender = senderUserId?.trim()
        if (trimmedSender != null && trimmedSender.isNotEmpty()) {
            parameters["sender_user_id"] = trimmedSender
        }
        if (startAt != null) {
            parameters["start_at"] = startAt.toUtc().toIso8601String()
        }
        if (endAt != null) {
            parameters["end_at"] = endAt.toUtc().toIso8601String()
        }
        if (before != null && before.trim().isNotEmpty()) {
            parameters["before"] = before.trim()
        }
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/rooms/$roomId/message-history", parameters),
                    headers = _headers(token),
                )
            }
        return MessagePage.fromJson(decoded)
    }

    override suspend fun hideMessageHistory(
        roomId: String,
        messageIds: List<String>,
    ): Int {
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/message-history/hide"),
                    headers = _headers(token),
                    body = encodeJsonBody(linkedMapOf("message_ids" to messageIds, "confirm" to true)),
                )
            }
        return (decoded["deleted_count"] as Number?)?.toInt() ?: 0
    }

    override suspend fun sendMessage(
        roomId: String,
        clientMessageId: String,
        body: String,
        type: String,
        attachments: List<MessageAttachment>,
        mentions: List<JsonMap>,
        quoteMessageId: String?,
        quoteMessageIds: List<String>,
        idempotencyKey: String?,
    ): Message {
        val requestIdempotencyKey = idempotencyKey ?: newUuid()
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                val requestBody =
                    linkedMapOf<String, Any?>(
                        "client_message_id" to clientMessageId,
                        "body" to body,
                    )
                if (type != "text") requestBody["type"] = type
                if (attachments.isNotEmpty()) {
                    requestBody["attachments"] = attachments.map { attachment -> attachment.toJson() }
                }
                if (mentions.isNotEmpty()) {
                    requestBody["mentions"] = mentions
                }
                if (quoteMessageId != null && quoteMessageId.isNotEmpty()) {
                    requestBody["quote_message_id"] = quoteMessageId
                }
                if (quoteMessageIds.isNotEmpty()) {
                    requestBody["quote_message_ids"] = quoteMessageIds
                    requestBody["quote_message_id"] = quoteMessageIds.first()
                }
                _httpClient.post(
                    _uri("/rooms/$roomId/messages"),
                    headers = _headers(token, idempotencyKey = requestIdempotencyKey),
                    body = encodeJsonBody(requestBody),
                )
            }
        return Message.fromJson(_jsonMap(decoded["message"]))
    }

    override suspend fun markRead(
        roomId: String,
        lastReadMessageId: String,
    ): Int {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/read"),
                    headers = _headers(token),
                    body = encodeJsonBody(mapOf("last_read_message_id" to lastReadMessageId)),
                )
            }
        return (decoded["unread_count"] as Number).toInt()
    }

    override suspend fun recallMessage(
        roomId: String,
        messageId: String,
        reason: String?,
    ): MessageRecallResult {
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/messages/$messageId/recall"),
                    headers = _headers(token),
                    body = encodeJsonBody(buildMap<String, Any?> { if (reason != null) put("reason", reason) }),
                )
            }
        return MessageRecallResult.fromJson(decoded)
    }

    override suspend fun listMessageRecallRequests(
        roomId: String,
        status: String,
    ): List<MessageRecallRequest> {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/rooms/$roomId/message-recall-requests", mapOf("status" to status)),
                    headers = _headers(token),
                )
            }
        val requests = _jsonMapList(decoded["requests"])
        return requests.map { MessageRecallRequest.fromJson(it) }
    }

    override suspend fun reviewMessageRecallRequest(
        roomId: String,
        requestId: String,
        approve: Boolean,
        reason: String?,
    ) {
        _sendJson { token ->
            _httpClient.patch(
                _uri("/rooms/$roomId/message-recall-requests/$requestId"),
                headers = _headers(token),
                body =
                    encodeJsonBody(
                        buildMap<String, Any?> {
                            put("decision", if (approve) "approve" else "reject")
                            if (reason != null) put("reason", reason)
                        },
                    ),
            )
        }
    }

    override suspend fun forceDeleteMessage(
        roomId: String,
        messageId: String,
        reason: String?,
    ): Message {
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/messages/$messageId/force-delete"),
                    headers = _headers(token),
                    body =
                        encodeJsonBody(
                            buildMap<String, Any?> {
                                put("confirm", true)
                                if (reason != null) put("reason", reason)
                            },
                        ),
                )
            }
        return Message.fromJson(_jsonMap(decoded["message"]))
    }

    override suspend fun listMyLiveMemberVolumes(roomId: String): List<LiveMemberVolume> {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/rooms/$roomId/live/me/member-volumes"),
                    headers = _headers(token),
                )
            }
        val volumes = _jsonMapList(decoded["member_volumes"])
        return volumes.map { LiveMemberVolume.fromJson(it) }
    }

    override suspend fun updateMyLiveMemberVolume(
        roomId: String,
        targetUserId: String,
        volume: Int,
    ): LiveMemberVolume {
        val decoded =
            _sendJson { token ->
                _httpClient.patch(
                    _uri("/rooms/$roomId/live/me/member-volumes/$targetUserId"),
                    headers = _headers(token),
                    body = encodeJsonBody(mapOf("volume" to volume)),
                )
            }
        return LiveMemberVolume.fromJson(_jsonMap(decoded["member_volume"]))
    }

    override suspend fun moderateLiveParticipant(
        roomId: String,
        userId: String,
        action: String,
        reason: String?,
    ) {
        _sendJson { token ->
            _httpClient.post(
                _uri("/rooms/$roomId/live/participants/$userId/moderation"),
                headers = _headers(token),
                body =
                    encodeJsonBody(
                        buildMap<String, Any?> {
                            put("action", action)
                            if (reason != null) put("reason", reason)
                        },
                    ),
            )
        }
    }

    override suspend fun getLiveState(roomId: String): LiveState {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/rooms/$roomId/live"),
                    headers = _headers(token),
                )
            }
        return LiveState.fromJson(_jsonMap(decoded["live"]))
    }

    override suspend fun joinLive(
        roomId: String,
        clientLiveSessionId: String,
        source: String,
        micMuted: Boolean,
        headphonesMuted: Boolean,
        idempotencyKey: String?,
    ): LiveJoinResult {
        val requestIdempotencyKey = idempotencyKey ?: newUuid()
        return LiveJoinResult.fromJson(
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/live/join"),
                    headers = _headers(token, idempotencyKey = requestIdempotencyKey),
                    body =
                        encodeJsonBody(
                            linkedMapOf(
                                "client_live_session_id" to clientLiveSessionId,
                                "source" to source,
                                "mic_muted" to micMuted,
                                "headphones_muted" to headphonesMuted,
                            ),
                        ),
                )
            },
        )
    }

    override suspend fun issueScreenAudioToken(roomId: String): ScreenAudioToken =
        ScreenAudioToken.fromJson(
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/live/screen-audio-token"),
                    headers = _headers(token),
                )
            },
        )

    override suspend fun updateMyLiveState(
        roomId: String,
        micMuted: Boolean?,
        headphonesMuted: Boolean?,
        cameraOn: Boolean?,
        cameraMirrored: Boolean?,
        screenSharing: Boolean?,
        connectionState: String?,
    ): LiveParticipant {
        val body = LinkedHashMap<String, Any?>()
        if (micMuted != null) body["mic_muted"] = micMuted
        if (headphonesMuted != null) body["headphones_muted"] = headphonesMuted
        if (cameraOn != null) body["camera_on"] = cameraOn
        if (cameraMirrored != null) body["camera_mirrored"] = cameraMirrored
        if (screenSharing != null) body["screen_sharing"] = screenSharing
        if (connectionState != null) body["connection_state"] = connectionState
        val decoded =
            _sendJson { token ->
                _httpClient.patch(
                    _uri("/rooms/$roomId/live/me"),
                    headers = _headers(token),
                    body = encodeJsonBody(body),
                )
            }
        return LiveParticipant.fromJson(_jsonMap(decoded["participant"]))
    }

    override suspend fun updateMyLiveScreenView(
        roomId: String,
        broadcasterUserId: String?,
    ) {
        _sendJson { token ->
            _httpClient.patch(
                _uri("/rooms/$roomId/live/me/screen-view"),
                headers = _headers(token),
                body = encodeJsonBody(mapOf("broadcaster_user_id" to (broadcasterUserId ?: ""))),
            )
        }
    }

    override suspend fun getMusicBoxState(roomId: String): MusicBoxState {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/rooms/$roomId/music-box/state"),
                    headers = _headers(token),
                )
            }
        return MusicBoxState.fromJson(decoded)
    }

    override suspend fun searchMusicBox(
        roomId: String,
        keyword: String,
        source: String?,
        count: Int?,
        page: Int?,
    ): List<MusicBoxSearchResult> {
        val query = linkedMapOf("keyword" to keyword)
        if (source != null && source.isNotEmpty()) query["source"] = source
        if (count != null) query["count"] = "$count"
        if (page != null) query["page"] = "$page"
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/rooms/$roomId/music-box/search", query),
                    headers = _headers(token),
                )
            }
        return _jsonMapList(decoded["results"]).map { MusicBoxSearchResult.fromJson(it) }
    }

    override suspend fun listPersonalMusicPlaylists(
        page: Int,
        pageSize: Int,
    ): PersonalMusicPlaylistPage {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri(
                        "/me/music-box/playlists",
                        linkedMapOf(
                            "page" to "$page",
                            "page_size" to "$pageSize",
                        ),
                    ),
                    headers = _headers(token),
                )
            }
        return PersonalMusicPlaylistPage.fromJson(decoded)
    }

    override suspend fun createPersonalMusicPlaylist(name: String): PersonalMusicPlaylist {
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/me/music-box/playlists"),
                    headers = _headers(token),
                    body = encodeJsonBody(mapOf("name" to name)),
                )
            }
        return PersonalMusicPlaylist.fromJson(_jsonMap(decoded["playlist"]))
    }

    override suspend fun mergePersonalMusicPlaylists(
        name: String,
        playlistIds: List<String>,
    ): PersonalMusicPlaylistMergeResult {
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/me/music-box/playlists/merge"),
                    headers = _headers(token),
                    body = encodeJsonBody(linkedMapOf("name" to name, "playlist_ids" to playlistIds)),
                )
            }
        return PersonalMusicPlaylistMergeResult.fromJson(decoded)
    }

    override suspend fun renamePersonalMusicPlaylist(
        playlistId: String,
        name: String,
    ): PersonalMusicPlaylist {
        val decoded =
            _sendJson { token ->
                _httpClient.patch(
                    _uri("/me/music-box/playlists/$playlistId"),
                    headers = _headers(token),
                    body = encodeJsonBody(mapOf("name" to name)),
                )
            }
        return PersonalMusicPlaylist.fromJson(_jsonMap(decoded["playlist"]))
    }

    override suspend fun deletePersonalMusicPlaylist(playlistId: String) {
        _sendJson { token ->
            _httpClient.delete(
                _uri("/me/music-box/playlists/$playlistId"),
                headers = _headers(token),
            )
        }
    }

    override suspend fun pinPersonalMusicPlaylists(playlistIds: List<String>) {
        _sendJson { token ->
            _httpClient.patch(
                _uri("/me/music-box/playlists/order"),
                headers = _headers(token),
                body = encodeJsonBody(mapOf("playlist_ids" to playlistIds)),
            )
        }
    }

    override suspend fun movePersonalMusicPlaylist(
        playlistId: String,
        direction: String,
    ) {
        _sendJson { token ->
            _httpClient.patch(
                _uri("/me/music-box/playlists/order"),
                headers = _headers(token),
                body =
                    encodeJsonBody(
                        linkedMapOf(
                            "playlist_id" to playlistId,
                            "direction" to direction,
                        ),
                    ),
            )
        }
    }

    override suspend fun getPersonalMusicPlaylist(
        playlistId: String,
        page: Int,
        pageSize: Int,
        keyword: String?,
        source: String?,
    ): PersonalMusicPlaylistItemsPage {
        val query = linkedMapOf("page" to "$page", "page_size" to "$pageSize")
        if (keyword != null && keyword.trim().isNotEmpty()) {
            query["keyword"] = keyword.trim()
        }
        if (source != null && source.trim().isNotEmpty()) {
            query["source"] = source.trim()
        }
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/me/music-box/playlists/$playlistId", query),
                    headers = _headers(token),
                )
            }
        return PersonalMusicPlaylistItemsPage.fromJson(decoded)
    }

    override suspend fun searchPersonalMusicPlaylistTracks(
        keyword: String,
        source: String?,
        count: Int,
        page: Int,
    ): List<MusicBoxSearchResult> {
        val query =
            linkedMapOf(
                "keyword" to keyword,
                "count" to "$count",
                "page" to "$page",
            )
        if (source != null && source.trim().isNotEmpty()) {
            query["source"] = source.trim()
        }
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/me/music-box/search", query),
                    headers = _headers(token),
                )
            }
        return _jsonMapList(decoded["results"]).map { MusicBoxSearchResult.fromJson(it) }
    }

    override suspend fun downloadMusicTrackPreview(
        source: String,
        trackId: String,
    ): DownloadedFile {
        val response =
            _sendWithAuth { token ->
                _httpClient.post(
                    _uri("/me/music-box/preview"),
                    headers = _headers(token),
                    body = encodeJsonBody(linkedMapOf("source" to source, "track_id" to trackId)),
                )
            }
        _throwIfFailed(response)
        return DownloadedFile(
            bytes = response.bodyBytes,
            filename = _downloadFilename(response),
            mimeType = response.headers["content-type"] ?: "audio/mp4",
        )
    }

    override suspend fun addPersonalMusicPlaylistItem(
        playlistId: String,
        track: MusicBoxSearchResult,
        durationMs: Int?,
    ): PersonalMusicPlaylistItem {
        val body =
            linkedMapOf<String, Any?>(
                "track_id" to track.trackId,
                "source" to track.source,
                "title" to track.name,
                "artists" to track.artists,
            )
        if (durationMs != null) body["duration_ms"] = durationMs
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/me/music-box/playlists/$playlistId/items"),
                    headers = _headers(token),
                    body = encodeJsonBody(body),
                )
            }
        return PersonalMusicPlaylistItem.fromJson(_jsonMap(decoded["item"]))
    }

    override suspend fun batchAddPersonalMusicPlaylistItems(
        sourcePlaylistId: String,
        targetPlaylistId: String,
        itemIds: List<String>,
    ): PersonalMusicPlaylistBatchAddResult {
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/me/music-box/playlists/$targetPlaylistId/items/batch-add"),
                    headers = _headers(token),
                    body =
                        encodeJsonBody(
                            linkedMapOf(
                                "source_playlist_id" to sourcePlaylistId,
                                "item_ids" to itemIds,
                            ),
                        ),
                )
            }
        return PersonalMusicPlaylistBatchAddResult.fromJson(decoded)
    }

    override suspend fun deletePersonalMusicPlaylistItem(
        playlistId: String,
        itemId: String,
    ) {
        _sendJson { token ->
            _httpClient.delete(
                _uri("/me/music-box/playlists/$playlistId/items/$itemId"),
                headers = _headers(token),
            )
        }
    }

    override suspend fun deletePersonalMusicPlaylistItems(
        playlistId: String,
        itemIds: List<String>,
    ) {
        _sendJson { token ->
            _httpClient.delete(
                _uri("/me/music-box/playlists/$playlistId/items"),
                headers = _headers(token),
                body = encodeJsonBody(mapOf("item_ids" to itemIds)),
            )
        }
    }

    override suspend fun movePersonalMusicPlaylistItem(
        playlistId: String,
        itemId: String,
        direction: String,
    ) {
        _sendJson { token ->
            _httpClient.patch(
                _uri("/me/music-box/playlists/$playlistId/items/order"),
                headers = _headers(token),
                body = encodeJsonBody(linkedMapOf("item_id" to itemId, "direction" to direction)),
            )
        }
    }

    override suspend fun reorderPersonalMusicPlaylistItems(
        playlistId: String,
        itemIds: List<String>,
    ) {
        _sendJson { token ->
            _httpClient.patch(
                _uri("/me/music-box/playlists/$playlistId/items/order"),
                headers = _headers(token),
                body = encodeJsonBody(mapOf("item_ids" to itemIds)),
            )
        }
    }

    override suspend fun listRoomMusicPlaylists(
        roomId: String,
        page: Int,
        pageSize: Int,
    ): PersonalMusicPlaylistPage {
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri(
                        "/rooms/$roomId/music-box/playlists",
                        linkedMapOf(
                            "page" to "$page",
                            "page_size" to "$pageSize",
                        ),
                    ),
                    headers = _headers(token),
                )
            }
        return PersonalMusicPlaylistPage.fromJson(decoded)
    }

    override suspend fun createRoomMusicPlaylist(
        roomId: String,
        name: String,
    ): PersonalMusicPlaylist {
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/music-box/playlists"),
                    headers = _headers(token),
                    body = encodeJsonBody(mapOf("name" to name)),
                )
            }
        return PersonalMusicPlaylist.fromJson(_jsonMap(decoded["playlist"]))
    }

    override suspend fun mergeRoomMusicPlaylists(
        roomId: String,
        name: String,
        playlistIds: List<String>,
    ): PersonalMusicPlaylistMergeResult {
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/music-box/playlists/merge"),
                    headers = _headers(token),
                    body = encodeJsonBody(linkedMapOf("name" to name, "playlist_ids" to playlistIds)),
                )
            }
        return PersonalMusicPlaylistMergeResult.fromJson(decoded)
    }

    override suspend fun createRoomMusicPlaylistFromPersonal(
        roomId: String,
        name: String,
        importPlaylistId: String,
    ): PersonalMusicPlaylist {
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/music-box/playlists"),
                    headers = _headers(token),
                    body =
                        encodeJsonBody(
                            linkedMapOf(
                                "name" to name,
                                "import_playlist_id" to importPlaylistId,
                            ),
                        ),
                )
            }
        return PersonalMusicPlaylist.fromJson(_jsonMap(decoded["playlist"]))
    }

    override suspend fun cloneRoomMusicPlaylistToPersonal(
        roomId: String,
        playlistId: String,
    ): PersonalMusicPlaylist {
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/music-box/playlists/$playlistId/clone-to-me"),
                    headers = _headers(token),
                )
            }
        return PersonalMusicPlaylist.fromJson(_jsonMap(decoded["playlist"]))
    }

    override suspend fun cloneSharedMusicPlaylistToPersonal(
        roomId: String,
        messageId: String,
    ): PersonalMusicPlaylist {
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/messages/$messageId/playlist/clone-to-me"),
                    headers = _headers(token),
                )
            }
        return PersonalMusicPlaylist.fromJson(_jsonMap(decoded["playlist"]))
    }

    override suspend fun renameRoomMusicPlaylist(
        roomId: String,
        playlistId: String,
        name: String,
    ): PersonalMusicPlaylist {
        val decoded =
            _sendJson { token ->
                _httpClient.patch(
                    _uri("/rooms/$roomId/music-box/playlists/$playlistId"),
                    headers = _headers(token),
                    body = encodeJsonBody(mapOf("name" to name)),
                )
            }
        return PersonalMusicPlaylist.fromJson(_jsonMap(decoded["playlist"]))
    }

    override suspend fun deleteRoomMusicPlaylist(
        roomId: String,
        playlistId: String,
    ) {
        _sendJson { token ->
            _httpClient.delete(
                _uri("/rooms/$roomId/music-box/playlists/$playlistId"),
                headers = _headers(token),
            )
        }
    }

    override suspend fun pinRoomMusicPlaylists(
        roomId: String,
        playlistIds: List<String>,
    ) {
        _sendJson { token ->
            _httpClient.patch(
                _uri("/rooms/$roomId/music-box/playlists/order"),
                headers = _headers(token),
                body = encodeJsonBody(mapOf("playlist_ids" to playlistIds)),
            )
        }
    }

    override suspend fun moveRoomMusicPlaylist(
        roomId: String,
        playlistId: String,
        direction: String,
    ) {
        _sendJson { token ->
            _httpClient.patch(
                _uri("/rooms/$roomId/music-box/playlists/order"),
                headers = _headers(token),
                body =
                    encodeJsonBody(
                        linkedMapOf(
                            "playlist_id" to playlistId,
                            "direction" to direction,
                        ),
                    ),
            )
        }
    }

    override suspend fun getRoomMusicPlaylist(
        roomId: String,
        playlistId: String,
        page: Int,
        pageSize: Int,
        keyword: String?,
        source: String?,
    ): PersonalMusicPlaylistItemsPage {
        val query = linkedMapOf("page" to "$page", "page_size" to "$pageSize")
        if (keyword != null && keyword.trim().isNotEmpty()) {
            query["keyword"] = keyword.trim()
        }
        if (source != null && source.trim().isNotEmpty()) {
            query["source"] = source.trim()
        }
        val decoded =
            _sendJson(retryTransientFailures = true) { token ->
                _httpClient.get(
                    _uri("/rooms/$roomId/music-box/playlists/$playlistId", query),
                    headers = _headers(token),
                )
            }
        return PersonalMusicPlaylistItemsPage.fromJson(decoded)
    }

    override suspend fun addRoomMusicPlaylistItem(
        roomId: String,
        playlistId: String,
        track: MusicBoxSearchResult,
        durationMs: Int?,
    ): PersonalMusicPlaylistItem {
        val body =
            linkedMapOf<String, Any?>(
                "track_id" to track.trackId,
                "source" to track.source,
                "title" to track.name,
                "artists" to track.artists,
            )
        if (durationMs != null) body["duration_ms"] = durationMs
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/music-box/playlists/$playlistId/items"),
                    headers = _headers(token),
                    body = encodeJsonBody(body),
                )
            }
        return PersonalMusicPlaylistItem.fromJson(_jsonMap(decoded["item"]))
    }

    override suspend fun batchAddRoomMusicPlaylistItems(
        roomId: String,
        sourcePlaylistId: String,
        targetPlaylistId: String,
        itemIds: List<String>,
    ): PersonalMusicPlaylistBatchAddResult {
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri(
                        "/rooms/$roomId/music-box/playlists/" +
                            "$targetPlaylistId/items/batch-add",
                    ),
                    headers = _headers(token),
                    body =
                        encodeJsonBody(
                            linkedMapOf(
                                "source_playlist_id" to sourcePlaylistId,
                                "item_ids" to itemIds,
                            ),
                        ),
                )
            }
        return PersonalMusicPlaylistBatchAddResult.fromJson(decoded)
    }

    override suspend fun deleteRoomMusicPlaylistItem(
        roomId: String,
        playlistId: String,
        itemId: String,
    ) {
        _sendJson { token ->
            _httpClient.delete(
                _uri("/rooms/$roomId/music-box/playlists/$playlistId/items/$itemId"),
                headers = _headers(token),
            )
        }
    }

    override suspend fun deleteRoomMusicPlaylistItems(
        roomId: String,
        playlistId: String,
        itemIds: List<String>,
    ) {
        _sendJson { token ->
            _httpClient.delete(
                _uri("/rooms/$roomId/music-box/playlists/$playlistId/items"),
                headers = _headers(token),
                body = encodeJsonBody(mapOf("item_ids" to itemIds)),
            )
        }
    }

    override suspend fun moveRoomMusicPlaylistItem(
        roomId: String,
        playlistId: String,
        itemId: String,
        direction: String,
    ) {
        _sendJson { token ->
            _httpClient.patch(
                _uri("/rooms/$roomId/music-box/playlists/$playlistId/items/order"),
                headers = _headers(token),
                body = encodeJsonBody(linkedMapOf("item_id" to itemId, "direction" to direction)),
            )
        }
    }

    override suspend fun reorderRoomMusicPlaylistItems(
        roomId: String,
        playlistId: String,
        itemIds: List<String>,
    ) {
        _sendJson { token ->
            _httpClient.patch(
                _uri("/rooms/$roomId/music-box/playlists/$playlistId/items/order"),
                headers = _headers(token),
                body = encodeJsonBody(mapOf("item_ids" to itemIds)),
            )
        }
    }

    override suspend fun queueMusicBoxTrack(
        roomId: String,
        trackId: String,
        title: String,
        source: String?,
        artist: String?,
        durationMs: Int?,
        idempotencyKey: String?,
    ): MusicBoxState {
        val body = linkedMapOf<String, Any?>("track_id" to trackId, "title" to title)
        if (source != null && source.isNotEmpty()) body["source"] = source
        if (artist != null && artist.isNotEmpty()) body["artist"] = artist
        if (durationMs != null) body["duration_ms"] = durationMs
        val requestIdempotencyKey = idempotencyKey ?: newUuid()
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/music-box/queue"),
                    headers = _headers(token, idempotencyKey = requestIdempotencyKey),
                    body = encodeJsonBody(body),
                )
            }
        return MusicBoxState.fromJson(decoded)
    }

    override suspend fun removeMusicBoxItem(
        roomId: String,
        itemId: String,
    ): MusicBoxState {
        val decoded =
            _sendJson { token ->
                _httpClient.delete(
                    _uri("/rooms/$roomId/music-box/queue/$itemId"),
                    headers = _headers(token),
                )
            }
        return MusicBoxState.fromJson(decoded)
    }

    override suspend fun controlMusicBox(
        roomId: String,
        action: String,
        itemId: String?,
        mode: String?,
        commandId: String?,
        expectedRevision: Int?,
    ): MusicBoxState {
        val decoded =
            _sendJson { token ->
                val body = linkedMapOf<String, Any?>("action" to action)
                if (itemId != null && itemId.trim().isNotEmpty()) {
                    body["item_id"] = itemId.trim()
                }
                if (mode != null && mode.trim().isNotEmpty()) {
                    body["mode"] = mode.trim()
                }
                if (commandId != null && commandId.trim().isNotEmpty()) {
                    body["command_id"] = commandId.trim()
                }
                if (expectedRevision != null) {
                    body["expected_revision"] = expectedRevision
                }
                _httpClient.post(
                    _uri("/rooms/$roomId/music-box/control"),
                    headers = _headers(token),
                    body = encodeJsonBody(body),
                )
            }
        return MusicBoxState.fromJson(decoded)
    }

    override suspend fun activateMusicBoxPlaylist(
        roomId: String,
        sourceType: MusicBoxActiveSourceType,
        playlistId: String?,
        startPlay: Boolean,
        startItemId: String?,
    ): MusicBoxState {
        val body =
            linkedMapOf<String, Any?>(
                "source_type" to musicBoxActiveSourceTypeValue(sourceType),
                "start_play" to startPlay,
            )
        if (playlistId != null && playlistId.trim().isNotEmpty()) {
            body["playlist_id"] = playlistId.trim()
        }
        if (startItemId != null && startItemId.trim().isNotEmpty()) {
            body["start_item_id"] = startItemId.trim()
        }
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/music-box/activate"),
                    headers = _headers(token),
                    body = encodeJsonBody(body),
                )
            }
        return MusicBoxState.fromJson(decoded)
    }

    override suspend fun cloneActiveMusicBoxPlaylist(
        roomId: String,
        playlistId: String,
        snapshotId: String,
    ): PersonalMusicPlaylist {
        val decoded =
            _sendJson { token ->
                _httpClient.post(
                    _uri("/rooms/$roomId/music-box/active-playlist/clone"),
                    headers = _headers(token),
                    body =
                        encodeJsonBody(
                            linkedMapOf(
                                "playlist_id" to playlistId,
                                "snapshot_id" to snapshotId,
                            ),
                        ),
                )
            }
        return PersonalMusicPlaylist.fromJson(_jsonMap(decoded["playlist"]))
    }

    private suspend fun _sendJson(
        retryTransientFailures: Boolean = false,
        send: suspend (accessToken: String) -> HttpResponse,
    ): JsonMap {
        val decoded =
            _sendJsonValue(
                retryTransientFailures = retryTransientFailures,
                send = send,
            )
        return _jsonMap(decoded)
    }

    private suspend fun _sendJsonValue(
        retryTransientFailures: Boolean = false,
        send: suspend (accessToken: String) -> HttpResponse,
    ): Any? {
        var response: HttpResponse
        try {
            response = _sendWithAuth(send)
        } catch (e: ClientException) {
            if (!retryTransientFailures || !_isRetryableTransportFailure(e)) {
                throw e
            }
            response = _sendWithAuth(send)
        }
        if (retryTransientFailures &&
            _isRetryableHttpFailure(response.statusCode)
        ) {
            response = _sendWithAuth(send)
        }
        _throwIfFailed(response)
        val decoded = decodeJsonBody(response)
        return decoded ?: LinkedHashMap<String, Any?>()
    }

    private suspend fun _sendWithAuth(
        send: suspend (accessToken: String) -> HttpResponse,
    ): HttpResponse {
        var token = accessTokenProvider()
        val stopwatch = Stopwatch().start()
        var response = send(token)
        if (response.statusCode == 401) {
            token = accessTokenProvider(forceRefresh = true)
            response = send(token)
        }
        stopwatch.stop()
        onRequestLatency?.invoke(stopwatch.elapsed)
        onServerTime?.invoke(response.headers[gangServerTimeHeader])
        return response
    }

    private fun _isRetryableTransportFailure(error: ClientException): Boolean {
        val message = error.message.lowercase()
        return message.contains("connection closed") ||
            message.contains("connection reset") ||
            message.contains("broken pipe") ||
            message.contains("write failed") ||
            message.contains("connection abort") ||
            message.contains("errno = 10053") ||
            message.contains("errno=10053")
    }

    private fun _isRetryableHttpFailure(statusCode: Int): Boolean =
        statusCode == 502 || statusCode == 503 || statusCode == 504

    private fun _throwIfFailed(response: HttpResponse) {
        if (response.statusCode >= 200 && response.statusCode < 300) return
        throw ApiException.fromResponse(response)
    }

    private fun _uri(path: String, query: Map<String, String>? = null): Uri {
        val normalizedBase =
            if (baseUrl.endsWith("/")) baseUrl.substring(0, baseUrl.length - 1) else baseUrl
        val uri = Uri.parse("$normalizedBase$path")
        if (query == null || query.isEmpty()) return uri
        return uri.replace(queryParameters = query)
    }

    private fun _headers(accessToken: String, idempotencyKey: String? = null): Map<String, String> {
        val headers =
            linkedMapOf(
                "authorization" to "Bearer $accessToken",
                "accept" to jsonAcceptHeader,
                "content-type" to jsonUtf8ContentType,
            )
        if (idempotencyKey != null) headers["idempotency-key"] = idempotencyKey
        return headers
    }

    private fun _downloadFilename(response: HttpResponse): String {
        val disposition = response.headers["content-disposition"] ?: ""
        val encoded =
            Regex(
                "filename\\*=UTF-8''([^;]+)",
                RegexOption.IGNORE_CASE,
            ).find(disposition)
        if (encoded != null) {
            return Uri.decodeComponent(encoded.groupValues[1])
        }
        val quoted = Regex("filename=\"([^\"]+)\"").find(disposition)
        if (quoted != null) return quoted.groupValues[1]
        val unquoted = Regex("filename=([^;]+)").find(disposition)
        if (unquoted != null) return unquoted.groupValues[1].trim()
        val mimeType = response.headers["content-type"] ?: ""
        return if (mimeType.contains("zip")) "stickers.zip" else "sticker"
    }

    override fun close() = _httpClient.close()
}

class RoomPage(val rooms: List<RoomCard>, val nextCursor: String?) {
    companion object {
        fun fromJson(json: JsonMap): RoomPage =
            RoomPage(
                rooms = (json["rooms"] as List<*>).map { RoomCard.fromJson(_jsonMap(it)) },
                nextCursor = json["next_cursor"] as String?,
            )
    }
}

class MessagePage(
    val messages: List<Message>,
    val hasMore: Boolean,
    val nextBefore: String?,
) {
    companion object {
        fun fromJson(json: JsonMap): MessagePage =
            MessagePage(
                messages = (json["messages"] as List<*>).map { Message.fromJson(_jsonMap(it)) },
                hasMore = json["has_more"] as Boolean,
                nextBefore = json["next_before"] as String?,
            )
    }
}

class ApiException(
    override val message: String,
    val statusCode: Int,
    val code: String,
    val requestId: String?,
) : Exception(message) {
    val isUnauthorized: Boolean get() = statusCode == 401

    override fun toString(): String {
        if (requestId == null) return message
        return "$message（请求编号：$requestId）"
    }

    companion object {
        fun fromResponse(response: HttpResponse): ApiException {
            val headerRequestId = response.headers["x-request-id"]
            try {
                val decoded = _jsonMap(decodeJsonBody(response))
                val error = _jsonMapOrNull(decoded["error"])
                val message = error?.get("message") as String?
                val code = error?.get("code") as String?
                val requestId = error?.get("request_id") as String? ?: headerRequestId
                if (message != null && message.isNotEmpty()) {
                    return ApiException(
                        localizedServerErrorMessage(
                            code = code ?: "request_failed",
                            statusCode = response.statusCode,
                            message = message,
                        ),
                        statusCode = response.statusCode,
                        code = code ?: "request_failed",
                        requestId = requestId,
                    )
                }
            } catch (_: Exception) {
                // Fall through to the status-based message.
            }
            return ApiException(
                "请求失败 (${response.statusCode})",
                statusCode = response.statusCode,
                code = "request_failed",
                requestId = headerRequestId,
            )
        }
    }
}

private fun _uploadByteStream(
    bytes: ByteArray,
    onProgress: UploadProgressCallback? = null,
    controller: UploadTransferController? = null,
): ByteStream =
    ByteStream(
        flow {
            val chunkSize = 64 * 1024
            val total = bytes.size
            onProgress?.invoke(sentBytes = 0L, totalBytes = total.toLong())
            var sent = 0
            while (sent < total) {
                if (controller?.isCancelled == true) {
                    throw UploadCancelledException()
                }
                controller?.waitIfPaused()
                if (controller?.isCancelled == true) {
                    throw UploadCancelledException()
                }
                val end = if ((sent + chunkSize) > total) total else sent + chunkSize
                emit(bytes.copyOfRange(sent, end))
                sent = end
                onProgress?.invoke(sentBytes = sent.toLong(), totalBytes = total.toLong())
            }
        },
    )

fun newClientId(prefix: String): String = "${prefix}_${newUuid()}"

private val _secureRandom = SecureRandom()

fun newUuid(): String {
    val bytes = IntArray(16) { _secureRandom.nextInt(256) }
    bytes[6] = (bytes[6] and 0x0f) or 0x40
    bytes[8] = (bytes[8] and 0x3f) or 0x80

    fun hex(byte: Int): String = byte.toString(16).padStart(2, '0')
    val value = bytes.joinToString("") { hex(it) }
    return listOf(
        value.substring(0, 8),
        value.substring(8, 12),
        value.substring(12, 16),
        value.substring(16, 20),
        value.substring(20),
    ).joinToString("-")
}

/** Dart `value as Map<String, Object?>` (throws when it is not a map). */
@Suppress("UNCHECKED_CAST")
private fun _jsonMap(value: Any?): JsonMap = value as Map<String, Any?>

/** Dart `value as Map<String, Object?>?`. */
@Suppress("UNCHECKED_CAST")
private fun _jsonMapOrNull(value: Any?): JsonMap? = value as Map<String, Any?>?

/** Dart `(value as List<Object?>? ?? const []).cast<Map<String, Object?>>()`. */
private fun _jsonMapList(value: Any?): List<JsonMap> =
    ((value as List<*>?) ?: emptyList<Any?>()).map { _jsonMap(it) }

/** `listSessions` accepts a bare array or `{items}` / `{sessions}`. */
private fun _sessionItems(decoded: Any?): List<JsonMap> {
    val items: List<*> =
        when (decoded) {
            is List<*> -> decoded
            is Map<*, *> ->
                decoded["items"] as List<*>?
                    ?: decoded["sessions"] as List<*>?
                    ?: emptyList<Any?>()
            else -> emptyList<Any?>()
        }
    return items.map { _jsonMap(it) }
}
