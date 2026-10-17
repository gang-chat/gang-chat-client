package com.gangchat.client.platform

import android.media.AudioAttributes
import com.gangchat.client.foundation.TimeoutException
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.elapse
import com.gangchat.client.testing.pumpEventQueue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
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

/**
 * Runs [play] and completes the SoundPool load it starts: Robolectric's
 * SoundPool never finishes a load by itself.
 */
internal suspend fun TestScope.playAndLoad(cues: SoundPoolCuePlayer, name: String, play: suspend () -> Unit) {
    val playing = async { play() }
    val deadline = System.nanoTime() + 5.seconds.inWholeNanoseconds
    // The cue file is written on Dispatchers.IO before the load starts.
    while (cues.pendingLoadCount == 0 && !playing.isCompleted) {
        check(System.nanoTime() < deadline) { "the $name cue was never loaded" }
        Thread.sleep(2)
        pumpEventQueue()
    }
    cues.pool?.let { shadowOf(it).notifyPathLoaded(cues.cueFile(name).path, true) }
    playing.await()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SoundPoolCuePlayerTest {
    @get:Rule
    val gang = GangTestRule()

    private val wav = byteArrayOf(1, 2, 3, 4)

    @Test
    fun `a cue is written to the cache, loaded once and played at the volume`() =
        gang.runTest {
            val cues = SoundPoolCuePlayer(usage = AudioAttributes.USAGE_NOTIFICATION_EVENT)
            val file = cues.cueFile("ping")

            playAndLoad(cues, "ping") { cues.play("ping", wav, volume = 0.4) }

            assertArrayEquals(wav, file.readBytes())
            val shadow = shadowOf(cues.pool)
            assertEquals(1, shadow.getPathPlaybacks(file.path).size)
            assertEquals(0.4f, shadow.getPathPlaybacks(file.path)[0].leftVolume, 0.0001f)
            assertEquals(0.4f, shadow.getPathPlaybacks(file.path)[0].rightVolume, 0.0001f)

            // Already loaded: plays straight away, clamped to 0..1.
            cues.play("ping", wav, volume = 3.0)
            assertEquals(0, cues.pendingLoadCount)
            val playbacks = shadow.getPathPlaybacks(file.path)
            assertEquals(2, playbacks.size)
            assertEquals(playbacks[0].soundId, playbacks[1].soundId)
            assertEquals(1f, playbacks[1].leftVolume, 0.0001f)
            cues.dispose()
        }

    @Test
    fun `a load that never completes times out and the next play loads again`() =
        gang.runTest {
            val cues = SoundPoolCuePlayer(usage = AudioAttributes.USAGE_MEDIA)
            // runCatching: a failed async child would fail the whole test scope.
            val playing = async { runCatching { cues.play("ping", wav, volume = 1.0) } }
            val deadline = System.nanoTime() + 5.seconds.inWholeNanoseconds
            while (cues.pendingLoadCount == 0) {
                assertTrue("the load never started", System.nanoTime() < deadline)
                Thread.sleep(2)
                pumpEventQueue()
            }
            elapse(5.seconds)
            assertTrue(playing.await().exceptionOrNull() is TimeoutException)
            assertEquals(0, cues.pendingLoadCount)

            playAndLoad(cues, "ping") { cues.play("ping", wav, volume = 1.0) }
            assertTrue(shadowOf(cues.pool).wasPathPlayed(cues.cueFile("ping").path))
            cues.dispose()
        }

    @Test
    fun `a disposed player releases its pool and plays nothing`() =
        gang.runTest {
            val cues = SoundPoolCuePlayer(usage = AudioAttributes.USAGE_MEDIA)
            playAndLoad(cues, "ping") { cues.play("ping", wav, volume = 1.0) }

            cues.dispose()
            assertNull(cues.pool)
            cues.play("ping", wav, volume = 1.0)
            assertNull(cues.pool)
        }
}
