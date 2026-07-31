part of 'settings_page.dart';

class _PersonalMusicPlaylistsPanel extends StatefulWidget {
  const _PersonalMusicPlaylistsPanel({
    required this.controller,
    required this.reloadToken,
    required this.unavailableMessage,
    required this.onLoadingChanged,
  });

  final PersonalMusicPlaylistsController controller;
  final int reloadToken;
  final String unavailableMessage;
  final ValueChanged<bool> onLoadingChanged;

  @override
  State<_PersonalMusicPlaylistsPanel> createState() =>
      _PersonalMusicPlaylistsPanelState();
}

class _PersonalMusicPlaylistsPanelState
    extends State<_PersonalMusicPlaylistsPanel> {
  final _trackSearchController = TextEditingController();
  final _itemFilterController = TextEditingController();

  List<PersonalMusicPlaylist> _playlists = const [];
  PersonalMusicPlaylist? _activePlaylist;
  List<PersonalMusicPlaylistItem> _items = const [];
  List<MusicBoxSearchResult> _searchResults = const [];
  Set<String> _selectedItemIds = const {};

  String _searchSource = musicBoxDefaultSource;
  String _filterSource = '';
  String _appliedFilterKeyword = '';
  int _itemsPage = 1;
  int _itemTotal = 0;
  int _maxPlaylists = 50;
  int _maxPlaylistItems = 500;
  bool _itemsHaveMore = false;
  bool _loading = false;
  bool _loadingItems = false;
  bool _loadingMore = false;
  bool _searching = false;
  bool _creating = false;
  bool _deletingPlaylist = false;
  bool _deletingItems = false;
  bool _managingItems = false;
  String? _error;
  final Set<String> _busyTrackKeys = {};
  final Set<String> _busyItemIds = {};
  int _loadGeneration = 0;
  int _searchGeneration = 0;

  bool get _filterActive => personalPlaylistFilterActive(
    keyword: _appliedFilterKeyword,
    source: _filterSource,
  );

  bool get _busy =>
      _loading ||
      _loadingItems ||
      _loadingMore ||
      _creating ||
      _deletingPlaylist ||
      _deletingItems;

  @override
  void initState() {
    super.initState();
    scheduleMicrotask(_loadPlaylists);
  }

  @override
  void didUpdateWidget(covariant _PersonalMusicPlaylistsPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.reloadToken != widget.reloadToken ||
        oldWidget.controller.api != widget.controller.api) {
      scheduleMicrotask(_loadPlaylists);
    }
  }

  @override
  void dispose() {
    _loadGeneration += 1;
    _searchGeneration += 1;
    _trackSearchController.dispose();
    _itemFilterController.dispose();
    super.dispose();
  }

  void _setLoading(bool loading) {
    if (!mounted || _loading == loading) return;
    setState(() => _loading = loading);
    widget.onLoadingChanged(loading);
  }

  Future<void> _loadPlaylists() async {
    if (!widget.controller.available) return;
    final generation = ++_loadGeneration;
    _setLoading(true);
    if (mounted) setState(() => _error = null);
    try {
      final result = await widget.controller.loadPlaylists();
      if (!mounted || generation != _loadGeneration || result == null) return;
      final activeID = _activePlaylist?.id;
      PersonalMusicPlaylist? refreshedActive;
      if (activeID != null) {
        for (final playlist in result.playlists) {
          if (playlist.id == activeID) {
            refreshedActive = playlist;
            break;
          }
        }
      }
      setState(() {
        _playlists = result.playlists;
        _maxPlaylists = result.maxPlaylists;
        _maxPlaylistItems = result.maxPlaylistItems;
        _activePlaylist = refreshedActive;
        if (activeID != null && refreshedActive == null) {
          _items = const [];
          _selectedItemIds = const {};
          _managingItems = false;
        }
      });
      if (refreshedActive != null) {
        scheduleMicrotask(() => _loadItems(reset: true));
      }
    } catch (error) {
      if (!mounted || generation != _loadGeneration) return;
      setState(() => _error = error.toString());
    } finally {
      if (mounted && generation == _loadGeneration) _setLoading(false);
    }
  }

  Future<void> _openPlaylist(PersonalMusicPlaylist playlist) async {
    setState(() {
      _activePlaylist = playlist;
      _items = const [];
      _selectedItemIds = const {};
      _managingItems = false;
      _error = null;
      _itemFilterController.clear();
      _appliedFilterKeyword = '';
      _filterSource = '';
    });
    await _loadItems(reset: true);
  }

  void _closePlaylist() {
    _searchGeneration += 1;
    setState(() {
      _activePlaylist = null;
      _items = const [];
      _searchResults = const [];
      _selectedItemIds = const {};
      _managingItems = false;
      _error = null;
      _trackSearchController.clear();
      _itemFilterController.clear();
      _appliedFilterKeyword = '';
      _filterSource = '';
    });
  }

  Future<void> _loadItems({required bool reset}) async {
    final playlist = _activePlaylist;
    if (playlist == null || !widget.controller.available) return;
    if (reset && _loadingItems) return;
    if (!reset && (_loadingMore || !_itemsHaveMore)) return;
    final generation = reset ? ++_loadGeneration : _loadGeneration;
    final page = reset ? 1 : _itemsPage + 1;
    setState(() {
      if (reset) {
        _loadingItems = true;
        _selectedItemIds = const {};
      } else {
        _loadingMore = true;
      }
      _error = null;
    });
    try {
      final result = await widget.controller.loadItems(
        playlistId: playlist.id,
        page: page,
        keyword: _appliedFilterKeyword,
        source: _filterSource,
      );
      if (!mounted ||
          generation != _loadGeneration ||
          result == null ||
          _activePlaylist?.id != playlist.id) {
        return;
      }
      setState(() {
        _activePlaylist = result.playlist;
        _items = reset
            ? result.items
            : <PersonalMusicPlaylistItem>[..._items, ...result.items];
        _itemsPage = result.page;
        _itemTotal = result.total;
        _itemsHaveMore = result.hasMore;
      });
    } catch (error) {
      if (!mounted || generation != _loadGeneration) return;
      setState(() => _error = error.toString());
    } finally {
      if (mounted && generation == _loadGeneration) {
        setState(() {
          _loadingItems = false;
          _loadingMore = false;
        });
      }
    }
  }

  Future<void> _createPlaylist() async {
    if (_busy || _playlists.length >= _maxPlaylists) return;
    final name = await showDialog<String>(
      context: context,
      builder: (context) => const _CreateMusicPlaylistDialog(),
    );
    if (!mounted || name == null) return;
    setState(() {
      _creating = true;
      _error = null;
    });
    try {
      await widget.controller.createPlaylist(name);
      await _loadPlaylists();
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _creating = false);
    }
  }

  Future<void> _deletePlaylist(PersonalMusicPlaylist playlist) async {
    if (_busy) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StickerConfirmDialog(
        title: '删除歌单',
        body: '确定删除“${playlist.name}”吗？歌单中的歌曲记录也会一并删除。',
        confirmLabel: '删除',
        confirmIcon: Icons.delete_outline,
        danger: true,
      ),
    );
    if (!mounted || confirmed != true) return;
    setState(() {
      _deletingPlaylist = true;
      _error = null;
    });
    try {
      await widget.controller.deletePlaylist(playlist.id);
      if (_activePlaylist?.id == playlist.id) _closePlaylist();
      await _loadPlaylists();
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _deletingPlaylist = false);
    }
  }

  Future<void> _searchTracks() async {
    final keyword = _trackSearchController.text.trim();
    if (keyword.isEmpty || _searching) return;
    final generation = ++_searchGeneration;
    setState(() {
      _searching = true;
      _error = null;
    });
    try {
      final results = await widget.controller.searchTracks(
        keyword: keyword,
        source: _searchSource,
      );
      if (!mounted || generation != _searchGeneration || results == null) {
        return;
      }
      setState(() => _searchResults = results);
    } catch (error) {
      if (!mounted || generation != _searchGeneration) return;
      setState(() {
        _searchResults = const [];
        _error = error.toString();
      });
    } finally {
      if (mounted && generation == _searchGeneration) {
        setState(() => _searching = false);
      }
    }
  }

  Future<void> _addTrack(MusicBoxSearchResult track) async {
    final playlist = _activePlaylist;
    if (playlist == null) return;
    final key = '${track.source}:${track.trackId}';
    if (_busyTrackKeys.contains(key)) return;
    setState(() {
      _busyTrackKeys.add(key);
      _error = null;
    });
    try {
      await widget.controller.addTrack(playlistId: playlist.id, track: track);
      await _loadItems(reset: true);
      await _reloadPlaylistSummariesWithoutClosing();
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _busyTrackKeys.remove(key));
    }
  }

  Future<void> _reloadPlaylistSummariesWithoutClosing() async {
    final result = await widget.controller.loadPlaylists();
    if (!mounted || result == null) return;
    final activeID = _activePlaylist?.id;
    setState(() {
      _playlists = result.playlists;
      _maxPlaylists = result.maxPlaylists;
      _maxPlaylistItems = result.maxPlaylistItems;
      if (activeID != null) {
        for (final playlist in result.playlists) {
          if (playlist.id == activeID) {
            _activePlaylist = playlist;
            break;
          }
        }
      }
    });
  }

  void _applyFilter() {
    final keyword = _itemFilterController.text.trim();
    if (keyword == _appliedFilterKeyword) return;
    setState(() {
      _appliedFilterKeyword = keyword;
      _managingItems = false;
      _selectedItemIds = const {};
    });
    unawaited(_loadItems(reset: true));
  }

  void _setFilterSource(String source) {
    if (_filterSource == source) return;
    setState(() {
      _filterSource = source;
      _managingItems = false;
      _selectedItemIds = const {};
    });
    unawaited(_loadItems(reset: true));
  }

  void _setSearchSource(String source) {
    if (_searchSource == source) return;
    _searchGeneration += 1;
    setState(() {
      _searchSource = source;
      _searchResults = const [];
      _searching = false;
    });
  }

  void _toggleManagingItems() {
    if (_busy) return;
    setState(() {
      _managingItems = !_managingItems;
      _selectedItemIds = const {};
    });
  }

  void _toggleItemSelection(String itemID) {
    if (!_managingItems || _deletingItems) return;
    setState(() {
      _selectedItemIds = toggledPersonalPlaylistSelection(
        _selectedItemIds,
        itemID,
      );
    });
  }

  void _toggleSelectVisible() {
    final visibleIDs = _items.map((item) => item.id).toSet();
    final allSelected =
        visibleIDs.isNotEmpty && visibleIDs.every(_selectedItemIds.contains);
    setState(() {
      final next = Set<String>.of(_selectedItemIds);
      if (allSelected) {
        next.removeAll(visibleIDs);
      } else {
        next.addAll(visibleIDs);
      }
      _selectedItemIds = next;
    });
  }

  Future<void> _deleteSelectedItems() async {
    final playlist = _activePlaylist;
    if (playlist == null || _selectedItemIds.isEmpty || _deletingItems) {
      return;
    }
    final count = _selectedItemIds.length;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StickerConfirmDialog(
        title: '批量删除歌曲',
        body: '确定从“${playlist.name}”中删除已选择的 $count 首歌曲吗？',
        confirmLabel: '删除',
        confirmIcon: Icons.delete_outline,
        danger: true,
      ),
    );
    if (!mounted || confirmed != true) return;
    setState(() {
      _deletingItems = true;
      _error = null;
    });
    try {
      await widget.controller.deleteItems(
        playlistId: playlist.id,
        itemIds: _selectedItemIds.toList(),
      );
      setState(() {
        _selectedItemIds = const {};
        _managingItems = false;
      });
      await _loadItems(reset: true);
      await _reloadPlaylistSummariesWithoutClosing();
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _deletingItems = false);
    }
  }

  Future<void> _deleteSingleItem(PersonalMusicPlaylistItem item) async {
    final playlist = _activePlaylist;
    if (playlist == null || _busyItemIds.contains(item.id)) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StickerConfirmDialog(
        title: '删除歌曲',
        body: '确定从“${playlist.name}”中删除“${item.title}”吗？',
        confirmLabel: '删除',
        confirmIcon: Icons.delete_outline,
        danger: true,
      ),
    );
    if (!mounted || confirmed != true) return;
    setState(() {
      _busyItemIds.add(item.id);
      _error = null;
    });
    try {
      await widget.controller.deleteItems(
        playlistId: playlist.id,
        itemIds: [item.id],
      );
      await _loadItems(reset: true);
      await _reloadPlaylistSummariesWithoutClosing();
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _busyItemIds.remove(item.id));
    }
  }

  Future<void> _moveItem(PersonalMusicPlaylistItem item, int delta) async {
    final playlist = _activePlaylist;
    if (playlist == null || _filterActive || _busyItemIds.contains(item.id)) {
      return;
    }
    setState(() {
      _busyItemIds.add(item.id);
      _error = null;
    });
    try {
      await widget.controller.moveItem(
        playlistId: playlist.id,
        itemId: item.id,
        delta: delta,
      );
      await _loadItems(reset: true);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _busyItemIds.remove(item.id));
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!widget.controller.available) {
      return SettingsList(
        children: [_SettingsEmptyState(text: widget.unavailableMessage)],
      );
    }
    final playlist = _activePlaylist;
    return playlist == null ? _buildPlaylistList() : _buildPlaylistManager();
  }

  Widget _buildPlaylistList() {
    return SettingsList(
      children: [
        _SettingsGroup(
          title: '我的歌单',
          trailing: Text(
            '${_playlists.length} / $_maxPlaylists',
            style: const TextStyle(
              color: _textMuted,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
          children: [
            Button(
              key: const ValueKey('create-personal-music-playlist'),
              onPressed: !_busy && _playlists.length < _maxPlaylists
                  ? _createPlaylist
                  : null,
              loading: _creating,
              tone: ButtonTone.primary,
              icon: const Icon(Icons.playlist_add),
              width: double.infinity,
              child: const Text('新建歌单'),
            ),
            if (_error != null) _MusicPlaylistErrorText(_error!),
            if (_loading && _playlists.isEmpty)
              const SizedBox(
                height: 128,
                child: Center(child: CircularProgressIndicator(color: _cyan)),
              )
            else if (_playlists.isEmpty)
              const _SettingsEmptyState(text: '暂无歌单，点击上方按钮新建')
            else
              for (final playlist in _playlists)
                _MusicPlaylistSummaryTile(
                  playlist: playlist,
                  busy: _busy,
                  onManage: () => _openPlaylist(playlist),
                  onDelete: () => _deletePlaylist(playlist),
                ),
          ],
        ),
      ],
    );
  }

  Widget _buildPlaylistManager() {
    final playlist = _activePlaylist!;
    final allVisibleSelected =
        _items.isNotEmpty &&
        _items.every((item) => _selectedItemIds.contains(item.id));
    return SettingsList(
      children: [
        _SettingsGroup(
          title: playlist.name,
          trailing: Text(
            '${playlist.itemCount} / $_maxPlaylistItems 首',
            style: const TextStyle(
              color: _textMuted,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
          children: [
            Button(
              key: const ValueKey('back-to-personal-music-playlists'),
              onPressed: _busy ? null : _closePlaylist,
              icon: const Icon(Icons.arrow_back),
              width: double.infinity,
              child: const Text('返回歌单列表'),
            ),
            if (_error != null) _MusicPlaylistErrorText(_error!),
          ],
        ),
        _SettingsGroup(
          title: '搜索添加',
          children: [
            SegmentedControl<String>(
              expanded: true,
              value: _searchSource,
              onChanged: _setSearchSource,
              segments: [
                for (final source in musicBoxSources)
                  Segment(value: source.id, label: source.label),
              ],
            ),
            _MusicPlaylistSearchRow(
              controller: _trackSearchController,
              hintText: '搜索歌曲添加到歌单',
              buttonLabel: '搜索',
              loading: _searching,
              onSubmitted: (_) => _searchTracks(),
              onPressed: _searchTracks,
            ),
            if (!_searching && _searchResults.isEmpty)
              const _SettingsEmptyState(text: '搜索歌曲后可添加到当前歌单')
            else
              for (final result in _searchResults)
                _MusicPlaylistSearchResultTile(
                  result: result,
                  loading: _busyTrackKeys.contains(
                    '${result.source}:${result.trackId}',
                  ),
                  onAdd: () => _addTrack(result),
                ),
          ],
        ),
        _SettingsGroup(
          title: '管理歌曲',
          trailing: Text(
            _filterActive ? '筛选 $_itemTotal 首' : '共 $_itemTotal 首',
            style: const TextStyle(
              color: _textMuted,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
          children: [
            _MusicPlaylistSearchRow(
              controller: _itemFilterController,
              hintText: '筛选歌名或歌手',
              buttonLabel: '筛选',
              loading: _loadingItems,
              onSubmitted: (_) => _applyFilter(),
              onPressed: _applyFilter,
            ),
            SegmentedControl<String>(
              expanded: true,
              value: _filterSource,
              onChanged: _setFilterSource,
              segments: [
                const Segment(value: '', label: '全部'),
                for (final source in musicBoxSources)
                  Segment(value: source.id, label: source.label),
              ],
            ),
            StickerActionGrid(
              actions: [
                StickerActionGridEntry(
                  label: _managingItems ? '取消管理' : '批量管理',
                  button: Button(
                    onPressed: _busy ? null : _toggleManagingItems,
                    selected: _managingItems,
                    tone: _managingItems
                        ? ButtonTone.primary
                        : ButtonTone.neutral,
                    icon: Icon(
                      _managingItems ? Icons.close : Icons.checklist_rtl,
                    ),
                    width: double.infinity,
                    child: Text(_managingItems ? '取消管理' : '批量管理'),
                  ),
                ),
                if (_managingItems)
                  StickerActionGridEntry(
                    label: '删除',
                    button: Button(
                      onPressed: !_deletingItems && _selectedItemIds.isNotEmpty
                          ? _deleteSelectedItems
                          : null,
                      loading: _deletingItems,
                      tone: ButtonTone.danger,
                      icon: const Icon(Icons.delete_outline),
                      width: double.infinity,
                      child: const Text('删除'),
                    ),
                  ),
                if (_managingItems)
                  StickerActionGridEntry(
                    label: allVisibleSelected ? '取消全选' : '全选当前页',
                    button: Button(
                      onPressed: _items.isEmpty ? null : _toggleSelectVisible,
                      selected: allVisibleSelected,
                      icon: Icon(
                        allVisibleSelected
                            ? Icons.check_box
                            : Icons.check_box_outline_blank,
                      ),
                      width: double.infinity,
                      child: Text(allVisibleSelected ? '取消全选' : '全选当前页'),
                    ),
                  ),
              ],
            ),
            if (_filterActive)
              const Text(
                '筛选状态下暂不提供排序，清除筛选后可调整顺序。',
                style: TextStyle(color: _textMuted, fontSize: 12),
              ),
            if (_loadingItems && _items.isEmpty)
              const SizedBox(
                height: 128,
                child: Center(child: CircularProgressIndicator(color: _cyan)),
              )
            else if (_items.isEmpty)
              _SettingsEmptyState(text: _filterActive ? '没有匹配的歌曲' : '歌单还是空的')
            else
              for (var index = 0; index < _items.length; index++)
                _MusicPlaylistItemTile(
                  item: _items[index],
                  managing: _managingItems,
                  selected: _selectedItemIds.contains(_items[index].id),
                  busy: _busyItemIds.contains(_items[index].id),
                  canMoveUp: !_filterActive && index > 0,
                  canMoveDown:
                      !_filterActive &&
                      (index < _items.length - 1 || _itemsHaveMore),
                  onTap: () => _toggleItemSelection(_items[index].id),
                  onDelete: () => _deleteSingleItem(_items[index]),
                  onMoveUp: () => _moveItem(_items[index], -1),
                  onMoveDown: () => _moveItem(_items[index], 1),
                ),
            if (_itemsHaveMore)
              Button(
                key: const ValueKey('load-more-personal-playlist-items'),
                onPressed: _loadingMore ? null : () => _loadItems(reset: false),
                loading: _loadingMore,
                icon: const Icon(Icons.expand_more),
                width: double.infinity,
                child: const Text('加载更多'),
              ),
          ],
        ),
      ],
    );
  }
}

class _MusicPlaylistSummaryTile extends StatelessWidget {
  const _MusicPlaylistSummaryTile({
    required this.playlist,
    required this.busy,
    required this.onManage,
    required this.onDelete,
  });

  final PersonalMusicPlaylist playlist;
  final bool busy;
  final VoidCallback onManage;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final information = Row(
      children: [
        const Icon(Icons.queue_music, color: _cyan, size: 24),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                playlist.name,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: _textPrimary,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                '${playlist.itemCount} 首歌曲',
                style: const TextStyle(color: _textMuted, fontSize: 12),
              ),
            ],
          ),
        ),
      ],
    );
    final actions = [
      Button(
        onPressed: busy ? null : onManage,
        icon: const Icon(Icons.edit_outlined),
        tone: ButtonTone.primary,
        child: const Text('管理'),
      ),
      Button(
        onPressed: busy ? null : onDelete,
        icon: const Icon(Icons.delete_outline),
        tone: ButtonTone.danger,
        child: const Text('删除'),
      ),
    ];
    return _SettingsSubPanel(
      child: LayoutBuilder(
        builder: (context, constraints) {
          if (constraints.maxWidth < 430) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                information,
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: Button(
                        onPressed: busy ? null : onManage,
                        icon: const Icon(Icons.edit_outlined),
                        tone: ButtonTone.primary,
                        width: double.infinity,
                        child: const Text('管理'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Button(
                        onPressed: busy ? null : onDelete,
                        icon: const Icon(Icons.delete_outline),
                        tone: ButtonTone.danger,
                        width: double.infinity,
                        child: const Text('删除'),
                      ),
                    ),
                  ],
                ),
              ],
            );
          }
          return Row(
            children: [
              Expanded(child: information),
              const SizedBox(width: 12),
              ...[
                for (var index = 0; index < actions.length; index++) ...[
                  if (index > 0) const SizedBox(width: 10),
                  actions[index],
                ],
              ],
            ],
          );
        },
      ),
    );
  }
}

