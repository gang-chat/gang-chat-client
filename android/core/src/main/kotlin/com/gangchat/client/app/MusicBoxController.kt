package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.ApiException
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.protocol.MusicBoxActivePlaylistCloneApi
import com.gangchat.client.protocol.MusicBoxActiveSource
import com.gangchat.client.protocol.MusicBoxActiveSourceType
import com.gangchat.client.protocol.MusicBoxPlaybackMode
import com.gangchat.client.protocol.MusicBoxPlaybackState
import com.gangchat.client.protocol.MusicBoxProgressEvent
import com.gangchat.client.protocol.MusicBoxQueueItem
import com.gangchat.client.protocol.MusicBoxSearchResult
import com.gangchat.client.protocol.MusicBoxState
import com.gangchat.client.protocol.PersonalMusicPlaylist
import com.gangchat.client.protocol.PersonalMusicPlaylistApi
import com.gangchat.client.protocol.PersonalMusicPlaylistItem
import com.gangchat.client.protocol.PersonalMusicPlaylistItemsPage
import com.gangchat.client.protocol.PersonalMusicPlaylistPage
import com.gangchat.client.protocol.RoomMusicPlaylistApi
import com.gangchat.client.protocol.musicBoxPlaybackModeValue

/**
 * Thin HTTP facade over the room music box endpoints. Stateless: every call
 * returns the server's authoritative [MusicBoxState] snapshot (search aside),
 * which callers use to overwrite local state wholesale.
 */
class MusicBoxController(val api: GangApi) {
    suspend fun getState(roomId: String): MusicBoxState {
        return api.getMusicBoxState(roomId)
    }

    suspend fun search(
        roomId: String,
        keyword: String,
        source: String? = null,
        count: Int? = null,
        page: Int? = null,
    ): List<MusicBoxSearchResult> {
        return api.searchMusicBox(
            roomId = roomId,
            keyword = keyword,
            source = source,
            count = count,
            page = page,
        )
    }

    /**
     * Adds a search hit to the queue, mapping the search shape onto the queue
     * request body: `name -> title`, and the `artists` array joined into the
     * single `artist` string the server expects.
     */
    suspend fun queueSearchResult(
        roomId: String,
        result: MusicBoxSearchResult,
        durationMs: Int? = null,
    ): MusicBoxState {
        return api.queueMusicBoxTrack(
            roomId = roomId,
            trackId = result.trackId,
            title = result.name,
            source = result.source,
            artist = result.artists.joinToString("、"),
            durationMs = durationMs,
        )
    }

    suspend fun removeItem(
        roomId: String,
        itemId: String,
    ): MusicBoxState {
        return api.removeMusicBoxItem(roomId = roomId, itemId = itemId)
    }

    suspend fun control(
        roomId: String,
        action: String,
        mode: MusicBoxPlaybackMode? = null,
        currentState: MusicBoxState? = null,
    ): MusicBoxState {
        val shouldGuardRevision = musicBoxControlRequiresExpectedRevision(action)
        return api.controlMusicBox(
            roomId = roomId,
            action = action,
            mode = if (mode == null) null else musicBoxPlaybackModeValue(mode),
            commandId = _nextCommandId(),
            expectedRevision =
                if (shouldGuardRevision &&
                    currentState != null &&
                    currentState.hasRevision
                ) {
                    currentState.revision
                } else {
                    null
                },
        )
    }

    suspend fun playNow(
        roomId: String,
        item: MusicBoxQueueItem,
    ): MusicBoxState {
        return api.controlMusicBox(
            roomId = roomId,
            action = "play_now",
            itemId = item.id,
            commandId = _nextCommandId(),
        )
    }

    suspend fun clearTemporaryQueue(
        roomId: String,
        currentState: MusicBoxState? = null,
    ): MusicBoxState {
        return api.controlMusicBox(
            roomId = roomId,
            action = "clear_temporary_playlist",
            commandId = _nextCommandId(),
            expectedRevision =
                if (currentState != null && currentState.hasRevision) {
                    currentState.revision
                } else {
                    null
                },
        )
    }

