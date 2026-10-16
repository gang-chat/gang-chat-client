package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.MusicBoxActiveSource
import com.gangchat.client.protocol.MusicBoxActiveSourceType
import com.gangchat.client.protocol.MusicBoxPlayback
import com.gangchat.client.protocol.MusicBoxPlaybackMode
import com.gangchat.client.protocol.MusicBoxPlaybackState
import com.gangchat.client.protocol.MusicBoxQueueItem
import com.gangchat.client.protocol.MusicBoxQueueItemStatus
import com.gangchat.client.protocol.MusicBoxState
import com.gangchat.client.protocol.MusicBoxUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Dart `group` names prefix each case. JVM method names cannot contain
// `.`, `/` or `:`, so those characters are written as the look-alikes
// `․` (U+2024), `∕` (U+2215) and `∶` (U+2236).
class MusicBoxDisplayTest {
    // group('music box sources')

    @Test
    fun `music box sources temporarily excludes QQ Music from selectable sources`() {
        assertEquals(
            listOf("netease", "bilibili"),
            musicBoxSources.map { source -> source.id },
        )
        assertFalse(musicBoxSourceEnabled("tencent"))
    }

    @Test
    fun `music box sources normalizes a disabled or unknown source to the default`() {
        assertEquals(musicBoxDefaultSource, normalizedMusicBoxSource("tencent"))
        assertEquals(musicBoxDefaultSource, normalizedMusicBoxSource("unknown"))
        assertEquals("bilibili", normalizedMusicBoxSource("bilibili"))
    }

    @Test
    fun `music box sources uses consistent labels without hiding future providers`() {
        assertEquals("网易云", musicBoxSourceLabel("netease"))
        assertEquals("哔哩哔哩", musicBoxSourceLabel("bilibili"))
        assertEquals("future-source", musicBoxSourceLabel("future-source"))
        assertEquals("未知来源", musicBoxSourceLabel(""))
    }

    @Test
    fun `music box sources uses author for Bilibili and artist for music providers`() {
        assertEquals("作者", musicBoxArtistFieldLabel("bilibili"))
        assertEquals("作者", musicBoxArtistFieldLabel(" BILIBILI "))
        assertEquals("歌手", musicBoxArtistFieldLabel("netease"))
        assertEquals("歌手", musicBoxArtistFieldLabel("future-source"))
    }

    // group('music box flat list navigation')

    @Test
    fun `music box flat list navigation every active source type has an owning section`() {
        assertEquals(
            MusicBoxSection.queue,
            musicBoxSectionForSource(MusicBoxActiveSourceType.temporary),
        )
        assertEquals(
            MusicBoxSection.roomPlaylists,
            musicBoxSectionForSource(MusicBoxActiveSourceType.roomPlaylist),
        )
        assertEquals(
            MusicBoxSection.myPlaylists,
            musicBoxSectionForSource(MusicBoxActiveSourceType.userPlaylist),
        )
    }

    @Test
    fun `music box flat list navigation playlist key is the wire type value plus id`() {
        assertEquals(
            "room_playlist:pl-1",
            musicBoxPlaylistKey(MusicBoxActiveSourceType.roomPlaylist, "pl-1"),
        )
        assertEquals(
            "user_playlist:pl-1",
            musicBoxPlaylistKey(MusicBoxActiveSourceType.userPlaylist, "pl-1"),
        )
        assertNotEquals(
            musicBoxPlaylistKey(MusicBoxActiveSourceType.userPlaylist, "pl-1"),
            musicBoxPlaylistKey(MusicBoxActiveSourceType.roomPlaylist, "pl-1"),
        )
    }

    @Test
    fun `music box flat list navigation temporary source is active regardless of id`() {
        val state = _state()
        assertTrue(
            musicBoxSourceIsActive(state, MusicBoxActiveSourceType.temporary),
        )
        assertTrue(
            musicBoxSourceIsActive(
                state,
                MusicBoxActiveSourceType.temporary,
                "ignored",
            ),
        )
        assertFalse(
            musicBoxSourceIsActive(state, MusicBoxActiveSourceType.roomPlaylist),
        )
    }

