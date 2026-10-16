// The notification center: room invites addressed to me, my join
// applications, and room event notifications (mentions, role changes,
// removals) with delete + mark-all-read.
import { emitToUser } from '../lib/events.mjs';
import { badRequest, conflict, forbidden, notFound, ok } from '../lib/http.mjs';
import { oneOf } from '../lib/input.mjs';
import { admitMember, roomAdminIds } from '../lib/room_ops.mjs';
import { route } from '../lib/router.mjs';

const byNewest = (a, b) => b.createdAt - a.createdAt;
const statusMatches = (status, wanted) => !wanted || wanted === 'all' || status === wanted;

function listInvites(c) {
  const wanted = c.query.get('status') ?? 'pending';
  const invites = [...c.store.invites.values()]
    .filter((invite) => invite.inviteeId === c.user.id && !invite.hidden && statusMatches(invite.status, wanted))
    .sort(byNewest);
  return { invites: invites.map((invite) => c.store.inviteJson(invite)) };
}

/** PATCH /room-invites/:id `{ decision: accept | reject }` — accepting joins immediately. */
function reviewInvite(c) {
  const invite = c.store.invites.get(c.params.inviteId);
  if (!invite || invite.inviteeId !== c.user.id) throw notFound('invite not found');
  if (invite.status !== 'pending') throw conflict('invite already handled');
  const decision = oneOf(c.body.decision, ['accept', 'reject'], 'decision');
  if (!decision) throw badRequest('decision is required', 'validation_failed');
  const room = c.store.requireRoom(invite.roomId);
  invite.updatedAt = Date.now();
  emitToUser(c.app, c.user.id, 'room_invites_updated');
  if (decision === 'reject') {
    invite.status = 'rejected';
    return { invite: c.store.inviteJson(invite) };
  }
  if (room.blacklist.has(c.user.id)) throw forbidden('user is blocked from this room', 'blocked');
  admitMember(c.app, room, c.user.id);
  invite.status = 'accepted';
  return { room: c.store.roomDetail(room, c.user.id) };
}

function listApplications(c) {
  const wanted = c.query.get('status') ?? 'pending';
  const applications = [...c.store.joinRequests.values()]
    .filter((request) => request.userId === c.user.id && statusMatches(request.status, wanted))
    .sort(byNewest);
  return { applications: applications.map((request) => c.store.applicationJson(request)) };
}

function withdrawApplication(c) {
  const request = c.store.joinRequests.get(c.params.requestId);
  if (!request || request.userId !== c.user.id) throw notFound('join request not found');
  if (c.body.decision !== 'withdraw') throw badRequest('decision must be withdraw', 'validation_failed');
  if (request.status !== 'pending') throw conflict('join request already reviewed');
  request.status = 'withdrawn';
  request.updatedAt = Date.now();
  const room = c.store.rooms.get(request.roomId);
  if (room) c.hub.sendToUsers(roomAdminIds(c.app, room), 'room_join_requests_updated', room.id, { room_id: room.id });
  return { application: c.store.applicationJson(request) };
}

function listNotifications(c) {
  const notifications = [...c.store.notifications.values()]
    .filter((notification) => notification.userId === c.user.id && c.store.rooms.has(notification.roomId))
    .sort(byNewest);
  return { notifications: notifications.map((notification) => c.store.notificationJson(notification)) };
}

/** DELETE /room-notifications/:type/:id — `type` says which list the id belongs to. */
function deleteNotification(c) {
  const { notificationType, notificationId } = c.params;
  const mineRequest = () => {
    const request = c.store.joinRequests.get(notificationId);
    if (!request || request.userId !== c.user.id) throw notFound('room notification not found');
    return request;
  };
  switch (notificationType) {
    case 'room_event': {
      const notification = c.store.notifications.get(notificationId);
      if (!notification || notification.userId !== c.user.id) throw notFound('room notification not found');
      c.store.notifications.delete(notificationId);
      break;
    }
    case 'invite': {
      const invite = c.store.invites.get(notificationId);
      if (!invite || invite.inviteeId !== c.user.id) throw notFound('room notification not found');
      invite.hidden = true;
      break;
    }
    case 'application_requested':
      mineRequest().requestNotificationDeleted = true;
      break;
    case 'application_reviewed':
      mineRequest().reviewNotificationDeleted = true;
      break;
    default:
      throw notFound('room notification not found');
  }
  return ok();
}

function markNotificationsRead(c) {
  const now = Date.now();
  for (const notification of c.store.notifications.values()) {
    if (notification.userId === c.user.id && !notification.readAt) notification.readAt = now;
  }
  emitToUser(c.app, c.user.id, 'room_notifications_updated');
  return ok();
}

export const notificationRoutes = [
  route('GET', '/room-invites', listInvites),
  route('PATCH', '/room-invites/:inviteId', reviewInvite),
  route('GET', '/room-applications', listApplications),
  route('PATCH', '/room-applications/:requestId', withdrawApplication),
  route('GET', '/room-notifications', listNotifications),
  route('POST', '/room-notifications/read', markNotificationsRead),
  route('DELETE', '/room-notifications/:notificationType/:notificationId', deleteNotification),
];
