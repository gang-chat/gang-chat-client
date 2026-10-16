import { describe, expect, it } from 'vitest';

import { shouldAcceptMusicBoxSnapshot } from '../../src/app/music_box_controller';
import {
  MusicBoxSection,
  MusicBoxTransportAction,
  musicBoxActiveSourceLabel,
  musicBoxArtistFieldLabel,
  musicBoxArtistsLabel,
  musicBoxDefaultSource,
  musicBoxFormatBytes,
  musicBoxFormatDuration,
  musicBoxHasActivePlayback,
  musicBoxIsCurrent,
  musicBoxPlaylistKey,
  musicBoxPrimaryTransport,
  musicBoxProgress,
  musicBoxQueueStatusLabel,
  musicBoxRecordSpinning,
  musicBoxRequestQueueContainsTrack,
  musicBoxRequestQueueLabel,
  musicBoxSectionForSource,
  musicBoxSourceEnabled,
  musicBoxSourceIsActive,
  musicBoxSourceLabel,
  musicBoxSources,
  musicBoxTrackIsCurrent,
  musicBoxTransportApiAction,
  musicBoxUsageFraction,
  musicBoxUsageHint,
  musicBoxUsageLabel,
  normalizedMusicBoxSource,
} from '../../src/app/music_box_display';
import {
  MusicBoxActiveSource,
  MusicBoxActiveSourceType,
  MusicBoxPlayback,
  MusicBoxPlaybackState,
  MusicBoxQueueItem,
  MusicBoxQueueItemStatus,
  MusicBoxState,
  MusicBoxUsage,
} from '../../src/protocol/models';

describe('music box sources', () => {
  it('temporarily excludes QQ Music from selectable sources', () => {
    expect(musicBoxSources.map((source) => source.id)).toEqual(['netease', 'bilibili']);
    expect(musicBoxSourceEnabled('tencent')).toBe(false);
  });

  it('normalizes a disabled or unknown source to the default', () => {
    expect(normalizedMusicBoxSource('tencent')).toBe(musicBoxDefaultSource);
    expect(normalizedMusicBoxSource('unknown')).toBe(musicBoxDefaultSource);
    expect(normalizedMusicBoxSource('bilibili')).toBe('bilibili');
  });

  it('uses consistent labels without hiding future providers', () => {
    expect(musicBoxSourceLabel('netease')).toBe('网易云');
    expect(musicBoxSourceLabel('bilibili')).toBe('哔哩哔哩');
    expect(musicBoxSourceLabel('future-source')).toBe('future-source');
    expect(musicBoxSourceLabel('')).toBe('未知来源');
  });

  it('uses author for Bilibili and artist for music providers', () => {
    expect(musicBoxArtistFieldLabel('bilibili')).toBe('作者');
    expect(musicBoxArtistFieldLabel(' BILIBILI ')).toBe('作者');
    expect(musicBoxArtistFieldLabel('netease')).toBe('歌手');
    expect(musicBoxArtistFieldLabel('future-source')).toBe('歌手');
  });
});

