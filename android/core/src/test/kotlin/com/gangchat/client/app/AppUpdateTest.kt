package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    @Test
    fun `only the Windows installer launch terminates the application`() {
        assertTrue(
            shouldTerminateApplicationAfterInstallerLaunch(AppUpdatePlatform.windows),
        )
        // macOS hands the DMG to the browser and keeps running.
        assertFalse(
            shouldTerminateApplicationAfterInstallerLaunch(AppUpdatePlatform.macos),
        )
        assertFalse(
            shouldTerminateApplicationAfterInstallerLaunch(AppUpdatePlatform.android),
        )
    }

    @Test
    fun `macOS downloads updates in the browser, not inside the sandbox`() {
        assertTrue(downloadsUpdateInBrowser(AppUpdatePlatform.macos))
        assertFalse(downloadsUpdateInBrowser(AppUpdatePlatform.windows))
        assertFalse(downloadsUpdateInBrowser(AppUpdatePlatform.android))

        val macBody = updateDownloadConfirmationBody(AppUpdatePlatform.macos)
        assertTrue(macBody.contains("浏览器"))
        assertTrue(macBody.contains("应用程序"))
        assertFalse(macBody.contains("退出当前程序"))
    }

    @Test
    fun `the Sparkle appcast sits next to the installers`() {
        assertEquals(
            "https://os.ky-z.com:9000/gang-chat/releases/appcast.xml",
            sparkleAppcastUrl("https://os.ky-z.com:9000/gang-chat"),
        )
        assertEquals(
            "https://os.ky-z.com:9000/gang-chat/releases/appcast.xml",
            sparkleAppcastUrl("https://os.ky-z.com:9000/gang-chat/"),
        )
    }

    @Test
    fun `Android update copy allows backgrounding but warns against exit`() {
        val androidBody =
            updateDownloadConfirmationBody(
                AppUpdatePlatform.android,
            )
        assertTrue(androidBody.contains("在后台"))
        assertTrue(androidBody.contains("点击安装"))
        assertTrue(androidBody.contains("未开启通知权限"))
        assertTrue(androidBody.contains("重新打开 Gang Chat"))
        assertTrue(androidBody.contains("请勿退出 Gang Chat"))
        assertFalse(androidBody.contains("请勿关闭 Gang Chat"))

        val windowsBody =
            updateDownloadConfirmationBody(
                AppUpdatePlatform.windows,
            )
        assertTrue(windowsBody.contains("退出当前程序并启动安装程序"))
        assertFalse(windowsBody.contains("在后台"))
    }

    @Test
    fun `parseReleaseAssetsFromS3List accepts GangChat release names only`() {
        val assets =
            parseReleaseAssetsFromS3List(
                """
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
    """,
            )

        assertEquals(3, assets.size)
        assertEquals("0.5.0", assets.first().version)
        assertEquals(AppUpdatePlatform.windows, assets.first().platform)
        assertEquals(DateTime.utc(2026, 7, 8, 1, 2, 3), assets.first().releasedAt)
        assertEquals("0.5.1", assets[1].version)
        assertEquals(AppUpdatePlatform.macos, assets[1].platform)
        assertEquals(DateTime.utc(2026, 7, 9, 4, 5, 6), assets[1].releasedAt)
        assertEquals("0.5.2", assets.last().version)
        assertEquals(AppUpdatePlatform.android, assets.last().platform)
        assertEquals(DateTime.utc(2026, 7, 10, 5, 6, 7), assets.last().releasedAt)
    }

    @Test
    fun `latestReleaseAssetForPlatform chooses highest semantic version`() {
        val assets =
            listOf(
                ReleaseAsset(
                    key = "releases/GangChat_v0.5.0.exe",
                    version = "0.5.0",
                    platform = AppUpdatePlatform.windows,
                ),
                ReleaseAsset(
                    key = "releases/GangChat_v0.10.0.exe",
                    version = "0.10.0",
                    platform = AppUpdatePlatform.windows,
                ),
                ReleaseAsset(
                    key = "releases/GangChat_v0.9.0.dmg",
                    version = "0.9.0",
                    platform = AppUpdatePlatform.macos,
                ),
                ReleaseAsset(
                    key = "releases/GangChat_v0.11.0.apk",
                    version = "0.11.0",
                    platform = AppUpdatePlatform.android,
                ),
            )

        val latest =
            latestReleaseAssetForPlatform(
                assets,
                AppUpdatePlatform.windows,
            )

        assertEquals("0.10.0", latest?.version)
        assertEquals(
            "0.11.0",
            latestReleaseAssetForPlatform(assets, AppUpdatePlatform.android)?.version,
        )
    }

    @Test
    fun `releaseAssetUrl encodes each key segment`() {
        assertEquals(
            "https://os.example.test/gang-chat/releases/GangChat_v1.2.3.exe",
            releaseAssetUrl(
                "https://os.example.test/gang-chat/",
                "releases/GangChat_v1.2.3.exe",
            ),
        )
    }

    @Test
    fun `releaseTimeLabel formats release time when available`() {
        assertEquals("暂无", releaseTimeLabel(null))
        assertEquals(
            "2026/07/08 09:05 UTC+08:00",
            releaseTimeLabel(DateTime.utc(2026, 7, 8, 1, 5)),
        )
    }

    @Test
    fun `releaseNotesLabel falls back to none`() {
        assertEquals("无", releaseNotesLabel(null))
        assertEquals("无", releaseNotesLabel("   "))
        assertEquals("修复更新安装流程", releaseNotesLabel("修复更新安装流程"))
    }
}
