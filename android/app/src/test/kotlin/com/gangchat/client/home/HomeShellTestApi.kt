package com.gangchat.client.home

import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.foundation.utf8Decode
import com.gangchat.client.protocol.GangApiClient
import com.gangchat.client.testing.MockClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

// Dart's `_roomsApi` and its JSON helpers (test/widget/shell/gang_app_shell_test.dart):
// a GangApiClient whose HTTP client answers the home shell's routes.

val homeShellCurrentUserJson: JsonMap =
    linkedMapOf(
        "id" to "user-1",
        "uid" to "uid-1",
        "username" to "kai",
        "display_name" to "Kai",
        "bio" to "",
        "gender" to "secret",
        "email" to "kai@example.com",
        "email_verified" to true,
        "email_public" to false,
        "phone_number" to null,
        "phone_number_public" to false,
        "avatar_url" to null,
        "default_avatar_key" to "blue-3",
        "is_superuser" to false,
        "created_at" to "2026-01-01T00:00:00Z",
        "status" to "active",
        "language" to "zh-Hans",
    )

fun homeShellJsonResponse(body: Any?, statusCode: Int = 200): HttpResponse =
    HttpResponse(jsonEncode(body), statusCode, headers = mapOf("content-type" to "application/json; charset=utf-8"))

fun homeShellUserJson(
    id: String,
    username: String,
    displayName: String,
    uid: String? = null,
    isOnline: Boolean? = null,
): MutableMap<String, Any?> {
    val json =
        linkedMapOf<String, Any?>(
            "id" to id,
            "username" to username,
            "display_name" to displayName,
            "avatar_url" to null,
            "default_avatar_key" to "blue-3",
        )
    if (uid != null) json["uid"] = uid
    if (isOnline != null) json["is_online"] = isOnline
    return json
}

fun homeShellRoomCardJson(
    id: String,
    name: String,
    joinPolicy: String = "approval_required",
    memberCount: Int = 1,
    liveParticipantCount: Int = 0,
    unreadCount: Int = 0,
    isPinned: Boolean = false,
    hasPendingJoinRequests: Boolean = false,
): MutableMap<String, Any?> =
    linkedMapOf(
        "id" to id,
        "name" to name,
        "rid" to id,
        "visibility" to "private",
        "join_policy" to joinPolicy,
        "description" to "",
        "notification_policy" to "all",
        "avatar_url" to null,
        "default_avatar_key" to "room-1",
        "member_count" to memberCount,
        "live_participant_count" to liveParticipantCount,
        "live_avatar_preview" to emptyList<Any?>(),
        "last_message" to null,
        "unread_count" to unreadCount,
        "has_pending_join_requests" to hasPendingJoinRequests,
        "is_pinned" to isPinned,
        "updated_at" to "2026-06-05T00:00:00Z",
    )

fun homeShellLiveParticipantJson(
    user: JsonMap,
    liveSessionId: String,
    micMuted: Boolean = false,
    micBlocked: Boolean = false,
    headphonesMuted: Boolean = false,
    headphonesBlocked: Boolean = false,
    voiceBlocked: Boolean = false,
    cameraOn: Boolean = false,
    cameraMirrored: Boolean = false,
    screenSharing: Boolean = false,
    connectionState: String = "connected",
): JsonMap =
    linkedMapOf(
        "live_session_id" to liveSessionId,
        "user" to user,
        "joined_at" to "2026-06-05T08:00:00Z",
        "mic_muted" to micMuted,
        "mic_blocked" to micBlocked,
        "headphones_muted" to headphonesMuted,
        "headphones_blocked" to headphonesBlocked,
        "headphones_listening" to (!headphonesMuted && !headphonesBlocked),
        "voice_blocked" to voiceBlocked,
        "camera_on" to cameraOn,
        "camera_mirrored" to cameraMirrored,
        "screen_sharing" to screenSharing,
        "connection_state" to connectionState,
    )

fun homeShellLiveStateJson(
    roomId: String,
    participantCount: Int,
    participants: List<Any?>? = null,
    updatedAt: String = "2026-06-05T08:00:00Z",
): JsonMap =
    linkedMapOf(
        "room_id" to roomId,
        "participant_count" to participantCount,
        "participants" to
            (
                participants
                    ?: if (participantCount <= 0) {
                        emptyList()
                    } else {
                        listOf(homeShellLiveParticipantJson(user = homeShellCurrentUserJson, liveSessionId = "live-session-1"))
                    }
            ),
        "updated_at" to updatedAt,
    )

fun homeShellRoomDetailJson(
    id: String,
    name: String,
    memberCount: Int,
    onlineMemberCount: Int,
    liveParticipantCount: Int,
    description: String = "",
    visibility: String = "private",
    joinPolicy: String = "approval_required",
    aiVoiceAnnouncementsEnabled: Boolean = false,
    role: String = "owner",
    createdBy: JsonMap? = null,
): MutableMap<String, Any?> {
    val json = homeShellRoomCardJson(id = id, name = name, memberCount = memberCount, liveParticipantCount = liveParticipantCount)
    json["online_member_count"] = onlineMemberCount
    json["description"] = description
    json["visibility"] = visibility
    json["join_policy"] = joinPolicy
    json["ai_voice_announcements_enabled"] = aiVoiceAnnouncementsEnabled
    json["my_membership"] = linkedMapOf("joined_at" to "2026-06-01T00:00:00Z", "role" to role)
    json["created_by"] = createdBy ?: homeShellCurrentUserJson
    json["live"] = homeShellLiveStateJson(roomId = id, participantCount = liveParticipantCount)
    json["created_at"] = "2026-06-01T00:00:00Z"
    return json
}

