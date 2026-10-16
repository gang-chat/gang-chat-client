package com.gangchat.client.shell

import com.gangchat.client.platform.AudioPlayer
import com.gangchat.client.platform.BytesSource
import com.gangchat.client.platform.PlayerMode
import com.gangchat.client.platform.Source
import kotlinx.coroutines.CancellationException

interface MessageNotificationSoundPlayer {
    suspend fun play(volume: Double)

    suspend fun dispose()
}

/**
 * Plays the short, locally synthesized cue for a realtime text-channel
 * message. No network request or packaged binary asset is required.
 *
 * Note: like the Flutter build on Android (audioplayers_android rejects byte
 * sources in low-latency mode), [play] fails with a `PlatformException`; the
 * caller ignores the error, so Android plays no cue. See [AudioPlayer].
 */
class MessageNotificationSoundService(player: AudioPlayer? = null) : MessageNotificationSoundPlayer {
    private val _player: AudioPlayer = player ?: AudioPlayer()
    private var _disposed = false

    override suspend fun play(volume: Double) {
        if (_disposed) return
        val normalizedVolume = volume.coerceIn(0.0, 1.0)
        if (normalizedVolume <= 0) return
        _player.stop()
        _player.play(
            _sourceFor(_tone),
            volume = normalizedVolume,
            mode = PlayerMode.lowLatency,
        )
    }

    private fun _sourceFor(bytes: ByteArray): Source = BytesSource(bytes, mimeType = "audio/wav")

    override suspend fun dispose() {
        if (_disposed) return
        _disposed = true
        try {
            _player.dispose()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
        }
    }

    private companion object {
        val _tone: ByteArray by lazy { buildMessageNotificationTone() }
    }
}

fun messageNotificationSoundFrequencies(): List<Double> {
    return listOf(659.25, 880.0)
}

fun buildMessageNotificationTone(sampleRate: Int = 44100): ByteArray {
    val frequencies = messageNotificationSoundFrequencies()
    return buildSynthesizedToneWav(
        sampleRate = sampleRate,
        tailDuration = 0.022,
        amplitude = 0.2,
        notes =
            listOf(
                SynthesizedToneNote(frequency = frequencies[0], duration = 0.075, gapAfter = 0.018),
                SynthesizedToneNote(frequency = frequencies[1], duration = 0.115),
            ),
    )
}
