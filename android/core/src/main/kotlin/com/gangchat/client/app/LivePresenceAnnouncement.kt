package com.gangchat.client.app

interface LivePresenceSpeechPlayer {
    suspend fun speak(
        announcement: LivePresenceAnnouncement,
        volume: Double,
    )

    suspend fun dispose()
}

enum class LivePresenceAnnouncementAction { joined, left, removed }

fun shouldSpeakLivePresenceAnnouncement(
    enabled: Boolean,
    participantIdentity: String?,
    currentUserIdentity: String,
): Boolean {
    val identity = participantIdentity?.trim()
    return enabled &&
        identity != null &&
        identity.isNotEmpty() &&
        identity != currentUserIdentity
}

class LivePresenceAnnouncement(
    val roleLabel: String,
    val roomDisplayName: String,
    val action: LivePresenceAnnouncementAction,
) {
    val actionLabel: String
        get() =
            when (action) {
                LivePresenceAnnouncementAction.joined -> "进入了语音频道"
                LivePresenceAnnouncementAction.left -> "离开了语音频道"
                LivePresenceAnnouncementAction.removed -> "被踢出了语音频道"
            }

    /**
     * Separate segments let the platform TTS adapter insert real pauses instead
     * of relying on punctuation, whose timing varies between installed voices.
     */
    val segments: List<String> get() = listOf(roleLabel, roomDisplayName, actionLabel)
}
