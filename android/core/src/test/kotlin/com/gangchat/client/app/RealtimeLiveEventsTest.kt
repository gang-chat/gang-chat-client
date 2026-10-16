package com.gangchat.client.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeLiveEventsTest {
    @Test
    fun `all server live reconnect snapshots use the live state route`() {
        assertTrue(
            listOf(
                "live_participant_reconnecting",
                "live_participant_reconnected",
                "live_participants_reconciled",
                "live_room_reconnecting",
            ).all(::isLiveSnapshotRealtimeEventType),
        )
    }

    @Test
    fun `unrelated realtime events stay on their dedicated routes`() {
        assertFalse(isLiveSnapshotRealtimeEventType("room_updated"))
        assertFalse(isLiveSnapshotRealtimeEventType("message_created"))
    }
}
