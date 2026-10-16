// Membership and room lifecycle operations shared by several route modules
// (join via policy/invite/approval, leave/remove/block, delete, leave live).
import {
  addRoomNotification,
  emitLiveSnapshot,
  emitRoomAdded,
  emitRoomDeleted,
  emitRoomUpdated,
  emitToUser,
  memberIds,
  postSystemMessage,
} from './events.mjs';

/** Adds `userId` to the room, posts the join system message and updates everyone's room list. */
export function admitMember(app, room, userId) {
  if (room.members.has(userId)) return room.members.get(userId);
  const membership = app.store.addMember(room, userId, { role: 'member', lastReadSeq: Math.max(0, room.seq - 5) });
  postSystemMessage(app, room, { event: 'room_member_joined', userId });
  emitRoomAdded(app, room, userId);
  for (const request of app.store.joinRequests.values()) {
    if (request.roomId === room.id && request.userId === userId && request.status === 'pending') {
      request.status = 'approved';
      request.updatedAt = Date.now();
    }
  }
  for (const invite of app.store.invites.values()) {
    if (invite.roomId === room.id && invite.inviteeId === userId && invite.status === 'pending') {
      invite.status = 'accepted';
      invite.updatedAt = Date.now();
    }
  }
  return membership;
}

/** Removes the user from the room's voice channel, if present. */
export function leaveLive(app, room, userId, { announce = true } = {}) {
  const participant = room.live.participants.get(userId);
  if (!participant) return null;
  room.live.participants.delete(userId);
  for (const other of room.live.participants.values()) other.screenViewerIds.delete(userId);
  room.live.updatedAt = Date.now();
  emitLiveSnapshot(app, room, 'live_participant_left', { user_id: userId });
  if (announce && room.members.has(userId)) postSystemMessage(app, room, { event: 'live_left', userId });
  return participant;
}

/**
 * Drops a membership (`reason`: left | removed | blocked). The departing user
 * gets `room_deleted`; ownership passes on if the owner leaves; an empty room
 * is deleted.
 */
export function removeMembership(app, room, userId, { reason = 'left', actorId = null } = {}) {
  const membership = room.members.get(userId);
  if (!membership) return;
  leaveLive(app, room, userId, { announce: false });
  if (reason === 'left') postSystemMessage(app, room, { event: 'room_member_left', userId });
  else postSystemMessage(app, room, { event: 'room_member_removed', actorId, targetId: userId });
  room.members.delete(userId);
  emitRoomDeleted(app, room.id, [userId]);
  if (reason !== 'left') {
    addRoomNotification(app, { userId, type: 'member_removed', roomId: room.id, actorId });
  }
  if (room.members.size === 0) {
    deleteRoom(app, room);
    return;
  }
  if (membership.role === 'owner') {
    const heir =
      [...room.members.values()].filter((m) => m.role === 'admin').sort((a, b) => a.joinedAt - b.joinedAt)[0] ??
      [...room.members.values()].sort((a, b) => a.joinedAt - b.joinedAt)[0];
    heir.role = 'owner';
    emitToUser(app, heir.userId, 'room_role_changed', room.id, { role: 'owner' });
  }
  emitRoomUpdated(app, room, 'member_left');
}

/** Deletes the room and everything that references it. */
export function deleteRoom(app, room) {
  const affected = new Set(memberIds(room));
  app.store.rooms.delete(room.id);
  for (const [id, invite] of app.store.invites) if (invite.roomId === room.id) app.store.invites.delete(id);
  for (const [id, request] of app.store.joinRequests) if (request.roomId === room.id) app.store.joinRequests.delete(id);
  for (const [id, notification] of app.store.notifications) {
    if (notification.roomId === room.id) {
      app.store.notifications.delete(id);
      affected.add(notification.userId);
    }
  }
  for (const [id, pack] of app.store.stickerPacks) if (pack.roomId === room.id) app.store.stickerPacks.delete(id);
  for (const [id, playlist] of app.store.playlists) if (playlist.ownerKey === `room:${room.id}`) app.store.playlists.delete(id);
  emitRoomDeleted(app, room.id, [...affected]);
}

/** Admins (and the owner) who review join requests. */
export function roomAdminIds(app, room) {
  return [...room.members.values()].filter((m) => app.store.isAdminRole(m.role)).map((m) => m.userId);
}
