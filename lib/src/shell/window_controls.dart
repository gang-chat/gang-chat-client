import 'dart:async';

import 'package:flutter/material.dart';

import '../ui/ui.dart';
import 'desktop_window_controller.dart';

const appWindowControlsWidth = 134.0;
const appWindowCompactControlsWidth = 94.0;
const appWindowControlWidth = 34.0;
const appWindowControlHeight = 28.0;
const appWindowControlGap = 6.0;

class AppWindowDragRegion extends StatelessWidget {
  const AppWindowDragRegion({
    super.key,
    required this.windowController,
    required this.child,
    this.onDoubleTap,
  });

  final DesktopWindowController windowController;
  final Widget child;
  final VoidCallback? onDoubleTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      onPanStart: (_) => unawaited(windowController.startDragging()),
      onDoubleTap: onDoubleTap,
      child: child,
    );
  }
}

class AppWindowControls extends StatelessWidget {
  const AppWindowControls({
    super.key,
    required this.onMinimize,
    required this.onClose,
    this.maximized = false,
    this.onToggleMaximize,
    this.showMaximize = true,
  }) : assert(!showMaximize || onToggleMaximize != null);

  final bool maximized;
  final VoidCallback onMinimize;
  final VoidCallback? onToggleMaximize;
  final VoidCallback onClose;
  final bool showMaximize;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: showMaximize
          ? appWindowControlsWidth
          : appWindowCompactControlsWidth,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            AppWindowControlButton(
              tooltip: '最小化',
              icon: Icons.remove,
              onPressed: onMinimize,
            ),
            if (showMaximize) ...[
              const SizedBox(width: appWindowControlGap),
              AppWindowControlButton(
                tooltip: maximized ? '还原' : '最大化',
                icon: maximized ? Icons.filter_none : Icons.crop_square,
                onPressed: onToggleMaximize!,
              ),
            ],
            const SizedBox(width: appWindowControlGap),
            AppWindowControlButton(
              tooltip: '关闭',
              icon: Icons.close,
              danger: true,
              onPressed: onClose,
            ),
          ],
        ),
      ),
    );
  }
}

class AppWindowControlButton extends StatefulWidget {
  const AppWindowControlButton({
    super.key,
    required this.tooltip,
    required this.icon,
    required this.onPressed,
    this.danger = false,
  });

  final String tooltip;
  final IconData icon;
  final VoidCallback onPressed;
  final bool danger;

  @override
  State<AppWindowControlButton> createState() => _AppWindowControlButtonState();
}

class _AppWindowControlButtonState extends State<AppWindowControlButton> {
  bool _hovered = false;
  bool _pressed = false;

  void _setHovered(bool hovered) {
    if (_hovered == hovered) return;
    setState(() => _hovered = hovered);
  }

  void _setPressed(bool pressed) {
    if (_pressed == pressed) return;
    setState(() => _pressed = pressed);
  }

  @override
  Widget build(BuildContext context) {
    final active = _hovered || _pressed;
    final background = active
        ? widget.danger
              ? const Color(0xFF332126)
              : UiColors.surface
        : Colors.transparent;
    final border = active
        ? widget.danger
              ? UiColors.dangerBorder
              : UiColors.border
        : Colors.transparent;
    final foreground = active && widget.danger
        ? UiColors.danger
        : UiColors.textSecondary;

    return UiTooltip(
      message: widget.tooltip,
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => _setHovered(true),
        onExit: (_) {
          _setHovered(false);
          _setPressed(false);
        },
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTapDown: (_) => _setPressed(true),
          onTapCancel: () => _setPressed(false),
          onTapUp: (_) => _setPressed(false),
          onTap: widget.onPressed,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 90),
            curve: Curves.easeOutCubic,
            width: appWindowControlWidth,
            height: appWindowControlHeight,
            decoration: BoxDecoration(
              color: background,
              borderRadius: BorderRadius.circular(UiRadii.sm),
              border: Border.all(color: border),
            ),
            child: Center(
              child: Icon(widget.icon, size: 16, color: foreground),
            ),
          ),
        ),
      ),
    );
  }
}
