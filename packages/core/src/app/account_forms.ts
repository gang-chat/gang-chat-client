import type { CurrentUser, UserSummary } from '../protocol/models';
import {
  accountBindingsSavedNotice,
  accountNoBindingChangesNotice,
  accountPresetAvatarNotice,
  avatarUpdatedNotice,
  avatarUploadedPendingProfileNotice,
  normalizeGender,
  passwordUpdatedNotice,
  preferencesNoChangesNotice,
  preferencesSavedNotice,
  profileNoChangesNotice,
  profileSavedNotice,
  shouldClearUploadedAvatarForPreset,
} from './account_display';
import { userFacingErrorMessage } from './error_display';
import { normalizeLanguagePreference } from './language_preference';

export enum AccountFormSaveTarget {
  account = 'account',
  profile = 'profile',
  preferences = 'preferences',
}

export enum AccountAvatarErrorTarget {
  account = 'account',
  sticker = 'sticker',
}

export enum PasswordVisibilityField {
  current = 'current',
  newPassword = 'newPassword',
  confirm = 'confirm',
}

interface AccountUpdateFields {
  username?: string | null;
  email?: string | null;
  emailPublic?: boolean | null;
  phoneNumber?: string | null;
  phoneNumberPublic?: boolean | null;
  language?: string | null;
}

export class AccountUpdateDraft {
  private constructor(opts: AccountUpdateFields & { error?: string | null; noChanges?: boolean }) {
    this.username = opts.username ?? null;
    this.email = opts.email ?? null;
    this.emailPublic = opts.emailPublic ?? null;
    this.phoneNumber = opts.phoneNumber ?? null;
    this.phoneNumberPublic = opts.phoneNumberPublic ?? null;
    this.language = opts.language ?? null;
    this.error = opts.error ?? null;
    this.noChanges = opts.noChanges ?? false;
  }

  static invalid(error: string): AccountUpdateDraft {
    return new AccountUpdateDraft({ error });
  }

  static noChanges(): AccountUpdateDraft {
    return new AccountUpdateDraft({ noChanges: true });
  }

  static valid(opts: AccountUpdateFields = {}): AccountUpdateDraft {
    return new AccountUpdateDraft({ ...opts, error: null, noChanges: false });
  }

  readonly username: string | null;
  readonly email: string | null;
  readonly emailPublic: boolean | null;
  readonly phoneNumber: string | null;
  readonly phoneNumberPublic: boolean | null;
  readonly language: string | null;
  readonly error: string | null;
  readonly noChanges: boolean;

  get isValid(): boolean {
    return this.error == null && !this.noChanges;
  }
}

interface ProfileUpdateFields {
  displayName?: string | null;
  bio?: string | null;
  gender?: string | null;
  defaultAvatarKey?: string | null;
  avatarAssetId?: string | null;
}

export class ProfileUpdateDraft {
  private constructor(opts: ProfileUpdateFields & { error?: string | null; noChanges?: boolean }) {
    this.displayName = opts.displayName ?? null;
    this.bio = opts.bio ?? null;
    this.gender = opts.gender ?? null;
    this.defaultAvatarKey = opts.defaultAvatarKey ?? null;
    this.avatarAssetId = opts.avatarAssetId ?? null;
    this.error = opts.error ?? null;
    this.noChanges = opts.noChanges ?? false;
  }

  static invalid(error: string): ProfileUpdateDraft {
    return new ProfileUpdateDraft({ error });
  }

  static noChanges(): ProfileUpdateDraft {
    return new ProfileUpdateDraft({ noChanges: true });
  }

  static valid(opts: ProfileUpdateFields = {}): ProfileUpdateDraft {
    return new ProfileUpdateDraft({ ...opts, error: null, noChanges: false });
  }

  readonly displayName: string | null;
  readonly bio: string | null;
  readonly gender: string | null;
  readonly defaultAvatarKey: string | null;
  readonly avatarAssetId: string | null;
  readonly error: string | null;
  readonly noChanges: boolean;

