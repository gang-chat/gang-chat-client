// Port of `lib/src/settings/settings_page.dart`: the app settings page
// ("Control Room" layout — see docs/ui-design-system.md §8). The Dart part
// files are separate modules:
//   settings_components.tsx       search index, form groups and fields
//   settings_profile_widgets.tsx  delete-account dialog, field adornments
//   settings_audio_widgets.tsx    devices, volume / test panels, screen share
//   settings_music_playlists.tsx  MusicPlaylistsPanel
// The library-private colour aliases below are exported (underscore names)
// for those modules. They are only read at render time, which keeps the
// part modules' circular imports of this file safe.

import { useMemo, type CSSProperties, type ReactNode } from 'react';

import * as account_display from '@gang-chat/core/app/account_display';
import {
  AccountAvatarErrorTarget,
  AccountFormSaveTarget,
  AccountUpdateDraft,
  PasswordVisibilityField,
  accountAvatarActionCancelled,
  accountAvatarActionFailed,
  accountAvatarPendingUploadSucceeded,
  accountAvatarPreparationStarted,
  accountAvatarProfileUpdatedFromStickerSucceeded,
  accountAvatarUploadStarted,
  accountDeletionFailed,
  accountDeletionFinished,
  accountDeletionStarted,
  accountEmailPublicChanged,
  accountFormSaveCancelled,
  accountFormSaveFailed,
  accountFormSaveNoChanges,
  accountFormSaveStarted,
  accountFormSaveSucceeded,
  accountFormSaveValidationFailed,
  accountGenderChanged,
  accountPhoneNumberPublicChanged,
  accountPresetAvatarSelected,
  accountUpdateDraftFromForm,
  loginUsernameAvailabilityError,
  loginUsernameUpdateDraftFromForm,
  loginUsernameValidationError,
  normalizeAccountLanguage,
  passwordChangeDraftFromForm,
  passwordChangeFailed,
  passwordChangeStarted,
  passwordChangeSucceeded,
  passwordChangeValidationFailed,
  passwordVisibilityToggled,
  preferencesUpdateDraftFromForm,
  profileUpdateDraftFromForm,
  stickerAvatarUploadFilename,
  type AccountAvatarStatePatch,
  type AccountDeletionStatePatch,
  type AccountEditableFieldsPatch,
  type AccountFormSaveStatePatch,
  type AccountPresetAvatarSelectionPatch,
  type PasswordChangeStatePatch,
  type PasswordVisibilityPatch,
} from '@gang-chat/core/app/account_forms';
import {
  accountSessionsLoadCancelled,
  accountSessionsLoadFailed,
  accountSessionsLoadStarted,
  accountSessionsLoadSucceeded,
  type AccountSessionsLoadPatch,
} from '@gang-chat/core/app/account_sessions';
import {
  accountLoadCancelled,
  accountLoadFailed,
  accountLoadStarted,
  accountLoadSucceeded,
  type AccountLoadPatch,
} from '@gang-chat/core/app/account_state';
import {
  AppUpdatePlatform,
  shouldTerminateApplicationAfterInstallerLaunch,
  type AvailableAppUpdate,
} from '@gang-chat/core/app/app_update';
import { registerEmailValidationError } from '@gang-chat/core/app/auth_form';
import {
  audioInputTestTooltip,
  audioOutputTestTooltip,
  canStartAudioDeviceSelection,
} from '@gang-chat/core/app/audio_device_display';
import { AudioDeviceInfo, audioDeviceInfoId, audioDeviceInfoKind } from '@gang-chat/core/app/audio_device_info';
import {
  audioDeviceListApplied,
  audioDeviceListLoadFailed,
  audioDeviceListLoadStarted,
  audioDeviceSelectionFailed,
  audioDeviceSelectionStarted,
  audioDeviceSelectionSucceeded,
  audioInputDeviceSelectedEffects,
  audioInputLevelChanged,
  audioInputTestFailed,
  audioInputTestStarted,
  audioInputTestStopped,
  audioInputVolumeChanged,
  audioOutputDeviceSelectedEffects,
  audioOutputLevelChanged,
  audioOutputTestFailed,
  audioOutputTestStarted,
  audioOutputTestStopped,
  audioOutputVolumeChanged,
  audioStoredVolumesApplied,
  rememberedAudioVolume,
  restoredAudioVolume,
  type AudioDeviceListPatch,
  type AudioDeviceSelectionPatch,
  type AudioTestStatePatch,
  type AudioVolumePatch,
} from '@gang-chat/core/app/audio_device_state';
import {
  RestoredAudioDevices,
  defaultAudioVolume,
  storedAudioDeviceFrom,
  type AudioDeviceStore,
} from '@gang-chat/core/app/audio_device_store';
import { audioVolumePercentText, normalizedAudioVolume } from '@gang-chat/core/app/audio_levels';
import {
  CloseBehavior,
  closeBehaviorDescription,
  closeBehaviorFromStorageValue,
  closeBehaviorLabel,
  closeBehaviorStorageValue,
  defaultCloseBehavior,
  type CloseBehaviorStore,
} from '@gang-chat/core/app/close_behavior';
import type { EmailVerificationController } from '@gang-chat/core/app/email_verification_controller';
import { userFacingErrorMessage } from '@gang-chat/core/app/error_display';
import type { LanguagePreferenceStore } from '@gang-chat/core/app/language_preference';
import type { MusicTrackPreviewPlatformFactory } from '@gang-chat/core/app/music_track_preview';
import type { PasswordResetController } from '@gang-chat/core/app/password_reset_controller';
import { PersonalMusicPlaylistsController } from '@gang-chat/core/app/personal_music_playlists';
import {
  appVersionLabel,
  appVersionNumberLabel,
  boundEmailForFeedback,
  defaultAutoUpdatePromptEnabled,
  feedbackMailBody,
  feedbackMailSubject,
  gangChatClientReleaseTimestamp,
  gangChatClientVersion,
  gangChatSupportEmail,
  installTimeLabel,
  officialVersionTimeLabel,
  type AutoUpdatePromptStore,
} from '@gang-chat/core/app/settings_about';
import { SettingsController } from '@gang-chat/core/app/settings_controller';
import {
  SettingsSection,
  settingsSectionRefreshing,
  settingsSectionSelected,
  type SettingsSectionPatch,
} from '@gang-chat/core/app/settings_shell_state';
import {
  StickerActionKind,
  StickerManagementScope,
  canSelectVisibleStickers,
  canStartStickerPrimaryAction,
  canStartStickerSelectionAction,
  canUseStickerManagementControl,
  defaultStickerPackName,
  filteredManagedStickerItems,
  managedStickerItems,
  stickerActionCancelled,
  stickerActionErrorShown,
  stickerActionFailed,
  stickerActionNoticeShown,
  stickerActionStarted,
  stickerActionSucceeded,
  stickerAllVisibleSelected,
  stickerBulkDeleteConfirmBody,
  stickerDeleteDialogTitle,
  stickerDeletedNotice,
  stickerDownloadNotice,
  stickerFilterActive,
  stickerFilterApplied,
  stickerManagementBusy,
  stickerManagementCountText,
  stickerManagementModeToggled,
  stickerMoveNotice,
  stickerNoOrderChangeNotice,
  stickerPacksLoadFailed,
  stickerPacksLoadStarted,
  stickerPacksLoadSucceeded,
  stickerPinnedNotice,
  stickerRenameName,
  stickerSelectionNumbers,
  stickerSelectionToggled,
  stickerSingleDeleteConfirmBody,
  stickerUploadNotice,
  stickerVisibleSelectionButtonText,
  stickerVisibleSelectionToggled,
  type ManagedSticker,
  type StickerActionPatch,
  type StickerFilterDraft,
  type StickerPackLoadPatch,
  type StickerSelectionPatch,
} from '@gang-chat/core/app/sticker_management';
import * as sticker_ordering from '@gang-chat/core/app/sticker_ordering';
import {
  stickerNameFromFilename,
  stickerNoUploadableImagesMessage,
  stickerPackRequiresServerMessage,
  stickerPickerOpenFailureMessage,
  stickerUploadFilename,
  stickerUploadItemsFromFiles,
} from '@gang-chat/core/app/sticker_uploads';
import type { AppConfig } from '@gang-chat/core/config/app_config';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import { Timer, delay, unawaited } from '@gang-chat/core/lib/async';
import type { StreamSubscription } from '@gang-chat/core/lib/event_stream';
import { restoreStoredAudioDevices } from '@gang-chat/core/live/audio_device_restorer';
import { LiveAudioDeviceService } from '@gang-chat/core/live/audio_device_service';
import { AudioTestService, type AudioTestHandle } from '@gang-chat/core/live/audio_test_service';
import {
  androidScreenShareFrameRateCap,
  defaultScreenShareFrameRate,
  defaultScreenShareMaxHeight,
  desktopScreenShareFrameRateCap,
  normalizedScreenShareFrameRate,
  normalizedScreenShareMaxHeight,
} from '@gang-chat/core/live/screen_share_quality';
import { SystemAudioDevices } from '@gang-chat/core/live/system_audio_devices';
import type { GangApi, MusicTrackPreviewApi, PersonalMusicPlaylistApi } from '@gang-chat/core/protocol/api_client';
import type { CurrentUser, StickerPack, UserSession } from '@gang-chat/core/protocol/models';
import { StickerPackStore } from '@gang-chat/core/protocol/sticker_pack_store';
import { AndroidSystemService } from '@gang-chat/core/shell/android_system_service';
import { ClipboardService } from '@gang-chat/core/shell/clipboard_service';
import { DesktopWindowController } from '@gang-chat/core/shell/desktop_window_controller';
import { ExternalUriLauncher } from '@gang-chat/core/shell/external_uri_launcher';
import { FeedbackMailDraft, FeedbackMailService } from '@gang-chat/core/shell/feedback_mail_service';
import { FileSelectionService, FileTypeGroup, type SelectedFile } from '@gang-chat/core/shell/file_selection_service';
import { InstallInfoService } from '@gang-chat/core/shell/install_info_service';
import { LocalAudioDeviceStore } from '@gang-chat/core/shell/local_audio_device_store';
import { LocalAutoUpdatePromptStore } from '@gang-chat/core/shell/local_auto_update_prompt_store';
import { LocalCloseBehaviorStore } from '@gang-chat/core/shell/local_close_behavior_store';
import { LocalLanguagePreferenceStore } from '@gang-chat/core/shell/local_language_preference_store';
import { DefaultMusicTrackPreviewPlatformFactory } from '@gang-chat/core/shell/music_track_preview_service';
import {
  ReleaseDownloadCancellationToken,
  ReleaseDownloadCancelledException,
  ReleaseUpdateService,
  appUpdatePlatformForOperatingSystem,
} from '@gang-chat/core/shell/release_update_service';
import { SparkleUpdater } from '@gang-chat/core/shell/sparkle_updater';

import { Center, SizedBox, Text } from '../lib/layout';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { TextEditingController, TextInputType } from '../lib/text_editing';
import { ViewState, useViewState } from '../lib/view_state';
import {
  AppUpdatePage,
  sharedReleaseUpdateService,
  updateDownloadConfirmationBodyFor,
  updateDownloadsInBrowser,
} from '../shell/app_update_gate';
import {
  EmailVerificationInputAction,
  emailVerificationErrorMessage,
  showEmailVerificationDialog,
} from '../shell/email_verification_flow';
import { verifyPasswordResetEmail } from '../shell/password_reset_flow';
import { useAppConfig } from '../ui/app_config_scope';
import { AvatarCropDialog } from '../ui/avatar_crop_dialog';
import { AvatarPicker } from '../ui/avatar_picker';
import { Button, ButtonIcon, ButtonTone } from '../ui/button';
import {
  DialogFrame,
  FloatingNotice,
  FloatingNoticeEmitter,
  FloatingNoticeTone,
  ResponsiveDialogAction,
  ResponsiveDialogActionBar,
  floatingNoticeVisibleDuration,
  showFloatingErrorNotice,
  showFloatingSuccessNotice,
} from '../ui/feedback';
import { Icon, Icons, type IconData } from '../ui/icon';
import { Input } from '../ui/input';
import { showDialog, useRoute } from '../ui/overlay';
import { SettingsFixedHeaderCard, SettingsList, SettingsScaffold, SettingsTileNavigation } from '../ui/settings_scaffold';
import {
  StickerActionGrid,
  StickerActionGridEntry,
  StickerConfirmDialog,
  StickerFilterDialog,
  StickerGrid,
  StickerPreviewDialog,
  type StickerImagePreviewOpener,
} from '../ui/sticker_manager';
import { decodeStickerImageDimensions, stickerUploadSourcesFromSelectedFiles } from '../ui/sticker_upload_adapter';
import { UiColors, UiTypography } from '../ui/tokens';
import { _AudioControlPanel, _DeviceSection, _ScreenShareQualitySection } from './settings_audio_widgets';
import {
  _CopyableField,
  _LabeledTextField,
  _ReadOnlyLine,
  _SegmentOption,
  _SegmentedSetting,
  _SessionList,
  _SettingsGroup,
  _SettingsSearchResultRow,
  _SettingsSubPanel,
  _ToggleSetting,
  _settingsSectionSegments,
  settingsSearchMatches,
} from './settings_components';
import { MusicPlaylistsPanel } from './settings_music_playlists';
import {
  _DeleteAccountDialog,
  _EmailVerificationStatusIndicator,
  _SettingsEmptyState,
  _UsernameValidityIndicator,
} from './settings_profile_widgets';

export const _primaryDark = UiColors.background;
export const _primaryDarkLow = UiColors.surfaceLow;
export const _borderColor = UiColors.border;
export const _cyan = UiColors.accent;
export const _textPrimary = UiColors.text;
export const _textSecondary = UiColors.textSecondary;
export const _textMuted = UiColors.textMuted;
export const _danger = UiColors.danger;

/**
 * Dart's `StateError`: its `toString()` ("Bad state: …") is what
 * [userFacingErrorMessage] shows for the sticker-pack failures below.
 */
class StateError extends Error {
  override toString(): string {
    return `Bad state: ${this.message}`;
  }
}

export interface SettingsPageProps {
  isSubWindow?: boolean;
  audioDeviceStore?: AudioDeviceStore;
  audioDeviceService?: LiveAudioDeviceService;
  systemAudioDevices?: SystemAudioDevices | null;
  /** Kept for older tests/call sites that injected the macOS-only adapter. */
  systemDefaultAudioInput?: SystemAudioDevices | null;
  controller?: SettingsController | null;
  api?: GangApi | null;
  apiBaseUrl?: string;
  emailVerificationController?: EmailVerificationController | null;
  passwordResetController?: PasswordResetController | null;
  stickerPackStore?: StickerPackStore;
  clipboardService?: ClipboardService;
  fileSelectionService?: FileSelectionService;
  feedbackMailService?: FeedbackMailService;
  androidSystemService?: AndroidSystemService;
  autoUpdatePromptStore?: AutoUpdatePromptStore;
  installInfoService?: InstallInfoService;
  releaseUpdateService?: ReleaseUpdateService;
  sparkleUpdater?: SparkleUpdater;
  externalUriLauncher?: ExternalUriLauncher;

  /**
   * Platform whose installers the update check looks for; defaults to the
   * running operating system (tests pin it, like `AppUpdateGate` does).
   */
  updatePlatformOverride?: AppUpdatePlatform | null;
  windowController?: DesktopWindowController | null;
  closeBehaviorStore?: CloseBehaviorStore;
  languageStore?: LanguagePreferenceStore;
  initialSection?: SettingsSection;
  initialMusicPlaylistId?: string | null;
  initialAppUpdate?: AvailableAppUpdate | null;
  stickerImagePreviewOpener?: StickerImagePreviewOpener | null;
  appVersion?: string;
  onAppUpdateDownloadCancellationChanged?: ((token: ReleaseDownloadCancellationToken | null) => void) | null;
  currentUser?: CurrentUser | null;
  onUserUpdated?: ((user: CurrentUser) => void) | null;
  onMusicPlaylistsChanged?: (() => void) | null;
  onDeviceSelected?: ((kind: string, deviceId: string) => void) | null;
  onVolumeChanged?: ((kind: string, volume: number) => void) | null;

  /**
   * Fired when the user picks a screen-share resolution, so a live session can
   * re-scale immediately. The choice is also persisted to [audioDeviceStore].
   */
  onScreenShareMaxHeightChanged?: ((height: number) => void) | null;
  onScreenShareFrameRateChanged?: ((frameRate: number) => void) | null;
  onAccountDeleted?: (() => Promise<void>) | null;
  onClose?: (() => void) | null;
  musicTrackPreviewPlatformFactory?: MusicTrackPreviewPlatformFactory;
}

/** [SettingsPageProps] with the Dart constructor defaults filled in (Dart `widget`). */
export type _SettingsPageWidget = { [K in keyof SettingsPageProps]-?: Exclude<SettingsPageProps[K], undefined> };

interface _SettingsPageDefaultServices {
  audioDeviceStore: AudioDeviceStore;
  audioDeviceService: LiveAudioDeviceService;
  stickerPackStore: StickerPackStore;
  clipboardService: ClipboardService;
  fileSelectionService: FileSelectionService;
  feedbackMailService: FeedbackMailService;
  androidSystemService: AndroidSystemService;
  autoUpdatePromptStore: AutoUpdatePromptStore;
  installInfoService: InstallInfoService;
  releaseUpdateService: ReleaseUpdateService;
  sparkleUpdater: SparkleUpdater;
  externalUriLauncher: ExternalUriLauncher;
  closeBehaviorStore: CloseBehaviorStore;
  languageStore: LanguagePreferenceStore;
  musicTrackPreviewPlatformFactory: MusicTrackPreviewPlatformFactory;
}

// Dart's `const` default instances: one shared instance each, created on
// first use so importing this module touches no platform services.
let _defaultServicesInstance: _SettingsPageDefaultServices | null = null;

function _defaultServices(): _SettingsPageDefaultServices {
  return (_defaultServicesInstance ??= {
    audioDeviceStore: new LocalAudioDeviceStore(),
    audioDeviceService: new LiveAudioDeviceService(),
    stickerPackStore: new StickerPackStore(),
    clipboardService: new ClipboardService(),
    fileSelectionService: new FileSelectionService(),
    feedbackMailService: new FeedbackMailService(),
    androidSystemService: new AndroidSystemService(),
    autoUpdatePromptStore: new LocalAutoUpdatePromptStore(),
    installInfoService: new InstallInfoService(),
    // The gate and this page share one service: `isNativeUpdate` only knows
    // updates found through the same instance.
    releaseUpdateService: sharedReleaseUpdateService,
    sparkleUpdater: new SparkleUpdater(),
    externalUriLauncher: new ExternalUriLauncher(),
    closeBehaviorStore: new LocalCloseBehaviorStore(),
    languageStore: new LocalLanguagePreferenceStore(),
    musicTrackPreviewPlatformFactory: new DefaultMusicTrackPreviewPlatformFactory(),
  });
}

