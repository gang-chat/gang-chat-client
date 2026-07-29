import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/live/screen_audio_publisher.dart';
import 'package:client/src/protocol/models.dart';

void main() {
  test('stop waits when token acquisition is still in flight', () async {
    final tokenRequested = Completer<void>();
    final releaseToken = Completer<void>();
    final events = <String>[];
    final backend = _FakeScreenAudioPublisherBackend(
      events: events,
      onPublish: () async {},
    );
    final publisher = ScreenAudioPublisher(
      tokenProvider: (roomId) async {
        tokenRequested.complete();
        await releaseToken.future;
        return _screenAudioToken(roomId);
      },
      backendFactory: () => backend,
    );

    final start = publisher.start(
      liveKitUrl: 'wss://live.example.test',
      roomName: 'room-1',
    );
    await tokenRequested.future;

    var stopCompleted = false;
    final stop = publisher.stop().then((_) => stopCompleted = true);
    await Future<void>.delayed(Duration.zero);

    expect(stopCompleted, isFalse);
    expect(events, isEmpty);

    releaseToken.complete();
    await Future.wait([start, stop]);

    expect(events, ['connect', 'publish', 'stop']);
    expect(backend.stopCount, 1);
    expect(publisher.isPublishing, isFalse);
  });

  test(
    'stop waits for an in-flight publish before releasing the backend',
    () async {
      final publishStarted = Completer<void>();
      final releasePublish = Completer<void>();
      final events = <String>[];
      final backend = _FakeScreenAudioPublisherBackend(
        events: events,
        onPublish: () async {
          publishStarted.complete();
          await releasePublish.future;
        },
      );
      final publisher = ScreenAudioPublisher(
        tokenProvider: _tokenProvider,
        backendFactory: () => backend,
      );

      final start = publisher.start(
        liveKitUrl: 'wss://live.example.test',
        roomName: 'room-1',
      );
      await publishStarted.future;

      var stopCompleted = false;
      final stop = publisher.stop().then((_) => stopCompleted = true);
      await Future<void>.delayed(Duration.zero);

      expect(stopCompleted, isFalse);
      expect(events, ['connect', 'publish']);

      releasePublish.complete();
      await Future.wait([start, stop]);

      expect(events, ['connect', 'publish', 'stop']);
      expect(backend.stopCount, 1);
      expect(publisher.isPublishing, isFalse);
    },
  );

  test('a failed publish releases the connected backend', () async {
    final events = <String>[];
    final backend = _FakeScreenAudioPublisherBackend(
      events: events,
      onPublish: () async => throw StateError('publish failed'),
    );
    final publisher = ScreenAudioPublisher(
      tokenProvider: _tokenProvider,
      backendFactory: () => backend,
    );

    await expectLater(
      publisher.start(
        liveKitUrl: 'wss://live.example.test',
        roomName: 'room-1',
      ),
      throwsStateError,
    );

    expect(events, ['connect', 'publish', 'stop']);
    expect(backend.stopCount, 1);
    expect(publisher.isPublishing, isFalse);

    await publisher.stop();
    expect(backend.stopCount, 1);
  });
}

Future<ScreenAudioToken> _tokenProvider(String roomId) async {
  return _screenAudioToken(roomId);
}

ScreenAudioToken _screenAudioToken(String roomId) {
  return ScreenAudioToken(
    serverUrl: 'wss://live.example.test',
    token: 'screen-audio-token',
    tokenExpiresAt: DateTime.utc(2026, 7, 29, 1),
    roomName: roomId,
    identity: 'user-1--screen-audio',
  );
}

class _FakeScreenAudioPublisherBackend implements ScreenAudioPublisherBackend {
  _FakeScreenAudioPublisherBackend({
    required this.events,
    required this.onPublish,
  });

  final List<String> events;
  final Future<void> Function() onPublish;
  int stopCount = 0;

  @override
  Future<void> connect({
    required String liveKitUrl,
    required String token,
  }) async {
    events.add('connect');
  }

  @override
  Future<void> publish() async {
    events.add('publish');
    await onPublish();
  }

  @override
  Future<void> stop() async {
    stopCount++;
    events.add('stop');
  }
}
