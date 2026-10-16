package com.gangchat.client.protocol

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.JsonMap

const val defaultUserLanguage = "zh-Hans"

class AppVersionInfo(
    val latestVersion: String,
    val minimumSupportedVersion: String? = null,
    val releaseNotes: String? = null,
    val downloadUrl: String? = null,
) {
    companion object {
        fun fromJson(json: JsonMap): AppVersionInfo =
            AppVersionInfo(
                latestVersion = _stringFromJson(json, listOf("latest_version", "version")) ?: "",
                minimumSupportedVersion =
                    _stringFromJson(
                        json,
                        listOf(
                            "minimum_supported_version",
                            "minimum_version",
                        ),
                    ),
                releaseNotes = _stringFromJson(json, listOf("release_notes", "notes")),
                downloadUrl = _stringFromJson(json, listOf("download_url")),
            )
    }
}

class UserCommonRoom(
    val id: String,
    val rid: String,
    val name: String,
    val visibility: String = "private",
    val remarkName: String? = null,
    val avatarUrl: String? = null,
    val defaultAvatarKey: String = "blue-3",
    val roomDisplayName: String? = null,
    val roomRole: String? = null,
) {
    val isUsable: Boolean get() = id.trim().isNotEmpty() && name.trim().isNotEmpty()

    companion object {
        fun fromJson(json: JsonMap): UserCommonRoom =
            UserCommonRoom(
                id = _stringFromJson(json, listOf("id", "room_id")) ?: "",
                rid = _stringFromJson(json, listOf("rid", "room_rid")) ?: "",
                name = _stringFromJson(json, listOf("name", "room_name")) ?: "",
                visibility =
                    _stringFromJson(json, listOf("visibility", "room_visibility"))
                        ?: "private",
                remarkName =
                    _stringFromJson(
                        json,
                        listOf(
                            "remark_name",
                            "room_remark",
                            "room_remark_name",
                        ),
                    ),
                avatarUrl = _stringFromJson(json, listOf("avatar_url", "room_avatar_url")),
                defaultAvatarKey =
                    _stringFromJson(
                        json,
                        listOf(
                            "default_avatar_key",
                            "room_default_avatar_key",
                        ),
                    ) ?: "blue-3",
                roomDisplayName =
                    _stringFromJson(
                        json,
                        listOf(
                            "room_display_name",
                            "room_username",
                            "room_nickname",
                            "room_remark",
                            "member_display_name",
                        ),
                    ),
                roomRole =
                    _stringFromJson(json, listOf("room_role", "membership_role"))
                        ?: _stringFromJson(_nullableMap(json["membership"]), listOf("role")),
            )
    }
}

class UserSummary(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val defaultAvatarKey: String,
    val uid: String? = null,
    val bio: String? = null,
    val gender: String? = null,
    val email: String? = null,
    val emailPublic: Boolean? = null,
    val phoneNumber: String? = null,
    val phoneNumberPublic: Boolean? = null,
    val roomDisplayName: String? = null,
    val roomRole: String? = null,
    val isSuperuser: Boolean = false,
    val isOnline: Boolean? = null,
    val commonRooms: List<UserCommonRoom> = emptyList(),
    val isDeleted: Boolean = false,
    val isSuspended: Boolean = false,
    /**
     * gang-bot, the room AI. It is not a user account: there is no profile to
     * open, and its messages carry an AI badge.
     */
    val isBot: Boolean = false,
) {
    fun copyWith(
        displayName: String? = null,
        avatarUrl: String? = null,
        defaultAvatarKey: String? = null,
        uid: String? = null,
        bio: String? = null,
        gender: String? = null,
        email: String? = null,
        emailPublic: Boolean? = null,
        phoneNumber: String? = null,
        phoneNumberPublic: Boolean? = null,
        roomDisplayName: String? = null,
        roomRole: String? = null,
        isSuperuser: Boolean? = null,
        isOnline: Boolean? = null,
        commonRooms: List<UserCommonRoom>? = null,
        isDeleted: Boolean? = null,
        isSuspended: Boolean? = null,
        isBot: Boolean? = null,
    ): UserSummary =
        UserSummary(
            id = id,
            username = username,
            displayName = displayName ?: this.displayName,
            avatarUrl = avatarUrl ?: this.avatarUrl,
            defaultAvatarKey = defaultAvatarKey ?: this.defaultAvatarKey,
            uid = uid ?: this.uid,
            bio = bio ?: this.bio,
            gender = gender ?: this.gender,
            email = email ?: this.email,
            emailPublic = emailPublic ?: this.emailPublic,
            phoneNumber = phoneNumber ?: this.phoneNumber,
            phoneNumberPublic = phoneNumberPublic ?: this.phoneNumberPublic,
            roomDisplayName = roomDisplayName ?: this.roomDisplayName,
            roomRole = roomRole ?: this.roomRole,
            isSuperuser = isSuperuser ?: this.isSuperuser,
            isOnline = isOnline ?: this.isOnline,
            commonRooms = commonRooms ?: this.commonRooms,
            isDeleted = isDeleted ?: this.isDeleted,
            isSuspended = isSuspended ?: this.isSuspended,
            isBot = isBot ?: this.isBot,
        )

    fun mergeMissing(fallback: UserSummary): UserSummary {
        if (isDeleted) return this
        if (fallback.isDeleted) return fallback
        fun nonEmptyOrFallback(value: String?, fallback: String?): String? {
            if (value != null && value.trim().isNotEmpty()) return value
            return fallback
        }

        return UserSummary(
            id = id,
            username = username,
            displayName = if (displayName.trim().isEmpty()) fallback.displayName else displayName,
            avatarUrl = avatarUrl ?: fallback.avatarUrl,
            defaultAvatarKey = defaultAvatarKey,
            uid = nonEmptyOrFallback(uid, fallback.uid),
            bio = nonEmptyOrFallback(bio, fallback.bio),
            gender = nonEmptyOrFallback(gender, fallback.gender),
            email = nonEmptyOrFallback(email, fallback.email),
            emailPublic = emailPublic ?: fallback.emailPublic,
            phoneNumber = nonEmptyOrFallback(phoneNumber, fallback.phoneNumber),
            phoneNumberPublic = phoneNumberPublic ?: fallback.phoneNumberPublic,
            roomDisplayName = nonEmptyOrFallback(roomDisplayName, fallback.roomDisplayName),
            roomRole = nonEmptyOrFallback(roomRole, fallback.roomRole),
            isSuperuser = isSuperuser || fallback.isSuperuser,
            isOnline = isOnline ?: fallback.isOnline,
            commonRooms = if (commonRooms.isNotEmpty()) commonRooms else fallback.commonRooms,
            isDeleted = false,
            isSuspended = isSuspended || fallback.isSuspended,
            isBot = isBot || fallback.isBot,
        )
    }

    companion object {
        fun fromJson(json: JsonMap): UserSummary {
            val username = json["username"] as String
            return UserSummary(
                id = json["id"] as String,
                username = username,
                displayName = json["display_name"] as String? ?: username,
                avatarUrl = json["avatar_url"] as String?,
                defaultAvatarKey = json["default_avatar_key"] as String? ?: "blue-3",
                uid = _stringFromJson(json, listOf("uid", "user_uid")),
                bio = _stringFromJson(json, listOf("bio", "signature")),
                gender = _stringFromJson(json, listOf("gender")),
                email = _stringFromJson(json, listOf("email")),
                emailPublic = _boolFromJson(json, listOf("email_public")),
                phoneNumber = _stringFromJson(json, listOf("phone_number", "phone")),
                phoneNumberPublic =
                    _boolFromJson(
                        json,
                        listOf(
                            "phone_number_public",
                            "phone_public",
                        ),
                    ),
                roomDisplayName =
                    _stringFromJson(
                        json,
                        listOf(
                            "room_display_name",
                            "room_username",
                            "room_nickname",
                            "room_remark",
                            "remark_name",
                            "remark",
                        ),
                    ),
                roomRole =
                    _stringFromJson(json, listOf("room_role", "membership_role"))
                        ?: _stringFromJson(_nullableMap(json["membership"]), listOf("role"))
                        ?: _stringFromJson(json, listOf("role")),
                isSuperuser = json["is_superuser"] as Boolean? ?: false,
                isOnline =
                    _boolFromJson(json, listOf("is_online", "online"))
                        ?: _onlineFromStatus(
                            _stringFromJson(
                                json,
                                listOf(
                                    "presence",
                                    "presence_status",
                                    "status",
                                    "connection_state",
                                ),
                            ),
                        ),
                commonRooms =
                    _listOfMaps(json["common_rooms"])
                        .map { UserCommonRoom.fromJson(it) }
                        .filter { room -> room.isUsable },
                isDeleted = json["is_deleted"] as Boolean? ?: false,
                isSuspended =
                    json["is_suspended"] as Boolean?
                        ?: (_stringFromJson(json, listOf("account_status")) == "suspended"),
                isBot = json["is_bot"] as Boolean? ?: false,
            )
        }
    }
}

class CurrentUser(
    val id: String,
    val uid: String,
    val username: String,
    val displayName: String,
    val bio: String,
    val gender: String,
    val email: String?,
    val emailVerified: Boolean = false,
    val emailPublic: Boolean,
    val phoneNumber: String?,
    val phoneNumberPublic: Boolean,
    val avatarUrl: String?,
    val defaultAvatarKey: String,
    val isSuperuser: Boolean,
    val createdAt: DateTime?,
    val language: String = defaultUserLanguage,
    val usernameUpdatedAt: DateTime? = null,
    val canChangeUsernameAt: DateTime? = null,
    val status: String? = null,
) {
    fun toSummary(): UserSummary =
        UserSummary(
            id = id,
            uid = uid,
            username = username,
            displayName = displayName,
            bio = bio,
            gender = gender,
            email = email,
            emailPublic = emailPublic,
            phoneNumber = phoneNumber,
            phoneNumberPublic = phoneNumberPublic,
            avatarUrl = avatarUrl,
            defaultAvatarKey = defaultAvatarKey,
            isSuperuser = isSuperuser,
            isOnline = _onlineFromStatus(status) ?: true,
            isSuspended = status?.trim()?.lowercase() == "suspended",
        )

    companion object {
        fun fromJson(json: JsonMap): CurrentUser {
            val username = json["username"] as String
            val id = json["id"] as String
            return CurrentUser(
                id = id,
                uid = json["uid"] as String? ?: id,
                username = username,
                displayName = json["display_name"] as String? ?: username,
                bio = json["bio"] as String? ?: "",
                gender = json["gender"] as String? ?: "secret",
                email = json["email"] as String?,
                emailVerified = json["email_verified"] as Boolean? ?: false,
                emailPublic = json["email_public"] as Boolean? ?: false,
                phoneNumber = json["phone_number"] as String?,
                phoneNumberPublic = json["phone_number_public"] as Boolean? ?: false,
                avatarUrl = json["avatar_url"] as String?,
                defaultAvatarKey = json["default_avatar_key"] as String? ?: "blue-3",
                isSuperuser = json["is_superuser"] as Boolean? ?: false,
                language =
                    _stringFromJson(json, listOf("language", "locale"))
                        ?: defaultUserLanguage,
                usernameUpdatedAt = _parseDateTime(json["username_updated_at"]),
                canChangeUsernameAt = _parseDateTime(json["can_change_username_at"]),
                createdAt = _parseDateTime(json["created_at"]),
                status = json["status"] as String?,
            )
        }
    }
}

class UserSearchPage(
    val users: List<UserSummary>,
    val nextCursor: String? = null,
    val totalCount: Int? = null,
) {
    companion object {
        fun fromJson(json: JsonMap): UserSearchPage =
            UserSearchPage(
                users = _listOfMaps(json["users"]).map { UserSummary.fromJson(it) },
                nextCursor = json["next_cursor"] as String?,
                totalCount = _nullableInt(json["total_count"]),
            )
    }
}

class UserAudioSettings(
    val defaultAudioInputVolume: Int,
    val defaultAudioOutputVolume: Int,
    val liveMicInputVolume: Int,
    val liveVoiceOutputVolume: Int,
    val liveScreenShareOutputVolume: Int,
    val liveMusicOutputVolume: Int,
    val updatedAt: DateTime? = null,
) {
    companion object {
        fun fromJson(json: JsonMap): UserAudioSettings {
            fun volume(key: String): Int = _nullableInt(json[key]) ?: 100
            return UserAudioSettings(
                defaultAudioInputVolume = volume("default_audio_input_volume"),
                defaultAudioOutputVolume = volume("default_audio_output_volume"),
                liveMicInputVolume = volume("live_mic_input_volume"),
                liveVoiceOutputVolume = volume("live_voice_output_volume"),
                liveScreenShareOutputVolume = volume("live_screen_share_output_volume"),
                liveMusicOutputVolume = volume("live_music_output_volume"),
                updatedAt = _parseDateTime(json["updated_at"]),
            )
        }
    }
}

