import { describe, expect, test } from 'vitest';

import {
  LoginAccountRecord,
  NoopLoginAccountHistoryStore,
  deleteLoginAccountRecord,
  findLoginAccountRecord,
  lastLoginAccountRecord,
  normalizeLoginAccountHistory,
  rememberLoginAccount,
  updateLoginAccountAvatarMetadata,
} from '../../src/app/login_account_history';

const utc = (year: number, month: number, day: number): Date => new Date(Date.UTC(year, month - 1, day));

describe('login_account_history', () => {
  test('rememberLoginAccount records recent login without password by default', () => {
    const records = rememberLoginAccount({
      records: [],
      login: '  Kai@example.com  ',
      password: 'secret',
      rememberPassword: false,
      now: utc(2026, 1, 2),
    });

    expect(records).toHaveLength(1);
    expect(records[0]!.login).toBe('Kai@example.com');
    expect(records[0]!.password).toBeNull();
    expect(records[0]!.remembersPassword).toBe(false);
    expect(records[0]!.useCount).toBe(1);
  });

  test('rememberLoginAccount stores password only when enabled', () => {
    const records = rememberLoginAccount({
      records: [],
      login: 'kai',
      password: 'secret',
      rememberPassword: true,
      avatarUrl: '/avatars/kai.png',
      defaultAvatarKey: 'green-2',
      now: utc(2026, 1, 2),
    });

    expect(records[0]!.password).toBe('secret');
    expect(records[0]!.remembersPassword).toBe(true);
    expect(records[0]!.avatarUrl).toBe('/avatars/kai.png');
    expect(records[0]!.defaultAvatarKey).toBe('green-2');
    expect(records[0]!.useCount).toBe(1);
  });

  test('rememberLoginAccount moves existing account to top and clears password', () => {
    const records = rememberLoginAccount({
      records: [
        new LoginAccountRecord({ login: 'kai', password: 'old-secret', useCount: 2, updatedAt: utc(2026, 1, 1) }),
        new LoginAccountRecord({ login: 'morgan', updatedAt: utc(2026, 1, 1) }),
      ],
      login: 'KAI',
      password: 'new-secret',
      rememberPassword: false,
      now: utc(2026, 1, 3),
    });

    expect(records.map((record) => record.login)).toEqual(['KAI', 'morgan']);
    expect(records[0]!.password).toBeNull();
    expect(records[0]!.useCount).toBe(3);
  });

  test('normalizeLoginAccountHistory sorts by use count then latest login', () => {
    const records = normalizeLoginAccountHistory([
      new LoginAccountRecord({ login: 'kai', useCount: 2, updatedAt: utc(2026, 1, 1) }),
      new LoginAccountRecord({ login: 'morgan', useCount: 2, updatedAt: utc(2026, 1, 3) }),
      new LoginAccountRecord({ login: 'riley', useCount: 3, updatedAt: utc(2026, 1, 2) }),
    ]);

    expect(records.map((record) => record.login)).toEqual(['riley', 'morgan', 'kai']);
  });

  test('normalizeLoginAccountHistory dedupes case-insensitively and applies the limit', () => {
    const records = normalizeLoginAccountHistory(
      [
        new LoginAccountRecord({ login: ' Kai ', useCount: 1 }),
        new LoginAccountRecord({ login: 'kai', useCount: 4, password: '' }),
        new LoginAccountRecord({ login: 'morgan', useCount: 2 }),
        new LoginAccountRecord({ login: '   ' }),
      ],
      { limit: 1 },
    );

    expect(records.map((record) => record.login)).toEqual(['kai']);
    expect(records[0]!.useCount).toBe(4);
    expect(records[0]!.password).toBeNull();
    expect(Object.isFrozen(records)).toBe(true);
  });

  test('lastLoginAccountRecord returns the most recent login', () => {
    const latest = lastLoginAccountRecord([
      new LoginAccountRecord({ login: 'kai', useCount: 5, updatedAt: utc(2026, 1, 1) }),
      new LoginAccountRecord({ login: 'morgan', useCount: 1, updatedAt: utc(2026, 1, 4) }),
    ]);

    expect(latest?.login).toBe('morgan');
  });

  test('avatar metadata refreshes matching username and email aliases', () => {
    const updatedAt = utc(2026, 1, 4);
    const records = updateLoginAccountAvatarMetadata({
      records: [
        new LoginAccountRecord({ login: 'Kai@example.com', password: 'secret', useCount: 3, updatedAt }),
        new LoginAccountRecord({ login: 'morgan', defaultAvatarKey: 'green-2', updatedAt: utc(2026, 1, 3) }),
      ],
      accountAliases: ['kai', 'kai@example.com'],
      avatarUrl: '/assets/avatar-kai/custom.png',
      defaultAvatarKey: 'purple-2',
    });

    const kai = findLoginAccountRecord(records, 'kai@example.com')!;
    expect(kai.avatarUrl).toBe('/assets/avatar-kai/custom.png');
    expect(kai.defaultAvatarKey).toBe('purple-2');
    expect(kai.password).toBe('secret');
    expect(kai.useCount).toBe(3);
    expect(kai.updatedAt).toEqual(updatedAt);
    expect(findLoginAccountRecord(records, 'morgan')!.defaultAvatarKey).toBe('green-2');
  });

  test('avatar metadata refresh clears a removed custom avatar', () => {
    const records = updateLoginAccountAvatarMetadata({
      records: [new LoginAccountRecord({ login: 'kai', avatarUrl: '/assets/avatar-kai/old.png', defaultAvatarKey: 'green-2' })],
      accountAliases: ['KAI'],
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
    });

    expect(records[0]!.avatarUrl).toBeNull();
    expect(records[0]!.defaultAvatarKey).toBe('blue-3');
  });

  test('deleteLoginAccountRecord removes records case-insensitively', () => {
    const records = deleteLoginAccountRecord({
      records: [new LoginAccountRecord({ login: 'kai' }), new LoginAccountRecord({ login: 'morgan' })],
      login: 'KAI',
    });

    expect(records.map((record) => record.login)).toEqual(['morgan']);
  });

  test('NoopLoginAccountHistoryStore stores nothing', async () => {
    const store = new NoopLoginAccountHistoryStore();
    await store.write([new LoginAccountRecord({ login: 'kai' })]);
    expect(await store.read()).toEqual([]);
  });
});
