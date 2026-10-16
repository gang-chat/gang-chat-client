import { expect, test } from 'vitest';

import { Completer, delay } from '../../src/lib/async';
import { StreamController } from '../../src/lib/event_stream';
import { AudioDeviceInfo } from '../../src/app/audio_device_info';
import { LiveAudioDeviceService } from '../../src/live/audio_device_service';
import { AudioInputRebinder, preferredLiveInputDeviceId } from '../../src/live/audio_input_rebinder';
import { SystemAudioDevices } from '../../src/live/system_audio_devices';
import { FakeAudioDeviceStore, device } from './live_test_helpers';

// Port of test/unit/live/audio_input_rebinder_test.dart.
test('rebinds input on a device change', async () => {
  const changes = new StreamController<void>();
  const rebound: Array<string | null> = [];

  const rebinder = new AudioInputRebinder({
    deviceChanges: changes.stream,
    currentInputDeviceId: async () => 'mic_1',
    rebindInput: async (id) => {
      rebound.push(id);
    },
    debounce: 10,
  });
  rebinder.start();

  changes.add();
  await delay(30);

  expect(rebound).toEqual(['mic_1']);
  await rebinder.stop();
});

test('coalesces a burst of changes into a single input rebind', async () => {
  const changes = new StreamController<void>();
  let rebounds = 0;

  const rebinder = new AudioInputRebinder({
    deviceChanges: changes.stream,
    currentInputDeviceId: async () => 'headset_mic',
    rebindInput: async () => {
      rebounds += 1;
    },
    debounce: 20,
  });
  rebinder.start();

  changes.add();
  changes.add();
  changes.add();
  await delay(50);

  expect(rebounds).toBe(1);
  await rebinder.stop();
});

test('treats later same-device changes as fresh input rebinds', async () => {
  const changes = new StreamController<void>();
  const rebound: Array<string | null> = [];

  const rebinder = new AudioInputRebinder({
    deviceChanges: changes.stream,
    currentInputDeviceId: async () => 'headset_mic',
    rebindInput: async (id) => {
      rebound.push(id);
    },
    debounce: 10,
  });
  rebinder.start();

  changes.add();
  await delay(30);
  changes.add();
  await delay(30);

  expect(rebound).toEqual(['headset_mic', 'headset_mic']);
  await rebinder.stop();
});

test('queues changes that arrive while an input rebind is running', async () => {
  const changes = new StreamController<void>();
  const firstStarted = new Completer<void>();
  const finishFirst = new Completer<void>();
  let attempts = 0;

  const rebinder = new AudioInputRebinder({
    deviceChanges: changes.stream,
    currentInputDeviceId: async () => 'headset_mic',
    rebindInput: async () => {
      attempts += 1;
      if (attempts === 1) {
        firstStarted.complete();
        await finishFirst.promise;
      }
    },
    debounce: 10,
  });
  rebinder.start();

  changes.add();
  await firstStarted.promise;
  changes.add();
  await delay(30);
  finishFirst.complete();
  await delay(30);

  expect(attempts).toBe(2);
  await rebinder.stop();
});

test('a failed input rebind is swallowed and does not block later ones', async () => {
  const changes = new StreamController<void>();
  let attempts = 0;

  const rebinder = new AudioInputRebinder({
    deviceChanges: changes.stream,
    currentInputDeviceId: async () => 'headset_mic',
    rebindInput: async () => {
      attempts += 1;
      if (attempts === 1) throw new Error('device vanished');
    },
    debounce: 10,
  });
  rebinder.start();

  changes.add();
  await delay(30);
  changes.add();
  await delay(30);

  expect(attempts).toBe(2);
  await rebinder.stop();
});

test('stop cancels a pending input rebind', async () => {
  const changes = new StreamController<void>();
  let rebounds = 0;

  const rebinder = new AudioInputRebinder({
    deviceChanges: changes.stream,
    currentInputDeviceId: async () => 'mic_1',
    rebindInput: async () => {
      rebounds += 1;
    },
    debounce: 30,
  });
  rebinder.start();

  changes.add();
  await rebinder.stop();
  await delay(50);

  expect(rebounds).toBe(0);
});

class _FakeLiveAudioDeviceService extends LiveAudioDeviceService {
  constructor(
    private readonly _devices: AudioDeviceInfo[],
    private readonly _enumerateError: Error | null = null,
  ) {
    super();
  }

  override async enumerateDevices(): Promise<AudioDeviceInfo[]> {
    if (this._enumerateError != null) throw this._enumerateError;
    return this._devices;
  }
}

class _FakeSystemAudioDevices extends SystemAudioDevices {
  constructor(private readonly _inputDeviceId: string | null) {
    super({ supported: false });
  }

  override async currentInputDeviceId(): Promise<string | null> {
    return this._inputDeviceId;
  }
}

test('preferredLiveInputDeviceId restores hotplugged input by signature', async () => {
  const defaultMic = device({ deviceId: 'default_mic', label: 'Default Mic', kind: 'audioinput', groupId: 'group_default' });
  const repluggedHeadset = device({
    deviceId: 'headset_new',
    label: 'Bluetooth Headset',
    kind: 'audioinput',
    groupId: 'group_headset',
  });

  await expect(
    preferredLiveInputDeviceId({
      audioDeviceStore: new FakeAudioDeviceStore({
        inputDeviceId: 'headset_old',
        inputDeviceLabel: 'Bluetooth Headset',
        inputDeviceGroupId: 'group_headset',
      }),
      audioDevices: new _FakeLiveAudioDeviceService([defaultMic, repluggedHeadset]),
      systemAudio: new _FakeSystemAudioDevices('default_mic'),
    }),
  ).resolves.toBe('headset_new');
});

test('preferredLiveInputDeviceId falls back to system default input', async () => {
  const defaultMic = device({ deviceId: 'mic_2', label: 'Room Mic', kind: 'audioinput' });

  await expect(
    preferredLiveInputDeviceId({
      audioDeviceStore: new FakeAudioDeviceStore({ inputDeviceId: 'missing_mic' }),
      audioDevices: new _FakeLiveAudioDeviceService([defaultMic]),
      systemAudio: new _FakeSystemAudioDevices('mic_2'),
    }),
  ).resolves.toBe('mic_2');
});

test('preferredLiveInputDeviceId falls back when enumeration fails', async () => {
  await expect(
    preferredLiveInputDeviceId({
      audioDeviceStore: new FakeAudioDeviceStore({ inputDeviceId: 'headset_mic' }),
      audioDevices: new _FakeLiveAudioDeviceService([], new Error('enumerate failed')),
      systemAudio: new _FakeSystemAudioDevices('default_mic'),
    }),
  ).resolves.toBe('default_mic');
});
