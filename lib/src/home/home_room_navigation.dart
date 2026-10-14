import 'package:flutter/gestures.dart'
    show PointerScrollEvent, PointerSignalEvent;
import 'package:flutter/material.dart';

import '../app/room_display.dart' as room_display;
import '../protocol/models.dart';
import '../ui/ui.dart';

const _tabHeight = 34.0;
const _tabNameMaxWidth = 150.0;
const _lobbyCardMinWidth = 240.0;
const _lobbyCardHeight = 176.0;
const _lobbyCompactCardHeight = 72.0;
const _lobbyGridGap = 14.0;
const unreadBadgeColor = Color(0xFFFF4D4F);
const mutedBadgeColor = Color(0xFF4A4A50);

/// Browser-style strip of room tabs that lives in the window header. The
/// first tab is the lobby (room overview); the trailing "+" creates a room.
class HomeRoomTabStrip extends StatefulWidget {
  const HomeRoomTabStrip({
    super.key,
    required this.rooms,
    required this.selectedRoomId,
    required this.lobbyActive,
    required this.joinedLiveRoomId,
    required this.roomDrafts,
    required this.createRoomActive,
    required this.onLobbySelected,
    required this.onRoomSelected,
    required this.onCreateRoom,
  });

  final List<RoomCard> rooms;
  final String? selectedRoomId;
  final bool lobbyActive;
  final String? joinedLiveRoomId;
  final Map<String, String> roomDrafts;
  final bool createRoomActive;
  final VoidCallback onLobbySelected;
  final ValueChanged<RoomCard> onRoomSelected;
  final VoidCallback onCreateRoom;

  @override
  State<HomeRoomTabStrip> createState() => _HomeRoomTabStripState();
}

class _HomeRoomTabStripState extends State<HomeRoomTabStrip> {
  final ScrollController _controller = ScrollController();
  bool _fadeStart = false;
  bool _fadeEnd = false;

  @override
  void initState() {
    super.initState();
    _controller.addListener(_syncFades);
    WidgetsBinding.instance.addPostFrameCallback((_) => _syncFades());
  }

  @override
  void didUpdateWidget(HomeRoomTabStrip oldWidget) {
    super.didUpdateWidget(oldWidget);
    WidgetsBinding.instance.addPostFrameCallback((_) => _syncFades());
  }

  @override
  void dispose() {
    _controller
      ..removeListener(_syncFades)
      ..dispose();
    super.dispose();
  }

  void _syncFades() {
    if (!mounted || !_controller.hasClients) return;
    final position = _controller.position;
    final fadeStart = position.pixels > 1;
    final fadeEnd = position.pixels < position.maxScrollExtent - 1;
    if (fadeStart == _fadeStart && fadeEnd == _fadeEnd) return;
    setState(() {
      _fadeStart = fadeStart;
      _fadeEnd = fadeEnd;
    });
  }

