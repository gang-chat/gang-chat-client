import { useState } from 'react';

import { Text } from '../lib/layout';
import { UiColors, UiRadii, UiTypography } from './tokens';

export interface CompactCategoryButtonProps {
  label: string;
  count?: number | null;
  selected: boolean;
  onPressed: () => void;
  height?: number;
}

/** Compact, flat category selector shared by search and dense filter rows. */
export function CompactCategoryButton({ label, count, selected, onPressed, height = 30 }: CompactCategoryButtonProps) {
  const [hovered, setHovered] = useState(false);
  const active = selected || hovered;
  const text = count == null ? label : `${label} ${count}`;
  return (
    <div
      role="button"
      aria-pressed={selected}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={onPressed}
      style={{
        boxSizing: 'border-box',
        height,
        padding: '0 8px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minWidth: 0,
        flexShrink: 0,
        cursor: 'pointer',
        backgroundColor: selected ? UiColors.inverse : active ? UiColors.surfaceHover : UiColors.surface,
        borderRadius: UiRadii.pill,
        transition: 'background-color 90ms linear',
      }}
    >
      <Text
        maxLines={1}
        style={{
          ...UiTypography.label,
          color: selected ? UiColors.onInverse : UiColors.textSecondary,
          fontSize: 11.5,
          fontWeight: 700,
        }}
      >
        {text}
      </Text>
    </div>
  );
}
