package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomApplication
import com.gangchat.client.protocol.RoomEventNotification
import com.gangchat.client.protocol.RoomInvite
import com.gangchat.client.protocol.UserCommonRoom
import com.gangchat.client.protocol.UserSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomNotificationsTest {
    @Test
    fun `room invite notifications sort pending first then newest first`() {
        val accepted =
            _invite(
                "accepted_old",
                status = "accepted",
                createdAt = DateTime.utc(2026, 6, 1, 8),
            )
        val pendingOld =
            _invite(
                "pending_old",
                status = "pending",
                createdAt = DateTime.utc(2026, 6, 2, 8),
            )
        val rejectedNew =
            _invite(
                "rejected_new",
                status = "rejected",
                createdAt = DateTime.utc(2026, 6, 3, 8),
            )
        val invalidNew =
            _invite(
                "invalid_new",
                status = "pending",
                createdAt = DateTime.utc(2026, 6, 5, 8),
                invalidReason = "inviter_left",
                inviter = _user("left_inviter", roomRole = "left"),
            )
        val pendingNew =
            _invite(
                "pending_new",
                status = "pending",
                createdAt = DateTime.utc(2026, 6, 4, 8),
            )

        val visible =
            roomInviteNotificationsForView(
                invites = listOf(accepted, pendingOld, rejectedNew, invalidNew, pendingNew),
                query = "",
                filter = RoomNotificationFilter.all,
            )

        assertEquals(
            listOf(
                "pending_new",
                "pending_old",
                "invalid_new",
                "rejected_new",
                "accepted_old",
            ),
            visible.map { invite -> invite.id },
        )
        assertEquals(2, pendingRoomInviteCount(visible))
    }

    @Test
    fun `room invite notification search includes room and inviter fields`() {
        val invite =
            _invite(
                "invite_1",
                roomName = "Design Lab",
                roomRid = "R-2026",
                inviter =
                    _user(
                        "user_1",
                        uid = "100001",
                        username = "mira",
                        displayName = "Mira Chen",
                        roomRole = "admin",
                        roomDisplayName = "Mira in Design",
                        commonRooms =
                            listOf(
                                UserCommonRoom(
                                    id = "shared_room",
                                    rid = "S-1",
                                    name = "Shared Room",
                                    remarkName = "Shared Remark",
                                ),
                            ),
                    ),
            )

        assertEquals(
            listOf(invite),
            roomInviteNotificationsForView(
                invites = listOf(invite),
                query = "design lab",
                filter = RoomNotificationFilter.invites,
            ),
        )
        assertEquals(
            listOf(invite),
            roomInviteNotificationsForView(
                invites = listOf(invite),
                query = "管理员",
                filter = RoomNotificationFilter.all,
            ),
        )
        assertEquals(
            listOf(invite),
            roomInviteNotificationsForView(
                invites = listOf(invite),
                query = "Shared Remark",
                filter = RoomNotificationFilter.all,
            ),
        )
        assertTrue(
            roomInviteNotificationsForView(
                invites = listOf(invite),
                query = "missing",
                filter = RoomNotificationFilter.all,
            ).isEmpty(),
        )
        assertTrue(
            roomInviteNotificationsForView(
                invites = listOf(invite),
                query = "",
                filter = RoomNotificationFilter.roomNotifications,
            ).isEmpty(),
        )
    }

    @Test
    fun `notification search does not match displayed timestamps`() {
        val createdAt = DateTime(2026, 6, 5, 12)
        val visible =
            roomNotificationsForView(
                invites = listOf(_invite("dated_invite", createdAt = createdAt)),
                applications = listOf(_application("dated_application", createdAt = createdAt)),
                roomEvents = listOf(_roomEvent("dated_room_event", createdAt = createdAt)),
                query = "2026/06/05 12:00",
                filter = RoomNotificationFilter.all,
            )

        assertTrue(visible.isEmpty())
    }

    @Test
    fun `notification date range composes with text and type filters`() {
        val application =
            _application(
                "application_in_range",
                status = "approved",
                roomName = "Date Room",
                createdAt = DateTime(2026, 6, 5, 23, 59),
                reviewedAt = DateTime(2026, 6, 7, 8),
                updatedAt = DateTime(2026, 6, 7, 8),
                reviewer = _user("date_reviewer"),
            )
        val range =
            RoomNotificationDateRange(
                startDate = DateTime(2026, 6, 5),
                endDate = DateTime(2026, 6, 6),
            )

        val visible =
            roomNotificationsForView(
                invites =
                    listOf(
                        _invite("invite_before_range", createdAt = DateTime(2026, 6, 4, 23, 59)),
                    ),
                applications = listOf(application),
                roomEvents =
                    listOf(
                        _roomEvent(
                            "room_event_on_end_date",
                            roomName = "Date Room",
                            createdAt = DateTime(2026, 6, 6, 23, 59),
                        ),
                    ),
                query = "Date Room",
                filter = RoomNotificationFilter.all,
                dateRange = range,
            )
        val applicationsOnly =
            roomNotificationsForView(
                invites = emptyList(),
                applications = listOf(application),
                roomEvents = emptyList(),
                query = "Date Room",
                filter = RoomNotificationFilter.applications,
                dateRange = range,
            )

        assertEquals(
            listOf(
                "room-event:room_event_on_end_date",
                "application-requested:application_in_range",
            ),
            visible.map { item -> item.id },
        )
        assertEquals(
            listOf(
                "application-requested:application_in_range",
            ),
            applicationsOnly.map { item -> item.id },
        )
    }

    @Test
    fun `default notification date range uses account date through today`() {
        val range =
            RoomNotificationDateRange.defaultFor(
                accountCreatedAt = DateTime(2025, 12, 3, 18, 30),
                today = DateTime(2026, 7, 10, 23, 59),
            )

        assertEquals(DateTime(2025, 12, 3), range.startDate)
        assertEquals(DateTime(2026, 7, 10), range.endDate)
        assertTrue(range.includes(DateTime(2025, 12, 3)))
        assertTrue(range.includes(DateTime(2026, 7, 10, 23, 59)))
        assertFalse(range.includes(DateTime(2025, 12, 2, 23, 59)))
    }

    @Test
    fun `room invite notification display helpers format labels`() {
        val pending = _invite("pending", status = "pending")
        val invalid =
            _invite(
                "invalid",
                status = "pending",
                invalidReason = "inviter_left",
                inviter = _user("left_inviter", roomRole = "left"),
            )
        val deletedInviterWithoutReason =
            _invite(
                "deleted_inviter_without_reason",
                status = "pending",
                inviterExists = false,
            )
        val accepted = _invite("accepted", status = "accepted")
        val rejected = _invite("rejected", status = "rejected")

        assertTrue(
            canReviewNotificationInvite(invite = pending, busyInviteId = null),
        )
        assertFalse(
            canReviewNotificationInvite(invite = pending, busyInviteId = "pending"),
        )
        assertFalse(
            canReviewNotificationInvite(invite = accepted, busyInviteId = null),
        )
        assertFalse(
            canReviewNotificationInvite(invite = invalid, busyInviteId = null),
        )
        assertFalse(
            canReviewNotificationInvite(
                invite = deletedInviterWithoutReason,
                busyInviteId = null,
            ),
        )
        assertTrue(isInvalidPendingRoomInvite(invalid))
        assertTrue(isInvalidPendingRoomInvite(deletedInviterWithoutReason))
        assertTrue(isActionablePendingRoomInvite(pending))
        assertFalse(isActionablePendingRoomInvite(invalid))
        assertFalse(isActionablePendingRoomInvite(deletedInviterWithoutReason))
        assertEquals("已失效", roomInviteDecisionLabel(invalid))
        assertEquals("已失效", roomInviteDecisionLabel(deletedInviterWithoutReason))
        assertEquals("已接受", roomInviteDecisionLabel(accepted))
        assertEquals("已拒绝", roomInviteDecisionLabel(rejected))
        assertEquals("创建者", roomInviteRoleLabel(_user("owner", roomRole = "owner")))
        assertEquals("管理员", roomInviteRoleLabel(_user("admin", roomRole = "admin")))
        assertEquals("成员", roomInviteRoleLabel(_user("member", roomRole = "member")))
        assertEquals("已离开", roomInviteRoleLabel(_user("left", roomRole = "left")))
        assertEquals(1, pendingRoomInviteCount(listOf(pending, invalid)))
        assertEquals(1, pendingRoomInviteCount(listOf(pending, deletedInviterWithoutReason)))
        assertEquals(
            "2026/06/09 08:05",
            roomInviteTimestampLabel(DateTime(2026, 6, 9, 8, 5)),
        )
    }

    @Test
    fun `deleted notification rooms display as deleted room targets`() {
        val deleted =
            _invite(
                "deleted",
                roomName = "Deleted Room",
                roomExists = false,
                invalidReason = "room_missing",
            )

        assertEquals(
            "房间已删除",
            roomNotificationRoomLabel(deleted.room, roomExists = deleted.roomExists),
        )
        assertEquals(
            "",
            roomNotificationRoomAvatarLabel(
                deleted.room,
                roomExists = deleted.roomExists,
            ),
        )
        assertNull(
            roomNotificationRoomAvatarUrl(
                deleted.room,
                roomExists = deleted.roomExists,
            ),
        )
        assertEquals(
            "graphite-2",
            roomNotificationRoomAvatarKey(
                deleted.room,
                roomExists = deleted.roomExists,
            ),
        )
        assertTrue(
            roomNotificationRoomCardEnabled(roomExists = deleted.roomExists),
        )
        assertEquals(
            listOf(deleted),
            roomInviteNotificationsForView(
                invites = listOf(deleted),
                query = "房间已删除",
                filter = RoomNotificationFilter.all,
            ),
        )
        assertTrue(
            roomInviteNotificationsForView(
                invites = listOf(deleted),
                query = "不存在",
                filter = RoomNotificationFilter.all,
            ).isEmpty(),
        )
        assertTrue(
            roomInviteNotificationsForView(
                invites = listOf(deleted),
                query = "Invite Room",
                filter = RoomNotificationFilter.all,
            ).isEmpty(),
        )
        assertTrue(
            roomInviteNotificationsForView(
                invites = listOf(deleted),
                query = "user_inviter_deleted_room",
                filter = RoomNotificationFilter.all,
            ).isEmpty(),
        )
    }

    @Test
    fun `deleted notification users display as deactivated user targets`() {
        val deletedInviter =
            _invite(
                "deleted_inviter",
                inviter =
                    _user(
                        "deleted_inviter",
                        displayName = "Deleted Inviter",
                        roomRole = "left",
                    ),
                inviterExists = false,
                invalidReason = "inviter_deleted",
            )
        val deletedReviewer =
            _application(
                "deleted_reviewer",
                status = "approved",
                reviewedAt = DateTime.utc(2026, 6, 7, 8),
                reviewer =
                    _user(
                        "deleted_reviewer",
                        displayName = "Deleted Reviewer",
                        roomRole = "owner",
                    ),
                reviewerExists = false,
            )

        assertEquals(
            "用户已注销",
            roomNotificationUserLabel(
                deletedInviter.inviter,
                userExists = deletedInviter.inviterExists,
            ),
        )
        assertEquals(
            "",
            roomNotificationUserAvatarLabel(
                deletedInviter.inviter,
                userExists = deletedInviter.inviterExists,
            ),
        )
        assertNull(
            roomNotificationUserAvatarUrl(
                deletedInviter.inviter,
                userExists = deletedInviter.inviterExists,
            ),
        )
        assertEquals(
            "graphite-2",
            roomNotificationUserAvatarKey(
                deletedInviter.inviter,
                userExists = deletedInviter.inviterExists,
            ),
        )
        assertTrue(
            roomInviteNotificationsForView(
                invites = listOf(deletedInviter),
                query = "Deleted Inviter",
                filter = RoomNotificationFilter.all,
            ).isEmpty(),
        )
        assertEquals(
            listOf(deletedInviter),
            roomInviteNotificationsForView(
                invites = listOf(deletedInviter),
                query = "用户已注销",
                filter = RoomNotificationFilter.all,
            ),
        )
        assertEquals(
            listOf(deletedInviter),
            roomInviteNotificationsForView(
                invites = listOf(deletedInviter),
                query = "用户已注销",
                filter = RoomNotificationFilter.all,
            ),
        )
        assertEquals(
            listOf(
                "application-reviewed:deleted_reviewer",
                "application-requested:deleted_reviewer",
            ),
            roomNotificationsForView(
                invites = emptyList(),
                applications = listOf(deletedReviewer),
                query = "用户已注销",
                filter = RoomNotificationFilter.all,
            ).map { item -> item.id },
        )
    }

    @Test
    fun `room notifications combine invites and applications for view`() {
        val pendingInvite =
            _invite(
                "pending_invite",
                status = "pending",
                createdAt = DateTime.utc(2026, 6, 5, 8),
            )
        val pendingApplication =
            _application(
                "pending_application",
                status = "pending",
                createdAt = DateTime.utc(2026, 6, 6, 8),
            )
        val approvedApplication =
            _application(
                "approved_application",
                status = "approved",
                createdAt = DateTime.utc(2026, 6, 1, 8),
                updatedAt = DateTime.utc(2026, 6, 7, 8),
                reviewedAt = DateTime.utc(2026, 6, 7, 8),
                reviewer =
                    _user(
                        "reviewer_1",
                        displayName = "Robin Reviewer",
                        roomRole = "admin",
                    ),
            )

        val visible =
            roomNotificationsForView(
                invites = listOf(pendingInvite),
                applications = listOf(approvedApplication, pendingApplication),
                query = "",
                filter = RoomNotificationFilter.all,
            )

        assertEquals(
            listOf(
                "application-requested:pending_application",
                "invite:pending_invite",
                "application-reviewed:approved_application",
                "application-requested:approved_application",
            ),
            visible.map { item -> item.id },
        )
        assertEquals(
            3,
            pendingRoomNotificationCount(
                invites = listOf(pendingInvite),
                applications = listOf(pendingApplication, approvedApplication),
                roomEvents =
                    listOf(
                        _roomEvent("unread_room_event"),
                        _roomEvent("read_room_event", readAt = DateTime.utc(2026, 6, 8, 9)),
                    ),
            ),
        )
    }

    @Test
    fun `room event notifications can be marked read locally`() {
        val alreadyReadAt = DateTime.utc(2026, 6, 8, 9)
        val readAt = DateTime.utc(2026, 6, 8, 10)
        val updated =
            markUnreadRoomEventNotificationsRead(
                notifications =
                    listOf(
                        _roomEvent("unread_room_event"),
                        _roomEvent("read_room_event", readAt = alreadyReadAt),
                    ),
                readAt = readAt,
            )

        assertFalse(updated.first().isUnread)
        assertEquals(readAt, updated.first().readAt)
        assertFalse(updated.last().isUnread)
        assertEquals(alreadyReadAt, updated.last().readAt)
    }

    @Test
    fun `room event notification visual read can be limited to known ids`() {
        val readAt = DateTime.utc(2026, 6, 8, 10)
        val updated =
            markUnreadRoomEventNotificationsRead(
                notifications =
                    listOf(
                        _roomEvent("old_room_event"),
                        _roomEvent("fresh_room_event"),
                    ),
                readAt = readAt,
                notificationIds = setOf("old_room_event"),
            )

        assertFalse(updated.first().isUnread)
        assertEquals(readAt, updated.first().readAt)
        assertTrue(updated.last().isUnread)
        assertNull(updated.last().readAt)
    }

    @Test
    fun `room event notifications filter and search room events`() {
        val promoted =
            _roomEvent(
                "promoted",
                type = kRoomEventNotificationRolePromoted,
                roomName = "Launch Room",
                actor =
                    _user(
                        "actor_promoted",
                        displayName = "Morgan Admin",
                        roomRole = "owner",
                    ),
                toRole = "admin",
            )
        val removed =
            _roomEvent(
                "removed",
                type = kRoomEventNotificationMemberRemoved,
                createdAt = DateTime.utc(2026, 6, 8, 8),
            )
        val mentioned =
            _roomEvent(
                "mentioned",
                type = kRoomEventNotificationMentioned,
                roomName = "Mention Room",
                actor =
                    _user(
                        "actor_mentioned",
                        displayName = "Casey Admin",
                        roomRole = "admin",
                    ),
                messageId = "msg-mentioned",
                messagePreview = "@Morgan Admin hello",
            )

        val roomEventsOnly =
            roomNotificationsForView(
                invites = listOf(_invite("invite_1")),
                applications = listOf(_application("application_1")),
                roomEvents = listOf(promoted, removed),
                query = "",
                filter = RoomNotificationFilter.roomNotifications,
            )
        assertEquals(
            listOf(
                "room-event:removed",
                "room-event:promoted",
            ),
            roomEventsOnly.map { item -> item.id },
        )
        assertTrue(roomEventsOnly.all { item -> item.invite == null })
        assertTrue(roomEventsOnly.all { item -> item.application == null })
        assertTrue(roomEventsOnly.first().newItem)

        assertSame(
            promoted,
            roomNotificationsForView(
                invites = emptyList(),
                applications = emptyList(),
                roomEvents = listOf(promoted),
                query = "Morgan Admin",
                filter = RoomNotificationFilter.all,
            ).single().roomEvent,
        )
        assertSame(
            promoted,
            roomNotificationsForView(
                invites = emptyList(),
                applications = emptyList(),
                roomEvents = listOf(promoted),
                query = "管理员",
                filter = RoomNotificationFilter.all,
            ).single().roomEvent,
        )
        assertSame(
            mentioned,
            roomNotificationsForView(
                invites = emptyList(),
                applications = emptyList(),
                roomEvents = listOf(mentioned),
                query = "提及",
                filter = RoomNotificationFilter.all,
            ).single().roomEvent,
        )
        assertTrue(
            roomNotificationsForView(
                invites = emptyList(),
                applications = emptyList(),
                roomEvents = listOf(promoted),
                query = "missing",
                filter = RoomNotificationFilter.all,
            ).isEmpty(),
        )

        val readEvent = _roomEvent("read", readAt = DateTime.utc(2026, 6, 8, 9))
        assertFalse(
            roomNotificationsForView(
                invites = emptyList(),
                applications = emptyList(),
                roomEvents = listOf(readEvent),
                query = "",
                filter = RoomNotificationFilter.all,
            ).single().newItem,
        )
    }

    @Test
    fun `room application notifications filter and search reviewer fields`() {
        val application =
            _application(
                "application_1",
                status = "rejected",
                roomName = "Launch Room",
                reviewedAt = DateTime.utc(2026, 6, 7, 8),
                reviewer =
                    _user(
                        "reviewer_1",
                        username = "robin",
                        displayName = "Robin Reviewer",
                        roomRole = "owner",
                        roomDisplayName = "Room Robin",
                    ),
            )

        val applicationsOnly =
            roomNotificationsForView(
                invites = listOf(_invite("invite_1")),
                applications = listOf(application),
                query = "",
                filter = RoomNotificationFilter.applications,
            )
        assertEquals(
            listOf(
                RoomNotificationItemType.applicationReviewed,
                RoomNotificationItemType.applicationRequested,
            ),
            applicationsOnly.map { item -> item.type },
        )

        assertTrue(
            roomNotificationsForView(
                invites = emptyList(),
                applications = listOf(application),
                query = "Room Robin",
                filter = RoomNotificationFilter.all,
            ).isNotEmpty(),
        )
        assertTrue(
            roomNotificationsForView(
                invites = emptyList(),
                applications = listOf(application),
                query = "missing",
                filter = RoomNotificationFilter.all,
            ).isEmpty(),
        )
    }

    @Test
    fun `room application notification display helpers format labels`() {
        val pending = _application("pending", status = "pending")
        val approved = _application("approved", status = "approved")
        val rejected = _application("rejected", status = "rejected")
        val withdrawn = _application("withdrawn", status = "withdrawn")

        assertTrue(
            canWithdrawNotificationApplication(
                application = pending,
                busyApplicationId = null,
            ),
        )
        assertFalse(
            canWithdrawNotificationApplication(
                application = pending,
                busyApplicationId = "pending",
            ),
        )
        assertFalse(
            canWithdrawNotificationApplication(
                application = approved,
                busyApplicationId = null,
            ),
        )
        assertEquals("已批准", roomApplicationStatusLabel(approved))
        assertEquals("已拒绝", roomApplicationStatusLabel(rejected))
        assertEquals("已撤回", roomApplicationStatusLabel(withdrawn))
        assertEquals("批准了您的申请", roomApplicationReviewActionLabel(approved))
        assertEquals("拒绝了您的申请", roomApplicationReviewActionLabel(rejected))
    }

    @Test
    fun `deleted application notification variants stay independently hidden`() {
        val application =
            _application(
                "application_deleted_variants",
                status = "approved",
                reviewedAt = DateTime.utc(2026, 6, 8, 9),
                reviewer = _user("reviewer_deleted_variants"),
                requestNotificationDeleted = true,
            )

        assertEquals(
            listOf(RoomNotificationItemType.applicationReviewed),
            roomNotificationsForView(
                invites = emptyList(),
                applications = listOf(application),
                query = "",
                filter = RoomNotificationFilter.applications,
            ).map { item -> item.type },
        )

        val reviewDeleted =
            _application(
                "application_review_deleted",
                status = "approved",
                reviewedAt = DateTime.utc(2026, 6, 8, 9),
                reviewer = _user("reviewer_review_deleted"),
                reviewNotificationDeleted = true,
            )
        assertEquals(
            listOf(RoomNotificationItemType.applicationRequested),
            roomNotificationsForView(
                invites = emptyList(),
                applications = listOf(reviewDeleted),
                query = "",
                filter = RoomNotificationFilter.applications,
            ).map { item -> item.type },
        )
    }

    @Test
    fun `notification copy text omits user identity badges`() {
        val invite =
            _invite(
                "copy_invite",
                status = "accepted",
                roomName = "Copy Room",
                inviter =
                    _user(
                        "copy_inviter",
                        displayName = "Alex Inviter",
                        roomRole = "admin",
                    ),
            )
        val inviteItem = RoomNotificationItem.invite(invite)

        assertEquals("invite", roomNotificationDeletionType(inviteItem))
        assertEquals(invite.id, roomNotificationDeletionId(inviteItem))
        assertEquals(
            "Alex Inviter 邀请您加入 Copy Room\n已接受",
            roomNotificationCopyText(inviteItem),
        )
        assertFalse(roomNotificationCopyText(inviteItem).contains("2026/06/05"))
        assertFalse(roomNotificationCopyText(inviteItem).contains("管理员"))

        val eventItem =
            RoomNotificationItem.roomEvent(
                _roomEvent(
                    "copy_event",
                    type = kRoomEventNotificationRolePromoted,
                    roomName = "Event Copy Room",
                    actor =
                        _user(
                            "copy_actor",
                            displayName = "Morgan Actor",
                            roomRole = "owner",
                        ),
                    toRole = "admin",
                ),
            )
        assertEquals("room_event", roomNotificationDeletionType(eventItem))
        assertEquals(
            "您在 Event Copy Room 中被 Morgan Actor 晋升为 管理员",
            roomNotificationCopyText(eventItem),
        )
        assertFalse(roomNotificationCopyText(eventItem).contains("2026/06/05"))
        assertFalse(roomNotificationCopyText(eventItem).contains("创建者"))
    }

    @Test
    fun `room invite application gate follows inviter role`() {
        assertTrue(
            roomInviteAcceptRequiresApplication(
                _invite(
                    "member",
                    joinPolicy = "approval_required",
                    inviter = _user("member", roomRole = "member"),
                ),
            ),
        )
        assertFalse(
            roomInviteAcceptRequiresApplication(
                _invite(
                    "admin",
                    joinPolicy = "approval_required",
                    inviter = _user("admin", roomRole = "admin"),
                ),
            ),
        )
        val memberInvite =
            _invite(
                "member-related",
                roomId = "room_shared",
                joinPolicy = "approval_required",
                inviter = _user("member", roomRole = "member"),
            )
        val adminInvite =
            _invite(
                "admin-related",
                roomId = "room_shared",
                joinPolicy = "approval_required",
                inviter = _user("admin", roomRole = "admin"),
            )
        assertFalse(
            roomInviteAcceptRequiresApplication(
                memberInvite,
                roomInvites = listOf(memberInvite, adminInvite),
            ),
        )
        assertFalse(
            roomInviteAcceptRequiresApplication(
                _invite(
                    "super",
                    joinPolicy = "approval_required",
                    inviter = _user("super", roomRole = "superuser", isSuperuser = true),
                ),
            ),
        )
        assertFalse(
            roomInviteAcceptRequiresApplication(_invite("open", joinPolicy = "open")),
        )
    }

    private fun _invite(
        id: String,
        status: String = "pending",
        createdAt: DateTime? = null,
        roomName: String = "Invite Room",
        roomRid: String = "R-1",
        roomId: String? = null,
        joinPolicy: String = "closed",
        inviter: UserSummary? = null,
        roomExists: Boolean = true,
        inviterExists: Boolean = true,
        invalidReason: String? = null,
    ): RoomInvite {
        return RoomInvite(
            id = id,
            status = status,
            room =
                PublicRoom(
                    id = roomId ?: "room_$id",
                    rid = roomRid,
                    name = roomName,
                    avatarUrl = null,
                    defaultAvatarKey = "room-1",
                    visibility = "private",
                    joinPolicy = joinPolicy,
                    memberCount = 3,
                    onlineMemberCount = 1,
                    liveParticipantCount = 0,
                    joined = false,
                    joinState = "none",
                ),
            inviter = inviter ?: _user("inviter_$id", roomRole = "owner"),
            createdAt = createdAt ?: DateTime.utc(2026, 6, 5, 12),
            roomExists = roomExists,
            inviterExists = inviterExists,
            invalidReason = invalidReason,
        )
    }

    private fun _application(
        id: String,
        status: String = "pending",
        createdAt: DateTime? = null,
        updatedAt: DateTime? = null,
        reviewedAt: DateTime? = null,
        roomName: String = "Application Room",
        roomRid: String = "A-1",
        reviewer: UserSummary? = null,
        reviewerExists: Boolean = true,
        requestNotificationDeleted: Boolean = false,
        reviewNotificationDeleted: Boolean = false,
    ): RoomApplication {
        return RoomApplication(
            id = id,
            status = status,
            room =
                PublicRoom(
                    id = "room_$id",
                    rid = roomRid,
                    name = roomName,
                    avatarUrl = null,
                    defaultAvatarKey = "room-1",
                    visibility = "private",
                    joinPolicy = "approval_required",
                    memberCount = 3,
                    onlineMemberCount = 1,
                    liveParticipantCount = 0,
                    joined = false,
                    joinState = "pending",
                ),
            createdAt = createdAt ?: DateTime.utc(2026, 6, 5, 12),
            updatedAt = updatedAt ?: DateTime.utc(2026, 6, 5, 12),
            reviewedAt = reviewedAt,
            reviewer = reviewer,
            reviewerExists = reviewerExists,
            requestNotificationDeleted = requestNotificationDeleted,
            reviewNotificationDeleted = reviewNotificationDeleted,
        )
    }

    private fun _roomEvent(
        id: String,
        type: String = kRoomEventNotificationRoleDemoted,
        createdAt: DateTime? = null,
        roomName: String = "Event Room",
        roomRid: String = "E-1",
        actor: UserSummary? = null,
        actorExists: Boolean = true,
        roomExists: Boolean = true,
        fromRole: String = "owner",
        toRole: String = "admin",
        messageId: String? = null,
        messagePreview: String? = null,
        readAt: DateTime? = null,
    ): RoomEventNotification {
        return RoomEventNotification(
            id = id,
            type = type,
            room =
                PublicRoom(
                    id = "room_event_$id",
                    rid = roomRid,
                    name = roomName,
                    avatarUrl = null,
                    defaultAvatarKey = "room-1",
                    visibility = "private",
                    joinPolicy = "approval_required",
                    memberCount = 3,
                    onlineMemberCount = 1,
                    liveParticipantCount = 0,
                    joined = false,
                    joinState = "none",
                ),
            actor = actor ?: _user("actor_$id", roomRole = "owner"),
            createdAt = createdAt ?: DateTime.utc(2026, 6, 5, 12),
            actorExists = actorExists,
            roomExists = roomExists,
            fromRole = fromRole,
            toRole = toRole,
            messageId = messageId,
            messagePreview = messagePreview,
            readAt = readAt,
        )
    }

    private fun _user(
        id: String,
        uid: String? = null,
        username: String? = null,
        displayName: String? = null,
        roomRole: String? = null,
        roomDisplayName: String? = null,
        isSuperuser: Boolean = false,
        commonRooms: List<UserCommonRoom> = emptyList(),
    ): UserSummary {
        return UserSummary(
            id = id,
            uid = uid,
            username = username ?: "user_$id",
            displayName = displayName ?: "User $id",
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
            roomRole = roomRole,
            roomDisplayName = roomDisplayName,
            isSuperuser = isSuperuser,
            commonRooms = commonRooms,
        )
    }
}
