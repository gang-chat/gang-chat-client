package com.gangchat.client.app

import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.complete
import com.gangchat.client.foundation.delayed
import com.gangchat.client.foundation.future
import com.gangchat.client.protocol.DownloadedFile
import com.gangchat.client.protocol.MusicTrackPreviewApi
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.pumpEventQueue
import kotlin.time.Duration
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MusicTrackPreviewTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `downloads, caches, plays, and stops one preview`() = gang.runTest {
        val api = _FakePreviewApi()
        val platform = _FakePreviewPlatform()
        val controller =
            MusicTrackPreviewController(
                api = api,
                platform = platform,
            )
        val track = MusicTrackPreviewTrack(source = "netease", trackId = "track-1")

        val pending = future { controller.preview(track) }
        delayed(Duration.ZERO)
        assertTrue(controller.snapshot.isLoading(track.key))
        api.download.complete(
            DownloadedFile(
                bytes = byteArrayOf(1, 2, 3),
                filename = "preview.m4a",
                mimeType = "audio/mp4",
            ),
        )
        pending.await()

        assertEquals(1, api.requests)
        assertArrayEquals(byteArrayOf(1, 2, 3), platform.storedBytes)
        assertEquals(listOf(track.key), platform.played)
        assertTrue(controller.snapshot.isPlaying(track.key))

        controller.stopIf(track.key)
        assertEquals(MusicTrackPreviewPhase.idle, controller.snapshot.phase)
        assertTrue(platform.stopCalls >= 2)
        controller.dispose()
    }

    @Test
    fun `closing while loading may cache but never starts stale playback`() = gang.runTest {
        val api = _FakePreviewApi()
        val platform = _FakePreviewPlatform()
        val controller =
            MusicTrackPreviewController(
                api = api,
                platform = platform,
            )
        val track =
            MusicTrackPreviewTrack(
                source = "bilibili",
                trackId = "BV1test",
            )

        val pending = future { controller.preview(track) }
        delayed(Duration.ZERO)
        controller.stopIf(track.key)
        api.download.complete(
            DownloadedFile(
                bytes = byteArrayOf(4, 5, 6),
                filename = "preview.m4a",
                mimeType = "audio/mp4",
            ),
        )
        pending.await()

        assertArrayEquals(byteArrayOf(4, 5, 6), platform.storedBytes)
        assertTrue(platform.played.isEmpty())
        assertEquals(MusicTrackPreviewPhase.idle, controller.snapshot.phase)
        controller.dispose()
    }

    @Test
    fun `closing during the initial player stop prevents a late preview`() = gang.runTest {
        val api = _FakePreviewApi()
        val platform = _FakePreviewPlatform().apply { nextStop = Completer() }
        val controller =
            MusicTrackPreviewController(
                api = api,
                platform = platform,
            )
        val track =
            MusicTrackPreviewTrack(
                source = "netease",
                trackId = "stop-race",
            )

        val preview = future { controller.preview(track) }
        assertTrue(controller.snapshot.isLoading(track.key))
        controller.stopIf(track.key)
        platform.nextStopCompletion?.complete()
        preview.await()

        assertEquals(0, api.requests)
        assertTrue(platform.played.isEmpty())
        assertEquals(MusicTrackPreviewPhase.idle, controller.snapshot.phase)
        controller.dispose()
    }

    @Test
    fun `player completion clears the cancel-preview state`() = gang.runTest {
        val api = _FakePreviewApi()
        val platform = _FakePreviewPlatform().apply { cached = true }
        val controller =
            MusicTrackPreviewController(
                api = api,
                platform = platform,
            )
        val track = MusicTrackPreviewTrack(source = "netease", trackId = "cached")

        controller.preview(track)
        assertTrue(controller.snapshot.isPlaying(track.key))
        platform.complete()
        pumpEventQueue()
        assertEquals(MusicTrackPreviewPhase.idle, controller.snapshot.phase)
        controller.dispose()
    }

    private class _FakePreviewApi : MusicTrackPreviewApi {
        val download: Completer<DownloadedFile> = Completer()
        var requests = 0

        override suspend fun downloadMusicTrackPreview(
            source: String,
            trackId: String,
        ): DownloadedFile {
            requests += 1
            return download.future.await()
        }
    }

    private class _FakePreviewPlatform : MusicTrackPreviewPlatform {
        private val _completed: StreamController<Unit> = StreamController.broadcast()
        var cached = false
        var storedBytes: ByteArray? = null
        val played: MutableList<String> = mutableListOf()
        var stopCalls = 0
        var nextStop: Completer<Unit>? = null
        var nextStopCompletion: Completer<Unit>? = null

        override val onCompleted: Stream<Unit> get() = _completed.stream

        override suspend fun findCached(cacheKey: String): MusicTrackPreviewAsset? {
            return if (cached) MusicTrackPreviewAsset(cacheKey) else null
        }

        override suspend fun store(cacheKey: String, bytes: ByteArray): MusicTrackPreviewAsset {
            storedBytes = bytes
            cached = true
            return MusicTrackPreviewAsset(cacheKey)
        }

        override suspend fun play(asset: MusicTrackPreviewAsset) {
            played.add(asset.location)
        }

        override suspend fun stop() {
            stopCalls += 1
            val pending = nextStop
            if (pending != null) {
                nextStop = null
                nextStopCompletion = pending
                pending.future.await()
            }
        }

        fun complete() = _completed.add(Unit)

        override suspend fun dispose() = _completed.close()
    }
}
