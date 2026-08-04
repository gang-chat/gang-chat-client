import 'package:flutter/material.dart';

import 'tokens.dart';

// A flat slider matching the app's recessed-groove idiom: a `surfacePressed`
// track with a `border` outline, an `accent` fill, and a plain thin bar handle
// (no raised square thumb). No Material Slider — that brings its own overlay
// theme we deliberately suppress app-wide. Supports both orientations; vertical
// fills from the bottom (min) up to the top (max).
const double _trackThickness = 4;
const double _handleLong = 4;
const double _handleShort = 14;
const double _hoverLabelWidth = 36;
const double _hoverLabelHeight = 18;
const double _hoverLabelGap = 4;

class UiSlider extends StatefulWidget {
  const UiSlider({
    super.key,
    required this.value,
    required this.onChanged,
    this.onChangeStart,
    this.onChangeEnd,
    this.min = 0.0,
    this.max = 1.0,
    this.enabled = true,
    this.axis = Axis.horizontal,
    this.hoverLabel,
  });

  /// Current value, clamped into [min]..[max] for display.
  final double value;

  /// Fired continuously while dragging or on a track tap. Null disables.
  final ValueChanged<double>? onChanged;

  /// Fired once when an interaction begins (pointer down on the track).
  final ValueChanged<double>? onChangeStart;

  /// Fired once when an interaction ends (pointer up or cancel).
  final ValueChanged<double>? onChangeEnd;
  final double min;
  final double max;
  final bool enabled;

  /// Optional value text shown above the current thumb while a desktop pointer
  /// hovers the slider. Touch behavior remains unchanged.
  final String? hoverLabel;

  /// Layout orientation. Vertical sliders run low→high bottom→top.
  final Axis axis;

  @override
  State<UiSlider> createState() => _UiSliderState();
}

class _UiSliderState extends State<UiSlider> {
  final GlobalKey _trackKey = GlobalKey();
  int? _pointer;
  bool _hovered = false;

  bool get _interactive => widget.enabled && widget.onChanged != null;
  bool get _vertical => widget.axis == Axis.vertical;

  double get _span {
    final span = widget.max - widget.min;
    return span <= 0 ? 1 : span;
  }

  double get _fraction => ((widget.value - widget.min) / _span).clamp(0.0, 1.0);

  void _emitFromPosition(Offset localPosition) {
    final renderObject = _trackKey.currentContext?.findRenderObject();
    if (renderObject is! RenderBox) return;
    final size = renderObject.size;
    final double fraction;
    if (_vertical) {
      final travel = size.height - _handleLong;
      if (travel <= 0) return;
      // Bottom is min, so invert the y reading.
      fraction = (1 - (localPosition.dy - _handleLong / 2) / travel).clamp(
        0.0,
        1.0,
      );
    } else {
      final travel = size.width - _handleLong;
      if (travel <= 0) return;
      fraction = ((localPosition.dx - _handleLong / 2) / travel).clamp(
        0.0,
        1.0,
      );
    }
    widget.onChanged?.call(widget.min + fraction * _span);
  }

  void _handlePointerDown(PointerDownEvent event) {
    if (!_interactive || _pointer != null) return;
    setState(() => _pointer = event.pointer);
    widget.onChangeStart?.call(widget.value);
    _emitFromPosition(event.localPosition);
  }

  void _handlePointerMove(PointerMoveEvent event) {
    if (event.pointer != _pointer) return;
    _emitFromPosition(event.localPosition);
  }

  void _handlePointerUp(PointerUpEvent event) {
    if (event.pointer != _pointer) return;
    setState(() => _pointer = null);
    widget.onChangeEnd?.call(widget.value);
  }

  void _handlePointerCancel(PointerCancelEvent event) {
    if (event.pointer != _pointer) return;
    setState(() => _pointer = null);
    widget.onChangeEnd?.call(widget.value);
  }

