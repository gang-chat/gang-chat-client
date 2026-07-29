import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/music_box_display.dart';
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
