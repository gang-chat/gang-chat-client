import { useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';

import { Text } from '../lib/layout';

export class CompactActivityLayout {
  private constructor() {}

  static readonly avatarSize = 18;
  static readonly timestampColumnWidth = 82;
  static readonly compactTimestampColumnWidth = 68;
  static readonly compactTimestampContentGap = 4;
  static readonly compactNotificationListHorizontalPadding = 16;

  static splitTimestamp(timestamp: string): string {
    const splitAt = timestamp.indexOf(' ');
    if (splitAt < 0) return timestamp;
    return `${timestamp.substring(0, splitAt)}\n${timestamp.substring(splitAt + 1)}`;
  }
}

export interface CompactActivityTimestampProps {
  timestamp: string;
  style: CSSProperties;
}

export function CompactActivityTimestamp({ timestamp, style }: CompactActivityTimestampProps) {
  const lines = CompactActivityLayout.splitTimestamp(timestamp).split('\n');
  return (
    <_FittedBoxScaleDown>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 'max-content' }}>
        {lines.map((line, index) => (
          <Text key={index} maxLines={1} softWrap={false} style={{ ...style, overflow: 'visible', textOverflow: 'clip' }}>
            {line}
          </Text>
        ))}
      </div>
    </_FittedBoxScaleDown>
  );
}

/**
 * Port of `FittedBox(fit: BoxFit.scaleDown, alignment: Alignment.center)`:
 * the child keeps its natural size when it fits and is scaled down
 * uniformly (centred) when it is wider than the available width.
 */
function _FittedBoxScaleDown({ children }: { children: ReactNode }) {
  const outerRef = useRef<HTMLDivElement | null>(null);
  const innerRef = useRef<HTMLDivElement | null>(null);
  const [fit, setFit] = useState<{ scale: number; width: number; height: number } | null>(null);

  useLayoutEffect(() => {
    const outer = outerRef.current;
    const inner = innerRef.current;
    if (outer == null || inner == null) return;
    const measure = () => {
      const available = outer.parentElement?.clientWidth ?? outer.clientWidth;
      const width = inner.offsetWidth;
      const height = inner.offsetHeight;
      const scale = width > 0 && available > 0 ? Math.min(1, available / width) : 1;
      setFit((previous) =>
        previous != null &&
        Math.abs(previous.scale - scale) < 0.001 &&
        previous.width === width &&
        previous.height === height
          ? previous
          : { scale, width, height },
      );
    };
    measure();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(measure);
    observer.observe(inner);
    if (outer.parentElement != null) observer.observe(outer.parentElement);
    return () => observer.disconnect();
  }, []);

  const scale = fit?.scale ?? 1;
  return (
    <div
      ref={outerRef}
      style={{
        position: 'relative',
        maxWidth: '100%',
        width: fit == null ? undefined : fit.width * scale,
        height: fit == null ? undefined : fit.height * scale,
        alignSelf: 'center',
        flexShrink: 0,
      }}
    >
      <div
        ref={innerRef}
        style={{
          position: fit == null ? 'relative' : 'absolute',
          left: 0,
          top: 0,
          width: 'max-content',
          transform: scale === 1 ? undefined : `scale(${scale})`,
          transformOrigin: 'top left',
        }}
      >
        {children}
      </div>
    </div>
  );
}
