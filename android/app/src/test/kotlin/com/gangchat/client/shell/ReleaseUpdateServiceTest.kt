package com.gangchat.client.shell

import com.gangchat.client.app.AppUpdatePlatform
import com.gangchat.client.app.AvailableAppUpdate
import com.gangchat.client.app.ReleaseAsset
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.FormatException
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.http.BaseRequest
import com.gangchat.client.foundation.http.ByteStream
import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.http.StreamedResponse
import com.gangchat.client.platform.PlatformException
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.MockClient
import java.io.File
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/**
 * Port of test/unit/shell/release_update_service_test.dart. The Windows
 * installer launcher cases (skipped off Windows in Dart too) and the desktop
 * temp-directory cleanup case are not ported: the Android service only
 * downloads Android APKs.
 */
class ReleaseUpdateServiceTest {
    @get:Rule
    val gang = GangTestRule()

    private fun androidUpdate(version: String = "0.5.1"): AvailableAppUpdate =
        AvailableAppUpdate(
            currentVersion = "0.5.0",
            latestVersion = version,
            asset =
                ReleaseAsset(
                    key = "releases/GangChat_v$version.apk",
                    version = version,
                    platform = AppUpdatePlatform.android,
                ),
            downloadUrl = Uri.parse("https://os.example.test/gang-chat/releases/GangChat_v$version.apk"),
        )

    private fun tempDir(name: String): File = File(gang.tempDir, name).apply { mkdirs() }

    @Test
    fun `operating system mapping includes Android without changing desktop`() {
        assertEquals(AppUpdatePlatform.windows, appUpdatePlatformForOperatingSystem("windows"))
        assertEquals(AppUpdatePlatform.macos, appUpdatePlatformForOperatingSystem("macos"))
        assertEquals(AppUpdatePlatform.android, appUpdatePlatformForOperatingSystem("android"))
        assertNull(appUpdatePlatformForOperatingSystem("linux"))
    }

    @Test
    fun `checkForUpdate returns platform-specific latest release`() =
        gang.runTest {
            val service =
                ReleaseUpdateService(
                    httpClient =
                        MockClient { request ->
                            assertEquals("GET", request.method)
                            assertEquals("2", request.url.queryParameters["list-type"])
                            assertEquals("releases/", request.url.queryParameters["prefix"])
                            HttpResponse(
                                """
                                <ListBucketResult>
                                  <Contents><Key>releases/GangChat_v0.5.0.exe</Key></Contents>
                                  <Contents>
                                    <Key>releases/GangChat_v0.5.1.exe</Key>
                                    <LastModified>2026-07-08T02:03:04.000Z</LastModified>
                                  </Contents>
                                  <Contents><Key>releases/GangChat_v0.5.1.dmg</Key></Contents>
                                </ListBucketResult>
                                """,
                                200,
                            )
                        },
                )

            val update =
                service.checkForUpdate(
                    bucketUrl = "https://os.example.test/gang-chat",
                    currentVersion = "0.5.0",
                    platform = AppUpdatePlatform.windows,
                )

            assertEquals("0.5.1", update?.latestVersion)
            assertEquals(DateTime.utc(2026, 7, 8, 2, 3, 4), update?.asset?.releasedAt)
            assertEquals(
                "https://os.example.test/gang-chat/releases/GangChat_v0.5.1.exe",
                update?.downloadUrl.toString(),
            )
        }

    @Test
    fun `checkForUpdate returns null when current version is latest`() =
        gang.runTest {
            val service =
                ReleaseUpdateService(
                    httpClient =
                        MockClient {
                            HttpResponse(
                                """
                                <ListBucketResult>
                                  <Contents><Key>releases/GangChat_v0.5.1.exe</Key></Contents>
                                </ListBucketResult>
                                """,
                                200,
                            )
                        },
                )

            val update =
                service.checkForUpdate(
                    bucketUrl = "https://os.example.test/gang-chat",
                    currentVersion = "0.5.1",
                    platform = AppUpdatePlatform.windows,
                )

            assertNull(update)
        }

