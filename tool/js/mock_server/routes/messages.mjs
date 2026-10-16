// Messages: paged history (ascending), filtered history view (newest first),
// send with idempotent retries, read receipts, recall (incl. the admin
// approval flow), force delete and per-user hiding.
import { emitRoomUpdated, postMessage } from '../lib/events.mjs';
import { badRequest, conflict, created, forbidden, json, notFound } from '../lib/http.mjs';
import { oneOf, stringList } from '../lib/input.mjs';
import { requirePlaylist, sharedPlaylistJson } from '../lib/playlists.mjs';
import { route } from '../lib/router.mjs';
import { clampInt, foldedIncludes, iso, nextId } from '../lib/util.mjs';

const MESSAGE_TYPES = ['text', 'file', 'audio', 'sticker', 'music_track', 'playlist'];

function memberRoom(c) {
  const room = c.store.requireRoom(c.params.roomId);
  const membership = c.store.requireMember(room, c.user);
  return { room, membership };
}

/** Takes `limit` messages strictly older than message `before` (an id). */
function pageBefore(messages, before, limit) {
  let end = messages.length;
  if (before) {
    end = messages.findIndex((message) => message.id === before);
    if (end < 0) throw notFound('message not found');
  }
  const start = Math.max(0, end - limit);
  return { page: messages.slice(start, end), hasMore: start > 0 };
}

/** GET /rooms/:id/messages — oldest-first page; `next_before` is the oldest id when more exist. */
function listMessages(c) {
  const { room } = memberRoom(c);
  const limit = clampInt(c.query.get('limit'), { min: 1, max: 100, fallback: 50 });
  const { page, hasMore } = pageBefore(c.store.visibleMessages(room, c.user.id), c.query.get('before'), limit);
  return {
    messages: page.map((message) => c.store.messageJson(room, message)),
    has_more: hasMore,
    next_before: hasMore && page.length > 0 ? page[0].id : null,
  };
}

const HISTORY_CATEGORIES = {
  all: () => true,
  links: (message) => /https?:\/\//i.test(message.body),
  voice: (message) => message.type === 'audio',
  stickers: (message) => message.type === 'sticker',
  music: (message) => message.type === 'music_track' || message.type === 'playlist',
  system: (message) => message.type === 'system',
};

/** GET /rooms/:id/message-history — filtered, newest first (the history dialog appends older pages). */
function listMessageHistory(c) {
  const { room } = memberRoom(c);
  const { store } = c;
  const limit = clampInt(c.query.get('limit'), { min: 1, max: 100, fallback: 50 });
  const category = c.query.get('category') ?? 'all';
  const query = c.query.get('query')?.trim() ?? '';
  const sender = c.query.get('sender_user_id');
  const startAt = Date.parse(c.query.get('start_at') ?? '') || null;
  const endAt = Date.parse(c.query.get('end_at') ?? '') || null;
  const isImage = (attachment) => store.assets.get(attachment.assetId)?.mimeType.startsWith('image/');
  const categoryFilter =
    category === 'images'
      ? (message) => message.attachments.some((attachment) => attachment.type === 'file' && isImage(attachment))
      : category === 'files'
        ? (message) => message.attachments.some((attachment) => attachment.type === 'file' && !isImage(attachment))
        : HISTORY_CATEGORIES[category];
  if (!categoryFilter) throw badRequest(`unknown category: ${category}`, 'validation_failed');

  const matches = store.visibleMessages(room, c.user.id).filter(
    (message) =>
      categoryFilter(message) &&
      (!sender || message.senderId === sender) &&
      (!startAt || message.createdAt >= startAt) &&
      (!endAt || message.createdAt < endAt) &&
      (!query ||
        foldedIncludes(message.body, query) ||
        message.attachments.some((attachment) => foldedIncludes(attachment.name, query))),
  );
  const { page, hasMore } = pageBefore(matches, c.query.get('before'), limit);
  const newestFirst = page.reverse();
  return {
    messages: newestFirst.map((message) => c.store.messageJson(room, message)),
    has_more: hasMore,
    next_before: hasMore && newestFirst.length > 0 ? newestFirst[newestFirst.length - 1].id : null,
  };
}

