package com.gangchat.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.ManagedUserAudioDeviceService
import com.gangchat.client.app.ManagedUserAudioSettingsStore
import com.gangchat.client.app.PersonalMusicPlaylistsController
import com.gangchat.client.app.RealtimeConnectionStatus
import com.gangchat.client.app.SettingsController
import com.gangchat.client.app.SettingsSection
import com.gangchat.client.app.publicRoomFromRoomDetail
import com.gangchat.client.app.roomAccessState
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.live.supportsLocalScreenShare
import com.gangchat.client.protocol.GlobalSearchResults
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.MusicTrackPreviewApi
import com.gangchat.client.protocol.PersonalMusicPlaylistApi
import com.gangchat.client.protocol.RoomMusicPlaylistApi
import com.gangchat.client.settings.MusicPlaylistsPanel
import com.gangchat.client.settings.SettingsPage
import com.gangchat.client.widgets.BuildContext

// Port of lib/src/home/home_shell_layout.dart.

internal val _HomeShellState._chatFileDownloadActions: ChatFileDownloadActions
    get() =
        ChatFileDownloadActions(
            onDownload = { message, attachment, index, resolvedUrl ->
                unawaited {
                    _downloadAttachment(message = message, attachment = attachment, index = index, resolvedUrl = resolvedUrl)
                }
            },
            onPause = { key -> _pauseDownload(key) },
            onResume = { key -> _resumeDownload(key) },
            onCancel = { key -> _cancelDownload(key) },
            onDismiss = { key -> _dismissDownload(key) },
        )

internal val _HomeShellState._chatVoicePlaybackActions: ChatVoicePlaybackActions
    get() =
        ChatVoicePlaybackActions(
            activeMessageId = if (_voicePlayback.playing) _voicePlayback.activeMessageId else null,
            activePosition = _voicePlayback.position,
            activeDuration = _voicePlayback.duration,
            onToggle = { messageId, resolvedUrl ->
                unawaited { _toggleVoicePlayback(messageId = messageId, resolvedUrl = resolvedUrl) }
            },
        )

private suspend fun _HomeShellState._openStickerManagerImagePreview(
    context: BuildContext,
    imageUrl: String,
    suggestedName: String,
    forceSquare: Boolean = false,
) {
    showChatImagePreview(
        context,
        imageUrl = imageUrl,
        suggestedName = suggestedName,
        actions = _imagePreviewActions,
        forceSquare = forceSquare,
    )
}

@Composable
internal fun _HomeShellState._buildNarrowLayout(width: Dp, footerLiveRoomDockBuilder: _TitleLiveRoomDockBuilder? = null) {
    if (!_narrowContentOpen) {
        key(homeNarrowRoomListTag) {
            _buildSidebar(
                width = width,
                openContentOnSelect = true,
                modifier = Modifier.testTag(homeNarrowRoomListTag),
                header = { _buildCompactSearchField() },
                bodyOverride = if (_hasSearchQuery && _searchExpanded) ({ _buildSearchResultsTapRegion() }) else null,
                footerMiddle =
                    if (footerLiveRoomDockBuilder == null) {
                        null
                    } else {
                        {
                            BoxWithConstraints {
                                val dockMaxWidth = maxWidth.coerceAtLeast(0.dp)
                                footerLiveRoomDockBuilder(dockMaxWidth, true)
                            }
                        }
                    },
            )
        }
        return
    }

    key(homeNarrowContentTag) {
        Box(Modifier.fillMaxSize().testTag(homeNarrowContentTag), propagateMinConstraints = true) {
            _buildContentPane(onNavigateBack = { _showNarrowRoomList() })
        }
    }
}

private fun _HomeShellState._showNarrowRoomList() {
    _setHomeState { _narrowContentOpen = false }
}

internal fun _HomeShellState._canHandleShellBack(narrowLayout: Boolean): Boolean =
    _appUpdateDownloadInProgress ||
        _fullScreenLiveTrack != null ||
        (_hasSearchQuery && _searchExpanded) ||
        _settingsOpen ||
        _contentMode != _ContentMode.chat ||
        (narrowLayout && _narrowContentOpen)

