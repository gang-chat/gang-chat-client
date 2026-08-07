part of 'settings_page.dart';

class MusicPlaylistsPanel extends StatefulWidget {
  const MusicPlaylistsPanel({
    super.key,
    required this.controller,
    this.reloadToken = 0,
    required this.unavailableMessage,
    this.onLoadingChanged,
    this.title = '歌单管理',
    this.previewApi,
    this.previewPlatformFactory,
    this.shareApi,
  });

  final PersonalMusicPlaylistsController controller;
  final int reloadToken;
  final String unavailableMessage;
  final ValueChanged<bool>? onLoadingChanged;
  final String title;
  final MusicTrackPreviewApi? previewApi;
  final MusicTrackPreviewPlatformFactory? previewPlatformFactory;
  final GangApi? shareApi;

  @override
  State<MusicPlaylistsPanel> createState() => _MusicPlaylistsPanelState();
}

class _MusicPlaylistsPanelState extends State<MusicPlaylistsPanel> {
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
  bool _mergingPlaylists = false;
  String? _cloningPlaylistId;
  String? _sharingPlaylistId;
  bool _deletingItems = false;
  bool _addingSelectedItems = false;
  bool _pinningItems = false;
  bool _managingPlaylists = false;
  bool _managingItems = false;
  String? _error;
  final Set<String> _busyPlaylistIds = {};
  final Set<String> _busyItemIds = {};
  int _playlistLoadGeneration = 0;
  int _itemLoadGeneration = 0;
  MusicTrackPreviewController? _previewController;

  MusicPlaylistManagementCapabilities get _capabilities =>
      widget.controller.capabilities;

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
      _mergingPlaylists ||
      _cloningPlaylistId != null ||
      _sharingPlaylistId != null ||
      _busyPlaylistIds.isNotEmpty;

  bool get _busy =>
      _loading ||
      _loadingItems ||
      _loadingMore ||
      _creating ||
      _deletingPlaylists ||
      _pinningPlaylists ||
      _mergingPlaylists ||
      _cloningPlaylistId != null ||
      _busyPlaylistIds.isNotEmpty ||
      _deletingItems ||
      _addingSelectedItems ||
      _pinningItems;

  @override
  void initState() {
    super.initState();
    _previewController = _createPreviewController();
    scheduleMicrotask(_loadPlaylists);
  }

