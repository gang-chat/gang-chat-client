import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';

import { loginUsernameValidationError } from '@gang-chat/core/app/account_forms';
import {
  AuthSubmitState,
  authRequestFromForm,
  authSubmitFailed,
  authSubmitInvalid,
  authSubmitStarted,
  registerEmailValidationError,
} from '@gang-chat/core/app/auth_form';
import type { AuthRequest } from '@gang-chat/core/app/auth_session_controller';
import type { EmailVerificationController } from '@gang-chat/core/app/email_verification_controller';
import { defaultLanguagePreference } from '@gang-chat/core/app/language_preference';
import {
  deleteLoginAccountRecord,
  lastLoginAccountRecord,
  LoginAccountHistoryStore,
  LoginAccountRecord,
  loginAccountsMatch,
  NoopLoginAccountHistoryStore,
  rememberLoginAccount,
} from '@gang-chat/core/app/login_account_history';
import type { PasswordResetController } from '@gang-chat/core/app/password_reset_controller';
import { AuthException, type EmailVerificationChallenge } from '@gang-chat/core/auth/auth_client';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import { Timer, unawaited } from '@gang-chat/core/lib/async';
import type { DesktopWindowController, Size } from '@gang-chat/core/shell/desktop_window_controller';

import { Center, Column, Expanded, Positioned, Row, SizedBox, Spacer, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { useTapOutside } from '../lib/tap_region';
import { AutofillHints, TextEditingController, TextEditingValue, TextInputType, TextSelection } from '../lib/text_editing';
import { useListenable } from '../lib/use_listenable';
import { useViewState, ViewState } from '../lib/view_state';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar } from '../ui/avatar';
import { Button, ButtonTone } from '../ui/button';
import { UiCheckbox } from '../ui/checkbox';
import { showFloatingErrorNotice } from '../ui/feedback';
import { Icon, Icons, type IconData } from '../ui/icon';
import { Input } from '../ui/input';
import { Segment, SegmentedControl } from '../ui/segmented_control';
import { UiTooltip } from '../ui/tooltip';
import { UiColors, UiRadii, kClientFontStack, withAlpha } from '../ui/tokens';
import { EmailVerificationInputAction, emailVerificationErrorMessage, showEmailVerificationDialog } from './email_verification_flow';
import { showPasswordResetFlow } from './password_reset_flow';
import { AppWindowControls, AppWindowDragRegion } from './window_controls';

export type AuthWindowLock = (
  opts?: { registering?: boolean; moveWindow?: boolean; centerWindow?: boolean; size?: Size | null },
) => Promise<void>;
export type AuthSizeForMode = (registering: boolean, opts?: { showingError?: boolean }) => Size;
export type AuthSubmit = (request: AuthRequest, opts: { rememberPassword: boolean }) => Promise<void>;
export type AuthUsernameAvailabilityCheck = (username: string) => Promise<boolean>;
export type AuthEmailAvailabilityCheck = (email: string) => Promise<boolean>;

enum _AuthMode {
  login = 'login',
  register = 'register',
}

const _authTitleBarTopInset = 14;
const _authTitleBarHeight = 54;
const _authTitleBarGap = 14;
const _authBrandIconSize = 36;
const _authBrandGap = 10;
const _authFieldGap = 3;
const _authModeGap = 10;
const _authActionGap = 6;
const _authErrorHeight = 11;
const _authPasswordErrorGap = 4;
const _authErrorRememberGap = 0;
const _authRememberHeight = 28;
const _authSubmitHeight = 32;
const _accountHistoryItemHeight = 38;
// The segmented control draws its border inside its height, so its outer
// height is the height it is given.
const _authSegmentedControlHeight = 36;
const _authSegmentedControlOuterHeight = _authSegmentedControlHeight;
// [Input] draws its 1px border outside its nominal height, so each field
// occupies its height plus the top and bottom border.
const _authInputHeight = Input.defaultHeight;
const _authInputOuterHeight = _authInputHeight + 2;
const _accountHistoryDropdownTop =
  _authTitleBarHeight + _authTitleBarGap + _authSegmentedControlOuterHeight + _authModeGap + _authInputOuterHeight + _authFieldGap;
const _authRegisterFieldsCenter =
  _authTitleBarTopInset +
  _authTitleBarHeight +
  _authTitleBarGap +
  _authSegmentedControlOuterHeight +
  _authModeGap +
  (_authInputOuterHeight * 4 + _authFieldGap * 3) / 2;
const _authTitleBarStyle: CSSProperties = {
  color: UiColors.text,
  fontFamily: kClientFontStack,
  fontSize: 22,
  fontWeight: 700,
  lineHeight: 1,
  letterSpacing: 0,
};
// PORT: `_androidAuthSystemUiOverlayStyle` (status/navigation bar colours)
// only applied to Android, which is the native client under android/.

// PORT: `Icons.person_add_alt_1_outlined` is missing from the generated
// `ui/icons.ts` (the generator's regex skips `Icons` split across lines in
// login_page.dart); the Material Icons Outlined codepoint is the same as the
// filled `person_add_alt_1`.
const _personAddAlt1Outlined: IconData = { codePoint: Icons.person_add_alt_1.codePoint, fontFamily: 'Material Icons Outlined' };

const _authModeSegments = [
  new Segment({ value: _AuthMode.login, label: '登录', icon: Icons.login_outlined }),
  new Segment({ value: _AuthMode.register, label: '注册', icon: _personAddAlt1Outlined }),
];

