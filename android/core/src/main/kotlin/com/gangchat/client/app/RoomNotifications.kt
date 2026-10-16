package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomApplication
import com.gangchat.client.protocol.RoomEventNotification
import com.gangchat.client.protocol.RoomInvite
import com.gangchat.client.protocol.UserSummary
import java.util.Objects

enum class RoomNotificationFilter { all, invites, applications, roomNotifications }

/**
 * Dart's factory constructors: `RoomNotificationDateRange(startDate:,
 * endDate:)` (the companion `invoke`) and
 * `RoomNotificationDateRange.defaultFor(...)`.
 */
class RoomNotificationDateRange private constructor(
    val startDate: DateTime,
    val endDate: DateTime,
    @Suppress("UNUSED_PARAMETER") normalized: Unit,
) {
    fun includes(value: DateTime): Boolean {
        val date = _localDateOnly(value)
        return !date.isBefore(startDate) && !date.isAfter(endDate)
    }

    override fun equals(other: Any?): Boolean {
        return other is RoomNotificationDateRange &&
            other.startDate == startDate &&
            other.endDate == endDate
    }

    override fun hashCode(): Int = Objects.hash(startDate, endDate)

    companion object {
        operator fun invoke(
            startDate: DateTime,
            endDate: DateTime,
        ): RoomNotificationDateRange {
            val normalizedStart = _localDateOnly(startDate)
            val normalizedEnd = _localDateOnly(endDate)
            if (normalizedEnd.isBefore(normalizedStart)) {
                // Dart: ArgumentError.value(endDate, 'endDate', ...).
                throw IllegalArgumentException("(endDate): must not be before startDate: $endDate")
            }
            return RoomNotificationDateRange(normalizedStart, normalizedEnd, Unit)
        }

        fun defaultFor(
            accountCreatedAt: DateTime?,
            today: DateTime,
        ): RoomNotificationDateRange {
            val normalizedToday = _localDateOnly(today)
            val accountDate =
                if (accountCreatedAt == null) {
                    DateTime(1970)
                } else {
                    _localDateOnly(accountCreatedAt)
                }
            return RoomNotificationDateRange(
                startDate =
                    if (accountDate.isAfter(normalizedToday)) {
                        normalizedToday
                    } else {
                        accountDate
                    },
                endDate = normalizedToday,
            )
        }
    }
}

enum class RoomNotificationItemType {
    invite,
    applicationRequested,
    applicationReviewed,
    roomEvent,
}

const val roomNotificationDeletionInvite = "invite"
const val roomNotificationDeletionApplicationRequested = "application_requested"
const val roomNotificationDeletionApplicationReviewed = "application_reviewed"
const val roomNotificationDeletionRoomEvent = "room_event"

const val kRoomEventNotificationMemberRemoved = "member_removed"
const val kRoomEventNotificationRolePromoted = "role_promoted"
const val kRoomEventNotificationRoleDemoted = "role_demoted"
const val kRoomEventNotificationCreatorTransferDemoted = "creator_transfer_demoted"
const val kRoomEventNotificationMentioned = "mentioned"

const val missingRoomNotificationRoomLabel = "房间已删除"
const val missingRoomNotificationRoomAvatarLabel = ""
const val missingRoomNotificationRoomAvatarKey = "graphite-2"
const val missingRoomNotificationUserLabel = "用户已注销"
const val missingRoomNotificationUserAvatarLabel = ""
const val missingRoomNotificationUserAvatarKey = "graphite-2"

/**
 * Dart's factory constructors `RoomNotificationItem.invite(...)`,
 * `.applicationRequested(...)`, `.applicationReviewed(...)` and
 * `.roomEvent(...)` are companion functions of the same names.
 */
