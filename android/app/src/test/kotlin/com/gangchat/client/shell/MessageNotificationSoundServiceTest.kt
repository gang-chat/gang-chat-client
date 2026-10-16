package com.gangchat.client.shell

import com.gangchat.client.platform.AudioPlayer
import com.gangchat.client.platform.PlatformException
import com.gangchat.client.platform.PlayerState
import com.gangchat.client.testing.GangTestRule
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
    fun `Android rejects the byte cue in low-latency mode like audioplayers_android`() =
        gang.runTest {
            val player = AudioPlayer()
            val service = MessageNotificationSoundService(player)

            try {
                service.play(volume = 0.6)
                fail("expected the plugin's AndroidAudioError")
            } catch (error: PlatformException) {
                assertEquals("AndroidAudioError", error.code)
                assertEquals("Bytes sources are not supported on LOW_LATENCY mode yet.", error.message)
            }
            assertEquals(PlayerState.stopped, player.state)

            service.dispose()
            // Silent or disposed: nothing to play.
            service.play(volume = 1.0)
            MessageNotificationSoundService(AudioPlayer()).play(volume = 0.0)
        }
}
