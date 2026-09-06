import 'package:flutter/material.dart';

import '../app/network_latency.dart' as network_latency;
import '../app/room_display.dart' as room_display;
import '../protocol/models.dart';
import '../ui/ui.dart';
import 'navigation.dart';

const _sidebarTopPadding = 16.0;
const _sidebarBottomPadding = 16.0;
const _homeSidebarHeaderHeight = 30.0;
const _homeSidebarHeaderGap = 14.0;
const _serverCardHeight = 68.0;
const _serverCardHoverLift = 2.0;
const _serverCardBaseDepth = 4.0;
const _serverCardGap = 10.0;
const _serverCardHorizontalPadding = 10.0;
const _serverListScrollbarGutter = 8.0;
const _footerButtonSize = 38.0;
const _footerButtonGap = 8.0;
const _footerButtonOuterHeight = _footerButtonSize + 3.0 + 5.0;
const _compactFooterBreakpoint = 130.0;
const _compactSummaryBreakpoint = 88.0;
const _latencySignalGoodColor = Color(0xFF26B36F);
const _latencySignalFairColor = Color(0xFFE0A12A);
const _latencySignalPoorColor = Color(0xFFE25A5A);

class HomeSidebar extends StatelessWidget {
  const HomeSidebar({
    super.key,
    required this.width,
    required this.currentUser,
    required this.servers,
    required this.timestampNow,
    this.roomDrafts = const {},
    required this.selectedServerId,
    required this.joinedLiveRoomId,
    required this.realtimeReconnecting,
    this.requestRoundTrip,
    required this.searchQuery,
    required this.loading,
    required this.error,
    required this.settingsActive,
    required this.createRoomActive,
    required this.notificationsActive,
    required this.logoutActive,
    required this.hasPendingNotifications,
    required this.pendingNotificationCount,
    required this.onServerSelected,
    required this.onCreateRoom,
    required this.onOpenNotifications,
    required this.onOpenSettings,
    required this.onLogout,
    this.includeWindowChromeOffset = true,
    this.header,
    this.bodyOverride,
    this.footerMiddle,
  });

  final double width;
  final CurrentUser currentUser;
  final List<RoomCard> servers;
  final DateTime timestampNow;
  final Map<String, String> roomDrafts;
  final String? selectedServerId;
  final String? joinedLiveRoomId;
  final bool realtimeReconnecting;
  final Duration? requestRoundTrip;
  final String searchQuery;
  final bool loading;
  final String? error;
  final bool settingsActive;
  final bool createRoomActive;
  final bool notificationsActive;
  final bool logoutActive;
  final bool hasPendingNotifications;
  final int pendingNotificationCount;
  final bool includeWindowChromeOffset;
  final Widget? header;
  final Widget? bodyOverride;
  final Widget? footerMiddle;
  final ValueChanged<RoomCard> onServerSelected;
  final VoidCallback onCreateRoom;
  final VoidCallback onOpenNotifications;
  final VoidCallback onOpenSettings;
  final VoidCallback onLogout;

