import { UiColors } from './tokens';

export interface BadgeDotProps {
  size?: number;
  color?: string;
  borderColor?: string;
}

export function BadgeDot({ size = 8, color = UiColors.danger, borderColor = UiColors.surface }: BadgeDotProps) {
  return (
    <div
      aria-hidden="true"
      style={{
        width: size,
        height: size,
        flexShrink: 0,
        boxSizing: 'border-box',
        borderRadius: '50%',
        backgroundColor: color,
        border: `1.4px solid ${borderColor}`,
        pointerEvents: 'none',
      }}
    />
  );
}
