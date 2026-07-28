import 'dart:async';
import 'dart:io' show Platform;

import 'package:client/src/live/audio_device_service.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart' as rtc;
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:livekit_client/livekit_client.dart' as lk;

// Ground-truth probe for the macOS "no microphone in Settings" bug. Runs the
// real flutter_webrtc/livekit plugins on the host so we can see what
// enumerateDevices() actually returns *before* any room/track exists, and
// whether creating a probe track changes that. Run with:
//   flutter test integration_test/audio_enumeration_probe_test.dart -d macos
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  Future<List<lk.MediaDevice>> enumerate() =>
      lk.Hardware.instance.enumerateDevices();

  testWidgets('enumerateDevices before any track', (tester) async {
    final devices = await enumerate();
    final inputs = devices.where((d) => d.kind == 'audioinput').toList();
    final outputs = devices.where((d) => d.kind == 'audiooutput').toList();
    debugPrint(
      'PROBE cold all=${devices.length} '
      'inputs=${inputs.length} outputs=${outputs.length}',
    );
    for (final d in devices) {
      debugPrint(
        'PROBE cold device kind=${d.kind} id=${d.deviceId} '
        'label=${d.label}',
      );
    }
  });

  testWidgets('enumerateDevices while a probe mic track is live', (
    tester,
  ) async {
    lk.LocalAudioTrack? track;
    try {
      track = await lk.LocalAudioTrack.create();
      await track.start();
      debugPrint('PROBE track created+started ok');
    } catch (e) {
      debugPrint('PROBE track create FAILED: $e');
    }
    final devices = await enumerate();
    final inputs = devices.where((d) => d.kind == 'audioinput').toList();
    debugPrint('PROBE warm all=${devices.length} inputs=${inputs.length}');
    for (final d in devices) {
      debugPrint(
        'PROBE warm device kind=${d.kind} id=${d.deviceId} '
        'label=${d.label}',
      );
    }
    await track?.stop();
    await track?.dispose();
  });

  testWidgets('native default-input channel responds', (tester) async {
    const channel = MethodChannel('gang_chat/audio_devices');
    try {
      final id = await channel.invokeMethod<String>('getDefaultInputDeviceId');
      debugPrint('PROBE native defaultInputDeviceId=$id');
    } catch (e) {
      debugPrint('PROBE native channel FAILED: $e');
    }
  });

  testWidgets('Windows app audio scans use system endpoints only', (
    tester,
  ) async {
    const service = LiveAudioDeviceService(systemAudioOnly: true);
    for (var scan = 0; scan < 50; scan += 1) {
      final devices = await service.enumerateDevices().timeout(
        const Duration(seconds: 2),
      );
      expect(
        devices.every(
          (device) =>
              device.kind == 'audioinput' || device.kind == 'audiooutput',
        ),
        isTrue,
      );
    }
  }, skip: !Platform.isWindows);

  testWidgets(
    'Windows WebRTC getSources never blocks the Flutter UI thread',
    (tester) async {
      var uiTicks = 0;
      final ticker = Timer.periodic(
        const Duration(milliseconds: 1),
        (_) => uiTicks += 1,
      );
      addTearDown(ticker.cancel);

      await Future.wait([
        for (var scan = 0; scan < 20; scan += 1)
          rtc.navigator.mediaDevices.enumerateDevices().timeout(
            const Duration(seconds: 5),
          ),
      ]);

      expect(
        uiTicks,
        greaterThan(0),
        reason: 'native enumeration must complete off the platform/UI thread',
      );
    },
    skip: !Platform.isWindows,
  );

  testWidgets(
    'Windows input/output selection and microphone restart stay responsive',
    (tester) async {
      final devices = await rtc.navigator.mediaDevices.enumerateDevices();
      final input = devices.firstWhere((device) => device.kind == 'audioinput');
      final output = devices.firstWhere(
        (device) => device.kind == 'audiooutput',
      );

      for (var cycle = 0; cycle < 10; cycle += 1) {
        await rtc.Helper.selectAudioInput(
          input.deviceId,
        ).timeout(const Duration(seconds: 5));
        await rtc.Helper.selectAudioOutput(
          output.deviceId,
        ).timeout(const Duration(seconds: 5));
      }

      final stream = await rtc.navigator.mediaDevices
          .getUserMedia({
            'audio': {
              'optional': [
                {'sourceId': input.deviceId},
              ],
            },
            'video': false,
          })
          .timeout(const Duration(seconds: 6));
      expect(stream.getAudioTracks(), hasLength(1));
      for (final track in stream.getTracks()) {
        await track.stop();
      }
      await stream.dispose();
    },
    skip: !Platform.isWindows,
  );

  final requestedSoakMinutes =
      int.tryParse(
        Platform.environment['GANG_CHAT_WEBRTC_SOAK_MINUTES'] ?? '',
      ) ??
      0;
  testWidgets(
    'Windows fullscreen WebRTC video soak keeps frames and UI responsive',
    (tester) async {
      final soakMinutes = requestedSoakMinutes.clamp(5, 30);
      final renderer = rtc.RTCVideoRenderer();
      rtc.MediaStream? stream;
      await renderer.initialize();
      try {
        stream = await rtc.navigator.mediaDevices
            .getUserMedia({'audio': false, 'video': true})
            .timeout(const Duration(seconds: 10));
        renderer.srcObject = stream;
        await tester.pumpWidget(
          Directionality(
            textDirection: TextDirection.ltr,
            child: SizedBox.expand(child: rtc.RTCVideoView(renderer)),
          ),
        );

        final deadline = DateTime.now().add(Duration(minutes: soakMinutes));
        while (DateTime.now().isBefore(deadline)) {
          await Future<void>.delayed(const Duration(seconds: 1));
          await tester.pump();
          expect(tester.takeException(), isNull);
        }
      } finally {
        renderer.srcObject = null;
        if (stream != null) {
          for (final track in stream.getTracks()) {
            await track.stop();
          }
          await stream.dispose();
        }
        await renderer.dispose();
      }
    },
    skip: !Platform.isWindows || requestedSoakMinutes == 0,
    timeout: const Timeout(Duration(minutes: 35)),
  );
}