class RoomNotificationItem private constructor(
    val type: RoomNotificationItemType,
    val id: String,
    val time: DateTime,
    val pending: Boolean,
    val newItem: Boolean,
    val invite: RoomInvite? = null,
    val application: RoomApplication? = null,
    val roomEvent: RoomEventNotification? = null,
) {
    companion object {
        fun invite(invite: RoomInvite): RoomNotificationItem {
            return RoomNotificationItem(
                type = RoomNotificationItemType.invite,
                id = "invite:${invite.id}",
                time = invite.createdAt,
                pending = isActionablePendingRoomInvite(invite),
                newItem = isActionablePendingRoomInvite(invite),
                invite = invite,
            )
        }

        fun applicationRequested(application: RoomApplication): RoomNotificationItem {
            return RoomNotificationItem(
                type = RoomNotificationItemType.applicationRequested,
                id = "application-requested:${application.id}",
                time = application.createdAt,
                pending = isPendingRoomApplication(application),
                newItem = isPendingRoomApplication(application),
                application = application,
            )
        }

        fun applicationReviewed(application: RoomApplication): RoomNotificationItem {
            return RoomNotificationItem(
                type = RoomNotificationItemType.applicationReviewed,
                id = "application-reviewed:${application.id}",
                time = application.reviewedAt ?: application.updatedAt,
                pending = false,
                newItem = false,
                application = application,
            )
        }

        fun roomEvent(notification: RoomEventNotification): RoomNotificationItem {
            return RoomNotificationItem(
                type = RoomNotificationItemType.roomEvent,
                id = "room-event:${notification.id}",
                time = notification.createdAt,
                pending = false,
                newItem = notification.isUnread,
                roomEvent = notification,
            )
        }
    }
}

fun roomNotificationDeletionType(item: RoomNotificationItem): String {
    return when (item.type) {
        RoomNotificationItemType.invite -> roomNotificationDeletionInvite
        RoomNotificationItemType.applicationRequested ->
            roomNotificationDeletionApplicationRequested
        RoomNotificationItemType.applicationReviewed ->
            roomNotificationDeletionApplicationReviewed
        RoomNotificationItemType.roomEvent -> roomNotificationDeletionRoomEvent
    }
}

fun roomNotificationDeletionId(item: RoomNotificationItem): String {
    return when (item.type) {
        RoomNotificationItemType.invite -> item.invite!!.id
        RoomNotificationItemType.applicationRequested,
        RoomNotificationItemType.applicationReviewed,
        -> item.application!!.id
        RoomNotificationItemType.roomEvent -> item.roomEvent!!.id
    }
}

fun isPendingRoomInvite(invite: RoomInvite): Boolean {
    return invite.status.lowercase() == "pending"
}

fun isAcceptedRoomInvite(invite: RoomInvite): Boolean {
    return invite.status.lowercase() == "accepted"
}

fun isRejectedRoomInvite(invite: RoomInvite): Boolean {
    return invite.status.lowercase() == "rejected"
}

fun isInvalidPendingRoomInvite(invite: RoomInvite): Boolean {
    if (!isPendingRoomInvite(invite)) return false
    val reason = invite.invalidReason?.trim()
    if (reason != null && reason.isNotEmpty()) return true
    return !invite.roomExists ||
        !invite.inviterExists ||
        _isLeftRoomRole(invite.inviter.roomRole)
}

fun isActionablePendingRoomInvite(invite: RoomInvite): Boolean {
    return isPendingRoomInvite(invite) && !isInvalidPendingRoomInvite(invite)
}

fun isPendingRoomApplication(application: RoomApplication): Boolean {
    return application.status.lowercase() == "pending"
}

fun isApprovedRoomApplication(application: RoomApplication): Boolean {
    return application.status.lowercase() == "approved"
}

fun isRejectedRoomApplication(application: RoomApplication): Boolean {
    return application.status.lowercase() == "rejected"
}

fun isWithdrawnRoomApplication(application: RoomApplication): Boolean {
    return application.status.lowercase() == "withdrawn"
}

fun isReviewedRoomApplication(application: RoomApplication): Boolean {
    return isApprovedRoomApplication(application) ||
        isRejectedRoomApplication(application)
}

fun canReviewNotificationInvite(
    invite: RoomInvite,
    busyInviteId: String?,
): Boolean {
    return isPendingRoomInvite(invite) &&
        !isInvalidPendingRoomInvite(invite) &&
        busyInviteId == null
}

fun canWithdrawNotificationApplication(
    application: RoomApplication,
    busyApplicationId: String?,
): Boolean {
    return isPendingRoomApplication(application) && busyApplicationId == null
}

