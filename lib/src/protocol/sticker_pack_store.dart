import 'dart:convert';
import 'dart:io';

import 'package:path_provider/path_provider.dart';

import 'models.dart';
import '../config/host_platform.dart';

class StickerPackStore {
  const StickerPackStore();

  static const _version = 1;
  static const _filePrefix = 'gang-sticker-packs-v1';

  static final Map<String, List<StickerPack>> _memoryCache = {};

  Future<List<StickerPack>?> readPersonalPacks({
    required String userId,
    required String apiBaseUrl,
  }) async {
    if (userId.isEmpty || apiBaseUrl.isEmpty) return null;
    final account = _accountKey(userId, apiBaseUrl);
    final memory = _memoryCache[account];
    if (memory != null) return memory;
    try {
      final raw = await _cacheFile(account).then((file) => file.readAsString());
      if (raw.isEmpty) return null;
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, Object?>) return null;
      if (decoded['version'] != _version) return null;
      final packs = decoded['packs'];
      if (packs is! List<Object?>) return null;
      final parsed = packs
          .cast<Map<String, Object?>>()
          .map(StickerPack.fromJson)
          .toList();
      _memoryCache[account] = parsed;
      return parsed;
    } catch (_) {
      return null;
    }
  }

  Future<void> writePersonalPacks({
    required String userId,
    required String apiBaseUrl,
    required List<StickerPack> packs,
  }) async {
    if (userId.isEmpty || apiBaseUrl.isEmpty) return;
    final account = _accountKey(userId, apiBaseUrl);
    _memoryCache[account] = packs;
    try {
      final file = await _cacheFile(account);
      await file.parent.create(recursive: true);
      final tmp = File('${file.path}.tmp');
      await tmp.writeAsString(
        jsonEncode({
          'version': _version,
          'saved_at': DateTime.now().toUtc().toIso8601String(),
          'packs': packs.map((pack) => pack.toJson()).toList(),
        }),
        flush: true,
      );
      if (await file.exists()) await file.delete();
      await tmp.rename(file.path);
    } catch (_) {
      // The cache is a convenience path. Network data should remain usable even
      // when the platform cache directory is unavailable.
    }
  }

  Future<void> clearPersonalPacks({
    required String userId,
    required String apiBaseUrl,
  }) async {
    if (userId.isEmpty || apiBaseUrl.isEmpty) return;
    final account = _accountKey(userId, apiBaseUrl);
    _memoryCache.remove(account);
    try {
      final file = await _cacheFile(account);
      if (await file.exists()) await file.delete();
    } catch (_) {}
  }

  String _accountKey(String userId, String apiBaseUrl) {
    return base64Url
        .encode(utf8.encode('$apiBaseUrl\n$userId'))
        .replaceAll('=', '');
  }

  Future<File> _cacheFile(String account) async {
    final dir = await getApplicationCacheDirectory();
    final separator = HostPlatform.pathSeparator;
    return File(
      '${dir.path}${separator}sticker-packs$separator$_filePrefix-$account.json',
    );
  }
}