  get isValid(): boolean {
    return this.error == null && !this.noChanges;
  }
}

export class PasswordChangeDraft {
  private constructor(opts: { currentPassword?: string | null; newPassword?: string | null; error?: string | null }) {
    this.currentPassword = opts.currentPassword ?? null;
    this.newPassword = opts.newPassword ?? null;
    this.error = opts.error ?? null;
  }

  static invalid(error: string): PasswordChangeDraft {
    return new PasswordChangeDraft({ error });
  }

  static valid(opts: { currentPassword: string; newPassword: string }): PasswordChangeDraft {
    return new PasswordChangeDraft({ currentPassword: opts.currentPassword, newPassword: opts.newPassword, error: null });
  }

  readonly currentPassword: string | null;
  readonly newPassword: string | null;
  readonly error: string | null;

  get isValid(): boolean {
    return this.error == null;
  }
}

const _loginUsernamePattern = /^[A-Za-z0-9_-]{3,32}$/;

export function loginUsernameValidationError(username: string): string | null {
  const trimmed = username.trim();
  if (trimmed.length === 0) {
    return '登录用户名不能为空';
  }
  if (!_loginUsernamePattern.test(trimmed)) {
    return '登录用户名需为 3-32 位，只能包含英文字母、数字、下划线或连字符';
  }
  return null;
}

export function isLoginUsernameFormatValid(username: string): boolean {
  return loginUsernameValidationError(username) == null;
}

export function loginUsernameAvailabilityError(opts: {
  user: CurrentUser;
  username: string;
  candidates: Iterable<UserSummary>;
}): string | null {
  const normalizedUsername = opts.username.trim().toLowerCase();
  let taken = false;
  for (const candidate of opts.candidates) {
    if (candidate.id !== opts.user.id && candidate.username.trim().toLowerCase() === normalizedUsername) {
      taken = true;
      break;
    }
  }
  return taken ? '该登录用户名已被其他用户使用' : null;
}

export class AccountFormSaveStatePatch {
  constructor(opts: {
    savingAccount: boolean;
    savingProfile: boolean;
    accountError: string | null;
    notice: string | null;
  }) {
    this.savingAccount = opts.savingAccount;
    this.savingProfile = opts.savingProfile;
    this.accountError = opts.accountError;
    this.notice = opts.notice;
  }

  readonly savingAccount: boolean;
  readonly savingProfile: boolean;
  readonly accountError: string | null;
  readonly notice: string | null;
}

export class AccountEditableFieldsPatch {
  constructor(opts: { gender: string; emailPublic: boolean; phoneNumberPublic: boolean }) {
    this.gender = opts.gender;
    this.emailPublic = opts.emailPublic;
    this.phoneNumberPublic = opts.phoneNumberPublic;
  }

  readonly gender: string;
  readonly emailPublic: boolean;
  readonly phoneNumberPublic: boolean;
}

export class PasswordChangeStatePatch {
  constructor(opts: { changingPassword: boolean; securityError: string | null; notice: string | null }) {
    this.changingPassword = opts.changingPassword;
    this.securityError = opts.securityError;
    this.notice = opts.notice;
  }

  readonly changingPassword: boolean;
  readonly securityError: string | null;
  readonly notice: string | null;
}

export class PasswordVisibilityPatch {
  constructor(opts: {
    obscureCurrentPassword: boolean;
    obscureNewPassword: boolean;
    obscureConfirmPassword: boolean;
  }) {
    this.obscureCurrentPassword = opts.obscureCurrentPassword;
    this.obscureNewPassword = opts.obscureNewPassword;
    this.obscureConfirmPassword = opts.obscureConfirmPassword;
  }

  readonly obscureCurrentPassword: boolean;
  readonly obscureNewPassword: boolean;
  readonly obscureConfirmPassword: boolean;
}

