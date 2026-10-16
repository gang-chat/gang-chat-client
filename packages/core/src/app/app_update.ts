import { _tryParseDateTime, compareAppVersions, officialDateTimeLabel } from './settings_about';

export enum AppUpdatePlatform {
  windows = 'windows',
  macos = 'macos',
  android = 'android',
}

export function shouldTerminateApplicationAfterInstallerLaunch(platform: AppUpdatePlatform): boolean {
  return platform === AppUpdatePlatform.windows;
}

/**
 * macOS hands the DMG download to the browser when Sparkle isn't configured.
 * A DMG downloaded by the sandboxed app is marked "created by a sandboxed
 * app", and macOS refuses to launch an app copied out of it.
 */
export function downloadsUpdateInBrowser(platform: AppUpdatePlatform): boolean {
  return platform === AppUpdatePlatform.macos;
}

export function updateDownloadConfirmationBody(platform: AppUpdatePlatform): string {
  if (platform === AppUpdatePlatform.android) {
    return (
      '将下载新版安装包。下载完成后会打开系统安装界面；如果 Gang Chat 在后台，' +
      '将弹出“点击安装”通知。若未开启通知权限，重新打开 Gang Chat 时也会弹出安装界面。' +
      '安装完成前请勿退出 Gang Chat。'
    );
  }
  if (downloadsUpdateInBrowser(platform)) {
    return '将在浏览器中下载新版安装包。下载完成后打开 DMG，' + '把 GangChat 拖到“应用程序”文件夹替换旧版本即可。';
  }
  return '将下载新版安装包。下载完成后，Gang Chat 会退出当前程序并启动安装程序。';
}

/** Sparkle's update feed, published next to the installers. */
export function sparkleAppcastUrl(bucketUrl: string): string {
  return releaseAssetUrl(bucketUrl, 'releases/appcast.xml');
}

export class ReleaseAsset {
  constructor(opts: { key: string; version: string; platform: AppUpdatePlatform; releasedAt?: Date | null }) {
    this.key = opts.key;
    this.version = opts.version;
    this.platform = opts.platform;
    this.releasedAt = opts.releasedAt ?? null;
  }

  readonly key: string;
  readonly version: string;
  readonly platform: AppUpdatePlatform;
  readonly releasedAt: Date | null;
}

export class AvailableAppUpdate {
  constructor(opts: {
    currentVersion: string;
    latestVersion: string;
    asset: ReleaseAsset;
    downloadUrl: string;
    releaseNotes?: string | null;
  }) {
    this.currentVersion = opts.currentVersion;
    this.latestVersion = opts.latestVersion;
    this.asset = opts.asset;
    this.downloadUrl = opts.downloadUrl;
    this.releaseNotes = opts.releaseNotes ?? null;
  }

  readonly currentVersion: string;
  readonly latestVersion: string;
  readonly asset: ReleaseAsset;
  readonly downloadUrl: string;
  readonly releaseNotes: string | null;
}

export function parseReleaseAssetsFromS3List(xmlText: string): ReleaseAsset[] {
  return _s3Objects(xmlText)
    .map((object) => parseReleaseAssetKey(object.key, { releasedAt: object.lastModified }))
    .filter((asset): asset is ReleaseAsset => asset != null);
}

export function parseReleaseAssetKey(key: string, opts: { releasedAt?: Date | null } = {}): ReleaseAsset | null {
  const match = /^releases\/GangChat_v(\d+\.\d+\.\d+)\.(exe|dmg|apk)$/i.exec(key.trim());
  if (match == null) return null;

  const extension = match[2]!.toLowerCase();
  let platform: AppUpdatePlatform;
  switch (extension) {
    case 'exe':
      platform = AppUpdatePlatform.windows;
      break;
    case 'dmg':
      platform = AppUpdatePlatform.macos;
      break;
    default:
      platform = AppUpdatePlatform.android;
  }
  return new ReleaseAsset({
    key: key.trim(),
    version: match[1]!,
    platform,
    releasedAt: opts.releasedAt ?? null,
  });
}

export function latestReleaseAssetForPlatform(
  assets: Iterable<ReleaseAsset>,
  platform: AppUpdatePlatform,
): ReleaseAsset | null {
  let latest: ReleaseAsset | null = null;
  for (const asset of assets) {
    if (asset.platform !== platform) continue;
    if (latest == null || compareAppVersions(asset.version, latest.version) > 0) {
      latest = asset;
    }
  }
  return latest;
}

export function isNewerAppVersion(opts: { currentVersion: string; latestVersion: string }): boolean {
  return compareAppVersions(opts.currentVersion, opts.latestVersion) < 0;
}

export function releaseAssetUrl(bucketUrl: string, key: string): string {
  const normalizedBucket = bucketUrl.endsWith('/') ? bucketUrl.substring(0, bucketUrl.length - 1) : bucketUrl;
  const encodedKey = key.split('/').map(_encodeComponent).join('/');
  return `${normalizedBucket}/${encodedKey}`;
}

export function releaseTimeLabel(releasedAt: Date | null | undefined): string {
  return officialDateTimeLabel(releasedAt);
}

export function releaseNotesLabel(releaseNotes: string | null | undefined): string {
  const trimmed = releaseNotes?.trim() ?? '';
  return trimmed.length === 0 ? '无' : trimmed;
}

/** Dart `Uri.encodeComponent`, which also escapes `!'()*`. */
function _encodeComponent(value: string): string {
  return encodeURIComponent(value).replace(/[!'()*]/g, (c) => `%${c.charCodeAt(0).toString(16).toUpperCase()}`);
}

class _S3Object {
  constructor(opts: { key: string; lastModified?: Date | null }) {
    this.key = opts.key;
    this.lastModified = opts.lastModified ?? null;
  }

  readonly key: string;
  readonly lastModified: Date | null;
}

function _s3Objects(xmlText: string): _S3Object[] {
  const objects: _S3Object[] = [];
  for (const match of xmlText.matchAll(/<Contents>(.*?)<\/Contents>/gis)) {
    const object = _s3ObjectFromContents(match[1] ?? '');
    if (object != null) objects.push(object);
  }
  return objects;
}

function _s3ObjectFromContents(contents: string): _S3Object | null {
  const key = _firstXmlValue(contents, 'Key')?.trim();
  if (key == null || key.length === 0) return null;

  return new _S3Object({
    key,
    lastModified: _tryParseDateTime(_firstXmlValue(contents, 'LastModified')?.trim() ?? ''),
  });
}

function _firstXmlValue(xmlText: string, tagName: string): string | null {
  const match = new RegExp(`<${tagName}>(.*?)</${tagName}>`, 'is').exec(xmlText);
  if (match == null) return null;
  return _decodeXmlText(match[1] ?? '');
}

function _decodeXmlText(value: string): string {
  return value
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&quot;', '"')
    .replaceAll('&apos;', "'")
    .replaceAll('&amp;', '&');
}
