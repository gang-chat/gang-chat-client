import {
  type AudioDeviceIdOf,
  type AudioDeviceKindOf,
  audioDeviceKeyOf,
  audioDevicePreferenceSaveFailureMessage,
  audioDevicesByKind,
  selectedAudioDeviceFrom,
} from './audio_device_display';
import { defaultAudioVolume, defaultOutputVolume } from './audio_device_preferences';
import { normalizedAudioVolume } from './audio_levels';
import { userFacingErrorMessage } from './error_display';

export class AudioDeviceListPatch<T> {
  constructor(opts: {
    inputs: T[];
    outputs: T[];
    selectedInput: T | null;
    selectedOutput: T | null;
    loading: boolean;
    error: string | null;
  }) {
    this.inputs = opts.inputs;
    this.outputs = opts.outputs;
    this.selectedInput = opts.selectedInput;
    this.selectedOutput = opts.selectedOutput;
    this.loading = opts.loading;
    this.error = opts.error;
  }

  readonly inputs: T[];
  readonly outputs: T[];
  readonly selectedInput: T | null;
  readonly selectedOutput: T | null;
  readonly loading: boolean;
  readonly error: string | null;
}

export class AudioDeviceSelectionPatch<T> {
  constructor(opts: {
    selectedInput: T | null;
    selectedOutput: T | null;
    busyDeviceId: string | null;
    error: string | null;
  }) {
    this.selectedInput = opts.selectedInput;
    this.selectedOutput = opts.selectedOutput;
    this.busyDeviceId = opts.busyDeviceId;
    this.error = opts.error;
  }

  readonly selectedInput: T | null;
  readonly selectedOutput: T | null;
  readonly busyDeviceId: string | null;
  readonly error: string | null;
}

export class AudioDeviceSelectionEffects {
  constructor(opts: { restartInputTest: boolean; restartOutputTest: boolean }) {
    this.restartInputTest = opts.restartInputTest;
    this.restartOutputTest = opts.restartOutputTest;
  }

  readonly restartInputTest: boolean;
  readonly restartOutputTest: boolean;
}

export class AudioVolumePatch {
  constructor(opts: { inputVolume: number; outputVolume: number }) {
    this.inputVolume = opts.inputVolume;
    this.outputVolume = opts.outputVolume;
  }

  readonly inputVolume: number;
  readonly outputVolume: number;
}

export class AudioVolumeEffects {
  constructor(opts: {
    deviceKind: string;
    volume: number;
    updateInputTestTrack: boolean;
    updateOutputTestPlayback: boolean;
  }) {
    this.deviceKind = opts.deviceKind;
    this.volume = opts.volume;
    this.updateInputTestTrack = opts.updateInputTestTrack;
    this.updateOutputTestPlayback = opts.updateOutputTestPlayback;
  }

  readonly deviceKind: string;
  readonly volume: number;
  readonly updateInputTestTrack: boolean;
  readonly updateOutputTestPlayback: boolean;
}

export class AudioTestStatePatch {
  constructor(opts: {
    testingInput: boolean;
    testingOutput: boolean;
    inputLevel: number;
    outputLevel: number;
    error: string | null;
  }) {
    this.testingInput = opts.testingInput;
    this.testingOutput = opts.testingOutput;
    this.inputLevel = opts.inputLevel;
    this.outputLevel = opts.outputLevel;
    this.error = opts.error;
  }

  readonly testingInput: boolean;
  readonly testingOutput: boolean;
  readonly inputLevel: number;
  readonly outputLevel: number;
  readonly error: string | null;
}

export function audioDeviceListLoadStarted<T>(opts: {
  inputs: T[];
  outputs: T[];
  selectedInput: T | null;
  selectedOutput: T | null;
}): AudioDeviceListPatch<T> {
  return new AudioDeviceListPatch<T>({
    inputs: opts.inputs,
    outputs: opts.outputs,
    selectedInput: opts.selectedInput,
    selectedOutput: opts.selectedOutput,
    loading: true,
    error: null,
  });
}

export function audioDeviceListLoadFailed<T>(opts: {
  inputs: T[];
  outputs: T[];
  selectedInput: T | null;
  selectedOutput: T | null;
  failure: unknown;
}): AudioDeviceListPatch<T> {
  return new AudioDeviceListPatch<T>({
    inputs: opts.inputs,
    outputs: opts.outputs,
    selectedInput: opts.selectedInput,
    selectedOutput: opts.selectedOutput,
    loading: false,
    error: userFacingErrorMessage(opts.failure),
  });
}

