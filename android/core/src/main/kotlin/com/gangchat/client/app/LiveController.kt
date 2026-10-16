package com.gangchat.client.app

import com.gangchat.client.foundation.AppCoroutines
import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.completedFuture
import com.gangchat.client.protocol.ApiException
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.protocol.LiveJoinResult
import com.gangchat.client.protocol.LiveParticipant
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.newClientId
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LiveDeparturePatch(val live: LiveState?, val rooms: List<RoomCard>)

class LiveLocalDeparturePatch(
    val live: LiveState?,
    val rooms: List<RoomCard>,
    val joinedLiveRoomId: String?,
    val joiningLive: Boolean,
    val cameraOn: Boolean,
    val screenSharing: Boolean,
    val voiceBlocked: Boolean,
)

class LivePublishPermissionPatch(
    val micMuted: Boolean,
    val voiceBlocked: Boolean,
)

class LiveOutputMutePatch(val headphonesMuted: Boolean)

enum class LiveModerationAction(val apiValue: String) {
    kick("kick"),
    muteMic("mute_mic"),
    blockVoice("block_voice"),
    restoreVoice("restore_voice"),
    restoreHeadphones("restore_headphones"),
}

class LiveJoinResultPatch(
    val micMuted: Boolean,
    val headphonesMuted: Boolean,
    val cameraOn: Boolean,
    val screenSharing: Boolean,
    val voiceBlocked: Boolean,
    val live: LiveState,
    val rooms: List<RoomCard>,
)

class LiveStateUpdatePatch(
    val micMuted: Boolean,
    val headphonesMuted: Boolean,
    val cameraOn: Boolean,
    val screenSharing: Boolean,
    val voiceBlocked: Boolean,
    val live: LiveState?,
)

/**
 * Keeps mutations of the current user's live state in request order.
 *
 * The live endpoint updates a single participant row. Sending optimistic UI
 * changes concurrently allows an older request to arrive last and overwrite a
 * newer microphone, headphones, camera, or screen-share choice. Failed
 * requests are delivered to their caller without breaking later requests.
 */
class LiveStateRequestQueue {
    private var _tail: Job = completedFuture(Unit)
    private var _pendingCount = 0

    val isIdle: Boolean get() = _pendingCount == 0

    /**
     * Dart's `Future<T> run<T>(Future<T> Function() request)`. Like the
     * `_tail.then(...)` chain, [request] never starts synchronously: it runs
     * on the event loop after every earlier request has settled.
     */
    fun <T> run(request: suspend () -> T): Deferred<T> {
        val completer = Completer<T>()
        _pendingCount += 1
        val previous = _tail
        _tail =
            AppCoroutines.scope.launch(AppCoroutines.loop) {
                previous.join()
                try {
                    val result = request()
                    _pendingCount -= 1
                    completer.complete(result)
                } catch (error: Throwable) {
                    _pendingCount -= 1
                    completer.completeError(error)
                }
            }
        return completer.future
    }
}

class LiveJoinStatePatch(
    val joinedLiveRoomId: String?,
    val joiningLive: Boolean,
    val livePanelOpen: Boolean,
    val error: String?,
)

class LiveJoinPreviousRoomDisconnectedPatch(
    val live: LiveState?,
    val rooms: List<RoomCard>,
    val joinedLiveRoomId: String?,
    val joiningLive: Boolean,
    val livePanelOpen: Boolean,
    val error: String?,
)

fun joinedLiveRoomToDisconnectBeforeJoin(
    joinedLiveRoomId: String?,
    targetRoomId: String,
): String? {
    if (joinedLiveRoomId == null || joinedLiveRoomId == targetRoomId) {
        return null
    }
    return joinedLiveRoomId
}

fun canPatchSelectedLiveState(
    joinedLiveRoomId: String?,
    selectedRoomId: String?,
): Boolean {
    return joinedLiveRoomId != null && joinedLiveRoomId == selectedRoomId
}

fun canApplyPickedScreenShareSource(
    pickedForRoomId: String,
    joinedLiveRoomId: String?,
    selectedRoomId: String?,
): Boolean {
    return joinedLiveRoomId == pickedForRoomId &&
        selectedRoomId == pickedForRoomId
}

fun isBenignGoneLiveStatePatch(error: Any): Boolean {
    return error is ApiException && error.statusCode == 409
}

