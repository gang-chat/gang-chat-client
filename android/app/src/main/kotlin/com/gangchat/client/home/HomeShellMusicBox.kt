package com.gangchat.client.home

import com.gangchat.client.app.applyMusicBoxProgress
import com.gangchat.client.app.applyMusicBoxRealtimeSnapshot
import com.gangchat.client.app.musicBoxDefaultSource
import com.gangchat.client.app.musicBoxPrimaryTransport
import com.gangchat.client.app.musicBoxTransportApiAction
import com.gangchat.client.app.normalizedMusicBoxSource
import com.gangchat.client.app.shouldAcceptMusicBoxSnapshot
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.debugPrint
import com.gangchat.client.foundation.kDebugMode
import com.gangchat.client.foundation.scheduleMicrotask
import com.gangchat.client.foundation.timeout
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.ApiException
import com.gangchat.client.protocol.MusicBoxPlaybackMode
import com.gangchat.client.protocol.MusicBoxProgressEvent
import com.gangchat.client.protocol.MusicBoxQueueItem
import com.gangchat.client.protocol.MusicBoxSearchResult
import com.gangchat.client.protocol.MusicBoxState
import com.gangchat.client.ui.FloatingNoticeTone
import com.gangchat.client.ui.showFloatingNotice
import com.gangchat.client.widgets.startTimer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException

// Port of lib/src/home/home_shell_music_box.dart.
//
// Music box orchestration: loading the per-room snapshot, search/queue/control
// writes, the local progress ticker, and SSE-driven refreshes. Audio itself is
// not handled here — once the user has joined the room's LiveKit session the
// music box bot's track arrives as an ordinary remote audio track.

internal fun _HomeShellState._markMusicPlaylistsChanged() {
    if (!mounted) return
    _setHomeState { _musicPlaylistsRevision += 1 }
}

/**
 * Resets all music box state. Called on room switch and account change so a
 * stale snapshot never bleeds across rooms.
 */
internal fun _HomeShellState._resetMusicBox() {
    _musicBoxLoadRetry?.cancel()
    _musicBoxLoadRetry = null
    _musicBoxLoadFailures = 0
    _musicBoxSearchDebounce?.cancel()
    _musicBoxSearchDebounce = null
    _musicBoxSearchSerial++
    _musicBoxRoomSerial++
    _musicBoxLoadingRoomId = null
    _musicBoxPendingRefreshRoomId = null
    _musicBox = null
    _musicBoxOpen = false
    _musicBoxSearchResults = emptyList()
    _musicBoxSearching = false
    _musicBoxSearchError = null
    _musicBoxSource = musicBoxDefaultSource
    if (_musicBoxSearchController.text.isNotEmpty()) {
        _musicBoxSearchController.clear()
    }
    _lastMusicBoxSearchText = _musicBoxSearchController.text
}

/**
 * Fetches the snapshot for [roomId]. A successful disabled snapshot still
 * hides the optional entry. Transient failures preserve the last valid
 * snapshot; clearing it would make both the inline player and open panel
 * disappear during token refreshes or brief network interruptions.
 */
internal suspend fun _HomeShellState._loadMusicBox(roomId: String, refreshAfterCurrent: Boolean = false) {
    if (_musicBoxLoadingRoomId == roomId) {
        if (refreshAfterCurrent) {
            _musicBoxPendingRefreshRoomId = roomId
        }
        return
    }
    val roomSerial = _musicBoxRoomSerial
    _musicBoxLoadRetry?.cancel()
    _musicBoxLoadRetry = null
    _musicBoxLoadingRoomId = roomId
    try {
        val state = timeout(12.seconds) { _musicBoxController.getState(roomId) }
        if (!mounted || _selectedServerId != roomId || roomSerial != _musicBoxRoomSerial) {
            return
        }
        _musicBoxLoadFailures = 0
        _setHomeState { _musicBox = state }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (kDebugMode) debugPrint("music-box: failed to load state for $roomId: $error")
        // Keep the last authoritative snapshot. When this was the first load,
        // schedule a bounded retry so one stalled request cannot hide the module
        // for the rest of the room session.
        _scheduleMusicBoxLoadRetry(roomId, roomSerial)
    } finally {
        var runPendingRefresh = false
        if (roomSerial == _musicBoxRoomSerial && _musicBoxLoadingRoomId == roomId) {
            _musicBoxLoadingRoomId = null
            runPendingRefresh = _musicBoxPendingRefreshRoomId == roomId
            if (runPendingRefresh) {
                _musicBoxPendingRefreshRoomId = null
            }
        }
        if (runPendingRefresh && mounted && _selectedServerId == roomId && roomSerial == _musicBoxRoomSerial) {
            scheduleMicrotask { unawaited { _loadMusicBox(roomId, refreshAfterCurrent = true) } }
        }
    }
}

