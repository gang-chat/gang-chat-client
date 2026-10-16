export const loginAccountHistoryLimit = 8;

export function normalizeLoginAccount(login: string): string {
  return login.trim();
}

export function loginAccountKey(login: string): string {
  return normalizeLoginAccount(login).toLowerCase();
}

export function loginAccountsMatch(first: string, second: string): boolean {
  const firstKey = loginAccountKey(first);
  return firstKey.length > 0 && firstKey === loginAccountKey(second);
}

export class LoginAccountRecord {
  constructor(opts: {
    login: string;
    password?: string | null;
    avatarUrl?: string | null;
    defaultAvatarKey?: string | null;
    useCount?: number;
    updatedAt?: Date | null;
  }) {
    const useCount = opts.useCount ?? 1;
    this.login = opts.login;
    this.password = opts.password ?? null;
    this.avatarUrl = opts.avatarUrl ?? null;
    this.defaultAvatarKey = opts.defaultAvatarKey ?? null;
    this.useCount = useCount < 1 ? 1 : useCount;
    this.updatedAt = opts.updatedAt ?? new Date(0);
  }

  readonly login: string;
  readonly password: string | null;
  readonly avatarUrl: string | null;
  readonly defaultAvatarKey: string | null;
  readonly useCount: number;
  readonly updatedAt: Date;

  get remembersPassword(): boolean {
    return this.password != null && this.password.length > 0;
  }

  copyWith(
    opts: {
      login?: string | null;
      password?: string | null;
      avatarUrl?: string | null;
      defaultAvatarKey?: string | null;
      useCount?: number | null;
      clearPassword?: boolean;
      clearAvatarUrl?: boolean;
      clearDefaultAvatarKey?: boolean;
      updatedAt?: Date | null;
    } = {},
  ): LoginAccountRecord {
    const { clearPassword = false, clearAvatarUrl = false, clearDefaultAvatarKey = false } = opts;
    return new LoginAccountRecord({
      login: opts.login ?? this.login,
      password: clearPassword ? null : (opts.password ?? this.password),
      avatarUrl: clearAvatarUrl ? null : (opts.avatarUrl ?? this.avatarUrl),
      defaultAvatarKey: clearDefaultAvatarKey ? null : (opts.defaultAvatarKey ?? this.defaultAvatarKey),
      useCount: opts.useCount ?? this.useCount,
      updatedAt: opts.updatedAt ?? this.updatedAt,
    });
  }
}

export abstract class LoginAccountHistoryStore {
  abstract read(): Promise<LoginAccountRecord[]>;

  abstract write(records: LoginAccountRecord[]): Promise<void>;
}

export class NoopLoginAccountHistoryStore extends LoginAccountHistoryStore {
  async read(): Promise<LoginAccountRecord[]> {
    return [];
  }

  async write(records: LoginAccountRecord[]): Promise<void> {}
}

export function normalizeLoginAccountHistory(
  records: Iterable<LoginAccountRecord>,
  opts: { limit?: number } = {},
): LoginAccountRecord[] {
  const { limit = loginAccountHistoryLimit } = opts;
  const byLogin = new Map<string, LoginAccountRecord>();
  for (const record of records) {
    const login = normalizeLoginAccount(record.login);
    const key = loginAccountKey(login);
    if (key.length === 0) continue;
    const normalized = record.copyWith({ login, clearPassword: !record.remembersPassword });
    const existing = byLogin.get(key);
    if (existing == null || _compareLoginRecords(normalized, existing) < 0) {
      byLogin.set(key, normalized);
    }
  }

  const result = [...byLogin.values()].sort(_compareLoginRecords);
  if (result.length > limit) {
    result.splice(limit, result.length - limit);
  }
  // `List.unmodifiable`: frozen at runtime, typed as a plain array so it can be
  // handed straight to [LoginAccountHistoryStore.write].
  return Object.freeze(result) as LoginAccountRecord[];
}

export function findLoginAccountRecord(
  records: Iterable<LoginAccountRecord>,
  login: string,
): LoginAccountRecord | null {
  for (const record of records) {
    if (loginAccountsMatch(record.login, login)) return record;
  }
  return null;
}

