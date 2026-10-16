import { expect, test } from 'vitest';

import { PasswordResetController } from '../../src/app/password_reset_controller';
import { AuthClient } from '../../src/auth/auth_client';
import { MockClient, mockResponse } from '../support/fake_http_client';

class _ClaimingAuthClient extends AuthClient {
  constructor() {
    super({ baseUrl: 'https://unused.test', httpClient: new MockClient(() => mockResponse('', 404)) });
  }

  readonly claims: string[] = [];

  override async claimPasswordResetForSession(opts: { accessToken: string; resetToken: string }): Promise<void> {
    this.claims.push(`${opts.accessToken}:${opts.resetToken}`);
  }

  override close(): void {}
}

test('password reset grant follows the active session and email', async () => {
  const client = new _ClaimingAuthClient();
  const controller = new PasswordResetController({
    apiBaseUrl: 'https://api.example.test',
    accessTokenProvider: async () => 'access-token',
    authClientFactory: () => client,
  });

  expect(controller.isCurrentSessionAuthorizedFor('kai@example.test')).toBe(false);

  await controller.claimForCurrentSession('reset-token', { email: ' Kai@Example.Test ' });

  expect(client.claims).toEqual(['access-token:reset-token']);
  expect(controller.isCurrentSessionAuthorizedFor('kai@example.test')).toBe(true);
  expect(controller.isCurrentSessionAuthorizedFor('other@example.test')).toBe(false);

  controller.invalidateAuthorizationIfEmailChanged('other@example.test');
  expect(controller.isCurrentSessionAuthorizedFor('kai@example.test')).toBe(false);
  controller.invalidateAuthorizationIfEmailChanged('kai@example.test');
  expect(controller.isCurrentSessionAuthorizedFor('kai@example.test')).toBe(false);

  controller.clearCurrentSessionAuthorization();
  expect(controller.isCurrentSessionAuthorizedFor('kai@example.test')).toBe(false);
});

test('claimForCurrentSession requires a session token provider', async () => {
  const controller = new PasswordResetController({
    apiBaseUrl: 'https://api.example.test',
    authClientFactory: () => new _ClaimingAuthClient(),
  });

  await expect(controller.claimForCurrentSession('reset-token', { email: 'kai@example.test' })).rejects.toThrow(
    '当前页面没有可用的登录会话',
  );
});
