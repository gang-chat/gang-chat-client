import { ApiException } from '../protocol/api_client';
import type {
  GangApi,
  MusicBoxActivePlaylistCloneApi,
  PersonalMusicPlaylistApi,
  RoomMusicPlaylistApi,
} from '../protocol/api_client';
import { MusicBoxPlaybackState, MusicBoxSearchResult, musicBoxPlaybackModeValue } from '../protocol/models';
import type {
  MusicBoxActiveSource,
  MusicBoxActiveSourceType,
  MusicBoxPlaybackMode,
  MusicBoxProgressEvent,
  MusicBoxQueueItem,
  MusicBoxState,
  PersonalMusicPlaylist,
  PersonalMusicPlaylistItem,
  PersonalMusicPlaylistItemsPage,
  PersonalMusicPlaylistPage,
} from '../protocol/models';

/**
 * Thin HTTP facade over the room music box endpoints. Stateless: every call
 * returns the server's authoritative [MusicBoxState] snapshot (search aside),
 * which callers use to overwrite local state wholesale.
 */
export class MusicBoxController {
  constructor(opts: { api: GangApi }) {
    this.api = opts.api;
  }

  readonly api: GangApi;

  private static _commandSerial = 0;

  getState(roomId: string): Promise<MusicBoxState> {
    return this.api.getMusicBoxState(roomId);
  }

  search(opts: {
    roomId: string;
    keyword: string;
    source?: string | null;
    count?: number | null;
    page?: number | null;
  }): Promise<MusicBoxSearchResult[]> {
    return this.api.searchMusicBox({
      roomId: opts.roomId,
      keyword: opts.keyword,
      source: opts.source ?? undefined,
      count: opts.count ?? undefined,
      page: opts.page ?? undefined,
    });
  }

  /**
   * Adds a search hit to the queue, mapping the search shape onto the queue
   * request body: `name -> title`, and the `artists` array joined into the
   * single `artist` string the server expects.
   */
  queueSearchResult(opts: {
    roomId: string;
    result: MusicBoxSearchResult;
    durationMs?: number | null;
  }): Promise<MusicBoxState> {
    const { roomId, result } = opts;
    return this.api.queueMusicBoxTrack({
      roomId,
      trackId: result.trackId,
      title: result.name,
      source: result.source,
      artist: result.artists.join('、'),
      durationMs: opts.durationMs ?? undefined,
    });
  }

  removeItem(opts: { roomId: string; itemId: string }): Promise<MusicBoxState> {
    return this.api.removeMusicBoxItem({ roomId: opts.roomId, itemId: opts.itemId });
  }

  control(opts: {
    roomId: string;
    action: string;
    mode?: MusicBoxPlaybackMode | null;
    currentState?: MusicBoxState | null;
  }): Promise<MusicBoxState> {
    const { roomId, action, mode, currentState } = opts;
    const shouldGuardRevision = musicBoxControlRequiresExpectedRevision(action);
    return this.api.controlMusicBox({
      roomId,
      action,
      mode: mode == null ? undefined : musicBoxPlaybackModeValue(mode),
      commandId: MusicBoxController._nextCommandId(),
      expectedRevision:
        shouldGuardRevision && currentState != null && currentState.hasRevision ? currentState.revision : undefined,
    });
  }

  playNow(opts: { roomId: string; item: MusicBoxQueueItem }): Promise<MusicBoxState> {
    return this.api.controlMusicBox({
      roomId: opts.roomId,
      action: 'play_now',
      itemId: opts.item.id,
      commandId: MusicBoxController._nextCommandId(),
    });
  }

  clearTemporaryQueue(opts: { roomId: string; currentState?: MusicBoxState | null }): Promise<MusicBoxState> {
    const { roomId, currentState } = opts;
    return this.api.controlMusicBox({
      roomId,
      action: 'clear_temporary_playlist',
      commandId: MusicBoxController._nextCommandId(),
      expectedRevision: currentState != null && currentState.hasRevision ? currentState.revision : undefined,
    });
  }

