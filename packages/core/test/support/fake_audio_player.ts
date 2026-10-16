/**
 * Scriptable [AudioPlayer] for service tests: records calls and lets the test
 * drive the player's streams ([emitState], [emitPosition], [complete], ...).
 */
import { StreamController } from '../../src/lib/event_stream';
import { PlayerState } from '../../src/shell/audio_player_types';
import type { AudioPlayer, PlayerMode, ReleaseMode, Source } from '../../src/shell/audio_player_types';

export class FakeAudioPlayer implements AudioPlayer {
  private readonly _state = new StreamController<PlayerState>();
  private readonly _position = new StreamController<number>();
  private readonly _duration = new StreamController<number>();
  private readonly _complete = new StreamController<void>();

  readonly onPlayerStateChanged = this._state.stream;
  readonly onPositionChanged = this._position.stream;
  readonly onDurationChanged = this._duration.stream;
  readonly onPlayerComplete = this._complete.stream;

  readonly calls: string[] = [];
  readonly played: Array<{ source: Source; volume?: number; mode?: PlayerMode }> = [];
  releaseMode: ReleaseMode | null = null;
  disposed = false;
  /** Makes the next [play] reject. */
  playError: Error | null = null;

  async play(source: Source, opts: { volume?: number; mode?: PlayerMode } = {}): Promise<void> {
    this.calls.push('play');
    if (this.playError) {
      const error = this.playError;
      this.playError = null;
      throw error;
    }
    this.played.push({ source, ...opts });
    this._state.add(PlayerState.playing);
  }

  async stop(): Promise<void> {
    this.calls.push('stop');
    this._state.add(PlayerState.stopped);
  }

  async setReleaseMode(mode: ReleaseMode): Promise<void> {
    this.calls.push('setReleaseMode');
    this.releaseMode = mode;
  }

  async dispose(): Promise<void> {
    this.calls.push('dispose');
    this.disposed = true;
    this._state.add(PlayerState.disposed);
  }

  emitState(state: PlayerState): void {
    this._state.add(state);
  }

  emitPosition(ms: number): void {
    this._position.add(ms);
  }

  emitDuration(ms: number): void {
    this._duration.add(ms);
  }

  complete(): void {
    this._state.add(PlayerState.completed);
    this._complete.add();
  }
}
