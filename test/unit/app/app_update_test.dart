import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/app_update.dart';

void main() {
  test('only desktop update launches terminate the current application', () {
    expect(
      shouldTerminateApplicationAfterInstallerLaunch(AppUpdatePlatform.windows),
      isTrue,
    );
    expect(
      shouldTerminateApplicationAfterInstallerLaunch(AppUpdatePlatform.macos),
      isTrue,
    );
    expect(
      shouldTerminateApplicationAfterInstallerLaunch(AppUpdatePlatform.android),
      isFalse,
    );
  });

  test('Android update copy allows backgrounding but warns against exit', () {
    final androidBody = updateDownloadConfirmationBody(
      AppUpdatePlatform.android,
    );
    expect(androidBody, contains('在后台'));
    expect(androidBody, contains('点击安装'));
    expect(androidBody, contains('未开启通知权限'));
    expect(androidBody, contains('重新打开 Gang Chat'));
    expect(androidBody, contains('请勿退出 Gang Chat'));
    expect(androidBody, isNot(contains('请勿关闭 Gang Chat')));

    final windowsBody = updateDownloadConfirmationBody(
      AppUpdatePlatform.windows,
    );
    expect(windowsBody, contains('退出当前程序并启动安装程序'));
    expect(windowsBody, isNot(contains('在后台')));
  });

  test('parseReleaseAssetsFromS3List accepts GangChat release names only', () {
    final assets = parseReleaseAssetsFromS3List('''
      <ListBucketResult>
        <Contents>
          <Key>releases/GangChat_v0.5.0.exe</Key>
          <LastModified>2026-07-08T01:02:03.000Z</LastModified>
        </Contents>
        <Contents>
          <Key>releases/GangChat_v0.5.1.dmg</Key>
          <LastModified>2026-07-09T04:05:06.000Z</LastModified>
        </Contents>
        <Contents>
          <Key>releases/GangChat_v0.5.2.apk</Key>
          <LastModified>2026-07-10T05:06:07.000Z</LastModified>
        </Contents>
        <Contents><Key>releases/GangChat-0.5.2-windows.zip</Key></Contents>
        <Contents><Key>avatars/not-a-release.png</Key></Contents>
      </ListBucketResult>
    ''');

    expect(assets, hasLength(3));
    expect(assets.first.version, '0.5.0');
    expect(assets.first.platform, AppUpdatePlatform.windows);
    expect(assets.first.releasedAt, DateTime.utc(2026, 7, 8, 1, 2, 3));
    expect(assets[1].version, '0.5.1');
    expect(assets[1].platform, AppUpdatePlatform.macos);
    expect(assets[1].releasedAt, DateTime.utc(2026, 7, 9, 4, 5, 6));
    expect(assets.last.version, '0.5.2');
    expect(assets.last.platform, AppUpdatePlatform.android);
    expect(assets.last.releasedAt, DateTime.utc(2026, 7, 10, 5, 6, 7));
  });

  test('latestReleaseAssetForPlatform chooses highest semantic version', () {
    final assets = [
      const ReleaseAsset(
        key: 'releases/GangChat_v0.5.0.exe',
        version: '0.5.0',
        platform: AppUpdatePlatform.windows,
      ),
      const ReleaseAsset(
        key: 'releases/GangChat_v0.10.0.exe',
        version: '0.10.0',
        platform: AppUpdatePlatform.windows,
      ),
      const ReleaseAsset(
        key: 'releases/GangChat_v0.9.0.dmg',
        version: '0.9.0',
        platform: AppUpdatePlatform.macos,
      ),
      const ReleaseAsset(
        key: 'releases/GangChat_v0.11.0.apk',
        version: '0.11.0',
        platform: AppUpdatePlatform.android,
      ),
    ];

    final latest = latestReleaseAssetForPlatform(
      assets,
      AppUpdatePlatform.windows,
    );

    expect(latest?.version, '0.10.0');
    expect(
      latestReleaseAssetForPlatform(assets, AppUpdatePlatform.android)?.version,
      '0.11.0',
    );
  });

  test('releaseAssetUrl encodes each key segment', () {
    expect(
      releaseAssetUrl(
        'https://os.example.test/gang-chat/',
        'releases/GangChat_v1.2.3.exe',
      ),
      'https://os.example.test/gang-chat/releases/GangChat_v1.2.3.exe',
    );
  });

  test('releaseTimeLabel formats release time when available', () {
    expect(releaseTimeLabel(null), '暂无');
    expect(
      releaseTimeLabel(DateTime.utc(2026, 7, 8, 1, 5)),
      '2026/07/08 09:05 UTC+08:00',
    );
  });

  test('releaseNotesLabel falls back to none', () {
    expect(releaseNotesLabel(null), '无');
    expect(releaseNotesLabel('   '), '无');
    expect(releaseNotesLabel('修复更新安装流程'), '修复更新安装流程');
  });
}
