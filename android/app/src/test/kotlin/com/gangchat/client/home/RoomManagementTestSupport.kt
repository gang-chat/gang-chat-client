package com.gangchat.client.home

import com.gangchat.client.app.RoomsController
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.foundation.utf8Decode
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.GangApiClient
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.testing.MockClient

// The room fixtures of test/widget/shell/gang_app_shell_test.dart
// (`_homeTestAppContext` / `_roomsApi`), limited to the routes the room
// settings and members panels call.

fun roomManagementTestCurrentUser(isSuperuser: Boolean = false): CurrentUser =
    CurrentUser(
        id = "user-1",
        uid = "uid-1",
        username = "kai",
        displayName = "Kai",
        bio = "",
        gender = "secret",
        email = "kai@example.com",
        emailPublic = false,
        phoneNumber = null,
        phoneNumberPublic = false,
        avatarUrl = null,
        defaultAvatarKey = "blue-3",
        isSuperuser = isSuperuser,
        createdAt = DateTime.utc(2026),
    )

val roomManagementCurrentUserJson: JsonMap =
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

fun roomManagementUserJson(id: String, username: String, displayName: String, uid: String? = null, isOnline: Boolean? = null): MutableMap<String, Any?> {
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

fun roomManagementRoomCardJson(
    id: String,
    name: String,
    joinPolicy: String = "approval_required",
    memberCount: Int = 1,
    liveParticipantCount: Int = 0,
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
        "unread_count" to 0,
        "has_pending_join_requests" to false,
        "is_pinned" to false,
        "updated_at" to "2026-06-05T00:00:00Z",
    )

fun roomManagementLiveParticipantJson(user: JsonMap, liveSessionId: String, micMuted: Boolean = false): JsonMap =
    linkedMapOf(
        "live_session_id" to liveSessionId,
        "user" to user,
        "joined_at" to "2026-06-05T08:00:00Z",
        "mic_muted" to micMuted,
        "mic_blocked" to false,
        "headphones_muted" to false,
        "headphones_blocked" to false,
        "headphones_listening" to true,
        "voice_blocked" to false,
        "camera_on" to false,
        "camera_mirrored" to false,
        "screen_sharing" to false,
        "connection_state" to "connected",
    )

fun roomManagementLiveStateJson(roomId: String, participantCount: Int, participants: List<Any?>? = null): JsonMap =
    linkedMapOf(
        "room_id" to roomId,
        "participant_count" to participantCount,
        "participants" to
            (
                participants
                    ?: if (participantCount <= 0) {
                        emptyList()
                    } else {
                        listOf(roomManagementLiveParticipantJson(user = roomManagementCurrentUserJson, liveSessionId = "live-session-1"))
                    }
            ),
        "updated_at" to "2026-06-05T08:00:00Z",
    )

fun roomManagementRoomDetailJson(
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
    val json = roomManagementRoomCardJson(id = id, name = name, memberCount = memberCount, liveParticipantCount = liveParticipantCount)
    json["online_member_count"] = onlineMemberCount
    json["description"] = description
    json["visibility"] = visibility
    json["join_policy"] = joinPolicy
    json["ai_voice_announcements_enabled"] = aiVoiceAnnouncementsEnabled
    json["my_membership"] = linkedMapOf("joined_at" to "2026-06-01T00:00:00Z", "role" to role)
    json["created_by"] = createdBy ?: roomManagementCurrentUserJson
    json["live"] = roomManagementLiveStateJson(roomId = id, participantCount = liveParticipantCount)
    json["created_at"] = "2026-06-01T00:00:00Z"
    return json
}

fun roomManagementRoomMemberJson(user: JsonMap, role: String = "member"): JsonMap =
    linkedMapOf(
        "user" to user,
        "role" to role,
        "joined_at" to "2026-06-01T00:00:00Z",
        "is_online" to (user["is_online"] as Boolean? ?: false),
    )

fun roomManagementJoinRequestJson(id: String, user: JsonMap, reason: String? = null): JsonMap {
    val json = linkedMapOf<String, Any?>("id" to id, "status" to "pending", "user" to user, "created_at" to "2026-06-05T08:00:00Z")
    if (reason != null) json["reason"] = reason
    return json
}

fun roomManagementRoomInviteJson(id: String = "invite-alpha"): JsonMap {
    val room = roomManagementRoomCardJson(id = "server-alpha", name = "Alpha Room", memberCount = 2, liveParticipantCount = 1)
    room["join_policy"] = "closed"
    room["joined"] = false
    room["join_state"] = "none"
    val inviter = LinkedHashMap(roomManagementCurrentUserJson)
    inviter["room_display_name"] = "Morgan Admin"
    inviter["room_role"] = "admin"
    return linkedMapOf(
        "id" to id,
        "status" to "pending",
        "room_exists" to true,
        "invalid_reason" to null,
        "room" to room,
        "inviter" to inviter,
        "created_at" to "2026-06-05T08:00:00Z",
        "updated_at" to "2026-06-05T08:00:00Z",
    )
}

private fun _jsonResponse(body: Any?, statusCode: Int = 200): HttpResponse =
    HttpResponse(jsonEncode(body), statusCode, headers = mapOf("content-type" to "application/json; charset=utf-8"))

/** Server state and request log of [RoomManagementTestApi]. */
class RoomManagementTestApi(
    val currentRoomRole: String = "owner",
    val currentRoomJoinPolicy: String = "approval_required",
    val secondaryMemberRole: String = "member",
    val secondaryMemberRoomDisplayName: String = "Morgan",
    val includeActionComparisonMember: Boolean = false,
    /** Blocked users (not in the Dart fixture, which never opens the blacklist). */
    blacklistUsers: List<JsonMap> = emptyList(),
) {
    private val blacklist =
        blacklistUsers.map { user -> linkedMapOf<String, Any?>("user" to user, "created_at" to "2026-06-05T08:00:00Z") }.toMutableList()

    val requestedPaths = mutableListOf<String>()
    val requestMethods = mutableListOf<String>()
    val roomSettingsUpdates = mutableListOf<JsonMap>()
    val myRoomSettingsUpdates = mutableListOf<JsonMap>()

    private var actionComparisonMemberRole = "member"
    private var alphaRoomName = "Alpha Room"
    private var alphaRoomDescription = ""
    private var alphaRoomVisibility = "private"
    private var alphaRoomJoinPolicy = currentRoomJoinPolicy
    private var alphaRoomAiVoiceAnnouncementsEnabled = false
    private var alphaRoomNotificationPolicy = "all"

    @Suppress("UNCHECKED_CAST")
    private fun _body(bodyBytes: ByteArray): JsonMap = jsonDecode(utf8Decode(bodyBytes)) as JsonMap

    private fun _alphaRoomJson(role: String = currentRoomRole): MutableMap<String, Any?> {
        val room =
            roomManagementRoomDetailJson(
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
        room["notification_policy"] = alphaRoomNotificationPolicy
        return room
    }

    val client =
        MockClient { request ->
            val path = request.url.path
            requestedPaths.add(path)
            requestMethods.add("${request.method} $path")
            when {
                path == "/api/v1/rooms/server-alpha/me" -> {
                    val body = _body(request.bodyBytes)
                    alphaRoomNotificationPolicy = body["notification_policy"] as String? ?: alphaRoomNotificationPolicy
                    alphaRoomAiVoiceAnnouncementsEnabled =
                        body["ai_voice_announcements_enabled"] as Boolean? ?: alphaRoomAiVoiceAnnouncementsEnabled
                    myRoomSettingsUpdates.add(body)
                    _jsonResponse(mapOf("room" to _alphaRoomJson()))
                }
                path == "/api/v1/rooms/server-alpha" && request.method == "PATCH" -> {
                    val body = _body(request.bodyBytes)
                    roomSettingsUpdates.add(body)
                    alphaRoomName = body["name"] as String? ?: alphaRoomName
                    alphaRoomDescription = body["description"] as String? ?: alphaRoomDescription
                    alphaRoomVisibility = body["visibility"] as String? ?: alphaRoomVisibility
                    alphaRoomJoinPolicy = body["join_policy"] as String? ?: alphaRoomJoinPolicy
                    alphaRoomAiVoiceAnnouncementsEnabled =
                        body["ai_voice_announcements_enabled"] as Boolean? ?: alphaRoomAiVoiceAnnouncementsEnabled
                    // Dart's PATCH response carries the default ("owner") role.
                    _jsonResponse(mapOf("room" to _alphaRoomJson(role = "owner")))
                }
                path == "/api/v1/rooms/server-alpha" -> _jsonResponse(mapOf("room" to _alphaRoomJson()))
                path == "/api/v1/rooms/server-alpha/members" ->
                    _jsonResponse(
                        mapOf(
                            "members" to
                                buildList<JsonMap> {
                                    add(roomManagementRoomMemberJson(user = roomManagementCurrentUserJson, role = currentRoomRole))
                                    val morgan =
                                        roomManagementUserJson(
                                            id = "user-2",
                                            username = "morgan",
                                            displayName = "Morgan Account",
                                            uid = "uid-2",
                                            isOnline = true,
                                        )
                                    morgan["room_display_name"] = secondaryMemberRoomDisplayName
                                    add(roomManagementRoomMemberJson(user = morgan, role = secondaryMemberRole))
                                    if (includeActionComparisonMember) {
                                        add(
                                            roomManagementRoomMemberJson(
                                                user =
                                                    roomManagementUserJson(
                                                        id = "user-5",
                                                        username = "taylor",
                                                        displayName = "Taylor",
                                                        uid = "uid-5",
                                                        isOnline = true,
                                                    ),
                                                role = actionComparisonMemberRole,
                                            ),
                                        )
                                    }
                                },
                            "next_cursor" to null,
                        ),
                    )
                path == "/api/v1/rooms/server-alpha/members/user-2" && request.method == "PATCH" ->
                    _jsonResponse(
                        mapOf(
                            "member" to
                                roomManagementRoomMemberJson(
                                    user = roomManagementUserJson(id = "user-2", username = "morgan", displayName = "Morgan", isOnline = true),
                                    role = "admin",
                                ),
                        ),
                    )
                path == "/api/v1/rooms/server-alpha/members/user-2" && request.method == "DELETE" -> _jsonResponse(mapOf("ok" to true))
                path == "/api/v1/rooms/server-alpha/members/user-5" && request.method == "PATCH" -> {
                    actionComparisonMemberRole = "admin"
                    _jsonResponse(
                        mapOf(
                            "member" to
                                roomManagementRoomMemberJson(
                                    user =
                                        roomManagementUserJson(
                                            id = "user-5",
                                            username = "taylor",
                                            displayName = "Taylor",
                                            uid = "uid-5",
                                            isOnline = true,
                                        ),
                                    role = "admin",
                                ),
                        ),
                    )
                }
                path == "/api/v1/rooms/server-alpha/join-requests" ->
                    _jsonResponse(
                        mapOf(
                            "requests" to
                                listOf(
                                    roomManagementJoinRequestJson(
                                        id = "join-request-riley",
                                        reason = "Please approve my request",
                                        user =
                                            roomManagementUserJson(
                                                id = "user-3",
                                                username = "riley",
                                                displayName = "Riley",
                                                uid = "10000001",
                                                isOnline = true,
                                            ),
                                    ),
                                ),
                        ),
                    )
                path == "/api/v1/rooms/server-alpha/join-requests/join-request-riley" -> _jsonResponse(emptyMap<String, Any?>())
                path == "/api/v1/users/search" ->
                    _jsonResponse(
                        mapOf(
                            "users" to
                                listOf(
                                    roomManagementUserJson(id = "user-3", username = "riley", displayName = "Riley", isOnline = true),
                                    roomManagementUserJson(id = "user-5", username = "river", displayName = "River", isOnline = true),
                                    roomManagementUserJson(id = "user-6", username = "rina", displayName = "Rina", isOnline = true),
                                    roomManagementUserJson(id = "user-7", username = "riko", displayName = "Riko", isOnline = true),
                                    roomManagementUserJson(id = "user-8", username = "rita", displayName = "Rita", isOnline = true),
                                ),
                        ),
                    )
                path == "/api/v1/rooms/server-alpha/invites" -> _jsonResponse(mapOf("invite" to roomManagementRoomInviteJson(id = "invite-riley")))
                path == "/api/v1/rooms/server-alpha/blacklist" && request.method == "POST" -> {
                    val userId = _body(request.bodyBytes)["user_id"] as String
                    val entry = linkedMapOf<String, Any?>("user" to roomManagementUserJson(id = userId, username = userId, displayName = userId), "created_at" to "2026-06-05T08:00:00Z")
                    blacklist.add(0, entry)
                    _jsonResponse(mapOf("entry" to entry))
                }
                path == "/api/v1/rooms/server-alpha/blacklist" -> _jsonResponse(mapOf("blacklist" to blacklist))
                path.startsWith("/api/v1/rooms/server-alpha/blacklist/") -> {
                    val userId = path.substringAfterLast('/')
                    blacklist.removeAll { (it["user"] as Map<*, *>)["id"] == userId }
                    _jsonResponse(mapOf("ok" to true))
                }
                path == "/api/v1/rooms/server-alpha/live" ->
                    _jsonResponse(
                        mapOf(
                            "live" to
                                roomManagementLiveStateJson(
                                    roomId = "server-alpha",
                                    participantCount = 1,
                                    participants =
                                        listOf(
                                            roomManagementLiveParticipantJson(
                                                user = roomManagementUserJson(id = "user-2", username = "morgan", displayName = "Morgan"),
                                                liveSessionId = "live-session-morgan",
                                                micMuted = true,
                                            ),
                                        ),
                                ),
                        ),
                    )
                path == "/api/v1/rooms/server-alpha/members/user-2/profile" -> {
                    val user = roomManagementUserJson(id = "user-2", username = "morgan", displayName = "Morgan Account", uid = "uid-2", isOnline = true)
                    user["room_display_name"] = "Morgan"
                    _jsonResponse(mapOf("profile" to mapOf("user" to user, "role" to "member", "joined_at" to "2026-06-01T00:00:00Z")))
                }
                else -> HttpResponse("unexpected request: ${request.url}", 404)
            }
        }

    val controller: RoomsController =
        RoomsController(
            GangApiClient(baseUrl = "http://example.test/api/v1", accessTokenProvider = { "access-token" }, httpClient = client),
        )

    /** The room the shell passes in (its `GET /rooms/server-alpha` detail). */
    fun room(): RoomDetail = RoomDetail.fromJson(_alphaRoomJson())

    /** The room's live state from the detail. */
    fun live(): LiveState = room().live
}
