import 'dart:async';

/// Debounces and serializes camera-topology refreshes.
///
/// Windows reports camera arrival/removal through the runner because
/// libwebrtc's desktop callback only observes audio endpoints. macOS and
/// Android continue to use their native WebRTC device-change callbacks.
class CameraDeviceReconciler {
  CameraDeviceReconciler({
    required Stream<void> deviceChanges,
    required Future<void> Function() refreshDevices,
    Duration debounce = const Duration(milliseconds: 300),
    Duration operationTimeout = const Duration(seconds: 7),
  }) : _deviceChanges = deviceChanges,
       _refreshDevices = refreshDevices,
       _debounce = debounce,
       _operationTimeout = operationTimeout;

  final Stream<void> _deviceChanges;
  final Future<void> Function() _refreshDevices;
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
      // Stopping deliberately invalidates the in-flight refresh.
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

  Future<void> _refresh() async {
    if (_stopped) return;
    if (_refreshing) {
      _refreshQueued = true;
      return;
    }
    _refreshing = true;
    try {
      await Future.any<void>([
        Future<void>.sync(_refreshDevices).timeout(_operationTimeout),
        _stopSignal.future,
      ]);
    } catch (_) {
      // A later topology event receives a fresh bounded attempt.
    } finally {
      _refreshing = false;
      if (_refreshQueued && !_stopped) {
        _refreshQueued = false;
        _scheduleRefresh();
      }
    }
  }
}
