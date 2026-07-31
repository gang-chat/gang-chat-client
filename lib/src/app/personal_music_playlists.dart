import '../protocol/api_client.dart';
import '../protocol/models.dart';

const int personalMusicPlaylistPageSize = 50;

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