export class AccountDeletionStatePatch {
  constructor(opts: { deletingAccount: boolean; securityError: string | null; notice: string | null }) {
    this.deletingAccount = opts.deletingAccount;
    this.securityError = opts.securityError;
    this.notice = opts.notice;
  }

  readonly deletingAccount: boolean;
  readonly securityError: string | null;
  readonly notice: string | null;
}

export class AccountAvatarStatePatch {
  constructor(opts: {
    pendingAvatarAssetId: string | null;
    pendingAvatarUrl: string | null;
    clearUploadedAvatar: boolean;
    uploadingAvatar: boolean;
    accountError: string | null;
    stickerError: string | null;
    notice: string | null;
  }) {
    this.pendingAvatarAssetId = opts.pendingAvatarAssetId;
    this.pendingAvatarUrl = opts.pendingAvatarUrl;
    this.clearUploadedAvatar = opts.clearUploadedAvatar;
    this.uploadingAvatar = opts.uploadingAvatar;
    this.accountError = opts.accountError;
    this.stickerError = opts.stickerError;
    this.notice = opts.notice;
  }

  readonly pendingAvatarAssetId: string | null;
  readonly pendingAvatarUrl: string | null;
  readonly clearUploadedAvatar: boolean;
  readonly uploadingAvatar: boolean;
  readonly accountError: string | null;
  readonly stickerError: string | null;
  readonly notice: string | null;
}

export function passwordChangeValidationFailed(opts: {
  error: string;
  changingPassword: boolean;
  notice: string | null;
}): PasswordChangeStatePatch {
  return new PasswordChangeStatePatch({
    changingPassword: opts.changingPassword,
    securityError: opts.error,
    notice: opts.notice,
  });
}

export function passwordVisibilityToggled(opts: {
  field: PasswordVisibilityField;
  obscureCurrentPassword: boolean;
  obscureNewPassword: boolean;
  obscureConfirmPassword: boolean;
}): PasswordVisibilityPatch {
  const { field, obscureCurrentPassword, obscureNewPassword, obscureConfirmPassword } = opts;
  return new PasswordVisibilityPatch({
    obscureCurrentPassword: field === PasswordVisibilityField.current ? !obscureCurrentPassword : obscureCurrentPassword,
    obscureNewPassword: field === PasswordVisibilityField.newPassword ? !obscureNewPassword : obscureNewPassword,
    obscureConfirmPassword: field === PasswordVisibilityField.confirm ? !obscureConfirmPassword : obscureConfirmPassword,
  });
}

export function passwordChangeStarted(): PasswordChangeStatePatch {
  return new PasswordChangeStatePatch({ changingPassword: true, securityError: null, notice: null });
}

export function passwordChangeFailed(failure: unknown): PasswordChangeStatePatch {
  return new PasswordChangeStatePatch({
    changingPassword: false,
    securityError: userFacingErrorMessage(failure),
    notice: null,
  });
}

export function passwordChangeSucceeded(): PasswordChangeStatePatch {
  return new PasswordChangeStatePatch({
    changingPassword: false,
    securityError: null,
    notice: passwordUpdatedNotice(),
  });
}

export function accountDeletionStarted(): AccountDeletionStatePatch {
  return new AccountDeletionStatePatch({ deletingAccount: true, securityError: null, notice: null });
}

export function accountDeletionFailed(failure: unknown): AccountDeletionStatePatch {
  return new AccountDeletionStatePatch({
    deletingAccount: false,
    securityError: userFacingErrorMessage(failure),
    notice: null,
  });
}

export function accountDeletionFinished(opts: {
  securityError: string | null;
  notice: string | null;
}): AccountDeletionStatePatch {
  return new AccountDeletionStatePatch({
    deletingAccount: false,
    securityError: opts.securityError,
    notice: opts.notice,
  });
}

