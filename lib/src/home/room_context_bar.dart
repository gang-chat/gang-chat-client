import 'package:flutter/material.dart';

import '../app/room_display.dart' as room_display;
import '../protocol/models.dart';
import '../ui/ui.dart';

enum RoomContextTab { chat, live, members, settings }

/// Persistent bar on top of an open room: identity on the left, the room's
/// four areas (chat, voice, members, settings) as tabs on the right.
class RoomContextBar extends StatelessWidget {
  const RoomContextBar({
    super.key,
    required this.title,
    required this.avatarLabel,
    required this.avatarUrl,
    required this.defaultAvatarKey,
    required this.memberCount,
    required this.onlineMemberCount,
    required this.liveParticipantCount,
    required this.liveAvatarPreview,
    required this.voiceJoined,
    required this.activeTab,
    required this.hasPendingJoinRequests,
    required this.onTabSelected,
    this.onBack,
    this.compact = false,
  });

  final String title;
  final String avatarLabel;
  final String? avatarUrl;
  final String defaultAvatarKey;
  final int? memberCount;
  final int? onlineMemberCount;
  final int? liveParticipantCount;
  final List<UserSummary> liveAvatarPreview;
  final bool voiceJoined;
  final RoomContextTab activeTab;
  final bool hasPendingJoinRequests;
  final ValueChanged<RoomContextTab> onTabSelected;
  final VoidCallback? onBack;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final identity = _RoomIdentity(
      title: title,
      avatarLabel: avatarLabel,
      avatarUrl: avatarUrl,
      defaultAvatarKey: defaultAvatarKey,
      memberCount: memberCount,
      onlineMemberCount: onlineMemberCount,
      compact: compact,
    );
    final tabs = _RoomTabs(
      activeTab: activeTab,
      liveParticipantCount: liveParticipantCount ?? 0,
      voiceJoined: voiceJoined,
      hasPendingJoinRequests: hasPendingJoinRequests,
      onTabSelected: onTabSelected,
      expanded: compact,
    );
    final back = onBack;
    final decoration = const BoxDecoration(
      border: Border(bottom: BorderSide(color: UiColors.border)),
    );
    if (compact) {
      return DecoratedBox(
        key: const ValueKey('room-context-bar'),
        decoration: decoration,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(6, 6, 12, 10),
          child: Column(
            children: [
              SizedBox(
                height: 48,
                child: Row(
                  children: [
                    if (back != null)
                      ButtonIconPlain(
                        key: const ValueKey('chat-header-back-button'),
                        tooltip: '返回房间列表',
                        onPressed: back,
                        width: 36,
                        height: 44,
                        icon: const Icon(Icons.arrow_back_rounded),
                      )
                    else
                      const SizedBox(width: 6),
                    Expanded(child: identity),
                  ],
                ),
              ),
              const SizedBox(height: 6),
              Padding(padding: const EdgeInsets.only(left: 6), child: tabs),
            ],
          ),
        ),
      );
    }
    final liveCount = liveParticipantCount ?? 0;
    return DecoratedBox(
      key: const ValueKey('room-context-bar'),
      decoration: decoration,
      child: SizedBox(
        height: 68,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Row(
            children: [
              if (back != null) ...[
                ButtonIconPlain(
                  key: const ValueKey('chat-header-back-button'),
                  tooltip: '返回房间列表',
                  onPressed: back,
                  width: 30,
                  height: 44,
                  icon: const Icon(Icons.arrow_back_rounded),
                ),
                const SizedBox(width: 6),
              ],
              Expanded(child: identity),
              if (liveCount > 0) ...[
                const SizedBox(width: 12),
                Flexible(
                  child: _LiveAvatarPreview(
                    users: liveAvatarPreview,
                    participantCount: liveCount,
                    voiceJoined: voiceJoined,
                  ),
                ),
              ],
              const SizedBox(width: 16),
              tabs,
            ],
          ),
        ),
      ),
    );
  }
}

class _RoomIdentity extends StatelessWidget {
  const _RoomIdentity({
    required this.title,
    required this.avatarLabel,
    required this.avatarUrl,
    required this.defaultAvatarKey,
    required this.memberCount,
    required this.onlineMemberCount,
    required this.compact,
  });