fun shouldSyncLiveKitMicAfterServerPatch(
    requestedMicMuted: Boolean?,
    serverMicMuted: Boolean,
): Boolean {
    return requestedMicMuted != null && serverMicMuted != requestedMicMuted
}

fun liveParticipantWithExclusiveMedia(participant: LiveParticipant): LiveParticipant {
    if (!participant.cameraOn || !participant.screenSharing) return participant
    return participant.copyWith(cameraOn = false)
}

fun liveOutputMuteToggled(headphonesMuted: Boolean): LiveOutputMutePatch {
    return LiveOutputMutePatch(headphonesMuted = !headphonesMuted)
}

class LiveController(val api: GangApi) {
    suspend fun joinLive(
        roomId: String,
        source: String,
        micMuted: Boolean,
        headphonesMuted: Boolean,
    ): LiveJoinResult {
        return api.joinLive(
            roomId = roomId,
            clientLiveSessionId = newClientId("clive"),
            source = source,
            micMuted = micMuted,
            headphonesMuted = headphonesMuted,
        )
    }

    suspend fun updateMyState(
        roomId: String,
        micMuted: Boolean? = null,
        headphonesMuted: Boolean? = null,
        cameraOn: Boolean? = null,
        cameraMirrored: Boolean? = null,
        screenSharing: Boolean? = null,
        connectionState: String? = null,
    ): LiveParticipant {
        return api.updateMyLiveState(
            roomId = roomId,
            micMuted = micMuted,
            headphonesMuted = headphonesMuted,
            cameraOn = cameraOn,
            cameraMirrored = cameraMirrored,
            screenSharing = screenSharing,
            connectionState = connectionState,
        )
    }

    suspend fun leaveLive(roomId: String): LiveParticipant {
        return api.updateMyLiveState(roomId = roomId, connectionState = "left")
    }

    suspend fun updateMyLiveScreenView(
        roomId: String,
        broadcasterUserId: String? = null,
    ) {
        api.updateMyLiveScreenView(
            roomId = roomId,
            broadcasterUserId = broadcasterUserId,
        )
    }

    suspend fun kickParticipant(
        roomId: String,
        userId: String,
    ) {
        moderateParticipant(
            roomId = roomId,
            userId = userId,
            action = LiveModerationAction.kick,
        )
    }

    suspend fun moderateParticipant(
        roomId: String,
        userId: String,
        action: LiveModerationAction,
    ) {
        api.moderateLiveParticipant(
            roomId = roomId,
            userId = userId,
            action = action.apiValue,
        )
    }

    fun patchJoinStarted(joinedLiveRoomId: String?): LiveJoinStatePatch {
        return LiveJoinStatePatch(
            joinedLiveRoomId = joinedLiveRoomId,
            joiningLive = true,
            livePanelOpen = true,
            error = null,
        )
    }

    fun patchJoinPreviousRoomDisconnected(
        live: LiveState?,
        rooms: List<RoomCard>,
        previousRoomId: String,
        userId: String,
        livePanelOpen: Boolean,
        error: String?,
    ): LiveJoinPreviousRoomDisconnectedPatch {
        val departure =
            removeUserFromLive(
                live = live,
                rooms = rooms,
                roomId = previousRoomId,
                userId = userId,
            )
        return LiveJoinPreviousRoomDisconnectedPatch(
            live = departure.live,
            rooms = departure.rooms,
            joinedLiveRoomId = null,
            joiningLive = true,
            livePanelOpen = livePanelOpen,
            error = error,
        )
    }

    fun patchJoinConnected(
        roomId: String,
        livePanelOpen: Boolean,
        error: String?,
    ): LiveJoinStatePatch {
        return LiveJoinStatePatch(
            joinedLiveRoomId = roomId,
            joiningLive = true,
            livePanelOpen = livePanelOpen,
            error = error,
        )
    }

    fun patchJoinFinished(
        joinedLiveRoomId: String?,
        livePanelOpen: Boolean,
        error: String?,
    ): LiveJoinStatePatch {
        return LiveJoinStatePatch(
            joinedLiveRoomId = joinedLiveRoomId,
            joiningLive = false,
            livePanelOpen = livePanelOpen,
            error = error,
        )
    }

