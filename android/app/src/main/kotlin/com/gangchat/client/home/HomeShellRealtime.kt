package com.gangchat.client.home

import com.gangchat.client.app.RealtimeConnectionStatus
import com.gangchat.client.app.RealtimeEvent
import com.gangchat.client.app.isLiveSnapshotRealtimeEventType
import com.gangchat.client.app.normalizedAudioVolume
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.asJsonMap
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.RoomCard
import kotlinx.coroutines.CancellationException

// Port of lib/src/home/home_shell_realtime.dart.

internal fun _HomeShellState._startRealtime() {
    _realtimeEvents?.cancel()
    _realtimeStatusEvents?.cancel()

    val realtime = _services.realtime
    realtime.onReconnect = { _onRealtimeReconnect() }
    _realtimeStatus = realtime.status
    _realtimeStatusEvents = realtime.statusChanges.listen({ status -> _onRealtimeStatusChanged(status) })
    _realtimeEvents = realtime.events.listen({ event -> _onRealtimeEvent(event) })
    unawaited { realtime.start() }
}

private fun _HomeShellState._onRealtimeStatusChanged(status: RealtimeConnectionStatus) {
    if (!mounted) return
    _setHomeState { _realtimeStatus = status }
}

private fun _HomeShellState._onRealtimeReconnect() {
    if (!mounted) return
    val pushRegistration = _androidPushRegistration
    unawaited { _ignoreErrors { pushRegistration?.synchronize() } }
    val roomReads = _services.roomReads
    unawaited { roomReads.retryPending() }
    unawaited { _loadServersSilently() }
    val selected = _selectedServerId
    if (selected != null) unawaited { _refreshLiveSilently(selected) }
    val joinedLiveRoomId = _joinedLiveRoomId
    if (joinedLiveRoomId != null && !_liveSessionController.isAttachedToRoom(joinedLiveRoomId)) {
        unawaited { _restoreLiveAfterRealtimeReconnect(joinedLiveRoomId) }
    }
}

