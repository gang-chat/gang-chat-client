package com.gangchat.client.app

import com.gangchat.client.protocol.LiveParticipant
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.UserSummary

class JoinedLiveRoomSummary(
    val roomId: String,
    val displayName: String,
    val avatarLabel: String,
    val avatarUrl: String?,
    val defaultAvatarKey: String,
)

class LiveParticipantTileState(
    val broadcasting: Boolean,
    val highlighted: Boolean,
    val micMutedForDisplay: Boolean,
    val micActive: Boolean,
)

class AuthoritativeLivePresenceChanges(
    val joined: Set<String>,
    val left: Set<String>,
)

/**
 * User identities which the server still considers present in the voice
 * channel. A reconnecting participant deliberately stays present during the
 * bounded recovery window; a first-time `joining` placeholder does not become
 * audible/visible presence until the app-level join reaches `online`.
 */
fun authoritativeLivePresenceUserIds(live: LiveState): Set<String> {
    val ids = LinkedHashSet<String>()
    for (participant in live.participants) {
        if (liveParticipantConnectionReady(participant) ||
            participant.connectionState.trim().lowercase() == "reconnecting"
        ) {
            ids.add(participant.user.id)
        }
    }
    return ids
}

/**
 * Computes join/leave cues from two authoritative server snapshots.
 *
 * LiveKit can emit `ParticipantDisconnectedEvent` at the start of a temporary
 * reconnect while the server correctly keeps that person in the roster. Cues
 * must therefore follow this snapshot transition rather than the raw media
 * callback, otherwise users hear a leave sound while the member remains.
 */
fun authoritativeLivePresenceChanges(
    before: Set<String>,
    after: Set<String>,
): AuthoritativeLivePresenceChanges {
    return AuthoritativeLivePresenceChanges(
        joined = after - before,
        left = before - after,
    )
}

class LiveMicControlState(
    val mutedForDisplay: Boolean,
    val active: Boolean,
    val enabled: Boolean,
)

enum class LiveScreenSourceListBodyState { loading, empty, results }

class LiveScreenSourcePickerState<T>(
    val sources: List<T>? = null,
    val selectedId: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
) {
    /**
     * Like Dart, [selectedId] and [error] use a sentinel default: omit them to
     * keep the current value, pass `null` to clear it.
     */
    fun copyWith(
        sources: List<T>? = null,
        selectedId: Any? = _liveScreenSourceSelectedIdUnchanged,
        loading: Boolean? = null,
        error: Any? = _liveScreenSourceErrorUnchanged,
    ): LiveScreenSourcePickerState<T> {
        return LiveScreenSourcePickerState(
            sources = sources ?: this.sources,
            selectedId =
                if (selectedId === _liveScreenSourceSelectedIdUnchanged) {
                    this.selectedId
                } else {
                    selectedId as String?
                },
            loading = loading ?: this.loading,
            error =
                if (error === _liveScreenSourceErrorUnchanged) {
                    this.error
                } else {
                    error as String?
                },
        )
    }
}

private val _liveScreenSourceSelectedIdUnchanged = Any()
private val _liveScreenSourceErrorUnchanged = Any()

fun liveParticipantByUserId(live: LiveState?, userId: String): LiveParticipant? {
    if (live == null) return null
    for (participant in live.participants) {
        if (participant.user.id == userId) return participant
    }
    return null
}

fun liveParticipantDisplayName(
    live: LiveState?,
    userId: String,
    fallback: String = "",
): String {
    val participant = liveParticipantByUserId(live, userId) ?: return fallback
    return liveUserDisplayName(participant.user, fallback = fallback)
}

fun liveUserDisplayName(user: UserSummary, fallback: String = ""): String {
    val roomName = _nonEmpty(user.roomDisplayName)
    if (roomName != null) return roomName
    val displayName = _nonEmpty(user.displayName)
    if (displayName != null) return displayName
    val username = _nonEmpty(user.username)
    if (username != null) return username
    return fallback
}

fun visibleLiveParticipantsForStage(
    participants: Iterable<LiveParticipant>,
    currentUserId: String,
    localParticipantReady: Boolean,
    connectedParticipantIds: Set<String> = emptySet(),
): List<LiveParticipant> {
    return participants.filter { participant ->
        (localParticipantReady || participant.user.id != currentUserId) &&
            liveParticipantVisibleInRoster(
                participant,
                connectedParticipantIds = connectedParticipantIds,
            )
    }
}

fun liveParticipantConnectionReady(participant: LiveParticipant): Boolean {
    val state = participant.connectionState.trim().lowercase()
    return state == "online" || state == "connected" || state == "joined"
}

fun liveParticipantVisibleInRoster(
    participant: LiveParticipant,
    connectedParticipantIds: Set<String> = emptySet(),
): Boolean {
    val connectionState = participant.connectionState.trim().lowercase()
    // A participant marked reconnecting remains part of the server's live
    // count during its bounded reconnect grace period. Keep that same member in
    // the open roster even while LiveKit is rebuilding the remote-participant
    // map, otherwise the room card says X users while the channel shows X - 1.
    // A provisional first-time "joining" participant is still hidden until
    // LiveKit confirms it; mic mute is independent of roster visibility.
    return liveParticipantConnectionReady(participant) ||
        connectionState == "reconnecting" ||
        connectedParticipantIds.contains(participant.user.id)
}

