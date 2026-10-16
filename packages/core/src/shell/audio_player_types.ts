/**
 * The slice of the `audioplayers` package API the Flutter shell services used
 * (`AudioPlayer`, `UrlSource` / `BytesSource` / `DeviceFileSource`,
 * `PlayerState`, `ReleaseMode`, `PlayerMode`).
 *
 * PORT: audio playback needs a browser API, so it follows the core
 * platform-file rule: `audio_player.web.ts` implements [AudioPlayer] with an
 * `HTMLAudioElement` (Electron / browser) and `audio_player.ts` is the native
 * placeholder a React Native app replaces. Services import `./audio_player`
 * and accept an injected [AudioPlayer] (tests pass a fake). These shared types
 * live here because a `.web.ts` file cannot import its own `.ts` sibling.
 */
import type { EventStream } from '../lib/event_stream';
import type { Milliseconds } from '../lib/async';

export class UrlSource {
  constructor(readonly url: string) {}
}

export class BytesSource {
  readonly mimeType: string | null;

  constructor(
    readonly bytes: Uint8Array,
    opts: { mimeType?: string | null } = {},
  ) {
    this.mimeType = opts.mimeType ?? null;
  }
}

/** A file on the local disk, addressed by absolute path. */
export class DeviceFileSource {
  readonly mimeType: string | null;

  constructor(
    readonly path: string,
    opts: { mimeType?: string | null } = {},
  ) {
    this.mimeType = opts.mimeType ?? null;
  }
}

export type Source = UrlSource | BytesSource | DeviceFileSource;

export enum PlayerState {
  stopped = 'stopped',
  playing = 'playing',
  paused = 'paused',
  completed = 'completed',
  disposed = 'disposed',
}

export enum PlayerMode {
  mediaPlayer = 'mediaPlayer',
  lowLatency = 'lowLatency',
}

export enum ReleaseMode {
  /** Releases the source after stop / completion (audioplayers default). */
  release = 'release',
  loop = 'loop',
  /** Keeps the source loaded after stop / completion. */
  stop = 'stop',
}

export interface AudioPlayer {
  /** Emits only on actual state changes, synchronously from the call that caused it. */
  readonly onPlayerStateChanged: EventStream<PlayerState>;
  readonly onPositionChanged: EventStream<Milliseconds>;
  readonly onDurationChanged: EventStream<Milliseconds>;
  readonly onPlayerComplete: EventStream<void>;

  /** Loads [source] and starts playback; rejects when the source cannot play. */
  play(source: Source, opts?: { volume?: number; mode?: PlayerMode }): Promise<void>;
  stop(): Promise<void>;
  setReleaseMode(mode: ReleaseMode): Promise<void>;
  dispose(): Promise<void>;
}

/** Best-effort audio MIME type from a file name / path extension. */
export function audioMimeTypeForPath(path: string): string | null {
  const dot = path.lastIndexOf('.');
  if (dot < 0) return null;
  switch (path.slice(dot + 1).toLowerCase()) {
    case 'm4a':
    case 'mp4':
    case 'aac':
      return 'audio/mp4';
    case 'mp3':
      return 'audio/mpeg';
    case 'wav':
      return 'audio/wav';
    case 'ogg':
    case 'oga':
    case 'opus':
      return 'audio/ogg';
    case 'webm':
      return 'audio/webm';
    default:
      return null;
  }
}