internal suspend fun _HomeShellState._loadServersSilently() {
    try {
        val servers = _roomsController.loadRooms()
        if (!mounted) return
        _setHomeState {
            var nextServers = _roomsController.patchRoomCardsRefreshed(rooms = servers).rooms
            val selectedRoomId = _selectedServerId
            if (selectedRoomId != null) {
                nextServers = _roomsController.patchRoomUnreadCleared(rooms = nextServers, roomId = selectedRoomId).rooms
            }
            _servers = nextServers
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
    }
}

internal suspend fun _HomeShellState._refreshLiveSilently(roomId: String) {
    try {
        val live = _roomsController.getLiveState(roomId)
        if (!mounted) return
        val patch =
            _roomsController.patchSelectedLiveRefreshed(
                live = live,
                selectedRoomId = _selectedServerId,
                currentLive = _live,
            ) ?: return
        _cacheJoinedLiveParticipantUsers(patch.live)
        _applyJoinedLiveAuthoritativeSnapshot(patch.live, eventType = "live_state_refreshed")
        _setHomeState {
            _live = patch.live
            _servers = _roomsController.patchRoomLiveCount(_servers, roomId, patch.live)
        }
        _syncJoinedLiveAudioFromSnapshot(patch.live)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
    }
}

private fun _HomeShellState._onRealtimeEvent(event: RealtimeEvent) {
    if (isLiveSnapshotRealtimeEventType(event.type)) {
        _applyLiveSnapshot(event.type, event.data)
        return
    }
    when (event.type) {
        "room_added" -> _applyRoomAdded(event.data)
        "room_updated" -> _applyRoomUpdated(event.data)
        "room_deleted" -> _applyRoomDeleted(event.data)
        "room_invites_updated" -> _applyRoomInvitesUpdated()
        "room_applications_updated" -> _applyRoomApplicationsUpdated()
        "room_notifications_updated" -> _applyRoomNotificationsUpdated()
        "room_role_changed" -> _applyRoomRoleChanged(event.data)
        "room_member_profile_changed" -> _applyRoomMemberProfileChanged(event.data)
        "room_join_requests_updated" -> _applyRoomJoinRequestsUpdated(event.data)
        "account_suspended" -> unawaited { _logoutSuspendedAccount() }
        "music_box_changed" -> _onMusicBoxChanged(event.data)
        "music_box_progress" -> _onMusicBoxProgress(event.data)
        else -> {}
    }
}

private suspend fun _HomeShellState._logoutSuspendedAccount() {
    if (_accountSuspensionLogoutInProgress) return
    _accountSuspensionLogoutInProgress = true
    _logout()
}

private fun _HomeShellState._applyLiveSnapshot(eventType: String, data: JsonMap) {
    val eventRoomId = data["room_id"] as String?
    val liveJson = data["live"]
    var joinedRoomSnapshot: LiveState? = null
    if (eventRoomId == _joinedLiveRoomId && liveJson is Map<*, *>) {
        try {
            val snapshot = LiveState.fromJson(liveJson.asJsonMap())
            joinedRoomSnapshot = snapshot
            _cacheJoinedLiveParticipantUsers(snapshot)
            _applyJoinedLiveAuthoritativeSnapshot(snapshot, eventType = eventType, eventData = data)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
        }
    }
    val patch =
        _roomsController.patchLiveSnapshot(
            rooms = _servers,
            selectedRoomId = _selectedServerId,
            data = data,
            joinedLiveRoomId = _joinedLiveRoomId,
            currentUserId = _currentUser.id,
            previousLive = _live,
            localParticipantConnected =
                eventRoomId != null &&
                    _liveSessionController.isAttachedToRoom(eventRoomId) &&
                    _liveSessionController.connectedParticipantIdentities.contains(_currentUser.id),
        )
    if (patch == null || !mounted) return
    _setHomeState {
        _servers = patch.rooms
        if (patch.selectedLive != null) _live = patch.selectedLive
    }
    val selectedLive = patch.selectedLive
    if (selectedLive != null) {
        _syncJoinedLiveAudioFromSnapshot(selectedLive)
    } else if (joinedRoomSnapshot != null) {
        // Voice participation continues while the user browses another room.
        // Moderation/reconnect snapshots for that joined room must still update
        // the actual microphone/headphones state even though its roster is not
        // the currently rendered one.
        _syncJoinedLiveAudioFromSnapshot(joinedRoomSnapshot)
    }
}

private fun _HomeShellState._applyRoomAdded(data: JsonMap) {
    val room = _roomsController.roomCardFromSnapshot(data)
    if (room == null || !mounted) return
    _setHomeState {
        _servers = _roomsController.patchRoomCardUpserted(rooms = _servers, room = room).rooms
    }
}

private fun _HomeShellState._applyRoomUpdated(data: JsonMap) {
    val previousJoinPolicy = _selectedRoom?.joinPolicy
    val room = _roomsController.roomCardFromSnapshot(data)
    if (room == null || !mounted) return
    val shouldNotifyMessage =
        _messageNotificationTracker.register(
            roomId = room.id,
            updateReason = data["update_reason"]?.toString() ?: "",
            notificationPolicy = room.notificationPolicy,
            messageId = room.lastMessage?.id,
        )
    if (room.id == _joinedLiveRoomId) {
        _joinedLivePersonalAiVoiceAnnouncementsEnabled = room.aiVoiceAnnouncementsEnabled
    }
    val shouldRefreshMessages =
        room.id == _selectedServerId &&
            room.lastMessage != null &&
            room.lastMessage!!.id != _latestLoadedServerMessageId()
    val patch =
        _roomsController.patchRoomUpdated(
            rooms = _servers,
            incoming = room,
            selectedRoom = _selectedRoom,
        )
    var selectedNewMessageCount: Int? = null
    if (room.id == _selectedServerId && room.hasUnreadCount) {
        // Account-scoped room snapshots are authoritative for unread state.
        // Apply a read receipt from another device even when the last message is
        // already loaded locally and no message refresh is necessary.
        selectedNewMessageCount = room.unreadCount
    } else if (shouldRefreshMessages) {
        for (candidate in patch.rooms) {
            if (candidate.id == room.id) {
                selectedNewMessageCount = candidate.unreadCount
                break
            }
        }
    }
    _setHomeState {
        _servers = patch.rooms
        _selectedRoom = patch.selectedRoom
        if (selectedNewMessageCount != null) {
            _selectedRoomNewMessageCount = selectedNewMessageCount
        }
        if (patch.shouldReloadMembers) _membersReloadToken++
    }
    val selectedRoom = patch.selectedRoom
    if (selectedRoom != null && selectedRoom.id == room.id && previousJoinPolicy != selectedRoom.joinPolicy) {
        unawaited { _refreshSelectedJoinRequestBadge(selectedRoom) }
    }
    if (shouldRefreshMessages) {
        unawaited { _refreshSelectedMessagesSilently(room.id) }
    }
    _syncAndroidNotificationState()
    if (shouldNotifyMessage) _notifyRealtimeRoomMessage(room)
}

private fun _HomeShellState._notifyRealtimeRoomMessage(room: RoomCard) {
    val androidSystemService = widget.androidSystemService
    if (androidSystemService.isSupported && !_isAppForeground) {
        val message = room.lastMessage
        if (message != null) {
            val unreadCount = _androidUnreadCount
            unawaited {
                _ignoreErrors {
                    androidSystemService.showRoomMessage(
                        roomId = room.id,
                        roomName = room.displayName,
                        sender = message.senderDisplayName,
                        body = message.bodyPreview,
                        unreadCount = unreadCount,
                        messageId = message.id,
                    )
                }
            }
        }
        return
    }
    val volume = if (_headphonesMuted) 0.0 else normalizedAudioVolume(_liveSessionController.outputVolume)
    val player = _messageNotificationSoundPlayer
    unawaited { _ignoreErrors { player.play(volume = volume) } }
    // PORT: Dart also asked the desktop window to flash its taskbar entry
    // (`requestMessageAttention`), a no-op on Android.
}

private val _HomeShellState._androidUnreadCount: Int
    get() = _servers.fold(0) { total, room -> total + room.unreadCount }

internal fun _HomeShellState._syncAndroidNotificationState() {
    val service = widget.androidSystemService
    if (!service.isSupported) return
    for (room in _servers) {
        if (room.unreadCount <= 0) {
            unawaited { _ignoreErrors { service.cancelRoomNotification(room.id) } }
        }
    }
    val unreadCount = _androidUnreadCount
    unawaited { _ignoreErrors { service.syncBadge(unreadCount) } }
}

private fun _HomeShellState._latestLoadedServerMessageId(): String? = _latestServerMessageId(_messages)

internal suspend fun _HomeShellState._refreshSelectedMessagesSilently(roomId: String) {
    try {
        val messages = _messagesController.loadMessages(roomId)
        if (!mounted || _selectedServerId != roomId) return
        val serverClientIds = messages.map { message -> message.clientMessageId }.toSet()
        val pending = _messages.filter { message -> message.pending && !serverClientIds.contains(message.clientMessageId) }
        val nextMessages = messages + pending
        _setHomeState { _messages = nextMessages }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
    }
}

private fun _HomeShellState._applyRoomDeleted(data: JsonMap) {
    val patch =
        _roomsController.patchRoomDeleted(
            rooms = _servers,
            selectedRoomId = _selectedServerId,
            selectedRoom = _selectedRoom,
            selectedRoomHasPendingJoinRequests = _selectedRoomHasPendingJoinRequests,
            messages = _messages,
            live = _live,
            livePanelOpen = _contentMode == _ContentMode.live,
            settingsOpen = _settingsOpen,
            joinedLiveRoomId = _joinedLiveRoomId,
            data = data,
        )
    if (patch == null || !mounted) return
    _setHomeState {
        _discardRoomDraftInState(patch.roomId)
        _servers = patch.rooms
        _selectedServerId = patch.selectedRoomId
        _selectedRoom = patch.selectedRoom
        _selectedRoomHasPendingJoinRequests = patch.selectedRoomHasPendingJoinRequests
        _messages = patch.messages
        _live = patch.live
        _settingsOpen = patch.settingsOpen
        _contentMode = if (patch.livePanelOpen) _ContentMode.live else _ContentMode.chat
        _joinedLiveRoomId = patch.joinedLiveRoomId
        if (patch.wasSelected) {
            for (entry in _stagedAttachments) {
                entry.uploadController.cancel()
            }
            _stagedAttachments.clear()
            _setComposerText("", saveDraft = false)
            _selectedRoomNewMessageCount = 0
            _fileTransfers = emptyMap()
            _roomError = null
            _sendError = null
            _narrowContentOpen = false
            _resetMusicBox()
        }
    }
    if (widget.androidSystemService.isSupported) {
        val service = widget.androidSystemService
        unawaited { _ignoreErrors { service.cancelRoomNotification(patch.roomId) } }
        _syncAndroidNotificationState()
    }
    if (patch.shouldDisconnectLive) {
        val liveSession = _liveSessionController
        unawaited { liveSession.disconnect() }
    }
}

/**
 * Applies a `room_role_changed` event for the affected member (the current
 * user). The shared room snapshot omits `my_role`, so this is the only way a
 * promote/demote reaches the open room without a manual refetch. Updates the
 * selected room's membership role in place so permission-gated UI (manage,
 * review join requests) re-evaluates immediately.
 */
private fun _HomeShellState._applyRoomRoleChanged(data: JsonMap) {
    val patch = _roomsController.patchRoomRoleChanged(selectedRoom = _selectedRoom, data = data)
    if (patch == null || !mounted) return
    _setHomeState {
        _selectedRoom = patch.selectedRoom
        // A role change can flip whether the user may review join requests, so
        // bump the members reload token to re-pull that list if the panel's open.
        _membersReloadToken++
    }
    unawaited { _refreshSelectedJoinRequestBadge(patch.selectedRoom) }
    // Music-box capabilities are actor-specific and derived from the current
    // room role. Refresh them immediately instead of waiting for the next
    // queue/playback mutation.
    if (patch.roomId == _selectedServerId) {
        unawaited { _loadMusicBox(patch.roomId, refreshAfterCurrent = true) }
    }
}

/**
 * Applies a `room_join_requests_updated` event: the pending join-request set
 * for the room changed (new request, or one approved/denied elsewhere). If it
 * targets the open room, nudge the members panel to reload its request list
 * via the reload token. Also refresh the notification badge, since a new
 * request may need the current user's attention.
 */
private fun _HomeShellState._applyRoomJoinRequestsUpdated(data: JsonMap) {
    val roomId = data["room_id"] as String?
    if (!mounted) return
    unawaited { _refreshPendingRoomInviteBadge() }
    if (roomId == null || roomId != _selectedServerId) return
    _setHomeState { _membersReloadToken++ }
    unawaited { _refreshSelectedJoinRequestBadge() }
}

private fun _HomeShellState._applyRoomMemberProfileChanged(data: JsonMap) {
    val roomId = data["room_id"] as String?
    if (!mounted || roomId == null || roomId != _selectedServerId) return
    _setHomeState { _membersReloadToken++ }
}