// `Image.asset('assets/branding/auth_brand_icon.png')` with its
// resolution-aware variants.
const _authBrandIcon = new URL('../../../../assets/branding/auth_brand_icon.png', import.meta.url).href;
const _authBrandIconSrcSet = [
  `${_authBrandIcon} 1x`,
  `${new URL('../../../../assets/branding/1.25x/auth_brand_icon.png', import.meta.url).href} 1.25x`,
  `${new URL('../../../../assets/branding/1.5x/auth_brand_icon.png', import.meta.url).href} 1.5x`,
  `${new URL('../../../../assets/branding/1.75x/auth_brand_icon.png', import.meta.url).href} 1.75x`,
  `${new URL('../../../../assets/branding/2.0x/auth_brand_icon.png', import.meta.url).href} 2x`,
  `${new URL('../../../../assets/branding/3.0x/auth_brand_icon.png', import.meta.url).href} 3x`,
  `${new URL('../../../../assets/branding/4.0x/auth_brand_icon.png', import.meta.url).href} 4x`,
].join(', ');

const _noopAccountHistoryStore = new NoopLoginAccountHistoryStore();

export interface LoginPageProps {
  sizeForMode: AuthSizeForMode;
  onSubmit: AuthSubmit;
  consumeInitialWindowLock: () => boolean;
  lockAuthWindow: AuthWindowLock;
  checkUsernameAvailability?: AuthUsernameAvailabilityCheck | null;
  checkEmailAvailability?: AuthEmailAvailabilityCheck | null;
  emailVerificationController?: EmailVerificationController | null;
  passwordResetController?: PasswordResetController | null;
  language?: string;
  accountHistoryStore?: LoginAccountHistoryStore;
  submitPersistsAccountHistory?: boolean;
  windowController?: DesktopWindowController | null;
}

class _LoginPageState extends ViewState<LoginPageProps> {
  readonly _login = new TextEditingController();
  readonly _email = new TextEditingController();
  readonly _password = new TextEditingController();
  readonly _confirmPassword = new TextEditingController();

  _registering = false;
  _obscurePassword = true;
  _obscureConfirmPassword = true;
  _submitState = new AuthSubmitState();
  _accountHistory: LoginAccountRecord[] = [];
  _showAccountHistory = false;
  _rememberPassword = false;
  _registerEmailEmpty = true;
  _verifiedEmail: string | null = null;
  _emailVerificationToken: string | null = null;
  _checkingEmailAvailability = false;
  _checkingPasswordReset = false;
  _selectedHistoryLogin: string | null = null;
  _usernameAvailabilityDebounce: Timer | null = null;
  _usernameAvailabilityQuery: string | null = null;
  _checkingUsernameAvailability = false;
  _usernameAvailabilityError: string | null = null;
  private _postFrame: number | null = null;

  get _language(): string {
    return this.widget.language ?? defaultLanguagePreference;
  }

  get _accountHistoryStore(): LoginAccountHistoryStore {
    return this.widget.accountHistoryStore ?? _noopAccountHistoryStore;
  }

  get _showingError(): boolean {
    return this._submitState.error != null && this._submitState.error.length > 0;
  }

  get _emailVerified(): boolean {
    return this._verifiedEmail != null && this._verifiedEmail === this._email.text.trim().toLowerCase();
  }

  get _canShowAccountHistory(): boolean {
    return !this._registering && !this._submitState.busy && this._accountHistory.length > 0;
  }

  override initState(): void {
    unawaited(this._loadAccountHistory());
    if (this.widget.consumeInitialWindowLock()) return;
    // addPostFrameCallback.
    this._postFrame = requestAnimationFrame(() => {
      this._postFrame = null;
      if (this.mounted) {
        unawaited(this._lockAuthSize({ registering: false, moveWindow: false }));
      }
    });
  }

  override didUpdateProps(oldProps: LoginPageProps): void {
    if ((oldProps.accountHistoryStore ?? _noopAccountHistoryStore) !== this._accountHistoryStore) {
      unawaited(this._loadAccountHistory());
    }
  }

  override dispose(): void {
    if (this._postFrame != null) cancelAnimationFrame(this._postFrame);
    this._login.dispose();
    this._email.dispose();
    this._password.dispose();
    this._confirmPassword.dispose();
    this._usernameAvailabilityDebounce?.cancel();
  }

  async _loadAccountHistory(): Promise<void> {
    try {
      const records = await this._accountHistoryStore.read();
      if (!this.mounted) return;
      const lastLogin = lastLoginAccountRecord(records);
      this.setState(() => {
        this._accountHistory = records;
        if (records.length === 0) this._showAccountHistory = false;
        if (!this._registering && this._login.text.length === 0 && this._password.text.length === 0 && lastLogin != null) {
          this._setText(this._login, lastLogin.login);
          this._selectedHistoryLogin = lastLogin.login;
          if (lastLogin.remembersPassword) {
            this._setText(this._password, lastLogin.password!);
            this._rememberPassword = true;
          } else {
            this._password.clear();
            this._rememberPassword = false;
          }
        }
      });
    } catch {
      // Local history is a convenience feature; the auth form stays usable.
    }
  }

  _submit = async (): Promise<void> => {
    if (this._submitState.busy) return;

    const result = authRequestFromForm({
      registering: this._registering,
      username: this._registering ? this._login.text : '',
      login: this._registering ? this._email.text : this._login.text,
      password: this._password.text,
      confirmPassword: this._confirmPassword.text,
      emailVerificationToken: this._emailVerified ? (this._emailVerificationToken ?? '') : '',
      language: this._language,
    });
    const request = result.request;
    if (request == null) {
      await this._showSubmitState(authSubmitInvalid(result.error));
      return;
    }

    this.setState(() => {
      this._submitState = authSubmitStarted();
    });

    if (this._registering) {
      const availabilityError = await this._ensureUsernameAvailable(request.username!);
      if (!this.mounted) return;
      if (availabilityError != null) {
        await this._showSubmitState(authSubmitInvalid(availabilityError));
        return;
      }
    }

    try {
      await this.widget.onSubmit(request, { rememberPassword: !this._registering && this._rememberPassword });
      if (!this.mounted) return;
      if (!request.registering && !(this.widget.submitPersistsAccountHistory ?? false)) {
        await this._rememberSubmittedAccount(request);
      }
    } catch (e) {
      if (!this.mounted) return;
      if (request.registering && e instanceof AuthException && e.code === 'email_verification_required') {
        this._verifiedEmail = null;
        this._emailVerificationToken = null;
      }
      await this._showSubmitState(authSubmitFailed(e, { language: this._language }));
    }
  };

