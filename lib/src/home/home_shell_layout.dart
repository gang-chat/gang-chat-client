part of 'home_shell.dart';

extension _HomeShellLayout on _HomeShellState {
  ChatFileDownloadActions get _chatFileDownloadActions =>
      ChatFileDownloadActions(
        onDownload: (message, attachment, index, resolvedUrl) => unawaited(
          _downloadAttachment(
            message: message,
            attachment: attachment,
            index: index,
            resolvedUrl: resolvedUrl,
          ),
        ),
        onPause: _pauseDownload,
        onResume: _resumeDownload,
        onCancel: _cancelDownload,
        onDismiss: _dismissDownload,
      );

  ChatVoicePlaybackActions get _chatVoicePlaybackActions =>
      ChatVoicePlaybackActions(
        activeMessageId: _voicePlayback.playing
            ? _voicePlayback.activeMessageId
            : null,
        activePosition: _voicePlayback.position,
        activeDuration: _voicePlayback.duration,
        onToggle: (messageId, resolvedUrl) => unawaited(
          _toggleVoicePlayback(messageId: messageId, resolvedUrl: resolvedUrl),
        ),
      );

  Future<void> _openStickerManagerImagePreview(
    BuildContext context, {
    required String imageUrl,
    required String suggestedName,
    bool forceSquare = false,
  }) {
    return showChatImagePreview(
      context,
      imageUrl: imageUrl,
      suggestedName: suggestedName,
      actions: _imagePreviewActions,
      forceSquare: forceSquare,
    );
  }

  Widget _buildNarrowLayout(
    double width, {
    required _TitleLiveRoomDockBuilder liveRoomDockBuilder,
  }) {
    if (!_narrowContentOpen) {
      return KeyedSubtree(
        key: const ValueKey('home-narrow-room-list'),
        child: _buildLobby(
          compact: true,
          footer: LayoutBuilder(
            builder: (context, constraints) {
              final dockMaxWidth = constraints.maxWidth
                  .clamp(0.0, double.infinity)
                  .toDouble();
              return liveRoomDockBuilder(dockMaxWidth, fillAvailable: true);
            },
          ),
        ),
      );
    }

    return KeyedSubtree(
      key: const ValueKey('home-narrow-content'),
      child: ColoredBox(
        color: UiColors.background,
        child: _buildContentPane(onNavigateBack: _showNarrowRoomList),
      ),
    );
  }

  void _showNarrowRoomList() {
    _setHomeState(() => _narrowContentOpen = false);
  }

  bool _canHandleShellBack({required bool narrowLayout}) {
    return _appUpdateDownloadInProgress ||
        _fullScreenLiveTrack != null ||
        _searchPaletteOpen ||
        (_hasSearchQuery && _searchExpanded) ||
        _settingsOpen ||
        _contentMode != _ContentMode.chat ||
        (narrowLayout && _narrowContentOpen);
  }

  void _handleShellBack({required bool narrowLayout}) {
    if (_appUpdateDownloadInProgress) return;
    if (_fullScreenLiveTrack != null) {
      _exitLiveFullScreen();
      return;
    }
    if (_searchPaletteOpen) {
      _closeSearchPalette();
      return;
    }
    if (_hasSearchQuery && _searchExpanded) {
      _collapseSearch();
      return;
    }
    if (_settingsOpen) {
      _closeSettings();
      return;
    }

    switch (_contentMode) {
      case _ContentMode.superuserUserSettings:
        _closeSuperuserUserSettings();
        return;
      case _ContentMode.createRoom:
        _closeCreateRoom();
        return;
      case _ContentMode.notifications:
        _closeNotifications();
        return;
      case _ContentMode.live:
      case _ContentMode.members:
      case _ContentMode.roomSettings:
        _openChat();
        return;
      case _ContentMode.chat:
        if (narrowLayout && _narrowContentOpen) {
          _showNarrowRoomList();
        }
        return;
    }
  }

