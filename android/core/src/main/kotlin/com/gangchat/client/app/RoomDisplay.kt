package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.LastMessagePreview
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomMemberProfile
import com.gangchat.client.protocol.UserCommonRoom
import com.gangchat.client.protocol.UserSummary

class RoomManagementPermissionState(
    val canEditCreatorOnly: Boolean,
    val canDeleteRoom: Boolean,
)

class RoomAccessState(
    val canManageRoom: Boolean,
    val canReviewJoinRequests: Boolean,
) {
    fun showJoinRequestBadge(hasPendingRequests: Boolean): Boolean {
        return canReviewJoinRequests && hasPendingRequests
    }
}

class RoomLeaveConfirmationSpec(
    val confirmDeleteIfEmpty: Boolean,
    val title: String,
    val body: String,
    val confirmLabel: String,
    val expectedText: String? = null,
) {
    val requiresStrongConfirmation: Boolean get() = expectedText != null
}

class RoomDeletionConfirmationSpec(
    val title: String,
    val body: String,
    val confirmLabel: String,
    val expectedText: String,
)

fun roomSubtitle(room: RoomCard): String {
    val live = room.liveParticipantCount
    val online = room.onlineMemberCount
    val last = room.lastMessage
    if (last != null) {
        val sender = _nonEmpty(last.senderDisplayName) ?: "用户"
        val body = _nonEmpty(last.bodyPreview) ?: "消息"
        return "$sender · $body"
    }
    return "${room.memberCount} 名成员 · $online 人在线 · $live 人语音"
}

fun roomSidebarSubtitle(room: RoomCard): String {
    val last = room.lastMessage
    if (last != null) {
        if (last.type == "system") {
            return "[系统] ${_systemLastMessagePreview(last)}"
        }
        val sender = _nonEmpty(last.senderDisplayName) ?: "用户"
        val body = _nonEmpty(last.bodyPreview) ?: "消息"
        return "$sender · $body"
    }

    val parts = mutableListOf("${room.memberCount} 名成员")
    if (room.liveParticipantCount > 0) {
        parts.add("${room.liveParticipantCount} 人语音")
    }
    return parts.joinToString(" · ")
}

fun roomDraftPreview(draft: String?): String? {
    val normalized =
        draft
            ?.trim()
            ?.split(_whitespace)
            ?.filter { part -> part.isNotEmpty() }
            ?.joinToString(" ")
    if (normalized == null || normalized.isEmpty()) return null
    return normalized
}

fun roomAttachmentDraftPreview(
    filename: String,
    mimeType: String?,
): String {
    val normalized =
        filename
            .trim()
            .split(_whitespace)
            .filter { part -> part.isNotEmpty() }
            .joinToString(" ")
    val label =
        if ((mimeType ?: "").lowercase().startsWith("image/")) {
            "[图片]"
        } else {
            "[文件]"
        }
    if (normalized.isEmpty()) return label
    return "$label $normalized"
}

fun roomDraftPreviewText(
    text: String?,
    attachmentFilename: String?,
    attachmentMimeType: String?,
    hasQuote: Boolean = false,
): String? {
    val preview: String? =
        if (attachmentFilename != null) {
            roomAttachmentDraftPreview(
                filename = attachmentFilename,
                mimeType = attachmentMimeType,
            )
        } else {
            roomDraftPreview(text)
        }
    if (!hasQuote) return preview
    return if (preview == null) "[引用]" else "[引用] $preview"
}

private fun _systemLastMessagePreview(last: LastMessagePreview): String {
    val sender = _nonEmpty(last.senderDisplayName)
    val body = _nonEmpty(last.bodyPreview)
    val roomProfilePreview =
        _roomProfileSystemLastMessagePreview(
            sender = sender,
            body = body,
        )
    if (roomProfilePreview != null) return roomProfilePreview
    val parts = listOfNotNull(sender, body)
    if (parts.isEmpty()) return "系统消息"
    return parts.joinToString(" ")
}

