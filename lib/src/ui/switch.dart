import 'package:flutter/material.dart';

import 'tooltip.dart';

import 'surface.dart';
import 'tokens.dart';

const double _switchWidth = 56;
const double _switchHeight = 32;
const double _switchThumbSize = 18;
const double _switchHorizontalInset = 7;
const double _switchThumbDepth = 3;
const Duration _switchDuration = Duration(milliseconds: 140);

class UiSwitch extends StatefulWidget {
  const UiSwitch({
    super.key,
    required this.value,
    required this.onChanged,
    this.enabled = true,
    this.tooltip,
  });

  final bool value;
  final ValueChanged<bool>? onChanged;
  final bool enabled;
  final String? tooltip;

  @override
  State<UiSwitch> createState() => _UiSwitchState();
}

class _UiSwitchState extends State<UiSwitch> {
  final GlobalKey _hitTargetKey = GlobalKey();
  int? _pressedPointer;

  bool get _interactive => widget.enabled && widget.onChanged != null;

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
    if (!_interactive || _pressedPointer != null) return;
    _pressedPointer = event.pointer;
  }

  void _handlePointerUp(PointerUpEvent event) {
    if (event.pointer != _pressedPointer) return;
    final shouldToggle = _isInsideHitTarget(event.localPosition);
    _pressedPointer = null;
    if (shouldToggle) widget.onChanged?.call(!widget.value);
  }

  void _handlePointerCancel(PointerCancelEvent event) {
    if (event.pointer != _pressedPointer) return;
    _pressedPointer = null;
  }

  void _toggle() {
    if (!_interactive) return;
    widget.onChanged?.call(!widget.value);
  }

  @override
  void didUpdateWidget(UiSwitch oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!_interactive && _pressedPointer != null) {
      _pressedPointer = null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final active = widget.value;
    final enabled = _interactive;
    final trackBackground = enabled
        ? UiColors.surfacePressed
        : UiColors.disabledSurface;
    final borderColor = enabled ? UiColors.border : UiColors.disabledBorder;
    final radius = BorderRadius.circular(UiRadii.md);
    final thumbBackground = !enabled
        ? UiColors.disabledSurface
        : active
        ? UiColors.selected
        : UiColors.surface;
    final thumbBorder = !enabled
        ? UiColors.disabledBorder
        : active
        ? UiColors.selectedBorder
        : UiColors.border;
    final thumbTop =
        ((_switchHeight - _switchThumbSize) / 2) - _switchThumbDepth;

    Widget result = MouseRegion(
      cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
      child: Listener(
        key: _hitTargetKey,
        behavior: HitTestBehavior.opaque,
        onPointerDown: _handlePointerDown,
        onPointerUp: _handlePointerUp,
        onPointerCancel: _handlePointerCancel,
        child: SizedBox(
          width: _switchWidth,
          height: _switchHeight,
          child: Stack(
            clipBehavior: Clip.none,
            children: [
              Positioned(
                left: 0,
                right: 0,
                top: 0,
                bottom: 0,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: trackBackground,
                    borderRadius: radius,
                    border: Border.all(color: borderColor),
                  ),
                ),
              ),
              AnimatedPositioned(
                duration: _switchDuration,
                curve: Curves.easeOutCubic,
                left: active
                    ? _switchWidth - _switchThumbSize - _switchHorizontalInset
                    : _switchHorizontalInset,
                top: thumbTop,
                width: _switchThumbSize,
                height: _switchThumbSize + _switchThumbDepth,
                child: PressableSurface(
                  width: _switchThumbSize,
                  height: _switchThumbSize,
                  enabled: enabled,
                  interactive: enabled,
                  hoverEffect: false,
                  hoverLift: 0,
                  baseDepth: _switchThumbDepth,
                  pressDepth: _switchThumbDepth,
                  borderRadius: UiRadii.sm,
                  padding: EdgeInsets.zero,
                  mouseCursor: enabled
                      ? SystemMouseCursors.click
                      : SystemMouseCursors.basic,
                  backgroundColor: thumbBackground,
                  selectedBackgroundColor: thumbBackground,
                  pressedBackgroundColor: UiColors.surfacePressed,
                  disabledBackgroundColor: UiColors.disabledSurface,
                  borderColor: thumbBorder,
                  selectedBorderColor: thumbBorder,
                  disabledBorderColor: UiColors.disabledBorder,
                  child: const SizedBox.expand(),
                ),
              ),
            ],
          ),
        ),
      ),
    );

    final tooltip = widget.tooltip;
    if (tooltip != null && tooltip.isNotEmpty) {
      result = UiTooltip(message: tooltip, child: result);
    }

    return Semantics(
      toggled: widget.value,
      enabled: _interactive,
      button: true,
      onTap: _interactive ? _toggle : null,
      child: result,
    );
  }
}