    @Test
    fun `music box flat list navigation saved playlist is active only when both type and id match`() {
        val state =
            _state(
                activeSource =
                    MusicBoxActiveSource(
                        type = MusicBoxActiveSourceType.roomPlaylist,
                        id = "playlist-1",
                        name = "一起听",
                    ),
            )
        assertTrue(
            musicBoxSourceIsActive(
                state,
                MusicBoxActiveSourceType.roomPlaylist,
                "playlist-1",
            ),
        )
        assertFalse(
            musicBoxSourceIsActive(
                state,
                MusicBoxActiveSourceType.roomPlaylist,
                "playlist-2",
            ),
        )
        assertFalse(
            musicBoxSourceIsActive(
                state,
                MusicBoxActiveSourceType.userPlaylist,
                "playlist-1",
            ),
        )
        assertFalse(
            musicBoxSourceIsActive(state, MusicBoxActiveSourceType.temporary),
        )
    }

    @Test
    fun `music box flat list navigation current track matches by catalog identity with link key rules`() {
        val current =
            MusicBoxQueueItem(
                id = "snapshot-item",
                source = "bilibili",
                trackId = "BV1AbC",
                title = "Song",
                artist = "",
                durationMs = 0,
                status = MusicBoxQueueItemStatus.ready,
                fileSizeBytes = 0,
                error = "",
                addedByUserId = "user",
                createdAt = null,
            )
        val playing =
            _state(
                playbackState = MusicBoxPlaybackState.playing,
                currentItemId = "snapshot-item",
                queue = listOf(current),
            )

        assertTrue(
            musicBoxTrackIsCurrent(
                playing,
                source = " BILIBILI ",
                trackId = " BV1AbC ",
            ),
        )
        assertFalse(
            musicBoxTrackIsCurrent(playing, source = "bilibili", trackId = "bv1abc"),
        )
        assertFalse(
            musicBoxTrackIsCurrent(playing, source = "netease", trackId = "BV1AbC"),
        )
        assertFalse(
            musicBoxTrackIsCurrent(
                _state(queue = listOf(current)),
                source = "bilibili",
                trackId = "BV1AbC",
            ),
        )
    }

    // group('music box request queue identity')

    @Test
    fun `music box request queue identity matches the same provider link and preserves track id case`() {
        val queued =
            MusicBoxQueueItem(
                id = "queued-1",
                source = "bilibili",
                trackId = "BV1AbC",
                title = "Song",
                artist = "Artist",
                durationMs = 0,
                status = MusicBoxQueueItemStatus.ready,
                fileSizeBytes = 0,
                error = "",
                addedByUserId = "user-1",
                createdAt = null,
            )

        assertTrue(
            musicBoxRequestQueueContainsTrack(
                listOf(queued),
                source = " BILIBILI ",
                trackId = " BV1AbC ",
            ),
        )
        assertFalse(
            musicBoxRequestQueueContainsTrack(
                listOf(queued),
                source = "bilibili",
                trackId = "bv1abc",
            ),
        )
    }

    // group('music box active source labels')

    @Test
    fun `music box active source labels uses 点歌队列 for temporary sources from old servers`() {
        val parsed =
            MusicBoxState.fromJson(
                mapOf(
                    "enabled" to true,
                    "active_source" to mapOf("type" to "temporary", "name" to "临时歌单"),
                ),
            )

        assertEquals(musicBoxRequestQueueLabel, parsed.activeSource.name)
        assertEquals(
            musicBoxRequestQueueLabel,
            musicBoxActiveSourceLabel(parsed.activeSource),
        )
    }

    @Test
    fun `music box active source labels keeps saved playlist names and has scoped fallbacks`() {
        assertEquals(
            "一起听",
            musicBoxActiveSourceLabel(
                MusicBoxActiveSource(
                    type = MusicBoxActiveSourceType.roomPlaylist,
                    name = "一起听",
                ),
            ),
        )
        assertEquals(
            "个人歌单",
            musicBoxActiveSourceLabel(
                MusicBoxActiveSource(
                    type = MusicBoxActiveSourceType.userPlaylist,
                    name = "   ",
                ),
            ),
        )
    }

