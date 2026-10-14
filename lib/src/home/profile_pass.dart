import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../ui/ui.dart';

/// Building blocks of the "pass" look shared by the user and room hover
/// cards: a printed ticket with a colour banner, an identity block, a
/// perforated tear line and a stub carrying the machine-readable bits
/// (UID / RID with a barcode).
class ProfilePass extends StatelessWidget {
  const ProfilePass({
    super.key,
    required this.color,
    required this.kicker,
    required this.code,
    required this.avatar,
    required this.identity,
    this.badges,
    this.body = const [],
    this.stub = const [],
    this.avatarSize = 60,
    this.avatarRounded = false,
  });

  /// Banner tint, normally the avatar's preset colour.
  final Color color;

  /// Small monospace label printed in the banner's top-left corner.
  final String kicker;

  /// Big faint dot-matrix code printed across the banner.
  final String code;
  final Widget avatar;
  final double avatarSize;

  /// Rounded-square avatar (rooms) instead of a circle (people).
  final bool avatarRounded;

  /// Badges aligned with the avatar, right of it, below the banner.
  final Widget? badges;

  /// Name and handle block under the avatar.
  final Widget identity;

  /// Everything above the tear line, after the identity block.
  final List<Widget> body;

  /// Everything below the tear line.
  final List<Widget> stub;

  static const double bannerHeight = 74;
  static const double horizontalPadding = 16;

  @override
  Widget build(BuildContext context) {
    final overlap = avatarSize * 0.55;
    return Padding(
      padding: const EdgeInsets.all(1),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(UiRadii.lg - 1),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SizedBox(
              height: bannerHeight + overlap,
              child: Stack(
                clipBehavior: Clip.none,
                children: [
                  Positioned(
                    left: 0,
                    right: 0,
                    top: 0,
                    height: bannerHeight,
                    child: _PassBanner(
                      color: color,
                      kicker: kicker,
                      code: code,
                    ),
                  ),
                  Positioned(
                    left: horizontalPadding - 4,
                    top: bannerHeight - avatarSize + overlap - 4,
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: UiColors.surfaceRaised,
                        borderRadius: BorderRadius.circular(
                          avatarRounded
                              ? avatarSize * 0.3 + 4
                              : (avatarSize + 8) / 2,
                        ),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.all(4),
                        child: avatar,
                      ),
                    ),
                  ),
                  if (badges != null)
                    Positioned(
                      left: horizontalPadding + avatarSize + 12,
                      right: horizontalPadding - 2,
                      top: bannerHeight + 8,
                      child: Align(
                        alignment: Alignment.centerRight,
                        child: badges,
                      ),
                    ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(
                horizontalPadding,
                12,
                horizontalPadding,
                0,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [identity, ...body],
              ),
            ),
            if (stub.isNotEmpty) ...[
              const SizedBox(height: 14),
              const PassPerforation(),
              Padding(
                padding: const EdgeInsets.fromLTRB(
                  horizontalPadding,
                  12,
                  horizontalPadding,
                  horizontalPadding,
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: stub,
                ),
              ),
            ] else
              const SizedBox(height: horizontalPadding),
          ],
        ),
      ),
    );
  }
}

class _PassBanner extends StatelessWidget {
  const _PassBanner({
    required this.color,
    required this.kicker,
    required this.code,
  });

