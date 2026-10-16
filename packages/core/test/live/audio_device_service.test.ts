import { expect, test } from 'vitest';

import { delay } from '../../src/lib/async';
import { AudioDeviceInfo } from '../../src/app/audio_device_info';
import { LiveAudioDeviceService, systemAudioOnlyForPlatform } from '../../src/live/audio_device_service';
import { LiveHardware } from '../../src/live/live_hardware';
import { SystemAudioDevices } from '../../src/live/system_audio_devices';
import { FakeMediaDevices, raw, waitFor } from './live_test_helpers';

// Port of test/unit/live/audio_device_service_test.dart.
test('only Windows desktop uses the system-only audio path', () => {
  expect(systemAudioOnlyForPlatform({ isWeb: false, isWindows: true })).toBe(true);
  // macOS keeps its system/WebRTC merge.
  expect(systemAudioOnlyForPlatform({ isWeb: false, isWindows: false })).toBe(false);
  // web/Android keep their WebRTC implementation.
  expect(systemAudioOnlyForPlatform({ isWeb: true, isWindows: false })).toBe(false);
});

test('Windows audio enumeration uses only SystemAudioDevices', async () => {
  const fake = new FakeMediaDevices();
  fake.devices = [raw('audioinput', 'system_mic', 'System Mic'), raw('audiooutput', 'system_speaker', 'System Speaker')];
  const hardware = new LiveHardware({ mediaDevices: null });

  // Pin the Windows path: the system list is the only source.
  const service = new LiveAudioDeviceService({
    systemAudioOnly: true,
    systemAudio: new SystemAudioDevices({ mediaDevices: fake, supported: true }),
    hardware,
  });
  const devices = await service.enumerateDevices();

  expect(fake.enumerations).toBe(1);
  expect(devices.map((d) => `${d.kind}:${d.deviceId}`)).toEqual(['audioinput:system_mic', 'audiooutput:system_speaker']);
});

test('Windows topology event refreshes the system endpoint list', async () => {
  const fake = new FakeMediaDevices();
  fake.devices = [];
  const service = new LiveAudioDeviceService({
    systemAudioOnly: true,
    systemAudio: new SystemAudioDevices({ mediaDevices: fake, supported: true }),
  });
  const emitted: AudioDeviceInfo[][] = [];
  const sub = service.devicesChanged.listen((devices) => emitted.push(devices));
  await delay(0);

  fake.change([raw('audioinput', 'hotplug_mic', 'Hotplug Mic'), raw('audiooutput', 'hotplug_speaker', 'Hotplug Speaker')]);
  await waitFor(() => emitted.length === 1);

  expect(emitted[0]!.map((d) => d.deviceId)).toEqual(['hotplug_mic', 'hotplug_speaker']);
  sub.cancel();
});

test('macOS path merges system endpoints missing from the WebRTC list', async () => {
  const webrtc = new FakeMediaDevices();
  webrtc.devices = [raw('audioinput', 'default', 'Default - Mic'), raw('audioinput', 'mic_1', 'Mic')];
  const system = new FakeMediaDevices();
  system.devices = [raw('audioinput', 'mic_1', 'Mic'), raw('audiooutput', 'speaker_1', 'Speaker')];
  const service = new LiveAudioDeviceService({
    systemAudioOnly: false,
    systemAudio: new SystemAudioDevices({ mediaDevices: system, supported: true }),
    hardware: new LiveHardware({ mediaDevices: webrtc }),
  });

  const devices = await service.enumerateDevices();

  expect(devices.map((d) => `${d.kind}:${d.deviceId}`)).toEqual([
    'audioinput:default',
    'audioinput:mic_1',
    'audiooutput:speaker_1',
  ]);
});

test('selection is remembered and output selections are broadcast', async () => {
  const hardware = new LiveHardware({ mediaDevices: null });
  const service = new LiveAudioDeviceService({ hardware, systemAudioOnly: false });
  const outputs: Array<string | null> = [];
  hardware.audioOutputSelections.listen((selected) => outputs.push(selected?.deviceId ?? null));

  await service.selectAudioInput(new AudioDeviceInfo({ deviceId: 'mic_1', label: 'Mic', kind: 'audioinput' }));
  await service.selectAudioOutput(
    new AudioDeviceInfo({ deviceId: 'speaker_1', label: 'Speaker', kind: 'audiooutput', groupId: 'g1' }),
  );

  expect(service.selectedAudioInput?.deviceId).toBe('mic_1');
  expect(service.selectedAudioOutput?.equals(
    new AudioDeviceInfo({ deviceId: 'speaker_1', label: 'Speaker', kind: 'audiooutput', groupId: 'g1' }),
  )).toBe(true);
  expect(outputs).toEqual(['speaker_1']);
});