  void _handlePointerSignal(PointerSignalEvent event) {
    if (event is! PointerScrollEvent || !_controller.hasClients) return;
    final delta = event.scrollDelta.dx.abs() > 0
        ? event.scrollDelta.dx
        : event.scrollDelta.dy;
    if (delta == 0) return;
    final position = _controller.position;
    _controller.jumpTo(
      (_controller.offset + delta)
          .clamp(position.minScrollExtent, position.maxScrollExtent)
          .toDouble(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final rooms = widget.rooms;
    final list = ListView.separated(
      key: const ValueKey('home-room-tab-list'),
      controller: _controller,
      shrinkWrap: true,
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(horizontal: 2),
      itemCount: rooms.length + 1,
      separatorBuilder: (context, index) => const SizedBox(width: 4),
      itemBuilder: (context, index) {
        if (index == 0) {
          return Center(
            child: _LobbyTab(
              active: widget.lobbyActive,
              onPressed: widget.onLobbySelected,
            ),
          );
        }
        final room = rooms[index - 1];
        return Center(
          child: _RoomTab(
            room: room,
            selected: !widget.lobbyActive && room.id == widget.selectedRoomId,
            voiceJoined: room.id == widget.joinedLiveRoomId,
            hasDraft:
                room_display.roomDraftPreview(widget.roomDrafts[room.id]) !=
                null,
            onPressed: () => widget.onRoomSelected(room),
          ),
        );
      },
    );
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Flexible(
          child: Listener(
            onPointerSignal: _handlePointerSignal,
            child: ShaderMask(
              blendMode: BlendMode.dstIn,
              shaderCallback: (bounds) => LinearGradient(
                colors: [
                  _fadeStart ? Colors.transparent : Colors.white,
                  Colors.white,
                  Colors.white,
                  _fadeEnd ? Colors.transparent : Colors.white,
                ],
                stops: const [0, 0.04, 0.96, 1],
              ).createShader(bounds),
              child: ScrollConfiguration(
                behavior: ScrollConfiguration.of(
                  context,
                ).copyWith(scrollbars: false),
                child: SizedBox(height: _tabHeight + 8, child: list),
              ),
            ),
          ),
        ),
        const SizedBox(width: 4),
        ButtonIcon(
          key: const ValueKey('home-sidebar-create-room-button'),
          tooltip: '创建房间',
          icon: const Icon(Icons.add_rounded),
          selected: widget.createRoomActive,
          ghost: true,
          size: 32,
          onPressed: widget.onCreateRoom,
        ),
      ],
    );
  }
}

class _LobbyTab extends StatelessWidget {
  const _LobbyTab({required this.active, required this.onPressed});

