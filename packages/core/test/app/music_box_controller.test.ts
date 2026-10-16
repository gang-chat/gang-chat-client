import { describe, expect, it } from 'vitest';

import {
  MusicBoxController,
  applyMusicBoxProgress,
  applyMusicBoxRealtimeSnapshot,
  musicBoxControlErrorMessage,
  musicBoxControlRequiresExpectedRevision,
} from '../../src/app/music_box_controller';
import { ApiException, type GangApi } from '../../src/protocol/api_client';
import {
  MusicBoxCapabilities,
  MusicBoxPlayback,
  MusicBoxPlaybackMode,
  MusicBoxPlaybackState,
  MusicBoxProgressEvent,
  MusicBoxQueueItem,
  MusicBoxQueueItemStatus,
  MusicBoxSearchResult,
  MusicBoxState,
  MusicBoxUsage,
  PersonalMusicPlaylistItem,
} from '../../src/protocol/models';

const _item = new MusicBoxQueueItem({
  id: 'priority-track',
  source: 'netease',
  trackId: 'track-priority',
  title: '优先歌曲',
  artist: '歌手',
  durationMs: 180000,
  status: MusicBoxQueueItemStatus.ready,
  fileSizeBytes: 1024,
  error: '',
  addedByUserId: 'requester',
  createdAt: null,
});

const _state = new MusicBoxState({
  enabled: true,
  playback: new MusicBoxPlayback({
    state: MusicBoxPlaybackState.playing,
    currentItemId: 'priority-track',
    positionMs: 0,
    volume: 100,
    updatedAt: null,
  }),
  queue: [_item],
  usage: new MusicBoxUsage({ usedBytes: 1024, limitBytes: 200 * 1024 * 1024 }),
  revision: 42,
  hasRevision: true,
});

interface ControlCall {
  roomId: string;
  action: string;
  itemId?: string;
  mode?: string;
  commandId?: string;
  expectedRevision?: number;
}

/** Port of the Dart `_RecordingMusicBoxApi implements GangApi` + noSuchMethod fake. */
class RecordingMusicBoxApi {
  constructor(readonly result: MusicBoxState) {}

  roomId: string | null = null;
  action: string | null = null;
  itemId: string | null = null;
  mode: string | null = null;
  commandId: string | null = null;
  expectedRevision: number | null = null;
  addedPlaylistTrack: { playlistId: string; track: MusicBoxSearchResult; durationMs?: number } | null = null;

  async controlMusicBox(opts: ControlCall): Promise<MusicBoxState> {
    this.roomId = opts.roomId;
    this.action = opts.action;
    this.itemId = opts.itemId ?? null;
    this.mode = opts.mode ?? null;
    this.commandId = opts.commandId ?? null;
    this.expectedRevision = opts.expectedRevision ?? null;
    return this.result;
  }

  async addPersonalMusicPlaylistItem(opts: {
    playlistId: string;
    track: MusicBoxSearchResult;
    durationMs?: number;
  }): Promise<PersonalMusicPlaylistItem> {
    this.addedPlaylistTrack = opts;
    return new PersonalMusicPlaylistItem({
      id: 'item',
      playlistId: opts.playlistId,
      trackId: opts.track.trackId,
      source: opts.track.source,
      title: opts.track.name,
      artists: opts.track.artists,
      durationMs: opts.durationMs ?? 0,
      sortOrder: 10,
      createdAt: null,
    });
  }

  asGangApi(): GangApi {
    // Only the members the controller under test calls exist on this fake.
    return this as unknown as GangApi;
  }
}

