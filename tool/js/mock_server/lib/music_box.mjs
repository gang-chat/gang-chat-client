// Room music box: a fake track catalog, the playback state machine, and the
// `MusicBoxState.fromJson` serializer. No audio is streamed (the real server
// publishes it through LiveKit); positions advance on the server clock and are
// pushed as `music_box_progress` events.
import { ApiError, badRequest, conflict, notFound } from './http.mjs';
import { iso, nextId } from './util.mjs';

export const PLAYBACK_MODES = ['sequential', 'repeat_one', 'repeat_all', 'shuffle'];

const track = (source, trackId, title, artists, seconds) => ({ source, trackId, title, artists, durationMs: seconds * 1000 });

/** Search corpus for `/music-box/search`, also used to seed queues and playlists. */
export const TRACK_CATALOG = [
  track('netease', 'ne-186016', '晴天', ['周杰伦'], 269),
  track('netease', 'ne-185811', '稻香', ['周杰伦'], 223),
  track('netease', 'ne-25906124', '平凡之路', ['朴树'], 301),
  track('netease', 'ne-28012031', '夜空中最亮的星', ['逃跑计划'], 252),
  track('netease', 'ne-347230', '海阔天空', ['Beyond'], 326),
  track('netease', 'ne-5257138', '后来', ['刘若英'], 341),
  track('netease', 'ne-1824020871', '孤勇者', ['陈奕迅'], 256),
  track('netease', 'ne-65538', '十年', ['陈奕迅'], 205),
  track('netease', 'ne-1293886117', '起风了', ['买辣椒也用券'], 325),
  track('netease', 'ne-1901371647', 'Shape of You', ['Ed Sheeran'], 234),
  track('netease', 'ne-1859245776', 'Blinding Lights', ['The Weeknd'], 200),
  track('netease', 'ne-1330348068', 'Lemon', ['米津玄師'], 255),
  track('netease', 'ne-4153366', 'Yesterday Once More', ['Carpenters'], 238),
  track('netease', 'ne-1306507078', 'Shallow', ['Lady Gaga', 'Bradley Cooper'], 215),
  track('tencent', 'qq-001JZx7l2ifJ4U', '七里香', ['周杰伦'], 299),
  track('tencent', 'qq-0039MnYb0qxYhV', '光年之外', ['G.E.M. 邓紫棋'], 235),
  track('tencent', 'qq-002E3MtF0IAMMY', '漠河舞厅', ['柳爽'], 296),
  track('tencent', 'qq-004emQMs09Z1lz', 'Viva La Vida', ['Coldplay'], 242),
  track('tencent', 'qq-000xdZuV2LcQ19', '小幸运', ['田馥甄'], 266),
  track('bilibili', 'BV1uv411q7Mv', '【钢琴】千与千寻 - 那个夏天', ['Animenz'], 244),
  track('bilibili', 'BV1Xx411c7cH', 'Lo-fi 学习电台 · 一小时精选', ['ChilledCow'], 3600),
  track('bilibili', 'BV1GJ411x7h7', '【8-bit】超级玛丽主题曲 Remix', ['FamiTracker'], 142),
];

export function catalogResultJson(entry) {
  return { track_id: entry.trackId, name: entry.title, artists: entry.artists, source: entry.source, duration_ms: entry.durationMs };
}

/** Keyword search over the catalog; unknown keywords get synthesized hits so search is never empty. */
export function searchCatalog(keyword, { source = null, count = 20, page = 1 } = {}) {
  const needle = String(keyword ?? '').trim().toLowerCase();
  let hits = TRACK_CATALOG.filter(
    (entry) =>
      (!source || entry.source === source) &&
      (!needle || entry.title.toLowerCase().includes(needle) || entry.artists.some((a) => a.toLowerCase().includes(needle))),
  );
  if (hits.length === 0 && needle) {
    const src = source ?? 'netease';
    hits = ['', ' (Live)', ' (Remix)', ' - 钢琴版', ' (伴奏)'].map((suffix, index) =>
      track(src, `${src}-mock-${encodeURIComponent(needle)}-${index}`, `${keyword.trim()}${suffix}`, ['Mock Artist'], 180 + index * 17),
    );
  }
  return hits.slice((page - 1) * count, page * count).map(catalogResultJson);
}

