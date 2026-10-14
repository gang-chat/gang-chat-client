part of 'live_channel_pane.dart';

/// Minimum height retained by the docked console. When the live controls need
/// an extra row, the panel extends upward instead of making the whole console
/// scroll.
const double _musicBoxMinComfortableHeight = 400;

/// Height of the dense controls that sit above the lists: the search field,
/// the source picker beside it, and the tab bar.
const double _musicBoxControlHeight = 30;

/// The in-pane music box console. Three fixed regions, none of which morph:
/// a now-playing strip (what is playing, from which source), a persistent
/// search-to-request bar, and a tab bar for 点歌队列 / 房间歌单 / 我的歌单.
/// Audio is delivered separately via the LiveKit session; this is purely the
/// control surface and status display.
class LiveMusicBoxPanel extends StatefulWidget {
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
    this.playlistsRevision = 0,
    this.room,
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
  final int playlistsRevision;
  final PublicRoom? room;
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
  State<LiveMusicBoxPanel> createState() => _LiveMusicBoxPanelState();
}

/// The body is one flat list, so navigation is just what is unfolded: the
/// search panel under the queue header, which playlist groups are open, and
/// which single playlist shows its tracks. Realtime snapshots never touch any
/// of these — the user decides what is open; the server decides what plays.
class _LiveMusicBoxPanelState extends State<LiveMusicBoxPanel> {
  bool _searchOpen = false;
  final Set<music_box_display.MusicBoxSection> _openSections = {};
  String? _openPlaylistKey;
  // Incremented when the header chip asks the body to scroll to the active
  // source; the body reacts to the change, not the value.
  int _revealSerial = 0;
  music_box_display.MusicBoxSection? _revealSection;

