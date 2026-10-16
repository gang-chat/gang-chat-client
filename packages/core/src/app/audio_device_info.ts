export class AudioDeviceInfo {
  constructor(opts: { deviceId: string; label: string; kind: string; groupId?: string }) {
    this.deviceId = opts.deviceId;
    this.label = opts.label;
    this.kind = opts.kind;
    this.groupId = opts.groupId ?? '';
  }

  readonly deviceId: string;
  readonly label: string;
  readonly kind: string;
  readonly groupId: string;

  equals(other: unknown): boolean {
    return (
      other instanceof AudioDeviceInfo &&
      other.deviceId === this.deviceId &&
      other.label === this.label &&
      other.kind === this.kind &&
      other.groupId === this.groupId
    );
  }

  toString(): string {
    return `AudioDeviceInfo(deviceId: ${this.deviceId}, label: ${this.label}, kind: ${this.kind})`;
  }
}

export const audioDeviceInfoKind = (device: AudioDeviceInfo): string => device.kind;
export const audioDeviceInfoId = (device: AudioDeviceInfo): string => device.deviceId;
export const audioDeviceInfoLabel = (device: AudioDeviceInfo): string => device.label;
export const audioDeviceInfoGroupId = (device: AudioDeviceInfo): string => device.groupId;