export function accountFormSaveValidationFailed(opts: {
  error: string;
  savingAccount: boolean;
  savingProfile: boolean;
  notice: string | null;
}): AccountFormSaveStatePatch {
  return new AccountFormSaveStatePatch({
    savingAccount: opts.savingAccount,
    savingProfile: opts.savingProfile,
    accountError: opts.error,
    notice: opts.notice,
  });
}

export function accountGenderChanged(opts: {
  emailPublic: boolean;
  phoneNumberPublic: boolean;
  gender: string;
}): AccountEditableFieldsPatch {
  return new AccountEditableFieldsPatch({
    gender: normalizeGender(opts.gender),
    emailPublic: opts.emailPublic,
    phoneNumberPublic: opts.phoneNumberPublic,
  });
}

export function accountEmailPublicChanged(opts: {
  gender: string;
  phoneNumberPublic: boolean;
  emailPublic: boolean;
}): AccountEditableFieldsPatch {
  return new AccountEditableFieldsPatch({
    gender: opts.gender,
    emailPublic: opts.emailPublic,
    phoneNumberPublic: opts.phoneNumberPublic,
  });
}

export function accountPhoneNumberPublicChanged(opts: {
  gender: string;
  emailPublic: boolean;
  phoneNumberPublic: boolean;
}): AccountEditableFieldsPatch {
  return new AccountEditableFieldsPatch({
    gender: opts.gender,
    emailPublic: opts.emailPublic,
    phoneNumberPublic: opts.phoneNumberPublic,
  });
}

export function accountFormSaveNoChanges(opts: {
  target: AccountFormSaveTarget;
  savingAccount: boolean;
  savingProfile: boolean;
  accountError: string | null;
}): AccountFormSaveStatePatch {
  return new AccountFormSaveStatePatch({
    savingAccount: opts.savingAccount,
    savingProfile: opts.savingProfile,
    accountError: opts.accountError,
    notice: _accountFormNoChangesNotice(opts.target),
  });
}

export function loginUsernameUpdateDraftFromForm(opts: { user: CurrentUser; username: string }): AccountUpdateDraft {
  const nextUsername = opts.username.trim();
  const validationError = loginUsernameValidationError(nextUsername);
  if (validationError != null) {
    return AccountUpdateDraft.invalid(validationError);
  }
  if (nextUsername === opts.user.username) {
    return AccountUpdateDraft.noChanges();
  }
  return AccountUpdateDraft.valid({ username: nextUsername });
}

export function accountFormSaveStarted(opts: {
  target: AccountFormSaveTarget;
  savingAccount: boolean;
  savingProfile: boolean;
}): AccountFormSaveStatePatch {
  const { target, savingAccount, savingProfile } = opts;
  return new AccountFormSaveStatePatch({
    savingAccount: _savingAccountForTarget(target, { savingAccount, targetSaving: true }),
    savingProfile: _savingProfileForTarget(target, { savingProfile, targetSaving: true }),
    accountError: null,
    notice: null,
  });
}

export function accountFormSaveCancelled(opts: {
  target: AccountFormSaveTarget;
  savingAccount: boolean;
  savingProfile: boolean;
  accountError: string | null;
  notice: string | null;
}): AccountFormSaveStatePatch {
  const { target, savingAccount, savingProfile } = opts;
  return new AccountFormSaveStatePatch({
    savingAccount: _savingAccountForTarget(target, { savingAccount, targetSaving: false }),
    savingProfile: _savingProfileForTarget(target, { savingProfile, targetSaving: false }),
    accountError: opts.accountError,
    notice: opts.notice,
  });
}

export function accountFormSaveFailed(opts: {
  target: AccountFormSaveTarget;
  savingAccount: boolean;
  savingProfile: boolean;
  failure: unknown;
}): AccountFormSaveStatePatch {
  const { target, savingAccount, savingProfile } = opts;
  return new AccountFormSaveStatePatch({
    savingAccount: _savingAccountForTarget(target, { savingAccount, targetSaving: false }),
    savingProfile: _savingProfileForTarget(target, { savingProfile, targetSaving: false }),
    accountError: userFacingErrorMessage(opts.failure),
    notice: null,
  });
}

