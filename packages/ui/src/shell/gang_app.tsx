import { useEffect, useRef, type ReactNode } from 'react';

import type { AvailableAppUpdate } from '@gang-chat/core/app/app_update';
import { AuthRestoreStatus, AuthSessionController, type AuthRequest } from '@gang-chat/core/app/auth_session_controller';
import { AuthenticatedAppContext } from '@gang-chat/core/app/authenticated_app_context';
import { EmailVerificationController } from '@gang-chat/core/app/email_verification_controller';
import {
  defaultLanguagePreference,
  LanguagePreferenceStore,
  normalizeLanguagePreference,
} from '@gang-chat/core/app/language_preference';
import {
  LoginAccountHistoryStore,
  rememberLoginAccount,
  updateLoginAccountAvatarMetadata,
} from '@gang-chat/core/app/login_account_history';
import { PasswordResetController } from '@gang-chat/core/app/password_reset_controller';
import { ServerClock } from '@gang-chat/core/app/server_clock';
import type { AuthSession } from '@gang-chat/core/auth/auth_client';
import { TokenStore } from '@gang-chat/core/auth/token_store';
import { AppConfig } from '@gang-chat/core/config/app_config';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import { delay, Timer, unawaited } from '@gang-chat/core/lib/async';
import type { CurrentUser } from '@gang-chat/core/protocol/models';
import { currentGangChatClientUserAgent } from '@gang-chat/core/shell/client_system_info';
import { appWindowBackground, DesktopWindowController } from '@gang-chat/core/shell/desktop_window_controller';
import { LocalLanguagePreferenceStore } from '@gang-chat/core/shell/local_language_preference_store';
import { LocalLoginAccountHistoryStore } from '@gang-chat/core/shell/local_login_account_history_store';

import { HomePage } from '../home/home_page';
import { Center, Column, SizedBox, Text } from '../lib/layout';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { useViewState, ViewState } from '../lib/view_state';
import { AppConfigScope } from '../ui/app_config_scope';
import { AppNotificationHost } from '../ui/feedback';
import { OverlayHost } from '../ui/overlay';
import { argb, kClientFontStack, UiDefaultTextStyle } from '../ui/tokens';
import { AppUpdateGate } from './app_update_gate';
import { LoginPage } from './login_page';

// Dart `const` defaults are canonical instances; keep one of each so prop
// identity stays stable across rebuilds.
let _defaultTokenStore: TokenStore | null = null;
let _defaultConfig: AppConfig | null = null;
const _defaultLanguageStore = new LocalLanguagePreferenceStore();
const _defaultLoginAccountHistoryStore = new LocalLoginAccountHistoryStore();

export interface GangAppProps {
  tokenStore?: TokenStore;
  config?: AppConfig;
  startsAuthenticated?: boolean;
  languageStore?: LanguagePreferenceStore;
  loginAccountHistoryStore?: LoginAccountHistoryStore;
  windowController?: DesktopWindowController;
}

/**
 * Port of `GangApp` (a `MaterialApp` under `AppConfigScope`).
 *
 * The MaterialApp layers map to: `builder` → [AppNotificationHost] around the
 * navigator; the navigator's overlay → [OverlayHost] (dialogs and menus
 * therefore see [AppConfigScope] and the notification host, as in Flutter);
 * `home` → [_AuthGate]. `theme: uiTheme()` is the kit's inline token styles
 * plus the renderer's global CSS, and `scrollBehavior: GangScrollBehavior()`
 * is the global scrollbar CSS.
 */
export function GangApp(props: GangAppProps) {
  const tokenStore = props.tokenStore ?? (_defaultTokenStore ??= new TokenStore());
  const config = props.config ?? (_defaultConfig ??= AppConfig.defaults());
  const startsAuthenticated = props.startsAuthenticated ?? false;
  const languageStore = props.languageStore ?? _defaultLanguageStore;
  const loginAccountHistoryStore = props.loginAccountHistoryStore ?? _defaultLoginAccountHistoryStore;
  // `windowController ?? DesktopWindowController()` in the constructor.
  const ownWindowController = useRef<DesktopWindowController | null>(null);
  if (props.windowController == null && ownWindowController.current == null) {
    ownWindowController.current = new DesktopWindowController();
  }
  const windowController = props.windowController ?? ownWindowController.current!;

  // MaterialApp(title: 'Gang Chat').
  useEffect(() => {
    if (typeof document !== 'undefined') document.title = 'Gang Chat';
  }, []);

  // PORT: the Android-only `TooltipTheme(triggerMode: manual)` wrapper is not
  // needed: the desktop tooltip has no touch long-press trigger.
  return (
    <AppConfigScope config={config}>
      <div style={_materialAppStyle}>
        <AppNotificationHost>
          <OverlayHost>
            <_AuthGate
              tokenStore={tokenStore}
              config={config}
              startsAuthenticated={startsAuthenticated}
              languageStore={languageStore}
              loginAccountHistoryStore={loginAccountHistoryStore}
              windowController={windowController}
            />
          </OverlayHost>
        </AppNotificationHost>
      </div>
    </AppConfigScope>
  );
}

