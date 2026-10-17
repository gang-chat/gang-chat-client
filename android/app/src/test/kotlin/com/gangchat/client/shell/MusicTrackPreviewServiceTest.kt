package com.gangchat.client.shell

import android.os.Looper
import com.gangchat.client.app.MusicTrackPreviewAsset
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.platform.AndroidMusicPreviewPlayer
import com.gangchat.client.platform.MissingPluginException
import com.gangchat.client.platform.PlatformException
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.pumpEventQueue
import java.io.File
import java.time.Duration as JavaDuration
import kotlinx.coroutines.async
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/**
 * The Android preview platform's file cache (no Dart test existed). Runs on
 * the plain JVM, where no native preview player is installed, so playback
 * calls behave like an unanswered `gang_chat/music_preview` channel.
 */
class MusicTrackPreviewServiceTest {
    @get:Rule
    val gang = GangTestRule()

    private val cacheRoot: File get() = File(gang.tempDir, "cache")
    private val previewDirectory: File get() = File(cacheRoot, "music-track-previews/v1")

    @Test
    fun `stores previews under the versioned cache directory and finds them again`() =
        gang.runTest {
            val platform = LocalMusicTrackPreviewPlatform()

            assertNull(platform.findCached("0123456789abcdef"))
            val stored = platform.store("0123456789abcdef", byteArrayOf(1, 2, 3))

            assertEquals(File(previewDirectory, "0123456789abcdef.m4a").path, stored.location)
            assertArrayEquals(byteArrayOf(1, 2, 3), File(stored.location).readBytes())
            assertEquals(listOf("0123456789abcdef.m4a"), previewDirectory.list()!!.toList())
            assertEquals(stored.location, platform.findCached("0123456789abcdef")?.location)
            platform.dispose()
        }

    @Test
    fun `finding a preview refreshes its modification time and empty files are misses`() =
        gang.runTest {
            gang.setNow(DateTime.utc(2026, 7, 1, 12))
            val platform = LocalMusicTrackPreviewPlatform()
            val stored = File(platform.store("a", byteArrayOf(9)).location)
            stored.setLastModified(0)

            assertNotNull(platform.findCached("a"))
            assertEquals(DateTime.utc(2026, 7, 1, 12).millisecondsSinceEpoch, stored.lastModified())

            stored.writeBytes(ByteArray(0))
            assertNull(platform.findCached("a"))
            assertFalse(stored.exists())
            platform.dispose()
        }

    @Test
    fun `legacy ogg and mp3 previews are removed when the cache opens`() =
        gang.runTest {
            previewDirectory.mkdirs()
            File(previewDirectory, "old.ogg").writeText("x")
            File(previewDirectory, "old.mp3").writeText("x")
            File(previewDirectory, "keep.m4a").writeText("x")
            val platform = LocalMusicTrackPreviewPlatform()

            assertNotNull(platform.findCached("keep"))

            assertEquals(listOf("keep.m4a"), previewDirectory.list()!!.toList())
            platform.dispose()
        }

    @Test
    fun `the cache keeps at most 256 MiB, evicting the oldest previews but never the new one`() =
        gang.runTest {
            previewDirectory.mkdirs()
            val oldest = File(previewDirectory, "oldest.m4a")
            val older = File(previewDirectory, "older.m4a")
            val newer = File(previewDirectory, "newer.m4a")
            for (file in listOf(oldest, older, newer)) {
                java.io.RandomAccessFile(file, "rw").use { it.setLength(100L shl 20) }
            }
            oldest.setLastModified(1_000_000L)
            older.setLastModified(2_000_000L)
            newer.setLastModified(3_000_000L)
            val platform = LocalMusicTrackPreviewPlatform()

            // 300 MiB + 1 byte: dropping the oldest file gets under the limit.
            platform.store("fresh", byteArrayOf(1))

            assertFalse(oldest.exists())
            assertTrue(older.exists())
            assertTrue(newer.exists())
            assertTrue(File(previewDirectory, "fresh.m4a").exists())
            assertEquals(4 - 1, previewDirectory.list()!!.size)
            platform.dispose()
        }

    @Test
    fun `without the native player stop and dispose are quiet but play fails`() =
        gang.runTest {
            val platform = LocalMusicTrackPreviewPlatform()
            platform.stop()
            try {
                platform.play(MusicTrackPreviewAsset("/missing.m4a"))
                fail("expected MissingPluginException")
            } catch (_: MissingPluginException) {
            }
            platform.dispose()
            // Disposed platforms ignore play requests.
            platform.play(MusicTrackPreviewAsset("/missing.m4a"))
        }

    @Test
    fun `the default factory creates the local platform`() {
        assertTrue(DefaultMusicTrackPreviewPlatformFactory().create() is LocalMusicTrackPreviewPlatform)
    }
}

/** Playback through the native [AndroidMusicPreviewPlayer] on Robolectric's MediaPlayer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MusicTrackPreviewServicePlaybackTest {
    @get:Rule
    val gang = GangTestRule()

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `plays a cached preview and reports completion`() =
        gang.runTest {
            val player = AndroidMusicPreviewPlayer()
            val platform = LocalMusicTrackPreviewPlatform(androidPlayer = player)
            val asset = platform.store("track", byteArrayOf(1, 2, 3, 4))
            ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(asset.location), ShadowMediaPlayer.MediaInfo(2000, 0))
            var completions = 0
            val subscription = platform.onCompleted.listen({ completions += 1 })

            val playing = async { platform.play(asset) }
            pumpEventQueue()
            idleMainLooper()
            pumpEventQueue()
            playing.await()

            shadowOf(Looper.getMainLooper()).idleFor(JavaDuration.ofMillis(2500))
            pumpEventQueue()
            assertEquals(1, completions)

            subscription.cancel()
            platform.dispose()
            assertNull(player.onCompleted)
        }

    @Test
    fun `play surfaces native failures and stop supersedes a pending play`() =
        gang.runTest {
            val player = AndroidMusicPreviewPlayer()
            val platform = LocalMusicTrackPreviewPlatform(androidPlayer = player)
            try {
                platform.play(MusicTrackPreviewAsset(" "))
                fail("expected PlatformException")
            } catch (error: PlatformException) {
                assertEquals("invalid_path", error.code)
            }

            val asset = platform.store("slow", byteArrayOf(1))
            ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(asset.location), ShadowMediaPlayer.MediaInfo(2000, 0))
            val playing = async { platform.play(asset) }
            pumpEventQueue()
            platform.stop()
            pumpEventQueue()
            playing.await()
            platform.dispose()
        }
}
