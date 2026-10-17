package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.kLongPressTimeout
import com.gangchat.client.widgets.kTouchSlop
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@OptIn(ExperimentalTestApi::class)
class PlatformGesturesTest {
    @get:Rule
    val compose = createComposeRule()

    private var taps = 0

    private fun pumpRegion() {
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                UiPointerTapRegion(onTap = { taps += 1 }, modifier = Modifier.testTag("region")) {
                    Box(Modifier.size(240.dp, 80.dp))
                }
            }
        }
    }

    @Test
    fun `UiPointerTapRegion fires once for a primary mouse click`() {
        pumpRegion()

        compose.onNodeWithTag("region").performMouseInput { click(center) }
        compose.waitForIdle()

        assertEquals(1, taps)
    }

    @Test
    fun `UiPointerTapRegion rejects a touch drag`() {
        pumpRegion()

        compose.onNodeWithTag("region").performTouchInput {
            down(center)
            moveBy(Offset((kTouchSlop + 4).dp.toPx(), 0f))
            up()
        }
        compose.waitForIdle()

        assertEquals(0, taps)
    }

    @Test
    fun `UiPointerTapRegion rejects a touch long press`() {
        pumpRegion()

        compose.onNodeWithTag("region").performTouchInput {
            down(center)
            advanceEventTime(kLongPressTimeout.inWholeMilliseconds)
            up()
        }
        compose.waitForIdle()

        assertEquals(0, taps)
    }
}