const _materialAppStyle = {
  ...UiDefaultTextStyle,
  display: 'flex',
  flexDirection: 'column' as const,
  flex: '1 1 0',
  minWidth: 0,
  minHeight: 0,
  width: '100%',
  height: '100%',
};

interface _AuthGateProps {
  tokenStore: TokenStore;
  config: AppConfig;
  startsAuthenticated: boolean;
  languageStore: LanguagePreferenceStore;
  loginAccountHistoryStore: LoginAccountHistoryStore;
  windowController: DesktopWindowController;
}

/** Resolves after the next frame is presented (`WidgetsBinding.endOfFrame`). */
function _endOfFrame(): Promise<void> {
  if (typeof requestAnimationFrame !== 'function') return Promise.resolve();
  return new Promise((resolve) => requestAnimationFrame(() => resolve()));
}

class _AuthGateState extends ViewState<_AuthGateProps> {
  // Dart `late final` fields: created on first use, which is initState.
  _auth!: AuthSessionController;
  _passwordReset!: PasswordResetController;
  _emailVerification!: EmailVerificationController;
  readonly _serverClock = new ServerClock();
  // Tracks whether initial auth restore has finished so an authenticated start
  // does not briefly mount LoginPage and shrink the prepared app window.
  _initialRestoreDone = false;
  // The window starts hidden and is revealed once we know which screen to show.
  _windowRevealed = false;
  _exitingSessionForAppExit = false;
  _detectedAppUpdate: AvailableAppUpdate | null = null;
  _authLanguage = defaultLanguagePreference;
  _revealFallbackTimer: Timer | null = null;

  get _window(): DesktopWindowController {
    return this.widget.windowController;
  }

  override initState(): void {
    this._auth = new AuthSessionController({
      tokenStore: this.widget.tokenStore,
      apiBaseUrl: this.widget.config.apiBaseUrl,
      clientUserAgent: currentGangChatClientUserAgent(),
    });
    this._passwordReset = new PasswordResetController({
      apiBaseUrl: this.widget.config.apiBaseUrl,
      accessTokenProvider: () => this._auth.accessToken(),
    });
    this._emailVerification = new EmailVerificationController({
      apiBaseUrl: this.widget.config.apiBaseUrl,
      accessTokenProvider: () => this._auth.accessToken(),
    });

    this._auth.addListener(this._onAuthChanged);
    if (this.widget.startsAuthenticated && this._window.supportsWindowManagement) {
      // Safety net: if auth refresh hangs, reveal the window anyway so the user
      // is not left staring at nothing.
      this._revealFallbackTimer = new Timer(4000, () => {
        unawaited(this._ensureWindowVisible());
      });
    }
    unawaited(this._loadAuthLanguage());
    unawaited(this._restoreSession());
    // PORT: Flutter's initState ran before the first build; ViewState's runs
    // in a layout effect after the first commit, so rebuild (before paint)
    // now that the controllers exist.
    this.setState();
  }

  _onAuthChanged = (): void => {
    if (this._auth.session == null) {
      this._passwordReset.clearCurrentSessionAuthorization();
      unawaited(this._loadAuthLanguage());
    }
    if (this.mounted) this.setState(() => {});
  };