  Widget _buildContentPane({VoidCallback? onNavigateBack}) {
    if (_settingsOpen) {
      return SettingsPage(
        isSubWindow: true,
        api: _services.api,
        apiBaseUrl: widget.app.apiBaseUrl,
        emailVerificationController: widget.app.emailVerificationController,
        passwordResetController: widget.app.passwordResetController,
        audioDeviceStore: widget.audioDeviceStore,
        closeBehaviorStore: widget.closeBehaviorStore,
        languageStore: widget.languageStore,
        androidSystemService: widget.androidSystemService,
        windowController: widget.windowController,
        initialSection: _settingsAppUpdate == null
            ? _settingsInitialSection
            : SettingsSection.about,
        initialMusicPlaylistId: _settingsInitialMusicPlaylistId,
        initialAppUpdate: _settingsAppUpdate,
        stickerPackStore: widget.app.stickerPackStore,
        stickerImagePreviewOpener: _openStickerManagerImagePreview,
        onAppUpdateDownloadCancellationChanged:
            _handleAppUpdateDownloadCancellationChanged,
        currentUser: _currentUser,
        onUserUpdated: _handleUserUpdated,
        onMusicPlaylistsChanged: _markMusicPlaylistsChanged,
        onAccountDeleted: _logout,
        onScreenShareMaxHeightChanged: (height) =>
            unawaited(_liveSessionController.setScreenShareMaxHeight(height)),
        onScreenShareFrameRateChanged: (frameRate) => unawaited(
          _liveSessionController.setScreenShareFrameRate(frameRate),
        ),
        onVolumeChanged: _syncSettingsAudioVolumeToLive,
        // The Settings picker already routes the native ADM (selectAudioInput/
        // Output). For inputs, also keep LiveSession's tracked capture device in
        // sync so a later mute/unmute republish stays on the chosen mic. Outputs
        // are global on the ADM and need no session-side action.
        onDeviceSelected: (kind, deviceId) {
          if (kind == 'audioinput') {
            unawaited(_liveSessionController.setInputDeviceId(deviceId));
          }
        },
        onClose: _closeSettings,
        musicTrackPreviewPlatformFactory:
            widget.musicTrackPreviewPlatformFactory,
      );
    }

    if (_contentMode == _ContentMode.superuserUserSettings &&
        _superuserSettingsTarget != null) {
      final target = _superuserSettingsTarget!;
      return SettingsPage(
        key: ValueKey('superuser-user-settings-${target.id}'),
        isSubWindow: true,
        api: _services.api,
        apiBaseUrl: widget.app.apiBaseUrl,
        emailVerificationController: widget.app.emailVerificationController,
        controller: SettingsController(
          api: _services.api,
          apiBaseUrl: widget.app.apiBaseUrl,
          stickerPackStore: widget.app.stickerPackStore,
          managedUserId: target.id,
        ),
        audioDeviceStore: ManagedUserAudioSettingsStore(
          api: _services.api,
          userId: target.id,
        ),
        audioDeviceService: const ManagedUserAudioDeviceService(),
        closeBehaviorStore: widget.closeBehaviorStore,
        languageStore: widget.languageStore,
        windowController: widget.windowController,
        stickerPackStore: widget.app.stickerPackStore,
        stickerImagePreviewOpener: _openStickerManagerImagePreview,
        fileSelectionService: _fileSelectionService,
        onClose: _closeSuperuserUserSettings,
        onAccountDeleted: () async {
          _setHomeState(() {
            final results = _searchResults;
            if (results != null) {
              _searchResults = GlobalSearchResults(
                myRooms: results.myRooms,
                publicRooms: results.publicRooms,
                userSettings: [
                  for (final user in results.userSettings)
                    if (user.id != target.id) user,
                ],
                messages: results.messages,
                files: results.files,
                nextCursors: results.nextCursors,
                totalCounts: results.totalCounts,
              );
            }
            _superuserSettingsTarget = null;
            _contentMode = _ContentMode.chat;
          });
        },
        onUserUpdated: (updated) {
          _setHomeState(() {
            _superuserSettingsTarget = updated.toSummary();
            final results = _searchResults;
            if (results != null) {
              _searchResults = GlobalSearchResults(
                myRooms: results.myRooms,
                publicRooms: results.publicRooms,
                userSettings: [
                  for (final user in results.userSettings)
                    if (user.id == updated.id) updated.toSummary() else user,
                ],
                messages: results.messages,
                files: results.files,
                nextCursors: results.nextCursors,
                totalCounts: results.totalCounts,
              );
            }
          });
        },
      );
    }

    if (_contentMode == _ContentMode.createRoom) {
      return RoomSettingsDialog.create(
        key: const ValueKey('home-create-room-settings-dialog'),
        controller: _roomsController,
        currentUser: _currentUser,
        embedded: true,
        onClose: _closeCreateRoom,
        onResult: _handleCreateRoomResult,
        stickerImagePreviewOpener: _openStickerManagerImagePreview,
      );
    }

    if (_contentMode == _ContentMode.notifications) {
      return HomeNotificationsPane(
        invites: _notificationInvites,
        applications: _notificationApplications,
        roomNotifications: _notificationRoomEvents,
        currentUser: _currentUser,
        loading: _loadingNotifications,
        error: _notificationError,
        busyInviteId: _busyNotificationInviteId,
        busyApplicationId: _busyNotificationApplicationId,
        onClose: _closeNotifications,
        onRefresh: () =>
            unawaited(_loadNotifications(clearVisualReadMarkers: true)),
        onReviewInvite: _reviewNotificationInvite,
        onWithdrawApplication: _withdrawNotificationApplication,
        onCopyNotification: _copyNotification,
        onDeleteNotification: _deleteNotification,
        onDeleteNotifications: _deleteNotifications,
        onResolveRoomProfile: _resolveRoomProfile,
        onResolveRoomUserProfile: _resolveRoomUserProfile,
        onOpenRoom: _openNotificationRoom,
        onOpenRoomEvent: _openNotificationRoomEvent,
      );
    }

    if (_selectedServerId == null ||
        (_lobbyOpen &&
            _contentMode == _ContentMode.chat &&
            onNavigateBack == null)) {
      return _buildLobby(compact: onNavigateBack != null);
    }
    if (_contentMode == _ContentMode.members && _selectedRoom != null) {
      return _wrapRoomContext(
        RoomContextTab.members,
        onNavigateBack: onNavigateBack,
        child: RoomMembersDialog(
        controller: _roomsController,
        room: _selectedRoom!,
        currentUser: _currentUser,
        initialLive: _live ?? _selectedRoom!.live,
        initialSearchQuery: _membersInitialSearchQuery,
        hasPendingJoinRequests: _selectedRoomHasPendingJoinRequests,
        reloadToken: _membersReloadToken,
        embedded: true,
        onClose: _openChat,
        onChanged: () => unawaited(_loadServers()),
        onPendingJoinRequestsChanged: _handleSelectedJoinRequestsChanged,
        onOpenRoom: _openNotificationRoom,
      ),
      );
    }
    if (_contentMode == _ContentMode.roomSettings && _selectedRoom != null) {
      final room = _selectedRoom!;
      return _wrapRoomContext(
        RoomContextTab.settings,
        onNavigateBack: onNavigateBack,
        child: RoomSettingsDialog(
        key: ValueKey('home-room-settings-${room.id}'),
        controller: _roomsController,
        room: room,
        currentUser: _currentUser,
        initialSection: _roomSettingsInitialSection,
        isInLive: _joinedLiveRoomId == room.id,
        onRoomUpdated: _applyManagedRoomUpdated,
        onLeaveLive: () async {
          if (_joinedLiveRoomId == room.id) await _leaveLive();
        },
        embedded: true,
        onClose: _openChat,
        onResult: (result) => _handleRoomSettingsResult(room.id, result),
        stickerImagePreviewOpener: _openStickerManagerImagePreview,
        messageHistoryBuilder: (context) => _RoomMessageHistoryPane(
          room: room,
          currentUser: _currentUser,
          roomsController: _roomsController,
          messagesController: _messagesController,
          clipboardService: _clipboardService,
          fileDownloads: _fileDownloads,
          downloadActions: _chatFileDownloadActions,
          voicePlaybackActions: _chatVoicePlaybackActions,
          imagePreviewActions: _imagePreviewActions,
          messageActions: _chatMessageActions,
          onResolveRoomProfile: _resolveRoomProfile,
          onResolveRoomUserProfile: _resolveRoomUserProfile,
          onOpenRoom: _openNotificationRoom,
          profileActionBuilder: _messageProfileAction,
          onJumpToMessage: (messageId) {
            final server = _selectedServer;
            if (server == null) return;
            unawaited(
              _openRoom(server, openContent: true, focusMessageId: messageId),
            );
          },
        ),
        musicPlaylistsBuilder: (context) => MusicPlaylistsPanel(
          key: ValueKey('room-music-playlists-${room.id}'),
          controller: PersonalMusicPlaylistsController.room(
            roomApi: _services.api is RoomMusicPlaylistApi
                ? _services.api as RoomMusicPlaylistApi
                : null,
            personalApi: _services.api is PersonalMusicPlaylistApi
                ? _services.api as PersonalMusicPlaylistApi
                : null,
            roomId: room.id,
            canManage: room_display
                .roomAccessState(room: room, currentUser: _currentUser)
                .canManageRoom,
            searchTracks: ({required keyword, required source}) =>
                _services.api.searchMusicBox(
                  roomId: room.id,
                  keyword: keyword,
                  source: source,
                  count: 20,
                  page: 1,
                ),
            onChanged: _markMusicPlaylistsChanged,
          ),
          initialPlaylistId: _roomSettingsInitialMusicPlaylistId,
          title: '房间歌单',
          unavailableMessage: '房间歌单需要登录后从服务端读取',
          previewApi: _services.api is MusicTrackPreviewApi
              ? _services.api as MusicTrackPreviewApi
              : null,
          previewPlatformFactory: widget.musicTrackPreviewPlatformFactory,
          shareApi: _services.api,
          currentUser: _currentUser,
        ),
      ),
      );
    }
    if (_contentMode == _ContentMode.live) {
      return _wrapRoomContext(
        RoomContextTab.live,
        onNavigateBack: onNavigateBack,
        child: LiveChannelPane(
        title: _roomTitle(_selectedRoom, _selectedServer),
        avatarUrl: _selectedRoom?.avatarUrl ?? _selectedServer?.avatarUrl,
        live: _live ?? _selectedRoom?.live,
        currentUser: _currentUser,
        loading: _loadingRoom,
        joined: _joinedLiveRoomId == _selectedServerId,
        joining: _joiningLive,
        leaving: _leavingLive,
        micMuted: _micMuted,
        headphonesMuted: _headphonesMuted,
        voiceBlocked: _voiceBlocked,
        cameraOn: _cameraOn,
        screenSharing: _screenSharing,
        speakingUserIds: _liveSessionController.speakingIdentities,
        connectedParticipantIds:
            _liveSessionController.connectedParticipantIdentities,
        liveKitMicMutedByParticipantId:
            _liveSessionController.micMutedByIdentity,
        videoTracks: _liveSessionController.videoTracks,
        stageSelection: _liveStageSelections[_selectedServerId],
        suspendStageVideo:
            !kIsWeb &&
            Theme.of(context).platform == TargetPlatform.windows &&
            _fullScreenLiveTrack != null,
        onStageSelectionChanged: _setLiveStageSelection,
        onEnterFullScreen: _enterLiveFullScreen,
        onBackToChat: _openChat,
        onJoin: () => unawaited(_joinLive('live_panel')),
        onLeave: () => unawaited(_leaveLive()),
        onToggleMic: _voiceBlocked ? null : _toggleMicMute,
        onToggleHeadphones: _toggleHeadphonesMute,
        onToggleCamera: () => unawaited(_toggleCamera()),
        onFlipCamera:
            !kIsWeb &&
                Theme.of(context).platform == TargetPlatform.android &&
                _liveSessionController.canFlipCamera
            ? _flipLocalCamera
            : null,
        onSetLocalCameraMirrored: _setLocalCameraMirrored,
        onToggleShare: supportsLocalScreenShare
            ? () => unawaited(_toggleScreenShare())
            : null,
        musicBox: _musicBox,
        musicBoxOpen: _musicBoxOpen,
        musicBoxSearchController: _musicBoxSearchController,
        musicBoxSearchResults: _musicBoxSearchResults,
        musicBoxSearching: _musicBoxSearching,
        musicBoxSearchError: _musicBoxSearchError,
        musicBoxSource: _musicBoxSource,
        onToggleMusicBox: _toggleMusicBoxPanel,
        onMusicBoxTogglePlayback: _toggleMusicBoxPlayback,
        onMusicBoxSkip: () => unawaited(_controlMusicBox('skip')),
        onMusicBoxPrevious: () => unawaited(_controlMusicBox('previous')),
        onMusicBoxModeChanged: (mode) =>
            unawaited(_changeMusicBoxPlaybackMode(mode)),
        musicBoxController: _musicBoxController,
        musicBoxRoomId: _selectedServerId,
        musicPlaylistsRevision: _musicPlaylistsRevision,
        musicBoxRoom: _selectedRoom == null
            ? null
            : room_display.publicRoomFromRoomDetail(_selectedRoom!),
        onMusicBoxStateChanged: _applyMusicBoxSnapshot,
        onCreateFirstRoomMusicPlaylist:
            _selectedRoom != null &&
                room_display
                    .roomAccessState(
                      room: _selectedRoom!,
                      currentUser: _currentUser,
                    )
                    .canManageRoom
            ? () => unawaited(
                _openRoomSettings(
                  initialSection: RoomSettingsSection.playlists,
                ),
              )
            : null,
        onCreateFirstPersonalMusicPlaylist: () =>
            _openSettingsSection(SettingsSection.playlists, openContent: true),
        onEditRoomMusicPlaylist:
            _selectedRoom != null &&
                room_display
                    .roomAccessState(
                      room: _selectedRoom!,
                      currentUser: _currentUser,
                    )
                    .canManageRoom
            ? (playlistId) => _openRoomSettings(
                initialSection: RoomSettingsSection.playlists,
                initialMusicPlaylistId: playlistId,
              )
            : null,
        onEditPersonalMusicPlaylist: (playlistId) async {
          _openSettingsSection(
            SettingsSection.playlists,
            openContent: true,
            initialMusicPlaylistId: playlistId,
          );
        },
        onMusicBoxQueueResult: (result) =>
            unawaited(_queueMusicBoxTrack(result)),
        onMusicBoxRemoveItem: (item) => unawaited(_removeMusicBoxItem(item)),
        onMusicBoxSourceChanged: _changeMusicBoxSource,
        inputVolume: _liveSessionController.inputVolume,
        outputVolume: _liveSessionController.outputVolume,
        musicBoxVolume: _liveSessionController.musicBoxVolume,
        screenShareVolume: _liveSessionController.screenShareVolume,
        onInputVolumeChanged: _changeInputVolume,
        onOutputVolumeChanged: _changeOutputVolume,
        onMusicBoxVolumeChanged: (volume) =>
            unawaited(_liveSessionController.setMusicBoxVolume(volume)),
        onScreenShareVolumeChanged: _changeScreenShareVolume,
        onScreenShareMuteToggled: _toggleScreenShareAudioMute,
        participantVoiceVolume: _participantVoiceVolume,
        onParticipantVoiceVolumeChanged: _changeParticipantVoiceVolume,
        onParticipantVoiceMuteToggled: _toggleParticipantVoiceMute,
        canModerateParticipant: _canModerateLiveParticipant,
        onToggleParticipantMicModeration: (participant) =>
            unawaited(_toggleLiveParticipantMicModeration(participant)),
        onToggleParticipantHeadphonesModeration: (participant) =>
            unawaited(_toggleLiveParticipantHeadphonesModeration(participant)),
        canRemoveParticipant: _canRemoveLiveParticipant,
        onRemoveParticipant: (participant) =>
            unawaited(_removeLiveParticipant(participant)),
        onResolveParticipantProfile: _resolveSenderProfile,
        onResolveParticipantRoomProfile: _resolveRoomProfile,
        onEnterParticipantProfileRoom: _openNotificationRoom,
        participantProfileActionBuilder: _messageProfileAction,
      ),
      );
    }

    final visibleMessages = _visibleMessagesForMe(_messages);
    return _wrapRoomContext(
      RoomContextTab.chat,
      onNavigateBack: onNavigateBack,
      child: ChatPane(
      currentUser: _currentUser,
      timestampNow: _serverNow,
      roomCard: _selectedServer,
      room: _selectedRoom,
      live: _live,
      messages: visibleMessages,
      newMessageCount: _visibleNewMessageCount(visibleMessages),
      focusMessageId: _focusedMessageId,
      onFocusMessageHandled: _handleFocusMessageHandled,
      fileTransfers: _fileTransfers,
      fileDownloads: _fileDownloads,
      downloadActions: _chatFileDownloadActions,
      imagePreviewActions: _imagePreviewActions,
      messageActions: _chatMessageActions,
      voicePlaybackActions: _chatVoicePlaybackActions,
      loading: _loadingRoom,
      error: _roomError,
      sending: _sending,
      sendError: _sendError,
      composerController: _composerController,
      composerPanelController: _composerPanelController,
      stickerPanel: _stickerPanelState,
      voiceState: _voiceState,
      composerAttachments: _stagedAttachmentViews,
      composerQuotes: _selectedComposerQuotes,
      onRemoveComposerQuote: _removeSelectedComposerQuote,
      composerComponent: _selectedComposerComponent,
      onRemoveComposerComponent: _removeSelectedComposerComponent,
      fileActionHighlighted:
          _pickingAttachments || _stagedAttachments.isNotEmpty,
      mentionOptions: _composerMentionOptions,
      mentionMembers: _composerMentionMembersRoomId == _selectedServerId
          ? _composerMentionMembers
          : const [],
      mentionMembersReady: _composerMentionMembersRoomId == _selectedServerId,
      mentionLoading:
          _composerMentionQuery != null && _loadingComposerMentionMembers,
      mentionSelectedIndex: _composerMentionSelectedIndex,
      onSelectMention: _selectComposerMention,
      composerInputFormatters: [
        _ConfirmedMentionBackspaceFormatter(_composerController),
      ],
      onNavigateMentionSelection: _navigateComposerMentionSelection,
      onConfirmMentionSelection: _confirmComposerMentionSelection,
      onHighlightMentionSelection: _highlightComposerMentionSelection,
      onSubmit: (value) => unawaited(_sendText(value)),
      onSendSticker: (sticker) => unawaited(_sendSticker(sticker)),
      onLoadStickers: () => unawaited(_loadStickerPacks(forceReload: true)),
      onRefreshStickers: () => unawaited(_loadStickerPacks(forceReload: true)),
      onStickerSourceChanged: _changeStickerSource,
      onUploadFirstPersonalSticker: () =>
          _openSettingsSection(SettingsSection.stickers, openContent: true),
      onUploadFirstRoomSticker:
          _selectedRoom != null &&
              room_display
                  .roomAccessState(
                    room: _selectedRoom!,
                    currentUser: _currentUser,
                  )
                  .canManageRoom
          ? () => unawaited(
              _openRoomSettings(initialSection: RoomSettingsSection.stickers),
            )
          : null,
      onStartVoice: () => unawaited(_startVoiceRecording()),
      onSendVoice: () => unawaited(_finishAndSendVoice()),
      onCancelVoice: () => unawaited(_cancelVoiceRecording()),
      onPickFile: () => unawaited(_pickAttachments()),
      onPasteFiles: _pasteAttachments,
      onCanPasteFiles: _canPasteAttachments,
      onRemoveAttachment: _removeAttachment,
      onRetryAttachment: _retryAttachment,
      onRetry: () => unawaited(_retryOpenSelectedRoom()),
      onViewedNewMessages: _clearSelectedRoomNewMessagePrompt,
      onResolveSenderProfile: _resolveSenderProfile,
      onResolveRoomProfile: _resolveRoomProfile,
      onEnterProfileRoom: _openNotificationRoom,
      senderProfileActionBuilder: _messageProfileAction,
      onMentionUser: _mentionUserFromAvatar,
      composerDropKey: _composerDropKey,
    ),
    );
  }

