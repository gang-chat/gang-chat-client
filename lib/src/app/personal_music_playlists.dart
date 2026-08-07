import '../protocol/api_client.dart';
import '../protocol/models.dart';

const int personalMusicPlaylistPageSize = 50;
const String personalPlaylistCountAll = '';
const String personalPlaylistCountEmpty = 'empty';
const String personalPlaylistCount1To10 = '1-10';
const String personalPlaylistCount11To50 = '11-50';
const String personalPlaylistCount51To100 = '51-100';
const String personalPlaylistCountOver100 = '101+';

typedef MusicPlaylistTrackSearch =
    Future<List<MusicBoxSearchResult>> Function({
      required String keyword,
      required String source,
    });

class PersonalPlaylistFilterDraft {
  const PersonalPlaylistFilterDraft({
    required this.keyword,
    required this.countFilter,
  });

  final String keyword;
  final String countFilter;
}

class PersonalPlaylistCreateDraft {
  const PersonalPlaylistCreateDraft({
    required this.name,
    this.importPlaylistId,
  });

  final String name;
  final String? importPlaylistId;
}

class PersonalPlaylistItemFilterDraft {
  const PersonalPlaylistItemFilterDraft({
    required this.keyword,
    required this.source,
  });

  final String keyword;
  final String source;
}

class MusicPlaylistManagementCapabilities {
  const MusicPlaylistManagementCapabilities({
    this.canCreatePlaylists = true,
    this.canRenamePlaylists = true,
    this.canDeletePlaylists = true,
    this.canReorderPlaylists = true,
    this.canAddItems = true,
    this.canDeleteItems = true,
    this.canReorderItems = true,
  });

  const MusicPlaylistManagementCapabilities.readOnly()
    : canCreatePlaylists = false,
      canRenamePlaylists = false,
      canDeletePlaylists = false,
      canReorderPlaylists = false,
      canAddItems = false,
      canDeleteItems = false,
      canReorderItems = false;

  final bool canCreatePlaylists;
  final bool canRenamePlaylists;
  final bool canDeletePlaylists;
  final bool canReorderPlaylists;
  final bool canAddItems;
  final bool canDeleteItems;
  final bool canReorderItems;

  bool get canManagePlaylists =>
      canRenamePlaylists || canDeletePlaylists || canReorderPlaylists;

  bool get canManageItems => canDeleteItems || canReorderItems;
}

abstract interface class _MusicPlaylistsBackend {
  Object get identity;

  MusicPlaylistManagementCapabilities get capabilities;

  Future<PersonalMusicPlaylistPage> loadPlaylists();

  bool get canImportPersonalPlaylist;

  Future<PersonalMusicPlaylistPage?> loadImportPlaylists();

  bool get canCloneRoomPlaylistsToPersonal;

  Future<PersonalMusicPlaylist> cloneRoomPlaylistToPersonal({
    required String playlistId,
  });

  bool get canMergePlaylists;

  Future<PersonalMusicPlaylistMergeResult> mergePlaylists({
    required String name,
    required List<String> playlistIds,
  });

  bool get canBatchAddItems;

  Future<PersonalMusicPlaylistBatchAddResult> batchAddItems({
    required String sourcePlaylistId,
    required String targetPlaylistId,
    required List<String> itemIds,
  });

  Future<PersonalMusicPlaylist> createPlaylist({
    required String name,
    String? importPlaylistId,
  });

  Future<PersonalMusicPlaylist> renamePlaylist({
    required String playlistId,
    required String name,
  });

  Future<void> deletePlaylist(String playlistId);

  Future<void> pinPlaylists({required List<String> playlistIds});

  Future<void> movePlaylist({
    required String playlistId,
    required String direction,
  });

  Future<PersonalMusicPlaylistItemsPage> loadItems({
    required String playlistId,
    required int page,
    required int pageSize,
    String? keyword,
    String? source,
  });

  Future<List<MusicBoxSearchResult>> searchTracks({
    required String keyword,
    required String source,
  });

