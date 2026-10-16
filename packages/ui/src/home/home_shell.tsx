import { createRef, useEffect, useLayoutEffect, useMemo, useState, type CSSProperties, type ReactNode, type RefObject } from 'react';

import type { AvailableAppUpdate } from '@gang-chat/core/app/app_update';
import type { AudioDeviceStore } from '@gang-chat/core/app/audio_device_store';
import { AndroidPushRegistrationController } from '@gang-chat/core/app/android_push_registration_controller';
import { AuthenticatedAppServices } from '@gang-chat/core/app/authenticated_app_services';
import type { AuthenticatedAppContext } from '@gang-chat/core/app/authenticated_app_context';
import type { CloseBehaviorStore } from '@gang-chat/core/app/close_behavior';
import { ComposerAttachmentStatus } from '@gang-chat/core/app/composer_attachment_display';
import { userFacingErrorMessage } from '@gang-chat/core/app/error_display';
import type { FileDownloadsController } from '@gang-chat/core/app/file_downloads_controller';
import type { FileTransferState } from '@gang-chat/core/app/file_transfer_state';
import type { GlobalSearchController } from '@gang-chat/core/app/global_search_controller';
import type { LanguagePreferenceStore } from '@gang-chat/core/app/language_preference';
import type { LiveController } from '@gang-chat/core/app/live_controller';
import { LiveStateRequestQueue } from '@gang-chat/core/app/live_controller';
import * as live_display from '@gang-chat/core/app/live_display';
import type { LivePresenceSpeechPlayer } from '@gang-chat/core/app/live_presence_announcement';
import type { LiveSessionController } from '@gang-chat/core/app/live_session_controller';
import type { MediaCacheController } from '@gang-chat/core/app/media_cache_controller';
import type * as message_mentions from '@gang-chat/core/app/message_mentions';
import { RealtimeMessageNotificationTracker } from '@gang-chat/core/app/message_notifications';
import type { MessagesController } from '@gang-chat/core/app/messages_controller';
import type { MusicBoxController } from '@gang-chat/core/app/music_box_controller';
import * as music_box_display from '@gang-chat/core/app/music_box_display';
import {
  MusicTrackPreviewController,
  type MusicTrackPreviewPlatformFactory,
} from '@gang-chat/core/app/music_track_preview';
import { RealtimeConnectionStatus, type RealtimeService } from '@gang-chat/core/app/realtime_controller';
import type { RoomsController } from '@gang-chat/core/app/rooms_controller';
import type * as search_display from '@gang-chat/core/app/search_display';
import { SettingsSection } from '@gang-chat/core/app/settings_shell_state';
import * as sticker_display from '@gang-chat/core/app/sticker_display';
import type { StickerPacksController } from '@gang-chat/core/app/sticker_packs_controller';
import * as voice_display from '@gang-chat/core/app/voice_message_display';
import type { VoiceRecorderController } from '@gang-chat/core/app/voice_recorder_controller';
import type { AppConfig } from '@gang-chat/core/config/app_config';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import { Timer, unawaited, type PeriodicTimer } from '@gang-chat/core/lib/async';
import type { StreamSubscription } from '@gang-chat/core/lib/event_stream';
import { LivePresenceAudioCoordinator } from '@gang-chat/core/live/live_presence_audio_coordinator';
import { LivePresenceSoundService, type LivePresenceSoundPlayer } from '@gang-chat/core/live/live_presence_sound_service';
import type { LiveVideoTrack } from '@gang-chat/core/live/live_session';
import type { MusicTrackPreviewApi } from '@gang-chat/core/protocol/api_client';
import { UploadTransferController } from '@gang-chat/core/protocol/api_client';
import type {
  CurrentUser,
  GlobalSearchResults,
  LiveState,
  Message,
  MessageQuote,
  MusicBoxSearchResult,
  MusicBoxState,
  RoomApplication,
  RoomCard,
  RoomDetail,
  RoomEventNotification,
  RoomInvite,
  RoomMember,
  UploadedAsset,
  UserSummary,
} from '@gang-chat/core/protocol/models';
import { AndroidSystemService } from '@gang-chat/core/shell/android_system_service';
import { ClipboardService } from '@gang-chat/core/shell/clipboard_service';
import type { DesktopWindowController } from '@gang-chat/core/shell/desktop_window_controller';
import { FileDropService } from '@gang-chat/core/shell/file_drop_service';
import { FileSelectionService, type SelectedFile } from '@gang-chat/core/shell/file_selection_service';
import {
  MessageNotificationSoundService,
  type MessageNotificationSoundPlayer,
} from '@gang-chat/core/shell/message_notification_sound_service';
import { DefaultMusicTrackPreviewPlatformFactory } from '@gang-chat/core/shell/music_track_preview_service';
import type { ReleaseDownloadCancellationToken } from '@gang-chat/core/shell/release_update_service';
import { SystemLivePresenceSpeechPlayer } from '@gang-chat/core/shell/system_live_presence_speech_player';
import { VoicePlaybackService, VoicePlaybackSnapshot } from '@gang-chat/core/shell/voice_playback_service';

import { Column, Expanded, Positioned, Row, Stack } from '../lib/layout';
import {
  TextEditingController,
  TextRange,
  TextSelection,
  type TextEditingValue,
  type TextInputFormatter,
} from '../lib/text_editing';
import type { ElementSize } from '../lib/use_element_size';
import { ViewState, useViewState } from '../lib/view_state';
import { FullScreenMediaOrientation } from '../shell/full_screen_media_orientation';
import { useAppConfig } from '../ui/app_config_scope';
import { ChatComposerController } from '../ui/chat_composer';
import { MediaCacheScope } from '../ui/media_cache_scope';
import { UiColors, argb } from '../ui/tokens';
import { HomeAdaptiveLayout } from './adaptive_layout';
import { ChatImagePreviewActionsScope } from './chat_image_preview';
import { HomeKeyboardOverlayViewport, shouldResizeHomeForKeyboard } from './home_keyboard_layout';
import { HomeShellDownloads } from './home_shell_downloads';
import { HomeShellImagePreview } from './home_shell_image_preview';
import { HomeShellLayout } from './home_shell_layout';
import { HomeShellLiveActions } from './home_shell_live_actions';
import { HomeShellMessages } from './home_shell_messages';
import { HomeShellMusicBox } from './home_shell_music_box';
import { HomeShellNotifications } from './home_shell_notifications';
import { HomeShellRealtime } from './home_shell_realtime';
import { HomeShellRoomActions } from './home_shell_room_actions';
import { HomeShellSearch } from './home_shell_search';
import { _HomeTitleBar, _TitleLiveRoomDock, _homeTitleBarHeight, _homeTitleBarSearchLayout } from './home_shell_title_bar';
import { LiveFullScreenStage, liveStageTrackLabel } from './live_channel_media';
import type { LiveStageSelection } from './live_channel_pane';
import { sidebarWidth, usesNarrowHomeLayout } from './navigation';
import { RoomSettingsSection } from './room_settings_dialog';

// The extension tables (`extension _HomeShellX on _HomeShellState` in the
// Dart part files) are merged into [HomeShellState] below. Each lives in its
// own module so it can be ported and reviewed against its Dart part file.

export const _windowEdgeBorder = argb(0xff303842);
export const _defaultLiveVolumeRestore = 0.5;

