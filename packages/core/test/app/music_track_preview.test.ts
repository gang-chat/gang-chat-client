import { describe, expect, it } from 'vitest';

import {
  MusicTrackPreviewAsset,
  MusicTrackPreviewController,
  MusicTrackPreviewPhase,
  MusicTrackPreviewTrack,
  musicTrackPreviewCacheKey,
  type MusicTrackPreviewPlatform,
} from '../../src/app/music_track_preview';
import { Completer, delay } from '../../src/lib/async';
import { StreamController } from '../../src/lib/event_stream';
import { DownloadedFile, type MusicTrackPreviewApi } from '../../src/protocol/api_client';

class FakePreviewApi implements MusicTrackPreviewApi {
  readonly download = new Completer<DownloadedFile>();
  requests = 0;

  downloadMusicTrackPreview(opts: { source: string; trackId: string }): Promise<DownloadedFile> {
    this.requests += 1;
    return this.download.promise;
  }
}

class FakePreviewPlatform implements MusicTrackPreviewPlatform {
  private readonly _completed = new StreamController<void>();
  cached = false;
  storedBytes: Uint8Array | null = null;
  readonly played: string[] = [];
  stopCalls = 0;
  nextStop: Completer<void> | null = null;
  nextStopCompletion: Completer<void> | null = null;

  get onCompleted() {
    return this._completed.stream;
  }

  async findCached(cacheKey: string): Promise<MusicTrackPreviewAsset | null> {
    return this.cached ? new MusicTrackPreviewAsset(cacheKey) : null;
  }

  async store(cacheKey: string, bytes: Uint8Array): Promise<MusicTrackPreviewAsset> {
    this.storedBytes = bytes;
    this.cached = true;
    return new MusicTrackPreviewAsset(cacheKey);
  }

  async play(asset: MusicTrackPreviewAsset): Promise<void> {
    this.played.push(asset.location);
  }

  async stop(): Promise<void> {
    this.stopCalls += 1;
    const pending = this.nextStop;
    if (pending != null) {
      this.nextStop = null;
      this.nextStopCompletion = pending;
      await pending.promise;
    }
  }

  complete(): void {
    this._completed.add(undefined);
  }

  async dispose(): Promise<void> {
    this._completed.close();
  }
}

function previewFile(bytes: number[]): DownloadedFile {
  return new DownloadedFile({ bytes: Uint8Array.from(bytes), filename: 'preview.m4a', mimeType: 'audio/mp4' });
}

describe('MusicTrackPreviewController', () => {
  it('downloads, caches, plays, and stops one preview', async () => {
    const api = new FakePreviewApi();
    const platform = new FakePreviewPlatform();
    const controller = new MusicTrackPreviewController({ api, platform });
    const track = new MusicTrackPreviewTrack({ source: 'netease', trackId: 'track-1' });

    const future = controller.preview(track);
    await delay(0);
    expect(controller.snapshot.isLoading(track.key)).toBe(true);
    api.download.complete(previewFile([1, 2, 3]));
    await future;

    expect(api.requests).toBe(1);
    expect(Array.from(platform.storedBytes!)).toEqual([1, 2, 3]);
    expect(platform.played).toEqual([track.key]);
    expect(controller.snapshot.isPlaying(track.key)).toBe(true);

    await controller.stopIf(track.key);
    expect(controller.snapshot.phase).toBe(MusicTrackPreviewPhase.idle);
    expect(platform.stopCalls).toBeGreaterThanOrEqual(2);
    await controller.dispose();
  });

  it('closing while loading may cache but never starts stale playback', async () => {
    const api = new FakePreviewApi();
    const platform = new FakePreviewPlatform();
    const controller = new MusicTrackPreviewController({ api, platform });
    const track = new MusicTrackPreviewTrack({ source: 'bilibili', trackId: 'BV1test' });

    const future = controller.preview(track);
    await delay(0);
    await controller.stopIf(track.key);
    api.download.complete(previewFile([4, 5, 6]));
    await future;

    expect(Array.from(platform.storedBytes!)).toEqual([4, 5, 6]);
    expect(platform.played).toEqual([]);
    expect(controller.snapshot.phase).toBe(MusicTrackPreviewPhase.idle);
    await controller.dispose();
  });

  it('closing during the initial player stop prevents a late preview', async () => {
    const api = new FakePreviewApi();
    const platform = new FakePreviewPlatform();
    platform.nextStop = new Completer<void>();
    const controller = new MusicTrackPreviewController({ api, platform });
    const track = new MusicTrackPreviewTrack({ source: 'netease', trackId: 'stop-race' });

    const preview = controller.preview(track);
    expect(controller.snapshot.isLoading(track.key)).toBe(true);
    await controller.stopIf(track.key);
    platform.nextStopCompletion?.complete();
    await preview;

    expect(api.requests).toBe(0);
    expect(platform.played).toEqual([]);
    expect(controller.snapshot.phase).toBe(MusicTrackPreviewPhase.idle);
    await controller.dispose();
  });

  it('player completion clears the cancel-preview state', async () => {
    const api = new FakePreviewApi();
    const platform = new FakePreviewPlatform();
    platform.cached = true;
    const controller = new MusicTrackPreviewController({ api, platform });
    const track = new MusicTrackPreviewTrack({ source: 'netease', trackId: 'cached' });
    const phases: MusicTrackPreviewPhase[] = [];
    controller.changes.listen((snapshot) => phases.push(snapshot.phase));

    await controller.preview(track);
    expect(controller.snapshot.isPlaying(track.key)).toBe(true);
    platform.complete();
    await delay(0);
    expect(controller.snapshot.phase).toBe(MusicTrackPreviewPhase.idle);
    expect(phases).toEqual([MusicTrackPreviewPhase.loading, MusicTrackPreviewPhase.playing, MusicTrackPreviewPhase.idle]);
    await controller.dispose();
  });

  it('empty preview downloads fail and reset the snapshot', async () => {
    const api = new FakePreviewApi();
    const platform = new FakePreviewPlatform();
    const controller = new MusicTrackPreviewController({ api, platform });
    const track = new MusicTrackPreviewTrack({ source: 'netease', trackId: 'empty' });

    const future = controller.preview(track);
    api.download.complete(previewFile([]));

    await expect(future).rejects.toThrow('试听文件为空');
    expect(controller.snapshot.phase).toBe(MusicTrackPreviewPhase.idle);
    await controller.dispose();
  });

  it('cache keys are 16 hex digits, positive and trim their inputs', () => {
    const key = musicTrackPreviewCacheKey('netease', 'track-1');
    expect(key).toMatch(/^[0-7][0-9a-f]{15}$/);
    expect(musicTrackPreviewCacheKey(' netease ', ' track-1 ')).toBe(key);
    expect(musicTrackPreviewCacheKey('bilibili', 'track-1')).not.toBe(key);
  });
});