export function findCatalogTrack(source, trackId) {
  return TRACK_CATALOG.find((entry) => entry.trackId === trackId && (!source || entry.source === source)) ?? null;
}

export function createMusicBox() {
  return {
    enabled: true,
    revision: 1,
    mode: 'sequential',
    state: 'stopped',
    currentItemId: '',
    positionMs: 0,
    startedAt: 0,
    volume: 80,
    temporaryQueue: [],
    playlistQueue: [],
    activeSource: { type: 'temporary' },
    updatedAt: Date.now(),
  };
}

export function queueItem({ source, trackId, title, artist, durationMs }, addedByUserId, createdAt = Date.now()) {
  return {
    id: nextId('mbi'),
    source: source || 'netease',
    trackId,
    title,
    artist: artist ?? '',
    durationMs: durationMs || 180_000,
    status: 'ready',
    fileSizeBytes: Math.round(((durationMs || 180_000) / 1000) * 16_000),
    addedByUserId,
    createdAt,
  };
}

const activeQueue = (box) => (box.activeSource.type === 'temporary' ? box.temporaryQueue : box.playlistQueue);
const currentIndex = (box) => activeQueue(box).findIndex((item) => item.id === box.currentItemId);

export function currentPosition(box, now = Date.now()) {
  const item = activeQueue(box).find((candidate) => candidate.id === box.currentItemId);
  if (!item) return 0;
  const position = box.state === 'playing' ? now - box.startedAt : box.positionMs;
  return Math.max(0, Math.min(item.durationMs, Math.round(position)));
}

function touch(box, now) {
  box.revision += 1;
  box.updatedAt = now;
}

function play(box, item, now, positionMs = 0) {
  box.currentItemId = item.id;
  box.state = 'playing';
  box.positionMs = positionMs;
  box.startedAt = now - positionMs;
}

function stop(box) {
  box.state = 'stopped';
  box.currentItemId = '';
  box.positionMs = 0;
}

/** Switches to `item` from the start; a paused box stays paused (docs/music-box-api.md). */
function switchTo(box, item, now) {
  const wasPaused = box.state === 'paused';
  play(box, item, now);
  if (wasPaused) box.state = 'paused';
}

function assertRevision(box, expectedRevision) {
  if (expectedRevision != null && expectedRevision !== box.revision) {
    throw conflict('music box state changed; refresh and try again', 'music_box_revision_conflict');
  }
}

/** The temporary queue only plays in order or repeats one; saved playlists allow every mode. */
export function allowedModes(box) {
  return box.activeSource.type === 'temporary' ? ['sequential', 'repeat_one'] : PLAYBACK_MODES;
}

/** Index of the item after the current one, or -1. `natural` = the track ended on its own. */
function nextIndex(box, { natural }) {
  const queue = activeQueue(box);
  if (queue.length === 0) return -1;
  const index = currentIndex(box);
  if (natural && box.mode === 'repeat_one' && index >= 0) return index;
  if (box.mode === 'shuffle' && queue.length > 1) {
    let candidate = index;
    while (candidate === index) candidate = Math.floor(Math.random() * queue.length);
    return candidate;
  }
  if (index + 1 < queue.length) return index + 1;
  return box.mode === 'sequential' ? -1 : 0;
}

function advance(box, now, { natural }) {
  const index = nextIndex(box, { natural });
  if (index < 0) stop(box);
  else switchTo(box, activeQueue(box)[index], now);
}

/**
 * Applies `POST /music-box/control` (actions per docs/music-box-api.md).
 * Transport actions ignore `expected_revision`; `set_mode` and
 * `clear_temporary_playlist` reject a stale one. Throws ApiError for invalid commands.
 */
