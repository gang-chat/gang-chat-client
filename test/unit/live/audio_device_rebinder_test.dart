import 'dart:async';

import 'package:client/src/app/audio_device_info.dart';
import 'package:client/src/app/audio_device_store.dart';
import 'package:client/src/live/audio_device_rebinder.dart';
import 'package:client/src/live/audio_device_service.dart';
import 'package:client/src/live/system_audio_devices.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('resolves input and output from one device enumeration', () async {
    final service = _FakeAudioDeviceService(
      devices: const [
        AudioDeviceInfo(
          deviceId: 'mic_new',
          label: 'USB Headset',
          kind: 'audioinput',
        ),
        AudioDeviceInfo(
          deviceId: 'speaker_new',
          label: 'USB Headset',
          kind: 'audiooutput',
        ),
      ],
    );
    final system = _FakeSystemAudioDevices(
      inputDeviceId: 'mic_default',
      outputDeviceId: 'speaker_default',
    );

    final resolved = await preferredLiveAudioDevices(
      audioDeviceStore: const _FakeAudioDeviceStore(
        inputDeviceId: 'mic_old',
        inputDeviceLabel: 'USB Headset',
        outputDeviceId: 'speaker_old',
        outputDeviceLabel: 'USB Headset',
      ),
      audioDevices: service,
      systemAudio: system,
    );

    expect(service.enumerations, 1);
    expect(resolved.inputDeviceId, 'mic_new');
    expect(resolved.outputDeviceId, 'speaker_new');
  });

  test('one device-change burst rebinds both directions once', () async {
    final changes = StreamController<void>.broadcast();
    addTearDown(changes.close);
    var resolutions = 0;
    final operations = <String>[];
    final rebinder = AudioDeviceRebinder(
      deviceChanges: changes.stream,
      resolvePreferredDevices: () async {
        resolutions += 1;
        return const PreferredLiveAudioDevices(
          inputDeviceId: 'mic_1',
          outputDeviceId: 'speaker_1',
        );
      },
      rebindInput: (id) async => operations.add('input:$id'),
      selectOutput: (id) async => operations.add('output:$id'),
      onOutputRebound: () async => operations.add('routing'),
      debounce: const Duration(milliseconds: 10),
    );
    addTearDown(rebinder.stop);
    rebinder.start();

    changes
      ..add(null)
      ..add(null)
      ..add(null);
    await _waitFor(() => operations.length == 3);

    expect(resolutions, 1);
    expect(operations, ['input:mic_1', 'output:speaker_1', 'routing']);
  });

  test('refresh is single-flight and retains one trailing refresh', () async {
    final changes = StreamController<void>.broadcast();
    addTearDown(changes.close);
    final firstStarted = Completer<void>();
    final releaseFirst = Completer<void>();
    var resolutions = 0;
    var activeResolutions = 0;
    var maxActiveResolutions = 0;
    var inputRebinds = 0;
    var outputRebinds = 0;

    final rebinder = AudioDeviceRebinder(
      deviceChanges: changes.stream,
      resolvePreferredDevices: () async {
        resolutions += 1;
        activeResolutions += 1;
        if (activeResolutions > maxActiveResolutions) {
          maxActiveResolutions = activeResolutions;
        }
        if (resolutions == 1) {
          firstStarted.complete();
          await releaseFirst.future;
        }
        activeResolutions -= 1;
        return const PreferredLiveAudioDevices(
          inputDeviceId: 'mic',
          outputDeviceId: 'speaker',
        );
      },
      rebindInput: (_) async => inputRebinds += 1,
      selectOutput: (_) async => outputRebinds += 1,
      onOutputRebound: () async {},
      debounce: Duration.zero,
    );
    addTearDown(rebinder.stop);
    rebinder.start();

    changes.add(null);
    await firstStarted.future;
    changes
      ..add(null)
      ..add(null);
    await Future<void>.delayed(Duration.zero);
    releaseFirst.complete();
    await _waitFor(() => resolutions == 2 && outputRebinds == 2);

    expect(maxActiveResolutions, 1);
    expect(inputRebinds, 2);
    expect(outputRebinds, 2);
  });

  test('long playback event storm stays serialized and recoverable', () async {
    final changes = StreamController<void>.broadcast();
    addTearDown(changes.close);
    var resolutions = 0;
    var activeOperations = 0;
    var maxActiveOperations = 0;
    var routingReapplies = 0;

    Future<void> operation() async {
      activeOperations += 1;
      if (activeOperations > maxActiveOperations) {
        maxActiveOperations = activeOperations;
      }
      await Future<void>.delayed(Duration.zero);
      activeOperations -= 1;
    }

    final rebinder = AudioDeviceRebinder(
      deviceChanges: changes.stream,
      resolvePreferredDevices: () async {
        resolutions += 1;
        await operation();
        return const PreferredLiveAudioDevices(
          inputDeviceId: 'mic',
          outputDeviceId: 'speaker',
        );
      },
      rebindInput: (_) => operation(),
      selectOutput: (_) => operation(),
      onOutputRebound: () async {
        await operation();
        routingReapplies += 1;
      },
      debounce: Duration.zero,
    );
    addTearDown(rebinder.stop);
    rebinder.start();

    // Simulates repeated endpoint/profile churn during a long viewing session
    // without making the suite sleep for real minutes.
    for (var cycle = 0; cycle < 100; cycle += 1) {
      changes
        ..add(null)
        ..add(null);
      await _waitFor(() => routingReapplies == cycle + 1);
    }

    expect(resolutions, 100);
    expect(routingReapplies, 100);
    expect(maxActiveOperations, 1);
  });

  test('a timed-out refresh does not block a later device change', () async {
    final changes = StreamController<void>.broadcast();
    addTearDown(changes.close);
    final firstStarted = Completer<void>();
    final neverCompletes = Completer<PreferredLiveAudioDevices>();
    var resolutions = 0;
    final operations = <String>[];
    final rebinder = AudioDeviceRebinder(
      deviceChanges: changes.stream,
      resolvePreferredDevices: () {
        resolutions += 1;
        if (resolutions == 1) {
          firstStarted.complete();
          return neverCompletes.future;
        }
        return Future.value(
          const PreferredLiveAudioDevices(
            inputDeviceId: 'mic_recovered',
            outputDeviceId: 'speaker_recovered',
          ),
        );
      },
      rebindInput: (id) async => operations.add('input:$id'),
      selectOutput: (id) async => operations.add('output:$id'),
      onOutputRebound: () async => operations.add('routing'),
      debounce: Duration.zero,
      operationTimeout: const Duration(milliseconds: 20),
    );
    addTearDown(rebinder.stop);
    rebinder.start();

    changes.add(null);
    await firstStarted.future;
    await Future<void>.delayed(const Duration(milliseconds: 30));
    changes.add(null);
    await _waitFor(() => operations.length == 3);

    expect(resolutions, 2);
    expect(operations, [
      'input:mic_recovered',
      'output:speaker_recovered',
      'routing',
    ]);
  });

  test(
    'stop cancels an in-flight refresh without waiting for native work',
    () async {
      final changes = StreamController<void>.broadcast();
      addTearDown(changes.close);
      final started = Completer<void>();
      final neverCompletes = Completer<PreferredLiveAudioDevices>();
      var inputRebinds = 0;
      final rebinder = AudioDeviceRebinder(
        deviceChanges: changes.stream,
        resolvePreferredDevices: () {
          started.complete();
          return neverCompletes.future;
        },
        rebindInput: (_) async => inputRebinds += 1,
        selectOutput: (_) async {},
        onOutputRebound: () async {},
        debounce: Duration.zero,
        operationTimeout: const Duration(seconds: 30),
      );
      rebinder.start();

      changes.add(null);
      await started.future;
      await rebinder.stop().timeout(const Duration(milliseconds: 200));

      expect(inputRebinds, 0);
    },
  );

  test('output selection failure still reapplies routing and volume', () async {
    final changes = StreamController<void>.broadcast();
    addTearDown(changes.close);
    var routingReapplies = 0;
    final rebinder = AudioDeviceRebinder(
      deviceChanges: changes.stream,
      resolvePreferredDevices: () async {
        return const PreferredLiveAudioDevices(
          inputDeviceId: 'mic',
          outputDeviceId: 'speaker',
        );
      },
      rebindInput: (_) async {},
      selectOutput: (_) async => throw StateError('endpoint disappeared'),
      onOutputRebound: () async => routingReapplies += 1,
      debounce: Duration.zero,
    );
    addTearDown(rebinder.stop);
    rebinder.start();

    changes.add(null);
    await _waitFor(() => routingReapplies == 1);
  });

  test('device enumeration failure is not mistaken for an empty topology', () {
    final service = _FakeAudioDeviceService(
      devices: const [],
      error: StateError('native enumeration timed out'),
    );

    expect(
      preferredLiveAudioDevices(
        audioDeviceStore: null,
        audioDevices: service,
        systemAudio: _FakeSystemAudioDevices(
          inputDeviceId: 'default_mic',
          outputDeviceId: 'default_speaker',
        ),
      ),
      throwsA(isA<StateError>()),
    );
  });
}

