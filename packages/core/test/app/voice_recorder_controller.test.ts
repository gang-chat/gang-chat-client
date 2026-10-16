import { afterEach, describe, expect, test } from 'vitest';

import {
  type Amplitude,
  AudioEncoder,
  type AudioRecorder,
  type RecordConfig,
  VoiceRecorderController,
  VoiceRecorderPermissionException,
  setAudioRecorderFactory,
} from '../../src/app/voice_recorder_controller';
import { type EventStream, StreamController } from '../../src/lib/event_stream';
import { installFakePlatform } from '../support/fake_platform';

class _FakeRecorder implements AudioRecorder {
  permission = true;
  aacSupported = true;
  started: Array<{ config: RecordConfig; path: string }> = [];
  cancelled = 0;
  disposed = 0;
  stopResult: string | null = null;
  readonly amplitude = new StreamController<Amplitude>();

  async hasPermission(): Promise<boolean> {
    return this.permission;
  }

  async isEncoderSupported(encoder: AudioEncoder): Promise<boolean> {
    return encoder === AudioEncoder.aacLc ? this.aacSupported : true;
  }

  async start(config: RecordConfig, opts: { path: string }): Promise<void> {
    this.started.push({ config, path: opts.path });
  }

  async stop(): Promise<string | null> {
    return this.stopResult ?? this.started[this.started.length - 1]?.path ?? null;
  }

  async cancel(): Promise<void> {
    this.cancelled += 1;
  }

  onAmplitudeChanged(interval: number): EventStream<Amplitude> {
    return this.amplitude.stream;
  }

  async dispose(): Promise<void> {
    this.disposed += 1;
  }
}

afterEach(() => setAudioRecorderFactory(null));

describe('VoiceRecorderController', () => {
  test('records AAC into the temp directory on native hosts', async () => {
    const fake = installFakePlatform({ os: 'windows' });
    const recorder = new _FakeRecorder();
    const controller = new VoiceRecorderController({ recorder });

    await controller.start();

    expect(recorder.started).toHaveLength(1);
    expect(recorder.started[0]!.config.encoder).toBe(AudioEncoder.aacLc);
    expect(recorder.started[0]!.config.bitRate).toBe(64000);
    expect(recorder.started[0]!.path).toMatch(/^\/tmp\/voice_\d+\.m4a$/);
    expect(controller.clipMimeType).toBe('audio/mp4');
    expect(controller.clipExtension).toBe('m4a');

    const path = (await controller.stop())!;
    fake.files.put(path, [1, 2, 3]);
    expect([...(await controller.readClip(path))]).toEqual([1, 2, 3]);
    await controller.discardClip(path);
    expect(await fake.files.exists(path)).toBe(false);
  });

  test('falls back to 16 kHz WAV on native hosts without an AAC encoder', async () => {
    installFakePlatform({ os: 'linux' });
    const recorder = new _FakeRecorder();
    recorder.aacSupported = false;
    const controller = new VoiceRecorderController({ recorder });

    await controller.start();

    expect(recorder.started[0]!.config.encoder).toBe(AudioEncoder.wav);
    expect(recorder.started[0]!.config.sampleRate).toBe(16000);
    expect(recorder.started[0]!.path).toMatch(/^\/tmp\/voice_\d+\.wav$/);
    expect(controller.clipExtension).toBe('wav');
  });

  test('cancel deletes the temp clip', async () => {
    const fake = installFakePlatform({ os: 'linux' });
    const recorder = new _FakeRecorder();
    const controller = new VoiceRecorderController({ recorder });

    await controller.start();
    const path = recorder.started[0]!.path;
    fake.files.put(path, [9]);
    await controller.cancel();

    expect(recorder.cancelled).toBe(1);
    expect(await fake.files.exists(path)).toBe(false);
  });

  test('browsers without AAC record WAV', async () => {
    installFakePlatform({ os: 'web' });
    const recorder = new _FakeRecorder();
    recorder.aacSupported = false;
    const controller = new VoiceRecorderController({ recorder });

    await controller.start();

    expect(recorder.started[0]!.config.encoder).toBe(AudioEncoder.wav);
    expect(recorder.started[0]!.config.sampleRate).toBe(16000);
    expect(recorder.started[0]!.path).toMatch(/^voice_\d+\.wav$/);
    expect(controller.clipMimeType).toBe('audio/wav');
  });

  test('a denied microphone throws the permission exception', async () => {
    installFakePlatform();
    const recorder = new _FakeRecorder();
    recorder.permission = false;
    const controller = new VoiceRecorderController({ recorder });

    await expect(controller.start()).rejects.toBeInstanceOf(VoiceRecorderPermissionException);
    expect(String(new VoiceRecorderPermissionException())).toBe('麦克风权限被拒绝');
  });

  test('the host recorder factory is used lazily', async () => {
    installFakePlatform();
    const controller = new VoiceRecorderController();
    // Disposing before any use needs no recorder.
    await controller.dispose();

    expect(() => controller.hasPermission()).toThrow('当前平台不支持录音');

    const recorder = new _FakeRecorder();
    let created = 0;
    setAudioRecorderFactory(() => {
      created += 1;
      return recorder;
    });
    const hosted = new VoiceRecorderController();
    expect(created).toBe(0);
    expect(await hosted.hasPermission()).toBe(true);
    expect(await hosted.hasPermission()).toBe(true);
    expect(created).toBe(1);
    await hosted.dispose();
    expect(recorder.disposed).toBe(1);
  });
});
