// Music box (room queue + playback control) and saved playlists. Personal
// (`/me/music-box/playlists`) and room (`/rooms/:roomId/music-box/playlists`)
// playlists share one route table built by `playlistRoutes`.
import { emitMusicBoxChanged } from '../lib/events.mjs';
import { ApiError, badRequest, binary, conflict, created, notFound, ok } from '../lib/http.mjs';
import { requiredString, stringList } from '../lib/input.mjs';
import { encodeWav } from '../lib/media.mjs';
import { activate, control, enqueue, findCatalogTrack, musicBoxJson, removeItem, searchCatalog } from '../lib/music_box.mjs';
import {
  MAX_PLAYLISTS,
  MAX_PLAYLIST_ITEMS,
  addItem,
  copyItems,
  createPlaylist,
  playlistItemJson,
  playlistJson,
  playlistsFor,
  requirePlaylist,
  touchPlaylist,
  trackFromItemJson,
  uniquePlaylistName,
} from '../lib/playlists.mjs';
import { route } from '../lib/router.mjs';
import { clampInt, foldedIncludes, moveIdsToFront, moveOneStep, paginateByPage } from '../lib/util.mjs';

const personalKey = (userId) => `user:${userId}`;

function memberRoom(c) {
  const room = c.store.requireRoom(c.params.roomId);
  c.store.requireMember(room, c.user);
  return room;
}

function searchParams(c) {
  return {
    source: c.query.get('source') || null,
    count: clampInt(c.query.get('count'), { min: 1, max: 50, fallback: 20 }),
    page: clampInt(c.query.get('page'), { min: 1, max: 100, fallback: 1 }),
  };
}

// Music box ---------------------------------------------------------------------

function musicBoxState(c) {
  return musicBoxJson(c.store, memberRoom(c));
}

function searchMusicBox(c) {
  memberRoom(c);
  return { results: searchCatalog(c.query.get('keyword'), searchParams(c)) };
}

/** Mutates the room's music box, broadcasts `music_box_changed`, returns the new snapshot. */
function mutateMusicBox(c, mutate) {
  const room = memberRoom(c);
  try {
    mutate(room.musicBox, room);
  } catch (error) {
    // A revision conflict carries the authoritative snapshot for the client to apply.
    if (error instanceof ApiError && error.code === 'music_box_revision_conflict') {
      error.extra = { state: musicBoxJson(c.store, room) };
    }
    throw error;
  }
  emitMusicBoxChanged(c.app, room);
  return musicBoxJson(c.store, room);
}

function queueTrack(c) {
  const key = c.req.headers['idempotency-key'];
  const replayKey = key ? `${c.user.id}:music:${key}` : null;
  if (replayKey && c.store.idempotencyKeys.has(replayKey)) return musicBoxJson(c.store, memberRoom(c));
  const trackId = requiredString(c.body, 'track_id');
  const known = findCatalogTrack(c.body.source, trackId);
  const result = mutateMusicBox(c, (box) =>
    enqueue(
      box,
      {
        trackId,
        source: c.body.source || known?.source,
        title: requiredString(c.body, 'title'),
        artist: c.body.artist ?? known?.artists.join(' / ') ?? '',
        durationMs: c.body.duration_ms ?? known?.durationMs,
      },
      c.user.id,
    ),
  );
  if (replayKey) c.store.idempotencyKeys.set(replayKey, trackId);
  return result;
}

function removeQueueItem(c) {
  return mutateMusicBox(c, (box) => removeItem(box, c.params.itemId));
}

function controlMusicBox(c) {
  return mutateMusicBox(c, (box) =>
    control(box, {
      action: String(c.body.action ?? ''),
      itemId: c.body.item_id,
      mode: c.body.mode,
      expectedRevision: typeof c.body.expected_revision === 'number' ? c.body.expected_revision : null,
    }),
  );
}

