import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/audio_device_store.dart';
import 'package:client/src/app/live_session_controller.dart';
import 'package:client/src/live/live_session.dart';
import 'package:client/src/protocol/models.dart';

void main() {
  test(
    'connectWithRetry restores audio settings and retries LiveKit connect',
    () async {
      final session = _FakeLiveSession(failFirstConnect: true);
      var restoredDevices = 0;
      final controller = LiveSessionController(
        apiBaseUrl: 'https://api.example.test/api/v1',
        session: session,
        audioDeviceStore: const _FakeAudioDeviceStore(),
        audioDeviceRestorer: (_) async {
          restoredDevices += 1;
          return null;
        },
      );

      await controller.connectWithRetry(_liveJoinResult);

      expect(session.connectAttempts, 2);
      expect(session.disconnects, 1);
      expect(session.inputVolumes, [0.35, 0.35]);
      expect(session.outputVolumes, [0.75, 0.75]);
      expect(session.screenShareVolumes, [0.5, 0.5]);
      expect(restoredDevices, 2);
      expect(session.connectedUrl, 'wss://live.example.test');
      expect(session.connectedRoomName, 'room_live_1');
      expect(session.connectedMicMuted, isTrue);
    },
  );

  test('disconnect respects timeout when LiveKit teardown hangs', () async {
    final session = _FakeLiveSession(hangDisconnect: true);
    final controller = LiveSessionController(
      apiBaseUrl: 'https://api.example.test/api/v1',
      session: session,
      audioDeviceStore: const _FakeAudioDeviceStore(),
      audioDeviceRestorer: (_) async => null,
    );

    await controller.disconnect(timeout: const Duration(milliseconds: 1));

    expect(session.disconnects, 1);
  });

  test('live session callbacks attach through controller boundary', () {
    final session = _FakeLiveSession();
    final controller = LiveSessionController(
      apiBaseUrl: 'https://api.example.test/api/v1',
      session: session,
      audioDeviceStore: const _FakeAudioDeviceStore(),
      audioDeviceRestorer: (_) async => null,
    );
    var changes = 0;
    var removals = 0;
    var unexpectedDisconnects = 0;
    final joins = <String>[];
    final leaves = <String>[];
    final publishPermissions = <bool>[];
    void onChanged() => changes += 1;

    controller.attachSessionCallbacks(
      onChanged: onChanged,
      onForciblyRemoved: () => removals += 1,
      onUnexpectedlyDisconnected: () => unexpectedDisconnects += 1,
      onPublishPermissionChanged: publishPermissions.add,
      onParticipantJoined: joins.add,
      onParticipantLeft: (identity, kind) =>
          leaves.add('$identity:${kind.name}'),
    );
    session.emitChange();
    session.onForciblyRemoved?.call();
    session.onUnexpectedlyDisconnected?.call();
    session.onPublishPermissionChanged?.call(false);
    session.onParticipantJoined?.call('user-2');
    session.onParticipantLeft?.call(
      'user-3',
      LiveParticipantDepartureKind.removed,
    );

    expect(changes, 1);
    expect(removals, 1);
    expect(unexpectedDisconnects, 1);
    expect(publishPermissions, [false]);
    expect(joins, ['user-2']);
    expect(leaves, ['user-3:removed']);

    controller.detachSessionCallbacks(onChanged: onChanged);
    session.emitChange();
    expect(changes, 1);
    expect(session.onForciblyRemoved, isNull);
    expect(session.onUnexpectedlyDisconnected, isNull);
    expect(session.onPublishPermissionChanged, isNull);
    expect(session.onParticipantJoined, isNull);
    expect(session.onParticipantLeft, isNull);
  });

  test('live session media controls proxy through controller', () async {
    final session = _FakeLiveSession();
    final controller = LiveSessionController(
      apiBaseUrl: 'https://api.example.test/api/v1',
      session: session,
      audioDeviceStore: const _FakeAudioDeviceStore(),
      audioDeviceRestorer: (_) async => null,
    );

    await controller.setMicMuted(true);
    await controller.setCameraEnabled(true);
    await controller.flipCamera();
    await controller.setScreenShareEnabled(true, sourceId: 'screen_1');
    await controller.setOutputMuted(true);
    await controller.setInputVolume(0.4);
    await controller.setOutputVolume(0.6);
    await controller.setParticipantVoiceVolume('user_2', 0.25);
    await controller.setScreenShareVolume(0.8);

    expect(session.micMutes, [true]);
    expect(session.cameraEnables, [true]);
    expect(session.cameraFlips, 1);
    expect(session.screenShareEnables, [true]);
    expect(session.screenShareSourceIds, ['screen_1']);
    expect(session.outputMutes, [true]);
    expect(session.inputVolumes, [0.4]);
    expect(session.outputVolumes, [0.6]);
    expect(session.participantVoiceVolumeWrites, ['user_2:0.25']);
    expect(controller.participantVoiceVolume('user_2'), 0.25);
    expect(session.screenShareVolumes, [0.8]);
  });

  test(
    'live session volume controls persist through the local store',
    () async {
      final session = _FakeLiveSession();
      final store = _RecordingAudioDeviceStore();
      final controller = LiveSessionController(
        apiBaseUrl: 'https://api.example.test/api/v1',
        session: session,
        audioDeviceStore: store,
        audioDeviceRestorer: (_) async => null,
      );

      await controller.setInputVolume(0.4);
      await controller.setOutputVolume(0.6);
      await controller.setParticipantVoiceVolume('user_2', 1.5);
      await controller.setScreenShareVolume(0.8);

      expect(store.inputVolumeWrites, [0.4]);
      expect(store.outputVolumeWrites, [0.6]);
      expect(store.participantVoiceVolumeWrites, ['user_2:1.50']);
      expect(store.screenShareVolumeWrites, [0.8]);
    },
  );

  test(
    'session mute gain does not replace stored volume preferences',
    () async {
      final session = _FakeLiveSession();
      final store = _RecordingAudioDeviceStore();
      final controller = LiveSessionController(
        apiBaseUrl: 'https://api.example.test/api/v1',
        session: session,
        audioDeviceStore: store,
        audioDeviceRestorer: (_) async => null,
      );

      await controller.setTransientInputVolume(0);
      await controller.setTransientOutputVolume(0);

      expect(session.inputVolumes, [0]);
      expect(session.outputVolumes, [0]);
      expect(store.inputVolumeWrites, isEmpty);
      expect(store.outputVolumeWrites, isEmpty);
    },
  );

  test('fresh open session repairs legacy persisted mute gain', () async {
    final session = _FakeLiveSession();
    final store = _RecordingAudioDeviceStore(
      storedInputVolume: 0,
      storedOutputVolume: 0,
    );
    final controller = LiveSessionController(
      apiBaseUrl: 'https://api.example.test/api/v1',
      session: session,
      audioDeviceStore: store,
      audioDeviceRestorer: (_) async => null,
    );

    await controller.restoreStoredAudioSettings();

    expect(session.inputVolumes, [defaultAudioVolume]);
    expect(session.outputVolumes, [defaultAudioVolume]);
    expect(store.inputVolumeWrites, [defaultAudioVolume]);
    expect(store.outputVolumeWrites, [defaultAudioVolume]);
  });

  test('explicitly muted join keeps an intentional zero gain', () async {
    final session = _FakeLiveSession();
    final store = _RecordingAudioDeviceStore(
      storedInputVolume: 0,
      storedOutputVolume: 0,
    );
    final controller = LiveSessionController(
      apiBaseUrl: 'https://api.example.test/api/v1',
      session: session,
      audioDeviceStore: store,
      audioDeviceRestorer: (_) async => null,
    );

    await controller.restoreStoredAudioSettings(
      micMuted: true,
      headphonesMuted: true,
    );

    expect(session.inputVolumes, [0]);
    expect(session.outputVolumes, [0]);
    expect(store.inputVolumeWrites, isEmpty);
    expect(store.outputVolumeWrites, isEmpty);
  });

  test('participant voice volume restores and toggles local mute', () async {
    final session = _FakeLiveSession();
    final store = _RecordingAudioDeviceStore(
      storedParticipantVoiceVolumes: {'user_2': 1.5},
    );
    final controller = LiveSessionController(
      apiBaseUrl: 'https://api.example.test/api/v1',
      session: session,
      audioDeviceStore: store,
      audioDeviceRestorer: (_) async => null,
    );

    expect(await controller.restoreParticipantVoiceVolume('user_2'), isTrue);
    expect(controller.participantVoiceVolume('user_2'), 1.5);

    await controller.toggleParticipantVoiceMuted('user_2');
    expect(controller.participantVoiceVolume('user_2'), 0);

    await controller.toggleParticipantVoiceMuted('user_2');
    expect(controller.participantVoiceVolume('user_2'), 1.5);
    expect(session.participantVoiceVolumeWrites, [
      'user_2:1.50',
      'user_2:0.00',
      'user_2:1.50',
    ]);
    expect(store.participantVoiceVolumeWrites, ['user_2:0.00', 'user_2:1.50']);
  });

  test('setScreenShareMaxHeight applies to the session and persists', () async {
    final session = _FakeLiveSession();
    final store = _RecordingAudioDeviceStore();
    final controller = LiveSessionController(
      apiBaseUrl: 'https://api.example.test/api/v1',
      session: session,
      audioDeviceStore: store,
      audioDeviceRestorer: (_) async => null,
    );

    await controller.setScreenShareMaxHeight(720);

    expect(session.screenShareMaxHeights, [720]);
    expect(store.screenShareWrites, [720]);
  });

  test('connectWithRetry restores the stored screen-share height', () async {
    final session = _FakeLiveSession();
    final store = _RecordingAudioDeviceStore(storedScreenShareMaxHeight: 480);
    final controller = LiveSessionController(
      apiBaseUrl: 'https://api.example.test/api/v1',
      session: session,
      audioDeviceStore: store,
      audioDeviceRestorer: (_) async => null,
    );

    await controller.connectWithRetry(_liveJoinResult);

    expect(session.screenShareMaxHeights, [480]);
  });
}