export function _withAndroidBottomSafeArea(opts: {
  enabled: boolean;
  preserveKeyboardViewport?: boolean;
  child: ReactNode;
}): ReactNode {
  const { enabled, preserveKeyboardViewport = false, child } = opts;
  if (!enabled) return child;
  return (
    <HomeKeyboardOverlayViewport enabled={preserveKeyboardViewport}>
      {/* SafeArea(top: false, left: false, right: false, bottom: true) */}
      <div
        style={{
          flex: '1 1 0',
          minWidth: 0,
          minHeight: 0,
          display: 'flex',
          flexDirection: 'column',
          paddingBottom: 'env(safe-area-inset-bottom, 0px)',
          boxSizing: 'border-box',
        }}
      >
        {child}
      </div>
    </HomeKeyboardOverlayViewport>
  );
}

/** Port of the Dart top-level getter `_supportsWindowManagement`. */
export function _supportsWindowManagement(): boolean {
  return !HostPlatform.isWeb && (HostPlatform.isWindows || HostPlatform.isLinux || HostPlatform.isMacOS);
}

export enum _ContentMode {
  chat = 'chat',
  live = 'live',
  members = 'members',
  roomSettings = 'roomSettings',
  createRoom = 'createRoom',
  notifications = 'notifications',
  superuserUserSettings = 'superuserUserSettings',
}

/** Port of Flutter's `AppLifecycleState` (only the Android shell observes it). */
export enum AppLifecycleState {
  resumed = 'resumed',
  inactive = 'inactive',
  hidden = 'hidden',
  paused = 'paused',
  detached = 'detached',
}

export interface HomeShellProps {
  app: AuthenticatedAppContext;
  audioDeviceStore: AudioDeviceStore;
  closeBehaviorStore: CloseBehaviorStore;
  languageStore: LanguagePreferenceStore;
  windowController: DesktopWindowController;
  liveSessionController?: LiveSessionController | null;
  livePresenceSoundPlayer?: LivePresenceSoundPlayer | null;
  livePresenceSpeechPlayer?: LivePresenceSpeechPlayer | null;
  messageNotificationSoundPlayer?: MessageNotificationSoundPlayer | null;
  realtime?: RealtimeService | null;
  androidSystemService?: AndroidSystemService;
  detectedAppUpdate?: AvailableAppUpdate | null;
  onDetectedAppUpdateShown?: (() => void) | null;
  musicTrackPreviewPlatformFactory?: MusicTrackPreviewPlatformFactory;
}

/** [HomeShellProps] with the Dart constructor defaults applied (the state's `widget`). */
export type ResolvedHomeShellProps = HomeShellProps & {
  androidSystemService: AndroidSystemService;
  musicTrackPreviewPlatformFactory: MusicTrackPreviewPlatformFactory;
};

// `const AndroidSystemService()` / `const DefaultMusicTrackPreviewPlatformFactory()`
// are canonical instances in Dart; share one instance each.
let _defaultAndroidSystemService: AndroidSystemService | null = null;
let _defaultMusicTrackPreviewPlatformFactory: DefaultMusicTrackPreviewPlatformFactory | null = null;

function _resolveHomeShellProps(props: HomeShellProps): ResolvedHomeShellProps {
  return {
    ...props,
    androidSystemService: props.androidSystemService ?? (_defaultAndroidSystemService ??= new AndroidSystemService()),
    musicTrackPreviewPlatformFactory:
      props.musicTrackPreviewPlatformFactory ??
      (_defaultMusicTrackPreviewPlatformFactory ??= new DefaultMusicTrackPreviewPlatformFactory()),
  };
}

function _isMusicTrackPreviewApi(api: unknown): api is MusicTrackPreviewApi {
  return (
    api != null &&
    typeof api === 'object' &&
    'downloadMusicTrackPreview' in api &&
    typeof (api as MusicTrackPreviewApi).downloadMusicTrackPreview === 'function'
  );
}

type HomeShellMethods = typeof HomeShellRealtime &
  typeof HomeShellRoomActions &
  typeof HomeShellLiveActions &
  typeof HomeShellMusicBox &
  typeof HomeShellMessages &
  typeof HomeShellDownloads &
  typeof HomeShellImagePreview &
  typeof HomeShellNotifications &
  typeof HomeShellSearch &
  typeof HomeShellLayout;

// eslint-disable-next-line @typescript-eslint/no-empty-object-type, @typescript-eslint/no-unsafe-declaration-merging
export interface HomeShellState extends HomeShellMethods {}

// eslint-disable-next-line @typescript-eslint/no-unsafe-declaration-merging
export class HomeShellState extends ViewState<ResolvedHomeShellProps> {
  constructor() {
    super();
    _installHomeShellMixins();
  }

  _services!: AuthenticatedAppServices;
  _currentUser!: CurrentUser;
  readonly _composerController = new _MentionTextEditingController();
  readonly _composerPanelController = new ChatComposerController();
  readonly _titleSearchController = new TextEditingController();
  _lastTitleSearchText = '';
  /** Dart `GlobalKey _composerDropKey`: the composer's drop-target box. */
  readonly _composerDropKey: RefObject<HTMLDivElement | null> = createRef<HTMLDivElement>();
  /** Dart `Object _searchTapRegionGroup` (a `TapRegion` group id). */
  readonly _searchTapRegionGroup: object = {};
  /** The `AppConfigScope` value, refreshed by [HomeShell] on every render. */
  _appConfig: AppConfig | null = null;
  _realtimeEvents: StreamSubscription | null = null;
  _accountSuspensionLogoutInProgress = false;
  _realtimeStatusEvents: StreamSubscription | null = null;
  _fileDropEvents: StreamSubscription | null = null;
  _androidNotificationSelections: StreamSubscription | null = null;
  _androidTaskRemovals: StreamSubscription | null = null;
  _androidPushRegistration: AndroidPushRegistrationController | null = null;
  _isAppForeground = true;
  _pendingNotificationRoomId: string | null = null;

