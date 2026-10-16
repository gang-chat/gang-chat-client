import type { CurrentUser, UserSession } from '../protocol/models';
import { basename } from './file_display';

export enum SessionListBodyState {
  loading = 'loading',
  empty = 'empty',
  results = 'results',
}

export class AccountDeletionConfirmationSpec {
  constructor(opts: { title: string; body: string; expectedText: string; inputHint: string; confirmLabel: string }) {
    this.title = opts.title;
    this.body = opts.body;
    this.expectedText = opts.expectedText;
    this.inputHint = opts.inputHint;
    this.confirmLabel = opts.confirmLabel;
  }

  readonly title: string;
  readonly body: string;
  readonly expectedText: string;
  readonly inputHint: string;
  readonly confirmLabel: string;
}

export function normalizeGender(value: string): string {
  switch (value) {
    case 'male':
    case 'female':
    case 'secret':
      return value;
    default:
      return 'secret';
  }
}

export function formatDateTime(value: Date | null | undefined): string {
  if (value == null) return '未知';
  const year = value.getFullYear().toString().padStart(4, '0');
  const month = (value.getMonth() + 1).toString().padStart(2, '0');
  const day = value.getDate().toString().padStart(2, '0');
  const hour = value.getHours().toString().padStart(2, '0');
  const minute = value.getMinutes().toString().padStart(2, '0');
  return `${year}-${month}-${day} ${hour}:${minute}`;
}

export function canEditUsername(user: CurrentUser, opts: { now?: Date | null } = {}): boolean {
  const canAt = user.canChangeUsernameAt;
  return canAt == null || !(canAt.getTime() > (opts.now ?? new Date()).getTime());
}

export function usernameHelperText(user: CurrentUser, opts: { now?: Date | null } = {}): string {
  if (canEditUsername(user, opts)) {
    return '用于登录和账号识别；同一账号一天只能修改一次。';
  }
  return `下次可修改时间：${formatDateTime(user.canChangeUsernameAt)}`;
}

export function avatarUploadFilename(originalName: string, opts: { now?: Date | null } = {}): string {
  const cleaned = basename(originalName)
    .trim()
    .replace(/[^A-Za-z0-9._-]+/g, '-')
    .replace(/-+/g, '-');
  const stem = cleaned.replace(/\.[A-Za-z0-9]+$/, '');
  const safeStem = stem.length === 0 ? 'avatar' : stem;
  const timestamp = (opts.now ?? new Date()).getTime();
  return `${safeStem}-${timestamp}.png`;
}

export function accountAvatarPreviewPath(opts: {
  clearUploadedAvatar: boolean;
  pendingAvatarUrl?: string | null;
  currentAvatarUrl?: string | null;
}): string | null {
  if (opts.clearUploadedAvatar) return null;
  return opts.pendingAvatarUrl ?? opts.currentAvatarUrl ?? null;
}

export function shouldClearUploadedAvatarForPreset(currentAvatarUrl: string | null | undefined): boolean {
  return currentAvatarUrl != null;
}

export function accountPresetAvatarNotice(opts: { clearUploadedAvatar: boolean }): string | null {
  return opts.clearUploadedAvatar ? '保存用户资料后将使用预设头像' : null;
}

export function accountNoBindingChangesNotice(): string {
  return '没有账号绑定变更';
}

export function accountBindingsSavedNotice(): string {
  return '账号绑定已保存';
}

export function preferencesNoChangesNotice(): string {
  return '没有偏好设置变更';
}

export function preferencesSavedNotice(): string {
  return '偏好设置已保存';
}

export function profileNoChangesNotice(): string {
  return '没有用户资料变更';
}

export function profileSavedNotice(): string {
  return '用户资料已保存';
}

export function avatarPickerOpenFailureMessage(error: unknown): string {
  return '无法打开文件选择器';
}

export function avatarReadFailureMessage(error: unknown): string {
  return '无法读取图片';
}

export function avatarEmptyFileMessage(): string {
  return '图片文件为空';
}

export function avatarUploadedPendingProfileNotice(): string {
  return '头像已上传，保存用户资料后生效';
}

export function avatarUpdatedNotice(): string {
  return '头像已更新';
}

export function passwordUpdatedNotice(): string {
  return '密码已更新';
}

