import 'dart:ui' show PointMode;

import 'package:flutter/material.dart';

import 'tokens.dart';

/// Fills its area with [color] and a faint dot grid, the canvas texture of
/// the Signal look.
class DotGridBackground extends StatelessWidget {
  const DotGridBackground({
    super.key,
    this.color = UiColors.background,
    this.dotColor = const Color(0x16FFFFFF),
    this.spacing = 18,
    this.dotRadius = 0.8,
    this.child,
  });

  final Color color;
  final Color dotColor;
  final double spacing;
  final double dotRadius;
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      painter: _DotGridPainter(
        color: color,
        dotColor: dotColor,
        spacing: spacing,
        dotRadius: dotRadius,
      ),
      child: child ?? const SizedBox.expand(),
    );
  }
}

class _DotGridPainter extends CustomPainter {
  const _DotGridPainter({
    required this.color,
    required this.dotColor,
    required this.spacing,
    required this.dotRadius,
  });

  final Color color;
  final Color dotColor;
  final double spacing;
  final double dotRadius;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(Offset.zero & size, Paint()..color = color);
    if (spacing <= 0) return;
    final dots = Paint()..color = dotColor;
    final points = <Offset>[];
    for (var y = spacing / 2; y < size.height; y += spacing) {
      for (var x = spacing / 2; x < size.width; x += spacing) {
        points.add(Offset(x, y));
      }
    }
    canvas.drawPoints(
      PointMode.points,
      points,
      dots
        ..strokeWidth = dotRadius * 2
        ..strokeCap = StrokeCap.round,
    );
  }

  @override
  bool shouldRepaint(_DotGridPainter oldDelegate) =>
      oldDelegate.color != color ||
      oldDelegate.dotColor != dotColor ||
      oldDelegate.spacing != spacing ||
      oldDelegate.dotRadius != dotRadius;
}

/// Small monospace uppercase tag, e.g. `● LIVE 3`.
class SignalTag extends StatelessWidget {
  const SignalTag({
    super.key,
    required this.label,
    this.color = UiColors.textSecondary,
    this.filled = false,
    this.dot = false,
    this.icon,
  });

  final String label;
  final Color color;

  /// Solid tag (signal colour background, dark text).
  final bool filled;

  /// Leading status dot.
  final bool dot;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final foreground = filled ? UiColors.onAccent : color;
    return Container(
      height: 20,
      padding: const EdgeInsets.symmetric(horizontal: 7),
      decoration: BoxDecoration(
        color: filled ? color : color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(UiRadii.pill),
        border: filled
            ? null
            : Border.all(color: color.withValues(alpha: 0.28)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (dot) ...[
            Container(
              width: 6,
              height: 6,
              decoration: BoxDecoration(
                color: foreground,
                shape: BoxShape.circle,
              ),
            ),
            const SizedBox(width: 5),
          ],
          if (icon != null) ...[
            Icon(icon, size: 11, color: foreground),
            const SizedBox(width: 4),
          ],
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              softWrap: false,
              style: UiTypography.overline.copyWith(
                color: foreground,
                fontSize: 9.5,
                letterSpacing: 0.9,
                height: 1,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// A frozen equalizer: a row of rounded bars of varying height. Used as the
/// "voice is happening here" glyph. Static on purpose so it never keeps a
/// ticker alive.
class EqualizerBars extends StatelessWidget {
  const EqualizerBars({
    super.key,
    this.color = UiColors.accent,
    this.height = 14,
    this.barWidth = 3,
    this.gap = 2,
    this.levels = const [0.45, 0.9, 0.6, 1, 0.35],
  });

  final Color color;
  final double height;
  final double barWidth;
  final double gap;
  final List<double> levels;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: height,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          for (var index = 0; index < levels.length; index++) ...[
            if (index > 0) SizedBox(width: gap),
            Container(
              width: barWidth,
              height: (height * levels[index]).clamp(barWidth, height),
              decoration: BoxDecoration(
                color: color,
                borderRadius: BorderRadius.circular(barWidth),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// A dot-matrix numeral with a monospace caption underneath.
class DotCounter extends StatelessWidget {
  const DotCounter({
    super.key,
    required this.value,
    required this.label,
    this.color = UiColors.text,
    this.size = 34,
  });

  final int value;
  final String label;
  final Color color;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          value.clamp(0, 99).toString().padLeft(2, '0'),
          style: UiTypography.dot.copyWith(color: color, fontSize: size),
        ),
        const SizedBox(height: 6),
        Text(label, style: UiTypography.overline),
      ],
    );
  }
}
