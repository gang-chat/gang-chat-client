package com.gangchat.client.live

import com.gangchat.client.app.LivePresenceAnnouncement
import com.gangchat.client.app.LivePresenceSpeechPlayer
import com.gangchat.client.foundation.completedFuture
import com.gangchat.client.foundation.delayed
import com.gangchat.client.foundation.future
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Deferred

/**
 * Serializes presence audio so bursts of joins/leaves remain intelligible.
 *
 * Each item always starts with the existing cue. When an announcement is
 * supplied, speech starts only after the short cue has finished.
 */
class LivePresenceAudioCoordinator(
    soundPlayer: LivePresenceSoundPlayer,
    speechPlayer: LivePresenceSpeechPlayer,
    val cueDuration: Duration = 330.milliseconds,
    val itemGap: Duration = 90.milliseconds,
) {
    private val _soundPlayer = soundPlayer
    private val _speechPlayer = speechPlayer

    private var _tail: Deferred<Unit> = completedFuture(Unit)
    private var _disposed = false

    fun play(
        sound: LivePresenceSound,
        volume: Double,
        announcement: LivePresenceAnnouncement? = null,
    ): Deferred<Unit> {
        if (_disposed || volume <= 0) return completedFuture(Unit)
        val normalizedVolume = volume.coerceIn(0.0, 1.0)
        val previous = _tail
        val completion =
            future {
                try {
                    previous.await()
                    if (_disposed) return@future
                    _soundPlayer.play(sound, volume = normalizedVolume)
                    delayed(cueDuration)
                    if (_disposed) return@future
                    if (announcement != null) {
                        _speechPlayer.speak(announcement, volume = normalizedVolume)
                    }
                    if (itemGap > Duration.ZERO) {
                        delayed(itemGap)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        _tail = completion
        return completion
    }

    fun dispose() {
        _disposed = true
    }
}
