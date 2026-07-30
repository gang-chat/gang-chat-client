import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

typedef SystemUiModeSetter =
    Future<void> Function(SystemUiMode mode, {List<SystemUiOverlay>? overlays});

/// Owns the Android system-UI boundary for full-screen media controls.
///
/// Desktop platforms are deliberately ignored. On Android, the status and
/// navigation bars follow the same visibility state as Gang Chat's media
/// controls and are always restored when the full-screen scope is disposed.
class FullScreenSystemUiController {
  FullScreenSystemUiController({
    TargetPlatform? platform,
    SystemUiModeSetter? setEnabledSystemUIMode,
  }) : _platform = platform ?? defaultTargetPlatform,
       _setEnabledSystemUIMode =
           setEnabledSystemUIMode ?? SystemChrome.setEnabledSystemUIMode;

  final TargetPlatform _platform;
  final SystemUiModeSetter _setEnabledSystemUIMode;
  Future<void> _pending = Future<void>.value();

  Future<void> setControlsVisible(bool visible) {
    if (_platform != TargetPlatform.android) return Future<void>.value();
    return _enqueue(
      visible
          ? () => _setEnabledSystemUIMode(
              SystemUiMode.manual,
              overlays: SystemUiOverlay.values,
            )
          : () => _setEnabledSystemUIMode(SystemUiMode.immersiveSticky),
    );
  }

  Future<void> restore() => setControlsVisible(true);

  Future<void> _enqueue(Future<void> Function() operation) {
    final next = _pending.then<void>((_) => operation());
    // A failed platform call must not prevent a later touch or disposal from
    // restoring the system bars.
    _pending = next.catchError((Object _) {});
    return next;
  }
}