class UserSession(
    val id: String,
    val userAgent: String?,
    val ipAddress: String?,
    val location: String,
    val createdAt: DateTime,
    val lastUsedAt: DateTime,
    val expiresAt: DateTime,
    val revokedAt: DateTime?,
    val isCurrent: Boolean,
) {
    val isActive: Boolean get() = revokedAt == null && expiresAt.isAfter(DateTime.now())

    companion object {
        fun fromJson(json: JsonMap): UserSession =
            UserSession(
                id = json["id"] as String,
                userAgent = json["user_agent"] as String?,
                ipAddress = json["ip_address"] as String?,
                location = json["location"] as String? ?: "未知地点",
                createdAt = _fromUnixSeconds((json["created_at"] as Number).toLong()),
                lastUsedAt = _fromUnixSeconds((json["last_used_at"] as Number).toLong()),
                expiresAt = _fromUnixSeconds((json["expires_at"] as Number).toLong()),
                revokedAt = _nullableUnixSeconds(json["revoked_at"]),
                isCurrent = json["is_current"] as Boolean,
            )
    }
}

class UploadedAsset(
    val id: String,
    val url: String,
    val thumbnailUrl: String?,
    val mimeType: String,
    val filename: String? = null,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val createdAt: DateTime? = null,
) {
    fun toJson(): JsonMap =
        buildMap {
            put("id", id)
            put("url", url)
            put("thumbnail_url", thumbnailUrl)
            put("mime_type", mimeType)
            if (filename != null) put("filename", filename)
            if (sizeBytes != null) put("size_bytes", sizeBytes)
            put("width", width)
            put("height", height)
            put("created_at", createdAt?.toIso8601String())
        }

    companion object {
        fun fromJson(json: JsonMap): UploadedAsset =
            UploadedAsset(
                id = json["id"] as String,
                url = json["url"] as String,
                thumbnailUrl = json["thumbnail_url"] as String?,
                mimeType = json["mime_type"] as String? ?: "application/octet-stream",
                filename = json["filename"] as String?,
                sizeBytes = _nullableLong(json["size_bytes"]),
                width = _nullableInt(json["width"]),
                height = _nullableInt(json["height"]),
                createdAt = _parseDateTime(json["created_at"]),
            )
    }
}

class StickerPack(
    val id: String,
    val scope: String,
    val roomId: String?,
    val name: String,
    val stickers: List<Sticker>,
    val sortOrder: Int,
    val updatedAt: DateTime?,
) {
    fun toJson(): JsonMap =
        buildMap {
            put("id", id)
            put("scope", scope)
            put("room_id", roomId)
            put("name", name)
            put("stickers", stickers.map { sticker -> sticker.toJson() })
            put("sort_order", sortOrder)
            put("updated_at", updatedAt?.toIso8601String())
        }

    companion object {
        fun fromJson(json: JsonMap): StickerPack =
            StickerPack(
                id = json["id"] as String,
                scope = json["scope"] as String? ?: "personal",
                roomId = json["room_id"] as String?,
                name = json["name"] as String? ?: "Stickers",
                stickers = _listOfMaps(json["stickers"]).map { Sticker.fromJson(it) },
                sortOrder = _nullableInt(json["sort_order"]) ?: 10,
                updatedAt = _parseDateTime(json["updated_at"]),
            )
    }
}

class Sticker(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val asset: UploadedAsset,
) {
    fun toJson(): JsonMap =
        buildMap {
            put("id", id)
            put("name", name)
            put("sort_order", sortOrder)
            put("asset", asset.toJson())
        }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonMap): Sticker =
            Sticker(
                id = json["id"] as String,
                name = json["name"] as String? ?: "sticker",
                sortOrder = _nullableInt(json["sort_order"]) ?: 10,
                asset = UploadedAsset.fromJson(json["asset"] as Map<String, Any?>),
            )
    }
}

class MessageAttachment(
    val type: String,
    val playlistId: String? = null,
    val playlistScope: String? = null,
    val sourceRoomId: String? = null,
    val sourceMessageId: String? = null,
    val itemId: String? = null,
    val playlist: SharedMusicPlaylist? = null,
    val track: SharedMusicTrack? = null,
    val stickerId: String? = null,
    val name: String? = null,
    val asset: UploadedAsset? = null,
    val durationMs: Int? = null,
    val event: String? = null,
    val user: UserSummary? = null,
    val actor: UserSummary? = null,
    val target: UserSummary? = null,
    val fromRole: String? = null,
    val toRole: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
) {
    fun toJson(): JsonMap =
        buildMap {
            put("type", type)
            if (playlistId != null) put("playlist_id", playlistId)
            if (playlistScope != null) put("playlist_scope", playlistScope)
            if (sourceRoomId != null) put("source_room_id", sourceRoomId)
            if (sourceMessageId != null) put("source_message_id", sourceMessageId)
            if (itemId != null) put("item_id", itemId)
            if (playlist != null) put("playlist", playlist.toJson())
            if (track != null) put("track", track.toJson())
            if (stickerId != null) put("sticker_id", stickerId)
            if (name != null) put("name", name)
            if (asset != null) put("asset", asset.toJson())
            if (durationMs != null) put("duration_ms", durationMs)
            if (event != null) put("event", event)
            if (user != null) put("user", _userSummaryToJson(user))
            if (actor != null) put("actor", _userSummaryToJson(actor))
            if (target != null) put("target", _userSummaryToJson(target))
            if (fromRole != null) put("from_role", fromRole)
            if (toRole != null) put("to_role", toRole)
            if (oldValue != null) put("old_value", oldValue)
            if (newValue != null) put("new_value", newValue)
        }

    companion object {
        fun fromJson(json: JsonMap): MessageAttachment {
            val assetJson = _nullableMap(json["asset"])
            val userJson = _nullableMap(json["user"])
            val actorJson = _nullableMap(json["actor"])
            val targetJson = _nullableMap(json["target"])
            val playlistJson = _nullableMap(json["playlist"])
            val trackJson = _nullableMap(json["track"])
            return MessageAttachment(
                type = json["type"] as String? ?: "file",
                playlistId = _stringFromJson(json, listOf("playlist_id")),
                playlistScope = _stringFromJson(json, listOf("playlist_scope")),
                sourceRoomId = _stringFromJson(json, listOf("source_room_id")),
                sourceMessageId = _stringFromJson(json, listOf("source_message_id")),
                itemId = _stringFromJson(json, listOf("item_id")),
                playlist = if (playlistJson == null) null else SharedMusicPlaylist.fromJson(playlistJson),
                track = if (trackJson == null) null else SharedMusicTrack.fromJson(trackJson),
                stickerId = json["sticker_id"] as String?,
                name = json["name"] as String?,
                asset = if (assetJson == null) null else UploadedAsset.fromJson(assetJson),
                durationMs = _nullableInt(json["duration_ms"]),
                event = _stringFromJson(json, listOf("event", "system_event")),
                user = if (userJson == null) null else UserSummary.fromJson(userJson),
                actor = if (actorJson == null) null else UserSummary.fromJson(actorJson),
                target = if (targetJson == null) null else UserSummary.fromJson(targetJson),
                fromRole = _stringFromJson(json, listOf("from_role", "previous_role")),
                toRole = _stringFromJson(json, listOf("to_role", "role")),
                oldValue = _stringFromJson(json, listOf("old_value", "previous_value")),
                newValue = _stringFromJson(json, listOf("new_value", "value")),
            )
        }
    }
}

/** Immutable song metadata embedded in a chat message at send time. */
class SharedMusicTrack(
    val id: String,
    val playlistId: String,
    val trackId: String,
    val source: String,
    val title: String,
    val artists: List<String>,
    val durationMs: Int,
) {
    fun toJson(): JsonMap =
        buildMap {
            put("id", id)
            put("playlist_id", playlistId)
            put("track_id", trackId)
            put("source", source)
            put("title", title)
            put("artists", artists)
            put("duration_ms", durationMs)
        }

    companion object {
        fun fromJson(json: JsonMap): SharedMusicTrack =
            SharedMusicTrack(
                id = _stringFromJson(json, listOf("id")) ?: "",
                playlistId = _stringFromJson(json, listOf("playlist_id")) ?: "",
                trackId = _stringFromJson(json, listOf("track_id")) ?: "",
                source = _stringFromJson(json, listOf("source")) ?: "",
                title = _stringFromJson(json, listOf("title", "name")) ?: "",
                artists = _artistsFromJson(json["artists"]),
                durationMs = _intFromJson(json, listOf("duration_ms")) ?: 0,
            )
    }
}

/**
 * Immutable playlist data embedded in a chat message at send time.
 *
 * It is intentionally independent from the current user's playlist APIs: the
 * source playlist may later be renamed or deleted, while message history must
 * remain viewable and must never expose another user's management endpoint.
 */
class SharedMusicPlaylist(
    val id: String,
    val name: String,
    val description: String,
    val itemCount: Int,
    val createdAt: DateTime?,
    val creator: UserSummary,
    val items: List<PersonalMusicPlaylistItem>,
) {
    fun toJson(): JsonMap =
        buildMap {
            put("id", id)
            put("name", name)
            put("description", description)
            put("item_count", itemCount)
            put("created_at", createdAt?.toIso8601String())
            put("creator", _userSummaryToJson(creator))
            put(
                "items",
                items.map { item ->
                    buildMap<String, Any?> {
                        put("id", item.id)
                        put("playlist_id", item.playlistId)
                        put("track_id", item.trackId)
                        put("source", item.source)
                        put("title", item.title)
                        put("artists", item.artists)
                        put("duration_ms", item.durationMs)
                        put("sort_order", item.sortOrder)
                        put("created_at", item.createdAt?.toIso8601String())
                    }
                },
            )
        }

    companion object {
        private val _unknownCreator: JsonMap =
            linkedMapOf(
                "id" to "",
                "username" to "unknown",
                "display_name" to "未知用户",
                "avatar_url" to null,
                "default_avatar_key" to "blue-3",
            )

        fun fromJson(json: JsonMap): SharedMusicPlaylist =
            SharedMusicPlaylist(
                id = _stringFromJson(json, listOf("id")) ?: "",
                name = _stringFromJson(json, listOf("name")) ?: "",
                description = json["description"]?.toString() ?: "",
                itemCount = _intFromJson(json, listOf("item_count")) ?: 0,
                createdAt = _parseDateTime(json["created_at"]),
                creator = UserSummary.fromJson(_nullableMap(json["creator"]) ?: _unknownCreator),
                items = _listOfMaps(json["items"]).map { PersonalMusicPlaylistItem.fromJson(it) },
            )
    }
}

private fun _userSummaryToJson(user: UserSummary): JsonMap =
    buildMap {
        put("id", user.id)
        if (user.uid != null) put("uid", user.uid)
        put("username", user.username)
        put("display_name", user.displayName)
        put("avatar_url", user.avatarUrl)
        put("default_avatar_key", user.defaultAvatarKey)
        if (user.roomDisplayName != null) put("room_display_name", user.roomDisplayName)
        if (user.roomRole != null) put("room_role", user.roomRole)
        if (user.isSuperuser) put("is_superuser", true)
        if (user.isOnline != null) put("is_online", user.isOnline)
    }

class LastMessagePreview(
    val id: String,
    val senderDisplayName: String,
    val bodyPreview: String,
    val createdAt: DateTime,
    val type: String = "text",
) {
    companion object {
        fun fromJson(json: JsonMap): LastMessagePreview =
            LastMessagePreview(
                id = json["id"] as String,
                type = json["type"] as String? ?: "text",
                senderDisplayName = json["sender_display_name"] as String,
                bodyPreview = json["body_preview"] as String,
                createdAt = DateTime.parse(json["created_at"] as String),
            )
    }
}

