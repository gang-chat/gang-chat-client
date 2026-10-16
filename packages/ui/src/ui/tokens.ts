import type { CSSProperties } from 'react';

/**
 * Signal design tokens (port of `lib/src/ui/tokens.dart`).
 *
 * Feature code takes colours, radii, type, shadows and motion only from here.
 * Colours are `#RRGGBB` / `#RRGGBBAA` strings usable directly in CSS.
 */

export const kClientFontFamily = 'Microsoft YaHei';
export const kClientFontFamilyFallback = ['Microsoft YaHei UI', 'PingFang SC', 'Noto Sans SC', 'Noto Sans CJK SC'];

/** Latin display face for headings and numerals (CJK falls back to the platform font). */
export const kDisplayFontFamily = 'Space Grotesk';

/** Monospace face for labels, tags, timestamps and counters. */
export const kMonoFontFamily = 'JetBrains Mono';

/** Dot-matrix face for the wordmark and big numerals. */
export const kDotFontFamily = 'Doto';

/** Builds a CSS `font-family` stack: [primary], then the client CJK faces. */
export function fontStack(primary: string, fallback: readonly string[] = [kClientFontFamily, ...kClientFontFamilyFallback]): string {
  const quote = (name: string) => (/^[a-zA-Z-]+$/.test(name) ? name : `"${name}"`);
  return [primary, ...fallback, 'system-ui', 'sans-serif'].map(quote).join(', ');
}

export const kClientFontStack = fontStack(kClientFontFamily, kClientFontFamilyFallback);

/**
 * "Signal" palette: true-black canvas, graphite layers and a single acid-lime
 * signal colour. Lime means *live or actionable*. White pills mean *selected*.
 */
export const UiColors = {
  // Neutral layers, darkest to lightest.
  surfacePressed: '#000000',
  background: '#060607',
  surfaceLow: '#0D0D0F',

  /** Grouping cards that sit on a [surfaceLow] panel. */
  card: '#131316',
  surface: '#17171A',
  surfaceRaised: '#1E1E22',
  surfaceHover: '#27272C',

  selected: '#1C220C',
  border: '#222226',
  borderStrong: '#323238',
  accentBorder: '#465418',
  selectedBorder: '#9FC72A',
  dangerBorder: '#5E1F21',
  disabledSurface: '#111113',
  disabledBorder: '#1C1C1F',

  /** Text-field wells sit slightly below the panel they live on. */
  fieldFill: '#09090A',
  fieldBorder: '#26262B',
  fieldBorderHover: '#3A3A41',

  text: '#F4F4F1',
  textSecondary: '#A3A3A8',
  textMuted: '#66666D',
  onAccent: '#0A0B05',

  /** Inverted "selected" pill (white surface, black text). */
  inverse: '#F4F4F1',
  onInverse: '#0A0A0B',

  /** Acid-lime signal colour for text and icons on dark surfaces. */
  accent: '#D4FF3F',

  /** Lime used for filled controls (primary buttons, toggles). */
  accentStrong: '#CCF43A',
  accentSoft: '#151A07',
  controlAccent: '#5CE1FF',
  violet: '#B69CFF',
  amber: '#FFB224',
  danger: '#FF4D4F',
  dangerSurface: '#250D0E',
  success: '#D4FF3F',
  successSurface: '#161C07',
  successBorder: '#465418',

  presenceOnline: '#4ADE80',
  presenceOnlineSurface: '#0D2015',
  presenceOnlineBorder: '#1E5534',
  presenceOffline: '#7C7C84',
  presenceOfflineSurface: '#18181B',
  presenceOfflineBorder: '#34343A',
  presenceVoice: '#D4FF3F',
  presenceVoiceSurface: '#161C07',
  presenceVoiceBorder: '#55661B',
  presenceReconnecting: '#FFB224',

  roleMember: '#5CB8FF',
  roleMemberSurface: '#0E1D2B',
  roleMemberBorder: '#24527A',
  roleAdmin: '#FFD84D',
  roleAdminSurface: '#261F08',
  roleAdminBorder: '#7A6420',
  roleCreator: '#FF8A3D',
  roleCreatorSurface: '#2A160A',
  roleCreatorBorder: '#8A4820',
  roleSuperuser: '#FF4D6A',
  roleSuperuserSurface: '#2B0D13',
  roleSuperuserBorder: '#8A2638',

  transparent: 'transparent',
  white: '#FFFFFF',
  black: '#000000',
} as const;

export type UiColor = string;

// ---------------------------------------------------------------------------
// Colour math (ports of Color.withValues(alpha:), Color.lerp, color.a).

interface Rgba {
  r: number;
  g: number;
  b: number;
  a: number;
}

/** Converts a Flutter ARGB int (`0xAARRGGBB`) to `#RRGGBB` / `#RRGGBBAA`. */
export function argb(value: number): string {
  const a = Math.floor(value / 0x1000000) & 0xff;
  const r = (value >>> 16) & 0xff;
  const g = (value >>> 8) & 0xff;
  const b = value & 0xff;
  const hex = (n: number) => n.toString(16).padStart(2, '0').toUpperCase();
  return a === 0xff ? `#${hex(r)}${hex(g)}${hex(b)}` : `#${hex(r)}${hex(g)}${hex(b)}${hex(a)}`;
}

