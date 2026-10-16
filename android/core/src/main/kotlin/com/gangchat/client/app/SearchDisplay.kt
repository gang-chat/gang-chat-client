package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.GlobalSearchCounts
import com.gangchat.client.protocol.GlobalSearchCursors
import com.gangchat.client.protocol.GlobalSearchResults
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.MessageSearchResult
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.SearchRoomContext

enum class GlobalSearchCategory { myRooms, publicRooms, userSettings, messages, files }

val globalSearchCategories: List<GlobalSearchCategory> =
    listOf(
        GlobalSearchCategory.myRooms,
        GlobalSearchCategory.publicRooms,
        GlobalSearchCategory.messages,
        GlobalSearchCategory.files,
    )

val superuserGlobalSearchCategories: List<GlobalSearchCategory> =
    listOf(
        GlobalSearchCategory.myRooms,
        GlobalSearchCategory.userSettings,
        GlobalSearchCategory.messages,
        GlobalSearchCategory.files,
    )

fun globalSearchCategoryKey(category: GlobalSearchCategory): String {
    return when (category) {
        GlobalSearchCategory.myRooms -> "my_rooms"
        GlobalSearchCategory.publicRooms -> "public_rooms"
        GlobalSearchCategory.userSettings -> "user_settings"
        GlobalSearchCategory.messages -> "messages"
        GlobalSearchCategory.files -> "files"
    }
}

fun globalSearchCursorForCategory(
    cursors: GlobalSearchCursors,
    category: GlobalSearchCategory,
): String? {
    return when (category) {
        GlobalSearchCategory.myRooms -> cursors.myRooms
        GlobalSearchCategory.publicRooms -> cursors.publicRooms
        GlobalSearchCategory.userSettings -> cursors.userSettings
        GlobalSearchCategory.messages -> cursors.messages
        GlobalSearchCategory.files -> cursors.files
    }
}

fun globalSearchCursorsForCategories(
    cursors: GlobalSearchCursors,
    categories: Iterable<GlobalSearchCategory>,
): GlobalSearchCursors {
    var myRooms: String? = null
    var publicRooms: String? = null
    var userSettings: String? = null
    var messages: String? = null
    var files: String? = null
    for (category in categories) {
        when (category) {
            GlobalSearchCategory.myRooms -> myRooms = cursors.myRooms
            GlobalSearchCategory.publicRooms -> publicRooms = cursors.publicRooms
            GlobalSearchCategory.userSettings -> userSettings = cursors.userSettings
            GlobalSearchCategory.messages -> messages = cursors.messages
            GlobalSearchCategory.files -> files = cursors.files
        }
    }
    return GlobalSearchCursors(
        myRooms = myRooms,
        publicRooms = publicRooms,
        userSettings = userSettings,
        messages = messages,
        files = files,
    )
}

fun globalSearchResultsByAppendingPage(
    current: GlobalSearchResults,
    page: GlobalSearchResults,
    categories: Iterable<GlobalSearchCategory>,
): GlobalSearchResults {
    var appendMyRooms = false
    var appendPublicRooms = false
    var appendUserSettings = false
    var appendMessages = false
    var appendFiles = false
    for (category in categories) {
        when (category) {
            GlobalSearchCategory.myRooms -> appendMyRooms = true
            GlobalSearchCategory.publicRooms -> appendPublicRooms = true
            GlobalSearchCategory.userSettings -> appendUserSettings = true
            GlobalSearchCategory.messages -> appendMessages = true
            GlobalSearchCategory.files -> appendFiles = true
        }
    }

    return GlobalSearchResults(
        myRooms = if (appendMyRooms) current.myRooms + page.myRooms else current.myRooms,
        publicRooms =
            if (appendPublicRooms) current.publicRooms + page.publicRooms else current.publicRooms,
        userSettings =
            if (appendUserSettings) current.userSettings + page.userSettings else current.userSettings,
        messages = if (appendMessages) current.messages + page.messages else current.messages,
        files = if (appendFiles) current.files + page.files else current.files,
        nextCursors =
            GlobalSearchCursors(
                myRooms =
                    if (appendMyRooms) page.nextCursors.myRooms else current.nextCursors.myRooms,
                publicRooms =
                    if (appendPublicRooms) page.nextCursors.publicRooms else current.nextCursors.publicRooms,
                userSettings =
                    if (appendUserSettings) page.nextCursors.userSettings else current.nextCursors.userSettings,
                messages =
                    if (appendMessages) page.nextCursors.messages else current.nextCursors.messages,
                files = if (appendFiles) page.nextCursors.files else current.nextCursors.files,
            ),
        totalCounts =
            GlobalSearchCounts(
                myRooms =
                    if (appendMyRooms) {
                        page.totalCounts.myRooms ?: current.totalCounts.myRooms
                    } else {
                        current.totalCounts.myRooms
                    },
                publicRooms =
                    if (appendPublicRooms) {
                        page.totalCounts.publicRooms ?: current.totalCounts.publicRooms
                    } else {
                        current.totalCounts.publicRooms
                    },
                userSettings =
                    if (appendUserSettings) {
                        page.totalCounts.userSettings ?: current.totalCounts.userSettings
                    } else {
                        current.totalCounts.userSettings
                    },
                messages =
                    if (appendMessages) {
                        page.totalCounts.messages ?: current.totalCounts.messages
                    } else {
                        current.totalCounts.messages
                    },
                files =
                    if (appendFiles) {
                        page.totalCounts.files ?: current.totalCounts.files
                    } else {
                        current.totalCounts.files
                    },
            ),
    )
}

