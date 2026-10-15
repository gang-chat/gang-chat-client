import 'dart:async';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/services.dart';
import '../config/host_platform.dart';

class FileDropEvent {
  const FileDropEvent({required this.paths, required this.x, required this.y});

  final List<String> paths;
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
    if (_installed ||
        kIsWeb ||
        !(HostPlatform.isWindows || HostPlatform.isMacOS)) {
      return;
    }
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
