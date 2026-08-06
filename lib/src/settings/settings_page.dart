import 'dart:async';
import 'dart:io' show Platform;
import 'dart:typed_data';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';

import '../app/account_display.dart' as account_display;
import '../app/app_update.dart';
import '../app/account_forms.dart';
import '../app/account_sessions.dart';
import '../app/account_state.dart';
import '../app/auth_form.dart';
import '../app/audio_device_display.dart';
import '../app/audio_device_info.dart';
import '../app/audio_device_state.dart';
import '../app/audio_device_store.dart';
import '../app/audio_levels.dart';
import '../app/close_behavior.dart';
import '../app/email_verification_controller.dart';
import '../app/error_display.dart';
import '../app/confirmation.dart';
import '../app/language_preference.dart';
import '../app/password_reset_controller.dart';
import '../app/personal_music_playlists.dart';
import '../app/settings_about.dart';
import '../app/settings_controller.dart';
import '../app/settings_shell_state.dart';
import '../app/sticker_management.dart';
import '../app/sticker_ordering.dart' as sticker_ordering;
import '../app/sticker_uploads.dart';
import '../app/music_box_display.dart';
import '../app/music_track_preview.dart';
import '../live/audio_device_restorer.dart';
import '../live/audio_device_service.dart';
import '../live/audio_test_service.dart';
import '../live/screen_share_quality.dart';
import '../live/system_audio_devices.dart';
import '../protocol/api_client.dart';
import '../protocol/models.dart';
import '../protocol/sticker_pack_store.dart';
import '../shell/clipboard_service.dart';
import '../shell/android_system_service.dart';
import '../shell/app_update_gate.dart';
import '../shell/desktop_window_controller.dart';
import '../shell/email_verification_flow.dart';
import '../shell/file_selection_service.dart';
import '../shell/feedback_mail_service.dart';
import '../shell/install_info_service.dart';
import '../shell/local_auto_update_prompt_store.dart';
import '../shell/local_close_behavior_store.dart';
import '../shell/local_audio_device_store.dart';
import '../shell/local_language_preference_store.dart';
import '../shell/music_track_preview_service.dart';
import '../shell/release_update_service.dart';
import '../shell/password_reset_flow.dart';
import '../ui/avatar_crop_dialog.dart';
import '../ui/sticker_upload_adapter.dart';
import '../ui/ui.dart';
import '../home/music_track_profile_card.dart';

part 'settings_components.dart';
part 'settings_profile_widgets.dart';
part 'settings_audio_widgets.dart';
part 'settings_music_playlists.dart';

const _primaryDark = Color(0xFF14171D);
const _primaryDarkLow = Color(0xFF181C24);
const _borderColor = Color(0xFF2A2F38);
const _cyan = UiColors.controlAccent;
const _textPrimary = Color(0xFFECEFF1);
const _textSecondary = Color(0xFFB0B8C0);
const _textMuted = Color(0xFF6F7785);
const _danger = Color(0xFFE58383);

class SettingsPage extends StatefulWidget {
  const SettingsPage({
    super.key,
    this.isSubWindow = false,
    this.audioDeviceStore = const LocalAudioDeviceStore(),
    this.audioDeviceService = const LiveAudioDeviceService(),
    this.systemAudioDevices,
    this.systemDefaultAudioInput,
    this.controller,
    this.api,
    this.apiBaseUrl = '',
    this.emailVerificationController,
    this.passwordResetController,
    this.stickerPackStore = const StickerPackStore(),
    this.clipboardService = const ClipboardService(),
    this.fileSelectionService = const FileSelectionService(),
    this.feedbackMailService = const FeedbackMailService(),
    this.androidSystemService = const AndroidSystemService(),
    this.autoUpdatePromptStore = const LocalAutoUpdatePromptStore(),
    this.installInfoService = const InstallInfoService(),
    this.releaseUpdateService = const ReleaseUpdateService(),
    this.windowController,
    this.closeBehaviorStore = const LocalCloseBehaviorStore(),
    this.languageStore = const LocalLanguagePreferenceStore(),
    this.initialSection = SettingsSection.profile,
    this.initialAppUpdate,
    this.stickerImagePreviewOpener,
    this.appVersion = gangChatClientVersion,
    this.onAppUpdateDownloadCancellationChanged,
    this.currentUser,
    this.onUserUpdated,
    this.onDeviceSelected,
    this.onVolumeChanged,
    this.onScreenShareMaxHeightChanged,
    this.onScreenShareFrameRateChanged,
    this.onAccountDeleted,
    this.onClose,
    this.musicTrackPreviewPlatformFactory =
        const DefaultMusicTrackPreviewPlatformFactory(),
  });

  final bool isSubWindow;
  final AudioDeviceStore audioDeviceStore;
  final LiveAudioDeviceService audioDeviceService;
  final SystemAudioDevices? systemAudioDevices;
  // Kept for older tests/call sites that injected the macOS-only adapter.
  final SystemAudioDevices? systemDefaultAudioInput;
  final SettingsController? controller;
  final GangApi? api;
  final String apiBaseUrl;
  final EmailVerificationController? emailVerificationController;
  final PasswordResetController? passwordResetController;
  final StickerPackStore stickerPackStore;
  final ClipboardService clipboardService;
  final FileSelectionService fileSelectionService;
  final FeedbackMailService feedbackMailService;
  final AndroidSystemService androidSystemService;
  final AutoUpdatePromptStore autoUpdatePromptStore;
  final InstallInfoService installInfoService;
  final ReleaseUpdateService releaseUpdateService;
  final DesktopWindowController? windowController;
  final CloseBehaviorStore closeBehaviorStore;
  final LanguagePreferenceStore languageStore;
  final SettingsSection initialSection;
  final AvailableAppUpdate? initialAppUpdate;
  final StickerImagePreviewOpener? stickerImagePreviewOpener;
  final String appVersion;
  final ValueChanged<ReleaseDownloadCancellationToken?>?
  onAppUpdateDownloadCancellationChanged;
  final CurrentUser? currentUser;
  final ValueChanged<CurrentUser>? onUserUpdated;
  final void Function(String kind, String deviceId)? onDeviceSelected;
  final void Function(String kind, double volume)? onVolumeChanged;

  /// Fired when the user picks a screen-share resolution, so a live session can
  /// re-scale immediately. The choice is also persisted to [audioDeviceStore].
  final ValueChanged<int>? onScreenShareMaxHeightChanged;
  final ValueChanged<int>? onScreenShareFrameRateChanged;
  final Future<void> Function()? onAccountDeleted;
  final VoidCallback? onClose;
  final MusicTrackPreviewPlatformFactory musicTrackPreviewPlatformFactory;

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage>
    with WidgetsBindingObserver {
  final _usernameController = TextEditingController();
  final _displayNameController = TextEditingController();
  final _bioController = TextEditingController();
  final _emailController = TextEditingController();
  final _phoneController = TextEditingController();
  final _currentPasswordController = TextEditingController();
  final _newPasswordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();

  static const _usernameAvailabilityDebounceDuration = Duration(
    milliseconds: 350,
  );

  SettingsSection _section = SettingsSection.profile;
  CurrentUser? _user;
  List<UserSession> _sessions = const [];
  String _gender = 'secret';
  String _defaultAvatarKey = 'blue-3';
  bool _emailPublic = false;
  bool _phonePublic = false;
  bool _loadingAccount = false;
  bool _loadingSessions = false;
  bool _savingAccount = false;
  bool _savingProfile = false;
  bool _uploadingAvatar = false;
  bool _clearUploadedAvatar = false;
  String? _pendingAvatarAssetId;
  String? _pendingAvatarUrl;
  bool _changingPassword = false;
  bool _deletingAccount = false;
  bool _changingAccountSuspension = false;
  List<StickerPack> _stickerPacks = const [];
  List<String> _selectedStickerIds = <String>[];
  final Map<String, List<String>> _stickerOrderDrafts = {};
  bool _managingStickers = false;
  bool _loadingStickers = false;
  bool _uploadingStickers = false;
  bool _deletingStickers = false;
  bool _savingStickerOrder = false;
  bool _downloadingStickers = false;
  String _stickerFilterKeyword = '';
  String _stickerFilterMimeType = '';
  String? _stickerError;
  int _playlistReloadToken = 0;
  bool _loadingPlaylists = false;
  bool _obscureCurrentPassword = true;
  bool _obscureNewPassword = true;
  bool _obscureConfirmPassword = true;
  String? _accountError;
  String? _securityError;
  String? _aboutError;
  String? _notice;
  bool _loadingAbout = false;
  bool _checkingAppVersion = false;
  AvailableAppUpdate? _availableAppUpdate;
  bool _downloadingAppUpdate = false;
  int _updateDownloadedBytes = 0;
  int? _updateDownloadTotalBytes;
  String? _updateDownloadError;
  bool _openingFeedbackMail = false;
  bool _autoPromptUpdates = defaultAutoUpdatePromptEnabled;
  bool _androidNotificationPreferenceEnabled = true;
  bool _androidNotificationsAvailable = false;
  bool _savingAndroidNotifications = false;
  String _lastUpdateDate = installTimeLabel(null);
  Timer? _usernameAvailabilityDebounce;
  int _usernameAvailabilityRequestId = 0;
  String? _usernameAvailabilityQuery;
  bool _checkingUsernameAvailability = false;
  bool _checkingEmailAvailability = false;
  int _emailVerificationRequestId = 0;
  String? _verifiedEmail;
  String? _emailVerificationToken;
  bool _verifyingPasswordReset = false;
  String? _usernameAvailabilityError;
  int _floatingNoticeSerial = 0;
  final Map<String, int> _floatingNoticeEventKeys = {};

  StreamSubscription<List<AudioDeviceInfo>>? _deviceSubscription;
  List<AudioDeviceInfo> _audioInputs = const [];
  List<AudioDeviceInfo> _audioOutputs = const [];
  AudioDeviceInfo? _selectedInput;
  AudioDeviceInfo? _selectedOutput;
  String? _busyDeviceId;
  double _inputVolume = defaultAudioVolume;
  double _outputVolume = defaultAudioVolume;
  double _lastOutputVolumeBeforeMute = defaultAudioVolume;
  double _inputLevel = 0.0;
  double _outputLevel = 0.0;
  bool _testingInput = false;
  bool _testingOutput = false;
  bool _voiceInitialized = false;
  int _screenShareMaxHeight = defaultScreenShareMaxHeight;
  int _screenShareFrameRate = defaultScreenShareFrameRate;
  String? _error;
  bool _loading = false;
  String _language = 'zh-Hans';
  CloseBehavior _closeBehavior = defaultCloseBehavior;
  bool _loadingCloseBehavior = false;
  bool _savingCloseBehavior = false;
  String? _closeBehaviorError;
  final _audioTestService = AudioTestService();
  AudioTestHandle? _inputTest;
  AudioTestHandle? _outputTest;
  SystemAudioDevices? _systemAudioDevices;
  DesktopWindowController? _ownedWindowController;
  StreamSubscription<String?>? _systemDefaultInputSubscription;
  StreamSubscription<String?>? _systemDefaultOutputSubscription;
  String? _systemDefaultInputId;
  String? _systemDefaultOutputId;

  SystemAudioDevices get _systemAudio {
    return _systemAudioDevices ??=
        widget.systemAudioDevices ??
        widget.systemDefaultAudioInput ??
        SystemAudioDevices();
  }

  SettingsController get _settingsController {
    final injected = widget.controller;
    if (injected != null) return injected;
    return SettingsController(
      api: widget.api,
      apiBaseUrl: widget.apiBaseUrl,
      stickerPackStore: widget.stickerPackStore,
    );
  }

  bool get _isManagingUser => _settingsController.isManagingUser;
  PersonalMusicPlaylistsController get _musicPlaylistsController {
    final api = widget.api;
    return PersonalMusicPlaylistsController(
      !_isManagingUser && api is PersonalMusicPlaylistApi
          ? api as PersonalMusicPlaylistApi
          : null,
    );
  }

  bool get _accountSuspended =>
      _user?.status?.trim().toLowerCase() == 'suspended';

  String get _pageTitle {
    if (!_isManagingUser) return '设置';
    final displayName = _user?.displayName.trim() ?? '';
    return displayName.isEmpty ? '用户设置' : '$displayName 的用户设置';
  }

  DesktopWindowController get _windowController =>
      widget.windowController ??
      (_ownedWindowController ??= DesktopWindowController());

  @override
  void initState() {
    super.initState();
    if (widget.androidSystemService.isSupported) {
      WidgetsBinding.instance.addObserver(this);
    }
    _user = widget.currentUser;
    _syncUserFields(widget.currentUser);
    _section = widget.initialAppUpdate == null
        ? widget.initialSection
        : SettingsSection.about;
    _availableAppUpdate = widget.initialAppUpdate;
    unawaited(_loadCloseBehavior());
    unawaited(_loadAndroidNotificationSettings());
    unawaited(_loadAutoUpdatePrompt());
    unawaited(_loadInstallDate());
    unawaited(_loadAccount());
  }

  @override
  void didUpdateWidget(SettingsPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.currentUser != widget.currentUser) {
      _user = widget.currentUser;
      _syncUserFields(widget.currentUser);
    }
    if (oldWidget.closeBehaviorStore != widget.closeBehaviorStore) {
      unawaited(_loadCloseBehavior());
    }
    if (oldWidget.autoUpdatePromptStore != widget.autoUpdatePromptStore) {
      unawaited(_loadAutoUpdatePrompt());
    }
    if (oldWidget.installInfoService != widget.installInfoService) {
      unawaited(_loadInstallDate());
    }
    if (oldWidget.initialAppUpdate != widget.initialAppUpdate &&
        widget.initialAppUpdate != null) {
      _section = SettingsSection.about;
      _availableAppUpdate = widget.initialAppUpdate;
      _resetUpdateDownloadState();
    }
  }

