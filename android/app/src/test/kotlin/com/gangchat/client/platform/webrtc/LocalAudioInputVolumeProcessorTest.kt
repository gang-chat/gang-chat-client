package com.gangchat.client.platform.webrtc

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Port of the flutter_webrtc fork's
 * android/src/test/java/com/cloudwebrtc/webrtc/audio/AudioProcessingAdapterTest.java.
 */
class LocalAudioInputVolumeProcessorTest {
    @Test
    fun scaleFloatSamplesUsesNativeByteOrder() {
        val samples = ByteBuffer.allocateDirect(3 * java.lang.Float.BYTES).order(ByteOrder.nativeOrder())
        samples.putFloat(0, 0.75f)
        samples.putFloat(java.lang.Float.BYTES, -0.5f)
        samples.putFloat(2 * java.lang.Float.BYTES, 0.25f)

        // A JNI direct buffer may expose native bytes while retaining Java's
        // default big-endian order metadata.
        samples.order(ByteOrder.BIG_ENDIAN)
        LocalAudioInputVolumeProcessor.scaleFloatSamples(samples, 0.5)

        samples.order(ByteOrder.nativeOrder())
        assertEquals(0.375f, samples.getFloat(0), 0.000001f)
        assertEquals(-0.25f, samples.getFloat(java.lang.Float.BYTES), 0.000001f)
        assertEquals(0.125f, samples.getFloat(2 * java.lang.Float.BYTES), 0.000001f)
    }

    @Test
    fun scaleFloatSamplesOnlyTouchesRemainingCompleteSamples() {
        val samples = ByteBuffer.allocateDirect(4 * java.lang.Float.BYTES).order(ByteOrder.nativeOrder())
        samples.putFloat(0, 1.0f)
        samples.putFloat(java.lang.Float.BYTES, 0.8f)
        samples.putFloat(2 * java.lang.Float.BYTES, -0.4f)
        samples.putFloat(3 * java.lang.Float.BYTES, -1.0f)
        samples.position(java.lang.Float.BYTES)
        samples.limit(3 * java.lang.Float.BYTES)

        LocalAudioInputVolumeProcessor.scaleFloatSamples(samples, 0.25)

        samples.clear()
        samples.order(ByteOrder.nativeOrder())
        assertEquals(1.0f, samples.getFloat(0), 0.000001f)
        assertEquals(0.2f, samples.getFloat(java.lang.Float.BYTES), 0.000001f)
        assertEquals(-0.1f, samples.getFloat(2 * java.lang.Float.BYTES), 0.000001f)
        assertEquals(-1.0f, samples.getFloat(3 * java.lang.Float.BYTES), 0.000001f)
    }

    @Test
    fun boostedVolumeSaturatesAtTheFloatS16Range() {
        // WebRTC's AudioBuffer holds the capture samples as floats in the 16-bit range.
        val samples = ByteBuffer.allocateDirect(4 * java.lang.Float.BYTES).order(ByteOrder.nativeOrder())
        samples.putFloat(0, 1000.0f)
        samples.putFloat(java.lang.Float.BYTES, -2000.0f)
        samples.putFloat(2 * java.lang.Float.BYTES, 30000.0f)
        samples.putFloat(3 * java.lang.Float.BYTES, -30000.0f)

        LocalAudioInputVolumeProcessor.scaleFloatSamples(samples, 1.5)

        assertEquals(1500.0f, samples.getFloat(0), 0.0f)
        assertEquals(-3000.0f, samples.getFloat(java.lang.Float.BYTES), 0.0f)
        assertEquals(32767.0f, samples.getFloat(2 * java.lang.Float.BYTES), 0.0f)
        assertEquals(-32768.0f, samples.getFloat(3 * java.lang.Float.BYTES), 0.0f)
    }

    @Test
    fun processingVolumeAllowsTheBoostAndLeavesUnityUntouched() {
        val processor = LocalAudioInputVolumeProcessor()

        processor.setProcessingVolume(1.5)
        assertEquals(1.5, processor.volume, 0.0)
        processor.setProcessingVolume(4.0)
        assertEquals(1.5, processor.volume, 0.0)
        processor.setProcessingVolume(-1.0)
        assertEquals(0.0, processor.volume, 0.0)
        processor.setProcessingVolume(Double.NaN)
        assertEquals(1.0, processor.volume, 0.0)

        val samples = ByteBuffer.allocateDirect(2 * java.lang.Float.BYTES).order(ByteOrder.nativeOrder())
        samples.putFloat(0, 1200.0f)
        samples.putFloat(java.lang.Float.BYTES, -800.0f)
        processor.processAudio(1, 2, samples)
        assertEquals(1200.0f, samples.getFloat(0), 0.0f)
        assertEquals(-800.0f, samples.getFloat(java.lang.Float.BYTES), 0.0f)

        processor.setProcessingVolume(1.5)
        processor.processAudio(1, 2, samples)
        assertEquals(1800.0f, samples.getFloat(0), 0.0f)
        assertEquals(-1200.0f, samples.getFloat(java.lang.Float.BYTES), 0.0f)
    }
}