fun roomInviteAcceptRequiresApplication(
    invite: RoomInvite,
    roomInvites: Iterable<RoomInvite> = emptyList(),
): Boolean {
    if (invite.room.joinPolicy != "approval_required") return false
    val relatedInvites = mutableListOf(invite)
    for (other in roomInvites) {
        if (other.id != invite.id &&
            other.room.id == invite.room.id &&
            isPendingRoomInvite(other) &&
            !isInvalidPendingRoomInvite(other)
        ) {
            relatedInvites.add(other)
        }
    }
    return !relatedInvites.any { item -> _isPrivilegedInviter(item.inviter) }
}

private fun _isPrivilegedInviter(inviter: UserSummary): Boolean {
    if (inviter.isSuperuser) return true
    val role = inviter.roomRole?.trim()?.lowercase()
    return when (role) {
        "owner", "creator", "admin", "administrator", "superuser" -> true
        else -> false
    }
}

fun pendingRoomInviteCount(invites: Iterable<RoomInvite>): Int {
    return invites.count(::isActionablePendingRoomInvite)
}

fun pendingRoomNotificationCount(
    invites: Iterable<RoomInvite>,
    applications: Iterable<RoomApplication>,
    roomEvents: Iterable<RoomEventNotification> = emptyList(),
): Int {
    return pendingRoomInviteCount(invites) +
        applications.count(::isPendingRoomApplication) +
        unreadRoomEventNotificationCount(roomEvents)
}

fun unreadRoomEventNotificationCount(
    notifications: Iterable<RoomEventNotification>,
): Int {
    return notifications.count { notification -> notification.isUnread }
}

fun markUnreadRoomEventNotificationsRead(
    notifications: Iterable<RoomEventNotification>,
    readAt: DateTime,
    notificationIds: Iterable<String>? = null,
): List<RoomEventNotification> {
    val ids = notificationIds?.toSet()
    return notifications.map { notification ->
        if (notification.isUnread && (ids == null || ids.contains(notification.id))) {
            notification.markRead(readAt)
        } else {
            notification
        }
    }
}

fun roomInviteDecisionLabel(invite: RoomInvite): String {
    if (isInvalidPendingRoomInvite(invite)) return "已失效"
    if (isAcceptedRoomInvite(invite)) return "已接受"
    if (isRejectedRoomInvite(invite)) return "已拒绝"
    return ""
}

fun roomApplicationStatusLabel(application: RoomApplication): String {
    if (isApprovedRoomApplication(application)) return "已批准"
    if (isRejectedRoomApplication(application)) return "已拒绝"
    if (isWithdrawnRoomApplication(application)) return "已撤回"
    return ""
}

fun roomApplicationReviewActionLabel(application: RoomApplication): String {
    if (isApprovedRoomApplication(application)) return "批准了您的申请"
    if (isRejectedRoomApplication(application)) return "拒绝了您的申请"
    return ""
}

fun roomInviteRoleLabel(inviter: UserSummary): String {
    val role = inviter.roomRole?.trim()?.lowercase()
    return when (role) {
        "owner", "creator" -> "创建者"
        "admin", "administrator" -> "管理员"
        "member" -> "成员"
        "superuser" -> "超级用户"
        "left", "left_room", "departed" -> "已离开"
        null, "" -> "成员"
        else -> inviter.roomRole.trim()
    }
}

fun roomNotificationRoleLabel(role: String?): String {
    return when (role?.trim()?.lowercase()) {
        "owner", "creator" -> "创建者"
        "admin", "administrator" -> "管理员"
        "member" -> "成员"
        "superuser" -> "超级用户"
        "left", "left_room", "departed" -> "已离开"
        null, "" -> "成员"
        else -> role.trim()
    }
}

fun roomEventNotificationRoleActionLabel(
    notification: RoomEventNotification,
): String {
    return when (notification.type) {
        kRoomEventNotificationRolePromoted -> "晋升为"
        else -> "降职为"
    }
}

fun roomInviteTimestampLabel(value: DateTime): String {
    val local = value.toLocal()
    return "${local.year.toString().padStart(4, '0')}/" +
        "${local.month.toString().padStart(2, '0')}/" +
        "${local.day.toString().padStart(2, '0')} " +
        "${local.hour.toString().padStart(2, '0')}:" +
        local.minute.toString().padStart(2, '0')
}