  final bool active;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return PressableSurface(
      key: const ValueKey('home-lobby-tab'),
      height: _tabHeight,
      tooltip: '全部房间',
      selected: active,
      onPressed: onPressed,
      backgroundColor: Colors.transparent,
      hoverBackgroundColor: UiColors.surface,
      selectedBackgroundColor: UiColors.inverse,
      borderColor: UiColors.border,
      selectedBorderColor: Colors.transparent,
      borderRadius: UiRadii.pill,
      padding: const EdgeInsets.symmetric(horizontal: 12),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            Icons.grid_view_rounded,
            size: 15,
            color: active ? UiColors.onInverse : UiColors.textSecondary,
          ),
          const SizedBox(width: 7),
          Text(
            '大厅',
            style: UiTypography.label.copyWith(
              color: active ? UiColors.onInverse : UiColors.textSecondary,
              fontSize: 13,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}

class _RoomTab extends StatelessWidget {
  const _RoomTab({
    required this.room,
    required this.selected,
    required this.voiceJoined,
    required this.hasDraft,
    required this.onPressed,
  });

  final RoomCard room;
  final bool selected;
  final bool voiceJoined;
  final bool hasDraft;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final muted =
        room_display.normalizeRoomNotificationPolicy(room.notificationPolicy) ==
        'silent';
    final unread = room.unreadCount > 0;
    final live = room.liveParticipantCount > 0;
    final mention = room.unreadMentionCount > 0;
    return PressableSurface(
      key: ValueKey('home-room-tab-${room.id}'),
      height: _tabHeight,
      tooltip:
          '${room.displayName} · ${room_display.roomSidebarSubtitle(room)}',
      selected: selected,
      onPressed: onPressed,
      backgroundColor: Colors.transparent,
      hoverBackgroundColor: UiColors.surface,
      selectedBackgroundColor: UiColors.inverse,
      borderColor: Colors.transparent,
      selectedBorderColor: Colors.transparent,
      borderRadius: UiRadii.pill,
      padding: const EdgeInsets.only(left: 5, right: 12),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Stack(
            clipBehavior: Clip.none,
            children: [
              Avatar(
                label: room_display.roomCardAvatarLabel(room),
                imageUrl: AppConfigScope.of(
                  context,
                ).resolveAssetUrl(room.avatarUrl),
                defaultAvatarKey: room.defaultAvatarKey,
                size: 24,
                showBorder: false,
              ),
              if (live)
                Positioned(
                  right: -3,
                  bottom: -3,
                  child: Container(
                    key: ValueKey('home-room-tab-live-${room.id}'),
                    width: 10,
                    height: 10,
                    decoration: BoxDecoration(
                      color: UiColors.accent,
                      shape: BoxShape.circle,
                      border: Border.all(
                        color: selected
                            ? UiColors.inverse
                            : UiColors.background,
                        width: 2,
                      ),
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(width: 8),
          if (room.isPinned) ...[
            Icon(
              Icons.push_pin_rounded,
              size: 11,
              color: selected ? UiColors.onInverse : UiColors.textMuted,
            ),
            const SizedBox(width: 3),
          ],
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: _tabNameMaxWidth),
            child: Text(
              room.displayName,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: UiTypography.label.copyWith(
                fontFamily: kDisplayFontFamily,
                fontFamilyFallback: const [
                  kClientFontFamily,
                  ...kClientFontFamilyFallback,
                ],
                color: selected
                    ? UiColors.onInverse
                    : unread
                    ? UiColors.text
                    : UiColors.textSecondary,
                fontSize: 13,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          if (hasDraft) ...[
            const SizedBox(width: 6),
            Icon(
              Icons.edit_rounded,
              key: ValueKey('home-room-tab-draft-${room.id}'),
              size: 12,
              color: UiColors.danger,
            ),
          ],
          if (mention) ...[
            const SizedBox(width: 6),
            _MentionBadge(key: ValueKey('home-room-tab-mention-${room.id}')),
          ],
          if (unread) ...[
            const SizedBox(width: 6),
            RoomUnreadBadge(count: room.unreadCount, muted: muted),
          ] else if (room.hasPendingJoinRequests &&
              room_display.normalizeRoomNotificationPolicy(
                    room.notificationPolicy,
                  ) !=
                  'blocked') ...[
            const SizedBox(width: 6),
            RoomPendingDot(
              key: ValueKey('home-room-tab-pending-join-requests-${room.id}'),
              muted: muted,
            ),
          ],
        ],
      ),
    );
  }
}

class _MentionBadge extends StatelessWidget {
  const _MentionBadge({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 16,
      height: 16,
      decoration: BoxDecoration(
        color: UiColors.amber.withValues(alpha: 0.18),
        shape: BoxShape.circle,
      ),
      child: const Center(
        child: Text(
          '@',
          style: TextStyle(
            color: UiColors.amber,
            fontSize: 10.5,
            fontWeight: FontWeight.w800,
            height: 1,
          ),
        ),
      ),
    );
  }
}

class RoomPendingDot extends StatelessWidget {
  const RoomPendingDot({super.key, required this.muted});

  final bool muted;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: muted ? mutedBadgeColor : unreadBadgeColor,
        shape: BoxShape.circle,
      ),
      child: const SizedBox.square(dimension: 8),
    );
  }
}

class RoomUnreadBadge extends StatelessWidget {
  const RoomUnreadBadge({super.key, required this.count, this.muted = false});

  final int count;
  final bool muted;

  @override
  Widget build(BuildContext context) {
    final label = count > 99 ? '99+' : '$count';
    return Container(
      height: 18,
      constraints: const BoxConstraints(minWidth: 18),
      padding: const EdgeInsets.symmetric(horizontal: 5),
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: muted ? mutedBadgeColor : unreadBadgeColor,
        borderRadius: BorderRadius.circular(UiRadii.pill),
      ),
      child: Text(
        label,
        maxLines: 1,
        style: const TextStyle(
          color: Colors.white,
          fontFamily: kMonoFontFamily,
          fontSize: 10.5,
          fontWeight: FontWeight.w700,
          height: 1,
          letterSpacing: 0,
        ),
      ),
    );
  }
}

/// The room overview, laid out as a bento grid: a hero tile with dot-matrix
/// counters, a wide tile for every room with live voice, regular tiles for
/// the rest and a tile to create a room. Shown when no room is open and
/// whenever the lobby tab is selected.
class HomeLobby extends StatelessWidget {
  const HomeLobby({
    super.key,
    required this.currentUser,
    required this.rooms,
    required this.timestampNow,
    required this.roomDrafts,
    required this.selectedRoomId,
    required this.joinedLiveRoomId,
    required this.loading,
    required this.error,
    required this.onRoomSelected,
    required this.onCreateRoom,
    this.compact = false,
    this.footer,
  });

