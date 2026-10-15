import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../app/auth_form.dart';
import '../app/auth_session_controller.dart';
import '../app/account_forms.dart';
import '../app/email_verification_controller.dart';
import '../app/language_preference.dart';
import '../app/login_account_history.dart';
import '../app/password_reset_controller.dart';
import '../auth/auth_client.dart';
import '../ui/ui.dart';
import 'desktop_window_controller.dart';
import 'email_verification_flow.dart';
import 'window_controls.dart';
import 'password_reset_flow.dart';

typedef AuthWindowLock =
    Future<void> Function({
      bool registering,
      bool moveWindow,
      bool centerWindow,
      Size? size,
    });
typedef AuthSizeForMode = Size Function(bool registering, {bool showingError});
typedef AuthSubmit =
    Future<void> Function(
      AuthRequest request, {
      required bool rememberPassword,
    });
typedef AuthUsernameAvailabilityCheck = Future<bool> Function(String username);
typedef AuthEmailAvailabilityCheck = Future<bool> Function(String email);

enum _AuthMode { login, register }

const double _authTitleBarTopInset = 14;
const double _authTitleBarHeight = 54;
const double _authTitleBarGap = 14;
const double _authBrandIconSize = 36;
const double _authBrandGap = 10;
const double _authFieldGap = 3;
const double _authModeGap = 10;
const double _authActionGap = 6;
const double _authErrorHeight = 11;
const double _authPasswordErrorGap = 4;
const double _authErrorRememberGap = 0;
const double _authRememberHeight = 28;
const double _authSubmitHeight = 32;
const double _accountHistoryItemHeight = 38;
const double _authSegmentedControlOuterHeight = 42;
const double _authInputOuterHeight = Input.defaultHeight + 8;
const double _accountHistoryDropdownTop =
    _authTitleBarHeight +
    _authTitleBarGap +
    _authSegmentedControlOuterHeight +
    _authModeGap +
    _authInputOuterHeight +
    _authFieldGap;
const double _authRegisterFieldsCenter =
    _authTitleBarTopInset +
    _authTitleBarHeight +
    _authTitleBarGap +
    _authSegmentedControlOuterHeight +
    _authModeGap +
    (_authInputOuterHeight * 4 + _authFieldGap * 3) / 2;
const _authTitleBarStyle = TextStyle(
  color: UiColors.text,
  fontSize: 22,
  fontWeight: FontWeight.w700,
  height: 1,
  letterSpacing: 0,
);
const _androidAuthSystemUiOverlayStyle = SystemUiOverlayStyle(
  statusBarColor: UiColors.surfaceLow,
  statusBarBrightness: Brightness.dark,
  statusBarIconBrightness: Brightness.light,
  systemStatusBarContrastEnforced: false,
  systemNavigationBarColor: UiColors.surfaceLow,
  systemNavigationBarDividerColor: UiColors.surfaceLow,
  systemNavigationBarIconBrightness: Brightness.light,
  systemNavigationBarContrastEnforced: false,
);

class LoginPage extends StatefulWidget {
  const LoginPage({
    super.key,
    required this.sizeForMode,
    required this.onSubmit,
    required this.consumeInitialWindowLock,
    required this.lockAuthWindow,
    this.checkUsernameAvailability,
    this.checkEmailAvailability,
    this.emailVerificationController,
    this.passwordResetController,
    this.language = defaultLanguagePreference,
    this.accountHistoryStore = const NoopLoginAccountHistoryStore(),
    this.submitPersistsAccountHistory = false,
    this.windowController,
  });

  final AuthSizeForMode sizeForMode;
  final AuthSubmit onSubmit;
  final bool Function() consumeInitialWindowLock;
  final AuthWindowLock lockAuthWindow;
  final AuthUsernameAvailabilityCheck? checkUsernameAvailability;
  final AuthEmailAvailabilityCheck? checkEmailAvailability;
  final EmailVerificationController? emailVerificationController;
  final PasswordResetController? passwordResetController;
  final String language;
  final LoginAccountHistoryStore accountHistoryStore;
  final bool submitPersistsAccountHistory;
  final DesktopWindowController? windowController;

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _login = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _confirmPassword = TextEditingController();

  bool _registering = false;
  bool _obscurePassword = true;
  bool _obscureConfirmPassword = true;
  AuthSubmitState _submitState = const AuthSubmitState();
  List<LoginAccountRecord> _accountHistory = const [];
  bool _showAccountHistory = false;
  bool _rememberPassword = false;
  bool _registerEmailEmpty = true;
  String? _verifiedEmail;
  String? _emailVerificationToken;
  bool _checkingEmailAvailability = false;
  bool _checkingPasswordReset = false;
  String? _selectedHistoryLogin;
  final Object _accountHistoryTapRegion = Object();
  Timer? _usernameAvailabilityDebounce;
  String? _usernameAvailabilityQuery;
  bool _checkingUsernameAvailability = false;
  String? _usernameAvailabilityError;

  bool get _showingError =>
      _submitState.error != null && _submitState.error!.isNotEmpty;
  bool get _emailVerified =>
      _verifiedEmail != null &&
      _verifiedEmail == _email.text.trim().toLowerCase();
  bool get _canShowAccountHistory =>
      !_registering && !_submitState.busy && _accountHistory.isNotEmpty;