  Future<PersonalMusicPlaylistItem> addTrack({
    required String playlistId,
    required MusicBoxSearchResult track,
  });

  Future<void> deleteItems({
    required String playlistId,
    required List<String> itemIds,
  });

  Future<void> moveItem({
    required String playlistId,
    required String itemId,
    required String direction,
  });

  Future<void> reorderItems({
    required String playlistId,
    required List<String> itemIds,
  });
}

class PersonalMusicPlaylistsController {
  PersonalMusicPlaylistsController(PersonalMusicPlaylistApi? api)
    : _backend = api == null ? null : _PersonalMusicPlaylistsBackend(api);

  PersonalMusicPlaylistsController.room({
    required RoomMusicPlaylistApi? roomApi,
    PersonalMusicPlaylistApi? personalApi,
    required String roomId,
    required bool canManage,
    required MusicPlaylistTrackSearch searchTracks,
  }) : _backend = roomApi == null
           ? null
           : _RoomMusicPlaylistsBackend(
               roomApi: roomApi,
               personalApi: personalApi,
               roomId: roomId,
               canManage: canManage,
               searchTracksCallback: searchTracks,
             );

  final _MusicPlaylistsBackend? _backend;

  Object? get api => _backend?.identity;

  MusicPlaylistManagementCapabilities get capabilities =>
      _backend?.capabilities ??
      const MusicPlaylistManagementCapabilities.readOnly();

  bool get available => _backend != null;

  bool get roomScoped => _backend is _RoomMusicPlaylistsBackend;

  String? get sourceRoomId {
    final backend = _backend;
    return backend is _RoomMusicPlaylistsBackend ? backend.roomId : null;
  }

  bool get canImportPersonalPlaylist =>
      _backend?.canImportPersonalPlaylist ?? false;

  bool get canCloneRoomPlaylistsToPersonal =>
      _backend?.canCloneRoomPlaylistsToPersonal ?? false;

  bool get canMergePlaylists => _backend?.canMergePlaylists ?? false;

  bool get canBatchAddItems => _backend?.canBatchAddItems ?? false;

  Future<PersonalMusicPlaylistPage?> loadPlaylists() {
    final client = _backend;
    if (client == null) return Future.value();
    return client.loadPlaylists();
  }

  Future<PersonalMusicPlaylistPage?> loadImportPlaylists() {
    return _backend?.loadImportPlaylists() ?? Future.value();
  }

  Future<PersonalMusicPlaylist?> cloneRoomPlaylistToPersonal(
    String playlistId,
  ) {
    final normalized = playlistId.trim();
    if (normalized.isEmpty) return Future.value();
    return _backend?.cloneRoomPlaylistToPersonal(playlistId: normalized) ??
        Future.value();
  }

  Future<PersonalMusicPlaylistMergeResult?> mergePlaylists({
    required String name,
    required List<String> playlistIds,
  }) {
    final normalizedName = normalizedPersonalPlaylistName(name);
    final normalizedIDs = uniquePersonalPlaylistIds(playlistIds);
    if (normalizedName == null ||
        normalizedIDs.length < 2 ||
        normalizedIDs.length != playlistIds.length) {
      return Future.value();
    }
    return _backend?.mergePlaylists(
          name: normalizedName,
          playlistIds: normalizedIDs,
        ) ??
        Future.value();
  }

  Future<PersonalMusicPlaylistBatchAddResult?> batchAddItems({
    required String sourcePlaylistId,
    required String targetPlaylistId,
    required Iterable<String> itemIds,
  }) {
    final sourceID = sourcePlaylistId.trim();
    final targetID = targetPlaylistId.trim();
    final requestedItemIDs = itemIds.toList(growable: false);
    final normalizedItemIDs = uniquePersonalPlaylistItemIds(requestedItemIDs);
    if (sourceID.isEmpty ||
        targetID.isEmpty ||
        sourceID == targetID ||
        normalizedItemIDs.isEmpty ||
        normalizedItemIDs.length != requestedItemIDs.length ||
        normalizedItemIDs.length > 500) {
      return Future.value();
    }
    return _backend?.batchAddItems(
          sourcePlaylistId: sourceID,
          targetPlaylistId: targetID,
          itemIds: normalizedItemIDs,
        ) ??
        Future.value();
  }

