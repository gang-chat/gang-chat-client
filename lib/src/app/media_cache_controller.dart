import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';

import 'file_transfer_state.dart';
import 'fnv_hash.dart';
import '../config/host_platform.dart';

const _mediaCacheVersion = 'v1';

/// The web has no file system, so recently used media is kept in memory, up
/// to this many bytes.
const _webMemoryCacheMaxBytes = 64 * 1024 * 1024;

typedef MediaCacheDirectoryProvider = Future<Directory> Function();
typedef MediaCacheProgressHandler =
    void Function({required int sentBytes, required int totalBytes});

class MediaCacheCancelledException implements Exception {
  const MediaCacheCancelledException();

  @override
  String toString() => '下载已取消';
}

class MediaCacheRequest {
  const MediaCacheRequest._({
    required this.uri,
    required this.cacheKey,
    required this.cacheFilename,
    this.expectedBytes,
  });

  final Uri uri;
  final String cacheKey;
  final String cacheFilename;
  final int? expectedBytes;

  static MediaCacheRequest? tryFromUrl({
    required String? url,
    String? filename,
    String? mimeType,
    int? expectedBytes,
    String namespace = 'asset',
  }) {
    final trimmed = url?.trim();
    if (trimmed == null || trimmed.isEmpty) return null;
    final uri = Uri.tryParse(trimmed);
    if (uri == null || !_isHttpUri(uri)) return null;

    final sourceName = _firstNonEmpty([
      filename,
      _basenameFromUriPath(uri.path),
      'asset',
    ])!;
    final extension = _extensionFor(
      filename: sourceName,
      uriPath: uri.path,
      mimeType: mimeType,
    );
    final stem = _safeStem(sourceName, extension: extension);
    final key = '$namespace:${uri.toString()}';
    final hash = _fnv1a64Hex(key);
    return MediaCacheRequest._(
      uri: uri,
      cacheKey: hash,
      cacheFilename: '$stem-$hash$extension',
      expectedBytes: expectedBytes,
    );
  }
}

class MediaCacheController {
  MediaCacheController({
    http.Client Function()? httpClientFactory,
    MediaCacheDirectoryProvider? cacheDirectoryProvider,
  }) : _httpClientFactory = httpClientFactory ?? http.Client.new,
       _cacheDirectoryProvider =
           cacheDirectoryProvider ?? getApplicationCacheDirectory;

  final http.Client Function() _httpClientFactory;
  final MediaCacheDirectoryProvider _cacheDirectoryProvider;
  final Map<String, Future<File>> _inFlight = {};
  // Web only: insertion order doubles as recency (oldest first).
  final Map<String, Uint8List> _webMemory = {};
  var _webMemoryBytes = 0;
  final Map<String, Future<Uint8List>> _webInFlight = {};

  Future<File?> cachedFile(MediaCacheRequest request) async {
    final file = await _cacheFile(request);
    try {
      if (!await file.exists()) return null;
      if (await file.length() <= 0) return null;
      return file;
    } catch (_) {
      return null;
    }
  }

  Future<File> getOrDownload({
    required MediaCacheRequest request,
    FileTransferState? transfer,
    MediaCacheProgressHandler? onProgress,
  }) async {
    final existing = await cachedFile(request);
    if (existing != null) return existing;

    final active = _inFlight[request.cacheKey];
    if (active != null) return active;

    final future = _downloadToCache(
      request: request,
      transfer: transfer,
      onProgress: onProgress,
    );
    _inFlight[request.cacheKey] = future;
    try {
      return await future;
    } finally {
      if (identical(_inFlight[request.cacheKey], future)) {
        _inFlight.remove(request.cacheKey);
      }
    }
  }

  Future<List<int>> readBytes({required MediaCacheRequest request}) {
    return getOrDownloadBytes(request: request);
  }

