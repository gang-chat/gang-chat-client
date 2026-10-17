package com.gangchat.client.live

import android.media.AudioAttributes
import com.gangchat.client.platform.SoundPoolCuePlayer
import com.gangchat.client.platform.isInVoiceCall
import com.gangchat.client.shell.SynthesizedToneNote
import com.gangchat.client.shell.buildSynthesizedToneWav

enum class LivePresenceSound { joined, left }

interface LivePresenceSoundPlayer {
    suspend fun play(sound: LivePresenceSound, volume: Double)

    suspend fun dispose()
}

/**
 * Plays short, locally generated voice-channel presence cues.
 *
 * The tones are synthesized into WAV data so the cue is available on every
 * supported platform without another network request or a binary asset.
 *
 * Fixed on Android (the Flutter build never sounded them: audioplayers_android
 * rejected the byte source in low-latency mode, which also skipped the
 * announcement chained after the cue). The cues go through
 * [SoundPoolCuePlayer]: during a voice call as voice communication, so they
 * play on the call's route (earpiece, speaker or Bluetooth headset) at the call
 * volume; otherwise (a leave cue after the call ended) as media.
 */
class LivePresenceSoundService(
    callCuePlayer: SoundPoolCuePlayer? = null,
    mediaCuePlayer: SoundPoolCuePlayer? = null,
    private val isInCall: () -> Boolean = { isInVoiceCall() },
) : LivePresenceSoundPlayer {
    private val _callCuePlayer: SoundPoolCuePlayer =
        callCuePlayer ?: SoundPoolCuePlayer(usage = AudioAttributes.USAGE_VOICE_COMMUNICATION)
    private val _mediaCuePlayer: SoundPoolCuePlayer =
        mediaCuePlayer ?: SoundPoolCuePlayer(usage = AudioAttributes.USAGE_MEDIA)
    private var _disposed = false

    override suspend fun play(sound: LivePresenceSound, volume: Double) {
        if (_disposed) return
        val normalizedVolume = volume.coerceIn(0.0, 1.0)
        if (normalizedVolume <= 0) return
        val bytes = if (sound == LivePresenceSound.joined) _joinedTone else _leftTone
        val player = if (isInCall()) _callCuePlayer else _mediaCuePlayer
        player.play(cueName(sound), bytes, volume = normalizedVolume)
    }

    override suspend fun dispose() {
        if (_disposed) return
        _disposed = true
        _callCuePlayer.dispose()
        _mediaCuePlayer.dispose()
    }

    companion object {
        internal fun cueName(sound: LivePresenceSound): String = "live_presence_${sound.name}"

        private val _joinedTone: ByteArray by lazy { buildLivePresenceTone(LivePresenceSound.joined) }
        private val _leftTone: ByteArray by lazy { buildLivePresenceTone(LivePresenceSound.left) }
    }
}

fun livePresenceSoundNoteFrequencies(sound: LivePresenceSound): List<Double> {
    val rising = listOf(523.25, 783.99)
    return if (sound == LivePresenceSound.joined) rising else rising.reversed()
}

fun buildLivePresenceTone(sound: LivePresenceSound, sampleRate: Int = 44100): ByteArray {
    val notes = livePresenceSoundNoteFrequencies(sound)
    return buildSynthesizedToneWav(
        sampleRate = sampleRate,
        tailDuration = 0.025,
        notes =
            listOf(
                SynthesizedToneNote(frequency = notes[0], duration = 0.105, gapAfter = 0.022),
                SynthesizedToneNote(frequency = notes[1], duration = 0.155),
            ),
    )
}
