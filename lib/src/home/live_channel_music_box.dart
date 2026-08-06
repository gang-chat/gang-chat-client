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
      room: room,
      previewPlatformFactory: previewPlatformFactory,
      onStateChanged: onStateChanged,
      currentUser: currentUser,
      onResolveUserProfile: onResolveUserProfile,
      onResolveRoomProfile: onResolveRoomProfile,
      onEnterCommonRoom: onEnterCommonRoom,
      userProfileActionBuilder: userProfileActionBuilder,
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
    required this.room,
    required this.previewPlatformFactory,
    required this.onStateChanged,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
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

  @override
  State<_MusicBoxBody> createState() => _MusicBoxBodyState();
}

class _MusicBoxBodyState extends State<_MusicBoxBody> {
  _MusicBoxSection _section = _MusicBoxSection.search;
  bool _showAddSources = false;
  bool _activatingTemporary = false;
  bool _clearingTemporary = false;
  PersonalMusicPlaylist? _requestedPlaylist;
  bool? _requestedPlaylistRoomScoped;

  void _setShowAddSources(bool value) {
    if (!value) FocusManager.instance.primaryFocus?.unfocus();
    setState(() => _showAddSources = value);
  }

  void _handleActivatedState(MusicBoxState state) {
    widget.onStateChanged?.call(state);
    if (mounted) setState(() => _showAddSources = false);
  }

  Future<void> _viewPlaylist(
    PersonalMusicPlaylist playlist,
    bool roomScoped,
  ) async {
    FocusManager.instance.primaryFocus?.unfocus();
    if (!mounted) return;
    setState(() {
      _requestedPlaylist = playlist;
      _requestedPlaylistRoomScoped = roomScoped;
      _section = roomScoped
          ? _MusicBoxSection.roomPlaylists
          : _MusicBoxSection.myPlaylists;
      _showAddSources = true;
    });
  }

  void _clearRequestedPlaylist() {
    if (_requestedPlaylist == null && _requestedPlaylistRoomScoped == null) {
      return;
    }
    setState(() {
      _requestedPlaylist = null;
      _requestedPlaylistRoomScoped = null;
    });
  }

  MusicPlaylistCardData _currentPlaylistCardData() {
    final source = widget.state.activeSource;
    final owner = _musicBoxActiveSourceOwner(source, widget.currentUser);
    return MusicPlaylistCardData(
      id: source.type == MusicBoxActiveSourceType.temporary
          ? 'temporary:${widget.roomId ?? ''}'
          : source.id,
      name: music_box_display.musicBoxActiveSourceLabel(source),
      songCount: widget.state.queue.length,
      createdAt: source.createdAt,
      creator: source.type == MusicBoxActiveSourceType.userPlaylist
          ? owner
          : null,
      room: source.type == MusicBoxActiveSourceType.userPlaylist
          ? null
          : widget.room,
      showPlayingStatus: true,
    );
  }

  MusicPlaylistCardResolver? _currentPlaylistResolver() {
    final controller = widget.controller;
    final source = widget.state.activeSource;
    if (controller == null || source.id.isEmpty) return null;
    if (source.type == MusicBoxActiveSourceType.roomPlaylist) {
      final roomId = widget.roomId;
      if (roomId == null) return null;
      return (current) async {
        final page = await controller.loadRoomPlaylists(roomId: roomId);
        final playlist = _musicBoxPlaylistById(page.playlists, source.id);
        if (playlist == null) return current;
        return current.copyWith(
          name: playlist.name,
          songCount: playlist.itemCount,
          createdAt: playlist.createdAt,
        );
      };
    }
    if (source.type == MusicBoxActiveSourceType.userPlaylist &&
        (source.ownerUserId.isEmpty ||
            source.ownerUserId == widget.currentUser?.id)) {
      return (current) async {
        final page = await controller.loadMyPlaylists();
        final playlist = _musicBoxPlaylistById(page.playlists, source.id);
        if (playlist == null) return current;
        return current.copyWith(
          name: playlist.name,
          songCount: playlist.itemCount,
          createdAt: playlist.createdAt,
        );
      };
    }
    return null;
  }

