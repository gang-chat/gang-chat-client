import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/music_track_preview.dart';
import 'package:client/src/protocol/api_client.dart';

void main() {
  test('downloads, caches, plays, and stops one preview', () async {
    final api = _FakePreviewApi();
    final platform = _FakePreviewPlatform();
    final controller = MusicTrackPreviewController(
      api: api,
      platform: platform,
    );
    const track = MusicTrackPreviewTrack(source: 'netease', trackId: 'track-1');

    final future = controller.preview(track);
    await Future<void>.delayed(Duration.zero);
    expect(controller.snapshot.isLoading(track.key), isTrue);
    api.download.complete(
      DownloadedFile(
        bytes: Uint8List.fromList([1, 2, 3]),
        filename: 'preview.m4a',
        mimeType: 'audio/mp4',
      ),
    );
    await future;

    expect(api.requests, 1);
    expect(platform.storedBytes, [1, 2, 3]);
    expect(platform.played, [track.key]);
    expect(controller.snapshot.isPlaying(track.key), isTrue);

    await controller.stopIf(track.key);
    expect(controller.snapshot.phase, MusicTrackPreviewPhase.idle);
    expect(platform.stopCalls, greaterThanOrEqualTo(2));
    await controller.dispose();
  });

  test(
    'closing while loading may cache but never starts stale playback',
    () async {
      final api = _FakePreviewApi();
      final platform = _FakePreviewPlatform();
      final controller = MusicTrackPreviewController(
        api: api,
        platform: platform,
      );
      const track = MusicTrackPreviewTrack(
        source: 'bilibili',
        trackId: 'BV1test',
      );

      final future = controller.preview(track);
      await Future<void>.delayed(Duration.zero);
      await controller.stopIf(track.key);
      api.download.complete(
        DownloadedFile(
          bytes: Uint8List.fromList([4, 5, 6]),
          filename: 'preview.m4a',
          mimeType: 'audio/mp4',
        ),
      );
      await future;

      expect(platform.storedBytes, [4, 5, 6]);
      expect(platform.played, isEmpty);
      expect(controller.snapshot.phase, MusicTrackPreviewPhase.idle);
      await controller.dispose();
    },
  );

  test(
    'closing during the initial player stop prevents a late preview',
    () async {
      final api = _FakePreviewApi();
      final platform = _FakePreviewPlatform()..nextStop = Completer<void>();
      final controller = MusicTrackPreviewController(
        api: api,
        platform: platform,
      );
      const track = MusicTrackPreviewTrack(
        source: 'netease',
        trackId: 'stop-race',
      );

      final preview = controller.preview(track);
      expect(controller.snapshot.isLoading(track.key), isTrue);
      await controller.stopIf(track.key);
      platform.nextStopCompletion?.complete();
      await preview;

      expect(api.requests, 0);
      expect(platform.played, isEmpty);
      expect(controller.snapshot.phase, MusicTrackPreviewPhase.idle);
      await controller.dispose();
    },
  );

  test('player completion clears the cancel-preview state', () async {
    final api = _FakePreviewApi();
    final platform = _FakePreviewPlatform()..cached = true;
    final controller = MusicTrackPreviewController(
      api: api,
      platform: platform,
    );
    const track = MusicTrackPreviewTrack(source: 'netease', trackId: 'cached');

    await controller.preview(track);
    expect(controller.snapshot.isPlaying(track.key), isTrue);
    platform.complete();
    await Future<void>.delayed(Duration.zero);
    expect(controller.snapshot.phase, MusicTrackPreviewPhase.idle);
    await controller.dispose();
  });
}

class _FakePreviewApi implements MusicTrackPreviewApi {
  final Completer<DownloadedFile> download = Completer<DownloadedFile>();
  int requests = 0;

  @override
  Future<DownloadedFile> downloadMusicTrackPreview({
    required String source,
    required String trackId,
  }) {
    requests += 1;
    return download.future;
  }
}

class _FakePreviewPlatform implements MusicTrackPreviewPlatform {
  final StreamController<void> _completed = StreamController<void>.broadcast();
  bool cached = false;
  Uint8List? storedBytes;
  final List<String> played = [];
  int stopCalls = 0;
  Completer<void>? nextStop;
  Completer<void>? nextStopCompletion;

  @override
  Stream<void> get onCompleted => _completed.stream;

  @override
  Future<MusicTrackPreviewAsset?> findCached(String cacheKey) async {
    return cached ? MusicTrackPreviewAsset(cacheKey) : null;
  }

  @override
  Future<MusicTrackPreviewAsset> store(String cacheKey, Uint8List bytes) async {
    storedBytes = bytes;
    cached = true;
    return MusicTrackPreviewAsset(cacheKey);
  }

  @override
  Future<void> play(MusicTrackPreviewAsset asset) async {
    played.add(asset.location);
  }

  @override
  Future<void> stop() async {
    stopCalls += 1;
    final pending = nextStop;
    if (pending != null) {
      nextStop = null;
      nextStopCompletion = pending;
      await pending.future;
    }
  }

  void complete() => _completed.add(null);

  @override
  Future<void> dispose() => _completed.close();
}