export function lastLoginAccountRecord(records: Iterable<LoginAccountRecord>): LoginAccountRecord | null {
  let latest: LoginAccountRecord | null = null;
  for (const record of records) {
    if (normalizeLoginAccount(record.login).length === 0) continue;
    if (
      latest == null ||
      record.updatedAt.getTime() > latest.updatedAt.getTime() ||
      (record.updatedAt.getTime() === latest.updatedAt.getTime() && record.useCount > latest.useCount)
    ) {
      latest = record;
    }
  }
  return latest;
}

export function rememberLoginAccount(opts: {
  records: Iterable<LoginAccountRecord>;
  login: string;
  password: string;
  rememberPassword: boolean;
  avatarUrl?: string | null;
  defaultAvatarKey?: string | null;
  updateAvatarMetadata?: boolean;
  now?: Date | null;
  limit?: number;
}): LoginAccountRecord[] {
  const { password, rememberPassword, updateAvatarMetadata = false, limit = loginAccountHistoryLimit } = opts;
  const records = [...opts.records];
  const avatarUrl = opts.avatarUrl ?? null;
  const defaultAvatarKey = opts.defaultAvatarKey ?? null;
  const normalizedLogin = normalizeLoginAccount(opts.login);
  if (normalizedLogin.length === 0) {
    return normalizeLoginAccountHistory(records, { limit });
  }

  const existing = findLoginAccountRecord(records, normalizedLogin);
  const applyAvatarMetadata = updateAvatarMetadata || avatarUrl != null || defaultAvatarKey != null;
  const updated = new LoginAccountRecord({
    login: normalizedLogin,
    password: rememberPassword && password.length > 0 ? password : null,
    avatarUrl: applyAvatarMetadata ? _nonEmptyString(avatarUrl) : (existing?.avatarUrl ?? null),
    defaultAvatarKey: applyAvatarMetadata ? _nonEmptyString(defaultAvatarKey) : (existing?.defaultAvatarKey ?? null),
    useCount: (existing?.useCount ?? 0) + 1,
    updatedAt: opts.now ?? new Date(),
  });

  return normalizeLoginAccountHistory(
    [updated, ...records.filter((record) => !loginAccountsMatch(record.login, normalizedLogin))],
    { limit },
  );
}

export function updateLoginAccountAvatarMetadata(opts: {
  records: Iterable<LoginAccountRecord>;
  accountAliases: Iterable<string>;
  avatarUrl: string | null;
  defaultAvatarKey: string | null;
  limit?: number;
}): LoginAccountRecord[] {
  const { limit = loginAccountHistoryLimit } = opts;
  const records = [...opts.records];
  const aliasKeys = new Set([...opts.accountAliases].map(loginAccountKey).filter((key) => key.length > 0));
  if (aliasKeys.size === 0) {
    return normalizeLoginAccountHistory(records, { limit });
  }

  const normalizedAvatarUrl = _nonEmptyString(opts.avatarUrl);
  const normalizedDefaultAvatarKey = _nonEmptyString(opts.defaultAvatarKey);
  return normalizeLoginAccountHistory(
    records.map((record) => {
      if (!aliasKeys.has(loginAccountKey(record.login))) return record;
      return record.copyWith({
        avatarUrl: normalizedAvatarUrl,
        defaultAvatarKey: normalizedDefaultAvatarKey,
        clearAvatarUrl: normalizedAvatarUrl == null,
        clearDefaultAvatarKey: normalizedDefaultAvatarKey == null,
      });
    }),
    { limit },
  );
}

export function deleteLoginAccountRecord(opts: {
  records: Iterable<LoginAccountRecord>;
  login: string;
  limit?: number;
}): LoginAccountRecord[] {
  const { limit = loginAccountHistoryLimit } = opts;
  return normalizeLoginAccountHistory(
    [...opts.records].filter((record) => !loginAccountsMatch(record.login, opts.login)),
    { limit },
  );
}

function _compareLoginRecords(first: LoginAccountRecord, second: LoginAccountRecord): number {
  const useCount = _compare(second.useCount, first.useCount);
  if (useCount !== 0) return useCount;
  const updatedAt = _compare(second.updatedAt.getTime(), first.updatedAt.getTime());
  if (updatedAt !== 0) return updatedAt;
  return _compareStrings(loginAccountKey(first.login), loginAccountKey(second.login));
}

function _compare(a: number, b: number): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

/** Dart `String.compareTo`: UTF-16 code unit order. */
function _compareStrings(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

function _nonEmptyString(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed == null || trimmed.length === 0 ? null : trimmed;
}
