package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.UploadedAsset
import com.gangchat.client.protocol.UserSummary
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMessageDisplayTest {
    @Test
    fun `start moves from idle to recording with a zeroed timer`() {
        val state = voiceRecordingStarted()
        assertEquals(VoiceRecorderPhase.recording, state.phase)
        assertTrue(state.isRecording)
        assertEquals(Duration.ZERO, state.elapsed)
        assertNull(state.recordingPath)
    }

    @Test
    fun `ticks advance the timer only while recording`() {
        val recording = voiceRecordingStarted()
        val ticked = voiceRecordingTicked(recording, 3.seconds)
        assertEquals(3.seconds, ticked.elapsed)

        // A stray tick after reset must not resurrect the timer.
        val idle = voiceRecorderReset()
        val ignored = voiceRecordingTicked(idle, 9.seconds)
        assertEquals(Duration.ZERO, ignored.elapsed)
        assertEquals(VoiceRecorderPhase.idle, ignored.phase)
    }

    @Test
    fun `stop moves a long-enough clip into review`() {
        val state =
            voiceRecordingStopped(
                state = voiceRecordingStarted(),
                path = "/tmp/voice_1.m4a",
                elapsed = 4.seconds,
            )
        assertEquals(VoiceRecorderPhase.review, state.phase)
        assertTrue(state.isReviewing)
        assertEquals("/tmp/voice_1.m4a", state.recordingPath)
        assertEquals(4.seconds, state.elapsed)
        assertTrue(state.canSend)
    }

    @Test
    fun `stop discards a too-short or pathless clip back to idle`() {
        val tooShort =
            voiceRecordingStopped(
                state = voiceRecordingStarted(),
                path = "/tmp/voice_1.m4a",
                elapsed = 200.milliseconds,
            )
        assertEquals(VoiceRecorderPhase.idle, tooShort.phase)
        assertNull(tooShort.recordingPath)

        val noPath =
            voiceRecordingStopped(
                state = voiceRecordingStarted(),
                path = null,
                elapsed = 5.seconds,
            )
        assertEquals(VoiceRecorderPhase.idle, noPath.phase)
    }

    @Test
    fun `cancel always returns to a clean idle state`() {
        assertEquals(VoiceRecorderPhase.idle, voiceRecordingCancelled().phase)
        assertNull(voiceRecordingCancelled().recordingPath)
        assertNull(voiceRecordingCancelled().error)
    }

    // Dart: 'send transitions review -> sending -> idle on success' (JVM method
    // names cannot contain `>`).
    @Test
    fun `send transitions review → sending → idle on success`() {
        val review =
            voiceRecordingStopped(
                state = voiceRecordingStarted(),
                path = "/tmp/voice_1.m4a",
                elapsed = 4.seconds,
            )
        val sending = voiceSendStarted(review)
        assertEquals(VoiceRecorderPhase.sending, sending.phase)
        assertTrue(sending.isSending)
        assertNull(sending.error)

        assertEquals(VoiceRecorderPhase.idle, voiceSendSucceeded().phase)
    }

    @Test
    fun `send failure returns to review and keeps the clip for retry`() {
        val review =
            voiceRecordingStopped(
                state = voiceRecordingStarted(),
                path = "/tmp/voice_1.m4a",
                elapsed = 4.seconds,
            )
        val failed =
            voiceSendFailed(
                state = voiceSendStarted(review),
                failure = Exception("network down"),
            )
        assertEquals(VoiceRecorderPhase.review, failed.phase)
        assertEquals("/tmp/voice_1.m4a", failed.recordingPath)
        assertEquals("网络连接失败，请检查网络后重试", failed.error)
        assertTrue(failed.canSend)
    }

    @Test
    fun `reachedLimit fires at the max duration`() {
        assertFalse(
            voiceRecordingReachedLimit(4.minutes + 59.seconds),
        )
        assertTrue(voiceRecordingReachedLimit(kVoiceRecordingMaxDuration))
    }

    @Test
    fun `duration formats with minute and second marks`() {
        assertEquals("0\"", formatVoiceDuration(Duration.ZERO))
        assertEquals("4\"", formatVoiceDuration(4.seconds))
        assertEquals("1'05\"", formatVoiceDuration(65.seconds))
        assertEquals(
            "12'09\"",
            formatVoiceDuration(12.minutes + 9.seconds),
        )
        assertEquals("0\"", formatVoiceDuration((-3).seconds))
    }

    @Test
    fun `voice filename carries an audio extension and timestamp`() {
        val name = voiceMessageFilename(DateTime.utc(2026, 6, 1, 12, 0, 0))
        assertTrue(name.startsWith("voice_"))
        assertTrue(name.endsWith(".m4a"))
        assertEquals("audio/mp4", kVoiceMessageMimeType)
    }

    // Dart: 'browser WAV clips keep a .wav name and audio/wav type' (JVM method
    // names cannot contain `.` or `/`).
    @Test
    fun `browser WAV clips keep a wav name and audio-wav type`() {
        val name =
            voiceMessageFilename(
                DateTime.utc(2026, 6, 1, 12, 0, 0),
                extension = kVoiceMessageWavExtension,
            )
        assertTrue(name.endsWith(".wav"))
        assertEquals("audio/wav", kVoiceMessageWavMimeType)
    }

    @Test
    fun `bubble duration uses marked seconds and minutes`() {
        assertEquals("", formatVoiceBubbleDuration(null))
        assertEquals("0\"", formatVoiceBubbleDuration(Duration.ZERO))
        assertEquals("2\"", formatVoiceBubbleDuration(1200.milliseconds))
        assertEquals("15\"", formatVoiceBubbleDuration(15.seconds))
        assertEquals("1'05\"", formatVoiceBubbleDuration(65.seconds))
    }

    @Test
    fun `waveform width grows with duration up to a cap`() {
        assertEquals(96.0, kVoiceWaveformMinWidth, 0.0)
        assertEquals(kVoiceWaveformMinWidth, voiceWaveformWidth(null), 0.0)
        assertEquals(kVoiceWaveformMinWidth, voiceWaveformWidth(Duration.ZERO), 0.0)
        assertTrue(
            voiceWaveformWidth(5.seconds) > kVoiceWaveformMinWidth,
        )
        assertTrue(
            voiceWaveformWidth(20.seconds) > voiceWaveformWidth(5.seconds),
        )
        assertEquals(
            kVoiceWaveformMaxWidth,
            voiceWaveformWidth(5.minutes),
            0.0,
        )
    }

    @Test
    fun `playback progress is clamped to the current duration`() {
        assertEquals(
            0.0,
            voicePlaybackProgress(
                position = 5.seconds,
                duration = null,
            ),
            0.0,
        )
        assertEquals(
            0.0,
            voicePlaybackProgress(
                position = (-1).seconds,
                duration = 10.seconds,
            ),
            0.0,
        )
        assertEquals(
            0.5,
            voicePlaybackProgress(
                position = 5.seconds,
                duration = 10.seconds,
            ),
            0.0,
        )
        assertEquals(
            1.0,
            voicePlaybackProgress(
                position = 15.seconds,
                duration = 10.seconds,
            ),
            0.0,
        )
    }

    @Test
    fun `voice attachment detection handles audio messages and legacy files`() {
        val audio =
            _message(
                type = "audio",
                attachments =
                    listOf(
                        MessageAttachment(
                            type = "audio",
                            name = "voice_1.m4a",
                            durationMs = 15000,
                            asset = _voiceAsset,
                        ),
                    ),
            )
        assertEquals(15000, voiceMessageAttachment(audio)?.durationMs)

        val legacyFile =
            _message(
                type = "file",
                attachments =
                    listOf(
                        MessageAttachment(
                            type = "file",
                            name = "voice_2.m4a",
                            asset = _voiceAsset,
                        ),
                    ),
            )
        assertEquals("voice_2.m4a", voiceMessageAttachment(legacyFile)?.name)

        val normalFile =
            _message(
                type = "file",
                attachments =
                    listOf(
                        MessageAttachment(type = "file", name = "song.m4a", asset = _voiceAsset),
                    ),
            )
        assertNull(voiceMessageAttachment(normalFile))
    }

    private val _sender =
        UserSummary(
            id = "user_1",
            username = "alice",
            displayName = "Alice",
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
        )

    private val _voiceAsset =
        UploadedAsset(
            id = "asset_voice",
            url = "/uploads/voice_1.m4a",
            thumbnailUrl = null,
            mimeType = "audio/mp4",
        )

    private fun _message(
        type: String,
        attachments: List<MessageAttachment>,
    ): Message {
        return Message(
            id = "message_1",
            roomId = "room_1",
            sender = _sender,
            clientMessageId = "client_1",
            type = type,
            body = "voice_1.m4a",
            createdAt = DateTime.utc(2026, 6, 11),
            attachments = attachments,
        )
    }
}
