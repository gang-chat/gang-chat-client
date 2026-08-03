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
  List<PersonalMusicPlaylist> _playlists = const [];
  PersonalMusicPlaylist? _activePlaylist;
  List<PersonalMusicPlaylistItem> _items = const [];
  List<String> _selectedPlaylistIds = const [];
  Set<String> _selectedItemIds = const {};

  String _playlistFilterKeyword = '';
  String _playlistCountFilter = personalPlaylistCountAll;
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
  bool _creating = false;
  bool _deletingPlaylists = false;
  bool _pinningPlaylists = false;
  bool _deletingItems = false;
  bool _pinningItems = false;
  bool _managingPlaylists = false;
  bool _managingItems = false;
  String? _error;
  final Set<String> _busyPlaylistIds = {};
  final Set<String> _busyItemIds = {};
  int _playlistLoadGeneration = 0;
  int _itemLoadGeneration = 0;

  bool get _filterActive => personalPlaylistFilterActive(
    keyword: _appliedFilterKeyword,
    source: _filterSource,
  );

  bool get _playlistFilterActive => personalPlaylistListFilterActive(
    keyword: _playlistFilterKeyword,
    countFilter: _playlistCountFilter,
  );

  bool get _playlistManagementBusy =>
      _loading ||
      _creating ||
      _deletingPlaylists ||
      _pinningPlaylists ||
      _busyPlaylistIds.isNotEmpty;

  bool get _busy =>
      _loading ||
      _loadingItems ||
      _loadingMore ||
      _creating ||
      _deletingPlaylists ||
      _pinningPlaylists ||
      _busyPlaylistIds.isNotEmpty ||
      _deletingItems ||
      _pinningItems;

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
    _playlistLoadGeneration += 1;
    _itemLoadGeneration += 1;
    super.dispose();
  }

  void _setLoading(bool loading) {
    if (!mounted || _loading == loading) return;
    setState(() => _loading = loading);
    widget.onLoadingChanged(loading);
  }

  Future<void> _loadPlaylists() async {
    if (!widget.controller.available) return;
    final generation = ++_playlistLoadGeneration;
    _setLoading(true);
    if (mounted) setState(() => _error = null);
    try {
      final result = await widget.controller.loadPlaylists();
      if (!mounted || generation != _playlistLoadGeneration || result == null) {
        return;
      }
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
      if (activeID != null && refreshedActive == null) {
        _itemLoadGeneration += 1;
      }
      setState(() {
        _playlists = result.playlists;
        final playlistIDs = {
          for (final playlist in result.playlists) playlist.id,
        };
        _selectedPlaylistIds = [
          for (final id in _selectedPlaylistIds)
            if (playlistIDs.contains(id)) id,
        ];
        _maxPlaylists = result.maxPlaylists;
        _maxPlaylistItems = result.maxPlaylistItems;
        _activePlaylist = refreshedActive;
        if (activeID != null && refreshedActive == null) {
          _items = const [];
          _selectedItemIds = const {};
          _managingItems = false;
          _loadingItems = false;
          _loadingMore = false;
        }
      });
      if (refreshedActive != null) {
        scheduleMicrotask(() => _loadItems(reset: true));
      }
    } catch (error) {
      if (!mounted || generation != _playlistLoadGeneration) return;
      setState(() => _error = error.toString());
    } finally {
      if (mounted && generation == _playlistLoadGeneration) {
        _setLoading(false);
      }
    }
  }

  Future<void> _openPlaylist(PersonalMusicPlaylist playlist) async {
    setState(() {
      _activePlaylist = playlist;
      _items = const [];
      _selectedItemIds = const {};
      _managingItems = false;
      _error = null;
      _appliedFilterKeyword = '';
      _filterSource = '';
    });
    await _loadItems(reset: true);
  }

  void _closePlaylist() {
    _itemLoadGeneration += 1;
    setState(() {
      _activePlaylist = null;
      _items = const [];
      _selectedItemIds = const {};
      _managingItems = false;
      _loadingItems = false;
      _loadingMore = false;
      _error = null;
      _appliedFilterKeyword = '';
      _filterSource = '';
    });
  }

  Future<void> _loadItems({required bool reset}) async {
    final playlist = _activePlaylist;
    if (playlist == null || !widget.controller.available) return;
    if (!reset && (_loadingItems || _loadingMore || !_itemsHaveMore)) return;
    final generation = reset ? ++_itemLoadGeneration : _itemLoadGeneration;
    final page = reset ? 1 : _itemsPage + 1;
    setState(() {
      if (reset) {
        _loadingItems = true;
        _loadingMore = false;
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
          generation != _itemLoadGeneration ||
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
      if (!mounted || generation != _itemLoadGeneration) return;
      setState(() => _error = error.toString());
    } finally {
      if (mounted && generation == _itemLoadGeneration) {
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
      builder: (context) => const _MusicPlaylistNameDialog(
        title: '新建歌单',
        icon: Icons.playlist_add,
        confirmLabel: '创建',
        confirmIcon: Icons.add,
      ),
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

  Future<void> _renamePlaylist(PersonalMusicPlaylist playlist) async {
    if (_playlistManagementBusy) return;
    final name = await showDialog<String>(
      context: context,
      builder: (context) => _MusicPlaylistNameDialog(
        title: '重命名歌单',
        icon: Icons.edit_outlined,
        confirmLabel: '重命名',
        confirmIcon: Icons.check,
        initialName: playlist.name,
      ),
    );
    if (!mounted || name == null || name == playlist.name) return;
    setState(() {
      _busyPlaylistIds.add(playlist.id);
      _error = null;
    });
    try {
      await widget.controller.renamePlaylist(
        playlistId: playlist.id,
        name: name,
      );
      await _loadPlaylists();
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) {
        setState(() => _busyPlaylistIds.remove(playlist.id));
      }
    }
  }

  void _togglePlaylistManageMode() {
    if (_playlistManagementBusy) return;
    setState(() {
      _managingPlaylists = !_managingPlaylists;
      _selectedPlaylistIds = const [];
    });
  }

  void _togglePlaylistSelection(String playlistId) {
    if (_playlistManagementBusy) return;
    setState(() {
      _selectedPlaylistIds = toggledPersonalPlaylistListSelection(
        _selectedPlaylistIds,
        playlistId,
      );
    });
  }

  void _toggleSelectVisiblePlaylists(
    List<PersonalMusicPlaylist> visiblePlaylists,
  ) {
    if (_playlistManagementBusy || visiblePlaylists.isEmpty) return;
    setState(() {
      _selectedPlaylistIds = toggledVisiblePersonalPlaylistSelection(
        selectedPlaylistIds: _selectedPlaylistIds,
        visiblePlaylists: visiblePlaylists,
      );
    });
  }

  Future<void> _openPlaylistFilter() async {
    if (_playlistManagementBusy) return;
    final draft = await showDialog<PersonalPlaylistFilterDraft>(
      context: context,
      builder: (context) => _MusicPlaylistFilterDialog(
        keyword: _playlistFilterKeyword,
        countFilter: _playlistCountFilter,
      ),
    );
    if (!mounted || draft == null) return;
    setState(() {
      _playlistFilterKeyword = draft.keyword;
      _playlistCountFilter = draft.countFilter;
      _selectedPlaylistIds = const [];
    });
  }

  Future<void> _deleteSelectedPlaylists() async {
    if (_playlistManagementBusy || _selectedPlaylistIds.isEmpty) return;
    final selectedIDs = List<String>.of(_selectedPlaylistIds);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StickerConfirmDialog(
        title: '删除歌单',
        body: '确定删除选中的 ${selectedIDs.length} 个歌单吗？歌单中的歌曲记录也会一并删除。',
        confirmLabel: '删除',
        confirmIcon: Icons.delete_outline,
        danger: true,
      ),
    );
    if (!mounted || confirmed != true) return;
    setState(() {
      _deletingPlaylists = true;
      _error = null;
    });
    final failedIDs = <String>[];
    Object? firstError;
    for (final playlistId in selectedIDs) {
      try {
        await widget.controller.deletePlaylist(playlistId);
      } catch (error) {
        failedIDs.add(playlistId);
        firstError ??= error;
      }
    }
    if (!mounted) return;
    await _loadPlaylists();
    if (!mounted) return;
    setState(() {
      _deletingPlaylists = false;
      if (failedIDs.isEmpty) {
        _managingPlaylists = false;
        _selectedPlaylistIds = const [];
        _error = null;
      } else {
        final currentIDs = {for (final playlist in _playlists) playlist.id};
        _selectedPlaylistIds = [
          for (final id in failedIDs)
            if (currentIDs.contains(id)) id,
        ];
        final deletedCount = selectedIDs.length - failedIDs.length;
        _error = '已删除 $deletedCount 个歌单，${failedIDs.length} 个删除失败：$firstError';
      }
    });
  }

  Future<void> _pinSelectedPlaylists() async {
    if (_playlistManagementBusy ||
        personalPlaylistOrderWithSelectionPinnedToFront(
              playlists: _playlists,
              selectedPlaylistIds: _selectedPlaylistIds,
            ) ==
            null) {
      return;
    }
    final selectedIDs = List<String>.of(_selectedPlaylistIds);
    setState(() {
      _pinningPlaylists = true;
      _error = null;
    });
    try {
      await widget.controller.pinPlaylists(selectedIDs);
      await _loadPlaylists();
      if (mounted) setState(() => _selectedPlaylistIds = const []);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _pinningPlaylists = false);
    }
  }

  Future<void> _movePlaylist(PersonalMusicPlaylist playlist, int delta) async {
    if (_playlistManagementBusy || _playlistFilterActive) return;
    setState(() {
      _busyPlaylistIds.add(playlist.id);
      _error = null;
    });
    try {
      await widget.controller.movePlaylist(
        playlistId: playlist.id,
        delta: delta,
      );
      await _loadPlaylists();
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _busyPlaylistIds.remove(playlist.id));
    }
  }

  Future<void> _deleteSinglePlaylist(PersonalMusicPlaylist playlist) async {
    if (_playlistManagementBusy) return;
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
      _busyPlaylistIds.add(playlist.id);
      _error = null;
    });
    try {
      await widget.controller.deletePlaylist(playlist.id);
      await _loadPlaylists();
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _busyPlaylistIds.remove(playlist.id));
    }
  }

  void _openOrSelectPlaylist(PersonalMusicPlaylist playlist) {
    if (_managingPlaylists) {
      _togglePlaylistSelection(playlist.id);
      return;
    }
    unawaited(_openPlaylist(playlist));
  }

  Future<void> _openTrackSearchDialog() async {
    final playlist = _activePlaylist;
    if (playlist == null || _busy) return;
    await showDialog<void>(
      context: context,
      builder: (context) => _MusicPlaylistTrackSearchDialog(
        controller: widget.controller,
        playlistId: playlist.id,
        onTrackAdded: _refreshAfterTrackAdded,
      ),
    );
  }

  Future<void> _refreshAfterTrackAdded() async {
    if (!mounted || _activePlaylist == null) return;
    await _loadItems(reset: true);
    await _reloadPlaylistSummariesWithoutClosing();
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

  Future<void> _openItemFilterDialog() async {
    if (_busy) return;
    final draft = await showDialog<PersonalPlaylistItemFilterDraft>(
      context: context,
      builder: (context) => _MusicPlaylistItemFilterDialog(
        keyword: _appliedFilterKeyword,
        source: _filterSource,
      ),
    );
    if (!mounted || draft == null) return;
    if (draft.keyword == _appliedFilterKeyword &&
        draft.source == _filterSource) {
      return;
    }
    setState(() {
      _appliedFilterKeyword = draft.keyword;
      _filterSource = draft.source;
      _selectedItemIds = const {};
    });
    await _loadItems(reset: true);
  }

  void _toggleManagingItems() {
    if (_busy) return;
    setState(() {
      _managingItems = !_managingItems;
      _selectedItemIds = const {};
    });
  }

  void _toggleItemSelection(String itemID) {
    if (!_managingItems || _deletingItems || _pinningItems) return;
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

  Future<void> _pinSelectedItems() async {
    final playlist = _activePlaylist;
    if (playlist == null || _selectedItemIds.isEmpty || _pinningItems) return;
    final selectedIds = List<String>.of(_selectedItemIds);
    setState(() {
      _pinningItems = true;
      _error = null;
    });
    try {
      await widget.controller.pinItems(
        playlistId: playlist.id,
        selectedItemIds: selectedIds,
      );
      await _loadItems(reset: true);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _pinningItems = false);
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
    final visiblePlaylists = filteredPersonalMusicPlaylists(
      _playlists,
      keyword: _playlistFilterKeyword,
      countFilter: _playlistCountFilter,
    );
    final selectionNumbers = personalPlaylistSelectionNumbers(
      _selectedPlaylistIds,
    );
    final allVisibleSelected = personalPlaylistAllVisibleSelected(
      selectedPlaylistIds: _selectedPlaylistIds,
      visiblePlaylists: visiblePlaylists,
    );
    final canPinSelection =
        personalPlaylistOrderWithSelectionPinnedToFront(
          playlists: _playlists,
          selectedPlaylistIds: _selectedPlaylistIds,
        ) !=
        null;
    return SettingsList(
      children: [
        _SettingsGroup(
          title: '歌单管理',
          spacing: 10,
          trailing: Text(
            _playlistFilterActive
                ? '筛选 ${visiblePlaylists.length} / ${_playlists.length}'
                : '${_playlists.length} / $_maxPlaylists',
            style: const TextStyle(
              color: _textMuted,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
          children: [
            StickerActionGrid(
              actions: [
                StickerActionGridEntry(
                  label: _managingPlaylists ? '删除' : '新建歌单',
                  button: Button(
                    key: _managingPlaylists
                        ? const ValueKey(
                            'delete-selected-personal-music-playlists',
                          )
                        : const ValueKey('create-personal-music-playlist'),
                    onPressed: _managingPlaylists
                        ? (_selectedPlaylistIds.isNotEmpty &&
                                  !_playlistManagementBusy
                              ? _deleteSelectedPlaylists
                              : null)
                        : (!_playlistManagementBusy &&
                                  _playlists.length < _maxPlaylists
                              ? _createPlaylist
                              : null),
                    loading: _managingPlaylists
                        ? _deletingPlaylists
                        : _creating,
                    tone: _managingPlaylists
                        ? ButtonTone.danger
                        : ButtonTone.primary,
                    icon: Icon(
                      _managingPlaylists
                          ? Icons.delete_outline
                          : Icons.playlist_add,
                    ),
                    width: double.infinity,
                    child: Text(_managingPlaylists ? '删除' : '新建歌单'),
                  ),
                ),
                StickerActionGridEntry(
                  label: _managingPlaylists ? '取消管理' : '管理',
                  button: Button(
                    key: const ValueKey('manage-personal-music-playlists'),
                    onPressed: _playlistManagementBusy
                        ? null
                        : _togglePlaylistManageMode,
                    selected: _managingPlaylists,
                    tone: _managingPlaylists
                        ? ButtonTone.primary
                        : ButtonTone.neutral,
                    icon: Icon(
                      _managingPlaylists ? Icons.close : Icons.checklist_rtl,
                    ),
                    width: double.infinity,
                    child: Text(_managingPlaylists ? '取消管理' : '管理'),
                  ),
                ),
                StickerActionGridEntry(
                  label: '筛选',
                  button: Button(
                    key: const ValueKey('filter-personal-music-playlists'),
                    onPressed: _playlistManagementBusy
                        ? null
                        : _openPlaylistFilter,
                    selected: _playlistFilterActive,
                    icon: const Icon(Icons.filter_alt_outlined),
                    width: double.infinity,
                    child: const Text('筛选'),
                  ),
                ),
                if (_managingPlaylists)
                  StickerActionGridEntry(
                    label: '分享',
                    button: const Button(
                      key: ValueKey('share-personal-music-playlists'),
                      onPressed: null,
                      icon: Icon(Icons.share_outlined),
                      width: double.infinity,
                      child: Text('分享'),
                    ),
                  ),
                if (_managingPlaylists)
                  StickerActionGridEntry(
                    label: '置顶',
                    button: Button(
                      key: const ValueKey(
                        'pin-selected-personal-music-playlists',
                      ),
                      onPressed: !_playlistManagementBusy && canPinSelection
                          ? _pinSelectedPlaylists
                          : null,
                      loading: _pinningPlaylists,
                      icon: const Icon(Icons.vertical_align_top),
                      width: double.infinity,
                      child: const Text('置顶'),
                    ),
                  ),
                if (_managingPlaylists)
                  StickerActionGridEntry(
                    label: allVisibleSelected ? '取消全选' : '全选',
                    button: Button(
                      key: const ValueKey(
                        'select-visible-personal-music-playlists',
                      ),
                      onPressed:
                          !_playlistManagementBusy &&
                              visiblePlaylists.isNotEmpty
                          ? () =>
                                _toggleSelectVisiblePlaylists(visiblePlaylists)
                          : null,
                      selected: allVisibleSelected,
                      icon: Icon(
                        allVisibleSelected
                            ? Icons.check_box
                            : Icons.check_box_outline_blank,
                      ),
                      width: double.infinity,
                      child: Text(allVisibleSelected ? '取消全选' : '全选'),
                    ),
                  ),
              ],
            ),
            if (_error != null) _MusicPlaylistErrorText(_error!),
            if (_loading && _playlists.isEmpty)
              const SizedBox(
                height: 128,
                child: Center(child: CircularProgressIndicator(color: _cyan)),
              )
            else if (_playlists.isEmpty)
              const _SettingsEmptyState(text: '暂无歌单，点击上方按钮新建')
            else if (visiblePlaylists.isEmpty)
              const _SettingsEmptyState(text: '没有符合筛选条件的歌单')
            else
              _MusicPlaylistSummaryList(
                playlists: visiblePlaylists,
                managing: _managingPlaylists,
                selectionNumbers: selectionNumbers,
                busy: _managingPlaylists && _playlistManagementBusy,
                filterActive: _playlistFilterActive,
                onTap: _openOrSelectPlaylist,
                onRename: _renamePlaylist,
                onMove: _movePlaylist,
                onDelete: _deleteSinglePlaylist,
              ),
          ],
        ),
      ],
    );
  }

  Widget _buildPlaylistManager() {
    final playlist = _activePlaylist!;
    final itemSelectionNumbers = personalPlaylistSelectionNumbers(
      _selectedItemIds.toList(),
    );
    final allVisibleSelected =
        _items.isNotEmpty &&
        _items.every((item) => _selectedItemIds.contains(item.id));
    return SettingsList(
      children: [
        _SettingsGroup(
          title: playlist.name,
          titleWidget: Row(
            key: const ValueKey('personal-music-playlist-header'),
            children: [
              ButtonIconPlain(
                key: const ValueKey('back-to-personal-music-playlists'),
                tooltip: '返回歌单列表',
                onPressed: _busy ? null : _closePlaylist,
                icon: const Icon(Icons.arrow_back_rounded),
              ),
              const SizedBox(width: 10),
              const Icon(Icons.queue_music_outlined, color: _cyan, size: 20),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  playlist.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: _textPrimary,
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
          spacing: 10,
          trailing: Text(
            _filterActive
                ? '筛选 $_itemTotal / ${playlist.itemCount} 首'
                : '${playlist.itemCount} / $_maxPlaylistItems 首',
            style: const TextStyle(
              color: _textMuted,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
          children: [
            StickerActionGrid(
              actions: [
                StickerActionGridEntry(
                  label: _managingItems ? '删除' : '搜索添加',
                  button: Button(
                    key: _managingItems
                        ? const ValueKey(
                            'delete-selected-personal-music-playlist-items',
                          )
                        : const ValueKey(
                            'search-add-personal-music-playlist-item',
                          ),
                    onPressed: _managingItems
                        ? (!_deletingItems && _selectedItemIds.isNotEmpty
                              ? _deleteSelectedItems
                              : null)
                        : (_busy ? null : _openTrackSearchDialog),
                    loading: _managingItems ? _deletingItems : false,
                    tone: _managingItems
                        ? ButtonTone.danger
                        : ButtonTone.primary,
                    icon: Icon(
                      _managingItems
                          ? Icons.delete_outline
                          : Icons.playlist_add,
                    ),
                    width: double.infinity,
                    child: Text(_managingItems ? '删除' : '搜索添加'),
                  ),
                ),
                StickerActionGridEntry(
                  label: _managingItems ? '取消管理' : '管理',
                  button: Button(
                    key: const ValueKey('manage-personal-music-playlist-items'),
                    onPressed: _busy ? null : _toggleManagingItems,
                    selected: _managingItems,
                    tone: _managingItems
                        ? ButtonTone.primary
                        : ButtonTone.neutral,
                    icon: Icon(
                      _managingItems ? Icons.close : Icons.checklist_rtl,
                    ),
                    width: double.infinity,
                    child: Text(_managingItems ? '取消管理' : '管理'),
                  ),
                ),
                StickerActionGridEntry(
                  label: '筛选',
                  button: Button(
                    key: const ValueKey('filter-personal-music-playlist-items'),
                    onPressed: _busy ? null : _openItemFilterDialog,
                    selected: _filterActive,
                    icon: const Icon(Icons.filter_alt_outlined),
                    width: double.infinity,
                    child: const Text('筛选'),
                  ),
                ),
                if (_managingItems)
                  StickerActionGridEntry(
                    label: '分享',
                    button: const Button(
                      key: ValueKey('share-personal-music-playlist-items'),
                      onPressed: null,
                      icon: Icon(Icons.share_outlined),
                      width: double.infinity,
                      child: Text('分享'),
                    ),
                  ),
                if (_managingItems)
                  StickerActionGridEntry(
                    label: '置顶',
                    button: Button(
                      key: const ValueKey(
                        'pin-selected-personal-music-playlist-items',
                      ),
                      onPressed: !_pinningItems && _selectedItemIds.isNotEmpty
                          ? _pinSelectedItems
                          : null,
                      loading: _pinningItems,
                      icon: const Icon(Icons.vertical_align_top),
                      width: double.infinity,
                      child: const Text('置顶'),
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
            if (_error != null) _MusicPlaylistErrorText(_error!),
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
                  key: ValueKey(
                    'personal-music-playlist-item-${_items[index].id}',
                  ),
                  item: _items[index],
                  managing: _managingItems,
                  selectionNumber: itemSelectionNumbers[_items[index].id],
                  busy:
                      _pinningItems ||
                      _deletingItems ||
                      _busyItemIds.contains(_items[index].id),
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

class _MusicPlaylistSummaryList extends StatelessWidget {
  const _MusicPlaylistSummaryList({
    required this.playlists,
    required this.managing,
    required this.selectionNumbers,
    required this.busy,
    required this.filterActive,
    required this.onTap,
    required this.onRename,
    required this.onMove,
    required this.onDelete,
  });

  final List<PersonalMusicPlaylist> playlists;
  final bool managing;
  final Map<String, int> selectionNumbers;
  final bool busy;
  final bool filterActive;
  final ValueChanged<PersonalMusicPlaylist> onTap;
  final ValueChanged<PersonalMusicPlaylist> onRename;
  final void Function(PersonalMusicPlaylist playlist, int direction) onMove;
  final ValueChanged<PersonalMusicPlaylist> onDelete;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        const subPanelHorizontalPadding = 28.0;
        final contentWidth = constraints.maxWidth.isFinite
            ? (constraints.maxWidth - subPanelHorizontalPadding)
                  .clamp(0.0, double.infinity)
                  .toDouble()
            : double.infinity;
        final stackControls =
            managing &&
            playlists.any(
              (playlist) => _playlistNameNeedsStackedControls(
                context: context,
                availableWidth: contentWidth,
                name: playlist.name,
                selected: selectionNumbers.containsKey(playlist.id),
              ),
            );
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (var index = 0; index < playlists.length; index++) ...[
              _MusicPlaylistSummaryTile(
                playlist: playlists[index],
                managing: managing,
                selectionNumber: selectionNumbers[playlists[index].id],
                busy: busy,
                canMoveUp: !filterActive && index > 0,
                canMoveDown: !filterActive && index < playlists.length - 1,
                stackControls: stackControls,
                nameMaxLines:
                    _playlistNameNeedsTwoLines(
                      context: context,
                      availableWidth: contentWidth,
                      name: playlists[index].name,
                      managing: managing,
                      selected: selectionNumbers.containsKey(
                        playlists[index].id,
                      ),
                      controlsStacked: stackControls,
                    )
                    ? 2
                    : 1,
                onTap: () => onTap(playlists[index]),
                onRename: () => onRename(playlists[index]),
                onMoveUp: () => onMove(playlists[index], -1),
                onMoveDown: () => onMove(playlists[index], 1),
                onDelete: () => onDelete(playlists[index]),
              ),
              if (index < playlists.length - 1) const SizedBox(height: 10),
            ],
          ],
        );
      },
    );
  }
}

class _MusicPlaylistSummaryTile extends StatelessWidget {
  const _MusicPlaylistSummaryTile({
    required this.playlist,
    required this.managing,
    required this.selectionNumber,
    required this.busy,
    required this.canMoveUp,
    required this.canMoveDown,
    required this.stackControls,
    required this.nameMaxLines,
    required this.onTap,
    required this.onRename,
    required this.onMoveUp,
    required this.onMoveDown,
    required this.onDelete,
  });

  final PersonalMusicPlaylist playlist;
  final bool managing;
  final int? selectionNumber;
  final bool busy;
  final bool canMoveUp;
  final bool canMoveDown;
  final bool stackControls;
  final int nameMaxLines;
  final VoidCallback onTap;
  final VoidCallback onRename;
  final VoidCallback onMoveUp;
  final VoidCallback onMoveDown;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final selected = selectionNumber != null;
    final cardMouseCursor = managing
        ? SystemMouseCursors.basic
        : SystemMouseCursors.click;
    final informationContent = Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        children: [
          if (managing) ...[
            Icon(
              selected ? Icons.check_box : Icons.check_box_outline_blank,
              color: selected ? _cyan : _textMuted,
              size: 20,
            ),
            const SizedBox(width: 10),
          ],
          const Icon(Icons.queue_music, color: _cyan, size: 24),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  playlist.name,
                  maxLines: nameMaxLines,
                  overflow: TextOverflow.ellipsis,
                  style: _playlistSummaryNameStyle,
                ),
                const SizedBox(height: 3),
                Text(
                  '${playlist.itemCount} 首歌曲',
                  style: const TextStyle(color: _textMuted, fontSize: 12),
                ),
              ],
            ),
          ),
          if (managing && selected)
            Container(
              width: 28,
              height: 28,
              decoration: const BoxDecoration(
                color: _cyan,
                shape: BoxShape.circle,
              ),
              alignment: Alignment.center,
              child: Text(
                '$selectionNumber',
                style: const TextStyle(
                  color: _primaryDark,
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                ),
              ),
            )
          else if (!managing)
            const Icon(Icons.chevron_right, color: _textMuted, size: 22),
        ],
      ),
    );
    final information = KeyedSubtree(
      key: ValueKey('personal-music-playlist-information-${playlist.id}'),
      child: informationContent,
    );
    final controls = _ManagementCardActionArea(
      child: Wrap(
        key: ValueKey('personal-music-playlist-controls-${playlist.id}'),
        spacing: 8,
        runSpacing: 8,
        alignment: WrapAlignment.end,
        children: [
          ButtonIcon(
            tooltip: '重命名',
            onPressed: busy ? null : onRename,
            icon: const Icon(Icons.edit_outlined),
            size: 36,
          ),
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
          ),
        ],
      ),
    );
    final panel = _SettingsSubPanel(
      key: ValueKey('personal-music-playlist-card-${playlist.id}'),
      hoverable: true,
      highlighted: selected,
      mouseCursor: cardMouseCursor,
      child: stackControls && managing
          ? Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                information,
                const SizedBox(height: 10),
                Align(alignment: Alignment.centerRight, child: controls),
              ],
            )
          : Row(
              children: [
                Expanded(child: information),
                if (managing) ...[const SizedBox(width: 10), controls],
              ],
            ),
    );
    if (managing) {
      return _ManagementCardTapTarget(onTap: busy ? null : onTap, child: panel);
    }
    return UiPointerTapRegion(
      onTap: busy ? null : onTap,
      disableSelection: true,
      child: panel,
    );
  }
}

const _playlistSummaryNameStyle = TextStyle(
  color: _textPrimary,
  fontSize: 14,
  fontWeight: FontWeight.w600,
);
const _playlistSummaryControlsWidth = 4 * 36.0 + 3 * 8.0;
const _playlistSummaryControlsGap = 10.0;

bool _playlistNameNeedsStackedControls({
  required BuildContext context,
  required double availableWidth,
  required String name,
  required bool selected,
}) {
  final requiredWidth =
      _playlistSummaryInformationFixedWidth(
        managing: true,
        selected: selected,
      ) +
      _playlistSummaryTextWidth(context, name) +
      _playlistSummaryTruncationGuardWidth(context) +
      _playlistSummaryControlsGap +
      _playlistSummaryControlsWidth;
  return requiredWidth > availableWidth;
}

bool _playlistNameNeedsTwoLines({
  required BuildContext context,
  required double availableWidth,
  required String name,
  required bool managing,
  required bool selected,
  required bool controlsStacked,
}) {
  if (managing && !controlsStacked) return false;
  final requiredWidth =
      _playlistSummaryInformationFixedWidth(
        managing: managing,
        selected: selected,
      ) +
      _playlistSummaryTextWidth(context, name) +
      _playlistSummaryTruncationGuardWidth(context);
  return requiredWidth > availableWidth;
}

double _playlistSummaryInformationFixedWidth({
  required bool managing,
  required bool selected,
}) {
  return (managing ? 20 + 10 : 0) +
      24 +
      12 +
      (managing ? (selected ? 28 : 0) : 22);
}

double _playlistSummaryTextWidth(BuildContext context, String text) {
  final painter = TextPainter(
    text: TextSpan(text: text, style: _playlistSummaryNameStyle),
    maxLines: 1,
    textDirection: Directionality.of(context),
    textScaler: MediaQuery.textScalerOf(context),
  )..layout();
  return painter.width;
}

double _playlistSummaryTruncationGuardWidth(BuildContext context) {
  return _playlistSummaryTextWidth(context, '…');
}

class _MusicPlaylistSearchRow extends StatelessWidget {
  const _MusicPlaylistSearchRow({
    required this.controller,
    this.focusNode,
    required this.hintText,
    required this.onSubmitted,
  });

  final TextEditingController controller;
  final FocusNode? focusNode;
  final String hintText;
  final ValueChanged<String> onSubmitted;

  @override
  Widget build(BuildContext context) {
    return Input(
      controller: controller,
      focusNode: focusNode,
      hintText: hintText,
      prefixIcon: Icons.search,
      showClearButton: true,
      onSubmitted: onSubmitted,
    );
  }
}

class _MusicPlaylistSearchResultTile extends StatelessWidget {
  const _MusicPlaylistSearchResultTile({
    required this.result,
    required this.query,
    required this.loading,
    required this.onAdd,
  });

  final MusicBoxSearchResult result;
  final String query;
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
                HighlightedText(
                  text: result.name,
                  query: query,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: _textPrimary,
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 3),
                HighlightedText(
                  text:
                      '${personalPlaylistArtistsLabel(result.artists)} · '
                      '${personalPlaylistSourceLabel(result.source)}',
                  query: query,
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
    super.key,
    required this.item,
    required this.managing,
    required this.selectionNumber,
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
  final int? selectionNumber;
  final bool busy;
  final bool canMoveUp;
  final bool canMoveDown;
  final VoidCallback onTap;
  final VoidCallback onDelete;
  final VoidCallback onMoveUp;
  final VoidCallback onMoveDown;

  @override
  Widget build(BuildContext context) {
    final selected = selectionNumber != null;
    final information = Padding(
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
          if (managing && selected) ...[
            const SizedBox(width: 10),
            Container(
              key: ValueKey(
                'personal-music-playlist-item-selection-number-${item.id}',
              ),
              width: 28,
              height: 28,
              decoration: const BoxDecoration(
                color: _cyan,
                shape: BoxShape.circle,
              ),
              alignment: Alignment.center,
              child: Text(
                '$selectionNumber',
                style: const TextStyle(
                  color: _primaryDark,
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ],
        ],
      ),
    );
    final controls = _ManagementCardActionArea(
      child: Wrap(
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
      ),
    );
    final panel = _SettingsSubPanel(
      highlighted: selected,
      child: LayoutBuilder(
        builder: (context, constraints) {
          if (constraints.maxWidth < 420) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                information,
                if (managing) ...[
                  const SizedBox(height: 10),
                  Align(alignment: Alignment.centerRight, child: controls),
                ],
              ],
            );
          }
          return Row(
            children: [
              Expanded(child: information),
              if (managing) ...[const SizedBox(width: 10), controls],
            ],
          );
        },
      ),
    );
    if (!managing) return panel;
    return _ManagementCardTapTarget(onTap: busy ? null : onTap, child: panel);
  }
}

/// Makes every non-action part of a management card select the card.
///
/// Action clusters use [_ManagementCardActionArea], whose nested recognizer
/// wins Flutter's gesture arena. This keeps active and disabled action buttons
/// from also toggling the card selection while preserving the full card's
/// padding and layout gaps as useful selection targets.
class _ManagementCardTapTarget extends StatelessWidget {
  const _ManagementCardTapTarget({required this.child, required this.onTap});

  final Widget child;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      enabled: onTap != null,
      onTap: onTap,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        excludeFromSemantics: true,
        onTap: onTap,
        child: child,
      ),
    );
  }
}