    suspend fun addQueueItemToRoomPlaylist(
        roomId: String,
        playlistId: String,
        item: MusicBoxQueueItem,
    ): PersonalMusicPlaylistItem {
        return addSearchResultToRoomPlaylist(
            roomId = roomId,
            playlistId = playlistId,
            result = _queueItemAsSearchResult(item),
            durationMs = if (item.durationMs > 0) item.durationMs else null,
        )
    }

    suspend fun addSearchResultToRoomPlaylist(
        roomId: String,
        playlistId: String,
        result: MusicBoxSearchResult,
        durationMs: Int? = null,
    ): PersonalMusicPlaylistItem {
        return (api as RoomMusicPlaylistApi).addRoomMusicPlaylistItem(
            roomId = roomId,
            playlistId = playlistId,
            track = result,
            durationMs = durationMs,
        )
    }

    suspend fun addQueueItemToMyPlaylist(
        playlistId: String,
        item: MusicBoxQueueItem,
    ): PersonalMusicPlaylistItem {
        return addSearchResultToMyPlaylist(
            playlistId = playlistId,
            result = _queueItemAsSearchResult(item),
            durationMs = if (item.durationMs > 0) item.durationMs else null,
        )
    }

    suspend fun addSearchResultToMyPlaylist(
        playlistId: String,
        result: MusicBoxSearchResult,
        durationMs: Int? = null,
    ): PersonalMusicPlaylistItem {
        return (api as PersonalMusicPlaylistApi).addPersonalMusicPlaylistItem(
            playlistId = playlistId,
            track = result,
            durationMs = durationMs,
        )
    }

    suspend fun activatePlaylist(
        roomId: String,
        sourceType: MusicBoxActiveSourceType,
        playlistId: String? = null,
        startPlay: Boolean = true,
        startItemId: String? = null,
    ): MusicBoxState {
        return api.activateMusicBoxPlaylist(
            roomId = roomId,
            sourceType = sourceType,
            playlistId = playlistId,
            startPlay = startPlay,
            startItemId = startItemId,
        )
    }

    suspend fun cloneActivePlaylist(
        roomId: String,
        source: MusicBoxActiveSource,
    ): PersonalMusicPlaylist {
        return (api as MusicBoxActivePlaylistCloneApi).cloneActiveMusicBoxPlaylist(
            roomId = roomId,
            playlistId = source.id,
            snapshotId = source.snapshotId,
        )
    }

    suspend fun loadRoomPlaylists(
        roomId: String,
        page: Int = 1,
        pageSize: Int = 50,
    ): PersonalMusicPlaylistPage {
        return (api as RoomMusicPlaylistApi).listRoomMusicPlaylists(
            roomId = roomId,
            page = page,
            pageSize = pageSize,
        )
    }

    suspend fun loadMyPlaylists(
        page: Int = 1,
        pageSize: Int = 50,
    ): PersonalMusicPlaylistPage {
        return (api as PersonalMusicPlaylistApi).listPersonalMusicPlaylists(
            page = page,
            pageSize = pageSize,
        )
    }

    suspend fun loadRoomPlaylist(
        roomId: String,
        playlistId: String,
        page: Int = 1,
        pageSize: Int = 100,
    ): PersonalMusicPlaylistItemsPage {
        return (api as RoomMusicPlaylistApi).getRoomMusicPlaylist(
            roomId = roomId,
            playlistId = playlistId,
            page = page,
            pageSize = pageSize,
        )
    }

    suspend fun loadMyPlaylist(
        playlistId: String,
        page: Int = 1,
        pageSize: Int = 100,
    ): PersonalMusicPlaylistItemsPage {
        return (api as PersonalMusicPlaylistApi).getPersonalMusicPlaylist(
            playlistId = playlistId,
            page = page,
            pageSize = pageSize,
        )
    }