function hideMessageHistory(c) {
  const { room } = memberRoom(c);
  if (c.body.confirm !== true) throw badRequest('confirmation required', 'confirmation_required');
  const ids = new Set(stringList(c.body, 'message_ids'));
  const hidden = room.hiddenMessageIds.get(c.user.id) ?? new Set();
  let count = 0;
  for (const message of room.messages) {
    if (ids.has(message.id) && !hidden.has(message.id)) {
      hidden.add(message.id);
      count += 1;
    }
  }
  room.hiddenMessageIds.set(c.user.id, hidden);
  return { deleted_count: count };
}

/** Converts a client attachment (MessageAttachment.toJson) into a stored spec. */
function attachmentSpec(c, json) {
  if (!json || typeof json !== 'object') throw badRequest('invalid attachment', 'validation_failed');
  const assetId = json.asset?.id && c.store.assets.has(json.asset.id) ? json.asset.id : null;
  return {
    type: typeof json.type === 'string' ? json.type : 'file',
    name: json.name ?? null,
    assetId,
    asset: assetId ? null : (json.asset ?? null),
    stickerId: json.sticker_id ?? null,
    durationMs: json.duration_ms ?? null,
    playlistId: json.playlist_id ?? null,
    playlistScope: json.playlist_scope ?? null,
    sourceRoomId: json.source_room_id ?? null,
    sourceMessageId: json.source_message_id ?? null,
    itemId: json.item_id ?? null,
    track: json.track ?? null,
    playlist: json.playlist ?? null,
  };
}

/** Finds a non-removed message in any room the caller belongs to (forwarding). */
function findAccessibleMessage(c, messageId) {
  for (const room of c.store.rooms.values()) {
    if (!room.members.has(c.user.id)) continue;
    const message = room.messages.find((candidate) => candidate.id === messageId);
    if (message && !c.store.isRemoved(message)) return message;
  }
  throw notFound('message not found');
}

/** Resolves `{ playlist_scope, source_room_id }` to a playlist owner key the caller may read. */
function shareOwnerKey(c, spec) {
  if (spec.playlistScope !== 'room') return `user:${c.user.id}`;
  const room = c.store.requireRoom(spec.sourceRoomId ?? '');
  c.store.requireMember(room, c.user);
  return `room:${room.id}`;
}

/**
 * Playlist and song shares carry only references (docs/music-box-api.md): the
 * server swaps in an immutable snapshot and normalizes the body. Forwards
 * (`source_message_id`) copy the original message's snapshot.
 */
function resolveMusicShare(c, type, attachments) {
  const ref = attachments.find((attachment) => attachment.type === type);
  if (!ref) throw badRequest(`${type} attachment is required`, 'validation_failed');
  const label = type === 'playlist' ? '歌单' : '歌曲';
  if (ref.sourceMessageId) {
    const original = findAccessibleMessage(c, ref.sourceMessageId).attachments.find(
      (attachment) => attachment.type === type && (attachment.playlist || attachment.track),
    );
    if (!original) throw notFound('message not found');
    return { body: `[${label}] ${original.playlist?.name ?? original.track?.title}`, attachments: [{ ...original }] };
  }
  if (!ref.playlistId) return { attachments }; // already a snapshot (legacy shape): keep it
  const ownerKey = shareOwnerKey(c, ref);
  const playlist = requirePlaylist(c.store, ownerKey, ref.playlistId);
  const scope = { playlistId: playlist.id, playlistScope: ref.playlistScope ?? 'personal', sourceRoomId: ref.sourceRoomId };
  if (type === 'playlist') {
    const snapshot = sharedPlaylistJson(c.store, playlist, c.user.id);
    return { body: `[歌单] ${playlist.name}`, attachments: [{ type, ...scope, playlist: snapshot }] };
  }
  const item = playlist.items.find((candidate) => candidate.id === ref.itemId);
  if (!item) throw notFound('playlist item not found');
  const track = {
    id: item.id,
    playlist_id: playlist.id,
    track_id: item.trackId,
    source: item.source,
    title: item.title,
    artists: item.artists,
    duration_ms: item.durationMs,
  };
  return { body: `[歌曲] ${item.title}`, attachments: [{ type, ...scope, itemId: item.id, track }] };
}

