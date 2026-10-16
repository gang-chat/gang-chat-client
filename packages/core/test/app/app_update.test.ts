import { describe, expect, test } from 'vitest';

import {
  AppUpdatePlatform,
  ReleaseAsset,
  downloadsUpdateInBrowser,
  latestReleaseAssetForPlatform,
  parseReleaseAssetsFromS3List,
  releaseAssetUrl,
  releaseNotesLabel,
  releaseTimeLabel,
  shouldTerminateApplicationAfterInstallerLaunch,
  sparkleAppcastUrl,
  updateDownloadConfirmationBody,
} from '../../src/app/app_update';

describe('app_update', () => {
  test('only the Windows installer launch terminates the application', () => {
    expect(shouldTerminateApplicationAfterInstallerLaunch(AppUpdatePlatform.windows)).toBe(true);
    // macOS hands the DMG to the browser and keeps running.
    expect(shouldTerminateApplicationAfterInstallerLaunch(AppUpdatePlatform.macos)).toBe(false);
    expect(shouldTerminateApplicationAfterInstallerLaunch(AppUpdatePlatform.android)).toBe(false);
  });

  test('macOS downloads updates in the browser, not inside the sandbox', () => {
    expect(downloadsUpdateInBrowser(AppUpdatePlatform.macos)).toBe(true);
    expect(downloadsUpdateInBrowser(AppUpdatePlatform.windows)).toBe(false);
    expect(downloadsUpdateInBrowser(AppUpdatePlatform.android)).toBe(false);

    const macBody = updateDownloadConfirmationBody(AppUpdatePlatform.macos);
    expect(macBody).toContain('浏览器');
    expect(macBody).toContain('应用程序');
    expect(macBody).not.toContain('退出当前程序');
  });

  test('the Sparkle appcast sits next to the installers', () => {
    expect(sparkleAppcastUrl('https://os.ky-z.com:9000/gang-chat')).toBe(
      'https://os.ky-z.com:9000/gang-chat/releases/appcast.xml',
    );
    expect(sparkleAppcastUrl('https://os.ky-z.com:9000/gang-chat/')).toBe(
      'https://os.ky-z.com:9000/gang-chat/releases/appcast.xml',
    );
  });

  test('Android update copy allows backgrounding but warns against exit', () => {
    const androidBody = updateDownloadConfirmationBody(AppUpdatePlatform.android);
    expect(androidBody).toContain('在后台');
    expect(androidBody).toContain('点击安装');
    expect(androidBody).toContain('未开启通知权限');
    expect(androidBody).toContain('重新打开 Gang Chat');
    expect(androidBody).toContain('请勿退出 Gang Chat');
    expect(androidBody).not.toContain('请勿关闭 Gang Chat');

    const windowsBody = updateDownloadConfirmationBody(AppUpdatePlatform.windows);
    expect(windowsBody).toContain('退出当前程序并启动安装程序');
    expect(windowsBody).not.toContain('在后台');
  });

  test('parseReleaseAssetsFromS3List accepts GangChat release names only', () => {
    const assets = parseReleaseAssetsFromS3List(`
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
    `);

    expect(assets).toHaveLength(3);
    expect(assets[0]!.version).toBe('0.5.0');
    expect(assets[0]!.platform).toBe(AppUpdatePlatform.windows);
    expect(assets[0]!.releasedAt).toEqual(new Date(Date.UTC(2026, 6, 8, 1, 2, 3)));
    expect(assets[1]!.version).toBe('0.5.1');
    expect(assets[1]!.platform).toBe(AppUpdatePlatform.macos);
    expect(assets[1]!.releasedAt).toEqual(new Date(Date.UTC(2026, 6, 9, 4, 5, 6)));
    expect(assets[2]!.version).toBe('0.5.2');
    expect(assets[2]!.platform).toBe(AppUpdatePlatform.android);
    expect(assets[2]!.releasedAt).toEqual(new Date(Date.UTC(2026, 6, 10, 5, 6, 7)));
  });

  test('latestReleaseAssetForPlatform chooses highest semantic version', () => {
    const assets = [
      new ReleaseAsset({ key: 'releases/GangChat_v0.5.0.exe', version: '0.5.0', platform: AppUpdatePlatform.windows }),
      new ReleaseAsset({ key: 'releases/GangChat_v0.10.0.exe', version: '0.10.0', platform: AppUpdatePlatform.windows }),
      new ReleaseAsset({ key: 'releases/GangChat_v0.9.0.dmg', version: '0.9.0', platform: AppUpdatePlatform.macos }),
      new ReleaseAsset({ key: 'releases/GangChat_v0.11.0.apk', version: '0.11.0', platform: AppUpdatePlatform.android }),
    ];

    expect(latestReleaseAssetForPlatform(assets, AppUpdatePlatform.windows)?.version).toBe('0.10.0');
    expect(latestReleaseAssetForPlatform(assets, AppUpdatePlatform.android)?.version).toBe('0.11.0');
  });

  test('releaseAssetUrl encodes each key segment', () => {
    expect(releaseAssetUrl('https://os.example.test/gang-chat/', 'releases/GangChat_v1.2.3.exe')).toBe(
      'https://os.example.test/gang-chat/releases/GangChat_v1.2.3.exe',
    );
    expect(releaseAssetUrl('https://os.example.test/gang-chat', 'releases/a b(1).exe')).toBe(
      'https://os.example.test/gang-chat/releases/a%20b%281%29.exe',
    );
  });

  test('releaseTimeLabel formats release time when available', () => {
    expect(releaseTimeLabel(null)).toBe('暂无');
    expect(releaseTimeLabel(new Date(Date.UTC(2026, 6, 8, 1, 5)))).toBe('2026/07/08 09:05 UTC+08:00');
  });

  test('releaseNotesLabel falls back to none', () => {
    expect(releaseNotesLabel(null)).toBe('无');
    expect(releaseNotesLabel('   ')).toBe('无');
    expect(releaseNotesLabel('修复更新安装流程')).toBe('修复更新安装流程');
  });
});
