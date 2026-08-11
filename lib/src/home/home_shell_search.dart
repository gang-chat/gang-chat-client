part of 'home_shell.dart';

extension _HomeShellSearch on _HomeShellState {
  bool get _hasSearchQuery => search_display.hasGlobalSearchQuery(_searchQuery);

  Widget _buildCompactSearchField() {
    return TapRegion(
      groupId: _searchTapRegionGroup,
      onTapOutside: (_) => _collapseSearch(),
      child: SizedBox.expand(
        key: const ValueKey('home-title-search'),
        child: _TitleSearchField(
          controller: _titleSearchController,
          tapRegionGroup: _searchTapRegionGroup,
          enabled: !_appUpdateDownloadInProgress,
          onContextMenuOpenChanged: _handleTitleSearchContextMenuOpenChanged,
          onActivated: _activateSearch,
          onClearQuery: _clearSearchQuery,
        ),
      ),
    );
  }

  Widget _buildSearchResultsTapRegion() {
    return TapRegion(
      key: const ValueKey('home-title-search-results'),
      groupId: _searchTapRegionGroup,
      child: HoverCardTapRegionScope(
        tapRegionGroup: _searchTapRegionGroup,
        child: _TitleSearchResultsPanel(
          query: _searchQuery,
          results: _searchResults,
          loading: _searching,
          loadingMore: _searchLoadingMore,
          error: _searchError,
          timestampNow: _serverNow,
          currentUser: widget.app.currentUser,
          activeCategory: _activeSearchCategory,
          visibleCategories: _visibleSearchCategories,
          busyPublicRoomId: _busySearchPublicRoomId,
          pendingPublicRoomIds: _searchPendingPublicRoomIds,
          onCategorySelected: _selectSearchCategory,
          onLoadMore: () => unawaited(_loadMoreSearchResults()),
          onMyRoomSelected: _openSearchRoom,
          onProfileRoomSelected: _openSearchProfileRoom,
          onResolveRoomProfile: _resolveRoomProfile,
          onResolveRoomUserProfile: _resolveRoomUserProfile,
          onResolveUserProfile: _resolveUserProfile,
          onPublicRoomAction: (room) =>
              unawaited(_handlePublicRoomSearchAction(room)),
          onUserSettingsSelected: _openSuperuserUserSettings,
          onMessageSelected: _openMessageSearchResult,
          onFileSelected: _openMessageSearchResult,
        ),
      ),
    );
  }

  bool get _filteringSidebarBySearch =>
      _hasSearchQuery &&
      _activeSearchCategory == search_display.GlobalSearchCategory.myRooms;

  List<RoomCard> get _sidebarServers {
    return search_display.sidebarRoomsForSearch(
      rooms: _servers,
      query: _searchQuery,
      activeCategory: _activeSearchCategory,
      results: _searchResults,
    );
  }

  bool get _loadingSidebarSearch =>
      _filteringSidebarBySearch && _searching && _searchResults == null;

  List<search_display.GlobalSearchCategory> get _visibleSearchCategories {
    final activeCategory = _activeSearchCategory;
    return activeCategory == null
        ? (_currentUser.isSuperuser
              ? search_display.superuserGlobalSearchCategories
              : search_display.globalSearchCategories)
        : [activeCategory];
  }

  void _handleTitleSearchChanged() {
    final query = _titleSearchController.text;
    if (query == _lastTitleSearchText) return;
    _lastTitleSearchText = query;
    _pendingTitleSearchContextMenuUpdate = null;
    _searchDebounce?.cancel();
    _searchRequestSerial++;
    final requestSerial = _searchRequestSerial;
    final hasQuery = search_display.hasGlobalSearchQuery(query);

    _setHomeState(() {
      _searchQuery = query;
      _searchExpanded = hasQuery;
      _searchError = null;
      _searchLoadingMore = false;
      if (hasQuery) {
        _searching = true;
        _searchResults = null;
      } else {
        _searching = false;
        _searchResults = null;
      }
    });

    if (!hasQuery) return;
    _searchDebounce = Timer(const Duration(milliseconds: 260), () {
      unawaited(_runSearch(requestSerial, query));
    });
  }

