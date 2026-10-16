import {
  LoginAccountHistoryStore,
  LoginAccountRecord,
  loginAccountKey,
  normalizeLoginAccount,
  normalizeLoginAccountHistory,
} from '../app/login_account_history';
import { isJsonMap } from '../lib/json';
import type { JsonMap } from '../lib/json';
import { platform } from '../platform/platform';
import type { PreferencesStore, SecureStorage } from '../platform/platform';
import { utf8Encode } from '../platform/http';

/**
 * Remembered login accounts: metadata in plain preferences, passwords in
 * secure storage (one entry per account). Keys match the Flutter client.
 *
 * PORT: flutter_secure_storage's macOS `usesDataProtectionKeychain: false`
 * option is a host concern of `platform().secureStorage`.
 */
export class LocalLoginAccountHistoryStore extends LoginAccountHistoryStore {
  constructor(opts: { preferences?: PreferencesStore; secureStorage?: SecureStorage } = {}) {
    super();
    this._preferences = opts.preferences ?? null;
    this._secureStorage = opts.secureStorage ?? null;
  }

  private readonly _preferences: PreferencesStore | null;
  private readonly _secureStorage: SecureStorage | null;

  private static readonly _accountsKey = 'gang.loginAccounts';
  private static readonly _passwordKeyPrefix = 'gang.loginAccountPassword.';

  private get _prefs(): PreferencesStore {
    return this._preferences ?? platform().preferences;
  }

  private get _storage(): SecureStorage {
    return this._secureStorage ?? platform().secureStorage;
  }

  override async read(): Promise<LoginAccountRecord[]> {
    const metadata = this._readMetadata(this._prefs);
    const records: LoginAccountRecord[] = [];
    for (const item of metadata) {
      const password = item.remembersPassword ? await this._storage.read(this._passwordKey(item.login)) : null;
      records.push(
        new LoginAccountRecord({
          login: item.login,
          password: password == null || password.length === 0 ? null : password,
          avatarUrl: item.avatarUrl,
          defaultAvatarKey: item.defaultAvatarKey,
          useCount: item.useCount,
          updatedAt: item.updatedAt,
        }),
      );
    }
    return normalizeLoginAccountHistory(records);
  }

  override async write(records: LoginAccountRecord[]): Promise<void> {
    const prefs = this._prefs;
    const oldMetadata = this._readMetadata(prefs);
    const normalized = normalizeLoginAccountHistory(records);
    const activePasswordKeys = new Set<string>();

    for (const record of normalized) {
      const key = this._passwordKey(record.login);
      activePasswordKeys.add(key);
      if (record.remembersPassword) {
        await this._storage.write(key, record.password!);
      } else {
        await this._storage.delete(key);
      }
    }

    for (const item of oldMetadata) {
      const key = this._passwordKey(item.login);
      if (!activePasswordKeys.has(key)) {
        await this._storage.delete(key);
      }
    }

    if (normalized.length === 0) {
      await prefs.remove(LocalLoginAccountHistoryStore._accountsKey);
      return;
    }
    await prefs.setString(
      LocalLoginAccountHistoryStore._accountsKey,
      JSON.stringify(
        normalized.map((record) => ({
          login: record.login,
          updatedAt: record.updatedAt.toISOString(),
          remembersPassword: record.remembersPassword,
          avatarUrl: record.avatarUrl,
          defaultAvatarKey: record.defaultAvatarKey,
          useCount: record.useCount,
        })),
      ),
    );
  }

  private _readMetadata(prefs: PreferencesStore): _LoginAccountMetadata[] {
    const encoded = prefs.getString(LocalLoginAccountHistoryStore._accountsKey);
    if (encoded == null || encoded.length === 0) return [];
    try {
      const decoded: unknown = JSON.parse(encoded);
      if (!Array.isArray(decoded)) return [];
      return decoded
        .filter(isJsonMap)
        .map((json) => _LoginAccountMetadata.fromJson(json))
        .filter((item): item is _LoginAccountMetadata => item != null);
    } catch {
      return [];
    }
  }

  private _passwordKey(login: string): string {
    const key = _base64UrlEncode(utf8Encode(loginAccountKey(login)));
    return `${LocalLoginAccountHistoryStore._passwordKeyPrefix}${key}`;
  }
}

class _LoginAccountMetadata {
  constructor(opts: {
    login: string;
    updatedAt: Date;
    remembersPassword: boolean;
    avatarUrl: string | null;
    defaultAvatarKey: string | null;
    useCount: number;
  }) {
    this.login = opts.login;
    this.updatedAt = opts.updatedAt;
    this.remembersPassword = opts.remembersPassword;
    this.avatarUrl = opts.avatarUrl;
    this.defaultAvatarKey = opts.defaultAvatarKey;
    this.useCount = opts.useCount;
  }

  readonly login: string;
  readonly updatedAt: Date;
  readonly remembersPassword: boolean;
  readonly avatarUrl: string | null;
  readonly defaultAvatarKey: string | null;
  readonly useCount: number;

  static fromJson(json: JsonMap): _LoginAccountMetadata | null {
    const login = json['login'];
    if (typeof login !== 'string' || normalizeLoginAccount(login).length === 0) return null;
    const updatedAtText = typeof json['updatedAt'] === 'string' ? json['updatedAt'] : '';
    return new _LoginAccountMetadata({
      login: normalizeLoginAccount(login),
      updatedAt: _tryParseDate(updatedAtText) ?? new Date(0),
      remembersPassword: json['remembersPassword'] === true,
      avatarUrl: _nonEmptyString(json['avatarUrl']),
      defaultAvatarKey: _nonEmptyString(json['defaultAvatarKey']),
      useCount: _positiveInt(json['useCount']),
    });
  }
}

function _tryParseDate(value: string): Date | null {
  if (value.length === 0) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function _nonEmptyString(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}

function _positiveInt(value: unknown): number {
  // Dart: an int is kept, any other positive num goes through toInt().
  if (typeof value === 'number' && Number.isFinite(value) && value > 0) return Math.trunc(value);
  // Dart `int.tryParse`: optional sign, digits, surrounding whitespace ignored.
  if (typeof value === 'string' && /^\s*[+-]?\d+\s*$/.test(value)) {
    const parsed = Number.parseInt(value.trim(), 10);
    if (parsed > 0) return parsed;
  }
  return 1;
}

const _base64UrlAlphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';

/** Dart `base64Url.encode`: URL-safe alphabet, with `=` padding. */
function _base64UrlEncode(bytes: Uint8Array): string {
  let out = '';
  let index = 0;
  for (; index + 2 < bytes.length; index += 3) {
    const n = (bytes[index]! << 16) | (bytes[index + 1]! << 8) | bytes[index + 2]!;
    out +=
      _base64UrlAlphabet[(n >> 18) & 63]! +
      _base64UrlAlphabet[(n >> 12) & 63]! +
      _base64UrlAlphabet[(n >> 6) & 63]! +
      _base64UrlAlphabet[n & 63]!;
  }
  const remaining = bytes.length - index;
  if (remaining === 1) {
    const n = bytes[index]! << 16;
    out += `${_base64UrlAlphabet[(n >> 18) & 63]}${_base64UrlAlphabet[(n >> 12) & 63]}==`;
  } else if (remaining === 2) {
    const n = (bytes[index]! << 16) | (bytes[index + 1]! << 8);
    out += `${_base64UrlAlphabet[(n >> 18) & 63]}${_base64UrlAlphabet[(n >> 12) & 63]}${_base64UrlAlphabet[(n >> 6) & 63]}=`;
  }
  return out;
}
