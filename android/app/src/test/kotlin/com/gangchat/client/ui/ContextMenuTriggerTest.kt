package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class ContextMenuTriggerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Android touch hold triggers the application context action`() {
        val positions = mutableListOf<DpOffset>()
        var tapCount = 0
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                UiContextMenuTriggerRegion(onTriggered = { positions.add(it) }, onTap = { tapCount++ }) {
                    Box(Modifier.testTag("target").size(120.dp, 56.dp))
                }
            }
        }

        val bounds = compose.onNodeWithTag("target").getBoundsInRoot()
        compose.onNodeWithTag("target").performTouchInput { longClick(center) }
        compose.waitForIdle()

        assertEquals(1, positions.size)
        assertEquals(((bounds.left + bounds.right) / 2).value, positions.single().x.value, 0.01f)
        assertEquals(((bounds.top + bounds.bottom) / 2).value, positions.single().y.value, 0.01f)
        assertEquals(0, tapCount)
    }

    @Test
    fun `Android context target keeps nested text double-tap selection`() {
        val positions = mutableListOf<DpOffset>()
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(240.dp)) {
                    UiContextMenuTriggerRegion(onTriggered = { positions.add(it) }) {
                        ReadOnlySelectableText(value = "hello world", style = uiTheme().textTheme.bodyMedium)
                    }
                }
            }
        }

        val field = compose.onNodeWithText("hello world")
        field.performTouchInput { doubleClick(Offset(left + 28.dp.toPx(), centerY)) }
        compose.waitForIdle()

        val selection = field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertFalse(selection.collapsed)
        assertTrue(positions.isEmpty())
    }
}
