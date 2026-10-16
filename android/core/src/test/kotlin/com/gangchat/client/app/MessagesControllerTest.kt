package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.asJsonMap
import com.gangchat.client.foundation.http.ClientException
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.foundation.utf8Decode
import com.gangchat.client.foundation.utf8Encode
import com.gangchat.client.protocol.GangApiClient
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.MessageQuote
import com.gangchat.client.protocol.UploadTransferController
import com.gangchat.client.protocol.UploadedAsset
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.MockClient
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MessagesControllerTest {
    @get:Rule
    val gang = GangTestRule()

    // group('composerMessageSendPlan')

    @Test
    fun `composerMessageSendPlan keeps a normal text message as one message`() {
        val plan = composerMessageSendPlan(body = "hello   ")

        assertEquals(1, plan.size)
        assertEquals("text", plan.single().type)
        assertEquals("hello", plan.single().body)
        assertTrue(plan.single().attachments.isEmpty())
    }

    @Test
    fun `composerMessageSendPlan sends a copied component before following text`() {
        val component =
            Message.local(
                roomId = "room_1",
                sender = _sender,
                clientMessageId = "source_message_1",
                type = "playlist",
                body = "[歌单] 夜晚歌单",
            )

        val plan =
            composerMessageSendPlan(
                body = "紧接着发送的说明",
                component = component,
            )

        assertEquals(2, plan.size)
        assertEquals("playlist", plan.first().type)
        assertTrue(plan.first().body.isEmpty())
        assertEquals("playlist", plan.first().attachments.single().type)
        assertEquals("source_message_1", plan.first().attachments.single().sourceMessageId)
        assertEquals("text", plan.last().type)
        assertEquals("紧接着发送的说明", plan.last().body)
    }

    @Test
    fun `composerMessageSendPlan keeps a component-only draft as one message`() {
        val component =
            Message.local(
                roomId = "room_1",
                sender = _sender,
                clientMessageId = "source_track_1",
                type = "music_track",
                body = "[歌曲] 一首歌",
            )

        val plan = composerMessageSendPlan(body = "", component = component)

        assertEquals(1, plan.size)
        assertEquals("music_track", plan.single().type)
        assertEquals("source_track_1", plan.single().attachments.single().sourceMessageId)
    }

    @Test
    fun `composerMessageSendPlan sequential execution stops after the first failed part`() = gang.runTest {
        val component =
            Message.local(
                roomId = "room_1",
                sender = _sender,
                clientMessageId = "source_track_2",
                type = "music_track",
                body = "[歌曲] 一首歌",
            )
        val plan = composerMessageSendPlan(body = "说明文字", component = component)
        val attempted = mutableListOf<String>()
        val followingFlags = mutableListOf<Boolean>()

        val completed =
            sendComposerMessagePartsSequentially(
                parts = plan,
                send = { part, hasFollowingPart ->
                    attempted.add(part.type)
                    followingFlags.add(hasFollowingPart)
                    part.type != "text"
                },
            )

        assertFalse(completed)
        assertEquals(listOf("music_track", "text"), attempted)
        assertEquals(listOf(true, false), followingFlags)

        val firstFailureAttempts = mutableListOf<String>()
        val stoppedImmediately =
            sendComposerMessagePartsSequentially(
                parts = plan,
                send = { part, _ ->
                    firstFailureAttempts.add(part.type)
                    false
                },
            )
        assertFalse(stoppedImmediately)
        assertEquals(listOf("music_track"), firstFailureAttempts)
    }

    @Test
    fun `hideMessageHistory deletes large selections in server-sized batches`() = gang.runTest {
        val batchSizes = mutableListOf<Int>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("/api/v1/rooms/room_1/message-history/hide", request.url.path)
                        val body = jsonDecode(utf8Decode(request.bodyBytes)).asJsonMap()
                        val ids = (body["message_ids"] as List<*>).map { it as String }
                        assertEquals(true, body["confirm"])
                        batchSizes.add(ids.size)
                        HttpResponse(
                            jsonEncode(mapOf("ok" to true, "deleted_count" to ids.size)),
                            200,
                        )
                    },
            )

        try {
            val deleted =
                MessagesController(api = api).hideMessageHistory(
                    roomId = "room_1",
                    messageIds = List(205) { index -> "msg_$index" },
                )

            assertEquals(205, deleted)
            assertEquals(listOf(100, 100, 5), batchSizes)
        } finally {
            api.close()
        }
    }

    @Test
    fun `sendComposedMessage publishes a local pending message before sending`() = gang.runTest {
        var pendingClientId: String? = null
        val firstQuote =
            MessageQuote(
                messageId = "source_1",
                senderDisplayName = "房内 Alice",
                body = "source body",
                createdAt = DateTime.utc(2026, 7, 14, 8, 12),
            )
        val secondQuote =
            MessageQuote(
                messageId = "source_2",
                senderDisplayName = "房内 Bob",
                body = "second source",
                createdAt = DateTime.utc(2026, 7, 14, 8, 13),
            )
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/rooms/room_1/messages", request.url.path)
                        val body = jsonDecode(utf8Decode(request.bodyBytes)).asJsonMap()
                        assertEquals(pendingClientId, body["client_message_id"])
                        assertEquals("hello", body["body"])
                        assertEquals("source_1", body["quote_message_id"])
                        assertEquals(listOf("source_1", "source_2"), body["quote_message_ids"])
                        val message =
                            _messageJson(
                                clientMessageId = body["client_message_id"] as String,
                                body = "hello",
                            )
                        message["quote"] =
                            linkedMapOf(
                                "message_id" to "source_1",
                                "sender_display_name" to "房内 Alice",
                                "body" to "source body",
                                "created_at" to "2026-07-14T08:12:00Z",
                            )
                        message["quotes"] =
                            listOf(
                                message["quote"],
                                linkedMapOf(
                                    "message_id" to "source_2",
                                    "sender_display_name" to "房内 Bob",
                                    "body" to "second source",
                                    "created_at" to "2026-07-14T08:13:00Z",
                                ),
                            )
                        HttpResponse.bytes(
                            utf8Encode(jsonEncode(mapOf("message" to message))),
                            201,
                            headers = mapOf("content-type" to "application/json; charset=utf-8"),
                        )
                    },
            )

        try {
            val pendingBodies = mutableListOf<String>()
            val sent =
                MessagesController(api = api).sendComposedMessage(
                    roomId = "room_1",
                    sender = _sender,
                    body = "hello",
                    quotes = listOf(firstQuote, secondQuote),
                    onPending = { pending ->
                        pendingClientId = pending.clientMessageId
                        pendingBodies.add(pending.local.body)
                        assertTrue(pending.local.pending)
                        assertEquals(listOf(firstQuote, secondQuote), pending.local.effectiveQuotes)
                    },
                )

            assertEquals(listOf("hello"), pendingBodies)
            assertEquals(pendingClientId, sent.clientMessageId)
            assertFalse(sent.pending)
            assertEquals("房内 Alice", sent.quote?.senderDisplayName)
            assertEquals(
                listOf("source_1", "source_2"),
                sent.effectiveQuotes.map { quote -> quote.messageId },
            )
        } finally {
            api.close()
        }
    }

    @Test
    fun `sendFileMessage owns pending upload and send sequencing`() = gang.runTest {
        var pendingClientId: String? = null
        val requests = mutableListOf<String>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requests.add(request.url.path)
                        if (request.url.path == "/api/v1/uploads/files") {
                            val multipartBody = utf8Decode(request.bodyBytes, allowMalformed = true)
                            assertTrue(multipartBody.contains("report.pdf"))
                            return@MockClient HttpResponse(
                                jsonEncode(
                                    mapOf(
                                        "asset" to
                                            _assetJson(
                                                id = "asset_1",
                                                filename = "report.pdf",
                                                sizeBytes = 3,
                                            ),
                                    ),
                                ),
                                201,
                            )
                        }

                        assertEquals("/api/v1/rooms/room_1/messages", request.url.path)
                        val body = jsonDecode(utf8Decode(request.bodyBytes)).asJsonMap()
                        assertEquals(pendingClientId, body["client_message_id"])
                        assertEquals("report.pdf", body["body"])
                        assertEquals("file", body["type"])
                        val attachments = body["attachments"] as List<*>
                        val attachment = attachments.single().asJsonMap()
                        assertEquals("file", attachment["type"])
                        assertEquals("report.pdf", attachment["name"])
                        assertEquals("asset_1", attachment["asset"].asJsonMap()["id"])
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "message" to
                                        _messageJson(
                                            clientMessageId = body["client_message_id"] as String,
                                            body = "report.pdf",
                                            type = "file",
                                            attachments = attachments,
                                        ),
                                ),
                            ),
                            201,
                        )
                    },
            )

        try {
            val events = mutableListOf<String>()
            val progress = mutableListOf<Long>()
            val sent =
                MessagesController(api = api).sendFileMessage(
                    roomId = "room_1",
                    sender = _sender,
                    filename = "report.pdf",
                    sizeBytes = 3,
                    mimeType = "application/pdf",
                    readBytes = { byteArrayOf(1, 2, 3) },
                    onPending = { pending ->
                        pendingClientId = pending.clientMessageId
                        events.add("pending")
                        assertEquals("file", pending.local.type)
                        assertTrue(pending.local.pending)
                    },
                    onProgress = { _, sentBytes, totalBytes ->
                        assertEquals(3L, totalBytes)
                        progress.add(sentBytes)
                    },
                    onUploaded = { _, attachment ->
                        events.add("uploaded")
                        assertEquals("asset_1", attachment.asset?.id)
                    },
                )

            assertEquals(
                listOf(
                    "/api/v1/uploads/files",
                    "/api/v1/rooms/room_1/messages",
                ),
                requests,
            )
            assertEquals(listOf("pending", "uploaded"), events)
            assertEquals(0L, progress.first())
            assertEquals(3L, progress.last())
            assertEquals("file", sent.type)
            assertEquals("asset_1", sent.attachments.single().asset?.id)
        } finally {
            api.close()
        }
    }

    @Test
    fun `sendVoiceMessage uploads audio and sends duration metadata`() = gang.runTest {
        var pendingClientId: String? = null
        val requests = mutableListOf<String>()
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requests.add(request.url.path)
                        if (request.url.path == "/api/v1/uploads/files") {
                            val multipartBody = utf8Decode(request.bodyBytes, allowMalformed = true)
                            assertTrue(multipartBody.contains("voice_1.m4a"))
                            return@MockClient HttpResponse(
                                jsonEncode(
                                    mapOf(
                                        "asset" to
                                            _assetJson(
                                                id = "asset_voice",
                                                filename = "voice_1.m4a",
                                                sizeBytes = 4,
                                                mimeType = "audio/mp4",
                                            ),
                                    ),
                                ),
                                201,
                            )
                        }

                        assertEquals("/api/v1/rooms/room_1/messages", request.url.path)
                        val body = jsonDecode(utf8Decode(request.bodyBytes)).asJsonMap()
                        assertEquals(pendingClientId, body["client_message_id"])
                        assertEquals("voice_1.m4a", body["body"])
                        assertEquals("audio", body["type"])
                        val attachments = body["attachments"] as List<*>
                        val attachment = attachments.single().asJsonMap()
                        assertEquals("audio", attachment["type"])
                        assertEquals("voice_1.m4a", attachment["name"])
                        assertEquals(15000L, attachment["duration_ms"])
                        assertEquals("asset_voice", attachment["asset"].asJsonMap()["id"])
                        HttpResponse(
                            jsonEncode(
                                mapOf(
                                    "message" to
                                        _messageJson(
                                            clientMessageId = body["client_message_id"] as String,
                                            body = "voice_1.m4a",
                                            type = "audio",
                                            attachments = attachments,
                                        ),
                                ),
                            ),
                            201,
                        )
                    },
            )

        try {
            val events = mutableListOf<String>()
            val sent =
                MessagesController(api = api).sendVoiceMessage(
                    roomId = "room_1",
                    sender = _sender,
                    filename = "voice_1.m4a",
                    sizeBytes = 4,
                    mimeType = "audio/mp4",
                    duration = 15.seconds,
                    readBytes = { byteArrayOf(1, 2, 3, 4) },
                    onPending = { pending ->
                        pendingClientId = pending.clientMessageId
                        events.add("pending")
                        assertEquals("audio", pending.local.type)
                        assertEquals(15000, pending.local.attachments.single().durationMs)
                    },
                    onUploaded = { _, attachment ->
                        events.add("uploaded")
                        assertEquals("audio", attachment.type)
                        assertEquals(15000, attachment.durationMs)
                    },
                )

            assertEquals(
                listOf(
                    "/api/v1/uploads/files",
                    "/api/v1/rooms/room_1/messages",
                ),
                requests,
            )
            assertEquals(listOf("pending", "uploaded"), events)
            assertEquals("audio", sent.type)
            assertEquals(15000, sent.attachments.single().durationMs)
            assertEquals("audio/mp4", sent.attachments.single().asset?.mimeType)
        } finally {
            api.close()
        }
    }

    @Test
    fun `message list reducers append replace remove and mark failed`() {
        val controller = MessagesController()
        val local =
            Message.local(
                roomId = "room_1",
                sender = _sender,
                clientMessageId = "client_1",
                body = "pending",
            )
        val sent =
            Message(
                id = "message_1",
                roomId = "room_1",
                sender = _sender,
                clientMessageId = "client_1",
                body = "sent",
                createdAt = DateTime.utc(2026, 6, 4),
            )

        val appended = controller.appendLocalMessage(emptyList(), local)
        assertTrue(appended.single().pending)

        val failed = controller.markFailedByClientId(appended, "client_1")
        assertTrue(failed.single().failed)

        val replaced = controller.replaceByClientId(failed, sent)
        assertEquals("sent", replaced.single().body)
        assertFalse(replaced.single().pending)

        assertTrue(controller.removeByClientId(replaced, "client_1").isEmpty())
    }

    @Test
    fun `composed message state patches cover pending sent and failed`() {
        val controller = MessagesController()
        val existing = listOf(_message("existing", clientMessageId = "existing_client"))
        val pending =
            controller.createPendingMessage(
                roomId = "room_1",
                sender = _sender,
                body = "hello",
            )
        val sent =
            Message(
                id = "message_1",
                roomId = "room_1",
                sender = _sender,
                clientMessageId = pending.clientMessageId,
                body = "hello",
                createdAt = DateTime.utc(2026, 6, 4),
            )

        val started = controller.patchMessageSendStarted(messages = existing)
        assertSame(existing, started.messages)
        assertTrue(started.sending)
        assertNull(started.error)

        val pendingPatch =
            controller.patchMessageSendPending(
                messages = existing,
                pending = pending,
                error = "kept",
            )
        assertEquals(2, pendingPatch.messages.size)
        assertTrue(pendingPatch.messages.last().pending)
        assertTrue(pendingPatch.sending)
        assertEquals("kept", pendingPatch.error)

        val failedPatch =
            controller.patchMessageSendFailed(
                messages = pendingPatch.messages,
                clientMessageId = pending.clientMessageId,
                error = null,
            )
        assertTrue(failedPatch.messages.last().failed)
        assertTrue(failedPatch.sending)
        assertNull(failedPatch.error)

        val failedWithoutClientId =
            controller.patchMessageSendFailed(
                messages = pendingPatch.messages,
                clientMessageId = null,
                error = "kept",
            )
        assertSame(pendingPatch.messages, failedWithoutClientId.messages)
        assertTrue(failedWithoutClientId.sending)
        assertEquals("kept", failedWithoutClientId.error)

        val sentPatch =
            controller.patchMessageSendSucceeded(
                messages = failedPatch.messages,
                sent = sent,
                error = null,
            )
        assertEquals(sent, sentPatch.messages.last())
        assertTrue(sentPatch.sending)
        assertNull(sentPatch.error)

        val finished =
            controller.patchMessageSendFinished(
                messages = sentPatch.messages,
                error = "kept",
            )
        assertSame(sentPatch.messages, finished.messages)
        assertFalse(finished.sending)
        assertEquals("kept", finished.error)
    }

    @Test
    fun `file message state patches cover upload lifecycle`() {
        val controller = MessagesController()
        val pending =
            controller.createPendingFileMessage(
                roomId = "room_1",
                sender = _sender,
                filename = "report.pdf",
                sizeBytes = 100,
                mimeType = "application/pdf",
            )

        var patch =
            controller.patchPendingFileMessage(
                messages = emptyList(),
                fileTransfers = emptyMap(),
                pending = pending,
            )
        assertTrue(patch.messages.single().pending)
        assertSame(pending.transfer, patch.fileTransfers[pending.clientMessageId])

        val progressPatch =
            controller.patchFileTransferProgress(
                messages = patch.messages,
                fileTransfers = patch.fileTransfers,
                pending = pending,
                sentBytes = 25,
                totalBytes = 100,
            )
        assertNotNull(progressPatch)
        assertSame(patch.messages, progressPatch!!.messages)
        assertSame(patch.fileTransfers, progressPatch.fileTransfers)
        assertEquals(25L, pending.transfer.sentBytes)
        patch = progressPatch

        val attachment =
            MessageAttachment(
                type = "file",
                name = "report.pdf",
                asset =
                    UploadedAsset(
                        id = "asset_1",
                        url = "/assets/asset_1/report.pdf",
                        thumbnailUrl = null,
                        mimeType = "application/pdf",
                        filename = "report.pdf",
                        sizeBytes = 100,
                    ),
            )
        patch =
            controller.patchUploadedFileMessage(
                messages = patch.messages,
                fileTransfers = patch.fileTransfers,
                pending = pending,
                attachment = attachment,
            )
        assertTrue(pending.transfer.sendingMessage)
        assertEquals("asset_1", patch.messages.single().attachments.single().asset?.id)

        patch =
            controller.patchFailedFileMessage(
                messages = patch.messages,
                fileTransfers = patch.fileTransfers,
                clientMessageId = pending.clientMessageId,
                failure = "network failed",
            )
        assertTrue(pending.transfer.failed)
        assertEquals("网络连接失败，请检查网络后重试", pending.transfer.error)
        assertTrue(patch.messages.single().failed)

        val sent =
            Message(
                id = "message_1",
                roomId = "room_1",
                sender = _sender,
                clientMessageId = pending.clientMessageId,
                type = "file",
                body = "report.pdf",
                attachments = listOf(attachment),
                createdAt = DateTime.utc(2026, 6, 4),
            )
        patch =
            controller.patchSentFileMessage(
                messages = patch.messages,
                fileTransfers = patch.fileTransfers,
                clientMessageId = pending.clientMessageId,
                sent = sent,
            )
        assertEquals(sent, patch.messages.single())
        assertTrue(patch.fileTransfers.isEmpty())
    }

    @Test
    fun `file message remove patch drops local message and transfer`() {
        val controller = MessagesController()
        val pending =
            controller.createPendingFileMessage(
                roomId = "room_1",
                sender = _sender,
                filename = "report.pdf",
                sizeBytes = 100,
                mimeType = "application/pdf",
            )
        val pendingPatch =
            controller.patchPendingFileMessage(
                messages = emptyList(),
                fileTransfers = emptyMap(),
                pending = pending,
            )

        val removed =
            controller.patchRemovedFileMessage(
                messages = pendingPatch.messages,
                fileTransfers = pendingPatch.fileTransfers,
                clientMessageId = pending.clientMessageId,
            )

        assertTrue(removed.messages.isEmpty())
        assertTrue(removed.fileTransfers.isEmpty())
    }

    @Test
    fun `file upload controls pause resume and cancel only upload transfers`() {
        val controller = MessagesController()
        val pending =
            controller.createPendingFileMessage(
                roomId = "room_1",
                sender = _sender,
                filename = "report.pdf",
                sizeBytes = 100,
                mimeType = "application/pdf",
            )
        val transfers =
            mapOf(
                pending.clientMessageId to pending.transfer,
                "download" to
                    FileTransferState.download(
                        controller = UploadTransferController(),
                        totalBytes = 100,
                        destinationPath = "/tmp/report.pdf",
                    ),
            )

        assertNotNull(
            controller.patchPausedFileUpload(
                messages = emptyList(),
                fileTransfers = transfers,
                clientMessageId = pending.clientMessageId,
            ),
        )
        assertTrue(pending.transfer.paused)
        assertNull(
            controller.patchPausedFileUpload(
                messages = emptyList(),
                fileTransfers = transfers,
                clientMessageId = pending.clientMessageId,
            ),
        )

        assertNotNull(
            controller.patchResumedFileUpload(
                messages = emptyList(),
                fileTransfers = transfers,
                clientMessageId = pending.clientMessageId,
            ),
        )
        assertFalse(pending.transfer.paused)
        assertNull(
            controller.patchResumedFileUpload(
                messages = emptyList(),
                fileTransfers = transfers,
                clientMessageId = pending.clientMessageId,
            ),
        )

        assertFalse(
            controller.pauseFileUpload(
                fileTransfers = transfers,
                clientMessageId = "download",
            ),
        )
        assertFalse(
            controller.cancelFileUpload(
                fileTransfers = transfers,
                clientMessageId = "download",
            ),
        )
        assertFalse(
            controller.cancelFileUpload(
                fileTransfers = transfers,
                clientMessageId = "missing",
            ),
        )

        assertTrue(
            controller.cancelFileUpload(
                fileTransfers = transfers,
                clientMessageId = pending.clientMessageId,
            ),
        )
        assertTrue(pending.transfer.cancelled)
        assertFalse(
            controller.cancelFileUpload(
                fileTransfers = transfers,
                clientMessageId = pending.clientMessageId,
            ),
        )
    }

    @Test
    fun `canSendComposedMessage validates text and attachment messages`() {
        assertTrue(
            canSendComposedMessage(
                body = " hello ",
                type = "text",
                attachments = emptyList(),
            ),
        )
        assertFalse(
            canSendComposedMessage(body = " ", type = "text", attachments = emptyList()),
        )
        assertTrue(
            canSendComposedMessage(
                body = "",
                type = "sticker",
                attachments = listOf(_stickerAttachment()),
            ),
        )
        assertFalse(
            canSendComposedMessage(
                body = "ignored",
                type = "sticker",
                attachments = emptyList(),
            ),
        )
    }

    @Test
    fun `markRead forwards the latest message id to the API`() = gang.runTest {
        var requests = 0
        val api =
            GangApiClient(
                baseUrl = "http://example.test/api/v1",
                accessTokenProvider = { "token" },
                httpClient =
                    MockClient { request ->
                        requests++
                        assertEquals("POST", request.method)
                        assertEquals("/api/v1/rooms/room_1/read", request.url.path)
                        assertEquals(
                            mapOf("last_read_message_id" to "msg_2"),
                            jsonDecode(utf8Decode(request.bodyBytes)),
                        )
                        if (requests == 1) {
                            throw ClientException("Connection reset by peer", request.url)
                        }
                        HttpResponse(jsonEncode(mapOf("ok" to true, "unread_count" to 0)), 200)
                    },
            )

        try {
            val unread =
                MessagesController(
                    api = api,
                ).markRead(roomId = "room_1", lastReadMessageId = "msg_2")

            assertEquals(0, unread)
            assertEquals(2, requests)
        } finally {
            api.close()
        }
    }

    private val _sender =
        UserSummary(
            id = "user_1",
            username = "alice",
            displayName = "Alice",
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
        )

    private fun _message(id: String, clientMessageId: String): Message {
        return Message(
            id = id,
            roomId = "room_1",
            sender = _sender,
            clientMessageId = clientMessageId,
            body = id,
            createdAt = DateTime.utc(2026, 6, 4),
        )
    }

    private fun _messageJson(
        clientMessageId: String,
        body: String,
        type: String = "text",
        attachments: List<Any?> = emptyList(),
    ): MutableMap<String, Any?> {
        return linkedMapOf(
            "id" to "msg_1",
            "room_id" to "room_1",
            "sender" to
                linkedMapOf(
                    "id" to _sender.id,
                    "username" to _sender.username,
                    "display_name" to _sender.displayName,
                ),
            "client_message_id" to clientMessageId,
            "type" to type,
            "body" to body,
            "attachments" to attachments,
            "created_at" to "2026-05-31T14:00:00Z",
        )
    }

    private fun _assetJson(
        id: String,
        filename: String,
        sizeBytes: Int,
        mimeType: String = "application/pdf",
    ): JsonMap {
        return linkedMapOf(
            "id" to id,
            "filename" to filename,
            "size_bytes" to sizeBytes,
            "url" to "/assets/$id/$filename",
            "thumbnail_url" to null,
            "mime_type" to mimeType,
        )
    }

    private fun _stickerAttachment(): MessageAttachment {
        return MessageAttachment(
            type = "sticker",
            asset =
                UploadedAsset(
                    id = "sticker_asset",
                    url = "/assets/sticker.webp",
                    thumbnailUrl = null,
                    mimeType = "image/webp",
                ),
        )
    }
}