/** POST /music-box/activate `{ source_type, playlist_id, start_play, start_item_id }`. */
function activatePlaylist(c) {
  return mutateMusicBox(c, (box, room) => {
    const sourceType = String(c.body.source_type ?? 'temporary');
    let playlist = null;
    let owner = null;
    if (sourceType === 'room_playlist') {
      playlist = requirePlaylist(c.store, `room:${room.id}`, String(c.body.playlist_id ?? ''));
    } else if (sourceType === 'user_playlist') {
      playlist = requirePlaylist(c.store, personalKey(c.user.id), String(c.body.playlist_id ?? ''));
      owner = c.user;
    } else if (sourceType !== 'temporary') {
      throw badRequest(`unknown source_type: ${sourceType}`, 'validation_failed');
    }
    activate(box, {
      sourceType,
      playlist,
      owner,
      startPlay: c.body.start_play !== false,
      startItemId: c.body.start_item_id ?? null,
    });
  });
}

function cloneActivePlaylist(c) {
  const room = memberRoom(c);
  const source = room.musicBox.activeSource;
  if (source.type === 'temporary' || source.id !== c.body.playlist_id || source.snapshotId !== c.body.snapshot_id) {
    throw notFound('playlist not found');
  }
  const ownerKey = personalKey(c.user.id);
  const items = room.musicBox.playlistQueue.map((item) => ({ ...item, artists: item.artist ? item.artist.split(' / ') : [] }));
  const playlist = createPlaylist(c.store, ownerKey, { name: uniquePlaylistName(c.store, ownerKey, source.name), items });
  return created({ playlist: playlistJson(playlist) });
}

// Saved playlists -----------------------------------------------------------------

function pageJson(c, playlists) {
  const page = clampInt(c.query.get('page'), { min: 1, max: 1000, fallback: 1 });
  const pageSize = clampInt(c.query.get('page_size'), { min: 1, max: 200, fallback: 50 });
  const { page: items, pagination } = paginateByPage(playlists, { page, pageSize });
  return {
    playlists: items.map(playlistJson),
    pagination,
    limits: { max_playlists: MAX_PLAYLISTS, max_playlist_items: MAX_PLAYLIST_ITEMS },
  };
}

function trackFromBody(body) {
  return {
    trackId: requiredString(body, 'track_id'),
    source: body.source || 'netease',
    title: requiredString(body, 'title'),
    artists: Array.isArray(body.artists) ? body.artists.map(String) : [],
    durationMs: typeof body.duration_ms === 'number' ? body.duration_ms : 0,
  };
}

/**
 * The playlist endpoint family under `base`. `ownerKeyOf(c)` performs access
 * checks and returns the owner key (`user:<id>` / `room:<id>`).
 */