  @override
  void initState() {
    super.initState();
    unawaited(_loadAccountHistory());
    if (widget.consumeInitialWindowLock()) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        unawaited(_lockAuthSize(registering: false, moveWindow: false));
      }
    });
  }

  @override
  void didUpdateWidget(LoginPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.accountHistoryStore != widget.accountHistoryStore) {
      unawaited(_loadAccountHistory());
    }
  }

  @override
  void dispose() {
    _login.dispose();
    _email.dispose();
    _password.dispose();
    _confirmPassword.dispose();
    _usernameAvailabilityDebounce?.cancel();
    super.dispose();
  }

  Future<void> _loadAccountHistory() async {
    try {
      final records = await widget.accountHistoryStore.read();
      if (!mounted) return;
      final lastLogin = lastLoginAccountRecord(records);
      setState(() {
        _accountHistory = records;
        if (records.isEmpty) _showAccountHistory = false;
        if (!_registering &&
            _login.text.isEmpty &&
            _password.text.isEmpty &&
            lastLogin != null) {
          _setText(_login, lastLogin.login);
          _selectedHistoryLogin = lastLogin.login;
          if (lastLogin.remembersPassword) {
            _setText(_password, lastLogin.password!);
            _rememberPassword = true;
          } else {
            _password.clear();
            _rememberPassword = false;
          }
        }
      });
    } catch (_) {
      // Local history is a convenience feature; the auth form stays usable.
    }
  }

  Future<void> _submit() async {
    if (_submitState.busy) return;

    final result = authRequestFromForm(
      registering: _registering,
      username: _registering ? _login.text : '',
      login: _registering ? _email.text : _login.text,
      password: _password.text,
      confirmPassword: _confirmPassword.text,
      emailVerificationToken: _emailVerified
          ? _emailVerificationToken ?? ''
          : '',
      language: widget.language,
    );
    final request = result.request;
    if (request == null) {
      await _showSubmitState(authSubmitInvalid(result.error));
      return;
    }

    setState(() => _submitState = authSubmitStarted());

    if (_registering) {
      final availabilityError = await _ensureUsernameAvailable(
        request.username!,
      );
      if (!mounted) return;
      if (availabilityError != null) {
        await _showSubmitState(authSubmitInvalid(availabilityError));
        return;
      }
    }

    try {
      await widget.onSubmit(
        request,
        rememberPassword: !_registering && _rememberPassword,
      );
      if (!mounted) return;
      if (!request.registering && !widget.submitPersistsAccountHistory) {
        await _rememberSubmittedAccount(request);
      }
    } catch (e) {
      if (!mounted) return;
      if (request.registering &&
          e is AuthException &&
          e.code == 'email_verification_required') {
        _verifiedEmail = null;
        _emailVerificationToken = null;
      }
      await _showSubmitState(authSubmitFailed(e, language: widget.language));
    }
  }

  Future<void> _rememberSubmittedAccount(AuthRequest request) async {
    final updated = rememberLoginAccount(
      records: _accountHistory,
      login: request.login,
      password: request.password,
      rememberPassword: _rememberPassword,
    );
    setState(() {
      _accountHistory = updated;
      _showAccountHistory = false;
    });
    try {
      await widget.accountHistoryStore.write(updated);
    } catch (_) {
      // The login already succeeded; failing to persist history is non-fatal.
    }
  }

  void _minimizeWindow() {
    final windowController = widget.windowController;
    if (windowController == null) return;
    unawaited(windowController.minimizeWindow());
  }

  void _closeWindow() {
    final windowController = widget.windowController;
    if (windowController == null) return;
    unawaited(windowController.closeWindow());
  }

  void _setMode(bool registering) {
    if (_submitState.busy || _registering == registering) return;
    if (registering) {
      unawaited(_expandAndShowRegister());
      return;
    }
    setState(() {
      _registering = false;
      _resetUsernameAvailability();
      _submitState = const AuthSubmitState();
      _showAccountHistory = false;
    });
    unawaited(_lockAuthSize(registering: false));
  }

  Future<void> _expandAndShowRegister() async {
    await _lockAuthSize(registering: true);
    if (!mounted || _submitState.busy) return;
    setState(() {
      _registering = true;
      _submitState = const AuthSubmitState();
      _showAccountHistory = false;
    });
    _scheduleUsernameAvailabilityCheck(_login.text);
  }

  void _toggleAccountHistory() {
    if (!_canShowAccountHistory) return;
    setState(() => _showAccountHistory = !_showAccountHistory);
  }

  void _closeAccountHistory() {
    if (!_showAccountHistory) return;
    setState(() => _showAccountHistory = false);
  }

  void _clearLoginInput() {
    if (_submitState.busy) return;
    _login.clear();
    _password.clear();
    setState(() {
      _selectedHistoryLogin = null;
      _rememberPassword = false;
      _showAccountHistory = false;
      _submitState = const AuthSubmitState();
    });
  }

  void _selectAccountRecord(LoginAccountRecord record) {
    _setText(_login, record.login);
    if (record.remembersPassword) {
      _setText(_password, record.password!);
    } else {
      _password.clear();
    }
    setState(() {
      _selectedHistoryLogin = record.login;
      _rememberPassword = record.remembersPassword;
      _showAccountHistory = false;
      _submitState = const AuthSubmitState();
    });
  }

  void _deleteAccountRecord(LoginAccountRecord record) {
    final updated = deleteLoginAccountRecord(
      records: _accountHistory,
      login: record.login,
    );
    setState(() {
      _accountHistory = updated;
      if (updated.isEmpty) _showAccountHistory = false;
      if (_selectedHistoryLogin != null &&
          loginAccountsMatch(_selectedHistoryLogin!, record.login)) {
        _selectedHistoryLogin = null;
      }
    });
    unawaited(_writeAccountHistory(updated));
  }

  Future<void> _writeAccountHistory(List<LoginAccountRecord> records) async {
    try {
      await widget.accountHistoryStore.write(records);
    } catch (_) {
      // The in-memory UI can still continue if the convenience store fails.
    }
  }

  void _handleLoginChanged(String value) {
    final selected = _selectedHistoryLogin;
    if (selected == null || loginAccountsMatch(selected, value)) return;
    setState(() {
      _selectedHistoryLogin = null;
      if (_rememberPassword) _rememberPassword = false;
    });
  }

  void _setRememberPassword(bool rememberPassword) {
    if (_submitState.busy) return;
    setState(() => _rememberPassword = rememberPassword);
  }

  void _setText(TextEditingController controller, String text) {
    controller.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
  }

  Future<void> _showSubmitState(AuthSubmitState state) async {
    setState(() => _submitState = state);
  }

  Future<void> _showEmailVerification() async {
    if (_submitState.busy || _checkingEmailAvailability) return;
    final email = _email.text.trim();
    final error = registerEmailValidationError(email);
    if (error != null) {
      showFloatingErrorNotice(context, error);
      return;
    }
    final checker = widget.checkEmailAvailability;
    if (checker == null) {
      showFloatingErrorNotice(context, '暂时无法检测邮箱是否重复');
      return;
    }
    setState(() => _checkingEmailAvailability = true);
    bool? available;
    try {
      available = await checker(email);
    } catch (_) {
      available = null;
    }
    if (!mounted) return;
    if (!_registering || _email.text.trim() != email) {
      setState(() => _checkingEmailAvailability = false);
      return;
    }
    if (available == null) {
      setState(() => _checkingEmailAvailability = false);
      showFloatingErrorNotice(context, '暂时无法检测邮箱是否重复');
      return;
    }
    if (!available) {
      setState(() => _checkingEmailAvailability = false);
      showFloatingErrorNotice(context, '该邮箱已被其他用户使用');
      return;
    }
    final controller = widget.emailVerificationController;
    if (controller == null) {
      setState(() => _checkingEmailAvailability = false);
      showFloatingErrorNotice(context, '暂时无法使用邮箱验证功能');
      return;
    }
    EmailVerificationChallenge? challenge;
    try {
      challenge = await controller.inspectOrStart(email);
      if (!mounted) return;
    } catch (error) {
      if (mounted) {
        showFloatingErrorNotice(context, emailVerificationErrorMessage(error));
      }
    }
    if (!mounted) return;
    setState(() => _checkingEmailAvailability = false);
    if (challenge == null || !_registering || _email.text.trim() != email) {
      return;
    }
    final verificationToken = await showEmailVerificationDialog(
      context: context,
      email: email,
      challenge: challenge,
      controller: controller,
    );
    if (verificationToken != null && mounted) {
      setState(() {
        _verifiedEmail = email.toLowerCase();
        _emailVerificationToken = verificationToken;
      });
    }
  }

  Future<void> _forgotPassword() async {
    if (_submitState.busy || _checkingPasswordReset) return;
    final controller = widget.passwordResetController;
    if (controller == null) {
      showFloatingErrorNotice(context, '暂时无法使用忘记密码功能');
      return;
    }
    if (_login.text.trim().isEmpty) {
      showFloatingErrorNotice(context, '请先输入登录用户名或邮箱');
      return;
    }
    setState(() => _checkingPasswordReset = true);
    await showPasswordResetFlow(
      context: context,
      login: _login.text,
      controller: controller,
    );
    if (mounted) setState(() => _checkingPasswordReset = false);
  }

  void _handleRegisterEmailChanged(String value) {
    final normalized = value.trim().toLowerCase();
    final emailEmpty = normalized.isEmpty;
    final verificationChanged =
        _verifiedEmail != null && _verifiedEmail != normalized;
    if (_registerEmailEmpty == emailEmpty && !verificationChanged) return;
    setState(() {
      _registerEmailEmpty = emailEmpty;
      if (verificationChanged) {
        _verifiedEmail = null;
        _emailVerificationToken = null;
      }
    });
  }

  void _resetUsernameAvailability() {
    _usernameAvailabilityDebounce?.cancel();
    _usernameAvailabilityDebounce = null;
    _usernameAvailabilityQuery = null;
    _checkingUsernameAvailability = false;
    _usernameAvailabilityError = null;
  }

  void _handleRegisterUsernameChanged(String value) {
    final username = value.trim();
    _usernameAvailabilityDebounce?.cancel();
    final formatError = loginUsernameValidationError(username);
    setState(() {
      _usernameAvailabilityQuery = null;
      _checkingUsernameAvailability = false;
      _usernameAvailabilityError = formatError;
    });
    if (formatError != null || widget.checkUsernameAvailability == null) return;
    _scheduleUsernameAvailabilityCheck(username);
  }

  void _scheduleUsernameAvailabilityCheck(String value) {
    final username = value.trim();
    if (loginUsernameValidationError(username) != null ||
        widget.checkUsernameAvailability == null) {
      return;
    }
    _usernameAvailabilityDebounce?.cancel();
    if (mounted && _registering) {
      setState(() {
        _usernameAvailabilityQuery = username;
        _checkingUsernameAvailability = true;
        _usernameAvailabilityError = null;
      });
    }
    _usernameAvailabilityDebounce = Timer(
      const Duration(milliseconds: 350),
      () => unawaited(_checkUsernameAvailability(username)),
    );
  }

  Future<String?> _checkUsernameAvailability(String username) async {
    final checker = widget.checkUsernameAvailability;
    if (checker == null) return null;
    final normalized = username.trim();
    if (loginUsernameValidationError(normalized) case final error?) {
      return error;
    }
    if (mounted) {
      setState(() {
        _usernameAvailabilityQuery = normalized;
        _checkingUsernameAvailability = true;
        _usernameAvailabilityError = null;
      });
    }
    try {
      final available = await checker(normalized);
      final error = available ? null : '该登录用户名已被其他用户使用';
      if (mounted && _registering && _login.text.trim() == normalized) {
        setState(() {
          _checkingUsernameAvailability = false;
          _usernameAvailabilityError = error;
        });
      }
      return error;
    } catch (_) {
      const error = '暂时无法检测登录用户名是否重复';
      if (mounted && _registering && _login.text.trim() == normalized) {
        setState(() {
          _checkingUsernameAvailability = false;
          _usernameAvailabilityError = error;
        });
      }
      return error;
    }
  }

  Future<String?> _ensureUsernameAvailable(String username) async {
    final normalized = username.trim();
    if (_usernameAvailabilityQuery == normalized &&
        !_checkingUsernameAvailability) {
      return _usernameAvailabilityError;
    }
    _usernameAvailabilityDebounce?.cancel();
    return _checkUsernameAvailability(normalized);
  }

  Future<void> _lockAuthSize({
    bool? registering,
    bool showingError = false,
    bool moveWindow = true,
    bool centerWindow = false,
  }) {
    final resolvedRegistering = registering ?? _registering;
    return widget.lockAuthWindow(
      registering: resolvedRegistering,
      moveWindow: moveWindow,
      centerWindow: centerWindow,
      size: widget.sizeForMode(resolvedRegistering, showingError: showingError),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isAndroid = Theme.of(context).platform == TargetPlatform.android;
    // Android keeps both modes on the registration geometry. The four
    // registration fields are centered by that surface size, and the login
    // fields retain the same starting position when switching modes.
    final size = widget.sizeForMode(isAndroid ? true : _registering);
    final showWindowControls =
        widget.windowController != null &&
        Theme.of(context).platform == TargetPlatform.windows;
    final page = Scaffold(
      backgroundColor: isAndroid ? UiColors.surfaceLow : UiColors.background,
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) {
            final surfaceWidth = math.min(size.width, constraints.maxWidth);
            final surfaceHeight = math.min(size.height, constraints.maxHeight);
            final verticalSlack = constraints.maxHeight - surfaceHeight;
            final surfaceTop = isAndroid
                ? (constraints.maxHeight / 2 - _authRegisterFieldsCenter)
                      .clamp(0.0, verticalSlack)
                      .toDouble()
                : 0.0;
            return Padding(
              padding: EdgeInsets.only(top: surfaceTop),
              child: Align(
                alignment: Alignment.topCenter,
                child: SizedBox(
                  key: const ValueKey('auth-surface'),
                  width: surfaceWidth,
                  height: surfaceHeight,
                  child: DecoratedBox(
                    decoration: const BoxDecoration(color: UiColors.surfaceLow),
                    child: Stack(
                      children: [
                        Padding(
                          padding: const EdgeInsets.fromLTRB(
                            24,
                            _authTitleBarTopInset,
                            24,
                            7,
                          ),
                          child: AutofillGroup(
                            child: LayoutBuilder(
                              builder: (context, constraints) {
                                return Stack(
                                  clipBehavior: Clip.none,
                                  children: [
                                    SingleChildScrollView(
                                      physics: const ClampingScrollPhysics(),
                                      child: ConstrainedBox(
                                        constraints: BoxConstraints(
                                          minHeight: constraints.maxHeight,
                                        ),
                                        child: Column(
                                          crossAxisAlignment:
                                              CrossAxisAlignment.stretch,
                                          children: [
                                            _buildTitleBar(),
                                            const SizedBox(
                                              height: _authTitleBarGap,
                                            ),
                                            SegmentedControl<_AuthMode>(
                                              expanded: true,
                                              value: _registering
                                                  ? _AuthMode.register
                                                  : _AuthMode.login,
                                              segments: const [
                                                Segment(
                                                  value: _AuthMode.login,
                                                  label: '登录',
                                                  icon: Icons.login_outlined,
                                                ),
                                                Segment(
                                                  value: _AuthMode.register,
                                                  label: '注册',
                                                  icon: Icons
                                                      .person_add_alt_1_outlined,
                                                ),
                                              ],
                                              onChanged: (mode) {
                                                if (_submitState.busy) return;
                                                _setMode(
                                                  mode == _AuthMode.register,
                                                );
                                              },
                                            ),
                                            const SizedBox(
                                              height: _authModeGap,
                                            ),
                                            TapRegion(
                                              groupId: _accountHistoryTapRegion,
                                              onTapOutside: (_) =>
                                                  _closeAccountHistory(),
                                              child:
                                                  _buildPrimaryIdentityInput(),
                                            ),
                                            if (_registering) ...[
                                              const SizedBox(
                                                height: _authFieldGap,
                                              ),
                                              _buildRegisterEmailInput(),
                                            ],
                                            const SizedBox(
                                              height: _authFieldGap,
                                            ),
                                            Input(
                                              controller: _password,
                                              enabled: !_submitState.busy,
                                              hintText: '密码',
                                              prefixIcon: Icons.lock_outline,
                                              autofillHints: [
                                                _registering
                                                    ? AutofillHints.newPassword
                                                    : AutofillHints.password,
                                              ],
                                              obscureText: _obscurePassword,
                                              suffix: _PasswordVisibilityToggle(
                                                obscure: _obscurePassword,
                                                enabled: !_submitState.busy,
                                                onPressed: () {
                                                  setState(
                                                    () => _obscurePassword =
                                                        !_obscurePassword,
                                                  );
                                                },
                                              ),
                                              maxLines: 1,
                                              onSubmitted: _registering
                                                  ? null
                                                  : (_) => _submit(),
                                            ),
                                            if (!_registering) ...[
                                              _buildLoginTail(),
                                            ],
                                            if (_registering) ...[
                                              const SizedBox(
                                                height: _authFieldGap,
                                              ),
                                              Input(
                                                controller: _confirmPassword,
                                                enabled: !_submitState.busy,
                                                hintText: '确认密码',
                                                prefixIcon: Icons.lock_outline,
                                                autofillHints: const [
                                                  AutofillHints.newPassword,
                                                ],
                                                obscureText:
                                                    _obscureConfirmPassword,
                                                suffix: _PasswordVisibilityToggle(
                                                  obscure:
                                                      _obscureConfirmPassword,
                                                  enabled: !_submitState.busy,
                                                  onPressed: () {
                                                    setState(
                                                      () => _obscureConfirmPassword =
                                                          !_obscureConfirmPassword,
                                                    );
                                                  },
                                                ),
                                                maxLines: 1,
                                                onSubmitted: (_) => _submit(),
                                              ),
                                              _buildRegisterTail(),
                                            ],
                                            const SizedBox(
                                              height: _authActionGap,
                                            ),
                                            Button(
                                              width: double.infinity,
                                              tone: ButtonTone.primary,
                                              height: _authSubmitHeight,
                                              loading: _submitState.busy,
                                              onPressed: _submit,
                                              child: Text(
                                                _registering ? '创建账号' : '登录',
                                              ),
                                            ),
                                          ],
                                        ),
                                      ),
                                    ),
                                    if (_showAccountHistory &&
                                        _canShowAccountHistory)
                                      Positioned(
                                        top: _accountHistoryDropdownTop,
                                        left: 0,
                                        right: 0,
                                        child: TapRegion(
                                          groupId: _accountHistoryTapRegion,
                                          child: _AccountHistoryDropdown(
                                            records: _accountHistory,
                                            onSelected: _selectAccountRecord,
                                            onDeleted: _deleteAccountRecord,
                                          ),
                                        ),
                                      ),
                                  ],
                                );
                              },
                            ),
                          ),
                        ),
                        if (showWindowControls)
                          Positioned(
                            top: 4,
                            right: 0,
                            child: SelectionContainer.disabled(
                              child: AppWindowControls(
                                onMinimize: _minimizeWindow,
                                onClose: _closeWindow,
                                showMaximize: false,
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
    if (!isAndroid) return page;
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: _androidAuthSystemUiOverlayStyle,
      child: page,
    );
  }

  Widget _buildLoginTail() {
    const errorTop = _authPasswordErrorGap;
    const rememberTop = errorTop + _authErrorHeight + _authErrorRememberGap;
    const height = rememberTop + _authRememberHeight;

    return SizedBox(
      height: height,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Positioned(
            left: 0,
            right: 0,
            top: rememberTop,
            child: _RememberPasswordRow(
              value: _rememberPassword,
              enabled: !_submitState.busy,
              onChanged: _setRememberPassword,
              onForgotPassword: _forgotPassword,
              checkingPasswordReset: _checkingPasswordReset,
            ),
          ),
          Positioned(
            left: 0,
            right: 0,
            top: errorTop,
            child: _buildErrorSlot(),
          ),
        ],
      ),
    );
  }

  Widget _buildRegisterTail() {
    const errorTop = _authPasswordErrorGap;
    const height = errorTop + _authErrorHeight;

    return SizedBox(
      height: height,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Positioned(
            left: 0,
            right: 0,
            top: errorTop,
            child: _buildErrorSlot(),
          ),
        ],
      ),
    );
  }

  Widget _buildErrorSlot() {
    return SizedBox(
      key: const ValueKey('auth-error-slot'),
      height: _authErrorHeight,
      child: Align(
        alignment: Alignment.centerLeft,
        child: _showingError
            ? Text(
                _submitState.error!,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: UiColors.danger,
                  fontSize: 10,
                  height: 1,
                  fontWeight: FontWeight.w500,
                ),
              )
            : const SizedBox.shrink(),
      ),
    );
  }

  Widget _buildPrimaryIdentityInput() {
    return Input(
      controller: _login,
      enabled: !_submitState.busy,
      hintText: _registering ? '登录用户名' : '登录用户名或邮箱地址',
      prefixIcon: Icons.person_outline,
      suffix: _registering
          ? _RegisterUsernameValidityIndicator(
              controller: _login,
              checking: _checkingUsernameAvailability,
              availabilityQuery: _usernameAvailabilityQuery,
              availabilityError: _usernameAvailabilityError,
              checksAvailability: widget.checkUsernameAvailability != null,
            )
          : _canShowAccountHistory
          ? _AccountInputSuffix(
              controller: _login,
              expanded: _showAccountHistory,
              enabled: !_submitState.busy,
              onClear: _clearLoginInput,
              onToggle: _toggleAccountHistory,
            )
          : null,
      autofillHints: _registering
          ? const [AutofillHints.username]
          : const [AutofillHints.username, AutofillHints.email],
      keyboardType: TextInputType.text,
      maxLines: 1,
      onChanged: _registering
          ? _handleRegisterUsernameChanged
          : _handleLoginChanged,
      onSubmitted: (_) => _submit(),
    );
  }

  Widget _buildRegisterEmailInput() {
    return Input(
      controller: _email,
      enabled: !_submitState.busy,
      hintText: '邮箱地址',
      prefixIcon: Icons.alternate_email,
      suffix: _registerEmailEmpty
          ? null
          : _checkingEmailAvailability
          ? const _InputCheckingIndicator(
              iconKey: ValueKey('auth-email-checking'),
              message: '正在检测邮箱是否可用',
            )
          : _emailVerified
          ? const _InputValidityIndicator(
              iconKey: ValueKey('auth-email-verified'),
              message: '邮箱已验证',
              valid: true,
            )
          : EmailVerificationInputAction(
              actionKey: const ValueKey('auth-email-verification-button'),
              label: '验证',
              semanticsLabel: '验证邮箱',
              enabled: !_submitState.busy,
              onPressed: _showEmailVerification,
            ),
      autofillHints: const [AutofillHints.email],
      keyboardType: TextInputType.emailAddress,
      maxLines: 1,
      onChanged: _handleRegisterEmailChanged,
    );
  }

  Widget _buildTitleBar() {
    final title = MouseRegion(
      cursor: SystemMouseCursors.basic,
      child: SelectionContainer.disabled(
        child: SizedBox(
          height: _authTitleBarHeight,
          child: Center(
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Image.asset(
                  'assets/branding/auth_brand_icon.png',
                  key: const ValueKey('auth-brand-icon'),
                  width: _authBrandIconSize,
                  height: _authBrandIconSize,
                  fit: BoxFit.contain,
                  filterQuality: FilterQuality.none,
                ),
                const SizedBox(width: _authBrandGap),
                const Text(
                  'Gang Chat',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: _authTitleBarStyle,
                ),
              ],
            ),
          ),
        ),
      ),
    );
    final windowController = widget.windowController;
    if (windowController == null) return title;
    return AppWindowDragRegion(
      windowController: windowController,
      child: title,
    );
  }
}

class _RememberPasswordRow extends StatelessWidget {
  const _RememberPasswordRow({
    required this.value,
    required this.enabled,
    required this.onChanged,
    required this.onForgotPassword,
    required this.checkingPasswordReset,
  });

  final bool value;
  final bool enabled;
  final ValueChanged<bool> onChanged;
  final VoidCallback onForgotPassword;
  final bool checkingPasswordReset;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: _authRememberHeight,
      child: Row(
        children: [
          Semantics(
            toggled: value,
            enabled: enabled,
            button: true,
            onTap: enabled ? () => onChanged(!value) : null,
            child: GestureDetector(
              key: const ValueKey('auth-remember-password-hot-zone'),
              behavior: HitTestBehavior.opaque,
              onTap: enabled ? () => onChanged(!value) : null,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text(
                    '记住密码',
                    style: TextStyle(
                      color: UiColors.textSecondary,
                      fontSize: 12,
                      fontWeight: FontWeight.w500,
                      letterSpacing: 0,
                    ),
                  ),
                  const SizedBox(width: 8),
                  ExcludeSemantics(
                    child: IgnorePointer(
                      child: UiCheckbox(
                        value: value,
                        enabled: enabled,
                        onChanged: enabled ? (_) {} : null,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const Spacer(),
          _AuthTextLink(
            label: '忘记密码？',
            enabled: enabled && !checkingPasswordReset,
            onPressed: onForgotPassword,
          ),
        ],
      ),
    );
  }
}

class _RegisterUsernameValidityIndicator extends StatelessWidget {
  const _RegisterUsernameValidityIndicator({
    required this.controller,
    required this.checking,
    required this.availabilityQuery,
    required this.availabilityError,
    required this.checksAvailability,
  });

  final TextEditingController controller;
  final bool checking;
  final String? availabilityQuery;
  final String? availabilityError;
  final bool checksAvailability;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        final username = controller.text.trim();
        if (username.isEmpty) return const SizedBox.shrink();
        final formatError = loginUsernameValidationError(username);
        final availabilityApplies = availabilityQuery == username;
        final pending = formatError == null && availabilityApplies && checking;
        final error =
            formatError ?? (availabilityApplies ? availabilityError : null);
        final availabilityChecked =
            !checksAvailability || (availabilityApplies && !checking);
        final valid = error == null && availabilityChecked;
        final message = pending
            ? '正在检测登录用户名是否可用'
            : error ?? (valid ? '登录用户名可用' : '等待检测登录用户名是否可用');
        if (pending || !valid && error == null) {
          return _InputCheckingIndicator(
            iconKey: const ValueKey('auth-username-checking'),
            message: message,
          );
        }
        return _InputValidityIndicator(
          iconKey: ValueKey<String>(
            valid ? 'auth-username-valid' : 'auth-username-invalid',
          ),
          message: message,
          valid: valid,
        );
      },
    );
  }
}

class _InputValidityIndicator extends StatelessWidget {
  const _InputValidityIndicator({
    required this.iconKey,
    required this.message,
    required this.valid,
  });

  final Key iconKey;
  final String message;
  final bool valid;

  @override
  Widget build(BuildContext context) {
    return UiTooltip(
      message: message,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8),
        child: Icon(
          valid ? Icons.check_circle_outline : Icons.error_outline,
          key: iconKey,
          size: 18,
          color: valid ? UiColors.accent : UiColors.danger,
        ),
      ),
    );
  }
}

