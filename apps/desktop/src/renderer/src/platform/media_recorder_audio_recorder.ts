import {
  AudioEncoder,
  type Amplitude,
  type AudioRecorder,
  type RecordConfig,
} from '@gang-chat/core/app/voice_recorder_controller';
import { StreamController, type EventStream } from '@gang-chat/core/lib/event_stream';
import { platform } from '@gang-chat/core/platform/platform';

/**
 * Voice-message recorder for the desktop renderer (the `record` plugin of the
 * Flutter client).
 *
 * AAC-LC goes through `MediaRecorder` into an MP4 (m4a) container, which
 * Chromium encodes with the OS encoder on Windows and macOS. Where no AAC
 * encoder exists the recorder captures 16-bit PCM through Web Audio and
 * writes a WAV, the same fallback the Flutter web build used. Finished clips
 * are written to the requested temp path through the main process.
 */
const aacMimeType = 'audio/mp4;codecs=mp4a.40.2';

interface ActiveRecording {
  stream: MediaStream;
  context: AudioContext;
  analyser: AnalyserNode;
  path: string;
  finish: () => Promise<Uint8Array>;
  abort: () => void;
}

export class MediaRecorderAudioRecorder implements AudioRecorder {
  private _active: ActiveRecording | null = null;
  private _maxAmplitude = -160;

  async hasPermission(): Promise<boolean> {
    try {
      const status = await navigator.permissions.query({ name: 'microphone' as PermissionName });
      if (status.state === 'denied') return false;
    } catch {
      // Permission queries are optional; capture itself will tell.
    }
    return navigator.mediaDevices?.getUserMedia != null;
  }

  async isEncoderSupported(encoder: AudioEncoder): Promise<boolean> {
    switch (encoder) {
      case AudioEncoder.aacLc:
        return typeof MediaRecorder !== 'undefined' && MediaRecorder.isTypeSupported(aacMimeType);
      case AudioEncoder.wav:
        return true;
    }
  }

  async start(config: RecordConfig, opts: { path: string }): Promise<void> {
    await this.cancel();
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: config.numChannels ?? 1,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
    });
    const wav = config.encoder === AudioEncoder.wav || !(await this.isEncoderSupported(AudioEncoder.aacLc));
    const context = new AudioContext(wav && config.sampleRate ? { sampleRate: config.sampleRate } : undefined);
    const source = context.createMediaStreamSource(stream);
    const analyser = context.createAnalyser();
    analyser.fftSize = 2048;
    source.connect(analyser);
    this._maxAmplitude = -160;

    let finish: () => Promise<Uint8Array>;
    let abort: () => void;
    if (wav) {
      // PCM capture: a ScriptProcessor is enough for short voice clips and
      // needs no separate worklet module.
      const processor = context.createScriptProcessor(4096, 1, 1);
      const chunks: Float32Array[] = [];
      processor.onaudioprocess = (event) => {
        chunks.push(new Float32Array(event.inputBuffer.getChannelData(0)));
      };
      source.connect(processor);
      processor.connect(context.destination);
      finish = async () => {
        processor.disconnect();
        return encodeWav(chunks, context.sampleRate);
      };
      abort = () => processor.disconnect();
    } else {
      const recorder = new MediaRecorder(stream, {
        mimeType: aacMimeType,
        audioBitsPerSecond: config.bitRate ?? 64000,
      });
      const parts: Blob[] = [];
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) parts.push(event.data);
      };
      recorder.start(250);
      finish = () =>
        new Promise<Uint8Array>((resolve, reject) => {
          recorder.onstop = async () => {
            try {
              resolve(new Uint8Array(await new Blob(parts, { type: 'audio/mp4' }).arrayBuffer()));
            } catch (error) {
              reject(error);
            }
          };
          recorder.stop();
        });
      abort = () => {
        if (recorder.state !== 'inactive') recorder.stop();
      };
    }
    this._active = { stream, context, analyser, path: opts.path, finish, abort };
  }

  async stop(): Promise<string | null> {
    const active = this._active;
    if (!active) return null;
    this._active = null;
    try {
      const bytes = await active.finish();
      if (bytes.length === 0) return null;
      await platform().files.writeFile(active.path, bytes);
      return active.path;
    } finally {
      release(active);
    }
  }

  async cancel(): Promise<void> {
    const active = this._active;
    if (!active) return;
    this._active = null;
    try {
      active.abort();
    } finally {
      release(active);
    }
  }

  onAmplitudeChanged(interval: number): EventStream<Amplitude> {
    const controller = new StreamController<Amplitude>();
    const samples = new Float32Array(2048);
    const timer = setInterval(() => {
      const active = this._active;
      if (!active) {
        clearInterval(timer);
        controller.close();
        return;
      }
      active.analyser.getFloatTimeDomainData(samples);
      let sum = 0;
      for (const sample of samples) sum += sample * sample;
      const rms = Math.sqrt(sum / samples.length);
      const current = rms > 0 ? Math.max(-160, 20 * Math.log10(rms)) : -160;
      this._maxAmplitude = Math.max(this._maxAmplitude, current);
      controller.add({ current, max: this._maxAmplitude });
    }, Math.max(16, interval));
    return controller.stream;
  }

  async dispose(): Promise<void> {
    await this.cancel();
  }
}

function release(active: ActiveRecording): void {
  for (const track of active.stream.getTracks()) track.stop();
  void active.context.close().catch(() => {});
}

/** 16-bit little-endian mono WAV. */
function encodeWav(chunks: Float32Array[], sampleRate: number): Uint8Array {
  const length = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
  if (length === 0) return new Uint8Array(0);
  const buffer = new ArrayBuffer(44 + length * 2);
  const view = new DataView(buffer);
  const writeAscii = (offset: number, text: string) => {
    for (let i = 0; i < text.length; i += 1) view.setUint8(offset + i, text.charCodeAt(i));
  };
  writeAscii(0, 'RIFF');
  view.setUint32(4, 36 + length * 2, true);
  writeAscii(8, 'WAVE');
  writeAscii(12, 'fmt ');
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  writeAscii(36, 'data');
  view.setUint32(40, length * 2, true);
  let offset = 44;
  for (const chunk of chunks) {
    for (const sample of chunk) {
      const clamped = Math.max(-1, Math.min(1, sample));
      view.setInt16(offset, clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff, true);
      offset += 2;
    }
  }
  return new Uint8Array(buffer);
}
