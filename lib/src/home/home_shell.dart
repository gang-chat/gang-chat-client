import 'dart:async';
import 'dart:io' show Directory, File, Platform;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'package:window_manager/window_manager.dart';

import '../app/audio_device_store.dart';
import '../app/app_update.dart';
import '../app/audio_device_state.dart'
    show rememberedAudioVolume, restoredAudioVolume;
import '../app/audio_levels.dart';
import '../app/android_push_registration_controller.dart';
import '../app/authenticated_app_services.dart';
import '../app/authenticated_app_context.dart';
import '../app/close_behavior.dart';
import '../app/file_display.dart' as file_display;
import '../app/error_display.dart';
import '../app/file_downloads_controller.dart';
import '../app/file_transfer_state.dart';
import '../app/media_cache_controller.dart';
import '../app/managed_user_audio_settings.dart';
import '../app/composer_attachment_display.dart' as composer_attachment;
import '../app/global_search_controller.dart';
import '../app/live_controller.dart';
import '../app/live_presence_announcement.dart';
import '../app/live_display.dart' as live_display;
import '../app/live_session_controller.dart';
import '../app/language_preference.dart';
import '../app/message_mentions.dart' as message_mentions;
import '../app/message_notifications.dart';
import '../app/message_display.dart' as message_display;
import '../app/messages_controller.dart';
import '../app/music_box_controller.dart';
import '../app/music_box_display.dart' as music_box_display;
import '../app/music_track_preview.dart';
import '../app/personal_music_playlists.dart';
import '../app/realtime_controller.dart';
import '../app/realtime_live_events.dart';
import '../app/room_badges.dart' as room_badges;
import '../app/room_display.dart' as room_display;
import '../app/room_invites.dart' as room_invites;
import '../app/room_join.dart' as room_join;
import '../app/room_members_filter.dart' as member_filter;
import '../app/room_notifications.dart' as room_notifications;
import '../app/room_message_history.dart' as room_message_history;
import '../app/rooms_controller.dart';
import '../app/search_display.dart' as search_display;
import '../app/settings_shell_state.dart';
import '../app/settings_controller.dart';
import '../app/sticker_display.dart' as sticker_display;
import '../app/sticker_packs_controller.dart';
import '../app/voice_message_display.dart' as voice_display;
import '../app/voice_recorder_controller.dart';
import '../live/live_session.dart';
import '../live/live_presence_sound_service.dart';
import '../live/live_presence_audio_coordinator.dart';
import '../protocol/api_client.dart'
    show
        ApiException,
        MessagePage,
        MusicTrackPreviewApi,
        PersonalMusicPlaylistApi,
        RoomMusicPlaylistApi,
        UploadCancelledException,
        UploadTransferController;
import '../protocol/models.dart';
import '../settings/settings_page.dart';
import '../shell/clipboard_service.dart';
import '../shell/android_system_service.dart';
import '../shell/desktop_window_controller.dart';
import '../shell/file_drop_service.dart';
import '../shell/file_selection_service.dart';
import '../shell/full_screen_media_orientation.dart';
import '../shell/message_notification_sound_service.dart';
import '../shell/music_track_preview_service.dart';
import '../shell/release_update_service.dart';
import '../shell/system_live_presence_speech_player.dart';
import '../shell/voice_playback_service.dart';
import '../shell/window_controls.dart';
import '../ui/ui.dart';
import 'adaptive_layout.dart';
import 'chat_pane.dart';
import 'compact_activity_layout.dart';
import 'home_content.dart';
import 'home_keyboard_layout.dart';
import 'hover_card_anchor.dart';
import 'home_notifications.dart';
import 'home_sidebar.dart';
import 'live_channel_pane.dart';
import 'live_screen_share_picker.dart';
import 'navigation.dart';
import 'room_profile_card.dart';
import 'room_management.dart';

part 'home_shell_realtime.dart';
part 'home_shell_room_actions.dart';
part 'home_shell_live_actions.dart';
part 'home_shell_music_box.dart';
part 'home_shell_messages.dart';
part 'home_shell_downloads.dart';
part 'home_shell_image_preview.dart';
part 'home_shell_notifications.dart';
part 'home_shell_room_message_history.dart';
part 'home_shell_search.dart';
part 'home_shell_layout.dart';
part 'home_shell_title_bar.dart';
part 'home_shell_join_dialog.dart';

const _windowEdgeBorder = Color(0xFF303842);
const _defaultLiveVolumeRestore = 0.5;

Widget _withAndroidBottomSafeArea({
  required bool enabled,
  bool preserveKeyboardViewport = false,
  required Widget child,
}) {
  if (!enabled) return child;
  return HomeKeyboardOverlayViewport(
    enabled: preserveKeyboardViewport,
    child: SafeArea(
      top: false,
      left: false,
      right: false,
      bottom: true,
      child: child,
    ),
  );
}

bool get _supportsWindowManagement =>
    !kIsWeb && (Platform.isWindows || Platform.isLinux || Platform.isMacOS);

enum _ContentMode {
  chat,
  live,
  members,
  roomSettings,
  createRoom,
  notifications,
  superuserUserSettings,
}

