part of 'live_channel_pane.dart';

/// Shared list furniture for the music box body: one track row used by the
/// request queue, search results, and saved-playlist tracks; one playlist row;
/// section headers; and the empty/loading/error states. Keeping a single row
/// implementation means a song looks and behaves the same no matter which tab
/// it was found in.

const double _musicBoxRowMinHeight = 44;
const double _musicBoxRowActionSize = 28;
const double _musicBoxRowLeadingWidth = 24;

/// Shrinks the font a little when [text] would wrap past [comfortableLines]
/// at [width], so long titles stay fully visible without an ellipsis.
TextStyle _musicBoxAdaptiveListTextStyle(
  BuildContext context, {
  required String text,
  required TextStyle baseStyle,
  required double width,
  int comfortableLines = 2,
  double maximumReduction = 2,
}) {
  final baseSize = baseStyle.fontSize ?? 13;
  final painter = TextPainter(
    text: TextSpan(text: text, style: baseStyle),
    textDirection: Directionality.of(context),
    textScaler: MediaQuery.textScalerOf(context),
  )..layout(maxWidth: width);
  final lineCount = painter.computeLineMetrics().length;
  if (lineCount <= comfortableLines) return baseStyle;
  final reduction = (lineCount - comfortableLines)
      .clamp(1, maximumReduction.toInt())
      .toDouble();
  return baseStyle.copyWith(fontSize: baseSize - reduction);
}

/// One song in any music-box list. [leading] is the row's status glyph
/// (index number, download spinner, playing waveform); [trailing] holds the
/// list's constant actions. The title area opens the song card
/// ([cardBuilder]).
class _MusicBoxTrackRow extends StatefulWidget {
  const _MusicBoxTrackRow({
    super.key,
    required this.leading,
    required this.title,
    required this.subtitle,
    this.query = '',
    this.selected = false,
    this.subtitleColor = UiColors.textSecondary,
    this.cardBuilder,
    this.cardResetKey,
    this.trailing = const <Widget>[],
    this.hoverColor = UiColors.surfaceLow,
  });

  final Widget leading;
  final String title;
  final String subtitle;
  final String query;
  final bool selected;
  final Color subtitleColor;
  final HoverCardBuilder? cardBuilder;
  final Object? cardResetKey;
  final List<Widget> trailing;
  final Color hoverColor;

  @override
  State<_MusicBoxTrackRow> createState() => _MusicBoxTrackRowState();
}

class _MusicBoxTrackRowState extends State<_MusicBoxTrackRow> {
  bool _hovered = false;

  void _setHovered(bool hovered) {
    if (_hovered == hovered) return;
    setState(() => _hovered = hovered);
  }

  @override
  Widget build(BuildContext context) {
    final actionCount = widget.trailing.length;
    final titleStyle = TextStyle(
      color: widget.selected ? UiColors.accent : UiColors.text,
      fontSize: 13,
      fontWeight: FontWeight.w500,
    );
    final subtitleStyle = TextStyle(
      color: widget.subtitleColor,
      fontSize: 11,
      fontWeight: FontWeight.w600,
    );
    final body = LayoutBuilder(
      builder: (context, constraints) {
        final textWidth =
            (constraints.maxWidth -
                    16 -
                    _musicBoxRowLeadingWidth -
                    8 -
                    actionCount * (_musicBoxRowActionSize + 2))
                .clamp(24.0, double.infinity);
        final adaptiveTitle = _musicBoxAdaptiveListTextStyle(
          context,
          text: widget.title,
          baseStyle: titleStyle,
          width: textWidth,
        );
        final adaptiveSubtitle = _musicBoxAdaptiveListTextStyle(
          context,
          text: widget.subtitle,
          baseStyle: subtitleStyle,
          width: textWidth,
        );
        final text = Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            HighlightedText(
              text: widget.title,
              query: widget.query,
              style: adaptiveTitle,
            ),
            if (widget.subtitle.isNotEmpty) ...[
              const SizedBox(height: 2),
              HighlightedText(
                text: widget.subtitle,
                query: widget.query,
                style: adaptiveSubtitle,
              ),
            ],
          ],
        );
        final cardBuilder = widget.cardBuilder;
        return AnimatedContainer(
          duration: const Duration(milliseconds: 80),
          curve: Curves.easeOutCubic,
          constraints: const BoxConstraints(minHeight: _musicBoxRowMinHeight),
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
          decoration: BoxDecoration(
            color: widget.selected
                ? UiColors.selected
                : _hovered
                ? widget.hoverColor
                : Colors.transparent,
            borderRadius: BorderRadius.circular(UiRadii.sm),
          ),
          child: Row(
            children: [
              SizedBox(
                width: _musicBoxRowLeadingWidth,
                child: Center(child: widget.leading),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: cardBuilder == null
                    ? text
                    : HoverCardAnchor(
                        resetKey: widget.cardResetKey,
                        cardWidth: 310,
                        cardBuilder: cardBuilder,
                        child: text,
                      ),
              ),
              for (final action in widget.trailing) ...[
                const SizedBox(width: 2),
                action,
              ],
            ],
          ),
        );
      },
    );
    return MouseRegion(
      onEnter: (_) => _setHovered(true),
      onExit: (_) => _setHovered(false),
      child: body,
    );
  }
}

