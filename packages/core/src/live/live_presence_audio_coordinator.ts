/**
 * Port of `lib/src/live/live_presence_audio_coordinator.dart`.
 */
import { delay, type Milliseconds } from '../lib/async';
import type { LivePresenceAnnouncement, LivePresenceSpeechPlayer } from '../app/live_presence_announcement';
import type { LivePresenceSound, LivePresenceSoundPlayer } from './live_presence_sound_service';

/**
 * Serializes presence audio so bursts of joins/leaves remain intelligible.
 *
 * Each item always starts with the existing cue. When an announcement is
 * supplied, speech starts only after the short cue has finished.
 */
export class LivePresenceAudioCoordinator {
  constructor(opts: {
    soundPlayer: LivePresenceSoundPlayer;
    speechPlayer: LivePresenceSpeechPlayer;
    cueDuration?: Milliseconds;
    itemGap?: Milliseconds;
  }) {
    this._soundPlayer = opts.soundPlayer;
    this._speechPlayer = opts.speechPlayer;
    this.cueDuration = opts.cueDuration ?? 330;
    this.itemGap = opts.itemGap ?? 90;
  }

  private readonly _soundPlayer: LivePresenceSoundPlayer;
  private readonly _speechPlayer: LivePresenceSpeechPlayer;
  readonly cueDuration: Milliseconds;
  readonly itemGap: Milliseconds;

  private _tail: Promise<void> = Promise.resolve();
  private _disposed = false;

  play(
    sound: LivePresenceSound,
    opts: { volume: number; announcement?: LivePresenceAnnouncement | null },
  ): Promise<void> {
    const { volume, announcement = null } = opts;
    if (this._disposed || volume <= 0) return Promise.resolve();
    const normalizedVolume = Math.min(1, Math.max(0, volume));
    const completion = this._tail
      .then(async () => {
        if (this._disposed) return;
        await this._soundPlayer.play(sound, { volume: normalizedVolume });
        await delay(this.cueDuration);
        if (this._disposed) return;
        if (announcement != null) {
          await this._speechPlayer.speak(announcement, { volume: normalizedVolume });
        }
        if (this.itemGap > 0) {
          await delay(this.itemGap);
        }
      })
      .catch(() => {});
    this._tail = completion;
    return completion;
  }

  dispose(): void {
    this._disposed = true;
  }
}