fun liveStateMissingConnectedParticipants(
    live: LiveState?,
    connectedParticipantIds: Set<String>,
): Boolean {
    if (live == null || connectedParticipantIds.isEmpty()) return false
    val liveUserIds = live.participants.mapTo(LinkedHashSet()) { participant -> participant.user.id }
    return connectedParticipantIds.any { id -> !liveUserIds.contains(id) }
}

fun liveParticipantTileState(
    participant: LiveParticipant,
    speaking: Boolean,
    liveKitMicMuted: Boolean? = null,
): LiveParticipantTileState {
    val broadcasting = participant.cameraOn || participant.screenSharing
    // App mute uses capture gain 0 while deliberately leaving the published
    // LiveKit microphone track enabled. The server snapshot is therefore the
    // authority for logical mute. A newly connected LiveKit participant exists
    // briefly before its microphone publication arrives, which LiveKit reports
    // as muted. Only let media state tighten the display after the participant
    // has completed the app-level join; otherwise an open-mic join visibly
    // flashes as muted. A real publish failure is reported to the server before
    // connection_state becomes ready, so it still renders muted.
    val liveKitMicMutedForDisplay =
        liveParticipantConnectionReady(participant) && liveKitMicMuted == true
    val micMutedForDisplay =
        participant.micBlocked ||
            participant.voiceBlocked ||
            participant.micMuted ||
            liveKitMicMutedForDisplay
    return LiveParticipantTileState(
        broadcasting = broadcasting,
        highlighted = speaking || broadcasting,
        micMutedForDisplay = micMutedForDisplay,
        micActive = !micMutedForDisplay && speaking,
    )
}

fun <T> pickLiveStageShare(
    tracks: Iterable<T>,
    isScreenShare: (track: T) -> Boolean,
    isLocal: (track: T) -> Boolean,
): T? {
    var localShare: T? = null
    for (track in tracks) {
        if (!isScreenShare(track)) continue
        if (!isLocal(track)) return track
        if (localShare == null) localShare = track
    }
    return localShare
}

fun <T> liveScreenShareByIdentity(
    tracks: Iterable<T>,
    identity: String?,
    trackIdentity: (track: T) -> String,
    isScreenShare: (track: T) -> Boolean,
): T? {
    if (identity == null) return null
    for (track in tracks) {
        if (isScreenShare(track) && trackIdentity(track) == identity) return track
    }
    return null
}

fun shouldExitMissingFullScreenShare(
    fullScreenShareIdentity: String?,
    fullScreenShare: Any?,
): Boolean {
    return fullScreenShareIdentity != null && fullScreenShare == null
}

fun shouldPatchEndedLocalScreenShare(
    localScreenSharing: Boolean,
    sessionScreenSharing: Boolean,
    joinedLiveRoomId: String?,
    selectedRoomId: String?,
): Boolean {
    return localScreenSharing &&
        !sessionScreenSharing &&
        joinedLiveRoomId != null &&
        joinedLiveRoomId == selectedRoomId
}

fun joinedLiveRoomSummary(
    joinedLiveRoomId: String?,
    selectedRoom: RoomDetail?,
    rooms: Iterable<RoomCard>,
): JoinedLiveRoomSummary? {
    val roomId = _nonEmpty(joinedLiveRoomId) ?: return null

    val selected = selectedRoom
    if (selected != null && selected.id == roomId) {
        return JoinedLiveRoomSummary(
            roomId = selected.id,
            displayName = _roomDetailDisplayName(selected),
            avatarLabel = _roomDetailAvatarLabel(selected),
            avatarUrl = selected.avatarUrl,
            defaultAvatarKey = selected.defaultAvatarKey,
        )
    }

    for (room in rooms) {
        if (room.id != roomId) continue
        return JoinedLiveRoomSummary(
            roomId = room.id,
            displayName = room.displayName,
            avatarLabel = _roomCardAvatarLabel(room),
            avatarUrl = room.avatarUrl,
            defaultAvatarKey = room.defaultAvatarKey,
        )
    }

    return null
}

fun <T> reconcileLiveScreenSourceSelection(
    sources: Iterable<T>,
    selectedId: String?,
    sourceId: (source: T) -> String,
): String? {
    var firstSource: T? = null
    for (source in sources) {
        if (firstSource == null) firstSource = source
        if (sourceId(source) == selectedId) return selectedId
    }
    return if (firstSource == null) null else sourceId(firstSource)
}

fun <T> liveScreenSourceById(
    sources: Iterable<T>?,
    selectedId: String?,
    sourceId: (source: T) -> String,
): T? {
    if (selectedId == null || sources == null) return null
    for (source in sources) {
        if (sourceId(source) == selectedId) return source
    }
    return null
}

