part of 'home_shell.dart';

const _homeTitleBarHeight = 52.0;
const _homeTitleBarControlsWidth = appWindowControlsWidth;
const _homeSearchFieldHeight = 44.0;
const _homeSearchPaletteMaxWidth = 660.0;
const _headerActionSize = 34.0;
const _liveDockHeight = 52.0;
const _liveDockActionSize = 30.0;
typedef _TitleLiveRoomDockBuilder =
    Widget Function(double maxWidth, {bool fillAvailable});

bool _homeTitleBarShowsCustomWindowControls(BuildContext context) {
  final platform = Theme.of(context).platform;
  return platform != TargetPlatform.macOS && platform != TargetPlatform.android;
}

double _homeTitleBarLeadingInset(BuildContext context) {
  if (Theme.of(context).platform == TargetPlatform.macOS) {
    return WindowChromeInsets.of(context).titleBarLeadingControlsSafeInset;
  }
  return 16;
}

/// The window header. Wide layouts carry the room tabs here; every layout
/// carries the global actions (voice, search, notifications, settings and the
/// account menu) on the right and the window buttons at the far edge.
class _HomeTitleBar extends StatefulWidget {
  const _HomeTitleBar({
    required this.windowController,
    required this.compact,
    required this.interactionLocked,
    required this.tabStrip,
    required this.voiceCapsule,
    required this.searchActive,
    required this.onOpenSearch,
    required this.notificationsActive,
    required this.hasPendingNotifications,
    required this.pendingNotificationCount,
    required this.onOpenNotifications,
    required this.settingsActive,
    required this.onOpenSettings,
    required this.account,
  });

  final DesktopWindowController windowController;
  final bool compact;
  final bool interactionLocked;
  final Widget? tabStrip;
  final Widget? voiceCapsule;
  final bool searchActive;
  final VoidCallback onOpenSearch;
  final bool notificationsActive;
  final bool hasPendingNotifications;
  final int pendingNotificationCount;
  final VoidCallback onOpenNotifications;
  final bool settingsActive;
  final VoidCallback onOpenSettings;
  final Widget account;

  @override
  State<_HomeTitleBar> createState() => _HomeTitleBarState();
}

class _HomeTitleBarState extends State<_HomeTitleBar> {
  bool _maximized = false;

  @override
  void initState() {
    super.initState();
    unawaited(_syncMaximized());
  }

