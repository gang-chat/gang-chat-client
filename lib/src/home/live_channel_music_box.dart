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
            const SizedBox(height: 7),
            _MusicBoxVolume(
              initialVolume: widget.volume,
              onChanged: widget.onVolumeChanged,
            ),
            const SizedBox(height: 10),
            Expanded(
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
                onCreateFirstRoomPlaylist: widget.onCreateFirstRoomPlaylist,
                onCreateFirstPersonalPlaylist:
                    widget.onCreateFirstPersonalPlaylist,
                onEditRoomPlaylist: widget.onEditRoomPlaylist,
                onEditPersonalPlaylist: widget.onEditPersonalPlaylist,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Title row. The trailing source chip names the authoritative active source
/// and jumps to its tab, so "what is playing" is always one tap away no matter
/// where the user is browsing.
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
          child: Align(
            alignment: Alignment.centerRight,
            child: PressableSurface(
              key: const ValueKey<String>('music-box-active-source-chip'),
              height: 24,
              tooltip: playing ? '正在播放的来源' : '当前来源',
              onPressed: onOpenSource,
              padding: const EdgeInsets.symmetric(horizontal: 8),
              backgroundColor: UiColors.surfaceLow,
              selected: playing,
              borderRadius: UiRadii.md,
              hoverLift: 1,
              baseDepth: 2,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    playing
                        ? Icons.graphic_eq
                        : _musicBoxSourceTypeIcon(active.type),
                    size: 13,
                    color: playing ? UiColors.accent : UiColors.textSecondary,
                  ),
                  const SizedBox(width: 5),
                  ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 120),
                    child: Text(
                      music_box_display.musicBoxActiveSourceLabel(active),
                      maxLines: 1,
                      softWrap: false,
                      overflow: TextOverflow.fade,
                      style: TextStyle(
                        color: playing ? UiColors.accent : UiColors.text,
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  const SizedBox(width: 2),
                  const Icon(
                    Icons.chevron_right,
                    size: 13,
                    color: UiColors.textMuted,
                  ),
                ],
              ),
            ),
          ),
        ),
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

IconData _musicBoxSourceTypeIcon(MusicBoxActiveSourceType type) {
  return switch (type) {
    MusicBoxActiveSourceType.temporary => Icons.queue_music,
    MusicBoxActiveSourceType.roomPlaylist => Icons.meeting_room,
    MusicBoxActiveSourceType.userPlaylist => Icons.person,
  };
}

/// The now-playing strip: spinning vinyl, title, artist plus requester, the
/// progress bar, and transport controls. Members the server does not let
/// control playback see a single status line instead of four disabled
/// buttons.
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
                      style: const TextStyle(
                        color: UiColors.textSecondary,
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  if (requester != null) ...[
                    const SizedBox(width: 6),
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
              const SizedBox(height: 8),
              _MusicBoxProgressBar(state: state),
              const SizedBox(height: 8),
              if (!canControl)
                const Center(
                  child: Text(
                    '播放由房间管理员控制',
                    key: ValueKey<String>('music-box-transport-locked'),
                    style: TextStyle(
                      color: UiColors.textMuted,
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                )
              else
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    ButtonIcon(
                      key: const ValueKey<String>(
                        'music-box-transport-previous',
                      ),
                      icon: const Icon(Icons.skip_previous),
                      tooltip: '上一首',
                      onPressed: state.playback.canPrevious ? onPrevious : null,
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
                        music_box_display.MusicBoxTransportAction.pause =>
                          '暂停',
                        music_box_display.MusicBoxTransportAction.resume =>
                          '继续播放',
                        music_box_display.MusicBoxTransportAction.play => '播放',
                      },
                      tone: ButtonTone.primary,
                      onPressed: hasQueue ? onTogglePlayback : null,
                      size: 30,
                    ),
                    const SizedBox(width: 7),
                    ButtonIcon(
                      key: const ValueKey<String>('music-box-transport-next'),
                      icon: const Icon(Icons.skip_next),
                      tooltip: '下一首',
                      onPressed: hasQueue && state.playback.canNext
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
          size: 18,
        ),
        const SizedBox(width: 4),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 72),
          child: Text(
            requester.displayName,
            maxLines: 1,
            softWrap: false,
            overflow: TextOverflow.fade,
            style: const TextStyle(
              color: UiColors.textMuted,
              fontSize: 10,
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
