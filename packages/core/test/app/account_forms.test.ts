import { describe, expect, test } from 'vitest';

import {
  AccountAvatarErrorTarget,
  AccountFormSaveTarget,
  PasswordVisibilityField,
  accountAvatarActionCancelled,
  accountAvatarActionFailed,
  accountAvatarPendingUploadSucceeded,
  accountAvatarPreparationStarted,
  accountAvatarProfileUpdatedFromStickerSucceeded,
  accountAvatarUploadStarted,
  accountDeletionFailed,
  accountDeletionFinished,
  accountDeletionStarted,
  accountEmailPublicChanged,
  accountFormSaveCancelled,
  accountFormSaveFailed,
  accountFormSaveNoChanges,
  accountFormSaveStarted,
  accountFormSaveSucceeded,
  accountFormSaveValidationFailed,
  accountGenderChanged,
  accountPhoneNumberPublicChanged,
  accountPresetAvatarSelected,
  accountUpdateDraftFromForm,
  isLoginUsernameFormatValid,
  loginUsernameAvailabilityError,
  loginUsernameUpdateDraftFromForm,
  loginUsernameValidationError,
  passwordChangeDraftFromForm,
  passwordChangeFailed,
  passwordChangeStarted,
  passwordChangeSucceeded,
  passwordChangeValidationFailed,
  passwordVisibilityToggled,
  preferencesUpdateDraftFromForm,
  profileUpdateDraftFromForm,
  stickerAvatarUploadFilename,
} from '../../src/app/account_forms';
import { CurrentUser, UserSummary, defaultUserLanguage } from '../../src/protocol/models';

function _user(
  opts: {
    id?: string;
    username?: string;
    displayName?: string;
    bio?: string;
    gender?: string;
    email?: string | null;
    emailPublic?: boolean;
    phoneNumber?: string | null;
    phoneNumberPublic?: boolean;
    defaultAvatarKey?: string;
    language?: string;
  } = {},
): CurrentUser {
  return new CurrentUser({
    id: opts.id ?? 'user_1',
    uid: '1001',
    username: opts.username ?? 'logan',
    displayName: opts.displayName ?? 'Logan',
    bio: opts.bio ?? '',
    gender: opts.gender ?? 'secret',
    email: opts.email ?? null,
    emailPublic: opts.emailPublic ?? false,
    phoneNumber: opts.phoneNumber ?? null,
    phoneNumberPublic: opts.phoneNumberPublic ?? false,
    avatarUrl: null,
    defaultAvatarKey: opts.defaultAvatarKey ?? 'blue-3',
    isSuperuser: false,
    language: opts.language ?? defaultUserLanguage,
    createdAt: new Date(Date.UTC(2026, 5, 4)),
  });
}

function _summary(id: string, username: string): UserSummary {
  return new UserSummary({
    id,
    uid: id,
    username,
    displayName: username,
    bio: '',
    gender: 'secret',
    email: null,
    emailPublic: false,
    phoneNumber: null,
    phoneNumberPublic: false,
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
    isSuperuser: false,
    isOnline: true,
  });
}

const _accountForm = {
  username: 'logan',
  email: 'logan@example.test',
  emailPublic: false,
  phoneNumber: '',
  phoneNumberPublic: false,
  language: defaultUserLanguage,
};