class _MusicPlaylistSearchRow extends StatelessWidget {
  const _MusicPlaylistSearchRow({
    required this.controller,
    required this.hintText,
    required this.buttonLabel,
    required this.loading,
    required this.onSubmitted,
    required this.onPressed,
  });

  final TextEditingController controller;
  final String hintText;
  final String buttonLabel;
  final bool loading;
  final ValueChanged<String> onSubmitted;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final input = Input(
      controller: controller,
      hintText: hintText,
      prefixIcon: Icons.search,
      showClearButton: true,
      onSubmitted: onSubmitted,
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 360) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              input,
              const SizedBox(height: 10),
              Button(
                onPressed: loading ? null : onPressed,
                loading: loading,
                tone: ButtonTone.primary,
                icon: const Icon(Icons.search),
                width: double.infinity,
                child: Text(buttonLabel),
              ),
            ],
          );
        }
        return Row(
          children: [
            Expanded(child: input),
            const SizedBox(width: 10),
            Button(
              onPressed: loading ? null : onPressed,
              loading: loading,
              tone: ButtonTone.primary,
              icon: const Icon(Icons.search),
              child: Text(buttonLabel),
            ),
          ],
        );
      },
    );
  }
}

class _MusicPlaylistSearchResultTile extends StatelessWidget {
  const _MusicPlaylistSearchResultTile({
    required this.result,
    required this.loading,
    required this.onAdd,
  });