private fun _roomProfileSystemLastMessagePreview(
    sender: String?,
    body: String?,
): String? {
    if (body == null) return null
    if (body.startsWith("房间名称 被 ") ||
        body.startsWith("房间简介 被 ") ||
        body.startsWith("房间可见性 被 ") ||
        body.startsWith("房间加入方式 被 ")
    ) {
        return body
    }

    val namePrefix = "房间名称修改为"
    val descriptionPrefix = "房间简介修改为"
    val visibilityPrefix = "房间可见性修改为"
    val joinPolicyPrefix = "房间加入方式修改为"
    if (body.startsWith(namePrefix)) {
        return _roomProfileChangedPreview(
            subject = "房间名称",
            sender = sender,
            value = body.substring(namePrefix.length),
        )
    }
    if (body.startsWith(descriptionPrefix)) {
        return _roomProfileChangedPreview(
            subject = "房间简介",
            sender = sender,
            value = body.substring(descriptionPrefix.length),
        )
    }
    if (body.startsWith(visibilityPrefix)) {
        return _roomProfileChangedPreview(
            subject = "房间可见性",
            sender = sender,
            value = body.substring(visibilityPrefix.length),
        )
    }
    if (body.startsWith(joinPolicyPrefix)) {
        return _roomProfileChangedPreview(
            subject = "房间加入方式",
            sender = sender,
            value = body.substring(joinPolicyPrefix.length),
        )
    }
    return null
}

private fun _roomProfileChangedPreview(
    subject: String,
    sender: String?,
    value: String,
): String {
    val normalizedValue = _normalizeSystemChangedValue(value)
    if (sender == null) return "$subject 修改为 $normalizedValue"
    return "$subject 被 $sender 修改为 $normalizedValue"
}

private fun _normalizeSystemChangedValue(value: String): String {
    val normalized =
        value
            .trim()
            .split(_whitespace)
            .filter { part -> part.isNotEmpty() }
            .joinToString(" ")
    if (normalized.isEmpty()) return "（空）"
    return normalized
}

fun roomSidebarLastMessageTime(room: RoomCard, now: DateTime? = null): String {
    val last = room.lastMessage ?: return ""
    return roomSidebarTimestamp(last.createdAt, now = now)
}

fun roomSidebarTimestamp(value: DateTime, now: DateTime? = null): String {
    val local = value.toLocal()
    val localNow = (now ?: DateTime.now()).toLocal()
    val today = DateTime(localNow.year, localNow.month, localNow.day)
    val date = DateTime(local.year, local.month, local.day)
    val dayDelta = today.difference(date).inWholeDays
    val time = _formatClock(local)

    if (dayDelta == 0L) return time
    if (dayDelta == 1L) return "昨天 $time"
    if (dayDelta == 2L) return "前天 $time"
    if (dayDelta >= 3 && dayDelta < 7) return _weekdayLabel(local.weekday)
    return "${local.year}/${_twoDigits(local.month)}/${_twoDigits(local.day)}"
}

fun roomCopySuccessNotice(label: String): String {
    return "$label 已复制"
}

@Suppress("UNUSED_PARAMETER")
fun roomCopyFailureMessage(error: Any): String {
    return "无法复制"
}

fun userUidCopySuccessNotice(): String {
    return "UID 已复制"
}

@Suppress("UNUSED_PARAMETER")
fun userUidCopyFailureMessage(error: Any): String {
    return "无法复制 UID"
}

@Suppress("UNUSED_PARAMETER")
fun roomOpenFailureMessage(error: Any): String {
    return "无法打开房间"
}

fun roomOptimisticOpenRefreshFailureNotice(): String {
    return "房间刷新失败，已先打开当前房间"
}

fun roomUseGlobalProfileNotice(): String {
    return "保存后将使用全局默认用户名和默认头像"
}

fun roomInfoSavedNotice(): String {
    return "房间信息已保存"
}

fun roomPersonalPreferencesSavedNotice(): String {
    return "个人偏好已保存"
}

