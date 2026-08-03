part of 'chat_pane.dart';

class _RoomHeader extends StatelessWidget {
  const _RoomHeader({
    this.onNavigateBack,
    required this.title,
    required this.avatarLabel,
    required this.avatarUrl,
    required this.defaultAvatarKey,
    required this.memberCount,
    required this.onlineMemberCount,
    required this.liveParticipantCount,
    required this.liveAvatarPreview,
    required this.hasPendingJoinRequests,
    required this.onLivePressed,
    required this.onMembersPressed,
    required this.onSettingsPressed,
  });

  final VoidCallback? onNavigateBack;
  final String title;
  final String avatarLabel;
  final String? avatarUrl;
  final String defaultAvatarKey;
  final int? memberCount;
  final int? onlineMemberCount;
  final int? liveParticipantCount;
  final List<UserSummary> liveAvatarPreview;
  final bool hasPendingJoinRequests;
  final VoidCallback onLivePressed;
  final VoidCallback onMembersPressed;
  final VoidCallback onSettingsPressed;

  @override
  Widget build(BuildContext context) {
    const headerTopInset = _chatHeaderVisualTopInset - _headerSurfaceHoverLift;
    return SizedBox(
      height: _chatHeaderHeight,
      child: Padding(
        padding: EdgeInsets.fromLTRB(
          _chatHeaderHorizontalInset,
          headerTopInset,
          _chatHeaderHorizontalInset,
          _chatHeaderBottomInset,
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: _LiveChannelHeaderCard(
                onNavigateBack: onNavigateBack,
                title: title,
                avatarLabel: avatarLabel,
                avatarUrl: avatarUrl,
                defaultAvatarKey: defaultAvatarKey,
                memberCount: memberCount,
                onlineMemberCount: onlineMemberCount,
                liveParticipantCount: liveParticipantCount,
                liveAvatarPreview: liveAvatarPreview,
                onPressed: onLivePressed,
              ),
            ),
            const SizedBox(width: 10),
            _RoomHeaderActions(
              hasPendingJoinRequests: hasPendingJoinRequests,
              onMembersPressed: onMembersPressed,
              onSettingsPressed: onSettingsPressed,
            ),
          ],
        ),
      ),
    );
  }
}

class _LiveChannelHeaderCard extends StatelessWidget {
  const _LiveChannelHeaderCard({
    this.onNavigateBack,
    required this.title,
    required this.avatarLabel,
    required this.avatarUrl,
    required this.defaultAvatarKey,
    required this.memberCount,
    required this.onlineMemberCount,
    required this.liveParticipantCount,
    required this.liveAvatarPreview,
    required this.onPressed,
  });