fun roomNotificationCopyText(item: RoomNotificationItem): String {
    return when (item.type) {
        RoomNotificationItemType.invite -> {
            val invite = item.invite!!
            return _notificationCopyLines(
                listOf(
                    _notificationCopySentence(
                        listOf(
                            roomNotificationUserLabel(
                                invite.inviter,
                                userExists = invite.inviterExists,
                            ),
                            "邀请您加入",
                            roomNotificationRoomLabel(invite.room, roomExists = invite.roomExists),
                        ),
                    ),
                    roomInviteDecisionLabel(invite),
                ),
            )
        }
        RoomNotificationItemType.applicationRequested -> {
            val application = item.application!!
            return _notificationCopyLines(
                listOf(
                    _notificationCopySentence(
                        listOf(
                            "您已申请加入",
                            roomNotificationRoomLabel(application.room, roomExists = true),
                        ),
                    ),
                    roomApplicationStatusLabel(application),
                ),
            )
        }
        RoomNotificationItemType.applicationReviewed -> {
            val application = item.application!!
            val reviewer = application.reviewer
            val reviewerName =
                if (reviewer == null) {
                    ""
                } else {
                    roomNotificationUserLabel(
                        reviewer,
                        userExists = application.reviewerExists,
                    )
                }
            return _notificationCopyLines(
                listOf(
                    _notificationCopySentence(
                        listOf(
                            reviewerName,
                            roomApplicationReviewActionLabel(application),
                            roomNotificationRoomLabel(application.room, roomExists = true),
                        ),
                    ),
                ),
            )
        }
        RoomNotificationItemType.roomEvent -> {
            val notification = item.roomEvent!!
            val room =
                roomNotificationRoomLabel(
                    notification.room,
                    roomExists = notification.roomExists,
                )
            val actor = notification.actor
            val actorName =
                if (actor == null) {
                    ""
                } else {
                    roomNotificationUserLabel(
                        actor,
                        userExists = notification.actorExists,
                    )
                }
            val body =
                when (notification.type) {
                    kRoomEventNotificationMemberRemoved ->
                        _notificationCopySentence(
                            listOf(
                                "您被",
                                actorName,
                                "踢出了",
                                room,
                            ),
                        )
                    kRoomEventNotificationRolePromoted,
                    kRoomEventNotificationRoleDemoted,
                    ->
                        _notificationCopySentence(
                            listOf(
                                "您在",
                                room,
                                "中被",
                                actorName,
                                roomEventNotificationRoleActionLabel(notification),
                                roomNotificationRoleLabel(notification.toRole),
                            ),
                        )
                    kRoomEventNotificationCreatorTransferDemoted ->
                        _notificationCopySentence(
                            listOf(
                                "您在",
                                room,
                                "中降职为",
                                roomNotificationRoleLabel(notification.toRole),
                            ),
                        )
                    kRoomEventNotificationMentioned ->
                        _notificationCopySentence(
                            listOf(
                                "您在",
                                room,
                                "中被",
                                actorName,
                                "提及",
                            ),
                        )
                    else -> _notificationCopySentence(listOf("您在", room, "收到了房间通知"))
                }
            return _notificationCopyLines(listOf(body))
        }
    }
}

private fun _notificationCopyLines(lines: Iterable<String>): String {
    return lines
        .map { line -> line.trim() }
        .filter { line -> line.isNotEmpty() }
        .joinToString("\n")
}

private fun _notificationCopySentence(parts: Iterable<String>): String {
    return parts
        .map { part -> part.trim() }
        .filter { part -> part.isNotEmpty() }
        .joinToString(" ")
}

fun roomNotificationDateLabel(value: DateTime): String {
    val local = value.toLocal()
    return "${local.year.toString().padStart(4, '0')}/" +
        "${local.month.toString().padStart(2, '0')}/" +
        local.day.toString().padStart(2, '0')
}

fun roomNotificationRoomLabel(room: PublicRoom, roomExists: Boolean): String {
    return if (roomExists) room.name else missingRoomNotificationRoomLabel
}

fun roomNotificationRoomAvatarLabel(
    room: PublicRoom,
    roomExists: Boolean,
): String {
    return if (roomExists) room.name else missingRoomNotificationRoomAvatarLabel
}