  addQueueItemToRoomPlaylist(opts: {
    roomId: string;
    playlistId: string;
    item: MusicBoxQueueItem;
  }): Promise<PersonalMusicPlaylistItem> {
    const { item } = opts;
    return this.addSearchResultToRoomPlaylist({
      roomId: opts.roomId,
      playlistId: opts.playlistId,
      result: MusicBoxController._queueItemAsSearchResult(item),
      durationMs: item.durationMs > 0 ? item.durationMs : null,
    });
  }

  addSearchResultToRoomPlaylist(opts: {
    roomId: string;
    playlistId: string;
    result: MusicBoxSearchResult;
    durationMs?: number | null;
  }): Promise<PersonalMusicPlaylistItem> {
    // Dart casts `api as RoomMusicPlaylistApi`; the TS interfaces are
    // structural, so the cast goes through `unknown`.
    return (this.api as unknown as RoomMusicPlaylistApi).addRoomMusicPlaylistItem({
      roomId: opts.roomId,
      playlistId: opts.playlistId,
      track: opts.result,
      durationMs: opts.durationMs ?? undefined,
    });
  }

  addQueueItemToMyPlaylist(opts: { playlistId: string; item: MusicBoxQueueItem }): Promise<PersonalMusicPlaylistItem> {
    const { item } = opts;
    return this.addSearchResultToMyPlaylist({
      playlistId: opts.playlistId,
      result: MusicBoxController._queueItemAsSearchResult(item),
      durationMs: item.durationMs > 0 ? item.durationMs : null,
    });
  }

  addSearchResultToMyPlaylist(opts: {
    playlistId: string;
    result: MusicBoxSearchResult;
    durationMs?: number | null;
  }): Promise<PersonalMusicPlaylistItem> {
    return (this.api as unknown as PersonalMusicPlaylistApi).addPersonalMusicPlaylistItem({
      playlistId: opts.playlistId,
      track: opts.result,
      durationMs: opts.durationMs ?? undefined,
    });
  }

  activatePlaylist(opts: {
    roomId: string;
    sourceType: MusicBoxActiveSourceType;
    playlistId?: string | null;
    startPlay?: boolean;
    startItemId?: string | null;
  }): Promise<MusicBoxState> {
    const { roomId, sourceType, playlistId, startPlay = true, startItemId } = opts;
    return this.api.activateMusicBoxPlaylist({
      roomId,
      sourceType,
      playlistId: playlistId ?? undefined,
      startPlay,
      startItemId: startItemId ?? undefined,
    });
  }

  cloneActivePlaylist(opts: { roomId: string; source: MusicBoxActiveSource }): Promise<PersonalMusicPlaylist> {
    return (this.api as unknown as MusicBoxActivePlaylistCloneApi).cloneActiveMusicBoxPlaylist({
      roomId: opts.roomId,
      playlistId: opts.source.id,
      snapshotId: opts.source.snapshotId,
    });
  }

  loadRoomPlaylists(opts: { roomId: string; page?: number; pageSize?: number }): Promise<PersonalMusicPlaylistPage> {
    const { roomId, page = 1, pageSize = 50 } = opts;
    return (this.api as unknown as RoomMusicPlaylistApi).listRoomMusicPlaylists({ roomId, page, pageSize });
  }

  loadMyPlaylists(opts: { page?: number; pageSize?: number } = {}): Promise<PersonalMusicPlaylistPage> {
    const { page = 1, pageSize = 50 } = opts;
    return (this.api as unknown as PersonalMusicPlaylistApi).listPersonalMusicPlaylists({ page, pageSize });
  }

  loadRoomPlaylist(opts: {
    roomId: string;
    playlistId: string;
    page?: number;
    pageSize?: number;
  }): Promise<PersonalMusicPlaylistItemsPage> {
    const { roomId, playlistId, page = 1, pageSize = 100 } = opts;
    return (this.api as unknown as RoomMusicPlaylistApi).getRoomMusicPlaylist({ roomId, playlistId, page, pageSize });
  }

  loadMyPlaylist(opts: { playlistId: string; page?: number; pageSize?: number }): Promise<PersonalMusicPlaylistItemsPage> {
    const { playlistId, page = 1, pageSize = 100 } = opts;
    return (this.api as unknown as PersonalMusicPlaylistApi).getPersonalMusicPlaylist({ playlistId, page, pageSize });
  }

