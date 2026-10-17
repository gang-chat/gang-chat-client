package com.gangchat.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.gangchat.client.app.GlobalSearchCategory
import com.gangchat.client.app.SettingsSection
import com.gangchat.client.app.globalSearchCategories
import com.gangchat.client.app.globalSearchCursorForCategory
import com.gangchat.client.app.globalSearchCursorsForCategories
import com.gangchat.client.app.globalSearchResultsByAppendingPage
import com.gangchat.client.app.globalSearchResultsForView
import com.gangchat.client.app.hasGlobalSearchQuery
import com.gangchat.client.app.joinRequestReasonValue
import com.gangchat.client.app.publicRoomFromRoomDetail
import com.gangchat.client.app.publicRoomJoinActionable
import com.gangchat.client.app.publicRoomJoinRequiresApplication
import com.gangchat.client.app.resolvedRoomMemberProfileUser
import com.gangchat.client.app.roomCardFromSearchContext
import com.gangchat.client.app.roomJoinPublicActionFailed
import com.gangchat.client.app.roomJoinPublicActionPending
import com.gangchat.client.app.roomJoinPublicActionStarted
import com.gangchat.client.app.sidebarRoomsForSearch
import com.gangchat.client.app.superuserGlobalSearchCategories
import com.gangchat.client.app.userFacingErrorMessage
import com.gangchat.client.app.userWithLocalCommonRoomNames
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.GlobalSearchResults
import com.gangchat.client.protocol.MessageSearchResult
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.widgets.TapRegion
import com.gangchat.client.widgets.showDialog
import com.gangchat.client.widgets.startTimer
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException

// Port of lib/src/home/home_shell_search.dart.

/** Test tag of the title-bar / compact search field (Dart's `ValueKey('home-title-search')`). */
const val homeTitleSearchTag = "home-title-search"

/** Test tag of the search results region (Dart's `ValueKey('home-title-search-results')`). */
const val homeTitleSearchResultsTag = "home-title-search-results"

internal val _HomeShellState._hasSearchQuery: Boolean get() = hasGlobalSearchQuery(_searchQuery)

@Composable
internal fun _HomeShellState._buildCompactSearchField() {
    TapRegion(groupId = _searchTapRegionGroup, onTapOutside = { _collapseSearch() }) {
        Box(Modifier.fillMaxSize().testTag(homeTitleSearchTag), propagateMinConstraints = true) {
            _TitleSearchField(
                controller = _titleSearchController,
                tapRegionGroup = _searchTapRegionGroup,
                enabled = !_appUpdateDownloadInProgress,
                onContextMenuOpenChanged = { open -> _handleTitleSearchContextMenuOpenChanged(open) },
                onActivated = { _activateSearch() },
                onClearQuery = { _clearSearchQuery() },
            )
        }
    }
}

@Composable
internal fun _HomeShellState._buildSearchResultsTapRegion() {
    TapRegion(modifier = Modifier.testTag(homeTitleSearchResultsTag), groupId = _searchTapRegionGroup) {
        HoverCardTapRegionScope(tapRegionGroup = _searchTapRegionGroup) {
            _TitleSearchResultsPanel(
                query = _searchQuery,
                results = _searchResults,
                loading = _searching,
                loadingMore = _searchLoadingMore,
                error = _searchError,
                timestampNow = _serverNow,
                currentUser = widget.app.currentUser,
                activeCategory = _activeSearchCategory,
                visibleCategories = _visibleSearchCategories,
                busyPublicRoomId = _busySearchPublicRoomId,
                pendingPublicRoomIds = _searchPendingPublicRoomIds,
                onCategorySelected = { category -> _selectSearchCategory(category) },
                onLoadMore = { unawaited { _loadMoreSearchResults() } },
                onMyRoomSelected = { room -> _openSearchRoom(room) },
                onProfileRoomSelected = { room -> _openSearchProfileRoom(room) },
                onResolveRoomProfile = { room -> _resolveRoomProfile(room) },
                onResolveRoomUserProfile = { roomId, user -> _resolveRoomUserProfile(roomId, user) },
                onResolveUserProfile = { user -> _resolveUserProfile(user) },
                onPublicRoomAction = { room -> unawaited { _handlePublicRoomSearchAction(room) } },
                onUserSettingsSelected = { user -> _openSuperuserUserSettings(user) },
                onMessageSelected = { result -> _openMessageSearchResult(result) },
                onFileSelected = { result -> _openMessageSearchResult(result) },
            )
        }
    }
}

private val _HomeShellState._filteringSidebarBySearch: Boolean
    get() = _hasSearchQuery && _activeSearchCategory == GlobalSearchCategory.myRooms

