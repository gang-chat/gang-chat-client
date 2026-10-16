// Current user, profiles, account settings, user search, app version, push
// devices, and the superuser-only "forced" user management endpoints.
import { emitRoomUpdated, emitToUser, memberIds } from '../lib/events.mjs';
import { badRequest, conflict, notFound, ok } from '../lib/http.mjs';
import { optionalBool, optionalString, oneOf, requireSuperuser } from '../lib/input.mjs';
import { route } from '../lib/router.mjs';
import { clampInt, foldedIncludes, iso, paginateByCursor } from '../lib/util.mjs';

const GENDERS = ['male', 'female', 'secret', 'other'];

function appVersion(c) {
  return {
    latest_version: c.config.latestVersion,
    minimum_supported_version: '1.0.0',
    release_notes: 'Gang Chat mock server — nothing to download.',
    download_url: null,
  };
}

function me(c) {
  return c.store.currentUser(c.user);
}

/** Lets everyone sharing a room with `user` refresh their member lists. */
function broadcastProfileChange(c, user) {
  for (const room of c.store.rooms.values()) {
    if (!room.members.has(user.id)) continue;
    c.hub.sendToUsers(memberIds(room), 'room_member_profile_changed', room.id, { room_id: room.id, user_id: user.id });
  }
}

function resolveAvatarAsset(c, assetId) {
  const asset = c.store.assets.get(assetId);
  if (!asset || !asset.mimeType.startsWith('image/')) throw notFound('asset not found');
  return asset.id;
}

/** Shared by PATCH /users/me/profile and the superuser settings endpoint. */
function applyProfile(c, user, body) {
  const displayName = optionalString(body, 'display_name');
  if (displayName !== undefined) {
    if (!displayName || displayName.length > 32) throw badRequest('display_name must be 1-32 characters', 'validation_failed');
    user.displayName = displayName;
  }
  const bio = optionalString(body, 'bio');
  if (bio !== undefined) user.bio = bio.slice(0, 200);
  const gender = oneOf(optionalString(body, 'gender'), GENDERS, 'gender');
  if (gender !== undefined) user.gender = gender;
  const defaultAvatarKey = optionalString(body, 'default_avatar_key');
  if (defaultAvatarKey) {
    user.defaultAvatarKey = defaultAvatarKey;
    if (!body.avatar_asset_id) user.avatarAssetId = null;
  }
  if (body.avatar_asset_id) user.avatarAssetId = resolveAvatarAsset(c, body.avatar_asset_id);
}

function applyAccount(c, user, body, { requireEmailToken }) {
  const username = optionalString(body, 'username');
  if (username !== undefined && username !== user.username) {
    if (!/^[A-Za-z0-9_.-]{3,32}$/.test(username)) throw badRequest('invalid username', 'validation_failed');
    const owner = c.store.findUserByLogin(username);
    if (owner && owner.id !== user.id) throw conflict('username or email already taken');
    user.username = username;
    user.usernameUpdatedAt = Date.now();
  }
  const email = optionalString(body, 'email');
  if (email !== undefined && email !== user.email) {
    if (requireEmailToken && !body.email_verification_token) {
      throw badRequest('email verification required', 'email_verification_required');
    }
    const owner = c.store.findUserByLogin(email);
    if (owner && owner.id !== user.id) throw conflict('username or email already taken');
    user.email = email || null;
    user.emailVerified = Boolean(email);
  }
  const phone = optionalString(body, 'phone_number');
  if (phone !== undefined) user.phoneNumber = phone || null;
  const flags = [['email_public', 'emailPublic'], ['phone_number_public', 'phoneNumberPublic']];
  if (!requireEmailToken) flags.push(['email_verified', 'emailVerified']); // superuser override only
  for (const [field, key] of flags) {
    const value = optionalBool(body, field);
    if (value !== undefined) user[key] = value;
  }
  const language = optionalString(body, 'language');
  if (language) user.language = language;
}

function updateProfile(c) {
  applyProfile(c, c.user, c.body);
  broadcastProfileChange(c, c.user);
  return { user: c.store.currentUser(c.user) };
}

function updateAccount(c) {
  applyAccount(c, c.user, c.body, { requireEmailToken: true });
  broadcastProfileChange(c, c.user);
  return { user: c.store.currentUser(c.user) };
}

/** Marks the account deleted, signs it out everywhere and leaves its rooms. */
function deleteAccountOf(c, user) {
  user.status = 'deleted';
  for (const session of c.store.sessions.values()) {
    if (session.userId !== user.id || session.revokedAt) continue;
    c.store.revokeSession(session);
    c.hub.disconnectSession(session.id);
  }
  for (const room of c.store.rooms.values()) {
    if (!room.members.delete(user.id)) continue;
    room.live.participants.delete(user.id);
    emitRoomUpdated(c.app, room, 'member_left');
  }
}

function deleteMyAccount(c) {
  if (c.body.confirm !== true) throw badRequest('confirmation required', 'confirmation_required');
  deleteAccountOf(c, c.user);
  return ok();
}

