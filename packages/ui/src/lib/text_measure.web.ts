import type { TextStyle } from 'react-native';

import { kClientFontStack } from '../ui/tokens';

/**
 * Synchronous text measurement (the `TextPainter.layout()` the Flutter widgets
 * use to size buttons and segments before laying them out). The web measures
 * with a 2D canvas using the same CSS font the label renders with.
 */

let context: CanvasRenderingContext2D | null = null;

function canvasContext(): CanvasRenderingContext2D | null {
  if (context != null) return context;
  if (typeof document === 'undefined') return null;
  const canvas = document.createElement('canvas');
  context = canvas.getContext('2d');
  return context;
}

function cssFont(style: TextStyle): string {
  const size = style.fontSize ?? 14;
  const weight = style.fontWeight ?? '400';
  const fontStyle = style.fontStyle ?? 'normal';
  const family = style.fontFamily ?? kClientFontStack;
  return `${fontStyle} ${weight === 'normal' ? '400' : weight === 'bold' ? '700' : weight} ${size}px ${family}`;
}

/** Width of [text] on one line, in logical pixels. */
export function measureTextWidth(text: string, style: TextStyle): number {
  const ctx = canvasContext();
  if (ctx == null) return estimateTextWidth(text, style);
  ctx.font = cssFont(style);
  const letterSpacing = style.letterSpacing ?? 0;
  const width = ctx.measureText(text).width;
  return width + letterSpacing * Array.from(text).length;
}

/**
 * Number of lines [text] wraps to inside [maxWidth] (greedy wrapping at spaces
 * and between CJK characters, like the browser does for `white-space: normal`).
 */
export function measureLineCount(text: string, style: TextStyle, maxWidth: number): number {
  if (!(maxWidth > 0) || !Number.isFinite(maxWidth)) return 1;
  let lines = 0;
  for (const paragraph of text.split('\n')) {
    lines += wrapParagraph(paragraph, (s) => measureTextWidth(s, style), maxWidth);
  }
  return Math.max(1, lines);
}

/** Splits into break opportunities: words (with trailing spaces) and single CJK characters. */
function segments(paragraph: string): string[] {
  const out: string[] = [];
  const re = /[⺀-鿿豈-﫿＀-￯]|[^\s⺀-鿿豈-﫿＀-￯]+\s*|\s+/gu;
  for (const match of paragraph.matchAll(re)) out.push(match[0]);
  return out;
}

function wrapParagraph(paragraph: string, measure: (s: string) => number, maxWidth: number): number {
  if (paragraph.length === 0) return 1;
  let lines = 1;
  let line = '';
  for (const segment of segments(paragraph)) {
    const candidate = line + segment;
    if (line.length === 0 || measure(candidate.trimEnd()) <= maxWidth) {
      line = candidate;
      continue;
    }
    lines += 1;
    line = segment.trimStart();
    // A single word wider than the line breaks anywhere.
    while (line.length > 1 && measure(line.trimEnd()) > maxWidth) {
      let fit = 1;
      while (fit < line.length && measure(line.slice(0, fit + 1)) <= maxWidth) fit += 1;
      line = line.slice(fit);
      lines += 1;
    }
  }
  return lines;
}

/** Rough width used when no canvas is available (tests, server rendering). */
export function estimateTextWidth(text: string, style: TextStyle): number {
  const size = style.fontSize ?? 14;
  let width = 0;
  for (const char of text) {
    const code = char.codePointAt(0) ?? 0;
    width += code >= 0x2e80 ? size : code === 0x20 ? size * 0.3 : size * 0.56;
  }
  return width + (style.letterSpacing ?? 0) * Array.from(text).length;
}
