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
}
