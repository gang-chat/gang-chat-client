import type { Message, MessageAttachment } from '../protocol/models';
import { userFacingErrorMessage } from './error_display';
import * as file_display from './file_display';

/**
 * Pure logic for the voice-message recorder shown in the chat composer.
 *
 * The widget layer drives an [AudioRecorder] and a ticking timer; this module
 * owns the state machine and formatting so the transitions can be unit-tested
 * without a microphone. The flow is click-to-start (not press-and-hold):
 *
 *   idle ──start──▶ recording ──stop──▶ review ──send──▶ sending ──▶ idle
 *     ▲                  │                  │                          │
 *     └────── cancel ────┴────── cancel ────┴────────── failed ────────┘
 *
 * "review" is the state after recording stops where the user decides whether
 * to send or discard the clip.
 *
 * All durations are milliseconds.
 */
export enum VoiceRecorderPhase {
  idle = 'idle',
  recording = 'recording',
  review = 'review',
  sending = 'sending',
}

export class VoiceRecorderState {
  constructor(
    opts: {
      phase?: VoiceRecorderPhase;
      elapsed?: number;
      recordingPath?: string | null;
      error?: string | null;
    } = {},
  ) {
    this.phase = opts.phase ?? VoiceRecorderPhase.idle;
    this.elapsed = opts.elapsed ?? 0;
    this.recordingPath = opts.recordingPath ?? null;
    this.error = opts.error ?? null;
  }

  readonly phase: VoiceRecorderPhase;

  /** How long the current (or just-finished) clip runs, in milliseconds. */
  readonly elapsed: number;

  /** Filesystem path of the captured clip, set once recording stops. */
  readonly recordingPath: string | null;

  readonly error: string | null;

  get isRecording(): boolean {
    return this.phase === VoiceRecorderPhase.recording;
  }

  get isReviewing(): boolean {
    return this.phase === VoiceRecorderPhase.review;
  }

  get isSending(): boolean {
    return this.phase === VoiceRecorderPhase.sending;
  }

  get isIdle(): boolean {
    return this.phase === VoiceRecorderPhase.idle;
  }

  /** A clip is only worth sending once it has a path and some duration. */
  get canSend(): boolean {
    return this.phase === VoiceRecorderPhase.review && this.recordingPath != null && this.elapsed > 0;
  }

  /**
   * `undefined` (omitted) keeps a field; an explicit `null` clears
   * [recordingPath] / [error] — the Dart `_unchanged` sentinel.
   */
  copyWith(
    opts: {
      phase?: VoiceRecorderPhase;
      elapsed?: number;
      recordingPath?: string | null;
      error?: string | null;
    } = {},
  ): VoiceRecorderState {
    return new VoiceRecorderState({
      phase: opts.phase ?? this.phase,
      elapsed: opts.elapsed ?? this.elapsed,
      recordingPath: opts.recordingPath === undefined ? this.recordingPath : opts.recordingPath,
      error: opts.error === undefined ? this.error : opts.error,
    });
  }
}

/**
 * Hard cap so an abandoned recording cannot grow without bound. The widget
 * layer is expected to call [voiceRecordingStopped] when this is reached.
 */
export const kVoiceRecordingMaxDuration = 5 * 60 * 1000;

/** Below this a clip is treated as an accidental tap and discarded on stop. */
export const kVoiceRecordingMinDuration = 800;

export function voiceRecorderReset(): VoiceRecorderState {
  return new VoiceRecorderState();
}

export function voiceRecordingStarted(): VoiceRecorderState {
  return new VoiceRecorderState({ phase: VoiceRecorderPhase.recording });
}

/**
 * Advances the displayed duration while recording. Ignored in other phases so
 * a late timer tick cannot resurrect a cancelled clip.
 */
export function voiceRecordingTicked(state: VoiceRecorderState, elapsed: number): VoiceRecorderState {
  if (state.phase !== VoiceRecorderPhase.recording) return state;
  return state.copyWith({ elapsed });
}

export function voiceRecordingReachedLimit(elapsed: number): boolean {
  return elapsed >= kVoiceRecordingMaxDuration;
}

/**
 * Recording finished. A clip shorter than [kVoiceRecordingMinDuration] is
 * discarded back to idle; otherwise we move to review with the captured path.
 */
export function voiceRecordingStopped(opts: {
  state: VoiceRecorderState;
  path: string | null;
  elapsed: number;
}): VoiceRecorderState {
  const { path, elapsed } = opts;
  if (path == null || elapsed < kVoiceRecordingMinDuration) {
    return voiceRecorderReset();
  }
  return new VoiceRecorderState({ phase: VoiceRecorderPhase.review, elapsed, recordingPath: path });
}