  _servers: RoomCard[] = [];
  _loadingServers = false;
  _serverLoadError: string | null = null;
  _selectedServerId: string | null = null;
  _selectedRoom: RoomDetail | null = null;
  _live: LiveState | null = null;
  _messages: Message[] = [];
  _messageDrafts: Map<string, string> = new Map();
  _messageQuoteDrafts: Map<string, MessageQuote[]> = new Map();
  _messageComponentDrafts: Map<string, Message> = new Map();
  _copiedMessageComponent: Message | null = null;
  _stagedAttachmentDrafts: Map<string, _StagedAttachment[]> = new Map();
  _updatingComposerFromDraft = false;
  _composerMentionQuery: message_mentions.MessageMentionQuery | null = null;
  _composerMentionOptions: message_mentions.MessageMentionOption[] = [];
  _composerMentionSelectedIndex = 0;
  _composerMentionMembers: RoomMember[] = [];
  _composerMentionMembersRoomId: string | null = null;
  _loadingComposerMentionMembersRoomId: string | null = null;
  _loadingComposerMentionMembers = false;
  _composerMentionMembersSerial = 0;
  _locallyDeletedMessageKeys = new Set<string>();
  _selectedRoomNewMessageCount = 0;
  _focusedMessageId: string | null = null;
  _fileTransfers: Map<string, FileTransferState> = new Map();
  // Active file downloads, keyed by [file_display.fileDownloadKey]. Kept apart
  // from [_fileTransfers] (outgoing uploads) so the two never collide on a
  // message id and each tile can pick the transfer that applies to it.
  _fileDownloads: Map<string, FileTransferState> = new Map();
  _liveStageSelections = new Map<string, LiveStageSelection | null>();
  _liveScreenViewSyncTail: Promise<void> = Promise.resolve();
  _liveFullScreenTransitionTail: Promise<void> = Promise.resolve();
  _liveFullScreenTransitionGeneration = 0;
  _fullScreenLiveTrack: LiveVideoTrack | null = null;
  _windowCaptureExcludedForLiveFullScreen = false;
  _loadingRoom = false;
  _roomError: string | null = null;
  _sending = false;
  _sendError: string | null = null;
  _stickerPanelState: sticker_display.StickerPanelLoadState = new sticker_display.StickerPanelLoadState();
  _voiceState: voice_display.VoiceRecorderState = new voice_display.VoiceRecorderState();
  readonly _voicePlaybackService = new VoicePlaybackService();
  _livePresenceSoundPlayer!: LivePresenceSoundPlayer;
  _livePresenceSpeechPlayer!: LivePresenceSpeechPlayer;
  _livePresenceAudioCoordinator!: LivePresenceAudioCoordinator;
  _messageNotificationSoundPlayer!: MessageNotificationSoundPlayer;
  readonly _messageNotificationTracker = new RealtimeMessageNotificationTracker();
  _livePresenceEventTail: Promise<void> = Promise.resolve();
  _voicePlayback: VoicePlaybackSnapshot = VoicePlaybackSnapshot.idle;
  _voiceTicker: Timer | PeriodicTimer | null = null;
  _voiceStartedAt: Date | null = null;
  // Files picked for the next message, kept in pick order. Each uploads as
  // soon as it is picked; the message send later just collects the finished
  // assets. See [_StagedAttachment].
  _stagedAttachments: _StagedAttachment[] = [];
  readonly _clipboardService = new ClipboardService();
  readonly _fileDropService = new FileDropService();
  readonly _fileSelectionService = new FileSelectionService();
  _clipboardImagePasteSerial = 0;
  _pickingAttachments = false;
  _settingsOpen = false;
  _settingsInitialSection: SettingsSection = SettingsSection.profile;
  _settingsInitialMusicPlaylistId: string | null = null;
  _settingsAppUpdate: AvailableAppUpdate | null = null;
  _appUpdateDownloadCancellationToken: ReleaseDownloadCancellationToken | null = null;
  _logoutConfirming = false;
  _closeConfirming = false;
  _exitingApplication = false;
  _narrowContentOpen = false;
  _compactLayout = false;
  _auxiliaryOpenedFromNarrowSidebar = false;
  _contentMode: _ContentMode = _ContentMode.chat;
  _roomSettingsInitialSection: RoomSettingsSection = RoomSettingsSection.info;
  _roomSettingsInitialMusicPlaylistId: string | null = null;
  _superuserSettingsTarget: UserSummary | null = null;
  // Bumped to ask an open members panel to reload (e.g. after a
  // `room_join_requests_updated`, `room_role_changed`, or
  // `room_member_profile_changed` SSE event). The panel watches this via
  // didUpdateProps and re-pulls its members/requests.
  _membersReloadToken = 0;
  // Invalidates saved-playlist summaries and tracks cached by the open music
  // box after a successful edit in Settings or Room Settings. Playback state
  // remains server-authoritative and is intentionally not rewritten here.
  _musicPlaylistsRevision = 0;
  _membersInitialSearchQuery = '';
  _notificationInvites: RoomInvite[] = [];
  _notificationApplications: RoomApplication[] = [];
  _notificationRoomEvents: RoomEventNotification[] = [];
  _deferredRoomNotificationVisualReadAt: Date | null = null;
  _deferredRoomNotificationVisualReadIds: Set<string> = new Set();
  _loadingNotifications = false;
  _notificationError: string | null = null;
  _busyNotificationInviteId: string | null = null;
  _busyNotificationApplicationId: string | null = null;
  _hasPendingRoomInvites = false;
  _pendingRoomNotificationCount = 0;
  _selectedRoomHasPendingJoinRequests = false;
  _joinedLiveRoomId: string | null = null;
  _joinedLivePersonalAiVoiceAnnouncementsEnabled = false;
  _joinedLiveParticipantUsers = new Map<string, UserSummary>();
  _joinedLiveAuthoritativeParticipantIds: Set<string> = new Set<string>();
  _joinedLivePresenceCueParticipantIds = new Set<string>();
  _joinedLiveAuthoritativeSnapshotAt: Date | null = null;
  _joiningLive = false;
  _leavingLive = false;
  _syncingLiveConnectedParticipants = false;
  _micMuted = false;
  _headphonesMuted = false;
  readonly _liveStateRequests = new LiveStateRequestQueue();
  _lastInputVolumeBeforeMute = _defaultLiveVolumeRestore;
  _lastOutputVolumeBeforeMute = _defaultLiveVolumeRestore;
  _lastScreenShareVolumeBeforeMute = _defaultLiveVolumeRestore;
  _cameraOn = false;
  _screenSharing = false;
  _switchingAndroidLocalVideoSource = false;
  _voiceBlocked = false;
  _sharedMessageTrackPreviewController: MusicTrackPreviewController | null = null;
  _busyLiveMemberRemovalIds = new Set<string>();
  _busyLiveMemberModerationIds = new Set<string>();
  // The selected room's music box snapshot, or null when not loaded / disabled.
  // Overwritten wholesale from state fetches, write responses, and the
  // `music_box_changed` SSE event; never merged field by field.
  _musicBox: MusicBoxState | null = null;
  // Guards the optional state fetch across room-open and live-join entry
  // points. A live join can happen while the initial room fetch is still
  // competing for a refreshed access token, so it also acts as a safe retry.
  _musicBoxLoadingRoomId: string | null = null;
  _musicBoxPendingRefreshRoomId: string | null = null;
  _musicBoxRoomSerial = 0;
  _musicBoxLoadFailures = 0;
  _musicBoxLoadRetry: Timer | null = null;
  // Whether the in-pane music box panel is expanded over the live channel.
  _musicBoxOpen = false;
  // Drives the music box search field; results are fetched debounced.
  readonly _musicBoxSearchController = new TextEditingController();
  _lastMusicBoxSearchText = '';
  _musicBoxSearchResults: MusicBoxSearchResult[] = [];
  _musicBoxSearching = false;
  _musicBoxSearchError: string | null = null;
  // The selected search source forwarded to the GD music API (defaults to
  // netease). Changing it re-runs the current query.
  _musicBoxSource: string = music_box_display.musicBoxDefaultSource;
  _musicBoxSearchSerial = 0;
  _musicBoxSearchDebounce: Timer | null = null;
  _searchDebounce: Timer | null = null;
  _searchRequestSerial = 0;
  _searchQuery = '';
  _searchExpanded = false;
  _titleSearchContextMenuOpen = false;
  _pendingTitleSearchContextMenuUpdate: (() => void) | null = null;
  _searching = false;
  _searchLoadingMore = false;
  _searchError: string | null = null;
  _searchResults: GlobalSearchResults | null = null;
  _activeSearchCategory: search_display.GlobalSearchCategory | null = null;
  _busySearchPublicRoomId: string | null = null;
  _searchPendingPublicRoomIds: Set<string> = new Set();
  _showingJoinApplicationDialog = false;
  _realtimeStatus: RealtimeConnectionStatus = RealtimeConnectionStatus.offline;

