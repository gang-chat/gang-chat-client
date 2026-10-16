import { useRef, type CSSProperties } from 'react';

import { Text } from '../lib/layout';
import { measureLineCount } from '../lib/text_measure';
import { useElementSize } from '../lib/use_element_size';
import { UiColors, withAlpha } from './tokens';

export interface HighlightedTextProps {
  text: string;
  query: string;
  /** Base style; inherits the surrounding text style when omitted (Dart `DefaultTextStyle`). */
  style?: CSSProperties | null;
  highlightStyle?: CSSProperties | null;
  maxLines?: number | null;
  overflow?: 'ellipsis' | 'clip' | 'visible' | null;
  textAlign?: CSSProperties['textAlign'] | null;
  softWrap?: boolean | null;
}

export function HighlightedText({ text, query, style, highlightStyle, maxLines, overflow, textAlign, softWrap }: HighlightedTextProps) {
  const spans = _highlightedSpans({ text, query });
  const resolvedHighlight = highlightStyle ?? _defaultHighlightStyle();
  return (
    <Text
      style={style ?? undefined}
      maxLines={maxLines ?? undefined}
      overflow={overflow ?? undefined}
      textAlign={textAlign ?? undefined}
      softWrap={softWrap ?? true}
    >
      {spans == null
        ? text
        : spans.map((span, index) =>
            span.highlighted ? (
              <span key={index} style={resolvedHighlight}>
                {span.text}
              </span>
            ) : (
              <span key={index}>{span.text}</span>
            ),
          )}
    </Text>
  );
}

export interface AdaptiveHighlightedTextProps {
  text: string;
  query: string;
  style?: CSSProperties | null;
  highlightStyle?: CSSProperties | null;
  comfortableLines?: number;
  maximumFontSizeReduction?: number;
  minimumFontSize?: number;
  textAlign?: CSSProperties['textAlign'] | null;
}

/**
 * Keeps short labels on their natural line count, then allows longer labels
 * to wrap in full while reducing the font only when the comfortable line
 * count is exceeded.
 */
export function AdaptiveHighlightedText({
  text,
  query,
  style,
  highlightStyle,
  comfortableLines = 2,
  maximumFontSizeReduction = 2,
  minimumFontSize = 10,
  textAlign,
}: AdaptiveHighlightedTextProps) {
  const ref = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(ref);
  const baseStyle: CSSProperties = style ?? {};
  let effectiveStyle = baseStyle;
  const width = size?.width ?? 0;
  const baseSize = typeof baseStyle.fontSize === 'number' ? baseStyle.fontSize : null;
  if (Number.isFinite(width) && width > 0 && baseSize != null) {
    const lineCount = measureLineCount(text.length === 0 ? ' ' : text, baseStyle, width);
    if (lineCount > comfortableLines) {
      const reduction = Math.min(Math.max(lineCount - comfortableLines, 1), maximumFontSizeReduction);
      effectiveStyle = { ...baseStyle, fontSize: Math.min(Math.max(baseSize - reduction, minimumFontSize), baseSize) };
    }
  }
  return (
    <div ref={ref} style={{ minWidth: 0, width: '100%' }}>
      <HighlightedText
        text={text}
        query={query}
        style={effectiveStyle}
        highlightStyle={highlightStyle}
        textAlign={textAlign}
        softWrap
      />
    </div>
  );
}

function _defaultHighlightStyle(): CSSProperties {
  return {
    color: UiColors.accent,
    fontWeight: 700,
    backgroundColor: withAlpha(UiColors.accent, 0.2),
  };
}

export interface HighlightedSpan {
  text: string;
  highlighted: boolean;
}

/**
 * Splits [text] around every case-insensitive occurrence of the trimmed
 * [query]. Returns null when nothing matches (render the plain text).
 */
export function _highlightedSpans({ text, query }: { text: string; query: string }): HighlightedSpan[] | null {
  const needle = query.trim();
  if (text.length === 0 || needle.length === 0) return null;

  const lowerText = text.toLowerCase();
  const lowerNeedle = needle.toLowerCase();
  let searchStart = 0;
  let hasMatch = false;
  const spans: HighlightedSpan[] = [];

  while (searchStart < text.length) {
    const matchStart = lowerText.indexOf(lowerNeedle, searchStart);
    if (matchStart < 0) break;
    const matchEnd = matchStart + needle.length;
    if (matchStart > searchStart) {
      spans.push({ text: text.substring(searchStart, matchStart), highlighted: false });
    }
    spans.push({ text: text.substring(matchStart, matchEnd), highlighted: true });
    hasMatch = true;
    searchStart = matchEnd;
  }

  if (!hasMatch) return null;
  if (searchStart < text.length) {
    spans.push({ text: text.substring(searchStart), highlighted: false });
  }
  return spans;
}
