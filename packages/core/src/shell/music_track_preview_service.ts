import { MusicTrackPreviewAsset } from '../app/music_track_preview';
import type { MusicTrackPreviewPlatform, MusicTrackPreviewPlatformFactory } from '../app/music_track_preview';
import { HostPlatform } from '../config/host_platform';
import type { EventStream, StreamSubscription } from '../lib/event_stream';
import { StreamController } from '../lib/event_stream';
import { platform } from '../platform/platform';
import type { FileSystemBridge } from '../platform/platform';
import { BytesSource, DeviceFileSource, ReleaseMode, createAudioPlayer } from './audio_player';
import type { AudioPlayer } from './audio_player';

export class DefaultMusicTrackPreviewPlatformFactory implements MusicTrackPreviewPlatformFactory {
  create(): MusicTrackPreviewPlatform {
    return HostPlatform.isWeb ? new BrowserMusicTrackPreviewPlatform() : new LocalMusicTrackPreviewPlatform();
  }
}

/**
 * Web previews: browsers have no cache directory, so recent previews are kept
 * in memory and played from bytes.
 */
export class BrowserMusicTrackPreviewPlatform implements MusicTrackPreviewPlatform {
  private static readonly _maxCacheBytes = 64 << 20;

  constructor(opts: { player?: AudioPlayer } = {}) {
    this._player = opts.player ?? createAudioPlayer();
    this._completion = this._player.onPlayerComplete.listen(() => {
      if (!this._disposed && !this._completed.isClosed) this._completed.add();
    });
  }

  private readonly _player: AudioPlayer;
  // Insertion order doubles as recency (oldest first).
  private readonly _previews = new Map<string, Uint8Array>();
  private _cachedBytes = 0;
  private readonly _completed = new StreamController<void>();
  private readonly _completion: StreamSubscription;
  private _disposed = false;

  get onCompleted(): EventStream<void> {
    return this._completed.stream;
  }

  async findCached(cacheKey: string): Promise<MusicTrackPreviewAsset | null> {
    const bytes = this._previews.get(cacheKey);
    if (bytes == null) return null;
    this._previews.delete(cacheKey);
    this._previews.set(cacheKey, bytes);
    return new MusicTrackPreviewAsset(cacheKey);
  }

  async store(cacheKey: string, bytes: Uint8Array): Promise<MusicTrackPreviewAsset> {
    const previous = this._previews.get(cacheKey);
    if (previous != null) {
      this._previews.delete(cacheKey);
      this._cachedBytes -= previous.length;
    }
    this._previews.set(cacheKey, bytes);
    this._cachedBytes += bytes.length;
    while (this._cachedBytes > BrowserMusicTrackPreviewPlatform._maxCacheBytes && this._previews.size > 1) {
      const oldest = this._previews.keys().next().value as string;
      this._cachedBytes -= this._previews.get(oldest)!.length;
      this._previews.delete(oldest);
    }
    return new MusicTrackPreviewAsset(cacheKey);
  }

  async play(asset: MusicTrackPreviewAsset): Promise<void> {
    if (this._disposed) return;
    const bytes = this._previews.get(asset.location);
    if (bytes == null) throw new Error('试听缓存已失效');
    await this._player.setReleaseMode(ReleaseMode.stop);
    await this._player.play(new BytesSource(bytes, { mimeType: 'audio/mp4' }));
  }

  stop(): Promise<void> {
    return this._player.stop();
  }

  async dispose(): Promise<void> {
    if (this._disposed) return;
    this._disposed = true;
    this._completion.cancel();
    await this._player.dispose();
    this._completed.close();
    this._previews.clear();
  }
}

/**
 * Stores authenticated preview responses in the application cache and plays
 * them locally.
 *
 * PORT: the Flutter Android build used a tiny native MediaPlayer bridge
 * (`gang_chat/music_preview`) so WebRTC audio routing was untouched, and macOS
 * set an AVAudioSession `mixWithOthers` context. Neither applies to the
 * HTMLAudioElement player (Android stays on Flutter), so only the desktop path
 * is ported. Files go through `platform().files`.
 */
export class LocalMusicTrackPreviewPlatform implements MusicTrackPreviewPlatform {
  private static readonly _maxCacheBytes = 256 << 20;

  constructor(
    opts: {
      desktopPlayer?: AudioPlayer;
      /** Root cache directory; defaults to `platform().files.directories().applicationCache`. */
      cacheDirectoryProvider?: () => Promise<string>;
      files?: FileSystemBridge;
    } = {},
  ) {
    this._desktopPlayer = opts.desktopPlayer ?? null;
    this._files = opts.files ?? null;
    this._cacheDirectoryProvider =
      opts.cacheDirectoryProvider ?? (async () => (await this._fs.directories()).applicationCache);
    this._ensureDesktopPlayer();
  }

