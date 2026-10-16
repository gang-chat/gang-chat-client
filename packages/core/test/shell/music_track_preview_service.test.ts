import { beforeEach, expect, test } from 'vitest';
import { BytesSource, DeviceFileSource, ReleaseMode } from '../../src/shell/audio_player_types';
import {
  BrowserMusicTrackPreviewPlatform,
  LocalMusicTrackPreviewPlatform,
} from '../../src/shell/music_track_preview_service';
import { FakeAudioPlayer } from '../support/fake_audio_player';
import { installFakePlatform } from '../support/fake_platform';
import type { FakePlatform } from '../support/fake_platform';

let fake: FakePlatform;

beforeEach(() => {
  fake = installFakePlatform({ os: 'windows' });
});

test('local previews are cached under music-track-previews/v1 and replayed from disk', async () => {
  fake.files.put('/cache/music-track-previews/v1/old.mp3', 'legacy');
  const player = new FakeAudioPlayer();
  const preview = new LocalMusicTrackPreviewPlatform({ desktopPlayer: player });
  let completions = 0;
  preview.onCompleted.listen(() => completions++);

  expect(await preview.findCached('track')).toBeNull();
  const asset = await preview.store('track', Uint8Array.from([1, 2, 3]));
  expect(asset.location).toBe('/cache/music-track-previews/v1/track.m4a');
  expect(fake.files.files.has('/cache/music-track-previews/v1/old.mp3')).toBe(false);
  expect([...fake.files.files.keys()].filter((path) => path.includes('.tmp-'))).toEqual([]);
  expect((await preview.findCached('track'))?.location).toBe(asset.location);

  await preview.play(asset);
  expect(player.releaseMode).toBe(ReleaseMode.stop);
  expect((player.played[0]!.source as DeviceFileSource).path).toBe(asset.location);
  player.complete();
  expect(completions).toBe(1);

  await preview.dispose();
  await preview.play(asset);
  expect(player.played).toHaveLength(1);
});

test('empty cache files are treated as misses and removed', async () => {
  fake.files.put('/cache/music-track-previews/v1/empty.m4a', '');
  const preview = new LocalMusicTrackPreviewPlatform({ desktopPlayer: new FakeAudioPlayer() });
  expect(await preview.findCached('empty')).toBeNull();
  expect(fake.files.files.has('/cache/music-track-previews/v1/empty.m4a')).toBe(false);
});

test('browser previews keep a bounded in-memory LRU', async () => {
  const player = new FakeAudioPlayer();
  const preview = new BrowserMusicTrackPreviewPlatform({ player });
  const big = new Uint8Array(40 << 20);
  await preview.store('a', big);
  await preview.store('b', big);
  expect(await preview.findCached('a')).toBeNull();
  const asset = await preview.findCached('b');
  expect(asset?.location).toBe('b');

  await preview.play(asset!);
  expect((player.played[0]!.source as BytesSource).mimeType).toBe('audio/mp4');
  await preview.dispose();
  await expect(new BrowserMusicTrackPreviewPlatform({ player: new FakeAudioPlayer() }).play(asset!)).rejects.toThrow(
    '试听缓存已失效',
  );
});