class RoomCard(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val defaultAvatarKey: String,
    val memberCount: Int,
    val liveParticipantCount: Int,
    val liveAvatarPreview: List<UserSummary>,
    val lastMessage: LastMessagePreview?,
    val unreadCount: Int,
    val updatedAt: DateTime,
    val rid: String = "",
    val visibility: String = "private",
    val remarkName: String? = null,
    val description: String = "",
    val notificationPolicy: String = "all",
    val isPinned: Boolean = false,
    val onlineMemberCount: Int = 0,
    val hasUnreadCount: Boolean = false,
    val unreadMentionCount: Int = 0,
    val hasUnreadMentionCount: Boolean = false,
    val hasPendingJoinRequests: Boolean = false,
    val aiVoiceAnnouncementsEnabled: Boolean = false,
    /** Whether this server runs gang-bot, and whether this room has it on. */
    val gangBotAvailable: Boolean = false,
    val gangBotEnabled: Boolean = true,
) {
    val gangBotActive: Boolean get() = gangBotAvailable && gangBotEnabled

    val displayName: String
        get() {
            val remark = _nonEmptyString(remarkName) ?: return name
            return remark
        }

    /**
     * Returns a copy with selected fields overridden. Used when merging a
     * server-pushed public snapshot (room_updated) over an existing card: the
     * snapshot carries no per-user fields, so the caller re-supplies the local
     * [unreadCount] to keep it from being reset to 0.
     */
    fun copyWith(
        unreadCount: Int? = null,
        unreadMentionCount: Int? = null,
        isPinned: Boolean? = null,
        hasUnreadCount: Boolean? = null,
        hasUnreadMentionCount: Boolean? = null,
        hasPendingJoinRequests: Boolean? = null,
    ): RoomCard =
        RoomCard(
            id = id,
            name = name,
            rid = rid,
            visibility = visibility,
            remarkName = remarkName,
            description = description,
            notificationPolicy = notificationPolicy,
            isPinned = isPinned ?: this.isPinned,
            avatarUrl = avatarUrl,
            defaultAvatarKey = defaultAvatarKey,
            memberCount = memberCount,
            onlineMemberCount = onlineMemberCount,
            liveParticipantCount = liveParticipantCount,
            liveAvatarPreview = liveAvatarPreview,
            lastMessage = lastMessage,
            unreadCount = unreadCount ?: this.unreadCount,
            hasUnreadCount = hasUnreadCount ?: this.hasUnreadCount,
            unreadMentionCount = unreadMentionCount ?: this.unreadMentionCount,
            hasUnreadMentionCount = hasUnreadMentionCount ?: this.hasUnreadMentionCount,
            hasPendingJoinRequests = hasPendingJoinRequests ?: this.hasPendingJoinRequests,
            aiVoiceAnnouncementsEnabled = aiVoiceAnnouncementsEnabled,
            gangBotAvailable = gangBotAvailable,
            gangBotEnabled = gangBotEnabled,
            updatedAt = updatedAt,
        )

    companion object {
        fun fromJson(json: JsonMap): RoomCard {
            val hasUnreadCount = json.containsKey("unread_count")
            val hasUnreadMentionCount = json.containsKey("unread_mention_count")
            val pendingJoinRequestCount =
                _intFromJson(
                    json,
                    listOf(
                        "pending_join_request_count",
                        "pending_join_requests_count",
                    ),
                )
            val hasPendingJoinRequests =
                _boolFromJson(
                    json,
                    listOf(
                        "has_pending_join_requests",
                        "pending_join_requests",
                    ),
                ) ?: (pendingJoinRequestCount != null && pendingJoinRequestCount > 0)
            return RoomCard(
                id = json["id"] as String,
                name = json["name"] as String,
                rid = json["rid"] as String? ?: "",
                visibility = json["visibility"] as String? ?: "private",
                remarkName = _stringFromJson(json, listOf("remark_name", "room_remark")),
                description =
                    _stringFromJson(json, listOf("description", "intro", "bio")) ?: "",
                notificationPolicy =
                    _stringFromJson(
                        json,
                        listOf(
                            "notification_policy",
                            "notification_level",
                        ),
                    ) ?: "all",
                isPinned = _boolFromJson(json, listOf("is_pinned", "pinned")) ?: false,
                avatarUrl = json["avatar_url"] as String?,
                defaultAvatarKey = json["default_avatar_key"] as String? ?: "blue-3",
                memberCount = _requireInt(json["member_count"]),
                onlineMemberCount =
                    _intFromJson(json, listOf("online_member_count", "online_count")) ?: 0,
                liveParticipantCount = _castInt(json["live_participant_count"]) ?: 0,
                liveAvatarPreview =
                    _listOfMaps(json["live_avatar_preview"]).map { UserSummary.fromJson(it) },
                lastMessage =
                    if (_nullableMap(json["last_message"]) == null) {
                        null
                    } else {
                        LastMessagePreview.fromJson(_nullableMap(json["last_message"])!!)
                    },
                unreadCount = _castInt(json["unread_count"]) ?: 0,
                hasUnreadCount = hasUnreadCount,
                unreadMentionCount = _castInt(json["unread_mention_count"]) ?: 0,
                hasUnreadMentionCount = hasUnreadMentionCount,
                hasPendingJoinRequests = hasPendingJoinRequests,
                aiVoiceAnnouncementsEnabled =
                    _boolFromJson(
                        json,
                        listOf(
                            "ai_voice_announcements_enabled",
                            "ai_voice_announce_enabled",
                        ),
                    ) ?: false,
                gangBotAvailable = json["gang_bot_available"] as Boolean? ?: false,
                gangBotEnabled = json["gang_bot_enabled"] as Boolean? ?: true,
                updatedAt = DateTime.parse(json["updated_at"] as String),
            )
        }
    }
}

/**
 * A room as seen from search results — the viewer may or may not already be
 * a member. [joinState] is one of `none`, `pending`, `joined` and drives the
 * join button's affordance.
 */
class PublicRoom(
    val id: String,
    val rid: String,
    val name: String,
    val avatarUrl: String?,
    val defaultAvatarKey: String,
    val visibility: String,
    val joinPolicy: String,
    val memberCount: Int,
    val liveParticipantCount: Int,
    val joined: Boolean,
    val joinState: String,
    val avatarLabel: String? = null,
    val onlineMemberCount: Int = 0,
    val description: String = "",
    val createdBy: UserSummary? = null,
    val personalProfile: RoomPersonalProfile = RoomPersonalProfile(),
    val myMembership: RoomMembership? = null,
    val isDeleted: Boolean = false,
) {
    fun copyWith(
        joined: Boolean? = null,
        joinState: String? = null,
        memberCount: Int? = null,
        onlineMemberCount: Int? = null,
        liveParticipantCount: Int? = null,
        isDeleted: Boolean? = null,
    ): PublicRoom =
        PublicRoom(
            id = id,
            rid = rid,
            name = name,
            avatarLabel = avatarLabel,
            avatarUrl = avatarUrl,
            defaultAvatarKey = defaultAvatarKey,
            visibility = visibility,
            joinPolicy = joinPolicy,
            description = description,
            memberCount = memberCount ?: this.memberCount,
            onlineMemberCount = onlineMemberCount ?: this.onlineMemberCount,
            liveParticipantCount = liveParticipantCount ?: this.liveParticipantCount,
            joined = joined ?: this.joined,
            joinState = joinState ?: this.joinState,
            createdBy = createdBy,
            personalProfile = personalProfile,
            myMembership = myMembership,
            isDeleted = isDeleted ?: this.isDeleted,
        )

    companion object {
        fun fromJson(json: JsonMap): PublicRoom =
            PublicRoom(
                id = json["id"] as String,
                rid = json["rid"] as String? ?: "",
                name = json["name"] as String,
                avatarUrl = json["avatar_url"] as String?,
                defaultAvatarKey = json["default_avatar_key"] as String? ?: "blue-3",
                visibility = json["visibility"] as String? ?: "public",
                joinPolicy = json["join_policy"] as String? ?: "approval_required",
                description =
                    _stringFromJson(json, listOf("description", "intro", "bio")) ?: "",
                memberCount = _castInt(json["member_count"]) ?: 0,
                onlineMemberCount =
                    _intFromJson(json, listOf("online_member_count", "online_count")) ?: 0,
                liveParticipantCount = _castInt(json["live_participant_count"]) ?: 0,
                joined = json["joined"] as Boolean? ?: false,
                joinState = json["join_state"] as String? ?: "none",
                createdBy =
                    if (_nullableMap(json["created_by"]) == null) {
                        null
                    } else {
                        UserSummary.fromJson(_nullableMap(json["created_by"])!!)
                    },
                avatarLabel = _stringFromJson(json, listOf("avatar_label")),
                personalProfile =
                    RoomPersonalProfile.fromJson(
                        _nullableMap(json["personal_profile"])
                            ?: _nullableMap(json["my_room_profile"])
                            ?: _nullableMap(json["room_profile"]),
                    ),
                myMembership =
                    if (_nullableMap(json["my_membership"]) == null) {
                        null
                    } else {
                        RoomMembership.fromJson(_nullableMap(json["my_membership"])!!)
                    },
                isDeleted = json["is_deleted"] as Boolean? ?: false,
            )
    }
}

class SearchRoomContext(
    val id: String,
    val rid: String,
    val name: String,
    val avatarUrl: String?,
    val defaultAvatarKey: String,
    val remarkName: String? = null,
) {
    val displayName: String
        get() {
            val remark = _nonEmptyString(remarkName) ?: return name
            return remark
        }

    companion object {
        fun fromJson(json: JsonMap): SearchRoomContext =
            SearchRoomContext(
                id = json["id"] as String,
                rid = json["rid"] as String? ?: "",
                name = json["name"] as String,
                remarkName = _stringFromJson(json, listOf("remark_name", "room_remark")),
                avatarUrl = json["avatar_url"] as String?,
                defaultAvatarKey = json["default_avatar_key"] as String? ?: "blue-3",
            )
    }
}

class MessageSearchResult(val room: SearchRoomContext, val message: Message) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonMap): MessageSearchResult =
            MessageSearchResult(
                room = SearchRoomContext.fromJson(json["room"] as Map<String, Any?>),
                message = Message.fromJson(json["message"] as Map<String, Any?>),
            )
    }
}

class GlobalSearchCursors(
    val myRooms: String? = null,
    val publicRooms: String? = null,
    val userSettings: String? = null,
    val messages: String? = null,
    val files: String? = null,
) {
    companion object {
        fun fromJson(json: JsonMap?): GlobalSearchCursors {
            if (json == null) return GlobalSearchCursors()
            return GlobalSearchCursors(
                myRooms = json["my_rooms"] as String?,
                publicRooms = json["public_rooms"] as String?,
                userSettings = json["user_settings"] as String?,
                messages = json["messages"] as String?,
                files = json["files"] as String?,
            )
        }
    }
}

class GlobalSearchCounts(
    val myRooms: Int? = null,
    val publicRooms: Int? = null,
    val userSettings: Int? = null,
    val messages: Int? = null,
    val files: Int? = null,
) {
    companion object {
        fun fromJson(json: JsonMap?): GlobalSearchCounts {
            if (json == null) return GlobalSearchCounts()
            return GlobalSearchCounts(
                myRooms = _nullableInt(json["my_rooms"]),
                publicRooms = _nullableInt(json["public_rooms"]),
                userSettings = _nullableInt(json["user_settings"]),
                messages = _nullableInt(json["messages"]),
                files = _nullableInt(json["files"]),
            )
        }
    }
}

class GlobalSearchResults(
    val myRooms: List<RoomCard>,
    val publicRooms: List<PublicRoom>,
    val userSettings: List<UserSummary> = emptyList(),
    val messages: List<MessageSearchResult>,
    val files: List<MessageSearchResult>,
    val nextCursors: GlobalSearchCursors = GlobalSearchCursors(),
    val totalCounts: GlobalSearchCounts = GlobalSearchCounts(),
) {
    companion object {
        fun fromJson(json: JsonMap): GlobalSearchResults =
            GlobalSearchResults(
                myRooms = _listOfMaps(json["my_rooms"]).map { RoomCard.fromJson(it) },
                publicRooms = _listOfMaps(json["public_rooms"]).map { PublicRoom.fromJson(it) },
                userSettings = _listOfMaps(json["user_settings"]).map { UserSummary.fromJson(it) },
                messages = _listOfMaps(json["messages"]).map { MessageSearchResult.fromJson(it) },
                files = _listOfMaps(json["files"]).map { MessageSearchResult.fromJson(it) },
                nextCursors = GlobalSearchCursors.fromJson(_nullableMap(json["next_cursors"])),
                totalCounts = GlobalSearchCounts.fromJson(_nullableMap(json["total_counts"])),
            )
    }
}

/**
 * Result of POST /rooms/:id/join. Either we joined and got the full room
 * detail back, or the room needs approval and we got a pending request.
 */
class JoinRoomResult(val room: RoomDetail? = null, val pending: Boolean = false) {
    val joined: Boolean get() = room != null
}

class RoomMembership(
    val joinedAt: DateTime,
    val role: String,
    val aiVoiceAnnouncementsEnabled: Boolean = false,
) {
    companion object {
        fun fromJson(json: JsonMap): RoomMembership =
            RoomMembership(
                joinedAt = DateTime.parse(json["joined_at"] as String),
                role = json["role"] as String? ?: "member",
                aiVoiceAnnouncementsEnabled =
                    _boolFromJson(json, listOf("ai_voice_announcements_enabled")) ?: false,
            )
    }
}

