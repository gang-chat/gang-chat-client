import 'package:client/src/app/music_box_controller.dart';
import 'package:client/src/protocol/api_client.dart';
import 'package:client/src/protocol/models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'playNow sends an idempotent item command without a stale revision',
    () async {
      final api = _RecordingMusicBoxApi(_state);
      final controller = MusicBoxController(api: api);

      await controller.playNow(roomId: 'room-1', item: _item);

      expect(api.roomId, 'room-1');
      expect(api.action, 'play_now');
      expect(api.itemId, _item.id);
      expect(api.commandId, isNotEmpty);
      expect(api.expectedRevision, isNull);
    },
  );

  test('music box command errors preserve localized API feedback', () {
    final error = ApiException(
      '歌曲尚未准备完成',
      statusCode: 409,
      code: 'music_box_item_not_ready',
      requestId: 'request-1',
    );

    expect(musicBoxControlErrorMessage(error, '优先播放失败，请重试'), '歌曲尚未准备完成');
    expect(
      musicBoxControlErrorMessage(Exception('offline'), '优先播放失败，请重试'),
      '优先播放失败，请重试',
    );
  });

  test('compact progress updates only its matching authoritative snapshot', () {
    final matching = applyMusicBoxProgress(
      _state,
      const MusicBoxProgressEvent(
        revision: 42,
        currentItemId: 'priority-track',
        positionMs: 12345,
      ),
    );

    expect(matching, isNotNull);
    expect(matching!.playback.positionMs, 12345);
    expect(matching.queue, same(_state.queue));

    final stale = applyMusicBoxProgress(
      _state,
      const MusicBoxProgressEvent(
        revision: 41,
        currentItemId: 'priority-track',
        positionMs: 99999,
      ),
    );
    final wrongTrack = applyMusicBoxProgress(
      _state,
      const MusicBoxProgressEvent(
        revision: 42,
        currentItemId: 'another-track',
        positionMs: 99999,
      ),
    );

    expect(stale, same(_state));
    expect(wrongTrack, same(_state));
  });

  test('compact progress parser accepts the SSE payload shape', () {
    final progress = MusicBoxProgressEvent.fromJson({
      'revision': 7,
      'current_item_id': 'item-7',
      'position_ms': 4567,
    });

    expect(progress.revision, 7);
    expect(progress.currentItemId, 'item-7');
    expect(progress.positionMs, 4567);
  });

  test(
    'same-revision full heartbeat preserves actor-specific capabilities',
    () {
      const capabilities = MusicBoxCapabilities(
        canControl: true,
        canChangeMode: true,
      );
      final current = _state.copyWith(
        playback: const MusicBoxPlayback(
          state: MusicBoxPlaybackState.playing,
          currentItemId: 'priority-track',
          positionMs: 1000,
          volume: 100,
          updatedAt: null,
          capabilities: capabilities,
        ),
      );
      final heartbeat = MusicBoxState(
        enabled: true,
        playback: const MusicBoxPlayback(
          state: MusicBoxPlaybackState.playing,
          currentItemId: 'priority-track',
          positionMs: 2000,
          volume: 100,
          updatedAt: null,
        ),
        queue: _state.queue,
        usage: _state.usage,
        revision: 42,
        hasRevision: true,
      );

      final reduced = applyMusicBoxRealtimeSnapshot(current, heartbeat);

      expect(reduced, isNotNull);
      expect(reduced, isNot(same(heartbeat)));
      expect(reduced!.playback.positionMs, 2000);
      expect(reduced.playback.capabilities, same(capabilities));
      expect(applyMusicBoxRealtimeSnapshot(reduced, heartbeat), same(reduced));
    },
  );

  test('newer structural snapshot is applied wholesale', () {
    final incoming = MusicBoxState(
      enabled: true,
      playback: _state.playback,
      queue: _state.queue,
      usage: _state.usage,
      revision: 43,
      hasRevision: true,
    );

    expect(applyMusicBoxRealtimeSnapshot(_state, incoming), same(incoming));
  });
}

const _item = MusicBoxQueueItem(
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
);

const _state = MusicBoxState(
  enabled: true,
  playback: MusicBoxPlayback(
    state: MusicBoxPlaybackState.playing,
    currentItemId: 'priority-track',
    positionMs: 0,
    volume: 100,
    updatedAt: null,
  ),
  queue: [_item],
  usage: MusicBoxUsage(usedBytes: 1024, limitBytes: 200 * 1024 * 1024),
  revision: 42,
  hasRevision: true,
);

class _RecordingMusicBoxApi implements GangApi {
  _RecordingMusicBoxApi(this.result);

  final MusicBoxState result;
  String? roomId;
  String? action;
  String? itemId;
  String? commandId;
  int? expectedRevision;

  @override
  Future<MusicBoxState> controlMusicBox({
    required String roomId,
    required String action,
    String? itemId,
    String? mode,
    String? commandId,
    int? expectedRevision,
  }) async {
    this.roomId = roomId;
    this.action = action;
    this.itemId = itemId;
    this.commandId = commandId;
    this.expectedRevision = expectedRevision;
    return result;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}