internal val _HomeShellState._sidebarServers: List<RoomCard>
    get() =
        sidebarRoomsForSearch(
            rooms = _servers,
            query = _searchQuery,
            activeCategory = _activeSearchCategory,
            results = _searchResults,
        )

internal val _HomeShellState._sidebarSearchQuery: String
    get() = if (_filteringSidebarBySearch) _searchQuery else ""

internal val _HomeShellState._loadingSidebarSearch: Boolean
    get() = _filteringSidebarBySearch && _searching && _searchResults == null

private val _HomeShellState._visibleSearchCategories: List<GlobalSearchCategory>
    get() {
        val activeCategory = _activeSearchCategory
        return if (activeCategory == null) {
            if (_currentUser.isSuperuser) superuserGlobalSearchCategories else globalSearchCategories
        } else {
            listOf(activeCategory)
        }
    }

internal fun _HomeShellState._handleTitleSearchChanged() {
    val query = _titleSearchController.text
    if (query == _lastTitleSearchText) return
    _lastTitleSearchText = query
    _pendingTitleSearchContextMenuUpdate = null
    _searchDebounce?.cancel()
    _searchRequestSerial++
    val requestSerial = _searchRequestSerial
    val hasQuery = hasGlobalSearchQuery(query)

    _setHomeState {
        _searchQuery = query
        _searchExpanded = hasQuery
        _searchError = null
        _searchLoadingMore = false
        if (hasQuery) {
            _searching = true
            _searchResults = null
        } else {
            _searching = false
            _searchResults = null
        }
    }

    if (!hasQuery) return
    _searchDebounce = _scope?.startTimer(260.milliseconds) { unawaited { _runSearch(requestSerial, query) } }
}

private suspend fun _HomeShellState._runSearch(requestSerial: Int, rawQuery: String) {
    val query = rawQuery.trim()
    if (!hasGlobalSearchQuery(query)) return

    try {
        val results = _globalSearchController.search(query = query, categories = _visibleSearchCategories)
        if (!mounted || requestSerial != _searchRequestSerial) return
        if (_titleSearchController.text.trim() != query) return
        _setTitleSearchResultsState {
            _searchResults = globalSearchResultsForView(results, query = query)
            _searching = false
            _searchLoadingMore = false
            _searchError = null
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted || requestSerial != _searchRequestSerial) return
        _setTitleSearchResultsState {
            _searching = false
            _searchLoadingMore = false
            _searchError = userFacingErrorMessage(error, fallback = "搜索失败")
        }
    }
}

private suspend fun _HomeShellState._loadMoreSearchResults() {
    val current = _searchResults
    val query = _searchQuery.trim()
    if (_searchLoadingMore || _searching || current == null) return
    if (!hasGlobalSearchQuery(query)) return

    val categories =
        _visibleSearchCategories.filter { category -> globalSearchCursorForCategory(current.nextCursors, category) != null }
    if (categories.isEmpty()) return

    val requestSerial = _searchRequestSerial
    val cursors = globalSearchCursorsForCategories(current.nextCursors, categories)
    _setTitleSearchResultsState { _searchLoadingMore = true }

    try {
        val page =
            _globalSearchController.search(
                query = query,
                categories = categories,
                myRoomsCursor = cursors.myRooms,
                publicRoomsCursor = cursors.publicRooms,
                userSettingsCursor = cursors.userSettings,
                messagesCursor = cursors.messages,
                filesCursor = cursors.files,
            )
        if (!mounted || requestSerial != _searchRequestSerial) return
        if (_titleSearchController.text.trim() != query) return

        val visiblePage = globalSearchResultsForView(page, query = query)
        _setTitleSearchResultsState {
            val latest = _searchResults
            _searchLoadingMore = false
            if (latest != null) {
                _searchResults = globalSearchResultsByAppendingPage(current = latest, page = visiblePage, categories = categories)
            }
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted || requestSerial != _searchRequestSerial) return
        _setTitleSearchResultsState { _searchLoadingMore = false }
    }
}

internal fun _HomeShellState._handleTitleSearchContextMenuOpenChanged(open: Boolean) {
    _titleSearchContextMenuOpen = open
    if (open) return
    val pending = _pendingTitleSearchContextMenuUpdate
    _pendingTitleSearchContextMenuUpdate = null
    if (pending == null || !mounted) return
    _setHomeState(pending)
}

private fun _HomeShellState._setTitleSearchResultsState(update: () -> Unit) {
    if (!_titleSearchContextMenuOpen) {
        _setHomeState(update)
        return
    }
    val pending = _pendingTitleSearchContextMenuUpdate
    _pendingTitleSearchContextMenuUpdate = {
        pending?.invoke()
        update()
    }
}

