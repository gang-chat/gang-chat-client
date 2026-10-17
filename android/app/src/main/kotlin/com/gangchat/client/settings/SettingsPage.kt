package com.gangchat.client.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gangchat.client.app.AccountAvatarErrorTarget
import com.gangchat.client.app.AccountAvatarStatePatch
import com.gangchat.client.app.AccountDeletionStatePatch
import com.gangchat.client.app.AccountEditableFieldsPatch
import com.gangchat.client.app.AccountFormSaveStatePatch
import com.gangchat.client.app.AccountFormSaveTarget
import com.gangchat.client.app.AccountLoadPatch
import com.gangchat.client.app.AccountPresetAvatarSelectionPatch
import com.gangchat.client.app.AccountSessionsLoadPatch
import com.gangchat.client.app.AccountUpdateDraft
import com.gangchat.client.app.AppUpdatePlatform
import com.gangchat.client.app.AudioDeviceInfo
import com.gangchat.client.app.AudioDeviceListPatch
import com.gangchat.client.app.AudioDeviceSelectionPatch
import com.gangchat.client.app.AudioDeviceStore
import com.gangchat.client.app.AudioTestStatePatch
import com.gangchat.client.app.AudioVolumePatch
import com.gangchat.client.app.AutoUpdatePromptStore
import com.gangchat.client.app.AvailableAppUpdate
import com.gangchat.client.app.CloseBehavior
import com.gangchat.client.app.CloseBehaviorStore
import com.gangchat.client.app.EmailVerificationController
import com.gangchat.client.app.LanguagePreferenceStore
import com.gangchat.client.app.ManagedSticker
import com.gangchat.client.app.PasswordChangeStatePatch
import com.gangchat.client.app.PasswordResetController
import com.gangchat.client.app.PasswordVisibilityField
import com.gangchat.client.app.PasswordVisibilityPatch
import com.gangchat.client.app.PersonalMusicPlaylistsController
import com.gangchat.client.app.RestoredAudioDevices
import com.gangchat.client.app.SettingsController
import com.gangchat.client.app.SettingsSection
import com.gangchat.client.app.SettingsSectionPatch
import com.gangchat.client.app.StickerActionKind
import com.gangchat.client.app.StickerActionPatch
import com.gangchat.client.app.StickerFilterDraft
import com.gangchat.client.app.StickerManagementScope
import com.gangchat.client.app.StickerPackLoadPatch
import com.gangchat.client.app.StickerPlacementData
import com.gangchat.client.app.StickerSelectionPatch
import com.gangchat.client.app.accountAvatarActionCancelled
import com.gangchat.client.app.accountAvatarActionFailed
import com.gangchat.client.app.accountAvatarPendingUploadSucceeded
import com.gangchat.client.app.accountAvatarPreparationStarted
import com.gangchat.client.app.accountAvatarPreviewPath
import com.gangchat.client.app.accountAvatarProfileUpdatedFromStickerSucceeded
import com.gangchat.client.app.accountAvatarUploadStarted
import com.gangchat.client.app.accountDeletionConfirmationSpec
import com.gangchat.client.app.accountDeletionDescription
import com.gangchat.client.app.accountDeletionFailed
import com.gangchat.client.app.accountDeletionFinished
import com.gangchat.client.app.accountDeletionStarted
import com.gangchat.client.app.accountEmailPublicChanged
import com.gangchat.client.app.accountFormSaveCancelled
import com.gangchat.client.app.accountFormSaveFailed
import com.gangchat.client.app.accountFormSaveNoChanges
import com.gangchat.client.app.accountFormSaveStarted
import com.gangchat.client.app.accountFormSaveSucceeded
import com.gangchat.client.app.accountFormSaveValidationFailed
import com.gangchat.client.app.accountGenderChanged
import com.gangchat.client.app.accountLoadCancelled
import com.gangchat.client.app.accountLoadFailed
import com.gangchat.client.app.accountLoadStarted
import com.gangchat.client.app.accountLoadSucceeded
import com.gangchat.client.app.accountPhoneNumberPublicChanged
import com.gangchat.client.app.accountPresetAvatarSelected
import com.gangchat.client.app.accountSessionsLoadCancelled
import com.gangchat.client.app.accountSessionsLoadFailed
import com.gangchat.client.app.accountSessionsLoadStarted
import com.gangchat.client.app.accountSessionsLoadSucceeded
import com.gangchat.client.app.accountUpdateDraftFromForm
import com.gangchat.client.app.appVersionLabel
import com.gangchat.client.app.appVersionNumberLabel
import com.gangchat.client.app.audioDeviceInfoId
import com.gangchat.client.app.audioDeviceInfoKind
import com.gangchat.client.app.audioDeviceListApplied
import com.gangchat.client.app.audioDeviceListLoadFailed
import com.gangchat.client.app.audioDeviceListLoadStarted
import com.gangchat.client.app.audioDeviceSelectionFailed
import com.gangchat.client.app.audioDeviceSelectionStarted
import com.gangchat.client.app.audioDeviceSelectionSucceeded
import com.gangchat.client.app.audioInputDeviceSelectedEffects
import com.gangchat.client.app.audioInputLevelChanged
import com.gangchat.client.app.audioInputTestFailed
import com.gangchat.client.app.audioInputTestStarted
import com.gangchat.client.app.audioInputTestStopped
import com.gangchat.client.app.audioInputTestTooltip
import com.gangchat.client.app.audioInputVolumeChanged
import com.gangchat.client.app.audioOutputDeviceSelectedEffects
import com.gangchat.client.app.audioOutputLevelChanged
import com.gangchat.client.app.audioOutputTestFailed
import com.gangchat.client.app.audioOutputTestStarted
import com.gangchat.client.app.audioOutputTestStopped
import com.gangchat.client.app.audioOutputTestTooltip
import com.gangchat.client.app.audioOutputVolumeChanged
import com.gangchat.client.app.audioStoredVolumesApplied
import com.gangchat.client.app.audioVolumePercentText
import com.gangchat.client.app.avatarEmptyFileMessage
import com.gangchat.client.app.avatarPickerOpenFailureMessage
import com.gangchat.client.app.avatarReadFailureMessage
import com.gangchat.client.app.avatarUploadFilename
import com.gangchat.client.app.boundEmailForFeedback
import com.gangchat.client.app.canEditUsername
import com.gangchat.client.app.canSelectVisibleStickers
import com.gangchat.client.app.canStartAccountDeletion
import com.gangchat.client.app.canStartAudioDeviceSelection
import com.gangchat.client.app.canStartPasswordChange
import com.gangchat.client.app.canStartStickerPrimaryAction
import com.gangchat.client.app.canStartStickerSelectionAction
import com.gangchat.client.app.canUseStickerManagementControl
import com.gangchat.client.app.closeBehaviorFromStorageValue
import com.gangchat.client.app.defaultAudioVolume
import com.gangchat.client.app.defaultAutoUpdatePromptEnabled
import com.gangchat.client.app.defaultCloseBehavior
import com.gangchat.client.app.defaultStickerPackName
import com.gangchat.client.app.feedbackMailBody
import com.gangchat.client.app.feedbackMailSubject
import com.gangchat.client.app.filteredManagedStickerItems
import com.gangchat.client.app.formatDateTime
import com.gangchat.client.app.gangChatClientReleaseTimestamp
import com.gangchat.client.app.gangChatClientVersion
import com.gangchat.client.app.gangChatSupportEmail
import com.gangchat.client.app.installTimeLabel
import com.gangchat.client.app.loginUsernameAvailabilityError
import com.gangchat.client.app.loginUsernameUpdateDraftFromForm
import com.gangchat.client.app.loginUsernameValidationError
import com.gangchat.client.app.managedStickerItems
import com.gangchat.client.app.normalizeAccountLanguage
import com.gangchat.client.app.normalizeGender
import com.gangchat.client.app.normalizedAudioVolume
import com.gangchat.client.app.officialVersionTimeLabel
import com.gangchat.client.app.passwordChangeDraftFromForm
import com.gangchat.client.app.passwordChangeFailed
import com.gangchat.client.app.passwordChangeStarted
import com.gangchat.client.app.passwordChangeSucceeded
import com.gangchat.client.app.passwordChangeValidationFailed
import com.gangchat.client.app.passwordVisibilityToggled
import com.gangchat.client.app.preferencesUpdateDraftFromForm
import com.gangchat.client.app.profileUpdateDraftFromForm
import com.gangchat.client.app.registerEmailValidationError
import com.gangchat.client.app.rememberedAudioVolume
import com.gangchat.client.app.restoredAudioVolume
import com.gangchat.client.app.settingsSectionRefreshing
import com.gangchat.client.app.settingsSectionSelected
import com.gangchat.client.app.stickerActionCancelled
import com.gangchat.client.app.stickerActionErrorShown
import com.gangchat.client.app.stickerActionFailed
import com.gangchat.client.app.stickerActionNoticeShown
import com.gangchat.client.app.stickerActionStarted
import com.gangchat.client.app.stickerActionSucceeded
import com.gangchat.client.app.stickerAllVisibleSelected
import com.gangchat.client.app.stickerAvatarUploadFilename
import com.gangchat.client.app.stickerBulkDeleteConfirmBody
import com.gangchat.client.app.stickerDeleteDialogTitle
import com.gangchat.client.app.stickerDeletedNotice
import com.gangchat.client.app.stickerDownloadNotice
import com.gangchat.client.app.stickerFilterActive
import com.gangchat.client.app.stickerFilterApplied
import com.gangchat.client.app.stickerManagementBusy
import com.gangchat.client.app.stickerManagementCountText
import com.gangchat.client.app.stickerManagementModeToggled
import com.gangchat.client.app.stickerMoveNotice
import com.gangchat.client.app.stickerNameFromFilename
import com.gangchat.client.app.stickerNoOrderChangeNotice
import com.gangchat.client.app.stickerNoUploadableImagesMessage
import com.gangchat.client.app.stickerPackRequiresServerMessage
import com.gangchat.client.app.stickerPacksLoadFailed
import com.gangchat.client.app.stickerPacksLoadStarted
import com.gangchat.client.app.stickerPacksLoadSucceeded
import com.gangchat.client.app.stickerPickerOpenFailureMessage
import com.gangchat.client.app.stickerPinnedNotice
import com.gangchat.client.app.stickerRenameName
import com.gangchat.client.app.stickerSelectionNumbers
import com.gangchat.client.app.stickerSelectionToggled
import com.gangchat.client.app.stickerSingleDeleteConfirmBody
import com.gangchat.client.app.stickerUploadFilename
import com.gangchat.client.app.stickerUploadItemsFromFiles
import com.gangchat.client.app.stickerUploadNotice
import com.gangchat.client.app.stickerVisibleSelectionButtonText
import com.gangchat.client.app.stickerVisibleSelectionToggled
import com.gangchat.client.app.storedAudioDeviceFrom
import com.gangchat.client.app.updateDownloadConfirmationBody
import com.gangchat.client.app.usernameHelperText
import com.gangchat.client.app.userFacingErrorMessage
import com.gangchat.client.app.movedStickerOrder
import com.gangchat.client.app.pinnedStickerOrder
import com.gangchat.client.app.selectedStickerIdsByPack
import com.gangchat.client.app.stickerOrderWithStickerIdsPinnedToFront
import com.gangchat.client.app.stickerPlacement
import com.gangchat.client.app.stickerSelectionWouldChangePinnedOrder
import com.gangchat.client.app.stickerSortOrdersBeforeExisting
import com.gangchat.client.app.storageValue
import com.gangchat.client.live.AudioTestHandle
import com.gangchat.client.live.AudioTestService
import com.gangchat.client.live.LiveAudioDeviceService
import com.gangchat.client.live.SystemAudioDevices
import com.gangchat.client.live.androidScreenShareFrameRateCap
import com.gangchat.client.live.defaultScreenShareFrameRate
import com.gangchat.client.live.defaultScreenShareMaxHeight
import com.gangchat.client.live.normalizedScreenShareFrameRate
import com.gangchat.client.live.normalizedScreenShareMaxHeight
import com.gangchat.client.live.restoreStoredAudioDevices
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.protocol.MusicTrackPreviewApi
import com.gangchat.client.protocol.PersonalMusicPlaylistApi
import com.gangchat.client.protocol.StickerPack
import com.gangchat.client.protocol.StickerPackStore
import com.gangchat.client.protocol.UserSession
import com.gangchat.client.shell.AndroidSystemService
import com.gangchat.client.shell.AppUpdatePage
import com.gangchat.client.shell.ClipboardService
import com.gangchat.client.shell.DefaultMusicTrackPreviewPlatformFactory
import com.gangchat.client.shell.EmailVerificationInputAction
import com.gangchat.client.shell.ExternalUriLauncher
import com.gangchat.client.shell.FeedbackMailDraft
import com.gangchat.client.shell.FeedbackMailService
import com.gangchat.client.shell.FileSelectionService
import com.gangchat.client.shell.FileTypeGroup
import com.gangchat.client.shell.InstallInfoService
import com.gangchat.client.shell.ReleaseDownloadCancellationToken
import com.gangchat.client.shell.ReleaseDownloadCancelledException
import com.gangchat.client.shell.ReleaseUpdateService
import com.gangchat.client.shell.SelectedFile
import com.gangchat.client.shell.appUpdatePlatformForOperatingSystem
import com.gangchat.client.shell.emailVerificationErrorMessage
import com.gangchat.client.shell.showEmailVerificationDialog
import com.gangchat.client.shell.verifyPasswordResetEmail
import com.gangchat.client.app.MusicTrackPreviewPlatformFactory
import com.gangchat.client.config.HostPlatform
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.shell.LocalAudioDeviceStore
import com.gangchat.client.shell.LocalAutoUpdatePromptStore
import com.gangchat.client.shell.LocalCloseBehaviorStore
import com.gangchat.client.shell.LocalLanguagePreferenceStore
import com.gangchat.client.ui.AppConfigScope
import com.gangchat.client.ui.AvatarCropDialog
import com.gangchat.client.ui.AvatarPicker
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.ButtonWidget
import com.gangchat.client.ui.DialogFrame
import com.gangchat.client.ui.FloatingNotice
import com.gangchat.client.ui.FloatingNoticeEmitter
import com.gangchat.client.ui.FloatingNoticeTone
import com.gangchat.client.ui.Input
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.ResponsiveDialogActionBar
import com.gangchat.client.ui.SettingsFixedHeaderCard
import com.gangchat.client.ui.SettingsList
import com.gangchat.client.ui.SettingsScaffold
import com.gangchat.client.ui.SettingsTileNavigation
import com.gangchat.client.ui.StickerActionGrid
import com.gangchat.client.ui.StickerActionGridEntry
import com.gangchat.client.ui.StickerConfirmDialog
import com.gangchat.client.ui.StickerFilterDialog
import com.gangchat.client.ui.StickerGrid
import com.gangchat.client.ui.StickerImagePreviewOpener
import com.gangchat.client.ui.StickerPreviewDialog
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.decodeStickerImageDimensions
import com.gangchat.client.ui.floatingNoticeVisibleDuration
import com.gangchat.client.ui.showFloatingErrorNotice
import com.gangchat.client.ui.showFloatingSuccessNotice
import com.gangchat.client.ui.stickerUploadSourcesFromSelectedFiles
import com.gangchat.client.ui.uiTextStyle
import com.gangchat.client.widgets.CompositionTimer
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.Scaffold
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.showDialog
import com.gangchat.client.widgets.startTimer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal val _primaryDark = UiColors.background
internal val _primaryDarkLow = UiColors.surfaceLow
internal val _borderColor = UiColors.border
internal val _cyan = UiColors.accent
internal val _textPrimary = UiColors.text
internal val _textSecondary = UiColors.textSecondary
internal val _textMuted = UiColors.textMuted
internal val _danger = UiColors.danger

/**
 * The defaults Dart wrote as `const` constructor calls. Dart canonicalizes
 * `const` instances, so a rebuilt [SettingsPage] keeps comparing equal stores
 * in `didUpdateWidget`; one shared instance per type keeps that here.
 */
private object _SettingsDefaults {
    val audioDeviceStore: AudioDeviceStore by lazy { LocalAudioDeviceStore() }
    val audioDeviceService: LiveAudioDeviceService by lazy { LiveAudioDeviceService() }
    val stickerPackStore: StickerPackStore by lazy { StickerPackStore() }
    val clipboardService: ClipboardService by lazy { ClipboardService() }
    val fileSelectionService: FileSelectionService by lazy { FileSelectionService() }
    val feedbackMailService: FeedbackMailService by lazy { FeedbackMailService() }
    val androidSystemService: AndroidSystemService by lazy { AndroidSystemService() }
    val autoUpdatePromptStore: AutoUpdatePromptStore by lazy { LocalAutoUpdatePromptStore() }
    val installInfoService: InstallInfoService by lazy { InstallInfoService() }
    val releaseUpdateService: ReleaseUpdateService by lazy { ReleaseUpdateService() }
    val externalUriLauncher: ExternalUriLauncher by lazy { ExternalUriLauncher() }
    val closeBehaviorStore: CloseBehaviorStore by lazy { LocalCloseBehaviorStore() }
    val languageStore: LanguagePreferenceStore by lazy { LocalLanguagePreferenceStore() }
    val musicTrackPreviewPlatformFactory: MusicTrackPreviewPlatformFactory by lazy { DefaultMusicTrackPreviewPlatformFactory() }
}

/** Test tag of the settings search field (Dart's `ValueKey('settings-search-input')`). */
const val settingsSearchInputTag = "settings-search-input"

/** Test tag of the email field (Dart's `ValueKey('settings-email-input')`). */
const val settingsEmailInputTag = "settings-email-input"

/** Test tag of the email verification action (Dart's `ValueKey('settings-email-verification-button')`). */
const val settingsEmailVerificationButtonTag = "settings-email-verification-button"

const val settingsLanguageSegmentedTag = "settings-language-segmented"
const val settingsGenderSegmentedTag = "settings-gender-segmented"

/** Test tag of the personal sticker grid (Dart's `ValueKey('personal-sticker-items-scroll')`). */
const val personalStickerItemsScrollTag = "personal-sticker-items-scroll"

/** Test tag of the username field's column. */
const val settingsUsernameInputTag = "settings-username-input"

/**
 * The app settings page: profile, preferences, privacy and security, voice
 * and video, stickers, playlists and about.
 *
 * PORT: the desktop-only parts are not ported: the window close behaviour
 * group (Dart hides it on Android; its local store is still read and written
 * like Dart does), the Sparkle and DMG update paths, terminating the process
 * after launching a desktop installer, and the macOS audio adapter parameter.
 */
