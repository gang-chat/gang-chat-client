package com.gangchat.client.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameNanos
import com.gangchat.client.app.LiveJoinPreviousRoomDisconnectedPatch
import com.gangchat.client.app.LiveJoinResultPatch
import com.gangchat.client.app.LiveJoinStatePatch
import com.gangchat.client.app.LiveLocalDeparturePatch
import com.gangchat.client.app.LiveModerationAction
import com.gangchat.client.app.LivePresenceAnnouncement
import com.gangchat.client.app.LivePresenceAnnouncementAction
import com.gangchat.client.app.LivePublishPermissionPatch
import com.gangchat.client.app.RoomMemberPermissionState
import com.gangchat.client.app.authoritativeLivePresenceChanges
import com.gangchat.client.app.authoritativeLivePresenceUserIds
import com.gangchat.client.app.canApplyPickedScreenShareSource
import com.gangchat.client.app.canPatchSelectedLiveState
import com.gangchat.client.app.isBenignGoneLiveStatePatch
import com.gangchat.client.app.joinedLiveRoomToDisconnectBeforeJoin
import com.gangchat.client.app.liveOutputMuteToggled
import com.gangchat.client.app.liveParticipantByUserId
import com.gangchat.client.app.livePresenceAnnouncementForUser
import com.gangchat.client.app.liveStateMissingConnectedParticipants
import com.gangchat.client.app.normalizedAudioVolume
import com.gangchat.client.app.rememberedAudioVolume
import com.gangchat.client.app.restoredAudioVolume
import com.gangchat.client.app.roomManagementPermissionState
import com.gangchat.client.app.roomMemberDisplayName
import com.gangchat.client.app.roomMemberPermissionState
import com.gangchat.client.app.shouldPatchEndedLocalScreenShare
import com.gangchat.client.app.shouldSpeakLivePresenceAnnouncement
import com.gangchat.client.app.shouldSyncLiveKitMicAfterServerPatch
import com.gangchat.client.app.userFacingErrorMessage
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.delayed
import com.gangchat.client.foundation.future
import com.gangchat.client.foundation.timeout
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.live.LiveParticipantDepartureKind
import com.gangchat.client.live.LivePresenceSound
import com.gangchat.client.live.LiveVideoTrack
import com.gangchat.client.live.supportsLocalScreenShare
import com.gangchat.client.protocol.ApiException
import com.gangchat.client.protocol.LiveJoinResult
import com.gangchat.client.protocol.LiveParticipant
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.DialogFrame
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.showFloatingErrorNotice
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.showDialog
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.yield

// Port of lib/src/home/home_shell_live_actions.dart.
//
// PORT: the desktop full-screen parts are dropped: Windows capture exclusion
// for a local screen-share preview, the Windows media-playback power request
// and the desktop window's full-screen mode (all no-ops on Android), and the
// desktop screen-source picker (Android asks through the MediaProjection
// prompt inside `LiveSession.setScreenShareEnabled`).

internal fun _HomeShellState._setLiveStageSelection(selection: LiveStageSelection?) {
    val roomId = _selectedServerId ?: return
    _setHomeState { _liveStageSelections[roomId] = selection }
    _syncWatchedLiveStageSelection(selection)
}

internal fun _HomeShellState._enterLiveFullScreen(track: LiveVideoTrack) {
    val generation = ++_liveFullScreenTransitionGeneration
    _queueLiveFullScreenTransition { _performEnterLiveFullScreen(track, generation) }
}

private suspend fun _HomeShellState._performEnterLiveFullScreen(track: LiveVideoTrack, generation: Int) {
    if (!mounted || generation != _liveFullScreenTransitionGeneration) return
    if (track.isLocal && track.isScreenShare && widget.androidSystemService.isSupported) {
        showFloatingErrorNotice(context, "Android 无法安全排除本机窗口，屏幕共享期间请使用小窗预览")
        return
    }

    _setHomeState { _fullScreenLiveTrack = track }
    if (!track.isLocal) {
        val liveSession = _liveSessionController
        if (track.isScreenShare) {
            unawaited { liveSession.setWatchedScreenShareIdentity(track.identity) }
        } else {
            unawaited { liveSession.setWatchedCameraIdentity(track.identity) }
        }
    }
    _setSystemFullScreen(true)
}

internal fun _HomeShellState._exitLiveFullScreen() {
    ++_liveFullScreenTransitionGeneration
    _setHomeState { _fullScreenLiveTrack = null }
    _syncWatchedLiveStageSelection(_liveStageSelections[_selectedServerId])
    _queueLiveFullScreenTransition { _performExitLiveFullScreen() }
}

private suspend fun _HomeShellState._performExitLiveFullScreen() {
    _setSystemFullScreen(false)
}

internal fun _HomeShellState._resetLiveFullScreenForLifecycle() {
    ++_liveFullScreenTransitionGeneration
    _fullScreenLiveTrack = null
    _queueLiveFullScreenTransition { _setSystemFullScreen(false) }
}