    @Test
    fun `checkForUpdate finds the Android APK and reports list failures`() =
        gang.runTest {
            val service =
                ReleaseUpdateService(
                    httpClient =
                        MockClient {
                            HttpResponse(
                                "<ListBucketResult><Contents><Key>releases/GangChat_v0.6.0.apk</Key></Contents>" +
                                    "<Contents><Key>releases/GangChat_v0.7.0.exe</Key></Contents></ListBucketResult>",
                                200,
                            )
                        },
                )
            val update =
                service.checkForUpdate(
                    bucketUrl = "https://os.example.test/gang-chat/",
                    currentVersion = "0.5.9",
                    platform = AppUpdatePlatform.android,
                )
            assertEquals("0.6.0", update?.latestVersion)
            assertEquals(
                "https://os.example.test/gang-chat/releases/GangChat_v0.6.0.apk",
                update?.downloadUrl.toString(),
            )

            val failing = ReleaseUpdateService(httpClient = MockClient { HttpResponse("", 503) })
            try {
                failing.checkForUpdate(
                    bucketUrl = "https://os.example.test/gang-chat",
                    currentVersion = "0.5.0",
                    platform = AppUpdatePlatform.android,
                )
                fail("expected HttpException")
            } catch (error: HttpException) {
                assertEquals(
                    "HttpException: 读取版本列表失败（状态码 503）, " +
                        "uri = https://os.example.test/gang-chat?list-type=2&prefix=releases%2F",
                    error.toString(),
                )
            }
        }

