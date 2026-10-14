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

  // Geometry on a 64-unit grid. Every element keeps at least half a stroke
  // of clear space from its neighbours so nothing overlaps at any size, and
  // the mark's ink bounds are centred on the grid.
  static const _strokeWidth = 4.4;
  static const _cupTop = 30.5;
  static const _cupWidth = 7.2;
  static const _cupHeight = 18.0;
  static const _leftCupX = 4.0;
  static const _rightCupX = 52.8;

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.shortestSide / 64;
    canvas.save();
    canvas.scale(scale);
    final stroke = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = _strokeWidth
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..color = ink;

    // Headband: lands exactly on the centre of each cup's top edge, so its
    // caps merge into the cup outline instead of poking inside it.
    const leftCupCenter = _leftCupX + _cupWidth / 2;
    const rightCupCenter = _rightCupX + _cupWidth / 2;
    canvas.drawPath(
      Path()
        ..moveTo(leftCupCenter, _cupTop)
        ..arcToPoint(
          const Offset(rightCupCenter, _cupTop),
          radius: const Radius.elliptical(
            (rightCupCenter - leftCupCenter) / 2,
            22,
          ),
        ),
      stroke,
    );
    for (final left in const [_leftCupX, _rightCupX]) {
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          const Rect.fromLTWH(
            0,
            _cupTop,
            _cupWidth,
            _cupHeight,
          ).shift(Offset(left, 0)),
          const Radius.circular(_cupWidth / 2),
        ),
        stroke,
      );
    }

    // Speech bubble and tail as one outline: the bottom edge stops where the
    // tail opens, so no line runs through it.
    const r = Radius.circular(8);
    canvas.drawPath(
      Path()
        ..moveTo(26, 22.5)
        ..lineTo(38, 22.5)
        ..arcToPoint(const Offset(46, 30.5), radius: r)
        ..lineTo(46, 39.5)
        ..arcToPoint(const Offset(38, 47.5), radius: r)
        ..lineTo(34, 47.5)
        ..lineTo(26, 55.5)
        ..lineTo(26, 47.5)
        ..arcToPoint(const Offset(18, 39.5), radius: r)
        ..lineTo(18, 30.5)
        ..arcToPoint(const Offset(26, 22.5), radius: r)
        ..close(),
      stroke,
    );

    // Voice waveform, centred in the bubble.
    canvas.drawLine(const Offset(25, 32), const Offset(25, 38), stroke);
    canvas.drawLine(const Offset(32, 29), const Offset(32, 41), stroke);
    canvas.drawLine(const Offset(39, 32), const Offset(39, 38), stroke);
    canvas.restore();
  }

  @override
  bool shouldRepaint(_BrandMarkPainter oldDelegate) => oldDelegate.ink != ink;
}
