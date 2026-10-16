import { expect, test } from 'vitest';

import { delay } from '../../src/lib/async';
import { MacAudioDevices } from '../../src/live/mac_audio_devices';
import { SystemAudioDevices, _defaultEndpointId } from '../../src/live/system_audio_devices';
import { FakeMediaDevices, raw, waitFor } from './live_test_helpers';

// Port of test/unit/live/mac_audio_devices_test.dart. The native
// `gang_chat/audio_devices` channel is replaced by `navigator.mediaDevices`.

function service(fake: FakeMediaDevices): SystemAudioDevices {
  return new SystemAudioDevices({ mediaDevices: fake, supported: true });
}

const settle = async (): Promise<void> => {
  for (let i = 0; i < 5; i++) await delay(0);
};

test('MacAudioDevices is the same class', () => {
  expect(MacAudioDevices).toBe(SystemAudioDevices);
});

test('currentInputDeviceId returns the real endpoint behind the default entry', async () => {
  const fake = new FakeMediaDevices();
  fake.devices = [
    raw('audioinput', 'default', 'Default - Mic 2', 'g2'),
    raw('audioinput', 'mic_1', 'Mic 1', 'g1'),
    raw('audioinput', 'mic_2', 'Mic 2', 'g2'),
  ];
  const devices = service(fake);

  expect(await devices.currentInputDeviceId()).toBe('mic_2');
  expect(await devices.currentDeviceId()).toBe('mic_2');
  await devices.dispose();
});

test('currentOutputDeviceId returns the default output id', async () => {
  const fake = new FakeMediaDevices();
  fake.devices = [
    raw('audiooutput', 'default', 'Default - Speaker 2', 'g2'),
    raw('audiooutput', 'communications', 'Communications - Speaker 1', 'g1'),
    raw('audiooutput', 'speaker_1', 'Speaker 1', 'g1'),
    raw('audiooutput', 'speaker_2', 'Speaker 2', 'g2'),
  ];
  const devices = service(fake);

  expect(await devices.currentOutputDeviceId()).toBe('speaker_2');
  await devices.dispose();
});

test('default resolution falls back to the group, then to the default entry', () => {
  expect(
    _defaultEndpointId([raw('audioinput', 'default', 'Default', 'g1'), raw('audioinput', 'mic_1', 'Mic', 'g1')], 'audioinput'),
  ).toBe('mic_1');
  expect(_defaultEndpointId([raw('audioinput', 'default', 'Default', '')], 'audioinput')).toBe('default');
  expect(_defaultEndpointId([raw('audioinput', 'mic_1', 'Mic', 'g1')], 'audioinput')).toBeNull();
});

test('current device queries swallow failures', async () => {
  const fake = new FakeMediaDevices();
  fake.enumerateError = new Error('unavailable');
  const devices = service(fake);

  expect(await devices.currentInputDeviceId()).toBeNull();
  expect(await devices.currentOutputDeviceId()).toBeNull();
  await devices.dispose();
});

test('enumerateInputs maps the list to audioinput devices', async () => {
  const fake = new FakeMediaDevices();
  fake.devices = [
    raw('audioinput', 'default', 'Default - 内建麦克风'),
    raw('audioinput', '87', '内建麦克风'),
    raw('audioinput', '92', 'USB Mic'),
    // Entries without a deviceId (no permission yet) are dropped.
    raw('audioinput', '', 'broken'),
    raw('audiooutput', '54', 'Speaker'),
  ];
  const devices = service(fake);

  const inputs = await devices.enumerateInputs();
  expect(inputs.map((d) => d.deviceId)).toEqual(['87', '92']);
  expect(inputs.map((d) => d.label)).toEqual(['内建麦克风', 'USB Mic']);
  expect(inputs.every((d) => d.kind === 'audioinput')).toBe(true);
  await devices.dispose();
});

test('enumeration preserves the endpoint group id and labels unnamed devices', async () => {
  const fake = new FakeMediaDevices();
  fake.devices = [raw('audioinput', 'mic_1', 'USB Mic', 'container_1'), raw('audioinput', 'mic_2', '', 'container_2')];
  const devices = service(fake);

  const inputs = await devices.enumerateInputs();
  expect(inputs[0]!.groupId).toBe('container_1');
  expect(inputs[1]!.label).toBe('麦克风');
  await devices.dispose();
});

test('enumerateInputs returns empty when the platform fails', async () => {
  const fake = new FakeMediaDevices();
  fake.enumerateError = new Error('unavailable');
  const devices = service(fake);

  expect(await devices.enumerateInputs()).toEqual([]);
  expect(await devices.enumerateOutputs()).toEqual([]);
  await devices.dispose();
});

test('enumerateOutputs maps the list to audiooutput devices', async () => {
  const fake = new FakeMediaDevices();
  fake.devices = [
    raw('audiooutput', '54', '内建扬声器'),
    raw('audiooutput', '61', 'USB Speaker'),
    raw('audiooutput', '', 'broken'),
  ];
  const devices = service(fake);

  const outputs = await devices.enumerateOutputs();
  expect(outputs.map((d) => d.deviceId)).toEqual(['54', '61']);
  expect(outputs.map((d) => d.label)).toEqual(['内建扬声器', 'USB Speaker']);
  expect(outputs.every((d) => d.kind === 'audiooutput')).toBe(true);
  await devices.dispose();
});