internal fun _HomeShellState._activateSearch() {
    if (_searchExpanded) return
    _setHomeState { _searchExpanded = true }
}

internal fun _HomeShellState._collapseSearch() {
    if (!_searchExpanded) return
    _setHomeState { _searchExpanded = false }
}

private fun _HomeShellState._selectSearchCategory(category: GlobalSearchCategory) {
    _setHomeState { _activeSearchCategory = if (_activeSearchCategory == category) null else category }
}

internal fun _HomeShellState._clearSearchQuery() {
    _pendingTitleSearchContextMenuUpdate = null
    _titleSearchController.clear()
}

private fun _HomeShellState._openSearchRoom(room: RoomCard) {
    _selectServer(room, openContent = _compactLayout)
}

private fun _HomeShellState._openSearchProfileRoom(room: PublicRoom) {
    _openSearchRoom(_roomCardForPublicRoom(room))
}

private fun _HomeShellState._openSuperuserUserSettings(user: UserSummary) {
    if (!_currentUser.isSuperuser) return
    if (user.id == _currentUser.id) {
        _setHomeState {
            _superuserSettingsTarget = null
            _settingsOpen = true
            _settingsInitialSection = SettingsSection.profile
            _settingsInitialMusicPlaylistId = null
            _settingsAppUpdate = null
            _contentMode = _ContentMode.chat
            _narrowContentOpen = true
            _searchExpanded = false
        }
        return
    }
    _setHomeState {
        _superuserSettingsTarget = user
        _settingsOpen = false
        _contentMode = _ContentMode.superuserUserSettings
        _narrowContentOpen = true
        _searchExpanded = false
    }
}

internal fun _HomeShellState._closeSuperuserUserSettings() {
    _setHomeState {
        _superuserSettingsTarget = null
        _contentMode = _ContentMode.chat
        if (_compactLayout && _selectedServerId == null) {
            _narrowContentOpen = false
        }
    }
}

internal suspend fun _HomeShellState._resolveRoomProfile(room: PublicRoom): PublicRoom {
    val detail = _roomsController.getRoom(room.id)
    return publicRoomFromRoomDetail(detail)
}

internal suspend fun _HomeShellState._resolveRoomUserProfile(roomId: String, user: UserSummary): UserSummary {
    return try {
        val profile = _roomsController.getRoomMemberProfile(roomId = roomId, userId = user.id)
        _userWithLocalCommonRoomNames(resolvedRoomMemberProfileUser(profile = profile, fallback = user), _servers)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        _resolveUserProfile(user)
    }
}

internal suspend fun _HomeShellState._resolveUserProfile(user: UserSummary): UserSummary {
    return try {
        val profile = _roomsController.getUserProfile(user.id)
        _userWithLocalCommonRoomNames(profile.mergeMissing(user), _servers)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        _userWithLocalCommonRoomNames(user, _servers)
    }
}

private suspend fun _HomeShellState._handlePublicRoomSearchAction(room: PublicRoom) {
    val pending = room.joinState == "pending" || _searchPendingPublicRoomIds.contains(room.id)
    if (!publicRoomJoinActionable(room, pending = pending)) {
        return
    }

    if (room.joined) {
        _openSearchRoom(_roomCardForPublicRoom(room))
        return
    }

    var reason: String? = null
    var startedBeforeDialog = false
    if (publicRoomJoinRequiresApplication(room)) {
        val started = roomJoinPublicActionStarted(roomId = room.id, pendingRoomIds = _searchPendingPublicRoomIds)
        _setHomeState {
            _busySearchPublicRoomId = started.busyRoomId
            _searchError = started.error
            _searchPendingPublicRoomIds = started.pendingRoomIds
        }
        startedBeforeDialog = true
        val rawReason = _showJoinApplicationDialog(room)
        if (rawReason == null || !mounted) {
            if (mounted) {
                _setHomeState { _busySearchPublicRoomId = null }
            }
            return
        }
        reason = joinRequestReasonValue(rawReason)
    }

    if (!startedBeforeDialog) {
        val started = roomJoinPublicActionStarted(roomId = room.id, pendingRoomIds = _searchPendingPublicRoomIds)
        _setHomeState {
            _busySearchPublicRoomId = started.busyRoomId
            _searchError = started.error
            _searchPendingPublicRoomIds = started.pendingRoomIds
        }
    }

    try {
        val result = _roomsController.joinRoom(room.id, reason = reason)
        if (!mounted) return
        val joinedRoom = result.room
        if (result.joined && joinedRoom != null) {
            _applyJoinedSearchRoom(joinedRoom)
            if (!mounted) return
            _setHomeState {
                _busySearchPublicRoomId = null
                _searchResults = _searchResultsWithPublicRoomJoined(joinedRoom)
            }
            unawaited { _loadServersSilently() }
            return
        }

        val pendingPatch =
            roomJoinPublicActionPending(
                busyRoomId = null,
                error = null,
                pendingRoomIds = _searchPendingPublicRoomIds,
                room = room,
                result = result,
            )
        _setHomeState {
            _busySearchPublicRoomId = pendingPatch.busyRoomId
            _searchError = pendingPatch.error
            _searchPendingPublicRoomIds = pendingPatch.pendingRoomIds
            _hasPendingRoomInvites = true
        }
        unawaited { _refreshPendingRoomInviteBadge() }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted) return
        val failed =
            roomJoinPublicActionFailed(
                busyRoomId = null,
                pendingRoomIds = _searchPendingPublicRoomIds,
                failure = error,
            )
        _setHomeState {
            _busySearchPublicRoomId = failed.busyRoomId
            _searchError = failed.error
            _searchPendingPublicRoomIds = failed.pendingRoomIds
        }
    }
}

