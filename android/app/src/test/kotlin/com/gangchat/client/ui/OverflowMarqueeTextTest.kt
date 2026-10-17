package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangchat.client.widgets.DefaultTextStyle
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
class OverflowMarqueeTextTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `marquee reaches both ends with inherited typography and text scaling`() {
        val text = "张顺飞 - 绝不认输 feat.侯顺玉 MV正式发布"
        compose.mainClock.autoAdvance = false
        compose.setUiContent { _ ->
            val density = LocalDensity.current
            // Flutter's MediaQuery(textScaler: TextScaler.linear(1.2)).
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.2f)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    DefaultTextStyle(style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W700, letterSpacing = 1.5.sp)) {
                        Box(Modifier.testTag("marquee-viewport").width(150.dp)) {
                            OverflowMarqueeText(text = text, style = TextStyle(color = Color.White), trackKey = "marquee-track")
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()

        val viewport = compose.onNodeWithTag("marquee-viewport").getBoundsInRoot()
        val start = compose.onNodeWithText(text).getUnclippedBoundsInRoot()
        assertEquals(viewport.left.value, start.left.value, 0.01f)

        // Wait through the initial pause and the maximum configured travel time.
        compose.mainClock.advanceTimeBy(851)
        compose.mainClock.advanceTimeBy(10001)

        val atTrailingBoundary = compose.onNodeWithText(text).getUnclippedBoundsInRoot()
        assertTrue(atTrailingBoundary.right <= viewport.right + 0.01.dp)
        assertEquals(viewport.right.value, atTrailingBoundary.right.value, 1.01f)
    }
}
