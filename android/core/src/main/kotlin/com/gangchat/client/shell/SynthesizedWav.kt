package com.gangchat.client.shell

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.truncate

class SynthesizedToneNote(
    val frequency: Double,
    val duration: Double,
    val gapAfter: Double = 0.0,
)

/**
 * Builds a mono 16-bit PCM WAV cue with the same soft harmonic envelope used
 * by Gang Chat's short local notification sounds.
 */
fun buildSynthesizedToneWav(
    notes: List<SynthesizedToneNote>,
    sampleRate: Int = 44100,
    tailDuration: Double = 0.025,
    amplitude: Double = 0.24,
): ByteArray {
    if (notes.isEmpty()) throw IllegalArgumentException("Invalid argument (notes): $notes")
    if (sampleRate <= 0) throw IllegalArgumentException("Invalid argument (sampleRate): $sampleRate")
    if (tailDuration < 0) {
        throw IllegalArgumentException("Invalid argument (tailDuration): $tailDuration")
    }
    for (note in notes) {
        if (note.frequency <= 0 || note.duration <= 0 || note.gapAfter < 0) {
            throw IllegalArgumentException("Invalid argument (notes): $note")
        }
    }

    val totalDuration =
        notes.fold(tailDuration) { total, note -> total + note.duration + note.gapAfter }
    val frameCount = _dartRound(sampleRate * totalDuration).toInt()
    val pcmLength = frameCount * 2
    val bytes = ByteArray(44 + pcmLength)
    val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    fun writeAscii(offset: Int, value: String) {
        for (index in value.indices) {
            data.put(offset + index, value[index].code.toByte())
        }
    }

    writeAscii(0, "RIFF")
    data.putInt(4, 36 + pcmLength)
    writeAscii(8, "WAVE")
    writeAscii(12, "fmt ")
    data.putInt(16, 16)
    data.putShort(20, 1)
    data.putShort(22, 1)
    data.putInt(24, sampleRate)
    data.putInt(28, sampleRate * 2)
    data.putShort(32, 2)
    data.putShort(34, 16)
    writeAscii(36, "data")
    data.putInt(40, pcmLength)

    fun noteSample(localTime: Double, duration: Double, frequency: Double): Double {
        if (localTime < 0 || localTime >= duration) return 0.0
        val attackDuration = 0.008
        val releaseDuration = 0.045
        val attack = (localTime / attackDuration).coerceIn(0.0, 1.0)
        val release = ((duration - localTime) / releaseDuration).coerceIn(0.0, 1.0)
        val envelope = sin(PI * 0.5 * min(attack, release))
        val phase = 2 * PI * frequency * localTime
        return envelope *
            (sin(phase) +
                0.16 * sin(phase * 2) +
                0.04 * sin(phase * 3))
    }

    for (frame in 0 until frameCount) {
        val time = frame.toDouble() / sampleRate
        var noteStart = 0.0
        var sample = 0.0
        for (note in notes) {
            val noteEnd = noteStart + note.duration
            if (time >= noteStart && time < noteEnd) {
                sample = noteSample(time - noteStart, note.duration, note.frequency)
                break
            }
            noteStart = noteEnd + note.gapAfter
        }
        val pcm = _dartRound(sample * amplitude * 32767).coerceIn(-32768L, 32767L)
        data.putShort(44 + frame * 2, pcm.toInt().toShort())
    }
    return bytes
}

/** Dart's `double.round()`: the closest integer, halves rounded away from zero. */
private fun _dartRound(value: Double): Long {
    val truncated = truncate(value)
    val fraction = value - truncated
    return when {
        fraction >= 0.5 -> truncated.toLong() + 1
        fraction <= -0.5 -> truncated.toLong() - 1
        else -> truncated.toLong()
    }
}
