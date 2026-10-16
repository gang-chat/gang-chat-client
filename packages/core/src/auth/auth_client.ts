import { delay } from '../lib/async';
import { asBool, asDate, asString, isJsonMap, mapList, requireMap, requireString, type JsonMap } from '../lib/json';
import { HttpClientHelpers, type HttpClient, type HttpHeaders, type HttpResponse } from '../platform/http';
import { platform } from '../platform/platform';
import { localizedServerErrorMessage } from '../protocol/error_messages';
import { CurrentUser, UserSession } from '../protocol/models';
import { decodeJsonBody, encodeJsonBody, jsonAcceptHeader, jsonUtf8ContentType } from '../protocol/utf8_json';
import { createEnvironmentProxyAuthHttpClient, isTlsHandshakeFailure, type AuthHttpClientFactory } from './auth_transport';

export class AuthClient {
  readonly baseUrl: string;
  readonly userAgent: string | null;
  private readonly _httpClient: HttpClient;
  private readonly _http: HttpClientHelpers;
  private readonly _environmentProxyClientFactory: AuthHttpClientFactory;
  /** Delays (ms) between TLS handshake retries. */
  readonly handshakeRetryDelays: readonly number[];
  private _environmentProxyClient: HttpClient | null = null;

  constructor(opts: {
    baseUrl: string;
    userAgent?: string | null;
    httpClient?: HttpClient | null;
    environmentProxyClientFactory?: AuthHttpClientFactory | null;
    handshakeRetryDelays?: readonly number[];
  }) {
    this.baseUrl = opts.baseUrl;
    this.userAgent = opts.userAgent ?? null;
    // PORT: Dart created a private `http.Client()`; the port shares the host's
    // `platform().http` transport (wrapped without `close`, so `close()` never
    // shuts the app-wide transport down) unless one is injected.
    this._httpClient = opts.httpClient ?? _sharedPlatformHttpClient();
    this._http = new HttpClientHelpers(this._httpClient);
    this._environmentProxyClientFactory = opts.environmentProxyClientFactory ?? createEnvironmentProxyAuthHttpClient;
    this.handshakeRetryDelays = opts.handshakeRetryDelays ?? [250, 750];
  }

  register(opts: {
    username: string;
    email: string;
    password: string;
    emailVerificationToken: string;
  }): Promise<AuthSession> {
    const { username, email, password, emailVerificationToken } = opts;
    return this._postAuth('/auth/register', {
      username: username,
      email: email,
      password: password,
      email_verification_token: emailVerificationToken,
    });
  }

  login(opts: { login: string; password: string }): Promise<AuthSession> {
    const { login, password } = opts;
    return this._postAuth('/auth/login', { login: login, password: password });
  }

  async isUsernameAvailable(username: string): Promise<boolean> {
    const uri = _replaceQuery(this._uri('/auth/username-availability'), { username: username.trim() });
    const response = await this._sendWithHandshakeRetry((client) => client.get(uri, { headers: this._headers() }));
    this._throwIfFailed(response);
    const body = _requireJsonMap(decodeJsonBody(response));
    return asBool(body['available']) ?? false;
  }

  async isEmailAvailable(email: string): Promise<boolean> {
    const uri = _replaceQuery(this._uri('/auth/email-availability'), { email: email.trim() });
    const response = await this._sendWithHandshakeRetry((client) => client.get(uri, { headers: this._headers() }));
    this._throwIfFailed(response);
    const body = _requireJsonMap(decodeJsonBody(response));
    return asBool(body['available']) ?? false;
  }

  async inspectEmailVerification(
    email: string,
    opts: { accessToken?: string | null } = {},
  ): Promise<EmailVerificationInspection> {
    const { accessToken } = opts;
    const response = await this._postAuthWithHandshakeRetry(
      accessToken == null ? '/auth/email-verification/inspect' : '/users/me/email-verification/inspect',
      { email: email.trim() },
      { accessToken: accessToken },
    );
    this._throwIfFailed(response);
    return EmailVerificationInspection.fromJson(_requireJsonMap(decodeJsonBody(response)));
  }

