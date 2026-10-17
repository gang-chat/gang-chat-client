package com.gangchat.client.shell

import android.media.AudioAttributes
import com.gangchat.client.platform.SoundPoolCuePlayer

interface MessageNotificationSoundPlayer {
    suspend fun play(volume: Double)

    suspend fun dispose()
}

/**
 * Plays the short, locally synthesized cue for a realtime text-channel
 * message. No network request or packaged binary asset is required.
 *
 * Fixed on Android (the Flutter build never sounded it: audioplayers_android
 * rejected the byte source in low-latency mode). The cue goes through
 * [SoundPoolCuePlayer] as a notification event, so it follows the
 * notification volume and ringer mode, plays on the call route during a voice
 * call, and does not pause other apps' audio.
 */
class MessageNotificationSoundService(cuePlayer: SoundPoolCuePlayer? = null) : MessageNotificationSoundPlayer {
    private val _cuePlayer: SoundPoolCuePlayer =
        cuePlayer ?: SoundPoolCuePlayer(usage = AudioAttributes.USAGE_NOTIFICATION_EVENT)
    private var _disposed = false

    override suspend fun play(volume: Double) {
        if (_disposed) return
        val normalizedVolume = volume.coerceIn(0.0, 1.0)
        if (normalizedVolume <= 0) return
        _cuePlayer.play(cueName, _tone, volume = normalizedVolume)
    }

    override suspend fun dispose() {
        if (_disposed) return
        _disposed = true
        _cuePlayer.dispose()
    }

    companion object {
        internal const val cueName = "message_notification"
        private val _tone: ByteArray by lazy { buildMessageNotificationTone() }
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