@Composable
fun SettingsPage(
    modifier: Modifier = Modifier,
    isSubWindow: Boolean = false,
    audioDeviceStore: AudioDeviceStore = _SettingsDefaults.audioDeviceStore,
    audioDeviceService: LiveAudioDeviceService = _SettingsDefaults.audioDeviceService,
    systemAudioDevices: SystemAudioDevices? = null,
    controller: SettingsController? = null,
    api: GangApi? = null,
    apiBaseUrl: String = "",
    emailVerificationController: EmailVerificationController? = null,
    passwordResetController: PasswordResetController? = null,
    stickerPackStore: StickerPackStore = _SettingsDefaults.stickerPackStore,
    clipboardService: ClipboardService = _SettingsDefaults.clipboardService,
    fileSelectionService: FileSelectionService = _SettingsDefaults.fileSelectionService,
    feedbackMailService: FeedbackMailService = _SettingsDefaults.feedbackMailService,
    androidSystemService: AndroidSystemService = _SettingsDefaults.androidSystemService,
    autoUpdatePromptStore: AutoUpdatePromptStore = _SettingsDefaults.autoUpdatePromptStore,
    installInfoService: InstallInfoService = _SettingsDefaults.installInfoService,
    releaseUpdateService: ReleaseUpdateService = _SettingsDefaults.releaseUpdateService,
    externalUriLauncher: ExternalUriLauncher = _SettingsDefaults.externalUriLauncher,
    /**
     * Platform whose installers the update check looks for; defaults to the
     * running operating system (tests pin it, like `AppUpdateGate` does).
     */
    updatePlatformOverride: AppUpdatePlatform? = null,
    closeBehaviorStore: CloseBehaviorStore = _SettingsDefaults.closeBehaviorStore,
    languageStore: LanguagePreferenceStore = _SettingsDefaults.languageStore,
    initialSection: SettingsSection = SettingsSection.profile,
    initialMusicPlaylistId: String? = null,
    initialAppUpdate: AvailableAppUpdate? = null,
    stickerImagePreviewOpener: StickerImagePreviewOpener? = null,
    appVersion: String = gangChatClientVersion,
    onAppUpdateDownloadCancellationChanged: ((ReleaseDownloadCancellationToken?) -> Unit)? = null,
    currentUser: CurrentUser? = null,
    onUserUpdated: ((CurrentUser) -> Unit)? = null,
    onMusicPlaylistsChanged: (() -> Unit)? = null,
    onDeviceSelected: ((kind: String, deviceId: String) -> Unit)? = null,
    onVolumeChanged: ((kind: String, volume: Double) -> Unit)? = null,
    /**
     * Fired when the user picks a screen-share resolution, so a live session
     * can re-scale immediately. The choice is also persisted to
     * [audioDeviceStore].
     */
    onScreenShareMaxHeightChanged: ((Int) -> Unit)? = null,
    onScreenShareFrameRateChanged: ((Int) -> Unit)? = null,
    onAccountDeleted: (suspend () -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    musicTrackPreviewPlatformFactory: MusicTrackPreviewPlatformFactory = _SettingsDefaults.musicTrackPreviewPlatformFactory,
) = StatefulWidget(
    SettingsPageWidget(
        isSubWindow = isSubWindow,
        audioDeviceStore = audioDeviceStore,
        audioDeviceService = audioDeviceService,
        systemAudioDevices = systemAudioDevices,
        controller = controller,
        api = api,
        apiBaseUrl = apiBaseUrl,
        emailVerificationController = emailVerificationController,
        passwordResetController = passwordResetController,
        stickerPackStore = stickerPackStore,
        clipboardService = clipboardService,
        fileSelectionService = fileSelectionService,
        feedbackMailService = feedbackMailService,
        androidSystemService = androidSystemService,
        autoUpdatePromptStore = autoUpdatePromptStore,
        installInfoService = installInfoService,
        releaseUpdateService = releaseUpdateService,
        externalUriLauncher = externalUriLauncher,
        updatePlatformOverride = updatePlatformOverride,
        closeBehaviorStore = closeBehaviorStore,
        languageStore = languageStore,
        initialSection = initialSection,
        initialMusicPlaylistId = initialMusicPlaylistId,
        initialAppUpdate = initialAppUpdate,
        stickerImagePreviewOpener = stickerImagePreviewOpener,
        appVersion = appVersion,
        onAppUpdateDownloadCancellationChanged = onAppUpdateDownloadCancellationChanged,
        currentUser = currentUser,
        onUserUpdated = onUserUpdated,
        onMusicPlaylistsChanged = onMusicPlaylistsChanged,
        onDeviceSelected = onDeviceSelected,
        onVolumeChanged = onVolumeChanged,
        onScreenShareMaxHeightChanged = onScreenShareMaxHeightChanged,
        onScreenShareFrameRateChanged = onScreenShareFrameRateChanged,
        onAccountDeleted = onAccountDeleted,
        onClose = onClose,
        musicTrackPreviewPlatformFactory = musicTrackPreviewPlatformFactory,
        modifier = modifier,
    ),
) { _SettingsPageState() }

class SettingsPageWidget(
    val isSubWindow: Boolean,
    val audioDeviceStore: AudioDeviceStore,
    val audioDeviceService: LiveAudioDeviceService,
    val systemAudioDevices: SystemAudioDevices?,
    val controller: SettingsController?,
    val api: GangApi?,
    val apiBaseUrl: String,
    val emailVerificationController: EmailVerificationController?,
    val passwordResetController: PasswordResetController?,
    val stickerPackStore: StickerPackStore,
    // PORT: kept for call-site parity; Dart's settings page never reads it.
    val clipboardService: ClipboardService,
    val fileSelectionService: FileSelectionService,
    val feedbackMailService: FeedbackMailService,
    val androidSystemService: AndroidSystemService,
    val autoUpdatePromptStore: AutoUpdatePromptStore,
    val installInfoService: InstallInfoService,
    val releaseUpdateService: ReleaseUpdateService,
    val externalUriLauncher: ExternalUriLauncher,
    val updatePlatformOverride: AppUpdatePlatform?,
    val closeBehaviorStore: CloseBehaviorStore,
    val languageStore: LanguagePreferenceStore,
    val initialSection: SettingsSection,
    val initialMusicPlaylistId: String?,
    val initialAppUpdate: AvailableAppUpdate?,
    val stickerImagePreviewOpener: StickerImagePreviewOpener?,
    val appVersion: String,
    val onAppUpdateDownloadCancellationChanged: ((ReleaseDownloadCancellationToken?) -> Unit)?,
    val currentUser: CurrentUser?,
    val onUserUpdated: ((CurrentUser) -> Unit)?,
    val onMusicPlaylistsChanged: (() -> Unit)?,
    val onDeviceSelected: ((kind: String, deviceId: String) -> Unit)?,
    val onVolumeChanged: ((kind: String, volume: Double) -> Unit)?,
    val onScreenShareMaxHeightChanged: ((Int) -> Unit)?,
    val onScreenShareFrameRateChanged: ((Int) -> Unit)?,
    val onAccountDeleted: (suspend () -> Unit)?,
    val onClose: (() -> Unit)?,
    val musicTrackPreviewPlatformFactory: MusicTrackPreviewPlatformFactory,
    val modifier: Modifier,
)

private val _stickerFileTypeGroups =
    listOf(FileTypeGroup(label = "图片和 ZIP", extensions = listOf("png", "jpg", "jpeg", "webp", "gif", "zip")))

private fun _rethrowCancellation(error: Throwable) {
    if (error is CancellationException) throw error
}

/** Dart's `Future.wait` over [blocks]: starts each one now and waits for all of them. */
private suspend fun _waitAll(vararg blocks: suspend () -> Unit) {
    coroutineScope {
        blocks.map { block -> async(start = CoroutineStart.UNDISPATCHED) { block() } }.awaitAll()
    }
}

internal class _SettingsPageState : ViewState<SettingsPageWidget>() {
    private val _usernameController = TextEditingController()
    private val _displayNameController = TextEditingController()
    private val _bioController = TextEditingController()
    private val _emailController = TextEditingController()
    private val _phoneController = TextEditingController()
    private val _currentPasswordController = TextEditingController()
    private val _newPasswordController = TextEditingController()
    private val _confirmPasswordController = TextEditingController()
    private val _settingsSearchController = TextEditingController()

    private var _section = SettingsSection.profile
    private var _user: CurrentUser? = null
    private var _sessions: List<UserSession> = emptyList()
    private var _gender = "secret"
    private var _defaultAvatarKey = "blue-3"
    private var _emailPublic = false
    private var _phonePublic = false
    private var _loadingAccount = false
    private var _loadingSessions = false
    private var _savingAccount = false
    private var _savingProfile = false
    private var _uploadingAvatar = false
    private var _clearUploadedAvatar = false
    private var _pendingAvatarAssetId: String? = null
    private var _pendingAvatarUrl: String? = null
    private var _changingPassword = false
    private var _deletingAccount = false
    private var _changingAccountSuspension = false
    private var _stickerPacks: List<StickerPack> = emptyList()
    private var _selectedStickerIds: List<String> = emptyList()
    private val _stickerOrderDrafts = LinkedHashMap<String, List<String>>()
    private var _managingStickers = false
    private var _loadingStickers = false
    private var _uploadingStickers = false
    private var _deletingStickers = false
    private var _savingStickerOrder = false
    private var _downloadingStickers = false
    private var _stickerFilterKeyword = ""
    private var _stickerFilterMimeType = ""
    private var _stickerError: String? = null
    private var _playlistReloadToken = 0
    private var _loadingPlaylists = false
    private var _obscureCurrentPassword = true
    private var _obscureNewPassword = true
    private var _obscureConfirmPassword = true
    private var _accountError: String? = null
    private var _securityError: String? = null
    private var _aboutError: String? = null
    private var _notice: String? = null
    private var _loadingAbout = false
    private var _checkingAppVersion = false
    private var _availableAppUpdate: AvailableAppUpdate? = null
    private var _downloadingAppUpdate = false
    private var _updateDownloadedBytes = 0L
    private var _updateDownloadTotalBytes: Long? = null
    private var _updateDownloadError: String? = null
    private var _openingFeedbackMail = false
    private var _autoPromptUpdates = defaultAutoUpdatePromptEnabled
    private var _androidNotificationPreferenceEnabled = true
    private var _androidNotificationsAvailable = false
    private var _savingAndroidNotifications = false
    private var _lastUpdateDate = installTimeLabel(null)
    private var _usernameAvailabilityDebounce: CompositionTimer? = null
    private var _usernameAvailabilityRequestId = 0
    private var _usernameAvailabilityQuery: String? = null
    private var _checkingUsernameAvailability = false
    private var _checkingEmailAvailability = false
    private var _emailVerificationRequestId = 0
    private var _verifiedEmail: String? = null
    private var _emailVerificationToken: String? = null
    private var _verifyingPasswordReset = false
    private var _usernameAvailabilityError: String? = null
    private var _floatingNoticeSerial = 0
    private val _floatingNoticeEventKeys = LinkedHashMap<String, Int>()

    private var _deviceSubscription: com.gangchat.client.foundation.StreamSubscription<List<AudioDeviceInfo>>? = null
    private var _audioInputs: List<AudioDeviceInfo> = emptyList()
    private var _audioOutputs: List<AudioDeviceInfo> = emptyList()
    private var _selectedInput: AudioDeviceInfo? = null
    private var _selectedOutput: AudioDeviceInfo? = null
    private var _busyDeviceId: String? = null
    private var _inputVolume = defaultAudioVolume
    private var _outputVolume = defaultAudioVolume
    private var _lastOutputVolumeBeforeMute = defaultAudioVolume
    private var _inputLevel = 0.0
    private var _outputLevel = 0.0
    private var _testingInput = false
    private var _testingOutput = false
    private var _voiceInitialized = false
    private var _screenShareMaxHeight = defaultScreenShareMaxHeight
    private var _screenShareFrameRate = defaultScreenShareFrameRate
    private var _error: String? = null
    private var _loading = false
    private var _language = "zh-Hans"
    private var _closeBehavior: CloseBehavior = defaultCloseBehavior
    private var _loadingCloseBehavior = false
    private var _savingCloseBehavior = false
    private var _closeBehaviorError: String? = null
    private val _audioTestService = AudioTestService()
    private var _inputTest: AudioTestHandle? = null
    private var _outputTest: AudioTestHandle? = null
    private var _systemAudioDevices: SystemAudioDevices? = null
    private var _systemDefaultInputSubscription: com.gangchat.client.foundation.StreamSubscription<String?>? = null
    private var _systemDefaultOutputSubscription: com.gangchat.client.foundation.StreamSubscription<String?>? = null
    private var _systemDefaultInputId: String? = null
    private var _systemDefaultOutputId: String? = null

    /** The composition's scope, for the username availability debounce timer. */
    private var _scope: CoroutineScope? = null

    private val _systemAudio: SystemAudioDevices
        get() {
            val existing = _systemAudioDevices
            if (existing != null) return existing
            val created = widget.systemAudioDevices ?: SystemAudioDevices()
            _systemAudioDevices = created
            return created
        }

    private val _settingsController: SettingsController
        get() {
            val injected = widget.controller
            if (injected != null) return injected
            return SettingsController(
                api = widget.api,
                apiBaseUrl = widget.apiBaseUrl,
                stickerPackStore = widget.stickerPackStore,
            )
        }

    private val _isManagingUser: Boolean get() = _settingsController.isManagingUser

    private val _musicPlaylistsController: PersonalMusicPlaylistsController
        get() {
            val api = widget.api
            return PersonalMusicPlaylistsController(
                if (!_isManagingUser && api is PersonalMusicPlaylistApi) api else null,
                onChanged = widget.onMusicPlaylistsChanged,
            )
        }

    private val _accountSuspended: Boolean get() = _user?.status?.trim()?.lowercase() == "suspended"

    private val _pageTitle: String
        get() {
            if (!_isManagingUser) return "设置"
            val displayName = _user?.displayName?.trim() ?: ""
            return if (displayName.isEmpty()) "用户设置" else "$displayName 的用户设置"
        }

    override fun initState() {
        _user = widget.currentUser
        _syncUserFields(widget.currentUser)
        _section = if (widget.initialAppUpdate == null) widget.initialSection else SettingsSection.about
        _availableAppUpdate = widget.initialAppUpdate
        unawaited { _loadCloseBehavior() }
        unawaited { _loadAndroidNotificationSettings() }
        unawaited { _loadAutoUpdatePrompt() }
        unawaited { _loadInstallDate() }
        unawaited { _loadAccount() }
    }

    override fun didUpdateWidget(oldWidget: SettingsPageWidget) {
        if (oldWidget.currentUser != widget.currentUser) {
            _user = widget.currentUser
            _syncUserFields(widget.currentUser)
        }
        if (oldWidget.closeBehaviorStore != widget.closeBehaviorStore) {
            unawaited { _loadCloseBehavior() }
        }
        if (oldWidget.autoUpdatePromptStore != widget.autoUpdatePromptStore) {
            unawaited { _loadAutoUpdatePrompt() }
        }
        if (oldWidget.installInfoService != widget.installInfoService) {
            unawaited { _loadInstallDate() }
        }
        if (oldWidget.initialAppUpdate != widget.initialAppUpdate && widget.initialAppUpdate != null) {
            _section = SettingsSection.about
            _availableAppUpdate = widget.initialAppUpdate
            _resetUpdateDownloadState()
        }
    }

    override fun dispose() {
        _settingsSearchController.dispose()
        unawaited { _stopInputTest(updateState = false) }
        unawaited { _stopOutputTest(updateState = false) }
        _usernameAvailabilityDebounce?.cancel()
        _deviceSubscription?.cancel()
        _systemDefaultInputSubscription?.cancel()
        _systemDefaultOutputSubscription?.cancel()
        val systemAudio = _systemAudioDevices
        if (systemAudio != null) unawaited { systemAudio.dispose() }
        _usernameController.dispose()
        _displayNameController.dispose()
        _bioController.dispose()
        _emailController.dispose()
        _phoneController.dispose()
        _currentPasswordController.dispose()
        _newPasswordController.dispose()
        _confirmPasswordController.dispose()
    }

    /** Dart's `didChangeAppLifecycleState(AppLifecycleState.resumed)`. */
    private fun _onAppResumed() {
        if (widget.androidSystemService.isSupported) {
            unawaited { _loadAndroidNotificationSettings() }
        }
    }

    private fun _syncUserFields(user: CurrentUser?) {
        if (user == null) return
        widget.passwordResetController?.invalidateAuthorizationIfEmailChanged(user.email)
        _usernameController.text = user.username
        _displayNameController.text = user.displayName
        _bioController.text = user.bio
        _emailController.text = user.email ?: ""
        _emailVerificationRequestId++
        _checkingEmailAvailability = false
        _verifiedEmail = null
        _emailVerificationToken = null
        _phoneController.text = user.phoneNumber ?: ""
        _gender = normalizeGender(user.gender)
        _defaultAvatarKey = user.defaultAvatarKey
        _emailPublic = user.emailPublic
        _phonePublic = user.phoneNumberPublic
        _language = normalizeAccountLanguage(user.language)
        _clearUploadedAvatar = false
        _pendingAvatarAssetId = null
        _pendingAvatarUrl = null
        _resetUsernameAvailabilityCheck()
    }

    private val _normalizedAccountEmail: String get() = _emailController.text.trim().lowercase()

    private val _accountEmailMatchesUser: Boolean
        get() {
            val user = _user
            return user != null && _normalizedAccountEmail == (user.email ?: "").trim().lowercase()
        }

    private val _accountEmailVerified: Boolean
        get() {
            if (_normalizedAccountEmail.isEmpty()) return false
            if (_accountEmailMatchesUser && (_user?.emailVerified ?: false)) return true
            return _verifiedEmail == _normalizedAccountEmail && _emailVerificationToken != null
        }

    private fun _resetUsernameAvailabilityCheck() {
        _usernameAvailabilityDebounce?.cancel()
        _usernameAvailabilityRequestId++
        _usernameAvailabilityQuery = null
        _checkingUsernameAvailability = false
        _usernameAvailabilityError = null
    }

    private fun _markFloatingNoticeEvent(channel: String, message: String?) {
        if (message == null || message.trim().isEmpty()) return
        _floatingNoticeEventKeys[channel] = ++_floatingNoticeSerial
    }

    private fun _floatingNoticeEventKey(channel: String): Any? = _floatingNoticeEventKeys[channel]

    private suspend fun _loadAccount() {
        setState { _applyAccountLoadPatch(accountLoadStarted(user = _user)) }
        try {
            val user = _settingsController.loadAccount()
            if (!mounted) return
            if (user == null) {
                setState { _applyAccountLoadPatch(accountLoadCancelled(user = _user, accountError = _accountError)) }
                return
            }
            setState {
                _applyAccountLoadPatch(accountLoadSucceeded(user = user))
                _syncUserFields(user)
            }
            widget.onUserUpdated?.invoke(user)
            if (!_isManagingUser) {
                unawaited { _rememberLanguagePreference(user.language) }
            }
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return
            setState { _applyAccountLoadPatch(accountLoadFailed(user = _user, failure = e)) }
        }
    }

    private fun _onLoginUsernameChanged(value: String) {
        val user = _user
        val username = value.trim()
        _usernameAvailabilityDebounce?.cancel()
        val requestId = ++_usernameAvailabilityRequestId
        val shouldCheck =
            user != null &&
                username != user.username &&
                loginUsernameValidationError(username) == null &&
                _settingsController.hasApi
        if (!shouldCheck) {
            setState {
                _usernameAvailabilityQuery = null
                _checkingUsernameAvailability = false
                _usernameAvailabilityError = null
            }
            return
        }

        setState {
            _usernameAvailabilityQuery = username
            _checkingUsernameAvailability = true
            _usernameAvailabilityError = null
        }
        _usernameAvailabilityDebounce =
            _scope?.startTimer(_usernameAvailabilityDebounceDuration) {
                unawaited {
                    _checkLoginUsernameAvailability(user = user!!, username = username, requestId = requestId)
                }
            }
    }

    private fun _onAccountEmailChanged(value: String) {
        val normalized = value.trim().lowercase()
        _emailVerificationRequestId++
        setState {
            _checkingEmailAvailability = false
            if (_verifiedEmail != normalized) {
                _verifiedEmail = null
                _emailVerificationToken = null
            }
        }
    }

    private suspend fun _showAccountEmailVerification() {
        if (_savingAccount || _checkingEmailAvailability) return
        val email = _emailController.text.trim()
        val validationError = registerEmailValidationError(email)
        if (validationError != null) {
            showFloatingErrorNotice(context, validationError)
            return
        }
        val controller = widget.emailVerificationController
        if (controller == null) {
            showFloatingErrorNotice(context, "暂时无法使用邮箱验证功能")
            return
        }

        val requestId = ++_emailVerificationRequestId
        setState { _checkingEmailAvailability = true }
        try {
            if (!_accountEmailMatchesUser) {
                val available = controller.isEmailAvailable(email)
                if (!mounted || requestId != _emailVerificationRequestId) return
                if (!available) {
                    setState { _checkingEmailAvailability = false }
                    showFloatingErrorNotice(context, "该邮箱已被其他用户使用")
                    return
                }
            }

            val challenge =
                if (_isManagingUser) {
                    controller.inspectOrStart(email)
                } else {
                    controller.inspectOrStartForCurrentUser(email)
                }
            if (!mounted || requestId != _emailVerificationRequestId) return
            setState { _checkingEmailAvailability = false }
            val verificationToken =
                showEmailVerificationDialog(context = context, email = email, challenge = challenge, controller = controller)
            if (!mounted ||
                verificationToken == null ||
                requestId != _emailVerificationRequestId ||
                _emailController.text.trim() != email
            ) {
                return
            }
            setState {
                _verifiedEmail = email.lowercase()
                _emailVerificationToken = verificationToken
            }
        } catch (error: Exception) {
            _rethrowCancellation(error)
            if (!mounted || requestId != _emailVerificationRequestId) return
            setState { _checkingEmailAvailability = false }
            showFloatingErrorNotice(context, emailVerificationErrorMessage(error))
        }
    }

    private suspend fun _checkLoginUsernameAvailability(user: CurrentUser, username: String, requestId: Int) {
        try {
            val users = _settingsController.searchUsers(query = username, limit = 8)
            if (!mounted || requestId != _usernameAvailabilityRequestId) return
            val error = loginUsernameAvailabilityError(user = user, username = username, candidates = users ?: emptyList())
            setState {
                _usernameAvailabilityQuery = username
                _checkingUsernameAvailability = false
                _usernameAvailabilityError = error
            }
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted || requestId != _usernameAvailabilityRequestId) return
            setState {
                _usernameAvailabilityQuery = username
                _checkingUsernameAvailability = false
                _usernameAvailabilityError = "暂时无法检测登录用户名是否重复"
            }
        }
    }

    private suspend fun _ensureLoginUsernameAvailable(user: CurrentUser, username: String): String? {
        val normalizedUsername = username.trim()
        if (_usernameAvailabilityQuery == normalizedUsername && !_checkingUsernameAvailability) {
            return _usernameAvailabilityError
        }

        _usernameAvailabilityDebounce?.cancel()
        val requestId = ++_usernameAvailabilityRequestId
        setState {
            _usernameAvailabilityQuery = normalizedUsername
            _checkingUsernameAvailability = true
            _usernameAvailabilityError = null
        }
        try {
            val users = _settingsController.searchUsers(query = normalizedUsername, limit = 8)
            if (!mounted || requestId != _usernameAvailabilityRequestId) return null
            val error =
                loginUsernameAvailabilityError(user = user, username = normalizedUsername, candidates = users ?: emptyList())
            setState {
                _checkingUsernameAvailability = false
                _usernameAvailabilityError = error
            }
            return error
        } catch (e: Exception) {
            _rethrowCancellation(e)
            val error = "暂时无法检测登录用户名是否重复"
            if (!mounted || requestId != _usernameAvailabilityRequestId) return error
            setState {
                _checkingUsernameAvailability = false
                _usernameAvailabilityError = error
            }
            return error
        }
    }

    private suspend fun _loadSessions() {
        setState { _applyAccountSessionsLoadPatch(accountSessionsLoadStarted(sessions = _sessions)) }
        try {
            val sessions = _settingsController.loadSessions()
            if (!mounted) return
            if (sessions == null) {
                setState {
                    _applyAccountSessionsLoadPatch(
                        accountSessionsLoadCancelled(sessions = _sessions, securityError = _securityError),
                    )
                }
                return
            }
            setState { _applyAccountSessionsLoadPatch(accountSessionsLoadSucceeded(sessions = sessions)) }
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return
            setState { _applyAccountSessionsLoadPatch(accountSessionsLoadFailed(sessions = _sessions, failure = e)) }
        }
    }

    private suspend fun _loadCloseBehavior() {
        if (_isManagingUser) return
        if (_loadingCloseBehavior) return
        setState {
            _loadingCloseBehavior = true
            _closeBehaviorError = null
        }
        try {
            val behavior = widget.closeBehaviorStore.read()
            if (!mounted) return
            setState {
                _closeBehavior = behavior
                _loadingCloseBehavior = false
            }
        } catch (error: Exception) {
            _rethrowCancellation(error)
            if (!mounted) return
            setState {
                _loadingCloseBehavior = false
                _closeBehaviorError = userFacingErrorMessage(error, fallback = "读取关闭方式失败")
            }
        }
    }

    private suspend fun _loadAutoUpdatePrompt() {
        if (_isManagingUser) return
        try {
            val enabled = widget.autoUpdatePromptStore.read()
            if (!mounted) return
            setState { _autoPromptUpdates = enabled }
        } catch (error: Exception) {
            _rethrowCancellation(error)
            if (!mounted) return
            setState {
                _aboutError = "读取自动提示更新失败"
                _markFloatingNoticeEvent("aboutError", _aboutError)
            }
        }
    }

    private suspend fun _loadAndroidNotificationSettings() {
        if (!widget.androidSystemService.isSupported || _isManagingUser) return
        try {
            var preferenceEnabled = true
            var available = false
            _waitAll(
                { preferenceEnabled = widget.androidSystemService.notificationPreferenceEnabled() },
                { available = widget.androidSystemService.notificationsEnabled() },
            )
            if (!mounted) return
            setState {
                _androidNotificationPreferenceEnabled = preferenceEnabled
                _androidNotificationsAvailable = available
            }
        } catch (e: Exception) {
            _rethrowCancellation(e)
        }
    }

    private suspend fun _setAndroidNotificationsEnabled(enabled: Boolean) {
        if (_savingAndroidNotifications) return
        setState { _savingAndroidNotifications = true }
        try {
            widget.androidSystemService.setNotificationPreferenceEnabled(enabled)
            var available = false
            if (enabled) {
                available = widget.androidSystemService.requestNotificationPermission()
            }
            if (!mounted) return
            setState {
                _androidNotificationPreferenceEnabled = enabled
                _androidNotificationsAvailable = available
            }
            val registration = widget.androidSystemService.pushRegistration()
            val api = widget.api
            if (registration != null && api != null) {
                api.upsertPushDevice(
                    provider = registration.provider,
                    installationId = registration.installationId,
                    token = registration.token,
                    enabled = enabled,
                )
            }
        } catch (error: Exception) {
            _rethrowCancellation(error)
            if (!mounted) return
            showFloatingErrorNotice(context, userFacingErrorMessage(error, fallback = "修改系统通知失败"))
        } finally {
            if (mounted) setState { _savingAndroidNotifications = false }
        }
    }

    private suspend fun _openAndroidNotificationSettings() {
        try {
            widget.androidSystemService.openNotificationSettings()
        } catch (error: Exception) {
            _rethrowCancellation(error)
            if (!mounted) return
            showFloatingErrorNotice(context, userFacingErrorMessage(error, fallback = "无法打开系统通知设置"))
        }
    }

    private suspend fun _loadInstallDate() {
        var installedAt: String? = null
        try {
            installedAt = widget.installInfoService.readInstalledAt()
        } catch (e: Exception) {
            _rethrowCancellation(e)
        }
        if (!mounted) return
        setState { _lastUpdateDate = installTimeLabel(installedAt) }
    }

    private suspend fun _setAutoUpdatePrompt(value: Boolean) {
        if (_isManagingUser) return
        if (_autoPromptUpdates == value) return
        val previous = _autoPromptUpdates
        setState {
            _autoPromptUpdates = value
            _aboutError = null
            _notice = null
        }
        try {
            widget.autoUpdatePromptStore.write(value)
            if (!mounted) return
            setState {
                _notice = "自动提示更新已保存"
                _markFloatingNoticeEvent("notice", _notice)
            }
        } catch (error: Exception) {
            _rethrowCancellation(error)
            if (!mounted) return
            setState {
                _autoPromptUpdates = previous
                _aboutError = "自动提示更新保存失败"
                _markFloatingNoticeEvent("aboutError", _aboutError)
            }
        }
    }

    private suspend fun _rememberLanguagePreference(language: String) {
        try {
            widget.languageStore.write(language)
        } catch (e: Exception) {
            _rethrowCancellation(e)
            // A local language-cache failure should not block server-backed settings.
        }
    }

    @Suppress("unused")
    private fun _setCloseBehavior(value: String) {
        setState {
            _closeBehavior = closeBehaviorFromStorageValue(value)
            _closeBehaviorError = null
        }
    }

    private suspend fun _savePreferences() {
        if (_savingAccount || _savingCloseBehavior) return
        setState {
            _savingCloseBehavior = true
            _closeBehaviorError = null
            _notice = null
        }
        if (!_isManagingUser) {
            try {
                widget.closeBehaviorStore.write(_closeBehavior)
                if (!mounted) return
                setState { _savingCloseBehavior = false }
            } catch (error: Exception) {
                _rethrowCancellation(error)
                if (!mounted) return
                setState {
                    _savingCloseBehavior = false
                    _closeBehaviorError = userFacingErrorMessage(error, fallback = "保存关闭方式失败")
                    _markFloatingNoticeEvent("closeBehaviorError", _closeBehaviorError)
                }
                return
            }
        } else {
            setState { _savingCloseBehavior = false }
        }

        val user = _user
        if (!_settingsController.hasApi || user == null) {
            if (!mounted) return
            setState {
                _notice = "偏好设置已保存"
                _markFloatingNoticeEvent("notice", _notice)
            }
            return
        }

        val draft = preferencesUpdateDraftFromForm(user = user, language = _language)
        if (draft.error == null && draft.noChanges) {
            if (!_isManagingUser) {
                _rememberLanguagePreference(_language)
            }
            if (!mounted) return
            setState {
                _notice = "偏好设置已保存"
                _markFloatingNoticeEvent("notice", _notice)
            }
            return
        }
        _saveAccount(target = AccountFormSaveTarget.preferences)
    }

    private suspend fun _ensureStickersLoaded(forceReload: Boolean = false) {
        if (_loadingStickers) return
        if (!forceReload && _stickerPacks.isNotEmpty()) return
        _loadStickers(forceReload = forceReload)
    }

    private suspend fun _loadStickers(forceReload: Boolean = false) {
        setState {
            _applyStickerPackLoadPatch(
                stickerPacksLoadStarted(packs = _stickerPacks, selectedStickerIds = _selectedStickerIds),
            )
        }
        try {
            val result = _settingsController.loadPersonalStickerPacks(userId = _stickerCacheUserId, forceReload = forceReload)
            if (!mounted) return
            val packs = result?.packs
            setState {
                _applyStickerPackLoadPatch(
                    stickerPacksLoadSucceeded(packs = packs ?: _stickerPacks, selectedStickerIds = _selectedStickerIds),
                )
                if (packs != null) _syncStickerOrderDrafts(packs)
            }
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return
            setState {
                _applyStickerPackLoadPatch(
                    stickerPacksLoadFailed(packs = _stickerPacks, selectedStickerIds = _selectedStickerIds, failure = e),
                )
            }
        }
    }

    private val _stickerCacheUserId: String get() = _user?.id ?: widget.currentUser?.id ?: ""

    private fun _applyStickerPackLoadPatch(patch: StickerPackLoadPatch) {
        _stickerPacks = patch.packs
        _selectedStickerIds = patch.selectedStickerIds
        _loadingStickers = patch.loading
        _stickerError = patch.error
        _markFloatingNoticeEvent("stickerError", _stickerError)
    }

    private fun _applyStickerSelectionPatch(patch: StickerSelectionPatch) {
        _managingStickers = patch.managing
        _stickerFilterKeyword = patch.filterKeyword
        _stickerFilterMimeType = patch.filterMimeType
        _selectedStickerIds = patch.selectedStickerIds
    }

    private fun _applyStickerActionPatch(patch: StickerActionPatch) {
        _uploadingStickers = patch.uploading
        _deletingStickers = patch.deleting
        _savingStickerOrder = patch.savingOrder
        _downloadingStickers = patch.downloading
        _selectedStickerIds = patch.selectedStickerIds
        _stickerError = patch.error
        _notice = patch.notice
        _markFloatingNoticeEvent("stickerError", _stickerError)
        _markFloatingNoticeEvent("notice", _notice)
    }

    private fun _applyAccountSessionsLoadPatch(patch: AccountSessionsLoadPatch) {
        _sessions = patch.sessions
        _loadingSessions = patch.loading
        _securityError = patch.securityError
        _markFloatingNoticeEvent("securityError", _securityError)
    }

    private fun _applyAccountLoadPatch(patch: AccountLoadPatch) {
        _user = patch.user
        _loadingAccount = patch.loading
        _accountError = patch.accountError
        _markFloatingNoticeEvent("accountError", _accountError)
    }

    private fun _applyAudioDeviceListPatch(patch: AudioDeviceListPatch<AudioDeviceInfo>) {
        _audioInputs = patch.inputs
        _audioOutputs = patch.outputs
        _selectedInput = patch.selectedInput
        _selectedOutput = patch.selectedOutput
        _loading = patch.loading
        _error = patch.error
        _markFloatingNoticeEvent("audioError", _error)
    }

    private fun _applyAudioDeviceSelectionPatch(patch: AudioDeviceSelectionPatch<AudioDeviceInfo>) {
        _selectedInput = patch.selectedInput
        _selectedOutput = patch.selectedOutput
        _busyDeviceId = patch.busyDeviceId
        _error = patch.error
        _markFloatingNoticeEvent("audioError", _error)
    }

    private fun _applyAudioVolumePatch(patch: AudioVolumePatch) {
        _inputVolume = patch.inputVolume
        _outputVolume = patch.outputVolume
    }

    private fun _rememberOutputVolume(volume: Double) {
        _lastOutputVolumeBeforeMute = rememberedAudioVolume(volume)
    }

    private fun _restoredOutputVolume(): Double = restoredAudioVolume(_lastOutputVolumeBeforeMute)

    private fun _applyAudioTestStatePatch(patch: AudioTestStatePatch) {
        _testingInput = patch.testingInput
        _testingOutput = patch.testingOutput
        _inputLevel = patch.inputLevel
        _outputLevel = patch.outputLevel
        _error = patch.error
        _markFloatingNoticeEvent("audioError", _error)
    }

    private fun _applyAccountFormSaveStatePatch(patch: AccountFormSaveStatePatch) {
        _savingAccount = patch.savingAccount
        _savingProfile = patch.savingProfile
        _accountError = patch.accountError
        _notice = patch.notice
        _markFloatingNoticeEvent("accountError", _accountError)
        _markFloatingNoticeEvent("notice", _notice)
    }

    private fun _applyAccountEditableFieldsPatch(patch: AccountEditableFieldsPatch) {
        _gender = patch.gender
        _emailPublic = patch.emailPublic
        _phonePublic = patch.phoneNumberPublic
    }

    private fun _applyPasswordChangeStatePatch(patch: PasswordChangeStatePatch) {
        _changingPassword = patch.changingPassword
        _securityError = patch.securityError
        _notice = patch.notice
        _markFloatingNoticeEvent("securityError", _securityError)
        _markFloatingNoticeEvent("notice", _notice)
    }

    private fun _applyPasswordVisibilityPatch(patch: PasswordVisibilityPatch) {
        _obscureCurrentPassword = patch.obscureCurrentPassword
        _obscureNewPassword = patch.obscureNewPassword
        _obscureConfirmPassword = patch.obscureConfirmPassword
    }

    private fun _applyAccountDeletionStatePatch(patch: AccountDeletionStatePatch) {
        _deletingAccount = patch.deletingAccount
        _securityError = patch.securityError
        _notice = patch.notice
        _markFloatingNoticeEvent("securityError", _securityError)
        _markFloatingNoticeEvent("notice", _notice)
    }

    private fun _applyAccountAvatarStatePatch(patch: AccountAvatarStatePatch) {
        _pendingAvatarAssetId = patch.pendingAvatarAssetId
        _pendingAvatarUrl = patch.pendingAvatarUrl
        _clearUploadedAvatar = patch.clearUploadedAvatar
        _uploadingAvatar = patch.uploadingAvatar
        _accountError = patch.accountError
        _stickerError = patch.stickerError
        _notice = patch.notice
        _markFloatingNoticeEvent("accountError", _accountError)
        _markFloatingNoticeEvent("stickerError", _stickerError)
        _markFloatingNoticeEvent("notice", _notice)
    }

    private fun _applyAccountPresetAvatarSelectionPatch(patch: AccountPresetAvatarSelectionPatch) {
        _defaultAvatarKey = patch.defaultAvatarKey
        _pendingAvatarAssetId = patch.pendingAvatarAssetId
        _pendingAvatarUrl = patch.pendingAvatarUrl
        _clearUploadedAvatar = patch.clearUploadedAvatar
        _notice = patch.notice
        _markFloatingNoticeEvent("notice", _notice)
    }

    private fun _applySettingsSectionPatch(patch: SettingsSectionPatch) {
        _section = patch.section
        _notice = patch.notice
        _markFloatingNoticeEvent("notice", _notice)
    }

    private fun _floatingNotices(): List<FloatingNotice> {
        val notices = mutableListOf<FloatingNotice>()

        fun add(
            message: String?,
            tone: FloatingNoticeTone,
            channel: String,
            duration: Duration? = floatingNoticeVisibleDuration,
        ) {
            if (message == null || message.trim().isEmpty()) return
            notices.add(FloatingNotice(message = message, tone = tone, duration = duration, eventKey = _floatingNoticeEventKey(channel)))
        }

        add(_notice, FloatingNoticeTone.success, channel = "notice")
        add(_stickerError, FloatingNoticeTone.error, duration = null, channel = "stickerError")
        add(_accountError, FloatingNoticeTone.error, duration = null, channel = "accountError")
        add(_securityError, FloatingNoticeTone.error, duration = null, channel = "securityError")
        add(_aboutError, FloatingNoticeTone.error, duration = null, channel = "aboutError")
        add(_closeBehaviorError, FloatingNoticeTone.error, duration = null, channel = "closeBehaviorError")
        add(_updateDownloadError, FloatingNoticeTone.error, duration = null, channel = "updateDownloadError")
        add(_error, FloatingNoticeTone.error, duration = null, channel = "audioError")
        return notices
    }

    private fun _syncStickerOrderDrafts(packs: List<StickerPack>) {
        _stickerOrderDrafts.clear()
        for (pack in packs) {
            _stickerOrderDrafts[pack.id] = pack.stickers.map { sticker -> sticker.id }
        }
    }

    private suspend fun _refreshActiveSection() {
        when (_section) {
            SettingsSection.profile -> _loadAccount()
            SettingsSection.preferences -> _waitAll({ _loadCloseBehavior() }, { _loadAndroidNotificationSettings() })
            SettingsSection.stickers -> _loadStickers(forceReload = true)
            SettingsSection.playlists -> if (mounted) setState { _playlistReloadToken += 1 }
            SettingsSection.security -> _waitAll({ _loadAccount() }, { _loadSessions() })
            SettingsSection.voice -> _ensureVoiceInitialized(forceReload = true)
            SettingsSection.about -> _refreshAboutInfo()
        }
    }

    private suspend fun _refreshAboutInfo() {
        if (_loadingAbout) return
        setState { _loadingAbout = true }
        _waitAll({ _loadAutoUpdatePrompt() }, { _loadInstallDate() })
        if (!mounted) return
        setState { _loadingAbout = false }
    }

    private suspend fun _checkAppVersion() {
        if (_checkingAppVersion) return
        val platform = _currentUpdatePlatform()
        if (platform == null) {
            setState {
                _aboutError = "当前平台暂不支持自动检查更新"
                _markFloatingNoticeEvent("aboutError", _aboutError)
            }
            return
        }
        val bucketUrl = AppConfigScope.of(context).releaseBucketUrl
        setState {
            _checkingAppVersion = true
            _aboutError = null
            _notice = null
        }
        // PORT: the macOS Sparkle branch is desktop-only.
        try {
            val update =
                widget.releaseUpdateService.checkForUpdate(
                    bucketUrl = bucketUrl,
                    currentVersion = widget.appVersion,
                    platform = platform,
                )
            if (!mounted) return
            setState {
                _checkingAppVersion = false
                _availableAppUpdate = update
                _resetUpdateDownloadState()
                _notice = if (update == null) "当前已是最新版本" else null
                _markFloatingNoticeEvent("notice", _notice)
            }
        } catch (error: Exception) {
            _rethrowCancellation(error)
            if (!mounted) return
            setState {
                _checkingAppVersion = false
                _aboutError = "检查更新失败"
                _markFloatingNoticeEvent("aboutError", _aboutError)
            }
        }
    }

    private fun _resetUpdateDownloadState() {
        _downloadingAppUpdate = false
        _updateDownloadedBytes = 0
        _updateDownloadTotalBytes = null
        _updateDownloadError = null
    }

    private fun _closeAppUpdatePage() {
        if (_downloadingAppUpdate) return
        setState {
            _section = SettingsSection.about
            _availableAppUpdate = null
            _resetUpdateDownloadState()
        }
    }

    private suspend fun _confirmIgnoreAvailableUpdate() {
        val update = _availableAppUpdate
        if (update == null || _downloadingAppUpdate) return
        val confirmed =
            _confirmAppUpdateAction(
                title = "忽略此版本",
                icon = Icons.Outlined.NotificationsOff,
                body = "忽略 ${appVersionLabel(update.latestVersion)} 后，除非你主动点击检查更新，或出现更高版本，否则不会再主动提示该版本。",
                confirmLabel = "忽略此版本",
            )
        if (!confirmed || !mounted) return
        try {
            widget.autoUpdatePromptStore.writeIgnoredVersion(update.latestVersion)
            if (!mounted) return
            setState {
                _section = SettingsSection.about
                _availableAppUpdate = null
                _notice = "已忽略此版本 ${appVersionLabel(update.latestVersion)}"
                _markFloatingNoticeEvent("notice", _notice)
                _resetUpdateDownloadState()
            }
        } catch (error: Exception) {
            _rethrowCancellation(error)
            if (!mounted) return
            setState {
                _updateDownloadError = "忽略此版本失败"
                _markFloatingNoticeEvent("updateDownloadError", _updateDownloadError)
            }
        }
    }

    private suspend fun _confirmDownloadAvailableUpdate() {
        val update = _availableAppUpdate
        if (update == null || _downloadingAppUpdate) return
        val confirmed =
            _confirmAppUpdateAction(
                title = "下载新版本",
                icon = Icons.Outlined.DownloadForOffline,
                body = updateDownloadConfirmationBody(update.asset.platform),
                confirmLabel = "下载新版本",
            )
        if (!confirmed || !mounted) return
        if (update.asset.platform == AppUpdatePlatform.android) {
            try {
                widget.androidSystemService.requestUpdateNotificationPermission()
                if (!mounted) return
            } catch (e: Exception) {
                _rethrowCancellation(e)
            }
        }
        _downloadAndInstallAvailableUpdate()
    }

    private suspend fun _confirmAppUpdateAction(
        title: String,
        icon: ImageVector,
        body: String,
        confirmLabel: String,
    ): Boolean {
        val confirmed =
            showDialog<Boolean>(context) { dialogContext ->
                DialogFrame(
                    title = title,
                    icon = icon,
                    adaptiveActions =
                        listOf(
                            ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(dialogContext).pop(false) }),
                            ResponsiveDialogAction(
                                label = confirmLabel,
                                onPressed = { Navigator.of(dialogContext).pop(true) },
                                tone = ButtonTone.primary,
                            ),
                        ),
                    child = {
                        Text(body, style = UiTypography.body.copyWith(color = UiColors.textSecondary, height = 1.55))
                    },
                )
            }
        return confirmed ?: false
    }

    /**
     * Downloads the update (reporting progress, cancellable through the token
     * handed to [SettingsPageWidget.onAppUpdateDownloadCancellationChanged])
     * and hands the APK to the system installer.
     *
     * PORT: the DMG-in-browser branch and terminating the app after a desktop
     * installer starts only ran on macOS / Windows.
     */
    private suspend fun _downloadAndInstallAvailableUpdate() {
        val update = _availableAppUpdate
        if (update == null || _downloadingAppUpdate) return
        val cancellationToken = ReleaseDownloadCancellationToken()
        widget.onAppUpdateDownloadCancellationChanged?.invoke(cancellationToken)
        var androidDownloadStatusSet = false
        setState {
            _downloadingAppUpdate = true
            _updateDownloadError = null
            _updateDownloadedBytes = 0
            _updateDownloadTotalBytes = null
        }
        try {
            if (update.asset.platform == AppUpdatePlatform.android) {
                widget.androidSystemService.setUpdateDownloadActive(true)
                androidDownloadStatusSet = true
            }
            val file =
                widget.releaseUpdateService.downloadUpdate(
                    update,
                    cancellationToken = cancellationToken,
                    onProgress = { receivedBytes, totalBytes ->
                        if (mounted) {
                            setState {
                                _updateDownloadedBytes = receivedBytes
                                _updateDownloadTotalBytes = totalBytes
                            }
                        }
                    },
                )
            if (!mounted) return
            widget.releaseUpdateService.startInstaller(file, platform = update.asset.platform)
            if (mounted) {
                // Android's package installer reads the APK from this process through
                // FileProvider. Keep the process alive until the OS has finished its
                // prepare/verify phase; the OS will replace it when installation wins.
                setState { _downloadingAppUpdate = false }
            }
        } catch (error: ReleaseDownloadCancelledException) {
            if (!mounted) return
            setState {
                _downloadingAppUpdate = false
                _updateDownloadedBytes = 0
                _updateDownloadTotalBytes = null
                _updateDownloadError = "下载已中断"
                _markFloatingNoticeEvent("updateDownloadError", _updateDownloadError)
            }
        } catch (error: Exception) {
            _rethrowCancellation(error)
            if (!mounted) return
            setState {
                _downloadingAppUpdate = false
                _updateDownloadError = userFacingErrorMessage(error, fallback = "下载或启动安装程序失败")
                _markFloatingNoticeEvent("updateDownloadError", _updateDownloadError)
            }
        } finally {
            if (androidDownloadStatusSet) {
                try {
                    widget.androidSystemService.setUpdateDownloadActive(false)
                } catch (e: Exception) {
                    // Notification decoration must not mask the actual update result.
                }
            }
            widget.onAppUpdateDownloadCancellationChanged?.invoke(null)
        }
    }

    private fun _currentUpdatePlatform(): AppUpdatePlatform? =
        widget.updatePlatformOverride ?: appUpdatePlatformForOperatingSystem(HostPlatform.operatingSystem)

    private suspend fun _openFeedbackMail() {
        if (_openingFeedbackMail) return
        val senderEmail = boundEmailForFeedback(_user?.email)
        if (senderEmail == null) {
            setState {
                _aboutError = "当前账号未绑定邮箱，无法发起意见反馈"
                _markFloatingNoticeEvent("aboutError", _aboutError)
            }
            return
        }
        val draft =
            FeedbackMailDraft(
                from = senderEmail,
                to = gangChatSupportEmail,
                subject = feedbackMailSubject(widget.appVersion),
                body = feedbackMailBody(senderEmail = senderEmail, currentVersion = widget.appVersion),
            )
        setState {
            _openingFeedbackMail = true
            _aboutError = null
            _notice = null
        }
        try {
            widget.feedbackMailService.openDraft(draft)
            if (!mounted) return
            setState {
                _openingFeedbackMail = false
                _notice = "已打开邮件客户端"
                _markFloatingNoticeEvent("notice", _notice)
            }
        } catch (error: Exception) {
            _rethrowCancellation(error)
            if (!mounted) return
            setState {
                _openingFeedbackMail = false
                _aboutError = "无法打开邮件客户端"
                _markFloatingNoticeEvent("aboutError", _aboutError)
            }
        }
    }

    private suspend fun _ensureVoiceInitialized(forceReload: Boolean = false) {
        if (_voiceInitialized && !forceReload) return
        if (!_voiceInitialized) {
            _voiceInitialized = true
            if (_deviceSubscription == null) {
                _deviceSubscription =
                    widget.audioDeviceService.devicesChanged.listen({ devices -> unawaited { _applyDevices(devices) } })
            }
            // Follow OS-selected audio endpoints when the user has not pinned one.
            // The subscriptions re-run selection whenever the system defaults change
            // underneath Settings.
            if (_systemDefaultInputSubscription == null) {
                _systemDefaultInputSubscription =
                    _systemAudio.inputChanges.listen({ deviceId ->
                        _systemDefaultInputId = deviceId
                        unawaited { _onSystemDefaultAudioChanged() }
                    })
            }
            if (_systemDefaultOutputSubscription == null) {
                _systemDefaultOutputSubscription =
                    _systemAudio.outputChanges.listen({ deviceId ->
                        _systemDefaultOutputId = deviceId
                        unawaited { _onSystemDefaultAudioChanged() }
                    })
            }
            unawaited { _loadStoredAudioSettings() }
        }
        var inputId: String? = null
        var outputId: String? = null
        _waitAll(
            { inputId = _systemAudio.currentInputDeviceId() },
            { outputId = _systemAudio.currentOutputDeviceId() },
        )
        _systemDefaultInputId = inputId
        _systemDefaultOutputId = outputId
        _loadDevices()
    }

    private suspend fun _saveAccount(target: AccountFormSaveTarget = AccountFormSaveTarget.account) {
        val user = _user
        if (!_settingsController.hasApi || user == null || _savingAccount || _savingProfile) {
            return
        }

        val draft: AccountUpdateDraft =
            when (target) {
                AccountFormSaveTarget.preferences -> preferencesUpdateDraftFromForm(user = user, language = _language)
                AccountFormSaveTarget.account ->
                    accountUpdateDraftFromForm(
                        user = user,
                        username = _usernameController.text,
                        email = _emailController.text,
                        emailPublic = _emailPublic,
                        phoneNumber = _phoneController.text,
                        phoneNumberPublic = _phonePublic,
                        language = _language,
                    )
                AccountFormSaveTarget.profile -> throw IllegalStateException("Profile uses _saveProfile")
            }
        val draftError = draft.error
        if (draftError != null) {
            setState {
                _applyAccountFormSaveStatePatch(
                    accountFormSaveValidationFailed(
                        error = draftError,
                        savingAccount = _savingAccount,
                        savingProfile = _savingProfile,
                        notice = _notice,
                    ),
                )
            }
            return
        }
        val accountEmailUpdate = target == AccountFormSaveTarget.account
        val emailIdentityChanged = accountEmailUpdate && !_accountEmailMatchesUser
        val emailVerificationRequired = accountEmailUpdate && (emailIdentityChanged || !user.emailVerified)
        if (emailVerificationRequired && !_accountEmailVerified) {
            showFloatingErrorNotice(context, "请先验证邮箱")
            return
        }
        val emailVerificationOnlyUpdate = emailVerificationRequired && draft.email == null
        if (draft.noChanges && !emailVerificationOnlyUpdate) {
            if (target == AccountFormSaveTarget.preferences) {
                if (!_isManagingUser) {
                    _rememberLanguagePreference(_language)
                }
                if (!mounted) return
            }
            setState {
                _applyAccountFormSaveStatePatch(
                    accountFormSaveNoChanges(
                        target = target,
                        savingAccount = _savingAccount,
                        savingProfile = _savingProfile,
                        accountError = _accountError,
                    ),
                )
            }
            return
        }

        setState {
            _applyAccountFormSaveStatePatch(
                accountFormSaveStarted(target = target, savingAccount = _savingAccount, savingProfile = _savingProfile),
            )
        }
        try {
            val updated =
                _settingsController.updateAccount(
                    username = draft.username,
                    email = draft.email ?: if (emailVerificationOnlyUpdate) _emailController.text.trim() else null,
                    emailVerificationToken = if (emailVerificationRequired) _emailVerificationToken else null,
                    emailPublic = draft.emailPublic,
                    phoneNumber = draft.phoneNumber,
                    phoneNumberPublic = draft.phoneNumberPublic,
                    language = draft.language,
                )
            if (!mounted) return
            if (updated == null) {
                setState {
                    _applyAccountFormSaveStatePatch(
                        accountFormSaveCancelled(
                            target = target,
                            savingAccount = _savingAccount,
                            savingProfile = _savingProfile,
                            accountError = _accountError,
                            notice = _notice,
                        ),
                    )
                }
                return
            }
            if (target == AccountFormSaveTarget.preferences) {
                if (!_isManagingUser) {
                    _rememberLanguagePreference(updated.language)
                }
                if (!mounted) return
            }
            setState {
                _user = updated
                _syncUserFields(updated)
                _applyAccountFormSaveStatePatch(
                    accountFormSaveSucceeded(target = target, savingAccount = _savingAccount, savingProfile = _savingProfile),
                )
            }
            widget.onUserUpdated?.invoke(updated)
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return
            setState {
                _applyAccountFormSaveStatePatch(
                    accountFormSaveFailed(
                        target = target,
                        savingAccount = _savingAccount,
                        savingProfile = _savingProfile,
                        failure = e,
                    ),
                )
            }
        }
    }

    private suspend fun _saveProfile() {
        val user = _user
        if (!_settingsController.hasApi || user == null || _savingProfile || _savingAccount) {
            return
        }

        val usernameChanged = _usernameController.text.trim() != user.username
        val usernameDraft =
            if (usernameChanged) {
                loginUsernameUpdateDraftFromForm(user = user, username = _usernameController.text)
            } else {
                AccountUpdateDraft.noChanges()
            }
        val usernameError = usernameDraft.error
        if (usernameError != null) {
            setState {
                _applyAccountFormSaveStatePatch(
                    accountFormSaveValidationFailed(
                        error = usernameError,
                        savingAccount = _savingAccount,
                        savingProfile = _savingProfile,
                        notice = _notice,
                    ),
                )
            }
            return
        }
        if (!usernameDraft.noChanges) {
            val availabilityError = _ensureLoginUsernameAvailable(user = user, username = usernameDraft.username!!)
            if (!mounted) return
            if (availabilityError != null) {
                setState {
                    _applyAccountFormSaveStatePatch(
                        accountFormSaveValidationFailed(
                            error = availabilityError,
                            savingAccount = _savingAccount,
                            savingProfile = _savingProfile,
                            notice = _notice,
                        ),
                    )
                }
                return
            }
        }

        val profileDraft =
            profileUpdateDraftFromForm(
                user = user,
                displayName = _displayNameController.text,
                bio = _bioController.text,
                gender = _gender,
                defaultAvatarKey = _defaultAvatarKey,
                pendingAvatarAssetId = _pendingAvatarAssetId,
                clearUploadedAvatar = _clearUploadedAvatar,
            )
        val profileError = profileDraft.error
        if (profileError != null) {
            setState {
                _applyAccountFormSaveStatePatch(
                    accountFormSaveValidationFailed(
                        error = profileError,
                        savingAccount = _savingAccount,
                        savingProfile = _savingProfile,
                        notice = _notice,
                    ),
                )
            }
            return
        }
        if (usernameDraft.noChanges && profileDraft.noChanges) {
            setState {
                _applyAccountFormSaveStatePatch(
                    accountFormSaveNoChanges(
                        target = AccountFormSaveTarget.profile,
                        savingAccount = _savingAccount,
                        savingProfile = _savingProfile,
                        accountError = _accountError,
                    ),
                )
            }
            return
        }

        setState {
            _applyAccountFormSaveStatePatch(
                accountFormSaveStarted(
                    target = AccountFormSaveTarget.profile,
                    savingAccount = _savingAccount,
                    savingProfile = _savingProfile,
                ),
            )
        }
        try {
            var updated: CurrentUser = user
            if (!usernameDraft.noChanges) {
                val accountUpdated = _settingsController.updateAccount(username = usernameDraft.username)
                if (!mounted) return
                if (accountUpdated == null) {
                    setState {
                        _applyAccountFormSaveStatePatch(
                            accountFormSaveCancelled(
                                target = AccountFormSaveTarget.profile,
                                savingAccount = _savingAccount,
                                savingProfile = _savingProfile,
                                accountError = _accountError,
                                notice = _notice,
                            ),
                        )
                    }
                    return
                }
                updated = accountUpdated
            }
            if (!profileDraft.noChanges) {
                val profileUpdated =
                    _settingsController.updateProfile(
                        displayName = profileDraft.displayName,
                        bio = profileDraft.bio,
                        gender = profileDraft.gender,
                        defaultAvatarKey = profileDraft.defaultAvatarKey,
                        avatarAssetId = profileDraft.avatarAssetId,
                    )
                if (!mounted) return
                if (profileUpdated == null) {
                    setState {
                        _applyAccountFormSaveStatePatch(
                            accountFormSaveCancelled(
                                target = AccountFormSaveTarget.profile,
                                savingAccount = _savingAccount,
                                savingProfile = _savingProfile,
                                accountError = _accountError,
                                notice = _notice,
                            ),
                        )
                    }
                    return
                }
                updated = profileUpdated
            }
            setState {
                _user = updated
                _syncUserFields(updated)
                _applyAccountFormSaveStatePatch(
                    accountFormSaveSucceeded(
                        target = AccountFormSaveTarget.profile,
                        savingAccount = _savingAccount,
                        savingProfile = _savingProfile,
                    ),
                )
            }
            widget.onUserUpdated?.invoke(updated)
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return
            setState {
                _applyAccountFormSaveStatePatch(
                    accountFormSaveFailed(
                        target = AccountFormSaveTarget.profile,
                        savingAccount = _savingAccount,
                        savingProfile = _savingProfile,
                        failure = e,
                    ),
                )
            }
        }
    }

    private suspend fun _pickAndUploadAvatar() {
        if (!_settingsController.hasApi || _uploadingAvatar) return
        setState {
            _applyAccountAvatarStatePatch(
                accountAvatarPreparationStarted(
                    target = AccountAvatarErrorTarget.account,
                    pendingAvatarAssetId = _pendingAvatarAssetId,
                    pendingAvatarUrl = _pendingAvatarUrl,
                    clearUploadedAvatar = _clearUploadedAvatar,
                    uploadingAvatar = _uploadingAvatar,
                    accountError = _accountError,
                    stickerError = _stickerError,
                ),
            )
        }

        val file: SelectedFile?
        try {
            file =
                widget.fileSelectionService.openFile(
                    acceptedTypeGroups = listOf(FileTypeGroup(label = "图片", extensions = listOf("png", "jpg", "jpeg", "webp"))),
                )
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return
            setState { _applyAccountAvatarStatePatch(_avatarActionFailed(AccountAvatarErrorTarget.account, avatarPickerOpenFailureMessage(e))) }
            return
        }
        if (file == null) return

        val bytes: ByteArray
        try {
            bytes = file.readAsBytes()
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return
            setState { _applyAccountAvatarStatePatch(_avatarActionFailed(AccountAvatarErrorTarget.account, avatarReadFailureMessage(e))) }
            return
        }
        if (bytes.isEmpty()) {
            setState { _applyAccountAvatarStatePatch(_avatarActionFailed(AccountAvatarErrorTarget.account, avatarEmptyFileMessage())) }
            return
        }
        if (!mounted) return

        val cropped = showDialog<ByteArray>(context, barrierDismissible = false) { _ -> AvatarCropDialog(bytes = bytes) }
        if (cropped == null || !mounted) return

        setState {
            _applyAccountAvatarStatePatch(
                accountAvatarUploadStarted(
                    target = AccountAvatarErrorTarget.account,
                    pendingAvatarAssetId = _pendingAvatarAssetId,
                    pendingAvatarUrl = _pendingAvatarUrl,
                    clearUploadedAvatar = _clearUploadedAvatar,
                    accountError = _accountError,
                    stickerError = _stickerError,
                ),
            )
        }
        try {
            val asset =
                _settingsController.uploadImageAsset(
                    bytes = cropped,
                    filename = avatarUploadFilename(file.name),
                    purpose = "avatar",
                )
            if (asset == null) {
                if (!mounted) return
                setState { _applyAccountAvatarStatePatch(_avatarActionCancelled(AccountAvatarErrorTarget.account)) }
                return
            }
            if (!mounted) return
            setState {
                _applyAccountAvatarStatePatch(
                    accountAvatarPendingUploadSucceeded(assetId = asset.id, assetUrl = asset.url, stickerError = _stickerError),
                )
            }
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return
            setState { _applyAccountAvatarStatePatch(_avatarActionFailed(AccountAvatarErrorTarget.account, e)) }
        }
    }

    private fun _avatarActionFailed(target: AccountAvatarErrorTarget, failure: Any): AccountAvatarStatePatch =
        accountAvatarActionFailed(
            target = target,
            pendingAvatarAssetId = _pendingAvatarAssetId,
            pendingAvatarUrl = _pendingAvatarUrl,
            clearUploadedAvatar = _clearUploadedAvatar,
            accountError = _accountError,
            stickerError = _stickerError,
            failure = failure,
        )

    private fun _avatarActionCancelled(target: AccountAvatarErrorTarget): AccountAvatarStatePatch =
        accountAvatarActionCancelled(
            target = target,
            pendingAvatarAssetId = _pendingAvatarAssetId,
            pendingAvatarUrl = _pendingAvatarUrl,
            clearUploadedAvatar = _clearUploadedAvatar,
            accountError = _accountError,
            stickerError = _stickerError,
        )

    private fun _allStickerItems(): List<ManagedSticker> =
        managedStickerItems(_stickerPacks, orderForPack = { pack -> _stickerOrderDrafts[pack.id] })

    private fun _filteredStickerItems(): List<ManagedSticker> =
        filteredManagedStickerItems(_allStickerItems(), keyword = _stickerFilterKeyword, mimeType = _stickerFilterMimeType)

    private val _stickerFilterActive: Boolean
        get() = stickerFilterActive(keyword = _stickerFilterKeyword, mimeType = _stickerFilterMimeType)

    private val _stickerManagementBusy: Boolean
        get() =
            stickerManagementBusy(
                uploading = _uploadingStickers,
                deleting = _deletingStickers,
                savingOrder = _savingStickerOrder,
                downloading = _downloadingStickers,
            )

    private fun _stickerSelectionNumbers(): Map<String, Int> = stickerSelectionNumbers(_selectedStickerIds)

    private suspend fun _ensureActiveStickerPack(): StickerPack {
        if (_stickerPacks.isNotEmpty()) return _stickerPacks.first()

        if (!_settingsController.hasApi) {
            throw IllegalStateException(stickerPackRequiresServerMessage())
        }
        val created =
            _settingsController.createStickerPack(
                name = defaultStickerPackName(StickerManagementScope.personal),
                sortOrder = (_stickerPacks.size + 1) * 10,
            ) ?: throw IllegalStateException(stickerPackRequiresServerMessage())
        if (mounted) {
            setState {
                _stickerPacks = _stickerPacks + created
                _stickerOrderDrafts[created.id] = emptyList()
            }
        }
        return created
    }

    private fun _stickerActionStarted(action: StickerActionKind): StickerActionPatch =
        stickerActionStarted(
            action = action,
            uploading = _uploadingStickers,
            deleting = _deletingStickers,
            savingOrder = _savingStickerOrder,
            downloading = _downloadingStickers,
            selectedStickerIds = _selectedStickerIds,
        )

    private fun _stickerActionFailed(action: StickerActionKind, failure: Any): StickerActionPatch =
        stickerActionFailed(
            action = action,
            uploading = _uploadingStickers,
            deleting = _deletingStickers,
            savingOrder = _savingStickerOrder,
            downloading = _downloadingStickers,
            selectedStickerIds = _selectedStickerIds,
            failure = failure,
        )

    private fun _stickerActionCancelled(action: StickerActionKind): StickerActionPatch =
        stickerActionCancelled(
            action = action,
            uploading = _uploadingStickers,
            deleting = _deletingStickers,
            savingOrder = _savingStickerOrder,
            downloading = _downloadingStickers,
            selectedStickerIds = _selectedStickerIds,
        )

    private fun _stickerActionSucceeded(
        action: StickerActionKind,
        notice: String,
        clearSelection: Boolean = false,
    ): StickerActionPatch =
        stickerActionSucceeded(
            action = action,
            uploading = _uploadingStickers,
            deleting = _deletingStickers,
            savingOrder = _savingStickerOrder,
            downloading = _downloadingStickers,
            selectedStickerIds = _selectedStickerIds,
            error = _stickerError,
            notice = notice,
            clearSelection = clearSelection,
        )

    private fun _stickerErrorShown(failure: Any): StickerActionPatch =
        stickerActionErrorShown(
            uploading = _uploadingStickers,
            deleting = _deletingStickers,
            savingOrder = _savingStickerOrder,
            downloading = _downloadingStickers,
            selectedStickerIds = _selectedStickerIds,
            failure = failure,
            notice = _notice,
        )

    private suspend fun _pickAndUploadStickers() {
        if (!_settingsController.hasApi || _stickerManagementBusy) return

        val files: List<SelectedFile>
        try {
            files = widget.fileSelectionService.openFiles(acceptedTypeGroups = _stickerFileTypeGroups)
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return
            setState { _applyStickerActionPatch(_stickerErrorShown(stickerPickerOpenFailureMessage(e))) }
            return
        }
        if (files.isEmpty()) return

        setState { _applyStickerActionPatch(_stickerActionStarted(StickerActionKind.upload)) }
        var uploadedCount = 0
        try {
            val uploadItems =
                stickerUploadItemsFromFiles(
                    stickerUploadSourcesFromSelectedFiles(files),
                    decodeImageDimensions = ::decodeStickerImageDimensions,
                )
            if (uploadItems.isEmpty()) {
                throw IllegalStateException(stickerNoUploadableImagesMessage())
            }
            val pack = _ensureActiveStickerPack()
            val uploadSortOrders = stickerSortOrdersBeforeExisting(pack, uploadItems.size)
            for ((index, item) in uploadItems.withIndex()) {
                val asset =
                    _settingsController.uploadImageAsset(
                        bytes = item.bytes,
                        filename = stickerUploadFilename(item.filename, index),
                        purpose = "sticker",
                    ) ?: continue
                _settingsController.addSticker(
                    packId = pack.id,
                    assetId = asset.id,
                    name = stickerNameFromFilename(item.filename),
                    sortOrder = uploadSortOrders[index],
                )
                uploadedCount += 1
            }
            _loadStickers(forceReload = true)
            if (!mounted) return
            setState {
                _applyStickerActionPatch(
                    _stickerActionSucceeded(
                        StickerActionKind.upload,
                        notice = stickerUploadNotice(scope = StickerManagementScope.personal, count = uploadedCount),
                    ),
                )
            }
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return
            setState { _applyStickerActionPatch(_stickerActionFailed(StickerActionKind.upload, e)) }
        }
    }

    private fun _toggleStickerManageMode() {
        setState {
            _applyStickerSelectionPatch(
                stickerManagementModeToggled(
                    managing = _managingStickers,
                    filterKeyword = _stickerFilterKeyword,
                    filterMimeType = _stickerFilterMimeType,
                ),
            )
        }
    }

    private fun _toggleStickerSelection(stickerId: String) {
        setState {
            _applyStickerSelectionPatch(
                stickerSelectionToggled(
                    managing = _managingStickers,
                    filterKeyword = _stickerFilterKeyword,
                    filterMimeType = _stickerFilterMimeType,
                    selectedStickerIds = _selectedStickerIds,
                    stickerId = stickerId,
                ),
            )
        }
    }

    private fun _selectAllVisibleStickers(items: List<ManagedSticker>) {
        setState {
            _applyStickerSelectionPatch(
                stickerVisibleSelectionToggled(
                    managing = _managingStickers,
                    busy = _stickerManagementBusy,
                    filterKeyword = _stickerFilterKeyword,
                    filterMimeType = _stickerFilterMimeType,
                    selectedStickerIds = _selectedStickerIds,
                    visibleItems = items,
                ),
            )
        }
    }

    private suspend fun _deleteSelectedStickers() {
        val selectedIds = _selectedStickerIds.toList()
        if (!_settingsController.hasApi ||
            !canStartStickerSelectionAction(busy = _stickerManagementBusy, selectedStickerIds = selectedIds)
        ) {
            return
        }
        val byStickerId = _allStickerItems().associateBy { item -> item.sticker.id }

        val confirmed =
            showDialog<Boolean>(context) { _ ->
                StickerConfirmDialog(
                    title = stickerDeleteDialogTitle(StickerManagementScope.personal),
                    body = stickerBulkDeleteConfirmBody(scope = StickerManagementScope.personal, count = selectedIds.size),
                    confirmLabel = "删除",
                    confirmIcon = Icons.Filled.DeleteOutline,
                    danger = true,
                )
            }
        if (confirmed != true || !mounted) return

        setState { _applyStickerActionPatch(_stickerActionStarted(StickerActionKind.delete)) }
        try {
            for (stickerId in selectedIds) {
                val item = byStickerId[stickerId] ?: continue
                _settingsController.deleteSticker(packId = item.pack.id, stickerId = stickerId)
            }
            _loadStickers(forceReload = true)
            if (!mounted) return
            setState {
                _applyStickerActionPatch(
                    _stickerActionSucceeded(
                        StickerActionKind.delete,
                        notice = stickerDeletedNotice(scope = StickerManagementScope.personal, count = selectedIds.size),
                        clearSelection = true,
                    ),
                )
            }
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return
            setState { _applyStickerActionPatch(_stickerActionFailed(StickerActionKind.delete, e)) }
        }
    }

    private suspend fun _downloadSelectedStickers() {
        val selectedIds = _selectedStickerIds.toList()
        _downloadStickerIds(selectedIds)
    }

    private suspend fun _downloadStickerIds(stickerIds: List<String>) {
        if (!_settingsController.hasApi ||
            !canStartStickerSelectionAction(busy = _stickerManagementBusy, selectedStickerIds = stickerIds)
        ) {
            return
        }

        setState { _applyStickerActionPatch(_stickerActionStarted(StickerActionKind.download)) }
        try {
            val downloaded = _settingsController.downloadStickers(stickerIds = stickerIds)
            if (downloaded == null) {
                if (!mounted) return
                setState { _applyStickerActionPatch(_stickerActionCancelled(StickerActionKind.download)) }
                return
            }
            val location =
                widget.fileSelectionService.getSaveLocation(
                    suggestedName = downloaded.filename,
                    acceptedTypeGroups = _stickerFileTypeGroups,
                    confirmButtonText = "保存",
                )
            if (location == null) {
                if (!mounted) return
                setState { _applyStickerActionPatch(_stickerActionCancelled(StickerActionKind.download)) }
                return
            }
            widget.fileSelectionService.saveBytesToLocation(
                bytes = downloaded.bytes,
                location = location,
                filename = downloaded.filename,
                mimeType = downloaded.mimeType,
            )
            if (!mounted) return
            setState {
                _applyStickerActionPatch(
                    _stickerActionSucceeded(
                        StickerActionKind.download,
                        notice = stickerDownloadNotice(scope = StickerManagementScope.personal, count = stickerIds.size),
                    ),
                )
            }
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return
            setState { _applyStickerActionPatch(_stickerActionFailed(StickerActionKind.download, e)) }
        }
    }

    private suspend fun _pinSelectedStickers() {
        val selectedIds = _selectedStickerIds.toList()
        if (!_settingsController.hasApi ||
            !canStartStickerSelectionAction(busy = _stickerManagementBusy, selectedStickerIds = selectedIds) ||
            !stickerSelectionWouldChangePinnedOrder(
                packs = _stickerPacks,
                selectedStickerIds = selectedIds,
                orderByPack = _stickerOrderDrafts,
            )
        ) {
            return
        }

        val selectedByPack = selectedStickerIdsByPack(_stickerPacks, selectedIds)
        if (selectedByPack.isEmpty()) {
            setState {
                _applyStickerActionPatch(
                    stickerActionNoticeShown(
                        uploading = _uploadingStickers,
                        deleting = _deletingStickers,
                        savingOrder = _savingStickerOrder,
                        downloading = _downloadingStickers,
                        selectedStickerIds = _selectedStickerIds,
                        error = _stickerError,
                        notice = stickerNoOrderChangeNotice(StickerManagementScope.personal),
                    ),
                )
            }
            return
        }

        setState { _applyStickerActionPatch(_stickerActionStarted(StickerActionKind.order)) }
        try {
            for (pack in _stickerPacks) {
                val selectedInPack = selectedByPack[pack.id]
                if (selectedInPack == null || selectedInPack.isEmpty()) continue
                val nextOrder =
                    stickerOrderWithStickerIdsPinnedToFront(pack, selectedInPack, order = _stickerOrderDrafts[pack.id])
                        ?: continue
                _settingsController.reorderStickers(packId = pack.id, stickerIds = nextOrder)
            }
            _loadStickers(forceReload = true)
            if (!mounted) return
            setState {
                _applyStickerActionPatch(
                    _stickerActionSucceeded(
                        StickerActionKind.order,
                        notice = stickerPinnedNotice(scope = StickerManagementScope.personal, count = selectedIds.size),
                    ),
                )
            }
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return
            setState { _applyStickerActionPatch(_stickerActionFailed(StickerActionKind.order, e)) }
        }
    }

    private fun _previewSticker(item: ManagedSticker) {
        val imageUrl = AppConfigScope.of(context).resolveAssetUrl(item.sticker.asset.url) ?: return
        val placement = _stickerPlacement(item.sticker.id)
        unawaited {
            showDialog<Unit>(context) { _ ->
                StickerPreviewDialog(
                    item = item,
                    imageUrl = imageUrl,
                    canMoveUp = !_stickerFilterActive && (placement?.canMoveUp ?: false),
                    canMoveDown = !_stickerFilterActive && (placement?.canMoveDown ?: false),
                    canPin = placement?.canPin ?: false,
                    canRename = true,
                    canDownload = true,
                    canDelete = true,
                    onRename = { name -> _renameSticker(item, name) },
                    onSetAvatar = { _setStickerAsAvatar(item) },
                    onDownload = { _downloadStickerIds(listOf(item.sticker.id)) },
                    onDelete = { _deleteStickerItem(item) },
                    onMoveUp = { _moveStickerItem(item, -1) },
                    onMoveDown = { _moveStickerItem(item, 1) },
                    onPin = { _pinStickerItem(item) },
                    imagePreviewOpener = widget.stickerImagePreviewOpener,
                )
            }
        }
    }

    private fun _stickerPlacement(stickerId: String): StickerPlacementData? =
        stickerPlacement(_stickerPacks, stickerId, orderForPack = { pack -> _stickerOrderDrafts[pack.id] })

    private suspend fun _renameSticker(item: ManagedSticker, name: String): String? {
        val trimmed = stickerRenameName(name)
        if (!_settingsController.hasApi || trimmed == null) return null
        try {
            val updated =
                _settingsController.updateSticker(packId = item.pack.id, stickerId = item.sticker.id, name = trimmed)
                    ?: return null
            _loadStickers(forceReload = true)
            return updated.name
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return null
            setState { _applyStickerActionPatch(_stickerErrorShown(e)) }
            return null
        }
    }

    private suspend fun _deleteStickerItem(item: ManagedSticker): Boolean {
        if (!_settingsController.hasApi || _stickerManagementBusy) return false

        val confirmed =
            showDialog<Boolean>(context) { _ ->
                StickerConfirmDialog(
                    title = stickerDeleteDialogTitle(StickerManagementScope.personal),
                    body = stickerSingleDeleteConfirmBody(scope = StickerManagementScope.personal, stickerName = item.sticker.name),
                    confirmLabel = "删除",
                    confirmIcon = Icons.Filled.DeleteOutline,
                    danger = true,
                )
            }
        if (confirmed != true || !mounted) return false

        setState { _applyStickerActionPatch(_stickerActionStarted(StickerActionKind.delete)) }
        try {
            _settingsController.deleteSticker(packId = item.pack.id, stickerId = item.sticker.id)
            _loadStickers(forceReload = true)
            if (!mounted) return false
            setState {
                _applyStickerActionPatch(
                    _stickerActionSucceeded(
                        StickerActionKind.delete,
                        notice = stickerDeletedNotice(scope = StickerManagementScope.personal),
                    ),
                )
            }
            return true
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return false
            setState { _applyStickerActionPatch(_stickerActionFailed(StickerActionKind.delete, e)) }
            return false
        }
    }

    private suspend fun _moveStickerItem(item: ManagedSticker, delta: Int): StickerPlacementData? {
        val placement = _stickerPlacement(item.sticker.id)
        if (_stickerFilterActive) return placement
        if (!_settingsController.hasApi || placement == null || _stickerManagementBusy) {
            return placement
        }
        val ids = movedStickerOrder(placement.pack, item.sticker.id, delta, order = _stickerOrderDrafts[placement.pack.id]) ?: return placement

        setState { _applyStickerActionPatch(_stickerActionStarted(StickerActionKind.order)) }
        try {
            _settingsController.reorderStickers(packId = placement.pack.id, stickerIds = ids)
            _loadStickers(forceReload = true)
            if (!mounted) return placement
            setState {
                _applyStickerActionPatch(
                    _stickerActionSucceeded(
                        StickerActionKind.order,
                        notice = stickerMoveNotice(scope = StickerManagementScope.personal, delta = delta),
                    ),
                )
            }
            return _stickerPlacement(item.sticker.id)
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return placement
            setState { _applyStickerActionPatch(_stickerActionFailed(StickerActionKind.order, e)) }
            return placement
        }
    }

    private suspend fun _pinStickerItem(item: ManagedSticker): StickerPlacementData? {
        val placement = _stickerPlacement(item.sticker.id)
        if (!_settingsController.hasApi || placement == null || placement.index == 0 || _stickerManagementBusy) {
            return placement
        }

        val ids = pinnedStickerOrder(placement.pack, item.sticker.id, order = _stickerOrderDrafts[placement.pack.id]) ?: return placement

        setState { _applyStickerActionPatch(_stickerActionStarted(StickerActionKind.order)) }
        try {
            _settingsController.reorderStickers(packId = placement.pack.id, stickerIds = ids)
            _loadStickers(forceReload = true)
            if (!mounted) return placement
            setState {
                _applyStickerActionPatch(
                    _stickerActionSucceeded(
                        StickerActionKind.order,
                        notice = stickerPinnedNotice(scope = StickerManagementScope.personal),
                    ),
                )
            }
            return _stickerPlacement(item.sticker.id)
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return placement
            setState { _applyStickerActionPatch(_stickerActionFailed(StickerActionKind.order, e)) }
            return placement
        }
    }

    private suspend fun _setStickerAsAvatar(item: ManagedSticker) {
        if (!_settingsController.hasApi || _uploadingAvatar) return
        setState {
            _applyAccountAvatarStatePatch(
                accountAvatarUploadStarted(
                    target = AccountAvatarErrorTarget.sticker,
                    pendingAvatarAssetId = _pendingAvatarAssetId,
                    pendingAvatarUrl = _pendingAvatarUrl,
                    clearUploadedAvatar = _clearUploadedAvatar,
                    accountError = _accountError,
                    stickerError = _stickerError,
                ),
            )
        }
        try {
            val downloaded = _settingsController.downloadStickers(stickerIds = listOf(item.sticker.id))
            if (downloaded == null) {
                if (!mounted) return
                setState { _applyAccountAvatarStatePatch(_avatarActionCancelled(AccountAvatarErrorTarget.sticker)) }
                return
            }
            if (!mounted) return
            val cropped = showDialog<ByteArray>(context, barrierDismissible = false) { _ -> AvatarCropDialog(bytes = downloaded.bytes) }
            if (cropped == null || !mounted) {
                if (!mounted) return
                setState { _applyAccountAvatarStatePatch(_avatarActionCancelled(AccountAvatarErrorTarget.sticker)) }
                return
            }
            val asset =
                _settingsController.uploadImageAsset(
                    bytes = cropped,
                    filename = stickerAvatarUploadFilename(stickerId = item.sticker.id),
                    purpose = "avatar",
                )
            if (asset == null) {
                if (!mounted) return
                setState { _applyAccountAvatarStatePatch(_avatarActionCancelled(AccountAvatarErrorTarget.sticker)) }
                return
            }
            val updated = _settingsController.updateProfile(avatarAssetId = asset.id)
            if (updated == null) {
                if (!mounted) return
                setState { _applyAccountAvatarStatePatch(_avatarActionCancelled(AccountAvatarErrorTarget.sticker)) }
                return
            }
            if (!mounted) return
            setState {
                _user = updated
                _syncUserFields(updated)
                _applyAccountAvatarStatePatch(
                    accountAvatarProfileUpdatedFromStickerSucceeded(
                        pendingAvatarAssetId = _pendingAvatarAssetId,
                        pendingAvatarUrl = _pendingAvatarUrl,
                        clearUploadedAvatar = _clearUploadedAvatar,
                        accountError = _accountError,
                    ),
                )
            }
            widget.onUserUpdated?.invoke(updated)
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return
            setState { _applyAccountAvatarStatePatch(_avatarActionFailed(AccountAvatarErrorTarget.sticker, e)) }
        }
    }

    private suspend fun _openStickerFilter() {
        val result =
            showDialog<StickerFilterDraft>(context) { _ ->
                StickerFilterDialog(keyword = _stickerFilterKeyword, mimeType = _stickerFilterMimeType)
            }
        if (result == null || !mounted) return
        setState {
            _applyStickerSelectionPatch(
                stickerFilterApplied(managing = _managingStickers, keyword = result.keyword, mimeType = result.mimeType),
            )
        }
    }

    private fun _selectDefaultAvatarKey(value: String) {
        setState {
            _applyAccountPresetAvatarSelectionPatch(
                accountPresetAvatarSelected(defaultAvatarKey = value, currentAvatarUrl = _user?.avatarUrl),
            )
        }
    }

    private suspend fun _changePassword() {
        if (!canStartPasswordChange(hasApi = _settingsController.hasApi, changingPassword = _changingPassword)) {
            return
        }
        val draft =
            passwordChangeDraftFromForm(
                currentPassword = _currentPasswordController.text,
                newPassword = _newPasswordController.text,
                confirmPassword = _confirmPasswordController.text,
                currentPasswordRequired = !_canResetPasswordWithoutCurrentPassword,
            )
        if (!draft.isValid) {
            setState {
                _applyPasswordChangeStatePatch(
                    passwordChangeValidationFailed(error = draft.error!!, changingPassword = _changingPassword, notice = _notice),
                )
            }
            return
        }

        setState { _applyPasswordChangeStatePatch(passwordChangeStarted()) }
        try {
            _settingsController.changePassword(currentPassword = draft.currentPassword!!, newPassword = draft.newPassword!!)
            if (!mounted) return
            _currentPasswordController.clear()
            _newPasswordController.clear()
            _confirmPasswordController.clear()
            setState { _applyPasswordChangeStatePatch(passwordChangeSucceeded()) }
            unawaited { _loadSessions() }
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return
            setState { _applyPasswordChangeStatePatch(passwordChangeFailed(e)) }
        }
    }

    private val _canResetPasswordWithoutCurrentPassword: Boolean
        get() = _isManagingUser || (widget.passwordResetController?.isCurrentSessionAuthorizedFor(_user?.email) ?: false)

    private suspend fun _verifyEmailForPasswordReset() {
        if (_verifyingPasswordReset) return
        val controller = widget.passwordResetController
        val user = _user
        if (controller == null || user == null) {
            showFloatingErrorNotice(context, "暂时无法使用忘记密码功能")
            return
        }
        val login = if (user.username.trim().isNotEmpty()) user.username else (user.email ?: "")
        setState { _verifyingPasswordReset = true }
        val resetToken = verifyPasswordResetEmail(context = context, login = login, controller = controller)
        if (!mounted) return
        if (resetToken == null) {
            setState { _verifyingPasswordReset = false }
            return
        }
        try {
            controller.claimForCurrentSession(resetToken, email = user.email ?: "")
            if (!mounted) return
            setState {
                _verifyingPasswordReset = false
                _currentPasswordController.clear()
            }
            showFloatingSuccessNotice(context, "邮箱验证成功，可以直接设置新密码")
        } catch (error: Exception) {
            _rethrowCancellation(error)
            if (!mounted) return
            setState { _verifyingPasswordReset = false }
            showFloatingErrorNotice(context, userFacingErrorMessage(error, fallback = "邮箱验证失败"))
        }
    }

    private suspend fun _confirmDeleteAccount() {
        val user = _user
        if (!_settingsController.canDeleteAccount ||
            (_isManagingUser && (user?.isSuperuser ?: false)) ||
            !canStartAccountDeletion(hasApi = _settingsController.hasApi, user = user, deletingAccount = _deletingAccount)
        ) {
            return
        }
        val targetUser = user!!
        val confirmed =
            showDialog<Boolean>(context) { _ -> _DeleteAccountDialog(spec = accountDeletionConfirmationSpec(targetUser)) }
        if (confirmed != true || !mounted) return
        setState { _applyAccountDeletionStatePatch(accountDeletionStarted()) }
        try {
            _settingsController.deleteMyAccount()
            if (!mounted) return
            widget.onAccountDeleted?.invoke()
            if (!mounted) return
            setState {
                _applyAccountDeletionStatePatch(accountDeletionFinished(securityError = _securityError, notice = _notice))
            }
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return
            setState { _applyAccountDeletionStatePatch(accountDeletionFailed(e)) }
        }
    }

    private suspend fun _toggleManagedAccountSuspension() {
        val user = _user
        if (!_isManagingUser || user == null || user.isSuperuser || _changingAccountSuspension) {
            return
        }
        val suspending = !_accountSuspended
        val confirmed =
            showDialog<Boolean>(context) { dialogContext ->
                DialogFrame(
                    title = if (suspending) "确认封禁账号" else "确认解除封禁",
                    icon = if (suspending) Icons.Outlined.Block else Icons.Outlined.LockOpen,
                    adaptiveActions =
                        listOf(
                            ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(dialogContext).pop(false) }),
                            ResponsiveDialogAction(
                                label = if (suspending) "封禁账号" else "解除封禁",
                                onPressed = { Navigator.of(dialogContext).pop(true) },
                                tone = if (suspending) ButtonTone.danger else ButtonTone.primary,
                            ),
                        ),
                    child = {
                        Text(
                            if (suspending) {
                                "封禁后，${user.displayName} 的所有登录会话将立即失效，且无法登录。用户名、头像、资料和历史消息将保留。"
                            } else {
                                "解除封禁后，${user.displayName} 可以重新登录；此前已撤销的登录会话不会恢复。"
                            },
                            style = UiTypography.body.copyWith(color = UiColors.textSecondary, height = 1.55),
                        )
                    },
                )
            }
        if (confirmed != true || !mounted) return

        setState { _changingAccountSuspension = true }
        try {
            val updated = _settingsController.setManagedAccountSuspended(suspending)
            if (!mounted || updated == null) return
            setState {
                _user = updated
                _changingAccountSuspension = false
                _securityError = null
                _notice = if (suspending) "账号已封禁" else "账号已解除封禁"
                _markFloatingNoticeEvent("notice", _notice)
            }
            widget.onUserUpdated?.invoke(updated)
            unawaited { _loadSessions() }
        } catch (error: Exception) {
            _rethrowCancellation(error)
            if (!mounted) return
            setState {
                _changingAccountSuspension = false
                _securityError = userFacingErrorMessage(error, fallback = if (suspending) "封禁账号失败" else "解除封禁失败")
                _markFloatingNoticeEvent("securityError", _securityError)
            }
        }
    }

    private suspend fun _loadStoredAudioSettings() {
        try {
            val stored = widget.audioDeviceStore.read()
            if (!mounted) return
            val patch = audioStoredVolumesApplied(inputVolume = stored.inputVolume, outputVolume = stored.outputVolume)
            _rememberOutputVolume(patch.outputVolume)
            setState { _applyAudioVolumePatch(patch) }
            widget.onVolumeChanged?.invoke("audioinput", patch.inputVolume)
            widget.onVolumeChanged?.invoke("audiooutput", patch.outputVolume)
            val platformFrameRateCap = androidScreenShareFrameRateCap
            setState {
                _screenShareMaxHeight = stored.screenShareMaxHeight
                _screenShareFrameRate =
                    normalizedScreenShareFrameRate(stored.screenShareFrameRate, maxFrameRate = platformFrameRateCap)
            }
        } catch (e: Exception) {
            _rethrowCancellation(e)
        }
    }

    private fun _setScreenShareMaxHeight(height: Int) {
        val normalized = normalizedScreenShareMaxHeight(height)
        if (_screenShareMaxHeight == normalized) return
        setState { _screenShareMaxHeight = normalized }
        widget.onScreenShareMaxHeightChanged?.invoke(normalized)
        unawaited { widget.audioDeviceStore.writeScreenShareMaxHeight(normalized) }
    }

    private fun _setScreenShareFrameRate(frameRate: Int) {
        val platformFrameRateCap = androidScreenShareFrameRateCap
        val normalized = normalizedScreenShareFrameRate(frameRate, maxFrameRate = platformFrameRateCap)
        if (_screenShareFrameRate == normalized) return
        setState { _screenShareFrameRate = normalized }
        widget.onScreenShareFrameRateChanged?.invoke(normalized)
        unawaited { widget.audioDeviceStore.writeScreenShareFrameRate(normalized) }
    }

    private suspend fun _loadDevices() {
        setState {
            _applyAudioDeviceListPatch(
                audioDeviceListLoadStarted(
                    inputs = _audioInputs,
                    outputs = _audioOutputs,
                    selectedInput = _selectedInput,
                    selectedOutput = _selectedOutput,
                ),
            )
        }
        try {
            if (widget.androidSystemService.isSupported) {
                try {
                    widget.androidSystemService.requestBluetoothConnectPermission()
                } catch (e: Exception) {
                    _rethrowCancellation(e)
                    // Keep the built-in speaker/microphone list available even when the
                    // user declines nearby-device access.
                }
                if (!mounted) return
            }
            val devices = widget.audioDeviceService.enumerateDevices()
            _applyDevices(devices)
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (!mounted) return
            setState {
                _applyAudioDeviceListPatch(
                    audioDeviceListLoadFailed(
                        inputs = _audioInputs,
                        outputs = _audioOutputs,
                        selectedInput = _selectedInput,
                        selectedOutput = _selectedOutput,
                        failure = e,
                    ),
                )
            }
        }
    }

    private suspend fun _applyDevices(devices: List<AudioDeviceInfo>) {
        if (!mounted) return
        val systemDefaultInputId = _systemDefaultInputId
        val systemDefaultOutputId = _systemDefaultOutputId
        var restored: RestoredAudioDevices<AudioDeviceInfo> = RestoredAudioDevices()
        try {
            restored =
                restoreStoredAudioDevices(
                    widget.audioDeviceStore,
                    audioDevices = widget.audioDeviceService,
                    devices = devices,
                    systemDefaultInputId = systemDefaultInputId,
                    systemDefaultOutputId = systemDefaultOutputId,
                )
        } catch (e: Exception) {
            _rethrowCancellation(e)
            // Device choices are a local convenience. If storage or OS routing fails,
            // keep rendering the current device list and let the user re-select.
        }
        if (!mounted) return
        val systemDefaultInput =
            if (systemDefaultInputId == null) {
                null
            } else {
                storedAudioDeviceFrom(
                    devices,
                    kind = "audioinput",
                    deviceId = systemDefaultInputId,
                    kindOf = ::audioDeviceInfoKind,
                    deviceIdOf = ::audioDeviceInfoId,
                )
            }
        val systemDefaultOutput =
            if (systemDefaultOutputId == null) {
                null
            } else {
                storedAudioDeviceFrom(
                    devices,
                    kind = "audiooutput",
                    deviceId = systemDefaultOutputId,
                    kindOf = ::audioDeviceInfoKind,
                    deviceIdOf = ::audioDeviceInfoId,
                )
            }
        setState {
            _applyAudioDeviceListPatch(
                audioDeviceListApplied(
                    devices = devices,
                    restoredInput = restored.input,
                    restoredOutput = restored.output,
                    hardwareInput = widget.audioDeviceService.selectedAudioInput,
                    hardwareOutput = widget.audioDeviceService.selectedAudioOutput,
                    currentInput = _selectedInput,
                    currentOutput = _selectedOutput,
                    kindOf = ::audioDeviceInfoKind,
                    deviceIdOf = ::audioDeviceInfoId,
                    error = _error,
                    systemDefaultInput = systemDefaultInput,
                    systemDefaultOutput = systemDefaultOutput,
                ),
            )
        }
    }

    // Re-runs device selection when OS defaults change while Settings is open.
    // restoreStoredAudioDevices only falls back to the system defaults when the
    // user has not pinned a device, so explicit choices are preserved.
    private suspend fun _onSystemDefaultAudioChanged() {
        if (!mounted || !_voiceInitialized) return
        _applyDevices(_audioInputs + _audioOutputs)
    }

    private suspend fun _selectInput(device: AudioDeviceInfo) {
        val wasTestingInput = _testingInput
        val wasTestingOutput = _testingOutput
        val didSelect =
            _selectDevice(
                device,
                { widget.audioDeviceService.selectAudioInput(device) },
                {
                    widget.audioDeviceStore.writeInputDevicePreference(
                        deviceId = device.deviceId,
                        label = device.label,
                        groupId = device.groupId,
                    )
                },
            )
        if (!didSelect) return
        val effects = audioInputDeviceSelectedEffects(wasTestingInput = wasTestingInput, wasTestingOutput = wasTestingOutput)
        if (effects.restartInputTest) _restartInputTest()
        if (effects.restartOutputTest) _restartOutputTest()
    }

    private suspend fun _selectOutput(device: AudioDeviceInfo) {
        val didSelect =
            _selectDevice(
                device,
                { widget.audioDeviceService.selectAudioOutput(device) },
                {
                    widget.audioDeviceStore.writeOutputDevicePreference(
                        deviceId = device.deviceId,
                        label = device.label,
                        groupId = device.groupId,
                    )
                },
            )
        if (!didSelect) return
        val effects = audioOutputDeviceSelectedEffects()
        if (effects.restartOutputTest) _restartOutputTest()
    }

    private suspend fun _selectDevice(
        device: AudioDeviceInfo,
        select: suspend () -> Unit,
        rememberSelection: suspend () -> Unit,
    ): Boolean {
        if (!canStartAudioDeviceSelection(_busyDeviceId)) return false
        setState {
            _applyAudioDeviceSelectionPatch(
                audioDeviceSelectionStarted(
                    device = device,
                    selectedInput = _selectedInput,
                    selectedOutput = _selectedOutput,
                    kindOf = ::audioDeviceInfoKind,
                    deviceIdOf = ::audioDeviceInfoId,
                ),
            )
        }
        var didSelect = false
        try {
            select()
            var storageError: Any? = null
            try {
                rememberSelection()
            } catch (e: Exception) {
                _rethrowCancellation(e)
                storageError = e
            }
            if (!mounted) return false
            setState {
                _applyAudioDeviceSelectionPatch(
                    audioDeviceSelectionSucceeded(
                        device = device,
                        selectedInput = _selectedInput,
                        selectedOutput = _selectedOutput,
                        kindOf = ::audioDeviceInfoKind,
                        storageFailure = storageError,
                    ),
                )
            }
            widget.onDeviceSelected?.invoke(device.kind, device.deviceId)
            didSelect = true
        } catch (e: Exception) {
            _rethrowCancellation(e)
            if (mounted) {
                setState {
                    _applyAudioDeviceSelectionPatch(
                        audioDeviceSelectionFailed(selectedInput = _selectedInput, selectedOutput = _selectedOutput, failure = e),
                    )
                }
            }
        }
        return didSelect
    }

    private suspend fun _setInputVolume(volume: Double) {
        val normalized = normalizedAudioVolume(volume)
        val patch =
            audioInputVolumeChanged(
                inputVolume = normalized,
                outputVolume = _outputVolume,
                restoreOutputVolume = _restoredOutputVolume(),
            )
        _setAudioVolumes(patch)
    }

    private suspend fun _setOutputVolume(volume: Double) {
        val normalized = normalizedAudioVolume(volume)
        if (normalized == 0.0) {
            _rememberOutputVolume(_outputVolume)
        } else {
            _rememberOutputVolume(normalized)
        }
        val patch = audioOutputVolumeChanged(inputVolume = _inputVolume, outputVolume = normalized)
        _setAudioVolumes(patch)
    }

    private suspend fun _setAudioVolumes(patch: AudioVolumePatch) {
        val previousInputVolume = _inputVolume
        val previousOutputVolume = _outputVolume
        if (patch.inputVolume == previousInputVolume && patch.outputVolume == previousOutputVolume) {
            return
        }
        setState { _applyAudioVolumePatch(patch) }
        if (patch.outputVolume > 0) _rememberOutputVolume(patch.outputVolume)

        val inputTest = _inputTest
        if (patch.inputVolume != previousInputVolume) {
            widget.onVolumeChanged?.invoke("audioinput", patch.inputVolume)
            unawaited { widget.audioDeviceStore.writeInputVolume(patch.inputVolume) }
            try {
                inputTest?.setCaptureVolume(patch.inputVolume)
            } catch (e: Exception) {
                _rethrowCancellation(e)
            }
        }

        val outputTest = _outputTest
        if (patch.outputVolume != previousOutputVolume) {
            widget.onVolumeChanged?.invoke("audiooutput", patch.outputVolume)
            unawaited { widget.audioDeviceStore.writeOutputVolume(patch.outputVolume) }
            try {
                outputTest?.setPlaybackVolume(patch.outputVolume)
            } catch (e: Exception) {
                _rethrowCancellation(e)
            }
        }
    }

    private suspend fun _toggleInputTest() {
        if (_testingInput) _stopInputTest() else _startInputTest()
    }

    private suspend fun _toggleOutputTest() {
        if (_testingOutput) _stopOutputTest() else _startOutputTest()
    }

    private suspend fun _restartInputTest() {
        _stopInputTest()
        if (mounted) _startInputTest()
    }

    private suspend fun _restartOutputTest() {
        _stopOutputTest()
        if (mounted) _startOutputTest()
    }

    private suspend fun _startInputTest() {
        if (_testingInput) return
        setState { _applyAudioTestStatePatch(audioInputTestStarted(testingOutput = _testingOutput, outputLevel = _outputLevel)) }
        var handle: AudioTestHandle? = null
        try {
            handle =
                _audioTestService.startInputTest(
                    inputDeviceId = _selectedInput?.deviceId,
                    volume = _inputVolume,
                    onLevel = { level ->
                        if (mounted) {
                            setState {
                                _applyAudioTestStatePatch(
                                    audioInputLevelChanged(
                                        level = level,
                                        inputVolume = _inputVolume,
                                        testingOutput = _testingOutput,
                                        outputLevel = _outputLevel,
                                        error = _error,
                                    ),
                                )
                            }
                        }
                    },
                )
            if (!mounted) {
                handle.dispose()
                return
            }
            _inputTest = handle
        } catch (e: Exception) {
            _rethrowCancellation(e)
            handle?.dispose()
            if (!mounted) return
            setState {
                _applyAudioTestStatePatch(audioInputTestFailed(testingOutput = _testingOutput, outputLevel = _outputLevel, failure = e))
            }
        }
    }

    private suspend fun _stopInputTest(updateState: Boolean = true) {
        val inputTest = _inputTest
        _inputTest = null
        inputTest?.dispose()
        if (updateState && mounted) {
            setState {
                _applyAudioTestStatePatch(audioInputTestStopped(testingOutput = _testingOutput, outputLevel = _outputLevel, error = _error))
            }
        }
    }

    private suspend fun _startOutputTest() {
        if (_testingOutput) return
        setState { _applyAudioTestStatePatch(audioOutputTestStarted(testingInput = _testingInput, inputLevel = _inputLevel)) }
        var handle: AudioTestHandle? = null
        try {
            handle =
                _audioTestService.startOutputTest(
                    volume = _outputVolume,
                    onLevel = { level ->
                        if (mounted) {
                            setState {
                                _applyAudioTestStatePatch(
                                    audioOutputLevelChanged(
                                        level = level,
                                        outputVolume = _outputVolume,
                                        testingInput = _testingInput,
                                        inputLevel = _inputLevel,
                                        error = _error,
                                    ),
                                )
                            }
                        }
                    },
                )
            if (!mounted) {
                handle.dispose()
                return
            }
            _outputTest = handle
        } catch (e: Exception) {
            _rethrowCancellation(e)
            handle?.dispose()
            if (!mounted) return
            setState {
                _applyAudioTestStatePatch(audioOutputTestFailed(testingInput = _testingInput, inputLevel = _inputLevel, failure = e))
            }
        }
    }

    private suspend fun _stopOutputTest(updateState: Boolean = true) {
        val outputTest = _outputTest
        _outputTest = null
        outputTest?.dispose()
        if (updateState && mounted) {
            setState {
                _applyAudioTestStatePatch(audioOutputTestStopped(testingInput = _testingInput, inputLevel = _inputLevel, error = _error))
            }
        }
    }

    private val _isRefreshing: Boolean
        get() =
            settingsSectionRefreshing(
                section = _section,
                loadingAccount = _loadingAccount,
                loadingPreferences = _loadingCloseBehavior,
                loadingStickers = _loadingStickers,
                loadingSessions = _loadingSessions,
                loadingVoice = _loading,
                loadingPlaylists = _loadingPlaylists,
                loadingAbout = _loadingAbout || _checkingAppVersion,
            )

    private fun _selectSection(section: SettingsSection) {
        val patch = settingsSectionSelected(section = section, sessionsEmpty = _sessions.isEmpty(), loadingSessions = _loadingSessions)
        setState { _applySettingsSectionPatch(patch) }
        if (patch.shouldLoadStickers) {
            unawaited { _ensureStickersLoaded() }
        }
        if (patch.shouldLoadSessions) {
            unawaited { _loadSessions() }
        }
        if (patch.shouldInitializeVoice) {
            unawaited { _ensureVoiceInitialized() }
        }
    }

    private fun _sectionSummaries(): Map<SettingsSection, String> {
        val user = _user
        val language =
            when (_language) {
                "zh-Hant" -> "繁體中文"
                "en" -> "English"
                else -> "简体中文"
            }
        val email = _normalizedAccountEmail
        val stickerCount = _allStickerItems().size
        return linkedMapOf(
            SettingsSection.profile to if (user == null) "未登录" else "@${user.username}",
            // PORT: the desktop summary also named the close behaviour.
            SettingsSection.preferences to language,
            SettingsSection.security to
                if (email.isEmpty()) {
                    "未绑定邮箱"
                } else if (_accountEmailVerified) {
                    "邮箱已验证"
                } else {
                    "邮箱待验证"
                },
            SettingsSection.voice to "输入 ${audioVolumePercentText(_inputVolume)} · 输出 ${audioVolumePercentText(_outputVolume)}",
            SettingsSection.stickers to if (stickerCount == 0) "上传与整理表情" else "$stickerCount 个表情",
            SettingsSection.playlists to "整理个人歌单",
            SettingsSection.about to appVersionNumberLabel(widget.appVersion),
        )
    }

    @Composable
    private fun _buildSearchResults(query: String) {
        val results = settingsSearchMatches(query, android = true)
        if (results.isEmpty()) {
            SettingsList(children = listOf { _SettingsEmptyState(text = "没有匹配的设置") })
            return
        }
        SettingsList(
            spacing = 6.dp,
            children =
                results.mapIndexed { i, entry ->
                    {
                        _SettingsSearchResultRow(
                            entry = entry,
                            query = query.trim(),
                            onPressed = {
                                _settingsSearchController.clear()
                                _selectSection(entry.section)
                            },
                            modifier = Modifier.testTag(settingsSearchResultTag(i)),
                        )
                    }
                },
        )
    }

    @Composable
    private fun _buildSectionContent() {
        when (_section) {
            SettingsSection.profile -> _buildProfileContent()
            SettingsSection.preferences -> _buildPreferencesContent()
            SettingsSection.stickers -> _buildStickersContent()
            SettingsSection.playlists -> _buildMusicPlaylistsContent()
            SettingsSection.security -> _buildSecurityContent()
            SettingsSection.voice -> _buildVoiceContent()
            SettingsSection.about -> _buildAboutContent()
        }
    }

    @Composable
    private fun _buildMusicPlaylistsContent() {
        val api = widget.api
        MusicPlaylistsPanel(
            controller = _musicPlaylistsController,
            initialPlaylistId = widget.initialMusicPlaylistId,
            previewApi = if (api is MusicTrackPreviewApi) api else null,
            previewPlatformFactory = widget.musicTrackPreviewPlatformFactory,
            shareApi = if (_isManagingUser) null else widget.api,
            currentUser = _user ?: widget.currentUser,
            reloadToken = _playlistReloadToken,
            unavailableMessage = if (_isManagingUser) "管理其他账号时不能编辑个人歌单" else "我的歌单需要登录后从服务端读取",
            onLoadingChanged = { loading ->
                if (mounted && loading != _loadingPlaylists) {
                    setState { _loadingPlaylists = loading }
                }
            },
        )
    }

    @Composable
    private fun _buildStickersContent() {
        val unavailable = !_settingsController.hasApi
        val items = _filteredStickerItems()
        val totalCount = _allStickerItems().size
        val selectionNumbers = _stickerSelectionNumbers()
        val busy = _stickerManagementBusy
        val allVisibleSelected = stickerAllVisibleSelected(selectedStickerIds = _selectedStickerIds, visibleItems = items)
        val canPinSelection =
            canStartStickerSelectionAction(busy = busy, selectedStickerIds = _selectedStickerIds) &&
                stickerSelectionWouldChangePinnedOrder(
                    packs = _stickerPacks,
                    selectedStickerIds = _selectedStickerIds,
                    orderByPack = _stickerOrderDrafts,
                )
        val stickerBody: @Composable () -> Unit =
            when {
                _loadingStickers && _stickerPacks.isEmpty() -> {
                    { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { _SettingsProgressIndicator() } }
                }
                totalCount == 0 -> {
                    {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            _SettingsEmptyState(text = "暂无表情，点击本地上传会自动创建")
                        }
                    }
                }
                items.isEmpty() -> {
                    {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            _SettingsEmptyState(text = "没有匹配的表情")
                        }
                    }
                }
                else -> {
                    {
                        StickerGrid(
                            modifier = Modifier.testTag(personalStickerItemsScrollTag),
                            items = items,
                            managing = _managingStickers,
                            selectionNumbers = selectionNumbers,
                            busy = busy,
                            scrollable = true,
                            onTap = { item ->
                                if (_managingStickers) {
                                    _toggleStickerSelection(item.sticker.id)
                                } else {
                                    _previewSticker(item)
                                }
                            },
                        )
                    }
                }
            }

        if (unavailable) {
            SettingsList(children = listOf { _SettingsEmptyState(text = "表情包需要登录后从服务端读取") })
            return
        }
        val managing = _managingStickers
        val filterActive = _stickerFilterActive
        val actions =
            mutableListOf(
                StickerActionGridEntry(
                    label = if (managing) "删除" else "本地上传",
                    button =
                        ButtonWidget(
                            onPressed =
                                if (canStartStickerPrimaryAction(busy = busy)) {
                                    if (managing) ({ unawaited { _deleteSelectedStickers() } }) else ({ unawaited { _pickAndUploadStickers() } })
                                } else {
                                    null
                                },
                            loading = if (managing) _deletingStickers else _uploadingStickers,
                            tone = if (managing) ButtonTone.danger else ButtonTone.primary,
                            icon = { Icon(if (managing) Icons.Filled.DeleteOutline else Icons.Filled.UploadFile) },
                            width = Dp.Infinity,
                            child = { Text(if (managing) "删除" else "本地上传") },
                        ),
                ),
                StickerActionGridEntry(
                    label = if (managing) "取消管理" else "批量管理",
                    button =
                        ButtonWidget(
                            onPressed = if (canUseStickerManagementControl(busy = busy)) ({ _toggleStickerManageMode() }) else null,
                            selected = managing,
                            tone = if (managing) ButtonTone.primary else ButtonTone.neutral,
                            icon = { Icon(if (managing) Icons.Filled.Close else Icons.Filled.ChecklistRtl) },
                            width = Dp.Infinity,
                            child = { Text(if (managing) "取消管理" else "批量管理") },
                        ),
                ),
                StickerActionGridEntry(
                    label = "筛选",
                    button =
                        ButtonWidget(
                            onPressed = if (canUseStickerManagementControl(busy = busy)) ({ unawaited { _openStickerFilter() } }) else null,
                            selected = filterActive,
                            tone = if (filterActive) ButtonTone.primary else ButtonTone.neutral,
                            icon = { Icon(Icons.Outlined.FilterAlt) },
                            width = Dp.Infinity,
                            child = { Text("筛选") },
                        ),
                ),
            )
        if (managing) {
            val selectionLabel = stickerVisibleSelectionButtonText(selectedStickerIds = _selectedStickerIds, visibleItems = items)
            actions.add(
                StickerActionGridEntry(
                    label = "下载",
                    button =
                        ButtonWidget(
                            onPressed =
                                if (canStartStickerSelectionAction(busy = busy, selectedStickerIds = _selectedStickerIds)) {
                                    { unawaited { _downloadSelectedStickers() } }
                                } else {
                                    null
                                },
                            loading = _downloadingStickers,
                            icon = { Icon(Icons.Outlined.Download) },
                            width = Dp.Infinity,
                            child = { Text("下载") },
                        ),
                ),
            )
            actions.add(
                StickerActionGridEntry(
                    label = "置顶",
                    button =
                        ButtonWidget(
                            onPressed = if (canPinSelection) ({ unawaited { _pinSelectedStickers() } }) else null,
                            loading = _savingStickerOrder,
                            icon = { Icon(Icons.Filled.VerticalAlignTop) },
                            width = Dp.Infinity,
                            child = { Text("置顶") },
                        ),
                ),
            )
            actions.add(
                StickerActionGridEntry(
                    label = selectionLabel,
                    button =
                        ButtonWidget(
                            onPressed =
                                if (canSelectVisibleStickers(busy = busy, visibleItems = items)) ({ _selectAllVisibleStickers(items) }) else null,
                            selected = allVisibleSelected,
                            icon = { Icon(if (allVisibleSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank) },
                            width = Dp.Infinity,
                            child = { Text(selectionLabel) },
                        ),
                ),
            )
        }
        SettingsFixedHeaderCard(
            title = "表情包管理",
            spacing = 10.dp,
            trailing = {
                Text(
                    stickerManagementCountText(filterActive = filterActive, visibleCount = items.size, totalCount = totalCount),
                    style = uiTextStyle(color = _textMuted, fontSize = 12.0, fontWeight = FontWeight.W600),
                )
            },
            headerChildren = listOf { StickerActionGrid(actions = actions) },
            body = stickerBody,
        )
    }

    @Composable
    private fun _buildPreferencesContent() {
        val unavailable = !_settingsController.hasApi || _user == null
        val androidNotifications = widget.androidSystemService.isSupported && !_isManagingUser
        val children = mutableListOf<@Composable () -> Unit>()
        // PORT: Dart's "关闭方式" (window close behaviour) group only shows on
        // desktop.
        if (androidNotifications) {
            children.add {
                _SettingsGroup(
                    title = "系统通知",
                    trailing =
                        if (_savingAndroidNotifications) {
                            { _SettingsProgressIndicator(size = 16.dp, strokeWidth = 2.dp) }
                        } else {
                            null
                        },
                    children =
                        listOf(
                            {
                                _ToggleSetting(
                                    label = "允许房间消息通知",
                                    value = _androidNotificationPreferenceEnabled,
                                    enabled = !_savingAndroidNotifications,
                                    onChanged = { value -> unawaited { _setAndroidNotificationsEnabled(value) } },
                                )
                            },
                            _gap(10.dp),
                            {
                                Text(
                                    if (!_androidNotificationPreferenceEnabled) {
                                        "已在 Gang Chat 中关闭通知"
                                    } else if (_androidNotificationsAvailable) {
                                        "通知已开启；房间设置为“全部”时，后台消息会显示内容和数字角标"
                                    } else {
                                        "系统尚未允许通知，请在 Android 设置中开启"
                                    },
                                    style = uiTextStyle(color = _textSecondary, fontSize = 13.0, height = 1.45),
                                )
                            },
                            _gap(12.dp),
                            {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                    com.gangchat.client.ui.Button(
                                        onPressed = { unawaited { _openAndroidNotificationSettings() } },
                                        icon = { Icon(Icons.Outlined.Notifications) },
                                    ) {
                                        Text("系统通知设置")
                                    }
                                }
                            },
                        ),
                )
            }
        }
        if (unavailable) {
            children.add { _SettingsEmptyState(text = "语言偏好需要登录后从服务端读取") }
        } else {
            children.add {
                _SettingsGroup(
                    title = "语言切换",
                    children =
                        listOf {
                            _SegmentedSetting(
                                modifier = Modifier.testTag(settingsLanguageSegmentedTag),
                                label = "语言",
                                value = _language,
                                options =
                                    listOf(
                                        _SegmentOption(value = "zh-Hans", label = "简体中文"),
                                        _SegmentOption(value = "zh-Hant", label = "繁體中文"),
                                        _SegmentOption(value = "en", label = "English"),
                                    ),
                                onChanged = { value -> setState { _language = value } },
                            )
                        },
                )
            }
        }
        children.add {
            val saving = _savingAccount || _savingCloseBehavior
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                com.gangchat.client.ui.Button(
                    onPressed = if (saving) null else ({ unawaited { _savePreferences() } }),
                    loading = saving,
                    icon = { Icon(Icons.Outlined.Save) },
                    tone = ButtonTone.primary,
                ) {
                    Text("保存偏好设置")
                }
            }
        }
        SettingsList(children = children)
    }

    private suspend fun _openProfileAvatarPreview(imageUrl: String?) {
        val url = imageUrl?.trim()
        val opener = widget.stickerImagePreviewOpener
        if (url == null || url.isEmpty() || opener == null) return
        val displayName = _displayNameController.text.trim()
        val username = _user?.username?.trim() ?: ""
        val stem =
            if (displayName.isNotEmpty()) {
                displayName
            } else if (username.isNotEmpty()) {
                username
            } else {
                "avatar"
            }
        opener(context, url, "$stem-avatar.png", true)
    }

    @Composable
    private fun _buildProfileContent() {
        val user = _user
        val unavailable = !_settingsController.hasApi || user == null
        val appConfig = AppConfigScope.of(context)
        val avatarPreviewUrl =
            appConfig.resolveAssetUrl(
                accountAvatarPreviewPath(
                    clearUploadedAvatar = _clearUploadedAvatar,
                    pendingAvatarUrl = _pendingAvatarUrl,
                    currentAvatarUrl = user?.avatarUrl,
                ),
            )
        val usernameValidationError = loginUsernameValidationError(_usernameController.text)
        val usernameEditable = user != null && (_isManagingUser || canEditUsername(user))
        val usernameChanged = user != null && _usernameController.text.trim() != user.username
        val usernameAvailabilityApplies =
            usernameChanged && usernameValidationError == null && _usernameAvailabilityQuery == _usernameController.text.trim()
        val usernameEffectiveError = usernameValidationError ?: if (usernameAvailabilityApplies) _usernameAvailabilityError else null
        val usernameChecking = usernameAvailabilityApplies && _checkingUsernameAvailability
        val children = mutableListOf<@Composable () -> Unit>()
        if (unavailable) {
            children.add { _SettingsEmptyState(text = "账号资料需要登录后从服务端读取") }
        } else {
            children.add {
                _SettingsGroup(
                    title = "账号标识",
                    children =
                        listOf(
                            { _CopyableField(label = "个人 UID", value = user!!.uid) },
                            _gap(14.dp),
                            {
                                _LabeledTextField(
                                    modifier = Modifier.testTag(settingsUsernameInputTag),
                                    label = "登录用户名",
                                    controller = _usernameController,
                                    enabled = usernameEditable,
                                    helperText = usernameHelperText(user!!),
                                    suffix =
                                        if (usernameChanged) {
                                            {
                                                _UsernameValidityIndicator(
                                                    error = usernameEffectiveError,
                                                    checking = usernameChecking,
                                                    enabled = usernameEditable,
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    onChanged = ::_onLoginUsernameChanged,
                                )
                            },
                        ),
                )
            }
            children.add {
                _SettingsGroup(
                    title = "默认资料",
                    children =
                        listOf(
                            {
                                _LabeledTextField(
                                    label = "用户名",
                                    controller = _displayNameController,
                                    helperText = "没有房间个人资料覆盖时展示。",
                                )
                            },
                            _gap(14.dp),
                            {
                                _SegmentedSetting(
                                    modifier = Modifier.testTag(settingsGenderSegmentedTag),
                                    label = "性别",
                                    value = _gender,
                                    options =
                                        listOf(
                                            _SegmentOption(value = "male", label = "男"),
                                            _SegmentOption(value = "female", label = "女"),
                                            _SegmentOption(value = "secret", label = "保密"),
                                        ),
                                    onChanged = { value ->
                                        setState {
                                            _applyAccountEditableFieldsPatch(
                                                accountGenderChanged(gender = value, emailPublic = _emailPublic, phoneNumberPublic = _phonePublic),
                                            )
                                        }
                                    },
                                )
                            },
                            _gap(14.dp),
                            {
                                AvatarPicker(
                                    label = "头像",
                                    displayName = _displayNameController.text,
                                    imageUrl = avatarPreviewUrl,
                                    defaultAvatarKey = _defaultAvatarKey,
                                    usingPreset = avatarPreviewUrl == null,
                                    uploading = _uploadingAvatar,
                                    enabled = !_uploadingAvatar,
                                    onUpload = { unawaited { _pickAndUploadAvatar() } },
                                    onPresetSelected = ::_selectDefaultAvatarKey,
                                    onImagePreview =
                                        if (widget.stickerImagePreviewOpener == null) {
                                            null
                                        } else {
                                            { unawaited { _openProfileAvatarPreview(avatarPreviewUrl) } }
                                        },
                                    uploadLabel = "上传头像",
                                )
                            },
                            _gap(14.dp),
                            {
                                _LabeledTextField(
                                    label = "签名",
                                    controller = _bioController,
                                    maxLines = 4,
                                    helperText = "用于个人资料面板展示。",
                                )
                            },
                        ),
                )
            }
            children.add {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    com.gangchat.client.ui.Button(
                        onPressed = if (_savingProfile || _savingAccount) null else ({ unawaited { _saveProfile() } }),
                        loading = _savingProfile,
                        icon = { Icon(Icons.Outlined.Save) },
                        tone = ButtonTone.primary,
                    ) {
                        Text("保存用户资料")
                    }
                }
            }
        }
        SettingsList(children = children)
    }

    @Composable
    private fun _buildSecurityContent() {
        val user = _user
        val unavailable = !_settingsController.hasApi || user == null
        val emailEmpty = _normalizedAccountEmail.isEmpty()
        val managedSuperuserPasswordBlocked = _isManagingUser && (user?.isSuperuser ?: false)
        val children = mutableListOf<@Composable () -> Unit>()
        if (unavailable) {
            children.add { _SettingsEmptyState(text = "安全设置需要登录后从服务端读取") }
            SettingsList(children = children)
            return
        }
        user!!
        children.add {
            _SettingsGroup(
                title = "绑定信息",
                children =
                    listOf(
                        {
                            _LabeledTextField(
                                modifier = Modifier.testTag(settingsEmailInputTag),
                                label = "邮箱绑定",
                                controller = _emailController,
                                keyboardType = KeyboardType.Email,
                                helperText = "用于登录、账号找回和安全通知。",
                                suffix =
                                    if (emailEmpty) {
                                        null
                                    } else if (_checkingEmailAvailability) {
                                        { _EmailVerificationStatusIndicator(checking = true) }
                                    } else if (_accountEmailVerified) {
                                        { _EmailVerificationStatusIndicator(checking = false) }
                                    } else {
                                        {
                                            EmailVerificationInputAction(
                                                actionKey = settingsEmailVerificationButtonTag,
                                                label = "验证",
                                                semanticsLabel = "验证邮箱",
                                                enabled = !_savingAccount,
                                                onPressed = { unawaited { _showAccountEmailVerification() } },
                                            )
                                        }
                                    },
                                onChanged = ::_onAccountEmailChanged,
                            )
                        },
                        _gap(10.dp),
                        {
                            _ToggleSetting(
                                label = "公开邮箱",
                                value = _emailPublic,
                                onChanged = { value ->
                                    setState {
                                        _applyAccountEditableFieldsPatch(
                                            accountEmailPublicChanged(gender = _gender, emailPublic = value, phoneNumberPublic = _phonePublic),
                                        )
                                    }
                                },
                            )
                        },
                        _gap(14.dp),
                        {
                            _LabeledTextField(
                                label = "手机号绑定",
                                controller = _phoneController,
                                keyboardType = KeyboardType.Phone,
                                helperText = "用于账号找回和安全通知，留空表示解绑。",
                            )
                        },
                        _gap(10.dp),
                        {
                            _ToggleSetting(
                                label = "公开手机号",
                                value = _phonePublic,
                                onChanged = { value ->
                                    setState {
                                        _applyAccountEditableFieldsPatch(
                                            accountPhoneNumberPublicChanged(gender = _gender, emailPublic = _emailPublic, phoneNumberPublic = value),
                                        )
                                    }
                                },
                            )
                        },
                        _gap(14.dp),
                        {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                com.gangchat.client.ui.Button(
                                    onPressed = if (_savingAccount) null else ({ unawaited { _saveAccount() } }),
                                    loading = _savingAccount,
                                    icon = { Icon(Icons.Outlined.Save) },
                                    tone = ButtonTone.primary,
                                ) {
                                    Text("保存绑定信息")
                                }
                            }
                        },
                    ),
            )
        }
        children.add {
            val passwordChildren = mutableListOf<@Composable () -> Unit>()
            if (!_canResetPasswordWithoutCurrentPassword) {
                passwordChildren.add {
                    _LabeledTextField(
                        label = "当前密码",
                        controller = _currentPasswordController,
                        obscureText = _obscureCurrentPassword,
                        onTogglePasswordVisibility = { _togglePasswordVisibility(PasswordVisibilityField.current) },
                    )
                }
                passwordChildren.add(_gap(12.dp))
            }
            passwordChildren.add {
                _LabeledTextField(
                    label = "新密码",
                    controller = _newPasswordController,
                    obscureText = _obscureNewPassword,
                    onTogglePasswordVisibility = { _togglePasswordVisibility(PasswordVisibilityField.newPassword) },
                )
            }
            passwordChildren.add(_gap(12.dp))
            passwordChildren.add {
                _LabeledTextField(
                    label = "确认新密码",
                    controller = _confirmPasswordController,
                    obscureText = _obscureConfirmPassword,
                    onTogglePasswordVisibility = { _togglePasswordVisibility(PasswordVisibilityField.confirm) },
                )
            }
            passwordChildren.add(_gap(14.dp))
            passwordChildren.add {
                ResponsiveDialogActionBar(
                    expanded = true,
                    actions =
                        listOf(
                            ResponsiveDialogAction(
                                label = "忘记密码",
                                icon = Icons.AutoMirrored.Filled.HelpOutline,
                                loading = _verifyingPasswordReset,
                                onPressed =
                                    if (_verifyingPasswordReset || _isManagingUser) null else ({ unawaited { _verifyEmailForPasswordReset() } }),
                            ),
                            ResponsiveDialogAction(
                                label = "更新密码",
                                icon = Icons.Filled.LockReset,
                                tone = ButtonTone.primary,
                                loading = _changingPassword,
                                onPressed =
                                    if (_changingPassword || managedSuperuserPasswordBlocked) null else ({ unawaited { _changePassword() } }),
                            ),
                        ),
                )
            }
            _SettingsGroup(title = "重置密码", children = passwordChildren)
        }
        children.add {
            _SettingsGroup(
                title = "账号活动",
                trailing = {
                    ButtonIcon(
                        tooltip = "刷新账号活动",
                        onPressed = if (_loadingSessions) null else ({ unawaited { _loadSessions() } }),
                        icon = { Icon(Icons.Filled.Refresh) },
                        size = 30.dp,
                    )
                },
                children =
                    listOf(
                        { _ReadOnlyLine(label = "账号创建时间", value = formatDateTime(user.createdAt)) },
                        _gap(14.dp),
                        { _SessionList(sessions = _sessions, loading = _loadingSessions) },
                    ),
            )
        }
        if (_isManagingUser) {
            children.add {
                val suspended = _accountSuspended
                _SettingsGroup(
                    title = "账号状态",
                    danger = suspended,
                    children =
                        listOf(
                            {
                                Text(
                                    if (suspended) "账号封禁中" else "账号状态正常",
                                    style =
                                        UiTypography.body.copyWith(
                                            color = if (suspended) UiColors.danger else UiColors.textSecondary,
                                            fontWeight = FontWeight.W600,
                                        ),
                                )
                            },
                            _gap(6.dp),
                            {
                                Text(
                                    if (suspended) {
                                        "该账号当前无法登录；用户名、头像、资料和历史消息仍正常保留。"
                                    } else {
                                        "封禁会立即撤销该账号的所有登录会话，并阻止其再次登录。"
                                    },
                                    style = UiTypography.body.copyWith(color = UiColors.textSecondary, height = 1.5),
                                )
                            },
                            _gap(14.dp),
                            {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                    com.gangchat.client.ui.Button(
                                        onPressed =
                                            if (user.isSuperuser || _changingAccountSuspension) {
                                                null
                                            } else {
                                                { unawaited { _toggleManagedAccountSuspension() } }
                                            },
                                        loading = _changingAccountSuspension,
                                        icon = { Icon(if (suspended) Icons.Outlined.LockOpen else Icons.Outlined.Block) },
                                        tone = if (suspended) ButtonTone.primary else ButtonTone.danger,
                                    ) {
                                        Text(if (suspended) "解除封禁" else "封禁账号")
                                    }
                                }
                            },
                        ),
                )
            }
        }
        children.add {
            val canDelete =
                _settingsController.canDeleteAccount &&
                    !(_isManagingUser && user.isSuperuser) &&
                    canStartAccountDeletion(hasApi = _settingsController.hasApi, user = user, deletingAccount = _deletingAccount)
            _SettingsGroup(
                title = "注销账号",
                danger = true,
                children =
                    listOf(
                        {
                            Text(
                                accountDeletionDescription(user),
                                style = uiTextStyle(color = _textSecondary, fontSize = 13.0, height = 1.5),
                            )
                        },
                        _gap(14.dp),
                        {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                com.gangchat.client.ui.Button(
                                    onPressed = if (canDelete) ({ unawaited { _confirmDeleteAccount() } }) else null,
                                    loading = _deletingAccount,
                                    icon = { Icon(Icons.Filled.DeleteOutline) },
                                    tone = ButtonTone.danger,
                                ) {
                                    Text("注销账号")
                                }
                            }
                        },
                    ),
            )
        }
        SettingsList(children = children)
    }

    private fun _togglePasswordVisibility(field: PasswordVisibilityField) {
        setState {
            _applyPasswordVisibilityPatch(
                passwordVisibilityToggled(
                    field = field,
                    obscureCurrentPassword = _obscureCurrentPassword,
                    obscureNewPassword = _obscureNewPassword,
                    obscureConfirmPassword = _obscureConfirmPassword,
                ),
            )
        }
    }

    @Composable
    private fun _buildAboutContent() {
        val versionChildren = mutableListOf<@Composable () -> Unit>()
        versionChildren.add { _CopyableField(label = "版本编号", value = appVersionNumberLabel(widget.appVersion)) }
        versionChildren.add(_gap(14.dp))
        versionChildren.add { _CopyableField(label = "发行时间", value = officialVersionTimeLabel(gangChatClientReleaseTimestamp)) }
        versionChildren.add(_gap(14.dp))
        versionChildren.add { _CopyableField(label = "上次更新时间", value = _lastUpdateDate) }
        versionChildren.add(_gap(14.dp))
        versionChildren.add {
            _ToggleSetting(
                label = "自动提示更新",
                value = _autoPromptUpdates,
                enabled = !_isManagingUser,
                onChanged = { value -> unawaited { _setAutoUpdatePrompt(value) } },
            )
        }
        if (_isManagingUser) {
            versionChildren.add(_gap(10.dp))
            versionChildren.add {
                Text("该设置仅保存在用户设备，无法远程读取", style = uiTextStyle(color = _textSecondary, fontSize = 13.0, height = 1.4))
            }
        }
        versionChildren.add(_gap(14.dp))
        versionChildren.add {
            com.gangchat.client.ui.Button(
                onPressed = if (_checkingAppVersion) null else ({ unawaited { _checkAppVersion() } }),
                loading = _checkingAppVersion,
                tone = ButtonTone.primary,
                width = Dp.Infinity,
            ) {
                Text("检查更新")
            }
        }
        SettingsList(
            children =
                listOf(
                    { _SettingsGroup(title = "版本信息", children = versionChildren) },
                    {
                        _SettingsGroup(
                            title = "意见反馈",
                            children =
                                listOf {
                                    com.gangchat.client.ui.Button(
                                        onPressed = if (_openingFeedbackMail) null else ({ unawaited { _openFeedbackMail() } }),
                                        loading = _openingFeedbackMail,
                                        tone = ButtonTone.primary,
                                        width = Dp.Infinity,
                                    ) {
                                        Text("意见反馈")
                                    }
                                },
                        )
                    },
                ),
        )
    }

    @Composable
    private fun _buildAppUpdateContent(update: AvailableAppUpdate) {
        AppUpdatePage(
            update = update,
            checking = _checkingAppVersion,
            downloading = _downloadingAppUpdate,
            downloadedBytes = _updateDownloadedBytes,
            downloadTotalBytes = _updateDownloadTotalBytes,
            error = _updateDownloadError,
            onBack = ::_closeAppUpdatePage,
            onRefresh = { unawaited { _checkAppVersion() } },
            onIgnoreVersion = { unawaited { _confirmIgnoreAvailableUpdate() } },
            onDownload = { unawaited { _confirmDownloadAvailableUpdate() } },
        )
    }

    @Composable
    private fun _buildVoiceContent() {
        val managing = _isManagingUser
        SettingsList(
            children =
                listOf(
                    {
                        _SettingsGroup(
                            title = "输入",
                            children =
                                listOf(
                                    {
                                        _SettingsSubPanel {
                                            _DeviceSection(
                                                title = "输入源",
                                                icon = Icons.Filled.Mic,
                                                devices = _audioInputs,
                                                selectedDevice = _selectedInput,
                                                busyDeviceId = _busyDeviceId,
                                                emptyText =
                                                    if (managing) {
                                                        "该设置仅保存在用户设备，无法远程读取"
                                                    } else if (_loading) {
                                                        "正在加载输入源"
                                                    } else {
                                                        "未找到输入源"
                                                    },
                                                fallbackLabel = "麦克风",
                                                onSelect = { device -> unawaited { _selectInput(device) } },
                                            )
                                        }
                                    },
                                    _gap(12.dp),
                                    {
                                        _SettingsSubPanel {
                                            _AudioControlPanel(
                                                title = "输入音量",
                                                icon = Icons.Filled.GraphicEq,
                                                volume = _inputVolume,
                                                level = _inputLevel,
                                                testing = _testingInput,
                                                testTooltip = audioInputTestTooltip(_testingInput),
                                                disabled = !managing && _audioInputs.isEmpty(),
                                                testDisabled = managing,
                                                onVolumeChanged = { value -> unawaited { _setInputVolume(value) } },
                                                onToggleTest = { unawaited { _toggleInputTest() } },
                                            )
                                        }
                                    },
                                ),
                        )
                    },
                    {
                        _SettingsGroup(
                            title = "输出",
                            children =
                                listOf(
                                    {
                                        _SettingsSubPanel {
                                            _DeviceSection(
                                                title = "输出源",
                                                icon = Icons.Filled.Headphones,
                                                devices = _audioOutputs,
                                                selectedDevice = _selectedOutput,
                                                busyDeviceId = _busyDeviceId,
                                                emptyText =
                                                    if (managing) {
                                                        "该设置仅保存在用户设备，无法远程读取"
                                                    } else if (_loading) {
                                                        "正在加载输出源"
                                                    } else {
                                                        "未找到输出源"
                                                    },
                                                fallbackLabel = "输出",
                                                onSelect = { device -> unawaited { _selectOutput(device) } },
                                            )
                                        }
                                    },
                                    _gap(12.dp),
                                    {
                                        _SettingsSubPanel {
                                            _AudioControlPanel(
                                                title = "输出音量",
                                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                                volume = _outputVolume,
                                                level = _outputLevel,
                                                testing = _testingOutput,
                                                testTooltip = audioOutputTestTooltip(_testingOutput),
                                                disabled = !managing && _audioOutputs.isEmpty(),
                                                testDisabled = managing,
                                                onVolumeChanged = { value -> unawaited { _setOutputVolume(value) } },
                                                onToggleTest = { unawaited { _toggleOutputTest() } },
                                            )
                                        }
                                    },
                                ),
                        )
                    },
                    {
                        _SettingsGroup(
                            title = "屏幕共享",
                            children =
                                listOf {
                                    _SettingsSubPanel {
                                        _ScreenShareQualitySection(
                                            selectedHeight = _screenShareMaxHeight,
                                            selectedFrameRate = _screenShareFrameRate,
                                            remoteUnavailable = managing,
                                            onHeightSelected = ::_setScreenShareMaxHeight,
                                            onFrameRateSelected = ::_setScreenShareFrameRate,
                                        )
                                    }
                                },
                        )
                    },
                ),
        )
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
                    if (!replaying && event == Lifecycle.Event.ON_RESUME) _onAppResumed()
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            replaying = false
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    @Composable
    override fun build() {
        _scope = rememberCoroutineScope()
        _AppLifecycleObserver()
        val availableUpdate = _availableAppUpdate
        if (_section == SettingsSection.about && availableUpdate != null) {
            FloatingNoticeEmitter(notices = _floatingNotices()) {
                Scaffold(backgroundColor = _primaryDarkLow, modifier = widget.modifier) {
                    _buildAppUpdateContent(availableUpdate)
                }
            }
            return
        }

        val searchQuery = _settingsSearchController.text.trim()
        val refreshing = _isRefreshing
        val onBack: (() -> Unit)? =
            if (widget.onClose != null || !widget.isSubWindow) {
                widget.onClose ?: { Navigator.of(context).pop() }
            } else {
                null
            }
        FloatingNoticeEmitter(notices = _floatingNotices()) {
            Scaffold(backgroundColor = _primaryDarkLow, modifier = widget.modifier) {
                SettingsScaffold(
                    icon = Icons.Outlined.Settings,
                    title = _pageTitle,
                    onBack = onBack,
                    headerAction = {
                        ButtonIcon(
                            tooltip = "刷新设置",
                            onPressed = if (refreshing) null else ({ unawaited { _refreshActiveSection() } }),
                            icon = { Icon(Icons.Filled.Refresh) },
                            size = 38.dp,
                            loading = refreshing,
                        )
                    },
                    sectionTitleOverride = if (searchQuery.isEmpty()) null else "搜索结果",
                    sectionMetaOverride =
                        if (searchQuery.isEmpty()) null else "${settingsSearchMatches(searchQuery, android = true).size} 项",
                    search = {
                        Input(
                            modifier = Modifier.testTag(settingsSearchInputTag),
                            controller = _settingsSearchController,
                            hintText = "搜索设置",
                            prefixIcon = Icons.Rounded.Search,
                            showClearButton = true,
                            maxLines = 1,
                            height = 38.dp,
                            onChanged = { _ -> setState() },
                        )
                    },
                    navigation =
                        SettingsTileNavigation(
                            value = _section,
                            onChanged = { section ->
                                if (_settingsSearchController.text.isNotEmpty()) {
                                    _settingsSearchController.clear()
                                }
                                _selectSection(section)
                            },
                            segments = _settingsSectionSegments,
                            summaries = _sectionSummaries(),
                        ),
                    body = {
                        if (searchQuery.isEmpty()) _buildSectionContent() else _buildSearchResults(searchQuery)
                    },
                )
            }
        }
    }

    companion object {
        private val _usernameAvailabilityDebounceDuration = 350.milliseconds
    }
}