class RoomPersonalProfile(val displayName: String? = null) {
    val isEmpty: Boolean get() = _nonEmptyString(displayName) == null

    companion object {
        fun fromJson(json: JsonMap?): RoomPersonalProfile {
            if (json == null) return RoomPersonalProfile()
            return RoomPersonalProfile(
                displayName =
                    _stringFromJson(
                        json,
                        listOf(
                            "display_name",
                            "room_display_name",
                            "room_username",
                            "room_nickname",
                        ),
                    ),
            )
        }
    }
}

/**
 * A pending request to join an approval-required room, as seen by an admin
 * reviewing the room's join queue.
 */
class JoinRequest(
    val id: String,
    val status: String,
    val user: UserSummary,
    val createdAt: DateTime,
    val reason: String = "",
    val source: String = "public_search",
    val inviters: List<UserSummary> = emptyList(),
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonMap): JoinRequest {
            val inviters = _listOfMaps(json["inviters"]).map { UserSummary.fromJson(it) }
            return JoinRequest(
                id = json["id"] as String,
                status = json["status"] as String? ?: "pending",
                reason = _stringFromJson(json, listOf("reason", "description")) ?: "",
                source =
                    _stringFromJson(json, listOf("source", "request_source"))
                        ?: (if (inviters.isEmpty()) "public_search" else "invitation"),
                inviters = inviters,
                user = UserSummary.fromJson(json["user"] as Map<String, Any?>),
                createdAt = DateTime.parse(json["created_at"] as String),
            )
        }
    }
}

class RoomMemberPage(val members: List<RoomMember>, val nextCursor: String?) {
    companion object {
        fun fromJson(json: JsonMap): RoomMemberPage =
            RoomMemberPage(
                members = _listOfMaps(json["members"]).map { RoomMember.fromJson(it) },
                nextCursor = _stringFromJson(json, listOf("next_cursor")),
            )
    }
}

class RoomMember(
    val user: UserSummary,
    val role: String,
    val joinedAt: DateTime,
    val roomDisplayName: String? = null,
    val remarkName: String? = null,
    val textMutedUntil: String? = null,
    val isOnline: Boolean? = null,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonMap): RoomMember {
            val userJson = json["user"] as Map<String, Any?>
            val baseUser = UserSummary.fromJson(userJson)
            val membership = _nullableMap(json["membership"])
            val role =
                _stringFromJson(json, listOf("role", "room_role", "membership_role"))
                    ?: _stringFromJson(membership, listOf("role"))
                    ?: baseUser.roomRole
                    ?: "member"
            val roomDisplayName =
                _stringFromJson(
                    json,
                    listOf(
                        "room_display_name",
                        "room_username",
                        "room_nickname",
                        "member_display_name",
                    ),
                ) ?: _stringFromJson(
                    membership,
                    listOf(
                        "room_display_name",
                        "room_username",
                        "room_nickname",
                        "member_display_name",
                    ),
                ) ?: baseUser.roomDisplayName
            val remarkName =
                _stringFromJson(json, listOf("remark_name", "remark"))
                    ?: _stringFromJson(membership, listOf("remark_name", "remark"))
            val textMutedUntil =
                _stringFromJson(json, listOf("text_muted_until"))
                    ?: _stringFromJson(membership, listOf("text_muted_until"))
            val isOnline =
                _boolFromJson(json, listOf("is_online", "online"))
                    ?: _boolFromJson(
                        _nullableMap(json["presence"]),
                        listOf(
                            "is_online",
                            "online",
                        ),
                    )
                    ?: _onlineFromStatus(
                        _stringFromJson(
                            json,
                            listOf(
                                "presence",
                                "presence_status",
                                "status",
                                "connection_state",
                            ),
                        ) ?: _stringFromJson(
                            _nullableMap(json["presence"]),
                            listOf(
                                "status",
                                "connection_state",
                            ),
                        ),
                    )
                    ?: baseUser.isOnline
            val user =
                baseUser.copyWith(
                    roomDisplayName = roomDisplayName,
                    roomRole = role,
                    isOnline = isOnline,
                )
            return RoomMember(
                user = user,
                role = role,
                joinedAt =
                    _parseDateTime(json["joined_at"])
                        ?: _parseDateTime(membership?.get("joined_at"))
                        ?: DateTime.fromMillisecondsSinceEpoch(0, isUtc = true),
                roomDisplayName = roomDisplayName,
                remarkName = remarkName,
                textMutedUntil = textMutedUntil,
                isOnline = isOnline,
            )
        }
    }
}

class RoomMemberProfile(
    val user: UserSummary,
    val role: String,
    val joinedAt: DateTime,
    val roomDisplayName: String? = null,
    val textMutedUntil: String? = null,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonMap): RoomMemberProfile {
            val baseUser = UserSummary.fromJson(json["user"] as Map<String, Any?>)
            val role =
                _stringFromJson(json, listOf("role", "room_role", "membership_role"))
                    ?: baseUser.roomRole
                    ?: "member"
            val roomDisplayName =
                _stringFromJson(
                    json,
                    listOf(
                        "room_display_name",
                        "room_username",
                        "room_nickname",
                        "member_display_name",
                    ),
                )
            val isOnline =
                baseUser.isOnline
                    ?: _boolFromJson(json, listOf("is_online", "online"))
                    ?: _onlineFromStatus(
                        _stringFromJson(
                            json,
                            listOf(
                                "presence",
                                "presence_status",
                                "status",
                                "connection_state",
                            ),
                        ),
                    )
            val user =
                baseUser.copyWith(
                    roomDisplayName = roomDisplayName,
                    roomRole = role,
                    isOnline = isOnline,
                )
            return RoomMemberProfile(
                user = user,
                role = role,
                joinedAt =
                    _parseDateTime(json["joined_at"])
                        ?: DateTime.fromMillisecondsSinceEpoch(0, isUtc = true),
                roomDisplayName = roomDisplayName,
                textMutedUntil = _stringFromJson(json, listOf("text_muted_until")),
            )
        }
    }
}

class RoomInvite(
    val id: String,
    val status: String,
    val room: PublicRoom,
    val inviter: UserSummary,
    val createdAt: DateTime,
    val roomExists: Boolean = true,
    val inviterExists: Boolean = true,
    val invalidReason: String? = null,
    val updatedAt: DateTime? = null,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonMap): RoomInvite {
            val roomExists = json["room_exists"] as Boolean? ?: true
            val inviterExists = json["inviter_exists"] as Boolean? ?: true
            return RoomInvite(
                id = json["id"] as String,
                status = json["status"] as String? ?: "pending",
                room = PublicRoom.fromJson(json["room"] as Map<String, Any?>).copyWith(isDeleted = !roomExists),
                inviter =
                    UserSummary.fromJson(json["inviter"] as Map<String, Any?>)
                        .copyWith(isDeleted = !inviterExists),
                createdAt =
                    _parseDateTime(json["created_at"])
                        ?: DateTime.fromMillisecondsSinceEpoch(0, isUtc = true),
                roomExists = roomExists,
                inviterExists = inviterExists,
                invalidReason = _stringFromJson(json, listOf("invalid_reason")),
                updatedAt = _parseDateTime(json["updated_at"]),
            )
        }
    }
}

class RoomBlacklistEntry(
    val user: UserSummary,
    val createdAt: DateTime,
    val blockedBy: UserSummary? = null,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonMap): RoomBlacklistEntry =
            RoomBlacklistEntry(
                user = UserSummary.fromJson(json["user"] as Map<String, Any?>),
                createdAt =
                    _parseDateTime(json["created_at"])
                        ?: DateTime.fromMillisecondsSinceEpoch(0, isUtc = true),
                blockedBy =
                    if (_nullableMap(json["blocked_by"]) == null) {
                        null
                    } else {
                        UserSummary.fromJson(_nullableMap(json["blocked_by"])!!)
                    },
            )
    }
}

class RoomApplication(
    val id: String,
    val status: String,
    val room: PublicRoom,
    val createdAt: DateTime,
    val updatedAt: DateTime,
    val reason: String = "",
    val reviewedAt: DateTime? = null,
    val reviewer: UserSummary? = null,
    val reviewerExists: Boolean = true,
    val requestNotificationDeleted: Boolean = false,
    val reviewNotificationDeleted: Boolean = false,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonMap): RoomApplication {
            val reviewerExists = json["reviewer_exists"] as Boolean? ?: true
            val reviewerJson = _nullableMap(json["reviewer"])
            return RoomApplication(
                id = json["id"] as String,
                status = json["status"] as String? ?: "pending",
                reason = _stringFromJson(json, listOf("reason", "description")) ?: "",
                room = PublicRoom.fromJson(json["room"] as Map<String, Any?>),
                createdAt =
                    _parseDateTime(json["created_at"])
                        ?: DateTime.fromMillisecondsSinceEpoch(0, isUtc = true),
                updatedAt =
                    _parseDateTime(json["updated_at"])
                        ?: DateTime.fromMillisecondsSinceEpoch(0, isUtc = true),
                reviewedAt = _parseDateTime(json["reviewed_at"]),
                reviewer =
                    if (reviewerJson == null) {
                        null
                    } else {
                        UserSummary.fromJson(reviewerJson).copyWith(isDeleted = !reviewerExists)
                    },
                reviewerExists = reviewerExists,
                requestNotificationDeleted = json["request_notification_deleted"] as Boolean? ?: false,
                reviewNotificationDeleted = json["review_notification_deleted"] as Boolean? ?: false,
            )
        }
    }
}

class RoomEventNotification(
    val id: String,
    val type: String,
    val room: PublicRoom,
    val createdAt: DateTime,
    val roomExists: Boolean = true,
    val actor: UserSummary? = null,
    val actorExists: Boolean = true,
    val fromRole: String? = null,
    val toRole: String? = null,
    val messageId: String? = null,
    val messagePreview: String? = null,
    val readAt: DateTime? = null,
) {
    val isUnread: Boolean get() = readAt == null

    fun markRead(readAt: DateTime): RoomEventNotification =
        RoomEventNotification(
            id = id,
            type = type,
            room = room,
            createdAt = createdAt,
            roomExists = roomExists,
            actor = actor,
            actorExists = actorExists,
            fromRole = fromRole,
            toRole = toRole,
            messageId = messageId,
            messagePreview = messagePreview,
            readAt = readAt,
        )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonMap): RoomEventNotification {
            val actorJson = _nullableMap(json["actor"])
            val roomExists = json["room_exists"] as Boolean? ?: true
            val actorExists = json["actor_exists"] as Boolean? ?: (actorJson != null)
            val actor =
                if (actorJson == null) {
                    null
                } else {
                    UserSummary.fromJson(actorJson).copyWith(isDeleted = !actorExists)
                }
            return RoomEventNotification(
                id = json["id"] as String,
                type = json["type"] as String? ?: "",
                room = PublicRoom.fromJson(json["room"] as Map<String, Any?>).copyWith(isDeleted = !roomExists),
                createdAt =
                    _parseDateTime(json["created_at"])
                        ?: DateTime.fromMillisecondsSinceEpoch(0, isUtc = true),
                roomExists = roomExists,
                actor = actor,
                actorExists = actorExists,
                fromRole = _stringFromJson(json, listOf("from_role")),
                toRole = _stringFromJson(json, listOf("to_role")),
                messageId = _stringFromJson(json, listOf("message_id")),
                messagePreview = _stringFromJson(json, listOf("message_preview")),
                readAt = _parseDateTime(json["read_at"]),
            )
        }
    }
}