class _InputCheckingIndicator extends StatelessWidget {
  const _InputCheckingIndicator({required this.iconKey, required this.message});

  final Key iconKey;
  final String message;

  @override
  Widget build(BuildContext context) {
    return UiTooltip(
      message: message,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8),
        child: Icon(
          Icons.hourglass_empty_outlined,
          key: iconKey,
          size: 18,
          color: UiColors.textSecondary,
        ),
      ),
    );
  }
}

class _AuthTextLink extends StatefulWidget {
  const _AuthTextLink({
    required this.label,
    required this.enabled,
    required this.onPressed,
  });

  final String label;
  final bool enabled;
  final VoidCallback onPressed;

  @override
  State<_AuthTextLink> createState() => _AuthTextLinkState();
}

class _AuthTextLinkState extends State<_AuthTextLink> {
  bool _hovered = false;

  bool get _interactive => widget.enabled;

  void _setHovered(bool value) {
    if (_hovered == value) return;
    setState(() => _hovered = value);
  }

  void _handleTap() {
    if (!_interactive) return;
    widget.onPressed();
  }

  @override
  Widget build(BuildContext context) {
    final color = !_interactive
        ? UiColors.textMuted
        : _hovered
        ? UiColors.controlAccent
        : UiColors.accent;
    return Semantics(
      button: true,
      enabled: _interactive,
      label: widget.label,
      child: MouseRegion(
        cursor: _interactive
            ? SystemMouseCursors.click
            : SystemMouseCursors.basic,
        onEnter: (_) => _setHovered(true),
        onExit: (_) => _setHovered(false),
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: _handleTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 4),
            child: Text(
              widget.label,
              style: TextStyle(
                color: color,
                fontSize: 12,
                fontWeight: FontWeight.w500,
                letterSpacing: 0,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _AccountInputSuffix extends StatefulWidget {
  const _AccountInputSuffix({
    required this.controller,
    required this.expanded,
    required this.enabled,
    required this.onClear,
    required this.onToggle,
  });

  final TextEditingController controller;
  final bool expanded;
  final bool enabled;
  final VoidCallback onClear;
  final VoidCallback onToggle;

  @override
  State<_AccountInputSuffix> createState() => _AccountInputSuffixState();
}

class _AccountInputSuffixState extends State<_AccountInputSuffix> {
  bool get _canClear =>
      widget.enabled && widget.controller.text.trim().isNotEmpty;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_handleTextChanged);
  }

  @override
  void didUpdateWidget(_AccountInputSuffix oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller == widget.controller) return;
    oldWidget.controller.removeListener(_handleTextChanged);
    widget.controller.addListener(_handleTextChanged);
  }

  @override
  void dispose() {
    widget.controller.removeListener(_handleTextChanged);
    super.dispose();
  }

  void _handleTextChanged() => setState(() {});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (_canClear)
          _AccountInputClearButton(
            enabled: widget.enabled,
            onPressed: widget.onClear,
          ),
        _AccountHistoryToggle(
          expanded: widget.expanded,
          onPressed: widget.onToggle,
        ),
      ],
    );
  }
}