  @override
  void didUpdateWidget(UiSlider oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!_interactive) _pointer = null;
  }

  @override
  Widget build(BuildContext context) {
    final enabled = _interactive;
    final grooveColor = enabled
        ? UiColors.surfacePressed
        : UiColors.disabledSurface;
    final grooveBorder = enabled ? UiColors.border : UiColors.disabledBorder;
    final fillColor = enabled ? UiColors.accent : UiColors.textMuted;
    final thumbColor = enabled ? UiColors.text : UiColors.textMuted;

    // Center the track along the cross axis, leaving room for the handle that
    // overhangs it.
    const crossInset = (_handleShort - _trackThickness) / 2;

    final hoverLabel = widget.hoverLabel?.trim();
    final showHoverLabel =
        !_vertical &&
        hoverLabel != null &&
        hoverLabel.isNotEmpty &&
        (_hovered || _pointer != null);
    return MouseRegion(
      cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: Listener(
        behavior: HitTestBehavior.opaque,
        onPointerDown: _handlePointerDown,
        onPointerMove: _handlePointerMove,
        onPointerUp: _handlePointerUp,
        onPointerCancel: _handlePointerCancel,
        child: SizedBox(
          key: _trackKey,
          width: _vertical ? _handleShort : null,
          height: _vertical ? null : _handleShort,
          child: LayoutBuilder(
            builder: (context, constraints) {
              final extent = _vertical
                  ? constraints.maxHeight
                  : constraints.maxWidth;
              final travel = (extent - _handleLong).clamp(0.0, double.infinity);
              final handleStart = travel * _fraction;
              final fillExtent = handleStart + _handleLong / 2;
              final hoverLabelLeft =
                  (handleStart + _handleLong / 2 - _hoverLabelWidth / 2)
                      .clamp(
                        0.0,
                        (extent - _hoverLabelWidth).clamp(0.0, double.infinity),
                      )
                      .toDouble();
              return Stack(
                clipBehavior: Clip.none,
                children: [
                  _groove(grooveColor, grooveBorder, crossInset),
                  _fill(fillColor, fillExtent, crossInset),
                  _handle(thumbColor, handleStart),
                  if (showHoverLabel)
                    Positioned(
                      key: const ValueKey<String>('ui-slider-hover-label'),
                      left: hoverLabelLeft,
                      bottom: _handleShort + _hoverLabelGap,
                      width: _hoverLabelWidth,
                      height: _hoverLabelHeight,
                      child: DecoratedBox(
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(UiRadii.sm),
                        ),
                        child: Center(
                          child: Text(
                            hoverLabel,
                            style: UiTypography.label.copyWith(
                              color: Colors.black87,
                              fontSize: 10,
                            ),
                          ),
                        ),
                      ),
                    ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }

  // The recessed track, spanning the full main-axis extent.
  Widget _groove(Color color, Color borderColor, double crossInset) {
    final decoration = BoxDecoration(
      color: color,
      borderRadius: BorderRadius.circular(_trackThickness / 2),
      border: Border.all(color: borderColor),
    );
    return Positioned(
      left: _vertical ? crossInset : 0,
      right: _vertical ? crossInset : 0,
      top: _vertical ? 0 : crossInset,
      bottom: _vertical ? 0 : crossInset,
      child: DecoratedBox(decoration: decoration),
    );
  }

  // The accent fill from the low end up to the handle.
  Widget _fill(Color color, double extent, double crossInset) {
    final decoration = BoxDecoration(
      color: color,
      borderRadius: BorderRadius.circular(_trackThickness / 2),
    );
    if (_vertical) {
      return Positioned(
        left: crossInset,
        right: crossInset,
        bottom: 0,
        height: extent,
        child: DecoratedBox(decoration: decoration),
      );
    }
    return Positioned(
      left: 0,
      top: crossInset,
      width: extent,
      height: _trackThickness,
      child: DecoratedBox(decoration: decoration),
    );
  }

  // A plain thin bar handle straddling the track.
  Widget _handle(Color color, double start) {
    final decoration = BoxDecoration(
      color: color,
      borderRadius: BorderRadius.circular(_handleLong / 2),
    );
    if (_vertical) {
      // start is measured from the low (bottom) end.
      return Positioned(
        left: 0,
        right: 0,
        bottom: start,
        height: _handleLong,
        child: DecoratedBox(decoration: decoration),
      );
    }
    return Positioned(
      left: start,
      top: 0,
      width: _handleLong,
      height: _handleShort,
      child: DecoratedBox(decoration: decoration),
    );
  }
}