private fun _HomeShellState._queueLiveFullScreenTransition(transition: suspend () -> Unit) {
    val previous = _liveFullScreenTransitionTail
    _liveFullScreenTransitionTail =
        future {
            try {
                previous.await()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
            try {
                transition()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
        }
}

internal fun _HomeShellState._syncWatchedLiveStageSelection(selection: LiveStageSelection?) {
    var screenShareIdentity: String? = null
    var cameraIdentity: String? = null
    if (selection?.mode == LiveStageSelectionMode.track && selection.identity != _currentUser.id) {
        if (selection.isScreenShare == true) {
            screenShareIdentity = selection.identity
        } else {
            cameraIdentity = selection.identity
        }
    }
    val liveSession = _liveSessionController
    unawaited { liveSession.setWatchedScreenShareIdentity(screenShareIdentity) }
    unawaited { liveSession.setWatchedCameraIdentity(cameraIdentity) }

    val roomId = _joinedLiveRoomId ?: return
    val previousSync = _liveScreenViewSyncTail
    val targetIdentity = screenShareIdentity
    _liveScreenViewSyncTail =
        future {
            try {
                previousSync.await()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
            if (!mounted || _joinedLiveRoomId != roomId) return@future

            fun isStillCurrentTarget(): Boolean {
                val currentSelection = _liveStageSelections[roomId]
                val currentIdentity =
                    if (currentSelection?.mode == LiveStageSelectionMode.track &&
                        currentSelection.isScreenShare == true &&
                        currentSelection.identity != _currentUser.id
                    ) {
                        currentSelection.identity
                    } else {
                        null
                    }
                return currentIdentity == targetIdentity
            }

            for (attempt in 0 until 3) {
                if (!mounted || _joinedLiveRoomId != roomId || !isStillCurrentTarget()) {
                    return@future
                }
                try {
                    _liveController.updateMyLiveScreenView(roomId = roomId, broadcasterUserId = targetIdentity)
                    return@future
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    val shouldRetry =
                        targetIdentity != null &&
                            error is ApiException &&
                            error.code == "screen_share_not_active" &&
                            attempt < 2
                    if (!shouldRetry) return@future
                    delayed((120 * (attempt + 1)).milliseconds)
                }
            }
        }
}

/**
 * Dart's `_setSystemFullScreen`: the desktop window's full-screen state and
 * the Windows media-playback power request. Both are no-ops on Android, where
 * [LiveFullScreenStage] hides the system bars itself.
 */
@Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
private suspend fun _HomeShellState._setSystemFullScreen(fullScreen: Boolean) {}

internal fun _HomeShellState._resolveFullScreenLiveTrack(): LiveVideoTrack? {
    val selected = _fullScreenLiveTrack ?: return null
    for (track in _liveSessionController.videoTracks) {
        if (track.identity == selected.identity && track.isScreenShare == selected.isScreenShare) {
            return track
        }
    }
    return null
}

internal fun _HomeShellState._onLiveSessionChanged() {
    if (!mounted) return
    val joinedLiveRoomId = _joinedLiveRoomId
    val connectedParticipantIds = _liveSessionController.connectedParticipantIdentities
    if (joinedLiveRoomId != null &&
        joinedLiveRoomId == _selectedServerId &&
        liveStateMissingConnectedParticipants(
            _live ?: _selectedRoom?.live,
            connectedParticipantIds = connectedParticipantIds,
        )
    ) {
        unawaited { _syncLiveConnectedParticipants(joinedLiveRoomId) }
    }
    if (shouldPatchEndedLocalScreenShare(
            localScreenSharing = _screenSharing,
            sessionScreenSharing = _liveSessionController.isScreenSharing,
            joinedLiveRoomId = _joinedLiveRoomId,
            selectedRoomId = _selectedServerId,
        )
    ) {
        unawaited { _patchLiveState(screenSharing = false) }
    }
    if (_fullScreenLiveTrack != null && _resolveFullScreenLiveTrack() == null) {
        _exitLiveFullScreen()
    }
    _setHomeState {}
}

internal fun _HomeShellState._onLiveParticipantJoined(participantIdentity: String) {
    if (participantIdentity == _currentUser.id || !_joinedLivePresenceCueParticipantIds.add(participantIdentity)) {
        return
    }
    // A media join is safe to announce immediately. The cue identity set
    // deduplicates the authoritative snapshot that normally follows it.
    _playLivePresenceSound(
        LivePresenceSound.joined,
        participantIdentity = participantIdentity,
        announcementAction = LivePresenceAnnouncementAction.joined,
    )
}

@Suppress("UNUSED_PARAMETER", "unused")
internal fun _HomeShellState._onLiveParticipantLeft(
    participantIdentity: String,
    departureKind: LiveParticipantDepartureKind,
) {
    // A transient transport loss emits this callback immediately while the
    // server intentionally keeps the participant as `reconnecting` for its
    // recovery grace period. Removing the card or playing a leave cue here
    // causes the exact mismatch reported in production. Track/media cleanup
    // remains inside LiveSession; roster and cue changes follow the next
    // authoritative server snapshot instead.
}

private fun _HomeShellState._seedJoinedLiveAuthoritativePresence(live: LiveState) {
    _joinedLiveAuthoritativeParticipantIds = authoritativeLivePresenceUserIds(live)
    _joinedLiveAuthoritativeSnapshotAt = live.updatedAt
    _joinedLivePresenceCueParticipantIds.clear()
}

private fun _HomeShellState._clearJoinedLiveAuthoritativePresence() {
    _joinedLiveAuthoritativeParticipantIds = LinkedHashSet()
    _joinedLivePresenceCueParticipantIds.clear()
    _joinedLiveAuthoritativeSnapshotAt = null
}

internal fun _HomeShellState._applyJoinedLiveAuthoritativeSnapshot(
    live: LiveState,
    eventType: String,
    eventData: JsonMap = emptyMap(),
) {
    if (live.roomId != _joinedLiveRoomId) return
    val previousUpdatedAt = _joinedLiveAuthoritativeSnapshotAt
    if (previousUpdatedAt != null && live.updatedAt.isBefore(previousUpdatedAt)) {
        return
    }
    val next = authoritativeLivePresenceUserIds(live)
    val changes = authoritativeLivePresenceChanges(before = _joinedLiveAuthoritativeParticipantIds, after = next)
    _joinedLiveAuthoritativeParticipantIds = next
    _joinedLiveAuthoritativeSnapshotAt = live.updatedAt

    for (identity in changes.joined) {
        if (identity == _currentUser.id) continue
        if (!_joinedLivePresenceCueParticipantIds.add(identity)) continue
        _playLivePresenceSound(
            LivePresenceSound.joined,
            participantIdentity = identity,
            announcementAction = LivePresenceAnnouncementAction.joined,
        )
    }
    val removedUserId = eventData["user_id"]?.toString()
    val removedByModerator = eventType == "live_participant_moderated" && eventData["action"] == "kick"
    for (identity in changes.left) {
        if (identity == _currentUser.id) continue
        _joinedLivePresenceCueParticipantIds.remove(identity)
        _playLivePresenceSound(
            LivePresenceSound.left,
            participantIdentity = identity,
            announcementAction =
                if (removedByModerator && removedUserId == identity) {
                    LivePresenceAnnouncementAction.removed
                } else {
                    LivePresenceAnnouncementAction.left
                },
        )
    }

    // Keep a remote stage selection through a transient media reconnect so it
    // can recover in place. Clear it only when the server has confirmed that
    // participant is no longer present.
    val roomId = _joinedLiveRoomId
    val selection = if (roomId == null) null else _liveStageSelections[roomId]
    val selectedParticipantLeft =
        selection?.mode == LiveStageSelectionMode.track && changes.left.contains(selection.identity)
    if (roomId != null && selectedParticipantLeft) {
        _liveStageSelections[roomId] = LiveStageSelection.none()
        _syncWatchedLiveStageSelection(LiveStageSelection.none())
    }
    val fullScreenTrack = _fullScreenLiveTrack
    if (fullScreenTrack != null && !fullScreenTrack.isLocal && changes.left.contains(fullScreenTrack.identity)) {
        _exitLiveFullScreen()
    }
}

internal fun _HomeShellState._playLivePresenceSound(
    sound: LivePresenceSound,
    participantIdentity: String? = null,
    announcementAction: LivePresenceAnnouncementAction? = null,
    continueAfterRoomExit: Boolean = false,
) {
    val roomId = _joinedLiveRoomId ?: return
    val previous = _livePresenceEventTail
    _livePresenceEventTail =
        future {
            try {
                // Dart chained the cue with `.then`, which always runs later.
                yield()
                previous.await()
                _queueLivePresenceAudio(
                    sound,
                    roomId = roomId,
                    participantIdentity = participantIdentity,
                    announcementAction = announcementAction,
                    continueAfterRoomExit = continueAfterRoomExit,
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
        }
}

private suspend fun _HomeShellState._queueLivePresenceAudio(
    sound: LivePresenceSound,
    roomId: String,
    participantIdentity: String?,
    announcementAction: LivePresenceAnnouncementAction?,
    continueAfterRoomExit: Boolean,
) {
    if (!continueAfterRoomExit && _joinedLiveRoomId != roomId) return
    val volume = if (_headphonesMuted) 0.0 else normalizedAudioVolume(_liveSessionController.outputVolume)
    var announcement: LivePresenceAnnouncement? = null
    // This personal preference gates only synthesized speech. The coordinator
    // below always receives the join/leave cue, even when announcements are off.
    val shouldAnnounce =
        shouldSpeakLivePresenceAnnouncement(
            enabled = _joinedLivePersonalAiVoiceAnnouncementsEnabled,
            participantIdentity = participantIdentity,
            currentUserIdentity = _currentUser.id,
        )
    if (shouldAnnounce && announcementAction != null) {
        val user = _resolveJoinedLiveParticipantUser(roomId, participantIdentity!!)
        if (user != null && _joinedLiveRoomId == roomId) {
            announcement = livePresenceAnnouncementForUser(user = user, action = announcementAction)
        }
    }
    _livePresenceAudioCoordinator.play(sound, volume = volume, announcement = announcement).await()
}

private suspend fun _HomeShellState._resolveJoinedLiveParticipantUser(
    roomId: String,
    participantIdentity: String,
): UserSummary? {
    val cached = _joinedLiveParticipantUsers[participantIdentity]
    if (cached != null) return cached
    return try {
        val live = _roomsController.getLiveState(roomId)
        if (_joinedLiveRoomId != roomId) return null
        _cacheJoinedLiveParticipantUsers(live)
        _joinedLiveParticipantUsers[participantIdentity]
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        null
    }
}

internal fun _HomeShellState._cacheJoinedLiveParticipantUsers(live: LiveState) {
    if (live.roomId != _joinedLiveRoomId) return
    for (participant in live.participants) {
        _joinedLiveParticipantUsers[participant.user.id] = participant.user
    }
}

private suspend fun _HomeShellState._syncLiveConnectedParticipants(roomId: String) {
    if (_syncingLiveConnectedParticipants) return
    _syncingLiveConnectedParticipants = true
    try {
        _refreshLiveSilently(roomId)
    } finally {
        if (mounted) _syncingLiveConnectedParticipants = false
    }
}

internal fun _HomeShellState._onForciblyRemovedFromLive() {
    if (!mounted) return
    _playLivePresenceSound(LivePresenceSound.left, continueAfterRoomExit = true)
    val patch =
        _liveController.patchLocalDeparture(
            live = _live,
            rooms = _servers,
            joinedLiveRoomId = _joinedLiveRoomId,
            userId = _currentUser.id,
            joiningLive = false,
        )
    _setHomeState {
        _leavingLive = false
        _applyLiveLocalDeparturePatch(patch)
    }
    _syncWatchedLiveStageSelection(null)
    val liveSession = _liveSessionController
    unawaited { liveSession.disconnect() }
}

internal fun _HomeShellState._onUnexpectedlyDisconnectedFromLive() {
    if (!mounted) return
    val roomId = _joinedLiveRoomId ?: return
    unawaited { _restoreLiveAfterRealtimeReconnect(roomId) }
}

internal fun _HomeShellState._onPublishPermissionChanged(canPublish: Boolean) {
    if (!mounted) return
    val patch = _liveController.patchPublishPermission(canPublish = canPublish, micMuted = _micMuted)
    _setHomeState { _applyLivePublishPermissionPatch(patch) }
}

private fun _HomeShellState._applyLiveJoinStatePatch(patch: LiveJoinStatePatch) {
    _joinedLiveRoomId = patch.joinedLiveRoomId
    _joiningLive = patch.joiningLive
    _contentMode = if (patch.livePanelOpen) _ContentMode.live else _contentMode
    _roomError = patch.error
    if (patch.joinedLiveRoomId == null) {
        _joinedLivePersonalAiVoiceAnnouncementsEnabled = false
        _joinedLiveParticipantUsers.clear()
        _clearJoinedLiveAuthoritativePresence()
    }
}

private fun _HomeShellState._applyLiveJoinPreviousRoomDisconnectedPatch(patch: LiveJoinPreviousRoomDisconnectedPatch) {
    val departedRoomId = _joinedLiveRoomId
    _live = patch.live
    _servers = _roomsController.patchRoomCardsRefreshed(rooms = patch.rooms).rooms
    _joinedLiveRoomId = patch.joinedLiveRoomId
    _joiningLive = patch.joiningLive
    _contentMode = if (patch.livePanelOpen) _ContentMode.live else _contentMode
    _roomError = patch.error
    if (departedRoomId != null && departedRoomId != patch.joinedLiveRoomId) {
        _liveStageSelections.remove(departedRoomId)
        _clearJoinedLiveAuthoritativePresence()
    }
}

private fun _HomeShellState._applyLiveLocalDeparturePatch(patch: LiveLocalDeparturePatch) {
    val departedRoomId = _joinedLiveRoomId
    _live = patch.live
    _servers = _roomsController.patchRoomCardsRefreshed(rooms = patch.rooms).rooms
    _joinedLiveRoomId = patch.joinedLiveRoomId
    _joiningLive = patch.joiningLive
    _cameraOn = patch.cameraOn
    _screenSharing = patch.screenSharing
    _voiceBlocked = patch.voiceBlocked
    if (patch.joinedLiveRoomId == null) {
        // A selected remote camera/share belongs to this voice participation,
        // not to the room UI. Forget it when that participation ends so a later
        // manual join never resumes watching stale media. Realtime reconnects do
        // not pass through this departure patch and therefore keep watching.
        if (departedRoomId != null) {
            _liveStageSelections.remove(departedRoomId)
        }
        _joinedLivePersonalAiVoiceAnnouncementsEnabled = false
        _joinedLiveParticipantUsers.clear()
        _clearJoinedLiveAuthoritativePresence()
    }
}

private fun _HomeShellState._applyLivePublishPermissionPatch(patch: LivePublishPermissionPatch) {
    _voiceBlocked = patch.voiceBlocked
    _micMuted = patch.micMuted
}

private fun _HomeShellState._applyLiveJoinResultPatch(patch: LiveJoinResultPatch) {
    _micMuted = patch.micMuted
    _headphonesMuted = patch.headphonesMuted
    _cameraOn = patch.cameraOn
    _screenSharing = patch.screenSharing
    _voiceBlocked = patch.voiceBlocked
    _live = patch.live
    _servers = _roomsController.patchRoomCardsRefreshed(rooms = patch.rooms).rooms
}

private fun _HomeShellState._withCurrentRoomLiveDisplayNameInJoinResult(result: LiveJoinResult, roomId: String): LiveJoinResult {
    val participant = _withCurrentRoomLiveDisplayName(result.participant, roomId)
    val live = _withCurrentRoomLiveDisplayNameInLive(result.live, roomId)
    if (participant === result.participant && live === result.live) {
        return result
    }
    return LiveJoinResult(liveKit = result.liveKit, participant = participant, live = live)
}

private fun _HomeShellState._withCurrentRoomLiveDisplayNameInLive(live: LiveState, roomId: String): LiveState {
    if (live.roomId != roomId) return live
    var changed = false
    val participants =
        live.participants.map { participant ->
            _withCurrentRoomLiveDisplayName(participant, roomId, changed = { changed = true })
        }
    if (!changed) return live
    return LiveState(
        roomId = live.roomId,
        participantCount = live.participantCount,
        participants = participants,
        updatedAt = live.updatedAt,
    )
}

private fun _HomeShellState._withCurrentRoomLiveDisplayName(
    participant: LiveParticipant,
    roomId: String,
    changed: (() -> Unit)? = null,
): LiveParticipant {
    if (participant.user.id != _currentUser.id) return participant
    val displayName = if (_selectedRoom?.id == roomId) _selectedRoom?.personalProfile?.displayName?.trim() else null
    if (displayName == null || displayName.isEmpty()) return participant
    if (participant.user.roomDisplayName?.trim() == displayName) {
        return participant
    }
    changed?.invoke()
    return participant.copyWith(user = participant.user.copyWith(roomDisplayName = displayName))
}

private fun _HomeShellState._setMicMutedLocally(muted: Boolean, syncVolume: Boolean = true) {
    val liveSession = _liveSessionController
    if (syncVolume) {
        val volume = if (muted) 0.0 else _restoredInputVolume()
        if (muted) {
            _rememberInputVolume(liveSession.inputVolume)
        }
        unawaited { liveSession.setInputVolume(volume, persist = false) }
    }
    unawaited { liveSession.setMicMuted(muted) }
    if (_micMuted != muted) {
        _setHomeState { _micMuted = muted }
    }
}

private fun _HomeShellState._syncLocalAudioFromParticipant(participant: LiveParticipant) {
    val authoritativeMicMuted = participant.micMuted || participant.micBlocked || participant.voiceBlocked
    val authoritativeHeadphonesMuted = participant.headphonesMuted || participant.headphonesBlocked
    if (_micMuted != authoritativeMicMuted) {
        _setMicMutedLocally(authoritativeMicMuted)
    }
    if (_headphonesMuted != authoritativeHeadphonesMuted) {
        _setHeadphonesMuted(authoritativeHeadphonesMuted, patchState = false, coupleMic = false)
    }
}

internal fun _HomeShellState._syncJoinedLiveAudioFromSnapshot(live: LiveState) {
    if (!_liveStateRequests.isIdle || _joinedLiveRoomId != live.roomId) return
    for (participant in live.participants) {
        if (participant.user.id != _currentUser.id) continue
        _syncLocalAudioFromParticipant(participant)
        return
    }
}

internal fun _HomeShellState._toggleMicMute() {
    val muted = !_micMuted
    val unmuteHeadphones = !muted && _headphonesMuted
    _setMicMutedLocally(muted)
    if (unmuteHeadphones) {
        _setHeadphonesMuted(false, patchState = false)
    }
    unawaited {
        _patchLiveState(
            micMuted = muted,
            headphonesMuted = if (unmuteHeadphones) false else null,
            syncLiveKitMic = false,
        )
    }
}

internal fun _HomeShellState._toggleHeadphonesMute() {
    val patch = liveOutputMuteToggled(headphonesMuted = _headphonesMuted)
    _setHeadphonesMuted(patch.headphonesMuted)
}

private fun _HomeShellState._setHeadphonesMuted(
    muted: Boolean,
    syncVolume: Boolean = true,
    patchState: Boolean = true,
    coupleMic: Boolean = true,
) {
    val headphonesChanged = _headphonesMuted != muted
    val muteMic = coupleMic && muted && !_micMuted
    if (!headphonesChanged && !muteMic) return

    _setHomeState {
        if (muteMic) {
            _micMuted = true
        }
        if (headphonesChanged) {
            _headphonesMuted = muted
        }
    }

    val liveSession = _liveSessionController
    if (muteMic) {
        _rememberInputVolume(liveSession.inputVolume)
        unawaited { liveSession.setInputVolume(0.0, persist = false) }
        unawaited { liveSession.setMicMuted(true) }
    }
    if (headphonesChanged && syncVolume) {
        val volume = if (muted) 0.0 else _restoredOutputVolume()
        if (muted) {
            _rememberOutputVolume(liveSession.outputVolume)
        }
        unawaited { liveSession.setOutputVolume(volume, persist = false) }
    }
    if (headphonesChanged) {
        unawaited { liveSession.setOutputMuted(muted) }
    }
    // Report the headphone state to the server so other participants see it on
    // the live snapshot. Best-effort: a failure here doesn't undo the local
    // mute, which already took effect on the LiveKit session above.
    if (patchState) {
        val micMutedPatch = if (muteMic || (!muted && _micMuted)) true else null
        unawaited {
            _patchLiveState(
                micMuted = micMutedPatch,
                headphonesMuted = if (headphonesChanged) muted else null,
                syncLiveKitMic = micMutedPatch == null,
            )
        }
    }
}

internal fun _HomeShellState._changeInputVolume(volume: Double) {
    val normalized = normalizedAudioVolume(volume)
    val liveSession = _liveSessionController
    if (normalized == 0.0) {
        _rememberInputVolume(liveSession.inputVolume)
    } else {
        _rememberInputVolume(normalized)
    }
    unawaited { liveSession.setInputVolume(normalized) }
    val muted = normalized == 0.0
    if (!muted && _voiceBlocked) return
    val micChanged = muted != _micMuted
    if (micChanged) {
        _setMicMutedLocally(muted, syncVolume = false)
    }
    val unmuteHeadphones = !muted && _headphonesMuted
    if (unmuteHeadphones) {
        _setHeadphonesMuted(false, patchState = false)
    }
    if (!micChanged && !unmuteHeadphones) return
    unawaited {
        _patchLiveState(
            micMuted = if (micChanged) muted else null,
            headphonesMuted = if (unmuteHeadphones) false else null,
            syncLiveKitMic = false,
        )
    }
}

internal fun _HomeShellState._changeOutputVolume(volume: Double) {
    val normalized = normalizedAudioVolume(volume)
    val liveSession = _liveSessionController
    if (normalized == 0.0) {
        _rememberOutputVolume(liveSession.outputVolume)
    } else {
        _rememberOutputVolume(normalized)
    }
    unawaited { liveSession.setOutputVolume(normalized) }
    _setHeadphonesMuted(normalized == 0.0, syncVolume = false)
}

internal fun _HomeShellState._changeScreenShareVolume(volume: Double) {
    val normalized = normalizedAudioVolume(volume)
    if (normalized > 0) {
        _lastScreenShareVolumeBeforeMute = rememberedAudioVolume(normalized)
    }
    val liveSession = _liveSessionController
    _runLiveAudioStateChange(future { liveSession.setScreenShareVolume(normalized) })
}

internal fun _HomeShellState._toggleScreenShareAudioMute() {
    val current = normalizedAudioVolume(_liveSessionController.screenShareVolume)
    if (current <= 0) {
        _changeScreenShareVolume(restoredAudioVolume(_lastScreenShareVolumeBeforeMute))
        return
    }
    _lastScreenShareVolumeBeforeMute = rememberedAudioVolume(current)
    _changeScreenShareVolume(0.0)
}

internal fun _HomeShellState._participantVoiceVolume(userId: String): Double {
    unawaited { _restoreParticipantVoiceVolume(userId) }
    return _liveSessionController.participantVoiceVolume(userId)
}

internal fun _HomeShellState._changeParticipantVoiceVolume(userId: String, volume: Double) {
    val liveSession = _liveSessionController
    _runLiveAudioStateChange(future { liveSession.setParticipantVoiceVolume(userId, volume) })
}

internal fun _HomeShellState._toggleParticipantVoiceMute(userId: String) {
    val liveSession = _liveSessionController
    _runLiveAudioStateChange(future { liveSession.toggleParticipantVoiceMuted(userId) })
}

private suspend fun _HomeShellState._restoreParticipantVoiceVolume(userId: String) {
    val changed = _liveSessionController.restoreParticipantVoiceVolume(userId)
    if (!changed || !mounted) return
    _setHomeState {}
}

private fun _HomeShellState._runLiveAudioStateChange(change: Deferred<Unit>) {
    // LiveSession updates its normalized volume before applying it to tracks.
    // Rebuild now so taps and slider changes are reflected immediately, then
    // rebuild once more after the asynchronous device/persistence work ends.
    if (mounted) _setHomeState {}
    unawaited { _finishLiveAudioStateChange(change) }
}

private suspend fun _HomeShellState._finishLiveAudioStateChange(change: Deferred<Unit>) {
    change.await()
    if (!mounted) return
    _setHomeState {}
}

internal fun _HomeShellState._canModerateLiveParticipant(participant: LiveParticipant): Boolean {
    val room = _selectedRoom
    if (room == null ||
        _busyLiveMemberRemovalIds.contains(participant.user.id) ||
        _busyLiveMemberModerationIds.contains(participant.user.id)
    ) {
        return false
    }
    return _liveParticipantPermission(participant = participant, room = room).canRemoveMember
}

internal fun _HomeShellState._canRemoveLiveParticipant(participant: LiveParticipant): Boolean {
    val room = _selectedRoom
    if (room == null || _busyLiveMemberRemovalIds.contains(participant.user.id)) {
        return false
    }
    return _liveParticipantPermission(participant = participant, room = room).canRemoveMember
}

private fun _HomeShellState._liveParticipantPermission(participant: LiveParticipant, room: RoomDetail): RoomMemberPermissionState {
    val managementPermission = roomManagementPermissionState(room = room, currentUser = _currentUser)
    return roomMemberPermissionState(
        member = _roomMemberFromLiveParticipant(participant, room),
        currentUser = _currentUser,
        canEditCreatorOnly = managementPermission.canEditCreatorOnly,
        canManageMembers = room.isAdmin || _currentUser.isSuperuser,
        ownerUserId = room.createdBy?.id,
    )
}

internal suspend fun _HomeShellState._toggleLiveParticipantMicModeration(participant: LiveParticipant) {
    val blocked = participant.micBlocked || participant.voiceBlocked
    val action = if (blocked) LiveModerationAction.restoreVoice else LiveModerationAction.muteMic
    _moderateLiveParticipant(participant, action)
}

internal suspend fun _HomeShellState._toggleLiveParticipantHeadphonesModeration(participant: LiveParticipant) {
    val action =
        if (participant.headphonesBlocked) LiveModerationAction.restoreHeadphones else LiveModerationAction.blockVoice
    _moderateLiveParticipant(participant, action)
}

private suspend fun _HomeShellState._moderateLiveParticipant(participant: LiveParticipant, action: LiveModerationAction) {
    val room = _selectedRoom
    if (room == null || !_canModerateLiveParticipant(participant)) return
    val member = _roomMemberFromLiveParticipant(participant, room)
    val confirmed =
        showDialog<Boolean>(context) { dialogContext ->
            _LiveMemberModerationConfirmDialog(dialogContext, member = member, action = action)
        }
    if (confirmed != true || !mounted) return
    val userId = participant.user.id
    if (_busyLiveMemberModerationIds.contains(userId)) return
    _setHomeState {
        _busyLiveMemberModerationIds.add(userId)
        _roomError = null
    }
    try {
        _liveController.moderateParticipant(roomId = room.id, userId = userId, action = action)
        if (!mounted) return
        val live = _liveController.patchModeratedParticipant(live = _live, participant = participant, action = action)
        _setHomeState {
            _busyLiveMemberModerationIds.remove(userId)
            _live = live
            _roomError = null
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted) return
        _setHomeState {
            _busyLiveMemberModerationIds.remove(userId)
            _roomError = userFacingErrorMessage(error)
        }
    }
}

internal suspend fun _HomeShellState._removeLiveParticipant(participant: LiveParticipant) {
    val room = _selectedRoom
    if (room == null || !_canRemoveLiveParticipant(participant)) return
    val member = _roomMemberFromLiveParticipant(participant, room)
    val confirmed =
        showDialog<Boolean>(context) { dialogContext -> _LiveMemberRemoveConfirmDialog(dialogContext, member = member) }
    if (confirmed != true || !mounted) return
    val userId = participant.user.id
    if (_busyLiveMemberRemovalIds.contains(userId)) return
    _setHomeState {
        _busyLiveMemberRemovalIds.add(userId)
        _roomError = null
    }
    try {
        _liveController.kickParticipant(roomId = room.id, userId = userId)
        if (!mounted) return
        val patch = _liveController.removeUserFromLive(live = _live, rooms = _servers, roomId = room.id, userId = userId)
        val authoritativeIds = LinkedHashSet(_joinedLiveAuthoritativeParticipantIds)
        val wasAuthoritativelyPresent = authoritativeIds.remove(userId)
        _joinedLiveAuthoritativeParticipantIds = authoritativeIds
        _joinedLivePresenceCueParticipantIds.remove(userId)
        if (wasAuthoritativelyPresent) {
            _playLivePresenceSound(
                LivePresenceSound.left,
                participantIdentity = userId,
                announcementAction = LivePresenceAnnouncementAction.removed,
            )
        }
        _setHomeState {
            _busyLiveMemberRemovalIds.remove(userId)
            _live = patch.live
            _servers = patch.rooms
            _roomError = null
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted) return
        _setHomeState {
            _busyLiveMemberRemovalIds.remove(userId)
            _roomError = userFacingErrorMessage(error)
        }
    }
}

private fun _roomMemberFromLiveParticipant(participant: LiveParticipant, room: RoomDetail): RoomMember {
    val role = participant.user.roomRole ?: if (participant.user.id == room.createdBy?.id) "owner" else "member"
    return RoomMember(
        user = participant.user,
        role = role,
        joinedAt = participant.joinedAt,
        roomDisplayName = participant.user.roomDisplayName,
    )
}

internal fun _HomeShellState._syncSettingsAudioVolumeToLive(kind: String, volume: Double) {
    if (kind == "audioinput") {
        _changeInputVolume(volume)
        return
    }
    if (kind == "audiooutput") {
        _changeOutputVolume(volume)
    }
}

private fun _HomeShellState._rememberInputVolume(volume: Double) {
    _lastInputVolumeBeforeMute = rememberedAudioVolume(volume)
}

private fun _HomeShellState._rememberOutputVolume(volume: Double) {
    _lastOutputVolumeBeforeMute = rememberedAudioVolume(volume)
}

internal fun _HomeShellState._restoredInputVolume(): Double = restoredAudioVolume(_lastInputVolumeBeforeMute)

internal fun _HomeShellState._restoredOutputVolume(): Double = restoredAudioVolume(_lastOutputVolumeBeforeMute)

internal suspend fun _HomeShellState._joinLive(source: String) {
    val room = _selectedRoom
    if (room == null || _joiningLive || _leavingLive) return
    // Opening a room normally starts this fetch already. Retrying at the live
    // lifecycle boundary covers startup/token-refresh races without delaying
    // the voice join itself.
    _ensureMusicBoxLoaded(room.id)
    val desiredMicMuted = _micMuted
    val desiredHeadphonesMuted = _headphonesMuted

    if (widget.androidSystemService.isSupported) {
        try {
            widget.androidSystemService.requestBluetoothConnectPermission()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Speaker/earpiece routing remains usable when nearby-device access is
            // denied or unavailable.
        }
        if (!mounted) return
    }

    _setHomeState { _applyLiveJoinStatePatch(_liveController.patchJoinStarted(joinedLiveRoomId = _joinedLiveRoomId)) }

    val previousLiveRoomId = joinedLiveRoomToDisconnectBeforeJoin(joinedLiveRoomId = _joinedLiveRoomId, targetRoomId = room.id)
    if (previousLiveRoomId != null) {
        _playLivePresenceSound(LivePresenceSound.left, continueAfterRoomExit = true)
        _notifyLiveLeft(previousLiveRoomId)
        _liveSessionController.disconnect()
        if (mounted) {
            _setHomeState {
                _applyLiveJoinPreviousRoomDisconnectedPatch(
                    _liveController.patchJoinPreviousRoomDisconnected(
                        live = _live,
                        rooms = _servers,
                        previousRoomId = previousLiveRoomId,
                        userId = _currentUser.id,
                        livePanelOpen = true,
                        error = _roomError,
                    ),
                )
            }
            _syncWatchedLiveStageSelection(null)
        }
    }

    try {
        val result =
            _liveController.joinLive(
                roomId = room.id,
                source = source,
                micMuted = if (_voiceBlocked) true else desiredMicMuted,
                headphonesMuted = desiredHeadphonesMuted,
            )
        if (!mounted) return
        val displayResult = _withCurrentRoomLiveDisplayNameInJoinResult(result, room.id)
        val joinPatch = _liveController.patchJoinResult(rooms = _servers, result = displayResult)
        _setHomeState {
            _applyLiveJoinResultPatch(joinPatch)
            _micMuted = if (joinPatch.voiceBlocked) true else desiredMicMuted
            _headphonesMuted = desiredHeadphonesMuted
            _joinedLivePersonalAiVoiceAnnouncementsEnabled = room.aiVoiceAnnouncementsEnabled
            _joinedLiveParticipantUsers.clear()
            for (participant in displayResult.live.participants) {
                _joinedLiveParticipantUsers[participant.user.id] = participant.user
            }
            _seedJoinedLiveAuthoritativePresence(displayResult.live)
        }
        try {
            _liveSessionController.connectWithRetry(
                displayResult,
                isCancelled = { !mounted },
                initialMicMuted = if (_voiceBlocked) true else desiredMicMuted,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (mounted) {
                _setHomeState { _roomError = userFacingErrorMessage(error) }
            }
            return
        }
        if (!mounted) return
        _setHomeState {
            _applyLiveJoinStatePatch(
                _liveController.patchJoinConnected(roomId = room.id, livePanelOpen = true, error = _roomError),
            )
        }
        _playLivePresenceSound(LivePresenceSound.joined)
        // Publish the user's pre-join audio choices and ready state atomically.
        // This also restores the last mic/headphones choice after leaving and
        // prevents the server's initial joining placeholder from leaking into
        // the visible roster.
        _restoreLiveParticipantState(
            roomId = room.id,
            micMuted = if (_voiceBlocked) true else desiredMicMuted,
            headphonesMuted = desiredHeadphonesMuted,
            cameraOn = false,
            cameraMirrored = false,
            screenSharing = false,
        )
        // Only publish the viewer relation after this participant is online.
        // Otherwise the screen-view snapshot can race the ready-state snapshot
        // and leave every client displaying the viewer as still "joining".
        _syncWatchedLiveStageSelection(_liveStageSelections[room.id])
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (mounted) {
            _setHomeState { _roomError = userFacingErrorMessage(error) }
        }
    } finally {
        if (mounted) {
            _setHomeState {
                _applyLiveJoinStatePatch(
                    _liveController.patchJoinFinished(
                        joinedLiveRoomId = _joinedLiveRoomId,
                        livePanelOpen = true,
                        error = _roomError,
                    ),
                )
            }
        }
    }
}

internal suspend fun _HomeShellState._restoreLiveAfterRealtimeReconnect(roomId: String) {
    if (_joiningLive || _joinedLiveRoomId != roomId) return
    _ensureMusicBoxLoaded(roomId)
    if (_liveSessionController.isAttachedToRoom(roomId)) {
        _refreshLiveSilently(roomId)
        return
    }

    val previousMicMuted = _micMuted
    val previousHeadphonesMuted = _headphonesMuted
    val previousCameraOn = _cameraOn
    val previousCameraMirrored = liveParticipantByUserId(_live ?: _selectedRoom?.live, _currentUser.id)?.cameraMirrored ?: false
    val previousScreenSharing = _screenSharing
    _setHomeState {
        _joiningLive = true
        _roomError = null
    }

    try {
        val result =
            _liveController.joinLive(
                roomId = roomId,
                source = "reconnect",
                micMuted = previousMicMuted,
                headphonesMuted = previousHeadphonesMuted,
            )
        if (!mounted || _joinedLiveRoomId != roomId) return

        val displayResult = _withCurrentRoomLiveDisplayNameInJoinResult(result, roomId)
        val joinPatch = _liveController.patchJoinResult(rooms = _servers, result = displayResult)
        _setHomeState {
            _micMuted = joinPatch.micMuted
            _headphonesMuted = joinPatch.headphonesMuted
            _cameraOn = joinPatch.cameraOn
            _screenSharing = joinPatch.screenSharing
            _voiceBlocked = joinPatch.voiceBlocked
            _servers = joinPatch.rooms
            if (_selectedServerId == result.live.roomId) {
                _live = joinPatch.live
            }
        }

        try {
            _liveSessionController.connectWithRetry(
                result,
                isCancelled = { !mounted || _joinedLiveRoomId != roomId },
                initialMicMuted = if (joinPatch.voiceBlocked) true else previousMicMuted,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (mounted) {
                _setHomeState { _roomError = userFacingErrorMessage(error) }
            }
            return
        }
        if (!mounted || _joinedLiveRoomId != roomId) return

        val canPublish = !joinPatch.voiceBlocked
        _restoreLiveParticipantState(
            roomId = roomId,
            micMuted = if (canPublish) previousMicMuted else true,
            headphonesMuted = previousHeadphonesMuted,
            cameraOn = canPublish && previousCameraOn,
            cameraMirrored = previousCameraMirrored,
            screenSharing = canPublish && previousScreenSharing && _liveSessionController.isScreenSharing,
        )
        _syncWatchedLiveStageSelection(_liveStageSelections[roomId])
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (mounted) {
            _setHomeState { _roomError = userFacingErrorMessage(error) }
        }
    } finally {
        if (mounted) _setHomeState { _joiningLive = false }
    }
}

private suspend fun _HomeShellState._restoreLiveParticipantState(
    roomId: String,
    micMuted: Boolean,
    headphonesMuted: Boolean,
    cameraOn: Boolean,
    cameraMirrored: Boolean,
    screenSharing: Boolean,
) {
    var restoredCameraOn = cameraOn
    if (headphonesMuted) {
        _ignoreErrors { _liveSessionController.setOutputMuted(true) }
    }
    if (restoredCameraOn) {
        try {
            _liveSessionController.setCameraEnabled(true)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            restoredCameraOn = false
            if (mounted) {
                _setHomeState { _roomError = userFacingErrorMessage(error) }
            }
        }
    }
    if (!screenSharing && _liveSessionController.isScreenSharing) {
        _ignoreErrors { _stopLocalScreenShareSafely() }
    }

    _ignoreErrors { _liveSessionController.setMicMuted(micMuted) }
    val effectiveMicMuted = if (micMuted == false) _liveSessionController.localMicMuted else micMuted
    try {
        val participant =
            _liveStateRequests.run {
                _liveController.updateMyState(
                    roomId = roomId,
                    micMuted = effectiveMicMuted,
                    headphonesMuted = headphonesMuted,
                    cameraOn = restoredCameraOn,
                    cameraMirrored = cameraMirrored,
                    screenSharing = screenSharing,
                    connectionState = "online",
                )
            }.await()
        val displayParticipant = _withCurrentRoomLiveDisplayName(participant, roomId)
        if (shouldSyncLiveKitMicAfterServerPatch(
                requestedMicMuted = effectiveMicMuted,
                serverMicMuted = displayParticipant.micMuted,
            )
        ) {
            _ignoreErrors { _liveSessionController.setMicMuted(displayParticipant.micMuted) }
        }
        if (!mounted || _joinedLiveRoomId != roomId || !_liveStateRequests.isIdle) {
            return
        }
        val updateSelectedLive = _selectedServerId == roomId
        val patch = _liveController.patchStateUpdate(live = if (updateSelectedLive) _live else null, participant = displayParticipant)
        _syncLocalAudioFromParticipant(displayParticipant)
        _setHomeState {
            _micMuted = patch.micMuted
            _headphonesMuted = patch.headphonesMuted
            _cameraOn = patch.cameraOn
            _screenSharing = patch.screenSharing
            _voiceBlocked = patch.voiceBlocked
            if (updateSelectedLive) _live = patch.live
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted || isBenignGoneLiveStatePatch(error)) return
        _setHomeState { _roomError = userFacingErrorMessage(error) }
    }
}

internal suspend fun _HomeShellState._leaveLive() {
    val roomId = _joinedLiveRoomId
    if (roomId == null || _leavingLive) return
    _playLivePresenceSound(LivePresenceSound.left, continueAfterRoomExit = true)
    val shouldKeepLivePanelOpen = _contentMode == _ContentMode.live
    val patch =
        _liveController.patchLocalDeparture(
            live = _live,
            rooms = _servers,
            joinedLiveRoomId = roomId,
            userId = _currentUser.id,
            joiningLive = false,
        )
    _setHomeState {
        _leavingLive = true
        _applyLiveLocalDeparturePatch(patch)
    }
    _syncWatchedLiveStageSelection(null)
    try {
        _notifyLiveLeft(roomId)
        _liveSessionController.disconnect()
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (mounted) {
            _setHomeState { _roomError = userFacingErrorMessage(error) }
        }
    } finally {
        if (mounted) {
            _setHomeState {
                _leavingLive = false
                _applyLiveJoinStatePatch(
                    _liveController.patchJoinFinished(
                        joinedLiveRoomId = _joinedLiveRoomId,
                        livePanelOpen = shouldKeepLivePanelOpen,
                        error = _roomError,
                    ),
                )
            }
        }
    }
}

internal suspend fun _HomeShellState._notifyLiveLeft(roomId: String) {
    try {
        _liveStateRequests.run { _liveController.leaveLive(roomId = roomId) }.await()
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted || isBenignGoneLiveStatePatch(error)) return
        _setHomeState { _roomError = userFacingErrorMessage(error) }
    }
}

internal suspend fun _HomeShellState._patchLiveState(
    micMuted: Boolean? = null,
    headphonesMuted: Boolean? = null,
    cameraOn: Boolean? = null,
    cameraMirrored: Boolean? = null,
    screenSharing: Boolean? = null,
    connectionState: String? = null,
    syncLiveKitMic: Boolean = true,
) {
    val roomId = _joinedLiveRoomId ?: return
    try {
        var effectiveMicMuted = micMuted
        val shouldApplyLocalMic = micMuted != null && (syncLiveKitMic || micMuted == false)
        if (shouldApplyLocalMic) {
            _ignoreErrors { _liveSessionController.setMicMuted(micMuted!!) }
            if (micMuted == false) {
                effectiveMicMuted = _liveSessionController.localMicMuted
            }
        }
        val participant =
            _liveStateRequests.run {
                _liveController.updateMyState(
                    roomId = roomId,
                    micMuted = effectiveMicMuted,
                    headphonesMuted = headphonesMuted,
                    cameraOn = cameraOn,
                    cameraMirrored = cameraMirrored,
                    screenSharing = screenSharing,
                    connectionState = connectionState,
                )
            }.await()
        val displayParticipant = _withCurrentRoomLiveDisplayName(participant, roomId)
        if (shouldSyncLiveKitMicAfterServerPatch(
                requestedMicMuted = effectiveMicMuted,
                serverMicMuted = displayParticipant.micMuted,
            )
        ) {
            _ignoreErrors { _liveSessionController.setMicMuted(displayParticipant.micMuted) }
        }
        if (!mounted || _joinedLiveRoomId != roomId || !_liveStateRequests.isIdle) {
            return
        }
        val updateSelectedLive = canPatchSelectedLiveState(joinedLiveRoomId = roomId, selectedRoomId = _selectedServerId)
        val patch = _liveController.patchStateUpdate(live = if (updateSelectedLive) _live else null, participant = displayParticipant)
        _syncLocalAudioFromParticipant(displayParticipant)
        _setHomeState {
            _micMuted = patch.micMuted
            _headphonesMuted = patch.headphonesMuted
            _cameraOn = patch.cameraOn
            _screenSharing = patch.screenSharing
            _voiceBlocked = patch.voiceBlocked
            if (updateSelectedLive) _live = patch.live
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted || isBenignGoneLiveStatePatch(error)) return
        _setHomeState { _roomError = userFacingErrorMessage(error) }
    }
}

internal suspend fun _HomeShellState._toggleCamera() {
    // Android serializes local video source switches.
    if (_switchingAndroidLocalVideoSource) return
    _switchingAndroidLocalVideoSource = true
    try {
        _toggleCameraImpl()
    } finally {
        _switchingAndroidLocalVideoSource = false
    }
}

internal suspend fun _HomeShellState._flipLocalCamera(): Boolean {
    return try {
        val flipped = _liveSessionController.flipCamera()
        if (!flipped && mounted) {
            _setHomeState { _roomError = "当前设备无法翻转摄像头" }
        }
        flipped
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (mounted) {
            _setHomeState { _roomError = userFacingErrorMessage(error) }
        }
        false
    }
}

internal suspend fun _HomeShellState._setLocalCameraMirrored(mirrored: Boolean): Boolean {
    val roomId = _joinedLiveRoomId ?: return false
    val updateSelectedLive = canPatchSelectedLiveState(joinedLiveRoomId = roomId, selectedRoomId = _selectedServerId)
    return try {
        val participant =
            _liveStateRequests.run { _liveController.updateMyState(roomId = roomId, cameraMirrored = mirrored) }.await()
        val displayParticipant = _withCurrentRoomLiveDisplayName(participant, roomId)
        if (!mounted || _joinedLiveRoomId != roomId || !_liveStateRequests.isIdle) {
            return true
        }
        val patch = _liveController.patchStateUpdate(live = if (updateSelectedLive) _live else null, participant = displayParticipant)
        _setHomeState {
            if (updateSelectedLive) _live = patch.live
        }
        displayParticipant.cameraMirrored == mirrored
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (mounted && !isBenignGoneLiveStatePatch(error)) {
            _setHomeState { _roomError = userFacingErrorMessage(error) }
        }
        false
    }
}

private suspend fun _HomeShellState._toggleCameraImpl() {
    val roomId = _joinedLiveRoomId
    if (roomId == null || !canPatchSelectedLiveState(joinedLiveRoomId = roomId, selectedRoomId = _selectedServerId)) {
        return
    }
    val enable = !_cameraOn
    if (enable && _screenSharing) {
        try {
            _stopLocalScreenShareSafely()
            if (mounted && _screenSharing) {
                _setHomeState { _screenSharing = false }
            }
            _patchLiveState(screenSharing = false)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (mounted) {
                _setHomeState { _roomError = userFacingErrorMessage(error) }
            }
            return
        }
    }
    try {
        _liveSessionController.setCameraEnabled(enable)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (mounted) {
            _setHomeState { _roomError = userFacingErrorMessage(error) }
        }
        return
    }
    _patchLiveState(cameraOn = enable, screenSharing = if (enable) false else null)
}

internal suspend fun _HomeShellState._toggleScreenShare() {
    // Android serializes local video source switches.
    if (_switchingAndroidLocalVideoSource) return
    _switchingAndroidLocalVideoSource = true
    try {
        _toggleScreenShareImpl()
    } finally {
        _switchingAndroidLocalVideoSource = false
    }
}

private suspend fun _HomeShellState._toggleScreenShareImpl() {
    if (!supportsLocalScreenShare) return
    val roomId = _joinedLiveRoomId
    if (roomId == null || !canPatchSelectedLiveState(joinedLiveRoomId = roomId, selectedRoomId = _selectedServerId)) {
        return
    }

    if (_screenSharing) {
        _ignoreErrors { _stopLocalScreenShareSafely() }
        _patchLiveState(screenSharing = false)
        return
    }

    // Android has no source picker: the MediaProjection prompt opens inside
    // setScreenShareEnabled.
    if (!canApplyPickedScreenShareSource(
            pickedForRoomId = roomId,
            joinedLiveRoomId = _joinedLiveRoomId,
            selectedRoomId = _selectedServerId,
        )
    ) {
        return
    }

    val restoreCameraOnFailure = _cameraOn
    if (_cameraOn) {
        try {
            _liveSessionController.setCameraEnabled(false)
            if (mounted && _cameraOn) _setHomeState { _cameraOn = false }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (mounted) {
                _setHomeState { _roomError = userFacingErrorMessage(error) }
            }
            return
        }
    }

    try {
        _liveSessionController.setScreenShareEnabled(true, sourceId = null)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (restoreCameraOnFailure) {
            try {
                _liveSessionController.setCameraEnabled(true)
                if (mounted) _setHomeState { _cameraOn = true }
            } catch (restoreError: Exception) {
                if (restoreError is CancellationException) throw restoreError
            }
        }
        if (mounted) {
            _setHomeState { _roomError = userFacingErrorMessage(error) }
        }
        return
    }
    _patchLiveState(screenSharing = true, cameraOn = false)
}

private suspend fun _HomeShellState._stopLocalScreenShareSafely() {
    val fullScreenTrack = _fullScreenLiveTrack
    val shouldDisposeAndroidFullScreenFirst =
        mounted &&
            widget.androidSystemService.isSupported &&
            fullScreenTrack != null &&
            fullScreenTrack.isLocal &&
            fullScreenTrack.isScreenShare
    if (shouldDisposeAndroidFullScreenFirst) {
        // A full-screen renderer can still own the MediaProjection texture for
        // the current frame. Remove that renderer before asking WebRTC to
        // release the virtual display; otherwise Android may synchronously
        // wait for a texture/handler teardown that is itself waiting for the UI
        // thread.
        _exitLiveFullScreen()
        _awaitEndOfFrame()
        // The renderer has already been disposed by the completed frame. A
        // slow system-UI transition must not permanently block capture stop.
        val transition = _liveFullScreenTransitionTail
        timeout(800.milliseconds, onTimeout = {}) { transition.await() }
    }
    _liveSessionController.setScreenShareEnabled(false)
}

/** Dart's `await WidgetsBinding.instance.endOfFrame`. */
private suspend fun _HomeShellState._awaitEndOfFrame() {
    val scope = _scope ?: return
    try {
        scope.async { withFrameNanos {} }.await()
    } catch (error: CancellationException) {
        // The shell left the composition; nothing is drawn any more.
    }
}

@Composable
private fun _LiveMemberRemoveConfirmDialog(dialogContext: BuildContext, member: RoomMember) {
    DialogFrame(
        title = "踢出语音频道",
        icon = Icons.Outlined.WarningAmber,
        adaptiveActions =
            listOf(
                ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(dialogContext).pop(false) }),
                ResponsiveDialogAction(
                    label = "踢出",
                    tone = ButtonTone.danger,
                    onPressed = { Navigator.of(dialogContext).pop(true) },
                ),
            ),
        child = {
            Text("确定要将 ${roomMemberDisplayName(member)} 踢出语音频道吗？", style = UiTypography.body)
        },
    )
}

@Composable
private fun _LiveMemberModerationConfirmDialog(dialogContext: BuildContext, member: RoomMember, action: LiveModerationAction) {
    val name = roomMemberDisplayName(member)
    val title =
        when (action) {
            LiveModerationAction.muteMic -> "麦克风静音此用户"
            LiveModerationAction.blockVoice -> "耳机静音此用户"
            LiveModerationAction.restoreVoice -> "取消麦克风静音"
            LiveModerationAction.restoreHeadphones -> "取消耳机静音"
            LiveModerationAction.kick -> "踢出语音频道"
        }
    val confirmLabel =
        when (action) {
            LiveModerationAction.muteMic -> "麦克风静音"
            LiveModerationAction.blockVoice -> "耳机静音"
            LiveModerationAction.restoreVoice -> "取消麦克风静音"
            LiveModerationAction.restoreHeadphones -> "取消耳机静音"
            LiveModerationAction.kick -> "踢出"
        }
    val body =
        when (action) {
            LiveModerationAction.muteMic -> "确定要将 $name 麦克风静音吗？对方的麦克风不会上传到语音频道。"
            LiveModerationAction.blockVoice -> "确定要将 $name 耳机静音吗？对方将无法听到语音频道。"
            LiveModerationAction.restoreVoice -> "确定要取消 $name 的麦克风静音吗？麦克风会恢复到正常状态。"
            LiveModerationAction.restoreHeadphones -> "确定要取消 $name 的耳机静音吗？耳机会恢复到正常状态。"
            LiveModerationAction.kick -> "确定要将 $name 踢出语音频道吗？"
        }
    DialogFrame(
        title = title,
        icon = Icons.Outlined.WarningAmber,
        adaptiveActions =
            listOf(
                ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(dialogContext).pop(false) }),
                ResponsiveDialogAction(
                    label = confirmLabel,
                    tone = ButtonTone.danger,
                    onPressed = { Navigator.of(dialogContext).pop(true) },
                ),
            ),
        child = { Text(body, style = UiTypography.body) },
    )
}
