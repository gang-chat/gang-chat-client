package com.gangchat.client.app

import com.gangchat.client.protocol.ApiException
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.protocol.MusicBoxCapabilities
import com.gangchat.client.protocol.MusicBoxPlayback
import com.gangchat.client.protocol.MusicBoxPlaybackMode
import com.gangchat.client.protocol.MusicBoxPlaybackState
import com.gangchat.client.protocol.MusicBoxProgressEvent
import com.gangchat.client.protocol.MusicBoxQueueItem
import com.gangchat.client.protocol.MusicBoxQueueItemStatus
import com.gangchat.client.protocol.MusicBoxState
import com.gangchat.client.protocol.MusicBoxUsage
import com.gangchat.client.testing.GangTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MusicBoxControllerTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `playNow sends an idempotent item command without a stale revision`() = gang.runTest {
        val api = _RecordingMusicBoxApi(_state)
        val controller = MusicBoxController(api = api)

        controller.playNow(roomId = "room-1", item = _item)

        assertEquals("room-1", api.roomId)
        assertEquals("play_now", api.action)
        assertEquals(_item.id, api.itemId)
        assertTrue(api.commandId!!.isNotEmpty())
        assertNull(api.expectedRevision)
    }

    @Test
    fun `transport controls execute without a stale UI revision`() = gang.runTest {
        val actions =
            listOf(
                "play",
                "resume",
                "pause",
                "skip",
                "next",
                "previous",
                "stop",
            )

        for (action in actions) {
            val api = _RecordingMusicBoxApi(_state)
            val controller = MusicBoxController(api = api)

            controller.control(
                roomId = "room-1",
                action = action,
                currentState = _state,
            )

            assertNull("action=$action", api.expectedRevision)
            assertTrue("action=$action", api.commandId!!.isNotEmpty())
        }
    }

    @Test
    fun `playback mode changes retain optimistic concurrency`() = gang.runTest {
        val api = _RecordingMusicBoxApi(_state)
        val controller = MusicBoxController(api = api)

        controller.control(
            roomId = "room-1",
            action = "set_mode",
            mode = MusicBoxPlaybackMode.repeatOne,
            currentState = _state,
        )

        assertEquals(_state.revision, api.expectedRevision)
    }

    @Test
    fun `clearing the request queue retains optimistic concurrency`() = gang.runTest {
        val api = _RecordingMusicBoxApi(_state)
        val controller = MusicBoxController(api = api)

        controller.clearTemporaryQueue(
            roomId = "room-1",
            currentState = _state,
        )

        assertEquals(_state.revision, api.expectedRevision)
    }

    @Test
    fun `music box command errors preserve localized API feedback`() {
        val error =
            ApiException(
                "歌曲尚未准备完成",
                statusCode = 409,
                code = "music_box_item_not_ready",
                requestId = "request-1",
            )

        assertEquals("歌曲尚未准备完成", musicBoxControlErrorMessage(error, "优先播放失败，请重试"))
        assertEquals(
            "优先播放失败，请重试",
            musicBoxControlErrorMessage(Exception("offline"), "优先播放失败，请重试"),
        )
    }

    @Test
    fun `compact progress updates only its matching authoritative snapshot`() {
        val matching =
            applyMusicBoxProgress(
                _state,
                MusicBoxProgressEvent(
                    revision = 42,
                    currentItemId = "priority-track",
                    positionMs = 12345,
                ),
            )

        assertNotNull(matching)
        assertEquals(12345, matching!!.playback.positionMs)
        assertSame(_state.queue, matching.queue)

        val stale =
            applyMusicBoxProgress(
                _state,
                MusicBoxProgressEvent(
                    revision = 41,
                    currentItemId = "priority-track",
                    positionMs = 99999,
                ),
            )
        val wrongTrack =
            applyMusicBoxProgress(
                _state,
                MusicBoxProgressEvent(
                    revision = 42,
                    currentItemId = "another-track",
                    positionMs = 99999,
                ),
            )

        assertSame(_state, stale)
        assertSame(_state, wrongTrack)
    }

    @Test
    fun `compact progress parser accepts the SSE payload shape`() {
        val progress =
            MusicBoxProgressEvent.fromJson(
                mapOf(
                    "revision" to 7,
                    "current_item_id" to "item-7",
                    "position_ms" to 4567,
                ),
            )

        assertEquals(7, progress.revision)
        assertEquals("item-7", progress.currentItemId)
        assertEquals(4567, progress.positionMs)
    }

    @Test
    fun `same-revision full heartbeat preserves actor-specific capabilities`() {
        val capabilities =
            MusicBoxCapabilities(
                canControl = true,
                canChangeMode = true,
            )
        val current =
            _state.copyWith(
                playback =
                    MusicBoxPlayback(
                        state = MusicBoxPlaybackState.playing,
                        currentItemId = "priority-track",
                        positionMs = 1000,
                        volume = 100,
                        updatedAt = null,
                        capabilities = capabilities,
                    ),
            )
        val heartbeat =
            MusicBoxState(
                enabled = true,
                playback =
                    MusicBoxPlayback(
                        state = MusicBoxPlaybackState.playing,
                        currentItemId = "priority-track",
                        positionMs = 2000,
                        volume = 100,
                        updatedAt = null,
                    ),
                queue = _state.queue,
                usage = _state.usage,
                revision = 42,
                hasRevision = true,
            )

        val reduced = applyMusicBoxRealtimeSnapshot(current, heartbeat)

        assertNotNull(reduced)
        assertNotSame(heartbeat, reduced)
        assertEquals(2000, reduced!!.playback.positionMs)
        assertSame(capabilities, reduced.playback.capabilities)
        assertSame(reduced, applyMusicBoxRealtimeSnapshot(reduced, heartbeat))
    }

    @Test
    fun `newer structural snapshot is applied wholesale`() {
        val incoming =
            MusicBoxState(
                enabled = true,
                playback = _state.playback,
                queue = _state.queue,
                usage = _state.usage,
                revision = 43,
                hasRevision = true,
            )

        assertSame(incoming, applyMusicBoxRealtimeSnapshot(_state, incoming))
    }

    private class _RecordingMusicBoxApi(val result: MusicBoxState) : GangApi by _unimplemented() {
        var roomId: String? = null
        var action: String? = null
        var itemId: String? = null
        var commandId: String? = null
        var expectedRevision: Int? = null

        override suspend fun controlMusicBox(
            roomId: String,
            action: String,
            itemId: String?,
            mode: String?,
            commandId: String?,
            expectedRevision: Int?,
        ): MusicBoxState {
            this.roomId = roomId
            this.action = action
            this.itemId = itemId
            this.commandId = commandId
            this.expectedRevision = expectedRevision
            return result
        }
    }

    private companion object {
        val _item =
            MusicBoxQueueItem(
                id = "priority-track",
                source = "netease",
                trackId = "track-priority",
                title = "优先歌曲",
                artist = "歌手",
                durationMs = 180000,
                status = MusicBoxQueueItemStatus.ready,
                fileSizeBytes = 1024,
                error = "",
                addedByUserId = "requester",
                createdAt = null,
            )

        val _state =
            MusicBoxState(
                enabled = true,
                playback =
                    MusicBoxPlayback(
                        state = MusicBoxPlaybackState.playing,
                        currentItemId = "priority-track",
                        positionMs = 0,
                        volume = 100,
                        updatedAt = null,
                    ),
                queue = listOf(_item),
                usage = MusicBoxUsage(usedBytes = 1024, limitBytes = 200L * 1024 * 1024),
                revision = 42,
                hasRevision = true,
            )
    }
}

/**
 * Dart's `noSuchMethod` fallback for fakes that implement only part of an
 * interface: every method not overridden throws.
 */
private inline fun <reified T : Any> _unimplemented(): T {
    val name = T::class.java.simpleName
    return java.lang.reflect.Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.getOrNull(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "Unimplemented $name"
            else -> throw NotImplementedError("$name.${method.name} is not implemented by this fake")
        }
    } as T
}