  Future<PersonalMusicPlaylist?> createPlaylist(
    String name, {
    String? importPlaylistId,
  }) {
    final normalized = normalizedPersonalPlaylistName(name);
    if (normalized == null) return Future.value();
    final client = _backend;
    if (client == null) return Future.value();
    return client.createPlaylist(
      name: normalized,
      importPlaylistId: importPlaylistId?.trim(),
    );
  }

  Future<PersonalMusicPlaylist?> renamePlaylist({
    required String playlistId,
    required String name,
  }) {
    final normalized = normalizedPersonalPlaylistName(name);
    if (normalized == null) return Future.value();
    final client = _backend;
    if (client == null) return Future.value();
    return client.renamePlaylist(playlistId: playlistId, name: normalized);
  }

  Future<void> deletePlaylist(String playlistId) {
    return _backend?.deletePlaylist(playlistId) ?? Future.value();
  }

  Future<void> pinPlaylists(List<String> playlistIds) {
    final ids = uniquePersonalPlaylistIds(playlistIds);
    if (ids.isEmpty) return Future.value();
    return _backend?.pinPlaylists(playlistIds: ids) ?? Future.value();
  }

  Future<void> movePlaylist({required String playlistId, required int delta}) {
    if (delta != -1 && delta != 1) {
      return Future.error(
        ArgumentError.value(delta, 'delta', 'must be -1 or 1'),
      );
    }
    return _backend?.movePlaylist(
          playlistId: playlistId,
          direction: delta < 0 ? 'up' : 'down',
        ) ??
        Future.value();
  }

  Future<PersonalMusicPlaylistItemsPage?> loadItems({
    required String playlistId,
    int page = 1,
    String keyword = '',
    String source = '',
  }) {
    final client = _backend;
    if (client == null) return Future.value();
    return client.loadItems(
      playlistId: playlistId,
      page: page,
      pageSize: personalMusicPlaylistPageSize,
      keyword: keyword,
      source: source,
    );
  }

  Future<List<MusicBoxSearchResult>?> searchTracks({
    required String keyword,
    required String source,
  }) {
    final normalizedKeyword = keyword.trim();
    if (normalizedKeyword.isEmpty) {
      return Future.value(const <MusicBoxSearchResult>[]);
    }
    final client = _backend;
    if (client == null) return Future.value();
    return client.searchTracks(keyword: normalizedKeyword, source: source);
  }

  Future<PersonalMusicPlaylistItem?> addTrack({
    required String playlistId,
    required MusicBoxSearchResult track,
  }) {
    final client = _backend;
    if (client == null) return Future.value();
    return client.addTrack(playlistId: playlistId, track: track);
  }

  Future<void> deleteItems({
    required String playlistId,
    required List<String> itemIds,
  }) {
    final ids = uniquePersonalPlaylistItemIds(itemIds);
    if (ids.isEmpty) return Future.value();
    return _backend?.deleteItems(playlistId: playlistId, itemIds: ids) ??
        Future.value();
  }

  Future<void> moveItem({
    required String playlistId,
    required String itemId,
    required int delta,
  }) {
    if (delta != -1 && delta != 1) {
      return Future.error(
        ArgumentError.value(delta, 'delta', 'must be -1 or 1'),
      );
    }
    return _backend?.moveItem(
          playlistId: playlistId,
          itemId: itemId,
          direction: delta < 0 ? 'up' : 'down',
        ) ??
        Future.value();
  }

