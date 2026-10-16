package com.gangchat.client.shell

import com.gangchat.client.platform.PlatformException
import com.gangchat.client.platform.SystemUiMode
import com.gangchat.client.platform.SystemUiOverlay
import com.gangchat.client.testing.GangTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/**
 * Port of test/unit/shell/full_screen_system_ui_controller_test.dart. The
 * Dart case "system UI requests never reach non-Android platforms" has no
 * counterpart: the native client only runs on Android.
 */
class FullScreenSystemUiControllerTest {
    @get:Rule
    val gang = GangTestRule()

    private class Request(val mode: SystemUiMode, val overlays: List<SystemUiOverlay>?)

    @Test
    fun `Android system bars follow full-screen media controls`() =
        gang.runTest {
            val requests = mutableListOf<Request>()
            val controller =
                FullScreenSystemUiController(
                    setEnabledSystemUIMode = { mode, overlays -> requests.add(Request(mode, overlays)) },
                )

            controller.setControlsVisible(true)
            controller.setControlsVisible(false)
            controller.restore()

            assertEquals(3, requests.size)
            assertEquals(SystemUiMode.manual, requests[0].mode)
            assertEquals(SystemUiOverlay.entries, requests[0].overlays)
            assertEquals(SystemUiMode.immersiveSticky, requests[1].mode)
            assertNull(requests[1].overlays)
            assertEquals(SystemUiMode.manual, requests[2].mode)
            assertEquals(SystemUiOverlay.entries, requests[2].overlays)
        }

    @Test
    fun `restore still runs after an earlier platform failure`() =
        gang.runTest {
            val modes = mutableListOf<SystemUiMode>()
            var calls = 0
            val controller =
                FullScreenSystemUiController(
                    setEnabledSystemUIMode = { mode, _ ->
                        calls += 1
                        modes.add(mode)
                        if (calls == 1) throw PlatformException(code = "unsupported")
                    },
                )

            try {
                controller.setControlsVisible(false)
                fail("expected PlatformException")
            } catch (_: PlatformException) {
            }
            controller.restore()

            assertEquals(listOf(SystemUiMode.immersiveSticky, SystemUiMode.manual), modes)
        }
}