  async _rememberSubmittedAccount(request: AuthRequest): Promise<void> {
    const updated = rememberLoginAccount({
      records: this._accountHistory,
      login: request.login,
      password: request.password,
      rememberPassword: this._rememberPassword,
    });
    this.setState(() => {
      this._accountHistory = updated;
      this._showAccountHistory = false;
    });
    try {
      await this._accountHistoryStore.write(updated);
    } catch {
      // The login already succeeded; failing to persist history is non-fatal.
    }
  }

  _minimizeWindow = (): void => {
    const windowController = this.widget.windowController;
    if (windowController == null) return;
    unawaited(windowController.minimizeWindow());
  };

  _closeWindow = (): void => {
    const windowController = this.widget.windowController;
    if (windowController == null) return;
    unawaited(windowController.closeWindow());
  };

  _setMode(registering: boolean): void {
    if (this._submitState.busy || this._registering === registering) return;
    if (registering) {
      unawaited(this._expandAndShowRegister());
      return;
    }
    this.setState(() => {
      this._registering = false;
      this._resetUsernameAvailability();
      this._submitState = new AuthSubmitState();
      this._showAccountHistory = false;
    });
    unawaited(this._lockAuthSize({ registering: false }));
  }

  async _expandAndShowRegister(): Promise<void> {
    await this._lockAuthSize({ registering: true });
    if (!this.mounted || this._submitState.busy) return;
    this.setState(() => {
      this._registering = true;
      this._submitState = new AuthSubmitState();
      this._showAccountHistory = false;
    });
    this._scheduleUsernameAvailabilityCheck(this._login.text);
  }

  _toggleAccountHistory = (): void => {
    if (!this._canShowAccountHistory) return;
    this.setState(() => {
      this._showAccountHistory = !this._showAccountHistory;
    });
  };

  _closeAccountHistory = (): void => {
    if (!this._showAccountHistory) return;
    this.setState(() => {
      this._showAccountHistory = false;
    });
  };

  _clearLoginInput = (): void => {
    if (this._submitState.busy) return;
    this._login.clear();
    this._password.clear();
    this.setState(() => {
      this._selectedHistoryLogin = null;
      this._rememberPassword = false;
      this._showAccountHistory = false;
      this._submitState = new AuthSubmitState();
    });
  };

  _selectAccountRecord = (record: LoginAccountRecord): void => {
    this._setText(this._login, record.login);
    if (record.remembersPassword) {
      this._setText(this._password, record.password!);
    } else {
      this._password.clear();
    }
    this.setState(() => {
      this._selectedHistoryLogin = record.login;
      this._rememberPassword = record.remembersPassword;
      this._showAccountHistory = false;
      this._submitState = new AuthSubmitState();
    });
  };

  _deleteAccountRecord = (record: LoginAccountRecord): void => {
    const updated = deleteLoginAccountRecord({ records: this._accountHistory, login: record.login });
    this.setState(() => {
      this._accountHistory = updated;
      if (updated.length === 0) this._showAccountHistory = false;
      if (this._selectedHistoryLogin != null && loginAccountsMatch(this._selectedHistoryLogin, record.login)) {
        this._selectedHistoryLogin = null;
      }
    });
    unawaited(this._writeAccountHistory(updated));
  };

  async _writeAccountHistory(records: LoginAccountRecord[]): Promise<void> {
    try {
      await this._accountHistoryStore.write(records);
    } catch {
      // The in-memory UI can still continue if the convenience store fails.
    }
  }

  _handleLoginChanged = (value: string): void => {
    const selected = this._selectedHistoryLogin;
    if (selected == null || loginAccountsMatch(selected, value)) return;
    this.setState(() => {
      this._selectedHistoryLogin = null;
      if (this._rememberPassword) this._rememberPassword = false;
    });
  };

  _setRememberPassword = (rememberPassword: boolean): void => {
    if (this._submitState.busy) return;
    this.setState(() => {
      this._rememberPassword = rememberPassword;
    });
  };

  _setText(controller: TextEditingController, text: string): void {
    controller.value = new TextEditingValue({ text, selection: TextSelection.collapsed({ offset: text.length }) });
  }

  async _showSubmitState(state: AuthSubmitState): Promise<void> {
    this.setState(() => {
      this._submitState = state;
    });
  }

  _showEmailVerification = async (): Promise<void> => {
    if (this._submitState.busy || this._checkingEmailAvailability) return;
    const email = this._email.text.trim();
    const error = registerEmailValidationError(email);
    if (error != null) {
      showFloatingErrorNotice(error);
      return;
    }
    const checker = this.widget.checkEmailAvailability;
    if (checker == null) {
      showFloatingErrorNotice('暂时无法检测邮箱是否重复');
      return;
    }
    this.setState(() => {
      this._checkingEmailAvailability = true;
    });
    let available: boolean | null;
    try {
      available = await checker(email);
    } catch {
      available = null;
    }
    if (!this.mounted) return;
    if (!this._registering || this._email.text.trim() !== email) {
      this.setState(() => {
        this._checkingEmailAvailability = false;
      });
      return;
    }
    if (available == null) {
      this.setState(() => {
        this._checkingEmailAvailability = false;
      });
      showFloatingErrorNotice('暂时无法检测邮箱是否重复');
      return;
    }
    if (!available) {
      this.setState(() => {
        this._checkingEmailAvailability = false;
      });
      showFloatingErrorNotice('该邮箱已被其他用户使用');
      return;
    }
    const controller = this.widget.emailVerificationController;
    if (controller == null) {
      this.setState(() => {
        this._checkingEmailAvailability = false;
      });
      showFloatingErrorNotice('暂时无法使用邮箱验证功能');
      return;
    }
    let challenge: EmailVerificationChallenge | null = null;
    try {
      challenge = await controller.inspectOrStart(email);
      if (!this.mounted) return;
    } catch (error) {
      if (this.mounted) {
        showFloatingErrorNotice(emailVerificationErrorMessage(error));
      }
    }
    if (!this.mounted) return;
    this.setState(() => {
      this._checkingEmailAvailability = false;
    });
    if (challenge == null || !this._registering || this._email.text.trim() !== email) {
      return;
    }
    const verificationToken = await showEmailVerificationDialog({ email, challenge, controller });
    if (verificationToken != null && this.mounted) {
      this.setState(() => {
        this._verifiedEmail = email.toLowerCase();
        this._emailVerificationToken = verificationToken;
      });
    }
  };

