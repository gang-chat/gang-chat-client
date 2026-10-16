// Saved music playlists, shared by the personal (`/me/music-box/playlists`)
// and room (`/rooms/:roomId/music-box/playlists`) endpoints. A playlist's
// `ownerKey` is `user:<id>` or `room:<id>`.
import { conflict, notFound } from './http.mjs';
import { iso, nextId } from './util.mjs';

export const MAX_PLAYLISTS = 50;
export const MAX_PLAYLIST_ITEMS = 500;

export function playlistsFor(store, ownerKey) {
  return [...store.playlists.values()].filter((playlist) => playlist.ownerKey === ownerKey).sort((a, b) => a.position - b.position);
}

export function requirePlaylist(store, ownerKey, playlistId) {
  const playlist = store.playlists.get(playlistId);
  if (!playlist || playlist.ownerKey !== ownerKey) throw notFound('playlist not found');
  return playlist;
}

export function createPlaylist(store, ownerKey, { name, description = '', items = [], createdAt = Date.now() }) {
  const existing = playlistsFor(store, ownerKey);
  if (existing.length >= MAX_PLAYLISTS) throw conflict('playlist limit reached', 'playlist_limit_reached');
  if (existing.some((playlist) => playlist.name === name)) throw conflict('playlist name conflict', 'playlist_name_conflict');
  const playlist = {
    id: nextId('pl'),
    ownerKey,
    name,
    description,
    revision: 1,
    position: existing.length ? existing[existing.length - 1].position + 1 : 0,
    createdAt,
    updatedAt: createdAt,
    items: [],
  };
  store.playlists.set(playlist.id, playlist);
  copyItems(playlist, items);
  return playlist;
}

/** A free name: `name`, then `name (2)`, `name (3)`, ... */
export function uniquePlaylistName(store, ownerKey, name) {
  const taken = new Set(playlistsFor(store, ownerKey).map((playlist) => playlist.name));
  if (!taken.has(name)) return name;
  for (let n = 2; ; n++) if (!taken.has(`${name} (${n})`)) return `${name} (${n})`;
}

export function touchPlaylist(playlist) {
  playlist.revision += 1;
  playlist.updatedAt = Date.now();
}

const sameTrack = (a, b) => a.trackId === b.trackId && a.source === b.source;

/** Adds a track (`{ trackId, source, title, artists, durationMs }`); throws on duplicates. */
export function addItem(playlist, track, createdAt = Date.now()) {
  if (playlist.items.some((item) => sameTrack(item, track))) {
    throw conflict('playlist item already exists', 'playlist_item_already_exists');
  }
  if (playlist.items.length >= MAX_PLAYLIST_ITEMS) throw conflict('playlist item limit reached', 'playlist_item_limit_reached');
  const item = {
    id: nextId('pli'),
    trackId: track.trackId,
    source: track.source || 'netease',
    title: track.title,
    artists: track.artists ?? [],
    durationMs: track.durationMs ?? 0,
    createdAt,
  };
  playlist.items.push(item);
  touchPlaylist(playlist);
  return item;
}

/** Copies `items` into `target`, skipping tracks it already has. Returns the added items. */
export function copyItems(target, items) {
  const added = [];
  for (const item of items) {
    if (target.items.some((existing) => sameTrack(existing, item))) continue;
    if (target.items.length >= MAX_PLAYLIST_ITEMS) break;
    added.push(addItem(target, item));
  }
  return added;
}

/** `PersonalMusicPlaylist.fromJson`. */
export function playlistJson(playlist) {
  return {
    id: playlist.id,
    name: playlist.name,
    description: playlist.description,
    revision: playlist.revision,
    item_count: playlist.items.length,
    created_at: iso(playlist.createdAt),
    updated_at: iso(playlist.updatedAt),
  };
}

/** `PersonalMusicPlaylistItem.fromJson`. */
export function playlistItemJson(playlist, item) {
  return {
    id: item.id,
    playlist_id: playlist.id,
    track_id: item.trackId,
    source: item.source,
    title: item.title,
    artists: item.artists,
    duration_ms: item.durationMs,
    sort_order: playlist.items.indexOf(item),
    created_at: iso(item.createdAt),
  };
}

/** `SharedMusicPlaylist.fromJson`: the immutable snapshot embedded in a chat message. */
export function sharedPlaylistJson(store, playlist, creatorId) {
  const creator = store.users.get(creatorId);
  return {
    id: playlist.id,
    name: playlist.name,
    description: playlist.description,
    item_count: playlist.items.length,
    created_at: iso(playlist.createdAt),
    creator: creator ? store.userSummary(creator) : null,
    items: playlist.items.map((item) => playlistItemJson(playlist, item)),
  };
}

/** Converts a stored playlist-item JSON (as embedded in messages) back to a track. */
export function trackFromItemJson(json) {
  return {
    trackId: json.track_id,
    source: json.source,
    title: json.title ?? json.name ?? '',
    artists: json.artists ?? [],
    durationMs: json.duration_ms ?? 0,
  };
}