/** POST /rooms/:id/messages — retries with the same `client_message_id` return the original. */
function sendMessage(c) {
  const { room } = memberRoom(c);
  const clientMessageId = String(c.body.client_message_id ?? '').trim();
  if (!clientMessageId) throw badRequest('client_message_id is required', 'validation_failed');
  const existing = room.messages.find(
    (message) => message.senderId === c.user.id && message.clientMessageId === clientMessageId,
  );
  if (existing) return { message: c.store.messageJson(room, existing) };

  const type = oneOf(c.body.type ?? 'text', MESSAGE_TYPES, 'type');
  let body = typeof c.body.body === 'string' ? c.body.body : '';
  let attachments = (Array.isArray(c.body.attachments) ? c.body.attachments : []).map((item) => attachmentSpec(c, item));
  if (type === 'playlist' || type === 'music_track') {
    const share = resolveMusicShare(c, type, attachments);
    body = share.body ?? body;
    attachments = share.attachments;
  }
  if (!body.trim() && attachments.length === 0) throw badRequest('message body is required', 'validation_failed');
  if (body.length > 10_000) throw badRequest('message body is too long', 'validation_failed');

  const quoteIds = stringList(c.body, 'quote_message_ids');
  if (quoteIds.length === 0 && typeof c.body.quote_message_id === 'string') quoteIds.push(c.body.quote_message_id);
  const quotes = quoteIds.map((id) => {
    const quoted = room.messages.find((message) => message.id === id);
    if (!quoted || c.store.isRemoved(quoted)) throw badRequest('quoted message is unavailable');
    return c.store.quoteSpec(room, quoted);
  });
  const mentions = (Array.isArray(c.body.mentions) ? c.body.mentions : []).filter(
    (mention) => mention && typeof mention === 'object',
  );

  const message = postMessage(c.app, room, { senderId: c.user.id, clientMessageId, type, body, attachments, mentions, quotes });
  return created({ message: c.store.messageJson(room, message) });
}

function markRead(c) {
  const { room, membership } = memberRoom(c);
  const message = c.store.findMessage(room, String(c.body.last_read_message_id ?? ''));
  if (room.members.has(c.user.id)) membership.lastReadSeq = Math.max(membership.lastReadSeq, message.seq);
  const unread = room.members.has(c.user.id) ? c.store.unreadFor(room, c.user.id) : { count: 0, mentions: 0 };
  // Account-scoped snapshot: other devices of this user clear their badge too.
  emitRoomUpdated(c.app, room, 'read', [c.user.id]);
  return { unread_count: unread.count, unread_mention_count: unread.mentions, last_read_message_id: message.id };
}

function recall(c, room, message, byUserId) {
  message.recalledAt = Date.now();
  message.recalledBy = byUserId;
  emitRoomUpdated(c.app, room, 'message_recalled');
}

function recallRequestJson(request) {
  return {
    id: request.id,
    room_id: request.roomId,
    message_id: request.messageId,
    status: request.status,
    requested_by_user_id: request.requestedByUserId,
    created_at: iso(request.createdAt),
  };
}

/**
 * POST /rooms/:id/messages/:messageId/recall. Admins recall anything; senders
 * recall their own within the room window. Under `admin_approval` a sender's
 * request is queued (202 + `recall_request`).
 */
