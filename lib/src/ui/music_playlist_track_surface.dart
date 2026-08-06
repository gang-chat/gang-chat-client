import 'package:flutter/material.dart';

import 'tokens.dart';

/// Shared song-row surface used by saved playlists and read-only playlist
/// previews. Keeping the padding and decoration here prevents the same track
/// from changing height when it is opened from a different music-box entry.
class MusicPlaylistTrackSurface extends StatelessWidget {
  const MusicPlaylistTrackSurface({
    super.key,
    required this.child,
    this.highlighted = false,
  });

  final Widget child;
  final bool highlighted;

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 90),
      curve: Curves.easeOutCubic,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: highlighted ? UiColors.selected : UiColors.background,
        borderRadius: BorderRadius.circular(UiRadii.md),
        border: Border.all(
          color: highlighted ? UiColors.selectedBorder : UiColors.border,
        ),
      ),
      child: child,
    );
  }
}