  async _restoreSession(): Promise<void> {
    // AppConfig is the source of truth for server selection. A stored base URL
    // is only a stale copy of older config because there is no server picker.
    const result = await this._auth.restoreSession();
    if (!this.mounted) return;

    if (result.status === AuthRestoreStatus.missingRefreshToken) {
      this.setState(() => {
        this._initialRestoreDone = true;
      });
      if (this.widget.startsAuthenticated) {
        await this._lockLoginAuthWindow({ centerWindow: true });
      }
      await this._ensureWindowVisible();
      return;
    }

    if (result.hasSession) {
      const session = this._auth.session;
      if (session != null) {
        await this._syncAndroidLoginAccountAvatarMetadata(session.user);
        unawaited(this._rememberAuthLanguage(session.user.language));
      }
      if (this.widget.startsAuthenticated) {
        this.setState(() => {
          this._initialRestoreDone = true;
        });
        await _endOfFrame();
        await delay(120);
        await this._ensureWindowVisible();
      } else {
        await this._window.runWithHiddenWindow(async () => {
          if (!this.mounted) return;
          this.setState(() => {
            this._initialRestoreDone = true;
          });
          await this._window.restoreAppWindow();
        });
        await this._ensureWindowVisible();
      }
      return;
    }

    this.setState(() => {
      this._initialRestoreDone = true;
    });
    if (this.widget.startsAuthenticated) {
      await this._window.runWithHiddenWindow(async () => {
        await this._lockLoginAuthWindow({ centerWindow: true });
      });
    }
    await this._ensureWindowVisible();
  }

  async _ensureWindowVisible(): Promise<void> {
    if (!this._window.supportsWindowManagement) return;
    if (this._windowRevealed) return;
    this._windowRevealed = true;
    this._revealFallbackTimer?.cancel();
    this._revealFallbackTimer = null;
    await this._window.showInitialWindow();
  }

  async _loadAuthLanguage(): Promise<void> {
    try {
      const language = await this.widget.languageStore.read();
      if (!this.mounted) return;
      this.setState(() => {
        this._authLanguage = normalizeLanguagePreference(language);
      });
    } catch {
      // A missing/unavailable preference store should keep the default language.
    }
  }

  async _rememberAuthLanguage(language: string): Promise<void> {
    const normalized = normalizeLanguagePreference(language);
    if (this.mounted && this._authLanguage !== normalized) {
      this.setState(() => {
        this._authLanguage = normalized;
      });
    }
    try {
      await this.widget.languageStore.write(normalized);
    } catch {
      // Login should not fail because a non-sensitive local preference failed.
    }
  }

  _logout = async (): Promise<void> => {
    await this._loadAuthLanguage();
    let session: AuthSession | null = null;
    await this._window.runWithHiddenWindow(async () => {
      if (!this.mounted) return;
      session = this._auth.beginLogout();
      await this._lockLoginAuthWindow({ centerWindow: true });
    });
    await this._auth.finishLogout(session);
  };

  _exitSessionForAppExit = async (): Promise<void> => {
    if (this.mounted) {
      this.setState(() => {
        this._exitingSessionForAppExit = true;
      });
    } else {
      this._exitingSessionForAppExit = true;
    }
    this._auth.beginLogout();
  };

  _lockLoginAuthWindow(opts: { centerWindow?: boolean } = {}): Promise<void> {
    const { centerWindow = false } = opts;
    return this._window.lockAuthWindow({ centerWindow, size: this._window.authWidgetSize(false) });
  }

  _submitAuthRequest = async (request: AuthRequest, opts: { rememberPassword: boolean }): Promise<void> => {
    const session = await this._auth.authenticate(request);
    if (!request.registering) {
      await this._rememberLoginAccount(request, {
        rememberPassword: opts.rememberPassword,
        avatarUrl: session.user.avatarUrl,
        defaultAvatarKey: session.user.defaultAvatarKey,
      });
    }
    await this._rememberAuthLanguage(session.user.language);
    if (!this.mounted) return;
    await this._window.runWithHiddenWindow(async () => {
      if (!this.mounted) return;
      await this._auth.acceptSession(session);
      await this._window.restoreAppWindow();
    });
  };

  async _rememberLoginAccount(
    request: AuthRequest,
    opts: { rememberPassword: boolean; avatarUrl: string | null; defaultAvatarKey: string },
  ): Promise<void> {
    try {
      const records = await this.widget.loginAccountHistoryStore.read();
      await this.widget.loginAccountHistoryStore.write(
        rememberLoginAccount({
          records,
          login: request.login,
          password: request.password,
          rememberPassword: opts.rememberPassword,
          avatarUrl: opts.avatarUrl,
          defaultAvatarKey: opts.defaultAvatarKey,
          updateAvatarMetadata: true,
        }),
      );
    } catch {
      // Successful authentication should not be blocked by local history IO.
    }
  }

