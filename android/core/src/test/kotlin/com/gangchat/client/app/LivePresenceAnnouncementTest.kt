package com.gangchat.client.app

import com.gangchat.client.protocol.UserSummary
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

    @Test
    fun `presence announcement uses role and room display name as segments`() {
        val announcement =
            livePresenceAnnouncementForUser(
                user =
                    UserSummary(
                        id = "user-2",
                        username = "morgan",
                        displayName = "Morgan Account",
                        roomDisplayName = "房间里的 Morgan",
                        roomRole = "admin",
                        avatarUrl = null,
                        defaultAvatarKey = "blue-1",
                    ),
                action = LivePresenceAnnouncementAction.joined,
            )

        assertEquals(listOf("管理员", "房间里的 Morgan", "进入了语音频道"), announcement.segments)
    }

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

    @Test
    fun `presence announcement falls back to account display name`() {
        val announcement =
            livePresenceAnnouncementForUser(
                user =
                    UserSummary(
                        id = "user-2",
                        username = "morgan",
                        displayName = "Morgan",
                        avatarUrl = null,
                        defaultAvatarKey = "blue-1",
                    ),
                action = LivePresenceAnnouncementAction.left,
            )

        assertEquals(listOf("成员", "Morgan", "离开了语音频道"), announcement.segments)
    }
}