  final CurrentUser currentUser;
  final List<RoomCard> rooms;
  final DateTime timestampNow;
  final Map<String, String> roomDrafts;
  final String? selectedRoomId;
  final String? joinedLiveRoomId;
  final bool loading;
  final String? error;
  final ValueChanged<RoomCard> onRoomSelected;
  final VoidCallback onCreateRoom;
  final bool compact;

  /// Pinned below the scrolling content (the compact layout puts the voice
  /// connection here).
  final Widget? footer;

  @override
  Widget build(BuildContext context) {
    final footer = this.footer;
    return ColoredBox(
      key: const ValueKey('home-lobby'),
      color: Colors.transparent,
      child: Column(
        children: [
          Expanded(child: _buildBody(context)),
          if (footer != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
              child: footer,
            ),
        ],
      ),
    );
  }

  Widget _buildBody(BuildContext context) {
    if (rooms.isEmpty && loading) {
      return const Center(
        child: SizedBox.square(
          dimension: 22,
          child: CircularProgressIndicator(strokeWidth: 2.2),
        ),
      );
    }
    if (rooms.isEmpty && error != null) {
      return FloatingNoticeEmitter(
        notices: [
          FloatingNotice(
            message: error!,
            tone: FloatingNoticeTone.error,
            duration: null,
          ),
        ],
        child: const Center(
          child: Icon(Icons.error_outline, color: UiColors.danger, size: 22),
        ),
      );
    }
    final liveRooms = rooms
        .where((room) => room.liveParticipantCount > 0)
        .toList();
    final unread = rooms.fold<int>(0, (sum, room) => sum + room.unreadCount);
    final horizontalPadding = compact ? 12.0 : 28.0;
    return LayoutBuilder(
      builder: (context, constraints) {
        final available = constraints.maxWidth - horizontalPadding * 2;
        final columns = compact
            ? 1
            : ((available + _lobbyGridGap) /
                      (_lobbyCardMinWidth + _lobbyGridGap))
                  .floor()
                  .clamp(1, 6);
        final cell = (available - _lobbyGridGap * (columns - 1)) / columns;
        double span(int count) {
          final cells = count.clamp(1, columns);
          return cell * cells + _lobbyGridGap * (cells - 1);
        }

        final tiles = <Widget>[
          SizedBox(
            width: compact ? available : span(columns >= 3 ? 2 : columns),
            child: _LobbyHero(
              name: currentUser.displayName,
              roomCount: rooms.length,
              liveRoomCount: liveRooms.length,
              unreadCount: unread,
              compact: compact,
            ),
          ),
          if (!compact && columns >= 3)
            SizedBox(
              width: span(1),
              child: _CreateRoomCard(onPressed: onCreateRoom),
            ),
          for (final room in rooms)
            SizedBox(
              width: compact
                  ? available
                  : span(room.liveParticipantCount > 0 && columns >= 2 ? 2 : 1),
              child: compact
                  ? _LobbyRoomRow(
                      room: room,
                      timestampNow: timestampNow,
                      draft: roomDrafts[room.id],
                      selected: room.id == selectedRoomId,
                      voiceJoined: room.id == joinedLiveRoomId,
                      onPressed: () => onRoomSelected(room),
                    )
                  : _LobbyRoomCard(
                      room: room,
                      timestampNow: timestampNow,
                      draft: roomDrafts[room.id],
                      selected: room.id == selectedRoomId,
                      voiceJoined: room.id == joinedLiveRoomId,
                      wide: room.liveParticipantCount > 0 && columns >= 2,
                      onPressed: () => onRoomSelected(room),
                    ),
            ),
          if (compact || columns < 3)
            SizedBox(
              width: compact ? available : span(1),
              child: _CreateRoomCard(onPressed: onCreateRoom, compact: compact),
            ),
        ];
        return ListView(
          key: const ValueKey('home-lobby-scroll'),
          padding: EdgeInsets.fromLTRB(
            horizontalPadding,
            compact ? 12 : 26,
            horizontalPadding,
            24,
          ),
          children: [
            if (rooms.isEmpty) ...[
              tiles.first,
              const SizedBox(height: _lobbyGridGap),
              _LobbyEmptyState(onCreateRoom: onCreateRoom),
            ] else
              Wrap(
                spacing: _lobbyGridGap,
                runSpacing: compact ? 8 : _lobbyGridGap,
                children: tiles,
              ),
          ],
        );
      },
    );
  }
}

class _LobbyHero extends StatelessWidget {
  const _LobbyHero({
    required this.name,
    required this.roomCount,
    required this.liveRoomCount,
    required this.unreadCount,
    required this.compact,
  });