fun <T> liveScreenSourceListBodyState(
    sources: Iterable<T>?,
): LiveScreenSourceListBodyState {
    if (sources == null) return LiveScreenSourceListBodyState.loading
    if (!sources.iterator().hasNext()) return LiveScreenSourceListBodyState.empty
    return LiveScreenSourceListBodyState.results
}

fun <T> liveScreenSourceSelected(
    source: T,
    selectedId: String?,
    sourceId: (source: T) -> String,
): Boolean {
    return selectedId != null && sourceId(source) == selectedId
}

fun canConfirmLiveScreenSourceSelection(selectedId: String?): Boolean {
    return selectedId != null
}

fun <T> canLoadLiveScreenSources(state: LiveScreenSourcePickerState<T>): Boolean {
    return !state.loading
}

fun <T> liveScreenSourceLoadStarted(
    state: LiveScreenSourcePickerState<T>,
): LiveScreenSourcePickerState<T> {
    return state.copyWith(loading = true, error = null)
}

fun <T> liveScreenSourceLoadSucceeded(
    state: LiveScreenSourcePickerState<T>,
    sources: Iterable<T>,
    sourceId: (source: T) -> String,
): LiveScreenSourcePickerState<T> {
    val nextSources = sources.toList()
    return state.copyWith(
        sources = nextSources,
        selectedId =
            reconcileLiveScreenSourceSelection(
                nextSources,
                selectedId = state.selectedId,
                sourceId = sourceId,
            ),
        loading = false,
        error = null,
    )
}

fun <T> liveScreenSourceLoadFailed(
    state: LiveScreenSourcePickerState<T>,
    failure: Any,
): LiveScreenSourcePickerState<T> {
    return state.copyWith(
        loading = false,
        error = userFacingErrorMessage(failure, fallback = "加载语音频道失败"),
    )
}

fun <T> liveScreenSourceSelectedChanged(
    state: LiveScreenSourcePickerState<T>,
    selectedId: String?,
): LiveScreenSourcePickerState<T> {
    return state.copyWith(selectedId = selectedId)
}

/** Dart's `T extends Iterable<int>` (a thumbnail as a list of byte values). */
fun <T : Iterable<Int>> visibleLiveScreenSourceThumbnail(
    thumbnail: T?,
    imageError: Any?,
): T? {
    if (thumbnail == null || !thumbnail.iterator().hasNext() || imageError != null) {
        return null
    }
    return thumbnail
}

/** The same check for a `Uint8List` thumbnail, which is a [ByteArray] here. */
fun visibleLiveScreenSourceThumbnail(
    thumbnail: ByteArray?,
    imageError: Any?,
): ByteArray? {
    if (thumbnail == null || thumbnail.isEmpty() || imageError != null) {
        return null
    }
    return thumbnail
}

fun canOpenLiveStageShareFullScreen(
    stageShareIdentity: String,
    localUserId: String,
): Boolean {
    return stageShareIdentity != localUserId
}

fun liveScreenShareStageLabel(displayName: String): String {
    return if (displayName.isEmpty()) "屏幕共享" else "$displayName 的屏幕"
}

fun liveScreenShareViewers(
    live: LiveState?,
    broadcasterUserId: String,
): List<UserSummary> {
    val participant = liveParticipantByUserId(live, broadcasterUserId)
    if (participant == null || !participant.screenSharing) {
        return emptyList()
    }
    return participant.screenViewers
        .filter { viewer -> viewer.id != broadcasterUserId }
}

fun liveMicControlState(
    micMuted: Boolean,
    voiceBlocked: Boolean,
): LiveMicControlState {
    if (voiceBlocked) {
        return LiveMicControlState(
            mutedForDisplay = true,
            active = false,
            enabled = false,
        )
    }
    return LiveMicControlState(
        mutedForDisplay = micMuted,
        active = !micMuted,
        enabled = true,
    )
}

fun liveForciblyRemovedNotice(): String {
    return "你已被移出语音"
}

@Suppress("UNUSED_PARAMETER")
fun liveVoiceConnectFailureMessage(error: Any): String {
    return "无法连接语音频道"
}

@Suppress("UNUSED_PARAMETER")
fun liveCameraOpenFailureMessage(error: Any): String {
    return "无法打开摄像头"
}

@Suppress("UNUSED_PARAMETER")
fun liveScreenShareFailureMessage(error: Any): String {
    return "无法共享屏幕"
}

private fun _roomDetailDisplayName(room: RoomDetail): String {
    return _nonEmpty(room.remarkName) ?: room.name
}

private fun _roomDetailAvatarLabel(room: RoomDetail): String {
    return _nonEmpty(room.name) ?: room.rid
}

private fun _roomCardAvatarLabel(room: RoomCard): String {
    return _nonEmpty(room.name) ?: room.rid
}

private fun _nonEmpty(value: String?): String? {
    val trimmed = value?.trim()
    if (trimmed == null || trimmed.isEmpty()) return null
    return trimmed
}
