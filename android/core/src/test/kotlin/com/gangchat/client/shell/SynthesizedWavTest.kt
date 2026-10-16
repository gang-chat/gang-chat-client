package com.gangchat.client.shell

import com.gangchat.client.foundation.Size
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Not a Dart test: checks the WAV layout of the synthesized_wav.dart port and the form factor check. */
class SynthesizedWavTest {
    @Test
    fun `builds a mono 16-bit PCM WAV with the expected header`() {
        val bytes =
            buildSynthesizedToneWav(
                notes = listOf(SynthesizedToneNote(frequency = 880.0, duration = 0.05, gapAfter = 0.01)),
                sampleRate = 8000,
            )
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        // (0.025 + 0.05 + 0.01) s at 8 kHz = 680 frames.
        assertEquals(44 + 680 * 2, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(36 + 680 * 2, data.getInt(4))
        assertEquals("WAVEfmt ", String(bytes, 8, 8, Charsets.US_ASCII))
        assertEquals(16, data.getInt(16))
        assertEquals(1, data.getShort(20).toInt())
        assertEquals(1, data.getShort(22).toInt())
        assertEquals(8000, data.getInt(24))
        assertEquals(16000, data.getInt(28))
        assertEquals(2, data.getShort(32).toInt())
        assertEquals(16, data.getShort(34).toInt())
        assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))
        assertEquals(680 * 2, data.getInt(40))
        // The envelope starts silent and the tail after the note stays silent.
        assertEquals(0, data.getShort(44).toInt())
        assertEquals(0, data.getShort(44 + 679 * 2).toInt())
        assertTrue((0 until 680).any { data.getShort(44 + it * 2).toInt() != 0 })
    }

    @Test
    fun `rejects invalid notes`() {
        val error = runCatching { buildSynthesizedToneWav(notes = emptyList()) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `tablet form factor uses the shortest side`() {
        assertTrue(isAndroidTabletLogicalSize(Size(1280.0, 600.0)))
        assertFalse(isAndroidTabletLogicalSize(Size(411.0, 891.0)))
    }
}
