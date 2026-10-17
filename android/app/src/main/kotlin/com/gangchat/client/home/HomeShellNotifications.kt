package com.gangchat.client.home

import com.gangchat.client.app.RoomNavigationBadgePatch
import com.gangchat.client.app.RoomNotificationItem
import com.gangchat.client.app.canReviewNotificationInvite
import com.gangchat.client.app.canWithdrawNotificationApplication
import com.gangchat.client.app.joinRequestReasonValue
import com.gangchat.client.app.markUnreadRoomEventNotificationsRead
import com.gangchat.client.app.pendingRoomNotificationCount
import com.gangchat.client.app.roomAccessState
import com.gangchat.client.app.roomInviteAcceptRequiresApplication
import com.gangchat.client.app.roomInviteBadgeUpdated
import com.gangchat.client.app.roomInvitesEnabled
import com.gangchat.client.app.roomNotificationCopyText
import com.gangchat.client.app.roomNotificationDeletionId
import com.gangchat.client.app.roomNotificationDeletionType
import com.gangchat.client.app.selectedJoinRequestBadgeUpdated
import com.gangchat.client.app.userFacingErrorMessage
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.future
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.foundation.waitAll
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomApplication
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomEventNotification
import com.gangchat.client.protocol.RoomInvite
import kotlinx.coroutines.CancellationException

// Port of lib/src/home/home_shell_notifications.dart.

private fun _HomeShellState._canShowJoinRequestBadgeForRoom(room: RoomDetail): Boolean {
    val access = roomAccessState(room = room, currentUser = _currentUser)
    return access.canReviewJoinRequests && roomInvitesEnabled(room.joinPolicy)
}

private fun _HomeShellState._applyRoomNavigationBadgePatch(patch: RoomNavigationBadgePatch?) {
    if (patch == null || !mounted) return
    _setHomeState {
        _hasPendingRoomInvites = patch.hasPendingRoomInvites
        if (patch.pendingRoomNotificationCount != null) {
            _pendingRoomNotificationCount = patch.pendingRoomNotificationCount!!
        }
        _selectedRoomHasPendingJoinRequests = patch.selectedRoomHasPendingJoinRequests
    }
}

private fun _HomeShellState._setSelectedJoinRequestBadge(hasPending: Boolean) {
    _applyRoomNavigationBadgePatch(
        selectedJoinRequestBadgeUpdated(
            currentHasPendingRoomInvites = _hasPendingRoomInvites,
            currentSelectedRoomHasPendingJoinRequests = _selectedRoomHasPendingJoinRequests,
            hasPending = hasPending,
        ),
    )
}

internal fun _HomeShellState._handleSelectedJoinRequestsChanged(hasPending: Boolean) {
    val room = _selectedRoom ?: return
    _setSelectedJoinRequestBadge(hasPending && _canShowJoinRequestBadgeForRoom(room))
}

internal suspend fun _HomeShellState._refreshSelectedJoinRequestBadge(room: RoomDetail? = null) {
    val target = room ?: _selectedRoom
    if (target == null) {
        _setSelectedJoinRequestBadge(false)
        return
    }
    if (!_canShowJoinRequestBadgeForRoom(target)) {
        _setSelectedJoinRequestBadge(false)
        return
    }
    try {
        val hasPending = _roomsController.hasPendingJoinRequests(target, canReviewJoinRequests = true)
        if (!mounted || _selectedServerId != target.id) return
        val currentRoom = _selectedRoom
        _setSelectedJoinRequestBadge(
            hasPending &&
                currentRoom != null &&
                currentRoom.id == target.id &&
                _canShowJoinRequestBadgeForRoom(currentRoom),
        )
    } catch (error: Exception) {
        if (error is CancellationException) throw error
    }
}

internal fun _HomeShellState._openNotifications(openContent: Boolean) {
    if (!_settingsOpen && _contentMode == _ContentMode.notifications) {
        _closeNotifications()
        return
    }
    _setHomeState {
        _settingsOpen = false
        _contentMode = _ContentMode.notifications
        _auxiliaryOpenedFromNarrowSidebar = openContent
        if (openContent) _narrowContentOpen = true
    }
    val silent =
        _notificationInvites.isNotEmpty() || _notificationApplications.isNotEmpty() || _notificationRoomEvents.isNotEmpty()
    unawaited { _loadNotifications(silent = silent) }
}

internal fun _HomeShellState._closeNotifications() {
    _setHomeState {
        _clearDeferredRoomNotificationVisualMarkersInState()
        _contentMode = _ContentMode.chat
        _restorePaneAfterAuxiliaryInState()
    }
}

internal fun _HomeShellState._openNotificationRoom(room: PublicRoom) {
    if (!room.joined) return
    _setHomeState { _clearDeferredRoomNotificationVisualMarkersInState() }
    _selectServer(_roomCardForPublicRoom(room), openContent = _compactLayout)
}

