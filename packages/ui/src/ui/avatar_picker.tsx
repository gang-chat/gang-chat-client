import type { ReactNode } from 'react';

import { Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { Avatar, avatarFallbackColor, avatarPresetLabel, normalizeAvatarPresetKey } from './avatar';
import { Button, ButtonTone } from './button';
import { BoxFit, CachedAssetImage } from './cached_asset_image';
import { Icon, Icons } from './icon';
import { UiTooltip } from './tooltip';
import { UiColors, UiShadows, UiTypography, boxShadow } from './tokens';

/**
 * Shared color-swatch palette offered by every avatar picker. Mirrors the
 * keys understood by [avatarFallbackColor].
 */
export const kAvatarPresetKeys: readonly string[] = [
  'blue-3',
  'navy-2',
  'azure-2',
  'sky-2',
  'cyan-2',
  'turquoise-2',
  'mint-2',
  'emerald-2',
  'green-2',
  'forest-2',
  'lime-2',
  'yellow-2',
  'amber-2',
  'gold-2',
  'orange-2',
  'coral-2',
  'red-2',
  'crimson-2',
  'pink-2',
  'magenta-2',
  'violet-2',
  'purple-2',
  'lavender-2',
  'indigo-2',
  'rose-2',
  'teal-2',
  'olive-2',
  'brown-2',
  'sand-2',
  'slate-2',
  'steel-2',
  'gray-2',
  'graphite-2',
  'black-2',
];

export interface AvatarPickerProps {
  /** Heading shown above the picker, e.g. '头像' or '房间图标'. */
  label: string;
  /** Display name used to derive the fallback initials. */
  displayName: string;
  /** Resolved URL of the uploaded image. Ignored while [usingPreset] is true. */
  imageUrl: string | null;
  /** Currently selected preset color key. */
  defaultAvatarKey: string;
  /** Whether the preset color (rather than an uploaded image) is active. */
  usingPreset: boolean;
  /** Whether an upload is in flight. */
  uploading: boolean;
  /** Whether the picker accepts interaction. */
  enabled: boolean;
  onUpload: () => void;
  onPresetSelected: (key: string) => void;
  onImagePreview?: (() => void) | null;
  presetKeys?: readonly string[];
  uploadLabel?: string;
  /** Rounded-square preview, used for room icons. */
  rounded?: boolean;
}

/**
 * A live circular avatar preview, a row of preset color swatches, and an
 * upload button. Used by both the account profile editor and the room
 * settings dialog so the two stay in sync.
 *
 * Tapping a swatch switches to that preset (callers handle the state change
 * through [onPresetSelected]); the upload button opens the image flow.
 */
export function AvatarPicker({
  label,
  displayName,
  imageUrl,
  defaultAvatarKey,
  usingPreset,
  uploading,
  enabled,
  onUpload,
  onPresetSelected,
  onImagePreview,
  presetKeys = kAvatarPresetKeys,
  uploadLabel = '上传图片',
  rounded = false,
}: AvatarPickerProps) {
  const normalizedDefaultAvatarKey = normalizeAvatarPresetKey(defaultAvatarKey);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'stretch', minWidth: 0 }}>
      <Text style={UiTypography.label}>{label}</Text>
      <div style={{ height: 10 }} />
      <div style={{ display: 'flex', flexDirection: 'row', alignItems: 'center', minWidth: 0 }}>
        <_AvatarPickerPreview
          label={displayName}
          imageUrl={usingPreset ? null : imageUrl}
          defaultAvatarKey={normalizedDefaultAvatarKey}
          size={88}
          onPreview={onImagePreview ?? null}
          rounded={rounded}
        />
        <div style={{ width: 16, flexShrink: 0 }} />
        <div style={{ flex: '1 1 0', minWidth: 0, display: 'flex', flexDirection: 'row', flexWrap: 'wrap', gap: 4 }}>
          {presetKeys.map((key) => (
            <_AvatarPickerSwatch
              key={key}
              keyName={key}
              selected={usingPreset && key === normalizedDefaultAvatarKey}
              onTap={enabled ? () => onPresetSelected(key) : null}
            />
          ))}
        </div>
      </div>
      <div style={{ height: 12 }} />
      <Button
        width={Infinity}
        height={38}
        tone={usingPreset ? ButtonTone.neutral : ButtonTone.primary}
        selected={!usingPreset}
        loading={uploading}
        onPressed={enabled && !uploading ? onUpload : null}
        icon={<Icon icon={Icons.upload_file_outlined} />}
      >
        {uploadLabel}
      </Button>
    </div>
  );
}

function _AvatarPickerPreview({
  label,
  imageUrl,
  defaultAvatarKey,
  size,
  onPreview,
  rounded = false,
}: {
  label: string;
  imageUrl: string | null;
  defaultAvatarKey: string;
  size: number;
  onPreview: (() => void) | null;
  rounded?: boolean;
}) {
  const fallback = <Avatar label={label} defaultAvatarKey={defaultAvatarKey} size={size} showBorder={false} rounded={rounded} />;
  const url = imageUrl;
  const radius = rounded ? size * 0.3 : size;
  const tappable = url != null && onPreview != null;
  const preview: ReactNode = (
    <div
      data-testid="avatar-picker-preview"
      onClick={tappable ? () => onPreview() : undefined}
      style={{
        width: size,
        height: size,
        flexShrink: 0,
        borderRadius: radius,
        overflow: 'hidden',
        // The DecoratedBox border and shadow paint behind the clipped child.
        boxShadow: `inset 0 0 0 1px ${UiColors.border}, ${boxShadow(UiShadows.raised)}`,
        cursor: tappable ? SystemMouseCursors.click : undefined,
      }}
    >
      {url == null ? fallback : <CachedAssetImage url={url} fit={BoxFit.cover} errorBuilder={() => fallback} />}
    </div>
  );
  return <>{preview}</>;
}

function _AvatarPickerSwatch({ keyName, selected, onTap }: { keyName: string; selected: boolean; onTap: (() => void) | null }) {
  return (
    <UiTooltip message={avatarPresetLabel(keyName)}>
      <div
        role="button"
        aria-label={avatarPresetLabel(keyName)}
        aria-pressed={selected}
        onClick={onTap == null ? undefined : () => onTap()}
        style={{
          width: 24,
          height: 24,
          flexShrink: 0,
          boxSizing: 'border-box',
          borderRadius: '50%',
          backgroundColor: avatarFallbackColor(keyName),
          border: `2px solid ${selected ? UiColors.inverse : UiColors.transparent}`,
          boxShadow: selected ? boxShadow(UiShadows.glow(UiColors.inverse, 0.35)) : 'none',
          // InkWell's hover / splash overlays are transparent app-wide.
          cursor: onTap != null ? SystemMouseCursors.click : SystemMouseCursors.basic,
        }}
      />
    </UiTooltip>
  );
}
