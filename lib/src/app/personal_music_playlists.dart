import '../protocol/api_client.dart';
import '../protocol/models.dart';

const int personalMusicPlaylistPageSize = 50;
const String personalPlaylistCountAll = '';
const String personalPlaylistCountEmpty = 'empty';
const String personalPlaylistCount1To10 = '1-10';
const String personalPlaylistCount11To50 = '11-50';
const String personalPlaylistCount51To100 = '51-100';
const String personalPlaylistCountOver100 = '101+';

class PersonalPlaylistFilterDraft {
  const PersonalPlaylistFilterDraft({
    required this.keyword,
    required this.countFilter,
  });

  final String keyword;
  final String countFilter;
}

class PersonalMusicPlaylistsController {
  const PersonalMusicPlaylistsController(this.api);

  final PersonalMusicPlaylistApi? api;

  bool get available => api != null;

  Future<PersonalMusicPlaylistPage?> loadPlaylists() {
    final client = api;
    if (client == null) return Future.value();
    return client.listPersonalMusicPlaylists(
      page: 1,
      pageSize: personalMusicPlaylistPageSize,
    );
  }

  Future<PersonalMusicPlaylist?> createPlaylist(String name) {
    final normalized = normalizedPersonalPlaylistName(name);
    if (normalized == null) return Future.value();
    final client = api;
    if (client == null) return Future.value();
    return client.createPersonalMusicPlaylist(name: normalized);
  }

  Future<void> deletePlaylist(String playlistId) {
    return api?.deletePersonalMusicPlaylist(playlistId) ?? Future.value();
  }

  Future<PersonalMusicPlaylistItemsPage?> loadItems({
    required String playlistId,
    int page = 1,
    String keyword = '',
    String source = '',
  }) {
    final client = api;
    if (client == null) return Future.value();
    return client.getPersonalMusicPlaylist(
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
    final client = api;
    if (client == null) return Future.value();
    return client.searchPersonalMusicPlaylistTracks(
      keyword: normalizedKeyword,
      source: source,
      count: 20,
      page: 1,
    );
  }

  Future<PersonalMusicPlaylistItem?> addTrack({
    required String playlistId,
    required MusicBoxSearchResult track,
  }) {
    final client = api;
    if (client == null) return Future.value();
    return client.addPersonalMusicPlaylistItem(
      playlistId: playlistId,
      track: track,
    );
  }

  Future<void> deleteItems({
    required String playlistId,
    required List<String> itemIds,
  }) {
    final ids = uniquePersonalPlaylistItemIds(itemIds);
    if (ids.isEmpty) return Future.value();
    return api?.deletePersonalMusicPlaylistItems(
          playlistId: playlistId,
          itemIds: ids,
        ) ??
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
    return api?.movePersonalMusicPlaylistItem(
          playlistId: playlistId,
          itemId: itemId,
          direction: delta < 0 ? 'up' : 'down',
        ) ??
        Future.value();
  }
}

String? normalizedPersonalPlaylistName(String value) {
  final normalized = value.trim();
  if (normalized.isEmpty || normalized.runes.length > 64) return null;
  return normalized;
}

List<String> uniquePersonalPlaylistItemIds(Iterable<String> values) {
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
