package com.gangchat.client.live

import android.media.AudioAttributes
import com.gangchat.client.platform.SoundPoolCuePlayer
import com.gangchat.client.platform.playAndLoad
import com.gangchat.client.testing.GangTestRule
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Port of test/unit/live/live_presence_sound_service_test.dart, plus the Android playback fix. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LivePresenceSoundServiceTest {
    @get:Rule
    val gang = GangTestRule()

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

    @Test
    fun `cues play on the call route during a call and as media otherwise`() =
        gang.runTest {
            val callCues = SoundPoolCuePlayer(usage = AudioAttributes.USAGE_VOICE_COMMUNICATION)
            val mediaCues = SoundPoolCuePlayer(usage = AudioAttributes.USAGE_MEDIA)
            var inCall = true
            val service = LivePresenceSoundService(callCues, mediaCues, isInCall = { inCall })
            val joined = LivePresenceSoundService.cueName(LivePresenceSound.joined)
            val left = LivePresenceSoundService.cueName(LivePresenceSound.left)

            // The Flutter build never sounded these on Android.
            playAndLoad(callCues, joined) { service.play(LivePresenceSound.joined, volume = 0.5) }
            assertArrayEquals(buildLivePresenceTone(LivePresenceSound.joined), callCues.cueFile(joined).readBytes())
            val joinedPlaybacks = shadowOf(callCues.pool).getPathPlaybacks(callCues.cueFile(joined).path)
            assertEquals(1, joinedPlaybacks.size)
            assertEquals(0.5f, joinedPlaybacks[0].leftVolume, 0.0001f)
            assertNull(mediaCues.pool)

            // A leave cue after the call ended.
            inCall = false
            playAndLoad(mediaCues, left) { service.play(LivePresenceSound.left, volume = 1.0) }
            assertTrue(shadowOf(mediaCues.pool).wasPathPlayed(mediaCues.cueFile(left).path))
            assertEquals(1, shadowOf(callCues.pool).getPathPlaybacks(callCues.cueFile(joined).path).size)

            service.dispose()
            assertNull(callCues.pool)
            assertNull(mediaCues.pool)
        }
}
