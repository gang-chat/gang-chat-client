package com.gangchat.client.app

import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.RoomCard

fun patchRoomLiveCount(
    rooms: List<RoomCard>,
    roomId: String,
    live: LiveState,
): List<RoomCard> {
    return rooms.map { room -> if (room.id == roomId) roomCardWithLive(room, live) else room }
}

/**
 * PORT: like the Dart constructor call, this does not copy `gangBotAvailable`
 * / `gangBotEnabled`, so the patched card falls back to their defaults.
 */
fun roomCardWithLive(room: RoomCard, live: LiveState): RoomCard {
    val preview = live.participants.take(5).map { item -> item.user }
    return RoomCard(
        id = room.id,
        name = room.name,
        rid = room.rid,
        visibility = room.visibility,
        remarkName = room.remarkName,
        description = room.description,
        notificationPolicy = room.notificationPolicy,
        isPinned = room.isPinned,
        avatarUrl = room.avatarUrl,
        defaultAvatarKey = room.defaultAvatarKey,
        memberCount = room.memberCount,
        onlineMemberCount = room.onlineMemberCount,
        liveParticipantCount = live.participantCount,
        liveAvatarPreview = preview,
        lastMessage = room.lastMessage,
        unreadCount = room.unreadCount,
        hasUnreadCount = room.hasUnreadCount,
        unreadMentionCount = room.unreadMentionCount,
        hasUnreadMentionCount = room.hasUnreadMentionCount,
        hasPendingJoinRequests = room.hasPendingJoinRequests,
        aiVoiceAnnouncementsEnabled = room.aiVoiceAnnouncementsEnabled,
        updatedAt = room.updatedAt,
    )
}