  // PORT: Dart tear-offs (`addListener(_handleX)` / `removeListener(_handleX)`)
  // compare equal per instance. These bound listeners give the add/remove
  // pairs the same stable identity.
  readonly _composerDraftListener = (): void => this._handleComposerDraftChanged();
  readonly _titleSearchListener = (): void => this._handleTitleSearchChanged();
  readonly _musicBoxSearchListener = (): void => this._handleMusicBoxSearchChanged();
  readonly _liveSessionChangedListener = (): void => this._onLiveSessionChanged();
  readonly _windowCloseRequestHandler = (): Promise<boolean> => this._handleWindowCloseRequest();
  readonly _trayExitHandler = (): Promise<void> => this._exitApplication();
  readonly _visibilityChangeListener = (): void => {
    if (typeof document === 'undefined') return;
    this.didChangeAppLifecycleState(
      document.visibilityState === 'visible' ? AppLifecycleState.resumed : AppLifecycleState.hidden,
    );
  };

  get _roomsController(): RoomsController {
    return this._services.rooms;
  }

  get _messagesController(): MessagesController {
    return this._services.messages;
  }

  get _globalSearchController(): GlobalSearchController {
    return this._services.search;
  }

  get _stickerPacksController(): StickerPacksController {
    return this._services.stickers;
  }

  get _voiceRecorder(): VoiceRecorderController {
    return this._services.voiceRecorder;
  }

  get _liveController(): LiveController {
    return this._services.live;
  }

  get _liveSessionController(): LiveSessionController {
    return this._services.liveSession;
  }

  get _musicBoxController(): MusicBoxController {
    return this._services.musicBox;
  }

  get _fileDownloadsController(): FileDownloadsController {
    return this._services.fileDownloads;
  }

  get _mediaCacheController(): MediaCacheController {
    return this._services.mediaCache;
  }

  get _serverNow(): Date {
    return this.widget.app.serverClock.now();
  }

  get _appUpdateDownloadInProgress(): boolean {
    return this._appUpdateDownloadCancellationToken != null;
  }

  override initState(): void {
    const widget = this.widget;
    if (widget.androidSystemService.isSupported) {
      // PORT: `WidgetsBinding.instance.addObserver(this)`; the DOM page
      // visibility stands in for the app lifecycle.
      if (typeof document !== 'undefined') {
        document.addEventListener('visibilitychange', this._visibilityChangeListener);
      }
      this._androidNotificationSelections = widget.androidSystemService.selectedNotificationRoomIds.listen((roomId) =>
        this._handleAndroidNotificationRoomSelected(roomId),
      );
      this._androidTaskRemovals = widget.androidSystemService.taskRemovalRequests.listen((request) =>
        unawaited(this._handleAndroidTaskRemoved(request)),
      );
      unawaited(this._takeInitialAndroidNotification());
      unawaited(this._ensureAndroidNotificationPermission());
    }
    this._currentUser = widget.app.currentUser;
    this._livePresenceSoundPlayer = widget.livePresenceSoundPlayer ?? new LivePresenceSoundService();
    this._livePresenceSpeechPlayer = widget.livePresenceSpeechPlayer ?? new SystemLivePresenceSpeechPlayer();
    this._livePresenceAudioCoordinator = new LivePresenceAudioCoordinator({
      soundPlayer: this._livePresenceSoundPlayer,
      speechPlayer: this._livePresenceSpeechPlayer,
    });
    this._messageNotificationSoundPlayer = widget.messageNotificationSoundPlayer ?? new MessageNotificationSoundService();
    this._composerController.addListener(this._composerDraftListener);
    this._titleSearchController.addListener(this._titleSearchListener);
    this._musicBoxSearchController.addListener(this._musicBoxSearchListener);
    this._voicePlaybackService.state.addListener(this._handleVoicePlaybackChanged);
    widget.app.serverClock.addListener(this._handleServerClockChanged);
    this._installServices();
    this._installAndroidPushRegistration();
    this._attachLiveSessionCallbacks();
    widget.windowController.setCloseRequestHandler(this._windowCloseRequestHandler);
    widget.windowController.setTrayExitHandler(this._trayExitHandler);
    this._fileDropEvents = this._fileDropService.drops.listen((event) => this._handleDroppedFiles(event));
    this._clipboardService.setWebPasteHandler((files, text) => this._handleWebPaste(files, text));
    this._startRealtime();
    const detectedAppUpdate = widget.detectedAppUpdate ?? null;
    if (detectedAppUpdate != null) {
      this._showSettingsAppUpdateInState(detectedAppUpdate);
      _addPostFrameCallback(() => {
        if (!this.mounted) return;
        this.widget.onDetectedAppUpdateShown?.();
      });
    }
    unawaited(this._loadServers());
    unawaited(this._refreshPendingRoomInviteBadge());
    // PORT: ViewState runs initState after the first (empty) render; rebuild
    // now that the services exist.
    this.setState();
  }

  override didUpdateProps(oldWidget: ResolvedHomeShellProps): void {
    const widget = this.widget;
    if (oldWidget.windowController !== widget.windowController) {
      oldWidget.windowController.setCloseRequestHandler(null);
      oldWidget.windowController.setTrayExitHandler(null);
      widget.windowController.setCloseRequestHandler(this._windowCloseRequestHandler);
      widget.windowController.setTrayExitHandler(this._trayExitHandler);
    }
    if ((oldWidget.detectedAppUpdate ?? null) !== (widget.detectedAppUpdate ?? null) && widget.detectedAppUpdate != null) {
      this._openSettingsForAppUpdate(widget.detectedAppUpdate);
      _addPostFrameCallback(() => {
        if (!this.mounted) return;
        this.widget.onDetectedAppUpdateShown?.();
      });
    }
    const appChanged =
      widget.app.currentUser.id !== oldWidget.app.currentUser.id || !widget.app.hasSameApiSource(oldWidget.app);
    if (!appChanged) {
      if (oldWidget.app.serverClock !== widget.app.serverClock) {
        oldWidget.app.serverClock.removeListener(this._handleServerClockChanged);
        widget.app.serverClock.addListener(this._handleServerClockChanged);
      }
      return;
    }

    oldWidget.app.serverClock.removeListener(this._handleServerClockChanged);
    widget.app.serverClock.addListener(this._handleServerClockChanged);
    this._detachLiveSessionCallbacks();
    unawaited(this._androidPushRegistration?.dispose());
    this._androidPushRegistration = null;
    this._services.close();
    this._installServices();
    this._installAndroidPushRegistration();
    this._messageNotificationTracker.clear();
    this._attachLiveSessionCallbacks();
    this._startRealtime();
    this._searchDebounce?.cancel();
    this._searchRequestSerial++;
    this._titleSearchController.removeListener(this._titleSearchListener);
    this._titleSearchController.clear();
    this._lastTitleSearchText = this._titleSearchController.text;
    this._titleSearchContextMenuOpen = false;
    this._pendingTitleSearchContextMenuUpdate = null;
    this._titleSearchController.addListener(this._titleSearchListener);
    this._setComposerText('', { saveDraft: false });
    this._resetLiveFullScreenForLifecycle({ windowController: oldWidget.windowController });
    this.setState(() => {
      this._currentUser = widget.app.currentUser;
      this._servers = [];
      this._serverLoadError = null;
      this._selectedServerId = null;
      this._selectedRoom = null;
      this._live = null;
      this._messages = [];
      this._cancelAllDraftAttachments();
      this._stagedAttachments.length = 0;
      this._messageDrafts = new Map();
      this._stagedAttachmentDrafts = new Map();
      this._selectedRoomNewMessageCount = 0;
      this._fileTransfers = new Map();
      this._fileDownloads = new Map();
      this._liveStageSelections.clear();
      this._fullScreenLiveTrack = null;
      this._loadingRoom = false;
      this._roomError = null;
      this._sending = false;
      this._sendError = null;
      this._stickerPanelState = new sticker_display.StickerPanelLoadState();
      this._settingsOpen = false;
      this._settingsAppUpdate = null;
      this._logoutConfirming = false;
      this._narrowContentOpen = false;
      this._auxiliaryOpenedFromNarrowSidebar = false;
      this._contentMode = _ContentMode.chat;
      this._superuserSettingsTarget = null;
      this._membersInitialSearchQuery = '';
      this._notificationInvites = [];
      this._notificationApplications = [];
      this._notificationRoomEvents = [];
      this._loadingNotifications = false;
      this._notificationError = null;
      this._busyNotificationInviteId = null;
      this._busyNotificationApplicationId = null;
      this._hasPendingRoomInvites = false;
      this._pendingRoomNotificationCount = 0;
      this._selectedRoomHasPendingJoinRequests = false;
      this._joinedLiveRoomId = null;
      this._joinedLivePersonalAiVoiceAnnouncementsEnabled = false;
      this._joinedLiveParticipantUsers.clear();
      this._joinedLiveAuthoritativeParticipantIds = new Set<string>();
      this._joinedLivePresenceCueParticipantIds.clear();
      this._joinedLiveAuthoritativeSnapshotAt = null;
      this._joiningLive = false;
      this._leavingLive = false;
      this._micMuted = false;
      this._headphonesMuted = false;
      this._cameraOn = false;
      this._screenSharing = false;
      this._switchingAndroidLocalVideoSource = false;
      this._voiceBlocked = false;
      this._resetMusicBox();
      this._searchQuery = '';
      this._searchExpanded = false;
      this._searching = false;
      this._searchLoadingMore = false;
      this._searchError = null;
      this._searchResults = null;
      this._activeSearchCategory = null;
      this._busySearchPublicRoomId = null;
      this._searchPendingPublicRoomIds = new Set();
      this._voicePlayback = VoicePlaybackSnapshot.idle;
    });
    unawaited(this._voicePlaybackService.stop());
    unawaited(this._loadServers());
    unawaited(this._refreshPendingRoomInviteBadge());
  }