    fun patchLocalDeparture(
        live: LiveState?,
        rooms: List<RoomCard>,
        joinedLiveRoomId: String?,
        userId: String,
        joiningLive: Boolean,
    ): LiveLocalDeparturePatch {
        val roomId = joinedLiveRoomId
        val departure =
            if (roomId == null) {
                LiveDeparturePatch(live = live, rooms = rooms)
            } else {
                removeUserFromLive(
                    live = live,
                    rooms = rooms,
                    roomId = roomId,
                    userId = userId,
                )
            }
        return LiveLocalDeparturePatch(
            live = departure.live,
            rooms = departure.rooms,
            joinedLiveRoomId = null,
            joiningLive = joiningLive,
            cameraOn = false,
            screenSharing = false,
            voiceBlocked = false,
        )
    }

    fun patchPublishPermission(
        canPublish: Boolean,
        micMuted: Boolean,
    ): LivePublishPermissionPatch {
        return LivePublishPermissionPatch(
            micMuted = if (canPublish) micMuted else true,
            voiceBlocked = !canPublish,
        )
    }

    fun patchJoinResult(
        rooms: List<RoomCard>,
        result: LiveJoinResult,
        showMicUnmutedWhenAllowed: Boolean = false,
    ): LiveJoinResultPatch {
        val participant =
            liveParticipantWithExclusiveMedia(
                _joinResultParticipantForDisplay(
                    result.participant,
                    showMicUnmutedWhenAllowed = showMicUnmutedWhenAllowed,
                ),
            )
        val live = _replaceParticipantInLive(result.live, participant)
        return LiveJoinResultPatch(
            micMuted = participant.micMuted,
            headphonesMuted = participant.headphonesMuted,
            cameraOn = participant.cameraOn,
            screenSharing = participant.screenSharing,
            voiceBlocked = participant.voiceBlocked,
            live = live,
            rooms =
                patchRoomLiveCount(
                    rooms = rooms,
                    roomId = live.roomId,
                    live = live,
                ),
        )
    }

    private fun _joinResultParticipantForDisplay(
        participant: LiveParticipant,
        showMicUnmutedWhenAllowed: Boolean,
    ): LiveParticipant {
        if (!showMicUnmutedWhenAllowed ||
            participant.voiceBlocked ||
            participant.micBlocked ||
            !participant.micMuted
        ) {
            return participant
        }
        return participant.copyWith(micMuted = false)
    }

    private fun _replaceParticipantInLive(
        live: LiveState,
        participant: LiveParticipant,
    ): LiveState {
        var replaced = false
        var changed = false
        val participants =
            live.participants.map { item ->
                if (item.liveSessionId != participant.liveSessionId) return@map item
                replaced = true
                val merged = _mergeLiveParticipant(participant, fallback = item)
                if (item === merged) return@map item
                changed = true
                merged
            }
        if (!replaced || !changed) return live
        return LiveState(
            roomId = live.roomId,
            participantCount = live.participantCount,
            participants = participants,
            updatedAt = live.updatedAt,
        )
    }

    fun patchStateUpdate(
        live: LiveState?,
        participant: LiveParticipant,
    ): LiveStateUpdatePatch {
        val normalized = liveParticipantWithExclusiveMedia(participant)
        return LiveStateUpdatePatch(
            micMuted = normalized.micMuted,
            headphonesMuted = normalized.headphonesMuted,
            cameraOn = normalized.cameraOn,
            screenSharing = normalized.screenSharing,
            voiceBlocked = normalized.voiceBlocked,
            live = mergeParticipant(live, normalized),
        )
    }

    fun removeUserFromLive(
        live: LiveState?,
        rooms: List<RoomCard>,
        roomId: String,
        userId: String,
    ): LiveDeparturePatch {
        return LiveDeparturePatch(
            live =
                _removeUserFromLiveState(
                    live = live,
                    roomId = roomId,
                    userId = userId,
                ),
            rooms =
                _removeUserFromRoomLivePreview(
                    rooms = rooms,
                    roomId = roomId,
                    userId = userId,
                ),
        )
    }

