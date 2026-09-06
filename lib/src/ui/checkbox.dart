import 'package:flutter/material.dart';

import 'tooltip.dart';

import 'surface.dart';
import 'tokens.dart';

const double _checkboxSize = 18;
const double _checkboxDepth = 2;

class UiCheckbox extends StatelessWidget {
  const UiCheckbox({
    super.key,
    required this.value,
    required this.onChanged,
    this.enabled = true,
    this.tooltip,
    this.semanticLabel,
  });

  final bool value;
  final ValueChanged<bool>? onChanged;
  final bool enabled;
  final String? tooltip;
  final String? semanticLabel;

  bool get _interactive => enabled && onChanged != null;

  void _toggle() {
    if (!_interactive) return;
    onChanged?.call(!value);
  }

  @override
  Widget build(BuildContext context) {
    final active = value;
    final foreground = !active
        ? Colors.transparent
        : !_interactive
        ? UiColors.textMuted
        : UiColors.controlAccent;
    final background = active ? UiColors.selected : UiColors.surface;
    final border = active ? UiColors.selectedBorder : UiColors.border;

    Widget result = PressableSurface(
      width: _checkboxSize,
      height: _checkboxSize,
      enabled: _interactive,
      interactive: _interactive,
      onPressed: _toggle,
      hoverLift: 0,
      baseDepth: _checkboxDepth,
      pressDepth: _checkboxDepth,
      borderRadius: UiRadii.sm,
      padding: EdgeInsets.zero,
      mouseCursor: _interactive
          ? SystemMouseCursors.click
          : SystemMouseCursors.basic,
      backgroundColor: background,
      selectedBackgroundColor: UiColors.selected,
      pressedBackgroundColor: UiColors.surfacePressed,
      disabledBackgroundColor: UiColors.disabledSurface,
      borderColor: border,
      selectedBorderColor: UiColors.selectedBorder,
      disabledBorderColor: UiColors.disabledBorder,
      child: Center(
        child: Icon(Icons.check_rounded, size: 15, color: foreground),
      ),
    );

    final tooltip = this.tooltip;
    if (tooltip != null && tooltip.isNotEmpty) {
      result = UiTooltip(message: tooltip, child: result);
    }

    return Semantics(
      label: semanticLabel ?? tooltip,
      toggled: value,
      enabled: _interactive,
      button: true,
      onTap: _interactive ? _toggle : null,
      child: result,
    );
  }
}
