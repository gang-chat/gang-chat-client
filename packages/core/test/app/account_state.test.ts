import { describe, expect, test } from 'vitest';

import {
  accountLoadCancelled,
  accountLoadFailed,
  accountLoadStarted,
  accountLoadSucceeded,
} from '../../src/app/account_state';
import { CurrentUser } from '../../src/protocol/models';

function _user(id: string): CurrentUser {
  return new CurrentUser({
    id,
    uid: '1001',
    username: 'logan',
    displayName: 'Logan',
    bio: '',
    gender: 'secret',
    email: 'logan@example.test',
    emailPublic: false,
    phoneNumber: null,
    phoneNumberPublic: false,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
    isSuperuser: false,
    createdAt: new Date(2026, 5, 4),
  });
}

describe('account_state', () => {
  test('accountLoadStarted preserves user and clears error', () => {
    const user = _user('user_1');
    const patch = accountLoadStarted({ user });

    expect(patch.user).toBe(user);
    expect(patch.loading).toBe(true);
    expect(patch.accountError).toBeNull();
  });

  test('accountLoadSucceeded stores user and clears busy state', () => {
    const user = _user('user_2');
    const patch = accountLoadSucceeded({ user });

    expect(patch.user).toBe(user);
    expect(patch.loading).toBe(false);
    expect(patch.accountError).toBeNull();
  });

  test('accountLoadCancelled preserves existing feedback', () => {
    const user = _user('user_1');
    const patch = accountLoadCancelled({ user, accountError: 'previous error' });

    expect(patch.user).toBe(user);
    expect(patch.loading).toBe(false);
    expect(patch.accountError).toBe('previous error');
  });

  test('accountLoadFailed preserves user and reports error', () => {
    const user = _user('user_1');
    const patch = accountLoadFailed({ user, failure: 'load failed' });

    expect(patch.user).toBe(user);
    expect(patch.loading).toBe(false);
    expect(patch.accountError).toBe('操作失败，请稍后重试');
  });
});
