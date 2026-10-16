import { describe, expect, test } from 'vitest';

import {
  audioDeviceListApplied,
  audioDeviceListLoadFailed,
  audioDeviceListLoadStarted,
  audioDeviceSelectionFailed,
  audioDeviceSelectionStarted,
  audioDeviceSelectionSucceeded,
  audioInputDeviceSelectedEffects,
  audioInputLevelChanged,
  audioInputTestFailed,
  audioInputTestStarted,
  audioInputTestStopped,
  audioInputVolumeChanged,
  audioInputVolumeChangedEffects,
  audioOutputDeviceSelectedEffects,
  audioOutputLevelChanged,
  audioOutputTestFailed,
  audioOutputTestStarted,
  audioOutputTestStopped,
  audioOutputVolumeChanged,
  audioOutputVolumeChangedEffects,
  audioStoredVolumesApplied,
} from '../../src/app/audio_device_state';

class _Device {
  constructor(
    readonly deviceId: string,
    readonly label: string,
    readonly kind: string,
  ) {}
}

const _kindOf = (device: _Device): string => device.kind;
const _deviceIdOf = (device: _Device): string => device.deviceId;

function _applied(opts: {
  devices: _Device[];
  restoredInput?: _Device | null;
  restoredOutput?: _Device | null;
  hardwareInput?: _Device | null;
  hardwareOutput?: _Device | null;
  currentInput?: _Device | null;
  currentOutput?: _Device | null;
  error?: string | null;
  systemDefaultInput?: _Device | null;
  systemDefaultOutput?: _Device | null;
}) {
  return audioDeviceListApplied<_Device>({
    devices: opts.devices,
    restoredInput: opts.restoredInput ?? null,
    restoredOutput: opts.restoredOutput ?? null,
    hardwareInput: opts.hardwareInput ?? null,
    hardwareOutput: opts.hardwareOutput ?? null,
    currentInput: opts.currentInput ?? null,
    currentOutput: opts.currentOutput ?? null,
    kindOf: _kindOf,
    deviceIdOf: _deviceIdOf,
    error: opts.error ?? null,
    systemDefaultInput: opts.systemDefaultInput,
    systemDefaultOutput: opts.systemDefaultOutput,
  });
}

