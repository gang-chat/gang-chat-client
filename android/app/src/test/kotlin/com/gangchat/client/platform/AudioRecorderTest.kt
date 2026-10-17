package com.gangchat.client.platform

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaCodecInfo
import android.media.MediaFormat
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.pumpEventQueue
import java.io.File
import java.nio.ByteBuffer
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder
import org.robolectric.shadows.MediaCodecInfoBuilder
import org.robolectric.shadows.ShadowAudioRecord
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowMediaCodec
import org.robolectric.shadows.ShadowMediaCodecList
import org.robolectric.util.ReflectionHelpers

/**
 * The `record`-plugin equivalent over Robolectric's AudioRecord, MediaCodec
 * and MediaMuxer (no Dart test existed). The AAC encoder here is a
 * pass-through test codec that reports no codec-specific data, so -- as in
 * the plugin -- the MPEG-4 container is never opened: these check the format
 * request, the session lifecycle, focus, routing and file handling, not real
 * AAC output.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioRecorderTest {
    @get:Rule
    val gang = GangTestRule()

    private lateinit var application: Application
    private lateinit var activity: ComponentActivity
    private val audioManager: AudioManager
        get() = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val voiceConfig = RecordConfig(encoder = AudioEncoder.aacLc, bitRate = 64000, sampleRate = 44100, numChannels = 1)

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        GangChatPlatform.attachActivity(activity)
        ShadowLog.stream = System.out
    }

    @After
    fun tearDown() {
        GangChatPlatform.detachActivity(activity)
        ShadowAudioRecord.clearSource()
        ShadowMediaCodec.clearCodecs()
        ShadowMediaCodecList.reset()
    }

    private fun installAacEncoder() {
        val capabilities =
            MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
                .setMediaFormat(AudioRecorder.aacMediaFormat(voiceConfig))
                .setIsEncoder(true)
                .setProfileLevels(
                    arrayOf(
                        MediaCodecInfo.CodecProfileLevel().apply {
                            profile = MediaCodecInfo.CodecProfileLevel.AACObjectLC
                        },
                    ),
                )
                .build()
        ShadowMediaCodecList.addCodec(
            MediaCodecInfoBuilder.newBuilder()
                .setName("c2.test.aac.encoder")
                .setIsEncoder(true)
                .setCapabilities(capabilities)
                .build(),
        )
        ShadowMediaCodec.addEncoder(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            ShadowMediaCodec.CodecConfig(
                4096,
                4096,
                object : ShadowMediaCodec.CodecConfig.Codec {
                    override fun process(input: ByteBuffer, output: ByteBuffer) {
                        output.put(input)
                    }

                    override fun onConfigured(
                        format: MediaFormat,
                        surface: android.view.Surface?,
                        crypto: android.media.MediaCrypto?,
                        flags: Int,
                    ) {
                        // A real AAC encoder reports its AudioSpecificConfig.
                        format.setByteBuffer("csd-0", ByteBuffer.wrap(byteArrayOf(0x12, 0x08)))
                    }
                },
            ),
        )
        // A steady half-scale signal: -6.02 dBFS.
        ShadowAudioRecord.setSourceProvider {
            object : ShadowAudioRecord.AudioRecordSource {
                override fun readInShortArray(
                    audioData: ShortArray,
                    offsetInShorts: Int,
                    sizeInShorts: Int,
                    isBlocking: Boolean,
                ): Int {
                    Thread.sleep(5)
                    for (i in offsetInShorts until offsetInShorts + sizeInShorts) audioData[i] = 16384
                    return sizeInShorts
                }
            }
        }
    }

    private fun grantMicrophone() {
        shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
    }

    @Test
    fun `the voice clip format asks the encoder for mono AAC-LC at 64 kbps and 44_1 kHz`() {
        val format = AudioRecorder.aacMediaFormat(voiceConfig)

        assertEquals("audio/mp4a-latm", format.getString(MediaFormat.KEY_MIME))
        assertEquals(44100, format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
        assertEquals(1, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
        assertEquals(64000, format.getInteger(MediaFormat.KEY_BIT_RATE))
        assertEquals(MediaCodecInfo.CodecProfileLevel.AACObjectLC, format.getInteger(MediaFormat.KEY_AAC_PROFILE))
    }

    @Test
    fun `encoder selection uses the first AAC encoder that accepts the format`() {
        installAacEncoder()

        val selected = AudioRecorder._selectAacFormat(voiceConfig)

        assertEquals("c2.test.aac.encoder", selected.codecName)
        assertEquals(44100, selected.mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE))
        assertEquals(1, selected.mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
        assertEquals(64000, selected.mediaFormat.getInteger(MediaFormat.KEY_BIT_RATE))
    }

    @Test
    fun `without an AAC encoder selection fails like the plugin`() {
        try {
            AudioRecorder._selectAacFormat(voiceConfig)
            fail("expected an exception")
        } catch (error: Exception) {
            assertTrue(error.message!!.startsWith("No codec found for given config"))
        }
    }

    @Test
    fun `hasPermission reports a granted microphone without prompting`() =
        gang.runTest {
            grantMicrophone()

            assertTrue(AudioRecorder().hasPermission())
            assertNull(shadowOf(activity).lastRequestedPermission)
        }

    @Test
    fun `hasPermission asks for the microphone and returns the answer`() =
        gang.runTest {
            val recorder = AudioRecorder()
            assertFalse(recorder.hasPermission(request = false))

            val answer = async { recorder.hasPermission() }
            pumpEventQueue()
            val request = shadowOf(activity).lastRequestedPermission
            assertNotNull(request)
            assertEquals(listOf(Manifest.permission.RECORD_AUDIO), request.requestedPermissions.toList())
            @Suppress("DEPRECATION")
            activity.onRequestPermissionsResult(
                request.requestCode,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                intArrayOf(PackageManager.PERMISSION_DENIED),
            )
            pumpEventQueue()

            assertFalse(answer.await())
        }

    @Test
    fun `without an activity there is no permission`() =
        gang.runTest {
            GangChatPlatform.detachActivity(activity)
            assertFalse(AudioRecorder().hasPermission())
        }

    /** Runs [body] with a recorder that is always disposed, so a failure cannot leave its thread or timer running. */
    private suspend fun withRecorder(body: suspend (AudioRecorder) -> Unit) {
        val recorder = AudioRecorder()
        try {
            body(recorder)
        } finally {
            try {
                recorder.dispose()
            } catch (_: PlatformException) {
            }
        }
    }

    @Test
    fun `records to the requested path, holding media focus until stop`() =
        gang.runTest {
            grantMicrophone()
            installAacEncoder()
            withRecorder { recorder ->
                val path = File(gang.tempDir, "voice_1.m4a").path

                assertNull(recorder.stop())
                assertEquals(0.0, recorder.getAmplitude().current, 0.0)

                recorder.start(voiceConfig, path = path)
                assertTrue("recording after start", recorder.isRecording())
                val focus = shadowOf(audioManager).lastAudioFocusRequest.audioFocusRequest
                assertEquals(AudioManager.AUDIOFOCUS_GAIN, focus.focusGain)
                assertEquals(AudioAttributes.USAGE_MEDIA, focus.audioAttributes.usage)
                assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, focus.audioAttributes.contentType)

                withContext(Dispatchers.IO) { Thread.sleep(100) }
                val amplitude = recorder.getAmplitude()
                assertEquals(-6.02, amplitude.current, 0.01)
                assertEquals(-6.02, amplitude.max, 0.01)

                assertEquals(path, recorder.stop())
                assertFalse("recording after stop", recorder.isRecording())
                // No codec-specific data from the test codec, so no container.
                assertFalse(File(path).exists())
                assertEquals(focus, shadowOf(audioManager).lastAbandonedAudioFocusRequest)
                // Stopping again still reports the last path, like the plugin.
                assertEquals(path, recorder.stop())
                assertEquals(-160.0, recorder.getAmplitude().current, 0.0)
            }
        }

    @Test
    fun `losing audio focus pauses the recording`() =
        gang.runTest {
            grantMicrophone()
            installAacEncoder()
            withRecorder { recorder ->
                recorder.start(voiceConfig, path = File(gang.tempDir, "voice_6.m4a").path)
                val listener = shadowOf(audioManager).lastAudioFocusRequest.listener
                listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                // Paused recordings still count as recording, like the plugin.
                assertTrue(recorder.isRecording())
                assertEquals(File(gang.tempDir, "voice_6.m4a").path, recorder.stop())
            }
        }

    @Test
    fun `cancel deletes the recording once the thread finishes`() =
        gang.runTest {
            grantMicrophone()
            installAacEncoder()
            withRecorder { recorder ->
                val path = File(gang.tempDir, "voice_2.m4a").path
                // Stands in for the container the encoder would have opened.
                File(path).writeBytes(byteArrayOf(1))

                recorder.start(voiceConfig, path = path)
                withContext(Dispatchers.IO) { Thread.sleep(50) }
                recorder.cancel()
                // A new recording waits for the cancelled one to finish.
                val next = File(gang.tempDir, "voice_3.m4a").path
                recorder.start(voiceConfig, path = next)
                assertFalse("cancelled file deleted", File(path).exists())
                assertEquals(next, recorder.stop())
            }
        }

    @Test
    fun `amplitude stream ticks while recording`() =
        gang.runTest {
            grantMicrophone()
            installAacEncoder()
            withRecorder { recorder ->
                // Waiting on the recording thread lets the test scheduler jump
                // virtual time, so nobody listens while the test blocks.
                val values = mutableListOf<Amplitude>()
                var subscription = recorder.onAmplitudeChanged(200.milliseconds).listen({ values.add(it) })
                testScheduler.advanceTimeBy(450.milliseconds)
                pumpEventQueue()
                // Not recording yet: ticks deliver nothing.
                assertEquals(0, values.size)
                subscription.cancel()

                recorder.start(voiceConfig, path = File(gang.tempDir, "voice_4.m4a").path)
                withContext(Dispatchers.IO) { Thread.sleep(50) }
                pumpEventQueue()
                subscription = recorder.onAmplitudeChanged(200.milliseconds).listen({ values.add(it) })
                testScheduler.advanceTimeBy(450.milliseconds)
                pumpEventQueue()

                assertEquals(2, values.size)
                assertEquals(-6.02, values.last().current, 0.01)
                assertEquals(-6.02, values.last().max, 0.01)
                subscription.cancel()

                recorder.stop()
                subscription = recorder.onAmplitudeChanged(200.milliseconds).listen({ values.add(it) })
                testScheduler.advanceTimeBy(450.milliseconds)
                pumpEventQueue()
                // Stopping ends the monitoring.
                assertEquals(2, values.size)
                subscription.cancel()
            }
        }

    /** A device with its own id (the builder leaves every id at 0). */
    private fun device(type: Int, id: Int, source: Boolean = false): AudioDeviceInfo {
        val device = AudioDeviceInfoBuilder.newBuilder().setType(type).build()
        val port = ReflectionHelpers.getField<Any>(device, "mPort")
        ReflectionHelpers.setField(ReflectionHelpers.getField<Any>(port, "mHandle"), "mId", id)
        // The builder leaves the port role unset; capture devices are sources.
        if (source) ReflectionHelpers.setField(port, "mRole", 1)
        return device
    }

    private fun bluetoothHeadset(): AudioDeviceInfo {
        val headset = device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, id = 7, source = true)
        assertTrue(headset.isSource)
        val shadowAudio = shadowOf(audioManager)
        shadowAudio.setInputDevices(listOf(headset))
        shadowAudio.setIsBluetoothScoAvailableOffCall(true)
        return headset
    }

    @Test
    fun `a connected Bluetooth SCO headset becomes the communication device while recording`() =
        gang.runTest {
            grantMicrophone()
            installAacEncoder()
            val headset = bluetoothHeadset()
            shadowOf(audioManager).setAvailableCommunicationDevices(listOf(headset))
            withRecorder { recorder ->
                recorder.start(voiceConfig, path = File(gang.tempDir, "voice_5.m4a").path)
                assertEquals(headset, audioManager.communicationDevice)

                // Fixed: the plugin kept the route after stopping.
                recorder.stop()
                assertNull(audioManager.communicationDevice)

                recorder.start(voiceConfig, path = File(gang.tempDir, "voice_6.m4a").path)
                assertEquals(headset, audioManager.communicationDevice)
                recorder.cancel()
                assertNull(audioManager.communicationDevice)
            }
        }

    @Test
    fun `releasing the headset route restores the previous communication device`() =
        gang.runTest {
            grantMicrophone()
            installAacEncoder()
            val headset = bluetoothHeadset()
            val speaker = device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, id = 3)
            shadowOf(audioManager).setAvailableCommunicationDevices(listOf(speaker, headset))
            assertTrue(audioManager.setCommunicationDevice(speaker))
            withRecorder { recorder ->
                recorder.start(voiceConfig, path = File(gang.tempDir, "voice_7.m4a").path)
                assertEquals(headset, audioManager.communicationDevice)

                // The plugin cleared the communication device here.
                recorder.cancel()
                assertEquals(speaker, audioManager.communicationDevice)
            }
        }

    @Test
    fun `recording during a voice call leaves the call's route alone`() =
        gang.runTest {
            grantMicrophone()
            installAacEncoder()
            val headset = bluetoothHeadset()
            val speaker = device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, id = 3)
            shadowOf(audioManager).setAvailableCommunicationDevices(listOf(speaker, headset))
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            assertTrue(audioManager.setCommunicationDevice(speaker))
            withRecorder { recorder ->
                recorder.start(voiceConfig, path = File(gang.tempDir, "voice_8.m4a").path)
                assertEquals(speaker, audioManager.communicationDevice)

                recorder.cancel()
                assertEquals(speaker, audioManager.communicationDevice)
            }
        }

    @Test
    fun `without a headset, stopping or cancelling keeps the communication device`() =
        gang.runTest {
            grantMicrophone()
            installAacEncoder()
            val speaker = device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, id = 3)
            shadowOf(audioManager).setAvailableCommunicationDevices(listOf(speaker))
            assertTrue(audioManager.setCommunicationDevice(speaker))
            withRecorder { recorder ->
                recorder.start(voiceConfig, path = File(gang.tempDir, "voice_9.m4a").path)
                recorder.stop()
                assertEquals(speaker, audioManager.communicationDevice)

                recorder.start(voiceConfig, path = File(gang.tempDir, "voice_10.m4a").path)
                // The plugin cleared the communication device on cancel, even
                // though it never set one.
                recorder.cancel()
                assertEquals(speaker, audioManager.communicationDevice)
            }
        }

    @Test
    fun `a disposed recorder rejects calls like the plugin`() =
        gang.runTest {
            val recorder = AudioRecorder()
            recorder.dispose()
            try {
                recorder.isRecording()
                fail("expected PlatformException")
            } catch (error: PlatformException) {
                assertEquals("record", error.code)
            }
        }
}