  final String title;
  final String avatarLabel;
  final String? avatarUrl;
  final String defaultAvatarKey;
  final int? memberCount;
  final int? onlineMemberCount;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Avatar(
          key: const ValueKey('chat-header-room-avatar'),
          label: avatarLabel,
          imageUrl: AppConfigScope.of(context).resolveAssetUrl(avatarUrl),
          defaultAvatarKey: defaultAvatarKey,
          size: compact ? 34 : 38,
          showBorder: false,
          rounded: true,
        ),
        const SizedBox(width: 12),
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
                style: UiTypography.title.copyWith(
                  fontSize: compact ? 16 : 18,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                roomContextMeta(
                  memberCount: memberCount,
                  onlineMemberCount: onlineMemberCount,
                ),
                key: const ValueKey('chat-header-room-meta'),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: UiTypography.mono,
              ),
            ],
          ),
        ),
      ],
    );
  }
}

String roomContextMeta({
  required int? memberCount,
  required int? onlineMemberCount,
}) {
  final parts = <String>[];
  final members = memberCount ?? 0;
  if (members > 0) parts.add('$members 名成员');
  final online = onlineMemberCount ?? 0;
  if (online > 0) parts.add('$online 人在线');
  return parts.isEmpty ? '就绪' : parts.join(' · ');
}

class _LiveAvatarPreview extends StatelessWidget {
  const _LiveAvatarPreview({
    required this.users,
    required this.participantCount,
    required this.voiceJoined,
  });

  final List<UserSummary> users;
  final int participantCount;
  final bool voiceJoined;

