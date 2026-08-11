part of 'live_channel_pane.dart';

/// Minimum height retained by the docked console. When the live controls need
/// an extra row, the panel extends upward instead of making the whole console
/// scroll.
const double _musicBoxMinComfortableHeight = 400;

/// The search field / queue-toggle button height — slimmer than the app-wide
/// [Input.defaultHeight] to keep the docked panel dense.
const double _musicBoxSearchFieldHeight = 30;

/// Shared geometry for saved-playlist rows and playlist-picker targets.
const double _musicBoxPlaylistRowMinHeight = 50;
const double _musicBoxPlaylistRowVerticalPadding = 18;

/// The in-pane music box console: a spinning vinyl for the current track, a
/// progress bar with transport controls, the queue, and a search-to-queue
/// field. Audio is delivered separately via the LiveKit session; this is purely
/// the control surface and status display.
class LiveMusicBoxPanel extends StatelessWidget {
  const LiveMusicBoxPanel({
    super.key,
    required this.state,
    required this.searchController,
    required this.searchResults,
    required this.searching,
    required this.searchError,
    required this.source,
    required this.onTogglePlayback,
    required this.onSkip,
    this.onPrevious,
    this.onModeChanged,
    this.controller,
    this.roomId,
    this.room,
    this.previewPlatformFactory,
    this.onStateChanged,
    this.currentUser,
    this.onResolveUserProfile,
    this.onResolveRoomProfile,
    this.onEnterCommonRoom,
    this.userProfileActionBuilder,
    this.onCreateFirstRoomPlaylist,
    this.onCreateFirstPersonalPlaylist,
    this.onEditRoomPlaylist,
    this.onEditPersonalPlaylist,
    required this.onQueueResult,
    required this.onRemoveItem,
    required this.onSourceChanged,
    required this.onClose,
    required this.volume,
    required this.onVolumeChanged,
  });

  final MusicBoxState state;
  final TextEditingController searchController;
  final List<MusicBoxSearchResult> searchResults;
  final bool searching;
  final String? searchError;
  final String source;
  final VoidCallback onTogglePlayback;
  final VoidCallback onSkip;
  final VoidCallback? onPrevious;
  final ValueChanged<MusicBoxPlaybackMode>? onModeChanged;
  final MusicBoxController? controller;
  final String? roomId;
  final PublicRoom? room;
  final MusicTrackPreviewPlatformFactory? previewPlatformFactory;
  final ValueChanged<MusicBoxState>? onStateChanged;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;
  final VoidCallback? onCreateFirstRoomPlaylist;
  final VoidCallback? onCreateFirstPersonalPlaylist;
  final MusicPlaylistEditCallback? onEditRoomPlaylist;
  final MusicPlaylistEditCallback? onEditPersonalPlaylist;
  final ValueChanged<MusicBoxSearchResult> onQueueResult;
  final ValueChanged<MusicBoxQueueItem> onRemoveItem;
  final ValueChanged<String> onSourceChanged;
  final VoidCallback onClose;

  /// Local listening volume for the music box (0–1), restored from the store.
  final double volume;
  final ValueChanged<double> onVolumeChanged;

  @override
  Widget build(BuildContext context) {
    final bodyKey = GlobalObjectKey<_MusicBoxBodyState>(searchController);
    final body = _MusicBoxBody(
      key: bodyKey,
      state: state,
      searchController: searchController,
      searchResults: searchResults,
      searching: searching,
      searchError: searchError,
      source: source,
      onQueueResult: onQueueResult,
      onRemoveItem: onRemoveItem,
      onSourceChanged: onSourceChanged,
      controller: controller,
      roomId: roomId,
      room: room,
      previewPlatformFactory: previewPlatformFactory,
      onStateChanged: onStateChanged,
      currentUser: currentUser,
      onResolveUserProfile: onResolveUserProfile,
      onResolveRoomProfile: onResolveRoomProfile,
      onEnterCommonRoom: onEnterCommonRoom,
      userProfileActionBuilder: userProfileActionBuilder,
      onCreateFirstRoomPlaylist: onCreateFirstRoomPlaylist,
      onCreateFirstPersonalPlaylist: onCreateFirstPersonalPlaylist,
      onEditRoomPlaylist: onEditRoomPlaylist,
      onEditPersonalPlaylist: onEditPersonalPlaylist,
    );
    return DecoratedBox(
      decoration: BoxDecoration(
        color: UiColors.surface,
        borderRadius: BorderRadius.circular(_liveRoomRadius),
        border: Border.all(color: UiColors.border),
        boxShadow: const [
          BoxShadow(
            color: Color(0x66080A0D),
            offset: Offset(0, 10),
            blurRadius: 22,
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(11),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            ..._controls(
              onPrimaryPlayback: () {
                if (state.currentItem == null) {
                  final start = bodyKey.currentState
                      ?.startViewedSourceFromBeginning();
                  if (start != null) {
                    unawaited(start);
                    return;
                  }
                }
                onTogglePlayback();
              },
              canStartViewedSource: controller != null && roomId != null,
            ),
            Expanded(child: body),
          ],
        ),
      ),
    );
  }

  // The fixed control stack shared by both the roomy and compact layouts.
  List<Widget> _controls({
    required VoidCallback onPrimaryPlayback,
    required bool canStartViewedSource,
  }) {
    return [
      _MusicBoxHeader(onClose: onClose),
      const SizedBox(height: 8),
      _MusicBoxNowPlaying(
        state: state,
        onTogglePlayback: onPrimaryPlayback,
        canStartViewedSource: canStartViewedSource,
        onSkip: onSkip,
        onPrevious: onPrevious,
        onModeChanged: onModeChanged,
      ),
      const SizedBox(height: 7),
      _MusicBoxVolume(initialVolume: volume, onChanged: onVolumeChanged),
      const SizedBox(height: 9),
    ];
  }
}

class _MusicBoxHeader extends StatelessWidget {
  const _MusicBoxHeader({required this.onClose});

  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const Icon(Icons.library_music, size: 16, color: UiColors.accent),
        const SizedBox(width: 8),
        const Text(
          '音乐盒',
          style: TextStyle(
            color: UiColors.text,
            fontSize: 14,
            fontWeight: FontWeight.w500,
          ),
        ),
        const Spacer(),
        const SizedBox(width: 6),
        ButtonIcon(
          key: const ValueKey<String>('music-box-close'),
          icon: const Icon(Icons.close),
          tooltip: '关闭音乐盒',
          onPressed: onClose,
          size: 26,
        ),
      ],
    );
  }
}

/// The now-playing strip: spinning vinyl, title/artist, progress bar, and
/// transport controls.
class _MusicBoxNowPlaying extends StatelessWidget {
  const _MusicBoxNowPlaying({
    required this.state,
    required this.onTogglePlayback,
    required this.canStartViewedSource,
    required this.onSkip,
    required this.onPrevious,
    required this.onModeChanged,
  });

  final MusicBoxState state;
  final VoidCallback onTogglePlayback;
  final bool canStartViewedSource;
  final VoidCallback onSkip;
  final VoidCallback? onPrevious;
  final ValueChanged<MusicBoxPlaybackMode>? onModeChanged;

