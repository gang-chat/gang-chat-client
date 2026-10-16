import { describe, expect, test } from 'vitest';

import {
  StoredAudioDevices,
  audioVolumeStorageString,
  preferredStoredAudioDeviceFrom,
  selectStoredAudioDeviceIfChanged,
  storedAudioDeviceIdFromStorageValue,
  storedAudioVolumeFromStorageValue,
} from '../../src/app/audio_device_preferences';

class _Device {
  constructor(
    readonly deviceId: string,
    readonly label: string,
    readonly kind: string,
    readonly groupId = '',
  ) {}
}

const _kindOf = (device: _Device): string => device.kind;
const _deviceIdOf = (device: _Device): string => device.deviceId;
const _labelOf = (device: _Device): string => device.label;
const _groupIdOf = (device: _Device): string => device.groupId;
const _accessors = { kindOf: _kindOf, deviceIdOf: _deviceIdOf };
const _signatureAccessors = { ..._accessors, labelOf: _labelOf, groupIdOf: _groupIdOf };

describe('audio_device_preferences', () => {
  test('stored audio device ids trim empty storage values', () => {
    expect(storedAudioDeviceIdFromStorageValue(null)).toBeNull();
    expect(storedAudioDeviceIdFromStorageValue('  ')).toBeNull();
    expect(storedAudioDeviceIdFromStorageValue(' mic_1 ')).toBe('mic_1');
  });

  test('stored audio volume parsing falls back and clamps', () => {
    expect(storedAudioVolumeFromStorageValue(null)).toBe(1);
    expect(storedAudioVolumeFromStorageValue('bad')).toBe(1);
    expect(storedAudioVolumeFromStorageValue('-0.5')).toBe(0);
    expect(storedAudioVolumeFromStorageValue('1.5')).toBe(1);
    expect(storedAudioVolumeFromStorageValue('0.425')).toBe(0.425);
    expect(audioVolumeStorageString(1.2)).toBe('1.000');
    expect(audioVolumeStorageString(0.425)).toBe('0.425');
  });

  test('StoredAudioDevices defaults are empty', () => {
    expect(new StoredAudioDevices().isEmpty).toBe(true);
    expect(new StoredAudioDevices({ inputDeviceId: '' }).isEmpty).toBe(true);
    expect(new StoredAudioDevices({ inputDeviceId: 'mic_1' }).isEmpty).toBe(false);
    expect(new StoredAudioDevices({ outputVolume: 0.8 }).isEmpty).toBe(false);
  });

  test('preferredStoredAudioDeviceFrom uses stored id then default fallback', () => {
    const devices = [
      new _Device('default', 'Default Mic', 'audioinput'),
      new _Device('mic_1', 'Desk Mic', 'audioinput'),
      new _Device('speaker_1', 'Speaker', 'audiooutput'),
    ];

    expect(preferredStoredAudioDeviceFrom(devices, { kind: 'audioinput', storedDeviceId: 'mic_1', ..._accessors })).toBe(
      devices[1],
    );
    expect(
      preferredStoredAudioDeviceFrom(devices, { kind: 'audioinput', storedDeviceId: 'missing', ..._accessors }),
    ).toBe(devices[0]);
    expect(
      preferredStoredAudioDeviceFrom(devices, { kind: 'audiooutput', storedDeviceId: 'missing', ..._accessors }),
    ).toBeNull();
  });

  test('preferredStoredAudioDeviceFrom restores hotplugged device signature', () => {
    const devices = [
      new _Device('speaker_2', 'Desk Speaker', 'audiooutput', 'group_speaker'),
      new _Device('speaker_new', 'USB Headset', 'audiooutput', 'group_headset'),
    ];

    expect(
      preferredStoredAudioDeviceFrom(devices, {
        kind: 'audiooutput',
        storedDeviceId: 'speaker_old',
        storedDeviceLabel: 'USB Headset',
        storedDeviceGroupId: 'group_headset',
        systemDefaultDeviceId: 'speaker_2',
        ..._signatureAccessors,
      }),
    ).toBe(devices[1]);
  });

  test('preferredStoredAudioDeviceFrom avoids ambiguous label-only restoration', () => {
    const devices = [
      new _Device('speaker_1', 'USB Headset', 'audiooutput'),
      new _Device('speaker_2', 'USB Headset', 'audiooutput'),
      new _Device('speaker_3', 'Desk Speaker', 'audiooutput'),
    ];

    expect(
      preferredStoredAudioDeviceFrom(devices, {
        kind: 'audiooutput',
        storedDeviceId: 'speaker_old',
        storedDeviceLabel: 'USB Headset',
        storedDeviceGroupId: null,
        systemDefaultDeviceId: 'speaker_3',
        ..._signatureAccessors,
      }),
    ).toBe(devices[2]);
  });

  test('preferredStoredAudioDeviceFrom restores unique label after group id churn', () => {
    const devices = [
      new _Device('speaker_new', 'USB Headset', 'audiooutput', 'group_new'),
      new _Device('speaker_2', 'Desk Speaker', 'audiooutput', 'group_desk'),
    ];

    expect(
      preferredStoredAudioDeviceFrom(devices, {
        kind: 'audiooutput',
        storedDeviceId: 'speaker_old',
        storedDeviceLabel: 'USB Headset',
        storedDeviceGroupId: 'group_old',
        systemDefaultDeviceId: 'speaker_2',
        ..._signatureAccessors,
      }),
    ).toBe(devices[0]);
  });

  test('group id churn does not restore an ambiguous device label', () => {
    const devices = [
      new _Device('speaker_a', 'USB Headset', 'audiooutput', 'group_a'),
      new _Device('speaker_b', 'USB Headset', 'audiooutput', 'group_b'),
      new _Device('speaker_default', 'Desk Speaker', 'audiooutput', 'group_desk'),
    ];

    expect(
      preferredStoredAudioDeviceFrom(devices, {
        kind: 'audiooutput',
        storedDeviceId: 'speaker_old',
        storedDeviceLabel: 'USB Headset',
        storedDeviceGroupId: 'group_old',
        systemDefaultDeviceId: 'speaker_default',
        ..._signatureAccessors,
      }),
    ).toBe(devices[2]);
  });

  test('preferredStoredAudioDeviceFrom prefers stored, then system default, then synthetic default', () => {
    const devices = [
      new _Device('default', 'Default Mic', 'audioinput'),
      new _Device('mic_1', 'Desk Mic', 'audioinput'),
      new _Device('mic_2', 'Room Mic', 'audioinput'),
    ];

    // A saved preference still wins over the OS default.
    expect(
      preferredStoredAudioDeviceFrom(devices, {
        kind: 'audioinput',
        storedDeviceId: 'mic_1',
        systemDefaultDeviceId: 'mic_2',
        ..._accessors,
      }),
    ).toBe(devices[1]);
    // No saved preference: follow the system default the native channel
    // reported, ahead of the synthetic "default" device.
    expect(
      preferredStoredAudioDeviceFrom(devices, {
        kind: 'audioinput',
        storedDeviceId: null,
        systemDefaultDeviceId: 'mic_2',
        ..._accessors,
      }),
    ).toBe(devices[2]);
    // No saved preference and no usable system default: fall back to the
    // synthetic "default" device (the Windows path).
    expect(
      preferredStoredAudioDeviceFrom(devices, {
        kind: 'audioinput',
        storedDeviceId: null,
        systemDefaultDeviceId: 'missing',
        ..._accessors,
      }),
    ).toBe(devices[0]);
  });

  test('selectStoredAudioDeviceIfChanged skips selected devices', async () => {
    const device = new _Device('mic_1', 'Desk Mic', 'audioinput');
    let selects = 0;

    const selected = await selectStoredAudioDeviceIfChanged({
      selected: device,
      device,
      select: async () => {
        selects += 1;
      },
      ..._accessors,
    });

    expect(selected).toBe(device);
    expect(selects).toBe(0);
  });

  test('selectStoredAudioDeviceIfChanged reports failed selection as null', async () => {
    const selected = new _Device('mic_1', 'Desk Mic', 'audioinput');
    const next = new _Device('mic_2', 'Room Mic', 'audioinput');

    await expect(
      selectStoredAudioDeviceIfChanged({
        selected,
        device: next,
        select: async () => {
          throw new Error('denied');
        },
        ..._accessors,
      }),
    ).resolves.toBeNull();
  });
});
