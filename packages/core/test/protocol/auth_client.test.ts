import { describe, expect, it } from 'vitest';

import { AuthClient, AuthException, AuthSession } from '../../src/auth/auth_client';
import { isTlsHandshakeFailure } from '../../src/auth/auth_transport';
import { TokenStore } from '../../src/auth/token_store';
import { ClientException } from '../../src/platform/http';
import { setPlatform, type Platform, type PreferencesStore, type SecureStorage } from '../../src/platform/platform';
import { jsonResponse, MockClient, mockResponse, type MockRequest } from '../support/fake_http_client';

/** Stand-in for `dart:io`'s `HandshakeException`. */
class HandshakeException extends Error {
  override name = 'HandshakeException';
}

const jsonHeaders = { 'content-type': 'application/json; charset=utf-8' };

function authResponse() {
  return jsonResponse(
    {
      access_token: 'access-token',
      refresh_token: 'refresh-token',
      expires_in: 900,
      user: { id: 'user-1', uid: '10000001', username: 'alice', display_name: 'Alice' },
    },
    200,
    jsonHeaders,
  );
}

describe('AuthClient', () => {
  it('auth requests report the supplied client system user agent', async () => {
    let capturedRequest: MockRequest | null = null;
    const client = new AuthClient({
      baseUrl: 'https://api.example.test',
      userAgent: 'GangChat Client (Android)',
      httpClient: new MockClient((request) => {
        capturedRequest = request;
        return authResponse();
      }),
    });

    await client.login({ login: 'alice', password: 'secret' });

    expect(capturedRequest!.headers['user-agent']).toBe('GangChat Client (Android)');
    expect(capturedRequest!.headers['accept']).toBe('application/json');
    expect(capturedRequest!.headers['content-type']).toBe('application/json; charset=utf-8');
    expect(capturedRequest!.headers['authorization']).toBeUndefined();
    expect(JSON.parse(capturedRequest!.body)).toEqual({ login: 'alice', password: 'secret' });
    client.close();
  });

  it('auth checks normalized username availability', async () => {
    let requestedUri: URL | null = null;
    const client = new AuthClient({
      baseUrl: 'https://api.example.test',
      httpClient: new MockClient((request) => {
        requestedUri = request.uri;
        return jsonResponse({ available: false }, 200, jsonHeaders);
      }),
    });

    const available = await client.isUsernameAvailable(' Taken_Name ');

    expect(available).toBe(false);
    expect(requestedUri!.pathname).toBe('/auth/username-availability');
    expect(requestedUri!.searchParams.get('username')).toBe('Taken_Name');
    client.close();
  });

  it('username availability uses the auth TLS fallback chain', async () => {
    let directCalls = 0;
    let fallbackCalls = 0;
    const client = new AuthClient({
      baseUrl: 'https://api.example.test',
      httpClient: new MockClient(() => {
        directCalls += 1;
        throw new HandshakeException('connection terminated');
      }),
      environmentProxyClientFactory: () =>
        new MockClient(() => {
          fallbackCalls += 1;
          return jsonResponse({ available: true }, 200, jsonHeaders);
        }),
      handshakeRetryDelays: [0, 0],
    });

    const available = await client.isUsernameAvailable('available_name');

    expect(available).toBe(true);
    expect(directCalls).toBe(2);
    expect(fallbackCalls).toBe(1);
    client.close();
  });

  it('auth checks normalized email availability', async () => {
    let requestedUri: URL | null = null;
    const client = new AuthClient({
      baseUrl: 'https://api.example.test',
      httpClient: new MockClient((request) => {
        requestedUri = request.uri;
        return jsonResponse({ available: false }, 200, jsonHeaders);
      }),
    });

    const available = await client.isEmailAvailable(' Taken@Example.Test ');

    expect(available).toBe(false);
    expect(requestedUri!.pathname).toBe('/auth/email-availability');
    expect(requestedUri!.searchParams.get('email')).toBe('Taken@Example.Test');
    client.close();
  });

  it('auth retries a TLS handshake failure on the direct client', async () => {
    let calls = 0;
    let fallbackCalls = 0;
    const client = new AuthClient({
      baseUrl: 'https://api.example.test',
      httpClient: new MockClient(() => {
        calls += 1;
        if (calls === 1) throw new HandshakeException('connection terminated');
        return authResponse();
      }),
      environmentProxyClientFactory: () =>
        new MockClient(() => {
          fallbackCalls += 1;
          return authResponse();
        }),
      handshakeRetryDelays: [0, 0],
    });

    const session = await client.login({ login: 'alice', password: 'secret' });

    expect(session.user.username).toBe('alice');
    expect(calls).toBe(2);
    expect(fallbackCalls).toBe(0);
    client.close();
  });

  it('auth falls back to the environment proxy after TLS retries', async () => {
    let directCalls = 0;
    let fallbackCalls = 0;
    const fallbackClients: MockClient[] = [];
    const direct = new MockClient(() => {
      directCalls += 1;
      throw new HandshakeException('connection terminated');
    });
    const client = new AuthClient({
      baseUrl: 'https://api.example.test',
      httpClient: direct,
      environmentProxyClientFactory: () => {
        const fallback = new MockClient(() => {
          fallbackCalls += 1;
          return authResponse();
        });
        fallbackClients.push(fallback);
        return fallback;
      },
      handshakeRetryDelays: [0, 0],
    });

    const session = await client.login({ login: 'alice', password: 'secret' });

    expect(session.user.username).toBe('alice');
    expect(directCalls).toBe(2);
    expect(fallbackCalls).toBe(1);
    client.close();
    expect(direct.closed).toBe(true);
    expect(fallbackClients).toHaveLength(1);
    expect(fallbackClients[0]!.closed).toBe(true);
  });

  it('auth does not retry unrelated transport failures', async () => {
    let directCalls = 0;
    let fallbackCalls = 0;
    const client = new AuthClient({
      baseUrl: 'https://api.example.test',
      httpClient: new MockClient((request) => {
        directCalls += 1;
        throw new ClientException('connection reset', request.url);
      }),
      environmentProxyClientFactory: () =>
        new MockClient(() => {
          fallbackCalls += 1;
          return authResponse();
        }),
      handshakeRetryDelays: [0, 0],
    });

    await expect(client.login({ login: 'alice', password: 'secret' })).rejects.toBeInstanceOf(ClientException);

    expect(directCalls).toBe(1);
    expect(fallbackCalls).toBe(0);
    client.close();
  });

  it('registration email verification sends the complete protocol', async () => {
    const requests: MockRequest[] = [];
    const client = new AuthClient({
      baseUrl: 'https://api.example.test/api/v1',
      httpClient: new MockClient((request) => {
        requests.push(request);
        switch (request.uri.pathname) {
          case '/api/v1/auth/email-verification/inspect':
            return jsonResponse({ can_send: true, retry_after: 0 }, 200, jsonHeaders);
          case '/api/v1/auth/email-verification/start':
          case '/api/v1/auth/email-verification/resend':
            return jsonResponse({ challenge_id: 'email-challenge', retry_after: 60 }, 200, jsonHeaders);
          case '/api/v1/auth/email-verification/verify':
            return jsonResponse({ verification_token: 'verified-email-token' }, 200, jsonHeaders);
          case '/api/v1/auth/register':
            return authResponse();
        }
        return mockResponse('not found', 404);
      }),
    });

    const inspection = await client.inspectEmailVerification(' kai@example.test ');
    const started = await client.startEmailVerification('kai@example.test');
    const resent = await client.resendEmailVerificationCode(started.id);
    const token = await client.verifyEmailVerificationCode({ challengeId: resent.id, code: '123456' });
    await client.register({
      username: 'kai',
      email: 'kai@example.test',
      password: 'secret-password',
      emailVerificationToken: token,
    });

    expect(inspection.canSend).toBe(true);
    expect(started.retryAfterSeconds).toBe(60);
    expect(token).toBe('verified-email-token');
    expect(JSON.parse(requests[0]!.body)).toEqual({ email: 'kai@example.test' });
    expect(JSON.parse(requests[3]!.body)).toEqual({ challenge_id: 'email-challenge', code: '123456' });
    expect(JSON.parse(requests[requests.length - 1]!.body)).toEqual({
      username: 'kai',
      email: 'kai@example.test',
      password: 'secret-password',
      email_verification_token: 'verified-email-token',
    });
    client.close();
  });

  it('authenticated email verification uses the current-user routes', async () => {
    const requests: MockRequest[] = [];
    const client = new AuthClient({
      baseUrl: 'https://api.example.test/api/v1',
      httpClient: new MockClient((request) => {
        requests.push(request);
        if (request.uri.pathname.endsWith('/inspect')) {
          return jsonResponse({ can_send: true, retry_after: 0 });
        }
        return jsonResponse({ challenge_id: 'challenge', retry_after: 60 });
      }),
    });

    await client.inspectEmailVerification('bound@example.test', { accessToken: 'access-token' });
    await client.startEmailVerification('bound@example.test', { accessToken: 'access-token' });

    expect(requests.map((request) => request.uri.pathname)).toEqual([
      '/api/v1/users/me/email-verification/inspect',
      '/api/v1/users/me/email-verification/start',
    ]);
    expect(requests.every((request) => request.headers['authorization'] === 'Bearer access-token')).toBe(true);
    client.close();
  });

  it('password reset client sends the complete protocol payloads', async () => {
    const requests: MockRequest[] = [];
    const client = new AuthClient({
      baseUrl: 'https://api.example.test/api/v1',
      httpClient: new MockClient((request) => {
        requests.push(request);
        switch (request.uri.pathname) {
          case '/api/v1/auth/password-reset/inspect':
            return jsonResponse({ can_send: true, masked_email: 'k***@example.test', retry_after: 0 }, 200, jsonHeaders);
          case '/api/v1/auth/password-reset/start':
          case '/api/v1/auth/password-reset/resend':
            return jsonResponse(
              { challenge_id: 'challenge-1', masked_email: 'k***@example.test', retry_after: 60 },
              200,
              jsonHeaders,
            );
          case '/api/v1/auth/password-reset/verify':
            return jsonResponse({ reset_token: 'reset-token' }, 200, jsonHeaders);
          case '/api/v1/auth/password-reset/complete':
          case '/api/v1/auth/password-reset/claim':
            return jsonResponse({ ok: true }, 200, jsonHeaders);
        }
        return mockResponse('not found', 404);
      }),
    });

    const inspection = await client.inspectPasswordReset(' kai ');
    const started = await client.startPasswordReset(' kai ');
    const resent = await client.resendPasswordResetCode(started.id);
    const token = await client.verifyPasswordResetCode({ challengeId: started.id, code: '123456' });
    await client.completePasswordReset({ resetToken: token, newPassword: 'new-password' });
    await client.claimPasswordResetForSession({ accessToken: 'access-token', resetToken: token });

    expect(inspection.canSend).toBe(true);
    expect(started.maskedEmail).toBe('k***@example.test');
    expect(resent.retryAfterSeconds).toBe(60);
    expect(token).toBe('reset-token');
    expect(JSON.parse(requests[0]!.body)).toEqual({ login: 'kai' });
    expect(JSON.parse(requests[3]!.body)).toEqual({ challenge_id: 'challenge-1', code: '123456' });
    expect(requests[requests.length - 1]!.headers['authorization']).toBe('Bearer access-token');
    client.close();
  });

  it('logout always sends the refresh_token key', async () => {
    const bodies: unknown[] = [];
    const client = new AuthClient({
      baseUrl: 'https://api.example.test',
      httpClient: new MockClient((request) => {
        bodies.push(JSON.parse(request.body));
        return jsonResponse({ ok: true });
      }),
    });

    await client.logout();
    await client.logout({ accessToken: 'access', refreshToken: 'refresh' });

    expect(bodies).toEqual([{ refresh_token: null }, { refresh_token: 'refresh' }]);
    client.close();
  });

  it('AuthException localizes the server error envelope', async () => {
    const client = new AuthClient({
      baseUrl: 'https://api.example.test',
      httpClient: new MockClient(() =>
        jsonResponse({ error: { code: 'invalid_credentials', message: '账号或密码不正确' } }, 401),
      ),
    });

    const error = (await client.login({ login: 'alice', password: 'bad' }).catch((e: unknown) => e)) as AuthException;

    expect(error).toBeInstanceOf(AuthException);
    expect(error.statusCode).toBe(401);
    expect(error.code).toBe('invalid_credentials');
    expect(error.isUnauthorized).toBe(true);
    expect(error.toString()).toBe('账号或密码不正确');
    client.close();
  });

  it('AuthException falls back to the status message for non-JSON bodies', async () => {
    const client = new AuthClient({
      baseUrl: 'https://api.example.test',
      httpClient: new MockClient(() => mockResponse('<html>bad gateway</html>', 502)),
    });

    const error = (await client.me('access').catch((e: unknown) => e)) as AuthException;

    expect(error).toBeInstanceOf(AuthException);
    expect(error.code).toBe('request_failed');
    expect(error.message).toBe('请求失败 (502)');
    client.close();
  });
});

