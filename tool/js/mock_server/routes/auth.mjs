// Auth endpoints (packages/core/src/auth/auth_client.ts): login/register/refresh/logout,
// email verification and password reset (the code is always 123456), sessions
// and password change.
//
// Accounts: every seeded username accepts `demo1234` (e.g. demo / demo1234),
// and any email-shaped login is accepted with any password, creating the
// account on first use.
import { ApiError, badRequest, conflict, created, forbidden, notFound, ok, unauthorized } from '../lib/http.mjs';
import { requiredString } from '../lib/input.mjs';
import { route } from '../lib/router.mjs';
import { onboardNewUser } from '../lib/seed.mjs';
import { isEmailLike, maskEmail, nextId, randomToken } from '../lib/util.mjs';

export const VERIFICATION_CODE = '123456';

function sessionMeta(c) {
  return { userAgent: c.req.headers['user-agent'] ?? null, ip: c.req.socket.remoteAddress ?? null };
}

function uniqueUsername(store, base) {
  const cleaned = base.toLowerCase().replace(/[^a-z0-9_.-]/g, '').slice(0, 24) || 'user';
  let candidate = cleaned;
  for (let n = 2; store.findUserByLogin(candidate); n++) candidate = `${cleaned}${n}`;
  return candidate;
}

function createAccount(c, { username, email, password }) {
  const user = c.store.addUser({
    id: nextId('usr'),
    username,
    displayName: username,
    email,
    emailVerified: true,
    password,
    createdAt: Date.now(),
  });
  onboardNewUser(c.store, user);
  c.app.log(`[auth] created account ${username} <${email}>`);
  return user;
}

function login(c) {
  const loginName = requiredString(c.body, 'login', { message: 'login is required' });
  const password = String(c.body.password ?? '');
  if (!password) throw badRequest('password is required', 'validation_failed');
  let user = c.store.findUserByLogin(loginName);
  if (isEmailLike(loginName)) {
    user ??= createAccount(c, { username: uniqueUsername(c.store, loginName.split('@')[0]), email: loginName, password });
  } else if (!user || user.password !== password) {
    throw new ApiError(401, 'invalid_credentials', 'invalid credentials');
  }
  if (user.status === 'suspended') throw forbidden('account suspended', 'account_suspended');
  const session = c.store.createSession(user.id, sessionMeta(c));
  c.app.log(`[auth] login ${user.username} (session ${session.id})`);
  return c.store.authSession(session);
}

function register(c) {
  const username = requiredString(c.body, 'username', { max: 32 });
  const email = requiredString(c.body, 'email', { max: 254 });
  const password = requiredString(c.body, 'password', { max: 128 });
  if (!c.body.email_verification_token) {
    throw badRequest('email verification required', 'email_verification_required');
  }
  if (password.length < 6) throw badRequest('password must be at least 6 characters', 'validation_failed');
  if (c.store.findUserByLogin(username) || c.store.findUserByLogin(email)) {
    throw conflict('username or email already taken');
  }
  const user = createAccount(c, { username, email, password });
  return created(c.store.authSession(c.store.createSession(user.id, sessionMeta(c))));
}

function refresh(c) {
  const session = c.store.refreshSession(String(c.body.refresh_token ?? ''));
  if (!session) throw unauthorized('invalid refresh token');
  return c.store.authSession(session);
}

function logout(c) {
  const refreshToken = c.body.refresh_token;
  const session =
    c.session ?? [...c.store.sessions.values()].find((candidate) => refreshToken && candidate.refreshToken === refreshToken);
  if (session && !session.revokedAt) {
    c.store.revokeSession(session);
    c.hub.disconnectSession(session.id);
  }
  return ok();
}

function usernameAvailability(c) {
  const username = String(c.query.get('username') ?? '').trim();
  return { available: username.length >= 3 && !c.store.findUserByLogin(username) };
}

function emailAvailability(c) {
  const email = String(c.query.get('email') ?? '').trim();
  return { available: isEmailLike(email) && !c.store.findUserByLogin(email) };
}

// Email verification -----------------------------------------------------------

function inspectEmailVerification() {
  return { can_send: true, retry_after: 0, challenge_id: null };
}

function startEmailVerification(c) {
  const email = requiredString(c.body, 'email');
  if (!isEmailLike(email)) throw badRequest('invalid email', 'validation_failed');
  const challenge = { id: nextId('evc'), email, createdAt: Date.now() };
  c.store.emailChallenges.set(challenge.id, challenge);
  c.app.log(`[auth] email verification for ${email}: code ${VERIFICATION_CODE}`);
  return { challenge_id: challenge.id, retry_after: 60 };
}

function resendEmailVerification(c) {
  const challenge = c.store.emailChallenges.get(String(c.body.challenge_id ?? ''));
  if (!challenge) throw badRequest('challenge not found', 'challenge_not_found');
  return { challenge_id: challenge.id, retry_after: 60 };
}

function verifyEmailVerification(c) {
  const challenge = c.store.emailChallenges.get(String(c.body.challenge_id ?? ''));
  if (!challenge) throw badRequest('challenge not found', 'challenge_not_found');
  if (String(c.body.code ?? '').trim() !== VERIFICATION_CODE) {
    throw badRequest('invalid verification code', 'invalid_verification_code');
  }
  const token = randomToken('mock-evt');
  c.store.verificationTokens.set(token, challenge.email);
  return { verification_token: token };
}