fun roomNotificationRoomAvatarUrl(
    room: PublicRoom,
    roomExists: Boolean,
): String? {
    return if (roomExists) room.avatarUrl else null
}

fun roomNotificationRoomAvatarKey(
    room: PublicRoom,
    roomExists: Boolean,
): String {
    return if (roomExists) {
        room.defaultAvatarKey
    } else {
        missingRoomNotificationRoomAvatarKey
    }
}

@Suppress("UNUSED_PARAMETER")
fun roomNotificationRoomCardEnabled(roomExists: Boolean): Boolean {
    return true
}

fun roomNotificationUserLabel(user: UserSummary, userExists: Boolean): String {
    if (!userExists) return missingRoomNotificationUserLabel
    val roomName = user.roomDisplayName?.trim()
    if (roomName != null && roomName.isNotEmpty()) return roomName
    val displayName = user.displayName.trim()
    if (displayName.isNotEmpty()) return displayName
    return user.username
}

fun roomNotificationUserAvatarLabel(
    user: UserSummary,
    userExists: Boolean,
): String {
    return if (userExists) {
        userAvatarLabel(user)
    } else {
        missingRoomNotificationUserAvatarLabel
    }
}

fun roomNotificationUserAvatarUrl(
    user: UserSummary,
    userExists: Boolean,
): String? {
    return if (userExists) user.avatarUrl else null
}

fun roomNotificationUserAvatarKey(
    user: UserSummary,
    userExists: Boolean,
): String {
    return if (userExists) {
        user.defaultAvatarKey
    } else {
        missingRoomNotificationUserAvatarKey
    }
}

fun roomNotificationsForView(
    invites: Iterable<RoomInvite>,
    applications: Iterable<RoomApplication>,
    roomEvents: Iterable<RoomEventNotification> = emptyList(),
    query: String,
    filter: RoomNotificationFilter,
    dateRange: RoomNotificationDateRange? = null,
): List<RoomNotificationItem> {
    val normalizedQuery = query.trim().lowercase()
    val items = mutableListOf<RoomNotificationItem>()

    if (filter == RoomNotificationFilter.all ||
        filter == RoomNotificationFilter.invites
    ) {
        for (invite in invites) {
            val item = RoomNotificationItem.invite(invite)
            if (_matchesRoomInvite(invite, normalizedQuery) &&
                _isInRoomNotificationDateRange(item, dateRange)
            ) {
                items.add(item)
            }
        }
    }

    if (filter == RoomNotificationFilter.all ||
        filter == RoomNotificationFilter.applications
    ) {
        for (application in applications) {
            if (!_matchesRoomApplication(application, normalizedQuery)) {
                continue
            }
            val requested = RoomNotificationItem.applicationRequested(application)
            if (!application.requestNotificationDeleted &&
                _isInRoomNotificationDateRange(requested, dateRange)
            ) {
                items.add(requested)
            }
            if (isReviewedRoomApplication(application) &&
                application.reviewedAt != null &&
                application.reviewer != null &&
                !application.reviewNotificationDeleted
            ) {
                val reviewed = RoomNotificationItem.applicationReviewed(application)
                if (_isInRoomNotificationDateRange(reviewed, dateRange)) {
                    items.add(reviewed)
                }
            }
        }
    }

    if (filter == RoomNotificationFilter.all ||
        filter == RoomNotificationFilter.roomNotifications
    ) {
        for (notification in roomEvents) {
            val item = RoomNotificationItem.roomEvent(notification)
            if (_matchesRoomEventNotification(notification, normalizedQuery) &&
                _isInRoomNotificationDateRange(item, dateRange)
            ) {
                items.add(item)
            }
        }
    }

    items.sortWith(::compareRoomNotificationItems)
    return items
}

fun roomInviteNotificationsForView(
    invites: Iterable<RoomInvite>,
    query: String,
    filter: RoomNotificationFilter,
    dateRange: RoomNotificationDateRange? = null,
): List<RoomInvite> {
    if (filter != RoomNotificationFilter.all &&
        filter != RoomNotificationFilter.invites
    ) {
        return emptyList()
    }

    val normalizedQuery = query.trim().lowercase()
    val filtered =
        invites.filter { invite ->
            _matchesRoomInvite(invite, normalizedQuery) &&
                (dateRange?.includes(invite.createdAt) ?: true)
        }.toMutableList()
    filtered.sortWith(::compareRoomInviteNotifications)
    return filtered
}

