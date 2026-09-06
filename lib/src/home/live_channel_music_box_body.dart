part of 'live_channel_pane.dart';

/// Lower body of the music box: one flat scrolling list. The request queue
/// sits on top and is always open; a search button on its header unfolds a
/// search panel right under it. Below come 房间歌单 and 我的歌单 as collapsed
/// groups; opening a group lists its playlists, opening a playlist lists its
/// tracks in place. Nothing morphs and nothing navigates away.
class _MusicBoxBody extends StatefulWidget {
  const _MusicBoxBody({
    required this.state,
    required this.searchOpen,
    required this.openSections,
    required this.openPlaylistKey,
    required this.revealSerial,
    required this.revealSection,
    required this.onToggleSearch,
    required this.onToggleSection,
    required this.onTogglePlaylist,
    required this.onOpenPlaylist,
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
    required this.playlistsRevision,
    required this.room,
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
  final bool searchOpen;
  final Set<music_box_display.MusicBoxSection> openSections;
  final String? openPlaylistKey;
  final int revealSerial;
  final music_box_display.MusicBoxSection? revealSection;
  final VoidCallback onToggleSearch;
  final ValueChanged<music_box_display.MusicBoxSection> onToggleSection;
  final void Function(MusicBoxActiveSourceType type, String id)
  onTogglePlaylist;
  final void Function(MusicBoxActiveSourceType type, String id) onOpenPlaylist;
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

  @override
  State<_MusicBoxBody> createState() => _MusicBoxBodyState();
}

class _MusicBoxBodyState extends State<_MusicBoxBody> {
  final ScrollController _scrollController = ScrollController();
  final Map<music_box_display.MusicBoxSection, GlobalKey> _sectionKeys = {
    for (final section in music_box_display.MusicBoxSection.values)
      section: GlobalKey(),
  };

  @override
  void initState() {
    super.initState();
    widget.searchController.addListener(_handleSearchChanged);
  }

  @override
  void didUpdateWidget(covariant _MusicBoxBody oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.searchController != widget.searchController) {
      oldWidget.searchController.removeListener(_handleSearchChanged);
      widget.searchController.addListener(_handleSearchChanged);
    }
    if (oldWidget.revealSerial != widget.revealSerial) {
      final section = widget.revealSection;
      if (section != null) _scheduleReveal(section);
    }
  }

  @override
  void dispose() {
    widget.searchController.removeListener(_handleSearchChanged);
    _scrollController.dispose();
    super.dispose();
  }

  void _handleSearchChanged() {
    if (mounted) setState(() {});
  }

  void _scheduleReveal(music_box_display.MusicBoxSection section) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final headerContext = _sectionKeys[section]!.currentContext;
      if (headerContext == null) return;
      unawaited(
        Scrollable.ensureVisible(
          headerContext,
          alignment: 0,
          duration: const Duration(milliseconds: 180),
          curve: Curves.easeOutCubic,
        ),
      );
    });
  }

  Future<void> _viewPlaylist(
    PersonalMusicPlaylist playlist,
    bool roomScoped,
  ) async {
    FocusManager.instance.primaryFocus?.unfocus();
    widget.onOpenPlaylist(
      roomScoped
          ? MusicBoxActiveSourceType.roomPlaylist
          : MusicBoxActiveSourceType.userPlaylist,
      playlist.id,
    );
  }

  Widget _playlistGroup(MusicBoxActiveSourceType type) {
    final section = music_box_display.musicBoxSectionForSource(type);
    final roomScoped = type == MusicBoxActiveSourceType.roomPlaylist;
    return _MusicBoxPlaylistGroup(
      key: ValueKey<String>(
        'music-box-group:${musicBoxActiveSourceTypeValue(type)}',
      ),
      headerKey: _sectionKeys[section]!,
      type: type,
      open: widget.openSections.contains(section),
      openPlaylistKey: widget.openPlaylistKey,
      state: widget.state,
      controller: widget.controller,
      roomId: widget.roomId,
      playlistsRevision: widget.playlistsRevision,
      room: widget.room,
      currentUser: widget.currentUser,
      onStateChanged: widget.onStateChanged,
      onToggle: () => widget.onToggleSection(section),
      onTogglePlaylist: (id) => widget.onTogglePlaylist(type, id),
      onCreateFirstPlaylist: roomScoped
          ? widget.onCreateFirstRoomPlaylist
          : widget.onCreateFirstPersonalPlaylist,
      onEdit: roomScoped
          ? widget.onEditRoomPlaylist
          : widget.onEditPersonalPlaylist,
      onResolveUserProfile: widget.onResolveUserProfile,
      onResolveRoomProfile: widget.onResolveRoomProfile,
      onEnterCommonRoom: widget.onEnterCommonRoom,
      userProfileActionBuilder: widget.userProfileActionBuilder,
    );
  }

  @override
  Widget build(BuildContext context) {
    final query = widget.searchController.text;
    final hasQuery = widget.searchOpen && query.trim().isNotEmpty;
    return MusicPlaylistCardHostScope(
      currentUser: widget.currentUser,
      room: widget.room,
      onResolveUserProfile: widget.onResolveUserProfile,
      onResolveRoomProfile: widget.onResolveRoomProfile,
      onEnterCommonRoom: widget.onEnterCommonRoom,
      userProfileActionBuilder: widget.userProfileActionBuilder,
      onStateChanged: widget.onStateChanged,
      onViewPlaylist: _viewPlaylist,
      onEditRoomPlaylist: widget.onEditRoomPlaylist,
      onEditPersonalPlaylist: widget.onEditPersonalPlaylist,
      child: CustomScrollView(
        key: const ValueKey<String>('music-box-list'),
        controller: _scrollController,
        slivers: [
          _MusicBoxQueueSection(
            key: const ValueKey<String>('music-box-section:queue'),
            headerKey: _sectionKeys[music_box_display.MusicBoxSection.queue]!,
            scrollController: _scrollController,
            state: widget.state,
            searchOpen: widget.searchOpen,
            query: query,
            searchController: widget.searchController,
            searchResults: widget.searchResults,
            searching: widget.searching,
            searchError: widget.searchError,
            source: widget.source,
            onToggleSearch: widget.onToggleSearch,
            onSourceChanged: widget.onSourceChanged,
            onQueueResult: widget.onQueueResult,
            onRemoveItem: widget.onRemoveItem,
            controller: widget.controller,
            roomId: widget.roomId,
            onStateChanged: widget.onStateChanged,
            currentUser: widget.currentUser,
            onResolveUserProfile: widget.onResolveUserProfile,
            onResolveRoomProfile: widget.onResolveRoomProfile,
            onEnterCommonRoom: widget.onEnterCommonRoom,
            userProfileActionBuilder: widget.userProfileActionBuilder,
          ),
          // While searching, the results take the whole list so the user is
          // not scrolling past playlists to find the next hit.
          if (!hasQuery) ...[
            _playlistGroup(MusicBoxActiveSourceType.roomPlaylist),
            _playlistGroup(MusicBoxActiveSourceType.userPlaylist),
          ],
        ],
      ),
    );
  }
}

