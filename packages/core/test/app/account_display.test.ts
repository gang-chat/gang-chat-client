import { describe, expect, test } from 'vitest';

import {
  SessionListBodyState,
  accountAvatarPreviewPath,
  accountBindingsSavedNotice,
  accountDeletionConfirmationSpec,
  accountDeletionDescription,
  accountNoBindingChangesNotice,
  accountPresetAvatarNotice,
  avatarEmptyFileMessage,
  avatarPickerOpenFailureMessage,
  avatarReadFailureMessage,
  avatarUpdatedNotice,
  avatarUploadFilename,
  avatarUploadedPendingProfileNotice,
  canDeleteAccount,
  canEditUsername,
  canStartAccountDeletion,
  canStartPasswordChange,
  formatDateTime,
  initials,
  normalizeGender,
  passwordUpdatedNotice,
  preferencesNoChangesNotice,
  preferencesSavedNotice,
  profileNoChangesNotice,
  profileSavedNotice,
  sessionDetailText,
  sessionDeviceLabel,
  sessionIpAddressLabel,
  sessionListBodyState,
  sessionLocationLabel,
  sessionStateText,
  shouldClearUploadedAvatarForPreset,
  usernameHelperText,
} from '../../src/app/account_display';
import { CurrentUser, UserSession } from '../../src/protocol/models';

const hour = 60 * 60 * 1000;

function _user(opts: { canChangeUsernameAt?: Date | null; isSuperuser?: boolean } = {}): CurrentUser {
  return new CurrentUser({
    id: 'user_1',
    uid: '1001',
    username: 'logan',
    displayName: 'Logan',
    bio: '',
    gender: 'secret',
    email: null,
    emailPublic: false,
    phoneNumber: null,
    phoneNumberPublic: false,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
    isSuperuser: opts.isSuperuser ?? false,
    createdAt: new Date(2026, 5, 4),
    canChangeUsernameAt: opts.canChangeUsernameAt ?? null,
  });
}

function _session(
  opts: {
    isCurrent?: boolean;
    userAgent?: string | null;
    ipAddress?: string | null;
    expiresAt?: Date;
    revokedAt?: Date | null;
  } = {},
): UserSession {
  return new UserSession({
    id: 'session_1',
    userAgent: opts.userAgent === undefined ? 'Test Browser' : opts.userAgent,
    ipAddress: opts.ipAddress === undefined ? '127.0.0.1' : opts.ipAddress,
    location: 'Local',
    createdAt: new Date(2026, 5, 1),
    lastUsedAt: new Date(2026, 5, 4),
    expiresAt: opts.expiresAt ?? new Date(2026, 5, 5),
    revokedAt: opts.revokedAt ?? null,
    isCurrent: opts.isCurrent ?? false,
  });
}

