package com.gangchat.client.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.ManageAccounts
import com.gangchat.client.app.RoomOpenSnapshot
import com.gangchat.client.app.SettingsSection
import com.gangchat.client.app.canOpenRoomMemberManagementFromProfile
import com.gangchat.client.app.resolvedRoomMemberProfileUser
import com.gangchat.client.app.roomUserInfoCommonRooms
import com.gangchat.client.app.roomUserInfoProfile
import com.gangchat.client.app.stickerPanelReset
import com.gangchat.client.app.userFacingErrorMessage
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.foundation.waitAll
import com.gangchat.client.foundation.future
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.shell.AndroidTaskRemovalRequest
import com.gangchat.client.shell.ReleaseDownloadCancellationToken
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.DialogFrame
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.ResponsiveDialogActionBar
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.showDialog
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred

// Port of lib/src/home/home_shell_room_actions.dart.
//
// PORT: the desktop window paths (the close request handler, the "关闭 Gang
// Chat" close-behaviour prompt and its `_ClosePromptResult`, minimising to
// the tray, the update-download close prompt and exiting the program) are not
// ported; Android ends the session through task removal instead.

/**
 * Fetches the full profile for a message sender so the hover card can show
 * fields the lightweight message summary omits (gender, common rooms). Falls
 * back to the summary we already have when the room context or request is
 * unavailable.
 */
internal suspend fun _HomeShellState._resolveSenderProfile(sender: UserSummary): UserSummary {
    val room = _selectedRoom ?: return sender
    var resolved = sender
    try {
        val profile = _roomsController.getRoomMemberProfile(roomId = room.id, userId = sender.id)
        resolved = resolvedRoomMemberProfileUser(profile = profile, fallback = sender)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        resolved = _resolveUserProfile(sender)
    }
    val withRole = roomUserInfoProfile(user = resolved, room = room, currentUser = _currentUser)
    return _userWithLocalCommonRoomNames(
        withRole.copyWith(
            commonRooms =
                roomUserInfoCommonRooms(
                    user = withRole,
                    selectedRoom = room,
                    currentUser = _currentUser,
                    includeSelectedRoom = false,
                ),
        ),
        _servers,
    )
}

internal suspend fun _HomeShellState._loadServers() {
    _setHomeState {
        _loadingServers = true
        _serverLoadError = null
    }

    try {
        val servers = _roomsController.loadRooms()
        if (!mounted) return
        _setHomeState {
            var nextServers = servers
            val selectedRoomId = _selectedServerId
            if (selectedRoomId != null) {
                nextServers = _roomsController.patchRoomUnreadCleared(rooms = nextServers, roomId = selectedRoomId).rooms
            }
            _servers = nextServers
            _loadingServers = false
            if (_selectedServerId != null && !_servers.any { server -> server.id == _selectedServerId }) {
                val removedRoomId = _selectedServerId!!
                val shouldDisconnectLive = _joinedLiveRoomId == removedRoomId
                for (entry in _stagedAttachments) {
                    entry.uploadController.cancel()
                }
                _stagedAttachments.clear()
                _discardRoomDraftInState(removedRoomId)
                _setComposerText("", saveDraft = false)
                _selectedServerId = null
                _selectedRoom = null
                _live = null
                _messages = emptyList()
                _selectedRoomNewMessageCount = 0
                _fileTransfers = emptyMap()
                _fileDownloads = emptyMap()
                _selectedRoomHasPendingJoinRequests = false
                _resetMusicBox()
                _contentMode = _ContentMode.chat
                if (shouldDisconnectLive) {
                    _joinedLiveRoomId = null
                    val liveSession = _liveSessionController
                    unawaited { liveSession.disconnect() }
                }
            }
        }
        _syncAndroidNotificationState()
        _openPendingAndroidNotificationRoom()
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted) return
        _setHomeState {
            _loadingServers = false
            _serverLoadError = userFacingErrorMessage(error, fallback = "加载服务器失败")
        }
    }
}

internal fun _HomeShellState._selectServer(server: RoomCard, openContent: Boolean, focusMessageId: String? = null) {
    unawaited { _openRoom(server, openContent = openContent, focusMessageId = focusMessageId) }
}

internal fun _HomeShellState._handleFocusMessageHandled(messageId: String) {
    if (_focusedMessageId != messageId) return
    _setHomeState { _focusedMessageId = null }
}

