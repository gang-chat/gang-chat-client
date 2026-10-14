// ignore_for_file: implementation_imports, invalid_use_of_internal_member

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart' as rtc;
import 'package:livekit_client/livekit_client.dart' as lk;
import 'package:livekit_client/src/utils.dart';

void main() {
  // libwebrtc only accepts bitrate/network priority on encodings[0]; any later
  // encoding with a non-default value makes addTransceiver fail with
  // UNSUPPORTED_PARAMETER (macOS screen share never published).
  test('simulcast priority is carried only on the first encoding', () {
    const options = lk.VideoPublishOptions(
      screenShareEncoding: lk.VideoEncoding(
        maxFramerate: 30,
        maxBitrate: 3000000,
        bitratePriority: lk.Priority.high,
        networkPriority: lk.Priority.high,
      ),
      screenShareSimulcastLayers: [
        lk.VideoParameters(
          dimensions: lk.VideoDimensions(640, 360),
          encoding: lk.VideoEncoding(maxFramerate: 15, maxBitrate: 1000000),
        ),
      ],
    );

    final encodings = Utils.computeVideoEncodings(
      isScreenShare: true,
      dimensions: const lk.VideoDimensions(1280, 720),
      options: options,
    )!;

    expect(encodings, hasLength(2));
    expect(encodings[0].rid, 'q');
    expect(encodings[0].maxBitrate, 1000000);
    expect(encodings[0].priority, rtc.RTCPriorityType.high);
    expect(encodings[0].networkPriority, rtc.RTCPriorityType.high);
    expect(encodings[1].rid, 'h');
    expect(encodings[1].maxBitrate, 3000000);
    expect(encodings[1].priority, rtc.RTCPriorityType.low);
    expect(encodings[1].networkPriority, isNull);
  });

  test('a single encoding keeps its own priority', () {
    const options = lk.VideoPublishOptions(
      simulcast: false,
      screenShareEncoding: lk.VideoEncoding(
        maxFramerate: 30,
        maxBitrate: 3000000,
        bitratePriority: lk.Priority.high,
        networkPriority: lk.Priority.high,
      ),
    );

    final encodings = Utils.computeVideoEncodings(
      isScreenShare: true,
      dimensions: const lk.VideoDimensions(1280, 720),
      options: options,
    )!;

    expect(encodings, hasLength(1));
    expect(encodings[0].priority, rtc.RTCPriorityType.high);
    expect(encodings[0].networkPriority, rtc.RTCPriorityType.high);
  });
}
