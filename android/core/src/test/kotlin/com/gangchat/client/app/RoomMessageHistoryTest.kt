package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.UserSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomMessageHistoryTest {
    @Test
    fun `history categories expose stable server values`() {
        assertEquals(
            listOf(
                "all",
                "links",
                "voice",
                "stickers",
                "images",
                "files",
                "music",
                "system",
            ),
            RoomMessageHistoryCategory.entries.map { category -> category.apiValue },
        )
    }

    @Test
    fun `history copy contains only message content`() {
        val message =
            Message(
                id = "msg_1",
                roomId = "room_1",
                sender =
                    UserSummary(
                        id = "user_1",
                        username = "alice",
                        displayName = "Alice",
                        avatarUrl = null,
                        defaultAvatarKey = "blue-3",
                    ),
                clientMessageId = "client_1",
                body = "  hello world  ",
                createdAt = DateTime.utc(2026, 7, 13, 8, 30),
            )

        assertEquals("hello world", roomMessageHistoryCopyText(message))
    }

    @Test
    fun `date filtering uses a local inclusive end day`() {
        val day = DateTime(2026, 7, 13, 20, 30)

        assertEquals(DateTime(2026, 7, 13), roomMessageHistoryDayStart(day))
        assertEquals(DateTime(2026, 7, 14), roomMessageHistoryDayEndExclusive(day))
    }
}