  @override
  Widget build(BuildContext context) {
    final current = state.currentItem;
    final spinning = music_box_display.musicBoxRecordSpinning(state);
    final transport = music_box_display.musicBoxPrimaryTransport(state);
    final hasQueue = state.queue.isNotEmpty;
    final hasPlayableSource =
        hasQueue || (current == null && canStartViewedSource);
    final canControl = state.playback.capabilities.canControl;

    return Row(
      children: [
        _VinylRecord(spinning: spinning, label: current?.title),
        const SizedBox(width: 11),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              OverflowMarqueeText(
                key: const ValueKey<String>('music-box-now-playing:title'),
                trackKey: const ValueKey<String>(
                  'music-box-now-playing:title-marquee-track',
                ),
                text: current?.title ?? '未在播放',
                style: TextStyle(
                  color: current == null ? UiColors.textMuted : UiColors.text,
                  fontSize: 13,
                  fontWeight: FontWeight.w500,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                current?.artist.isNotEmpty == true
                    ? current!.artist
                    : '点一首歌开始播放',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: UiColors.textSecondary,
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 8),
              _MusicBoxProgressBar(state: state),
              const SizedBox(height: 10),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  ButtonIcon(
                    key: const ValueKey<String>('music-box-transport-previous'),
                    icon: const Icon(Icons.skip_previous),
                    tooltip: '上一首',
                    onPressed: canControl && state.playback.canPrevious
                        ? onPrevious
                        : null,
                    size: 30,
                  ),
                  const SizedBox(width: 7),
                  ButtonIcon(
                    key: const ValueKey<String>('music-box-primary-playback'),
                    icon: Icon(
                      transport ==
                              music_box_display.MusicBoxTransportAction.pause
                          ? Icons.pause
                          : Icons.play_arrow,
                    ),
                    tooltip: switch (transport) {
                      music_box_display.MusicBoxTransportAction.pause => '暂停',
                      music_box_display.MusicBoxTransportAction.resume =>
                        '继续播放',
                      music_box_display.MusicBoxTransportAction.play => '播放',
                    },
                    tone: ButtonTone.primary,
                    onPressed: canControl && hasPlayableSource
                        ? onTogglePlayback
                        : null,
                    size: 30,
                  ),
                  const SizedBox(width: 7),
                  ButtonIcon(
                    key: const ValueKey<String>('music-box-transport-next'),
                    icon: const Icon(Icons.skip_next),
                    tooltip: '下一首',
                    onPressed: canControl && hasQueue && state.playback.canNext
                        ? onSkip
                        : null,
                    size: 30,
                  ),
                  const SizedBox(width: 7),
                  Builder(
                    builder: (context) => ButtonIcon(
                      key: const ValueKey<String>('music-box-transport-mode'),
                      icon: Icon(_musicBoxModeIcon(state.playback.mode)),
                      tooltip:
                          '播放模式：${_musicBoxModeLabel(state.playback.mode)}',
                      selected:
                          state.playback.mode !=
                          MusicBoxPlaybackMode.sequential,
                      onPressed:
                          state.playback.capabilities.canChangeMode &&
                              onModeChanged != null
                          ? () => _showMusicBoxModeMenu(
                              context,
                              state.playback,
                              onModeChanged!,
                            )
                          : null,
                      size: 30,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }
}

IconData _musicBoxModeIcon(MusicBoxPlaybackMode mode) {
  return switch (mode) {
    MusicBoxPlaybackMode.sequential => Icons.format_list_numbered,
    MusicBoxPlaybackMode.repeatOne => Icons.repeat_one,
    MusicBoxPlaybackMode.repeatAll => Icons.repeat,
    MusicBoxPlaybackMode.shuffle => Icons.shuffle,
  };
}

String _musicBoxModeLabel(MusicBoxPlaybackMode mode) {
  return switch (mode) {
    MusicBoxPlaybackMode.sequential => '顺序播放',
    MusicBoxPlaybackMode.repeatOne => '单曲循环',
    MusicBoxPlaybackMode.repeatAll => '列表循环',
    MusicBoxPlaybackMode.shuffle => '随机播放',
  };
}

Future<void> _showMusicBoxModeMenu(
  BuildContext context,
  MusicBoxPlayback playback,
  ValueChanged<MusicBoxPlaybackMode> onChanged,
) {
  final box = context.findRenderObject() as RenderBox?;
  final position = box == null
      ? Offset.zero
      : box.localToGlobal(Offset(box.size.width, box.size.height));
  return showUiContextMenu(
    context,
    position: position,
    sections: [
      UiContextMenuSection([
        for (final mode in playback.capabilities.allowedModes)
          UiContextMenuItem(
            label: _musicBoxModeLabel(mode),
            icon: _musicBoxModeIcon(mode),
            selected: mode == playback.mode,
            onPressed: () => onChanged(mode),
          ),
      ]),
    ],
  );
}

/// A flat volume control shaped like a single icon button at rest that
/// elongates rightward on hover, the extra width revealing an inline [UiSlider]
/// within the same pill. Drives the local listening volume of the music box bot's
/// audio track via [onChanged] — purely a per-listener preference, independent
/// of the room's output volume. [initialVolume] seeds it from the restored
/// store; the widget then owns the value while mounted.
class _MusicBoxVolume extends StatefulWidget {
  const _MusicBoxVolume({required this.initialVolume, required this.onChanged});

  final double initialVolume;
  final ValueChanged<double> onChanged;

  @override
  State<_MusicBoxVolume> createState() => _MusicBoxVolumeState();
}

class _MusicBoxVolumeState extends State<_MusicBoxVolume> {
  // The collapsed square size / pill height.
  static const _size = 24.0;
  // The width the slider tail expands to on hover.
  static const _sliderExtent = 110.0;
  static const _gap = 10.0;
  static const _pad = 12.0;
  static const _duration = Duration(milliseconds: 180);

  late double _volume = widget.initialVolume;
  // The level to restore to when unmuting; captured at the moment of muting.
  late double _premute = widget.initialVolume > 0 ? widget.initialVolume : 0.7;
  bool _hovered = false;
  // True while a slider drag is in flight. The pointer gets captured by the
  // slider for the whole drag, so the cursor can wander off the pill (firing
  // MouseRegion.onExit) without meaning to collapse it — keep it open until the
  // drag ends, then fall back to the hover state.
  bool _dragging = false;

  // When true, the pill sits expanded at rest (full width, slider always
  // visible) instead of collapsing to a square that grows on hover. The
  // hover/drag expansion still works either way — flip this back to false to
  // restore the collapse-by-default behaviour.
  static const _expandedByDefault = true;

  bool get _expanded => _expandedByDefault || _hovered || _dragging;

  bool get _muted => _volume <= 0;

  @override
  void didUpdateWidget(_MusicBoxVolume oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.initialVolume == widget.initialVolume) return;
    final nextVolume = widget.initialVolume.clamp(0.0, 1.0).toDouble();
    _volume = nextVolume;
    if (nextVolume > 0) _premute = nextVolume;
  }

  void _setVolume(double value) {
    setState(() => _volume = value);
    widget.onChanged(value);
  }

  void _toggleMute() {
    if (_muted) {
      _setVolume(_premute > 0 ? _premute : 0.7);
    } else {
      _premute = _volume;
      _setVolume(0);
    }
  }

  IconData get _icon {
    return _volumeLevelIcon(_volume);
  }

  ({Color background, Color border, Color foreground}) get _palette {
    final active = !_muted;
    return (
      background: active ? UiColors.selected : UiColors.surface,
      border: active ? UiColors.selectedBorder : UiColors.border,
      foreground: active ? UiColors.accent : UiColors.text,
    );
  }

  Widget _iconButton(double size) {
    return _VolumeIconButton(
      icon: _icon,
      color: _palette.foreground,
      size: size,
      onTap: _toggleMute,
    );
  }

  Widget _slider() {
    return UiSlider(
      value: _volume,
      hoverLabel: audioVolumePercentText(_volume),
      onChangeStart: (_) => setState(() => _dragging = true),
      onChangeEnd: (_) => setState(() => _dragging = false),
      onChanged: _setVolume,
    );
  }

  // Elongates rightward in place; the icon stays put on the left. Collapsed it
  // is a single square; expanded it fills the full panel width so the slider
  // gets the whole row to travel across.
  @override
  Widget build(BuildContext context) {
    final p = _palette;
    final height = _size;

    // The width is tweened explicitly so hover expansion remains smooth even
    // though the control is a flat decorated surface rather than a button.
    // Muting remains the inner icon's gesture; the slider owns its own pointer
    // handling, so removing the button press layer does not change interaction.
    return LayoutBuilder(
      builder: (context, constraints) {
        // Expand to the full available row width; fall back to the legacy fixed
        // extent if the panel is laid out unbounded (shouldn't happen in the
        // music box column, but keeps the tween finite).
        final expandedWidth = constraints.maxWidth.isFinite
            ? constraints.maxWidth
            : height + _gap + _sliderExtent + _pad;
        return Align(
          alignment: Alignment.centerLeft,
          child: MouseRegion(
            onEnter: (_) => setState(() => _hovered = true),
            onExit: (_) => setState(() => _hovered = false),
            child: TweenAnimationBuilder<double>(
              duration: _duration,
              curve: Curves.easeOutCubic,
              tween: Tween(end: _expanded ? expandedWidth : height),
              builder: (context, width, child) {
                return SizedBox(
                  key: const ValueKey<String>('music-box-volume-control'),
                  height: height,
                  width: width,
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      color: p.background,
                      borderRadius: BorderRadius.circular(UiRadii.md),
                      border: Border.all(color: p.border),
                    ),
                    child: child!,
                  ),
                );
              },
              child: Row(
                children: [
                  _iconButton(height),
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.only(right: _pad),
                      child: _slider(),
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

/// A bare, borderless tap target for the mute icon inside the volume pill.
class _VolumeIconButton extends StatelessWidget {
  const _VolumeIconButton({
    required this.icon,
    required this.color,
    required this.size,
    required this.onTap,
  });

  final IconData icon;
  final Color color;
  final double size;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: SizedBox(
          width: size,
          height: size,
          child: Icon(icon, color: color, size: size * 0.46),
        ),
      ),
    );
  }
}

/// A spinning black vinyl record. Rotates continuously while [spinning], and
/// freezes its current angle when paused/stopped — an at-a-glance read of
/// whether the music box is playing.
class _VinylRecord extends StatefulWidget {
  const _VinylRecord({required this.spinning, this.label, this.size = 40});

  final bool spinning;
  final String? label;
  final double size;

  @override
  State<_VinylRecord> createState() => _VinylRecordState();
}

class _VinylRecordState extends State<_VinylRecord>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(seconds: 6),
  );

  @override
  void initState() {
    super.initState();
    if (widget.spinning) _controller.repeat();
  }

  @override
  void didUpdateWidget(_VinylRecord oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.spinning && !_controller.isAnimating) {
      _controller.repeat();
    } else if (!widget.spinning && _controller.isAnimating) {
      // Freeze at the current angle rather than snapping back to zero.
      _controller.stop();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return RotationTransition(
      turns: _controller,
      child: CustomPaint(
        size: Size.square(widget.size),
        painter: _VinylPainter(),
      ),
    );
  }
}

class _VinylPainter extends CustomPainter {
  const _VinylPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.width / 2;

    // Disc body.
    canvas.drawCircle(center, radius, Paint()..color = const Color(0xFF12151A));
    canvas.drawCircle(
      center,
      radius,
      Paint()
        ..color = const Color(0xFF2A2F38)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1,
    );

    // Grooves.
    final groovePaint = Paint()
      ..color = const Color(0xFF20242C)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1;
    for (var r = radius - 6; r > radius * 0.34; r -= 5) {
      canvas.drawCircle(center, r, groovePaint);
    }

    // Accent label and spindle hole.
    canvas.drawCircle(center, radius * 0.30, Paint()..color = UiColors.accent);
    canvas.drawCircle(
      center,
      radius * 0.30,
      Paint()
        ..color = const Color(0xFF14171D)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1,
    );
    canvas.drawCircle(
      center,
      radius * 0.07,
      Paint()..color = const Color(0xFF14171D),
    );
  }

  @override
  bool shouldRepaint(_VinylPainter oldDelegate) => false;
}

/// Server-authoritative progress bar. The server pushes a fresh snapshot every
/// second, so the bar simply renders the snapshot's reported position — no local
/// stepping, no client clock, nothing to drift.
class _MusicBoxProgressBar extends StatelessWidget {
  const _MusicBoxProgressBar({required this.state});

  final MusicBoxState state;

  @override
  Widget build(BuildContext context) {
    final progress = music_box_display.musicBoxProgress(state);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(3),
          child: LinearProgressIndicator(
            value: progress.fraction,
            minHeight: 4,
            backgroundColor: UiColors.surfaceRaised,
            valueColor: const AlwaysStoppedAnimation(UiColors.accent),
          ),
        ),
        const SizedBox(height: 4),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              music_box_display.musicBoxFormatDuration(progress.positionMs),
              style: const TextStyle(
                color: UiColors.textMuted,
                fontSize: 10,
                fontWeight: FontWeight.w600,
              ),
            ),
            Text(
              music_box_display.musicBoxFormatDuration(progress.durationMs),
              style: const TextStyle(
                color: UiColors.textMuted,
                fontSize: 10,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

/// Lower body that defaults to the authoritative active queue. A single plus
/// toggle reveals search and saved-playlist sources while keeping the viewed
/// source independent from the authoritative source that is actually playing.
class _MusicBoxBody extends StatefulWidget {
  const _MusicBoxBody({
    super.key,
    required this.state,
    required this.searchController,
    required this.searchResults,
    required this.searching,
    required this.searchError,
    required this.source,
    required this.onQueueResult,
    required this.onRemoveItem,
    required this.onSourceChanged,
    required this.controller,
    required this.roomId,
    required this.room,
    required this.previewPlatformFactory,
    required this.onStateChanged,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
    required this.onCreateFirstRoomPlaylist,
    required this.onCreateFirstPersonalPlaylist,
    required this.onEditRoomPlaylist,
    required this.onEditPersonalPlaylist,
  });

  final MusicBoxState state;
  final TextEditingController searchController;
  final List<MusicBoxSearchResult> searchResults;
  final bool searching;
  final String? searchError;
  final String source;
  final ValueChanged<MusicBoxSearchResult> onQueueResult;
  final ValueChanged<MusicBoxQueueItem> onRemoveItem;
  final ValueChanged<String> onSourceChanged;
  final MusicBoxController? controller;
  final String? roomId;
  final PublicRoom? room;
  final MusicTrackPreviewPlatformFactory? previewPlatformFactory;
  final ValueChanged<MusicBoxState>? onStateChanged;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;
  final VoidCallback? onCreateFirstRoomPlaylist;
  final VoidCallback? onCreateFirstPersonalPlaylist;
  final MusicPlaylistEditCallback? onEditRoomPlaylist;
  final MusicPlaylistEditCallback? onEditPersonalPlaylist;

  @override
  State<_MusicBoxBody> createState() => _MusicBoxBodyState();
}

class _MusicBoxBodyState extends State<_MusicBoxBody> {
  _MusicBoxSection _section = _MusicBoxSection.queue;
  _MusicBoxBrowseSource? _viewedSourceOverride;
  late final TextEditingController _sourceSearchController;
  late final FocusNode _sourceSearchFocusNode;
  final Object _sourceBrowserTapRegionGroup = Object();
  bool _startingViewedSource = false;

  @override
  void initState() {
    super.initState();
    _sourceSearchController = TextEditingController()
      ..addListener(_handleSourceSearchChanged);
    _sourceSearchFocusNode = FocusNode();
  }

  @override
  void dispose() {
    _sourceSearchController
      ..removeListener(_handleSourceSearchChanged)
      ..dispose();
    _sourceSearchFocusNode.dispose();
    super.dispose();
  }

  void _handleSourceSearchChanged() {
    if (mounted) setState(() {});
  }

  @override
  void didUpdateWidget(covariant _MusicBoxBody oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller ||
        oldWidget.roomId != widget.roomId) {
      _viewedSourceOverride = null;
      _sourceSearchController.clear();
    }
  }

  _MusicBoxBrowseSource get _viewedSource {
    final active = _musicBoxActiveBrowseSource(widget.state, widget.roomId);
    final override = _viewedSourceOverride;
    if (override == null) return active;
    if (override.type == MusicBoxActiveSourceType.temporary) {
      return override.copyWith(
        itemCount: widget.state.temporaryQueue.length,
        current:
            widget.state.activeSource.type ==
            MusicBoxActiveSourceType.temporary,
        queueItems: widget.state.temporaryQueue,
      );
    }
    if (_musicBoxBrowseSourceMatchesActive(override, widget.state)) {
      return active;
    }
    return override.copyWith(current: false);
  }

  bool get _viewedSourceIsActive =>
      _musicBoxBrowseSourceMatchesActive(_viewedSource, widget.state);

  Future<void>? startViewedSourceFromBeginning() {
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || roomId == null) return null;
    if (_startingViewedSource) return Future<void>.value();
    final source = _viewedSource;
    if (source.itemCount <= 0) {
      showFloatingErrorNotice(context, '当前所视歌单没有歌曲');
      return Future<void>.value();
    }
    _startingViewedSource = true;
    return () async {
      try {
        final state = await controller.activatePlaylist(
          roomId: roomId,
          sourceType: source.type,
          playlistId: source.type == MusicBoxActiveSourceType.temporary
              ? null
              : source.id,
          startItemId: source.startItemIdFromBeginning,
        );
        widget.onStateChanged?.call(state);
      } catch (error) {
        if (mounted) {
          showFloatingErrorNotice(
            context,
            musicBoxControlErrorMessage(error, '播放歌单失败，请重试'),
          );
        }
      } finally {
        _startingViewedSource = false;
      }
    }();
  }

  Future<void> _confirmAndClearTemporaryQueue() async {
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null ||
        roomId == null ||
        !widget.state.canClearTemporary ||
        widget.state.temporaryQueue.isEmpty) {
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => DialogFrame(
        title: '清空点歌队列',
        icon: Icons.delete_sweep_outlined,
        adaptiveActions: [
          ResponsiveDialogAction(
            label: '取消',
            onPressed: () => Navigator.of(dialogContext).pop(false),
          ),
          ResponsiveDialogAction(
            buttonKey: const ValueKey<String>(
              'music-box-confirm-clear-temporary-queue',
            ),
            label: '清空队列',
            icon: Icons.delete_sweep_outlined,
            tone: ButtonTone.danger,
            onPressed: () => Navigator.of(dialogContext).pop(true),
          ),
        ],
        child: const Text(
          '确认清空当前点歌队列？此操作不会删除已保存的歌单。',
          style: UiTypography.body,
        ),
      ),
    );
    if (confirmed != true || !mounted) return;
    try {
      final state = await controller.clearTemporaryQueue(
        roomId: roomId,
        currentState: widget.state,
      );
      widget.onStateChanged?.call(state);
      if (mounted) showFloatingSuccessNotice(context, '已清空点歌队列');
    } catch (error) {
      if (mounted) {
        showFloatingErrorNotice(
          context,
          musicBoxControlErrorMessage(error, '清空点歌队列失败，请重试'),
        );
      }
    }
  }

  void _toggleSourceBrowser() {
    final opening = _section != _MusicBoxSection.sources;
    if (!opening) {
      _collapseSourceBrowser();
      return;
    }
    setState(() {
      _section = _MusicBoxSection.sources;
    });
  }

  void _collapseSourceBrowser() {
    if (_section != _MusicBoxSection.sources) return;
    _sourceSearchController.clear();
    _sourceSearchFocusNode.unfocus();
    setState(() => _section = _MusicBoxSection.queue);
  }

  Widget _searchInput({
    Key? key,
    required TextEditingController controller,
    required String hintText,
    FocusNode? focusNode,
    TapRegionCallback? onTapOutside,
    Object? tapRegionGroupId,
  }) {
    return Input(
      key: key,
      controller: controller,
      focusNode: focusNode,
      hintText: hintText,
      prefixIcon: Icons.search,
      showClearButton: true,
      maxLines: 1,
      height: _musicBoxSearchFieldHeight,
      onTapOutside: onTapOutside,
      tapRegionGroupId: tapRegionGroupId,
    );
  }

  void _selectViewedSource(_MusicBoxBrowseSource source) {
    _sourceSearchController.clear();
    _sourceSearchFocusNode.unfocus();
    setState(() {
      _viewedSourceOverride = source;
      _section = _MusicBoxSection.queue;
    });
  }

  void _setSearchVisible(bool value) {
    if (!value) FocusManager.instance.primaryFocus?.unfocus();
    _sourceSearchController.clear();
    _sourceSearchFocusNode.unfocus();
    setState(() {
      _section = value ? _MusicBoxSection.search : _MusicBoxSection.queue;
    });
  }

  void _handleActivatedState(MusicBoxState state) {
    widget.onStateChanged?.call(state);
    if (mounted) {
      _sourceSearchController.clear();
      _sourceSearchFocusNode.unfocus();
      setState(() {
        _section = _MusicBoxSection.queue;
      });
    }
  }

  Future<void> _viewPlaylistWindow(
    PersonalMusicPlaylist playlist,
    bool roomScoped,
  ) async {
    FocusManager.instance.primaryFocus?.unfocus();
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || (roomScoped && roomId == null) || !mounted) {
      return;
    }

    final active = widget.state.activeSource;
    final activeType = roomScoped
        ? MusicBoxActiveSourceType.roomPlaylist
        : MusicBoxActiveSourceType.userPlaylist;
    if (roomId != null &&
        active.type == activeType &&
        active.id == playlist.id) {
      await showDialog<void>(
        context: context,
        builder: (_) => _ActiveMusicPlaylistDialog(
          source: active,
          items: widget.state.queue,
          controller: controller,
          roomId: roomId,
          previewPlatformFactory: widget.previewPlatformFactory,
        ),
      );
      return;
    }

    PersonalMusicPlaylistItemsPage page;
    try {
      page = roomScoped
          ? await controller.loadRoomPlaylist(
              roomId: roomId!,
              playlistId: playlist.id,
            )
          : await controller.loadMyPlaylist(playlistId: playlist.id);
    } catch (error) {
      if (mounted) {
        showFloatingErrorNotice(
          context,
          musicBoxControlErrorMessage(error, '加载歌单失败，请重试'),
        );
      }
      return;
    }
    if (!mounted) return;
    final tracks = [
      for (final item in page.items)
        MusicTrackCardData(
          id: item.id,
          source: item.source,
          trackId: item.trackId,
          title: item.title,
          artists: item.artists,
          durationMs: item.durationMs,
        ),
    ];
    await showDialog<void>(
      context: context,
      builder: (_) => MusicPlaylistSnapshotDialog(
        title: page.playlist.name,
        tracks: tracks,
        previewApi: controller.api is MusicTrackPreviewApi
            ? controller.api as MusicTrackPreviewApi
            : null,
        previewPlatformFactory: widget.previewPlatformFactory,
        loadPersonalPlaylists: controller.loadMyPlaylists,
        onAddToPlaylist: (track, target) {
          return controller.addSearchResultToMyPlaylist(
            playlistId: target.id,
            result: MusicBoxSearchResult(
              trackId: track.trackId,
              name: track.title,
              artists: track.artists,
              source: track.source,
            ),
            durationMs: track.durationMs > 0 ? track.durationMs : null,
          );
        },
        contentKey: ValueKey<String>(
          'music-playlist-window:${page.playlist.id}',
        ),
        trackListKey: ValueKey<String>(
          'music-playlist-window-tracks:${page.playlist.id}',
        ),
        doneButtonKey: ValueKey<String>(
          'music-playlist-window-done:${page.playlist.id}',
        ),
      ),
    );
  }

  Widget _currentQueueView() {
    return _MusicBoxQueueList(
      state: widget.state,
      queue: widget.state.queue,
      onRemoveItem: widget.onRemoveItem,
      controller: widget.controller,
      roomId: widget.roomId,
      onStateChanged: widget.onStateChanged,
      currentUser: widget.currentUser,
      onResolveUserProfile: widget.onResolveUserProfile,
      onResolveRoomProfile: widget.onResolveRoomProfile,
      onEnterCommonRoom: widget.onEnterCommonRoom,
      userProfileActionBuilder: widget.userProfileActionBuilder,
    );
  }

  Widget _currentSourceLeading(
    BuildContext context,
    _MusicBoxBrowseSource source,
  ) {
    final icon = Icon(
      _musicBoxBrowseSourceIcon(source),
      key: const ValueKey<String>('music-box-current-source-leading-icon'),
      size: 15,
      color: UiColors.accent,
    );
    final iconHitArea = SizedBox.square(
      dimension: 22,
      child: Center(child: icon),
    );
    final active = widget.state.activeSource;
    final sourceIsActive = _musicBoxBrowseSourceMatchesActive(
      source,
      widget.state,
    );
    final playing = sourceIsActive && widget.state.currentItem != null;
    if (source.type == MusicBoxActiveSourceType.temporary) {
      return MusicPlaylistHoverCard(
        key: const ValueKey<String>(
          'music-box-current-source-playlist-card-anchor',
        ),
        data: MusicPlaylistCardData(
          id: 'temporary:${source.id}',
          name: source.name,
          songCount: source.itemCount,
          createdAt: null,
          room: widget.room,
          showPlayingStatus: playing,
          showCreatedAt: false,
        ),
        currentUser: widget.currentUser,
        onResolveUserProfile: widget.onResolveUserProfile,
        onResolveRoomProfile: widget.onResolveRoomProfile,
        onEnterCommonRoom: widget.onEnterCommonRoom,
        userProfileActionBuilder: widget.userProfileActionBuilder,
        onPlayAll: playing || source.itemCount <= 0
            ? null
            : () async {
                final start = startViewedSourceFromBeginning();
                if (start != null) await start;
              },
        onViewPlaylist: source.itemCount <= 0 || !widget.state.canClearTemporary
            ? null
            : _confirmAndClearTemporaryQueue,
        secondaryActionLabel: '清空队列',
        secondaryActionIcon: Icons.delete_sweep_outlined,
        secondaryActionTone: ButtonTone.danger,
        child: iconHitArea,
      );
    }

    if (source.id.isEmpty) return iconHitArea;

    final playlist = source.playlist;
    final creator = source.type == MusicBoxActiveSourceType.userPlaylist
        ? sourceIsActive
              ? _musicBoxActiveSourceOwner(active, widget.currentUser)
              : widget.currentUser?.toSummary()
        : null;
    final ownerId = sourceIsActive
        ? active.ownerUserId.isNotEmpty
              ? active.ownerUserId
              : active.owner?.userId ?? ''
        : widget.currentUser?.id ?? '';
    final ownedPersonalPlaylist =
        source.type == MusicBoxActiveSourceType.userPlaylist &&
        widget.currentUser != null &&
        (ownerId.isEmpty || ownerId == widget.currentUser!.id);
    final editPlaylist = source.roomScoped
        ? widget.onEditRoomPlaylist
        : ownedPersonalPlaylist
        ? widget.onEditPersonalPlaylist
        : null;
    final playlistValue =
        playlist ??
        PersonalMusicPlaylist(
          id: source.id,
          name: source.name,
          description: '',
          revision: 0,
          itemCount: source.itemCount,
          createdAt: sourceIsActive ? active.createdAt : null,
          updatedAt: null,
        );
    return MusicPlaylistHoverCard(
      key: const ValueKey<String>(
        'music-box-current-source-playlist-card-anchor',
      ),
      data: MusicPlaylistCardData(
        id: source.id,
        name: source.name,
        songCount: source.itemCount,
        createdAt:
            playlist?.createdAt ?? (sourceIsActive ? active.createdAt : null),
        creator: creator,
        room: source.roomScoped ? widget.room : null,
        showPlayingStatus: playing,
      ),
      currentUser: widget.currentUser,
      onResolveUserProfile: widget.onResolveUserProfile,
      onResolveRoomProfile: widget.onResolveRoomProfile,
      onEnterCommonRoom: widget.onEnterCommonRoom,
      userProfileActionBuilder: widget.userProfileActionBuilder,
      onPlayAll: playing
          ? null
          : () async {
              final start = startViewedSourceFromBeginning();
              if (start != null) await start;
            },
      onViewPlaylist: editPlaylist == null
          ? () => _viewPlaylistWindow(playlistValue, source.roomScoped)
          : () => editPlaylist(source.id),
      secondaryActionLabel: editPlaylist == null ? '查看歌单' : '编辑歌单',
      secondaryActionIcon: editPlaylist == null
          ? Icons.queue_music
          : Icons.edit_outlined,
      child: iconHitArea,
    );
  }

  @override
  Widget build(BuildContext context) {
    final hasQuery = widget.searchController.text.trim().isNotEmpty;
    final searchVisible = _section == _MusicBoxSection.search;
    final sourceBrowserVisible = _section == _MusicBoxSection.sources;
    final viewedSource = _viewedSource;
    return MusicPlaylistCardHostScope(
      currentUser: widget.currentUser,
      room: widget.room,
      onResolveUserProfile: widget.onResolveUserProfile,
      onResolveRoomProfile: widget.onResolveRoomProfile,
      onEnterCommonRoom: widget.onEnterCommonRoom,
      userProfileActionBuilder: widget.userProfileActionBuilder,
      onStateChanged: widget.onStateChanged,
      onViewPlaylist: _viewPlaylistWindow,
      onEditRoomPlaylist: widget.onEditRoomPlaylist,
      onEditPersonalPlaylist: widget.onEditPersonalPlaylist,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: searchVisible
                    ? _searchInput(
                        controller: widget.searchController,
                        hintText: '搜索歌曲点歌',
                      )
                    : sourceBrowserVisible
                    ? TapRegion(
                        groupId: _sourceBrowserTapRegionGroup,
                        onTapOutside: (_) => _collapseSourceBrowser(),
                        child: _searchInput(
                          key: const ValueKey<String>(
                            'music-box-source-search-input',
                          ),
                          controller: _sourceSearchController,
                          focusNode: _sourceSearchFocusNode,
                          hintText: '搜索歌单',
                          tapRegionGroupId: _sourceBrowserTapRegionGroup,
                        ),
                      )
                    : _MusicBoxCurrentSourceHeader(
                        source: viewedSource,
                        expanded: sourceBrowserVisible,
                        leading: _currentSourceLeading(context, viewedSource),
                        onPressed: _toggleSourceBrowser,
                      ),
              ),
              const SizedBox(width: 8),
              if (sourceBrowserVisible)
                ButtonIconPlain(
                  key: const ValueKey<String>(
                    'music-box-source-browser-collapse',
                  ),
                  icon: const Icon(Icons.expand_less),
                  tooltip: '收起歌单',
                  width: _musicBoxSearchFieldHeight,
                  height: _musicBoxSearchFieldHeight,
                  iconSize: 17,
                  onPressed: _collapseSourceBrowser,
                )
              else
                ButtonIcon(
                  key: const ValueKey<String>('music-box-search-toggle'),
                  icon: Icon(searchVisible ? Icons.close : Icons.search),
                  tooltip: searchVisible ? '关闭搜索' : '搜索添加',
                  tone: searchVisible ? ButtonTone.danger : ButtonTone.neutral,
                  toggleValue: searchVisible,
                  onToggleChanged: _setSearchVisible,
                  size: _musicBoxSearchFieldHeight,
                ),
            ],
          ),
          if (searchVisible) ...[
            const SizedBox(height: 10),
            SegmentedControl<String>(
              expanded: true,
              height: _musicBoxSearchFieldHeight,
              value: music_box_display.normalizedMusicBoxSource(widget.source),
              segments: [
                for (final source in music_box_display.musicBoxSources)
                  Segment(value: source.id, label: source.label),
              ],
              onChanged: widget.onSourceChanged,
            ),
          ],
          const SizedBox(height: 12),
          Expanded(
            key: const ValueKey<String>('music-box-results-viewport'),
            child: switch (_section) {
              _MusicBoxSection.queue =>
                _viewedSourceIsActive
                    ? _currentQueueView()
                    : _MusicBoxSourceBrowser(
                        controller: widget.controller,
                        roomId: widget.roomId,
                        state: widget.state,
                        showSourceList: false,
                        viewedSource: viewedSource,
                        onViewedSourceChanged: _selectViewedSource,
                        onStateChanged: _handleActivatedState,
                        onQueueResult: widget.onQueueResult,
                        onCreateFirstRoomPlaylist:
                            widget.onCreateFirstRoomPlaylist,
                        onCreateFirstPersonalPlaylist:
                            widget.onCreateFirstPersonalPlaylist,
                      ),
              _MusicBoxSection.sources => _MusicBoxSourceBrowser(
                controller: widget.controller,
                roomId: widget.roomId,
                state: widget.state,
                showSourceList: true,
                viewedSource: viewedSource,
                onViewedSourceChanged: _selectViewedSource,
                onStateChanged: _handleActivatedState,
                onQueueResult: widget.onQueueResult,
                onCreateFirstRoomPlaylist: widget.onCreateFirstRoomPlaylist,
                onCreateFirstPersonalPlaylist:
                    widget.onCreateFirstPersonalPlaylist,
                sourceQuery: _sourceSearchController.text,
                tapRegionGroupId: _sourceBrowserTapRegionGroup,
              ),
              _MusicBoxSection.roomPlaylists => _MusicBoxPlaylistBrowser(
                controller: widget.controller,
                roomId: widget.roomId,
                roomScoped: true,
                temporaryQueue: widget.state.temporaryQueue,
                onQueueResult: widget.onQueueResult,
                onStateChanged: _handleActivatedState,
                onCreateFirstPlaylist: widget.onCreateFirstRoomPlaylist,
              ),
              _MusicBoxSection.myPlaylists => _MusicBoxPlaylistBrowser(
                controller: widget.controller,
                roomId: widget.roomId,
                roomScoped: false,
                temporaryQueue: widget.state.temporaryQueue,
                onQueueResult: widget.onQueueResult,
                onStateChanged: _handleActivatedState,
                onCreateFirstPlaylist: widget.onCreateFirstPersonalPlaylist,
              ),
              _MusicBoxSection.search => _MusicBoxSearchList(
                results: widget.searchResults,
                query: widget.searchController.text,
                searching: widget.searching,
                error: widget.searchError,
                hasQuery: hasQuery,
                controller: widget.controller,
                roomId: widget.roomId,
                temporaryQueue: widget.state.temporaryQueue,
                onQueueResult: widget.onQueueResult,
              ),
            },
          ),
        ],
      ),
    );
  }
}

