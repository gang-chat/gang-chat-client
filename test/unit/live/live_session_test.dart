import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart' as rtc;
import 'package:livekit_client/livekit_client.dart' as lk;

import 'package:client/src/live/audio_device_rebinder.dart';
import 'package:client/src/live/audio_input_rebinder.dart';
import 'package:client/src/live/audio_output_rebinder.dart';
import 'package:client/src/live/camera_device_reconciler.dart';
import 'package:client/src/live/live_session.dart';

void main() {
  test('camera flip capability distinguishes unknown and single-camera', () {
    expect(cameraFlipAvailabilityFromVideoInputCount(0), isNull);
    expect(cameraFlipAvailabilityFromVideoInputCount(1), isFalse);
    expect(cameraFlipAvailabilityFromVideoInputCount(2), isTrue);
  });

  test('Android music box switches playback to media mode and volume', () {
    final media = androidLiveAudioConfiguration(musicBoxActive: true);
    expect(media.androidAudioMode, rtc.AndroidAudioMode.normal);
    expect(media.androidAudioStreamType, rtc.AndroidAudioStreamType.music);
    expect(
      media.androidAudioAttributesUsageType,
      rtc.AndroidAudioAttributesUsageType.media,
    );
    expect(
      media.androidAudioAttributesContentType,
      rtc.AndroidAudioAttributesContentType.music,
    );
    expect(media.forceHandleAudioRouting, isTrue);

    final voice = androidLiveAudioConfiguration(musicBoxActive: false);
    expect(voice.androidAudioMode, rtc.AndroidAudioMode.inCommunication);
    expect(voice.androidAudioStreamType, rtc.AndroidAudioStreamType.voiceCall);
    expect(
      voice.androidAudioAttributesUsageType,
      rtc.AndroidAudioAttributesUsageType.voiceCommunication,
    );
  });

  test('participant removal maps to the kicked announcement kind', () {
    expect(
      liveParticipantDepartureKind(lk.DisconnectReason.participantRemoved),
      LiveParticipantDepartureKind.removed,
    );
    expect(
      liveParticipantDepartureKind(lk.DisconnectReason.clientInitiated),
      LiveParticipantDepartureKind.left,
    );
    expect(
      liveParticipantDepartureKind(null),
      LiveParticipantDepartureKind.left,
    );
  });

  test(
    'missing microphone publication is unknown during participant reconnect',
    () {
      expect(
        liveParticipantMicPublicationMuted(
          _FakeLiveParticipant(microphonePublication: null),
        ),
        isNull,
      );
      expect(
        liveParticipantMicPublicationMuted(
          _FakeLiveParticipant(
            microphonePublication: _FakeTrackPublication(muted: true),
          ),
        ),
        isTrue,
      );
      expect(
        liveParticipantMicPublicationMuted(
          _FakeLiveParticipant(
            microphonePublication: _FakeTrackPublication(muted: false),
          ),
        ),
        isFalse,
      );
    },
  );

  test('media reconnect clears stale speaking highlights immediately', () {
    final session = LiveSession(
      inputRebinderFactory: (_) => null,
      outputRebinderFactory: (_) => null,
    );
    addTearDown(session.dispose);
    final participant = _FakeLiveParticipant(
      identity: 'speaker',
      microphonePublication: null,
    );

    session.debugHandleRoomEvent(
      lk.ActiveSpeakersChangedEvent(speakers: [participant]),
    );
    expect(session.speakingIdentities, {'speaker'});

    session.debugHandleRoomEvent(const lk.RoomReconnectingEvent());

    expect(session.speakingIdentities, isEmpty);
    expect(session.micMutedByIdentity, isEmpty);
  });

  test('only recoverable room disconnects request an app-level reconnect', () {
    final session = LiveSession(
      inputRebinderFactory: (_) => null,
      outputRebinderFactory: (_) => null,
    );
    addTearDown(session.dispose);
    var reconnects = 0;
    var removals = 0;
    session.onUnexpectedlyDisconnected = () => reconnects += 1;
    session.onForciblyRemoved = () => removals += 1;

    session.debugHandleRoomEvent(
      lk.RoomDisconnectedEvent(
        reason: lk.DisconnectReason.reconnectAttemptsExceeded,
      ),
    );
    session.debugHandleRoomEvent(
      lk.RoomDisconnectedEvent(reason: lk.DisconnectReason.duplicateIdentity),
    );
    session.debugHandleRoomEvent(
      lk.RoomDisconnectedEvent(reason: lk.DisconnectReason.participantRemoved),
    );

    expect(reconnects, 1);
    expect(removals, 1);
  });

  test('presence cues ignore hidden audio participants', () {
    expect(isLivePresenceSoundParticipantIdentity('user-2'), isTrue);
    expect(
      isLivePresenceSoundParticipantIdentity('user-2--screen-audio'),
      isFalse,
    );
    expect(isLivePresenceSoundParticipantIdentity('__musicbox__'), isFalse);
    expect(isLivePresenceSoundParticipantIdentity(''), isFalse);
  });

  test('reconnect presence diff ignores users restored on both sides', () {
    final changes = liveReconnectPresenceChanges(
      before: {'stayed', 'left'},
      after: {'stayed', 'joined'},
    );

    expect(changes.left, {'left'});
    expect(changes.joined, {'joined'});
  });

  test('reconnect republishes only an established permitted microphone', () {
    expect(
      shouldRepublishLocalMicrophoneAfterReconnect(
        resumedSignalConnection: true,
        hadPublicationBeforeReconnect: true,
        canPublish: true,
      ),
      isTrue,
    );
    expect(
      shouldRepublishLocalMicrophoneAfterReconnect(
        resumedSignalConnection: true,
        hadPublicationBeforeReconnect: false,
        canPublish: true,
      ),
      isFalse,
    );
    expect(
      shouldRepublishLocalMicrophoneAfterReconnect(
        resumedSignalConnection: true,
        hadPublicationBeforeReconnect: true,
        canPublish: false,
      ),
      isFalse,
    );
    expect(
      shouldRepublishLocalMicrophoneAfterReconnect(
        resumedSignalConnection: false,
        hadPublicationBeforeReconnect: true,
        canPublish: true,
      ),
      isFalse,
    );
  });

  test(
    'reconnect microphone recovery retries after native teardown races',
    () async {
      var attempts = 0;
      final waits = <Duration>[];

      final recovered = await retryLiveReconnectMicrophoneRecovery(
        attempt: () async {
          attempts += 1;
          return attempts == 3;
        },
        isCurrent: () => true,
        retryDelays: const [
          Duration.zero,
          Duration(milliseconds: 10),
          Duration(milliseconds: 20),
        ],
        wait: (delay) async => waits.add(delay),
      );

      expect(recovered, isTrue);
      expect(attempts, 3);
      expect(waits, const [
        Duration(milliseconds: 10),
        Duration(milliseconds: 20),
      ]);
    },
  );

  test(
    'reconnect microphone recovery stops when its room is superseded',
    () async {
      var current = true;
      var attempts = 0;

      final recovered = await retryLiveReconnectMicrophoneRecovery(
        attempt: () async {
          attempts += 1;
          current = false;
          return false;
        },
        isCurrent: () => current,
        retryDelays: const [Duration.zero, Duration.zero],
      );

      expect(recovered, isFalse);
      expect(attempts, 1);
    },
  );

  test('latest subscription reconciliation runs last', () async {
    final reconciler = LatestLiveSubscriptionReconciler();
    final firstStarted = Completer<void>();
    final releaseFirst = Completer<void>();
    final applied = <String>[];

    final first = reconciler.schedule((isCurrent) async {
      firstStarted.complete();
      await releaseFirst.future;
      if (isCurrent()) applied.add('first');
    });
    await firstStarted.future;

    final second = reconciler.schedule((isCurrent) async {
      if (isCurrent()) applied.add('second');
    });
    releaseFirst.complete();
    await Future.wait([first, second]);

    expect(applied, ['second']);
  });

  test('invalidated subscription reconciliation does not start', () async {
    final reconciler = LatestLiveSubscriptionReconciler();
    final blocker = Completer<void>();
    final blockingTask = reconciler.schedule((_) => blocker.future);
    var ranInvalidatedTask = false;
    final invalidatedTask = reconciler.schedule((_) async {
      ranInvalidatedTask = true;
    });

    reconciler.invalidate();
    blocker.complete();
    await Future.wait([blockingTask, invalidatedTask]);

    expect(ranInvalidatedTask, isFalse);
  });

  test('screen-share source picker keeps screens without thumbnails', () {
    final sources = filterScreenSourcesForPicker([
      const ScreenSource(
        id: 'screen:0',
        name: 'Screen 1',
        thumbnail: null,
        isWindow: false,
      ),
    ]);

    expect(sources.map((s) => s.id), ['screen:0']);
  });

  test(
    'screen-share source picker filters known invisible overlay windows',
    () {
      final sources = filterScreenSourcesForPicker([
        ScreenSource(
          id: 'window:overlay',
          name: 'NVIDIA GeForce Overlay',
          thumbnail: Uint8List.fromList(List<int>.filled(64, 1)),
          isWindow: true,
        ),
        ScreenSource(
          id: 'window:editor',
          name: 'Code',
          thumbnail: Uint8List.fromList(List<int>.filled(64, 2)),
          isWindow: true,
        ),
      ]);

      expect(sources.map((s) => s.id), ['window:editor']);
    },
  );

  test('screen-share source picker keeps windows without thumbnails', () {
    final sources = filterScreenSourcesForPicker([
      const ScreenSource(
        id: 'window:browser',
        name: 'Browser',
        thumbnail: null,
        isWindow: true,
      ),
    ]);

    expect(sources.map((s) => s.id), ['window:browser']);
  });

  test('screen-share source picker keeps non-overlay system windows', () {
    final sources = filterScreenSourcesForPicker([
      const ScreenSource(
        id: 'window:input',
        name: 'Microsoft Text Input Application',
        thumbnail: null,
        isWindow: true,
      ),
      const ScreenSource(
        id: 'window:program-manager',
        name: 'Program Manager',
        thumbnail: null,
        isWindow: true,
      ),
    ]);

    expect(sources.map((s) => s.id), [
      'window:input',
      'window:program-manager',
    ]);
  });

  test('screen-share source picker keeps same raw id across types', () {
    final sources = filterScreenSourcesForPicker([
      const ScreenSource(
        id: '1',
        name: 'Screen 1',
        thumbnail: null,
        isWindow: false,
        thumbnailKey: 'screen:1',
      ),
      const ScreenSource(
        id: '1',
        name: 'Window 1',
        thumbnail: null,
        isWindow: true,
        thumbnailKey: 'window:1',
      ),
    ]);

    expect(sources.map((s) => s.thumbnailKey), ['screen:1', 'window:1']);
  });

  test('screen-share requests audio for desktop and Android capture', () {
    expect(
      shouldRequestScreenShareAudio(
        sourceId: 'screen-primary',
        isDesktopSourcePickerPlatform: true,
        isWindowsDesktop: false,
        isAndroidPlatform: false,
      ),
      isTrue,
    );
    expect(
      shouldRequestScreenShareAudio(
        sourceId: null,
        isDesktopSourcePickerPlatform: true,
        isWindowsDesktop: false,
        isAndroidPlatform: false,
      ),
      isTrue,
    );
    expect(
      shouldRequestScreenShareAudio(
        sourceId: 'screen-primary',
        isDesktopSourcePickerPlatform: false,
        isWindowsDesktop: false,
        isAndroidPlatform: false,
      ),
      isFalse,
    );
    expect(
      shouldRequestScreenShareAudio(
        sourceId: null,
        isDesktopSourcePickerPlatform: false,
        isWindowsDesktop: false,
        isAndroidPlatform: true,
      ),
      isTrue,
    );
  });

  test('screen-share requests aux audio on Windows desktop', () {
    expect(
      shouldRequestScreenShareAudio(
        sourceId: 'screen-primary',
        isDesktopSourcePickerPlatform: true,
        isWindowsDesktop: true,
        isAndroidPlatform: false,
      ),
      isTrue,
    );
  });

  test(
    'screen-share thumbnail updates are cached for reopened picker',
    () async {
      resetScreenSourceThumbnailCacheForTest();
      addTearDown(resetScreenSourceThumbnailCacheForTest);

      final controller = StreamController<Uint8List>.broadcast(sync: true);
      addTearDown(controller.close);

      final observed = <Uint8List>[];
      final subscription = cacheScreenSourceThumbnailUpdatesForTest(
        'screen:0',
        controller.stream,
      ).listen(observed.add);
      addTearDown(subscription.cancel);
      final sourceIdUpdates = <Uint8List>[];
      final sourceIdSubscription = screenSourceThumbnailUpdatesForTest(
        'screen:0',
      ).listen(sourceIdUpdates.add);
      addTearDown(sourceIdSubscription.cancel);

      final thumbnail = Uint8List.fromList([1, 2, 3]);
      controller.add(thumbnail);
      await Future<void>.delayed(Duration.zero);

      expect(observed, hasLength(1));
      expect(observed.single, same(thumbnail));
      expect(sourceIdUpdates, hasLength(1));
      expect(sourceIdUpdates.single, same(thumbnail));
      expect(cachedScreenSourceThumbnailForTest('screen:0'), same(thumbnail));
    },
  );

  test('session starts and stops the combined audio-device rebinder', () async {
    final changes = StreamController<void>.broadcast();
    addTearDown(changes.close);
    var resolutions = 0;

    final session = LiveSession(
      audioDeviceRebinderFactory: (_) => AudioDeviceRebinder(
        deviceChanges: changes.stream,
        resolvePreferredDevices: () async {
          resolutions += 1;
          return const PreferredLiveAudioDevices();
        },
        rebindInput: (_) async {},
        selectOutput: (_) async {},
        onOutputRebound: () async {},
        debounce: const Duration(milliseconds: 10),
      ),
    );
    addTearDown(session.dispose);

    session.debugStartAudioDeviceRebinder();
    changes.add(null);
    await Future<void>.delayed(const Duration(milliseconds: 30));
    expect(resolutions, 1);

    session.debugStopAudioDeviceRebinder();
    changes.add(null);
    await Future<void>.delayed(const Duration(milliseconds: 30));
    expect(resolutions, 1);
  });

  test('session starts and stops the Windows camera reconciler', () async {
    final changes = StreamController<void>.broadcast();
    addTearDown(changes.close);
    var refreshes = 0;

    final session = LiveSession(
      audioDeviceRebinderFactory: (_) => null,
      cameraDeviceReconcilerFactory: () => CameraDeviceReconciler(
        deviceChanges: changes.stream,
        refreshDevices: () async => refreshes += 1,
        debounce: const Duration(milliseconds: 10),
      ),
    );
    addTearDown(session.dispose);

    session.debugStartAudioDeviceRebinder();
    changes.add(null);
    await Future<void>.delayed(const Duration(milliseconds: 30));
    expect(refreshes, 1);

    session.debugStopAudioDeviceRebinder();
    changes.add(null);
    await Future<void>.delayed(const Duration(milliseconds: 30));
    expect(refreshes, 1);
  });

  test('session starts the output rebinder while connected', () async {
    final changes = StreamController<void>.broadcast();
    addTearDown(changes.close);
    final selected = <String>[];

    AudioOutputRebinder? built;
    final session = LiveSession(
      outputRebinderFactory: (s) {
        built = AudioOutputRebinder(
          deviceChanges: changes.stream,
          currentOutputDeviceId: () async => 'speaker_1',
          selectOutput: (id) async => selected.add(id),
          onRebound: () async {},
          debounce: const Duration(milliseconds: 10),
        );
        return built;
      },
    );
    addTearDown(session.dispose);

    session.debugStartOutputRebinder();
    changes.add(null);
    await Future<void>.delayed(const Duration(milliseconds: 30));

    // The rebinder built by the factory is live and reacting to device flips.
    expect(built, isNotNull);
    expect(selected, ['speaker_1']);
  });

  test('session starts the input rebinder while connected', () async {
    final changes = StreamController<void>.broadcast();
    addTearDown(changes.close);
    final rebound = <String?>[];

    AudioInputRebinder? built;
    final session = LiveSession(
      inputRebinderFactory: (s) {
        built = AudioInputRebinder(
          deviceChanges: changes.stream,
          currentInputDeviceId: () async => 'mic_1',
          rebindInput: (id) async => rebound.add(id),
          debounce: const Duration(milliseconds: 10),
        );
        return built;
      },
      outputRebinderFactory: (_) => null,
    );
    addTearDown(session.dispose);

    session.debugStartInputRebinder();
    changes.add(null);
    await Future<void>.delayed(const Duration(milliseconds: 30));

    expect(built, isNotNull);
    expect(rebound, ['mic_1']);
  });

  test('session stops the output rebinder so flips stop rebinding', () async {
    final changes = StreamController<void>.broadcast();
    addTearDown(changes.close);
    var selects = 0;

    final session = LiveSession(
      outputRebinderFactory: (s) => AudioOutputRebinder(
        deviceChanges: changes.stream,
        currentOutputDeviceId: () async => 'speaker_1',
        selectOutput: (_) async => selects += 1,
        onRebound: () async {},
        debounce: const Duration(milliseconds: 10),
      ),
    );
    addTearDown(session.dispose);

    session.debugStartOutputRebinder();
    session.debugStopOutputRebinder();
    changes.add(null);
    await Future<void>.delayed(const Duration(milliseconds: 30));

    expect(selects, 0);
  });

  test('session stops the input rebinder so flips stop rebinding', () async {
    final changes = StreamController<void>.broadcast();
    addTearDown(changes.close);
    var rebounds = 0;

    final session = LiveSession(
      inputRebinderFactory: (s) => AudioInputRebinder(
        deviceChanges: changes.stream,
        currentInputDeviceId: () async => 'mic_1',
        rebindInput: (_) async => rebounds += 1,
        debounce: const Duration(milliseconds: 10),
      ),
      outputRebinderFactory: (_) => null,
    );
    addTearDown(session.dispose);

    session.debugStartInputRebinder();
    session.debugStopInputRebinder();
    changes.add(null);
    await Future<void>.delayed(const Duration(milliseconds: 30));

    expect(rebounds, 0);
  });

  test('a null factory disables output rebinding (non-macOS)', () async {
    final session = LiveSession(outputRebinderFactory: (_) => null);
    addTearDown(session.dispose);

    // Should be a no-op rather than throwing when there's nothing to rebind.
    session.debugStartOutputRebinder();
    session.debugStopOutputRebinder();
  });

  test('a null factory disables input rebinding (non-desktop)', () async {
    final session = LiveSession(
      inputRebinderFactory: (_) => null,
      outputRebinderFactory: (_) => null,
    );
    addTearDown(session.dispose);

    session.debugStartInputRebinder();
    session.debugStopInputRebinder();
  });

  test(
    'publish permission updates ignore headphone-only permission events',
    () {
      expect(
        shouldApplyLivePublishPermissionUpdate(
          currentCanPublish: true,
          oldCanPublish: true,
          nextCanPublish: true,
        ),
        isFalse,
      );
      expect(
        shouldApplyLivePublishPermissionUpdate(
          currentCanPublish: true,
          oldCanPublish: true,
          nextCanPublish: false,
        ),
        isTrue,
      );
      expect(
        shouldApplyLivePublishPermissionUpdate(
          currentCanPublish: false,
          oldCanPublish: false,
          nextCanPublish: true,
        ),
        isTrue,
      );
      expect(
        shouldApplyLivePublishPermissionUpdate(
          currentCanPublish: true,
          oldCanPublish: false,
          nextCanPublish: false,
        ),
        isFalse,
      );
    },
  );
}

class _FakeLiveParticipant implements lk.Participant<lk.TrackPublication> {
  _FakeLiveParticipant({
    this.identity = 'participant',
    required this.microphonePublication,
  });

  @override
  final String identity;
  final lk.TrackPublication? microphonePublication;

  @override
  lk.TrackPublication? getTrackPublicationBySource(lk.TrackSource source) {
    return source == lk.TrackSource.microphone ? microphonePublication : null;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _FakeTrackPublication implements lk.TrackPublication<lk.Track> {
  _FakeTrackPublication({required this.muted});

  @override
  final bool muted;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}