export function control(box, { action, itemId, mode, expectedRevision }, now = Date.now()) {
  const queue = activeQueue(box);
  switch (action) {
    case 'play': {
      if (box.state === 'paused') return control(box, { action: 'resume' }, now);
      const item = queue.find((candidate) => candidate.id === (itemId || box.currentItemId)) ?? queue[0];
      if (!item) throw new ApiError(409, 'music_box_item_not_ready', 'queue is empty');
      play(box, item, now);
      break;
    }
    case 'pause':
      if (box.state !== 'playing') return;
      box.positionMs = currentPosition(box, now);
      box.state = 'paused';
      break;
    case 'resume':
      if (box.state !== 'paused') return;
      box.state = 'playing';
      box.startedAt = now - box.positionMs;
      break;
    case 'stop':
      stop(box);
      break;
    case 'skip':
    case 'next':
      advance(box, now, { natural: false });
      break;
    case 'previous': {
      const index = currentIndex(box);
      if (index < 0) return;
      // Past 3 seconds "previous" restarts the current song.
      if (currentPosition(box, now) > 3000) switchTo(box, queue[index], now);
      else if (index > 0) switchTo(box, queue[index - 1], now);
      else switchTo(box, box.mode === 'repeat_all' ? queue[queue.length - 1] : queue[0], now);
      break;
    }
    case 'play_now': {
      const item = queue.find((candidate) => candidate.id === itemId);
      if (!item) throw notFound('music box item not found');
      if (item.status !== 'ready') throw new ApiError(409, 'music_box_item_not_ready', 'music box item is not ready');
      if (box.activeSource.type === 'temporary' && box.currentItemId && item.id !== box.currentItemId) {
        // Queue order becomes: current song -> priority song -> the rest.
        const rest = box.temporaryQueue.filter((candidate) => candidate.id !== item.id);
        rest.splice(rest.findIndex((candidate) => candidate.id === box.currentItemId) + 1, 0, item);
        box.temporaryQueue = rest;
      }
      play(box, item, now);
      break;
    }
    case 'set_mode':
      if (!allowedModes(box).includes(mode)) throw badRequest(`mode ${mode} is not available for this source`);
      assertRevision(box, expectedRevision);
      box.mode = mode;
      break;
    case 'clear_temporary_playlist':
      assertRevision(box, expectedRevision);
      if (box.activeSource.type === 'temporary') stop(box);
      box.temporaryQueue = [];
      break;
    default:
      throw badRequest(`unknown music box action: ${action}`);
  }
  touch(box, now);
}

export function enqueue(box, entry, userId, now = Date.now()) {
  if (box.temporaryQueue.some((item) => item.trackId === entry.trackId && item.source === (entry.source || 'netease'))) {
    throw conflict('track already queued', 'music_box_item_already_queued');
  }
  if (box.temporaryQueue.length >= 200) throw conflict('queue limit reached', 'music_box_queue_limit_reached');
  const item = queueItem(entry, userId, now);
  box.temporaryQueue.push(item);
  if (box.activeSource.type === 'temporary' && box.state === 'stopped') play(box, item, now);
  touch(box, now);
  return item;
}

export function removeItem(box, itemId, now = Date.now()) {
  const queue = activeQueue(box);
  const inQueue = queue.some((item) => item.id === itemId);
  const inTemporary = box.temporaryQueue.some((item) => item.id === itemId);
  if (!inQueue && !inTemporary) throw notFound('music box item not found');
  if (box.currentItemId === itemId) advance(box, now, { natural: false });
  if (box.currentItemId === itemId) stop(box); // it was the only item
  box.temporaryQueue = box.temporaryQueue.filter((item) => item.id !== itemId);
  box.playlistQueue = box.playlistQueue.filter((item) => item.id !== itemId);
  touch(box, now);
}

/** Switches the active source to a playlist snapshot (or back to the temporary queue). */
export function activate(box, { sourceType, playlist = null, owner = null, startPlay = true, startItemId = null }, now = Date.now()) {
  if (sourceType === 'temporary') {
    box.activeSource = { type: 'temporary' };
    box.playlistQueue = [];
    if (!allowedModes(box).includes(box.mode)) box.mode = 'sequential';
  } else {
    if (!playlist) throw notFound('playlist not found');
    box.activeSource = {
      type: sourceType,
      id: playlist.id,
      name: playlist.name,
      ownerUserId: owner?.id ?? '',
      snapshotId: nextId('snap'),
      createdAt: now,
    };
    box.playlistQueue = playlist.items.map((item) =>
      queueItem({ ...item, artist: item.artists.join(' / ') }, owner?.id ?? '', now),
    );
    box.playlistQueue.forEach((queueEntry, index) => {
      queueEntry.playlistItemId = playlist.items[index].id;
    });
  }
  stop(box);
  if (startPlay) {
    const queue = activeQueue(box);
    const start = queue.find((item) => item.id === startItemId || item.playlistItemId === startItemId) ?? queue[0];
    if (start) play(box, start, now);
  }
  touch(box, now);
}

