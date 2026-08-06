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
    _TitleLiveRoomDockBuilder? footerLiveRoomDockBuilder,
  }) {
    if (!_narrowContentOpen) {
      return KeyedSubtree(
        key: const ValueKey('home-narrow-room-list'),
        child: _buildSidebar(
          width: width,
          openContentOnSelect: true,
          header: _buildCompactSearchField(),
          bodyOverride: _hasSearchQuery && _searchExpanded
              ? _buildSearchResultsTapRegion()
              : null,
          footerMiddle: footerLiveRoomDockBuilder == null
              ? null
              : LayoutBuilder(
                  builder: (context, constraints) {
                    final dockMaxWidth = constraints.maxWidth
                        .clamp(0.0, double.infinity)
                        .toDouble();
                    return footerLiveRoomDockBuilder(
                      dockMaxWidth,
                      fillAvailable: true,
                    );
                  },
                ),
        ),
      );
    }

    return KeyedSubtree(
      key: const ValueKey('home-narrow-content'),
      child: _buildContentPane(onNavigateBack: _showNarrowRoomList),
    );
  }

  void _showNarrowRoomList() {
    _setHomeState(() => _narrowContentOpen = false);
  }

  bool _canHandleShellBack({required bool narrowLayout}) {
    return _appUpdateDownloadInProgress ||
        _fullScreenLiveTrack != null ||
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
            ? SettingsSection.profile
            : SettingsSection.about,
        initialAppUpdate: _settingsAppUpdate,
        stickerPackStore: widget.app.stickerPackStore,
        stickerImagePreviewOpener: _openStickerManagerImagePreview,
        onAppUpdateDownloadCancellationChanged:
            _handleAppUpdateDownloadCancellationChanged,
        currentUser: _currentUser,
        onUserUpdated: _handleUserUpdated,
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

    if (_selectedServerId == null) return const HomeContent();
    if (_contentMode == _ContentMode.members && _selectedRoom != null) {
      return RoomMembersDialog(
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
      );
    }
    if (_contentMode == _ContentMode.roomSettings && _selectedRoom != null) {
      final room = _selectedRoom!;
      return RoomSettingsDialog(
        key: ValueKey('home-room-settings-${room.id}'),
        controller: _roomsController,
        room: room,
        currentUser: _currentUser,
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
          ),
          title: '房间歌单',
          unavailableMessage: '房间歌单需要登录后从服务端读取',
          previewApi: _services.api is MusicTrackPreviewApi
              ? _services.api as MusicTrackPreviewApi
              : null,
          previewPlatformFactory: widget.musicTrackPreviewPlatformFactory,
        ),
      );
    }
    if (_contentMode == _ContentMode.live) {
      return LiveChannelPane(
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
        musicBoxRoom: _selectedRoom == null
            ? null
            : room_display.publicRoomFromRoomDetail(_selectedRoom!),
        musicTrackPreviewPlatformFactory:
            widget.musicTrackPreviewPlatformFactory,
        onMusicBoxStateChanged: _applyMusicBoxSnapshot,
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
      );
    }

    final visibleMessages = _visibleMessagesForMe(_messages);
    return ChatPane(
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
      hasPendingJoinRequests: _selectedRoomHasPendingJoinRequests,
      onSubmit: (value) => unawaited(_sendText(value)),
      onSendSticker: (sticker) => unawaited(_sendSticker(sticker)),
      onLoadStickers: () => unawaited(_loadStickerPacks(forceReload: true)),
      onRefreshStickers: () => unawaited(_loadStickerPacks(forceReload: true)),
      onStickerSourceChanged: _changeStickerSource,
      onStartVoice: () => unawaited(_startVoiceRecording()),
      onSendVoice: () => unawaited(_finishAndSendVoice()),
      onCancelVoice: () => unawaited(_cancelVoiceRecording()),
      onPickFile: () => unawaited(_pickAttachments()),
      onPasteFiles: _pasteAttachments,
      onCanPasteFiles: _canPasteAttachments,
      onRemoveAttachment: _removeAttachment,
      onRetryAttachment: _retryAttachment,
      onRetry: () => unawaited(_retryOpenSelectedRoom()),
      onNavigateBack: onNavigateBack,
      onOpenLiveChannel: _openLiveChannel,
      onOpenRoomMembers: () => unawaited(_openRoomMembers()),
      onOpenRoomSettings: () => unawaited(_openRoomSettings()),
      onViewedNewMessages: _clearSelectedRoomNewMessagePrompt,
      onResolveSenderProfile: _resolveSenderProfile,
      onResolveRoomProfile: _resolveRoomProfile,
      onEnterProfileRoom: _openNotificationRoom,
      senderProfileActionBuilder: _messageProfileAction,
      onMentionUser: _mentionUserFromAvatar,
      composerDropKey: _composerDropKey,
    );
  }

  Widget _buildSidebar({
    required double width,
    required bool openContentOnSelect,
    Widget? header,
    Widget? bodyOverride,
    Widget? footerMiddle,
  }) {
    return HomeSidebar(
      width: width,
      currentUser: _currentUser,
      servers: _sidebarServers,
      timestampNow: _serverNow,
      roomDrafts: _sidebarRoomDrafts,
      selectedServerId: _selectedServerId,
      joinedLiveRoomId: _joinedLiveRoomId,
      realtimeReconnecting:
          _realtimeStatus == RealtimeConnectionStatus.reconnecting,
      requestRoundTrip: widget.app.serverClock.requestRoundTrip,
      searchQuery: _filteringSidebarBySearch ? _searchQuery : '',
      loading: _loadingServers || _loadingSidebarSearch,
      error: _serverLoadError,
      settingsActive: _settingsOpen,
      createRoomActive:
          !_settingsOpen && _contentMode == _ContentMode.createRoom,
      notificationsActive:
          !_settingsOpen && _contentMode == _ContentMode.notifications,
      logoutActive: _logoutConfirming,
      hasPendingNotifications: _hasPendingRoomInvites,
      pendingNotificationCount: _pendingRoomNotificationCount,
      includeWindowChromeOffset: false,
      header: header,
      bodyOverride: bodyOverride,
      footerMiddle: footerMiddle,
      onServerSelected: (server) =>
          _selectServer(server, openContent: openContentOnSelect),
      onCreateRoom: () => _openCreateRoom(openContent: openContentOnSelect),
      onOpenNotifications: () =>
          _openNotifications(openContent: openContentOnSelect),
      onOpenSettings: () => _toggleSettings(openContent: openContentOnSelect),
      onLogout: () => unawaited(_confirmLogout()),
    );
  }
}
