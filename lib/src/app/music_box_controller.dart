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
    final now = DateTime.now().microsecondsSinceEpoch;
    final commandId = 'mbx-$now-${_commandSerial++}';
    return api.controlMusicBox(
      roomId: roomId,
      action: action,
      mode: mode == null ? null : musicBoxPlaybackModeValue(mode),
      commandId: commandId,
      expectedRevision: currentState != null && currentState.hasRevision
          ? currentState.revision
          : null,
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