class _ManagementCardActionArea extends StatelessWidget {
  const _ManagementCardActionArea({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      excludeFromSemantics: true,
      onTap: () {},
      child: child,
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

class _MusicPlaylistTrackSearchDialog extends StatefulWidget {
  const _MusicPlaylistTrackSearchDialog({
    required this.controller,
    required this.playlistId,
    required this.onTrackAdded,
  });

  final PersonalMusicPlaylistsController controller;
  final String playlistId;
  final Future<void> Function() onTrackAdded;

  @override
  State<_MusicPlaylistTrackSearchDialog> createState() =>
      _MusicPlaylistTrackSearchDialogState();
}

class _MusicPlaylistTrackSearchDialogState
    extends State<_MusicPlaylistTrackSearchDialog> {
  final TextEditingController _searchController = TextEditingController();
  final FocusNode _searchFocusNode = FocusNode();
  final Set<String> _busyTrackKeys = {};
  Timer? _debounce;
  List<MusicBoxSearchResult> _results = const [];
  String _source = musicBoxDefaultSource;
  String? _error;
  int _generation = 0;
  bool _searching = false;

  @override
  void initState() {
    super.initState();
    _searchController.addListener(_handleSearchChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _searchFocusNode.requestFocus();
    });
  }

  @override
  void dispose() {
    _generation += 1;
    _debounce?.cancel();
    _searchController.removeListener(_handleSearchChanged);
    _searchController.dispose();
    _searchFocusNode.dispose();
    super.dispose();
  }

  void _handleSearchChanged() => _scheduleSearch();

  void _scheduleSearch({bool immediate = false}) {
    _debounce?.cancel();
    final keyword = _searchController.text.trim();
    final source = _source;
    final generation = ++_generation;
    if (keyword.isEmpty) {
      setState(() {
        _results = const [];
        _searching = false;
        _error = null;
      });
      return;
    }
    setState(() {
      _searching = true;
      _results = const [];
      _error = null;
    });
    if (immediate) {
      unawaited(
        _runSearch(keyword: keyword, source: source, generation: generation),
      );
      return;
    }
    _debounce = Timer(
      const Duration(milliseconds: 350),
      () => unawaited(
        _runSearch(keyword: keyword, source: source, generation: generation),
      ),
    );
  }

  Future<void> _submitSearch() async {
    _debounce?.cancel();
    final keyword = _searchController.text.trim();
    if (keyword.isEmpty) {
      _scheduleSearch(immediate: true);
      return;
    }
    final generation = ++_generation;
    setState(() {
      _searching = true;
      _results = const [];
      _error = null;
    });
    await _runSearch(keyword: keyword, source: _source, generation: generation);
  }

  Future<void> _runSearch({
    required String keyword,
    required String source,
    required int generation,
  }) async {
    try {
      final results = await widget.controller.searchTracks(
        keyword: keyword,
        source: source,
      );
      if (!mounted ||
          generation != _generation ||
          _searchController.text.trim() != keyword ||
          _source != source ||
          results == null) {
        return;
      }
      setState(() => _results = results);
    } catch (error) {
      if (!mounted || generation != _generation) return;
      setState(() {
        _results = const [];
        _error = error.toString();
      });
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _searching = false);
      }
    }
  }