  Future<void> pinItems({
    required String playlistId,
    required Iterable<String> selectedItemIds,
  }) async {
    final client = _backend;
    if (client == null) return;
    final selectedIds = uniquePersonalPlaylistItemIds(selectedItemIds);
    if (selectedIds.isEmpty) return;

    final items = <PersonalMusicPlaylistItem>[];
    var page = 1;
    while (true) {
      final result = await client.loadItems(
        playlistId: playlistId,
        page: page,
        pageSize: personalMusicPlaylistPageSize,
      );
      items.addAll(result.items);
      if (!result.hasMore) break;
      if (result.items.isEmpty || items.length >= result.total) {
        throw StateError('歌单分页数据不完整，请刷新后重试');
      }
      page += 1;
    }

    final order = personalPlaylistItemOrderWithSelectionPinnedToFront(
      items: items,
      selectedItemIds: selectedIds,
    );
    if (order == null) return;
    await client.reorderItems(playlistId: playlistId, itemIds: order);
  }
}

class _PersonalMusicPlaylistsBackend implements _MusicPlaylistsBackend {
  const _PersonalMusicPlaylistsBackend(this.api);

  final PersonalMusicPlaylistApi api;

  @override
  Object get identity => api;

  @override
  MusicPlaylistManagementCapabilities get capabilities =>
      const MusicPlaylistManagementCapabilities();

  @override
  bool get canImportPersonalPlaylist => false;

  @override
  bool get canCloneRoomPlaylistsToPersonal => false;

  @override
  bool get canMergePlaylists => api is PersonalMusicPlaylistMergeApi;

  @override
  bool get canBatchAddItems => api is PersonalMusicPlaylistBatchAddApi;

  @override
  Future<PersonalMusicPlaylist> cloneRoomPlaylistToPersonal({
    required String playlistId,
  }) {
    return Future.error(StateError('个人歌单不能作为房间歌单的克隆来源'));
  }

  @override
  Future<PersonalMusicPlaylistMergeResult> mergePlaylists({
    required String name,
    required List<String> playlistIds,
  }) {
    final mergeApi = api;
    if (mergeApi is! PersonalMusicPlaylistMergeApi) {
      return Future.error(StateError('当前服务端不支持合并个人歌单'));
    }
    return (mergeApi as PersonalMusicPlaylistMergeApi)
        .mergePersonalMusicPlaylists(name: name, playlistIds: playlistIds);
  }

  @override
  Future<PersonalMusicPlaylistBatchAddResult> batchAddItems({
    required String sourcePlaylistId,
    required String targetPlaylistId,
    required List<String> itemIds,
  }) {
    final batchApi = api;
    if (batchApi is! PersonalMusicPlaylistBatchAddApi) {
      return Future.error(StateError('当前服务端不支持批量添加个人歌单歌曲'));
    }
    return (batchApi as PersonalMusicPlaylistBatchAddApi)
        .batchAddPersonalMusicPlaylistItems(
          sourcePlaylistId: sourcePlaylistId,
          targetPlaylistId: targetPlaylistId,
          itemIds: itemIds,
        );
  }

  @override
  Future<PersonalMusicPlaylistPage?> loadImportPlaylists() {
    return Future.value();
  }

  @override
  Future<PersonalMusicPlaylistPage> loadPlaylists() {
    return api.listPersonalMusicPlaylists();
  }

  @override
  Future<PersonalMusicPlaylist> createPlaylist({
    required String name,
    String? importPlaylistId,
  }) {
    if (importPlaylistId != null && importPlaylistId.isNotEmpty) {
      return Future.error(StateError('个人歌单不能从另一个个人歌单导入创建'));
    }
    return api.createPersonalMusicPlaylist(name: name);
  }

  @override
  Future<PersonalMusicPlaylist> renamePlaylist({
    required String playlistId,
    required String name,
  }) {
    return api.renamePersonalMusicPlaylist(playlistId: playlistId, name: name);
  }

  @override
  Future<void> deletePlaylist(String playlistId) {
    return api.deletePersonalMusicPlaylist(playlistId);
  }

