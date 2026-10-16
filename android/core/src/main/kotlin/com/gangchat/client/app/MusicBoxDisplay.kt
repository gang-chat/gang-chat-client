package com.gangchat.client.app

import com.gangchat.client.protocol.MusicBoxActiveSource
import com.gangchat.client.protocol.MusicBoxActiveSourceType
import com.gangchat.client.protocol.MusicBoxPlaybackState
import com.gangchat.client.protocol.MusicBoxQueueItem
import com.gangchat.client.protocol.MusicBoxQueueItemStatus
import com.gangchat.client.protocol.MusicBoxState
import com.gangchat.client.protocol.MusicBoxUsage
import com.gangchat.client.protocol.musicBoxActiveSourceTypeValue
import java.util.Locale

// Pure presentation logic for the room music box. No UI imports: every
// rendering decision (progress, status text, delete permission, formatting)
// lives here so it can be unit-tested without a widget tree, mirroring
// `live_display.dart`.

/**
 * The live playback position. Fully server-authoritative: the client renders
 * the snapshot's [MusicBoxPlayback.positionMs] as-is and never extrapolates
 * locally. The server pushes a fresh snapshot every second (the
 * `music_box_changed` event), so the bar advances purely by re-rendering each
 * snapshot — no client clock is involved, so there's nothing to drift.
 */
class MusicBoxProgress(val positionMs: Int, val durationMs: Int) {
    /** 0.0–1.0 for a progress bar; 0 when the duration is unknown. */
    val fraction: Double
        get() {
            if (durationMs <= 0) return 0.0
            val ratio = positionMs.toDouble() / durationMs
            if (ratio.isNaN()) return 0.0
            return ratio.coerceIn(0.0, 1.0)
        }
}

/**
 * The position to render for [state]. Reads the server-reported position
 * straight from the snapshot, floored to whole seconds for a steady
 * per-second display, and clamped to the track duration.
 */
fun musicBoxProgress(state: MusicBoxState): MusicBoxProgress {
    val current = state.currentItem
    val durationMs = current?.durationMs ?: 0
    var positionMs = state.playback.positionMs
    if (positionMs < 0) positionMs = 0
    // Floor to whole seconds so the bar and the time label move in second steps.
    positionMs -= positionMs % 1000
    if (durationMs > 0 && positionMs > durationMs) positionMs = durationMs
    return MusicBoxProgress(positionMs = positionMs, durationMs = durationMs)
}

/**
 * The play/pause toggle the primary transport button should drive, given the
 * current state. `play` resumes from pause, starts a stopped queue, and is a
 * no-op target only when the queue is empty.
 */
enum class MusicBoxTransportAction { play, pause, resume }

fun musicBoxPrimaryTransport(state: MusicBoxState): MusicBoxTransportAction {
    return when (state.playback.state) {
        MusicBoxPlaybackState.playing -> MusicBoxTransportAction.pause
        MusicBoxPlaybackState.paused -> MusicBoxTransportAction.resume
        MusicBoxPlaybackState.stopped -> MusicBoxTransportAction.play
    }
}

/**
 * User-facing label for the room's direct-request queue. The wire protocol
 * intentionally keeps the stable `temporary` identifier for old clients and
 * rolling server deployments, while every UI surface uses the product name.
 */
const val musicBoxRequestQueueLabel: String = "点歌队列"

/**
 * The sections of the flat music-box list, in display order: the request
 * queue (always open) and the two saved-playlist groups (collapsed until
 * opened). "What is playing" is marked on the section that owns the active
 * source; "what is open" is the user's choice and realtime never changes it.
 */
enum class MusicBoxSection { queue, roomPlaylists, myPlaylists }

/**
 * The section that lists [type]. A personal playlist owned by someone else
 * is not listable by the viewer, but it still lives under 我的歌单 so the
 * now-playing chip has a deterministic home for its snapshot view.
 */
fun musicBoxSectionForSource(type: MusicBoxActiveSourceType): MusicBoxSection {
    return when (type) {
        MusicBoxActiveSourceType.temporary -> MusicBoxSection.queue
        MusicBoxActiveSourceType.roomPlaylist -> MusicBoxSection.roomPlaylists
        MusicBoxActiveSourceType.userPlaylist -> MusicBoxSection.myPlaylists
    }
}

/** Stable key for one saved playlist inside the flat list. */
fun musicBoxPlaylistKey(type: MusicBoxActiveSourceType, id: String): String {
    return "${musicBoxActiveSourceTypeValue(type)}:$id"
}

