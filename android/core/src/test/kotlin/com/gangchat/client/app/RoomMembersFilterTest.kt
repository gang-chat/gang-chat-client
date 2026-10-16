package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.LiveParticipant
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.RoomMembership
import com.gangchat.client.protocol.UserSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMembersFilterTest {
    @Test
    fun `roomMemberPresenceLabel exposes reusable display labels`() {
        assertEquals("语音", roomMemberPresenceLabel(RoomMemberPresence.live))
        assertEquals("在线", roomMemberPresenceLabel(RoomMemberPresence.online))
        assertEquals("离线", roomMemberPresenceLabel(RoomMemberPresence.offline))
    }

    @Test
    fun `room member filter counts feed presence and role labels`() {
        val counts =
            roomMemberFilterCounts(
                members =
                    listOf(
                        _member("owner", isOnline = false),
                        _member("admin", role = "admin", isOnline = true),
                        _member("member", isOnline = false),
                        _member("live_member", isOnline = false),
                    ),
                live = _live(listOf("live_member")),
                ownerUserId = "owner",
            )

        assertEquals(4, counts.allPresence)
        assertEquals(1, counts.live)
        assertEquals(2, counts.online)
        assertEquals(2, counts.offline)
        assertEquals(4, counts.allRoles)
        assertEquals(2, counts.roleMembers)
        assertEquals(2, counts.admins)
        assertEquals(1, counts.creators)
        assertEquals(
            "全部 4",
            roomMemberPresenceFilterLabel(RoomMemberPresenceFilter.all, counts),
        )
        assertEquals(
            "语音 1",
            roomMemberPresenceFilterLabel(RoomMemberPresenceFilter.live, counts),
        )
        assertEquals(
            "在线 2",
            roomMemberPresenceFilterLabel(RoomMemberPresenceFilter.online, counts),
        )
        assertEquals(
            "离线 2",
            roomMemberPresenceFilterLabel(RoomMemberPresenceFilter.offline, counts),
        )
        assertEquals(
            "所有身份 4",
            roomMemberRoleFilterLabel(RoomMemberRoleFilter.all, counts),
        )
        assertEquals(
            "成员 2",
            roomMemberRoleFilterLabel(RoomMemberRoleFilter.member, counts),
        )
        assertEquals(
            "管理员 2",
            roomMemberRoleFilterLabel(RoomMemberRoleFilter.admin, counts),
        )
        assertEquals(
            "创建者 1",
            roomMemberRoleFilterLabel(RoomMemberRoleFilter.creator, counts),
        )
    }

    @Test
    fun `room member filter patches preserve the other filter dimension`() {
        val presencePatch =
            roomMemberPresenceFilterChanged(
                searchQuery = "alice",
                presenceFilter = RoomMemberPresenceFilter.online,
                roleFilter = RoomMemberRoleFilter.admin,
            )

        assertEquals("alice", presencePatch.searchQuery)
        assertEquals(RoomMemberPresenceFilter.online, presencePatch.presenceFilter)
        assertEquals(RoomMemberRoleFilter.admin, presencePatch.roleFilter)

        val rolePatch =
            roomMemberRoleFilterChanged(
                searchQuery = "bob",
                presenceFilter = RoomMemberPresenceFilter.offline,
                roleFilter = RoomMemberRoleFilter.member,
            )

        assertEquals("bob", rolePatch.searchQuery)
        assertEquals(RoomMemberPresenceFilter.offline, rolePatch.presenceFilter)
        assertEquals(RoomMemberRoleFilter.member, rolePatch.roleFilter)

        val searchPatch =
            roomMemberSearchQueryChanged(
                searchQuery = "carol",
                presenceFilter = RoomMemberPresenceFilter.all,
                roleFilter = RoomMemberRoleFilter.all,
            )
        assertEquals("carol", searchPatch.searchQuery)
        assertEquals(RoomMemberPresenceFilter.all, searchPatch.presenceFilter)
        assertEquals(RoomMemberRoleFilter.all, searchPatch.roleFilter)
    }

    @Test
    fun `visibleRoomMembers sorts by live presence before online state`() {
        val members =
            listOf(
                _member("offline_owner", role = "member", isOnline = false),
                _member("online_member", isOnline = true),
                _member("live_member", isOnline = false),
            )

        val visible =
            visibleRoomMembers(
                members = members,
                live = _live(listOf("live_member")),
                presenceFilter = RoomMemberPresenceFilter.all,
                roleFilter = RoomMemberRoleFilter.all,
                query = "",
                ownerUserId = "offline_owner",
            )

        assertEquals(
            listOf(
                "live_member",
                "online_member",
                "offline_owner",
            ),
            visible.map { member -> member.user.id },
        )
    }

    @Test
    fun `visibleRoomMembers sorts members with equal presence by room role`() {
        val members =
            listOf(
                _member("member"),
                _member("admin", role = "admin"),
                _member("owner"),
                _member("superuser", isSuperuser = true),
            )

        val visible =
            visibleRoomMembers(
                members = members,
                live = _live(),
                presenceFilter = RoomMemberPresenceFilter.all,
                roleFilter = RoomMemberRoleFilter.all,
                query = "",
                ownerUserId = "owner",
            )

        assertEquals(
            listOf(
                "superuser",
                "owner",
                "admin",
                "member",
            ),
            visible.map { member -> member.user.id },
        )
    }

    @Test
    fun `ordered visible members keep their prior order after role patches`() {
        val members =
            listOf(
                _member("admin_a", role = "admin", displayName = "Admin A"),
                _member("member_b", displayName = "Member B"),
                _member("target_c", displayName = "Target C"),
            )

        val initialOrder =
            visibleRoomMembers(
                members = members,
                live = _live(),
                presenceFilter = RoomMemberPresenceFilter.all,
                roleFilter = RoomMemberRoleFilter.all,
                query = "",
            ).map { member -> member.user.id }
        assertEquals(listOf("admin_a", "member_b", "target_c"), initialOrder)

        val updatedMembers =
            listOf(
                members[0],
                members[1],
                roomMemberWithRole(members[2], "admin"),
            )
        assertEquals(
            listOf("admin_a", "target_c", "member_b"),
            visibleRoomMembers(
                members = updatedMembers,
                live = _live(),
                presenceFilter = RoomMemberPresenceFilter.all,
                roleFilter = RoomMemberRoleFilter.all,
                query = "",
            ).map { member -> member.user.id },
        )

        val filtered =
            filteredRoomMembers(
                members = updatedMembers,
                live = _live(),
                presenceFilter = RoomMemberPresenceFilter.all,
                roleFilter = RoomMemberRoleFilter.all,
                query = "",
            )
        assertEquals(
            listOf("admin_a", "member_b", "target_c"),
            orderRoomMembersByUserIds(
                members = filtered,
                orderedUserIds = initialOrder,
            ).map { member -> member.user.id },
        )
    }

    @Test
    fun `roomMemberPresenceGroups buckets members in display order`() {
        val members =
            listOf(
                _member("offline", isOnline = false),
                _member("online", isOnline = true),
                _member("live", isOnline = false),
                _member("also_live", isOnline = true),
            )

        val groups =
            roomMemberPresenceGroups(
                members = members,
                live = _live(listOf("live", "also_live")),
            )

        assertEquals(
            listOf(
                RoomMemberPresence.live,
                RoomMemberPresence.online,
                RoomMemberPresence.offline,
            ),
            groups.map { group -> group.presence },
        )
        assertEquals(listOf(2, 1, 1), groups.map { group -> group.count })
        assertEquals(
            listOf(
                "live",
                "also_live",
            ),
            groups[0].members.map { member -> member.user.id },
        )
        assertEquals("online", groups[1].members.single().user.id)
        assertEquals("offline", groups[2].members.single().user.id)
    }

    @Test
    fun `current user presence fallback keeps the signed-in member online`() {
        val self = _member("self", isOnline = false)
        val other = _member("other", isOnline = false)

        val members =
            roomMembersWithCurrentUserPresence(
                listOf(
                    self,
                    other,
                ),
                currentUserId = "self",
            )

        assertEquals("self", members.first().user.id)
        assertEquals(true, members.first().isOnline)
        assertEquals(true, members.first().user.isOnline)
        assertSame(other, members.last())
        assertEquals(
            RoomMemberPresence.online,
            roomMemberPresence(members.first(), live = _live()),
        )

        val online =
            visibleRoomMembers(
                members = members,
                live = _live(),
                presenceFilter = RoomMemberPresenceFilter.online,
                roleFilter = RoomMemberRoleFilter.all,
                query = "",
            )
        assertEquals(listOf("self"), online.map { member -> member.user.id })
    }

    @Test
    fun `visibleRoomMembers filters presence and role consistently`() {
        val members =
            listOf(
                _member("live_admin", role = "admin", isOnline = false),
                _member("online_member", isOnline = true),
                _member("offline_member", isOnline = false),
                _member("owner", isOnline = false),
            )

        val live =
            visibleRoomMembers(
                members = members,
                live = _live(listOf("live_admin")),
                presenceFilter = RoomMemberPresenceFilter.live,
                roleFilter = RoomMemberRoleFilter.all,
                query = "",
                ownerUserId = "owner",
            )
        assertEquals(listOf("live_admin"), live.map { member -> member.user.id })

        val online =
            visibleRoomMembers(
                members = members,
                live = _live(listOf("live_admin")),
                presenceFilter = RoomMemberPresenceFilter.online,
                roleFilter = RoomMemberRoleFilter.all,
                query = "",
                ownerUserId = "owner",
            )
        assertEquals(
            listOf(
                "live_admin",
                "online_member",
            ),
            online.map { member -> member.user.id },
        )

        val offlineMembers =
            visibleRoomMembers(
                members = members,
                live = _live(listOf("live_admin")),
                presenceFilter = RoomMemberPresenceFilter.offline,
                roleFilter = RoomMemberRoleFilter.member,
                query = "",
                ownerUserId = "owner",
            )
        assertEquals(listOf("offline_member"), offlineMembers.map { member -> member.user.id })

        val admins =
            visibleRoomMembers(
                members = members,
                live = _live(listOf("live_admin")),
                presenceFilter = RoomMemberPresenceFilter.all,
                roleFilter = RoomMemberRoleFilter.admin,
                query = "",
                ownerUserId = "owner",
            )
        assertEquals(listOf("live_admin", "owner"), admins.map { member -> member.user.id })

        val creators =
            visibleRoomMembers(
                members = members,
                live = _live(listOf("live_admin")),
                presenceFilter = RoomMemberPresenceFilter.all,
                roleFilter = RoomMemberRoleFilter.creator,
                query = "",
                ownerUserId = "owner",
            )
        assertEquals(listOf("owner"), creators.map { member -> member.user.id })
    }

    @Test
    fun `search rank prefers uid then display name then remark`() {
        val members =
            listOf(
                _member("remark_match", remarkName = "alpha note"),
                _member("name_match", displayName = "Alpha Name"),
                _member("uid_match", uid = "alpha-001"),
                _member("miss"),
            )

        val visible =
            visibleRoomMembers(
                members = members,
                live = _live(),
                presenceFilter = RoomMemberPresenceFilter.all,
                roleFilter = RoomMemberRoleFilter.all,
                query = "alpha",
            )

        assertEquals(
            listOf(
                "uid_match",
                "name_match",
                "remark_match",
            ),
            visible.map { member -> member.user.id },
        )
    }

    @Test
    fun `member search ignores room display names from any context`() {
        val members =
            listOf(
                _member(
                    "shared_context",
                    displayName = "Shared Context",
                    userRoomDisplayName = "Alpha From Another Room",
                ),
                _member(
                    "current_room_name",
                    displayName = "Current User",
                    roomDisplayName = "Alpha Current",
                ),
                _member("profile_name", displayName = "Alpha Profile"),
            )

        val sharedContextOnly =
            visibleRoomMembers(
                members = members,
                live = _live(),
                presenceFilter = RoomMemberPresenceFilter.all,
                roleFilter = RoomMemberRoleFilter.all,
                query = "another room",
            )
        assertTrue(sharedContextOnly.isEmpty())

        val currentRoomDisplayNameOnly =
            visibleRoomMembers(
                members = members,
                live = _live(),
                presenceFilter = RoomMemberPresenceFilter.all,
                roleFilter = RoomMemberRoleFilter.all,
                query = "alpha current",
            )
        assertTrue(currentRoomDisplayNameOnly.isEmpty())

        val profileName =
            visibleRoomMembers(
                members = members,
                live = _live(),
                presenceFilter = RoomMemberPresenceFilter.all,
                roleFilter = RoomMemberRoleFilter.all,
                query = "alpha profile",
            )
        assertEquals(listOf("profile_name"), profileName.map { member -> member.user.id })
    }

    @Test
    fun `replaceRoomMember swaps the member with the same user id`() {
        val members = listOf(_member("a"), _member("b"), _member("c"))
        val updated = _member("b", role = "admin")

        val next = replaceRoomMember(members, updated)

        assertEquals(listOf("a", "b", "c"), next.map { member -> member.user.id })
        assertEquals("admin", next[1].role)
        assertSame(members[0], next[0])
        assertSame(members[2], next[2])
    }

    @Test
    fun `roomMemberWithRole updates member and embedded user role`() {
        val member =
            _member(
                "target",
                roomDisplayName = "Room Target",
                remarkName = "Pinned",
                isOnline = false,
            )

        val updated = roomMemberWithRole(member, "admin")

        assertEquals("admin", updated.role)
        assertEquals("admin", updated.user.roomRole)
        assertEquals("Room Target", updated.roomDisplayName)
        assertEquals("Pinned", updated.remarkName)
        assertEquals(false, updated.isOnline)
    }

    @Test
    fun `room member action helpers guard busy members`() {
        val member = _member("busy")
        val busyMemberIds = setOf("busy")

        assertFalse(
            canStartRoomMemberAction(userId = "busy", busyMemberIds = busyMemberIds),
        )
        assertTrue(
            canStartRoomMemberAction(userId = "idle", busyMemberIds = busyMemberIds),
        )
        assertTrue(
            roomMemberActionBusy(member = member, busyMemberIds = busyMemberIds),
        )
    }

    @Test
    fun `room member action helpers provide role and transfer copy`() {
        val member = _member("target", roomDisplayName = "Room Target")

        assertEquals("已授予管理员身份", roomMemberRoleUpdateNotice("admin"))
        assertEquals("已移除管理员身份", roomMemberRoleUpdateNotice("member"))
        assertEquals("设为管理员", roomMemberRoleUpdateConfirmTitle("admin"))
        assertEquals("移除管理员", roomMemberRoleUpdateConfirmTitle("member"))
        assertEquals(
            "确定要将 Room Target 设为管理员吗？",
            roomMemberRoleUpdateConfirmBody(member, "admin"),
        )
        assertEquals(
            "确定要移除 Room Target 的管理员身份吗？",
            roomMemberRoleUpdateConfirmBody(member, "member"),
        )
        assertEquals("设为管理员", roomMemberRoleUpdateConfirmLabel("admin"))
        assertEquals("移除", roomMemberRoleUpdateConfirmLabel("member"))
        assertEquals("转让创建者", transferCreatorDialogTitle())
        assertEquals(
            "创建者身份会转让给 Room Target，你将成为管理员。",
            transferCreatorConfirmBody(member, currentUserIsCreator = true),
        )
        assertEquals(
            "创建者身份会转让给 Room Target，原创建者将成为管理员。",
            transferCreatorConfirmBody(member, currentUserIsCreator = false),
        )
        assertEquals("转让", transferCreatorConfirmLabel())
        assertEquals("创建者已转让", transferCreatorSuccessNotice())
        assertEquals("踢出此用户", removeRoomMemberConfirmTitle())
        assertEquals("确定要将 Room Target 从房间中移除吗？", removeRoomMemberConfirmBody(member))
        assertEquals("踢出", removeRoomMemberConfirmLabel())
        assertEquals("已踢出 Room Target", removeRoomMemberSuccessNotice(member))
    }

    @Test
    fun `roomMemberPermissionState gates creator-only member role actions`() {
        val currentUser = _currentUser("current")

        val member =
            roomMemberPermissionState(
                member = _member("member"),
                currentUser = currentUser,
                canEditCreatorOnly = true,
                canManageMembers = true,
                ownerUserId = "owner",
            )
        assertTrue(member.canSetAdmin)
        assertFalse(member.canUnsetAdmin)
        assertTrue(member.canTransferCreator)
        assertTrue(member.canRemoveMember)
        assertTrue(member.canEditRoomDisplayName)
        assertEquals("设为管理员", member.adminActionLabel)

        val admin =
            roomMemberPermissionState(
                member = _member("admin", role = "admin"),
                currentUser = currentUser,
                canEditCreatorOnly = true,
                canManageMembers = true,
                ownerUserId = "owner",
            )
        assertFalse(admin.canSetAdmin)
        assertTrue(admin.canUnsetAdmin)
        assertTrue(admin.canRemoveMember)
        assertTrue(admin.canEditRoomDisplayName)
        assertEquals("移除管理员", admin.adminActionLabel)

        val adminManagedByAdmin =
            roomMemberPermissionState(
                member = _member("admin_peer", role = "admin"),
                currentUser = currentUser,
                canEditCreatorOnly = false,
                canManageMembers = true,
                ownerUserId = "owner",
            )
        assertFalse(adminManagedByAdmin.canRoleEdit)
        assertFalse(adminManagedByAdmin.canRemoveMember)
        assertFalse(adminManagedByAdmin.canEditRoomDisplayName)

        val managedByAdmin =
            roomMemberPermissionState(
                member = _member("managed"),
                currentUser = currentUser,
                canEditCreatorOnly = false,
                canManageMembers = true,
                ownerUserId = "owner",
            )
        assertFalse(managedByAdmin.canRoleEdit)
        assertTrue(managedByAdmin.canRemoveMember)
        assertTrue(managedByAdmin.canEditRoomDisplayName)
    }

    @Test
    fun `roomMemberPermissionState blocks self owner and superuser edits`() {
        val currentUser = _currentUser("current")

        assertFalse(
            roomMemberPermissionState(
                member = _member("current"),
                currentUser = currentUser,
                canEditCreatorOnly = true,
                canManageMembers = true,
            ).canRoleEdit,
        )
        assertFalse(
            roomMemberPermissionState(
                member = _member("current"),
                currentUser = currentUser,
                canEditCreatorOnly = true,
                canManageMembers = true,
            ).canRemoveMember,
        )
        assertFalse(
            roomMemberPermissionState(
                member = _member("current"),
                currentUser = currentUser,
                canEditCreatorOnly = true,
                canManageMembers = true,
            ).canEditRoomDisplayName,
        )
        assertFalse(
            roomMemberPermissionState(
                member = _member("owner"),
                currentUser = currentUser,
                canEditCreatorOnly = true,
                canManageMembers = true,
                ownerUserId = "owner",
            ).canRoleEdit,
        )
        assertFalse(
            roomMemberPermissionState(
                member = _member("owner"),
                currentUser = currentUser,
                canEditCreatorOnly = true,
                canManageMembers = true,
                ownerUserId = "owner",
            ).canRemoveMember,
        )
        assertFalse(
            roomMemberPermissionState(
                member = _member("owner"),
                currentUser = currentUser,
                canEditCreatorOnly = true,
                canManageMembers = true,
                ownerUserId = "owner",
            ).canEditRoomDisplayName,
        )
        assertFalse(
            roomMemberPermissionState(
                member = _member("superuser", isSuperuser = true),
                currentUser = currentUser,
                canEditCreatorOnly = true,
                canManageMembers = true,
            ).canRoleEdit,
        )
        assertFalse(
            roomMemberPermissionState(
                member = _member("superuser", isSuperuser = true),
                currentUser = currentUser,
                canEditCreatorOnly = true,
                canManageMembers = true,
            ).canRemoveMember,
        )
        assertFalse(
            roomMemberPermissionState(
                member = _member("superuser", isSuperuser = true),
                currentUser = currentUser,
                canEditCreatorOnly = true,
                canManageMembers = true,
            ).canEditRoomDisplayName,
        )
        assertFalse(
            roomMemberPermissionState(
                member = _member("member"),
                currentUser = currentUser,
                canEditCreatorOnly = false,
            ).canRoleEdit,
        )
        assertFalse(
            roomMemberPermissionState(
                member = _member("member"),
                currentUser = currentUser,
                canEditCreatorOnly = false,
            ).canRemoveMember,
        )
        assertFalse(
            roomMemberPermissionState(
                member = _member("member"),
                currentUser = currentUser,
                canEditCreatorOnly = false,
            ).canEditRoomDisplayName,
        )
    }

    @Test
    fun `roomMemberPermissionState lets superusers remove creators`() {
        val currentUser = _currentUser("current", isSuperuser = true)

        val creator =
            roomMemberPermissionState(
                member = _member("owner", role = "owner"),
                currentUser = currentUser,
                canEditCreatorOnly = true,
                canManageMembers = true,
                ownerUserId = "owner",
            )
        assertFalse(creator.canRoleEdit)
        assertFalse(creator.canSetAdmin)
        assertFalse(creator.canUnsetAdmin)
        assertFalse(creator.canTransferCreator)
        assertTrue(creator.canRemoveMember)
        assertTrue(creator.canEditRoomDisplayName)

        val superuserCreator =
            roomMemberPermissionState(
                member = _member("owner", role = "owner", isSuperuser = true),
                currentUser = currentUser,
                canEditCreatorOnly = true,
                canManageMembers = true,
                ownerUserId = "owner",
            )
        assertFalse(superuserCreator.canRemoveMember)
        assertFalse(superuserCreator.canEditRoomDisplayName)
    }

    @Test
    fun `profile management shortcut follows member management permissions`() {
        val currentUser = _currentUser("current")
        val superuser = _currentUser("superuser", isSuperuser = true)
        val ownerRoom = _room("room_1", role = "owner")
        val adminRoom = _room("room_1", role = "admin")

        assertTrue(
            canOpenRoomMemberManagementFromProfile(
                room = ownerRoom,
                currentUser = currentUser,
                target = _member("admin", role = "admin", uid = "uid-admin").user,
            ),
        )
        assertTrue(
            canOpenRoomMemberManagementFromProfile(
                room = adminRoom,
                currentUser = currentUser,
                target = _member("member", uid = "uid-member").user,
            ),
        )
        assertFalse(
            canOpenRoomMemberManagementFromProfile(
                room = adminRoom,
                currentUser = currentUser,
                target = _member("admin", role = "admin", uid = "uid-admin").user,
            ),
        )
        assertFalse(
            canOpenRoomMemberManagementFromProfile(
                room = ownerRoom,
                currentUser = currentUser,
                target = _member("current", uid = "uid-current").user,
            ),
        )
        assertFalse(
            canOpenRoomMemberManagementFromProfile(
                room = ownerRoom,
                currentUser = currentUser,
                target = _member("member").user,
            ),
        )
        assertTrue(
            canOpenRoomMemberManagementFromProfile(
                room = adminRoom,
                currentUser = superuser,
                target = _member("owner", role = "owner", uid = "uid-owner").user,
            ),
        )
    }

    @Test
    fun `room member management action patches busy state`() {
        val room = _room("room_1")
        val members = listOf(_member("a"), _member("b"))

        val patch =
            roomMemberManagementActionStarted(
                room = room,
                members = members,
                changed = true,
                userId = "b",
                busyMemberIds = setOf("other"),
            )

        assertSame(room, patch.room)
        assertEquals(members, patch.members)
        assertEquals(setOf("other", "b"), patch.busyMemberIds)
        assertTrue(patch.changed)
        assertNull(patch.error)
        assertNull(patch.notice)
    }

    @Test
    fun `room member load patches loading members and error state`() {
        val currentMembers = listOf(_member("a"))

        val started = roomMembersLoadStarted(members = currentMembers)
        assertEquals(currentMembers, started.members)
        assertTrue(started.loading)
        assertNull(started.error)

        val loadedMembers = listOf(_member("b"), _member("c"))
        val succeeded = roomMembersLoadSucceeded(members = loadedMembers)
        assertEquals(loadedMembers, succeeded.members)
        assertFalse(succeeded.loading)
        assertNull(succeeded.error)

        val failed =
            roomMembersLoadFailed(
                members = currentMembers,
                failure = Exception("load failed"),
            )
        assertEquals(currentMembers, failed.members)
        assertFalse(failed.loading)
        assertEquals("操作失败，请稍后重试", failed.error)
    }

    @Test
    fun `room member role update patches members busy ids and notice`() {
        val room = _room("room_1")
        val members = listOf(_member("a"), _member("b"))
        val updated = _member("b", role = "admin")

        val succeeded =
            roomMemberRoleUpdateSucceeded(
                room = room,
                members = members,
                updated = updated,
                role = "admin",
                busyMemberIds = setOf("b", "other"),
            )
        assertSame(room, succeeded.room)
        assertEquals(listOf("a", "b"), succeeded.members.map { member -> member.user.id })
        assertSame(updated, succeeded.members[1])
        assertEquals(setOf("other"), succeeded.busyMemberIds)
        assertTrue(succeeded.changed)
        assertNull(succeeded.error)
        assertEquals("已授予管理员身份", succeeded.notice)

        val failed =
            roomMemberRoleUpdateFailed(
                room = room,
                members = members,
                changed = false,
                userId = "b",
                busyMemberIds = setOf("b", "other"),
                failure = Exception("role failed"),
            )
        assertSame(room, failed.room)
        assertEquals(listOf("a", "b"), failed.members.map { member -> member.user.id })
        assertEquals(setOf("other"), failed.busyMemberIds)
        assertFalse(failed.changed)
        assertEquals("操作失败，请稍后重试", failed.error)
        assertNull(failed.notice)
    }

    @Test
    fun `room member remove patches members busy ids and notice`() {
        val room = _room("room_1")
        val removed = _member("b", roomDisplayName = "Removed Target")
        val members = listOf(_member("a"), removed, _member("c"))

        val succeeded =
            roomMemberRemovedSucceeded(
                room = room,
                members = members,
                removed = removed,
                busyMemberIds = setOf("b", "other"),
            )
        assertSame(room, succeeded.room)
        assertEquals(listOf("a", "c"), succeeded.members.map { member -> member.user.id })
        assertEquals(setOf("other"), succeeded.busyMemberIds)
        assertTrue(succeeded.changed)
        assertNull(succeeded.error)
        assertEquals("已踢出 Removed Target", succeeded.notice)

        val failed =
            roomMemberRemoveFailed(
                room = room,
                members = members,
                changed = true,
                userId = "b",
                busyMemberIds = setOf("b", "other"),
                failure = Exception("remove failed"),
            )
        assertSame(room, failed.room)
        assertEquals(members, failed.members)
        assertEquals(setOf("other"), failed.busyMemberIds)
        assertTrue(failed.changed)
        assertEquals("操作失败，请稍后重试", failed.error)
        assertNull(failed.notice)
    }

    @Test
    fun `transfer creator patches room busy ids and notice`() {
        val room = _room("room_1")
        val updatedRoom = _room("room_1", role = "admin")
        val members = listOf(_member("a"), _member("b"))

        val succeeded =
            transferCreatorSucceeded(
                updatedRoom = updatedRoom,
                members = members,
                userId = "b",
                busyMemberIds = setOf("b", "other"),
            )
        assertSame(updatedRoom, succeeded.room)
        assertEquals(members, succeeded.members)
        assertEquals(setOf("other"), succeeded.busyMemberIds)
        assertTrue(succeeded.changed)
        assertNull(succeeded.error)
        assertEquals("创建者已转让", succeeded.notice)

        val failed =
            transferCreatorFailed(
                room = room,
                members = members,
                changed = true,
                userId = "b",
                busyMemberIds = setOf("b", "other"),
                failure = Exception("transfer failed"),
            )
        assertSame(room, failed.room)
        assertEquals(members, failed.members)
        assertEquals(setOf("other"), failed.busyMemberIds)
        assertTrue(failed.changed)
        assertEquals("操作失败，请稍后重试", failed.error)
        assertNull(failed.notice)
    }

    private fun _member(
        id: String,
        role: String = "member",
        uid: String? = null,
        displayName: String? = null,
        roomDisplayName: String? = null,
        userRoomDisplayName: String? = null,
        remarkName: String? = null,
        isOnline: Boolean = true,
        isSuperuser: Boolean = false,
    ): RoomMember {
        val user =
            UserSummary(
                id = id,
                username = "user_$id",
                displayName = displayName ?: "User $id",
                avatarUrl = null,
                defaultAvatarKey = "blue-3",
                uid = uid,
                roomDisplayName = userRoomDisplayName ?: roomDisplayName,
                roomRole = role,
                isSuperuser = isSuperuser,
            )
        return RoomMember(
            user = user,
            role = role,
            joinedAt = DateTime.utc(2026, 6, 4),
            roomDisplayName = roomDisplayName,
            remarkName = remarkName,
            isOnline = isOnline,
        )
    }

    private fun _live(participantIds: List<String> = emptyList()): LiveState {
        return LiveState(
            roomId = "room_1",
            participantCount = participantIds.size,
            participants =
                participantIds.map { id ->
                    LiveParticipant(
                        liveSessionId = "live_$id",
                        user = _user(id),
                        joinedAt = DateTime.utc(2026, 6, 4),
                        micMuted = true,
                        headphonesMuted = false,
                        voiceBlocked = false,
                        cameraOn = false,
                        screenSharing = false,
                        connectionState = "connected",
                    )
                },
            updatedAt = DateTime.utc(2026, 6, 4),
        )
    }

    private fun _user(id: String): UserSummary {
        return UserSummary(
            id = id,
            username = "user_$id",
            displayName = "User $id",
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
        )
    }

    private fun _currentUser(id: String, isSuperuser: Boolean = false): CurrentUser {
        return CurrentUser(
            id = id,
            uid = id,
            username = "user_$id",
            displayName = "User $id",
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
        )
    }

    private fun _room(id: String, role: String = "member"): RoomDetail {
        return RoomDetail(
            id = id,
            name = id,
            avatarUrl = null,
            defaultAvatarKey = "room-1",
            memberCount = 2,
            myMembership =
                RoomMembership(
                    joinedAt = DateTime.utc(2026, 6, 4),
                    role = role,
                ),
            live = _live(),
            createdAt = DateTime.utc(2026, 6, 4),
            updatedAt = DateTime.utc(2026, 6, 4),
        )
    }
}