describe('audio_device_state', () => {
  test('audioDeviceListLoadStarted preserves devices and clears error', () => {
    const input = new _Device('mic_1', 'Mic', 'audioinput');
    const output = new _Device('speaker_1', 'Speaker', 'audiooutput');
    const patch = audioDeviceListLoadStarted({
      inputs: [input],
      outputs: [output],
      selectedInput: input,
      selectedOutput: output,
    });

    expect(patch.inputs).toEqual([input]);
    expect(patch.outputs).toEqual([output]);
    expect(patch.selectedInput).toBe(input);
    expect(patch.selectedOutput).toBe(output);
    expect(patch.loading).toBe(true);
    expect(patch.error).toBeNull();
  });

  test('audioDeviceListLoadFailed preserves device state and reports error', () => {
    const input = new _Device('mic_1', 'Mic', 'audioinput');
    const patch = audioDeviceListLoadFailed<_Device>({
      inputs: [input],
      outputs: [],
      selectedInput: input,
      selectedOutput: null,
      failure: 'device denied',
    });

    expect(patch.inputs).toEqual([input]);
    expect(patch.outputs).toEqual([]);
    expect(patch.selectedInput).toBe(input);
    expect(patch.selectedOutput).toBeNull();
    expect(patch.loading).toBe(false);
    expect(patch.error).toBe('操作失败，请稍后重试');
  });

  test('audioDeviceListApplied filters and selects from priority candidates', () => {
    const inputOne = new _Device('mic_1', 'Mic 1', 'audioinput');
    const inputTwo = new _Device('mic_2', 'Mic 2', 'audioinput');
    const outputOne = new _Device('speaker_1', 'Speaker 1', 'audiooutput');
    const outputTwo = new _Device('speaker_2', 'Speaker 2', 'audiooutput');

    const patch = _applied({
      devices: [inputOne, inputTwo, outputOne, outputTwo],
      restoredInput: inputTwo,
      hardwareInput: inputOne,
      hardwareOutput: outputTwo,
      currentOutput: outputOne,
      error: 'previous warning',
    });

    expect(patch.inputs).toEqual([inputOne, inputTwo]);
    expect(patch.outputs).toEqual([outputOne, outputTwo]);
    expect(patch.selectedInput).toBe(inputTwo);
    expect(patch.selectedOutput).toBe(outputTwo);
    expect(patch.loading).toBe(false);
    expect(patch.error).toBe('previous warning');
  });

  test('audioDeviceListApplied follows system default input when none restored', () => {
    const inputOne = new _Device('mic_1', 'Mic 1', 'audioinput');
    const inputTwo = new _Device('mic_2', 'Mic 2', 'audioinput');

    const patch = _applied({ devices: [inputOne, inputTwo], hardwareInput: inputOne, systemDefaultInput: inputTwo });

    // The system default sits ahead of the hardware/current device, so it is
    // chosen even though the hardware reports mic_1.
    expect(patch.selectedInput).toBe(inputTwo);
  });

  test('audioDeviceListApplied keeps restored input over system default', () => {
    const inputOne = new _Device('mic_1', 'Mic 1', 'audioinput');
    const inputTwo = new _Device('mic_2', 'Mic 2', 'audioinput');

    const patch = _applied({ devices: [inputOne, inputTwo], restoredInput: inputOne, systemDefaultInput: inputTwo });

    // An explicit saved preference still wins over the OS default.
    expect(patch.selectedInput).toBe(inputOne);
  });

  test('audioDeviceListApplied follows system default output when none restored', () => {
    const outputOne = new _Device('speaker_1', 'Speaker 1', 'audiooutput');
    const outputTwo = new _Device('speaker_2', 'Speaker 2', 'audiooutput');

    const patch = _applied({ devices: [outputOne, outputTwo], hardwareOutput: outputOne, systemDefaultOutput: outputTwo });

    expect(patch.selectedOutput).toBe(outputTwo);
  });

  test('audioDeviceListApplied keeps restored output over system default', () => {
    const outputOne = new _Device('speaker_1', 'Speaker 1', 'audiooutput');
    const outputTwo = new _Device('speaker_2', 'Speaker 2', 'audiooutput');

    const patch = _applied({ devices: [outputOne, outputTwo], restoredOutput: outputOne, systemDefaultOutput: outputTwo });

    expect(patch.selectedOutput).toBe(outputOne);
  });

  test('audioDeviceSelectionStarted marks selected device busy', () => {
    const input = new _Device('mic_1', 'Mic', 'audioinput');
    const output = new _Device('speaker_1', 'Speaker', 'audiooutput');
    const patch = audioDeviceSelectionStarted({
      device: input,
      selectedInput: input,
      selectedOutput: output,
      kindOf: _kindOf,
      deviceIdOf: _deviceIdOf,
    });

    expect(patch.selectedInput).toBe(input);
    expect(patch.selectedOutput).toBe(output);
    expect(patch.busyDeviceId).toBe('audioinput:mic_1');
    expect(patch.error).toBeNull();
  });

  test('audioDeviceSelectionSucceeded updates only matching kind', () => {
    const oldInput = new _Device('mic_1', 'Mic 1', 'audioinput');
    const newOutput = new _Device('speaker_2', 'Speaker 2', 'audiooutput');

    const patch = audioDeviceSelectionSucceeded<_Device>({
      device: newOutput,
      selectedInput: oldInput,
      selectedOutput: null,
      kindOf: _kindOf,
      storageFailure: 'storage denied',
    });

    expect(patch.selectedInput).toBe(oldInput);
    expect(patch.selectedOutput).toBe(newOutput);
    expect(patch.busyDeviceId).toBeNull();
    expect(patch.error).toBe('无法保存音频设备偏好');
  });

  test('audioDeviceSelectionFailed preserves selection and clears busy', () => {
    const input = new _Device('mic_1', 'Mic', 'audioinput');
    const patch = audioDeviceSelectionFailed<_Device>({
      selectedInput: input,
      selectedOutput: null,
      failure: 'select failed',
    });

    expect(patch.selectedInput).toBe(input);
    expect(patch.selectedOutput).toBeNull();
    expect(patch.busyDeviceId).toBeNull();
    expect(patch.error).toBe('操作失败，请稍后重试');
  });

  test('audio device selection effects describe follow-up test work', () => {
    let effects = audioInputDeviceSelectedEffects({ wasTestingInput: true, wasTestingOutput: false });
    expect(effects.restartInputTest).toBe(true);
    expect(effects.restartOutputTest).toBe(false);

    effects = audioInputDeviceSelectedEffects({ wasTestingInput: false, wasTestingOutput: true });
    expect(effects.restartInputTest).toBe(false);
    expect(effects.restartOutputTest).toBe(true);

    effects = audioOutputDeviceSelectedEffects();
    expect(effects.restartInputTest).toBe(false);
    expect(effects.restartOutputTest).toBe(false);
  });

  test('audioStoredVolumesApplied normalizes stored volumes', () => {
    const patch = audioStoredVolumesApplied({ inputVolume: 1.2, outputVolume: -0.2 });

    expect(patch.inputVolume).toBe(0);
    expect(patch.outputVolume).toBe(0);
  });

  test('audio volume changes enforce live mute coupling', () => {
    const inputPatch = audioInputVolumeChanged({ inputVolume: 0.42, outputVolume: 0.8 });
    const restoredOutputPatch = audioInputVolumeChanged({ inputVolume: 0.42, outputVolume: 0, restoreOutputVolume: 0.73 });
    const outputPatch = audioOutputVolumeChanged({ inputVolume: 0.42, outputVolume: 1.4 });
    const mutedOutputPatch = audioOutputVolumeChanged({ inputVolume: 0.42, outputVolume: 0 });

    expect(inputPatch.inputVolume).toBe(0.42);
    expect(inputPatch.outputVolume).toBe(0.8);
    expect(restoredOutputPatch.inputVolume).toBe(0.42);
    expect(restoredOutputPatch.outputVolume).toBe(0.73);
    expect(outputPatch.inputVolume).toBe(0.42);
    expect(outputPatch.outputVolume).toBe(1);
    expect(mutedOutputPatch.inputVolume).toBe(0);
    expect(mutedOutputPatch.outputVolume).toBe(0);
  });

  test('audio volume effects describe persistence and local test updates', () => {
    let effects = audioInputVolumeChangedEffects({ inputVolume: 1.2, hasInputTestTrack: true });
    expect(effects.deviceKind).toBe('audioinput');
    expect(effects.volume).toBe(1);
    expect(effects.updateInputTestTrack).toBe(true);
    expect(effects.updateOutputTestPlayback).toBe(false);

    effects = audioOutputVolumeChangedEffects({ outputVolume: -0.2, hasOutputTestPlayback: true });
    expect(effects.deviceKind).toBe('audiooutput');
    expect(effects.volume).toBe(0);
    expect(effects.updateInputTestTrack).toBe(false);
    expect(effects.updateOutputTestPlayback).toBe(true);
  });

  test('audioInputTestStarted resets input level and clears error', () => {
    const patch = audioInputTestStarted({ testingOutput: true, outputLevel: 0.6 });

    expect(patch.testingInput).toBe(true);
    expect(patch.testingOutput).toBe(true);
    expect(patch.inputLevel).toBe(0);
    expect(patch.outputLevel).toBe(0.6);
    expect(patch.error).toBeNull();
  });

  test('audioInputTestStopped and failed clear input test state', () => {
    const stopped = audioInputTestStopped({ testingOutput: true, outputLevel: 0.6, error: 'previous warning' });
    const failed = audioInputTestFailed({ testingOutput: false, outputLevel: 0.2, failure: 'input failed' });

    expect(stopped.testingInput).toBe(false);
    expect(stopped.testingOutput).toBe(true);
    expect(stopped.inputLevel).toBe(0);
    expect(stopped.outputLevel).toBe(0.6);
    expect(stopped.error).toBe('previous warning');
    expect(failed.testingInput).toBe(false);
    expect(failed.testingOutput).toBe(false);
    expect(failed.inputLevel).toBe(0);
    expect(failed.outputLevel).toBe(0.2);
    expect(failed.error).toBe('操作失败，请稍后重试');
  });

  test('audioOutputTestStarted resets output level and clears error', () => {
    const patch = audioOutputTestStarted({ testingInput: true, inputLevel: 0.5 });

    expect(patch.testingInput).toBe(true);
    expect(patch.testingOutput).toBe(true);
    expect(patch.inputLevel).toBe(0.5);
    expect(patch.outputLevel).toBe(0);
    expect(patch.error).toBeNull();
  });

  test('audioOutputTestStopped and failed clear output test state', () => {
    const stopped = audioOutputTestStopped({ testingInput: true, inputLevel: 0.7, error: 'previous warning' });
    const failed = audioOutputTestFailed({ testingInput: false, inputLevel: 0.1, failure: 'output failed' });

    expect(stopped.testingInput).toBe(true);
    expect(stopped.testingOutput).toBe(false);
    expect(stopped.inputLevel).toBe(0.7);
    expect(stopped.outputLevel).toBe(0);
    expect(stopped.error).toBe('previous warning');
    expect(failed.testingInput).toBe(false);
    expect(failed.testingOutput).toBe(false);
    expect(failed.inputLevel).toBe(0.1);
    expect(failed.outputLevel).toBe(0);
    expect(failed.error).toBe('操作失败，请稍后重试');
  });

  test('audio test level changes apply current volume', () => {
    const inputPatch = audioInputLevelChanged({
      level: 0.8,
      inputVolume: 0.5,
      testingOutput: true,
      outputLevel: 0.3,
      error: null,
    });
    const outputPatch = audioOutputLevelChanged({
      level: 0.8,
      outputVolume: 1.5,
      testingInput: true,
      inputLevel: 0.4,
      error: 'previous warning',
    });

    expect(inputPatch.testingInput).toBe(true);
    expect(inputPatch.testingOutput).toBe(true);
    expect(inputPatch.inputLevel).toBe(0.4);
    expect(inputPatch.outputLevel).toBe(0.3);
    expect(inputPatch.error).toBeNull();
    expect(outputPatch.testingInput).toBe(true);
    expect(outputPatch.testingOutput).toBe(true);
    expect(outputPatch.inputLevel).toBe(0.4);
    expect(outputPatch.outputLevel).toBe(1);
    expect(outputPatch.error).toBe('previous warning');
  });
});