/**
 * Whether the saved playlist [type]/[id] (or the request queue when [type]
 * is temporary) is the room's active source. Activation is server-owned, so
 * this is a pure comparison against the authoritative snapshot.
 */
fun musicBoxSourceIsActive(
    state: MusicBoxState,
    type: MusicBoxActiveSourceType,
    id: String = "",
): Boolean {
    val active = state.activeSource
    if (type == MusicBoxActiveSourceType.temporary) {
        return active.type == MusicBoxActiveSourceType.temporary
    }
    return active.type == type && active.id == id
}

/**
 * Whether a saved-playlist template row describes the track the room is
 * currently playing. Template item ids differ from the activation snapshot's
 * queue item ids, so the match is by catalog identity instead.
 */
fun musicBoxTrackIsCurrent(
    state: MusicBoxState,
    source: String,
    trackId: String,
): Boolean {
    val current = state.currentItem ?: return false
    return musicBoxTrackLinkKey(source = current.source, trackId = current.trackId) ==
        musicBoxTrackLinkKey(source = source, trackId = trackId)
}

/**
 * Stable identity for a concrete catalog track. Sources are protocol enums
 * and therefore case-insensitive; external track ids keep their original case
 * because providers may treat them as case-sensitive.
 */
fun musicBoxTrackLinkKey(source: String, trackId: String): String {
    return "${source.trim().lowercase()}:${trackId.trim()}"
}

/**
 * Whether [source]/[trackId] already exists in the room's direct-request
 * queue. Saved-playlist snapshots deliberately do not count as requests.
 */
fun musicBoxRequestQueueContainsTrack(
    queue: List<MusicBoxQueueItem>,
    source: String,
    trackId: String,
): Boolean {
    val target = musicBoxTrackLinkKey(source = source, trackId = trackId)
    return queue.any { item ->
        musicBoxTrackLinkKey(source = item.source, trackId = item.trackId) == target
    }
}

fun musicBoxActiveSourceLabel(source: MusicBoxActiveSource): String {
    if (source.type == MusicBoxActiveSourceType.temporary) {
        return musicBoxRequestQueueLabel
    }
    val name = source.name.trim()
    if (name.isNotEmpty()) return name
    return if (source.type == MusicBoxActiveSourceType.roomPlaylist) "房间歌单" else "个人歌单"
}

/**
 * Stable source ids stay on the wire; every song surface uses the same
 * user-facing source label. Unknown sources remain visible for forward
 * compatibility instead of being mislabeled as the default source.
 */
fun musicBoxSourceLabel(value: String): String {
    val source = value.trim()
    for (candidate in musicBoxSources) {
        if (candidate.id == source) return candidate.label
    }
    return if (source.isEmpty()) "未知来源" else source
}

/**
 * Bilibili catalog entries describe their creator as an uploader/author,
 * while music providers expose the same wire field as an artist. Keeping the
 * source-specific wording here makes every song card use the same label.
 */
fun musicBoxArtistFieldLabel(source: String): String {
    return if (source.trim().lowercase() == "bilibili") "作者" else "歌手"
}

fun musicBoxTransportApiAction(action: MusicBoxTransportAction): String {
    return when (action) {
        MusicBoxTransportAction.play -> "play"
        MusicBoxTransportAction.pause -> "pause"
        MusicBoxTransportAction.resume -> "resume"
    }
}

/**
 * Whether the spinning vinyl should rotate: it spins while playing and freezes
 * otherwise, giving an at-a-glance read of playback.
 */
fun musicBoxRecordSpinning(state: MusicBoxState): Boolean {
    return state.playback.state == MusicBoxPlaybackState.playing
}

/**
 * Whether the room currently has a resumable playback context.
 *
 * [MusicBoxActiveSource] is retained after playback stops so the user can keep
 * browsing the last source. That source is not "playing" until the server also
 * reports a current item and a playing/paused transport state.
 */
fun musicBoxHasActivePlayback(state: MusicBoxState): Boolean {
    return state.currentItem != null &&
        state.playback.state != MusicBoxPlaybackState.stopped
}

/**
 * A short status label for a queue item, reflecting its download/transcode
 * lifecycle. Returns null for [MusicBoxQueueItemStatus.ready], which renders
 * normally with no badge.
 */
