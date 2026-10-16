package com.gangchat.client.shell

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin

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

    val totalDuration = notes.fold(tailDuration) { total, note -> total + note.duration + note.gapAfter }
    val frameCount = dartRound(sampleRate * totalDuration).toInt()
    val pcmLength = frameCount * 2
    val bytes = ByteArray(44 + pcmLength)

    fun writeAscii(offset: Int, value: String) {
        for (index in value.indices) bytes[offset + index] = value[index].code.toByte()
    }

    fun setUint16(offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xff).toByte()
    }

    fun setUint32(offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xff).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xff).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xff).toByte()
    }

    writeAscii(0, "RIFF")
    setUint32(4, 36 + pcmLength)
    writeAscii(8, "WAVE")
    writeAscii(12, "fmt ")
    setUint32(16, 16)
    setUint16(20, 1)
    setUint16(22, 1)
    setUint32(24, sampleRate)
    setUint32(28, sampleRate * 2)
    setUint16(32, 2)
    setUint16(34, 16)
    writeAscii(36, "data")
    setUint32(40, pcmLength)

    fun noteSample(localTime: Double, duration: Double, frequency: Double): Double {
        if (localTime < 0 || localTime >= duration) return 0.0
        val attackDuration = 0.008
        val releaseDuration = 0.045
        val attack = (localTime / attackDuration).coerceIn(0.0, 1.0)
        val release = ((duration - localTime) / releaseDuration).coerceIn(0.0, 1.0)
        val envelope = sin(PI * 0.5 * min(attack, release))
        val phase = 2 * PI * frequency * localTime
        return envelope *
            (sin(phase) + 0.16 * sin(phase * 2) + 0.04 * sin(phase * 3))
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
        val pcm = dartRound(sample * amplitude * 32767).coerceIn(-32768, 32767).toInt()
        setUint16(44 + frame * 2, pcm and 0xffff)
    }
    return bytes
}

/** Dart's `double.round()`: halves round away from zero. */
private fun dartRound(value: Double): Long =
    if (value < 0) -((-value).roundToLong()) else value.roundToLong()