  @override
  Widget build(BuildContext context) {
    final config = AppConfigScope.of(context);
    final color = voiceJoined ? UiColors.success : UiColors.presenceVoice;
    return LayoutBuilder(
      builder: (context, constraints) {
        const avatarSize = 24.0;
        const overlap = 8.0;
        final label = '$participantCount 人正在语音';
        final labelWidth = (TextPainter(
          text: TextSpan(text: label, style: UiTypography.caption),
          textDirection: Directionality.of(context),
          maxLines: 1,
        )..layout()).width;
        var visible = users.length.clamp(0, 4);
        while (visible > 0 &&
            avatarSize + (visible - 1) * (avatarSize - overlap) + 18 + labelWidth >
                constraints.maxWidth) {
          visible -= 1;
        }
        final visibleUsers = users.take(visible).toList();
        return Row(
          key: const ValueKey('chat-header-live-preview'),
          mainAxisSize: MainAxisSize.min,
          children: [
            if (visibleUsers.isNotEmpty) ...[
              SizedBox(
                width:
                    avatarSize +
                    (visibleUsers.length - 1) * (avatarSize - overlap),
                height: avatarSize,
                child: Stack(
                  children: [
                    for (var index = 0; index < visibleUsers.length; index++)
                      Positioned(
                        left: index * (avatarSize - overlap),
                        child: Container(
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            border: Border.all(
                              color: UiColors.surfaceLow,
                              width: 2,
                            ),
                          ),
                          child: Avatar(
                            label: room_display.userAvatarLabel(
                              visibleUsers[index],
                            ),
                            imageUrl: config.resolveAssetUrl(
                              visibleUsers[index].avatarUrl,
                            ),
                            defaultAvatarKey:
                                visibleUsers[index].defaultAvatarKey,
                            size: avatarSize - 4,
                            showBorder: false,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
            ],
            Icon(
              Icons.graphic_eq_rounded,
              key: const ValueKey('chat-header-live-preview-icon'),
              size: 14,
              color: color,
            ),
            const SizedBox(width: 4),
            Flexible(
              child: Text(
                label,
                key: const ValueKey('chat-header-live-preview-count'),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: UiTypography.caption.copyWith(
                  color: color,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}

class _RoomTabs extends StatelessWidget {
  const _RoomTabs({
    required this.activeTab,
    required this.liveParticipantCount,
    required this.voiceJoined,
    required this.hasPendingJoinRequests,
    required this.onTabSelected,
    required this.expanded,
  });

  final RoomContextTab activeTab;
  final int liveParticipantCount;
  final bool voiceJoined;
  final bool hasPendingJoinRequests;
  final ValueChanged<RoomContextTab> onTabSelected;
  final bool expanded;

  @override
  Widget build(BuildContext context) {
    Widget tab(Widget child) => expanded
        ? Expanded(
            child: FittedBox(fit: BoxFit.scaleDown, child: child),
          )
        : child;
    return Container(
      height: 40,
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: UiColors.fieldFill,
        borderRadius: BorderRadius.circular(UiRadii.pill),
        border: Border.all(color: UiColors.border),
      ),
      child: Row(
        mainAxisSize: expanded ? MainAxisSize.max : MainAxisSize.min,
        children: [
          tab(
            _RoomTabButton(
              key: const ValueKey('room-tab-chat'),
              icon: Icons.chat_bubble_outline_rounded,
              activeIcon: Icons.chat_bubble_rounded,
              label: '聊天',
              tooltip: '聊天',
              selected: activeTab == RoomContextTab.chat,
              onPressed: () => onTabSelected(RoomContextTab.chat),
            ),
          ),
          tab(
            _RoomTabButton(
              key: const ValueKey('chat-header-live-button'),
              icon: Icons.graphic_eq_rounded,
              activeIcon: Icons.graphic_eq_rounded,
              label: '语音',
              tooltip: '进入语音频道',
              selected: activeTab == RoomContextTab.live,
              onPressed: () => onTabSelected(RoomContextTab.live),
              trailing: liveParticipantCount > 0
                  ? Container(
                      key: const ValueKey('room-tab-live-count'),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 1,
                      ),
                      decoration: BoxDecoration(
                        color:
                            (voiceJoined
                                    ? UiColors.success
                                    : UiColors.presenceVoice)
                                .withValues(alpha: 0.18),
                        borderRadius: BorderRadius.circular(UiRadii.pill),
                      ),
                      child: Text(
                        '$liveParticipantCount',
                        style: UiTypography.caption.copyWith(
                          color: voiceJoined
                              ? UiColors.success
                              : UiColors.presenceVoice,
                          fontWeight: FontWeight.w700,
                          fontSize: 10.5,
                        ),
                      ),
                    )
                  : null,
            ),
          ),
          tab(
            _RoomTabButton(
              key: const ValueKey('room-tab-members'),
              icon: Icons.group_outlined,
              activeIcon: Icons.group_rounded,
              label: '成员',
              tooltip: '房间成员',
              selected: activeTab == RoomContextTab.members,
              onPressed: () => onTabSelected(RoomContextTab.members),
              trailing: hasPendingJoinRequests
                  ? const BadgeDot(
                      key: ValueKey('room-members-entry-badge'),
                      size: 7,
                      borderColor: Colors.transparent,
                    )
                  : null,
            ),
          ),
          tab(
            _RoomTabButton(
              key: const ValueKey('room-tab-settings'),
              icon: Icons.tune_rounded,
              activeIcon: Icons.tune_rounded,
              label: '设置',
              tooltip: '房间设置',
              selected: activeTab == RoomContextTab.settings,
              onPressed: () => onTabSelected(RoomContextTab.settings),
            ),
          ),
        ],
      ),
    );
  }
}

class _RoomTabButton extends StatelessWidget {
  const _RoomTabButton({
    super.key,
    required this.icon,
    required this.activeIcon,
    required this.label,
    required this.tooltip,
    required this.selected,
    required this.onPressed,
    this.trailing,
  });

  final IconData icon;
  final IconData activeIcon;
  final String label;
  final String tooltip;
  final bool selected;
  final VoidCallback onPressed;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final trailing = this.trailing;
    return PressableSurface(
      height: 32,
      tooltip: tooltip,
      selected: selected,
      onPressed: onPressed,
      backgroundColor: Colors.transparent,
      hoverBackgroundColor: UiColors.surface,
      selectedBackgroundColor: UiColors.inverse,
      borderColor: Colors.transparent,
      selectedBorderColor: Colors.transparent,
      borderRadius: UiRadii.pill,
      padding: const EdgeInsets.symmetric(horizontal: 14),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            selected ? activeIcon : icon,
            size: 15,
            color: selected ? UiColors.onInverse : UiColors.textMuted,
          ),
          const SizedBox(width: 6),
          Text(
            label,
            style: UiTypography.label.copyWith(
              fontSize: 13,
              color: selected ? UiColors.onInverse : UiColors.textSecondary,
              fontWeight: FontWeight.w700,
            ),
          ),
          if (trailing != null) ...[const SizedBox(width: 6), trailing],
        ],
      ),
    );
  }
}
