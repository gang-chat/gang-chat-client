import type { CSSProperties } from 'react';

import { HostPlatform } from '@gang-chat/core/config/host_platform';

import { Text } from '../lib/layout';
import { BoxFit, CachedAssetImage } from './cached_asset_image';
import { Icon, type IconData } from './icon';
import { UiColors, argb, withAlpha } from './tokens';

/**
 * Fallback initials shown when an avatar has no image. Takes the first two
 * characters of the label so list avatars, the settings preview, and every
 * other fallback stay identical. Returns '?' for an empty label.
 */
export function avatarInitials(label: string): string {
  const trimmed = label.trim();
  if (trimmed.length === 0) return '?';
  return _graphemes(trimmed).slice(0, 2).join('').toUpperCase();
}

/** Dart `String.characters` (grapheme clusters). */
function _graphemes(text: string): string[] {
  const Segmenter = (Intl as { Segmenter?: typeof Intl.Segmenter }).Segmenter;
  if (Segmenter != null) {
    return Array.from(new Segmenter(undefined, { granularity: 'grapheme' }).segment(text), (s) => s.segment);
  }
  return Array.from(text);
}

export const kDefaultAvatarPresetKey = 'blue-3';

export const kLegacyAvatarPresetAliases: Readonly<Record<string, string>> = {
  'room-1': kDefaultAvatarPresetKey,
};

export function normalizeAvatarPresetKey(key: string): string {
  const trimmed = key.trim();
  if (trimmed.length === 0) return kDefaultAvatarPresetKey;
  return kLegacyAvatarPresetAliases[trimmed] ?? trimmed;
}

const _presetColors: Readonly<Record<string, number>> = {
  [kDefaultAvatarPresetKey]: 0xff4e68d0,
  'navy-2': 0xff3b589b,
  'azure-2': 0xff367fc9,
  'sky-2': 0xff3095c0,
  'cyan-2': 0xff2b95a1,
  'turquoise-2': 0xff319b8d,
  'mint-2': 0xff419f7a,
  'emerald-2': 0xff2c9661,
  'green-2': 0xff418b52,
  'forest-2': 0xff427740,
  'lime-2': 0xff6f913b,
  'yellow-2': 0xffae9932,
  'amber-2': 0xffa87f38,
  'gold-2': 0xffa38533,
  'orange-2': 0xffc47131,
  'coral-2': 0xffd46049,
  'red-2': 0xffc9363f,
  'crimson-2': 0xffab3655,
  'pink-2': 0xffc25191,
  'magenta-2': 0xffa744a7,
  'violet-2': 0xff8b57c7,
  'purple-2': 0xff7f44a7,
  'lavender-2': 0xff8573c9,
  'indigo-2': 0xff4a4ebf,
  'rose-2': 0xffbe6073,
  'teal-2': 0xff407782,
  'olive-2': 0xff767e44,
  'brown-2': 0xff7e5b44,
  'sand-2': 0xffa3875c,
  'slate-2': 0xff5e6782,
  'steel-2': 0xff517085,
  'gray-2': 0xff696e77,
  'graphite-2': 0xff50525e,
  'black-2': 0xff31333f,
};

export function avatarFallbackColor(key: string): string {
  return argb(_presetColors[normalizeAvatarPresetKey(key)] ?? 0xff4e68d0);
}

const _presetLabels: Readonly<Record<string, string>> = {
  [kDefaultAvatarPresetKey]: '蓝色',
  'navy-2': '深海蓝',
  'azure-2': '蔚蓝',
  'sky-2': '天蓝色',
  'cyan-2': '青色',
  'turquoise-2': '绿松石色',
  'mint-2': '薄荷绿',
  'emerald-2': '翡翠绿',
  'green-2': '绿色',
  'forest-2': '森林绿',
  'lime-2': '青柠绿',
  'yellow-2': '黄色',
  'amber-2': '琥珀色',
  'gold-2': '金色',
  'orange-2': '橙色',
  'coral-2': '珊瑚色',
  'red-2': '红色',
  'crimson-2': '绯红色',
  'pink-2': '粉色',
  'magenta-2': '品红色',
  'violet-2': '紫罗兰色',
  'purple-2': '紫色',
  'lavender-2': '薰衣草色',
  'indigo-2': '靛蓝色',
  'rose-2': '玫瑰色',
  'teal-2': '蓝绿色',
  'olive-2': '橄榄绿',
  'brown-2': '棕色',
  'sand-2': '沙色',
  'slate-2': '石板灰',
  'steel-2': '钢蓝色',
  'gray-2': '灰色',
  'graphite-2': '石墨灰',
  'black-2': '墨黑色',
};

