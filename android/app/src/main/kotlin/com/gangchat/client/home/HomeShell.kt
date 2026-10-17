package com.gangchat.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gangchat.client.app.AndroidPushRegistrationController
import com.gangchat.client.app.AudioDeviceStore
import com.gangchat.client.app.AuthenticatedAppContext
import com.gangchat.client.app.AuthenticatedAppServices
import com.gangchat.client.app.AvailableAppUpdate
import com.gangchat.client.app.CloseBehaviorStore
import com.gangchat.client.app.ComposerAttachmentStatus
import com.gangchat.client.app.FileDownloadsController
import com.gangchat.client.app.FileTransferState
import com.gangchat.client.app.GlobalSearchCategory
import com.gangchat.client.app.GlobalSearchController
import com.gangchat.client.app.LanguagePreferenceStore
import com.gangchat.client.app.LiveController
import com.gangchat.client.app.LivePresenceSpeechPlayer
import com.gangchat.client.app.LiveSessionController
import com.gangchat.client.app.LiveStateRequestQueue
import com.gangchat.client.app.MediaCacheController
import com.gangchat.client.app.MessageMentionOption
import com.gangchat.client.app.MessageMentionQuery
import com.gangchat.client.app.MessagesController
import com.gangchat.client.app.MusicBoxController
import com.gangchat.client.app.MusicTrackPreviewController
import com.gangchat.client.app.MusicTrackPreviewPlatformFactory
import com.gangchat.client.app.RealtimeConnectionStatus
import com.gangchat.client.app.RealtimeEvent
import com.gangchat.client.app.RealtimeMessageNotificationTracker
import com.gangchat.client.app.RealtimeService
import com.gangchat.client.app.RoomsController
import com.gangchat.client.app.SettingsSection
import com.gangchat.client.app.StickerPacksController
import com.gangchat.client.app.StickerPanelLoadState
import com.gangchat.client.app.VoiceRecorderController
import com.gangchat.client.app.VoiceRecorderState
import com.gangchat.client.app.joinedLiveRoomSummary
import com.gangchat.client.app.liveParticipantByUserId
import com.gangchat.client.app.liveScreenShareViewers
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.StreamSubscription
import com.gangchat.client.foundation.completedFuture
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.live.LivePresenceAudioCoordinator
import com.gangchat.client.live.LivePresenceSoundPlayer
import com.gangchat.client.live.LivePresenceSoundService
import com.gangchat.client.live.LiveVideoTrack
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.GlobalSearchResults
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageQuote
import com.gangchat.client.protocol.MusicBoxSearchResult
import com.gangchat.client.protocol.MusicBoxState
import com.gangchat.client.protocol.MusicTrackPreviewApi
import com.gangchat.client.protocol.RoomApplication
import com.gangchat.client.protocol.RoomCard
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomEventNotification
import com.gangchat.client.protocol.RoomInvite
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.UploadTransferController
import com.gangchat.client.protocol.UploadedAsset
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.shell.AndroidSystemService
import com.gangchat.client.shell.AndroidTaskRemovalRequest
import com.gangchat.client.shell.ClipboardService
import com.gangchat.client.shell.DefaultMusicTrackPreviewPlatformFactory
import com.gangchat.client.shell.FileSelectionService
import com.gangchat.client.shell.FullScreenMediaOrientation
import com.gangchat.client.shell.MessageNotificationSoundPlayer
import com.gangchat.client.shell.MessageNotificationSoundService
import com.gangchat.client.shell.ReleaseDownloadCancellationToken
import com.gangchat.client.shell.SelectedFile
import com.gangchat.client.shell.SystemLivePresenceSpeechPlayer
import com.gangchat.client.shell.VoicePlaybackService
import com.gangchat.client.shell.VoicePlaybackSnapshot
import com.gangchat.client.ui.ChatComposerController
import com.gangchat.client.ui.MediaCacheScope
import com.gangchat.client.ui.UiColors
import com.gangchat.client.widgets.CompositionTimer
import com.gangchat.client.widgets.PopScope
import com.gangchat.client.widgets.SafeArea
import com.gangchat.client.widgets.Scaffold
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.TextInputFormatter
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.ignorePointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job

// Port of lib/src/home/home_shell.dart. Its parts are ported as
// HomeShell<Part>.kt files of extension functions on [_HomeShellState]:
// Realtime, RoomActions, LiveActions, MusicBox, Messages, Downloads,
// ImagePreview, Notifications, RoomMessageHistory, Search, Layout, TitleBar
// and JoinDialog.
//
// PORT: the desktop parts are not ported: the window controller (close
// request / tray exit handlers, the close-behaviour prompt, minimise to tray,
// exiting the program, window capture exclusion, the window title bar's
// drag regions and buttons), file drops and the web paste hook. Android only
// shows the title bar's search field and live room dock on tablets.

internal val _windowEdgeBorder = Color(0xFF303842)
internal const val _defaultLiveVolumeRestore = 0.5

@Composable
private fun _withAndroidBottomSafeArea(
    enabled: Boolean,
    preserveKeyboardViewport: Boolean = false,
    child: @Composable () -> Unit,
) {
    if (!enabled) {
        child()
        return
    }
    HomeKeyboardOverlayViewport(enabled = preserveKeyboardViewport) {
        SafeArea(top = false, left = false, right = false, bottom = true) { child() }
    }
}

internal enum class _ContentMode {
    chat,
    live,
    members,
    roomSettings,
    createRoom,
    notifications,
    superuserUserSettings,
}

/**
 * The defaults Dart wrote as `const` constructor calls (canonical instances,
 * so a rebuilt shell keeps comparing equal services).
 */
internal object _HomeShellDefaults {
    val androidSystemService: AndroidSystemService by lazy { AndroidSystemService() }
    val musicTrackPreviewPlatformFactory: MusicTrackPreviewPlatformFactory by lazy { DefaultMusicTrackPreviewPlatformFactory() }
}

/** Test tag of the shell's system-back scope (Dart's `ValueKey('android-shell-back-scope')`). */
const val androidShellBackScopeTag = "android-shell-back-scope"