  final VoidCallback? onNavigateBack;
  final String title;
  final String avatarLabel;
  final String? avatarUrl;
  final String defaultAvatarKey;
  final int? memberCount;
  final int? onlineMemberCount;
  final int? liveParticipantCount;
  final List<UserSummary> liveAvatarPreview;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final liveActive = (liveParticipantCount ?? 0) > 0;
    if (!HomeAdaptiveLayout.usesCompactLayout(context)) {
      return _buildDesktopCard(liveActive);
    }
    return _buildAndroidCard(liveActive);
  }

  Widget _buildDesktopCard(bool liveActive) {
    return PressableSurface(
      key: const ValueKey('chat-header-live-button'),
      width: double.infinity,
      height: _liveHeaderCardHeight,
      hoverLift: _headerSurfaceHoverLift,
      baseDepth: _headerSurfaceBaseDepth,
      borderRadius: UiRadii.md,
      backgroundColor: UiColors.surface,
      selectedBackgroundColor: UiColors.selected,
      borderColor: UiColors.border,
      selectedBorderColor: UiColors.selectedBorder,
      selected: liveActive,
      padding: const EdgeInsets.symmetric(horizontal: 10),
      tooltip: '进入语音频道',
      onPressed: onPressed,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final previewMaxWidth = liveActive
              ? math.min(
                  _desktopLiveHeaderPreviewMaxWidth,
                  constraints.maxWidth,
                )
              : 0.0;
          final roomInfoWidth = math.max(
            0.0,
            constraints.maxWidth -
                (previewMaxWidth > 0
                    ? previewMaxWidth + _liveHeaderSideGap
                    : 0),
          );
          return Stack(
            children: [
              if (roomInfoWidth >= _liveHeaderRoomInfoMinWidth)
                Align(
                  alignment: Alignment.centerLeft,
                  child: SizedBox(
                    width: roomInfoWidth,
                    child: _LiveHeaderRoomInfo(
                      title: title,
                      avatarLabel: avatarLabel,
                      avatarUrl: avatarUrl,
                      defaultAvatarKey: defaultAvatarKey,
                      memberCount: memberCount,
                      onlineMemberCount: onlineMemberCount,
                      liveActive: liveActive,
                    ),
                  ),
                ),
              if (liveActive &&
                  previewMaxWidth >= _desktopLiveHeaderPreviewMinWidth)
                Align(
                  alignment: Alignment.centerRight,
                  child: ConstrainedBox(
                    constraints: BoxConstraints(maxWidth: previewMaxWidth),
                    child: _LiveHeaderAvatarPreview(
                      users: liveAvatarPreview,
                      participantCount: liveParticipantCount ?? 0,
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }

  Widget _buildAndroidCard(bool liveActive) {
    return PressableSurface(
      key: const ValueKey('chat-header-live-button'),
      width: double.infinity,
      height: _liveHeaderCardHeight,
      hoverLift: _headerSurfaceHoverLift,
      baseDepth: _headerSurfaceBaseDepth,
      borderRadius: UiRadii.md,
      backgroundColor: UiColors.surface,
      selectedBackgroundColor: UiColors.selected,
      borderColor: UiColors.border,
      selectedBorderColor: UiColors.selectedBorder,
      selected: liveActive,
      interactive: true,
      padding: const EdgeInsets.only(
        left: _liveHeaderLeftPadding,
        right: _liveHeaderRightPadding,
      ),
      child: Row(
        children: [
          if (onNavigateBack != null) ...[
            _LiveHeaderBackColumn(onPressed: onNavigateBack!),
            const SizedBox(width: _liveHeaderBackGap),
          ],
          Expanded(
            child: Tooltip(
              message: '进入语音频道',
              child: Semantics(
                button: true,
                label: '进入语音频道',
                onTap: onPressed,
                child: MouseRegion(
                  cursor: SystemMouseCursors.click,
                  child: GestureDetector(
                    behavior: HitTestBehavior.opaque,
                    excludeFromSemantics: true,
                    onTap: onPressed,
                    child: LayoutBuilder(
                      builder: (context, constraints) {
                        final previewMaxWidth = liveActive
                            ? math.min(
                                _androidLiveHeaderPreviewMaxWidth,
                                constraints.maxWidth,
                              )
                            : 0.0;
                        final roomInfoWidth = math.max(
                          0.0,
                          constraints.maxWidth -
                              (previewMaxWidth > 0
                                  ? previewMaxWidth + _liveHeaderSideGap
                                  : 0),
                        );
                        return Stack(
                          children: [
                            if (roomInfoWidth >= _liveHeaderRoomInfoMinWidth)
                              Align(
                                alignment: Alignment.centerLeft,
                                child: SizedBox(
                                  width: roomInfoWidth,
                                  child: _LiveHeaderRoomInfo(
                                    title: title,
                                    avatarLabel: avatarLabel,
                                    avatarUrl: avatarUrl,
                                    defaultAvatarKey: defaultAvatarKey,
                                    memberCount: memberCount,
                                    onlineMemberCount: onlineMemberCount,
                                    liveActive: liveActive,
                                  ),
                                ),
                              ),
                            if (liveActive &&
                                previewMaxWidth >=
                                    _androidLiveHeaderPreviewMinWidth)
                              Align(
                                alignment: Alignment.centerRight,
                                child: ConstrainedBox(
                                  constraints: BoxConstraints(
                                    maxWidth: previewMaxWidth,
                                  ),
                                  child: _LiveHeaderParticipantCount(
                                    participantCount: liveParticipantCount ?? 0,
                                  ),
                                ),
                              ),
                          ],
                        );
                      },
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

const _liveHeaderLeftPadding = 7.0;
const _liveHeaderRightPadding = 13.0;
const _liveHeaderBackColumnWidth = 30.0;
const _liveHeaderBackGap = 3.0;
const _androidLiveHeaderPreviewMaxWidth = 52.0;
const _desktopLiveHeaderPreviewMaxWidth = 190.0;
const _liveHeaderSideGap = 10.0;
const _liveHeaderRoomInfoMinWidth = 44.0;
const _androidLiveHeaderPreviewMinWidth = 32.0;
const _desktopLiveHeaderPreviewMinWidth = 60.0;

class _LiveHeaderBackColumn extends StatelessWidget {
  const _LiveHeaderBackColumn({required this.onPressed});

  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return ButtonIconPlain(
      key: const ValueKey('chat-header-back-button'),
      tooltip: '返回房间列表',
      onPressed: onPressed,
      width: _liveHeaderBackColumnWidth,
      height: double.infinity,
      icon: const Icon(Icons.arrow_back_rounded),
    );
  }
}

class _LiveHeaderRoomInfo extends StatelessWidget {
  const _LiveHeaderRoomInfo({
    required this.title,
    required this.avatarLabel,
    required this.avatarUrl,
    required this.defaultAvatarKey,
    required this.memberCount,
    required this.onlineMemberCount,
    required this.liveActive,
  });

  final String title;
  final String avatarLabel;
  final String? avatarUrl;
  final String defaultAvatarKey;
  final int? memberCount;
  final int? onlineMemberCount;
  final bool liveActive;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Avatar(
          key: const ValueKey('chat-header-room-avatar'),
          label: avatarLabel,
          imageUrl: AppConfigScope.of(context).resolveAssetUrl(avatarUrl),
          defaultAvatarKey: defaultAvatarKey,
          size: 34,
          active: liveActive,
          activeBorderWidth: 1.1,
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                key: const ValueKey('chat-header-room-title'),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: UiTypography.label.copyWith(
                  color: UiColors.text,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                _roomMeta(
                  memberCount: memberCount,
                  onlineMemberCount: onlineMemberCount,
                ),
                key: const ValueKey('chat-header-room-meta'),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: UiTypography.label.copyWith(color: UiColors.textMuted),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _LiveHeaderParticipantCount extends StatelessWidget {
  const _LiveHeaderParticipantCount({required this.participantCount});

  final int participantCount;

  @override
  Widget build(BuildContext context) {
    if (participantCount <= 0) return const SizedBox.shrink();
    return Row(
      key: const ValueKey('chat-header-live-preview'),
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(
          Icons.volume_up,
          key: ValueKey('chat-header-live-preview-icon'),
          color: UiColors.accent,
          size: 18,
        ),
        const SizedBox(width: 6),
        Text(
          '$participantCount',
          key: const ValueKey('chat-header-live-preview-count'),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: UiTypography.label.copyWith(
            color: Colors.white,
            fontWeight: FontWeight.w700,
            fontSize: 13,
          ),
        ),
      ],
    );
  }
}

class _LiveHeaderAvatarPreview extends StatelessWidget {
  const _LiveHeaderAvatarPreview({
    required this.users,
    required this.participantCount,
  });

  final List<UserSummary> users;
  final int participantCount;

  @override
  Widget build(BuildContext context) {
    if (participantCount <= 0 || users.isEmpty) return const SizedBox.shrink();
    final config = AppConfigScope.of(context);
    final exactCountLabel = '共 $participantCount 人';
    final overflowCountLabel = '等共 $participantCount 人';
    final labelStyle = UiTypography.label.copyWith(
      color: Colors.white,
      fontWeight: FontWeight.w600,
      fontSize: 11,
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        const avatarSize = 24.0;
        const iconSize = 18.0;
        const overlap = 8.0;
        const gap = 7.0;
        final textDirection = Directionality.of(context);
        double measureLabelWidth(String label) {
          final painter = TextPainter(
            text: TextSpan(text: label, style: labelStyle),
            textDirection: textDirection,
            maxLines: 1,
          )..layout();
          return painter.width;
        }

        final maxVisible = math.min(5, users.length);
        var visibleCount = 0;
        var countLabel = '';
        for (var count = maxVisible; count >= 1; count -= 1) {
          final avatarWidth = avatarSize + (count - 1) * (avatarSize - overlap);
          final candidateLabel = participantCount > count
              ? overflowCountLabel
              : exactCountLabel;
          final neededWidth =
              iconSize +
              gap +
              avatarWidth +
              gap +
              measureLabelWidth(candidateLabel);
          if (neededWidth <= constraints.maxWidth) {
            visibleCount = count;
            countLabel = candidateLabel;
            break;
          }
        }
        if (visibleCount == 0) return const SizedBox.shrink();
        final visibleUsers = users.take(visibleCount).toList();
        final avatarWidth =
            avatarSize + (visibleUsers.length - 1) * (avatarSize - overlap);
        return Row(
          key: const ValueKey('chat-header-live-preview'),
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              Icons.volume_up,
              key: ValueKey('chat-header-live-preview-icon'),
              color: UiColors.accent,
              size: iconSize,
            ),
            const SizedBox(width: gap),
            SizedBox(
              width: avatarWidth,
              height: avatarSize,
              child: Stack(
                clipBehavior: Clip.none,
                children: [
                  for (var index = 0; index < visibleUsers.length; index += 1)
                    Positioned(
                      left: index * (avatarSize - overlap),
                      child: Avatar(
                        label: room_display.userAvatarLabel(
                          visibleUsers[index],
                        ),
                        imageUrl: config.resolveAssetUrl(
                          visibleUsers[index].avatarUrl,
                        ),
                        defaultAvatarKey: visibleUsers[index].defaultAvatarKey,
                        size: avatarSize,
                        active: true,
                        activeBorderWidth: 1,
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(width: gap),
            Text(
              countLabel,
              key: const ValueKey('chat-header-live-preview-count'),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: labelStyle,
            ),
          ],
        );
      },
    );
  }
}

class _RoomHeaderActions extends StatelessWidget {
  const _RoomHeaderActions({
    required this.hasPendingJoinRequests,
    required this.onMembersPressed,
    required this.onSettingsPressed,
  });

  final bool hasPendingJoinRequests;
  final VoidCallback onMembersPressed;
  final VoidCallback onSettingsPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: _headerActionButtonSize,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _HeaderIconButton(
            tooltip: '房间成员',
            icon: Icons.groups_outlined,
            showBadge: hasPendingJoinRequests,
            badgeKey: const ValueKey('room-members-entry-badge'),
            onPressed: onMembersPressed,
          ),
          const SizedBox(height: _headerActionGap),
          _HeaderIconButton(
            tooltip: '房间设置',
            icon: Icons.more_horiz,
            onPressed: onSettingsPressed,
          ),
        ],
      ),
    );
  }
}

class _HeaderIconButton extends StatelessWidget {
  const _HeaderIconButton({
    required this.tooltip,
    required this.icon,
    required this.onPressed,
    this.showBadge = false,
    this.badgeKey,
  });

  final String tooltip;
  final IconData icon;
  final VoidCallback onPressed;
  final bool showBadge;
  final Key? badgeKey;

  @override
  Widget build(BuildContext context) {
    return PressableSurface(
      width: _headerActionButtonSize,
      height: _headerActionButtonSize,
      hoverLift: _headerSurfaceHoverLift,
      baseDepth: _headerSurfaceBaseDepth,
      borderRadius: UiRadii.sm,
      tooltip: tooltip,
      padding: EdgeInsets.zero,
      onPressed: onPressed,
      backgroundColor: UiColors.surface,
      borderColor: UiColors.border,
      child: Center(
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            Icon(icon, size: 16, color: UiColors.textSecondary),
            if (showBadge)
              Positioned(
                key: badgeKey,
                top: -5,
                right: -5,
                child: const BadgeDot(size: 8),
              ),
          ],
        ),
      ),
    );
  }
}

String _roomMeta({required int? memberCount, required int? onlineMemberCount}) {
  final parts = <String>[];
  final members = memberCount ?? 0;
  if (members > 0) parts.add('$members 名成员');
  final online = onlineMemberCount ?? 0;
  if (online > 0) parts.add('$online 人在线');
  return parts.isEmpty ? '就绪' : parts.join(' · ');
}