  override dispose(): void {
    const widget = this.widget;
    if (widget.androidSystemService.isSupported) {
      if (typeof document !== 'undefined') {
        document.removeEventListener('visibilitychange', this._visibilityChangeListener);
      }
      unawaited(widget.androidSystemService.enterForeground());
    }
    this._androidNotificationSelections?.cancel();
    this._androidTaskRemovals?.cancel();
    unawaited(this._androidPushRegistration?.dispose());
    this._realtimeEvents?.cancel();
    this._realtimeStatusEvents?.cancel();
    this._fileDropEvents?.cancel();
    this._clipboardService.setWebPasteHandler(null);
    this._resetLiveFullScreenForLifecycle({ windowController: widget.windowController });
    this._detachLiveSessionCallbacks();
    widget.windowController.setCloseRequestHandler(null);
    widget.windowController.setTrayExitHandler(null);
    this._voiceTicker?.cancel();
    this._musicBoxLoadRetry?.cancel();
    this._musicBoxSearchDebounce?.cancel();
    this._musicBoxSearchController.dispose();
    this._searchDebounce?.cancel();
    this._titleSearchController.removeListener(this._titleSearchListener);
    this._titleSearchController.dispose();
    this._composerController.removeListener(this._composerDraftListener);
    this._composerController.dispose();
    this._composerPanelController.dispose();
    this._voicePlaybackService.state.removeListener(this._handleVoicePlaybackChanged);
    unawaited(this._voicePlaybackService.dispose());
    this._livePresenceAudioCoordinator.dispose();
    unawaited(this._livePresenceSoundPlayer.dispose());
    unawaited(this._livePresenceSpeechPlayer.dispose());
    unawaited(this._messageNotificationSoundPlayer.dispose());
    const sharedTrackPreview = this._sharedMessageTrackPreviewController;
    if (sharedTrackPreview != null) unawaited(sharedTrackPreview.dispose());
    widget.app.serverClock.removeListener(this._handleServerClockChanged);
    this._cancelActiveDownloads();
    this._services.close();
  }

  didChangeAppLifecycleState(state: AppLifecycleState): void {
    const androidSystemService = this.widget.androidSystemService;
    if (!androidSystemService.isSupported) return;
    switch (state) {
      case AppLifecycleState.resumed:
        this._isAppForeground = true;
        unawaited(androidSystemService.enterForeground());
        unawaited(this._takeInitialAndroidNotification());
        unawaited(this._androidPushRegistration?.synchronize().catch(() => {}));
        break;
      case AppLifecycleState.paused:
      case AppLifecycleState.hidden:
        this._isAppForeground = false;
        unawaited(androidSystemService.enterBackground());
        break;
      case AppLifecycleState.inactive:
        // Start while the activity is still visible. Android 12+ rejects most
        // foreground-service starts after the app is already backgrounded.
        unawaited(androidSystemService.enterBackground());
        break;
      case AppLifecycleState.detached:
        break;
    }
  }

  async _takeInitialAndroidNotification(): Promise<void> {
    try {
      const roomId = await this.widget.androidSystemService.takeInitialNotificationRoomId();
      if (roomId != null) this._handleAndroidNotificationRoomSelected(roomId);
    } catch {
      // Best effort.
    }
  }

  async _ensureAndroidNotificationPermission(): Promise<void> {
    try {
      if (!(await this.widget.androidSystemService.notificationPreferenceEnabled())) {
        return;
      }
      await this.widget.androidSystemService.requestNotificationPermission();
    } catch {
      // Best effort.
    }
  }

  _handleAndroidNotificationRoomSelected(roomId: string): void {
    if (!this.mounted || roomId.trim().length === 0) return;
    const room = this._servers.find((candidate) => candidate.id === roomId) ?? null;
    if (room == null) {
      this._pendingNotificationRoomId = roomId;
      return;
    }
    this._pendingNotificationRoomId = null;
    unawaited(this._openRoom(room, { openContent: true }));
  }

  _openPendingAndroidNotificationRoom(): void {
    const roomId = this._pendingNotificationRoomId;
    if (roomId == null) return;
    this._handleAndroidNotificationRoomSelected(roomId);
  }

  _installServices(): void {
    const widget = this.widget;
    const previousSharedTrackPreview = this._sharedMessageTrackPreviewController;
    this._services = new AuthenticatedAppServices(widget.app, {
      audioDeviceStore: widget.audioDeviceStore,
      liveSessionController: widget.liveSessionController,
      realtime: widget.realtime,
    });
    const api = this._services.api;
    this._sharedMessageTrackPreviewController = _isMusicTrackPreviewApi(api)
      ? new MusicTrackPreviewController({
          api,
          platform: widget.musicTrackPreviewPlatformFactory.create(),
        })
      : null;
    if (previousSharedTrackPreview != null) {
      unawaited(previousSharedTrackPreview.dispose());
    }
  }