/** Test tag of the narrow room list (Dart's `ValueKey('home-narrow-room-list')`). */
const val homeNarrowRoomListTag = "home-narrow-room-list"

/** Test tag of the narrow content screen (Dart's `ValueKey('home-narrow-content')`). */
const val homeNarrowContentTag = "home-narrow-content"

/**
 * The authenticated home: title bar, room sidebar and the content pane (chat,
 * live channel, members, room settings, create room, notifications, settings),
 * with the compact one-pane-at-a-time layout on phones and the sidebar +
 * content layout on tablets. It owns the per-session services and every
 * Android integration of the signed-in app: system back, notification taps,
 * the background service, task removal, push registration and message
 * notification sounds.
 */
@Composable
fun HomeShell(
    app: AuthenticatedAppContext,
    audioDeviceStore: AudioDeviceStore,
    closeBehaviorStore: CloseBehaviorStore,
    languageStore: LanguagePreferenceStore,
    modifier: Modifier = Modifier,
    liveSessionController: LiveSessionController? = null,
    livePresenceSoundPlayer: LivePresenceSoundPlayer? = null,
    livePresenceSpeechPlayer: LivePresenceSpeechPlayer? = null,
    messageNotificationSoundPlayer: MessageNotificationSoundPlayer? = null,
    realtime: RealtimeService? = null,
    androidSystemService: AndroidSystemService = _HomeShellDefaults.androidSystemService,
    detectedAppUpdate: AvailableAppUpdate? = null,
    onDetectedAppUpdateShown: (() -> Unit)? = null,
    musicTrackPreviewPlatformFactory: MusicTrackPreviewPlatformFactory = _HomeShellDefaults.musicTrackPreviewPlatformFactory,
) = StatefulWidget(
    HomeShellWidget(
        app = app,
        audioDeviceStore = audioDeviceStore,
        closeBehaviorStore = closeBehaviorStore,
        languageStore = languageStore,
        liveSessionController = liveSessionController,
        livePresenceSoundPlayer = livePresenceSoundPlayer,
        livePresenceSpeechPlayer = livePresenceSpeechPlayer,
        messageNotificationSoundPlayer = messageNotificationSoundPlayer,
        realtime = realtime,
        androidSystemService = androidSystemService,
        detectedAppUpdate = detectedAppUpdate,
        onDetectedAppUpdateShown = onDetectedAppUpdateShown,
        musicTrackPreviewPlatformFactory = musicTrackPreviewPlatformFactory,
        modifier = modifier,
    ),
) { _HomeShellState() }

class HomeShellWidget(
    val app: AuthenticatedAppContext,
    val audioDeviceStore: AudioDeviceStore,
    val closeBehaviorStore: CloseBehaviorStore,
    val languageStore: LanguagePreferenceStore,
    val liveSessionController: LiveSessionController?,
    val livePresenceSoundPlayer: LivePresenceSoundPlayer?,
    val livePresenceSpeechPlayer: LivePresenceSpeechPlayer?,
    val messageNotificationSoundPlayer: MessageNotificationSoundPlayer?,
    val realtime: RealtimeService?,
    val androidSystemService: AndroidSystemService,
    val detectedAppUpdate: AvailableAppUpdate?,
    val onDetectedAppUpdateShown: (() -> Unit)?,
    val musicTrackPreviewPlatformFactory: MusicTrackPreviewPlatformFactory,
    val modifier: Modifier,
)

internal class _HomeShellState : ViewState<HomeShellWidget>() {
    internal lateinit var _services: AuthenticatedAppServices
    internal lateinit var _currentUser: CurrentUser
    internal val _composerController = _MentionTextEditingController()
    internal val _composerPanelController = ChatComposerController()
    internal val _titleSearchController = TextEditingController()
    internal var _lastTitleSearchText = ""
    internal val _searchTapRegionGroup = Any()
    internal var _realtimeEvents: StreamSubscription<RealtimeEvent>? = null
    internal var _accountSuspensionLogoutInProgress = false
    internal var _realtimeStatusEvents: StreamSubscription<RealtimeConnectionStatus>? = null
    internal var _androidNotificationSelections: StreamSubscription<String>? = null
    internal var _androidTaskRemovals: StreamSubscription<AndroidTaskRemovalRequest>? = null
    internal var _androidPushRegistration: AndroidPushRegistrationController? = null
    internal var _isAppForeground = true
    internal var _pendingNotificationRoomId: String? = null

    internal var _servers: List<RoomCard> = emptyList()
    internal var _loadingServers = false
    internal var _serverLoadError: String? = null
    internal var _selectedServerId: String? = null
    internal var _selectedRoom: RoomDetail? = null
    internal var _live: LiveState? = null
    internal var _messages: List<Message> = emptyList()
    internal var _messageDrafts: Map<String, String> = emptyMap()
    internal var _messageQuoteDrafts: Map<String, List<MessageQuote>> = emptyMap()
    internal var _messageComponentDrafts: Map<String, Message> = emptyMap()
    internal var _copiedMessageComponent: Message? = null
    internal var _stagedAttachmentDrafts: Map<String, List<_StagedAttachment>> = emptyMap()
    internal var _updatingComposerFromDraft = false
    internal var _composerMentionQuery: MessageMentionQuery? = null
    internal var _composerMentionOptions: List<MessageMentionOption> = emptyList()
    internal var _composerMentionSelectedIndex = 0
    internal var _composerMentionMembers: List<RoomMember> = emptyList()
    internal var _composerMentionMembersRoomId: String? = null
    internal var _loadingComposerMentionMembersRoomId: String? = null
    internal var _loadingComposerMentionMembers = false
    internal var _composerMentionMembersSerial = 0
    internal val _locallyDeletedMessageKeys = LinkedHashSet<String>()
    internal var _selectedRoomNewMessageCount = 0
    internal var _focusedMessageId: String? = null
    internal var _fileTransfers: Map<String, FileTransferState> = emptyMap()