describe('account_forms drafts', () => {
  test('accountUpdateDraftFromForm validates required fields', () => {
    expect(accountUpdateDraftFromForm({ user: _user(), ..._accountForm, username: '' }).error).toBe('登录用户名不能为空');
    expect(accountUpdateDraftFromForm({ user: _user(), ..._accountForm, email: '' }).error).toBe('邮箱不能为空');
    expect(accountUpdateDraftFromForm({ user: _user(), ..._accountForm, username: 'lo gan' }).error).toBe(
      '登录用户名需为 3-32 位，只能包含英文字母、数字、下划线或连字符',
    );
  });

  test('loginUsernameUpdateDraftFromForm validates username only', () => {
    expect(loginUsernameValidationError('lo')).not.toBeNull();
    expect(loginUsernameValidationError('logan_01-test')).toBeNull();
    expect(isLoginUsernameFormatValid('logan.test')).toBe(false);

    expect(loginUsernameUpdateDraftFromForm({ user: _user({ username: 'logan' }), username: ' logan ' }).noChanges).toBe(
      true,
    );

    const draft = loginUsernameUpdateDraftFromForm({ user: _user({ username: 'logan' }), username: ' new_logan ' });
    expect(draft.error).toBeNull();
    expect(draft.username).toBe('new_logan');
    expect(draft.email).toBeNull();
    expect(draft.language).toBeNull();
  });

  test('loginUsernameAvailabilityError detects other users only', () => {
    expect(
      loginUsernameAvailabilityError({
        user: _user({ id: 'user_1', username: 'logan' }),
        username: 'Logan',
        candidates: [_summary('user_1', 'logan'), _summary('user_2', 'logan_suffix')],
      }),
    ).toBeNull();
    expect(
      loginUsernameAvailabilityError({
        user: _user({ id: 'user_1', username: 'logan' }),
        username: 'New_Logan',
        candidates: [_summary('user_2', 'new_logan')],
      }),
    ).toBe('该登录用户名已被其他用户使用');
  });

  test('accountUpdateDraftFromForm returns changed account fields only', () => {
    const draft = accountUpdateDraftFromForm({
      user: _user({ email: 'old@example.test', phoneNumber: '' }),
      username: ' logan ',
      email: 'new@example.test',
      emailPublic: true,
      phoneNumber: ' 123 ',
      phoneNumberPublic: false,
      language: 'zh-Hant',
    });

    expect(draft.error).toBeNull();
    expect(draft.noChanges).toBe(false);
    expect(draft.username).toBeNull();
    expect(draft.email).toBe('new@example.test');
    expect(draft.emailPublic).toBe(true);
    expect(draft.phoneNumber).toBe('123');
    expect(draft.phoneNumberPublic).toBeNull();
    expect(draft.language).toBe('zh-Hant');
  });

  test('preferencesUpdateDraftFromForm returns language changes only', () => {
    expect(preferencesUpdateDraftFromForm({ user: _user({ language: 'zh-Hans' }), language: 'zh-Hans' }).noChanges).toBe(
      true,
    );

    const draft = preferencesUpdateDraftFromForm({
      user: _user({ username: 'dirty', email: 'dirty@example.test' }),
      language: 'en',
    });

    expect(draft.error).toBeNull();
    expect(draft.noChanges).toBe(false);
    expect(draft.username).toBeNull();
    expect(draft.email).toBeNull();
    expect(draft.emailPublic).toBeNull();
    expect(draft.phoneNumber).toBeNull();
    expect(draft.phoneNumberPublic).toBeNull();
    expect(draft.language).toBe('en');
  });

  test('profileUpdateDraftFromForm validates and returns changed fields only', () => {
    expect(
      profileUpdateDraftFromForm({
        user: _user(),
        displayName: ' ',
        bio: '',
        gender: 'secret',
        defaultAvatarKey: 'blue-3',
        clearUploadedAvatar: false,
      }).error,
    ).toBe('用户名不能为空');

    const draft = profileUpdateDraftFromForm({
      user: _user({ bio: 'old', gender: 'unknown' }),
      displayName: ' Logan ',
      bio: 'new',
      gender: 'male',
      defaultAvatarKey: 'green-2',
      pendingAvatarAssetId: 'asset_1',
      clearUploadedAvatar: false,
    });

    expect(draft.error).toBeNull();
    expect(draft.displayName).toBeNull();
    expect(draft.bio).toBe('new');
    expect(draft.gender).toBe('male');
    expect(draft.defaultAvatarKey).toBe('green-2');
    expect(draft.avatarAssetId).toBe('asset_1');

    const cleared = profileUpdateDraftFromForm({
      user: _user(),
      displayName: 'Logan',
      bio: '',
      gender: 'secret',
      defaultAvatarKey: 'blue-3',
      clearUploadedAvatar: true,
    });
    expect(cleared.avatarAssetId).toBe('');
  });

  test('profile and account drafts report noChanges', () => {
    expect(accountUpdateDraftFromForm({ user: _user({ email: 'logan@example.test' }), ..._accountForm }).noChanges).toBe(
      true,
    );
    expect(
      profileUpdateDraftFromForm({
        user: _user(),
        displayName: 'Logan',
        bio: '',
        gender: 'secret',
        defaultAvatarKey: 'blue-3',
        clearUploadedAvatar: false,
      }).noChanges,
    ).toBe(true);
  });

  test('passwordChangeDraftFromForm validates and preserves passwords', () => {
    expect(
      passwordChangeDraftFromForm({ currentPassword: '', newPassword: 'new-password', confirmPassword: 'new-password' })
        .error,
    ).toBe('请完整填写当前密码、新密码和确认密码');
    expect(
      passwordChangeDraftFromForm({ currentPassword: 'old-password', newPassword: 'short', confirmPassword: 'short' }).error,
    ).toBe('新密码至少需要 8 个字符');
    expect(
      passwordChangeDraftFromForm({
        currentPassword: 'old-password',
        newPassword: 'new-password',
        confirmPassword: 'other-password',
      }).error,
    ).toBe('两次输入的新密码不一致');

    const draft = passwordChangeDraftFromForm({
      currentPassword: ' old-password ',
      newPassword: ' new-password ',
      confirmPassword: ' new-password ',
    });

    expect(draft.isValid).toBe(true);
    expect(draft.currentPassword).toBe(' old-password ');
    expect(draft.newPassword).toBe(' new-password ');

    const verifiedDraft = passwordChangeDraftFromForm({
      currentPassword: '',
      newPassword: 'new-password',
      confirmPassword: 'new-password',
      currentPasswordRequired: false,
    });
    expect(verifiedDraft.isValid).toBe(true);
    expect(verifiedDraft.currentPassword).toBe('');
  });
});