export function parseColor(color: string): Rgba {
  if (color === 'transparent') return { r: 0, g: 0, b: 0, a: 0 };
  if (color.startsWith('#')) {
    const hex = color.slice(1);
    const full =
      hex.length === 3 || hex.length === 4
        ? hex
            .split('')
            .map((c) => c + c)
            .join('')
        : hex;
    const r = parseInt(full.slice(0, 2), 16);
    const g = parseInt(full.slice(2, 4), 16);
    const b = parseInt(full.slice(4, 6), 16);
    const a = full.length >= 8 ? parseInt(full.slice(6, 8), 16) / 255 : 1;
    return { r, g, b, a };
  }
  const match = color.match(/rgba?\(([^)]+)\)/);
  if (match) {
    const parts = match[1]!.split(',').map((p) => parseFloat(p.trim()));
    return { r: parts[0] ?? 0, g: parts[1] ?? 0, b: parts[2] ?? 0, a: parts[3] ?? 1 };
  }
  return { r: 0, g: 0, b: 0, a: 1 };
}

function formatColor({ r, g, b, a }: Rgba): string {
  const clamp = (n: number) => Math.max(0, Math.min(255, Math.round(n)));
  const hex = (n: number) => clamp(n).toString(16).padStart(2, '0').toUpperCase();
  const alpha = Math.max(0, Math.min(1, a));
  return alpha >= 1 ? `#${hex(r)}${hex(g)}${hex(b)}` : `#${hex(r)}${hex(g)}${hex(b)}${hex(alpha * 255)}`;
}

/** Port of `color.withValues(alpha: alpha)` (alpha 0..1). */
export function withAlpha(color: string, alpha: number): string {
  return formatColor({ ...parseColor(color), a: alpha });
}

/** Port of `color.a` (0..1). */
export function colorAlpha(color: string): number {
  return parseColor(color).a;
}

/** Port of `Color.lerp(a, b, t)`. */
export function lerpColor(a: string, b: string, t: number): string {
  const x = parseColor(a);
  const y = parseColor(b);
  return formatColor({
    r: x.r + (y.r - x.r) * t,
    g: x.g + (y.g - x.g) * t,
    b: x.b + (y.b - x.b) * t,
    a: x.a + (y.a - x.a) * t,
  });
}

/** Port of `Color.alphaBlend(foreground, background)`. */
export function alphaBlend(foreground: string, background: string): string {
  const f = parseColor(foreground);
  const bg = parseColor(background);
  const a = f.a + bg.a * (1 - f.a);
  if (a === 0) return 'transparent';
  return formatColor({
    r: (f.r * f.a + bg.r * bg.a * (1 - f.a)) / a,
    g: (f.g * f.a + bg.g * bg.a * (1 - f.a)) / a,
    b: (f.b * f.a + bg.b * bg.a * (1 - f.a)) / a,
    a,
  });
}

/**
 * Interaction tints shared by every pressable control so hover and press
 * feedback reads identically across buttons, rows and cards.
 */
export const UiStates = {
  hover(base: string): string {
    if (colorAlpha(base) === 0) return withAlpha(UiColors.white, 0.05);
    return lerpColor(base, UiColors.white, 0.055);
  },
  pressed(base: string): string {
    if (colorAlpha(base) === 0) return withAlpha(UiColors.white, 0.03);
    return lerpColor(base, UiColors.black, 0.18);
  },
};

export const UiSpacing = {
  xxs: 4,
  xs: 6,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20,
  xxl: 24,
  xxxl: 32,
} as const;

export const UiRadii = {
  xs: 5,
  sm: 8,
  md: 12,
  lg: 18,
  xl: 26,
  pill: 999,
} as const;

/** Durations in milliseconds; [curve] is `Curves.easeOutCubic` as a CSS easing. */
export const UiMotion = {
  fast: 120,
  standard: 180,
  slow: 260,
  curve: 'cubic-bezier(0.215, 0.61, 0.355, 1)',
} as const;

/** Flutter curves used by the kit, as CSS easings. */
export const UiCurves = {
  linear: 'linear',
  easeOut: 'cubic-bezier(0, 0, 0.58, 1)',
  easeIn: 'cubic-bezier(0.42, 0, 1, 1)',
  easeInOut: 'cubic-bezier(0.42, 0, 0.58, 1)',
  easeOutCubic: 'cubic-bezier(0.215, 0.61, 0.355, 1)',
  easeInCubic: 'cubic-bezier(0.55, 0.055, 0.675, 0.19)',
  easeInOutCubic: 'cubic-bezier(0.645, 0.045, 0.355, 1)',
  easeOutBack: 'cubic-bezier(0.175, 0.885, 0.32, 1.275)',
  fastOutSlowIn: 'cubic-bezier(0.4, 0, 0.2, 1)',
} as const;

// ---------------------------------------------------------------------------
// Shadows. Flutter `BoxShadow` lists become CSS `box-shadow` strings.

