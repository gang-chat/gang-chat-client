import { expect, test } from 'vitest';

import { Completer, delay, withTimeout } from '../../src/lib/async';
import { StreamController } from '../../src/lib/event_stream';
import { AudioDeviceInfo } from '../../src/app/audio_device_info';
import {
  AudioDeviceRebinder,
  PreferredLiveAudioDevices,
  preferredLiveAudioDevices,
} from '../../src/live/audio_device_rebinder';
import { LiveAudioDeviceService } from '../../src/live/audio_device_service';
import { SystemAudioDevices } from '../../src/live/system_audio_devices';
import { FakeAudioDeviceStore, device, waitFor } from './live_test_helpers';

// Port of test/unit/live/audio_device_rebinder_test.dart.
class _FakeAudioDeviceService extends LiveAudioDeviceService {
  constructor(
    private readonly _devices: AudioDeviceInfo[],
    private readonly _error: Error | null = null,
  ) {
    super();
  }

  enumerations = 0;

  override async enumerateDevices(): Promise<AudioDeviceInfo[]> {
    this.enumerations += 1;
    if (this._error != null) throw this._error;
    return this._devices;
  }
}

class _FakeSystemAudioDevices extends SystemAudioDevices {
  constructor(
    private readonly _inputDeviceId: string | null,
    private readonly _outputDeviceId: string | null,
  ) {
    super({ supported: false });
  }

  override async currentInputDeviceId(): Promise<string | null> {
    return this._inputDeviceId;
  }

  override async currentOutputDeviceId(): Promise<string | null> {
    return this._outputDeviceId;
  }
}

test('resolves input and output from one device enumeration', async () => {
  const service = new _FakeAudioDeviceService([
    device({ deviceId: 'mic_new', label: 'USB Headset', kind: 'audioinput' }),
    device({ deviceId: 'speaker_new', label: 'USB Headset', kind: 'audiooutput' }),
  ]);
  const system = new _FakeSystemAudioDevices('mic_default', 'speaker_default');

  const resolved = await preferredLiveAudioDevices({
    audioDeviceStore: new FakeAudioDeviceStore({
      inputDeviceId: 'mic_old',
      inputDeviceLabel: 'USB Headset',
      outputDeviceId: 'speaker_old',
      outputDeviceLabel: 'USB Headset',
    }),
    audioDevices: service,
    systemAudio: system,
  });

  expect(service.enumerations).toBe(1);
  expect(resolved.inputDeviceId).toBe('mic_new');
  expect(resolved.outputDeviceId).toBe('speaker_new');
});

test('one device-change burst rebinds both directions once', async () => {
  const changes = new StreamController<void>();
  let resolutions = 0;
  const operations: string[] = [];
  const rebinder = new AudioDeviceRebinder({
    deviceChanges: changes.stream,
    resolvePreferredDevices: async () => {
      resolutions += 1;
      return new PreferredLiveAudioDevices({ inputDeviceId: 'mic_1', outputDeviceId: 'speaker_1' });
    },
    rebindInput: async (id) => {
      operations.push(`input:${id}`);
    },
    selectOutput: async (id) => {
      operations.push(`output:${id}`);
    },
    onOutputRebound: async () => {
      operations.push('routing');
    },
    debounce: 10,
  });
  rebinder.start();

  changes.add();
  changes.add();
  changes.add();
  await waitFor(() => operations.length === 3);

  expect(resolutions).toBe(1);
  expect(operations).toEqual(['input:mic_1', 'output:speaker_1', 'routing']);
  await rebinder.stop();
});

test('refresh is single-flight and retains one trailing refresh', async () => {
  const changes = new StreamController<void>();
  const firstStarted = new Completer<void>();
  const releaseFirst = new Completer<void>();
  let resolutions = 0;
  let activeResolutions = 0;
  let maxActiveResolutions = 0;
  let inputRebinds = 0;
  let outputRebinds = 0;

  const rebinder = new AudioDeviceRebinder({
    deviceChanges: changes.stream,
    resolvePreferredDevices: async () => {
      resolutions += 1;
      activeResolutions += 1;
      if (activeResolutions > maxActiveResolutions) maxActiveResolutions = activeResolutions;
      if (resolutions === 1) {
        firstStarted.complete();
        await releaseFirst.promise;
      }
      activeResolutions -= 1;
      return new PreferredLiveAudioDevices({ inputDeviceId: 'mic', outputDeviceId: 'speaker' });
    },
    rebindInput: async () => {
      inputRebinds += 1;
    },
    selectOutput: async () => {
      outputRebinds += 1;
    },
    onOutputRebound: async () => {},
    debounce: 0,
  });
  rebinder.start();

  changes.add();
  await firstStarted.promise;
  changes.add();
  changes.add();
  await delay(0);
  releaseFirst.complete();
  await waitFor(() => resolutions === 2 && outputRebinds === 2);

  expect(maxActiveResolutions).toBe(1);
  expect(inputRebinds).toBe(2);
  expect(outputRebinds).toBe(2);
  await rebinder.stop();
});

