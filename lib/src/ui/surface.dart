import 'package:flutter/gestures.dart' show PointerDeviceKind, kTouchSlop;
import 'package:flutter/material.dart';

import 'tooltip.dart';

import 'tokens.dart';

enum SurfaceCorner { topLeft, topRight, bottomLeft, bottomRight }

class PressableSurface extends StatefulWidget {
  const PressableSurface({
    super.key,
    required this.child,
    required this.height,
    this.width,
    this.padding = EdgeInsets.zero,
    this.margin = EdgeInsets.zero,
    this.onPressed,
    this.tooltip,
    this.mouseCursor,
    this.enabled = true,
    this.interactive = false,
    this.loading = false,
    this.pressRequiresHover = false,
    this.cancelTouchPressOnDrag = false,
    this.selected = false,
    this.backgroundColor = UiColors.surface,
    this.selectedBackgroundColor = UiColors.selected,
    this.pressedBackgroundColor = UiColors.surfacePressed,
    this.disabledBackgroundColor = UiColors.disabledSurface,
    this.borderColor = UiColors.border,
    this.baseBorderColor,
    this.selectedBorderColor = UiColors.selectedBorder,
    this.disabledBorderColor = UiColors.disabledBorder,
    this.borderRadius = UiRadii.sm,
    this.hoverLift = 3,
    this.pressDepth = double.infinity,
    this.baseDepth = 5,
    this.hoverEffect = true,
    this.pressEffect = true,
    this.defaultRaised = false,
    this.elevateOnHover = false,
    this.cornerCut = Size.zero,
    this.cutCorner,
  });

  final Widget child;
  final double height;
  final double? width;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry margin;
  final VoidCallback? onPressed;
  final String? tooltip;
  final MouseCursor? mouseCursor;
  final bool enabled;
  final bool interactive;
  final bool loading;
  final bool pressRequiresHover;

  /// Cancels [onPressed] when a touch-like pointer moves beyond Flutter's
  /// normal touch slop. Opt in for surfaces nested in a scrollable whose raw
  /// pointer handling would otherwise bypass the gesture arena.
  final bool cancelTouchPressOnDrag;
  final bool selected;
  final Color backgroundColor;
  final Color selectedBackgroundColor;
  final Color pressedBackgroundColor;
  final Color disabledBackgroundColor;
  final Color borderColor;

  /// Optional border used by the lower depth layer. When omitted, the lower
  /// layer keeps the same border as the cap for backwards compatibility.
  final Color? baseBorderColor;
  final Color selectedBorderColor;
  final Color disabledBorderColor;
  final double borderRadius;
  final double hoverLift;
  final double pressDepth;
  final double baseDepth;
  final bool hoverEffect;
  final bool pressEffect;
  final bool defaultRaised;
  final bool elevateOnHover;
  final Size cornerCut;
  final SurfaceCorner? cutCorner;

  @override
  State<PressableSurface> createState() => _PressableSurfaceState();
}

class _PressableSurfaceState extends State<PressableSurface> {
  final GlobalKey _hitTargetKey = GlobalKey();

  bool _hovered = false;
  bool _pressed = false;
  int? _pressedPointer;
  Offset? _pressGlobalOrigin;
  bool _trackingTouchDrag = false;
  bool _touchDragCancelled = false;

  bool get _isInteractive =>
      widget.enabled && (widget.interactive || widget.onPressed != null);

  void _setHover(bool hovered) {
    if (!mounted) return;
    _hovered = hovered;
    if (!hovered && _pressedPointer == null) _pressed = false;
    if (mounted) setState(() {});
  }

  void _setPressed(bool pressed) {
    if (!mounted) return;
    if (!_isInteractive || !widget.pressEffect) return;
    if (pressed && widget.pressRequiresHover && !_hovered) return;
    if (_pressed == pressed) return;
    setState(() => _pressed = pressed);
  }

  void _handleTap() {
    if (!mounted) return;
    if (!widget.enabled || widget.loading) return;
    widget.onPressed?.call();
  }

  bool _isInsideHitTarget(Offset position) {
    final renderObject = _hitTargetKey.currentContext?.findRenderObject();
    if (renderObject is! RenderBox) return false;
    final size = renderObject.size;
    return position.dx >= 0 &&
        position.dy >= 0 &&
        position.dx <= size.width &&
        position.dy <= size.height;
  }

  void _handlePointerDown(PointerDownEvent event) {
    if (!mounted) return;
    if (!_isInteractive || widget.loading || _pressedPointer != null) return;
    _pressedPointer = event.pointer;
    _pressGlobalOrigin = event.position;
    _trackingTouchDrag =
        widget.cancelTouchPressOnDrag && _isTouchLike(event.kind);
    _touchDragCancelled = false;
    _setPressed(_isInsideHitTarget(event.localPosition));
  }