export function accountFormSaveSucceeded(opts: {
  target: AccountFormSaveTarget;
  savingAccount: boolean;
  savingProfile: boolean;
}): AccountFormSaveStatePatch {
  const { target, savingAccount, savingProfile } = opts;
  return new AccountFormSaveStatePatch({
    savingAccount: _savingAccountForTarget(target, { savingAccount, targetSaving: false }),
    savingProfile: _savingProfileForTarget(target, { savingProfile, targetSaving: false }),
    accountError: null,
    notice: _accountFormSavedNotice(target),
  });
}

export class AccountPresetAvatarSelectionPatch {
  constructor(opts: {
    defaultAvatarKey: string;
    pendingAvatarAssetId: string | null;
    pendingAvatarUrl: string | null;
    clearUploadedAvatar: boolean;
    notice: string | null;
  }) {
    this.defaultAvatarKey = opts.defaultAvatarKey;
    this.pendingAvatarAssetId = opts.pendingAvatarAssetId;
    this.pendingAvatarUrl = opts.pendingAvatarUrl;
    this.clearUploadedAvatar = opts.clearUploadedAvatar;
    this.notice = opts.notice;
  }

  readonly defaultAvatarKey: string;
  readonly pendingAvatarAssetId: string | null;
  readonly pendingAvatarUrl: string | null;
  readonly clearUploadedAvatar: boolean;
  readonly notice: string | null;
}

export function accountPresetAvatarSelected(opts: {
  defaultAvatarKey: string;
  currentAvatarUrl: string | null;
}): AccountPresetAvatarSelectionPatch {
  const clearUploadedAvatar = shouldClearUploadedAvatarForPreset(opts.currentAvatarUrl);
  return new AccountPresetAvatarSelectionPatch({
    defaultAvatarKey: opts.defaultAvatarKey,
    pendingAvatarAssetId: null,
    pendingAvatarUrl: null,
    clearUploadedAvatar,
    notice: accountPresetAvatarNotice({ clearUploadedAvatar }),
  });
}

export function accountAvatarPreparationStarted(opts: {
  target: AccountAvatarErrorTarget;
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  clearUploadedAvatar: boolean;
  uploadingAvatar: boolean;
  accountError: string | null;
  stickerError: string | null;
}): AccountAvatarStatePatch {
  return _accountAvatarPatch({ ...opts, targetError: null, notice: null });
}

export function accountAvatarUploadStarted(opts: {
  target: AccountAvatarErrorTarget;
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  clearUploadedAvatar: boolean;
  accountError: string | null;
  stickerError: string | null;
}): AccountAvatarStatePatch {
  return _accountAvatarPatch({ ...opts, uploadingAvatar: true, targetError: null, notice: null });
}

export function accountAvatarActionCancelled(opts: {
  target: AccountAvatarErrorTarget;
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  clearUploadedAvatar: boolean;
  accountError: string | null;
  stickerError: string | null;
}): AccountAvatarStatePatch {
  return _accountAvatarPatch({ ...opts, uploadingAvatar: false, targetError: null, notice: null });
}

export function accountAvatarActionFailed(opts: {
  target: AccountAvatarErrorTarget;
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  clearUploadedAvatar: boolean;
  accountError: string | null;
  stickerError: string | null;
  failure: unknown;
}): AccountAvatarStatePatch {
  const { failure, ...rest } = opts;
  return _accountAvatarPatch({
    ...rest,
    uploadingAvatar: false,
    targetError: userFacingErrorMessage(failure),
    notice: null,
  });
}

export function accountAvatarPendingUploadSucceeded(opts: {
  assetId: string;
  assetUrl: string;
  stickerError: string | null;
}): AccountAvatarStatePatch {
  return new AccountAvatarStatePatch({
    pendingAvatarAssetId: opts.assetId,
    pendingAvatarUrl: opts.assetUrl,
    clearUploadedAvatar: false,
    uploadingAvatar: false,
    accountError: null,
    stickerError: opts.stickerError,
    notice: avatarUploadedPendingProfileNotice(),
  });
}

