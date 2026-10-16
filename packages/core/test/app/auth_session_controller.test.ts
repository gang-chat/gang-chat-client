import { describe, expect, test } from 'vitest';

import { AuthRestoreStatus, AuthSessionController } from '../../src/app/auth_session_controller';
import { AuthClient, AuthException, AuthSession } from '../../src/auth/auth_client';
import { TokenStore } from '../../src/auth/token_store';
import { CurrentUser } from '../../src/protocol/models';
import { MockClient, mockResponse } from '../support/fake_http_client';

class _FakeTokenStore extends TokenStore {
  constructor(public refreshToken: string | null = null) {
    super();
  }

  apiBaseUrl: string | null = null;
  clearCount = 0;
  failWrites = false;

  override async readRefreshToken(): Promise<string | null> {
    return this.refreshToken;
  }

  override async writeRefreshToken(refreshToken: string): Promise<void> {
    if (this.failWrites) throw new Error('keychain unavailable');
    this.refreshToken = refreshToken;
  }

  override async writeApiBaseUrl(baseUrl: string): Promise<void> {
    this.apiBaseUrl = baseUrl;
  }

  override async clearRefreshToken(): Promise<void> {
    this.clearCount += 1;
    this.refreshToken = null;
  }
}

class _FakeAuthClient extends AuthClient {
  constructor(private readonly onRefresh: ((refreshToken: string) => Promise<AuthSession>) | null = null) {
    super({ baseUrl: 'https://unused.test', httpClient: new MockClient(() => mockResponse('', 404)) });
  }

  override refresh(refreshToken: string): Promise<AuthSession> {
    const refresh = this.onRefresh;
    if (refresh == null) return super.refresh(refreshToken);
    return refresh(refreshToken);
  }
}

const day = 24 * 60 * 60 * 1000;

const _session = new AuthSession({
  accessToken: 'access_1',
  refreshToken: 'refresh_1',
  accessTokenExpiresAt: new Date(Date.now() + day),
  user: new CurrentUser({
    id: 'user_1',
    uid: '1001',
    username: 'alice',
    displayName: 'Alice',
    bio: '',
    gender: 'secret',
    email: 'alice@example.test',
    emailPublic: false,
    phoneNumber: null,
    phoneNumberPublic: false,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
    isSuperuser: false,
    createdAt: new Date(Date.UTC(2026, 5, 1)),
  }),
});

const _rotatedSession = new AuthSession({
  accessToken: 'access_2',
  refreshToken: 'refresh_2',
  accessTokenExpiresAt: new Date(Date.now() + day),
  user: _session.user,
});

const _unauthorized = () => new AuthException('session expired', { statusCode: 401, code: 'unauthorized' });