class _AccountInputClearButton extends StatelessWidget {
  const _AccountInputClearButton({
    required this.enabled,
    required this.onPressed,
  });

  final bool enabled;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return UiTooltip(
      message: '清除账号',
      child: Semantics(
        button: true,
        enabled: enabled,
        label: '清除账号',
        child: MouseRegion(
          cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: enabled ? onPressed : null,
            child: SizedBox.square(
              dimension: 24,
              child: Center(
                child: Icon(Icons.close, size: 15, color: UiColors.textMuted),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _AccountHistoryToggle extends StatelessWidget {
  const _AccountHistoryToggle({
    required this.expanded,
    required this.onPressed,
  });

  final bool expanded;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return UiTooltip(
      message: expanded ? '收起账号记录' : '展开账号记录',
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: onPressed,
          child: SizedBox.square(
            dimension: 24,
            child: Center(
              child: Icon(
                expanded ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down,
                size: 18,
                color: UiColors.textMuted,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _AccountHistoryDropdown extends StatefulWidget {
  const _AccountHistoryDropdown({
    required this.records,
    required this.onSelected,
    required this.onDeleted,
  });

  final List<LoginAccountRecord> records;
  final ValueChanged<LoginAccountRecord> onSelected;
  final ValueChanged<LoginAccountRecord> onDeleted;

  @override
  State<_AccountHistoryDropdown> createState() =>
      _AccountHistoryDropdownState();
}

class _AccountHistoryDropdownState extends State<_AccountHistoryDropdown> {
  final ScrollController _scrollController = ScrollController();

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final rowCount = widget.records.length > 4 ? 4 : widget.records.length;
    final scrollable = widget.records.length > rowCount;
    return Material(
      key: const ValueKey('auth-account-history-dropdown'),
      color: Colors.transparent,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: UiColors.surface,
          borderRadius: BorderRadius.circular(UiRadii.md),
          border: Border.all(color: UiColors.border),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.26),
              blurRadius: 18,
              offset: const Offset(0, 10),
            ),
          ],
        ),
        child: SizedBox(
          height: rowCount * _accountHistoryItemHeight,
          child: Scrollbar(
            controller: _scrollController,
            interactive: scrollable,
            radius: const Radius.circular(999),
            thickness: scrollable ? 5 : null,
            thumbVisibility: scrollable,
            trackVisibility: false,
            child: ListView.builder(
              controller: _scrollController,
              padding: EdgeInsets.only(right: scrollable ? 7 : 0),
              physics: scrollable
                  ? const ClampingScrollPhysics()
                  : const NeverScrollableScrollPhysics(),
              itemExtent: _accountHistoryItemHeight,
              itemCount: widget.records.length,
              itemBuilder: (context, index) {
                final record = widget.records[index];
                return _AccountHistoryItem(
                  record: record,
                  onSelected: () => widget.onSelected(record),
                  onDeleted: () => widget.onDeleted(record),
                );
              },
            ),
          ),
        ),
      ),
    );
  }
}

class _AccountHistoryItem extends StatefulWidget {
  const _AccountHistoryItem({
    required this.record,
    required this.onSelected,
    required this.onDeleted,
  });

  final LoginAccountRecord record;
  final VoidCallback onSelected;
  final VoidCallback onDeleted;

  @override
  State<_AccountHistoryItem> createState() => _AccountHistoryItemState();
}

class _AccountHistoryItemState extends State<_AccountHistoryItem> {
  bool _hovered = false;

  void _setHovered(bool hovered) {
    if (_hovered == hovered) return;
    setState(() => _hovered = hovered);
  }

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => _setHovered(true),
      onExit: (_) => _setHovered(false),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: widget.onSelected,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 90),
          height: _accountHistoryItemHeight,
          padding: const EdgeInsets.only(left: 10, right: 6),
          color: _hovered ? UiColors.selected : Colors.transparent,
          child: Row(
            children: [
              Avatar(
                label: widget.record.login,
                imageUrl: AppConfigScope.of(
                  context,
                ).resolveAssetUrl(widget.record.avatarUrl),
                defaultAvatarKey: widget.record.defaultAvatarKey,
                size: 22,
              ),
              const SizedBox(width: 9),
              Expanded(
                child: Text(
                  widget.record.login,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: UiColors.text,
                    fontSize: 13,
                    fontWeight: FontWeight.w500,
                    letterSpacing: 0,
                  ),
                ),
              ),
              if (widget.record.remembersPassword) ...[
                const SizedBox(width: 6),
                const Icon(
                  Icons.lock_outline,
                  size: 13,
                  color: UiColors.textMuted,
                ),
              ],
              const SizedBox(width: 6),
              _AccountHistoryDeleteButton(onPressed: widget.onDeleted),
            ],
          ),
        ),
      ),
    );
  }
}

