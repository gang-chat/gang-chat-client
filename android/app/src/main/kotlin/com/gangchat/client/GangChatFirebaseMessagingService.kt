package com.gangchat.client

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class GangChatFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        GangChatPushRegistrationProvider.onTokenRefreshed(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (GangChatAppVisibility.isForeground) return
        val data = message.data
        if (data["type"] != "room_message") return
        val roomId = data["room_id"]?.trim().orEmpty()
        if (roomId.isEmpty()) return
        GangChatNotifications.showRoomMessage(
            context = this,
            roomId = roomId,
            roomName = data["room_name"].orEmpty(),
            sender = data["sender_name"].orEmpty(),
            body = data["body"].orEmpty(),
            unreadCount = data["unread_count"]?.toIntOrNull() ?: 1,
            messageId = data["message_id"].orEmpty(),
        )
    }
}

object GangChatAppVisibility {
    @Volatile
    var isForeground: Boolean = false
}