describe('music box flat list navigation', () => {
  it('every active source type has an owning section', () => {
    expect(musicBoxSectionForSource(MusicBoxActiveSourceType.temporary)).toBe(MusicBoxSection.queue);
    expect(musicBoxSectionForSource(MusicBoxActiveSourceType.roomPlaylist)).toBe(MusicBoxSection.roomPlaylists);
    expect(musicBoxSectionForSource(MusicBoxActiveSourceType.userPlaylist)).toBe(MusicBoxSection.myPlaylists);
  });

  it('playlist key is the wire type value plus id', () => {
    expect(musicBoxPlaylistKey(MusicBoxActiveSourceType.roomPlaylist, 'pl-1')).toBe('room_playlist:pl-1');
    expect(musicBoxPlaylistKey(MusicBoxActiveSourceType.userPlaylist, 'pl-1')).toBe('user_playlist:pl-1');
  });

  it('temporary source is active regardless of id', () => {
    const state = _state();
    expect(musicBoxSourceIsActive(state, MusicBoxActiveSourceType.temporary)).toBe(true);
    expect(musicBoxSourceIsActive(state, MusicBoxActiveSourceType.temporary, 'ignored')).toBe(true);
    expect(musicBoxSourceIsActive(state, MusicBoxActiveSourceType.roomPlaylist)).toBe(false);
  });

  it('saved playlist is active only when both type and id match', () => {
    const state = _state({
      activeSource: new MusicBoxActiveSource({
        type: MusicBoxActiveSourceType.roomPlaylist,
        id: 'playlist-1',
        name: '一起听',
      }),
    });
    expect(musicBoxSourceIsActive(state, MusicBoxActiveSourceType.roomPlaylist, 'playlist-1')).toBe(true);
    expect(musicBoxSourceIsActive(state, MusicBoxActiveSourceType.roomPlaylist, 'playlist-2')).toBe(false);
    expect(musicBoxSourceIsActive(state, MusicBoxActiveSourceType.userPlaylist, 'playlist-1')).toBe(false);
    expect(musicBoxSourceIsActive(state, MusicBoxActiveSourceType.temporary)).toBe(false);
  });

  it('current track matches by catalog identity with link key rules', () => {
    const current = _item({ id: 'snapshot-item', source: 'bilibili', trackId: 'BV1AbC' });
    const playing = _state({
      playbackState: MusicBoxPlaybackState.playing,
      currentItemId: 'snapshot-item',
      queue: [current],
    });

    expect(musicBoxTrackIsCurrent(playing, { source: ' BILIBILI ', trackId: ' BV1AbC ' })).toBe(true);
    expect(musicBoxTrackIsCurrent(playing, { source: 'bilibili', trackId: 'bv1abc' })).toBe(false);
    expect(musicBoxTrackIsCurrent(playing, { source: 'netease', trackId: 'BV1AbC' })).toBe(false);
    expect(musicBoxTrackIsCurrent(_state({ queue: [current] }), { source: 'bilibili', trackId: 'BV1AbC' })).toBe(false);
    expect(musicBoxIsCurrent(playing, current)).toBe(true);
  });
});

describe('music box request queue identity', () => {
  it('matches the same provider link and preserves track id case', () => {
    const queued = _item({ id: 'queued-1', source: 'bilibili', trackId: 'BV1AbC' });

    expect(musicBoxRequestQueueContainsTrack([queued], { source: ' BILIBILI ', trackId: ' BV1AbC ' })).toBe(true);
    expect(musicBoxRequestQueueContainsTrack([queued], { source: 'bilibili', trackId: 'bv1abc' })).toBe(false);
  });
});

describe('music box active source labels', () => {
  it('uses 点歌队列 for temporary sources from old servers', () => {
    const parsed = MusicBoxState.fromJson({
      enabled: true,
      active_source: { type: 'temporary', name: '临时歌单' },
    });

    expect(parsed.activeSource.name).toBe(musicBoxRequestQueueLabel);
    expect(musicBoxActiveSourceLabel(parsed.activeSource)).toBe(musicBoxRequestQueueLabel);
  });

  it('keeps saved playlist names and has scoped fallbacks', () => {
    expect(
      musicBoxActiveSourceLabel(new MusicBoxActiveSource({ type: MusicBoxActiveSourceType.roomPlaylist, name: '一起听' })),
    ).toBe('一起听');
    expect(
      musicBoxActiveSourceLabel(new MusicBoxActiveSource({ type: MusicBoxActiveSourceType.userPlaylist, name: '   ' })),
    ).toBe('个人歌单');
    expect(
      musicBoxActiveSourceLabel(new MusicBoxActiveSource({ type: MusicBoxActiveSourceType.roomPlaylist, name: '' })),
    ).toBe('房间歌单');
  });
});

describe('musicBoxProgress', () => {
  it('renders the server-reported position as-is', () => {
    const progress = musicBoxProgress(
      _state({
        playbackState: MusicBoxPlaybackState.playing,
        currentItemId: 'item-1',
        positionMs: 8000,
        queue: [_item({ id: 'item-1', durationMs: 200000 })],
      }),
    );

    expect(progress.positionMs).toBe(8000);
    expect(progress.durationMs).toBe(200000);
    expect(progress.fraction).toBeCloseTo(0.04, 4);
  });

  it('uses the recorded position regardless of playback state', () => {
    const progress = musicBoxProgress(
      _state({
        playbackState: MusicBoxPlaybackState.paused,
        currentItemId: 'item-1',
        positionMs: 5000,
        queue: [_item({ id: 'item-1', durationMs: 200000 })],
      }),
    );

    expect(progress.positionMs).toBe(5000);
  });

  it('floors the position to whole seconds', () => {
    const progress = musicBoxProgress(
      _state({
        playbackState: MusicBoxPlaybackState.playing,
        currentItemId: 'item-1',
        positionMs: 6900,
        queue: [_item({ id: 'item-1', durationMs: 200000 })],
      }),
    );

    expect(progress.positionMs).toBe(6000);
  });

  it('clamps the position to the track duration', () => {
    const progress = musicBoxProgress(
      _state({
        playbackState: MusicBoxPlaybackState.playing,
        currentItemId: 'item-1',
        positionMs: 30000,
        queue: [_item({ id: 'item-1', durationMs: 10000 })],
      }),
    );

    expect(progress.positionMs).toBe(10000);
    expect(progress.fraction).toBe(1.0);
  });

  it('fraction is zero when duration is unknown', () => {
    const progress = musicBoxProgress(
      _state({
        playbackState: MusicBoxPlaybackState.playing,
        currentItemId: 'item-1',
        positionMs: 5000,
        queue: [_item({ id: 'item-1', durationMs: 0 })],
      }),
    );

    expect(progress.fraction).toBe(0);
  });
});

