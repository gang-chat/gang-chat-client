package com.gangchat.client.shell

import com.gangchat.client.app.LivePresenceAnnouncement
import com.gangchat.client.app.LivePresenceSpeechPlayer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Free local TTS backed by the operating system's installed voices (Android's
 * TextToSpeech through [AndroidSystemService]).
 *
 * It intentionally lives behind an app-layer protocol so room/live business
 * logic never depends on process APIs or a particular speech engine.
 */
class SystemLivePresenceSpeechPlayer(
    val pause: Duration = 280.milliseconds,
    val androidSystemService: AndroidSystemService = AndroidSystemService(),
) : LivePresenceSpeechPlayer {
    private var _disposed = false

    override suspend fun speak(announcement: LivePresenceAnnouncement, volume: Double) {
        if (_disposed || volume <= 0) return
        val segments =
            announcement.segments
                .map { segment -> segment.trim() }
                .filter { segment -> segment.isNotEmpty() }
        if (segments.isEmpty()) return

        try {
            androidSystemService.speak(
                segments = segments,
                volume = volume,
                pause = pause,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Presence speech is best-effort and must never interrupt live controls.
        }
    }

    override suspend fun dispose() {
        _disposed = true
        try {
            androidSystemService.disposeSpeech()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }
}
