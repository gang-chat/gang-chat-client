import { HostPlatform } from '../config/host_platform';
import type { EventStream } from '../lib/event_stream';
import { platform } from '../platform/platform';
import {
  kVoiceMessageExtension,
  kVoiceMessageMimeType,
  kVoiceMessageWavExtension,
  kVoiceMessageWavMimeType,
} from './voice_message_display';

export class VoiceRecorderPermissionException extends Error {
  constructor() {
    super('麦克风权限被拒绝');
    this.name = 'VoiceRecorderPermissionException';
  }

  override toString(): string {
    return '麦克风权限被拒绝';
  }
}

/** The encoders of the `record` plugin the controller asks for. */
export enum AudioEncoder {
  aacLc = 'aacLc',
  wav = 'wav',
}

/** Port of the `record` plugin's `RecordConfig`. */
export interface RecordConfig {
  encoder: AudioEncoder;
  bitRate?: number;
  sampleRate?: number;
  numChannels?: number;
}

/** Port of the `record` plugin's `Amplitude` (dBFS values). */
export interface Amplitude {
  current: number;
  max: number;
}

/**
 * The microphone recorder the controller drives — the subset of the Flutter
 * `record` plugin's `AudioRecorder` it used. The host implements it
 * (desktop: getUserMedia + an encoder in the renderer, writing to the temp
 * path through the main process) and installs it with
 * [setAudioRecorderFactory]; tests inject one directly.
 */
export interface AudioRecorder {
  hasPermission(): Promise<boolean>;
  isEncoderSupported(encoder: AudioEncoder): Promise<boolean>;
  /**
   * Starts recording to [path]. On the web, [path] only names the recording
   * and [stop] returns a `blob:` URL instead.
   */
  start(config: RecordConfig, opts: { path: string }): Promise<void>;
  /** Stops recording; returns the clip's path (or blob URL), or null. */
  stop(): Promise<string | null>;
  /** Stops and discards the recording. */
  cancel(): Promise<void>;
  /** [interval] is in milliseconds. */
  onAmplitudeChanged(interval: number): EventStream<Amplitude>;
  dispose(): Promise<void>;
}

let _audioRecorderFactory: (() => AudioRecorder) | null = null;

/**
 * Installs the host's recorder factory, the equivalent of the `record`
 * plugin's `AudioRecorder()` constructor. Call it during app start-up.
 */
export function setAudioRecorderFactory(factory: (() => AudioRecorder) | null): void {
  _audioRecorderFactory = factory;
}

/**
 * Thin wrapper around the host's [AudioRecorder] that captures a single voice
 * clip to a temp file. The recorder phase/timer state machine lives in
 * [voice_message_display]; this class only owns the device side: permission,
 * start/stop/cancel, and reading the captured bytes for upload.
 */
export class VoiceRecorderController {
  constructor(opts: { recorder?: AudioRecorder } = {}) {
    this._injectedRecorder = opts.recorder ?? null;
  }

  private readonly _injectedRecorder: AudioRecorder | null;
  private _createdRecorder: AudioRecorder | null = null;
  private _currentPath: string | null = null;
  private _recordingWav = false;

  // PORT: the Dart controller created the plugin recorder eagerly; here it is
  // created on first use so app services can be built before (or without) a
  // host recorder being installed.
  private get _recorder(): AudioRecorder {
    if (this._injectedRecorder != null) return this._injectedRecorder;
    if (this._createdRecorder == null) {
      const factory = _audioRecorderFactory;
      if (factory == null) throw new Error('当前平台不支持录音');
      this._createdRecorder = factory();
    }
    return this._createdRecorder;
  }

  /**
   * Records mono AAC-LC at a speech-friendly bitrate — small files that still
   * sound clean for voice, wrapped in an m4a/[kVoiceMessageMimeType] container.
   */
  private static readonly _config: RecordConfig = {
    encoder: AudioEncoder.aacLc,
    bitRate: 64000,
    sampleRate: 44100,
    numChannels: 1,
  };