class _FakeAudioDeviceStore extends AudioDeviceStore {
  const _FakeAudioDeviceStore();

  @override
  Future<StoredAudioDevices> read() async {
    return const StoredAudioDevices(inputVolume: 0.35, outputVolume: 0.75);
  }
}

class _FakeLiveSession extends LiveSession {
  _FakeLiveSession({
    this.failFirstConnect = false,
    this.hangDisconnect = false,
  });

  final bool failFirstConnect;
  final bool hangDisconnect;
  int connectAttempts = 0;
  int disconnects = 0;
  final inputVolumes = <double>[];
  final outputVolumes = <double>[];
  final participantVoiceVolumeWrites = <String>[];
  final screenShareVolumes = <double>[];
  final outputMutes = <bool>[];
  final micMutes = <bool>[];
  final cameraEnables = <bool>[];
  int cameraFlips = 0;
  final screenShareEnables = <bool>[];
  final screenShareSourceIds = <String?>[];
  String? connectedUrl;
  String? connectedRoomName;
  bool? connectedMicMuted;
  double _inputVolume = defaultAudioVolume;
  double _outputVolume = defaultAudioVolume;
  double _screenShareVolume = defaultAudioVolume;
  final _participantVoiceVolumes = <String, double>{};

  void emitChange() => notifyListeners();

