package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.platform.Amplitude
import com.gangchat.client.platform.AudioEncoder
import com.gangchat.client.platform.AudioRecorder
import com.gangchat.client.platform.RecordConfig
import com.gangchat.client.testing.GangTestRule
import java.io.File
import java.io.FileNotFoundException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/** The voice clip recorder over a fake `record` plugin (no Dart test existed). */
class VoiceRecorderControllerTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `start records mono AAC-LC at 64 kbps and 44_1 kHz into a timestamped temp m4a`() =
        gang.runTest {
            gang.setNow(DateTime.fromMillisecondsSinceEpoch(1_767_225_600_123L, isUtc = true))
            val recorder = _FakeAudioRecorder()
            val controller = VoiceRecorderController(recorder)

            controller.start()

            val config = recorder.startedConfig!!
            assertEquals(AudioEncoder.aacLc, config.encoder)
            assertEquals(64000, config.bitRate)
            assertEquals(44100, config.sampleRate)
            assertEquals(1, config.numChannels)
            assertEquals(File(gang.tempDir, "tmp/voice_1767225600123000.m4a").path, recorder.startedPath)
            assertEquals("audio/mp4", controller.clipMimeType)
            assertEquals("m4a", controller.clipExtension)
        }

    @Test
    fun `start throws the permission error when the microphone is denied`() =
        gang.runTest {
            val recorder = _FakeAudioRecorder(permission = false)
            val controller = VoiceRecorderController(recorder)

            assertFalse(controller.hasPermission())
            try {
                controller.start()
                fail("expected VoiceRecorderPermissionException")
            } catch (error: VoiceRecorderPermissionException) {
                assertEquals("麦克风权限被拒绝", error.toString())
                assertEquals("麦克风权限被拒绝", userFacingErrorMessage(error))
            }
            assertNull(recorder.startedPath)
        }

    @Test
    fun `stop returns the recorded path and the clip can be read then discarded`() =
        gang.runTest {
            val recorder = _FakeAudioRecorder()
            val controller = VoiceRecorderController(recorder)
            controller.start()
            val path = recorder.startedPath!!
            File(path).writeBytes(byteArrayOf(7, 8, 9))

            assertEquals(path, controller.stop())
            assertArrayEquals(byteArrayOf(7, 8, 9), controller.readClip(path))
            // Reading keeps the file so a failed send can retry.
            assertTrue(File(path).exists())

            controller.discardClip(path)
            assertFalse(File(path).exists())
            // A stale path is fine.
            controller.discardClip(path)
            controller.discardClip(null)
            try {
                controller.readClip(path)
                fail("expected FileNotFoundException")
            } catch (_: FileNotFoundException) {
            }
        }

    @Test
    fun `cancel deletes the temp clip even when the recorder fails to cancel`() =
        gang.runTest {
            val recorder = _FakeAudioRecorder()
            val controller = VoiceRecorderController(recorder)
            controller.start()
            val path = recorder.startedPath!!
            File(path).writeBytes(byteArrayOf(1))
            recorder.cancelError = IllegalStateException("record failed")

            try {
                controller.cancel()
                fail("expected the recorder error")
            } catch (error: IllegalStateException) {
                assertEquals("record failed", error.message)
            }
            assertTrue(recorder.cancelled)
            assertFalse(File(path).exists())

            // After stop the current path is cleared, so cancel leaves the clip alone.
            recorder.cancelError = null
            controller.start()
            val second = recorder.startedPath!!
            File(second).writeBytes(byteArrayOf(2))
            controller.stop()
            controller.cancel()
            assertTrue(File(second).exists())
        }

    @Test
    fun `amplitude stream and dispose go to the recorder`() =
        gang.runTest {
            val recorder = _FakeAudioRecorder()
            val controller = VoiceRecorderController(recorder)

            assertSame(recorder.amplitudes.stream, controller.amplitudeStream())
            assertEquals(200.milliseconds, recorder.amplitudeInterval)
            controller.amplitudeStream(interval = 50.milliseconds)
            assertEquals(50.milliseconds, recorder.amplitudeInterval)

            controller.dispose()
            assertTrue(recorder.disposed)
        }

    private class _FakeAudioRecorder(private val permission: Boolean = true) : AudioRecorder() {
        var startedConfig: RecordConfig? = null
        var startedPath: String? = null
        var cancelled = false
        var cancelError: Exception? = null
        var disposed = false
        var amplitudeInterval: Duration? = null
        val amplitudes = StreamController.broadcast<Amplitude>()

        override suspend fun hasPermission(request: Boolean): Boolean = permission

        override suspend fun start(config: RecordConfig, path: String) {
            startedConfig = config
            startedPath = path
        }

        override suspend fun stop(): String? = startedPath

        override suspend fun cancel() {
            cancelled = true
            cancelError?.let { throw it }
        }

        override fun onAmplitudeChanged(interval: Duration): Stream<Amplitude> {
            amplitudeInterval = interval
            return amplitudes.stream
        }

        override suspend fun dispose() {
            disposed = true
        }
    }
}