function playlistRoutes(base, ownerKeyOf) {
  const load = (c) => requirePlaylist(c.store, ownerKeyOf(c), c.params.playlistId);

  const list = (c) => pageJson(c, playlistsFor(c.store, ownerKeyOf(c)));

  const create = (c) => {
    const ownerKey = ownerKeyOf(c);
    const name = requiredString(c.body, 'name', { max: 40 });
    const importId = c.body.import_playlist_id;
    const items = importId ? requirePlaylist(c.store, personalKey(c.user.id), importId).items : [];
    return created({ playlist: playlistJson(createPlaylist(c.store, ownerKey, { name, items })) });
  };

  const merge = (c) => {
    const ownerKey = ownerKeyOf(c);
    const sources = stringList(c.body, 'playlist_ids').map((id) => requirePlaylist(c.store, ownerKey, id));
    if (sources.length < 2) throw badRequest('select at least two playlists', 'validation_failed');
    const name = requiredString(c.body, 'name', { max: 40 });
    const sourceIds = new Set(sources.map((playlist) => playlist.id));
    if (playlistsFor(c.store, ownerKey).some((playlist) => !sourceIds.has(playlist.id) && playlist.name === name)) {
      throw conflict('playlist name conflict', 'playlist_name_conflict');
    }
    const all = sources.flatMap((playlist) => playlist.items);
    for (const source of sources) c.store.playlists.delete(source.id);
    const target = createPlaylist(c.store, ownerKey, { name });
    const added = copyItems(target, all);
    return {
      playlist: playlistJson(target),
      merge: {
        source_item_count: all.length,
        unique_item_count: added.length,
        duplicate_count: all.length - added.length,
        item_count: target.items.length,
        omitted_count: 0,
        deleted_playlist_count: sources.length,
        retained_playlist_count: 0,
        consumed_source_item_count: all.length,
        truncated: false,
      },
    };
  };

  const reorderPlaylists = (c) => {
    const ownerKey = ownerKeyOf(c);
    let ordered = playlistsFor(c.store, ownerKey);
    if (Array.isArray(c.body.playlist_ids)) ordered = moveIdsToFront(ordered, stringList(c.body, 'playlist_ids'));
    else ordered = moveOneStep(ordered, String(c.body.playlist_id ?? ''), c.body.direction);
    ordered.forEach((playlist, index) => {
      playlist.position = index;
    });
    return ok();
  };

  const get = (c) => {
    const playlist = load(c);
    const keyword = c.query.get('keyword');
    const source = c.query.get('source');
    const items = playlist.items.filter(
      (item) =>
        (!source || item.source === source) &&
        (!keyword || foldedIncludes(item.title, keyword) || item.artists.some((artist) => foldedIncludes(artist, keyword))),
    );
    const page = clampInt(c.query.get('page'), { min: 1, max: 1000, fallback: 1 });
    const pageSize = clampInt(c.query.get('page_size'), { min: 1, max: 500, fallback: 50 });
    const { page: pageItems, pagination } = paginateByPage(items, { page, pageSize });
    return { playlist: playlistJson(playlist), items: pageItems.map((item) => playlistItemJson(playlist, item)), pagination };
  };

  const rename = (c) => {
    const playlist = load(c);
    playlist.name = requiredString(c.body, 'name', { max: 40 });
    touchPlaylist(playlist);
    return { playlist: playlistJson(playlist) };
  };

  const remove = (c) => {
    c.store.playlists.delete(load(c).id);
    return ok();
  };

  const addTrack = (c) => {
    const playlist = load(c);
    return created({ item: playlistItemJson(playlist, addItem(playlist, trackFromBody(c.body))) });
  };

  const removeItems = (c, ids) => {
    const playlist = load(c);
    const before = playlist.items.length;
    playlist.items = playlist.items.filter((item) => !ids.includes(item.id));
    if (playlist.items.length !== before) touchPlaylist(playlist);
    return ok();
  };

  const reorderItems = (c) => {
    const playlist = load(c);
    playlist.items = Array.isArray(c.body.item_ids)
      ? moveIdsToFront(playlist.items, stringList(c.body, 'item_ids'))
      : moveOneStep(playlist.items, String(c.body.item_id ?? ''), c.body.direction);
    touchPlaylist(playlist);
    return ok();
  };

  const batchAdd = (c) => {
    const target = load(c);
    const sourceId = String(c.body.source_playlist_id ?? '');
    const source = c.store.playlists.get(sourceId);
    if (!source || (source.ownerKey !== target.ownerKey && source.ownerKey !== personalKey(c.user.id))) {
      throw notFound('playlist not found');
    }
    const ids = stringList(c.body, 'item_ids');
    const selected = source.items.filter((item) => ids.includes(item.id));
    const added = copyItems(target, selected);
    return {
      playlist: playlistJson(target),
      batch_add: {
        selected_item_count: ids.length,
        unique_item_count: selected.length,
        duplicate_count: ids.length - selected.length,
        already_present_count: selected.length - added.length,
        added_item_count: added.length,
        omitted_count: 0,
        truncated: false,
      },
    };
  };

  return [
    route('GET', base, list),
    route('POST', base, create),
    route('POST', `${base}/merge`, merge),
    route('PATCH', `${base}/order`, reorderPlaylists),
    route('GET', `${base}/:playlistId`, get),
    route('PATCH', `${base}/:playlistId`, rename),
    route('DELETE', `${base}/:playlistId`, remove),
    route('POST', `${base}/:playlistId/items`, addTrack),
    route('DELETE', `${base}/:playlistId/items`, (c) => removeItems(c, stringList(c.body, 'item_ids'))),
    route('DELETE', `${base}/:playlistId/items/:itemId`, (c) => removeItems(c, [c.params.itemId])),
    route('PATCH', `${base}/:playlistId/items/order`, reorderItems),
    route('POST', `${base}/:playlistId/items/batch-add`, batchAdd),
  ];
}

