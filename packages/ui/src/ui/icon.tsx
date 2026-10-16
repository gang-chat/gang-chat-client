import { createContext, useContext, type CSSProperties, type ReactNode } from 'react';

import type { IconData } from './icons';
import { UiColors } from './tokens';

export { Icons, type IconData, type IconName } from './icons';

export interface IconThemeData {
  color?: string;
  size?: number;
}

const IconThemeContext = createContext<IconThemeData>({});

/**
 * Port of `IconTheme.merge`: icons below [data] default to its colour and
 * size (an explicit `color` / `size` on the `Icon` still wins). Buttons use it
 * so `icon={<Icon icon={Icons.add} />}` picks up the button's foreground.
 */
export function IconTheme({ data, children }: { data: IconThemeData; children?: ReactNode }) {
  const parent = useContext(IconThemeContext);
  return (
    <IconThemeContext.Provider value={{ color: data.color ?? parent.color, size: data.size ?? parent.size }}>
      {children}
    </IconThemeContext.Provider>
  );
}

/** Port of `IconTheme.of(context)`. */
export function useIconTheme(): IconThemeData {
  return useContext(IconThemeContext);
}

export interface IconProps {
  icon: IconData;
  size?: number;
  color?: string;
  style?: CSSProperties;
  /** Accessibility label (Flutter `semanticLabel`). */
  semanticLabel?: string;
}

/**
 * Port of Flutter's `Icon` widget: draws one glyph from the Material Icons
 * font named by [icon]. Size and colour default to the enclosing [IconTheme]
 * (24 / text colour without one, like Flutter's default `IconTheme`).
 */
export function Icon({ icon, size, color, style, semanticLabel }: IconProps) {
  const theme = useContext(IconThemeContext);
  const resolvedSize = size ?? theme.size ?? 24;
  const resolvedColor = color ?? theme.color ?? UiColors.text;
  return (
    <span
      role={semanticLabel != null ? 'img' : undefined}
      aria-label={semanticLabel}
      aria-hidden={semanticLabel == null ? true : undefined}
      style={{
        display: 'inline-block',
        flexShrink: 0,
        fontFamily: `"${icon.fontFamily}"`,
        fontSize: resolvedSize,
        lineHeight: 1,
        width: resolvedSize,
        height: resolvedSize,
        color: resolvedColor,
        textAlign: 'center',
        fontWeight: 'normal',
        fontStyle: 'normal',
        letterSpacing: 'normal',
        whiteSpace: 'nowrap',
        overflow: 'hidden',
        userSelect: 'none',
        WebkitFontSmoothing: 'antialiased',
        ...style,
      }}
    >
      {String.fromCodePoint(icon.codePoint)}
    </span>
  );
}
