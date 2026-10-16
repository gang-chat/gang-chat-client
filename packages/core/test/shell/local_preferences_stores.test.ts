import { beforeEach, expect, test } from 'vitest';
import { CloseBehavior } from '../../src/app/close_behavior';
import { LoginAccountRecord } from '../../src/app/login_account_history';
import { LocalCloseBehaviorStore } from '../../src/shell/local_close_behavior_store';
import { LocalLanguagePreferenceStore } from '../../src/shell/local_language_preference_store';
import { LocalLoginAccountHistoryStore } from '../../src/shell/local_login_account_history_store';
import { installFakePlatform } from '../support/fake_platform';
import type { FakePlatform } from '../support/fake_platform';

let fake: FakePlatform;

beforeEach(() => {
  fake = installFakePlatform({ preferences: {} });
});

test('close behavior uses the Dart storage values', async () => {
  const store = new LocalCloseBehaviorStore();
  expect(await store.read()).toBe(CloseBehavior.askEveryTime);
  await store.write(CloseBehavior.minimizeToTray);
  expect(fake.preferences.getString('gang.closeBehavior')).toBe('minimize_to_tray');
  expect(await store.read()).toBe(CloseBehavior.minimizeToTray);
});

test('language preference is normalized on read and write', async () => {
  const store = new LocalLanguagePreferenceStore();
  await store.write(' en ');
  expect(fake.preferences.getString('gang.language')).toBe('en');
  await fake.preferences.setString('gang.language', 'klingon');
  expect(await store.read()).toBe(await new LocalLanguagePreferenceStore().read());
});

test('login history keeps metadata in preferences and passwords in secure storage', async () => {
  const store = new LocalLoginAccountHistoryStore();
  await store.write([
    new LoginAccountRecord({ login: ' Alice ', password: 'secret', useCount: 2, updatedAt: new Date(Date.UTC(2026, 0, 2)) }),
    new LoginAccountRecord({ login: 'bob', avatarUrl: ' https://a/b.png ', updatedAt: new Date(Date.UTC(2026, 0, 1)) }),
  ]);

  // base64Url(utf8('alice')) with padding, as Dart's base64Url.encode.
  expect([...fake.secureStorage.values.entries()]).toEqual([['gang.loginAccountPassword.YWxpY2U=', 'secret']]);
  const metadata = JSON.parse(fake.preferences.getString('gang.loginAccounts')!) as Array<Record<string, unknown>>;
  expect(metadata.map((item) => item['login'])).toEqual(['Alice', 'bob']);
  expect(metadata[0]).toMatchObject({ remembersPassword: true, useCount: 2, updatedAt: '2026-01-02T00:00:00.000Z' });

  const records = await store.read();
  expect(records.map((record) => [record.login, record.password])).toEqual([
    ['Alice', 'secret'],
    ['bob', null],
  ]);
  expect(records[1]!.avatarUrl).toBe('https://a/b.png');

  await store.write([records[1]!]);
  expect(fake.secureStorage.values.size).toBe(0);
  await store.write([]);
  expect(fake.preferences.containsKey('gang.loginAccounts')).toBe(false);
});

test('login history ignores corrupt metadata', async () => {
  await fake.preferences.setString('gang.loginAccounts', '{not json');
  expect(await new LocalLoginAccountHistoryStore().read()).toEqual([]);
  await fake.preferences.setString('gang.loginAccounts', JSON.stringify([{ login: '  ' }, { login: 'x', useCount: '3' }, 5]));
  const records = await new LocalLoginAccountHistoryStore().read();
  expect(records.map((record) => [record.login, record.useCount])).toEqual([['x', 3]]);
});
