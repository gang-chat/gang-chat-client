package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import kotlin.math.ln
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

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
 */
enum class VoiceRecorderPhase { idle, recording, review, sending }

class VoiceRecorderState(
    val phase: VoiceRecorderPhase = VoiceRecorderPhase.idle,
    /** How long the current (or just-finished) clip runs. */
    val elapsed: Duration = Duration.ZERO,
    /** Filesystem path of the captured clip, set once recording stops. */
    val recordingPath: String? = null,
    val error: String? = null,
) {
    val isRecording: Boolean get() = phase == VoiceRecorderPhase.recording
    val isReviewing: Boolean get() = phase == VoiceRecorderPhase.review
    val isSending: Boolean get() = phase == VoiceRecorderPhase.sending
    val isIdle: Boolean get() = phase == VoiceRecorderPhase.idle

    /** A clip is only worth sending once it has a path and some duration. */
    val canSend: Boolean
        get() =
            phase == VoiceRecorderPhase.review &&
                recordingPath != null &&
                elapsed > Duration.ZERO

    /**
     * Like Dart, [recordingPath] and [error] use a sentinel default: omit them
     * to keep the current value, pass `null` to clear it.
     */
    fun copyWith(
        phase: VoiceRecorderPhase? = null,
        elapsed: Duration? = null,
        recordingPath: Any? = _unchanged,
        error: Any? = _unchanged,
    ): VoiceRecorderState {
        return VoiceRecorderState(
            phase = phase ?: this.phase,
            elapsed = elapsed ?: this.elapsed,
            recordingPath =
                if (recordingPath === _unchanged) {
                    this.recordingPath
                } else {
                    recordingPath as String?
                },
            error = if (error === _unchanged) this.error else error as String?,
        )
    }
}

private val _unchanged = Any()

/**
 * Hard cap so an abandoned recording cannot grow without bound. The widget
 * layer is expected to call [voiceRecordingStopped] when this is reached.
 */
val kVoiceRecordingMaxDuration: Duration = 5.minutes

/** Below this a clip is treated as an accidental tap and discarded on stop. */
val kVoiceRecordingMinDuration: Duration = 800.milliseconds

fun voiceRecorderReset(): VoiceRecorderState = VoiceRecorderState()

fun voiceRecordingStarted(): VoiceRecorderState {
    return VoiceRecorderState(phase = VoiceRecorderPhase.recording)
}

/**
 * Advances the displayed duration while recording. Ignored in other phases so
 * a late timer tick cannot resurrect a cancelled clip.
 */
fun voiceRecordingTicked(
    state: VoiceRecorderState,
    elapsed: Duration,
): VoiceRecorderState {
    if (state.phase != VoiceRecorderPhase.recording) return state
    return state.copyWith(elapsed = elapsed)
}

fun voiceRecordingReachedLimit(elapsed: Duration): Boolean {
    return elapsed >= kVoiceRecordingMaxDuration
}

/**
 * Recording finished. A clip shorter than [kVoiceRecordingMinDuration] is
 * discarded back to idle; otherwise we move to review with the captured path.
 */
fun voiceRecordingStopped(
    state: VoiceRecorderState,
    path: String?,
    elapsed: Duration,
): VoiceRecorderState {
    if (path == null || elapsed < kVoiceRecordingMinDuration) {
        return voiceRecorderReset()
    }
    return VoiceRecorderState(
        phase = VoiceRecorderPhase.review,
        elapsed = elapsed,
        recordingPath = path,
    )
}

fun voiceRecordingCancelled(): VoiceRecorderState = voiceRecorderReset()

fun voiceSendStarted(state: VoiceRecorderState): VoiceRecorderState {
    return state.copyWith(phase = VoiceRecorderPhase.sending, error = null)
}

fun voiceSendSucceeded(): VoiceRecorderState = voiceRecorderReset()

fun voiceSendFailed(
    state: VoiceRecorderState,
    failure: Any,
): VoiceRecorderState {
    // Drop back to review so the user can retry sending the same clip.
    return state.copyWith(
        phase = VoiceRecorderPhase.review,
        error = userFacingErrorMessage(failure, fallback = "发送语音消息失败"),
    )
}

