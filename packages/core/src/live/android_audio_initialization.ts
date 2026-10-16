/**
 * Port of `lib/src/live/android_audio_initialization.dart`.
 *
 * PORT: no-op. The Flutter Android app initialised flutter_webrtc's native
 * factory with software AEC/NS and media playback attributes. Android stays
 * on Flutter for now, and a future React Native app configures the same
 * behaviour through `@livekit/react-native`'s `AudioSession` /
 * `registerGlobals` instead of this options map. The map is kept verbatim so
 * that app can reuse the values.
 */
import type { JsonMap } from '../lib/json';

/**
 * WebRTC factory options used only by the Android app process.
 *
 * Some Android vendors expose hardware AEC/noise suppression even when their
 * implementation produces a persistent buzz or other artifacts. Keep the
 * normal WebRTC voice-processing pipeline, but let its software APM handle
 * echo and noise consistently across devices.
 *
 * WebRTC creates one native AudioTrack for the complete remote-room mix. Its
 * audio attributes are immutable after the WebRTC factory is created, so they
 * must be media attributes from process startup. Runtime routing can still
 * switch between communication mode for voice-only rooms and normal mode
 * while the music-box participant is audible.
 */
export const androidWebRtcInitializationOptions: Readonly<JsonMap> = {
  androidUseHardwareAudioProcessing: false,
  androidAudioConfiguration: {
    androidAudioAttributesUsageType: 'media',
    androidAudioAttributesContentType: 'music',
  },
};

export async function initializeAndroidWebRtcAudio(): Promise<void> {
  // PORT: see the file comment; nothing to initialise outside Flutter.
}
