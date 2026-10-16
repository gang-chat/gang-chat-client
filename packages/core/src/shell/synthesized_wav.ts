export class SynthesizedToneNote {
  constructor(opts: { frequency: number; duration: number; gapAfter?: number }) {
    this.frequency = opts.frequency;
    this.duration = opts.duration;
    this.gapAfter = opts.gapAfter ?? 0;
  }

  /** Hz. */
  readonly frequency: number;
  /** Seconds. */
  readonly duration: number;
  /** Seconds of silence after the note. */
  readonly gapAfter: number;
}

function argumentError(value: unknown, name: string): Error {
  const error = new Error(`Invalid argument (${name}): ${String(value)}`);
  error.name = 'ArgumentError';
  return error;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

/** Dart `num.round()`: halves round away from zero. */
function dartRound(value: number): number {
  return value < 0 ? -Math.round(-value) : Math.round(value);
}

/**
 * Builds a mono 16-bit PCM WAV cue with the same soft harmonic envelope used
 * by Gang Chat's short local notification sounds.
 *
 * Durations are in seconds, as in the Dart original.
 */
export function buildSynthesizedToneWav(opts: {
  notes: SynthesizedToneNote[];
  sampleRate?: number;
  tailDuration?: number;
  amplitude?: number;
}): Uint8Array {
  const { notes, sampleRate = 44100, tailDuration = 0.025, amplitude = 0.24 } = opts;
  if (notes.length === 0) throw argumentError(notes, 'notes');
  if (sampleRate <= 0) throw argumentError(sampleRate, 'sampleRate');
  if (tailDuration < 0) throw argumentError(tailDuration, 'tailDuration');
  for (const note of notes) {
    if (note.frequency <= 0 || note.duration <= 0 || note.gapAfter < 0) {
      throw argumentError(note, 'notes');
    }
  }

  const totalDuration = notes.reduce((total, note) => total + note.duration + note.gapAfter, tailDuration);
  const frameCount = Math.round(sampleRate * totalDuration);
  const pcmLength = frameCount * 2;
  const bytes = new Uint8Array(44 + pcmLength);
  const data = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);

  const writeAscii = (offset: number, value: string): void => {
    for (let index = 0; index < value.length; index += 1) {
      data.setUint8(offset + index, value.charCodeAt(index));
    }
  };

  writeAscii(0, 'RIFF');
  data.setUint32(4, 36 + pcmLength, true);
  writeAscii(8, 'WAVE');
  writeAscii(12, 'fmt ');
  data.setUint32(16, 16, true);
  data.setUint16(20, 1, true);
  data.setUint16(22, 1, true);
  data.setUint32(24, sampleRate, true);
  data.setUint32(28, sampleRate * 2, true);
  data.setUint16(32, 2, true);
  data.setUint16(34, 16, true);
  writeAscii(36, 'data');
  data.setUint32(40, pcmLength, true);

  const noteSample = (localTime: number, duration: number, frequency: number): number => {
    if (localTime < 0 || localTime >= duration) return 0;
    const attackDuration = 0.008;
    const releaseDuration = 0.045;
    const attack = clamp(localTime / attackDuration, 0, 1);
    const release = clamp((duration - localTime) / releaseDuration, 0, 1);
    const envelope = Math.sin(Math.PI * 0.5 * Math.min(attack, release));
    const phase = 2 * Math.PI * frequency * localTime;
    return envelope * (Math.sin(phase) + 0.16 * Math.sin(phase * 2) + 0.04 * Math.sin(phase * 3));
  };

  for (let frame = 0; frame < frameCount; frame += 1) {
    const time = frame / sampleRate;
    let noteStart = 0;
    let sample = 0;
    for (const note of notes) {
      const noteEnd = noteStart + note.duration;
      if (time >= noteStart && time < noteEnd) {
        sample = noteSample(time - noteStart, note.duration, note.frequency);
        break;
      }
      noteStart = noteEnd + note.gapAfter;
    }
    const pcm = clamp(dartRound(sample * amplitude * 32767), -32768, 32767);
    data.setInt16(44 + frame * 2, pcm, true);
  }
  return bytes;
}