    // Active file downloads, keyed by [fileDownloadKey]. Kept apart from
    // [_fileTransfers] (outgoing uploads) so the two never collide on a
    // message id and each tile can pick the transfer that applies to it.
    internal var _fileDownloads: Map<String, FileTransferState> = emptyMap()
    internal val _liveStageSelections = LinkedHashMap<String, LiveStageSelection?>()
    internal var _liveScreenViewSyncTail: Deferred<Unit> = completedFuture(Unit)
    internal var _liveFullScreenTransitionTail: Deferred<Unit> = completedFuture(Unit)
    internal var _liveFullScreenTransitionGeneration = 0
    internal var _fullScreenLiveTrack: LiveVideoTrack? = null
    internal var _loadingRoom = false
    internal var _roomError: String? = null
    internal var _sending = false
    internal var _sendError: String? = null
    internal var _stickerPanelState = StickerPanelLoadState()
    internal var _voiceState = VoiceRecorderState()
    internal val _voicePlaybackService = VoicePlaybackService()
    internal lateinit var _livePresenceSoundPlayer: LivePresenceSoundPlayer
    internal lateinit var _livePresenceSpeechPlayer: LivePresenceSpeechPlayer
    internal lateinit var _livePresenceAudioCoordinator: LivePresenceAudioCoordinator
    internal lateinit var _messageNotificationSoundPlayer: MessageNotificationSoundPlayer
    internal val _messageNotificationTracker = RealtimeMessageNotificationTracker()
    internal var _livePresenceEventTail: Deferred<Unit> = completedFuture(Unit)
    internal var _voicePlayback = VoicePlaybackSnapshot()
    internal var _voiceTicker: Job? = null
    internal var _voiceStartedAt: DateTime? = null

    // Files picked for the next message, kept in pick order. Each uploads as
    // soon as it is picked; the message send later just collects the finished
    // assets. See [_StagedAttachment].
    internal val _stagedAttachments = mutableListOf<_StagedAttachment>()
    internal val _clipboardService = ClipboardService()
    internal val _fileSelectionService = FileSelectionService()
    internal var _clipboardImagePasteSerial = 0
    internal var _pickingAttachments = false
    internal var _settingsOpen = false
    internal var _settingsInitialSection = SettingsSection.profile
    internal var _settingsInitialMusicPlaylistId: String? = null
    internal var _settingsAppUpdate: AvailableAppUpdate? = null
    internal var _appUpdateDownloadCancellationToken: ReleaseDownloadCancellationToken? = null
    internal var _logoutConfirming = false
    internal var _exitingApplication = false
    internal var _narrowContentOpen = false
    internal var _compactLayout = false
    internal var _auxiliaryOpenedFromNarrowSidebar = false
    internal var _contentMode = _ContentMode.chat
    internal var _roomSettingsInitialSection = RoomSettingsSection.info
    internal var _roomSettingsInitialMusicPlaylistId: String? = null
    internal var _superuserSettingsTarget: UserSummary? = null

    // Bumped to ask an open members panel to reload (e.g. after a
    // `room_join_requests_updated`, `room_role_changed`, or
    // `room_member_profile_changed` SSE event). The panel watches this via
    // didUpdateWidget and re-pulls its members/requests.
    internal var _membersReloadToken = 0

    // Invalidates saved-playlist summaries and tracks cached by the open music
    // box after a successful edit in Settings or Room Settings. Playback state
    // remains server-authoritative and is intentionally not rewritten here.
    internal var _musicPlaylistsRevision = 0
    internal var _membersInitialSearchQuery = ""
    internal var _notificationInvites: List<RoomInvite> = emptyList()
    internal var _notificationApplications: List<RoomApplication> = emptyList()
    internal var _notificationRoomEvents: List<RoomEventNotification> = emptyList()
    internal var _deferredRoomNotificationVisualReadAt: DateTime? = null
    internal var _deferredRoomNotificationVisualReadIds: Set<String> = emptySet()
    internal var _loadingNotifications = false
    internal var _notificationError: String? = null
    internal var _busyNotificationInviteId: String? = null
    internal var _busyNotificationApplicationId: String? = null
    internal var _hasPendingRoomInvites = false
    internal var _pendingRoomNotificationCount = 0
    internal var _selectedRoomHasPendingJoinRequests = false
    internal var _joinedLiveRoomId: String? = null
    internal var _joinedLivePersonalAiVoiceAnnouncementsEnabled = false
    internal val _joinedLiveParticipantUsers = LinkedHashMap<String, UserSummary>()
    internal var _joinedLiveAuthoritativeParticipantIds: Set<String> = LinkedHashSet()
    internal val _joinedLivePresenceCueParticipantIds = LinkedHashSet<String>()
    internal var _joinedLiveAuthoritativeSnapshotAt: DateTime? = null
    internal var _joiningLive = false
    internal var _leavingLive = false
    internal var _syncingLiveConnectedParticipants = false
    internal var _micMuted = false
    internal var _headphonesMuted = false
    internal val _liveStateRequests = LiveStateRequestQueue()
    internal var _lastInputVolumeBeforeMute = _defaultLiveVolumeRestore
    internal var _lastOutputVolumeBeforeMute = _defaultLiveVolumeRestore
    internal var _lastScreenShareVolumeBeforeMute = _defaultLiveVolumeRestore
    internal var _cameraOn = false
    internal var _screenSharing = false
    internal var _switchingAndroidLocalVideoSource = false
    internal var _voiceBlocked = false
    internal var _sharedMessageTrackPreviewController: MusicTrackPreviewController? = null
    internal val _busyLiveMemberRemovalIds = LinkedHashSet<String>()
    internal val _busyLiveMemberModerationIds = LinkedHashSet<String>()

    // The selected room's music box snapshot, or null when not loaded /
    // disabled. Overwritten wholesale from state fetches, write responses, and
    // the `music_box_changed` SSE event; never merged field by field.
    internal var _musicBox: MusicBoxState? = null

    // Guards the optional state fetch across room-open and live-join entry
    // points. A live join can happen while the initial room fetch is still
    // competing for a refreshed access token, so it also acts as a safe retry.
    internal var _musicBoxLoadingRoomId: String? = null
    internal var _musicBoxPendingRefreshRoomId: String? = null
    internal var _musicBoxRoomSerial = 0
    internal var _musicBoxLoadFailures = 0
    internal var _musicBoxLoadRetry: CompositionTimer? = null

    // Whether the in-pane music box panel is expanded over the live channel.
    internal var _musicBoxOpen = false