test('enumerateDevices captures inputs and outputs from one snapshot', async () => {
  const fake = new FakeMediaDevices();
  fake.devices = [raw('audiooutput', 'speaker_1', 'Speaker'), raw('audioinput', 'mic_1', 'Mic')];
  const devices = service(fake);

  const all = await devices.enumerateDevices();

  expect(fake.enumerations).toBe(1);
  expect(all.map((d) => d.deviceId)).toEqual(['mic_1', 'speaker_1']);
  await devices.dispose();
});

test('strict enumeration preserves platform failures', async () => {
  const fake = new FakeMediaDevices();
  fake.enumerateError = new Error('device_query_timeout');
  const devices = service(fake);

  await expect(devices.enumerateDevicesOrThrow()).rejects.toThrow('device_query_timeout');
  await devices.dispose();
});

test('unsupported hosts report nothing', async () => {
  const fake = new FakeMediaDevices();
  fake.devices = [raw('audioinput', 'mic_1', 'Mic')];
  const devices = new SystemAudioDevices({ mediaDevices: fake, supported: false });

  expect(await devices.enumerateDevices()).toEqual([]);
  expect(await devices.enumerateDevicesOrThrow()).toEqual([]);
  expect(await devices.currentInputDeviceId()).toBeNull();
  expect(fake.enumerations).toBe(0);
});

test('inputChanges emits the new default when it changes', async () => {
  const fake = new FakeMediaDevices();
  fake.devices = [raw('audioinput', 'default', 'Default - Mic 1', 'g1'), raw('audioinput', 'mic_1', 'Mic 1', 'g1')];
  const devices = service(fake);

  const emissions: Array<string | null> = [];
  const sub = devices.inputChanges.listen((id) => emissions.push(id));
  // Subscribing starts observing the platform.
  expect(fake.listenerCount).toBe(1);
  await settle();

  fake.change([
    raw('audioinput', 'default', 'Default - Mic 3', 'g3'),
    raw('audioinput', 'mic_1', 'Mic 1', 'g1'),
    raw('audioinput', 'mic_3', 'Mic 3', 'g3'),
  ]);
  await waitFor(() => emissions.length === 1);

  expect(emissions).toEqual(['mic_3']);
  sub.cancel();
  await devices.dispose();
  expect(fake.listenerCount).toBe(0);
});

test('outputChanges emits the new default when it changes', async () => {
  const fake = new FakeMediaDevices();
  fake.devices = [raw('audiooutput', 'default', 'Default - Speaker 1', 'g1'), raw('audiooutput', 'speaker_1', 'Speaker 1', 'g1')];
  const devices = service(fake);

  const emissions: Array<string | null> = [];
  const sub = devices.outputChanges.listen((id) => emissions.push(id));
  await settle();

  fake.change([
    raw('audiooutput', 'default', 'Default - Speaker 3', 'g3'),
    raw('audiooutput', 'speaker_1', 'Speaker 1', 'g1'),
    raw('audiooutput', 'speaker_3', 'Speaker 3', 'g3'),
  ]);
  await waitFor(() => emissions.length === 1);

  expect(emissions).toEqual(['speaker_3']);
  sub.cancel();
  await devices.dispose();
});

test('changes merges input, output, and topology events', async () => {
  const fake = new FakeMediaDevices();
  fake.devices = [
    raw('audioinput', 'default', 'Default - Mic 1', 'm1'),
    raw('audioinput', 'mic_1', 'Mic 1', 'm1'),
    raw('audioinput', 'mic_4', 'Mic 4', 'm4'),
    raw('audiooutput', 'default', 'Default - Speaker 1', 's1'),
    raw('audiooutput', 'speaker_1', 'Speaker 1', 's1'),
    raw('audiooutput', 'speaker_4', 'Speaker 4', 's4'),
  ];
  const devices = service(fake);

  const emissions: Array<string | null> = [];
  const sub = devices.changes.listen((id) => emissions.push(id));
  await settle();

  // Default input moves.
  fake.change([
    raw('audioinput', 'default', 'Default - Mic 4', 'm4'),
    raw('audioinput', 'mic_1', 'Mic 1', 'm1'),
    raw('audioinput', 'mic_4', 'Mic 4', 'm4'),
    raw('audiooutput', 'default', 'Default - Speaker 1', 's1'),
    raw('audiooutput', 'speaker_1', 'Speaker 1', 's1'),
    raw('audiooutput', 'speaker_4', 'Speaker 4', 's4'),
  ]);
  await waitFor(() => emissions.length >= 2);
  // The default entry's label changed too, which is a topology change.
  expect(emissions).toEqual(['mic_4', null]);

  // An endpoint appears without a default change.
  fake.change([...fake.devices, raw('audiooutput', 'speaker_5', 'Speaker 5', 's5')]);
  await waitFor(() => emissions.length >= 3);
  expect(emissions).toEqual(['mic_4', null, null]);
  sub.cancel();
  await devices.dispose();
});

test('videoChanges stays separate from audio endpoint changes', async () => {
  const fake = new FakeMediaDevices();
  fake.devices = [raw('audioinput', 'mic_1', 'Mic 1')];
  const devices = service(fake);

  let videoChanges = 0;
  const videoSub = devices.videoChanges.listen(() => (videoChanges += 1));
  const audioEmissions: Array<string | null> = [];
  const audioSub = devices.changes.listen((id) => audioEmissions.push(id));
  await settle();

  fake.change([raw('audioinput', 'mic_1', 'Mic 1'), raw('videoinput', 'cam_1', 'Camera')]);
  await waitFor(() => videoChanges === 1);
  await settle();

  expect(audioEmissions).toEqual([]);
  videoSub.cancel();
  audioSub.cancel();
  await devices.dispose();
});
