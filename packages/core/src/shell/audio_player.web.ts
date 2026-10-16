/**
 * `HTMLAudioElement` implementation of [AudioPlayer] for Electron and the
 * browser (see `audio_player_types.ts`). React Native resolves
 * `./audio_player` to the native `audio_player.ts` instead.
 */
import { StreamController } from '../lib/event_stream';
import type { Milliseconds } from '../lib/async';
import { hasPlatform, platform } from '../platform/platform';
import {
  BytesSource,
  DeviceFileSource,
  PlayerState,
  ReleaseMode,
  UrlSource,
  audioMimeTypeForPath,
} from './audio_player_types';
import type { AudioPlayer, PlayerMode, Source } from './audio_player_types';

export * from './audio_player_types';

class HtmlAudioPlayer implements AudioPlayer {
  private readonly _state = new StreamController<PlayerState>();
  private readonly _position = new StreamController<Milliseconds>();
  private readonly _duration = new StreamController<Milliseconds>();
  private readonly _complete = new StreamController<void>();
  private _current = PlayerState.stopped;
  private _audio: HTMLAudioElement | null = null;
  private _objectUrl: string | null = null;
  private _releaseMode = ReleaseMode.release;
  private _volume = 1;
  /** Bumped by every play/stop so a slow source load cannot start stale audio. */
  private _generation = 0;

  readonly onPlayerStateChanged = this._state.stream;
  readonly onPositionChanged = this._position.stream;
  readonly onDurationChanged = this._duration.stream;
  readonly onPlayerComplete = this._complete.stream;

  async play(source: Source, opts: { volume?: number; mode?: PlayerMode } = {}): Promise<void> {
    if (this._current === PlayerState.disposed) return;
    const generation = ++this._generation;
    if (opts.volume != null) this._volume = Math.min(1, Math.max(0, opts.volume));
    const audio = this._ensureAudio();
    audio.pause();
    const url = await this._urlFor(source);
    if (generation !== this._generation || this._isDisposed()) {
      if (url.revoke) URL.revokeObjectURL(url.src);
      return;
    }
    this._replaceObjectUrl(url.revoke ? url.src : null);
    audio.loop = this._releaseMode === ReleaseMode.loop;
    audio.volume = this._volume;
    audio.src = url.src;
    await audio.play();
    if (generation !== this._generation) return;
    this._setState(PlayerState.playing);
  }

  async stop(): Promise<void> {
    if (this._current === PlayerState.disposed) return;
    this._generation += 1;
    const audio = this._audio;
    if (audio != null) {
      audio.pause();
      if (this._releaseMode === ReleaseMode.release) {
        this._releaseSource(audio);
      } else {
        try {
          audio.currentTime = 0;
        } catch {
          // No source loaded yet.
        }
      }
    }
    this._setState(PlayerState.stopped);
  }

  async setReleaseMode(mode: ReleaseMode): Promise<void> {
    this._releaseMode = mode;
    if (this._audio != null) this._audio.loop = mode === ReleaseMode.loop;
  }

  async dispose(): Promise<void> {
    if (this._current === PlayerState.disposed) return;
    this._generation += 1;
    const audio = this._audio;
    this._audio = null;
    if (audio != null) {
      audio.pause();
      audio.removeEventListener('timeupdate', this._onTimeUpdate);
      audio.removeEventListener('durationchange', this._onDurationChange);
      audio.removeEventListener('ended', this._onEnded);
      this._releaseSource(audio);
    }
    this._setState(PlayerState.disposed);
    this._state.close();
    this._position.close();
    this._duration.close();
    this._complete.close();
  }

  private _ensureAudio(): HTMLAudioElement {
    if (this._audio != null) return this._audio;
    const audio = new Audio();
    audio.preload = 'auto';
    audio.addEventListener('timeupdate', this._onTimeUpdate);
    audio.addEventListener('durationchange', this._onDurationChange);
    audio.addEventListener('ended', this._onEnded);
    this._audio = audio;
    return audio;
  }

  private readonly _onTimeUpdate = (): void => {
    const audio = this._audio;
    if (audio == null) return;
    this._position.add(Math.round(audio.currentTime * 1000));
  };

  private readonly _onDurationChange = (): void => {
    const audio = this._audio;
    if (audio == null || !Number.isFinite(audio.duration)) return;
    this._duration.add(Math.round(audio.duration * 1000));
  };

  private readonly _onEnded = (): void => {
    const audio = this._audio;
    if (audio == null || audio.loop) return;
    if (this._releaseMode === ReleaseMode.release) this._releaseSource(audio);
    this._setState(PlayerState.completed);
    this._complete.add();
  };

  private async _urlFor(source: Source): Promise<{ src: string; revoke: boolean }> {
    if (source instanceof UrlSource) return { src: source.url, revoke: false };
    if (source instanceof BytesSource) {
      return { src: objectUrl(source.bytes, source.mimeType), revoke: true };
    }
    if (source instanceof DeviceFileSource) {
      // The renderer cannot load file:// URLs from an http(s) origin, so the
      // bytes come through the platform bridge.
      if (!hasPlatform()) throw new Error('Local audio files need a platform bridge.');
      const bytes = await platform().files.readFile(source.path);
      return { src: objectUrl(bytes, source.mimeType ?? audioMimeTypeForPath(source.path)), revoke: true };
    }
    throw new Error('Unsupported audio source.');
  }

  private _releaseSource(audio: HTMLAudioElement): void {
    audio.removeAttribute('src');
    audio.load();
    this._replaceObjectUrl(null);
  }

  private _replaceObjectUrl(next: string | null): void {
    if (this._objectUrl != null && this._objectUrl !== next) URL.revokeObjectURL(this._objectUrl);
    this._objectUrl = next;
  }

  /** A method, not an inline check, so TS does not narrow `_current` across awaits. */
  private _isDisposed(): boolean {
    return this._current === PlayerState.disposed;
  }

  private _setState(state: PlayerState): void {
    if (this._current === state) return;
    this._current = state;
    this._state.add(state);
  }
}

function objectUrl(bytes: Uint8Array, mimeType: string | null): string {
  // Copy into a fresh ArrayBuffer-backed view: Blob rejects SharedArrayBuffer views.
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  return URL.createObjectURL(new Blob([copy], mimeType ? { type: mimeType } : {}));
}

/** Port of `AudioPlayer()` from `package:audioplayers`. */
export function createAudioPlayer(): AudioPlayer {
  return new HtmlAudioPlayer();
}