  @override
  void didUpdateWidget(_HomeTitleBar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.windowController != widget.windowController) {
      unawaited(_syncMaximized());
    }
  }

  Future<void> _syncMaximized() async {
    final maximized = await widget.windowController.isMaximizedWindow();
    if (!mounted) return;
    setState(() => _maximized = maximized);
  }

  void _minimize() {
    unawaited(widget.windowController.minimizeWindow());
  }

  void _toggleMaximize() {
    unawaited(() async {
      await widget.windowController.toggleMaximizeWindow();
      await _syncMaximized();
    }());
  }

  void _close() {
    unawaited(widget.windowController.closeWindow());
  }

  @override
  Widget build(BuildContext context) {
    // Windows/Linux 使用自定义窗口按钮；macOS 使用系统原生红绿灯，
    // Android/iOS 等非桌面平台不构建窗口控制区。
    final showWindowControls = _homeTitleBarShowsCustomWindowControls(context);
    final locked = widget.interactionLocked;
    final tabStrip = widget.tabStrip;
    final voiceCapsule = widget.voiceCapsule;
    final pendingCount = widget.pendingNotificationCount <= 0
        ? 1
        : widget.pendingNotificationCount;
    Widget lockable(Widget child) => IgnorePointer(
      ignoring: locked,
      child: Opacity(opacity: locked ? 0.54 : 1, child: child),
    );
    return SizedBox(
      height: _homeTitleBarHeight,
      child: ColoredBox(
        color: Colors.transparent,
        child: Row(
          children: [
            SizedBox(width: _homeTitleBarLeadingInset(context)),
            _HomeTitleBrand(showWordmark: !widget.compact),
            const SizedBox(width: 14),
            Expanded(
              child: AppWindowDragRegion(
                windowController: widget.windowController,
                onDoubleTap: _toggleMaximize,
                child: SizedBox.expand(
                  child: tabStrip == null
                      ? null
                      : Align(
                          alignment: Alignment.centerLeft,
                          child: lockable(tabStrip),
                        ),
                ),
              ),
            ),
            const SizedBox(width: 10),
            lockable(
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (voiceCapsule != null) ...[
                    voiceCapsule,
                    const SizedBox(width: 10),
                  ],
                  ButtonIcon(
                    key: const ValueKey('home-title-search-button'),
                    tooltip: '搜索',
                    icon: const Icon(Icons.search_rounded),
                    selected: widget.searchActive,
                    ghost: true,
                    size: _headerActionSize,
                    onPressed: widget.onOpenSearch,
                  ),
                  const SizedBox(width: 2),
                  Stack(
                    clipBehavior: Clip.none,
                    children: [
                      ButtonIcon(
                        key: const ValueKey(
                          'home-sidebar-notifications-button',
                        ),
                        tooltip: '通知',
                        icon: Icon(
                          widget.notificationsActive
                              ? Icons.notifications_rounded
                              : Icons.notifications_none_rounded,
                        ),
                        selected: widget.notificationsActive,
                        ghost: true,
                        size: _headerActionSize,
                        onPressed: widget.onOpenNotifications,
                      ),
                      if (widget.hasPendingNotifications)
                        Positioned(
                          right: -5,
                          top: -3,
                          child: IgnorePointer(
                            child: RoomUnreadBadge(count: pendingCount),
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(width: 2),
                  ButtonIcon(
                    key: const ValueKey('home-sidebar-settings-button'),
                    tooltip: '设置',
                    icon: Icon(
                      widget.settingsActive
                          ? Icons.settings_rounded
                          : Icons.settings_outlined,
                    ),
                    selected: widget.settingsActive,
                    ghost: true,
                    size: _headerActionSize,
                    onPressed: widget.onOpenSettings,
                  ),
                  const SizedBox(width: 8),
                  widget.account,
                ],
              ),
            ),
            SizedBox(width: showWindowControls ? 6 : 14),
            if (showWindowControls)
              SelectionContainer.disabled(
                child: SizedBox(
                  width: _homeTitleBarControlsWidth,
                  child: AppWindowControls(
                    maximized: _maximized,
                    onMinimize: _minimize,
                    onToggleMaximize: _toggleMaximize,
                    onClose: _close,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _HomeTitleBrand extends StatelessWidget {
  const _HomeTitleBrand({required this.showWordmark});

  final bool showWordmark;

  @override
  Widget build(BuildContext context) {
    return SelectionContainer.disabled(
      child: Row(
        key: const ValueKey('home-title-brand'),
        mainAxisSize: MainAxisSize.min,
        children: [
          const BrandMark(size: 30),
          if (showWordmark) ...[
            const SizedBox(width: 10),
            Text(
              'GANG CHAT',
              style: UiTypography.dot.copyWith(
                fontSize: 17,
                letterSpacing: 1.2,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// Avatar button in the header that opens the account panel: identity,
/// presence, connection quality and the account actions.
class _HeaderAccountButton extends StatelessWidget {
  const _HeaderAccountButton({
    required this.user,
    required this.inLive,
    required this.reconnecting,
    required this.requestRoundTrip,
    required this.logoutActive,
    required this.onOpenSettings,
    required this.onLogout,
  });

  final CurrentUser user;
  final bool inLive;
  final bool reconnecting;
  final Duration? requestRoundTrip;
  final bool logoutActive;
  final VoidCallback onOpenSettings;
  final VoidCallback onLogout;

  @override
  Widget build(BuildContext context) {
    final statusLabel = room_display.currentUserPresenceLabel(
      user,
      inLive: inLive,
      reconnecting: reconnecting,
    );
    final presenceColor = _presenceColor(statusLabel);
    final avatarUrl = AppConfigScope.of(context).resolveAssetUrl(user.avatarUrl);
    final latencyQuality = network_latency.networkLatencyQuality(
      requestRoundTrip,
    );
    return AnchoredPanelAnchor(
      width: 264,
      anchor: (context, open, toggle) => UiTooltip(
        message: user.displayName,
        child: MouseRegion(
          cursor: SystemMouseCursors.click,
          child: GestureDetector(
            key: const ValueKey('home-account-button'),
            behavior: HitTestBehavior.opaque,
            onTap: toggle,
            child: AnimatedContainer(
              duration: UiMotion.fast,
              padding: const EdgeInsets.all(2),
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(
                  color: open || logoutActive
                      ? UiColors.accent
                      : Colors.transparent,
                  width: 1.5,
                ),
              ),
              child: Stack(
                clipBehavior: Clip.none,
                children: [
                  Avatar(
                    label: room_display.userAvatarLabel(user.toSummary()),
                    imageUrl: avatarUrl,
                    defaultAvatarKey: user.defaultAvatarKey,
                    size: 30,
                    showBorder: false,
                  ),
                  Positioned(
                    right: -2,
                    bottom: -2,
                    child: Container(
                      key: const ValueKey('home-sidebar-presence-dot'),
                      width: 11,
                      height: 11,
                      decoration: BoxDecoration(
                        color: presenceColor,
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: UiColors.background,
                          width: 2,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
      panelBuilder: (context, close) => Column(
        key: const ValueKey('home-sidebar-user-summary'),
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 14, 14, 12),
            child: Row(
              children: [
                Avatar(
                  label: room_display.userAvatarLabel(user.toSummary()),
                  imageUrl: avatarUrl,
                  defaultAvatarKey: user.defaultAvatarKey,
                  size: 42,
                  showBorder: false,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        user.displayName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: UiTypography.subtitle,
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Container(
                            width: 7,
                            height: 7,
                            decoration: BoxDecoration(
                              color: presenceColor,
                              shape: BoxShape.circle,
                            ),
                          ),
                          const SizedBox(width: 6),
                          Text(
                            statusLabel,
                            style: UiTypography.caption.copyWith(
                              color: UiColors.textSecondary,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          Container(
            margin: const EdgeInsets.symmetric(horizontal: 10),
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
            decoration: BoxDecoration(
              color: UiColors.surface,
              borderRadius: BorderRadius.circular(UiRadii.md),
            ),
            child: Row(
              children: [
                LatencySignalBadge(
                  activeBars: network_latency.networkLatencySignalBars(
                    requestRoundTrip,
                  ),
                  activeColor: _latencySignalColor(latencyQuality),
                  tooltip: network_latency.networkLatencyTooltip(
                    requestRoundTrip,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    network_latency.networkLatencyTooltip(requestRoundTrip),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: UiTypography.caption.copyWith(
                      color: UiColors.textSecondary,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          const Divider(height: 1),
          Padding(
            padding: const EdgeInsets.all(6),
            child: Column(
              children: [
                _AccountMenuItem(
                  icon: Icons.tune_rounded,
                  label: '偏好与账号设置',
                  onPressed: () {
                    close();
                    onOpenSettings();
                  },
                ),
                _AccountMenuItem(
                  key: const ValueKey('home-account-logout'),
                  icon: Icons.logout_rounded,
                  label: '退出登录',
                  danger: true,
                  onPressed: () {
                    close();
                    onLogout();
                  },
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _AccountMenuItem extends StatefulWidget {
  const _AccountMenuItem({
    super.key,
    required this.icon,
    required this.label,
    required this.onPressed,
    this.danger = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback onPressed;
  final bool danger;

  @override
  State<_AccountMenuItem> createState() => _AccountMenuItemState();
}

class _AccountMenuItemState extends State<_AccountMenuItem> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    final color = widget.danger ? UiColors.danger : UiColors.text;
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: widget.onPressed,
        child: AnimatedContainer(
          duration: UiMotion.fast,
          height: 36,
          padding: const EdgeInsets.symmetric(horizontal: 10),
          decoration: BoxDecoration(
            color: _hovered
                ? (widget.danger
                      ? UiColors.danger.withValues(alpha: 0.14)
                      : UiColors.surfaceHover)
                : Colors.transparent,
            borderRadius: BorderRadius.circular(UiRadii.sm),
          ),
          child: Row(
            children: [
              Icon(
                widget.icon,
                size: 17,
                color: widget.danger ? color : UiColors.textSecondary,
              ),
              const SizedBox(width: 10),
              Text(
                widget.label,
                style: UiTypography.body.copyWith(fontSize: 13, color: color),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

Color _presenceColor(String label) {
  return switch (label) {
    '语音' => UiColors.presenceVoice,
    '重连中' => UiColors.presenceReconnecting,
    '离线' => UiColors.presenceOffline,
    _ => UiColors.presenceOnline,
  };
}

Color _latencySignalColor(network_latency.NetworkLatencyQuality quality) {
  return switch (quality) {
    network_latency.NetworkLatencyQuality.good => UiColors.success,
    network_latency.NetworkLatencyQuality.fair => UiColors.amber,
    network_latency.NetworkLatencyQuality.poor => UiColors.danger,
    network_latency.NetworkLatencyQuality.unavailable => UiColors.textMuted,
  };
}

/// Voice connection control. In the header it is a compact capsule; with
/// [fillAvailable] (compact layout) it becomes a full-width bar. Before a
/// voice channel is joined it still exposes the microphone and headphone
/// toggles so they can be set up front.
class _TitleLiveRoomDock extends StatelessWidget {
  const _TitleLiveRoomDock({
    required this.maxWidth,
    this.fillAvailable = false,
    required this.room,
    required this.micMuted,
    required this.headphonesMuted,
    required this.voiceBlocked,
    required this.onOpen,
    required this.onToggleMic,
    required this.onToggleHeadphones,
    required this.onLeave,
    required this.interactionLocked,
  });

  final double maxWidth;
  final bool fillAvailable;
  final live_display.JoinedLiveRoomSummary? room;
  final bool micMuted;
  final bool headphonesMuted;
  final bool voiceBlocked;
  final VoidCallback? onOpen;
  final VoidCallback? onToggleMic;
  final VoidCallback onToggleHeadphones;
  final VoidCallback? onLeave;
  final bool interactionLocked;

  @override
  Widget build(BuildContext context) {
    final joined = room != null;
    final label = room?.displayName ?? '未加入语音频道';
    final micControl = live_display.liveMicControlState(
      micMuted: micMuted,
      voiceBlocked: voiceBlocked,
    );
    final actions = <Widget>[
      _TitleLiveActionButton(
        key: const ValueKey<String>('home-title-live-room:mic'),
        size: _liveDockActionSize,
        tooltip: micControl.mutedForDisplay ? '打开麦克风' : '关闭麦克风',
        icon: micControl.mutedForDisplay
            ? Icons.mic_off_rounded
            : Icons.mic_rounded,
        color: !micControl.enabled
            ? UiColors.danger
            : micControl.active
            ? UiColors.text
            : UiColors.danger,
        onPressed: micControl.enabled ? onToggleMic : null,
      ),
      _TitleLiveActionButton(
        key: const ValueKey<String>('home-title-live-room:headphones'),
        size: _liveDockActionSize,
        tooltip: headphonesMuted ? '打开耳机' : '关闭耳机',
        icon: headphonesMuted
            ? Icons.headset_off_rounded
            : Icons.headphones_rounded,
        color: headphonesMuted ? UiColors.danger : UiColors.text,
        onPressed: onToggleHeadphones,
      ),
      if (joined)
        _TitleLiveActionButton(
          key: const ValueKey<String>('home-title-live-room:leave'),
          size: _liveDockActionSize,
          tooltip: '离开语音频道',
          icon: Icons.call_end_rounded,
          color: Colors.white,
          background: UiColors.danger.withValues(alpha: 0.85),
          onPressed: onLeave,
        ),
    ];
    final name = OverflowMarqueeText(
      key: const ValueKey<String>('home-title-live-room:name'),
      trackKey: const ValueKey<String>('home-title-live-room:marquee-track'),
      text: label,
      style: UiTypography.label.copyWith(
        color: joined ? UiColors.text : UiColors.textSecondary,
        fontWeight: FontWeight.w600,
      ),
    );
    final Widget dock;
    if (fillAvailable) {
      dock = Container(
        width: maxWidth,
        height: _liveDockHeight,
        padding: const EdgeInsets.only(left: 12, right: 8),
        decoration: BoxDecoration(
          color: joined ? const Color(0xFF13241F) : UiColors.surface,
          borderRadius: BorderRadius.circular(UiRadii.lg),
          border: Border.all(
            color: joined ? UiColors.successBorder : UiColors.border,
          ),
        ),
        child: Row(
          children: [
            _VoiceStatusOrb(joined: joined),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    joined ? '语音已连接' : '语音',
                    style: UiTypography.caption.copyWith(
                      color: joined ? UiColors.success : UiColors.textMuted,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 2),
                  name,
                ],
              ),
            ),
            ...actions,
          ],
        ),
      );
    } else {
      dock = AnimatedContainer(
        duration: UiMotion.standard,
        curve: UiMotion.curve,
        height: 36,
        padding: EdgeInsets.only(left: joined ? 10 : 4, right: 3),
        decoration: BoxDecoration(
          color: joined ? const Color(0xFF13241F) : UiColors.surface,
          borderRadius: BorderRadius.circular(UiRadii.pill),
          border: Border.all(
            color: joined ? UiColors.successBorder : UiColors.border,
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (joined) ...[
              const _VoiceStatusOrb(joined: true, size: 8),
              const SizedBox(width: 8),
              ConstrainedBox(
                constraints: BoxConstraints(
                  maxWidth: (maxWidth - 120).clamp(40.0, 150.0).toDouble(),
                ),
                child: name,
              ),
              const SizedBox(width: 6),
            ],
            ...actions,
          ],
        ),
      );
    }
    return UiTooltip(
      message: joined ? '打开语音频道' : '可在加入前设置麦克风和耳机',
      child: MouseRegion(
        cursor: joined ? SystemMouseCursors.click : SystemMouseCursors.basic,
        child: GestureDetector(
          key: const ValueKey<String>('home-title-live-room'),
          behavior: HitTestBehavior.opaque,
          onTap: onOpen,
          child: IgnorePointer(
            ignoring: interactionLocked,
            child: Opacity(opacity: interactionLocked ? 0.54 : 1, child: dock),
          ),
        ),
      ),
    );
  }
}

class _VoiceStatusOrb extends StatelessWidget {
  const _VoiceStatusOrb({required this.joined, this.size});

  final bool joined;
  final double? size;

  @override
  Widget build(BuildContext context) {
    final size = this.size;
    if (size != null) {
      return Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          color: UiColors.success,
          shape: BoxShape.circle,
          boxShadow: UiShadows.glow(UiColors.success, strength: 0.7),
        ),
      );
    }
    return Container(
      width: 30,
      height: 30,
      decoration: BoxDecoration(
        color: joined ? UiColors.successSurface : UiColors.surfaceRaised,
        borderRadius: BorderRadius.circular(9),
      ),
      child: Icon(
        Icons.graphic_eq_rounded,
        size: 16,
        color: joined ? UiColors.success : UiColors.textMuted,
      ),
    );
  }
}

class _TitleLiveActionButton extends StatefulWidget {
  const _TitleLiveActionButton({
    super.key,
    required this.tooltip,
    required this.icon,
    required this.color,
    required this.onPressed,
    required this.size,
    this.background,
  });

  final String tooltip;
  final IconData icon;
  final Color color;
  final VoidCallback? onPressed;
  final double size;
  final Color? background;

  @override
  State<_TitleLiveActionButton> createState() => _TitleLiveActionButtonState();
}

class _TitleLiveActionButtonState extends State<_TitleLiveActionButton> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    final enabled = widget.onPressed != null;
    final background = widget.background;
    return UiTooltip(
      message: widget.tooltip,
      child: MouseRegion(
        cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
        onEnter: (_) => setState(() => _hovered = true),
        onExit: (_) => setState(() => _hovered = false),
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: () {
            if (enabled) widget.onPressed!();
          },
          child: AnimatedContainer(
            duration: UiMotion.fast,
            width: widget.size,
            height: widget.size,
            margin: const EdgeInsets.symmetric(horizontal: 1),
            decoration: BoxDecoration(
              color: background != null
                  ? (_hovered ? UiStates.hover(background) : background)
                  : _hovered && enabled
                  ? Colors.white.withValues(alpha: 0.08)
                  : Colors.transparent,
              shape: BoxShape.circle,
            ),
            child: Center(
              child: Icon(
                widget.icon,
                size: 16,
                color: enabled
                    ? widget.color
                    : widget.color.withValues(alpha: 0.56),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Centered search overlay ("command palette"): one large field on top and
/// the categorised results underneath.
class _SearchPalette extends StatelessWidget {
  const _SearchPalette({
    required this.compact,
    required this.field,
    required this.results,
    required this.onDismiss,
  });

  final bool compact;
  final Widget field;
  final Widget? results;
  final VoidCallback onDismiss;

  @override
  Widget build(BuildContext context) {
    final results = this.results;
    return CallbackShortcuts(
      bindings: {
        const SingleActivator(LogicalKeyboardKey.escape): onDismiss,
      },
      child: Stack(
        key: const ValueKey('home-search-palette'),
        children: [
          Positioned.fill(
            child: GestureDetector(
              key: const ValueKey('home-search-palette-barrier'),
              behavior: HitTestBehavior.opaque,
              onTap: onDismiss,
              child: const ColoredBox(color: Color(0x99050608)),
            ),
          ),
          Positioned.fill(
            child: SafeArea(
              child: Align(
                alignment: Alignment.topCenter,
                child: Padding(
                  padding: EdgeInsets.fromLTRB(
                    compact ? 10 : 24,
                    compact ? 10 : 72,
                    compact ? 10 : 24,
                    24,
                  ),
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(
                      maxWidth: _homeSearchPaletteMaxWidth,
                    ),
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: UiColors.surfaceRaised,
                        borderRadius: BorderRadius.circular(UiRadii.xl),
                        border: Border.all(color: UiColors.borderStrong),
                        boxShadow: UiShadows.dialog,
                      ),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(UiRadii.xl - 1),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            Padding(
                              padding: const EdgeInsets.all(10),
                              child: SizedBox(
                                height: _homeSearchFieldHeight,
                                child: field,
                              ),
                            ),
                            const Divider(height: 1),
                            if (results != null)
                              Flexible(child: results)
                            else
                              const _SearchPaletteHint(),
                          ],
                        ),
                      ),
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

class _SearchPaletteHint extends StatelessWidget {
  const _SearchPaletteHint();

  @override
  Widget build(BuildContext context) {
    Widget chip(IconData icon, String label) => Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: UiColors.surface,
        borderRadius: BorderRadius.circular(UiRadii.pill),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: UiColors.textMuted),
          const SizedBox(width: 6),
          Text(
            label,
            style: UiTypography.caption.copyWith(
              color: UiColors.textSecondary,
            ),
          ),
        ],
      ),
    );
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 16, 18, 18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '可以搜索',
            style: UiTypography.overline,
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              chip(Icons.meeting_room_outlined, '我的房间'),
              chip(Icons.public_rounded, '公开房间'),
              chip(Icons.chat_bubble_outline_rounded, '聊天记录'),
              chip(Icons.insert_drive_file_outlined, '文件'),
            ],
          ),
        ],
      ),
    );
  }
}

/// 搜索面板顶部的大号输入框。
class _TitleSearchField extends StatefulWidget {
  const _TitleSearchField({
    required this.controller,
    required this.tapRegionGroup,
    required this.enabled,
    required this.onContextMenuOpenChanged,
    required this.onActivated,
    required this.onClearQuery,
    this.autofocus = false,
  });

  final TextEditingController controller;
  final Object tapRegionGroup;
  final bool enabled;
  final ValueChanged<bool> onContextMenuOpenChanged;
  final VoidCallback onActivated;
  final VoidCallback onClearQuery;
  final bool autofocus;

  @override
  State<_TitleSearchField> createState() => _TitleSearchFieldState();
}

class _TitleSearchFieldState extends State<_TitleSearchField> {
  final FocusNode _focusNode = FocusNode();
  final UndoHistoryController _undoController = UndoHistoryController();
  late final EditableTextContextMenuBuilder _contextMenuBuilder =
      _buildContextMenu;
  bool _focused = false;
  bool _hasText = false;

  @override
  void initState() {
    super.initState();
    _hasText = widget.controller.text.trim().isNotEmpty;
    widget.controller.addListener(_handleTextChanged);
    _focusNode.addListener(_handleFocusChange);
  }

  @override
  void didUpdateWidget(_TitleSearchField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.enabled && !widget.enabled) {
      _focusNode.unfocus();
    }
    if (oldWidget.controller == widget.controller) return;
    oldWidget.controller.removeListener(_handleTextChanged);
    _hasText = widget.controller.text.trim().isNotEmpty;
    widget.controller.addListener(_handleTextChanged);
  }

  @override
  void dispose() {
    widget.controller.removeListener(_handleTextChanged);
    _focusNode.removeListener(_handleFocusChange);
    _focusNode.dispose();
    _undoController.dispose();
    super.dispose();
  }

  void _handleFocusChange() {
    if (_focusNode.hasFocus && widget.enabled) widget.onActivated();
    if (_focused != _focusNode.hasFocus) {
      setState(() => _focused = _focusNode.hasFocus);
    }
  }

  void _handleTextChanged() {
    final hasText = widget.controller.text.trim().isNotEmpty;
    if (_hasText == hasText) return;
    setState(() => _hasText = hasText);
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: _homeSearchFieldHeight,
      padding: const EdgeInsets.only(left: 12, right: 8),
      child: Row(
        children: [
          const Icon(Icons.search_rounded, size: 20, color: UiColors.accent),
          const SizedBox(width: 12),
          Expanded(
            child: TextFieldEditingShortcuts(
              controller: widget.controller,
              focusNode: _focusNode,
              undoController: _undoController,
              child: TextField(
                controller: widget.controller,
                focusNode: _focusNode,
                autofocus: widget.autofocus,
                maxLines: 1,
                enabled: widget.enabled,
                textInputAction: TextInputAction.search,
                undoController: _undoController,
                onTap: widget.enabled ? widget.onActivated : null,
                cursorColor: UiColors.accent,
                cursorWidth: 1.5,
                style: UiTypography.body.copyWith(fontSize: 16, height: 1.2),
                contextMenuBuilder: _contextMenuBuilder,
                decoration: InputDecoration(
                  isCollapsed: true,
                  border: InputBorder.none,
                  hintText: '搜索房间、聊天记录和文件',
                  hintStyle: UiTypography.body.copyWith(
                    fontSize: 16,
                    height: 1.2,
                    color: UiColors.textMuted,
                  ),
                ),
              ),
            ),
          ),
          if (_hasText && widget.enabled) ...[
            const SizedBox(width: 6),
            UiTooltip(
              message: '清空搜索',
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: widget.onClearQuery,
                child: Container(
                  width: 22,
                  height: 22,
                  decoration: const BoxDecoration(
                    color: UiColors.surfaceHover,
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    Icons.close_rounded,
                    size: 14,
                    color: UiColors.textSecondary,
                  ),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildContextMenu(
    BuildContext context,
    EditableTextState editableTextState,
  ) {
    return buildTextFieldContextMenu(
      context,
      editableTextState,
      undoController: _undoController,
      tapRegionGroupId: widget.tapRegionGroup,
      onOpenChanged: widget.onContextMenuOpenChanged,
    );
  }
}

class _TitleSearchResultsPanel extends StatelessWidget {
  const _TitleSearchResultsPanel({
    required this.query,
    required this.results,
    required this.loading,
    required this.loadingMore,
    required this.error,
    required this.timestampNow,
    required this.currentUser,
    required this.activeCategory,
    required this.visibleCategories,
    required this.busyPublicRoomId,
    required this.pendingPublicRoomIds,
    required this.onCategorySelected,
    required this.onLoadMore,
    required this.onMyRoomSelected,
    required this.onProfileRoomSelected,
    required this.onResolveRoomProfile,
    required this.onResolveRoomUserProfile,
    required this.onResolveUserProfile,
    required this.onPublicRoomAction,
    required this.onUserSettingsSelected,
    required this.onMessageSelected,
    required this.onFileSelected,
  });

  final String query;
  final GlobalSearchResults? results;
  final bool loading;
  final bool loadingMore;
  final String? error;
  final DateTime timestampNow;
  final CurrentUser currentUser;
  final search_display.GlobalSearchCategory? activeCategory;
  final List<search_display.GlobalSearchCategory> visibleCategories;
  final String? busyPublicRoomId;
  final Set<String> pendingPublicRoomIds;
  final ValueChanged<search_display.GlobalSearchCategory> onCategorySelected;
  final VoidCallback onLoadMore;
  final ValueChanged<RoomCard> onMyRoomSelected;
  final ValueChanged<PublicRoom> onProfileRoomSelected;
  final RoomProfileResolver onResolveRoomProfile;
  final Future<UserSummary> Function(String roomId, UserSummary user)
  onResolveRoomUserProfile;
  final UserProfileResolver onResolveUserProfile;
  final ValueChanged<PublicRoom> onPublicRoomAction;
  final ValueChanged<UserSummary> onUserSettingsSelected;
  final ValueChanged<MessageSearchResult> onMessageSelected;
  final ValueChanged<MessageSearchResult> onFileSelected;

  @override
  Widget build(BuildContext context) {
    final message = error?.trim();
    final panel = DecoratedBox(
      decoration: BoxDecoration(
        color: UiColors.surfaceRaised,
        borderRadius: BorderRadius.circular(UiRadii.lg),
        border: Border.all(color: UiColors.borderStrong),
        boxShadow: UiShadows.popover,
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(UiRadii.lg),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxHeight: 480),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              _SearchCategoryTabs(
                results: results,
                activeCategory: activeCategory,
                categories: currentUser.isSuperuser
                    ? search_display.superuserGlobalSearchCategories
                    : search_display.globalSearchCategories,
                onCategorySelected: onCategorySelected,
              ),
              const Divider(height: 1, color: UiColors.border),
              Flexible(child: _buildBody(context)),
            ],
          ),
        ),
      ),
    );
    if (message == null || message.isEmpty) return panel;
    return FloatingNoticeEmitter(
      notices: [
        FloatingNotice(
          message: message,
          tone: FloatingNoticeTone.error,
          duration: null,
        ),
      ],
      child: panel,
    );
  }

  Widget _buildBody(BuildContext context) {
    final failure = error;
    if (failure != null) {
      return _SearchPanelState(
        icon: Icons.error_outline,
        title: '搜索失败',
        detail: '请稍后重试',
      );
    }

    final snapshot = results;
    if (snapshot == null && loading) {
      return const SizedBox(
        height: 104,
        child: Center(
          child: SizedBox.square(
            dimension: 22,
            child: CircularProgressIndicator(
              strokeWidth: 2.2,
              color: UiColors.accent,
            ),
          ),
        ),
      );
    }

    if (!search_display.globalSearchHasResults(snapshot)) {
      return _SearchPanelState(
        icon: Icons.search,
        title: '没有找到结果',
        detail: query.trim(),
      );
    }

    final sections = <Widget>[];
    for (final category in visibleCategories) {
      final section = _sectionFor(context, snapshot!, category);
      if (section != null) {
        if (sections.isNotEmpty) sections.add(const SizedBox(height: 10));
        sections.add(section);
      }
    }

    if (sections.isEmpty) {
      return _SearchPanelState(
        icon: Icons.filter_alt_outlined,
        title: '该分类没有结果',
        detail: activeCategory == null
            ? query.trim()
            : search_display.globalSearchCategoryLabel(activeCategory!),
      );
    }

    return NotificationListener<ScrollNotification>(
      onNotification: _handleScrollNotification,
      child: ListView(
        shrinkWrap: true,
        padding: const EdgeInsets.fromLTRB(10, 10, 10, 12),
        children: [
          ...sections,
          if (loadingMore) const _SearchLoadingMoreIndicator(),
        ],
      ),
    );
  }

  bool _handleScrollNotification(ScrollNotification notification) {
    if (loadingMore || notification.metrics.axis != Axis.vertical) {
      return false;
    }
    if (notification.metrics.maxScrollExtent <= 0) return false;
    if (notification.metrics.extentAfter <= 96) onLoadMore();
    return false;
  }

  Widget? _sectionFor(
    BuildContext context,
    GlobalSearchResults snapshot,
    search_display.GlobalSearchCategory category,
  ) {
    final count = search_display.globalSearchCategoryCount(snapshot, category);
    if (count == 0) return null;

    final children = switch (category) {
      search_display.GlobalSearchCategory.myRooms => snapshot.myRooms.map((
        room,
      ) {
        final time = room_display.roomSidebarLastMessageTime(
          room,
          now: timestampNow,
        );
        return _RoomSearchResultTile(
          title: room.displayName,
          subtitle: room_display.roomSidebarSubtitle(room),
          query: query,
          leading: RoomHoverCard(
            room: _publicRoomFromRoomCard(room),
            currentUser: currentUser,
            onResolveRoom: onResolveRoomProfile,
            onResolveUserProfile: (user) =>
                onResolveRoomUserProfile(room.id, user),
            onEnterRoom: onProfileRoomSelected,
            child: Avatar(
              label: room_display.roomCardAvatarLabel(room),
              imageUrl: AppConfigScope.of(
                context,
              ).resolveAssetUrl(room.avatarUrl),
              defaultAvatarKey: room.defaultAvatarKey,
              size: 30,
            ),
          ),
          titleTrailing: time.isEmpty
              ? null
              : Text(
                  time,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: UiTypography.label.copyWith(
                    color: UiColors.textMuted,
                    fontSize: 10.5,
                  ),
                ),
          trailing: _MyRoomEnterActivity(
            room: room,
            onPressed: onMyRoomSelected,
          ),
        );
      }).toList(),
      search_display.GlobalSearchCategory.publicRooms =>
        room_join
            .publicRoomJoinCandidates(
              rooms: snapshot.publicRooms,
              pendingRoomIds: pendingPublicRoomIds,
              busyRoomId: busyPublicRoomId,
            )
            .map(
              (candidate) => _RoomSearchResultTile(
                title: candidate.room.name,
                subtitle: _publicRoomSearchMeta(candidate.room),
                query: query,
                leading: RoomHoverCard(
                  room: candidate.room,
                  currentUser: currentUser,
                  onResolveRoom: onResolveRoomProfile,
                  onResolveUserProfile: (user) =>
                      onResolveRoomUserProfile(candidate.room.id, user),
                  onEnterRoom: candidate.room.joined
                      ? onProfileRoomSelected
                      : null,
                  child: Avatar(
                    label: candidate.room.name,
                    imageUrl: AppConfigScope.of(
                      context,
                    ).resolveAssetUrl(candidate.room.avatarUrl),
                    defaultAvatarKey: candidate.room.defaultAvatarKey,
                    size: 30,
                  ),
                ),
                trailing: _PublicRoomJoinActivity(
                  candidate: candidate,
                  onPressed: onPublicRoomAction,
                ),
              ),
            )
            .toList(),
      search_display.GlobalSearchCategory.userSettings =>
        snapshot.userSettings
            .map(
              (user) => _RoomSearchResultTile(
                title: user.displayName.trim().isEmpty
                    ? user.username
                    : user.displayName,
                subtitle: '@${user.username}',
                query: query,
                leading: UserHoverCard(
                  user: user,
                  currentUser: currentUser,
                  onResolveProfile: onResolveUserProfile,
                  child: Avatar(
                    label: user.displayName,
                    imageUrl: AppConfigScope.of(
                      context,
                    ).resolveAssetUrl(user.avatarUrl),
                    defaultAvatarKey: user.defaultAvatarKey,
                    size: 30,
                  ),
                ),
                trailing: Button(
                  key: ValueKey('open-user-settings-${user.id}'),
                  width: _searchResultActionWidth(context, hasIcon: true),
                  height: 30,
                  padding: _searchResultActionPadding(
                    context,
                    wideHorizontal: 16,
                  ),
                  onPressed: () => onUserSettingsSelected(user),
                  icon: const Icon(Icons.manage_accounts_outlined, size: 15),
                  child: const Text('进入设置'),
                ),
              ),
            )
            .toList(),
      search_display.GlobalSearchCategory.messages =>
        snapshot.messages
            .map(
              (result) => _MessageSearchResultTile(
                result: result,
                title: search_display.globalSearchMessageTitle(result),
                subtitle: search_display.globalSearchMessageSubtitle(result),
                query: query,
                onPressed: () => onMessageSelected(result),
              ),
            )
            .toList(),
      search_display.GlobalSearchCategory.files =>
        snapshot.files
            .map(
              (result) => _MessageSearchResultTile(
                result: result,
                title: search_display.globalSearchFileTitle(result),
                subtitle: search_display.globalSearchFileSubtitle(result),
                query: query,
                onPressed: () => onFileSelected(result),
              ),
            )
            .toList(),
    };

    return _SearchResultSection(
      title: search_display.globalSearchCategoryLabel(category),
      count: count,
      children: children,
    );
  }
}

class _SearchCategoryTabs extends StatelessWidget {
  const _SearchCategoryTabs({
    required this.results,
    required this.activeCategory,
    required this.categories,
    required this.onCategorySelected,
  });

  final GlobalSearchResults? results;
  final search_display.GlobalSearchCategory? activeCategory;
  final List<search_display.GlobalSearchCategory> categories;
  final ValueChanged<search_display.GlobalSearchCategory> onCategorySelected;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(8),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final compact = constraints.maxWidth < 380;
          final columns = compact ? 2 : 4;
          final itemWidth =
              ((constraints.maxWidth - (columns - 1) * 6) / columns)
                  .clamp(0.0, constraints.maxWidth)
                  .toDouble();
          return Wrap(
            spacing: 6,
            runSpacing: 6,
            children: [
              for (final category in categories)
                SizedBox(
                  width: itemWidth,
                  child: CompactCategoryButton(
                    key: ValueKey('search-category-${category.name}'),
                    label: search_display.globalSearchCategoryLabel(category),
                    count: search_display.globalSearchCategoryCount(
                      results,
                      category,
                    ),
                    selected: activeCategory == category,
                    onPressed: () => onCategorySelected(category),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

class _SearchResultSection extends StatelessWidget {
  const _SearchResultSection({
    required this.title,
    required this.count,
    required this.children,
  });

  final String title;
  final int count;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(2, 0, 2, 7),
          child: Text(
            '$title $count',
            style: UiTypography.label.copyWith(color: UiColors.textMuted),
          ),
        ),
        ...children,
      ],
    );
  }
}

class _SearchLoadingMoreIndicator extends StatelessWidget {
  const _SearchLoadingMoreIndicator();

  @override
  Widget build(BuildContext context) {
    return const SizedBox(
      height: 34,
      child: Center(
        child: SizedBox.square(
          dimension: 16,
          child: CircularProgressIndicator(
            strokeWidth: 2,
            color: UiColors.accent,
          ),
        ),
      ),
    );
  }
}

class _RoomSearchResultTile extends StatelessWidget {
  const _RoomSearchResultTile({
    required this.title,
    required this.subtitle,
    required this.query,
    required this.leading,
    this.titleTrailing,
    this.trailing,
  });

  final String title;
  final String subtitle;
  final String query;
  final Widget leading;
  final Widget? titleTrailing;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return _SearchResultTile(
      leading: leading,
      title: title,
      titleTrailing: titleTrailing,
      subtitle: subtitle,
      query: query,
      trailing: trailing,
    );
  }
}

class _MyRoomEnterActivity extends StatelessWidget {
  const _MyRoomEnterActivity({required this.room, required this.onPressed});

  final RoomCard room;
  final ValueChanged<RoomCard> onPressed;

  @override
  Widget build(BuildContext context) {
    return Button(
      key: ValueKey('my-room-action-${room.id}'),
      width: _searchResultActionWidth(context, hasIcon: false),
      height: 30,
      padding: _searchResultActionPadding(context, wideHorizontal: 10),
      tone: ButtonTone.primary,
      onPressed: () => onPressed(room),
      child: const Text('进入房间'),
    );
  }
}

class _PublicRoomJoinActivity extends StatelessWidget {
  const _PublicRoomJoinActivity({
    required this.candidate,
    required this.onPressed,
  });

  final room_join.PublicRoomJoinCandidate candidate;
  final ValueChanged<PublicRoom> onPressed;

  @override
  Widget build(BuildContext context) {
    final room = candidate.room;
    return Button(
      key: ValueKey('public-room-action-${room.id}'),
      width: _searchResultActionWidth(context, hasIcon: false),
      height: 30,
      padding: _searchResultActionPadding(context, wideHorizontal: 10),
      tone: candidate.actionable ? ButtonTone.primary : ButtonTone.neutral,
      loading: candidate.busy,
      onPressed: candidate.actionEnabled ? () => onPressed(room) : null,
      child: Text(_publicRoomSearchActionLabel(candidate)),
    );
  }
}

EdgeInsets _searchResultActionPadding(
  BuildContext context, {
  required double wideHorizontal,
}) {
  return EdgeInsets.symmetric(
    horizontal: HomeAdaptiveLayout.usesCompactLayout(context)
        ? 4
        : wideHorizontal,
  );
}

double? _searchResultActionWidth(
  BuildContext context, {
  required bool hasIcon,
}) {
  if (!HomeAdaptiveLayout.usesCompactLayout(context)) return null;
  return hasIcon ? 90 : 72;
}

class _MessageSearchResultTile extends StatelessWidget {
  const _MessageSearchResultTile({
    required this.result,
    required this.title,
    required this.subtitle,
    required this.query,
    required this.onPressed,
  });

  final MessageSearchResult result;
  final String title;
  final String subtitle;
  final String query;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return _SearchResultTile(
      key: ValueKey('search-message-result-${result.message.id}'),
      onPressed: onPressed,
      leading: Avatar(
        label: result.room.name,
        imageUrl: AppConfigScope.of(
          context,
        ).resolveAssetUrl(result.room.avatarUrl),
        defaultAvatarKey: result.room.defaultAvatarKey,
        size: 30,
      ),
      title: title,
      subtitle: subtitle,
      query: query,
      trailing: Text(
        search_display.globalSearchResultTimeLabel(result),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: UiTypography.label.copyWith(color: UiColors.textMuted),
      ),
    );
  }
}

class _SearchResultTile extends StatefulWidget {
  const _SearchResultTile({
    super.key,
    required this.leading,
    required this.title,
    required this.subtitle,
    required this.query,
    this.titleTrailing,
    this.trailing,
    this.onPressed,
  });

  final Widget leading;
  final String title;
  final String subtitle;
  final String query;
  final Widget? titleTrailing;
  final Widget? trailing;
  final VoidCallback? onPressed;

  @override
  State<_SearchResultTile> createState() => _SearchResultTileState();
}

class _SearchResultTileState extends State<_SearchResultTile> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    final interactive = widget.onPressed != null;
    final compact = HomeAdaptiveLayout.usesCompactLayout(context);
    return MouseRegion(
      cursor: interactive ? SystemMouseCursors.click : SystemMouseCursors.basic,
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: widget.onPressed,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 90),
          margin: const EdgeInsets.only(bottom: 6),
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
          decoration: BoxDecoration(
            color: _hovered && interactive
                ? UiColors.surface
                : Colors.transparent,
            borderRadius: BorderRadius.circular(UiRadii.sm),
          ),
          child: Row(
            children: [
              SizedBox.square(
                dimension: 30,
                child: Center(child: widget.leading),
              ),
              const SizedBox(width: 9),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: HighlightedText(
                            text: widget.title,
                            query: widget.query,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: UiTypography.body.copyWith(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                        if (widget.titleTrailing != null) ...[
                          const SizedBox(width: 8),
                          widget.titleTrailing!,
                        ],
                      ],
                    ),
                    if (widget.subtitle.trim().isNotEmpty) ...[
                      const SizedBox(height: 3),
                      Row(
                        children: [
                          Expanded(
                            child: HighlightedText(
                              text: widget.subtitle,
                              query: widget.query,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: UiTypography.label.copyWith(
                                color: UiColors.textMuted,
                                height: compact ? 1.35 : 1.2,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ],
                ),
              ),
              if (widget.trailing != null) ...[
                const SizedBox(width: 8),
                ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 126),
                  child: widget.trailing!,
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _SearchPanelState extends StatelessWidget {
  const _SearchPanelState({
    required this.icon,
    required this.title,
    required this.detail,
  });

  final IconData icon;
  final String title;
  final String detail;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 118,
      child: Center(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 18),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, color: UiColors.textMuted, size: 22),
              const SizedBox(height: 8),
              Text(title, style: UiTypography.body),
              const SizedBox(height: 4),
              Text(
                detail,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: UiTypography.label.copyWith(color: UiColors.textMuted),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

String _publicRoomSearchMeta(PublicRoom room) {
  return '${room.memberCount} 名成员';
}

String _publicRoomSearchActionLabel(
  room_join.PublicRoomJoinCandidate candidate,
) {
  final room = candidate.room;
  if (room.joined) return '进入房间';
  if (candidate.pending) return '待审批';
  if (room.joinPolicy == 'closed') return '不可加入';
  return '加入房间';
}

PublicRoom _publicRoomFromRoomCard(RoomCard room) {
  return PublicRoom(
    id: room.id,
    rid: room.rid,
    name: room.displayName,
    avatarLabel: room_display.roomCardAvatarLabel(room),
    avatarUrl: room.avatarUrl,
    defaultAvatarKey: room.defaultAvatarKey,
    visibility: room.visibility,
    joinPolicy: room.visibility == 'public' ? 'open' : 'closed',
    description: room.description,
    memberCount: room.memberCount,
    onlineMemberCount: room.onlineMemberCount,
    liveParticipantCount: room.liveParticipantCount,
    joined: true,
    joinState: 'joined',
    myMembership: RoomMembership(
      joinedAt: DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
      role: 'member',
    ),
  );
}
