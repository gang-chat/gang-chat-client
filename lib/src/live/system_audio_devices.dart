import 'dart:async';
import 'dart:io' show Platform;
import 'dart:math' as math;

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/services.dart';

import '../app/audio_device_info.dart';

/// Desktop system audio-device access backed by the native
/// `gang_chat/audio_devices` channel.
///
/// macOS needs CoreAudio enumeration because flutter_webrtc lists no audio
/// devices before a room is joined. Windows deliberately uses this channel as
/// the authoritative audio-device source: asking WebRTC's ADM for device names
/// can synchronously wait inside libwebrtc while a device is being rebuilt.
class SystemAudioDevices {
  SystemAudioDevices({
    MethodChannel? channel,
    bool? supported,
    bool sharedEvents = true,
  }) : _channel = channel ?? const MethodChannel('gang_chat/audio_devices'),
       _supportedOverride = supported,
       _usesSharedEvents = channel == null && sharedEvents;

  static final _sharedEvents = _SystemAudioDeviceEventBus(
    const MethodChannel('gang_chat/audio_devices'),
  );
  final MethodChannel _channel;
  final bool? _supportedOverride;
  final bool _usesSharedEvents;
  final _inputChanges = StreamController<String?>.broadcast();
  final _outputChanges = StreamController<String?>.broadcast();
  final _videoChanges = StreamController<void>.broadcast();
  final _changes = StreamController<String?>.broadcast();
  bool _handlerInstalled = false;
  bool _nativeListening = false;
  bool _disposed = false;
  int _listenRetryAttempt = 0;
  Future<void>? _listenStart;
  Timer? _listenRetryTimer;

  bool get _supported {
    return _supportedOverride ??
        (!kIsWeb && (Platform.isMacOS || Platform.isWindows));
  }

  /// Native input devices, or an empty list when unsupported/unavailable.
  Future<List<AudioDeviceInfo>> enumerateInputs() {
    return _enumerate('enumerateInputs', 'audioinput', '麦克风');
  }

  /// Native output devices, or an empty list when unsupported/unavailable.
  Future<List<AudioDeviceInfo>> enumerateOutputs() {
    return _enumerate('enumerateOutputs', 'audiooutput', '扬声器');
  }

  /// Captures one logical input/output snapshot.
  ///
  /// Both directions are requested together so callers never perform two
  /// independent full refreshes for the same native device-change burst.
  Future<List<AudioDeviceInfo>> enumerateDevices() async {
    final devices = await Future.wait<List<AudioDeviceInfo>>([
      enumerateInputs(),
      enumerateOutputs(),
    ]);
    return [...devices[0], ...devices[1]];
  }

  /// Captures a native input/output snapshot without converting a platform
  /// failure into an empty topology.
  ///
  /// Device pickers intentionally use [enumerateDevices] so they can remain
  /// usable when a platform integration is unavailable. Automatic rebinding
  /// must use this strict variant: treating a timeout as "no devices" would
  /// incorrectly switch both endpoints to their defaults.
  Future<List<AudioDeviceInfo>> enumerateDevicesOrThrow() async {
    if (!_supported) return const [];
    final devices = await Future.wait<List<AudioDeviceInfo>>([
      _enumerate('enumerateInputs', 'audioinput', '麦克风', rethrowError: true),
      _enumerate('enumerateOutputs', 'audiooutput', '扬声器', rethrowError: true),
    ]);
    return [...devices[0], ...devices[1]];
  }

  Future<List<AudioDeviceInfo>> _enumerate(
    String method,
    String kind,
    String fallbackLabel, {
    bool rethrowError = false,
  }) async {
    if (!_supported) return const [];
    try {
      final raw = await _channel.invokeListMethod<Map<Object?, Object?>>(
        method,
      );
      if (raw == null) return const [];
      return [
        for (final entry in raw)
          AudioDeviceInfo(
            deviceId: (entry['deviceId'] as String?) ?? '',
            label: (entry['label'] as String?) ?? fallbackLabel,
            kind: kind,
            groupId: (entry['groupId'] as String?) ?? '',
          ),
      ].where((device) => device.deviceId.isNotEmpty).toList();
    } catch (_) {
      if (rethrowError) rethrow;
      return const [];
    }
  }

  Future<String?> currentInputDeviceId() {
    return _currentDeviceId('getDefaultInputDeviceId');
  }

  Future<String?> currentOutputDeviceId() {
    return _currentDeviceId('getDefaultOutputDeviceId');
  }

  /// Backwards-compatible alias for the default input device id.
  Future<String?> currentDeviceId() => currentInputDeviceId();

  Future<String?> _currentDeviceId(String method) async {
    if (!_supported) return null;
    try {
      return await _channel.invokeMethod<String>(method);
    } catch (_) {
      // The picker degrades to the enumerated/WebRTC list when the native
      // default is unavailable; surfacing the failure would not help the user.
      return null;
    }
  }

  Stream<String?> get inputChanges {
    _ensureListening();
    return _usesSharedEvents
        ? _sharedEvents.inputChanges
        : _inputChanges.stream;
  }

  Stream<String?> get outputChanges {
    _ensureListening();
    return _usesSharedEvents
        ? _sharedEvents.outputChanges
        : _outputChanges.stream;
  }

