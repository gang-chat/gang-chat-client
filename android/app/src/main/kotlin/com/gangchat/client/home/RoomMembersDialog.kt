package com.gangchat.client.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.RoomMemberFilterCounts
import com.gangchat.client.app.RoomMemberPermissionState
import com.gangchat.client.app.RoomMemberPresenceFilter
import com.gangchat.client.app.RoomMemberRoleFilter
import com.gangchat.client.app.RoomsController
import com.gangchat.client.app.filteredRoomMembers
import com.gangchat.client.app.orderRoomMembersByUserIds
import com.gangchat.client.app.publicRoomFromRoomDetail
import com.gangchat.client.app.removeRoomBlacklistEntry
import com.gangchat.client.app.removeRoomMemberConfirmBody
import com.gangchat.client.app.removeRoomMemberConfirmLabel
import com.gangchat.client.app.removeRoomMemberConfirmTitle
import com.gangchat.client.app.replaceRoomMember
import com.gangchat.client.app.resolvedRoomMemberProfileUser
import com.gangchat.client.app.roomAccessState
import com.gangchat.client.app.roomInvitesEnabled
import com.gangchat.client.app.roomManagementPermissionState
import com.gangchat.client.app.roomMemberFilterCounts
import com.gangchat.client.app.roomMemberPermissionState
import com.gangchat.client.app.roomMemberRemoveFailed
import com.gangchat.client.app.roomMemberRemovedSucceeded
import com.gangchat.client.app.roomMemberRoleUpdateConfirmBody
import com.gangchat.client.app.roomMemberRoleUpdateConfirmLabel
import com.gangchat.client.app.roomMemberRoleUpdateConfirmTitle
import com.gangchat.client.app.roomMemberRoleUpdateNotice
import com.gangchat.client.app.roomMemberRoomDisplayNameUpdatedNotice
import com.gangchat.client.app.roomMemberWithRole
import com.gangchat.client.app.roomMembersWithCurrentUserPresence
import com.gangchat.client.app.transferCreatorConfirmBody
import com.gangchat.client.app.transferCreatorConfirmLabel
import com.gangchat.client.app.transferCreatorDialogTitle
import com.gangchat.client.app.transferCreatorSuccessNotice
import com.gangchat.client.app.upsertRoomBlacklistEntry
import com.gangchat.client.app.userFacingErrorMessage
import com.gangchat.client.app.userWithLocalCommonRoomNames
import com.gangchat.client.app.visibleRoomMembers
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.JoinRequest
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomBlacklistEntry
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.FloatingNotice
import com.gangchat.client.ui.FloatingNoticeEmitter
import com.gangchat.client.ui.FloatingNoticeTone
import com.gangchat.client.ui.Segment
import com.gangchat.client.ui.SegmentedControl
import com.gangchat.client.ui.SettingsList
import com.gangchat.client.ui.UiColors
import com.gangchat.client.widgets.CompositionTimer
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.showDialog
import com.gangchat.client.widgets.startTimer
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

internal enum class _RoomMembersSection { roomMembers, newMembers, blacklist }

// Test tags of the members panel (Dart's `ValueKey`s).
const val newMembersTabBadgeTag = "new-members-tab-badge"
const val roomMembersListTag = "room-members-list"

/**
 * The room members panel: the member list with presence/role filters and
 * moderation actions (rename in room, admin role, remove, creator transfer),
 * new members (invites and join requests) and the room blacklist.
 *
 * Embedded (the home shell) it is a page reporting through [onClose] and
 * [onChanged]; otherwise a dialog popped with whether anything changed.
 * [reloadToken] is bumped by the host when a realtime event makes the data
 * stale.
 */
@Composable
fun RoomMembersDialog(
    controller: RoomsController,
    room: RoomDetail,
    currentUser: CurrentUser,
    initialLive: LiveState,
    modifier: Modifier = Modifier,
    initialSearchQuery: String = "",
    hasPendingJoinRequests: Boolean = false,
    reloadToken: Int = 0,
    embedded: Boolean = false,
    onClose: (() -> Unit)? = null,
    onChanged: (() -> Unit)? = null,
    onPendingJoinRequestsChanged: ((Boolean) -> Unit)? = null,
    onOpenRoom: ((PublicRoom) -> Unit)? = null,
) = StatefulWidget(
    RoomMembersDialogWidget(
        controller, room, currentUser, initialLive, initialSearchQuery, hasPendingJoinRequests, reloadToken, embedded,
        onClose, onChanged, onPendingJoinRequestsChanged, onOpenRoom, modifier,
    ),
) { _RoomMembersDialogState() }

