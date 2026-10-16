package com.gangchat.client.platform

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.gangchat.client.testing.GangTestRule
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The installer's APK checks report the Flutter host's error codes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidUpdateInstallerTest {
    @get:Rule
    val gang = GangTestRule()

    private suspend fun installError(path: String?): PlatformException {
        try {
            AndroidUpdateInstaller.instance.installApk(path)
        } catch (error: PlatformException) {
            return error
        }
        fail("expected a PlatformException")
        throw AssertionError()
    }

    @Test
    fun `rejects missing, misplaced, misnamed and unparsable APKs`() =
        gang.runTest {
            val application = ApplicationProvider.getApplicationContext<Application>()
            val updates = File(application.cacheDir, "release-updates").apply { mkdirs() }

            assertEquals("The APK path is missing.", installError(" ").message)

            val outside = File(application.filesDir, "GangChat_v1.2.3.apk").apply { writeBytes(byteArrayOf(1)) }
            val misplaced = installError(outside.path)
            assertEquals("invalid_apk", misplaced.code)
            assertEquals("The APK is outside the private update directory or unreadable.", misplaced.message)

            val misnamed = File(updates, "other.apk").apply { writeBytes(byteArrayOf(1)) }
            assertEquals("invalid_apk", installError(misnamed.path).code)

            val escaping = File(updates, "../GangChat_v1.2.3.apk").path
            assertEquals("invalid_apk", installError(escaping).code)

            val junk = File(updates, "GangChat_v1.2.3.apk").apply { writeBytes(ByteArray(64) { 7 }) }
            val unparsable = installError(junk.path)
            assertEquals("invalid_apk", unparsable.code)
        }
}
