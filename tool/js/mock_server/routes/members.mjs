// Room members: list, profile, role / nickname changes, removal and leaving,
// invites, the blacklist, and the admin join-request queue.
import { addRoomNotification, emitRoomUpdated, emitToUser, memberIds, postSystemMessage } from '../lib/events.mjs';
import { badRequest, conflict, created, forbidden, notFound, ok } from '../lib/http.mjs';
import { optionalString, oneOf } from '../lib/input.mjs';
import { admitMember, removeMembership, roomAdminIds } from '../lib/room_ops.mjs';
import { route } from '../lib/router.mjs';
import { clampInt, iso, nextId, paginateByCursor } from '../lib/util.mjs';

function listMembers(c) {
  const room = c.store.requireRoom(c.params.roomId);
  c.store.requireMember(room, c.user);
  const limit = clampInt(c.query.get('limit'), { min: 1, max: 200, fallback: 100 });
  const { page, nextCursor } = paginateByCursor(c.store.sortedMembers(room), { cursor: c.query.get('cursor'), limit });
  return { members: page.map((membership) => c.store.memberJson(room, membership)), next_cursor: nextCursor };
}

function requireTargetMember(room, userId) {
  const membership = room.members.get(userId);
  if (!membership) throw notFound('member not found');
  return membership;
}

function memberProfile(c) {
  const room = c.store.requireRoom(c.params.roomId);
  c.store.requireMember(room, c.user);
  const membership = requireTargetMember(room, c.params.userId);
  const user = c.store.users.get(membership.userId);
  return {
    profile: {
      user: c.store.userSummary(user, { commonRoomsFor: c.user.id }),
      role: membership.role,
      joined_at: iso(membership.joinedAt),
      room_display_name: membership.roomDisplayName,
      text_muted_until: membership.textMutedUntil,
    },
  };
}

/** PATCH /rooms/:id/members/:userId — `{ role }` (admins) or `{ room_display_name }` (self or admins). */
function updateMember(c) {
  const room = c.store.requireRoom(c.params.roomId);
  const mine = c.store.requireMember(room, c.user);
  const target = requireTargetMember(room, c.params.userId);
  const isSelf = target.userId === c.user.id;

  if (c.body.room_display_name !== undefined) {
    if (!isSelf && !c.store.isAdminRole(mine.role)) throw forbidden('admin required');
    target.roomDisplayName = optionalString(c.body, 'room_display_name') || null;
    c.hub.sendToUsers(memberIds(room), 'room_member_profile_changed', room.id, { room_id: room.id, user_id: target.userId });
  }

  const role = oneOf(c.body.role, ['admin', 'member'], 'role');
  if (role !== undefined && role !== target.role) {
    if (!c.store.isAdminRole(mine.role)) throw forbidden('admin required');
    if (target.role === 'owner') throw forbidden('owner required');
    if (target.role === 'admin' && mine.role !== 'owner' && !c.user.isSuperuser) throw forbidden('owner required');
    const fromRole = target.role;
    target.role = role;
    postSystemMessage(c.app, room, { event: 'room_role_changed', actorId: c.user.id, targetId: target.userId, fromRole, toRole: role });
    addRoomNotification(c.app, {
      userId: target.userId,
      type: role === 'admin' ? 'role_promoted' : 'role_demoted',
      roomId: room.id,
      actorId: c.user.id,
      fromRole,
      toRole: role,
    });
    emitToUser(c.app, target.userId, 'room_role_changed', room.id, { role });
  }
  return { member: c.store.memberJson(room, target) };
}

function leaveRoom(c) {
  const room = c.store.requireRoom(c.params.roomId);
  if (!room.members.has(c.user.id)) throw forbidden('not a room member');
  if (room.members.size === 1 && c.body.confirm_delete_if_empty !== true) {
    throw badRequest('the last member must confirm deleting the room', 'confirmation_required');
  }
  removeMembership(c.app, room, c.user.id, { reason: 'left' });
  return ok();
}

/** Admins may remove members; removing an admin needs the owner. */
function assertCanModerate(c, room, mine, target) {
  if (!c.store.isAdminRole(mine.role)) throw forbidden('admin required');
  if (target.userId === c.user.id) throw badRequest('cannot remove yourself');
  if (target.role === 'owner') throw forbidden('owner required');
  if (target.role === 'admin' && mine.role !== 'owner' && !c.user.isSuperuser) throw forbidden('owner required');
}

function removeMember(c) {
  const room = c.store.requireRoom(c.params.roomId);
  const mine = c.store.requireMember(room, c.user);
  const target = requireTargetMember(room, c.params.userId);
  assertCanModerate(c, room, mine, target);
  removeMembership(c.app, room, target.userId, { reason: 'removed', actorId: c.user.id });
  return ok();
}

