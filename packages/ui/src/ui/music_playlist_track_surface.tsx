import type { CSSProperties, ReactNode } from 'react';

import { UiColors, UiCurves, UiRadii } from './tokens';

export interface MusicPlaylistTrackSurfaceProps {
  children?: ReactNode;
  highlighted?: boolean;
  /** Outer box style (flex / width), which Flutter got from parent constraints. */
  style?: CSSProperties;
}

/**
 * Shared song-row surface used by saved playlists and read-only playlist
 * previews. Keeping the padding and decoration here prevents the same track
 * from changing height when it is opened from a different music-box entry.
 */
export function MusicPlaylistTrackSurface({ children, highlighted = false, style }: MusicPlaylistTrackSurfaceProps) {
  return (
    <div
      style={{
        boxSizing: 'border-box',
        padding: 14,
        backgroundColor: highlighted ? UiColors.selected : UiColors.background,
        borderRadius: UiRadii.md,
        border: `1px solid ${highlighted ? UiColors.selectedBorder : UiColors.border}`,
        transition: `background-color 90ms ${UiCurves.easeOutCubic}, border-color 90ms ${UiCurves.easeOutCubic}`,
        ...style,
      }}
    >
      {children}
    </div>
  );
}