  @override
  Widget build(BuildContext context) {
    final chrome = WindowChromeInsets.of(context);
    final topChromeOffset = includeWindowChromeOffset
        ? chrome.sidebarTopOffset
        : 0.0;
    return SizedBox(
      width: width,
      child: DecoratedBox(
        decoration: const BoxDecoration(
          color: UiColors.surfaceLow,
          border: Border(right: BorderSide(color: UiColors.border)),
        ),
        child: Padding(
          padding: EdgeInsets.fromLTRB(
            sidebarHorizontalPadding,
            _sidebarTopPadding + topChromeOffset,
            sidebarHorizontalPadding - _serverListScrollbarGutter,
            _sidebarBottomPadding,
          ),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final header = this.header;
              final headerHeight = header == null
                  ? 0.0
                  : _homeSidebarHeaderHeight + _homeSidebarHeaderGap;
              final availableBodyHeight = constraints.maxHeight - headerHeight;
              final showSummary =
                  availableBodyHeight >= _compactSummaryBreakpoint;
              final showFooter =
                  availableBodyHeight >= _compactFooterBreakpoint;
              return Column(
                children: [
                  if (showSummary) ...[
                    Padding(
                      padding: const EdgeInsets.only(
                        right: _serverListScrollbarGutter,
                      ),
                      child: _UserSummaryBar(
                        user: currentUser,
                        inLive: joinedLiveRoomId != null,
                        reconnecting: realtimeReconnecting,
                        requestRoundTrip: requestRoundTrip,
                        logoutActive: logoutActive,
                        onLogout: onLogout,
                      ),
                    ),
                    SizedBox(
                      height: header != null || showFooter
                          ? _homeSidebarHeaderGap
                          : 10,
                    ),
                  ],
                  if (header != null) ...[
                    Padding(
                      padding: const EdgeInsets.only(
                        right: _serverListScrollbarGutter,
                      ),
                      child: SizedBox(
                        height: _homeSidebarHeaderHeight,
                        width: double.infinity,
                        child: header,
                      ),
                    ),
                    const SizedBox(height: _homeSidebarHeaderGap),
                  ],
                  if (bodyOverride != null)
                    Expanded(
                      child: Padding(
                        padding: const EdgeInsets.only(
                          right: _serverListScrollbarGutter,
                        ),
                        child: bodyOverride!,
                      ),
                    )
                  else ...[
                    Expanded(child: _buildServerList(context)),
                    if (showFooter) ...[
                      const SizedBox(height: 12),
                      Padding(
                        padding: const EdgeInsets.only(
                          right: _serverListScrollbarGutter,
                        ),
                        child: _SidebarFooter(
                          settingsActive: settingsActive,
                          createRoomActive: createRoomActive,
                          notificationsActive: notificationsActive,
                          hasPendingNotifications: hasPendingNotifications,
                          pendingNotificationCount: pendingNotificationCount,
                          middle: footerMiddle,
                          onCreateRoom: onCreateRoom,
                          onOpenNotifications: onOpenNotifications,
                          onOpenSettings: onOpenSettings,
                        ),
                      ),
                    ],
                  ],
                ],
              );
            },
          ),
        ),
      ),
    );
  }

  Widget _buildServerList(BuildContext context) {
    if (servers.isEmpty && loading) {
      return const Center(
        child: SizedBox.square(
          dimension: 22,
          child: CircularProgressIndicator(
            strokeWidth: 2.2,
            color: UiColors.accent,
          ),
        ),
      );
    }

    if (servers.isEmpty && error != null) {
      return FloatingNoticeEmitter(
        notices: [
          FloatingNotice(
            message: error!,
            tone: FloatingNoticeTone.error,
            duration: null,
          ),
        ],
        child: Center(
          child: Icon(Icons.error_outline, color: UiColors.danger, size: 22),
        ),
      );
    }

    return _ServerList(
      servers: servers,
      timestampNow: timestampNow,
      roomDrafts: roomDrafts,
      selectedServerId: selectedServerId,
      joinedLiveRoomId: joinedLiveRoomId,
      searchQuery: searchQuery,
      onServerSelected: onServerSelected,
    );
  }
}

class _ServerList extends StatefulWidget {
  const _ServerList({
    required this.servers,
    required this.timestampNow,
    required this.roomDrafts,
    required this.selectedServerId,
    required this.joinedLiveRoomId,
    required this.searchQuery,
    required this.onServerSelected,
  });

  final List<RoomCard> servers;
  final DateTime timestampNow;
  final Map<String, String> roomDrafts;
  final String? selectedServerId;
  final String? joinedLiveRoomId;
  final String searchQuery;
  final ValueChanged<RoomCard> onServerSelected;

  @override
  State<_ServerList> createState() => _ServerListState();
}

