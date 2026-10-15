import 'dart:io' show File;

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

import '../shell/synthesized_wav.dart';
import '../config/host_platform.dart';

enum LivePresenceSound { joined, left }

abstract interface class LivePresenceSoundPlayer {
  Future<void> play(LivePresenceSound sound, {required double volume});

  Future<void> dispose();
}

/// Plays short, locally generated voice-channel presence cues.
///
/// The tones are synthesized into WAV data so the cue is available on every
/// supported platform without another network request or a binary asset.
class LivePresenceSoundService implements LivePresenceSoundPlayer {
  LivePresenceSoundService({AudioPlayer? joinedPlayer, AudioPlayer? leftPlayer})
    : _joinedPlayer = joinedPlayer ?? AudioPlayer(),
      _leftPlayer = leftPlayer ?? AudioPlayer();

  final AudioPlayer _joinedPlayer;
  final AudioPlayer _leftPlayer;
  bool _disposed = false;
  Future<String>? _joinedFilePath;
  Future<String>? _leftFilePath;

  static final Uint8List _joinedTone = buildLivePresenceTone(
    LivePresenceSound.joined,
  );
  static final Uint8List _leftTone = buildLivePresenceTone(
    LivePresenceSound.left,
  );

  @override
  Future<void> play(LivePresenceSound sound, {required double volume}) async {
    if (_disposed) return;
    final normalizedVolume = volume.clamp(0.0, 1.0).toDouble();
    if (normalizedVolume <= 0) return;
    final player = sound == LivePresenceSound.joined
        ? _joinedPlayer
        : _leftPlayer;
    final bytes = sound == LivePresenceSound.joined ? _joinedTone : _leftTone;
    await player.stop();
    await player.play(
      await _sourceFor(sound, bytes),
      volume: normalizedVolume,
      mode: PlayerMode.lowLatency,
    );
  }

  Future<Source> _sourceFor(LivePresenceSound sound, Uint8List bytes) async {
    if (kIsWeb || !HostPlatform.isWindows) {
      return BytesSource(bytes, mimeType: 'audio/wav');
    }
    final pathFuture = sound == LivePresenceSound.joined
        ? (_joinedFilePath ??= _writeWindowsToneFile(sound, bytes))
        : (_leftFilePath ??= _writeWindowsToneFile(sound, bytes));
    return DeviceFileSource(await pathFuture, mimeType: 'audio/wav');
  }

  Future<String> _writeWindowsToneFile(
    LivePresenceSound sound,
    Uint8List bytes,
  ) async {
    final directory = await getTemporaryDirectory();
    final file = File(
      '${directory.path}\\gang_chat_live_${sound.name}_presence.wav',
    );
    if (!await file.exists() || await file.length() != bytes.length) {
      await file.writeAsBytes(bytes, flush: true);
    }
    return file.path;
  }

  @override
  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    await Future.wait<void>([
      _joinedPlayer.dispose().catchError((_) {}),
      _leftPlayer.dispose().catchError((_) {}),
    ]);
  }
}

List<double> livePresenceSoundNoteFrequencies(LivePresenceSound sound) {
  const rising = <double>[523.25, 783.99];
  return sound == LivePresenceSound.joined
      ? rising
      : rising.reversed.toList(growable: false);
}

Uint8List buildLivePresenceTone(
  LivePresenceSound sound, {
  int sampleRate = 44100,
}) {
  final notes = livePresenceSoundNoteFrequencies(sound);
  return buildSynthesizedToneWav(
    sampleRate: sampleRate,
    tailDuration: 0.025,
    notes: [
      SynthesizedToneNote(
        frequency: notes[0],
        duration: 0.105,
        gapAfter: 0.022,
      ),
      SynthesizedToneNote(frequency: notes[1], duration: 0.155),
    ],
  );
}