    // Drives the music box search field; results are fetched debounced.
    internal val _musicBoxSearchController = TextEditingController()
    internal var _lastMusicBoxSearchText = ""
    internal var _musicBoxSearchResults: List<MusicBoxSearchResult> = emptyList()
    internal var _musicBoxSearching = false
    internal var _musicBoxSearchError: String? = null

    // The selected search source forwarded to the GD music API (defaults to
    // netease). Changing it re-runs the current query.
    internal var _musicBoxSource: String = com.gangchat.client.app.musicBoxDefaultSource
    internal var _musicBoxSearchSerial = 0
    internal var _musicBoxSearchDebounce: CompositionTimer? = null
    internal var _searchDebounce: CompositionTimer? = null
    internal var _searchRequestSerial = 0
    internal var _searchQuery = ""
    internal var _searchExpanded = false
    internal var _titleSearchContextMenuOpen = false
    internal var _pendingTitleSearchContextMenuUpdate: (() -> Unit)? = null
    internal var _searching = false
    internal var _searchLoadingMore = false
    internal var _searchError: String? = null
    internal var _searchResults: GlobalSearchResults? = null
    internal var _activeSearchCategory: GlobalSearchCategory? = null
    internal var _busySearchPublicRoomId: String? = null
    internal var _searchPendingPublicRoomIds: Set<String> = emptySet()
    internal var _showingJoinApplicationDialog = false
    internal var _realtimeStatus = RealtimeConnectionStatus.offline

    /**
     * The composition's coroutine scope: the shell's debounce, retry and
     * ticker timers follow the composition clock like other widget timers
     * (see `CompositionTimer`).
     */
    internal var _scope: CoroutineScope? = null

    // Post-frame `onDetectedAppUpdateShown` (Dart's addPostFrameCallback).
    private var _notifyDetectedAppUpdateShownAfterFrame = false

    // Listener references kept stable so they can be removed again.
    internal val _composerDraftListener: () -> Unit = { _handleComposerDraftChanged() }
    internal val _titleSearchListener: () -> Unit = { _handleTitleSearchChanged() }
    internal val _musicBoxSearchListener: () -> Unit = { _handleMusicBoxSearchChanged() }
    private val _voicePlaybackListener: () -> Unit = { _handleVoicePlaybackChanged() }
    private val _serverClockListener: () -> Unit = { _handleServerClockChanged() }
    internal val _liveSessionChangedListener: () -> Unit = { _onLiveSessionChanged() }

    internal val _roomsController: RoomsController get() = _services.rooms
    internal val _messagesController: MessagesController get() = _services.messages
    internal val _globalSearchController: GlobalSearchController get() = _services.search
    internal val _stickerPacksController: StickerPacksController get() = _services.stickers
    internal val _voiceRecorder: VoiceRecorderController get() = _services.voiceRecorder
    internal val _liveController: LiveController get() = _services.live
    internal val _liveSessionController: LiveSessionController get() = _services.liveSession
    internal val _musicBoxController: MusicBoxController get() = _services.musicBox
    internal val _fileDownloadsController: FileDownloadsController get() = _services.fileDownloads
    internal val _mediaCacheController: MediaCacheController get() = _services.mediaCache
    internal val _serverNow: DateTime get() = widget.app.serverClock.now()
    internal val _appUpdateDownloadInProgress: Boolean get() = _appUpdateDownloadCancellationToken != null

    override fun initState() {
        if (widget.androidSystemService.isSupported) {
            // The app lifecycle observer is installed by build().
            _androidNotificationSelections =
                widget.androidSystemService.selectedNotificationRoomIds.listen({ roomId ->
                    _handleAndroidNotificationRoomSelected(roomId)
                })
            _androidTaskRemovals =
                widget.androidSystemService.taskRemovalRequests.listen({ request ->
                    unawaited { _handleAndroidTaskRemoved(request) }
                })
            unawaited { _takeInitialAndroidNotification() }
            unawaited { _ensureAndroidNotificationPermission() }
        }
        _currentUser = widget.app.currentUser
        _livePresenceSoundPlayer = widget.livePresenceSoundPlayer ?: LivePresenceSoundService()
        _livePresenceSpeechPlayer = widget.livePresenceSpeechPlayer ?: SystemLivePresenceSpeechPlayer()
        _livePresenceAudioCoordinator =
            LivePresenceAudioCoordinator(
                soundPlayer = _livePresenceSoundPlayer,
                speechPlayer = _livePresenceSpeechPlayer,
            )
        _messageNotificationSoundPlayer = widget.messageNotificationSoundPlayer ?: MessageNotificationSoundService()
        _composerController.addListener(_composerDraftListener)
        _titleSearchController.addListener(_titleSearchListener)
        _musicBoxSearchController.addListener(_musicBoxSearchListener)
        _voicePlaybackService.state.addListener(_voicePlaybackListener)
        widget.app.serverClock.addListener(_serverClockListener)
        _installServices()
        _installAndroidPushRegistration()
        _attachLiveSessionCallbacks()
        _startRealtime()
        val detectedAppUpdate = widget.detectedAppUpdate
        if (detectedAppUpdate != null) {
            _showSettingsAppUpdateInState(detectedAppUpdate)
            _notifyDetectedAppUpdateShownAfterFrame = true
        }
        unawaited { _loadServers() }
        unawaited { _refreshPendingRoomInviteBadge() }
    }