  private _desktopPlayer: AudioPlayer | null;
  private readonly _files: FileSystemBridge | null;
  private readonly _cacheDirectoryProvider: () => Promise<string>;
  private readonly _completed = new StreamController<void>();
  private _desktopCompletion: StreamSubscription | null = null;
  private _cacheDirectory: Promise<string> | null = null;
  private _desktopConfigured = false;
  private _disposed = false;

  get onCompleted(): EventStream<void> {
    return this._completed.stream;
  }

  private get _fs(): FileSystemBridge {
    return this._files ?? platform().files;
  }

  private _ensureDesktopPlayer(): AudioPlayer {
    const existing = this._desktopPlayer;
    if (existing != null) {
      this._desktopCompletion ??= existing.onPlayerComplete.listen(() => {
        if (!this._disposed && !this._completed.isClosed) this._completed.add();
      });
      return existing;
    }
    const player = createAudioPlayer();
    this._desktopPlayer = player;
    this._desktopCompletion = player.onPlayerComplete.listen(() => {
      if (!this._disposed && !this._completed.isClosed) this._completed.add();
    });
    return player;
  }

  private _previewDirectory(): Promise<string> {
    return (this._cacheDirectory ??= (async () => {
      const root = await this._cacheDirectoryProvider();
      const directory = this._fs.joinPath(root, 'music-track-previews', 'v1');
      await this._fs.ensureDirectory(directory);
      await this._removeLegacyPreviews(directory);
      return directory;
    })());
  }

  private async _removeLegacyPreviews(directory: string): Promise<void> {
    try {
      for (const entry of await this._fs.listDirectory(directory)) {
        if (!entry.isDirectory && (entry.path.endsWith('.ogg') || entry.path.endsWith('.mp3'))) {
          await this._fs.deleteFile(entry.path);
        }
      }
    } catch {
      // A locked legacy file can be retried on the next application start.
    }
  }

  private async _cacheFile(cacheKey: string): Promise<string> {
    const directory = await this._previewDirectory();
    return this._fs.joinPath(directory, `${cacheKey}.m4a`);
  }

  async findCached(cacheKey: string): Promise<MusicTrackPreviewAsset | null> {
    const file = await this._cacheFile(cacheKey);
    try {
      const length = await this._fs.fileSize(file);
      if (length != null && length > 0) {
        await this._fs.setLastModified?.(file, Date.now());
        return new MusicTrackPreviewAsset(file);
      }
    } catch {
      // Missing, empty, or unreadable files are treated as cache misses.
    }
    try {
      if (await this._fs.exists(file)) await this._fs.deleteFile(file);
    } catch {
      // ignore
    }
    return null;
  }

  async store(cacheKey: string, bytes: Uint8Array): Promise<MusicTrackPreviewAsset> {
    const file = await this._cacheFile(cacheKey);
    const temporary = `${file}.tmp-${Date.now() * 1000}`;
    try {
      await this._fs.writeFile(temporary, bytes);
      if (await this._fs.exists(file)) await this._fs.deleteFile(file);
      await this._fs.rename(temporary, file);
      await this._trimCache({ protectedPath: file });
    } finally {
      try {
        if (await this._fs.exists(temporary)) await this._fs.deleteFile(temporary);
      } catch {
        // ignore
      }
    }
    return new MusicTrackPreviewAsset(file);
  }

  private async _trimCache(opts: { protectedPath: string }): Promise<void> {
    try {
      const directory = await this._previewDirectory();
      const entries: Array<{ path: string; bytes: number; modifiedMs: number }> = [];
      let totalBytes = 0;
      for (const entry of await this._fs.listDirectory(directory)) {
        if (entry.isDirectory || !entry.path.endsWith('.m4a')) continue;
        totalBytes += entry.size;
        entries.push({ path: entry.path, bytes: entry.size, modifiedMs: entry.modifiedMs });
      }
      if (totalBytes <= LocalMusicTrackPreviewPlatform._maxCacheBytes) return;
      entries.sort((a, b) => a.modifiedMs - b.modifiedMs);
      for (const entry of entries) {
        if (totalBytes <= LocalMusicTrackPreviewPlatform._maxCacheBytes) break;
        if (entry.path === opts.protectedPath) continue;
        await this._fs.deleteFile(entry.path);
        totalBytes -= entry.bytes;
      }
    } catch {
      // Preview cache eviction is best effort and must never block playback.
    }
  }

  async play(asset: MusicTrackPreviewAsset): Promise<void> {
    if (this._disposed) return;
    const player = this._ensureDesktopPlayer();
    if (!this._desktopConfigured) {
      await player.setReleaseMode(ReleaseMode.stop);
      this._desktopConfigured = true;
    }
    await player.play(new DeviceFileSource(asset.location, { mimeType: 'audio/mp4' }));
  }

  async stop(): Promise<void> {
    await this._desktopPlayer?.stop();
  }

  async dispose(): Promise<void> {
    if (this._disposed) return;
    this._disposed = true;
    this._desktopCompletion?.cancel();
    await this._desktopPlayer?.dispose();
    this._completed.close();
  }
}
