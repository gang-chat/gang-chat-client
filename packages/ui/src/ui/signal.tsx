import { useLayoutEffect, useRef, type CSSProperties, type ReactNode } from 'react';

import { Text } from '../lib/layout';
import { Icon, type IconData } from './icon';
import { UiColors, UiRadii, UiTypography, argb, withAlpha } from './tokens';

export interface DotGridBackgroundProps {
  color?: string;
  dotColor?: string;
  spacing?: number;
  dotRadius?: number;
  children?: ReactNode;
  /** Outer box style; without children the grid fills its parent (`SizedBox.expand`). */
  style?: CSSProperties;
}

/**
 * Fills its area with [color] and a faint dot grid, the canvas texture of
 * the Signal look. The dots are painted on a canvas behind the children at
 * device resolution, like the Dart `CustomPainter`.
 */
export function DotGridBackground({
  color = UiColors.background,
  dotColor = argb(0x16ffffff),
  spacing = 18,
  dotRadius = 0.8,
  children,
  style,
}: DotGridBackgroundProps) {
  const hostRef = useRef<HTMLDivElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useLayoutEffect(() => {
    const host = hostRef.current;
    const canvas = canvasRef.current;
    if (host == null || canvas == null) return;
    const paint = () => {
      const width = host.clientWidth;
      const height = host.clientHeight;
      const ratio = window.devicePixelRatio || 1;
      canvas.width = Math.max(1, Math.round(width * ratio));
      canvas.height = Math.max(1, Math.round(height * ratio));
      const ctx = canvas.getContext('2d');
      if (ctx == null) return;
      ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
      ctx.clearRect(0, 0, width, height);
      if (spacing <= 0) return;
      ctx.fillStyle = dotColor;
      ctx.beginPath();
      for (let y = spacing / 2; y < height; y += spacing) {
        for (let x = spacing / 2; x < width; x += spacing) {
          ctx.moveTo(x + dotRadius, y);
          ctx.arc(x, y, dotRadius, 0, Math.PI * 2);
        }
      }
      ctx.fill();
    };
    paint();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(paint);
    observer.observe(host);
    return () => observer.disconnect();
  }, [dotColor, spacing, dotRadius]);

  const fill = children == null;
  return (
    <div
      ref={hostRef}
      style={{
        position: 'relative',
        // Own stacking context: the canvas (z-index -1) paints above this
        // background but below the children.
        isolation: 'isolate',
        backgroundColor: color,
        boxSizing: 'border-box',
        ...(fill ? { width: '100%', height: '100%' } : null),
        ...style,
      }}
    >
      <canvas
        ref={canvasRef}
        aria-hidden="true"
        style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', pointerEvents: 'none', zIndex: -1 }}
      />
      {children}
    </div>
  );
}

export interface SignalTagProps {
  label: string;
  color?: string;
  /** Solid tag (signal colour background, dark text). */
  filled?: boolean;
  /** Leading status dot. */
  dot?: boolean;
  icon?: IconData | null;
}

/** Small monospace uppercase tag, e.g. `● LIVE 3`. */
export function SignalTag({ label, color = UiColors.textSecondary, filled = false, dot = false, icon }: SignalTagProps) {
  const foreground = filled ? UiColors.onAccent : color;
  return (
    <div
      style={{
        boxSizing: 'border-box',
        height: 20,
        padding: '0 7px',
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        width: 'fit-content',
        maxWidth: '100%',
        minWidth: 0,
        flexShrink: 0,
        backgroundColor: filled ? color : withAlpha(color, 0.1),
        borderRadius: UiRadii.pill,
        border: filled ? undefined : `1px solid ${withAlpha(color, 0.28)}`,
      }}
    >
      {dot ? (
        <>
          <div style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: foreground, flexShrink: 0 }} />
          <div style={{ width: 5, flexShrink: 0 }} />
        </>
      ) : null}
      {icon != null ? (
        <>
          <Icon icon={icon} size={11} color={foreground} />
          <div style={{ width: 4, flexShrink: 0 }} />
        </>
      ) : null}
      <Text
        maxLines={1}
        softWrap={false}
        style={{ ...UiTypography.overline, color: foreground, fontSize: 9.5, letterSpacing: 0.9, lineHeight: 1, flex: '0 1 auto' }}
      >
        {label}
      </Text>
    </div>
  );
}

export interface EqualizerBarsProps {
  color?: string;
  height?: number;
  barWidth?: number;
  gap?: number;
  levels?: readonly number[];
}

/**
 * A frozen equalizer: a row of rounded bars of varying height. Used as the
 * "voice is happening here" glyph. Static on purpose so it never animates.
 */
export function EqualizerBars({
  color = UiColors.accent,
  height = 14,
  barWidth = 3,
  gap = 2,
  levels = [0.45, 0.9, 0.6, 1, 0.35],
}: EqualizerBarsProps) {
  return (
    <div style={{ height, display: 'flex', flexDirection: 'row', alignItems: 'flex-end', width: 'fit-content', flexShrink: 0 }}>
      {levels.map((level, index) => (
        <div key={index} style={{ display: 'flex', flexDirection: 'row', alignItems: 'flex-end' }}>
          {index > 0 ? <div style={{ width: gap }} /> : null}
          <div
            style={{
              width: barWidth,
              height: Math.min(Math.max(height * level, barWidth), height),
              backgroundColor: color,
              borderRadius: barWidth,
            }}
          />
        </div>
      ))}
    </div>
  );
}

export interface DotCounterProps {
  value: number;
  label: string;
  color?: string;
  size?: number;
}

/** A dot-matrix numeral with a monospace caption underneath. */
export function DotCounter({ value, label, color = UiColors.text, size = 34 }: DotCounterProps) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', width: 'fit-content' }}>
      <Text softWrap={false} style={{ ...UiTypography.dot, color, fontSize: size }}>
        {String(Math.min(Math.max(Math.trunc(value), 0), 99)).padStart(2, '0')}
      </Text>
      <div style={{ height: 6 }} />
      <Text style={UiTypography.overline}>{label}</Text>
    </div>
  );
}