    fun mergeParticipant(live: LiveState?, participant: LiveParticipant): LiveState? {
        if (live == null) return null
        var replaced = false
        val participants =
            live.participants.map { item ->
                if (item.liveSessionId != participant.liveSessionId) return@map item
                replaced = true
                _mergeLiveParticipant(participant, fallback = item)
            }
        return LiveState(
            roomId = live.roomId,
            participantCount =
                if (replaced) live.participantCount else live.participantCount + 1,
            participants =
                if (replaced) {
                    participants
                } else {
                    participants + liveParticipantWithExclusiveMedia(participant)
                },
            // A participant PATCH response does not carry a room-snapshot revision.
            // Keep the last server revision until the authoritative realtime/refresh
            // snapshot arrives. Stamping this with the client clock can make every
            // later server snapshot look stale after reconnect (especially with
            // clock skew), leaving remote mic/headphones state frozen.
            updatedAt = live.updatedAt,
        )
    }

    private fun _mergeLiveParticipant(
        participant: LiveParticipant,
        fallback: LiveParticipant,
    ): LiveParticipant {
        return liveParticipantWithExclusiveMedia(
            participant.copyWith(user = participant.user.mergeMissing(fallback.user)),
        )
    }

    fun patchModeratedParticipant(
        live: LiveState?,
        participant: LiveParticipant,
        action: LiveModerationAction,
    ): LiveState? {
        val moderated =
            when (action) {
                LiveModerationAction.muteMic ->
                    participant.copyWith(
                        micMuted = true,
                        micBlocked = true,
                        voiceBlocked = true,
                    )
                LiveModerationAction.blockVoice ->
                    participant.copyWith(
                        headphonesMuted = true,
                        headphonesBlocked = true,
                        headphonesListening = false,
                    )
                LiveModerationAction.restoreVoice ->
                    participant.copyWith(
                        micMuted = false,
                        micBlocked = false,
                        voiceBlocked = false,
                    )
                LiveModerationAction.restoreHeadphones ->
                    participant.copyWith(
                        headphonesMuted = false,
                        headphonesBlocked = false,
                        headphonesListening = true,
                    )
                LiveModerationAction.kick -> participant
            }
        if (action == LiveModerationAction.kick) return live
        return mergeParticipant(live, moderated)
    }

    private fun _removeUserFromLiveState(
        live: LiveState?,
        roomId: String,
        userId: String,
    ): LiveState? {
        if (live == null || live.roomId != roomId) return live
        val remaining =
            live.participants.filter { participant -> participant.user.id != userId }
        if (remaining.size == live.participants.size) return live
        return LiveState(
            roomId = live.roomId,
            participantCount = remaining.size,
            participants = remaining,
            updatedAt = live.updatedAt,
        )
    }

    /**
     * PORT: like the Dart constructor call, this does not copy
     * `gangBotAvailable` / `gangBotEnabled`, so the patched card falls back to
     * their defaults.
     */
    private fun _removeUserFromRoomLivePreview(
        rooms: List<RoomCard>,
        roomId: String,
        userId: String,
    ): List<RoomCard> {
        val idx = rooms.indexOfFirst { room -> room.id == roomId }
        if (idx < 0) return rooms

        val existing = rooms[idx]
        val remainingPreview =
            existing.liveAvatarPreview.filter { user -> user.id != userId }
        val nextCount =
            if (existing.liveParticipantCount > 0) existing.liveParticipantCount - 1 else 0
        if (remainingPreview.size == existing.liveAvatarPreview.size &&
            nextCount == existing.liveParticipantCount
        ) {
            return rooms
        }
        val next = rooms.toMutableList()
        next[idx] =
            RoomCard(
                id = existing.id,
                name = existing.name,
                rid = existing.rid,
                visibility = existing.visibility,
                remarkName = existing.remarkName,
                description = existing.description,
                notificationPolicy = existing.notificationPolicy,
                isPinned = existing.isPinned,
                avatarUrl = existing.avatarUrl,
                defaultAvatarKey = existing.defaultAvatarKey,
                memberCount = existing.memberCount,
                onlineMemberCount = existing.onlineMemberCount,
                liveParticipantCount = nextCount,
                liveAvatarPreview = remainingPreview,
                lastMessage = existing.lastMessage,
                unreadCount = existing.unreadCount,
                hasUnreadCount = existing.hasUnreadCount,
                unreadMentionCount = existing.unreadMentionCount,
                hasUnreadMentionCount = existing.hasUnreadMentionCount,
                hasPendingJoinRequests = existing.hasPendingJoinRequests,
                aiVoiceAnnouncementsEnabled = existing.aiVoiceAnnouncementsEnabled,
                updatedAt = existing.updatedAt,
            )
        return next
    }
}
