import 'package:flutter/material.dart';

import 'tokens.dart';

/// The Gang Chat mark (headset wrapped around a speech bubble with a voice
/// waveform), painted as vectors so it always follows the current palette.
class BrandMark extends StatelessWidget {
  const BrandMark({super.key, this.size = 28, this.tile = true});

  static const _ink = UiColors.onAccent;

  final double size;

  /// Draws the mark on a rounded tile. Without the tile only the strokes are
  /// painted, for places that already provide a background.
  final bool tile;

  @override
  Widget build(BuildContext context) {
    final mark = CustomPaint(
      size: Size.square(size),
      painter: _BrandMarkPainter(ink: tile ? _ink : UiColors.accent),
    );
    if (!tile) return SizedBox.square(dimension: size, child: mark);
    return Container(
      width: size,
      height: size,
      padding: EdgeInsets.all(size * 0.12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(size * 0.3),
        color: UiColors.accentStrong,
        boxShadow: UiShadows.glow(UiColors.accent, strength: 0.35),
      ),
      child: mark,
    );
  }
}

class _BrandMarkPainter extends CustomPainter {
  const _BrandMarkPainter({required this.ink});

  final Color ink;

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.shortestSide / 64;
    canvas.save();
    canvas.scale(scale);
    final stroke = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 4.8
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..color = ink;
    final bubble = RRect.fromRectAndRadius(
      const Rect.fromLTWH(16, 16, 32, 30.72),
      const Radius.circular(11.52),
    );
    canvas.drawRRect(bubble, stroke);
    canvas.drawPath(
      Path()
        ..moveTo(25.6, 45.44)
        ..lineTo(25.6, 55.04)
        ..lineTo(35.2, 46.08),
      stroke,
    );
    canvas.drawPath(
      Path()
        ..moveTo(9.6, 28.8)
        ..arcToPoint(
          const Offset(54.4, 28.8),
          radius: const Radius.elliptical(22.4, 23.04),
        ),
      stroke,
    );
    for (final left in const [4.48, 48.0]) {
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(left, 26.88, 11.52, 19.2),
          const Radius.circular(5.12),
        ),
        stroke,
      );
    }
    canvas.drawLine(
      const Offset(23.68, 29.44),
      const Offset(23.68, 35.84),
      stroke,
    );
    canvas.drawLine(const Offset(32, 24.32), const Offset(32, 40.96), stroke);
    canvas.drawLine(
      const Offset(40.32, 29.44),
      const Offset(40.32, 35.84),
      stroke,
    );
    canvas.restore();
  }

  @override
  bool shouldRepaint(_BrandMarkPainter oldDelegate) => oldDelegate.ink != ink;
}