  _forgotPassword = async (): Promise<void> => {
    if (this._submitState.busy || this._checkingPasswordReset) return;
    const controller = this.widget.passwordResetController;
    if (controller == null) {
      showFloatingErrorNotice('暂时无法使用忘记密码功能');
      return;
    }
    if (this._login.text.trim().length === 0) {
      showFloatingErrorNotice('请先输入登录用户名或邮箱');
      return;
    }
    this.setState(() => {
      this._checkingPasswordReset = true;
    });
    await showPasswordResetFlow({ login: this._login.text, controller });
    if (this.mounted) {
      this.setState(() => {
        this._checkingPasswordReset = false;
      });
    }
  };

  _handleRegisterEmailChanged = (value: string): void => {
    const normalized = value.trim().toLowerCase();
    const emailEmpty = normalized.length === 0;
    const verificationChanged = this._verifiedEmail != null && this._verifiedEmail !== normalized;
    if (this._registerEmailEmpty === emailEmpty && !verificationChanged) return;
    this.setState(() => {
      this._registerEmailEmpty = emailEmpty;
      if (verificationChanged) {
        this._verifiedEmail = null;
        this._emailVerificationToken = null;
      }
    });
  };

  _resetUsernameAvailability(): void {
    this._usernameAvailabilityDebounce?.cancel();
    this._usernameAvailabilityDebounce = null;
    this._usernameAvailabilityQuery = null;
    this._checkingUsernameAvailability = false;
    this._usernameAvailabilityError = null;
  }

  _handleRegisterUsernameChanged = (value: string): void => {
    const username = value.trim();
    this._usernameAvailabilityDebounce?.cancel();
    const formatError = loginUsernameValidationError(username);
    this.setState(() => {
      this._usernameAvailabilityQuery = null;
      this._checkingUsernameAvailability = false;
      this._usernameAvailabilityError = formatError;
    });
    if (formatError != null || this.widget.checkUsernameAvailability == null) return;
    this._scheduleUsernameAvailabilityCheck(username);
  };

  _scheduleUsernameAvailabilityCheck(value: string): void {
    const username = value.trim();
    if (loginUsernameValidationError(username) != null || this.widget.checkUsernameAvailability == null) {
      return;
    }
    this._usernameAvailabilityDebounce?.cancel();
    if (this.mounted && this._registering) {
      this.setState(() => {
        this._usernameAvailabilityQuery = username;
        this._checkingUsernameAvailability = true;
        this._usernameAvailabilityError = null;
      });
    }
    this._usernameAvailabilityDebounce = new Timer(350, () => unawaited(this._checkUsernameAvailability(username)));
  }

  async _checkUsernameAvailability(username: string): Promise<string | null> {
    const checker = this.widget.checkUsernameAvailability;
    if (checker == null) return null;
    const normalized = username.trim();
    const formatError = loginUsernameValidationError(normalized);
    if (formatError != null) {
      return formatError;
    }
    if (this.mounted) {
      this.setState(() => {
        this._usernameAvailabilityQuery = normalized;
        this._checkingUsernameAvailability = true;
        this._usernameAvailabilityError = null;
      });
    }
    try {
      const available = await checker(normalized);
      const error = available ? null : '该登录用户名已被其他用户使用';
      if (this.mounted && this._registering && this._login.text.trim() === normalized) {
        this.setState(() => {
          this._checkingUsernameAvailability = false;
          this._usernameAvailabilityError = error;
        });
      }
      return error;
    } catch {
      const error = '暂时无法检测登录用户名是否重复';
      if (this.mounted && this._registering && this._login.text.trim() === normalized) {
        this.setState(() => {
          this._checkingUsernameAvailability = false;
          this._usernameAvailabilityError = error;
        });
      }
      return error;
    }
  }

  async _ensureUsernameAvailable(username: string): Promise<string | null> {
    const normalized = username.trim();
    if (this._usernameAvailabilityQuery === normalized && !this._checkingUsernameAvailability) {
      return this._usernameAvailabilityError;
    }
    this._usernameAvailabilityDebounce?.cancel();
    return this._checkUsernameAvailability(normalized);
  }

  _lockAuthSize(
    opts: { registering?: boolean | null; showingError?: boolean; moveWindow?: boolean; centerWindow?: boolean } = {},
  ): Promise<void> {
    const { showingError = false, moveWindow = true, centerWindow = false } = opts;
    const resolvedRegistering = opts.registering ?? this._registering;
    return this.widget.lockAuthWindow({
      registering: resolvedRegistering,
      moveWindow,
      centerWindow,
      size: this.widget.sizeForMode(resolvedRegistering, { showingError }),
    });
  }
}

