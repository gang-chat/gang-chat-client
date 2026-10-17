package com.gangchat.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.gangchat.client.ui.Input
import com.gangchat.client.ui.UiTheme
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.FocusNode
import com.gangchat.client.widgets.LocalMediaQuery
import com.gangchat.client.widgets.MediaQueryData
import com.gangchat.client.widgets.TextEditingController
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
@Config(sdk = [35], qualifiers = "w360dp-h740dp-mdpi")
class HomeKeyboardLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private var height by mutableStateOf(740.dp)
    private var keyboardInset by mutableStateOf(0.dp)

    /** Dart's host: a MediaQuery with the keyboard inset and a 360 x [height] box at the top left. */
    private fun host(enabled: Boolean = true, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            UiTheme {
                CompositionLocalProvider(
                    LocalMediaQuery provides MediaQueryData(size = DpSize(360.dp, height), viewInsets = EdgeInsets.only(bottom = keyboardInset)),
                ) {
                    Box(Modifier.fillMaxSize().wrapContentSize(Alignment.TopStart, unbounded = true)) {
                        Box(Modifier.requiredSize(360.dp, height)) {
                            HomeKeyboardOverlayViewport(enabled = enabled) { content() }
                        }
                    }
                }
            }
        }
    }

    private fun contentSize(): DpSize {
        val node = compose.onNodeWithTag("home-keyboard-content").fetchSemanticsNode()
        val density = compose.density.density
        return DpSize((node.size.width / density).dp, (node.size.height / density).dp)
    }

    @Test
    fun `Android music box overlays the keyboard without resizing home`() {
        assertFalse(shouldResizeHomeForKeyboard(liveMusicBoxVisible = true))
    }

    @Test
    fun `other Android inputs keep normal keyboard resizing`() {
        assertTrue(shouldResizeHomeForKeyboard(liveMusicBoxVisible = false))
    }

    @Test
    fun `keyboard animation keeps the enabled home viewport stable`() {
        host { Box(Modifier.fillMaxSize().testTag("home-keyboard-content")) }
        compose.waitForIdle()
        assertEquals(DpSize(360.dp, 740.dp), contentSize())

        for ((frameHeight, inset) in listOf(660 to 80, 560 to 180, 440 to 300, 560 to 180, 660 to 80)) {
            height = frameHeight.dp
            keyboardInset = inset.dp
            compose.waitForIdle()
            assertEquals(DpSize(360.dp, 740.dp), contentSize())
        }

        height = 740.dp
        keyboardInset = 0.dp
        compose.waitForIdle()
        assertEquals(DpSize(360.dp, 740.dp), contentSize())
    }

    @Test
    fun `keyboard transition keeps an internally focused input mounted`() {
        val controller = TextEditingController()
        val focusNode = FocusNode()
        host {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Input(
                    modifier = Modifier.width(300.dp),
                    controller = controller,
                    focusNode = focusNode,
                    hintText = "Search",
                    prefixIcon = Icons.Filled.Search,
                )
            }
        }
        compose.onNode(hasSetTextAction()).performClick()
        compose.waitForIdle()
        assertTrue(focusNode.hasFocus)

        height = 440.dp
        keyboardInset = 300.dp
        compose.waitForIdle()

        assertTrue(focusNode.hasFocus)
        compose.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun `disabled viewport follows ordinary keyboard constraints`() {
        height = 440.dp
        keyboardInset = 300.dp
        host(enabled = false) { Box(Modifier.fillMaxSize().testTag("home-keyboard-content")) }
        compose.waitForIdle()

        assertEquals(DpSize(360.dp, 440.dp), contentSize())
    }
}