  @override
  void didUpdateWidget(covariant LiveMusicBoxPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.roomId != widget.roomId ||
        oldWidget.controller != widget.controller) {
      _searchOpen = false;
      _openSections.clear();
      _openPlaylistKey = null;
    }
  }

  void _toggleSearch() {
    setState(() {
      _searchOpen = !_searchOpen;
      if (!_searchOpen) widget.searchController.clear();
    });
  }

  void _toggleSection(music_box_display.MusicBoxSection section) {
    setState(() {
      if (!_openSections.add(section)) _openSections.remove(section);
    });
  }

  void _togglePlaylist(MusicBoxActiveSourceType type, String id) {
    final key = music_box_display.musicBoxPlaylistKey(type, id);
    setState(() => _openPlaylistKey = _openPlaylistKey == key ? null : key);
  }

  /// Unfold a specific playlist (from a playlist card's 查看歌单).
  void _openPlaylist(MusicBoxActiveSourceType type, String id) {
    setState(() {
      _openSections.add(music_box_display.musicBoxSectionForSource(type));
      _openPlaylistKey = music_box_display.musicBoxPlaylistKey(type, id);
    });
  }

  /// Header chip: unfold whatever owns the active source and scroll to it.
  void _showActiveSource() {
    final active = widget.state.activeSource;
    final section = music_box_display.musicBoxSectionForSource(active.type);
    setState(() {
      if (section != music_box_display.MusicBoxSection.queue) {
        _openSections.add(section);
        _openPlaylistKey = music_box_display.musicBoxPlaylistKey(
          active.type,
          active.id,
        );
      }
      _revealSection = section;
      _revealSerial += 1;
    });
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: UiColors.surfaceLow,
        borderRadius: BorderRadius.circular(UiRadii.xl),
        border: Border.all(color: UiColors.borderStrong),
        boxShadow: UiShadows.popover,
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(10, 10, 10, 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _MusicBoxHeader(
              state: state,
              onOpenSource: _showActiveSource,
              onClose: widget.onClose,
            ),
            const SizedBox(height: 8),
            _MusicBoxNowPlaying(
              state: state,
              onTogglePlayback: widget.onTogglePlayback,
              onSkip: widget.onSkip,
              onPrevious: widget.onPrevious,
              onModeChanged: widget.onModeChanged,
              currentUser: widget.currentUser,
              onResolveUserProfile: widget.onResolveUserProfile,
              onResolveRoomProfile: widget.onResolveRoomProfile,
              onEnterCommonRoom: widget.onEnterCommonRoom,
              userProfileActionBuilder: widget.userProfileActionBuilder,
            ),
            const SizedBox(height: 8),
            _MusicBoxVolume(
              initialVolume: widget.volume,
              onChanged: widget.onVolumeChanged,
            ),
            const SizedBox(height: 10),
            Expanded(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: UiColors.background,
                  borderRadius: BorderRadius.circular(UiRadii.lg),
                  border: Border.all(color: UiColors.border),
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(UiRadii.lg - 1),
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(6, 6, 6, 0),
                    child: _MusicBoxBody(
                      state: state,
                      searchOpen: _searchOpen,
                      openSections: _openSections,
                      openPlaylistKey: _openPlaylistKey,
                      revealSerial: _revealSerial,
                      revealSection: _revealSection,
                      onToggleSearch: _toggleSearch,
                      onToggleSection: _toggleSection,
                      onTogglePlaylist: _togglePlaylist,
                      onOpenPlaylist: _openPlaylist,
                      searchController: widget.searchController,
                      searchResults: widget.searchResults,
                      searching: widget.searching,
                      searchError: widget.searchError,
                      source: widget.source,
                      onQueueResult: widget.onQueueResult,
                      onRemoveItem: widget.onRemoveItem,
                      onSourceChanged: widget.onSourceChanged,
                      controller: widget.controller,
                      roomId: widget.roomId,
                      playlistsRevision: widget.playlistsRevision,
                      room: widget.room,
                      onStateChanged: widget.onStateChanged,
                      currentUser: widget.currentUser,
                      onResolveUserProfile: widget.onResolveUserProfile,
                      onResolveRoomProfile: widget.onResolveRoomProfile,
                      onEnterCommonRoom: widget.onEnterCommonRoom,
                      userProfileActionBuilder: widget.userProfileActionBuilder,
                      onCreateFirstRoomPlaylist:
                          widget.onCreateFirstRoomPlaylist,
                      onCreateFirstPersonalPlaylist:
                          widget.onCreateFirstPersonalPlaylist,
                      onEditRoomPlaylist: widget.onEditRoomPlaylist,
                      onEditPersonalPlaylist: widget.onEditPersonalPlaylist,
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Title row: the dot-matrix wordmark, the active-source chip (jumps to the
/// list that owns what is playing) and the close key.
class _MusicBoxHeader extends StatelessWidget {
  const _MusicBoxHeader({
    required this.state,
    required this.onOpenSource,
    required this.onClose,
  });

  final MusicBoxState state;
  final VoidCallback onOpenSource;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final active = state.activeSource;
    final playing = music_box_display.musicBoxHasActivePlayback(state);
    return Row(
      children: [
        const SizedBox(width: 4),
        Semantics(
          label: '音乐盒',
          child: ExcludeSemantics(
            child: Text(
              'MUSIC BOX',
              style: UiTypography.dot.copyWith(
                fontSize: 15,
                color: UiColors.text,
                letterSpacing: 0.5,
              ),
            ),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Align(
            alignment: Alignment.centerRight,
            child: PressableSurface(
              key: const ValueKey<String>('music-box-active-source-chip'),
              height: 26,
              tooltip: playing ? '正在播放的来源' : '当前来源',
              onPressed: onOpenSource,
              padding: const EdgeInsets.symmetric(horizontal: 9),
              backgroundColor: playing ? UiColors.selected : UiColors.surface,
              selected: false,
              borderColor: playing ? UiColors.accentBorder : UiColors.border,
              borderRadius: UiRadii.pill,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (playing)
                    const EqualizerBars(height: 10, barWidth: 2, gap: 1.5)
                  else
                    Icon(
                      _musicBoxSourceTypeIcon(active.type),
                      size: 13,
                      color: UiColors.textSecondary,
                    ),
                  const SizedBox(width: 6),
                  ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 120),
                    child: Text(
                      music_box_display.musicBoxActiveSourceLabel(active),
                      maxLines: 1,
                      softWrap: false,
                      overflow: TextOverflow.fade,
                      style: UiTypography.label.copyWith(
                        color: playing ? UiColors.accent : UiColors.text,
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  const SizedBox(width: 2),
                  Icon(
                    Icons.arrow_outward_rounded,
                    size: 12,
                    color: playing ? UiColors.accent : UiColors.textMuted,
                  ),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(width: 6),
        ButtonIcon(
          key: const ValueKey<String>('music-box-close'),
          icon: const Icon(Icons.close_rounded),
          tooltip: '关闭音乐盒',
          onPressed: onClose,
          ghost: true,
          size: 28,
        ),
      ],
    );
  }
}

IconData _musicBoxSourceTypeIcon(MusicBoxActiveSourceType type) {
  return switch (type) {
    MusicBoxActiveSourceType.temporary => Icons.queue_music,
    MusicBoxActiveSourceType.roomPlaylist => Icons.meeting_room,
    MusicBoxActiveSourceType.userPlaylist => Icons.person,
  };
}

/// The deck: a black dot-matrix display (status, title, artist and requester,
/// dotted progress) with the record spinning half out of its right edge, then
/// a row of hardware-style transport keys. Members the server does not let
/// control playback see a single locked line instead of disabled keys.
class _MusicBoxNowPlaying extends StatelessWidget {
  const _MusicBoxNowPlaying({
    required this.state,
    required this.onTogglePlayback,
    required this.onSkip,
    required this.onPrevious,
    required this.onModeChanged,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
  });

  final MusicBoxState state;
  final VoidCallback onTogglePlayback;
  final VoidCallback onSkip;
  final VoidCallback? onPrevious;
  final ValueChanged<MusicBoxPlaybackMode>? onModeChanged;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;

  static const double _recordSize = 104;

  @override
  Widget build(BuildContext context) {
    final current = state.currentItem;
    final spinning = music_box_display.musicBoxRecordSpinning(state);
    final transport = music_box_display.musicBoxPrimaryTransport(state);
    final hasQueue = state.queue.isNotEmpty;
    final canControl = state.playback.capabilities.canControl;
    final requester =
        state.activeSource.type == MusicBoxActiveSourceType.temporary
        ? current?.requestedBy
        : null;
    final artist = current?.artist.trim() ?? '';
    final statusLabel = current == null
        ? 'IDLE'
        : switch (state.playback.state) {
            MusicBoxPlaybackState.playing => 'NOW PLAYING',
            MusicBoxPlaybackState.paused => 'PAUSED',
            MusicBoxPlaybackState.stopped => 'STOPPED',
          };
    final live =
        current != null &&
        state.playback.state == MusicBoxPlaybackState.playing;

    final display = DecoratedBox(
      decoration: BoxDecoration(
        color: UiColors.surfacePressed,
        borderRadius: BorderRadius.circular(UiRadii.lg),
        border: Border.all(color: UiColors.border),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(UiRadii.lg - 1),
        child: Stack(
          children: [
            const Positioned.fill(
              child: DotGridBackground(
                color: Colors.transparent,
                dotColor: Color(0x12FFFFFF),
                spacing: 6,
                dotRadius: 0.6,
              ),
            ),
            if (live)
              Positioned.fill(
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: RadialGradient(
                      center: const Alignment(1, 0),
                      radius: 1.1,
                      colors: [
                        UiColors.accent.withValues(alpha: 0.14),
                        UiColors.accent.withValues(alpha: 0),
                      ],
                    ),
                  ),
                ),
              ),
            Positioned(
              right: -_recordSize * 0.42,
              top: 0,
              bottom: 0,
              child: Center(
                child: _VinylRecord(
                  spinning: spinning,
                  label: current?.title,
                  size: _recordSize,
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(
                12,
                10,
                _recordSize * 0.58 + 10,
                10,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: SignalTag(
                          label: statusLabel,
                          color: live ? UiColors.accent : UiColors.textMuted,
                          dot: true,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  OverflowMarqueeText(
                    key: const ValueKey<String>('music-box-now-playing:title'),
                    trackKey: const ValueKey<String>(
                      'music-box-now-playing:title-marquee-track',
                    ),
                    text: current?.title ?? '未在播放',
                    style: UiTypography.title.copyWith(
                      color: current == null
                          ? UiColors.textMuted
                          : UiColors.text,
                      fontSize: 16,
                      height: 1.25,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Row(
                    children: [
                      Flexible(
                        child: Text(
                          current == null
                              ? '在下方搜索歌曲点歌'
                              : artist.isEmpty
                              ? '未知艺人'
                              : artist,
                          maxLines: 1,
                          softWrap: false,
                          overflow: TextOverflow.fade,
                          style: UiTypography.mono.copyWith(
                            color: UiColors.textSecondary,
                            fontSize: 11,
                          ),
                        ),
                      ),
                      if (requester != null) ...[
                        const SizedBox(width: 8),
                        _MusicBoxRequesterChip(
                          key: const ValueKey<String>(
                            'music-box-now-playing:requester',
                          ),
                          requester: requester,
                          currentUser: currentUser,
                          onResolveUserProfile: onResolveUserProfile,
                          onResolveRoomProfile: onResolveRoomProfile,
                          onEnterCommonRoom: onEnterCommonRoom,
                          userProfileActionBuilder: userProfileActionBuilder,
                        ),
                      ],
                    ],
                  ),
                  const SizedBox(height: 12),
                  _MusicBoxProgressBar(state: state),
                ],
              ),
            ),
          ],
        ),
      ),
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        display,
        const SizedBox(height: 8),
        if (!canControl)
          Container(
            height: 40,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(UiRadii.md),
              border: Border.all(color: UiColors.border),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(
                  Icons.lock_outline_rounded,
                  size: 14,
                  color: UiColors.textMuted,
                ),
                const SizedBox(width: 6),
                Text(
                  '播放由房间管理员控制',
                  key: const ValueKey<String>('music-box-transport-locked'),
                  style: UiTypography.label.copyWith(
                    color: UiColors.textMuted,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          )
        else
          LayoutBuilder(
            builder: (context, constraints) {
              const gap = 6.0;
              final unit = ((constraints.maxWidth - gap * 3) / 5)
                  .clamp(30.0, 120.0)
                  .toDouble();
              return Row(
                children: [
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
                      size: 40,
                      width: unit,
                      radius: UiRadii.md,
                      iconSize: 18,
                    ),
                  ),
                  const SizedBox(width: gap),
                  ButtonIcon(
                    key: const ValueKey<String>('music-box-transport-previous'),
                    icon: const Icon(Icons.skip_previous_rounded),
                    tooltip: '上一首',
                    onPressed: state.playback.canPrevious ? onPrevious : null,
                    size: 40,
                    width: unit,
                    radius: UiRadii.md,
                    iconSize: 20,
                  ),
                  const SizedBox(width: gap),
                  ButtonIcon(
                    key: const ValueKey<String>('music-box-primary-playback'),
                    icon: Icon(
                      transport ==
                              music_box_display.MusicBoxTransportAction.pause
                          ? Icons.pause_rounded
                          : Icons.play_arrow_rounded,
                    ),
                    tooltip: switch (transport) {
                      music_box_display.MusicBoxTransportAction.pause => '暂停',
                      music_box_display.MusicBoxTransportAction.resume =>
                        '继续播放',
                      music_box_display.MusicBoxTransportAction.play => '播放',
                    },
                    tone: ButtonTone.primary,
                    onPressed: hasQueue ? onTogglePlayback : null,
                    size: 40,
                    width: unit * 2,
                    radius: UiRadii.md,
                    iconSize: 24,
                  ),
                  const SizedBox(width: gap),
                  ButtonIcon(
                    key: const ValueKey<String>('music-box-transport-next'),
                    icon: const Icon(Icons.skip_next_rounded),
                    tooltip: '下一首',
                    onPressed: hasQueue && state.playback.canNext
                        ? onSkip
                        : null,
                    size: 40,
                    width: unit,
                    radius: UiRadii.md,
                    iconSize: 20,
                  ),
                ],
              );
            },
          ),
      ],
    );
  }
}

/// Avatar + name for the person who requested the current song. The avatar
/// carries the user card; the name is spelled out, so no tooltip is needed.
class _MusicBoxRequesterChip extends StatelessWidget {
  const _MusicBoxRequesterChip({
    super.key,
    required this.requester,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
  });

  final MusicBoxRequester requester;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        _MusicBoxRequesterAvatar(
          requester: requester,
          currentUser: currentUser,
          onResolveUserProfile: onResolveUserProfile,
          onResolveRoomProfile: onResolveRoomProfile,
          onEnterCommonRoom: onEnterCommonRoom,
          userProfileActionBuilder: userProfileActionBuilder,
          size: 16,
        ),
        const SizedBox(width: 5),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 72),
          child: Text(
            requester.displayName,
            maxLines: 1,
            softWrap: false,
            overflow: TextOverflow.fade,
            style: UiTypography.label.copyWith(
              color: UiColors.textMuted,
              fontSize: 10.5,
              fontWeight: FontWeight.w600,
            ),
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
  // The mute key size; the strip is a little taller.
  static const _size = 24.0;
  static const _pad = 12.0;

  late double _volume = widget.initialVolume;
  // The level to restore to when unmuting; captured at the moment of muting.
  late double _premute = widget.initialVolume > 0 ? widget.initialVolume : 0.7;
  bool _hovered = false;
  // True while a slider drag is in flight. The pointer gets captured by the
  // slider for the whole drag, so the cursor can wander off the pill (firing
  // MouseRegion.onExit) without meaning to collapse it — keep it open until the
  // drag ends, then fall back to the hover state.
  bool _dragging = false;

  // The fader strip is always fully shown; hover/drag only light its frame.
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

  // A flat fader strip: mute key, the slider, and the level in monospace.
  @override
  Widget build(BuildContext context) {
    final p = _palette;
    return MouseRegion(
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: SizedBox(
        key: const ValueKey<String>('music-box-volume-control'),
        height: _size + 8,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: UiColors.surface,
            borderRadius: BorderRadius.circular(UiRadii.md),
            border: Border.all(
              color: _expanded && _hovered
                  ? UiColors.borderStrong
                  : UiColors.border,
            ),
          ),
          child: Row(
            children: [
              const SizedBox(width: 4),
              _iconButton(_size + 4),
              Text(
                'VOL',
                style: UiTypography.overline.copyWith(
                  color: p.foreground == UiColors.accent
                      ? UiColors.textSecondary
                      : UiColors.textMuted,
                  fontSize: 9.5,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(child: _slider()),
              const SizedBox(width: 10),
              SizedBox(
                width: 30,
                child: Text(
                  (_volume * 100).round().toString().padLeft(2, '0'),
                  textAlign: TextAlign.right,
                  style: UiTypography.dot.copyWith(
                    fontSize: 14,
                    color: _muted ? UiColors.textMuted : UiColors.accent,
                  ),
                ),
              ),
              const SizedBox(width: _pad - 2),
            ],
          ),
        ),
      ),
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
    final size = Size.square(widget.size);
    return SizedBox.fromSize(
      size: size,
      child: Stack(
        children: [
          RotationTransition(
            turns: _controller,
            child: CustomPaint(size: size, painter: const _VinylPainter()),
          ),
          // The light catching the grooves stays put while the disc turns.
          IgnorePointer(
            child: CustomPaint(size: size, painter: const _VinylSheenPainter()),
          ),
        ],
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

    canvas.drawCircle(center, radius, Paint()..color = const Color(0xFF0B0B0D));
    canvas.drawCircle(
      center,
      radius - 0.5,
      Paint()
        ..color = UiColors.borderStrong
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1,
    );

    final groove = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 0.8;
    var index = 0;
    for (var r = radius - 4; r > radius * 0.4; r -= 2.6) {
      groove.color = index.isEven
          ? const Color(0xFF1A1A1E)
          : const Color(0xFF131316);
      canvas.drawCircle(center, r, groove);
      index += 1;
    }

    // Lime label with a printed stripe so the rotation reads at a glance.
    final labelRadius = radius * 0.36;
    canvas.drawCircle(center, labelRadius, Paint()..color = UiColors.accent);
    canvas.save();
    canvas.clipPath(
      Path()..addOval(Rect.fromCircle(center: center, radius: labelRadius)),
    );
    canvas.drawRect(
      Rect.fromLTWH(
        center.dx - labelRadius,
        center.dy - labelRadius * 0.62,
        labelRadius * 2,
        labelRadius * 0.28,
      ),
      Paint()..color = UiColors.onAccent.withValues(alpha: 0.85),
    );
    canvas.restore();
    canvas.drawCircle(
      center,
      labelRadius * 0.16,
      Paint()..color = UiColors.surfacePressed,
    );
  }

  @override
  bool shouldRepaint(_VinylPainter oldDelegate) => false;
}

class _VinylSheenPainter extends CustomPainter {
  const _VinylSheenPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    final center = rect.center;
    final radius = size.width / 2;
    final ring = Path()
      ..fillType = PathFillType.evenOdd
      ..addOval(Rect.fromCircle(center: center, radius: radius - 2))
      ..addOval(Rect.fromCircle(center: center, radius: radius * 0.38));
    canvas.drawPath(
      ring,
      Paint()
        ..shader = SweepGradient(
          colors: [
            Colors.white.withValues(alpha: 0),
            Colors.white.withValues(alpha: 0.07),
            Colors.white.withValues(alpha: 0),
            Colors.white.withValues(alpha: 0),
            Colors.white.withValues(alpha: 0.05),
            Colors.white.withValues(alpha: 0),
          ],
          stops: const [0.0, 0.1, 0.22, 0.5, 0.6, 0.72],
        ).createShader(rect),
    );
  }

  @override
  bool shouldRepaint(_VinylSheenPainter oldDelegate) => false;
}

/// Server-authoritative progress, drawn as a row of dots that light up as
/// the song plays. The server pushes a fresh snapshot every second, so the
/// bar renders the snapshot's reported position — no local stepping.
class _MusicBoxProgressBar extends StatelessWidget {
  const _MusicBoxProgressBar({required this.state});

  final MusicBoxState state;

  @override
  Widget build(BuildContext context) {
    final progress = music_box_display.musicBoxProgress(state);
    final hasTrack = state.currentItem != null;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        Semantics(
          value: '${(progress.fraction * 100).round()}%',
          child: SizedBox(
            height: 6,
            child: CustomPaint(
              painter: _DotProgressPainter(
                fraction: progress.fraction,
                active: hasTrack,
              ),
            ),
          ),
        ),
        const SizedBox(height: 7),
        Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(
              music_box_display.musicBoxFormatDuration(progress.positionMs),
              style: UiTypography.dot.copyWith(
                fontSize: 17,
                height: 1,
                color: hasTrack ? UiColors.text : UiColors.textMuted,
              ),
            ),
            const SizedBox(width: 6),
            Text(
              '/',
              style: UiTypography.mono.copyWith(
                fontSize: 10,
                color: UiColors.textMuted,
                height: 1,
              ),
            ),
            const SizedBox(width: 6),
            Text(
              music_box_display.musicBoxFormatDuration(progress.durationMs),
              style: UiTypography.mono.copyWith(
                fontSize: 10,
                color: UiColors.textMuted,
                height: 1,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _DotProgressPainter extends CustomPainter {
  const _DotProgressPainter({required this.fraction, required this.active});

  final double fraction;
  final bool active;

  @override
  void paint(Canvas canvas, Size size) {
    const step = 5.0;
    const radius = 1.3;
    final count = math.max(2, (size.width / step).floor());
    final spacing = (size.width - radius * 2) / (count - 1);
    final lit = active ? (fraction.clamp(0.0, 1.0) * (count - 1)).round() : -1;
    final y = size.height / 2;
    final off = Paint()..color = const Color(0xFF2A2A30);
    final on = Paint()..color = UiColors.accent;
    for (var i = 0; i < count; i++) {
      final x = radius + i * spacing;
      if (i == lit) {
        canvas.drawCircle(
          Offset(x, y),
          radius * 2.6,
          Paint()..color = UiColors.accent.withValues(alpha: 0.25),
        );
        canvas.drawCircle(Offset(x, y), radius * 1.6, on);
      } else {
        canvas.drawCircle(Offset(x, y), radius, i < lit ? on : off);
      }
    }
  }

  @override
  bool shouldRepaint(_DotProgressPainter oldDelegate) =>
      oldDelegate.fraction != fraction || oldDelegate.active != active;
}