  Future<void> _runSearch(int requestSerial, String rawQuery) async {
    final query = rawQuery.trim();
    if (!search_display.hasGlobalSearchQuery(query)) return;

    try {
      final results = await _globalSearchController.search(
        query: query,
        categories: _visibleSearchCategories,
      );
      if (!mounted || requestSerial != _searchRequestSerial) return;
      if (_titleSearchController.text.trim() != query) return;
      _setTitleSearchResultsState(() {
        _searchResults = search_display.globalSearchResultsForView(
          results,
          query: query,
        );
        _searching = false;
        _searchLoadingMore = false;
        _searchError = null;
      });
    } catch (error) {
      if (!mounted || requestSerial != _searchRequestSerial) return;
      _setTitleSearchResultsState(() {
        _searching = false;
        _searchLoadingMore = false;
        _searchError = userFacingErrorMessage(error, fallback: '搜索失败');
      });
    }
  }

  Future<void> _loadMoreSearchResults() async {
    final current = _searchResults;
    final query = _searchQuery.trim();
    if (_searchLoadingMore || _searching || current == null) return;
    if (!search_display.hasGlobalSearchQuery(query)) return;

    final categories = [
      for (final category in _visibleSearchCategories)
        if (search_display.globalSearchCursorForCategory(
              current.nextCursors,
              category,
            ) !=
            null)
          category,
    ];
    if (categories.isEmpty) return;

    final requestSerial = _searchRequestSerial;
    final cursors = search_display.globalSearchCursorsForCategories(
      current.nextCursors,
      categories,
    );
    _setTitleSearchResultsState(() => _searchLoadingMore = true);

    try {
      final page = await _globalSearchController.search(
        query: query,
        categories: categories,
        myRoomsCursor: cursors.myRooms,
        publicRoomsCursor: cursors.publicRooms,
        userSettingsCursor: cursors.userSettings,
        messagesCursor: cursors.messages,
        filesCursor: cursors.files,
      );
      if (!mounted || requestSerial != _searchRequestSerial) return;
      if (_titleSearchController.text.trim() != query) return;

      final visiblePage = search_display.globalSearchResultsForView(
        page,
        query: query,
      );
      _setTitleSearchResultsState(() {
        final latest = _searchResults;
        _searchLoadingMore = false;
        if (latest == null) return;
        _searchResults = search_display.globalSearchResultsByAppendingPage(
          current: latest,
          page: visiblePage,
          categories: categories,
        );
      });
    } catch (_) {
      if (!mounted || requestSerial != _searchRequestSerial) return;
      _setTitleSearchResultsState(() => _searchLoadingMore = false);
    }
  }

  void _handleTitleSearchContextMenuOpenChanged(bool open) {
    _titleSearchContextMenuOpen = open;
    if (open) return;
    final pending = _pendingTitleSearchContextMenuUpdate;
    _pendingTitleSearchContextMenuUpdate = null;
    if (pending == null || !mounted) return;
    _setHomeState(pending);
  }

  void _setTitleSearchResultsState(VoidCallback update) {
    if (!_titleSearchContextMenuOpen) {
      _setHomeState(update);
      return;
    }
    final pending = _pendingTitleSearchContextMenuUpdate;
    _pendingTitleSearchContextMenuUpdate = () {
      pending?.call();
      update();
    };
  }

  void _activateSearch() {
    if (_searchExpanded) return;
    _setHomeState(() => _searchExpanded = true);
  }

  void _collapseSearch() {
    if (!_searchExpanded) return;
    _setHomeState(() => _searchExpanded = false);
  }

  void _selectSearchCategory(search_display.GlobalSearchCategory category) {
    _setHomeState(() {
      _activeSearchCategory = _activeSearchCategory == category
          ? null
          : category;
    });
  }

  void _clearSearchQuery() {
    _pendingTitleSearchContextMenuUpdate = null;
    _titleSearchController.clear();
  }

  void _openSearchRoom(RoomCard room) {
    _selectServer(room, openContent: _compactLayout);
  }

  void _openSearchProfileRoom(PublicRoom room) {
    _openSearchRoom(_roomCardForPublicRoom(room));
  }