    @Test
    fun `music box active source labels parses the owner summary for a personal playlist`() {
        val parsed =
            MusicBoxState.fromJson(
                mapOf(
                    "enabled" to true,
                    "active_source" to
                        mapOf(
                            "type" to "user_playlist",
                            "playlist_id" to "playlist-1",
                            "name" to "通勤歌单",
                            "owner_user_id" to "owner-1",
                            "owner" to
                                mapOf(
                                    "user_id" to "owner-1",
                                    "username" to "owner_handle",
                                    "display_name" to "点歌用户",
                                    "avatar_label" to "全局用户",
                                    "avatar_url" to "/avatar.png",
                                    "default_avatar_key" to "green-2",
                                ),
                        ),
                ),
            )

        assertEquals("owner-1", parsed.activeSource.ownerUserId)
        assertEquals("owner_handle", parsed.activeSource.owner?.username)
        assertEquals("点歌用户", parsed.activeSource.owner?.displayName)
        assertEquals("全局用户", parsed.activeSource.owner?.avatarLabel)
        assertEquals("/avatar.png", parsed.activeSource.owner?.avatarUrl)
    }

    // group('musicBoxProgress')

    @Test
    fun `musicBoxProgress renders the server-reported position as-is`() {
        val state =
            _state(
                playbackState = MusicBoxPlaybackState.playing,
                currentItemId = "item-1",
                positionMs = 8000,
                queue = listOf(_item(id = "item-1", durationMs = 200000)),
            )

        val progress = musicBoxProgress(state)

        assertEquals(8000, progress.positionMs)
        assertEquals(200000, progress.durationMs)
        assertEquals(0.04, progress.fraction, 0.0001)
    }

    @Test
    fun `musicBoxProgress uses the recorded position regardless of playback state`() {
        val state =
            _state(
                playbackState = MusicBoxPlaybackState.paused,
                currentItemId = "item-1",
                positionMs = 5000,
                queue = listOf(_item(id = "item-1", durationMs = 200000)),
            )

        val progress = musicBoxProgress(state)

        assertEquals(5000, progress.positionMs)
    }

    @Test
    fun `musicBoxProgress floors the position to whole seconds`() {
        val state =
            _state(
                playbackState = MusicBoxPlaybackState.playing,
                currentItemId = "item-1",
                positionMs = 6900,
                queue = listOf(_item(id = "item-1", durationMs = 200000)),
            )

        val progress = musicBoxProgress(state)

        assertEquals(6000, progress.positionMs)
    }

    @Test
    fun `musicBoxProgress clamps the position to the track duration`() {
        val state =
            _state(
                playbackState = MusicBoxPlaybackState.playing,
                currentItemId = "item-1",
                positionMs = 30000,
                queue = listOf(_item(id = "item-1", durationMs = 10000)),
            )

        val progress = musicBoxProgress(state)

        assertEquals(10000, progress.positionMs)
        assertEquals(1.0, progress.fraction, 0.0)
    }

    @Test
    fun `musicBoxProgress fraction is zero when duration is unknown`() {
        val state =
            _state(
                playbackState = MusicBoxPlaybackState.playing,
                currentItemId = "item-1",
                positionMs = 5000,
                queue = listOf(_item(id = "item-1", durationMs = 0)),
            )

        val progress = musicBoxProgress(state)

        assertEquals(0.0, progress.fraction, 0.0)
    }

    // group('musicBoxPrimaryTransport')

    @Test
    fun `musicBoxPrimaryTransport maps playback state to the transport action`() {
        assertEquals(
            MusicBoxTransportAction.pause,
            musicBoxPrimaryTransport(
                _state(playbackState = MusicBoxPlaybackState.playing),
            ),
        )
        assertEquals(
            MusicBoxTransportAction.resume,
            musicBoxPrimaryTransport(
                _state(playbackState = MusicBoxPlaybackState.paused),
            ),
        )
        assertEquals(
            MusicBoxTransportAction.play,
            musicBoxPrimaryTransport(
                _state(playbackState = MusicBoxPlaybackState.stopped),
            ),
        )
    }

    @Test
    fun `musicBoxPrimaryTransport maps transport action to the API verb`() {
        assertEquals("play", musicBoxTransportApiAction(MusicBoxTransportAction.play))
        assertEquals(
            "pause",
            musicBoxTransportApiAction(MusicBoxTransportAction.pause),
        )
        assertEquals(
            "resume",
            musicBoxTransportApiAction(MusicBoxTransportAction.resume),
        )
    }

    // group('musicBoxRecordSpinning')