/**
 * Simplified-Chinese display name for a persisted avatar color key.
 *
 * The key remains stable in API payloads and snapshots. Only the picker copy
 * is localized so older accounts and room records remain compatible.
 */
export function avatarPresetLabel(key: string): string {
  return _presetLabels[normalizeAvatarPresetKey(key)] ?? '蓝色';
}

export interface AvatarProps {
  label: string;
  imageUrl?: string | null;
  /**
   * Preset color key driving the fallback fill. When null, the neutral
   * surface color is used. Matches the swatch colors offered by the avatar
   * picker (see [avatarFallbackColor]) so list avatars and the settings
   * preview stay in sync.
   */
  defaultAvatarKey?: string | null;
  size?: number;
  active?: boolean;
  activeBorderWidth?: number;
  activeBorderColor?: string | null;
  paintBorderOnForeground?: boolean;
  showBorder?: boolean;
  showFallbackText?: boolean;
  /**
   * Rounded-square ("squircle") shape. Rooms use it so they read differently
   * from people, which are always circles.
   */
  rounded?: boolean;
  style?: CSSProperties;
}

export function Avatar({
  label,
  imageUrl,
  defaultAvatarKey,
  size = 40,
  active = false,
  activeBorderWidth = 2,
  activeBorderColor,
  paintBorderOnForeground = false,
  showBorder = true,
  showFallbackText = true,
  rounded = false,
  style,
}: AvatarProps) {
  const initials = avatarInitials(label);
  const useAndroidFallbackText = HostPlatform.isAndroid;
  const key = defaultAvatarKey == null ? null : normalizeAvatarPresetKey(defaultAvatarKey);
  const fallbackColor = key == null ? UiColors.surface : avatarFallbackColor(key);
  const fillColor = imageUrl == null ? fallbackColor : 'transparent';
  const activeColor = activeBorderColor ?? UiColors.accent;
  const borderActive = showBorder && active;
  const border = showBorder
    ? `${borderActive ? activeBorderWidth : 1}px solid ${borderActive ? activeColor : withAlpha(UiColors.white, 0.06)}`
    : undefined;
  const radius: CSSProperties['borderRadius'] = rounded ? size * 0.3 : '50%';

  return (
    <div
      style={{
        position: 'relative',
        width: size,
        height: size,
        flexShrink: 0,
        boxSizing: 'border-box',
        backgroundColor: fillColor,
        borderRadius: radius,
        border: paintBorderOnForeground ? undefined : border,
        overflow: 'hidden',
        ...style,
      }}
    >
      {imageUrl == null ? (
        showFallbackText ? (
          <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Text
              softWrap={false}
              textAlign="center"
              style={{
                color: key == null && borderActive ? activeColor : UiColors.white,
                fontSize: size * 0.36,
                fontWeight: 700,
                // The desktop font family is unavailable on Android; keep the
                // native font's own metrics there.
                fontFamily: useAndroidFallbackText ? 'sans-serif' : undefined,
                lineHeight: 'normal',
              }}
            >
              {initials}
            </Text>
          </div>
        ) : null
      ) : (
        <CachedAssetImage url={imageUrl} fit={BoxFit.cover} />
      )}
      {paintBorderOnForeground && border != null ? (
        <div
          aria-hidden="true"
          style={{
            position: 'absolute',
            inset: 0,
            boxSizing: 'border-box',
            borderRadius: radius,
            border,
            pointerEvents: 'none',
          }}
        />
      ) : null}
    </div>
  );
}

export interface StatusBadgeProps {
  label: string;
  icon?: IconData | null;
  active?: boolean;
  danger?: boolean;
}

export function StatusBadge({ label, icon, active = false, danger = false }: StatusBadgeProps) {
  const color = danger ? UiColors.danger : active ? UiColors.accent : UiColors.textMuted;
  return (
    <div
      style={{
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        width: 'fit-content',
        maxWidth: '100%',
        backgroundColor: withAlpha(color, 0.12),
        borderRadius: 999,
        border: `1px solid ${withAlpha(color, 0.45)}`,
        padding: '5px 9px',
      }}
    >
      {icon != null ? (
        <>
          <Icon icon={icon} size={13} color={color} />
          <div style={{ width: 5, flexShrink: 0 }} />
        </>
      ) : null}
      <Text softWrap={false} style={{ color, fontSize: 12, fontWeight: 600 }}>
        {label}
      </Text>
    </div>
  );
}