export function accountAvatarProfileUpdatedFromStickerSucceeded(opts: {
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  clearUploadedAvatar: boolean;
  accountError: string | null;
}): AccountAvatarStatePatch {
  return new AccountAvatarStatePatch({
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    clearUploadedAvatar: opts.clearUploadedAvatar,
    uploadingAvatar: false,
    accountError: opts.accountError,
    stickerError: null,
    notice: avatarUpdatedNotice(),
  });
}

export function stickerAvatarUploadFilename(opts: { stickerId: string }): string {
  return `avatar-${opts.stickerId}.png`;
}

export function accountUpdateDraftFromForm(opts: {
  user: CurrentUser;
  username: string;
  email: string;
  emailPublic: boolean;
  phoneNumber: string;
  phoneNumberPublic: boolean;
  language: string;
}): AccountUpdateDraft {
  const { user, emailPublic, phoneNumberPublic } = opts;
  const nextUsername = opts.username.trim();
  const nextEmailValue = opts.email.trim();
  const nextPhoneValue = opts.phoneNumber.trim();
  const usernameError = loginUsernameValidationError(nextUsername);
  if (usernameError != null) {
    return AccountUpdateDraft.invalid(usernameError);
  }
  if (nextEmailValue.length === 0) {
    return AccountUpdateDraft.invalid('邮箱不能为空');
  }

  const changedUsername = nextUsername === user.username ? null : nextUsername;
  const changedEmail = nextEmailValue === (user.email ?? '') ? null : nextEmailValue;
  const changedEmailPublic = emailPublic === user.emailPublic ? null : emailPublic;
  const changedPhone = nextPhoneValue === (user.phoneNumber ?? '') ? null : nextPhoneValue;
  const changedPhonePublic = phoneNumberPublic === user.phoneNumberPublic ? null : phoneNumberPublic;
  const nextLanguage = normalizeAccountLanguage(opts.language);
  const changedLanguage = nextLanguage === normalizeAccountLanguage(user.language) ? null : nextLanguage;

  if (
    changedUsername == null &&
    changedEmail == null &&
    changedEmailPublic == null &&
    changedPhone == null &&
    changedPhonePublic == null &&
    changedLanguage == null
  ) {
    return AccountUpdateDraft.noChanges();
  }

  return AccountUpdateDraft.valid({
    username: changedUsername,
    email: changedEmail,
    emailPublic: changedEmailPublic,
    phoneNumber: changedPhone,
    phoneNumberPublic: changedPhonePublic,
    language: changedLanguage,
  });
}

export function preferencesUpdateDraftFromForm(opts: { user: CurrentUser; language: string }): AccountUpdateDraft {
  const nextLanguage = normalizeAccountLanguage(opts.language);
  if (nextLanguage === normalizeAccountLanguage(opts.user.language)) {
    return AccountUpdateDraft.noChanges();
  }
  return AccountUpdateDraft.valid({ language: nextLanguage });
}

export function normalizeAccountLanguage(language: string): string {
  return normalizeLanguagePreference(language);
}