describe('account_forms patches', () => {
  test('account editable fields patches update one form field at a time', () => {
    const genderPatch = accountGenderChanged({ gender: 'female', emailPublic: true, phoneNumberPublic: false });

    expect(genderPatch.gender).toBe('female');
    expect(genderPatch.emailPublic).toBe(true);
    expect(genderPatch.phoneNumberPublic).toBe(false);

    const emailPatch = accountEmailPublicChanged({
      gender: genderPatch.gender,
      emailPublic: false,
      phoneNumberPublic: genderPatch.phoneNumberPublic,
    });

    expect(emailPatch.gender).toBe('female');
    expect(emailPatch.emailPublic).toBe(false);
    expect(emailPatch.phoneNumberPublic).toBe(false);

    const phonePatch = accountPhoneNumberPublicChanged({
      gender: emailPatch.gender,
      emailPublic: emailPatch.emailPublic,
      phoneNumberPublic: true,
    });

    expect(phonePatch.gender).toBe('female');
    expect(phonePatch.emailPublic).toBe(false);
    expect(phonePatch.phoneNumberPublic).toBe(true);
    expect(accountGenderChanged({ gender: 'other', emailPublic: false, phoneNumberPublic: false }).gender).toBe('secret');
  });

  test('passwordVisibilityToggled flips only the requested field', () => {
    const current = passwordVisibilityToggled({
      field: PasswordVisibilityField.current,
      obscureCurrentPassword: true,
      obscureNewPassword: true,
      obscureConfirmPassword: false,
    });

    expect(current.obscureCurrentPassword).toBe(false);
    expect(current.obscureNewPassword).toBe(true);
    expect(current.obscureConfirmPassword).toBe(false);

    const next = passwordVisibilityToggled({ field: PasswordVisibilityField.newPassword, ...current });

    expect(next.obscureCurrentPassword).toBe(false);
    expect(next.obscureNewPassword).toBe(false);
    expect(next.obscureConfirmPassword).toBe(false);

    const confirm = passwordVisibilityToggled({
      field: PasswordVisibilityField.confirm,
      obscureCurrentPassword: next.obscureCurrentPassword,
      obscureNewPassword: next.obscureNewPassword,
      obscureConfirmPassword: next.obscureConfirmPassword,
    });

    expect(confirm.obscureCurrentPassword).toBe(false);
    expect(confirm.obscureNewPassword).toBe(false);
    expect(confirm.obscureConfirmPassword).toBe(true);
  });

  test('password change patches', () => {
    const validation = passwordChangeValidationFailed({
      error: '新密码至少需要 8 个字符',
      changingPassword: true,
      notice: 'previous notice',
    });
    expect(validation.changingPassword).toBe(true);
    expect(validation.securityError).toBe('新密码至少需要 8 个字符');
    expect(validation.notice).toBe('previous notice');

    const started = passwordChangeStarted();
    expect(started.changingPassword).toBe(true);
    expect(started.securityError).toBeNull();
    expect(started.notice).toBeNull();

    const failed = passwordChangeFailed('request failed');
    expect(failed.changingPassword).toBe(false);
    expect(failed.securityError).toBe('操作失败，请稍后重试');
    expect(failed.notice).toBeNull();

    const succeeded = passwordChangeSucceeded();
    expect(succeeded.changingPassword).toBe(false);
    expect(succeeded.securityError).toBeNull();
    expect(succeeded.notice).toBe('密码已更新');
  });

  test('account deletion patches', () => {
    const started = accountDeletionStarted();
    expect(started.deletingAccount).toBe(true);
    expect(started.securityError).toBeNull();
    expect(started.notice).toBeNull();

    const failed = accountDeletionFailed('delete failed');
    expect(failed.deletingAccount).toBe(false);
    expect(failed.securityError).toBe('操作失败，请稍后重试');
    expect(failed.notice).toBeNull();

    const finished = accountDeletionFinished({ securityError: null, notice: 'previous notice' });
    expect(finished.deletingAccount).toBe(false);
    expect(finished.securityError).toBeNull();
    expect(finished.notice).toBe('previous notice');
  });

  test('accountFormSaveValidationFailed preserves busy state and notice', () => {
    const patch = accountFormSaveValidationFailed({
      error: '邮箱不能为空',
      savingAccount: true,
      savingProfile: false,
      notice: 'previous notice',
    });

    expect(patch.savingAccount).toBe(true);
    expect(patch.savingProfile).toBe(false);
    expect(patch.accountError).toBe('邮箱不能为空');
    expect(patch.notice).toBe('previous notice');
  });

  test('accountFormSaveNoChanges reports notice without clearing error', () => {
    const accountPatch = accountFormSaveNoChanges({
      target: AccountFormSaveTarget.account,
      savingAccount: false,
      savingProfile: true,
      accountError: 'previous error',
    });
    const profilePatch = accountFormSaveNoChanges({
      target: AccountFormSaveTarget.profile,
      savingAccount: true,
      savingProfile: false,
      accountError: null,
    });
    const preferencesPatch = accountFormSaveNoChanges({
      target: AccountFormSaveTarget.preferences,
      savingAccount: true,
      savingProfile: false,
      accountError: null,
    });

    expect(accountPatch.savingAccount).toBe(false);
    expect(accountPatch.savingProfile).toBe(true);
    expect(accountPatch.accountError).toBe('previous error');
    expect(accountPatch.notice).toBe('没有账号绑定变更');
    expect(profilePatch.savingAccount).toBe(true);
    expect(profilePatch.savingProfile).toBe(false);
    expect(profilePatch.accountError).toBeNull();
    expect(profilePatch.notice).toBe('没有用户资料变更');
    expect(preferencesPatch.savingAccount).toBe(true);
    expect(preferencesPatch.savingProfile).toBe(false);
    expect(preferencesPatch.notice).toBe('没有偏好设置变更');
  });

  test('accountFormSaveStarted sets only the target busy flag', () => {
    const accountPatch = accountFormSaveStarted({
      target: AccountFormSaveTarget.account,
      savingAccount: false,
      savingProfile: true,
    });
    const profilePatch = accountFormSaveStarted({
      target: AccountFormSaveTarget.profile,
      savingAccount: true,
      savingProfile: false,
    });
    const preferencesPatch = accountFormSaveStarted({
      target: AccountFormSaveTarget.preferences,
      savingAccount: false,
      savingProfile: true,
    });

    expect(accountPatch.savingAccount).toBe(true);
    expect(accountPatch.savingProfile).toBe(true);
    expect(accountPatch.accountError).toBeNull();
    expect(accountPatch.notice).toBeNull();
    expect(profilePatch.savingAccount).toBe(true);
    expect(profilePatch.savingProfile).toBe(true);
    expect(preferencesPatch.savingAccount).toBe(true);
    expect(preferencesPatch.savingProfile).toBe(true);
  });

  test('accountFormSaveCancelled clears only the target busy flag', () => {
    const patch = accountFormSaveCancelled({
      target: AccountFormSaveTarget.profile,
      savingAccount: true,
      savingProfile: true,
      accountError: 'previous error',
      notice: 'previous notice',
    });

    expect(patch.savingAccount).toBe(true);
    expect(patch.savingProfile).toBe(false);
    expect(patch.accountError).toBe('previous error');
    expect(patch.notice).toBe('previous notice');
  });

  test('accountFormSaveFailed clears target busy flag and sets error', () => {
    const patch = accountFormSaveFailed({
      target: AccountFormSaveTarget.account,
      savingAccount: true,
      savingProfile: true,
      failure: 'request failed',
    });

    expect(patch.savingAccount).toBe(false);
    expect(patch.savingProfile).toBe(true);
    expect(patch.accountError).toBe('操作失败，请稍后重试');
    expect(patch.notice).toBeNull();
  });

  test('accountFormSaveSucceeded clears target busy flag and sets notice', () => {
    const accountPatch = accountFormSaveSucceeded({
      target: AccountFormSaveTarget.account,
      savingAccount: true,
      savingProfile: false,
    });
    const profilePatch = accountFormSaveSucceeded({
      target: AccountFormSaveTarget.profile,
      savingAccount: false,
      savingProfile: true,
    });
    const preferencesPatch = accountFormSaveSucceeded({
      target: AccountFormSaveTarget.preferences,
      savingAccount: true,
      savingProfile: false,
    });

    expect(accountPatch.savingAccount).toBe(false);
    expect(accountPatch.savingProfile).toBe(false);
    expect(accountPatch.notice).toBe('账号绑定已保存');
    expect(profilePatch.savingAccount).toBe(false);
    expect(profilePatch.savingProfile).toBe(false);
    expect(profilePatch.notice).toBe('用户资料已保存');
    expect(preferencesPatch.savingAccount).toBe(false);
    expect(preferencesPatch.savingProfile).toBe(false);
    expect(preferencesPatch.notice).toBe('偏好设置已保存');
  });
});