export function voiceRecordingCancelled(): VoiceRecorderState {
  return voiceRecorderReset();
}

export function voiceSendStarted(state: VoiceRecorderState): VoiceRecorderState {
  return state.copyWith({ phase: VoiceRecorderPhase.sending, error: null });
}

export function voiceSendSucceeded(): VoiceRecorderState {
  return voiceRecorderReset();
}

export function voiceSendFailed(opts: { state: VoiceRecorderState; failure: unknown }): VoiceRecorderState {
  // Drop back to review so the user can retry sending the same clip.
  return opts.state.copyWith({
    phase: VoiceRecorderPhase.review,
    error: userFacingErrorMessage(opts.failure, { fallback: '发送语音消息失败' }),
  });
}

/** Compact voice duration label. Minutes use `'` and seconds use `"`. */
export function formatVoiceDuration(duration: number): string {
  const clamped = duration < 0 ? 0 : duration;
  return _formatVoiceDurationLabel(Math.trunc(clamped / 1000));
}

/**
 * Filename for an outgoing voice clip. Voice messages ride the regular file
 * attachment path, so they need a stable name and an audio mime the server and
 * renderer can recognise.
 */
export const kVoiceMessageMimeType = 'audio/mp4';
export const kVoiceMessageExtension = 'm4a';
export const kVoiceMessageAttachmentType = 'audio';

/** Browsers that can't record AAC send WAV instead, which every client plays. */
export const kVoiceMessageWavMimeType = 'audio/wav';
export const kVoiceMessageWavExtension = 'wav';

export function voiceMessageFilename(timestamp: Date, opts: { extension?: string } = {}): string {
  const { extension = kVoiceMessageExtension } = opts;
  const stamp = timestamp.getTime();
  return `voice_${stamp}.${extension}`;
}

export function voiceMessageAttachment(message: Message): MessageAttachment | null {
  const messageIsAudio = message.type === kVoiceMessageAttachmentType;
  for (const attachment of message.attachments) {
    if (_isVoiceAttachment(attachment, { messageIsAudio })) {
      return attachment;
    }
  }
  return null;
}

function _isVoiceAttachment(attachment: MessageAttachment, opts: { messageIsAudio: boolean }): boolean {
  const asset = attachment.asset;
  const typeIsAudio = attachment.type === kVoiceMessageAttachmentType;
  const mimeIsAudio = file_display.isAudioMimeType(asset?.mimeType);
  const title = file_display.fileAttachmentTitle(attachment).toLowerCase();
  const legacyVoiceFile = attachment.type === 'file' && mimeIsAudio && file_display.basename(title).startsWith('voice_');
  return (opts.messageIsAudio || typeIsAudio || legacyVoiceFile) && (asset != null || attachment.durationMs != null);
}

/** The attachment's duration in milliseconds. */
export function voiceAttachmentDuration(attachment: MessageAttachment): number | null {
  const durationMs = attachment.durationMs;
  if (durationMs == null || durationMs <= 0) return null;
  return durationMs;
}

export function formatVoiceBubbleDuration(duration: number | null): string {
  if (duration == null) return '';
  const clamped = duration < 0 ? 0 : duration;
  const totalSeconds = Math.min(Math.max(Math.trunc((Math.trunc(clamped) + 999) / 1000), 0), 9999);
  return _formatVoiceDurationLabel(totalSeconds);
}

function _formatVoiceDurationLabel(totalSeconds: number): string {
  const secondsMark = '"';
  if (totalSeconds < 60) return `${totalSeconds}${secondsMark}`;
  const minutes = Math.trunc(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}'${seconds.toString().padStart(2, '0')}${secondsMark}`;
}

export const kVoiceWaveformMinWidth = 96;
export const kVoiceWaveformMaxWidth = 160;

export function voiceWaveformWidth(duration: number | null): number {
  if (duration == null || duration <= 0) {
    return kVoiceWaveformMinWidth;
  }
  const maxSeconds = Math.trunc(kVoiceRecordingMaxDuration / 1000);
  const seconds = Math.min(Math.max(Math.trunc((Math.trunc(duration) + 999) / 1000), 1), maxSeconds);
  const ratio = Math.log(seconds + 1) / Math.log(maxSeconds + 1);
  return kVoiceWaveformMinWidth + (kVoiceWaveformMaxWidth - kVoiceWaveformMinWidth) * ratio;
}

export function voicePlaybackProgress(opts: { position: number; duration: number | null }): number {
  const total = opts.duration;
  if (total == null || total <= 0) return 0;
  const ratio = Math.trunc(opts.position) / Math.trunc(total);
  return Math.min(Math.max(ratio, 0), 1);
}
