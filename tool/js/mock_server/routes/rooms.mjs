// Rooms: list (paged), create, detail, update, personal settings, ownership
// transfer, delete, join, public room search and global search.
import { emitRoomAdded, emitRoomUpdated, emitToUser, memberIds, postSystemMessage } from '../lib/events.mjs';
import { badRequest, created, forbidden, json, notFound, ok } from '../lib/http.mjs';
import { optionalBool, optionalString, oneOf, requiredString } from '../lib/input.mjs';
import { createMusicBox } from '../lib/music_box.mjs';
import { admitMember, deleteRoom, roomAdminIds } from '../lib/room_ops.mjs';
import { route } from '../lib/router.mjs';
import { clampInt, foldedIncludes, nextId, paginateByCursor } from '../lib/util.mjs';

export const VISIBILITIES = ['public', 'private'];
export const JOIN_POLICIES = ['open', 'approval_required', 'closed'];
const NOTIFICATION_POLICIES = ['all', 'silent', 'blocked'];

/** Rooms of `userId`, pinned first, then by latest activity. */
function myRooms(store, userId) {
  return [...store.rooms.values()]
    .filter((room) => room.members.has(userId))
    .sort((a, b) => {
      const pinned = Number(b.members.get(userId).isPinned) - Number(a.members.get(userId).isPinned);
      return pinned !== 0 ? pinned : b.updatedAt - a.updatedAt;
    });
}

function listRooms(c) {
  const limit = clampInt(c.query.get('limit'), { min: 1, max: 100, fallback: 50 });
  const { page, nextCursor } = paginateByCursor(myRooms(c.store, c.user.id), { cursor: c.query.get('cursor'), limit });
  return { rooms: page.map((room) => c.store.roomCard(room, c.user.id)), next_cursor: nextCursor };
}

function createRoom(c) {
  const key = c.req.headers['idempotency-key'];
  const replay = key && c.store.rooms.get(c.store.idempotencyKeys.get(`${c.user.id}:${key}`));
  if (replay) return created({ room: c.store.roomDetail(replay, c.user.id) });

  const name = requiredString(c.body, 'name', { max: 32, message: 'room name is required' });
  const room = c.store.addRoom({
    id: nextId('room'),
    name,
    description: optionalString(c.body, 'description') ?? '',
    visibility: oneOf(c.body.visibility, VISIBILITIES, 'visibility') ?? 'public',
    joinPolicy: oneOf(c.body.join_policy, JOIN_POLICIES, 'join_policy') ?? 'approval_required',
    aiVoiceAnnouncementsEnabled: optionalBool(c.body, 'ai_voice_announcements_enabled') ?? false,
    defaultAvatarKey: optionalString(c.body, 'default_avatar_key') || 'blue-3',
    avatarAssetId: c.body.avatar_asset_id && c.store.assets.has(c.body.avatar_asset_id) ? c.body.avatar_asset_id : null,
    createdBy: c.user.id,
    createdAt: Date.now(),
  });
  room.musicBox = createMusicBox();
  c.store.addMember(room, c.user.id, { role: 'owner', aiVoiceAnnouncementsEnabled: room.aiVoiceAnnouncementsEnabled });
  if (key) c.store.idempotencyKeys.set(`${c.user.id}:${key}`, room.id);
  emitRoomAdded(c.app, room, c.user.id);
  return created({ room: c.store.roomDetail(room, c.user.id) });
}

function getRoom(c) {
  const room = c.store.requireRoom(c.params.roomId);
  c.store.requireMember(room, c.user);
  return { room: c.store.roomDetail(room, c.user.id) };
}

/** Admin-only room settings; posts the matching system messages. */
function updateRoom(c) {
  const room = c.store.requireRoom(c.params.roomId);
  c.store.requireAdmin(room, c.user);
  const changes = [];
  const change = (event, field, value) => {
    if (value === undefined || value === room[field]) return;
    changes.push({ event, oldValue: String(room[field]), newValue: String(value) });
    room[field] = value;
  };
  const name = optionalString(c.body, 'name');
  if (name !== undefined && !name) throw badRequest('room name is required', 'validation_failed');
  change('room_name_changed', 'name', name);
  change('room_description_changed', 'description', optionalString(c.body, 'description'));
  change('room_visibility_changed', 'visibility', oneOf(c.body.visibility, VISIBILITIES, 'visibility'));
  change('room_join_policy_changed', 'joinPolicy', oneOf(c.body.join_policy, JOIN_POLICIES, 'join_policy'));
  const aiVoice = optionalBool(c.body, 'ai_voice_announcements_enabled');
  if (aiVoice !== undefined) room.aiVoiceAnnouncementsEnabled = aiVoice;
  const gangBot = optionalBool(c.body, 'gang_bot_enabled');
  if (gangBot !== undefined) room.gangBotEnabled = gangBot;
  if (c.body.avatar_asset_id) {
    if (!c.store.assets.has(c.body.avatar_asset_id)) throw notFound('asset not found');
    room.avatarAssetId = c.body.avatar_asset_id;
  }
  const avatarKey = optionalString(c.body, 'default_avatar_key');
  if (avatarKey) {
    room.defaultAvatarKey = avatarKey;
    if (!c.body.avatar_asset_id) room.avatarAssetId = null;
  }
  room.updatedAt = Date.now();
  for (const { event, oldValue, newValue } of changes) {
    postSystemMessage(c.app, room, { event, actorId: c.user.id, oldValue, newValue });
  }
  if (changes.length === 0) emitRoomUpdated(c.app, room, 'room_updated');
  return { room: c.store.roomDetail(room, c.user.id) };
}