/** Port of Flutter's `MediaQuery.sizeOf(context)` for the window. */
function _useWindowSize(): { width: number; height: number } {
  const [size, setSize] = useState(() => ({ width: window.innerWidth, height: window.innerHeight }));
  useEffect(() => {
    const update = () => {
      setSize((previous) =>
        previous.width === window.innerWidth && previous.height === window.innerHeight
          ? previous
          : { width: window.innerWidth, height: window.innerHeight },
      );
    };
    update();
    window.addEventListener('resize', update);
    return () => window.removeEventListener('resize', update);
  }, []);
  return size;
}

export function LoginPage(props: LoginPageProps) {
  const state = useViewState(() => new _LoginPageState(), props);
  const windowSize = _useWindowSize();
  const identityRegionRef = useRef<HTMLDivElement | null>(null);
  const dropdownRegionRef = useRef<HTMLDivElement | null>(null);
  // TapRegion(groupId: _accountHistoryTapRegion, onTapOutside: _closeAccountHistory).
  useTapOutside([identityRegionRef, dropdownRegionRef], () => state._closeAccountHistory(), state._showAccountHistory);

  const busy = state._submitState.busy;
  const registering = state._registering;
  const isAndroid = HostPlatform.isAndroid;
  // Android keeps both modes on the registration geometry. The four
  // registration fields are centered by that surface size, and the login
  // fields retain the same starting position when switching modes.
  const size = props.sizeForMode(isAndroid ? true : registering);
  // PORT: Flutter drew these only on Windows (macOS keeps its native traffic
  // lights). The Electron window is frameless on Linux too, so it gets the
  // same self-drawn controls (docs/ui-design-system.md §9).
  const showWindowControls = props.windowController != null && (HostPlatform.isWindows || HostPlatform.isLinux);

  // LayoutBuilder over the SafeArea body: the window size.
  const surfaceWidth = Math.min(size.width, windowSize.width);
  const surfaceHeight = Math.min(size.height, windowSize.height);
  const verticalSlack = windowSize.height - surfaceHeight;
  // Desktop shrinks the window to the card. A browser page is
  // usually much taller, so the card is centred there instead.
  const surfaceTop = isAndroid
    ? Math.min(Math.max(windowSize.height / 2 - _authRegisterFieldsCenter, 0), verticalSlack)
    : HostPlatform.isWeb
      ? verticalSlack / 2
      : 0;

  const titleBar = _buildTitleBar(props.windowController ?? null);

  const primaryIdentityInput = (
    <Input
      controller={state._login}
      height={_authInputHeight}
      enabled={!busy}
      hintText={registering ? '登录用户名' : '登录用户名或邮箱地址'}
      prefixIcon={Icons.person_outline}
      suffix={
        registering ? (
          <_RegisterUsernameValidityIndicator
            controller={state._login}
            checking={state._checkingUsernameAvailability}
            availabilityQuery={state._usernameAvailabilityQuery}
            availabilityError={state._usernameAvailabilityError}
            checksAvailability={props.checkUsernameAvailability != null}
          />
        ) : state._canShowAccountHistory ? (
          <_AccountInputSuffix
            controller={state._login}
            expanded={state._showAccountHistory}
            enabled={!busy}
            onClear={state._clearLoginInput}
            onToggle={state._toggleAccountHistory}
          />
        ) : null
      }
      autofillHints={registering ? [AutofillHints.username] : [AutofillHints.username, AutofillHints.email]}
      keyboardType={TextInputType.text}
      maxLines={1}
      onChanged={registering ? state._handleRegisterUsernameChanged : state._handleLoginChanged}
      onSubmitted={() => void state._submit()}
    />
  );

  const registerEmailInput = (
    <Input
      controller={state._email}
      height={_authInputHeight}
      enabled={!busy}
      hintText="邮箱地址"
      prefixIcon={Icons.alternate_email}
      suffix={
        state._registerEmailEmpty ? null : state._checkingEmailAvailability ? (
          <_InputCheckingIndicator iconKey="auth-email-checking" message="正在检测邮箱是否可用" />
        ) : state._emailVerified ? (
          <_InputValidityIndicator iconKey="auth-email-verified" message="邮箱已验证" valid />
        ) : (
          <EmailVerificationInputAction
            actionKey="auth-email-verification-button"
            label="验证"
            semanticsLabel="验证邮箱"
            enabled={!busy}
            onPressed={() => void state._showEmailVerification()}
          />
        )
      }
      autofillHints={[AutofillHints.email]}
      keyboardType={TextInputType.emailAddress}
      maxLines={1}
      onChanged={state._handleRegisterEmailChanged}
    />
  );

  const errorSlot = (
    <SizedBox data-testid="auth-error-slot" height={_authErrorHeight} style={{ display: 'flex', alignItems: 'center' }}>
      {state._showingError ? (
        <Text
          maxLines={1}
          style={{ color: UiColors.danger, fontFamily: kClientFontStack, fontSize: 10, lineHeight: 1, fontWeight: 500 }}
        >
          {state._submitState.error}
        </Text>
      ) : null}
    </SizedBox>
  );

  const loginTail = () => {
    const errorTop = _authPasswordErrorGap;
    const rememberTop = errorTop + _authErrorHeight + _authErrorRememberGap;
    const height = rememberTop + _authRememberHeight;
    return (
      <SizedBox height={height} style={{ position: 'relative' }}>
        <Positioned left={0} right={0} top={rememberTop}>
          <_RememberPasswordRow
            value={state._rememberPassword}
            enabled={!busy}
            onChanged={state._setRememberPassword}
            onForgotPassword={() => void state._forgotPassword()}
            checkingPasswordReset={state._checkingPasswordReset}
          />
        </Positioned>
        <Positioned left={0} right={0} top={errorTop}>
          {errorSlot}
        </Positioned>
      </SizedBox>
    );
  };

  const registerTail = () => {
    const errorTop = _authPasswordErrorGap;
    const height = errorTop + _authErrorHeight;
    return (
      <SizedBox height={height} style={{ position: 'relative' }}>
        <Positioned left={0} right={0} top={errorTop}>
          {errorSlot}
        </Positioned>
      </SizedBox>
    );
  };

  return (
    <div
      style={{
        flex: '1 1 0',
        minWidth: 0,
        minHeight: 0,
        position: 'relative',
        overflow: 'hidden',
        backgroundColor: isAndroid ? UiColors.surfaceLow : UiColors.background,
      }}
    >
      <div style={{ paddingTop: surfaceTop, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
        <div
          data-testid="auth-surface"
          style={{
            width: surfaceWidth,
            height: surfaceHeight,
            flexShrink: 0,
            position: 'relative',
            backgroundColor: UiColors.surfaceLow,
          }}
        >
          <div
            style={{
              position: 'absolute',
              inset: 0,
              boxSizing: 'border-box',
              paddingLeft: 24,
              paddingTop: _authTitleBarTopInset,
              paddingRight: 24,
              paddingBottom: 7,
            }}
          >
            {/* AutofillGroup: one form so password managers pair the fields.
                Inputs handle Enter themselves, so the form never submits. */}
            <form
              autoComplete="on"
              onSubmit={(event) => event.preventDefault()}
              style={{ position: 'relative', height: '100%', margin: 0 }}
            >
              <div style={{ height: '100%', overflowY: 'auto', overscrollBehavior: 'contain' }}>
                <Column crossAxisAlignment="stretch" style={{ minHeight: '100%' }}>
                  {titleBar}
                  <SizedBox height={_authTitleBarGap} />
                  <SegmentedControl<_AuthMode>
                    expanded
                    height={_authSegmentedControlHeight}
                    value={registering ? _AuthMode.register : _AuthMode.login}
                    segments={_authModeSegments}
                    onChanged={(mode) => {
                      if (state._submitState.busy) return;
                      state._setMode(mode === _AuthMode.register);
                    }}
                  />
                  <SizedBox height={_authModeGap} />
                  <div ref={identityRegionRef}>{primaryIdentityInput}</div>
                  {registering ? (
                    <>
                      <SizedBox height={_authFieldGap} />
                      {registerEmailInput}
                    </>
                  ) : null}
                  <SizedBox height={_authFieldGap} />
                  <Input
                    controller={state._password}
                    height={_authInputHeight}
                    enabled={!busy}
                    hintText="密码"
                    prefixIcon={Icons.lock_outline}
                    autofillHints={[registering ? AutofillHints.newPassword : AutofillHints.password]}
                    obscureText={state._obscurePassword}
                    suffix={
                      <_PasswordVisibilityToggle
                        obscure={state._obscurePassword}
                        enabled={!busy}
                        onPressed={() =>
                          state.setState(() => {
                            state._obscurePassword = !state._obscurePassword;
                          })
                        }
                      />
                    }
                    maxLines={1}
                    onSubmitted={registering ? null : () => void state._submit()}
                  />
                  {!registering ? loginTail() : null}
                  {registering ? (
                    <>
                      <SizedBox height={_authFieldGap} />
                      <Input
                        controller={state._confirmPassword}
                        height={_authInputHeight}
                        enabled={!busy}
                        hintText="确认密码"
                        prefixIcon={Icons.lock_outline}
                        autofillHints={[AutofillHints.newPassword]}
                        obscureText={state._obscureConfirmPassword}
                        suffix={
                          <_PasswordVisibilityToggle
                            obscure={state._obscureConfirmPassword}
                            enabled={!busy}
                            onPressed={() =>
                              state.setState(() => {
                                state._obscureConfirmPassword = !state._obscureConfirmPassword;
                              })
                            }
                          />
                        }
                        maxLines={1}
                        onSubmitted={() => void state._submit()}
                      />
                      {registerTail()}
                    </>
                  ) : null}
                  <SizedBox height={_authActionGap} />
                  <Button
                    width={Infinity}
                    tone={ButtonTone.primary}
                    height={_authSubmitHeight}
                    loading={busy}
                    onPressed={() => void state._submit()}
                  >
                    {registering ? '创建账号' : '登录'}
                  </Button>
                </Column>
              </div>
              {state._showAccountHistory && state._canShowAccountHistory ? (
                <Positioned ref={dropdownRegionRef} top={_accountHistoryDropdownTop} left={0} right={0} style={{ zIndex: 1 }}>
                  <_AccountHistoryDropdown
                    records={state._accountHistory}
                    onSelected={state._selectAccountRecord}
                    onDeleted={state._deleteAccountRecord}
                  />
                </Positioned>
              ) : null}
            </form>
          </div>
          {showWindowControls ? (
            <Positioned top={4} right={0} style={{ zIndex: 2 }}>
              <AppWindowControls onMinimize={state._minimizeWindow} onClose={state._closeWindow} showMaximize={false} />
            </Positioned>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function _buildTitleBar(windowController: DesktopWindowController | null): ReactNode {
  const title = (
    <SizedBox height={_authTitleBarHeight} style={{ cursor: SystemMouseCursors.basic }}>
      <Center style={{ height: '100%' }}>
        <Row mainAxisSize="min">
          <img
            data-testid="auth-brand-icon"
            src={_authBrandIcon}
            srcSet={_authBrandIconSrcSet}
            width={_authBrandIconSize}
            height={_authBrandIconSize}
            alt=""
            draggable={false}
            style={{
              display: 'block',
              flexShrink: 0,
              width: _authBrandIconSize,
              height: _authBrandIconSize,
              objectFit: 'contain',
              // FilterQuality.none.
              imageRendering: 'pixelated',
            }}
          />
          <SizedBox width={_authBrandGap} />
          <Text maxLines={1} style={_authTitleBarStyle}>
            Gang Chat
          </Text>
        </Row>
      </Center>
    </SizedBox>
  );
  if (windowController == null) return title;
  return <AppWindowDragRegion windowController={windowController}>{title}</AppWindowDragRegion>;
}

// ---------------------------------------------------------------------------

/** A tap on an input's suffix keeps the field's focus (Flutter `TextFieldTapRegion`). */
function _keepFieldFocus(event: { preventDefault(): void }): void {
  event.preventDefault();
}

function _RememberPasswordRow({
  value,
  enabled,
  onChanged,
  onForgotPassword,
  checkingPasswordReset,
}: {
  value: boolean;
  enabled: boolean;
  onChanged: (value: boolean) => void;
  onForgotPassword: () => void;
  checkingPasswordReset: boolean;
}) {
  return (
    <SizedBox height={_authRememberHeight}>
      <Row style={{ height: '100%' }}>
        <div
          role="checkbox"
          aria-checked={value}
          aria-disabled={!enabled}
          data-testid="auth-remember-password-hot-zone"
          onClick={(event) => {
            if (event.button !== 0 || !enabled) return;
            onChanged(!value);
          }}
          style={{ display: 'flex', flexDirection: 'row', alignItems: 'center', flexShrink: 0 }}
        >
          <Text style={{ color: UiColors.textSecondary, fontFamily: kClientFontStack, fontSize: 12, fontWeight: 500, letterSpacing: 0 }}>
            记住密码
          </Text>
          <SizedBox width={8} />
          {/* ExcludeSemantics + IgnorePointer: the hot zone owns the tap. */}
          <div aria-hidden="true" style={{ pointerEvents: 'none', display: 'flex' }}>
            <UiCheckbox value={value} enabled={enabled} onChanged={enabled ? () => {} : null} />
          </div>
        </div>
        <Spacer />
        <_AuthTextLink label="忘记密码？" enabled={enabled && !checkingPasswordReset} onPressed={onForgotPassword} />
      </Row>
    </SizedBox>
  );
}

function _RegisterUsernameValidityIndicator({
  controller,
  checking,
  availabilityQuery,
  availabilityError,
  checksAvailability,
}: {
  controller: TextEditingController;
  checking: boolean;
  availabilityQuery: string | null;
  availabilityError: string | null;
  checksAvailability: boolean;
}) {
  // AnimatedBuilder(animation: controller).
  useListenable(controller);
  const username = controller.text.trim();
  if (username.length === 0) return null;
  const formatError = loginUsernameValidationError(username);
  const availabilityApplies = availabilityQuery === username;
  const pending = formatError == null && availabilityApplies && checking;
  const error = formatError ?? (availabilityApplies ? availabilityError : null);
  const availabilityChecked = !checksAvailability || (availabilityApplies && !checking);
  const valid = error == null && availabilityChecked;
  const message = pending ? '正在检测登录用户名是否可用' : (error ?? (valid ? '登录用户名可用' : '等待检测登录用户名是否可用'));
  if (pending || (!valid && error == null)) {
    return <_InputCheckingIndicator iconKey="auth-username-checking" message={message} />;
  }
  return <_InputValidityIndicator iconKey={valid ? 'auth-username-valid' : 'auth-username-invalid'} message={message} valid={valid} />;
}

function _InputValidityIndicator({ iconKey, message, valid }: { iconKey: string; message: string; valid: boolean }) {
  return (
    <UiTooltip message={message}>
      <div data-testid={iconKey} style={{ paddingLeft: 8, paddingRight: 8, display: 'flex', flexShrink: 0 }}>
        <Icon icon={valid ? Icons.check_circle_outline : Icons.error_outline} size={18} color={valid ? UiColors.accent : UiColors.danger} />
      </div>
    </UiTooltip>
  );
}

function _InputCheckingIndicator({ iconKey, message }: { iconKey: string; message: string }) {
  return (
    <UiTooltip message={message}>
      <div data-testid={iconKey} style={{ paddingLeft: 8, paddingRight: 8, display: 'flex', flexShrink: 0 }}>
        <Icon icon={Icons.hourglass_empty_outlined} size={18} color={UiColors.textSecondary} />
      </div>
    </UiTooltip>
  );
}

function _AuthTextLink({ label, enabled, onPressed }: { label: string; enabled: boolean; onPressed: () => void }) {
  const [hovered, setHovered] = useState(false);
  const interactive = enabled;
  const color = !interactive ? UiColors.textMuted : hovered ? UiColors.controlAccent : UiColors.accent;
  return (
    <div
      role="button"
      aria-label={label}
      aria-disabled={!interactive}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={(event) => {
        if (event.button !== 0 || !interactive) return;
        onPressed();
      }}
      style={{
        cursor: interactive ? SystemMouseCursors.click : SystemMouseCursors.basic,
        paddingLeft: 2,
        paddingRight: 2,
        paddingTop: 4,
        paddingBottom: 4,
        flexShrink: 0,
      }}
    >
      <Text style={{ color, fontFamily: kClientFontStack, fontSize: 12, fontWeight: 500, letterSpacing: 0, whiteSpace: 'nowrap' }}>{label}</Text>
    </div>
  );
}

function _AccountInputSuffix({
  controller,
  expanded,
  enabled,
  onClear,
  onToggle,
}: {
  controller: TextEditingController;
  expanded: boolean;
  enabled: boolean;
  onClear: () => void;
  onToggle: () => void;
}) {
  // `controller.addListener(_handleTextChanged)` → rebuild on every edit.
  useListenable(controller);
  const canClear = enabled && controller.text.trim().length > 0;
  return (
    <Row mainAxisSize="min">
      {canClear ? <_AccountInputClearButton enabled={enabled} onPressed={onClear} /> : null}
      <_AccountHistoryToggle expanded={expanded} onPressed={onToggle} />
    </Row>
  );
}

function _AccountInputClearButton({ enabled, onPressed }: { enabled: boolean; onPressed: () => void }) {
  return (
    <UiTooltip message="清除账号">
      <div
        role="button"
        aria-label="清除账号"
        aria-disabled={!enabled}
        onMouseDown={_keepFieldFocus}
        onClick={(event) => {
          if (event.button !== 0 || !enabled) return;
          onPressed();
        }}
        style={{
          width: 24,
          height: 24,
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
        }}
      >
        <Icon icon={Icons.close} size={15} color={UiColors.textMuted} />
      </div>
    </UiTooltip>
  );
}

function _AccountHistoryToggle({ expanded, onPressed }: { expanded: boolean; onPressed: () => void }) {
  return (
    <UiTooltip message={expanded ? '收起账号记录' : '展开账号记录'}>
      <div
        role="button"
        aria-label={expanded ? '收起账号记录' : '展开账号记录'}
        onMouseDown={_keepFieldFocus}
        onClick={(event) => {
          if (event.button !== 0) return;
          onPressed();
        }}
        style={{
          width: 24,
          height: 24,
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: SystemMouseCursors.click,
        }}
      >
        <Icon icon={expanded ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down} size={18} color={UiColors.textMuted} />
      </div>
    </UiTooltip>
  );
}

function _AccountHistoryDropdown({
  records,
  onSelected,
  onDeleted,
}: {
  records: readonly LoginAccountRecord[];
  onSelected: (record: LoginAccountRecord) => void;
  onDeleted: (record: LoginAccountRecord) => void;
}) {
  const rowCount = records.length > 4 ? 4 : records.length;
  const scrollable = records.length > rowCount;
  return (
    <div
      data-testid="auth-account-history-dropdown"
      style={{
        height: rowCount * _accountHistoryItemHeight,
        boxSizing: 'border-box',
        backgroundColor: UiColors.surface,
        borderRadius: UiRadii.md,
        // The DecoratedBox border paints behind the list without insetting it,
        // like an inset shadow.
        boxShadow: `0 10px 18px 0 ${withAlpha(UiColors.black, 0.26)}, inset 0 0 0 1px ${UiColors.border}`,
      }}
    >
      <div
        style={{
          height: '100%',
          overflowY: scrollable ? 'auto' : 'hidden',
          overscrollBehavior: 'contain',
          // PORT: Flutter padded the list by 7px for its overlay scrollbar;
          // the renderer's classic scrollbar reserves its own gutter.
          paddingRight: 0,
          boxSizing: 'border-box',
        }}
      >
        {records.map((record) => (
          <_AccountHistoryItem
            key={record.login}
            record={record}
            onSelected={() => onSelected(record)}
            onDeleted={() => onDeleted(record)}
          />
        ))}
      </div>
    </div>
  );
}

function _AccountHistoryItem({
  record,
  onSelected,
  onDeleted,
}: {
  record: LoginAccountRecord;
  onSelected: () => void;
  onDeleted: () => void;
}) {
  const [hovered, setHoveredState] = useState(false);
  const config = useAppConfig();
  const setHovered = (value: boolean) => {
    if (hovered === value) return;
    setHoveredState(value);
  };
  return (
    <div
      role="button"
      aria-label={record.login}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={(event) => {
        if (event.button !== 0) return;
        onSelected();
      }}
      style={{
        height: _accountHistoryItemHeight,
        boxSizing: 'border-box',
        paddingLeft: 10,
        paddingRight: 6,
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        cursor: SystemMouseCursors.click,
        backgroundColor: hovered ? UiColors.selected : UiColors.transparent,
        transition: 'background-color 90ms linear',
      }}
    >
      <Avatar
        label={record.login}
        imageUrl={config.resolveAssetUrl(record.avatarUrl)}
        defaultAvatarKey={record.defaultAvatarKey}
        size={22}
      />
      <SizedBox width={9} />
      <Expanded>
        <Text maxLines={1} style={{ color: UiColors.text, fontFamily: kClientFontStack, fontSize: 13, fontWeight: 500, letterSpacing: 0 }}>
          {record.login}
        </Text>
      </Expanded>
      {record.remembersPassword ? (
        <>
          <SizedBox width={6} />
          <Icon icon={Icons.lock_outline} size={13} color={UiColors.textMuted} />
        </>
      ) : null}
      <SizedBox width={6} />
      <_AccountHistoryDeleteButton onPressed={onDeleted} />
    </div>
  );
}

function _AccountHistoryDeleteButton({ onPressed }: { onPressed: () => void }) {
  const [hovered, setHovered] = useState(false);
  return (
    <UiTooltip message="删除账号记录">
      <div
        role="button"
        aria-label="删除账号记录"
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
        onClick={(event) => {
          if (event.button !== 0) return;
          // The inner GestureDetector wins the tap over the row.
          event.stopPropagation();
          onPressed();
        }}
        style={{
          width: 26,
          height: 26,
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: SystemMouseCursors.click,
        }}
      >
        <Icon icon={Icons.close} size={15} color={hovered ? UiColors.danger : UiColors.textMuted} />
      </div>
    </UiTooltip>
  );
}

function _PasswordVisibilityToggle({ obscure, enabled, onPressed }: { obscure: boolean; enabled: boolean; onPressed: () => void }) {
  const color = enabled ? UiColors.textSecondary : UiColors.textMuted;
  return (
    <UiTooltip message={obscure ? '显示密码' : '隐藏密码'}>
      <div
        role="button"
        aria-label={obscure ? '显示密码' : '隐藏密码'}
        aria-disabled={!enabled}
        onMouseDown={_keepFieldFocus}
        onClick={(event) => {
          if (event.button !== 0 || !enabled) return;
          onPressed();
        }}
        style={{ width: 24, height: 24, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
      >
        <Icon icon={obscure ? Icons.visibility_off_outlined : Icons.visibility_outlined} size={17} color={color} />
      </div>
    </UiTooltip>
  );
}