internal suspend fun _HomeShellState._showJoinApplicationDialog(room: PublicRoom): String? {
    if (_showingJoinApplicationDialog) return null
    _showingJoinApplicationDialog = true
    try {
        return showDialog<String>(context, useRootNavigator = true) { _ -> _JoinApplicationDialog(room = room) }
    } finally {
        _showingJoinApplicationDialog = false
    }
}

private suspend fun _HomeShellState._applyJoinedSearchRoom(room: RoomDetail) {
    val patch = _roomsController.patchRoomDetailApplied(rooms = _servers, detail = room)
    _setHomeState { _servers = patch.rooms }
    _openRoom(room.toCard(), openContent = true)
}

private fun _HomeShellState._openMessageSearchResult(result: MessageSearchResult) {
    _openSearchRoom(_roomCardForSearchResult(result))
}

private fun _HomeShellState._roomCardForSearchResult(result: MessageSearchResult): RoomCard {
    val existing = _searchServerById(result.room.id)
    if (existing != null) return existing
    return roomCardFromSearchContext(result.room, updatedAt = result.message.createdAt)
}

// Dart's `_HomeShellSearch._serverById`: also looks at the search results'
// own rooms (unlike the room-actions extension's method of the same name).
private fun _HomeShellState._searchServerById(roomId: String): RoomCard? {
    for (server in _servers) {
        if (server.id == roomId) return server
    }
    val results = _searchResults
    if (results != null) {
        for (room in results.myRooms) {
            if (room.id == roomId) return room
        }
    }
    return null
}

internal fun _HomeShellState._roomCardForPublicRoom(room: PublicRoom): RoomCard {
    val existing = _searchServerById(room.id)
    if (existing != null) return existing
    return RoomCard(
        id = room.id,
        rid = room.rid,
        name = room.name,
        visibility = room.visibility,
        description = room.description,
        avatarUrl = room.avatarUrl,
        defaultAvatarKey = room.defaultAvatarKey,
        memberCount = room.memberCount,
        onlineMemberCount = room.onlineMemberCount,
        liveParticipantCount = room.liveParticipantCount,
        liveAvatarPreview = emptyList(),
        lastMessage = null,
        unreadCount = 0,
        updatedAt = DateTime.now().toUtc(),
    )
}

private fun _HomeShellState._searchResultsWithPublicRoomJoined(room: RoomDetail): GlobalSearchResults? {
    val snapshot = _searchResults ?: return null
    return GlobalSearchResults(
        myRooms = snapshot.myRooms,
        publicRooms =
            snapshot.publicRooms.map { publicRoom ->
                if (publicRoom.id == room.id) {
                    publicRoom.copyWith(
                        joined = true,
                        joinState = "joined",
                        memberCount = room.memberCount,
                        onlineMemberCount = room.onlineMemberCount,
                        liveParticipantCount = room.live.participantCount,
                    )
                } else {
                    publicRoom
                }
            },
        userSettings = snapshot.userSettings,
        messages = snapshot.messages,
        files = snapshot.files,
        nextCursors = snapshot.nextCursors,
        totalCounts = snapshot.totalCounts,
    )
}

internal fun _userWithLocalCommonRoomNames(user: UserSummary, rooms: List<RoomCard>): UserSummary =
    userWithLocalCommonRoomNames(user = user, rooms = rooms)