internal fun _HomeShellState._openNotificationRoomEvent(notification: RoomEventNotification) {
    val room = notification.room
    if (!room.joined) return
    _setHomeState { _clearDeferredRoomNotificationVisualMarkersInState() }
    _selectServer(
        _roomCardForPublicRoom(room),
        openContent = _compactLayout,
        focusMessageId = notification.messageId,
    )
}

internal suspend fun _HomeShellState._loadNotifications(silent: Boolean = false, clearVisualReadMarkers: Boolean = false) {
    if (!silent) {
        _setHomeState {
            _loadingNotifications = true
            _notificationError = null
        }
    }

    try {
        val rooms = _roomsController
        val invitesFuture = future { rooms.listRoomInvites(status = "all") }
        val applicationsFuture = future { rooms.listRoomApplications(status = "all") }
        val roomEventsFuture = future { rooms.listRoomNotifications() }
        waitAll(listOf(invitesFuture, applicationsFuture, roomEventsFuture))
        val invites: List<RoomInvite> = invitesFuture.await()
        val applications: List<RoomApplication> = applicationsFuture.await()
        val roomEvents: List<RoomEventNotification> = roomEventsFuture.await()
        if (!mounted) return
        _setHomeState {
            val visualReadAt = if (clearVisualReadMarkers) _deferredRoomNotificationVisualReadAt else null
            val visualReadIds = if (clearVisualReadMarkers) _deferredRoomNotificationVisualReadIds else emptySet()
            _notificationInvites = invites
            _notificationApplications = applications
            _notificationRoomEvents =
                if (visualReadAt == null || visualReadIds.isEmpty()) {
                    roomEvents
                } else {
                    markUnreadRoomEventNotificationsRead(
                        notifications = roomEvents,
                        readAt = visualReadAt,
                        notificationIds = visualReadIds,
                    )
                }
            if (clearVisualReadMarkers) {
                _deferredRoomNotificationVisualReadAt = null
                _deferredRoomNotificationVisualReadIds = emptySet()
            }
            _loadingNotifications = false
            _notificationError = null
            _pendingRoomNotificationCount =
                pendingRoomNotificationCount(invites = invites, applications = applications, roomEvents = roomEvents)
            _hasPendingRoomInvites = _pendingRoomNotificationCount > 0
        }
        if (_contentMode == _ContentMode.notifications) {
            unawaited { _markLoadedRoomNotificationsRead(roomEvents) }
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted) return
        _setHomeState {
            _loadingNotifications = false
            _notificationError = userFacingErrorMessage(error)
        }
    }
}