describe('account_forms avatar patches', () => {
  test('accountPresetAvatarSelected clears pending upload state', () => {
    const patch = accountPresetAvatarSelected({ defaultAvatarKey: 'green-2', currentAvatarUrl: '/assets/avatar.png' });

    expect(patch.defaultAvatarKey).toBe('green-2');
    expect(patch.pendingAvatarAssetId).toBeNull();
    expect(patch.pendingAvatarUrl).toBeNull();
    expect(patch.clearUploadedAvatar).toBe(true);
    expect(patch.notice).toBe('保存用户资料后将使用预设头像');
  });

  test('accountAvatarPreparationStarted clears target error and notice', () => {
    const patch = accountAvatarPreparationStarted({
      target: AccountAvatarErrorTarget.account,
      pendingAvatarAssetId: 'asset_1',
      pendingAvatarUrl: '/avatar.png',
      clearUploadedAvatar: true,
      uploadingAvatar: true,
      accountError: 'account failed',
      stickerError: 'sticker failed',
    });

    expect(patch.pendingAvatarAssetId).toBe('asset_1');
    expect(patch.pendingAvatarUrl).toBe('/avatar.png');
    expect(patch.clearUploadedAvatar).toBe(true);
    expect(patch.uploadingAvatar).toBe(true);
    expect(patch.accountError).toBeNull();
    expect(patch.stickerError).toBe('sticker failed');
    expect(patch.notice).toBeNull();
  });

  test('accountAvatarUploadStarted marks avatar upload busy', () => {
    const patch = accountAvatarUploadStarted({
      target: AccountAvatarErrorTarget.sticker,
      pendingAvatarAssetId: null,
      pendingAvatarUrl: null,
      clearUploadedAvatar: false,
      accountError: 'account failed',
      stickerError: 'sticker failed',
    });

    expect(patch.uploadingAvatar).toBe(true);
    expect(patch.accountError).toBe('account failed');
    expect(patch.stickerError).toBeNull();
    expect(patch.notice).toBeNull();
  });

  test('accountAvatarActionFailed routes errors by target', () => {
    const accountPatch = accountAvatarActionFailed({
      target: AccountAvatarErrorTarget.account,
      pendingAvatarAssetId: null,
      pendingAvatarUrl: null,
      clearUploadedAvatar: false,
      accountError: null,
      stickerError: 'sticker failed',
      failure: 'account failed',
    });
    const stickerPatch = accountAvatarActionFailed({
      target: AccountAvatarErrorTarget.sticker,
      pendingAvatarAssetId: null,
      pendingAvatarUrl: null,
      clearUploadedAvatar: false,
      accountError: 'account failed',
      stickerError: null,
      failure: 'sticker failed',
    });

    expect(accountPatch.uploadingAvatar).toBe(false);
    expect(accountPatch.accountError).toBe('操作失败，请稍后重试');
    expect(accountPatch.stickerError).toBe('sticker failed');
    expect(stickerPatch.uploadingAvatar).toBe(false);
    expect(stickerPatch.accountError).toBe('account failed');
    expect(stickerPatch.stickerError).toBe('操作失败，请稍后重试');
  });

  test('accountAvatarActionCancelled clears target error and busy state', () => {
    const patch = accountAvatarActionCancelled({
      target: AccountAvatarErrorTarget.account,
      pendingAvatarAssetId: 'asset_1',
      pendingAvatarUrl: '/avatar.png',
      clearUploadedAvatar: false,
      accountError: 'account failed',
      stickerError: 'sticker failed',
    });

    expect(patch.pendingAvatarAssetId).toBe('asset_1');
    expect(patch.pendingAvatarUrl).toBe('/avatar.png');
    expect(patch.uploadingAvatar).toBe(false);
    expect(patch.accountError).toBeNull();
    expect(patch.stickerError).toBe('sticker failed');
    expect(patch.notice).toBeNull();
  });

  test('accountAvatarPendingUploadSucceeded stores pending avatar', () => {
    const patch = accountAvatarPendingUploadSucceeded({
      assetId: 'asset_1',
      assetUrl: '/avatar.png',
      stickerError: 'sticker failed',
    });

    expect(patch.pendingAvatarAssetId).toBe('asset_1');
    expect(patch.pendingAvatarUrl).toBe('/avatar.png');
    expect(patch.clearUploadedAvatar).toBe(false);
    expect(patch.uploadingAvatar).toBe(false);
    expect(patch.accountError).toBeNull();
    expect(patch.stickerError).toBe('sticker failed');
    expect(patch.notice).toBe('头像已上传，保存用户资料后生效');
  });

  test('accountAvatarProfileUpdatedFromStickerSucceeded clears sticker state', () => {
    const patch = accountAvatarProfileUpdatedFromStickerSucceeded({
      pendingAvatarAssetId: null,
      pendingAvatarUrl: null,
      clearUploadedAvatar: false,
      accountError: 'account failed',
    });

    expect(patch.uploadingAvatar).toBe(false);
    expect(patch.accountError).toBe('account failed');
    expect(patch.stickerError).toBeNull();
    expect(patch.notice).toBe('头像已更新');
  });

  test('stickerAvatarUploadFilename stays stable for avatar uploads', () => {
    expect(stickerAvatarUploadFilename({ stickerId: 'sticker_1' })).toBe('avatar-sticker_1.png');
  });
});
