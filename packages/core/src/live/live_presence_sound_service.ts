/**
 * Port of `lib/src/live/live_presence_sound_service.dart`.
 */
import { BytesSource, PlayerMode, createAudioPlayer, type AudioPlayer } from '../shell/audio_player';
import { SynthesizedToneNote, buildSynthesizedToneWav } from '../shell/synthesized_wav';

export enum LivePresenceSound {
  joined = 'joined',
  left = 'left',
}

export interface LivePresenceSoundPlayer {
  play(sound: LivePresenceSound, opts: { volume: number }): Promise<void>;

  dispose(): Promise<void>;
}

/**
 * Plays short, locally generated voice-channel presence cues.
 *
 * The tones are synthesized into WAV data so the cue is available on every
 * supported platform without another network request or a binary asset.
 *
 * PORT: the Flutter Windows build wrote the WAV to a temp file because
 * audioplayers could not play bytes there; the shared [AudioPlayer] plays the
 * bytes directly on every platform, so that workaround is gone.
 */
export class LivePresenceSoundService implements LivePresenceSoundPlayer {
  constructor(opts: { joinedPlayer?: AudioPlayer; leftPlayer?: AudioPlayer } = {}) {
    this._joinedPlayer = opts.joinedPlayer ?? createAudioPlayer();
    this._leftPlayer = opts.leftPlayer ?? createAudioPlayer();
  }

  private readonly _joinedPlayer: AudioPlayer;
  private readonly _leftPlayer: AudioPlayer;
  private _disposed = false;

  private static _joinedToneCache: Uint8Array | null = null;
  private static _leftToneCache: Uint8Array | null = null;

  private static get _joinedTone(): Uint8Array {
    return (LivePresenceSoundService._joinedToneCache ??= buildLivePresenceTone(LivePresenceSound.joined));
  }

  private static get _leftTone(): Uint8Array {
    return (LivePresenceSoundService._leftToneCache ??= buildLivePresenceTone(LivePresenceSound.left));
  }

  async play(sound: LivePresenceSound, opts: { volume: number }): Promise<void> {
    if (this._disposed) return;
    const normalizedVolume = Math.min(1, Math.max(0, opts.volume));
    if (normalizedVolume <= 0) return;
    const player = sound === LivePresenceSound.joined ? this._joinedPlayer : this._leftPlayer;
    const bytes = sound === LivePresenceSound.joined ? LivePresenceSoundService._joinedTone : LivePresenceSoundService._leftTone;
    await player.stop();
    await player.play(new BytesSource(bytes, { mimeType: 'audio/wav' }), {
      volume: normalizedVolume,
      mode: PlayerMode.lowLatency,
    });
  }

  async dispose(): Promise<void> {
    if (this._disposed) return;
    this._disposed = true;
    await Promise.all([this._joinedPlayer.dispose().catch(() => {}), this._leftPlayer.dispose().catch(() => {})]);
  }
}

export function livePresenceSoundNoteFrequencies(sound: LivePresenceSound): number[] {
  const rising = [523.25, 783.99];
  return sound === LivePresenceSound.joined ? rising : [...rising].reverse();
}

export function buildLivePresenceTone(sound: LivePresenceSound, opts: { sampleRate?: number } = {}): Uint8Array {
  const { sampleRate = 44100 } = opts;
  const notes = livePresenceSoundNoteFrequencies(sound);
  return buildSynthesizedToneWav({
    sampleRate,
    tailDuration: 0.025,
    notes: [
      new SynthesizedToneNote({ frequency: notes[0]!, duration: 0.105, gapAfter: 0.022 }),
      new SynthesizedToneNote({ frequency: notes[1]!, duration: 0.155 }),
    ],
  });
}
