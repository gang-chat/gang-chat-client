import {
  type AudioDeviceGroupIdOf,
  type AudioDeviceIdOf,
  type AudioDeviceKindOf,
  type AudioDeviceLabelOf,
  isSameAudioDevice,
} from './audio_device_display';
import { normalizedAudioVolume } from './audio_levels';
import { defaultScreenShareFrameRate, defaultScreenShareMaxHeight } from '../live/screen_share_quality';

/** Default music-box and screen-share listening volume. */
export const defaultAudioVolume = 0.5;

/**
 * Default microphone and playback gain: unity, the slider at its end. The
 * Flutter client started both at [defaultAudioVolume], which halved the voice
 * that was sent and everything that was heard.
 */
export const defaultInputVolume = 1.0;
export const defaultOutputVolume = 1.0;

export class StoredAudioDevices {
  constructor(
    opts: {
      inputDeviceId?: string | null;
      inputDeviceLabel?: string | null;
      inputDeviceGroupId?: string | null;
      outputDeviceId?: string | null;
      outputDeviceLabel?: string | null;
      outputDeviceGroupId?: string | null;
      inputVolume?: number;
      outputVolume?: number;
      inputVolumeBoost?: boolean;
      outputVolumeBoost?: boolean;
      musicBoxVolume?: number;
      screenShareVolume?: number;
      screenShareMaxHeight?: number;
      screenShareFrameRate?: number;
    } = {},
  ) {
    this.inputDeviceId = opts.inputDeviceId ?? null;
    this.inputDeviceLabel = opts.inputDeviceLabel ?? null;
    this.inputDeviceGroupId = opts.inputDeviceGroupId ?? null;
    this.outputDeviceId = opts.outputDeviceId ?? null;
    this.outputDeviceLabel = opts.outputDeviceLabel ?? null;
    this.outputDeviceGroupId = opts.outputDeviceGroupId ?? null;
    this.inputVolume = opts.inputVolume ?? defaultInputVolume;
    this.outputVolume = opts.outputVolume ?? defaultOutputVolume;
    this.inputVolumeBoost = opts.inputVolumeBoost ?? false;
    this.outputVolumeBoost = opts.outputVolumeBoost ?? false;
    this.musicBoxVolume = opts.musicBoxVolume ?? defaultAudioVolume;
    this.screenShareVolume = opts.screenShareVolume ?? defaultAudioVolume;
    this.screenShareMaxHeight = opts.screenShareMaxHeight ?? defaultScreenShareMaxHeight;
    this.screenShareFrameRate = opts.screenShareFrameRate ?? defaultScreenShareFrameRate;
  }

  readonly inputDeviceId: string | null;
  readonly inputDeviceLabel: string | null;
  readonly inputDeviceGroupId: string | null;
  readonly outputDeviceId: string | null;
  readonly outputDeviceLabel: string | null;
  readonly outputDeviceGroupId: string | null;
  readonly inputVolume: number;
  readonly outputVolume: number;

  /** The 增强 switches: the gain is the volume × [audioVolumeBoostFactor]. */
  readonly inputVolumeBoost: boolean;
  readonly outputVolumeBoost: boolean;

  /**
   * Local listening volume for the music box bot's audio track. Independent of
   * [outputVolume] — it scales only the `__musicbox__` participant.
   */
  readonly musicBoxVolume: number;

  /**
   * Local listening volume for remote screen-share audio. Independent of
   * [outputVolume], which scales ordinary voice tracks.
   */
  readonly screenShareVolume: number;

  /**
   * Target max height (px) for the local screen share — one of
   * [screenShareHeightOptions]. [defaultScreenShareMaxHeight] is the balanced
   * resolution; lower values cap what we publish to save bandwidth.
   */
  readonly screenShareMaxHeight: number;

  /**
   * Selected publisher frame-rate cap. Platforms may clamp this to their
   * capture ceiling (Android currently caps it at 30 FPS).
   */
  readonly screenShareFrameRate: number;

