package com.gangchat.client.shell

import android.os.Looper
import com.gangchat.client.platform.AudioPlayer
import com.gangchat.client.platform.PlatformException
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.pumpEventQueue
import java.io.IOException
import java.time.Duration as JavaDuration
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

/** The voice-message player over the audioplayers-equivalent [AudioPlayer] (no Dart test existed). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoicePlaybackServiceTest {
    @get:Rule
    val gang = GangTestRule()

    private val url = "https://ky-z.com/gang-chat/assets/voice/1.m4a"

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `plays a voice message, tracks duration and resets when it completes`() =
        gang.runTest {
            ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(url), ShadowMediaPlayer.MediaInfo(3000, 0))
            val service = VoicePlaybackService(AudioPlayer())

            val playing = async { service.play(messageId = "m1", resolvedUrl = url) }
            pumpEventQueue()
            assertTrue(service.state.value.isPlaying("m1"))
            idleMainLooper()
            pumpEventQueue()
            playing.await()

            assertTrue(service.state.value.isPlaying("m1"))
            assertEquals(3.seconds, service.state.value.duration)

            shadowOf(Looper.getMainLooper()).idleFor(JavaDuration.ofMillis(3500))
            pumpEventQueue()

            assertNull(service.state.value.activeMessageId)
            assertFalse(service.state.value.playing)
            assertEquals(Duration.ZERO, service.state.value.duration)
            service.dispose()
        }

    @Test
    fun `toggle stops the playing message`() =
        gang.runTest {
            ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(url), ShadowMediaPlayer.MediaInfo(10_000, 0))
            val service = VoicePlaybackService(AudioPlayer())
            val playing = async { service.toggle(messageId = "m1", resolvedUrl = url) }
            pumpEventQueue()
            idleMainLooper()
            pumpEventQueue()
            playing.await()
            assertTrue(service.state.value.isPlaying("m1"))

            service.toggle(messageId = "m1", resolvedUrl = url)

            assertFalse(service.state.value.isPlaying("m1"))
            assertNull(service.state.value.activeMessageId)
            service.dispose()
        }

    @Test
    fun `a source that cannot be opened resets the state and reports the error`() =
        gang.runTest {
            ShadowMediaPlayer.addException(DataSource.toDataSource(url), IOException("unreachable"))
            val service = VoicePlaybackService(AudioPlayer())

            try {
                service.play(messageId = "m1", resolvedUrl = url)
                fail("expected AndroidAudioError")
            } catch (error: PlatformException) {
                assertEquals("AndroidAudioError", error.code)
            }
            assertNull(service.state.value.activeMessageId)
            service.dispose()
        }
}