/// An icon action inside the scrolling list. Deliberately no Material
/// [Tooltip]: hovering between tooltip overlays inside a viewport corrupts the
/// Windows accessibility tree (flutter/flutter#182444), after which every
/// later semantics update to that subtree is rejected. The label is exposed
/// through semantics instead, so screen readers still announce it.
class _MusicBoxRowAction extends StatelessWidget {
  const _MusicBoxRowAction({
    super.key,
    required this.icon,
    required this.label,
    required this.onPressed,
    this.tone = ButtonTone.neutral,
    this.selected = false,
    this.loading = false,
    this.size = _musicBoxRowActionSize,
  });

  final IconData icon;
  final String label;
  final VoidCallback? onPressed;
  final ButtonTone tone;
  final bool selected;
  final bool loading;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: label,
      child: ButtonIcon(
        icon: Icon(icon),
        tone: tone,
        selected: selected,
        loading: loading,
        onPressed: onPressed,
        size: size,
      ),
    );
  }
}

/// The person who requested a song: their avatar with the app's user card on
/// hover/tap. No Material [Tooltip] here — tooltips inside a scrolling list
/// trip the Windows accessibility bridge (flutter/flutter#182444), and the
/// card already names the user.
class _MusicBoxRequesterAvatar extends StatelessWidget {
  const _MusicBoxRequesterAvatar({
    super.key,
    required this.requester,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
    this.size = 24,
  });

  final MusicBoxRequester requester;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;
  final double size;

  @override
  Widget build(BuildContext context) {
    return UserHoverCard(
      user: UserSummary(
        id: requester.userId,
        username: requester.username,
        displayName: requester.displayName,
        avatarUrl: requester.avatarUrl,
        defaultAvatarKey: requester.defaultAvatarKey,
      ),
      currentUser: currentUser,
      onResolveProfile: onResolveUserProfile,
      onResolveRoomProfile: onResolveRoomProfile,
      onEnterCommonRoom: onEnterCommonRoom,
      profileActionBuilder: userProfileActionBuilder,
      showRoomRole: true,
      child: Semantics(
        label: '点歌人：${requester.displayName}',
        child: Avatar(
          label: requester.avatarLabel,
          imageUrl: AppConfigScope.of(
            context,
          ).resolveAssetUrl(requester.avatarUrl),
          defaultAvatarKey: requester.defaultAvatarKey,
          size: size,
          showBorder: false,
        ),
      ),
    );
  }
}

/// Leading glyph for a queue row: the 1-based position normally, a spinner
/// while the server is still fetching the file, a waveform for the current
/// track.
class _MusicBoxQueueRowLeading extends StatelessWidget {
  const _MusicBoxQueueRowLeading({
    required this.item,
    required this.index,
    required this.isCurrent,
  });

  final MusicBoxQueueItem item;
  final int index;
  final bool isCurrent;

  @override
  Widget build(BuildContext context) {
    final loading =
        item.status == MusicBoxQueueItemStatus.pending ||
        item.status == MusicBoxQueueItemStatus.downloading;
    if (loading) {
      return SizedBox(
        key: ValueKey<String>('music-box-queue-leading-loading:${item.id}'),
        width: 14,
        height: 14,
        child: const CircularProgressIndicator(strokeWidth: 1.8),
      );
    }
    if (isCurrent) {
      return Icon(
        Icons.graphic_eq,
        key: ValueKey<String>('music-box-queue-leading-playing:${item.id}'),
        size: 18,
        color: UiColors.accent,
        semanticLabel: '正在播放',
      );
    }
    if (item.status == MusicBoxQueueItemStatus.failed) {
      return Icon(
        Icons.error_outline,
        key: ValueKey<String>('music-box-queue-leading-failed:${item.id}'),
        size: 16,
        color: UiColors.danger,
      );
    }
    return Text(
      '${index + 1}',
      key: ValueKey<String>('music-box-queue-leading-index:${item.id}'),
      style: const TextStyle(
        color: UiColors.textMuted,
        fontSize: 11,
        fontWeight: FontWeight.w600,
        fontFeatures: [FontFeature.tabularFigures()],
      ),
    );
  }
}

/// A saved playlist block in the flat list. Tapping the row unfolds its
/// tracks in place; playback starts from a specific song inside, never from
/// the playlist as a whole. The leading icon carries the playlist card
/// (creator, count, 查看/编辑歌单).
class _MusicBoxPlaylistRow extends StatefulWidget {
  const _MusicBoxPlaylistRow({
    super.key,
    required this.name,
    required this.itemCount,
    required this.playing,
    this.open = false,
    required this.icon,
    required this.onOpen,
    this.hoverColor = UiColors.surfaceLow,
    this.leadingCard,
  });

  final String name;
  final int itemCount;
  final bool playing;
  final bool open;
  final IconData icon;
  final VoidCallback onOpen;
  final Color hoverColor;

