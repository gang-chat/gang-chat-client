import { beforeEach, expect, test } from 'vitest';
import { LocalPreferencesMigration } from '../../src/shell/local_preferences_migration';
import { installFakePlatform } from '../support/fake_platform';
import type { FakePlatform } from '../support/fake_platform';

let fake: FakePlatform;

beforeEach(() => {
  fake = installFakePlatform({ os: 'windows', preferences: { 'gang.audioInputVolume': 0.8 } });
});

test('migrates every supported gang preference without overwriting', async () => {
  const preferences = fake.preferences;

  const count = await LocalPreferencesMigration.migrateLegacyPreferences({
    preferences,
    legacyValues: {
      'flutter.gang.audioInputVolume': 0.25,
      'flutter.gang.audioOutputVolume': 0.65,
      'flutter.gang.closeBehavior': 'minimize_to_tray',
      'flutter.gang.autoUpdatePrompt': false,
      'flutter.gang.futureStringList': ['one', 'two'],
      'flutter.gang.refreshToken': 'must-not-migrate',
      'flutter.gang.loginAccountPassword.alice': 'must-not-migrate',
      'flutter.unrelated.preference': 'ignored',
      'flutter.gang.unsupported': { value: 1 },
    },
  });

  expect(count).toBe(4);
  expect(preferences.getDouble('gang.audioInputVolume')).toBe(0.8);
  expect(preferences.getDouble('gang.audioOutputVolume')).toBe(0.65);
  expect(preferences.getString('gang.closeBehavior')).toBe('minimize_to_tray');
  expect(preferences.getBool('gang.autoUpdatePrompt')).toBe(false);
  expect(preferences.getStringList('gang.futureStringList')).toEqual(['one', 'two']);
  expect(preferences.containsKey('unrelated.preference')).toBe(false);
  expect(preferences.containsKey('gang.unsupported')).toBe(false);
  expect(preferences.containsKey('gang.refreshToken')).toBe(false);
  expect(preferences.containsKey('gang.loginAccountPassword.alice')).toBe(false);
});

test('recovers defaulted volumes but preserves later custom values', async () => {
  fake.preferences.reset({
    'gang.audioInputVolume': 0.5,
    'gang.audioOutputVolume': 0.35,
    'gang.musicBoxVolume': 0.5,
  });

  const count = await LocalPreferencesMigration.migrateLegacyPreferences({
    preferences: fake.preferences,
    legacyValues: {
      'flutter.gang.audioInputVolume': 0.9,
      'flutter.gang.audioOutputVolume': 0.8,
      'flutter.gang.musicBoxVolume': 0.65,
    },
  });

  expect(count).toBe(2);
  expect(fake.preferences.getDouble('gang.audioInputVolume')).toBe(0.9);
  expect(fake.preferences.getDouble('gang.audioOutputVolume')).toBe(0.35);
  expect(fake.preferences.getDouble('gang.musicBoxVolume')).toBe(0.65);
});

test('migrate reads the legacy Windows file once', async () => {
  fake.files.dirs.roamingAppData = '/appdata';
  fake.files.put(
    '/appdata/com.gangchat/client/shared_preferences.json',
    JSON.stringify({ 'flutter.gang.language': 'en', 'flutter.gang.screenShareMaxHeight': 1080 }),
  );

  await new LocalPreferencesMigration().migrate();

  expect(fake.preferences.getString('gang.language')).toBe('en');
  expect(fake.preferences.getInt('gang.screenShareMaxHeight')).toBe(1080);
  expect(fake.preferences.getBool('gang.preferencesMigration.windowsProductName.v1')).toBe(true);

  await fake.preferences.remove('gang.language');
  await new LocalPreferencesMigration().migrate();
  expect(fake.preferences.containsKey('gang.language')).toBe(false);
});

test('migrate is a no-op off Windows or without a legacy file', async () => {
  fake.files.dirs.roamingAppData = '/appdata';
  await new LocalPreferencesMigration().migrate();
  expect(fake.preferences.containsKey('gang.preferencesMigration.windowsProductName.v1')).toBe(false);

  const mac = installFakePlatform({ os: 'macos' });
  mac.files.dirs.roamingAppData = '/appdata';
  mac.files.put('/appdata/com.gangchat/client/shared_preferences.json', '{"flutter.gang.language":"en"}');
  await new LocalPreferencesMigration().migrate();
  expect(mac.preferences.containsKey('gang.language')).toBe(false);
});