/** Compact voice duration label. Minutes use `'` and seconds use `"`. */
fun formatVoiceDuration(duration: Duration): String {
    val clamped = if (duration.isNegative()) Duration.ZERO else duration
    return _formatVoiceDurationLabel(clamped.inWholeSeconds.toInt())
}

/**
 * Filename for an outgoing voice clip. Voice messages ride the regular file
 * attachment path, so they need a stable name and an audio mime the server and
 * renderer can recognise.
 */
const val kVoiceMessageMimeType: String = "audio/mp4"
const val kVoiceMessageExtension: String = "m4a"
const val kVoiceMessageAttachmentType: String = "audio"

/** Browsers that can't record AAC send WAV instead, which every client plays. */
const val kVoiceMessageWavMimeType: String = "audio/wav"
const val kVoiceMessageWavExtension: String = "wav"

fun voiceMessageFilename(
    timestamp: DateTime,
    extension: String = kVoiceMessageExtension,
): String {
    val stamp = timestamp.toUtc().millisecondsSinceEpoch
    return "voice_$stamp.$extension"
}

fun voiceMessageAttachment(message: Message): MessageAttachment? {
    val messageIsAudio = message.type == kVoiceMessageAttachmentType
    for (attachment in message.attachments) {
        if (_isVoiceAttachment(attachment, messageIsAudio = messageIsAudio)) {
            return attachment
        }
    }
    return null
}

private fun _isVoiceAttachment(
    attachment: MessageAttachment,
    messageIsAudio: Boolean,
): Boolean {
    val asset = attachment.asset
    val typeIsAudio = attachment.type == kVoiceMessageAttachmentType
    val mimeIsAudio = isAudioMimeType(asset?.mimeType)
    val title = fileAttachmentTitle(attachment).lowercase()
    val legacyVoiceFile =
        attachment.type == "file" &&
            mimeIsAudio &&
            basename(title).startsWith("voice_")
    return (messageIsAudio || typeIsAudio || legacyVoiceFile) &&
        (asset != null || attachment.durationMs != null)
}

fun voiceAttachmentDuration(attachment: MessageAttachment): Duration? {
    val durationMs = attachment.durationMs
    if (durationMs == null || durationMs <= 0) return null
    return durationMs.milliseconds
}

fun formatVoiceBubbleDuration(duration: Duration?): String {
    if (duration == null) return ""
    val clamped = if (duration.isNegative()) Duration.ZERO else duration
    val totalSeconds = ((clamped.inWholeMilliseconds + 999) / 1000).coerceIn(0, 9999)
    return _formatVoiceDurationLabel(totalSeconds.toInt())
}

private fun _formatVoiceDurationLabel(totalSeconds: Int): String {
    val secondsMark = "\""
    if (totalSeconds < 60) return "$totalSeconds$secondsMark"
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes'${seconds.toString().padStart(2, '0')}$secondsMark"
}

const val kVoiceWaveformMinWidth: Double = 96.0
const val kVoiceWaveformMaxWidth: Double = 160.0

fun voiceWaveformWidth(duration: Duration?): Double {
    if (duration == null || duration <= Duration.ZERO) {
        return kVoiceWaveformMinWidth
    }
    val seconds =
        ((duration.inWholeMilliseconds + 999) / 1000).coerceIn(
            1,
            kVoiceRecordingMaxDuration.inWholeSeconds,
        )
    val ratio =
        ln((seconds + 1).toDouble()) /
            ln((kVoiceRecordingMaxDuration.inWholeSeconds + 1).toDouble())
    return kVoiceWaveformMinWidth +
        (kVoiceWaveformMaxWidth - kVoiceWaveformMinWidth) * ratio
}

fun voicePlaybackProgress(
    position: Duration,
    duration: Duration?,
): Double {
    val total = duration
    if (total == null || total <= Duration.ZERO) return 0.0
    val ratio = position.inWholeMilliseconds.toDouble() / total.inWholeMilliseconds
    return ratio.coerceIn(0.0, 1.0)
}