/** Advances finished tracks. Returns 'changed' (structural), 'progress', or null (idle). */
export function tick(box, now = Date.now()) {
  if (box.state !== 'playing') return null;
  const item = activeQueue(box).find((candidate) => candidate.id === box.currentItemId);
  if (!item) {
    stop(box);
    touch(box, now);
    return 'changed';
  }
  if (now - box.startedAt < item.durationMs) return 'progress';
  advance(box, now, { natural: true });
  touch(box, now);
  return 'changed';
}

export function progressJson(box, now = Date.now()) {
  return { revision: box.revision, current_item_id: box.currentItemId, position_ms: currentPosition(box, now) };
}

function requesterJson(store, userId) {
  const user = userId ? store.users.get(userId) : null;
  if (!user) return null;
  return {
    user_id: user.id,
    username: user.username,
    display_name: user.displayName,
    avatar_label: user.displayName,
    avatar_url: store.userAvatarUrl(user),
    default_avatar_key: user.defaultAvatarKey,
  };
}

function itemJson(store, box, item) {
  return {
    id: item.id,
    source: item.source,
    track_id: item.trackId,
    title: item.title,
    artist: item.artist,
    duration_ms: item.durationMs,
    status: item.status,
    file_size_bytes: item.fileSizeBytes,
    error: '',
    added_by_user_id: item.addedByUserId,
    created_at: iso(item.createdAt),
    can_play_now: item.id !== box.currentItemId,
    can_remove: true,
    requested_by: requesterJson(store, item.addedByUserId),
    playable: item.status === 'ready',
  };
}

/**
 * `MusicBoxState.fromJson` — the snapshot every music-box write returns.
 * Room fan-out (`broadcast`) carries all-false capabilities, as the real server
 * does, because they are actor-specific; clients refetch their own snapshot.
 */
export function musicBoxJson(store, room, { broadcast = false, now = Date.now() } = {}) {
  const box = room.musicBox;
  const can = !broadcast;
  const queue = activeQueue(box);
  const index = currentIndex(box);
  const source = box.activeSource;
  return {
    enabled: box.enabled,
    revision: box.revision,
    playback: {
      state: box.state,
      current_item_id: box.currentItemId,
      position_ms: currentPosition(box, now),
      volume: box.volume,
      updated_at: iso(box.updatedAt),
      mode: box.mode,
      can_previous: index >= 0 && queue.length > 0,
      can_next: queue.length > 0 && (index < queue.length - 1 || box.mode !== 'sequential'),
      capabilities: { can_control: can, can_change_mode: can, can_reorder: can, allowed_modes: allowedModes(box) },
    },
    queue: queue.map((item) => itemJson(store, box, item)),
    usage: {
      used_bytes: [...box.temporaryQueue, ...box.playlistQueue].reduce((sum, item) => sum + item.fileSizeBytes, 0),
      limit_bytes: 2 * 1024 * 1024 * 1024,
    },
    active_source:
      source.type === 'temporary'
        ? { type: 'temporary' }
        : {
            type: source.type,
            id: source.id,
            playlist_id: source.id,
            name: source.name,
            owner_user_id: source.ownerUserId,
            snapshot_id: source.snapshotId,
            created_at: iso(source.createdAt),
            owner: requesterJson(store, source.ownerUserId),
          },
    temporary_queue: box.temporaryQueue.map((item) => itemJson(store, box, item)),
    temporary_playlist: {
      queued_count: box.temporaryQueue.length,
      capabilities: { can_enqueue: can, can_switch: can, can_reorder: can, can_clear: can, can_play_now: can },
    },
  };
}