class RoomMembersDialogWidget(
    val controller: RoomsController,
    val room: RoomDetail,
    val currentUser: CurrentUser,
    val initialLive: LiveState,
    val initialSearchQuery: String,
    val hasPendingJoinRequests: Boolean,
    /**
     * Incremented by the host when a realtime event (join requests updated, role
     * changed) means this panel's data is stale. A change triggers a reload.
     */
    val reloadToken: Int,
    val embedded: Boolean,
    val onClose: (() -> Unit)?,
    val onChanged: (() -> Unit)?,
    val onPendingJoinRequestsChanged: ((Boolean) -> Unit)?,
    val onOpenRoom: ((PublicRoom) -> Unit)?,
    val modifier: Modifier,
)

private class _RoomMembersDialogState : ViewState<RoomMembersDialogWidget>() {
    private val _memberSearchController = TextEditingController()
    private val _inviteSearchController = TextEditingController()
    private val _blockSearchController = TextEditingController()
    private val _busyRequestIds = LinkedHashSet<String>()
    private val _busyMemberIds = LinkedHashSet<String>()
    private val _busyInviteUserIds = LinkedHashSet<String>()
    private val _busyBlockUserIds = LinkedHashSet<String>()
    private val _pendingInviteUserIds = LinkedHashSet<String>()

    private var _inviteSearchDebounce: CompositionTimer? = null
    private var _blockSearchDebounce: CompositionTimer? = null
    private var _inviteSearchSeq = 0
    private var _blockSearchSeq = 0
    private var _scope: CoroutineScope? = null

    private lateinit var _room: RoomDetail
    private lateinit var _live: LiveState
    private var _members: List<RoomMember> = emptyList()
    private var _memberDisplayOrder: List<String> = emptyList()
    private var _requests: List<JoinRequest> = emptyList()
    private var _inviteResults: List<UserSummary> = emptyList()
    private var _blockResults: List<UserSummary> = emptyList()
    private var _blacklist: List<RoomBlacklistEntry> = emptyList()

    private var _loading = true
    private var _searchingInvites = false
    private var _searchingBlocks = false
    private var _loadingBlacklist = false
    private var _blacklistLoaded = false
    private var _changed = false
    private var _memberQuery = ""
    private var _inviteQuery = ""
    private var _blockQuery = ""
    private var _lastMemberSearchText = ""
    private var _lastInviteSearchText = ""
    private var _lastBlockSearchText = ""
    private var _error: String? = null
    private var _requestError: String? = null
    private var _inviteError: String? = null
    private var _blockError: String? = null
    private var _blacklistError: String? = null
    private var _notice: String? = null
    private var _floatingNoticeSerial = 0
    private val _floatingNoticeEventKeys = HashMap<String, Int>()
    private var _activeJoinRequestDetailId: String? = null
    private var _presenceFilter = RoomMemberPresenceFilter.all
    private var _roleFilter = RoomMemberRoleFilter.all
    private var _section = _RoomMembersSection.roomMembers
    private var _syncingMemberSearch = false

    private val _canReviewRequests: Boolean
        get() = roomAccessState(room = _room, currentUser = widget.currentUser).canReviewJoinRequests

    private val _canEditCreatorOnly: Boolean
        get() = roomManagementPermissionState(room = _room, currentUser = widget.currentUser).canEditCreatorOnly

    private val _canInviteMembers: Boolean get() = _canViewNewMembers

    private val _canViewNewMembers: Boolean get() = roomInvitesEnabled(_room.joinPolicy)

    private val _canViewJoinRequests: Boolean get() = _canViewNewMembers && _canReviewRequests

    private val _canManageMembers: Boolean get() = roomAccessState(room = _room, currentUser = widget.currentUser).canManageRoom

    private val _canViewBlacklist: Boolean get() = _canManageMembers

    private val _onMemberSearchChanged: () -> Unit = {
        if (!_syncingMemberSearch) {
            val text = _memberSearchController.text
            if (text != _lastMemberSearchText) {
                _lastMemberSearchText = text
                setState {
                    _memberQuery = text
                    _refreshMemberDisplayOrder()
                }
            }
        }
    }

    private val _onInviteSearchChanged: () -> Unit = { _handleInviteSearchChanged() }

    private val _onBlockSearchChanged: () -> Unit = { _handleBlockSearchChanged() }

    override fun initState() {
        _room = widget.room
        _live = widget.initialLive
        _applyMemberSearchQuery(widget.initialSearchQuery)
        _memberSearchController.addListener(_onMemberSearchChanged)
        _inviteSearchController.addListener(_onInviteSearchChanged)
        _blockSearchController.addListener(_onBlockSearchChanged)
        unawaited { _load() }
    }

    override fun dispose() {
        _inviteSearchDebounce?.cancel()
        _blockSearchDebounce?.cancel()
        _memberSearchController.removeListener(_onMemberSearchChanged)
        _inviteSearchController.removeListener(_onInviteSearchChanged)
        _blockSearchController.removeListener(_onBlockSearchChanged)
        _memberSearchController.dispose()
        _inviteSearchController.dispose()
        _blockSearchController.dispose()
    }