  Widget _buildContentSheet(Widget child) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        homeContentSheetInset,
        0,
        homeContentSheetInset,
        homeContentSheetInset,
      ),
      child: DecoratedBox(
        key: const ValueKey('home-content-sheet'),
        decoration: BoxDecoration(
          color: UiColors.surfaceLow,
          borderRadius: BorderRadius.circular(UiRadii.xl),
          border: Border.all(color: UiColors.border),
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(UiRadii.xl - 1),
          child: child,
        ),
      ),
    );
  }

  bool get _roomContentVisible =>
      !_settingsOpen &&
      _selectedServerId != null &&
      !_lobbyOpen &&
      switch (_contentMode) {
        _ContentMode.chat ||
        _ContentMode.live ||
        _ContentMode.members ||
        _ContentMode.roomSettings => true,
        _ => false,
      };

  bool get _lobbyVisible =>
      !_settingsOpen &&
      _contentMode == _ContentMode.chat &&
      (_selectedServerId == null || _lobbyOpen);

  Widget _buildHeader({
    required bool compact,
    required _TitleLiveRoomDockBuilder liveRoomDockBuilder,
  }) {
    return _HomeTitleBar(
      windowController: widget.windowController,
      compact: compact,
      interactionLocked: _appUpdateDownloadInProgress,
      tabStrip: compact
          ? null
          : HomeRoomTabStrip(
              rooms: _servers,
              selectedRoomId: _roomContentVisible ? _selectedServerId : null,
              lobbyActive: _lobbyVisible,
              joinedLiveRoomId: _joinedLiveRoomId,
              roomDrafts: _sidebarRoomDrafts,
              createRoomActive:
                  !_settingsOpen && _contentMode == _ContentMode.createRoom,
              onLobbySelected: _openLobby,
              onRoomSelected: (room) =>
                  _selectServer(room, openContent: false),
              onCreateRoom: () => _openCreateRoom(openContent: false),
            ),
      voiceCapsule: compact ? null : liveRoomDockBuilder(320),
      searchActive: _searchPaletteOpen,
      onOpenSearch: _openSearchPalette,
      notificationsActive:
          !_settingsOpen && _contentMode == _ContentMode.notifications,
      hasPendingNotifications: _hasPendingRoomInvites,
      pendingNotificationCount: _pendingRoomNotificationCount,
      onOpenNotifications: () => _openNotifications(openContent: compact),
      settingsActive: _settingsOpen,
      onOpenSettings: () => _toggleSettings(openContent: compact),
      account: _HeaderAccountButton(
        user: _currentUser,
        inLive: _joinedLiveRoomId != null,
        reconnecting: _realtimeStatus == RealtimeConnectionStatus.reconnecting,
        requestRoundTrip: widget.app.serverClock.requestRoundTrip,
        logoutActive: _logoutConfirming,
        onOpenSettings: () =>
            _openSettingsSection(SettingsSection.profile, openContent: compact),
        onLogout: () => unawaited(_confirmLogout()),
      ),
    );
  }

  Widget _buildLobby({required bool compact, Widget? footer}) {
    return HomeLobby(
      currentUser: _currentUser,
      rooms: _servers,
      timestampNow: _serverNow,
      roomDrafts: _sidebarRoomDrafts,
      selectedRoomId: _selectedServerId,
      joinedLiveRoomId: _joinedLiveRoomId,
      loading: _loadingServers,
      error: _serverLoadError,
      compact: compact,
      footer: footer,
      onRoomSelected: (room) => _selectServer(room, openContent: compact),
      onCreateRoom: () => _openCreateRoom(openContent: compact),
    );
  }

  void _openLobby() {
    _setHomeState(() {
      _clearDeferredRoomNotificationVisualMarkersInState();
      _settingsOpen = false;
      _settingsAppUpdate = null;
      _superuserSettingsTarget = null;
      _contentMode = _ContentMode.chat;
      _lobbyOpen = true;
    });
  }

  Widget _wrapRoomContext(
    RoomContextTab tab, {
    required Widget child,
    VoidCallback? onNavigateBack,
  }) {
    final room = _selectedRoom;
    final card = _selectedServer;
    final live = _live;
    final liveParticipantCount =
        live?.participantCount ??
        room?.live.participantCount ??
        card?.liveParticipantCount;
    final liveAvatarPreview =
        live?.participants
            .map((participant) => participant.user)
            .take(5)
            .toList() ??
        room?.live.participants
            .map((participant) => participant.user)
            .take(5)
            .toList() ??
        card?.liveAvatarPreview ??
        const <UserSummary>[];
    return Column(
      children: [
        RoomContextBar(
          title: _roomTitle(room, card),
          avatarLabel: room != null
              ? room_display.roomAvatarLabel(room)
              : card != null
              ? room_display.roomCardAvatarLabel(card)
              : '聊天',
          avatarUrl: room?.avatarUrl ?? card?.avatarUrl,
          defaultAvatarKey:
              room?.defaultAvatarKey ??
              card?.defaultAvatarKey ??
              kDefaultAvatarPresetKey,
          memberCount: room?.memberCount ?? card?.memberCount,
          onlineMemberCount: room?.onlineMemberCount ?? card?.onlineMemberCount,
          liveParticipantCount: liveParticipantCount,
          liveAvatarPreview: liveAvatarPreview,
          voiceJoined:
              _joinedLiveRoomId != null &&
              _joinedLiveRoomId == _selectedServerId,
          activeTab: tab,
          hasPendingJoinRequests: _selectedRoomHasPendingJoinRequests,
          onTabSelected: (next) => _selectRoomContextTab(tab, next),
          onBack: onNavigateBack,
          compact: onNavigateBack != null,
        ),
        Expanded(child: child),
      ],
    );
  }

  void _selectRoomContextTab(RoomContextTab current, RoomContextTab next) {
    if (current == next) return;
    switch (next) {
      case RoomContextTab.chat:
        _openChat();
      case RoomContextTab.live:
        _openLiveChannel();
      case RoomContextTab.members:
        unawaited(_openRoomMembers());
      case RoomContextTab.settings:
        unawaited(_openRoomSettings());
    }
  }
}
