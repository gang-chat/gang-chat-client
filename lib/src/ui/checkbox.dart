import 'package:flutter/material.dart';

import 'tooltip.dart';

import 'surface.dart';
import 'tokens.dart';

const double _checkboxSize = 18;

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
    final interactive = _interactive;
    final background = !active
        ? UiColors.fieldFill
        : interactive
        ? UiColors.accentStrong
        : UiColors.accentStrong.withValues(alpha: 0.35);
    final border = active
        ? Colors.transparent
        : interactive
        ? UiColors.borderStrong
        : UiColors.disabledBorder;

    Widget result = PressableSurface(
      width: _checkboxSize,
      height: _checkboxSize,
      enabled: interactive,
      interactive: interactive,
      onPressed: _toggle,
      borderRadius: UiRadii.xs + 1,
      padding: EdgeInsets.zero,
      mouseCursor: interactive
          ? SystemMouseCursors.click
          : SystemMouseCursors.basic,
      backgroundColor: background,
      selectedBackgroundColor: background,
      disabledBackgroundColor: background,
      borderColor: border,
      selectedBorderColor: border,
      disabledBorderColor: border,
      child: Center(
        child: Icon(
          Icons.check_rounded,
          size: 14,
          color: !active
              ? Colors.transparent
              : interactive
              ? UiColors.onAccent
              : UiColors.textSecondary,
        ),
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
