package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.PersonalMusicPlaylistItem
import com.gangchat.client.protocol.SharedMusicPlaylist
import com.gangchat.client.protocol.SharedMusicTrack
import com.gangchat.client.protocol.Sticker
import com.gangchat.client.protocol.UploadedAsset
import com.gangchat.client.protocol.UserSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDisplayTest {
    @Test
    fun `message draft helpers save restore and remove per room text`() {
        val original = mapOf("room_1" to "hello")

        assertEquals(
            mapOf("room_1" to "hello", "room_2" to "draft"),
            saveMessageDraft(drafts = original, roomId = "room_2", text = "draft"),
        )
        assertSame(
            original,
            saveMessageDraft(drafts = original, roomId = null, text = "ignored"),
        )
        assertEquals("hello", messageDraftForRoom(drafts = original, roomId = "room_1"))
        assertEquals("", messageDraftForRoom(drafts = original, roomId = "missing"))

        assertTrue(removeMessageDraft(drafts = original, roomId = "room_1").isEmpty())
        assertSame(
            original,
            removeMessageDraft(drafts = original, roomId = "missing"),
        )
        assertSame(original, removeMessageDraft(drafts = original, roomId = null))
    }

    @Test
    fun `formatMessageTime formats local wall clock time`() {
        assertEquals("07:05", formatMessageTime(DateTime(2026, 6, 4, 7, 5)))
        assertEquals("23:59", formatMessageTime(DateTime(2026, 6, 4, 23, 59)))
    }

    @Test
    fun `formatChatTimestamp follows chat relative date labels`() {
        val now = DateTime(2026, 6, 12, 10)

        assertEquals("07:05", formatChatTimestamp(DateTime(2026, 6, 12, 7, 5), now = now))
        assertEquals(
            "昨天 07:05",
            formatChatTimestamp(DateTime(2026, 6, 11, 7, 5), now = now),
        )
        assertEquals(
            "前天 07:05",
            formatChatTimestamp(DateTime(2026, 6, 10, 7, 5), now = now),
        )
        assertEquals(
            "星期二 07:05",
            formatChatTimestamp(DateTime(2026, 6, 9, 7, 5), now = now),
        )
        assertEquals(
            "5月31日 07:05",
            formatChatTimestamp(DateTime(2026, 5, 31, 7, 5), now = now),
        )
        assertEquals(
            "2025年12月31日 07:05",
            formatChatTimestamp(DateTime(2025, 12, 31, 7, 5), now = now),
        )
    }

    @Test
    fun `formatDetailedChatTimestamp always includes full date and weekday`() {
        assertEquals(
            "2026年6月12日 星期五 07:05",
            formatDetailedChatTimestamp(DateTime(2026, 6, 12, 7, 5)),
        )
        assertEquals(
            "2025年12月31日 星期三 23:59",
            formatDetailedChatTimestamp(DateTime(2025, 12, 31, 23, 59)),
        )
    }

    @Test
    fun `shouldShowChatTimestamp shares identical timestamp labels`() {
        val now = DateTime(2026, 6, 12, 10)

        assertTrue(
            shouldShowChatTimestamp(
                current = DateTime(2026, 6, 12, 7, 5),
                previous = null,
                now = now,
            ),
        )
        assertFalse(
            shouldShowChatTimestamp(
                current = DateTime(2026, 6, 12, 7, 5, 45),
                previous = DateTime(2026, 6, 12, 7, 5, 5),
                now = now,
            ),
        )
        assertTrue(
            shouldShowChatTimestamp(
                current = DateTime(2026, 6, 12, 7, 6),
                previous = DateTime(2026, 6, 12, 7, 5),
                now = now,
            ),
        )
    }

    @Test
    fun `messageContentKind recognizes playlists alongside existing message types`() {
        assertEquals(MessageContentKind.text, messageContentKind(_message()))
        assertEquals(
            MessageContentKind.voice,
            messageContentKind(
                _message(
                    type = "audio",
                    attachments =
                        listOf(
                            MessageAttachment(
                                type = "audio",
                                name = "voice_1.m4a",
                                durationMs = 15000,
                                asset =
                                    UploadedAsset(
                                        id = "asset_voice",
                                        url = "/uploads/voice_1.m4a",
                                        thumbnailUrl = null,
                                        mimeType = "audio/mp4",
                                    ),
                            ),
                        ),
                ),
            ),
        )
        assertEquals(
            MessageContentKind.files,
            messageContentKind(
                _message(
                    type = "file",
                    attachments = listOf(MessageAttachment(type = "file", name = "a.pdf")),
                ),
            ),
        )
        assertEquals(
            MessageContentKind.sticker,
            messageContentKind(
                _message(type = "sticker", attachments = listOf(_stickerAttachment())),
            ),
        )
        assertEquals(
            MessageContentKind.playlist,
            messageContentKind(
                _message(
                    type = "playlist",
                    attachments =
                        listOf(
                            MessageAttachment(
                                type = "playlist",
                                playlistId = "playlist_1",
                                playlist =
                                    SharedMusicPlaylist(
                                        id = "playlist_1",
                                        name = "夜晚",
                                        description = "",
                                        itemCount = 0,
                                        createdAt = null,
                                        creator =
                                            UserSummary(
                                                id = "user_1",
                                                username = "alice",
                                                displayName = "Alice",
                                                avatarUrl = null,
                                                defaultAvatarKey = "blue-1",
                                            ),
                                        items = emptyList(),
                                    ),
                            ),
                        ),
                ),
            ),
        )
        assertEquals(
            MessageContentKind.musicTrack,
            messageContentKind(
                _message(
                    type = "music_track",
                    attachments =
                        listOf(
                            MessageAttachment(
                                type = "music_track",
                                track =
                                    SharedMusicTrack(
                                        id = "item_1",
                                        playlistId = "playlist_1",
                                        trackId = "track_1",
                                        source = "netease",
                                        title = "歌曲",
                                        artists = listOf("歌手"),
                                        durationMs = 180000,
                                    ),
                            ),
                        ),
                ),
            ),
        )
    }

    @Test
    fun `systemMessageEvent parses structured role-change messages`() {
        val actor =
            UserSummary(
                id = "user_actor",
                username = "owner",
                displayName = "Owner",
                avatarUrl = null,
                defaultAvatarKey = "blue-3",
            )
        val target =
            UserSummary(
                id = "user_target",
                username = "logan",
                displayName = "Logan",
                avatarUrl = null,
                defaultAvatarKey = "green-2",
            )
        val event =
            systemMessageEvent(
                _message(
                    type = "system",
                    body = "降职为管理员",
                    attachments =
                        listOf(
                            MessageAttachment(
                                type = "system",
                                event = kSystemEventRoomRoleChanged,
                                actor = actor,
                                target = target,
                                fromRole = "owner",
                                toRole = "admin",
                            ),
                        ),
                ),
            )

        assertNotNull(event)
        assertEquals(kSystemEventRoomRoleChanged, event!!.event)
        assertEquals("user_target", event.subject.id)
        assertEquals("user_actor", event.actor?.id)
        assertEquals("管理员", systemMessageRoleLabel(event.toRole))
        assertEquals("降职为", systemMessageRoleVerb(event))
        assertTrue(systemMessageRoleChangeOmitsActor(event))
    }

    @Test
    fun `systemMessageEvent parses room profile change values`() {
        val actor =
            UserSummary(
                id = "user_actor",
                username = "owner",
                displayName = "Owner",
                avatarUrl = null,
                defaultAvatarKey = "blue-3",
            )
        val event =
            systemMessageEvent(
                _message(
                    type = "system",
                    body = "房间名称被Owner修改为New Room",
                    attachments =
                        listOf(
                            MessageAttachment(
                                type = "system",
                                event = kSystemEventRoomNameChanged,
                                user = actor,
                                actor = actor,
                                oldValue = "Old Room",
                                newValue = "New Room",
                            ),
                        ),
                ),
            )

        assertNotNull(event)
        assertEquals(kSystemEventRoomNameChanged, event!!.event)
        assertEquals("user_actor", event.subject.id)
        assertEquals("user_actor", event.actor?.id)
        assertEquals("Old Room", event.oldValue)
        assertEquals("New Room", event.newValue)
    }

    @Test
    fun `messageCopyText renders structured system message text`() {
        val actor =
            UserSummary(
                id = "user_actor",
                username = "owner",
                displayName = "Owner",
                avatarUrl = null,
                defaultAvatarKey = "blue-3",
                roomDisplayName = "房内Owner",
            )
        val nameChanged =
            _message(
                type = "system",
                body = "房间名称修改为New Room",
                attachments =
                    listOf(
                        MessageAttachment(
                            type = "system",
                            event = kSystemEventRoomNameChanged,
                            actor = actor,
                            oldValue = "Old Room",
                            newValue = "New Room",
                        ),
                    ),
            )
        val descriptionChanged =
            _message(
                type = "system",
                body = "房间简介修改为Hello",
                attachments =
                    listOf(
                        MessageAttachment(
                            type = "system",
                            event = kSystemEventRoomDescriptionChanged,
                            actor = actor,
                            oldValue = "Old",
                            newValue = "Hello",
                        ),
                    ),
            )
        val visibilityChanged =
            _message(
                type = "system",
                body = "房间可见性修改为私密",
                attachments =
                    listOf(
                        MessageAttachment(
                            type = "system",
                            event = kSystemEventRoomVisibilityChanged,
                            actor = actor,
                            oldValue = "public",
                            newValue = "private",
                        ),
                    ),
            )
        val joinPolicyChanged =
            _message(
                type = "system",
                body = "房间加入方式修改为关闭",
                attachments =
                    listOf(
                        MessageAttachment(
                            type = "system",
                            event = kSystemEventRoomJoinPolicyChanged,
                            actor = actor,
                            oldValue = "approval_required",
                            newValue = "closed",
                        ),
                    ),
            )

        assertEquals("房间名称 被 房内Owner 修改为 New Room", messageCopyText(nameChanged))
        assertEquals("房间简介 被 房内Owner 修改为\nHello", messageCopyText(descriptionChanged))
        assertEquals("房间可见性 被 房内Owner 修改为 私密", messageCopyText(visibilityChanged))
        assertEquals("房间加入方式 被 房内Owner 修改为 关闭", messageCopyText(joinPolicyChanged))
    }

    @Test
    fun `messageCopyText renders removed message placeholders`() {
        val sender =
            UserSummary(
                id = "user_sender",
                username = "logan",
                displayName = "Logan",
                avatarUrl = null,
                defaultAvatarKey = "green-2",
                roomDisplayName = "房内Logan",
            )
        val admin =
            UserSummary(
                id = "user_admin",
                username = "admin",
                displayName = "Admin",
                avatarUrl = null,
                defaultAvatarKey = "blue-3",
            )

        assertEquals(
            "Admin 撤回了一条来自 房内Logan 的消息",
            messageCopyText(
                _message(sender = sender, isRecalled = true, recalledBy = admin),
            ),
        )
        assertEquals(
            "Admin 删除了一条消息",
            messageCopyText(
                _message(sender = sender, isForceDeleted = true, forceDeletedBy = admin),
            ),
        )
    }

    @Test
    fun `messageClipboardText preserves complete music components`() {
        val creator =
            UserSummary(
                id = "user_creator",
                username = "alice",
                displayName = "Alice",
                avatarUrl = null,
                defaultAvatarKey = "blue-3",
                roomDisplayName = "房内 Alice",
            )
        val playlistMessage =
            _message(
                type = "playlist",
                body = "[歌单] 夜晚",
                attachments =
                    listOf(
                        MessageAttachment(
                            type = "playlist",
                            playlist =
                                SharedMusicPlaylist(
                                    id = "playlist_1",
                                    name = "夜晚",
                                    description = "适合夜里",
                                    itemCount = 1,
                                    createdAt = DateTime.utc(2026, 8, 7, 9, 30),
                                    creator = creator,
                                    items =
                                        listOf(
                                            PersonalMusicPlaylistItem(
                                                id = "item_1",
                                                playlistId = "playlist_1",
                                                trackId = "track_1",
                                                source = "netease",
                                                title = "晴天",
                                                artists = listOf("周杰伦"),
                                                durationMs = 269000,
                                                sortOrder = 10,
                                                createdAt = null,
                                            ),
                                        ),
                                ),
                        ),
                    ),
            )
        val playlistText = messageClipboardText(playlistMessage)
        assertTrue(playlistText.contains("[歌单] 夜晚"))
        assertTrue(playlistText.contains("歌曲数量：1"))
        assertTrue(playlistText.contains("创建人：房内 Alice"))
        assertTrue(playlistText.contains("1. 晴天 - 周杰伦 [netease:track_1]"))
        assertTrue(messageClipboardTextMatches(playlistMessage, playlistText))
        assertTrue(
            messageClipboardTextMatches(
                playlistMessage,
                "${playlistText.replace("\n", "\r\n")}\r\n",
            ),
        )
        assertFalse(
            messageClipboardTextMatches(playlistMessage, "$playlistText 已修改"),
        )

        val trackText =
            messageClipboardText(
                _message(
                    type = "music_track",
                    body = "[歌曲] 晴天",
                    attachments =
                        listOf(
                            MessageAttachment(
                                type = "music_track",
                                track =
                                    SharedMusicTrack(
                                        id = "item_1",
                                        playlistId = "playlist_1",
                                        trackId = "track_1",
                                        source = "netease",
                                        title = "晴天",
                                        artists = listOf("周杰伦"),
                                        durationMs = 269000,
                                    ),
                            ),
                        ),
                ),
            )
        assertTrue(trackText.contains("[歌曲] 晴天"))
        assertTrue(trackText.contains("歌手：周杰伦"))
        assertTrue(trackText.contains("时长：4:29"))
        assertTrue(trackText.contains("链接标识：track_1"))

        val encodedText = messageComponentClipboardText(playlistMessage)
        assertTrue(encodedText.startsWith(playlistText))
        assertTrue(messageClipboardTextMatches(playlistMessage, encodedText))
        val restored = messageComponentFromClipboardText(encodedText)
        assertNotNull(restored)
        assertEquals(playlistMessage.id, restored!!.id)
        assertEquals(playlistMessage.roomId, restored.roomId)
        assertEquals("夜晚", restored.playlistAttachment?.playlist?.name)
        assertEquals(1, restored.playlistAttachment?.playlist?.items?.size)
        assertNull(messageComponentFromClipboardText("$playlistText 已修改"))
        assertNull(messageComponentFromClipboardText("篡改的可见内容$encodedText"))
    }

    @Test
    fun `messageQuoteSnapshot omits the sender for system messages`() {
        val systemQuote =
            messageQuoteSnapshot(
                _message(type = kSystemMessageType, body = "joined the room"),
            )
        val userQuote = messageQuoteSnapshot(_message())

        assertTrue(systemQuote.senderDisplayName.isEmpty())
        assertEquals("Logan", userQuote.senderDisplayName)
    }

    @Test
    fun `shouldShowFileAttachmentBody hides duplicate single-file body`() {
        val attachment = MessageAttachment(type = "file", name = "report.pdf")

        assertFalse(
            shouldShowFileAttachmentBody(
                body = " report.pdf ",
                attachments = listOf(attachment),
            ),
        )
        assertTrue(
            shouldShowFileAttachmentBody(
                body = "see attached",
                attachments = listOf(attachment),
            ),
        )
        assertTrue(
            shouldShowFileAttachmentBody(
                body = "report.pdf",
                attachments =
                    listOf(
                        attachment,
                        MessageAttachment(type = "file", name = "other.pdf"),
                    ),
            ),
        )
        assertFalse(
            shouldShowFileAttachmentBody(body = " ", attachments = listOf(attachment)),
        )
    }

    @Test
    fun `stickerAttachmentTitle trims names and falls back to sticker label`() {
        assertEquals(
            "wave",
            stickerAttachmentTitle(
                MessageAttachment(type = "sticker", name = " wave "),
            ),
        )
        assertEquals(
            "表情",
            stickerAttachmentTitle(
                MessageAttachment(type = "sticker", name = "   "),
            ),
        )
        assertEquals(
            "表情",
            stickerAttachmentTitle(MessageAttachment(type = "sticker")),
        )
    }

    @Test
    fun `messageDeliveryStatusText reflects pending and failed states`() {
        assertNull(messageDeliveryStatusText(_message()))
        assertEquals("发送中", messageDeliveryStatusText(_message(pending = true)))
        assertEquals(
            "发送失败",
            messageDeliveryStatusText(_message(pending = true, failed = true)),
        )
    }

    @Test
    fun `outgoingTextMessageBody trims trailing whitespace and skips blanks`() {
        assertEquals(" hello", outgoingTextMessageBody(" hello  \n "))
        assertNull(outgoingTextMessageBody("   \n"))
    }

    @Test
    fun `stickerMessageDraft builds reusable sticker payload data`() {
        val sticker = _sticker()
        val draft = stickerMessageDraft(sticker)

        assertEquals("[表情] wave", draft.body)
        assertEquals("sticker", draft.type)
        assertEquals(1, draft.attachments.size)
        assertEquals("sticker", draft.attachments.single().type)
        assertEquals("sticker_1", draft.attachments.single().stickerId)
        assertEquals("wave", draft.attachments.single().name)
        assertSame(sticker.asset, draft.attachments.single().asset)
    }

    @Test
    fun `insertMessageText replaces selection and returns cursor offset`() {
        var edit =
            insertMessageText(
                currentText = "hello",
                insertedText = "!",
                selectionStart = null,
                selectionEnd = null,
            )
        assertEquals("hello!", edit.text)
        assertEquals(6, edit.cursorOffset)

        edit =
            insertMessageText(
                currentText = "hello world",
                insertedText = "there",
                selectionStart = 6,
                selectionEnd = 11,
            )
        assertEquals("hello there", edit.text)
        assertEquals(11, edit.cursorOffset)

        edit =
            insertMessageText(
                currentText = "hello world",
                insertedText = "there",
                selectionStart = 11,
                selectionEnd = 6,
            )
        assertEquals("hello there", edit.text)
        assertEquals(11, edit.cursorOffset)

        edit =
            insertMessageText(
                currentText = "hello",
                insertedText = "x",
                selectionStart = -20,
                selectionEnd = 20,
            )
        assertEquals("x", edit.text)
        assertEquals(1, edit.cursorOffset)
    }

    private fun _message(
        type: String = "text",
        body: String = "hello",
        attachments: List<MessageAttachment> = emptyList(),
        sender: UserSummary? = null,
        isRecalled: Boolean = false,
        recalledBy: UserSummary? = null,
        isForceDeleted: Boolean = false,
        forceDeletedBy: UserSummary? = null,
        pending: Boolean = false,
        failed: Boolean = false,
    ): Message {
        return Message(
            id = "message_1",
            roomId = "room_1",
            sender =
                sender
                    ?: UserSummary(
                        id = "user_1",
                        username = "logan",
                        displayName = "Logan",
                        avatarUrl = null,
                        defaultAvatarKey = "blue-3",
                    ),
            clientMessageId = "client_1",
            type = type,
            body = body,
            createdAt = DateTime.utc(2026, 6, 4),
            attachments = attachments,
            isRecalled = isRecalled,
            recalledBy = recalledBy,
            isForceDeleted = isForceDeleted,
            forceDeletedBy = forceDeletedBy,
            pending = pending,
            failed = failed,
        )
    }

    private fun _stickerAttachment(): MessageAttachment {
        return MessageAttachment(
            type = "sticker",
            asset =
                UploadedAsset(
                    id = "asset_1",
                    url = "/sticker.webp",
                    thumbnailUrl = null,
                    mimeType = "image/webp",
                ),
        )
    }

    private fun _sticker(): Sticker {
        return Sticker(
            id = "sticker_1",
            name = "wave",
            sortOrder = 10,
            asset =
                UploadedAsset(
                    id = "asset_1",
                    url = "/sticker.webp",
                    thumbnailUrl = null,
                    mimeType = "image/webp",
                ),
        )
    }
}
