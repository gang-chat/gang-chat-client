/**
 * Native (React Native) audio player placeholder. See `audio_player_types.ts`.
 *
 * PORT: the Electron / browser build resolves `./audio_player` to
 * `audio_player.web.ts` (HTMLAudioElement). A React Native app replaces this
 * file with a native player (e.g. expo-av). Until then playback is silent and
 * completes immediately, so callers' state machines (voice playback, music
 * previews) return to idle instead of waiting forever.
 */
import { StreamController } from '../lib/event_stream';
import type { Milliseconds } from '../lib/async';
import { PlayerState, ReleaseMode } from './audio_player_types';
import type { AudioPlayer, PlayerMode, Source } from './audio_player_types';

export * from './audio_player_types';

class SilentAudioPlayer implements AudioPlayer {
  private readonly _state = new StreamController<PlayerState>();
  private readonly _position = new StreamController<Milliseconds>();
  private readonly _duration = new StreamController<Milliseconds>();
  private readonly _complete = new StreamController<void>();
  private _current = PlayerState.stopped;

  readonly onPlayerStateChanged = this._state.stream;
  readonly onPositionChanged = this._position.stream;
  readonly onDurationChanged = this._duration.stream;
  readonly onPlayerComplete = this._complete.stream;

  async play(_source: Source, _opts?: { volume?: number; mode?: PlayerMode }): Promise<void> {
    if (this._current === PlayerState.disposed) return;
    this._setState(PlayerState.playing);
    this._setState(PlayerState.completed);
    this._complete.add();
  }

  async stop(): Promise<void> {
    if (this._current === PlayerState.disposed) return;
    this._setState(PlayerState.stopped);
  }

  async setReleaseMode(_mode: ReleaseMode): Promise<void> {}

  async dispose(): Promise<void> {
    if (this._current === PlayerState.disposed) return;
    this._setState(PlayerState.disposed);
    this._state.close();
    this._position.close();
    this._duration.close();
    this._complete.close();
  }

  private _setState(state: PlayerState): void {
    if (this._current === state) return;
    this._current = state;
    this._state.add(state);
  }
}

/** Port of `AudioPlayer()` from `package:audioplayers`. */
export function createAudioPlayer(): AudioPlayer {
  return new SilentAudioPlayer();
}
