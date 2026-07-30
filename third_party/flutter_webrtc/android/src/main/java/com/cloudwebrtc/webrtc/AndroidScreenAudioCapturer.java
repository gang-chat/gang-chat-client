package com.cloudwebrtc.webrtc;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.webrtc.audio.JavaAudioDeviceModule;

import java.nio.ByteBuffer;

/**
 * Feeds Android playback-capture PCM into an isolated WebRTC audio factory.
 *
 * <p>The app UID is explicitly excluded so Gang Chat voice, sound effects,
 * and music-box playback cannot be captured back into the shared audio.</p>
 */
@RequiresApi(api = Build.VERSION_CODES.Q)
final class AndroidScreenAudioCapturer
        implements JavaAudioDeviceModule.AudioBufferCallback {
    private static final String TAG = "ScreenAudioCapturer";
    private static final int BUFFER_SIZE_FACTOR = 2;
    private static final long EMPTY_BUFFER_DELAY_MILLIS = 10L;

    private final Object audioRecordLock = new Object();
    private final MediaProjection mediaProjection;

    private AudioRecord audioRecord;
    private boolean initializationAttempted;
    private boolean released;

    AndroidScreenAudioCapturer(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
    }

    @Override
    public long onBuffer(
            ByteBuffer buffer,
            int audioFormat,
            int channelCount,
            int sampleRate,
            int ignoredBytesRead,
            long ignoredCaptureTimeNs) {
        AudioRecord current = ensureAudioRecord(
                audioFormat,
                channelCount,
                sampleRate,
                buffer.capacity());
        if (current == null) {
            clearBuffer(buffer);
            SystemClock.sleep(EMPTY_BUFFER_DELAY_MILLIS);
            return System.nanoTime();
        }

        buffer.clear();
        final int read;
        try {
            read = current.read(buffer, buffer.capacity(), AudioRecord.READ_BLOCKING);
        } catch (RuntimeException error) {
            Log.e(TAG, "Playback AudioRecord.read failed", error);
            releaseAudioResources();
            clearBuffer(buffer);
            return System.nanoTime();
        }

        if (read < 0) {
            Log.e(TAG, "Playback AudioRecord.read failed with code " + read);
            releaseAudioResources();
            clearBuffer(buffer);
        } else if (read < buffer.capacity()) {
            for (int i = Math.max(read, 0); i < buffer.capacity(); i++) {
                buffer.put(i, (byte) 0);
            }
        }
        buffer.position(0);
        return System.nanoTime();
    }

    private AudioRecord ensureAudioRecord(
            int audioFormat,
            int channelCount,
            int sampleRate,
            int callbackBufferSize) {
        synchronized (audioRecordLock) {
            if (released) return null;
            if (audioRecord != null) return audioRecord;
            if (initializationAttempted) return null;
            initializationAttempted = true;

            if (channelCount != 1 && channelCount != 2) {
                Log.e(TAG, "Unsupported playback channel count: " + channelCount);
                return null;
            }

            final int channelMask = channelCount == 1
                    ? AudioFormat.CHANNEL_IN_MONO
                    : AudioFormat.CHANNEL_IN_STEREO;
            final int encoding = audioFormat == AudioFormat.ENCODING_DEFAULT
                    ? AudioFormat.ENCODING_PCM_16BIT
                    : audioFormat;
            final int minBufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    channelMask,
                    encoding);
            if (minBufferSize <= 0) {
                Log.e(TAG, "Invalid playback capture buffer size: " + minBufferSize);
                return null;
            }

            final AudioPlaybackCaptureConfiguration captureConfiguration;
            try {
                captureConfiguration =
                        new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                                .excludeUid(Process.myUid())
                                .build();
            } catch (RuntimeException error) {
                Log.e(TAG, "Failed to configure Android playback capture", error);
                return null;
            }

            AudioRecord created = null;
            try {
                created = new AudioRecord.Builder()
                        .setAudioFormat(
                                new AudioFormat.Builder()
                                        .setEncoding(encoding)
                                        .setSampleRate(sampleRate)
                                        .setChannelMask(channelMask)
                                        .build())
                        .setBufferSizeInBytes(
                                Math.max(BUFFER_SIZE_FACTOR * minBufferSize, callbackBufferSize))
                        .setAudioPlaybackCaptureConfig(captureConfiguration)
                        .build();
                created.startRecording();
                if (created.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.e(TAG, "Playback AudioRecord did not enter recording state");
                    created.release();
                    return null;
                }
                audioRecord = created;
                Log.i(
                        TAG,
                        "Android playback capture started at "
                                + sampleRate
                                + " Hz, channels="
                                + channelCount);
                return audioRecord;
            } catch (RuntimeException error) {
                Log.e(TAG, "Failed to start Android playback capture", error);
                if (created != null) {
                    created.release();
                }
                return null;
            }
        }
    }

    void releaseAudioResources() {
        synchronized (audioRecordLock) {
            released = true;
            AudioRecord current = audioRecord;
            audioRecord = null;
            if (current == null) return;
            try {
                current.stop();
            } catch (RuntimeException ignored) {
                // A revoked MediaProjection can stop the record first.
            }
            current.release();
        }
    }

    private static void clearBuffer(ByteBuffer buffer) {
        buffer.clear();
        for (int i = 0; i < buffer.capacity(); i++) {
            buffer.put(i, (byte) 0);
        }
        buffer.position(0);
    }
}