  _installAndroidPushRegistration(): void {
    const widget = this.widget;
    if (!widget.androidSystemService.isSupported) return;
    const controller = new AndroidPushRegistrationController({
      api: this._services.api,
      androidSystemService: widget.androidSystemService,
    });
    this._androidPushRegistration = controller;
    unawaited(controller.start().catch(() => {}));
  }

  readonly _handleServerClockChanged = (): void => {
    if (!this.mounted) return;
    this.setState(() => {});
  };

  _setHomeState(update: () => void): void {
    this.setState(update);
  }

  _restorePaneAfterAuxiliaryInState(): void {
    const returnToNarrowSidebar = this._auxiliaryOpenedFromNarrowSidebar;
    this._auxiliaryOpenedFromNarrowSidebar = false;
    if (returnToNarrowSidebar || this._selectedServerId == null) {
      this._narrowContentOpen = false;
    }
  }

  _showSettingsAppUpdateInState(update: AvailableAppUpdate): void {
    this._settingsOpen = true;
    this._settingsInitialSection = SettingsSection.about;
    this._settingsInitialMusicPlaylistId = null;
    this._settingsAppUpdate = update;
    this._auxiliaryOpenedFromNarrowSidebar = false;
    this._contentMode = _ContentMode.chat;
    this._narrowContentOpen = true;
  }

  _openSettingsForAppUpdate(update: AvailableAppUpdate): void {
    if (!this.mounted) return;
    this._setHomeState(() => this._showSettingsAppUpdateInState(update));
  }

  readonly _handleVoicePlaybackChanged = (): void => {
    if (!this.mounted) return;
    this._setHomeState(() => {
      this._voicePlayback = this._voicePlaybackService.state.value;
    });
  };

  async _toggleVoicePlayback(opts: { messageId: string; resolvedUrl: string }): Promise<void> {
    const { messageId, resolvedUrl } = opts;
    try {
      await this._voicePlaybackService.toggle({ messageId, resolvedUrl });
    } catch (error) {
      if (!this.mounted) return;
      this._setHomeState(() => {
        this._sendError = userFacingErrorMessage(error);
      });
    }
  }

  _attachLiveSessionCallbacks(): void {
    this._liveSessionController.attachSessionCallbacks({
      onChanged: this._liveSessionChangedListener,
      onForciblyRemoved: () => this._onForciblyRemovedFromLive(),
      onUnexpectedlyDisconnected: () => this._onUnexpectedlyDisconnectedFromLive(),
      onPublishPermissionChanged: (canPublish) => this._onPublishPermissionChanged(canPublish),
      onParticipantJoined: (participantIdentity) => this._onLiveParticipantJoined(participantIdentity),
      onParticipantLeft: (participantIdentity, departureKind) =>
        this._onLiveParticipantLeft(participantIdentity, departureKind),
    });
  }

  _detachLiveSessionCallbacks(): void {
    this._liveSessionController.detachSessionCallbacks({
      onChanged: this._liveSessionChangedListener,
    });
  }

  /**
   * Port of `build(BuildContext)`. [shellSize] is the measured inner size of
   * the bordered shell box (the outer `LayoutBuilder`'s constraints; null
   * before the first measurement) and [shellRef] attaches that box.
   */
  build(shellSize: ElementSize | null, shellRef: (node: HTMLDivElement | null) => void): ReactNode {
    const useAndroidLayout = HostPlatform.isAndroid;
    const liveMusicBoxVisible =
      this._contentMode === _ContentMode.live &&
      this._joinedLiveRoomId === this._selectedServerId &&
      this._musicBox?.enabled === true &&
      this._musicBoxOpen;
    // PORT: Scaffold.resizeToAvoidBottomInset has no DOM counterpart; the
    // decision is kept for parity but the page never resizes for an IME here.
    shouldResizeHomeForKeyboard({ platform: HostPlatform.operatingSystem, liveMusicBoxVisible });

    // PORT: Flutter runs initState before the first build; ViewState mounts
    // after the first render, so that render only paints the empty scaffold.
    if (!this.mounted) return <div style={_scaffoldStyle} />;

    const fullScreenTrack = this._resolveFullScreenLiveTrack();
    const joinedLiveRoom = live_display.joinedLiveRoomSummary({
      joinedLiveRoomId: this._joinedLiveRoomId,
      selectedRoom: this._selectedRoom,
      rooms: this._servers,
    });
    const liveRoomDockBuilder = (maxWidth: number, opts: { fillAvailable?: boolean } = {}): ReactNode => (
      <_TitleLiveRoomDock
        maxWidth={maxWidth}
        fillAvailable={opts.fillAvailable ?? false}
        room={joinedLiveRoom}
        micMuted={this._micMuted}
        headphonesMuted={this._headphonesMuted}
        voiceBlocked={this._voiceBlocked}
        interactionLocked={this._appUpdateDownloadInProgress}
        onOpen={joinedLiveRoom == null ? null : () => unawaited(this._openJoinedLiveChannel())}
        onToggleMic={this._voiceBlocked ? null : () => this._toggleMicMute()}
        onToggleHeadphones={() => this._toggleHeadphonesMute()}
        onLeave={joinedLiveRoom == null ? null : () => unawaited(this._leaveLive())}
      />
    );

    const maxWidth = shellSize?.width ?? (typeof window === 'undefined' ? 0 : Math.max(0, window.innerWidth - 2));
    const narrowLayout = usesNarrowHomeLayout(maxWidth);
    this._compactLayout = narrowLayout;
    const titleSearchLayout = _homeTitleBarSearchLayout(maxWidth, { hasLiveRoom: true });
    const showSearchOverlay = titleSearchLayout != null && !this._appUpdateDownloadInProgress;
    // The inner LayoutBuilder (below the title bar) sees the same width as
    // the outer one: both sit inside the shell border at full width.
    const narrow = usesNarrowHomeLayout(maxWidth);
    const content = (
      <Stack style={{ flex: '1 1 0', minWidth: 0, minHeight: 0 }}>
        <Column key={this.widget.app.currentUser.id} crossAxisAlignment="stretch" style={{ position: 'absolute', inset: 0 }}>
          <_HomeTitleBar
            windowController={this.widget.windowController}
            searchController={this._titleSearchController}
            searchTapRegionGroup={this._searchTapRegionGroup}
            liveRoomDockBuilder={liveRoomDockBuilder}
            interactionLocked={this._appUpdateDownloadInProgress}
            onActivateSearch={() => this._activateSearch()}
            onSearchTapOutside={() => this._collapseSearch()}
            onSearchContextMenuOpenChanged={(open: boolean) => this._handleTitleSearchContextMenuOpenChanged(open)}
            onClearSearchQuery={() => this._clearSearchQuery()}
          />
          <Expanded>
            {this._buildAppUpdateLockedBody(
              narrow ? (
                this._buildNarrowLayout(maxWidth, {
                  footerLiveRoomDockBuilder: useAndroidLayout ? liveRoomDockBuilder : null,
                })
              ) : (
                <Row crossAxisAlignment="stretch" style={{ flex: '1 1 0', minWidth: 0, minHeight: 0 }}>
                  {this._buildSidebar({ width: sidebarWidth, openContentOnSelect: false })}
                  <Expanded>{this._buildContentPane()}</Expanded>
                </Row>
              ),
            )}
          </Expanded>
        </Column>
        {this._hasSearchQuery && this._searchExpanded && showSearchOverlay ? (
          <Positioned top={_homeTitleBarHeight - 1} left={titleSearchLayout.left} width={titleSearchLayout.width}>
            {this._buildSearchResultsTapRegion()}
          </Positioned>
        ) : null}
        {fullScreenTrack != null ? (
          <Positioned fill style={{ display: 'flex', flexDirection: 'column' }}>
            <FullScreenMediaOrientation>
              <LiveFullScreenStage
                track={fullScreenTrack}
                label={liveStageTrackLabel(this._live, fullScreenTrack)}
                cameraMirrored={
                  live_display.liveParticipantByUserId(this._live, fullScreenTrack.identity)?.cameraMirrored ?? false
                }
                screenShareViewers={
                  fullScreenTrack.isScreenShare
                    ? live_display.liveScreenShareViewers(this._live, fullScreenTrack.identity)
                    : []
                }
                screenShareVolume={this._liveSessionController.screenShareVolume}
                onScreenShareVolumeChanged={(volume: number) => this._changeScreenShareVolume(volume)}
                onScreenShareMuteToggled={() => this._toggleScreenShareAudioMute()}
                onExit={() => this._exitLiveFullScreen()}
              />
            </FullScreenMediaOrientation>
          </Positioned>
        ) : null}
      </Stack>
    );
    const responsiveContent = <HomeAdaptiveLayout compact={narrowLayout}>{content}</HomeAdaptiveLayout>;
    const shellContent = !useAndroidLayout ? (
      responsiveContent
    ) : (
      <_ShellBackScope
        key="android-shell-back-scope"
        canPop={!this._canHandleShellBack({ narrowLayout })}
        onBackInvoked={() => this._handleShellBack({ narrowLayout })}
      >
        {responsiveContent}
      </_ShellBackScope>
    );
    return (
      <MediaCacheScope cache={this._mediaCacheController}>
        <ChatImagePreviewActionsScope actions={this._imagePreviewActions}>
          <div style={_scaffoldStyle}>
            {_withAndroidBottomSafeArea({
              enabled: useAndroidLayout,
              preserveKeyboardViewport: liveMusicBoxVisible,
              child: (
                <div ref={shellRef} style={_shellBorderStyle}>
                  {shellContent}
                </div>
              ),
            })}
          </div>
        </ChatImagePreviewActionsScope>
      </MediaCacheScope>
    );
  }

