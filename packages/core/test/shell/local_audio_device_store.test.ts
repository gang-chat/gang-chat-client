import { beforeEach, expect, test } from 'vitest';
import { normalizedAudioVolume } from '../../src/app/audio_levels';
import { LocalAudioDeviceStore } from '../../src/shell/local_audio_device_store';
import { installFakePlatform } from '../support/fake_platform';
import type { FakePlatform } from '../support/fake_platform';

let fake: FakePlatform;

beforeEach(() => {
  fake = installFakePlatform({ preferences: {} });
});

test('read returns defaults when nothing is stored', async () => {
  const store = new LocalAudioDeviceStore();

  const stored = await store.read();

  expect(stored.inputDeviceId).toBeNull();
  expect(stored.outputDeviceId).toBeNull();
  expect(stored.inputVolume).toBe(0.5);
  expect(stored.outputVolume).toBe(0.5);
  expect(stored.musicBoxVolume).toBe(0.5);
  expect(stored.screenShareVolume).toBe(0.5);
  expect(stored.screenShareMaxHeight).toBe(720);
  expect(stored.screenShareFrameRate).toBe(30);
  expect(await store.readParticipantVoiceVolume('user_2')).toBe(1.0);
});

test('writes round-trip through the preferences store', async () => {
  const store = new LocalAudioDeviceStore();

  await store.writeInputDevicePreference({ deviceId: '87', label: 'Desk Mic', groupId: 'group_mic' });
  await store.writeOutputDevicePreference({ deviceId: '54', label: 'USB Headset', groupId: 'group_headset' });
  await store.writeInputVolume(0.4);
  await store.writeOutputVolume(0.6);
  await store.writeMusicBoxVolume(0.2);
  await store.writeScreenShareVolume(0.8);
  await store.writeScreenShareMaxHeight(720);
  await store.writeScreenShareFrameRate(30);
  await store.writeParticipantVoiceVolume('user/2', 1.75);

  const stored = await store.read();
  expect(stored.inputDeviceId).toBe('87');
  expect(stored.inputDeviceLabel).toBe('Desk Mic');
  expect(stored.inputDeviceGroupId).toBe('group_mic');
  expect(stored.outputDeviceId).toBe('54');
  expect(stored.outputDeviceLabel).toBe('USB Headset');
  expect(stored.outputDeviceGroupId).toBe('group_headset');
  expect(stored.inputVolume).toBeCloseTo(0.4, 9);
  expect(stored.outputVolume).toBeCloseTo(0.6, 9);
  expect(stored.musicBoxVolume).toBeCloseTo(0.2, 9);
  expect(stored.screenShareVolume).toBeCloseTo(0.8, 9);
  expect(stored.screenShareMaxHeight).toBe(720);
  expect(stored.screenShareFrameRate).toBe(30);
  expect(await store.readParticipantVoiceVolume('user/2')).toBeCloseTo(1.75, 9);
});

test('keeps the Dart SharedPreferences keys', async () => {
  const store = new LocalAudioDeviceStore();
  await store.writeInputDevicePreference({ deviceId: '87', label: '  ', groupId: ' g ' });
  await store.writeParticipantVoiceVolume('user/2', 1.5);
  await store.writeParticipantVoiceVolume('user_3', 1.0);

  expect(Object.fromEntries(fake.preferences.values)).toEqual({
    'gang.audioInputDeviceId': '87',
    'gang.audioInputDeviceGroupId': 'g',
    'gang.participantVoiceVolume.user%2F2': 1.5,
  });
});

test('keeps an explicitly selected desktop high profile', async () => {
  const store = new LocalAudioDeviceStore();

  await store.writeScreenShareMaxHeight(1080);
  await store.writeScreenShareFrameRate(60);
  const stored = await store.read();

  expect(stored.screenShareMaxHeight).toBe(1080);
  expect(stored.screenShareFrameRate).toBe(60);
});

test('screen share height is coerced to a supported option on write', async () => {
  const store = new LocalAudioDeviceStore();

  await store.writeScreenShareMaxHeight(999);
  const stored = await store.read();

  expect(stored.screenShareMaxHeight).toBe(720);
});

test('screen share frame rate is coerced to a supported option', async () => {
  const store = new LocalAudioDeviceStore();

  await store.writeScreenShareFrameRate(24);
  const stored = await store.read();

  expect(stored.screenShareFrameRate).toBe(30);
});

test('volumes are clamped to the valid range on write', async () => {
  const store = new LocalAudioDeviceStore();

  await store.writeOutputVolume(5.0);
  await store.writeScreenShareVolume(-0.5);
  await store.writeParticipantVoiceVolume('user_2', 5.0);
  await store.writeParticipantVoiceVolume('user_3', -1.0);
  const stored = await store.read();

  expect(stored.outputVolume).toBe(normalizedAudioVolume(5.0));
  expect(stored.outputVolume).toBe(1.0);
  expect(stored.screenShareVolume).toBe(0.0);
  expect(await store.readParticipantVoiceVolume('user_2')).toBe(2.0);
  expect(await store.readParticipantVoiceVolume('user_3')).toBe(0.0);
});