internal suspend fun _HomeShellState._openRoom(server: RoomCard, openContent: Boolean, focusMessageId: String? = null) {
    if (_loadingRoom && _selectedServerId == server.id) return
    _storeSelectedComposerDraft()

    _setHomeState {
        _clearDeferredRoomNotificationVisualMarkersInState()
        _selectedServerId = server.id
        _restoreComposerDraftForRoom(server.id)
        _selectedRoomNewMessageCount = server.unreadCount
        _clearRoomUnreadCount(server.id)
        _settingsOpen = false
        _contentMode = _ContentMode.chat
        if (openContent) _narrowContentOpen = true
        _selectedRoom = null
        _live = null
        _messages = emptyList()
        _focusedMessageId = focusMessageId
        _fileTransfers = emptyMap()
        _fileDownloads = emptyMap()
        _membersInitialSearchQuery = ""
        _selectedRoomHasPendingJoinRequests = false
        _roomError = null
        _sendError = null
        _resetMusicBox()
        _stickerPanelState = stickerPanelReset(source = _stickerPanelState.source)
        _loadingRoom = true
    }
    if (widget.androidSystemService.isSupported) {
        val service = widget.androidSystemService
        unawaited { service.cancelRoomNotification(server.id) }
        _syncAndroidNotificationState()
    }

    try {
        val snapshot = _roomsController.openRoom(server.id)
        if (!mounted || _selectedServerId != server.id) return
        var messages = snapshot.messages
        if (focusMessageId != null && !messages.any { message -> message.id == focusMessageId }) {
            messages = _messagesController.loadMessagesUntil(roomId = server.id, messageId = focusMessageId)
            if (!mounted || _selectedServerId != server.id) return
        }
        _setHomeState {
            _selectedRoom = snapshot.detail
            _live = snapshot.live
            _messages = messages
            _focusedMessageId = focusMessageId
            _loadingRoom = false
            _roomError = null
        }
        val loadedMessages = messages
        unawaited { _markRoomReadFromMessages(server.id, loadedMessages) }
        unawaited { _ensureComposerMentionMembers(server.id) }
        unawaited { _refreshSelectedJoinRequestBadge(snapshot.detail) }
        unawaited { _loadMusicBox(server.id) }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted || _selectedServerId != server.id) return
        _setHomeState {
            _loadingRoom = false
            _roomError = userFacingErrorMessage(error)
        }
    }
}

internal fun _HomeShellState._toggleSettings(openContent: Boolean) {
    _setHomeState {
        _clearDeferredRoomNotificationVisualMarkersInState()
        val opening = !_settingsOpen
        _settingsOpen = opening
        if (opening) {
            _settingsInitialSection = SettingsSection.profile
            _settingsInitialMusicPlaylistId = null
            _contentMode = _ContentMode.chat
            _settingsAppUpdate = null
        }
        _auxiliaryOpenedFromNarrowSidebar = opening && openContent
        if (openContent) {
            _narrowContentOpen = opening
        }
    }
}

internal fun _HomeShellState._openSettingsSection(
    section: SettingsSection,
    openContent: Boolean,
    initialMusicPlaylistId: String? = null,
) {
    _setHomeState {
        _clearDeferredRoomNotificationVisualMarkersInState()
        _settingsOpen = true
        _settingsInitialSection = section
        _settingsInitialMusicPlaylistId = initialMusicPlaylistId
        _settingsAppUpdate = null
        _contentMode = _ContentMode.chat
        _auxiliaryOpenedFromNarrowSidebar = openContent
        if (openContent) _narrowContentOpen = true
    }
}

internal fun _HomeShellState._openCreateRoom(openContent: Boolean) {
    if (!_settingsOpen && _contentMode == _ContentMode.createRoom) {
        _closeCreateRoom()
        return
    }
    _setHomeState {
        _clearDeferredRoomNotificationVisualMarkersInState()
        _settingsOpen = false
        _contentMode = _ContentMode.createRoom
        _auxiliaryOpenedFromNarrowSidebar = openContent
        if (openContent) _narrowContentOpen = true
    }
}

internal fun _HomeShellState._closeCreateRoom() {
    _setHomeState {
        _contentMode = _ContentMode.chat
        _restorePaneAfterAuxiliaryInState()
    }
}

internal fun _HomeShellState._closeSettings() {
    _setHomeState {
        _settingsOpen = false
        _settingsAppUpdate = null
        _restorePaneAfterAuxiliaryInState()
    }
}