  @override
  Future<void> pinPlaylists({required List<String> playlistIds}) {
    return api.pinPersonalMusicPlaylists(playlistIds: playlistIds);
  }

  @override
  Future<void> movePlaylist({
    required String playlistId,
    required String direction,
  }) {
    return api.movePersonalMusicPlaylist(
      playlistId: playlistId,
      direction: direction,
    );
  }

  @override
  Future<PersonalMusicPlaylistItemsPage> loadItems({
    required String playlistId,
    required int page,
    required int pageSize,
    String? keyword,
    String? source,
  }) {
    return api.getPersonalMusicPlaylist(
      playlistId: playlistId,
      page: page,
      pageSize: pageSize,
      keyword: keyword,
      source: source,
    );
  }

  @override
  Future<List<MusicBoxSearchResult>> searchTracks({
    required String keyword,
    required String source,
  }) {
    return api.searchPersonalMusicPlaylistTracks(
      keyword: keyword,
      source: source,
    );
  }

  @override
  Future<PersonalMusicPlaylistItem> addTrack({
    required String playlistId,
    required MusicBoxSearchResult track,
  }) {
    return api.addPersonalMusicPlaylistItem(
      playlistId: playlistId,
      track: track,
    );
  }

  @override
  Future<void> deleteItems({
    required String playlistId,
    required List<String> itemIds,
  }) {
    return api.deletePersonalMusicPlaylistItems(
      playlistId: playlistId,
      itemIds: itemIds,
    );
  }

  @override
  Future<void> moveItem({
    required String playlistId,
    required String itemId,
    required String direction,
  }) {
    return api.movePersonalMusicPlaylistItem(
      playlistId: playlistId,
      itemId: itemId,
      direction: direction,
    );
  }

  @override
  Future<void> reorderItems({
    required String playlistId,
    required List<String> itemIds,
  }) {
    return api.reorderPersonalMusicPlaylistItems(
      playlistId: playlistId,
      itemIds: itemIds,
    );
  }
}

class _RoomMusicPlaylistsBackend implements _MusicPlaylistsBackend {
  const _RoomMusicPlaylistsBackend({
    required this.roomApi,
    required this.personalApi,
    required this.roomId,
    required this.canManage,
    required this.searchTracksCallback,
  });

  final RoomMusicPlaylistApi roomApi;
  final PersonalMusicPlaylistApi? personalApi;
  final String roomId;
  final bool canManage;
  final MusicPlaylistTrackSearch searchTracksCallback;

  void _requireManagePermission() {
    if (!canManage) {
      throw StateError('当前账号没有管理房间歌单的权限');
    }
  }

  @override
  Object get identity => (roomApi, personalApi, roomId, canManage);

  @override
  MusicPlaylistManagementCapabilities get capabilities => canManage
      ? const MusicPlaylistManagementCapabilities()
      : const MusicPlaylistManagementCapabilities.readOnly();

  @override
  bool get canImportPersonalPlaylist =>
      canManage && personalApi != null && roomApi is RoomMusicPlaylistImportApi;

  @override
  bool get canCloneRoomPlaylistsToPersonal =>
      canManage && roomApi is RoomMusicPlaylistCloneApi;

  @override
  bool get canMergePlaylists =>
      canManage && roomApi is RoomMusicPlaylistMergeApi;

  @override
  bool get canBatchAddItems =>
      canManage && roomApi is RoomMusicPlaylistBatchAddApi;

  @override
  Future<PersonalMusicPlaylist> cloneRoomPlaylistToPersonal({
    required String playlistId,
  }) {
    _requireManagePermission();
    if (roomApi is! RoomMusicPlaylistCloneApi) {
      return Future.error(StateError('当前服务端不支持克隆房间歌单'));
    }
    return (roomApi as RoomMusicPlaylistCloneApi)
        .cloneRoomMusicPlaylistToPersonal(
          roomId: roomId,
          playlistId: playlistId,
        );
  }

