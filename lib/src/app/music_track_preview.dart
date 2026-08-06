import 'dart:async';
import 'dart:typed_data';

import '../protocol/api_client.dart';

enum MusicTrackPreviewPhase { idle, loading, playing }

class MusicTrackPreviewTrack {
  const MusicTrackPreviewTrack({required this.source, required this.trackId});

  final String source;
  final String trackId;

  String get key => musicTrackPreviewCacheKey(source, trackId);
}

class MusicTrackPreviewSnapshot {
  const MusicTrackPreviewSnapshot({
    this.phase = MusicTrackPreviewPhase.idle,
    this.activeTrackKey,
  });

  final MusicTrackPreviewPhase phase;
  final String? activeTrackKey;

  bool isLoading(String trackKey) =>
      activeTrackKey == trackKey && phase == MusicTrackPreviewPhase.loading;

  bool isPlaying(String trackKey) =>
      activeTrackKey == trackKey && phase == MusicTrackPreviewPhase.playing;
}

class MusicTrackPreviewAsset {
  const MusicTrackPreviewAsset(this.location);

  /// Opaque platform locator. The default implementation uses a local path.
  final String location;
}

abstract interface class MusicTrackPreviewPlatform {
  Stream<void> get onCompleted;

  Future<MusicTrackPreviewAsset?> findCached(String cacheKey);

  Future<MusicTrackPreviewAsset> store(String cacheKey, Uint8List bytes);

  Future<void> play(MusicTrackPreviewAsset asset);

  Future<void> stop();

  Future<void> dispose();
}

abstract interface class MusicTrackPreviewPlatformFactory {
  MusicTrackPreviewPlatform create();
}

/// Owns one local preview at a time. Downloading may finish and populate the
/// cache after a card closes, but the generation check prevents stale work
/// from ever starting playback.
class MusicTrackPreviewController {
  MusicTrackPreviewController({
    required MusicTrackPreviewApi api,
    required MusicTrackPreviewPlatform platform,
  }) : _api = api,
       _platform = platform {
    _completionSubscription = _platform.onCompleted.listen((_) {
      if (_disposed || _snapshot.phase != MusicTrackPreviewPhase.playing) {
        return;
      }
      _publish(const MusicTrackPreviewSnapshot());
    });
  }

  final MusicTrackPreviewApi _api;
  final MusicTrackPreviewPlatform _platform;
  final StreamController<MusicTrackPreviewSnapshot> _changes =
      StreamController<MusicTrackPreviewSnapshot>.broadcast(sync: true);
  late final StreamSubscription<void> _completionSubscription;
  MusicTrackPreviewSnapshot _snapshot = const MusicTrackPreviewSnapshot();
  int _generation = 0;
  bool _disposed = false;

  MusicTrackPreviewSnapshot get snapshot => _snapshot;
  Stream<MusicTrackPreviewSnapshot> get changes => _changes.stream;

  Future<void> toggle(MusicTrackPreviewTrack track) async {
    final key = track.key;
    if (_snapshot.isPlaying(key)) {
      await stop();
      return;
    }
    await preview(track);
  }

  Future<void> preview(MusicTrackPreviewTrack track) async {
    if (_disposed) return;
    final generation = ++_generation;
    _publish(
      MusicTrackPreviewSnapshot(
        phase: MusicTrackPreviewPhase.loading,
        activeTrackKey: track.key,
      ),
    );

    try {
      await _platform.stop();
      if (_disposed || generation != _generation) return;
      var asset = await _platform.findCached(track.key);
      if (asset == null) {
        final downloaded = await _api.downloadMusicTrackPreview(
          source: track.source,
          trackId: track.trackId,
        );
        if (downloaded.bytes.isEmpty) {
          throw StateError('试听文件为空');
        }
        asset = await _platform.store(track.key, downloaded.bytes);
      }
      if (_disposed || generation != _generation) return;
      await _platform.play(asset);
      if (_disposed || generation != _generation) {
        await _platform.stop();
        return;
      }
      _publish(
        MusicTrackPreviewSnapshot(
          phase: MusicTrackPreviewPhase.playing,
          activeTrackKey: track.key,
        ),
      );
    } catch (_) {
      if (!_disposed && generation == _generation) {
        _publish(const MusicTrackPreviewSnapshot());
      }
      rethrow;
    }
  }

  Future<void> stopIf(String trackKey) async {
    if (_snapshot.activeTrackKey != trackKey) return;
    await stop();
  }

  Future<void> stop() async {
    if (_disposed) return;
    _generation += 1;
    await _platform.stop();
    if (!_disposed) _publish(const MusicTrackPreviewSnapshot());
  }

  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    _generation += 1;
    await _completionSubscription.cancel();
    await _platform.stop();
    await _platform.dispose();
    await _changes.close();
  }

  void _publish(MusicTrackPreviewSnapshot next) {
    _snapshot = next;
    if (!_changes.isClosed) _changes.add(next);
  }
}

String musicTrackPreviewCacheKey(String source, String trackId) {
  final value = '${source.trim()}\u0000${trackId.trim()}';
  const offset = 0xcbf29ce484222325;
  const prime = 0x100000001b3;
  var hash = offset;
  for (final unit in value.codeUnits) {
    hash ^= unit;
    hash = (hash * prime) & 0x7fffffffffffffff;
  }
  return hash.toRadixString(16).padLeft(16, '0');
}