fun roomLeaveConfirmationSpec(
    room: RoomDetail,
    isInLive: Boolean,
): RoomLeaveConfirmationSpec {
    val confirmDeleteIfEmpty = room.isCreator && room.memberCount <= 1
    if (confirmDeleteIfEmpty) {
        return RoomLeaveConfirmationSpec(
            confirmDeleteIfEmpty = true,
            title = "退出并删除房间",
            body = "这是房间里的最后一位成员。退出会删除房间和所有房间内数据，请输入房间名确认。",
            confirmLabel = "退出并删除",
            expectedText = room.name,
        )
    }
    return RoomLeaveConfirmationSpec(
        confirmDeleteIfEmpty = false,
        title = "退出房间",
        body =
            if (isInLive) {
                "退出后会离开当前房间，并同时离开 Live Channel。"
            } else {
                "退出后你会从房间成员中移除，房间会从列表中消失。"
            },
        confirmLabel = "退出",
    )
}

fun roomDeletionConfirmationSpec(room: RoomDetail): RoomDeletionConfirmationSpec {
    return RoomDeletionConfirmationSpec(
        title = "删除房间",
        body = "将清空房间所有数据。这个动作不可恢复，请输入房间名确认。",
        confirmLabel = "删除房间",
        expectedText = room.name,
    )
}

fun roomIdentifier(room: RoomDetail): String {
    return _nonEmpty(room.rid) ?: room.id
}

fun roomCreatedAtLabel(value: DateTime): String {
    val local = value.toLocal()
    return "${local.year.toString().padStart(4, '0')}/" +
        "${_twoDigits(local.month)}/" +
        "${_twoDigits(local.day)} " +
        "${_twoDigits(local.hour)}:" +
        _twoDigits(local.minute)
}

fun roomDescriptionValue(room: RoomDetail): String? {
    return _nonEmpty(room.description)
}

fun roomDescriptionText(room: RoomDetail): String {
    return roomDescriptionValue(room) ?: "暂无介绍"
}

fun roomDisplayName(room: RoomDetail): String {
    val remark = _nonEmpty(room.remarkName) ?: return room.name
    return remark
}

fun roomAvatarLabel(room: RoomDetail): String {
    return _nonEmpty(room.name) ?: room.rid
}

fun roomCardAvatarLabel(room: RoomCard): String {
    return _nonEmpty(room.name) ?: room.rid
}

fun publicRoomAvatarLabel(room: PublicRoom): String {
    return _nonEmpty(room.avatarLabel) ?: _nonEmpty(room.name) ?: room.rid
}

fun publicRoomFromRoomDetail(room: RoomDetail): PublicRoom {
    return PublicRoom(
        id = room.id,
        rid = room.rid,
        name = roomDisplayName(room),
        avatarLabel = roomAvatarLabel(room),
        avatarUrl = room.avatarUrl,
        defaultAvatarKey = room.defaultAvatarKey,
        visibility = room.visibility,
        joinPolicy = room.joinPolicy,
        description = room.description,
        memberCount = room.memberCount,
        onlineMemberCount = room.onlineMemberCount,
        liveParticipantCount = room.live.participantCount,
        joined = true,
        joinState = "joined",
        createdBy = room.createdBy,
        personalProfile = room.personalProfile,
        myMembership = room.myMembership,
    )
}

fun roomMemberSummary(room: RoomDetail): String {
    return "${room.memberCount} 名成员 · ${room.onlineMemberCount} 人在线"
}

fun userPrimaryName(user: UserSummary): String {
    return _nonEmpty(user.roomDisplayName)
        ?: _nonEmpty(user.displayName)
        ?: user.username
}

fun userAvatarLabel(user: UserSummary): String {
    return _nonEmpty(user.displayName) ?: user.username
}

fun userUidLabel(user: UserSummary): String {
    return user.uid ?: user.id
}

fun userUsernameLabel(user: UserSummary): String {
    return "@${user.username}"
}

fun userIdentityMeta(user: UserSummary): String {
    return "${userUidLabel(user)} · ${userUsernameLabel(user)}"
}

fun userSignatureText(user: UserSummary): String? {
    return _nonEmpty(user.bio)
}

fun userRoomsSectionTitle(
    user: UserSummary,
    currentUser: CurrentUser,
): String? {
    if (user.isSuperuser) return null
    if (currentUser.isSuperuser || user.id == currentUser.id) {
        return "所有房间"
    }
    return "共同房间"
}