export function canDeleteAccount(user: CurrentUser): boolean {
  return !user.isSuperuser;
}

export function canStartAccountDeletion(opts: {
  hasApi: boolean;
  user: CurrentUser | null;
  deletingAccount: boolean;
}): boolean {
  const { hasApi, user, deletingAccount } = opts;
  return hasApi && user != null && canDeleteAccount(user) && !deletingAccount;
}

export function accountDeletionDescription(user: CurrentUser): string {
  if (!canDeleteAccount(user)) return '超级用户账号不能被注销。';
  return '注销后账号不能继续登录，当前会话会失效，服务端将删除和该账号有关的信息。';
}

export function accountDeletionConfirmationSpec(user: CurrentUser): AccountDeletionConfirmationSpec {
  return new AccountDeletionConfirmationSpec({
    title: '确认注销账号',
    body: accountDeletionDescription(user),
    expectedText: user.username,
    inputHint: `输入 ${user.username} 确认`,
    confirmLabel: '确认注销',
  });
}

export function canStartPasswordChange(opts: { hasApi: boolean; changingPassword: boolean }): boolean {
  return opts.hasApi && !opts.changingPassword;
}

export function sessionStateText(session: UserSession, opts: { now?: Date | null } = {}): string {
  if (session.isCurrent) return '当前会话';
  if (session.revokedAt != null) return '已失效';
  if (!(session.expiresAt.getTime() > (opts.now ?? new Date()).getTime())) return '已过期';
  return '有效';
}

export function sessionDeviceLabel(session: UserSession): string {
  const userAgent = session.userAgent?.trim();
  if (userAgent == null || userAgent.length === 0) return '未知系统';

  const gangChatMatch = /^GangChat Client \((Android|Fuchsia|iOS|Linux|macOS|Windows)\)$/i.exec(userAgent);
  if (gangChatMatch != null) {
    return _normalizeSystemName(gangChatMatch[1]!);
  }

  const normalized = userAgent.toLowerCase();
  if (normalized.includes('android')) return 'Android';
  if (normalized.includes('windows')) return 'Windows';
  if (normalized.includes('iphone') || normalized.includes('ipad') || normalized.includes('ios')) {
    return 'iOS';
  }
  if (normalized.includes('macintosh') || normalized.includes('mac os x') || normalized.includes('macos')) {
    return 'macOS';
  }
  if (normalized.includes('linux')) return 'Linux';
  if (normalized.includes('fuchsia')) return 'Fuchsia';
  return '未知系统';
}

function _normalizeSystemName(value: string): string {
  switch (value.toLowerCase()) {
    case 'android':
      return 'Android';
    case 'fuchsia':
      return 'Fuchsia';
    case 'ios':
      return 'iOS';
    case 'linux':
      return 'Linux';
    case 'macos':
      return 'macOS';
    case 'windows':
      return 'Windows';
    default:
      return '未知系统';
  }
}

export function sessionIpAddressLabel(session: UserSession): string {
  const ipAddress = session.ipAddress?.trim();
  if (ipAddress == null || ipAddress.length === 0) return '未知 IP';
  return ipAddress;
}

export function sessionLocationLabel(session: UserSession): string {
  const location = session.location.trim();
  if (location.length === 0) return '未知地点';
  return location;
}

export function sessionDetailText(session: UserSession): string {
  return `位置：${sessionLocationLabel(session)} · IP：${sessionIpAddressLabel(session)} · 最近活动：${formatDateTime(session.lastUsedAt)}`;
}

export function sessionListBodyState(opts: {
  loading: boolean;
  sessions: Iterable<UserSession>;
}): SessionListBodyState {
  const isNotEmpty = !opts.sessions[Symbol.iterator]().next().done;
  if (isNotEmpty) return SessionListBodyState.results;
  if (opts.loading) return SessionListBodyState.loading;
  return SessionListBodyState.empty;
}

export function initials(value: string): string {
  const trimmed = value.trim();
  if (trimmed.length === 0) return '?';
  const parts = trimmed.split(/\s+/).filter((part) => part.length > 0);
  const result = parts
    .slice(0, 2)
    .map((part) => String.fromCodePoint(part.codePointAt(0)!))
    .join('');
  return result.toUpperCase();
}