fun homeShellMessageJson(
    id: String,
    roomId: String,
    sender: JsonMap,
    clientMessageId: String,
    body: String,
    type: String = "text",
    attachments: List<Any?> = emptyList(),
): MutableMap<String, Any?> =
    linkedMapOf(
        "id" to id,
        "room_id" to roomId,
        "sender" to sender,
        "client_message_id" to clientMessageId,
        "type" to type,
        "body" to body,
        "attachments" to attachments,
        "created_at" to "2026-06-05T08:00:00Z",
    )

fun homeShellRoomMemberJson(user: JsonMap, role: String = "member"): JsonMap =
    linkedMapOf(
        "user" to user,
        "role" to role,
        "joined_at" to "2026-06-01T00:00:00Z",
        "is_online" to (user["is_online"] as Boolean? ?: false),
    )

fun homeShellJoinRequestJson(id: String, user: JsonMap, reason: String? = null): JsonMap {
    val json =
        linkedMapOf<String, Any?>(
            "id" to id,
            "status" to "pending",
            "user" to user,
            "created_at" to "2026-06-05T08:00:00Z",
        )
    if (reason != null) json["reason"] = reason
    return json
}

fun homeShellSearchRoomContextJson(id: String, name: String): JsonMap =
    linkedMapOf(
        "id" to id,
        "rid" to id,
        "name" to name,
        "avatar_url" to null,
        "default_avatar_key" to "room-1",
    )

fun homeShellRoomInviteJson(
    id: String = "invite-alpha",
    status: String = "pending",
    inviter: JsonMap? = null,
    inviterRoomRole: String = "admin",
    inviterRoomDisplayName: String = "Morgan Admin",
    joinPolicy: String = "closed",
    roomExists: Boolean = true,
    invalidReason: String? = null,
): JsonMap {
    val room = homeShellRoomCardJson(id = "server-alpha", name = "Alpha Room", memberCount = 2, liveParticipantCount = 1)
    room["join_policy"] = joinPolicy
    room["joined"] = false
    room["join_state"] = "none"
    val inviterJson = LinkedHashMap<String, Any?>(inviter ?: homeShellCurrentUserJson)
    inviterJson["room_display_name"] = inviterRoomDisplayName
    inviterJson["room_role"] = inviterRoomRole
    return linkedMapOf(
        "id" to id,
        "status" to status,
        "room_exists" to roomExists,
        "invalid_reason" to invalidReason,
        "room" to room,
        "inviter" to inviterJson,
        "created_at" to "2026-06-05T08:00:00Z",
        "updated_at" to "2026-06-05T08:00:00Z",
    )
}

fun homeShellRoomApplicationJson(
    id: String = "application-alpha",
    status: String = "pending",
    createdAt: String = "2026-06-06T08:00:00Z",
    updatedAt: String = "2026-06-06T08:00:00Z",
    reviewedAt: String? = null,
    reviewer: JsonMap? = null,
): JsonMap {
    val room = homeShellRoomCardJson(id = "server-alpha", name = "Alpha Room", memberCount = 2, liveParticipantCount = 1)
    room["join_policy"] = "approval_required"
    room["joined"] = false
    room["join_state"] = if (status == "pending") "pending" else "none"
    return linkedMapOf(
        "id" to id,
        "status" to status,
        "room" to room,
        "created_at" to createdAt,
        "updated_at" to updatedAt,
        "reviewed_at" to reviewedAt,
        "reviewer" to reviewer,
    )
}

fun homeShellRoomEventNotificationJson(
    id: String = "room-event-alpha",
    type: String = "role_promoted",
    readAt: String? = null,
): JsonMap {
    val room = homeShellRoomCardJson(id = "server-alpha", name = "Alpha Room", memberCount = 2, liveParticipantCount = 1)
    room["join_policy"] = "approval_required"
    room["joined"] = true
    room["join_state"] = "joined"
    val actor = homeShellUserJson(id = "user-2", username = "morgan", displayName = "Morgan")
    actor["room_display_name"] = "Morgan Admin"
    actor["room_role"] = "owner"
    return linkedMapOf(
        "id" to id,
        "type" to type,
        "created_at" to "2026-06-07T08:00:00Z",
        "read_at" to readAt,
        "room_exists" to true,
        "actor_exists" to true,
        "from_role" to "member",
        "to_role" to "admin",
        "message_id" to "msg-room-event-alpha",
        "message_preview" to "@Morgan Admin hello",
        "room" to room,
        "actor" to actor,
    )
}

fun homeShellServerListJson(
    currentRoomJoinPolicy: String = "approval_required",
    pinAlphaRoom: Boolean = false,
    alphaRoomName: String = "Alpha Room",
    alphaRoomUnreadCount: Int = 3,
    alphaRoomHasPendingJoinRequests: Boolean = false,
): List<JsonMap> =
    listOf(
        homeShellRoomCardJson(
            id = "server-alpha",
            name = alphaRoomName,
            memberCount = 2,
            liveParticipantCount = 1,
            unreadCount = alphaRoomUnreadCount,
            joinPolicy = currentRoomJoinPolicy,
            isPinned = pinAlphaRoom,
            hasPendingJoinRequests = alphaRoomHasPendingJoinRequests,
        ),
        homeShellRoomCardJson(id = "server-beta", name = "Beta Room", memberCount = 5),
    )

private val _sharedMusicTrackAttachment: JsonMap =
    linkedMapOf(
        "type" to "music_track",
        "track" to
            linkedMapOf(
                "id" to "mbpi-shared",
                "playlist_id" to "mbp-source",
                "track_id" to "BV1shared",
                "source" to "bilibili",
                "title" to "分享歌曲完整名称",
                "artists" to listOf("分享歌手"),
                "duration_ms" to 180000,
            ),
    )

/**
 * Dart's `_roomsApi(...)`: the options and recording lists of
 * `_homeTestAppContext` plus the route handler. Requests and request bodies
 * land in the public lists.
 */