function searchUsers(c) {
  const query = String(c.query.get('q') ?? '').trim();
  const limit = clampInt(c.query.get('limit'), { min: 1, max: 50, fallback: 20 });
  const includeSuspended = c.user.isSuperuser && c.query.get('include_suspended') === 'true';
  const matches = [...c.store.users.values()].filter(
    (user) =>
      !user.isBot &&
      user.status !== 'deleted' &&
      (includeSuspended || user.status !== 'suspended') &&
      query.length > 0 &&
      (foldedIncludes(user.username, query) ||
        foldedIncludes(user.displayName, query) ||
        user.uid === query ||
        user.email?.toLowerCase() === query.toLowerCase()),
  );
  const { page, nextCursor, total } = paginateByCursor(matches, { cursor: c.query.get('cursor'), limit });
  return {
    users: page.map((user) => c.store.userSummary(user, { commonRoomsFor: c.user.id })),
    next_cursor: nextCursor,
    total_count: total,
  };
}

function userProfile(c) {
  const user = c.store.requireUser(c.params.userId);
  return { profile: { user: c.store.userSummary(user, { commonRoomsFor: c.user.id }) } };
}

// Push devices (Android FCM in the Flutter app; accepted and ignored here) -----

function pushDeviceKey(c) {
  return `${c.user.id}:${c.params.provider}:${c.params.installationId}`;
}

function upsertPushDevice(c) {
  c.store.pushDevices.set(pushDeviceKey(c), { ...c.body, updatedAt: Date.now() });
  return ok();
}

function updatePushDevice(c) {
  const device = c.store.pushDevices.get(pushDeviceKey(c));
  if (!device) throw notFound('push device not found');
  device.enabled = Boolean(c.body.enabled);
  return ok();
}

function deletePushDevice(c) {
  c.store.pushDevices.delete(pushDeviceKey(c));
  return ok();
}

// Superuser user management ----------------------------------------------------

function managedUser(c) {
  requireSuperuser(c.user);
  return c.store.requireUser(c.params.userId);
}

function getForcedSettings(c) {
  return { user: c.store.currentUser(managedUser(c)) };
}

function updateForcedSettings(c) {
  const user = managedUser(c);
  applyAccount(c, user, c.body, { requireEmailToken: false });
  applyProfile(c, user, c.body);
  const status = oneOf(optionalString(c.body, 'status'), ['active', 'suspended'], 'status');
  if (status && status !== user.status) {
    user.status = status;
    if (status === 'suspended') {
      emitToUser(c.app, user.id, 'account_suspended');
      for (const session of c.store.sessions.values()) {
        if (session.userId === user.id && !session.revokedAt) c.store.revokeSession(session);
      }
    }
  }
  broadcastProfileChange(c, user);
  return { user: c.store.currentUser(user) };
}

function forcedSessions(c) {
  const user = managedUser(c);
  const sessions = [...c.store.sessions.values()].filter((session) => session.userId === user.id);
  return { sessions: sessions.map((session) => c.store.sessionJson(session, null)) };
}

function forceDeleteAccount(c) {
  deleteAccountOf(c, managedUser(c));
  return ok();
}

const VOLUME_KEYS = [
  'default_audio_input_volume',
  'default_audio_output_volume',
  'live_mic_input_volume',
  'live_voice_output_volume',
  'live_screen_share_output_volume',
  'live_music_output_volume',
];

function audioSettingsOf(c, user) {
  if (!c.store.audioSettings.has(user.id)) {
    c.store.audioSettings.set(user.id, { ...Object.fromEntries(VOLUME_KEYS.map((key) => [key, 100])), updatedAt: null });
  }
  return c.store.audioSettings.get(user.id);
}

function audioSettingsJson(settings) {
  const { updatedAt, ...volumes } = settings;
  return { ...volumes, updated_at: updatedAt ? iso(updatedAt) : null };
}

function getForcedAudio(c) {
  return { audio_settings: audioSettingsJson(audioSettingsOf(c, managedUser(c))) };
}

function updateForcedAudio(c) {
  const settings = audioSettingsOf(c, managedUser(c));
  for (const key of VOLUME_KEYS) {
    if (c.body[key] !== undefined) settings[key] = clampInt(c.body[key], { min: 0, max: 100, fallback: settings[key] });
  }
  settings.updatedAt = Date.now();
  return { audio_settings: audioSettingsJson(settings) };
}

function forceResetPassword(c) {
  const user = managedUser(c);
  const password = String(c.body.new_password ?? '');
  if (password.length < 6) throw badRequest('password must be at least 6 characters', 'validation_failed');
  user.password = password;
  return ok();
}

export const userRoutes = [
  route('GET', '/app/version', appVersion, { public: true }),
  route('GET', '/me', me),
  route('PUT', '/me/push-devices/:provider/:installationId', upsertPushDevice),
  route('PATCH', '/me/push-devices/:provider/:installationId', updatePushDevice),
  route('DELETE', '/me/push-devices/:provider/:installationId', deletePushDevice),
  route('PATCH', '/users/me/profile', updateProfile),
  route('PATCH', '/users/me/account', updateAccount),
  route('DELETE', '/users/me/account', deleteMyAccount),
  route('GET', '/users/search', searchUsers),
  route('GET', '/users/:userId/profile', userProfile),
  route('GET', '/users/:userId/settings', getForcedSettings),
  route('PATCH', '/users/:userId/settings', updateForcedSettings),
  route('GET', '/users/:userId/sessions', forcedSessions),
  route('DELETE', '/users/:userId/account', forceDeleteAccount),
  route('GET', '/users/:userId/audio-settings', getForcedAudio),
  route('PATCH', '/users/:userId/audio-settings', updateForcedAudio),
  route('POST', '/users/:userId/password', forceResetPassword),
];
