// Domain side effects shared by route handlers and the background simulation:
// posting messages, system messages and notifications, and fanning the
// resulting realtime events out over SSE.
//
// Event payloads follow lib/src/home/home_shell_realtime.dart:
//   room_updated / room_added   -> RoomCard snapshot (account-scoped) + update_reason
//   room_deleted                -> { room_id }
//   live_participant_*          -> { live: LiveState, participant_count, preview: [UserSummary] }
//   music_box_changed           -> MusicBoxState snapshot
//   music_box_progress          -> { revision, current_item_id, position_ms }
//   room_role_changed           -> { room_id, role }
//   room_*_updated              -> {} (the client refetches)
// There is no message-level event: a new message arrives as `room_updated`
// with `update_reason: "message_created"` (`"message_sent"` for the sender's
// own sessions) and the client reloads messages.
import { musicBoxJson } from './music_box.mjs';
import { nextId } from './util.mjs';

export const memberIds = (room) => [...room.members.keys()];

/** `reason` may be a function of the recipient's user id. */
export function emitRoomUpdated(app, room, reason, userIds = memberIds(room)) {
  app.hub.sendToUsers(userIds, 'room_updated', room.id, (userId) => ({
    ...app.store.roomCard(room, userId),
    update_reason: typeof reason === 'function' ? reason(userId) : reason,
  }));
}

export function emitRoomAdded(app, room, userId) {
  app.hub.sendToUser(userId, 'room_added', room.id, app.store.roomCard(room, userId));
}

export function emitRoomDeleted(app, roomId, userIds) {
  app.hub.sendToUsers(userIds, 'room_deleted', roomId, { room_id: roomId });
}

/** `type` is one of the live snapshot event names (see realtime_live_events.dart). */
export function emitLiveSnapshot(app, room, type, extra = {}) {
  const live = app.store.liveState(room);
  app.hub.sendToUsers(memberIds(room), type, room.id, {
    ...extra,
    live,
    participant_count: live.participant_count,
    preview: app.store.livePreview(room),
  });
}

export function emitMusicBoxChanged(app, room) {
  app.hub.sendToUsers(memberIds(room), 'music_box_changed', room.id, musicBoxJson(app.store, room, { broadcast: true }));
}

export function emitToUser(app, userId, type, roomId = null, data = {}) {
  app.hub.sendToUser(userId, type, roomId, roomId ? { room_id: roomId, ...data } : data);
}

/**
 * Adds a message, broadcasts the room snapshot, notifies mentions and lets
 * gang-bot answer. The client plays its new-message sound for every
 * `message_created` snapshot, so the sender's own sessions get `message_sent`.
 */
export function postMessage(app, room, spec) {
  const message = app.store.addMessage(room, spec);
  emitRoomUpdated(app, room, (userId) => (userId === message.senderId ? 'message_sent' : 'message_created'));
  notifyMentionedUsers(app, room, message);
  maybeReplyAsBot(app, room, message);
  return message;
}

const ROLE_LABELS = { owner: '创建者', admin: '管理员', member: '成员' };
const POLICY_LABELS = { open: '开放加入', approval_required: '需审批', closed: '关闭加入' };

/** Builds the body text the client falls back to for system events. */
function systemBody(store, room, { event, userId, actorId, targetId, toRole, newValue }) {
  const name = (id) => (id ? store.senderLabel(room, id) : '某人');
  switch (event) {
    case 'room_member_joined':
      return `${name(userId)} 加入了房间`;
    case 'room_member_left':
      return `${name(userId)} 退出了房间`;
    case 'room_member_removed':
      return `${name(actorId)} 将 ${name(targetId)} 移出了房间`;
    case 'live_joined':
      return `${name(userId)} 加入了语音`;
    case 'live_left':
      return `${name(userId)} 离开了语音`;
    case 'room_role_changed':
      return `${name(actorId)} 将 ${name(targetId)} 设为${ROLE_LABELS[toRole] ?? toRole}`;
    case 'room_name_changed':
      return `${name(actorId)} 将房间名称修改为“${newValue}”`;
    case 'room_description_changed':
      return `${name(actorId)} 修改了房间简介`;
    case 'room_visibility_changed':
      return `${name(actorId)} 将房间设为${newValue === 'private' ? '私有' : '公开'}`;
    case 'room_join_policy_changed':
      return `${name(actorId)} 将加入方式改为${POLICY_LABELS[newValue] ?? newValue}`;
    default:
      return event;
  }
}

/**
 * Builds a `type: "system"` message spec. Its single attachment carries the
 * structured event (`MessageAttachment.event`, user/actor/target, roles, values).
 */
export function systemMessageSpec(store, room, details) {
  const { event, userId = null, actorId = null, targetId = null, fromRole, toRole, oldValue, newValue, createdAt } = details;
  return {
    senderId: actorId ?? userId ?? targetId,
    type: 'system',
    body: systemBody(store, room, details),
    attachments: [{ type: 'system', event, userId, actorId, targetId, fromRole, toRole, oldValue, newValue }],
    ...(createdAt ? { createdAt } : {}),
  };
}

export function postSystemMessage(app, room, details) {
  return postMessage(app, room, systemMessageSpec(app.store, room, details));
}

/** Stores a `RoomEventNotification` and tells the user's clients to refetch notifications. */
export function addRoomNotification(app, spec) {
  const notification = {
    id: nextId('ntf'),
    createdAt: Date.now(),
    readAt: null,
    actorId: null,
    fromRole: null,
    toRole: null,
    messageId: null,
    messagePreview: null,
    ...spec,
  };
  app.store.notifications.set(notification.id, notification);
  emitToUser(app, notification.userId, 'room_notifications_updated');
  return notification;
}

function notifyMentionedUsers(app, room, message) {
  for (const mention of message.mentions) {
    if (mention.type !== 'user' || !mention.user_id || mention.user_id === message.senderId) continue;
    if (!room.members.has(mention.user_id)) continue;
    addRoomNotification(app, {
      userId: mention.user_id,
      type: 'mentioned',
      roomId: room.id,
      actorId: message.senderId,
      messageId: message.id,
      messagePreview: message.body.slice(0, 80),
    });
  }
}

const BOT_REPLIES = [
  '我是 mock 版 gang-bot：真正的 AI 不在这台服务器上，不过你的消息我收到了 🤖',
  '收到！这是一条来自 mock server 的自动回复。',
  '（mock）我暂时只会复读：',
];

/** gang-bot answers `@gang-bot` mentions (mention type `bot`) after a short delay. */
function maybeReplyAsBot(app, room, message) {
  if (!room.gangBotEnabled || !message.mentions.some((mention) => mention.type === 'bot')) return;
  const bot = [...app.store.users.values()].find((user) => user.isBot);
  if (!bot || message.senderId === bot.id) return;
  const timer = setTimeout(() => {
    if (!app.store.rooms.has(room.id)) return;
    const reply = BOT_REPLIES[room.seq % BOT_REPLIES.length];
    const echo = message.body.replace(/@gang-bot\s*/g, '').trim();
    postMessage(app, room, {
      senderId: bot.id,
      body: reply.endsWith('：') ? `${reply}${echo || '…'}` : reply,
      quotes: [app.store.quoteSpec(room, message)],
    });
  }, 1500);
  timer.unref();
}
