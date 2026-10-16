import { AuthException } from '../auth/auth_client';
import { isTlsHandshakeFailure } from '../auth/auth_transport';
import { loginUsernameValidationError } from './account_forms';
import { AuthRequest } from './auth_session_controller';
import { defaultLanguagePreference, normalizeLanguagePreference } from './language_preference';

const _registerEmailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function registerEmailValidationError(email: string): string | null {
  const normalized = email.trim();
  if (normalized.length === 0) return '邮箱不能为空';
  if (normalized.length > 254 || !_registerEmailPattern.test(normalized)) {
    return '请输入有效的邮箱地址';
  }
  return null;
}

export class AuthFormResult {
  private constructor(opts: { request?: AuthRequest | null; error?: string | null }) {
    this.request = opts.request ?? null;
    this.error = opts.error ?? null;
  }

  static valid(request: AuthRequest): AuthFormResult {
    return new AuthFormResult({ request });
  }

  static invalid(error: string): AuthFormResult {
    return new AuthFormResult({ error });
  }

  readonly request: AuthRequest | null;
  readonly error: string | null;

  get isValid(): boolean {
    return this.request != null;
  }
}

export class AuthSubmitState {
  constructor(opts: { busy?: boolean; error?: string | null } = {}) {
    this.busy = opts.busy ?? false;
    this.error = opts.error ?? null;
  }

  readonly busy: boolean;
  readonly error: string | null;
}

export function authRequestFromForm(opts: {
  registering: boolean;
  login: string;
  password: string;
  username?: string;
  confirmPassword?: string;
  emailVerificationToken?: string;
  language?: string;
}): AuthFormResult {
  const {
    registering,
    login,
    password,
    username = '',
    confirmPassword = '',
    emailVerificationToken = '',
    language = defaultLanguagePreference,
  } = opts;
  const copy = authFormCopy(language);
  const normalizedLogin = login.trim();
  if (normalizedLogin.length === 0 || password.length === 0) {
    return AuthFormResult.invalid(copy.missingCredentials);
  }

  if (!registering) {
    return AuthFormResult.valid(AuthRequest.login({ login: normalizedLogin, password }));
  }

  const normalizedUsername = username.trim();
  if (normalizedUsername.length === 0) {
    return AuthFormResult.invalid(copy.missingUsername);
  }
  const usernameError = loginUsernameValidationError(normalizedUsername);
  if (usernameError != null) {
    return AuthFormResult.invalid(usernameError);
  }
  const emailError = registerEmailValidationError(normalizedLogin);
  if (emailError != null) {
    return AuthFormResult.invalid(emailError);
  }
  if (password !== confirmPassword) {
    return AuthFormResult.invalid(copy.passwordMismatch);
  }
  if (emailVerificationToken.trim().length === 0) {
    return AuthFormResult.invalid(copy.emailVerificationRequired);
  }

  return AuthFormResult.valid(
    AuthRequest.register({
      username: normalizedUsername,
      login: normalizedLogin,
      password,
      emailVerificationToken,
    }),
  );
}

export function authSubmitStarted(): AuthSubmitState {
  return new AuthSubmitState({ busy: true });
}

export function authSubmitInvalid(error: string | null): AuthSubmitState {
  return new AuthSubmitState({ error });
}

export function authSubmitFailed(failure: unknown, opts: { language?: string } = {}): AuthSubmitState {
  return new AuthSubmitState({ error: authSubmitFailureMessage(failure, opts) });
}

export function authSubmitFailureMessage(failure: unknown, opts: { language?: string } = {}): string {
  const { language = defaultLanguagePreference } = opts;
  const copy = authFormCopy(language);
  if (failure instanceof AuthException) return copy.authException(failure);
  if (isTlsHandshakeFailure(failure)) return copy.secureConnectionFailed;
  return copy.connectionFailed(failure);
}

export function authFormCopy(language: string): AuthFormCopy {
  switch (normalizeLanguagePreference(language)) {
    case 'zh-Hant':
      return AuthFormCopy.zhHant();
    case 'en':
      return AuthFormCopy.en();
    default:
      return AuthFormCopy.zhHans();
  }
}

interface AuthFormCopyFields {
  missingCredentials: string;
  missingUsername: string;
  passwordMismatch: string;
  emailVerificationRequired: string;
  connectionFailedPrefix: string;
  secureConnectionFailed: string;
  invalidCredentials: string;
  rateLimited: string;
  conflict: string;
  badRequest: string;
  serverError: string;
  requestFailedPrefix: string;
  fallback: string;
}