  @override
  void didUpdateWidget(covariant MusicPlaylistsPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller.api != widget.controller.api ||
        oldWidget.previewApi != widget.previewApi ||
        oldWidget.previewPlatformFactory != widget.previewPlatformFactory) {
      final previous = _previewController;
      _previewController = _createPreviewController();
      if (previous != null) unawaited(previous.dispose());
    }
    if (oldWidget.reloadToken != widget.reloadToken ||
        oldWidget.controller.api != widget.controller.api) {
      _managingPlaylists = false;
      _managingItems = false;
      _selectedPlaylistIds = const [];
      _selectedItemIds = const {};
      scheduleMicrotask(_loadPlaylists);
    }
  }

  @override
  void dispose() {
    _playlistLoadGeneration += 1;
    _itemLoadGeneration += 1;
    final preview = _previewController;
    if (preview != null) unawaited(preview.dispose());
    super.dispose();
  }

  MusicTrackPreviewController? _createPreviewController() {
    final api = widget.previewApi ?? widget.controller.api;
    final factory = widget.previewPlatformFactory;
    if (api is! MusicTrackPreviewApi || factory == null) return null;
    return MusicTrackPreviewController(api: api, platform: factory.create());
  }

  void _setLoading(bool loading) {
    if (!mounted || _loading == loading) return;
    setState(() => _loading = loading);
    widget.onLoadingChanged?.call(loading);
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
    final preview = _previewController;
    if (preview != null) unawaited(preview.stop());
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

  Future<void> _addPlaylistItemToPlaylist(
    PersonalMusicPlaylistItem item,
    PersonalMusicPlaylist target,
  ) => _addSearchResultToPlaylist(
    MusicBoxSearchResult(
      trackId: item.trackId,
      name: item.title,
      artists: item.artists,
      source: item.source,
    ),
    target,
  );

  Future<void> _addSearchResultToPlaylist(
    MusicBoxSearchResult track,
    PersonalMusicPlaylist target,
  ) async {
    final added = await widget.controller.addTrack(
      playlistId: target.id,
      track: track,
    );
    if (added == null) throw StateError('当前歌单不可写');
    if (!mounted) return;

    final activeTarget = _activePlaylist?.id == target.id;
    setState(() {
      _playlists = [
        for (final playlist in _playlists)
          if (playlist.id == target.id)
            playlist.copyWith(itemCount: playlist.itemCount + 1)
          else
            playlist,
      ];
      if (activeTarget) {
        _activePlaylist = _activePlaylist!.copyWith(
          itemCount: _activePlaylist!.itemCount + 1,
        );
        _itemTotal += 1;
        if (!_filterActive && !_itemsHaveMore) {
          _items = [..._items, added];
        } else {
          scheduleMicrotask(() => _loadItems(reset: true));
        }
      }
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
    if (!_capabilities.canCreatePlaylists ||
        _busy ||
        _playlists.length >= _maxPlaylists) {
      return;
    }
    final draft = await showDialog<PersonalPlaylistCreateDraft>(
      context: context,
      builder: (context) => _MusicPlaylistNameDialog(
        title: '新建歌单',
        icon: Icons.playlist_add,
        confirmLabel: '创建',
        confirmIcon: Icons.add,
        loadImportPlaylists: widget.controller.canImportPersonalPlaylist
            ? widget.controller.loadImportPlaylists
            : null,
      ),
    );
    if (!mounted || draft == null) return;
    setState(() {
      _creating = true;
      _error = null;
    });
    try {
      await widget.controller.createPlaylist(
        draft.name,
        importPlaylistId: draft.importPlaylistId,
      );
      await _loadPlaylists();
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _creating = false);
    }
  }

  Future<void> _renamePlaylist(PersonalMusicPlaylist playlist) async {
    if (!_capabilities.canRenamePlaylists || _playlistManagementBusy) return;
    final draft = await showDialog<PersonalPlaylistCreateDraft>(
      context: context,
      builder: (context) => _MusicPlaylistNameDialog(
        title: '重命名歌单',
        icon: Icons.edit_outlined,
        confirmLabel: '重命名',
        confirmIcon: Icons.check,
        initialName: playlist.name,
      ),
    );
    if (!mounted || draft == null || draft.name == playlist.name) return;
    setState(() {
      _busyPlaylistIds.add(playlist.id);
      _error = null;
    });
    try {
      await widget.controller.renamePlaylist(
        playlistId: playlist.id,
        name: draft.name,
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
    if (!_capabilities.canManagePlaylists || _playlistManagementBusy) return;
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
    if (!_capabilities.canDeletePlaylists ||
        _playlistManagementBusy ||
        _selectedPlaylistIds.isEmpty) {
      return;
    }
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
    if (!_capabilities.canReorderPlaylists ||
        _playlistManagementBusy ||
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

  Future<void> _mergeSelectedPlaylists() async {
    if (!widget.controller.canMergePlaylists ||
        _playlistManagementBusy ||
        _selectedPlaylistIds.length < 2) {
      return;
    }
    final selectedIDs = List<String>.of(_selectedPlaylistIds);
    final draft = await showDialog<PersonalPlaylistCreateDraft>(
      context: context,
      builder: (context) => const _MusicPlaylistNameDialog(
        title: '合并歌单',
        icon: Icons.merge_type,
        confirmLabel: '合并',
        confirmIcon: Icons.merge_type,
        description:
            '歌曲会按选择顺序合并并按具体链接去重。已完整合并的来源歌单会删除；'
            '超过 500 首时，未合并的剩余歌曲仍保留在原歌单中。',
      ),
    );
    if (!mounted || draft == null) return;
    final sourceItemCount = _playlists
        .where((playlist) => selectedIDs.contains(playlist.id))
        .fold<int>(0, (total, playlist) => total + playlist.itemCount);
    final exceedsItemLimit = sourceItemCount > _maxPlaylistItems;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StickerConfirmDialog(
        title: exceedsItemLimit ? '歌曲不能全部保留' : '确认合并歌单',
        body: exceedsItemLimit
            ? '所选歌单共有 $sourceItemCount 首歌曲，超过单个歌单 $_maxPlaylistItems 首上限，'
                  '无法全部原样保留。系统会按选择顺序、按具体链接去重，'
                  '合并后的歌单最多保留前 $_maxPlaylistItems 首；未能合并的剩余歌曲会保留在原歌单中，'
                  '已完整合并的来源歌单会删除。是否继续？'
            : '确定将 ${selectedIDs.length} 个歌单按选择顺序合并为“${draft.name}”吗？'
                  '已完整合并的来源歌单会删除，此操作无法撤销。',
        confirmLabel: exceedsItemLimit ? '继续合并' : '确认合并',
        confirmIcon: Icons.merge_type,
      ),
    );
    if (!mounted || confirmed != true) return;
    setState(() {
      _mergingPlaylists = true;
      _error = null;
    });
    try {
      final result = await widget.controller.mergePlaylists(
        name: draft.name,
        playlistIds: selectedIDs,
      );
      if (!mounted || result == null) return;
      await _loadPlaylists();
      if (!mounted) return;
      setState(() {
        _managingPlaylists = false;
        _selectedPlaylistIds = const [];
      });
      _showPlaylistMergeResultNotice(result);
    } catch (error) {
      if (mounted) {
        showFloatingErrorNotice(context, '合并歌单失败：$error');
      }
    } finally {
      if (mounted) setState(() => _mergingPlaylists = false);
    }
  }

  void _showPlaylistMergeResultNotice(PersonalMusicPlaylistMergeResult result) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (result.truncated) {
        showFloatingSuccessNotice(
          context,
          '已合并为“${result.playlist.name}”，按顺序保留前 '
          '${result.itemCount} 首；其余歌曲已保留在原歌单中',
        );
        return;
      }
      final duplicateLabel = result.duplicateCount == 0
          ? ''
          : '，已去重 ${result.duplicateCount} 首';
      showFloatingSuccessNotice(
        context,
        '已合并为“${result.playlist.name}”，共 ${result.itemCount} 首'
        '$duplicateLabel',
      );
    });
  }

  Future<void> _cloneRoomPlaylist(PersonalMusicPlaylist playlist) async {
    if (!widget.controller.canCloneRoomPlaylistsToPersonal ||
        _playlistManagementBusy) {
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StickerConfirmDialog(
        title: '克隆歌单',
        body:
            '确定将“${playlist.name}”克隆到“我的歌单”吗？'
            '克隆后的名称使用“房间备注名 · 歌单名”。',
        confirmLabel: '克隆',
        confirmIcon: Icons.library_add_outlined,
      ),
    );
    if (!mounted || confirmed != true) return;
    setState(() {
      _cloningPlaylistId = playlist.id;
      _error = null;
    });
    try {
      final result = await widget.controller.cloneRoomPlaylistToPersonal(
        playlist.id,
      );
      if (!mounted || result == null) return;
      setState(() => _cloningPlaylistId = null);
      _showRoomPlaylistCloneResultNotice(result);
    } catch (error) {
      if (!mounted) return;
      setState(() => _cloningPlaylistId = null);
      _showRoomPlaylistCloneErrorNotice(error);
    } finally {
      if (mounted && _cloningPlaylistId == playlist.id) {
        setState(() => _cloningPlaylistId = null);
      }
    }
  }

  void _showRoomPlaylistCloneResultNotice(PersonalMusicPlaylist result) {
    // Wait until the confirmation route has finished closing and management
    // mode has rebuilt. This keeps the global notice above the settled page
    // instead of briefly placing it behind the outgoing dialog overlay.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      showFloatingSuccessNotice(context, '已克隆到我的歌单 - ${result.name}');
    });
  }

  void _showRoomPlaylistCloneErrorNotice(Object error) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final message =
          error is ApiException && error.code == 'playlist_limit_reached'
          ? '克隆失败：我的歌单已达 50 个上限'
          : '克隆失败：$error';
      showFloatingErrorNotice(context, message);
    });
  }

  Future<void> _sharePersonalPlaylist(PersonalMusicPlaylist playlist) async {
    final api = widget.shareApi;
    if (api == null ||
        widget.controller.roomScoped ||
        _playlistManagementBusy) {
      return;
    }
    final room = await showDialog<RoomCard>(
      context: context,
      builder: (context) =>
          _MusicPlaylistShareRoomDialog(api: api, playlistName: playlist.name),
    );
    if (!mounted || room == null) return;
    setState(() {
      _sharingPlaylistId = playlist.id;
      _error = null;
    });
    try {
      await api.sendMessage(
        roomId: room.id,
        clientMessageId: newUuid(),
        body: '',
        type: 'playlist',
        attachments: [
          MessageAttachment(type: 'playlist', playlistId: playlist.id),
        ],
      );
      if (!mounted) return;
      showFloatingSuccessNotice(
        context,
        '已将“${playlist.name}”分享到“${room.displayName}”',
      );
    } catch (error) {
      if (mounted) showFloatingErrorNotice(context, '分享歌单失败：$error');
    } finally {
      if (mounted && _sharingPlaylistId == playlist.id) {
        setState(() => _sharingPlaylistId = null);
      }
    }
  }

  Future<void> _movePlaylist(PersonalMusicPlaylist playlist, int delta) async {
    if (!_capabilities.canReorderPlaylists ||
        _playlistManagementBusy ||
        _playlistFilterActive) {
      return;
    }
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

  void _openOrSelectPlaylist(PersonalMusicPlaylist playlist) {
    if (_managingPlaylists) {
      _togglePlaylistSelection(playlist.id);
      return;
    }
    unawaited(_openPlaylist(playlist));
  }

  Future<void> _openTrackSearchDialog() async {
    final playlist = _activePlaylist;
    if (!_capabilities.canAddItems || playlist == null || _busy) return;
    await showDialog<void>(
      context: context,
      builder: (context) => _MusicPlaylistTrackSearchDialog(
        controller: widget.controller,
        playlistId: playlist.id,
        previewController: _previewController,
        playlists: _playlists,
        playlistsRoomScoped: widget.controller.roomScoped,
        onAddToPlaylist: _addSearchResultToPlaylist,
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
    if (!_capabilities.canManageItems || _busy) return;
    setState(() {
      _managingItems = !_managingItems;
      _selectedItemIds = const {};
    });
  }

  void _toggleItemSelection(String itemID) {
    if (!_managingItems ||
        _deletingItems ||
        _addingSelectedItems ||
        _pinningItems) {
      return;
    }
    setState(() {
      _selectedItemIds = toggledPersonalPlaylistSelection(
        _selectedItemIds,
        itemID,
      );
    });
  }

  void _toggleSelectVisible() {
    if (_addingSelectedItems || _deletingItems || _pinningItems) return;
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
    if (!_capabilities.canDeleteItems ||
        playlist == null ||
        _selectedItemIds.isEmpty ||
        _deletingItems ||
        _addingSelectedItems) {
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

  Future<void> _addSelectedItemsToPlaylist() async {
    final source = _activePlaylist;
    if (source == null ||
        !widget.controller.canBatchAddItems ||
        _selectedItemIds.isEmpty ||
        _addingSelectedItems) {
      return;
    }
    final targets = [
      for (final playlist in _playlists)
        if (playlist.id != source.id) playlist,
    ];
    if (targets.isEmpty) {
      showFloatingErrorNotice(context, '没有可添加歌曲的其他歌单');
      return;
    }
    final target = await showDialog<PersonalMusicPlaylist>(
      context: context,
      builder: (context) => _MusicPlaylistImportDialog(
        playlists: targets,
        selectedPlaylistId: null,
        title: '选择目标歌单',
        emptyText: '没有可添加歌曲的其他歌单',
        pickerKey: const ValueKey<String>(
          'music-playlist-batch-add-target-picker',
        ),
        optionKeyPrefix: 'music-playlist-batch-add-target',
      ),
    );
    if (!mounted || target == null) return;

    final selectedIDs = _selectedItemIds.toList(growable: false);
    final available = (_maxPlaylistItems - target.itemCount).clamp(
      0,
      _maxPlaylistItems,
    );
    if (available == 0) {
      showFloatingErrorNotice(
        context,
        '添加失败：“${target.name}”已达 $_maxPlaylistItems 首上限',
      );
      return;
    }
    final exceedsLimit = selectedIDs.length > available;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StickerConfirmDialog(
        title: exceedsLimit ? '歌曲不能全部添加' : '确认添加歌曲',
        body: exceedsLimit
            ? '“${target.name}”最多还能添加 $available 首，无法全部添加。'
                  '系统会按选择顺序、按具体链接去重，最多添加到 '
                  '$_maxPlaylistItems 首；原歌单中的歌曲不会删除。是否继续？'
            : '确定将已选择的 ${selectedIDs.length} 首歌曲按选择顺序添加到'
                  '“${target.name}”吗？重复链接不会再次添加，原歌单中的歌曲不会删除。',
        confirmLabel: exceedsLimit ? '继续添加' : '确认添加',
        confirmIcon: Icons.playlist_add,
      ),
    );
    if (!mounted || confirmed != true) return;

    setState(() {
      _addingSelectedItems = true;
      _error = null;
    });
    try {
      final result = await widget.controller.batchAddItems(
        sourcePlaylistId: source.id,
        targetPlaylistId: target.id,
        itemIds: selectedIDs,
      );
      if (!mounted || result == null) return;
      await _reloadPlaylistSummariesWithoutClosing();
      if (!mounted) return;
      setState(() {
        _managingItems = false;
        _selectedItemIds = const {};
      });
      _showPlaylistBatchAddResultNotice(result);
    } catch (error) {
      if (mounted) showFloatingErrorNotice(context, '添加歌曲失败：$error');
    } finally {
      if (mounted) setState(() => _addingSelectedItems = false);
    }
  }

  void _showPlaylistBatchAddResultNotice(
    PersonalMusicPlaylistBatchAddResult result,
  ) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final targetName = result.playlist.name;
      if (result.addedItemCount == 0 && result.omittedCount > 0) {
        showFloatingErrorNotice(
          context,
          '“$targetName”已达 $_maxPlaylistItems 首上限，没有歌曲被添加',
        );
        return;
      }
      if (result.addedItemCount == 0) {
        showFloatingSuccessNotice(context, '“$targetName”已包含所选歌曲，未重复添加');
        return;
      }
      if (result.truncated) {
        showFloatingSuccessNotice(
          context,
          '已添加到“$targetName”，新增 ${result.addedItemCount} 首；'
          '目标歌单已达 $_maxPlaylistItems 首上限，${result.omittedCount} 首未添加',
        );
        return;
      }
      final skipped = result.duplicateCount + result.alreadyPresentCount;
      final skippedLabel = skipped == 0 ? '' : '，已跳过 $skipped 首重复歌曲';
      showFloatingSuccessNotice(
        context,
        '已添加到“$targetName”，新增 ${result.addedItemCount} 首$skippedLabel',
      );
    });
  }

  Future<void> _pinSelectedItems() async {
    final playlist = _activePlaylist;
    if (!_capabilities.canReorderItems ||
        playlist == null ||
        _selectedItemIds.isEmpty ||
        _pinningItems ||
        _filterActive ||
        personalPlaylistItemOrderWithSelectionPinnedToFront(
              items: _items,
              selectedItemIds: _selectedItemIds,
            ) ==
            null) {
      return;
    }
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
    if (!_capabilities.canDeleteItems ||
        playlist == null ||
        _busyItemIds.contains(item.id)) {
      return;
    }
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
    if (!_capabilities.canReorderItems ||
        playlist == null ||
        _filterActive ||
        _busyItemIds.contains(item.id)) {
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
    final Widget body;
    if (_loading && _playlists.isEmpty) {
      body = const Center(child: CircularProgressIndicator(color: _cyan));
    } else if (_playlists.isEmpty) {
      body = const Align(
        alignment: Alignment.topCenter,
        child: SizedBox(
          width: double.infinity,
          child: _SettingsEmptyState(text: '暂无歌单，点击上方按钮新建'),
        ),
      );
    } else if (visiblePlaylists.isEmpty) {
      body = const Align(
        alignment: Alignment.topCenter,
        child: SizedBox(
          width: double.infinity,
          child: _SettingsEmptyState(text: '没有符合筛选条件的歌单'),
        ),
      );
    } else {
      body = _MusicPlaylistSummaryList(
        playlists: visiblePlaylists,
        managing: _managingPlaylists,
        selectionNumbers: selectionNumbers,
        busy: _managingPlaylists && _playlistManagementBusy,
        filterActive: _playlistFilterActive,
        onTap: _openOrSelectPlaylist,
        onRename: _renamePlaylist,
        onMove: _movePlaylist,
        useCloneAction: widget.controller.roomScoped,
        cloningPlaylistId: _cloningPlaylistId,
        canShare: widget.shareApi != null && !widget.controller.roomScoped,
        sharingPlaylistId: _sharingPlaylistId,
        onShare: _sharePersonalPlaylist,
        onClone: widget.controller.canCloneRoomPlaylistsToPersonal
            ? _cloneRoomPlaylist
            : null,
      );
    }
    return SettingsFixedHeaderCard(
      title: widget.title,
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
      headerChildren: [
        StickerActionGrid(
          actions: [
            StickerActionGridEntry(
              label: _managingPlaylists ? '删除' : '新建歌单',
              button: Button(
                key: _managingPlaylists
                    ? const ValueKey('delete-selected-personal-music-playlists')
                    : const ValueKey('create-personal-music-playlist'),
                onPressed: _managingPlaylists
                    ? (_capabilities.canDeletePlaylists &&
                              _selectedPlaylistIds.isNotEmpty &&
                              !_playlistManagementBusy
                          ? _deleteSelectedPlaylists
                          : null)
                    : (_capabilities.canCreatePlaylists &&
                              !_playlistManagementBusy &&
                              _playlists.length < _maxPlaylists
                          ? _createPlaylist
                          : null),
                loading: _managingPlaylists ? _deletingPlaylists : _creating,
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
                onPressed:
                    _playlistManagementBusy || !_capabilities.canManagePlaylists
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
                onPressed: _playlistManagementBusy ? null : _openPlaylistFilter,
                selected: _playlistFilterActive,
                icon: const Icon(Icons.filter_alt_outlined),
                width: double.infinity,
                child: const Text('筛选'),
              ),
            ),
            if (_managingPlaylists && widget.controller.canMergePlaylists)
              StickerActionGridEntry(
                label: '合并',
                button: Button(
                  key: const ValueKey('merge-selected-music-playlists'),
                  onPressed:
                      !_playlistManagementBusy &&
                          _selectedPlaylistIds.length >= 2
                      ? _mergeSelectedPlaylists
                      : null,
                  loading: _mergingPlaylists,
                  icon: const Icon(Icons.merge_type),
                  width: double.infinity,
                  child: const Text('合并'),
                ),
              ),
            if (_managingPlaylists)
              StickerActionGridEntry(
                label: '置顶',
                button: Button(
                  key: const ValueKey('pin-selected-personal-music-playlists'),
                  onPressed:
                      !_playlistManagementBusy &&
                          canPinSelection &&
                          _capabilities.canReorderPlaylists
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
                      !_playlistManagementBusy && visiblePlaylists.isNotEmpty
                      ? () => _toggleSelectVisiblePlaylists(visiblePlaylists)
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
      ],
      body: body,
    );
  }

  Widget _buildPlaylistItemsScroller({
    required Map<String, int> selectionNumbers,
  }) {
    if (_loadingItems && _items.isEmpty) {
      return const Center(child: CircularProgressIndicator(color: _cyan));
    }
    if (_items.isEmpty) {
      return Align(
        alignment: Alignment.topCenter,
        child: SizedBox(
          width: double.infinity,
          child: _SettingsEmptyState(
            text: _filterActive ? '没有匹配的歌曲' : '歌单还是空的',
          ),
        ),
      );
    }

    final itemCount = _items.length + (_itemsHaveMore ? 1 : 0);
    return ListView.separated(
      key: const ValueKey('personal-music-playlist-items-scroll'),
      padding: EdgeInsets.zero,
      itemCount: itemCount,
      separatorBuilder: (_, _) => const SizedBox(height: 10),
      itemBuilder: (context, index) {
        if (index == _items.length) {
          return Button(
            key: const ValueKey('load-more-personal-playlist-items'),
            onPressed: _loadingMore ? null : () => _loadItems(reset: false),
            loading: _loadingMore,
            icon: const Icon(Icons.expand_more),
            width: double.infinity,
            child: const Text('加载更多'),
          );
        }
        final item = _items[index];
        return _MusicPlaylistItemTile(
          key: ValueKey('personal-music-playlist-item-${item.id}'),
          item: item,
          managing: _managingItems,
          selectionNumber: selectionNumbers[item.id],
          busy:
              _pinningItems ||
              _deletingItems ||
              _addingSelectedItems ||
              _busyItemIds.contains(item.id),
          canMoveUp: !_filterActive && index > 0,
          canMoveDown:
              !_filterActive && (index < _items.length - 1 || _itemsHaveMore),
          onTap: () => _toggleItemSelection(item.id),
          onDelete: () => _deleteSingleItem(item),
          onMoveUp: () => _moveItem(item, -1),
          onMoveDown: () => _moveItem(item, 1),
          previewController: _previewController,
          playlists: _playlists,
          playlistsRoomScoped: widget.controller.roomScoped,
          onAddToPlaylist: _capabilities.canAddItems
              ? (playlist) => _addPlaylistItemToPlaylist(item, playlist)
              : null,
        );
      },
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
    final canPinSelectedItems =
        !_filterActive &&
        personalPlaylistItemOrderWithSelectionPinnedToFront(
              items: _items,
              selectedItemIds: _selectedItemIds,
            ) !=
            null;
    return SettingsFixedHeaderCard(
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
      headerChildren: [
        StickerActionGrid(
          actions: [
            StickerActionGridEntry(
              label: _managingItems ? '删除' : '搜索添加',
              button: Button(
                key: _managingItems
                    ? const ValueKey(
                        'delete-selected-personal-music-playlist-items',
                      )
                    : const ValueKey('search-add-personal-music-playlist-item'),
                onPressed: _managingItems
                    ? (_capabilities.canDeleteItems &&
                              !_deletingItems &&
                              !_addingSelectedItems &&
                              _selectedItemIds.isNotEmpty
                          ? _deleteSelectedItems
                          : null)
                    : (_busy || !_capabilities.canAddItems
                          ? null
                          : _openTrackSearchDialog),
                loading: _managingItems ? _deletingItems : false,
                tone: _managingItems ? ButtonTone.danger : ButtonTone.primary,
                icon: Icon(
                  _managingItems ? Icons.delete_outline : Icons.playlist_add,
                ),
                width: double.infinity,
                child: Text(_managingItems ? '删除' : '搜索添加'),
              ),
            ),
            StickerActionGridEntry(
              label: _managingItems ? '取消管理' : '管理',
              button: Button(
                key: const ValueKey('manage-personal-music-playlist-items'),
                onPressed: _busy || !_capabilities.canManageItems
                    ? null
                    : _toggleManagingItems,
                selected: _managingItems,
                tone: _managingItems ? ButtonTone.primary : ButtonTone.neutral,
                icon: Icon(_managingItems ? Icons.close : Icons.checklist_rtl),
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
                label: '添加到歌单',
                button: Button(
                  key: const ValueKey(
                    'add-selected-personal-music-playlist-items',
                  ),
                  onPressed:
                      widget.controller.canBatchAddItems &&
                          !_addingSelectedItems &&
                          _selectedItemIds.isNotEmpty
                      ? _addSelectedItemsToPlaylist
                      : null,
                  loading: _addingSelectedItems,
                  icon: const Icon(Icons.playlist_add),
                  width: double.infinity,
                  child: const Text('添加到歌单'),
                ),
              ),
            if (_managingItems)
              StickerActionGridEntry(
                label: '置顶',
                button: Button(
                  key: const ValueKey(
                    'pin-selected-personal-music-playlist-items',
                  ),
                  onPressed:
                      !_pinningItems &&
                          canPinSelectedItems &&
                          _capabilities.canReorderItems
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
                label: allVisibleSelected ? '取消全选' : '全选已加载',
                button: Button(
                  onPressed: _items.isEmpty ? null : _toggleSelectVisible,
                  selected: allVisibleSelected,
                  icon: Icon(
                    allVisibleSelected
                        ? Icons.check_box
                        : Icons.check_box_outline_blank,
                  ),
                  width: double.infinity,
                  child: Text(allVisibleSelected ? '取消全选' : '全选已加载'),
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
      ],
      body: _buildPlaylistItemsScroller(selectionNumbers: itemSelectionNumbers),
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
    required this.useCloneAction,
    required this.cloningPlaylistId,
    required this.canShare,
    required this.sharingPlaylistId,
    required this.onShare,
    required this.onClone,
  });

  final List<PersonalMusicPlaylist> playlists;
  final bool managing;
  final Map<String, int> selectionNumbers;
  final bool busy;
  final bool filterActive;
  final ValueChanged<PersonalMusicPlaylist> onTap;
  final ValueChanged<PersonalMusicPlaylist> onRename;
  final void Function(PersonalMusicPlaylist playlist, int direction) onMove;
  final bool useCloneAction;
  final String? cloningPlaylistId;
  final ValueChanged<PersonalMusicPlaylist>? onClone;
  final bool canShare;
  final String? sharingPlaylistId;
  final ValueChanged<PersonalMusicPlaylist> onShare;

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
        return ListView.separated(
          key: const ValueKey('personal-music-playlists-scroll'),
          padding: EdgeInsets.zero,
          itemCount: playlists.length,
          separatorBuilder: (_, _) => const SizedBox(height: 10),
          itemBuilder: (context, index) => _MusicPlaylistSummaryTile(
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
                  selected: selectionNumbers.containsKey(playlists[index].id),
                  controlsStacked: stackControls,
                )
                ? 2
                : 1,
            onTap: () => onTap(playlists[index]),
            onRename: () => onRename(playlists[index]),
            onMoveUp: () => onMove(playlists[index], -1),
            onMoveDown: () => onMove(playlists[index], 1),
            useCloneAction: useCloneAction,
            cloning: cloningPlaylistId == playlists[index].id,
            onClone: onClone == null ? null : () => onClone!(playlists[index]),
            canShare: canShare,
            sharing: sharingPlaylistId == playlists[index].id,
            onShare: () => onShare(playlists[index]),
          ),
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
    required this.useCloneAction,
    required this.cloning,
    required this.onClone,
    required this.canShare,
    required this.sharing,
    required this.onShare,
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
  final bool useCloneAction;
  final bool cloning;
  final VoidCallback? onClone;
  final bool canShare;
  final bool sharing;
  final VoidCallback onShare;

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
          if (useCloneAction)
            ButtonIcon(
              key: ValueKey('clone-room-music-playlist-${playlist.id}'),
              tooltip: '克隆',
              onPressed: busy || cloning ? null : onClone,
              loading: cloning,
              icon: const Icon(Icons.library_add_outlined),
              size: 36,
            )
          else
            ButtonIcon(
              key: ValueKey('share-personal-music-playlist-${playlist.id}'),
              tooltip: '分享',
              onPressed: busy || !canShare || sharing ? null : onShare,
              loading: sharing,
              icon: const Icon(Icons.share_outlined),
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
    required this.previewController,
    required this.playlists,
    required this.playlistsRoomScoped,
    required this.onAddToPlaylist,
  });

  final MusicBoxSearchResult result;
  final String query;
  final bool loading;
  final VoidCallback onAdd;
  final MusicTrackPreviewController? previewController;
  final List<PersonalMusicPlaylist> playlists;
  final bool playlistsRoomScoped;
  final Future<void> Function(
    MusicBoxSearchResult track,
    PersonalMusicPlaylist playlist,
  )
  onAddToPlaylist;

  @override
  Widget build(BuildContext context) {
    final panel = _SettingsSubPanel(
      key: ValueKey<String>(
        'music-playlist-search-result:${result.source}:${result.trackId}',
      ),
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
                  text: personalPlaylistArtistsLabel(result.artists),
                  query: query,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: _textMuted, fontSize: 12),
                ),
              ],
            ),
          ),
          const SizedBox(width: 10),
          _ManagementCardActionArea(
            child: ButtonIcon(
              key: ValueKey<String>(
                'music-playlist-search-result-add:${result.source}:${result.trackId}',
              ),
              tooltip: '添加到当前歌单',
              onPressed: loading ? null : onAdd,
              loading: loading,
              tone: ButtonTone.primary,
              icon: const Icon(Icons.add),
              size: 40,
            ),
          ),
        ],
      ),
    );
    final preview = previewController;
    if (preview == null) return panel;
    return MusicTrackHoverCard(
      data: MusicTrackCardData(
        id: '${result.source}:${result.trackId}',
        source: result.source,
        trackId: result.trackId,
        title: result.name,
        artists: result.artists,
        durationMs: 0,
      ),
      previewController: preview,
      playlists: playlists,
      playlistsRoomScoped: playlistsRoomScoped,
      onAddToPlaylist: (playlist) => onAddToPlaylist(result, playlist),
      child: panel,
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
    required this.previewController,
    required this.playlists,
    required this.playlistsRoomScoped,
    required this.onAddToPlaylist,
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
  final MusicTrackPreviewController? previewController;
  final List<PersonalMusicPlaylist> playlists;
  final bool playlistsRoomScoped;
  final Future<void> Function(PersonalMusicPlaylist playlist)? onAddToPlaylist;

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
    final panel = MusicPlaylistTrackSurface(
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
    if (!managing) {
      final preview = previewController;
      if (preview == null) return panel;
      return MusicTrackHoverCard(
        data: MusicTrackCardData(
          id: item.id,
          source: item.source,
          trackId: item.trackId,
          title: item.title,
          artists: item.artists,
          durationMs: item.durationMs,
        ),
        previewController: preview,
        playlists: playlists,
        playlistsRoomScoped: playlistsRoomScoped,
        onAddToPlaylist: onAddToPlaylist,
        child: panel,
      );
    }
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
    required this.previewController,
    required this.playlists,
    required this.playlistsRoomScoped,
    required this.onAddToPlaylist,
    required this.onTrackAdded,
  });

  final PersonalMusicPlaylistsController controller;
  final String playlistId;
  final MusicTrackPreviewController? previewController;
  final List<PersonalMusicPlaylist> playlists;
  final bool playlistsRoomScoped;
  final Future<void> Function(
    MusicBoxSearchResult track,
    PersonalMusicPlaylist playlist,
  )
  onAddToPlaylist;
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
                          previewController: widget.previewController,
                          playlists: widget.playlists,
                          playlistsRoomScoped: widget.playlistsRoomScoped,
                          onAddToPlaylist: widget.onAddToPlaylist,
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

class _MusicPlaylistShareRoomDialog extends StatefulWidget {
  const _MusicPlaylistShareRoomDialog({
    required this.api,
    required this.playlistName,
  });

  final GangApi api;
  final String playlistName;

  @override
  State<_MusicPlaylistShareRoomDialog> createState() =>
      _MusicPlaylistShareRoomDialogState();
}

class _MusicPlaylistShareRoomDialogState
    extends State<_MusicPlaylistShareRoomDialog> {
  final TextEditingController _searchController = TextEditingController();
  final FocusNode _searchFocusNode = FocusNode();
  List<RoomCard> _rooms = const [];
  String? _selectedRoomId;
  String? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    unawaited(_loadRooms());
  }

  @override
  void dispose() {
    _searchController.dispose();
    _searchFocusNode.dispose();
    super.dispose();
  }

  Future<void> _loadRooms() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final rooms = <RoomCard>[];
      final seen = <String>{};
      final seenCursors = <String>{};
      String? cursor;
      do {
        final page = await widget.api.listRooms(limit: 50, cursor: cursor);
        for (final room in page.rooms) {
          if (seen.add(room.id)) rooms.add(room);
        }
        final next = page.nextCursor?.trim();
        cursor = next == null || next.isEmpty || !seenCursors.add(next)
            ? null
            : next;
      } while (cursor != null);
      if (!mounted) return;
      setState(() {
        _rooms = rooms;
        _loading = false;
      });
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _searchFocusNode.requestFocus();
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = error.toString();
      });
    }
  }

  List<RoomCard> get _visibleRooms {
    final query = _searchController.text.trim().toLowerCase();
    if (query.isEmpty) return _rooms;
    return _rooms
        .where((room) {
          return room.displayName.toLowerCase().contains(query) ||
              room.name.toLowerCase().contains(query) ||
              room.rid.toLowerCase().contains(query);
        })
        .toList(growable: false);
  }

  void _confirm() {
    RoomCard? selected;
    for (final room in _rooms) {
      if (room.id == _selectedRoomId) {
        selected = room;
        break;
      }
    }
    if (selected != null) Navigator.of(context).pop(selected);
  }

  @override
  Widget build(BuildContext context) {
    final visibleRooms = _visibleRooms;
    final viewportHeight = (MediaQuery.sizeOf(context).height * 0.58).clamp(
      240.0,
      560.0,
    );
    return DialogFrame(
      title: '分享歌单',
      icon: Icons.share_outlined,
      maxWidth: 620,
      adaptiveActions: [
        ResponsiveDialogAction(
          label: '取消',
          onPressed: () => Navigator.of(context).pop(),
        ),
        ResponsiveDialogAction(
          label: '确认分享',
          icon: Icons.send_outlined,
          tone: ButtonTone.primary,
          onPressed: _selectedRoomId == null ? null : _confirm,
        ),
      ],
      child: SizedBox(
        height: viewportHeight,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              '选择要将“${widget.playlistName}”分享到的文字频道',
              style: const TextStyle(color: _textMuted, fontSize: 12),
            ),
            const SizedBox(height: 10),
            Input(
              key: const ValueKey('music-playlist-share-room-search'),
              controller: _searchController,
              focusNode: _searchFocusNode,
              prefixIcon: Icons.search,
              hintText: '搜索房间',
              showClearButton: true,
              onChanged: (_) => setState(() {}),
              textInputAction: TextInputAction.search,
            ),
            if (_error != null) ...[
              const SizedBox(height: 8),
              _MusicPlaylistErrorText(_error!),
            ],
            const SizedBox(height: 10),
            Expanded(
              child: _loading
                  ? const Center(child: CircularProgressIndicator(color: _cyan))
                  : visibleRooms.isEmpty
                  ? _SettingsEmptyState(
                      text: _rooms.isEmpty ? '还没有可分享的房间' : '没有匹配的房间',
                    )
                  : ListView.separated(
                      padding: EdgeInsets.zero,
                      itemCount: visibleRooms.length,
                      separatorBuilder: (_, _) => const SizedBox(height: 8),
                      itemBuilder: (context, index) {
                        final room = visibleRooms[index];
                        final selected = room.id == _selectedRoomId;
                        return UiPointerTapRegion(
                          key: ValueKey(
                            'music-playlist-share-room-option-${room.id}',
                          ),
                          onTap: () =>
                              setState(() => _selectedRoomId = room.id),
                          disableSelection: true,
                          child: _SettingsSubPanel(
                            highlighted: selected,
                            hoverable: true,
                            child: Row(
                              children: [
                                Avatar(
                                  label: room.displayName,
                                  imageUrl: AppConfigScope.of(
                                    context,
                                  ).resolveAssetUrl(room.avatarUrl),
                                  defaultAvatarKey: room.defaultAvatarKey,
                                  size: 36,
                                ),
                                const SizedBox(width: 10),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        room.displayName,
                                        style: const TextStyle(
                                          color: _textPrimary,
                                          fontSize: 14,
                                          fontWeight: FontWeight.w600,
                                        ),
                                      ),
                                      if (room.rid.trim().isNotEmpty) ...[
                                        const SizedBox(height: 2),
                                        Text(
                                          'RID：${room.rid}',
                                          style: const TextStyle(
                                            color: _textMuted,
                                            fontSize: 12,
                                          ),
                                        ),
                                      ],
                                    ],
                                  ),
                                ),
                                Icon(
                                  selected
                                      ? Icons.radio_button_checked
                                      : Icons.radio_button_off,
                                  color: selected ? _cyan : _textMuted,
                                  size: 20,
                                ),
                              ],
                            ),
                          ),
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

class _MusicPlaylistNameDialog extends StatefulWidget {
  const _MusicPlaylistNameDialog({
    required this.title,
    required this.icon,
    required this.confirmLabel,
    required this.confirmIcon,
    this.initialName = '',
    this.loadImportPlaylists,
    this.description,
  });

  final String title;
  final IconData icon;
  final String confirmLabel;
  final IconData confirmIcon;
  final String initialName;
  final Future<PersonalMusicPlaylistPage?> Function()? loadImportPlaylists;
  final String? description;

  @override
  State<_MusicPlaylistNameDialog> createState() =>
      _MusicPlaylistNameDialogState();
}

class _MusicPlaylistNameDialogState extends State<_MusicPlaylistNameDialog> {
  late final TextEditingController _controller;
  final FocusNode _focusNode = FocusNode();
  PersonalMusicPlaylist? _importPlaylist;
  bool _loadingImportPlaylists = false;
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
    Navigator.of(context).pop(
      PersonalPlaylistCreateDraft(
        name: name,
        importPlaylistId: _importPlaylist?.id,
      ),
    );
  }

  Future<void> _chooseImportPlaylist() async {
    final load = widget.loadImportPlaylists;
    if (load == null || _loadingImportPlaylists) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _loadingImportPlaylists = true;
      _error = null;
    });
    try {
      final page = await load();
      if (!mounted) return;
      final selected = await showDialog<PersonalMusicPlaylist>(
        context: context,
        builder: (context) => _MusicPlaylistImportDialog(
          playlists: page?.playlists ?? const [],
          selectedPlaylistId: _importPlaylist?.id,
        ),
      );
      if (!mounted || selected == null) return;
      setState(() => _importPlaylist = selected);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _loadingImportPlaylists = false);
    }
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
          if (widget.description != null) ...[
            Text(
              widget.description!,
              style: const TextStyle(
                color: _textMuted,
                fontSize: 12,
                height: 1.5,
              ),
            ),
            const SizedBox(height: 10),
          ],
          Input(
            key: const ValueKey('personal-music-playlist-name-input'),
            controller: _controller,
            focusNode: _focusNode,
            hintText: widget.initialName.isEmpty ? '歌单名称' : '',
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _submit(),
          ),
          if (widget.loadImportPlaylists != null) ...[
            const SizedBox(height: 10),
            Button(
              key: const ValueKey<String>(
                'import-personal-music-playlist-button',
              ),
              width: double.infinity,
              loading: _loadingImportPlaylists,
              selected: _importPlaylist != null,
              tone: _importPlaylist == null
                  ? ButtonTone.neutral
                  : ButtonTone.primary,
              icon: const Icon(Icons.library_add_outlined),
              onPressed: _chooseImportPlaylist,
              child: const Text('导入我的歌单'),
            ),
            if (_importPlaylist != null) ...[
              const SizedBox(height: 8),
              Container(
                key: const ValueKey<String>(
                  'selected-personal-music-playlist-import',
                ),
                padding: const EdgeInsets.fromLTRB(12, 8, 6, 8),
                decoration: BoxDecoration(
                  color: _primaryDark,
                  border: Border.all(color: UiColors.selectedBorder),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.queue_music, color: _cyan, size: 20),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            _importPlaylist!.name,
                            style: const TextStyle(
                              color: _textPrimary,
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            '${_importPlaylist!.itemCount} 首歌曲',
                            style: const TextStyle(
                              color: _textMuted,
                              fontSize: 12,
                            ),
                          ),
                        ],
                      ),
                    ),
                    ButtonIcon(
                      key: const ValueKey<String>(
                        'clear-personal-music-playlist-import',
                      ),
                      icon: const Icon(Icons.close),
                      tooltip: '取消导入',
                      onPressed: () => setState(() => _importPlaylist = null),
                      size: 32,
                    ),
                  ],
                ),
              ),
            ],
          ],
          if (_error != null) ...[
            const SizedBox(height: 8),
            _MusicPlaylistErrorText(_error!),
          ],
        ],
      ),
    );
  }
}

