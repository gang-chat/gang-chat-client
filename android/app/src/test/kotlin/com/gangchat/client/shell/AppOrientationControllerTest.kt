package com.gangchat.client.shell

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.gangchat.client.platform.DeviceOrientation
import com.gangchat.client.platform.GangChatPlatform
import com.gangchat.client.platform.PlatformException
import com.gangchat.client.platform.SystemChrome
import com.gangchat.client.testing.GangTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Port of test/unit/shell/app_orientation_controller_test.dart. The Dart
 * case "orientation requests never reach non-Android platforms" has no
 * counterpart: the native client only runs on Android.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppOrientationControllerTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `Android defaults to portrait-only`() =
        gang.runTest {
            val requests = mutableListOf<List<DeviceOrientation>>()
            val controller =
                AppOrientationController(
                    logicalViewSize = { DpSize(400.dp, 800.dp) },
                    setPreferredOrientations = { orientations -> requests.add(orientations.toList()) },
                )

            controller.lockPortrait()

            assertEquals(listOf(AppOrientationController.portraitOnly), requests)
        }

    @Test
    fun `Android full-screen media allows both landscape directions`() =
        gang.runTest {
            val requests = mutableListOf<List<DeviceOrientation>>()
            val controller =
                AppOrientationController(
                    logicalViewSize = { DpSize(400.dp, 800.dp) },
                    setPreferredOrientations = { orientations -> requests.add(orientations.toList()) },
                )

            controller.allowFullScreenMediaLandscape()

            assertEquals(
                listOf(
                    listOf(
                        DeviceOrientation.portraitUp,
                        DeviceOrientation.landscapeLeft,
                        DeviceOrientation.landscapeRight,
                    ),
                ),
                requests,
            )
        }

    @Test
    fun `Android tablet keeps unrestricted default orientation`() =
        gang.runTest {
            val requests = mutableListOf<List<DeviceOrientation>>()
            val controller =
                AppOrientationController(
                    logicalViewSize = { DpSize(1280.dp, 800.dp) },
                    setPreferredOrientations = { orientations -> requests.add(orientations.toList()) },
                )

            controller.restoreDefaultOrientation()
            controller.allowFullScreenMediaLandscape()

            assertFalse(controller.restoresPortraitByDefault)
            assertEquals(listOf(AppOrientationController.unrestricted, AppOrientationController.unrestricted), requests)
        }

    @Test
    fun `Android tablet boundary uses the standard 600dp shortest side`() {
        assertFalse(isAndroidTabletLogicalSize(DpSize(599.dp, 1024.dp)))
        assertTrue(isAndroidTabletLogicalSize(DpSize(600.dp, 960.dp)))
        assertTrue(isAndroidTabletLogicalSize(DpSize(1280.dp, 800.dp)))
    }

    @Test
    fun `portrait restoration still runs after an earlier request fails`() =
        gang.runTest {
            val requests = mutableListOf<List<DeviceOrientation>>()
            var calls = 0
            val controller =
                AppOrientationController(
                    logicalViewSize = { DpSize(400.dp, 800.dp) },
                    setPreferredOrientations = { orientations ->
                        calls += 1
                        requests.add(orientations.toList())
                        if (calls == 1) throw PlatformException(code = "unsupported")
                    },
                )

            try {
                controller.allowFullScreenMediaLandscape()
                fail("expected PlatformException")
            } catch (_: PlatformException) {
            }
            controller.lockPortrait()

            assertEquals(AppOrientationController.portraitOnly, requests.last())
        }

    @Test
    fun `orientation sets map onto the activity like the Flutter engine`() =
        gang.runTest {
            val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
            GangChatPlatform.attachActivity(activity)
            try {
                val controller = AppOrientationController(logicalViewSize = { DpSize(400.dp, 800.dp) })

                controller.allowFullScreenMediaLandscape()
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, activity.requestedOrientation)
                controller.restoreDefaultOrientation()
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)

                val tablet = AppOrientationController(logicalViewSize = { DpSize(800.dp, 1280.dp) })
                tablet.restoreDefaultOrientation()
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
            } finally {
                GangChatPlatform.detachActivity(activity)
            }

            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
                SystemChrome.androidOrientationFor(DeviceOrientation.entries),
            )
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
                SystemChrome.androidOrientationFor(listOf(DeviceOrientation.landscapeLeft, DeviceOrientation.landscapeRight)),
            )
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                SystemChrome.androidOrientationFor(listOf(DeviceOrientation.landscapeLeft, DeviceOrientation.portraitUp)),
            )
        }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun `the default size reader sees a tablet window`() =
        gang.runTest {
            val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
            GangChatPlatform.attachActivity(activity)
            try {
                assertFalse(AppOrientationController().restoresPortraitByDefault)
            } finally {
                GangChatPlatform.detachActivity(activity)
            }
        }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the default size reader sees a phone window`() =
        gang.runTest {
            val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
            GangChatPlatform.attachActivity(activity)
            try {
                assertTrue(AppOrientationController().restoresPortraitByDefault)
            } finally {
                GangChatPlatform.detachActivity(activity)
            }
        }
}