export function audioDeviceListApplied<T>(opts: {
  devices: T[];
  restoredInput: T | null;
  restoredOutput: T | null;
  hardwareInput: T | null;
  hardwareOutput: T | null;
  currentInput: T | null;
  currentOutput: T | null;
  kindOf: AudioDeviceKindOf<T>;
  deviceIdOf: AudioDeviceIdOf<T>;
  error: string | null;
  systemDefaultInput?: T | null;
  systemDefaultOutput?: T | null;
}): AudioDeviceListPatch<T> {
  const { kindOf, deviceIdOf } = opts;
  const inputs = audioDevicesByKind(opts.devices, 'audioinput', { kindOf });
  const outputs = audioDevicesByKind(opts.devices, 'audiooutput', { kindOf });
  return new AudioDeviceListPatch<T>({
    inputs,
    outputs,
    // System defaults sit between the saved preference and the hardware/current
    // device so that, absent an explicit choice, the picker follows the OS.
    selectedInput: selectedAudioDeviceFrom(
      inputs,
      [opts.restoredInput, opts.systemDefaultInput, opts.hardwareInput, opts.currentInput],
      { kindOf, deviceIdOf },
    ),
    selectedOutput: selectedAudioDeviceFrom(
      outputs,
      [opts.restoredOutput, opts.systemDefaultOutput, opts.hardwareOutput, opts.currentOutput],
      { kindOf, deviceIdOf },
    ),
    loading: false,
    error: opts.error,
  });
}

export function audioDeviceSelectionStarted<T>(opts: {
  device: T;
  selectedInput: T | null;
  selectedOutput: T | null;
  kindOf: AudioDeviceKindOf<T>;
  deviceIdOf: AudioDeviceIdOf<T>;
}): AudioDeviceSelectionPatch<T> {
  return new AudioDeviceSelectionPatch<T>({
    selectedInput: opts.selectedInput,
    selectedOutput: opts.selectedOutput,
    busyDeviceId: audioDeviceKeyOf(opts.device, { kindOf: opts.kindOf, deviceIdOf: opts.deviceIdOf }),
    error: null,
  });
}

export function audioDeviceSelectionSucceeded<T>(opts: {
  device: T;
  selectedInput: T | null;
  selectedOutput: T | null;
  kindOf: AudioDeviceKindOf<T>;
  storageFailure: unknown;
}): AudioDeviceSelectionPatch<T> {
  const { device, kindOf, storageFailure } = opts;
  return new AudioDeviceSelectionPatch<T>({
    selectedInput: kindOf(device) === 'audioinput' ? device : opts.selectedInput,
    selectedOutput: kindOf(device) === 'audiooutput' ? device : opts.selectedOutput,
    busyDeviceId: null,
    error: storageFailure == null ? null : audioDevicePreferenceSaveFailureMessage(storageFailure),
  });
}

export function audioDeviceSelectionFailed<T>(opts: {
  selectedInput: T | null;
  selectedOutput: T | null;
  failure: unknown;
}): AudioDeviceSelectionPatch<T> {
  return new AudioDeviceSelectionPatch<T>({
    selectedInput: opts.selectedInput,
    selectedOutput: opts.selectedOutput,
    busyDeviceId: null,
    error: userFacingErrorMessage(opts.failure),
  });
}

export function audioInputDeviceSelectedEffects(opts: {
  wasTestingInput: boolean;
  wasTestingOutput: boolean;
}): AudioDeviceSelectionEffects {
  return new AudioDeviceSelectionEffects({
    restartInputTest: opts.wasTestingInput,
    restartOutputTest: opts.wasTestingOutput,
  });
}

export function audioOutputDeviceSelectedEffects(): AudioDeviceSelectionEffects {
  return new AudioDeviceSelectionEffects({ restartInputTest: false, restartOutputTest: false });
}

export function audioStoredVolumesApplied(opts: { inputVolume: number; outputVolume: number }): AudioVolumePatch {
  const output = normalizedAudioVolume(opts.outputVolume);
  return new AudioVolumePatch({
    inputVolume: output === 0 ? 0 : normalizedAudioVolume(opts.inputVolume),
    outputVolume: output,
  });
}

export function audioInputVolumeChanged(opts: {
  inputVolume: number;
  outputVolume: number;
  restoreOutputVolume?: number;
}): AudioVolumePatch {
  const { restoreOutputVolume = defaultOutputVolume } = opts;
  const input = normalizedAudioVolume(opts.inputVolume);
  const output = normalizedAudioVolume(opts.outputVolume);
  return new AudioVolumePatch({
    inputVolume: input,
    outputVolume: input > 0 && output === 0 ? restoredAudioVolume(restoreOutputVolume, defaultOutputVolume) : output,
  });
}

