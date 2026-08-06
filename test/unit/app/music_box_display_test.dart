import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/music_box_display.dart';
import 'package:client/src/app/music_box_controller.dart';
import 'package:client/src/protocol/models.dart';

void main() {
  group('music box sources', () {
    test('temporarily excludes QQ Music from selectable sources', () {
      expect(
        musicBoxSources.map((source) => source.id),
        orderedEquals(const ['netease', 'bilibili']),
      );
      expect(musicBoxSourceEnabled('tencent'), isFalse);
    });

    test('normalizes a disabled or unknown source to the default', () {
      expect(normalizedMusicBoxSource('tencent'), musicBoxDefaultSource);
      expect(normalizedMusicBoxSource('unknown'), musicBoxDefaultSource);
      expect(normalizedMusicBoxSource('bilibili'), 'bilibili');
    });

    test('uses consistent labels without hiding future providers', () {
      expect(musicBoxSourceLabel('netease'), '网易云');
      expect(musicBoxSourceLabel('bilibili'), '哔哩哔哩');
      expect(musicBoxSourceLabel('future-source'), 'future-source');
      expect(musicBoxSourceLabel(''), '未知来源');
    });
  });

  group('music box active source labels', () {
    test('uses 点歌队列 for temporary sources from old servers', () {
      final parsed = MusicBoxState.fromJson({
        'enabled': true,
        'active_source': {'type': 'temporary', 'name': '临时歌单'},
      });

      expect(parsed.activeSource.name, musicBoxRequestQueueLabel);
      expect(
        musicBoxActiveSourceLabel(parsed.activeSource),
        musicBoxRequestQueueLabel,
      );
    });

    test('keeps saved playlist names and has scoped fallbacks', () {
      expect(
        musicBoxActiveSourceLabel(
          const MusicBoxActiveSource(
            type: MusicBoxActiveSourceType.roomPlaylist,
            name: '一起听',
          ),
        ),
        '一起听',
      );
      expect(
        musicBoxActiveSourceLabel(
          const MusicBoxActiveSource(
            type: MusicBoxActiveSourceType.userPlaylist,
            name: '   ',
          ),
        ),
        '个人歌单',
      );
    });

    test('parses the owner summary for a personal playlist', () {
      final parsed = MusicBoxState.fromJson({
        'enabled': true,
        'active_source': {
          'type': 'user_playlist',
          'playlist_id': 'playlist-1',
          'name': '通勤歌单',
          'owner_user_id': 'owner-1',
          'owner': {
            'user_id': 'owner-1',
            'username': 'owner_handle',
            'display_name': '点歌用户',
            'avatar_label': '全局用户',
            'avatar_url': '/avatar.png',
            'default_avatar_key': 'green-2',
          },
        },
      });

      expect(parsed.activeSource.ownerUserId, 'owner-1');
      expect(parsed.activeSource.owner?.username, 'owner_handle');
      expect(parsed.activeSource.owner?.displayName, '点歌用户');
      expect(parsed.activeSource.owner?.avatarLabel, '全局用户');
      expect(parsed.activeSource.owner?.avatarUrl, '/avatar.png');
    });
  });

  group('musicBoxProgress', () {
    test('renders the server-reported position as-is', () {
      final state = _state(
        playbackState: MusicBoxPlaybackState.playing,
        currentItemId: 'item-1',
        positionMs: 8000,
        queue: [_item(id: 'item-1', durationMs: 200000)],
      );

      final progress = musicBoxProgress(state);

      expect(progress.positionMs, 8000);
      expect(progress.durationMs, 200000);
      expect(progress.fraction, closeTo(0.04, 0.0001));
    });

    test('uses the recorded position regardless of playback state', () {
      final state = _state(
        playbackState: MusicBoxPlaybackState.paused,
        currentItemId: 'item-1',
        positionMs: 5000,
        queue: [_item(id: 'item-1', durationMs: 200000)],
      );

      final progress = musicBoxProgress(state);

      expect(progress.positionMs, 5000);
    });

    test('floors the position to whole seconds', () {
      final state = _state(
        playbackState: MusicBoxPlaybackState.playing,
        currentItemId: 'item-1',
        positionMs: 6900,
        queue: [_item(id: 'item-1', durationMs: 200000)],
      );

      final progress = musicBoxProgress(state);

      expect(progress.positionMs, 6000);
    });

    test('clamps the position to the track duration', () {
      final state = _state(
        playbackState: MusicBoxPlaybackState.playing,
        currentItemId: 'item-1',
        positionMs: 30000,
        queue: [_item(id: 'item-1', durationMs: 10000)],
      );

      final progress = musicBoxProgress(state);

      expect(progress.positionMs, 10000);
      expect(progress.fraction, 1.0);
    });

    test('fraction is zero when duration is unknown', () {
      final state = _state(
        playbackState: MusicBoxPlaybackState.playing,
        currentItemId: 'item-1',
        positionMs: 5000,
        queue: [_item(id: 'item-1', durationMs: 0)],
      );

      final progress = musicBoxProgress(state);

      expect(progress.fraction, 0);
    });
  });

  group('musicBoxPrimaryTransport', () {
    test('maps playback state to the transport action', () {
      expect(
        musicBoxPrimaryTransport(
          _state(playbackState: MusicBoxPlaybackState.playing),
        ),
        MusicBoxTransportAction.pause,
      );
      expect(
        musicBoxPrimaryTransport(
          _state(playbackState: MusicBoxPlaybackState.paused),
        ),
        MusicBoxTransportAction.resume,
      );
      expect(
        musicBoxPrimaryTransport(
          _state(playbackState: MusicBoxPlaybackState.stopped),
        ),
        MusicBoxTransportAction.play,
      );
    });

    test('maps transport action to the API verb', () {
      expect(musicBoxTransportApiAction(MusicBoxTransportAction.play), 'play');
      expect(
        musicBoxTransportApiAction(MusicBoxTransportAction.pause),
        'pause',
      );
      expect(
        musicBoxTransportApiAction(MusicBoxTransportAction.resume),
        'resume',
      );
    });
  });

  group('musicBoxRecordSpinning', () {
    test('spins only while playing', () {
      expect(
        musicBoxRecordSpinning(
          _state(playbackState: MusicBoxPlaybackState.playing),
        ),
        isTrue,
      );
      expect(
        musicBoxRecordSpinning(
          _state(playbackState: MusicBoxPlaybackState.paused),
        ),
        isFalse,
      );
    });
  });

  group('musicBoxQueueStatusLabel', () {
    test('labels each lifecycle stage', () {
      expect(
        musicBoxQueueStatusLabel(
          _item(status: MusicBoxQueueItemStatus.pending),
        ),
        '排队中，等待下载',
      );
      expect(
        musicBoxQueueStatusLabel(
          _item(status: MusicBoxQueueItemStatus.downloading),
        ),
        '下载中',
      );
      expect(
        musicBoxQueueStatusLabel(_item(status: MusicBoxQueueItemStatus.ready)),
        isNull,
      );
    });

    test('uses the server error for failed items, with a fallback', () {
      expect(
        musicBoxQueueStatusLabel(
          _item(status: MusicBoxQueueItemStatus.failed, error: '版权限制'),
        ),
        '版权限制',
      );
      expect(
        musicBoxQueueStatusLabel(_item(status: MusicBoxQueueItemStatus.failed)),
        '处理失败',
      );
    });
  });

  group('musicBoxUsageHint', () {
    test('warns near and at the limit', () {
      expect(
        musicBoxUsageHint(const MusicBoxUsage(usedBytes: 50, limitBytes: 100)),
        isNull,
      );
      expect(
        musicBoxUsageHint(const MusicBoxUsage(usedBytes: 92, limitBytes: 100)),
        '空间已接近上限',
      );
      expect(
        musicBoxUsageHint(const MusicBoxUsage(usedBytes: 100, limitBytes: 100)),
        '空间已满，新歌将排队等待下载',
      );
      expect(
        musicBoxUsageHint(const MusicBoxUsage(usedBytes: 0, limitBytes: 0)),
        isNull,
      );
    });

    test('usage fraction is clamped', () {
      expect(
        musicBoxUsageFraction(
          const MusicBoxUsage(usedBytes: 25, limitBytes: 100),
        ),
        0.25,
      );
      expect(
        musicBoxUsageFraction(
          const MusicBoxUsage(usedBytes: 200, limitBytes: 100),
        ),
        1.0,
      );
      expect(
        musicBoxUsageFraction(const MusicBoxUsage(usedBytes: 5, limitBytes: 0)),
        0,
      );
    });
  });

  group('formatting', () {
    test('formats durations as mm:ss and h:mm:ss', () {
      expect(musicBoxFormatDuration(0), '--:--');
      expect(musicBoxFormatDuration(65000), '1:05');
      expect(musicBoxFormatDuration(3725000), '1:02:05');
    });

    test('formats byte sizes', () {
      expect(musicBoxFormatBytes(0), '0 B');
      expect(musicBoxFormatBytes(512), '512 B');
      expect(musicBoxFormatBytes(1536), '1.5 KB');
      expect(musicBoxFormatBytes(5 * 1024 * 1024), '5 MB');
    });

    test('joins artist lists, dropping blanks', () {
      expect(musicBoxArtistsLabel(['林俊杰', '孙燕姿']), '林俊杰、孙燕姿');
      expect(musicBoxArtistsLabel(['', '  ', '周杰伦']), '周杰伦');
      expect(musicBoxArtistsLabel(const []), '');
    });
  });

  group('MusicBoxState.fromJson', () {
    test('parses a full snapshot and resolves the current item', () {
      final state = MusicBoxState.fromJson({
        'enabled': true,
        'playback': {
          'state': 'playing',
          'current_item_id': 'item-2',
          'position_ms': 1200,
          'volume': 80,
          'updated_at': '2026-01-01T12:00:00Z',
        },
        'queue': [
          {'id': 'item-1', 'title': 'A', 'status': 'ready'},
          {
            'id': 'item-2',
            'title': 'B',
            'artist': 'Artist',
            'status': 'downloading',
            'duration_ms': 180000,
          },
        ],
        'usage': {'used_bytes': 10, 'limit_bytes': 100},
      });

      expect(state.enabled, isTrue);
      expect(state.playback.state, MusicBoxPlaybackState.playing);
      expect(state.playback.hasCurrent, isTrue);
      expect(state.currentItem?.title, 'B');
      expect(state.queue, hasLength(2));
      expect(state.queue.first.status, MusicBoxQueueItemStatus.ready);
      expect(state.usage.limitBytes, 100);
    });

    test('defaults a missing playback block to stopped', () {
      final state = MusicBoxState.fromJson({'enabled': false});

      expect(state.enabled, isFalse);
      expect(state.playback.state, MusicBoxPlaybackState.stopped);
      expect(state.currentItem, isNull);
      expect(state.queue, isEmpty);
    });

    test(
      'parses revision, active saved source and independent temporary queue',
      () {
        final state = MusicBoxState.fromJson({
          'enabled': true,
          'revision': 12,
          'active_source': {
            'type': 'room_playlist',
            'playlist_id': 'playlist-1',
            'name': 'Shared favorites',
          },
          'playback': {
            'state': 'playing',
            'current_item_id': 'saved-1',
            'mode': 'repeat_all',
            'can_previous': true,
            'can_next': true,
            'capabilities': {
              'allowed_modes': ['sequential', 'repeat_one', 'repeat_all'],
            },
          },
          'queue': [
            {
              'id': 'saved-1',
              'title': 'Saved song',
              'status': 'ready',
              'can_play_now': true,
              'requested_by': <String, Object?>{
                'user_id': 'user-1',
                'display_name': '房间专属名',
                'avatar_label': 'Alice',
                'avatar_url': '/avatar.png',
              },
            },
          ],
          'temporary_queue': [
            {
              'id': 'temporary-1',
              'title': 'Requested song',
              'status': 'pending',
            },
          ],
          'temporary_playlist': {'queued_count': 1},
          'usage': {},
        });

        expect(state.hasRevision, isTrue);
        expect(state.revision, 12);
        expect(state.activeSource.type, MusicBoxActiveSourceType.roomPlaylist);
        expect(state.activeSource.id, 'playlist-1');
        expect(state.playback.mode, MusicBoxPlaybackMode.repeatAll);
        expect(state.currentItem?.canPlayNow, isTrue);
        expect(state.currentItem?.requestedBy?.displayName, '房间专属名');
        expect(state.currentItem?.requestedBy?.avatarLabel, 'Alice');
        expect(state.temporaryQueue.single.id, 'temporary-1');
        expect(state.temporaryQueuedCount, 1);
      },
    );

    test('rejects stale revision snapshots but keeps legacy compatibility', () {
      final current = MusicBoxState.fromJson({
        'enabled': true,
        'revision': 5,
        'playback': {'current_item_id': 'current'},
      });
      final stale = MusicBoxState.fromJson({
        'enabled': true,
        'revision': 4,
        'playback': {'current_item_id': 'old'},
      });
      final newer = MusicBoxState.fromJson({
        'enabled': true,
        'revision': 6,
        'playback': {'current_item_id': 'new'},
      });
      final legacy = MusicBoxState.fromJson({'enabled': true});

      expect(shouldAcceptMusicBoxSnapshot(current, stale), isFalse);
      expect(shouldAcceptMusicBoxSnapshot(current, newer), isTrue);
      expect(shouldAcceptMusicBoxSnapshot(current, legacy), isTrue);
    });
  });
}

MusicBoxState _state({
  bool enabled = true,
  MusicBoxPlaybackState playbackState = MusicBoxPlaybackState.stopped,
  String currentItemId = '',
  int positionMs = 0,
  DateTime? updatedAt,
  List<MusicBoxQueueItem> queue = const [],
}) {
  return MusicBoxState(
    enabled: enabled,
    playback: MusicBoxPlayback(
      state: playbackState,
      currentItemId: currentItemId,
      positionMs: positionMs,
      volume: 100,
      updatedAt: updatedAt,
    ),
    queue: queue,
    usage: const MusicBoxUsage(usedBytes: 0, limitBytes: 0),
  );
}

MusicBoxQueueItem _item({
  String id = 'item',
  String title = 'Song',
  String artist = '',
  MusicBoxQueueItemStatus status = MusicBoxQueueItemStatus.ready,
  int durationMs = 0,
  String error = '',
  String addedByUserId = 'user',
}) {
  return MusicBoxQueueItem(
    id: id,
    source: 'netease',
    trackId: 'track-$id',
    title: title,
    artist: artist,
    durationMs: durationMs,
    status: status,
    fileSizeBytes: 0,
    error: error,
    addedByUserId: addedByUserId,
    createdAt: null,
  );
}
