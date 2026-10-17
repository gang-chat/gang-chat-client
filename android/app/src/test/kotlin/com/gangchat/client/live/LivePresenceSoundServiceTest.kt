package com.gangchat.client.live

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of test/unit/live/live_presence_sound_service_test.dart. */
class LivePresenceSoundServiceTest {
    @Test
    fun `join cue rises and leave cue falls`() {
        val joined = livePresenceSoundNoteFrequencies(LivePresenceSound.joined)
        val left = livePresenceSoundNoteFrequencies(LivePresenceSound.left)

        assertTrue(joined.first() < joined.last())
        assertTrue(left.first() > left.last())
        assertEquals(joined.reversed(), left)
    }

    @Test
    fun `presence cues are valid mono PCM WAV data`() {
        for (sound in LivePresenceSound.entries) {
            val bytes = buildLivePresenceTone(sound)
            val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            assertEquals("RIFF", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
            assertEquals("WAVE", String(bytes.copyOfRange(8, 12), Charsets.US_ASCII))
            assertEquals(1, data.getShort(20).toInt())
            assertEquals(1, data.getShort(22).toInt())
            assertEquals(44100, data.getInt(24))
            assertEquals(16, data.getShort(34).toInt())
            assertEquals(bytes.size - 44, data.getInt(40))
            assertTrue(bytes.size > 20000)
        }
    }
}
