/**
 * Build-time version, the equivalent of the Flutter
 * `--dart-define=GANG_CHAT_VERSION=...` flag. The host's bundler defines
 * `__GANG_CHAT_VERSION__` for release builds; development builds and tests
 * leave it undefined and get the fixed `1.0.0`, like the Dart debug build.
 */
declare const __GANG_CHAT_VERSION__: string | undefined;

const _gangChatClientBuildVersion =
  typeof __GANG_CHAT_VERSION__ === 'string' && __GANG_CHAT_VERSION__.length > 0 ? __GANG_CHAT_VERSION__ : '1.0.0';
// PORT: Dart pinned debug builds to 1.0.0 via `dart.vm.product`; here the
// version is only defined for release builds, so the fallback covers both.
export const gangChatClientVersion = _gangChatClientBuildVersion;

export const gangChatClientReleaseTimestamp = '2026-07-12T20:34:27.126Z';
export const gangChatOfficialTimeZoneLabel = 'UTC+08:00';
export const gangChatClientInstallInfoFileName = 'gang_chat_install_info.txt';
export const gangChatSupportEmail = 'gang-chat@outlook.com';
export const defaultAutoUpdatePromptEnabled = true;

/**
 * The official (UTC+8) wall-clock time of [value]. The result is a `Date`
 * whose UTC fields read as Beijing time, like the shifted UTC `DateTime` in
 * the Dart code.
 */
export function gangChatOfficialTime(value: Date): Date {
  return new Date(value.getTime() + 8 * 60 * 60 * 1000);
}

export function officialDateTimeLabel(value: Date | null | undefined): string {
  if (value == null) return '暂无';
  const official = gangChatOfficialTime(value);
  const year = _pad(official.getUTCFullYear(), 4);
  const month = _pad(official.getUTCMonth() + 1, 2);
  const day = _pad(official.getUTCDate(), 2);
  const hour = _pad(official.getUTCHours(), 2);
  const minute = _pad(official.getUTCMinutes(), 2);
  return `${year}/${month}/${day} ${hour}:${minute} ${gangChatOfficialTimeZoneLabel}`;
}

export function officialVersionTimeLabel(
  value: string | null | undefined,
  opts: { fallback?: string } = {},
): string {
  const { fallback = gangChatClientReleaseTimestamp } = opts;
  const timestamp = _tryParseDateTime(value?.trim() ?? '');
  const fallbackTimestamp = _tryParseDateTime(fallback);
  return officialDateTimeLabel(timestamp ?? fallbackTimestamp);
}

export function installTimeLabel(value: string | null | undefined, opts: { fallback?: string } = {}): string {
  const { fallback = gangChatClientReleaseTimestamp } = opts;
  const normalized = value?.trim() ?? '';
  const timestamp = _tryParseDateTime(normalized);
  if (timestamp != null) return localDateTimeLabel(timestamp);
  if (/^\d{4}\/\d{2}\/\d{2}$/.test(normalized)) {
    return normalized;
  }
  return localDateTimeLabel(_tryParseDateTime(fallback));
}

export function localDateTimeLabel(value: Date | null | undefined): string {
  if (value == null) return '暂无';
  const year = _pad(value.getFullYear(), 4);
  const month = _pad(value.getMonth() + 1, 2);
  const day = _pad(value.getDate(), 2);
  const hour = _pad(value.getHours(), 2);
  const minute = _pad(value.getMinutes(), 2);
  return `${year}/${month}/${day} ${hour}:${minute}`;
}

export function appVersionLabel(version: string): string {
  const normalized = version.trim();
  if (normalized.length === 0) return 'v0.0.0';
  return normalized.startsWith('v') ? normalized : `v${normalized}`;
}

export function appVersionNumberLabel(version: string): string {
  const normalized = version.trim();
  if (normalized.length === 0) return '0.0.0';
  return normalized.startsWith('v') ? normalized.substring(1) : normalized;
}

export function updateCheckSucceededText(opts: { currentVersion: string; latestVersion: string }): string {
  const comparison = compareAppVersions(opts.currentVersion, opts.latestVersion);
  if (comparison >= 0) return '当前已是最新版本';
  return `发现新版本 ${appVersionLabel(opts.latestVersion)}`;
}

export function feedbackMailSubject(version: string): string {
  return `Gang Chat 意见反馈 (${appVersionLabel(version)})`;
}

