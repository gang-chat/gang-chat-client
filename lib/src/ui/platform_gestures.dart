import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';

/// Observes an Android touch/stylus hold without joining the gesture arena.
///
/// This is intentionally a pointer-event helper instead of a recognizer. It
/// lets an outer application context-menu target observe a hold even when an
/// inner editable text control owns Flutter's native long-press recognizer.
class UiAndroidLongPressTracker {
  UiAndroidLongPressTracker({required this.onLongPress});

  final ValueChanged<Offset> onLongPress;

  Timer? _timer;
  int? _pointer;
  Offset? _origin;

  void handlePointerDown(PointerDownEvent event, {required bool enabled}) {
    if (_pointer != null) {
      // A second contact means a multi-touch gesture, never a context-menu
      // hold. Do not transfer tracking to the newer pointer.
      cancel();
      return;
    }
    cancel();
    if (!enabled || !_isTouchLike(event.kind)) return;
    _pointer = event.pointer;
    _origin = event.position;
    _timer = Timer(kLongPressTimeout, () {
      final position = _origin;
      if (_pointer == null || position == null) return;
      _timer = null;
      onLongPress(position);
    });
  }

  void handlePointerMove(PointerMoveEvent event) {
    if (event.pointer != _pointer) return;
    final origin = _origin;
    if (origin == null || (event.position - origin).distance <= kTouchSlop) {
      return;
    }
    cancel();
  }

  void handlePointerUp(PointerUpEvent event) {
    if (event.pointer == _pointer) cancel();
  }

  void handlePointerCancel(PointerCancelEvent event) {
    if (event.pointer == _pointer) cancel();
  }

  void cancel() {
    _timer?.cancel();
    _timer = null;
    _pointer = null;
    _origin = null;
  }

  bool _isTouchLike(PointerDeviceKind kind) {
    return kind == PointerDeviceKind.touch ||
        kind == PointerDeviceKind.stylus ||
        kind == PointerDeviceKind.invertedStylus;
  }
}