  get _selectedServer(): RoomCard | null {
    for (const server of this._servers) {
      if (server.id === this._selectedServerId) return server;
    }
    return null;
  }

  _buildAppUpdateLockedBody(child: ReactNode): ReactNode {
    const locked = this._appUpdateDownloadInProgress;
    // Focus(canRequestFocus/descendantsAreFocusable: !locked) + AbsorbPointer.
    return (
      <div
        inert={locked}
        aria-disabled={locked || undefined}
        style={{
          flex: '1 1 0',
          minWidth: 0,
          minHeight: 0,
          display: 'flex',
          flexDirection: 'column',
          pointerEvents: locked ? 'none' : undefined,
        }}
      >
        {child}
      </div>
    );
  }
}

const _scaffoldStyle: CSSProperties = {
  flex: '1 1 0',
  alignSelf: 'stretch',
  display: 'flex',
  flexDirection: 'column',
  width: '100%',
  height: '100%',
  minWidth: 0,
  minHeight: 0,
  backgroundColor: UiColors.background,
};

const _shellBorderStyle: CSSProperties = {
  flex: '1 1 0',
  minWidth: 0,
  minHeight: 0,
  display: 'flex',
  flexDirection: 'column',
  position: 'relative',
  boxSizing: 'border-box',
  border: `1px solid ${_windowEdgeBorder}`,
};

function _addPostFrameCallback(callback: () => void): void {
  if (typeof requestAnimationFrame === 'function') {
    requestAnimationFrame(() => callback());
    return;
  }
  setTimeout(callback, 0);
}

/**
 * Port of the Android `PopScope(canPop:, onPopInvokedWithResult:)` around the
 * shell. PORT: the DOM has no system back gesture; Android browsers and
 * WebViews deliver the hardware back key as `GoBack` / `BrowserBack`.
 */
function _ShellBackScope({
  canPop,
  onBackInvoked,
  children,
}: {
  canPop: boolean;
  onBackInvoked: () => void;
  children: ReactNode;
}) {
  useEffect(() => {
    if (typeof document === 'undefined') return;
    const listener = (event: KeyboardEvent) => {
      if (event.key !== 'GoBack' && event.key !== 'BrowserBack') return;
      if (canPop) return;
      event.preventDefault();
      onBackInvoked();
    };
    document.addEventListener('keydown', listener);
    return () => document.removeEventListener('keydown', listener);
  }, [canPop, onBackInvoked]);
  return <>{children}</>;
}

/**
 * `LayoutBuilder` stand-in for the shell box: a callback ref plus the box's
 * client size (inside its border), re-observed whenever the node changes.
 */
function _useShellBoxSize(): [(node: HTMLDivElement | null) => void, ElementSize | null] {
  const [node, setNode] = useState<HTMLDivElement | null>(null);
  const [size, setSize] = useState<ElementSize | null>(null);
  useLayoutEffect(() => {
    if (node == null) return;
    const update = () => {
      const width = node.clientWidth;
      const height = node.clientHeight;
      setSize((previous) =>
        previous != null && Math.abs(previous.width - width) < 0.5 && Math.abs(previous.height - height) < 0.5
          ? previous
          : { width, height },
      );
    };
    update();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(update);
    observer.observe(node);
    return () => observer.disconnect();
  }, [node]);
  return [setNode, size];
}

export function HomeShell(props: HomeShellProps) {
  const widget = useMemo(() => _resolveHomeShellProps(props), [props]);
  const state = useViewState(() => new HomeShellState(), widget);
  // Dart read AppConfigScope.of(context) inside the state's methods; publish
  // the scoped config so the message/attachment actions can resolve URLs.
  state._appConfig = useAppConfig();
  const [shellRef, shellSize] = _useShellBoxSize();
  return state.build(shellSize, shellRef);
}

let _homeShellMixinsInstalled = false;

/**
 * Merges the extension method tables into [HomeShellState.prototype].
 *
 * PORT: done on first construction rather than at module evaluation, because
 * the part modules import values (`_ContentMode`, `_StagedAttachment`, …) from
 * this module; with an import cycle a table may not be initialised yet while
 * this module body runs. Property descriptors are copied (not
 * `Object.assign`) so getters in the tables (Dart extension getters such as
 * `_imagePreviewActions`) stay getters bound to the instance.
 */
function _installHomeShellMixins(): void {
  if (_homeShellMixinsInstalled) return;
  _homeShellMixinsInstalled = true;
  const tables: object[] = [
    HomeShellRealtime,
    HomeShellRoomActions,
    HomeShellLiveActions,
    HomeShellMusicBox,
    HomeShellMessages,
    HomeShellDownloads,
    HomeShellImagePreview,
    HomeShellNotifications,
    HomeShellSearch,
    HomeShellLayout,
  ];
  for (const table of tables) {
    Object.defineProperties(HomeShellState.prototype, Object.getOwnPropertyDescriptors(table));
  }
}

export function _roomTitle(room: RoomDetail | null, card: RoomCard | null): string {
  const detailRemark = room?.remarkName?.trim();
  if (detailRemark != null && detailRemark.length > 0) return detailRemark;
  const detailTitle = room?.name.trim();
  if (detailTitle != null && detailTitle.length > 0) return detailTitle;
  const cardTitle = card?.displayName.trim();
  if (cardTitle != null && cardTitle.length > 0) return cardTitle;
  return '聊天';
}