  async startEmailVerification(
    email: string,
    opts: { accessToken?: string | null } = {},
  ): Promise<EmailVerificationChallenge> {
    const { accessToken } = opts;
    const response = await this._postAuthWithHandshakeRetry(
      accessToken == null ? '/auth/email-verification/start' : '/users/me/email-verification/start',
      { email: email.trim() },
      { accessToken: accessToken },
    );
    this._throwIfFailed(response);
    return EmailVerificationChallenge.fromJson(_requireJsonMap(decodeJsonBody(response)));
  }

  async resendEmailVerificationCode(challengeId: string): Promise<EmailVerificationChallenge> {
    const response = await this._postAuthWithHandshakeRetry('/auth/email-verification/resend', {
      challenge_id: challengeId,
    });
    this._throwIfFailed(response);
    return EmailVerificationChallenge.fromJson(_requireJsonMap(decodeJsonBody(response)));
  }

  async verifyEmailVerificationCode(opts: { challengeId: string; code: string }): Promise<string> {
    const { challengeId, code } = opts;
    const response = await this._postAuthWithHandshakeRetry('/auth/email-verification/verify', {
      challenge_id: challengeId,
      code: code.trim(),
    });
    this._throwIfFailed(response);
    const body = _requireJsonMap(decodeJsonBody(response));
    return requireString(body, 'verification_token');
  }

  refresh(refreshToken: string): Promise<AuthSession> {
    return this._postAuth('/auth/refresh', { refresh_token: refreshToken });
  }

  async logout(opts: { accessToken?: string | null; refreshToken?: string | null } = {}): Promise<void> {
    const { accessToken, refreshToken } = opts;
    const response = await this._http.post(this._uri('/auth/logout'), {
      headers: this._headers({ accessToken: accessToken }),
      // `?? null` keeps the explicit `"refresh_token": null` Dart sends.
      body: encodeJsonBody({ refresh_token: refreshToken ?? null }),
    });
    this._throwIfFailed(response);
  }

  async me(accessToken: string): Promise<CurrentUser> {
    const response = await this._http.get(this._uri('/me'), {
      headers: this._headers({ accessToken: accessToken }),
    });
    this._throwIfFailed(response);
    return CurrentUser.fromJson(_requireJsonMap(decodeJsonBody(response)));
  }

  async listSessions(accessToken: string): Promise<UserSession[]> {
    const response = await this._http.get(this._uri('/auth/sessions'), {
      headers: this._headers({ accessToken: accessToken }),
    });
    this._throwIfFailed(response);
    const decoded = decodeJsonBody(response);
    // Dart: `decodeJsonBody(response) as List<Object?>`.
    if (!Array.isArray(decoded)) throw new TypeError('Expected a JSON list response body');
    return mapList(decoded, (json) => UserSession.fromJson(json));
  }

  async revokeSession(opts: { accessToken: string; sessionId: string }): Promise<void> {
    const { accessToken, sessionId } = opts;
    const response = await this._http.delete(this._uri(`/auth/sessions/${sessionId}`), {
      headers: this._headers({ accessToken: accessToken }),
    });
    this._throwIfFailed(response);
  }

  async changePassword(opts: {
    accessToken: string;
    currentPassword: string;
    newPassword: string;
    revokeOtherSessions?: boolean;
  }): Promise<void> {
    const { accessToken, currentPassword, newPassword, revokeOtherSessions = true } = opts;
    const response = await this._http.post(this._uri('/auth/password'), {
      headers: this._headers({ accessToken: accessToken }),
      body: encodeJsonBody({
        current_password: currentPassword,
        new_password: newPassword,
        revoke_other_sessions: revokeOtherSessions,
      }),
    });
    this._throwIfFailed(response);
  }

  async startPasswordReset(login: string): Promise<PasswordResetChallenge> {
    const response = await this._postAuthWithHandshakeRetry('/auth/password-reset/start', { login: login.trim() });
    this._throwIfFailed(response);
    return PasswordResetChallenge.fromJson(_requireJsonMap(decodeJsonBody(response)));
  }

  async inspectPasswordReset(login: string): Promise<PasswordResetInspection> {
    const response = await this._postAuthWithHandshakeRetry('/auth/password-reset/inspect', { login: login.trim() });
    this._throwIfFailed(response);
    return PasswordResetInspection.fromJson(_requireJsonMap(decodeJsonBody(response)));
  }