  /// The media's bytes. Native builds read them through the on-disk cache;
  /// the web has no file system, so it downloads into a bounded in-memory
  /// cache instead. Use this rather than [getOrDownload] in code that also
  /// runs on the web.
  ///
  /// [transfer] and [onProgress] report progress and allow pausing and
  /// cancelling, as for [getOrDownload].
  Future<Uint8List> getOrDownloadBytes({
    required MediaCacheRequest request,
    FileTransferState? transfer,
    MediaCacheProgressHandler? onProgress,
  }) async {
    if (!kIsWeb) {
      final file = await getOrDownload(
        request: request,
        transfer: transfer,
        onProgress: onProgress,
      );
      return file.readAsBytes();
    }
    final key = request.cacheKey;
    final cached = _webMemory.remove(key);
    if (cached != null) {
      _webMemory[key] = cached;
      return cached;
    }
    final active = _webInFlight[key];
    if (active != null) return active;

    final future = _downloadBytes(
      request,
      transfer: transfer,
      onProgress: onProgress,
    );
    _webInFlight[key] = future;
    try {
      final bytes = await future;
      _rememberWebBytes(key, bytes);
      return bytes;
    } finally {
      if (identical(_webInFlight[key], future)) _webInFlight.remove(key);
    }
  }

  Future<Uint8List> _downloadBytes(
    MediaCacheRequest request, {
    FileTransferState? transfer,
    MediaCacheProgressHandler? onProgress,
  }) async {
    final client = _httpClientFactory();
    if (transfer != null) transfer.downloadClient = client;
    try {
      final response = await client.send(http.Request('GET', request.uri));
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw StateError('下载失败 (${response.statusCode})');
      }
      // Browsers return the decoded body, so Content-Length (the encoded
      // size) can differ; only the size recorded for the file is reliable.
      final expected = request.expectedBytes;
      final total = expected ?? response.contentLength ?? 0;
      transfer?.updateProgress(sentBytes: 0, totalBytes: total);
      onProgress?.call(sentBytes: 0, totalBytes: total);

      final builder = BytesBuilder(copy: false);
      await for (final chunk in response.stream) {
        await _waitIfPaused(transfer);
        if (transfer?.cancelled == true) {
          throw const MediaCacheCancelledException();
        }
        builder.add(chunk);
        transfer?.updateProgress(sentBytes: builder.length, totalBytes: total);
        onProgress?.call(sentBytes: builder.length, totalBytes: total);
      }
      if (transfer?.cancelled == true) {
        throw const MediaCacheCancelledException();
      }
      final bytes = builder.takeBytes();
      if (expected != null && expected > 0 && expected != bytes.length) {
        throw StateError('下载不完整');
      }
      return bytes;
    } catch (_) {
      // Cancelling closes the client, which surfaces as a transport error.
      if (transfer?.cancelled == true) {
        throw const MediaCacheCancelledException();
      }
      rethrow;
    } finally {
      if (transfer != null && identical(transfer.downloadClient, client)) {
        transfer.downloadClient = null;
      }
      client.close();
    }
  }

  void _rememberWebBytes(String key, Uint8List bytes) {
    // A single file larger than a quarter of the budget would evict most of
    // the cache; serve it without keeping it.
    if (bytes.length > _webMemoryCacheMaxBytes ~/ 4) return;
    final previous = _webMemory.remove(key);
    if (previous != null) _webMemoryBytes -= previous.length;
    _webMemory[key] = bytes;
    _webMemoryBytes += bytes.length;
    while (_webMemoryBytes > _webMemoryCacheMaxBytes && _webMemory.isNotEmpty) {
      final oldest = _webMemory.keys.first;
      _webMemoryBytes -= _webMemory.remove(oldest)!.length;
    }
  }

  Future<void> copyFileToPath({
    required File source,
    required String destinationPath,
    FileTransferState? transfer,
    MediaCacheProgressHandler? onProgress,
  }) async {
    if (source.path == destinationPath) {
      final total = await source.length();
      transfer?.updateProgress(sentBytes: total, totalBytes: total);
      onProgress?.call(sentBytes: total, totalBytes: total);
      return;
    }

    final destination = File(destinationPath);
    await destination.parent.create(recursive: true);
    final temp = File(
      '$destinationPath.gangtmp-${DateTime.now().microsecondsSinceEpoch}',
    );
    IOSink? sink;
    var received = 0;
    final total = await source.length();
    try {
      sink = temp.openWrite();
      transfer?.updateProgress(sentBytes: 0, totalBytes: total);
      onProgress?.call(sentBytes: 0, totalBytes: total);
      await for (final chunk in source.openRead()) {
        await _waitIfPaused(transfer);
        if (transfer?.cancelled == true) {
          throw const MediaCacheCancelledException();
        }
        sink.add(chunk);
        received += chunk.length;
        transfer?.updateProgress(sentBytes: received, totalBytes: total);
        onProgress?.call(sentBytes: received, totalBytes: total);
      }
      await sink.flush();
      await sink.close();
      sink = null;

      if (transfer?.cancelled == true) {
        throw const MediaCacheCancelledException();
      }
      if (await destination.exists()) await destination.delete();
      await temp.rename(destinationPath);
      if (transfer != null) transfer.wroteDestination = true;
    } on MediaCacheCancelledException {
      await _deleteQuietly(temp);
      rethrow;
    } catch (_) {
      await _deleteQuietly(temp);
      rethrow;
    } finally {
      if (sink != null) {
        try {
          await sink.close();
        } catch (_) {}
      }
    }
  }

  Future<File> _downloadToCache({
    required MediaCacheRequest request,
    FileTransferState? transfer,
    MediaCacheProgressHandler? onProgress,
  }) async {
    final file = await _cacheFile(request);
    await file.parent.create(recursive: true);
    final temp = File(
      '${file.path}.tmp-${DateTime.now().microsecondsSinceEpoch}',
    );

    http.Client? client;
    IOSink? sink;
    var received = 0;
    try {
      client = _httpClientFactory();
      if (transfer != null) transfer.downloadClient = client;
      final response = await client.send(http.Request('GET', request.uri));
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw StateError('下载失败 (${response.statusCode})');
      }
      final total = response.contentLength ?? request.expectedBytes ?? 0;
      transfer?.updateProgress(sentBytes: 0, totalBytes: total);
      onProgress?.call(sentBytes: 0, totalBytes: total);

      sink = temp.openWrite();
      await for (final chunk in response.stream) {
        await _waitIfPaused(transfer);
        if (transfer?.cancelled == true) {
          throw const MediaCacheCancelledException();
        }
        sink.add(chunk);
        received += chunk.length;
        transfer?.updateProgress(sentBytes: received, totalBytes: total);
        onProgress?.call(sentBytes: received, totalBytes: total);
      }
      await sink.flush();
      await sink.close();
      sink = null;

      if (transfer?.cancelled == true) {
        throw const MediaCacheCancelledException();
      }
      final expectedLength = response.contentLength ?? request.expectedBytes;
      if (expectedLength != null &&
          expectedLength > 0 &&
          expectedLength != received) {
        throw StateError('下载不完整');
      }
      if (await file.exists()) await file.delete();
      return await temp.rename(file.path);
    } on MediaCacheCancelledException {
      await _deleteQuietly(temp);
      rethrow;
    } catch (_) {
      await _deleteQuietly(temp);
      rethrow;
    } finally {
      if (transfer != null && identical(transfer.downloadClient, client)) {
        transfer.downloadClient = null;
      }
      client?.close();
      if (sink != null) {
        try {
          await sink.close();
        } catch (_) {}
      }
    }
  }

  Future<File> _cacheFile(MediaCacheRequest request) async {
    final root = await _cacheDirectoryProvider();
    final separator = HostPlatform.pathSeparator;
    return File(
      '${root.path}${separator}media-assets$separator$_mediaCacheVersion$separator${request.cacheFilename}',
    );
  }

  static Future<void> _waitIfPaused(FileTransferState? transfer) {
    final controller = transfer?.controller;
    if (controller == null) return Future.value();
    return controller.waitIfPaused();
  }

  static Future<void> _deleteQuietly(File file) async {
    try {
      if (await file.exists()) await file.delete();
    } catch (_) {}
  }
}