  final String name;
  final int roomCount;
  final int liveRoomCount;
  final int unreadCount;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final greeting = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        const SignalTag(label: 'GANG CHAT // LOBBY', dot: true),
        SizedBox(height: compact ? 10 : 14),
        Text(
          '欢迎回来，${name.trim().isEmpty ? '朋友' : name.trim()}',
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: compact
              ? UiTypography.headline
              : UiTypography.display.copyWith(fontSize: 28),
        ),
      ],
    );
    final counters = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        DotCounter(value: roomCount, label: 'ROOMS', size: compact ? 30 : 46),
        SizedBox(width: compact ? 18 : 28),
        DotCounter(
          value: liveRoomCount,
          label: 'LIVE',
          color: liveRoomCount > 0 ? UiColors.accent : UiColors.textMuted,
          size: compact ? 30 : 46,
        ),
        SizedBox(width: compact ? 18 : 28),
        DotCounter(
          value: unreadCount,
          label: 'UNREAD',
          color: unreadCount > 0 ? unreadBadgeColor : UiColors.textMuted,
          size: compact ? 30 : 46,
        ),
      ],
    );
    return Container(
      key: const ValueKey('home-lobby-hero'),
      height: compact ? null : _lobbyCardHeight,
      padding: EdgeInsets.all(compact ? 16 : 22),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(UiRadii.xl),
        border: Border.all(color: UiColors.border),
        gradient: RadialGradient(
          center: const Alignment(-1.05, -1.3),
          radius: compact ? 1.6 : 1.25,
          colors: [
            Color.alphaBlend(
              UiColors.accent.withValues(alpha: 0.2),
              UiColors.card,
            ),
            UiColors.card,
          ],
        ),
      ),
      child: compact
          ? Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [greeting, const SizedBox(height: 16), counters],
            )
          : Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Expanded(child: greeting),
                const SizedBox(width: 16),
                Align(alignment: Alignment.bottomRight, child: counters),
              ],
            ),
    );
  }
}

class _LobbyEmptyState extends StatelessWidget {
  const _LobbyEmptyState({required this.onCreateRoom});

  final VoidCallback onCreateRoom;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 40, horizontal: 20),
      decoration: BoxDecoration(
        color: UiColors.card,
        borderRadius: BorderRadius.circular(UiRadii.xl),
        border: Border.all(color: UiColors.border),
      ),
      child: Column(
        children: [
          const BrandMark(size: 44),
          const SizedBox(height: 14),
          Text('还没有房间', style: UiTypography.title),
          const SizedBox(height: 6),
          Text(
            '创建一个房间，或通过搜索加入公开房间',
            textAlign: TextAlign.center,
            style: UiTypography.label.copyWith(color: UiColors.textMuted),
          ),
          const SizedBox(height: 18),
          Button(
            tone: ButtonTone.primary,
            icon: const Icon(Icons.add_rounded),
            onPressed: onCreateRoom,
            child: const Text('创建房间'),
          ),
        ],
      ),
    );
  }
}