describe('musicBoxPrimaryTransport', () => {
  it('maps playback state to the transport action', () => {
    expect(musicBoxPrimaryTransport(_state({ playbackState: MusicBoxPlaybackState.playing }))).toBe(
      MusicBoxTransportAction.pause,
    );
    expect(musicBoxPrimaryTransport(_state({ playbackState: MusicBoxPlaybackState.paused }))).toBe(
      MusicBoxTransportAction.resume,
    );
    expect(musicBoxPrimaryTransport(_state({ playbackState: MusicBoxPlaybackState.stopped }))).toBe(
      MusicBoxTransportAction.play,
    );
  });

  it('maps transport action to the API verb', () => {
    expect(musicBoxTransportApiAction(MusicBoxTransportAction.play)).toBe('play');
    expect(musicBoxTransportApiAction(MusicBoxTransportAction.pause)).toBe('pause');
    expect(musicBoxTransportApiAction(MusicBoxTransportAction.resume)).toBe('resume');
  });

  it('spins only while playing', () => {
    expect(musicBoxRecordSpinning(_state({ playbackState: MusicBoxPlaybackState.playing }))).toBe(true);
    expect(musicBoxRecordSpinning(_state({ playbackState: MusicBoxPlaybackState.paused }))).toBe(false);
  });

  it('requires a current item and a non-stopped transport state', () => {
    const queue = [_item({ id: 'item-1' })];

    expect(
      musicBoxHasActivePlayback(
        _state({ playbackState: MusicBoxPlaybackState.stopped, currentItemId: 'item-1', queue }),
      ),
    ).toBe(false);
    expect(
      musicBoxHasActivePlayback(
        _state({ playbackState: MusicBoxPlaybackState.playing, currentItemId: 'item-1', queue }),
      ),
    ).toBe(true);
    expect(
      musicBoxHasActivePlayback(
        _state({ playbackState: MusicBoxPlaybackState.paused, currentItemId: 'item-1', queue }),
      ),
    ).toBe(true);
    expect(musicBoxHasActivePlayback(_state({ playbackState: MusicBoxPlaybackState.playing, queue }))).toBe(false);
  });
});

describe('musicBoxQueueStatusLabel', () => {
  it('labels each lifecycle stage', () => {
    expect(musicBoxQueueStatusLabel(_item({ status: MusicBoxQueueItemStatus.pending }))).toBe('排队中，等待下载');
    expect(musicBoxQueueStatusLabel(_item({ status: MusicBoxQueueItemStatus.downloading }))).toBe('下载中');
    expect(musicBoxQueueStatusLabel(_item({ status: MusicBoxQueueItemStatus.ready }))).toBeNull();
  });

  it('uses the server error for failed items, with a fallback', () => {
    expect(musicBoxQueueStatusLabel(_item({ status: MusicBoxQueueItemStatus.failed, error: '版权限制' }))).toBe('版权限制');
    expect(musicBoxQueueStatusLabel(_item({ status: MusicBoxQueueItemStatus.failed }))).toBe('处理失败');
  });
});

