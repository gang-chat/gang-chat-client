package com.gangchat.client.live

import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.complete
import com.gangchat.client.foundation.delayed
import com.gangchat.client.foundation.future
import com.gangchat.client.foundation.waitAll
import com.gangchat.client.protocol.ScreenAudioToken
import com.gangchat.client.testing.GangTestRule
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/** Port of test/unit/live/screen_audio_publisher_test.dart. */
class ScreenAudioPublisherTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `stop cancels before token acquisition completes`() = gang.runTest {
        val tokenRequested = Completer<Unit>()
        val releaseToken = Completer<Unit>()
        val events = mutableListOf<String>()
        val backend = FakeScreenAudioPublisherBackend(events = events, onPublish = { })
        val publisher =
            ScreenAudioPublisher(
                tokenProvider = { roomId ->
                    tokenRequested.complete()
                    releaseToken.future.await()
                    _screenAudioToken(roomId)
                },
                backendFactory = { backend },
            )

        val start = future { publisher.start(liveKitUrl = "wss://live.example.test", roomName = "room-1") }
        tokenRequested.future.await()

        publisher.stop()
        assertTrue(events.isEmpty())

        releaseToken.complete()
        start.await()

        assertTrue(events.isEmpty())
        assertEquals(0, backend.stopCount)
        assertFalse(publisher.isPublishing)
    }

    @Test
    fun `stop waits for an in-flight publish before releasing the backend`() = gang.runTest {
        val publishStarted = Completer<Unit>()
        val releasePublish = Completer<Unit>()
        val events = mutableListOf<String>()
        val backend =
            FakeScreenAudioPublisherBackend(
                events = events,
                onPublish = {
                    publishStarted.complete()
                    releasePublish.future.await()
                },
            )
        val publisher = ScreenAudioPublisher(tokenProvider = ::_tokenProvider, backendFactory = { backend })

        val start = future { publisher.start(liveKitUrl = "wss://live.example.test", roomName = "room-1") }
        publishStarted.future.await()

        var stopCompleted = false
        val stop =
            future {
                publisher.stop()
                stopCompleted = true
            }
        delayed(Duration.ZERO)

        assertFalse(stopCompleted)
        assertEquals(listOf("connect", "publish"), events)

        releasePublish.complete()
        waitAll(listOf(start, stop))

        assertEquals(listOf("connect", "publish", "stop"), events)
        assertEquals(1, backend.stopCount)
        assertFalse(publisher.isPublishing)
    }

    @Test
    fun `stop prevents publish after an in-flight connect`() = gang.runTest {
        val connectStarted = Completer<Unit>()
        val releaseConnect = Completer<Unit>()
        val events = mutableListOf<String>()
        val backend =
            FakeScreenAudioPublisherBackend(
                events = events,
                onConnect = {
                    connectStarted.complete()
                    releaseConnect.future.await()
                },
                onPublish = { },
            )
        val publisher = ScreenAudioPublisher(tokenProvider = ::_tokenProvider, backendFactory = { backend })

        val start = future { publisher.start(liveKitUrl = "wss://live.example.test", roomName = "room-1") }
        connectStarted.future.await()

        val stop = future { publisher.stop() }
        releaseConnect.complete()
        waitAll(listOf(start, stop))

        assertEquals(listOf("connect", "stop"), events)
        assertEquals(1, backend.stopCount)
        assertFalse(publisher.isPublishing)
    }

    @Test
    fun `a failed publish releases the connected backend`() = gang.runTest {
        val events = mutableListOf<String>()
        val backend =
            FakeScreenAudioPublisherBackend(
                events = events,
                onPublish = { throw IllegalStateException("publish failed") },
            )
        val publisher = ScreenAudioPublisher(tokenProvider = ::_tokenProvider, backendFactory = { backend })

        try {
            publisher.start(liveKitUrl = "wss://live.example.test", roomName = "room-1")
            fail("expected the publish failure")
        } catch (error: IllegalStateException) {
            assertEquals("publish failed", error.message)
        }

        assertEquals(listOf("connect", "publish", "stop"), events)
        assertEquals(1, backend.stopCount)
        assertFalse(publisher.isPublishing)

        publisher.stop()
        assertEquals(1, backend.stopCount)
    }
}

private suspend fun _tokenProvider(roomId: String): ScreenAudioToken = _screenAudioToken(roomId)

private fun _screenAudioToken(roomId: String): ScreenAudioToken {
    return ScreenAudioToken(
        serverUrl = "wss://live.example.test",
        token = "screen-audio-token",
        tokenExpiresAt = DateTime.utc(2026, 7, 29, 1),
        roomName = roomId,
        identity = "user-1--screen-audio",
    )
}

private class FakeScreenAudioPublisherBackend(
    val events: MutableList<String>,
    val onPublish: suspend () -> Unit,
    val onConnect: (suspend () -> Unit)? = null,
) : ScreenAudioPublisherBackend {
    var stopCount = 0

    override suspend fun connect(liveKitUrl: String, token: String) {
        events.add("connect")
        onConnect?.invoke()
    }

    override suspend fun publish() {
        events.add("publish")
        onPublish()
    }

    override suspend fun stop() {
        stopCount++
        events.add("stop")
    }
}