function recallMessage(c) {
  const { room, membership } = memberRoom(c);
  const message = c.store.findMessage(room, c.params.messageId);
  if (c.store.isRemoved(message)) throw conflict('message already recalled');
  if (message.type === 'system') throw forbidden('system messages cannot be recalled');
  const isAdmin = c.store.isAdminRole(membership.role);
  if (!isAdmin) {
    if (room.messageRecallPolicy === 'disabled' || room.messageRecallPolicy === 'admins') {
      throw forbidden('message recall is restricted to admins');
    }
    if (message.senderId !== c.user.id) throw forbidden('only the sender can recall this message');
    const window = room.messageRecallWindowSeconds;
    if (window != null && Date.now() - message.createdAt > window * 1000) {
      throw forbidden(`messages can only be recalled within ${window} seconds`);
    }
    if (room.messageRecallPolicy === 'admin_approval') {
      const request = {
        id: nextId('rcr'),
        roomId: room.id,
        messageId: message.id,
        status: 'pending',
        requestedByUserId: c.user.id,
        reason: c.body.reason ?? null,
        createdAt: Date.now(),
      };
      room.recallRequests.push(request);
      return json(202, { recall_request: recallRequestJson(request) });
    }
  }
  recall(c, room, message, c.user.id);
  return { message: c.store.messageJson(room, message) };
}

function listRecallRequests(c) {
  const room = c.store.requireRoom(c.params.roomId);
  c.store.requireAdmin(room, c.user);
  const status = c.query.get('status') ?? 'pending';
  return {
    requests: room.recallRequests
      .filter((request) => status === 'all' || request.status === status)
      .map(recallRequestJson),
  };
}

function reviewRecallRequest(c) {
  const room = c.store.requireRoom(c.params.roomId);
  c.store.requireAdmin(room, c.user);
  const request = room.recallRequests.find((candidate) => candidate.id === c.params.requestId);
  if (!request) throw notFound('recall request not found');
  if (request.status !== 'pending') throw conflict('recall request already reviewed');
  const decision = oneOf(c.body.decision, ['approve', 'reject'], 'decision');
  if (!decision) throw badRequest('decision is required', 'validation_failed');
  request.status = decision === 'approve' ? 'approved' : 'rejected';
  if (decision === 'approve') {
    const message = c.store.findMessage(room, request.messageId);
    if (!c.store.isRemoved(message)) recall(c, room, message, request.requestedByUserId);
  }
  return { request: recallRequestJson(request) };
}

function forceDeleteMessage(c) {
  const room = c.store.requireRoom(c.params.roomId);
  c.store.requireAdmin(room, c.user);
  if (c.body.confirm !== true) throw badRequest('confirmation required', 'confirmation_required');
  const message = c.store.findMessage(room, c.params.messageId);
  if (!message.forceDeletedAt) {
    message.forceDeletedAt = Date.now();
    message.forceDeletedBy = c.user.id;
    emitRoomUpdated(c.app, room, 'message_deleted');
  }
  return { message: c.store.messageJson(room, message) };
}

export const messageRoutes = [
  route('GET', '/rooms/:roomId/messages', listMessages),
  route('POST', '/rooms/:roomId/messages', sendMessage),
  route('GET', '/rooms/:roomId/message-history', listMessageHistory),
  route('POST', '/rooms/:roomId/message-history/hide', hideMessageHistory),
  route('POST', '/rooms/:roomId/read', markRead),
  route('POST', '/rooms/:roomId/messages/:messageId/recall', recallMessage),
  route('POST', '/rooms/:roomId/messages/:messageId/force-delete', forceDeleteMessage),
  route('GET', '/rooms/:roomId/message-recall-requests', listRecallRequests),
  route('PATCH', '/rooms/:roomId/message-recall-requests/:requestId', reviewRecallRequest),
];