fun musicBoxQueueStatusLabel(item: MusicBoxQueueItem): String? {
    return when (item.status) {
        MusicBoxQueueItemStatus.pending -> "排队中，等待下载"
        MusicBoxQueueItemStatus.downloading -> "下载中"
        MusicBoxQueueItemStatus.failed -> musicBoxQueueErrorLabel(item)
        MusicBoxQueueItemStatus.ready -> null
    }
}

/**
 * The error line for a failed item, falling back to a generic message when the
 * server didn't supply one.
 */
fun musicBoxQueueErrorLabel(item: MusicBoxQueueItem): String {
    val error = item.error.trim()
    return if (error.isEmpty()) "处理失败" else error
}

/**
 * A spinning vinyl is only meaningful once a track is current; tiles still in
 * the queue show artwork without rotation.
 */
fun musicBoxIsCurrent(state: MusicBoxState, item: MusicBoxQueueItem): Boolean {
    return state.playback.currentItemId == item.id && item.id.isNotEmpty()
}

/**
 * A near-limit hint for the usage meter, or null when there's comfortable
 * headroom. Triggers at 90%. Over the limit, new songs aren't rejected — they
 * queue as `pending` and download once earlier tracks finish playing.
 */
fun musicBoxUsageHint(usage: MusicBoxUsage): String? {
    if (usage.limitBytes <= 0) return null
    val ratio = usage.usedBytes.toDouble() / usage.limitBytes
    if (ratio < 0.9) return null
    if (ratio >= 1.0) return "空间已满，新歌将排队等待下载"
    return "空间已接近上限"
}

fun musicBoxUsageFraction(usage: MusicBoxUsage): Double {
    if (usage.limitBytes <= 0) return 0.0
    return (usage.usedBytes.toDouble() / usage.limitBytes).coerceIn(0.0, 1.0)
}

/** Joins a search hit's artist list for display, e.g. `林俊杰、孙燕姿`. */
fun musicBoxArtistsLabel(artists: List<String>): String {
    return artists.filter { a -> a.trim().isNotEmpty() }.joinToString("、")
}

/** `mm:ss` (or `h:mm:ss`) for a millisecond duration; em dash when unknown. */
fun musicBoxFormatDuration(milliseconds: Int): String {
    if (milliseconds <= 0) return "--:--"
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val mm = minutes.toString().padStart(if (hours > 0) 2 else 1, '0')
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$mm:$ss" else "$mm:$ss"
}

/** Human-readable byte size, e.g. `3.7 MB`. */
fun musicBoxFormatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit += 1
    }
    val text =
        if (unit == 0 || value >= 100) {
            String.format(Locale.ROOT, "%.0f", value)
        } else {
            String.format(Locale.ROOT, "%.1f", value)
        }
    // Drop a trailing `.0` so whole values read `5 MB`, not `5.0 MB`.
    val trimmed =
        if (text.endsWith(".0")) {
            text.substring(0, text.length - 2)
        } else {
            text
        }
    return "$trimmed ${units[unit]}"
}

fun musicBoxUsageLabel(usage: MusicBoxUsage): String {
    return "${musicBoxFormatBytes(usage.usedBytes)} / " +
        musicBoxFormatBytes(usage.limitBytes)
}

/**
 * A selectable music search source. netease and bilibili go through the GD
 * music API; tencent routes to the self-hosted QQ Music service on the server.
 */
class MusicBoxSource(
    /** The `source` value the server routes on. */
    val id: String,
    /** Short display name for the source picker. */
    val label: String,
)

/**
 * Temporarily keeps QQ Music out of the client without removing its routing
 * implementation. Re-enable it here once the source is ready for users again.
 */
const val musicBoxTencentSourceEnabled: Boolean = false

/** The default source id (netease). */
const val musicBoxDefaultSource: String = "netease"

/** The sources offered in the search picker, in display order. */
val musicBoxSources: List<MusicBoxSource> =
    buildList {
        add(MusicBoxSource(id = "netease", label = "网易云"))
        add(MusicBoxSource(id = "bilibili", label = "哔哩哔哩"))
        @Suppress("KotlinConstantConditions")
        if (musicBoxTencentSourceEnabled) {
            add(MusicBoxSource(id = "tencent", label = "QQ音乐"))
        }
    }

fun musicBoxSourceEnabled(source: String): Boolean {
    return musicBoxSources.any { candidate -> candidate.id == source }
}

fun normalizedMusicBoxSource(source: String): String {
    return if (musicBoxSourceEnabled(source)) source else musicBoxDefaultSource
}
