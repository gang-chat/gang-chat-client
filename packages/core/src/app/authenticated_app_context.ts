import type { AuthSession } from '../auth/auth_client';
import { type AccessTokenProvider, type GangApi, GangApiClient } from '../protocol/api_client';
import type { CurrentUser } from '../protocol/models';
import { StickerPackStore } from '../protocol/sticker_pack_store';
import type { EmailVerificationController } from './email_verification_controller';
import type { PasswordResetController } from './password_reset_controller';
import type { ServerClock } from './server_clock';

export type LogoutCallback = () => Promise<void>;
export type ExitSessionCallback = () => Promise<void>;
export type CurrentUserUpdatedCallback = (user: CurrentUser) => void;

export class AuthenticatedAppContext {
  constructor(opts: {
    session: AuthSession;
    apiBaseUrl: string;
    accessTokenProvider: AccessTokenProvider;
    logout: LogoutCallback;
    exitSessionForAppExit: ExitSessionCallback;
    serverClock: ServerClock;
    emailVerificationController?: EmailVerificationController | null;
    passwordResetController?: PasswordResetController | null;
    onCurrentUserUpdated?: CurrentUserUpdatedCallback | null;
    api?: GangApi | null;
    stickerPackStore?: StickerPackStore;
  }) {
    this.session = opts.session;
    this.apiBaseUrl = opts.apiBaseUrl;
    this.accessTokenProvider = opts.accessTokenProvider;
    this.logout = opts.logout;
    this.exitSessionForAppExit = opts.exitSessionForAppExit;
    this.serverClock = opts.serverClock;
    this.emailVerificationController = opts.emailVerificationController ?? null;
    this.passwordResetController = opts.passwordResetController ?? null;
    this.onCurrentUserUpdated = opts.onCurrentUserUpdated ?? null;
    this.api = opts.api ?? null;
    this.stickerPackStore = opts.stickerPackStore ?? new StickerPackStore();
  }

  readonly session: AuthSession;
  readonly apiBaseUrl: string;
  readonly accessTokenProvider: AccessTokenProvider;
  readonly logout: LogoutCallback;
  readonly exitSessionForAppExit: ExitSessionCallback;
  readonly serverClock: ServerClock;
  readonly emailVerificationController: EmailVerificationController | null;
  readonly passwordResetController: PasswordResetController | null;
  readonly onCurrentUserUpdated: CurrentUserUpdatedCallback | null;
  readonly api: GangApi | null;
  readonly stickerPackStore: StickerPackStore;

  get currentUser(): CurrentUser {
    return this.session.user;
  }

  createApiClient(): GangApi {
    const injectedApi = this.api;
    if (injectedApi != null) return injectedApi;
    const serverClock = this.serverClock;
    return new GangApiClient({
      baseUrl: this.apiBaseUrl,
      accessTokenProvider: this.accessTokenProvider,
      onServerTime: (value) => {
        serverClock.updateFromHeader(value);
      },
      onRequestLatency: (value) => {
        serverClock.updateRequestRoundTrip(value);
      },
    });
  }

  hasSameApiSource(other: AuthenticatedAppContext): boolean {
    return this.apiBaseUrl === other.apiBaseUrl && this.api === other.api && this.serverClock === other.serverClock;
  }
}
