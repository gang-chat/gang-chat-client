package com.gangchat.client.shell

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

/** Port of test/unit/shell/message_notification_sound_service_test.dart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MessageNotificationSoundServiceTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `message notification cue uses a short rising phrase`() {
        val frequencies = messageNotificationSoundFrequencies()

        assertEquals(2, frequencies.size)
        assertTrue(frequencies.first() < frequencies.last())
    }

    @Test
    fun `message notification cue is valid mono PCM WAV data`() {
        val bytes = buildMessageNotificationTone()
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("RIFF", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes.copyOfRange(8, 12), Charsets.US_ASCII))
        assertEquals(1, data.getShort(20).toInt())
        assertEquals(1, data.getShort(22).toInt())
        assertEquals(44100, data.getInt(24))
        assertEquals(16, data.getShort(34).toInt())
        assertEquals(bytes.size - 44, data.getInt(40))
        assertTrue(bytes.size > 15000)
    }

    @Test
    fun `Android plays the cue through a notification-event SoundPool`() =
        gang.runTest {
            val cues = SoundPoolCuePlayer(usage = AudioAttributes.USAGE_NOTIFICATION_EVENT)
            val service = MessageNotificationSoundService(cues)
            val name = MessageNotificationSoundService.cueName

            // The Flutter build never sounded this cue on Android.
            playAndLoad(cues, name) { service.play(volume = 0.6) }

            val file = cues.cueFile(name)
            assertArrayEquals(buildMessageNotificationTone(), file.readBytes())
            val playbacks = shadowOf(cues.pool).getPathPlaybacks(file.path)
            assertEquals(1, playbacks.size)
            assertEquals(0.6f, playbacks[0].leftVolume, 0.0001f)

            // Silent: nothing to play.
            service.play(volume = 0.0)
            assertEquals(1, shadowOf(cues.pool).getPathPlaybacks(file.path).size)

            service.dispose()
            assertNull(cues.pool)
            service.play(volume = 1.0)
            assertNull(cues.pool)
        }
}