  /// Windows device-tree notifications used to reconcile camera selection.
  ///
  /// This is separate from [changes] so camera-only hotplug never restarts
  /// microphone capture or playout routing.
  Stream<void> get videoChanges {
    _ensureListening();
    return _usesSharedEvents
        ? _sharedEvents.videoChanges
        : _videoChanges.stream;
  }

  /// Any input/output default or endpoint-topology change.
  ///
  /// The value is the new default endpoint when one is known, otherwise null
  /// for add/remove/state-change notifications.
  Stream<String?> get changes {
    _ensureListening();
    return _usesSharedEvents ? _sharedEvents.changes : _changes.stream;
  }

  void _ensureListening() {
    if (!_supported || _disposed) return;
    if (_usesSharedEvents) {
      _sharedEvents.ensureListening();
      return;
    }
    if (!_handlerInstalled) {
      _handlerInstalled = true;
      _channel.setMethodCallHandler(_handleCall);
    }
    _startListening();
  }

  void _startListening() {
    if (_disposed || _nativeListening || _listenStart != null) return;
    final start = _channel.invokeMethod<void>('startListening');
    _listenStart = start;
    unawaited(() async {
      try {
        await start;
        if (_disposed) return;
        _nativeListening = true;
        _listenRetryAttempt = 0;
        _listenRetryTimer?.cancel();
        _listenRetryTimer = null;
      } catch (_) {
        if (!_disposed) _scheduleListeningRetry();
      } finally {
        if (identical(_listenStart, start)) _listenStart = null;
      }
    }());
  }

  void _scheduleListeningRetry() {
    if (_listenRetryTimer != null || _disposed || _nativeListening) return;
    final delayMs = 250 * (1 << math.min(_listenRetryAttempt, 5));
    _listenRetryAttempt += 1;
    _listenRetryTimer = Timer(Duration(milliseconds: delayMs), () {
      _listenRetryTimer = null;
      _startListening();
    });
  }

  Future<dynamic> _handleCall(MethodCall call) async {
    final deviceId = call.arguments;
    switch (call.method) {
      case 'defaultInputDeviceChanged':
        final id = deviceId is String ? deviceId : null;
        _inputChanges.add(id);
        _changes.add(id);
        break;
      case 'defaultOutputDeviceChanged':
        final id = deviceId is String ? deviceId : null;
        _outputChanges.add(id);
        _changes.add(id);
        break;
      case 'audioDevicesChanged':
        _changes.add(null);
        break;
      case 'videoDevicesChanged':
        _videoChanges.add(null);
        break;
    }
    return null;
  }

  Future<void> dispose() async {
    _disposed = true;
    _listenRetryTimer?.cancel();
    _listenRetryTimer = null;
    if (_handlerInstalled) {
      _channel.setMethodCallHandler(null);
      _handlerInstalled = false;
    }
    await _inputChanges.close();
    await _outputChanges.close();
    await _videoChanges.close();
    await _changes.close();
  }
}

/// One process-wide platform-channel handler fans native events out to every
/// default [SystemAudioDevices] instance. MethodChannel supports only one
/// handler per channel name; without this bus opening Settings could replace
/// the live-session hotplug handler (or vice versa).
class _SystemAudioDeviceEventBus {
  _SystemAudioDeviceEventBus(this.channel);

  final MethodChannel channel;
  final _inputChanges = StreamController<String?>.broadcast();
  final _outputChanges = StreamController<String?>.broadcast();
  final _videoChanges = StreamController<void>.broadcast();
  final _changes = StreamController<String?>.broadcast();
  bool _listening = false;
  bool _handlerInstalled = false;
  int _retryAttempt = 0;
  Future<void>? _start;
  Timer? _retryTimer;

  Stream<String?> get inputChanges => _inputChanges.stream;
  Stream<String?> get outputChanges => _outputChanges.stream;
  Stream<void> get videoChanges => _videoChanges.stream;
  Stream<String?> get changes => _changes.stream;

  void ensureListening() {
    if (_listening) return;
    if (!_handlerInstalled) {
      _handlerInstalled = true;
      channel.setMethodCallHandler(_handleCall);
    }
    _startListening();
  }

  void _startListening() {
    if (_listening || _start != null) return;
    final start = channel.invokeMethod<void>('startListening');
    _start = start;
    unawaited(() async {
      try {
        await start;
        _listening = true;
        _retryAttempt = 0;
        _retryTimer?.cancel();
        _retryTimer = null;
      } catch (_) {
        _scheduleRetry();
      } finally {
        if (identical(_start, start)) _start = null;
      }
    }());
  }

  void _scheduleRetry() {
    if (_retryTimer != null || _listening) return;
    final delayMs = 250 * (1 << math.min(_retryAttempt, 5));
    _retryAttempt += 1;
    _retryTimer = Timer(Duration(milliseconds: delayMs), () {
      _retryTimer = null;
      _startListening();
    });
  }

  Future<dynamic> _handleCall(MethodCall call) async {
    final deviceId = call.arguments;
    switch (call.method) {
      case 'defaultInputDeviceChanged':
        final id = deviceId is String ? deviceId : null;
        _inputChanges.add(id);
        _changes.add(id);
        break;
      case 'defaultOutputDeviceChanged':
        final id = deviceId is String ? deviceId : null;
        _outputChanges.add(id);
        _changes.add(id);
        break;
      case 'audioDevicesChanged':
        _changes.add(null);
        break;
      case 'videoDevicesChanged':
        _videoChanges.add(null);
        break;
    }
    return null;
  }
}
