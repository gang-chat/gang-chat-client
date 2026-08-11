import 'package:flutter_webrtc/flutter_webrtc.dart' as rtc;

/// WebRTC factory options used only by the Android app process.
///
/// Some Android vendors expose hardware AEC/noise suppression even when their
/// implementation produces a persistent buzz or other artifacts. Keep the
/// normal WebRTC voice-processing pipeline, but let its software APM handle
/// echo and noise consistently across devices.
///
/// WebRTC creates one native [AudioTrack] for the complete remote-room mix.
/// Its audio attributes are immutable after the WebRTC factory is created, so
/// they must be media attributes from process startup. Runtime routing can
/// still switch between communication mode for voice-only rooms and normal
/// mode while the music-box participant is audible.
const Map<String, dynamic> androidWebRtcInitializationOptions = {
  'androidUseHardwareAudioProcessing': false,
  'androidAudioConfiguration': <String, dynamic>{
    'androidAudioAttributesUsageType': 'media',
    'androidAudioAttributesContentType': 'music',
  },
};

Future<void> initializeAndroidWebRtcAudio() {
  return rtc.WebRTC.initialize(options: androidWebRtcInitializationOptions);
}
