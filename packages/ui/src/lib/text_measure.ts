import type { CSSProperties } from 'react';

import { kClientFontStack } from '../ui/tokens';

/**
 * Synchronous text measurement: the `TextPainter.layout()` the Flutter widgets
 * use to size buttons and segments before laying them out. Measures with a 2D
 * canvas using the same CSS font the label renders with.
 */

let context: CanvasRenderingContext2D | null | undefined;

function canvasContext(): CanvasRenderingContext2D | null {
  if (context !== undefined) return context;
  if (typeof document === 'undefined') return null;
  try {
    context = document.createElement('canvas').getContext('2d');
  } catch {
    context = null;
  }
  return context;
}

function cssFont(style: CSSProperties): string {
  const size = typeof style.fontSize === 'number' ? style.fontSize : parseFloat(String(style.fontSize ?? 14)) || 14;
  const weight = style.fontWeight ?? 400;
  const fontStyle = style.fontStyle ?? 'normal';
  const family = style.fontFamily ?? kClientFontStack;
  return `${fontStyle} ${weight} ${size}px ${family}`;
}

function letterSpacingOf(style: CSSProperties): number {
  return typeof style.letterSpacing === 'number' ? style.letterSpacing : 0;
}

/** Width of [text] on one line, in CSS pixels. */
export function measureTextWidth(text: string, style: CSSProperties): number {
  const ctx = canvasContext();
  if (ctx == null) return estimateTextWidth(text, style);
  ctx.font = cssFont(style);
  return ctx.measureText(text).width + letterSpacingOf(style) * Array.from(text).length;
}

/**
 * Number of lines [text] wraps to inside [maxWidth] (greedy wrapping at spaces
 * and between CJK characters, like `white-space: pre-wrap`).
 */
export function measureLineCount(text: string, style: CSSProperties, maxWidth: number): number {
  if (!(maxWidth > 0) || !Number.isFinite(maxWidth)) return 1;
  let lines = 0;
  for (const paragraph of text.split('\n')) {
    lines += wrapParagraph(paragraph, (s) => measureTextWidth(s, style), maxWidth);
  }
  return Math.max(1, lines);
}

const kBreakSegments = /[⺀-鿿豈-﫿＀-￯]|[^\s⺀-鿿豈-﫿＀-￯]+\s*|\s+/gu;

function wrapParagraph(paragraph: string, measure: (s: string) => number, maxWidth: number): number {
  if (paragraph.length === 0) return 1;
  let lines = 1;
  let line = '';
  for (const match of paragraph.matchAll(kBreakSegments)) {
    const segment = match[0];
    const candidate = line + segment;
    if (line.length === 0 || measure(candidate.trimEnd()) <= maxWidth) {
      line = candidate;
    } else {
      lines += 1;
      line = segment.trimStart();
    }
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

/** Rough width used when no canvas is available (unit tests). */
export function estimateTextWidth(text: string, style: CSSProperties): number {
  const size = typeof style.fontSize === 'number' ? style.fontSize : 14;
  let width = 0;
  for (const char of text) {
    const code = char.codePointAt(0) ?? 0;
    width += code >= 0x2e80 ? size : code === 0x20 ? size * 0.3 : size * 0.56;
  }
  return width + letterSpacingOf(style) * Array.from(text).length;
}
