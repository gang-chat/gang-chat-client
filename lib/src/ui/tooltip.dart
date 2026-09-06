import 'package:flutter/material.dart';

/// Material [Tooltip] inside its own semantics boundary.
///
/// [Tooltip] shows its bubble through an [OverlayPortal], which grafts the
/// bubble into the semantics tree by tagging the anchor with a
/// `traversalParentIdentifier`. That tag lands on whichever semantics node
/// absorbs the anchor. When several portal anchors (tooltips, hover cards,
/// docked panels) merge into one node, only one identifier survives and the
/// other portals' bubbles become orphan nodes; Flutter keeps re-sending them
/// and the Windows accessibility bridge keeps rejecting every later update in
/// that subtree (`Failed to update ui::AXTree ... will not be in the tree`).
/// Giving each tooltip a container node of its own keeps the identifiers
/// apart. Parameter names mirror [Tooltip] so call sites are interchangeable.
class UiTooltip extends StatefulWidget {
  const UiTooltip({
    super.key,
    required this.message,
    required this.child,
    this.waitDuration,
    this.exitDuration,
    this.preferBelow,
    this.verticalOffset,
    this.triggerMode,
  });

  final String message;
  final Widget child;
  final Duration? waitDuration;
  final Duration? exitDuration;
  final bool? preferBelow;
  final double? verticalOffset;
  final TooltipTriggerMode? triggerMode;

  /// Dismisses every open tooltip on screen.
  static void dismissAllToolTips() => Tooltip.dismissAllToolTips();

  @override
  State<UiTooltip> createState() => UiTooltipState();
}

class UiTooltipState extends State<UiTooltip> {
  final GlobalKey<TooltipState> _tooltipKey = GlobalKey<TooltipState>();

  /// Shows the tooltip programmatically; mirrors [TooltipState].
  bool ensureTooltipVisible() {
    return _tooltipKey.currentState?.ensureTooltipVisible() ?? false;
  }

  @override
  Widget build(BuildContext context) {
    if (widget.message.isEmpty) return widget.child;
    return Semantics(
      container: true,
      child: Tooltip(
        key: _tooltipKey,
        message: widget.message,
        waitDuration: widget.waitDuration,
        exitDuration: widget.exitDuration,
        preferBelow: widget.preferBelow,
        verticalOffset: widget.verticalOffset,
        triggerMode: widget.triggerMode,
        child: widget.child,
      ),
    );
  }
}
