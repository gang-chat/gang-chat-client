import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/voice_message_display.dart';
import 'package:client/src/protocol/models.dart';

void main() {
  test('start moves from idle to recording with a zeroed timer', () {
    final state = voiceRecordingStarted();
    expect(state.phase, VoiceRecorderPhase.recording);
    expect(state.isRecording, isTrue);
    expect(state.elapsed, Duration.zero);
    expect(state.recordingPath, isNull);
  });

  test('ticks advance the timer only while recording', () {
    final recording = voiceRecordingStarted();
    final ticked = voiceRecordingTicked(recording, const Duration(seconds: 3));
    expect(ticked.elapsed, const Duration(seconds: 3));

    // A stray tick after reset must not resurrect the timer.
    final idle = voiceRecorderReset();
    final ignored = voiceRecordingTicked(idle, const Duration(seconds: 9));
    expect(ignored.elapsed, Duration.zero);
    expect(ignored.phase, VoiceRecorderPhase.idle);
  });

  test('stop moves a long-enough clip into review', () {
    final state = voiceRecordingStopped(
      state: voiceRecordingStarted(),
      path: '/tmp/voice_1.m4a',
      elapsed: const Duration(seconds: 4),
    );
    expect(state.phase, VoiceRecorderPhase.review);
    expect(state.isReviewing, isTrue);
    expect(state.recordingPath, '/tmp/voice_1.m4a');
    expect(state.elapsed, const Duration(seconds: 4));
    expect(state.canSend, isTrue);
  });

  test('stop discards a too-short or pathless clip back to idle', () {
    final tooShort = voiceRecordingStopped(
      state: voiceRecordingStarted(),
      path: '/tmp/voice_1.m4a',
      elapsed: const Duration(milliseconds: 200),
    );
    expect(tooShort.phase, VoiceRecorderPhase.idle);
    expect(tooShort.recordingPath, isNull);

    final noPath = voiceRecordingStopped(
      state: voiceRecordingStarted(),
      path: null,
      elapsed: const Duration(seconds: 5),
    );
    expect(noPath.phase, VoiceRecorderPhase.idle);
  });

  test('cancel always returns to a clean idle state', () {
    expect(voiceRecordingCancelled().phase, VoiceRecorderPhase.idle);
    expect(voiceRecordingCancelled().recordingPath, isNull);
    expect(voiceRecordingCancelled().error, isNull);
  });

  test('send transitions review -> sending -> idle on success', () {
    final review = voiceRecordingStopped(
      state: voiceRecordingStarted(),
      path: '/tmp/voice_1.m4a',
      elapsed: const Duration(seconds: 4),
    );
    final sending = voiceSendStarted(review);
    expect(sending.phase, VoiceRecorderPhase.sending);
    expect(sending.isSending, isTrue);
    expect(sending.error, isNull);

    expect(voiceSendSucceeded().phase, VoiceRecorderPhase.idle);
  });

  test('send failure returns to review and keeps the clip for retry', () {
    final review = voiceRecordingStopped(
      state: voiceRecordingStarted(),
      path: '/tmp/voice_1.m4a',
      elapsed: const Duration(seconds: 4),
    );
    final failed = voiceSendFailed(
      state: voiceSendStarted(review),
      failure: Exception('network down'),
    );
    expect(failed.phase, VoiceRecorderPhase.review);
    expect(failed.recordingPath, '/tmp/voice_1.m4a');
    expect(failed.error, '网络连接失败，请检查网络后重试');
    expect(failed.canSend, isTrue);
  });

  test('reachedLimit fires at the max duration', () {
    expect(
      voiceRecordingReachedLimit(const Duration(minutes: 4, seconds: 59)),
      isFalse,
    );
    expect(voiceRecordingReachedLimit(kVoiceRecordingMaxDuration), isTrue);
  });

  test('duration formats with minute and second marks', () {
    expect(formatVoiceDuration(Duration.zero), '0"');
    expect(formatVoiceDuration(const Duration(seconds: 4)), '4"');
    expect(formatVoiceDuration(const Duration(seconds: 65)), '1\'05"');
    expect(
      formatVoiceDuration(const Duration(minutes: 12, seconds: 9)),
      '12\'09"',
    );
    expect(formatVoiceDuration(const Duration(seconds: -3)), '0"');
  });

  test('voice filename carries an audio extension and timestamp', () {
    final name = voiceMessageFilename(DateTime.utc(2026, 6, 1, 12, 0, 0));
    expect(name, startsWith('voice_'));
    expect(name, endsWith('.m4a'));
    expect(kVoiceMessageMimeType, 'audio/mp4');
  });

  test('browser WAV clips keep a .wav name and audio/wav type', () {
    final name = voiceMessageFilename(
      DateTime.utc(2026, 6, 1, 12, 0, 0),
      extension: kVoiceMessageWavExtension,
    );
    expect(name, endsWith('.wav'));
    expect(kVoiceMessageWavMimeType, 'audio/wav');
  });

  test('bubble duration uses marked seconds and minutes', () {
    expect(formatVoiceBubbleDuration(null), '');
    expect(formatVoiceBubbleDuration(Duration.zero), '0"');
    expect(formatVoiceBubbleDuration(const Duration(milliseconds: 1200)), '2"');
    expect(formatVoiceBubbleDuration(const Duration(seconds: 15)), '15"');
    expect(formatVoiceBubbleDuration(const Duration(seconds: 65)), '1\'05"');
  });

  test('waveform width grows with duration up to a cap', () {
    expect(kVoiceWaveformMinWidth, 96);
    expect(voiceWaveformWidth(null), kVoiceWaveformMinWidth);
    expect(voiceWaveformWidth(Duration.zero), kVoiceWaveformMinWidth);
    expect(
      voiceWaveformWidth(const Duration(seconds: 5)),
      greaterThan(kVoiceWaveformMinWidth),
    );
    expect(
      voiceWaveformWidth(const Duration(seconds: 20)),
      greaterThan(voiceWaveformWidth(const Duration(seconds: 5))),
    );
    expect(
      voiceWaveformWidth(const Duration(minutes: 5)),
      kVoiceWaveformMaxWidth,
    );
  });

  test('playback progress is clamped to the current duration', () {
    expect(
      voicePlaybackProgress(
        position: const Duration(seconds: 5),
        duration: null,
      ),
      0,
    );
    expect(
      voicePlaybackProgress(
        position: const Duration(seconds: -1),
        duration: const Duration(seconds: 10),
      ),
      0,
    );
    expect(
      voicePlaybackProgress(
        position: const Duration(seconds: 5),
        duration: const Duration(seconds: 10),
      ),
      0.5,
    );
    expect(
      voicePlaybackProgress(
        position: const Duration(seconds: 15),
        duration: const Duration(seconds: 10),
      ),
      1,
    );
  });

  test(
    'voice attachment detection handles audio messages and legacy files',
    () {
      final audio = _message(
        type: 'audio',
        attachments: const [
          MessageAttachment(
            type: 'audio',
            name: 'voice_1.m4a',
            durationMs: 15000,
            asset: _voiceAsset,
          ),
        ],
      );
      expect(voiceMessageAttachment(audio)?.durationMs, 15000);

      final legacyFile = _message(
        type: 'file',
        attachments: const [
          MessageAttachment(
            type: 'file',
            name: 'voice_2.m4a',
            asset: _voiceAsset,
          ),
        ],
      );
      expect(voiceMessageAttachment(legacyFile)?.name, 'voice_2.m4a');

      final normalFile = _message(
        type: 'file',
        attachments: const [
          MessageAttachment(type: 'file', name: 'song.m4a', asset: _voiceAsset),
        ],
      );
      expect(voiceMessageAttachment(normalFile), isNull);
    },
  );
}

const _sender = UserSummary(
  id: 'user_1',
  username: 'alice',
  displayName: 'Alice',
  avatarUrl: null,
  defaultAvatarKey: 'blue-3',
);

const _voiceAsset = UploadedAsset(
  id: 'asset_voice',
  url: '/uploads/voice_1.m4a',
  thumbnailUrl: null,
  mimeType: 'audio/mp4',
);

Message _message({
  required String type,
  required List<MessageAttachment> attachments,
}) {
  return Message(
    id: 'message_1',
    roomId: 'room_1',
    sender: _sender,
    clientMessageId: 'client_1',
    type: type,
    body: 'voice_1.m4a',
    createdAt: DateTime.utc(2026, 6, 11),
    attachments: attachments,
  );
}