class RoomDetail(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val defaultAvatarKey: String,
    val memberCount: Int,
    val myMembership: RoomMembership,
    val live: LiveState,
    val createdAt: DateTime,
    val updatedAt: DateTime,
    val createdBy: UserSummary? = null,
    val rid: String = "",
    val visibility: String = "private",
    val description: String = "",
    val joinPolicy: String = "approval_required",
    val remarkName: String? = null,
    val notificationPolicy: String = "all",
    val isPinned: Boolean = false,
    val personalProfile: RoomPersonalProfile = RoomPersonalProfile(),
    val aiVoiceAnnouncementsEnabled: Boolean = false,
    /**
     * Who may recall messages and within what window. Mirrors the server's
     * `message_recall_policy` (e.g. `sender_only`, `admins`, `disabled`) and
     * `message_recall_window_seconds` (null = unlimited). Drives whether the
     * recall action is offered and for how long after sending.
     */
    val messageRecallPolicy: String = "sender_only",
    val messageRecallWindowSeconds: Int? = null,
    val canDeleteRoom: Boolean? = null,
    val onlineMemberCount: Int = 0,
    /** Whether this server runs gang-bot, and whether this room has it on. */
    val gangBotAvailable: Boolean = false,
    val gangBotEnabled: Boolean = true,
) {
    val gangBotActive: Boolean get() = gangBotAvailable && gangBotEnabled

    /**
     * Whether the current user can administer this room (review join requests,
     * etc.). Mirrors the server's admin check: owner, admin, or superuser.
     */
    val isAdmin: Boolean
        get() {
            val role = myMembership.role.lowercase()
            return role == "owner" ||
                role == "creator" ||
                role == "admin" ||
                role == "administrator" ||
                role == "superuser"
        }

    val isCreator: Boolean
        get() {
            val role = myMembership.role.lowercase()
            return role == "owner" || role == "creator"
        }

    val isSuperuser: Boolean get() = myMembership.role.lowercase() == "superuser"

    val canDelete: Boolean get() = canDeleteRoom ?: (isCreator || isSuperuser)

    /**
     * Returns a copy with the current user's membership role replaced. Used to
     * apply a `room_role_changed` SSE event (promote/demote) without re-fetching
     * the whole room detail.
     */
    fun copyWithRole(role: String): RoomDetail =
        RoomDetail(
            id = id,
            name = name,
            rid = rid,
            visibility = visibility,
            description = description,
            joinPolicy = joinPolicy,
            remarkName = remarkName,
            notificationPolicy = notificationPolicy,
            isPinned = isPinned,
            personalProfile = personalProfile,
            aiVoiceAnnouncementsEnabled = aiVoiceAnnouncementsEnabled,
            messageRecallPolicy = messageRecallPolicy,
            messageRecallWindowSeconds = messageRecallWindowSeconds,
            canDeleteRoom = canDeleteRoom,
            gangBotAvailable = gangBotAvailable,
            gangBotEnabled = gangBotEnabled,
            avatarUrl = avatarUrl,
            defaultAvatarKey = defaultAvatarKey,
            memberCount = memberCount,
            onlineMemberCount = onlineMemberCount,
            createdBy = createdBy,
            myMembership =
                RoomMembership(
                    joinedAt = myMembership.joinedAt,
                    role = role,
                    aiVoiceAnnouncementsEnabled = myMembership.aiVoiceAnnouncementsEnabled,
                ),
            live = live,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    fun toCard(): RoomCard =
        RoomCard(
            id = id,
            name = name,
            rid = rid,
            visibility = visibility,
            remarkName = remarkName,
            description = description,
            notificationPolicy = notificationPolicy,
            isPinned = isPinned,
            avatarUrl = avatarUrl,
            defaultAvatarKey = defaultAvatarKey,
            memberCount = memberCount,
            onlineMemberCount = onlineMemberCount,
            liveParticipantCount = live.participantCount,
            liveAvatarPreview = live.participants.map { p -> p.user }.take(5),
            aiVoiceAnnouncementsEnabled = aiVoiceAnnouncementsEnabled,
            gangBotAvailable = gangBotAvailable,
            gangBotEnabled = gangBotEnabled,
            lastMessage = null,
            unreadCount = 0,
            updatedAt = updatedAt,
        )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonMap): RoomDetail =
            RoomDetail(
                id = json["id"] as String,
                name = json["name"] as String,
                rid = json["rid"] as String? ?: "",
                visibility = json["visibility"] as String? ?: "private",
                description =
                    _stringFromJson(json, listOf("description", "intro", "bio")) ?: "",
                joinPolicy = json["join_policy"] as String? ?: "approval_required",
                remarkName = _stringFromJson(json, listOf("remark_name", "room_remark")),
                notificationPolicy =
                    _stringFromJson(
                        json,
                        listOf(
                            "notification_policy",
                            "notification_level",
                        ),
                    ) ?: "all",
                isPinned =
                    _boolFromJson(json, listOf("is_pinned", "pinned"))
                        ?: _boolFromJson(
                            _nullableMap(json["my_membership"]),
                            listOf(
                                "is_pinned",
                                "pinned",
                            ),
                        )
                        ?: false,
                personalProfile =
                    RoomPersonalProfile.fromJson(
                        _nullableMap(json["personal_profile"])
                            ?: _nullableMap(json["my_room_profile"])
                            ?: _nullableMap(json["room_profile"]),
                    ),
                aiVoiceAnnouncementsEnabled =
                    _boolFromJson(
                        json,
                        listOf(
                            "ai_voice_announcements_enabled",
                            "ai_voice_auto_broadcast",
                        ),
                    ) ?: _boolFromJson(
                        _nullableMap(json["my_membership"]),
                        listOf(
                            "ai_voice_announcements_enabled",
                        ),
                    ) ?: false,
                messageRecallPolicy =
                    _stringFromJson(json, listOf("message_recall_policy")) ?: "sender_only",
                messageRecallWindowSeconds =
                    _intFromJson(
                        json,
                        listOf(
                            "message_recall_window_seconds",
                        ),
                    ),
                canDeleteRoom =
                    _boolFromJson(
                        json,
                        listOf(
                            "can_delete_room",
                            "can_delete",
                        ),
                    ),
                gangBotAvailable = json["gang_bot_available"] as Boolean? ?: false,
                gangBotEnabled = json["gang_bot_enabled"] as Boolean? ?: true,
                avatarUrl = json["avatar_url"] as String?,
                defaultAvatarKey = json["default_avatar_key"] as String? ?: "blue-3",
                memberCount = _requireInt(json["member_count"]),
                onlineMemberCount =
                    _intFromJson(json, listOf("online_member_count", "online_count")) ?: 0,
                createdBy =
                    if (_nullableMap(json["created_by"]) == null) {
                        null
                    } else {
                        UserSummary.fromJson(_nullableMap(json["created_by"])!!)
                    },
                myMembership = RoomMembership.fromJson(json["my_membership"] as Map<String, Any?>),
                live = LiveState.fromJson(json["live"] as Map<String, Any?>),
                createdAt = DateTime.parse(json["created_at"] as String),
                updatedAt = DateTime.parse(json["updated_at"] as String),
            )
    }
}

class MessageQuote(
    val messageId: String,
    val senderDisplayName: String,
    val body: String,
    val createdAt: DateTime,
    val previewAttachment: MessageAttachment? = null,
) {
    companion object {
        fun fromJson(json: JsonMap): MessageQuote {
            val previewAttachmentJson = _nullableMap(json["preview_attachment"])
            return MessageQuote(
                messageId = json["message_id"] as String,
                senderDisplayName = json["sender_display_name"] as String? ?: "用户",
                body = json["body"] as String? ?: "",
                createdAt = DateTime.parse(json["created_at"] as String),
                previewAttachment =
                    if (previewAttachmentJson == null) {
                        null
                    } else {
                        MessageAttachment.fromJson(previewAttachmentJson)
                    },
            )
        }
    }
}

class Message(
    val id: String,
    val roomId: String,
    val sender: UserSummary,
    val clientMessageId: String,
    val type: String = "text",
    val body: String,
    val createdAt: DateTime,
    val attachments: List<MessageAttachment> = emptyList(),
    /**
     * Raw mention descriptors as sent by the server (opaque maps; shape is
     * `{user_id, ...}`). Kept as-is so the renderer can highlight @mentions.
     */
    val mentions: List<JsonMap> = emptyList(),
    val quote: MessageQuote? = null,
    val quotes: List<MessageQuote> = emptyList(),
    /**
     * Set when the sender (or an admin within policy) recalled the message.
     * The body/attachments are tombstoned server-side; the client shows a
     * "message recalled" placeholder.
     */
    val isRecalled: Boolean = false,
    val recalledAt: DateTime? = null,
    val recalledBy: UserSummary? = null,
    /**
     * Set when an admin force-deleted the message. Distinct from a recall:
     * it's a moderation action, not the author retracting their own message.
     */
    val isForceDeleted: Boolean = false,
    val forceDeletedAt: DateTime? = null,
    val forceDeletedBy: UserSummary? = null,
    val pending: Boolean = false,
    val failed: Boolean = false,
) {
    val effectiveQuotes: List<MessageQuote>
        get() {
            if (quotes.isNotEmpty()) return quotes
            val legacy = quote
            return if (legacy == null) emptyList() else listOf(legacy)
        }

    /**
     * Whether the message content has been removed (recalled or force-deleted)
     * and should render as a placeholder rather than its original body.
     */
    val isRemoved: Boolean get() = isRecalled || isForceDeleted

    val stickerAttachment: MessageAttachment?
        get() {
            if (type != "sticker") return null
            for (attachment in attachments) {
                if (attachment.type == "sticker" && attachment.asset != null) {
                    return attachment
                }
            }
            return null
        }

    val fileAttachments: List<MessageAttachment>
        get() = attachments.filter { attachment -> attachment.type == "file" }

    val playlistAttachment: MessageAttachment?
        get() {
            if (type != "playlist") return null
            for (attachment in attachments) {
                if (attachment.type == "playlist" && attachment.playlist != null) {
                    return attachment
                }
            }
            return null
        }

    val musicTrackAttachment: MessageAttachment?
        get() {
            if (type != "music_track") return null
            for (attachment in attachments) {
                if (attachment.type == "music_track" && attachment.track != null) {
                    return attachment
                }
            }
            return null
        }

    fun markFailed(): Message =
        Message(
            id = id,
            roomId = roomId,
            sender = sender,
            clientMessageId = clientMessageId,
            type = type,
            body = body,
            createdAt = createdAt,
            attachments = attachments,
            mentions = mentions,
            quote = quote,
            quotes = quotes,
            isRecalled = isRecalled,
            recalledAt = recalledAt,
            recalledBy = recalledBy,
            isForceDeleted = isForceDeleted,
            forceDeletedAt = forceDeletedAt,
            forceDeletedBy = forceDeletedBy,
            failed = true,
        )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonMap): Message {
            val senderCommonRooms =
                _listOfMaps(json["sender_common_rooms"])
                    .map { UserCommonRoom.fromJson(it) }
                    .filter { room -> room.isUsable }
            val sender =
                UserSummary.fromJson(json["sender"] as Map<String, Any?>)
                    .copyWith(
                        roomDisplayName =
                            _stringFromJson(
                                json,
                                listOf(
                                    "sender_room_display_name",
                                    "sender_room_username",
                                    "sender_room_nickname",
                                    "sender_room_remark",
                                ),
                            ),
                        roomRole =
                            _stringFromJson(
                                json,
                                listOf(
                                    "sender_room_role",
                                    "sender_membership_role",
                                ),
                            ),
                        commonRooms = if (senderCommonRooms.isEmpty()) null else senderCommonRooms,
                    )
            val recalledByJson = _nullableMap(json["recalled_by"])
            val forceDeletedByJson = _nullableMap(json["force_deleted_by"])
            val quoteJson = _nullableMap(json["quote"])
            val parsedQuotes = _listOfMaps(json["quotes"]).map { MessageQuote.fromJson(it) }
            val legacyQuote = if (quoteJson == null) null else MessageQuote.fromJson(quoteJson)
            return Message(
                id = json["id"] as String,
                roomId = json["room_id"] as String,
                sender = sender,
                clientMessageId = json["client_message_id"] as String,
                type = json["type"] as String? ?: "text",
                body = json["body"] as String? ?: "",
                attachments = _listOfMaps(json["attachments"]).map { MessageAttachment.fromJson(it) },
                mentions = _listOfMaps(json["mentions"]),
                quote = legacyQuote ?: (if (parsedQuotes.isEmpty()) null else parsedQuotes.first()),
                quotes =
                    if (parsedQuotes.isNotEmpty()) {
                        parsedQuotes
                    } else if (legacyQuote == null) {
                        emptyList()
                    } else {
                        listOf(legacyQuote)
                    },
                isRecalled = _boolFromJson(json, listOf("is_recalled")) ?: false,
                recalledAt = _parseDateTime(json["recalled_at"]),
                recalledBy = if (recalledByJson == null) null else UserSummary.fromJson(recalledByJson),
                isForceDeleted = _boolFromJson(json, listOf("is_force_deleted")) ?: false,
                forceDeletedAt = _parseDateTime(json["force_deleted_at"]),
                forceDeletedBy = if (forceDeletedByJson == null) null else UserSummary.fromJson(forceDeletedByJson),
                createdAt = DateTime.parse(json["created_at"] as String),
            )
        }

        /** Dart's `Message.local(...)`. */
        fun local(
            roomId: String,
            sender: UserSummary,
            clientMessageId: String,
            body: String,
            type: String = "text",
            attachments: List<MessageAttachment> = emptyList(),
            mentions: List<JsonMap> = emptyList(),
            quote: MessageQuote? = null,
            quotes: List<MessageQuote> = emptyList(),
        ): Message =
            Message(
                id = clientMessageId,
                roomId = roomId,
                sender = sender,
                clientMessageId = clientMessageId,
                type = type,
                body = body,
                createdAt = DateTime.now().toUtc(),
                attachments = attachments,
                mentions = mentions,
                quote = quote,
                quotes = quotes,
                pending = true,
            )
    }
}

