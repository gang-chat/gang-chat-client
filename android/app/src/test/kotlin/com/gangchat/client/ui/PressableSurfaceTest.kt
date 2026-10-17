package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.TapRegion
import com.gangchat.client.widgets.Text
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PressableSurfaceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `ignores a pointer release delivered after disposal`() {
        val groupId = Any()
        var showSurface by mutableStateOf(true)
        compose.setUiContent { _ ->
            Column {
                TapRegion(groupId = groupId, onTapOutside = { showSurface = false }) {
                    Box(Modifier.size(40.dp))
                }
                if (showSurface) {
                    PressableSurface(
                        height = 36.dp,
                        width = 120.dp,
                        onPressed = {},
                        modifier = Modifier.testTag("disposable-surface"),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("打开页面") }
                    }
                }
            }
        }

        // The pointer down outside the tap region removes the surface; the
        // release then arrives for a disposed surface and must be ignored.
        compose.onNodeWithTag("disposable-surface").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("disposable-surface").assertDoesNotExist()
    }
}
