package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.GlobalSearchCounts
import com.gangchat.client.protocol.GlobalSearchCursors
import com.gangchat.client.protocol.GlobalSearchResults
import com.gangchat.client.protocol.LastMessagePreview
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.MessageSearchResult
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.SearchRoomContext
import com.gangchat.client.protocol.UploadedAsset
import com.gangchat.client.protocol.UserSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchDisplayTest {
    @Test
    fun `global search labels and counts map result categories`() {
        val results =
            GlobalSearchResults(
                myRooms = listOf(_room("room_1")),
                publicRooms = listOf(_publicRoom("room_2")),
                userSettings = listOf(_user("user_1")),
                messages = listOf(_messageResult("room_1")),
                files = listOf(_messageResult("room_1", type = "file")),
                totalCounts =
                    GlobalSearchCounts(
                        myRooms = 10,
                        publicRooms = 11,
                        userSettings = 14,
                        messages = 12,
                        files = 13,
                    ),
            )

        assertEquals("我的房间", globalSearchCategoryLabel(GlobalSearchCategory.myRooms))
        assertEquals(
            10,
            globalSearchCategoryCount(results, GlobalSearchCategory.myRooms),
        )
        assertEquals(
            11,
            globalSearchCategoryCount(results, GlobalSearchCategory.publicRooms),
        )
        assertEquals(
            14,
            globalSearchCategoryCount(results, GlobalSearchCategory.userSettings),
        )
        assertEquals(
            12,
            globalSearchCategoryCount(results, GlobalSearchCategory.messages),
        )
        assertEquals(13, globalSearchCategoryCount(results, GlobalSearchCategory.files))
        assertEquals("my_rooms", globalSearchCategoryKey(GlobalSearchCategory.myRooms))
        assertEquals(
            "public_rooms",
            globalSearchCategoryKey(GlobalSearchCategory.publicRooms),
        )
        assertEquals(
            "user_settings",
            globalSearchCategoryKey(GlobalSearchCategory.userSettings),
        )
        assertEquals(
            "用户设置",
            globalSearchCategoryLabel(GlobalSearchCategory.userSettings),
        )
        assertFalse(
            superuserGlobalSearchCategories.contains(GlobalSearchCategory.publicRooms),
        )
        assertTrue(
            superuserGlobalSearchCategories.contains(GlobalSearchCategory.userSettings),
        )
        assertEquals("messages", globalSearchCategoryKey(GlobalSearchCategory.messages))
        assertEquals("files", globalSearchCategoryKey(GlobalSearchCategory.files))
        assertTrue(globalSearchHasResults(results))
    }

    @Test
    fun `global search cursors parse and merge selected pages`() {
        val parsed =
            GlobalSearchResults.fromJson(
                mapOf(
                    "my_rooms" to emptyList<Any?>(),
                    "public_rooms" to emptyList<Any?>(),
                    "messages" to emptyList<Any?>(),
                    "files" to emptyList<Any?>(),
                    "next_cursors" to
                        mapOf(
                            "my_rooms" to "my-next",
                            "public_rooms" to null,
                            "messages" to "message-next",
                            "files" to "file-next",
                        ),
                    "total_counts" to
                        mapOf(
                            "my_rooms" to 1L,
                            "public_rooms" to 2L,
                            "messages" to 3L,
                            "files" to 4L,
                        ),
                ),
            )

        assertEquals("my-next", parsed.nextCursors.myRooms)
        assertNull(parsed.nextCursors.publicRooms)
        assertEquals("message-next", parsed.nextCursors.messages)
        assertEquals("file-next", parsed.nextCursors.files)
        assertEquals(1, parsed.totalCounts.myRooms)
        assertEquals(2, parsed.totalCounts.publicRooms)
        assertEquals(3, parsed.totalCounts.messages)
        assertEquals(4, parsed.totalCounts.files)

        val current =
            GlobalSearchResults(
                myRooms = listOf(_room("room_1")),
                publicRooms = listOf(_publicRoom("public_1")),
                messages = listOf(_messageResult("room_1")),
                files = listOf(_messageResult("room_1", type = "file")),
                nextCursors =
                    GlobalSearchCursors(
                        myRooms = "my-next",
                        publicRooms = "public-next",
                        messages = "message-next",
                        files = "file-next",
                    ),
                totalCounts =
                    GlobalSearchCounts(
                        myRooms = 1,
                        publicRooms = 1,
                        messages = 8,
                        files = 1,
                    ),
            )
        val page =
            GlobalSearchResults(
                myRooms = emptyList(),
                publicRooms = emptyList(),
                messages = listOf(_messageResult("room_2")),
                files = emptyList(),
                nextCursors = GlobalSearchCursors(messages = "message-next-2"),
                totalCounts = GlobalSearchCounts(messages = 9),
            )

        val merged =
            globalSearchResultsByAppendingPage(
                current = current,
                page = page,
                categories = listOf(GlobalSearchCategory.messages),
            )

        assertEquals(current.myRooms, merged.myRooms)
        assertEquals(current.publicRooms, merged.publicRooms)
        assertEquals(2, merged.messages.size)
        assertEquals(current.files, merged.files)
        assertEquals("my-next", merged.nextCursors.myRooms)
        assertEquals("public-next", merged.nextCursors.publicRooms)
        assertEquals("message-next-2", merged.nextCursors.messages)
        assertEquals("file-next", merged.nextCursors.files)
        assertEquals(1, merged.totalCounts.myRooms)
        assertEquals(1, merged.totalCounts.publicRooms)
        assertEquals(9, merged.totalCounts.messages)
        assertEquals(1, merged.totalCounts.files)
    }

    @Test
    fun `sidebar search filter only applies to active my rooms category`() {
        val rooms = listOf(_room("room_1"), _room("room_2"))
        val results =
            GlobalSearchResults(
                myRooms = listOf(_room("room_2")),
                publicRooms = emptyList(),
                messages = emptyList(),
                files = emptyList(),
            )

        assertEquals(
            rooms,
            sidebarRoomsForSearch(
                rooms = rooms,
                query = "alpha",
                activeCategory = null,
                results = results,
            ),
        )
        assertEquals(
            rooms,
            sidebarRoomsForSearch(
                rooms = rooms,
                query = "alpha",
                activeCategory = GlobalSearchCategory.messages,
                results = results,
            ),
        )
        assertEquals(
            listOf("room_2"),
            sidebarRoomsForSearch(
                rooms = rooms,
                query = "alpha",
                activeCategory = GlobalSearchCategory.myRooms,
                results = results,
            ).map { room -> room.id },
        )
        assertEquals(
            rooms,
            sidebarRoomsForSearch(
                rooms = rooms,
                query = " ",
                activeCategory = GlobalSearchCategory.myRooms,
                results = results,
            ),
        )
    }

    @Test
    fun `global search view hides my rooms that only match hidden details`() {
        val results =
            GlobalSearchResults(
                myRooms =
                    listOf(
                        _room("hidden", name = "Hidden", description = "12345"),
                        _room("title", name = "Room 1"),
                        _room("message", name = "Notes", lastMessageBody = "visible 1"),
                        _room("rid", name = "RID Room", rid = "20000001"),
                    ),
                publicRooms = emptyList(),
                messages = emptyList(),
                files = emptyList(),
            )

        val visibleForOne = globalSearchResultsForView(results, query = "1")
        assertEquals(listOf("title", "message"), visibleForOne.myRooms.map { room -> room.id })

        val visibleForRid =
            globalSearchResultsForView(
                results,
                query = "20000001",
            )
        assertEquals(listOf("rid"), visibleForRid.myRooms.map { room -> room.id })
    }

    @Test
    fun `message search title uses room and subtitle keeps sender with message`() {
        val result =
            _messageResult(
                "room_1",
                createdAt = DateTime(2026, 6, 1, 9, 30),
            )

        assertEquals("Alpha", globalSearchMessageTitle(result))
        assertEquals("Alice", globalSearchMessageSenderName(result))
        assertEquals("Alice · alpha roadmap", globalSearchMessageSubtitle(result))
        assertEquals("2026/06/01 09:30", globalSearchResultTimeLabel(result))
    }

    @Test
    fun `file search display uses filename and asset metadata`() {
        val result = _messageResult("room_1", type = "file")

        assertEquals("alpha.pdf", globalSearchFileTitle(result))
        assertEquals("application/pdf - 4.0 KB", globalSearchFileSubtitle(result))
    }

    private fun _user(id: String): UserSummary {
        return UserSummary(
            id = id,
            username = "alice",
            displayName = "Alice",
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
        )
    }

    private fun _room(
        id: String,
        name: String? = null,
        rid: String = "",
        description: String = "",
        lastMessageBody: String? = null,
    ): RoomCard {
        return RoomCard(
            id = id,
            name = name ?: (if (id == "room_1") "Alpha" else "Beta"),
            rid = rid,
            description = description,
            avatarUrl = null,
            defaultAvatarKey = "room-1",
            memberCount = 2,
            liveParticipantCount = 0,
            liveAvatarPreview = emptyList(),
            lastMessage =
                if (lastMessageBody == null) {
                    null
                } else {
                    LastMessagePreview(
                        id = "last_$id",
                        type = "text",
                        senderDisplayName = "Alice",
                        bodyPreview = lastMessageBody,
                        createdAt = DateTime.utc(2026, 6, 1),
                    )
                },
            unreadCount = 0,
            updatedAt = DateTime.utc(2026, 6, 1),
        )
    }

    private fun _publicRoom(id: String): PublicRoom {
        return PublicRoom(
            id = id,
            rid = "900002",
            name = "Public",
            avatarUrl = null,
            defaultAvatarKey = "room-2",
            visibility = "public",
            joinPolicy = "open",
            memberCount = 2,
            liveParticipantCount = 0,
            joined = false,
            joinState = "none",
        )
    }

    private fun _messageResult(
        roomId: String,
        type: String = "text",
        createdAt: DateTime? = null,
    ): MessageSearchResult {
        return MessageSearchResult(
            room =
                SearchRoomContext(
                    id = roomId,
                    rid = "900001",
                    name = "Alpha",
                    avatarUrl = null,
                    defaultAvatarKey = "room-1",
                ),
            message =
                Message(
                    id = "msg_$type",
                    roomId = roomId,
                    sender =
                        UserSummary(
                            id = "user_1",
                            username = "alice",
                            displayName = "Alice",
                            avatarUrl = null,
                            defaultAvatarKey = "blue-3",
                        ),
                    clientMessageId = "cmsg_$type",
                    type = type,
                    body = if (type == "file") "alpha.pdf" else "alpha roadmap",
                    createdAt = createdAt ?: DateTime.utc(2026, 6, 1),
                    attachments =
                        if (type == "file") {
                            listOf(
                                MessageAttachment(
                                    type = "file",
                                    name = "alpha.pdf",
                                    asset =
                                        UploadedAsset(
                                            id = "asset_1",
                                            url = "/alpha.pdf",
                                            thumbnailUrl = null,
                                            mimeType = "application/pdf",
                                            filename = "alpha.pdf",
                                            sizeBytes = 4096,
                                        ),
                                ),
                            )
                        } else {
                            emptyList()
                        },
                ),
        )
    }
}