fun roomRoleLabel(user: UserSummary, ownerUserId: String? = null): String {
    val role = _nonEmpty(user.roomRole)?.lowercase()
    if (role == "pending") return "待审批"
    if (user.isSuperuser || role == "superuser") return "超级用户"
    if (user.id == ownerUserId || role == "owner" || role == "creator") {
        return "创建者"
    }
    if (role == "admin" || role == "administrator") return "管理员"
    return "成员"
}

fun livePresenceAnnouncementForUser(
    user: UserSummary,
    action: LivePresenceAnnouncementAction,
    ownerUserId: String? = null,
): LivePresenceAnnouncement {
    val roomDisplayName =
        _nonEmpty(user.roomDisplayName)
            ?: _nonEmpty(user.displayName)
            ?: _nonEmpty(user.username)
            ?: "用户"
    return LivePresenceAnnouncement(
        roleLabel = roomRoleLabel(user, ownerUserId = ownerUserId),
        roomDisplayName = roomDisplayName,
        action = action,
    )
}

fun commonRoomTitle(room: UserCommonRoom): String {
    val rid = _nonEmpty(room.rid)
    val name = commonRoomDisplayName(room)
    if (rid == null) return name
    return "$name · $rid"
}

fun commonRoomDisplayName(room: UserCommonRoom): String {
    val remark = _nonEmpty(room.remarkName) ?: return room.name
    return remark
}

fun userWithLocalCommonRoomNames(
    user: UserSummary,
    rooms: Iterable<RoomCard>,
): UserSummary {
    return user.copyWith(
        commonRooms =
            commonRoomsWithLocalRoomNames(
                commonRooms = user.commonRooms,
                rooms = rooms,
            ),
    )
}

fun commonRoomsWithLocalRoomNames(
    commonRooms: List<UserCommonRoom>,
    rooms: Iterable<RoomCard>,
): List<UserCommonRoom> {
    if (commonRooms.isEmpty()) return commonRooms
    val localRoomsById = LinkedHashMap<String, RoomCard>()
    for (room in rooms) {
        if (room.id.trim().isNotEmpty()) localRoomsById[room.id] = room
    }
    if (localRoomsById.isEmpty()) return commonRooms

    var changed = false
    val resolved = mutableListOf<UserCommonRoom>()
    for (commonRoom in commonRooms) {
        val localRoom = localRoomsById[commonRoom.id]
        if (localRoom == null) {
            resolved.add(commonRoom)
            continue
        }
        changed = true
        resolved.add(
            UserCommonRoom(
                id = commonRoom.id,
                rid = localRoom.rid,
                name = localRoom.name,
                visibility = localRoom.visibility,
                remarkName = localRoom.remarkName,
                avatarUrl = localRoom.avatarUrl,
                defaultAvatarKey = localRoom.defaultAvatarKey,
                roomDisplayName = commonRoom.roomDisplayName,
                roomRole = commonRoom.roomRole,
            ),
        )
    }
    return if (changed) resolved else commonRooms
}

fun commonRoomAvatarLabel(room: UserCommonRoom): String {
    return _nonEmpty(room.name) ?: room.rid
}

fun visibilityLabel(value: String): String {
    return when (value.lowercase()) {
        "public" -> "公开"
        else -> "私有"
    }
}

fun roomJoinPolicyLabel(value: String): String {
    return when (normalizeRoomJoinPolicy(value)) {
        "open" -> "开放"
        "closed" -> "关闭"
        else -> "需审批"
    }
}

fun commonRoomMeta(room: UserCommonRoom): String? {
    val roomDisplayName = _nonEmpty(room.roomDisplayName)
    val roleLabel = roomRoleLabelFromValue(room.roomRole)
    val parts = listOfNotNull(roomDisplayName, roleLabel)
    if (parts.isEmpty()) return null
    return parts.joinToString(" · ")
}

fun userPresenceLabel(user: UserSummary): String? {
    val isOnline = user.isOnline ?: return null
    return if (isOnline) "在线" else "离线"
}

fun currentUserPresenceLabel(
    user: CurrentUser,
    inLive: Boolean,
    reconnecting: Boolean = false,
): String {
    if (reconnecting) return "重连中"
    if (inLive) return "语音"
    return userPresenceLabel(user.toSummary()) ?: "在线"
}