class LiveParticipant(
    val liveSessionId: String,
    val user: UserSummary,
    val joinedAt: DateTime,
    val micMuted: Boolean,
    val micBlocked: Boolean = false,
    val headphonesMuted: Boolean,
    val headphonesBlocked: Boolean = false,
    /**
     * Whether the participant is actively listening (headphones engaged).
     * Inverse-ish of [headphonesMuted] but sent explicitly by the server;
     * defaults to true for servers that omit it.
     */
    val headphonesListening: Boolean = true,
    /**
     * A persistent room-level microphone mute set by an admin (`mute_mic`). While
     * true the participant's LiveKit publish permission is revoked: the mic is
     * force-muted and self-unmute requests are rejected by the server. Survives
     * reconnects until an admin runs `restore_voice`. Defaults to false for
     * servers that don't send the field.
     */
    val voiceBlocked: Boolean,
    val cameraOn: Boolean,
    val cameraMirrored: Boolean = false,
    val screenSharing: Boolean,
    val screenViewers: List<UserSummary> = emptyList(),
    val connectionState: String,
) {
    fun copyWith(
        liveSessionId: String? = null,
        user: UserSummary? = null,
        joinedAt: DateTime? = null,
        micMuted: Boolean? = null,
        micBlocked: Boolean? = null,
        headphonesMuted: Boolean? = null,
        headphonesBlocked: Boolean? = null,
        headphonesListening: Boolean? = null,
        voiceBlocked: Boolean? = null,
        cameraOn: Boolean? = null,
        cameraMirrored: Boolean? = null,
        screenSharing: Boolean? = null,
        screenViewers: List<UserSummary>? = null,
        connectionState: String? = null,
    ): LiveParticipant =
        LiveParticipant(
            liveSessionId = liveSessionId ?: this.liveSessionId,
            user = user ?: this.user,
            joinedAt = joinedAt ?: this.joinedAt,
            micMuted = micMuted ?: this.micMuted,
            micBlocked = micBlocked ?: this.micBlocked,
            headphonesMuted = headphonesMuted ?: this.headphonesMuted,
            headphonesBlocked = headphonesBlocked ?: this.headphonesBlocked,
            headphonesListening = headphonesListening ?: this.headphonesListening,
            voiceBlocked = voiceBlocked ?: this.voiceBlocked,
            cameraOn = cameraOn ?: this.cameraOn,
            cameraMirrored = cameraMirrored ?: this.cameraMirrored,
            screenSharing = screenSharing ?: this.screenSharing,
            screenViewers = screenViewers ?: this.screenViewers,
            connectionState = connectionState ?: this.connectionState,
        )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonMap): LiveParticipant {
            val commonRooms =
                _listOfMaps(json["common_rooms"])
                    .map { UserCommonRoom.fromJson(it) }
                    .filter { room -> room.isUsable }
            val user =
                UserSummary.fromJson(json["user"] as Map<String, Any?>)
                    .copyWith(
                        roomDisplayName =
                            _stringFromJson(
                                json,
                                listOf(
                                    "room_display_name",
                                    "room_username",
                                    "room_nickname",
                                    "room_remark",
                                ),
                            ),
                        roomRole =
                            _stringFromJson(
                                json,
                                listOf(
                                    "room_role",
                                    "membership_role",
                                ),
                            ),
                        commonRooms = if (commonRooms.isEmpty()) null else commonRooms,
                    )
            return LiveParticipant(
                liveSessionId = json["live_session_id"] as String,
                user = user,
                joinedAt = DateTime.parse(json["joined_at"] as String),
                micMuted = json["mic_muted"] as Boolean,
                micBlocked = json["mic_blocked"] as Boolean? ?: false,
                headphonesMuted = json["headphones_muted"] as Boolean? ?: false,
                headphonesBlocked = json["headphones_blocked"] as Boolean? ?: false,
                headphonesListening = json["headphones_listening"] as Boolean? ?: true,
                voiceBlocked = json["voice_blocked"] as Boolean? ?: false,
                cameraOn = json["camera_on"] as Boolean,
                cameraMirrored = json["camera_mirrored"] as Boolean? ?: false,
                screenSharing = json["screen_sharing"] as Boolean,
                screenViewers = _listOfMaps(json["screen_viewers"]).map { UserSummary.fromJson(it) },
                connectionState = json["connection_state"] as String,
            )
        }
    }
}

/**
 * A per-listener volume override for one target speaker in a live room. Each
 * listener tunes how loudly they hear each other participant; the server keys
 * these by (room, listener, target) and only ever returns the caller's own
 * overrides.
 */
class LiveMemberVolume(
    val roomId: String,
    val targetUser: UserSummary,
    /** 0–100, where 100 is the unattenuated default. */
    val volume: Int,
    val updatedAt: DateTime,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonMap): LiveMemberVolume =
            LiveMemberVolume(
                roomId = json["room_id"] as String,
                targetUser = UserSummary.fromJson(json["target_user"] as Map<String, Any?>),
                volume = _requireInt(json["volume"]),
                updatedAt = DateTime.parse(json["updated_at"] as String),
            )
    }
}

/**
 * A pending request to recall a message, created when a room's recall policy
 * is `admin_approval` and a non-admin asks to recall their own message. Admins
 * list and approve/reject these.
 */
class MessageRecallRequest(
    val id: String,
    val roomId: String,
    val messageId: String,
    val status: String,
    val createdAt: DateTime,
    val requestedByUserId: String? = null,
) {
    companion object {
        fun fromJson(json: JsonMap): MessageRecallRequest =
            MessageRecallRequest(
                id = json["id"] as String,
                roomId = json["room_id"] as String,
                messageId = json["message_id"] as String,
                status = json["status"] as String? ?: "pending",
                requestedByUserId = json["requested_by_user_id"] as String?,
                createdAt = DateTime.parse(json["created_at"] as String),
            )
    }
}

/**
 * The outcome of a recall request. The server either recalls the message
 * immediately (sender within policy/window, or an admin) returning the updated
 * [message], or — under `admin_approval` for a non-admin — files a pending
 * [recallRequest] for an admin to review.
 */
class MessageRecallResult(
    /** Set when the recall took effect immediately. */
    val message: Message? = null,
    /** Set when the recall was queued for admin approval instead. */
    val recallRequest: MessageRecallRequest? = null,
) {
    val isPending: Boolean get() = recallRequest != null && message == null

    companion object {
        fun fromJson(json: JsonMap): MessageRecallResult {
            val messageJson = _nullableMap(json["message"])
            val requestJson = _nullableMap(json["recall_request"])
            return MessageRecallResult(
                message = if (messageJson == null) null else Message.fromJson(messageJson),
                recallRequest = if (requestJson == null) null else MessageRecallRequest.fromJson(requestJson),
            )
        }
    }
}

class LiveState(
    val roomId: String,
    val participantCount: Int,
    val participants: List<LiveParticipant>,
    val updatedAt: DateTime,
) {
    companion object {
        fun fromJson(json: JsonMap): LiveState =
            LiveState(
                roomId = json["room_id"] as String,
                participantCount = _requireInt(json["participant_count"]),
                participants = _listOfMaps(json["participants"]).map { LiveParticipant.fromJson(it) },
                updatedAt = DateTime.parse(json["updated_at"] as String),
            )
    }
}

class LiveKitConnectionInfo(
    val serverUrl: String,
    val token: String,
    val tokenExpiresAt: DateTime,
    val roomName: String,
) {
    companion object {
        fun fromJson(json: JsonMap): LiveKitConnectionInfo =
            LiveKitConnectionInfo(
                serverUrl = json["server_url"] as String,
                token = json["token"] as String,
                tokenExpiresAt = DateTime.parse(json["token_expires_at"] as String),
                roomName = json["room_name"] as String,
            )
    }
}

/**
 * A publish-only LiveKit token for the hidden screen-audio aux participant
 * (`<ownerId>--screen-audio`). The aux participant publishes the screen-share
 * audio track through an isolated WebRTC factory; it is hidden from the
 * receiver UI and never appears in the roster.
 */
class ScreenAudioToken(
    val serverUrl: String,
    val token: String,
    val tokenExpiresAt: DateTime,
    val roomName: String,
    val identity: String,
) {
    companion object {
        fun fromJson(json: JsonMap): ScreenAudioToken =
            ScreenAudioToken(
                serverUrl = json["server_url"] as String,
                token = json["token"] as String,
                tokenExpiresAt = DateTime.parse(json["token_expires_at"] as String),
                roomName = json["room_name"] as String,
                identity = json["identity"] as String,
            )
    }
}

class LiveJoinResult(
    val liveKit: LiveKitConnectionInfo,
    val participant: LiveParticipant,
    val live: LiveState,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonMap): LiveJoinResult =
            LiveJoinResult(
                liveKit = LiveKitConnectionInfo.fromJson(json["livekit"] as Map<String, Any?>),
                participant = LiveParticipant.fromJson(json["participant"] as Map<String, Any?>),
                live = LiveState.fromJson(json["live"] as Map<String, Any?>),
            )
    }
}

/**
 * The fixed LiveKit identity of the server-side music box bot. It joins the
 * room's voice session and publishes a single audio track; filter it out of
 * participant lists so it isn't rendered as a real member.
 */
const val musicBoxBotIdentity = "__musicbox__"

enum class MusicBoxPlaybackState { stopped, playing, paused }

enum class MusicBoxPlaybackMode { sequential, repeatOne, repeatAll, shuffle }

private fun _musicBoxPlaybackModeFrom(value: String?): MusicBoxPlaybackMode =
    when (value?.trim()?.lowercase()) {
        "repeat_one" -> MusicBoxPlaybackMode.repeatOne
        "repeat_all" -> MusicBoxPlaybackMode.repeatAll
        "shuffle" -> MusicBoxPlaybackMode.shuffle
        else -> MusicBoxPlaybackMode.sequential
    }

fun musicBoxPlaybackModeValue(mode: MusicBoxPlaybackMode): String =
    when (mode) {
        MusicBoxPlaybackMode.sequential -> "sequential"
        MusicBoxPlaybackMode.repeatOne -> "repeat_one"
        MusicBoxPlaybackMode.repeatAll -> "repeat_all"
        MusicBoxPlaybackMode.shuffle -> "shuffle"
    }

enum class MusicBoxActiveSourceType { temporary, roomPlaylist, userPlaylist }

private fun _musicBoxActiveSourceTypeFrom(value: String?): MusicBoxActiveSourceType =
    when (value?.trim()?.lowercase()) {
        "room_playlist" -> MusicBoxActiveSourceType.roomPlaylist
        "user_playlist" -> MusicBoxActiveSourceType.userPlaylist
        else -> MusicBoxActiveSourceType.temporary
    }

fun musicBoxActiveSourceTypeValue(type: MusicBoxActiveSourceType): String =
    when (type) {
        MusicBoxActiveSourceType.temporary -> "temporary"
        MusicBoxActiveSourceType.roomPlaylist -> "room_playlist"
        MusicBoxActiveSourceType.userPlaylist -> "user_playlist"
    }

private val _allMusicBoxPlaybackModes: List<MusicBoxPlaybackMode> =
    listOf(
        MusicBoxPlaybackMode.sequential,
        MusicBoxPlaybackMode.repeatOne,
        MusicBoxPlaybackMode.repeatAll,
        MusicBoxPlaybackMode.shuffle,
    )

class MusicBoxCapabilities(
    val canControl: Boolean = true,
    val canChangeMode: Boolean = true,
    val canReorder: Boolean = true,
    val allowedModes: List<MusicBoxPlaybackMode> = _allMusicBoxPlaybackModes,
) {
    companion object {
        fun fromJson(json: JsonMap?): MusicBoxCapabilities {
            val values =
                ((json?.get("allowed_modes") as List<*>?) ?: emptyList<Any?>())
                    .map { value -> _musicBoxPlaybackModeFrom(value?.toString()) }
                    .toSet()
                    .toList()
            return MusicBoxCapabilities(
                canControl = _boolFromJson(json, listOf("can_control")) ?: true,
                canChangeMode = _boolFromJson(json, listOf("can_change_mode")) ?: true,
                canReorder = _boolFromJson(json, listOf("can_reorder")) ?: true,
                allowedModes = if (values.isEmpty()) _allMusicBoxPlaybackModes else values,
            )
        }
    }
}