internal fun _HomeShellState._handleShellBack(narrowLayout: Boolean) {
    if (_appUpdateDownloadInProgress) return
    if (_fullScreenLiveTrack != null) {
        _exitLiveFullScreen()
        return
    }
    if (_hasSearchQuery && _searchExpanded) {
        _collapseSearch()
        return
    }
    if (_settingsOpen) {
        _closeSettings()
        return
    }

    when (_contentMode) {
        _ContentMode.superuserUserSettings -> _closeSuperuserUserSettings()
        _ContentMode.createRoom -> _closeCreateRoom()
        _ContentMode.notifications -> _closeNotifications()
        _ContentMode.live, _ContentMode.members, _ContentMode.roomSettings -> _openChat()
        _ContentMode.chat -> {
            if (narrowLayout && _narrowContentOpen) {
                _showNarrowRoomList()
            }
        }
    }
}

@Composable
internal fun _HomeShellState._buildContentPane(onNavigateBack: (() -> Unit)? = null) {
    if (_settingsOpen) {
        SettingsPage(
            isSubWindow = true,
            api = _services.api,
            apiBaseUrl = widget.app.apiBaseUrl,
            emailVerificationController = widget.app.emailVerificationController,
            passwordResetController = widget.app.passwordResetController,
            audioDeviceStore = widget.audioDeviceStore,
            closeBehaviorStore = widget.closeBehaviorStore,
            languageStore = widget.languageStore,
            androidSystemService = widget.androidSystemService,
            initialSection = if (_settingsAppUpdate == null) _settingsInitialSection else SettingsSection.about,
            initialMusicPlaylistId = _settingsInitialMusicPlaylistId,
            initialAppUpdate = _settingsAppUpdate,
            stickerPackStore = widget.app.stickerPackStore,
            stickerImagePreviewOpener = { context, imageUrl, suggestedName, forceSquare ->
                _openStickerManagerImagePreview(context, imageUrl, suggestedName, forceSquare)
            },
            onAppUpdateDownloadCancellationChanged = { token -> _handleAppUpdateDownloadCancellationChanged(token) },
            currentUser = _currentUser,
            onUserUpdated = { user -> _handleUserUpdated(user) },
            onMusicPlaylistsChanged = { _markMusicPlaylistsChanged() },
            onAccountDeleted = { _logout() },
            onScreenShareMaxHeightChanged = { height ->
                val liveSession = _liveSessionController
                unawaited { liveSession.setScreenShareMaxHeight(height) }
            },
            onScreenShareFrameRateChanged = { frameRate ->
                val liveSession = _liveSessionController
                unawaited { liveSession.setScreenShareFrameRate(frameRate) }
            },
            onVolumeChanged = { kind, volume -> _syncSettingsAudioVolumeToLive(kind, volume) },
            // The Settings picker already routes the native ADM (selectAudioInput/
            // Output). For inputs, also keep LiveSession's tracked capture device in
            // sync so a later mute/unmute republish stays on the chosen mic. Outputs
            // are global on the ADM and need no session-side action.
            onDeviceSelected = { kind, deviceId ->
                if (kind == "audioinput") {
                    val liveSession = _liveSessionController
                    unawaited { liveSession.setInputDeviceId(deviceId) }
                }
            },
            onClose = { _closeSettings() },
            musicTrackPreviewPlatformFactory = widget.musicTrackPreviewPlatformFactory,
        )
        return
    }

    val superuserTarget = _superuserSettingsTarget
    if (_contentMode == _ContentMode.superuserUserSettings && superuserTarget != null) {
        val target = superuserTarget
        key("superuser-user-settings-${target.id}") {
            SettingsPage(
                isSubWindow = true,
                api = _services.api,
                apiBaseUrl = widget.app.apiBaseUrl,
                emailVerificationController = widget.app.emailVerificationController,
                controller =
                    SettingsController(
                        api = _services.api,
                        apiBaseUrl = widget.app.apiBaseUrl,
                        stickerPackStore = widget.app.stickerPackStore,
                        managedUserId = target.id,
                    ),
                audioDeviceStore = ManagedUserAudioSettingsStore(api = _services.api, userId = target.id),
                audioDeviceService = ManagedUserAudioDeviceService(),
                closeBehaviorStore = widget.closeBehaviorStore,
                languageStore = widget.languageStore,
                stickerPackStore = widget.app.stickerPackStore,
                stickerImagePreviewOpener = { context, imageUrl, suggestedName, forceSquare ->
                    _openStickerManagerImagePreview(context, imageUrl, suggestedName, forceSquare)
                },
                fileSelectionService = _fileSelectionService,
                onClose = { _closeSuperuserUserSettings() },
                onAccountDeleted = {
                    _setHomeState {
                        val results = _searchResults
                        if (results != null) {
                            _searchResults =
                                GlobalSearchResults(
                                    myRooms = results.myRooms,
                                    publicRooms = results.publicRooms,
                                    userSettings = results.userSettings.filter { user -> user.id != target.id },
                                    messages = results.messages,
                                    files = results.files,
                                    nextCursors = results.nextCursors,
                                    totalCounts = results.totalCounts,
                                )
                        }
                        _superuserSettingsTarget = null
                        _contentMode = _ContentMode.chat
                    }
                },
                onUserUpdated = { updated ->
                    _setHomeState {
                        _superuserSettingsTarget = updated.toSummary()
                        val results = _searchResults
                        if (results != null) {
                            _searchResults =
                                GlobalSearchResults(
                                    myRooms = results.myRooms,
                                    publicRooms = results.publicRooms,
                                    userSettings =
                                        results.userSettings.map { user ->
                                            if (user.id == updated.id) updated.toSummary() else user
                                        },
                                    messages = results.messages,
                                    files = results.files,
                                    nextCursors = results.nextCursors,
                                    totalCounts = results.totalCounts,
                                )
                        }
                    }
                },
            )
        }
        return
    }

    if (_contentMode == _ContentMode.createRoom) {
        key("home-create-room-settings-dialog") {
            RoomSettingsDialog.create(
                controller = _roomsController,
                currentUser = _currentUser,
                embedded = true,
                onClose = { _closeCreateRoom() },
                onResult = { result -> _handleCreateRoomResult(result) },
                stickerImagePreviewOpener = { context, imageUrl, suggestedName, forceSquare ->
                    _openStickerManagerImagePreview(context, imageUrl, suggestedName, forceSquare)
                },
            )
        }
        return
    }

    if (_contentMode == _ContentMode.notifications) {
        HomeNotificationsPane(
            invites = _notificationInvites,
            applications = _notificationApplications,
            roomNotifications = _notificationRoomEvents,
            currentUser = _currentUser,
            loading = _loadingNotifications,
            error = _notificationError,
            busyInviteId = _busyNotificationInviteId,
            busyApplicationId = _busyNotificationApplicationId,
            onClose = { _closeNotifications() },
            onRefresh = { unawaited { _loadNotifications(clearVisualReadMarkers = true) } },
            onReviewInvite = { invite, accept -> _reviewNotificationInvite(invite, accept) },
            onWithdrawApplication = { application -> _withdrawNotificationApplication(application) },
            onCopyNotification = { item -> _copyNotification(item) },
            onDeleteNotification = { item -> _deleteNotification(item) },
            onDeleteNotifications = { items -> _deleteNotifications(items) },
            onResolveRoomProfile = { room -> _resolveRoomProfile(room) },
            onResolveRoomUserProfile = { roomId, user -> _resolveRoomUserProfile(roomId, user) },
            onOpenRoom = { room -> _openNotificationRoom(room) },
            onOpenRoomEvent = { notification -> _openNotificationRoomEvent(notification) },
        )
        return
    }

    if (_selectedServerId == null) {
        HomeContent(Modifier.fillMaxSize())
        return
    }
    val selectedRoom = _selectedRoom
    if (_contentMode == _ContentMode.members && selectedRoom != null) {
        RoomMembersDialog(
            controller = _roomsController,
            room = selectedRoom,
            currentUser = _currentUser,
            initialLive = _live ?: selectedRoom.live,
            initialSearchQuery = _membersInitialSearchQuery,
            hasPendingJoinRequests = _selectedRoomHasPendingJoinRequests,
            reloadToken = _membersReloadToken,
            embedded = true,
            onClose = { _openChat() },
            onChanged = { unawaited { _loadServers() } },
            onPendingJoinRequestsChanged = { hasPending -> _handleSelectedJoinRequestsChanged(hasPending) },
            onOpenRoom = { room -> _openNotificationRoom(room) },
        )
        return
    }
    if (_contentMode == _ContentMode.roomSettings && selectedRoom != null) {
        val room = selectedRoom
        key("home-room-settings-${room.id}") {
            RoomSettingsDialog(
                controller = _roomsController,
                room = room,
                currentUser = _currentUser,
                initialSection = _roomSettingsInitialSection,
                isInLive = _joinedLiveRoomId == room.id,
                onRoomUpdated = { updated -> _applyManagedRoomUpdated(updated) },
                onLeaveLive = {
                    if (_joinedLiveRoomId == room.id) _leaveLive()
                },
                embedded = true,
                onClose = { _openChat() },
                onResult = { result -> _handleRoomSettingsResult(room.id, result) },
                stickerImagePreviewOpener = { context, imageUrl, suggestedName, forceSquare ->
                    _openStickerManagerImagePreview(context, imageUrl, suggestedName, forceSquare)
                },
                messageHistoryBuilder = { _ ->
                    _RoomMessageHistoryPane(
                        room = room,
                        currentUser = _currentUser,
                        roomsController = _roomsController,
                        messagesController = _messagesController,
                        clipboardService = _clipboardService,
                        fileDownloads = _fileDownloads,
                        downloadActions = _chatFileDownloadActions,
                        voicePlaybackActions = _chatVoicePlaybackActions,
                        imagePreviewActions = _imagePreviewActions,
                        messageActions = _chatMessageActions,
                        onResolveRoomProfile = { profileRoom -> _resolveRoomProfile(profileRoom) },
                        onResolveRoomUserProfile = { roomId, user -> _resolveRoomUserProfile(roomId, user) },
                        onOpenRoom = { profileRoom -> _openNotificationRoom(profileRoom) },
                        profileActionBuilder = { user -> _messageProfileAction(user) },
                        onJumpToMessage = { messageId ->
                            val server = _selectedServer
                            if (server != null) {
                                unawaited { _openRoom(server, openContent = true, focusMessageId = messageId) }
                            }
                        },
                    )
                },
                musicPlaylistsBuilder = { _ ->
                    val api = _services.api
                    key("room-music-playlists-${room.id}") {
                        MusicPlaylistsPanel(
                            controller =
                                PersonalMusicPlaylistsController.room(
                                    roomApi = api as? RoomMusicPlaylistApi,
                                    personalApi = api as? PersonalMusicPlaylistApi,
                                    roomId = room.id,
                                    canManage = roomAccessState(room = room, currentUser = _currentUser).canManageRoom,
                                    searchTracks = { keyword, source ->
                                        api.searchMusicBox(roomId = room.id, keyword = keyword, source = source, count = 20, page = 1)
                                    },
                                    onChanged = { _markMusicPlaylistsChanged() },
                                ),
                            initialPlaylistId = _roomSettingsInitialMusicPlaylistId,
                            title = "房间歌单",
                            unavailableMessage = "房间歌单需要登录后从服务端读取",
                            previewApi = api as? MusicTrackPreviewApi,
                            previewPlatformFactory = widget.musicTrackPreviewPlatformFactory,
                            shareApi = api,
                            currentUser = _currentUser,
                        )
                    }
                },
            )
        }
        return
    }
    if (_contentMode == _ContentMode.live) {
        val canManageRoom =
            selectedRoom != null && roomAccessState(room = selectedRoom, currentUser = _currentUser).canManageRoom
        val liveSession = _liveSessionController
        LiveChannelPane(
            title = _roomTitle(selectedRoom, _selectedServer),
            avatarUrl = selectedRoom?.avatarUrl ?: _selectedServer?.avatarUrl,
            live = _live ?: selectedRoom?.live,
            currentUser = _currentUser,
            loading = _loadingRoom,
            joined = _joinedLiveRoomId == _selectedServerId,
            joining = _joiningLive,
            leaving = _leavingLive,
            micMuted = _micMuted,
            headphonesMuted = _headphonesMuted,
            voiceBlocked = _voiceBlocked,
            cameraOn = _cameraOn,
            screenSharing = _screenSharing,
            speakingUserIds = liveSession.speakingIdentities,
            connectedParticipantIds = liveSession.connectedParticipantIdentities,
            liveKitMicMutedByParticipantId = liveSession.micMutedByIdentity,
            videoTracks = liveSession.videoTracks,
            stageSelection = _liveStageSelections[_selectedServerId],
            // Dart suspended the stage video only on Windows while the
            // full-screen overlay rendered the same track.
            suspendStageVideo = false,
            onStageSelectionChanged = { selection -> _setLiveStageSelection(selection) },
            onEnterFullScreen = { track -> _enterLiveFullScreen(track) },
            onBackToChat = { _openChat() },
            onJoin = { unawaited { _joinLive("live_panel") } },
            onLeave = { unawaited { _leaveLive() } },
            onToggleMic = if (_voiceBlocked) null else ({ _toggleMicMute() }),
            onToggleHeadphones = { _toggleHeadphonesMute() },
            onToggleCamera = { unawaited { _toggleCamera() } },
            onFlipCamera = if (liveSession.canFlipCamera) ({ _flipLocalCamera() }) else null,
            onSetLocalCameraMirrored = { mirrored -> _setLocalCameraMirrored(mirrored) },
            onToggleShare = if (supportsLocalScreenShare) ({ unawaited { _toggleScreenShare() } }) else null,
            musicBox = _musicBox,
            musicBoxOpen = _musicBoxOpen,
            musicBoxSearchController = _musicBoxSearchController,
            musicBoxSearchResults = _musicBoxSearchResults,
            musicBoxSearching = _musicBoxSearching,
            musicBoxSearchError = _musicBoxSearchError,
            musicBoxSource = _musicBoxSource,
            onToggleMusicBox = { _toggleMusicBoxPanel() },
            onMusicBoxTogglePlayback = { _toggleMusicBoxPlayback() },
            onMusicBoxSkip = { unawaited { _controlMusicBox("skip") } },
            onMusicBoxPrevious = { unawaited { _controlMusicBox("previous") } },
            onMusicBoxModeChanged = { mode -> unawaited { _changeMusicBoxPlaybackMode(mode) } },
            musicBoxController = _musicBoxController,
            musicBoxRoomId = _selectedServerId,
            musicPlaylistsRevision = _musicPlaylistsRevision,
            musicBoxRoom = selectedRoom?.let { publicRoomFromRoomDetail(it) },
            onMusicBoxStateChanged = { state -> _applyMusicBoxSnapshot(state) },
            onCreateFirstRoomMusicPlaylist =
                if (canManageRoom) {
                    { _openRoomSettings(initialSection = RoomSettingsSection.playlists) }
                } else {
                    null
                },
            onCreateFirstPersonalMusicPlaylist = { _openSettingsSection(SettingsSection.playlists, openContent = true) },
            onEditRoomMusicPlaylist =
                if (canManageRoom) {
                    { playlistId ->
                        _openRoomSettings(
                            initialSection = RoomSettingsSection.playlists,
                            initialMusicPlaylistId = playlistId,
                        )
                    }
                } else {
                    null
                },
            onEditPersonalMusicPlaylist = { playlistId ->
                _openSettingsSection(SettingsSection.playlists, openContent = true, initialMusicPlaylistId = playlistId)
            },
            onMusicBoxQueueResult = { result -> unawaited { _queueMusicBoxTrack(result) } },
            onMusicBoxRemoveItem = { item -> unawaited { _removeMusicBoxItem(item) } },
            onMusicBoxSourceChanged = { source -> _changeMusicBoxSource(source) },
            inputVolume = liveSession.inputVolume,
            outputVolume = liveSession.outputVolume,
            musicBoxVolume = liveSession.musicBoxVolume,
            screenShareVolume = liveSession.screenShareVolume,
            onInputVolumeChanged = { volume -> _changeInputVolume(volume) },
            onOutputVolumeChanged = { volume -> _changeOutputVolume(volume) },
            onMusicBoxVolumeChanged = { volume -> unawaited { liveSession.setMusicBoxVolume(volume) } },
            onScreenShareVolumeChanged = { volume -> _changeScreenShareVolume(volume) },
            onScreenShareMuteToggled = { _toggleScreenShareAudioMute() },
            participantVoiceVolume = { userId -> _participantVoiceVolume(userId) },
            onParticipantVoiceVolumeChanged = { userId, volume -> _changeParticipantVoiceVolume(userId, volume) },
            onParticipantVoiceMuteToggled = { userId -> _toggleParticipantVoiceMute(userId) },
            canModerateParticipant = { participant -> _canModerateLiveParticipant(participant) },
            onToggleParticipantMicModeration = { participant -> unawaited { _toggleLiveParticipantMicModeration(participant) } },
            onToggleParticipantHeadphonesModeration = { participant ->
                unawaited { _toggleLiveParticipantHeadphonesModeration(participant) }
            },
            canRemoveParticipant = { participant -> _canRemoveLiveParticipant(participant) },
            onRemoveParticipant = { participant -> unawaited { _removeLiveParticipant(participant) } },
            onResolveParticipantProfile = { user -> _resolveSenderProfile(user) },
            onResolveParticipantRoomProfile = { room -> _resolveRoomProfile(room) },
            onEnterParticipantProfileRoom = { room -> _openNotificationRoom(room) },
            participantProfileActionBuilder = { user -> _messageProfileAction(user) },
        )
        return
    }

    val visibleMessages = _visibleMessagesForMe(_messages)
    val canManageSelectedRoom =
        selectedRoom != null && roomAccessState(room = selectedRoom, currentUser = _currentUser).canManageRoom
    ChatPane(
        currentUser = _currentUser,
        timestampNow = _serverNow,
        roomCard = _selectedServer,
        room = selectedRoom,
        live = _live,
        messages = visibleMessages,
        newMessageCount = _visibleNewMessageCount(visibleMessages),
        focusMessageId = _focusedMessageId,
        onFocusMessageHandled = { messageId -> _handleFocusMessageHandled(messageId) },
        fileTransfers = _fileTransfers,
        fileDownloads = _fileDownloads,
        downloadActions = _chatFileDownloadActions,
        imagePreviewActions = _imagePreviewActions,
        messageActions = _chatMessageActions,
        voicePlaybackActions = _chatVoicePlaybackActions,
        loading = _loadingRoom,
        error = _roomError,
        sending = _sending,
        sendError = _sendError,
        composerController = _composerController,
        composerPanelController = _composerPanelController,
        stickerPanel = _stickerPanelState,
        voiceState = _voiceState,
        composerAttachments = _stagedAttachmentViews,
        composerQuotes = _selectedComposerQuotes,
        onRemoveComposerQuote = { messageId -> _removeSelectedComposerQuote(messageId) },
        composerComponent = _selectedComposerComponent,
        onRemoveComposerComponent = { _removeSelectedComposerComponent() },
        fileActionHighlighted = _pickingAttachments || _stagedAttachments.isNotEmpty(),
        mentionOptions = _composerMentionOptions,
        mentionMembers = if (_composerMentionMembersRoomId == _selectedServerId) _composerMentionMembers else emptyList(),
        mentionMembersReady = _composerMentionMembersRoomId == _selectedServerId,
        mentionLoading = _composerMentionQuery != null && _loadingComposerMentionMembers,
        mentionSelectedIndex = _composerMentionSelectedIndex,
        onSelectMention = { option -> _selectComposerMention(option) },
        composerInputFormatters = listOf(_ConfirmedMentionBackspaceFormatter(_composerController)),
        onNavigateMentionSelection = { navigation -> _navigateComposerMentionSelection(navigation) },
        onConfirmMentionSelection = { action -> _confirmComposerMentionSelection(action) },
        onHighlightMentionSelection = { index -> _highlightComposerMentionSelection(index) },
        hasPendingJoinRequests = _selectedRoomHasPendingJoinRequests,
        onSubmit = { value -> unawaited { _sendText(value) } },
        onSendSticker = { sticker -> unawaited { _sendSticker(sticker) } },
        onLoadStickers = { unawaited { _loadStickerPacks(forceReload = true) } },
        onRefreshStickers = { unawaited { _loadStickerPacks(forceReload = true) } },
        onStickerSourceChanged = { source -> _changeStickerSource(source) },
        onUploadFirstPersonalSticker = { _openSettingsSection(SettingsSection.stickers, openContent = true) },
        onUploadFirstRoomSticker =
            if (canManageSelectedRoom) {
                { _openRoomSettings(initialSection = RoomSettingsSection.stickers) }
            } else {
                null
            },
        onStartVoice = { unawaited { _startVoiceRecording() } },
        onSendVoice = { unawaited { _finishAndSendVoice() } },
        onCancelVoice = { unawaited { _cancelVoiceRecording() } },
        onPickFile = { unawaited { _pickAttachments() } },
        onPasteFiles = { _pasteAttachments() },
        onCanPasteFiles = { _canPasteAttachments() },
        onRemoveAttachment = { id -> _removeAttachment(id) },
        onRetryAttachment = { id -> _retryAttachment(id) },
        onRetry = { unawaited { _retryOpenSelectedRoom() } },
        onNavigateBack = onNavigateBack,
        onOpenLiveChannel = { _openLiveChannel() },
        onOpenRoomMembers = { _openRoomMembers() },
        onOpenRoomSettings = { _openRoomSettings() },
        onViewedNewMessages = { _clearSelectedRoomNewMessagePrompt() },
        onResolveSenderProfile = { user -> _resolveSenderProfile(user) },
        onResolveRoomProfile = { room -> _resolveRoomProfile(room) },
        onEnterProfileRoom = { room -> _openNotificationRoom(room) },
        senderProfileActionBuilder = { user -> _messageProfileAction(user) },
        onMentionUser = { user -> _mentionUserFromAvatar(user) },
    )
}