export function feedbackMailBody(opts: { senderEmail: string; currentVersion: string }): string {
  return [
    `发件人（绑定邮箱）：${opts.senderEmail}`,
    `当前版本：${appVersionLabel(opts.currentVersion)}`,
    '',
    '请在这里填写你的意见反馈：',
  ].join('\n');
}

export function boundEmailForFeedback(email: string | null | undefined): string | null {
  const trimmed = email?.trim();
  if (trimmed == null || trimmed.length === 0) return null;
  return trimmed;
}

export class AutoUpdatePromptStore {
  read(): Promise<boolean> {
    throw new Error('AutoUpdatePromptStore.read must be implemented.');
  }

  write(enabled: boolean): Promise<void> {
    throw new Error('AutoUpdatePromptStore.write must be implemented.');
  }

  readIgnoredVersion(): Promise<string | null> {
    throw new Error('AutoUpdatePromptStore.readIgnoredVersion must be implemented.');
  }

  writeIgnoredVersion(version: string | null): Promise<void> {
    throw new Error('AutoUpdatePromptStore.writeIgnoredVersion must be implemented.');
  }
}

export function compareAppVersions(left: string, right: string): number {
  const leftParts = _versionParts(left);
  const rightParts = _versionParts(right);
  const length = leftParts.length > rightParts.length ? leftParts.length : rightParts.length;
  for (let index = 0; index < length; index++) {
    const leftValue = index < leftParts.length ? leftParts[index]! : 0;
    const rightValue = index < rightParts.length ? rightParts[index]! : 0;
    if (leftValue !== rightValue) return leftValue < rightValue ? -1 : 1;
  }
  return 0;
}

function _versionParts(version: string): number[] {
  const withoutBuild = version.trim().split('+')[0]!;
  const withoutPrefix = withoutBuild.startsWith('v') ? withoutBuild.substring(1) : withoutBuild;
  return withoutPrefix.split('.').map((part) => _tryParseInt(part.replace(/[^0-9].*$/, '')) ?? 0);
}

function _tryParseInt(value: string): number | null {
  return /^[+-]?\d+$/.test(value.trim()) ? Number.parseInt(value.trim(), 10) : null;
}

function _pad(value: number, width: number): string {
  return value.toString().padStart(width, '0');
}

const _dateTimeFormat =
  /^([+-]?\d{4,6})-?(\d\d)-?(\d\d)(?:[ T](\d\d)(?::?(\d\d)(?::?(\d\d)(?:[.,](\d+))?)?)?( ?[zZ]| ?([-+])(\d\d)(?::?(\d\d))?)?)?$/;

/**
 * Port of Dart's `DateTime.tryParse`: accepts the same ISO-8601 subset
 * (rejecting the looser formats `Date.parse` takes) and, like Dart, reads a
 * value without a timezone as local time.
 */
export function _tryParseDateTime(value: string): Date | null {
  const match = _dateTimeFormat.exec(value);
  if (match == null) return null;
  const year = Number.parseInt(match[1]!, 10);
  const month = Number.parseInt(match[2]!, 10);
  const day = Number.parseInt(match[3]!, 10);
  const hour = match[4] == null ? 0 : Number.parseInt(match[4], 10);
  const minute = match[5] == null ? 0 : Number.parseInt(match[5], 10);
  const second = match[6] == null ? 0 : Number.parseInt(match[6], 10);
  const fraction = match[7];
  // Dart keeps microseconds; a JS Date holds milliseconds.
  const millisecond = fraction == null ? 0 : Math.trunc(Number(`0.${fraction}`) * 1000);
  const isUtc = match[8] != null;
  let date: Date;
  if (isUtc) {
    const utc = new Date(0);
    utc.setUTCFullYear(year, month - 1, day);
    utc.setUTCHours(hour, minute, second, millisecond);
    let offsetMinutes = 0;
    if (match[9] != null) {
      const sign = match[9] === '-' ? -1 : 1;
      offsetMinutes = sign * (Number.parseInt(match[10]!, 10) * 60 + (match[11] == null ? 0 : Number.parseInt(match[11], 10)));
    }
    date = new Date(utc.getTime() - offsetMinutes * 60 * 1000);
  } else {
    date = new Date(0);
    date.setFullYear(year, month - 1, day);
    date.setHours(hour, minute, second, millisecond);
  }
  return Number.isNaN(date.getTime()) ? null : date;
}