function cloneRoomPlaylistToMe(c) {
  const room = memberRoom(c);
  const source = requirePlaylist(c.store, `room:${room.id}`, c.params.playlistId);
  const ownerKey = personalKey(c.user.id);
  const playlist = createPlaylist(c.store, ownerKey, { name: uniquePlaylistName(c.store, ownerKey, source.name), items: source.items });
  return created({ playlist: playlistJson(playlist) });
}

/** Clones the immutable playlist snapshot embedded in a `type: "playlist"` message. */
function cloneSharedPlaylistToMe(c) {
  const room = memberRoom(c);
  const message = c.store.findMessage(room, c.params.messageId);
  const snapshot = message.attachments.find((attachment) => attachment.type === 'playlist')?.playlist;
  if (!snapshot || c.store.isRemoved(message)) throw notFound('playlist not found');
  const ownerKey = personalKey(c.user.id);
  const playlist = createPlaylist(c.store, ownerKey, {
    name: uniquePlaylistName(c.store, ownerKey, snapshot.name),
    items: (snapshot.items ?? []).map(trackFromItemJson),
  });
  return created({ playlist: playlistJson(playlist) });
}

function searchPersonal(c) {
  return { results: searchCatalog(c.query.get('keyword'), searchParams(c)) };
}

/** A short synthesized clip standing in for the real track preview. */
function previewTrack(c) {
  const trackId = requiredString(c.body, 'track_id');
  const known = findCatalogTrack(c.body.source, trackId);
  const pitch = 196 + (trackId.length % 6) * 24;
  return binary({
    contentType: 'audio/wav',
    body: encodeWav({ durationMs: 15_000, sampleRate: 22_050, pitch, style: 'music' }),
    filename: `${known?.title ?? trackId}.wav`,
  });
}

export const musicRoutes = [
  route('GET', '/rooms/:roomId/music-box/state', musicBoxState),
  route('GET', '/rooms/:roomId/music-box/search', searchMusicBox),
  route('POST', '/rooms/:roomId/music-box/queue', queueTrack),
  route('DELETE', '/rooms/:roomId/music-box/queue/:itemId', removeQueueItem),
  route('POST', '/rooms/:roomId/music-box/control', controlMusicBox),
  route('POST', '/rooms/:roomId/music-box/activate', activatePlaylist),
  route('POST', '/rooms/:roomId/music-box/active-playlist/clone', cloneActivePlaylist),
  route('POST', '/rooms/:roomId/music-box/playlists/:playlistId/clone-to-me', cloneRoomPlaylistToMe),
  route('POST', '/rooms/:roomId/messages/:messageId/playlist/clone-to-me', cloneSharedPlaylistToMe),
  route('GET', '/me/music-box/search', searchPersonal),
  route('POST', '/me/music-box/preview', previewTrack),
  ...playlistRoutes('/me/music-box/playlists', (c) => personalKey(c.user.id)),
  ...playlistRoutes('/rooms/:roomId/music-box/playlists', (c) => `room:${memberRoom(c).id}`),
];
