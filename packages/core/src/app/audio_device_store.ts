import { StoredAudioDevices } from './audio_device_preferences';
import { defaultParticipantVoiceVolume } from './audio_levels';

export * from './audio_device_preferences';

export class AudioDeviceStore {
  read(): Promise<StoredAudioDevices> {
    throw new Error('AudioDeviceStore.read must be implemented.');
  }

  writeInputDeviceId(deviceId: string): Promise<void> {
    throw new Error('AudioDeviceStore.writeInputDeviceId must be implemented.');
  }

  writeInputDevicePreference(opts: { deviceId: string; label?: string | null; groupId?: string | null }): Promise<void> {
    return this.writeInputDeviceId(opts.deviceId);
  }

  writeOutputDeviceId(deviceId: string): Promise<void> {
    throw new Error('AudioDeviceStore.writeOutputDeviceId must be implemented.');
  }

  writeOutputDevicePreference(opts: { deviceId: string; label?: string | null; groupId?: string | null }): Promise<void> {
    return this.writeOutputDeviceId(opts.deviceId);
  }

  writeInputVolume(volume: number): Promise<void> {
    throw new Error('AudioDeviceStore.writeInputVolume must be implemented.');
  }

  writeOutputVolume(volume: number): Promise<void> {
    throw new Error('AudioDeviceStore.writeOutputVolume must be implemented.');
  }

  writeMusicBoxVolume(volume: number): Promise<void> {
    throw new Error('AudioDeviceStore.writeMusicBoxVolume must be implemented.');
  }

  writeScreenShareVolume(volume: number): Promise<void> {
    throw new Error('AudioDeviceStore.writeScreenShareVolume must be implemented.');
  }

  writeScreenShareMaxHeight(height: number): Promise<void> {
    throw new Error('AudioDeviceStore.writeScreenShareMaxHeight must be implemented.');
  }

  writeScreenShareFrameRate(frameRate: number): Promise<void> {
    throw new Error('AudioDeviceStore.writeScreenShareFrameRate must be implemented.');
  }

  async readParticipantVoiceVolume(userId: string): Promise<number> {
    return defaultParticipantVoiceVolume;
  }

  async writeParticipantVoiceVolume(userId: string, volume: number): Promise<void> {}
}
