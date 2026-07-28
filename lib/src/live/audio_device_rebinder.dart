import 'dart:async';

import '../app/audio_device_info.dart';
import '../app/audio_device_store.dart';
import 'audio_device_service.dart';
import 'system_audio_devices.dart';

/// The input/output choice resolved from one device-list snapshot.
class PreferredLiveAudioDevices {
  const PreferredLiveAudioDevices({this.inputDeviceId, this.outputDeviceId});

  final String? inputDeviceId;
  final String? outputDeviceId;
}

/// Resolves both preferred endpoints together.
///
/// In particular, [audioDevices.enumerateDevices] is invoked exactly once.
/// This prevents an input and output change from racing two native device
/// enumerations against the same WebRTC audio-device module.
Future<PreferredLiveAudioDevices> preferredLiveAudioDevices({
  required AudioDeviceStore? audioDeviceStore,
  required LiveAudioDeviceService audioDevices,
  required SystemAudioDevices systemAudio,
}) async {
  final defaultsFuture = Future.wait<String?>([
    systemAudio.currentInputDeviceId(),
    systemAudio.currentOutputDeviceId(),
  ]);
  final storedFuture = _readStoredAudioDevices(audioDeviceStore);

  // Do not turn a native timeout into an empty topology. Keeping the existing
  // endpoints is safer than accidentally rebinding both directions to a
  // default while Windows CoreAudio/WebRTC is still recovering.
  final devices = await audioDevices.enumerateDevices();

  final defaults = await defaultsFuture;
  final stored = await storedFuture;

  final input = preferredStoredAudioDeviceFrom<AudioDeviceInfo>(
    devices,
    kind: 'audioinput',
    storedDeviceId: stored.inputDeviceId,
    storedDeviceLabel: stored.inputDeviceLabel,
    storedDeviceGroupId: stored.inputDeviceGroupId,
    kindOf: audioDeviceInfoKind,
    deviceIdOf: audioDeviceInfoId,
    labelOf: audioDeviceInfoLabel,
    groupIdOf: audioDeviceInfoGroupId,
    systemDefaultDeviceId: defaults[0],
  );
  final output = preferredStoredAudioDeviceFrom<AudioDeviceInfo>(
    devices,
    kind: 'audiooutput',
    storedDeviceId: stored.outputDeviceId,
    storedDeviceLabel: stored.outputDeviceLabel,
    storedDeviceGroupId: stored.outputDeviceGroupId,
    kindOf: audioDeviceInfoKind,
    deviceIdOf: audioDeviceInfoId,
    labelOf: audioDeviceInfoLabel,
    groupIdOf: audioDeviceInfoGroupId,
    systemDefaultDeviceId: defaults[1],
  );

  return PreferredLiveAudioDevices(
    inputDeviceId: input?.deviceId ?? defaults[0],
    outputDeviceId: output?.deviceId ?? defaults[1],
  );
}

Future<StoredAudioDevices> _readStoredAudioDevices(
  AudioDeviceStore? store,
) async {
  if (store == null) return const StoredAudioDevices();
  try {
    return await store.read();
  } catch (_) {
    return const StoredAudioDevices();
  }
}

/// Coalesces desktop input/output changes into one serialized refresh.
///
/// A device transition can emit several notifications and can emit another
/// notification while a rebind is still running. At most one refresh runs at
/// once; one trailing refresh is retained so the final settled topology is
/// always applied.
class AudioDeviceRebinder {
  AudioDeviceRebinder({
    required Stream<void> deviceChanges,
    required Future<PreferredLiveAudioDevices> Function()
    resolvePreferredDevices,
    required Future<void> Function(String? deviceId) rebindInput,
    required Future<void> Function(String deviceId) selectOutput,
    required Future<void> Function() onOutputRebound,
    Duration debounce = const Duration(milliseconds: 300),
    Duration operationTimeout = const Duration(seconds: 7),
  }) : _deviceChanges = deviceChanges,
       _resolvePreferredDevices = resolvePreferredDevices,
       _rebindInput = rebindInput,
       _selectOutput = selectOutput,
       _onOutputRebound = onOutputRebound,
       _debounce = debounce,
       _operationTimeout = operationTimeout;

  final Stream<void> _deviceChanges;
  final Future<PreferredLiveAudioDevices> Function() _resolvePreferredDevices;
  final Future<void> Function(String? deviceId) _rebindInput;
  final Future<void> Function(String deviceId) _selectOutput;
  final Future<void> Function() _onOutputRebound;
  final Duration _debounce;
  final Duration _operationTimeout;

  StreamSubscription<void>? _subscription;
  Timer? _debounceTimer;
  final Completer<void> _stopSignal = Completer<void>();
  Future<void>? _activeRefresh;
  bool _refreshing = false;
  bool _refreshQueued = false;
  bool _stopped = false;

  void start() {
    if (_subscription != null || _stopped) return;
    _subscription = _deviceChanges.listen(
      (_) => _scheduleRefresh(),
      onError: (_, _) {},
    );
  }

  Future<void> stop() async {
    if (_stopped) {
      await _activeRefresh;
      return;
    }
    _stopped = true;
    if (!_stopSignal.isCompleted) _stopSignal.complete();
    _debounceTimer?.cancel();
    _debounceTimer = null;
    final subscription = _subscription;
    _subscription = null;
    await subscription?.cancel();
    try {
      await _activeRefresh;
    } catch (_) {
      // Stopping deliberately cancels/invalidates the in-flight refresh.
    }
  }

  void _scheduleRefresh() {
    if (_stopped) return;
    _debounceTimer?.cancel();
    _debounceTimer = Timer(_debounce, () {
      final refresh = _refresh();
      _activeRefresh = refresh;
      unawaited(
        refresh.whenComplete(() {
          if (identical(_activeRefresh, refresh)) _activeRefresh = null;
        }),
      );
    });
  }

  Future<T> _runStage<T>(Future<T> Function() operation) {
    final stage = Future<T>.sync(operation).timeout(_operationTimeout);
    return Future.any<T>([
      stage,
      _stopSignal.future.then<T>((_) => throw const _RebindCancelled()),
    ]);
  }

  Future<void> _refresh() async {
    if (_stopped) return;
    if (_refreshing) {
      _refreshQueued = true;
      return;
    }

    _refreshing = true;
    try {
      final preferred = await _runStage(_resolvePreferredDevices);
      if (_stopped) return;

      // Keep input and output native operations serialized too. Some desktop
      // WebRTC ADMs use one device thread for both directions.
      try {
        await _runStage(() => _rebindInput(preferred.inputDeviceId));
      } catch (_) {
        // Input recovery is best-effort; still give output a chance to recover.
      }
      if (_stopped) return;

      final outputDeviceId = preferred.outputDeviceId;
      if (outputDeviceId != null && outputDeviceId.isNotEmpty) {
        try {
          await _runStage(() => _selectOutput(outputDeviceId));
        } catch (_) {
          // The OS may already have moved playback to its new default. Always
          // reapply routing/volume even when explicit selection is unavailable.
        }
      }
      if (_stopped) return;
      await _runStage(_onOutputRebound);
    } catch (_) {
      // The next device event gets another attempt without disconnecting room.
    } finally {
      _refreshing = false;
      if (_refreshQueued && !_stopped) {
        _refreshQueued = false;
        _scheduleRefresh();
      }
    }
  }
}

class _RebindCancelled implements Exception {
  const _RebindCancelled();
}
