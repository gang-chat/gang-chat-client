// Live (voice) channel. Joining returns a LiveJoinResult whose LiveKit URL
// points at a server that does not exist (default ws://127.0.0.1:7880), so the
// client's media connection fails and its error path can be exercised while
// the roster, mute toggles and moderation still round-trip through this mock.
// With --livekit-api-key/--livekit-api-secret the tokens are signed, so a
// local `livekit-server --dev` accepts them and real media flows.
import { createHmac } from 'node:crypto';

import { emitLiveSnapshot, postSystemMessage } from '../lib/events.mjs';
import { badRequest, conflict, forbidden, notFound, ok } from '../lib/http.mjs';
import { optionalBool } from '../lib/input.mjs';
import { leaveLive } from '../lib/room_ops.mjs';
import { route } from '../lib/router.mjs';
import { clampInt, iso } from '../lib/util.mjs';

const TOKEN_TTL_MS = 6 * 60 * 60 * 1000;

/**
 * A LiveKit access token. Unsigned (JWT-shaped, nothing verifies it) unless
 * the server was started with a LiveKit API key and secret.
 */
function liveKitToken(config, { identity, name, roomName, expiresAt, canPublishOnly = false }) {
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
  const claims = {
    iss: config.livekitApiKey ?? 'gang-chat-mock',
    sub: identity,
    name,
    nbf: Math.floor(Date.now() / 1000),
    exp: Math.floor(expiresAt / 1000),
    video: { room: roomName, roomJoin: true, canPublish: true, canSubscribe: !canPublishOnly },
  };
  const unsigned = `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(claims)}`;
  if (config.livekitApiKey == null || config.livekitApiSecret == null) return `${unsigned}.mock-signature`;
  return `${unsigned}.${createHmac('sha256', config.livekitApiSecret).update(unsigned).digest('base64url')}`;
}

const roomNameFor = (room) => `gang-${room.id}`;

function memberRoom(c) {
  const room = c.store.requireRoom(c.params.roomId);
  c.store.requireMember(room, c.user);
  return room;
}

function getLive(c) {
  return { live: c.store.liveState(memberRoom(c)) };
}

/** POST /rooms/:id/live/join — also leaves any other room's live channel (one call at a time). */
function joinLive(c) {
  const room = memberRoom(c);
  for (const other of c.store.rooms.values()) {
    if (other !== room && other.live.participants.has(c.user.id)) leaveLive(c.app, other, c.user.id);
  }
  const rejoin = room.live.participants.get(c.user.id);
  const overrides = {
    micMuted: c.body.mic_muted === true || Boolean(rejoin?.voiceBlocked),
    headphonesMuted: c.body.headphones_muted === true,
    voiceBlocked: rejoin?.voiceBlocked ?? false,
    connectionState: 'joining',
  };
  const clientSessionId = String(c.body.client_live_session_id ?? '').trim();
  if (clientSessionId) overrides.liveSessionId = clientSessionId;
  room.live.participants.set(c.user.id, c.store.newParticipant(c.user.id, overrides));
  room.live.updatedAt = Date.now();
  emitLiveSnapshot(c.app, room, 'live_participant_joined', { user_id: c.user.id });
  if (!rejoin) postSystemMessage(c.app, room, { event: 'live_joined', userId: c.user.id });

  const expiresAt = Date.now() + TOKEN_TTL_MS;
  const roomName = roomNameFor(room);
  const signed = c.config.livekitApiKey != null;
  c.app.log(`[live] ${c.user.username} joined ${room.id}; LiveKit ${c.config.livekitUrl}${signed ? '' : ' (expected to fail)'}`);
  return {
    livekit: {
      server_url: c.config.livekitUrl,
      token: liveKitToken(c.config, { identity: c.user.id, name: c.user.displayName, roomName, expiresAt }),
      token_expires_at: iso(expiresAt),
      room_name: roomName,
    },
    participant: c.store.participantJson(room, room.live.participants.get(c.user.id)),
    live: c.store.liveState(room),
  };
}

/** PATCH /rooms/:id/live/me — mute/camera/screen toggles; `connection_state: "left"` leaves. */
function updateMyLiveState(c) {
  const room = memberRoom(c);
  const participant = room.live.participants.get(c.user.id);
  if (c.body.connection_state === 'left') {
    const left = participant ? leaveLive(c.app, room, c.user.id) : c.store.newParticipant(c.user.id);
    return { participant: { ...c.store.participantJson(room, left), connection_state: 'left' } };
  }
  if (!participant) throw notFound('not in live');
  if (participant.voiceBlocked && c.body.mic_muted === false) throw forbidden('user is muted in this room');
  const fields = {
    mic_muted: 'micMuted',
    headphones_muted: 'headphonesMuted',
    camera_on: 'cameraOn',
    camera_mirrored: 'cameraMirrored',
    screen_sharing: 'screenSharing',
  };
  for (const [field, key] of Object.entries(fields)) {
    const value = optionalBool(c.body, field);
    if (value !== undefined) participant[key] = value;
  }
  if (!participant.screenSharing) participant.screenViewerIds.clear();
  if (typeof c.body.connection_state === 'string' && c.body.connection_state) {
    participant.connectionState = c.body.connection_state === 'online' ? 'connected' : c.body.connection_state;
  }
  room.live.updatedAt = Date.now();
  emitLiveSnapshot(c.app, room, 'live_participant_updated', { user_id: c.user.id });
  return { participant: c.store.participantJson(room, participant) };
}