  /// Wraps the leading icon; used to attach a [MusicPlaylistHoverCard].
  final Widget Function(Widget icon)? leadingCard;

  @override
  State<_MusicBoxPlaylistRow> createState() => _MusicBoxPlaylistRowState();
}

class _MusicBoxPlaylistRowState extends State<_MusicBoxPlaylistRow> {
  bool _hovered = false;

  void _setHovered(bool hovered) {
    if (_hovered == hovered) return;
    setState(() => _hovered = hovered);
  }

  @override
  Widget build(BuildContext context) {
    final icon = Icon(
      widget.playing ? Icons.graphic_eq : widget.icon,
      size: 18,
      color: widget.playing ? UiColors.accent : UiColors.textSecondary,
    );
    final leading = widget.leadingCard?.call(icon) ?? icon;
    return Semantics(
      button: true,
      label: widget.name,
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => _setHovered(true),
        onExit: (_) => _setHovered(false),
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: widget.onOpen,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 80),
            curve: Curves.easeOutCubic,
            constraints: const BoxConstraints(
              minHeight: _musicBoxRowMinHeight,
            ),
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
            decoration: BoxDecoration(
              color: widget.playing
                  ? UiColors.selected
                  : _hovered
                  ? widget.hoverColor
                  : Colors.transparent,
              borderRadius: BorderRadius.circular(UiRadii.sm),
            ),
            child: Row(
              children: [
                SizedBox(
                  width: _musicBoxRowLeadingWidth,
                  child: Center(child: leading),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.name,
                        style: TextStyle(
                          color: widget.playing
                              ? UiColors.accent
                              : UiColors.text,
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        widget.playing
                            ? '正在播放 · ${widget.itemCount} 首'
                            : '${widget.itemCount} 首',
                        style: const TextStyle(
                          color: UiColors.textMuted,
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 2),
                Icon(
                  widget.open ? Icons.expand_more : Icons.chevron_right,
                  size: 16,
                  color: UiColors.textMuted,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// The compact status line above a list: what it is, how many songs, and a
/// slot for the one contextual action that belongs to the whole list.
class _MusicBoxSectionHeader extends StatelessWidget {
  const _MusicBoxSectionHeader({
    required this.title,
    this.leading,
    this.trailing = const <Widget>[],
  });

  final String title;
  final Widget? leading;
  final List<Widget> trailing;

  @override
  Widget build(BuildContext context) {
    // Raised buttons paint their hover lift and base depth below the nominal
    // size, so the row must size to its tallest action rather than clip it.
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: _musicBoxControlHeight),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          if (leading != null) ...[leading!, const SizedBox(width: 4)],
          Expanded(
            child: Text(
              title,
              maxLines: 1,
              softWrap: false,
              overflow: TextOverflow.fade,
              style: const TextStyle(
                color: UiColors.textSecondary,
                fontSize: 11,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          for (final action in trailing) ...[
            const SizedBox(width: 4),
            action,
          ],
        ],
      ),
    );
  }
}

class _MusicBoxEmpty extends StatelessWidget {
  const _MusicBoxEmpty({
    required this.icon,
    required this.message,
    this.hint,
    this.actionKey,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String message;
  final String? hint;
  final Key? actionKey;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: FittedBox(
        fit: BoxFit.scaleDown,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 30, color: UiColors.textMuted),
            const SizedBox(height: 10),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(
                color: UiColors.textMuted,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
            if (hint != null) ...[
              const SizedBox(height: 4),
              Text(
                hint!,
                textAlign: TextAlign.center,
                style: const TextStyle(color: UiColors.textMuted, fontSize: 11),
              ),
            ],
            if (actionLabel != null && onAction != null) ...[
              const SizedBox(height: 14),
              Button(
                key: actionKey,
                icon: const Icon(Icons.add),
                tone: ButtonTone.primary,
                height: 34,
                padding: const EdgeInsets.symmetric(horizontal: 13),
                onPressed: onAction,
                child: Text(actionLabel!),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _MusicBoxLoading extends StatelessWidget {
  const _MusicBoxLoading();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: SizedBox.square(
        dimension: 22,
        child: CircularProgressIndicator(strokeWidth: 2),
      ),
    );
  }
}

class _MusicBoxRetryState extends StatelessWidget {
  const _MusicBoxRetryState({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.error_outline, size: 28, color: UiColors.textMuted),
          const SizedBox(height: 8),
          Text(
            message,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: UiColors.textMuted,
              fontSize: 11,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 8),
          ButtonIcon(
            key: const ValueKey<String>('music-box-retry'),
            icon: const Icon(Icons.refresh),
            tooltip: '重试',
            onPressed: onRetry,
            size: 30,
          ),
        ],
      ),
    );
  }
}

MusicBoxSearchResult _musicBoxPlaylistItemAsResult(
  PersonalMusicPlaylistItem item,
) {
  return MusicBoxSearchResult(
    trackId: item.trackId,
    name: item.title,
    artists: item.artists,
    source: item.source,
  );
}
