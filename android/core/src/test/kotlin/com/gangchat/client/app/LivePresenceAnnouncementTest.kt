package com.gangchat.client.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePresenceAnnouncementTest {
    @Test
    fun `presence speech excludes self and obeys the room switch`() {
        assertFalse(
            shouldSpeakLivePresenceAnnouncement(
                enabled = true,
                participantIdentity = "user-1",
                currentUserIdentity = "user-1",
            ),
        )
        assertFalse(
            shouldSpeakLivePresenceAnnouncement(
                enabled = false,
                participantIdentity = "user-2",
                currentUserIdentity = "user-1",
            ),
        )
        assertTrue(
            shouldSpeakLivePresenceAnnouncement(
                enabled = true,
                participantIdentity = "user-2",
                currentUserIdentity = "user-1",
            ),
        )
    }

    // 'presence announcement uses role and room display name as segments' and
    // 'presence announcement falls back to account display name' test
    // livePresenceAnnouncementForUser (room_display.dart) with UserSummary
    // (protocol/models.dart); they move with room_display's port.

    @Test
    fun `presence announcement distinguishes leave from removal`() {
        val left =
            LivePresenceAnnouncement(
                roleLabel = "成员",
                roomDisplayName = "小林",
                action = LivePresenceAnnouncementAction.left,
            )
        val removed =
            LivePresenceAnnouncement(
                roleLabel = "成员",
                roomDisplayName = "小林",
                action = LivePresenceAnnouncementAction.removed,
            )

        assertEquals("离开了语音频道", left.actionLabel)
        assertEquals("被踢出了语音频道", removed.actionLabel)
    }
}