describe('AuthSession', () => {
  const user = { id: 'user-1', uid: '10000001', username: 'alice', display_name: 'Alice' };

  it('prefers the absolute access token expiry', () => {
    const session = AuthSession.fromJson({
      access_token: 'a',
      refresh_token: 'r',
      access_token_expires_at: '2030-01-01T00:00:00Z',
      expires_in: 5,
      user,
    });

    expect(session.accessTokenExpiresAt.toISOString()).toBe('2030-01-01T00:00:00.000Z');
    expect(session.isAccessTokenExpiringSoon()).toBe(false);
  });

  it('derives the expiry from a numeric string expires_in and falls back to 15 minutes', () => {
    const before = Date.now();
    const fromString = AuthSession.fromJson({ access_token: 'a', refresh_token: 'r', expires_in: '120', user });
    const fallback = AuthSession.fromJson({ access_token: 'a', refresh_token: 'r', expires_in: 'soon', user });
    const after = Date.now();

    expect(fromString.accessTokenExpiresAt.getTime()).toBeGreaterThanOrEqual(before + 120_000);
    expect(fromString.accessTokenExpiresAt.getTime()).toBeLessThanOrEqual(after + 120_000);
    expect(fromString.isAccessTokenExpiringSoon()).toBe(false);
    expect(fromString.isAccessTokenExpiringSoon({ threshold: 180_000 })).toBe(true);
    expect(fallback.accessTokenExpiresAt.getTime()).toBeGreaterThanOrEqual(before + 15 * 60_000);
  });
});

