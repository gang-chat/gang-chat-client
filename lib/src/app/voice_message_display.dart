import 'dart:math' as math;

import '../protocol/models.dart';
import 'error_display.dart';
import 'file_display.dart' as file_display;

/// Pure logic for the voice-message recorder shown in the chat composer.
///
/// The widget layer drives an [AudioRecorder] and a ticking timer; this module
/// owns the state machine and formatting so the transitions can be unit-tested
/// without a microphone. The flow is click-to-start (not press-and-hold):
///
///   idle ──start──▶ recording ──stop──▶ review ──send──▶ sending ──▶ idle
///     ▲                  │                  │                          │
///     └────── cancel ────┴────── cancel ────┴────────── failed ────────┘
///
/// "review" is the state after recording stops where the user decides whether
/// to send or discard the clip.
enum VoiceRecorderPhase { idle, recording, review, sending }

class VoiceRecorderState {
  const VoiceRecorderState({
    this.phase = VoiceRecorderPhase.idle,
    this.elapsed = Duration.zero,
    this.recordingPath,
    this.error,
  });

  final VoiceRecorderPhase phase;

  /// How long the current (or just-finished) clip runs.
  final Duration elapsed;

  /// Filesystem path of the captured clip, set once recording stops.
  final String? recordingPath;

  final String? error;

  bool get isRecording => phase == VoiceRecorderPhase.recording;
  bool get isReviewing => phase == VoiceRecorderPhase.review;
  bool get isSending => phase == VoiceRecorderPhase.sending;
  bool get isIdle => phase == VoiceRecorderPhase.idle;

  /// A clip is only worth sending once it has a path and some duration.
  bool get canSend =>
      phase == VoiceRecorderPhase.review &&
      recordingPath != null &&
      elapsed > Duration.zero;

  VoiceRecorderState copyWith({
    VoiceRecorderPhase? phase,
    Duration? elapsed,
    Object? recordingPath = _unchanged,
    Object? error = _unchanged,
  }) {
    return VoiceRecorderState(
      phase: phase ?? this.phase,
      elapsed: elapsed ?? this.elapsed,
      recordingPath: identical(recordingPath, _unchanged)
          ? this.recordingPath
          : recordingPath as String?,
      error: identical(error, _unchanged) ? this.error : error as String?,
    );
  }
}

const Object _unchanged = Object();

/// Hard cap so an abandoned recording cannot grow without bound. The widget
/// layer is expected to call [voiceRecordingStopped] when this is reached.
const Duration kVoiceRecordingMaxDuration = Duration(minutes: 5);

/// Below this a clip is treated as an accidental tap and discarded on stop.
const Duration kVoiceRecordingMinDuration = Duration(milliseconds: 800);

VoiceRecorderState voiceRecorderReset() => const VoiceRecorderState();

VoiceRecorderState voiceRecordingStarted() {
  return const VoiceRecorderState(phase: VoiceRecorderPhase.recording);
}

/// Advances the displayed duration while recording. Ignored in other phases so
/// a late timer tick cannot resurrect a cancelled clip.
VoiceRecorderState voiceRecordingTicked(
  VoiceRecorderState state,
  Duration elapsed,
) {
  if (state.phase != VoiceRecorderPhase.recording) return state;
  return state.copyWith(elapsed: elapsed);
}

bool voiceRecordingReachedLimit(Duration elapsed) {
  return elapsed >= kVoiceRecordingMaxDuration;
}

/// Recording finished. A clip shorter than [kVoiceRecordingMinDuration] is
/// discarded back to idle; otherwise we move to review with the captured path.
VoiceRecorderState voiceRecordingStopped({
  required VoiceRecorderState state,
  required String? path,
  required Duration elapsed,
}) {
  if (path == null || elapsed < kVoiceRecordingMinDuration) {
    return voiceRecorderReset();
  }
  return VoiceRecorderState(
    phase: VoiceRecorderPhase.review,
    elapsed: elapsed,
    recordingPath: path,
  );
}

VoiceRecorderState voiceRecordingCancelled() => voiceRecorderReset();

VoiceRecorderState voiceSendStarted(VoiceRecorderState state) {
  return state.copyWith(phase: VoiceRecorderPhase.sending, error: null);
}

VoiceRecorderState voiceSendSucceeded() => voiceRecorderReset();

