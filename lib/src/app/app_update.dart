import 'settings_about.dart';

enum AppUpdatePlatform { windows, macos, android }

bool shouldTerminateApplicationAfterInstallerLaunch(
  AppUpdatePlatform platform,
) => platform != AppUpdatePlatform.android;

String updateDownloadConfirmationBody(AppUpdatePlatform platform) {
  if (platform == AppUpdatePlatform.android) {
    return '将下载新版安装包。下载完成后会打开系统安装界面；如果 Gang Chat 在后台，'
        '将弹出“点击安装”通知。若未开启通知权限，重新打开 Gang Chat 时也会弹出安装界面。'
        '安装完成前请勿退出 Gang Chat。';
  }
  return '将下载新版安装包。下载完成后，Gang Chat 会退出当前程序并启动安装程序。';
}

class ReleaseAsset {
  const ReleaseAsset({
    required this.key,
    required this.version,
    required this.platform,
    this.releasedAt,
  });

  final String key;
  final String version;
  final AppUpdatePlatform platform;
  final DateTime? releasedAt;
}

class AvailableAppUpdate {
  const AvailableAppUpdate({
    required this.currentVersion,
    required this.latestVersion,
    required this.asset,
    required this.downloadUrl,
    this.releaseNotes,
  });

  final String currentVersion;
  final String latestVersion;
  final ReleaseAsset asset;
  final Uri downloadUrl;
  final String? releaseNotes;
}

List<ReleaseAsset> parseReleaseAssetsFromS3List(String xmlText) {
  return _s3Objects(xmlText)
      .map(
        (object) =>
            parseReleaseAssetKey(object.key, releasedAt: object.lastModified),
      )
      .whereType<ReleaseAsset>()
      .toList(growable: false);
}

ReleaseAsset? parseReleaseAssetKey(String key, {DateTime? releasedAt}) {
  final match = RegExp(
    r'^releases/GangChat_v(\d+\.\d+\.\d+)\.(exe|dmg|apk)$',
    caseSensitive: false,
  ).firstMatch(key.trim());
  if (match == null) return null;

  final extension = match.group(2)!.toLowerCase();
  return ReleaseAsset(
    key: key.trim(),
    version: match.group(1)!,
    platform: switch (extension) {
      'exe' => AppUpdatePlatform.windows,
      'dmg' => AppUpdatePlatform.macos,
      _ => AppUpdatePlatform.android,
    },
    releasedAt: releasedAt,
  );
}

ReleaseAsset? latestReleaseAssetForPlatform(
  Iterable<ReleaseAsset> assets,
  AppUpdatePlatform platform,
) {
  ReleaseAsset? latest;
  for (final asset in assets.where((asset) => asset.platform == platform)) {
    if (latest == null ||
        compareAppVersions(asset.version, latest.version) > 0) {
      latest = asset;
    }
  }
  return latest;
}

bool isNewerAppVersion({
  required String currentVersion,
  required String latestVersion,
}) {
  return compareAppVersions(currentVersion, latestVersion) < 0;
}

String releaseAssetUrl(String bucketUrl, String key) {
  final normalizedBucket = bucketUrl.endsWith('/')
      ? bucketUrl.substring(0, bucketUrl.length - 1)
      : bucketUrl;
  final encodedKey = key.split('/').map(Uri.encodeComponent).join('/');
  return '$normalizedBucket/$encodedKey';
}

String releaseTimeLabel(DateTime? releasedAt) {
  return officialDateTimeLabel(releasedAt);
}

String releaseNotesLabel(String? releaseNotes) {
  final trimmed = releaseNotes?.trim() ?? '';
  return trimmed.isEmpty ? '无' : trimmed;
}

List<_S3Object> _s3Objects(String xmlText) {
  return RegExp(
        r'<Contents>(.*?)</Contents>',
        dotAll: true,
        caseSensitive: false,
      )
      .allMatches(xmlText)
      .map((match) => _s3ObjectFromContents(match.group(1) ?? ''))
      .whereType<_S3Object>()
      .toList(growable: false);
}

_S3Object? _s3ObjectFromContents(String contents) {
  final key = _firstXmlValue(contents, 'Key')?.trim();
  if (key == null || key.isEmpty) return null;

  return _S3Object(
    key: key,
    lastModified: DateTime.tryParse(
      _firstXmlValue(contents, 'LastModified')?.trim() ?? '',
    ),
  );
}

String? _firstXmlValue(String xmlText, String tagName) {
  final match = RegExp(
    '<$tagName>(.*?)</$tagName>',
    dotAll: true,
    caseSensitive: false,
  ).firstMatch(xmlText);
  if (match == null) return null;
  return _decodeXmlText(match.group(1) ?? '');
}

class _S3Object {
  const _S3Object({required this.key, this.lastModified});

  final String key;
  final DateTime? lastModified;
}

String _decodeXmlText(String value) {
  return value
      .replaceAll('&lt;', '<')
      .replaceAll('&gt;', '>')
      .replaceAll('&quot;', '"')
      .replaceAll('&apos;', "'")
      .replaceAll('&amp;', '&');
}
