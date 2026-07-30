package com.cloudwebrtc.webrtc.audio;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.Test;

public class AudioProcessingAdapterTest {
    @Test
    public void scaleFloatSamplesUsesNativeByteOrder() {
        ByteBuffer samples = ByteBuffer.allocateDirect(3 * Float.BYTES)
                .order(ByteOrder.nativeOrder());
        samples.putFloat(0, 0.75f);
        samples.putFloat(Float.BYTES, -0.5f);
        samples.putFloat(2 * Float.BYTES, 0.25f);

        // A JNI direct buffer may expose native bytes while retaining Java's
        // default big-endian order metadata.
        samples.order(ByteOrder.BIG_ENDIAN);
        AudioProcessingAdapter.scaleFloatSamples(samples, 0.5);

        samples.order(ByteOrder.nativeOrder());
        assertEquals(0.375f, samples.getFloat(0), 0.000001f);
        assertEquals(-0.25f, samples.getFloat(Float.BYTES), 0.000001f);
        assertEquals(0.125f, samples.getFloat(2 * Float.BYTES), 0.000001f);
    }

    @Test
    public void scaleFloatSamplesOnlyTouchesRemainingCompleteSamples() {
        ByteBuffer samples = ByteBuffer.allocateDirect(4 * Float.BYTES)
                .order(ByteOrder.nativeOrder());
        samples.putFloat(0, 1.0f);
        samples.putFloat(Float.BYTES, 0.8f);
        samples.putFloat(2 * Float.BYTES, -0.4f);
        samples.putFloat(3 * Float.BYTES, -1.0f);
        samples.position(Float.BYTES);
        samples.limit(3 * Float.BYTES);

        AudioProcessingAdapter.scaleFloatSamples(samples, 0.25);

        samples.clear();
        samples.order(ByteOrder.nativeOrder());
        assertEquals(1.0f, samples.getFloat(0), 0.000001f);
        assertEquals(0.2f, samples.getFloat(Float.BYTES), 0.000001f);
        assertEquals(-0.1f, samples.getFloat(2 * Float.BYTES), 0.000001f);
        assertEquals(-1.0f, samples.getFloat(3 * Float.BYTES), 0.000001f);
    }
}
