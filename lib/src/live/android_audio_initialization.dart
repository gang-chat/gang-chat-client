import 'package:flutter_webrtc/flutter_webrtc.dart' as rtc;

/// WebRTC factory options used only by the Android app process.
///
/// Some Android vendors expose hardware AEC/noise suppression even when their
/// implementation produces a persistent buzz or other artifacts. Keep the
/// normal WebRTC voice-processing pipeline, but let its software APM handle
/// echo and noise consistently across devices.
const Map<String, dynamic> androidWebRtcInitializationOptions = {
  'androidUseHardwareAudioProcessing': false,
};

Future<void> initializeAndroidWebRtcAudio() {
  return rtc.WebRTC.initialize(options: androidWebRtcInitializationOptions);
}
