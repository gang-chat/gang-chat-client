import { describe, expect, test } from 'vitest';

import {
  audioDeviceBusy,
  audioDeviceKey,
  audioDeviceKeyOf,
  audioDeviceLabels,
  audioDevicePreferenceSaveFailureMessage,
  audioDevicesByKind,
  audioInputTestTooltip,
  audioOutputTestTooltip,
  canStartAudioDeviceSelection,
  isSameAudioDevice,
  selectedAudioDeviceFrom,
} from '../../src/app/audio_device_display';

class _Device {
  constructor(
    readonly deviceId: string,
    readonly label: string,
    readonly kind: string,
  ) {}
}

const _kindOf = (device: _Device): string => device.kind;
const _deviceIdOf = (device: _Device): string => device.deviceId;
const _labelOf = (device: _Device): string => device.label;

describe('audio_device_display', () => {
  test('audioDeviceKey is stable from kind and id', () => {
    expect(audioDeviceKey({ kind: 'audioinput', deviceId: 'mic_1' })).toBe('audioinput:mic_1');
    expect(
      audioDeviceKeyOf(new _Device('mic_1', 'Mic', 'audioinput'), { kindOf: _kindOf, deviceIdOf: _deviceIdOf }),
    ).toBe('audioinput:mic_1');
  });

  test('audio device selection helpers expose busy state', () => {
    const device = new _Device('mic_1', 'Mic', 'audioinput');

    expect(canStartAudioDeviceSelection(null)).toBe(true);
    expect(canStartAudioDeviceSelection('audioinput:mic_1')).toBe(false);
    expect(audioDeviceBusy(device, 'audioinput:mic_1', { kindOf: _kindOf, deviceIdOf: _deviceIdOf })).toBe(true);
    expect(audioDeviceBusy(device, 'audiooutput:mic_1', { kindOf: _kindOf, deviceIdOf: _deviceIdOf })).toBe(false);
    expect(audioDevicePreferenceSaveFailureMessage('denied')).toBe('无法保存音频设备偏好');
  });

  test('isSameAudioDevice compares kind and device id', () => {
    const device = new _Device('mic_1', 'Mic', 'audioinput');
    expect(isSameAudioDevice(device, device, { kindOf: _kindOf, deviceIdOf: _deviceIdOf })).toBe(true);
    expect(
      isSameAudioDevice(new _Device('mic_1', 'Speaker', 'audiooutput'), device, {
        kindOf: _kindOf,
        deviceIdOf: _deviceIdOf,
      }),
    ).toBe(false);
  });

  test('audioDeviceLabels normalizes empty and duplicate labels', () => {
    const labels = audioDeviceLabels(
      [
        new _Device('default', '', 'audioinput'),
        new _Device('communications', '', 'audioinput'),
        new _Device('mic_1', '', 'audioinput'),
        new _Device('mic_2', 'Desk Mic', 'audioinput'),
        new _Device('mic_3', 'Desk Mic', 'audioinput'),
      ],
      { fallbackLabel: '麦克风', labelOf: _labelOf, deviceIdOf: _deviceIdOf },
    );

    expect(labels).toEqual(['系统默认', '通信设备', '麦克风 3', 'Desk Mic', 'Desk Mic #2']);
  });

  test('audioDevicesByKind filters devices by WebRTC kind', () => {
    const inputs = audioDevicesByKind(
      [new _Device('mic_1', 'Mic', 'audioinput'), new _Device('speaker_1', 'Speaker', 'audiooutput')],
      'audioinput',
      { kindOf: _kindOf },
    );

    expect(inputs.map((device) => device.deviceId)).toEqual(['mic_1']);
  });

  test('selectedAudioDeviceFrom picks first matching candidate then fallback', () => {
    const devices = [new _Device('mic_1', 'Mic 1', 'audioinput'), new _Device('mic_2', 'Mic 2', 'audioinput')];
    const opts = { kindOf: _kindOf, deviceIdOf: _deviceIdOf };

    expect(
      selectedAudioDeviceFrom(
        devices,
        [new _Device('missing', 'Missing', 'audioinput'), new _Device('mic_2', 'Mic 2', 'audioinput')],
        opts,
      )?.deviceId,
    ).toBe('mic_2');
    expect(selectedAudioDeviceFrom<_Device>(devices, [], opts)?.deviceId).toBe('mic_1');
    expect(selectedAudioDeviceFrom<_Device>([], devices, opts)).toBeNull();
  });

  test('audio test tooltips reflect active state', () => {
    expect(audioInputTestTooltip(false)).toBe('测试输入音量');
    expect(audioInputTestTooltip(true)).toBe('停止输入测试');
    expect(audioOutputTestTooltip(false)).toBe('测试输出音量');
    expect(audioOutputTestTooltip(true)).toBe('停止输出测试');
  });
});
