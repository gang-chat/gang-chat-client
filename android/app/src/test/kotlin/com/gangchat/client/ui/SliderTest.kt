package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class SliderTest {
    @get:Rule
    val compose = createComposeRule()

    // Size of the round knob's slot in [UiSlider].
    private val knobSize = 14f

    @Test
    fun `horizontal slider reaches both visual endpoints`() {
        var value by mutableDoubleStateOf(0.0)
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(100.dp)) { UiSlider(value = value, onChanged = {}, modifier = Modifier.testTag("slider")) }
            }
        }

        fun handleLeft(): Float =
            compose.onNodeWithTag(uiSliderHandleTag).getBoundsInRoot().left.value -
                compose.onNodeWithTag("slider").getBoundsInRoot().left.value

        assertEquals(0f, handleLeft(), 0.01f)

        value = 1.0
        compose.waitForIdle()
        // The round knob's right edge lands on the slider's right edge.
        assertEquals(100f, handleLeft() + knobSize, 0.01f)
    }

    @Test
    fun `slider shows hover percentage above its thumb`() {
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(100.dp)) {
                    UiSlider(value = 0.46, hoverLabel = "46%", onChanged = {}, modifier = Modifier.testTag("slider"))
                }
            }
        }

        compose.onNodeWithTag(uiSliderHoverLabelTag).assertDoesNotExist()

        compose.onRoot().performMouseInput { moveTo(Offset.Zero) }
        compose.onNodeWithTag("slider").performMouseInput { moveTo(center) }
        compose.waitForIdle()

        compose.onNodeWithText("46%").assertExists()
        val labelRect = compose.onNodeWithTag(uiSliderHoverLabelTag).getUnclippedBoundsInRoot()
        val handleRect = compose.onNodeWithTag(uiSliderHandleTag).getBoundsInRoot()
        val sliderRect = compose.onNodeWithTag("slider").getBoundsInRoot()
        assertTrue(labelRect.bottom < sliderRect.top)
        assertEquals(
            ((handleRect.left + handleRect.right) / 2).value,
            ((labelRect.left + labelRect.right) / 2).value,
            0.01f,
        )
    }
}