describe('MusicBoxController', () => {
  it('playNow sends an idempotent item command without a stale revision', async () => {
    const api = new RecordingMusicBoxApi(_state);
    const controller = new MusicBoxController({ api: api.asGangApi() });

    await controller.playNow({ roomId: 'room-1', item: _item });

    expect(api.roomId).toBe('room-1');
    expect(api.action).toBe('play_now');
    expect(api.itemId).toBe(_item.id);
    expect(api.commandId).toBeTruthy();
    expect(api.expectedRevision).toBeNull();
  });

  it('transport controls execute without a stale UI revision', async () => {
    const actions = ['play', 'resume', 'pause', 'skip', 'next', 'previous', 'stop'];

    for (const action of actions) {
      const api = new RecordingMusicBoxApi(_state);
      const controller = new MusicBoxController({ api: api.asGangApi() });

      await controller.control({ roomId: 'room-1', action, currentState: _state });

      expect(api.expectedRevision, `action=${action}`).toBeNull();
      expect(api.commandId, `action=${action}`).toBeTruthy();
    }
  });

  it('command ids stay unique across commands', async () => {
    const api = new RecordingMusicBoxApi(_state);
    const controller = new MusicBoxController({ api: api.asGangApi() });

    await controller.control({ roomId: 'room-1', action: 'play' });
    const first = api.commandId;
    await controller.control({ roomId: 'room-1', action: 'play' });

    expect(first).toMatch(/^mbx-\d+-\d+$/);
    expect(api.commandId).not.toBe(first);
  });

  it('playback mode changes retain optimistic concurrency', async () => {
    const api = new RecordingMusicBoxApi(_state);
    const controller = new MusicBoxController({ api: api.asGangApi() });

    await controller.control({
      roomId: 'room-1',
      action: 'set_mode',
      mode: MusicBoxPlaybackMode.repeatOne,
      currentState: _state,
    });

    expect(api.expectedRevision).toBe(_state.revision);
    expect(api.mode).toBe('repeat_one');
    expect(musicBoxControlRequiresExpectedRevision('set_mode')).toBe(true);
    expect(musicBoxControlRequiresExpectedRevision('play')).toBe(false);
  });

  it('clearing the request queue retains optimistic concurrency', async () => {
    const api = new RecordingMusicBoxApi(_state);
    const controller = new MusicBoxController({ api: api.asGangApi() });

    await controller.clearTemporaryQueue({ roomId: 'room-1', currentState: _state });

    expect(api.action).toBe('clear_temporary_playlist');
    expect(api.expectedRevision).toBe(_state.revision);
  });

  it('queue items map onto the playlist search-result shape', async () => {
    const api = new RecordingMusicBoxApi(_state);
    const controller = new MusicBoxController({ api: api.asGangApi() });

    await controller.addQueueItemToMyPlaylist({ playlistId: 'playlist-1', item: _item });

    expect(api.addedPlaylistTrack?.playlistId).toBe('playlist-1');
    expect(api.addedPlaylistTrack?.track.trackId).toBe('track-priority');
    expect(api.addedPlaylistTrack?.track.name).toBe('优先歌曲');
    expect(api.addedPlaylistTrack?.track.artists).toEqual(['歌手']);
    expect(api.addedPlaylistTrack?.durationMs).toBe(180000);
  });

  it('music box command errors preserve localized API feedback', () => {
    const error = new ApiException('歌曲尚未准备完成', {
      statusCode: 409,
      code: 'music_box_item_not_ready',
      requestId: 'request-1',
    });

    expect(musicBoxControlErrorMessage(error, '优先播放失败，请重试')).toBe('歌曲尚未准备完成');
    expect(musicBoxControlErrorMessage(new Error('offline'), '优先播放失败，请重试')).toBe('优先播放失败，请重试');
  });

  it('compact progress updates only its matching authoritative snapshot', () => {
    const matching = applyMusicBoxProgress(
      _state,
      new MusicBoxProgressEvent({ revision: 42, currentItemId: 'priority-track', positionMs: 12345 }),
    );

    expect(matching).not.toBeNull();
    expect(matching!.playback.positionMs).toBe(12345);
    expect(matching!.queue).toBe(_state.queue);

    const stale = applyMusicBoxProgress(
      _state,
      new MusicBoxProgressEvent({ revision: 41, currentItemId: 'priority-track', positionMs: 99999 }),
    );
    const wrongTrack = applyMusicBoxProgress(
      _state,
      new MusicBoxProgressEvent({ revision: 42, currentItemId: 'another-track', positionMs: 99999 }),
    );

    expect(stale).toBe(_state);
    expect(wrongTrack).toBe(_state);
  });

  it('compact progress parser accepts the SSE payload shape', () => {
    const progress = MusicBoxProgressEvent.fromJson({
      revision: 7,
      current_item_id: 'item-7',
      position_ms: 4567,
    });

    expect(progress.revision).toBe(7);
    expect(progress.currentItemId).toBe('item-7');
    expect(progress.positionMs).toBe(4567);
  });

  it('same-revision full heartbeat preserves actor-specific capabilities', () => {
    const capabilities = new MusicBoxCapabilities({ canControl: true, canChangeMode: true });
    const current = _state.copyWith({
      playback: new MusicBoxPlayback({
        state: MusicBoxPlaybackState.playing,
        currentItemId: 'priority-track',
        positionMs: 1000,
        volume: 100,
        updatedAt: null,
        capabilities,
      }),
    });
    const heartbeat = new MusicBoxState({
      enabled: true,
      playback: new MusicBoxPlayback({
        state: MusicBoxPlaybackState.playing,
        currentItemId: 'priority-track',
        positionMs: 2000,
        volume: 100,
        updatedAt: null,
      }),
      queue: _state.queue,
      usage: _state.usage,
      revision: 42,
      hasRevision: true,
    });

    const reduced = applyMusicBoxRealtimeSnapshot(current, heartbeat);

    expect(reduced).not.toBeNull();
    expect(reduced).not.toBe(heartbeat);
    expect(reduced!.playback.positionMs).toBe(2000);
    expect(reduced!.playback.capabilities).toBe(capabilities);
    expect(applyMusicBoxRealtimeSnapshot(reduced, heartbeat)).toBe(reduced);
  });

  it('newer structural snapshot is applied wholesale', () => {
    const incoming = new MusicBoxState({
      enabled: true,
      playback: _state.playback,
      queue: _state.queue,
      usage: _state.usage,
      revision: 43,
      hasRevision: true,
    });

    expect(applyMusicBoxRealtimeSnapshot(_state, incoming)).toBe(incoming);
  });
});