  @override
  Future<PersonalMusicPlaylistMergeResult> mergePlaylists({
    required String name,
    required List<String> playlistIds,
  }) {
    _requireManagePermission();
    final mergeApi = roomApi;
    if (mergeApi is! RoomMusicPlaylistMergeApi) {
      return Future.error(StateError('当前服务端不支持合并房间歌单'));
    }
    return (mergeApi as RoomMusicPlaylistMergeApi).mergeRoomMusicPlaylists(
      roomId: roomId,
      name: name,
      playlistIds: playlistIds,
    );
  }

  @override
  Future<PersonalMusicPlaylistBatchAddResult> batchAddItems({
    required String sourcePlaylistId,
    required String targetPlaylistId,
    required List<String> itemIds,
  }) {
    _requireManagePermission();
    final batchApi = roomApi;
    if (batchApi is! RoomMusicPlaylistBatchAddApi) {
      return Future.error(StateError('当前服务端不支持批量添加房间歌单歌曲'));
    }
    return (batchApi as RoomMusicPlaylistBatchAddApi)
        .batchAddRoomMusicPlaylistItems(
          roomId: roomId,
          sourcePlaylistId: sourcePlaylistId,
          targetPlaylistId: targetPlaylistId,
          itemIds: itemIds,
        );
  }

  @override
  Future<PersonalMusicPlaylistPage?> loadImportPlaylists() {
    _requireManagePermission();
    final api = personalApi;
    if (api == null) return Future.value();
    return api.listPersonalMusicPlaylists(
      page: 1,
      pageSize: personalMusicPlaylistPageSize,
    );
  }

  @override
  Future<PersonalMusicPlaylistPage> loadPlaylists() {
    return roomApi.listRoomMusicPlaylists(roomId: roomId);
  }

  @override
  Future<PersonalMusicPlaylist> createPlaylist({
    required String name,
    String? importPlaylistId,
  }) {
    _requireManagePermission();
    final normalizedImportID = importPlaylistId?.trim() ?? '';
    if (normalizedImportID.isNotEmpty) {
      final importApi = roomApi;
      if (importApi is! RoomMusicPlaylistImportApi) {
        return Future.error(StateError('当前服务端不支持导入我的歌单'));
      }
      return (importApi as RoomMusicPlaylistImportApi)
          .createRoomMusicPlaylistFromPersonal(
            roomId: roomId,
            name: name,
            importPlaylistId: normalizedImportID,
          );
    }
    return roomApi.createRoomMusicPlaylist(roomId: roomId, name: name);
  }

  @override
  Future<PersonalMusicPlaylist> renamePlaylist({
    required String playlistId,
    required String name,
  }) {
    _requireManagePermission();
    return roomApi.renameRoomMusicPlaylist(
      roomId: roomId,
      playlistId: playlistId,
      name: name,
    );
  }

  @override
  Future<void> deletePlaylist(String playlistId) {
    _requireManagePermission();
    return roomApi.deleteRoomMusicPlaylist(
      roomId: roomId,
      playlistId: playlistId,
    );
  }

  @override
  Future<void> pinPlaylists({required List<String> playlistIds}) {
    _requireManagePermission();
    return roomApi.pinRoomMusicPlaylists(
      roomId: roomId,
      playlistIds: playlistIds,
    );
  }

  @override
  Future<void> movePlaylist({
    required String playlistId,
    required String direction,
  }) {
    _requireManagePermission();
    return roomApi.moveRoomMusicPlaylist(
      roomId: roomId,
      playlistId: playlistId,
      direction: direction,
    );
  }

  @override
  Future<PersonalMusicPlaylistItemsPage> loadItems({
    required String playlistId,
    required int page,
    required int pageSize,
    String? keyword,
    String? source,
  }) {
    return roomApi.getRoomMusicPlaylist(
      roomId: roomId,
      playlistId: playlistId,
      page: page,
      pageSize: pageSize,
      keyword: keyword,
      source: source,
    );
  }

