import { expect, test } from 'vitest';

import { EmailVerificationController } from '../../src/app/email_verification_controller';
import { AuthClient } from '../../src/auth/auth_client';
import { MockClient, jsonResponse, mockResponse } from '../support/fake_http_client';

test('current-user verification opens active challenge when inspect omits id', async () => {
  const requestedPaths: string[] = [];
  const authorizationHeaders: Array<string | undefined> = [];
  const controller = new EmailVerificationController({
    apiBaseUrl: 'https://api.example.test/api/v1',
    accessTokenProvider: async () => 'access-token',
    authClientFactory: (baseUrl) =>
      new AuthClient({
        baseUrl,
        httpClient: new MockClient((request) => {
          requestedPaths.push(request.uri.pathname);
          authorizationHeaders.push(request.headers['authorization']);
          if (request.uri.pathname === '/api/v1/users/me/email-verification/inspect') {
            return jsonResponse({ can_send: false, retry_after: 42 });
          }
          if (request.uri.pathname === '/api/v1/users/me/email-verification/start') {
            return jsonResponse({ challenge_id: 'existing-challenge', retry_after: 42 });
          }
          return mockResponse('unexpected request', 404);
        }),
      }),
  });

  const challenge = await controller.inspectOrStartForCurrentUser('new@example.test');

  expect(challenge.id).toBe('existing-challenge');
  expect(challenge.retryAfterSeconds).toBe(42);
  expect(requestedPaths).toEqual([
    '/api/v1/users/me/email-verification/inspect',
    '/api/v1/users/me/email-verification/start',
  ]);
  expect(authorizationHeaders).toEqual(['Bearer access-token', 'Bearer access-token']);
});

test('inspectOrStart reuses the inspected challenge', async () => {
  const requestedPaths: string[] = [];
  const controller = new EmailVerificationController({
    apiBaseUrl: 'https://api.example.test/api/v1',
    authClientFactory: (baseUrl) =>
      new AuthClient({
        baseUrl,
        httpClient: new MockClient((request) => {
          requestedPaths.push(request.uri.pathname);
          return jsonResponse({ can_send: false, retry_after: 7, challenge_id: 'reuse-me' });
        }),
      }),
  });

  const challenge = await controller.inspectOrStart('a@example.test');

  expect(challenge.id).toBe('reuse-me');
  expect(challenge.retryAfterSeconds).toBe(7);
  expect(requestedPaths).toEqual(['/api/v1/auth/email-verification/inspect']);
});
