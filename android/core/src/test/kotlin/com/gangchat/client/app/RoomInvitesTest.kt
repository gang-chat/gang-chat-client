package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.JoinRoomResult
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomInvite
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.UserCommonRoom
import com.gangchat.client.protocol.UserSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomInvitesTest {
    @Test
    fun `roomInviteCandidates combines search results and matching members`() {
        val candidates =
            roomInviteCandidates(
                searchResults =
                    listOf(
                        _user("external", displayName = "Alpha External"),
                        _user("member", displayName = "Alpha Member"),
                    ),
                members =
                    listOf(
                        _member("member", uid = "1001"),
                        _member("remark", remarkName = "Alpha note"),
                    ),
                query = "alpha",
            )

        assertEquals(
            listOf("external", "member", "remark"),
            candidates.map { item -> item.user.id },
        )
        assertFalse(candidates[0].existing)
        assertTrue(candidates[1].existing)
        assertTrue(candidates[2].existing)
    }

    @Test
    fun `roomInviteCandidates ignores room names in search results and members`() {
        val candidates =
            roomInviteCandidates(
                searchResults =
                    listOf(
                        _user(
                            "room_context",
                            roomDisplayName = "Alpha Room Alias",
                            commonRooms =
                                listOf(
                                    UserCommonRoom(
                                        id = "room_1",
                                        rid = "R10001",
                                        name = "Alpha Common Room",
                                        roomDisplayName = "Alpha Shared Name",
                                    ),
                                ),
                        ),
                        _user("display_match", displayName = "Alpha Person"),
                    ),
                members =
                    listOf(
                        _member("room_member", roomDisplayName = "Alpha Current Room Name"),
                    ),
                query = "alpha",
            )

        assertEquals(listOf("display_match"), candidates.map { item -> item.user.id })
    }

    @Test
    fun `roomInviteCandidates marks pending and busy invite states`() {
        val candidates =
            roomInviteCandidates(
                searchResults = listOf(_user("pending"), _user("busy")),
                members = emptyList(),
                query = "user",
                pendingInviteUserIds = listOf("pending"),
                busyUserIds = listOf("busy"),
            )

        assertTrue(candidates[0].pending)
        assertFalse(candidates[0].inviteActionEnabled)
        assertFalse(candidates[0].canStartInvite)
        assertTrue(candidates[1].busy)
        assertTrue(candidates[1].inviteActionEnabled)
        assertFalse(candidates[1].canStartInvite)
    }

    @Test
    fun `roomInviteCandidates filters out superusers`() {
        val candidates =
            roomInviteCandidates(
                searchResults = listOf(_user("super", isSuperuser = true), _user("normal")),
                members = listOf(_member("member_super", isSuperuser = true), _member("member")),
                query = "user",
            )

        assertEquals(listOf("normal", "member"), candidates.map { item -> item.user.id })
    }

    @Test
    fun `roomInviteSearchBodyState describes loading prompt empty and results`() {
        val candidate =
            RoomInviteCandidate(
                user = _user("user_1"),
                existing = false,
                pending = false,
                busy = false,
            )

        assertEquals(
            RoomInviteSearchBodyState.loading,
            roomInviteSearchBodyState(
                searching = true,
                query = "",
                candidates = emptyList(),
            ),
        )
        assertEquals(
            RoomInviteSearchBodyState.prompt,
            roomInviteSearchBodyState(
                searching = false,
                query = "   ",
                candidates = listOf(candidate),
            ),
        )
        assertEquals(
            RoomInviteSearchBodyState.empty,
            roomInviteSearchBodyState(
                searching = false,
                query = "logan",
                candidates = emptyList(),
            ),
        )
        assertEquals(
            RoomInviteSearchBodyState.results,
            roomInviteSearchBodyState(
                searching = false,
                query = "logan",
                candidates = listOf(candidate),
            ),
        )
    }

    @Test
    fun `canStartRoomInvite rejects existing pending and busy users`() {
        val members = listOf(_member("existing"))

        assertTrue(roomInvitesEnabled("open"))
        assertTrue(roomInvitesEnabled("approval_required"))
        assertFalse(roomInvitesEnabled("closed"))
        assertFalse(canStartRoomInvite(userId = "existing", members = members))
        assertFalse(
            canStartRoomInvite(
                userId = "pending",
                members = members,
                pendingInviteUserIds = listOf("pending"),
            ),
        )
        assertFalse(
            canStartRoomInvite(
                userId = "busy",
                members = members,
                busyUserIds = listOf("busy"),
            ),
        )
        assertTrue(canStartRoomInvite(userId = "new", members = members))
        assertFalse(
            canStartRoomInvite(
                userId = "new",
                members = members,
                invitesEnabled = false,
            ),
        )
        assertFalse(
            canStartRoomInvite(userId = "super", members = members, isSuperuser = true),
        )
    }

    @Test
    fun `roomInviteCandidates disables invite actions when room is closed`() {
        val candidates =
            roomInviteCandidates(
                searchResults = listOf(_user("new")),
                members = emptyList(),
                query = "user",
                invitesEnabled = false,
            )

        assertTrue(candidates.single().existing)
        assertFalse(candidates.single().inviteActionEnabled)
        assertFalse(candidates.single().canStartInvite)
    }

    @Test
    fun `room member invite action reducers update pending and busy ids`() {
        val started =
            roomMemberInviteActionStarted(
                userId = "new",
                pendingInviteUserIds = listOf("pending"),
                busyUserIds = listOf("other_busy"),
            )
        assertEquals(setOf("pending"), started.pendingInviteUserIds)
        assertEquals(setOf("other_busy", "new"), started.busyUserIds)

        val succeeded =
            roomMemberInviteActionSucceeded(
                userId = "new",
                pendingInviteUserIds = started.pendingInviteUserIds,
                busyUserIds = started.busyUserIds,
            )
        assertEquals(setOf("pending", "new"), succeeded.pendingInviteUserIds)
        assertEquals(setOf("other_busy"), succeeded.busyUserIds)

        val failed =
            roomMemberInviteActionFailed(
                userId = "new",
                pendingInviteUserIds = started.pendingInviteUserIds,
                busyUserIds = started.busyUserIds,
            )
        assertEquals(setOf("pending"), failed.pendingInviteUserIds)
        assertEquals(setOf("other_busy"), failed.busyUserIds)
    }

    @Test
    fun `room member invite dialog search patches preserve ui state`() {
        val existingResults = listOf(_user("existing"))

        val cleared =
            roomMemberInviteSearchCleared(
                pendingInviteUserIds = listOf("pending"),
                busyUserIds = listOf("busy"),
            )
        assertTrue(cleared.searchResults.isEmpty())
        assertFalse(cleared.searching)
        assertNull(cleared.error)
        assertEquals(setOf("pending"), cleared.pendingInviteUserIds)
        assertEquals(setOf("busy"), cleared.busyUserIds)

        val started =
            roomMemberInviteSearchStarted(
                searchResults = existingResults,
                pendingInviteUserIds = listOf("pending"),
                busyUserIds = listOf("busy"),
            )
        assertEquals(existingResults, started.searchResults)
        assertTrue(started.searching)
        assertNull(started.error)
        assertEquals(setOf("pending"), started.pendingInviteUserIds)
        assertEquals(setOf("busy"), started.busyUserIds)

        val inputStarted =
            roomMemberInviteSearchQueryChanged(
                rawQuery = "  user  ",
                searchResults = existingResults,
                pendingInviteUserIds = listOf("pending"),
                busyUserIds = listOf("busy"),
            )
        assertEquals("user", inputStarted.query)
        assertTrue(inputStarted.shouldSearch)
        assertTrue(inputStarted.shouldCancelInFlightSearch)
        assertEquals(existingResults, inputStarted.dialog.searchResults)
        assertTrue(inputStarted.dialog.searching)
        assertEquals(setOf("pending"), inputStarted.dialog.pendingInviteUserIds)
        assertEquals(setOf("busy"), inputStarted.dialog.busyUserIds)

        val inputCleared =
            roomMemberInviteSearchQueryChanged(
                rawQuery = "   ",
                searchResults = existingResults,
                pendingInviteUserIds = listOf("pending"),
                busyUserIds = listOf("busy"),
            )
        assertEquals("", inputCleared.query)
        assertFalse(inputCleared.shouldSearch)
        assertTrue(inputCleared.shouldCancelInFlightSearch)
        assertTrue(inputCleared.dialog.searchResults.isEmpty())
        assertFalse(inputCleared.dialog.searching)
        assertNull(inputCleared.dialog.error)
        assertEquals(setOf("pending"), inputCleared.dialog.pendingInviteUserIds)
        assertEquals(setOf("busy"), inputCleared.dialog.busyUserIds)

        val succeeded =
            roomMemberInviteSearchSucceeded(
                searchResults = listOf(_user("result")),
                pendingInviteUserIds = listOf("pending"),
                busyUserIds = listOf("busy"),
            )
        assertEquals(listOf("result"), succeeded.searchResults.map { user -> user.id })
        assertFalse(succeeded.searching)
        assertNull(succeeded.error)
        assertEquals(setOf("pending"), succeeded.pendingInviteUserIds)
        assertEquals(setOf("busy"), succeeded.busyUserIds)

        val failed =
            roomMemberInviteSearchFailed(
                searchResults = existingResults,
                pendingInviteUserIds = listOf("pending"),
                busyUserIds = listOf("busy"),
                failure = Exception("search failed"),
            )
        assertEquals(existingResults, failed.searchResults)
        assertFalse(failed.searching)
        assertEquals("操作失败，请稍后重试", failed.error)
        assertEquals(setOf("pending"), failed.pendingInviteUserIds)
        assertEquals(setOf("busy"), failed.busyUserIds)
    }

    @Test
    fun `room member invite dialog action patches update invite state`() {
        val results = listOf(_user("new"))

        val started =
            roomMemberInviteStarted(
                searchResults = results,
                searching = false,
                userId = "new",
                pendingInviteUserIds = listOf("pending"),
                busyUserIds = listOf("other_busy"),
            )
        assertEquals(results, started.searchResults)
        assertFalse(started.searching)
        assertNull(started.error)
        assertEquals(setOf("pending"), started.pendingInviteUserIds)
        assertEquals(setOf("other_busy", "new"), started.busyUserIds)

        val succeeded =
            roomMemberInviteSucceeded(
                searchResults = results,
                searching = true,
                error = "kept",
                userId = "new",
                pendingInviteUserIds = started.pendingInviteUserIds,
                busyUserIds = started.busyUserIds,
            )
        assertEquals(results, succeeded.searchResults)
        assertTrue(succeeded.searching)
        assertEquals("kept", succeeded.error)
        assertEquals(setOf("pending", "new"), succeeded.pendingInviteUserIds)
        assertEquals(setOf("other_busy"), succeeded.busyUserIds)

        val failed =
            roomMemberInviteFailed(
                searchResults = results,
                searching = true,
                userId = "new",
                pendingInviteUserIds = started.pendingInviteUserIds,
                busyUserIds = started.busyUserIds,
                failure = Exception("invite failed"),
            )
        assertEquals(results, failed.searchResults)
        assertTrue(failed.searching)
        assertEquals("操作失败，请稍后重试", failed.error)
        assertEquals(setOf("pending"), failed.pendingInviteUserIds)
        assertEquals(setOf("other_busy"), failed.busyUserIds)
    }

    @Test
    fun `reviewedRoomInvites removes invite and tracks pending room ids`() {
        val invite = _invite("invite_1", roomId = "room_1")
        val relatedInvite = _invite("invite_3", roomId = "room_1")
        val state =
            reviewedRoomInvites(
                invites =
                    listOf(
                        invite,
                        _invite("invite_2", roomId = "room_2"),
                        relatedInvite,
                    ),
                pendingRoomIds = listOf("existing_room"),
                invite = invite,
                accept = true,
                result = JoinRoomResult(pending = true),
            )

        assertEquals(listOf("invite_2"), state.invites.map { item -> item.id })
        assertEquals(setOf("existing_room", "room_1"), state.pendingRoomIds)
        assertTrue(state.hasPendingInvites)

        val rejected =
            reviewedRoomInvites(
                invites = listOf(invite, relatedInvite),
                pendingRoomIds = emptyList(),
                invite = invite,
                accept = false,
                result = JoinRoomResult(),
            )

        assertEquals(listOf("invite_3"), rejected.invites.map { item -> item.id })
        assertTrue(rejected.pendingRoomIds.isEmpty())
        assertTrue(rejected.hasPendingInvites)
    }

    private fun _member(
        id: String,
        uid: String? = null,
        remarkName: String? = null,
        roomDisplayName: String? = null,
        isSuperuser: Boolean = false,
    ): RoomMember {
        return RoomMember(
            user = _user(id, uid = uid, isSuperuser = isSuperuser),
            role = "member",
            joinedAt = DateTime.utc(2026, 6, 5),
            roomDisplayName = roomDisplayName,
            remarkName = remarkName,
        )
    }

    private fun _user(
        id: String,
        uid: String? = null,
        displayName: String? = null,
        roomDisplayName: String? = null,
        commonRooms: List<UserCommonRoom> = emptyList(),
        isSuperuser: Boolean = false,
    ): UserSummary {
        return UserSummary(
            id = id,
            username = "user_$id",
            displayName = displayName ?: "User $id",
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
            uid = uid,
            roomDisplayName = roomDisplayName,
            commonRooms = commonRooms,
            isSuperuser = isSuperuser,
        )
    }

    private fun _invite(id: String, roomId: String): RoomInvite {
        return RoomInvite(
            id = id,
            status = "pending",
            room =
                PublicRoom(
                    id = roomId,
                    rid = roomId,
                    name = roomId,
                    avatarUrl = null,
                    defaultAvatarKey = "room-1",
                    visibility = "public",
                    joinPolicy = "approval_required",
                    memberCount = 3,
                    liveParticipantCount = 0,
                    joined = false,
                    joinState = "none",
                ),
            inviter = _user("inviter_$id"),
            createdAt = DateTime.utc(2026, 6, 5),
        )
    }
}