  Future<void> _playCurrentPlaylist() async {
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || roomId == null) return;
    final source = widget.state.activeSource;
    try {
      final state = await controller.activatePlaylist(
        roomId: roomId,
        sourceType: source.type,
        playlistId: source.type == MusicBoxActiveSourceType.temporary
            ? null
            : source.id,
      );
      _handleActivatedState(state);
    } catch (error) {
      if (mounted) {
        showFloatingErrorNotice(
          context,
          musicBoxControlErrorMessage(error, '播放歌单失败，请重试'),
        );
      }
    }
  }

  Future<void> _viewCurrentPlaylist() async {
    final source = widget.state.activeSource;
    if (source.type == MusicBoxActiveSourceType.temporary ||
        source.id.isEmpty) {
      return;
    }
    final isAnotherUsersPlaylist =
        source.type == MusicBoxActiveSourceType.userPlaylist &&
        source.ownerUserId.isNotEmpty &&
        source.ownerUserId != widget.currentUser?.id;
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (isAnotherUsersPlaylist && controller != null && roomId != null) {
      final snapshotItems = List<MusicBoxQueueItem>.unmodifiable(
        widget.state.queue,
      );
      unawaited(
        showDialog<void>(
          context: context,
          builder: (context) => _ActiveMusicPlaylistDialog(
            source: source,
            items: snapshotItems,
            controller: controller,
            roomId: roomId,
            previewPlatformFactory: widget.previewPlatformFactory,
          ),
        ),
      );
      return;
    }
    await _viewPlaylist(
      PersonalMusicPlaylist(
        id: source.id,
        name: music_box_display.musicBoxActiveSourceLabel(source),
        description: '',
        revision: 0,
        itemCount: widget.state.queue.length,
        createdAt: source.createdAt,
        updatedAt: null,
      ),
      source.type == MusicBoxActiveSourceType.roomPlaylist,
    );
  }

  Future<void> _activateTemporary() async {
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || roomId == null || _activatingTemporary) return;
    setState(() => _activatingTemporary = true);
    try {
      final state = await controller.activatePlaylist(
        roomId: roomId,
        sourceType: MusicBoxActiveSourceType.temporary,
      );
      _handleActivatedState(state);
    } catch (error) {
      if (mounted) {
        showFloatingErrorNotice(
          context,
          musicBoxControlErrorMessage(error, '切换点歌队列失败，请重试'),
        );
      }
    } finally {
      if (mounted) setState(() => _activatingTemporary = false);
    }
  }

  Future<void> _confirmClearTemporary() async {
    final controller = widget.controller;
    final roomId = widget.roomId;
    final count = widget.state.temporaryQueuedCount;
    if (controller == null ||
        roomId == null ||
        count == 0 ||
        _clearingTemporary) {
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => _MusicBoxClearQueueConfirmDialog(itemCount: count),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _clearingTemporary = true);
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
    } finally {
      if (mounted) setState(() => _clearingTemporary = false);
    }
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

  @override
  Widget build(BuildContext context) {
    final hasQuery = widget.searchController.text.trim().isNotEmpty;
    final isTemporaryActive =
        widget.state.activeSource.type == MusicBoxActiveSourceType.temporary;
    final temporaryQueueEmpty = widget.state.temporaryQueuedCount == 0;
    return MusicPlaylistCardHostScope(
      currentUser: widget.currentUser,
      room: widget.room,
      onResolveUserProfile: widget.onResolveUserProfile,
      onResolveRoomProfile: widget.onResolveRoomProfile,
      onEnterCommonRoom: widget.onEnterCommonRoom,
      userProfileActionBuilder: widget.userProfileActionBuilder,
      onStateChanged: widget.onStateChanged,
      onViewPlaylist: _viewPlaylist,
      child: Column(
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
                          Segment(
                            value: _MusicBoxSection.search,
                            label: '搜索添加',
                          ),
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
                    : isTemporaryActive
                    ? _MusicBoxCurrentSourceHeader(state: widget.state)
                    : MusicPlaylistHoverCard(
                        data: _currentPlaylistCardData(),
                        resolveData: _currentPlaylistResolver(),
                        currentUser: widget.currentUser,
                        onResolveUserProfile: widget.onResolveUserProfile,
                        onResolveRoomProfile: widget.onResolveRoomProfile,
                        onEnterCommonRoom: widget.onEnterCommonRoom,
                        userProfileActionBuilder:
                            widget.userProfileActionBuilder,
                        onPlayAll:
                            widget.controller == null || widget.roomId == null
                            ? null
                            : _playCurrentPlaylist,
                        onViewPlaylist: _viewCurrentPlaylist,
                        child: _MusicBoxCurrentSourceHeader(
                          state: widget.state,
                        ),
                      ),
              ),
              const SizedBox(width: 8),
              if (!_showAddSources) ...[
                ButtonIcon(
                  key: const ValueKey<String>('music-box-queue-context-action'),
                  icon: Icon(
                    isTemporaryActive
                        ? Icons.delete_sweep_outlined
                        : Icons.playlist_play,
                  ),
                  tooltip: isTemporaryActive ? '清空点歌队列' : '切回点歌队列',
                  tone: isTemporaryActive
                      ? ButtonTone.danger
                      : ButtonTone.neutral,
                  loading: isTemporaryActive
                      ? _clearingTemporary
                      : _activatingTemporary,
                  onPressed: isTemporaryActive
                      ? temporaryQueueEmpty
                            ? null
                            : () => unawaited(_confirmClearTemporary())
                      : () => unawaited(_activateTemporary()),
                  size: _musicBoxSearchFieldHeight,
                ),
                const SizedBox(width: 8),
              ],
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
                      initialPlaylist: _requestedPlaylistRoomScoped == true
                          ? _requestedPlaylist
                          : null,
                      onPlaylistClosed: _clearRequestedPlaylist,
                    ),
                    _MusicBoxSection.myPlaylists => _MusicBoxPlaylistBrowser(
                      controller: widget.controller,
                      roomId: widget.roomId,
                      roomScoped: false,
                      onQueueResult: widget.onQueueResult,
                      onStateChanged: _handleActivatedState,
                      initialPlaylist: _requestedPlaylistRoomScoped == false
                          ? _requestedPlaylist
                          : null,
                      onPlaylistClosed: _clearRequestedPlaylist,
                    ),
                    _MusicBoxSection.search => _MusicBoxSearchList(
                      results: widget.searchResults,
                      query: widget.searchController.text,
                      searching: widget.searching,
                      error: widget.searchError,
                      hasQuery: hasQuery,
                      controller: widget.controller,
                      roomId: widget.roomId,
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
                  return _ActiveMusicPlaylistTrackTile(
                    item: item,
                    previewController: _previewController,
                    playlists: _myPlaylists,
                    onAddToPlaylist: (playlist) =>
                        _addToPlaylist(item, playlist),
                  );
                },
              ),
      ),
    );
  }
}

