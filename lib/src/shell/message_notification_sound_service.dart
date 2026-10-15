import 'dart:io' show File;
import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

import 'synthesized_wav.dart';
import '../config/host_platform.dart';

abstract interface class MessageNotificationSoundPlayer {
  Future<void> play({required double volume});

  Future<void> dispose();
}

/// Plays the short, locally synthesized cue for a realtime text-channel
/// message. No network request or packaged binary asset is required.
class MessageNotificationSoundService
    implements MessageNotificationSoundPlayer {
  MessageNotificationSoundService({AudioPlayer? player})
    : _player = player ?? AudioPlayer();

  final AudioPlayer _player;
  bool _disposed = false;
  Future<String>? _windowsFilePath;

  static final Uint8List _tone = buildMessageNotificationTone();

  @override
  Future<void> play({required double volume}) async {
    if (_disposed) return;
    final normalizedVolume = volume.clamp(0.0, 1.0).toDouble();
    if (normalizedVolume <= 0) return;
    await _player.stop();
    await _player.play(
      await _sourceFor(_tone),
      volume: normalizedVolume,
      mode: PlayerMode.lowLatency,
    );
  }

  Future<Source> _sourceFor(Uint8List bytes) async {
    if (kIsWeb || !HostPlatform.isWindows) {
      return BytesSource(bytes, mimeType: 'audio/wav');
    }
    return DeviceFileSource(
      await (_windowsFilePath ??= _writeWindowsToneFile(bytes)),
      mimeType: 'audio/wav',
    );
  }

  Future<String> _writeWindowsToneFile(Uint8List bytes) async {
    final directory = await getTemporaryDirectory();
    final file = File('${directory.path}\\gang_chat_message_notification.wav');
    if (!await file.exists() || await file.length() != bytes.length) {
      await file.writeAsBytes(bytes, flush: true);
    }
    return file.path;
  }

  @override
  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    await _player.dispose().catchError((_) {});
  }
}

List<double> messageNotificationSoundFrequencies() {
  return const <double>[659.25, 880.0];
}

Uint8List buildMessageNotificationTone({int sampleRate = 44100}) {
  final frequencies = messageNotificationSoundFrequencies();
  return buildSynthesizedToneWav(
    sampleRate: sampleRate,
    tailDuration: 0.022,
    amplitude: 0.2,
    notes: [
      SynthesizedToneNote(
        frequency: frequencies[0],
        duration: 0.075,
        gapAfter: 0.018,
      ),
      SynthesizedToneNote(frequency: frequencies[1], duration: 0.115),
    ],
  );
}
