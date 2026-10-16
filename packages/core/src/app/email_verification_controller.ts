import {
  AuthClient,
  type EmailVerificationChallenge,
  type EmailVerificationInspection,
} from '../auth/auth_client';

export type EmailVerificationAuthClientFactory = (baseUrl: string) => AuthClient;
export type EmailVerificationAccessTokenProvider = () => Promise<string>;

export class EmailVerificationController {
  constructor(opts: {
    apiBaseUrl: string;
    authClientFactory?: EmailVerificationAuthClientFactory;
    accessTokenProvider?: EmailVerificationAccessTokenProvider | null;
  }) {
    this.apiBaseUrl = opts.apiBaseUrl;
    this._authClientFactory = opts.authClientFactory ?? ((baseUrl) => new AuthClient({ baseUrl }));
    this.accessTokenProvider = opts.accessTokenProvider ?? null;
  }

  readonly apiBaseUrl: string;
  private readonly _authClientFactory: EmailVerificationAuthClientFactory;
  readonly accessTokenProvider: EmailVerificationAccessTokenProvider | null;

  isEmailAvailable(email: string): Promise<boolean> {
    return this._withClient((client) => client.isEmailAvailable(email));
  }

  inspect(email: string): Promise<EmailVerificationInspection> {
    return this._withClient((client) => client.inspectEmailVerification(email));
  }

  start(email: string): Promise<EmailVerificationChallenge> {
    return this._withClient((client) => client.startEmailVerification(email));
  }

  async inspectOrStart(email: string): Promise<EmailVerificationChallenge> {
    const inspection = await this.inspect(email);
    const reusable = inspection.reusableChallenge;
    if (reusable != null) return reusable;
    // Start is idempotent during the server cooldown: it returns the active
    // challenge without sending another email. This also supports servers
    // whose inspection response omits the reusable challenge id.
    return this.start(email);
  }

  async inspectOrStartForCurrentUser(email: string): Promise<EmailVerificationChallenge> {
    const provider = this.accessTokenProvider;
    if (provider == null) return this.inspectOrStart(email);
    const accessToken = await provider();
    const inspection = await this._withClient((client) => client.inspectEmailVerification(email, { accessToken }));
    const reusable = inspection.reusableChallenge;
    if (reusable != null) return reusable;
    return this._withClient((client) => client.startEmailVerification(email, { accessToken }));
  }

  resend(challengeId: string): Promise<EmailVerificationChallenge> {
    return this._withClient((client) => client.resendEmailVerificationCode(challengeId));
  }

  verify(opts: { challengeId: string; code: string }): Promise<string> {
    return this._withClient((client) =>
      client.verifyEmailVerificationCode({ challengeId: opts.challengeId, code: opts.code }),
    );
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