// Password reset ---------------------------------------------------------------

function resetTarget(c) {
  const user = c.store.findUserByLogin(requiredString(c.body, 'login'));
  if (!user) throw notFound('account not found', 'account_not_found');
  if (!user.email) throw badRequest('password reset requires a bound email', 'password_reset_verification_required');
  return user;
}

function inspectPasswordReset(c) {
  const user = resetTarget(c);
  return { can_send: true, masked_email: maskEmail(user.email), retry_after: 0, challenge_id: null };
}

function startPasswordReset(c) {
  const user = resetTarget(c);
  const challenge = { id: nextId('prc'), userId: user.id, createdAt: Date.now() };
  c.store.resetChallenges.set(challenge.id, challenge);
  c.app.log(`[auth] password reset for ${user.username}: code ${VERIFICATION_CODE}`);
  return { challenge_id: challenge.id, masked_email: maskEmail(user.email), retry_after: 60 };
}

function resendPasswordReset(c) {
  const challenge = c.store.resetChallenges.get(String(c.body.challenge_id ?? ''));
  if (!challenge) throw badRequest('challenge not found', 'challenge_not_found');
  return { challenge_id: challenge.id, masked_email: maskEmail(c.store.users.get(challenge.userId).email), retry_after: 60 };
}

function verifyPasswordReset(c) {
  const challenge = c.store.resetChallenges.get(String(c.body.challenge_id ?? ''));
  if (!challenge) throw badRequest('challenge not found', 'challenge_not_found');
  if (String(c.body.code ?? '').trim() !== VERIFICATION_CODE) {
    throw badRequest('invalid verification code', 'invalid_verification_code');
  }
  const token = randomToken('mock-rst');
  c.store.resetTokens.set(token, challenge.userId);
  return { reset_token: token };
}

function completePasswordReset(c) {
  const userId = c.store.resetTokens.get(String(c.body.reset_token ?? ''));
  if (!userId) throw badRequest('reset token expired', 'verification_expired');
  const password = requiredString(c.body, 'new_password');
  const user = c.store.users.get(userId);
  user.password = password;
  for (const session of c.store.sessions.values()) {
    if (session.userId === userId && !session.revokedAt) c.store.revokeSession(session);
  }
  return ok();
}

function claimPasswordReset(c) {
  if (!c.store.resetTokens.has(String(c.body.reset_token ?? ''))) {
    throw badRequest('reset token expired', 'verification_expired');
  }
  return ok();
}

// Sessions and password --------------------------------------------------------

function listSessions(c) {
  return [...c.store.sessions.values()]
    .filter((session) => session.userId === c.user.id)
    .sort((a, b) => b.lastUsedAt - a.lastUsedAt)
    .map((session) => c.store.sessionJson(session, c.session.id));
}

function revokeSession(c) {
  const session = c.store.sessions.get(c.params.sessionId);
  if (!session || session.userId !== c.user.id) throw notFound('session not found');
  c.store.revokeSession(session);
  c.hub.disconnectSession(session.id);
  return ok();
}

function changePassword(c) {
  if (String(c.body.current_password ?? '') !== c.user.password) {
    throw badRequest('current password incorrect', 'invalid_password');
  }
  const next = requiredString(c.body, 'new_password', { max: 128 });
  if (next.length < 6) throw badRequest('password must be at least 6 characters', 'validation_failed');
  c.user.password = next;
  if (c.body.revoke_other_sessions !== false) {
    for (const session of c.store.sessions.values()) {
      if (session.userId !== c.user.id || session.id === c.session.id || session.revokedAt) continue;
      c.store.revokeSession(session);
      c.hub.disconnectSession(session.id);
    }
  }
  return ok();
}

const open = { public: true };

export const authRoutes = [
  route('POST', '/auth/login', login, open),
  route('POST', '/auth/register', register, open),
  route('POST', '/auth/refresh', refresh, open),
  route('POST', '/auth/logout', logout, open),
  route('GET', '/auth/username-availability', usernameAvailability, open),
  route('GET', '/auth/email-availability', emailAvailability, open),
  route('POST', '/auth/email-verification/inspect', inspectEmailVerification, open),
  route('POST', '/auth/email-verification/start', startEmailVerification, open),
  route('POST', '/auth/email-verification/resend', resendEmailVerification, open),
  route('POST', '/auth/email-verification/verify', verifyEmailVerification, open),
  route('POST', '/users/me/email-verification/inspect', inspectEmailVerification),
  route('POST', '/users/me/email-verification/start', startEmailVerification),
  route('POST', '/auth/password-reset/inspect', inspectPasswordReset, open),
  route('POST', '/auth/password-reset/start', startPasswordReset, open),
  route('POST', '/auth/password-reset/resend', resendPasswordReset, open),
  route('POST', '/auth/password-reset/verify', verifyPasswordReset, open),
  route('POST', '/auth/password-reset/complete', completePasswordReset, open),
  route('POST', '/auth/password-reset/claim', claimPasswordReset),
  route('GET', '/auth/sessions', listSessions),
  route('DELETE', '/auth/sessions/:sessionId', revokeSession),
  route('POST', '/auth/password', changePassword),
];
