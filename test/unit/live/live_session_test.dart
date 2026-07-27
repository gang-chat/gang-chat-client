import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:livekit_client/livekit_client.dart' as lk;

import 'package:client/src/live/audio_input_rebinder.dart';
import 'package:client/src/live/audio_output_rebinder.dart';
import 'package:client/src/live/live_session.dart';

void main() {
  test('camera flip capability distinguishes unknown and single-camera', () {
    expect(cameraFlipAvailabilityFromVideoInputCount(0), isNull);
    expect(cameraFlipAvailabilityFromVideoInputCount(1), isFalse);
    expect(cameraFlipAvailabilityFromVideoInputCount(2), isTrue);
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

  test('presence cues ignore hidden audio participants', () {
    expect(isLivePresenceSoundParticipantIdentity('user-2'), isTrue);
    expect(
      isLivePresenceSoundParticipantIdentity('user-2--screen-audio'),
      isFalse,
    );
    expect(isLivePresenceSoundParticipantIdentity('__musicbox__'), isFalse);
    expect(isLivePresenceSoundParticipantIdentity(''), isFalse);
  });

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

  test('screen-share requests audio only for desktop capture', () {
    expect(
      shouldRequestScreenShareAudio(
        sourceId: 'screen-primary',
        isDesktopSourcePickerPlatform: true,
        isWindowsDesktop: false,
      ),
      isTrue,
    );
    expect(
      shouldRequestScreenShareAudio(
        sourceId: null,
        isDesktopSourcePickerPlatform: true,
        isWindowsDesktop: false,
      ),
      isTrue,
    );
    expect(
      shouldRequestScreenShareAudio(
        sourceId: 'screen-primary',
        isDesktopSourcePickerPlatform: false,
        isWindowsDesktop: false,
      ),
      isFalse,
    );
  });

  test('screen-share requests aux audio on Windows desktop', () {
    expect(
      shouldRequestScreenShareAudio(
        sourceId: 'screen-primary',
        isDesktopSourcePickerPlatform: true,
        isWindowsDesktop: true,
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
