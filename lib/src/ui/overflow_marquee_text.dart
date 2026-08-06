import 'dart:async';

import 'package:flutter/material.dart';

/// Keeps a single-line label static while it fits, then moves it between both
/// overflow boundaries with a short pause at either end.
///
/// This is shared by compact identity and media controls so they use the same
/// overflow threshold, speed, and pause timing instead of falling back to
/// ellipses or maintaining separate marquee implementations.
class OverflowMarqueeText extends StatefulWidget {
  const OverflowMarqueeText({
    super.key,
    required this.text,
    required this.style,
    this.trackKey,
  });

  final String text;
  final TextStyle style;

  /// Optional key for tests or callers that need to inspect the moving track.
  final Key? trackKey;

  @override
  State<OverflowMarqueeText> createState() => _OverflowMarqueeTextState();
}

class _OverflowMarqueeTextState extends State<OverflowMarqueeText>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  Timer? _pauseTimer;
  double _configuredViewportWidth = -1;
  double _scrollDistance = 0;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this)
      ..addStatusListener(_handleAnimationStatus);
  }

  @override
  void didUpdateWidget(OverflowMarqueeText oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.text != widget.text || oldWidget.style != widget.style) {
      _configuredViewportWidth = -1;
    }
  }

  void _handleAnimationStatus(AnimationStatus status) {
    if (_scrollDistance <= 0) return;
    if (status == AnimationStatus.completed) {
      _scheduleAnimation(forward: false);
    } else if (status == AnimationStatus.dismissed) {
      _scheduleAnimation(forward: true);
    }
  }

  void _scheduleAnimation({required bool forward}) {
    _pauseTimer?.cancel();
    _pauseTimer = Timer(const Duration(milliseconds: 850), () {
      if (!mounted || _scrollDistance <= 0) return;
      if (forward) {
        unawaited(_controller.forward());
      } else {
        unawaited(_controller.reverse());
      }
    });
  }

  void _configure(double viewportWidth, double scrollDistance) {
    if ((_configuredViewportWidth - viewportWidth).abs() < 0.1 &&
        (_scrollDistance - scrollDistance).abs() < 0.1) {
      return;
    }
    _configuredViewportWidth = viewportWidth;
    _scrollDistance = scrollDistance;
    _pauseTimer?.cancel();
    _controller.stop();
    _controller.reset();
    if (scrollDistance <= 0) return;
    final milliseconds = (scrollDistance / 28 * 1000)
        .clamp(1200.0, 10000.0)
        .round();
    _controller.duration = Duration(milliseconds: milliseconds);
    _scheduleAnimation(forward: true);
  }

  @override
  void dispose() {
    _pauseTimer?.cancel();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final textDirection = Directionality.of(context);
    final textScaler = MediaQuery.textScalerOf(context);
    return LayoutBuilder(
      builder: (context, constraints) {
        final painter = TextPainter(
          text: TextSpan(text: widget.text, style: widget.style),
          maxLines: 1,
          textDirection: textDirection,
          textScaler: textScaler,
        )..layout();
        final viewportWidth = constraints.maxWidth;
        final scrollDistance = (painter.width - viewportWidth)
            .clamp(0.0, double.infinity)
            .toDouble();
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) _configure(viewportWidth, scrollDistance);
        });
        if (scrollDistance <= 0) {
          return Text(
            widget.text,
            maxLines: 1,
            softWrap: false,
            overflow: TextOverflow.clip,
            style: widget.style,
          );
        }
        return SizedBox(
          height: painter.height,
          child: ClipRect(
            child: AnimatedBuilder(
              animation: _controller,
              child: OverflowBox(
                alignment: Alignment.centerLeft,
                minWidth: painter.width,
                maxWidth: painter.width,
                child: Text(
                  widget.text,
                  maxLines: 1,
                  softWrap: false,
                  overflow: TextOverflow.visible,
                  style: widget.style,
                ),
              ),
              builder: (context, child) => Transform.translate(
                key: widget.trackKey,
                offset: Offset(-scrollDistance * _controller.value, 0),
                child: child,
              ),
            ),
          ),
        );
      },
    );
  }
}