  @override
  double get inputVolume => _inputVolume;

  @override
  double get outputVolume => _outputVolume;

  @override
  double participantVoiceVolume(String userId) {
    return _participantVoiceVolumes[userId] ?? 1.0;
  }

  @override
  double get screenShareVolume => _screenShareVolume;

  @override
  Future<void> connect({
    required String url,
    required String token,
    required String roomName,
    required bool micMuted,
  }) async {
    connectAttempts += 1;
    if (failFirstConnect && connectAttempts == 1) {
      throw StateError('first connect failed');
    }
    connectedUrl = url;
    connectedRoomName = roomName;
    connectedMicMuted = micMuted;
  }

  @override
  Future<void> disconnect() {
    disconnects += 1;
    if (hangDisconnect) return Completer<void>().future;
    return Future<void>.value();
  }

  @override
  Future<void> setInputVolume(double volume) async {
    _inputVolume = volume;
    inputVolumes.add(volume);
  }

  @override
  Future<void> setOutputVolume(double volume) async {
    _outputVolume = volume;
    outputVolumes.add(volume);
  }

  @override
  Future<void> setParticipantVoiceVolume(String userId, double volume) async {
    _participantVoiceVolumes[userId] = volume;
    participantVoiceVolumeWrites.add('$userId:${volume.toStringAsFixed(2)}');
  }

