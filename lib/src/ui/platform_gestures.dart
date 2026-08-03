import 'dart:async';

import 'package:flutter/gestures.dart';
import 'package:flutter/widgets.dart';

/// A primary-button tap target that observes pointer events without competing
/// in Flutter's gesture arena.
///
/// This is useful for navigation cards embedded in scrollable/selectable
/// surfaces where an ancestor recognizer may consume the first ordinary tap.
/// Touch drags, long presses, secondary clicks, and multi-touch are rejected so
/// scrolling and context-menu gestures keep their normal behavior.
class UiPointerTapRegion extends StatefulWidget {
  const UiPointerTapRegion({
    super.key,
    required this.child,
    required this.onTap,
    this.disableSelection = false,
  });

  final Widget child;
  final VoidCallback? onTap;
  final bool disableSelection;

  @override
  State<UiPointerTapRegion> createState() => _UiPointerTapRegionState();
}

class _UiPointerTapRegionState extends State<UiPointerTapRegion> {
  final GlobalKey _hitTargetKey = GlobalKey();
  int? _pointer;
  Offset? _origin;
  Timer? _longPressTimer;
  bool _touchLike = false;
  bool _cancelled = false;

  bool _isTouchLike(PointerDeviceKind kind) {
    return kind == PointerDeviceKind.touch ||
        kind == PointerDeviceKind.stylus ||
        kind == PointerDeviceKind.invertedStylus;
  }

  void _handlePointerDown(PointerDownEvent event) {
    if (widget.onTap == null || event.buttons & kPrimaryButton == 0) return;
    if (_pointer != null) {
      _clearTracking();
      return;
    }
    _pointer = event.pointer;
    _origin = event.position;
    _touchLike = _isTouchLike(event.kind);
    _cancelled = false;
    if (_touchLike) {
      final pointer = event.pointer;
      _longPressTimer = Timer(kLongPressTimeout, () {
        if (_pointer == pointer) _cancelled = true;
      });
    }
  }

  void _handlePointerMove(PointerMoveEvent event) {
    if (event.pointer != _pointer || !_touchLike) return;
    final origin = _origin;
    if (origin != null &&
        (event.position - origin).distanceSquared > kTouchSlop * kTouchSlop) {
      _cancelled = true;
    }
  }

  void _handlePointerUp(PointerUpEvent event) {
    if (event.pointer != _pointer) return;
    final shouldTap = !_cancelled && _contains(event.localPosition);
    _clearTracking();
    if (shouldTap) widget.onTap?.call();
  }

  void _handlePointerCancel(PointerCancelEvent event) {
    if (event.pointer == _pointer) _clearTracking();
  }

  bool _contains(Offset localPosition) {
    final renderObject = _hitTargetKey.currentContext?.findRenderObject();
    if (renderObject is! RenderBox) return false;
    return localPosition.dx >= 0 &&
        localPosition.dy >= 0 &&
        localPosition.dx <= renderObject.size.width &&
        localPosition.dy <= renderObject.size.height;
  }

  void _clearTracking() {
    _longPressTimer?.cancel();
    _longPressTimer = null;
    _pointer = null;
    _origin = null;
    _touchLike = false;
    _cancelled = false;
  }

  @override
  void didUpdateWidget(covariant UiPointerTapRegion oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.onTap != null && widget.onTap == null) _clearTracking();
  }

  @override
  void dispose() {
    _longPressTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    Widget result = Semantics(
      button: true,
      enabled: widget.onTap != null,
      onTap: widget.onTap,
      child: Listener(
        key: _hitTargetKey,
        behavior: HitTestBehavior.opaque,
        onPointerDown: _handlePointerDown,
        onPointerMove: _handlePointerMove,
        onPointerUp: _handlePointerUp,
        onPointerCancel: _handlePointerCancel,
        child: widget.child,
      ),
    );
    if (widget.disableSelection) {
      result = SelectionContainer.disabled(child: result);
    }
    return result;
  }
}

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