export interface BoxShadowSpec {
  color: string;
  blurRadius: number;
  spreadRadius?: number;
  offsetX?: number;
  offsetY?: number;
}

export function boxShadow(shadows: readonly BoxShadowSpec[]): string {
  if (shadows.length === 0) return 'none';
  return shadows
    .map((s) => `${s.offsetX ?? 0}px ${s.offsetY ?? 0}px ${s.blurRadius}px ${s.spreadRadius ?? 0}px ${s.color}`)
    .join(', ');
}

export const UiShadows = {
  raised: [{ color: argb(0x40000000), blurRadius: 10, offsetY: 3 }] as BoxShadowSpec[],
  popover: [
    { color: argb(0x80000000), blurRadius: 32, offsetY: 14 },
    { color: argb(0x33000000), blurRadius: 4, offsetY: 1 },
  ] as BoxShadowSpec[],
  dialog: [{ color: argb(0x99000000), blurRadius: 56, offsetY: 24 }] as BoxShadowSpec[],
  glow(color: string, strength = 0.32): BoxShadowSpec[] {
    return [{ color: withAlpha(color, strength), blurRadius: 18, spreadRadius: -2 }];
  },
};

// ---------------------------------------------------------------------------
// Typography. Flutter's `TextStyle.height` multiplier maps to a unitless CSS
// `line-height`; letter spacing is in px.

export type UiTextStyle = CSSProperties;

function textStyle(spec: {
  color: string;
  fontFamily?: string;
  fontSize: number;
  fontWeight?: number;
  letterSpacing?: number;
  height?: number;
}): UiTextStyle {
  const style: UiTextStyle = {
    color: spec.color,
    fontFamily: spec.fontFamily ?? kClientFontStack,
    fontSize: spec.fontSize,
  };
  if (spec.fontWeight != null) style.fontWeight = spec.fontWeight;
  if (spec.letterSpacing != null) style.letterSpacing = spec.letterSpacing;
  if (spec.height != null) style.lineHeight = spec.height;
  return style;
}

export const UiTypography = {
  /** Big display text (hero greetings, page titles). */
  display: textStyle({
    color: UiColors.text,
    fontFamily: fontStack(kDisplayFontFamily),
    fontSize: 30,
    fontWeight: 700,
    letterSpacing: -0.6,
    height: 1.15,
  }),
  headline: textStyle({
    color: UiColors.text,
    fontFamily: fontStack(kDisplayFontFamily),
    fontSize: 21,
    fontWeight: 700,
    letterSpacing: -0.3,
    height: 1.25,
  }),
  title: textStyle({
    color: UiColors.text,
    fontFamily: fontStack(kDisplayFontFamily),
    fontSize: 17,
    fontWeight: 700,
    letterSpacing: -0.2,
    height: 1.3,
  }),
  subtitle: textStyle({
    color: UiColors.text,
    fontFamily: fontStack(kDisplayFontFamily),
    fontSize: 15,
    fontWeight: 700,
    letterSpacing: -0.1,
    height: 1.35,
  }),
  body: textStyle({ color: UiColors.text, fontSize: 14, height: 1.4 }),
  label: textStyle({ color: UiColors.textSecondary, fontSize: 12, fontWeight: 500, height: 1.35 }),
  caption: textStyle({ color: UiColors.textMuted, fontSize: 11.5, fontWeight: 500, height: 1.35 }),
  /** Monospace uppercase label ("tag" look) for section headers, counters and timestamps. */
  overline: textStyle({
    color: UiColors.textMuted,
    fontFamily: fontStack(kMonoFontFamily),
    fontSize: 10.5,
    fontWeight: 700,
    letterSpacing: 1.2,
    height: 1.3,
  }),
  mono: textStyle({
    color: UiColors.textMuted,
    fontFamily: fontStack(kMonoFontFamily),
    fontSize: 11,
    fontWeight: 500,
    letterSpacing: 0.2,
    height: 1.3,
  }),
  /** Dot-matrix numerals and wordmark. */
  dot: textStyle({
    color: UiColors.text,
    fontFamily: fontStack(kDotFontFamily, [kMonoFontFamily, kClientFontFamily]),
    fontSize: 28,
    fontWeight: 800,
    height: 1.0,
  }),
} satisfies Record<string, UiTextStyle>;

/** Base text style of the app (Flutter theme default: 14px client font). */
export const UiDefaultTextStyle: UiTextStyle = {
  color: UiColors.text,
  fontFamily: kClientFontStack,
  fontSize: 14,
};

/** Selection / cursor colours (Flutter `textSelectionTheme`). */
export const UiSelection = {
  cursorColor: UiColors.accent,
  selectionColor: withAlpha(UiColors.accentStrong, 0.3),
  handleColor: UiColors.accent,
};

/** Tooltip look (Flutter `tooltipTheme`). */
export const UiTooltipTheme = {
  background: UiColors.surfaceHover,
  border: UiColors.borderStrong,
  radius: UiRadii.sm,
  paddingHorizontal: 9,
  paddingVertical: 5,
  waitDuration: 380,
  textStyle: textStyle({ color: UiColors.text, fontSize: 12, fontWeight: 500 }),
};
