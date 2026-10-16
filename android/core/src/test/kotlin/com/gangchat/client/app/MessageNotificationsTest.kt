package com.gangchat.client.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageNotificationsTest {
    @Test
    fun `only explicit realtime message updates under all policy notify`() {
        val tracker = RealtimeMessageNotificationTracker()

        assertFalse(
            tracker.register(
                roomId = "room-1",
                updateReason = "",
                notificationPolicy = "all",
                messageId = "message-history",
            ),
        )
        assertTrue(
            tracker.register(
                roomId = "room-1",
                updateReason = realtimeMessageCreatedUpdateReason,
                notificationPolicy = "all",
                messageId = "message-live",
            ),
        )
    }

    @Test
    fun `duplicates stay silent even if the notification policy changes`() {
        val tracker = RealtimeMessageNotificationTracker()

        assertFalse(
            tracker.register(
                roomId = "room-1",
                updateReason = realtimeMessageCreatedUpdateReason,
                notificationPolicy = "silent",
                messageId = "message-1",
            ),
        )
        assertFalse(
            tracker.register(
                roomId = "room-1",
                updateReason = realtimeMessageCreatedUpdateReason,
                notificationPolicy = "all",
                messageId = "message-1",
            ),
        )
    }

    @Test
    fun `room id participates in deduplication and old entries are bounded`() {
        val tracker = RealtimeMessageNotificationTracker(capacity = 2)

        fun register(roomId: String, messageId: String): Boolean =
            tracker.register(
                roomId = roomId,
                updateReason = realtimeMessageCreatedUpdateReason,
                notificationPolicy = "all",
                messageId = messageId,
            )

        assertTrue(register("room-1", "message-1"))
        assertTrue(register("room-2", "message-1"))
        assertTrue(register("room-3", "message-3"))
        assertTrue(register("room-1", "message-1"))
    }

    @Test
    fun `clear permits notifications in a replacement account session`() {
        val tracker = RealtimeMessageNotificationTracker()

        assertTrue(
            tracker.register(
                roomId = "room-1",
                updateReason = realtimeMessageCreatedUpdateReason,
                notificationPolicy = "all",
                messageId = "message-1",
            ),
        )
        tracker.clear()
        assertTrue(
            tracker.register(
                roomId = "room-1",
                updateReason = realtimeMessageCreatedUpdateReason,
                notificationPolicy = "all",
                messageId = "message-1",
            ),
        )
    }
}