    override fun didUpdateWidget(oldWidget: RoomMembersDialogWidget) {
        val liveChanged = widget.initialLive !== oldWidget.initialLive
        val liveBelongsToRoom = widget.initialLive.roomId == widget.room.id
        if (widget.initialSearchQuery != oldWidget.initialSearchQuery) {
            setState {
                _applyMemberSearchQuery(widget.initialSearchQuery)
                _refreshMemberDisplayOrder()
                _section = _RoomMembersSection.roomMembers
            }
        }
        // The host bumps reloadToken when a realtime event invalidates this panel
        // (join requests changed, or the current user's role changed). Re-pull the
        // member/request lists, and adopt the freshest room so permission checks
        // (canReviewRequests, canManageMembers) reflect the new role.
        if (widget.reloadToken != oldWidget.reloadToken || widget.room !== oldWidget.room) {
            val roomChanged = widget.room.id != oldWidget.room.id
            _room = widget.room
            if (roomChanged) {
                _resetBlacklistState()
                _memberDisplayOrder = emptyList()
            }
            if (liveChanged && liveBelongsToRoom) _live = widget.initialLive
            unawaited { _load(refreshDisplayOrder = roomChanged) }
            if (_section == _RoomMembersSection.blacklist) unawaited { _loadBlacklist() }
        } else if (liveChanged && liveBelongsToRoom) {
            setState { _live = widget.initialLive }
        }
    }

    private fun _close() {
        if (widget.embedded) {
            if (_changed) widget.onChanged?.invoke()
            widget.onClose?.invoke()
            return
        }
        Navigator.of(context).pop(_changed)
    }

    private fun _selectSection(section: _RoomMembersSection) {
        if (section == _RoomMembersSection.newMembers && !_canViewNewMembers) return
        if (section == _RoomMembersSection.blacklist && !_canViewBlacklist) return
        setState { _section = section }
        if (section == _RoomMembersSection.blacklist && !_blacklistLoaded) unawaited { _loadBlacklist() }
    }

    private fun _resetBlacklistState() {
        _blockSearchDebounce?.cancel()
        _blockSearchSeq += 1
        _blockResults = emptyList()
        _blacklist = emptyList()
        _searchingBlocks = false
        _loadingBlacklist = false
        _blacklistLoaded = false
        _blockError = null
        _blacklistError = null
    }

    private fun _applyMemberSearchQuery(query: String) {
        val value = query.trim()
        _memberQuery = value
        _lastMemberSearchText = value
        if (_memberSearchController.text == value) return
        _syncingMemberSearch = true
        _memberSearchController.value = TextFieldValue(text = value, selection = TextRange(value.length))
        _syncingMemberSearch = false
    }

    private fun _handleInviteSearchChanged() {
        val text = _inviteSearchController.text
        if (text == _lastInviteSearchText) return
        _lastInviteSearchText = text
        if (!_canInviteMembers) return
        val query = text.trim()
        setState {
            _inviteQuery = query
            _inviteError = null
            if (query.isEmpty()) {
                _inviteResults = emptyList()
                _searchingInvites = false
            }
        }
        _inviteSearchDebounce?.cancel()
        if (query.length < 2) {
            _inviteSearchSeq += 1
            return
        }
        val seq = ++_inviteSearchSeq
        _inviteSearchDebounce = _scope?.startTimer(260.milliseconds) { unawaited { _searchInviteCandidates(query, seq) } }
    }

    private fun _handleBlockSearchChanged() {
        val text = _blockSearchController.text
        if (text == _lastBlockSearchText) return
        _lastBlockSearchText = text
        if (!_canManageMembers) return
        val query = text.trim()
        setState {
            _blockQuery = query
            _blockError = null
            if (query.isEmpty()) {
                _blockResults = emptyList()
                _searchingBlocks = false
            }
        }
        _blockSearchDebounce?.cancel()
        if (query.length < 2) {
            _blockSearchSeq += 1
            return
        }
        val seq = ++_blockSearchSeq
        _blockSearchDebounce = _scope?.startTimer(260.milliseconds) { unawaited { _searchBlockCandidates(query, seq) } }
    }

    private suspend fun _load(refreshDisplayOrder: Boolean = true) {
        setState {
            _loading = true
            _error = null
            _requestError = null
        }
        try {
            val snapshot =
                widget.controller.loadRoomMembersSnapshot(
                    roomId = _room.id,
                    fallbackLive = _live,
                    includeJoinRequests = _canViewJoinRequests,
                )
            if (!mounted) return
            setState {
                _members = snapshot.members
                _live = snapshot.live
                if (refreshDisplayOrder || _memberDisplayOrder.isEmpty()) _refreshMemberDisplayOrder()
                _requests = snapshot.joinRequests
                _requestError = snapshot.joinRequestsError
                _loading = false
            }
            _notifyPendingJoinRequestsChanged(snapshot.joinRequests, snapshot.joinRequestsError)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState {
                _loading = false
                _error = userFacingErrorMessage(error)
                _markFloatingNoticeEvent("error", _error)
            }
        }
    }

