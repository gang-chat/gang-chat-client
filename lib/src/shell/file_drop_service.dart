import 'dart:async';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/services.dart';

import 'browser/browser_bridge.dart';
import 'file_selection_service.dart';
import '../config/host_platform.dart';

class FileDropEvent {
  const FileDropEvent({
    this.paths = const [],
    this.files = const [],
    required this.x,
    required this.y,
  });

  /// Desktop drops carry paths.
  final List<String> paths;

  /// Browser drops carry contents instead, since browsers expose no paths.
  final List<SelectedFile> files;
  final double x;
  final double y;
}

class FileDropService {
  const FileDropService();

  static const _channel = MethodChannel('gang_chat/file_drop');
  static final _events = StreamController<FileDropEvent>.broadcast();
  static bool _installed = false;

  Stream<FileDropEvent> get drops {
    _ensureInstalled();
    return _events.stream;
  }

  static void _ensureInstalled() {
    if (_installed) return;
    if (kIsWeb) {
      _installed = true;
      browserFileDrops().listen((drop) {
        _events.add(
          FileDropEvent(
            files: [
              for (final file in drop.files)
                SelectedFile.deferred(
                  name: file.name,
                  mimeType: file.mimeType,
                  length: file.size,
                  readAsBytes: file.read,
                ),
            ],
            x: drop.x,
            y: drop.y,
          ),
        );
      });
      return;
    }
    if (!(HostPlatform.isWindows || HostPlatform.isMacOS)) return;
    _installed = true;
    _channel.setMethodCallHandler((call) async {
      if (call.method != 'dropFiles') return null;
      final arguments = call.arguments;
      if (arguments is! Map) return null;
      final paths = (arguments['paths'] as List?)?.whereType<String>().toList(
        growable: false,
      );
      if (paths == null || paths.isEmpty) return null;
      final x = _asDouble(arguments['x']);
      final y = _asDouble(arguments['y']);
      if (x == null || y == null) return null;
      _events.add(FileDropEvent(paths: paths, x: x, y: y));
      return null;
    });
  }
}

double? _asDouble(Object? value) {
  if (value is int) return value.toDouble();
  if (value is double) return value;
  return null;
}