function updateMyRoomSettings(c) {
  const room = c.store.requireRoom(c.params.roomId);
  const membership = room.members.get(c.user.id);
  if (!membership) throw forbidden('not a room member');
  const remark = optionalString(c.body, 'remark_name');
  if (remark !== undefined) membership.remarkName = remark || null;
  const policy = oneOf(c.body.notification_policy, NOTIFICATION_POLICIES, 'notification_policy');
  if (policy !== undefined) membership.notificationPolicy = policy;
  const nickname = optionalString(c.body, 'room_display_name');
  if (nickname !== undefined) membership.roomDisplayName = nickname || null;
  const pinned = optionalBool(c.body, 'is_pinned');
  if (pinned !== undefined) membership.isPinned = pinned;
  const aiVoice = optionalBool(c.body, 'ai_voice_announcements_enabled');
  if (aiVoice !== undefined) membership.aiVoiceAnnouncementsEnabled = aiVoice;
  emitRoomUpdated(c.app, room, 'settings_updated', [c.user.id]);
  if (nickname !== undefined) {
    c.hub.sendToUsers(memberIds(room), 'room_member_profile_changed', room.id, { room_id: room.id, user_id: c.user.id });
  }
  return { room: c.store.roomDetail(room, c.user.id) };
}

function transferCreator(c) {
  const room = c.store.requireRoom(c.params.roomId);
  const mine = c.store.requireMember(room, c.user);
  if (mine.role !== 'owner' && !c.user.isSuperuser) throw forbidden('owner required');
  const target = room.members.get(String(c.body.user_id ?? ''));
  if (!target) throw notFound('member not found');
  const previousOwner = c.store.ownerOf(room);
  if (previousOwner && previousOwner !== target) {
    previousOwner.role = 'admin';
    emitToUser(c.app, previousOwner.userId, 'room_role_changed', room.id, { role: 'admin' });
  }
  const fromRole = target.role;
  target.role = 'owner';
  emitToUser(c.app, target.userId, 'room_role_changed', room.id, { role: 'owner' });
  postSystemMessage(c.app, room, { event: 'room_role_changed', actorId: c.user.id, targetId: target.userId, fromRole, toRole: 'owner' });
  return { room: c.store.roomDetail(room, c.user.id) };
}

function removeRoom(c) {
  const room = c.store.requireRoom(c.params.roomId);
  const membership = c.store.requireMember(room, c.user);
  if (membership.role !== 'owner' && !c.user.isSuperuser) throw forbidden('owner required');
  if (String(c.body.confirm_name ?? '').trim() !== room.name) {
    throw badRequest('confirm_name does not match the room name', 'confirmation_required');
  }
  deleteRoom(c.app, room);
  return ok();
}

/** POST /rooms/:id/join — open rooms join immediately; approval rooms answer 202 with a join request. */
function joinRoom(c) {
  const room = c.store.requireRoom(c.params.roomId);
  if (room.members.has(c.user.id)) return { room: c.store.roomDetail(room, c.user.id) };
  if (room.blacklist.has(c.user.id)) throw forbidden('user is blocked from this room', 'blocked');
  const invited = [...c.store.invites.values()].some(
    (invite) => invite.roomId === room.id && invite.inviteeId === c.user.id && invite.status === 'pending',
  );
  if (room.joinPolicy === 'open' || invited) {
    admitMember(c.app, room, c.user.id);
    return { room: c.store.roomDetail(room, c.user.id) };
  }
  if (room.joinPolicy === 'closed') throw forbidden('room is not accepting new members');
  let request = [...c.store.joinRequests.values()].find(
    (candidate) => candidate.roomId === room.id && candidate.userId === c.user.id && candidate.status === 'pending',
  );
  if (!request) {
    request = {
      id: nextId('jrq'),
      roomId: room.id,
      userId: c.user.id,
      status: 'pending',
      reason: optionalString(c.body, 'reason') ?? '',
      source: 'public_search',
      inviterIds: [],
      createdAt: Date.now(),
      updatedAt: Date.now(),
      reviewedAt: null,
      reviewerId: null,
      requestNotificationDeleted: false,
      reviewNotificationDeleted: false,
    };
    c.store.joinRequests.set(request.id, request);
    c.hub.sendToUsers(roomAdminIds(c.app, room), 'room_join_requests_updated', room.id, { room_id: room.id });
    emitRoomUpdated(c.app, room, 'join_requests_updated', roomAdminIds(c.app, room));
    emitToUser(c.app, c.user.id, 'room_applications_updated');
  }
  return json(202, { join_request: c.store.joinRequestJson(request) });
}

