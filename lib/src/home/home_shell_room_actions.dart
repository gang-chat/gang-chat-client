part of 'home_shell.dart';

extension _HomeShellRoomActions on _HomeShellState {
  /// Fetches the full profile for a message sender so the hover card can show
  /// fields the lightweight message summary omits (gender, common rooms). Falls
  /// back to the summary we already have when the room context or request is
  /// unavailable.
  Future<UserSummary> _resolveSenderProfile(UserSummary sender) async {
    final room = _selectedRoom;
    if (room == null) return sender;
    var resolved = sender;
    try {
      final profile = await _roomsController.getRoomMemberProfile(
        roomId: room.id,
        userId: sender.id,
      );
      resolved = room_display.resolvedRoomMemberProfileUser(
        profile: profile,
        fallback: sender,
      );
    } catch (_) {
      resolved = await _resolveUserProfile(sender);
    }
    final withRole = room_display.roomUserInfoProfile(
      user: resolved,
      room: room,
      currentUser: _currentUser,
    );
    return _userWithLocalCommonRoomNames(
      withRole.copyWith(
        commonRooms: room_display.roomUserInfoCommonRooms(
          user: withRole,
          selectedRoom: room,
          currentUser: _currentUser,
          includeSelectedRoom: false,
        ),
      ),
      _servers,
    );
  }

  Future<void> _loadServers() async {
    _setHomeState(() {
      _loadingServers = true;
      _serverLoadError = null;
    });

    try {
      final servers = await _roomsController.loadRooms();
      if (!mounted) return;
      _setHomeState(() {
        var nextServers = servers;
        final selectedRoomId = _selectedServerId;
        if (selectedRoomId != null) {
          nextServers = _roomsController
              .patchRoomUnreadCleared(
                rooms: nextServers,
                roomId: selectedRoomId,
              )
              .rooms;
        }
        _servers = nextServers;
        _loadingServers = false;
        if (_selectedServerId != null &&
            !_servers.any((server) => server.id == _selectedServerId)) {
          final removedRoomId = _selectedServerId!;
          final shouldDisconnectLive = _joinedLiveRoomId == removedRoomId;
          for (final entry in _stagedAttachments) {
            entry.uploadController.cancel();
          }
          _stagedAttachments.clear();
          _discardRoomDraftInState(removedRoomId);
          _setComposerText('', saveDraft: false);
          _selectedServerId = null;
          _selectedRoom = null;
          _live = null;
          _messages = const [];
          _selectedRoomNewMessageCount = 0;
          _fileTransfers = const {};
          _fileDownloads = const {};
          _selectedRoomHasPendingJoinRequests = false;
          _resetMusicBox();
          _contentMode = _ContentMode.chat;
          if (shouldDisconnectLive) {
            _joinedLiveRoomId = null;
            unawaited(_liveSessionController.disconnect());
          }
        }
      });
      _syncAndroidNotificationState();
      _openPendingAndroidNotificationRoom();
    } catch (error) {
      if (!mounted) return;
      _setHomeState(() {
        _loadingServers = false;
        _serverLoadError = userFacingErrorMessage(error, fallback: '加载服务器失败');
      });
    }
  }

  void _selectServer(
    RoomCard server, {
    required bool openContent,
    String? focusMessageId,
  }) {
    unawaited(
      _openRoom(
        server,
        openContent: openContent,
        focusMessageId: focusMessageId,
      ),
    );
  }

  void _handleFocusMessageHandled(String messageId) {
    if (_focusedMessageId != messageId) return;
    _setHomeState(() => _focusedMessageId = null);
  }