private val _musicBoxLoadRetryDelays: List<Duration> =
    listOf(2.seconds, 5.seconds, 15.seconds, 30.seconds, 1.minutes)

private fun _HomeShellState._scheduleMusicBoxLoadRetry(roomId: String, roomSerial: Int) {
    if (!mounted ||
        _selectedServerId != roomId ||
        roomSerial != _musicBoxRoomSerial ||
        (_musicBoxLoadRetry?.isActive ?: false)
    ) {
        return
    }
    val delay = _musicBoxLoadRetryDelays[_musicBoxLoadFailures.coerceIn(0, _musicBoxLoadRetryDelays.size - 1)]
    _musicBoxLoadFailures++
    _musicBoxLoadRetry =
        _scope?.startTimer(delay) {
            _musicBoxLoadRetry = null
            if (!mounted || _selectedServerId != roomId || roomSerial != _musicBoxRoomSerial) {
                return@startTimer
            }
            unawaited { _loadMusicBox(roomId) }
        }
}

internal fun _HomeShellState._ensureMusicBoxLoaded(roomId: String) {
    if (_selectedServerId != roomId || _musicBox != null) return
    unawaited { _loadMusicBox(roomId) }
}

internal fun _HomeShellState._toggleMusicBoxPanel() {
    _setHomeState { _musicBoxOpen = !_musicBoxOpen }
}

/** Switches the search source and re-runs the current query against it. */
internal fun _HomeShellState._changeMusicBoxSource(source: String) {
    val normalizedSource = normalizedMusicBoxSource(source)
    if (normalizedSource == _musicBoxSource) return
    _setHomeState { _musicBoxSource = normalizedSource }
    val keyword = _musicBoxSearchController.text.trim()
    if (keyword.isEmpty()) return
    _musicBoxSearchDebounce?.cancel()
    _setHomeState {
        _musicBoxSearchResults = emptyList()
        _musicBoxSearching = true
        _musicBoxSearchError = null
    }
    unawaited { _searchMusicBox(keyword) }
}

/**
 * Applies an authoritative snapshot from a write response or SSE event,
 * overwriting local state wholesale per the server contract.
 */
internal fun _HomeShellState._applyMusicBoxSnapshot(state: MusicBoxState) {
    if (!mounted) return
    if (!shouldAcceptMusicBoxSnapshot(_musicBox, state)) return
    _setHomeState { _musicBox = state }
}

internal fun _HomeShellState._onMusicBoxChanged(event: JsonMap) {
    val roomId = event["room_id"] as String?
    if (roomId == null || roomId != _selectedServerId) return
    // The realtime client flattens the event envelope, merging the payload's
    // fields up alongside `room_id` (see LiveStreamClient._emit). The snapshot
    // therefore lives at the top level of [event], not under a `data` key.
    val incoming = MusicBoxState.fromJson(event)
    val current = _musicBox
    val next = applyMusicBoxRealtimeSnapshot(current, incoming)
    if (next === current) return
    if (next != null) _setHomeState { _musicBox = next }
    // A same-revision compatibility heartbeat was reduced to a position-only
    // update above, preserving actor-specific capabilities and avoiding a GET
    // on every second. Only an accepted structural/legacy snapshot needs the
    // personalized refresh.
    if (next !== incoming) return
    // Room fan-out cannot carry one shared capability set because every
    // subscriber may have a different room role. Refresh the actor-specific
    // snapshot after applying the structural event; concurrent events are
    // coalesced by _loadMusicBox.
    unawaited { _loadMusicBox(roomId, refreshAfterCurrent = true) }
}

internal fun _HomeShellState._onMusicBoxProgress(event: JsonMap) {
    val roomId = event["room_id"] as String?
    if (roomId == null || roomId != _selectedServerId) return
    val next = applyMusicBoxProgress(_musicBox, MusicBoxProgressEvent.fromJson(event))
    if (next == null || next === _musicBox) return
    _setHomeState { _musicBox = next }
}

// --- Writes -----------------------------------------------------------