  final MusicBoxSearchResult result;
  final bool loading;
  final VoidCallback onAdd;

  @override
  Widget build(BuildContext context) {
    return _SettingsSubPanel(
      child: Row(
        children: [
          const Icon(Icons.music_note, color: _cyan, size: 20),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  result.name,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: _textPrimary,
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  '${personalPlaylistArtistsLabel(result.artists)} · '
                  '${personalPlaylistSourceLabel(result.source)}',
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: _textMuted, fontSize: 12),
                ),
              ],
            ),
          ),
          const SizedBox(width: 10),
          Button(
            onPressed: loading ? null : onAdd,
            loading: loading,
            tone: ButtonTone.primary,
            icon: const Icon(Icons.add),
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: const Text('添加'),
          ),
        ],
      ),
    );
  }
}

class _MusicPlaylistItemTile extends StatelessWidget {
  const _MusicPlaylistItemTile({
    required this.item,
    required this.managing,
    required this.selected,
    required this.busy,
    required this.canMoveUp,
    required this.canMoveDown,
    required this.onTap,
    required this.onDelete,
    required this.onMoveUp,
    required this.onMoveDown,
  });

  final PersonalMusicPlaylistItem item;
  final bool managing;
  final bool selected;
  final bool busy;
  final bool canMoveUp;
  final bool canMoveDown;
  final VoidCallback onTap;
  final VoidCallback onDelete;
  final VoidCallback onMoveUp;
  final VoidCallback onMoveDown;