function _settingsPageWidget(props: SettingsPageProps): _SettingsPageWidget {
  const defaults = _defaultServices();
  return {
    isSubWindow: props.isSubWindow ?? false,
    audioDeviceStore: props.audioDeviceStore ?? defaults.audioDeviceStore,
    audioDeviceService: props.audioDeviceService ?? defaults.audioDeviceService,
    systemAudioDevices: props.systemAudioDevices ?? null,
    systemDefaultAudioInput: props.systemDefaultAudioInput ?? null,
    controller: props.controller ?? null,
    api: props.api ?? null,
    apiBaseUrl: props.apiBaseUrl ?? '',
    emailVerificationController: props.emailVerificationController ?? null,
    passwordResetController: props.passwordResetController ?? null,
    stickerPackStore: props.stickerPackStore ?? defaults.stickerPackStore,
    clipboardService: props.clipboardService ?? defaults.clipboardService,
    fileSelectionService: props.fileSelectionService ?? defaults.fileSelectionService,
    feedbackMailService: props.feedbackMailService ?? defaults.feedbackMailService,
    androidSystemService: props.androidSystemService ?? defaults.androidSystemService,
    autoUpdatePromptStore: props.autoUpdatePromptStore ?? defaults.autoUpdatePromptStore,
    installInfoService: props.installInfoService ?? defaults.installInfoService,
    releaseUpdateService: props.releaseUpdateService ?? defaults.releaseUpdateService,
    sparkleUpdater: props.sparkleUpdater ?? defaults.sparkleUpdater,
    externalUriLauncher: props.externalUriLauncher ?? defaults.externalUriLauncher,
    updatePlatformOverride: props.updatePlatformOverride ?? null,
    windowController: props.windowController ?? null,
    closeBehaviorStore: props.closeBehaviorStore ?? defaults.closeBehaviorStore,
    languageStore: props.languageStore ?? defaults.languageStore,
    initialSection: props.initialSection ?? SettingsSection.profile,
    initialMusicPlaylistId: props.initialMusicPlaylistId ?? null,
    initialAppUpdate: props.initialAppUpdate ?? null,
    stickerImagePreviewOpener: props.stickerImagePreviewOpener ?? null,
    appVersion: props.appVersion ?? gangChatClientVersion,
    onAppUpdateDownloadCancellationChanged: props.onAppUpdateDownloadCancellationChanged ?? null,
    currentUser: props.currentUser ?? null,
    onUserUpdated: props.onUserUpdated ?? null,
    onMusicPlaylistsChanged: props.onMusicPlaylistsChanged ?? null,
    onDeviceSelected: props.onDeviceSelected ?? null,
    onVolumeChanged: props.onVolumeChanged ?? null,
    onScreenShareMaxHeightChanged: props.onScreenShareMaxHeightChanged ?? null,
    onScreenShareFrameRateChanged: props.onScreenShareFrameRateChanged ?? null,
    onAccountDeleted: props.onAccountDeleted ?? null,
    onClose: props.onClose ?? null,
    musicTrackPreviewPlatformFactory: props.musicTrackPreviewPlatformFactory ?? defaults.musicTrackPreviewPlatformFactory,
  };
}

// Dart `api is PersonalMusicPlaylistApi` / `api is MusicTrackPreviewApi`.
function _isPersonalMusicPlaylistApi(api: GangApi | null): api is GangApi & PersonalMusicPlaylistApi {
  return (
    api != null &&
    'listPersonalMusicPlaylists' in api &&
    typeof (api as Partial<PersonalMusicPlaylistApi>).listPersonalMusicPlaylists === 'function'
  );
}

function _isMusicTrackPreviewApi(api: GangApi | null): api is GangApi & MusicTrackPreviewApi {
  return (
    api != null &&
    'downloadMusicTrackPreview' in api &&
    typeof (api as Partial<MusicTrackPreviewApi>).downloadMusicTrackPreview === 'function'
  );
}

const _avatarFileTypeGroups = [
  new FileTypeGroup({
    label: '图片',
    extensions: ['png', 'jpg', 'jpeg', 'webp'],
  }),
];

const _stickerFileTypeGroups = [
  new FileTypeGroup({
    label: '图片和 ZIP',
    extensions: ['png', 'jpg', 'jpeg', 'webp', 'gif', 'zip'],
  }),
];

export class _SettingsPageState extends ViewState<_SettingsPageWidget> {
  readonly _usernameController = new TextEditingController();
  readonly _displayNameController = new TextEditingController();
  readonly _bioController = new TextEditingController();
  readonly _emailController = new TextEditingController();
  readonly _phoneController = new TextEditingController();
  readonly _currentPasswordController = new TextEditingController();
  readonly _newPasswordController = new TextEditingController();
  readonly _confirmPasswordController = new TextEditingController();
  readonly _settingsSearchController = new TextEditingController();

  static readonly _usernameAvailabilityDebounceDuration = 350;

  _section: SettingsSection = SettingsSection.profile;
  _user: CurrentUser | null = null;
  _sessions: UserSession[] = [];
  _gender = 'secret';
  _defaultAvatarKey = 'blue-3';
  _emailPublic = false;
  _phonePublic = false;
  _loadingAccount = false;
  _loadingSessions = false;
  _savingAccount = false;
  _savingProfile = false;
  _uploadingAvatar = false;
  _clearUploadedAvatar = false;
  _pendingAvatarAssetId: string | null = null;
  _pendingAvatarUrl: string | null = null;
  _changingPassword = false;
  _deletingAccount = false;
  _changingAccountSuspension = false;
  _stickerPacks: StickerPack[] = [];
  _selectedStickerIds: string[] = [];
  readonly _stickerOrderDrafts = new Map<string, string[]>();
  _managingStickers = false;
  _loadingStickers = false;
  _uploadingStickers = false;
  _deletingStickers = false;
  _savingStickerOrder = false;
  _downloadingStickers = false;
  _stickerFilterKeyword = '';
  _stickerFilterMimeType = '';
  _stickerError: string | null = null;
  _playlistReloadToken = 0;
  _loadingPlaylists = false;
  _obscureCurrentPassword = true;
  _obscureNewPassword = true;
  _obscureConfirmPassword = true;
  _accountError: string | null = null;
  _securityError: string | null = null;
  _aboutError: string | null = null;
  _notice: string | null = null;
  _loadingAbout = false;
  _checkingAppVersion = false;
  _availableAppUpdate: AvailableAppUpdate | null = null;
  _downloadingAppUpdate = false;
  _updateDownloadedBytes = 0;
  _updateDownloadTotalBytes: number | null = null;
  _updateDownloadError: string | null = null;
  _openingFeedbackMail = false;
  _autoPromptUpdates = defaultAutoUpdatePromptEnabled;
  _androidNotificationPreferenceEnabled = true;
  _androidNotificationsAvailable = false;
  _savingAndroidNotifications = false;
  _lastUpdateDate = installTimeLabel(null);
  _usernameAvailabilityDebounce: Timer | null = null;
  _usernameAvailabilityRequestId = 0;
  _usernameAvailabilityQuery: string | null = null;
  _checkingUsernameAvailability = false;
  _checkingEmailAvailability = false;
  _emailVerificationRequestId = 0;
  _verifiedEmail: string | null = null;
  _emailVerificationToken: string | null = null;
  _verifyingPasswordReset = false;
  _usernameAvailabilityError: string | null = null;
  _floatingNoticeSerial = 0;
  readonly _floatingNoticeEventKeys = new Map<string, number>();

  _deviceSubscription: StreamSubscription | null = null;
  _audioInputs: AudioDeviceInfo[] = [];
  _audioOutputs: AudioDeviceInfo[] = [];
  _selectedInput: AudioDeviceInfo | null = null;
  _selectedOutput: AudioDeviceInfo | null = null;
  _busyDeviceId: string | null = null;
  _inputVolume = defaultAudioVolume;
  _outputVolume = defaultAudioVolume;
  _lastOutputVolumeBeforeMute = defaultAudioVolume;
  _inputLevel = 0.0;
  _outputLevel = 0.0;
  _testingInput = false;
  _testingOutput = false;
  _voiceInitialized = false;
  _screenShareMaxHeight = defaultScreenShareMaxHeight;
  _screenShareFrameRate = defaultScreenShareFrameRate;
  _error: string | null = null;
  _loading = false;
  _language = 'zh-Hans';
  _closeBehavior: CloseBehavior = defaultCloseBehavior;
  _loadingCloseBehavior = false;
  _savingCloseBehavior = false;
  _closeBehaviorError: string | null = null;
  readonly _audioTestService = new AudioTestService();
  _inputTest: AudioTestHandle | null = null;
  _outputTest: AudioTestHandle | null = null;
  _systemAudioDevices: SystemAudioDevices | null = null;
  _ownedWindowController: DesktopWindowController | null = null;
  _systemDefaultInputSubscription: StreamSubscription | null = null;
  _systemDefaultOutputSubscription: StreamSubscription | null = null;
  _systemDefaultInputId: string | null = null;
  _systemDefaultOutputId: string | null = null;

  /** `AppConfigScope.of(context)`; refreshed on every build. */
  _appConfig!: AppConfig;
  /** `Navigator.of(context).pop()`; refreshed on every build. */
  _navigatorPop: () => void = () => {};

  constructor(widget: _SettingsPageWidget) {
    super();
    // The first build runs before initState (see useViewState), so the parts
    // of Dart's initState that feed the first frame run here.
    this.props = widget;
    this._user = widget.currentUser;
    this._syncUserFields(widget.currentUser);
    this._section = widget.initialAppUpdate == null ? widget.initialSection : SettingsSection.about;
    this._availableAppUpdate = widget.initialAppUpdate;
  }

  get _systemAudio(): SystemAudioDevices {
    return (this._systemAudioDevices ??=
      this.widget.systemAudioDevices ?? this.widget.systemDefaultAudioInput ?? new SystemAudioDevices());
  }

  get _settingsController(): SettingsController {
    const injected = this.widget.controller;
    if (injected != null) return injected;
    return new SettingsController({
      api: this.widget.api,
      apiBaseUrl: this.widget.apiBaseUrl,
      stickerPackStore: this.widget.stickerPackStore,
    });
  }

  get _isManagingUser(): boolean {
    return this._settingsController.isManagingUser;
  }

  get _musicPlaylistsController(): PersonalMusicPlaylistsController {
    const api = this.widget.api;
    return new PersonalMusicPlaylistsController(!this._isManagingUser && _isPersonalMusicPlaylistApi(api) ? api : null, {
      onChanged: this.widget.onMusicPlaylistsChanged,
    });
  }

  get _accountSuspended(): boolean {
    return this._user?.status?.trim().toLowerCase() === 'suspended';
  }

  get _pageTitle(): string {
    if (!this._isManagingUser) return '设置';
    const displayName = this._user?.displayName.trim() ?? '';
    return displayName.length === 0 ? '用户设置' : `${displayName} 的用户设置`;
  }

  get _windowController(): DesktopWindowController {
    return this.widget.windowController ?? (this._ownedWindowController ??= new DesktopWindowController());
  }

  override initState(): void {
    if (this.widget.androidSystemService.isSupported) {
      // PORT: WidgetsBinding.addObserver → the page's visibility; a hidden →
      // visible change stands in for AppLifecycleState.resumed.
      document.addEventListener('visibilitychange', this._onVisibilityChange);
    }
    unawaited(this._loadCloseBehavior());
    unawaited(this._loadAndroidNotificationSettings());
    unawaited(this._loadAutoUpdatePrompt());
    unawaited(this._loadInstallDate());
    unawaited(this._loadAccount());
  }

  override didUpdateProps(oldWidget: _SettingsPageWidget): void {
    // PORT: didUpdateWidget runs before Flutter's next build; here it runs
    // after the render, so the field updates go through setState.
    const userChanged = oldWidget.currentUser !== this.widget.currentUser;
    const appUpdateChanged =
      oldWidget.initialAppUpdate !== this.widget.initialAppUpdate && this.widget.initialAppUpdate != null;
    if (userChanged || appUpdateChanged) {
      this.setState(() => {
        if (userChanged) {
          this._user = this.widget.currentUser;
          this._syncUserFields(this.widget.currentUser);
        }
        if (appUpdateChanged) {
          this._section = SettingsSection.about;
          this._availableAppUpdate = this.widget.initialAppUpdate;
          this._resetUpdateDownloadState();
        }
      });
    }
    if (oldWidget.closeBehaviorStore !== this.widget.closeBehaviorStore) {
      unawaited(this._loadCloseBehavior());
    }
    if (oldWidget.autoUpdatePromptStore !== this.widget.autoUpdatePromptStore) {
      unawaited(this._loadAutoUpdatePrompt());
    }
    if (oldWidget.installInfoService !== this.widget.installInfoService) {
      unawaited(this._loadInstallDate());
    }
  }

  override dispose(): void {
    this._settingsSearchController.dispose();
    if (this.widget.androidSystemService.isSupported) {
      document.removeEventListener('visibilitychange', this._onVisibilityChange);
    }
    unawaited(this._stopInputTest({ updateState: false }));
    unawaited(this._stopOutputTest({ updateState: false }));
    this._usernameAvailabilityDebounce?.cancel();
    this._deviceSubscription?.cancel();
    this._systemDefaultInputSubscription?.cancel();
    this._systemDefaultOutputSubscription?.cancel();
    unawaited(this._systemAudioDevices?.dispose());
    this._usernameController.dispose();
    this._displayNameController.dispose();
    this._bioController.dispose();
    this._emailController.dispose();
    this._phoneController.dispose();
    this._currentPasswordController.dispose();
    this._newPasswordController.dispose();
    this._confirmPasswordController.dispose();
  }

  readonly _onVisibilityChange = (): void => {
    if (document.visibilityState !== 'visible') return;
    this.didChangeAppLifecycleState();
  };

  /** `didChangeAppLifecycleState(AppLifecycleState.resumed)`. */
  didChangeAppLifecycleState(): void {
    if (this.widget.androidSystemService.isSupported) {
      unawaited(this._loadAndroidNotificationSettings());
    }
  }

  _syncUserFields(user: CurrentUser | null): void {
    if (user == null) return;
    this.widget.passwordResetController?.invalidateAuthorizationIfEmailChanged(user.email);
    this._usernameController.text = user.username;
    this._displayNameController.text = user.displayName;
    this._bioController.text = user.bio;
    this._emailController.text = user.email ?? '';
    this._emailVerificationRequestId++;
    this._checkingEmailAvailability = false;
    this._verifiedEmail = null;
    this._emailVerificationToken = null;
    this._phoneController.text = user.phoneNumber ?? '';
    this._gender = account_display.normalizeGender(user.gender);
    this._defaultAvatarKey = user.defaultAvatarKey;
    this._emailPublic = user.emailPublic;
    this._phonePublic = user.phoneNumberPublic;
    this._language = normalizeAccountLanguage(user.language);
    this._clearUploadedAvatar = false;
    this._pendingAvatarAssetId = null;
    this._pendingAvatarUrl = null;
    this._resetUsernameAvailabilityCheck();
  }

  get _normalizedAccountEmail(): string {
    return this._emailController.text.trim().toLowerCase();
  }

  get _accountEmailMatchesUser(): boolean {
    const user = this._user;
    return user != null && this._normalizedAccountEmail === (user.email ?? '').trim().toLowerCase();
  }

  get _accountEmailVerified(): boolean {
    if (this._normalizedAccountEmail.length === 0) return false;
    if (this._accountEmailMatchesUser && (this._user?.emailVerified ?? false)) {
      return true;
    }
    return this._verifiedEmail === this._normalizedAccountEmail && this._emailVerificationToken != null;
  }

  _resetUsernameAvailabilityCheck(): void {
    this._usernameAvailabilityDebounce?.cancel();
    this._usernameAvailabilityRequestId++;
    this._usernameAvailabilityQuery = null;
    this._checkingUsernameAvailability = false;
    this._usernameAvailabilityError = null;
  }

  _markFloatingNoticeEvent(channel: string, message: string | null): void {
    if (message == null || message.trim().length === 0) return;
    this._floatingNoticeEventKeys.set(channel, ++this._floatingNoticeSerial);
  }

  _floatingNoticeEventKey(channel: string): number | null {
    return this._floatingNoticeEventKeys.get(channel) ?? null;
  }