/**
 * A file staged in the composer. Uploading starts the moment the file is
 * picked, so the entry tracks the in-flight upload (progress + cancellation)
 * and caches the resulting [asset] once it lands, ready for the next send.
 */
export class _StagedAttachment {
  constructor(opts: { id: string; file: SelectedFile }) {
    this.id = opts.id;
    this.file = opts.file;
    this.status = ComposerAttachmentStatus.uploading;
  }

  readonly id: string;
  readonly file: SelectedFile;
  readonly uploadController = new UploadTransferController();

  status: ComposerAttachmentStatus;
  asset: UploadedAsset | null = null;
  sizeBytes: number | null = null;
  progress: number | null = null;
  error: unknown = null;
  errorMessage: string | null = null;

  get isUploaded(): boolean {
    return this.status === ComposerAttachmentStatus.uploaded;
  }
}

/**
 * The composer's controller: tracks the `@name` mentions confirmed from the
 * suggestion list so they render highlighted and delete as a unit.
 *
 * PORT: Flutter painted [buildTextSpan] inside the field. A DOM `<textarea>`
 * cannot style ranges of its value, so the composer shows plain text; the
 * span builder is kept (as React nodes) for a highlight layer, and the
 * mention bookkeeping / whole-mention backspace work on the textarea's
 * value + selection exactly as before.
 */
export class _MentionTextEditingController extends TextEditingController {
  _confirmedMentions: readonly _ConfirmedComposerMention[] = [];

  clearConfirmedMentions(): void {
    if (this._confirmedMentions.length === 0) return;
    this._confirmedMentions = [];
    this.notifyListeners();
  }

  addConfirmedMention(opts: { start: number; label: string }): void {
    const { start, label } = opts;
    const trimmedLabel = label.trim();
    if (trimmedLabel.length === 0) return;
    const mentionText = `@${trimmedLabel}`;
    const end = start + mentionText.length;
    const text = this.text;
    if (start < 0 || end > text.length || text.substring(start, end) !== mentionText) {
      return;
    }
    const next = [
      ...this._validConfirmedMentions().filter((mention) => mention.end <= start || mention.start >= end),
      new _ConfirmedComposerMention({ start, end, text: mentionText }),
    ].sort((a, b) => a.start - b.start);
    this._confirmedMentions = Object.freeze(next);
    this.notifyListeners();
  }

  pruneInvalidConfirmedMentions(opts: { notify?: boolean } = {}): void {
    const { notify = true } = opts;
    const valid = this._validConfirmedMentions();
    if (valid.length === this._confirmedMentions.length) return;
    this._confirmedMentions = valid;
    if (notify) this.notifyListeners();
  }

  confirmedMentionLabels(): string[] {
    return this._validConfirmedMentions().map((mention) => mention.text.substring(1));
  }

  hasConfirmedMention(opts: { start: number; end: number }): boolean {
    for (const mention of this._validConfirmedMentions()) {
      if (mention.start === opts.start && mention.end === opts.end) return true;
    }
    return false;
  }

  formatConfirmedMentionBackspace(opts: {
    oldValue: TextEditingValue;
    defaultValue: TextEditingValue;
  }): TextEditingValue | null {
    const { oldValue, defaultValue } = opts;
    const selection = oldValue.selection;
    if (!selection.isValid || !selection.isCollapsed) return null;
    const cursor = selection.extentOffset;
    if (cursor <= 0) return null;
    if (oldValue.text.length !== defaultValue.text.length + 1) return null;
    const defaultBackspaceText = oldValue.text.substring(0, cursor - 1) + oldValue.text.substring(cursor);
    if (defaultValue.text !== defaultBackspaceText) return null;

    const mentions = this._validConfirmedMentionsFor(oldValue.text);
    for (const mention of mentions) {
      const hasTrailingSpace = mention.end < oldValue.text.length && oldValue.text.charCodeAt(mention.end) === 0x20;
      const deleteEnd =
        cursor === mention.end ? mention.end : hasTrailingSpace && cursor === mention.end + 1 ? mention.end + 1 : null;
      if (deleteEnd == null) continue;
      const removedLength = deleteEnd - mention.start;
      const nextText = oldValue.text.substring(0, mention.start) + oldValue.text.substring(deleteEnd);
      const nextMentions: _ConfirmedComposerMention[] = [];
      for (const entry of mentions) {
        if (entry.start === mention.start && entry.end === mention.end) {
          continue;
        }
        nextMentions.push(entry.start >= deleteEnd ? entry.shift(-removedLength) : entry);
      }
      this._confirmedMentions = Object.freeze(nextMentions);
      return oldValue.copyWith({
        text: nextText,
        selection: TextSelection.collapsed({ offset: mention.start }),
        composing: TextRange.empty,
      });
    }
    return null;
  }

  _validConfirmedMentions(): readonly _ConfirmedComposerMention[] {
    return this._validConfirmedMentionsFor(this.text);
  }

  _validConfirmedMentionsFor(value: string): readonly _ConfirmedComposerMention[] {
    if (this._confirmedMentions.length === 0) return [];
    return this._confirmedMentions.filter(
      (mention) =>
        mention.start >= 0 && mention.end <= value.length && value.substring(mention.start, mention.end) === mention.text,
    );
  }

  /** Port of `buildTextSpan`: the field text with confirmed mentions highlighted. */
  buildTextSpan(opts: { style?: CSSProperties | null; withComposing?: boolean } = {}): ReactNode {
    const textStyle = opts.style ?? undefined;
    const text = this.text;
    const mentions = this._validConfirmedMentions();
    if (mentions.length === 0) {
      return <span style={textStyle}>{text}</span>;
    }

    const mentionStyle: CSSProperties = { color: UiColors.controlAccent, fontWeight: 600 };
    const spans: ReactNode[] = [];
    let cursor = 0;
    for (const mention of mentions) {
      if (mention.start > cursor) {
        spans.push(<span key={`t${cursor}`}>{text.substring(cursor, mention.start)}</span>);
      }
      spans.push(
        <span key={`m${mention.start}`} style={mentionStyle}>
          {text.substring(mention.start, mention.end)}
        </span>,
      );
      cursor = mention.end;
    }
    if (cursor < text.length) {
      spans.push(<span key={`t${cursor}`}>{text.substring(cursor)}</span>);
    }
    return <span style={textStyle}>{spans}</span>;
  }
}

export class _ConfirmedMentionBackspaceFormatter implements TextInputFormatter {
  constructor(readonly controller: _MentionTextEditingController) {}

  formatEditUpdate(oldValue: TextEditingValue, newValue: TextEditingValue): TextEditingValue {
    return this.controller.formatConfirmedMentionBackspace({ oldValue, defaultValue: newValue }) ?? newValue;
  }
}

export class _ConfirmedComposerMention {
  constructor(opts: { start: number; end: number; text: string }) {
    this.start = opts.start;
    this.end = opts.end;
    this.text = opts.text;
  }

  readonly start: number;
  readonly end: number;
  readonly text: string;

  shift(delta: number): _ConfirmedComposerMention {
    if (delta === 0) return this;
    return new _ConfirmedComposerMention({
      start: this.start + delta,
      end: this.end + delta,
      text: this.text,
    });
  }
}
