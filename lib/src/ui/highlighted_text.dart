import 'package:flutter/material.dart';

import 'tokens.dart';

class HighlightedText extends StatelessWidget {
  const HighlightedText({
    super.key,
    required this.text,
    required this.query,
    this.style,
    this.highlightStyle,
    this.maxLines,
    this.overflow,
    this.textAlign,
    this.softWrap,
  });

  final String text;
  final String query;
  final TextStyle? style;
  final TextStyle? highlightStyle;
  final int? maxLines;
  final TextOverflow? overflow;
  final TextAlign? textAlign;
  final bool? softWrap;

  @override
  Widget build(BuildContext context) {
    final effectiveStyle = style ?? DefaultTextStyle.of(context).style;
    final spans = _highlightedSpans(
      text: text,
      query: query,
      style: effectiveStyle,
      highlightStyle: highlightStyle ?? _defaultHighlightStyle(effectiveStyle),
    );
    if (spans == null) {
      return Text(
        text,
        maxLines: maxLines,
        overflow: overflow,
        textAlign: textAlign,
        softWrap: softWrap,
        style: effectiveStyle,
      );
    }

    return Text.rich(
      TextSpan(style: effectiveStyle, children: spans),
      maxLines: maxLines,
      overflow: overflow,
      textAlign: textAlign,
      softWrap: softWrap,
    );
  }
}

/// Keeps short labels on their natural line count, then allows longer labels
/// to wrap in full while reducing the font only when the comfortable line
/// count is exceeded.
class AdaptiveHighlightedText extends StatelessWidget {
  const AdaptiveHighlightedText({
    super.key,
    required this.text,
    required this.query,
    this.style,
    this.highlightStyle,
    this.comfortableLines = 2,
    this.maximumFontSizeReduction = 2,
    this.minimumFontSize = 10,
    this.textAlign,
  }) : assert(comfortableLines > 0),
       assert(maximumFontSizeReduction > 0),
       assert(minimumFontSize > 0);

  final String text;
  final String query;
  final TextStyle? style;
  final TextStyle? highlightStyle;
  final int comfortableLines;
  final double maximumFontSizeReduction;
  final double minimumFontSize;
  final TextAlign? textAlign;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final baseStyle = style ?? DefaultTextStyle.of(context).style;
        var effectiveStyle = baseStyle;
        final width = constraints.maxWidth;
        final baseSize = baseStyle.fontSize;
        if (width.isFinite && width > 0 && baseSize != null) {
          final painter = TextPainter(
            text: TextSpan(text: text.isEmpty ? ' ' : text, style: baseStyle),
            textDirection: Directionality.of(context),
            textScaler: MediaQuery.textScalerOf(context),
          )..layout(maxWidth: width);
          final lineCount = painter.computeLineMetrics().length;
          if (lineCount > comfortableLines) {
            final reduction = (lineCount - comfortableLines).toDouble().clamp(
              1.0,
              maximumFontSizeReduction,
            );
            effectiveStyle = baseStyle.copyWith(
              fontSize: (baseSize - reduction).clamp(minimumFontSize, baseSize),
            );
          }
        }
        return HighlightedText(
          text: text,
          query: query,
          style: effectiveStyle,
          highlightStyle: highlightStyle,
          textAlign: textAlign,
          softWrap: true,
        );
      },
    );
  }
}

TextStyle _defaultHighlightStyle(TextStyle baseStyle) {
  return baseStyle.copyWith(
    color: UiColors.accent,
    fontWeight: FontWeight.w700,
    backgroundColor: UiColors.accent.withValues(alpha: 0.2),
  );
}

List<TextSpan>? _highlightedSpans({
  required String text,
  required String query,
  required TextStyle style,
  required TextStyle highlightStyle,
}) {
  final needle = query.trim();
  if (text.isEmpty || needle.isEmpty) return null;

  final lowerText = text.toLowerCase();
  final lowerNeedle = needle.toLowerCase();
  var searchStart = 0;
  var hasMatch = false;
  final spans = <TextSpan>[];

  while (searchStart < text.length) {
    final matchStart = lowerText.indexOf(lowerNeedle, searchStart);
    if (matchStart < 0) break;
    final matchEnd = matchStart + needle.length;
    if (matchStart > searchStart) {
      spans.add(TextSpan(text: text.substring(searchStart, matchStart)));
    }
    spans.add(
      TextSpan(
        text: text.substring(matchStart, matchEnd),
        style: highlightStyle,
      ),
    );
    hasMatch = true;
    searchStart = matchEnd;
  }

  if (!hasMatch) return null;
  if (searchStart < text.length) {
    spans.add(TextSpan(text: text.substring(searchStart)));
  }
  return spans;
}
