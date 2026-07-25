import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';

import '../app/live_presence_announcement.dart';
import 'android_system_service.dart';

/// Free local TTS backed by the operating system's installed voices.
///
/// It intentionally lives behind an app-layer protocol so room/live business
/// logic never depends on process APIs or a particular speech engine.
class SystemLivePresenceSpeechPlayer implements LivePresenceSpeechPlayer {
  SystemLivePresenceSpeechPlayer({
    this.pause = const Duration(milliseconds: 280),
    this.androidSystemService = const AndroidSystemService(),
  });

  final Duration pause;
  final AndroidSystemService androidSystemService;
  bool _disposed = false;

  @override
  Future<void> speak(
    LivePresenceAnnouncement announcement, {
    required double volume,
  }) async {
    if (_disposed || kIsWeb || volume <= 0) return;
    final segments = announcement.segments
        .map((segment) => segment.trim())
        .where((segment) => segment.isNotEmpty)
        .toList(growable: false);
    if (segments.isEmpty) return;

    try {
      if (Platform.isWindows) {
        await _speakOnWindows(segments, volume);
      } else if (Platform.isMacOS) {
        await _speakOnMacOS(segments);
      } else if (Platform.isLinux) {
        await _speakOnLinux(segments);
      } else if (Platform.isAndroid) {
        await androidSystemService.speak(
          segments: segments,
          volume: volume,
          pause: pause,
        );
      }
    } catch (_) {
      // Presence speech is best-effort and must never interrupt live controls.
    }
  }

  Future<void> _speakOnWindows(List<String> segments, double volume) async {
    const script = r'''
$voice = New-Object -ComObject SAPI.SpVoice
$voice.Volume = [Math]::Round([double]$env:GANG_CHAT_TTS_VOLUME * 100)
$chineseVoice = @($voice.GetVoices()) | Where-Object {
  $language = $_.GetAttribute('Language')
  $language -match '(^|;)0?804($|;)' -or $language -match '(^|;)0?404($|;)'
} | Select-Object -First 1
if ($null -ne $chineseVoice) { $voice.Voice = $chineseVoice }
$segments = @(
  $env:GANG_CHAT_TTS_SEGMENT_0,
  $env:GANG_CHAT_TTS_SEGMENT_1,
  $env:GANG_CHAT_TTS_SEGMENT_2
)
for ($index = 0; $index -lt $segments.Count; $index++) {
  if (-not [string]::IsNullOrWhiteSpace($segments[$index])) {
    [void]$voice.Speak($segments[$index])
  }
  if ($index -lt $segments.Count - 1) {
    Start-Sleep -Milliseconds ([int]$env:GANG_CHAT_TTS_PAUSE_MS)
  }
}
''';
    final result = await Process.run(
      'powershell.exe',
      [
        '-NoLogo',
        '-NoProfile',
        '-NonInteractive',
        '-ExecutionPolicy',
        'Bypass',
        '-EncodedCommand',
        _encodePowerShellCommand(script),
      ],
      environment: {
        'GANG_CHAT_TTS_VOLUME': volume.clamp(0.0, 1.0).toString(),
        'GANG_CHAT_TTS_PAUSE_MS': pause.inMilliseconds.toString(),
        for (var index = 0; index < segments.length && index < 3; index += 1)
          'GANG_CHAT_TTS_SEGMENT_$index': segments[index],
      },
      stdoutEncoding: utf8,
      stderrEncoding: utf8,
    );
    if (result.exitCode != 0) {
      throw ProcessException(
        'powershell.exe',
        const [],
        result.stderr.toString(),
      );
    }
  }

  Future<void> _speakOnMacOS(List<String> segments) async {
    for (var index = 0; index < segments.length; index += 1) {
      var result = await Process.run('/usr/bin/say', [
        '-v',
        'Ting-Ting',
        segments[index],
      ]);
      if (result.exitCode != 0) {
        result = await Process.run('/usr/bin/say', [segments[index]]);
      }
      if (result.exitCode != 0) return;
      if (index < segments.length - 1) await Future<void>.delayed(pause);
    }
  }

  Future<void> _speakOnLinux(List<String> segments) async {
    for (var index = 0; index < segments.length; index += 1) {
      final result = await Process.run('spd-say', ['--wait', segments[index]]);
      if (result.exitCode != 0) return;
      if (index < segments.length - 1) await Future<void>.delayed(pause);
    }
  }

  String _encodePowerShellCommand(String command) {
    final bytes = <int>[];
    for (final codeUnit in command.codeUnits) {
      bytes
        ..add(codeUnit & 0xff)
        ..add((codeUnit >> 8) & 0xff);
    }
    return base64Encode(bytes);
  }

  @override
  Future<void> dispose() async {
    _disposed = true;
    if (!kIsWeb && Platform.isAndroid) {
      try {
        await androidSystemService.disposeSpeech();
      } catch (_) {}
    }
  }
}
