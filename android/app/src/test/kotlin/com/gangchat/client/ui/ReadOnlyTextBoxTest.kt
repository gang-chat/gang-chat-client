package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class ReadOnlyTextBoxTest {
    @get:Rule
    val compose = createComposeRule()

    private fun pumpReadOnlyTextBox(value: String) {
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(320.dp)) { ReadOnlyTextBox(value = value) }
            }
        }
    }

    private fun field() = compose.onNode(isTextField)

    private fun selection(): TextRange = field().fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]

    @Test
    fun `read-only text box shows select all when text is unselected`() {
        mockClipboardText(null)
        pumpReadOnlyTextBox("hello world")
        compose.showTextContextMenu(TextRange(5), field = isTextField)

        compose.onNodeWithText("全选").assertExists()
        compose.onNodeWithText("Ctrl+A").assertExists()
        compose.onNodeWithText("复制").assertDoesNotExist()
        compose.onNodeWithText("剪切").assertDoesNotExist()
        compose.onNodeWithText("粘贴").assertDoesNotExist()

        compose.onNodeWithText("全选").performClick()
        compose.waitForIdle()

        assertEquals(TextRange(0, 11), selection())
    }

    @Test
    fun `read-only text box shows copy when text is selected`() {
        mockClipboardText(null)
        pumpReadOnlyTextBox("hello world")
        compose.showTextContextMenu(TextRange(0, 5), field = isTextField)

        compose.onNodeWithText("复制").assertExists()
        compose.onNodeWithText("Ctrl+C").assertExists()
        compose.onNodeWithText("全选").assertExists()
        compose.onNodeWithText("剪切").assertDoesNotExist()
        compose.onNodeWithText("粘贴").assertDoesNotExist()

        compose.onNodeWithText("复制").performClick()
        compose.waitForIdle()

        assertEquals("hello", clipboardText())
    }

    @Test
    fun `read-only text box hides menu when text is empty`() {
        mockClipboardText(null)
        pumpReadOnlyTextBox("")
        compose.showTextContextMenu(TextRange(0), field = isTextField)

        compose.onNodeWithText("全选").assertDoesNotExist()
        compose.onNodeWithText("复制").assertDoesNotExist()
    }

    @Test
    fun `Android double tap selects text`() {
        pumpReadOnlyTextBox("hello world")

        field().performTouchInput { doubleClick(Offset(left + 34.dp.toPx(), centerY)) }
        compose.waitForIdle()

        assertFalse(selection().collapsed)
    }

    @Test
    fun `Android hold uses native text selection and context menu`() {
        pumpReadOnlyTextBox("hello world")
        field().performClick()
        compose.waitForIdle()
        field().performTextInputSelection(TextRange(0, 5))
        compose.waitForIdle()

        field().performTouchInput { longClick(Offset(right - 8.dp.toPx(), centerY)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)

        val selection = selection()
        assertFalse(selection.start < 0 || selection.end < 0)
        compose.onNodeWithTag(textContextMenuPanelTag).assertExists()
    }
}
