part of 'live_channel_pane.dart';

/// Minimum height retained by the docked console. When the live controls need
/// an extra row, the panel extends upward instead of making the whole console
/// scroll.
const double _musicBoxMinComfortableHeight = 400;

/// The search field / queue-toggle button height — slimmer than the app-wide
/// [Input.defaultHeight] to keep the docked panel dense.
const double _musicBoxSearchFieldHeight = 30;

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
    this.onStateChanged,
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
  final ValueChanged<MusicBoxState>? onStateChanged;
  final ValueChanged<MusicBoxSearchResult> onQueueResult;
  final ValueChanged<MusicBoxQueueItem> onRemoveItem;
  final ValueChanged<String> onSourceChanged;
  final VoidCallback onClose;

  /// Local listening volume for the music box (0–1), restored from the store.
  final double volume;
  final ValueChanged<double> onVolumeChanged;

  @override
  Widget build(BuildContext context) {
    final preserveAndroidSearchFocus =
        Theme.of(context).platform == TargetPlatform.android;
    final body = _MusicBoxBody(
      key: preserveAndroidSearchFocus
          ? GlobalObjectKey<_MusicBoxBodyState>(searchController)
          : null,
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
      onStateChanged: onStateChanged,
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
            ..._controls(),
            Expanded(child: body),
          ],
        ),
      ),
    );
  }

  // The fixed control stack shared by both the roomy and compact layouts.
  List<Widget> _controls() {
    return [
      _MusicBoxHeader(onClose: onClose),
      const SizedBox(height: 8),
      _MusicBoxNowPlaying(
        state: state,
        onTogglePlayback: onTogglePlayback,
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
        ButtonIcon(icon: const Icon(Icons.close), onPressed: onClose, size: 26),
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
    required this.onSkip,
    required this.onPrevious,
    required this.onModeChanged,
  });

  final MusicBoxState state;
  final VoidCallback onTogglePlayback;
  final VoidCallback onSkip;
  final VoidCallback? onPrevious;
  final ValueChanged<MusicBoxPlaybackMode>? onModeChanged;

  @override
  Widget build(BuildContext context) {
    final current = state.currentItem;
    final spinning = music_box_display.musicBoxRecordSpinning(state);
    final transport = music_box_display.musicBoxPrimaryTransport(state);
    final hasQueue = state.queue.isNotEmpty;
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
              Text(
                current?.title ?? '未在播放',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
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
                    icon: const Icon(Icons.skip_previous),
                    onPressed: canControl && state.playback.canPrevious
                        ? onPrevious
                        : null,
                    size: 30,
                  ),
                  const SizedBox(width: 7),
                  ButtonIcon(
                    icon: Icon(
                      transport ==
                              music_box_display.MusicBoxTransportAction.pause
                          ? Icons.pause
                          : Icons.play_arrow,
                    ),
                    tone: ButtonTone.primary,
                    onPressed: canControl && hasQueue ? onTogglePlayback : null,
                    size: 30,
                  ),
                  const SizedBox(width: 7),
                  ButtonIcon(
                    icon: const Icon(Icons.skip_next),
                    onPressed: canControl && hasQueue && state.playback.canNext
                        ? onSkip
                        : null,
                    size: 30,
                  ),
                  const SizedBox(width: 7),
                  Builder(
                    builder: (context) => ButtonIcon(
                      icon: Icon(_musicBoxModeIcon(state.playback.mode)),
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
/// toggle reveals search and saved-playlist sources without making the queue a
/// peer tab, so closing the picker always returns to what is actually playing.
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
    required this.onStateChanged,
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
  final ValueChanged<MusicBoxState>? onStateChanged;

  @override
  State<_MusicBoxBody> createState() => _MusicBoxBodyState();
}

class _MusicBoxBodyState extends State<_MusicBoxBody> {
  _MusicBoxSection _section = _MusicBoxSection.search;
  bool _showAddSources = false;
  bool _activatingTemporary = false;
  String? _temporaryActivationError;

  void _setShowAddSources(bool value) {
    if (!value) FocusManager.instance.primaryFocus?.unfocus();
    setState(() => _showAddSources = value);
  }

  void _handleActivatedState(MusicBoxState state) {
    widget.onStateChanged?.call(state);
    if (mounted) setState(() => _showAddSources = false);
  }

  Future<void> _activateTemporary() async {
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || roomId == null || _activatingTemporary) return;
    setState(() {
      _activatingTemporary = true;
      _temporaryActivationError = null;
    });
    try {
      final state = await controller.activatePlaylist(
        roomId: roomId,
        sourceType: MusicBoxActiveSourceType.temporary,
      );
      _handleActivatedState(state);
    } catch (_) {
      if (mounted) setState(() => _temporaryActivationError = '切换点歌队列失败');
    } finally {
      if (mounted) setState(() => _activatingTemporary = false);
    }
  }

  Widget _currentQueueView() {
    final isActive =
        widget.state.activeSource.type == MusicBoxActiveSourceType.temporary;
    final list = _MusicBoxQueueList(
      state: widget.state,
      queue: widget.state.queue,
      onRemoveItem: widget.onRemoveItem,
      controller: widget.controller,
      roomId: widget.roomId,
      onStateChanged: widget.onStateChanged,
    );
    if (isActive) return list;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Button(
          icon: const Icon(Icons.playlist_play),
          tone: ButtonTone.primary,
          height: 32,
          loading: _activatingTemporary,
          onPressed: () => unawaited(_activateTemporary()),
          child: Text('切回点歌队列（${widget.state.temporaryQueuedCount}）'),
        ),
        if (_temporaryActivationError case final message?) ...[
          const SizedBox(height: 6),
          Text(
            message,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: UiColors.danger,
              fontSize: 10,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
        const SizedBox(height: 10),
        Expanded(child: list),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final hasQuery = widget.searchController.text.trim().isNotEmpty;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Expanded(
              child: _showAddSources
                  ? SegmentedControl<_MusicBoxSection>(
                      expanded: true,
                      height: _musicBoxSearchFieldHeight,
                      value: _section,
                      segments: const [
                        Segment(value: _MusicBoxSection.search, label: '搜索添加'),
                        Segment(
                          value: _MusicBoxSection.roomPlaylists,
                          label: '房间歌单',
                        ),
                        Segment(
                          value: _MusicBoxSection.myPlaylists,
                          label: '我的歌单',
                        ),
                      ],
                      onChanged: (value) => setState(() => _section = value),
                    )
                  : PressableSurface(
                      key: const ValueKey<String>(
                        'music-box-current-queue-header',
                      ),
                      height: _musicBoxSearchFieldHeight,
                      padding: const EdgeInsets.symmetric(horizontal: 10),
                      backgroundColor: UiColors.surfaceLow,
                      borderColor: UiColors.border,
                      hoverEffect: false,
                      pressEffect: false,
                      child: Row(
                        children: [
                          const Icon(
                            Icons.queue_music,
                            size: 15,
                            color: UiColors.accent,
                          ),
                          const SizedBox(width: 7),
                          Expanded(
                            child: Text(
                              music_box_display.musicBoxActiveSourceLabel(
                                widget.state.activeSource,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                color: UiColors.text,
                                fontSize: 12,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                          Text(
                            '${widget.state.queue.length}',
                            style: const TextStyle(
                              color: UiColors.textMuted,
                              fontSize: 10,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ],
                      ),
                    ),
            ),
            const SizedBox(width: 8),
            ButtonIcon(
              key: const ValueKey<String>('music-box-add-toggle'),
              icon: const Icon(Icons.add),
              tooltip: _showAddSources ? '返回当前队列' : '搜索或选择歌单',
              toggleValue: _showAddSources,
              onToggleChanged: _setShowAddSources,
              size: _musicBoxSearchFieldHeight,
            ),
          ],
        ),
        if (_showAddSources && _section == _MusicBoxSection.search) ...[
          const SizedBox(height: 10),
          Input(
            controller: widget.searchController,
            hintText: '搜索歌曲点歌',
            prefixIcon: Icons.search,
            showClearButton: true,
            maxLines: 1,
            height: _musicBoxSearchFieldHeight,
          ),
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
          child: !_showAddSources
              ? _currentQueueView()
              : switch (_section) {
                  _MusicBoxSection.roomPlaylists => _MusicBoxPlaylistBrowser(
                    controller: widget.controller,
                    roomId: widget.roomId,
                    roomScoped: true,
                    onQueueResult: widget.onQueueResult,
                    onStateChanged: _handleActivatedState,
                  ),
                  _MusicBoxSection.myPlaylists => _MusicBoxPlaylistBrowser(
                    controller: widget.controller,
                    roomId: widget.roomId,
                    roomScoped: false,
                    onQueueResult: widget.onQueueResult,
                    onStateChanged: _handleActivatedState,
                  ),
                  _MusicBoxSection.search => _MusicBoxSearchList(
                    results: widget.searchResults,
                    query: widget.searchController.text,
                    searching: widget.searching,
                    error: widget.searchError,
                    hasQuery: hasQuery,
                    onQueueResult: widget.onQueueResult,
                  ),
                },
        ),
      ],
    );
  }
}

enum _MusicBoxSection { search, roomPlaylists, myPlaylists }

class _MusicBoxPlaylistBrowser extends StatefulWidget {
  const _MusicBoxPlaylistBrowser({
    required this.controller,
    required this.roomId,
    required this.roomScoped,
    required this.onQueueResult,
    required this.onStateChanged,
  });

  final MusicBoxController? controller;
  final String? roomId;
  final bool roomScoped;
  final ValueChanged<MusicBoxSearchResult> onQueueResult;
  final ValueChanged<MusicBoxState>? onStateChanged;

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
                onPressed: () => setState(() {
                  _selected = null;
                  _items = const [];
                }),
                size: 28,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  selected.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: UiColors.text,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
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
                      return _MusicBoxSavedTrackTile(
                        item: item,
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
      );
    }
    return ListView.separated(
      padding: EdgeInsets.zero,
      itemCount: _playlists.length,
      separatorBuilder: (_, _) => const SizedBox(height: 8),
      itemBuilder: (context, index) {
        final playlist = _playlists[index];
        return PressableSurface(
          width: double.infinity,
          height: 50,
          hoverLift: 2,
          baseDepth: 4,
          backgroundColor: UiColors.surfaceLow,
          pressedBackgroundColor: UiColors.surfacePressed,
          borderColor: UiColors.border,
          borderRadius: UiRadii.md,
          padding: const EdgeInsets.symmetric(horizontal: 10),
          onPressed: () => unawaited(_openPlaylist(playlist)),
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
                '${playlist.itemCount} 首',
                style: const TextStyle(
                  color: UiColors.textMuted,
                  fontSize: 10,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(width: 4),
              ButtonIcon(
                icon: const Icon(Icons.play_arrow),
                tooltip: '播放整个歌单',
                tone: ButtonTone.primary,
                loading: _activatingPlaylistId == playlist.id,
                onPressed: () => unawaited(_activatePlaylist(playlist)),
                size: 28,
              ),
              const SizedBox(width: 2),
              const Icon(
                Icons.chevron_right,
                size: 18,
                color: UiColors.textMuted,
              ),
            ],
          ),
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

class _MusicBoxSavedTrackTile extends StatelessWidget {
  const _MusicBoxSavedTrackTile({required this.item, required this.onQueue});

  final PersonalMusicPlaylistItem item;
  final VoidCallback onQueue;

  @override
  Widget build(BuildContext context) {
    return PressableSurface(
      width: double.infinity,
      height: 50,
      hoverLift: 2,
      baseDepth: 4,
      backgroundColor: UiColors.surfaceLow,
      pressedBackgroundColor: UiColors.surfacePressed,
      borderColor: UiColors.border,
      borderRadius: UiRadii.md,
      padding: const EdgeInsets.symmetric(horizontal: 10),
      onPressed: onQueue,
      child: Row(
        children: [
          const Icon(Icons.add, size: 17, color: UiColors.accent),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  item.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: UiColors.text,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                Text(
                  music_box_display.musicBoxArtistsLabel(item.artists),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: UiColors.textMuted,
                    fontSize: 10,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ],
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

class _MusicBoxQueueList extends StatelessWidget {
  const _MusicBoxQueueList({
    required this.state,
    required this.queue,
    required this.onRemoveItem,
    required this.controller,
    required this.roomId,
    required this.onStateChanged,
  });

  final MusicBoxState state;
  final List<MusicBoxQueueItem> queue;
  final ValueChanged<MusicBoxQueueItem> onRemoveItem;
  final MusicBoxController? controller;
  final String? roomId;
  final ValueChanged<MusicBoxState>? onStateChanged;

  @override
  Widget build(BuildContext context) {
    if (queue.isEmpty) {
      return const _MusicBoxEmpty(
        icon: Icons.queue_music,
        message: '当前队列为空，点击 + 添加歌曲',
      );
    }
    return ListView.separated(
      key: const ValueKey<String>('music-box-queue-list'),
      padding: EdgeInsets.zero,
      itemCount: queue.length,
      separatorBuilder: (_, _) => const SizedBox(height: 8),
      itemBuilder: (context, index) {
        final item = queue[index];
        return _MusicBoxQueueTile(
          item: item,
          isCurrent: music_box_display.musicBoxIsCurrent(state, item),
          onRemove: () => onRemoveItem(item),
          controller: controller,
          roomId: roomId,
          currentState: state,
          onStateChanged: onStateChanged,
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
  });

  final MusicBoxQueueItem item;
  final bool isCurrent;
  final VoidCallback onRemove;
  final MusicBoxController? controller;
  final String? roomId;
  final MusicBoxState currentState;
  final ValueChanged<MusicBoxState>? onStateChanged;

  @override
  Widget build(BuildContext context) {
    final statusLabel = music_box_display.musicBoxQueueStatusLabel(item);
    final failed = item.status == MusicBoxQueueItemStatus.failed;
    final loading =
        item.status == MusicBoxQueueItemStatus.pending ||
        item.status == MusicBoxQueueItemStatus.downloading;
    final sourceLabel = music_box_display.musicBoxSourceLabel(item.source);
    return PressableSurface(
      width: double.infinity,
      height: 50,
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
              ),
              cardWidth: 310,
              cardBuilder: (_) => _MusicBoxSongCard(
                item: item,
                isCurrent: isCurrent,
                controller: controller,
                roomId: roomId,
                currentState: currentState,
                onStateChanged: onStateChanged,
              ),
              child: SizedBox(
                height: 50,
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
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text(
                            item.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: isCurrent
                                  ? UiColors.accent
                                  : UiColors.text,
                              fontSize: 13,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Row(
                            children: [
                              if (loading) ...[
                                const SizedBox(
                                  width: 10,
                                  height: 10,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 1.6,
                                  ),
                                ),
                                const SizedBox(width: 6),
                              ],
                              Expanded(
                                child: Text(
                                  [
                                    if (item.artist.trim().isNotEmpty)
                                      item.artist.trim(),
                                    sourceLabel,
                                    ?statusLabel,
                                  ].join(' · '),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                    color: failed
                                        ? UiColors.danger
                                        : UiColors.textSecondary,
                                    fontSize: 11,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                    if (item.durationMs > 0) ...[
                      const SizedBox(width: 8),
                      Text(
                        music_box_display.musicBoxFormatDuration(
                          item.durationMs,
                        ),
                        style: const TextStyle(
                          color: UiColors.textMuted,
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(width: 4),
          ButtonIcon(
            icon: const Icon(Icons.close),
            tone: ButtonTone.danger,
            onPressed: onRemove,
            size: 28,
          ),
        ],
      ),
    );
  }
}

class _MusicBoxSongCard extends StatefulWidget {
  const _MusicBoxSongCard({
    required this.item,
    required this.isCurrent,
    required this.controller,
    required this.roomId,
    required this.currentState,
    required this.onStateChanged,
  });

  final MusicBoxQueueItem item;
  final bool isCurrent;
  final MusicBoxController? controller;
  final String? roomId;
  final MusicBoxState currentState;
  final ValueChanged<MusicBoxState>? onStateChanged;

  @override
  State<_MusicBoxSongCard> createState() => _MusicBoxSongCardState();
}

class _MusicBoxSongCardState extends State<_MusicBoxSongCard> {
  bool _playingNow = false;
  bool _showPlaylistPicker = false;
  bool _loadingPlaylists = false;
  String? _addingPlaylistId;
  String? _message;
  List<_MusicBoxPlaylistTarget> _playlistTargets = const [];

  Future<void> _playNow() async {
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || roomId == null || _playingNow) return;
    setState(() {
      _playingNow = true;
      _message = null;
    });
    try {
      final state = await controller.playNow(
        roomId: roomId,
        item: widget.item,
        currentState: widget.currentState,
      );
      widget.onStateChanged?.call(state);
      if (mounted) setState(() => _message = '已优先播放');
    } catch (_) {
      if (mounted) setState(() => _message = '优先播放失败，请重试');
    } finally {
      if (mounted) setState(() => _playingNow = false);
    }
  }

  Future<void> _openPlaylistPicker() async {
    final controller = widget.controller;
    if (controller == null || _loadingPlaylists) return;
    setState(() {
      _showPlaylistPicker = true;
      _loadingPlaylists = true;
      _message = null;
      _playlistTargets = const [];
    });
    final targets = <_MusicBoxPlaylistTarget>[];
    var failed = false;
    try {
      final roomId = widget.roomId;
      if (roomId != null) {
        final page = await controller.loadRoomPlaylists(roomId: roomId);
        targets.addAll(
          page.playlists.map(
            (playlist) =>
                _MusicBoxPlaylistTarget(playlist: playlist, roomScoped: true),
          ),
        );
      }
    } catch (_) {
      failed = true;
    }
    try {
      final page = await controller.loadMyPlaylists();
      targets.addAll(
        page.playlists.map(
          (playlist) =>
              _MusicBoxPlaylistTarget(playlist: playlist, roomScoped: false),
        ),
      );
    } catch (_) {
      failed = true;
    }
    if (!mounted) return;
    setState(() {
      _loadingPlaylists = false;
      _playlistTargets = targets;
      if (targets.isEmpty && failed) _message = '加载歌单失败，请重试';
    });
  }

  Future<void> _addToPlaylist(_MusicBoxPlaylistTarget target) async {
    final controller = widget.controller;
    if (controller == null || _addingPlaylistId != null) return;
    setState(() {
      _addingPlaylistId = target.playlist.id;
      _message = null;
    });
    try {
      if (target.roomScoped) {
        final roomId = widget.roomId;
        if (roomId == null) return;
        await controller.addQueueItemToRoomPlaylist(
          roomId: roomId,
          playlistId: target.playlist.id,
          item: widget.item,
        );
      } else {
        await controller.addQueueItemToMyPlaylist(
          playlistId: target.playlist.id,
          item: widget.item,
        );
      }
      if (!mounted) return;
      setState(() {
        _showPlaylistPicker = false;
        _message = '已添加到「${target.playlist.name}」';
      });
    } catch (_) {
      if (mounted) setState(() => _message = '添加失败，请检查歌单权限');
    } finally {
      if (mounted) setState(() => _addingPlaylistId = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final item = widget.item;
    final requester = item.requestedBy;
    final canPlayNow =
        item.canPlayNow &&
        item.status == MusicBoxQueueItemStatus.ready &&
        widget.controller != null &&
        widget.roomId != null;
    return Padding(
      key: ValueKey<String>('music-box-song-card:${item.id}'),
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
                child: Text(
                  item.title,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: UiTypography.title.copyWith(fontSize: 16),
                ),
              ),
            ],
          ),
          const SizedBox(height: UiSpacing.md),
          _MusicBoxSongDetailRow(
            label: '时长',
            value: music_box_display.musicBoxFormatDuration(item.durationMs),
          ),
          _MusicBoxSongDetailRow(
            label: '歌手',
            value: item.artist.trim().isEmpty ? '未知艺人' : item.artist.trim(),
          ),
          _MusicBoxSongDetailRow(
            label: '来源',
            value: music_box_display.musicBoxSourceLabel(item.source),
          ),
          const SizedBox(height: UiSpacing.sm),
          Row(
            children: [
              Text(
                '点歌人',
                style: UiTypography.label.copyWith(color: UiColors.textMuted),
              ),
              const Spacer(),
              if (requester != null) ...[
                Avatar(
                  label: requester.avatarLabel,
                  imageUrl: requester.avatarUrl,
                  defaultAvatarKey: requester.defaultAvatarKey,
                  size: 26,
                  showBorder: false,
                ),
                const SizedBox(width: 7),
                Flexible(
                  child: Text(
                    requester.displayName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: UiTypography.label.copyWith(color: UiColors.text),
                  ),
                ),
              ] else
                Text(
                  '歌单',
                  style: UiTypography.label.copyWith(color: UiColors.text),
                ),
            ],
          ),
          const SizedBox(height: UiSpacing.md),
          if (!_showPlaylistPicker)
            Row(
              children: [
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
            )
          else ...[
            Row(
              children: [
                ButtonIcon(
                  icon: const Icon(Icons.arrow_back),
                  size: 28,
                  onPressed: () => setState(() => _showPlaylistPicker = false),
                ),
                const SizedBox(width: 8),
                const Text(
                  '选择歌单',
                  style: TextStyle(
                    color: UiColors.text,
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            if (_loadingPlaylists)
              const SizedBox(
                height: 72,
                child: Center(child: CircularProgressIndicator()),
              )
            else if (_playlistTargets.isEmpty)
              const SizedBox(
                height: 54,
                child: Center(
                  child: Text(
                    '还没有可用歌单',
                    style: TextStyle(color: UiColors.textMuted, fontSize: 11),
                  ),
                ),
              )
            else
              SizedBox(
                height: (_playlistTargets.length * 38.0).clamp(38.0, 160.0),
                child: ListView.separated(
                  padding: EdgeInsets.zero,
                  itemCount: _playlistTargets.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 6),
                  itemBuilder: (context, index) {
                    final target = _playlistTargets[index];
                    return Button(
                      icon: Icon(
                        target.roomScoped ? Icons.meeting_room : Icons.person,
                      ),
                      height: 32,
                      loading: _addingPlaylistId == target.playlist.id,
                      onPressed: _addingPlaylistId == null
                          ? () => unawaited(_addToPlaylist(target))
                          : null,
                      mainAxisSize: MainAxisSize.max,
                      child: Text(
                        '${target.roomScoped ? '房间' : '我的'} · '
                        '${target.playlist.name}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    );
                  },
                ),
              ),
          ],
          if (_message case final message?) ...[
            const SizedBox(height: 8),
            Text(
              message,
              textAlign: TextAlign.center,
              style: TextStyle(
                color: message.contains('失败')
                    ? UiColors.danger
                    : UiColors.accent,
                fontSize: 11,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ],
      ),
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
            child: Text(
              label,
              style: UiTypography.label.copyWith(color: UiColors.textMuted),
            ),
          ),
          Expanded(
            child: Text(
              value,
              textAlign: TextAlign.right,
              style: UiTypography.label.copyWith(color: UiColors.text),
            ),
          ),
        ],
      ),
    );
  }
}

class _MusicBoxPlaylistTarget {
  const _MusicBoxPlaylistTarget({
    required this.playlist,
    required this.roomScoped,
  });

  final PersonalMusicPlaylist playlist;
  final bool roomScoped;
}

class _MusicBoxSearchList extends StatelessWidget {
  const _MusicBoxSearchList({
    required this.results,
    required this.query,
    required this.searching,
    required this.error,
    required this.hasQuery,
    required this.onQueueResult,
  });

  final List<MusicBoxSearchResult> results;
  final String query;
  final bool searching;
  final String? error;
  final bool hasQuery;
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
        return _MusicBoxSearchTile(
          result: result,
          query: query,
          onQueue: () => onQueueResult(result),
        );
      },
    );
  }
}

class _MusicBoxSearchTile extends StatelessWidget {
  const _MusicBoxSearchTile({
    required this.result,
    required this.query,
    required this.onQueue,
  });

  final MusicBoxSearchResult result;
  final String query;
  final VoidCallback onQueue;

  @override
  Widget build(BuildContext context) {
    final artists = music_box_display.musicBoxArtistsLabel(result.artists);
    return PressableSurface(
      width: double.infinity,
      height: 50,
      hoverLift: 2,
      baseDepth: 4,
      backgroundColor: UiColors.surfaceLow,
      pressedBackgroundColor: UiColors.surfacePressed,
      borderColor: UiColors.border,
      borderRadius: UiRadii.md,
      padding: const EdgeInsets.symmetric(horizontal: 10),
      onPressed: onQueue,
      child: Row(
        children: [
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                HighlightedText(
                  text: result.name,
                  query: query,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: UiColors.text,
                    fontSize: 13,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                const SizedBox(height: 2),
                HighlightedText(
                  text: artists.isEmpty ? '未知艺人' : artists,
                  query: query,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: UiColors.textSecondary,
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _MusicBoxEmpty extends StatelessWidget {
  const _MusicBoxEmpty({required this.icon, required this.message});

  final IconData icon;
  final String message;

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
          ],
        ),
      ),
    );
  }
}
