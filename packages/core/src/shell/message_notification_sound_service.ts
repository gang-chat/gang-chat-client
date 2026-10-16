import { BytesSource, PlayerMode, createAudioPlayer } from './audio_player';
import type { AudioPlayer } from './audio_player';
import { SynthesizedToneNote, buildSynthesizedToneWav } from './synthesized_wav';

export interface MessageNotificationSoundPlayer {
  play(opts: { volume: number }): Promise<void>;

  dispose(): Promise<void>;
}

let _tone: Uint8Array | null = null;

/**
 * Plays the short, locally synthesized cue for a realtime text-channel
 * message. No network request or packaged binary asset is required.
 *
 * PORT: the Flutter Windows build wrote the tone to a temp WAV file because
 * audioplayers' Windows backend could not play bytes; the HTMLAudioElement
 * player plays the bytes directly on every platform.
 */
export class MessageNotificationSoundService implements MessageNotificationSoundPlayer {
  constructor(opts: { player?: AudioPlayer } = {}) {
    this._player = opts.player ?? createAudioPlayer();
  }

  private readonly _player: AudioPlayer;
  private _disposed = false;

  private static get _tone(): Uint8Array {
    return (_tone ??= buildMessageNotificationTone());
  }

  async play(opts: { volume: number }): Promise<void> {
    if (this._disposed) return;
    const normalizedVolume = Math.min(1, Math.max(0, opts.volume));
    if (!(normalizedVolume > 0)) return;
    await this._player.stop();
    await this._player.play(new BytesSource(MessageNotificationSoundService._tone, { mimeType: 'audio/wav' }), {
      volume: normalizedVolume,
      mode: PlayerMode.lowLatency,
    });
  }

  async dispose(): Promise<void> {
    if (this._disposed) return;
    this._disposed = true;
    await this._player.dispose().catch(() => {});
  }
}

export function messageNotificationSoundFrequencies(): number[] {
  return [659.25, 880.0];
}

export function buildMessageNotificationTone(opts: { sampleRate?: number } = {}): Uint8Array {
  const { sampleRate = 44100 } = opts;
  const frequencies = messageNotificationSoundFrequencies();
  return buildSynthesizedToneWav({
    sampleRate,
    tailDuration: 0.022,
    amplitude: 0.2,
    notes: [
      new SynthesizedToneNote({ frequency: frequencies[0]!, duration: 0.075, gapAfter: 0.018 }),
      new SynthesizedToneNote({ frequency: frequencies[1]!, duration: 0.115 }),
    ],
  });
}
