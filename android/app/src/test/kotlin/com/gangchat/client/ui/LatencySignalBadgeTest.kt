package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
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
class LatencySignalBadgeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun pumpBadge() {
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize().testTag("page"), contentAlignment = Alignment.Center) {
                LatencySignalBadge(activeBars = 2, activeColor = UiColors.presenceReconnecting, tooltip = "228 ms")
            }
        }
    }

    private fun page() = compose.onNodeWithTag("page")

    /** Advances the clock by [millis] and lets the resulting frames run. */
    private fun pump(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun badge() = compose.onNodeWithTag(latencySignalBadgeTag, useUnmergedTree = true)

    /** The colour painted at [x] (a fraction of the width), mid-height, in bar [index]. */
    private fun barPixel(index: Int, x: Float): Color {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val bounds = compose.onNodeWithTag(latencySignalBarTag(index), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        return pixels[(bounds.left + bounds.width * x).toInt(), bounds.center.y.toInt()]
    }

    /** The fill painted at the centre of bar [index]. */
    private fun barColor(index: Int): Color = barPixel(index, 0.5f)

    /** The colour at the bar's left edge (a border would differ from the fill). */
    private fun barEdgeColor(index: Int): Color = barPixel(index, 0f)

    @Test
    fun `latency signal lights the requested bars`() {
        pumpBadge()

        assertEquals(UiColors.presenceReconnecting, barColor(1))
        assertEquals(UiColors.presenceReconnecting, barColor(2))
        assertEquals(Color(0xFF8A93A3), barColor(3))
        assertEquals(barColor(1), barEdgeColor(1))
    }

    @Test
    fun `latency tooltip uses Material Tooltip and pins on touch tap`() {
        pumpBadge()

        compose.onTooltip("228 ms").assertExists()
        badge().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("228 ms").assertExists()

        page().performTouchInput { click(Offset(5f, 5f)) }
        compose.waitForIdle()

        compose.onNodeWithText("228 ms").assertDoesNotExist()
    }

    @Test
    fun `mouse hover can be pinned after leaving the latency signal`() {
        pumpBadge()
        compose.mainClock.autoAdvance = false

        page().performMouseInput { moveTo(Offset.Zero) }
        badge().performMouseInput { moveTo(center) }
        pump(350)

        compose.onNodeWithText("228 ms").assertExists()

        badge().performMouseInput { click(center) }
        pump(500)
        page().performMouseInput { moveTo(Offset(5f, 5f)) }
        pump(150)

        compose.onNodeWithText("228 ms").assertExists()

        page().performMouseInput { click(Offset(5f, 5f)) }
        pump(500)

        compose.onNodeWithText("228 ms").assertDoesNotExist()
    }
}