export function profileUpdateDraftFromForm(opts: {
  user: CurrentUser;
  displayName: string;
  bio: string;
  gender: string;
  defaultAvatarKey: string;
  pendingAvatarAssetId?: string | null;
  clearUploadedAvatar: boolean;
}): ProfileUpdateDraft {
  const { user, gender, defaultAvatarKey, clearUploadedAvatar } = opts;
  const nextDisplayNameValue = opts.displayName.trim();
  const nextBioValue = opts.bio.trim();
  if (nextDisplayNameValue.length === 0) {
    return ProfileUpdateDraft.invalid('用户名不能为空');
  }

  const changedDisplayName = nextDisplayNameValue === user.displayName ? null : nextDisplayNameValue;
  const changedBio = nextBioValue === user.bio ? null : nextBioValue;
  const changedGender = gender === normalizeGender(user.gender) ? null : gender;
  const changedAvatarKey = defaultAvatarKey === user.defaultAvatarKey ? null : defaultAvatarKey;
  const changedAvatarAssetId = opts.pendingAvatarAssetId ?? (clearUploadedAvatar ? '' : null);

  if (
    changedDisplayName == null &&
    changedBio == null &&
    changedGender == null &&
    changedAvatarKey == null &&
    changedAvatarAssetId == null
  ) {
    return ProfileUpdateDraft.noChanges();
  }

  return ProfileUpdateDraft.valid({
    displayName: changedDisplayName,
    bio: changedBio,
    gender: changedGender,
    defaultAvatarKey: changedAvatarKey,
    avatarAssetId: changedAvatarAssetId,
  });
}

export function passwordChangeDraftFromForm(opts: {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
  currentPasswordRequired?: boolean;
}): PasswordChangeDraft {
  const { currentPassword, newPassword, confirmPassword, currentPasswordRequired = true } = opts;
  if ((currentPasswordRequired && currentPassword.length === 0) || newPassword.length === 0 || confirmPassword.length === 0) {
    return PasswordChangeDraft.invalid(currentPasswordRequired ? '请完整填写当前密码、新密码和确认密码' : '请完整填写新密码和确认密码');
  }
  if (newPassword.length < 8) {
    return PasswordChangeDraft.invalid('新密码至少需要 8 个字符');
  }
  if (newPassword !== confirmPassword) {
    return PasswordChangeDraft.invalid('两次输入的新密码不一致');
  }
  return PasswordChangeDraft.valid({ currentPassword, newPassword });
}

function _savingAccountForTarget(
  target: AccountFormSaveTarget,
  opts: { savingAccount: boolean; targetSaving: boolean },
): boolean {
  return target === AccountFormSaveTarget.profile ? opts.savingAccount : opts.targetSaving;
}

function _savingProfileForTarget(
  target: AccountFormSaveTarget,
  opts: { savingProfile: boolean; targetSaving: boolean },
): boolean {
  return target === AccountFormSaveTarget.profile ? opts.targetSaving : opts.savingProfile;
}

function _accountFormNoChangesNotice(target: AccountFormSaveTarget): string {
  switch (target) {
    case AccountFormSaveTarget.account:
      return accountNoBindingChangesNotice();
    case AccountFormSaveTarget.preferences:
      return preferencesNoChangesNotice();
    case AccountFormSaveTarget.profile:
      return profileNoChangesNotice();
  }
}

function _accountFormSavedNotice(target: AccountFormSaveTarget): string {
  switch (target) {
    case AccountFormSaveTarget.account:
      return accountBindingsSavedNotice();
    case AccountFormSaveTarget.preferences:
      return preferencesSavedNotice();
    case AccountFormSaveTarget.profile:
      return profileSavedNotice();
  }
}

function _accountAvatarPatch(opts: {
  target: AccountAvatarErrorTarget;
  pendingAvatarAssetId: string | null;
  pendingAvatarUrl: string | null;
  clearUploadedAvatar: boolean;
  uploadingAvatar: boolean;
  accountError: string | null;
  stickerError: string | null;
  targetError: string | null;
  notice: string | null;
}): AccountAvatarStatePatch {
  return new AccountAvatarStatePatch({
    pendingAvatarAssetId: opts.pendingAvatarAssetId,
    pendingAvatarUrl: opts.pendingAvatarUrl,
    clearUploadedAvatar: opts.clearUploadedAvatar,
    uploadingAvatar: opts.uploadingAvatar,
    accountError: opts.target === AccountAvatarErrorTarget.account ? opts.targetError : opts.accountError,
    stickerError: opts.target === AccountAvatarErrorTarget.sticker ? opts.targetError : opts.stickerError,
    notice: opts.notice,
  });
}