  private static _nextCommandId(): string {
    // PORT: Dart used microsecondsSinceEpoch; JS clocks expose milliseconds.
    // The per-process serial keeps ids unique either way.
    const now = Date.now() * 1000;
    return `mbx-${now}-${MusicBoxController._commandSerial++}`;
  }

  private static _queueItemAsSearchResult(item: MusicBoxQueueItem): MusicBoxSearchResult {
    const artist = item.artist.trim();
    return new MusicBoxSearchResult({
      trackId: item.trackId,
      name: item.title,
      artists: artist.length === 0 ? [] : [artist],
      source: item.source,
    });
  }
}

/**
 * Only commands whose result depends on the exact snapshot being edited use
 * optimistic concurrency. Transport commands are serialized by the server and
 * intentionally operate on the authoritative state at execution time; adding
 * a UI snapshot revision to them would turn harmless realtime updates into
 * false conflicts.
 */
export function musicBoxControlRequiresExpectedRevision(action: string): boolean {
  return action === 'set_mode';
}

/**
 * Keeps actionable server feedback when a music-box command fails while still
 * providing stable copy for transport and unexpected failures.
 */
export function musicBoxControlErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiException && error.message.trim().length > 0) {
    return error.message;
  }
  return fallback;
}

/**
 * New servers provide monotonically increasing revisions.  Legacy servers do
 * not, so the old whole-snapshot behaviour remains the compatibility path.
 */
export function shouldAcceptMusicBoxSnapshot(current: MusicBoxState | null, incoming: MusicBoxState): boolean {
  if (current == null || !current.hasRevision || !incoming.hasRevision) {
    return true;
  }
  if (incoming.revision > current.revision) return true;
  if (incoming.revision < current.revision) return false;
  return incoming.playback.currentItemId === current.playback.currentItemId;
}

/**
 * Reduces a realtime full snapshot without letting compatibility heartbeats
 * replace actor-specific capabilities every second.
 *
 * Room fan-out events deliberately carry conservative capabilities because
 * the same payload is shared by users with different roles. Current servers
 * still emit a full snapshot for each progress tick unless compact-progress
 * mode is enabled. When revision, track and transport state are unchanged,
 * only the position is new; retaining the existing snapshot keeps the
 * personalized permissions stable and avoids a redundant state fetch. A real
 * structural change returns [incoming] unchanged so callers can refresh the
 * actor-specific snapshot.
 */
export function applyMusicBoxRealtimeSnapshot(
  current: MusicBoxState | null,
  incoming: MusicBoxState,
): MusicBoxState | null {
  if (!shouldAcceptMusicBoxSnapshot(current, incoming)) return current;
  if (
    current == null ||
    !current.hasRevision ||
    !incoming.hasRevision ||
    current.revision !== incoming.revision ||
    current.playback.currentItemId !== incoming.playback.currentItemId ||
    current.playback.state !== incoming.playback.state
  ) {
    return incoming;
  }
  if (current.playback.positionMs === incoming.playback.positionMs) {
    return current;
  }
  return current.copyWith({
    playback: current.playback.copyWith({
      positionMs: incoming.playback.positionMs,
      // Dart's `copyWith(updatedAt: null)` keeps the existing value.
      updatedAt: incoming.playback.updatedAt ?? undefined,
    }),
  });
}

/**
 * Applies a compact progress event only to the exact authoritative snapshot
 * it belongs to. Returning the identical instance makes stale events a cheap
 * no-op for UI callers.
 */
export function applyMusicBoxProgress(
  current: MusicBoxState | null,
  progress: MusicBoxProgressEvent,
): MusicBoxState | null {
  if (
    current == null ||
    !current.hasRevision ||
    current.revision !== progress.revision ||
    current.playback.state !== MusicBoxPlaybackState.playing ||
    current.playback.currentItemId.length === 0 ||
    current.playback.currentItemId !== progress.currentItemId ||
    progress.positionMs < 0
  ) {
    return current;
  }
  if (current.playback.positionMs === progress.positionMs) return current;
  return current.copyWith({
    playback: current.playback.copyWith({ positionMs: progress.positionMs }),
  });
}
