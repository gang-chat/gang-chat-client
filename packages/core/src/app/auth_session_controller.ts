import { Timer, unawaited } from '../lib/async';
import { AuthClient, AuthException, type AuthSession } from '../auth/auth_client';
import type { TokenStore } from '../auth/token_store';

export type AuthClientFactory = (baseUrl: string) => AuthClient;
export type AuthSessionListener = () => void;

export class AuthRequest {
  private constructor(opts: {
    registering: boolean;
    username: string | null;
    emailVerificationToken: string | null;
    login: string;
    password: string;
  }) {
    this.registering = opts.registering;
    this.username = opts.username;
    this.emailVerificationToken = opts.emailVerificationToken;
    this.login = opts.login;
    this.password = opts.password;
  }

  static login(opts: { login: string; password: string }): AuthRequest {
    return new AuthRequest({
      registering: false,
      username: null,
      emailVerificationToken: null,
      login: opts.login,
      password: opts.password,
    });
  }

  static register(opts: {
    username: string;
    login: string;
    password: string;
    emailVerificationToken: string;
  }): AuthRequest {
    return new AuthRequest({
      registering: true,
      username: opts.username,
      emailVerificationToken: opts.emailVerificationToken,
      login: opts.login,
      password: opts.password,
    });
  }

  readonly registering: boolean;
  readonly username: string | null;
  readonly emailVerificationToken: string | null;
  readonly login: string;
  readonly password: string;
}

export enum AuthRestoreStatus {
  missingRefreshToken = 'missingRefreshToken',
  restored = 'restored',
  refreshFailed = 'refreshFailed',
}

export class AuthRestoreResult {
  private constructor(
    readonly status: AuthRestoreStatus,
    opts: { error?: unknown } = {},
  ) {
    this.error = opts.error ?? null;
  }

  static missingRefreshToken(): AuthRestoreResult {
    return new AuthRestoreResult(AuthRestoreStatus.missingRefreshToken);
  }

  static restored(): AuthRestoreResult {
    return new AuthRestoreResult(AuthRestoreStatus.restored);
  }

  static refreshFailed(error: unknown): AuthRestoreResult {
    return new AuthRestoreResult(AuthRestoreStatus.refreshFailed, { error });
  }

  readonly error: unknown;

  get hasSession(): boolean {
    return this.status === AuthRestoreStatus.restored;
  }
}

export class AuthSessionController {
  constructor(opts: {
    tokenStore: TokenStore;
    apiBaseUrl: string;
    clientUserAgent?: string | null;
    authClientFactory?: AuthClientFactory;
  }) {
    this._tokenStore = opts.tokenStore;
    this.apiBaseUrl = opts.apiBaseUrl;
    const clientUserAgent = opts.clientUserAgent ?? null;
    this._authClientFactory =
      opts.authClientFactory ?? ((baseUrl) => new AuthClient({ baseUrl, userAgent: clientUserAgent }));
  }

  private readonly _tokenStore: TokenStore;
  readonly apiBaseUrl: string;
  private readonly _authClientFactory: AuthClientFactory;

  private _session: AuthSession | null = null;
  private _refreshTimer: Timer | null = null;
  private _refreshInFlight: Promise<string> | null = null;
  private readonly _listeners: AuthSessionListener[] = [];

  /** Milliseconds. */
  private static readonly _refreshRetryDelay = 30_000;

  get session(): AuthSession | null {
    return this._session;
  }

  addListener(listener: AuthSessionListener): void {
    this._listeners.push(listener);
  }

  removeListener(listener: AuthSessionListener): void {
    const index = this._listeners.indexOf(listener);
    if (index >= 0) this._listeners.splice(index, 1);
  }

  async restoreSession(): Promise<AuthRestoreResult> {
    let refreshToken: string | null;
    try {
      refreshToken = await this._tokenStore.readRefreshToken();
    } catch (e) {
      this._clearSession();
      return AuthRestoreResult.refreshFailed(e);
    }
    if (refreshToken == null || refreshToken.length === 0) {
      this._clearSession();
      return AuthRestoreResult.missingRefreshToken();
    }

    const client = this._authClientFactory(this.apiBaseUrl);
    try {
      const session = await client.refresh(refreshToken);
      this._setSession(session);
      this._scheduleTokenRefresh(session);
      await this._writeRefreshTokenBestEffort(session.refreshToken);
      return AuthRestoreResult.restored();
    } catch (e) {
      if (this._isTerminalRefreshFailure(e)) {
        await this._tokenStore.clearRefreshToken();
      }
      this._clearSession();
      return AuthRestoreResult.refreshFailed(e);
    } finally {
      client.close();
    }
  }