  @override
  Widget build(BuildContext context) {
    final information = InkWell(
      onTap: managing ? onTap : null,
      borderRadius: BorderRadius.circular(UiRadii.md),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          children: [
            if (managing) ...[
              Icon(
                selected ? Icons.check_box : Icons.check_box_outline_blank,
                size: 20,
                color: selected ? _cyan : _textMuted,
              ),
              const SizedBox(width: 10),
            ],
            const Icon(Icons.music_note, size: 19, color: _cyan),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: _textPrimary,
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    '${personalPlaylistArtistsLabel(item.artists)} · '
                    '${personalPlaylistSourceLabel(item.source)}',
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: _textMuted, fontSize: 12),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
    final controls = Wrap(
      spacing: 8,
      runSpacing: 8,
      alignment: WrapAlignment.end,
      children: [
        ButtonIcon(
          tooltip: '上移',
          onPressed: !busy && canMoveUp ? onMoveUp : null,
          icon: const Icon(Icons.arrow_upward),
          size: 36,
        ),
        ButtonIcon(
          tooltip: '下移',
          onPressed: !busy && canMoveDown ? onMoveDown : null,
          icon: const Icon(Icons.arrow_downward),
          size: 36,
        ),
        ButtonIcon(
          tooltip: '删除',
          onPressed: busy ? null : onDelete,
          tone: ButtonTone.danger,
          icon: const Icon(Icons.delete_outline),
          size: 36,
          loading: busy,
        ),
      ],
    );
    return _SettingsSubPanel(
      child: LayoutBuilder(
        builder: (context, constraints) {
          if (constraints.maxWidth < 420) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                information,
                if (!managing) ...[
                  const SizedBox(height: 10),
                  Align(alignment: Alignment.centerRight, child: controls),
                ],
              ],
            );
          }
          return Row(
            children: [
              Expanded(child: information),
              if (!managing) ...[const SizedBox(width: 10), controls],
            ],
          );
        },
      ),
    );
  }
}