    @Test
    fun `downloadUpdate keeps Android APKs in the private release directory`() =
        gang.runTest {
            val temp = tempDir("gang_chat_android_release_update_test")
            val updateDirectory = File(temp, "release-updates")
            updateDirectory.mkdirs()
            val oldApk = File(updateDirectory, "GangChat_v0.5.0.apk")
            val unrelated = File(updateDirectory, "keep.txt")
            oldApk.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
            unrelated.writeText("keep me")

            val progress = mutableListOf<Pair<Long, Long?>>()
            val service =
                ReleaseUpdateService(
                    httpClient =
                        MockClient {
                            HttpResponse.bytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00), 200)
                        },
                    temporaryDirectoryProvider = { temp },
                )
            val file =
                service.downloadUpdate(
                    androidUpdate(),
                    onProgress = { receivedBytes, totalBytes -> progress.add(receivedBytes to totalBytes) },
                )

            assertFalse(oldApk.exists())
            assertTrue(unrelated.exists())
            assertEquals(updateDirectory.path, file.parentFile!!.path)
            assertTrue(file.path.endsWith("GangChat_v0.5.1.apk"))
            assertArrayEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00), file.readBytes())
            assertEquals(listOf<Pair<Long, Long?>>(0L to 6L, 6L to 6L), progress)
        }

    @Test
    fun `downloadUpdate deletes an invalid Android APK response`() =
        gang.runTest {
            val temp = tempDir("gang_chat_invalid_android_release_update_test")
            val service =
                ReleaseUpdateService(
                    httpClient = MockClient { HttpResponse("not an apk", 200) },
                    temporaryDirectoryProvider = { temp },
                )

            try {
                service.downloadUpdate(androidUpdate())
                fail("expected FormatException")
            } catch (error: FormatException) {
                assertEquals("FormatException: 下载的文件不是有效的 Android 安装包", error.toString())
            }
            assertFalse(File(temp, "release-updates/GangChat_v0.5.1.apk").exists())
        }

    @Test
    fun `downloadUpdate rejects an incomplete Android APK and a failed status`() =
        gang.runTest {
            val temp = tempDir("gang_chat_incomplete_android_release_update_test")
            val truncated =
                ReleaseUpdateService(
                    httpClient =
                        MockClient.streaming {
                            StreamedResponse(
                                ByteStream(flowOf(byteArrayOf(0x50, 0x4B, 0x03, 0x04))),
                                200,
                                contentLength = 10,
                            )
                        },
                    temporaryDirectoryProvider = { temp },
                )
            try {
                truncated.downloadUpdate(androidUpdate())
                fail("expected FormatException")
            } catch (error: FormatException) {
                assertEquals("Android 安装包下载不完整", error.message)
            }
            assertFalse(File(temp, "release-updates/GangChat_v0.5.1.apk").exists())

            val missing =
                ReleaseUpdateService(
                    httpClient = MockClient { HttpResponse("", 404) },
                    temporaryDirectoryProvider = { temp },
                )
            try {
                missing.downloadUpdate(androidUpdate())
                fail("expected HttpException")
            } catch (error: HttpException) {
                assertEquals("版本下载失败（状态码 404）", error.message)
            }
        }

    @Test
    fun `downloadUpdate cancellation deletes partial installer`() =
        gang.runTest {
            val temp = tempDir("gang_chat_release_update_cancel_test")
            val token = ReleaseDownloadCancellationToken()
            val service =
                ReleaseUpdateService(
                    httpClient =
                        _ChunkedDownloadClient(
                            listOf("partial ".toByteArray(), "installer".toByteArray()),
                        ),
                    temporaryDirectoryProvider = { temp },
                )

            try {
                service.downloadUpdate(
                    androidUpdate(),
                    cancellationToken = token,
                    onProgress = { receivedBytes, _ ->
                        if (receivedBytes > 0) token.cancel()
                    },
                )
                fail("expected ReleaseDownloadCancelledException")
            } catch (error: ReleaseDownloadCancelledException) {
                assertEquals("版本下载已取消", error.toString())
            }

            assertFalse(File(temp, "release-updates/GangChat_v0.5.1.apk").exists())
            assertTrue(token.isCancelled)
        }

    @Test
    fun `a token cancelled before the download starts closes the client`() =
        gang.runTest {
            val temp = tempDir("gang_chat_release_update_precancel_test")
            val token = ReleaseDownloadCancellationToken()
            token.cancel()
            val client = _ChunkedDownloadClient(listOf("data".toByteArray()))
            val service = ReleaseUpdateService(httpClient = client, temporaryDirectoryProvider = { temp })

            try {
                service.downloadUpdate(androidUpdate(), cancellationToken = token)
                fail("expected ReleaseDownloadCancelledException")
            } catch (_: ReleaseDownloadCancelledException) {
            }
            assertTrue(client.closed)
            assertEquals(0, client.sends)

            token.cancelAndDeletePartialFile()
            assertFalse(File(temp, "release-updates/GangChat_v0.5.1.apk").exists())
        }

    @Test
    fun `startInstaller delegates Android APKs to the native installer`() =
        gang.runTest {
            var launchedPath: String? = null
            val service =
                ReleaseUpdateService(
                    androidInstallerLauncher = { path -> launchedPath = path },
                )
            val file = File(gang.tempDir, "GangChat_v0.5.1.apk")

            service.startInstaller(file, platform = AppUpdatePlatform.android)

            assertEquals(file.path, launchedPath)

            // Without an explicit platform the host (Android) decides.
            launchedPath = null
            service.startInstaller(file)
            assertEquals(file.path, launchedPath)
        }

    @Test
    fun `startInstaller localizes Android signature mismatch`() =
        gang.runTest {
            val service =
                ReleaseUpdateService(
                    androidInstallerLauncher = { _ -> throw PlatformException(code = "signature_mismatch") },
                )

            try {
                service.startInstaller(
                    File("GangChat_v0.5.1.apk"),
                    platform = AppUpdatePlatform.android,
                )
                fail("expected ReleaseInstallerException")
            } catch (error: ReleaseInstallerException) {
                assertTrue(error.message.contains("签名"))
            }
        }

    @Test
    fun `startInstaller rejects files that are not APKs and desktop platforms`() =
        gang.runTest {
            var launched = false
            val service = ReleaseUpdateService(androidInstallerLauncher = { _ -> launched = true })

            try {
                service.startInstaller(File("GangChat_v0.5.1.exe"), platform = AppUpdatePlatform.android)
                fail("expected ReleaseInstallerException")
            } catch (error: ReleaseInstallerException) {
                assertEquals("下载的文件不是 Android 安装包", error.toString())
            }
            try {
                service.startInstaller(File("GangChat_v0.5.1.exe"), platform = AppUpdatePlatform.windows)
                fail("expected UnsupportedOperationException")
            } catch (_: UnsupportedOperationException) {
            }
            assertFalse(launched)
        }

    private class _ChunkedDownloadClient(private val chunks: List<ByteArray>) : HttpClient {
        var closed = false
        var sends = 0

        override suspend fun send(request: BaseRequest): StreamedResponse {
            sends += 1
            return StreamedResponse(ByteStream(flowOf(*chunks.toTypedArray())), 200)
        }

        override fun close() {
            closed = true
        }
    }
}