    override fun didUpdateWidget(oldWidget: HomeShellWidget) {
        if (oldWidget.detectedAppUpdate != widget.detectedAppUpdate && widget.detectedAppUpdate != null) {
            _openSettingsForAppUpdate(widget.detectedAppUpdate!!)
            _notifyDetectedAppUpdateShownAfterFrame = true
        }
        val appChanged =
            widget.app.currentUser.id != oldWidget.app.currentUser.id ||
                !widget.app.hasSameApiSource(oldWidget.app)
        if (!appChanged) {
            if (oldWidget.app.serverClock !== widget.app.serverClock) {
                oldWidget.app.serverClock.removeListener(_serverClockListener)
                widget.app.serverClock.addListener(_serverClockListener)
            }
            return
        }

        oldWidget.app.serverClock.removeListener(_serverClockListener)
        widget.app.serverClock.addListener(_serverClockListener)
        _detachLiveSessionCallbacks()
        val previousPushRegistration = _androidPushRegistration
        unawaited { previousPushRegistration?.dispose() }
        _androidPushRegistration = null
        _services.close()
        _installServices()
        _installAndroidPushRegistration()
        _messageNotificationTracker.clear()
        _attachLiveSessionCallbacks()
        _startRealtime()
        _searchDebounce?.cancel()
        _searchRequestSerial++
        _titleSearchController.removeListener(_titleSearchListener)
        _titleSearchController.clear()
        _lastTitleSearchText = _titleSearchController.text
        _titleSearchContextMenuOpen = false
        _pendingTitleSearchContextMenuUpdate = null
        _titleSearchController.addListener(_titleSearchListener)
        _setComposerText("", saveDraft = false)
        _resetLiveFullScreenForLifecycle()
        setState {
            _currentUser = widget.app.currentUser
            _servers = emptyList()
            _serverLoadError = null
            _selectedServerId = null
            _selectedRoom = null
            _live = null
            _messages = emptyList()
            _cancelAllDraftAttachments()
            _stagedAttachments.clear()
            _messageDrafts = emptyMap()
            _stagedAttachmentDrafts = emptyMap()
            _selectedRoomNewMessageCount = 0
            _fileTransfers = emptyMap()
            _fileDownloads = emptyMap()
            _liveStageSelections.clear()
            _fullScreenLiveTrack = null
            _loadingRoom = false
            _roomError = null
            _sending = false
            _sendError = null
            _stickerPanelState = StickerPanelLoadState()
            _settingsOpen = false
            _settingsAppUpdate = null
            _logoutConfirming = false
            _narrowContentOpen = false
            _auxiliaryOpenedFromNarrowSidebar = false
            _contentMode = _ContentMode.chat
            _superuserSettingsTarget = null
            _membersInitialSearchQuery = ""
            _notificationInvites = emptyList()
            _notificationApplications = emptyList()
            _notificationRoomEvents = emptyList()
            _loadingNotifications = false
            _notificationError = null
            _busyNotificationInviteId = null
            _busyNotificationApplicationId = null
            _hasPendingRoomInvites = false
            _pendingRoomNotificationCount = 0
            _selectedRoomHasPendingJoinRequests = false
            _joinedLiveRoomId = null
            _joinedLivePersonalAiVoiceAnnouncementsEnabled = false
            _joinedLiveParticipantUsers.clear()
            _joinedLiveAuthoritativeParticipantIds = LinkedHashSet()
            _joinedLivePresenceCueParticipantIds.clear()
            _joinedLiveAuthoritativeSnapshotAt = null
            _joiningLive = false
            _leavingLive = false
            _micMuted = false
            _headphonesMuted = false
            _cameraOn = false
            _screenSharing = false
            _switchingAndroidLocalVideoSource = false
            _voiceBlocked = false
            _resetMusicBox()
            _searchQuery = ""
            _searchExpanded = false
            _searching = false
            _searchLoadingMore = false
            _searchError = null
            _searchResults = null
            _activeSearchCategory = null
            _busySearchPublicRoomId = null
            _searchPendingPublicRoomIds = emptySet()
            _voicePlayback = VoicePlaybackSnapshot()
        }
        unawaited { _voicePlaybackService.stop() }
        unawaited { _loadServers() }
        unawaited { _refreshPendingRoomInviteBadge() }
    }

    override fun dispose() {
        if (widget.androidSystemService.isSupported) {
            val service = widget.androidSystemService
            unawaited { service.enterForeground() }
        }
        _androidNotificationSelections?.cancel()
        _androidTaskRemovals?.cancel()
        val pushRegistration = _androidPushRegistration
        unawaited { pushRegistration?.dispose() }
        _realtimeEvents?.cancel()
        _realtimeStatusEvents?.cancel()
        _resetLiveFullScreenForLifecycle()
        _detachLiveSessionCallbacks()
        _voiceTicker?.cancel()
        _musicBoxLoadRetry?.cancel()
        _musicBoxSearchDebounce?.cancel()
        _musicBoxSearchController.dispose()
        _searchDebounce?.cancel()
        _titleSearchController.removeListener(_titleSearchListener)
        _titleSearchController.dispose()
        _composerController.removeListener(_composerDraftListener)
        _composerController.dispose()
        _composerPanelController.dispose()
        _voicePlaybackService.state.removeListener(_voicePlaybackListener)
        val voicePlaybackService = _voicePlaybackService
        unawaited { voicePlaybackService.dispose() }
        _livePresenceAudioCoordinator.dispose()
        val soundPlayer = _livePresenceSoundPlayer
        unawaited { soundPlayer.dispose() }
        val speechPlayer = _livePresenceSpeechPlayer
        unawaited { speechPlayer.dispose() }
        val messageSoundPlayer = _messageNotificationSoundPlayer
        unawaited { messageSoundPlayer.dispose() }
        val sharedTrackPreview = _sharedMessageTrackPreviewController
        if (sharedTrackPreview != null) unawaited { sharedTrackPreview.dispose() }
        widget.app.serverClock.removeListener(_serverClockListener)
        _cancelActiveDownloads()
        _services.close()
    }

    /**
     * Dart's `didChangeAppLifecycleState`, from the activity lifecycle the way
     * Flutter's Android embedding reports it: `onResume` is resumed,
     * `onPause` inactive, `onStop` hidden then paused, `onStart` hidden then
     * inactive.
     */
    private fun _didChangeAppLifecycleState(state: _AppLifecycleState) {
        if (!widget.androidSystemService.isSupported) return
        val service = widget.androidSystemService
        when (state) {
            _AppLifecycleState.resumed -> {
                _isAppForeground = true
                unawaited { service.enterForeground() }
                unawaited { _takeInitialAndroidNotification() }
                val pushRegistration = _androidPushRegistration
                unawaited { _ignoreErrors { pushRegistration?.synchronize() } }
            }
            _AppLifecycleState.paused, _AppLifecycleState.hidden -> {
                _isAppForeground = false
                unawaited { service.enterBackground() }
            }
            _AppLifecycleState.inactive -> {
                // Start while the activity is still visible. Android 12+ rejects most
                // foreground-service starts after the app is already backgrounded.
                unawaited { service.enterBackground() }
            }
            _AppLifecycleState.detached -> {}
        }
    }

