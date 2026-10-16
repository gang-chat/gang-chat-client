package com.gangchat.client.app

const val realtimeMessageCreatedUpdateReason = "message_created"

/**
 * Tracks realtime message notifications without conflating them with room
 * snapshots loaded during sign-in or reconnect recovery.
 */
class RealtimeMessageNotificationTracker(val capacity: Int = 256) {
    private val _seenMessageKeys = LinkedHashSet<String>()

    fun register(
        roomId: String,
        updateReason: String,
        notificationPolicy: String,
        messageId: String?,
    ): Boolean {
        if (updateReason.trim().lowercase() != realtimeMessageCreatedUpdateReason) {
            return false
        }
        val normalizedRoomId = roomId.trim()
        val normalizedMessageId = messageId?.trim() ?: ""
        if (normalizedRoomId.isEmpty() || normalizedMessageId.isEmpty()) return false

        val key = "$normalizedRoomId:$normalizedMessageId"
        if (!_seenMessageKeys.add(key)) return false
        while (_seenMessageKeys.size > capacity) {
            _seenMessageKeys.remove(_seenMessageKeys.first())
        }

        return normalizeRoomNotificationPolicy(notificationPolicy) == "all"
    }

    fun clear() = _seenMessageKeys.clear()
}
