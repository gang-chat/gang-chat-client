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
      _MusicBoxHeader(usage: state.usage, onClose: onClose),
      const SizedBox(height: 8),
      _MusicBoxNowPlaying(
        state: state,
        onTogglePlayback: onTogglePlayback,
        onSkip: onSkip,
      ),
      const SizedBox(height: 7),
      _MusicBoxVolume(initialVolume: volume, onChanged: onVolumeChanged),
      const SizedBox(height: 9),
    ];
  }
}

class _MusicBoxHeader extends StatelessWidget {
  const _MusicBoxHeader({required this.usage, required this.onClose});

  final MusicBoxUsage usage;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final hint = music_box_display.musicBoxUsageHint(usage);
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
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            hint ?? music_box_display.musicBoxUsageLabel(usage),
            style: TextStyle(
              color: hint == null ? UiColors.textMuted : UiColors.amber,
              fontSize: 11,
              fontWeight: FontWeight.w600,
            ),
            overflow: TextOverflow.ellipsis,
          ),
        ),
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
  });

  final MusicBoxState state;
  final VoidCallback onTogglePlayback;
  final VoidCallback onSkip;

  @override
  Widget build(BuildContext context) {
    final current = state.currentItem;
    final spinning = music_box_display.musicBoxRecordSpinning(state);
    final transport = music_box_display.musicBoxPrimaryTransport(state);
    final hasQueue = state.queue.isNotEmpty;

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
                children: [
                  ButtonIcon(
                    icon: Icon(
                      transport ==
                              music_box_display.MusicBoxTransportAction.pause
                          ? Icons.pause
                          : Icons.play_arrow,
                    ),
                    tone: ButtonTone.primary,
                    onPressed: hasQueue ? onTogglePlayback : null,
                    size: 30,
                  ),
                  const SizedBox(width: 8),
                  ButtonIcon(
                    icon: const Icon(Icons.skip_next),
                    onPressed: hasQueue ? onSkip : null,
                    size: 30,
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
    if (_muted) return Icons.volume_off;
    if (_volume < 0.5) return Icons.volume_down;
    return Icons.volume_up;
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

/// Tabbed lower body: the queue, plus a search field that adds hits to it.
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

  @override
  State<_MusicBoxBody> createState() => _MusicBoxBodyState();
}

class _MusicBoxBodyState extends State<_MusicBoxBody> {
  // The body shows search by default; the toggle beside the search box flips to
  // the play queue and back.
  bool _showQueue = false;

  @override
  Widget build(BuildContext context) {
    final hasQuery = widget.searchController.text.trim().isNotEmpty;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Input(
                controller: widget.searchController,
                hintText: '搜索歌曲点歌',
                prefixIcon: Icons.search,
                showClearButton: true,
                maxLines: 1,
                height: _musicBoxSearchFieldHeight,
              ),
            ),
            const SizedBox(width: 8),
            ButtonIcon(
              icon: const Icon(Icons.queue_music),
              selected: _showQueue,
              onPressed: () => setState(() => _showQueue = !_showQueue),
              size: _musicBoxSearchFieldHeight,
            ),
          ],
        ),
        // The source picker only applies to search, so hide it on the queue tab.
        if (!_showQueue) ...[
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
          child: _showQueue
              ? _MusicBoxQueueList(
                  state: widget.state,
                  onRemoveItem: widget.onRemoveItem,
                )
              : _MusicBoxSearchList(
                  results: widget.searchResults,
                  query: widget.searchController.text,
                  searching: widget.searching,
                  error: widget.searchError,
                  hasQuery: hasQuery,
                  onQueueResult: widget.onQueueResult,
                ),
        ),
      ],
    );
  }
}

class _MusicBoxQueueList extends StatelessWidget {
  const _MusicBoxQueueList({required this.state, required this.onRemoveItem});

  final MusicBoxState state;
  final ValueChanged<MusicBoxQueueItem> onRemoveItem;

  @override
  Widget build(BuildContext context) {
    final queue = state.queue;
    if (queue.isEmpty) {
      return const _MusicBoxEmpty(
        icon: Icons.queue_music,
        message: '队列空空如也，搜索点歌吧',
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
  });

  final MusicBoxQueueItem item;
  final bool isCurrent;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    final statusLabel = music_box_display.musicBoxQueueStatusLabel(item);
    final failed = item.status == MusicBoxQueueItemStatus.failed;
    final loading =
        item.status == MusicBoxQueueItemStatus.pending ||
        item.status == MusicBoxQueueItemStatus.downloading;
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
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  item.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: isCurrent ? UiColors.accent : UiColors.text,
                    fontSize: 13,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                const SizedBox(height: 2),
                Row(
                  children: [
                    if (statusLabel != null) ...[
                      if (loading)
                        const SizedBox(
                          width: 10,
                          height: 10,
                          child: CircularProgressIndicator(strokeWidth: 1.6),
                        ),
                      if (loading) const SizedBox(width: 6),
                      Flexible(
                        child: Text(
                          statusLabel,
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
                    ] else
                      Expanded(
                        child: Text(
                          item.artist.isEmpty ? '未知艺人' : item.artist,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: UiColors.textSecondary,
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
              music_box_display.musicBoxFormatDuration(item.durationMs),
              style: const TextStyle(
                color: UiColors.textMuted,
                fontSize: 11,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
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