  @override
  Future<List<MusicBoxSearchResult>> searchTracks({
    required String keyword,
    required String source,
  }) {
    return searchTracksCallback(keyword: keyword, source: source);
  }

  @override
  Future<PersonalMusicPlaylistItem> addTrack({
    required String playlistId,
    required MusicBoxSearchResult track,
  }) {
    _requireManagePermission();
    return roomApi.addRoomMusicPlaylistItem(
      roomId: roomId,
      playlistId: playlistId,
      track: track,
    );
  }

  @override
  Future<void> deleteItems({
    required String playlistId,
    required List<String> itemIds,
  }) {
    _requireManagePermission();
    return roomApi.deleteRoomMusicPlaylistItems(
      roomId: roomId,
      playlistId: playlistId,
      itemIds: itemIds,
    );
  }

  @override
  Future<void> moveItem({
    required String playlistId,
    required String itemId,
    required String direction,
  }) {
    _requireManagePermission();
    return roomApi.moveRoomMusicPlaylistItem(
      roomId: roomId,
      playlistId: playlistId,
      itemId: itemId,
      direction: direction,
    );
  }

  @override
  Future<void> reorderItems({
    required String playlistId,
    required List<String> itemIds,
  }) {
    _requireManagePermission();
    return roomApi.reorderRoomMusicPlaylistItems(
      roomId: roomId,
      playlistId: playlistId,
      itemIds: itemIds,
    );
  }
}

String? normalizedPersonalPlaylistName(String value) {
  final normalized = value.trim();
  if (normalized.isEmpty || normalized.runes.length > 64) return null;
  return normalized;
}

List<String> uniquePersonalPlaylistItemIds(Iterable<String> values) {
  return _uniqueNonEmptyStrings(values);
}

List<String> uniquePersonalPlaylistIds(Iterable<String> values) {
  return _uniqueNonEmptyStrings(values);
}

List<String> _uniqueNonEmptyStrings(Iterable<String> values) {
  final result = <String>[];
  final seen = <String>{};
  for (final value in values) {
    final normalized = value.trim();
    if (normalized.isEmpty || !seen.add(normalized)) continue;
    result.add(normalized);
  }
  return result;
}

Set<String> toggledPersonalPlaylistSelection(
  Set<String> selected,
  String itemId,
) {
  final next = Set<String>.of(selected);
  if (!next.add(itemId)) next.remove(itemId);
  return next;
}

bool personalPlaylistFilterActive({
  required String keyword,
  required String source,
}) {
  return keyword.trim().isNotEmpty || source.trim().isNotEmpty;
}

bool personalPlaylistListFilterActive({
  required String keyword,
  required String countFilter,
}) {
  return keyword.trim().isNotEmpty || countFilter.trim().isNotEmpty;
}

List<PersonalMusicPlaylist> filteredPersonalMusicPlaylists(
  Iterable<PersonalMusicPlaylist> playlists, {
  required String keyword,
  required String countFilter,
}) {
  final needle = keyword.trim().toLowerCase();
  return [
    for (final playlist in playlists)
      if ((needle.isEmpty || playlist.name.toLowerCase().contains(needle)) &&
          personalPlaylistMatchesCountFilter(playlist.itemCount, countFilter))
        playlist,
  ];
}

bool personalPlaylistMatchesCountFilter(int count, String filter) {
  return switch (filter) {
    personalPlaylistCountAll => true,
    personalPlaylistCountEmpty => count == 0,
    personalPlaylistCount1To10 => count >= 1 && count <= 10,
    personalPlaylistCount11To50 => count >= 11 && count <= 50,
    personalPlaylistCount51To100 => count >= 51 && count <= 100,
    personalPlaylistCountOver100 => count >= 101,
    _ => true,
  };
}

