import '../protocol/api_client.dart';
import '../protocol/models.dart';

/// Thin HTTP facade over the room music box endpoints. Stateless: every call
/// returns the server's authoritative [MusicBoxState] snapshot (search aside),
/// which callers use to overwrite local state wholesale.
class MusicBoxController {
  const MusicBoxController({required this.api});

  final GangApi api;

  static int _commandSerial = 0;

  Future<MusicBoxState> getState(String roomId) {
    return api.getMusicBoxState(roomId);
  }

  Future<List<MusicBoxSearchResult>> search({
    required String roomId,
    required String keyword,
    String? source,
    int? count,
    int? page,
  }) {
    return api.searchMusicBox(
      roomId: roomId,
      keyword: keyword,
      source: source,
      count: count,
      page: page,
    );
  }

  /// Adds a search hit to the queue, mapping the search shape onto the queue
  /// request body: `name -> title`, and the `artists` array joined into the
  /// single `artist` string the server expects.
  Future<MusicBoxState> queueSearchResult({
    required String roomId,
    required MusicBoxSearchResult result,
    int? durationMs,
  }) {
    return api.queueMusicBoxTrack(
      roomId: roomId,
      trackId: result.trackId,
      title: result.name,
      source: result.source,
      artist: result.artists.join('、'),
      durationMs: durationMs,
    );
  }

  Future<MusicBoxState> removeItem({
    required String roomId,
    required String itemId,
  }) {
    return api.removeMusicBoxItem(roomId: roomId, itemId: itemId);
  }

  Future<MusicBoxState> control({
    required String roomId,
    required String action,
    MusicBoxPlaybackMode? mode,
    MusicBoxState? currentState,
  }) {
    return api.controlMusicBox(
      roomId: roomId,
      action: action,
      mode: mode == null ? null : musicBoxPlaybackModeValue(mode),
      commandId: _nextCommandId(),
      expectedRevision: currentState != null && currentState.hasRevision
          ? currentState.revision
          : null,
    );
  }

  Future<MusicBoxState> playNow({
    required String roomId,
    required MusicBoxQueueItem item,
    MusicBoxState? currentState,
  }) {
    return api.controlMusicBox(
      roomId: roomId,
      action: 'play_now',
      itemId: item.id,
      commandId: _nextCommandId(),
      expectedRevision: currentState != null && currentState.hasRevision
          ? currentState.revision
          : null,
    );
  }

  Future<PersonalMusicPlaylistItem> addQueueItemToRoomPlaylist({
    required String roomId,
    required String playlistId,
    required MusicBoxQueueItem item,
  }) {
    return (api as RoomMusicPlaylistApi).addRoomMusicPlaylistItem(
      roomId: roomId,
      playlistId: playlistId,
      track: _queueItemAsSearchResult(item),
      durationMs: item.durationMs > 0 ? item.durationMs : null,
    );
  }

  Future<PersonalMusicPlaylistItem> addQueueItemToMyPlaylist({
    required String playlistId,
    required MusicBoxQueueItem item,
  }) {
    return (api as PersonalMusicPlaylistApi).addPersonalMusicPlaylistItem(
      playlistId: playlistId,
      track: _queueItemAsSearchResult(item),
      durationMs: item.durationMs > 0 ? item.durationMs : null,
    );
  }

  Future<MusicBoxState> activatePlaylist({
    required String roomId,
    required MusicBoxActiveSourceType sourceType,
    String? playlistId,
    bool startPlay = true,
  }) {
    return api.activateMusicBoxPlaylist(
      roomId: roomId,
      sourceType: sourceType,
      playlistId: playlistId,
      startPlay: startPlay,
    );
  }

  Future<PersonalMusicPlaylistPage> loadRoomPlaylists({
    required String roomId,
    int page = 1,
    int pageSize = 50,
  }) {
    return (api as RoomMusicPlaylistApi).listRoomMusicPlaylists(
      roomId: roomId,
      page: page,
      pageSize: pageSize,
    );
  }

  Future<PersonalMusicPlaylistPage> loadMyPlaylists({
    int page = 1,
    int pageSize = 50,
  }) {
    return (api as PersonalMusicPlaylistApi).listPersonalMusicPlaylists(
      page: page,
      pageSize: pageSize,
    );
  }

  Future<PersonalMusicPlaylistItemsPage> loadRoomPlaylist({
    required String roomId,
    required String playlistId,
    int page = 1,
    int pageSize = 100,
  }) {
    return (api as RoomMusicPlaylistApi).getRoomMusicPlaylist(
      roomId: roomId,
      playlistId: playlistId,
      page: page,
      pageSize: pageSize,
    );
  }

  Future<PersonalMusicPlaylistItemsPage> loadMyPlaylist({
    required String playlistId,
    int page = 1,
    int pageSize = 100,
  }) {
    return (api as PersonalMusicPlaylistApi).getPersonalMusicPlaylist(
      playlistId: playlistId,
      page: page,
      pageSize: pageSize,
    );
  }

  static String _nextCommandId() {
    final now = DateTime.now().microsecondsSinceEpoch;
    return 'mbx-$now-${_commandSerial++}';
  }

  static MusicBoxSearchResult _queueItemAsSearchResult(MusicBoxQueueItem item) {
    final artist = item.artist.trim();
    return MusicBoxSearchResult(
      trackId: item.trackId,
      name: item.title,
      artists: artist.isEmpty ? const [] : [artist],
      source: item.source,
    );
  }
}

/// New servers provide monotonically increasing revisions.  Legacy servers do
/// not, so the old whole-snapshot behaviour remains the compatibility path.
bool shouldAcceptMusicBoxSnapshot(
  MusicBoxState? current,
  MusicBoxState incoming,
) {
  if (current == null || !current.hasRevision || !incoming.hasRevision) {
    return true;
  }
  if (incoming.revision > current.revision) return true;
  if (incoming.revision < current.revision) return false;
  return incoming.playback.currentItemId == current.playback.currentItemId;
}