  void _setSource(String source) {
    if (_source == source) return;
    _debounce?.cancel();
    _generation += 1;
    setState(() {
      _source = source;
      _results = const [];
      _searching = false;
      _error = null;
    });
    if (_searchController.text.trim().isNotEmpty) {
      _scheduleSearch(immediate: true);
    }
  }

  Future<void> _addTrack(MusicBoxSearchResult track) async {
    final key = '${track.source}:${track.trackId}';
    if (_busyTrackKeys.contains(key)) return;
    setState(() {
      _busyTrackKeys.add(key);
      _error = null;
    });
    try {
      await widget.controller.addTrack(
        playlistId: widget.playlistId,
        track: track,
      );
      await widget.onTrackAdded();
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _busyTrackKeys.remove(key));
    }
  }

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    final contentHeight = (media.size.height - media.viewInsets.bottom - 220)
        .clamp(180.0, 520.0)
        .toDouble();
    return DialogFrame(
      title: '搜索添加',
      icon: Icons.playlist_add,
      maxWidth: 680,
      adaptiveActions: [
        ResponsiveDialogAction(
          label: '完成',
          icon: Icons.check,
          tone: ButtonTone.primary,
          onPressed: () => Navigator.of(context).pop(),
        ),
      ],
      child: SizedBox(
        height: contentHeight,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SegmentedControl<String>(
              expanded: true,
              value: _source,
              onChanged: _setSource,
              segments: [
                for (final source in musicBoxSources)
                  Segment(value: source.id, label: source.label),
              ],
            ),
            const SizedBox(height: 10),
            _MusicPlaylistSearchRow(
              controller: _searchController,
              focusNode: _searchFocusNode,
              hintText: '搜索歌曲添加到歌单',
              onSubmitted: (_) => _submitSearch(),
            ),
            if (_error != null) ...[
              const SizedBox(height: 10),
              _MusicPlaylistErrorText(_error!),
            ],
            const SizedBox(height: 10),
            Expanded(
              child: _searching && _results.isEmpty
                  ? const Center(child: CircularProgressIndicator(color: _cyan))
                  : _results.isEmpty
                  ? _SettingsEmptyState(
                      text: _searchController.text.trim().isEmpty
                          ? '输入歌名或歌手后自动搜索'
                          : '没有找到相关歌曲',
                    )
                  : ListView.separated(
                      itemCount: _results.length,
                      separatorBuilder: (_, _) => const SizedBox(height: 10),
                      itemBuilder: (context, index) {
                        final result = _results[index];
                        return _MusicPlaylistSearchResultTile(
                          result: result,
                          query: _searchController.text,
                          loading: _busyTrackKeys.contains(
                            '${result.source}:${result.trackId}',
                          ),
                          onAdd: () => _addTrack(result),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MusicPlaylistItemFilterDialog extends StatefulWidget {
  const _MusicPlaylistItemFilterDialog({
    required this.keyword,
    required this.source,
  });

  final String keyword;
  final String source;

  @override
  State<_MusicPlaylistItemFilterDialog> createState() =>
      _MusicPlaylistItemFilterDialogState();
}

class _MusicPlaylistItemFilterDialogState
    extends State<_MusicPlaylistItemFilterDialog> {
  late final TextEditingController _keywordController;
  final FocusNode _keywordFocusNode = FocusNode();
  late String _source;

  @override
  void initState() {
    super.initState();
    _keywordController = TextEditingController(text: widget.keyword);
    _source = widget.source;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _keywordFocusNode.requestFocus();
    });
  }

  @override
  void dispose() {
    _keywordController.dispose();
    _keywordFocusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return DialogFrame(
      title: '筛选歌曲',
      icon: Icons.filter_alt_outlined,
      adaptiveActions: [
        ResponsiveDialogAction(
          label: '重置',
          icon: Icons.restart_alt,
          onPressed: () {
            _keywordController.clear();
            setState(() => _source = '');
          },
        ),
        ResponsiveDialogAction(
          label: '取消',
          onPressed: () => Navigator.of(context).pop(),
        ),
        ResponsiveDialogAction(
          label: '确认',
          icon: Icons.check,
          tone: ButtonTone.primary,
          onPressed: () => Navigator.of(context).pop(
            PersonalPlaylistItemFilterDraft(
              keyword: _keywordController.text.trim(),
              source: _source,
            ),
          ),
        ),
      ],
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const _MusicPlaylistFieldLabel('名称关键字'),
          const SizedBox(height: 8),
          Input(
            controller: _keywordController,
            focusNode: _keywordFocusNode,
            hintText: '',
            showClearButton: true,
            minLines: 1,
            maxLines: 1,
          ),
          const SizedBox(height: 16),
          const _MusicPlaylistFieldLabel('歌曲来源'),
          const SizedBox(height: 8),
          SegmentedControl<String>(
            expanded: true,
            value: _source,
            onChanged: (source) => setState(() => _source = source),
            segments: [
              const Segment(value: '', label: '全部'),
              for (final source in musicBoxSources)
                Segment(value: source.id, label: source.label),
            ],
          ),
        ],
      ),
    );
  }
}

class _MusicPlaylistFilterDialog extends StatefulWidget {
  const _MusicPlaylistFilterDialog({
    required this.keyword,
    required this.countFilter,
  });

  final String keyword;
  final String countFilter;

  @override
  State<_MusicPlaylistFilterDialog> createState() =>
      _MusicPlaylistFilterDialogState();
}

class _MusicPlaylistFilterDialogState
    extends State<_MusicPlaylistFilterDialog> {
  static const _countFilters = [
    _MusicPlaylistCountFilter(personalPlaylistCountAll, '全部'),
    _MusicPlaylistCountFilter(personalPlaylistCountEmpty, '空歌单'),
    _MusicPlaylistCountFilter(personalPlaylistCount1To10, '1～10 首'),
    _MusicPlaylistCountFilter(personalPlaylistCount11To50, '11～50 首'),
    _MusicPlaylistCountFilter(personalPlaylistCount51To100, '51～100 首'),
    _MusicPlaylistCountFilter(personalPlaylistCountOver100, '100 首以上'),
  ];

  late final TextEditingController _keywordController;
  final FocusNode _keywordFocusNode = FocusNode();
  late String _countFilter;

  @override
  void initState() {
    super.initState();
    _keywordController = TextEditingController(text: widget.keyword);
    _countFilter = widget.countFilter;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _keywordFocusNode.requestFocus();
    });
  }

  @override
  void dispose() {
    _keywordController.dispose();
    _keywordFocusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return DialogFrame(
      title: '筛选',
      icon: Icons.filter_alt_outlined,
      adaptiveActions: [
        ResponsiveDialogAction(
          label: '重置',
          icon: Icons.restart_alt,
          onPressed: () {
            _keywordController.clear();
            setState(() => _countFilter = personalPlaylistCountAll);
          },
        ),
        ResponsiveDialogAction(
          label: '取消',
          onPressed: () => Navigator.of(context).pop(),
        ),
        ResponsiveDialogAction(
          label: '确认',
          icon: Icons.check,
          tone: ButtonTone.primary,
          onPressed: () => Navigator.of(context).pop(
            PersonalPlaylistFilterDraft(
              keyword: _keywordController.text.trim(),
              countFilter: _countFilter,
            ),
          ),
        ),
      ],
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const _MusicPlaylistFieldLabel('名称关键字'),
          const SizedBox(height: 8),
          Input(
            controller: _keywordController,
            focusNode: _keywordFocusNode,
            hintText: '',
            showClearButton: true,
            minLines: 1,
            maxLines: 1,
          ),
          const SizedBox(height: 16),
          const _MusicPlaylistFieldLabel('歌曲数量'),
          const SizedBox(height: 8),
          LayoutBuilder(
            builder: (context, constraints) {
              const gap = 8.0;
              final columns = constraints.maxWidth < 330 ? 2 : 3;
              final itemWidth =
                  (constraints.maxWidth - (gap * (columns - 1))) / columns;
              return Wrap(
                spacing: gap,
                runSpacing: gap,
                children: [
                  for (final filter in _countFilters)
                    SizedBox(
                      width: itemWidth,
                      child: PressableSurface(
                        onPressed: () =>
                            setState(() => _countFilter = filter.value),
                        selected: _countFilter == filter.value,
                        height: 38,
                        backgroundColor: _primaryDark,
                        selectedBackgroundColor: UiColors.selected,
                        pressedBackgroundColor: _primaryDarkLow,
                        borderColor: _borderColor,
                        selectedBorderColor: UiColors.selectedBorder,
                        child: Center(
                          child: Text(
                            filter.label,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: _countFilter == filter.value
                                  ? _cyan
                                  : _textSecondary,
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ),
                    ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

class _MusicPlaylistCountFilter {
  const _MusicPlaylistCountFilter(this.value, this.label);

  final String value;
  final String label;
}

class _MusicPlaylistFieldLabel extends StatelessWidget {
  const _MusicPlaylistFieldLabel(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: const TextStyle(
        color: _textMuted,
        fontSize: 12,
        fontWeight: FontWeight.w500,
      ),
    );
  }
}

class _MusicPlaylistNameDialog extends StatefulWidget {
  const _MusicPlaylistNameDialog({
    required this.title,
    required this.icon,
    required this.confirmLabel,
    required this.confirmIcon,
    this.initialName = '',
  });

  final String title;
  final IconData icon;
  final String confirmLabel;
  final IconData confirmIcon;
  final String initialName;

  @override
  State<_MusicPlaylistNameDialog> createState() =>
      _MusicPlaylistNameDialogState();
}

class _MusicPlaylistNameDialogState extends State<_MusicPlaylistNameDialog> {
  late final TextEditingController _controller;
  final FocusNode _focusNode = FocusNode();
  String? _error;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialName);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _focusNode.requestFocus();
      _controller.selection = TextSelection(
        baseOffset: 0,
        extentOffset: _controller.text.length,
      );
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
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
      title: widget.title,
      icon: widget.icon,
      adaptiveActions: [
        ResponsiveDialogAction(
          label: '取消',
          onPressed: () => Navigator.of(context).pop(),
        ),
        ResponsiveDialogAction(
          label: widget.confirmLabel,
          icon: widget.confirmIcon,
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
            focusNode: _focusNode,
            hintText: widget.initialName.isEmpty ? '歌单名称' : '',
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