export function audioOutputVolumeChanged(opts: { inputVolume: number; outputVolume: number }): AudioVolumePatch {
  const output = normalizedAudioVolume(opts.outputVolume);
  return new AudioVolumePatch({
    inputVolume: output === 0 ? 0 : normalizedAudioVolume(opts.inputVolume),
    outputVolume: output,
  });
}

/** [fallback] is the volume to come back to when [volume] is 0 (muted). */
export function rememberedAudioVolume(volume: number, fallback: number = defaultAudioVolume): number {
  const normalized = normalizedAudioVolume(volume);
  return normalized > 0 ? normalized : fallback;
}

export function restoredAudioVolume(volume: number, fallback: number = defaultAudioVolume): number {
  const normalized = normalizedAudioVolume(volume);
  return normalized > 0 ? normalized : fallback;
}

export function audioInputVolumeChangedEffects(opts: {
  inputVolume: number;
  hasInputTestTrack: boolean;
}): AudioVolumeEffects {
  return new AudioVolumeEffects({
    deviceKind: 'audioinput',
    volume: normalizedAudioVolume(opts.inputVolume),
    updateInputTestTrack: opts.hasInputTestTrack,
    updateOutputTestPlayback: false,
  });
}

export function audioOutputVolumeChangedEffects(opts: {
  outputVolume: number;
  hasOutputTestPlayback: boolean;
}): AudioVolumeEffects {
  return new AudioVolumeEffects({
    deviceKind: 'audiooutput',
    volume: normalizedAudioVolume(opts.outputVolume),
    updateInputTestTrack: false,
    updateOutputTestPlayback: opts.hasOutputTestPlayback,
  });
}

export function audioInputTestStarted(opts: { testingOutput: boolean; outputLevel: number }): AudioTestStatePatch {
  return new AudioTestStatePatch({
    testingInput: true,
    testingOutput: opts.testingOutput,
    inputLevel: 0,
    outputLevel: opts.outputLevel,
    error: null,
  });
}

export function audioInputTestStopped(opts: {
  testingOutput: boolean;
  outputLevel: number;
  error: string | null;
}): AudioTestStatePatch {
  return new AudioTestStatePatch({
    testingInput: false,
    testingOutput: opts.testingOutput,
    inputLevel: 0,
    outputLevel: opts.outputLevel,
    error: opts.error,
  });
}

export function audioInputTestFailed(opts: {
  testingOutput: boolean;
  outputLevel: number;
  failure: unknown;
}): AudioTestStatePatch {
  return new AudioTestStatePatch({
    testingInput: false,
    testingOutput: opts.testingOutput,
    inputLevel: 0,
    outputLevel: opts.outputLevel,
    error: userFacingErrorMessage(opts.failure),
  });
}

export function audioOutputTestStarted(opts: { testingInput: boolean; inputLevel: number }): AudioTestStatePatch {
  return new AudioTestStatePatch({
    testingInput: opts.testingInput,
    testingOutput: true,
    inputLevel: opts.inputLevel,
    outputLevel: 0,
    error: null,
  });
}

export function audioOutputTestStopped(opts: {
  testingInput: boolean;
  inputLevel: number;
  error: string | null;
}): AudioTestStatePatch {
  return new AudioTestStatePatch({
    testingInput: opts.testingInput,
    testingOutput: false,
    inputLevel: opts.inputLevel,
    outputLevel: 0,
    error: opts.error,
  });
}

export function audioOutputTestFailed(opts: {
  testingInput: boolean;
  inputLevel: number;
  failure: unknown;
}): AudioTestStatePatch {
  return new AudioTestStatePatch({
    testingInput: opts.testingInput,
    testingOutput: false,
    inputLevel: opts.inputLevel,
    outputLevel: 0,
    error: userFacingErrorMessage(opts.failure),
  });
}

export function audioInputLevelChanged(opts: {
  level: number;
  inputVolume: number;
  testingOutput: boolean;
  outputLevel: number;
  error: string | null;
}): AudioTestStatePatch {
  return new AudioTestStatePatch({
    testingInput: true,
    testingOutput: opts.testingOutput,
    inputLevel: normalizedAudioVolume(opts.level * opts.inputVolume),
    outputLevel: opts.outputLevel,
    error: opts.error,
  });
}

export function audioOutputLevelChanged(opts: {
  level: number;
  outputVolume: number;
  testingInput: boolean;
  inputLevel: number;
  error: string | null;
}): AudioTestStatePatch {
  return new AudioTestStatePatch({
    testingInput: opts.testingInput,
    testingOutput: true,
    inputLevel: opts.inputLevel,
    outputLevel: normalizedAudioVolume(opts.level * opts.outputVolume),
    error: opts.error,
  });
}