/// Runs a music-box write, applies the returned snapshot, and surfaces
/// failures as a floating notice.
Future<void> _musicBoxRunWrite(
  BuildContext context, {
  required Future<MusicBoxState> Function() write,
  required ValueChanged<MusicBoxState>? onStateChanged,
  required String failureMessage,
  String? successMessage,
}) async {
  try {
    final state = await write();
    onStateChanged?.call(state);
    if (successMessage != null && context.mounted) {
      showFloatingSuccessNotice(context, successMessage);
    }
  } catch (error) {
    if (context.mounted) {
      showFloatingErrorNotice(
        context,
        musicBoxControlErrorMessage(error, failureMessage),
      );
    }
  }
}

/// A box-height slot for the shared empty/loading/error widgets, which center
/// themselves and therefore need bounded height inside a sliver list.
Widget _musicBoxSliverBox(Widget child, {double height = 120}) {
  return SliverToBoxAdapter(child: SizedBox(height: height, child: child));
}

/// Tappable header of a collapsed/expanded group in the flat list.
class _MusicBoxGroupHeader extends StatelessWidget {
  const _MusicBoxGroupHeader({
    super.key,
    required this.title,
    required this.icon,
    required this.open,
    required this.playing,
    required this.onToggle,
    this.count,
  });