  final Color color;
  final String kicker;
  final String code;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            Color.lerp(color, UiColors.surfaceRaised, 0.15)!,
            Color.lerp(color, UiColors.surfaceRaised, 0.72)!,
          ],
        ),
      ),
      child: Stack(
        children: [
          const Positioned.fill(
            child: DotGridBackground(
              color: Colors.transparent,
              dotColor: Color(0x2EFFFFFF),
              spacing: 7,
              dotRadius: 0.75,
            ),
          ),
          Positioned(
            right: -4,
            bottom: -10,
            child: Text(
              code,
              maxLines: 1,
              softWrap: false,
              style: UiTypography.dot.copyWith(
                fontSize: 46,
                height: 1,
                color: Colors.white.withValues(alpha: 0.2),
              ),
            ),
          ),
          Positioned(
            left: ProfilePass.horizontalPadding,
            top: 11,
            right: ProfilePass.horizontalPadding,
            child: Text(
              kicker,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: UiTypography.overline.copyWith(
                color: Colors.white.withValues(alpha: 0.82),
                fontSize: 9.5,
                letterSpacing: 1.6,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// The tear line between a pass and its stub: a dashed rule with a punched
/// half-hole on either edge.
class PassPerforation extends StatelessWidget {
  const PassPerforation({super.key});

  @override
  Widget build(BuildContext context) {
    return const SizedBox(
      height: 14,
      width: double.infinity,
      child: CustomPaint(painter: _PerforationPainter()),
    );
  }
}

class _PerforationPainter extends CustomPainter {
  const _PerforationPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final y = size.height / 2;
    const notchRadius = 9.0;
    final dash = Paint()
      ..color = UiColors.borderStrong
      ..strokeWidth = 1.2
      ..strokeCap = StrokeCap.round;
    for (var x = notchRadius + 6; x < size.width - notchRadius - 6; x += 7) {
      canvas.drawLine(
        Offset(x, y),
        Offset(math.min(x + 3, size.width), y),
        dash,
      );
    }
    final hole = Paint()..color = UiColors.background;
    final rim = Paint()
      ..color = UiColors.borderStrong
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1;
    for (final x in [0.0, size.width]) {
      canvas.drawCircle(Offset(x, y), notchRadius, hole);
      canvas.drawCircle(Offset(x, y), notchRadius, rim);
    }
  }

  @override
  bool shouldRepaint(_PerforationPainter oldDelegate) => false;
}

/// A decorative barcode derived from [value], so each pass prints its own.
class PassBarcode extends StatelessWidget {
  const PassBarcode({
    super.key,
    required this.value,
    this.height = 22,
    this.width = 74,
  });

  final String value;
  final double height;
  final double width;

  @override
  Widget build(BuildContext context) {
    return ExcludeSemantics(
      child: SizedBox(
        width: width,
        height: height,
        child: CustomPaint(painter: _BarcodePainter(value)),
      ),
    );
  }
}

class _BarcodePainter extends CustomPainter {
  const _BarcodePainter(this.value);

  final String value;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = UiColors.textSecondary;
    var seed = 7;
    for (final unit in value.codeUnits) {
      seed = (seed * 31 + unit) & 0x7fffffff;
    }
    var x = 0.0;
    var index = 0;
    while (x < size.width) {
      seed = (seed * 1103515245 + 12345) & 0x7fffffff;
      final bar = 1.0 + (seed >> 8) % 3;
      final gap = 1.0 + (seed >> 12) % 2;
      if (x + bar > size.width) break;
      final tall = index % 9 == 0;
      canvas.drawRect(
        Rect.fromLTWH(x, tall ? 0 : 2, bar, size.height - (tall ? 0 : 4)),
        paint,
      );
      x += bar + gap;
      index += 1;
    }
  }

  @override
  bool shouldRepaint(_BarcodePainter oldDelegate) => oldDelegate.value != value;
}

/// Monospace label used for the small headings inside a pass.
class PassLabel extends StatelessWidget {
  const PassLabel(this.text, {super.key, this.trailing});

  final String text;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Flexible(
          child: Text(
            text,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: UiTypography.overline.copyWith(letterSpacing: 1),
          ),
        ),
        if (trailing != null) ...[const SizedBox(width: 6), trailing!],
      ],
    );
  }
}

/// The last 4–5 characters of an identifier, used as the banner code.
String passCode(String? value, {String fallback = '----'}) {
  final trimmed = value?.trim() ?? '';
  if (trimmed.isEmpty) return fallback;
  return trimmed.length <= 5
      ? trimmed.toUpperCase()
      : trimmed.substring(trimmed.length - 5).toUpperCase();
}
