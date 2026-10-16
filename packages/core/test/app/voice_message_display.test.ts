import { describe, expect, test } from 'vitest';

import {
  VoiceRecorderPhase,
  formatVoiceBubbleDuration,
  formatVoiceDuration,
  kVoiceMessageMimeType,
  kVoiceMessageWavExtension,
  kVoiceMessageWavMimeType,
  kVoiceRecordingMaxDuration,
  kVoiceWaveformMaxWidth,
  kVoiceWaveformMinWidth,
  voiceMessageAttachment,
  voiceMessageFilename,
  voicePlaybackProgress,
  voiceRecorderReset,
  voiceRecordingCancelled,
  voiceRecordingReachedLimit,
  voiceRecordingStarted,
  voiceRecordingStopped,
  voiceRecordingTicked,
  voiceSendFailed,
  voiceSendStarted,
  voiceSendSucceeded,
  voiceWaveformWidth,
} from '../../src/app/voice_message_display';
import { Message, MessageAttachment, UploadedAsset, UserSummary } from '../../src/protocol/models';

const second = 1000;

const _sender = new UserSummary({
  id: 'user_1',
  username: 'alice',
  displayName: 'Alice',
  avatarUrl: null,
  defaultAvatarKey: 'blue-3',
});

const _voiceAsset = new UploadedAsset({
  id: 'asset_voice',
  url: '/uploads/voice_1.m4a',
  thumbnailUrl: null,
  mimeType: 'audio/mp4',
});

function _message(type: string, attachments: MessageAttachment[]): Message {
  return new Message({
    id: 'message_1',
    roomId: 'room_1',
    sender: _sender,
    clientMessageId: 'client_1',
    type,
    body: 'voice_1.m4a',
    createdAt: new Date(Date.UTC(2026, 5, 11)),
    attachments,
  });
}

function _review() {
  return voiceRecordingStopped({ state: voiceRecordingStarted(), path: '/tmp/voice_1.m4a', elapsed: 4 * second });
}

