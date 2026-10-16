import { AuthClient, type PasswordResetChallenge, type PasswordResetInspection } from '../auth/auth_client';

export type PasswordResetAuthClientFactory = (baseUrl: string) => AuthClient;
export type PasswordResetAccessTokenProvider = () => Promise<string>;

export class PasswordResetController {
  constructor(opts: {
    apiBaseUrl: string;
    authClientFactory?: PasswordResetAuthClientFactory;
    accessTokenProvider?: PasswordResetAccessTokenProvider | null;
  }) {
    this.apiBaseUrl = opts.apiBaseUrl;
    this._authClientFactory = opts.authClientFactory ?? ((baseUrl) => new AuthClient({ baseUrl }));
    this.accessTokenProvider = opts.accessTokenProvider ?? null;
  }

  readonly apiBaseUrl: string;
  private readonly _authClientFactory: PasswordResetAuthClientFactory;
  readonly accessTokenProvider: PasswordResetAccessTokenProvider | null;
  private _sessionAuthorizedEmail: string | null = null;

  isCurrentSessionAuthorizedFor(email: string | null | undefined): boolean {
    const normalized = email?.trim().toLowerCase() ?? '';
    return normalized.length > 0 && this._sessionAuthorizedEmail === normalized;
  }

  clearCurrentSessionAuthorization(): void {
    this._sessionAuthorizedEmail = null;
  }

  invalidateAuthorizationIfEmailChanged(email: string | null | undefined): void {
    const authorizedEmail = this._sessionAuthorizedEmail;
    if (authorizedEmail == null) return;
    if (authorizedEmail !== (email?.trim().toLowerCase() ?? '')) {
      this.clearCurrentSessionAuthorization();
    }
  }

  start(login: string): Promise<PasswordResetChallenge> {
    return this._withClient((client) => client.startPasswordReset(login));
  }

  inspect(login: string): Promise<PasswordResetInspection> {
    return this._withClient((client) => client.inspectPasswordReset(login));
  }

  resend(challengeId: string): Promise<PasswordResetChallenge> {
    return this._withClient((client) => client.resendPasswordResetCode(challengeId));
  }

  verify(opts: { challengeId: string; code: string }): Promise<string> {
    return this._withClient((client) =>
      client.verifyPasswordResetCode({ challengeId: opts.challengeId, code: opts.code }),
    );
  }

  complete(opts: { resetToken: string; newPassword: string }): Promise<void> {
    return this._withClient((client) =>
      client.completePasswordReset({ resetToken: opts.resetToken, newPassword: opts.newPassword }),
    );
  }

  async claimForCurrentSession(resetToken: string, opts: { email: string }): Promise<void> {
    const tokenProvider = this.accessTokenProvider;
    if (tokenProvider == null) {
      throw new Error('当前页面没有可用的登录会话');
    }
    const accessToken = await tokenProvider();
    await this._withClient((client) => client.claimPasswordResetForSession({ accessToken, resetToken }));
    this._sessionAuthorizedEmail = opts.email.trim().toLowerCase();
  }

  private async _withClient<T>(action: (client: AuthClient) => Promise<T>): Promise<T> {
    const client = this._authClientFactory(this.apiBaseUrl);
    try {
      return await action(client);
    } finally {
      client.close();
    }
  }
}