@Composable
internal fun _HomeShellState._buildSidebar(
    width: Dp,
    openContentOnSelect: Boolean,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    bodyOverride: (@Composable () -> Unit)? = null,
    footerMiddle: (@Composable () -> Unit)? = null,
) {
    HomeSidebar(
        width = width,
        currentUser = _currentUser,
        servers = _sidebarServers,
        timestampNow = _serverNow,
        roomDrafts = _sidebarRoomDrafts,
        selectedServerId = _selectedServerId,
        joinedLiveRoomId = _joinedLiveRoomId,
        realtimeReconnecting = _realtimeStatus == RealtimeConnectionStatus.reconnecting,
        requestRoundTrip = widget.app.serverClock.requestRoundTrip,
        searchQuery = _sidebarSearchQuery,
        loading = _loadingServers || _loadingSidebarSearch,
        error = _serverLoadError,
        settingsActive = _settingsOpen,
        createRoomActive = !_settingsOpen && _contentMode == _ContentMode.createRoom,
        notificationsActive = !_settingsOpen && _contentMode == _ContentMode.notifications,
        logoutActive = _logoutConfirming,
        hasPendingNotifications = _hasPendingRoomInvites,
        pendingNotificationCount = _pendingRoomNotificationCount,
        includeWindowChromeOffset = false,
        header = header,
        bodyOverride = bodyOverride,
        footerMiddle = footerMiddle,
        onServerSelected = { server -> _selectServer(server, openContent = openContentOnSelect) },
        onCreateRoom = { _openCreateRoom(openContent = openContentOnSelect) },
        onOpenNotifications = { _openNotifications(openContent = openContentOnSelect) },
        onOpenSettings = { _toggleSettings(openContent = openContentOnSelect) },
        onLogout = { unawaited { _confirmLogout() } },
        modifier = modifier,
    )
}

private fun _roomTitle(room: RoomDetail?, card: RoomCard?): String {
    val detailRemark = room?.remarkName?.trim()
    if (detailRemark != null && detailRemark.isNotEmpty()) return detailRemark
    val detailTitle = room?.name?.trim()
    if (detailTitle != null && detailTitle.isNotEmpty()) return detailTitle
    val cardTitle = card?.displayName?.trim()
    if (cardTitle != null && cardTitle.isNotEmpty()) return cardTitle
    return "聊天"
}