internal suspend fun _HomeShellState._confirmLogout() {
    if (_logoutConfirming) return
    _setHomeState { _logoutConfirming = true }
    try {
        val confirmed =
            showDialog<Boolean>(context) { dialogContext ->
                DialogFrame(
                    title = "退出登录",
                    icon = Icons.AutoMirrored.Filled.Logout,
                    actionBar = {
                        ResponsiveDialogActionBar(
                            actions =
                                listOf(
                                    ResponsiveDialogAction(
                                        label = "取消",
                                        onPressed = { Navigator.of(dialogContext).pop(false) },
                                    ),
                                    ResponsiveDialogAction(
                                        label = "退出登录",
                                        onPressed = { Navigator.of(dialogContext).pop(true) },
                                        tone = ButtonTone.danger,
                                    ),
                                ),
                        )
                    },
                    child = {
                        Text("确认退出当前账号？", style = UiTypography.body.copy(color = UiColors.textSecondary))
                    },
                )
            }
        if (!mounted || confirmed != true) return
        _logout()
    } finally {
        if (mounted) {
            _setHomeState { _logoutConfirming = false }
        }
    }
}

internal suspend fun _HomeShellState._logout() {
    if (widget.androidSystemService.isSupported) {
        try {
            _androidPushRegistration?.unregister()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // The server also ignores devices attached to the revoked session, so
            // logout remains safe when this best-effort cleanup is offline.
        }
        try {
            widget.androidSystemService.clearMessageNotifications()
            widget.androidSystemService.enterForeground()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
        }
    }
    _leaveLiveForSessionEnd(disconnectTimeout = 1.seconds)
    _resetLiveAudioStateAfterSessionEnd()
    widget.app.logout()
}

private suspend fun _HomeShellState._resetLiveAudioStateAfterSessionEnd() {
    if (mounted) {
        _setHomeState {
            _micMuted = false
            _headphonesMuted = false
            _voiceBlocked = false
        }
    }
    _ignoreErrors { _liveSessionController.setInputVolume(_restoredInputVolume()) }
    _ignoreErrors { _liveSessionController.setOutputVolume(_restoredOutputVolume()) }
    _ignoreErrors { _liveSessionController.setMicMuted(false) }
    _ignoreErrors { _liveSessionController.setOutputMuted(false) }
}

private suspend fun _HomeShellState._leaveLiveForSessionEnd(disconnectTimeout: Duration? = null) {
    val joinedLiveRoomId = _joinedLiveRoomId
    _joinedLiveRoomId = null
    if (joinedLiveRoomId != null) {
        _notifyLiveLeft(joinedLiveRoomId)
    }
    _liveSessionController.disconnect(timeout = disconnectTimeout)
}

private suspend fun _HomeShellState._stopRealtimeForExit() {
    _ignoreErrors { _services.realtime.stop() }
}

internal suspend fun _HomeShellState._handleAndroidTaskRemoved(request: AndroidTaskRemovalRequest) {
    if (_exitingApplication) {
        // Another explicit-exit path is already sending the same cleanup. Do not
        // acknowledge early and let native Android cut that request short; the
        // native grace timeout will still guarantee that the process terminates.
        return
    }
    _exitingApplication = true
    try {
        waitAll(
            listOf(
                future { _leaveLiveForAndroidTaskRemoval() },
                future { _stopRealtimeForExit() },
            ),
        )
        widget.app.exitSessionForAppExit()
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        // Native Android enforces a short process-exit timeout and LiveKit's
        // participant_left webhook is the final cleanup path.
    } finally {
        request.complete()
    }
}

private suspend fun _HomeShellState._leaveLiveForAndroidTaskRemoval() {
    val joinedLiveRoomId = _joinedLiveRoomId
    _joinedLiveRoomId = null

    // Task removal is terminal and has a strict native deadline. Do not put
    // this leave behind an in-flight mic/camera/screen-share PATCH. Start the
    // authenticated server leave and LiveKit disconnect together so either
    // path can remove the remote track and participant immediately.
    val liveSession = _liveSessionController
    val cleanup = mutableListOf<Deferred<Unit>>(future { liveSession.disconnect(timeout = 900.milliseconds) })
    if (joinedLiveRoomId != null) {
        val liveController = _liveController
        cleanup.add(
            future {
                try {
                    liveController.leaveLive(roomId = joinedLiveRoomId)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    // LiveKit's participant_left webhook remains the fallback.
                }
                Unit
            },
        )
    }
    waitAll(cleanup)
}

