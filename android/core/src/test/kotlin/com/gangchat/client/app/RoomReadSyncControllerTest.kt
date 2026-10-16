package com.gangchat.client.app

import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.complete
import com.gangchat.client.foundation.future
import com.gangchat.client.foundation.waitAll
import com.gangchat.client.testing.GangTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RoomReadSyncControllerTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `serializes and coalesces read receipts for one room`() = gang.runTest {
        val firstStarted = Completer<Unit>()
        val releaseFirst = Completer<Unit>()
        val requestedMessageIds = mutableListOf<String>()
        val messages =
            _FakeMessagesController { _, messageId ->
                requestedMessageIds.add(messageId)
                if (requestedMessageIds.size == 1) {
                    firstStarted.complete()
                    releaseFirst.future.await()
                }
                0
            }
        val controller = RoomReadSyncController(messages = messages)
        try {
            val first =
                future {
                    controller.markRead(
                        roomId = "room_1",
                        lastReadMessageId = "msg_1",
                        messageCreatedAt = DateTime.utc(2026, 7, 14, 10),
                    )
                }
            firstStarted.future.await()
            val second =
                future {
                    controller.markRead(
                        roomId = "room_1",
                        lastReadMessageId = "msg_2",
                        messageCreatedAt = DateTime.utc(2026, 7, 14, 10, 1),
                    )
                }
            releaseFirst.complete()
            waitAll(listOf(first, second))

            assertEquals(listOf("msg_1", "msg_2"), requestedMessageIds)
            assertTrue(controller.isSynced(roomId = "room_1", messageId = "msg_2"))
        } finally {
            controller.close()
        }
    }

    @Test
    fun `retains a failed receipt and retries it after reconnect`() = gang.runTest {
        var requests = 0
        val messages =
            _FakeMessagesController { _, _ ->
                requests++
                if (requests == 1) throw IllegalStateException("offline")
                0
            }
        val controller = RoomReadSyncController(messages = messages)
        try {
            controller.markRead(
                roomId = "room_1",
                lastReadMessageId = "msg_2",
                messageCreatedAt = DateTime.utc(2026, 7, 14, 10),
            )
            assertFalse(controller.isSynced(roomId = "room_1", messageId = "msg_2"))

            controller.retryPending()

            assertEquals(2, requests)
            assertTrue(controller.isSynced(roomId = "room_1", messageId = "msg_2"))
        } finally {
            controller.close()
        }
    }

    @Test
    fun `does not replace a failed newer receipt with an older intent`() = gang.runTest {
        val requestedMessageIds = mutableListOf<String>()
        val messages =
            _FakeMessagesController { _, messageId ->
                requestedMessageIds.add(messageId)
                if (requestedMessageIds.size == 1) throw IllegalStateException("offline")
                0
            }
        val controller = RoomReadSyncController(messages = messages)
        try {
            controller.markRead(
                roomId = "room_1",
                lastReadMessageId = "msg_2",
                messageCreatedAt = DateTime.utc(2026, 7, 14, 10, 1),
            )
            controller.markRead(
                roomId = "room_1",
                lastReadMessageId = "msg_1",
                messageCreatedAt = DateTime.utc(2026, 7, 14, 10),
            )

            assertEquals(listOf("msg_2", "msg_2"), requestedMessageIds)
            assertTrue(controller.isSynced(roomId = "room_1", messageId = "msg_2"))
        } finally {
            controller.close()
        }
    }

    @Test
    fun `does not resend an already synced receipt`() = gang.runTest {
        var requests = 0
        val messages =
            _FakeMessagesController { _, _ ->
                requests++
                0
            }
        val controller = RoomReadSyncController(messages = messages)
        try {
            controller.markRead(
                roomId = "room_1",
                lastReadMessageId = "msg_2",
                messageCreatedAt = DateTime.utc(2026, 7, 14, 10),
            )
            controller.markRead(
                roomId = "room_1",
                lastReadMessageId = "msg_2",
                messageCreatedAt = DateTime.utc(2026, 7, 14, 10),
            )

            assertEquals(1, requests)
        } finally {
            controller.close()
        }
    }

    @Test
    fun `drains different rooms independently`() = gang.runTest {
        val roomOneStarted = Completer<Unit>()
        val releaseRoomOne = Completer<Unit>()
        val requestedRooms = mutableListOf<String>()
        val messages =
            _FakeMessagesController { roomId, _ ->
                requestedRooms.add(roomId)
                if (roomId == "room_1") {
                    roomOneStarted.complete()
                    releaseRoomOne.future.await()
                }
                0
            }
        val controller = RoomReadSyncController(messages = messages)
        try {
            val roomOne =
                future {
                    controller.markRead(
                        roomId = "room_1",
                        lastReadMessageId = "msg_1",
                        messageCreatedAt = DateTime.utc(2026, 7, 14, 10),
                    )
                }
            roomOneStarted.future.await()
            controller.markRead(
                roomId = "room_2",
                lastReadMessageId = "msg_2",
                messageCreatedAt = DateTime.utc(2026, 7, 14, 10),
            )

            assertTrue(requestedRooms.containsAll(listOf("room_1", "room_2")))
            assertTrue(controller.isSynced(roomId = "room_2", messageId = "msg_2"))
            releaseRoomOne.complete()
            roomOne.await()
        } finally {
            controller.close()
        }
    }

    @Test
    fun `close ignores a successful response that arrives late`() = gang.runTest {
        val started = Completer<Unit>()
        val release = Completer<Unit>()
        var requests = 0
        val messages =
            _FakeMessagesController { _, _ ->
                requests++
                started.complete()
                release.future.await()
                0
            }
        val controller = RoomReadSyncController(messages = messages)

        val pending =
            future {
                controller.markRead(
                    roomId = "room_1",
                    lastReadMessageId = "msg_1",
                    messageCreatedAt = DateTime.utc(2026, 7, 14, 10),
                )
            }
        started.future.await()
        controller.close()
        release.complete()
        pending.await()
        controller.retryPending()

        assertEquals(1, requests)
        assertFalse(controller.isSynced(roomId = "room_1", messageId = "msg_1"))
    }

    @Test
    fun `uses the message id as a stable tie-breaker for equal timestamps`() = gang.runTest {
        val requestedMessageIds = mutableListOf<String>()
        val messages =
            _FakeMessagesController { _, messageId ->
                requestedMessageIds.add(messageId)
                0
            }
        val controller = RoomReadSyncController(messages = messages)
        try {
            val createdAt = DateTime.utc(2026, 7, 14, 10)

            controller.markRead(
                roomId = "room_1",
                lastReadMessageId = "msg_b",
                messageCreatedAt = createdAt,
            )
            controller.markRead(
                roomId = "room_1",
                lastReadMessageId = "msg_a",
                messageCreatedAt = createdAt,
            )

            assertEquals(listOf("msg_b"), requestedMessageIds)
            assertTrue(controller.isSynced(roomId = "room_1", messageId = "msg_b"))
        } finally {
            controller.close()
        }
    }

    @Test
    fun `ignores blank room and message identifiers`() = gang.runTest {
        var requests = 0
        val messages =
            _FakeMessagesController { _, _ ->
                requests++
                0
            }
        val controller = RoomReadSyncController(messages = messages)
        try {
            val createdAt = DateTime.utc(2026, 7, 14, 10)

            controller.markRead(
                roomId = "",
                lastReadMessageId = "msg_1",
                messageCreatedAt = createdAt,
            )
            controller.markRead(
                roomId = "room_1",
                lastReadMessageId = "",
                messageCreatedAt = createdAt,
            )

            assertEquals(0, requests)
        } finally {
            controller.close()
        }
    }

    // Nested so test helpers of other files in this package don't clash on the JVM.
    private class _FakeMessagesController(
        val onMarkRead: suspend (roomId: String, messageId: String) -> Int,
    ) : MessagesController() {
        override suspend fun markRead(
            roomId: String,
            lastReadMessageId: String,
        ): Int {
            return onMarkRead(roomId, lastReadMessageId)
        }
    }
}