  async authenticate(request: AuthRequest): Promise<AuthSession> {
    const client = this._authClientFactory(this.apiBaseUrl);
    try {
      if (request.registering) {
        return await client.register({
          username: request.username!,
          email: request.login,
          password: request.password,
          emailVerificationToken: request.emailVerificationToken!,
        });
      }
      return await client.login({ login: request.login, password: request.password });
    } finally {
      client.close();
    }
  }

  async isUsernameAvailable(username: string): Promise<boolean> {
    const client = this._authClientFactory(this.apiBaseUrl);
    try {
      return await client.isUsernameAvailable(username);
    } finally {
      client.close();
    }
  }

  async isEmailAvailable(email: string): Promise<boolean> {
    const client = this._authClientFactory(this.apiBaseUrl);
    try {
      return await client.isEmailAvailable(email);
    } finally {
      client.close();
    }
  }

  async acceptSession(session: AuthSession): Promise<void> {
    await this._tokenStore.writeRefreshToken(session.refreshToken);
    await this._tokenStore.writeApiBaseUrl(this.apiBaseUrl);
    this._setSession(session);
    this._scheduleTokenRefresh(session);
  }

  beginLogout(): AuthSession | null {
    this._refreshTimer?.cancel();
    this._refreshTimer = null;
    this._refreshInFlight = null;
    const session = this._session;
    this._clearSession();
    return session;
  }

  async finishLogout(session: AuthSession | null): Promise<void> {
    await this._tokenStore.clearRefreshToken();
    if (session == null) return;

    const client = this._authClientFactory(this.apiBaseUrl);
    try {
      await client.logout({ accessToken: session.accessToken, refreshToken: session.refreshToken });
    } catch {
      // Local logout should win even if the server revoke request fails.
    } finally {
      client.close();
    }
  }

  accessToken(opts: { forceRefresh?: boolean } = {}): Promise<string> {
    const { forceRefresh = false } = opts;
    const activeRefresh = this._refreshInFlight;
    if (activeRefresh != null) return activeRefresh;

    const session = this._session;
    if (session == null) return Promise.reject(new Error('尚未登录'));
    if (forceRefresh) return this._refreshAccessToken();
    if (!session.isAccessTokenExpiringSoon()) {
      return Promise.resolve(session.accessToken);
    }
    return this._refreshAccessToken();
  }

  private _refreshAccessToken(): Promise<string> {
    const activeRefresh = this._refreshInFlight;
    if (activeRefresh != null) return activeRefresh;

    const session = this._session;
    if (session == null) return Promise.reject(new Error('尚未登录'));

    const refresh = this._refreshSession(session.refreshToken);
    this._refreshInFlight = refresh;
    unawaited(
      refresh.then(
        () => {
          this._refreshInFlight = null;
        },
        () => {
          this._refreshInFlight = null;
        },
      ),
    );
    return refresh;
  }

  private async _refreshSession(refreshToken: string): Promise<string> {
    const client = this._authClientFactory(this.apiBaseUrl);
    try {
      const session = await client.refresh(refreshToken);
      this._setSession(session);
      this._scheduleTokenRefresh(session);
      await this._writeRefreshTokenBestEffort(session.refreshToken);
      return session.accessToken;
    } catch (e) {
      if (this._isTerminalRefreshFailure(e)) {
        await this._tokenStore.clearRefreshToken();
        this._clearSession();
      } else {
        this._scheduleRefreshRetry(refreshToken);
      }
      throw e;
    } finally {
      client.close();
    }
  }

  private _scheduleTokenRefresh(session: AuthSession): void {
    this._refreshTimer?.cancel();
    const delay = session.accessTokenExpiresAt.getTime() - 60_000 - Date.now();
    this._refreshTimer = new Timer(delay < 0 ? 0 : delay, () => {
      this._refreshAccessToken().catch(() => '');
    });
  }

  private _scheduleRefreshRetry(refreshToken: string): void {
    const session = this._session;
    if (session == null || session.refreshToken !== refreshToken) return;
    this._refreshTimer?.cancel();
    this._refreshTimer = new Timer(AuthSessionController._refreshRetryDelay, () => {
      this._refreshAccessToken().catch(() => '');
    });
  }

  private _isTerminalRefreshFailure(error: unknown): boolean {
    return error instanceof AuthException && error.isUnauthorized;
  }

  private async _writeRefreshTokenBestEffort(refreshToken: string): Promise<void> {
    try {
      await this._tokenStore.writeRefreshToken(refreshToken);
    } catch {
      // Keep the in-memory rotated token. A later refresh can persist it again.
    }
  }

  private _setSession(session: AuthSession): void {
    this._session = session;
    this._notifyListeners();
  }

  private _clearSession(): void {
    if (this._session == null) return;
    this._session = null;
    this._notifyListeners();
  }

  private _notifyListeners(): void {
    for (const listener of [...this._listeners]) {
      listener();
    }
  }

  dispose(): void {
    this._refreshTimer?.cancel();
    this._listeners.length = 0;
  }
}