VoiceRecorderState voiceSendFailed({
  required VoiceRecorderState state,
  required Object failure,
}) {
  // Drop back to review so the user can retry sending the same clip.
  return state.copyWith(
    phase: VoiceRecorderPhase.review,
    error: userFacingErrorMessage(failure, fallback: '发送语音消息失败'),
  );
}

/// Compact voice duration label. Minutes use `'` and seconds use `"`.
String formatVoiceDuration(Duration duration) {
  final clamped = duration.isNegative ? Duration.zero : duration;
  return _formatVoiceDurationLabel(clamped.inSeconds);
}

/// Filename for an outgoing voice clip. Voice messages ride the regular file
/// attachment path, so they need a stable name and an audio mime the server and
/// renderer can recognise.
const String kVoiceMessageMimeType = 'audio/mp4';
const String kVoiceMessageExtension = 'm4a';
const String kVoiceMessageAttachmentType = 'audio';

/// Browsers that can't record AAC send WAV instead, which every client plays.
const String kVoiceMessageWavMimeType = 'audio/wav';
const String kVoiceMessageWavExtension = 'wav';

String voiceMessageFilename(
  DateTime timestamp, {
  String extension = kVoiceMessageExtension,
}) {
  final stamp = timestamp.toUtc().millisecondsSinceEpoch;
  return 'voice_$stamp.$extension';
}

MessageAttachment? voiceMessageAttachment(Message message) {
  final messageIsAudio = message.type == kVoiceMessageAttachmentType;
  for (final attachment in message.attachments) {
    if (_isVoiceAttachment(attachment, messageIsAudio: messageIsAudio)) {
      return attachment;
    }
  }
  return null;
}

bool _isVoiceAttachment(
  MessageAttachment attachment, {
  required bool messageIsAudio,
}) {
  final asset = attachment.asset;
  final typeIsAudio = attachment.type == kVoiceMessageAttachmentType;
  final mimeIsAudio = file_display.isAudioMimeType(asset?.mimeType);
  final title = file_display.fileAttachmentTitle(attachment).toLowerCase();
  final legacyVoiceFile =
      attachment.type == 'file' &&
      mimeIsAudio &&
      file_display.basename(title).startsWith('voice_');
  return (messageIsAudio || typeIsAudio || legacyVoiceFile) &&
      (asset != null || attachment.durationMs != null);
}

Duration? voiceAttachmentDuration(MessageAttachment attachment) {
  final durationMs = attachment.durationMs;
  if (durationMs == null || durationMs <= 0) return null;
  return Duration(milliseconds: durationMs);
}

String formatVoiceBubbleDuration(Duration? duration) {
  if (duration == null) return '';
  final clamped = duration.isNegative ? Duration.zero : duration;
  final totalSeconds = ((clamped.inMilliseconds + 999) ~/ 1000).clamp(0, 9999);
  return _formatVoiceDurationLabel(totalSeconds);
}

String _formatVoiceDurationLabel(int totalSeconds) {
  const secondsMark = '"';
  if (totalSeconds < 60) return '$totalSeconds$secondsMark';
  final minutes = totalSeconds ~/ 60;
  final seconds = totalSeconds % 60;
  return "$minutes'${seconds.toString().padLeft(2, '0')}$secondsMark";
}

const double kVoiceWaveformMinWidth = 96;
const double kVoiceWaveformMaxWidth = 160;

double voiceWaveformWidth(Duration? duration) {
  if (duration == null || duration <= Duration.zero) {
    return kVoiceWaveformMinWidth;
  }
  final seconds = ((duration.inMilliseconds + 999) ~/ 1000).clamp(
    1,
    kVoiceRecordingMaxDuration.inSeconds,
  );
  final ratio =
      math.log(seconds + 1) /
      math.log(kVoiceRecordingMaxDuration.inSeconds + 1);
  return kVoiceWaveformMinWidth +
      (kVoiceWaveformMaxWidth - kVoiceWaveformMinWidth) * ratio;
}

double voicePlaybackProgress({
  required Duration position,
  required Duration? duration,
}) {
  final total = duration;
  if (total == null || total <= Duration.zero) return 0;
  final ratio = position.inMilliseconds / total.inMilliseconds;
  return ratio.clamp(0, 1).toDouble();
}