  Future<void> _openRoom(
    RoomCard server, {
    required bool openContent,
    String? focusMessageId,
  }) async {
    if (_loadingRoom && _selectedServerId == server.id) return;
    _storeSelectedComposerDraft();

    _setHomeState(() {
      _clearDeferredRoomNotificationVisualMarkersInState();
      _selectedServerId = server.id;
      _restoreComposerDraftForRoom(server.id);
      _selectedRoomNewMessageCount = server.unreadCount;
      _clearRoomUnreadCount(server.id);
      _settingsOpen = false;
      _contentMode = _ContentMode.chat;
      if (openContent) _narrowContentOpen = true;
      _selectedRoom = null;
      _live = null;
      _messages = const [];
      _focusedMessageId = focusMessageId;
      _fileTransfers = const {};
      _fileDownloads = const {};
      _membersInitialSearchQuery = '';
      _selectedRoomHasPendingJoinRequests = false;
      _roomError = null;
      _sendError = null;
      _resetMusicBox();
      _stickerPanelState = sticker_display.stickerPanelReset(
        source: _stickerPanelState.source,
      );
      _loadingRoom = true;
    });
    if (widget.androidSystemService.isSupported) {
      unawaited(widget.androidSystemService.cancelRoomNotification(server.id));
      _syncAndroidNotificationState();
    }

    try {
      final snapshot = await _roomsController.openRoom(server.id);
      if (!mounted || _selectedServerId != server.id) return;
      var messages = snapshot.messages;
      if (focusMessageId != null &&
          !messages.any((message) => message.id == focusMessageId)) {
        messages = await _messagesController.loadMessagesUntil(
          roomId: server.id,
          messageId: focusMessageId,
        );
        if (!mounted || _selectedServerId != server.id) return;
      }
      _setHomeState(() {
        _selectedRoom = snapshot.detail;
        _live = snapshot.live;
        _messages = messages;
        _focusedMessageId = focusMessageId;
        _loadingRoom = false;
        _roomError = null;
      });
      unawaited(_markRoomReadFromMessages(server.id, messages));
      unawaited(_ensureComposerMentionMembers(server.id));
      unawaited(_refreshSelectedJoinRequestBadge(snapshot.detail));
      unawaited(_loadMusicBox(server.id));
    } catch (error) {
      if (!mounted || _selectedServerId != server.id) return;
      _setHomeState(() {
        _loadingRoom = false;
        _roomError = userFacingErrorMessage(error);
      });
    }
  }

  void _toggleSettings({required bool openContent}) {
    _setHomeState(() {
      _clearDeferredRoomNotificationVisualMarkersInState();
      final opening = !_settingsOpen;
      _settingsOpen = opening;
      if (opening) {
        _contentMode = _ContentMode.chat;
        _settingsAppUpdate = null;
      }
      _auxiliaryOpenedFromNarrowSidebar = opening && openContent;
      if (openContent) {
        _narrowContentOpen = opening;
      }
    });
  }

  void _openCreateRoom({required bool openContent}) {
    if (!_settingsOpen && _contentMode == _ContentMode.createRoom) {
      _closeCreateRoom();
      return;
    }
    _setHomeState(() {
      _clearDeferredRoomNotificationVisualMarkersInState();
      _settingsOpen = false;
      _contentMode = _ContentMode.createRoom;
      _auxiliaryOpenedFromNarrowSidebar = openContent;
      if (openContent) _narrowContentOpen = true;
    });
  }

  void _closeCreateRoom() {
    _setHomeState(() {
      _contentMode = _ContentMode.chat;
      _restorePaneAfterAuxiliaryInState();
    });
  }

  void _closeSettings() {
    _setHomeState(() {
      _settingsOpen = false;
      _settingsAppUpdate = null;
      _restorePaneAfterAuxiliaryInState();
    });
  }