fun roomUserInfoProfile(
    user: UserSummary,
    room: RoomDetail,
    currentUser: CurrentUser,
): UserSummary {
    if (user.isDeleted) return user
    var profile = user
    val createdBy = room.createdBy
    if (createdBy != null && user.id == createdBy.id) {
        profile = profile.mergeMissing(createdBy)
    }
    if (user.id == currentUser.id) {
        profile = profile.mergeMissing(currentUser.toSummary())
    }
    val roomRole =
        profile.roomRole
            ?: if (createdBy != null && user.id == createdBy.id) {
                "owner"
            } else if (user.id == currentUser.id) {
                room.myMembership.role
            } else {
                null
            }
    return profile.copyWith(roomRole = roomRole)
}

/**
 * Merges a room profile with a lightweight list/message snapshot only while
 * the user is still a room member. A former member's room-scoped name, role,
 * and avatar snapshot must not override the current default account profile.
 */
fun resolvedRoomMemberProfileUser(
    profile: RoomMemberProfile,
    fallback: UserSummary,
): UserSummary {
    val role = profile.role.trim().lowercase()
    if (profile.user.isDeleted || role == "left" || role == "deleted") {
        return profile.user
    }
    return profile.user.mergeMissing(fallback)
}

fun roomUserInfoCommonRooms(
    user: UserSummary,
    selectedRoom: RoomDetail,
    currentUser: CurrentUser,
    includeSelectedRoom: Boolean,
): List<UserCommonRoom> {
    if (user.isDeleted) return emptyList()
    if (user.isSuperuser) {
        return emptyList()
    }
    if (currentUser.isSuperuser || user.id == currentUser.id) {
        return if (includeSelectedRoom) {
            commonRoomsWithSelectedRoom(user = user, room = selectedRoom)
        } else {
            user.commonRooms
        }
    }
    if (includeSelectedRoom) {
        return commonRoomsWithSelectedRoom(user = user, room = selectedRoom)
    }
    return user.commonRooms
}

fun commonRoomsWithSelectedRoom(
    user: UserSummary,
    room: RoomDetail,
): List<UserCommonRoom> {
    val rooms = mutableListOf<UserCommonRoom>()
    rooms.add(
        UserCommonRoom(
            id = room.id,
            rid = room.rid,
            name = room.name,
            visibility = room.visibility,
            remarkName = room.remarkName,
            avatarUrl = room.avatarUrl,
            defaultAvatarKey = room.defaultAvatarKey,
            roomDisplayName = user.roomDisplayName,
            roomRole = user.roomRole,
        ),
    )
    for (commonRoom in user.commonRooms) {
        if (commonRoom.id != room.id) rooms.add(commonRoom)
    }
    return rooms
}

fun roomRoleLabelFromValue(value: String?): String? {
    return when (_nonEmpty(value)?.lowercase()) {
        "superuser" -> "超级用户"
        "owner", "creator" -> "创建者"
        "admin", "administrator" -> "管理员"
        "member" -> "成员"
        "pending" -> "待审批"
        else -> null
    }
}

fun publicRoomJoinActionLabel(room: PublicRoom, pending: Boolean): String {
    if (room.joined) return "进入"
    if (pending) return "待审批"
    if (room.joinPolicy == "closed") return "不可加入"
    return "加入"
}

fun publicRoomJoinActionable(room: PublicRoom, pending: Boolean): Boolean {
    if (room.isDeleted) return false
    if (room.joined) return true
    if (pending) return false
    return room.joinPolicy != "closed"
}

fun publicRoomJoinRequiresApplication(room: PublicRoom): Boolean {
    return !room.isDeleted &&
        !room.joined &&
        room.joinPolicy == "approval_required"
}

fun roomAccessState(
    room: RoomDetail,
    currentUser: CurrentUser,
): RoomAccessState {
    val canManageRoom = room.isAdmin || currentUser.isSuperuser
    return RoomAccessState(
        canManageRoom = canManageRoom,
        canReviewJoinRequests = canManageRoom,
    )
}