class _AccountHistoryDeleteButton extends StatefulWidget {
  const _AccountHistoryDeleteButton({required this.onPressed});

  final VoidCallback onPressed;

  @override
  State<_AccountHistoryDeleteButton> createState() =>
      _AccountHistoryDeleteButtonState();
}

class _AccountHistoryDeleteButtonState
    extends State<_AccountHistoryDeleteButton> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    return UiTooltip(
      message: '删除账号记录',
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => setState(() => _hovered = true),
        onExit: (_) => setState(() => _hovered = false),
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: widget.onPressed,
          child: SizedBox.square(
            dimension: 26,
            child: Center(
              child: Icon(
                Icons.close,
                size: 15,
                color: _hovered ? UiColors.danger : UiColors.textMuted,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _PasswordVisibilityToggle extends StatelessWidget {
  const _PasswordVisibilityToggle({
    required this.obscure,
    required this.enabled,
    required this.onPressed,
  });

  final bool obscure;
  final bool enabled;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final color = enabled ? UiColors.textSecondary : UiColors.textMuted;
    return UiTooltip(
      message: obscure ? '显示密码' : '隐藏密码',
      child: Semantics(
        button: true,
        enabled: enabled,
        label: obscure ? '显示密码' : '隐藏密码',
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: enabled ? onPressed : null,
          child: SizedBox(
            width: 24,
            height: 24,
            child: Icon(
              obscure
                  ? Icons.visibility_off_outlined
                  : Icons.visibility_outlined,
              size: 17,
              color: color,
            ),
          ),
        ),
      ),
    );
  }
}