function searchRooms(c) {
  const query = String(c.query.get('q') ?? '').trim();
  const limit = clampInt(c.query.get('limit'), { min: 1, max: 50, fallback: 20 });
  return { rooms: matchingPublicRooms(c, query, { includeJoined: true }).slice(0, limit).map((room) => c.store.publicRoom(room, c.user.id)) };
}

function matchingPublicRooms(c, query, { includeJoined }) {
  if (!query) return [];
  return [...c.store.rooms.values()].filter(
    (room) =>
      (room.visibility === 'public' || room.members.has(c.user.id)) &&
      (includeJoined || !room.members.has(c.user.id)) &&
      (foldedIncludes(room.name, query) || room.rid === query || foldedIncludes(room.description, query)),
  );
}

/** GET /search — the title-bar global search across rooms, messages, files and (superusers) users. */
function globalSearch(c) {
  const { store, user } = c;
  const query = String(c.query.get('q') ?? '').trim();
  const limit = clampInt(c.query.get('limit'), { min: 1, max: 50, fallback: 8 });
  const requested = String(c.query.get('categories') ?? '').split(',').filter(Boolean);
  const wants = (category) => requested.length === 0 || requested.includes(category);
  const joined = myRooms(store, user.id);

  const messageHits = (predicate) =>
    query
      ? joined
          .flatMap((room) =>
            store
              .visibleMessages(room, user.id)
              .filter((message) => !store.isRemoved(message) && predicate(message))
              .map((message) => ({ room, message })),
          )
          .sort((a, b) => b.message.createdAt - a.message.createdAt)
      : [];
  const fileName = (attachment) => attachment.name ?? store.assets.get(attachment.assetId)?.filename ?? '';
  const sources = {
    my_rooms: () =>
      query
        ? joined.filter(
            (room) =>
              foldedIncludes(room.name, query) ||
              foldedIncludes(room.members.get(user.id).remarkName, query) ||
              room.rid === query,
          )
        : [],
    public_rooms: () => matchingPublicRooms(c, query, { includeJoined: false }),
    user_settings: () =>
      user.isSuperuser && query
        ? [...store.users.values()].filter(
            (candidate) => !candidate.isBot && (foldedIncludes(candidate.username, query) || foldedIncludes(candidate.displayName, query)),
          )
        : [],
    messages: () => messageHits((message) => message.type !== 'system' && foldedIncludes(message.body, query)),
    files: () =>
      messageHits((message) =>
        message.attachments.some((attachment) => attachment.type === 'file' && foldedIncludes(fileName(attachment), query)),
      ),
  };
  const render = {
    my_rooms: (room) => store.roomCard(room, user.id),
    public_rooms: (room) => store.publicRoom(room, user.id),
    user_settings: (match) => store.userSummary(match),
    messages: (hit) => ({ room: store.searchRoomContext(hit.room, user.id), message: store.messageJson(hit.room, hit.message) }),
  };
  render.files = render.messages;

  const result = { next_cursors: {}, total_counts: {} };
  for (const [category, source] of Object.entries(sources)) {
    const items = wants(category) ? source() : [];
    const { page, nextCursor, total } = paginateByCursor(items, { cursor: c.query.get(`${category}_cursor`), limit });
    result[category] = page.map(render[category]);
    result.next_cursors[category] = nextCursor;
    result.total_counts[category] = total;
  }
  return result;
}

export const roomRoutes = [
  route('GET', '/rooms', listRooms),
  route('POST', '/rooms', createRoom),
  route('GET', '/rooms/search', searchRooms),
  route('GET', '/rooms/:roomId', getRoom),
  route('PATCH', '/rooms/:roomId', updateRoom),
  route('DELETE', '/rooms/:roomId', removeRoom),
  route('PATCH', '/rooms/:roomId/me', updateMyRoomSettings),
  route('PATCH', '/rooms/:roomId/creator', transferCreator),
  route('POST', '/rooms/:roomId/join', joinRoom),
  route('GET', '/search', globalSearch),
];