describe('voice_message_display', () => {
  test('start moves from idle to recording with a zeroed timer', () => {
    const state = voiceRecordingStarted();
    expect(state.phase).toBe(VoiceRecorderPhase.recording);
    expect(state.isRecording).toBe(true);
    expect(state.elapsed).toBe(0);
    expect(state.recordingPath).toBeNull();
  });

  test('ticks advance the timer only while recording', () => {
    const recording = voiceRecordingStarted();
    const ticked = voiceRecordingTicked(recording, 3 * second);
    expect(ticked.elapsed).toBe(3 * second);

    // A stray tick after reset must not resurrect the timer.
    const idle = voiceRecorderReset();
    const ignored = voiceRecordingTicked(idle, 9 * second);
    expect(ignored.elapsed).toBe(0);
    expect(ignored.phase).toBe(VoiceRecorderPhase.idle);
  });

  test('stop moves a long-enough clip into review', () => {
    const state = _review();
    expect(state.phase).toBe(VoiceRecorderPhase.review);
    expect(state.isReviewing).toBe(true);
    expect(state.recordingPath).toBe('/tmp/voice_1.m4a');
    expect(state.elapsed).toBe(4 * second);
    expect(state.canSend).toBe(true);
  });

  test('stop discards a too-short or pathless clip back to idle', () => {
    const tooShort = voiceRecordingStopped({ state: voiceRecordingStarted(), path: '/tmp/voice_1.m4a', elapsed: 200 });
    expect(tooShort.phase).toBe(VoiceRecorderPhase.idle);
    expect(tooShort.recordingPath).toBeNull();

    const noPath = voiceRecordingStopped({ state: voiceRecordingStarted(), path: null, elapsed: 5 * second });
    expect(noPath.phase).toBe(VoiceRecorderPhase.idle);
  });

  test('cancel always returns to a clean idle state', () => {
    expect(voiceRecordingCancelled().phase).toBe(VoiceRecorderPhase.idle);
    expect(voiceRecordingCancelled().recordingPath).toBeNull();
    expect(voiceRecordingCancelled().error).toBeNull();
  });

  test('send transitions review -> sending -> idle on success', () => {
    const sending = voiceSendStarted(_review());
    expect(sending.phase).toBe(VoiceRecorderPhase.sending);
    expect(sending.isSending).toBe(true);
    expect(sending.error).toBeNull();

    expect(voiceSendSucceeded().phase).toBe(VoiceRecorderPhase.idle);
  });

  test('send failure returns to review and keeps the clip for retry', () => {
    const failed = voiceSendFailed({ state: voiceSendStarted(_review()), failure: new Error('network down') });
    expect(failed.phase).toBe(VoiceRecorderPhase.review);
    expect(failed.recordingPath).toBe('/tmp/voice_1.m4a');
    expect(failed.error).toBe('网络连接失败，请检查网络后重试');
    expect(failed.canSend).toBe(true);
  });

  test('copyWith distinguishes omitted and cleared fields', () => {
    const failed = voiceSendFailed({ state: _review(), failure: 'boom' });
    expect(failed.copyWith({ elapsed: 1 }).error).toBe(failed.error);
    expect(failed.copyWith({ error: null }).error).toBeNull();
    expect(failed.copyWith({ recordingPath: null }).recordingPath).toBeNull();
  });

  test('reachedLimit fires at the max duration', () => {
    expect(voiceRecordingReachedLimit(4 * 60 * second + 59 * second)).toBe(false);
    expect(voiceRecordingReachedLimit(kVoiceRecordingMaxDuration)).toBe(true);
  });

  test('duration formats with minute and second marks', () => {
    expect(formatVoiceDuration(0)).toBe('0"');
    expect(formatVoiceDuration(4 * second)).toBe('4"');
    expect(formatVoiceDuration(65 * second)).toBe('1\'05"');
    expect(formatVoiceDuration(12 * 60 * second + 9 * second)).toBe('12\'09"');
    expect(formatVoiceDuration(-3 * second)).toBe('0"');
  });

  test('voice filename carries an audio extension and timestamp', () => {
    const name = voiceMessageFilename(new Date(Date.UTC(2026, 5, 1, 12, 0, 0)));
    expect(name.startsWith('voice_')).toBe(true);
    expect(name.endsWith('.m4a')).toBe(true);
    expect(name).toBe(`voice_${Date.UTC(2026, 5, 1, 12)}.m4a`);
    expect(kVoiceMessageMimeType).toBe('audio/mp4');
  });

  test('browser WAV clips keep a .wav name and audio/wav type', () => {
    const name = voiceMessageFilename(new Date(Date.UTC(2026, 5, 1, 12, 0, 0)), { extension: kVoiceMessageWavExtension });
    expect(name.endsWith('.wav')).toBe(true);
    expect(kVoiceMessageWavMimeType).toBe('audio/wav');
  });

  test('bubble duration uses marked seconds and minutes', () => {
    expect(formatVoiceBubbleDuration(null)).toBe('');
    expect(formatVoiceBubbleDuration(0)).toBe('0"');
    expect(formatVoiceBubbleDuration(1200)).toBe('2"');
    expect(formatVoiceBubbleDuration(15 * second)).toBe('15"');
    expect(formatVoiceBubbleDuration(65 * second)).toBe('1\'05"');
  });

  test('waveform width grows with duration up to a cap', () => {
    expect(kVoiceWaveformMinWidth).toBe(96);
    expect(voiceWaveformWidth(null)).toBe(kVoiceWaveformMinWidth);
    expect(voiceWaveformWidth(0)).toBe(kVoiceWaveformMinWidth);
    expect(voiceWaveformWidth(5 * second)).toBeGreaterThan(kVoiceWaveformMinWidth);
    expect(voiceWaveformWidth(20 * second)).toBeGreaterThan(voiceWaveformWidth(5 * second));
    expect(voiceWaveformWidth(5 * 60 * second)).toBe(kVoiceWaveformMaxWidth);
  });

  test('playback progress is clamped to the current duration', () => {
    expect(voicePlaybackProgress({ position: 5 * second, duration: null })).toBe(0);
    expect(voicePlaybackProgress({ position: -1 * second, duration: 10 * second })).toBe(0);
    expect(voicePlaybackProgress({ position: 5 * second, duration: 10 * second })).toBe(0.5);
    expect(voicePlaybackProgress({ position: 15 * second, duration: 10 * second })).toBe(1);
  });

  test('voice attachment detection handles audio messages and legacy files', () => {
    const audio = _message('audio', [
      new MessageAttachment({ type: 'audio', name: 'voice_1.m4a', durationMs: 15000, asset: _voiceAsset }),
    ]);
    expect(voiceMessageAttachment(audio)?.durationMs).toBe(15000);

    const legacyFile = _message('file', [new MessageAttachment({ type: 'file', name: 'voice_2.m4a', asset: _voiceAsset })]);
    expect(voiceMessageAttachment(legacyFile)?.name).toBe('voice_2.m4a');

    const normalFile = _message('file', [new MessageAttachment({ type: 'file', name: 'song.m4a', asset: _voiceAsset })]);
    expect(voiceMessageAttachment(normalFile)).toBeNull();
  });
});
