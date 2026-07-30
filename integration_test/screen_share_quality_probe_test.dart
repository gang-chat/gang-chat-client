import 'dart:async';
import 'dart:io' show Platform;

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart' as rtc;
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'Windows desktop capture emits real frames capped to each quality profile',
    (tester) async {
      final sources = await rtc.desktopCapturer
          .getSources(types: <rtc.SourceType>[rtc.SourceType.Screen])
          .timeout(const Duration(seconds: 10));
      expect(sources, isNotEmpty);
      final source = sources.first;

      for (final profile in const <({int width, int height, int fps})>[
        (width: 854, height: 480, fps: 15),
        (width: 1280, height: 720, fps: 30),
        (width: 1920, height: 1080, fps: 30),
      ]) {
        rtc.MediaStream? stream;
        final renderer = rtc.RTCVideoRenderer();
        await renderer.initialize();
        try {
          stream = await rtc.navigator.mediaDevices
              .getDisplayMedia(<String, dynamic>{
                'audio': false,
                'video': <String, dynamic>{
                  'width': profile.width,
                  'height': profile.height,
                  'frameRate': profile.fps,
                  'deviceId': <String, dynamic>{'exact': source.id},
                  'mandatory': <String, dynamic>{
                    'frameRate': profile.fps.toDouble(),
                  },
                },
              })
              .timeout(const Duration(seconds: 10));
          expect(stream.getVideoTracks(), hasLength(1));
          renderer.srcObject = stream;
          await tester.pumpWidget(
            Directionality(
              textDirection: TextDirection.ltr,
              child: SizedBox.expand(child: rtc.RTCVideoView(renderer)),
            ),
          );

          final deadline = DateTime.now().add(const Duration(seconds: 8));
          while ((renderer.videoWidth <= 0 || renderer.videoHeight <= 0) &&
              DateTime.now().isBefore(deadline)) {
            await Future<void>.delayed(const Duration(milliseconds: 50));
            await tester.pump();
          }

          debugPrint(
            'SCREEN_QUALITY_PROBE requested=${profile.width}x'
            '${profile.height}/${profile.fps}fps '
            'rendered=${renderer.videoWidth}x${renderer.videoHeight}',
          );
          expect(renderer.videoWidth, greaterThan(0));
          expect(renderer.videoHeight, greaterThan(0));
          expect(renderer.videoWidth, lessThanOrEqualTo(profile.width));
          expect(renderer.videoHeight, lessThanOrEqualTo(profile.height));
          // At least one axis should reach the selected bounding box for a
          // normal monitor. This catches a stale 480p adapter when switching
          // upward without assuming the monitor's aspect ratio.
          expect(
            renderer.videoWidth >= profile.width - 2 ||
                renderer.videoHeight >= profile.height - 2,
            isTrue,
          );
        } finally {
          renderer.srcObject = null;
          if (stream != null) {
            for (final track in stream.getTracks()) {
              await track.stop();
            }
            await stream.dispose();
          }
          await renderer.dispose();
          await tester.pumpWidget(const SizedBox.shrink());
        }
      }
    },
    skip: !Platform.isWindows,
    timeout: const Timeout(Duration(minutes: 2)),
  );

  testWidgets(
    'Windows receiver observes live quality changes after sender track replacement',
    (tester) async {
      final sources = await rtc.desktopCapturer
          .getSources(types: <rtc.SourceType>[rtc.SourceType.Screen])
          .timeout(const Duration(seconds: 10));
      expect(sources, isNotEmpty);
      final source = sources.first;
      final streams = <rtc.MediaStream>[];
      final senderPeer = await rtc.createPeerConnection(<String, dynamic>{
        'iceServers': <Map<String, dynamic>>[],
      });
      final receiverPeer = await rtc.createPeerConnection(<String, dynamic>{
        'iceServers': <Map<String, dynamic>>[],
      });
      final renderer = rtc.RTCVideoRenderer();
      await renderer.initialize();

      final pendingForSender = <rtc.RTCIceCandidate>[];
      final pendingForReceiver = <rtc.RTCIceCandidate>[];
      var senderRemoteDescriptionReady = false;
      var receiverRemoteDescriptionReady = false;
      senderPeer.onIceCandidate = (candidate) {
        if (receiverRemoteDescriptionReady) {
          unawaited(receiverPeer.addCandidate(candidate));
        } else {
          pendingForReceiver.add(candidate);
        }
      };
      receiverPeer.onIceCandidate = (candidate) {
        if (senderRemoteDescriptionReady) {
          unawaited(senderPeer.addCandidate(candidate));
        } else {
          pendingForSender.add(candidate);
        }
      };

      final remoteTrack = Completer<rtc.MediaStreamTrack>();
      receiverPeer.onTrack = (event) {
        if (event.streams.isNotEmpty) {
          renderer.srcObject = event.streams.first;
        }
        if (!remoteTrack.isCompleted) remoteTrack.complete(event.track);
      };

      Future<rtc.MediaStream> capture({
        required int width,
        required int height,
        required int fps,
      }) async {
        final stream = await rtc.navigator.mediaDevices
            .getDisplayMedia(<String, dynamic>{
              'audio': false,
              'video': <String, dynamic>{
                'width': width,
                'height': height,
                'frameRate': fps,
                'deviceId': <String, dynamic>{'exact': source.id},
                'mandatory': <String, dynamic>{'frameRate': fps.toDouble()},
              },
            })
            .timeout(const Duration(seconds: 10));
        streams.add(stream);
        return stream;
      }

      Future<void> waitForRemoteProfile({
        required int width,
        required int height,
      }) async {
        final deadline = DateTime.now().add(const Duration(seconds: 10));
        while (DateTime.now().isBefore(deadline)) {
          await Future<void>.delayed(const Duration(milliseconds: 50));
          await tester.pump();
          final renderedWidth = renderer.videoWidth;
          final renderedHeight = renderer.videoHeight;
          if (renderedWidth > 0 &&
              renderedWidth <= width &&
              (renderedHeight - height).abs() <= 2) {
            debugPrint(
              'SCREEN_QUALITY_HOT_SWITCH expected=${width}x$height '
              'received=${renderedWidth}x$renderedHeight',
            );
            return;
          }
        }
        fail(
          'Remote frame never reached ${width}x$height; '
          'last=${renderer.videoWidth}x${renderer.videoHeight}',
        );
      }

      try {
        final initialStream = await capture(width: 1280, height: 720, fps: 30);
        final sender = await senderPeer.addTrack(
          initialStream.getVideoTracks().single,
          initialStream,
        );

        final offer = await senderPeer.createOffer();
        await senderPeer.setLocalDescription(offer);
        await receiverPeer.setRemoteDescription(offer);
        receiverRemoteDescriptionReady = true;
        for (final candidate in pendingForReceiver) {
          await receiverPeer.addCandidate(candidate);
        }
        pendingForReceiver.clear();

        final answer = await receiverPeer.createAnswer();
        await receiverPeer.setLocalDescription(answer);
        await senderPeer.setRemoteDescription(answer);
        senderRemoteDescriptionReady = true;
        for (final candidate in pendingForSender) {
          await senderPeer.addCandidate(candidate);
        }
        pendingForSender.clear();

        final senderParameters = sender.parameters;
        senderParameters.degradationPreference =
            rtc.RTCDegradationPreference.MAINTAIN_RESOLUTION;
        for (final encoding in senderParameters.encodings ?? const []) {
          encoding.scaleResolutionDownBy = 1.0;
          encoding.minBitrate = 2 * 1000 * 1000;
          encoding.maxBitrate = 8 * 1000 * 1000;
          encoding.maxFramerate = 30;
          encoding.priority = rtc.RTCPriorityType.high;
          encoding.networkPriority = rtc.RTCPriorityType.high;
        }
        expect(await sender.setParameters(senderParameters), isTrue);

        await remoteTrack.future.timeout(const Duration(seconds: 10));
        await tester.pumpWidget(
          Directionality(
            textDirection: TextDirection.ltr,
            child: SizedBox.expand(child: rtc.RTCVideoView(renderer)),
          ),
        );
        await waitForRemoteProfile(width: 1280, height: 720);

        final lowStream = await capture(width: 854, height: 480, fps: 15);
        await sender.replaceTrack(lowStream.getVideoTracks().single);
        await waitForRemoteProfile(width: 854, height: 480);

        final highStream = await capture(width: 1920, height: 1080, fps: 30);
        await sender.replaceTrack(highStream.getVideoTracks().single);
        await waitForRemoteProfile(width: 1920, height: 1080);
      } finally {
        renderer.srcObject = null;
        await senderPeer.close();
        await receiverPeer.close();
        await senderPeer.dispose();
        await receiverPeer.dispose();
        for (final stream in streams) {
          for (final track in stream.getTracks()) {
            await track.stop();
          }
          await stream.dispose();
        }
        await renderer.dispose();
        await tester.pumpWidget(const SizedBox.shrink());
      }
    },
    skip: !Platform.isWindows,
    timeout: const Timeout(Duration(minutes: 3)),
  );
}