class _ActiveMusicPlaylistDialog extends StatefulWidget {
  const _ActiveMusicPlaylistDialog({
    required this.source,
    required this.items,
    required this.controller,
    required this.roomId,
    required this.previewPlatformFactory,
  });

  final MusicBoxActiveSource source;
  final List<MusicBoxQueueItem> items;
  final MusicBoxController controller;
  final String roomId;
  final MusicTrackPreviewPlatformFactory? previewPlatformFactory;

  @override
  State<_ActiveMusicPlaylistDialog> createState() =>
      _ActiveMusicPlaylistDialogState();
}

class _ActiveMusicPlaylistDialogState
    extends State<_ActiveMusicPlaylistDialog> {
  MusicTrackPreviewController? _previewController;
  List<PersonalMusicPlaylist> _myPlaylists = const [];
  int _maxPlaylists = 50;
  bool _loadingPlaylists = true;
  bool _cloning = false;
  PersonalMusicPlaylist? _clonedPlaylist;

  bool get _playlistLimitReached =>
      !_loadingPlaylists && _myPlaylists.length >= _maxPlaylists;

  @override
  void initState() {
    super.initState();
    final api = widget.controller.api;
    final factory = widget.previewPlatformFactory;
    if (api is MusicTrackPreviewApi && factory != null) {
      _previewController = MusicTrackPreviewController(
        api: api as MusicTrackPreviewApi,
        platform: factory.create(),
      );
    }
    unawaited(_loadMyPlaylists());
  }

  @override
  void dispose() {
    final preview = _previewController;
    if (preview != null) unawaited(preview.dispose());
    super.dispose();
  }

  Future<void> _loadMyPlaylists() async {
    try {
      final page = await widget.controller.loadMyPlaylists();
      if (!mounted) return;
      setState(() {
        _myPlaylists = page.playlists;
        _maxPlaylists = page.maxPlaylists;
        _loadingPlaylists = false;
      });
    } catch (_) {
      if (mounted) setState(() => _loadingPlaylists = false);
    }
  }

  Future<void> _clone() async {
    if (_cloning ||
        _loadingPlaylists ||
        _clonedPlaylist != null ||
        _playlistLimitReached ||
        widget.source.snapshotId.isEmpty ||
        widget.controller.api is! MusicBoxActivePlaylistCloneApi) {
      return;
    }
    setState(() => _cloning = true);
    try {
      final playlist = await widget.controller.cloneActivePlaylist(
        roomId: widget.roomId,
        source: widget.source,
      );
      if (!mounted) return;
      setState(() {
        _clonedPlaylist = playlist;
        _myPlaylists = [..._myPlaylists, playlist];
      });
      showFloatingSuccessNotice(context, '已克隆为「${playlist.name}」');
    } catch (error) {
      if (mounted) {
        showFloatingErrorNotice(
          context,
          musicBoxControlErrorMessage(error, '克隆歌单失败，请重试'),
        );
      }
    } finally {
      if (mounted) setState(() => _cloning = false);
    }
  }

  Future<void> _addToPlaylist(
    MusicBoxQueueItem item,
    PersonalMusicPlaylist playlist,
  ) {
    return widget.controller.addQueueItemToMyPlaylist(
      playlistId: playlist.id,
      item: item,
    );
  }

  @override
  Widget build(BuildContext context) {
    final canClone =
        widget.controller.api is MusicBoxActivePlaylistCloneApi &&
        widget.source.snapshotId.isNotEmpty &&
        !_loadingPlaylists &&
        !_playlistLimitReached &&
        _clonedPlaylist == null;
    final cloneLabel = _clonedPlaylist != null
        ? '已克隆到我的歌单'
        : _loadingPlaylists
        ? '正在检查我的歌单'
        : _playlistLimitReached
        ? '我的歌单已满'
        : '克隆到我的歌单';
    final height = (MediaQuery.sizeOf(context).height * 0.68).clamp(
      260.0,
      620.0,
    );
    return DialogFrame(
      title: widget.source.name,
      icon: Icons.queue_music,
      maxWidth: 760,
      adaptiveActions: [
        ResponsiveDialogAction(
          buttonKey: const ValueKey<String>('active-music-playlist-clone'),
          label: cloneLabel,
          icon: Icons.library_add,
          tone: ButtonTone.primary,
          loading: _cloning,
          onPressed: canClone ? () => unawaited(_clone()) : null,
        ),
        ResponsiveDialogAction(
          buttonKey: const ValueKey<String>('active-music-playlist-done'),
          label: '完成',
          icon: Icons.check,
          onPressed: () => Navigator.of(context).pop(),
        ),
      ],
      child: SizedBox(
        key: const ValueKey<String>('active-music-playlist-dialog'),
        height: height,
        child: widget.items.isEmpty
            ? const _MusicBoxEmpty(icon: Icons.music_off, message: '这个歌单还没有歌曲')
            : ListView.separated(
                key: const ValueKey<String>('active-music-playlist-tracks'),
                padding: EdgeInsets.zero,
                itemCount: widget.items.length,
                separatorBuilder: (_, _) => const SizedBox(height: 10),
                itemBuilder: (context, index) {
                  final item = widget.items[index];
                  final artists = item.artist
                      .split(RegExp(r'[、,，]'))
                      .map((value) => value.trim())
                      .where((value) => value.isNotEmpty)
                      .toList(growable: false);
                  return MusicPlaylistSnapshotTrackTile(
                    track: MusicTrackCardData(
                      id: item.id,
                      source: item.source,
                      trackId: item.trackId,
                      title: item.title,
                      artists: artists,
                      durationMs: item.durationMs,
                    ),
                    previewController: _previewController,
                    playlists: _myPlaylists,
                    onAddToPlaylist: (playlist) =>
                        _addToPlaylist(item, playlist),
                    surfaceKey: ValueKey<String>(
                      'active-music-playlist-track:${item.id}',
                    ),
                  );
                },
              ),
      ),
    );
  }
}