fun compareRoomNotificationItems(
    a: RoomNotificationItem,
    b: RoomNotificationItem,
): Int {
    val pendingRank = _itemPendingRank(a).compareTo(_itemPendingRank(b))
    if (pendingRank != 0) return pendingRank
    val timeRank = b.time.compareTo(a.time)
    if (timeRank != 0) return timeRank
    return b.id.compareTo(a.id)
}

fun compareRoomInviteNotifications(a: RoomInvite, b: RoomInvite): Int {
    val pendingRank = _invitePendingRank(a).compareTo(_invitePendingRank(b))
    if (pendingRank != 0) return pendingRank
    val createdRank = b.createdAt.compareTo(a.createdAt)
    if (createdRank != 0) return createdRank
    return b.id.compareTo(a.id)
}

private fun _matchesRoomInvite(invite: RoomInvite, normalizedQuery: String): Boolean {
    if (normalizedQuery.isEmpty()) return true
    return _roomInviteSearchText(invite).contains(normalizedQuery)
}

private fun _matchesRoomApplication(
    application: RoomApplication,
    normalizedQuery: String,
): Boolean {
    if (normalizedQuery.isEmpty()) return true
    return _roomApplicationSearchText(application).contains(normalizedQuery)
}

private fun _matchesRoomEventNotification(
    notification: RoomEventNotification,
    normalizedQuery: String,
): Boolean {
    if (normalizedQuery.isEmpty()) return true
    return _roomEventNotificationSearchText(
        notification,
    ).contains(normalizedQuery)
}

private fun _isInRoomNotificationDateRange(
    item: RoomNotificationItem,
    dateRange: RoomNotificationDateRange?,
): Boolean {
    return dateRange?.includes(item.time) ?: true
}

private fun _itemPendingRank(item: RoomNotificationItem): Int {
    return if (item.pending) 0 else 1
}

private fun _invitePendingRank(invite: RoomInvite): Int {
    return if (isActionablePendingRoomInvite(invite)) 0 else 1
}

private fun _roomInviteSearchText(invite: RoomInvite): String {
    val values = mutableListOf<String>()
    if (invite.roomExists) {
        _addRoomSearchValues(values, invite.room)
    }
    if (invite.inviterExists) {
        _addUserSearchValues(values, invite.inviter)
    }
    _addSearchValue(values, invite.id)
    _addSearchValue(values, invite.status)
    if (invite.roomExists) {
        _addSearchValue(
            values,
            if (invite.room.joined) "已加入 joined" else "未加入 not joined",
        )
    }
    if (invite.inviterExists) {
        _addSearchValue(values, roomInviteRoleLabel(invite.inviter))
    }
    _addSearchValue(
        values,
        if (invite.inviterExists) {
            null
        } else {
            "$missingRoomNotificationUserLabel 已注销 user deactivated deleted"
        },
    )
    _addSearchValue(values, invite.invalidReason)
    _addSearchValue(
        values,
        if (isInvalidPendingRoomInvite(invite)) "已失效 invalid" else null,
    )
    _addSearchValue(
        values,
        if (invite.roomExists) {
            null
        } else {
            "$missingRoomNotificationRoomLabel 已删除 room deleted"
        },
    )
    _addSearchValue(values, roomInviteDecisionLabel(invite))
    return values.joinToString("\n")
}

private fun _isLeftRoomRole(value: String?): Boolean {
    val role = value?.trim()?.lowercase()
    return role == "left" || role == "left_room" || role == "departed"
}

private fun _roomApplicationSearchText(application: RoomApplication): String {
    val values = mutableListOf<String>()
    _addRoomSearchValues(values, application.room)
    _addSearchValue(values, application.id)
    _addSearchValue(values, application.status)
    _addSearchValue(values, application.reason)
    _addSearchValue(values, roomApplicationStatusLabel(application))
    _addSearchValue(values, roomApplicationReviewActionLabel(application))
    val reviewer = application.reviewer
    if (reviewer != null) {
        if (application.reviewerExists) {
            _addUserSearchValues(values, reviewer)
            _addSearchValue(values, roomInviteRoleLabel(reviewer))
        }
        _addSearchValue(
            values,
            if (application.reviewerExists) {
                null
            } else {
                "$missingRoomNotificationUserLabel 已注销 user deactivated deleted"
            },
        )
    }
    return values.joinToString("\n")
}