bool _isHttpUri(Uri uri) => uri.scheme == 'http' || uri.scheme == 'https';

String? _firstNonEmpty(Iterable<String?> values) {
  for (final value in values) {
    final trimmed = value?.trim();
    if (trimmed != null && trimmed.isNotEmpty) return trimmed;
  }
  return null;
}

String? _basenameFromUriPath(String path) {
  if (path.isEmpty) return null;
  final segments = path.split('/').where((segment) => segment.isNotEmpty);
  if (segments.isEmpty) return null;
  return Uri.decodeComponent(segments.last);
}

String _extensionFor({String? filename, String? uriPath, String? mimeType}) {
  for (final candidate in [filename, _basenameFromUriPath(uriPath ?? '')]) {
    final ext = _extensionFromName(candidate);
    if (ext != null) return ext;
  }
  return switch ((mimeType ?? '').toLowerCase()) {
    'image/jpeg' => '.jpg',
    'image/png' => '.png',
    'image/gif' => '.gif',
    'image/webp' => '.webp',
    'application/pdf' => '.pdf',
    'text/plain' => '.txt',
    _ => '',
  };
}

String? _extensionFromName(String? value) {
  final name = value?.trim();
  if (name == null || name.isEmpty) return null;
  final slash = _lastSeparatorIndex(name);
  final base = slash >= 0 ? name.substring(slash + 1) : name;
  final dot = base.lastIndexOf('.');
  if (dot <= 0 || dot == base.length - 1) return null;
  final ext = base.substring(dot).toLowerCase();
  if (ext.length > 12) return null;
  for (final unit in ext.codeUnits) {
    final isDot = unit == 46;
    final isDigit = unit >= 48 && unit <= 57;
    final isLower = unit >= 97 && unit <= 122;
    if (!isDot && !isDigit && !isLower) return null;
  }
  return ext;
}