    private suspend fun _loadBlacklist() {
        if (!_canManageMembers) return
        if (_loadingBlacklist) return
        setState {
            _loadingBlacklist = true
            _blacklistError = null
        }
        try {
            val entries = widget.controller.listRoomBlacklist(_room.id)
            if (!mounted) return
            setState {
                _blacklist = entries
                _blacklistLoaded = true
                _loadingBlacklist = false
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState {
                _loadingBlacklist = false
                _blacklistError = userFacingErrorMessage(error)
            }
        }
    }

    private suspend fun _searchInviteCandidates(query: String, seq: Int) {
        if (!_canInviteMembers) return
        setState {
            _searchingInvites = true
            _inviteError = null
        }
        try {
            val results = widget.controller.searchUsers(query = query, limit = 20)
            if (!mounted || seq != _inviteSearchSeq) return
            setState {
                _inviteResults = results
                _searchingInvites = false
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted || seq != _inviteSearchSeq) return
            setState {
                _searchingInvites = false
                _inviteError = userFacingErrorMessage(error)
            }
        }
    }

    private suspend fun _searchBlockCandidates(query: String, seq: Int) {
        if (!_canManageMembers) return
        setState {
            _searchingBlocks = true
            _blockError = null
        }
        try {
            val results = widget.controller.searchUsers(query = query, limit = 20)
            if (!mounted || seq != _blockSearchSeq) return
            setState {
                _blockResults = results
                _searchingBlocks = false
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted || seq != _blockSearchSeq) return
            setState {
                _searchingBlocks = false
                _blockError = userFacingErrorMessage(error)
            }
        }
    }

    private suspend fun _invite(user: UserSummary) {
        if (!_canInviteMembers) return
        if (_busyInviteUserIds.contains(user.id)) return
        if (_pendingInviteUserIds.contains(user.id)) return
        if (_members.any { member -> member.user.id == user.id }) return
        setState {
            _busyInviteUserIds.add(user.id)
            _inviteError = null
            _notice = null
        }
        try {
            widget.controller.inviteMember(roomId = _room.id, userId = user.id)
            if (!mounted) return
            setState {
                _busyInviteUserIds.remove(user.id)
                _pendingInviteUserIds.add(user.id)
                _changed = true
                _notice = "邀请已发送"
                _markFloatingNoticeEvent("notice", _notice)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState {
                _busyInviteUserIds.remove(user.id)
                _inviteError = userFacingErrorMessage(error)
            }
        }
    }

    private suspend fun _blockUser(user: UserSummary) {
        if (!_canManageMembers) return
        if (_busyBlockUserIds.contains(user.id)) return
        if (_blacklist.any { entry -> entry.user.id == user.id }) return
        if (_members.any { member -> member.user.id == user.id }) return
        if (user.isSuperuser) return
        setState {
            _busyBlockUserIds.add(user.id)
            _blockError = null
            _blacklistError = null
            _notice = null
        }
        try {
            val entry = widget.controller.blockRoomUser(roomId = _room.id, userId = user.id)
            if (!mounted) return
            setState {
                _busyBlockUserIds.remove(user.id)
                _blacklist = upsertRoomBlacklistEntry(_blacklist, entry)
                _blacklistLoaded = true
                _changed = true
                _notice = "已加入黑名单"
                _markFloatingNoticeEvent("notice", _notice)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState {
                _busyBlockUserIds.remove(user.id)
                _blockError = userFacingErrorMessage(error)
            }
        }
    }

    private suspend fun _unblockUser(user: UserSummary) {
        if (!_canManageMembers) return
        if (_busyBlockUserIds.contains(user.id)) return
        setState {
            _busyBlockUserIds.add(user.id)
            _blockError = null
            _blacklistError = null
            _notice = null
        }
        try {
            widget.controller.unblockRoomUser(roomId = _room.id, userId = user.id)
            if (!mounted) return
            setState {
                _busyBlockUserIds.remove(user.id)
                _blacklist = removeRoomBlacklistEntry(_blacklist, user.id)
                _blacklistLoaded = true
                _changed = true
                _notice = "已取消拉黑"
                _markFloatingNoticeEvent("notice", _notice)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState {
                _busyBlockUserIds.remove(user.id)
                _blacklistError = userFacingErrorMessage(error)
            }
        }
    }

    private suspend fun _reviewRequest(request: JoinRequest, approve: Boolean) {
        if (_busyRequestIds.contains(request.id)) return
        setState {
            _busyRequestIds.add(request.id)
            _requestError = null
            _notice = null
        }
        try {
            widget.controller.reviewJoinRequest(roomId = _room.id, requestId = request.id, approve = approve)
            if (!mounted) return
            setState {
                _busyRequestIds.remove(request.id)
                _requests = _requests.filter { item -> item.id != request.id }
                _changed = true
                _notice = if (approve) "申请已通过" else "申请已拒绝"
                _markFloatingNoticeEvent("notice", _notice)
            }
            _notifyPendingJoinRequestsChanged(_requests, _requestError)
            if (approve) unawaited { _load(refreshDisplayOrder = false) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState {
                _busyRequestIds.remove(request.id)
                _requestError = userFacingErrorMessage(error)
            }
        }
    }

    private fun _notifyPendingJoinRequestsChanged(requests: List<JoinRequest>, requestError: String?) {
        if (!_canViewJoinRequests || requestError != null) return
        widget.onPendingJoinRequestsChanged?.invoke(requests.isNotEmpty())
    }

    private fun _markFloatingNoticeEvent(channel: String, message: String?) {
        if (message == null || message.trim().isEmpty()) return
        _floatingNoticeEventKeys[channel] = ++_floatingNoticeSerial
    }

    private fun _floatingNoticeEventKey(channel: String): Any? = _floatingNoticeEventKeys[channel]

    private fun _floatingNotices(): List<FloatingNotice> =
        buildList {
            val notice = _notice
            if (notice != null) {
                add(FloatingNotice(message = notice, tone = FloatingNoticeTone.success, eventKey = _floatingNoticeEventKey("notice")))
            }
            val error = _error
            if (error != null) {
                add(FloatingNotice(message = error, tone = FloatingNoticeTone.error, duration = null, eventKey = _floatingNoticeEventKey("error")))
            }
        }

    private suspend fun _showJoinRequestDetails(request: JoinRequest) {
        setState { _activeJoinRequestDetailId = request.id }
        try {
            showDialog<Unit>(context) { _ ->
                _JoinRequestDetailsDialog(
                    request = request,
                    currentUser = widget.currentUser,
                    onResolveProfile = ::_resolveMemberProfile,
                    onResolveRoomProfile = ::_resolveRoomProfile,
                    onOpenRoom = widget.onOpenRoom,
                )
            }
        } finally {
            if (mounted) setState { _activeJoinRequestDetailId = null }
        }
    }

    private suspend fun _setMemberRole(member: RoomMember, role: String) {
        if (_busyMemberIds.contains(member.user.id)) return
        val confirmed =
            showDialog<Boolean>(context) { _ ->
                _ConfirmDialog(
                    title = roomMemberRoleUpdateConfirmTitle(role),
                    message = roomMemberRoleUpdateConfirmBody(member, role),
                    confirmLabel = roomMemberRoleUpdateConfirmLabel(role),
                    danger = role != "admin",
                )
            }
        if (confirmed != true || !mounted) return

        setState {
            _busyMemberIds.add(member.user.id)
            _error = null
            _notice = null
        }
        try {
            val updated = widget.controller.updateRoomMemberRole(roomId = _room.id, userId = member.user.id, role = role)
            if (!mounted) return
            setState {
                _busyMemberIds.remove(member.user.id)
                _members = replaceRoomMember(_members, updated)
                _changed = true
                _notice = roomMemberRoleUpdateNotice(role)
                _markFloatingNoticeEvent("notice", _notice)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState {
                _busyMemberIds.remove(member.user.id)
                _error = userFacingErrorMessage(error)
                _markFloatingNoticeEvent("error", _error)
            }
        }
    }

    private suspend fun _editRoomDisplayName(member: RoomMember) {
        if (_busyMemberIds.contains(member.user.id)) return
        val roomDisplayName = showDialog<String>(context) { _ -> _EditRoomDisplayNameDialog(member = member) }
        if (roomDisplayName == null || !mounted) return

        setState {
            _busyMemberIds.add(member.user.id)
            _error = null
            _notice = null
        }
        try {
            val updated =
                widget.controller.updateRoomMemberRoomDisplayName(
                    roomId = _room.id,
                    userId = member.user.id,
                    roomDisplayName = roomDisplayName,
                )
            if (!mounted) return
            setState {
                _busyMemberIds.remove(member.user.id)
                _members = replaceRoomMember(_members, updated)
                _changed = true
                _notice = roomMemberRoomDisplayNameUpdatedNotice(updated)
                _markFloatingNoticeEvent("notice", _notice)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState {
                _busyMemberIds.remove(member.user.id)
                _error = userFacingErrorMessage(error)
                _markFloatingNoticeEvent("error", _error)
            }
        }
    }

    private suspend fun _transferCreator(member: RoomMember) {
        if (_busyMemberIds.contains(member.user.id)) return
        val confirmed =
            showDialog<Boolean>(context) { _ ->
                _ConfirmDialog(
                    title = transferCreatorDialogTitle(),
                    message = transferCreatorConfirmBody(member, currentUserIsCreator = _room.isCreator),
                    confirmLabel = transferCreatorConfirmLabel(),
                    danger = true,
                )
            }
        if (confirmed != true || !mounted) return

        setState {
            _busyMemberIds.add(member.user.id)
            _error = null
            _notice = null
        }
        try {
            val previousOwnerId = _room.createdBy?.id
            val updated = widget.controller.transferRoomCreator(roomId = _room.id, userId = member.user.id)
            if (!mounted) return
            setState {
                _busyMemberIds.remove(member.user.id)
                _room = updated
                _members = _membersAfterCreatorTransfer(previousOwnerId = previousOwnerId, newOwnerId = member.user.id)
                _changed = true
                _notice = transferCreatorSuccessNotice()
                _markFloatingNoticeEvent("notice", _notice)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState {
                _busyMemberIds.remove(member.user.id)
                _error = userFacingErrorMessage(error)
                _markFloatingNoticeEvent("error", _error)
            }
        }
    }

    private suspend fun _removeMember(member: RoomMember) {
        if (_busyMemberIds.contains(member.user.id)) return
        val confirmed =
            showDialog<Boolean>(context) { _ ->
                _ConfirmDialog(
                    title = removeRoomMemberConfirmTitle(),
                    message = removeRoomMemberConfirmBody(member),
                    confirmLabel = removeRoomMemberConfirmLabel(),
                    danger = true,
                )
            }
        if (confirmed != true || !mounted) return

        setState {
            _busyMemberIds.add(member.user.id)
            _error = null
            _notice = null
        }
        try {
            widget.controller.removeRoomMember(roomId = _room.id, userId = member.user.id)
            if (!mounted) return
            val patch =
                roomMemberRemovedSucceeded(room = _room, members = _members, removed = member, busyMemberIds = _busyMemberIds)
            setState {
                _busyMemberIds.clear()
                _busyMemberIds.addAll(patch.busyMemberIds)
                _members = patch.members
                _changed = patch.changed
                _error = patch.error
                _notice = patch.notice
                _markFloatingNoticeEvent("error", _error)
                _markFloatingNoticeEvent("notice", _notice)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            val patch =
                roomMemberRemoveFailed(
                    room = _room,
                    members = _members,
                    changed = _changed,
                    userId = member.user.id,
                    busyMemberIds = _busyMemberIds,
                    failure = error,
                )
            setState {
                _busyMemberIds.clear()
                _busyMemberIds.addAll(patch.busyMemberIds)
                _error = patch.error
                _notice = patch.notice
                _markFloatingNoticeEvent("error", _error)
                _markFloatingNoticeEvent("notice", _notice)
            }
        }
    }

    private suspend fun _resolveMemberProfile(user: UserSummary): UserSummary =
        try {
            val profile = widget.controller.getRoomMemberProfile(roomId = _room.id, userId = user.id)
            _userWithCurrentRoomCommonRoomName(resolvedRoomMemberProfileUser(profile = profile, fallback = user))
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _resolveUserProfile(user)
        }

    private suspend fun _resolveUserProfile(user: UserSummary): UserSummary =
        try {
            val profile = widget.controller.getUserProfile(user.id)
            _userWithCurrentRoomCommonRoomName(profile.mergeMissing(user))
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _userWithCurrentRoomCommonRoomName(user)
        }

    private fun _userWithCurrentRoomCommonRoomName(user: UserSummary): UserSummary =
        userWithLocalCommonRoomNames(user = user, rooms = listOf(_room.toCard()))

    private suspend fun _resolveRoomProfile(room: PublicRoom): PublicRoom {
        val detail = widget.controller.getRoom(room.id)
        return publicRoomFromRoomDetail(detail)
    }

    private fun _visibleMembers(): List<RoomMember> {
        val visible =
            filteredRoomMembers(
                members = _effectiveMembers,
                live = _live,
                presenceFilter = _presenceFilter,
                roleFilter = _roleFilter,
                query = _memberQuery,
                ownerUserId = _room.createdBy?.id,
            )
        return orderRoomMembersByUserIds(members = visible, orderedUserIds = _memberDisplayOrder)
    }

    private val _effectiveMembers: List<RoomMember>
        get() = roomMembersWithCurrentUserPresence(_members, currentUserId = widget.currentUser.id)

    private fun _refreshMemberDisplayOrder() {
        _memberDisplayOrder =
            visibleRoomMembers(
                members = _effectiveMembers,
                live = _live,
                presenceFilter = _presenceFilter,
                roleFilter = _roleFilter,
                query = _memberQuery,
                ownerUserId = _room.createdBy?.id,
            ).map { member -> member.user.id }
    }

    private fun _membersAfterCreatorTransfer(previousOwnerId: String?, newOwnerId: String): List<RoomMember> =
        _members.map { member ->
            when {
                member.user.id == newOwnerId -> roomMemberWithRole(member, "owner")
                previousOwnerId != null && previousOwnerId != newOwnerId && member.user.id == previousOwnerId ->
                    roomMemberWithRole(member, "admin")
                else -> member
            }
        }

    @Composable
    override fun build() {
        _scope = rememberCoroutineScope()
        val effectiveMembers = _effectiveMembers
        val filterCounts = roomMemberFilterCounts(members = effectiveMembers, live = _live, ownerUserId = _room.createdBy?.id)
        var activeSection = _section
        if (activeSection == _RoomMembersSection.newMembers && !_canViewNewMembers) activeSection = _RoomMembersSection.roomMembers
        if (activeSection == _RoomMembersSection.blacklist && !_canViewBlacklist) activeSection = _RoomMembersSection.roomMembers
        val showJoinRequestsBadge = _canViewJoinRequests && (widget.hasPendingJoinRequests || _requests.isNotEmpty())
        val sections =
            buildList {
                add(Segment(value = _RoomMembersSection.roomMembers, label = "房间成员", icon = Icons.Outlined.Groups))
                if (_canViewNewMembers) {
                    add(
                        Segment(
                            value = _RoomMembersSection.newMembers,
                            label = "新成员",
                            icon = Icons.Filled.PersonAddAlt1,
                            showBadge = showJoinRequestsBadge,
                            badgeKey = newMembersTabBadgeTag,
                        ),
                    )
                }
                if (_canViewBlacklist) add(Segment(value = _RoomMembersSection.blacklist, label = "黑名单", icon = Icons.Filled.Block))
            }
        val section = activeSection
        FloatingNoticeEmitter(notices = _floatingNotices()) {
            _RoomDialogShell(
                modifier = widget.modifier,
                title = "成员",
                icon = Icons.Outlined.Group,
                maxWidth = _dialogMaxWidth,
                maxHeight = _dialogMaxHeight,
                embedded = widget.embedded,
                onClose = ::_close,
                pinned =
                    if (sections.size > 1) {
                        { SegmentedControl(expanded = true, value = section, onChanged = ::_selectSection, segments = sections) }
                    } else {
                        null
                    },
                headerAction =
                    if (_canReviewRequests || (section == _RoomMembersSection.blacklist && _canManageMembers)) {
                        {
                            ButtonIcon(
                                tooltip = "刷新",
                                icon = { Icon(Icons.Filled.Refresh) },
                                onPressed = {
                                    if (section == _RoomMembersSection.blacklist) unawaited { _loadBlacklist() } else unawaited { _load() }
                                },
                                size = 38.dp,
                            )
                        }
                    } else {
                        null
                    },
            ) {
                when (section) {
                    _RoomMembersSection.roomMembers -> _buildRoomMembersBody(filterCounts)
                    _RoomMembersSection.newMembers -> _buildNewMembersBody(effectiveMembers)
                    _RoomMembersSection.blacklist -> _buildBlacklistBody(effectiveMembers)
                }
            }
        }
    }

    @Composable
    private fun _buildRoomMembersBody(filterCounts: RoomMemberFilterCounts) {
        Column(Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, bottom = 22.dp)) {
            _MemberFilters(
                controller = _memberSearchController,
                filterCounts = filterCounts,
                presenceFilter = _presenceFilter,
                roleFilter = _roleFilter,
                onPresenceChanged = { value ->
                    setState {
                        _presenceFilter = value
                        _refreshMemberDisplayOrder()
                    }
                },
                onRoleChanged = { value ->
                    setState {
                        _roleFilter = value
                        _refreshMemberDisplayOrder()
                    }
                },
            )
            Spacer(Modifier.height(14.dp))
            Box(Modifier.weight(1f).fillMaxWidth().testTag(roomMembersListTag), propagateMinConstraints = true) {
                _buildMemberList()
            }
        }
    }

    @Composable
    private fun _buildNewMembersBody(effectiveMembers: List<RoomMember>) {
        SettingsList(
            children =
                buildList {
                    add {
                        _InviteSection(
                            controller = _inviteSearchController,
                            currentUser = widget.currentUser,
                            query = _inviteQuery,
                            searching = _searchingInvites,
                            results = _inviteResults,
                            members = effectiveMembers,
                            pendingInviteUserIds = _pendingInviteUserIds.toSet(),
                            busyUserIds = _busyInviteUserIds.toSet(),
                            error = _inviteError,
                            enabled = _canInviteMembers,
                            onResolveProfile = ::_resolveUserProfile,
                            onResolveRoomProfile = ::_resolveRoomProfile,
                            onOpenRoom = widget.onOpenRoom,
                            onInvite = { user -> unawaited { _invite(user) } },
                        )
                    }
                    if (_canViewJoinRequests) {
                        add {
                            _JoinRequestsSection(
                                requests = _requests,
                                currentUser = widget.currentUser,
                                busyRequestIds = _busyRequestIds.toSet(),
                                activeDetailRequestId = _activeJoinRequestDetailId,
                                error = _requestError,
                                onResolveProfile = ::_resolveMemberProfile,
                                onResolveRoomProfile = ::_resolveRoomProfile,
                                onOpenRoom = widget.onOpenRoom,
                                onDetail = { request -> unawaited { _showJoinRequestDetails(request) } },
                                onApprove = { request -> unawaited { _reviewRequest(request, true) } },
                                onReject = { request -> unawaited { _reviewRequest(request, false) } },
                            )
                        }
                    }
                },
        )
    }

    @Composable
    private fun _buildBlacklistBody(effectiveMembers: List<RoomMember>) {
        SettingsList(
            children =
                listOf(
                    {
                        _BlockUserSection(
                            controller = _blockSearchController,
                            currentUser = widget.currentUser,
                            query = _blockQuery,
                            searching = _searchingBlocks,
                            results = _blockResults,
                            members = effectiveMembers,
                            blacklist = _blacklist,
                            busyUserIds = _busyBlockUserIds.toSet(),
                            error = _blockError,
                            enabled = _canManageMembers,
                            onResolveProfile = ::_resolveUserProfile,
                            onResolveRoomProfile = ::_resolveRoomProfile,
                            onOpenRoom = widget.onOpenRoom,
                            onBlock = { user -> unawaited { _blockUser(user) } },
                            onUnblock = { user -> unawaited { _unblockUser(user) } },
                        )
                    },
                    {
                        _BlacklistSection(
                            entries = _blacklist,
                            currentUser = widget.currentUser,
                            busyUserIds = _busyBlockUserIds.toSet(),
                            loading = _loadingBlacklist,
                            error = _blacklistError,
                            onResolveProfile = ::_resolveUserProfile,
                            onResolveRoomProfile = ::_resolveRoomProfile,
                            onOpenRoom = widget.onOpenRoom,
                            onUnblock = { user -> unawaited { _unblockUser(user) } },
                        )
                    },
                ),
        )
    }

    @Composable
    private fun _buildMemberList() {
        if (_loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = UiColors.accent,
                    strokeWidth = 2.2.dp,
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Square,
                )
            }
            return
        }
        val members = _visibleMembers()
        if (members.isEmpty()) {
            _EmptyState(icon = Icons.Outlined.PersonSearch, title = "没有匹配的成员")
            return
        }

        fun permissionFor(member: RoomMember): RoomMemberPermissionState =
            roomMemberPermissionState(
                member = member,
                currentUser = widget.currentUser,
                canEditCreatorOnly = _canEditCreatorOnly,
                canManageMembers = _canManageMembers,
                ownerUserId = _room.createdBy?.id,
            )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rowSurfaceHorizontalPadding = 20.dp
            val rowContentWidth =
                if (constraints.hasBoundedWidth) (maxWidth - rowSurfaceHorizontalPadding).coerceAtLeast(0.dp) else androidx.compose.ui.unit.Dp.Infinity
            val stackMemberActions =
                HomeAdaptiveLayout.usesCompactLayout(context) ||
                    members.any { member ->
                        _memberRowNeedsStackedActions(
                            context = context,
                            availableWidth = rowContentWidth,
                            member = member,
                            live = _live,
                            permission = permissionFor(member),
                            ownerUserId = _room.createdBy?.id,
                        )
                    }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(members, key = { member -> member.user.id }) { member ->
                    val permission = permissionFor(member)
                    val busy = _busyMemberIds.contains(member.user.id)
                    val canMoveActions = _memberActionCount(permission) > 0
                    _MemberRow(
                        member = member,
                        currentUser = widget.currentUser,
                        live = _live,
                        permission = permission,
                        stackActions = stackMemberActions,
                        nameMaxLines =
                            if ((stackMemberActions || !canMoveActions) &&
                                _memberRowNeedsTwoLineName(
                                    context = context,
                                    availableWidth = rowContentWidth,
                                    member = member,
                                    live = _live,
                                    ownerUserId = _room.createdBy?.id,
                                    busy = busy,
                                )
                            ) {
                                2
                            } else {
                                1
                            },
                        ownerUserId = _room.createdBy?.id,
                        query = _memberQuery,
                        busy = busy,
                        onResolveProfile = ::_resolveMemberProfile,
                        onResolveRoomProfile = ::_resolveRoomProfile,
                        onOpenRoom = widget.onOpenRoom,
                        onEditRoomDisplayName = { unawaited { _editRoomDisplayName(member) } },
                        onSetAdmin = { unawaited { _setMemberRole(member, "admin") } },
                        onUnsetAdmin = { unawaited { _setMemberRole(member, "member") } },
                        onRemoveMember = { unawaited { _removeMember(member) } },
                        onTransferCreator = { unawaited { _transferCreator(member) } },
                    )
                }
            }
        }
    }
}