  void _openSuperuserUserSettings(UserSummary user) {
    if (!_currentUser.isSuperuser) return;
    if (user.id == _currentUser.id) {
      _setHomeState(() {
        _superuserSettingsTarget = null;
        _settingsOpen = true;
        _settingsInitialSection = SettingsSection.profile;
        _settingsInitialMusicPlaylistId = null;
        _settingsAppUpdate = null;
        _contentMode = _ContentMode.chat;
        _narrowContentOpen = true;
        _searchExpanded = false;
      });
      return;
    }
    _setHomeState(() {
      _superuserSettingsTarget = user;
      _settingsOpen = false;
      _contentMode = _ContentMode.superuserUserSettings;
      _narrowContentOpen = true;
      _searchExpanded = false;
    });
  }

  void _closeSuperuserUserSettings() {
    _setHomeState(() {
      _superuserSettingsTarget = null;
      _contentMode = _ContentMode.chat;
      if (_compactLayout && _selectedServerId == null) {
        _narrowContentOpen = false;
      }
    });
  }

  Future<PublicRoom> _resolveRoomProfile(PublicRoom room) async {
    final detail = await _roomsController.getRoom(room.id);
    return room_display.publicRoomFromRoomDetail(detail);
  }

  Future<UserSummary> _resolveRoomUserProfile(
    String roomId,
    UserSummary user,
  ) async {
    try {
      final profile = await _roomsController.getRoomMemberProfile(
        roomId: roomId,
        userId: user.id,
      );
      return _userWithLocalCommonRoomNames(
        room_display.resolvedRoomMemberProfileUser(
          profile: profile,
          fallback: user,
        ),
        _servers,
      );
    } catch (_) {
      return _resolveUserProfile(user);
    }
  }

  Future<UserSummary> _resolveUserProfile(UserSummary user) async {
    try {
      final profile = await _roomsController.getUserProfile(user.id);
      return _userWithLocalCommonRoomNames(
        profile.mergeMissing(user),
        _servers,
      );
    } catch (_) {
      return _userWithLocalCommonRoomNames(user, _servers);
    }
  }

  Future<void> _handlePublicRoomSearchAction(PublicRoom room) async {
    final pending =
        room.joinState == 'pending' ||
        _searchPendingPublicRoomIds.contains(room.id);
    if (!room_display.publicRoomJoinActionable(room, pending: pending)) {
      return;
    }

    if (room.joined) {
      _openSearchRoom(_roomCardForPublicRoom(room));
      return;
    }

    String? reason;
    var startedBeforeDialog = false;
    if (room_display.publicRoomJoinRequiresApplication(room)) {
      final started = room_join.roomJoinPublicActionStarted(
        roomId: room.id,
        pendingRoomIds: _searchPendingPublicRoomIds,
      );
      _setHomeState(() {
        _busySearchPublicRoomId = started.busyRoomId;
        _searchError = started.error;
        _searchPendingPublicRoomIds = started.pendingRoomIds;
      });
      startedBeforeDialog = true;
      final rawReason = await _showJoinApplicationDialog(room);
      if (rawReason == null || !mounted) {
        if (mounted) {
          _setHomeState(() => _busySearchPublicRoomId = null);
        }
        return;
      }
      reason = room_join.joinRequestReasonValue(rawReason);
    }

    if (!startedBeforeDialog) {
      final started = room_join.roomJoinPublicActionStarted(
        roomId: room.id,
        pendingRoomIds: _searchPendingPublicRoomIds,
      );
      _setHomeState(() {
        _busySearchPublicRoomId = started.busyRoomId;
        _searchError = started.error;
        _searchPendingPublicRoomIds = started.pendingRoomIds;
      });
    }

    try {
      final result = await _roomsController.joinRoom(room.id, reason: reason);
      if (!mounted) return;
      if (result.joined && result.room != null) {
        await _applyJoinedSearchRoom(result.room!);
        if (!mounted) return;
        _setHomeState(() {
          _busySearchPublicRoomId = null;
          _searchResults = _searchResultsWithPublicRoomJoined(result.room!);
        });
        unawaited(_loadServersSilently());
        return;
      }

      final pendingPatch = room_join.roomJoinPublicActionPending(
        busyRoomId: null,
        error: null,
        pendingRoomIds: _searchPendingPublicRoomIds,
        room: room,
        result: result,
      );
      _setHomeState(() {
        _busySearchPublicRoomId = pendingPatch.busyRoomId;
        _searchError = pendingPatch.error;
        _searchPendingPublicRoomIds = pendingPatch.pendingRoomIds;
        _hasPendingRoomInvites = true;
      });
      unawaited(_refreshPendingRoomInviteBadge());
    } catch (error) {
      if (!mounted) return;
      final failed = room_join.roomJoinPublicActionFailed(
        busyRoomId: null,
        pendingRoomIds: _searchPendingPublicRoomIds,
        failure: error,
      );
      _setHomeState(() {
        _busySearchPublicRoomId = failed.busyRoomId;
        _searchError = failed.error;
        _searchPendingPublicRoomIds = failed.pendingRoomIds;
      });
    }
  }