  void _handlePointerMove(PointerMoveEvent event) {
    if (!mounted) return;
    if (event.pointer != _pressedPointer) return;
    final origin = _pressGlobalOrigin;
    if (_trackingTouchDrag &&
        !_touchDragCancelled &&
        origin != null &&
        (event.position - origin).distanceSquared > kTouchSlop * kTouchSlop) {
      _touchDragCancelled = true;
      _setPressed(false);
      return;
    }
    if (_touchDragCancelled) return;
    // Keep the pressed effect for the whole hold, even if the pointer wanders
    // outside the hit target. Whether the tap actually fires is decided at
    // pointer-up from the release position.
    _setPressed(true);
  }

  void _handlePointerUp(PointerUpEvent event) {
    if (!mounted) return;
    if (event.pointer != _pressedPointer) return;
    final shouldPress =
        !_touchDragCancelled && _isInsideHitTarget(event.localPosition);
    _clearPointerTracking();
    _setPressed(false);
    if (shouldPress) _handleTap();
  }

  void _handlePointerCancel(PointerCancelEvent event) {
    if (!mounted) return;
    if (event.pointer != _pressedPointer) return;
    _clearPointerTracking();
    _setPressed(false);
  }

  void _clearPointerTracking() {
    _pressedPointer = null;
    _pressGlobalOrigin = null;
    _trackingTouchDrag = false;
    _touchDragCancelled = false;
  }

  bool _isTouchLike(PointerDeviceKind kind) {
    return kind == PointerDeviceKind.touch ||
        kind == PointerDeviceKind.stylus ||
        kind == PointerDeviceKind.invertedStylus;
  }

  @override
  void didUpdateWidget(PressableSurface oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!_isInteractive && (_hovered || _pressed)) {
      _hovered = false;
      _pressed = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    final radius = widget.borderRadius <= 0
        ? BorderRadius.zero
        : BorderRadius.circular(widget.borderRadius);
    final background = widget.enabled
        ? (widget.selected
              ? widget.selectedBackgroundColor
              : widget.backgroundColor)
        : widget.disabledBackgroundColor;
    final borderColor = widget.enabled
        ? (widget.selected ? widget.selectedBorderColor : widget.borderColor)
        : widget.disabledBorderColor;
    final pressed = widget.loading || (_pressed && widget.pressEffect);
    final effectiveBackground = pressed
        ? Color.lerp(background, widget.pressedBackgroundColor, 0.28)!
        : background;
    final shadowColor = _shadowFor(background);
    final hoverLift = widget.hoverEffect && _hovered && _isInteractive
        ? widget.hoverLift
        : 0.0;
    final maxDepth = widget.baseDepth.clamp(0.0, double.infinity).toDouble();
    final pressDepth = widget.pressDepth.clamp(0.0, maxDepth).toDouble();
    final outerHeight = widget.elevateOnHover
        ? widget.height
        : widget.height + widget.hoverLift + maxDepth;
    final baseTop = widget.elevateOnHover ? 0.0 : widget.hoverLift + maxDepth;
    final restTop = widget.elevateOnHover
        ? 0.0
        : widget.defaultRaised
        ? 0.0
        : widget.hoverLift;
    final bottomTop = widget.elevateOnHover ? 0.0 : widget.hoverLift + maxDepth;
    final capTop = widget.elevateOnHover
        ? (pressed ? 0.0 : -hoverLift)
        : pressed
        ? (restTop + pressDepth).clamp(0.0, bottomTop).toDouble()
        : (restTop - hoverLift).clamp(0.0, bottomTop).toDouble();
    final baseOpacity = widget.enabled ? 1.0 : 0.42;
    final explicitWidth = widget.width != null && widget.width!.isFinite
        ? widget.width
        : null;
    final cap = _SurfaceLayer(
      background: effectiveBackground,
      borderColor: borderColor,
      borderRadius: radius,
      cornerCut: widget.cornerCut,
      cutCorner: widget.cutCorner,
      padding: widget.padding,
      child: widget.child,
    );

    Widget result = Padding(
      padding: widget.margin,
      child: MouseRegion(
        cursor:
            widget.mouseCursor ??
            (_isInteractive
                ? SystemMouseCursors.click
                : SystemMouseCursors.basic),
        onEnter: (_) => _setHover(true),
        onExit: (_) => _setHover(false),
        child: Listener(
          key: _hitTargetKey,
          behavior: HitTestBehavior.opaque,
          onPointerDown: _handlePointerDown,
          onPointerMove: _handlePointerMove,
          onPointerUp: _handlePointerUp,
          onPointerCancel: _handlePointerCancel,
          child: LayoutBuilder(
            builder: (context, constraints) {
              final fillWidth =
                  explicitWidth != null || constraints.hasTightWidth;
              final stack = Stack(
                clipBehavior: widget.elevateOnHover ? Clip.none : Clip.hardEdge,
                children: [
                  Positioned(
                    left: 0,
                    right: 0,
                    top: baseTop,
                    height: widget.height,
                    child: Opacity(
                      opacity: baseOpacity,
                      child: _SurfaceLayer(
                        background: shadowColor,
                        borderColor: widget.baseBorderColor ?? borderColor,
                        borderRadius: radius,
                        cornerCut: widget.cornerCut,
                        cutCorner: widget.cutCorner,
                      ),
                    ),
                  ),
                  if (fillWidth)
                    AnimatedPositioned(
                      duration: const Duration(milliseconds: 95),
                      curve: Curves.easeOutCubic,
                      left: 0,
                      right: 0,
                      top: capTop,
                      height: widget.height,
                      child: cap,
                    )
                  else
                    AnimatedContainer(
                      duration: const Duration(milliseconds: 95),
                      curve: Curves.easeOutCubic,
                      transform: Matrix4.translationValues(0, capTop, 0),
                      child: SizedBox(height: widget.height, child: cap),
                    ),
                ],
              );

              return SizedBox(
                width: explicitWidth,
                height: outerHeight,
                child: stack,
              );
            },
          ),
        ),
      ),
    );

    final tooltip = widget.tooltip;
    if (tooltip != null && tooltip.isNotEmpty) {
      result = UiTooltip(message: tooltip, child: result);
    }

    return Semantics(
      button: widget.onPressed != null,
      enabled: _isInteractive,
      child: result,
    );
  }
}