class _ServerListState extends State<_ServerList> {
  final ScrollController _controller = ScrollController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final platform = Theme.of(context).platform;
    return RawScrollbar(
      key: const ValueKey('home-sidebar-room-scrollbar'),
      controller: _controller,
      interactive: true,
      radius: const Radius.circular(999),
      thickness: 7,
      thumbColor: UiColors.textMuted.withValues(alpha: 0.82),
      // This scrollbar is already laid out inside the app's safe content.
      // RawScrollbar otherwise inherits the window MediaQuery padding and
      // applies the Android status/navigation insets a second time.
      padding: platform == TargetPlatform.android ? EdgeInsets.zero : null,
      child: ScrollConfiguration(
        behavior: ScrollConfiguration.of(context).copyWith(scrollbars: false),
        child: ListView.separated(
          key: const ValueKey('home-sidebar-room-list'),
          controller: _controller,
          primary: false,
          padding: const EdgeInsets.only(right: _serverListScrollbarGutter),
          itemCount: widget.servers.length,
          separatorBuilder: (context, index) =>
              const SizedBox(height: _serverCardGap),
          itemBuilder: (context, index) {
            final server = widget.servers[index];
            return _ServerCard(
              server: server,
              timestampNow: widget.timestampNow,
              draft: widget.roomDrafts[server.id],
              selected: server.id == widget.selectedServerId,
              voiceJoined: server.id == widget.joinedLiveRoomId,
              searchQuery: widget.searchQuery,
              onPressed: () => widget.onServerSelected(server),
            );
          },
        ),
      ),
    );
  }
}

class _SidebarFooter extends StatelessWidget {
  const _SidebarFooter({
    required this.settingsActive,
    required this.createRoomActive,
    required this.notificationsActive,
    required this.hasPendingNotifications,
    required this.pendingNotificationCount,
    this.middle,
    required this.onCreateRoom,
    required this.onOpenNotifications,
    required this.onOpenSettings,
  });

  final bool settingsActive;
  final bool createRoomActive;
  final bool notificationsActive;
  final bool hasPendingNotifications;
  final int pendingNotificationCount;
  final Widget? middle;
  final VoidCallback onCreateRoom;
  final VoidCallback onOpenNotifications;
  final VoidCallback onOpenSettings;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: _footerButtonOuterHeight,
      child: Row(
        children: [
          ButtonIcon(
            key: const ValueKey('home-sidebar-create-room-button'),
            tooltip: '创建房间',
            icon: const Icon(Icons.add_circle_outline),
            selected: createRoomActive,
            onPressed: onCreateRoom,
            size: _footerButtonSize,
          ),
          const SizedBox(width: _footerButtonGap),
          _NotificationFooterButton(
            selected: notificationsActive,
            hasPending: hasPendingNotifications,
            count: pendingNotificationCount,
            onPressed: onOpenNotifications,
          ),
          if (middle == null)
            const Spacer()
          else ...[
            const SizedBox(width: _footerButtonGap),
            Expanded(child: Center(child: middle)),
            const SizedBox(width: _footerButtonGap),
          ],
          ButtonIcon(
            key: const ValueKey('home-sidebar-settings-button'),
            tooltip: '设置',
            icon: const Icon(Icons.settings_outlined),
            selected: settingsActive,
            onPressed: onOpenSettings,
            size: _footerButtonSize,
          ),
        ],
      ),
    );
  }
}

class _NotificationFooterButton extends StatelessWidget {
  const _NotificationFooterButton({
    required this.selected,
    required this.hasPending,
    required this.count,
    required this.onPressed,
  });

  final bool selected;
  final bool hasPending;
  final int count;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: _footerButtonSize,
      height: _footerButtonOuterHeight,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          ButtonIcon(
            key: const ValueKey('home-sidebar-notifications-button'),
            tooltip: '通知',
            icon: const Icon(Icons.notifications_none),
            selected: selected,
            onPressed: onPressed,
            size: _footerButtonSize,
          ),
          if (hasPending)
            Positioned(
              right: -4,
              top: -4,
              child: _UnreadBadge(count: count <= 0 ? 1 : count),
            ),
        ],
      ),
    );
  }
}