  async _syncAndroidLoginAccountAvatarMetadata(user: CurrentUser): Promise<void> {
    if (!this.mounted || !HostPlatform.isAndroid) {
      return;
    }
    try {
      const records = await this.widget.loginAccountHistoryStore.read();
      await this.widget.loginAccountHistoryStore.write(
        updateLoginAccountAvatarMetadata({
          records,
          accountAliases: [user.username, ...(user.email != null ? [user.email] : [])],
          avatarUrl: user.avatarUrl,
          defaultAvatarKey: user.defaultAvatarKey,
        }),
      );
    } catch {
      // Remembered-account decoration must never block auth or profile edits.
    }
  }

  override dispose(): void {
    this._auth.removeListener(this._onAuthChanged);
    this._auth.dispose();
    this._serverClock.dispose();
    this._revealFallbackTimer?.cancel();
  }

  build(): ReactNode {
    // The very first render precedes initState (see initState); it is
    // replaced before the browser paints.
    if (this._auth == null) return null;
    const session = this._auth.session;
    if (this._exitingSessionForAppExit) {
      return <_LoadingPage />;
    }
    if (session == null) {
      if (this.widget.startsAuthenticated && !this._initialRestoreDone) {
        return <_LoadingPage />;
      }
      const window = this._window;
      return (
        <LoginPage
          language={this._authLanguage}
          onSubmit={this._submitAuthRequest}
          checkUsernameAvailability={(username) => this._auth.isUsernameAvailable(username)}
          checkEmailAvailability={(email) => this._auth.isEmailAvailable(email)}
          emailVerificationController={this._emailVerification}
          passwordResetController={this._passwordReset}
          sizeForMode={(registering, opts) => window.authWidgetSize(registering, opts)}
          consumeInitialWindowLock={() => window.consumeSkipNextAuthWindowLock()}
          lockAuthWindow={(opts) => window.lockAuthWindow(opts)}
          windowController={window}
          accountHistoryStore={this.widget.loginAccountHistoryStore}
          submitPersistsAccountHistory={HostPlatform.isAndroid}
        />
      );
    }

    const app = new AuthenticatedAppContext({
      session,
      apiBaseUrl: this._auth.apiBaseUrl,
      accessTokenProvider: (opts) => this._auth.accessToken(opts),
      logout: this._logout,
      exitSessionForAppExit: this._exitSessionForAppExit,
      serverClock: this._serverClock,
      emailVerificationController: this._emailVerification,
      passwordResetController: this._passwordReset,
      onCurrentUserUpdated: HostPlatform.isAndroid ? (user) => unawaited(this._syncAndroidLoginAccountAvatarMetadata(user)) : null,
    });

    // PORT: Flutter wrapped this in `SelectionArea(child:
    // SelectionContainer.disabled(...))`, i.e. no app-wide selection; DOM text
    // is unselectable by default and selectable spots opt in themselves.
    return (
      <AppUpdateGate
        releaseBucketUrl={this.widget.config.releaseBucketUrl}
        windowController={this.widget.windowController}
        onUpdateAvailable={(update) => {
          if (!this.mounted) return;
          this.setState(() => {
            this._detectedAppUpdate = update;
          });
        }}
      >
        <HomePage
          app={app}
          languageStore={this.widget.languageStore}
          windowController={this.widget.windowController}
          detectedAppUpdate={this._detectedAppUpdate}
          onDetectedAppUpdateShown={() => {
            if (!this.mounted || this._detectedAppUpdate == null) return;
            this.setState(() => {
              this._detectedAppUpdate = null;
            });
          }}
        />
      </AppUpdateGate>
    );
  }
}

function _AuthGate(props: _AuthGateProps) {
  const state = useViewState(() => new _AuthGateState(), props);
  return <>{state.build()}</>;
}

function _LoadingPage() {
  return (
    <Center style={{ flex: '1 1 0', minWidth: 0, minHeight: 0, backgroundColor: appWindowBackground }}>
      <Column mainAxisSize="min">
        <Text style={{ color: argb(0xffeceff1), fontFamily: kClientFontStack, fontSize: 22, fontWeight: 600, letterSpacing: 0 }}>Gang Chat</Text>
        <SizedBox height={20} />
        <CircularProgressIndicator size={28} strokeWidth={2.5} color={argb(0xff6fcfa6)} />
        <SizedBox height={14} />
        <Text style={{ color: argb(0xffb0b8c0), fontFamily: kClientFontStack, fontSize: 13, fontWeight: 500, letterSpacing: 0 }}>正在加载</Text>
      </Column>
    </Center>
  );
}
