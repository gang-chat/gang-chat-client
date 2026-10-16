import type { TextStyle } from 'react-native';

/**
 * Synchronous text measurement (Flutter `TextPainter.layout()`).
 *
 * PORT: React Native has no synchronous text measurement API, so native
 * builds estimate widths from character classes (CJK ≈ 1em, Latin ≈ 0.56em).
 * Callers only use these numbers to choose between layouts (icon / no icon,
 * one row / stacked); the rendered text itself is laid out natively.
 */

export function measureTextWidth(text: string, style: TextStyle): number {
  return estimateTextWidth(text, style);
}

export function measureLineCount(text: string, style: TextStyle, maxWidth: number): number {
  if (!(maxWidth > 0) || !Number.isFinite(maxWidth)) return 1;
  let lines = 0;
  for (const paragraph of text.split('\n')) {
    lines += Math.max(1, Math.ceil(estimateTextWidth(paragraph, style) / maxWidth));
  }
  return Math.max(1, lines);
}

export function estimateTextWidth(text: string, style: TextStyle): number {
  const size = style.fontSize ?? 14;
  let width = 0;
  for (const char of text) {
    const code = char.codePointAt(0) ?? 0;
    width += code >= 0x2e80 ? size : code === 0x20 ? size * 0.3 : size * 0.56;
  }
  return width + (style.letterSpacing ?? 0) * Array.from(text).length;
}
