import { describe, expect, test } from 'vitest';

import {
  accountSessionsLoadCancelled,
  accountSessionsLoadFailed,
  accountSessionsLoadStarted,
  accountSessionsLoadSucceeded,
} from '../../src/app/account_sessions';
import { UserSession } from '../../src/protocol/models';

function _session(id: string): UserSession {
  return new UserSession({
    id,
    userAgent: 'Test Browser',
    ipAddress: '127.0.0.1',
    location: 'Local',
    createdAt: new Date(2026, 5, 1),
    lastUsedAt: new Date(2026, 5, 4),
    expiresAt: new Date(2026, 5, 5),
    revokedAt: null,
    isCurrent: false,
  });
}

describe('account_sessions', () => {
  test('accountSessionsLoadStarted preserves sessions and clears error', () => {
    const existing = [_session('session_1')];
    const patch = accountSessionsLoadStarted({ sessions: existing });

    expect(patch.sessions).toBe(existing);
    expect(patch.loading).toBe(true);
    expect(patch.securityError).toBeNull();
  });

  test('accountSessionsLoadSucceeded stores sessions and clears busy state', () => {
    const sessions = [_session('session_2')];
    const patch = accountSessionsLoadSucceeded({ sessions });

    expect(patch.sessions).toBe(sessions);
    expect(patch.loading).toBe(false);
    expect(patch.securityError).toBeNull();
  });

  test('accountSessionsLoadCancelled preserves existing feedback', () => {
    const existing = [_session('session_1')];
    const patch = accountSessionsLoadCancelled({ sessions: existing, securityError: 'previous error' });

    expect(patch.sessions).toBe(existing);
    expect(patch.loading).toBe(false);
    expect(patch.securityError).toBe('previous error');
  });

  test('accountSessionsLoadFailed preserves sessions and reports error', () => {
    const existing = [_session('session_1')];
    const patch = accountSessionsLoadFailed({ sessions: existing, failure: 'load failed' });

    expect(patch.sessions).toBe(existing);
    expect(patch.loading).toBe(false);
    expect(patch.securityError).toBe('操作失败，请稍后重试');
  });
});