Future<void> _waitFor(bool Function() condition) async {
  final deadline = DateTime.now().add(const Duration(seconds: 2));
  while (!condition()) {
    if (DateTime.now().isAfter(deadline)) {
      fail('Timed out waiting for asynchronous audio-device operation.');
    }
    await Future<void>.delayed(const Duration(milliseconds: 1));
  }
}

class _FakeAudioDeviceService extends LiveAudioDeviceService {
  _FakeAudioDeviceService({required this.devices, this.error});

  final List<AudioDeviceInfo> devices;
  final Object? error;
  int enumerations = 0;

  @override
  Future<List<AudioDeviceInfo>> enumerateDevices() async {
    enumerations += 1;
    if (error != null) throw error!;
    return devices;
  }
}

class _FakeSystemAudioDevices extends SystemAudioDevices {
  _FakeSystemAudioDevices({
    required this.inputDeviceId,
    required this.outputDeviceId,
  }) : super(supported: false);

  final String? inputDeviceId;
  final String? outputDeviceId;

  @override
  Future<String?> currentInputDeviceId() async => inputDeviceId;

  @override
  Future<String?> currentOutputDeviceId() async => outputDeviceId;
}

class _FakeAudioDeviceStore extends AudioDeviceStore {
  const _FakeAudioDeviceStore({
    this.inputDeviceId,
    this.inputDeviceLabel,
    this.outputDeviceId,
    this.outputDeviceLabel,
  });

  final String? inputDeviceId;
  final String? inputDeviceLabel;
  final String? outputDeviceId;
  final String? outputDeviceLabel;

  @override
  Future<StoredAudioDevices> read() async {
    return StoredAudioDevices(
      inputDeviceId: inputDeviceId,
      inputDeviceLabel: inputDeviceLabel,
      outputDeviceId: outputDeviceId,
      outputDeviceLabel: outputDeviceLabel,
    );
  }
}