export class AuthFormCopy implements AuthFormCopyFields {
  private constructor(fields: AuthFormCopyFields) {
    this.missingCredentials = fields.missingCredentials;
    this.missingUsername = fields.missingUsername;
    this.passwordMismatch = fields.passwordMismatch;
    this.emailVerificationRequired = fields.emailVerificationRequired;
    this.connectionFailedPrefix = fields.connectionFailedPrefix;
    this.secureConnectionFailed = fields.secureConnectionFailed;
    this.invalidCredentials = fields.invalidCredentials;
    this.rateLimited = fields.rateLimited;
    this.conflict = fields.conflict;
    this.badRequest = fields.badRequest;
    this.serverError = fields.serverError;
    this.requestFailedPrefix = fields.requestFailedPrefix;
    this.fallback = fields.fallback;
  }

  private static readonly _zhHans = new AuthFormCopy({
    missingCredentials: '请输入账号和密码后继续',
    missingUsername: '登录用户名不能为空',
    passwordMismatch: '两次输入的密码不一致',
    emailVerificationRequired: '请先验证邮箱',
    connectionFailedPrefix: '无法连接服务器：',
    secureConnectionFailed: '无法建立安全连接，请检查网络、代理或系统时间后重试',
    invalidCredentials: '账号或密码不正确',
    rateLimited: '登录尝试次数过多，请稍后再试',
    conflict: '登录用户名或邮箱已被占用',
    badRequest: '请检查账号信息后再试',
    serverError: '服务器暂时无法完成请求，请稍后再试',
    requestFailedPrefix: '请求失败',
    fallback: '认证请求失败，请稍后再试',
  });

  private static readonly _zhHant = new AuthFormCopy({
    missingCredentials: '請輸入帳號和密碼後繼續',
    missingUsername: '使用者名稱不能為空',
    passwordMismatch: '兩次輸入的密碼不一致',
    emailVerificationRequired: '請先驗證電子郵件',
    connectionFailedPrefix: '無法連線伺服器：',
    secureConnectionFailed: '無法建立安全連線，請檢查網路、代理或系統時間後重試',
    invalidCredentials: '帳號或密碼不正確',
    rateLimited: '登入嘗試次數過多，請稍後再試',
    conflict: '使用者名稱或電子郵件已被使用',
    badRequest: '請檢查帳號資訊後再試',
    serverError: '伺服器暫時無法完成請求，請稍後再試',
    requestFailedPrefix: '請求失敗',
    fallback: '認證請求失敗，請稍後再試',
  });

  private static readonly _en = new AuthFormCopy({
    missingCredentials: 'Enter your account and password to continue',
    missingUsername: 'Username is required',
    passwordMismatch: 'The two passwords do not match',
    emailVerificationRequired: 'Verify your email first',
    connectionFailedPrefix: 'Unable to connect to the server: ',
    secureConnectionFailed:
      'Could not establish a secure connection. Check your network, proxy, or system time and try again',
    invalidCredentials: 'Incorrect account or password',
    rateLimited: 'Too many login attempts, try again later',
    conflict: 'That username or email is already taken',
    badRequest: 'Check your account details and try again',
    serverError: 'The server could not complete the request, try again later',
    requestFailedPrefix: 'Request failed',
    fallback: 'Authentication failed, try again later',
  });

  static zhHans(): AuthFormCopy {
    return AuthFormCopy._zhHans;
  }

  static zhHant(): AuthFormCopy {
    return AuthFormCopy._zhHant;
  }

  static en(): AuthFormCopy {
    return AuthFormCopy._en;
  }

  readonly missingCredentials: string;
  readonly missingUsername: string;
  readonly passwordMismatch: string;
  readonly emailVerificationRequired: string;
  readonly connectionFailedPrefix: string;
  readonly secureConnectionFailed: string;
  readonly invalidCredentials: string;
  readonly rateLimited: string;
  readonly conflict: string;
  readonly badRequest: string;
  readonly serverError: string;
  readonly requestFailedPrefix: string;
  readonly fallback: string;

  connectionFailed(failure: unknown): string {
    return this.connectionFailedPrefix.replace(/[:：]\s*$/, '');
  }

  authException(failure: AuthException): string {
    switch (failure.code.trim()) {
      case 'unauthorized':
      case 'invalid_credentials':
        return this.invalidCredentials;
      case 'rate_limited':
        return this.rateLimited;
      case 'conflict':
        return this.conflict;
      case 'bad_request':
      case 'validation_failed':
        return this.badRequest;
      case 'email_verification_required':
        return this.emailVerificationRequired;
      case 'internal_error':
        return this.serverError;
      case 'request_failed':
        return this.requestFailed(failure.statusCode);
      default:
        return this.fallback;
    }
  }

  requestFailed(statusCode: number): string {
    return `${this.requestFailedPrefix} (${statusCode})`;
  }
}