internal fun _HomeShellState._handleAppUpdateDownloadCancellationChanged(token: ReleaseDownloadCancellationToken?) {
    if (_appUpdateDownloadCancellationToken === token) return
    _setHomeState {
        _appUpdateDownloadCancellationToken = token
        if (token != null) {
            _searchExpanded = false
            _titleSearchContextMenuOpen = false
            _pendingTitleSearchContextMenuUpdate = null
        }
    }
    if (token != null) {
        _clearPrimaryFocus()
        unawaited { _leaveLiveForAppUpdateDownload() }
    }
}

private suspend fun _HomeShellState._leaveLiveForAppUpdateDownload() {
    if (_joinedLiveRoomId == null) return
    _leaveLive()
}

/** Dart's `FocusManager.instance.primaryFocus?.unfocus()`. */
internal fun _HomeShellState._clearPrimaryFocus() {
    try {
        com.gangchat.client.widgets.FocusScope.unfocus(context)
    } catch (error: IllegalStateException) {
        // Before the first composition there is no focus to clear.
    }
}

internal fun _HomeShellState._handleUserUpdated(user: CurrentUser) {
    _setHomeState { _currentUser = user }
    widget.app.onCurrentUserUpdated?.invoke(user)
}

internal suspend fun _HomeShellState._retryOpenSelectedRoom() {
    val server = _selectedServer ?: return
    _openRoom(server, openContent = false)
}

internal fun _HomeShellState._openLiveChannel() {
    val roomId = _selectedServerId ?: return
    _setHomeState {
        _settingsOpen = false
        _contentMode = _ContentMode.live
        _narrowContentOpen = true
    }
    _ensureMusicBoxLoaded(roomId)
}

internal suspend fun _HomeShellState._openJoinedLiveChannel() {
    val roomId = _joinedLiveRoomId ?: return
    _collapseSearch()

    if (_selectedServerId == roomId && _selectedRoom?.id == roomId) {
        _openLiveChannel()
        return
    }

    val server = _joinedServerById(roomId) ?: return

    try {
        val snapshot = _roomsController.openRoom(roomId)
        if (!mounted || _joinedLiveRoomId != roomId) return
        _applyJoinedLiveRoomSnapshot(server, snapshot)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted || _joinedLiveRoomId != roomId) return
        if (_selectedServerId == roomId) {
            _setHomeState { _roomError = userFacingErrorMessage(error) }
        }
    }
}

private fun _HomeShellState._applyJoinedLiveRoomSnapshot(server: RoomCard, snapshot: RoomOpenSnapshot) {
    _storeSelectedComposerDraft()

    _setHomeState {
        _selectedServerId = server.id
        _restoreComposerDraftForRoom(server.id)
        _selectedRoomNewMessageCount = server.unreadCount
        _clearRoomUnreadCount(server.id)
        _settingsOpen = false
        _contentMode = _ContentMode.live
        _narrowContentOpen = true
        _selectedRoom = snapshot.detail
        _live = snapshot.live
        _messages = snapshot.messages
        _fileTransfers = emptyMap()
        _fileDownloads = emptyMap()
        _membersInitialSearchQuery = ""
        _selectedRoomHasPendingJoinRequests = false
        _roomError = null
        _sendError = null
        _loadingRoom = false
        _resetMusicBox()
        _stickerPanelState = stickerPanelReset(source = _stickerPanelState.source)
    }
    unawaited { _markRoomReadFromMessages(server.id, snapshot.messages) }
    unawaited { _refreshSelectedJoinRequestBadge(snapshot.detail) }
    unawaited { _loadMusicBox(server.id) }
}

// Dart's `_HomeShellRoomActions._serverById`, which (unlike the search
// extension's method of the same name) only looks at the room list.
private fun _HomeShellState._joinedServerById(roomId: String): RoomCard? {
    for (server in _servers) {
        if (server.id == roomId) return server
    }
    return null
}

internal fun _HomeShellState._openChat() {
    val roomId = _selectedServerId
    _setHomeState {
        _clearDeferredRoomNotificationVisualMarkersInState()
        _contentMode = _ContentMode.chat
    }
    if (roomId != null) {
        unawaited { _refreshSelectedMessagesSilently(roomId) }
    }
}

internal fun _HomeShellState._openRoomMembers(initialSearchQuery: String = "") {
    if (_selectedRoom == null) return
    _setHomeState {
        _settingsOpen = false
        _contentMode = _ContentMode.members
        _narrowContentOpen = true
        _membersInitialSearchQuery = initialSearchQuery.trim()
    }
}

