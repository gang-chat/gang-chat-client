package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.JoinRoomResult
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomInvite
import com.gangchat.client.protocol.RoomMembership
import com.gangchat.client.protocol.UserSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomJoinTest {
    @Test
    fun `publicRoomJoinCandidates marks pending from server and local state`() {
        val candidates =
            publicRoomJoinCandidates(
                rooms =
                    listOf(
                        _room("joined", joined = true),
                        _room("server_pending", joinState = "pending"),
                        _room("local_pending"),
                        _room("open"),
                    ),
                pendingRoomIds = listOf("local_pending"),
            )

        assertEquals(
            listOf(false, true, true, false),
            candidates.map { item -> item.pending },
        )
        assertTrue(candidates[0].opensJoinedRoom)
        assertFalse(candidates[1].actionable)
        assertTrue(candidates[3].actionable)
    }

    @Test
    fun `publicRoomJoinCandidates disables only the busy room action`() {
        val candidates =
            publicRoomJoinCandidates(
                rooms = listOf(_room("busy"), _room("idle")),
                busyRoomId = "busy",
            )

        assertTrue(candidates[0].busy)
        assertFalse(candidates[0].actionEnabled)
        assertFalse(candidates[1].busy)
        assertTrue(candidates[1].actionEnabled)
    }

    @Test
    fun `pendingRoomIdsAfterJoinResult tracks approval-required joins`() {
        assertEquals(
            setOf("existing", "room_1"),
            pendingRoomIdsAfterJoinResult(
                pendingRoomIds = listOf("existing"),
                room = _room("room_1"),
                result = JoinRoomResult(pending = true),
            ),
        )
        assertEquals(
            setOf("existing"),
            pendingRoomIdsAfterJoinResult(
                pendingRoomIds = listOf("existing"),
                room = _room("room_1"),
                result = JoinRoomResult(room = _roomDetail()),
            ),
        )
    }

    @Test
    fun `joinRequestReasonValue trims optional application text`() {
        assertEquals("hello team", joinRequestReasonValue("  hello team  "))
        assertNull(joinRequestReasonValue("   "))
    }

    @Test
    fun `join dialog search and invite list patches preserve state semantics`() {
        val existingResults = listOf(_room("existing"))
        val invites = listOf(_invite("invite_1"))

        val cleared = roomJoinSearchCleared()
        assertTrue(cleared.results.isEmpty())
        assertFalse(cleared.searching)
        assertNull(cleared.error)

        val searchStarted =
            roomJoinSearchStarted(
                results = existingResults,
                error = "stale error",
            )
        assertEquals(listOf("existing"), searchStarted.results.map { room -> room.id })
        assertTrue(searchStarted.searching)
        assertEquals("stale error", searchStarted.error)

        val inputStarted =
            roomJoinSearchQueryChanged(
                rawQuery = "  room  ",
                results = existingResults,
                error = "stale error",
            )
        assertEquals("room", inputStarted.query)
        assertTrue(inputStarted.shouldSearch)
        assertTrue(inputStarted.shouldCancelInFlightSearch)
        assertEquals(listOf("existing"), inputStarted.search.results.map { room -> room.id })
        assertTrue(inputStarted.search.searching)
        assertEquals("stale error", inputStarted.search.error)

        val inputCleared =
            roomJoinSearchQueryChanged(
                rawQuery = "   ",
                results = existingResults,
                error = "stale error",
            )
        assertEquals("", inputCleared.query)
        assertFalse(inputCleared.shouldSearch)
        assertTrue(inputCleared.shouldCancelInFlightSearch)
        assertTrue(inputCleared.search.results.isEmpty())
        assertFalse(inputCleared.search.searching)
        assertNull(inputCleared.search.error)

        val searchSucceeded =
            roomJoinSearchSucceeded(
                results = listOf(_room("room_2")),
            )
        assertEquals("room_2", searchSucceeded.results.single().id)
        assertFalse(searchSucceeded.searching)
        assertNull(searchSucceeded.error)

        val searchFailed =
            roomJoinSearchFailed(
                results = existingResults,
                failure = "search failed",
            )
        assertEquals(listOf("existing"), searchFailed.results.map { room -> room.id })
        assertFalse(searchFailed.searching)
        assertEquals("操作失败，请稍后重试", searchFailed.error)

        val invitesStarted = roomJoinInvitesLoadStarted(invites = invites)
        assertEquals(listOf("invite_1"), invitesStarted.invites.map { invite -> invite.id })
        assertTrue(invitesStarted.loading)
        assertNull(invitesStarted.error)

        val invitesSucceeded = roomJoinInvitesLoadSucceeded(invites = invites)
        assertEquals(listOf("invite_1"), invitesSucceeded.invites.map { invite -> invite.id })
        assertFalse(invitesSucceeded.loading)
        assertNull(invitesSucceeded.error)

        val invitesFailed =
            roomJoinInvitesLoadFailed(
                invites = invites,
                failure = "load failed",
            )
        assertEquals(listOf("invite_1"), invitesFailed.invites.map { invite -> invite.id })
        assertFalse(invitesFailed.loading)
        assertEquals("操作失败，请稍后重试", invitesFailed.error)
    }

    @Test
    fun `join dialog public room action patches track busy and pending state`() {
        val started =
            roomJoinPublicActionStarted(
                roomId = "room_1",
                pendingRoomIds = listOf("existing"),
            )
        assertEquals("room_1", started.busyRoomId)
        assertNull(started.error)
        assertEquals(setOf("existing"), started.pendingRoomIds)

        val pending =
            roomJoinPublicActionPending(
                busyRoomId = "room_1",
                error = null,
                pendingRoomIds = started.pendingRoomIds,
                room = _room("room_2"),
                result = JoinRoomResult(pending = true),
            )
        assertEquals("room_1", pending.busyRoomId)
        assertNull(pending.error)
        assertEquals(setOf("existing", "room_2"), pending.pendingRoomIds)

        val failed =
            roomJoinPublicActionFailed(
                busyRoomId = "room_1",
                pendingRoomIds = pending.pendingRoomIds,
                failure = "join failed",
            )
        assertEquals("room_1", failed.busyRoomId)
        assertEquals("操作失败，请稍后重试", failed.error)
        assertEquals(setOf("existing", "room_2"), failed.pendingRoomIds)

        val finished =
            roomJoinPublicActionFinished(
                error = failed.error,
                pendingRoomIds = failed.pendingRoomIds,
            )
        assertNull(finished.busyRoomId)
        assertEquals("操作失败，请稍后重试", finished.error)
        assertEquals(setOf("existing", "room_2"), finished.pendingRoomIds)
    }

    @Test
    fun `join dialog invite decision patches update invites and pending rooms`() {
        val invites = listOf(_invite("invite_1"), _invite("invite_2"))

        val started =
            roomJoinInviteDecisionStarted(
                invites = invites,
                pendingRoomIds = listOf("existing"),
                inviteId = "invite_1",
            )
        assertEquals(
            listOf("invite_1", "invite_2"),
            started.invites.map { invite -> invite.id },
        )
        assertEquals(setOf("existing"), started.pendingRoomIds)
        assertEquals("invite_1", started.busyInviteId)
        assertNull(started.error)

        val succeeded =
            roomJoinInviteDecisionSucceeded(
                invites = started.invites,
                pendingRoomIds = started.pendingRoomIds,
                invite = invites.first(),
                accept = true,
                result = JoinRoomResult(pending = true),
            )
        assertEquals(listOf("invite_2"), succeeded.invites.map { invite -> invite.id })
        assertEquals(setOf("existing", invites.first().room.id), succeeded.pendingRoomIds)
        assertNull(succeeded.busyInviteId)
        assertNull(succeeded.error)

        val failed =
            roomJoinInviteDecisionFailed(
                invites = started.invites,
                pendingRoomIds = started.pendingRoomIds,
                busyInviteId = started.busyInviteId,
                failure = "review failed",
            )
        assertEquals(
            listOf("invite_1", "invite_2"),
            failed.invites.map { invite -> invite.id },
        )
        assertEquals(setOf("existing"), failed.pendingRoomIds)
        assertEquals("invite_1", failed.busyInviteId)
        assertEquals("操作失败，请稍后重试", failed.error)

        val finished =
            roomJoinInviteDecisionFinished(
                invites = failed.invites,
                pendingRoomIds = failed.pendingRoomIds,
                error = failed.error,
            )
        assertEquals(
            listOf("invite_1", "invite_2"),
            finished.invites.map { invite -> invite.id },
        )
        assertEquals(setOf("existing"), finished.pendingRoomIds)
        assertNull(finished.busyInviteId)
        assertEquals("操作失败，请稍后重试", finished.error)
    }

    @Test
    fun `join dialog state helpers expose reusable gates`() {
        assertTrue(hasRoomSearchQuery("  room  "))
        assertFalse(hasRoomSearchQuery("   "))
        assertTrue(
            shouldShowRoomInviteSection(loadingInvites = true, invites = emptyList()),
        )
        assertTrue(
            shouldShowRoomInviteSection(
                loadingInvites = false,
                invites = listOf(_invite("invite_1")),
            ),
        )
        assertFalse(
            shouldShowRoomInviteSection(loadingInvites = false, invites = emptyList()),
        )
        assertTrue(canStartPublicRoomAction(null))
        assertFalse(canStartPublicRoomAction("room_1"))
        assertTrue(
            canStartRoomInviteDecision(busyInviteId = null, busyRoomId = null),
        )
        assertFalse(
            canStartRoomInviteDecision(busyInviteId = "invite_1", busyRoomId = null),
        )
        assertFalse(
            canStartRoomInviteDecision(busyInviteId = null, busyRoomId = "room_1"),
        )
    }

    @Test
    fun `join dialog display helpers format room and invite subtitles`() {
        assertEquals("1001 · 3 名成员", publicRoomJoinSubtitle(_room("1001")))
        assertEquals("3 名成员", publicRoomJoinSubtitle(_room("no_rid", rid = "")))
        assertEquals(
            "R002 · 3 名成员",
            publicRoomJoinSubtitle(_room("spaced", rid = "  R002  ")),
        )

        assertEquals(
            "邀请你加入 · RID R100",
            pendingRoomInviteSubtitle(_invite("invite_1", rid = "R100")),
        )
        assertEquals("邀请你加入", pendingRoomInviteSubtitle(_invite("invite_2", rid = "")))
    }

    private fun _room(
        id: String,
        rid: String? = null,
        joined: Boolean = false,
        joinPolicy: String = "open",
        joinState: String = "none",
    ): PublicRoom {
        return PublicRoom(
            id = id,
            rid = rid ?: id,
            name = "Room $id",
            avatarUrl = null,
            defaultAvatarKey = "room-1",
            visibility = "public",
            joinPolicy = joinPolicy,
            memberCount = 3,
            liveParticipantCount = 0,
            joined = joined,
            joinState = if (joined) "joined" else joinState,
        )
    }

    private fun _roomDetail(): RoomDetail {
        return RoomDetail(
            id = "room_1",
            name = "Room",
            rid = "R001",
            visibility = "public",
            avatarUrl = null,
            defaultAvatarKey = "room-1",
            memberCount = 1,
            description = "",
            createdBy = null,
            myMembership =
                RoomMembership(
                    joinedAt = DateTime.utc(2026, 6, 4),
                    role = "member",
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
        )
    }

    private fun _invite(id: String, rid: String? = null): RoomInvite {
        return RoomInvite(
            id = id,
            status = "pending",
            room = _room("room_$id", rid = rid),
            inviter =
                UserSummary(
                    id = "inviter",
                    username = "inviter",
                    displayName = "Inviter",
                    avatarUrl = null,
                    defaultAvatarKey = "blue-3",
                ),
            createdAt = DateTime.utc(2026, 6, 4),
        )
    }
}