  get isEmpty(): boolean {
    return (
      _isBlank(this.inputDeviceId) &&
      _isBlank(this.inputDeviceLabel) &&
      _isBlank(this.inputDeviceGroupId) &&
      _isBlank(this.outputDeviceId) &&
      _isBlank(this.outputDeviceLabel) &&
      _isBlank(this.outputDeviceGroupId) &&
      this.inputVolume === defaultInputVolume &&
      this.outputVolume === defaultOutputVolume &&
      !this.inputVolumeBoost &&
      !this.outputVolumeBoost &&
      this.musicBoxVolume === defaultAudioVolume &&
      this.screenShareVolume === defaultAudioVolume &&
      this.screenShareMaxHeight === defaultScreenShareMaxHeight &&
      this.screenShareFrameRate === defaultScreenShareFrameRate
    );
  }
}

function _isBlank(value: string | null): boolean {
  return value == null || value.length === 0;
}

export class RestoredAudioDevices<T> {
  constructor(opts: { input?: T | null; output?: T | null } = {}) {
    this.input = opts.input ?? null;
    this.output = opts.output ?? null;
  }

  readonly input: T | null;
  readonly output: T | null;
}

export function preferredStoredAudioDeviceFrom<T>(
  devices: readonly T[],
  opts: {
    kind: string;
    storedDeviceId: string | null;
    kindOf: AudioDeviceKindOf<T>;
    deviceIdOf: AudioDeviceIdOf<T>;
    storedDeviceLabel?: string | null;
    storedDeviceGroupId?: string | null;
    labelOf?: AudioDeviceLabelOf<T> | null;
    groupIdOf?: AudioDeviceGroupIdOf<T> | null;
    systemDefaultDeviceId?: string | null;
  },
): T | null {
  const { kind, storedDeviceId, kindOf, deviceIdOf } = opts;
  const storedDevice = storedPreferredAudioDeviceFrom(devices, {
    kind,
    storedDeviceId,
    storedDeviceLabel: opts.storedDeviceLabel ?? null,
    storedDeviceGroupId: opts.storedDeviceGroupId ?? null,
    kindOf,
    deviceIdOf,
    labelOf: opts.labelOf ?? null,
    groupIdOf: opts.groupIdOf ?? null,
  });
  if (storedDevice != null) return storedDevice;

  // macOS never enumerates a synthetic "default" device, so the OS-selected
  // device is identified by an explicit id from the native channel. Prefer it
  // when there is no local preference yet so the picker follows the system.
  const systemDefault = storedAudioDeviceFrom(devices, {
    kind,
    deviceId: opts.systemDefaultDeviceId ?? null,
    kindOf,
    deviceIdOf,
  });
  if (systemDefault != null) return systemDefault;

  // WebRTC exposes the OS-selected device as the synthetic "default" device
  // on Windows. Use it whenever there is no local preference yet, or when the
  // saved device is temporarily unavailable.
  return storedAudioDeviceFrom(devices, { kind, deviceId: 'default', kindOf, deviceIdOf });
}

export function storedPreferredAudioDeviceFrom<T>(
  devices: readonly T[],
  opts: {
    kind: string;
    storedDeviceId: string | null;
    storedDeviceLabel: string | null;
    storedDeviceGroupId: string | null;
    kindOf: AudioDeviceKindOf<T>;
    deviceIdOf: AudioDeviceIdOf<T>;
    labelOf: AudioDeviceLabelOf<T> | null;
    groupIdOf: AudioDeviceGroupIdOf<T> | null;
  },
): T | null {
  const storedDevice = storedAudioDeviceFrom(devices, {
    kind: opts.kind,
    deviceId: opts.storedDeviceId,
    kindOf: opts.kindOf,
    deviceIdOf: opts.deviceIdOf,
  });
  if (storedDevice != null) return storedDevice;

  return storedAudioDeviceBySignatureFrom(devices, {
    kind: opts.kind,
    label: opts.storedDeviceLabel,
    groupId: opts.storedDeviceGroupId,
    kindOf: opts.kindOf,
    labelOf: opts.labelOf,
    groupIdOf: opts.groupIdOf,
  });
}

