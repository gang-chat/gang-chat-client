package com.gangchat.client.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomBadgesTest {
    @Test
    fun `room navigation badge reset is a no-op when already clear`() {
        assertNull(
            roomNavigationBadgesReset(
                currentHasPendingRoomInvites = false,
                currentSelectedRoomHasPendingJoinRequests = false,
            ),
        )

        val patch =
            roomNavigationBadgesReset(
                currentHasPendingRoomInvites = true,
                currentSelectedRoomHasPendingJoinRequests = true,
            )

        assertEquals(false, patch?.hasPendingRoomInvites)
        assertEquals(false, patch?.selectedRoomHasPendingJoinRequests)
    }

    @Test
    fun `room invite badge update preserves selected join request badge`() {
        assertNull(
            roomInviteBadgeUpdated(
                currentHasPendingRoomInvites = true,
                currentSelectedRoomHasPendingJoinRequests = false,
                hasPending = true,
            ),
        )

        val patch =
            roomInviteBadgeUpdated(
                currentHasPendingRoomInvites = false,
                currentSelectedRoomHasPendingJoinRequests = true,
                hasPending = true,
            )

        assertEquals(true, patch?.hasPendingRoomInvites)
        assertEquals(true, patch?.selectedRoomHasPendingJoinRequests)
    }

    @Test
    fun `selected join request badge update preserves room invite badge`() {
        assertNull(
            selectedJoinRequestBadgeUpdated(
                currentHasPendingRoomInvites = true,
                currentSelectedRoomHasPendingJoinRequests = false,
                hasPending = false,
            ),
        )

        val patch =
            selectedJoinRequestBadgeUpdated(
                currentHasPendingRoomInvites = true,
                currentSelectedRoomHasPendingJoinRequests = false,
                hasPending = true,
            )

        assertEquals(true, patch?.hasPendingRoomInvites)
        assertEquals(true, patch?.selectedRoomHasPendingJoinRequests)
    }
}