  final String title;
  final IconData icon;
  final bool open;
  final bool playing;
  final VoidCallback onToggle;
  final int? count;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      expanded: open,
      label: title,
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: onToggle,
          child: SizedBox(
            height: 36,
            child: Row(
              children: [
                Icon(
                  open ? Icons.expand_more : Icons.chevron_right,
                  size: 18,
                  color: UiColors.textMuted,
                ),
                const SizedBox(width: 4),
                Icon(
                  playing ? Icons.graphic_eq : icon,
                  size: 15,
                  color: playing ? UiColors.accent : UiColors.textSecondary,
                ),
                const SizedBox(width: 8),
                Text(
                  title,
                  style: TextStyle(
                    color: playing ? UiColors.accent : UiColors.text,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                if (count != null) ...[
                  const SizedBox(width: 6),
                  Text(
                    '$count',
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
    );
  }
}

/// The request queue: header with the search toggle and queue-wide actions,
/// the unfolded search panel, then either the search results or the rows.
class _MusicBoxQueueSection extends StatefulWidget {
  const _MusicBoxQueueSection({
    super.key,
    required this.headerKey,
    required this.scrollController,
    required this.state,
    required this.searchOpen,
    required this.query,
    required this.searchController,
    required this.searchResults,
    required this.searching,
    required this.searchError,
    required this.source,
    required this.onToggleSearch,
    required this.onSourceChanged,
    required this.onQueueResult,
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

  final GlobalKey headerKey;
  final ScrollController scrollController;
  final MusicBoxState state;
  final bool searchOpen;
  final String query;
  final TextEditingController searchController;
  final List<MusicBoxSearchResult> searchResults;
  final bool searching;
  final String? searchError;
  final String source;
  final VoidCallback onToggleSearch;
  final ValueChanged<String> onSourceChanged;
  final ValueChanged<MusicBoxSearchResult> onQueueResult;
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
  State<_MusicBoxQueueSection> createState() => _MusicBoxQueueSectionState();
}

class _MusicBoxQueueSectionState extends State<_MusicBoxQueueSection> {
  // 26px raised buttons paint 34px tall (lift + depth), plus the 4px gap.
  static const double _headerHeight = 34 + 4;
  static const double _searchPanelHeight = _musicBoxControlHeight + 8;

  final GlobalKey _currentRowKey = GlobalKey();
  final FocusNode _searchFocus = FocusNode();
  String? _revealedItemId;
  String? _playingNowItemId;

  bool get _isActive => music_box_display.musicBoxSourceIsActive(
    widget.state,
    MusicBoxActiveSourceType.temporary,
  );

  List<MusicBoxQueueItem> get _items =>
      _isActive ? widget.state.queue : widget.state.temporaryQueue;

  @override
  void didUpdateWidget(covariant _MusicBoxQueueSection oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!oldWidget.searchOpen && widget.searchOpen) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _searchFocus.requestFocus();
      });
    }
  }

  @override
  void dispose() {
    _searchFocus.dispose();
    super.dispose();
  }

  /// Rows are built lazily, so a current item deep in the queue has no
  /// context yet. Jump near it first using the row minimum height as an
  /// estimate, then let [Scrollable.ensureVisible] correct for taller rows
  /// once the real row is laid out.
  void _scheduleRevealCurrent(String currentItemId, int currentIndex) {
    if (currentItemId.isEmpty ||
        currentIndex < 0 ||
        _revealedItemId == currentItemId) {
      return;
    }
    _revealedItemId = currentItemId;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !widget.scrollController.hasClients) return;
      final position = widget.scrollController.position;
      final estimatedCenter =
          _headerHeight +
          (widget.searchOpen ? _searchPanelHeight : 0) +
          currentIndex * _musicBoxRowMinHeight +
          _musicBoxRowMinHeight / 2;
      final target = estimatedCenter - position.viewportDimension / 2;
      widget.scrollController.jumpTo(
        target.clamp(position.minScrollExtent, position.maxScrollExtent),
      );
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        final currentContext = _currentRowKey.currentContext;
        if (currentContext == null) return;
        unawaited(
          Scrollable.ensureVisible(
            currentContext,
            alignment: 0.5,
            duration: Duration.zero,
          ),
        );
      });
    });
  }

  /// Plays [item]. While the request queue is the active source this is a
  /// server `play_now`; while a saved playlist is playing, picking a queue
  /// song switches the room back to the request queue starting from it.
  Future<void> _playItem(MusicBoxQueueItem item) async {
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || roomId == null || _playingNowItemId != null) {
      return;
    }
    final switching = !_isActive;
    setState(() => _playingNowItemId = item.id);
    await _musicBoxRunWrite(
      context,
      write: () => switching
          ? controller.activatePlaylist(
              roomId: roomId,
              sourceType: MusicBoxActiveSourceType.temporary,
              startItemId: item.id,
            )
          : controller.playNow(roomId: roomId, item: item),
      onStateChanged: widget.onStateChanged,
      failureMessage: switching ? '播放点歌队列失败，请重试' : '优先播放失败，请重试',
      successMessage: switching ? null : '已优先播放',
    );
    if (mounted) setState(() => _playingNowItemId = null);
  }

  Future<void> _confirmAndClear() async {
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || roomId == null) return;
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
    await _musicBoxRunWrite(
      context,
      write: () => controller.clearTemporaryQueue(
        roomId: roomId,
        currentState: widget.state,
      ),
      onStateChanged: widget.onStateChanged,
      failureMessage: '清空点歌队列失败，请重试',
      successMessage: '已清空点歌队列',
    );
  }

  String _headerTitle(int count) {
    final status = !_isActive
        ? '未播放'
        : switch (widget.state.playback.state) {
            MusicBoxPlaybackState.playing => '正在播放',
            MusicBoxPlaybackState.paused => '已暂停',
            MusicBoxPlaybackState.stopped => '未播放',
          };
    return '点歌队列 · $count 首 · $status';
  }

  Widget _header() {
    final state = widget.state;
    final items = _items;
    final canWrite = widget.controller != null && widget.roomId != null;
    return KeyedSubtree(
      key: widget.headerKey,
      child: Padding(
        padding: const EdgeInsets.only(bottom: 4),
        child: _MusicBoxSectionHeader(
          leading: Icon(
            _isActive && music_box_display.musicBoxHasActivePlayback(state)
                ? Icons.graphic_eq
                : Icons.queue_music,
            size: 15,
            color:
                _isActive && music_box_display.musicBoxHasActivePlayback(state)
                ? UiColors.accent
                : UiColors.textSecondary,
          ),
          title: _headerTitle(items.length),
          trailing: [
            _MusicBoxRowAction(
              key: const ValueKey<String>('music-box-search-toggle'),
              icon: widget.searchOpen ? Icons.search_off : Icons.search,
              label: widget.searchOpen ? '收起搜索' : '搜索歌曲点歌',
              tone: ButtonTone.primary,
              selected: widget.searchOpen,
              onPressed: widget.onToggleSearch,
              size: 26,
            ),
            if (items.isNotEmpty && state.canClearTemporary)
              _MusicBoxRowAction(
                key: const ValueKey<String>('music-box-queue-clear'),
                icon: Icons.delete_sweep_outlined,
                label: '清空点歌队列',
                tone: ButtonTone.danger,
                onPressed: canWrite
                    ? () => unawaited(_confirmAndClear())
                    : null,
                size: 26,
              ),
          ],
        ),
      ),
    );
  }

  /// The search panel unfolded under the header: field plus source picker.
  Widget _searchPanel() {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        key: const ValueKey<String>('music-box-search-panel'),
        children: [
          Expanded(
            child: Input(
              key: const ValueKey<String>('music-box-search-input'),
              controller: widget.searchController,
              focusNode: _searchFocus,
              hintText: '搜索歌曲点歌',
              prefixIcon: Icons.search,
              showClearButton: true,
              maxLines: 1,
              height: _musicBoxControlHeight,
            ),
          ),
          const SizedBox(width: 6),
          SegmentedControl<String>(
            key: const ValueKey<String>('music-box-search-source'),
            height: _musicBoxControlHeight,
            value: music_box_display.normalizedMusicBoxSource(widget.source),
            segments: [
              for (final source in music_box_display.musicBoxSources)
                Segment(value: source.id, label: source.label),
            ],
            onChanged: widget.onSourceChanged,
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final items = _items;
    final hasQuery = widget.searchOpen && widget.query.trim().isNotEmpty;
    final currentItemId = _isActive ? widget.state.playback.currentItemId : '';
    if (!hasQuery) {
      _scheduleRevealCurrent(
        currentItemId,
        currentItemId.isEmpty
            ? -1
            : items.indexWhere((item) => item.id == currentItemId),
      );
    }
    return SliverMainAxisGroup(
      slivers: [
        SliverToBoxAdapter(child: _header()),
        if (widget.searchOpen) SliverToBoxAdapter(child: _searchPanel()),
        if (hasQuery)
          _MusicBoxSearchResults(
            results: widget.searchResults,
            query: widget.query,
            searching: widget.searching,
            error: widget.searchError,
            source: widget.source,
            controller: widget.controller,
            roomId: widget.roomId,
            temporaryQueue: widget.state.temporaryQueue,
            onQueueResult: widget.onQueueResult,
          )
        else if (items.isEmpty)
          _musicBoxSliverBox(
            const _MusicBoxEmpty(
              icon: Icons.queue_music,
              message: '点歌队列为空',
              hint: '点击右上角的搜索按钮点歌',
            ),
          )
        else
          SliverList.builder(
            key: const ValueKey<String>('music-box-queue-list'),
            itemCount: items.length,
            itemBuilder: (context, index) {
              final item = items[index];
              final isCurrent =
                  item.id.isNotEmpty && item.id == currentItemId;
              final row = _queueRow(item, index, isCurrent);
              if (!isCurrent) return row;
              return KeyedSubtree(key: _currentRowKey, child: row);
            },
          ),
        const SliverToBoxAdapter(child: SizedBox(height: 8)),
      ],
    );
  }

  Widget _queueRow(MusicBoxQueueItem item, int index, bool isCurrent) {
    final canWrite = widget.controller != null && widget.roomId != null;
    // Active queue: play-now needs a ready file and the server's per-item
    // permission. Inactive queue: any song can switch the room back to the
    // request queue, subject to the switch capability.
    final canPlay =
        canWrite &&
        !isCurrent &&
        (_isActive
            ? item.canPlayNow && item.status == MusicBoxQueueItemStatus.ready
            : widget.state.canSwitchTemporary);
    final requester = item.requestedBy;
    return _MusicBoxTrackRow(
      key: ValueKey<String>('music-box-queue-tile:${item.id}'),
      leading: _MusicBoxQueueRowLeading(
        item: item,
        index: index,
        isCurrent: isCurrent,
      ),
      title: item.title,
      subtitle: _musicBoxQueueSubtitle(item),
      subtitleColor: item.status == MusicBoxQueueItemStatus.failed
          ? UiColors.danger
          : UiColors.textSecondary,
      selected: isCurrent,
      cardResetKey: Object.hash(
        item.id,
        item.status,
        item.canPlayNow,
        requester?.avatarUrl,
        requester?.avatarLabel,
        widget.state.activeSource.type,
        widget.state.activeSource.name,
      ),
      cardBuilder: (_) => _MusicBoxSongCard.queue(
        item: item,
        isCurrent: isCurrent,
        queueSongCount: _items.length,
        controller: widget.controller,
        roomId: widget.roomId,
        activeSource: widget.state.activeSource,
        onStateChanged: widget.onStateChanged,
        currentUser: widget.currentUser,
        onResolveUserProfile: widget.onResolveUserProfile,
        onResolveRoomProfile: widget.onResolveRoomProfile,
        onEnterCommonRoom: widget.onEnterCommonRoom,
        userProfileActionBuilder: widget.userProfileActionBuilder,
      ),
      trailing: [
        if (requester != null)
          _MusicBoxRequesterAvatar(
            key: ValueKey<String>('music-box-queue-requester:${item.id}'),
            requester: requester,
            currentUser: widget.currentUser,
            onResolveUserProfile: widget.onResolveUserProfile,
            onResolveRoomProfile: widget.onResolveRoomProfile,
            onEnterCommonRoom: widget.onEnterCommonRoom,
            userProfileActionBuilder: widget.userProfileActionBuilder,
          ),
        if (canPlay)
          _MusicBoxRowAction(
            key: ValueKey<String>('music-box-queue-play-now:${item.id}'),
            icon: Icons.play_arrow,
            label: _isActive ? '优先播放' : '从这首歌开始播放点歌队列',
            tone: ButtonTone.primary,
            loading: _playingNowItemId == item.id,
            onPressed: () => unawaited(_playItem(item)),
          ),
        if (item.canRemove)
          _MusicBoxRowAction(
            key: ValueKey<String>('music-box-queue-remove:${item.id}'),
            icon: Icons.close,
            label: '从点歌队列删除',
            tone: ButtonTone.danger,
            onPressed: () => widget.onRemoveItem(item),
          ),
      ],
    );
  }
}

String _musicBoxQueueSubtitle(MusicBoxQueueItem item) {
  final artist = item.artist.trim().isEmpty ? '未知艺人' : item.artist.trim();
  final status = music_box_display.musicBoxQueueStatusLabel(item);
  return status == null ? artist : '$artist · $status';
}

/// A collapsed group of saved playlists (room or personal). Loads its list the
/// first time it is opened; each playlist row can unfold its tracks in place.
/// If the active source belongs to this scope but is not in the list (someone
/// else's personal playlist, or a template deleted after activation) it is
/// pinned on top so the playing snapshot always has a row.
class _MusicBoxPlaylistGroup extends StatefulWidget {
  const _MusicBoxPlaylistGroup({
    super.key,
    required this.headerKey,
    required this.type,
    required this.open,
    required this.openPlaylistKey,
    required this.state,
    required this.controller,
    required this.roomId,
    required this.playlistsRevision,
    required this.room,
    required this.currentUser,
    required this.onStateChanged,
    required this.onToggle,
    required this.onTogglePlaylist,
    required this.onCreateFirstPlaylist,
    required this.onEdit,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
  });

  final GlobalKey headerKey;
  final MusicBoxActiveSourceType type;
  final bool open;
  final String? openPlaylistKey;
  final MusicBoxState state;
  final MusicBoxController? controller;
  final String? roomId;
  final int playlistsRevision;
  final PublicRoom? room;
  final CurrentUser? currentUser;
  final ValueChanged<MusicBoxState>? onStateChanged;
  final VoidCallback onToggle;
  final ValueChanged<String> onTogglePlaylist;
  final VoidCallback? onCreateFirstPlaylist;
  final MusicPlaylistEditCallback? onEdit;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;

  bool get roomScoped => type == MusicBoxActiveSourceType.roomPlaylist;

  @override
  State<_MusicBoxPlaylistGroup> createState() => _MusicBoxPlaylistGroupState();
}

class _MusicBoxPlaylistGroupState extends State<_MusicBoxPlaylistGroup> {
  bool _loading = false;
  bool _loaded = false;
  String? _error;
  List<PersonalMusicPlaylist> _playlists = const [];
  int _loadGeneration = 0;

  @override
  void initState() {
    super.initState();
    if (widget.open) unawaited(_load());
  }

  @override
  void didUpdateWidget(covariant _MusicBoxPlaylistGroup oldWidget) {
    super.didUpdateWidget(oldWidget);
    final identityChanged =
        oldWidget.type != widget.type ||
        oldWidget.roomId != widget.roomId ||
        oldWidget.controller != widget.controller;
    if (identityChanged) {
      _loaded = false;
      _playlists = const [];
    }
    final needsLoad =
        widget.open &&
        (identityChanged ||
            !_loaded ||
            oldWidget.playlistsRevision != widget.playlistsRevision);
    if (needsLoad && !_loading) unawaited(_load());
  }

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || (widget.roomScoped && roomId == null)) {
      setState(() {
        _loading = false;
        _loaded = true;
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
      if (!mounted || generation != _loadGeneration) return;
      setState(() {
        _loading = false;
        _loaded = true;
        _playlists = page.playlists;
      });
    } catch (_) {
      if (!mounted || generation != _loadGeneration) return;
      setState(() {
        _loading = false;
        _loaded = true;
        _error = '加载歌单失败';
      });
    }
  }

  Widget _leadingCard(PersonalMusicPlaylist playlist, bool playing, Widget icon) {
    final active = widget.state.activeSource;
    final isActive = music_box_display.musicBoxSourceIsActive(
      widget.state,
      widget.type,
      playlist.id,
    );
    final creator = widget.roomScoped
        ? null
        : isActive
        ? _musicBoxActiveSourceOwner(active, widget.currentUser)
        : widget.currentUser?.toSummary();
    final onEdit = widget.onEdit;
    return MusicPlaylistHoverCard(
      key: ValueKey<String>('music-box-playlist-card-anchor:${playlist.id}'),
      data: MusicPlaylistCardData(
        id: playlist.id,
        name: playlist.name,
        songCount: playlist.itemCount,
        createdAt: playlist.createdAt ?? (isActive ? active.createdAt : null),
        creator: creator,
        room: widget.roomScoped ? widget.room : null,
        showPlayingStatus: playing,
      ),
      currentUser: widget.currentUser,
      onResolveUserProfile: widget.onResolveUserProfile,
      onResolveRoomProfile: widget.onResolveRoomProfile,
      onEnterCommonRoom: widget.onEnterCommonRoom,
      userProfileActionBuilder: widget.userProfileActionBuilder,
      // Playback starts from a song inside the playlist, never from the
      // playlist as a whole: the card offers no play-all.
      onPlayAll: null,
      showPrimaryActionWhenDisabled: false,
      onViewPlaylist: onEdit == null
          ? () async => widget.onTogglePlaylist(playlist.id)
          : () => onEdit(playlist.id),
      secondaryActionLabel: onEdit == null ? '查看歌单' : '编辑歌单',
      secondaryActionIcon: onEdit == null
          ? Icons.queue_music
          : Icons.edit_outlined,
      child: SizedBox.square(dimension: 22, child: Center(child: icon)),
    );
  }

  List<PersonalMusicPlaylist> _entries() {
    final state = widget.state;
    final active = state.activeSource;
    final activeInScope = active.type == widget.type && active.id.isNotEmpty;
    final activeListed =
        activeInScope && _playlists.any((playlist) => playlist.id == active.id);
    return [
      if (activeInScope && !activeListed)
        PersonalMusicPlaylist(
          id: active.id,
          name: music_box_display.musicBoxActiveSourceLabel(active),
          description: '',
          revision: 0,
          itemCount: state.queue.length,
          createdAt: active.createdAt,
          updatedAt: null,
        ),
      ..._playlists,
    ];
  }

  List<Widget> _body() {
    if (_loading && !_loaded) return [_musicBoxSliverBox(const _MusicBoxLoading())];
    final entries = _entries();
    final error = _error;
    if (error != null) {
      // The playing snapshot needs no list load, so its pinned row stays
      // reachable under the retry state.
      return [
        _musicBoxSliverBox(
          _MusicBoxRetryState(message: error, onRetry: () => unawaited(_load())),
        ),
        ..._rows(entries),
      ];
    }
    if (entries.isEmpty) {
      return [
        _musicBoxSliverBox(
          _MusicBoxEmpty(
            icon: widget.roomScoped ? Icons.meeting_room : Icons.person,
            message: widget.roomScoped ? '还没有房间歌单' : '还没有个人歌单',
            actionKey: ValueKey<String>(
              widget.roomScoped
                  ? 'music-box-create-first-room-playlist'
                  : 'music-box-create-first-personal-playlist',
            ),
            actionLabel: widget.onCreateFirstPlaylist == null
                ? null
                : '新建第一个歌单',
            onAction: widget.onCreateFirstPlaylist,
          ),
          height: 140,
        ),
      ];
    }
    return _rows(entries);
  }

  List<Widget> _rows(List<PersonalMusicPlaylist> entries) {
    final state = widget.state;
    final active = state.activeSource;
    final hasActivePlayback = music_box_display.musicBoxHasActivePlayback(
      state,
    );
    final typeValue = musicBoxActiveSourceTypeValue(widget.type);
    return [
      for (final playlist in entries)
        _playlistBlock(
          playlist,
          typeValue: typeValue,
          isActive: active.type == widget.type && playlist.id == active.id,
          hasActivePlayback: hasActivePlayback,
        ),
      const SliverToBoxAdapter(child: SizedBox(height: 4)),
    ];
  }

  /// One playlist and, when unfolded, its tracks — framed as a single block
  /// with a gap below it, so neighbouring playlists and the next group read
  /// as separate things.
  Widget _playlistBlock(
    PersonalMusicPlaylist playlist, {
    required String typeValue,
    required bool isActive,
    required bool hasActivePlayback,
  }) {
    final playing = isActive && hasActivePlayback;
    final open =
        widget.openPlaylistKey ==
        music_box_display.musicBoxPlaylistKey(widget.type, playlist.id);
    final row = _MusicBoxPlaylistRow(
      key: ValueKey<String>('music-box-playlist-row:$typeValue:${playlist.id}'),
      name: playlist.name,
      itemCount: playlist.itemCount,
      playing: playing,
      open: open,
      icon: widget.roomScoped ? Icons.meeting_room : Icons.person,
      onOpen: () => widget.onTogglePlaylist(playlist.id),
      hoverColor: UiColors.surface,
      leadingCard: (icon) => _leadingCard(playlist, playing, icon),
    );
    // Every playlist is the same framed block; unfolding only adds the
    // tracks inside it, so folded and unfolded rows read as one kind of thing.
    return SliverPadding(
      padding: const EdgeInsets.only(bottom: 6),
      sliver: DecoratedSliver(
        decoration: BoxDecoration(
          color: UiColors.surfaceLow,
          borderRadius: BorderRadius.circular(UiRadii.md),
          border: Border.all(
            color: open ? UiColors.borderStrong : UiColors.border,
          ),
        ),
        sliver: SliverPadding(
          padding: const EdgeInsets.all(3),
          sliver: SliverMainAxisGroup(
            slivers: [
              SliverToBoxAdapter(child: row),
              if (open) ...[
                const SliverToBoxAdapter(child: SizedBox(height: 2)),
                _MusicBoxPlaylistTracks(
                  key: ValueKey<String>(
                    'music-box-playlist-tracks:${playlist.id}',
                  ),
                  type: widget.type,
                  playlistId: playlist.id,
                  state: widget.state,
                  controller: widget.controller,
                  roomId: widget.roomId,
                  playlistsRevision: widget.playlistsRevision,
                  onStateChanged: widget.onStateChanged,
                  currentUser: widget.currentUser,
                  onResolveUserProfile: widget.onResolveUserProfile,
                  onResolveRoomProfile: widget.onResolveRoomProfile,
                  onEnterCommonRoom: widget.onEnterCommonRoom,
                  userProfileActionBuilder: widget.userProfileActionBuilder,
                ),
                const SliverToBoxAdapter(child: SizedBox(height: 3)),
              ],
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final active = widget.state.activeSource;
    final playing =
        active.type == widget.type &&
        music_box_display.musicBoxHasActivePlayback(widget.state);
    return SliverMainAxisGroup(
      slivers: [
        SliverToBoxAdapter(
          child: KeyedSubtree(
            key: widget.headerKey,
            child: _MusicBoxGroupHeader(
              key: ValueKey<String>(
                'music-box-group-header:${musicBoxActiveSourceTypeValue(widget.type)}',
              ),
              title: widget.roomScoped ? '房间歌单' : '我的歌单',
              icon: widget.roomScoped ? Icons.meeting_room : Icons.person,
              open: widget.open,
              playing: playing,
              count: _loaded && _error == null ? _entries().length : null,
              onToggle: widget.onToggle,
            ),
          ),
        ),
        if (widget.open) ...[
          const SliverToBoxAdapter(child: SizedBox(height: 2)),
          ..._body(),
        ],
      ],
    );
  }
}

/// The tracks of one unfolded playlist, indented under its row. When the
/// playlist is the active source the rows are the authoritative activation
/// snapshot (live status, play-now); otherwise the saved template is loaded
/// and each row can start the playlist from that song. Presets are played
/// from here and edited elsewhere, so there are no request/add actions.
class _MusicBoxPlaylistTracks extends StatefulWidget {
  const _MusicBoxPlaylistTracks({
    super.key,
    required this.type,
    required this.playlistId,
    required this.state,
    required this.controller,
    required this.roomId,
    required this.playlistsRevision,
    required this.onStateChanged,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
  });

  final MusicBoxActiveSourceType type;
  final String playlistId;
  final MusicBoxState state;
  final MusicBoxController? controller;
  final String? roomId;
  final int playlistsRevision;
  final ValueChanged<MusicBoxState>? onStateChanged;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;

  bool get roomScoped => type == MusicBoxActiveSourceType.roomPlaylist;

  @override
  State<_MusicBoxPlaylistTracks> createState() =>
      _MusicBoxPlaylistTracksState();
}

class _MusicBoxPlaylistTracksState extends State<_MusicBoxPlaylistTracks> {
  static const double _indent = 12;

  bool _loading = false;
  String? _error;
  List<PersonalMusicPlaylistItem> _items = const [];
  int _loadGeneration = 0;
  String? _startingItemId;

  bool get _isActive => music_box_display.musicBoxSourceIsActive(
    widget.state,
    widget.type,
    widget.playlistId,
  );

  @override
  void initState() {
    super.initState();
    if (!_isActive) unawaited(_load());
  }

  @override
  void didUpdateWidget(covariant _MusicBoxPlaylistTracks oldWidget) {
    super.didUpdateWidget(oldWidget);
    final wasActive = music_box_display.musicBoxSourceIsActive(
      oldWidget.state,
      oldWidget.type,
      oldWidget.playlistId,
    );
    if (oldWidget.playlistId != widget.playlistId ||
        oldWidget.type != widget.type ||
        oldWidget.roomId != widget.roomId ||
        oldWidget.controller != widget.controller ||
        oldWidget.playlistsRevision != widget.playlistsRevision ||
        (wasActive && !_isActive)) {
      if (!_isActive) unawaited(_load());
    }
  }

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || (widget.roomScoped && roomId == null)) {
      setState(() {
        _loading = false;
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
          ? await controller.loadRoomPlaylist(
              roomId: roomId!,
              playlistId: widget.playlistId,
            )
          : await controller.loadMyPlaylist(playlistId: widget.playlistId);
      if (!mounted || generation != _loadGeneration) return;
      setState(() {
        _loading = false;
        _items = page.items;
      });
    } catch (_) {
      if (!mounted || generation != _loadGeneration) return;
      setState(() {
        _loading = false;
        _error = '加载歌曲失败';
      });
    }
  }

  Future<void> _activateFrom(PersonalMusicPlaylistItem item) async {
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || roomId == null || _startingItemId != null) {
      return;
    }
    setState(() => _startingItemId = item.id);
    await _musicBoxRunWrite(
      context,
      write: () => controller.activatePlaylist(
        roomId: roomId,
        sourceType: widget.type,
        playlistId: widget.playlistId,
        startItemId: item.id,
      ),
      onStateChanged: widget.onStateChanged,
      failureMessage: '播放歌曲失败，请重试',
    );
    if (mounted) setState(() => _startingItemId = null);
  }

  Future<void> _playNow(MusicBoxQueueItem item) async {
    final controller = widget.controller;
    final roomId = widget.roomId;
    if (controller == null || roomId == null || _startingItemId != null) {
      return;
    }
    setState(() => _startingItemId = item.id);
    await _musicBoxRunWrite(
      context,
      write: () => controller.playNow(roomId: roomId, item: item),
      onStateChanged: widget.onStateChanged,
      failureMessage: '优先播放失败，请重试',
      successMessage: '已优先播放',
    );
    if (mounted) setState(() => _startingItemId = null);
  }

  Widget _indented(Widget row) {
    return Padding(padding: const EdgeInsets.only(left: _indent), child: row);
  }

  Widget _snapshotRow(MusicBoxQueueItem item, int index) {
    final state = widget.state;
    final isCurrent =
        item.id.isNotEmpty && item.id == state.playback.currentItemId;
    final canPlayNow =
        widget.controller != null &&
        widget.roomId != null &&
        item.canPlayNow &&
        item.status == MusicBoxQueueItemStatus.ready &&
        !isCurrent;
    return _indented(
      _MusicBoxTrackRow(
        key: ValueKey<String>('music-box-queue-tile:${item.id}'),
        hoverColor: UiColors.surface,
        leading: _MusicBoxQueueRowLeading(
          item: item,
          index: index,
          isCurrent: isCurrent,
        ),
        title: item.title,
        subtitle: _musicBoxQueueSubtitle(item),
        subtitleColor: item.status == MusicBoxQueueItemStatus.failed
            ? UiColors.danger
            : UiColors.textSecondary,
        selected: isCurrent,
        cardResetKey: Object.hash(item.id, item.status, item.canPlayNow),
        cardBuilder: (_) => _MusicBoxSongCard.queue(
          item: item,
          isCurrent: isCurrent,
          queueSongCount: state.queue.length,
          controller: widget.controller,
          roomId: widget.roomId,
          activeSource: state.activeSource,
          onStateChanged: widget.onStateChanged,
          currentUser: widget.currentUser,
          onResolveUserProfile: widget.onResolveUserProfile,
          onResolveRoomProfile: widget.onResolveRoomProfile,
          onEnterCommonRoom: widget.onEnterCommonRoom,
          userProfileActionBuilder: widget.userProfileActionBuilder,
        ),
        trailing: [
          if (canPlayNow)
            _MusicBoxRowAction(
              key: ValueKey<String>('music-box-queue-play-now:${item.id}'),
              icon: Icons.play_arrow,
              label: '优先播放',
              tone: ButtonTone.primary,
              loading: _startingItemId == item.id,
              onPressed: () => unawaited(_playNow(item)),
            ),
        ],
      ),
    );
  }

  Widget _templateRow(PersonalMusicPlaylistItem item, int index) {
    final result = _musicBoxPlaylistItemAsResult(item);
    final identity = '${item.source}:${item.trackId}';
    final artists = music_box_display.musicBoxArtistsLabel(item.artists);
    final canWrite = widget.controller != null && widget.roomId != null;
    return _indented(
      _MusicBoxTrackRow(
        key: ValueKey<String>('music-box-playlist-track:$identity'),
        hoverColor: UiColors.surface,
        leading: Text(
          '${index + 1}',
          style: const TextStyle(
            color: UiColors.textMuted,
            fontSize: 11,
            fontWeight: FontWeight.w600,
            fontFeatures: [FontFeature.tabularFigures()],
          ),
        ),
        title: item.title,
        subtitle: artists.isEmpty ? '未知艺人' : artists,
        cardResetKey: identity,
        // Information only: presets are not requested from here.
        cardBuilder: (_) => _MusicBoxSongCard.search(
          result: result,
          controller: widget.controller,
          roomId: widget.roomId,
          onQueueResult: null,
          alreadyInRequestQueue: false,
          catalogDurationMs: item.durationMs,
        ),
        trailing: [
          _MusicBoxRowAction(
            key: ValueKey<String>('music-box-playlist-track-play:$identity'),
            icon: Icons.play_arrow,
            label: '从这首歌开始播放歌单',
            tone: ButtonTone.primary,
            loading: _startingItemId == item.id,
            onPressed: canWrite ? () => unawaited(_activateFrom(item)) : null,
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_isActive) {
      final queue = widget.state.queue;
      if (queue.isEmpty) {
        return _musicBoxSliverBox(
          const _MusicBoxEmpty(icon: Icons.queue_music, message: '这个歌单还没有歌曲'),
          height: 96,
        );
      }
      return SliverList.builder(
        itemCount: queue.length,
        itemBuilder: (context, index) => _snapshotRow(queue[index], index),
      );
    }
    if (_loading) return _musicBoxSliverBox(const _MusicBoxLoading(), height: 96);
    final error = _error;
    if (error != null) {
      return _musicBoxSliverBox(
        _MusicBoxRetryState(message: error, onRetry: () => unawaited(_load())),
      );
    }
    if (_items.isEmpty) {
      return _musicBoxSliverBox(
        const _MusicBoxEmpty(icon: Icons.queue_music, message: '这个歌单还没有歌曲'),
        height: 96,
      );
    }
    return SliverList.builder(
      itemCount: _items.length,
      itemBuilder: (context, index) => _templateRow(_items[index], index),
    );
  }
}

/// Search hits for the current query, rendered as slivers under the queue
/// header. The only action is "加入点歌队列"; a track already in the queue shows
/// a disabled check instead of failing after the tap.
class _MusicBoxSearchResults extends StatelessWidget {
  const _MusicBoxSearchResults({
    required this.results,
    required this.query,
    required this.searching,
    required this.error,
    required this.source,
    required this.controller,
    required this.roomId,
    required this.temporaryQueue,
    required this.onQueueResult,
  });

  final List<MusicBoxSearchResult> results;
  final String query;
  final bool searching;
  final String? error;
  final String source;
  final MusicBoxController? controller;
  final String? roomId;
  final List<MusicBoxQueueItem> temporaryQueue;
  final ValueChanged<MusicBoxSearchResult> onQueueResult;

  @override
  Widget build(BuildContext context) {
    final message = error?.trim();
    final sourceLabel = music_box_display.musicBoxSourceLabel(
      music_box_display.normalizedMusicBoxSource(source),
    );
    final Widget body;
    if (searching && results.isEmpty) {
      body = _musicBoxSliverBox(const _MusicBoxLoading());
    } else if (message != null && message.isNotEmpty) {
      body = _musicBoxSliverBox(
        _MusicBoxEmpty(icon: Icons.error_outline, message: '搜索失败', hint: message),
      );
    } else if (results.isEmpty) {
      body = _musicBoxSliverBox(
        const _MusicBoxEmpty(
          icon: Icons.search_off,
          message: '没有找到相关歌曲',
          hint: '换个关键词，或切换搜索来源试试',
        ),
      );
    } else {
      body = SliverList.builder(
        key: const ValueKey<String>('music-box-search-results-list'),
        itemCount: results.length,
        itemBuilder: (context, index) => _row(context, results[index]),
      );
    }
    return SliverMainAxisGroup(
      slivers: [
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.only(bottom: 4),
            child: _MusicBoxSectionHeader(
              title: searching
                  ? '正在搜索 $sourceLabel…'
                  : '搜索结果 · $sourceLabel · ${results.length} 首',
            ),
          ),
        ),
        body,
      ],
    );
  }

  Widget _row(BuildContext context, MusicBoxSearchResult result) {
    final identity = '${result.source}:${result.trackId}';
    final artists = music_box_display.musicBoxArtistsLabel(result.artists);
    final alreadyQueued = music_box_display.musicBoxRequestQueueContainsTrack(
      temporaryQueue,
      source: result.source,
      trackId: result.trackId,
    );
    return _MusicBoxTrackRow(
      key: ValueKey<String>('music-box-search-tile:$identity'),
      leading: const Icon(Icons.music_note, size: 18, color: UiColors.accent),
      title: result.name,
      subtitle: artists.isEmpty ? '未知艺人' : artists,
      query: query,
      cardResetKey: '$identity:$alreadyQueued',
      cardBuilder: (_) => _MusicBoxSongCard.search(
        result: result,
        controller: controller,
        roomId: roomId,
        onQueueResult: alreadyQueued ? null : onQueueResult,
        alreadyInRequestQueue: alreadyQueued,
      ),
      trailing: [
        _MusicBoxRowAction(
          key: ValueKey<String>('music-box-search-add:$identity'),
          icon: alreadyQueued ? Icons.playlist_add_check : Icons.add,
          label: alreadyQueued ? '已在点歌队列' : '加入点歌队列',
          tone: alreadyQueued ? ButtonTone.neutral : ButtonTone.primary,
          onPressed: alreadyQueued ? null : () => onQueueResult(result),
        ),
      ],
    );
  }
}
