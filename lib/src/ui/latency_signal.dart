import 'package:flutter/material.dart';

import 'tooltip.dart';

class LatencySignalBadge extends StatefulWidget {
  const LatencySignalBadge({
    super.key,
    required this.activeBars,
    required this.activeColor,
    required this.tooltip,
    this.size = 18,
  });

  final int activeBars;
  final Color activeColor;
  final String tooltip;
  final double size;

  @override
  State<LatencySignalBadge> createState() => _LatencySignalBadgeState();
}

class _LatencySignalBadgeState extends State<LatencySignalBadge> {
  static const _hoverWaitDuration = Duration(milliseconds: 350);
  static const _pinnedExitDuration = Duration(days: 365);

  final GlobalKey<UiTooltipState> _tooltipKey = GlobalKey<UiTooltipState>();
  bool _pinned = false;

  void _togglePinned() {
    if (_pinned) {
      setState(() => _pinned = false);
      UiTooltip.dismissAllToolTips();
      return;
    }
    setState(() => _pinned = true);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_pinned) return;
      _tooltipKey.currentState?.ensureTooltipVisible();
    });
  }

  void _handleTapOutside(PointerDownEvent event) {
    if (!_pinned) return;
    setState(() => _pinned = false);
    UiTooltip.dismissAllToolTips();
  }

  @override
  Widget build(BuildContext context) {
    final bars = widget.activeBars.clamp(0, 3);
    return TapRegion(
      onTapOutside: _handleTapOutside,
      child: GestureDetector(
        behavior: HitTestBehavior.translucent,
        onTap: _togglePinned,
        child: UiTooltip(
          key: _tooltipKey,
          message: widget.tooltip,
          waitDuration: _hoverWaitDuration,
          triggerMode: TooltipTriggerMode.manual,
          // Material Tooltip has no persistent-hover flag. A pinned tooltip
          // therefore keeps its hover-exit timer dormant; any outside pointer
          // down still dismisses it immediately through the normal route.
          exitDuration: _pinned ? _pinnedExitDuration : null,
          child: SizedBox(
            key: const ValueKey('latency-signal-badge'),
            width: widget.size,
            height: widget.size - 2,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(3.5, 3, 3.5, 3),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  for (var index = 1; index <= 3; index++) ...[
                    _SignalBar(
                      index: index,
                      active: bars >= index,
                      color: widget.activeColor,
                    ),
                    if (index != 3) const SizedBox(width: 1),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _SignalBar extends StatelessWidget {
  const _SignalBar({
    required this.index,
    required this.active,
    required this.color,
  });

  final int index;
  final bool active;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final fill = active ? color : const Color(0xFF8A93A3);
    return DecoratedBox(
      key: ValueKey('latency-signal-bar-$index'),
      decoration: BoxDecoration(
        color: fill,
        borderRadius: BorderRadius.circular(999),
      ),
      child: SizedBox(width: 3, height: 3.0 + index * 2.0),
    );
  }
}