class _MusicPlaylistImportDialog extends StatelessWidget {
  const _MusicPlaylistImportDialog({
    required this.playlists,
    required this.selectedPlaylistId,
    this.title = '选择歌单',
    this.emptyText = '我的歌单为空',
    this.pickerKey = const ValueKey<String>(
      'personal-music-playlist-import-picker',
    ),
    this.optionKeyPrefix = 'personal-music-playlist-import-option',
  });

  final List<PersonalMusicPlaylist> playlists;
  final String? selectedPlaylistId;
  final String title;
  final String emptyText;
  final Key pickerKey;
  final String optionKeyPrefix;

  @override
  Widget build(BuildContext context) {
    final viewportHeight = (MediaQuery.sizeOf(context).height * 0.56).clamp(
      180.0,
      520.0,
    );
    return DialogFrame(
      title: title,
      icon: Icons.library_music_outlined,
      maxWidth: 560,
      adaptiveActions: [
        ResponsiveDialogAction(
          label: '取消',
          onPressed: () => Navigator.of(context).pop(),
        ),
      ],
      child: SizedBox(
        key: pickerKey,
        height: viewportHeight,
        child: playlists.isEmpty
            ? Center(child: _SettingsEmptyState(text: emptyText))
            : ListView.separated(
                padding: EdgeInsets.zero,
                itemCount: playlists.length,
                separatorBuilder: (_, _) => const SizedBox(height: 10),
                itemBuilder: (context, index) {
                  final playlist = playlists[index];
                  return _MusicPlaylistImportTile(
                    playlist: playlist,
                    selected: playlist.id == selectedPlaylistId,
                    optionKeyPrefix: optionKeyPrefix,
                    onPressed: () => Navigator.of(context).pop(playlist),
                  );
                },
              ),
      ),
    );
  }
}

