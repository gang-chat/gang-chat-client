import 'package:flutter/material.dart';

import 'tooltip.dart';

import 'avatar.dart';
import 'button.dart';
import 'cached_asset_image.dart';
import 'tokens.dart';

/// Shared color-swatch palette offered by every avatar picker. Mirrors the
/// keys understood by [avatarFallbackColor].
const List<String> kAvatarPresetKeys = [
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

/// A live circular avatar preview, a row of preset color swatches, and an
/// upload button. Used by both the account profile editor and the room
/// settings dialog so the two stay in sync.
///
/// Tapping a swatch switches to that preset (callers handle the state change
/// through [onPresetSelected]); the upload button opens the image flow.
class AvatarPicker extends StatelessWidget {
  const AvatarPicker({
    super.key,
    required this.label,
    required this.displayName,
    required this.imageUrl,
    required this.defaultAvatarKey,
    required this.usingPreset,
    required this.uploading,
    required this.enabled,
    required this.onUpload,
    required this.onPresetSelected,
    this.onImagePreview,
    this.presetKeys = kAvatarPresetKeys,
    this.uploadLabel = '上传图片',
  });

  /// Heading shown above the picker, e.g. '头像' or '房间图标'.
  final String label;

  /// Display name used to derive the fallback initials.
  final String displayName;

  /// Resolved URL of the uploaded image. Ignored while [usingPreset] is true.
  final String? imageUrl;

  /// Currently selected preset color key.
  final String defaultAvatarKey;

  /// Whether the preset color (rather than an uploaded image) is active.
  final bool usingPreset;

  /// Whether an upload is in flight.
  final bool uploading;

  /// Whether the picker accepts interaction.
  final bool enabled;

  final VoidCallback onUpload;
  final ValueChanged<String> onPresetSelected;
  final VoidCallback? onImagePreview;
  final List<String> presetKeys;
  final String uploadLabel;

  @override
  Widget build(BuildContext context) {
    final normalizedDefaultAvatarKey = normalizeAvatarPresetKey(
      defaultAvatarKey,
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          label,
          style: UiTypography.label.copyWith(color: UiColors.textMuted),
        ),
        const SizedBox(height: 10),
        Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            _AvatarPickerPreview(
              label: displayName,
              imageUrl: usingPreset ? null : imageUrl,
              defaultAvatarKey: normalizedDefaultAvatarKey,
              size: 88,
              onPreview: onImagePreview,
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Wrap(
                spacing: 4,
                runSpacing: 4,
                children: [
                  for (final key in presetKeys)
                    _AvatarPickerSwatch(
                      keyName: key,
                      selected:
                          usingPreset && key == normalizedDefaultAvatarKey,
                      onTap: enabled ? () => onPresetSelected(key) : null,
                    ),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Button(
          width: double.infinity,
          height: 38,
          tone: usingPreset ? ButtonTone.neutral : ButtonTone.primary,
          selected: !usingPreset,
          loading: uploading,
          onPressed: enabled && !uploading ? onUpload : null,
          icon: const Icon(Icons.upload_file_outlined),
          child: Text(uploadLabel),
        ),
      ],
    );
  }
}

class _AvatarPickerPreview extends StatelessWidget {
  const _AvatarPickerPreview({
    required this.label,
    required this.imageUrl,
    required this.defaultAvatarKey,
    required this.size,
    required this.onPreview,
  });

  final String label;
  final String? imageUrl;
  final String defaultAvatarKey;
  final double size;
  final VoidCallback? onPreview;

  @override
  Widget build(BuildContext context) {
    final fallback = Avatar(
      label: label,
      defaultAvatarKey: defaultAvatarKey,
      size: size,
      showBorder: false,
    );
    final url = imageUrl;
    final preview = SizedBox.square(
      key: const ValueKey('avatar-picker-preview'),
      dimension: size,
      child: DecoratedBox(
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          border: Border.all(color: UiColors.border),
        ),
        child: ClipOval(
          child: url == null
              ? fallback
              : CachedAssetImage(
                  url: url,
                  fit: BoxFit.cover,
                  errorBuilder: (context, error, stackTrace) => fallback,
                ),
        ),
      ),
    );
    if (url == null || onPreview == null) return preview;

    return MouseRegion(
      cursor: SystemMouseCursors.click,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onPreview,
        child: preview,
      ),
    );
  }
}

class _AvatarPickerSwatch extends StatelessWidget {
  const _AvatarPickerSwatch({
    required this.keyName,
    required this.selected,
    required this.onTap,
  });

  final String keyName;
  final bool selected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return UiTooltip(
      message: avatarPresetLabel(keyName),
      child: InkWell(
        onTap: onTap,
        customBorder: const CircleBorder(),
        child: Container(
          width: 24,
          height: 24,
          decoration: BoxDecoration(
            color: avatarFallbackColor(keyName),
            shape: BoxShape.circle,
            border: Border.all(
              color: selected ? UiColors.accent : UiColors.border,
              width: selected ? 2 : 1,
            ),
          ),
        ),
      ),
    );
  }
}
