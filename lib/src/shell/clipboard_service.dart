import 'dart:io' show Platform;

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/services.dart';

import 'android_system_service.dart';

class ClipboardImageFile {
  const ClipboardImageFile({
    required this.bytes,
    this.filename = 'clipboard-image.png',
    this.mimeType = 'image/png',
  });

  final Uint8List bytes;
  final String filename;
  final String mimeType;
}

class ClipboardService {
  const ClipboardService({
    this.androidSystemService = const AndroidSystemService(),
  });

  final AndroidSystemService androidSystemService;

  static const _clipboardFilesChannel = MethodChannel('gang_chat/clipboard');

  Future<List<String>> readFilePaths() async {
    if (kIsWeb || !(Platform.isWindows || Platform.isMacOS)) {
      return const <String>[];
    }
    return await _clipboardFilesChannel.invokeListMethod<String>(
          'readFilePaths',
        ) ??
        const <String>[];
  }

  Future<ClipboardImageFile?> readImageFile() async {
    if (androidSystemService.isSupported) {
      final image = await androidSystemService.readClipboardImage();
      return image == null
          ? null
          : ClipboardImageFile(
              bytes: image.bytes,
              filename: image.filename,
              mimeType: image.mimeType,
            );
    }
    if (kIsWeb || !(Platform.isWindows || Platform.isMacOS)) return null;
    final result = await _clipboardFilesChannel
        .invokeMapMethod<Object?, Object?>('readImageFile');
    if (result == null) return null;
    final bytes = result['bytes'];
    if (bytes is! Uint8List || bytes.isEmpty) return null;
    final filename = result['filename'];
    final mimeType = result['mime_type'];
    return ClipboardImageFile(
      bytes: bytes,
      filename: filename is String && filename.isNotEmpty
          ? filename
          : 'clipboard-image.png',
      mimeType: mimeType is String && mimeType.isNotEmpty
          ? mimeType
          : 'image/png',
    );
  }

  Future<String?> readText() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text;
    return text == null || text.isEmpty ? null : text;
  }

  Future<void> writeText(String text) {
    return Clipboard.setData(ClipboardData(text: text));
  }

  /// Writes file paths to the system clipboard so they can be pasted as files
  /// in supported desktop apps. Returns false on unsupported platforms.
  Future<bool> writeFilePaths(List<String> paths) async {
    if (kIsWeb || !(Platform.isWindows || Platform.isMacOS)) return false;
    final normalized = paths
        .map((path) => path.trim())
        .where((path) => path.isNotEmpty)
        .toList(growable: false);
    if (normalized.isEmpty) return false;
    final result = await _clipboardFilesChannel.invokeMethod<bool>(
      'writeFilePaths',
      {'paths': normalized},
    );
    return result ?? false;
  }

  /// Writes raw image [bytes] (with the given [mimeType]) to the system
  /// clipboard via the native runner so it can be pasted into other apps.
  /// No-op on platforms without a native handler. Returns true on success.
  Future<bool> writeImage(
    Uint8List bytes, {
    String mimeType = 'image/png',
  }) async {
    if (androidSystemService.isSupported) {
      return androidSystemService.writeClipboardImage(
        bytes: bytes,
        mimeType: mimeType,
      );
    }
    if (kIsWeb || !(Platform.isWindows || Platform.isMacOS)) return false;
    if (bytes.isEmpty) return false;
    final result = await _clipboardFilesChannel.invokeMethod<bool>(
      'writeImageFile',
      {'bytes': bytes, 'mime_type': mimeType},
    );
    return result ?? false;
  }
}