  @override
  void dispose() {
    if (widget.androidSystemService.isSupported) {
      WidgetsBinding.instance.removeObserver(this);
    }
    unawaited(_stopInputTest(updateState: false));
    unawaited(_stopOutputTest(updateState: false));
    _usernameAvailabilityDebounce?.cancel();
    unawaited(_deviceSubscription?.cancel());
    unawaited(_systemDefaultInputSubscription?.cancel());
    unawaited(_systemDefaultOutputSubscription?.cancel());
    unawaited(_systemAudioDevices?.dispose());
    _usernameController.dispose();
    _displayNameController.dispose();
    _bioController.dispose();
    _emailController.dispose();
    _phoneController.dispose();
    _currentPasswordController.dispose();
    _newPasswordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed &&
        widget.androidSystemService.isSupported) {
      unawaited(_loadAndroidNotificationSettings());
    }
  }

  void _syncUserFields(CurrentUser? user) {
    if (user == null) return;
    widget.passwordResetController?.invalidateAuthorizationIfEmailChanged(
      user.email,
    );
    _usernameController.text = user.username;
    _displayNameController.text = user.displayName;
    _bioController.text = user.bio;
    _emailController.text = user.email ?? '';
    _emailVerificationRequestId++;
    _checkingEmailAvailability = false;
    _verifiedEmail = null;
    _emailVerificationToken = null;
    _phoneController.text = user.phoneNumber ?? '';
    _gender = account_display.normalizeGender(user.gender);
    _defaultAvatarKey = user.defaultAvatarKey;
    _emailPublic = user.emailPublic;
    _phonePublic = user.phoneNumberPublic;
    _language = normalizeAccountLanguage(user.language);
    _clearUploadedAvatar = false;
    _pendingAvatarAssetId = null;
    _pendingAvatarUrl = null;
    _resetUsernameAvailabilityCheck();
  }

  String get _normalizedAccountEmail =>
      _emailController.text.trim().toLowerCase();

  bool get _accountEmailMatchesUser {
    final user = _user;
    return user != null &&
        _normalizedAccountEmail == (user.email ?? '').trim().toLowerCase();
  }

  bool get _accountEmailVerified {
    if (_normalizedAccountEmail.isEmpty) return false;
    if (_accountEmailMatchesUser && (_user?.emailVerified ?? false)) {
      return true;
    }
    return _verifiedEmail == _normalizedAccountEmail &&
        _emailVerificationToken != null;
  }

  void _resetUsernameAvailabilityCheck() {
    _usernameAvailabilityDebounce?.cancel();
    _usernameAvailabilityRequestId++;
    _usernameAvailabilityQuery = null;
    _checkingUsernameAvailability = false;
    _usernameAvailabilityError = null;
  }

  void _markFloatingNoticeEvent(String channel, String? message) {
    if (message == null || message.trim().isEmpty) return;
    _floatingNoticeEventKeys[channel] = ++_floatingNoticeSerial;
  }

  Object? _floatingNoticeEventKey(String channel) {
    return _floatingNoticeEventKeys[channel];
  }

  Future<void> _loadAccount() async {
    setState(() => _applyAccountLoadPatch(accountLoadStarted(user: _user)));
    try {
      final user = await _settingsController.loadAccount();
      if (!mounted) return;
      if (user == null) {
        setState(
          () => _applyAccountLoadPatch(
            accountLoadCancelled(user: _user, accountError: _accountError),
          ),
        );
        return;
      }
      setState(() {
        _applyAccountLoadPatch(accountLoadSucceeded(user: user));
        _syncUserFields(user);
      });
      widget.onUserUpdated?.call(user);
      if (!_isManagingUser) {
        unawaited(_rememberLanguagePreference(user.language));
      }
    } catch (e) {
      if (!mounted) return;
      setState(
        () =>
            _applyAccountLoadPatch(accountLoadFailed(user: _user, failure: e)),
      );
    }
  }

  void _onLoginUsernameChanged(String value) {
    final user = _user;
    final username = value.trim();
    _usernameAvailabilityDebounce?.cancel();
    final requestId = ++_usernameAvailabilityRequestId;
    final shouldCheck =
        user != null &&
        username != user.username &&
        loginUsernameValidationError(username) == null &&
        _settingsController.hasApi;
    if (!shouldCheck) {
      setState(() {
        _usernameAvailabilityQuery = null;
        _checkingUsernameAvailability = false;
        _usernameAvailabilityError = null;
      });
      return;
    }

    setState(() {
      _usernameAvailabilityQuery = username;
      _checkingUsernameAvailability = true;
      _usernameAvailabilityError = null;
    });
    _usernameAvailabilityDebounce = Timer(
      _usernameAvailabilityDebounceDuration,
      () => unawaited(
        _checkLoginUsernameAvailability(
          user: user,
          username: username,
          requestId: requestId,
        ),
      ),
    );
  }

  void _onAccountEmailChanged(String value) {
    final normalized = value.trim().toLowerCase();
    _emailVerificationRequestId++;
    setState(() {
      _checkingEmailAvailability = false;
      if (_verifiedEmail != normalized) {
        _verifiedEmail = null;
        _emailVerificationToken = null;
      }
    });
  }

  Future<void> _showAccountEmailVerification() async {
    if (_savingAccount || _checkingEmailAvailability) return;
    final email = _emailController.text.trim();
    final validationError = registerEmailValidationError(email);
    if (validationError != null) {
      showFloatingErrorNotice(context, validationError);
      return;
    }
    final controller = widget.emailVerificationController;
    if (controller == null) {
      showFloatingErrorNotice(context, '暂时无法使用邮箱验证功能');
      return;
    }

    final requestId = ++_emailVerificationRequestId;
    setState(() => _checkingEmailAvailability = true);
    try {
      if (!_accountEmailMatchesUser) {
        final available = await controller.isEmailAvailable(email);
        if (!mounted || requestId != _emailVerificationRequestId) return;
        if (!available) {
          setState(() => _checkingEmailAvailability = false);
          showFloatingErrorNotice(context, '该邮箱已被其他用户使用');
          return;
        }
      }

      final challenge = _isManagingUser
          ? await controller.inspectOrStart(email)
          : await controller.inspectOrStartForCurrentUser(email);
      if (!mounted || requestId != _emailVerificationRequestId) return;
      setState(() => _checkingEmailAvailability = false);
      final verificationToken = await showEmailVerificationDialog(
        context: context,
        email: email,
        challenge: challenge,
        controller: controller,
      );
      if (!mounted ||
          verificationToken == null ||
          requestId != _emailVerificationRequestId ||
          _emailController.text.trim() != email) {
        return;
      }
      setState(() {
        _verifiedEmail = email.toLowerCase();
        _emailVerificationToken = verificationToken;
      });
    } catch (error) {
      if (!mounted || requestId != _emailVerificationRequestId) return;
      setState(() => _checkingEmailAvailability = false);
      showFloatingErrorNotice(context, emailVerificationErrorMessage(error));
    }
  }

  Future<void> _checkLoginUsernameAvailability({
    required CurrentUser user,
    required String username,
    required int requestId,
  }) async {
    try {
      final users = await _settingsController.searchUsers(
        query: username,
        limit: 8,
      );
      if (!mounted || requestId != _usernameAvailabilityRequestId) return;
      final error = loginUsernameAvailabilityError(
        user: user,
        username: username,
        candidates: users ?? const [],
      );
      setState(() {
        _usernameAvailabilityQuery = username;
        _checkingUsernameAvailability = false;
        _usernameAvailabilityError = error;
      });
    } catch (_) {
      if (!mounted || requestId != _usernameAvailabilityRequestId) return;
      setState(() {
        _usernameAvailabilityQuery = username;
        _checkingUsernameAvailability = false;
        _usernameAvailabilityError = '暂时无法检测登录用户名是否重复';
      });
    }
  }

  Future<String?> _ensureLoginUsernameAvailable({
    required CurrentUser user,
    required String username,
  }) async {
    final normalizedUsername = username.trim();
    if (_usernameAvailabilityQuery == normalizedUsername &&
        !_checkingUsernameAvailability) {
      return _usernameAvailabilityError;
    }

    _usernameAvailabilityDebounce?.cancel();
    final requestId = ++_usernameAvailabilityRequestId;
    setState(() {
      _usernameAvailabilityQuery = normalizedUsername;
      _checkingUsernameAvailability = true;
      _usernameAvailabilityError = null;
    });
    try {
      final users = await _settingsController.searchUsers(
        query: normalizedUsername,
        limit: 8,
      );
      if (!mounted || requestId != _usernameAvailabilityRequestId) {
        return null;
      }
      final error = loginUsernameAvailabilityError(
        user: user,
        username: normalizedUsername,
        candidates: users ?? const [],
      );
      setState(() {
        _checkingUsernameAvailability = false;
        _usernameAvailabilityError = error;
      });
      return error;
    } catch (_) {
      const error = '暂时无法检测登录用户名是否重复';
      if (!mounted || requestId != _usernameAvailabilityRequestId) {
        return error;
      }
      setState(() {
        _checkingUsernameAvailability = false;
        _usernameAvailabilityError = error;
      });
      return error;
    }
  }

  Future<void> _loadSessions() async {
    setState(
      () => _applyAccountSessionsLoadPatch(
        accountSessionsLoadStarted(sessions: _sessions),
      ),
    );
    try {
      final sessions = await _settingsController.loadSessions();
      if (!mounted) return;
      if (sessions == null) {
        setState(
          () => _applyAccountSessionsLoadPatch(
            accountSessionsLoadCancelled(
              sessions: _sessions,
              securityError: _securityError,
            ),
          ),
        );
        return;
      }
      setState(
        () => _applyAccountSessionsLoadPatch(
          accountSessionsLoadSucceeded(sessions: sessions),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyAccountSessionsLoadPatch(
          accountSessionsLoadFailed(sessions: _sessions, failure: e),
        ),
      );
    }
  }

  Future<void> _loadCloseBehavior() async {
    if (_isManagingUser) return;
    if (_loadingCloseBehavior) return;
    setState(() {
      _loadingCloseBehavior = true;
      _closeBehaviorError = null;
    });
    try {
      final behavior = await widget.closeBehaviorStore.read();
      if (!mounted) return;
      setState(() {
        _closeBehavior = behavior;
        _loadingCloseBehavior = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _loadingCloseBehavior = false;
        _closeBehaviorError = userFacingErrorMessage(
          error,
          fallback: '读取关闭方式失败',
        );
      });
    }
  }

  Future<void> _loadAutoUpdatePrompt() async {
    if (_isManagingUser) return;
    try {
      final enabled = await widget.autoUpdatePromptStore.read();
      if (!mounted) return;
      setState(() => _autoPromptUpdates = enabled);
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _aboutError = '读取自动提示更新失败';
        _markFloatingNoticeEvent('aboutError', _aboutError);
      });
    }
  }

  Future<void> _loadAndroidNotificationSettings() async {
    if (!widget.androidSystemService.isSupported || _isManagingUser) return;
    try {
      final values = await Future.wait<bool>([
        widget.androidSystemService.notificationPreferenceEnabled(),
        widget.androidSystemService.notificationsEnabled(),
      ]);
      if (!mounted) return;
      setState(() {
        _androidNotificationPreferenceEnabled = values[0];
        _androidNotificationsAvailable = values[1];
      });
    } catch (_) {}
  }

  Future<void> _setAndroidNotificationsEnabled(bool enabled) async {
    if (_savingAndroidNotifications) return;
    setState(() => _savingAndroidNotifications = true);
    try {
      await widget.androidSystemService.setNotificationPreferenceEnabled(
        enabled,
      );
      var available = false;
      if (enabled) {
        available = await widget.androidSystemService
            .requestNotificationPermission();
      }
      if (!mounted) return;
      setState(() {
        _androidNotificationPreferenceEnabled = enabled;
        _androidNotificationsAvailable = available;
      });
      final registration = await widget.androidSystemService.pushRegistration();
      final api = widget.api;
      if (registration != null && api != null) {
        await api.upsertPushDevice(
          provider: registration.provider,
          installationId: registration.installationId,
          token: registration.token,
          enabled: enabled,
        );
      }
    } catch (error) {
      if (!mounted) return;
      showFloatingErrorNotice(
        context,
        userFacingErrorMessage(error, fallback: '修改系统通知失败'),
      );
    } finally {
      if (mounted) setState(() => _savingAndroidNotifications = false);
    }
  }

  Future<void> _openAndroidNotificationSettings() async {
    try {
      await widget.androidSystemService.openNotificationSettings();
    } catch (error) {
      if (!mounted) return;
      showFloatingErrorNotice(
        context,
        userFacingErrorMessage(error, fallback: '无法打开系统通知设置'),
      );
    }
  }

  Future<void> _loadInstallDate() async {
    String? installedAt;
    try {
      installedAt = await widget.installInfoService.readInstalledAt();
    } catch (_) {}
    if (!mounted) return;
    setState(() => _lastUpdateDate = installTimeLabel(installedAt));
  }

  Future<void> _setAutoUpdatePrompt(bool value) async {
    if (_isManagingUser) return;
    if (_autoPromptUpdates == value) return;
    final previous = _autoPromptUpdates;
    setState(() {
      _autoPromptUpdates = value;
      _aboutError = null;
      _notice = null;
    });
    try {
      await widget.autoUpdatePromptStore.write(value);
      if (!mounted) return;
      setState(() {
        _notice = '自动提示更新已保存';
        _markFloatingNoticeEvent('notice', _notice);
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _autoPromptUpdates = previous;
        _aboutError = '自动提示更新保存失败';
        _markFloatingNoticeEvent('aboutError', _aboutError);
      });
    }
  }

  Future<void> _rememberLanguagePreference(String language) async {
    try {
      await widget.languageStore.write(language);
    } catch (_) {
      // A local language-cache failure should not block server-backed settings.
    }
  }

  void _setCloseBehavior(String value) {
    setState(() {
      _closeBehavior = closeBehaviorFromStorageValue(value);
      _closeBehaviorError = null;
    });
  }

  Future<void> _savePreferences() async {
    if (_savingAccount || _savingCloseBehavior) return;
    setState(() {
      _savingCloseBehavior = true;
      _closeBehaviorError = null;
      _notice = null;
    });
    if (!_isManagingUser) {
      try {
        await widget.closeBehaviorStore.write(_closeBehavior);
        if (!mounted) return;
        setState(() => _savingCloseBehavior = false);
      } catch (error) {
        if (!mounted) return;
        setState(() {
          _savingCloseBehavior = false;
          _closeBehaviorError = userFacingErrorMessage(
            error,
            fallback: '保存关闭方式失败',
          );
          _markFloatingNoticeEvent('closeBehaviorError', _closeBehaviorError);
        });
        return;
      }
    } else {
      setState(() => _savingCloseBehavior = false);
    }

    final user = _user;
    if (!_settingsController.hasApi || user == null) {
      if (!mounted) return;
      setState(() {
        _notice = '偏好设置已保存';
        _markFloatingNoticeEvent('notice', _notice);
      });
      return;
    }

    final draft = preferencesUpdateDraftFromForm(
      user: user,
      language: _language,
    );
    if (draft.error == null && draft.noChanges) {
      if (!_isManagingUser) {
        await _rememberLanguagePreference(_language);
      }
      if (!mounted) return;
      setState(() {
        _notice = '偏好设置已保存';
        _markFloatingNoticeEvent('notice', _notice);
      });
      return;
    }
    await _saveAccount(target: AccountFormSaveTarget.preferences);
  }

  Future<void> _ensureStickersLoaded({bool forceReload = false}) async {
    if (_loadingStickers) return;
    if (!forceReload && _stickerPacks.isNotEmpty) return;
    await _loadStickers(forceReload: forceReload);
  }

  Future<void> _loadStickers({bool forceReload = false}) async {
    setState(
      () => _applyStickerPackLoadPatch(
        stickerPacksLoadStarted(
          packs: _stickerPacks,
          selectedStickerIds: _selectedStickerIds,
        ),
      ),
    );
    try {
      final result = await _settingsController.loadPersonalStickerPacks(
        userId: _stickerCacheUserId,
        forceReload: forceReload,
      );
      if (!mounted) return;
      final packs = result?.packs;
      setState(() {
        _applyStickerPackLoadPatch(
          stickerPacksLoadSucceeded(
            packs: packs ?? _stickerPacks,
            selectedStickerIds: _selectedStickerIds,
          ),
        );
        if (packs != null) _syncStickerOrderDrafts(packs);
      });
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyStickerPackLoadPatch(
          stickerPacksLoadFailed(
            packs: _stickerPacks,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
          ),
        ),
      );
    }
  }

  String get _stickerCacheUserId => _user?.id ?? widget.currentUser?.id ?? '';

  void _applyStickerPackLoadPatch(StickerPackLoadPatch patch) {
    _stickerPacks = patch.packs;
    _selectedStickerIds = patch.selectedStickerIds;
    _loadingStickers = patch.loading;
    _stickerError = patch.error;
    _markFloatingNoticeEvent('stickerError', _stickerError);
  }

  void _applyStickerSelectionPatch(StickerSelectionPatch patch) {
    _managingStickers = patch.managing;
    _stickerFilterKeyword = patch.filterKeyword;
    _stickerFilterMimeType = patch.filterMimeType;
    _selectedStickerIds = patch.selectedStickerIds;
  }

  void _applyStickerActionPatch(StickerActionPatch patch) {
    _uploadingStickers = patch.uploading;
    _deletingStickers = patch.deleting;
    _savingStickerOrder = patch.savingOrder;
    _downloadingStickers = patch.downloading;
    _selectedStickerIds = patch.selectedStickerIds;
    _stickerError = patch.error;
    _notice = patch.notice;
    _markFloatingNoticeEvent('stickerError', _stickerError);
    _markFloatingNoticeEvent('notice', _notice);
  }

  void _applyAccountSessionsLoadPatch(AccountSessionsLoadPatch patch) {
    _sessions = patch.sessions;
    _loadingSessions = patch.loading;
    _securityError = patch.securityError;
    _markFloatingNoticeEvent('securityError', _securityError);
  }

  void _applyAccountLoadPatch(AccountLoadPatch patch) {
    _user = patch.user;
    _loadingAccount = patch.loading;
    _accountError = patch.accountError;
    _markFloatingNoticeEvent('accountError', _accountError);
  }

  void _applyAudioDeviceListPatch(AudioDeviceListPatch<AudioDeviceInfo> patch) {
    _audioInputs = patch.inputs;
    _audioOutputs = patch.outputs;
    _selectedInput = patch.selectedInput;
    _selectedOutput = patch.selectedOutput;
    _loading = patch.loading;
    _error = patch.error;
    _markFloatingNoticeEvent('audioError', _error);
  }

  void _applyAudioDeviceSelectionPatch(
    AudioDeviceSelectionPatch<AudioDeviceInfo> patch,
  ) {
    _selectedInput = patch.selectedInput;
    _selectedOutput = patch.selectedOutput;
    _busyDeviceId = patch.busyDeviceId;
    _error = patch.error;
    _markFloatingNoticeEvent('audioError', _error);
  }

  void _applyAudioVolumePatch(AudioVolumePatch patch) {
    _inputVolume = patch.inputVolume;
    _outputVolume = patch.outputVolume;
  }

  void _rememberOutputVolume(double volume) {
    _lastOutputVolumeBeforeMute = rememberedAudioVolume(volume);
  }

  double _restoredOutputVolume() {
    return restoredAudioVolume(_lastOutputVolumeBeforeMute);
  }

  void _applyAudioTestStatePatch(AudioTestStatePatch patch) {
    _testingInput = patch.testingInput;
    _testingOutput = patch.testingOutput;
    _inputLevel = patch.inputLevel;
    _outputLevel = patch.outputLevel;
    _error = patch.error;
    _markFloatingNoticeEvent('audioError', _error);
  }

  void _applyAccountFormSaveStatePatch(AccountFormSaveStatePatch patch) {
    _savingAccount = patch.savingAccount;
    _savingProfile = patch.savingProfile;
    _accountError = patch.accountError;
    _notice = patch.notice;
    _markFloatingNoticeEvent('accountError', _accountError);
    _markFloatingNoticeEvent('notice', _notice);
  }

  void _applyAccountEditableFieldsPatch(AccountEditableFieldsPatch patch) {
    _gender = patch.gender;
    _emailPublic = patch.emailPublic;
    _phonePublic = patch.phoneNumberPublic;
  }

  void _applyPasswordChangeStatePatch(PasswordChangeStatePatch patch) {
    _changingPassword = patch.changingPassword;
    _securityError = patch.securityError;
    _notice = patch.notice;
    _markFloatingNoticeEvent('securityError', _securityError);
    _markFloatingNoticeEvent('notice', _notice);
  }

  void _applyPasswordVisibilityPatch(PasswordVisibilityPatch patch) {
    _obscureCurrentPassword = patch.obscureCurrentPassword;
    _obscureNewPassword = patch.obscureNewPassword;
    _obscureConfirmPassword = patch.obscureConfirmPassword;
  }

  void _applyAccountDeletionStatePatch(AccountDeletionStatePatch patch) {
    _deletingAccount = patch.deletingAccount;
    _securityError = patch.securityError;
    _notice = patch.notice;
    _markFloatingNoticeEvent('securityError', _securityError);
    _markFloatingNoticeEvent('notice', _notice);
  }

  void _applyAccountAvatarStatePatch(AccountAvatarStatePatch patch) {
    _pendingAvatarAssetId = patch.pendingAvatarAssetId;
    _pendingAvatarUrl = patch.pendingAvatarUrl;
    _clearUploadedAvatar = patch.clearUploadedAvatar;
    _uploadingAvatar = patch.uploadingAvatar;
    _accountError = patch.accountError;
    _stickerError = patch.stickerError;
    _notice = patch.notice;
    _markFloatingNoticeEvent('accountError', _accountError);
    _markFloatingNoticeEvent('stickerError', _stickerError);
    _markFloatingNoticeEvent('notice', _notice);
  }

  void _applyAccountPresetAvatarSelectionPatch(
    AccountPresetAvatarSelectionPatch patch,
  ) {
    _defaultAvatarKey = patch.defaultAvatarKey;
    _pendingAvatarAssetId = patch.pendingAvatarAssetId;
    _pendingAvatarUrl = patch.pendingAvatarUrl;
    _clearUploadedAvatar = patch.clearUploadedAvatar;
    _notice = patch.notice;
    _markFloatingNoticeEvent('notice', _notice);
  }

  void _applySettingsSectionPatch(SettingsSectionPatch patch) {
    _section = patch.section;
    _notice = patch.notice;
    _markFloatingNoticeEvent('notice', _notice);
  }

  List<FloatingNotice> _floatingNotices() {
    final notices = <FloatingNotice>[];

    void add(
      String? message,
      FloatingNoticeTone tone, {
      Duration? duration = floatingNoticeVisibleDuration,
      required String channel,
    }) {
      if (message == null || message.trim().isEmpty) return;
      notices.add(
        FloatingNotice(
          message: message,
          tone: tone,
          duration: duration,
          eventKey: _floatingNoticeEventKey(channel),
        ),
      );
    }

    add(_notice, FloatingNoticeTone.success, channel: 'notice');
    add(
      _stickerError,
      FloatingNoticeTone.error,
      duration: null,
      channel: 'stickerError',
    );
    add(
      _accountError,
      FloatingNoticeTone.error,
      duration: null,
      channel: 'accountError',
    );
    add(
      _securityError,
      FloatingNoticeTone.error,
      duration: null,
      channel: 'securityError',
    );
    add(
      _aboutError,
      FloatingNoticeTone.error,
      duration: null,
      channel: 'aboutError',
    );
    add(
      _closeBehaviorError,
      FloatingNoticeTone.error,
      duration: null,
      channel: 'closeBehaviorError',
    );
    add(
      _updateDownloadError,
      FloatingNoticeTone.error,
      duration: null,
      channel: 'updateDownloadError',
    );
    add(
      _error,
      FloatingNoticeTone.error,
      duration: null,
      channel: 'audioError',
    );
    return notices;
  }

  void _syncStickerOrderDrafts(List<StickerPack> packs) {
    _stickerOrderDrafts
      ..clear()
      ..addEntries(
        packs.map(
          (pack) => MapEntry(
            pack.id,
            pack.stickers.map((sticker) => sticker.id).toList(),
          ),
        ),
      );
  }

  Future<void> _refreshActiveSection() async {
    switch (_section) {
      case SettingsSection.profile:
        await _loadAccount();
        break;
      case SettingsSection.preferences:
        await Future.wait<void>([
          _loadCloseBehavior(),
          _loadAndroidNotificationSettings(),
        ]);
        break;
      case SettingsSection.stickers:
        await _loadStickers(forceReload: true);
        break;
      case SettingsSection.playlists:
        if (mounted) {
          setState(() => _playlistReloadToken += 1);
        }
        break;
      case SettingsSection.security:
        await Future.wait([_loadAccount(), _loadSessions()]);
        break;
      case SettingsSection.voice:
        await _ensureVoiceInitialized(forceReload: true);
        break;
      case SettingsSection.about:
        await _refreshAboutInfo();
        break;
    }
  }

  Future<void> _refreshAboutInfo() async {
    if (_loadingAbout) return;
    setState(() => _loadingAbout = true);
    await Future.wait([_loadAutoUpdatePrompt(), _loadInstallDate()]);
    if (!mounted) return;
    setState(() => _loadingAbout = false);
  }

  Future<void> _checkAppVersion() async {
    if (_checkingAppVersion) return;
    final platform = _currentUpdatePlatform();
    if (platform == null) {
      setState(() {
        _aboutError = '当前平台暂不支持自动检查更新';
        _markFloatingNoticeEvent('aboutError', _aboutError);
      });
      return;
    }
    setState(() {
      _checkingAppVersion = true;
      _aboutError = null;
      _notice = null;
    });
    try {
      final update = await widget.releaseUpdateService.checkForUpdate(
        bucketUrl: AppConfigScope.of(context).releaseBucketUrl,
        currentVersion: widget.appVersion,
        platform: platform,
      );
      if (!mounted) return;
      setState(() {
        _checkingAppVersion = false;
        _availableAppUpdate = update;
        _resetUpdateDownloadState();
        _notice = update == null ? '当前已是最新版本' : null;
        _markFloatingNoticeEvent('notice', _notice);
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _checkingAppVersion = false;
        _aboutError = '检查更新失败';
        _markFloatingNoticeEvent('aboutError', _aboutError);
      });
    }
  }

  void _resetUpdateDownloadState() {
    _downloadingAppUpdate = false;
    _updateDownloadedBytes = 0;
    _updateDownloadTotalBytes = null;
    _updateDownloadError = null;
  }

  void _closeAppUpdatePage() {
    if (_downloadingAppUpdate) return;
    setState(() {
      _section = SettingsSection.about;
      _availableAppUpdate = null;
      _resetUpdateDownloadState();
    });
  }

  Future<void> _confirmIgnoreAvailableUpdate() async {
    final update = _availableAppUpdate;
    if (update == null || _downloadingAppUpdate) return;
    final confirmed = await _confirmAppUpdateAction(
      title: '忽略此版本',
      icon: Icons.notifications_off_outlined,
      body:
          '忽略 ${appVersionLabel(update.latestVersion)} 后，除非你主动点击检查更新，'
          '或出现更高版本，否则不会再主动提示该版本。',
      confirmLabel: '忽略此版本',
    );
    if (!confirmed || !mounted) return;
    try {
      await widget.autoUpdatePromptStore.writeIgnoredVersion(
        update.latestVersion,
      );
      if (!mounted) return;
      setState(() {
        _section = SettingsSection.about;
        _availableAppUpdate = null;
        _notice = '已忽略此版本 ${appVersionLabel(update.latestVersion)}';
        _markFloatingNoticeEvent('notice', _notice);
        _resetUpdateDownloadState();
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _updateDownloadError = '忽略此版本失败';
        _markFloatingNoticeEvent('updateDownloadError', _updateDownloadError);
      });
    }
  }

  Future<void> _confirmDownloadAvailableUpdate() async {
    final update = _availableAppUpdate;
    if (update == null || _downloadingAppUpdate) return;
    final confirmed = await _confirmAppUpdateAction(
      title: '下载新版本',
      icon: Icons.download_for_offline_outlined,
      body: updateDownloadConfirmationBody(update.asset.platform),
      confirmLabel: '下载新版本',
    );
    if (!confirmed || !mounted) return;
    if (update.asset.platform == AppUpdatePlatform.android) {
      try {
        await widget.androidSystemService.requestUpdateNotificationPermission();
        if (!mounted) return;
      } catch (_) {}
    }
    await _downloadAndInstallAvailableUpdate();
  }

  Future<bool> _confirmAppUpdateAction({
    required String title,
    required IconData icon,
    required String body,
    required String confirmLabel,
  }) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => DialogFrame(
        title: title,
        icon: icon,
        adaptiveActions: [
          ResponsiveDialogAction(
            label: '取消',
            onPressed: () => Navigator.of(context).pop(false),
          ),
          ResponsiveDialogAction(
            label: confirmLabel,
            onPressed: () => Navigator.of(context).pop(true),
            tone: ButtonTone.primary,
          ),
        ],
        child: Text(
          body,
          style: UiTypography.body.copyWith(
            color: UiColors.textSecondary,
            height: 1.55,
          ),
        ),
      ),
    );
    return confirmed ?? false;
  }

  Future<void> _downloadAndInstallAvailableUpdate() async {
    final update = _availableAppUpdate;
    if (update == null || _downloadingAppUpdate) return;
    final cancellationToken = ReleaseDownloadCancellationToken();
    widget.onAppUpdateDownloadCancellationChanged?.call(cancellationToken);
    var androidDownloadStatusSet = false;
    setState(() {
      _downloadingAppUpdate = true;
      _updateDownloadError = null;
      _updateDownloadedBytes = 0;
      _updateDownloadTotalBytes = null;
    });
    try {
      if (update.asset.platform == AppUpdatePlatform.android) {
        await widget.androidSystemService.setUpdateDownloadActive(true);
        androidDownloadStatusSet = true;
      }
      final file = await widget.releaseUpdateService.downloadUpdate(
        update,
        cancellationToken: cancellationToken,
        onProgress: ({required receivedBytes, totalBytes}) {
          if (!mounted) return;
          setState(() {
            _updateDownloadedBytes = receivedBytes;
            _updateDownloadTotalBytes = totalBytes;
          });
        },
      );
      if (!mounted) return;
      await widget.releaseUpdateService.startInstaller(
        file,
        platform: update.asset.platform,
      );
      if (shouldTerminateApplicationAfterInstallerLaunch(
        update.asset.platform,
      )) {
        await Future<void>.delayed(const Duration(milliseconds: 280));
        await _windowController.terminateApplication();
      } else if (mounted) {
        // Android's package installer reads the APK from this process through
        // FileProvider. Keep the process alive until the OS has finished its
        // prepare/verify phase; the OS will replace it when installation wins.
        setState(() => _downloadingAppUpdate = false);
      }
    } on ReleaseDownloadCancelledException {
      if (!mounted) return;
      setState(() {
        _downloadingAppUpdate = false;
        _updateDownloadedBytes = 0;
        _updateDownloadTotalBytes = null;
        _updateDownloadError = '下载已中断';
        _markFloatingNoticeEvent('updateDownloadError', _updateDownloadError);
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _downloadingAppUpdate = false;
        _updateDownloadError = userFacingErrorMessage(
          error,
          fallback: '下载或启动安装程序失败',
        );
        _markFloatingNoticeEvent('updateDownloadError', _updateDownloadError);
      });
    } finally {
      if (androidDownloadStatusSet) {
        try {
          await widget.androidSystemService.setUpdateDownloadActive(false);
        } catch (_) {
          // Notification decoration must not mask the actual update result.
        }
      }
      widget.onAppUpdateDownloadCancellationChanged?.call(null);
    }
  }

  AppUpdatePlatform? _currentUpdatePlatform() {
    return appUpdatePlatformForOperatingSystem(Platform.operatingSystem);
  }

  Future<void> _openFeedbackMail() async {
    if (_openingFeedbackMail) return;
    final senderEmail = boundEmailForFeedback(_user?.email);
    if (senderEmail == null) {
      setState(() {
        _aboutError = '当前账号未绑定邮箱，无法发起意见反馈';
        _markFloatingNoticeEvent('aboutError', _aboutError);
      });
      return;
    }
    final draft = FeedbackMailDraft(
      from: senderEmail,
      to: gangChatSupportEmail,
      subject: feedbackMailSubject(widget.appVersion),
      body: feedbackMailBody(
        senderEmail: senderEmail,
        currentVersion: widget.appVersion,
      ),
    );
    setState(() {
      _openingFeedbackMail = true;
      _aboutError = null;
      _notice = null;
    });
    try {
      await widget.feedbackMailService.openDraft(draft);
      if (!mounted) return;
      setState(() {
        _openingFeedbackMail = false;
        _notice = '已打开邮件客户端';
        _markFloatingNoticeEvent('notice', _notice);
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _openingFeedbackMail = false;
        _aboutError = '无法打开邮件客户端';
        _markFloatingNoticeEvent('aboutError', _aboutError);
      });
    }
  }

  Future<void> _ensureVoiceInitialized({bool forceReload = false}) async {
    if (_voiceInitialized && !forceReload) return;
    if (!_voiceInitialized) {
      _voiceInitialized = true;
      _deviceSubscription ??= widget.audioDeviceService.devicesChanged.listen((
        devices,
      ) {
        unawaited(_applyDevices(devices));
      });
      // Follow OS-selected audio endpoints when the user has not pinned one.
      // The subscriptions re-run selection whenever the system defaults change
      // underneath Settings.
      _systemDefaultInputSubscription ??= _systemAudio.inputChanges.listen((
        deviceId,
      ) {
        _systemDefaultInputId = deviceId;
        unawaited(_onSystemDefaultAudioChanged());
      });
      _systemDefaultOutputSubscription ??= _systemAudio.outputChanges.listen((
        deviceId,
      ) {
        _systemDefaultOutputId = deviceId;
        unawaited(_onSystemDefaultAudioChanged());
      });
      unawaited(_loadStoredAudioSettings());
    }
    final defaultDeviceIds = await Future.wait<String?>([
      _systemAudio.currentInputDeviceId(),
      _systemAudio.currentOutputDeviceId(),
    ]);
    _systemDefaultInputId = defaultDeviceIds[0];
    _systemDefaultOutputId = defaultDeviceIds[1];
    await _loadDevices();
  }

  Future<void> _saveAccount({
    AccountFormSaveTarget target = AccountFormSaveTarget.account,
  }) async {
    final user = _user;
    if (!_settingsController.hasApi ||
        user == null ||
        _savingAccount ||
        _savingProfile) {
      return;
    }

    final draft = switch (target) {
      AccountFormSaveTarget.preferences => preferencesUpdateDraftFromForm(
        user: user,
        language: _language,
      ),
      AccountFormSaveTarget.account => accountUpdateDraftFromForm(
        user: user,
        username: _usernameController.text,
        email: _emailController.text,
        emailPublic: _emailPublic,
        phoneNumber: _phoneController.text,
        phoneNumberPublic: _phonePublic,
        language: _language,
      ),
      AccountFormSaveTarget.profile => throw StateError(
        'Profile uses _saveProfile',
      ),
    };
    if (draft.error != null) {
      setState(
        () => _applyAccountFormSaveStatePatch(
          accountFormSaveValidationFailed(
            error: draft.error!,
            savingAccount: _savingAccount,
            savingProfile: _savingProfile,
            notice: _notice,
          ),
        ),
      );
      return;
    }
    final accountEmailUpdate = target == AccountFormSaveTarget.account;
    final emailIdentityChanged =
        accountEmailUpdate && !_accountEmailMatchesUser;
    final emailVerificationRequired =
        accountEmailUpdate && (emailIdentityChanged || !user.emailVerified);
    if (emailVerificationRequired && !_accountEmailVerified) {
      showFloatingErrorNotice(context, '请先验证邮箱');
      return;
    }
    final emailVerificationOnlyUpdate =
        emailVerificationRequired && draft.email == null;
    if (draft.noChanges && !emailVerificationOnlyUpdate) {
      if (target == AccountFormSaveTarget.preferences) {
        if (!_isManagingUser) {
          await _rememberLanguagePreference(_language);
        }
        if (!mounted) return;
      }
      setState(
        () => _applyAccountFormSaveStatePatch(
          accountFormSaveNoChanges(
            target: target,
            savingAccount: _savingAccount,
            savingProfile: _savingProfile,
            accountError: _accountError,
          ),
        ),
      );
      return;
    }

    setState(
      () => _applyAccountFormSaveStatePatch(
        accountFormSaveStarted(
          target: target,
          savingAccount: _savingAccount,
          savingProfile: _savingProfile,
        ),
      ),
    );
    try {
      final updated = await _settingsController.updateAccount(
        username: draft.username,
        email:
            draft.email ??
            (emailVerificationOnlyUpdate ? _emailController.text.trim() : null),
        emailVerificationToken: emailVerificationRequired
            ? _emailVerificationToken
            : null,
        emailPublic: draft.emailPublic,
        phoneNumber: draft.phoneNumber,
        phoneNumberPublic: draft.phoneNumberPublic,
        language: draft.language,
      );
      if (!mounted) return;
      if (updated == null) {
        setState(
          () => _applyAccountFormSaveStatePatch(
            accountFormSaveCancelled(
              target: target,
              savingAccount: _savingAccount,
              savingProfile: _savingProfile,
              accountError: _accountError,
              notice: _notice,
            ),
          ),
        );
        return;
      }
      if (target == AccountFormSaveTarget.preferences) {
        if (!_isManagingUser) {
          await _rememberLanguagePreference(updated.language);
        }
        if (!mounted) return;
      }
      setState(() {
        _user = updated;
        _syncUserFields(updated);
        _applyAccountFormSaveStatePatch(
          accountFormSaveSucceeded(
            target: target,
            savingAccount: _savingAccount,
            savingProfile: _savingProfile,
          ),
        );
      });
      widget.onUserUpdated?.call(updated);
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyAccountFormSaveStatePatch(
          accountFormSaveFailed(
            target: target,
            savingAccount: _savingAccount,
            savingProfile: _savingProfile,
            failure: e,
          ),
        ),
      );
    }
  }

  Future<void> _saveProfile() async {
    final user = _user;
    if (!_settingsController.hasApi ||
        user == null ||
        _savingProfile ||
        _savingAccount) {
      return;
    }

    final usernameChanged = _usernameController.text.trim() != user.username;
    final usernameDraft = usernameChanged
        ? loginUsernameUpdateDraftFromForm(
            user: user,
            username: _usernameController.text,
          )
        : const AccountUpdateDraft.noChanges();
    if (usernameDraft.error != null) {
      setState(
        () => _applyAccountFormSaveStatePatch(
          accountFormSaveValidationFailed(
            error: usernameDraft.error!,
            savingAccount: _savingAccount,
            savingProfile: _savingProfile,
            notice: _notice,
          ),
        ),
      );
      return;
    }
    if (!usernameDraft.noChanges) {
      final availabilityError = await _ensureLoginUsernameAvailable(
        user: user,
        username: usernameDraft.username!,
      );
      if (!mounted) return;
      if (availabilityError != null) {
        setState(
          () => _applyAccountFormSaveStatePatch(
            accountFormSaveValidationFailed(
              error: availabilityError,
              savingAccount: _savingAccount,
              savingProfile: _savingProfile,
              notice: _notice,
            ),
          ),
        );
        return;
      }
    }

    final profileDraft = profileUpdateDraftFromForm(
      user: user,
      displayName: _displayNameController.text,
      bio: _bioController.text,
      gender: _gender,
      defaultAvatarKey: _defaultAvatarKey,
      pendingAvatarAssetId: _pendingAvatarAssetId,
      clearUploadedAvatar: _clearUploadedAvatar,
    );
    if (profileDraft.error != null) {
      setState(
        () => _applyAccountFormSaveStatePatch(
          accountFormSaveValidationFailed(
            error: profileDraft.error!,
            savingAccount: _savingAccount,
            savingProfile: _savingProfile,
            notice: _notice,
          ),
        ),
      );
      return;
    }
    if (usernameDraft.noChanges && profileDraft.noChanges) {
      setState(
        () => _applyAccountFormSaveStatePatch(
          accountFormSaveNoChanges(
            target: AccountFormSaveTarget.profile,
            savingAccount: _savingAccount,
            savingProfile: _savingProfile,
            accountError: _accountError,
          ),
        ),
      );
      return;
    }

    setState(
      () => _applyAccountFormSaveStatePatch(
        accountFormSaveStarted(
          target: AccountFormSaveTarget.profile,
          savingAccount: _savingAccount,
          savingProfile: _savingProfile,
        ),
      ),
    );
    try {
      CurrentUser updated = user;
      if (!usernameDraft.noChanges) {
        final accountUpdated = await _settingsController.updateAccount(
          username: usernameDraft.username,
        );
        if (!mounted) return;
        if (accountUpdated == null) {
          setState(
            () => _applyAccountFormSaveStatePatch(
              accountFormSaveCancelled(
                target: AccountFormSaveTarget.profile,
                savingAccount: _savingAccount,
                savingProfile: _savingProfile,
                accountError: _accountError,
                notice: _notice,
              ),
            ),
          );
          return;
        }
        updated = accountUpdated;
      }
      if (!profileDraft.noChanges) {
        final profileUpdated = await _settingsController.updateProfile(
          displayName: profileDraft.displayName,
          bio: profileDraft.bio,
          gender: profileDraft.gender,
          defaultAvatarKey: profileDraft.defaultAvatarKey,
          avatarAssetId: profileDraft.avatarAssetId,
        );
        if (!mounted) return;
        if (profileUpdated == null) {
          setState(
            () => _applyAccountFormSaveStatePatch(
              accountFormSaveCancelled(
                target: AccountFormSaveTarget.profile,
                savingAccount: _savingAccount,
                savingProfile: _savingProfile,
                accountError: _accountError,
                notice: _notice,
              ),
            ),
          );
          return;
        }
        updated = profileUpdated;
      }
      setState(() {
        _user = updated;
        _syncUserFields(updated);
        _applyAccountFormSaveStatePatch(
          accountFormSaveSucceeded(
            target: AccountFormSaveTarget.profile,
            savingAccount: _savingAccount,
            savingProfile: _savingProfile,
          ),
        );
      });
      widget.onUserUpdated?.call(updated);
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyAccountFormSaveStatePatch(
          accountFormSaveFailed(
            target: AccountFormSaveTarget.profile,
            savingAccount: _savingAccount,
            savingProfile: _savingProfile,
            failure: e,
          ),
        ),
      );
    }
  }

  Future<void> _pickAndUploadAvatar() async {
    if (!_settingsController.hasApi || _uploadingAvatar) return;
    setState(
      () => _applyAccountAvatarStatePatch(
        accountAvatarPreparationStarted(
          target: AccountAvatarErrorTarget.account,
          pendingAvatarAssetId: _pendingAvatarAssetId,
          pendingAvatarUrl: _pendingAvatarUrl,
          clearUploadedAvatar: _clearUploadedAvatar,
          uploadingAvatar: _uploadingAvatar,
          accountError: _accountError,
          stickerError: _stickerError,
        ),
      ),
    );

    SelectedFile? file;
    try {
      file = await widget.fileSelectionService.openFile(
        acceptedTypeGroups: const [
          FileTypeGroup(
            label: '图片',
            extensions: ['png', 'jpg', 'jpeg', 'webp'],
          ),
        ],
      );
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyAccountAvatarStatePatch(
          accountAvatarActionFailed(
            target: AccountAvatarErrorTarget.account,
            pendingAvatarAssetId: _pendingAvatarAssetId,
            pendingAvatarUrl: _pendingAvatarUrl,
            clearUploadedAvatar: _clearUploadedAvatar,
            accountError: _accountError,
            stickerError: _stickerError,
            failure: account_display.avatarPickerOpenFailureMessage(e),
          ),
        ),
      );
      return;
    }
    if (file == null) return;

    Uint8List bytes;
    try {
      bytes = await file.readAsBytes();
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyAccountAvatarStatePatch(
          accountAvatarActionFailed(
            target: AccountAvatarErrorTarget.account,
            pendingAvatarAssetId: _pendingAvatarAssetId,
            pendingAvatarUrl: _pendingAvatarUrl,
            clearUploadedAvatar: _clearUploadedAvatar,
            accountError: _accountError,
            stickerError: _stickerError,
            failure: account_display.avatarReadFailureMessage(e),
          ),
        ),
      );
      return;
    }
    if (bytes.isEmpty) {
      setState(
        () => _applyAccountAvatarStatePatch(
          accountAvatarActionFailed(
            target: AccountAvatarErrorTarget.account,
            pendingAvatarAssetId: _pendingAvatarAssetId,
            pendingAvatarUrl: _pendingAvatarUrl,
            clearUploadedAvatar: _clearUploadedAvatar,
            accountError: _accountError,
            stickerError: _stickerError,
            failure: account_display.avatarEmptyFileMessage(),
          ),
        ),
      );
      return;
    }
    if (!mounted) return;

    final cropped = await showDialog<Uint8List>(
      context: context,
      barrierDismissible: false,
      builder: (context) => AvatarCropDialog(bytes: bytes),
    );
    if (cropped == null || !mounted) return;

    setState(
      () => _applyAccountAvatarStatePatch(
        accountAvatarUploadStarted(
          target: AccountAvatarErrorTarget.account,
          pendingAvatarAssetId: _pendingAvatarAssetId,
          pendingAvatarUrl: _pendingAvatarUrl,
          clearUploadedAvatar: _clearUploadedAvatar,
          accountError: _accountError,
          stickerError: _stickerError,
        ),
      ),
    );
    try {
      final asset = await _settingsController.uploadImageAsset(
        bytes: cropped,
        filename: account_display.avatarUploadFilename(file.name),
        purpose: 'avatar',
      );
      if (asset == null) {
        if (!mounted) return;
        setState(
          () => _applyAccountAvatarStatePatch(
            accountAvatarActionCancelled(
              target: AccountAvatarErrorTarget.account,
              pendingAvatarAssetId: _pendingAvatarAssetId,
              pendingAvatarUrl: _pendingAvatarUrl,
              clearUploadedAvatar: _clearUploadedAvatar,
              accountError: _accountError,
              stickerError: _stickerError,
            ),
          ),
        );
        return;
      }
      if (!mounted) return;
      setState(
        () => _applyAccountAvatarStatePatch(
          accountAvatarPendingUploadSucceeded(
            assetId: asset.id,
            assetUrl: asset.url,
            stickerError: _stickerError,
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyAccountAvatarStatePatch(
          accountAvatarActionFailed(
            target: AccountAvatarErrorTarget.account,
            pendingAvatarAssetId: _pendingAvatarAssetId,
            pendingAvatarUrl: _pendingAvatarUrl,
            clearUploadedAvatar: _clearUploadedAvatar,
            accountError: _accountError,
            stickerError: _stickerError,
            failure: e,
          ),
        ),
      );
    }
  }

  List<ManagedSticker> _allStickerItems() {
    return managedStickerItems(
      _stickerPacks,
      orderForPack: (pack) => _stickerOrderDrafts[pack.id],
    );
  }

  List<ManagedSticker> _filteredStickerItems() {
    return filteredManagedStickerItems(
      _allStickerItems(),
      keyword: _stickerFilterKeyword,
      mimeType: _stickerFilterMimeType,
    );
  }

  bool get _stickerFilterActive => stickerFilterActive(
    keyword: _stickerFilterKeyword,
    mimeType: _stickerFilterMimeType,
  );

  bool get _stickerManagementBusy => stickerManagementBusy(
    uploading: _uploadingStickers,
    deleting: _deletingStickers,
    savingOrder: _savingStickerOrder,
    downloading: _downloadingStickers,
  );

  Map<String, int> _stickerSelectionNumbers() {
    return stickerSelectionNumbers(_selectedStickerIds);
  }

  Future<StickerPack> _ensureActiveStickerPack() async {
    if (_stickerPacks.isNotEmpty) return _stickerPacks.first;

    if (!_settingsController.hasApi) {
      throw StateError(stickerPackRequiresServerMessage());
    }
    final created = await _settingsController.createStickerPack(
      name: defaultStickerPackName(StickerManagementScope.personal),
      sortOrder: (_stickerPacks.length + 1) * 10,
    );
    if (created == null) {
      throw StateError(stickerPackRequiresServerMessage());
    }
    if (mounted) {
      setState(() {
        _stickerPacks = [..._stickerPacks, created];
        _stickerOrderDrafts[created.id] = <String>[];
      });
    }
    return created;
  }

  Future<void> _pickAndUploadStickers() async {
    if (!_settingsController.hasApi || _stickerManagementBusy) return;

    List<SelectedFile> files;
    try {
      files = await widget.fileSelectionService.openFiles(
        acceptedTypeGroups: const [
          FileTypeGroup(
            label: '图片和 ZIP',
            extensions: ['png', 'jpg', 'jpeg', 'webp', 'gif', 'zip'],
          ),
        ],
      );
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyStickerActionPatch(
          stickerActionErrorShown(
            uploading: _uploadingStickers,
            deleting: _deletingStickers,
            savingOrder: _savingStickerOrder,
            downloading: _downloadingStickers,
            selectedStickerIds: _selectedStickerIds,
            failure: stickerPickerOpenFailureMessage(e),
            notice: _notice,
          ),
        ),
      );
      return;
    }
    if (files.isEmpty) return;

    setState(
      () => _applyStickerActionPatch(
        stickerActionStarted(
          action: StickerActionKind.upload,
          uploading: _uploadingStickers,
          deleting: _deletingStickers,
          savingOrder: _savingStickerOrder,
          downloading: _downloadingStickers,
          selectedStickerIds: _selectedStickerIds,
        ),
      ),
    );
    var uploadedCount = 0;
    try {
      final uploadItems = await stickerUploadItemsFromFiles(
        stickerUploadSourcesFromSelectedFiles(files),
        decodeImageDimensions: decodeStickerImageDimensions,
      );
      if (uploadItems.isEmpty) {
        throw StateError(stickerNoUploadableImagesMessage());
      }
      final pack = await _ensureActiveStickerPack();
      final uploadSortOrders = sticker_ordering.stickerSortOrdersBeforeExisting(
        pack,
        uploadItems.length,
      );
      for (final entry in uploadItems.asMap().entries) {
        final item = entry.value;
        final asset = await _settingsController.uploadImageAsset(
          bytes: item.bytes,
          filename: stickerUploadFilename(item.filename, entry.key),
          purpose: 'sticker',
        );
        if (asset == null) continue;
        await _settingsController.addSticker(
          packId: pack.id,
          assetId: asset.id,
          name: stickerNameFromFilename(item.filename),
          sortOrder: uploadSortOrders[entry.key],
        );
        uploadedCount += 1;
      }
      await _loadStickers(forceReload: true);
      if (!mounted) return;
      setState(
        () => _applyStickerActionPatch(
          stickerActionSucceeded(
            action: StickerActionKind.upload,
            uploading: _uploadingStickers,
            deleting: _deletingStickers,
            savingOrder: _savingStickerOrder,
            downloading: _downloadingStickers,
            selectedStickerIds: _selectedStickerIds,
            error: _stickerError,
            notice: stickerUploadNotice(
              scope: StickerManagementScope.personal,
              count: uploadedCount,
            ),
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyStickerActionPatch(
          stickerActionFailed(
            action: StickerActionKind.upload,
            uploading: _uploadingStickers,
            deleting: _deletingStickers,
            savingOrder: _savingStickerOrder,
            downloading: _downloadingStickers,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
          ),
        ),
      );
    }
  }

  void _toggleStickerManageMode() {
    setState(
      () => _applyStickerSelectionPatch(
        stickerManagementModeToggled(
          managing: _managingStickers,
          filterKeyword: _stickerFilterKeyword,
          filterMimeType: _stickerFilterMimeType,
        ),
      ),
    );
  }

  void _toggleStickerSelection(String stickerId) {
    setState(
      () => _applyStickerSelectionPatch(
        stickerSelectionToggled(
          managing: _managingStickers,
          filterKeyword: _stickerFilterKeyword,
          filterMimeType: _stickerFilterMimeType,
          selectedStickerIds: _selectedStickerIds,
          stickerId: stickerId,
        ),
      ),
    );
  }

  void _selectAllVisibleStickers(List<ManagedSticker> items) {
    setState(
      () => _applyStickerSelectionPatch(
        stickerVisibleSelectionToggled(
          managing: _managingStickers,
          busy: _stickerManagementBusy,
          filterKeyword: _stickerFilterKeyword,
          filterMimeType: _stickerFilterMimeType,
          selectedStickerIds: _selectedStickerIds,
          visibleItems: items,
        ),
      ),
    );
  }

  Future<void> _deleteSelectedStickers() async {
    final selectedIds = List<String>.from(_selectedStickerIds);
    if (!_settingsController.hasApi ||
        !canStartStickerSelectionAction(
          busy: _stickerManagementBusy,
          selectedStickerIds: selectedIds,
        )) {
      return;
    }
    final byStickerId = {
      for (final item in _allStickerItems()) item.sticker.id: item,
    };

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StickerConfirmDialog(
        title: stickerDeleteDialogTitle(StickerManagementScope.personal),
        body: stickerBulkDeleteConfirmBody(
          scope: StickerManagementScope.personal,
          count: selectedIds.length,
        ),
        confirmLabel: '删除',
        confirmIcon: Icons.delete_outline,
        danger: true,
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(
      () => _applyStickerActionPatch(
        stickerActionStarted(
          action: StickerActionKind.delete,
          uploading: _uploadingStickers,
          deleting: _deletingStickers,
          savingOrder: _savingStickerOrder,
          downloading: _downloadingStickers,
          selectedStickerIds: _selectedStickerIds,
        ),
      ),
    );
    try {
      for (final stickerId in selectedIds) {
        final item = byStickerId[stickerId];
        if (item == null) continue;
        await _settingsController.deleteSticker(
          packId: item.pack.id,
          stickerId: stickerId,
        );
      }
      await _loadStickers(forceReload: true);
      if (!mounted) return;
      setState(
        () => _applyStickerActionPatch(
          stickerActionSucceeded(
            action: StickerActionKind.delete,
            uploading: _uploadingStickers,
            deleting: _deletingStickers,
            savingOrder: _savingStickerOrder,
            downloading: _downloadingStickers,
            selectedStickerIds: _selectedStickerIds,
            error: _stickerError,
            notice: stickerDeletedNotice(
              scope: StickerManagementScope.personal,
              count: selectedIds.length,
            ),
            clearSelection: true,
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyStickerActionPatch(
          stickerActionFailed(
            action: StickerActionKind.delete,
            uploading: _uploadingStickers,
            deleting: _deletingStickers,
            savingOrder: _savingStickerOrder,
            downloading: _downloadingStickers,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
          ),
        ),
      );
    }
  }

  Future<void> _downloadSelectedStickers() async {
    final selectedIds = List<String>.from(_selectedStickerIds);
    await _downloadStickerIds(selectedIds);
  }

  Future<void> _downloadStickerIds(List<String> stickerIds) async {
    if (!_settingsController.hasApi ||
        !canStartStickerSelectionAction(
          busy: _stickerManagementBusy,
          selectedStickerIds: stickerIds,
        )) {
      return;
    }

    setState(
      () => _applyStickerActionPatch(
        stickerActionStarted(
          action: StickerActionKind.download,
          uploading: _uploadingStickers,
          deleting: _deletingStickers,
          savingOrder: _savingStickerOrder,
          downloading: _downloadingStickers,
          selectedStickerIds: _selectedStickerIds,
        ),
      ),
    );
    try {
      final downloaded = await _settingsController.downloadStickers(
        stickerIds: stickerIds,
      );
      if (downloaded == null) {
        if (!mounted) return;
        setState(
          () => _applyStickerActionPatch(
            stickerActionCancelled(
              action: StickerActionKind.download,
              uploading: _uploadingStickers,
              deleting: _deletingStickers,
              savingOrder: _savingStickerOrder,
              downloading: _downloadingStickers,
              selectedStickerIds: _selectedStickerIds,
            ),
          ),
        );
        return;
      }
      final location = await widget.fileSelectionService.getSaveLocation(
        suggestedName: downloaded.filename,
        acceptedTypeGroups: const [
          FileTypeGroup(
            label: '图片和 ZIP',
            extensions: ['png', 'jpg', 'jpeg', 'webp', 'gif', 'zip'],
          ),
        ],
        confirmButtonText: '保存',
      );
      if (location == null) {
        if (!mounted) return;
        setState(
          () => _applyStickerActionPatch(
            stickerActionCancelled(
              action: StickerActionKind.download,
              uploading: _uploadingStickers,
              deleting: _deletingStickers,
              savingOrder: _savingStickerOrder,
              downloading: _downloadingStickers,
              selectedStickerIds: _selectedStickerIds,
            ),
          ),
        );
        return;
      }
      await widget.fileSelectionService.saveBytesToLocation(
        bytes: downloaded.bytes,
        location: location,
        filename: downloaded.filename,
        mimeType: downloaded.mimeType,
      );
      if (!mounted) return;
      setState(
        () => _applyStickerActionPatch(
          stickerActionSucceeded(
            action: StickerActionKind.download,
            uploading: _uploadingStickers,
            deleting: _deletingStickers,
            savingOrder: _savingStickerOrder,
            downloading: _downloadingStickers,
            selectedStickerIds: _selectedStickerIds,
            error: _stickerError,
            notice: stickerDownloadNotice(
              scope: StickerManagementScope.personal,
              count: stickerIds.length,
            ),
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyStickerActionPatch(
          stickerActionFailed(
            action: StickerActionKind.download,
            uploading: _uploadingStickers,
            deleting: _deletingStickers,
            savingOrder: _savingStickerOrder,
            downloading: _downloadingStickers,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
          ),
        ),
      );
    }
  }

  Future<void> _pinSelectedStickers() async {
    final selectedIds = List<String>.from(_selectedStickerIds);
    if (!_settingsController.hasApi ||
        !canStartStickerSelectionAction(
          busy: _stickerManagementBusy,
          selectedStickerIds: selectedIds,
        ) ||
        !sticker_ordering.stickerSelectionWouldChangePinnedOrder(
          packs: _stickerPacks,
          selectedStickerIds: selectedIds,
          orderByPack: _stickerOrderDrafts,
        )) {
      return;
    }

    final selectedByPack = sticker_ordering.selectedStickerIdsByPack(
      _stickerPacks,
      selectedIds,
    );
    if (selectedByPack.isEmpty) {
      setState(
        () => _applyStickerActionPatch(
          stickerActionNoticeShown(
            uploading: _uploadingStickers,
            deleting: _deletingStickers,
            savingOrder: _savingStickerOrder,
            downloading: _downloadingStickers,
            selectedStickerIds: _selectedStickerIds,
            error: _stickerError,
            notice: stickerNoOrderChangeNotice(StickerManagementScope.personal),
          ),
        ),
      );
      return;
    }

    setState(
      () => _applyStickerActionPatch(
        stickerActionStarted(
          action: StickerActionKind.order,
          uploading: _uploadingStickers,
          deleting: _deletingStickers,
          savingOrder: _savingStickerOrder,
          downloading: _downloadingStickers,
          selectedStickerIds: _selectedStickerIds,
        ),
      ),
    );
    try {
      for (final pack in _stickerPacks) {
        final selectedInPack = selectedByPack[pack.id];
        if (selectedInPack == null || selectedInPack.isEmpty) continue;
        final nextOrder = sticker_ordering
            .stickerOrderWithStickerIdsPinnedToFront(
              pack,
              selectedInPack,
              order: _stickerOrderDrafts[pack.id],
            );
        if (nextOrder == null) continue;
        await _settingsController.reorderStickers(
          packId: pack.id,
          stickerIds: nextOrder,
        );
      }
      await _loadStickers(forceReload: true);
      if (!mounted) return;
      setState(
        () => _applyStickerActionPatch(
          stickerActionSucceeded(
            action: StickerActionKind.order,
            uploading: _uploadingStickers,
            deleting: _deletingStickers,
            savingOrder: _savingStickerOrder,
            downloading: _downloadingStickers,
            selectedStickerIds: _selectedStickerIds,
            error: _stickerError,
            notice: stickerPinnedNotice(
              scope: StickerManagementScope.personal,
              count: selectedIds.length,
            ),
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyStickerActionPatch(
          stickerActionFailed(
            action: StickerActionKind.order,
            uploading: _uploadingStickers,
            deleting: _deletingStickers,
            savingOrder: _savingStickerOrder,
            downloading: _downloadingStickers,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
          ),
        ),
      );
    }
  }

  void _previewSticker(ManagedSticker item) {
    final imageUrl = AppConfigScope.of(
      context,
    ).resolveAssetUrl(item.sticker.asset.url);
    if (imageUrl == null) return;
    final placement = _stickerPlacement(item.sticker.id);
    unawaited(
      showDialog<void>(
        context: context,
        builder: (context) => StickerPreviewDialog(
          item: item,
          imageUrl: imageUrl,
          canMoveUp: !_stickerFilterActive && (placement?.canMoveUp ?? false),
          canMoveDown:
              !_stickerFilterActive && (placement?.canMoveDown ?? false),
          canPin: placement?.canPin ?? false,
          canRename: true,
          canDownload: true,
          canDelete: true,
          onRename: (name) => _renameSticker(item, name),
          onSetAvatar: () => _setStickerAsAvatar(item),
          onDownload: () => _downloadStickerIds([item.sticker.id]),
          onDelete: () => _deleteStickerItem(item),
          onMoveUp: () => _moveStickerItem(item, -1),
          onMoveDown: () => _moveStickerItem(item, 1),
          onPin: () => _pinStickerItem(item),
          imagePreviewOpener: widget.stickerImagePreviewOpener,
        ),
      ),
    );
  }

  sticker_ordering.StickerPlacementData? _stickerPlacement(String stickerId) {
    return sticker_ordering.stickerPlacement(
      _stickerPacks,
      stickerId,
      orderForPack: (pack) => _stickerOrderDrafts[pack.id],
    );
  }

  Future<String?> _renameSticker(ManagedSticker item, String name) async {
    final trimmed = stickerRenameName(name);
    if (!_settingsController.hasApi || trimmed == null) return null;
    try {
      final updated = await _settingsController.updateSticker(
        packId: item.pack.id,
        stickerId: item.sticker.id,
        name: trimmed,
      );
      if (updated == null) return null;
      await _loadStickers(forceReload: true);
      return updated.name;
    } catch (e) {
      if (!mounted) return null;
      setState(
        () => _applyStickerActionPatch(
          stickerActionErrorShown(
            uploading: _uploadingStickers,
            deleting: _deletingStickers,
            savingOrder: _savingStickerOrder,
            downloading: _downloadingStickers,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
            notice: _notice,
          ),
        ),
      );
      return null;
    }
  }

  Future<bool> _deleteStickerItem(ManagedSticker item) async {
    if (!_settingsController.hasApi || _stickerManagementBusy) return false;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StickerConfirmDialog(
        title: stickerDeleteDialogTitle(StickerManagementScope.personal),
        body: stickerSingleDeleteConfirmBody(
          scope: StickerManagementScope.personal,
          stickerName: item.sticker.name,
        ),
        confirmLabel: '删除',
        confirmIcon: Icons.delete_outline,
        danger: true,
      ),
    );
    if (confirmed != true || !mounted) return false;

    setState(
      () => _applyStickerActionPatch(
        stickerActionStarted(
          action: StickerActionKind.delete,
          uploading: _uploadingStickers,
          deleting: _deletingStickers,
          savingOrder: _savingStickerOrder,
          downloading: _downloadingStickers,
          selectedStickerIds: _selectedStickerIds,
        ),
      ),
    );
    try {
      await _settingsController.deleteSticker(
        packId: item.pack.id,
        stickerId: item.sticker.id,
      );
      await _loadStickers(forceReload: true);
      if (!mounted) return false;
      setState(
        () => _applyStickerActionPatch(
          stickerActionSucceeded(
            action: StickerActionKind.delete,
            uploading: _uploadingStickers,
            deleting: _deletingStickers,
            savingOrder: _savingStickerOrder,
            downloading: _downloadingStickers,
            selectedStickerIds: _selectedStickerIds,
            error: _stickerError,
            notice: stickerDeletedNotice(
              scope: StickerManagementScope.personal,
            ),
          ),
        ),
      );
      return true;
    } catch (e) {
      if (!mounted) return false;
      setState(
        () => _applyStickerActionPatch(
          stickerActionFailed(
            action: StickerActionKind.delete,
            uploading: _uploadingStickers,
            deleting: _deletingStickers,
            savingOrder: _savingStickerOrder,
            downloading: _downloadingStickers,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
          ),
        ),
      );
      return false;
    }
  }

  Future<sticker_ordering.StickerPlacementData?> _moveStickerItem(
    ManagedSticker item,
    int delta,
  ) async {
    final placement = _stickerPlacement(item.sticker.id);
    if (_stickerFilterActive) return placement;
    if (!_settingsController.hasApi ||
        placement == null ||
        _stickerManagementBusy) {
      return placement;
    }
    final ids = sticker_ordering.movedStickerOrder(
      placement.pack,
      item.sticker.id,
      delta,
      order: _stickerOrderDrafts[placement.pack.id],
    );
    if (ids == null) return placement;

    setState(
      () => _applyStickerActionPatch(
        stickerActionStarted(
          action: StickerActionKind.order,
          uploading: _uploadingStickers,
          deleting: _deletingStickers,
          savingOrder: _savingStickerOrder,
          downloading: _downloadingStickers,
          selectedStickerIds: _selectedStickerIds,
        ),
      ),
    );
    try {
      await _settingsController.reorderStickers(
        packId: placement.pack.id,
        stickerIds: ids,
      );
      await _loadStickers(forceReload: true);
      if (!mounted) return placement;
      setState(
        () => _applyStickerActionPatch(
          stickerActionSucceeded(
            action: StickerActionKind.order,
            uploading: _uploadingStickers,
            deleting: _deletingStickers,
            savingOrder: _savingStickerOrder,
            downloading: _downloadingStickers,
            selectedStickerIds: _selectedStickerIds,
            error: _stickerError,
            notice: stickerMoveNotice(
              scope: StickerManagementScope.personal,
              delta: delta,
            ),
          ),
        ),
      );
      return _stickerPlacement(item.sticker.id);
    } catch (e) {
      if (!mounted) return placement;
      setState(
        () => _applyStickerActionPatch(
          stickerActionFailed(
            action: StickerActionKind.order,
            uploading: _uploadingStickers,
            deleting: _deletingStickers,
            savingOrder: _savingStickerOrder,
            downloading: _downloadingStickers,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
          ),
        ),
      );
      return placement;
    }
  }

  Future<sticker_ordering.StickerPlacementData?> _pinStickerItem(
    ManagedSticker item,
  ) async {
    final placement = _stickerPlacement(item.sticker.id);
    if (!_settingsController.hasApi ||
        placement == null ||
        placement.index == 0 ||
        _stickerManagementBusy) {
      return placement;
    }

    final ids = sticker_ordering.pinnedStickerOrder(
      placement.pack,
      item.sticker.id,
      order: _stickerOrderDrafts[placement.pack.id],
    );
    if (ids == null) return placement;

    setState(
      () => _applyStickerActionPatch(
        stickerActionStarted(
          action: StickerActionKind.order,
          uploading: _uploadingStickers,
          deleting: _deletingStickers,
          savingOrder: _savingStickerOrder,
          downloading: _downloadingStickers,
          selectedStickerIds: _selectedStickerIds,
        ),
      ),
    );
    try {
      await _settingsController.reorderStickers(
        packId: placement.pack.id,
        stickerIds: ids,
      );
      await _loadStickers(forceReload: true);
      if (!mounted) return placement;
      setState(
        () => _applyStickerActionPatch(
          stickerActionSucceeded(
            action: StickerActionKind.order,
            uploading: _uploadingStickers,
            deleting: _deletingStickers,
            savingOrder: _savingStickerOrder,
            downloading: _downloadingStickers,
            selectedStickerIds: _selectedStickerIds,
            error: _stickerError,
            notice: stickerPinnedNotice(scope: StickerManagementScope.personal),
          ),
        ),
      );
      return _stickerPlacement(item.sticker.id);
    } catch (e) {
      if (!mounted) return placement;
      setState(
        () => _applyStickerActionPatch(
          stickerActionFailed(
            action: StickerActionKind.order,
            uploading: _uploadingStickers,
            deleting: _deletingStickers,
            savingOrder: _savingStickerOrder,
            downloading: _downloadingStickers,
            selectedStickerIds: _selectedStickerIds,
            failure: e,
          ),
        ),
      );
      return placement;
    }
  }

  Future<void> _setStickerAsAvatar(ManagedSticker item) async {
    if (!_settingsController.hasApi || _uploadingAvatar) return;
    setState(
      () => _applyAccountAvatarStatePatch(
        accountAvatarUploadStarted(
          target: AccountAvatarErrorTarget.sticker,
          pendingAvatarAssetId: _pendingAvatarAssetId,
          pendingAvatarUrl: _pendingAvatarUrl,
          clearUploadedAvatar: _clearUploadedAvatar,
          accountError: _accountError,
          stickerError: _stickerError,
        ),
      ),
    );
    try {
      final downloaded = await _settingsController.downloadStickers(
        stickerIds: [item.sticker.id],
      );
      if (downloaded == null) {
        if (!mounted) return;
        setState(
          () => _applyAccountAvatarStatePatch(
            accountAvatarActionCancelled(
              target: AccountAvatarErrorTarget.sticker,
              pendingAvatarAssetId: _pendingAvatarAssetId,
              pendingAvatarUrl: _pendingAvatarUrl,
              clearUploadedAvatar: _clearUploadedAvatar,
              accountError: _accountError,
              stickerError: _stickerError,
            ),
          ),
        );
        return;
      }
      if (!mounted) return;
      final cropped = await showDialog<Uint8List>(
        context: context,
        barrierDismissible: false,
        builder: (context) => AvatarCropDialog(bytes: downloaded.bytes),
      );
      if (cropped == null || !mounted) {
        if (!mounted) return;
        setState(
          () => _applyAccountAvatarStatePatch(
            accountAvatarActionCancelled(
              target: AccountAvatarErrorTarget.sticker,
              pendingAvatarAssetId: _pendingAvatarAssetId,
              pendingAvatarUrl: _pendingAvatarUrl,
              clearUploadedAvatar: _clearUploadedAvatar,
              accountError: _accountError,
              stickerError: _stickerError,
            ),
          ),
        );
        return;
      }
      final asset = await _settingsController.uploadImageAsset(
        bytes: cropped,
        filename: stickerAvatarUploadFilename(stickerId: item.sticker.id),
        purpose: 'avatar',
      );
      if (asset == null) {
        if (!mounted) return;
        setState(
          () => _applyAccountAvatarStatePatch(
            accountAvatarActionCancelled(
              target: AccountAvatarErrorTarget.sticker,
              pendingAvatarAssetId: _pendingAvatarAssetId,
              pendingAvatarUrl: _pendingAvatarUrl,
              clearUploadedAvatar: _clearUploadedAvatar,
              accountError: _accountError,
              stickerError: _stickerError,
            ),
          ),
        );
        return;
      }
      final updated = await _settingsController.updateProfile(
        avatarAssetId: asset.id,
      );
      if (updated == null) {
        if (!mounted) return;
        setState(
          () => _applyAccountAvatarStatePatch(
            accountAvatarActionCancelled(
              target: AccountAvatarErrorTarget.sticker,
              pendingAvatarAssetId: _pendingAvatarAssetId,
              pendingAvatarUrl: _pendingAvatarUrl,
              clearUploadedAvatar: _clearUploadedAvatar,
              accountError: _accountError,
              stickerError: _stickerError,
            ),
          ),
        );
        return;
      }
      if (!mounted) return;
      setState(() {
        _user = updated;
        _syncUserFields(updated);
        _applyAccountAvatarStatePatch(
          accountAvatarProfileUpdatedFromStickerSucceeded(
            pendingAvatarAssetId: _pendingAvatarAssetId,
            pendingAvatarUrl: _pendingAvatarUrl,
            clearUploadedAvatar: _clearUploadedAvatar,
            accountError: _accountError,
          ),
        );
      });
      widget.onUserUpdated?.call(updated);
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyAccountAvatarStatePatch(
          accountAvatarActionFailed(
            target: AccountAvatarErrorTarget.sticker,
            pendingAvatarAssetId: _pendingAvatarAssetId,
            pendingAvatarUrl: _pendingAvatarUrl,
            clearUploadedAvatar: _clearUploadedAvatar,
            accountError: _accountError,
            stickerError: _stickerError,
            failure: e,
          ),
        ),
      );
    }
  }

  Future<void> _openStickerFilter() async {
    final result = await showDialog<StickerFilterDraft>(
      context: context,
      builder: (context) => StickerFilterDialog(
        keyword: _stickerFilterKeyword,
        mimeType: _stickerFilterMimeType,
      ),
    );
    if (result == null || !mounted) return;
    setState(
      () => _applyStickerSelectionPatch(
        stickerFilterApplied(
          managing: _managingStickers,
          keyword: result.keyword,
          mimeType: result.mimeType,
        ),
      ),
    );
  }

  void _selectDefaultAvatarKey(String value) {
    setState(
      () => _applyAccountPresetAvatarSelectionPatch(
        accountPresetAvatarSelected(
          defaultAvatarKey: value,
          currentAvatarUrl: _user?.avatarUrl,
        ),
      ),
    );
  }

  Future<void> _changePassword() async {
    if (!account_display.canStartPasswordChange(
      hasApi: _settingsController.hasApi,
      changingPassword: _changingPassword,
    )) {
      return;
    }
    final draft = passwordChangeDraftFromForm(
      currentPassword: _currentPasswordController.text,
      newPassword: _newPasswordController.text,
      confirmPassword: _confirmPasswordController.text,
      currentPasswordRequired: !_canResetPasswordWithoutCurrentPassword,
    );
    if (!draft.isValid) {
      setState(
        () => _applyPasswordChangeStatePatch(
          passwordChangeValidationFailed(
            error: draft.error!,
            changingPassword: _changingPassword,
            notice: _notice,
          ),
        ),
      );
      return;
    }

    setState(() => _applyPasswordChangeStatePatch(passwordChangeStarted()));
    try {
      await _settingsController.changePassword(
        currentPassword: draft.currentPassword!,
        newPassword: draft.newPassword!,
      );
      if (!mounted) return;
      _currentPasswordController.clear();
      _newPasswordController.clear();
      _confirmPasswordController.clear();
      setState(() => _applyPasswordChangeStatePatch(passwordChangeSucceeded()));
      unawaited(_loadSessions());
    } catch (e) {
      if (!mounted) return;
      setState(() => _applyPasswordChangeStatePatch(passwordChangeFailed(e)));
    }
  }

  bool get _canResetPasswordWithoutCurrentPassword {
    return _isManagingUser ||
        (widget.passwordResetController?.isCurrentSessionAuthorizedFor(
              _user?.email,
            ) ??
            false);
  }

  Future<void> _verifyEmailForPasswordReset() async {
    if (_verifyingPasswordReset) return;
    final controller = widget.passwordResetController;
    final user = _user;
    if (controller == null || user == null) {
      showFloatingErrorNotice(context, '暂时无法使用忘记密码功能');
      return;
    }
    final login = user.username.trim().isNotEmpty
        ? user.username
        : (user.email ?? '');
    setState(() => _verifyingPasswordReset = true);
    final resetToken = await verifyPasswordResetEmail(
      context: context,
      login: login,
      controller: controller,
    );
    if (!mounted) return;
    if (resetToken == null) {
      setState(() => _verifyingPasswordReset = false);
      return;
    }
    try {
      await controller.claimForCurrentSession(
        resetToken,
        email: user.email ?? '',
      );
      if (!mounted) return;
      setState(() {
        _verifyingPasswordReset = false;
        _currentPasswordController.clear();
      });
      showFloatingSuccessNotice(context, '邮箱验证成功，可以直接设置新密码');
    } catch (error) {
      if (!mounted) return;
      setState(() => _verifyingPasswordReset = false);
      showFloatingErrorNotice(
        context,
        userFacingErrorMessage(error, fallback: '邮箱验证失败'),
      );
    }
  }

  Future<void> _confirmDeleteAccount() async {
    final user = _user;
    if (!_settingsController.canDeleteAccount ||
        (_isManagingUser && (user?.isSuperuser ?? false)) ||
        !account_display.canStartAccountDeletion(
          hasApi: _settingsController.hasApi,
          user: user,
          deletingAccount: _deletingAccount,
        )) {
      return;
    }
    final targetUser = user!;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => _DeleteAccountDialog(
        spec: account_display.accountDeletionConfirmationSpec(targetUser),
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _applyAccountDeletionStatePatch(accountDeletionStarted()));
    try {
      await _settingsController.deleteMyAccount();
      if (!mounted) return;
      await widget.onAccountDeleted?.call();
      if (!mounted) return;
      setState(
        () => _applyAccountDeletionStatePatch(
          accountDeletionFinished(
            securityError: _securityError,
            notice: _notice,
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _applyAccountDeletionStatePatch(accountDeletionFailed(e)));
    }
  }

  Future<void> _toggleManagedAccountSuspension() async {
    final user = _user;
    if (!_isManagingUser ||
        user == null ||
        user.isSuperuser ||
        _changingAccountSuspension) {
      return;
    }
    final suspending = !_accountSuspended;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => DialogFrame(
        title: suspending ? '确认封禁账号' : '确认解除封禁',
        icon: suspending ? Icons.block_outlined : Icons.lock_open_outlined,
        adaptiveActions: [
          ResponsiveDialogAction(
            label: '取消',
            onPressed: () => Navigator.of(context).pop(false),
          ),
          ResponsiveDialogAction(
            label: suspending ? '封禁账号' : '解除封禁',
            onPressed: () => Navigator.of(context).pop(true),
            tone: suspending ? ButtonTone.danger : ButtonTone.primary,
          ),
        ],
        child: Text(
          suspending
              ? '封禁后，${user.displayName} 的所有登录会话将立即失效，且无法登录。用户名、头像、资料和历史消息将保留。'
              : '解除封禁后，${user.displayName} 可以重新登录；此前已撤销的登录会话不会恢复。',
          style: UiTypography.body.copyWith(
            color: UiColors.textSecondary,
            height: 1.55,
          ),
        ),
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() => _changingAccountSuspension = true);
    try {
      final updated = await _settingsController.setManagedAccountSuspended(
        suspending,
      );
      if (!mounted || updated == null) return;
      setState(() {
        _user = updated;
        _changingAccountSuspension = false;
        _securityError = null;
        _notice = suspending ? '账号已封禁' : '账号已解除封禁';
        _markFloatingNoticeEvent('notice', _notice);
      });
      widget.onUserUpdated?.call(updated);
      unawaited(_loadSessions());
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _changingAccountSuspension = false;
        _securityError = userFacingErrorMessage(
          error,
          fallback: suspending ? '封禁账号失败' : '解除封禁失败',
        );
        _markFloatingNoticeEvent('securityError', _securityError);
      });
    }
  }

  Future<void> _loadStoredAudioSettings() async {
    try {
      final stored = await widget.audioDeviceStore.read();
      if (!mounted) return;
      final patch = audioStoredVolumesApplied(
        inputVolume: stored.inputVolume,
        outputVolume: stored.outputVolume,
      );
      _rememberOutputVolume(patch.outputVolume);
      setState(() => _applyAudioVolumePatch(patch));
      widget.onVolumeChanged?.call('audioinput', patch.inputVolume);
      widget.onVolumeChanged?.call('audiooutput', patch.outputVolume);
      final platformFrameRateCap = !kIsWeb && Platform.isAndroid
          ? androidScreenShareFrameRateCap
          : desktopScreenShareFrameRateCap;
      setState(() {
        _screenShareMaxHeight = stored.screenShareMaxHeight;
        _screenShareFrameRate = normalizedScreenShareFrameRate(
          stored.screenShareFrameRate,
          maxFrameRate: platformFrameRateCap,
        );
      });
    } catch (_) {}
  }

  Future<void> _setScreenShareMaxHeight(int height) async {
    final normalized = normalizedScreenShareMaxHeight(height);
    if (_screenShareMaxHeight == normalized) return;
    setState(() => _screenShareMaxHeight = normalized);
    widget.onScreenShareMaxHeightChanged?.call(normalized);
    unawaited(widget.audioDeviceStore.writeScreenShareMaxHeight(normalized));
  }

  Future<void> _setScreenShareFrameRate(int frameRate) async {
    final platformFrameRateCap = !kIsWeb && Platform.isAndroid
        ? androidScreenShareFrameRateCap
        : desktopScreenShareFrameRateCap;
    final normalized = normalizedScreenShareFrameRate(
      frameRate,
      maxFrameRate: platformFrameRateCap,
    );
    if (_screenShareFrameRate == normalized) return;
    setState(() => _screenShareFrameRate = normalized);
    widget.onScreenShareFrameRateChanged?.call(normalized);
    unawaited(widget.audioDeviceStore.writeScreenShareFrameRate(normalized));
  }

  Future<void> _loadDevices() async {
    setState(
      () => _applyAudioDeviceListPatch(
        audioDeviceListLoadStarted(
          inputs: _audioInputs,
          outputs: _audioOutputs,
          selectedInput: _selectedInput,
          selectedOutput: _selectedOutput,
        ),
      ),
    );
    try {
      if (widget.androidSystemService.isSupported) {
        try {
          await widget.androidSystemService.requestBluetoothConnectPermission();
        } catch (_) {
          // Keep the built-in speaker/microphone list available even when the
          // user declines nearby-device access.
        }
        if (!mounted) return;
      }
      // macOS reports no audio inputs through flutter_webrtc until a room is
      // joined; LiveAudioDeviceService.enumerateDevices merges the native
      // CoreAudio input list in, so the picker is populated without a room.
      final devices = await widget.audioDeviceService.enumerateDevices();
      await _applyDevices(devices);
    } catch (e) {
      if (!mounted) return;
      setState(
        () => _applyAudioDeviceListPatch(
          audioDeviceListLoadFailed(
            inputs: _audioInputs,
            outputs: _audioOutputs,
            selectedInput: _selectedInput,
            selectedOutput: _selectedOutput,
            failure: e,
          ),
        ),
      );
    }
  }

  Future<void> _applyDevices(List<AudioDeviceInfo> devices) async {
    if (!mounted) return;
    final systemDefaultInputId = _systemDefaultInputId;
    final systemDefaultOutputId = _systemDefaultOutputId;
    RestoredAudioDevices<AudioDeviceInfo> restored =
        const RestoredAudioDevices();
    try {
      restored = await restoreStoredAudioDevices(
        widget.audioDeviceStore,
        audioDevices: widget.audioDeviceService,
        devices: devices,
        systemDefaultInputId: systemDefaultInputId,
        systemDefaultOutputId: systemDefaultOutputId,
      );
    } catch (_) {
      // Device choices are a local convenience. If storage or OS routing fails,
      // keep rendering the current device list and let the user re-select.
    }
    if (!mounted) return;
    final systemDefaultInput = systemDefaultInputId == null
        ? null
        : storedAudioDeviceFrom(
            devices,
            kind: 'audioinput',
            deviceId: systemDefaultInputId,
            kindOf: audioDeviceInfoKind,
            deviceIdOf: audioDeviceInfoId,
          );
    final systemDefaultOutput = systemDefaultOutputId == null
        ? null
        : storedAudioDeviceFrom(
            devices,
            kind: 'audiooutput',
            deviceId: systemDefaultOutputId,
            kindOf: audioDeviceInfoKind,
            deviceIdOf: audioDeviceInfoId,
          );
    setState(
      () => _applyAudioDeviceListPatch(
        audioDeviceListApplied(
          devices: devices,
          restoredInput: restored.input,
          restoredOutput: restored.output,
          hardwareInput: widget.audioDeviceService.selectedAudioInput,
          hardwareOutput: widget.audioDeviceService.selectedAudioOutput,
          currentInput: _selectedInput,
          currentOutput: _selectedOutput,
          kindOf: audioDeviceInfoKind,
          deviceIdOf: audioDeviceInfoId,
          error: _error,
          systemDefaultInput: systemDefaultInput,
          systemDefaultOutput: systemDefaultOutput,
        ),
      ),
    );
  }

  // Re-runs device selection when OS defaults change while Settings is open.
  // restoreStoredAudioDevices only falls back to the system defaults when the
  // user has not pinned a device, so explicit choices are preserved.
  Future<void> _onSystemDefaultAudioChanged() async {
    if (!mounted || !_voiceInitialized) return;
    await _applyDevices([..._audioInputs, ..._audioOutputs]);
  }

  Future<void> _selectInput(AudioDeviceInfo device) async {
    final wasTestingInput = _testingInput;
    final wasTestingOutput = _testingOutput;
    final didSelect = await _selectDevice(
      device,
      () => widget.audioDeviceService.selectAudioInput(device),
      () => widget.audioDeviceStore.writeInputDevicePreference(
        deviceId: device.deviceId,
        label: device.label,
        groupId: device.groupId,
      ),
    );
    if (!didSelect) return;
    final effects = audioInputDeviceSelectedEffects(
      wasTestingInput: wasTestingInput,
      wasTestingOutput: wasTestingOutput,
    );
    if (effects.restartInputTest) await _restartInputTest();
    if (effects.restartOutputTest) await _restartOutputTest();
  }

  Future<void> _selectOutput(AudioDeviceInfo device) async {
    final didSelect = await _selectDevice(
      device,
      () => widget.audioDeviceService.selectAudioOutput(device),
      () => widget.audioDeviceStore.writeOutputDevicePreference(
        deviceId: device.deviceId,
        label: device.label,
        groupId: device.groupId,
      ),
    );
    if (!didSelect) return;
    final effects = audioOutputDeviceSelectedEffects();
    if (effects.restartOutputTest) await _restartOutputTest();
  }

  Future<bool> _selectDevice(
    AudioDeviceInfo device,
    Future<void> Function() select,
    Future<void> Function() rememberSelection,
  ) async {
    if (!canStartAudioDeviceSelection(_busyDeviceId)) return false;
    setState(
      () => _applyAudioDeviceSelectionPatch(
        audioDeviceSelectionStarted(
          device: device,
          selectedInput: _selectedInput,
          selectedOutput: _selectedOutput,
          kindOf: audioDeviceInfoKind,
          deviceIdOf: audioDeviceInfoId,
        ),
      ),
    );
    var didSelect = false;
    try {
      await select();
      Object? storageError;
      try {
        await rememberSelection();
      } catch (e) {
        storageError = e;
      }
      if (!mounted) return false;
      setState(
        () => _applyAudioDeviceSelectionPatch(
          audioDeviceSelectionSucceeded(
            device: device,
            selectedInput: _selectedInput,
            selectedOutput: _selectedOutput,
            kindOf: audioDeviceInfoKind,
            storageFailure: storageError,
          ),
        ),
      );
      widget.onDeviceSelected?.call(device.kind, device.deviceId);
      didSelect = true;
    } catch (e) {
      if (mounted) {
        setState(
          () => _applyAudioDeviceSelectionPatch(
            audioDeviceSelectionFailed(
              selectedInput: _selectedInput,
              selectedOutput: _selectedOutput,
              failure: e,
            ),
          ),
        );
      }
    }
    return didSelect;
  }

  Future<void> _setInputVolume(double volume) async {
    final normalized = normalizedAudioVolume(volume);
    final patch = audioInputVolumeChanged(
      inputVolume: normalized,
      outputVolume: _outputVolume,
      restoreOutputVolume: _restoredOutputVolume(),
    );
    await _setAudioVolumes(patch);
  }

  Future<void> _setOutputVolume(double volume) async {
    final normalized = normalizedAudioVolume(volume);
    if (normalized == 0) {
      _rememberOutputVolume(_outputVolume);
    } else {
      _rememberOutputVolume(normalized);
    }
    final patch = audioOutputVolumeChanged(
      inputVolume: _inputVolume,
      outputVolume: normalized,
    );
    await _setAudioVolumes(patch);
  }

  Future<void> _setAudioVolumes(AudioVolumePatch patch) async {
    final previousInputVolume = _inputVolume;
    final previousOutputVolume = _outputVolume;
    if (patch.inputVolume == previousInputVolume &&
        patch.outputVolume == previousOutputVolume) {
      return;
    }
    setState(() => _applyAudioVolumePatch(patch));
    if (patch.outputVolume > 0) _rememberOutputVolume(patch.outputVolume);

    final inputTest = _inputTest;
    if (patch.inputVolume != previousInputVolume) {
      widget.onVolumeChanged?.call('audioinput', patch.inputVolume);
      unawaited(widget.audioDeviceStore.writeInputVolume(patch.inputVolume));
      try {
        await inputTest?.setCaptureVolume(patch.inputVolume);
      } catch (_) {}
    }

    final outputTest = _outputTest;
    if (patch.outputVolume != previousOutputVolume) {
      widget.onVolumeChanged?.call('audiooutput', patch.outputVolume);
      unawaited(widget.audioDeviceStore.writeOutputVolume(patch.outputVolume));
      try {
        await outputTest?.setPlaybackVolume(patch.outputVolume);
      } catch (_) {}
    }
  }

  Future<void> _toggleInputTest() async {
    if (_testingInput) {
      await _stopInputTest();
    } else {
      await _startInputTest();
    }
  }

  Future<void> _toggleOutputTest() async {
    if (_testingOutput) {
      await _stopOutputTest();
    } else {
      await _startOutputTest();
    }
  }

  Future<void> _restartInputTest() async {
    await _stopInputTest();
    if (mounted) await _startInputTest();
  }

  Future<void> _restartOutputTest() async {
    await _stopOutputTest();
    if (mounted) await _startOutputTest();
  }

  Future<void> _startInputTest() async {
    if (_testingInput) return;
    setState(
      () => _applyAudioTestStatePatch(
        audioInputTestStarted(
          testingOutput: _testingOutput,
          outputLevel: _outputLevel,
        ),
      ),
    );
    AudioTestHandle? handle;
    try {
      handle = await _audioTestService.startInputTest(
        inputDeviceId: _selectedInput?.deviceId,
        volume: _inputVolume,
        onLevel: (level) {
          if (!mounted) return;
          setState(
            () => _applyAudioTestStatePatch(
              audioInputLevelChanged(
                level: level,
                inputVolume: _inputVolume,
                testingOutput: _testingOutput,
                outputLevel: _outputLevel,
                error: _error,
              ),
            ),
          );
        },
      );
      if (!mounted) {
        await handle.dispose();
        return;
      }
      _inputTest = handle;
    } catch (e) {
      await handle?.dispose();
      if (!mounted) return;
      setState(
        () => _applyAudioTestStatePatch(
          audioInputTestFailed(
            testingOutput: _testingOutput,
            outputLevel: _outputLevel,
            failure: e,
          ),
        ),
      );
    }
  }

  Future<void> _stopInputTest({bool updateState = true}) async {
    final inputTest = _inputTest;
    _inputTest = null;
    await inputTest?.dispose();
    if (updateState && mounted) {
      setState(
        () => _applyAudioTestStatePatch(
          audioInputTestStopped(
            testingOutput: _testingOutput,
            outputLevel: _outputLevel,
            error: _error,
          ),
        ),
      );
    }
  }

  Future<void> _startOutputTest() async {
    if (_testingOutput) return;
    setState(
      () => _applyAudioTestStatePatch(
        audioOutputTestStarted(
          testingInput: _testingInput,
          inputLevel: _inputLevel,
        ),
      ),
    );
    AudioTestHandle? handle;
    try {
      handle = await _audioTestService.startOutputTest(
        volume: _outputVolume,
        onLevel: (level) {
          if (!mounted) return;
          setState(
            () => _applyAudioTestStatePatch(
              audioOutputLevelChanged(
                level: level,
                outputVolume: _outputVolume,
                testingInput: _testingInput,
                inputLevel: _inputLevel,
                error: _error,
              ),
            ),
          );
        },
      );
      if (!mounted) {
        await handle.dispose();
        return;
      }
      _outputTest = handle;
    } catch (e) {
      await handle?.dispose();
      if (!mounted) return;
      setState(
        () => _applyAudioTestStatePatch(
          audioOutputTestFailed(
            testingInput: _testingInput,
            inputLevel: _inputLevel,
            failure: e,
          ),
        ),
      );
    }
  }

  Future<void> _stopOutputTest({bool updateState = true}) async {
    final outputTest = _outputTest;
    _outputTest = null;
    await outputTest?.dispose();
    if (updateState && mounted) {
      setState(
        () => _applyAudioTestStatePatch(
          audioOutputTestStopped(
            testingInput: _testingInput,
            inputLevel: _inputLevel,
            error: _error,
          ),
        ),
      );
    }
  }

  bool get _isRefreshing {
    return settingsSectionRefreshing(
      section: _section,
      loadingAccount: _loadingAccount,
      loadingPreferences: _loadingCloseBehavior,
      loadingStickers: _loadingStickers,
      loadingSessions: _loadingSessions,
      loadingVoice: _loading,
      loadingPlaylists: _loadingPlaylists,
      loadingAbout: _loadingAbout || _checkingAppVersion,
    );
  }

  void _selectSection(SettingsSection section) {
    final patch = settingsSectionSelected(
      section: section,
      sessionsEmpty: _sessions.isEmpty,
      loadingSessions: _loadingSessions,
    );
    setState(() {
      _applySettingsSectionPatch(patch);
    });
    if (patch.shouldLoadStickers) {
      unawaited(_ensureStickersLoaded());
    }
    if (patch.shouldLoadSessions) {
      unawaited(_loadSessions());
    }
    if (patch.shouldInitializeVoice) {
      unawaited(_ensureVoiceInitialized());
    }
  }

  Widget _buildSectionContent() {
    return switch (_section) {
      SettingsSection.profile => _buildProfileContent(),
      SettingsSection.preferences => _buildPreferencesContent(),
      SettingsSection.stickers => _buildStickersContent(),
      SettingsSection.playlists => _buildMusicPlaylistsContent(),
      SettingsSection.security => _buildSecurityContent(),
      SettingsSection.voice => _buildVoiceContent(),
      SettingsSection.about => _buildAboutContent(),
    };
  }

  Widget _buildMusicPlaylistsContent() {
    return MusicPlaylistsPanel(
      controller: _musicPlaylistsController,
      previewApi: widget.api is MusicTrackPreviewApi
          ? widget.api as MusicTrackPreviewApi
          : null,
      previewPlatformFactory: widget.musicTrackPreviewPlatformFactory,
      reloadToken: _playlistReloadToken,
      unavailableMessage: _isManagingUser
          ? '管理其他账号时不能编辑个人歌单'
          : '我的歌单需要登录后从服务端读取',
      onLoadingChanged: (loading) {
        if (!mounted || loading == _loadingPlaylists) return;
        setState(() => _loadingPlaylists = loading);
      },
    );
  }

  Widget _buildStickersContent() {
    final unavailable = !_settingsController.hasApi;
    final items = _filteredStickerItems();
    final totalCount = _allStickerItems().length;
    final selectionNumbers = _stickerSelectionNumbers();
    final busy = _stickerManagementBusy;
    final allVisibleSelected = stickerAllVisibleSelected(
      selectedStickerIds: _selectedStickerIds,
      visibleItems: items,
    );
    final canPinSelection =
        canStartStickerSelectionAction(
          busy: busy,
          selectedStickerIds: _selectedStickerIds,
        ) &&
        sticker_ordering.stickerSelectionWouldChangePinnedOrder(
          packs: _stickerPacks,
          selectedStickerIds: _selectedStickerIds,
          orderByPack: _stickerOrderDrafts,
        );
    final Widget stickerBody;
    if (_loadingStickers && _stickerPacks.isEmpty) {
      stickerBody = const Center(
        child: CircularProgressIndicator(color: _cyan),
      );
    } else if (totalCount == 0) {
      stickerBody = const Align(
        alignment: Alignment.topCenter,
        child: SizedBox(
          width: double.infinity,
          child: _SettingsEmptyState(text: '暂无表情，点击本地上传会自动创建'),
        ),
      );
    } else if (items.isEmpty) {
      stickerBody = const Align(
        alignment: Alignment.topCenter,
        child: SizedBox(
          width: double.infinity,
          child: _SettingsEmptyState(text: '没有匹配的表情'),
        ),
      );
    } else {
      stickerBody = StickerGrid(
        key: const ValueKey('personal-sticker-items-scroll'),
        items: items,
        managing: _managingStickers,
        selectionNumbers: selectionNumbers,
        busy: busy,
        scrollable: true,
        onTap: (item) {
          if (_managingStickers) {
            _toggleStickerSelection(item.sticker.id);
          } else {
            _previewSticker(item);
          }
        },
      );
    }

    if (unavailable) {
      return SettingsList(
        children: const [_SettingsEmptyState(text: '表情包需要登录后从服务端读取')],
      );
    }
    return SettingsFixedHeaderCard(
      title: '表情包管理',
      spacing: 10,
      trailing: Text(
        stickerManagementCountText(
          filterActive: _stickerFilterActive,
          visibleCount: items.length,
          totalCount: totalCount,
        ),
        style: const TextStyle(
          color: _textMuted,
          fontSize: 12,
          fontWeight: FontWeight.w600,
        ),
      ),
      headerChildren: [
        StickerActionGrid(
          actions: [
            StickerActionGridEntry(
              label: _managingStickers ? '删除' : '本地上传',
              button: Button(
                onPressed: canStartStickerPrimaryAction(busy: busy)
                    ? _managingStickers
                          ? _deleteSelectedStickers
                          : _pickAndUploadStickers
                    : null,
                loading: _managingStickers
                    ? _deletingStickers
                    : _uploadingStickers,
                tone: _managingStickers
                    ? ButtonTone.danger
                    : ButtonTone.primary,
                icon: Icon(
                  _managingStickers ? Icons.delete_outline : Icons.upload_file,
                ),
                width: double.infinity,
                child: Text(_managingStickers ? '删除' : '本地上传'),
              ),
            ),
            StickerActionGridEntry(
              label: _managingStickers ? '取消管理' : '批量管理',
              button: Button(
                onPressed: canUseStickerManagementControl(busy: busy)
                    ? _toggleStickerManageMode
                    : null,
                selected: _managingStickers,
                tone: _managingStickers
                    ? ButtonTone.primary
                    : ButtonTone.neutral,
                icon: Icon(
                  _managingStickers ? Icons.close : Icons.checklist_rtl,
                ),
                width: double.infinity,
                child: Text(_managingStickers ? '取消管理' : '批量管理'),
              ),
            ),
            StickerActionGridEntry(
              label: '筛选',
              button: Button(
                onPressed: canUseStickerManagementControl(busy: busy)
                    ? _openStickerFilter
                    : null,
                selected: _stickerFilterActive,
                tone: _stickerFilterActive
                    ? ButtonTone.primary
                    : ButtonTone.neutral,
                icon: const Icon(Icons.filter_alt_outlined),
                width: double.infinity,
                child: const Text('筛选'),
              ),
            ),
            if (_managingStickers) ...[
              StickerActionGridEntry(
                label: '下载',
                button: Button(
                  onPressed:
                      canStartStickerSelectionAction(
                        busy: busy,
                        selectedStickerIds: _selectedStickerIds,
                      )
                      ? _downloadSelectedStickers
                      : null,
                  loading: _downloadingStickers,
                  icon: const Icon(Icons.download_outlined),
                  width: double.infinity,
                  child: const Text('下载'),
                ),
              ),
              StickerActionGridEntry(
                label: '置顶',
                button: Button(
                  onPressed: canPinSelection ? _pinSelectedStickers : null,
                  loading: _savingStickerOrder,
                  icon: const Icon(Icons.vertical_align_top),
                  width: double.infinity,
                  child: const Text('置顶'),
                ),
              ),
              StickerActionGridEntry(
                label: stickerVisibleSelectionButtonText(
                  selectedStickerIds: _selectedStickerIds,
                  visibleItems: items,
                ),
                button: Button(
                  onPressed:
                      canSelectVisibleStickers(busy: busy, visibleItems: items)
                      ? () => _selectAllVisibleStickers(items)
                      : null,
                  selected: allVisibleSelected,
                  icon: Icon(
                    allVisibleSelected
                        ? Icons.check_box
                        : Icons.check_box_outline_blank,
                  ),
                  width: double.infinity,
                  child: Text(
                    stickerVisibleSelectionButtonText(
                      selectedStickerIds: _selectedStickerIds,
                      visibleItems: items,
                    ),
                  ),
                ),
              ),
            ],
          ],
        ),
      ],
      body: stickerBody,
    );
  }

  Widget _buildPreferencesContent() {
    final unavailable = !_settingsController.hasApi || _user == null;
    final androidNotifications =
        widget.androidSystemService.isSupported && !_isManagingUser;
    return SettingsList(
      children: [
        if (!widget.androidSystemService.isSupported)
          _SettingsGroup(
            title: '关闭方式',
            trailing: _savingCloseBehavior || _loadingCloseBehavior
                ? const SizedBox.square(
                    dimension: 16,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: _cyan,
                    ),
                  )
                : null,
            children: [
              _SegmentedSetting(
                key: const ValueKey('settings-close-behavior-segmented'),
                label: '关闭方式',
                value: _closeBehavior.storageValue,
                options: CloseBehavior.values
                    .map(
                      (behavior) => _SegmentOption(
                        value: behavior.storageValue,
                        label: closeBehaviorLabel(behavior),
                      ),
                    )
                    .toList(growable: false),
                onChanged: _setCloseBehavior,
                enabled: !_isManagingUser,
              ),
              const SizedBox(height: 10),
              Text(
                _isManagingUser
                    ? '该设置仅保存在用户设备，无法远程读取'
                    : closeBehaviorDescription(_closeBehavior),
                style: const TextStyle(
                  color: _textSecondary,
                  fontSize: 13,
                  height: 1.45,
                ),
              ),
            ],
          ),
        if (androidNotifications)
          _SettingsGroup(
            title: '系统通知',
            trailing: _savingAndroidNotifications
                ? const SizedBox.square(
                    dimension: 16,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: _cyan,
                    ),
                  )
                : null,
            children: [
              _ToggleSetting(
                label: '允许房间消息通知',
                value: _androidNotificationPreferenceEnabled,
                enabled: !_savingAndroidNotifications,
                onChanged: (value) =>
                    unawaited(_setAndroidNotificationsEnabled(value)),
              ),
              const SizedBox(height: 10),
              Text(
                !_androidNotificationPreferenceEnabled
                    ? '已在 Gang Chat 中关闭通知'
                    : _androidNotificationsAvailable
                    ? '通知已开启；房间设置为“全部”时，后台消息会显示内容和数字角标'
                    : '系统尚未允许通知，请在 Android 设置中开启',
                style: const TextStyle(
                  color: _textSecondary,
                  fontSize: 13,
                  height: 1.45,
                ),
              ),
              const SizedBox(height: 12),
              Align(
                alignment: Alignment.centerLeft,
                child: Button(
                  onPressed: _openAndroidNotificationSettings,
                  icon: const Icon(Icons.notifications_outlined),
                  child: const Text('系统通知设置'),
                ),
              ),
            ],
          ),
        if (unavailable)
          const _SettingsEmptyState(text: '语言偏好需要登录后从服务端读取')
        else
          _SettingsGroup(
            title: '语言切换',
            children: [
              _SegmentedSetting(
                key: const ValueKey('settings-language-segmented'),
                label: '语言',
                value: _language,
                options: const [
                  _SegmentOption(value: 'zh-Hans', label: '简体中文'),
                  _SegmentOption(value: 'zh-Hant', label: '繁體中文'),
                  _SegmentOption(value: 'en', label: 'English'),
                ],
                onChanged: (value) => setState(() => _language = value),
              ),
            ],
          ),
        Align(
          alignment: Alignment.centerRight,
          child: Button(
            onPressed: _savingAccount || _savingCloseBehavior
                ? null
                : () => unawaited(_savePreferences()),
            loading: _savingAccount || _savingCloseBehavior,
            icon: const Icon(Icons.save_outlined),
            tone: ButtonTone.primary,
            child: const Text('保存偏好设置'),
          ),
        ),
      ],
    );
  }

  Future<void> _openProfileAvatarPreview(String? imageUrl) async {
    final url = imageUrl?.trim();
    final opener = widget.stickerImagePreviewOpener;
    if (url == null || url.isEmpty || opener == null) return;
    final displayName = _displayNameController.text.trim();
    final username = _user?.username.trim() ?? '';
    final stem = displayName.isNotEmpty
        ? displayName
        : username.isNotEmpty
        ? username
        : 'avatar';
    await opener(
      context,
      imageUrl: url,
      suggestedName: '$stem-avatar.png',
      forceSquare: true,
    );
  }

  Widget _buildProfileContent() {
    final user = _user;
    final unavailable = !_settingsController.hasApi || user == null;
    final appConfig = AppConfigScope.of(context);
    final avatarPreviewUrl = appConfig.resolveAssetUrl(
      account_display.accountAvatarPreviewPath(
        clearUploadedAvatar: _clearUploadedAvatar,
        pendingAvatarUrl: _pendingAvatarUrl,
        currentAvatarUrl: user?.avatarUrl,
      ),
    );
    final usernameValidationError = loginUsernameValidationError(
      _usernameController.text,
    );
    final usernameEditable =
        user != null &&
        (_isManagingUser || account_display.canEditUsername(user));
    final usernameChanged =
        user != null && _usernameController.text.trim() != user.username;
    final usernameAvailabilityApplies =
        usernameChanged &&
        usernameValidationError == null &&
        _usernameAvailabilityQuery == _usernameController.text.trim();
    final usernameEffectiveError =
        usernameValidationError ??
        (usernameAvailabilityApplies ? _usernameAvailabilityError : null);
    final usernameChecking =
        usernameAvailabilityApplies && _checkingUsernameAvailability;
    return SettingsList(
      children: [
        if (unavailable)
          const _SettingsEmptyState(text: '账号资料需要登录后从服务端读取')
        else ...[
          _SettingsGroup(
            title: '账号标识',
            children: [
              _CopyableField(label: '个人 UID', value: user.uid),
              const SizedBox(height: 14),
              _LabeledTextField(
                label: '登录用户名',
                controller: _usernameController,
                enabled: usernameEditable,
                helperText: account_display.usernameHelperText(user),
                suffix: usernameChanged
                    ? _UsernameValidityIndicator(
                        error: usernameEffectiveError,
                        checking: usernameChecking,
                        enabled: usernameEditable,
                      )
                    : null,
                onChanged: _onLoginUsernameChanged,
              ),
            ],
          ),
          _SettingsGroup(
            title: '默认资料',
            children: [
              _LabeledTextField(
                label: '用户名',
                controller: _displayNameController,
                helperText: '没有房间个人资料覆盖时展示。',
              ),
              const SizedBox(height: 14),
              _SegmentedSetting(
                key: const ValueKey('settings-gender-segmented'),
                label: '性别',
                value: _gender,
                options: const [
                  _SegmentOption(value: 'male', label: '男'),
                  _SegmentOption(value: 'female', label: '女'),
                  _SegmentOption(value: 'secret', label: '保密'),
                ],
                onChanged: (value) => setState(
                  () => _applyAccountEditableFieldsPatch(
                    accountGenderChanged(
                      gender: value,
                      emailPublic: _emailPublic,
                      phoneNumberPublic: _phonePublic,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 14),
              AvatarPicker(
                label: '头像',
                displayName: _displayNameController.text,
                imageUrl: avatarPreviewUrl,
                defaultAvatarKey: _defaultAvatarKey,
                usingPreset: avatarPreviewUrl == null,
                uploading: _uploadingAvatar,
                enabled: !_uploadingAvatar,
                onUpload: _pickAndUploadAvatar,
                onPresetSelected: _selectDefaultAvatarKey,
                onImagePreview: widget.stickerImagePreviewOpener == null
                    ? null
                    : () => unawaited(
                        _openProfileAvatarPreview(avatarPreviewUrl),
                      ),
                uploadLabel: '上传头像',
              ),
              const SizedBox(height: 14),
              _LabeledTextField(
                label: '签名',
                controller: _bioController,
                maxLines: 4,
                helperText: '用于个人资料面板展示。',
              ),
            ],
          ),
          Align(
            alignment: Alignment.centerRight,
            child: Button(
              onPressed: _savingProfile || _savingAccount ? null : _saveProfile,
              loading: _savingProfile,
              icon: const Icon(Icons.save_outlined),
              tone: ButtonTone.primary,
              child: const Text('保存用户资料'),
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildSecurityContent() {
    final user = _user;
    final unavailable = !_settingsController.hasApi || user == null;
    final emailEmpty = _normalizedAccountEmail.isEmpty;
    final managedSuperuserPasswordBlocked =
        _isManagingUser && (user?.isSuperuser ?? false);
    return SettingsList(
      children: [
        if (unavailable)
          const _SettingsEmptyState(text: '安全设置需要登录后从服务端读取')
        else ...[
          _SettingsGroup(
            title: '绑定信息',
            children: [
              _LabeledTextField(
                key: const ValueKey('settings-email-input'),
                label: '邮箱绑定',
                controller: _emailController,
                keyboardType: TextInputType.emailAddress,
                helperText: '用于登录、账号找回和安全通知。',
                suffix: emailEmpty
                    ? null
                    : _checkingEmailAvailability
                    ? const _EmailVerificationStatusIndicator(checking: true)
                    : _accountEmailVerified
                    ? const _EmailVerificationStatusIndicator(checking: false)
                    : EmailVerificationInputAction(
                        actionKey: const ValueKey(
                          'settings-email-verification-button',
                        ),
                        label: '验证',
                        semanticsLabel: '验证邮箱',
                        enabled: !_savingAccount,
                        onPressed: _showAccountEmailVerification,
                      ),
                onChanged: _onAccountEmailChanged,
              ),
              const SizedBox(height: 10),
              _ToggleSetting(
                label: '公开邮箱',
                value: _emailPublic,
                onChanged: (value) => setState(
                  () => _applyAccountEditableFieldsPatch(
                    accountEmailPublicChanged(
                      gender: _gender,
                      emailPublic: value,
                      phoneNumberPublic: _phonePublic,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 14),
              _LabeledTextField(
                label: '手机号绑定',
                controller: _phoneController,
                keyboardType: TextInputType.phone,
                helperText: '用于账号找回和安全通知，留空表示解绑。',
              ),
              const SizedBox(height: 10),
              _ToggleSetting(
                label: '公开手机号',
                value: _phonePublic,
                onChanged: (value) => setState(
                  () => _applyAccountEditableFieldsPatch(
                    accountPhoneNumberPublicChanged(
                      gender: _gender,
                      emailPublic: _emailPublic,
                      phoneNumberPublic: value,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 14),
              Align(
                alignment: Alignment.centerRight,
                child: Button(
                  onPressed: _savingAccount ? null : _saveAccount,
                  loading: _savingAccount,
                  icon: const Icon(Icons.save_outlined),
                  tone: ButtonTone.primary,
                  child: const Text('保存绑定信息'),
                ),
              ),
            ],
          ),
          _SettingsGroup(
            title: '重置密码',
            children: [
              if (!_canResetPasswordWithoutCurrentPassword) ...[
                _LabeledTextField(
                  label: '当前密码',
                  controller: _currentPasswordController,
                  obscureText: _obscureCurrentPassword,
                  onTogglePasswordVisibility: () => setState(
                    () => _applyPasswordVisibilityPatch(
                      passwordVisibilityToggled(
                        field: PasswordVisibilityField.current,
                        obscureCurrentPassword: _obscureCurrentPassword,
                        obscureNewPassword: _obscureNewPassword,
                        obscureConfirmPassword: _obscureConfirmPassword,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
              ],
              _LabeledTextField(
                label: '新密码',
                controller: _newPasswordController,
                obscureText: _obscureNewPassword,
                onTogglePasswordVisibility: () => setState(
                  () => _applyPasswordVisibilityPatch(
                    passwordVisibilityToggled(
                      field: PasswordVisibilityField.newPassword,
                      obscureCurrentPassword: _obscureCurrentPassword,
                      obscureNewPassword: _obscureNewPassword,
                      obscureConfirmPassword: _obscureConfirmPassword,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              _LabeledTextField(
                label: '确认新密码',
                controller: _confirmPasswordController,
                obscureText: _obscureConfirmPassword,
                onTogglePasswordVisibility: () => setState(
                  () => _applyPasswordVisibilityPatch(
                    passwordVisibilityToggled(
                      field: PasswordVisibilityField.confirm,
                      obscureCurrentPassword: _obscureCurrentPassword,
                      obscureNewPassword: _obscureNewPassword,
                      obscureConfirmPassword: _obscureConfirmPassword,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 14),
              ResponsiveDialogActionBar(
                expanded: true,
                actions: [
                  ResponsiveDialogAction(
                    label: '忘记密码',
                    icon: Icons.help_outline,
                    loading: _verifyingPasswordReset,
                    onPressed: _verifyingPasswordReset || _isManagingUser
                        ? null
                        : _verifyEmailForPasswordReset,
                  ),
                  ResponsiveDialogAction(
                    label: '更新密码',
                    icon: Icons.lock_reset,
                    tone: ButtonTone.primary,
                    loading: _changingPassword,
                    onPressed:
                        _changingPassword || managedSuperuserPasswordBlocked
                        ? null
                        : _changePassword,
                  ),
                ],
              ),
            ],
          ),
          _SettingsGroup(
            title: '账号活动',
            trailing: ButtonIcon(
              tooltip: '刷新账号活动',
              onPressed: _loadingSessions ? null : _loadSessions,
              icon: const Icon(Icons.refresh),
              size: 30,
            ),
            children: [
              _ReadOnlyLine(
                label: '账号创建时间',
                value: account_display.formatDateTime(user.createdAt),
              ),
              const SizedBox(height: 14),
              _SessionList(sessions: _sessions, loading: _loadingSessions),
            ],
          ),
          if (_isManagingUser)
            _SettingsGroup(
              title: '账号状态',
              danger: _accountSuspended,
              children: [
                Text(
                  _accountSuspended ? '账号封禁中' : '账号状态正常',
                  style: UiTypography.body.copyWith(
                    color: _accountSuspended
                        ? UiColors.danger
                        : UiColors.textSecondary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  _accountSuspended
                      ? '该账号当前无法登录；用户名、头像、资料和历史消息仍正常保留。'
                      : '封禁会立即撤销该账号的所有登录会话，并阻止其再次登录。',
                  style: UiTypography.body.copyWith(
                    color: UiColors.textSecondary,
                    height: 1.5,
                  ),
                ),
                const SizedBox(height: 14),
                Align(
                  alignment: Alignment.centerRight,
                  child: Button(
                    onPressed: user.isSuperuser || _changingAccountSuspension
                        ? null
                        : _toggleManagedAccountSuspension,
                    loading: _changingAccountSuspension,
                    icon: Icon(
                      _accountSuspended
                          ? Icons.lock_open_outlined
                          : Icons.block_outlined,
                    ),
                    tone: _accountSuspended
                        ? ButtonTone.primary
                        : ButtonTone.danger,
                    child: Text(_accountSuspended ? '解除封禁' : '封禁账号'),
                  ),
                ),
              ],
            ),
          _SettingsGroup(
            title: '注销账号',
            danger: true,
            children: [
              Text(
                account_display.accountDeletionDescription(user),
                style: const TextStyle(
                  color: _textSecondary,
                  fontSize: 13,
                  height: 1.5,
                ),
              ),
              const SizedBox(height: 14),
              Align(
                alignment: Alignment.centerRight,
                child: Button(
                  onPressed:
                      _settingsController.canDeleteAccount &&
                          !(_isManagingUser && user.isSuperuser) &&
                          account_display.canStartAccountDeletion(
                            hasApi: _settingsController.hasApi,
                            user: user,
                            deletingAccount: _deletingAccount,
                          )
                      ? _confirmDeleteAccount
                      : null,
                  loading: _deletingAccount,
                  icon: const Icon(Icons.delete_outline),
                  tone: ButtonTone.danger,
                  child: const Text('注销账号'),
                ),
              ),
            ],
          ),
        ],
      ],
    );
  }

  Widget _buildAboutContent() {
    return SettingsList(
      children: [
        _SettingsGroup(
          title: '版本信息',
          children: [
            _CopyableField(
              label: '版本编号',
              value: appVersionNumberLabel(widget.appVersion),
            ),
            const SizedBox(height: 14),
            _CopyableField(
              label: '发行时间',
              value: officialVersionTimeLabel(gangChatClientReleaseTimestamp),
            ),
            const SizedBox(height: 14),
            _CopyableField(label: '上次更新时间', value: _lastUpdateDate),
            const SizedBox(height: 14),
            _ToggleSetting(
              label: '自动提示更新',
              value: _autoPromptUpdates,
              enabled: !_isManagingUser,
              onChanged: (value) => unawaited(_setAutoUpdatePrompt(value)),
            ),
            if (_isManagingUser) ...[
              const SizedBox(height: 10),
              const Text(
                '该设置仅保存在用户设备，无法远程读取',
                style: TextStyle(
                  color: _textSecondary,
                  fontSize: 13,
                  height: 1.4,
                ),
              ),
            ],
            const SizedBox(height: 14),
            Button(
              onPressed: _checkingAppVersion
                  ? null
                  : () => unawaited(_checkAppVersion()),
              loading: _checkingAppVersion,
              tone: ButtonTone.primary,
              width: double.infinity,
              child: const Text('检查更新'),
            ),
          ],
        ),
        _SettingsGroup(
          title: '意见反馈',
          children: [
            Button(
              onPressed: _openingFeedbackMail
                  ? null
                  : () => unawaited(_openFeedbackMail()),
              loading: _openingFeedbackMail,
              tone: ButtonTone.primary,
              width: double.infinity,
              child: const Text('意见反馈'),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildAppUpdateContent(AvailableAppUpdate update) {
    return AppUpdatePage(
      update: update,
      checking: _checkingAppVersion,
      downloading: _downloadingAppUpdate,
      downloadedBytes: _updateDownloadedBytes,
      downloadTotalBytes: _updateDownloadTotalBytes,
      error: _updateDownloadError,
      onBack: _closeAppUpdatePage,
      onRefresh: () => unawaited(_checkAppVersion()),
      onIgnoreVersion: () => unawaited(_confirmIgnoreAvailableUpdate()),
      onDownload: () => unawaited(_confirmDownloadAvailableUpdate()),
    );
  }

  Widget _buildVoiceContent() {
    return SettingsList(
      children: [
        _SettingsGroup(
          title: '输入',
          children: [
            _SettingsSubPanel(
              child: _DeviceSection(
                title: '输入源',
                icon: Icons.mic,
                devices: _audioInputs,
                selectedDevice: _selectedInput,
                busyDeviceId: _busyDeviceId,
                emptyText: _isManagingUser
                    ? '该设置仅保存在用户设备，无法远程读取'
                    : (_loading ? '正在加载输入源' : '未找到输入源'),
                fallbackLabel: '麦克风',
                onSelect: _selectInput,
              ),
            ),
            const SizedBox(height: 12),
            _SettingsSubPanel(
              child: _AudioControlPanel(
                title: '输入音量',
                icon: Icons.graphic_eq,
                volume: _inputVolume,
                level: _inputLevel,
                testing: _testingInput,
                testTooltip: audioInputTestTooltip(_testingInput),
                disabled: !_isManagingUser && _audioInputs.isEmpty,
                testDisabled: _isManagingUser,
                onVolumeChanged: (value) => unawaited(_setInputVolume(value)),
                onToggleTest: _toggleInputTest,
              ),
            ),
          ],
        ),
        _SettingsGroup(
          title: '输出',
          children: [
            _SettingsSubPanel(
              child: _DeviceSection(
                title: '输出源',
                icon: Icons.headphones,
                devices: _audioOutputs,
                selectedDevice: _selectedOutput,
                busyDeviceId: _busyDeviceId,
                emptyText: _isManagingUser
                    ? '该设置仅保存在用户设备，无法远程读取'
                    : (_loading ? '正在加载输出源' : '未找到输出源'),
                fallbackLabel: '输出',
                onSelect: _selectOutput,
              ),
            ),
            const SizedBox(height: 12),
            _SettingsSubPanel(
              child: _AudioControlPanel(
                title: '输出音量',
                icon: Icons.volume_up,
                volume: _outputVolume,
                level: _outputLevel,
                testing: _testingOutput,
                testTooltip: audioOutputTestTooltip(_testingOutput),
                disabled: !_isManagingUser && _audioOutputs.isEmpty,
                testDisabled: _isManagingUser,
                onVolumeChanged: (value) => unawaited(_setOutputVolume(value)),
                onToggleTest: _toggleOutputTest,
              ),
            ),
          ],
        ),
        _SettingsGroup(
          title: '屏幕共享',
          children: [
            _SettingsSubPanel(
              child: _ScreenShareQualitySection(
                selectedHeight: _screenShareMaxHeight,
                selectedFrameRate: _screenShareFrameRate,
                remoteUnavailable: _isManagingUser,
                onHeightSelected: (height) =>
                    unawaited(_setScreenShareMaxHeight(height)),
                onFrameRateSelected: (frameRate) =>
                    unawaited(_setScreenShareFrameRate(frameRate)),
              ),
            ),
          ],
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final availableUpdate = _availableAppUpdate;
    if (_section == SettingsSection.about && availableUpdate != null) {
      return FloatingNoticeEmitter(
        notices: _floatingNotices(),
        child: Scaffold(
          backgroundColor: _primaryDarkLow,
          body: _buildAppUpdateContent(availableUpdate),
        ),
      );
    }

    return FloatingNoticeEmitter(
      notices: _floatingNotices(),
      child: Scaffold(
        backgroundColor: _primaryDarkLow,
        body: SettingsScaffold(
          icon: Icons.settings_outlined,
          title: _pageTitle,
          onBack: widget.onClose != null || !widget.isSubWindow
              ? (widget.onClose ?? () => Navigator.of(context).pop())
              : null,
          headerAction: ButtonIcon(
            tooltip: '刷新设置',
            onPressed: _isRefreshing ? null : _refreshActiveSection,
            icon: const Icon(Icons.refresh),
            size: 38,
            loading: _isRefreshing,
          ),
          pinned: _SettingsNavigation(
            selected: _section,
            onChanged: _selectSection,
          ),
          body: _buildSectionContent(),
        ),
      ),
    );
  }
}