describe('isTlsHandshakeFailure', () => {
  it('recognises handshake failures by name and message only', () => {
    expect(isTlsHandshakeFailure(new HandshakeException('connection terminated'))).toBe(true);
    expect(isTlsHandshakeFailure(new ClientException('HandshakeException: Handshake error in client'))).toBe(true);
    expect(isTlsHandshakeFailure(new Error('net::ERR_SSL_PROTOCOL_ERROR'))).toBe(true);
    expect(
      isTlsHandshakeFailure(new Error('Client network socket disconnected before secure TLS connection was established')),
    ).toBe(true);
    expect(isTlsHandshakeFailure(new ClientException('connection reset'))).toBe(false);
    expect(isTlsHandshakeFailure('timeout')).toBe(false);
  });
});

describe('TokenStore', () => {
  it('keeps the refresh token in secure storage and the base URL in preferences', async () => {
    const secure = new Map<string, string>();
    const prefs = new Map<string, string>();
    const secureStorage: SecureStorage = {
      read: async (key) => secure.get(key) ?? null,
      write: async (key, value) => {
        secure.set(key, value);
      },
      delete: async (key) => {
        secure.delete(key);
      },
    };
    const preferences: Pick<PreferencesStore, 'getString' | 'setString'> = {
      getString: (key) => prefs.get(key) ?? null,
      setString: async (key, value) => {
        prefs.set(key, value);
      },
    };
    // Only the members TokenStore touches are provided.
    setPlatform({ secureStorage, preferences } as unknown as Platform);
    const store = new TokenStore();

    expect(await store.readRefreshToken()).toBeNull();
    await store.writeRefreshToken('refresh-1');
    await store.writeApiBaseUrl('https://api.example.test');

    expect(secure.get('gang.refreshToken')).toBe('refresh-1');
    expect(prefs.get('gang.apiBaseUrl')).toBe('https://api.example.test');
    expect(await store.readRefreshToken()).toBe('refresh-1');
    expect(await store.readApiBaseUrl()).toBe('https://api.example.test');

    await store.clearRefreshToken();
    expect(await store.readRefreshToken()).toBeNull();
  });
});