test('long playback event storm stays serialized and recoverable', async () => {
  const changes = new StreamController<void>();
  let resolutions = 0;
  let activeOperations = 0;
  let maxActiveOperations = 0;
  let routingReapplies = 0;

  const operation = async (): Promise<void> => {
    activeOperations += 1;
    if (activeOperations > maxActiveOperations) maxActiveOperations = activeOperations;
    await delay(0);
    activeOperations -= 1;
  };

  const rebinder = new AudioDeviceRebinder({
    deviceChanges: changes.stream,
    resolvePreferredDevices: async () => {
      resolutions += 1;
      await operation();
      return new PreferredLiveAudioDevices({ inputDeviceId: 'mic', outputDeviceId: 'speaker' });
    },
    rebindInput: () => operation(),
    selectOutput: () => operation(),
    onOutputRebound: async () => {
      await operation();
      routingReapplies += 1;
    },
    debounce: 0,
  });
  rebinder.start();

  // Simulates repeated endpoint/profile churn during a long viewing session
  // without making the suite sleep for real minutes.
  for (let cycle = 0; cycle < 100; cycle += 1) {
    changes.add();
    changes.add();
    await waitFor(() => routingReapplies === cycle + 1);
  }

  expect(resolutions).toBe(100);
  expect(routingReapplies).toBe(100);
  expect(maxActiveOperations).toBe(1);
  await rebinder.stop();
});

test('a timed-out refresh does not block a later device change', async () => {
  const changes = new StreamController<void>();
  const firstStarted = new Completer<void>();
  const neverCompletes = new Completer<PreferredLiveAudioDevices>();
  let resolutions = 0;
  const operations: string[] = [];
  const rebinder = new AudioDeviceRebinder({
    deviceChanges: changes.stream,
    resolvePreferredDevices: () => {
      resolutions += 1;
      if (resolutions === 1) {
        firstStarted.complete();
        return neverCompletes.promise;
      }
      return Promise.resolve(
        new PreferredLiveAudioDevices({ inputDeviceId: 'mic_recovered', outputDeviceId: 'speaker_recovered' }),
      );
    },
    rebindInput: async (id) => {
      operations.push(`input:${id}`);
    },
    selectOutput: async (id) => {
      operations.push(`output:${id}`);
    },
    onOutputRebound: async () => {
      operations.push('routing');
    },
    debounce: 0,
    operationTimeout: 20,
  });
  rebinder.start();

  changes.add();
  await firstStarted.promise;
  await delay(30);
  changes.add();
  await waitFor(() => operations.length === 3);

  expect(resolutions).toBe(2);
  expect(operations).toEqual(['input:mic_recovered', 'output:speaker_recovered', 'routing']);
  await rebinder.stop();
});

test('stop cancels an in-flight refresh without waiting for native work', async () => {
  const changes = new StreamController<void>();
  const started = new Completer<void>();
  const neverCompletes = new Completer<PreferredLiveAudioDevices>();
  let inputRebinds = 0;
  const rebinder = new AudioDeviceRebinder({
    deviceChanges: changes.stream,
    resolvePreferredDevices: () => {
      started.complete();
      return neverCompletes.promise;
    },
    rebindInput: async () => {
      inputRebinds += 1;
    },
    selectOutput: async () => {},
    onOutputRebound: async () => {},
    debounce: 0,
    operationTimeout: 30_000,
  });
  rebinder.start();

  changes.add();
  await started.promise;
  await withTimeout(rebinder.stop(), 200);

  expect(inputRebinds).toBe(0);
});

test('output selection failure still reapplies routing and volume', async () => {
  const changes = new StreamController<void>();
  let routingReapplies = 0;
  const rebinder = new AudioDeviceRebinder({
    deviceChanges: changes.stream,
    resolvePreferredDevices: async () => new PreferredLiveAudioDevices({ inputDeviceId: 'mic', outputDeviceId: 'speaker' }),
    rebindInput: async () => {},
    selectOutput: async () => {
      throw new Error('endpoint disappeared');
    },
    onOutputRebound: async () => {
      routingReapplies += 1;
    },
    debounce: 0,
  });
  rebinder.start();

  changes.add();
  await waitFor(() => routingReapplies === 1);
  await rebinder.stop();
});

test('device enumeration failure is not mistaken for an empty topology', async () => {
  const service = new _FakeAudioDeviceService([], new Error('native enumeration timed out'));

  await expect(
    preferredLiveAudioDevices({
      audioDeviceStore: null,
      audioDevices: service,
      systemAudio: new _FakeSystemAudioDevices('default_mic', 'default_speaker'),
    }),
  ).rejects.toThrow('native enumeration timed out');
});
