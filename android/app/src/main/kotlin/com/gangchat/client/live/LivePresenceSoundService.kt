package com.gangchat.client.live

import com.gangchat.client.foundation.future
import com.gangchat.client.foundation.waitAll
import com.gangchat.client.platform.AudioPlayer
import com.gangchat.client.platform.BytesSource
import com.gangchat.client.platform.PlayerMode
import com.gangchat.client.platform.Source
import com.gangchat.client.shell.SynthesizedToneNote
import com.gangchat.client.shell.buildSynthesizedToneWav
import kotlin.coroutines.cancellation.CancellationException

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
 * Note: like the Flutter build on Android (audioplayers_android rejects byte
 * sources in low-latency mode), [play] fails with a `PlatformException`; see
 * [AudioPlayer].
 */
class LivePresenceSoundService(
    joinedPlayer: AudioPlayer? = null,
    leftPlayer: AudioPlayer? = null,
) : LivePresenceSoundPlayer {
    private val _joinedPlayer: AudioPlayer = joinedPlayer ?: AudioPlayer()
    private val _leftPlayer: AudioPlayer = leftPlayer ?: AudioPlayer()
    private var _disposed = false

    override suspend fun play(sound: LivePresenceSound, volume: Double) {
        if (_disposed) return
        val normalizedVolume = volume.coerceIn(0.0, 1.0)
        if (normalizedVolume <= 0) return
        val player = if (sound == LivePresenceSound.joined) _joinedPlayer else _leftPlayer
        val bytes = if (sound == LivePresenceSound.joined) _joinedTone else _leftTone
        player.stop()
        player.play(
            _sourceFor(bytes),
            volume = normalizedVolume,
            mode = PlayerMode.lowLatency,
        )
    }

    private fun _sourceFor(bytes: ByteArray): Source = BytesSource(bytes, mimeType = "audio/wav")

    override suspend fun dispose() {
        if (_disposed) return
        _disposed = true
        waitAll(
            listOf(
                future { _disposeQuietly(_joinedPlayer) },
                future { _disposeQuietly(_leftPlayer) },
            ),
        )
    }

    private suspend fun _disposeQuietly(player: AudioPlayer) {
        try {
            player.dispose()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    private companion object {
        val _joinedTone: ByteArray by lazy { buildLivePresenceTone(LivePresenceSound.joined) }
        val _leftTone: ByteArray by lazy { buildLivePresenceTone(LivePresenceSound.left) }
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