  /**
   * Browsers without an AAC MediaRecorder (Chrome and Firefox on many
   * systems) record 16 kHz mono WAV instead: larger, but playable on every
   * client, unlike the WebM/Opus they would otherwise produce.
   */
  private static readonly _webWavConfig: RecordConfig = {
    encoder: AudioEncoder.wav,
    sampleRate: 16000,
    numChannels: 1,
  };

  /** MIME type and file extension of the current (or last) clip. */
  get clipMimeType(): string {
    return this._recordingWav ? kVoiceMessageWavMimeType : kVoiceMessageMimeType;
  }

  get clipExtension(): string {
    return this._recordingWav ? kVoiceMessageWavExtension : kVoiceMessageExtension;
  }

  hasPermission(): Promise<boolean> {
    return this._recorder.hasPermission();
  }

  /**
   * Begins recording to a fresh temp file. Throws
   * [VoiceRecorderPermissionException] if the mic is denied.
   */
  async start(): Promise<void> {
    const recorder = this._recorder;
    if (!(await recorder.hasPermission())) {
      throw new VoiceRecorderPermissionException();
    }
    const stamp = Date.now() * 1000;
    if (HostPlatform.isWeb) {
      // Browsers record into memory; stop() returns a blob: URL and the path
      // only names the recording.
      const aac = await recorder.isEncoderSupported(AudioEncoder.aacLc);
      this._recordingWav = !aac;
      this._currentPath = null;
      await recorder.start(aac ? VoiceRecorderController._config : VoiceRecorderController._webWavConfig, {
        path: `voice_${stamp}.${this.clipExtension}`,
      });
      return;
    }
    // PORT: the Flutter `record` plugin always had a native AAC encoder on
    // desktop. Chromium's MediaRecorder encodes AAC through the OS (Windows,
    // macOS); elsewhere fall back to the same 16 kHz WAV the web build used.
    const aac = await recorder.isEncoderSupported(AudioEncoder.aacLc);
    this._recordingWav = !aac;
    const files = platform().files;
    const dir = (await files.directories()).temporary;
    const path = files.joinPath(dir, `voice_${stamp}.${this.clipExtension}`);
    this._currentPath = path;
    await recorder.start(aac ? VoiceRecorderController._config : VoiceRecorderController._webWavConfig, { path });
  }

  /**
   * Stops recording and returns the captured file path, or null if nothing was
   * written.
   */
  async stop(): Promise<string | null> {
    const path = await this._recorder.stop();
    this._currentPath = null;
    return path;
  }

  /** Stops and discards the recording, deleting the temp file. */
  async cancel(): Promise<void> {
    try {
      await this._recorder.cancel();
    } finally {
      await this._deleteTemp(this._currentPath);
      this._currentPath = null;
    }
  }

  /**
   * Live microphone amplitude for an optional level meter while recording.
   * [interval] is in milliseconds.
   */
  amplitudeStream(opts: { interval?: number } = {}): EventStream<Amplitude> {
    const { interval = 200 } = opts;
    return this._recorder.onAmplitudeChanged(interval);
  }

  /**
   * Reads a clip's bytes for upload. Does not delete the file, so a failed
   * send can retry from the same path; call [discardClip] once the clip is no
   * longer needed.
   */
  async readClip(path: string): Promise<Uint8Array> {
    // On the web [path] is the blob: URL the recorder returned.
    if (HostPlatform.isWeb) {
      const response = await fetch(path);
      return new Uint8Array(await response.arrayBuffer());
    }
    return platform().files.readFile(path);
  }

  /** Deletes a finished clip's temp file. Safe to call with a stale path. */
  discardClip(path: string | null): Promise<void> {
    return this._deleteTemp(path);
  }

  private async _deleteTemp(path: string | null): Promise<void> {
    if (path == null || HostPlatform.isWeb) return;
    try {
      const files = platform().files;
      if (await files.exists(path)) await files.deleteFile(path);
    } catch {
      // A leftover temp clip in the OS temp dir is harmless; never let cleanup
      // failure surface to the user.
    }
  }

  async dispose(): Promise<void> {
    const recorder = this._injectedRecorder ?? this._createdRecorder;
    if (recorder == null) return;
    await recorder.dispose();
  }
}