List<String> toggledPersonalPlaylistListSelection(
  List<String> selected,
  String playlistId,
) {
  final next = List<String>.of(selected);
  final index = next.indexOf(playlistId);
  if (index >= 0) {
    next.removeAt(index);
  } else {
    next.add(playlistId);
  }
  return next;
}

bool personalPlaylistAllVisibleSelected({
  required List<String> selectedPlaylistIds,
  required List<PersonalMusicPlaylist> visiblePlaylists,
}) {
  return visiblePlaylists.isNotEmpty &&
      visiblePlaylists.every(
        (playlist) => selectedPlaylistIds.contains(playlist.id),
      );
}

List<String> toggledVisiblePersonalPlaylistSelection({
  required List<String> selectedPlaylistIds,
  required List<PersonalMusicPlaylist> visiblePlaylists,
}) {
  if (visiblePlaylists.isEmpty) return List<String>.of(selectedPlaylistIds);
  final visibleIds = {for (final playlist in visiblePlaylists) playlist.id};
  if (personalPlaylistAllVisibleSelected(
    selectedPlaylistIds: selectedPlaylistIds,
    visiblePlaylists: visiblePlaylists,
  )) {
    return [
      for (final id in selectedPlaylistIds)
        if (!visibleIds.contains(id)) id,
    ];
  }
  final next = List<String>.of(selectedPlaylistIds);
  for (final playlist in visiblePlaylists) {
    if (!next.contains(playlist.id)) next.add(playlist.id);
  }
  return next;
}

Map<String, int> personalPlaylistSelectionNumbers(
  List<String> selectedPlaylistIds,
) {
  return {
    for (final entry in selectedPlaylistIds.asMap().entries)
      entry.value: entry.key + 1,
  };
}

List<String>? personalPlaylistOrderWithSelectionPinnedToFront({
  required List<PersonalMusicPlaylist> playlists,
  required List<String> selectedPlaylistIds,
}) {
  final currentOrder = [for (final playlist in playlists) playlist.id];
  final currentSet = currentOrder.toSet();
  final selectedOrder = [
    for (final playlistId in uniquePersonalPlaylistIds(selectedPlaylistIds))
      if (currentSet.contains(playlistId)) playlistId,
  ];
  if (selectedOrder.isEmpty) return null;
  final selectedSet = selectedOrder.toSet();
  final nextOrder = [
    ...selectedOrder,
    for (final playlistId in currentOrder)
      if (!selectedSet.contains(playlistId)) playlistId,
  ];
  for (var index = 0; index < currentOrder.length; index += 1) {
    if (currentOrder[index] != nextOrder[index]) return nextOrder;
  }
  return null;
}

List<String>? personalPlaylistItemOrderWithSelectionPinnedToFront({
  required Iterable<PersonalMusicPlaylistItem> items,
  required Iterable<String> selectedItemIds,
}) {
  final currentOrder = [for (final item in items) item.id];
  final currentSet = currentOrder.toSet();
  final selectedOrder = [
    for (final itemId in uniquePersonalPlaylistItemIds(selectedItemIds))
      if (currentSet.contains(itemId)) itemId,
  ];
  if (selectedOrder.isEmpty) return null;
  final selectedSet = selectedOrder.toSet();
  final nextOrder = [
    ...selectedOrder,
    for (final itemId in currentOrder)
      if (!selectedSet.contains(itemId)) itemId,
  ];
  for (var index = 0; index < currentOrder.length; index += 1) {
    if (currentOrder[index] != nextOrder[index]) return nextOrder;
  }
  return null;
}

String personalPlaylistArtistsLabel(List<String> artists) {
  final normalized = artists
      .map((artist) => artist.trim())
      .where((artist) => artist.isNotEmpty);
  return normalized.isEmpty ? '未知歌手' : normalized.join('、');
}

String personalPlaylistSourceLabel(String source) {
  return switch (source) {
    'netease' => '网易云',
    'bilibili' => '哔哩哔哩',
    'tencent' => 'QQ音乐',
    _ => source.trim().isEmpty ? '未知来源' : source,
  };
}