class _MusicBoxCurrentSourceHeader extends StatelessWidget {
  const _MusicBoxCurrentSourceHeader({
    required this.source,
    required this.expanded,
    required this.leading,
    required this.onPressed,
  });

  final _MusicBoxBrowseSource source;
  final bool expanded;
  final Widget leading;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return PressableSurface(
      key: const ValueKey<String>('music-box-current-queue-header'),
      height: _musicBoxSearchFieldHeight,
      padding: const EdgeInsets.symmetric(horizontal: 10),
      backgroundColor: UiColors.surfaceLow,
      borderColor: UiColors.border,
      hoverEffect: false,
      pressEffect: false,
      selected: expanded,
      child: Row(
        children: [
          MouseRegion(cursor: SystemMouseCursors.click, child: leading),
          Expanded(
            child: Tooltip(
              message: expanded ? '收起歌单' : '切换歌单',
              child: MouseRegion(
                cursor: SystemMouseCursors.click,
                child: GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTap: onPressed,
                  child: Row(
                    children: [
                      const SizedBox(width: 7),
                      Expanded(
                        child: Text(
                          source.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: UiColors.text,
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                      Text(
                        '${source.itemCount}',
                        style: const TextStyle(
                          color: UiColors.textMuted,
                          fontSize: 10,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(width: 6),
                      Icon(
                        expanded ? Icons.expand_less : Icons.expand_more,
                        size: 16,
                        color: UiColors.textMuted,
                      ),
                    ],
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

enum _MusicBoxSection { queue, sources, search, roomPlaylists, myPlaylists }

UserSummary? _musicBoxActiveSourceOwner(
  MusicBoxActiveSource source,
  CurrentUser? currentUser,
) {
  final owner = source.owner;
  if (owner != null) {
    return UserSummary(
      id: owner.userId,
      username: owner.username,
      displayName: owner.avatarLabel,
      roomDisplayName: owner.displayName,
      avatarUrl: owner.avatarUrl,
      defaultAvatarKey: owner.defaultAvatarKey,
    );
  }
  if (currentUser != null &&
      (source.ownerUserId.isEmpty || source.ownerUserId == currentUser.id)) {
    return currentUser.toSummary();
  }
  return null;
}

PersonalMusicPlaylist? _musicBoxPlaylistById(
  List<PersonalMusicPlaylist> playlists,
  String id,
) {
  for (final playlist in playlists) {
    if (playlist.id == id) return playlist;
  }
  return null;
}

class _MusicBoxBrowseSource {
  const _MusicBoxBrowseSource({
    required this.type,
    required this.id,
    required this.name,
    required this.itemCount,
    required this.current,
    required this.roomScoped,
    this.playlist,
    this.queueItems,
  });

  final MusicBoxActiveSourceType type;
  final String id;
  final String name;
  final int itemCount;
  final bool current;
  final bool roomScoped;
  final PersonalMusicPlaylist? playlist;
  final List<MusicBoxQueueItem>? queueItems;

  String get key => '${musicBoxActiveSourceTypeValue(type)}:$id';

  /// Saved-playlist snapshots expose generated queue item IDs, but the
  /// activation endpoint accepts original playlist item IDs. Starting a saved
  /// playlist from its beginning must therefore omit the item ID and let the
  /// server choose index zero. Temporary queues use their real queue item ID.
  String? get startItemIdFromBeginning {
    final items = queueItems;
    if (type != MusicBoxActiveSourceType.temporary ||
        items == null ||
        items.isEmpty) {
      return null;
    }
    return items.first.id;
  }

  _MusicBoxBrowseSource copyWith({
    String? name,
    int? itemCount,
    bool? current,
    List<MusicBoxQueueItem>? queueItems,
  }) {
    return _MusicBoxBrowseSource(
      type: type,
      id: id,
      name: name ?? this.name,
      itemCount: itemCount ?? this.itemCount,
      current: current ?? this.current,
      roomScoped: roomScoped,
      playlist: playlist,
      queueItems: queueItems ?? this.queueItems,
    );
  }
}

_MusicBoxBrowseSource _musicBoxActiveBrowseSource(
  MusicBoxState state,
  String? roomId,
) {
  final active = state.activeSource;
  if (active.type == MusicBoxActiveSourceType.temporary) {
    return _MusicBoxBrowseSource(
      type: MusicBoxActiveSourceType.temporary,
      id: roomId ?? '',
      name: '点歌队列',
      itemCount: state.temporaryQueue.length,
      current: true,
      roomScoped: true,
      queueItems: state.temporaryQueue,
    );
  }
  return _MusicBoxBrowseSource(
    type: active.type,
    id: active.id,
    name: music_box_display.musicBoxActiveSourceLabel(active),
    itemCount: state.queue.length,
    current: true,
    roomScoped: active.type == MusicBoxActiveSourceType.roomPlaylist,
    queueItems: state.queue,
  );
}

bool _musicBoxBrowseSourceMatchesActive(
  _MusicBoxBrowseSource source,
  MusicBoxState state,
) {
  final active = state.activeSource;
  if (source.type == MusicBoxActiveSourceType.temporary) {
    return active.type == MusicBoxActiveSourceType.temporary;
  }
  return active.type == source.type && active.id == source.id;
}

IconData _musicBoxBrowseSourceIcon(_MusicBoxBrowseSource source) {
  return switch (source.type) {
    MusicBoxActiveSourceType.temporary => Icons.queue_music,
    MusicBoxActiveSourceType.roomPlaylist => Icons.meeting_room,
    MusicBoxActiveSourceType.userPlaylist => Icons.person,
  };
}

/// Unified, browse-first source picker shown by the music-box source header.
/// Selecting a source collapses the picker and replaces the current content
/// with its tracks; playback changes exclusively through a per-track action.
class _MusicBoxSourceBrowser extends StatefulWidget {
  const _MusicBoxSourceBrowser({
    required this.controller,
    required this.roomId,
    required this.state,
    required this.showSourceList,
    required this.viewedSource,
    required this.onViewedSourceChanged,
    required this.onStateChanged,
    required this.onQueueResult,
    required this.onCreateFirstRoomPlaylist,
    required this.onCreateFirstPersonalPlaylist,
    this.sourceQuery = '',
    this.tapRegionGroupId,
  });

  final MusicBoxController? controller;
  final String? roomId;
  final MusicBoxState state;
  final bool showSourceList;
  final _MusicBoxBrowseSource viewedSource;
  final ValueChanged<_MusicBoxBrowseSource> onViewedSourceChanged;
  final ValueChanged<MusicBoxState> onStateChanged;
  final ValueChanged<MusicBoxSearchResult> onQueueResult;
  final VoidCallback? onCreateFirstRoomPlaylist;
  final VoidCallback? onCreateFirstPersonalPlaylist;
  final String sourceQuery;
  final Object? tapRegionGroupId;

  @override
  State<_MusicBoxSourceBrowser> createState() => _MusicBoxSourceBrowserState();
}

class _MusicBoxSourceBrowserState extends State<_MusicBoxSourceBrowser> {
  bool _sourceListLoading = true;
  String? _sourceListError;
  bool _trackLoading = false;
  String? _trackError;
  List<PersonalMusicPlaylist> _roomPlaylists = const [];
  List<PersonalMusicPlaylist> _personalPlaylists = const [];
  music_box_display.MusicBoxSourceScopeFilter? _filter;
  List<PersonalMusicPlaylistItem> _selectedItems = const [];
  String? _loadedSourceKey;
  String? _startingTrackKey;

  @override
  void initState() {
    super.initState();
    unawaited(_loadPlaylists());
    if (!widget.showSourceList && widget.viewedSource.queueItems == null) {
      unawaited(_loadViewedSource());
    }
  }

  @override
  void didUpdateWidget(covariant _MusicBoxSourceBrowser oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller ||
        oldWidget.roomId != widget.roomId) {
      _selectedItems = const [];
      _loadedSourceKey = null;
      unawaited(_loadPlaylists());
    }
    final viewedSourceChanged =
        oldWidget.viewedSource.key != widget.viewedSource.key;
    if (viewedSourceChanged) {
      _selectedItems = const [];
      _loadedSourceKey = null;
      _trackError = null;
    }
    if (!widget.showSourceList &&
        widget.viewedSource.queueItems == null &&
        _loadedSourceKey != widget.viewedSource.key &&
        !_trackLoading) {
      unawaited(_loadViewedSource());
    }
  }

  Future<void> _loadPlaylists() async {
    final controller = widget.controller;
    if (controller == null) {
      if (!mounted) return;
      setState(() {
        _sourceListLoading = false;
        _sourceListError = '歌单服务暂不可用';
      });
      return;
    }
    setState(() {
      _sourceListLoading = true;
      _sourceListError = null;
    });
    try {
      final roomFuture = widget.roomId == null
          ? Future<List<PersonalMusicPlaylist>>.value(const [])
          : controller
                .loadRoomPlaylists(roomId: widget.roomId!)
                .then((page) => page.playlists);
      final personalFuture = controller.loadMyPlaylists().then(
        (page) => page.playlists,
      );
      final results = await Future.wait<List<PersonalMusicPlaylist>>([
        roomFuture,
        personalFuture,
      ]);
      if (!mounted) return;
      setState(() {
        _sourceListLoading = false;
        _roomPlaylists = results[0];
        _personalPlaylists = results[1];
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _sourceListLoading = false;
        _sourceListError = '加载歌单失败';
      });
    }
  }

  Future<void> _loadViewedSource() async {
    final source = widget.viewedSource;
    final queueItems = source.queueItems;
    if (queueItems != null) {
      if (!mounted) return;
      setState(() {
        _trackLoading = false;
        _trackError = null;
        _selectedItems = const [];
        _loadedSourceKey = source.key;
      });
      return;
    }
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || (source.roomScoped && roomId == null)) {
      if (!mounted) return;
      setState(() {
        _trackLoading = false;
        _trackError = '歌单服务暂不可用';
      });
      return;
    }
    setState(() {
      _trackLoading = true;
      _trackError = null;
      _selectedItems = const [];
    });
    try {
      final page = source.roomScoped
          ? await controller.loadRoomPlaylist(
              roomId: roomId!,
              playlistId: source.id,
            )
          : await controller.loadMyPlaylist(playlistId: source.id);
      if (!mounted || widget.viewedSource.key != source.key) return;
      setState(() {
        _trackLoading = false;
        _selectedItems = page.items;
        _loadedSourceKey = source.key;
      });
    } catch (_) {
      if (!mounted || widget.viewedSource.key != source.key) return;
      setState(() {
        _trackLoading = false;
        _trackError = '加载歌曲失败';
      });
    }
  }

  List<_MusicBoxBrowseSource> _sources() {
    final active = widget.state.activeSource;
    final result = <_MusicBoxBrowseSource>[];
    if (active.type != MusicBoxActiveSourceType.temporary &&
        active.id.isNotEmpty &&
        music_box_display.musicBoxSourceVisibleForFilter(
          active.type,
          _filter,
        )) {
      result.add(
        _MusicBoxBrowseSource(
          type: active.type,
          id: active.id,
          name: music_box_display.musicBoxActiveSourceLabel(active),
          itemCount: widget.state.queue.length,
          current: true,
          roomScoped: active.type == MusicBoxActiveSourceType.roomPlaylist,
          queueItems: widget.state.queue,
        ),
      );
    }
    if (music_box_display.musicBoxSourceVisibleForFilter(
      MusicBoxActiveSourceType.temporary,
      _filter,
    )) {
      result.add(
        _MusicBoxBrowseSource(
          type: MusicBoxActiveSourceType.temporary,
          id: widget.roomId ?? '',
          name: '点歌队列',
          itemCount: widget.state.temporaryQueue.length,
          current: active.type == MusicBoxActiveSourceType.temporary,
          roomScoped: true,
          queueItems: widget.state.temporaryQueue,
        ),
      );
    }

    if (music_box_display.musicBoxSourceVisibleForFilter(
      MusicBoxActiveSourceType.roomPlaylist,
      _filter,
    )) {
      for (final playlist in _roomPlaylists) {
        if (active.type == MusicBoxActiveSourceType.roomPlaylist &&
            active.id == playlist.id) {
          continue;
        }
        result.add(
          _MusicBoxBrowseSource(
            type: MusicBoxActiveSourceType.roomPlaylist,
            id: playlist.id,
            name: playlist.name,
            itemCount: playlist.itemCount,
            current: false,
            roomScoped: true,
            playlist: playlist,
          ),
        );
      }
    }
    if (music_box_display.musicBoxSourceVisibleForFilter(
      MusicBoxActiveSourceType.userPlaylist,
      _filter,
    )) {
      for (final playlist in _personalPlaylists) {
        if (active.type == MusicBoxActiveSourceType.userPlaylist &&
            active.id == playlist.id) {
          continue;
        }
        result.add(
          _MusicBoxBrowseSource(
            type: MusicBoxActiveSourceType.userPlaylist,
            id: playlist.id,
            name: playlist.name,
            itemCount: playlist.itemCount,
            current: false,
            roomScoped: false,
            playlist: playlist,
          ),
        );
      }
    }
    return result
        .where(
          (source) => music_box_display.musicBoxSourceMatchesQuery(
            source.name,
            widget.sourceQuery,
          ),
        )
        .toList(growable: false);
  }

  void _toggleFilter(music_box_display.MusicBoxSourceScopeFilter value) {
    setState(() => _filter = _filter == value ? null : value);
  }

  void _openSource(_MusicBoxBrowseSource source) {
    widget.onViewedSourceChanged(source);
  }

  bool _sourceIsActive(_MusicBoxBrowseSource source) {
    final active = widget.state.activeSource;
    if (source.type == MusicBoxActiveSourceType.temporary) {
      return active.type == MusicBoxActiveSourceType.temporary;
    }
    return active.type == source.type && active.id == source.id;
  }

  Future<void> _playQueueItem(
    _MusicBoxBrowseSource source,
    MusicBoxQueueItem item,
  ) async {
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || roomId == null || _startingTrackKey != null) {
      return;
    }
    final key = '${source.key}:${item.id}';
    setState(() => _startingTrackKey = key);
    try {
      final state = _sourceIsActive(source)
          ? await controller.playNow(roomId: roomId, item: item)
          : await controller.activatePlaylist(
              roomId: roomId,
              sourceType: MusicBoxActiveSourceType.temporary,
              startItemId: item.id,
            );
      widget.onStateChanged(state);
    } catch (error) {
      if (mounted) {
        showFloatingErrorNotice(
          context,
          musicBoxControlErrorMessage(error, '播放歌曲失败，请重试'),
        );
      }
    } finally {
      if (mounted) setState(() => _startingTrackKey = null);
    }
  }

  Future<void> _playPlaylistItem(
    _MusicBoxBrowseSource source,
    PersonalMusicPlaylistItem item,
  ) async {
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || roomId == null || _startingTrackKey != null) {
      return;
    }
    final key = '${source.key}:${item.id}';
    setState(() => _startingTrackKey = key);
    try {
      final state = await controller.activatePlaylist(
        roomId: roomId,
        sourceType: source.type,
        playlistId: source.id,
        startItemId: item.id,
      );
      widget.onStateChanged(state);
    } catch (error) {
      if (mounted) {
        showFloatingErrorNotice(
          context,
          musicBoxControlErrorMessage(error, '播放歌曲失败，请重试'),
        );
      }
    } finally {
      if (mounted) setState(() => _startingTrackKey = null);
    }
  }

  Widget _filters() {
    return Row(
      children: [
        Expanded(
          child: CompactCategoryButton(
            key: const ValueKey<String>('music-box-source-filter-personal'),
            label: '我的歌单',
            selected:
                _filter == music_box_display.MusicBoxSourceScopeFilter.personal,
            onPressed: () => _toggleFilter(
              music_box_display.MusicBoxSourceScopeFilter.personal,
            ),
          ),
        ),
        const SizedBox(width: 6),
        Expanded(
          child: CompactCategoryButton(
            key: const ValueKey<String>('music-box-source-filter-room'),
            label: '房间歌单',
            selected:
                _filter == music_box_display.MusicBoxSourceScopeFilter.room,
            onPressed: () =>
                _toggleFilter(music_box_display.MusicBoxSourceScopeFilter.room),
          ),
        ),
      ],
    );
  }

  Widget _sourceList() {
    final sources = _sources();
    final emptyScope = switch (_filter) {
      music_box_display.MusicBoxSourceScopeFilter.personal =>
        _personalPlaylists.isEmpty,
      music_box_display.MusicBoxSourceScopeFilter.room =>
        _roomPlaylists.isEmpty,
      null => false,
    };
    final emptyMessage = switch (_filter) {
      music_box_display.MusicBoxSourceScopeFilter.personal when emptyScope =>
        '还没有个人歌单',
      music_box_display.MusicBoxSourceScopeFilter.room when emptyScope =>
        '还没有房间歌单',
      _ => '没有匹配的歌单',
    };
    final createAction = switch (_filter) {
      music_box_display.MusicBoxSourceScopeFilter.personal when emptyScope =>
        widget.onCreateFirstPersonalPlaylist,
      music_box_display.MusicBoxSourceScopeFilter.room when emptyScope =>
        widget.onCreateFirstRoomPlaylist,
      _ => null,
    };
    final createActionKey = switch (_filter) {
      music_box_display.MusicBoxSourceScopeFilter.personal when emptyScope =>
        const ValueKey<String>('music-box-create-first-personal-playlist'),
      music_box_display.MusicBoxSourceScopeFilter.room when emptyScope =>
        const ValueKey<String>('music-box-create-first-room-playlist'),
      _ => null,
    };
    final emptyIcon = switch (_filter) {
      music_box_display.MusicBoxSourceScopeFilter.personal when emptyScope =>
        Icons.person,
      music_box_display.MusicBoxSourceScopeFilter.room when emptyScope =>
        Icons.meeting_room,
      _ => Icons.search_off,
    };
    final emptyState = _MusicBoxEmpty(
      icon: emptyIcon,
      message: emptyMessage,
      actionKey: createActionKey,
      actionLabel: emptyScope ? '新建第一个歌单' : null,
      onAction: createAction,
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _filters(),
        const SizedBox(height: 8),
        Expanded(
          child: sources.isEmpty
              ? emptyState
              : ListView.separated(
                  key: const ValueKey<String>('music-box-source-list'),
                  padding: EdgeInsets.zero,
                  itemCount: sources.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 2),
                  itemBuilder: (context, index) {
                    final source = sources[index];
                    final row = _MusicBoxSourceListRow(
                      key: ValueKey<String>('music-box-source:${source.key}'),
                      source: source,
                      selected: source.key == widget.viewedSource.key,
                      onPressed: () => _openSource(source),
                    );
                    return row;
                  },
                ),
        ),
      ],
    );
  }

  Widget _sourceTracks(_MusicBoxBrowseSource source) {
    final queueItems = source.queueItems;
    final itemCount = queueItems?.length ?? _selectedItems.length;
    return itemCount == 0
        ? const _MusicBoxEmpty(icon: Icons.music_off, message: '歌单里还没有歌曲')
        : ListView.separated(
            key: ValueKey<String>('music-box-source-tracks:${source.key}'),
            padding: EdgeInsets.zero,
            itemCount: itemCount,
            separatorBuilder: (_, _) => const SizedBox(height: 8),
            itemBuilder: (context, index) {
              if (queueItems != null) {
                final item = queueItems[index];
                final result = MusicBoxSearchResult(
                  trackId: item.trackId,
                  name: item.title,
                  artists: item.artist.trim().isEmpty
                      ? const []
                      : [item.artist.trim()],
                  source: item.source,
                );
                final actionKey = '${source.key}:${item.id}';
                return _MusicBoxTrackTile(
                  keyScope: 'source-queue',
                  result: result,
                  query: '',
                  durationMs: item.durationMs,
                  controller: widget.controller,
                  roomId: widget.roomId,
                  alreadyInRequestQueue: true,
                  onQueue: () {},
                  actionIcon: Icons.play_arrow,
                  actionTooltip: '播放该歌曲',
                  actionLoading: _startingTrackKey == actionKey,
                  onAction: () => unawaited(_playQueueItem(source, item)),
                );
              }
              final item = _selectedItems[index];
              final result = MusicBoxSearchResult(
                trackId: item.trackId,
                name: item.title,
                artists: item.artists,
                source: item.source,
              );
              final actionKey = '${source.key}:${item.id}';
              return _MusicBoxTrackTile(
                keyScope: 'source-playlist',
                result: result,
                query: '',
                durationMs: item.durationMs,
                controller: widget.controller,
                roomId: widget.roomId,
                alreadyInRequestQueue: music_box_display
                    .musicBoxRequestQueueContainsTrack(
                      widget.state.temporaryQueue,
                      source: item.source,
                      trackId: item.trackId,
                    ),
                onQueue: () => widget.onQueueResult(result),
                actionIcon: Icons.play_arrow,
                actionTooltip: '从这首歌开始播放歌单',
                actionLoading: _startingTrackKey == actionKey,
                onAction: () => unawaited(_playPlaylistItem(source, item)),
              );
            },
          );
  }

  @override
  Widget build(BuildContext context) {
    if (widget.showSourceList && _sourceListLoading) {
      return const Center(
        child: SizedBox.square(
          dimension: 22,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
      );
    }
    if (widget.showSourceList && _sourceListError != null) {
      return _MusicBoxRetryState(
        message: _sourceListError!,
        onRetry: () => unawaited(_loadPlaylists()),
      );
    }
    if (widget.showSourceList) {
      final sourceList = _sourceList();
      final groupId = widget.tapRegionGroupId;
      return groupId == null
          ? sourceList
          : TapRegion(groupId: groupId, child: sourceList);
    }
    if (_trackLoading) {
      return const Center(
        child: SizedBox.square(
          dimension: 22,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
      );
    }
    if (_trackError != null) {
      return _MusicBoxRetryState(
        message: _trackError!,
        onRetry: () => unawaited(_loadViewedSource()),
      );
    }
    return _sourceTracks(widget.viewedSource);
  }
}

class _MusicBoxSourceListRow extends StatefulWidget {
  const _MusicBoxSourceListRow({
    super.key,
    required this.source,
    required this.selected,
    required this.onPressed,
  });

  final _MusicBoxBrowseSource source;
  final bool selected;
  final VoidCallback onPressed;

  @override
  State<_MusicBoxSourceListRow> createState() => _MusicBoxSourceListRowState();
}

class _MusicBoxSourceListRowState extends State<_MusicBoxSourceListRow> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    final foreground = widget.selected ? UiColors.accent : UiColors.text;
    return Semantics(
      button: true,
      selected: widget.selected,
      label: widget.source.name,
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => _setHovered(true),
        onExit: (_) => _setHovered(false),
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: widget.onPressed,
          child: AnimatedContainer(
            key: ValueKey<String>(
              'music-box-source-flat-row:${widget.source.key}',
            ),
            duration: const Duration(milliseconds: 80),
            curve: Curves.easeOutCubic,
            constraints: const BoxConstraints(minHeight: 48),
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: widget.selected
                  ? UiColors.selected
                  : _hovered
                  ? UiColors.surfaceLow
                  : Colors.transparent,
              borderRadius: BorderRadius.circular(UiRadii.sm),
            ),
            child: Row(
              children: [
                Icon(
                  _musicBoxBrowseSourceIcon(widget.source),
                  size: 18,
                  color: widget.selected
                      ? UiColors.accent
                      : UiColors.textSecondary,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.source.name,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          color: foreground,
                          fontSize: 12,
                          fontWeight: widget.selected
                              ? FontWeight.w600
                              : FontWeight.w500,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        widget.source.current
                            ? '正在播放 · ${widget.source.itemCount} 首歌曲'
                            : '${widget.source.itemCount} 首歌曲',
                        style: const TextStyle(
                          color: UiColors.textMuted,
                          fontSize: 10,
                        ),
                      ),
                    ],
                  ),
                ),
                if (widget.source.current) ...[
                  const SizedBox(width: 8),
                  Tooltip(
                    message: '正在播放',
                    child: Icon(
                      Icons.graphic_eq,
                      key: ValueKey<String>(
                        'music-box-source-playing-indicator:${widget.source.key}',
                      ),
                      size: 18,
                      color: UiColors.accent,
                      semanticLabel: '正在播放',
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _setHovered(bool hovered) {
    if (_hovered == hovered) return;
    setState(() => _hovered = hovered);
  }
}

class _MusicBoxPlaylistBrowser extends StatefulWidget {
  const _MusicBoxPlaylistBrowser({
    required this.controller,
    required this.roomId,
    required this.roomScoped,
    required this.temporaryQueue,
    required this.onQueueResult,
    required this.onStateChanged,
    required this.onCreateFirstPlaylist,
  });

  final MusicBoxController? controller;
  final String? roomId;
  final bool roomScoped;
  final List<MusicBoxQueueItem> temporaryQueue;
  final ValueChanged<MusicBoxSearchResult> onQueueResult;
  final ValueChanged<MusicBoxState>? onStateChanged;
  final VoidCallback? onCreateFirstPlaylist;

  @override
  State<_MusicBoxPlaylistBrowser> createState() =>
      _MusicBoxPlaylistBrowserState();
}

class _MusicBoxPlaylistBrowserState extends State<_MusicBoxPlaylistBrowser> {
  bool _loading = true;
  String? _error;
  List<PersonalMusicPlaylist> _playlists = const [];
  PersonalMusicPlaylist? _selected;
  List<PersonalMusicPlaylistItem> _items = const [];
  String? _activatingPlaylistId;

  @override
  void initState() {
    super.initState();
    unawaited(_loadPlaylists());
  }

  @override
  void didUpdateWidget(_MusicBoxPlaylistBrowser oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.roomId != widget.roomId ||
        oldWidget.roomScoped != widget.roomScoped ||
        oldWidget.controller != widget.controller) {
      _selected = null;
      _items = const [];
      unawaited(_loadPlaylists());
      return;
    }
  }

  Future<void> _loadPlaylists() async {
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || (widget.roomScoped && roomId == null)) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _playlists = const [];
        _error = '歌单服务暂不可用';
      });
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final page = widget.roomScoped
          ? await controller.loadRoomPlaylists(roomId: roomId!)
          : await controller.loadMyPlaylists();
      if (!mounted) return;
      setState(() {
        _loading = false;
        _playlists = page.playlists;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = '加载歌单失败';
      });
    }
  }

  Future<void> _openPlaylist(PersonalMusicPlaylist playlist) async {
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || (widget.roomScoped && roomId == null)) return;
    setState(() {
      _selected = playlist;
      _items = const [];
      _loading = true;
      _error = null;
    });
    try {
      final page = widget.roomScoped
          ? await controller.loadRoomPlaylist(
              roomId: roomId!,
              playlistId: playlist.id,
            )
          : await controller.loadMyPlaylist(playlistId: playlist.id);
      if (!mounted || _selected?.id != playlist.id) return;
      setState(() {
        _loading = false;
        _items = page.items;
      });
    } catch (_) {
      if (!mounted || _selected?.id != playlist.id) return;
      setState(() {
        _loading = false;
        _error = '加载歌曲失败';
      });
    }
  }

  Future<void> _activatePlaylist(PersonalMusicPlaylist playlist) async {
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || roomId == null || _activatingPlaylistId != null) {
      return;
    }
    setState(() => _activatingPlaylistId = playlist.id);
    try {
      final state = await controller.activatePlaylist(
        roomId: roomId,
        sourceType: widget.roomScoped
            ? MusicBoxActiveSourceType.roomPlaylist
            : MusicBoxActiveSourceType.userPlaylist,
        playlistId: playlist.id,
      );
      widget.onStateChanged?.call(state);
    } catch (_) {
      if (!mounted) return;
      setState(() => _error = '播放歌单失败');
    } finally {
      if (mounted) setState(() => _activatingPlaylistId = null);
    }
  }

  Widget _playlistCardAnchor({
    required BuildContext context,
    required PersonalMusicPlaylist playlist,
    required Widget child,
  }) {
    final host = MusicPlaylistCardHostScope.maybeOf(context);
    final editPlaylist = widget.roomScoped
        ? host?.onEditRoomPlaylist
        : host?.onEditPersonalPlaylist;
    return MusicPlaylistHoverCard(
      data: MusicPlaylistCardData(
        id: playlist.id,
        name: playlist.name,
        songCount: playlist.itemCount,
        createdAt: playlist.createdAt,
        creator: widget.roomScoped ? null : host?.currentUser?.toSummary(),
        room: widget.roomScoped ? host?.room : null,
      ),
      currentUser: host?.currentUser,
      onResolveUserProfile: host?.onResolveUserProfile,
      onResolveRoomProfile: host?.onResolveRoomProfile,
      onEnterCommonRoom: host?.onEnterCommonRoom,
      userProfileActionBuilder: host?.userProfileActionBuilder,
      onPlayAll: () => _activatePlaylist(playlist),
      onViewPlaylist: editPlaylist == null
          ? host == null
                ? null
                : () => host.onViewPlaylist(playlist, widget.roomScoped)
          : () => editPlaylist(playlist.id),
      secondaryActionLabel: editPlaylist == null ? '查看歌单' : '编辑歌单',
      secondaryActionIcon: editPlaylist == null
          ? Icons.queue_music
          : Icons.edit_outlined,
      child: child,
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(
        child: SizedBox.square(
          dimension: 22,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
      );
    }
    if (_error != null) {
      return _MusicBoxRetryState(message: _error!, onRetry: _retry);
    }
    final selected = _selected;
    if (selected != null) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              ButtonIcon(
                icon: const Icon(Icons.arrow_back),
                onPressed: () {
                  setState(() {
                    _selected = null;
                    _items = const [];
                  });
                },
                size: 28,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: LayoutBuilder(
                  builder: (context, constraints) {
                    const baseStyle = TextStyle(
                      color: UiColors.text,
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                    );
                    final nameStyle = _musicBoxAdaptiveListTextStyle(
                      context,
                      text: selected.name,
                      baseStyle: baseStyle,
                      width: (constraints.maxWidth - 24).clamp(
                        24.0,
                        double.infinity,
                      ),
                    );
                    return Row(
                      key: ValueKey<String>(
                        'music-box-playlist-header:${selected.id}',
                      ),
                      children: [
                        const Icon(
                          Icons.queue_music,
                          key: ValueKey<String>(
                            'music-box-playlist-header-icon',
                          ),
                          size: 17,
                          color: UiColors.accent,
                        ),
                        const SizedBox(width: 7),
                        Expanded(
                          child: Text(
                            selected.name,
                            style: nameStyle,
                            softWrap: true,
                          ),
                        ),
                      ],
                    );
                  },
                ),
              ),
              const SizedBox(width: 8),
              ButtonIcon(
                icon: const Icon(Icons.play_arrow),
                tooltip: '播放整个歌单',
                tone: ButtonTone.primary,
                loading: _activatingPlaylistId == selected.id,
                onPressed: () => unawaited(_activatePlaylist(selected)),
                size: 28,
              ),
            ],
          ),
          const SizedBox(height: 8),
          Expanded(
            child: _items.isEmpty
                ? const _MusicBoxEmpty(
                    icon: Icons.music_off,
                    message: '歌单里还没有歌曲',
                  )
                : ListView.separated(
                    padding: EdgeInsets.zero,
                    itemCount: _items.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 8),
                    itemBuilder: (context, index) {
                      final item = _items[index];
                      return _MusicBoxTrackTile(
                        keyScope: 'playlist',
                        result: MusicBoxSearchResult(
                          trackId: item.trackId,
                          name: item.title,
                          artists: item.artists,
                          source: item.source,
                        ),
                        query: '',
                        durationMs: item.durationMs,
                        controller: widget.controller,
                        roomId: widget.roomId,
                        alreadyInRequestQueue: music_box_display
                            .musicBoxRequestQueueContainsTrack(
                              widget.temporaryQueue,
                              source: item.source,
                              trackId: item.trackId,
                            ),
                        onQueue: () => widget.onQueueResult(
                          MusicBoxSearchResult(
                            trackId: item.trackId,
                            name: item.title,
                            artists: item.artists,
                            source: item.source,
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      );
    }
    if (_playlists.isEmpty) {
      return _MusicBoxEmpty(
        icon: widget.roomScoped ? Icons.meeting_room : Icons.person,
        message: widget.roomScoped ? '还没有房间歌单' : '还没有个人歌单',
        actionKey: ValueKey<String>(
          widget.roomScoped
              ? 'music-box-create-first-room-playlist'
              : 'music-box-create-first-personal-playlist',
        ),
        actionLabel: '新建第一个歌单',
        onAction: widget.onCreateFirstPlaylist,
      );
    }
    return ListView.separated(
      padding: EdgeInsets.zero,
      itemCount: _playlists.length,
      separatorBuilder: (_, _) => const SizedBox(height: 8),
      itemBuilder: (context, index) {
        final playlist = _playlists[index];
        const nameBaseStyle = TextStyle(
          color: UiColors.text,
          fontSize: 12,
          fontWeight: FontWeight.w600,
        );
        return LayoutBuilder(
          builder: (context, constraints) {
            final textWidth = (constraints.maxWidth - 20 - 17 - 8 - 28 - 2 - 34)
                .clamp(24.0, double.infinity);
            final nameStyle = _musicBoxAdaptiveListTextStyle(
              context,
              text: playlist.name,
              baseStyle: nameBaseStyle,
              width: textWidth,
            );
            final nameHeight = _musicBoxMeasureTextHeight(
              context,
              playlist.name,
              nameStyle,
              textWidth,
            );
            final tileHeight =
                (nameHeight + _musicBoxPlaylistRowVerticalPadding).clamp(
                  _musicBoxPlaylistRowMinHeight,
                  double.infinity,
                );
            return _playlistCardAnchor(
              context: context,
              playlist: playlist,
              child: PressableSurface(
                key: ValueKey<String>(
                  'music-box-playlist-summary:${playlist.id}',
                ),
                width: double.infinity,
                height: tileHeight,
                hoverLift: 2,
                baseDepth: 4,
                backgroundColor: UiColors.surfaceLow,
                pressedBackgroundColor: UiColors.surfacePressed,
                borderColor: UiColors.border,
                borderRadius: UiRadii.md,
                padding: const EdgeInsets.symmetric(horizontal: 10),
                child: Row(
                  children: [
                    Icon(
                      widget.roomScoped ? Icons.meeting_room : Icons.person,
                      size: 17,
                      color: UiColors.accent,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        playlist.name,
                        style: nameStyle,
                        softWrap: true,
                      ),
                    ),
                    _MusicBoxHoverCardActionGuard(
                      child: ButtonIcon(
                        icon: const Icon(Icons.play_arrow),
                        tooltip: '播放整个歌单',
                        tone: ButtonTone.primary,
                        loading: _activatingPlaylistId == playlist.id,
                        onPressed: () => unawaited(_activatePlaylist(playlist)),
                        size: 28,
                      ),
                    ),
                    const SizedBox(width: 2),
                    _MusicBoxHoverCardActionGuard(
                      child: ButtonIconPlain(
                        key: ValueKey<String>(
                          'music-box-playlist-open:${playlist.id}',
                        ),
                        icon: const Icon(Icons.chevron_right),
                        tooltip: '查看歌单',
                        width: 34,
                        height: tileHeight,
                        iconSize: 18,
                        onPressed: () => unawaited(_openPlaylist(playlist)),
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  void _retry() {
    final selected = _selected;
    if (selected == null) {
      unawaited(_loadPlaylists());
    } else {
      unawaited(_openPlaylist(selected));
    }
  }
}

double _musicBoxMeasureTextHeight(
  BuildContext context,
  String text,
  TextStyle style,
  double width,
) {
  final painter = TextPainter(
    text: TextSpan(text: text.isEmpty ? ' ' : text, style: style),
    textDirection: Directionality.of(context),
    textScaler: MediaQuery.textScalerOf(context),
  )..layout(maxWidth: width);
  return painter.height;
}

TextStyle _musicBoxAdaptiveListTextStyle(
  BuildContext context, {
  required String text,
  required TextStyle baseStyle,
  required double width,
  int comfortableLines = 2,
  double maximumReduction = 2,
}) {
  final painter = TextPainter(
    text: TextSpan(text: text.isEmpty ? ' ' : text, style: baseStyle),
    textDirection: Directionality.of(context),
    textScaler: MediaQuery.textScalerOf(context),
  )..layout(maxWidth: width);
  final lineCount = painter.computeLineMetrics().length;
  if (lineCount <= comfortableLines) return baseStyle;
  final reduction = (lineCount - comfortableLines)
      .clamp(1, maximumReduction.toInt())
      .toDouble();
  final baseSize = baseStyle.fontSize ?? 12;
  return baseStyle.copyWith(
    fontSize: (baseSize - reduction).clamp(10, baseSize),
  );
}

double _musicBoxAdaptiveSongRowHeight(
  BuildContext context, {
  required double width,
  required String title,
  required String subtitle,
  required TextStyle titleStyle,
  required TextStyle subtitleStyle,
  double contentScale = 1,
}) {
  final titlePainter = TextPainter(
    text: TextSpan(text: title.isEmpty ? ' ' : title, style: titleStyle),
    textDirection: Directionality.of(context),
    textScaler: MediaQuery.textScalerOf(context),
  )..layout(maxWidth: width);
  final subtitlePainter = TextPainter(
    text: TextSpan(
      text: subtitle.isEmpty ? ' ' : subtitle,
      style: subtitleStyle,
    ),
    textDirection: Directionality.of(context),
    textScaler: MediaQuery.textScalerOf(context),
  )..layout(maxWidth: width);
  final measuredContentHeight =
      titlePainter.height + 2 + subtitlePainter.height;
  final lineCount =
      titlePainter.computeLineMetrics().length +
      subtitlePainter.computeLineMetrics().length;
  // Multi-line rows need a little more breathing room than TextPainter's bare
  // glyph bounds, especially when a fallback CJK font is selected. Keep the
  // compact 50px row for two lines, and grow from measured content otherwise.
  final adaptiveScale = lineCount > 2 && contentScale < 1.25
      ? 1.25
      : contentScale;
  final fallbackFontLeading = lineCount > 2
      ? ((titleStyle.fontSize ?? 12) + (subtitleStyle.fontSize ?? 11)) * 0.125
      : 0.0;
  final naturalHeight =
      16 + measuredContentHeight * adaptiveScale + fallbackFontLeading;
  return naturalHeight < 50 ? 50 : naturalHeight;
}

double _musicBoxQueueTileHeight(
  BuildContext context, {
  required double width,
  required MusicBoxQueueItem item,
}) {
  final subtitle = _musicBoxQueueSubtitle(item);
  final textWidth = (width - 20 - 20 - 8 - 4 - 28).clamp(24.0, double.infinity);
  const titleStyle = TextStyle(fontSize: 13, fontWeight: FontWeight.w500);
  const subtitleStyle = TextStyle(fontSize: 11, fontWeight: FontWeight.w600);
  final adaptiveTitleStyle = _musicBoxAdaptiveListTextStyle(
    context,
    text: item.title,
    baseStyle: titleStyle,
    width: textWidth,
  );
  final adaptiveSubtitleStyle = _musicBoxAdaptiveListTextStyle(
    context,
    text: subtitle,
    baseStyle: subtitleStyle,
    width: textWidth,
  );
  return _musicBoxAdaptiveSongRowHeight(
    context,
    width: textWidth,
    title: item.title,
    subtitle: subtitle,
    titleStyle: adaptiveTitleStyle,
    subtitleStyle: adaptiveSubtitleStyle,
  );
}

String _musicBoxQueueSubtitle(MusicBoxQueueItem item) {
  final statusLabel = music_box_display.musicBoxQueueStatusLabel(item);
  return [
    if (item.artist.trim().isNotEmpty) item.artist.trim(),
    ?statusLabel,
  ].join(' · ');
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
            style: const TextStyle(
              color: UiColors.textMuted,
              fontSize: 11,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 8),
          ButtonIcon(
            icon: const Icon(Icons.refresh),
            onPressed: onRetry,
            size: 30,
          ),
        ],
      ),
    );
  }
}

class _MusicBoxQueueList extends StatefulWidget {
  const _MusicBoxQueueList({
    required this.state,
    required this.queue,
    required this.onRemoveItem,
    required this.controller,
    required this.roomId,
    required this.onStateChanged,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
  });

  final MusicBoxState state;
  final List<MusicBoxQueueItem> queue;
  final ValueChanged<MusicBoxQueueItem> onRemoveItem;
  final MusicBoxController? controller;
  final String? roomId;
  final ValueChanged<MusicBoxState>? onStateChanged;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;

  @override
  State<_MusicBoxQueueList> createState() => _MusicBoxQueueListState();
}

class _MusicBoxQueueListState extends State<_MusicBoxQueueList> {
  static const double _separatorHeight = 8;

  final ScrollController _scrollController = ScrollController();
  final GlobalKey _currentTileKey = GlobalKey();
  String? _scheduledCurrentItemId;
  bool _centeredInitialCurrentItem = false;

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _scheduleInitialCurrentItemCenter({
    required BoxConstraints constraints,
    required int currentIndex,
    required List<double> itemHeights,
  }) {
    if (_centeredInitialCurrentItem ||
        currentIndex < 0 ||
        !constraints.hasBoundedHeight ||
        constraints.maxHeight <= 0) {
      return;
    }
    final currentItemId = widget.state.playback.currentItemId;
    if (currentItemId.isEmpty || _scheduledCurrentItemId == currentItemId) {
      return;
    }
    var currentCenter = itemHeights[currentIndex] / 2;
    for (var index = 0; index < currentIndex; index += 1) {
      currentCenter += itemHeights[index] + _separatorHeight;
    }
    final targetOffset = currentCenter - constraints.maxHeight / 2;
    _scheduledCurrentItemId = currentItemId;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted ||
          widget.state.playback.currentItemId != currentItemId ||
          !_scrollController.hasClients) {
        _scheduledCurrentItemId = null;
        return;
      }
      final position = _scrollController.position;
      _scrollController.jumpTo(
        targetOffset.clamp(position.minScrollExtent, position.maxScrollExtent),
      );
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || widget.state.playback.currentItemId != currentItemId) {
          _scheduledCurrentItemId = null;
          return;
        }
        final currentContext = _currentTileKey.currentContext;
        if (currentContext != null) {
          unawaited(
            Scrollable.ensureVisible(
              currentContext,
              alignment: 0.5,
              duration: Duration.zero,
            ),
          );
        }
        _centeredInitialCurrentItem = true;
        _scheduledCurrentItemId = null;
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    if (widget.queue.isEmpty) {
      return const _MusicBoxEmpty(
        icon: Icons.queue_music,
        message: '当前队列为空，点击搜索按钮添加歌曲',
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) {
        final currentIndex = widget.queue.indexWhere(
          (item) => music_box_display.musicBoxIsCurrent(widget.state, item),
        );
        if (!_centeredInitialCurrentItem && currentIndex >= 0) {
          final itemHeights = [
            for (final item in widget.queue)
              _musicBoxQueueTileHeight(
                context,
                width: constraints.maxWidth,
                item: item,
              ),
          ];
          _scheduleInitialCurrentItemCenter(
            constraints: constraints,
            currentIndex: currentIndex,
            itemHeights: itemHeights,
          );
        }
        return ListView.separated(
          key: const ValueKey<String>('music-box-queue-list'),
          controller: _scrollController,
          padding: EdgeInsets.zero,
          itemCount: widget.queue.length,
          separatorBuilder: (_, _) => const SizedBox(height: _separatorHeight),
          itemBuilder: (context, index) {
            final item = widget.queue[index];
            final isCurrent = music_box_display.musicBoxIsCurrent(
              widget.state,
              item,
            );
            final tile = _MusicBoxQueueTile(
              item: item,
              isCurrent: isCurrent,
              onRemove: () => widget.onRemoveItem(item),
              controller: widget.controller,
              roomId: widget.roomId,
              currentState: widget.state,
              onStateChanged: widget.onStateChanged,
              currentUser: widget.currentUser,
              onResolveUserProfile: widget.onResolveUserProfile,
              onResolveRoomProfile: widget.onResolveRoomProfile,
              onEnterCommonRoom: widget.onEnterCommonRoom,
              userProfileActionBuilder: widget.userProfileActionBuilder,
            );
            if (!isCurrent) return tile;
            return KeyedSubtree(key: _currentTileKey, child: tile);
          },
        );
      },
    );
  }
}

class _MusicBoxQueueTile extends StatelessWidget {
  const _MusicBoxQueueTile({
    required this.item,
    required this.isCurrent,
    required this.onRemove,
    required this.controller,
    required this.roomId,
    required this.currentState,
    required this.onStateChanged,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
  });

  final MusicBoxQueueItem item;
  final bool isCurrent;
  final VoidCallback onRemove;
  final MusicBoxController? controller;
  final String? roomId;
  final MusicBoxState currentState;
  final ValueChanged<MusicBoxState>? onStateChanged;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;

  @override
  Widget build(BuildContext context) {
    final failed = item.status == MusicBoxQueueItemStatus.failed;
    final loading =
        item.status == MusicBoxQueueItemStatus.pending ||
        item.status == MusicBoxQueueItemStatus.downloading;
    final subtitle = _musicBoxQueueSubtitle(item);
    final titleStyle = TextStyle(
      color: isCurrent ? UiColors.accent : UiColors.text,
      fontSize: 13,
      fontWeight: FontWeight.w500,
    );
    final subtitleStyle = TextStyle(
      color: failed ? UiColors.danger : UiColors.textSecondary,
      fontSize: 11,
      fontWeight: FontWeight.w600,
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        final trailingWidth = item.canRemove ? 32.0 : 0.0;
        final textWidth = (constraints.maxWidth - 20 - 20 - 8 - trailingWidth)
            .clamp(24.0, double.infinity);
        final adaptiveTitleStyle = _musicBoxAdaptiveListTextStyle(
          context,
          text: item.title,
          baseStyle: titleStyle,
          width: textWidth,
        );
        final adaptiveSubtitleStyle = _musicBoxAdaptiveListTextStyle(
          context,
          text: subtitle,
          baseStyle: subtitleStyle,
          width: textWidth,
        );
        final tileHeight = _musicBoxAdaptiveSongRowHeight(
          context,
          width: textWidth,
          title: item.title,
          subtitle: subtitle,
          titleStyle: adaptiveTitleStyle,
          subtitleStyle: adaptiveSubtitleStyle,
        );
        return PressableSurface(
          key: ValueKey<String>('music-box-queue-tile:${item.id}'),
          width: double.infinity,
          height: tileHeight,
          hoverLift: 2,
          baseDepth: 4,
          interactive: false,
          hoverEffect: false,
          pressEffect: false,
          selected: isCurrent,
          backgroundColor: UiColors.surfaceLow,
          selectedBackgroundColor: UiColors.selected,
          borderColor: UiColors.border,
          selectedBorderColor: UiColors.selectedBorder,
          borderRadius: UiRadii.md,
          padding: const EdgeInsets.symmetric(horizontal: 10),
          child: Row(
            children: [
              Expanded(
                child: HoverCardAnchor(
                  resetKey: Object.hash(
                    item.id,
                    item.status,
                    item.canPlayNow,
                    item.requestedBy?.avatarUrl,
                    item.requestedBy?.avatarLabel,
                    currentState.activeSource.type,
                    currentState.activeSource.name,
                    currentState.activeSource.owner?.avatarUrl,
                    currentState.activeSource.owner?.avatarLabel,
                  ),
                  cardWidth: 310,
                  cardBuilder: (_) => _MusicBoxSongCard.queue(
                    item: item,
                    isCurrent: isCurrent,
                    queueSongCount: currentState.queue.length,
                    controller: controller,
                    roomId: roomId,
                    activeSource: currentState.activeSource,
                    onStateChanged: onStateChanged,
                    currentUser: currentUser,
                    onResolveUserProfile: onResolveUserProfile,
                    onResolveRoomProfile: onResolveRoomProfile,
                    onEnterCommonRoom: onEnterCommonRoom,
                    userProfileActionBuilder: userProfileActionBuilder,
                  ),
                  child: SizedBox(
                    height: tileHeight,
                    child: Row(
                      children: [
                        SizedBox(
                          width: 20,
                          height: 20,
                          child: loading
                              ? Center(
                                  child: SizedBox(
                                    key: ValueKey<String>(
                                      'music-box-queue-leading-loading:${item.id}',
                                    ),
                                    width: 14,
                                    height: 14,
                                    child: const CircularProgressIndicator(
                                      strokeWidth: 1.8,
                                    ),
                                  ),
                                )
                              : Icon(
                                  Icons.music_note,
                                  key: ValueKey<String>(
                                    'music-box-queue-leading-icon:${item.id}',
                                  ),
                                  size: 20,
                                  color: UiColors.accent,
                                ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Text(
                                item.title,
                                style: adaptiveTitleStyle,
                                softWrap: true,
                              ),
                              const SizedBox(height: 2),
                              Text(
                                subtitle,
                                style: adaptiveSubtitleStyle,
                                softWrap: true,
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              if (item.canRemove) ...[
                const SizedBox(width: 4),
                ButtonIcon(
                  key: ValueKey<String>('music-box-queue-remove:${item.id}'),
                  icon: const Icon(Icons.close),
                  tooltip: '从点歌队列删除',
                  tone: ButtonTone.danger,
                  onPressed: onRemove,
                  size: 28,
                ),
              ],
            ],
          ),
        );
      },
    );
  }
}

class _MusicBoxSongCard extends StatefulWidget {
  const _MusicBoxSongCard.queue({
    required this.item,
    required this.isCurrent,
    required this.queueSongCount,
    required this.controller,
    required this.roomId,
    required this.activeSource,
    required this.onStateChanged,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
  }) : result = null,
       catalogDurationMs = null,
       alreadyInRequestQueue = false,
       onQueueResult = null;

  const _MusicBoxSongCard.search({
    required this.result,
    required this.controller,
    required this.roomId,
    required this.onQueueResult,
    required this.alreadyInRequestQueue,
    this.catalogDurationMs,
  }) : item = null,
       isCurrent = false,
       queueSongCount = 0,
       activeSource = null,
       onStateChanged = null,
       currentUser = null,
       onResolveUserProfile = null,
       onResolveRoomProfile = null,
       onEnterCommonRoom = null,
       userProfileActionBuilder = null;

  final MusicBoxQueueItem? item;
  final MusicBoxSearchResult? result;
  final int? catalogDurationMs;
  final bool isCurrent;
  final int queueSongCount;
  final MusicBoxController? controller;
  final String? roomId;
  final MusicBoxActiveSource? activeSource;
  final ValueChanged<MusicBoxState>? onStateChanged;
  final ValueChanged<MusicBoxSearchResult>? onQueueResult;
  final bool alreadyInRequestQueue;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;

  @override
  State<_MusicBoxSongCard> createState() => _MusicBoxSongCardState();
}

class _MusicBoxSongCardState extends State<_MusicBoxSongCard> {
  bool _playingNow = false;

  Future<void> _playNow() async {
    final controller = widget.controller;
    final roomId = widget.roomId;
    final item = widget.item;
    if (controller == null || roomId == null || item == null || _playingNow) {
      return;
    }
    setState(() => _playingNow = true);
    try {
      final state = await controller.playNow(roomId: roomId, item: item);
      widget.onStateChanged?.call(state);
      if (mounted) showFloatingSuccessNotice(context, '已优先播放');
    } catch (error) {
      if (mounted) {
        showFloatingErrorNotice(
          context,
          musicBoxControlErrorMessage(error, '优先播放失败，请重试'),
        );
      }
    } finally {
      if (mounted) setState(() => _playingNow = false);
    }
  }

  Future<List<MusicTrackPlaylistTarget>> _loadPlaylistTargets() async {
    final controller = widget.controller;
    if (controller == null) return const [];
    final targets = <MusicTrackPlaylistTarget>[];
    final roomId = widget.roomId;
    Object? roomError;
    Object? personalError;
    if (roomId != null) {
      try {
        final page = await controller.loadRoomPlaylists(roomId: roomId);
        targets.addAll(
          page.playlists.map(
            (playlist) => MusicTrackPlaylistTarget.room(
              playlist: playlist,
              roomId: roomId,
            ),
          ),
        );
      } catch (error) {
        roomError = error;
      }
    }
    try {
      final page = await controller.loadMyPlaylists();
      targets.addAll(page.playlists.map(MusicTrackPlaylistTarget.personal));
    } catch (error) {
      personalError = error;
    }
    if (targets.isEmpty && (roomError != null || personalError != null)) {
      throw roomError ?? personalError!;
    }
    return targets;
  }

  Future<void> _openPlaylistPicker() async {
    final target = await showMusicTrackPlaylistTargetDialog(
      context,
      loadTargets: _loadPlaylistTargets,
      onAdd: _addToPlaylist,
    );
    if (!mounted || target == null) return;
    showFloatingSuccessNotice(context, '已添加到「${target.playlist.name}」');
  }

  Future<void> _addToPlaylist(MusicTrackPlaylistTarget target) async {
    final controller = widget.controller;
    if (controller == null) return;
    if (target.roomScoped) {
      final roomId = target.roomId ?? widget.roomId;
      if (roomId == null) return;
      final item = widget.item;
      if (item != null) {
        await controller.addQueueItemToRoomPlaylist(
          roomId: roomId,
          playlistId: target.playlist.id,
          item: item,
        );
      } else {
        await controller.addSearchResultToRoomPlaylist(
          roomId: roomId,
          playlistId: target.playlist.id,
          result: widget.result!,
        );
      }
    } else {
      final item = widget.item;
      if (item != null) {
        await controller.addQueueItemToMyPlaylist(
          playlistId: target.playlist.id,
          item: item,
        );
      } else {
        await controller.addSearchResultToMyPlaylist(
          playlistId: target.playlist.id,
          result: widget.result!,
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final item = widget.item;
    final result = widget.result;
    final isSearchResult = result != null;
    final title = item?.title ?? result!.name;
    final artist =
        item?.artist.trim() ??
        music_box_display.musicBoxArtistsLabel(result!.artists).trim();
    final source = item?.source ?? result!.source;
    final trackId = item?.trackId ?? result!.trackId;
    final durationMs = item?.durationMs ?? widget.catalogDurationMs ?? 0;
    final isBilibili = source.trim().toLowerCase() == 'bilibili';
    final canPlayNow =
        item != null &&
        item.canPlayNow &&
        item.status == MusicBoxQueueItemStatus.ready &&
        widget.controller != null &&
        widget.roomId != null;
    return SingleChildScrollView(
      child: Padding(
        key: ValueKey<String>(
          isSearchResult
              ? 'music-box-song-card:search:${result.source}:${result.trackId}'
              : 'music-box-song-card:${item!.id}',
        ),
        padding: const EdgeInsets.all(UiSpacing.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                const Icon(Icons.music_note, color: UiColors.accent, size: 28),
                const SizedBox(width: UiSpacing.sm),
                Expanded(
                  child: _MusicBoxAdaptiveFullText(
                    key: const ValueKey<String>('music-box-song-card-title'),
                    text: title,
                    style: UiTypography.title.copyWith(fontSize: 16),
                  ),
                ),
              ],
            ),
            const SizedBox(height: UiSpacing.md),
            _MusicBoxSongDetailRow(
              label: '时长',
              value: durationMs > 0
                  ? music_box_display.musicBoxFormatDuration(durationMs)
                  : '未知',
            ),
            _MusicBoxSongDetailRow(
              label: music_box_display.musicBoxArtistFieldLabel(source),
              value: artist.isEmpty
                  ? isBilibili
                        ? '未知作者'
                        : '未知艺人'
                  : artist,
            ),
            _MusicBoxSongDetailRow(
              label: '来源',
              value: music_box_display.musicBoxSourceLabel(source),
            ),
            if (!isSearchResult) ...[
              const SizedBox(height: UiSpacing.sm),
              _MusicBoxSongAttribution(
                source: widget.activeSource!,
                requester: item!.requestedBy,
                songCount: widget.queueSongCount,
                controller: widget.controller,
                roomId: widget.roomId,
                onStateChanged: widget.onStateChanged,
                currentUser: widget.currentUser,
                onResolveUserProfile: widget.onResolveUserProfile,
                onResolveRoomProfile: widget.onResolveRoomProfile,
                onEnterCommonRoom: widget.onEnterCommonRoom,
                userProfileActionBuilder: widget.userProfileActionBuilder,
              ),
            ],
            if (isBilibili) ...[
              const SizedBox(height: UiSpacing.sm),
              _MusicBoxSongDetailRow(
                label: '详情',
                value: _musicBoxBilibiliBvId(trackId),
              ),
            ],
            const SizedBox(height: UiSpacing.md),
            Row(
              children: [
                if (isSearchResult) ...[
                  Expanded(
                    child: Button(
                      icon: Icon(
                        widget.alreadyInRequestQueue
                            ? Icons.playlist_add_check
                            : Icons.playlist_add,
                      ),
                      tone: widget.alreadyInRequestQueue
                          ? ButtonTone.neutral
                          : ButtonTone.primary,
                      height: 34,
                      onPressed: widget.alreadyInRequestQueue
                          ? null
                          : () => widget.onQueueResult?.call(result),
                      child: Text(
                        widget.alreadyInRequestQueue ? '已在队列中' : '点歌队列',
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
                if (canPlayNow) ...[
                  Expanded(
                    child: Button(
                      icon: const Icon(Icons.play_arrow),
                      height: 34,
                      loading: _playingNow,
                      onPressed: widget.isCurrent
                          ? null
                          : () => unawaited(_playNow()),
                      child: Text(widget.isCurrent ? '正在播放' : '优先播放'),
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
                Expanded(
                  child: Button(
                    icon: const Icon(Icons.playlist_add),
                    tone: ButtonTone.primary,
                    height: 34,
                    onPressed: widget.controller == null
                        ? null
                        : () => unawaited(_openPlaylistPicker()),
                    child: const Text('添加到歌单'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _MusicBoxSongAttribution extends StatelessWidget {
  const _MusicBoxSongAttribution({
    required this.source,
    required this.requester,
    required this.songCount,
    required this.controller,
    required this.roomId,
    required this.onStateChanged,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
  });

  final MusicBoxActiveSource source;
  final MusicBoxRequester? requester;
  final int songCount;
  final MusicBoxController? controller;
  final String? roomId;
  final ValueChanged<MusicBoxState>? onStateChanged;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;

  MusicPlaylistCardResolver? _playlistResolver() {
    final musicController = controller;
    if (musicController == null || source.id.isEmpty) return null;
    if (source.type == MusicBoxActiveSourceType.roomPlaylist) {
      final currentRoomId = roomId;
      if (currentRoomId == null) return null;
      return (current) async {
        final page = await musicController.loadRoomPlaylists(
          roomId: currentRoomId,
        );
        final playlist = _musicBoxPlaylistById(page.playlists, source.id);
        if (playlist == null) return current;
        return current.copyWith(
          name: playlist.name,
          songCount: playlist.itemCount,
          createdAt: playlist.createdAt,
        );
      };
    }
    final ownerId = source.ownerUserId.isNotEmpty
        ? source.ownerUserId
        : source.owner?.userId ?? '';
    if (source.type != MusicBoxActiveSourceType.userPlaylist ||
        currentUser == null ||
        (ownerId.isNotEmpty && ownerId != currentUser!.id)) {
      return null;
    }
    return (current) async {
      final page = await musicController.loadMyPlaylists();
      final playlist = _musicBoxPlaylistById(page.playlists, source.id);
      if (playlist == null) return current;
      return current.copyWith(
        name: playlist.name,
        songCount: playlist.itemCount,
        createdAt: playlist.createdAt,
      );
    };
  }

  Future<void> _playPlaylist(BuildContext context) async {
    final musicController = controller;
    final currentRoomId = roomId;
    if (musicController == null || currentRoomId == null || source.id.isEmpty) {
      return;
    }
    try {
      final state = await musicController.activatePlaylist(
        roomId: currentRoomId,
        sourceType: source.type,
        playlistId: source.id,
      );
      onStateChanged?.call(state);
    } catch (error) {
      if (context.mounted) {
        showFloatingErrorNotice(
          context,
          musicBoxControlErrorMessage(error, '播放歌单失败，请重试'),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isPlaylist = source.type != MusicBoxActiveSourceType.temporary;
    final playlistName = source.name.trim().isEmpty ? '未命名歌单' : source.name;
    final host = MusicPlaylistCardHostScope.maybeOf(context);
    final creator = isPlaylist
        ? _musicBoxActiveSourceOwner(source, currentUser)
        : null;
    final ownerId = source.ownerUserId.isNotEmpty
        ? source.ownerUserId
        : source.owner?.userId ?? '';
    final viewer = currentUser;
    final isOwnedPersonalPlaylist =
        source.type == MusicBoxActiveSourceType.userPlaylist &&
        viewer != null &&
        (ownerId.isEmpty || ownerId == viewer.id);
    final editPlaylist = source.type == MusicBoxActiveSourceType.roomPlaylist
        ? host?.onEditRoomPlaylist
        : isOwnedPersonalPlaylist
        ? host?.onEditPersonalPlaylist
        : null;
    final playlistValue = _MusicBoxPlaylistAttributionValue(name: playlistName);
    final value = isPlaylist && source.id.isNotEmpty
        ? MusicPlaylistHoverCard(
            data: MusicPlaylistCardData(
              id: source.id,
              name: playlistName,
              songCount: songCount,
              createdAt: source.createdAt,
              creator: source.type == MusicBoxActiveSourceType.userPlaylist
                  ? creator
                  : null,
              room: source.type == MusicBoxActiveSourceType.roomPlaylist
                  ? host?.room
                  : null,
            ),
            resolveData: _playlistResolver(),
            currentUser: currentUser,
            onResolveUserProfile: onResolveUserProfile,
            onResolveRoomProfile: onResolveRoomProfile,
            onEnterCommonRoom: onEnterCommonRoom,
            userProfileActionBuilder: userProfileActionBuilder,
            onPlayAll: controller == null || roomId == null
                ? null
                : () => _playPlaylist(context),
            onViewPlaylist: editPlaylist != null
                ? () => editPlaylist(source.id)
                : host == null
                ? null
                : () => host.onViewPlaylist(
                    PersonalMusicPlaylist(
                      id: source.id,
                      name: playlistName,
                      description: '',
                      revision: 0,
                      itemCount: songCount,
                      createdAt: source.createdAt,
                      updatedAt: null,
                    ),
                    source.type == MusicBoxActiveSourceType.roomPlaylist,
                  ),
            secondaryActionLabel: editPlaylist == null ? '查看歌单' : '编辑歌单',
            secondaryActionIcon: editPlaylist == null
                ? Icons.queue_music
                : Icons.edit_outlined,
            child: playlistValue,
          )
        : isPlaylist
        ? playlistValue
        : _MusicBoxSongAttributionValue(
            value: requester?.displayName ?? '未知用户',
            person: requester,
            showAvatar: requester != null,
            currentUser: currentUser,
            onResolveUserProfile: onResolveUserProfile,
            onResolveRoomProfile: onResolveRoomProfile,
            onEnterCommonRoom: onEnterCommonRoom,
            userProfileActionBuilder: userProfileActionBuilder,
          );

    return Row(
      children: [
        SizedBox(
          width: 38,
          child: HoverCardSelectableText(
            value: isPlaylist ? '歌单' : '点歌人',
            style: UiTypography.label.copyWith(color: UiColors.textMuted),
          ),
        ),
        Expanded(child: value),
      ],
    );
  }
}

class _MusicBoxPlaylistAttributionValue extends StatelessWidget {
  const _MusicBoxPlaylistAttributionValue({required this.name});

  final String name;

  @override
  Widget build(BuildContext context) {
    return Row(
      key: const ValueKey<String>('music-box-song-playlist-attribution'),
      mainAxisAlignment: MainAxisAlignment.end,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        const Icon(
          Icons.queue_music,
          key: ValueKey<String>('music-box-song-playlist-icon'),
          size: 17,
          color: UiColors.accent,
        ),
        const SizedBox(width: 5),
        Flexible(
          child: Text(
            name,
            key: const ValueKey<String>('music-box-song-attribution-name'),
            textAlign: TextAlign.right,
            softWrap: true,
            style: UiTypography.label.copyWith(color: UiColors.text),
          ),
        ),
      ],
    );
  }
}

class _MusicBoxSongAttributionValue extends StatelessWidget {
  const _MusicBoxSongAttributionValue({
    required this.value,
    required this.person,
    required this.showAvatar,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
  });

  final String value;
  final MusicBoxRequester? person;
  final bool showAvatar;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;

  @override
  Widget build(BuildContext context) {
    final style = UiTypography.label.copyWith(color: UiColors.text);
    final user = person == null
        ? null
        : UserSummary(
            id: person!.userId,
            username: person!.displayName,
            displayName: person!.displayName,
            avatarUrl: person!.avatarUrl,
            defaultAvatarKey: person!.defaultAvatarKey,
          );
    return LayoutBuilder(
      builder: (context, constraints) {
        const avatarSize = 26.0;
        const avatarGap = 5.0;
        // TextPainter and RenderParagraph can differ by a few physical pixels
        // after font fallback and device-pixel rounding. Keep enough trailing
        // room that a measured single line never loses its final glyph.
        const textLayoutSlack = 8.0;
        final avatarWidth = showAvatar ? avatarSize + avatarGap : 0.0;
        final maxTextWidth = (constraints.maxWidth - avatarWidth).clamp(
          1.0,
          double.infinity,
        );
        final measurementWidth = (maxTextWidth - textLayoutSlack).clamp(
          1.0,
          maxTextWidth,
        );
        final painter = TextPainter(
          text: TextSpan(text: value.isEmpty ? ' ' : value, style: style),
          textDirection: Directionality.of(context),
          textScaler: MediaQuery.textScalerOf(context),
        )..layout(maxWidth: measurementWidth);
        final lines = painter.computeLineMetrics();
        var longestLine = 0.0;
        for (final line in lines) {
          if (line.width > longestLine) longestLine = line.width;
        }
        final textWidth = lines.length > 1
            ? maxTextWidth
            : (longestLine + textLayoutSlack).clamp(1.0, maxTextWidth);
        return Row(
          key: const ValueKey<String>('music-box-song-attribution-value-group'),
          mainAxisAlignment: MainAxisAlignment.end,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            if (showAvatar) ...[
              UserHoverCard(
                user: user!,
                currentUser: currentUser,
                onResolveProfile: onResolveUserProfile,
                onResolveRoomProfile: onResolveRoomProfile,
                onEnterCommonRoom: onEnterCommonRoom,
                profileActionBuilder: userProfileActionBuilder,
                showRoomRole: true,
                child: Avatar(
                  key: const ValueKey<String>(
                    'music-box-song-attribution-avatar',
                  ),
                  label: person!.avatarLabel,
                  imageUrl: person!.avatarUrl,
                  defaultAvatarKey: person!.defaultAvatarKey,
                  size: avatarSize,
                  showBorder: false,
                ),
              ),
              const SizedBox(width: avatarGap),
            ],
            SizedBox(
              width: textWidth,
              child: Text(
                value,
                key: const ValueKey<String>('music-box-song-attribution-name'),
                textAlign: TextAlign.right,
                style: style,
                softWrap: true,
              ),
            ),
          ],
        );
      },
    );
  }
}

class _MusicBoxSongDetailRow extends StatelessWidget {
  const _MusicBoxSongDetailRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 38,
            child: HoverCardSelectableText(
              value: label,
              style: UiTypography.label.copyWith(color: UiColors.textMuted),
            ),
          ),
          Expanded(
            child: HoverCardSelectableText(
              value: value,
              textAlign: TextAlign.right,
              style: UiTypography.label.copyWith(color: UiColors.text),
              maxLines: 12,
            ),
          ),
        ],
      ),
    );
  }
}

final RegExp _musicBoxBvidPattern = RegExp(
  r'BV[0-9A-Za-z]+',
  caseSensitive: false,
);

String _musicBoxBilibiliBvId(String trackId) {
  final raw = trackId.trim();
  final match = _musicBoxBvidPattern.firstMatch(raw)?.group(0);
  if (match == null || match.length <= 2) return raw.isEmpty ? '未知' : raw;
  return 'BV${match.substring(2)}';
}

class _MusicBoxAdaptiveFullText extends StatelessWidget {
  const _MusicBoxAdaptiveFullText({
    super.key,
    required this.text,
    required this.style,
  });

  final String text;
  final TextStyle style;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final baseSize = style.fontSize ?? 16;
        final painter = TextPainter(
          text: TextSpan(text: text, style: style),
          textDirection: Directionality.of(context),
          textScaler: MediaQuery.textScalerOf(context),
        )..layout(maxWidth: constraints.maxWidth);
        final lineCount = painter.computeLineMetrics().length;
        final reduction = lineCount <= 2
            ? 0.0
            : (lineCount - 2).clamp(1, 4).toDouble();
        final fontSize = (baseSize - reduction).clamp(11.0, baseSize);
        return HoverCardSelectableText(
          value: text,
          style: style.copyWith(fontSize: fontSize),
          maxLines: lineCount < 1 ? 1 : lineCount,
        );
      },
    );
  }
}

/// A Listener-based [ButtonIcon] nested in [HoverCardAnchor] would otherwise
/// also reach the anchor's tap recognizer. This local gesture winner preserves
/// the direct action without opening the surrounding playlist card.
class _MusicBoxHoverCardActionGuard extends StatelessWidget {
  const _MusicBoxHoverCardActionGuard({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      onTap: () {},
      child: child,
    );
  }
}

class _MusicBoxSearchList extends StatelessWidget {
  const _MusicBoxSearchList({
    required this.results,
    required this.query,
    required this.searching,
    required this.error,
    required this.hasQuery,
    required this.controller,
    required this.roomId,
    required this.temporaryQueue,
    required this.onQueueResult,
  });

  final List<MusicBoxSearchResult> results;
  final String query;
  final bool searching;
  final String? error;
  final bool hasQuery;
  final MusicBoxController? controller;
  final String? roomId;
  final List<MusicBoxQueueItem> temporaryQueue;
  final ValueChanged<MusicBoxSearchResult> onQueueResult;

  @override
  Widget build(BuildContext context) {
    final message = error?.trim();
    if (!hasQuery) {
      return const _MusicBoxEmpty(icon: Icons.search, message: '搜索歌曲点歌吧');
    }
    if (searching && results.isEmpty) {
      return const Center(
        child: SizedBox(
          width: 22,
          height: 22,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
      );
    }
    if (message != null && message.isNotEmpty) {
      return FloatingNoticeEmitter(
        notices: [
          FloatingNotice(
            message: message,
            tone: FloatingNoticeTone.error,
            duration: null,
          ),
        ],
        child: const _MusicBoxEmpty(icon: Icons.error_outline, message: '搜索失败'),
      );
    }
    if (results.isEmpty) {
      return const _MusicBoxEmpty(icon: Icons.search_off, message: '没有找到相关歌曲');
    }
    return ListView.separated(
      key: const ValueKey<String>('music-box-search-results-list'),
      padding: EdgeInsets.zero,
      itemCount: results.length,
      separatorBuilder: (_, _) => const SizedBox(height: 8),
      itemBuilder: (context, index) {
        final result = results[index];
        final alreadyInRequestQueue = music_box_display
            .musicBoxRequestQueueContainsTrack(
              temporaryQueue,
              source: result.source,
              trackId: result.trackId,
            );
        return _MusicBoxTrackTile(
          keyScope: 'search',
          result: result,
          query: query,
          controller: controller,
          roomId: roomId,
          alreadyInRequestQueue: alreadyInRequestQueue,
          onQueue: () => onQueueResult(result),
        );
      },
    );
  }
}

class _MusicBoxTrackTile extends StatelessWidget {
  const _MusicBoxTrackTile({
    required this.keyScope,
    required this.result,
    required this.query,
    required this.controller,
    required this.roomId,
    required this.alreadyInRequestQueue,
    required this.onQueue,
    this.durationMs,
    this.actionIcon,
    this.actionTooltip,
    this.actionLoading = false,
    this.onAction,
  });

  final String keyScope;
  final MusicBoxSearchResult result;
  final String query;
  final MusicBoxController? controller;
  final String? roomId;
  final bool alreadyInRequestQueue;
  final VoidCallback onQueue;
  final int? durationMs;
  final IconData? actionIcon;
  final String? actionTooltip;
  final bool actionLoading;
  final VoidCallback? onAction;

  void _queue(BuildContext context) {
    if (alreadyInRequestQueue) {
      showFloatingNotice(context, '已在队列中');
      return;
    }
    onQueue();
  }

  @override
  Widget build(BuildContext context) {
    final artists = music_box_display.musicBoxArtistsLabel(result.artists);
    final artistLabel = artists.isEmpty ? '未知艺人' : artists;
    const titleStyle = TextStyle(
      color: UiColors.text,
      fontSize: 13,
      fontWeight: FontWeight.w500,
    );
    const subtitleStyle = TextStyle(
      color: UiColors.textSecondary,
      fontSize: 11,
      fontWeight: FontWeight.w600,
    );
    final identity = '${result.source}:${result.trackId}';
    return LayoutBuilder(
      builder: (context, constraints) {
        final textWidth = (constraints.maxWidth - 20 - 20 - 8 - 4 - 28).clamp(
          24.0,
          double.infinity,
        );
        final tileHeight = _musicBoxAdaptiveSongRowHeight(
          context,
          width: textWidth,
          title: result.name,
          subtitle: artistLabel,
          // Search highlights use a heavier span which can wrap one line sooner
          // than the base style. Measure conservatively so the real rich text
          // always receives enough height.
          titleStyle: titleStyle.copyWith(fontWeight: FontWeight.w700),
          subtitleStyle: subtitleStyle.copyWith(fontWeight: FontWeight.w700),
          contentScale: 1.4,
        );
        return PressableSurface(
          key: ValueKey<String>('music-box-$keyScope-tile:$identity'),
          width: double.infinity,
          height: tileHeight,
          hoverLift: 2,
          baseDepth: 4,
          interactive: false,
          hoverEffect: false,
          pressEffect: false,
          backgroundColor: UiColors.surfaceLow,
          borderColor: UiColors.border,
          borderRadius: UiRadii.md,
          padding: const EdgeInsets.symmetric(horizontal: 10),
          child: Row(
            children: [
              Expanded(
                child: HoverCardAnchor(
                  resetKey: '$identity:$alreadyInRequestQueue',
                  cardWidth: 310,
                  cardBuilder: (_) => _MusicBoxSongCard.search(
                    result: result,
                    controller: controller,
                    roomId: roomId,
                    onQueueResult: (_) => _queue(context),
                    alreadyInRequestQueue: alreadyInRequestQueue,
                    catalogDurationMs: durationMs,
                  ),
                  child: SizedBox(
                    height: tileHeight,
                    child: Row(
                      children: [
                        const Icon(
                          Icons.music_note,
                          size: 20,
                          color: UiColors.accent,
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              HighlightedText(
                                text: result.name,
                                query: query,
                                style: titleStyle,
                              ),
                              const SizedBox(height: 2),
                              HighlightedText(
                                text: artistLabel,
                                query: query,
                                style: subtitleStyle,
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 4),
              ButtonIcon(
                key: ValueKey<String>('music-box-$keyScope-add:$identity'),
                icon: Icon(actionIcon ?? Icons.add),
                tooltip: actionTooltip ?? '加入点歌队列',
                tone: ButtonTone.primary,
                loading: actionLoading,
                onPressed: onAction ?? () => _queue(context),
                size: 28,
              ),
            ],
          ),
        );
      },
    );
  }
}

class _MusicBoxEmpty extends StatelessWidget {
  const _MusicBoxEmpty({
    required this.icon,
    required this.message,
    this.actionKey,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String message;
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