  async resendPasswordResetCode(challengeId: string): Promise<PasswordResetChallenge> {
    const response = await this._postAuthWithHandshakeRetry('/auth/password-reset/resend', {
      challenge_id: challengeId,
    });
    this._throwIfFailed(response);
    return PasswordResetChallenge.fromJson(_requireJsonMap(decodeJsonBody(response)));
  }

  async verifyPasswordResetCode(opts: { challengeId: string; code: string }): Promise<string> {
    const { challengeId, code } = opts;
    const response = await this._postAuthWithHandshakeRetry('/auth/password-reset/verify', {
      challenge_id: challengeId,
      code: code.trim(),
    });
    this._throwIfFailed(response);
    const body = _requireJsonMap(decodeJsonBody(response));
    return requireString(body, 'reset_token');
  }

  async completePasswordReset(opts: { resetToken: string; newPassword: string }): Promise<void> {
    const { resetToken, newPassword } = opts;
    const response = await this._postAuthWithHandshakeRetry('/auth/password-reset/complete', {
      reset_token: resetToken,
      new_password: newPassword,
    });
    this._throwIfFailed(response);
  }

  async claimPasswordResetForSession(opts: { accessToken: string; resetToken: string }): Promise<void> {
    const { accessToken, resetToken } = opts;
    const response = await this._http.post(this._uri('/auth/password-reset/claim'), {
      headers: this._headers({ accessToken: accessToken }),
      body: encodeJsonBody({ reset_token: resetToken }),
    });
    this._throwIfFailed(response);
  }

  private async _postAuth(path: string, body: JsonMap): Promise<AuthSession> {
    const response = await this._postAuthWithHandshakeRetry(path, body);
    this._throwIfFailed(response);
    return AuthSession.fromJson(_requireJsonMap(decodeJsonBody(response)));
  }

  private _postAuthWithHandshakeRetry(
    path: string,
    body: JsonMap,
    opts: { accessToken?: string | null } = {},
  ): Promise<HttpResponse> {
    const { accessToken } = opts;
    return this._sendWithHandshakeRetry((client) =>
      client.post(this._uri(path), {
        headers: this._headers({ accessToken: accessToken }),
        body: encodeJsonBody(body),
      }),
    );
  }

  private async _sendWithHandshakeRetry(
    send: (client: HttpClientHelpers) => Promise<HttpResponse>,
  ): Promise<HttpResponse> {
    let client = this._http;
    for (let attempt = 0; ; attempt += 1) {
      try {
        return await send(client);
      } catch (error) {
        if (!isTlsHandshakeFailure(error) || attempt >= this.handshakeRetryDelays.length) {
          throw error;
        }

        await delay(this.handshakeRetryDelays[attempt]!);
        if (attempt === this.handshakeRetryDelays.length - 1) {
          this._environmentProxyClient ??= this._environmentProxyClientFactory();
          client = new HttpClientHelpers(this._environmentProxyClient);
        }
      }
    }
  }

  private _throwIfFailed(response: HttpResponse): void {
    if (response.statusCode >= 200 && response.statusCode < 300) return;
    throw AuthException.fromResponse(response);
  }

  private _uri(path: string): string {
    const normalizedBase = this.baseUrl.endsWith('/') ? this.baseUrl.substring(0, this.baseUrl.length - 1) : this.baseUrl;
    return new URL(`${normalizedBase}${path}`).toString();
  }

  private _headers(opts: { accessToken?: string | null } = {}): HttpHeaders {
    const { accessToken } = opts;
    const normalizedUserAgent = this.userAgent?.trim();
    const headers: HttpHeaders = {
      accept: jsonAcceptHeader,
      'content-type': jsonUtf8ContentType,
    };
    if (normalizedUserAgent != null && normalizedUserAgent.length > 0) {
      headers['user-agent'] = normalizedUserAgent;
    }
    if (accessToken != null) headers['authorization'] = `Bearer ${accessToken}`;
    return headers;
  }

  close(): void {
    this._httpClient.close?.();
    const environmentProxyClient = this._environmentProxyClient;
    if (environmentProxyClient != null && environmentProxyClient !== this._httpClient) {
      environmentProxyClient.close?.();
    }
  }
}

export class PasswordResetChallenge {
  readonly id: string;
  readonly maskedEmail: string;
  readonly retryAfterSeconds: number;