internal suspend fun _HomeShellState._refreshPendingRoomInviteBadge() {
    try {
        val rooms = _roomsController
        val invitesFuture = future { rooms.listRoomInvites() }
        val applicationsFuture = future { rooms.listRoomApplications() }
        val roomEventsFuture = future { rooms.listRoomNotifications() }
        waitAll(listOf(invitesFuture, applicationsFuture, roomEventsFuture))
        val invites = invitesFuture.await()
        val applications = applicationsFuture.await()
        val roomEvents = roomEventsFuture.await()
        if (!mounted) return
        _setHomeState {
            val pendingCount =
                pendingRoomNotificationCount(invites = invites, applications = applications, roomEvents = roomEvents)
            val patch =
                roomInviteBadgeUpdated(
                    currentHasPendingRoomInvites = _hasPendingRoomInvites,
                    currentPendingRoomNotificationCount = _pendingRoomNotificationCount,
                    currentSelectedRoomHasPendingJoinRequests = _selectedRoomHasPendingJoinRequests,
                    hasPending = pendingCount > 0,
                    pendingRoomNotificationCount = pendingCount,
                ) ?: return@_setHomeState
            _hasPendingRoomInvites = patch.hasPendingRoomInvites
            _pendingRoomNotificationCount = pendingCount
            _selectedRoomHasPendingJoinRequests = patch.selectedRoomHasPendingJoinRequests
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
    }
}

private suspend fun _HomeShellState._markLoadedRoomNotificationsRead(roomEvents: List<RoomEventNotification>) {
    val unreadIds = roomEvents.filter { notification -> notification.isUnread }.map { notification -> notification.id }.toSet()
    if (unreadIds.isEmpty()) return
    try {
        _roomsController.markRoomNotificationsRead()
        if (!mounted) return
        _setHomeState {
            _deferredRoomNotificationVisualReadAt = DateTime.now().toUtc()
            _deferredRoomNotificationVisualReadIds = _deferredRoomNotificationVisualReadIds + unreadIds
            val pendingCount =
                pendingRoomNotificationCount(invites = _notificationInvites, applications = _notificationApplications)
            _pendingRoomNotificationCount = pendingCount
            _hasPendingRoomInvites = pendingCount > 0
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
    }
}

internal fun _HomeShellState._clearDeferredRoomNotificationVisualMarkersInState() {
    val readAt = _deferredRoomNotificationVisualReadAt
    val readIds = _deferredRoomNotificationVisualReadIds
    if (readAt == null || readIds.isEmpty()) return
    _notificationRoomEvents =
        markUnreadRoomEventNotificationsRead(
            notifications = _notificationRoomEvents,
            readAt = readAt,
            notificationIds = readIds,
        )
    _deferredRoomNotificationVisualReadAt = null
    _deferredRoomNotificationVisualReadIds = emptySet()
}

internal suspend fun _HomeShellState._reviewNotificationInvite(invite: RoomInvite, accept: Boolean) {
    if (!canReviewNotificationInvite(invite = invite, busyInviteId = _busyNotificationInviteId)) {
        return
    }

    var reason: String? = null
    var startedBeforeDialog = false
    if (accept && roomInviteAcceptRequiresApplication(invite, roomInvites = _notificationInvites)) {
        _setHomeState {
            _busyNotificationInviteId = invite.id
            _notificationError = null
        }
        startedBeforeDialog = true
        val rawReason = _showJoinApplicationDialog(invite.room)
        if (rawReason == null || !mounted) {
            if (mounted) {
                _setHomeState { _busyNotificationInviteId = null }
            }
            return
        }
        reason = joinRequestReasonValue(rawReason)
    }

    if (!startedBeforeDialog) {
        _setHomeState {
            _busyNotificationInviteId = invite.id
            _notificationError = null
        }
    }

    try {
        val result = _roomsController.reviewRoomInvite(inviteId = invite.id, accept = accept, reason = reason)
        if (!mounted) return
        _setHomeState {
            _busyNotificationInviteId = null
            val room = result.room
            if (accept && room != null) {
                _servers = _roomsController.patchRoomCardUpserted(rooms = _servers, room = room.toCard()).rooms
            }
        }
        _loadNotifications(silent = true)
        if (accept && result.joined) unawaited { _loadServersSilently() }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted) return
        _setHomeState {
            _busyNotificationInviteId = null
            _notificationError = userFacingErrorMessage(error)
        }
    }
}

internal suspend fun _HomeShellState._withdrawNotificationApplication(application: RoomApplication) {
    if (!canWithdrawNotificationApplication(application = application, busyApplicationId = _busyNotificationApplicationId)) {
        return
    }

    _setHomeState {
        _busyNotificationApplicationId = application.id
        _notificationError = null
    }

    try {
        _roomsController.withdrawRoomApplication(requestId = application.id)
        if (!mounted) return
        _setHomeState { _busyNotificationApplicationId = null }
        _loadNotifications(silent = true)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted) return
        _setHomeState {
            _busyNotificationApplicationId = null
            _notificationError = userFacingErrorMessage(error)
        }
    }
}

internal suspend fun _HomeShellState._copyNotification(item: RoomNotificationItem) {
    val text = roomNotificationCopyText(item)
    if (text.isEmpty()) {
        throw Exception("这条通知没有可复制的内容")
    }
    _clipboardService.writeText(text)
}

internal suspend fun _HomeShellState._deleteNotification(item: RoomNotificationItem) {
    _deleteNotifications(listOf(item))
}

internal suspend fun _HomeShellState._deleteNotifications(items: List<RoomNotificationItem>) {
    val uniqueItems = LinkedHashMap<String, RoomNotificationItem>().apply { for (item in items) put(item.id, item) }.values.toList()
    if (uniqueItems.isEmpty()) return
    try {
        val rooms = _roomsController
        waitAll(
            uniqueItems.map { item ->
                future {
                    rooms.deleteRoomNotification(
                        notificationType = roomNotificationDeletionType(item),
                        notificationId = roomNotificationDeletionId(item),
                    )
                }
            },
        )
    } finally {
        if (mounted) _loadNotifications(silent = true)
    }
}

internal fun _HomeShellState._applyRoomInvitesUpdated() {
    unawaited { _refreshPendingRoomInviteBadge() }
    if (_contentMode == _ContentMode.notifications) {
        unawaited { _loadNotifications(silent = true) }
    }
}

internal fun _HomeShellState._applyRoomApplicationsUpdated() {
    unawaited { _refreshPendingRoomInviteBadge() }
    if (_contentMode == _ContentMode.notifications) {
        unawaited { _loadNotifications(silent = true) }
    }
}

internal fun _HomeShellState._applyRoomNotificationsUpdated() {
    unawaited { _refreshPendingRoomInviteBadge() }
    if (_contentMode == _ContentMode.notifications) {
        unawaited { _loadNotifications(silent = true) }
    }
}
