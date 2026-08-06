import 'dart:async';
import 'dart:io';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

import '../app/music_track_preview.dart';

class DefaultMusicTrackPreviewPlatformFactory
    implements MusicTrackPreviewPlatformFactory {
  const DefaultMusicTrackPreviewPlatformFactory();

  @override
  MusicTrackPreviewPlatform create() => LocalMusicTrackPreviewPlatform();
}

/// Stores authenticated preview responses in the application cache and plays
/// them locally. Android uses a tiny native MediaPlayer bridge that neither
/// requests audio focus nor mutates AudioManager mode, so an active WebRTC
/// voice session keeps its routing and capture state. Desktop uses the existing
/// audioplayers dependency.
class LocalMusicTrackPreviewPlatform implements MusicTrackPreviewPlatform {
  static const int _maxCacheBytes = 256 << 20;

  LocalMusicTrackPreviewPlatform({
    AudioPlayer? desktopPlayer,
    MethodChannel? androidChannel,
    Future<Directory> Function()? cacheDirectoryProvider,
  }) : _desktopPlayer = desktopPlayer,
       _androidChannel =
           androidChannel ?? const MethodChannel('gang_chat/music_preview'),
       _cacheDirectoryProvider =
           cacheDirectoryProvider ?? getApplicationCacheDirectory {
    if (Platform.isAndroid) {
      _androidChannel.setMethodCallHandler(_handleAndroidMethod);
    } else {
      _ensureDesktopPlayer();
    }
  }

  AudioPlayer? _desktopPlayer;
  final MethodChannel _androidChannel;
  final Future<Directory> Function() _cacheDirectoryProvider;
  final StreamController<void> _completed = StreamController<void>.broadcast(
    sync: true,
  );
  StreamSubscription<void>? _desktopCompletion;
  Future<Directory>? _cacheDirectory;
  bool _desktopConfigured = false;
  bool _disposed = false;

  @override
  Stream<void> get onCompleted => _completed.stream;

  AudioPlayer _ensureDesktopPlayer() {
    final existing = _desktopPlayer;
    if (existing != null) {
      _desktopCompletion ??= existing.onPlayerComplete.listen((_) {
        if (!_disposed && !_completed.isClosed) _completed.add(null);
      });
      return existing;
    }
    final player = AudioPlayer();
    _desktopPlayer = player;
    _desktopCompletion = player.onPlayerComplete.listen((_) {
      if (!_disposed && !_completed.isClosed) _completed.add(null);
    });
    return player;
  }

  Future<Directory> _previewDirectory() {
    return _cacheDirectory ??= () async {
      final root = await _cacheDirectoryProvider();
      final directory = Directory(
        '${root.path}${Platform.pathSeparator}music-track-previews${Platform.pathSeparator}v1',
      );
      await directory.create(recursive: true);
      await _removeLegacyPreviews(directory);
      return directory;
    }();
  }

  Future<void> _removeLegacyPreviews(Directory directory) async {
    try {
      await for (final entity in directory.list(followLinks: false)) {
        if (entity is File &&
            (entity.path.endsWith('.ogg') || entity.path.endsWith('.mp3'))) {
          await entity.delete();
        }
      }
    } catch (_) {
      // A locked legacy file can be retried on the next application start.
    }
  }

  Future<File> _cacheFile(String cacheKey) async {
    final directory = await _previewDirectory();
    return File('${directory.path}${Platform.pathSeparator}$cacheKey.m4a');
  }

  @override
  Future<MusicTrackPreviewAsset?> findCached(String cacheKey) async {
    final file = await _cacheFile(cacheKey);
    try {
      if (await file.length() > 0) {
        await file.setLastModified(DateTime.now());
        return MusicTrackPreviewAsset(file.path);
      }
    } catch (_) {
      // Missing, empty, or unreadable files are treated as cache misses.
    }
    try {
      if (await file.exists()) await file.delete();
    } catch (_) {}
    return null;
  }

  @override
  Future<MusicTrackPreviewAsset> store(String cacheKey, Uint8List bytes) async {
    final file = await _cacheFile(cacheKey);
    final temporary = File(
      '${file.path}.tmp-${DateTime.now().microsecondsSinceEpoch}',
    );
    try {
      await temporary.writeAsBytes(bytes, flush: true);
      if (await file.exists()) await file.delete();
      await temporary.rename(file.path);
      await _trimCache(protectedPath: file.path);
    } finally {
      try {
        if (await temporary.exists()) await temporary.delete();
      } catch (_) {}
    }
    return MusicTrackPreviewAsset(file.path);
  }

  Future<void> _trimCache({required String protectedPath}) async {
    try {
      final directory = await _previewDirectory();
      final entries = <({File file, int bytes, DateTime modified})>[];
      var totalBytes = 0;
      await for (final entity in directory.list(followLinks: false)) {
        if (entity is! File || !entity.path.endsWith('.m4a')) continue;
        final stat = await entity.stat();
        totalBytes += stat.size;
        entries.add((file: entity, bytes: stat.size, modified: stat.modified));
      }
      if (totalBytes <= _maxCacheBytes) return;
      entries.sort((a, b) => a.modified.compareTo(b.modified));
      for (final entry in entries) {
        if (totalBytes <= _maxCacheBytes) break;
        if (entry.file.path == protectedPath) continue;
        await entry.file.delete();
        totalBytes -= entry.bytes;
      }
    } catch (_) {
      // Preview cache eviction is best effort and must never block playback.
    }
  }

  @override
  Future<void> play(MusicTrackPreviewAsset asset) async {
    if (_disposed) return;
    if (Platform.isAndroid) {
      await _androidChannel.invokeMethod<void>('play', {
        'path': asset.location,
      });
      return;
    }
    final player = _ensureDesktopPlayer();
    if (!_desktopConfigured) {
      await player.setReleaseMode(ReleaseMode.stop);
      if (Platform.isMacOS) {
        await player.setAudioContext(
          AudioContext(
            iOS: AudioContextIOS(
              category: AVAudioSessionCategory.playback,
              options: {AVAudioSessionOptions.mixWithOthers},
            ),
          ),
        );
      }
      _desktopConfigured = true;
    }
    await player.play(DeviceFileSource(asset.location));
  }

  @override
  Future<void> stop() async {
    if (Platform.isAndroid) {
      try {
        await _androidChannel.invokeMethod<void>('stop');
      } on MissingPluginException {
        // Tests and unsupported Android embeddings expose no native bridge.
      }
      return;
    }
    await _desktopPlayer?.stop();
  }

  @override
  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    if (Platform.isAndroid) {
      _androidChannel.setMethodCallHandler(null);
      try {
        await _androidChannel.invokeMethod<void>('dispose');
      } on MissingPluginException {
        // Tests and unsupported Android embeddings expose no native bridge.
      }
    }
    await _desktopCompletion?.cancel();
    await _desktopPlayer?.dispose();
    await _completed.close();
  }

  Future<void> _handleAndroidMethod(MethodCall call) async {
    if (call.method == 'completed' && !_disposed && !_completed.isClosed) {
      _completed.add(null);
    }
  }
}