fun roomManagementPermissionState(
    room: RoomDetail,
    currentUser: CurrentUser,
): RoomManagementPermissionState {
    val currentUserIsSuperuser = currentUser.isSuperuser
    return RoomManagementPermissionState(
        canEditCreatorOnly =
            room.isCreator || room.isSuperuser || currentUserIsSuperuser,
        canDeleteRoom = room.canDelete || currentUserIsSuperuser,
    )
}

fun roomProfileAvatarPath(
    usingGlobalProfile: Boolean,
    currentUserAvatarUrl: String?,
    pendingAvatarUrl: String?,
    usingPresetAvatar: Boolean,
    personalAvatarUrl: String?,
): String? {
    if (usingGlobalProfile) return currentUserAvatarUrl
    if (pendingAvatarUrl != null) return pendingAvatarUrl
    if (usingPresetAvatar) return null
    return personalAvatarUrl ?: currentUserAvatarUrl
}

fun roomProfileUploadedAvatarSelected(
    usingGlobalProfile: Boolean,
    usingPresetAvatar: Boolean,
    pendingAvatarUrl: String?,
    personalAvatarUrl: String?,
): Boolean {
    return !usingGlobalProfile &&
        !usingPresetAvatar &&
        (pendingAvatarUrl != null || personalAvatarUrl != null)
}

fun roomProfilePresetAvatarSelected(
    usingGlobalProfile: Boolean,
    usingPresetAvatar: Boolean,
): Boolean {
    return !usingGlobalProfile && usingPresetAvatar
}

fun roomProfileDisplayName(
    usingGlobalProfile: Boolean,
    currentUserDisplayName: String,
    roomDisplayNameText: String,
): String {
    if (usingGlobalProfile) return currentUserDisplayName
    return _nonEmpty(roomDisplayNameText) ?: currentUserDisplayName
}

fun roomManagementAvatarPath(
    usingPresetAvatar: Boolean,
    pendingAvatarUrl: String?,
    roomAvatarUrl: String?,
): String? {
    if (usingPresetAvatar) return null
    return pendingAvatarUrl ?: roomAvatarUrl
}

fun roomManagementUploadedAvatarSelected(usingPresetAvatar: Boolean): Boolean {
    return !usingPresetAvatar
}

fun normalizeRoomNotificationPolicy(value: String): String {
    return when (value.trim().lowercase()) {
        "silent",
        "quiet",
        "no_alert",
        "no_alerts",
        "receive_silent",
        "mention",
        "mentions",
        "only_mentions",
        "mention_only",
        "mute",
        "muted",
        "do_not_disturb",
        "dnd",
        -> "silent"
        "block", "blocked", "ignore", "ignored" -> "blocked"
        else -> "all"
    }
}

fun normalizeRoomVisibility(value: String): String {
    return when (value.trim().lowercase()) {
        "private" -> "private"
        else -> "public"
    }
}

fun normalizeRoomJoinPolicy(value: String): String {
    return when (value.trim().lowercase()) {
        "open", "allow_anyone", "anyone" -> "open"
        "closed", "none", "deny_all", "no_one" -> "closed"
        else -> "approval_required"
    }
}

private fun _nonEmpty(value: String?): String? {
    val trimmed = value?.trim()
    if (trimmed == null || trimmed.isEmpty()) return null
    return trimmed
}

/**
 * Dart's `RegExp(r'\s+')`: Dart regexps use JavaScript's `\s`, which also
 * matches Unicode spaces such as the ideographic space (U+3000) that Java's
 * default `\s` does not.
 */
private val _whitespace =
    Regex("[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+")

private fun _formatClock(value: DateTime): String {
    return "${_twoDigits(value.hour)}:${_twoDigits(value.minute)}"
}

private fun _twoDigits(value: Int): String = value.toString().padStart(2, '0')

private fun _weekdayLabel(weekday: Int): String {
    return when (weekday) {
        1 -> "星期一"
        2 -> "星期二"
        3 -> "星期三"
        4 -> "星期四"
        5 -> "星期五"
        6 -> "星期六"
        7 -> "星期日"
        else -> "星期日"
    }
}