class MusicBoxActiveSource(
    val type: MusicBoxActiveSourceType = MusicBoxActiveSourceType.temporary,
    val id: String = "",
    val name: String = "点歌队列",
    val ownerUserId: String = "",
    val snapshotId: String = "",
    val createdAt: DateTime? = null,
    val owner: MusicBoxRequester? = null,
) {
    companion object {
        fun fromJson(json: JsonMap?): MusicBoxActiveSource {
            val type = _musicBoxActiveSourceTypeFrom(json?.get("type")?.toString())
            val parsedName = _stringFromJson(json, listOf("name"))
            return MusicBoxActiveSource(
                type = type,
                id = _stringFromJson(json, listOf("id", "playlist_id")) ?: "",
                name =
                    if (type == MusicBoxActiveSourceType.temporary) {
                        "点歌队列"
                    } else {
                        parsedName ?: "未命名歌单"
                    },
                ownerUserId = _stringFromJson(json, listOf("owner_user_id")) ?: "",
                snapshotId = _stringFromJson(json, listOf("snapshot_id")) ?: "",
                createdAt = _parseDateTime(json?.get("created_at")),
                owner = MusicBoxRequester.fromJson(_nullableMap(json?.get("owner"))),
            )
        }
    }
}

private fun _musicBoxPlaybackStateFrom(value: String?): MusicBoxPlaybackState =
    when (value?.trim()?.lowercase()) {
        "playing" -> MusicBoxPlaybackState.playing
        "paused" -> MusicBoxPlaybackState.paused
        else -> MusicBoxPlaybackState.stopped
    }

enum class MusicBoxQueueItemStatus { pending, downloading, ready, failed }

private fun _musicBoxQueueItemStatusFrom(value: String?): MusicBoxQueueItemStatus =
    when (value?.trim()?.lowercase()) {
        "downloading" -> MusicBoxQueueItemStatus.downloading
        "ready" -> MusicBoxQueueItemStatus.ready
        "failed" -> MusicBoxQueueItemStatus.failed
        else -> MusicBoxQueueItemStatus.pending
    }

/**
 * The room music box's current playback head. [positionMs] comes from the
 * server-authoritative snapshot stream; clients render it as reported instead
 * of advancing a separate local clock. See `music_box_display.dart`.
 */
class MusicBoxPlayback(
    val state: MusicBoxPlaybackState,
    /**
     * The `id` of the playing queue item, matching [MusicBoxQueueItem.id].
     * Empty when nothing is current.
     */
    val currentItemId: String,
    val positionMs: Int,
    val volume: Int,
    val updatedAt: DateTime?,
    val mode: MusicBoxPlaybackMode = MusicBoxPlaybackMode.sequential,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val capabilities: MusicBoxCapabilities = MusicBoxCapabilities(),
) {
    val hasCurrent: Boolean get() = currentItemId.isNotEmpty()

    fun copyWith(
        positionMs: Int? = null,
        updatedAt: DateTime? = null,
        canPrevious: Boolean? = null,
        canNext: Boolean? = null,
    ): MusicBoxPlayback =
        MusicBoxPlayback(
            state = state,
            currentItemId = currentItemId,
            positionMs = positionMs ?: this.positionMs,
            volume = volume,
            updatedAt = updatedAt ?: this.updatedAt,
            mode = mode,
            canPrevious = canPrevious ?: this.canPrevious,
            canNext = canNext ?: this.canNext,
            capabilities = capabilities,
        )

    companion object {
        fun fromJson(json: JsonMap): MusicBoxPlayback =
            MusicBoxPlayback(
                state = _musicBoxPlaybackStateFrom(json["state"] as String?),
                currentItemId = _stringFromJson(json, listOf("current_item_id")) ?: "",
                positionMs = _intFromJson(json, listOf("position_ms")) ?: 0,
                volume = _intFromJson(json, listOf("volume")) ?: 100,
                updatedAt = _parseDateTime(json["updated_at"]),
                mode = _musicBoxPlaybackModeFrom(json["mode"]?.toString()),
                canPrevious = _boolFromJson(json, listOf("can_previous")) ?: false,
                canNext = _boolFromJson(json, listOf("can_next")) ?: false,
                capabilities = MusicBoxCapabilities.fromJson(_nullableMap(json["capabilities"])),
            )
    }
}

/**
 * A compact position-only SSE update. Structural changes continue to arrive
 * as a complete [MusicBoxState] snapshot.
 */
class MusicBoxProgressEvent(
    val revision: Int,
    val currentItemId: String,
    val positionMs: Int,
) {
    companion object {
        fun fromJson(json: JsonMap): MusicBoxProgressEvent =
            MusicBoxProgressEvent(
                revision = _intFromJson(json, listOf("revision")) ?: 0,
                currentItemId = _stringFromJson(json, listOf("current_item_id")) ?: "",
                positionMs = _intFromJson(json, listOf("position_ms")) ?: 0,
            )
    }
}

/**
 * One track in the room music box queue. [status] tracks the server-side
 * download/transcode lifecycle; see [playable] for when the room can play it.
 */
class MusicBoxQueueItem(
    val id: String,
    val source: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val durationMs: Int,
    val status: MusicBoxQueueItemStatus,
    val fileSizeBytes: Long,
    /**
     * Failure reason, populated only when [status] is
     * [MusicBoxQueueItemStatus.failed]. Empty otherwise.
     */
    val error: String,
    val addedByUserId: String,
    val createdAt: DateTime?,
    val canPlayNow: Boolean = false,
    val canRemove: Boolean = true,
    val requestedBy: MusicBoxRequester? = null,
    playable: Boolean? = null,
) {
    private val _playable: Boolean? = playable

    /**
     * Whether the room can play this item now: its audio is ready, or it is
     * still [MusicBoxQueueItemStatus.downloading] but the server is already
     * streaming the transcode. Servers that predate streaming don't send the
     * field; for them only ready items are playable.
     */
    val playable: Boolean get() = _playable ?: (status == MusicBoxQueueItemStatus.ready)

    companion object {
        fun fromJson(json: JsonMap): MusicBoxQueueItem =
            MusicBoxQueueItem(
                id = _stringFromJson(json, listOf("id", "item_id")) ?: "",
                source = _stringFromJson(json, listOf("source")) ?: "",
                trackId = _stringFromJson(json, listOf("track_id")) ?: "",
                title = _stringFromJson(json, listOf("title", "name")) ?: "",
                artist = _stringFromJson(json, listOf("artist")) ?: "",
                durationMs = _intFromJson(json, listOf("duration_ms")) ?: 0,
                status = _musicBoxQueueItemStatusFrom(json["status"] as String?),
                fileSizeBytes = _longFromJson(json, listOf("file_size_bytes")) ?: 0L,
                error = _stringFromJson(json, listOf("error")) ?: "",
                addedByUserId = _stringFromJson(json, listOf("added_by_user_id")) ?: "",
                createdAt = _parseDateTime(json["created_at"]),
                canPlayNow = json["can_play_now"] == true,
                canRemove = if (json.containsKey("can_remove")) json["can_remove"] == true else true,
                requestedBy = MusicBoxRequester.fromJson(_nullableMap(json["requested_by"])),
                playable = json["playable"] as? Boolean,
            )
    }
}

class MusicBoxRequester(
    val userId: String,
    val displayName: String,
    username: String? = null,
    avatarLabel: String? = null,
    val avatarUrl: String?,
    val defaultAvatarKey: String,
) {
    val username: String = username ?: avatarLabel ?: displayName
    val avatarLabel: String = avatarLabel ?: displayName

    companion object {
        fun fromJson(json: JsonMap?): MusicBoxRequester? {
            if (json == null) return null
            val userId = _stringFromJson(json, listOf("user_id", "id")) ?: ""
            if (userId.isEmpty()) return null
            return MusicBoxRequester(
                userId = userId,
                username = _stringFromJson(json, listOf("username")),
                displayName = _stringFromJson(json, listOf("display_name", "username")) ?: userId,
                avatarLabel = _stringFromJson(json, listOf("avatar_label", "username")) ?: userId,
                avatarUrl = _stringFromJson(json, listOf("avatar_url")),
                defaultAvatarKey = _stringFromJson(json, listOf("default_avatar_key")) ?: "blue-3",
            )
        }
    }
}

/**
 * The room music box's disk usage against its per-room limit. Useful for
 * warning the user as the room approaches its storage cap; over the limit,
 * new songs queue as `pending` rather than being rejected.
 */
class MusicBoxUsage(val usedBytes: Long, val limitBytes: Long) {
    companion object {
        fun fromJson(json: JsonMap?): MusicBoxUsage =
            MusicBoxUsage(
                usedBytes = _longFromJson(json, listOf("used_bytes")) ?: 0L,
                limitBytes = _longFromJson(json, listOf("limit_bytes")) ?: 0L,
            )
    }
}

/**
 * The authoritative music box snapshot. Every write (queue/control/remove) and
 * the `music_box_changed` SSE event return this same shape; clients overwrite
 * local state wholesale rather than merging field by field.
 */
class MusicBoxState(
    val enabled: Boolean,
    val playback: MusicBoxPlayback,
    val queue: List<MusicBoxQueueItem>,
    val usage: MusicBoxUsage,
    val revision: Int = 0,
    val hasRevision: Boolean = false,
    val activeSource: MusicBoxActiveSource = MusicBoxActiveSource(),
    val temporaryQueuedCount: Int = 0,
    val temporaryQueue: List<MusicBoxQueueItem> = emptyList(),
    val canEnqueueTemporary: Boolean = true,
    val canSwitchTemporary: Boolean = true,
    val canReorderTemporary: Boolean = true,
    val canClearTemporary: Boolean = true,
    val canPlayNowTemporary: Boolean = true,
) {
    /** The queue item currently playing, or null when nothing is current. */
    val currentItem: MusicBoxQueueItem?
        get() {
            if (!playback.hasCurrent) return null
            for (item in queue) {
                if (item.id == playback.currentItemId) return item
            }
            return null
        }

    fun copyWith(playback: MusicBoxPlayback? = null): MusicBoxState =
        MusicBoxState(
            enabled = enabled,
            playback = playback ?: this.playback,
            queue = queue,
            usage = usage,
            revision = revision,
            hasRevision = hasRevision,
            activeSource = activeSource,
            temporaryQueuedCount = temporaryQueuedCount,
            temporaryQueue = temporaryQueue,
            canEnqueueTemporary = canEnqueueTemporary,
            canSwitchTemporary = canSwitchTemporary,
            canReorderTemporary = canReorderTemporary,
            canClearTemporary = canClearTemporary,
            canPlayNowTemporary = canPlayNowTemporary,
        )

    companion object {
        fun fromJson(json: JsonMap): MusicBoxState {
            val playbackJson = _nullableMap(json["playback"])
            val queue = _listOfMaps(json["queue"]).map { MusicBoxQueueItem.fromJson(it) }
            val activeSource = MusicBoxActiveSource.fromJson(_nullableMap(json["active_source"]))
            val parsedTemporaryQueue =
                _listOfMaps(json["temporary_queue"]).map { MusicBoxQueueItem.fromJson(it) }
            val temporaryQueue =
                if (json.containsKey("temporary_queue")) {
                    parsedTemporaryQueue
                } else if (activeSource.type == MusicBoxActiveSourceType.temporary) {
                    queue
                } else {
                    emptyList()
                }
            val temporaryCapabilities =
                _nullableMap(_nullableMap(json["temporary_playlist"])?.get("capabilities"))
            var playback =
                if (playbackJson == null) {
                    MusicBoxPlayback(
                        state = MusicBoxPlaybackState.stopped,
                        currentItemId = "",
                        positionMs = 0,
                        volume = 100,
                        updatedAt = null,
                    )
                } else {
                    MusicBoxPlayback.fromJson(playbackJson)
                }
            if (playbackJson != null &&
                (!playbackJson.containsKey("can_previous") || !playbackJson.containsKey("can_next"))
            ) {
                playback =
                    playback.copyWith(
                        canPrevious =
                            if (playbackJson.containsKey("can_previous")) {
                                null
                            } else {
                                playback.hasCurrent && queue.isNotEmpty()
                            },
                        canNext = if (playbackJson.containsKey("can_next")) null else queue.isNotEmpty(),
                    )
            }
            return MusicBoxState(
                enabled = _boolFromJson(json, listOf("enabled")) ?: false,
                playback = playback,
                queue = queue,
                usage = MusicBoxUsage.fromJson(_nullableMap(json["usage"])),
                revision = _intFromJson(json, listOf("revision")) ?: 0,
                hasRevision = json.containsKey("revision"),
                activeSource = activeSource,
                temporaryQueuedCount =
                    _intFromJson(
                        _nullableMap(json["temporary_playlist"]),
                        listOf(
                            "queued_count",
                        ),
                    ) ?: temporaryQueue.size,
                temporaryQueue = temporaryQueue,
                canEnqueueTemporary = _boolFromJson(temporaryCapabilities, listOf("can_enqueue")) ?: true,
                canSwitchTemporary = _boolFromJson(temporaryCapabilities, listOf("can_switch")) ?: true,
                canReorderTemporary = _boolFromJson(temporaryCapabilities, listOf("can_reorder")) ?: true,
                canClearTemporary = _boolFromJson(temporaryCapabilities, listOf("can_clear")) ?: true,
                canPlayNowTemporary = _boolFromJson(temporaryCapabilities, listOf("can_play_now")) ?: true,
            )
        }
    }
}