  async _loadAccount(): Promise<void> {
    this.setState(() => this._applyAccountLoadPatch(accountLoadStarted({ user: this._user })));
    try {
      const user = await this._settingsController.loadAccount();
      if (!this.mounted) return;
      if (user == null) {
        this.setState(() =>
          this._applyAccountLoadPatch(accountLoadCancelled({ user: this._user, accountError: this._accountError })),
        );
        return;
      }
      this.setState(() => {
        this._applyAccountLoadPatch(accountLoadSucceeded({ user }));
        this._syncUserFields(user);
      });
      this.widget.onUserUpdated?.(user);
      if (!this._isManagingUser) {
        unawaited(this._rememberLanguagePreference(user.language));
      }
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() => this._applyAccountLoadPatch(accountLoadFailed({ user: this._user, failure: e })));
    }
  }

  _onLoginUsernameChanged(value: string): void {
    const user = this._user;
    const username = value.trim();
    this._usernameAvailabilityDebounce?.cancel();
    const requestId = ++this._usernameAvailabilityRequestId;
    const shouldCheck =
      user != null &&
      username !== user.username &&
      loginUsernameValidationError(username) == null &&
      this._settingsController.hasApi;
    if (!shouldCheck) {
      this.setState(() => {
        this._usernameAvailabilityQuery = null;
        this._checkingUsernameAvailability = false;
        this._usernameAvailabilityError = null;
      });
      return;
    }

    this.setState(() => {
      this._usernameAvailabilityQuery = username;
      this._checkingUsernameAvailability = true;
      this._usernameAvailabilityError = null;
    });
    this._usernameAvailabilityDebounce = new Timer(_SettingsPageState._usernameAvailabilityDebounceDuration, () =>
      unawaited(
        this._checkLoginUsernameAvailability({
          user: user!,
          username,
          requestId,
        }),
      ),
    );
  }

  _onAccountEmailChanged(value: string): void {
    const normalized = value.trim().toLowerCase();
    this._emailVerificationRequestId++;
    this.setState(() => {
      this._checkingEmailAvailability = false;
      if (this._verifiedEmail !== normalized) {
        this._verifiedEmail = null;
        this._emailVerificationToken = null;
      }
    });
  }

  async _showAccountEmailVerification(): Promise<void> {
    if (this._savingAccount || this._checkingEmailAvailability) return;
    const email = this._emailController.text.trim();
    const validationError = registerEmailValidationError(email);
    if (validationError != null) {
      showFloatingErrorNotice(validationError);
      return;
    }
    const controller = this.widget.emailVerificationController;
    if (controller == null) {
      showFloatingErrorNotice('暂时无法使用邮箱验证功能');
      return;
    }

    const requestId = ++this._emailVerificationRequestId;
    this.setState(() => {
      this._checkingEmailAvailability = true;
    });
    try {
      if (!this._accountEmailMatchesUser) {
        const available = await controller.isEmailAvailable(email);
        if (!this.mounted || requestId !== this._emailVerificationRequestId) return;
        if (!available) {
          this.setState(() => {
            this._checkingEmailAvailability = false;
          });
          showFloatingErrorNotice('该邮箱已被其他用户使用');
          return;
        }
      }

      const challenge = this._isManagingUser
        ? await controller.inspectOrStart(email)
        : await controller.inspectOrStartForCurrentUser(email);
      if (!this.mounted || requestId !== this._emailVerificationRequestId) return;
      this.setState(() => {
        this._checkingEmailAvailability = false;
      });
      const verificationToken = await showEmailVerificationDialog({
        email,
        challenge,
        controller,
      });
      if (
        !this.mounted ||
        verificationToken == null ||
        requestId !== this._emailVerificationRequestId ||
        this._emailController.text.trim() !== email
      ) {
        return;
      }
      this.setState(() => {
        this._verifiedEmail = email.toLowerCase();
        this._emailVerificationToken = verificationToken;
      });
    } catch (error) {
      if (!this.mounted || requestId !== this._emailVerificationRequestId) return;
      this.setState(() => {
        this._checkingEmailAvailability = false;
      });
      showFloatingErrorNotice(emailVerificationErrorMessage(error));
    }
  }

  async _checkLoginUsernameAvailability(opts: { user: CurrentUser; username: string; requestId: number }): Promise<void> {
    const { user, username, requestId } = opts;
    try {
      const users = await this._settingsController.searchUsers({ query: username, limit: 8 });
      if (!this.mounted || requestId !== this._usernameAvailabilityRequestId) return;
      const error = loginUsernameAvailabilityError({
        user,
        username,
        candidates: users ?? [],
      });
      this.setState(() => {
        this._usernameAvailabilityQuery = username;
        this._checkingUsernameAvailability = false;
        this._usernameAvailabilityError = error;
      });
    } catch {
      if (!this.mounted || requestId !== this._usernameAvailabilityRequestId) return;
      this.setState(() => {
        this._usernameAvailabilityQuery = username;
        this._checkingUsernameAvailability = false;
        this._usernameAvailabilityError = '暂时无法检测登录用户名是否重复';
      });
    }
  }

  async _ensureLoginUsernameAvailable(opts: { user: CurrentUser; username: string }): Promise<string | null> {
    const { user, username } = opts;
    const normalizedUsername = username.trim();
    if (this._usernameAvailabilityQuery === normalizedUsername && !this._checkingUsernameAvailability) {
      return this._usernameAvailabilityError;
    }

    this._usernameAvailabilityDebounce?.cancel();
    const requestId = ++this._usernameAvailabilityRequestId;
    this.setState(() => {
      this._usernameAvailabilityQuery = normalizedUsername;
      this._checkingUsernameAvailability = true;
      this._usernameAvailabilityError = null;
    });
    try {
      const users = await this._settingsController.searchUsers({ query: normalizedUsername, limit: 8 });
      if (!this.mounted || requestId !== this._usernameAvailabilityRequestId) {
        return null;
      }
      const error = loginUsernameAvailabilityError({
        user,
        username: normalizedUsername,
        candidates: users ?? [],
      });
      this.setState(() => {
        this._checkingUsernameAvailability = false;
        this._usernameAvailabilityError = error;
      });
      return error;
    } catch {
      const error = '暂时无法检测登录用户名是否重复';
      if (!this.mounted || requestId !== this._usernameAvailabilityRequestId) {
        return error;
      }
      this.setState(() => {
        this._checkingUsernameAvailability = false;
        this._usernameAvailabilityError = error;
      });
      return error;
    }
  }

  async _loadSessions(): Promise<void> {
    this.setState(() => this._applyAccountSessionsLoadPatch(accountSessionsLoadStarted({ sessions: this._sessions })));
    try {
      const sessions = await this._settingsController.loadSessions();
      if (!this.mounted) return;
      if (sessions == null) {
        this.setState(() =>
          this._applyAccountSessionsLoadPatch(
            accountSessionsLoadCancelled({
              sessions: this._sessions,
              securityError: this._securityError,
            }),
          ),
        );
        return;
      }
      this.setState(() => this._applyAccountSessionsLoadPatch(accountSessionsLoadSucceeded({ sessions })));
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyAccountSessionsLoadPatch(accountSessionsLoadFailed({ sessions: this._sessions, failure: e })),
      );
    }
  }

  async _loadCloseBehavior(): Promise<void> {
    if (this._isManagingUser) return;
    if (this._loadingCloseBehavior) return;
    this.setState(() => {
      this._loadingCloseBehavior = true;
      this._closeBehaviorError = null;
    });
    try {
      const behavior = await this.widget.closeBehaviorStore.read();
      if (!this.mounted) return;
      this.setState(() => {
        this._closeBehavior = behavior;
        this._loadingCloseBehavior = false;
      });
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._loadingCloseBehavior = false;
        this._closeBehaviorError = userFacingErrorMessage(error, { fallback: '读取关闭方式失败' });
      });
    }
  }

  async _loadAutoUpdatePrompt(): Promise<void> {
    if (this._isManagingUser) return;
    try {
      const enabled = await this.widget.autoUpdatePromptStore.read();
      if (!this.mounted) return;
      this.setState(() => {
        this._autoPromptUpdates = enabled;
      });
    } catch {
      if (!this.mounted) return;
      this.setState(() => {
        this._aboutError = '读取自动提示更新失败';
        this._markFloatingNoticeEvent('aboutError', this._aboutError);
      });
    }
  }

  async _loadAndroidNotificationSettings(): Promise<void> {
    if (!this.widget.androidSystemService.isSupported || this._isManagingUser) return;
    try {
      const values = await Promise.all([
        this.widget.androidSystemService.notificationPreferenceEnabled(),
        this.widget.androidSystemService.notificationsEnabled(),
      ]);
      if (!this.mounted) return;
      this.setState(() => {
        this._androidNotificationPreferenceEnabled = values[0];
        this._androidNotificationsAvailable = values[1];
      });
    } catch {
      // Ignored, like the Dart `catch (_) {}`.
    }
  }

  async _setAndroidNotificationsEnabled(enabled: boolean): Promise<void> {
    if (this._savingAndroidNotifications) return;
    this.setState(() => {
      this._savingAndroidNotifications = true;
    });
    try {
      await this.widget.androidSystemService.setNotificationPreferenceEnabled(enabled);
      let available = false;
      if (enabled) {
        available = await this.widget.androidSystemService.requestNotificationPermission();
      }
      if (!this.mounted) return;
      this.setState(() => {
        this._androidNotificationPreferenceEnabled = enabled;
        this._androidNotificationsAvailable = available;
      });
      const registration = await this.widget.androidSystemService.pushRegistration();
      const api = this.widget.api;
      if (registration != null && api != null) {
        await api.upsertPushDevice({
          provider: registration.provider,
          installationId: registration.installationId,
          token: registration.token,
          enabled,
        });
      }
    } catch (error) {
      if (!this.mounted) return;
      showFloatingErrorNotice(userFacingErrorMessage(error, { fallback: '修改系统通知失败' }));
    } finally {
      if (this.mounted) {
        this.setState(() => {
          this._savingAndroidNotifications = false;
        });
      }
    }
  }

  async _openAndroidNotificationSettings(): Promise<void> {
    try {
      await this.widget.androidSystemService.openNotificationSettings();
    } catch (error) {
      if (!this.mounted) return;
      showFloatingErrorNotice(userFacingErrorMessage(error, { fallback: '无法打开系统通知设置' }));
    }
  }

  async _loadInstallDate(): Promise<void> {
    let installedAt: string | null = null;
    try {
      installedAt = await this.widget.installInfoService.readInstalledAt();
    } catch {
      // Falls back to the release time below.
    }
    if (!this.mounted) return;
    this.setState(() => {
      this._lastUpdateDate = installTimeLabel(installedAt);
    });
  }

  async _setAutoUpdatePrompt(value: boolean): Promise<void> {
    if (this._isManagingUser) return;
    if (this._autoPromptUpdates === value) return;
    const previous = this._autoPromptUpdates;
    this.setState(() => {
      this._autoPromptUpdates = value;
      this._aboutError = null;
      this._notice = null;
    });
    try {
      await this.widget.autoUpdatePromptStore.write(value);
      if (!this.mounted) return;
      this.setState(() => {
        this._notice = '自动提示更新已保存';
        this._markFloatingNoticeEvent('notice', this._notice);
      });
    } catch {
      if (!this.mounted) return;
      this.setState(() => {
        this._autoPromptUpdates = previous;
        this._aboutError = '自动提示更新保存失败';
        this._markFloatingNoticeEvent('aboutError', this._aboutError);
      });
    }
  }

  async _rememberLanguagePreference(language: string): Promise<void> {
    try {
      await this.widget.languageStore.write(language);
    } catch {
      // A local language-cache failure should not block server-backed settings.
    }
  }

  _setCloseBehavior(value: string): void {
    this.setState(() => {
      this._closeBehavior = closeBehaviorFromStorageValue(value);
      this._closeBehaviorError = null;
    });
  }

  async _savePreferences(): Promise<void> {
    if (this._savingAccount || this._savingCloseBehavior) return;
    this.setState(() => {
      this._savingCloseBehavior = true;
      this._closeBehaviorError = null;
      this._notice = null;
    });
    if (!this._isManagingUser) {
      try {
        await this.widget.closeBehaviorStore.write(this._closeBehavior);
        if (!this.mounted) return;
        this.setState(() => {
          this._savingCloseBehavior = false;
        });
      } catch (error) {
        if (!this.mounted) return;
        this.setState(() => {
          this._savingCloseBehavior = false;
          this._closeBehaviorError = userFacingErrorMessage(error, { fallback: '保存关闭方式失败' });
          this._markFloatingNoticeEvent('closeBehaviorError', this._closeBehaviorError);
        });
        return;
      }
    } else {
      this.setState(() => {
        this._savingCloseBehavior = false;
      });
    }

    const user = this._user;
    if (!this._settingsController.hasApi || user == null) {
      if (!this.mounted) return;
      this.setState(() => {
        this._notice = '偏好设置已保存';
        this._markFloatingNoticeEvent('notice', this._notice);
      });
      return;
    }

    const draft = preferencesUpdateDraftFromForm({ user, language: this._language });
    if (draft.error == null && draft.noChanges) {
      if (!this._isManagingUser) {
        await this._rememberLanguagePreference(this._language);
      }
      if (!this.mounted) return;
      this.setState(() => {
        this._notice = '偏好设置已保存';
        this._markFloatingNoticeEvent('notice', this._notice);
      });
      return;
    }
    await this._saveAccount({ target: AccountFormSaveTarget.preferences });
  }

  async _ensureStickersLoaded(opts: { forceReload?: boolean } = {}): Promise<void> {
    const { forceReload = false } = opts;
    if (this._loadingStickers) return;
    if (!forceReload && this._stickerPacks.length > 0) return;
    await this._loadStickers({ forceReload });
  }

  async _loadStickers(opts: { forceReload?: boolean } = {}): Promise<void> {
    const { forceReload = false } = opts;
    this.setState(() =>
      this._applyStickerPackLoadPatch(
        stickerPacksLoadStarted({
          packs: this._stickerPacks,
          selectedStickerIds: this._selectedStickerIds,
        }),
      ),
    );
    try {
      const result = await this._settingsController.loadPersonalStickerPacks({
        userId: this._stickerCacheUserId,
        forceReload,
      });
      if (!this.mounted) return;
      const packs = result?.packs ?? null;
      this.setState(() => {
        this._applyStickerPackLoadPatch(
          stickerPacksLoadSucceeded({
            packs: packs ?? this._stickerPacks,
            selectedStickerIds: this._selectedStickerIds,
          }),
        );
        if (packs != null) this._syncStickerOrderDrafts(packs);
      });
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyStickerPackLoadPatch(
          stickerPacksLoadFailed({
            packs: this._stickerPacks,
            selectedStickerIds: this._selectedStickerIds,
            failure: e,
          }),
        ),
      );
    }
  }

  get _stickerCacheUserId(): string {
    return this._user?.id ?? this.widget.currentUser?.id ?? '';
  }

  _applyStickerPackLoadPatch(patch: StickerPackLoadPatch): void {
    this._stickerPacks = patch.packs;
    this._selectedStickerIds = patch.selectedStickerIds;
    this._loadingStickers = patch.loading;
    this._stickerError = patch.error;
    this._markFloatingNoticeEvent('stickerError', this._stickerError);
  }

  _applyStickerSelectionPatch(patch: StickerSelectionPatch): void {
    this._managingStickers = patch.managing;
    this._stickerFilterKeyword = patch.filterKeyword;
    this._stickerFilterMimeType = patch.filterMimeType;
    this._selectedStickerIds = patch.selectedStickerIds;
  }

  _applyStickerActionPatch(patch: StickerActionPatch): void {
    this._uploadingStickers = patch.uploading;
    this._deletingStickers = patch.deleting;
    this._savingStickerOrder = patch.savingOrder;
    this._downloadingStickers = patch.downloading;
    this._selectedStickerIds = patch.selectedStickerIds;
    this._stickerError = patch.error;
    this._notice = patch.notice;
    this._markFloatingNoticeEvent('stickerError', this._stickerError);
    this._markFloatingNoticeEvent('notice', this._notice);
  }

  _applyAccountSessionsLoadPatch(patch: AccountSessionsLoadPatch): void {
    this._sessions = patch.sessions;
    this._loadingSessions = patch.loading;
    this._securityError = patch.securityError;
    this._markFloatingNoticeEvent('securityError', this._securityError);
  }

  _applyAccountLoadPatch(patch: AccountLoadPatch): void {
    this._user = patch.user;
    this._loadingAccount = patch.loading;
    this._accountError = patch.accountError;
    this._markFloatingNoticeEvent('accountError', this._accountError);
  }

  _applyAudioDeviceListPatch(patch: AudioDeviceListPatch<AudioDeviceInfo>): void {
    this._audioInputs = patch.inputs;
    this._audioOutputs = patch.outputs;
    this._selectedInput = patch.selectedInput;
    this._selectedOutput = patch.selectedOutput;
    this._loading = patch.loading;
    this._error = patch.error;
    this._markFloatingNoticeEvent('audioError', this._error);
  }

  _applyAudioDeviceSelectionPatch(patch: AudioDeviceSelectionPatch<AudioDeviceInfo>): void {
    this._selectedInput = patch.selectedInput;
    this._selectedOutput = patch.selectedOutput;
    this._busyDeviceId = patch.busyDeviceId;
    this._error = patch.error;
    this._markFloatingNoticeEvent('audioError', this._error);
  }

  _applyAudioVolumePatch(patch: AudioVolumePatch): void {
    this._inputVolume = patch.inputVolume;
    this._outputVolume = patch.outputVolume;
  }

  _rememberOutputVolume(volume: number): void {
    this._lastOutputVolumeBeforeMute = rememberedAudioVolume(volume);
  }

  _restoredOutputVolume(): number {
    return restoredAudioVolume(this._lastOutputVolumeBeforeMute);
  }

  _applyAudioTestStatePatch(patch: AudioTestStatePatch): void {
    this._testingInput = patch.testingInput;
    this._testingOutput = patch.testingOutput;
    this._inputLevel = patch.inputLevel;
    this._outputLevel = patch.outputLevel;
    this._error = patch.error;
    this._markFloatingNoticeEvent('audioError', this._error);
  }

  _applyAccountFormSaveStatePatch(patch: AccountFormSaveStatePatch): void {
    this._savingAccount = patch.savingAccount;
    this._savingProfile = patch.savingProfile;
    this._accountError = patch.accountError;
    this._notice = patch.notice;
    this._markFloatingNoticeEvent('accountError', this._accountError);
    this._markFloatingNoticeEvent('notice', this._notice);
  }

  _applyAccountEditableFieldsPatch(patch: AccountEditableFieldsPatch): void {
    this._gender = patch.gender;
    this._emailPublic = patch.emailPublic;
    this._phonePublic = patch.phoneNumberPublic;
  }

  _applyPasswordChangeStatePatch(patch: PasswordChangeStatePatch): void {
    this._changingPassword = patch.changingPassword;
    this._securityError = patch.securityError;
    this._notice = patch.notice;
    this._markFloatingNoticeEvent('securityError', this._securityError);
    this._markFloatingNoticeEvent('notice', this._notice);
  }

  _applyPasswordVisibilityPatch(patch: PasswordVisibilityPatch): void {
    this._obscureCurrentPassword = patch.obscureCurrentPassword;
    this._obscureNewPassword = patch.obscureNewPassword;
    this._obscureConfirmPassword = patch.obscureConfirmPassword;
  }

  _applyAccountDeletionStatePatch(patch: AccountDeletionStatePatch): void {
    this._deletingAccount = patch.deletingAccount;
    this._securityError = patch.securityError;
    this._notice = patch.notice;
    this._markFloatingNoticeEvent('securityError', this._securityError);
    this._markFloatingNoticeEvent('notice', this._notice);
  }

  _applyAccountAvatarStatePatch(patch: AccountAvatarStatePatch): void {
    this._pendingAvatarAssetId = patch.pendingAvatarAssetId;
    this._pendingAvatarUrl = patch.pendingAvatarUrl;
    this._clearUploadedAvatar = patch.clearUploadedAvatar;
    this._uploadingAvatar = patch.uploadingAvatar;
    this._accountError = patch.accountError;
    this._stickerError = patch.stickerError;
    this._notice = patch.notice;
    this._markFloatingNoticeEvent('accountError', this._accountError);
    this._markFloatingNoticeEvent('stickerError', this._stickerError);
    this._markFloatingNoticeEvent('notice', this._notice);
  }

  _applyAccountPresetAvatarSelectionPatch(patch: AccountPresetAvatarSelectionPatch): void {
    this._defaultAvatarKey = patch.defaultAvatarKey;
    this._pendingAvatarAssetId = patch.pendingAvatarAssetId;
    this._pendingAvatarUrl = patch.pendingAvatarUrl;
    this._clearUploadedAvatar = patch.clearUploadedAvatar;
    this._notice = patch.notice;
    this._markFloatingNoticeEvent('notice', this._notice);
  }

  _applySettingsSectionPatch(patch: SettingsSectionPatch): void {
    this._section = patch.section;
    this._notice = patch.notice;
    this._markFloatingNoticeEvent('notice', this._notice);
  }

  _floatingNotices(): FloatingNotice[] {
    const notices: FloatingNotice[] = [];

    const add = (
      message: string | null,
      tone: FloatingNoticeTone,
      opts: { duration?: number | null; channel: string },
    ): void => {
      const { duration = floatingNoticeVisibleDuration, channel } = opts;
      if (message == null || message.trim().length === 0) return;
      notices.push(
        new FloatingNotice({
          message,
          tone,
          duration,
          eventKey: this._floatingNoticeEventKey(channel),
        }),
      );
    };

    add(this._notice, FloatingNoticeTone.success, { channel: 'notice' });
    add(this._stickerError, FloatingNoticeTone.error, { duration: null, channel: 'stickerError' });
    add(this._accountError, FloatingNoticeTone.error, { duration: null, channel: 'accountError' });
    add(this._securityError, FloatingNoticeTone.error, { duration: null, channel: 'securityError' });
    add(this._aboutError, FloatingNoticeTone.error, { duration: null, channel: 'aboutError' });
    add(this._closeBehaviorError, FloatingNoticeTone.error, { duration: null, channel: 'closeBehaviorError' });
    add(this._updateDownloadError, FloatingNoticeTone.error, { duration: null, channel: 'updateDownloadError' });
    add(this._error, FloatingNoticeTone.error, { duration: null, channel: 'audioError' });
    return notices;
  }

  _syncStickerOrderDrafts(packs: StickerPack[]): void {
    this._stickerOrderDrafts.clear();
    for (const pack of packs) {
      this._stickerOrderDrafts.set(
        pack.id,
        pack.stickers.map((sticker) => sticker.id),
      );
    }
  }

  async _refreshActiveSection(): Promise<void> {
    switch (this._section) {
      case SettingsSection.profile:
        await this._loadAccount();
        break;
      case SettingsSection.preferences:
        await Promise.all([this._loadCloseBehavior(), this._loadAndroidNotificationSettings()]);
        break;
      case SettingsSection.stickers:
        await this._loadStickers({ forceReload: true });
        break;
      case SettingsSection.playlists:
        if (this.mounted) {
          this.setState(() => {
            this._playlistReloadToken += 1;
          });
        }
        break;
      case SettingsSection.security:
        await Promise.all([this._loadAccount(), this._loadSessions()]);
        break;
      case SettingsSection.voice:
        await this._ensureVoiceInitialized({ forceReload: true });
        break;
      case SettingsSection.about:
        await this._refreshAboutInfo();
        break;
    }
  }

  async _refreshAboutInfo(): Promise<void> {
    if (this._loadingAbout) return;
    this.setState(() => {
      this._loadingAbout = true;
    });
    await Promise.all([this._loadAutoUpdatePrompt(), this._loadInstallDate()]);
    if (!this.mounted) return;
    this.setState(() => {
      this._loadingAbout = false;
    });
  }

  async _checkAppVersion(): Promise<void> {
    if (this._checkingAppVersion) return;
    const platform = this._currentUpdatePlatform();
    if (platform == null) {
      this.setState(() => {
        this._aboutError = '当前平台暂不支持自动检查更新';
        this._markFloatingNoticeEvent('aboutError', this._aboutError);
      });
      return;
    }
    const bucketUrl = this._appConfig.releaseBucketUrl;
    this.setState(() => {
      this._checkingAppVersion = true;
      this._aboutError = null;
      this._notice = null;
    });
    if (platform === AppUpdatePlatform.macos && (await this.widget.sparkleUpdater.isAvailable())) {
      await this._checkAppVersionWithSparkle(bucketUrl);
      return;
    }
    try {
      const update = await this.widget.releaseUpdateService.checkForUpdate({
        bucketUrl,
        currentVersion: this.widget.appVersion,
        platform,
      });
      if (!this.mounted) return;
      this.setState(() => {
        this._checkingAppVersion = false;
        this._availableAppUpdate = update;
        this._resetUpdateDownloadState();
        this._notice = update == null ? '当前已是最新版本' : null;
        this._markFloatingNoticeEvent('notice', this._notice);
      });
    } catch {
      if (!this.mounted) return;
      this.setState(() => {
        this._checkingAppVersion = false;
        this._aboutError = '检查更新失败';
        this._markFloatingNoticeEvent('aboutError', this._aboutError);
      });
    }
  }

  /** Sparkle shows its own window for the result, including "up to date". */
  async _checkAppVersionWithSparkle(bucketUrl: string): Promise<void> {
    let error: string | null = null;
    try {
      await this.widget.sparkleUpdater.checkForUpdates({ bucketUrl });
    } catch {
      error = '检查更新失败';
    }
    if (!this.mounted) return;
    this.setState(() => {
      this._checkingAppVersion = false;
      this._aboutError = error;
      this._markFloatingNoticeEvent('aboutError', this._aboutError);
    });
  }

  _resetUpdateDownloadState(): void {
    this._downloadingAppUpdate = false;
    this._updateDownloadedBytes = 0;
    this._updateDownloadTotalBytes = null;
    this._updateDownloadError = null;
  }

  _closeAppUpdatePage(): void {
    if (this._downloadingAppUpdate) return;
    this.setState(() => {
      this._section = SettingsSection.about;
      this._availableAppUpdate = null;
      this._resetUpdateDownloadState();
    });
  }

  async _confirmIgnoreAvailableUpdate(): Promise<void> {
    const update = this._availableAppUpdate;
    if (update == null || this._downloadingAppUpdate) return;
    const confirmed = await this._confirmAppUpdateAction({
      title: '忽略此版本',
      icon: Icons.notifications_off_outlined,
      body: `忽略 ${appVersionLabel(update.latestVersion)} 后，除非你主动点击检查更新，` + '或出现更高版本，否则不会再主动提示该版本。',
      confirmLabel: '忽略此版本',
    });
    if (!confirmed || !this.mounted) return;
    try {
      await this.widget.autoUpdatePromptStore.writeIgnoredVersion(update.latestVersion);
      if (!this.mounted) return;
      this.setState(() => {
        this._section = SettingsSection.about;
        this._availableAppUpdate = null;
        this._notice = `已忽略此版本 ${appVersionLabel(update.latestVersion)}`;
        this._markFloatingNoticeEvent('notice', this._notice);
        this._resetUpdateDownloadState();
      });
    } catch {
      if (!this.mounted) return;
      this.setState(() => {
        this._updateDownloadError = '忽略此版本失败';
        this._markFloatingNoticeEvent('updateDownloadError', this._updateDownloadError);
      });
    }
  }

  async _confirmDownloadAvailableUpdate(): Promise<void> {
    const update = this._availableAppUpdate;
    if (update == null || this._downloadingAppUpdate) return;
    const confirmed = await this._confirmAppUpdateAction({
      title: '下载新版本',
      icon: Icons.download_for_offline_outlined,
      body: updateDownloadConfirmationBodyFor(update, this.widget.releaseUpdateService),
      confirmLabel: '下载新版本',
    });
    if (!confirmed || !this.mounted) return;
    if (update.asset.platform === AppUpdatePlatform.android) {
      try {
        await this.widget.androidSystemService.requestUpdateNotificationPermission();
        if (!this.mounted) return;
      } catch {
        // The download continues without the progress notification.
      }
    }
    await this._downloadAndInstallAvailableUpdate();
  }

  async _confirmAppUpdateAction(opts: {
    title: string;
    icon: IconData;
    body: string;
    confirmLabel: string;
  }): Promise<boolean> {
    const { title, icon, body, confirmLabel } = opts;
    const confirmed = await showDialog<boolean>((close) => (
      <DialogFrame
        title={title}
        icon={icon}
        adaptiveActions={[
          new ResponsiveDialogAction({ label: '取消', onPressed: () => close(false) }),
          new ResponsiveDialogAction({
            label: confirmLabel,
            onPressed: () => close(true),
            tone: ButtonTone.primary,
          }),
        ]}
      >
        <Text style={{ ...UiTypography.body, color: UiColors.textSecondary, lineHeight: 1.55 }}>{body}</Text>
      </DialogFrame>
    ));
    return confirmed ?? false;
  }

  async _downloadAndInstallAvailableUpdate(): Promise<void> {
    const update = this._availableAppUpdate;
    if (update == null || this._downloadingAppUpdate) return;
    if (updateDownloadsInBrowser(update, this.widget.releaseUpdateService)) {
      await this._openAvailableUpdateInBrowser(update);
      return;
    }
    const cancellationToken = new ReleaseDownloadCancellationToken();
    this.widget.onAppUpdateDownloadCancellationChanged?.(cancellationToken);
    let androidDownloadStatusSet = false;
    this.setState(() => {
      this._downloadingAppUpdate = true;
      this._updateDownloadError = null;
      this._updateDownloadedBytes = 0;
      this._updateDownloadTotalBytes = null;
    });
    try {
      if (update.asset.platform === AppUpdatePlatform.android) {
        await this.widget.androidSystemService.setUpdateDownloadActive(true);
        androidDownloadStatusSet = true;
      }
      const file = await this.widget.releaseUpdateService.downloadUpdate(update, {
        cancellationToken,
        onProgress: ({ receivedBytes, totalBytes }) => {
          if (!this.mounted) return;
          this.setState(() => {
            this._updateDownloadedBytes = receivedBytes;
            this._updateDownloadTotalBytes = totalBytes;
          });
        },
      });
      if (!this.mounted) return;
      await this.widget.releaseUpdateService.startInstaller(file, { platform: update.asset.platform });
      if (shouldTerminateApplicationAfterInstallerLaunch(update.asset.platform)) {
        await delay(280);
        await this._windowController.terminateApplication();
      } else if (this.mounted) {
        // Android's package installer reads the APK from this process through
        // FileProvider. Keep the process alive until the OS has finished its
        // prepare/verify phase; the OS will replace it when installation wins.
        this.setState(() => {
          this._downloadingAppUpdate = false;
        });
      }
    } catch (error) {
      if (error instanceof ReleaseDownloadCancelledException) {
        if (!this.mounted) return;
        this.setState(() => {
          this._downloadingAppUpdate = false;
          this._updateDownloadedBytes = 0;
          this._updateDownloadTotalBytes = null;
          this._updateDownloadError = '下载已中断';
          this._markFloatingNoticeEvent('updateDownloadError', this._updateDownloadError);
        });
        return;
      }
      if (!this.mounted) return;
      this.setState(() => {
        this._downloadingAppUpdate = false;
        this._updateDownloadError = userFacingErrorMessage(error, { fallback: '下载或启动安装程序失败' });
        this._markFloatingNoticeEvent('updateDownloadError', this._updateDownloadError);
      });
    } finally {
      if (androidDownloadStatusSet) {
        try {
          await this.widget.androidSystemService.setUpdateDownloadActive(false);
        } catch {
          // Notification decoration must not mask the actual update result.
        }
      }
      this.widget.onAppUpdateDownloadCancellationChanged?.(null);
    }
  }

  async _openAvailableUpdateInBrowser(update: AvailableAppUpdate): Promise<void> {
    try {
      await this.widget.externalUriLauncher.open(update.downloadUrl);
      if (!this.mounted) return;
      this.setState(() => {
        this._notice = '已在浏览器中开始下载，下载完成后打开 DMG 替换旧版本';
        this._markFloatingNoticeEvent('notice', this._notice);
      });
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._updateDownloadError = userFacingErrorMessage(error, { fallback: '无法打开下载链接' });
        this._markFloatingNoticeEvent('updateDownloadError', this._updateDownloadError);
      });
    }
  }

  _currentUpdatePlatform(): AppUpdatePlatform | null {
    return this.widget.updatePlatformOverride ?? appUpdatePlatformForOperatingSystem(HostPlatform.operatingSystem);
  }

  async _openFeedbackMail(): Promise<void> {
    if (this._openingFeedbackMail) return;
    const senderEmail = boundEmailForFeedback(this._user?.email);
    if (senderEmail == null) {
      this.setState(() => {
        this._aboutError = '当前账号未绑定邮箱，无法发起意见反馈';
        this._markFloatingNoticeEvent('aboutError', this._aboutError);
      });
      return;
    }
    const draft = new FeedbackMailDraft({
      from: senderEmail,
      to: gangChatSupportEmail,
      subject: feedbackMailSubject(this.widget.appVersion),
      body: feedbackMailBody({
        senderEmail,
        currentVersion: this.widget.appVersion,
      }),
    });
    this.setState(() => {
      this._openingFeedbackMail = true;
      this._aboutError = null;
      this._notice = null;
    });
    try {
      await this.widget.feedbackMailService.openDraft(draft);
      if (!this.mounted) return;
      this.setState(() => {
        this._openingFeedbackMail = false;
        this._notice = '已打开邮件客户端';
        this._markFloatingNoticeEvent('notice', this._notice);
      });
    } catch {
      if (!this.mounted) return;
      this.setState(() => {
        this._openingFeedbackMail = false;
        this._aboutError = '无法打开邮件客户端';
        this._markFloatingNoticeEvent('aboutError', this._aboutError);
      });
    }
  }

  async _ensureVoiceInitialized(opts: { forceReload?: boolean } = {}): Promise<void> {
    const { forceReload = false } = opts;
    if (this._voiceInitialized && !forceReload) return;
    if (!this._voiceInitialized) {
      this._voiceInitialized = true;
      this._deviceSubscription ??= this.widget.audioDeviceService.devicesChanged.listen((devices) => {
        unawaited(this._applyDevices(devices));
      });
      // Follow OS-selected audio endpoints when the user has not pinned one.
      // The subscriptions re-run selection whenever the system defaults change
      // underneath Settings.
      this._systemDefaultInputSubscription ??= this._systemAudio.inputChanges.listen((deviceId) => {
        this._systemDefaultInputId = deviceId;
        unawaited(this._onSystemDefaultAudioChanged());
      });
      this._systemDefaultOutputSubscription ??= this._systemAudio.outputChanges.listen((deviceId) => {
        this._systemDefaultOutputId = deviceId;
        unawaited(this._onSystemDefaultAudioChanged());
      });
      unawaited(this._loadStoredAudioSettings());
    }
    const defaultDeviceIds = await Promise.all([
      this._systemAudio.currentInputDeviceId(),
      this._systemAudio.currentOutputDeviceId(),
    ]);
    this._systemDefaultInputId = defaultDeviceIds[0];
    this._systemDefaultOutputId = defaultDeviceIds[1];
    await this._loadDevices();
  }

  async _saveAccount(opts: { target?: AccountFormSaveTarget } = {}): Promise<void> {
    const { target = AccountFormSaveTarget.account } = opts;
    const user = this._user;
    if (!this._settingsController.hasApi || user == null || this._savingAccount || this._savingProfile) {
      return;
    }

    let draft: AccountUpdateDraft;
    switch (target) {
      case AccountFormSaveTarget.preferences:
        draft = preferencesUpdateDraftFromForm({ user, language: this._language });
        break;
      case AccountFormSaveTarget.account:
        draft = accountUpdateDraftFromForm({
          user,
          username: this._usernameController.text,
          email: this._emailController.text,
          emailPublic: this._emailPublic,
          phoneNumber: this._phoneController.text,
          phoneNumberPublic: this._phonePublic,
          language: this._language,
        });
        break;
      case AccountFormSaveTarget.profile:
        throw new StateError('Profile uses _saveProfile');
    }
    if (draft.error != null) {
      const error = draft.error;
      this.setState(() =>
        this._applyAccountFormSaveStatePatch(
          accountFormSaveValidationFailed({
            error,
            savingAccount: this._savingAccount,
            savingProfile: this._savingProfile,
            notice: this._notice,
          }),
        ),
      );
      return;
    }
    const accountEmailUpdate = target === AccountFormSaveTarget.account;
    const emailIdentityChanged = accountEmailUpdate && !this._accountEmailMatchesUser;
    const emailVerificationRequired = accountEmailUpdate && (emailIdentityChanged || !user.emailVerified);
    if (emailVerificationRequired && !this._accountEmailVerified) {
      showFloatingErrorNotice('请先验证邮箱');
      return;
    }
    const emailVerificationOnlyUpdate = emailVerificationRequired && draft.email == null;
    if (draft.noChanges && !emailVerificationOnlyUpdate) {
      if (target === AccountFormSaveTarget.preferences) {
        if (!this._isManagingUser) {
          await this._rememberLanguagePreference(this._language);
        }
        if (!this.mounted) return;
      }
      this.setState(() =>
        this._applyAccountFormSaveStatePatch(
          accountFormSaveNoChanges({
            target,
            savingAccount: this._savingAccount,
            savingProfile: this._savingProfile,
            accountError: this._accountError,
          }),
        ),
      );
      return;
    }

    this.setState(() =>
      this._applyAccountFormSaveStatePatch(
        accountFormSaveStarted({
          target,
          savingAccount: this._savingAccount,
          savingProfile: this._savingProfile,
        }),
      ),
    );
    try {
      const updated = await this._settingsController.updateAccount({
        username: draft.username,
        email: draft.email ?? (emailVerificationOnlyUpdate ? this._emailController.text.trim() : null),
        emailVerificationToken: emailVerificationRequired ? this._emailVerificationToken : null,
        emailPublic: draft.emailPublic,
        phoneNumber: draft.phoneNumber,
        phoneNumberPublic: draft.phoneNumberPublic,
        language: draft.language,
      });
      if (!this.mounted) return;
      if (updated == null) {
        this.setState(() =>
          this._applyAccountFormSaveStatePatch(
            accountFormSaveCancelled({
              target,
              savingAccount: this._savingAccount,
              savingProfile: this._savingProfile,
              accountError: this._accountError,
              notice: this._notice,
            }),
          ),
        );
        return;
      }
      if (target === AccountFormSaveTarget.preferences) {
        if (!this._isManagingUser) {
          await this._rememberLanguagePreference(updated.language);
        }
        if (!this.mounted) return;
      }
      this.setState(() => {
        this._user = updated;
        this._syncUserFields(updated);
        this._applyAccountFormSaveStatePatch(
          accountFormSaveSucceeded({
            target,
            savingAccount: this._savingAccount,
            savingProfile: this._savingProfile,
          }),
        );
      });
      this.widget.onUserUpdated?.(updated);
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyAccountFormSaveStatePatch(
          accountFormSaveFailed({
            target,
            savingAccount: this._savingAccount,
            savingProfile: this._savingProfile,
            failure: e,
          }),
        ),
      );
    }
  }

  async _saveProfile(): Promise<void> {
    const user = this._user;
    if (!this._settingsController.hasApi || user == null || this._savingProfile || this._savingAccount) {
      return;
    }

    const usernameChanged = this._usernameController.text.trim() !== user.username;
    const usernameDraft = usernameChanged
      ? loginUsernameUpdateDraftFromForm({
          user,
          username: this._usernameController.text,
        })
      : AccountUpdateDraft.noChanges();
    if (usernameDraft.error != null) {
      const error = usernameDraft.error;
      this.setState(() =>
        this._applyAccountFormSaveStatePatch(
          accountFormSaveValidationFailed({
            error,
            savingAccount: this._savingAccount,
            savingProfile: this._savingProfile,
            notice: this._notice,
          }),
        ),
      );
      return;
    }
    if (!usernameDraft.noChanges) {
      const availabilityError = await this._ensureLoginUsernameAvailable({
        user,
        username: usernameDraft.username!,
      });
      if (!this.mounted) return;
      if (availabilityError != null) {
        this.setState(() =>
          this._applyAccountFormSaveStatePatch(
            accountFormSaveValidationFailed({
              error: availabilityError,
              savingAccount: this._savingAccount,
              savingProfile: this._savingProfile,
              notice: this._notice,
            }),
          ),
        );
        return;
      }
    }

    const profileDraft = profileUpdateDraftFromForm({
      user,
      displayName: this._displayNameController.text,
      bio: this._bioController.text,
      gender: this._gender,
      defaultAvatarKey: this._defaultAvatarKey,
      pendingAvatarAssetId: this._pendingAvatarAssetId,
      clearUploadedAvatar: this._clearUploadedAvatar,
    });
    if (profileDraft.error != null) {
      const error = profileDraft.error;
      this.setState(() =>
        this._applyAccountFormSaveStatePatch(
          accountFormSaveValidationFailed({
            error,
            savingAccount: this._savingAccount,
            savingProfile: this._savingProfile,
            notice: this._notice,
          }),
        ),
      );
      return;
    }
    if (usernameDraft.noChanges && profileDraft.noChanges) {
      this.setState(() =>
        this._applyAccountFormSaveStatePatch(
          accountFormSaveNoChanges({
            target: AccountFormSaveTarget.profile,
            savingAccount: this._savingAccount,
            savingProfile: this._savingProfile,
            accountError: this._accountError,
          }),
        ),
      );
      return;
    }

    this.setState(() =>
      this._applyAccountFormSaveStatePatch(
        accountFormSaveStarted({
          target: AccountFormSaveTarget.profile,
          savingAccount: this._savingAccount,
          savingProfile: this._savingProfile,
        }),
      ),
    );
    try {
      let updated: CurrentUser = user;
      if (!usernameDraft.noChanges) {
        const accountUpdated = await this._settingsController.updateAccount({
          username: usernameDraft.username,
        });
        if (!this.mounted) return;
        if (accountUpdated == null) {
          this.setState(() =>
            this._applyAccountFormSaveStatePatch(
              accountFormSaveCancelled({
                target: AccountFormSaveTarget.profile,
                savingAccount: this._savingAccount,
                savingProfile: this._savingProfile,
                accountError: this._accountError,
                notice: this._notice,
              }),
            ),
          );
          return;
        }
        updated = accountUpdated;
      }
      if (!profileDraft.noChanges) {
        const profileUpdated = await this._settingsController.updateProfile({
          displayName: profileDraft.displayName,
          bio: profileDraft.bio,
          gender: profileDraft.gender,
          defaultAvatarKey: profileDraft.defaultAvatarKey,
          avatarAssetId: profileDraft.avatarAssetId,
        });
        if (!this.mounted) return;
        if (profileUpdated == null) {
          this.setState(() =>
            this._applyAccountFormSaveStatePatch(
              accountFormSaveCancelled({
                target: AccountFormSaveTarget.profile,
                savingAccount: this._savingAccount,
                savingProfile: this._savingProfile,
                accountError: this._accountError,
                notice: this._notice,
              }),
            ),
          );
          return;
        }
        updated = profileUpdated;
      }
      const result = updated;
      this.setState(() => {
        this._user = result;
        this._syncUserFields(result);
        this._applyAccountFormSaveStatePatch(
          accountFormSaveSucceeded({
            target: AccountFormSaveTarget.profile,
            savingAccount: this._savingAccount,
            savingProfile: this._savingProfile,
          }),
        );
      });
      this.widget.onUserUpdated?.(result);
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyAccountFormSaveStatePatch(
          accountFormSaveFailed({
            target: AccountFormSaveTarget.profile,
            savingAccount: this._savingAccount,
            savingProfile: this._savingProfile,
            failure: e,
          }),
        ),
      );
    }
  }

  /** The avatar flags every avatar patch carries over. */
  private get _avatarFlags() {
    return {
      pendingAvatarAssetId: this._pendingAvatarAssetId,
      pendingAvatarUrl: this._pendingAvatarUrl,
      clearUploadedAvatar: this._clearUploadedAvatar,
      accountError: this._accountError,
      stickerError: this._stickerError,
    };
  }

  async _pickAndUploadAvatar(): Promise<void> {
    if (!this._settingsController.hasApi || this._uploadingAvatar) return;
    this.setState(() =>
      this._applyAccountAvatarStatePatch(
        accountAvatarPreparationStarted({
          target: AccountAvatarErrorTarget.account,
          ...this._avatarFlags,
          uploadingAvatar: this._uploadingAvatar,
        }),
      ),
    );

    let file: SelectedFile | null;
    try {
      file = await this.widget.fileSelectionService.openFile({ acceptedTypeGroups: _avatarFileTypeGroups });
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyAccountAvatarStatePatch(
          accountAvatarActionFailed({
            target: AccountAvatarErrorTarget.account,
            ...this._avatarFlags,
            failure: account_display.avatarPickerOpenFailureMessage(e),
          }),
        ),
      );
      return;
    }
    if (file == null) return;

    let bytes: Uint8Array;
    try {
      bytes = await file.readAsBytes();
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyAccountAvatarStatePatch(
          accountAvatarActionFailed({
            target: AccountAvatarErrorTarget.account,
            ...this._avatarFlags,
            failure: account_display.avatarReadFailureMessage(e),
          }),
        ),
      );
      return;
    }
    if (bytes.length === 0) {
      this.setState(() =>
        this._applyAccountAvatarStatePatch(
          accountAvatarActionFailed({
            target: AccountAvatarErrorTarget.account,
            ...this._avatarFlags,
            failure: account_display.avatarEmptyFileMessage(),
          }),
        ),
      );
      return;
    }
    if (!this.mounted) return;

    const sourceBytes = bytes;
    const cropped = await showDialog<Uint8Array>(() => <AvatarCropDialog bytes={sourceBytes} />, {
      barrierDismissible: false,
    });
    if (cropped == null || !this.mounted) return;

    this.setState(() =>
      this._applyAccountAvatarStatePatch(
        accountAvatarUploadStarted({
          target: AccountAvatarErrorTarget.account,
          ...this._avatarFlags,
        }),
      ),
    );
    try {
      const asset = await this._settingsController.uploadImageAsset({
        bytes: cropped,
        filename: account_display.avatarUploadFilename(file.name),
        purpose: 'avatar',
      });
      if (asset == null) {
        if (!this.mounted) return;
        this.setState(() =>
          this._applyAccountAvatarStatePatch(
            accountAvatarActionCancelled({
              target: AccountAvatarErrorTarget.account,
              ...this._avatarFlags,
            }),
          ),
        );
        return;
      }
      if (!this.mounted) return;
      this.setState(() =>
        this._applyAccountAvatarStatePatch(
          accountAvatarPendingUploadSucceeded({
            assetId: asset.id,
            assetUrl: asset.url,
            stickerError: this._stickerError,
          }),
        ),
      );
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyAccountAvatarStatePatch(
          accountAvatarActionFailed({
            target: AccountAvatarErrorTarget.account,
            ...this._avatarFlags,
            failure: e,
          }),
        ),
      );
    }
  }

  _allStickerItems(): ManagedSticker[] {
    return managedStickerItems(this._stickerPacks, {
      orderForPack: (pack) => this._stickerOrderDrafts.get(pack.id),
    });
  }

  _filteredStickerItems(): ManagedSticker[] {
    return filteredManagedStickerItems(this._allStickerItems(), {
      keyword: this._stickerFilterKeyword,
      mimeType: this._stickerFilterMimeType,
    });
  }

  get _stickerFilterActive(): boolean {
    return stickerFilterActive({
      keyword: this._stickerFilterKeyword,
      mimeType: this._stickerFilterMimeType,
    });
  }

  get _stickerManagementBusy(): boolean {
    return stickerManagementBusy({
      uploading: this._uploadingStickers,
      deleting: this._deletingStickers,
      savingOrder: this._savingStickerOrder,
      downloading: this._downloadingStickers,
    });
  }

  _stickerSelectionNumbers(): Map<string, number> {
    return stickerSelectionNumbers(this._selectedStickerIds);
  }

  /** The action flags every sticker action patch carries over. */
  private get _stickerActionFlags() {
    return {
      uploading: this._uploadingStickers,
      deleting: this._deletingStickers,
      savingOrder: this._savingStickerOrder,
      downloading: this._downloadingStickers,
      selectedStickerIds: this._selectedStickerIds,
    };
  }

  async _ensureActiveStickerPack(): Promise<StickerPack> {
    if (this._stickerPacks.length > 0) return this._stickerPacks[0]!;

    if (!this._settingsController.hasApi) {
      throw new StateError(stickerPackRequiresServerMessage());
    }
    const created = await this._settingsController.createStickerPack({
      name: defaultStickerPackName(StickerManagementScope.personal),
      sortOrder: (this._stickerPacks.length + 1) * 10,
    });
    if (created == null) {
      throw new StateError(stickerPackRequiresServerMessage());
    }
    if (this.mounted) {
      this.setState(() => {
        this._stickerPacks = [...this._stickerPacks, created];
        this._stickerOrderDrafts.set(created.id, []);
      });
    }
    return created;
  }

  async _pickAndUploadStickers(): Promise<void> {
    if (!this._settingsController.hasApi || this._stickerManagementBusy) return;

    let files: SelectedFile[];
    try {
      files = await this.widget.fileSelectionService.openFiles({ acceptedTypeGroups: _stickerFileTypeGroups });
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyStickerActionPatch(
          stickerActionErrorShown({
            ...this._stickerActionFlags,
            failure: stickerPickerOpenFailureMessage(e),
            notice: this._notice,
          }),
        ),
      );
      return;
    }
    if (files.length === 0) return;

    this.setState(() =>
      this._applyStickerActionPatch(
        stickerActionStarted({ action: StickerActionKind.upload, ...this._stickerActionFlags }),
      ),
    );
    let uploadedCount = 0;
    try {
      const uploadItems = await stickerUploadItemsFromFiles(stickerUploadSourcesFromSelectedFiles(files), {
        decodeImageDimensions: decodeStickerImageDimensions,
      });
      if (uploadItems.length === 0) {
        throw new StateError(stickerNoUploadableImagesMessage());
      }
      const pack = await this._ensureActiveStickerPack();
      const uploadSortOrders = sticker_ordering.stickerSortOrdersBeforeExisting(pack, uploadItems.length);
      for (const [index, item] of uploadItems.entries()) {
        const asset = await this._settingsController.uploadImageAsset({
          bytes: item.bytes,
          filename: stickerUploadFilename(item.filename, index),
          purpose: 'sticker',
        });
        if (asset == null) continue;
        await this._settingsController.addSticker({
          packId: pack.id,
          assetId: asset.id,
          name: stickerNameFromFilename(item.filename),
          sortOrder: uploadSortOrders[index],
        });
        uploadedCount += 1;
      }
      await this._loadStickers({ forceReload: true });
      if (!this.mounted) return;
      this.setState(() =>
        this._applyStickerActionPatch(
          stickerActionSucceeded({
            action: StickerActionKind.upload,
            ...this._stickerActionFlags,
            error: this._stickerError,
            notice: stickerUploadNotice({
              scope: StickerManagementScope.personal,
              count: uploadedCount,
            }),
          }),
        ),
      );
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyStickerActionPatch(
          stickerActionFailed({ action: StickerActionKind.upload, ...this._stickerActionFlags, failure: e }),
        ),
      );
    }
  }

  _toggleStickerManageMode(): void {
    this.setState(() =>
      this._applyStickerSelectionPatch(
        stickerManagementModeToggled({
          managing: this._managingStickers,
          filterKeyword: this._stickerFilterKeyword,
          filterMimeType: this._stickerFilterMimeType,
        }),
      ),
    );
  }

  _toggleStickerSelection(stickerId: string): void {
    this.setState(() =>
      this._applyStickerSelectionPatch(
        stickerSelectionToggled({
          managing: this._managingStickers,
          filterKeyword: this._stickerFilterKeyword,
          filterMimeType: this._stickerFilterMimeType,
          selectedStickerIds: this._selectedStickerIds,
          stickerId,
        }),
      ),
    );
  }

  _selectAllVisibleStickers(items: ManagedSticker[]): void {
    this.setState(() =>
      this._applyStickerSelectionPatch(
        stickerVisibleSelectionToggled({
          managing: this._managingStickers,
          busy: this._stickerManagementBusy,
          filterKeyword: this._stickerFilterKeyword,
          filterMimeType: this._stickerFilterMimeType,
          selectedStickerIds: this._selectedStickerIds,
          visibleItems: items,
        }),
      ),
    );
  }

  async _deleteSelectedStickers(): Promise<void> {
    const selectedIds = [...this._selectedStickerIds];
    if (
      !this._settingsController.hasApi ||
      !canStartStickerSelectionAction({
        busy: this._stickerManagementBusy,
        selectedStickerIds: selectedIds,
      })
    ) {
      return;
    }
    const byStickerId = new Map<string, ManagedSticker>();
    for (const item of this._allStickerItems()) byStickerId.set(item.sticker.id, item);

    const confirmed = await showDialog<boolean>(() => (
      <StickerConfirmDialog
        title={stickerDeleteDialogTitle(StickerManagementScope.personal)}
        body={stickerBulkDeleteConfirmBody({
          scope: StickerManagementScope.personal,
          count: selectedIds.length,
        })}
        confirmLabel="删除"
        confirmIcon={Icons.delete_outline}
        danger
      />
    ));
    if (confirmed !== true || !this.mounted) return;

    this.setState(() =>
      this._applyStickerActionPatch(
        stickerActionStarted({ action: StickerActionKind.delete, ...this._stickerActionFlags }),
      ),
    );
    try {
      for (const stickerId of selectedIds) {
        const item = byStickerId.get(stickerId);
        if (item == null) continue;
        await this._settingsController.deleteSticker({
          packId: item.pack.id,
          stickerId,
        });
      }
      await this._loadStickers({ forceReload: true });
      if (!this.mounted) return;
      this.setState(() =>
        this._applyStickerActionPatch(
          stickerActionSucceeded({
            action: StickerActionKind.delete,
            ...this._stickerActionFlags,
            error: this._stickerError,
            notice: stickerDeletedNotice({
              scope: StickerManagementScope.personal,
              count: selectedIds.length,
            }),
            clearSelection: true,
          }),
        ),
      );
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyStickerActionPatch(
          stickerActionFailed({ action: StickerActionKind.delete, ...this._stickerActionFlags, failure: e }),
        ),
      );
    }
  }

  async _downloadSelectedStickers(): Promise<void> {
    const selectedIds = [...this._selectedStickerIds];
    await this._downloadStickerIds(selectedIds);
  }

  async _downloadStickerIds(stickerIds: string[]): Promise<void> {
    if (
      !this._settingsController.hasApi ||
      !canStartStickerSelectionAction({
        busy: this._stickerManagementBusy,
        selectedStickerIds: stickerIds,
      })
    ) {
      return;
    }

    this.setState(() =>
      this._applyStickerActionPatch(
        stickerActionStarted({ action: StickerActionKind.download, ...this._stickerActionFlags }),
      ),
    );
    try {
      const downloaded = await this._settingsController.downloadStickers({ stickerIds });
      if (downloaded == null) {
        if (!this.mounted) return;
        this.setState(() =>
          this._applyStickerActionPatch(
            stickerActionCancelled({ action: StickerActionKind.download, ...this._stickerActionFlags }),
          ),
        );
        return;
      }
      const location = await this.widget.fileSelectionService.getSaveLocation({
        suggestedName: downloaded.filename,
        acceptedTypeGroups: _stickerFileTypeGroups,
        confirmButtonText: '保存',
      });
      if (location == null) {
        if (!this.mounted) return;
        this.setState(() =>
          this._applyStickerActionPatch(
            stickerActionCancelled({ action: StickerActionKind.download, ...this._stickerActionFlags }),
          ),
        );
        return;
      }
      await this.widget.fileSelectionService.saveBytesToLocation({
        bytes: downloaded.bytes,
        location,
        filename: downloaded.filename,
        mimeType: downloaded.mimeType,
      });
      if (!this.mounted) return;
      this.setState(() =>
        this._applyStickerActionPatch(
          stickerActionSucceeded({
            action: StickerActionKind.download,
            ...this._stickerActionFlags,
            error: this._stickerError,
            notice: stickerDownloadNotice({
              scope: StickerManagementScope.personal,
              count: stickerIds.length,
            }),
          }),
        ),
      );
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyStickerActionPatch(
          stickerActionFailed({ action: StickerActionKind.download, ...this._stickerActionFlags, failure: e }),
        ),
      );
    }
  }

  async _pinSelectedStickers(): Promise<void> {
    const selectedIds = [...this._selectedStickerIds];
    if (
      !this._settingsController.hasApi ||
      !canStartStickerSelectionAction({
        busy: this._stickerManagementBusy,
        selectedStickerIds: selectedIds,
      }) ||
      !sticker_ordering.stickerSelectionWouldChangePinnedOrder({
        packs: this._stickerPacks,
        selectedStickerIds: selectedIds,
        orderByPack: this._stickerOrderDrafts,
      })
    ) {
      return;
    }

    const selectedByPack = sticker_ordering.selectedStickerIdsByPack(this._stickerPacks, selectedIds);
    if (selectedByPack.size === 0) {
      this.setState(() =>
        this._applyStickerActionPatch(
          stickerActionNoticeShown({
            ...this._stickerActionFlags,
            error: this._stickerError,
            notice: stickerNoOrderChangeNotice(StickerManagementScope.personal),
          }),
        ),
      );
      return;
    }

    this.setState(() =>
      this._applyStickerActionPatch(
        stickerActionStarted({ action: StickerActionKind.order, ...this._stickerActionFlags }),
      ),
    );
    try {
      for (const pack of this._stickerPacks) {
        const selectedInPack = selectedByPack.get(pack.id);
        if (selectedInPack == null || selectedInPack.length === 0) continue;
        const nextOrder = sticker_ordering.stickerOrderWithStickerIdsPinnedToFront(pack, selectedInPack, {
          order: this._stickerOrderDrafts.get(pack.id),
        });
        if (nextOrder == null) continue;
        await this._settingsController.reorderStickers({
          packId: pack.id,
          stickerIds: nextOrder,
        });
      }
      await this._loadStickers({ forceReload: true });
      if (!this.mounted) return;
      this.setState(() =>
        this._applyStickerActionPatch(
          stickerActionSucceeded({
            action: StickerActionKind.order,
            ...this._stickerActionFlags,
            error: this._stickerError,
            notice: stickerPinnedNotice({
              scope: StickerManagementScope.personal,
              count: selectedIds.length,
            }),
          }),
        ),
      );
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyStickerActionPatch(
          stickerActionFailed({ action: StickerActionKind.order, ...this._stickerActionFlags, failure: e }),
        ),
      );
    }
  }

  _previewSticker(item: ManagedSticker): void {
    const imageUrl = this._appConfig.resolveAssetUrl(item.sticker.asset.url);
    if (imageUrl == null) return;
    const placement = this._stickerPlacement(item.sticker.id);
    const filterActive = this._stickerFilterActive;
    const imagePreviewOpener = this.widget.stickerImagePreviewOpener;
    unawaited(
      showDialog<void>(() => (
        <StickerPreviewDialog
          item={item}
          imageUrl={imageUrl}
          canMoveUp={!filterActive && (placement?.canMoveUp ?? false)}
          canMoveDown={!filterActive && (placement?.canMoveDown ?? false)}
          canPin={placement?.canPin ?? false}
          canRename
          canDownload
          canDelete
          onRename={(name) => this._renameSticker(item, name)}
          onSetAvatar={() => this._setStickerAsAvatar(item)}
          onDownload={() => this._downloadStickerIds([item.sticker.id])}
          onDelete={() => this._deleteStickerItem(item)}
          onMoveUp={() => this._moveStickerItem(item, -1)}
          onMoveDown={() => this._moveStickerItem(item, 1)}
          onPin={() => this._pinStickerItem(item)}
          imagePreviewOpener={imagePreviewOpener}
        />
      )),
    );
  }

  _stickerPlacement(stickerId: string): sticker_ordering.StickerPlacementData | null {
    return sticker_ordering.stickerPlacement(this._stickerPacks, stickerId, {
      orderForPack: (pack) => this._stickerOrderDrafts.get(pack.id),
    });
  }

  async _renameSticker(item: ManagedSticker, name: string): Promise<string | null> {
    const trimmed = stickerRenameName(name);
    if (!this._settingsController.hasApi || trimmed == null) return null;
    try {
      const updated = await this._settingsController.updateSticker({
        packId: item.pack.id,
        stickerId: item.sticker.id,
        name: trimmed,
      });
      if (updated == null) return null;
      await this._loadStickers({ forceReload: true });
      return updated.name;
    } catch (e) {
      if (!this.mounted) return null;
      this.setState(() =>
        this._applyStickerActionPatch(
          stickerActionErrorShown({
            ...this._stickerActionFlags,
            failure: e,
            notice: this._notice,
          }),
        ),
      );
      return null;
    }
  }

  async _deleteStickerItem(item: ManagedSticker): Promise<boolean> {
    if (!this._settingsController.hasApi || this._stickerManagementBusy) return false;

    const confirmed = await showDialog<boolean>(() => (
      <StickerConfirmDialog
        title={stickerDeleteDialogTitle(StickerManagementScope.personal)}
        body={stickerSingleDeleteConfirmBody({
          scope: StickerManagementScope.personal,
          stickerName: item.sticker.name,
        })}
        confirmLabel="删除"
        confirmIcon={Icons.delete_outline}
        danger
      />
    ));
    if (confirmed !== true || !this.mounted) return false;

    this.setState(() =>
      this._applyStickerActionPatch(
        stickerActionStarted({ action: StickerActionKind.delete, ...this._stickerActionFlags }),
      ),
    );
    try {
      await this._settingsController.deleteSticker({
        packId: item.pack.id,
        stickerId: item.sticker.id,
      });
      await this._loadStickers({ forceReload: true });
      if (!this.mounted) return false;
      this.setState(() =>
        this._applyStickerActionPatch(
          stickerActionSucceeded({
            action: StickerActionKind.delete,
            ...this._stickerActionFlags,
            error: this._stickerError,
            notice: stickerDeletedNotice({
              scope: StickerManagementScope.personal,
            }),
          }),
        ),
      );
      return true;
    } catch (e) {
      if (!this.mounted) return false;
      this.setState(() =>
        this._applyStickerActionPatch(
          stickerActionFailed({ action: StickerActionKind.delete, ...this._stickerActionFlags, failure: e }),
        ),
      );
      return false;
    }
  }

  async _moveStickerItem(item: ManagedSticker, delta: number): Promise<sticker_ordering.StickerPlacementData | null> {
    const placement = this._stickerPlacement(item.sticker.id);
    if (this._stickerFilterActive) return placement;
    if (!this._settingsController.hasApi || placement == null || this._stickerManagementBusy) {
      return placement;
    }
    const ids = sticker_ordering.movedStickerOrder(placement.pack, item.sticker.id, delta, {
      order: this._stickerOrderDrafts.get(placement.pack.id),
    });
    if (ids == null) return placement;

    this.setState(() =>
      this._applyStickerActionPatch(
        stickerActionStarted({ action: StickerActionKind.order, ...this._stickerActionFlags }),
      ),
    );
    try {
      await this._settingsController.reorderStickers({
        packId: placement.pack.id,
        stickerIds: ids,
      });
      await this._loadStickers({ forceReload: true });
      if (!this.mounted) return placement;
      this.setState(() =>
        this._applyStickerActionPatch(
          stickerActionSucceeded({
            action: StickerActionKind.order,
            ...this._stickerActionFlags,
            error: this._stickerError,
            notice: stickerMoveNotice({
              scope: StickerManagementScope.personal,
              delta,
            }),
          }),
        ),
      );
      return this._stickerPlacement(item.sticker.id);
    } catch (e) {
      if (!this.mounted) return placement;
      this.setState(() =>
        this._applyStickerActionPatch(
          stickerActionFailed({ action: StickerActionKind.order, ...this._stickerActionFlags, failure: e }),
        ),
      );
      return placement;
    }
  }

  async _pinStickerItem(item: ManagedSticker): Promise<sticker_ordering.StickerPlacementData | null> {
    const placement = this._stickerPlacement(item.sticker.id);
    if (!this._settingsController.hasApi || placement == null || placement.index === 0 || this._stickerManagementBusy) {
      return placement;
    }

    const ids = sticker_ordering.pinnedStickerOrder(placement.pack, item.sticker.id, {
      order: this._stickerOrderDrafts.get(placement.pack.id),
    });
    if (ids == null) return placement;

    this.setState(() =>
      this._applyStickerActionPatch(
        stickerActionStarted({ action: StickerActionKind.order, ...this._stickerActionFlags }),
      ),
    );
    try {
      await this._settingsController.reorderStickers({
        packId: placement.pack.id,
        stickerIds: ids,
      });
      await this._loadStickers({ forceReload: true });
      if (!this.mounted) return placement;
      this.setState(() =>
        this._applyStickerActionPatch(
          stickerActionSucceeded({
            action: StickerActionKind.order,
            ...this._stickerActionFlags,
            error: this._stickerError,
            notice: stickerPinnedNotice({ scope: StickerManagementScope.personal }),
          }),
        ),
      );
      return this._stickerPlacement(item.sticker.id);
    } catch (e) {
      if (!this.mounted) return placement;
      this.setState(() =>
        this._applyStickerActionPatch(
          stickerActionFailed({ action: StickerActionKind.order, ...this._stickerActionFlags, failure: e }),
        ),
      );
      return placement;
    }
  }

  async _setStickerAsAvatar(item: ManagedSticker): Promise<void> {
    if (!this._settingsController.hasApi || this._uploadingAvatar) return;
    this.setState(() =>
      this._applyAccountAvatarStatePatch(
        accountAvatarUploadStarted({
          target: AccountAvatarErrorTarget.sticker,
          ...this._avatarFlags,
        }),
      ),
    );
    const cancelled = () =>
      this.setState(() =>
        this._applyAccountAvatarStatePatch(
          accountAvatarActionCancelled({
            target: AccountAvatarErrorTarget.sticker,
            ...this._avatarFlags,
          }),
        ),
      );
    try {
      const downloaded = await this._settingsController.downloadStickers({
        stickerIds: [item.sticker.id],
      });
      if (downloaded == null) {
        if (!this.mounted) return;
        cancelled();
        return;
      }
      if (!this.mounted) return;
      const cropped = await showDialog<Uint8Array>(() => <AvatarCropDialog bytes={downloaded.bytes} />, {
        barrierDismissible: false,
      });
      if (cropped == null || !this.mounted) {
        if (!this.mounted) return;
        cancelled();
        return;
      }
      const asset = await this._settingsController.uploadImageAsset({
        bytes: cropped,
        filename: stickerAvatarUploadFilename({ stickerId: item.sticker.id }),
        purpose: 'avatar',
      });
      if (asset == null) {
        if (!this.mounted) return;
        cancelled();
        return;
      }
      const updated = await this._settingsController.updateProfile({
        avatarAssetId: asset.id,
      });
      if (updated == null) {
        if (!this.mounted) return;
        cancelled();
        return;
      }
      if (!this.mounted) return;
      this.setState(() => {
        this._user = updated;
        this._syncUserFields(updated);
        this._applyAccountAvatarStatePatch(
          accountAvatarProfileUpdatedFromStickerSucceeded({
            pendingAvatarAssetId: this._pendingAvatarAssetId,
            pendingAvatarUrl: this._pendingAvatarUrl,
            clearUploadedAvatar: this._clearUploadedAvatar,
            accountError: this._accountError,
          }),
        );
      });
      this.widget.onUserUpdated?.(updated);
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyAccountAvatarStatePatch(
          accountAvatarActionFailed({
            target: AccountAvatarErrorTarget.sticker,
            ...this._avatarFlags,
            failure: e,
          }),
        ),
      );
    }
  }

  async _openStickerFilter(): Promise<void> {
    const keyword = this._stickerFilterKeyword;
    const mimeType = this._stickerFilterMimeType;
    const result = await showDialog<StickerFilterDraft>(() => <StickerFilterDialog keyword={keyword} mimeType={mimeType} />);
    if (result == null || !this.mounted) return;
    this.setState(() =>
      this._applyStickerSelectionPatch(
        stickerFilterApplied({
          managing: this._managingStickers,
          keyword: result.keyword,
          mimeType: result.mimeType,
        }),
      ),
    );
  }

  _selectDefaultAvatarKey(value: string): void {
    this.setState(() =>
      this._applyAccountPresetAvatarSelectionPatch(
        accountPresetAvatarSelected({
          defaultAvatarKey: value,
          currentAvatarUrl: this._user?.avatarUrl ?? null,
        }),
      ),
    );
  }

  async _changePassword(): Promise<void> {
    if (
      !account_display.canStartPasswordChange({
        hasApi: this._settingsController.hasApi,
        changingPassword: this._changingPassword,
      })
    ) {
      return;
    }
    const draft = passwordChangeDraftFromForm({
      currentPassword: this._currentPasswordController.text,
      newPassword: this._newPasswordController.text,
      confirmPassword: this._confirmPasswordController.text,
      currentPasswordRequired: !this._canResetPasswordWithoutCurrentPassword,
    });
    if (!draft.isValid) {
      this.setState(() =>
        this._applyPasswordChangeStatePatch(
          passwordChangeValidationFailed({
            error: draft.error!,
            changingPassword: this._changingPassword,
            notice: this._notice,
          }),
        ),
      );
      return;
    }

    this.setState(() => this._applyPasswordChangeStatePatch(passwordChangeStarted()));
    try {
      await this._settingsController.changePassword({
        currentPassword: draft.currentPassword!,
        newPassword: draft.newPassword!,
      });
      if (!this.mounted) return;
      this._currentPasswordController.clear();
      this._newPasswordController.clear();
      this._confirmPasswordController.clear();
      this.setState(() => this._applyPasswordChangeStatePatch(passwordChangeSucceeded()));
      unawaited(this._loadSessions());
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() => this._applyPasswordChangeStatePatch(passwordChangeFailed(e)));
    }
  }

  get _canResetPasswordWithoutCurrentPassword(): boolean {
    return (
      this._isManagingUser || (this.widget.passwordResetController?.isCurrentSessionAuthorizedFor(this._user?.email) ?? false)
    );
  }

  async _verifyEmailForPasswordReset(): Promise<void> {
    if (this._verifyingPasswordReset) return;
    const controller = this.widget.passwordResetController;
    const user = this._user;
    if (controller == null || user == null) {
      showFloatingErrorNotice('暂时无法使用忘记密码功能');
      return;
    }
    const login = user.username.trim().length > 0 ? user.username : (user.email ?? '');
    this.setState(() => {
      this._verifyingPasswordReset = true;
    });
    const resetToken = await verifyPasswordResetEmail({
      login,
      controller,
    });
    if (!this.mounted) return;
    if (resetToken == null) {
      this.setState(() => {
        this._verifyingPasswordReset = false;
      });
      return;
    }
    try {
      await controller.claimForCurrentSession(resetToken, { email: user.email ?? '' });
      if (!this.mounted) return;
      this.setState(() => {
        this._verifyingPasswordReset = false;
        this._currentPasswordController.clear();
      });
      showFloatingSuccessNotice('邮箱验证成功，可以直接设置新密码');
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._verifyingPasswordReset = false;
      });
      showFloatingErrorNotice(userFacingErrorMessage(error, { fallback: '邮箱验证失败' }));
    }
  }

  async _confirmDeleteAccount(): Promise<void> {
    const user = this._user;
    if (
      !this._settingsController.canDeleteAccount ||
      (this._isManagingUser && (user?.isSuperuser ?? false)) ||
      !account_display.canStartAccountDeletion({
        hasApi: this._settingsController.hasApi,
        user,
        deletingAccount: this._deletingAccount,
      })
    ) {
      return;
    }
    const targetUser = user!;
    const spec = account_display.accountDeletionConfirmationSpec(targetUser);
    const confirmed = await showDialog<boolean>(() => <_DeleteAccountDialog spec={spec} />);
    if (confirmed !== true || !this.mounted) return;
    this.setState(() => this._applyAccountDeletionStatePatch(accountDeletionStarted()));
    try {
      await this._settingsController.deleteMyAccount();
      if (!this.mounted) return;
      await this.widget.onAccountDeleted?.();
      if (!this.mounted) return;
      this.setState(() =>
        this._applyAccountDeletionStatePatch(
          accountDeletionFinished({
            securityError: this._securityError,
            notice: this._notice,
          }),
        ),
      );
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() => this._applyAccountDeletionStatePatch(accountDeletionFailed(e)));
    }
  }

  async _toggleManagedAccountSuspension(): Promise<void> {
    const user = this._user;
    if (!this._isManagingUser || user == null || user.isSuperuser || this._changingAccountSuspension) {
      return;
    }
    const suspending = !this._accountSuspended;
    const confirmed = await showDialog<boolean>((close) => (
      <DialogFrame
        title={suspending ? '确认封禁账号' : '确认解除封禁'}
        icon={suspending ? Icons.block_outlined : Icons.lock_open_outlined}
        adaptiveActions={[
          new ResponsiveDialogAction({ label: '取消', onPressed: () => close(false) }),
          new ResponsiveDialogAction({
            label: suspending ? '封禁账号' : '解除封禁',
            onPressed: () => close(true),
            tone: suspending ? ButtonTone.danger : ButtonTone.primary,
          }),
        ]}
      >
        <Text style={{ ...UiTypography.body, color: UiColors.textSecondary, lineHeight: 1.55 }}>
          {suspending
            ? `封禁后，${user.displayName} 的所有登录会话将立即失效，且无法登录。用户名、头像、资料和历史消息将保留。`
            : `解除封禁后，${user.displayName} 可以重新登录；此前已撤销的登录会话不会恢复。`}
        </Text>
      </DialogFrame>
    ));
    if (confirmed !== true || !this.mounted) return;

    this.setState(() => {
      this._changingAccountSuspension = true;
    });
    try {
      const updated = await this._settingsController.setManagedAccountSuspended(suspending);
      if (!this.mounted || updated == null) return;
      this.setState(() => {
        this._user = updated;
        this._changingAccountSuspension = false;
        this._securityError = null;
        this._notice = suspending ? '账号已封禁' : '账号已解除封禁';
        this._markFloatingNoticeEvent('notice', this._notice);
      });
      this.widget.onUserUpdated?.(updated);
      unawaited(this._loadSessions());
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._changingAccountSuspension = false;
        this._securityError = userFacingErrorMessage(error, {
          fallback: suspending ? '封禁账号失败' : '解除封禁失败',
        });
        this._markFloatingNoticeEvent('securityError', this._securityError);
      });
    }
  }

  get _platformFrameRateCap(): number {
    return !HostPlatform.isWeb && HostPlatform.isAndroid ? androidScreenShareFrameRateCap : desktopScreenShareFrameRateCap;
  }

  async _loadStoredAudioSettings(): Promise<void> {
    try {
      const stored = await this.widget.audioDeviceStore.read();
      if (!this.mounted) return;
      const patch = audioStoredVolumesApplied({
        inputVolume: stored.inputVolume,
        outputVolume: stored.outputVolume,
      });
      this._rememberOutputVolume(patch.outputVolume);
      this.setState(() => this._applyAudioVolumePatch(patch));
      this.widget.onVolumeChanged?.('audioinput', patch.inputVolume);
      this.widget.onVolumeChanged?.('audiooutput', patch.outputVolume);
      const platformFrameRateCap = this._platformFrameRateCap;
      this.setState(() => {
        this._screenShareMaxHeight = stored.screenShareMaxHeight;
        this._screenShareFrameRate = normalizedScreenShareFrameRate(stored.screenShareFrameRate, {
          maxFrameRate: platformFrameRateCap,
        });
      });
    } catch {
      // Stored audio settings are a local convenience; keep the defaults.
    }
  }

  async _setScreenShareMaxHeight(height: number): Promise<void> {
    const normalized = normalizedScreenShareMaxHeight(height);
    if (this._screenShareMaxHeight === normalized) return;
    this.setState(() => {
      this._screenShareMaxHeight = normalized;
    });
    this.widget.onScreenShareMaxHeightChanged?.(normalized);
    unawaited(this.widget.audioDeviceStore.writeScreenShareMaxHeight(normalized));
  }

  async _setScreenShareFrameRate(frameRate: number): Promise<void> {
    const normalized = normalizedScreenShareFrameRate(frameRate, {
      maxFrameRate: this._platformFrameRateCap,
    });
    if (this._screenShareFrameRate === normalized) return;
    this.setState(() => {
      this._screenShareFrameRate = normalized;
    });
    this.widget.onScreenShareFrameRateChanged?.(normalized);
    unawaited(this.widget.audioDeviceStore.writeScreenShareFrameRate(normalized));
  }

  async _loadDevices(): Promise<void> {
    this.setState(() =>
      this._applyAudioDeviceListPatch(
        audioDeviceListLoadStarted({
          inputs: this._audioInputs,
          outputs: this._audioOutputs,
          selectedInput: this._selectedInput,
          selectedOutput: this._selectedOutput,
        }),
      ),
    );
    try {
      if (this.widget.androidSystemService.isSupported) {
        try {
          await this.widget.androidSystemService.requestBluetoothConnectPermission();
        } catch {
          // Keep the built-in speaker/microphone list available even when the
          // user declines nearby-device access.
        }
        if (!this.mounted) return;
      }
      // macOS: LiveAudioDeviceService.enumerateDevices merges the system
      // input list in, so the picker is populated without joining a room.
      const devices = await this.widget.audioDeviceService.enumerateDevices();
      await this._applyDevices(devices);
    } catch (e) {
      if (!this.mounted) return;
      this.setState(() =>
        this._applyAudioDeviceListPatch(
          audioDeviceListLoadFailed({
            inputs: this._audioInputs,
            outputs: this._audioOutputs,
            selectedInput: this._selectedInput,
            selectedOutput: this._selectedOutput,
            failure: e,
          }),
        ),
      );
    }
  }

  async _applyDevices(devices: AudioDeviceInfo[]): Promise<void> {
    if (!this.mounted) return;
    const systemDefaultInputId = this._systemDefaultInputId;
    const systemDefaultOutputId = this._systemDefaultOutputId;
    let restored = new RestoredAudioDevices<AudioDeviceInfo>();
    try {
      restored = await restoreStoredAudioDevices(this.widget.audioDeviceStore, {
        audioDevices: this.widget.audioDeviceService,
        devices,
        systemDefaultInputId,
        systemDefaultOutputId,
      });
    } catch {
      // Device choices are a local convenience. If storage or OS routing fails,
      // keep rendering the current device list and let the user re-select.
    }
    if (!this.mounted) return;
    const systemDefaultInput =
      systemDefaultInputId == null
        ? null
        : storedAudioDeviceFrom(devices, {
            kind: 'audioinput',
            deviceId: systemDefaultInputId,
            kindOf: audioDeviceInfoKind,
            deviceIdOf: audioDeviceInfoId,
          });
    const systemDefaultOutput =
      systemDefaultOutputId == null
        ? null
        : storedAudioDeviceFrom(devices, {
            kind: 'audiooutput',
            deviceId: systemDefaultOutputId,
            kindOf: audioDeviceInfoKind,
            deviceIdOf: audioDeviceInfoId,
          });
    this.setState(() =>
      this._applyAudioDeviceListPatch(
        audioDeviceListApplied({
          devices,
          restoredInput: restored.input,
          restoredOutput: restored.output,
          hardwareInput: this.widget.audioDeviceService.selectedAudioInput,
          hardwareOutput: this.widget.audioDeviceService.selectedAudioOutput,
          currentInput: this._selectedInput,
          currentOutput: this._selectedOutput,
          kindOf: audioDeviceInfoKind,
          deviceIdOf: audioDeviceInfoId,
          error: this._error,
          systemDefaultInput,
          systemDefaultOutput,
        }),
      ),
    );
  }

  // Re-runs device selection when OS defaults change while Settings is open.
  // restoreStoredAudioDevices only falls back to the system defaults when the
  // user has not pinned a device, so explicit choices are preserved.
  async _onSystemDefaultAudioChanged(): Promise<void> {
    if (!this.mounted || !this._voiceInitialized) return;
    await this._applyDevices([...this._audioInputs, ...this._audioOutputs]);
  }

  async _selectInput(device: AudioDeviceInfo): Promise<void> {
    const wasTestingInput = this._testingInput;
    const wasTestingOutput = this._testingOutput;
    const didSelect = await this._selectDevice(
      device,
      () => this.widget.audioDeviceService.selectAudioInput(device),
      () =>
        this.widget.audioDeviceStore.writeInputDevicePreference({
          deviceId: device.deviceId,
          label: device.label,
          groupId: device.groupId,
        }),
    );
    if (!didSelect) return;
    const effects = audioInputDeviceSelectedEffects({
      wasTestingInput,
      wasTestingOutput,
    });
    if (effects.restartInputTest) await this._restartInputTest();
    if (effects.restartOutputTest) await this._restartOutputTest();
  }

  async _selectOutput(device: AudioDeviceInfo): Promise<void> {
    const didSelect = await this._selectDevice(
      device,
      () => this.widget.audioDeviceService.selectAudioOutput(device),
      () =>
        this.widget.audioDeviceStore.writeOutputDevicePreference({
          deviceId: device.deviceId,
          label: device.label,
          groupId: device.groupId,
        }),
    );
    if (!didSelect) return;
    const effects = audioOutputDeviceSelectedEffects();
    if (effects.restartOutputTest) await this._restartOutputTest();
  }

  async _selectDevice(
    device: AudioDeviceInfo,
    select: () => Promise<void>,
    rememberSelection: () => Promise<void>,
  ): Promise<boolean> {
    if (!canStartAudioDeviceSelection(this._busyDeviceId)) return false;
    this.setState(() =>
      this._applyAudioDeviceSelectionPatch(
        audioDeviceSelectionStarted({
          device,
          selectedInput: this._selectedInput,
          selectedOutput: this._selectedOutput,
          kindOf: audioDeviceInfoKind,
          deviceIdOf: audioDeviceInfoId,
        }),
      ),
    );
    let didSelect = false;
    try {
      await select();
      let storageError: unknown = null;
      try {
        await rememberSelection();
      } catch (e) {
        storageError = e;
      }
      if (!this.mounted) return false;
      this.setState(() =>
        this._applyAudioDeviceSelectionPatch(
          audioDeviceSelectionSucceeded({
            device,
            selectedInput: this._selectedInput,
            selectedOutput: this._selectedOutput,
            kindOf: audioDeviceInfoKind,
            storageFailure: storageError,
          }),
        ),
      );
      this.widget.onDeviceSelected?.(device.kind, device.deviceId);
      didSelect = true;
    } catch (e) {
      if (this.mounted) {
        this.setState(() =>
          this._applyAudioDeviceSelectionPatch(
            audioDeviceSelectionFailed({
              selectedInput: this._selectedInput,
              selectedOutput: this._selectedOutput,
              failure: e,
            }),
          ),
        );
      }
    }
    return didSelect;
  }

  async _setInputVolume(volume: number): Promise<void> {
    const normalized = normalizedAudioVolume(volume);
    const patch = audioInputVolumeChanged({
      inputVolume: normalized,
      outputVolume: this._outputVolume,
      restoreOutputVolume: this._restoredOutputVolume(),
    });
    await this._setAudioVolumes(patch);
  }

  async _setOutputVolume(volume: number): Promise<void> {
    const normalized = normalizedAudioVolume(volume);
    if (normalized === 0) {
      this._rememberOutputVolume(this._outputVolume);
    } else {
      this._rememberOutputVolume(normalized);
    }
    const patch = audioOutputVolumeChanged({
      inputVolume: this._inputVolume,
      outputVolume: normalized,
    });
    await this._setAudioVolumes(patch);
  }

  async _setAudioVolumes(patch: AudioVolumePatch): Promise<void> {
    const previousInputVolume = this._inputVolume;
    const previousOutputVolume = this._outputVolume;
    if (patch.inputVolume === previousInputVolume && patch.outputVolume === previousOutputVolume) {
      return;
    }
    this.setState(() => this._applyAudioVolumePatch(patch));
    if (patch.outputVolume > 0) this._rememberOutputVolume(patch.outputVolume);

    const inputTest = this._inputTest;
    if (patch.inputVolume !== previousInputVolume) {
      this.widget.onVolumeChanged?.('audioinput', patch.inputVolume);
      unawaited(this.widget.audioDeviceStore.writeInputVolume(patch.inputVolume));
      try {
        await inputTest?.setCaptureVolume(patch.inputVolume);
      } catch {
        // The meter keeps scaling in app state.
      }
    }

    const outputTest = this._outputTest;
    if (patch.outputVolume !== previousOutputVolume) {
      this.widget.onVolumeChanged?.('audiooutput', patch.outputVolume);
      unawaited(this.widget.audioDeviceStore.writeOutputVolume(patch.outputVolume));
      try {
        await outputTest?.setPlaybackVolume(patch.outputVolume);
      } catch {
        // The test tone keeps its previous volume.
      }
    }
  }

  async _toggleInputTest(): Promise<void> {
    if (this._testingInput) {
      await this._stopInputTest();
    } else {
      await this._startInputTest();
    }
  }

  async _toggleOutputTest(): Promise<void> {
    if (this._testingOutput) {
      await this._stopOutputTest();
    } else {
      await this._startOutputTest();
    }
  }

  async _restartInputTest(): Promise<void> {
    await this._stopInputTest();
    if (this.mounted) await this._startInputTest();
  }

  async _restartOutputTest(): Promise<void> {
    await this._stopOutputTest();
    if (this.mounted) await this._startOutputTest();
  }

  async _startInputTest(): Promise<void> {
    if (this._testingInput) return;
    this.setState(() =>
      this._applyAudioTestStatePatch(
        audioInputTestStarted({
          testingOutput: this._testingOutput,
          outputLevel: this._outputLevel,
        }),
      ),
    );
    let handle: AudioTestHandle | null = null;
    try {
      handle = await this._audioTestService.startInputTest({
        inputDeviceId: this._selectedInput?.deviceId ?? null,
        volume: this._inputVolume,
        onLevel: (level) => {
          if (!this.mounted) return;
          this.setState(() =>
            this._applyAudioTestStatePatch(
              audioInputLevelChanged({
                level,
                inputVolume: this._inputVolume,
                testingOutput: this._testingOutput,
                outputLevel: this._outputLevel,
                error: this._error,
              }),
            ),
          );
        },
      });
      if (!this.mounted) {
        await handle.dispose();
        return;
      }
      this._inputTest = handle;
    } catch (e) {
      await handle?.dispose();
      if (!this.mounted) return;
      this.setState(() =>
        this._applyAudioTestStatePatch(
          audioInputTestFailed({
            testingOutput: this._testingOutput,
            outputLevel: this._outputLevel,
            failure: e,
          }),
        ),
      );
    }
  }

  async _stopInputTest(opts: { updateState?: boolean } = {}): Promise<void> {
    const { updateState = true } = opts;
    const inputTest = this._inputTest;
    this._inputTest = null;
    await inputTest?.dispose();
    if (updateState && this.mounted) {
      this.setState(() =>
        this._applyAudioTestStatePatch(
          audioInputTestStopped({
            testingOutput: this._testingOutput,
            outputLevel: this._outputLevel,
            error: this._error,
          }),
        ),
      );
    }
  }

  async _startOutputTest(): Promise<void> {
    if (this._testingOutput) return;
    this.setState(() =>
      this._applyAudioTestStatePatch(
        audioOutputTestStarted({
          testingInput: this._testingInput,
          inputLevel: this._inputLevel,
        }),
      ),
    );
    let handle: AudioTestHandle | null = null;
    try {
      handle = await this._audioTestService.startOutputTest({
        volume: this._outputVolume,
        onLevel: (level) => {
          if (!this.mounted) return;
          this.setState(() =>
            this._applyAudioTestStatePatch(
              audioOutputLevelChanged({
                level,
                outputVolume: this._outputVolume,
                testingInput: this._testingInput,
                inputLevel: this._inputLevel,
                error: this._error,
              }),
            ),
          );
        },
      });
      if (!this.mounted) {
        await handle.dispose();
        return;
      }
      this._outputTest = handle;
    } catch (e) {
      await handle?.dispose();
      if (!this.mounted) return;
      this.setState(() =>
        this._applyAudioTestStatePatch(
          audioOutputTestFailed({
            testingInput: this._testingInput,
            inputLevel: this._inputLevel,
            failure: e,
          }),
        ),
      );
    }
  }

  async _stopOutputTest(opts: { updateState?: boolean } = {}): Promise<void> {
    const { updateState = true } = opts;
    const outputTest = this._outputTest;
    this._outputTest = null;
    await outputTest?.dispose();
    if (updateState && this.mounted) {
      this.setState(() =>
        this._applyAudioTestStatePatch(
          audioOutputTestStopped({
            testingInput: this._testingInput,
            inputLevel: this._inputLevel,
            error: this._error,
          }),
        ),
      );
    }
  }

  get _isRefreshing(): boolean {
    return settingsSectionRefreshing({
      section: this._section,
      loadingAccount: this._loadingAccount,
      loadingPreferences: this._loadingCloseBehavior,
      loadingStickers: this._loadingStickers,
      loadingSessions: this._loadingSessions,
      loadingVoice: this._loading,
      loadingPlaylists: this._loadingPlaylists,
      loadingAbout: this._loadingAbout || this._checkingAppVersion,
    });
  }

  _selectSection(section: SettingsSection): void {
    const patch = settingsSectionSelected({
      section,
      sessionsEmpty: this._sessions.length === 0,
      loadingSessions: this._loadingSessions,
    });
    this.setState(() => {
      this._applySettingsSectionPatch(patch);
    });
    if (patch.shouldLoadStickers) {
      unawaited(this._ensureStickersLoaded());
    }
    if (patch.shouldLoadSessions) {
      unawaited(this._loadSessions());
    }
    if (patch.shouldInitializeVoice) {
      unawaited(this._ensureVoiceInitialized());
    }
  }

  _sectionSummaries(): Map<SettingsSection, string> {
    const user = this._user;
    let language: string;
    switch (this._language) {
      case 'zh-Hant':
        language = '繁體中文';
        break;
      case 'en':
        language = 'English';
        break;
      default:
        language = '简体中文';
    }
    const email = this._normalizedAccountEmail;
    const stickerCount = this._allStickerItems().length;
    return new Map<SettingsSection, string>([
      [SettingsSection.profile, user == null ? '未登录' : `@${user.username}`],
      [
        SettingsSection.preferences,
        this.widget.androidSystemService.isSupported ? language : `${language} · ${closeBehaviorLabel(this._closeBehavior)}`,
      ],
      [SettingsSection.security, email.length === 0 ? '未绑定邮箱' : this._accountEmailVerified ? '邮箱已验证' : '邮箱待验证'],
      [
        SettingsSection.voice,
        `输入 ${audioVolumePercentText(this._inputVolume)} · ` + `输出 ${audioVolumePercentText(this._outputVolume)}`,
      ],
      [SettingsSection.stickers, stickerCount === 0 ? '上传与整理表情' : `${stickerCount} 个表情`],
      [SettingsSection.playlists, '整理个人歌单'],
      [SettingsSection.about, appVersionNumberLabel(this.widget.appVersion)],
    ]);
  }

  _buildSearchResults(query: string): ReactNode {
    const android = this.widget.androidSystemService.isSupported;
    const results = settingsSearchMatches(query, { android });
    if (results.length === 0) {
      return <SettingsList>{[<_SettingsEmptyState key="empty" text="没有匹配的设置" />]}</SettingsList>;
    }
    return (
      <SettingsList spacing={6}>
        {results.map((entry, i) => (
          <_SettingsSearchResultRow
            key={`settings-search-result-${i}`}
            testId={`settings-search-result-${i}`}
            entry={entry}
            query={query.trim()}
            onPressed={() => {
              this._settingsSearchController.clear();
              this._selectSection(entry.section);
            }}
          />
        ))}
      </SettingsList>
    );
  }

  _buildSectionContent(): ReactNode {
    switch (this._section) {
      case SettingsSection.profile:
        return this._buildProfileContent();
      case SettingsSection.preferences:
        return this._buildPreferencesContent();
      case SettingsSection.stickers:
        return this._buildStickersContent();
      case SettingsSection.playlists:
        return this._buildMusicPlaylistsContent();
      case SettingsSection.security:
        return this._buildSecurityContent();
      case SettingsSection.voice:
        return this._buildVoiceContent();
      case SettingsSection.about:
        return this._buildAboutContent();
    }
  }

  _buildMusicPlaylistsContent(): ReactNode {
    const api = this.widget.api;
    return (
      <MusicPlaylistsPanel
        controller={this._musicPlaylistsController}
        initialPlaylistId={this.widget.initialMusicPlaylistId}
        previewApi={_isMusicTrackPreviewApi(api) ? api : null}
        previewPlatformFactory={this.widget.musicTrackPreviewPlatformFactory}
        shareApi={this._isManagingUser ? null : api}
        currentUser={this._user ?? this.widget.currentUser}
        reloadToken={this._playlistReloadToken}
        unavailableMessage={this._isManagingUser ? '管理其他账号时不能编辑个人歌单' : '我的歌单需要登录后从服务端读取'}
        onLoadingChanged={(loading: boolean) => {
          if (!this.mounted || loading === this._loadingPlaylists) return;
          this.setState(() => {
            this._loadingPlaylists = loading;
          });
        }}
      />
    );
  }

  _buildStickersContent(): ReactNode {
    const unavailable = !this._settingsController.hasApi;
    const items = this._filteredStickerItems();
    const totalCount = this._allStickerItems().length;
    const selectionNumbers = this._stickerSelectionNumbers();
    const busy = this._stickerManagementBusy;
    const managing = this._managingStickers;
    const selectedStickerIds = this._selectedStickerIds;
    const allVisibleSelected = stickerAllVisibleSelected({
      selectedStickerIds,
      visibleItems: items,
    });
    const canPinSelection =
      canStartStickerSelectionAction({
        busy,
        selectedStickerIds,
      }) &&
      sticker_ordering.stickerSelectionWouldChangePinnedOrder({
        packs: this._stickerPacks,
        selectedStickerIds,
        orderByPack: this._stickerOrderDrafts,
      });
    let stickerBody: ReactNode;
    if (this._loadingStickers && this._stickerPacks.length === 0) {
      stickerBody = (
        <Center style={{ flex: '1 1 0' }}>
          <CircularProgressIndicator color={_cyan} />
        </Center>
      );
    } else if (totalCount === 0) {
      // Align(topCenter) > SizedBox(width: double.infinity).
      stickerBody = <_SettingsEmptyState text="暂无表情，点击本地上传会自动创建" />;
    } else if (items.length === 0) {
      stickerBody = <_SettingsEmptyState text="没有匹配的表情" />;
    } else {
      stickerBody = (
        <StickerGrid
          items={items}
          managing={managing}
          selectionNumbers={selectionNumbers}
          busy={busy}
          scrollable
          onTap={(item) => {
            if (this._managingStickers) {
              this._toggleStickerSelection(item.sticker.id);
            } else {
              this._previewSticker(item);
            }
          }}
        />
      );
    }

    if (unavailable) {
      return <SettingsList>{[<_SettingsEmptyState key="unavailable" text="表情包需要登录后从服务端读取" />]}</SettingsList>;
    }
    const filterActive = this._stickerFilterActive;
    const actions: StickerActionGridEntry[] = [
      new StickerActionGridEntry({
        label: managing ? '删除' : '本地上传',
        button: (
          <Button
            onPressed={
              canStartStickerPrimaryAction({ busy })
                ? managing
                  ? () => unawaited(this._deleteSelectedStickers())
                  : () => unawaited(this._pickAndUploadStickers())
                : null
            }
            loading={managing ? this._deletingStickers : this._uploadingStickers}
            tone={managing ? ButtonTone.danger : ButtonTone.primary}
            icon={<Icon icon={managing ? Icons.delete_outline : Icons.upload_file} />}
            width={Infinity}
          >
            {managing ? '删除' : '本地上传'}
          </Button>
        ),
      }),
      new StickerActionGridEntry({
        label: managing ? '取消管理' : '批量管理',
        button: (
          <Button
            onPressed={canUseStickerManagementControl({ busy }) ? () => this._toggleStickerManageMode() : null}
            selected={managing}
            tone={managing ? ButtonTone.primary : ButtonTone.neutral}
            icon={<Icon icon={managing ? Icons.close : Icons.checklist_rtl} />}
            width={Infinity}
          >
            {managing ? '取消管理' : '批量管理'}
          </Button>
        ),
      }),
      new StickerActionGridEntry({
        label: '筛选',
        button: (
          <Button
            onPressed={canUseStickerManagementControl({ busy }) ? () => unawaited(this._openStickerFilter()) : null}
            selected={filterActive}
            tone={filterActive ? ButtonTone.primary : ButtonTone.neutral}
            icon={<Icon icon={Icons.filter_alt_outlined} />}
            width={Infinity}
          >
            筛选
          </Button>
        ),
      }),
    ];
    if (managing) {
      const visibleSelectionText = stickerVisibleSelectionButtonText({
        selectedStickerIds,
        visibleItems: items,
      });
      actions.push(
        new StickerActionGridEntry({
          label: '下载',
          button: (
            <Button
              onPressed={
                canStartStickerSelectionAction({ busy, selectedStickerIds })
                  ? () => unawaited(this._downloadSelectedStickers())
                  : null
              }
              loading={this._downloadingStickers}
              icon={<Icon icon={Icons.download_outlined} />}
              width={Infinity}
            >
              下载
            </Button>
          ),
        }),
        new StickerActionGridEntry({
          label: '置顶',
          button: (
            <Button
              onPressed={canPinSelection ? () => unawaited(this._pinSelectedStickers()) : null}
              loading={this._savingStickerOrder}
              icon={<Icon icon={Icons.vertical_align_top} />}
              width={Infinity}
            >
              置顶
            </Button>
          ),
        }),
        new StickerActionGridEntry({
          label: visibleSelectionText,
          button: (
            <Button
              onPressed={
                canSelectVisibleStickers({ busy, visibleItems: items }) ? () => this._selectAllVisibleStickers(items) : null
              }
              selected={allVisibleSelected}
              icon={<Icon icon={allVisibleSelected ? Icons.check_box : Icons.check_box_outline_blank} />}
              width={Infinity}
            >
              {visibleSelectionText}
            </Button>
          ),
        }),
      );
    }
    return (
      <SettingsFixedHeaderCard
        title="表情包管理"
        spacing={10}
        trailing={
          <Text softWrap={false} style={{ color: _textMuted, fontSize: 12, fontWeight: 600 }}>
            {stickerManagementCountText({
              filterActive,
              visibleCount: items.length,
              totalCount,
            })}
          </Text>
        }
        headerChildren={[<StickerActionGrid key="actions" actions={actions} />]}
        body={stickerBody}
      />
    );
  }

  _buildPreferencesContent(): ReactNode {
    const unavailable = !this._settingsController.hasApi || this._user == null;
    const androidNotifications = this.widget.androidSystemService.isSupported && !this._isManagingUser;
    const children: ReactNode[] = [];
    if (!this.widget.androidSystemService.isSupported) {
      children.push(
        <_SettingsGroup
          key="close-behavior"
          title="关闭方式"
          trailing={
            this._savingCloseBehavior || this._loadingCloseBehavior ? (
              <CircularProgressIndicator size={16} strokeWidth={2} color={_cyan} />
            ) : null
          }
        >
          <_SegmentedSetting
            testId="settings-close-behavior-segmented"
            label="关闭方式"
            value={closeBehaviorStorageValue(this._closeBehavior)}
            options={Object.values(CloseBehavior).map(
              (behavior) =>
                new _SegmentOption({
                  value: closeBehaviorStorageValue(behavior),
                  label: closeBehaviorLabel(behavior),
                }),
            )}
            onChanged={(value) => this._setCloseBehavior(value)}
            enabled={!this._isManagingUser}
          />
          <SizedBox height={10} />
          <Text style={{ color: _textSecondary, fontSize: 13, lineHeight: 1.45 }}>
            {this._isManagingUser ? '该设置仅保存在用户设备，无法远程读取' : closeBehaviorDescription(this._closeBehavior)}
          </Text>
        </_SettingsGroup>,
      );
    }
    if (androidNotifications) {
      children.push(
        <_SettingsGroup
          key="android-notifications"
          title="系统通知"
          trailing={
            this._savingAndroidNotifications ? <CircularProgressIndicator size={16} strokeWidth={2} color={_cyan} /> : null
          }
        >
          <_ToggleSetting
            label="允许房间消息通知"
            value={this._androidNotificationPreferenceEnabled}
            enabled={!this._savingAndroidNotifications}
            onChanged={(value) => unawaited(this._setAndroidNotificationsEnabled(value))}
          />
          <SizedBox height={10} />
          <Text style={{ color: _textSecondary, fontSize: 13, lineHeight: 1.45 }}>
            {!this._androidNotificationPreferenceEnabled
              ? '已在 Gang Chat 中关闭通知'
              : this._androidNotificationsAvailable
                ? '通知已开启；房间设置为“全部”时，后台消息会显示内容和数字角标'
                : '系统尚未允许通知，请在 Android 设置中开启'}
          </Text>
          <SizedBox height={12} />
          {/* Align(centerLeft) */}
          <_AlignStart>
            <Button
              onPressed={() => unawaited(this._openAndroidNotificationSettings())}
              icon={<Icon icon={Icons.notifications_outlined} />}
            >
              系统通知设置
            </Button>
          </_AlignStart>
        </_SettingsGroup>,
      );
    }
    if (unavailable) {
      children.push(<_SettingsEmptyState key="language-unavailable" text="语言偏好需要登录后从服务端读取" />);
    } else {
      children.push(
        <_SettingsGroup key="language" title="语言切换">
          <_SegmentedSetting
            testId="settings-language-segmented"
            label="语言"
            value={this._language}
            options={[
              new _SegmentOption({ value: 'zh-Hans', label: '简体中文' }),
              new _SegmentOption({ value: 'zh-Hant', label: '繁體中文' }),
              new _SegmentOption({ value: 'en', label: 'English' }),
            ]}
            onChanged={(value) =>
              this.setState(() => {
                this._language = value;
              })
            }
          />
        </_SettingsGroup>,
      );
    }
    children.push(
      <_AlignEnd key="save">
        <Button
          onPressed={this._savingAccount || this._savingCloseBehavior ? null : () => unawaited(this._savePreferences())}
          loading={this._savingAccount || this._savingCloseBehavior}
          icon={<Icon icon={Icons.save_outlined} />}
          tone={ButtonTone.primary}
        >
          保存偏好设置
        </Button>
      </_AlignEnd>,
    );
    return <SettingsList>{children}</SettingsList>;
  }

  async _openProfileAvatarPreview(imageUrl: string | null): Promise<void> {
    const url = imageUrl?.trim();
    const opener = this.widget.stickerImagePreviewOpener;
    if (url == null || url.length === 0 || opener == null) return;
    const displayName = this._displayNameController.text.trim();
    const username = this._user?.username.trim() ?? '';
    const stem = displayName.length > 0 ? displayName : username.length > 0 ? username : 'avatar';
    await opener({
      imageUrl: url,
      suggestedName: `${stem}-avatar.png`,
      forceSquare: true,
    });
  }

  _buildProfileContent(): ReactNode {
    const user = this._user;
    if (!this._settingsController.hasApi || user == null) {
      return <SettingsList>{[<_SettingsEmptyState key="unavailable" text="账号资料需要登录后从服务端读取" />]}</SettingsList>;
    }
    const avatarPreviewUrl = this._appConfig.resolveAssetUrl(
      account_display.accountAvatarPreviewPath({
        clearUploadedAvatar: this._clearUploadedAvatar,
        pendingAvatarUrl: this._pendingAvatarUrl,
        currentAvatarUrl: user.avatarUrl,
      }),
    );
    const usernameValidationError = loginUsernameValidationError(this._usernameController.text);
    const usernameEditable = this._isManagingUser || account_display.canEditUsername(user);
    const usernameChanged = this._usernameController.text.trim() !== user.username;
    const usernameAvailabilityApplies =
      usernameChanged &&
      usernameValidationError == null &&
      this._usernameAvailabilityQuery === this._usernameController.text.trim();
    const usernameEffectiveError =
      usernameValidationError ?? (usernameAvailabilityApplies ? this._usernameAvailabilityError : null);
    const usernameChecking = usernameAvailabilityApplies && this._checkingUsernameAvailability;
    return (
      <SettingsList>
        {[
          <_SettingsGroup key="identity" title="账号标识">
            <_CopyableField label="个人 UID" value={user.uid} />
            <SizedBox height={14} />
            <_LabeledTextField
              label="登录用户名"
              controller={this._usernameController}
              enabled={usernameEditable}
              helperText={account_display.usernameHelperText(user)}
              suffix={
                usernameChanged ? (
                  <_UsernameValidityIndicator
                    error={usernameEffectiveError}
                    checking={usernameChecking}
                    enabled={usernameEditable}
                  />
                ) : null
              }
              onChanged={(value) => this._onLoginUsernameChanged(value)}
            />
          </_SettingsGroup>,
          <_SettingsGroup key="profile" title="默认资料">
            <_LabeledTextField label="用户名" controller={this._displayNameController} helperText="没有房间个人资料覆盖时展示。" />
            <SizedBox height={14} />
            <_SegmentedSetting
              testId="settings-gender-segmented"
              label="性别"
              value={this._gender}
              options={[
                new _SegmentOption({ value: 'male', label: '男' }),
                new _SegmentOption({ value: 'female', label: '女' }),
                new _SegmentOption({ value: 'secret', label: '保密' }),
              ]}
              onChanged={(value) =>
                this.setState(() =>
                  this._applyAccountEditableFieldsPatch(
                    accountGenderChanged({
                      gender: value,
                      emailPublic: this._emailPublic,
                      phoneNumberPublic: this._phonePublic,
                    }),
                  ),
                )
              }
            />
            <SizedBox height={14} />
            <AvatarPicker
              label="头像"
              displayName={this._displayNameController.text}
              imageUrl={avatarPreviewUrl}
              defaultAvatarKey={this._defaultAvatarKey}
              usingPreset={avatarPreviewUrl == null}
              uploading={this._uploadingAvatar}
              enabled={!this._uploadingAvatar}
              onUpload={() => unawaited(this._pickAndUploadAvatar())}
              onPresetSelected={(value) => this._selectDefaultAvatarKey(value)}
              onImagePreview={
                this.widget.stickerImagePreviewOpener == null
                  ? null
                  : () => unawaited(this._openProfileAvatarPreview(avatarPreviewUrl))
              }
              uploadLabel="上传头像"
            />
            <SizedBox height={14} />
            <_LabeledTextField label="签名" controller={this._bioController} maxLines={4} helperText="用于个人资料面板展示。" />
          </_SettingsGroup>,
          <_AlignEnd key="save">
            <Button
              onPressed={this._savingProfile || this._savingAccount ? null : () => unawaited(this._saveProfile())}
              loading={this._savingProfile}
              icon={<Icon icon={Icons.save_outlined} />}
              tone={ButtonTone.primary}
            >
              保存用户资料
            </Button>
          </_AlignEnd>,
        ]}
      </SettingsList>
    );
  }

  _buildSecurityContent(): ReactNode {
    const user = this._user;
    if (!this._settingsController.hasApi || user == null) {
      return <SettingsList>{[<_SettingsEmptyState key="unavailable" text="安全设置需要登录后从服务端读取" />]}</SettingsList>;
    }
    const emailEmpty = this._normalizedAccountEmail.length === 0;
    const managedSuperuserPasswordBlocked = this._isManagingUser && user.isSuperuser;
    const children: ReactNode[] = [
      <_SettingsGroup key="bindings" title="绑定信息">
        <_LabeledTextField
          testId="settings-email-input"
          label="邮箱绑定"
          controller={this._emailController}
          keyboardType={TextInputType.emailAddress}
          helperText="用于登录、账号找回和安全通知。"
          suffix={
            emailEmpty ? null : this._checkingEmailAvailability ? (
              <_EmailVerificationStatusIndicator checking />
            ) : this._accountEmailVerified ? (
              <_EmailVerificationStatusIndicator checking={false} />
            ) : (
              <EmailVerificationInputAction
                actionKey="settings-email-verification-button"
                label="验证"
                semanticsLabel="验证邮箱"
                enabled={!this._savingAccount}
                onPressed={() => unawaited(this._showAccountEmailVerification())}
              />
            )
          }
          onChanged={(value) => this._onAccountEmailChanged(value)}
        />
        <SizedBox height={10} />
        <_ToggleSetting
          label="公开邮箱"
          value={this._emailPublic}
          onChanged={(value) =>
            this.setState(() =>
              this._applyAccountEditableFieldsPatch(
                accountEmailPublicChanged({
                  gender: this._gender,
                  emailPublic: value,
                  phoneNumberPublic: this._phonePublic,
                }),
              ),
            )
          }
        />
        <SizedBox height={14} />
        <_LabeledTextField
          label="手机号绑定"
          controller={this._phoneController}
          keyboardType={TextInputType.phone}
          helperText="用于账号找回和安全通知，留空表示解绑。"
        />
        <SizedBox height={10} />
        <_ToggleSetting
          label="公开手机号"
          value={this._phonePublic}
          onChanged={(value) =>
            this.setState(() =>
              this._applyAccountEditableFieldsPatch(
                accountPhoneNumberPublicChanged({
                  gender: this._gender,
                  emailPublic: this._emailPublic,
                  phoneNumberPublic: value,
                }),
              ),
            )
          }
        />
        <SizedBox height={14} />
        <_AlignEnd>
          <Button
            onPressed={this._savingAccount ? null : () => unawaited(this._saveAccount())}
            loading={this._savingAccount}
            icon={<Icon icon={Icons.save_outlined} />}
            tone={ButtonTone.primary}
          >
            保存绑定信息
          </Button>
        </_AlignEnd>
      </_SettingsGroup>,
      <_SettingsGroup key="password" title="重置密码">
        {!this._canResetPasswordWithoutCurrentPassword ? (
          <>
            <_LabeledTextField
              label="当前密码"
              controller={this._currentPasswordController}
              obscureText={this._obscureCurrentPassword}
              onTogglePasswordVisibility={() => this._togglePasswordVisibility(PasswordVisibilityField.current)}
            />
            <SizedBox height={12} />
          </>
        ) : null}
        <_LabeledTextField
          label="新密码"
          controller={this._newPasswordController}
          obscureText={this._obscureNewPassword}
          onTogglePasswordVisibility={() => this._togglePasswordVisibility(PasswordVisibilityField.newPassword)}
        />
        <SizedBox height={12} />
        <_LabeledTextField
          label="确认新密码"
          controller={this._confirmPasswordController}
          obscureText={this._obscureConfirmPassword}
          onTogglePasswordVisibility={() => this._togglePasswordVisibility(PasswordVisibilityField.confirm)}
        />
        <SizedBox height={14} />
        <ResponsiveDialogActionBar
          expanded
          actions={[
            new ResponsiveDialogAction({
              label: '忘记密码',
              icon: Icons.help_outline,
              loading: this._verifyingPasswordReset,
              onPressed:
                this._verifyingPasswordReset || this._isManagingUser
                  ? null
                  : () => unawaited(this._verifyEmailForPasswordReset()),
            }),
            new ResponsiveDialogAction({
              label: '更新密码',
              icon: Icons.lock_reset,
              tone: ButtonTone.primary,
              loading: this._changingPassword,
              onPressed:
                this._changingPassword || managedSuperuserPasswordBlocked ? null : () => unawaited(this._changePassword()),
            }),
          ]}
        />
      </_SettingsGroup>,
      <_SettingsGroup
        key="sessions"
        title="账号活动"
        trailing={
          <ButtonIcon
            tooltip="刷新账号活动"
            onPressed={this._loadingSessions ? null : () => unawaited(this._loadSessions())}
            icon={<Icon icon={Icons.refresh} />}
            size={30}
          />
        }
      >
        <_ReadOnlyLine label="账号创建时间" value={account_display.formatDateTime(user.createdAt)} />
        <SizedBox height={14} />
        <_SessionList sessions={this._sessions} loading={this._loadingSessions} />
      </_SettingsGroup>,
    ];
    if (this._isManagingUser) {
      const suspended = this._accountSuspended;
      children.push(
        <_SettingsGroup key="status" title="账号状态" danger={suspended}>
          <Text
            style={{
              ...UiTypography.body,
              color: suspended ? UiColors.danger : UiColors.textSecondary,
              fontWeight: 600,
            }}
          >
            {suspended ? '账号封禁中' : '账号状态正常'}
          </Text>
          <SizedBox height={6} />
          <Text style={{ ...UiTypography.body, color: UiColors.textSecondary, lineHeight: 1.5 }}>
            {suspended
              ? '该账号当前无法登录；用户名、头像、资料和历史消息仍正常保留。'
              : '封禁会立即撤销该账号的所有登录会话，并阻止其再次登录。'}
          </Text>
          <SizedBox height={14} />
          <_AlignEnd>
            <Button
              onPressed={
                user.isSuperuser || this._changingAccountSuspension
                  ? null
                  : () => unawaited(this._toggleManagedAccountSuspension())
              }
              loading={this._changingAccountSuspension}
              icon={<Icon icon={suspended ? Icons.lock_open_outlined : Icons.block_outlined} />}
              tone={suspended ? ButtonTone.primary : ButtonTone.danger}
            >
              {suspended ? '解除封禁' : '封禁账号'}
            </Button>
          </_AlignEnd>
        </_SettingsGroup>,
      );
    }
    children.push(
      <_SettingsGroup key="delete" title="注销账号" danger>
        <Text style={{ color: _textSecondary, fontSize: 13, lineHeight: 1.5 }}>
          {account_display.accountDeletionDescription(user)}
        </Text>
        <SizedBox height={14} />
        <_AlignEnd>
          <Button
            onPressed={
              this._settingsController.canDeleteAccount &&
              !(this._isManagingUser && user.isSuperuser) &&
              account_display.canStartAccountDeletion({
                hasApi: this._settingsController.hasApi,
                user,
                deletingAccount: this._deletingAccount,
              })
                ? () => unawaited(this._confirmDeleteAccount())
                : null
            }
            loading={this._deletingAccount}
            icon={<Icon icon={Icons.delete_outline} />}
            tone={ButtonTone.danger}
          >
            注销账号
          </Button>
        </_AlignEnd>
      </_SettingsGroup>,
    );
    return <SettingsList>{children}</SettingsList>;
  }

  _togglePasswordVisibility(field: PasswordVisibilityField): void {
    this.setState(() =>
      this._applyPasswordVisibilityPatch(
        passwordVisibilityToggled({
          field,
          obscureCurrentPassword: this._obscureCurrentPassword,
          obscureNewPassword: this._obscureNewPassword,
          obscureConfirmPassword: this._obscureConfirmPassword,
        }),
      ),
    );
  }

  _buildAboutContent(): ReactNode {
    return (
      <SettingsList>
        {[
          <_SettingsGroup key="version" title="版本信息">
            <_CopyableField label="版本编号" value={appVersionNumberLabel(this.widget.appVersion)} />
            <SizedBox height={14} />
            <_CopyableField label="发行时间" value={officialVersionTimeLabel(gangChatClientReleaseTimestamp)} />
            <SizedBox height={14} />
            <_CopyableField label="上次更新时间" value={this._lastUpdateDate} />
            <SizedBox height={14} />
            <_ToggleSetting
              label="自动提示更新"
              value={this._autoPromptUpdates}
              enabled={!this._isManagingUser}
              onChanged={(value) => unawaited(this._setAutoUpdatePrompt(value))}
            />
            {this._isManagingUser ? (
              <>
                <SizedBox height={10} />
                <Text style={{ color: _textSecondary, fontSize: 13, lineHeight: 1.4 }}>该设置仅保存在用户设备，无法远程读取</Text>
              </>
            ) : null}
            <SizedBox height={14} />
            <Button
              onPressed={this._checkingAppVersion ? null : () => unawaited(this._checkAppVersion())}
              loading={this._checkingAppVersion}
              tone={ButtonTone.primary}
              width={Infinity}
            >
              检查更新
            </Button>
          </_SettingsGroup>,
          <_SettingsGroup key="feedback" title="意见反馈">
            <Button
              onPressed={this._openingFeedbackMail ? null : () => unawaited(this._openFeedbackMail())}
              loading={this._openingFeedbackMail}
              tone={ButtonTone.primary}
              width={Infinity}
            >
              意见反馈
            </Button>
          </_SettingsGroup>,
        ]}
      </SettingsList>
    );
  }

  _buildAppUpdateContent(update: AvailableAppUpdate): ReactNode {
    return (
      <AppUpdatePage
        update={update}
        checking={this._checkingAppVersion}
        downloading={this._downloadingAppUpdate}
        downloadedBytes={this._updateDownloadedBytes}
        downloadTotalBytes={this._updateDownloadTotalBytes}
        error={this._updateDownloadError}
        onBack={() => this._closeAppUpdatePage()}
        onRefresh={() => unawaited(this._checkAppVersion())}
        onIgnoreVersion={() => unawaited(this._confirmIgnoreAvailableUpdate())}
        onDownload={() => unawaited(this._confirmDownloadAvailableUpdate())}
      />
    );
  }

  _buildVoiceContent(): ReactNode {
    const remoteUnavailableText = '该设置仅保存在用户设备，无法远程读取';
    return (
      <SettingsList>
        {[
          <_SettingsGroup key="input" title="输入">
            <_SettingsSubPanel>
              <_DeviceSection
                title="输入源"
                icon={Icons.mic}
                devices={this._audioInputs}
                selectedDevice={this._selectedInput}
                busyDeviceId={this._busyDeviceId}
                emptyText={this._isManagingUser ? remoteUnavailableText : this._loading ? '正在加载输入源' : '未找到输入源'}
                fallbackLabel="麦克风"
                onSelect={(device) => unawaited(this._selectInput(device))}
              />
            </_SettingsSubPanel>
            <SizedBox height={12} />
            <_SettingsSubPanel>
              <_AudioControlPanel
                title="输入音量"
                icon={Icons.graphic_eq}
                volume={this._inputVolume}
                level={this._inputLevel}
                testing={this._testingInput}
                testTooltip={audioInputTestTooltip(this._testingInput)}
                disabled={!this._isManagingUser && this._audioInputs.length === 0}
                testDisabled={this._isManagingUser}
                onVolumeChanged={(value) => unawaited(this._setInputVolume(value))}
                onToggleTest={() => unawaited(this._toggleInputTest())}
              />
            </_SettingsSubPanel>
          </_SettingsGroup>,
          <_SettingsGroup key="output" title="输出">
            <_SettingsSubPanel>
              <_DeviceSection
                title="输出源"
                icon={Icons.headphones}
                devices={this._audioOutputs}
                selectedDevice={this._selectedOutput}
                busyDeviceId={this._busyDeviceId}
                emptyText={this._isManagingUser ? remoteUnavailableText : this._loading ? '正在加载输出源' : '未找到输出源'}
                fallbackLabel="输出"
                onSelect={(device) => unawaited(this._selectOutput(device))}
              />
            </_SettingsSubPanel>
            <SizedBox height={12} />
            <_SettingsSubPanel>
              <_AudioControlPanel
                title="输出音量"
                icon={Icons.volume_up}
                volume={this._outputVolume}
                level={this._outputLevel}
                testing={this._testingOutput}
                testTooltip={audioOutputTestTooltip(this._testingOutput)}
                disabled={!this._isManagingUser && this._audioOutputs.length === 0}
                testDisabled={this._isManagingUser}
                onVolumeChanged={(value) => unawaited(this._setOutputVolume(value))}
                onToggleTest={() => unawaited(this._toggleOutputTest())}
              />
            </_SettingsSubPanel>
          </_SettingsGroup>,
          <_SettingsGroup key="screen-share" title="屏幕共享">
            <_SettingsSubPanel>
              <_ScreenShareQualitySection
                selectedHeight={this._screenShareMaxHeight}
                selectedFrameRate={this._screenShareFrameRate}
                remoteUnavailable={this._isManagingUser}
                onHeightSelected={(height) => unawaited(this._setScreenShareMaxHeight(height))}
                onFrameRateSelected={(frameRate) => unawaited(this._setScreenShareFrameRate(frameRate))}
              />
            </_SettingsSubPanel>
          </_SettingsGroup>,
        ]}
      </SettingsList>
    );
  }

  build(): ReactNode {
    const availableUpdate = this._availableAppUpdate;
    if (this._section === SettingsSection.about && availableUpdate != null) {
      return (
        <FloatingNoticeEmitter notices={this._floatingNotices()}>
          <_Scaffold>{this._buildAppUpdateContent(availableUpdate)}</_Scaffold>
        </FloatingNoticeEmitter>
      );
    }

    const searchQuery = this._settingsSearchController.text.trim();
    const android = this.widget.androidSystemService.isSupported;
    const onClose = this.widget.onClose;
    return (
      <FloatingNoticeEmitter notices={this._floatingNotices()}>
        <_Scaffold>
          <SettingsScaffold
            icon={Icons.settings_outlined}
            title={this._pageTitle}
            onBack={onClose != null || !this.widget.isSubWindow ? (onClose ?? (() => this._navigatorPop())) : null}
            headerAction={
              <ButtonIcon
                tooltip="刷新设置"
                onPressed={this._isRefreshing ? null : () => unawaited(this._refreshActiveSection())}
                icon={<Icon icon={Icons.refresh} />}
                size={38}
                loading={this._isRefreshing}
              />
            }
            sectionTitleOverride={searchQuery.length === 0 ? null : '搜索结果'}
            sectionMetaOverride={
              searchQuery.length === 0 ? null : `${settingsSearchMatches(searchQuery, { android }).length} 项`
            }
            search={
              // Dart `key: ValueKey('settings-search-input')`, as a test id.
              <div data-testid="settings-search-input" style={{ minWidth: 0 }}>
                <Input
                  controller={this._settingsSearchController}
                  hintText="搜索设置"
                  prefixIcon={Icons.search_rounded}
                  showClearButton
                  maxLines={1}
                  height={38}
                  onChanged={() => this.setState()}
                />
              </div>
            }
            navigation={
              new SettingsTileNavigation<SettingsSection>({
                value: this._section,
                onChanged: (section) => {
                  if (this._settingsSearchController.text.length > 0) {
                    this._settingsSearchController.clear();
                  }
                  this._selectSection(section);
                },
                segments: _settingsSectionSegments,
                summaries: this._sectionSummaries(),
              })
            }
            body={searchQuery.length === 0 ? this._buildSectionContent() : this._buildSearchResults(searchQuery)}
          />
        </_Scaffold>
      </FloatingNoticeEmitter>
    );
  }
}