  Future<String?> _showJoinApplicationDialog(PublicRoom room) async {
    if (_showingJoinApplicationDialog) return null;
    _showingJoinApplicationDialog = true;
    try {
      return await showDialog<String>(
        context: context,
        useRootNavigator: true,
        builder: (context) => _JoinApplicationDialog(room: room),
      );
    } finally {
      _showingJoinApplicationDialog = false;
    }
  }

  Future<void> _applyJoinedSearchRoom(RoomDetail room) async {
    final patch = _roomsController.patchRoomDetailApplied(
      rooms: _servers,
      detail: room,
    );
    _setHomeState(() => _servers = patch.rooms);
    await _openRoom(room.toCard(), openContent: true);
  }

  void _openMessageSearchResult(MessageSearchResult result) {
    _openSearchRoom(_roomCardForSearchResult(result));
  }

  RoomCard _roomCardForSearchResult(MessageSearchResult result) {
    final existing = _serverById(result.room.id);
    if (existing != null) return existing;
    return search_display.roomCardFromSearchContext(
      result.room,
      updatedAt: result.message.createdAt,
    );
  }

  RoomCard? _serverById(String roomId) {
    for (final server in _servers) {
      if (server.id == roomId) return server;
    }
    final results = _searchResults;
    if (results != null) {
      for (final room in results.myRooms) {
        if (room.id == roomId) return room;
      }
    }
    return null;
  }

  RoomCard _roomCardForPublicRoom(PublicRoom room) {
    final existing = _serverById(room.id);
    if (existing != null) return existing;
    return RoomCard(
      id: room.id,
      rid: room.rid,
      name: room.name,
      visibility: room.visibility,
      description: room.description,
      avatarUrl: room.avatarUrl,
      defaultAvatarKey: room.defaultAvatarKey,
      memberCount: room.memberCount,
      onlineMemberCount: room.onlineMemberCount,
      liveParticipantCount: room.liveParticipantCount,
      liveAvatarPreview: const [],
      lastMessage: null,
      unreadCount: 0,
      updatedAt: DateTime.now().toUtc(),
    );
  }

  GlobalSearchResults? _searchResultsWithPublicRoomJoined(RoomDetail room) {
    final snapshot = _searchResults;
    if (snapshot == null) return null;
    return GlobalSearchResults(
      myRooms: snapshot.myRooms,
      publicRooms: [
        for (final publicRoom in snapshot.publicRooms)
          if (publicRoom.id == room.id)
            publicRoom.copyWith(
              joined: true,
              joinState: 'joined',
              memberCount: room.memberCount,
              onlineMemberCount: room.onlineMemberCount,
              liveParticipantCount: room.live.participantCount,
            )
          else
            publicRoom,
      ],
      userSettings: snapshot.userSettings,
      messages: snapshot.messages,
      files: snapshot.files,
      nextCursors: snapshot.nextCursors,
      totalCounts: snapshot.totalCounts,
    );
  }
}

UserSummary _userWithLocalCommonRoomNames(
  UserSummary user,
  List<RoomCard> rooms,
) {
  return room_display.userWithLocalCommonRoomNames(user: user, rooms: rooms);
}