class HomeShell extends StatefulWidget {
  const HomeShell({
    super.key,
    required this.app,
    required this.audioDeviceStore,
    required this.closeBehaviorStore,
    required this.languageStore,
    required this.windowController,
    this.liveSessionController,
    this.livePresenceSoundPlayer,
    this.livePresenceSpeechPlayer,
    this.messageNotificationSoundPlayer,
    this.realtime,
    this.androidSystemService = const AndroidSystemService(),
    this.detectedAppUpdate,
    this.onDetectedAppUpdateShown,
    this.musicTrackPreviewPlatformFactory =
        const DefaultMusicTrackPreviewPlatformFactory(),
  });

  final AuthenticatedAppContext app;
  final AudioDeviceStore audioDeviceStore;
  final CloseBehaviorStore closeBehaviorStore;
  final LanguagePreferenceStore languageStore;
  final DesktopWindowController windowController;
  final LiveSessionController? liveSessionController;
  final LivePresenceSoundPlayer? livePresenceSoundPlayer;
  final LivePresenceSpeechPlayer? livePresenceSpeechPlayer;
  final MessageNotificationSoundPlayer? messageNotificationSoundPlayer;
  final RealtimeService? realtime;
  final AndroidSystemService androidSystemService;
  final AvailableAppUpdate? detectedAppUpdate;
  final VoidCallback? onDetectedAppUpdateShown;
  final MusicTrackPreviewPlatformFactory musicTrackPreviewPlatformFactory;

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> with WidgetsBindingObserver {
  late AuthenticatedAppServices _services;
  late CurrentUser _currentUser;
  final _MentionTextEditingController _composerController =
      _MentionTextEditingController();
  final ChatComposerController _composerPanelController =
      ChatComposerController();
  final TextEditingController _titleSearchController = TextEditingController();
  String _lastTitleSearchText = '';
  final GlobalKey _composerDropKey = GlobalKey();
  final Object _searchTapRegionGroup = Object();
  StreamSubscription<RealtimeEvent>? _realtimeEvents;
  bool _accountSuspensionLogoutInProgress = false;
  StreamSubscription<RealtimeConnectionStatus>? _realtimeStatusEvents;
  StreamSubscription<FileDropEvent>? _fileDropEvents;
  StreamSubscription<String>? _androidNotificationSelections;
  StreamSubscription<AndroidTaskRemovalRequest>? _androidTaskRemovals;
  AndroidPushRegistrationController? _androidPushRegistration;
  bool _isAppForeground = true;
  String? _pendingNotificationRoomId;

  List<RoomCard> _servers = const [];
  bool _loadingServers = false;
  String? _serverLoadError;
  String? _selectedServerId;
  RoomDetail? _selectedRoom;
  LiveState? _live;
  List<Message> _messages = const [];
  Map<String, String> _messageDrafts = const {};
  Map<String, List<MessageQuote>> _messageQuoteDrafts = const {};
  Map<String, List<_StagedAttachment>> _stagedAttachmentDrafts = const {};
  bool _updatingComposerFromDraft = false;
  message_mentions.MessageMentionQuery? _composerMentionQuery;
  List<message_mentions.MessageMentionOption> _composerMentionOptions =
      const [];
  int _composerMentionSelectedIndex = 0;
  List<RoomMember> _composerMentionMembers = const [];
  String? _composerMentionMembersRoomId;
  String? _loadingComposerMentionMembersRoomId;
  bool _loadingComposerMentionMembers = false;
  int _composerMentionMembersSerial = 0;
  final Set<String> _locallyDeletedMessageKeys = {};
  int _selectedRoomNewMessageCount = 0;
  String? _focusedMessageId;
  Map<String, FileTransferState> _fileTransfers = const {};
  // Active file downloads, keyed by [file_display.fileDownloadKey]. Kept apart
  // from [_fileTransfers] (outgoing uploads) so the two never collide on a
  // message id and each tile can pick the transfer that applies to it.
  Map<String, FileTransferState> _fileDownloads = const {};
  final Map<String, LiveStageSelection?> _liveStageSelections = {};
  Future<void> _liveScreenViewSyncTail = Future<void>.value();
  Future<void> _liveFullScreenTransitionTail = Future<void>.value();
  int _liveFullScreenTransitionGeneration = 0;
  LiveVideoTrack? _fullScreenLiveTrack;
  bool _windowCaptureExcludedForLiveFullScreen = false;
  bool _loadingRoom = false;
  String? _roomError;
  bool _sending = false;
  String? _sendError;
  sticker_display.StickerPanelLoadState _stickerPanelState =
      const sticker_display.StickerPanelLoadState();
  voice_display.VoiceRecorderState _voiceState =
      const voice_display.VoiceRecorderState();
  final VoicePlaybackService _voicePlaybackService = VoicePlaybackService();
  late final LivePresenceSoundPlayer _livePresenceSoundPlayer;
  late final LivePresenceSpeechPlayer _livePresenceSpeechPlayer;
  late final LivePresenceAudioCoordinator _livePresenceAudioCoordinator;
  late final MessageNotificationSoundPlayer _messageNotificationSoundPlayer;
  final RealtimeMessageNotificationTracker _messageNotificationTracker =
      RealtimeMessageNotificationTracker();
  Future<void> _livePresenceEventTail = Future<void>.value();
  VoicePlaybackSnapshot _voicePlayback = const VoicePlaybackSnapshot();
  Timer? _voiceTicker;
  DateTime? _voiceStartedAt;
  // Files picked for the next message, kept in pick order. Each uploads as
  // soon as it is picked; the message send later just collects the finished
  // assets. See [_StagedAttachment].
  final List<_StagedAttachment> _stagedAttachments = [];
  final ClipboardService _clipboardService = const ClipboardService();
  final FileDropService _fileDropService = const FileDropService();
  final FileSelectionService _fileSelectionService =
      const FileSelectionService();
  int _clipboardImagePasteSerial = 0;
  bool _pickingAttachments = false;
  bool _settingsOpen = false;
  SettingsSection _settingsInitialSection = SettingsSection.profile;
  AvailableAppUpdate? _settingsAppUpdate;
  ReleaseDownloadCancellationToken? _appUpdateDownloadCancellationToken;
  bool _logoutConfirming = false;
  bool _closeConfirming = false;
  bool _exitingApplication = false;
  bool _narrowContentOpen = false;
  bool _compactLayout = false;
  bool _auxiliaryOpenedFromNarrowSidebar = false;
  _ContentMode _contentMode = _ContentMode.chat;
  RoomSettingsSection _roomSettingsInitialSection = RoomSettingsSection.info;
  UserSummary? _superuserSettingsTarget;
  // Bumped to ask an open members panel to reload (e.g. after a
  // `room_join_requests_updated`, `room_role_changed`, or
  // `room_member_profile_changed` SSE event). The panel watches this via
  // didUpdateWidget and re-pulls its members/requests.
  int _membersReloadToken = 0;
  String _membersInitialSearchQuery = '';
  List<RoomInvite> _notificationInvites = const [];
  List<RoomApplication> _notificationApplications = const [];
  List<RoomEventNotification> _notificationRoomEvents = const [];
  DateTime? _deferredRoomNotificationVisualReadAt;
  Set<String> _deferredRoomNotificationVisualReadIds = const {};
  bool _loadingNotifications = false;
  String? _notificationError;
  String? _busyNotificationInviteId;
  String? _busyNotificationApplicationId;
  bool _hasPendingRoomInvites = false;
  int _pendingRoomNotificationCount = 0;
  bool _selectedRoomHasPendingJoinRequests = false;
  String? _joinedLiveRoomId;
  bool _joinedLivePersonalAiVoiceAnnouncementsEnabled = false;
  final Map<String, UserSummary> _joinedLiveParticipantUsers = {};
  bool _joiningLive = false;
  bool _leavingLive = false;
  bool _syncingLiveConnectedParticipants = false;
  bool _micMuted = false;
  bool _headphonesMuted = false;
  final LiveStateRequestQueue _liveStateRequests = LiveStateRequestQueue();
  double _lastInputVolumeBeforeMute = _defaultLiveVolumeRestore;
  double _lastOutputVolumeBeforeMute = _defaultLiveVolumeRestore;
  double _lastScreenShareVolumeBeforeMute = _defaultLiveVolumeRestore;
  bool _cameraOn = false;
  bool _screenSharing = false;
  bool _switchingAndroidLocalVideoSource = false;
  bool _voiceBlocked = false;
  final Set<String> _busyLiveMemberRemovalIds = <String>{};
  final Set<String> _busyLiveMemberModerationIds = <String>{};
  // The selected room's music box snapshot, or null when not loaded / disabled.
  // Overwritten wholesale from state fetches, write responses, and the
  // `music_box_changed` SSE event; never merged field by field.
  MusicBoxState? _musicBox;
  // Whether the in-pane music box panel is expanded over the live channel.
  bool _musicBoxOpen = false;
  // Drives the music box search field; results are fetched debounced.
  final TextEditingController _musicBoxSearchController =
      TextEditingController();
  String _lastMusicBoxSearchText = '';
  List<MusicBoxSearchResult> _musicBoxSearchResults = const [];
  bool _musicBoxSearching = false;
  String? _musicBoxSearchError;
  // The selected search source forwarded to the GD music API (defaults to
  // netease). Changing it re-runs the current query.
  String _musicBoxSource = music_box_display.musicBoxDefaultSource;
  int _musicBoxSearchSerial = 0;
  Timer? _musicBoxSearchDebounce;
  Timer? _searchDebounce;
  int _searchRequestSerial = 0;
  String _searchQuery = '';
  bool _searchExpanded = false;
  bool _titleSearchContextMenuOpen = false;
  VoidCallback? _pendingTitleSearchContextMenuUpdate;
  bool _searching = false;
  bool _searchLoadingMore = false;
  String? _searchError;
  GlobalSearchResults? _searchResults;
  search_display.GlobalSearchCategory? _activeSearchCategory;
  String? _busySearchPublicRoomId;
  Set<String> _searchPendingPublicRoomIds = const {};
  bool _showingJoinApplicationDialog = false;
  RealtimeConnectionStatus _realtimeStatus = RealtimeConnectionStatus.offline;

  RoomsController get _roomsController => _services.rooms;
  MessagesController get _messagesController => _services.messages;
  GlobalSearchController get _globalSearchController => _services.search;
  StickerPacksController get _stickerPacksController => _services.stickers;
  VoiceRecorderController get _voiceRecorder => _services.voiceRecorder;
  LiveController get _liveController => _services.live;
  LiveSessionController get _liveSessionController => _services.liveSession;
  MusicBoxController get _musicBoxController => _services.musicBox;
  FileDownloadsController get _fileDownloadsController =>
      _services.fileDownloads;
  MediaCacheController get _mediaCacheController => _services.mediaCache;
  DateTime get _serverNow => widget.app.serverClock.now();
  bool get _appUpdateDownloadInProgress =>
      _appUpdateDownloadCancellationToken != null;

  @override
  void initState() {
    super.initState();
    if (widget.androidSystemService.isSupported) {
      WidgetsBinding.instance.addObserver(this);
      _androidNotificationSelections = widget
          .androidSystemService
          .selectedNotificationRoomIds
          .listen(_handleAndroidNotificationRoomSelected);
      _androidTaskRemovals = widget.androidSystemService.taskRemovalRequests
          .listen(_handleAndroidTaskRemoved);
      unawaited(_takeInitialAndroidNotification());
      unawaited(_ensureAndroidNotificationPermission());
    }
    _currentUser = widget.app.currentUser;
    _livePresenceSoundPlayer =
        widget.livePresenceSoundPlayer ?? LivePresenceSoundService();
    _livePresenceSpeechPlayer =
        widget.livePresenceSpeechPlayer ?? SystemLivePresenceSpeechPlayer();
    _livePresenceAudioCoordinator = LivePresenceAudioCoordinator(
      soundPlayer: _livePresenceSoundPlayer,
      speechPlayer: _livePresenceSpeechPlayer,
    );
    _messageNotificationSoundPlayer =
        widget.messageNotificationSoundPlayer ??
        MessageNotificationSoundService();
    _composerController.addListener(_handleComposerDraftChanged);
    _titleSearchController.addListener(_handleTitleSearchChanged);
    _musicBoxSearchController.addListener(_handleMusicBoxSearchChanged);
    _voicePlaybackService.state.addListener(_handleVoicePlaybackChanged);
    widget.app.serverClock.addListener(_handleServerClockChanged);
    _installServices();
    _installAndroidPushRegistration();
    _attachLiveSessionCallbacks();
    widget.windowController.setCloseRequestHandler(_handleWindowCloseRequest);
    widget.windowController.setTrayExitHandler(_exitApplication);
    _fileDropEvents = _fileDropService.drops.listen(_handleDroppedFiles);
    _startRealtime();
    final detectedAppUpdate = widget.detectedAppUpdate;
    if (detectedAppUpdate != null) {
      _showSettingsAppUpdateInState(detectedAppUpdate);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        widget.onDetectedAppUpdateShown?.call();
      });
    }
    unawaited(_loadServers());
    unawaited(_refreshPendingRoomInviteBadge());
  }

  @override
  void didUpdateWidget(HomeShell oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.windowController != widget.windowController) {
      oldWidget.windowController.setCloseRequestHandler(null);
      oldWidget.windowController.setTrayExitHandler(null);
      widget.windowController.setCloseRequestHandler(_handleWindowCloseRequest);
      widget.windowController.setTrayExitHandler(_exitApplication);
    }
    if (oldWidget.detectedAppUpdate != widget.detectedAppUpdate &&
        widget.detectedAppUpdate != null) {
      _openSettingsForAppUpdate(widget.detectedAppUpdate!);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        widget.onDetectedAppUpdateShown?.call();
      });
    }
    final appChanged =
        widget.app.currentUser.id != oldWidget.app.currentUser.id ||
        !widget.app.hasSameApiSource(oldWidget.app);
    if (!appChanged) {
      if (oldWidget.app.serverClock != widget.app.serverClock) {
        oldWidget.app.serverClock.removeListener(_handleServerClockChanged);
        widget.app.serverClock.addListener(_handleServerClockChanged);
      }
      return;
    }

    oldWidget.app.serverClock.removeListener(_handleServerClockChanged);
    widget.app.serverClock.addListener(_handleServerClockChanged);
    _detachLiveSessionCallbacks();
    unawaited(_androidPushRegistration?.dispose());
    _androidPushRegistration = null;
    _services.close();
    _installServices();
    _installAndroidPushRegistration();
    _messageNotificationTracker.clear();
    _attachLiveSessionCallbacks();
    _startRealtime();
    _searchDebounce?.cancel();
    _searchRequestSerial++;
    _titleSearchController.removeListener(_handleTitleSearchChanged);
    _titleSearchController.clear();
    _lastTitleSearchText = _titleSearchController.text;
    _titleSearchContextMenuOpen = false;
    _pendingTitleSearchContextMenuUpdate = null;
    _titleSearchController.addListener(_handleTitleSearchChanged);
    _setComposerText('', saveDraft: false);
    _resetLiveFullScreenForLifecycle(
      windowController: oldWidget.windowController,
    );
    setState(() {
      _currentUser = widget.app.currentUser;
      _servers = const [];
      _serverLoadError = null;
      _selectedServerId = null;
      _selectedRoom = null;
      _live = null;
      _messages = const [];
      _cancelAllDraftAttachments();
      _stagedAttachments.clear();
      _messageDrafts = const {};
      _stagedAttachmentDrafts = const {};
      _selectedRoomNewMessageCount = 0;
      _fileTransfers = const {};
      _fileDownloads = const {};
      _liveStageSelections.clear();
      _fullScreenLiveTrack = null;
      _loadingRoom = false;
      _roomError = null;
      _sending = false;
      _sendError = null;
      _stickerPanelState = const sticker_display.StickerPanelLoadState();
      _settingsOpen = false;
      _settingsAppUpdate = null;
      _logoutConfirming = false;
      _narrowContentOpen = false;
      _auxiliaryOpenedFromNarrowSidebar = false;
      _contentMode = _ContentMode.chat;
      _superuserSettingsTarget = null;
      _membersInitialSearchQuery = '';
      _notificationInvites = const [];
      _notificationApplications = const [];
      _notificationRoomEvents = const [];
      _loadingNotifications = false;
      _notificationError = null;
      _busyNotificationInviteId = null;
      _busyNotificationApplicationId = null;
      _hasPendingRoomInvites = false;
      _pendingRoomNotificationCount = 0;
      _selectedRoomHasPendingJoinRequests = false;
      _joinedLiveRoomId = null;
      _joinedLivePersonalAiVoiceAnnouncementsEnabled = false;
      _joinedLiveParticipantUsers.clear();
      _joiningLive = false;
      _leavingLive = false;
      _micMuted = false;
      _headphonesMuted = false;
      _cameraOn = false;
      _screenSharing = false;
      _switchingAndroidLocalVideoSource = false;
      _voiceBlocked = false;
      _resetMusicBox();
      _searchQuery = '';
      _searchExpanded = false;
      _searching = false;
      _searchLoadingMore = false;
      _searchError = null;
      _searchResults = null;
      _activeSearchCategory = null;
      _busySearchPublicRoomId = null;
      _searchPendingPublicRoomIds = const {};
      _voicePlayback = const VoicePlaybackSnapshot();
    });
    unawaited(_voicePlaybackService.stop());
    unawaited(_loadServers());
    unawaited(_refreshPendingRoomInviteBadge());
  }

  @override
  void dispose() {
    if (widget.androidSystemService.isSupported) {
      WidgetsBinding.instance.removeObserver(this);
      unawaited(widget.androidSystemService.enterForeground());
    }
    final androidNotificationSelections = _androidNotificationSelections;
    if (androidNotificationSelections != null) {
      unawaited(androidNotificationSelections.cancel());
    }
    final androidTaskRemovals = _androidTaskRemovals;
    if (androidTaskRemovals != null) {
      unawaited(androidTaskRemovals.cancel());
    }
    unawaited(_androidPushRegistration?.dispose());
    final realtimeEvents = _realtimeEvents;
    if (realtimeEvents != null) unawaited(realtimeEvents.cancel());
    final realtimeStatusEvents = _realtimeStatusEvents;
    if (realtimeStatusEvents != null) {
      unawaited(realtimeStatusEvents.cancel());
    }
    final fileDropEvents = _fileDropEvents;
    if (fileDropEvents != null) unawaited(fileDropEvents.cancel());
    _resetLiveFullScreenForLifecycle(windowController: widget.windowController);
    _detachLiveSessionCallbacks();
    widget.windowController.setCloseRequestHandler(null);
    widget.windowController.setTrayExitHandler(null);
    _voiceTicker?.cancel();
    _musicBoxSearchDebounce?.cancel();
    _musicBoxSearchController.dispose();
    _searchDebounce?.cancel();
    _titleSearchController.removeListener(_handleTitleSearchChanged);
    _titleSearchController.dispose();
    _composerController.removeListener(_handleComposerDraftChanged);
    _composerController.dispose();
    _composerPanelController.dispose();
    _voicePlaybackService.state.removeListener(_handleVoicePlaybackChanged);
    unawaited(_voicePlaybackService.dispose());
    _livePresenceAudioCoordinator.dispose();
    unawaited(_livePresenceSoundPlayer.dispose());
    unawaited(_livePresenceSpeechPlayer.dispose());
    unawaited(_messageNotificationSoundPlayer.dispose());
    widget.app.serverClock.removeListener(_handleServerClockChanged);
    _cancelActiveDownloads();
    _services.close();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (!widget.androidSystemService.isSupported) return;
    switch (state) {
      case AppLifecycleState.resumed:
        _isAppForeground = true;
        unawaited(widget.androidSystemService.enterForeground());
        unawaited(_takeInitialAndroidNotification());
        unawaited(
          _androidPushRegistration?.synchronize().catchError((Object _) {}),
        );
        break;
      case AppLifecycleState.paused:
      case AppLifecycleState.hidden:
        _isAppForeground = false;
        unawaited(widget.androidSystemService.enterBackground());
        break;
      case AppLifecycleState.inactive:
        // Start while the activity is still visible. Android 12+ rejects most
        // foreground-service starts after the app is already backgrounded.
        unawaited(widget.androidSystemService.enterBackground());
        break;
      case AppLifecycleState.detached:
        break;
    }
  }

  Future<void> _takeInitialAndroidNotification() async {
    try {
      final roomId = await widget.androidSystemService
          .takeInitialNotificationRoomId();
      if (roomId != null) _handleAndroidNotificationRoomSelected(roomId);
    } catch (_) {}
  }

  Future<void> _ensureAndroidNotificationPermission() async {
    try {
      if (!await widget.androidSystemService.notificationPreferenceEnabled()) {
        return;
      }
      await widget.androidSystemService.requestNotificationPermission();
    } catch (_) {}
  }

  void _handleAndroidNotificationRoomSelected(String roomId) {
    if (!mounted || roomId.trim().isEmpty) return;
    final room = _servers.cast<RoomCard?>().firstWhere(
      (candidate) => candidate?.id == roomId,
      orElse: () => null,
    );
    if (room == null) {
      _pendingNotificationRoomId = roomId;
      return;
    }
    _pendingNotificationRoomId = null;
    unawaited(_openRoom(room, openContent: true));
  }

  void _openPendingAndroidNotificationRoom() {
    final roomId = _pendingNotificationRoomId;
    if (roomId == null) return;
    _handleAndroidNotificationRoomSelected(roomId);
  }

  void _installServices() {
    _services = AuthenticatedAppServices(
      widget.app,
      audioDeviceStore: widget.audioDeviceStore,
      liveSessionController: widget.liveSessionController,
      realtime: widget.realtime,
    );
  }

  void _installAndroidPushRegistration() {
    if (!widget.androidSystemService.isSupported) return;
    final controller = AndroidPushRegistrationController(
      api: _services.api,
      androidSystemService: widget.androidSystemService,
    );
    _androidPushRegistration = controller;
    unawaited(controller.start().catchError((Object _) {}));
  }

  void _handleServerClockChanged() {
    if (!mounted) return;
    setState(() {});
  }

  void _setHomeState(VoidCallback update) => setState(update);

  void _restorePaneAfterAuxiliaryInState() {
    final returnToNarrowSidebar = _auxiliaryOpenedFromNarrowSidebar;
    _auxiliaryOpenedFromNarrowSidebar = false;
    if (returnToNarrowSidebar || _selectedServerId == null) {
      _narrowContentOpen = false;
    }
  }

  void _showSettingsAppUpdateInState(AvailableAppUpdate update) {
    _settingsOpen = true;
    _settingsInitialSection = SettingsSection.about;
    _settingsAppUpdate = update;
    _auxiliaryOpenedFromNarrowSidebar = false;
    _contentMode = _ContentMode.chat;
    _narrowContentOpen = true;
  }

  void _openSettingsForAppUpdate(AvailableAppUpdate update) {
    if (!mounted) return;
    _setHomeState(() => _showSettingsAppUpdateInState(update));
  }

  void _handleVoicePlaybackChanged() {
    if (!mounted) return;
    _setHomeState(() => _voicePlayback = _voicePlaybackService.state.value);
  }

  Future<void> _toggleVoicePlayback({
    required String messageId,
    required String resolvedUrl,
  }) async {
    try {
      await _voicePlaybackService.toggle(
        messageId: messageId,
        resolvedUrl: resolvedUrl,
      );
    } catch (error) {
      if (!mounted) return;
      _setHomeState(() => _sendError = userFacingErrorMessage(error));
    }
  }

  void _attachLiveSessionCallbacks() {
    _liveSessionController.attachSessionCallbacks(
      onChanged: _onLiveSessionChanged,
      onForciblyRemoved: _onForciblyRemovedFromLive,
      onUnexpectedlyDisconnected: _onUnexpectedlyDisconnectedFromLive,
      onPublishPermissionChanged: _onPublishPermissionChanged,
      onParticipantJoined: _onLiveParticipantJoined,
      onParticipantLeft: _onLiveParticipantLeft,
    );
  }

  void _detachLiveSessionCallbacks() {
    _liveSessionController.detachSessionCallbacks(
      onChanged: _onLiveSessionChanged,
    );
  }

  @override
  Widget build(BuildContext context) {
    final fullScreenTrack = _resolveFullScreenLiveTrack();
    final platform = Theme.of(context).platform;
    final useAndroidLayout = platform == TargetPlatform.android;
    final liveMusicBoxVisible =
        _contentMode == _ContentMode.live &&
        _joinedLiveRoomId == _selectedServerId &&
        _musicBox?.enabled == true &&
        _musicBoxOpen;
    final joinedLiveRoom = live_display.joinedLiveRoomSummary(
      joinedLiveRoomId: _joinedLiveRoomId,
      selectedRoom: _selectedRoom,
      rooms: _servers,
    );
    Widget liveRoomDockBuilder(double maxWidth, {bool fillAvailable = false}) =>
        _TitleLiveRoomDock(
          maxWidth: maxWidth,
          fillAvailable: fillAvailable,
          room: joinedLiveRoom,
          micMuted: _micMuted,
          headphonesMuted: _headphonesMuted,
          voiceBlocked: _voiceBlocked,
          interactionLocked: _appUpdateDownloadInProgress,
          onOpen: joinedLiveRoom == null
              ? null
              : () => unawaited(_openJoinedLiveChannel()),
          onToggleMic: _voiceBlocked ? null : _toggleMicMute,
          onToggleHeadphones: _toggleHeadphonesMute,
          onLeave: joinedLiveRoom == null
              ? null
              : () => unawaited(_leaveLive()),
        );
    return MediaCacheScope(
      cache: _mediaCacheController,
      child: ChatImagePreviewActionsScope(
        actions: _imagePreviewActions,
        child: Scaffold(
          backgroundColor: UiColors.background,
          resizeToAvoidBottomInset: shouldResizeHomeForKeyboard(
            platform: platform,
            liveMusicBoxVisible: liveMusicBoxVisible,
          ),
          body: _withAndroidBottomSafeArea(
            enabled: useAndroidLayout,
            preserveKeyboardViewport: liveMusicBoxVisible,
            child: DecoratedBox(
              decoration: BoxDecoration(
                border: Border.all(color: _windowEdgeBorder),
              ),
              child: LayoutBuilder(
                builder: (context, shellConstraints) {
                  final narrowLayout =
                      shellConstraints.maxWidth < narrowBreakpoint;
                  _compactLayout = narrowLayout;
                  final titleSearchLayout = _homeTitleBarSearchLayout(
                    context,
                    shellConstraints.maxWidth,
                    hasLiveRoom: true,
                  );
                  final showSearchOverlay =
                      titleSearchLayout != null &&
                      !_appUpdateDownloadInProgress;
                  final content = Stack(
                    fit: StackFit.expand,
                    children: [
                      KeyedSubtree(
                        key: ValueKey(widget.app.currentUser.id),
                        child: Column(
                          children: [
                            _HomeTitleBar(
                              windowController: widget.windowController,
                              searchController: _titleSearchController,
                              searchTapRegionGroup: _searchTapRegionGroup,
                              liveRoomDockBuilder: liveRoomDockBuilder,
                              interactionLocked: _appUpdateDownloadInProgress,
                              onActivateSearch: _activateSearch,
                              onSearchTapOutside: _collapseSearch,
                              onSearchContextMenuOpenChanged:
                                  _handleTitleSearchContextMenuOpenChanged,
                              onClearSearchQuery: _clearSearchQuery,
                            ),
                            Expanded(
                              child: _buildAppUpdateLockedBody(
                                LayoutBuilder(
                                  builder: (context, constraints) {
                                    final narrow =
                                        constraints.maxWidth < narrowBreakpoint;
                                    if (narrow) {
                                      return _buildNarrowLayout(
                                        constraints.maxWidth,
                                        footerLiveRoomDockBuilder:
                                            useAndroidLayout
                                            ? liveRoomDockBuilder
                                            : null,
                                      );
                                    }

                                    return Row(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.stretch,
                                      children: [
                                        _buildSidebar(
                                          width: sidebarWidth,
                                          openContentOnSelect: false,
                                        ),
                                        Expanded(child: _buildContentPane()),
                                      ],
                                    );
                                  },
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      if (_hasSearchQuery &&
                          _searchExpanded &&
                          showSearchOverlay)
                        Positioned(
                          top: _homeTitleBarHeight - 1,
                          left: titleSearchLayout.left,
                          width: titleSearchLayout.width,
                          child: _buildSearchResultsTapRegion(),
                        ),
                      if (fullScreenTrack != null)
                        Positioned.fill(
                          child: FullScreenMediaOrientation(
                            child: LiveFullScreenStage(
                              track: fullScreenTrack,
                              label: liveStageTrackLabel(
                                _live,
                                fullScreenTrack,
                              ),
                              cameraMirrored:
                                  live_display
                                      .liveParticipantByUserId(
                                        _live,
                                        fullScreenTrack.identity,
                                      )
                                      ?.cameraMirrored ??
                                  false,
                              screenShareViewers: fullScreenTrack.isScreenShare
                                  ? live_display.liveScreenShareViewers(
                                      _live,
                                      fullScreenTrack.identity,
                                    )
                                  : const <UserSummary>[],
                              screenShareVolume:
                                  _liveSessionController.screenShareVolume,
                              onScreenShareVolumeChanged:
                                  _changeScreenShareVolume,
                              onScreenShareMuteToggled:
                                  _toggleScreenShareAudioMute,
                              onExit: _exitLiveFullScreen,
                            ),
                          ),
                        ),
                    ],
                  );
                  final responsiveContent = HomeAdaptiveLayout(
                    compact: narrowLayout,
                    child: content,
                  );
                  if (!useAndroidLayout) return responsiveContent;
                  return PopScope(
                    key: const ValueKey('android-shell-back-scope'),
                    canPop: !_canHandleShellBack(narrowLayout: narrowLayout),
                    onPopInvokedWithResult: (didPop, result) {
                      if (!didPop) {
                        _handleShellBack(narrowLayout: narrowLayout);
                      }
                    },
                    child: responsiveContent,
                  );
                },
              ),
            ),
          ),
        ),
      ),
    );
  }

  RoomCard? get _selectedServer {
    for (final server in _servers) {
      if (server.id == _selectedServerId) return server;
    }
    return null;
  }

  Widget _buildAppUpdateLockedBody(Widget child) {
    final locked = _appUpdateDownloadInProgress;
    return Focus(
      canRequestFocus: !locked,
      descendantsAreFocusable: !locked,
      child: AbsorbPointer(absorbing: locked, child: child),
    );
  }
}

String _roomTitle(RoomDetail? room, RoomCard? card) {
  final detailRemark = room?.remarkName?.trim();
  if (detailRemark != null && detailRemark.isNotEmpty) return detailRemark;
  final detailTitle = room?.name.trim();
  if (detailTitle != null && detailTitle.isNotEmpty) return detailTitle;
  final cardTitle = card?.displayName.trim();
  if (cardTitle != null && cardTitle.isNotEmpty) return cardTitle;
  return '聊天';
}

/// A file staged in the composer. Uploading starts the moment the file is
/// picked, so the entry tracks the in-flight upload (progress + cancellation)
/// and caches the resulting [asset] once it lands, ready for the next send.
class _StagedAttachment {
  _StagedAttachment({required this.id, required this.file})
    : status = composer_attachment.ComposerAttachmentStatus.uploading;

  final String id;
  final SelectedFile file;
  final UploadTransferController uploadController = UploadTransferController();

  composer_attachment.ComposerAttachmentStatus status;
  UploadedAsset? asset;
  int? sizeBytes;
  double? progress;
  Object? error;
  String? errorMessage;

  bool get isUploaded =>
      status == composer_attachment.ComposerAttachmentStatus.uploaded;
}

class _MentionTextEditingController extends TextEditingController {
  List<_ConfirmedComposerMention> _confirmedMentions = const [];

  void clearConfirmedMentions() {
    if (_confirmedMentions.isEmpty) return;
    _confirmedMentions = const [];
    notifyListeners();
  }

  void addConfirmedMention({required int start, required String label}) {
    final trimmedLabel = label.trim();
    if (trimmedLabel.isEmpty) return;
    final mentionText = '@$trimmedLabel';
    final end = start + mentionText.length;
    if (start < 0 ||
        end > text.length ||
        text.substring(start, end) != mentionText) {
      return;
    }
    final next = [
      for (final mention in _validConfirmedMentions())
        if (mention.end <= start || mention.start >= end) mention,
      _ConfirmedComposerMention(start: start, end: end, text: mentionText),
    ]..sort((a, b) => a.start.compareTo(b.start));
    _confirmedMentions = List.unmodifiable(next);
    notifyListeners();
  }

  void pruneInvalidConfirmedMentions({bool notify = true}) {
    final valid = _validConfirmedMentions();
    if (valid.length == _confirmedMentions.length) return;
    _confirmedMentions = valid;
    if (notify) notifyListeners();
  }

  List<String> confirmedMentionLabels() {
    return [
      for (final mention in _validConfirmedMentions())
        mention.text.substring(1),
    ];
  }

  bool hasConfirmedMention({required int start, required int end}) {
    for (final mention in _validConfirmedMentions()) {
      if (mention.start == start && mention.end == end) return true;
    }
    return false;
  }

  TextEditingValue? formatConfirmedMentionBackspace({
    required TextEditingValue oldValue,
    required TextEditingValue defaultValue,
  }) {
    final selection = oldValue.selection;
    if (!selection.isValid || !selection.isCollapsed) return null;
    final cursor = selection.extentOffset;
    if (cursor <= 0) return null;
    if (oldValue.text.length != defaultValue.text.length + 1) return null;
    final defaultBackspaceText = oldValue.text.replaceRange(
      cursor - 1,
      cursor,
      '',
    );
    if (defaultValue.text != defaultBackspaceText) return null;

    final mentions = _validConfirmedMentionsFor(oldValue.text);
    for (final mention in mentions) {
      final hasTrailingSpace =
          mention.end < oldValue.text.length &&
          oldValue.text.codeUnitAt(mention.end) == 0x20;
      final deleteEnd = cursor == mention.end
          ? mention.end
          : hasTrailingSpace && cursor == mention.end + 1
          ? mention.end + 1
          : null;
      if (deleteEnd == null) continue;
      final removedLength = deleteEnd - mention.start;
      final nextText = oldValue.text.replaceRange(mention.start, deleteEnd, '');
      final nextMentions = <_ConfirmedComposerMention>[];
      for (final entry in mentions) {
        if (entry.start == mention.start && entry.end == mention.end) {
          continue;
        }
        nextMentions.add(
          entry.start >= deleteEnd ? entry.shift(-removedLength) : entry,
        );
      }
      _confirmedMentions = List.unmodifiable(nextMentions);
      return oldValue.copyWith(
        text: nextText,
        selection: TextSelection.collapsed(offset: mention.start),
        composing: TextRange.empty,
      );
    }
    return null;
  }

  List<_ConfirmedComposerMention> _validConfirmedMentions() {
    return _validConfirmedMentionsFor(text);
  }

  List<_ConfirmedComposerMention> _validConfirmedMentionsFor(String value) {
    if (_confirmedMentions.isEmpty) return const [];
    return [
      for (final mention in _confirmedMentions)
        if (mention.start >= 0 &&
            mention.end <= value.length &&
            value.substring(mention.start, mention.end) == mention.text)
          mention,
    ];
  }

  @override
  TextSpan buildTextSpan({
    required BuildContext context,
    TextStyle? style,
    required bool withComposing,
  }) {
    final textStyle = style ?? DefaultTextStyle.of(context).style;
    final mentions = _validConfirmedMentions();
    if (mentions.isEmpty) {
      return TextSpan(text: text, style: textStyle);
    }

    final mentionStyle = textStyle.copyWith(
      color: UiColors.controlAccent,
      fontWeight: FontWeight.w600,
    );
    final spans = <InlineSpan>[];
    var cursor = 0;
    for (final mention in mentions) {
      if (mention.start > cursor) {
        spans.add(TextSpan(text: text.substring(cursor, mention.start)));
      }
      spans.add(
        TextSpan(
          text: text.substring(mention.start, mention.end),
          style: mentionStyle,
        ),
      );
      cursor = mention.end;
    }
    if (cursor < text.length) {
      spans.add(TextSpan(text: text.substring(cursor)));
    }
    return TextSpan(style: textStyle, children: spans);
  }
}

class _ConfirmedMentionBackspaceFormatter extends TextInputFormatter {
  const _ConfirmedMentionBackspaceFormatter(this.controller);

  final _MentionTextEditingController controller;

  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    return controller.formatConfirmedMentionBackspace(
          oldValue: oldValue,
          defaultValue: newValue,
        ) ??
        newValue;
  }
}

class _ConfirmedComposerMention {
  const _ConfirmedComposerMention({
    required this.start,
    required this.end,
    required this.text,
  });

  final int start;
  final int end;
  final String text;

  _ConfirmedComposerMention shift(int delta) {
    if (delta == 0) return this;
    return _ConfirmedComposerMention(
      start: start + delta,
      end: end + delta,
      text: text,
    );
  }
}
