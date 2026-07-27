import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/live/android_audio_initialization.dart';

void main() {
  test('Android WebRTC initialization selects software voice processing', () {
    expect(
      androidWebRtcInitializationOptions,
      equals(<String, dynamic>{'androidUseHardwareAudioProcessing': false}),
    );
  });
}