class _LobbyRoomCard extends StatelessWidget {
  const _LobbyRoomCard({
    required this.room,
    required this.timestampNow,
    required this.draft,
    required this.selected,
    required this.voiceJoined,
    required this.wide,
    required this.onPressed,
  });

  final RoomCard room;
  final DateTime timestampNow;
  final String? draft;
  final bool selected;
  final bool voiceJoined;
  final bool wide;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final time = room_display.roomSidebarLastMessageTime(
      room,
      now: timestampNow,
    );
    final muted =
        room_display.normalizeRoomNotificationPolicy(room.notificationPolicy) ==
        'silent';
    final unread = room.unreadCount > 0;
    final live = room.liveParticipantCount > 0;
    final card = PressableSurface(
      key: ValueKey('home-lobby-room-${room.id}'),
      height: _lobbyCardHeight,
      selected: selected,
      onPressed: onPressed,
      backgroundColor: live ? const Color(0xFF12150A) : UiColors.card,
      selectedBackgroundColor: live ? const Color(0xFF12150A) : UiColors.card,
      borderColor: live ? UiColors.accentBorder : UiColors.border,
      selectedBorderColor: UiColors.selectedBorder,
      borderRadius: UiRadii.xl,
      padding: const EdgeInsets.fromLTRB(18, 18, 18, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Avatar(
                label: room_display.roomCardAvatarLabel(room),
                imageUrl: AppConfigScope.of(
                  context,
                ).resolveAssetUrl(room.avatarUrl),
                defaultAvatarKey: room.defaultAvatarKey,
                size: 44,
                showBorder: false,
                rounded: true,
              ),
              const Spacer(),
              if (room.isPinned) ...[
                Icon(
                  Icons.push_pin_rounded,
                  key: ValueKey('home-lobby-room-pinned-${room.id}'),
                  size: 14,
                  color: UiColors.textMuted,
                ),
                const SizedBox(width: 8),
              ],
              if (time.isNotEmpty)
                Text(
                  time,
                  style: UiTypography.mono.copyWith(
                    color: unread && !muted
                        ? UiColors.text
                        : UiColors.textMuted,
                  ),
                ),
              if (unread) ...[
                const SizedBox(width: 8),
                RoomUnreadBadge(count: room.unreadCount, muted: muted),
              ] else if (room.hasPendingJoinRequests) ...[
                const SizedBox(width: 8),
                RoomPendingDot(
                  key: ValueKey(
                    'home-lobby-room-pending-join-requests-${room.id}',
                  ),
                  muted: muted,
                ),
              ],
            ],
          ),
          const Spacer(),
          Text(
            room.displayName,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: UiTypography.title.copyWith(fontSize: 18),
          ),
          const SizedBox(height: 4),
          _RoomPreviewLine(room: room, draft: draft),
          const SizedBox(height: 12),
          _RoomVoiceStrip(room: room, voiceJoined: voiceJoined, wide: wide),
        ],
      ),
    );
    if (!live) return card;
    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(UiRadii.xl),
        boxShadow: UiShadows.glow(UiColors.accent, strength: 0.12),
      ),
      child: card,
    );
  }
}

class _LobbyRoomRow extends StatelessWidget {
  const _LobbyRoomRow({
    required this.room,
    required this.timestampNow,
    required this.draft,
    required this.selected,
    required this.voiceJoined,
    required this.onPressed,
  });