fun hasGlobalSearchQuery(query: String): Boolean {
    return query.trim().isNotEmpty()
}

fun globalSearchCategoryLabel(category: GlobalSearchCategory): String {
    return when (category) {
        GlobalSearchCategory.myRooms -> "我的房间"
        GlobalSearchCategory.publicRooms -> "公开房间"
        GlobalSearchCategory.userSettings -> "用户设置"
        GlobalSearchCategory.messages -> "聊天记录"
        GlobalSearchCategory.files -> "聊天文件"
    }
}

fun globalSearchCategoryCount(
    results: GlobalSearchResults?,
    category: GlobalSearchCategory,
): Int {
    if (results == null) return 0
    return when (category) {
        GlobalSearchCategory.myRooms -> results.totalCounts.myRooms ?: results.myRooms.size
        GlobalSearchCategory.publicRooms -> results.totalCounts.publicRooms ?: results.publicRooms.size
        GlobalSearchCategory.userSettings -> results.totalCounts.userSettings ?: results.userSettings.size
        GlobalSearchCategory.messages -> results.totalCounts.messages ?: results.messages.size
        GlobalSearchCategory.files -> results.totalCounts.files ?: results.files.size
    }
}

fun globalSearchHasResults(results: GlobalSearchResults?): Boolean {
    if (results == null) return false
    return results.myRooms.isNotEmpty() ||
        results.publicRooms.isNotEmpty() ||
        results.userSettings.isNotEmpty() ||
        results.messages.isNotEmpty() ||
        results.files.isNotEmpty()
}

fun globalSearchResultsForView(
    results: GlobalSearchResults,
    query: String,
): GlobalSearchResults {
    return GlobalSearchResults(
        myRooms = visibleMyRoomSearchResults(rooms = results.myRooms, query = query),
        publicRooms = results.publicRooms,
        userSettings = results.userSettings,
        messages = results.messages,
        files = results.files,
        nextCursors = results.nextCursors,
        totalCounts = results.totalCounts,
    )
}

fun visibleMyRoomSearchResults(
    rooms: Iterable<RoomCard>,
    query: String,
): List<RoomCard> {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) return rooms.toList()
    return rooms.filter { room -> _visibleRoomCardMatches(room, normalizedQuery) }
}

fun sidebarRoomsForSearch(
    rooms: List<RoomCard>,
    query: String,
    activeCategory: GlobalSearchCategory?,
    results: GlobalSearchResults?,
): List<RoomCard> {
    if (!hasGlobalSearchQuery(query)) return rooms
    if (activeCategory != GlobalSearchCategory.myRooms) return rooms
    return results?.myRooms ?: emptyList()
}

fun roomCardFromSearchContext(
    room: SearchRoomContext,
    updatedAt: DateTime,
): RoomCard {
    return RoomCard(
        id = room.id,
        name = room.name,
        rid = room.rid,
        remarkName = room.remarkName,
        avatarUrl = room.avatarUrl,
        defaultAvatarKey = room.defaultAvatarKey,
        memberCount = 0,
        liveParticipantCount = 0,
        liveAvatarPreview = emptyList(),
        lastMessage = null,
        unreadCount = 0,
        updatedAt = updatedAt,
    )
}

fun globalSearchMessageTitle(result: MessageSearchResult): String {
    val roomName = result.room.displayName.trim()
    return if (roomName.isNotEmpty()) roomName else "房间"
}

fun globalSearchMessageSubtitle(result: MessageSearchResult): String {
    val senderName = globalSearchMessageSenderName(result)
    val body = result.message.body.trim()
    if (body.isEmpty()) return senderName
    return "$senderName · $body"
}

fun globalSearchMessageSenderName(result: MessageSearchResult): String {
    val roomDisplayName = result.message.sender.roomDisplayName?.trim()
    if (roomDisplayName != null && roomDisplayName.isNotEmpty()) {
        return roomDisplayName
    }
    val displayName = result.message.sender.displayName.trim()
    if (displayName.isNotEmpty()) return displayName
    val username = result.message.sender.username.trim()
    return if (username.isNotEmpty()) username else "用户"
}

fun globalSearchFileTitle(result: MessageSearchResult): String {
    val attachment = _firstFileAttachment(result.message)
    if (attachment != null) return fileAttachmentTitle(attachment)
    val body = result.message.body.trim()
    return if (body.isNotEmpty()) body else "file"
}

fun globalSearchFileSubtitle(result: MessageSearchResult): String {
    val meta = fileAttachmentMeta(_firstFileAttachment(result.message)?.asset)
    return if (meta.isNotEmpty()) meta else "文件"
}

fun globalSearchResultTimeLabel(result: MessageSearchResult): String {
    return roomInviteTimestampLabel(result.message.createdAt)
}

private fun _firstFileAttachment(message: Message): MessageAttachment? {
    return message.fileAttachments.firstOrNull()
}

private fun _visibleRoomCardMatches(room: RoomCard, query: String): Boolean {
    if (_contains(room.name, query)) return true
    if (_contains(room.remarkName, query)) return true
    if (_contains(room.displayName, query)) return true
    if (room.rid.trim().lowercase() == query) return true
    return _contains(roomSidebarSubtitle(room), query)
}

private fun _contains(value: String?, query: String): Boolean {
    val trimmed = value?.trim()?.lowercase()
    return trimmed != null && trimmed.contains(query)
}