internal fun _HomeShellState._messageProfileAction(user: UserSummary): UserProfileAction? {
    val room = _selectedRoom ?: return null
    val uid = user.uid?.trim()
    if (uid == null || uid.isEmpty()) return null
    if (!canOpenRoomMemberManagementFromProfile(room = room, currentUser = _currentUser, target = user)) {
        return null
    }
    return UserProfileAction(
        label = "管理成员",
        icon = Icons.Outlined.ManageAccounts,
        onPressed = { _openRoomMembers(initialSearchQuery = uid) },
    )
}

internal fun _HomeShellState._openRoomSettings(
    initialSection: RoomSettingsSection = RoomSettingsSection.info,
    initialMusicPlaylistId: String? = null,
) {
    if (_selectedRoom == null) return
    _setHomeState {
        _settingsOpen = false
        _roomSettingsInitialSection = initialSection
        _roomSettingsInitialMusicPlaylistId = initialMusicPlaylistId
        _contentMode = _ContentMode.roomSettings
        _narrowContentOpen = true
    }
}

internal fun _HomeShellState._handleRoomSettingsResult(roomId: String, result: RoomManagementResult) {
    when (result.kind) {
        RoomManagementResultKind.created -> {}
        RoomManagementResultKind.updated -> {
            val updated = result.room
            if (updated != null) _applyManagedRoomUpdated(updated)
            unawaited { _loadServers() }
        }
        RoomManagementResultKind.left, RoomManagementResultKind.deleted -> {
            _applyManagedRoomRemoved(roomId)
            unawaited { _loadServers() }
        }
    }
}

internal fun _HomeShellState._handleCreateRoomResult(result: RoomManagementResult) {
    if (result.kind != RoomManagementResultKind.created) return
    val room = result.room ?: return
    _applyCreatedRoom(room)
    unawaited { _loadServers() }
}

private fun _HomeShellState._applyCreatedRoom(room: RoomDetail) {
    _storeSelectedComposerDraft()
    val patch = _roomsController.patchRoomDetailApplied(rooms = _servers, detail = room)
    _setHomeState {
        _selectedServerId = room.id
        _discardRoomDraftInState(room.id)
        _stagedAttachments.clear()
        _setComposerText("", saveDraft = false)
        _selectedRoom = patch.selectedRoom
        _servers = patch.rooms
        _live = room.live
        _messages = emptyList()
        _selectedRoomNewMessageCount = 0
        _fileTransfers = emptyMap()
        _fileDownloads = emptyMap()
        _settingsOpen = false
        _contentMode = _ContentMode.chat
        _membersInitialSearchQuery = ""
        _selectedRoomHasPendingJoinRequests = false
        _roomError = null
        _sendError = null
        _loadingRoom = false
        _narrowContentOpen = true
    }
}

internal fun _HomeShellState._applyManagedRoomUpdated(room: RoomDetail) {
    if (_selectedServerId != room.id) return
    if (_joinedLiveRoomId == room.id) {
        _joinedLivePersonalAiVoiceAnnouncementsEnabled = room.aiVoiceAnnouncementsEnabled
    }
    val patch = _roomsController.patchRoomDetailApplied(rooms = _servers, detail = room)
    _setHomeState {
        _selectedRoom = patch.selectedRoom
        _servers = patch.rooms
        _live = room.live
        _roomError = null
    }
    unawaited { _refreshSelectedMessagesSilently(room.id) }
    unawaited { _refreshSelectedJoinRequestBadge(room) }
}

private fun _HomeShellState._applyManagedRoomRemoved(roomId: String) {
    _setHomeState {
        _discardRoomDraftInState(roomId)
        _servers = _roomsController.removeRoomCard(_servers, roomId)
        if (_selectedServerId == roomId) {
            for (entry in _stagedAttachments) {
                entry.uploadController.cancel()
            }
            _stagedAttachments.clear()
            _setComposerText("", saveDraft = false)
            _selectedServerId = null
            _selectedRoom = null
            _live = null
            _messages = emptyList()
            _selectedRoomNewMessageCount = 0
            _fileTransfers = emptyMap()
            _fileDownloads = emptyMap()
            _contentMode = _ContentMode.chat
            _membersInitialSearchQuery = ""
            _settingsOpen = false
            _selectedRoomHasPendingJoinRequests = false
            _roomError = null
            _sendError = null
            _narrowContentOpen = false
        }
        if (_joinedLiveRoomId == roomId) {
            _joinedLiveRoomId = null
            _joiningLive = false
            _leavingLive = false
        }
    }
}