function inviteMember(c) {
  const room = c.store.requireRoom(c.params.roomId);
  c.store.requireMember(room, c.user);
  const invitee = c.store.requireUser(String(c.body.user_id ?? ''));
  if (room.members.has(invitee.id)) throw conflict('user is already a member');
  if (room.blacklist.has(invitee.id)) throw forbidden('user is blocked from this room', 'blocked');
  let invite = [...c.store.invites.values()].find(
    (candidate) => candidate.roomId === room.id && candidate.inviteeId === invitee.id && candidate.status === 'pending',
  );
  if (!invite) {
    invite = {
      id: nextId('inv'),
      roomId: room.id,
      inviterId: c.user.id,
      inviteeId: invitee.id,
      status: 'pending',
      createdAt: Date.now(),
      updatedAt: Date.now(),
      hidden: false,
    };
    c.store.invites.set(invite.id, invite);
    emitToUser(c.app, invitee.id, 'room_invites_updated');
  }
  return created({ invite: c.store.inviteJson(invite) });
}

function blacklistEntryJson(c, userId, entry) {
  const blockedBy = entry.blockedBy ? c.store.users.get(entry.blockedBy) : null;
  return {
    user: c.store.userSummary(c.store.users.get(userId)),
    created_at: iso(entry.createdAt),
    blocked_by: blockedBy ? c.store.userSummary(blockedBy) : null,
  };
}

function listBlacklist(c) {
  const room = c.store.requireRoom(c.params.roomId);
  c.store.requireAdmin(room, c.user);
  return { blacklist: [...room.blacklist].map(([userId, entry]) => blacklistEntryJson(c, userId, entry)) };
}

function blockUser(c) {
  const room = c.store.requireRoom(c.params.roomId);
  const mine = c.store.requireAdmin(room, c.user);
  const user = c.store.requireUser(String(c.body.user_id ?? ''));
  const target = room.members.get(user.id);
  if (target) {
    assertCanModerate(c, room, mine, target);
    removeMembership(c.app, room, user.id, { reason: 'blocked', actorId: c.user.id });
  }
  const entry = room.blacklist.get(user.id) ?? { createdAt: Date.now(), blockedBy: c.user.id };
  room.blacklist.set(user.id, entry);
  return created({ entry: blacklistEntryJson(c, user.id, entry) });
}

function unblockUser(c) {
  const room = c.store.requireRoom(c.params.roomId);
  c.store.requireAdmin(room, c.user);
  room.blacklist.delete(c.params.userId);
  return ok();
}

function listJoinRequests(c) {
  const room = c.store.requireRoom(c.params.roomId);
  c.store.requireAdmin(room, c.user);
  const status = c.query.get('status') ?? 'pending';
  const requests = [...c.store.joinRequests.values()]
    .filter((request) => request.roomId === room.id && (status === 'all' || request.status === status))
    .sort((a, b) => b.createdAt - a.createdAt);
  return { requests: requests.map((request) => c.store.joinRequestJson(request)) };
}

function reviewJoinRequest(c) {
  const room = c.store.requireRoom(c.params.roomId);
  c.store.requireAdmin(room, c.user);
  const request = c.store.joinRequests.get(c.params.requestId);
  if (!request || request.roomId !== room.id) throw notFound('join request not found');
  if (request.status !== 'pending') throw conflict('join request already reviewed');
  const decision = oneOf(c.body.decision, ['approve', 'reject'], 'decision');
  if (!decision) throw badRequest('decision is required', 'validation_failed');
  if (decision === 'approve' && room.blacklist.has(request.userId)) {
    throw forbidden('user is blocked from this room', 'blocked');
  }
  Object.assign(request, { reviewedAt: Date.now(), reviewerId: c.user.id, updatedAt: Date.now() });
  if (decision === 'approve') {
    admitMember(c.app, room, request.userId);
    request.status = 'approved';
  } else {
    request.status = 'rejected';
  }
  emitToUser(c.app, request.userId, 'room_applications_updated');
  c.hub.sendToUsers(roomAdminIds(c.app, room), 'room_join_requests_updated', room.id, { room_id: room.id });
  emitRoomUpdated(c.app, room, 'join_requests_updated', roomAdminIds(c.app, room));
  return { request: c.store.joinRequestJson(request) };
}

export const memberRoutes = [
  route('GET', '/rooms/:roomId/members', listMembers),
  route('GET', '/rooms/:roomId/members/:userId/profile', memberProfile),
  route('PATCH', '/rooms/:roomId/members/:userId', updateMember),
  route('DELETE', '/rooms/:roomId/members/me', leaveRoom),
  route('DELETE', '/rooms/:roomId/members/:userId', removeMember),
  route('POST', '/rooms/:roomId/invites', inviteMember),
  route('GET', '/rooms/:roomId/blacklist', listBlacklist),
  route('POST', '/rooms/:roomId/blacklist', blockUser),
  route('DELETE', '/rooms/:roomId/blacklist/:userId', unblockUser),
  route('GET', '/rooms/:roomId/join-requests', listJoinRequests),
  route('PATCH', '/rooms/:roomId/join-requests/:requestId', reviewJoinRequest),
];
