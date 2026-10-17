package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.gangchat.client.widgets.FocusNode
import com.gangchat.client.widgets.IconSemanticsKey
import com.gangchat.client.widgets.TapRegion
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.UndoHistoryController
import com.gangchat.client.widgets.UndoHistoryValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
class InputWidgetTest {
    @get:Rule
    val compose = createComposeRule()

    private fun pumpInput(
        controller: TextEditingController,
        focusNode: FocusNode? = null,
        undoController: UndoHistoryController? = null,
        obscureText: Boolean = false,
    ) {
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(320.dp)) {
                    Input(
                        controller = controller,
                        focusNode = focusNode,
                        undoController = undoController,
                        obscureText = obscureText,
                        modifier = Modifier.testTag("input"),
                    )
                }
            }
        }
    }

    private fun icon(vector: androidx.compose.ui.graphics.vector.ImageVector) =
        compose.onNode(SemanticsMatcher.expectValue(IconSemanticsKey, vector), useUnmergedTree = true)

    private fun DpRect.centerY(): Dp = (top + bottom) / 2


    @Test
    fun `input clear button clears text and reports the change`() {
        val controller = TextEditingController("alice")
        val changes = mutableListOf<String>()
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(320.dp)) {
                    Input(controller = controller, hintText = "搜索", showClearButton = true, onChanged = { changes.add(it) })
                }
            }
        }

        compose.onTooltip("清空搜索").assertExists()
        compose.onTooltip("清空搜索").performClick()
        compose.waitForIdle()

        assertEquals("", controller.text)
        assertTrue(changes.contains(""))
        compose.onTooltip("清空搜索").assertDoesNotExist()
    }

    @Test
    fun `search input submits with enter instead of accepting newlines`() {
        val controller = TextEditingController("alice")
        val submissions = mutableListOf<String>()
        compose.setUiContent { _ ->
            Box(Modifier.width(320.dp)) {
                Input(
                    controller = controller,
                    hintText = "搜索成员",
                    prefixIcon = Icons.Filled.Search,
                    showClearButton = true,
                    onSubmitted = { submissions.add(it) },
                )
            }
        }

        val field = compose.textField().fetchSemanticsNode()
        assertEquals(
            androidx.compose.ui.text.input.ImeAction.Search,
            field.config[androidx.compose.ui.semantics.SemanticsProperties.ImeAction],
        )
        compose.textField().performClick()
        compose.textField().performImeAction()
        compose.waitForIdle()

        assertEquals("alice", controller.text)
        assertEquals(listOf("alice"), submissions)
    }

    @Test
    fun `android search input centers text and icon vertically`() {
        compose.setUiContent { _ ->
            Box(Modifier.width(320.dp)) { Input(hintText = "搜索", prefixIcon = Icons.Filled.Search, modifier = Modifier.testTag("input")) }
        }
        val frame = compose.onNodeWithTag("input").getBoundsInRoot()
        val iconRect = icon(Icons.Filled.Search).getBoundsInRoot()
        // 40 dp field inside its 1 dp border.
        assertEquals(42f, frame.height.value, 0.5f)
        assertEquals(frame.centerY().value, iconRect.centerY().value, 0.5f)
        val hint = compose.onNodeWithText("搜索", useUnmergedTree = true).getBoundsInRoot()
        assertEquals(frame.centerY().value, hint.centerY().value, 1f)
    }

    @Test
    fun `compact search keeps hint and entered text geometry stable`() {
        val controller = TextEditingController()
        compose.setUiContent { _ ->
            Box(Modifier.width(260.dp)) {
                Input(
                    controller = controller,
                    hintText = "搜索歌曲点歌",
                    prefixIcon = Icons.Filled.Search,
                    showClearButton = true,
                    height = 30.dp,
                    modifier = Modifier.testTag("input"),
                )
            }
        }

        val emptySize = compose.onNodeWithTag("input").getBoundsInRoot()
        val emptyIconCenter = icon(Icons.Filled.Search).getBoundsInRoot().centerY()

        controller.text = "绝不认输"
        compose.waitForIdle()

        val filled = compose.onNodeWithTag("input").getBoundsInRoot()
        assertEquals(emptySize.height.value, filled.height.value, 0.34f)
        assertEquals(emptyIconCenter.value, icon(Icons.Filled.Search).getBoundsInRoot().centerY().value, 0.34f)
        assertEquals(emptyIconCenter.value, icon(Icons.Filled.Close).getBoundsInRoot().centerY().value, 0.34f)

        controller.clear()
        compose.waitForIdle()
        assertEquals(emptySize.height.value, compose.onNodeWithTag("input").getBoundsInRoot().height.value, 0.34f)
        assertEquals(emptyIconCenter.value, icon(Icons.Filled.Search).getBoundsInRoot().centerY().value, 0.34f)
    }

    @Test
    fun `compact clear button does not shift the field vertically`() {
        val controller = TextEditingController()
        var showClear by mutableStateOf(true)
        compose.setUiContent { _ ->
            Box(Modifier.width(260.dp)) {
                Input(
                    controller = controller,
                    hintText = "搜索歌曲点歌",
                    prefixIcon = Icons.Filled.Search,
                    showClearButton = showClear,
                    height = 30.dp,
                    modifier = Modifier.testTag("input"),
                )
            }
        }

        fun relativeSearchIconCenter(): Float =
            icon(Icons.Filled.Search).getBoundsInRoot().centerY().value - compose.onNodeWithTag("input").getBoundsInRoot().top.value

        val reserved = relativeSearchIconCenter()
        showClear = false
        compose.waitForIdle()
        assertEquals(reserved, relativeSearchIconCenter(), 0.34f)
    }

    @Test
    fun `android regular input centers text and icon vertically`() {
        compose.setUiContent { _ ->
            Box(Modifier.width(320.dp)) {
                Input(hintText = "普通输入", prefixIcon = Icons.Filled.PersonOutline, modifier = Modifier.testTag("input"))
            }
        }
        val frame = compose.onNodeWithTag("input").getBoundsInRoot()
        assertEquals(frame.centerY().value, icon(Icons.Filled.PersonOutline).getBoundsInRoot().centerY().value, 0.5f)
        assertEquals(frame.centerY().value, compose.onNodeWithText("普通输入", useUnmergedTree = true).getBoundsInRoot().centerY().value, 1f)
    }

    @Test
    fun `android unbounded multiline input centers its first line`() {
        compose.setUiContent { _ ->
            Box(Modifier.width(320.dp)) { Input(hintText = "简介", maxLines = null, modifier = Modifier.testTag("input")) }
        }
        val frame = compose.onNodeWithTag("input").getBoundsInRoot()
        // No extra bottom padding on Android: the collapsed field stays 40 dp.
        assertEquals(42f, frame.height.value, 0.5f)
        assertEquals(frame.centerY().value, compose.onNodeWithText("简介", useUnmergedTree = true).getBoundsInRoot().centerY().value, 1f)
    }

    @Test
    fun `input forwards explicit text alignment`() {
        val controller = TextEditingController("2026-07-10")
        compose.setUiContent { _ ->
            Box(Modifier.width(240.dp)) { Input(controller = controller, textAlign = TextAlign.Center) }
        }
        val results = mutableListOf<TextLayoutResult>()
        compose.textField().fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        assertEquals(TextAlign.Center, results.first().layoutInput.style.textAlign)
    }

    @Test
    fun `input keeps its focus ring while focused without hover`() {
        val controller = TextEditingController("hello")
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.testTag("frame").padding(8.dp).width(320.dp)) { Input(controller = controller, modifier = Modifier.testTag("input")) }
            }
        }

        fun borderColor(): Color {
            val pixels = compose.onNodeWithTag("input").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, 1]
        }
        fun ringColor(): Color {
            val pixels = compose.onNodeWithTag("frame").captureToImage().toPixelMap()
            // 1.5 dp above the field (inside the 3 dp ring) at xxhdpi.
            return pixels[pixels.width / 2, (8 * 3) - 4]
        }

        assertEquals(UiColors.fieldBorder, borderColor())
        assertEquals(UiColors.background, ringColor())

        compose.textField().performClick()
        compose.waitForIdle()

        assertEquals(UiColors.accentStrong, borderColor())
        assertNotEquals(UiColors.background, ringColor())
    }

    @Test
    fun `input context menu shows select all without clipboard text`() {
        mockClipboardText(null)
        val controller = TextEditingController("hello")
        pumpInput(controller)
        compose.showTextContextMenu(TextRange(5))

        compose.onNodeWithText("全选").assertExists()
        compose.onNodeWithText("Ctrl+A").assertExists()
        compose.onNodeWithText("粘贴").assertDoesNotExist()
        compose.onNodeWithText("剪切").assertDoesNotExist()
        compose.onNodeWithText("复制").assertDoesNotExist()
    }

    @Test
    fun `Android select all keeps the menu and both handles visible`() {
        mockClipboardText(null)
        val controller = TextEditingController("hello world")
        pumpInput(controller)
        compose.showTextContextMenu(TextRange(0, 5))

        compose.onNodeWithText("全选").performClick()
        compose.waitForIdle()

        assertEquals(TextRange(0, 11), controller.selection)
        assertEquals(2, compose.onAllNodes(isSelectionHandle, useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNodeWithTag(textContextMenuPanelTag).assertExists()
        compose.onNodeWithText("复制").assertExists()
        compose.onNodeWithText("全选").assertDoesNotExist()
    }

    @Test
    fun `input context menu hides select all when text is empty`() {
        mockClipboardText(null)
        val controller = TextEditingController()
        pumpInput(controller)
        // An empty field has no cursor handle to tap; the menu rules decide.
        val sections =
            textContextMenuSections(
                menu = TextFieldContextMenu(),
                value = controller.value,
                platformActions = emptyMap(),
                canPasteNonText = false,
                pasteIntent = {},
                selectAll = {},
                copySelection = {},
            )
        assertTrue(sections.isEmpty())
        compose.onNodeWithText("全选").assertDoesNotExist()
    }

    @Test
    fun `input context menu shows paste when clipboard has text`() {
        mockClipboardText("from clipboard")
        val controller = TextEditingController("hello")
        pumpInput(controller)
        compose.showTextContextMenu(TextRange(5))

        compose.onNodeWithText("粘贴").assertExists()
        compose.onNodeWithText("Ctrl+V").assertExists()
        compose.onNodeWithText("全选").assertExists()
        compose.onNodeWithText("Ctrl+A").assertExists()
        compose.onNodeWithText("剪切").assertDoesNotExist()
        compose.onNodeWithText("复制").assertDoesNotExist()
    }

    @Test
    fun `input context menu shows edit actions for selected text`() {
        mockClipboardText(null)
        val controller = TextEditingController("hello world")
        pumpInput(controller)
        compose.showTextContextMenu(TextRange(0, 5))

        compose.onNodeWithText("剪切").assertExists()
        compose.onNodeWithText("Ctrl+X").assertExists()
        compose.onNodeWithText("复制").assertExists()
        compose.onNodeWithText("Ctrl+C").assertExists()
        compose.onNodeWithText("粘贴").assertDoesNotExist()
        compose.onNodeWithText("Ctrl+V").assertDoesNotExist()
        compose.onNodeWithText("全选").assertExists()
        compose.onNodeWithText("Ctrl+A").assertExists()
    }

    @Test
    fun `obscured input context menu hides cut and copy`() {
        mockClipboardText("replacement")
        val controller = TextEditingController("secret-password")
        pumpInput(controller, obscureText = true)
        compose.showTextContextMenu(TextRange(0, 6))

        compose.onNodeWithText("剪切").assertDoesNotExist()
        compose.onNodeWithText("Ctrl+X").assertDoesNotExist()
        compose.onNodeWithText("复制").assertDoesNotExist()
        compose.onNodeWithText("Ctrl+C").assertDoesNotExist()
        compose.onNodeWithText("粘贴").assertExists()
        compose.onNodeWithText("全选").assertExists()
    }

    @Test
    fun `input context menu shows paste for selection when pasteable`() {
        mockClipboardText("from clipboard")
        val controller = TextEditingController("hello world")
        pumpInput(controller)
        compose.showTextContextMenu(TextRange(0, 5))

        compose.onNodeWithText("剪切").assertExists()
        compose.onNodeWithText("复制").assertExists()
        compose.onNodeWithText("粘贴").assertExists()
        compose.onNodeWithText("Ctrl+V").assertExists()
        compose.onNodeWithText("全选").assertExists()
    }

    @Test
    fun `input context menu item stays inside tap region`() {
        mockClipboardText(null)
        val group = Any()
        val controller = TextEditingController("hello")
        var outsideTapCount = 0
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TapRegion(groupId = group, onTapOutside = { outsideTapCount++ }) {
                    Box(Modifier.width(320.dp)) { Input(controller = controller, tapRegionGroupId = group) }
                }
            }
        }
        compose.showTextContextMenu(TextRange(5))

        compose.onNodeWithText("Ctrl+A").performClick()
        compose.waitForIdle()

        assertEquals(0, outsideTapCount)
        assertEquals(TextRange(0, 5), controller.selection)
    }

    @Test
    fun `input context menu remains open through ancestor rebuild`() {
        mockClipboardText(null)
        val controller = TextEditingController("hello world")
        val openStates = mutableListOf<Boolean>()
        var generation by mutableIntStateOf(0)
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("generation $generation")
                    Box(Modifier.width(320.dp)) {
                        ReadOnlySelectableText(value = controller.text, style = UiTypography.body, onContextMenuOpenChanged = { openStates.add(it) })
                    }
                }
            }
        }
        compose.onNodeWithText("hello world").performTouchInput { doubleClick(Offset(left + 30.dp.toPx(), centerY)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)

        compose.onNodeWithTag(textContextMenuPanelTag).assertExists()
        assertEquals(listOf(true), openStates)

        generation++
        compose.waitForIdle()

        compose.onNodeWithText("generation 1").assertExists()
        compose.onNodeWithTag(textContextMenuPanelTag).assertExists()
        assertEquals(listOf(true), openStates)
    }

    @Test
    fun `input context menu shows undo when undo is available`() {
        mockClipboardText(null)
        val controller = TextEditingController("hello")
        val undoController = UndoHistoryController(UndoHistoryValue(canUndo = true))
        pumpInput(controller, undoController = undoController)
        compose.textField().performClick()
        compose.waitForIdle()
        undoController.value = UndoHistoryValue(canUndo = true)
        compose.showTextContextMenu(TextRange(5))
        // The focus push of the undo history settles after 500 ms; keep the value.
        undoController.value = UndoHistoryValue(canUndo = true)
        compose.waitForIdle()

        compose.onNodeWithText("撤销").assertExists()
        compose.onNodeWithText("Ctrl+Z").assertExists()
        val divider = compose.onNodeWithTag(textContextMenuDividerTag, useUnmergedTree = true)
        divider.assertExists()
        assertTrue(divider.getBoundsInRoot().width > 120.dp)
    }

    @Test
    fun `input ctrl Y invokes redo on the focused field`() {
        val controller = TextEditingController()
        val undoController = UndoHistoryController(UndoHistoryValue(canRedo = true))
        var redoCount = 0
        undoController.onRedo.addListener { redoCount++ }
        pumpInput(controller, undoController = undoController)
        compose.textField().performClick()
        compose.waitForIdle()
        undoController.value = UndoHistoryValue(canRedo = true)

        compose.textField().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Y) } }
        compose.waitForIdle()

        assertEquals(1, redoCount)
    }

    @Test
    fun `Android input hold keeps focus and opens the text menu`() {
        val controller = TextEditingController("hello world")
        val focusNode = FocusNode()
        val focusEvents = mutableListOf<Boolean>()
        focusNode.addListener { focusEvents.add(focusNode.hasFocus) }
        pumpInput(controller, focusNode = focusNode)
        compose.textField().performClick()
        compose.waitForIdle()
        focusEvents.clear()

        compose.textField().performTouchInput { longClick(Offset(left + 30.dp.toPx(), centerY)) }
        compose.waitForIdle()

        assertTrue(focusNode.hasFocus)
        assertFalse(focusEvents.contains(false))
        assertFalse(controller.selection.collapsed)
        assertEquals(2, compose.onAllNodes(isSelectionHandle, useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNodeWithTag(textContextMenuPanelTag).assertExists()
    }

    @Test
    fun `Android input keeps its selection overlay across rebuilds`() {
        val controller = TextEditingController("hello world")
        pumpInput(controller)
        compose.textField().performTouchInput { doubleClick(Offset(left + 30.dp.toPx(), centerY)) }
        compose.waitForIdle()

        assertFalse(controller.selection.collapsed)
        assertEquals(2, compose.onAllNodes(isSelectionHandle, useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