private fun _roomEventNotificationSearchText(notification: RoomEventNotification): String {
    val values = mutableListOf<String>()
    if (notification.roomExists) {
        _addRoomSearchValues(values, notification.room)
    }
    _addSearchValue(values, notification.id)
    _addSearchValue(values, notification.type)
    _addSearchValue(values, roomNotificationRoleLabel(notification.fromRole))
    _addSearchValue(values, roomNotificationRoleLabel(notification.toRole))
    _addSearchValue(
        values,
        if (notification.roomExists) {
            null
        } else {
            "$missingRoomNotificationRoomLabel 已删除 room deleted"
        },
    )
    val actor = notification.actor
    if (actor != null) {
        if (notification.actorExists) {
            _addUserSearchValues(values, actor)
            _addSearchValue(values, roomInviteRoleLabel(actor))
        }
        _addSearchValue(
            values,
            if (notification.actorExists) {
                null
            } else {
                "$missingRoomNotificationUserLabel 已注销 user deactivated deleted"
            },
        )
    }
    when (notification.type) {
        kRoomEventNotificationMemberRemoved ->
            _addSearchValue(values, "踢出了 踢出房间 removed kicked")
        kRoomEventNotificationRolePromoted ->
            _addSearchValue(values, "晋升为 promotion promoted")
        kRoomEventNotificationRoleDemoted ->
            _addSearchValue(values, "降职为 demotion demoted")
        kRoomEventNotificationCreatorTransferDemoted ->
            _addSearchValue(values, "转让创建者 降职为 creator transferred")
        kRoomEventNotificationMentioned ->
            _addSearchValue(values, "提及 @ mentioned")
    }
    return values.joinToString("\n")
}

private fun _addRoomSearchValues(values: MutableList<String>, room: PublicRoom) {
    _addSearchValue(values, room.id)
    _addSearchValue(values, room.rid)
    _addSearchValue(values, room.name)
    _addSearchValue(values, room.visibility)
    _addSearchValue(values, room.joinPolicy)
    _addSearchValue(values, room.memberCount)
    _addSearchValue(values, room.onlineMemberCount)
    _addSearchValue(values, room.liveParticipantCount)
    _addSearchValue(values, room.joinState)
}

private fun _addUserSearchValues(values: MutableList<String>, user: UserSummary) {
    _addSearchValue(values, user.id)
    _addSearchValue(values, user.uid)
    _addSearchValue(values, user.username)
    _addSearchValue(values, user.displayName)
    _addSearchValue(values, user.defaultAvatarKey)
    _addSearchValue(values, user.bio)
    _addSearchValue(values, user.gender)
    _addSearchValue(values, user.email)
    _addSearchValue(values, user.phoneNumber)
    _addSearchValue(values, user.roomDisplayName)
    _addSearchValue(values, user.roomRole)
    _addSearchValue(values, if (user.isSuperuser) "超级用户 superuser" else null)
    _addSearchValue(
        values,
        when (user.isOnline) {
            null -> null
            true -> "在线 online"
            false -> "离线 offline"
        },
    )
    for (commonRoom in user.commonRooms) {
        _addSearchValue(values, commonRoom.id)
        _addSearchValue(values, commonRoom.rid)
        _addSearchValue(values, commonRoom.name)
        _addSearchValue(values, commonRoom.visibility)
        _addSearchValue(values, commonRoom.remarkName)
        _addSearchValue(values, commonRoom.defaultAvatarKey)
        _addSearchValue(values, commonRoom.roomDisplayName)
        _addSearchValue(values, commonRoom.roomRole)
    }
}

private fun _addSearchValue(values: MutableList<String>, value: Any?) {
    val text = value?.toString()?.trim()
    if (text != null && text.isNotEmpty()) values.add(text.lowercase())
}

private fun _localDateOnly(value: DateTime): DateTime {
    val local = value.toLocal()
    return DateTime(local.year, local.month, local.day)
}