describe('musicBoxUsageHint', () => {
  it('warns near and at the limit', () => {
    expect(musicBoxUsageHint(new MusicBoxUsage({ usedBytes: 50, limitBytes: 100 }))).toBeNull();
    expect(musicBoxUsageHint(new MusicBoxUsage({ usedBytes: 92, limitBytes: 100 }))).toBe('空间已接近上限');
    expect(musicBoxUsageHint(new MusicBoxUsage({ usedBytes: 100, limitBytes: 100 }))).toBe('空间已满，新歌将排队等待下载');
    expect(musicBoxUsageHint(new MusicBoxUsage({ usedBytes: 0, limitBytes: 0 }))).toBeNull();
  });

  it('usage fraction is clamped', () => {
    expect(musicBoxUsageFraction(new MusicBoxUsage({ usedBytes: 25, limitBytes: 100 }))).toBe(0.25);
    expect(musicBoxUsageFraction(new MusicBoxUsage({ usedBytes: 200, limitBytes: 100 }))).toBe(1.0);
    expect(musicBoxUsageFraction(new MusicBoxUsage({ usedBytes: 5, limitBytes: 0 }))).toBe(0);
  });
});

describe('formatting', () => {
  it('formats durations as mm:ss and h:mm:ss', () => {
    expect(musicBoxFormatDuration(0)).toBe('--:--');
    expect(musicBoxFormatDuration(65000)).toBe('1:05');
    expect(musicBoxFormatDuration(3725000)).toBe('1:02:05');
  });

  it('formats byte sizes', () => {
    expect(musicBoxFormatBytes(0)).toBe('0 B');
    expect(musicBoxFormatBytes(512)).toBe('512 B');
    expect(musicBoxFormatBytes(1536)).toBe('1.5 KB');
    expect(musicBoxFormatBytes(5 * 1024 * 1024)).toBe('5 MB');
    expect(musicBoxUsageLabel(new MusicBoxUsage({ usedBytes: 1536, limitBytes: 5 * 1024 * 1024 }))).toBe('1.5 KB / 5 MB');
  });

  it('joins artist lists, dropping blanks', () => {
    expect(musicBoxArtistsLabel(['林俊杰', '孙燕姿'])).toBe('林俊杰、孙燕姿');
    expect(musicBoxArtistsLabel(['', '  ', '周杰伦'])).toBe('周杰伦');
    expect(musicBoxArtistsLabel([])).toBe('');
  });
});

describe('snapshot acceptance', () => {
  it('rejects stale revision snapshots but keeps legacy compatibility', () => {
    const current = MusicBoxState.fromJson({ enabled: true, revision: 5, playback: { current_item_id: 'current' } });
    const stale = MusicBoxState.fromJson({ enabled: true, revision: 4, playback: { current_item_id: 'old' } });
    const newer = MusicBoxState.fromJson({ enabled: true, revision: 6, playback: { current_item_id: 'new' } });
    const legacy = MusicBoxState.fromJson({ enabled: true });

    expect(shouldAcceptMusicBoxSnapshot(current, stale)).toBe(false);
    expect(shouldAcceptMusicBoxSnapshot(current, newer)).toBe(true);
    expect(shouldAcceptMusicBoxSnapshot(current, legacy)).toBe(true);
  });
});

function _state(
  opts: {
    enabled?: boolean;
    playbackState?: MusicBoxPlaybackState;
    currentItemId?: string;
    positionMs?: number;
    updatedAt?: Date | null;
    queue?: MusicBoxQueueItem[];
    activeSource?: MusicBoxActiveSource;
  } = {},
): MusicBoxState {
  return new MusicBoxState({
    enabled: opts.enabled ?? true,
    playback: new MusicBoxPlayback({
      state: opts.playbackState ?? MusicBoxPlaybackState.stopped,
      currentItemId: opts.currentItemId ?? '',
      positionMs: opts.positionMs ?? 0,
      volume: 100,
      updatedAt: opts.updatedAt ?? null,
    }),
    queue: opts.queue ?? [],
    activeSource: opts.activeSource ?? new MusicBoxActiveSource(),
    usage: new MusicBoxUsage({ usedBytes: 0, limitBytes: 0 }),
  });
}

function _item(
  opts: {
    id?: string;
    source?: string;
    trackId?: string;
    title?: string;
    artist?: string;
    status?: MusicBoxQueueItemStatus;
    durationMs?: number;
    error?: string;
  } = {},
): MusicBoxQueueItem {
  const id = opts.id ?? 'item';
  return new MusicBoxQueueItem({
    id,
    source: opts.source ?? 'netease',
    trackId: opts.trackId ?? `track-${id}`,
    title: opts.title ?? 'Song',
    artist: opts.artist ?? '',
    durationMs: opts.durationMs ?? 0,
    status: opts.status ?? MusicBoxQueueItemStatus.ready,
    fileSizeBytes: 0,
    error: opts.error ?? '',
    addedByUserId: 'user',
    createdAt: null,
  });
}
