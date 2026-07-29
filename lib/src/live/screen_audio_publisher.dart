import 'dart:async';
import 'dart:io' show HttpOverrides;

import 'package:flutter/foundation.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart' as rtc;
import 'package:livekit_client/livekit_client.dart' as lk;

import '../protocol/models.dart';

/// Fetches a publish-only LiveKit token for the hidden screen-audio aux
/// participant. Implemented by the app layer (wired to `GangApi`), keeping
/// this class decoupled from the API client.
typedef ScreenAudioTokenProvider =
    Future<ScreenAudioToken> Function(String roomId);

@visibleForTesting
abstract interface class ScreenAudioPublisherBackend {
  Future<void> connect({required String liveKitUrl, required String token});

  Future<void> publish();

  Future<void> stop();
}

@visibleForTesting
typedef ScreenAudioPublisherBackendFactory =
    ScreenAudioPublisherBackend Function();

/// Publishes screen-share audio as an independent `TrackSource.screenShareAudio`
/// track through a *second* LiveKit Room whose PeerConnections live on an
/// isolated WebRTC factory.
///
/// The isolated factory is fully separate from the primary microphone factory:
/// macOS feeds it through `FlutterScreenAudioDevice`/ScreenCaptureKit, while
/// Windows creates the screen-audio track on a second native factory and feeds
/// it from WASAPI loopback. This removes the shared `AudioState` that would
/// otherwise fan mic capture into the screen-audio send stream and race its
/// capture checker (the fatal `audio_send_stream.cc:393` `RTC_CHECK`).
///
/// The aux participant joins with identity `<ownerId>--screen-audio`, is
/// publish-only (`canSubscribe=false`), and never appears in the roster (no
/// `live_participants` row is created for it). Receivers merge its audio into
/// the owner's `screenShareVolume` via the existing `_applyScreenShareVolume`
/// logic, which iterates all `screenShareAudio` publications across all remote
/// participants.
class ScreenAudioPublisher {
  ScreenAudioPublisher({
    required this.tokenProvider,
    @visibleForTesting ScreenAudioPublisherBackendFactory? backendFactory,
  }) : _backendFactory =
           backendFactory ?? _LiveKitScreenAudioPublisherBackend.new;

  final ScreenAudioTokenProvider tokenProvider;
  final ScreenAudioPublisherBackendFactory _backendFactory;

  ScreenAudioPublisherBackend? _backend;
  Future<void>? _startOperation;
  bool _publishing = false;

  bool get isPublishing => _publishing;

  /// Connects the aux room and publishes the screen-audio track.
  ///
  /// [liveKitUrl] is the resolved LiveKit server URL (same as the main room).
  /// [roomName] is the LiveKit room name (== roomID). The aux token is fetched
  /// on demand so it is always fresh regardless of how long the user has been
  /// in the call.
  Future<void> start({required String liveKitUrl, required String roomName}) {
    return _startOperation ??= _runStart(
      liveKitUrl: liveKitUrl,
      roomName: roomName,
    );
  }

  Future<void> _runStart({
    required String liveKitUrl,
    required String roomName,
  }) async {
    debugPrint(
      'screen-audio: starting publisher (url=$liveKitUrl, room=$roomName)',
    );
    final tokenResult = await tokenProvider(roomName);
    debugPrint(
      'screen-audio: token acquired (identity=${tokenResult.identity})',
    );

    final backend = _backendFactory();
    _backend = backend;
    try {
      debugPrint('screen-audio: connecting aux room...');
      await backend.connect(liveKitUrl: liveKitUrl, token: tokenResult.token);
      debugPrint('screen-audio: aux room connected');

      debugPrint('screen-audio: publishing track...');
      await backend.publish();
      _publishing = true;
      debugPrint('screen-audio: track published successfully');
    } catch (_) {
      if (identical(_backend, backend)) {
        _backend = null;
      }
      _publishing = false;
      await backend.stop();
      rethrow;
    }
  }

  /// Waits for any in-flight start before releasing the aux room and track.
  ///
  /// Waiting is intentional: leaving the voice channel can race token fetch,
  /// room connection, or track publication. Returning early would allow that
  /// background start to recreate a publisher after teardown and poison the
  /// next screen-share attempt.
  Future<void> stop() async {
    final startOperation = _startOperation;
    if (startOperation != null) {
      try {
        await startOperation;
      } catch (_) {
        // A failed start already performs its own best-effort cleanup.
      }
    }

    final backend = _backend;
    _backend = null;
    _startOperation = null;
    _publishing = false;
    if (backend != null) {
      await backend.stop();
    }
  }
}

class _LiveKitScreenAudioPublisherBackend
    implements ScreenAudioPublisherBackend {
  lk.Room? _room;
  Future<void> Function()? _stopTrack;

  @override
  Future<void> connect({
    required String liveKitUrl,
    required String token,
  }) async {
    final engine = lk.Engine(
      connectOptions: const lk.ConnectOptions(),
      roomOptions: const lk.RoomOptions(),
      peerConnectionCreate: _createScreenAudioPeerConnection,
    );
    final room = lk.Room(engine: engine);
    _room = room;
    await HttpOverrides.runZoned(
      () => room.connect(liveKitUrl, token),
      findProxyFromEnvironment: (uri, environment) => 'DIRECT',
    );
  }

  @override
  Future<void> publish() async {
    final local = _room?.localParticipant;
    if (local == null) {
      throw StateError(
        'screen-audio aux room connected without a local participant',
      );
    }

    // Create the audio track on the isolated factory. Its audio is pulled from
    // FlutterScreenAudioDevice (the second factory's ADM), which the
    // ScreenCaptureKit capturer feeds via enqueueSampleBuffer:.
    debugPrint('screen-audio: creating track on factory-2...');
    final stream = await rtc.createScreenAudioTrack();
    debugPrint('screen-audio: track created');
    // ignore: invalid_use_of_internal_member
    final track = lk.LocalAudioTrack(
      lk.TrackSource.screenShareAudio,
      stream,
      stream.getAudioTracks().first,
      const lk.AudioCaptureOptions(),
    );
    _stopTrack = () async {
      try {
        await track.stop();
      } catch (_) {}
      try {
        await track.dispose();
      } catch (_) {}
    };
    await local.publishAudioTrack(track);
  }

  @override
  Future<void> stop() async {
    final room = _room;
    final stopTrack = _stopTrack;
    _room = null;
    _stopTrack = null;

    if (room != null) {
      try {
        await room.disconnect();
      } catch (_) {}
    }
    if (stopTrack != null) {
      await stopTrack();
    }
    if (room != null) {
      try {
        await room.dispose();
      } catch (_) {}
    }
  }
}

Future<rtc.RTCPeerConnection> _createScreenAudioPeerConnection(
  Map<String, dynamic> configuration, [
  Map<String, dynamic>? constraints,
]) {
  return rtc.createScreenAudioPeerConnection(
    configuration,
    constraints ?? const {},
  );
}