private suspend fun _HomeShellState._searchMusicBox(keyword: String) {
    val roomId = _selectedServerId
    val trimmed = keyword.trim()
    if (roomId == null) return
    if (trimmed.isEmpty()) {
        _setHomeState {
            _musicBoxSearchResults = emptyList()
            _musicBoxSearching = false
            _musicBoxSearchError = null
        }
        return
    }
    val serial = ++_musicBoxSearchSerial
    _setHomeState {
        _musicBoxSearching = true
        _musicBoxSearchError = null
    }
    try {
        val results =
            _musicBoxController.search(
                roomId = roomId,
                keyword = trimmed,
                source = normalizedMusicBoxSource(_musicBoxSource),
            )
        if (!mounted || serial != _musicBoxSearchSerial) return
        _setHomeState {
            _musicBoxSearchResults = results
            _musicBoxSearching = false
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted || serial != _musicBoxSearchSerial) return
        _setHomeState {
            _musicBoxSearching = false
            _musicBoxSearchError = _musicBoxSearchErrorMessage(error)
        }
    }
}

/**
 * Debounced search driven by the search field's controller listener.
 *
 * Flips the searching state immediately so the panel swaps to the results
 * view (and shows a spinner) on the first keystroke, then debounces the
 * actual network call.
 */
internal fun _HomeShellState._handleMusicBoxSearchChanged() {
    val rawKeyword = _musicBoxSearchController.text
    if (rawKeyword == _lastMusicBoxSearchText) return
    _lastMusicBoxSearchText = rawKeyword
    _musicBoxSearchDebounce?.cancel()
    val keyword = rawKeyword.trim()
    if (keyword.isEmpty()) {
        unawaited { _searchMusicBox("") }
        return
    }
    _setHomeState {
        _musicBoxSearching = true
        _musicBoxSearchError = null
    }
    _musicBoxSearchDebounce = _scope?.startTimer(350.milliseconds) { unawaited { _searchMusicBox(keyword) } }
}

internal suspend fun _HomeShellState._queueMusicBoxTrack(result: MusicBoxSearchResult) {
    val roomId = _selectedServerId ?: return
    try {
        val state = _musicBoxController.queueSearchResult(roomId = roomId, result = result)
        _applyMusicBoxSnapshot(state)
        _showMusicBoxNotice("已加入队列：${result.name}", tone = FloatingNoticeTone.success)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        _showMusicBoxNotice(_musicBoxWriteErrorMessage(error), tone = FloatingNoticeTone.error)
    }
}

internal suspend fun _HomeShellState._removeMusicBoxItem(item: MusicBoxQueueItem) {
    val roomId = _selectedServerId ?: return
    try {
        val state = _musicBoxController.removeItem(roomId = roomId, itemId = item.id)
        _applyMusicBoxSnapshot(state)
        _showMusicBoxNotice("已从队列删除：${item.title}", tone = FloatingNoticeTone.success)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        _showMusicBoxNotice(_musicBoxWriteErrorMessage(error), tone = FloatingNoticeTone.error)
    }
}

internal suspend fun _HomeShellState._controlMusicBox(action: String, mode: MusicBoxPlaybackMode? = null) {
    val roomId = _selectedServerId ?: return
    try {
        val state = _musicBoxController.control(roomId = roomId, action = action, mode = mode, currentState = _musicBox)
        _applyMusicBoxSnapshot(state)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        _showMusicBoxNotice(_musicBoxWriteErrorMessage(error), tone = FloatingNoticeTone.error)
    }
}

internal suspend fun _HomeShellState._changeMusicBoxPlaybackMode(mode: MusicBoxPlaybackMode) {
    _controlMusicBox("set_mode", mode = mode)
}

internal fun _HomeShellState._toggleMusicBoxPlayback() {
    val state = _musicBox ?: return
    val action = musicBoxPrimaryTransport(state)
    unawaited { _controlMusicBox(musicBoxTransportApiAction(action)) }
}

// --- Error mapping ----------------------------------------------------

private fun _musicBoxSearchErrorMessage(error: Any): String {
    if (error is ApiException && error.statusCode == 502) {
        return "搜索服务暂时不可用，请稍后重试"
    }
    return "搜索失败，请重试"
}

private fun _musicBoxWriteErrorMessage(error: Any): String {
    if (error is ApiException) {
        when (error.code) {
            "music_box_unavailable" -> return "音乐盒当前不可用"
            "music_box_item_already_queued" -> return "已在队列中"
            "music_box_queue_limit_reached" -> return "点歌队列已达 200 首上限"
        }
    }
    return "操作失败，请重试"
}

private fun _HomeShellState._showMusicBoxNotice(message: String, tone: FloatingNoticeTone = FloatingNoticeTone.info) {
    if (!mounted) return
    showFloatingNotice(context, message, tone = tone)
}
