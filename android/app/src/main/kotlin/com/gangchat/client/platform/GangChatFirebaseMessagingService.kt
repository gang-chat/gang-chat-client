package com.gangchat.client.platform

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives FCM data messages while the app is not in the foreground and turns
 * `room_message` payloads into room notifications; token refreshes go to
 * [GangChatPushRegistrationProvider].
 */
class GangChatFirebaseMessagingService : FirebaseMessagingService() {
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        GangChatPushRegistrationProvider.onTokenRefreshed(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        handleData(message.data)
    }

    internal fun handleData(data: Map<String, String>) {
        if (GangChatAppVisibility.isForeground) return
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

/**
 * Whether the main activity is started (between `onStart` and `onStop`).
 * Push messages and deferred update installs check it.
 */
object GangChatAppVisibility {
    @Volatile
    var isForeground: Boolean = false
}