describe('AuthSessionController', () => {
  test('notifies listeners without Flutter notifier', async () => {
    const store = new _FakeTokenStore();
    const controller = new AuthSessionController({
      tokenStore: store,
      apiBaseUrl: 'https://api.example.test',
      authClientFactory: () => new _FakeAuthClient(),
    });
    let notifications = 0;
    const listener = () => {
      notifications += 1;
    };

    controller.addListener(listener);

    await controller.acceptSession(_session);
    expect(notifications).toBe(1);
    expect(controller.session).toBe(_session);
    expect(store.refreshToken).toBe('refresh_1');
    expect(store.apiBaseUrl).toBe('https://api.example.test');

    const loggedOut = controller.beginLogout();
    expect(loggedOut).toBe(_session);
    expect(notifications).toBe(2);
    expect(controller.session).toBeNull();

    controller.removeListener(listener);
    await controller.acceptSession(_session);
    expect(notifications).toBe(2);
    controller.dispose();
  });

  test('transient access refresh failure keeps the active session', async () => {
    const store = new _FakeTokenStore();
    const client = new _FakeAuthClient(async () => {
      throw new Error('offline');
    });
    const controller = new AuthSessionController({
      tokenStore: store,
      apiBaseUrl: 'https://api.example.test',
      authClientFactory: () => client,
    });

    await controller.acceptSession(_session);

    await expect(controller.accessToken({ forceRefresh: true })).rejects.toThrow('offline');
    expect(controller.session).toBe(_session);
    expect(store.refreshToken).toBe('refresh_1');
    expect(store.clearCount).toBe(0);

    controller.dispose();
  });

  test('unauthorized access refresh clears the active session', async () => {
    const store = new _FakeTokenStore();
    const client = new _FakeAuthClient(async () => {
      throw _unauthorized();
    });
    const controller = new AuthSessionController({
      tokenStore: store,
      apiBaseUrl: 'https://api.example.test',
      authClientFactory: () => client,
    });

    await controller.acceptSession(_session);

    await expect(controller.accessToken({ forceRefresh: true })).rejects.toBeInstanceOf(AuthException);
    expect(controller.session).toBeNull();
    expect(store.refreshToken).toBeNull();
    expect(store.clearCount).toBe(1);

    controller.dispose();
  });

  test('rotated refresh token write failure keeps the in-memory session', async () => {
    const store = new _FakeTokenStore();
    const client = new _FakeAuthClient(async () => _rotatedSession);
    const controller = new AuthSessionController({
      tokenStore: store,
      apiBaseUrl: 'https://api.example.test',
      authClientFactory: () => client,
    });

    await controller.acceptSession(_session);
    store.failWrites = true;

    const accessToken = await controller.accessToken({ forceRefresh: true });

    expect(accessToken).toBe('access_2');
    expect(controller.session).toBe(_rotatedSession);
    expect(store.refreshToken).toBe('refresh_1');
    expect(store.clearCount).toBe(0);

    controller.dispose();
  });

  test('concurrent access token requests share one refresh', async () => {
    const store = new _FakeTokenStore();
    let refreshes = 0;
    const client = new _FakeAuthClient(async () => {
      refreshes += 1;
      return _rotatedSession;
    });
    const controller = new AuthSessionController({
      tokenStore: store,
      apiBaseUrl: 'https://api.example.test',
      authClientFactory: () => client,
    });

    await controller.acceptSession(_session);
    const tokens = await Promise.all([
      controller.accessToken({ forceRefresh: true }),
      controller.accessToken({ forceRefresh: true }),
    ]);

    expect(tokens).toEqual(['access_2', 'access_2']);
    expect(refreshes).toBe(1);
    expect(await controller.accessToken()).toBe('access_2');
    controller.dispose();
  });

  test('accessToken rejects without a session', async () => {
    const controller = new AuthSessionController({
      tokenStore: new _FakeTokenStore(),
      apiBaseUrl: 'https://api.example.test',
      authClientFactory: () => new _FakeAuthClient(),
    });

    await expect(controller.accessToken()).rejects.toThrow('尚未登录');
    controller.dispose();
  });

  test('transient restore failure keeps the stored refresh token', async () => {
    const store = new _FakeTokenStore('stored_refresh');
    const client = new _FakeAuthClient(async () => {
      throw new Error('offline');
    });
    const controller = new AuthSessionController({
      tokenStore: store,
      apiBaseUrl: 'https://api.example.test',
      authClientFactory: () => client,
    });

    const result = await controller.restoreSession();

    expect(result.status).toBe(AuthRestoreStatus.refreshFailed);
    expect(controller.session).toBeNull();
    expect(store.refreshToken).toBe('stored_refresh');
    expect(store.clearCount).toBe(0);

    controller.dispose();
  });

  test('unauthorized restore failure clears the stored refresh token', async () => {
    const store = new _FakeTokenStore('stored_refresh');
    const client = new _FakeAuthClient(async () => {
      throw _unauthorized();
    });
    const controller = new AuthSessionController({
      tokenStore: store,
      apiBaseUrl: 'https://api.example.test',
      authClientFactory: () => client,
    });

    const result = await controller.restoreSession();

    expect(result.status).toBe(AuthRestoreStatus.refreshFailed);
    expect(controller.session).toBeNull();
    expect(store.refreshToken).toBeNull();
    expect(store.clearCount).toBe(1);

    controller.dispose();
  });

  test('restore without a refresh token reports missingRefreshToken', async () => {
    const controller = new AuthSessionController({
      tokenStore: new _FakeTokenStore(),
      apiBaseUrl: 'https://api.example.test',
      authClientFactory: () => new _FakeAuthClient(),
    });

    const result = await controller.restoreSession();

    expect(result.status).toBe(AuthRestoreStatus.missingRefreshToken);
    expect(result.hasSession).toBe(false);
    controller.dispose();
  });

  test('successful restore stores the rotated refresh token', async () => {
    const store = new _FakeTokenStore('stored_refresh');
    const controller = new AuthSessionController({
      tokenStore: store,
      apiBaseUrl: 'https://api.example.test',
      authClientFactory: () => new _FakeAuthClient(async () => _rotatedSession),
    });

    const result = await controller.restoreSession();

    expect(result.hasSession).toBe(true);
    expect(controller.session).toBe(_rotatedSession);
    expect(store.refreshToken).toBe('refresh_2');
    controller.dispose();
  });
});