  final RoomCard room;
  final DateTime timestampNow;
  final String? draft;
  final bool selected;
  final bool voiceJoined;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final platform = Theme.of(context).platform;
    final time = room_display.roomSidebarLastMessageTime(
      room,
      now: timestampNow,
    );
    final muted =
        room_display.normalizeRoomNotificationPolicy(room.notificationPolicy) ==
        'silent';
    final unread = room.unreadCount > 0;
    final live = room.liveParticipantCount > 0;
    return PressableSurface(
      key: ValueKey('home-lobby-room-${room.id}'),
      height: _lobbyCompactCardHeight,
      selected: selected,
      onPressed: onPressed,
      cancelTouchPressOnDrag: platform == TargetPlatform.android,
      backgroundColor: UiColors.card,
      selectedBackgroundColor: UiColors.card,
      borderColor: live ? UiColors.accentBorder : UiColors.border,
      selectedBorderColor: UiColors.selectedBorder,
      borderRadius: UiRadii.lg,
      padding: const EdgeInsets.symmetric(horizontal: 12),
      child: Row(
        children: [
          Avatar(
            label: room_display.roomCardAvatarLabel(room),
            imageUrl: AppConfigScope.of(
              context,
            ).resolveAssetUrl(room.avatarUrl),
            defaultAvatarKey: room.defaultAvatarKey,
            size: 44,
            showBorder: false,
            rounded: true,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    if (room.isPinned) ...[
                      const Icon(
                        Icons.push_pin_rounded,
                        size: 12,
                        color: UiColors.textMuted,
                      ),
                      const SizedBox(width: 4),
                    ],
                    Expanded(
                      child: Text(
                        room.displayName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: UiTypography.subtitle,
                      ),
                    ),
                    if (live) ...[
                      SignalTag(
                        label: 'LIVE ${room.liveParticipantCount}',
                        color: UiColors.accent,
                        filled: voiceJoined,
                        dot: true,
                      ),
                      const SizedBox(width: 8),
                    ],
                    if (time.isNotEmpty) Text(time, style: UiTypography.mono),
                  ],
                ),
                const SizedBox(height: 5),
                Row(
                  children: [
                    Expanded(
                      child: _RoomPreviewLine(room: room, draft: draft),
                    ),
                    if (unread) ...[
                      const SizedBox(width: 8),
                      RoomUnreadBadge(count: room.unreadCount, muted: muted),
                    ],
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _RoomPreviewLine extends StatelessWidget {
  const _RoomPreviewLine({required this.room, required this.draft});

  final RoomCard room;
  final String? draft;

  @override
  Widget build(BuildContext context) {
    final style = UiTypography.label.copyWith(color: UiColors.textMuted);
    final draftPreview = room_display.roomDraftPreview(draft);
    final subtitle = room_display.roomSidebarSubtitle(room);
    if (draftPreview != null) {
      return Text.rich(
        TextSpan(
          children: [
            TextSpan(
              text: '[草稿] ',
              style: style.copyWith(
                color: UiColors.danger,
                fontWeight: FontWeight.w700,
              ),
            ),
            TextSpan(text: draftPreview, style: style),
          ],
        ),
        key: ValueKey('home-sidebar-room-draft-${room.id}'),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      );
    }
    if (room.unreadMentionCount > 0) {
      return Text.rich(
        TextSpan(
          children: [
            TextSpan(
              text: '[@我] ',
              style: style.copyWith(
                color: UiColors.amber,
                fontWeight: FontWeight.w700,
              ),
            ),
            TextSpan(text: subtitle, style: style),
          ],
        ),
        key: ValueKey('home-sidebar-room-mention-${room.id}'),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      );
    }
    return Text(
      subtitle,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: style,
    );
  }
}

class _RoomVoiceStrip extends StatelessWidget {
  const _RoomVoiceStrip({
    required this.room,
    required this.voiceJoined,
    required this.wide,
  });

  final RoomCard room;
  final bool voiceJoined;
  final bool wide;

  @override
  Widget build(BuildContext context) {
    final count = room.liveParticipantCount;
    if (count <= 0) {
      return const Row(
        children: [
          EqualizerBars(
            color: UiColors.borderStrong,
            height: 10,
            barWidth: 2,
            levels: [0.3, 0.3, 0.3, 0.3, 0.3],
          ),
          SizedBox(width: 8),
          Text('IDLE', style: UiTypography.overline),
        ],
      );
    }
    final config = AppConfigScope.of(context);
    final preview = room.liveAvatarPreview.take(wide ? 6 : 3).toList();
    const size = 22.0;
    const overlap = 7.0;
    return Row(
      key: ValueKey('home-lobby-room-live-${room.id}'),
      children: [
        SignalTag(
          label: voiceJoined ? 'ON AIR · $count' : 'LIVE · $count',
          color: UiColors.accent,
          filled: voiceJoined,
          dot: true,
        ),
        const SizedBox(width: 10),
        if (preview.isNotEmpty) ...[
          SizedBox(
            width: size + (preview.length - 1) * (size - overlap),
            height: size,
            child: Stack(
              children: [
                for (var index = 0; index < preview.length; index++)
                  Positioned(
                    left: index * (size - overlap),
                    child: Container(
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: const Color(0xFF12150A),
                          width: 2,
                        ),
                      ),
                      child: Avatar(
                        label: room_display.userAvatarLabel(preview[index]),
                        imageUrl: config.resolveAssetUrl(
                          preview[index].avatarUrl,
                        ),
                        defaultAvatarKey: preview[index].defaultAvatarKey,
                        size: size - 4,
                        showBorder: false,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ],
        const Spacer(),
        const EqualizerBars(height: 16, barWidth: 3),
      ],
    );
  }
}

class _CreateRoomCard extends StatefulWidget {
  const _CreateRoomCard({required this.onPressed, this.compact = false});

  final VoidCallback onPressed;
  final bool compact;

  @override
  State<_CreateRoomCard> createState() => _CreateRoomCardState();
}

class _CreateRoomCardState extends State<_CreateRoomCard> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    final compact = widget.compact;
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: GestureDetector(
        key: const ValueKey('home-lobby-create-room'),
        behavior: HitTestBehavior.opaque,
        onTap: widget.onPressed,
        child: CustomPaint(
          painter: _DashedBorderPainter(
            color: _hovered ? UiColors.accent : UiColors.borderStrong,
            radius: compact ? UiRadii.lg : UiRadii.xl,
          ),
          child: SizedBox(
            height: compact ? 56 : _lobbyCardHeight,
            child: Flex(
              direction: compact ? Axis.horizontal : Axis.vertical,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                AnimatedContainer(
                  duration: UiMotion.fast,
                  width: compact ? 30 : 46,
                  height: compact ? 30 : 46,
                  decoration: BoxDecoration(
                    color: _hovered ? UiColors.accent : UiColors.surface,
                    shape: BoxShape.circle,
                  ),
                  child: Icon(
                    Icons.add_rounded,
                    size: compact ? 18 : 24,
                    color: _hovered
                        ? UiColors.onAccent
                        : UiColors.textSecondary,
                  ),
                ),
                SizedBox(width: compact ? 10 : 0, height: compact ? 0 : 12),
                Text(
                  '新建房间',
                  style: UiTypography.label.copyWith(
                    color: _hovered ? UiColors.text : UiColors.textSecondary,
                    fontWeight: FontWeight.w700,
                    fontSize: 13,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _DashedBorderPainter extends CustomPainter {
  const _DashedBorderPainter({required this.color, required this.radius});

  final Color color;
  final double radius;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.2;
    final path = Path()
      ..addRRect(
        RRect.fromRectAndRadius(
          (Offset.zero & size).deflate(0.6),
          Radius.circular(radius),
        ),
      );
    for (final metric in path.computeMetrics()) {
      var distance = 0.0;
      while (distance < metric.length) {
        canvas.drawPath(metric.extractPath(distance, distance + 6), paint);
        distance += 11;
      }
    }
  }

  @override
  bool shouldRepaint(_DashedBorderPainter oldDelegate) =>
      oldDelegate.color != color || oldDelegate.radius != radius;
}