/**
 * A single hit from the music box search endpoint. [artists] is an array
 * because a track may credit multiple performers; join it for display and map
 * it onto the queue request's single `artist` string when adding.
 */
class MusicBoxSearchResult(
    val trackId: String,
    val name: String,
    val artists: List<String>,
    val source: String,
) {
    companion object {
        fun fromJson(json: JsonMap): MusicBoxSearchResult {
            val artists = _artistsFromJson(json["artists"])
            return MusicBoxSearchResult(
                trackId = _stringFromJson(json, listOf("track_id", "id")) ?: "",
                name = _stringFromJson(json, listOf("name", "title")) ?: "",
                artists = artists,
                source = _stringFromJson(json, listOf("source")) ?: "",
            )
        }
    }
}

class PersonalMusicPlaylist(
    val id: String,
    val name: String,
    val description: String,
    val revision: Int,
    val itemCount: Int,
    val createdAt: DateTime?,
    val updatedAt: DateTime?,
) {
    fun copyWith(itemCount: Int? = null, revision: Int? = null): PersonalMusicPlaylist =
        PersonalMusicPlaylist(
            id = id,
            name = name,
            description = description,
            revision = revision ?: this.revision,
            itemCount = itemCount ?: this.itemCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        fun fromJson(json: JsonMap): PersonalMusicPlaylist =
            PersonalMusicPlaylist(
                id = _stringFromJson(json, listOf("id")) ?: "",
                name = _stringFromJson(json, listOf("name")) ?: "",
                description = json["description"]?.toString() ?: "",
                revision = _intFromJson(json, listOf("revision")) ?: 0,
                itemCount = _intFromJson(json, listOf("item_count")) ?: 0,
                createdAt = _parseDateTime(json["created_at"]),
                updatedAt = _parseDateTime(json["updated_at"]),
            )
    }
}

class PersonalMusicPlaylistMergeResult(
    val playlist: PersonalMusicPlaylist,
    val sourceItemCount: Int,
    val uniqueItemCount: Int,
    val duplicateCount: Int,
    val itemCount: Int,
    val omittedCount: Int,
    val deletedPlaylistCount: Int,
    val retainedPlaylistCount: Int,
    val consumedSourceItemCount: Int,
    val truncated: Boolean,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonMap): PersonalMusicPlaylistMergeResult {
            val merge = _nullableMap(json["merge"])
            return PersonalMusicPlaylistMergeResult(
                playlist = PersonalMusicPlaylist.fromJson(json["playlist"] as Map<String, Any?>),
                sourceItemCount = _intFromJson(merge, listOf("source_item_count")) ?: 0,
                uniqueItemCount = _intFromJson(merge, listOf("unique_item_count")) ?: 0,
                duplicateCount = _intFromJson(merge, listOf("duplicate_count")) ?: 0,
                itemCount = _intFromJson(merge, listOf("item_count")) ?: 0,
                omittedCount = _intFromJson(merge, listOf("omitted_count")) ?: 0,
                deletedPlaylistCount = _intFromJson(merge, listOf("deleted_playlist_count")) ?: 0,
                retainedPlaylistCount = _intFromJson(merge, listOf("retained_playlist_count")) ?: 0,
                consumedSourceItemCount = _intFromJson(merge, listOf("consumed_source_item_count")) ?: 0,
                truncated = _boolFromJson(merge, listOf("truncated")) ?: false,
            )
        }
    }
}

class PersonalMusicPlaylistBatchAddResult(
    val playlist: PersonalMusicPlaylist,
    val selectedItemCount: Int,
    val uniqueItemCount: Int,
    val duplicateCount: Int,
    val alreadyPresentCount: Int,
    val addedItemCount: Int,
    val omittedCount: Int,
    val truncated: Boolean,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonMap): PersonalMusicPlaylistBatchAddResult {
            val batchAdd = _nullableMap(json["batch_add"])
            return PersonalMusicPlaylistBatchAddResult(
                playlist = PersonalMusicPlaylist.fromJson(json["playlist"] as Map<String, Any?>),
                selectedItemCount = _intFromJson(batchAdd, listOf("selected_item_count")) ?: 0,
                uniqueItemCount = _intFromJson(batchAdd, listOf("unique_item_count")) ?: 0,
                duplicateCount = _intFromJson(batchAdd, listOf("duplicate_count")) ?: 0,
                alreadyPresentCount = _intFromJson(batchAdd, listOf("already_present_count")) ?: 0,
                addedItemCount = _intFromJson(batchAdd, listOf("added_item_count")) ?: 0,
                omittedCount = _intFromJson(batchAdd, listOf("omitted_count")) ?: 0,
                truncated = _boolFromJson(batchAdd, listOf("truncated")) ?: false,
            )
        }
    }
}

class PersonalMusicPlaylistItem(
    val id: String,
    val playlistId: String,
    val trackId: String,
    val source: String,
    val title: String,
    val artists: List<String>,
    val durationMs: Int,
    val sortOrder: Int,
    val createdAt: DateTime?,
) {
    companion object {
        fun fromJson(json: JsonMap): PersonalMusicPlaylistItem =
            PersonalMusicPlaylistItem(
                id = _stringFromJson(json, listOf("id")) ?: "",
                playlistId = _stringFromJson(json, listOf("playlist_id")) ?: "",
                trackId = _stringFromJson(json, listOf("track_id")) ?: "",
                source = _stringFromJson(json, listOf("source")) ?: "",
                title = _stringFromJson(json, listOf("title", "name")) ?: "",
                artists = _artistsFromJson(json["artists"]),
                durationMs = _intFromJson(json, listOf("duration_ms")) ?: 0,
                sortOrder = _intFromJson(json, listOf("sort_order")) ?: 0,
                createdAt = _parseDateTime(json["created_at"]),
            )
    }
}

class PersonalMusicPlaylistPage(
    val playlists: List<PersonalMusicPlaylist>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val hasMore: Boolean,
    val maxPlaylists: Int,
    val maxPlaylistItems: Int,
) {
    companion object {
        fun fromJson(json: JsonMap): PersonalMusicPlaylistPage {
            val pagination = _nullableMap(json["pagination"])
            val limits = _nullableMap(json["limits"])
            return PersonalMusicPlaylistPage(
                playlists = _listOfMaps(json["playlists"]).map { PersonalMusicPlaylist.fromJson(it) },
                page = _intFromJson(pagination, listOf("page")) ?: 1,
                pageSize = _intFromJson(pagination, listOf("page_size")) ?: 50,
                total = _intFromJson(pagination, listOf("total")) ?: 0,
                hasMore = _boolFromJson(pagination, listOf("has_more")) ?: false,
                maxPlaylists = _intFromJson(limits, listOf("max_playlists")) ?: 50,
                maxPlaylistItems = _intFromJson(limits, listOf("max_playlist_items")) ?: 500,
            )
        }
    }
}

class PersonalMusicPlaylistItemsPage(
    val playlist: PersonalMusicPlaylist,
    val items: List<PersonalMusicPlaylistItem>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val hasMore: Boolean,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonMap): PersonalMusicPlaylistItemsPage {
            val pagination = _nullableMap(json["pagination"])
            return PersonalMusicPlaylistItemsPage(
                playlist = PersonalMusicPlaylist.fromJson(json["playlist"] as Map<String, Any?>),
                items = _listOfMaps(json["items"]).map { PersonalMusicPlaylistItem.fromJson(it) },
                page = _intFromJson(pagination, listOf("page")) ?: 1,
                pageSize = _intFromJson(pagination, listOf("page_size")) ?: 50,
                total = _intFromJson(pagination, listOf("total")) ?: 0,
                hasMore = _boolFromJson(pagination, listOf("has_more")) ?: false,
            )
        }
    }
}

// --- JSON helpers --------------------------------------------------------
//
// Decoded JSON integers are Long (see foundation/Json.kt), while maps built in
// Kotlin code hold Int; every numeric read below goes through Number so both
// work, the way Dart's single `int` type does.

@Suppress("UNCHECKED_CAST")
private fun _listOfMaps(value: Any?): List<JsonMap> =
    ((value as List<Any?>?) ?: emptyList()).map { it as Map<String, Any?> }

@Suppress("UNCHECKED_CAST")
private fun _nullableMap(value: Any?): JsonMap? {
    if (value == null) return null
    if (value is Map<*, *>) {
        if (value.keys.all { it is String }) return value as Map<String, Any?>
        val converted = LinkedHashMap<String, Any?>()
        for ((key, nestedValue) in value) converted[key.toString()] = nestedValue
        return converted
    }
    return null
}

private fun _stringFromJson(json: JsonMap?, keys: List<String>): String? {
    if (json == null) return null
    for (key in keys) {
        val text =
            when (val value = json[key]) {
                is String -> value
                is Long, is Int, is Short, is Byte -> value.toString()
                else -> null
            }
        if (text != null && text.trim().isNotEmpty()) return text
    }
    return null
}

private fun _nonEmptyString(value: String?): String? {
    val trimmed = value?.trim()
    if (trimmed == null || trimmed.isEmpty()) return null
    return trimmed
}

private fun _boolFromJson(json: JsonMap?, keys: List<String>): Boolean? {
    if (json == null) return null
    for (key in keys) {
        val value = json[key]
        if (value is Boolean) return value
    }
    return null
}

private fun _intFromJson(json: JsonMap?, keys: List<String>): Int? = _longFromJson(json, keys)?.toInt()

private fun _longFromJson(json: JsonMap?, keys: List<String>): Long? {
    if (json == null) return null
    for (key in keys) {
        val value = _nullableLong(json[key])
        if (value != null) return value
    }
    return null
}

private fun _onlineFromStatus(value: String?): Boolean? {
    val status = value?.trim()?.lowercase()
    if (status == null || status.isEmpty()) return null
    return when (status) {
        "online", "active", "connected", "joining", "joined", "在线" -> true
        "offline", "inactive", "disconnected", "left", "离线" -> false
        else -> null
    }
}

private fun _parseDateTime(value: Any?): DateTime? {
    if (value !is String || value.isEmpty()) return null
    return DateTime.tryParse(value)
}

private fun _nullableInt(value: Any?): Int? = _nullableLong(value)?.toInt()

private fun _nullableLong(value: Any?): Long? =
    when (value) {
        is Long -> value
        is Int, is Short, is Byte -> (value as Number).toLong()
        is Number -> value.toDouble().toLong()
        is String -> dartIntTryParse(value)
        else -> null
    }

/** Dart `json[key] as int?` (throws for non-numbers, like the Dart cast). */
private fun _castInt(value: Any?): Int? = (value as Number?)?.toInt()

/** Dart `json[key]! as int`. */
private fun _requireInt(value: Any?): Int = (value as Number).toInt()

private fun _fromUnixSeconds(value: Long): DateTime = DateTime.fromMillisecondsSinceEpoch(value * 1000)

private fun _nullableUnixSeconds(value: Any?): DateTime? {
    if (value !is Long && value !is Int) return null
    return _fromUnixSeconds((value as Number).toLong())
}

/** `(json['artists'] as List<Object?>? ?? const []).map(trimmed text).where(isNotEmpty)`. */
private fun _artistsFromJson(value: Any?): List<String> =
    ((value as List<*>?) ?: emptyList<Any?>())
        .map { item -> item?.toString()?.trim() ?: "" }
        .filter { item -> item.isNotEmpty() }

/**
 * Dart's `int.tryParse(source)`: surrounding whitespace is ignored, an optional
 * sign is accepted, and a `0x` prefix selects hexadecimal (radix omitted).
 */
internal fun dartIntTryParse(source: String): Long? {
    var text = source.trim()
    if (text.isEmpty()) return null
    var negative = false
    if (text[0] == '+' || text[0] == '-') {
        negative = text[0] == '-'
        text = text.substring(1)
    }
    if (text.isEmpty() || text[0] == '+' || text[0] == '-') return null
    val radix =
        if (text.length > 2 && text[0] == '0' && (text[1] == 'x' || text[1] == 'X')) {
            text = text.substring(2)
            16
        } else {
            10
        }
    val validDigit: (Char) -> Boolean =
        if (radix == 16) {
            { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        } else {
            { it in '0'..'9' }
        }
    if (!text.all(validDigit)) return null
    return (if (negative) "-$text" else text).toLongOrNull(radix)
}