describe('account_display', () => {
  test('normalizeGender keeps known values and falls back to secret', () => {
    expect(normalizeGender('male')).toBe('male');
    expect(normalizeGender('female')).toBe('female');
    expect(normalizeGender('secret')).toBe('secret');
    expect(normalizeGender('unknown')).toBe('secret');
  });

  test('formatDateTime uses a compact local timestamp and handles null', () => {
    expect(formatDateTime(null)).toBe('未知');
    expect(formatDateTime(new Date(2026, 5, 4, 9, 5))).toBe('2026-06-04 09:05');
  });

  test('username edit helper respects can-change time', () => {
    const now = new Date(2026, 5, 4, 12);
    const editable = _user({ canChangeUsernameAt: now });
    const locked = _user({ canChangeUsernameAt: new Date(now.getTime() + 2 * hour) });

    expect(canEditUsername(editable, { now })).toBe(true);
    expect(usernameHelperText(editable, { now })).toContain('一天只能修改一次');
    expect(canEditUsername(locked, { now })).toBe(false);
    expect(usernameHelperText(locked, { now })).toContain('下次可修改时间');
  });

  test('avatarUploadFilename sanitizes basename and appends timestamp', () => {
    const now = new Date(1234567890);

    expect(avatarUploadFilename('/tmp/My Avatar.jpg', { now })).toBe('My-Avatar-1234567890.png');
    expect(avatarUploadFilename(' .jpg ', { now })).toBe('avatar-1234567890.png');
  });

  test('account avatar preview helpers handle pending and preset states', () => {
    expect(
      accountAvatarPreviewPath({
        clearUploadedAvatar: false,
        pendingAvatarUrl: '/pending.png',
        currentAvatarUrl: '/current.png',
      }),
    ).toBe('/pending.png');
    expect(
      accountAvatarPreviewPath({ clearUploadedAvatar: false, pendingAvatarUrl: null, currentAvatarUrl: '/current.png' }),
    ).toBe('/current.png');
    expect(
      accountAvatarPreviewPath({
        clearUploadedAvatar: true,
        pendingAvatarUrl: '/pending.png',
        currentAvatarUrl: '/current.png',
      }),
    ).toBeNull();
    expect(shouldClearUploadedAvatarForPreset('/current.png')).toBe(true);
    expect(shouldClearUploadedAvatarForPreset(null)).toBe(false);
    expect(accountPresetAvatarNotice({ clearUploadedAvatar: true })).toBe('保存用户资料后将使用预设头像');
    expect(accountPresetAvatarNotice({ clearUploadedAvatar: false })).toBeNull();
  });

  test('account profile and avatar notices stay outside UI', () => {
    expect(accountNoBindingChangesNotice()).toBe('没有账号绑定变更');
    expect(accountBindingsSavedNotice()).toBe('账号绑定已保存');
    expect(preferencesNoChangesNotice()).toBe('没有偏好设置变更');
    expect(preferencesSavedNotice()).toBe('偏好设置已保存');
    expect(profileNoChangesNotice()).toBe('没有用户资料变更');
    expect(profileSavedNotice()).toBe('用户资料已保存');
    expect(avatarPickerOpenFailureMessage('denied')).toBe('无法打开文件选择器');
    expect(avatarReadFailureMessage('bad file')).toBe('无法读取图片');
    expect(avatarEmptyFileMessage()).toBe('图片文件为空');
    expect(avatarUploadedPendingProfileNotice()).toBe('头像已上传，保存用户资料后生效');
    expect(avatarUpdatedNotice()).toBe('头像已更新');
    expect(passwordUpdatedNotice()).toBe('密码已更新');
  });

  test('account security action helpers gate dangerous actions', () => {
    const user = _user();
    const superuser = _user({ isSuperuser: true });

    expect(canDeleteAccount(user)).toBe(true);
    expect(canDeleteAccount(superuser)).toBe(false);
    expect(canStartAccountDeletion({ hasApi: true, user, deletingAccount: false })).toBe(true);
    expect(canStartAccountDeletion({ hasApi: false, user, deletingAccount: false })).toBe(false);
    expect(canStartAccountDeletion({ hasApi: true, user: superuser, deletingAccount: false })).toBe(false);
    expect(accountDeletionDescription(superuser)).toBe('超级用户账号不能被注销。');
    expect(accountDeletionDescription(user)).toContain('注销后账号不能继续登录');

    const spec = accountDeletionConfirmationSpec(user);
    expect(spec.title).toBe('确认注销账号');
    expect(spec.body).toBe(accountDeletionDescription(user));
    expect(spec.expectedText).toBe('logan');
    expect(spec.inputHint).toBe('输入 logan 确认');
    expect(spec.confirmLabel).toBe('确认注销');
  });

  test('password change guard follows api and busy state', () => {
    expect(canStartPasswordChange({ hasApi: true, changingPassword: false })).toBe(true);
    expect(canStartPasswordChange({ hasApi: false, changingPassword: false })).toBe(false);
    expect(canStartPasswordChange({ hasApi: true, changingPassword: true })).toBe(false);
  });

  test('sessionStateText prioritizes current revoked expired and active state', () => {
    const now = new Date(2026, 5, 4, 12);

    expect(sessionStateText(_session({ isCurrent: true }), { now })).toBe('当前会话');
    expect(sessionStateText(_session({ revokedAt: new Date(now.getTime() - 24 * hour) }), { now })).toBe('已失效');
    expect(sessionStateText(_session({ expiresAt: new Date(now.getTime() - 60_000) }), { now })).toBe('已过期');
    expect(sessionStateText(_session({ expiresAt: new Date(now.getTime() + 60_000) }), { now })).toBe('有效');
  });

  test('session display helpers provide fallback system and detail text', () => {
    const session = _session({ userAgent: '  ', ipAddress: '' });

    expect(sessionDeviceLabel(session)).toBe('未知系统');
    expect(sessionIpAddressLabel(session)).toBe('未知 IP');
    expect(sessionLocationLabel(session)).toBe('Local');
    expect(sessionDetailText(session)).toBe('位置：Local · IP：未知 IP · 最近活动：2026-06-04 00:00');
  });

  test('session device label extracts systems from Gang Chat clients', () => {
    expect(sessionDeviceLabel(_session({ userAgent: 'GangChat Client (Android)' }))).toBe('Android');
    expect(sessionDeviceLabel(_session({ userAgent: 'GangChat Client (Windows)' }))).toBe('Windows');
  });

  test('session device label recognizes common legacy user agents', () => {
    expect(
      sessionDeviceLabel(_session({ userAgent: 'Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36' })),
    ).toBe('Android');
    expect(sessionDeviceLabel(_session({ userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' }))).toBe('Windows');
    expect(sessionDeviceLabel(_session({ userAgent: 'Dart/3.9' }))).toBe('未知系统');
  });

  test('sessionListBodyState separates loading empty and result states', () => {
    expect(sessionListBodyState({ loading: true, sessions: [] })).toBe(SessionListBodyState.loading);
    expect(sessionListBodyState({ loading: false, sessions: [] })).toBe(SessionListBodyState.empty);
    expect(sessionListBodyState({ loading: true, sessions: [_session()] })).toBe(SessionListBodyState.results);
  });

  test('initials uses first characters from up to two words', () => {
    expect(initials('')).toBe('?');
    expect(initials('Logan')).toBe('L');
    expect(initials('Logan Zhang')).toBe('LZ');
  });
});