function updateScreenView(c) {
  const room = memberRoom(c);
  if (!room.live.participants.has(c.user.id)) throw notFound('not in live');
  const broadcasterId = String(c.body.broadcaster_user_id ?? '');
  if (broadcasterId === c.user.id) throw badRequest('cannot watch your own screen');
  for (const participant of room.live.participants.values()) participant.screenViewerIds.delete(c.user.id);
  if (broadcasterId) {
    const broadcaster = room.live.participants.get(broadcasterId);
    if (!broadcaster?.screenSharing) throw conflict('screen share is not active', 'screen_share_not_active');
    broadcaster.screenViewerIds.add(c.user.id);
  }
  room.live.updatedAt = Date.now();
  emitLiveSnapshot(c.app, room, 'live_participant_updated', { user_id: c.user.id });
  return ok();
}

function screenAudioToken(c) {
  const room = memberRoom(c);
  if (!room.live.participants.has(c.user.id)) throw notFound('not in live');
  const identity = `${c.user.id}--screen-audio`;
  const expiresAt = Date.now() + TOKEN_TTL_MS;
  return {
    server_url: c.config.livekitUrl,
    token: liveKitToken(c.config, { identity, name: identity, roomName: roomNameFor(room), expiresAt, canPublishOnly: true }),
    token_expires_at: iso(expiresAt),
    room_name: roomNameFor(room),
    identity,
  };
}

function memberVolumeJson(c, room, targetId, entry) {
  return {
    room_id: room.id,
    target_user: c.store.userSummary(c.store.users.get(targetId)),
    volume: entry.volume,
    updated_at: iso(entry.updatedAt),
  };
}

function listMemberVolumes(c) {
  const room = memberRoom(c);
  const prefix = `${room.id}:${c.user.id}:`;
  const volumes = [...c.store.memberVolumes]
    .filter(([key]) => key.startsWith(prefix))
    .map(([key, entry]) => memberVolumeJson(c, room, key.slice(prefix.length), entry));
  return { member_volumes: volumes };
}

function updateMemberVolume(c) {
  const room = memberRoom(c);
  const target = c.store.requireUser(c.params.targetUserId);
  const entry = { volume: clampInt(c.body.volume, { min: 0, max: 100, fallback: 100 }), updatedAt: Date.now() };
  c.store.memberVolumes.set(`${room.id}:${c.user.id}:${target.id}`, entry);
  return { member_volume: memberVolumeJson(c, room, target.id, entry) };
}

/** Admin moderation: kick | mute_mic | block_voice | restore_voice | restore_headphones. */
function moderateParticipant(c) {
  const room = c.store.requireRoom(c.params.roomId);
  c.store.requireAdmin(room, c.user);
  const participant = room.live.participants.get(c.params.userId);
  if (!participant) throw notFound('participant not found');
  const action = String(c.body.action ?? '');
  const extra = { action, target_user_id: participant.userId, actor_user_id: c.user.id };
  switch (action) {
    case 'kick':
      room.live.participants.delete(participant.userId);
      break;
    case 'mute_mic':
    case 'block_voice':
      Object.assign(participant, { micMuted: true, voiceBlocked: true });
      break;
    case 'restore_voice':
      Object.assign(participant, { voiceBlocked: false, micBlocked: false });
      break;
    case 'restore_headphones':
      Object.assign(participant, { headphonesBlocked: false, headphonesMuted: false });
      break;
    default:
      throw badRequest(`unknown moderation action: ${action}`, 'validation_failed');
  }
  room.live.updatedAt = Date.now();
  emitLiveSnapshot(c.app, room, 'live_participant_moderated', extra);
  return ok();
}

export const liveRoutes = [
  route('GET', '/rooms/:roomId/live', getLive),
  route('POST', '/rooms/:roomId/live/join', joinLive),
  route('PATCH', '/rooms/:roomId/live/me', updateMyLiveState),
  route('PATCH', '/rooms/:roomId/live/me/screen-view', updateScreenView),
  route('POST', '/rooms/:roomId/live/screen-audio-token', screenAudioToken),
  route('GET', '/rooms/:roomId/live/me/member-volumes', listMemberVolumes),
  route('PATCH', '/rooms/:roomId/live/me/member-volumes/:targetUserId', updateMemberVolume),
  route('POST', '/rooms/:roomId/live/participants/:userId/moderation', moderateParticipant),
];