    @Test
    fun `musicBoxRecordSpinning spins only while playing`() {
        assertTrue(
            musicBoxRecordSpinning(
                _state(playbackState = MusicBoxPlaybackState.playing),
            ),
        )
        assertFalse(
            musicBoxRecordSpinning(
                _state(playbackState = MusicBoxPlaybackState.paused),
            ),
        )
    }

    // group('musicBoxHasActivePlayback')

    @Test
    fun `musicBoxHasActivePlayback requires a current item and a non-stopped transport state`() {
        val queue = listOf(_item(id = "item-1"))

        assertFalse(
            musicBoxHasActivePlayback(
                _state(
                    playbackState = MusicBoxPlaybackState.stopped,
                    currentItemId = "item-1",
                    queue = queue,
                ),
            ),
        )
        assertTrue(
            musicBoxHasActivePlayback(
                _state(
                    playbackState = MusicBoxPlaybackState.playing,
                    currentItemId = "item-1",
                    queue = queue,
                ),
            ),
        )
        assertTrue(
            musicBoxHasActivePlayback(
                _state(
                    playbackState = MusicBoxPlaybackState.paused,
                    currentItemId = "item-1",
                    queue = queue,
                ),
            ),
        )
        assertFalse(
            musicBoxHasActivePlayback(
                _state(playbackState = MusicBoxPlaybackState.playing, queue = queue),
            ),
        )
    }

    // group('musicBoxQueueStatusLabel')

    @Test
    fun `musicBoxQueueStatusLabel labels each lifecycle stage`() {
        assertEquals(
            "排队中，等待下载",
            musicBoxQueueStatusLabel(
                _item(status = MusicBoxQueueItemStatus.pending),
            ),
        )
        assertEquals(
            "下载中",
            musicBoxQueueStatusLabel(
                _item(status = MusicBoxQueueItemStatus.downloading),
            ),
        )
        assertNull(
            musicBoxQueueStatusLabel(_item(status = MusicBoxQueueItemStatus.ready)),
        )
    }

    @Test
    fun `musicBoxQueueStatusLabel uses the server error for failed items, with a fallback`() {
        assertEquals(
            "版权限制",
            musicBoxQueueStatusLabel(
                _item(status = MusicBoxQueueItemStatus.failed, error = "版权限制"),
            ),
        )
        assertEquals(
            "处理失败",
            musicBoxQueueStatusLabel(_item(status = MusicBoxQueueItemStatus.failed)),
        )
    }

    // group('musicBoxUsageHint')

    @Test
    fun `musicBoxUsageHint warns near and at the limit`() {
        assertNull(
            musicBoxUsageHint(MusicBoxUsage(usedBytes = 50, limitBytes = 100)),
        )
        assertEquals(
            "空间已接近上限",
            musicBoxUsageHint(MusicBoxUsage(usedBytes = 92, limitBytes = 100)),
        )
        assertEquals(
            "空间已满，新歌将排队等待下载",
            musicBoxUsageHint(MusicBoxUsage(usedBytes = 100, limitBytes = 100)),
        )
        assertNull(
            musicBoxUsageHint(MusicBoxUsage(usedBytes = 0, limitBytes = 0)),
        )
    }

    @Test
    fun `musicBoxUsageHint usage fraction is clamped`() {
        assertEquals(
            0.25,
            musicBoxUsageFraction(
                MusicBoxUsage(usedBytes = 25, limitBytes = 100),
            ),
            0.0,
        )
        assertEquals(
            1.0,
            musicBoxUsageFraction(
                MusicBoxUsage(usedBytes = 200, limitBytes = 100),
            ),
            0.0,
        )
        assertEquals(
            0.0,
            musicBoxUsageFraction(MusicBoxUsage(usedBytes = 5, limitBytes = 0)),
            0.0,
        )
    }

    // group('formatting')

    @Test
    fun `formatting formats durations as mm∶ss and h∶mm∶ss`() {
        assertEquals("--:--", musicBoxFormatDuration(0))
        assertEquals("1:05", musicBoxFormatDuration(65000))
        assertEquals("1:02:05", musicBoxFormatDuration(3725000))
    }

    @Test
    fun `formatting formats byte sizes`() {
        assertEquals("0 B", musicBoxFormatBytes(0))
        assertEquals("512 B", musicBoxFormatBytes(512))
        assertEquals("1.5 KB", musicBoxFormatBytes(1536))
        assertEquals("5 MB", musicBoxFormatBytes(5L * 1024 * 1024))
    }

