import type { CSSProperties } from 'react';

import { UiColors } from '../ui/tokens';

/**
 * Port of Material's indeterminate `CircularProgressIndicator`: a rotating
 * arc whose length grows and shrinks. The keyframes (`gang-progress-rotate`,
 * `gang-progress-dash`) live in `ui/ui.css`.
 */
export function CircularProgressIndicator({
  size = 36,
  strokeWidth = 4,
  color = UiColors.accent,
  style,
}: {
  size?: number;
  strokeWidth?: number;
  color?: string;
  style?: CSSProperties;
}) {
  // Material draws the stroke inside the box.
  const radius = Math.max(0, (size - strokeWidth) / 2);
  const circumference = 2 * Math.PI * radius;
  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${size} ${size}`}
      role="progressbar"
      style={{ display: 'block', flexShrink: 0, animation: 'gang-progress-rotate 1.4s linear infinite', ...style }}
    >
      <circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        stroke={color}
        strokeWidth={strokeWidth}
        strokeLinecap="square"
        style={
          {
            '--gang-progress-circumference': `${circumference}`,
            strokeDasharray: `${circumference * 0.01} ${circumference}`,
            strokeDashoffset: 0,
            transformOrigin: 'center',
            animation: 'gang-progress-dash 1.4s ease-in-out infinite',
          } as CSSProperties
        }
      />
    </svg>
  );
}
