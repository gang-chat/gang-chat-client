import { StreamController } from '../lib/event_stream';
import type { EventStream, StreamSubscription } from '../lib/event_stream';
import type { MusicTrackPreviewApi } from '../protocol/api_client';
import { fnv1a64 } from './fnv_hash';

export enum MusicTrackPreviewPhase {
  idle = 'idle',
  loading = 'loading',
  playing = 'playing',
}

export class MusicTrackPreviewTrack {
  constructor(opts: { source: string; trackId: string }) {
    this.source = opts.source;
    this.trackId = opts.trackId;
  }

  readonly source: string;
  readonly trackId: string;

  get key(): string {
    return musicTrackPreviewCacheKey(this.source, this.trackId);
  }
}

export class MusicTrackPreviewSnapshot {
  constructor(opts: { phase?: MusicTrackPreviewPhase; activeTrackKey?: string | null } = {}) {
    this.phase = opts.phase ?? MusicTrackPreviewPhase.idle;
    this.activeTrackKey = opts.activeTrackKey ?? null;
  }

  readonly phase: MusicTrackPreviewPhase;
  readonly activeTrackKey: string | null;

  isLoading(trackKey: string): boolean {
    return this.activeTrackKey === trackKey && this.phase === MusicTrackPreviewPhase.loading;
  }

  isPlaying(trackKey: string): boolean {
    return this.activeTrackKey === trackKey && this.phase === MusicTrackPreviewPhase.playing;
  }
}

export class MusicTrackPreviewAsset {
  constructor(location: string) {
    this.location = location;
  }

  /** Opaque platform locator. The default implementation uses a local path. */
  readonly location: string;
}

export interface MusicTrackPreviewPlatform {
  readonly onCompleted: EventStream<void>;

  findCached(cacheKey: string): Promise<MusicTrackPreviewAsset | null>;

  store(cacheKey: string, bytes: Uint8Array): Promise<MusicTrackPreviewAsset>;

  play(asset: MusicTrackPreviewAsset): Promise<void>;

  stop(): Promise<void>;

  dispose(): Promise<void>;
}

export interface MusicTrackPreviewPlatformFactory {
  create(): MusicTrackPreviewPlatform;
}

/**
 * Owns one local preview at a time. Downloading may finish and populate the
 * cache after a card closes, but the generation check prevents stale work
 * from ever starting playback.
 */
export class MusicTrackPreviewController {
  constructor(opts: { api: MusicTrackPreviewApi; platform: MusicTrackPreviewPlatform }) {
    this._api = opts.api;
    this._platform = opts.platform;
    this._completionSubscription = this._platform.onCompleted.listen(() => {
      if (this._disposed || this._snapshot.phase !== MusicTrackPreviewPhase.playing) {
        return;
      }
      this._publish(new MusicTrackPreviewSnapshot());
    });
  }

  private readonly _api: MusicTrackPreviewApi;
  private readonly _platform: MusicTrackPreviewPlatform;
  private readonly _changes = new StreamController<MusicTrackPreviewSnapshot>();
  private readonly _completionSubscription: StreamSubscription;
  private _snapshot = new MusicTrackPreviewSnapshot();
  private _generation = 0;
  private _disposed = false;

  get snapshot(): MusicTrackPreviewSnapshot {
    return this._snapshot;
  }

  get changes(): EventStream<MusicTrackPreviewSnapshot> {
    return this._changes.stream;
  }

  async toggle(track: MusicTrackPreviewTrack): Promise<void> {
    const key = track.key;
    if (this._snapshot.isPlaying(key)) {
      await this.stop();
      return;
    }
    await this.preview(track);
  }

  async preview(track: MusicTrackPreviewTrack): Promise<void> {
    if (this._disposed) return;
    const generation = ++this._generation;
    this._publish(
      new MusicTrackPreviewSnapshot({
        phase: MusicTrackPreviewPhase.loading,
        activeTrackKey: track.key,
      }),
    );

    try {
      await this._platform.stop();
      if (this._disposed || generation !== this._generation) return;
      let asset = await this._platform.findCached(track.key);
      if (asset == null) {
        const downloaded = await this._api.downloadMusicTrackPreview({
          source: track.source,
          trackId: track.trackId,
        });
        if (downloaded.bytes.length === 0) {
          throw new Error('试听文件为空');
        }
        asset = await this._platform.store(track.key, downloaded.bytes);
      }
      if (this._disposed || generation !== this._generation) return;
      await this._platform.play(asset);
      if (this._disposed || generation !== this._generation) {
        await this._platform.stop();
        return;
      }
      this._publish(
        new MusicTrackPreviewSnapshot({
          phase: MusicTrackPreviewPhase.playing,
          activeTrackKey: track.key,
        }),
      );
    } catch (error) {
      if (!this._disposed && generation === this._generation) {
        this._publish(new MusicTrackPreviewSnapshot());
      }
      throw error;
    }
  }

  async stopIf(trackKey: string): Promise<void> {
    if (this._snapshot.activeTrackKey !== trackKey) return;
    await this.stop();
  }

  async stop(): Promise<void> {
    if (this._disposed) return;
    this._generation += 1;
    await this._platform.stop();
    if (!this._disposed) this._publish(new MusicTrackPreviewSnapshot());
  }

  async dispose(): Promise<void> {
    if (this._disposed) return;
    this._disposed = true;
    this._generation += 1;
    this._completionSubscription.cancel();
    await this._platform.stop();
    await this._platform.dispose();
    this._changes.close();
  }

  private _publish(next: MusicTrackPreviewSnapshot): void {
    this._snapshot = next;
    if (!this._changes.isClosed) this._changes.add(next);
  }
}

export function musicTrackPreviewCacheKey(source: string, trackId: string): string {
  const value = `${source.trim()}\u0000${trackId.trim()}`;
  // The low 63 bits keep the key positive.
  const hash = fnv1a64(value) & ((1n << 63n) - 1n);
  return hash.toString(16).padStart(16, '0');
}