    @Test
    fun `formatting joins artist lists, dropping blanks`() {
        assertEquals("林俊杰、孙燕姿", musicBoxArtistsLabel(listOf("林俊杰", "孙燕姿")))
        assertEquals("周杰伦", musicBoxArtistsLabel(listOf("", "  ", "周杰伦")))
        assertEquals("", musicBoxArtistsLabel(emptyList()))
    }

    // group('MusicBoxState.fromJson')

    @Test
    fun `MusicBoxState․fromJson parses a full snapshot and resolves the current item`() {
        val state =
            MusicBoxState.fromJson(
                mapOf(
                    "enabled" to true,
                    "playback" to
                        mapOf(
                            "state" to "playing",
                            "current_item_id" to "item-2",
                            "position_ms" to 1200,
                            "volume" to 80,
                            "updated_at" to "2026-01-01T12:00:00Z",
                        ),
                    "queue" to
                        listOf(
                            mapOf("id" to "item-1", "title" to "A", "status" to "ready"),
                            mapOf(
                                "id" to "item-2",
                                "title" to "B",
                                "artist" to "Artist",
                                "status" to "downloading",
                                "duration_ms" to 180000,
                            ),
                        ),
                    "usage" to mapOf("used_bytes" to 10, "limit_bytes" to 100),
                ),
            )

        assertTrue(state.enabled)
        assertEquals(MusicBoxPlaybackState.playing, state.playback.state)
        assertTrue(state.playback.hasCurrent)
        assertEquals("B", state.currentItem?.title)
        assertEquals(2, state.queue.size)
        assertEquals(MusicBoxQueueItemStatus.ready, state.queue.first().status)
        assertEquals(100L, state.usage.limitBytes)
    }

    @Test
    fun `MusicBoxState․fromJson defaults a missing playback block to stopped`() {
        val state = MusicBoxState.fromJson(mapOf("enabled" to false))

        assertFalse(state.enabled)
        assertEquals(MusicBoxPlaybackState.stopped, state.playback.state)
        assertNull(state.currentItem)
        assertTrue(state.queue.isEmpty())
    }

    @Test
    fun `MusicBoxState․fromJson parses revision, active saved source and independent temporary queue`() {
        val state =
            MusicBoxState.fromJson(
                mapOf(
                    "enabled" to true,
                    "revision" to 12,
                    "active_source" to
                        mapOf(
                            "type" to "room_playlist",
                            "playlist_id" to "playlist-1",
                            "name" to "Shared favorites",
                        ),
                    "playback" to
                        mapOf(
                            "state" to "playing",
                            "current_item_id" to "saved-1",
                            "mode" to "repeat_all",
                            "can_previous" to true,
                            "can_next" to true,
                            "capabilities" to
                                mapOf(
                                    "can_control" to false,
                                    "can_change_mode" to false,
                                    "can_reorder" to false,
                                    "allowed_modes" to listOf("sequential", "repeat_one", "repeat_all"),
                                ),
                        ),
                    "queue" to
                        listOf(
                            mapOf(
                                "id" to "saved-1",
                                "title" to "Saved song",
                                "status" to "ready",
                                "can_play_now" to true,
                                "can_remove" to false,
                                "requested_by" to
                                    mapOf<String, Any?>(
                                        "user_id" to "user-1",
                                        "display_name" to "房间专属名",
                                        "avatar_label" to "Alice",
                                        "avatar_url" to "/avatar.png",
                                    ),
                            ),
                        ),
                    "temporary_queue" to
                        listOf(
                            mapOf(
                                "id" to "temporary-1",
                                "title" to "Requested song",
                                "status" to "pending",
                            ),
                        ),
                    "temporary_playlist" to
                        mapOf(
                            "queued_count" to 1,
                            "capabilities" to
                                mapOf(
                                    "can_enqueue" to true,
                                    "can_switch" to true,
                                    "can_reorder" to false,
                                    "can_clear" to false,
                                    "can_play_now" to false,
                                ),
                        ),
                    "usage" to emptyMap<String, Any?>(),
                ),
            )

        assertTrue(state.hasRevision)
        assertEquals(12, state.revision)
        assertEquals(MusicBoxActiveSourceType.roomPlaylist, state.activeSource.type)
        assertEquals("playlist-1", state.activeSource.id)
        assertEquals(MusicBoxPlaybackMode.repeatAll, state.playback.mode)
        assertFalse(state.playback.capabilities.canControl)
        assertFalse(state.playback.capabilities.canChangeMode)
        assertFalse(state.playback.capabilities.canReorder)
        assertEquals(true, state.currentItem?.canPlayNow)
        assertEquals(false, state.currentItem?.canRemove)
        assertEquals("房间专属名", state.currentItem?.requestedBy?.displayName)
        assertEquals("Alice", state.currentItem?.requestedBy?.avatarLabel)
        assertEquals("temporary-1", state.temporaryQueue.single().id)
        assertEquals(1, state.temporaryQueuedCount)
        assertTrue(state.canEnqueueTemporary)
        assertTrue(state.canSwitchTemporary)
        assertFalse(state.canReorderTemporary)
        assertFalse(state.canClearTemporary)
        assertFalse(state.canPlayNowTemporary)
    }