class HomeShellTestApi(
    val currentRoomRole: String = "owner",
    val currentRoomJoinPolicy: String = "approval_required",
    val secondaryMemberRole: String = "member",
    val secondaryMemberRoomDisplayName: String = "Morgan",
    val includeActionComparisonMember: Boolean = false,
    val includeUnreadRoomNotification: Boolean = false,
    val includeFreshRoomNotificationOnRefresh: Boolean = false,
    val pinAlphaRoom: Boolean = false,
    initialAlphaRoomName: String = "Alpha Room",
    val alphaRoomUnreadCount: Int = 3,
    val alphaRoomHasPendingJoinRequests: Boolean = false,
    initialAlphaRoomAiVoiceAnnouncementsEnabled: Boolean = false,
    val includeSharedMusicTrackMessage: Boolean = false,
    val musicBoxEnabled: Boolean = false,
    musicBoxStateTransientFailures: Int = 0,
    val beforeRoomDetailResponse: (suspend (roomId: String) -> Unit)? = null,
    val beforeLiveStateResponse: (suspend (update: JsonMap) -> Unit)? = null,
) {
    val requestedPaths = mutableListOf<String>()
    val requestedUris = mutableListOf<Uri>()
    val accountUpdates = mutableListOf<JsonMap>()
    val roomCreations = mutableListOf<JsonMap>()
    val roomSettingsUpdates = mutableListOf<JsonMap>()
    val myRoomSettingsUpdates = mutableListOf<JsonMap>()
    val liveJoinRequests = mutableListOf<JsonMap>()
    val liveStateUpdates = mutableListOf<JsonMap>()
    val liveScreenViewUpdates = mutableListOf<String?>()
    val liveOperationLog = mutableListOf<String>()
    val liveModerationActions = mutableListOf<String>()
    val sentMessageRequests = mutableListOf<JsonMap>()

    private val initialAlphaRoomName = initialAlphaRoomName
    private var roomNotificationsMarkedRead = false
    private var actionComparisonMemberRole = "member"
    private var alphaRoomName = initialAlphaRoomName
    private var alphaRoomDescription = ""
    private var alphaRoomVisibility = "private"
    private var alphaRoomJoinPolicy = currentRoomJoinPolicy
    private var alphaRoomAiVoiceAnnouncementsEnabled = initialAlphaRoomAiVoiceAnnouncementsEnabled
    private var alphaRoomNotificationPolicy = "all"
    private var liveMicMuted = false
    private var liveHeadphonesMuted = false
    private var liveCameraOn = false
    private var liveCameraMirrored = false
    private var liveScreenSharing = false
    private var remainingMusicBoxStateFailures = musicBoxStateTransientFailures
    private var sentMessageRequestCount = 0

    val client: GangApiClient =
        GangApiClient(
            baseUrl = "http://example.test/api/v1",
            accessTokenProvider = { _ -> "access-token" },
            httpClient = MockClient { request -> _handle(request) },
        )

    @Suppress("UNCHECKED_CAST")
    private fun _body(request: com.gangchat.client.foundation.http.HttpRequest): JsonMap =
        jsonDecode(utf8Decode(request.bodyBytes)) as JsonMap

    private fun _alphaRoomJson(role: String? = currentRoomRole): MutableMap<String, Any?> {
        val room =
            if (role == null) {
                homeShellRoomDetailJson(
                    id = "server-alpha",
                    name = alphaRoomName,
                    memberCount = 2,
                    onlineMemberCount = 1,
                    liveParticipantCount = 1,
                    description = alphaRoomDescription,
                    visibility = alphaRoomVisibility,
                    joinPolicy = alphaRoomJoinPolicy,
                    aiVoiceAnnouncementsEnabled = alphaRoomAiVoiceAnnouncementsEnabled,
                )
            } else {
                homeShellRoomDetailJson(
                    id = "server-alpha",
                    name = alphaRoomName,
                    memberCount = 2,
                    onlineMemberCount = 1,
                    liveParticipantCount = 1,
                    description = alphaRoomDescription,
                    visibility = alphaRoomVisibility,
                    joinPolicy = alphaRoomJoinPolicy,
                    aiVoiceAnnouncementsEnabled = alphaRoomAiVoiceAnnouncementsEnabled,
                    role = role,
                )
            }
        room["notification_policy"] = alphaRoomNotificationPolicy
        return room
    }

    private fun _morganAccountJson(): MutableMap<String, Any?> {
        val morgan = homeShellUserJson(id = "user-2", username = "morgan", displayName = "Morgan Account", uid = "uid-2", isOnline = true)
        morgan["room_display_name"] = "Morgan"
        return morgan
    }

    private fun _pagedMessage(index: Int): JsonMap =
        linkedMapOf(
            "room" to homeShellSearchRoomContextJson(id = "server-page", name = "Paged Room"),
            "message" to
                homeShellMessageJson(
                    id = "msg-page-$index",
                    roomId = "server-page",
                    sender = homeShellUserJson(id = "user-2", username = "morgan", displayName = "Morgan"),
                    clientMessageId = "client-msg-page-$index",
                    body = "Page result $index",
                ),
        )

    private fun _emptyCursors(): JsonMap =
        linkedMapOf("my_rooms" to null, "public_rooms" to null, "messages" to null, "files" to null)

    private suspend fun _handle(request: com.gangchat.client.foundation.http.HttpRequest): HttpResponse {
        val path = request.url.path
        requestedPaths.add(path)
        requestedUris.add(request.url)
        when {
            path == "/api/v1/rooms" -> {
                if (request.method == "POST") {
                    val body = _body(request)
                    val created = LinkedHashMap<String, Any?>(body)
                    created["id"] = "server-created-${roomCreations.size + 1}"
                    roomCreations.add(created)
                    return homeShellJsonResponse(
                        mapOf(
                            "room" to
                                homeShellRoomDetailJson(
                                    id = created["id"] as String,
                                    name = body["name"] as String,
                                    memberCount = 1,
                                    onlineMemberCount = 1,
                                    liveParticipantCount = 0,
                                    description = body["description"] as String? ?: "",
                                    visibility = body["visibility"] as String? ?: "public",
                                    joinPolicy = body["join_policy"] as String? ?: "approval_required",
                                    aiVoiceAnnouncementsEnabled = body["ai_voice_announcements_enabled"] as Boolean? ?: false,
                                ),
                        ),
                    )
                }
                return homeShellJsonResponse(
                    mapOf(
                        "rooms" to
                            roomCreations.map { created ->
                                homeShellRoomCardJson(id = created["id"] as String, name = created["name"] as String, memberCount = 1)
                            } +
                            homeShellServerListJson(
                                currentRoomJoinPolicy = currentRoomJoinPolicy,
                                pinAlphaRoom = pinAlphaRoom,
                                alphaRoomName = initialAlphaRoomName,
                                alphaRoomUnreadCount = alphaRoomUnreadCount,
                                alphaRoomHasPendingJoinRequests = alphaRoomHasPendingJoinRequests,
                            ),
                    ),
                )
            }
            path == "/api/v1/search" -> {
                val query = request.url.queryParameters["q"]?.lowercase() ?: ""
                if (query == "page") {
                    if (request.url.queryParameters["messages_cursor"] == "message-cursor-8") {
                        return homeShellJsonResponse(
                            mapOf(
                                "my_rooms" to emptyList<Any?>(),
                                "public_rooms" to emptyList<Any?>(),
                                "messages" to listOf(_pagedMessage(9)),
                                "files" to emptyList<Any?>(),
                                "next_cursors" to _emptyCursors(),
                                "total_counts" to mapOf("my_rooms" to 0, "public_rooms" to 0, "messages" to 9, "files" to 0),
                            ),
                        )
                    }
                    return homeShellJsonResponse(
                        mapOf(
                            "my_rooms" to emptyList<Any?>(),
                            "public_rooms" to emptyList<Any?>(),
                            "messages" to (1..8).map { _pagedMessage(it) },
                            "files" to emptyList<Any?>(),
                            "next_cursors" to
                                linkedMapOf(
                                    "my_rooms" to null,
                                    "public_rooms" to null,
                                    "messages" to "message-cursor-8",
                                    "files" to null,
                                ),
                            "total_counts" to mapOf("my_rooms" to 0, "public_rooms" to 0, "messages" to 9, "files" to 0),
                        ),
                    )
                }
                if (query == "1") {
                    val beta = homeShellRoomCardJson(id = "server-beta", name = "Beta Room", memberCount = 5)
                    beta["description"] = "12345"
                    return homeShellJsonResponse(
                        mapOf(
                            "my_rooms" to listOf(beta),
                            "public_rooms" to emptyList<Any?>(),
                            "messages" to emptyList<Any?>(),
                            "files" to emptyList<Any?>(),
                            "next_cursors" to _emptyCursors(),
                        ),
                    )
                }
                val publicRoom = homeShellRoomCardJson(id = "server-public", name = "Beta Public", memberCount = 2)
                publicRoom["visibility"] = "public"
                publicRoom["join_policy"] = "open"
                publicRoom["joined"] = false
                publicRoom["join_state"] = "none"
                val fileMessage =
                    homeShellMessageJson(
                        id = "msg-file-beta",
                        roomId = "server-beta",
                        sender = homeShellCurrentUserJson,
                        clientMessageId = "client-msg-file-beta",
                        body = "Beta brief.pdf",
                    )
                fileMessage["type"] = "file"
                fileMessage["attachments"] =
                    listOf(
                        linkedMapOf(
                            "type" to "file",
                            "name" to "Beta brief.pdf",
                            "asset" to
                                linkedMapOf(
                                    "id" to "asset-beta",
                                    "url" to "/assets/beta.pdf",
                                    "thumbnail_url" to null,
                                    "mime_type" to "application/pdf",
                                    "filename" to "Beta brief.pdf",
                                    "size_bytes" to 1024,
                                ),
                        ),
                    )
                return homeShellJsonResponse(
                    mapOf(
                        "my_rooms" to listOf(homeShellRoomCardJson(id = "server-beta", name = "Beta Room", memberCount = 5)),
                        "public_rooms" to listOf(publicRoom),
                        "messages" to
                            listOf(
                                mapOf(
                                    "room" to homeShellSearchRoomContextJson(id = "server-beta", name = "Beta Room"),
                                    "message" to
                                        homeShellMessageJson(
                                            id = "msg-beta",
                                            roomId = "server-beta",
                                            sender = homeShellUserJson(id = "user-2", username = "morgan", displayName = "Morgan"),
                                            clientMessageId = "client-msg-beta",
                                            body = "Beta release notes",
                                        ),
                                ),
                            ),
                        "files" to
                            listOf(
                                mapOf(
                                    "room" to homeShellSearchRoomContextJson(id = "server-beta", name = "Beta Room"),
                                    "message" to fileMessage,
                                ),
                            ),
                        "next_cursors" to _emptyCursors(),
                    ),
                )
            }
            path.startsWith("/api/v1/rooms/") && path.endsWith("/read") -> {
                assertEquals("POST", request.method)
                val body = _body(request)
                assertTrue(body["last_read_message_id"] is String)
                return homeShellJsonResponse(mapOf("ok" to true, "unread_count" to 0))
            }
            path == "/api/v1/rooms/server-beta" -> {
                beforeRoomDetailResponse?.invoke("server-beta")
                return homeShellJsonResponse(
                    mapOf(
                        "room" to
                            homeShellRoomDetailJson(
                                id = "server-beta",
                                name = "Beta Room",
                                memberCount = 5,
                                onlineMemberCount = 1,
                                liveParticipantCount = 0,
                                visibility = "private",
                                joinPolicy = "closed",
                                role = "member",
                                createdBy = homeShellUserJson(id = "user-2", username = "morgan", displayName = "Morgan", isOnline = true),
                            ),
                    ),
                )
            }
            path == "/api/v1/rooms/server-beta/members/user-2/profile" -> {
                val user = homeShellUserJson(id = "user-2", username = "morgan", displayName = "Morgan", isOnline = true)
                user["bio"] = "Creator profile"
                user["common_rooms"] =
                    listOf(
                        linkedMapOf(
                            "id" to "server-alpha",
                            "rid" to "server-alpha",
                            "name" to "Shared Alpha",
                            "visibility" to "private",
                            "default_avatar_key" to "room-1",
                            "room_role" to "member",
                        ),
                    )
                return homeShellJsonResponse(
                    mapOf(
                        "profile" to
                            linkedMapOf(
                                "user" to user,
                                "room_display_name" to "Morgan Creator",
                                "role" to "owner",
                                "joined_at" to "2026-06-01T00:00:00Z",
                            ),
                    ),
                )
            }
            path == "/api/v1/rooms/server-alpha/members/user-2/profile" -> {
                val user = homeShellUserJson(id = "user-2", username = "morgan", displayName = "Morgan Account", uid = "uid-2", isOnline = true)
                user["room_display_name"] = "Morgan"
                return homeShellJsonResponse(
                    mapOf("profile" to linkedMapOf("user" to user, "role" to "member", "joined_at" to "2026-06-01T00:00:00Z")),
                )
            }
            path == "/api/v1/rooms/server-public/join" || path == "/api/v1/rooms/server-public" ->
                return homeShellJsonResponse(
                    mapOf(
                        "room" to
                            homeShellRoomDetailJson(
                                id = "server-public",
                                name = "Beta Public",
                                memberCount = 3,
                                onlineMemberCount = 1,
                                liveParticipantCount = 0,
                                visibility = "public",
                                joinPolicy = "open",
                                role = "member",
                            ),
                    ),
                )
            path == "/api/v1/rooms/server-public/messages" ->
                return homeShellJsonResponse(
                    mapOf(
                        "messages" to
                            listOf(
                                homeShellMessageJson(
                                    id = "msg-public-history",
                                    roomId = "server-public",
                                    sender = homeShellUserJson(id = "user-2", username = "morgan", displayName = "Morgan"),
                                    clientMessageId = "client-msg-public-history",
                                    body = "History visible immediately after join",
                                ),
                            ),
                        "has_more" to false,
                        "next_before" to null,
                    ),
                )
            path == "/api/v1/rooms/server-public/live" ->
                return homeShellJsonResponse(mapOf("live" to homeShellLiveStateJson(roomId = "server-public", participantCount = 0)))
            path == "/api/v1/users/me/account" -> {
                val body = _body(request)
                accountUpdates.add(body)
                return homeShellJsonResponse(mapOf("user" to LinkedHashMap<String, Any?>(homeShellCurrentUserJson).apply { putAll(body) }))
            }
            path == "/api/v1/rooms/server-alpha/me" -> {
                assertEquals("PATCH", request.method)
                val body = _body(request)
                alphaRoomNotificationPolicy = body["notification_policy"] as String? ?: alphaRoomNotificationPolicy
                alphaRoomAiVoiceAnnouncementsEnabled =
                    body["ai_voice_announcements_enabled"] as Boolean? ?: alphaRoomAiVoiceAnnouncementsEnabled
                myRoomSettingsUpdates.add(body)
                return homeShellJsonResponse(mapOf("room" to _alphaRoomJson()))
            }
            path == "/api/v1/rooms/server-alpha/music-box/playlists" -> {
                assertEquals("GET", request.method)
                return homeShellJsonResponse(
                    mapOf(
                        "playlists" to
                            listOf(
                                linkedMapOf(
                                    "id" to "mbp-room-alpha",
                                    "name" to "Alpha 房间歌单",
                                    "description" to "",
                                    "revision" to 1,
                                    "item_count" to 0,
                                ),
                            ),
                        "pagination" to mapOf("page" to 1, "page_size" to 50, "total" to 1, "has_more" to false),
                        "limits" to mapOf("max_playlists" to 50, "max_playlist_items" to 500),
                    ),
                )
            }
            path == "/api/v1/me/music-box/playlists" -> {
                assertEquals("GET", request.method)
                return homeShellJsonResponse(
                    mapOf(
                        "playlists" to
                            listOf(
                                linkedMapOf(
                                    "id" to "mbp-personal-kai",
                                    "name" to "Kai 的个人歌单",
                                    "description" to "",
                                    "revision" to 1,
                                    "item_count" to 0,
                                ),
                            ),
                        "pagination" to mapOf("page" to 1, "page_size" to 50, "total" to 1, "has_more" to false),
                        "limits" to mapOf("max_playlists" to 50, "max_playlist_items" to 500),
                    ),
                )
            }
            path == "/api/v1/rooms/server-alpha/music-box/playlists/mbp-room-alpha/items" -> {
                assertEquals("POST", request.method)
                val body = _body(request)
                return homeShellJsonResponse(
                    mapOf(
                        "item" to
                            linkedMapOf(
                                "id" to "mbpi-room-added",
                                "playlist_id" to "mbp-room-alpha",
                                "track_id" to body["track_id"],
                                "source" to body["source"],
                                "title" to body["title"],
                                "artists" to body["artists"],
                                "duration_ms" to (body["duration_ms"] ?: 0),
                                "sort_order" to 10,
                            ),
                    ),
                )
            }
            path == "/api/v1/rooms/server-alpha" -> {
                if (request.method == "PATCH") {
                    val body = _body(request)
                    roomSettingsUpdates.add(body)
                    alphaRoomName = body["name"] as String? ?: alphaRoomName
                    alphaRoomDescription = body["description"] as String? ?: alphaRoomDescription
                    alphaRoomVisibility = body["visibility"] as String? ?: alphaRoomVisibility
                    alphaRoomJoinPolicy = body["join_policy"] as String? ?: alphaRoomJoinPolicy
                    alphaRoomAiVoiceAnnouncementsEnabled =
                        body["ai_voice_announcements_enabled"] as Boolean? ?: alphaRoomAiVoiceAnnouncementsEnabled
                    return homeShellJsonResponse(mapOf("room" to _alphaRoomJson(role = null)))
                }
                beforeRoomDetailResponse?.invoke("server-alpha")
                return homeShellJsonResponse(mapOf("room" to _alphaRoomJson()))
            }
            path == "/api/v1/rooms/server-alpha/members" -> {
                val morgan = homeShellUserJson(id = "user-2", username = "morgan", displayName = "Morgan Account", uid = "uid-2", isOnline = true)
                morgan["room_display_name"] = secondaryMemberRoomDisplayName
                val members =
                    mutableListOf(
                        homeShellRoomMemberJson(user = homeShellCurrentUserJson, role = currentRoomRole),
                        homeShellRoomMemberJson(user = morgan, role = secondaryMemberRole),
                    )
                if (includeActionComparisonMember) {
                    members.add(
                        homeShellRoomMemberJson(
                            user = homeShellUserJson(id = "user-5", username = "taylor", displayName = "Taylor", uid = "uid-5", isOnline = true),
                            role = actionComparisonMemberRole,
                        ),
                    )
                }
                return homeShellJsonResponse(mapOf("members" to members, "next_cursor" to null))
            }
            path == "/api/v1/rooms/server-alpha/members/user-2" && request.method == "PATCH" ->
                return homeShellJsonResponse(
                    mapOf(
                        "member" to
                            homeShellRoomMemberJson(
                                user = homeShellUserJson(id = "user-2", username = "morgan", displayName = "Morgan", isOnline = true),
                                role = "admin",
                            ),
                    ),
                )
            path == "/api/v1/rooms/server-alpha/members/user-2" && request.method == "DELETE" ->
                return homeShellJsonResponse(mapOf("ok" to true))
            path == "/api/v1/rooms/server-alpha/members/user-5" && request.method == "PATCH" -> {
                actionComparisonMemberRole = "admin"
                return homeShellJsonResponse(
                    mapOf(
                        "member" to
                            homeShellRoomMemberJson(
                                user = homeShellUserJson(id = "user-5", username = "taylor", displayName = "Taylor", uid = "uid-5", isOnline = true),
                                role = "admin",
                            ),
                    ),
                )
            }
            path == "/api/v1/rooms/server-alpha/live/participants/user-2/moderation" -> {
                assertEquals("POST", request.method)
                val body = jsonDecode(request.body) as JsonMap
                liveModerationActions.add(body["action"] as String)
                assertTrue(body["action"] in listOf("kick", "mute_mic", "block_voice", "restore_voice", "restore_headphones"))
                return homeShellJsonResponse(mapOf("ok" to true))
            }
            path == "/api/v1/rooms/server-alpha/join-requests" ->
                return homeShellJsonResponse(
                    mapOf(
                        "requests" to
                            listOf(
                                homeShellJoinRequestJson(
                                    id = "join-request-riley",
                                    reason = "Please approve my request",
                                    user = homeShellUserJson(id = "user-3", username = "riley", displayName = "Riley", uid = "10000001", isOnline = true),
                                ),
                            ),
                    ),
                )
            path == "/api/v1/rooms/server-alpha/join-requests/join-request-riley" ->
                return homeShellJsonResponse(emptyMap<String, Any?>())
            path == "/api/v1/users/user-3/settings" -> {
                val user = LinkedHashMap<String, Any?>(homeShellCurrentUserJson)
                user["id"] = "user-3"
                user["uid"] = "10000003"
                user["username"] = "riley"
                user["display_name"] = "Riley"
                user["email"] = "riley@example.com"
                user["is_superuser"] = false
                return homeShellJsonResponse(mapOf("user" to user))
            }
            path == "/api/v1/users/user-3/sessions" -> return homeShellJsonResponse(emptyList<Any?>())
            path == "/api/v1/users/user-3/audio-settings" ->
                return homeShellJsonResponse(
                    mapOf(
                        "audio_settings" to
                            linkedMapOf(
                                "default_audio_input_volume" to 100,
                                "default_audio_output_volume" to 100,
                                "live_mic_input_volume" to 100,
                                "live_voice_output_volume" to 100,
                                "live_screen_share_output_volume" to 100,
                                "live_music_output_volume" to 100,
                            ),
                    ),
                )
            path == "/api/v1/users/search" ->
                return homeShellJsonResponse(
                    mapOf(
                        "users" to
                            listOf(
                                homeShellUserJson(id = "user-3", username = "riley", displayName = "Riley", isOnline = true),
                                homeShellUserJson(id = "user-5", username = "river", displayName = "River", isOnline = true),
                                homeShellUserJson(id = "user-6", username = "rina", displayName = "Rina", isOnline = true),
                                homeShellUserJson(id = "user-7", username = "riko", displayName = "Riko", isOnline = true),
                                homeShellUserJson(id = "user-8", username = "rita", displayName = "Rita", isOnline = true),
                            ),
                    ),
                )
            path == "/api/v1/rooms/server-alpha/invites" ->
                return homeShellJsonResponse(mapOf("invite" to homeShellRoomInviteJson(id = "invite-riley")))
            path == "/api/v1/room-invites" ->
                return homeShellJsonResponse(
                    mapOf(
                        "invites" to
                            listOf(
                                homeShellRoomInviteJson(
                                    joinPolicy = "approval_required",
                                    inviter = homeShellUserJson(id = "user-2", username = "morgan", displayName = "Morgan"),
                                    inviterRoomRole = "member",
                                    inviterRoomDisplayName = "Morgan Member",
                                ),
                                homeShellRoomInviteJson(
                                    id = "invite-invalid",
                                    inviter = homeShellUserJson(id = "user-left", username = "lefty", displayName = "Lefty"),
                                    inviterRoomRole = "left",
                                    inviterRoomDisplayName = "Lefty",
                                    invalidReason = "inviter_left",
                                ),
                            ),
                        "next_cursor" to null,
                    ),
                )
            path == "/api/v1/room-applications" -> {
                val reviewer = homeShellUserJson(id = "user-4", username = "ivy", displayName = "Ivy")
                reviewer["room_display_name"] = "Ivy Owner"
                reviewer["room_role"] = "owner"
                return homeShellJsonResponse(
                    mapOf(
                        "applications" to
                            listOf(
                                homeShellRoomApplicationJson(id = "application-alpha"),
                                homeShellRoomApplicationJson(
                                    id = "application-approved",
                                    status = "approved",
                                    createdAt = "2026-06-04T08:00:00Z",
                                    updatedAt = "2026-06-07T08:00:00Z",
                                    reviewedAt = "2026-06-07T08:00:00Z",
                                    reviewer = reviewer,
                                ),
                            ),
                        "next_cursor" to null,
                    ),
                )
            }
            path == "/api/v1/room-notifications" -> {
                val notifications = mutableListOf<JsonMap>()
                if (includeUnreadRoomNotification) {
                    notifications.add(
                        homeShellRoomEventNotificationJson(readAt = if (roomNotificationsMarkedRead) "2026-06-07T09:00:00Z" else null),
                    )
                }
                if (includeFreshRoomNotificationOnRefresh && roomNotificationsMarkedRead) {
                    notifications.add(homeShellRoomEventNotificationJson(id = "room-event-fresh"))
                }
                return homeShellJsonResponse(mapOf("notifications" to notifications, "next_cursor" to null))
            }
            path == "/api/v1/room-notifications/read" -> {
                roomNotificationsMarkedRead = true
                return homeShellJsonResponse(mapOf("ok" to true))
            }
            path == "/api/v1/room-applications/application-alpha" ->
                return homeShellJsonResponse(
                    mapOf("ok" to true, "application" to homeShellRoomApplicationJson(id = "application-alpha", status = "withdrawn")),
                )
            path == "/api/v1/room-invites/invite-alpha" ->
                return homeShellJsonResponse(
                    mapOf(
                        "ok" to true,
                        "invite" to homeShellRoomInviteJson(status = "accepted"),
                        "join_request" to
                            linkedMapOf(
                                "id" to "join-request-alpha",
                                "room_id" to "server-alpha",
                                "status" to "pending",
                                "reason" to "I was invited",
                                "created_at" to "2026-06-05T08:00:00Z",
                            ),
                    ),
                )
            path == "/api/v1/rooms/server-alpha/messages" -> {
                if (request.method == "POST") {
                    val body = _body(request)
                    sentMessageRequestCount += 1
                    sentMessageRequests.add(body)
                    val type = body["type"] as String? ?: "text"
                    val responseAttachments = if (type == "music_track") listOf(_sharedMusicTrackAttachment) else emptyList()
                    return homeShellJsonResponse(
                        mapOf(
                            "message" to
                                homeShellMessageJson(
                                    id = "msg-sent-$sentMessageRequestCount",
                                    roomId = "server-alpha",
                                    sender = homeShellCurrentUserJson,
                                    clientMessageId = body["client_message_id"] as String,
                                    body = body["body"] as String,
                                    type = type,
                                    attachments = responseAttachments,
                                ),
                        ),
                    )
                }
                val messages = mutableListOf<JsonMap>()
                if (includeSharedMusicTrackMessage) {
                    messages.add(
                        homeShellMessageJson(
                            id = "msg-music-track",
                            roomId = "server-alpha",
                            sender = homeShellCurrentUserJson,
                            clientMessageId = "client-msg-music-track",
                            body = "[歌曲] 分享歌曲完整名称",
                            type = "music_track",
                            attachments = listOf(_sharedMusicTrackAttachment),
                        ),
                    )
                }
                messages.add(
                    homeShellMessageJson(
                        id = "msg-1",
                        roomId = "server-alpha",
                        sender = homeShellUserJson(id = "user-2", username = "morgan", displayName = "Morgan", uid = "uid-2", isOnline = true),
                        clientMessageId = "client-msg-1",
                        body = "Hello from Morgan",
                    ),
                )
                messages.add(
                    homeShellMessageJson(
                        id = "msg-2",
                        roomId = "server-alpha",
                        sender = homeShellCurrentUserJson,
                        clientMessageId = "client-msg-2",
                        body = "Reply from Kai",
                    ),
                )
                return homeShellJsonResponse(mapOf("messages" to messages, "has_more" to false, "next_before" to null))
            }
            path == "/api/v1/rooms/server-alpha/message-history" -> {
                val morgan = _morganAccountJson()
                return homeShellJsonResponse(
                    mapOf(
                        "messages" to
                            listOf(
                                homeShellMessageJson(
                                    id = "msg-1",
                                    roomId = "server-alpha",
                                    sender = morgan,
                                    clientMessageId = "client-msg-1",
                                    body = "Hello from Morgan",
                                ),
                                homeShellMessageJson(
                                    id = "msg-2",
                                    roomId = "server-alpha",
                                    sender = homeShellCurrentUserJson,
                                    clientMessageId = "client-msg-2",
                                    body = "Reply from Kai",
                                ),
                                homeShellMessageJson(
                                    id = "msg-system",
                                    roomId = "server-alpha",
                                    sender = homeShellCurrentUserJson,
                                    clientMessageId = "client-msg-system",
                                    type = "system",
                                    body = "Morgan 加入了房间",
                                    attachments = listOf(linkedMapOf("type" to "system", "event" to "room_member_joined", "user" to morgan)),
                                ),
                            ),
                        "has_more" to false,
                        "next_before" to null,
                    ),
                )
            }
            path == "/api/v1/rooms/server-alpha/live" ->
                return homeShellJsonResponse(
                    mapOf(
                        "live" to
                            homeShellLiveStateJson(
                                roomId = "server-alpha",
                                participantCount = 1,
                                participants =
                                    listOf(
                                        homeShellLiveParticipantJson(
                                            user = homeShellUserJson(id = "user-2", username = "morgan", displayName = "Morgan"),
                                            liveSessionId = "live-session-morgan",
                                            micMuted = true,
                                        ),
                                    ),
                            ),
                    ),
                )
            path == "/api/v1/rooms/server-alpha/music-box/state" -> {
                if (remainingMusicBoxStateFailures > 0) {
                    remainingMusicBoxStateFailures--
                    return homeShellJsonResponse(mapOf("code" to "temporarily_unavailable", "message" to "retry"), statusCode = 503)
                }
                return homeShellJsonResponse(
                    mapOf(
                        "enabled" to musicBoxEnabled,
                        "revision" to 1,
                        "playback" to mapOf("state" to "stopped", "current_item_id" to "", "position_ms" to 0, "volume" to 100),
                        "queue" to emptyList<Any?>(),
                        "temporary_queue" to emptyList<Any?>(),
                        "temporary_playlist" to mapOf("queued_count" to 0),
                        "usage" to mapOf("used_bytes" to 0, "limit_bytes" to 209715200),
                        "active_source" to mapOf("type" to "temporary", "id" to "", "name" to "点歌队列"),
                    ),
                )
            }
            path == "/api/v1/rooms/server-alpha/live/join" -> {
                liveOperationLog.add("join")
                val body = _body(request)
                liveJoinRequests.add(body)
                (body["mic_muted"] as? Boolean)?.let { liveMicMuted = it }
                (body["headphones_muted"] as? Boolean)?.let { liveHeadphonesMuted = it }
                val participant =
                    homeShellLiveParticipantJson(
                        user = homeShellCurrentUserJson,
                        liveSessionId = "live-session-joined",
                        micMuted = liveMicMuted,
                        headphonesMuted = liveHeadphonesMuted,
                        cameraOn = liveCameraOn,
                        cameraMirrored = liveCameraMirrored,
                        screenSharing = liveScreenSharing,
                    )
                return homeShellJsonResponse(
                    mapOf(
                        "livekit" to
                            mapOf(
                                "server_url" to "ws://live.example.test",
                                "token" to "live-token",
                                "token_expires_at" to "2026-06-05T09:00:00Z",
                                "room_name" to "server-alpha",
                            ),
                        "participant" to participant,
                        "live" to
                            homeShellLiveStateJson(
                                roomId = "server-alpha",
                                participantCount = 2,
                                participants =
                                    listOf(
                                        participant,
                                        homeShellLiveParticipantJson(
                                            user = homeShellUserJson(id = "user-2", username = "morgan", displayName = "Morgan"),
                                            liveSessionId = "live-session-morgan",
                                            micMuted = true,
                                        ),
                                    ),
                            ),
                    ),
                )
            }
            path == "/api/v1/rooms/server-alpha/live/me" -> {
                val body = _body(request)
                liveOperationLog.add("state:${body["connection_state"] ?: "unchanged"}")
                liveStateUpdates.add(body)
                beforeLiveStateResponse?.invoke(body)
                (body["mic_muted"] as? Boolean)?.let { liveMicMuted = it }
                (body["headphones_muted"] as? Boolean)?.let { liveHeadphonesMuted = it }
                (body["camera_on"] as? Boolean)?.let { liveCameraOn = it }
                (body["camera_mirrored"] as? Boolean)?.let { liveCameraMirrored = it }
                (body["screen_sharing"] as? Boolean)?.let { liveScreenSharing = it }
                return homeShellJsonResponse(
                    mapOf(
                        "participant" to
                            homeShellLiveParticipantJson(
                                user = homeShellCurrentUserJson,
                                liveSessionId = "live-session-joined",
                                micMuted = liveMicMuted,
                                headphonesMuted = liveHeadphonesMuted,
                                cameraOn = liveCameraOn,
                                cameraMirrored = liveCameraMirrored,
                                screenSharing = liveScreenSharing,
                            ),
                    ),
                )
            }
            path == "/api/v1/rooms/server-alpha/live/me/screen-view" -> {
                assertEquals("PATCH", request.method)
                val body = _body(request)
                val broadcasterUserId = body["broadcaster_user_id"] as String?
                liveOperationLog.add("screen-view:${broadcasterUserId ?: ""}")
                liveScreenViewUpdates.add(if (broadcasterUserId == null || broadcasterUserId.isEmpty()) null else broadcasterUserId)
                return homeShellJsonResponse(emptyMap<String, Any?>())
            }
            path == "/api/v1/me" -> return homeShellJsonResponse(homeShellCurrentUserJson)
        }
        return HttpResponse("unexpected request: ${request.url}", 404)
    }
}