  constructor(opts: { id: string; maskedEmail: string; retryAfterSeconds: number }) {
    this.id = opts.id;
    this.maskedEmail = opts.maskedEmail;
    this.retryAfterSeconds = opts.retryAfterSeconds;
  }

  static fromJson(json: JsonMap): PasswordResetChallenge {
    return new PasswordResetChallenge({
      id: requireString(json, 'challenge_id'),
      maskedEmail: requireString(json, 'masked_email'),
      retryAfterSeconds: _retryAfterSeconds(json['retry_after']),
    });
  }
}

export class EmailVerificationChallenge {
  readonly id: string;
  readonly retryAfterSeconds: number;

  constructor(opts: { id: string; retryAfterSeconds: number }) {
    this.id = opts.id;
    this.retryAfterSeconds = opts.retryAfterSeconds;
  }

  static fromJson(json: JsonMap): EmailVerificationChallenge {
    return new EmailVerificationChallenge({
      id: requireString(json, 'challenge_id'),
      retryAfterSeconds: _retryAfterSeconds(json['retry_after']),
    });
  }
}

export class EmailVerificationInspection {
  readonly canSend: boolean;
  readonly retryAfterSeconds: number;
  readonly challengeId: string | null;

  constructor(opts: { canSend: boolean; retryAfterSeconds: number; challengeId?: string | null }) {
    this.canSend = opts.canSend;
    this.retryAfterSeconds = opts.retryAfterSeconds;
    this.challengeId = opts.challengeId ?? null;
  }

  get reusableChallenge(): EmailVerificationChallenge | null {
    const id = this.challengeId;
    if (id == null || id.length === 0) return null;
    return new EmailVerificationChallenge({
      id: id,
      retryAfterSeconds: this.retryAfterSeconds,
    });
  }

  static fromJson(json: JsonMap): EmailVerificationInspection {
    return new EmailVerificationInspection({
      canSend: asBool(json['can_send']) ?? false,
      challengeId: asString(json['challenge_id']),
      retryAfterSeconds: _retryAfterSeconds(json['retry_after']),
    });
  }
}

export class PasswordResetInspection {
  readonly canSend: boolean;
  readonly maskedEmail: string;
  readonly retryAfterSeconds: number;
  readonly challengeId: string | null;

  constructor(opts: { canSend: boolean; maskedEmail: string; retryAfterSeconds: number; challengeId?: string | null }) {
    this.canSend = opts.canSend;
    this.maskedEmail = opts.maskedEmail;
    this.retryAfterSeconds = opts.retryAfterSeconds;
    this.challengeId = opts.challengeId ?? null;
  }

  get reusableChallenge(): PasswordResetChallenge | null {
    const id = this.challengeId;
    if (id == null || id.length === 0) return null;
    return new PasswordResetChallenge({
      id: id,
      maskedEmail: this.maskedEmail,
      retryAfterSeconds: this.retryAfterSeconds,
    });
  }

  static fromJson(json: JsonMap): PasswordResetInspection {
    return new PasswordResetInspection({
      canSend: asBool(json['can_send']) ?? false,
      challengeId: asString(json['challenge_id']),
      maskedEmail: requireString(json, 'masked_email'),
      retryAfterSeconds: _retryAfterSeconds(json['retry_after']),
    });
  }
}

export class AuthSession {
  readonly accessToken: string;
  readonly refreshToken: string;
  readonly accessTokenExpiresAt: Date;
  readonly user: CurrentUser;

  constructor(opts: { accessToken: string; refreshToken: string; accessTokenExpiresAt: Date; user: CurrentUser }) {
    this.accessToken = opts.accessToken;
    this.refreshToken = opts.refreshToken;
    this.accessTokenExpiresAt = opts.accessTokenExpiresAt;
    this.user = opts.user;
  }

  static fromJson(json: JsonMap): AuthSession {
    return new AuthSession({
      accessToken: requireString(json, 'access_token'),
      refreshToken: requireString(json, 'refresh_token'),
      accessTokenExpiresAt: AuthSession._parseAccessTokenExpiry(json),
      user: CurrentUser.fromJson(requireMap(json, 'user')),
    });
  }

