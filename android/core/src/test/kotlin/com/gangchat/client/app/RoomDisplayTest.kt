package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.LastMessagePreview
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomMemberProfile
import com.gangchat.client.protocol.RoomMembership
import com.gangchat.client.protocol.UserCommonRoom
import com.gangchat.client.protocol.UserSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomDisplayTest {
    @Test
    fun `roomSubtitle includes last message preview when present`() {
        assertEquals("8 名成员 · 3 人在线 · 2 人语音", roomSubtitle(_roomCard()))

        assertEquals(
            "Logan · hello",
            roomSubtitle(
                _roomCard(
                    lastMessage =
                        LastMessagePreview(
                            id = "message_1",
                            senderDisplayName = "Logan",
                            bodyPreview = "hello",
                            createdAt = DateTime.utc(2026, 6, 4),
                        ),
                ),
            ),
        )
        assertEquals("8 名成员 · 2 人语音", roomSidebarSubtitle(_roomCard()))
        assertEquals(
            "Logan · hello",
            roomSidebarSubtitle(
                _roomCard(
                    lastMessage =
                        LastMessagePreview(
                            id = "message_1",
                            senderDisplayName = "Logan",
                            bodyPreview = "hello",
                            createdAt = DateTime.utc(2026, 6, 4),
                        ),
                ),
            ),
        )
        assertEquals(
            "[系统] Logan 加入了房间",
            roomSidebarSubtitle(
                _roomCard(
                    lastMessage =
                        LastMessagePreview(
                            id = "message_2",
                            type = "system",
                            senderDisplayName = "Logan",
                            bodyPreview = "加入了房间",
                            createdAt = DateTime.utc(2026, 6, 4),
                        ),
                ),
            ),
        )
        assertEquals(
            "[系统] 房间名称 被 丁 修改为 123345",
            roomSidebarSubtitle(
                _roomCard(
                    lastMessage =
                        LastMessagePreview(
                            id = "message_3",
                            type = "system",
                            senderDisplayName = "丁",
                            bodyPreview = "房间名称修改为123345",
                            createdAt = DateTime.utc(2026, 6, 4),
                        ),
                ),
            ),
        )
        assertEquals(
            "[系统] 房间简介 被 丁 修改为 新简介",
            roomSidebarSubtitle(
                _roomCard(
                    lastMessage =
                        LastMessagePreview(
                            id = "message_4",
                            type = "system",
                            senderDisplayName = "",
                            bodyPreview = "房间简介 被 丁 修改为 新简介",
                            createdAt = DateTime.utc(2026, 6, 4),
                        ),
                ),
            ),
        )
        assertEquals(
            "[系统] 房间可见性 被 丁 修改为 私密",
            roomSidebarSubtitle(
                _roomCard(
                    lastMessage =
                        LastMessagePreview(
                            id = "message_5",
                            type = "system",
                            senderDisplayName = "丁",
                            bodyPreview = "房间可见性修改为私密",
                            createdAt = DateTime.utc(2026, 6, 4),
                        ),
                ),
            ),
        )
        assertEquals(
            "[系统] 房间加入方式 被 丁 修改为 关闭",
            roomSidebarSubtitle(
                _roomCard(
                    lastMessage =
                        LastMessagePreview(
                            id = "message_6",
                            type = "system",
                            senderDisplayName = "丁",
                            bodyPreview = "房间加入方式修改为关闭",
                            createdAt = DateTime.utc(2026, 6, 4),
                        ),
                ),
            ),
        )
    }

    @Test
    fun `room sidebar latest message time follows compact chat rules`() {
        val now = DateTime(2026, 6, 12, 10)
        assertEquals(
            "07:05",
            roomSidebarTimestamp(DateTime(2026, 6, 12, 7, 5), now = now),
        )
        assertEquals(
            "昨天 07:05",
            roomSidebarTimestamp(DateTime(2026, 6, 11, 7, 5), now = now),
        )
        assertEquals(
            "前天 07:05",
            roomSidebarTimestamp(DateTime(2026, 6, 10, 7, 5), now = now),
        )
        assertEquals("星期二", roomSidebarTimestamp(DateTime(2026, 6, 9, 7, 5), now = now))
        assertEquals(
            "2026/05/31",
            roomSidebarTimestamp(DateTime(2026, 5, 31, 7, 5), now = now),
        )
        assertEquals(
            "2025/12/31",
            roomSidebarTimestamp(DateTime(2025, 12, 31, 7, 5), now = now),
        )
        assertEquals("", roomSidebarLastMessageTime(_roomCard(), now = now))
        assertEquals(
            "07:05",
            roomSidebarLastMessageTime(
                _roomCard(
                    lastMessage =
                        LastMessagePreview(
                            id = "message_1",
                            senderDisplayName = "Logan",
                            bodyPreview = "hello",
                            createdAt = DateTime(2026, 6, 12, 7, 5),
                        ),
                ),
                now = now,
            ),
        )
    }

    @Test
    fun `room draft preview normalizes visible draft text`() {
        assertNull(roomDraftPreview(null))
        assertNull(roomDraftPreview("   "))
        assertEquals("hello draft world", roomDraftPreview(" hello\n  draft\tworld "))
        assertEquals(
            "[文件] report final.pdf",
            roomDraftPreviewText(
                text = "typed text",
                attachmentFilename = " report final.pdf ",
                attachmentMimeType = "application/pdf",
            ),
        )
        assertEquals(
            "[图片] cat picture.png",
            roomDraftPreviewText(
                text = null,
                attachmentFilename = "cat picture.png",
                attachmentMimeType = "image/png",
            ),
        )
        assertEquals(
            "[引用] reply text",
            roomDraftPreviewText(
                text = "reply text",
                attachmentFilename = null,
                attachmentMimeType = null,
                hasQuote = true,
            ),
        )
        assertEquals(
            "[引用]",
            roomDraftPreviewText(
                text = "",
                attachmentFilename = null,
                attachmentMimeType = null,
                hasQuote = true,
            ),
        )
    }

    @Test
    fun `room identity display helpers provide stable fallbacks`() {
        assertEquals("R001", roomIdentifier(_roomDetail(rid = "R001")))
        assertEquals("room_1", roomIdentifier(_roomDetail()))
        assertEquals("2026/06/04 09:05", roomCreatedAtLabel(DateTime(2026, 6, 4, 9, 5)))
        assertEquals(
            "Launch room",
            roomDescriptionValue(_roomDetail(description = "  Launch room  ")),
        )
        assertNull(roomDescriptionValue(_roomDetail(description = " ")))
        assertEquals(
            "Launch room",
            roomDescriptionText(_roomDetail(description = "  Launch room  ")),
        )
        assertEquals("暂无介绍", roomDescriptionText(_roomDetail(description = " ")))
    }

    @Test
    fun `room leave confirmation spec separates normal live and delete exits`() {
        val normal =
            roomLeaveConfirmationSpec(
                room = _roomDetail(memberCount = 3),
                isInLive = false,
            )
        assertFalse(normal.confirmDeleteIfEmpty)
        assertFalse(normal.requiresStrongConfirmation)
        assertEquals("退出房间", normal.title)
        assertTrue(normal.body.contains("房间成员中移除"))
        assertEquals("退出", normal.confirmLabel)
        assertNull(normal.expectedText)

        val live =
            roomLeaveConfirmationSpec(
                room = _roomDetail(memberCount = 3),
                isInLive = true,
            )
        assertTrue(live.body.contains("Live Channel"))

        val finalCreator =
            roomLeaveConfirmationSpec(
                room = _roomDetail(role = "creator", memberCount = 1),
                isInLive = true,
            )
        assertTrue(finalCreator.confirmDeleteIfEmpty)
        assertTrue(finalCreator.requiresStrongConfirmation)
        assertEquals("退出并删除房间", finalCreator.title)
        assertEquals("退出并删除", finalCreator.confirmLabel)
        assertEquals("Room", finalCreator.expectedText)
    }

    @Test
    fun `room operation notices and delete confirmation stay outside UI`() {
        assertEquals("RID 已复制", roomCopySuccessNotice("RID"))
        assertEquals("无法复制", roomCopyFailureMessage("denied"))
        assertEquals("UID 已复制", userUidCopySuccessNotice())
        assertEquals("无法复制 UID", userUidCopyFailureMessage("denied"))
        assertEquals("无法打开房间", roomOpenFailureMessage("missing"))
        assertEquals("房间刷新失败，已先打开当前房间", roomOptimisticOpenRefreshFailureNotice())
        assertEquals("保存后将使用全局默认用户名和默认头像", roomUseGlobalProfileNotice())
        assertEquals("房间信息已保存", roomInfoSavedNotice())

        val deletion = roomDeletionConfirmationSpec(_roomDetail())
        assertEquals("删除房间", deletion.title)
        assertEquals("将清空房间所有数据。这个动作不可恢复，请输入房间名确认。", deletion.body)
        assertEquals("删除房间", deletion.confirmLabel)
        assertEquals("Room", deletion.expectedText)
    }

    @Test
    fun `userPrimaryName prefers room display name then global display name`() {
        assertEquals(
            "Room Logan",
            userPrimaryName(
                _user(roomDisplayName = " Room Logan ", displayName = ""),
            ),
        )
        assertEquals(
            "Global Logan",
            userPrimaryName(_user(displayName = " Global Logan ")),
        )
        assertEquals("logan", userPrimaryName(_user(displayName = "")))
    }

    @Test
    fun `userAvatarLabel ignores the room display name`() {
        assertEquals(
            "Test Example",
            userAvatarLabel(
                _user(
                    username = "testxxxx",
                    displayName = "Test Example",
                    roomDisplayName = "J",
                ),
            ),
        )
        assertEquals(
            "testxxxx",
            userAvatarLabel(
                _user(username = "testxxxx", displayName = "", roomDisplayName = "J"),
            ),
        )
    }

    @Test
    fun `user identity labels prefer uid and include username handle`() {
        assertEquals("1001", userUidLabel(_user(uid = "1001")))
        assertEquals(
            "user_fallback",
            userUidLabel(_user(id = "user_fallback", uid = null)),
        )
        assertEquals("@logan", userUsernameLabel(_user(username = "logan")))
        assertEquals(
            "user_fallback · @logan",
            userIdentityMeta(_user(id = "user_fallback")),
        )
        assertEquals("1001 · @logan", userIdentityMeta(_user(uid = "1001")))
        assertEquals("Hello", userSignatureText(_user(bio = "  Hello  ")))
        assertNull(userSignatureText(_user(bio = "   ")))
        assertEquals("在线", userPresenceLabel(_user(isOnline = true)))
        assertEquals("离线", userPresenceLabel(_user(isOnline = false)))
        assertNull(userPresenceLabel(_user()))
        assertEquals(
            "在线",
            currentUserPresenceLabel(_currentUser(status = "active"), inLive = false),
        )
        assertEquals(
            "离线",
            currentUserPresenceLabel(_currentUser(status = "offline"), inLive = false),
        )
        assertEquals(
            "语音",
            currentUserPresenceLabel(_currentUser(status = "active"), inLive = true),
        )
        assertEquals(
            "重连中",
            currentUserPresenceLabel(
                _currentUser(status = "active"),
                inLive = true,
                reconnecting = true,
            ),
        )
    }

    @Test
    fun `roomRoleLabel handles pending superuser owner admin and member roles`() {
        assertEquals("待审批", roomRoleLabel(_user(roomRole = "pending")))
        assertEquals("超级用户", roomRoleLabel(_user(isSuperuser = true)))
        assertEquals("创建者", roomRoleLabel(_user(id = "owner"), ownerUserId = "owner"))
        assertEquals("管理员", roomRoleLabel(_user(roomRole = "administrator")))
        assertEquals("成员", roomRoleLabel(_user(roomRole = "member")))
    }

    @Test
    fun `common room labels include rid visibility display name and role`() {
        val room =
            UserCommonRoom(
                id = "room_1",
                rid = "1001",
                name = "General",
                remarkName = "Ops",
                visibility = "public",
                roomDisplayName = "Room Logan",
                roomRole = "admin",
            )

        assertEquals("Ops · 1001", commonRoomTitle(room))
        assertEquals("General", commonRoomAvatarLabel(room))
        assertEquals("公开", visibilityLabel(room.visibility))
        assertEquals("Room Logan · 管理员", commonRoomMeta(room))
        assertEquals(
            "Private",
            commonRoomTitle(
                UserCommonRoom(id = "room_2", rid = "", name = "Private"),
            ),
        )
    }

    @Test
    fun `common room names prefer the viewer local room remark`() {
        val commonRooms =
            listOf(
                UserCommonRoom(
                    id = "room_1",
                    rid = "REMOTE",
                    name = "Official Old",
                    remarkName = "Other User Remark",
                    visibility = "public",
                    avatarUrl = "/remote.png",
                    defaultAvatarKey = "remote-1",
                    roomDisplayName = "Room Logan",
                    roomRole = "admin",
                ),
                UserCommonRoom(
                    id = "room_2",
                    rid = "R002",
                    name = "Unmatched",
                    remarkName = "Keep Remote",
                ),
            )
        val localRooms =
            listOf(
                RoomCard(
                    id = "room_1",
                    rid = "LOCAL",
                    name = "Official Local",
                    visibility = "private",
                    remarkName = "Viewer Remark",
                    avatarUrl = "/local.png",
                    defaultAvatarKey = "local-1",
                    memberCount = 3,
                    onlineMemberCount = 1,
                    liveParticipantCount = 0,
                    liveAvatarPreview = emptyList(),
                    lastMessage = null,
                    unreadCount = 0,
                    updatedAt = DateTime.utc(2026, 7, 9),
                ),
            )

        val resolved =
            commonRoomsWithLocalRoomNames(
                commonRooms = commonRooms,
                rooms = localRooms,
            )

        assertEquals("LOCAL", resolved.first().rid)
        assertEquals("Official Local", resolved.first().name)
        assertEquals("private", resolved.first().visibility)
        assertEquals("Viewer Remark", resolved.first().remarkName)
        assertEquals("/local.png", resolved.first().avatarUrl)
        assertEquals("local-1", resolved.first().defaultAvatarKey)
        assertEquals("Room Logan", resolved.first().roomDisplayName)
        assertEquals("admin", resolved.first().roomRole)
        assertEquals("Viewer Remark", commonRoomDisplayName(resolved.first()))
        assertEquals("Keep Remote", commonRoomDisplayName(resolved.last()))

        val noRemark =
            commonRoomsWithLocalRoomNames(
                commonRooms = commonRooms,
                rooms =
                    listOf(
                        RoomCard(
                            id = "room_1",
                            rid = "LOCAL",
                            name = "Official Local",
                            visibility = "private",
                            remarkName = null,
                            avatarUrl = null,
                            defaultAvatarKey = "local-1",
                            memberCount = 3,
                            onlineMemberCount = 1,
                            liveParticipantCount = 0,
                            liveAvatarPreview = emptyList(),
                            lastMessage = null,
                            unreadCount = 0,
                            updatedAt = DateTime.utc(2026, 7, 9),
                        ),
                    ),
            )

        assertEquals("Official Local", commonRoomDisplayName(noRemark.first()))
    }

    @Test
    fun `room user info profile merges owner and current user fields`() {
        val creator =
            _user(
                id = "creator",
                username = "creator",
                displayName = "Creator",
                uid = "C001",
            )
        val creatorProfile =
            roomUserInfoProfile(
                user = _user(id = "creator", username = "creator", displayName = "", uid = ""),
                room = _roomDetail(createdBy = creator),
                currentUser = _currentUser(),
            )

        assertEquals("Creator", creatorProfile.displayName)
        assertEquals("C001", creatorProfile.uid)
        assertEquals("owner", creatorProfile.roomRole)

        val currentUserProfile =
            roomUserInfoProfile(
                user = _user(id = "user_1", displayName = "", roomRole = null),
                room = _roomDetail(role = "admin"),
                currentUser = _currentUser(),
            )

        assertEquals("Logan", currentUserProfile.displayName)
        assertEquals("admin", currentUserProfile.roomRole)
    }

    @Test
    fun `former room member profile keeps current default account fields`() {
        val currentDefault =
            UserSummary(
                id = "former",
                username = "current_name",
                displayName = "当前默认名",
                avatarUrl = null,
                defaultAvatarKey = "green-2",
                uid = "10002001",
                roomRole = "left",
            )
        val historicalSnapshot =
            UserSummary(
                id = "former",
                username = "old_name",
                displayName = "发送时默认名",
                avatarUrl = "/old-avatar.png",
                defaultAvatarKey = "blue-1",
                uid = "10002001",
                roomDisplayName = "发送时房间名",
                roomRole = "member",
            )

        val resolved =
            resolvedRoomMemberProfileUser(
                profile =
                    RoomMemberProfile(
                        user = currentDefault,
                        role = "left",
                        joinedAt = DateTime.fromMillisecondsSinceEpoch(0, isUtc = true),
                    ),
                fallback = historicalSnapshot,
            )

        assertEquals("current_name", resolved.username)
        assertEquals("当前默认名", resolved.displayName)
        assertNull(resolved.avatarUrl)
        assertEquals("green-2", resolved.defaultAvatarKey)
        assertNull(resolved.roomDisplayName)
        assertEquals("left", resolved.roomRole)
    }

    @Test
    fun `current room member profile can still fill omitted snapshot fields`() {
        val resolved =
            resolvedRoomMemberProfileUser(
                profile =
                    RoomMemberProfile(
                        user = _user(id = "member", bio = null, roomRole = "member"),
                        role = "member",
                        joinedAt = DateTime.utc(2026, 7, 20),
                    ),
                fallback =
                    _user(
                        id = "member",
                        bio = "历史签名",
                        roomDisplayName = "房间名",
                        roomRole = "member",
                    ),
            )

        assertEquals("历史签名", resolved.bio)
        assertEquals("房间名", resolved.roomDisplayName)
    }

    @Test
    fun `room user info common rooms handles selected room visibility`() {
        val existing =
            UserCommonRoom(
                id = "room_2",
                rid = "R002",
                name = "Other",
                visibility = "private",
            )
        val user =
            _user(
                id = "other",
                commonRooms =
                    listOf(
                        UserCommonRoom(id = "room_1", rid = "old", name = "Duplicate"),
                        existing,
                    ),
            )
        val selectedRoom = _roomDetail(rid = "R001", visibility = "public")

        assertEquals(
            listOf("room_1", "room_2"),
            roomUserInfoCommonRooms(
                user = user,
                selectedRoom = selectedRoom,
                currentUser = _currentUser(),
                includeSelectedRoom = false,
            ).map { room -> room.id },
        )

        val withSelected =
            roomUserInfoCommonRooms(
                user = user.copyWith(roomDisplayName = "Room Other", roomRole = "member"),
                selectedRoom = selectedRoom,
                currentUser = _currentUser(),
                includeSelectedRoom = true,
            )

        assertEquals(listOf("room_1", "room_2"), withSelected.map { room -> room.id })
        assertEquals("R001", withSelected.first().rid)
        assertEquals("public", withSelected.first().visibility)
        assertEquals("Room Other", withSelected.first().roomDisplayName)
        assertEquals("member", withSelected.first().roomRole)

        assertEquals(
            listOf("room_1"),
            roomUserInfoCommonRooms(
                user = _user(id = "user_1"),
                selectedRoom = selectedRoom,
                currentUser = _currentUser(),
                includeSelectedRoom = true,
            ).map { room -> room.id },
        )
        assertTrue(
            roomUserInfoCommonRooms(
                user = _user(id = "super", isSuperuser = true),
                selectedRoom = selectedRoom,
                currentUser = _currentUser(),
                includeSelectedRoom = true,
            ).isEmpty(),
        )
        assertTrue(
            roomUserInfoCommonRooms(
                user = _user(id = "other", commonRooms = emptyList()),
                selectedRoom = selectedRoom,
                currentUser = _currentUser(isSuperuser = true),
                includeSelectedRoom = false,
            ).map { room -> room.id }.isEmpty(),
        )
        assertEquals(
            listOf("room_2"),
            roomUserInfoCommonRooms(
                user = _user(id = "other", commonRooms = listOf(existing)),
                selectedRoom = selectedRoom,
                currentUser = _currentUser(isSuperuser = true),
                includeSelectedRoom = false,
            ).map { room -> room.id },
        )
        assertNull(
            userRoomsSectionTitle(
                user = _user(id = "super", isSuperuser = true),
                currentUser = _currentUser(),
            ),
        )
        assertEquals(
            "所有房间",
            userRoomsSectionTitle(
                user = _user(id = "user_1"),
                currentUser = _currentUser(),
            ),
        )
        assertEquals(
            "共同房间",
            userRoomsSectionTitle(user = user, currentUser = _currentUser()),
        )
    }

    @Test
    fun `room setting values are normalized for server payloads`() {
        assertEquals("silent", normalizeRoomNotificationPolicy("mention_only"))
        assertEquals("silent", normalizeRoomNotificationPolicy("dnd"))
        assertEquals("blocked", normalizeRoomNotificationPolicy("blocked"))
        assertEquals("all", normalizeRoomNotificationPolicy("unknown"))

        assertEquals("private", normalizeRoomVisibility("private"))
        assertEquals("public", normalizeRoomVisibility("PUBLIC"))

        assertEquals("open", normalizeRoomJoinPolicy("allow_anyone"))
        assertEquals("closed", normalizeRoomJoinPolicy("deny_all"))
        assertEquals("approval_required", normalizeRoomJoinPolicy("unknown"))
        assertEquals("开放", roomJoinPolicyLabel("open"))
        assertEquals("需审批", roomJoinPolicyLabel("approval_required"))
        assertEquals("关闭", roomJoinPolicyLabel("closed"))
        assertEquals("需审批", roomJoinPolicyLabel("unknown"))
    }

    @Test
    fun `public room join action helpers reflect state`() {
        assertEquals(
            "进入",
            publicRoomJoinActionLabel(_publicRoom(joined = true), pending = false),
        )
        assertEquals("待审批", publicRoomJoinActionLabel(_publicRoom(), pending = true))
        assertEquals(
            "加入",
            publicRoomJoinActionLabel(
                _publicRoom(joinPolicy = "approval_required"),
                pending = false,
            ),
        )
        assertEquals(
            "不可加入",
            publicRoomJoinActionLabel(
                _publicRoom(joinPolicy = "closed"),
                pending = false,
            ),
        )
        assertEquals("加入", publicRoomJoinActionLabel(_publicRoom(), pending = false))
        assertFalse(publicRoomJoinActionable(_publicRoom(), pending = true))
        assertFalse(
            publicRoomJoinActionable(
                _publicRoom(joinPolicy = "closed"),
                pending = false,
            ),
        )
        assertTrue(
            publicRoomJoinRequiresApplication(
                _publicRoom(joinPolicy = "approval_required"),
            ),
        )
        assertTrue(
            publicRoomJoinActionable(_publicRoom(joined = true), pending = true),
        )
    }

    @Test
    fun `roomManagementPermissionState reuses room and user privileges`() {
        var access =
            roomAccessState(
                room = _roomDetail(role = "admin"),
                currentUser = _currentUser(),
            )
        assertTrue(access.canManageRoom)
        assertTrue(access.canReviewJoinRequests)
        assertTrue(access.showJoinRequestBadge(true))
        assertFalse(access.showJoinRequestBadge(false))

        access =
            roomAccessState(
                room = _roomDetail(role = "member"),
                currentUser = _currentUser(),
            )
        assertFalse(access.canManageRoom)
        assertFalse(access.canReviewJoinRequests)
        assertFalse(access.showJoinRequestBadge(true))

        access =
            roomAccessState(
                room = _roomDetail(role = "member"),
                currentUser = _currentUser(isSuperuser = true),
            )
        assertTrue(access.canManageRoom)
        assertTrue(access.canReviewJoinRequests)

        var permission =
            roomManagementPermissionState(
                room = _roomDetail(role = "admin", canDeleteRoom = false),
                currentUser = _currentUser(),
            )
        assertFalse(permission.canEditCreatorOnly)
        assertFalse(permission.canDeleteRoom)

        permission =
            roomManagementPermissionState(
                room = _roomDetail(role = "creator"),
                currentUser = _currentUser(),
            )
        assertTrue(permission.canEditCreatorOnly)
        assertTrue(permission.canDeleteRoom)

        permission =
            roomManagementPermissionState(
                room = _roomDetail(role = "member", canDeleteRoom = false),
                currentUser = _currentUser(isSuperuser = true),
            )
        assertTrue(permission.canEditCreatorOnly)
        assertTrue(permission.canDeleteRoom)
    }

    @Test
    fun `room profile avatar helpers preserve global preset and upload states`() {
        assertEquals(
            "/global.png",
            roomProfileAvatarPath(
                usingGlobalProfile = true,
                currentUserAvatarUrl = "/global.png",
                pendingAvatarUrl = "/pending.png",
                usingPresetAvatar = false,
                personalAvatarUrl = "/personal.png",
            ),
        )
        assertEquals(
            "/pending.png",
            roomProfileAvatarPath(
                usingGlobalProfile = false,
                currentUserAvatarUrl = "/global.png",
                pendingAvatarUrl = "/pending.png",
                usingPresetAvatar = false,
                personalAvatarUrl = "/personal.png",
            ),
        )
        assertNull(
            roomProfileAvatarPath(
                usingGlobalProfile = false,
                currentUserAvatarUrl = "/global.png",
                pendingAvatarUrl = null,
                usingPresetAvatar = true,
                personalAvatarUrl = "/personal.png",
            ),
        )
        assertTrue(
            roomProfileUploadedAvatarSelected(
                usingGlobalProfile = false,
                usingPresetAvatar = false,
                pendingAvatarUrl = null,
                personalAvatarUrl = "/personal.png",
            ),
        )
        assertTrue(
            roomProfilePresetAvatarSelected(
                usingGlobalProfile = false,
                usingPresetAvatar = true,
            ),
        )
    }

    @Test
    fun `room profile and management display helpers trim fallback names`() {
        assertEquals(
            "Room",
            roomProfileDisplayName(
                usingGlobalProfile = false,
                currentUserDisplayName = "Global",
                roomDisplayNameText = " Room ",
            ),
        )
        assertEquals(
            "Global",
            roomProfileDisplayName(
                usingGlobalProfile = false,
                currentUserDisplayName = "Global",
                roomDisplayNameText = " ",
            ),
        )
        assertEquals(
            "/pending.png",
            roomManagementAvatarPath(
                usingPresetAvatar = false,
                pendingAvatarUrl = "/pending.png",
                roomAvatarUrl = "/room.png",
            ),
        )
        assertNull(
            roomManagementAvatarPath(
                usingPresetAvatar = true,
                pendingAvatarUrl = "/pending.png",
                roomAvatarUrl = "/room.png",
            ),
        )
        assertTrue(
            roomManagementUploadedAvatarSelected(usingPresetAvatar = false),
        )
    }

    private fun _roomCard(lastMessage: LastMessagePreview? = null): RoomCard {
        return RoomCard(
            id = "room_1",
            name = "General",
            avatarUrl = null,
            defaultAvatarKey = "room-1",
            memberCount = 8,
            onlineMemberCount = 3,
            liveParticipantCount = 2,
            liveAvatarPreview = emptyList(),
            lastMessage = lastMessage,
            unreadCount = 0,
            updatedAt = DateTime.utc(2026, 6, 4),
        )
    }

    private fun _user(
        id: String = "user_1",
        username: String = "logan",
        uid: String? = null,
        displayName: String? = "Logan",
        roomDisplayName: String? = null,
        roomRole: String? = null,
        bio: String? = null,
        isSuperuser: Boolean = false,
        isOnline: Boolean? = null,
        commonRooms: List<UserCommonRoom> = emptyList(),
    ): UserSummary {
        return UserSummary(
            id = id,
            username = username,
            displayName = displayName ?: username,
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
            uid = uid,
            bio = bio,
            roomDisplayName = roomDisplayName,
            roomRole = roomRole,
            isSuperuser = isSuperuser,
            isOnline = isOnline,
            commonRooms = commonRooms,
        )
    }

    private fun _publicRoom(joined: Boolean = false, joinPolicy: String = "open"): PublicRoom {
        return PublicRoom(
            id = "room_1",
            rid = "R001",
            name = "Room",
            avatarUrl = null,
            defaultAvatarKey = "room-1",
            visibility = "public",
            joinPolicy = joinPolicy,
            memberCount = 3,
            liveParticipantCount = 0,
            joined = joined,
            joinState = if (joined) "joined" else "none",
        )
    }

    private fun _roomDetail(
        role: String = "member",
        canDeleteRoom: Boolean? = null,
        createdBy: UserSummary? = null,
        rid: String = "",
        visibility: String = "private",
        description: String = "",
        memberCount: Int = 1,
    ): RoomDetail {
        return RoomDetail(
            id = "room_1",
            name = "Room",
            rid = rid,
            visibility = visibility,
            avatarUrl = null,
            defaultAvatarKey = "room-1",
            memberCount = memberCount,
            description = description,
            createdBy = createdBy,
            myMembership =
                RoomMembership(
                    joinedAt = DateTime.utc(2026, 6, 4),
                    role = role,
                ),
            live =
                LiveState(
                    roomId = "room_1",
                    participantCount = 0,
                    participants = emptyList(),
                    updatedAt = DateTime.utc(2026, 6, 4),
                ),
            createdAt = DateTime.utc(2026, 6, 4),
            updatedAt = DateTime.utc(2026, 6, 4),
            canDeleteRoom = canDeleteRoom,
        )
    }

    private fun _currentUser(isSuperuser: Boolean = false, status: String? = null): CurrentUser {
        return CurrentUser(
            id = "user_1",
            uid = "1001",
            username = "logan",
            displayName = "Logan",
            bio = "",
            gender = "secret",
            email = null,
            emailPublic = false,
            phoneNumber = null,
            phoneNumberPublic = false,
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
            isSuperuser = isSuperuser,
            createdAt = DateTime.utc(2026, 6, 4),
            status = status,
        )
    }
}