/** The app settings page (Dart `SettingsPage`). */
export function SettingsPage(props: SettingsPageProps) {
  const widget = useMemo(() => _settingsPageWidget(props), [props]);
  const state = useViewState(() => new _SettingsPageState(widget), widget);
  state._appConfig = useAppConfig();
  const route = useRoute();
  state._navigatorPop = () => route.close();
  return <>{state.build()}</>;
}

// ---------------------------------------------------------------------------
// Small layout stand-ins.

const _scaffoldStyle: CSSProperties = {
  flex: '1 1 0',
  alignSelf: 'stretch',
  minWidth: 0,
  minHeight: 0,
  display: 'flex',
  flexDirection: 'column',
  backgroundColor: _primaryDarkLow,
};

/** `Scaffold(backgroundColor: _primaryDarkLow, body: …)`. */
function _Scaffold({ children }: { children?: ReactNode }) {
  return <div style={_scaffoldStyle}>{children}</div>;
}

/**
 * `UnconstrainedBox(alignment: Alignment.centerRight, constrainedAxis:
 * Axis.vertical)`: the child keeps its own width at the end of the row.
 */
function _AlignEnd({ children }: { children?: ReactNode }) {
  return <div style={{ display: 'flex', flexDirection: 'row', justifyContent: 'flex-end', minWidth: 0 }}>{children}</div>;
}

/** `Align(alignment: Alignment.centerLeft)`. */
function _AlignStart({ children }: { children?: ReactNode }) {
  return <div style={{ display: 'flex', flexDirection: 'row', justifyContent: 'flex-start', minWidth: 0 }}>{children}</div>;
}
