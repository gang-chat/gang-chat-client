import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/live/android_audio_initialization.dart';

void main() {
  test('Android WebRTC initializes playback with media audio attributes', () {
    expect(
      androidWebRtcInitializationOptions,
      equals(<String, dynamic>{
        'androidUseHardwareAudioProcessing': false,
        'androidAudioConfiguration': <String, dynamic>{
          'androidAudioAttributesUsageType': 'media',
          'androidAudioAttributesContentType': 'music',
        },
      }),
    );
  });
}