class _ActiveMusicPlaylistTrackTile extends StatelessWidget {
  const _ActiveMusicPlaylistTrackTile({
    required this.item,
    required this.previewController,
    required this.playlists,
    required this.onAddToPlaylist,
  });

  final MusicBoxQueueItem item;
  final MusicTrackPreviewController? previewController;
  final List<PersonalMusicPlaylist> playlists;
  final Future<void> Function(PersonalMusicPlaylist playlist) onAddToPlaylist;

  @override
  Widget build(BuildContext context) {
    final artists = item.artist
        .split(RegExp(r'[、,，]'))
        .map((value) => value.trim())
        .where((value) => value.isNotEmpty)
        .toList(growable: false);
    final subtitle = [
      if (artists.isNotEmpty) artists.join('、'),
      music_box_display.musicBoxSourceLabel(item.source),
    ].join(' · ');
    final surface = LayoutBuilder(
      builder: (context, constraints) {
        const titleStyle = TextStyle(
          color: UiColors.text,
          fontSize: 13,
          fontWeight: FontWeight.w600,
        );
        const subtitleStyle = TextStyle(
          color: UiColors.textMuted,
          fontSize: 12,
        );
        final textWidth = (constraints.maxWidth - 58).clamp(
          40.0,
          double.infinity,
        );
        final adaptiveTitle = _musicBoxAdaptiveListTextStyle(
          context,
          text: item.title,
          baseStyle: titleStyle,
          width: textWidth,
        );
        final adaptiveSubtitle = _musicBoxAdaptiveListTextStyle(
          context,
          text: subtitle,
          baseStyle: subtitleStyle,
          width: textWidth,
        );
        return MusicPlaylistTrackSurface(
          key: ValueKey<String>('active-music-playlist-track:${item.id}'),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Row(
              children: [
                const Icon(Icons.music_note, size: 19, color: UiColors.accent),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(item.title, style: adaptiveTitle, softWrap: true),
                      const SizedBox(height: 3),
                      Text(subtitle, style: adaptiveSubtitle, softWrap: true),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
    final preview = previewController;
    if (preview == null) return surface;
    return MusicTrackHoverCard(
      data: MusicTrackCardData(
        id: item.id,
        source: item.source,
        trackId: item.trackId,
        title: item.title,
        artists: artists,
        durationMs: item.durationMs,
      ),
      previewController: preview,
      playlists: playlists,
      onAddToPlaylist: onAddToPlaylist,
      child: surface,
    );
  }
}

class _MusicBoxCurrentSourceHeader extends StatelessWidget {
  const _MusicBoxCurrentSourceHeader({required this.state});

  final MusicBoxState state;

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
      child: Row(
        children: [
          const Icon(Icons.queue_music, size: 15, color: UiColors.accent),
          const SizedBox(width: 7),
          Expanded(
            child: Text(
              music_box_display.musicBoxActiveSourceLabel(state.activeSource),
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
            '${state.queue.length}',
            style: const TextStyle(
              color: UiColors.textMuted,
              fontSize: 10,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _MusicBoxClearQueueConfirmDialog extends StatelessWidget {
  const _MusicBoxClearQueueConfirmDialog({required this.itemCount});

  final int itemCount;

  @override
  Widget build(BuildContext context) {
    return DialogFrame(
      title: '清空点歌队列',
      icon: Icons.delete_sweep_outlined,
      adaptiveActions: [
        ResponsiveDialogAction(
          label: '取消',
          onPressed: () => Navigator.of(context).pop(false),
        ),
        ResponsiveDialogAction(
          label: '确认清空',
          tone: ButtonTone.danger,
          onPressed: () => Navigator.of(context).pop(true),
        ),
      ],
      child: Text(
        '确定清空点歌队列中的 $itemCount 首歌曲吗？如果正在播放点歌队列，播放也会停止。',
        style: UiTypography.body,
      ),
    );
  }
}

enum _MusicBoxSection { search, roomPlaylists, myPlaylists }

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

class _MusicBoxPlaylistBrowser extends StatefulWidget {
  const _MusicBoxPlaylistBrowser({
    required this.controller,
    required this.roomId,
    required this.roomScoped,
    required this.onQueueResult,
    required this.onStateChanged,
    required this.initialPlaylist,
    required this.onPlaylistClosed,
  });

  final MusicBoxController? controller;
  final String? roomId;
  final bool roomScoped;
  final ValueChanged<MusicBoxSearchResult> onQueueResult;
  final ValueChanged<MusicBoxState>? onStateChanged;
  final PersonalMusicPlaylist? initialPlaylist;
  final VoidCallback onPlaylistClosed;

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
    if (oldWidget.initialPlaylist?.id != widget.initialPlaylist?.id &&
        widget.initialPlaylist != null) {
      unawaited(_openPlaylist(widget.initialPlaylist!));
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
      final requested = widget.initialPlaylist;
      if (requested != null) {
        final playlist = page.playlists
            .cast<PersonalMusicPlaylist?>()
            .firstWhere(
              (value) => value?.id == requested.id,
              orElse: () => requested,
            );
        if (playlist != null) await _openPlaylist(playlist);
      }
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
      onViewPlaylist: () => _openPlaylist(playlist),
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
                  widget.onPlaylistClosed();
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
        message: '当前队列为空，点击 + 添加歌曲',
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
        final textWidth = (constraints.maxWidth - 20 - 20 - 8 - 4 - 28).clamp(
          24.0,
          double.infinity,
        );
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
       onQueueResult = null;

  const _MusicBoxSongCard.search({
    required this.result,
    required this.controller,
    required this.roomId,
    required this.onQueueResult,
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
  bool _showPlaylistPicker = false;
  bool _loadingPlaylists = false;
  String? _addingPlaylistId;
  List<_MusicBoxPlaylistTarget> _playlistTargets = const [];

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

  Future<void> _openPlaylistPicker() async {
    final controller = widget.controller;
    if (controller == null || _loadingPlaylists) return;
    setState(() {
      _showPlaylistPicker = true;
      _loadingPlaylists = true;
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
    });
    if (targets.isEmpty && failed) {
      showFloatingErrorNotice(context, '加载歌单失败，请重试');
    }
  }

  Future<void> _addToPlaylist(_MusicBoxPlaylistTarget target) async {
    final controller = widget.controller;
    if (controller == null || _addingPlaylistId != null) return;
    setState(() => _addingPlaylistId = target.playlist.id);
    try {
      if (target.roomScoped) {
        final roomId = widget.roomId;
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
      if (!mounted) return;
      setState(() => _showPlaylistPicker = false);
      showFloatingSuccessNotice(context, '已添加到「${target.playlist.name}」');
    } catch (_) {
      if (mounted) {
        showFloatingErrorNotice(context, '添加失败，请检查歌单权限');
      }
    } finally {
      if (mounted) setState(() => _addingPlaylistId = null);
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
              label: '歌手',
              value: artist.isEmpty ? '未知艺人' : artist,
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
            if (!_showPlaylistPicker)
              Row(
                children: [
                  if (isSearchResult) ...[
                    Expanded(
                      child: Button(
                        icon: const Icon(Icons.playlist_add),
                        tone: ButtonTone.primary,
                        height: 34,
                        onPressed: () => widget.onQueueResult?.call(result),
                        child: const Text('点歌队列'),
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
              )
            else ...[
              Row(
                children: [
                  ButtonIcon(
                    icon: const Icon(Icons.arrow_back),
                    size: 28,
                    onPressed: () =>
                        setState(() => _showPlaylistPicker = false),
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
                ConstrainedBox(
                  constraints: const BoxConstraints(maxHeight: 160),
                  child: ListView.separated(
                    padding: EdgeInsets.zero,
                    shrinkWrap: true,
                    itemCount: _playlistTargets.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 6),
                    itemBuilder: (context, index) {
                      final target = _playlistTargets[index];
                      return _MusicBoxPlaylistTargetButton(
                        target: target,
                        controller: widget.controller,
                        roomId: widget.roomId,
                        loading: _addingPlaylistId == target.playlist.id,
                        onAdd: _addingPlaylistId == null
                            ? () => unawaited(_addToPlaylist(target))
                            : null,
                      );
                    },
                  ),
                ),
            ],
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
            onViewPlaylist: host == null
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

class _MusicBoxPlaylistTarget {
  const _MusicBoxPlaylistTarget({
    required this.playlist,
    required this.roomScoped,
  });

  final PersonalMusicPlaylist playlist;
  final bool roomScoped;
}

class _MusicBoxPlaylistTargetButton extends StatelessWidget {
  const _MusicBoxPlaylistTargetButton({
    required this.target,
    required this.controller,
    required this.roomId,
    required this.loading,
    required this.onAdd,
  });

  final _MusicBoxPlaylistTarget target;
  final MusicBoxController? controller;
  final String? roomId;
  final bool loading;
  final VoidCallback? onAdd;

  Future<void> _playAll(
    BuildContext context,
    MusicPlaylistCardHostScope? host,
  ) async {
    final musicController = controller;
    final currentRoomId = roomId;
    if (musicController == null || currentRoomId == null) return;
    try {
      final state = await musicController.activatePlaylist(
        roomId: currentRoomId,
        sourceType: target.roomScoped
            ? MusicBoxActiveSourceType.roomPlaylist
            : MusicBoxActiveSourceType.userPlaylist,
        playlistId: target.playlist.id,
      );
      host?.onStateChanged?.call(state);
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
    const horizontalPadding = 10.0;
    const iconSize = 17.0;
    const iconGap = 8.0;
    const baseStyle = TextStyle(
      color: UiColors.text,
      fontSize: 12,
      fontWeight: FontWeight.w600,
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        final host = MusicPlaylistCardHostScope.maybeOf(context);
        const addButtonSize = 28.0;
        const addButtonGap = 6.0;
        final textWidth =
            (constraints.maxWidth -
                    horizontalPadding * 2 -
                    iconSize -
                    iconGap -
                    addButtonGap -
                    addButtonSize)
                .clamp(24.0, double.infinity);
        final basePainter = TextPainter(
          text: TextSpan(text: target.playlist.name, style: baseStyle),
          textDirection: Directionality.of(context),
          textScaler: MediaQuery.textScalerOf(context),
        )..layout(maxWidth: textWidth);
        final baseLineCount = basePainter.computeLineMetrics().length;
        final reduction = baseLineCount <= 2
            ? 0.0
            : (baseLineCount - 2).clamp(1, 2).toDouble();
        final style = baseStyle.copyWith(
          fontSize: baseStyle.fontSize! - reduction,
        );
        final textHeight = _musicBoxMeasureTextHeight(
          context,
          target.playlist.name,
          style,
          textWidth,
        );
        final height = (textHeight + _musicBoxPlaylistRowVerticalPadding).clamp(
          _musicBoxPlaylistRowMinHeight,
          double.infinity,
        );
        return MusicPlaylistHoverCard(
          data: MusicPlaylistCardData(
            id: target.playlist.id,
            name: target.playlist.name,
            songCount: target.playlist.itemCount,
            createdAt: target.playlist.createdAt,
            creator: target.roomScoped ? null : host?.currentUser?.toSummary(),
            room: target.roomScoped ? host?.room : null,
          ),
          currentUser: host?.currentUser,
          onResolveUserProfile: host?.onResolveUserProfile,
          onResolveRoomProfile: host?.onResolveRoomProfile,
          onEnterCommonRoom: host?.onEnterCommonRoom,
          userProfileActionBuilder: host?.userProfileActionBuilder,
          onPlayAll: controller == null || roomId == null
              ? null
              : () => _playAll(context, host),
          onViewPlaylist: host == null
              ? null
              : () => host.onViewPlaylist(target.playlist, target.roomScoped),
          child: PressableSurface(
            key: ValueKey<String>(
              'music-box-playlist-target:'
              '${target.roomScoped ? 'room' : 'personal'}:'
              '${target.playlist.id}',
            ),
            width: double.infinity,
            height: height,
            padding: const EdgeInsets.symmetric(horizontal: horizontalPadding),
            hoverLift: 2,
            baseDepth: 4,
            backgroundColor: UiColors.surfaceLow,
            pressedBackgroundColor: UiColors.surfacePressed,
            borderColor: UiColors.border,
            borderRadius: UiRadii.md,
            child: Row(
              children: [
                Icon(
                  target.roomScoped ? Icons.meeting_room : Icons.person,
                  size: iconSize,
                  color: UiColors.accent,
                ),
                const SizedBox(width: iconGap),
                Expanded(
                  child: Text(
                    target.playlist.name,
                    style: style,
                    softWrap: true,
                  ),
                ),
                const SizedBox(width: addButtonGap),
                _MusicBoxHoverCardActionGuard(
                  child: ButtonIcon(
                    key: ValueKey<String>(
                      'music-box-playlist-target-add:${target.playlist.id}',
                    ),
                    icon: const Icon(Icons.add),
                    tooltip: '添加到歌单',
                    tone: ButtonTone.primary,
                    loading: loading,
                    onPressed: onAdd,
                    size: addButtonSize,
                  ),
                ),
              ],
            ),
          ),
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
    required this.onQueueResult,
  });

  final List<MusicBoxSearchResult> results;
  final String query;
  final bool searching;
  final String? error;
  final bool hasQuery;
  final MusicBoxController? controller;
  final String? roomId;
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
        return _MusicBoxTrackTile(
          keyScope: 'search',
          result: result,
          query: query,
          controller: controller,
          roomId: roomId,
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
    required this.onQueue,
    this.durationMs,
  });

  final String keyScope;
  final MusicBoxSearchResult result;
  final String query;
  final MusicBoxController? controller;
  final String? roomId;
  final VoidCallback onQueue;
  final int? durationMs;

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
                  resetKey: identity,
                  cardWidth: 310,
                  cardBuilder: (_) => _MusicBoxSongCard.search(
                    result: result,
                    controller: controller,
                    roomId: roomId,
                    onQueueResult: (_) => onQueue(),
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
                icon: const Icon(Icons.add),
                tooltip: '点歌队列',
                tone: ButtonTone.primary,
                onPressed: onQueue,
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