    companion object {
        private var _commandSerial = 0

        private fun _nextCommandId(): String {
            val now = DateTime.now().microsecondsSinceEpoch
            return "mbx-$now-${_commandSerial++}"
        }

        private fun _queueItemAsSearchResult(item: MusicBoxQueueItem): MusicBoxSearchResult {
            val artist = item.artist.trim()
            return MusicBoxSearchResult(
                trackId = item.trackId,
                name = item.title,
                artists = if (artist.isEmpty()) emptyList() else listOf(artist),
                source = item.source,
            )
        }
    }
}

/**
 * Only commands whose result depends on the exact snapshot being edited use
 * optimistic concurrency. Transport commands are serialized by the server and
 * intentionally operate on the authoritative state at execution time; adding
 * a UI snapshot revision to them would turn harmless realtime updates into
 * false conflicts.
 */
fun musicBoxControlRequiresExpectedRevision(action: String): Boolean {
    return action == "set_mode"
}

/**
 * Keeps actionable server feedback when a music-box command fails while still
 * providing stable copy for transport and unexpected failures.
 */
fun musicBoxControlErrorMessage(error: Any, fallback: String): String {
    if (error is ApiException && error.message.trim().isNotEmpty()) {
        return error.message
    }
    return fallback
}

/**
 * New servers provide monotonically increasing revisions.  Legacy servers do
 * not, so the old whole-snapshot behaviour remains the compatibility path.
 */
fun shouldAcceptMusicBoxSnapshot(
    current: MusicBoxState?,
    incoming: MusicBoxState,
): Boolean {
    if (current == null || !current.hasRevision || !incoming.hasRevision) {
        return true
    }
    if (incoming.revision > current.revision) return true
    if (incoming.revision < current.revision) return false
    return incoming.playback.currentItemId == current.playback.currentItemId
}

/**
 * Reduces a realtime full snapshot without letting compatibility heartbeats
 * replace actor-specific capabilities every second.
 *
 * Room fan-out events deliberately carry conservative capabilities because
 * the same payload is shared by users with different roles. Current servers
 * still emit a full snapshot for each progress tick unless compact-progress
 * mode is enabled. When revision, track and transport state are unchanged,
 * only the position is new; retaining the existing snapshot keeps the
 * personalized permissions stable and avoids a redundant state fetch. A real
 * structural change returns [incoming] unchanged so callers can refresh the
 * actor-specific snapshot.
 */
fun applyMusicBoxRealtimeSnapshot(
    current: MusicBoxState?,
    incoming: MusicBoxState,
): MusicBoxState? {
    if (!shouldAcceptMusicBoxSnapshot(current, incoming)) return current
    if (current == null ||
        !current.hasRevision ||
        !incoming.hasRevision ||
        current.revision != incoming.revision ||
        current.playback.currentItemId != incoming.playback.currentItemId ||
        current.playback.state != incoming.playback.state
    ) {
        return incoming
    }
    if (current.playback.positionMs == incoming.playback.positionMs) {
        return current
    }
    return current.copyWith(
        playback =
            current.playback.copyWith(
                positionMs = incoming.playback.positionMs,
                updatedAt = incoming.playback.updatedAt,
            ),
    )
}

/**
 * Applies a compact progress event only to the exact authoritative snapshot
 * it belongs to. Returning the identical instance makes stale events a cheap
 * no-op for UI callers.
 */
fun applyMusicBoxProgress(
    current: MusicBoxState?,
    progress: MusicBoxProgressEvent,
): MusicBoxState? {
    if (current == null ||
        !current.hasRevision ||
        current.revision != progress.revision ||
        current.playback.state != MusicBoxPlaybackState.playing ||
        current.playback.currentItemId.isEmpty() ||
        current.playback.currentItemId != progress.currentItemId ||
        progress.positionMs < 0
    ) {
        return current
    }
    if (current.playback.positionMs == progress.positionMs) return current
    return current.copyWith(
        playback = current.playback.copyWith(positionMs = progress.positionMs),
    )
}