class _UserSummaryBar extends StatelessWidget {
  const _UserSummaryBar({
    required this.user,
    required this.inLive,
    required this.reconnecting,
    required this.requestRoundTrip,
    required this.logoutActive,
    required this.onLogout,
  });

  final CurrentUser user;
  final bool inLive;
  final bool reconnecting;
  final Duration? requestRoundTrip;
  final bool logoutActive;
  final VoidCallback onLogout;

  @override
  Widget build(BuildContext context) {
    final statusLabel = room_display.currentUserPresenceLabel(
      user,
      inLive: inLive,
      reconnecting: reconnecting,
    );
    final latencyQuality = network_latency.networkLatencyQuality(
      requestRoundTrip,
    );
    final latencyColor = _latencySignalColor(latencyQuality);
    final latencyLabel = network_latency.networkLatencyTooltip(
      requestRoundTrip,
    );
    return DecoratedBox(
      key: const ValueKey('home-sidebar-user-summary'),
      decoration: BoxDecoration(
        color: UiColors.surface,
        borderRadius: BorderRadius.circular(UiRadii.md),
        border: Border.all(color: UiColors.border),
      ),
      child: SizedBox(
        height: 58,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10),
          child: Row(
            children: [
              SizedBox.square(
                dimension: 38,
                child: Stack(
                  clipBehavior: Clip.none,
                  children: [
                    Avatar(
                      label: room_display.userAvatarLabel(user.toSummary()),
                      imageUrl: AppConfigScope.of(
                        context,
                      ).resolveAssetUrl(user.avatarUrl),
                      defaultAvatarKey: user.defaultAvatarKey,
                      size: 38,
                      showBorder: false,
                    ),
                    Positioned(
                      right: -3,
                      bottom: -2,
                      child: LatencySignalBadge(
                        activeBars: network_latency.networkLatencySignalBars(
                          requestRoundTrip,
                        ),
                        activeColor: latencyColor,
                        tooltip: latencyLabel,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 11),
              Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      user.displayName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: UiColors.text,
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        letterSpacing: 0,
                      ),
                    ),
                    const SizedBox(height: 4),
                    _SidebarPresenceLabel(label: statusLabel),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              _FlatIconButton(
                tooltip: '退出登录',
                icon: Icons.logout,
                color: logoutActive ? UiColors.accent : UiColors.textMuted,
                size: 30,
                onPressed: onLogout,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

Color _latencySignalColor(network_latency.NetworkLatencyQuality quality) {
  return switch (quality) {
    network_latency.NetworkLatencyQuality.good => _latencySignalGoodColor,
    network_latency.NetworkLatencyQuality.fair => _latencySignalFairColor,
    network_latency.NetworkLatencyQuality.poor => _latencySignalPoorColor,
    network_latency.NetworkLatencyQuality.unavailable => UiColors.textMuted,
  };
}

/// Compact status label with a colored presence dot.
class _SidebarPresenceLabel extends StatelessWidget {
  const _SidebarPresenceLabel({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final color = switch (label) {
      '语音' => UiColors.presenceVoice,
      '重连中' => UiColors.presenceReconnecting,
      '离线' => UiColors.presenceOffline,
      _ => UiColors.presenceOnline,
    };
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        DecoratedBox(
          key: const ValueKey('home-sidebar-presence-dot'),
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
          child: const SizedBox.square(dimension: 6),
        ),
        const SizedBox(width: 6),
        Flexible(
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: UiTypography.label.copyWith(
              color: color,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
      ],
    );
  }
}

class _FlatIconButton extends StatelessWidget {
  const _FlatIconButton({
    required this.tooltip,
    required this.icon,
    required this.color,
    required this.size,
    required this.onPressed,
  });

  final String tooltip;
  final IconData icon;
  final Color color;
  final double size;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return UiTooltip(
      message: tooltip,
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: onPressed,
          child: SizedBox(
            width: size,
            height: size,
            child: Icon(icon, color: color, size: size * 0.54),
          ),
        ),
      ),
    );
  }
}

class _ServerCard extends StatelessWidget {
  const _ServerCard({
    required this.server,
    required this.timestampNow,
    required this.draft,
    required this.selected,
    required this.voiceJoined,
    required this.searchQuery,
    required this.onPressed,
  });

  final RoomCard server;
  final DateTime timestampNow;
  final String? draft;
  final bool selected;
  final bool voiceJoined;
  final String searchQuery;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final platform = Theme.of(context).platform;
    final hoverLift = platform == TargetPlatform.android
        ? 0.0
        : _serverCardHoverLift;
    final lastMessageTime = room_display.roomSidebarLastMessageTime(
      server,
      now: timestampNow,
    );
    final draftPreview = room_display.roomDraftPreview(draft);
    final subtitle = room_display.roomSidebarSubtitle(server);
    final hasUnreadMention = server.unreadMentionCount > 0;
    return PressableSurface(
      key: ValueKey('home-sidebar-room-${server.id}'),
      width: double.infinity,
      height: _serverCardHeight,
      hoverLift: hoverLift,
      baseDepth: _serverCardBaseDepth,
      selected: selected,
      backgroundColor: UiColors.surfaceLow,
      selectedBackgroundColor: UiColors.selected,
      pressedBackgroundColor: UiColors.surfacePressed,
      borderColor: UiColors.border,
      selectedBorderColor: UiColors.selectedBorder,
      borderRadius: UiRadii.md,
      padding: const EdgeInsets.symmetric(
        horizontal: _serverCardHorizontalPadding,
      ),
      cancelTouchPressOnDrag: platform == TargetPlatform.android,
      onPressed: onPressed,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Row(
            children: [
              _ServerAvatar(
                server: server,
                selected: selected,
                voiceJoined: voiceJoined,
              ),
              const SizedBox(width: 9),
              Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: HighlightedText(
                            text: server.displayName,
                            query: searchQuery,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: UiColors.text,
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              letterSpacing: 0,
                            ),
                          ),
                        ),
                        if (lastMessageTime.isNotEmpty) ...[
                          const SizedBox(width: 8),
                          Text(
                            lastMessageTime,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: UiTypography.label.copyWith(
                              color: selected
                                  ? UiColors.textSecondary
                                  : UiColors.textMuted,
                              fontSize: 10.5,
                            ),
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 4),
                    if (draftPreview == null)
                      hasUnreadMention
                          ? Text.rich(
                              TextSpan(
                                children: [
                                  TextSpan(
                                    text: '[@我] ',
                                    style: UiTypography.label.copyWith(
                                      color: UiColors.accent,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                  TextSpan(
                                    text: subtitle,
                                    style: UiTypography.label.copyWith(
                                      color: selected
                                          ? UiColors.textSecondary
                                          : UiColors.textMuted,
                                    ),
                                  ),
                                ],
                              ),
                              key: ValueKey(
                                'home-sidebar-room-mention-${server.id}',
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            )
                          : HighlightedText(
                              text: subtitle,
                              query: searchQuery,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: UiTypography.label.copyWith(
                                color: selected
                                    ? UiColors.textSecondary
                                    : UiColors.textMuted,
                              ),
                            )
                    else
                      Text.rich(
                        TextSpan(
                          children: [
                            TextSpan(
                              text: '[草稿] ',
                              style: UiTypography.label.copyWith(
                                color: UiColors.accent,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            TextSpan(
                              text: draftPreview,
                              style: UiTypography.label.copyWith(
                                color: selected
                                    ? UiColors.textSecondary
                                    : UiColors.textMuted,
                              ),
                            ),
                          ],
                        ),
                        key: ValueKey('home-sidebar-room-draft-${server.id}'),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                  ],
                ),
              ),
            ],
          ),
          if (server.isPinned)
            Positioned(
              top: 2,
              left: 2 - _serverCardHorizontalPadding,
              child: Icon(
                Icons.push_pin,
                key: ValueKey('home-sidebar-room-pinned-${server.id}'),
                color: UiColors.text,
                size: 14,
                shadows: const [
                  Shadow(
                    color: Color(0xAA0F1115),
                    blurRadius: 3,
                    offset: Offset(0, 1),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _ServerAvatar extends StatelessWidget {
  const _ServerAvatar({
    required this.server,
    required this.selected,
    required this.voiceJoined,
  });

  final RoomCard server;
  final bool selected;
  final bool voiceJoined;

  @override
  Widget build(BuildContext context) {
    final hasLiveParticipants = server.liveParticipantCount > 0;
    final notificationPolicy = room_display.normalizeRoomNotificationPolicy(
      server.notificationPolicy,
    );
    final mutedBadge = notificationPolicy == 'silent';
    final showPendingJoinRequestBadge =
        server.unreadCount <= 0 &&
        server.hasPendingJoinRequests &&
        notificationPolicy != 'blocked';
    return SizedBox.square(
      dimension: 44,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Center(
            child: Avatar(
              label: room_display.roomCardAvatarLabel(server),
              imageUrl: AppConfigScope.of(
                context,
              ).resolveAssetUrl(server.avatarUrl),
              defaultAvatarKey: server.defaultAvatarKey,
              size: 40,
              active: selected,
              activeBorderWidth: 1.2,
            ),
          ),
          if (server.unreadCount > 0)
            Positioned(
              top: 0,
              right: 0,
              child: _UnreadBadge(count: server.unreadCount, muted: mutedBadge),
            ),
          if (showPendingJoinRequestBadge)
            Positioned(
              top: 3,
              right: 3,
              child: _PendingJoinRequestBadge(
                key: ValueKey(
                  'home-sidebar-room-pending-join-requests-${server.id}',
                ),
                muted: mutedBadge,
              ),
            ),
          if (hasLiveParticipants)
            Positioned(
              right: 0,
              bottom: 1,
              child: Icon(
                Icons.volume_up,
                key: ValueKey('home-sidebar-room-live-${server.id}'),
                color: voiceJoined ? UiColors.accent : Colors.white,
                size: 15,
                shadows: const [
                  Shadow(
                    color: Color(0xAA0F1115),
                    blurRadius: 3,
                    offset: Offset(0, 1),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _PendingJoinRequestBadge extends StatelessWidget {
  const _PendingJoinRequestBadge({super.key, required this.muted});

  final bool muted;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: muted ? const Color(0xFF737985) : const Color(0xFFE14747),
        shape: BoxShape.circle,
        border: Border.all(
          color: muted ? const Color(0xFF282D35) : const Color(0xFF35191D),
          width: 1.1,
        ),
      ),
      child: const SizedBox.square(dimension: 10),
    );
  }
}

class _UnreadBadge extends StatelessWidget {
  const _UnreadBadge({required this.count, this.muted = false});

  final int count;
  final bool muted;

  @override
  Widget build(BuildContext context) {
    final label = count > 99 ? '99+' : '$count';
    return DecoratedBox(
      decoration: BoxDecoration(
        color: muted ? const Color(0xFF737985) : const Color(0xFFE14747),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(
          color: muted ? const Color(0xFF282D35) : const Color(0xFF35191D),
          width: 1.2,
        ),
      ),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minWidth: 18, minHeight: 18),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 5),
          child: Center(
            child: Text(
              label,
              maxLines: 1,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 10,
                fontWeight: FontWeight.w600,
                height: 1,
                letterSpacing: 0,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
