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
    final shouldGuardRevision = musicBoxControlRequiresExpectedRevision(action);
    return api.controlMusicBox(
      roomId: roomId,
      action: action,
      mode: mode == null ? null : musicBoxPlaybackModeValue(mode),
      commandId: _nextCommandId(),
      expectedRevision:
          shouldGuardRevision &&
              currentState != null &&
              currentState.hasRevision
          ? currentState.revision
          : null,
    );
  }

  Future<MusicBoxState> playNow({
    required String roomId,
    required MusicBoxQueueItem item,
  }) {
    return api.controlMusicBox(
      roomId: roomId,
      action: 'play_now',
      itemId: item.id,
      commandId: _nextCommandId(),
    );
  }

  Future<MusicBoxState> clearTemporaryQueue({
    required String roomId,
    MusicBoxState? currentState,
  }) {
    return api.controlMusicBox(
      roomId: roomId,
      action: 'clear_temporary_playlist',
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
    return addSearchResultToRoomPlaylist(
      roomId: roomId,
      playlistId: playlistId,
      result: _queueItemAsSearchResult(item),
      durationMs: item.durationMs > 0 ? item.durationMs : null,
    );
  }

  Future<PersonalMusicPlaylistItem> addSearchResultToRoomPlaylist({
    required String roomId,
    required String playlistId,
    required MusicBoxSearchResult result,
    int? durationMs,
  }) {
    return (api as RoomMusicPlaylistApi).addRoomMusicPlaylistItem(
      roomId: roomId,
      playlistId: playlistId,
      track: result,
      durationMs: durationMs,
    );
  }

  Future<PersonalMusicPlaylistItem> addQueueItemToMyPlaylist({
    required String playlistId,
    required MusicBoxQueueItem item,
  }) {
    return addSearchResultToMyPlaylist(
      playlistId: playlistId,
      result: _queueItemAsSearchResult(item),
      durationMs: item.durationMs > 0 ? item.durationMs : null,
    );
  }

  Future<PersonalMusicPlaylistItem> addSearchResultToMyPlaylist({
    required String playlistId,
    required MusicBoxSearchResult result,
    int? durationMs,
  }) {
    return (api as PersonalMusicPlaylistApi).addPersonalMusicPlaylistItem(
      playlistId: playlistId,
      track: result,
      durationMs: durationMs,
    );
  }

  Future<MusicBoxState> activatePlaylist({
    required String roomId,
    required MusicBoxActiveSourceType sourceType,
    String? playlistId,
    bool startPlay = true,
    String? startItemId,
  }) {
    return api.activateMusicBoxPlaylist(
      roomId: roomId,
      sourceType: sourceType,
      playlistId: playlistId,
      startPlay: startPlay,
      startItemId: startItemId,
    );
  }

  Future<PersonalMusicPlaylist> cloneActivePlaylist({
    required String roomId,
    required MusicBoxActiveSource source,
  }) {
    return (api as MusicBoxActivePlaylistCloneApi).cloneActiveMusicBoxPlaylist(
      roomId: roomId,
      playlistId: source.id,
      snapshotId: source.snapshotId,
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

/// Only commands whose result depends on the exact snapshot being edited use
/// optimistic concurrency. Transport commands are serialized by the server and
/// intentionally operate on the authoritative state at execution time; adding
/// a UI snapshot revision to them would turn harmless realtime updates into
/// false conflicts.
bool musicBoxControlRequiresExpectedRevision(String action) {
  return action == 'set_mode';
}

/// Keeps actionable server feedback when a music-box command fails while still
/// providing stable copy for transport and unexpected failures.
String musicBoxControlErrorMessage(Object error, String fallback) {
  if (error case ApiException(:final message) when message.trim().isNotEmpty) {
    return message;
  }
  return fallback;
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

/// Reduces a realtime full snapshot without letting compatibility heartbeats
/// replace actor-specific capabilities every second.
///
/// Room fan-out events deliberately carry conservative capabilities because
/// the same payload is shared by users with different roles. Current servers
/// still emit a full snapshot for each progress tick unless compact-progress
/// mode is enabled. When revision, track and transport state are unchanged,
/// only the position is new; retaining the existing snapshot keeps the
/// personalized permissions stable and avoids a redundant state fetch. A real
/// structural change returns [incoming] unchanged so callers can refresh the
/// actor-specific snapshot.
MusicBoxState? applyMusicBoxRealtimeSnapshot(
  MusicBoxState? current,
  MusicBoxState incoming,
) {
  if (!shouldAcceptMusicBoxSnapshot(current, incoming)) return current;
  if (current == null ||
      !current.hasRevision ||
      !incoming.hasRevision ||
      current.revision != incoming.revision ||
      current.playback.currentItemId != incoming.playback.currentItemId ||
      current.playback.state != incoming.playback.state) {
    return incoming;
  }
  if (current.playback.positionMs == incoming.playback.positionMs) {
    return current;
  }
  return current.copyWith(
    playback: current.playback.copyWith(
      positionMs: incoming.playback.positionMs,
      updatedAt: incoming.playback.updatedAt,
    ),
  );
}

/// Applies a compact progress event only to the exact authoritative snapshot
/// it belongs to. Returning the identical instance makes stale events a cheap
/// no-op for UI callers.
MusicBoxState? applyMusicBoxProgress(
  MusicBoxState? current,
  MusicBoxProgressEvent progress,
) {
  if (current == null ||
      !current.hasRevision ||
      current.revision != progress.revision ||
      current.playback.state != MusicBoxPlaybackState.playing ||
      current.playback.currentItemId.isEmpty ||
      current.playback.currentItemId != progress.currentItemId ||
      progress.positionMs < 0) {
    return current;
  }
  if (current.playback.positionMs == progress.positionMs) return current;
  return current.copyWith(
    playback: current.playback.copyWith(positionMs: progress.positionMs),
  );
}
