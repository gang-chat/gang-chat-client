package com.gangchat.client.shell

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.gangchat.client.app.gangChatClientInstallInfoFileName
import com.gangchat.client.testing.GangTestRule
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The "Android install time comes from PackageManager bridge" case of
 * test/unit/shell/android_system_service_test.dart, through the real bridge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InstallInfoServiceTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `Android install time comes from PackageManager bridge`() =
        gang.runTest {
            val application = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(application.packageManager)
                .getInternalMutablePackageInfo(application.packageName)
                .lastUpdateTime = Instant.parse("2026-07-25T04:05:06Z").toEpochMilli()

            val service = InstallInfoService()

            assertEquals(gangChatClientInstallInfoFileName, service.fileName)
            assertEquals("2026-07-25T04:05:06.000Z", service.readInstalledAt())
        }

    @Test
    fun `bridge failures and a missing platform read as no install time`() =
        gang.runTest {
            val failing =
                object : AndroidSystemService() {
                    override suspend fun installedAt(): String? = throw IllegalStateException("bridge failed")
                }
            val unsupported =
                object : AndroidSystemService() {
                    override val isSupported: Boolean get() = false
                }

            assertNull(InstallInfoService(androidSystemService = failing).readInstalledAt())
            assertNull(InstallInfoService(androidSystemService = unsupported).readInstalledAt())
        }
}