String _safeStem(String value, {required String extension}) {
  var base = value.replaceAll('\\', '/');
  final slash = base.lastIndexOf('/');
  if (slash >= 0) base = base.substring(slash + 1);
  base = base.trim();
  if (extension.isNotEmpty && base.toLowerCase().endsWith(extension)) {
    base = base.substring(0, base.length - extension.length);
  }
  final buffer = StringBuffer();
  var lastDash = false;
  for (final unit in base.codeUnits) {
    if (_isSafeStemUnit(unit)) {
      buffer.writeCharCode(unit);
      lastDash = false;
    } else if (!lastDash) {
      buffer.writeCharCode(45);
      lastDash = true;
    }
  }
  final raw = buffer.toString();
  var start = 0;
  var end = raw.length;
  while (start < end && _isTrimmedStemUnit(raw.codeUnitAt(start))) {
    start++;
  }
  while (end > start && _isTrimmedStemUnit(raw.codeUnitAt(end - 1))) {
    end--;
  }
  final stem = start == end ? 'asset' : raw.substring(start, end);
  return stem.length <= 64 ? stem : stem.substring(0, 64);
}

int _lastSeparatorIndex(String value) {
  final slash = value.lastIndexOf('/');
  final backslash = value.lastIndexOf('\\');
  return slash > backslash ? slash : backslash;
}

bool _isSafeStemUnit(int unit) {
  return unit == 45 ||
      unit == 46 ||
      unit == 95 ||
      unit >= 48 && unit <= 57 ||
      unit >= 65 && unit <= 90 ||
      unit >= 97 && unit <= 122;
}

bool _isTrimmedStemUnit(int unit) => unit == 45 || unit == 46;

String _fnv1a64Hex(String value) {
  // Signed, so keys match the files already cached on disk (some start
  // with '-').
  return fnv1a64Signed(value).toRadixString(16).padLeft(16, '0');
}