class _MusicPlaylistErrorText extends StatelessWidget {
  const _MusicPlaylistErrorText(this.message);

  final String message;

  @override
  Widget build(BuildContext context) {
    return Text(
      message,
      style: const TextStyle(color: _danger, fontSize: 12, height: 1.4),
    );
  }
}

class _CreateMusicPlaylistDialog extends StatefulWidget {
  const _CreateMusicPlaylistDialog();

  @override
  State<_CreateMusicPlaylistDialog> createState() =>
      _CreateMusicPlaylistDialogState();
}

class _CreateMusicPlaylistDialogState
    extends State<_CreateMusicPlaylistDialog> {
  final _controller = TextEditingController();
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    final name = normalizedPersonalPlaylistName(_controller.text);
    if (name == null) {
      setState(() => _error = '请输入 1～64 个字符的歌单名');
      return;
    }
    Navigator.of(context).pop(name);
  }

  @override
  Widget build(BuildContext context) {
    return DialogFrame(
      title: '新建歌单',
      icon: Icons.playlist_add,
      adaptiveActions: [
        ResponsiveDialogAction(
          label: '取消',
          onPressed: () => Navigator.of(context).pop(),
        ),
        ResponsiveDialogAction(
          label: '创建',
          icon: Icons.add,
          tone: ButtonTone.primary,
          onPressed: _submit,
        ),
      ],
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Input(
            key: const ValueKey('personal-music-playlist-name-input'),
            controller: _controller,
            hintText: '歌单名称',
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _submit(),
          ),
          if (_error != null) ...[
            const SizedBox(height: 8),
            _MusicPlaylistErrorText(_error!),
          ],
        ],
      ),
    );
  }
}
