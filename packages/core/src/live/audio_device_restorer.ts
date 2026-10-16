/**
 * Port of `lib/src/live/audio_device_restorer.dart`.
 */
import {
  AudioDeviceInfo,
  audioDeviceInfoGroupId,
  audioDeviceInfoId,
  audioDeviceInfoKind,
  audioDeviceInfoLabel,
} from '../app/audio_device_info';
import {
  RestoredAudioDevices,
  preferredStoredAudioDeviceFrom,
  selectStoredAudioDeviceIfChanged,
  storedPreferredAudioDeviceFrom,
  type AudioDeviceStore,
} from '../app/audio_device_store';
import { LiveAudioDeviceService } from './audio_device_service';

export async function restoreStoredAudioDevices(
  store: AudioDeviceStore,
  opts: {
    audioDevices?: LiveAudioDeviceService;
    devices?: AudioDeviceInfo[] | null;
    systemDefaultInputId?: string | null;
    systemDefaultOutputId?: string | null;
  } = {},
): Promise<RestoredAudioDevices<AudioDeviceInfo>> {
  const audioDevices = opts.audioDevices ?? new LiveAudioDeviceService();
  const stored = await store.read();
  const availableDevices = opts.devices ?? (await audioDevices.enumerateDevices());
  const storedInputPreference = storedPreferredAudioDeviceFrom(availableDevices, {
    kind: 'audioinput',
    storedDeviceId: stored.inputDeviceId,
    storedDeviceLabel: stored.inputDeviceLabel,
    storedDeviceGroupId: stored.inputDeviceGroupId,
    kindOf: audioDeviceInfoKind,
    deviceIdOf: audioDeviceInfoId,
    labelOf: audioDeviceInfoLabel,
    groupIdOf: audioDeviceInfoGroupId,
  });
  const storedOutputPreference = storedPreferredAudioDeviceFrom(availableDevices, {
    kind: 'audiooutput',
    storedDeviceId: stored.outputDeviceId,
    storedDeviceLabel: stored.outputDeviceLabel,
    storedDeviceGroupId: stored.outputDeviceGroupId,
    kindOf: audioDeviceInfoKind,
    deviceIdOf: audioDeviceInfoId,
    labelOf: audioDeviceInfoLabel,
    groupIdOf: audioDeviceInfoGroupId,
  });
  const input = preferredStoredAudioDeviceFrom(availableDevices, {
    kind: 'audioinput',
    storedDeviceId: stored.inputDeviceId,
    storedDeviceLabel: stored.inputDeviceLabel,
    storedDeviceGroupId: stored.inputDeviceGroupId,
    kindOf: audioDeviceInfoKind,
    deviceIdOf: audioDeviceInfoId,
    labelOf: audioDeviceInfoLabel,
    groupIdOf: audioDeviceInfoGroupId,
    systemDefaultDeviceId: opts.systemDefaultInputId ?? null,
  });
  const output = preferredStoredAudioDeviceFrom(availableDevices, {
    kind: 'audiooutput',
    storedDeviceId: stored.outputDeviceId,
    storedDeviceLabel: stored.outputDeviceLabel,
    storedDeviceGroupId: stored.outputDeviceGroupId,
    kindOf: audioDeviceInfoKind,
    deviceIdOf: audioDeviceInfoId,
    labelOf: audioDeviceInfoLabel,
    groupIdOf: audioDeviceInfoGroupId,
    systemDefaultDeviceId: opts.systemDefaultOutputId ?? null,
  });

  const restoredInput =
    input == null
      ? null
      : await selectStoredAudioDeviceIfChanged({
          selected: audioDevices.selectedAudioInput,
          device: input,
          select: (device) => audioDevices.selectAudioInput(device),
          kindOf: audioDeviceInfoKind,
          deviceIdOf: audioDeviceInfoId,
        });
  const restoredOutput =
    output == null
      ? null
      : await selectStoredAudioDeviceIfChanged({
          selected: audioDevices.selectedAudioOutput,
          device: output,
          select: (device) => audioDevices.selectAudioOutput(device),
          kindOf: audioDeviceInfoKind,
          deviceIdOf: audioDeviceInfoId,
        });

  await _rememberRestoredPreferences(store, {
    input: storedInputPreference == null ? null : restoredInput,
    output: storedOutputPreference == null ? null : restoredOutput,
  });

  return new RestoredAudioDevices({ input: restoredInput, output: restoredOutput });
}

async function _rememberRestoredPreferences(
  store: AudioDeviceStore,
  opts: { input: AudioDeviceInfo | null; output: AudioDeviceInfo | null },
): Promise<void> {
  const { input, output } = opts;
  try {
    if (input != null) {
      await store.writeInputDevicePreference({
        deviceId: input.deviceId,
        label: input.label,
        groupId: input.groupId,
      });
    }
    if (output != null) {
      await store.writeOutputDevicePreference({
        deviceId: output.deviceId,
        label: output.label,
        groupId: output.groupId,
      });
    }
  } catch {
    // Signature backfill is a convenience; routing already succeeded.
  }
}
