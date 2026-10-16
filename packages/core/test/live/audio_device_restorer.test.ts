import { expect, test } from 'vitest';

import { AudioDeviceInfo } from '../../src/app/audio_device_info';
import { AudioDeviceStore, StoredAudioDevices } from '../../src/app/audio_device_store';
import { restoreStoredAudioDevices } from '../../src/live/audio_device_restorer';
import { LiveAudioDeviceService } from '../../src/live/audio_device_service';
import { device } from './live_test_helpers';

// Port of test/unit/live/audio_device_restorer_test.dart.
class _FakeAudioDeviceStore extends AudioDeviceStore {
  constructor(
    private readonly _output: { outputDeviceId?: string | null; outputDeviceLabel?: string; outputDeviceGroupId?: string } = {},
  ) {
    super();
  }

  writtenOutputDeviceId: string | null = null;
  writtenOutputDeviceLabel: string | null = null;
  writtenOutputDeviceGroupId: string | null = null;

  override async read(): Promise<StoredAudioDevices> {
    return new StoredAudioDevices({
      inputDeviceId: 'missing_input',
      outputDeviceId: this._output.outputDeviceId === undefined ? 'speaker_1' : this._output.outputDeviceId,
      outputDeviceLabel: this._output.outputDeviceLabel,
      outputDeviceGroupId: this._output.outputDeviceGroupId,
    });
  }

  override async writeOutputDevicePreference(opts: {
    deviceId: string;
    label?: string | null;
    groupId?: string | null;
  }): Promise<void> {
    this.writtenOutputDeviceId = opts.deviceId;
    this.writtenOutputDeviceLabel = opts.label ?? null;
    this.writtenOutputDeviceGroupId = opts.groupId ?? null;
  }
}

class _FakeLiveAudioDeviceService extends LiveAudioDeviceService {
  constructor(
    private readonly _devices: AudioDeviceInfo[],
    opts: { selectedInput?: AudioDeviceInfo; selectedOutput?: AudioDeviceInfo } = {},
  ) {
    super();
    this._selectedInput = opts.selectedInput ?? null;
    this._selectedOutput = opts.selectedOutput ?? null;
  }

  private _selectedInput: AudioDeviceInfo | null;
  private _selectedOutput: AudioDeviceInfo | null;
  enumerateCalls = 0;
  inputSelects = 0;
  outputSelects = 0;

  override async enumerateDevices(): Promise<AudioDeviceInfo[]> {
    this.enumerateCalls += 1;
    return this._devices;
  }

  override get selectedAudioInput(): AudioDeviceInfo | null {
    return this._selectedInput;
  }

  override get selectedAudioOutput(): AudioDeviceInfo | null {
    return this._selectedOutput;
  }

  get selectedOutput(): AudioDeviceInfo | null {
    return this._selectedOutput;
  }

  override async selectAudioInput(selected: AudioDeviceInfo): Promise<void> {
    this.inputSelects += 1;
    this._selectedInput = selected;
  }

  override async selectAudioOutput(selected: AudioDeviceInfo): Promise<void> {
    this.outputSelects += 1;
    this._selectedOutput = selected;
  }
}

test('restoreStoredAudioDevices uses injected audio device service', async () => {
  const defaultInput = device({ deviceId: 'default', label: 'System Default', kind: 'audioinput', groupId: 'group_default' });
  const speaker = device({ deviceId: 'speaker_1', label: 'Speaker', kind: 'audiooutput', groupId: 'group_speaker' });
  const service = new _FakeLiveAudioDeviceService([defaultInput, speaker], { selectedInput: defaultInput });

  const restored = await restoreStoredAudioDevices(new _FakeAudioDeviceStore(), { audioDevices: service });

  expect(restored.input).toBe(defaultInput);
  expect(restored.output).toBe(speaker);
  expect(service.enumerateCalls).toBe(1);
  expect(service.inputSelects).toBe(0);
  expect(service.outputSelects).toBe(1);
  expect(service.selectedOutput).toBe(speaker);
});

test('restoreStoredAudioDevices falls back to system default output', async () => {
  const speakerOne = device({ deviceId: 'speaker_1', label: 'Speaker 1', kind: 'audiooutput', groupId: 'group_speaker_1' });
  const speakerTwo = device({ deviceId: 'speaker_2', label: 'Speaker 2', kind: 'audiooutput', groupId: 'group_speaker_2' });
  const service = new _FakeLiveAudioDeviceService([speakerOne, speakerTwo], { selectedOutput: speakerOne });

  const restored = await restoreStoredAudioDevices(new _FakeAudioDeviceStore({ outputDeviceId: 'missing_output' }), {
    audioDevices: service,
    systemDefaultOutputId: 'speaker_2',
  });

  expect(restored.output).toBe(speakerTwo);
  expect(service.outputSelects).toBe(1);
  expect(service.selectedOutput).toBe(speakerTwo);
});

test('restoreStoredAudioDevices restores output by signature after hotplug', async () => {
  const speaker = device({ deviceId: 'speaker_2', label: 'Desk Speaker', kind: 'audiooutput', groupId: 'group_speaker' });
  const repluggedHeadset = device({
    deviceId: 'headset_new',
    label: 'USB Headset',
    kind: 'audiooutput',
    groupId: 'group_headset',
  });
  const service = new _FakeLiveAudioDeviceService([speaker, repluggedHeadset], { selectedOutput: speaker });

  const store = new _FakeAudioDeviceStore({
    outputDeviceId: 'headset_old',
    outputDeviceLabel: 'USB Headset',
    outputDeviceGroupId: 'group_headset',
  });

  const restored = await restoreStoredAudioDevices(store, { audioDevices: service, systemDefaultOutputId: 'speaker_2' });

  expect(restored.output).toBe(repluggedHeadset);
  expect(service.outputSelects).toBe(1);
  expect(service.selectedOutput).toBe(repluggedHeadset);
  expect(store.writtenOutputDeviceId).toBe('headset_new');
  expect(store.writtenOutputDeviceLabel).toBe('USB Headset');
  expect(store.writtenOutputDeviceGroupId).toBe('group_headset');
});
