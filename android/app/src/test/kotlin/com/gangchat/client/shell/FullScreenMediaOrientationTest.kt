package com.gangchat.client.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.gangchat.client.platform.DeviceOrientation
import com.gangchat.client.widgets.Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The full-screen media scope opens landscape on entry and restores the default on exit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FullScreenMediaOrientationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun allowsLandscapeWhileShownAndRestoresPortraitOnDispose() {
        val requests = mutableListOf<List<DeviceOrientation>>()
        val controller =
            AppOrientationController(
                logicalViewSize = { DpSize(400.dp, 800.dp) },
                setPreferredOrientations = { requests.add(it) },
            )
        var shown by mutableStateOf(true)
        compose.setContent {
            if (shown) FullScreenMediaOrientation(controller = controller) { Text("media") }
        }
        compose.waitForIdle()
        assertEquals(listOf(AppOrientationController.fullScreenMedia), requests)

        shown = false
        compose.waitForIdle()

        assertEquals(listOf(AppOrientationController.fullScreenMedia, AppOrientationController.portraitOnly), requests)
    }
}