  Future<void> _confirmLogout() async {
    if (_logoutConfirming) return;
    _setHomeState(() => _logoutConfirming = true);
    try {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => DialogFrame(
          title: '退出登录',
          icon: Icons.logout,
          actionBar: ResponsiveDialogActionBar(
            actions: [
              ResponsiveDialogAction(
                label: '取消',
                onPressed: () => Navigator.of(context).pop(false),
              ),
              ResponsiveDialogAction(
                label: '退出登录',
                onPressed: () => Navigator.of(context).pop(true),
                tone: ButtonTone.danger,
              ),
            ],
          ),
          child: Text(
            '确认退出当前账号？',
            style: UiTypography.body.copyWith(color: UiColors.textSecondary),
          ),
        ),
      );
      if (!mounted || confirmed != true) return;
      await _logout();
    } finally {
      if (mounted) {
        _setHomeState(() => _logoutConfirming = false);
      }
    }
  }

  Future<void> _logout() async {
    if (widget.androidSystemService.isSupported) {
      try {
        await _androidPushRegistration?.unregister();
      } catch (_) {
        // The server also ignores devices attached to the revoked session, so
        // logout remains safe when this best-effort cleanup is offline.
      }
      try {
        await widget.androidSystemService.clearMessageNotifications();
        await widget.androidSystemService.enterForeground();
      } catch (_) {}
    }
    await _leaveLiveForSessionEnd(
      disconnectTimeout: const Duration(seconds: 1),
    );
    await widget.app.logout();
  }

  Future<void> _leaveLiveForSessionEnd({Duration? disconnectTimeout}) async {
    final joinedLiveRoomId = _joinedLiveRoomId;
    _joinedLiveRoomId = null;
    if (joinedLiveRoomId != null) {
      await _notifyLiveLeft(joinedLiveRoomId);
    }
    await _liveSessionController.disconnect(timeout: disconnectTimeout);
  }

  Future<void> _stopRealtimeForExit() async {
    try {
      await _services.realtime.stop();
    } catch (_) {}
  }

  void _handleAppUpdateDownloadCancellationChanged(
    ReleaseDownloadCancellationToken? token,
  ) {
    if (_appUpdateDownloadCancellationToken == token) return;
    _setHomeState(() {
      _appUpdateDownloadCancellationToken = token;
      if (token != null) {
        _searchExpanded = false;
        _titleSearchContextMenuOpen = false;
        _pendingTitleSearchContextMenuUpdate = null;
      }
    });
    if (token != null) {
      FocusManager.instance.primaryFocus?.unfocus();
      unawaited(_leaveLiveForAppUpdateDownload());
    }
  }

  Future<void> _leaveLiveForAppUpdateDownload() async {
    if (_joinedLiveRoomId == null) return;
    await _leaveLive();
  }

  Future<bool> _handleWindowCloseRequest() async {
    if (_exitingApplication) return true;
    if (_appUpdateDownloadInProgress) {
      return _handleAppUpdateDownloadCloseRequest();
    }
    final behavior = await _readCloseBehavior();
    if (!mounted) return true;
    if (behavior == CloseBehavior.askEveryTime) {
      final result = await _showCloseBehaviorDialog();
      if (result == null) return true;
      if (result.remember) {
        try {
          await widget.closeBehaviorStore.write(result.behavior);
        } catch (_) {}
      }
      await _performCloseBehavior(result.behavior);
      return true;
    }
    await _performCloseBehavior(behavior);
    return true;
  }

  Future<bool> _handleAppUpdateDownloadCloseRequest() async {
    if (_closeConfirming) return true;
    _setHomeState(() => _closeConfirming = true);
    try {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => DialogFrame(
          title: '正在下载新版本',
          icon: Icons.system_update_alt_outlined,
          adaptiveActions: [
            ResponsiveDialogAction(
              label: '继续下载',
              onPressed: () => Navigator.of(context).pop(false),
            ),
            ResponsiveDialogAction(
              label: '中断下载并关闭',
              onPressed: () => Navigator.of(context).pop(true),
              tone: ButtonTone.danger,
              icon: Icons.close,
            ),
          ],
          child: Text(
            '正在下载新版本，关闭将中断下载。',
            style: UiTypography.body.copyWith(
              color: UiColors.textSecondary,
              height: 1.55,
            ),
          ),
        ),
      );
      if (!mounted || confirmed != true) return true;
      final token = _appUpdateDownloadCancellationToken;
      await token?.cancelAndDeletePartialFile();
      await _exitApplication();
      return true;
    } finally {
      if (mounted) _setHomeState(() => _closeConfirming = false);
    }
  }

  Future<CloseBehavior> _readCloseBehavior() async {
    try {
      return await widget.closeBehaviorStore.read();
    } catch (_) {
      return defaultCloseBehavior;
    }
  }

  Future<_ClosePromptResult?> _showCloseBehaviorDialog() async {
    if (_closeConfirming) return null;
    _setHomeState(() => _closeConfirming = true);
    try {
      var selectedBehavior = CloseBehavior.minimizeToTray;
      var remember = false;
      return await showDialog<_ClosePromptResult>(
        context: context,
        builder: (context) {
          return StatefulBuilder(
            builder: (context, setDialogState) {
              return DialogFrame(
                title: '关闭 Gang Chat',
                icon: Icons.close,
                actionBar: ResponsiveDialogActionBar(
                  actions: [
                    ResponsiveDialogAction(
                      label: '取消',
                      onPressed: () => Navigator.of(context).pop(),
                    ),
                    ResponsiveDialogAction(
                      label: '确认',
                      icon: Icons.check,
                      tone: selectedBehavior == CloseBehavior.exitProgram
                          ? ButtonTone.danger
                          : ButtonTone.primary,
                      onPressed: () => Navigator.of(context).pop(
                        _ClosePromptResult(
                          behavior: selectedBehavior,
                          remember: remember,
                        ),
                      ),
                    ),
                  ],
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    LayoutBuilder(
                      builder: (context, constraints) {
                        final minimizeWidth = Button.minimumWidthForLabel(
                          context,
                          label: '最小化到托盘',
                          hasIcon: true,
                        );
                        final exitWidth = Button.minimumWidthForLabel(
                          context,
                          label: '退出程序',
                          hasIcon: true,
                        );
                        final availableButtonWidth =
                            (constraints.maxWidth - 12) / 2;
                        final stack =
                            constraints.maxWidth.isFinite &&
                            (availableButtonWidth < minimizeWidth ||
                                availableButtonWidth < exitWidth);
                        final minimizeButton = Button(
                          key: const ValueKey('close-behavior-minimize-button'),
                          selected:
                              selectedBehavior == CloseBehavior.minimizeToTray,
                          onPressed: () => setDialogState(
                            () =>
                                selectedBehavior = CloseBehavior.minimizeToTray,
                          ),
                          icon: const Icon(Icons.keyboard_arrow_down),
                          width: double.infinity,
                          child: const Text('最小化到托盘'),
                        );
                        final exitButton = Button(
                          key: const ValueKey('close-behavior-exit-button'),
                          selected:
                              selectedBehavior == CloseBehavior.exitProgram,
                          onPressed: () => setDialogState(
                            () => selectedBehavior = CloseBehavior.exitProgram,
                          ),
                          icon: const Icon(Icons.logout),
                          width: double.infinity,
                          tone: selectedBehavior == CloseBehavior.exitProgram
                              ? ButtonTone.danger
                              : ButtonTone.neutral,
                          child: const Text('退出程序'),
                        );
                        if (stack) {
                          return Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              minimizeButton,
                              const SizedBox(height: 12),
                              exitButton,
                            ],
                          );
                        }
                        return Row(
                          children: [
                            Expanded(child: minimizeButton),
                            const SizedBox(width: 12),
                            Expanded(child: exitButton),
                          ],
                        );
                      },
                    ),
                    const SizedBox(height: 14),
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            '记住我的选择',
                            style: UiTypography.body.copyWith(
                              color: UiColors.textSecondary,
                            ),
                          ),
                        ),
                        UiSwitch(
                          value: remember,
                          onChanged: (value) =>
                              setDialogState(() => remember = value),
                        ),
                      ],
                    ),
                  ],
                ),
              );
            },
          );
        },
      );
    } finally {
      if (mounted) _setHomeState(() => _closeConfirming = false);
    }
  }

  Future<void> _performCloseBehavior(CloseBehavior behavior) async {
    switch (behavior) {
      case CloseBehavior.askEveryTime:
        return;
      case CloseBehavior.minimizeToTray:
        await widget.windowController.minimizeToTray();
        return;
      case CloseBehavior.exitProgram:
        await _exitApplication();
        return;
    }
  }

  Future<void> _exitApplication() async {
    if (_exitingApplication) return;
    _exitingApplication = true;
    try {
      await widget.windowController.hideAppWindowForExit();
      await _leaveLiveForSessionEnd(
        disconnectTimeout: const Duration(seconds: 1),
      );
      await _stopRealtimeForExit();
      await widget.app.exitSessionForAppExit();
    } finally {
      await widget.windowController.terminateApplication();
    }
  }

  void _handleUserUpdated(CurrentUser user) {
    _setHomeState(() => _currentUser = user);
    widget.app.onCurrentUserUpdated?.call(user);
  }

  Future<void> _retryOpenSelectedRoom() async {
    final server = _selectedServer;
    if (server == null) return;
    await _openRoom(server, openContent: false);
  }

  void _openLiveChannel() {
    if (_selectedServerId == null) return;
    _setHomeState(() {
      _settingsOpen = false;
      _contentMode = _ContentMode.live;
      _narrowContentOpen = true;
    });
  }

  Future<void> _openJoinedLiveChannel() async {
    final roomId = _joinedLiveRoomId;
    if (roomId == null) return;
    _collapseSearch();

    if (_selectedServerId == roomId && _selectedRoom?.id == roomId) {
      _openLiveChannel();
      return;
    }

    final server = _serverById(roomId);
    if (server == null) return;

    try {
      final snapshot = await _roomsController.openRoom(roomId);
      if (!mounted || _joinedLiveRoomId != roomId) return;
      _applyJoinedLiveRoomSnapshot(server, snapshot);
    } catch (error) {
      if (!mounted || _joinedLiveRoomId != roomId) return;
      if (_selectedServerId == roomId) {
        _setHomeState(() => _roomError = userFacingErrorMessage(error));
      }
    }
  }

  void _applyJoinedLiveRoomSnapshot(
    RoomCard server,
    RoomOpenSnapshot snapshot,
  ) {
    _storeSelectedComposerDraft();

    _setHomeState(() {
      _selectedServerId = server.id;
      _restoreComposerDraftForRoom(server.id);
      _selectedRoomNewMessageCount = server.unreadCount;
      _clearRoomUnreadCount(server.id);
      _settingsOpen = false;
      _contentMode = _ContentMode.live;
      _narrowContentOpen = true;
      _selectedRoom = snapshot.detail;
      _live = snapshot.live;
      _messages = snapshot.messages;
      _fileTransfers = const {};
      _fileDownloads = const {};
      _membersInitialSearchQuery = '';
      _selectedRoomHasPendingJoinRequests = false;
      _roomError = null;
      _sendError = null;
      _loadingRoom = false;
      _resetMusicBox();
      _stickerPanelState = sticker_display.stickerPanelReset(
        source: _stickerPanelState.source,
      );
    });
    unawaited(_markRoomReadFromMessages(server.id, snapshot.messages));
    unawaited(_refreshSelectedJoinRequestBadge(snapshot.detail));
    unawaited(_loadMusicBox(server.id));
  }

  RoomCard? _serverById(String roomId) {
    for (final server in _servers) {
      if (server.id == roomId) return server;
    }
    return null;
  }

  void _openChat() {
    final roomId = _selectedServerId;
    _setHomeState(() {
      _clearDeferredRoomNotificationVisualMarkersInState();
      _contentMode = _ContentMode.chat;
    });
    if (roomId != null) {
      unawaited(_refreshSelectedMessagesSilently(roomId));
    }
  }

  Future<void> _openRoomMembers({String initialSearchQuery = ''}) async {
    final room = _selectedRoom;
    if (room == null) return;
    _setHomeState(() {
      _settingsOpen = false;
      _contentMode = _ContentMode.members;
      _narrowContentOpen = true;
      _membersInitialSearchQuery = initialSearchQuery.trim();
    });
  }

  UserProfileAction? _messageProfileAction(UserSummary user) {
    final room = _selectedRoom;
    if (room == null) return null;
    final uid = user.uid?.trim();
    if (uid == null || uid.isEmpty) return null;
    if (!member_filter.canOpenRoomMemberManagementFromProfile(
      room: room,
      currentUser: _currentUser,
      target: user,
    )) {
      return null;
    }
    return UserProfileAction(
      label: '管理成员',
      icon: Icons.manage_accounts_outlined,
      onPressed: () => unawaited(_openRoomMembers(initialSearchQuery: uid)),
    );
  }

  Future<void> _openRoomSettings() async {
    final room = _selectedRoom;
    if (room == null) return;
    _setHomeState(() {
      _settingsOpen = false;
      _contentMode = _ContentMode.roomSettings;
      _narrowContentOpen = true;
    });
  }

  void _handleRoomSettingsResult(String roomId, RoomManagementResult result) {
    switch (result.kind) {
      case RoomManagementResultKind.created:
        break;
      case RoomManagementResultKind.updated:
        final updated = result.room;
        if (updated != null) _applyManagedRoomUpdated(updated);
        unawaited(_loadServers());
        break;
      case RoomManagementResultKind.left:
      case RoomManagementResultKind.deleted:
        _applyManagedRoomRemoved(roomId);
        unawaited(_loadServers());
        break;
    }
  }

  void _handleCreateRoomResult(RoomManagementResult result) {
    if (result.kind != RoomManagementResultKind.created) return;
    final room = result.room;
    if (room == null) return;
    _applyCreatedRoom(room);
    unawaited(_loadServers());
  }

  void _applyCreatedRoom(RoomDetail room) {
    _storeSelectedComposerDraft();
    final patch = _roomsController.patchRoomDetailApplied(
      rooms: _servers,
      detail: room,
    );
    _setHomeState(() {
      _selectedServerId = room.id;
      _discardRoomDraftInState(room.id);
      _stagedAttachments.clear();
      _setComposerText('', saveDraft: false);
      _selectedRoom = patch.selectedRoom;
      _servers = patch.rooms;
      _live = room.live;
      _messages = const [];
      _selectedRoomNewMessageCount = 0;
      _fileTransfers = const {};
      _fileDownloads = const {};
      _settingsOpen = false;
      _contentMode = _ContentMode.chat;
      _membersInitialSearchQuery = '';
      _selectedRoomHasPendingJoinRequests = false;
      _roomError = null;
      _sendError = null;
      _loadingRoom = false;
      _narrowContentOpen = true;
    });
  }

  void _applyManagedRoomUpdated(RoomDetail room) {
    if (_selectedServerId != room.id) return;
    if (_joinedLiveRoomId == room.id) {
      _joinedLivePersonalAiVoiceAnnouncementsEnabled =
          room.aiVoiceAnnouncementsEnabled;
    }
    final patch = _roomsController.patchRoomDetailApplied(
      rooms: _servers,
      detail: room,
    );
    _setHomeState(() {
      _selectedRoom = patch.selectedRoom;
      _servers = patch.rooms;
      _live = room.live;
      _roomError = null;
    });
    unawaited(_refreshSelectedMessagesSilently(room.id));
    unawaited(_refreshSelectedJoinRequestBadge(room));
  }

  void _applyManagedRoomRemoved(String roomId) {
    _setHomeState(() {
      _discardRoomDraftInState(roomId);
      _servers = _roomsController.removeRoomCard(_servers, roomId);
      if (_selectedServerId == roomId) {
        for (final entry in _stagedAttachments) {
          entry.uploadController.cancel();
        }
        _stagedAttachments.clear();
        _setComposerText('', saveDraft: false);
        _selectedServerId = null;
        _selectedRoom = null;
        _live = null;
        _messages = const [];
        _selectedRoomNewMessageCount = 0;
        _fileTransfers = const {};
        _fileDownloads = const {};
        _contentMode = _ContentMode.chat;
        _membersInitialSearchQuery = '';
        _settingsOpen = false;
        _selectedRoomHasPendingJoinRequests = false;
        _roomError = null;
        _sendError = null;
        _narrowContentOpen = false;
      }
      if (_joinedLiveRoomId == roomId) {
        _joinedLiveRoomId = null;
        _joiningLive = false;
      }
    });
  }
}

class _ClosePromptResult {
  const _ClosePromptResult({required this.behavior, required this.remember});

  final CloseBehavior behavior;
  final bool remember;
}