    @Composable
    private fun _AppLifecycleObserver() {
        if (!widget.androidSystemService.isSupported) return
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            var replaying = true
            val observer =
                LifecycleEventObserver { _, event ->
                    // Adding an observer replays the current state; Flutter only
                    // reports later transitions.
                    if (replaying) return@LifecycleEventObserver
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> _didChangeAppLifecycleState(_AppLifecycleState.resumed)
                        Lifecycle.Event.ON_PAUSE -> _didChangeAppLifecycleState(_AppLifecycleState.inactive)
                        Lifecycle.Event.ON_STOP -> {
                            _didChangeAppLifecycleState(_AppLifecycleState.hidden)
                            _didChangeAppLifecycleState(_AppLifecycleState.paused)
                        }
                        Lifecycle.Event.ON_START -> {
                            _didChangeAppLifecycleState(_AppLifecycleState.hidden)
                            _didChangeAppLifecycleState(_AppLifecycleState.inactive)
                        }
                        else -> {}
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            replaying = false
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    internal suspend fun _takeInitialAndroidNotification() {
        try {
            val roomId = widget.androidSystemService.takeInitialNotificationRoomId()
            if (roomId != null) _handleAndroidNotificationRoomSelected(roomId)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
        }
    }

    private suspend fun _ensureAndroidNotificationPermission() {
        try {
            if (!widget.androidSystemService.notificationPreferenceEnabled()) return
            widget.androidSystemService.requestNotificationPermission()
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
        }
    }

    internal fun _handleAndroidNotificationRoomSelected(roomId: String) {
        if (!mounted || roomId.trim().isEmpty()) return
        val room = _servers.firstOrNull { candidate -> candidate.id == roomId }
        if (room == null) {
            _pendingNotificationRoomId = roomId
            return
        }
        _pendingNotificationRoomId = null
        unawaited { _openRoom(room, openContent = true) }
    }

    internal fun _openPendingAndroidNotificationRoom() {
        val roomId = _pendingNotificationRoomId ?: return
        _handleAndroidNotificationRoomSelected(roomId)
    }

    private fun _installServices() {
        val previousSharedTrackPreview = _sharedMessageTrackPreviewController
        _services =
            AuthenticatedAppServices(
                widget.app,
                audioDeviceStore = widget.audioDeviceStore,
                liveSessionController = widget.liveSessionController,
                realtime = widget.realtime,
            )
        val api = _services.api
        _sharedMessageTrackPreviewController =
            if (api is MusicTrackPreviewApi) {
                MusicTrackPreviewController(
                    api = api,
                    platform = widget.musicTrackPreviewPlatformFactory.create(),
                )
            } else {
                null
            }
        if (previousSharedTrackPreview != null) unawaited { previousSharedTrackPreview.dispose() }
    }

    private fun _installAndroidPushRegistration() {
        if (!widget.androidSystemService.isSupported) return
        val controller =
            AndroidPushRegistrationController(
                api = _services.api,
                androidSystemService = widget.androidSystemService,
            )
        _androidPushRegistration = controller
        unawaited { _ignoreErrors { controller.start() } }
    }

    private fun _handleServerClockChanged() {
        if (!mounted) return
        setState {}
    }

    internal fun _setHomeState(update: () -> Unit) = setState(update)

    internal fun _restorePaneAfterAuxiliaryInState() {
        val returnToNarrowSidebar = _auxiliaryOpenedFromNarrowSidebar
        _auxiliaryOpenedFromNarrowSidebar = false
        if (returnToNarrowSidebar || _selectedServerId == null) {
            _narrowContentOpen = false
        }
    }

    private fun _showSettingsAppUpdateInState(update: AvailableAppUpdate) {
        _settingsOpen = true
        _settingsInitialSection = SettingsSection.about
        _settingsInitialMusicPlaylistId = null
        _settingsAppUpdate = update
        _auxiliaryOpenedFromNarrowSidebar = false
        _contentMode = _ContentMode.chat
        _narrowContentOpen = true
    }

    private fun _openSettingsForAppUpdate(update: AvailableAppUpdate) {
        if (!mounted) return
        _setHomeState { _showSettingsAppUpdateInState(update) }
    }

    private fun _handleVoicePlaybackChanged() {
        if (!mounted) return
        _setHomeState { _voicePlayback = _voicePlaybackService.state.value }
    }

    internal suspend fun _toggleVoicePlayback(messageId: String, resolvedUrl: String) {
        try {
            _voicePlaybackService.toggle(messageId = messageId, resolvedUrl = resolvedUrl)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (!mounted) return
            _setHomeState { _sendError = com.gangchat.client.app.userFacingErrorMessage(error) }
        }
    }

    internal fun _attachLiveSessionCallbacks() {
        _liveSessionController.attachSessionCallbacks(
            onChanged = _liveSessionChangedListener,
            onForciblyRemoved = { _onForciblyRemovedFromLive() },
            onUnexpectedlyDisconnected = { _onUnexpectedlyDisconnectedFromLive() },
            onPublishPermissionChanged = { canPublish -> _onPublishPermissionChanged(canPublish) },
            onParticipantJoined = { identity -> _onLiveParticipantJoined(identity) },
            onParticipantLeft = { identity, departureKind -> _onLiveParticipantLeft(identity, departureKind) },
        )
    }

    internal fun _detachLiveSessionCallbacks() {
        _liveSessionController.detachSessionCallbacks(onChanged = _liveSessionChangedListener)
    }

    @Composable
    override fun build() {
        _scope = rememberCoroutineScope()
        _AppLifecycleObserver()
        if (_notifyDetectedAppUpdateShownAfterFrame) {
            SideEffect {
                if (!_notifyDetectedAppUpdateShownAfterFrame) return@SideEffect
                _notifyDetectedAppUpdateShownAfterFrame = false
                if (!mounted) return@SideEffect
                widget.onDetectedAppUpdateShown?.invoke()
            }
        }
        val fullScreenTrack = _resolveFullScreenLiveTrack()
        val useAndroidLayout = true
        val liveMusicBoxVisible =
            _contentMode == _ContentMode.live &&
                _joinedLiveRoomId == _selectedServerId &&
                _musicBox?.enabled == true &&
                _musicBoxOpen
        val joinedLiveRoom =
            joinedLiveRoomSummary(
                joinedLiveRoomId = _joinedLiveRoomId,
                selectedRoom = _selectedRoom,
                rooms = _servers,
            )
        val liveRoomDockBuilder: _TitleLiveRoomDockBuilder = { maxWidth, fillAvailable ->
            _TitleLiveRoomDock(
                maxWidth = maxWidth,
                fillAvailable = fillAvailable,
                room = joinedLiveRoom,
                micMuted = _micMuted,
                headphonesMuted = _headphonesMuted,
                voiceBlocked = _voiceBlocked,
                interactionLocked = _appUpdateDownloadInProgress,
                onOpen = if (joinedLiveRoom == null) null else ({ unawaited { _openJoinedLiveChannel() } }),
                onToggleMic = if (_voiceBlocked) null else ({ _toggleMicMute() }),
                onToggleHeadphones = { _toggleHeadphonesMute() },
                onLeave = if (joinedLiveRoom == null) null else ({ unawaited { _leaveLive() } }),
            )
        }
        MediaCacheScope(_mediaCacheController) {
            ChatImagePreviewActionsScope(actions = _imagePreviewActions, modifier = widget.modifier) {
                Scaffold(
                    backgroundColor = UiColors.background,
                    resizeToAvoidBottomInset = shouldResizeHomeForKeyboard(liveMusicBoxVisible = liveMusicBoxVisible),
                ) {
                    _withAndroidBottomSafeArea(
                        enabled = useAndroidLayout,
                        preserveKeyboardViewport = liveMusicBoxVisible,
                    ) {
                        // Flutter's DecoratedBox: the border paints behind the content.
                        BoxWithConstraints(Modifier.fillMaxSize().decoration(borderColor = _windowEdgeBorder)) {
                            val narrowLayout = maxWidth < narrowBreakpoint
                            _compactLayout = narrowLayout
                            val titleSearchLayout = _homeTitleBarSearchLayout(maxWidth, hasLiveRoom = true)
                            val showSearchOverlay = titleSearchLayout != null && !_appUpdateDownloadInProgress
                            val content: @Composable () -> Unit = {
                                Box(Modifier.fillMaxSize().clipToBounds()) {
                                    key(widget.app.currentUser.id) {
                                        Column(Modifier.fillMaxSize()) {
                                            _HomeTitleBar(
                                                searchController = _titleSearchController,
                                                searchTapRegionGroup = _searchTapRegionGroup,
                                                liveRoomDockBuilder = liveRoomDockBuilder,
                                                interactionLocked = _appUpdateDownloadInProgress,
                                                onActivateSearch = { _activateSearch() },
                                                onSearchTapOutside = { _collapseSearch() },
                                                onSearchContextMenuOpenChanged = { open -> _handleTitleSearchContextMenuOpenChanged(open) },
                                                onClearSearchQuery = { _clearSearchQuery() },
                                            )
                                            Box(Modifier.fillMaxWidth().weight(1f)) {
                                                _buildAppUpdateLockedBody {
                                                    BoxWithConstraints(Modifier.fillMaxSize()) {
                                                        val narrow = maxWidth < narrowBreakpoint
                                                        if (narrow) {
                                                            _buildNarrowLayout(
                                                                maxWidth,
                                                                footerLiveRoomDockBuilder = if (useAndroidLayout) liveRoomDockBuilder else null,
                                                            )
                                                        } else {
                                                            Row(Modifier.fillMaxSize()) {
                                                                _buildSidebar(
                                                                    width = sidebarWidth,
                                                                    openContentOnSelect = false,
                                                                    modifier = Modifier.fillMaxHeight(),
                                                                )
                                                                Box(
                                                                    Modifier.weight(1f).fillMaxHeight(),
                                                                    propagateMinConstraints = true,
                                                                ) {
                                                                    _buildContentPane()
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (_hasSearchQuery && _searchExpanded && showSearchOverlay) {
                                        Box(
                                            Modifier
                                                .offset(x = titleSearchLayout!!.left, y = _homeTitleBarHeight - 1.dp)
                                                .width(titleSearchLayout.width),
                                        ) {
                                            _buildSearchResultsTapRegion()
                                        }
                                    }
                                    if (fullScreenTrack != null) {
                                        Box(Modifier.matchParentSize(), propagateMinConstraints = true) {
                                            FullScreenMediaOrientation {
                                                LiveFullScreenStage(
                                                    track = fullScreenTrack,
                                                    label = liveStageTrackLabel(_live, fullScreenTrack),
                                                    cameraMirrored =
                                                        liveParticipantByUserId(_live, fullScreenTrack.identity)?.cameraMirrored ?: false,
                                                    screenShareViewers =
                                                        if (fullScreenTrack.isScreenShare) {
                                                            liveScreenShareViewers(_live, fullScreenTrack.identity)
                                                        } else {
                                                            emptyList()
                                                        },
                                                    screenShareVolume = _liveSessionController.screenShareVolume,
                                                    onScreenShareVolumeChanged = { volume -> _changeScreenShareVolume(volume) },
                                                    onScreenShareMuteToggled = { _toggleScreenShareAudioMute() },
                                                    onExit = { _exitLiveFullScreen() },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            val responsiveContent: @Composable () -> Unit = {
                                HomeAdaptiveLayout(compact = narrowLayout, content = content)
                            }
                            if (!useAndroidLayout) {
                                responsiveContent()
                            } else {
                                PopScope(
                                    canPop = !_canHandleShellBack(narrowLayout = narrowLayout),
                                    onPopInvokedWithResult = { didPop, _ ->
                                        if (!didPop) _handleShellBack(narrowLayout = narrowLayout)
                                    },
                                ) {
                                    Box(Modifier.fillMaxSize().testTag(androidShellBackScopeTag), propagateMinConstraints = true) {
                                        responsiveContent()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    internal val _selectedServer: RoomCard?
        get() {
            for (server in _servers) {
                if (server.id == _selectedServerId) return server
            }
            return null
        }

    /**
     * Locks the body while an app update downloads (Dart's `Focus` that
     * refuses focus plus `AbsorbPointer`); the shell also clears focus when
     * the lock starts.
     */
    @Composable
    private fun _buildAppUpdateLockedBody(child: @Composable () -> Unit) {
        val locked = _appUpdateDownloadInProgress
        Box(Modifier.fillMaxSize().ignorePointer(locked), propagateMinConstraints = true) { child() }
    }
}

internal enum class _AppLifecycleState { resumed, inactive, hidden, paused, detached }

/** Runs [block], ignoring its failure (Dart's `.catchError((Object _) {})`). */
internal suspend fun _ignoreErrors(block: suspend () -> Unit) {
    try {
        block()
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
    }
}

/**
 * A file staged in the composer. Uploading starts the moment the file is
 * picked, so the entry tracks the in-flight upload (progress + cancellation)
 * and caches the resulting [asset] once it lands, ready for the next send.
 */
internal class _StagedAttachment(val id: String, val file: SelectedFile) {
    val uploadController = UploadTransferController()

    var status: ComposerAttachmentStatus = ComposerAttachmentStatus.uploading
    var asset: UploadedAsset? = null
    var sizeBytes: Long? = null
    var progress: Double? = null
    var error: Any? = null
    var errorMessage: String? = null

    val isUploaded: Boolean get() = status == ComposerAttachmentStatus.uploaded
}

internal class _MentionTextEditingController : TextEditingController() {
    private var _confirmedMentions: List<_ConfirmedComposerMention> = emptyList()

    fun clearConfirmedMentions() {
        if (_confirmedMentions.isEmpty()) return
        _confirmedMentions = emptyList()
        notifyListeners()
    }

    fun addConfirmedMention(start: Int, label: String) {
        val trimmedLabel = label.trim()
        if (trimmedLabel.isEmpty()) return
        val mentionText = "@$trimmedLabel"
        val end = start + mentionText.length
        if (start < 0 || end > text.length || text.substring(start, end) != mentionText) {
            return
        }
        val next =
            (
                _validConfirmedMentions().filter { mention -> mention.end <= start || mention.start >= end } +
                    _ConfirmedComposerMention(start = start, end = end, text = mentionText)
            ).sortedBy { it.start }
        _confirmedMentions = next
        notifyListeners()
    }

    fun pruneInvalidConfirmedMentions(notify: Boolean = true) {
        val valid = _validConfirmedMentions()
        if (valid.size == _confirmedMentions.size) return
        _confirmedMentions = valid
        if (notify) notifyListeners()
    }

    fun confirmedMentionLabels(): List<String> = _validConfirmedMentions().map { mention -> mention.text.substring(1) }

    fun hasConfirmedMention(start: Int, end: Int): Boolean {
        for (mention in _validConfirmedMentions()) {
            if (mention.start == start && mention.end == end) return true
        }
        return false
    }

    fun formatConfirmedMentionBackspace(oldValue: TextFieldValue, defaultValue: TextFieldValue): TextFieldValue? {
        val selection = oldValue.selection
        if (!selection.collapsed) return null
        val cursor = selection.end
        if (cursor <= 0) return null
        if (oldValue.text.length != defaultValue.text.length + 1) return null
        val defaultBackspaceText = oldValue.text.replaceRange(cursor - 1, cursor, "")
        if (defaultValue.text != defaultBackspaceText) return null

        val mentions = _validConfirmedMentionsFor(oldValue.text)
        for (mention in mentions) {
            val hasTrailingSpace = mention.end < oldValue.text.length && oldValue.text[mention.end].code == 0x20
            val deleteEnd =
                when {
                    cursor == mention.end -> mention.end
                    hasTrailingSpace && cursor == mention.end + 1 -> mention.end + 1
                    else -> null
                } ?: continue
            val removedLength = deleteEnd - mention.start
            val nextText = oldValue.text.replaceRange(mention.start, deleteEnd, "")
            val nextMentions = mutableListOf<_ConfirmedComposerMention>()
            for (entry in mentions) {
                if (entry.start == mention.start && entry.end == mention.end) continue
                nextMentions.add(if (entry.start >= deleteEnd) entry.shift(-removedLength) else entry)
            }
            _confirmedMentions = nextMentions.toList()
            return oldValue.copy(text = nextText, selection = TextRange(mention.start), composition = null)
        }
        return null
    }

    private fun _validConfirmedMentions(): List<_ConfirmedComposerMention> = _validConfirmedMentionsFor(text)

    private fun _validConfirmedMentionsFor(value: String): List<_ConfirmedComposerMention> {
        if (_confirmedMentions.isEmpty()) return emptyList()
        return _confirmedMentions.filter { mention ->
            mention.start >= 0 &&
                mention.end <= value.length &&
                value.substring(mention.start, mention.end) == mention.text
        }
    }

    override fun buildTextSpan(text: String, style: TextStyle): AnnotatedString? {
        val mentions = _validConfirmedMentionsFor(text)
        if (mentions.isEmpty()) return null
        val mentionStyle = SpanStyle(color = UiColors.controlAccent, fontWeight = FontWeight.W600)
        return buildAnnotatedString {
            var cursor = 0
            for (mention in mentions) {
                if (mention.start > cursor) append(text.substring(cursor, mention.start))
                pushStyle(mentionStyle)
                append(text.substring(mention.start, mention.end))
                pop()
                cursor = mention.end
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }
}

internal class _ConfirmedMentionBackspaceFormatter(val controller: _MentionTextEditingController) : TextInputFormatter {
    override fun formatEditUpdate(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue =
        controller.formatConfirmedMentionBackspace(oldValue = oldValue, defaultValue = newValue) ?: newValue
}

internal class _ConfirmedComposerMention(val start: Int, val end: Int, val text: String) {
    fun shift(delta: Int): _ConfirmedComposerMention {
        if (delta == 0) return this
        return _ConfirmedComposerMention(start = start + delta, end = end + delta, text = text)
    }
}
