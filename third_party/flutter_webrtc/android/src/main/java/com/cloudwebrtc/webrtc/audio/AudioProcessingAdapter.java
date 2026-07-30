package com.cloudwebrtc.webrtc.audio;

import org.webrtc.ExternalAudioProcessingFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class AudioProcessingAdapter implements ExternalAudioProcessingFactory.AudioProcessing {
    public interface ExternalAudioFrameProcessing {
        void initialize(int sampleRateHz, int numChannels);

        void reset(int newRate);

        void process(int numBands, int numFrames, ByteBuffer buffer);
    }

    public AudioProcessingAdapter() {}
    List<ExternalAudioFrameProcessing> audioProcessors = new ArrayList<>();
    private volatile double processingVolume = 1.0;

    public void setProcessingVolume(double volume) {
        if (Double.isNaN(volume) || Double.isInfinite(volume)) {
            volume = 1.0;
        }
        processingVolume = Math.max(0.0, Math.min(1.0, volume));
    }

    public void addProcessor(ExternalAudioFrameProcessing audioProcessor) {
        synchronized (audioProcessors) {
            audioProcessors.add(audioProcessor);
        }
    }

    public void removeProcessor(ExternalAudioFrameProcessing audioProcessor) {
        synchronized (audioProcessors) {
            audioProcessors.remove(audioProcessor);
        }
    }

    @Override
    public void initialize(int sampleRateHz, int numChannels) {
        synchronized (audioProcessors) {
            for (ExternalAudioFrameProcessing audioProcessor : audioProcessors) {
                audioProcessor.initialize(sampleRateHz, numChannels);
            }
        }
    }

    @Override
    public void reset(int newRate) {
        synchronized (audioProcessors) {
            for (ExternalAudioFrameProcessing audioProcessor : audioProcessors) {
                audioProcessor.reset(newRate);
            }
        }
    }

    @Override
    public void process(int numBands, int numFrames, ByteBuffer buffer) {
        double volume = processingVolume;
        if (volume < 0.999999) {
            scaleFloatSamples(buffer, volume);
        }
        synchronized (audioProcessors) {
            for (ExternalAudioFrameProcessing audioProcessor : audioProcessors) {
                audioProcessor.process(numBands, numFrames, buffer);
            }
        }
    }

    static void scaleFloatSamples(ByteBuffer buffer, double volume) {
        // WebRTC exposes native float samples through a direct ByteBuffer. The
        // Java buffer's byte-order metadata is not guaranteed to match the
        // native sample layout, so always decode and encode using native order.
        ByteBuffer duplicate = buffer.duplicate().order(ByteOrder.nativeOrder());
        int start = duplicate.position();
        int end = duplicate.limit() - ((duplicate.limit() - start) % Float.BYTES);
        for (int offset = start; offset < end; offset += Float.BYTES) {
            float sample = duplicate.getFloat(offset);
            if (!Float.isNaN(sample) && !Float.isInfinite(sample)) {
                duplicate.putFloat(offset, (float) (sample * volume));
            } else {
                duplicate.putFloat(offset, 0.0f);
            }
        }
    }
}