export function storedAudioDeviceFrom<T>(
  devices: readonly T[],
  opts: { kind: string; deviceId: string | null; kindOf: AudioDeviceKindOf<T>; deviceIdOf: AudioDeviceIdOf<T> },
): T | null {
  const { kind, deviceId, kindOf, deviceIdOf } = opts;
  if (deviceId == null || deviceId.length === 0) return null;
  for (const device of devices) {
    if (kindOf(device) === kind && deviceIdOf(device) === deviceId) {
      return device;
    }
  }
  return null;
}

export function storedAudioDeviceBySignatureFrom<T>(
  devices: readonly T[],
  opts: {
    kind: string;
    label: string | null;
    groupId: string | null;
    kindOf: AudioDeviceKindOf<T>;
    labelOf: AudioDeviceLabelOf<T> | null;
    groupIdOf: AudioDeviceGroupIdOf<T> | null;
  },
): T | null {
  const { kind, kindOf, labelOf, groupIdOf } = opts;
  const storedLabel = _normalizedAudioDeviceSignaturePart(opts.label);
  const storedGroupId = _normalizedAudioDeviceSignaturePart(opts.groupId);
  if (storedLabel == null && storedGroupId == null) return null;
  if (storedLabel != null && labelOf == null) return null;
  if (storedGroupId != null && groupIdOf == null) return null;

  const matches: T[] = [];
  const uniqueLabelFallbacks: T[] = [];
  for (const device of devices) {
    if (kindOf(device) !== kind) continue;
    const deviceLabel = labelOf == null ? null : _normalizedAudioDeviceSignaturePart(labelOf(device));
    const deviceGroupId = groupIdOf == null ? null : _normalizedAudioDeviceSignaturePart(groupIdOf(device));
    const labelMatches = storedLabel != null && deviceLabel === storedLabel;
    const groupMatches = storedGroupId != null && deviceGroupId === storedGroupId;

    if (labelMatches) {
      uniqueLabelFallbacks.push(device);
    }
    if (storedGroupId != null) {
      if (groupMatches && (storedLabel == null || labelMatches)) {
        matches.push(device);
      }
    } else if (labelMatches) {
      matches.push(device);
    }
  }
  if (matches.length === 1) return matches[0]!;
  // Some platforms change endpoint ids/group ids across Bluetooth profile
  // transitions or driver reinstalls. A unique label is safe to restore; an
  // ambiguous label still falls back to the system default.
  if (matches.length === 0 && storedGroupId != null && uniqueLabelFallbacks.length === 1) {
    return uniqueLabelFallbacks[0]!;
  }
  return null;
}

export async function selectStoredAudioDeviceIfChanged<T>(opts: {
  selected: T | null;
  device: T;
  select: (device: T) => Promise<void>;
  kindOf: AudioDeviceKindOf<T>;
  deviceIdOf: AudioDeviceIdOf<T>;
}): Promise<T | null> {
  const { selected, device, select, kindOf, deviceIdOf } = opts;
  if (isSameAudioDevice(selected, device, { kindOf, deviceIdOf })) {
    return device;
  }
  try {
    await select(device);
    return device;
  } catch {
    return null;
  }
}

export function storedAudioDeviceIdFromStorageValue(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed == null || trimmed.length === 0 ? null : trimmed;
}

export function storedAudioDeviceSignatureFromStorageValue(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed == null || trimmed.length === 0 ? null : trimmed;
}

export function storedAudioVolumeFromStorageValue(value: string | null | undefined): number {
  const parsed = _tryParseDouble(value ?? '');
  if (parsed == null) return 1.0;
  return normalizedAudioVolume(parsed);
}

export function audioVolumeStorageString(volume: number): string {
  return normalizedAudioVolume(volume).toFixed(3);
}

function _normalizedAudioDeviceSignaturePart(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  if (trimmed == null || trimmed.length === 0) return null;
  return trimmed.toLowerCase();
}

/** Dart `double.tryParse`: decimal / exponent notation only, surrounding whitespace ignored. */
function _tryParseDouble(value: string): number | null {
  const trimmed = value.trim();
  if (!/^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$|^[+-]?(Infinity|NaN)$/.test(trimmed)) return null;
  return Number(trimmed);
}
