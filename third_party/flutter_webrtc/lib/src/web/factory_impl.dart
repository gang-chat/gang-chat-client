import 'package:dart_webrtc/dart_webrtc.dart' show MediaStream, RTCPeerConnection;

import '../desktop_capturer.dart';

export 'package:dart_webrtc/dart_webrtc.dart'
    hide videoRenderer, MediaDevices, MediaRecorder;

DesktopCapturer get desktopCapturer => throw UnimplementedError();

// gang-chat fork: the isolated screen-audio factory is native only. Browsers
// deliver screen-share audio through getDisplayMedia instead, so these exist
// on web just to keep the shared API compiling.
Future<RTCPeerConnection> createScreenAudioPeerConnection(
    Map<String, dynamic> configuration,
    [Map<String, dynamic> constraints = const {}]) {
  throw UnsupportedError('Screen-audio peer connections are native only.');
}

Future<MediaStream> createScreenAudioTrack() {
  throw UnsupportedError('Screen-audio tracks are native only.');
}