class _SurfaceLayer extends StatelessWidget {
  const _SurfaceLayer({
    required this.background,
    required this.borderColor,
    required this.borderRadius,
    this.padding = EdgeInsets.zero,
    this.child,
    this.cornerCut = Size.zero,
    this.cutCorner,
  });

  final Color background;
  final Color borderColor;
  final BorderRadius borderRadius;
  final EdgeInsetsGeometry padding;
  final Widget? child;
  final Size cornerCut;
  final SurfaceCorner? cutCorner;

  @override
  Widget build(BuildContext context) {
    final content = DecoratedBox(
      decoration: BoxDecoration(
        color: background,
        borderRadius: borderRadius,
        border: _visibleBorder(borderColor)
            ? Border.all(color: borderColor)
            : null,
      ),
      child: Padding(padding: padding, child: child ?? const SizedBox.expand()),
    );

    if (cornerCut.width <= 0 || cornerCut.height <= 0 || cutCorner == null) {
      return ClipRRect(borderRadius: borderRadius, child: content);
    }

    return ClipPath(
      clipper: _SurfaceClipper(
        borderRadius: borderRadius,
        cornerCut: cornerCut,
        cutCorner: cutCorner!,
      ),
      child: content,
    );
  }
}

class _SurfaceClipper extends CustomClipper<Path> {
  const _SurfaceClipper({
    required this.borderRadius,
    required this.cornerCut,
    required this.cutCorner,
  });

  final BorderRadius borderRadius;
  final Size cornerCut;
  final SurfaceCorner cutCorner;

  @override
  Path getClip(Size size) =>
      _surfacePath(Offset.zero & size, borderRadius, cornerCut, cutCorner);

  @override
  bool shouldReclip(_SurfaceClipper oldClipper) =>
      oldClipper.borderRadius != borderRadius ||
      oldClipper.cornerCut != cornerCut ||
      oldClipper.cutCorner != cutCorner;
}

bool _visibleBorder(Color color) => color.a > 0;

Path _surfacePath(
  Rect rect,
  BorderRadius borderRadius,
  Size cornerCut,
  SurfaceCorner cutCorner,
) {
  final cw = cornerCut.width.clamp(0.0, rect.width).toDouble();
  final ch = cornerCut.height.clamp(0.0, rect.height).toDouble();
  if (cw <= 0 || ch <= 0) {
    return Path()..addRRect(borderRadius.toRRect(rect));
  }

  final path = Path();
  final l = rect.left;
  final t = rect.top;
  final r = rect.right;
  final b = rect.bottom;

  if (cutCorner == SurfaceCorner.topLeft) {
    path
      ..moveTo(l + cw, t)
      ..lineTo(r, t)
      ..lineTo(r, b)
      ..lineTo(l, b)
      ..lineTo(l, t + ch)
      ..lineTo(l + cw, t + ch);
  } else if (cutCorner == SurfaceCorner.topRight) {
    path
      ..moveTo(l, t)
      ..lineTo(r - cw, t)
      ..lineTo(r - cw, t + ch)
      ..lineTo(r, t + ch)
      ..lineTo(r, b)
      ..lineTo(l, b);
  } else if (cutCorner == SurfaceCorner.bottomRight) {
    path
      ..moveTo(l, t)
      ..lineTo(r, t)
      ..lineTo(r, b - ch)
      ..lineTo(r - cw, b - ch)
      ..lineTo(r - cw, b)
      ..lineTo(l, b);
  } else {
    path
      ..moveTo(l, t)
      ..lineTo(r, t)
      ..lineTo(r, b)
      ..lineTo(l + cw, b)
      ..lineTo(l + cw, b - ch)
      ..lineTo(l, b - ch);
  }

  path.close();
  return path;
}

Color _shadowFor(Color background) {
  return Color.lerp(background, Colors.black, 0.46)!;
}
