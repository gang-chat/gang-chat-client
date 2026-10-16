/**
 * Shared helpers for the live-layer tests (ports of the Dart tests' local
 * `_waitFor` and fakes).
 */
import { delay } from '../../src/lib/async';
import { AudioDeviceInfo } from '../../src/app/audio_device_info';
import { AudioDeviceStore, StoredAudioDevices } from '../../src/app/audio_device_store';
import type { MediaDevicesLike } from '../../src/live/live_hardware';

/** Polls [condition] every millisecond for up to two seconds. */
export async function waitFor(condition: () => boolean, message = 'Timed out waiting for asynchronous operation.'): Promise<void> {
  const deadline = Date.now() + 2000;
  while (!condition()) {
    if (Date.now() > deadline) throw new Error(message);
    await delay(1);
  }
}

export function device(opts: { deviceId: string; label: string; kind: string; groupId?: string }): AudioDeviceInfo {
  return new AudioDeviceInfo(opts);
}

export class FakeAudioDeviceStore extends AudioDeviceStore {
  constructor(private readonly _stored: ConstructorParameters<typeof StoredAudioDevices>[0] = {}) {
    super();
  }

  override async read(): Promise<StoredAudioDevices> {
    return new StoredAudioDevices(this._stored);
  }
}

type _RawDevice = { deviceId: string; label: string; kind: MediaDeviceKind; groupId: string };

/** Scriptable `navigator.mediaDevices`. */
export class FakeMediaDevices implements MediaDevicesLike {
  devices: _RawDevice[] = [];
  enumerateError: Error | null = null;
  enumerations = 0;
  private readonly _listeners = new Set<() => void>();

  async enumerateDevices(): Promise<_RawDevice[]> {
    this.enumerations += 1;
    if (this.enumerateError != null) throw this.enumerateError;
    return this.devices.map((d) => ({ ...d }));
  }

  addEventListener(_type: 'devicechange', listener: () => void): void {
    this._listeners.add(listener);
  }

  removeEventListener(_type: 'devicechange', listener: () => void): void {
    this._listeners.delete(listener);
  }

  get listenerCount(): number {
    return this._listeners.size;
  }

  /** Replaces the device list and fires `devicechange`. */
  change(devices: _RawDevice[]): void {
    this.devices = devices;
    for (const listener of [...this._listeners]) listener();
  }
}

export function raw(kind: MediaDeviceKind, deviceId: string, label: string, groupId = ''): _RawDevice {
  return { kind, deviceId, label, groupId };
}
