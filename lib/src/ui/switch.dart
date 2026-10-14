import 'package:flutter/material.dart';

import 'tooltip.dart';

import 'tokens.dart';

const double _switchWidth = 42;
const double _switchHeight = 24;
const double _switchThumbSize = 18;
const double _switchHorizontalInset = 3;
const Duration _switchDuration = Duration(milliseconds: 160);

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
    final trackColor = !enabled
        ? (active
              ? UiColors.accentStrong.withValues(alpha: 0.35)
              : UiColors.disabledSurface)
        : active
        ? UiColors.accentStrong
        : const Color(0xFF2A2A2F);
    final trackBorder = active || !enabled
        ? Colors.transparent
        : UiColors.borderStrong;
    final thumbColor = !enabled
        ? UiColors.textMuted
        : active
        ? UiColors.onAccent
        : const Color(0xFF8E8E95);
    const thumbTop = (_switchHeight - _switchThumbSize) / 2;

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
              Positioned.fill(
                child: AnimatedContainer(
                  duration: _switchDuration,
                  curve: Curves.easeOutCubic,
                  decoration: BoxDecoration(
                    color: trackColor,
                    borderRadius: BorderRadius.circular(UiRadii.pill),
                    border: Border.all(color: trackBorder),
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
                height: _switchThumbSize,
                child: AnimatedContainer(
                  duration: _switchDuration,
                  curve: Curves.easeOutCubic,
                  decoration: BoxDecoration(
                    color: thumbColor,
                    shape: BoxShape.circle,
                    boxShadow: enabled ? UiShadows.raised : null,
                  ),
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