class _MusicPlaylistImportTile extends StatelessWidget {
  const _MusicPlaylistImportTile({
    required this.playlist,
    required this.selected,
    this.optionKeyPrefix = 'personal-music-playlist-import-option',
    required this.onPressed,
  });

  final PersonalMusicPlaylist playlist;
  final bool selected;
  final String optionKeyPrefix;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        const nameStyle = TextStyle(
          color: _textPrimary,
          fontSize: 13,
          fontWeight: FontWeight.w600,
          height: 1.25,
        );
        final namePainter =
            TextPainter(
              text: TextSpan(text: playlist.name, style: nameStyle),
              textDirection: Directionality.of(context),
              textScaler: MediaQuery.textScalerOf(context),
            )..layout(
              maxWidth: (constraints.maxWidth - 118).clamp(
                40.0,
                double.infinity,
              ),
            );
        final measuredHeight = namePainter.height + 39;
        final height = measuredHeight < 58 ? 58.0 : measuredHeight;
        return PressableSurface(
          key: ValueKey<String>('$optionKeyPrefix-${playlist.id}'),
          height: height,
          selected: selected,
          onPressed: onPressed,
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          child: Row(
            children: [
              const Icon(Icons.queue_music, color: _cyan, size: 22),
              const SizedBox(width: 12),
              Expanded(child: Text(playlist.name, style: nameStyle)),
              const SizedBox(width: 10),
              Text(
                '${playlist.itemCount} 首',
                style: const TextStyle(color: _textMuted, fontSize: 12),
              ),
              const SizedBox(width: 6),
              Icon(
                selected ? Icons.check : Icons.chevron_right,
                color: selected ? _cyan : _textMuted,
                size: 20,
              ),
            ],
          ),
        );
      },
    );
  }
}