    @Test
    fun `MusicBoxState․fromJson queue items are playable when ready or already streaming`() {
        fun parse(json: Map<String, Any?>): MusicBoxQueueItem =
            MusicBoxQueueItem.fromJson(mapOf<String, Any?>("id" to "item") + json)

        assertTrue(parse(mapOf("status" to "ready")).playable)
        assertFalse(parse(mapOf("status" to "downloading")).playable)
        assertFalse(parse(mapOf("status" to "pending")).playable)
        val streaming =
            parse(
                mapOf(
                    "status" to "downloading",
                    "playable" to true,
                    "can_play_now" to true,
                ),
            )
        assertEquals(MusicBoxQueueItemStatus.downloading, streaming.status)
        assertTrue(streaming.playable)
        assertTrue(streaming.canPlayNow)
        assertFalse(
            parse(mapOf("status" to "downloading", "playable" to false)).playable,
        )
    }

    @Test
    fun `MusicBoxState․fromJson rejects stale revision snapshots but keeps legacy compatibility`() {
        val current =
            MusicBoxState.fromJson(
                mapOf(
                    "enabled" to true,
                    "revision" to 5,
                    "playback" to mapOf("current_item_id" to "current"),
                ),
            )
        val stale =
            MusicBoxState.fromJson(
                mapOf(
                    "enabled" to true,
                    "revision" to 4,
                    "playback" to mapOf("current_item_id" to "old"),
                ),
            )
        val newer =
            MusicBoxState.fromJson(
                mapOf(
                    "enabled" to true,
                    "revision" to 6,
                    "playback" to mapOf("current_item_id" to "new"),
                ),
            )
        val legacy = MusicBoxState.fromJson(mapOf("enabled" to true))

        assertFalse(shouldAcceptMusicBoxSnapshot(current, stale))
        assertTrue(shouldAcceptMusicBoxSnapshot(current, newer))
        assertTrue(shouldAcceptMusicBoxSnapshot(current, legacy))
    }
}

private fun _state(
    enabled: Boolean = true,
    playbackState: MusicBoxPlaybackState = MusicBoxPlaybackState.stopped,
    currentItemId: String = "",
    positionMs: Int = 0,
    updatedAt: DateTime? = null,
    queue: List<MusicBoxQueueItem> = emptyList(),
    activeSource: MusicBoxActiveSource = MusicBoxActiveSource(),
): MusicBoxState {
    return MusicBoxState(
        enabled = enabled,
        playback =
            MusicBoxPlayback(
                state = playbackState,
                currentItemId = currentItemId,
                positionMs = positionMs,
                volume = 100,
                updatedAt = updatedAt,
            ),
        queue = queue,
        activeSource = activeSource,
        usage = MusicBoxUsage(usedBytes = 0, limitBytes = 0),
    )
}

private fun _item(
    id: String = "item",
    title: String = "Song",
    artist: String = "",
    status: MusicBoxQueueItemStatus = MusicBoxQueueItemStatus.ready,
    durationMs: Int = 0,
    error: String = "",
    addedByUserId: String = "user",
): MusicBoxQueueItem {
    return MusicBoxQueueItem(
        id = id,
        source = "netease",
        trackId = "track-$id",
        title = title,
        artist = artist,
        durationMs = durationMs,
        status = status,
        fileSizeBytes = 0,
        error = error,
        addedByUserId = addedByUserId,
        createdAt = null,
    )
}