  @override
  Future<void> setScreenShareVolume(double volume) async {
    _screenShareVolume = volume;
    screenShareVolumes.add(volume);
  }

  @override
  Future<void> setOutputMuted(bool muted) async {
    outputMutes.add(muted);
  }

  @override
  Future<void> setMicMuted(bool muted) async {
    micMutes.add(muted);
  }

  @override
  Future<bool> setCameraEnabled(bool enabled) async {
    cameraEnables.add(enabled);
    return enabled;
  }

  @override
  Future<bool> flipCamera() async {
    cameraFlips += 1;
    return true;
  }

  @override
  Future<bool> setScreenShareEnabled(bool enabled, {String? sourceId}) async {
    screenShareEnables.add(enabled);
    screenShareSourceIds.add(sourceId);
    return enabled;
  }

  final screenShareMaxHeights = <int>[];

  @override
  Future<void> setScreenShareMaxHeight(int height) async {
    screenShareMaxHeights.add(height);
  }
}

class _RecordingAudioDeviceStore extends AudioDeviceStore {
  _RecordingAudioDeviceStore({
    this.storedScreenShareMaxHeight = 1080,
    this.storedInputVolume = 0.35,
    this.storedOutputVolume = 0.75,
    Map<String, double> storedParticipantVoiceVolumes = const {},
  }) : _storedParticipantVoiceVolumes = Map<String, double>.from(
         storedParticipantVoiceVolumes,
       );

  final int storedScreenShareMaxHeight;
  final double storedInputVolume;
  final double storedOutputVolume;
  final Map<String, double> _storedParticipantVoiceVolumes;
  final inputVolumeWrites = <double>[];
  final outputVolumeWrites = <double>[];
  final participantVoiceVolumeWrites = <String>[];
  final screenShareVolumeWrites = <double>[];
  final screenShareWrites = <int>[];

  @override
  Future<StoredAudioDevices> read() async {
    return StoredAudioDevices(
      inputVolume: storedInputVolume,
      outputVolume: storedOutputVolume,
      screenShareVolume: 1.0,
      screenShareMaxHeight: storedScreenShareMaxHeight,
    );
  }

  @override
  Future<void> writeInputVolume(double volume) async {
    inputVolumeWrites.add(volume);
  }

  @override
  Future<void> writeOutputVolume(double volume) async {
    outputVolumeWrites.add(volume);
  }

  @override
  Future<double> readParticipantVoiceVolume(String userId) async {
    return _storedParticipantVoiceVolumes[userId] ?? 1.0;
  }

  @override
  Future<void> writeParticipantVoiceVolume(String userId, double volume) async {
    _storedParticipantVoiceVolumes[userId] = volume;
    participantVoiceVolumeWrites.add('$userId:${volume.toStringAsFixed(2)}');
  }

  @override
  Future<void> writeScreenShareVolume(double volume) async {
    screenShareVolumeWrites.add(volume);
  }

  @override
  Future<void> writeScreenShareMaxHeight(int height) async {
    screenShareWrites.add(height);
  }
}

const _sender = UserSummary(
  id: 'user_1',
  username: 'alice',
  displayName: 'Alice',
  avatarUrl: null,
  defaultAvatarKey: 'blue-3',
);

final _participant = LiveParticipant(
  liveSessionId: 'live_1',
  user: _sender,
  joinedAt: DateTime.utc(2026, 6, 1),
  micMuted: true,
  headphonesMuted: false,
  voiceBlocked: false,
  cameraOn: false,
  screenSharing: false,
  connectionState: 'joined',
);

final _liveJoinResult = LiveJoinResult(
  liveKit: LiveKitConnectionInfo(
    serverUrl: 'wss://live.example.test',
    token: 'token',
    tokenExpiresAt: DateTime.utc(2026, 6, 1, 1),
    roomName: 'room_live_1',
  ),
  participant: _participant,
  live: LiveState(
    roomId: 'room_1',
    participantCount: 1,
    participants: [_participant],
    updatedAt: DateTime.utc(2026, 6, 1),
  ),
);