  /**
   * Resolves the access-token expiry from whichever field the server sent.
   * Prefers the absolute `access_token_expires_at`; otherwise derives it from
   * a relative `expires_in` (accepting int, double, or numeric string). Falls
   * back to a conservative default when neither is present or parseable, so a
   * malformed/partial token response can't crash the login flow.
   */
  private static _parseAccessTokenExpiry(json: JsonMap): Date {
    const fallback = 15 * 60 * 1000;
    const parsed = asDate(json['access_token_expires_at']);
    if (parsed != null) return parsed;
    const expiresIn = json['expires_in'];
    let seconds: number | null = null;
    if (typeof expiresIn === 'number' && Number.isFinite(expiresIn)) {
      seconds = Math.trunc(expiresIn);
    } else if (typeof expiresIn === 'string') {
      seconds = _tryParseInt(expiresIn);
    }
    return new Date(Date.now() + (seconds != null && seconds > 0 ? seconds * 1000 : fallback));
  }

  isAccessTokenExpiringSoon(opts: { threshold?: number } = {}): boolean {
    const { threshold = 60_000 } = opts;
    return Date.now() + threshold > this.accessTokenExpiresAt.getTime();
  }
}

export class AuthException extends Error {
  readonly statusCode: number;
  readonly code: string;

  constructor(message: string, opts: { statusCode: number; code: string }) {
    super(message);
    this.name = 'AuthException';
    this.statusCode = opts.statusCode;
    this.code = opts.code;
  }

  static fromResponse(response: HttpResponse): AuthException {
    try {
      const decoded = _castMap(decodeJsonBody(response));
      const error = _castNullableMap(decoded['error']);
      const message = _castNullableString(error?.['message']);
      const code = _castNullableString(error?.['code']);
      if (message != null && message.length > 0) {
        return new AuthException(
          localizedServerErrorMessage({
            code: code ?? 'request_failed',
            statusCode: response.statusCode,
            message: message,
          }),
          {
            statusCode: response.statusCode,
            code: code ?? 'request_failed',
          },
        );
      }
    } catch {
      // Fall through to the status-based message.
    }
    return new AuthException(`请求失败 (${response.statusCode})`, {
      statusCode: response.statusCode,
      code: 'request_failed',
    });
  }

  get isUnauthorized(): boolean {
    return this.statusCode === 401;
  }

  override toString(): string {
    return this.message;
  }
}

function _sharedPlatformHttpClient(): HttpClient {
  return { send: (request) => platform().http.send(request) };
}

/** Dart `uri.replace(queryParameters: query)`. */
function _replaceQuery(uri: string, query: Record<string, string>): string {
  const url = new URL(uri);
  url.search = '';
  for (const [key, value] of Object.entries(query)) url.searchParams.append(key, value);
  return url.toString();
}

/** Dart `decodeJsonBody(response)! as Map<String, Object?>`. */
function _requireJsonMap(value: unknown): JsonMap {
  if (!isJsonMap(value)) throw new TypeError('Expected a JSON object response body');
  return value;
}

/** The Dart `switch (retryAfter) { int / num / String / _ }` used by every challenge. */
function _retryAfterSeconds(retryAfter: unknown): number {
  if (typeof retryAfter === 'number' && Number.isFinite(retryAfter)) return Math.trunc(retryAfter);
  if (typeof retryAfter === 'string') return _tryParseInt(retryAfter) ?? 0;
  return 0;
}

/** Dart `int.tryParse` (decimal, or hex with a `0x` prefix). */
function _tryParseInt(value: string): number | null {
  const source = value.trim();
  if (/^[+-]?\d+$/.test(source)) return Number.parseInt(source, 10);
  const hex = /^([+-]?)0x([0-9a-f]+)$/i.exec(source);
  if (hex != null) {
    const magnitude = Number.parseInt(hex[2]!, 16);
    return hex[1] === '-' ? -magnitude : magnitude;
  }
  return null;
}

// Strict Dart `as` casts, so a malformed error body falls back to the
// status-based message exactly like the Dart `catch (_)` does.
function _castMap(value: unknown): JsonMap {
  if (!isJsonMap(value)) throw new TypeError('Expected a JSON object');
  return value;
}

function _castNullableMap(value: unknown): JsonMap | null {
  if (value == null) return null;
  return _castMap(value);
}

function _castNullableString(value: unknown): string | null {
  if (value == null) return null;
  if (typeof value !== 'string') throw new TypeError('Expected a string');
  return value;
}
