package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.JoinRequest
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomMembership
import com.gangchat.client.protocol.UserSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomJoinRequestsTest {
    @Test
    fun `joinRequestCandidates marks busy requests and pending info role`() {
        val candidates =
            joinRequestCandidates(
                requests = listOf(_request("a"), _request("b")),
                busyRequestIds = listOf("b"),
            )

        assertEquals(listOf("a", "b"), candidates.map { item -> item.request.id })
        assertFalse(candidates[0].busy)
        assertTrue(candidates[0].canReview)
        assertEquals("pending", candidates[0].userForInfo.roomRole)
        assertTrue(candidates[1].busy)
        assertFalse(candidates[1].canReview)
    }

    @Test
    fun `canStartJoinRequestReview rejects busy request ids`() {
        assertFalse(
            canStartJoinRequestReview(
                requestId = "busy",
                busyRequestIds = listOf("busy"),
            ),
        )
        assertTrue(canStartJoinRequestReview(requestId = "idle"))
    }

    @Test
    fun `joinRequestReasonText returns trimmed reason only when present`() {
        assertEquals(
            "hello",
            joinRequestReasonText(_request("with_reason", reason = "  hello  ")),
        )
        assertNull(joinRequestReasonText(_request("blank_reason", reason = "  ")))
        assertEquals("未填写", joinRequestDetailReasonText(_request("blank_reason")))
    }

    @Test
    fun `join request source helpers describe public search and invitations`() {
        val publicSearch = _request("public")
        assertFalse(joinRequestFromInvitation(publicSearch))
        assertEquals("公开房间搜索", joinRequestSourceText(publicSearch))

        val invited =
            _request(
                "invited",
                source = "invitation",
                inviters = listOf(_user("inviter_1", displayName = "Inviter One")),
            )
        assertTrue(joinRequestFromInvitation(invited))
        assertEquals("Inviter One 的邀请", joinRequestSourceText(invited))
    }

    @Test
    fun `join request badge refresh gate follows room and permission`() {
        assertTrue(
            shouldRefreshJoinRequestBadgeForEvent(
                data = mapOf("room_id" to "room_1"),
                selectedRoomId = "room_1",
                canReviewJoinRequests = true,
            ),
        )
        assertFalse(
            shouldRefreshJoinRequestBadgeForEvent(
                data = mapOf("room_id" to "room_2"),
                selectedRoomId = "room_1",
                canReviewJoinRequests = true,
            ),
        )
        assertFalse(
            shouldRefreshJoinRequestBadgeForEvent(
                data = mapOf("room_id" to "room_1"),
                selectedRoomId = "room_1",
                canReviewJoinRequests = false,
            ),
        )
        assertFalse(
            shouldRefreshJoinRequestBadgeForEvent(
                data = emptyMap(),
                selectedRoomId = "room_1",
                canReviewJoinRequests = true,
            ),
        )
    }

    @Test
    fun `join request badge refresh target follows room role`() {
        val adminRoom = _room("room_1", role = "admin")
        val memberRoom = _room("room_2", role = "member")

        assertSame(
            adminRoom,
            joinRequestBadgeRefreshTarget(
                room = adminRoom,
                canReviewJoinRequests = true,
            ),
        )
        assertNull(
            joinRequestBadgeRefreshTarget(
                room = memberRoom,
                canReviewJoinRequests = false,
            ),
        )
        assertSame(
            memberRoom,
            joinRequestBadgeRefreshTarget(
                room = memberRoom,
                canReviewJoinRequests = true,
            ),
        )
        assertNull(joinRequestBadgeRefreshTarget(room = null, canReviewJoinRequests = true))

        assertTrue(
            canApplyJoinRequestBadgeRefresh(
                targetRoomId = "room_1",
                selectedRoomId = "room_1",
            ),
        )
        assertFalse(
            canApplyJoinRequestBadgeRefresh(
                targetRoomId = "room_1",
                selectedRoomId = "room_2",
            ),
        )
    }

    @Test
    fun `join request review reducers update requests and busy ids`() {
        val requests = listOf(_request("a"), _request("b"))

        val started =
            joinRequestReviewStarted(
                requests = requests,
                requestId = "a",
                busyRequestIds = listOf("other"),
            )
        assertEquals(listOf("a", "b"), started.requests.map { request -> request.id })
        assertEquals(setOf("other", "a"), started.busyRequestIds)
        assertFalse(started.changed)
        assertTrue(started.hasPendingRequests)

        val succeeded =
            joinRequestReviewSucceeded(
                requests = started.requests,
                requestId = "a",
                busyRequestIds = started.busyRequestIds,
            )
        assertEquals(listOf("b"), succeeded.requests.map { request -> request.id })
        assertEquals(setOf("other"), succeeded.busyRequestIds)
        assertTrue(succeeded.changed)
        assertTrue(succeeded.hasPendingRequests)

        val failed =
            joinRequestReviewFailed(
                requests = started.requests,
                requestId = "a",
                busyRequestIds = started.busyRequestIds,
            )
        assertEquals(listOf("a", "b"), failed.requests.map { request -> request.id })
        assertEquals(setOf("other"), failed.busyRequestIds)
        assertFalse(failed.changed)
    }

    @Test
    fun `joinRequestListBodyState separates empty and result lists`() {
        assertEquals(JoinRequestListBodyState.empty, joinRequestListBodyState(emptyList()))

        assertEquals(
            JoinRequestListBodyState.results,
            joinRequestListBodyState(
                joinRequestCandidates(requests = listOf(_request("a"))),
            ),
        )
    }

    private fun _request(
        id: String,
        user: UserSummary? = null,
        reason: String = "",
        source: String = "public_search",
        inviters: List<UserSummary> = emptyList(),
    ): JoinRequest {
        return JoinRequest(
            id = id,
            status = "pending",
            reason = reason,
            source = source,
            inviters = inviters,
            user = user ?: _user("user_$id"),
            createdAt = DateTime.utc(2026, 6, 5),
        )
    }

    private fun _user(id: String, displayName: String? = null): UserSummary {
        return UserSummary(
            id = id,
            username = id,
            displayName = displayName ?: "User $id",
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
        )
    }

    private fun _room(id: String, role: String): RoomDetail {
        return RoomDetail(
            id = id,
            name = id,
            avatarUrl = null,
            defaultAvatarKey = "room-1",
            memberCount = 1,
            myMembership =
                RoomMembership(
                    joinedAt = DateTime.utc(2026, 6, 5),
                    role = role,
                ),
            live =
                LiveState(
                    roomId = id,
                    participantCount = 0,
                    participants = emptyList(),
                    updatedAt = DateTime.utc(2026, 6, 5),
                ),
            createdAt = DateTime.utc(2026, 6, 5),
            updatedAt = DateTime.utc(2026, 6, 5),
        )
    }
}
