package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.TextInputFormatter
import kotlinx.coroutines.CompletableDeferred
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
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
@OptIn(ExperimentalTestApi::class)
class ChatComposerWidgetTest {
    @get:Rule
    val compose = createComposeRule()

    private fun pumpComposer(
        controller: TextEditingController? = null,
        actions: List<ComposerAction> = emptyList(),
        onSubmitted: ((String) -> Unit)? = null,
        onChanged: ((String) -> Unit)? = null,
        suggestionShortcutsEnabled: () -> Boolean = { false },
        onSuggestionNavigationPressed: ((ComposerSuggestionNavigation) -> Boolean)? = null,
        onSuggestionActionPressed: ((ComposerSuggestionAction) -> Boolean)? = null,
        inputFormatters: List<TextInputFormatter>? = null,
        onPasteFiles: (suspend () -> Boolean)? = null,
        onCanPasteFiles: (suspend () -> Boolean)? = null,
    ) {
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                ChatComposer(
                    actions = actions,
                    controller = controller,
                    onSubmitted = onSubmitted,
                    onChanged = onChanged,
                    suggestionShortcutsEnabled = suggestionShortcutsEnabled(),
                    onSuggestionNavigationPressed = onSuggestionNavigationPressed,
                    onSuggestionActionPressed = onSuggestionActionPressed,
                    inputFormatters = inputFormatters,
                    onPasteFiles = onPasteFiles,
                    onCanPasteFiles = onCanPasteFiles,
                )
            }
        }
    }

    private fun focusInput() {
        compose.textField().performClick()
        compose.waitForIdle()
    }

    private fun pasteShortcut() {
        compose.textField().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.V) } }
        compose.waitForIdle()
    }

    @Test
    fun `chat composer action can be highlighted externally`() {
        pumpComposer(actions = listOf(ComposerAction(id = "file", icon = Icons.Filled.AttachFile, label = "File", selected = true, onPressed = {})))

        val button =
            (hasTooltip("File") or hasAnyAncestor(hasTooltip("File"))) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        compose.onNode(button, useUnmergedTree = true).assertIsSelected()
    }

    @Test
    fun `chat composer reports paste shortcut from the input`() {
        var pasteAttempts = 0
        pumpComposer(
            actions = listOf(ComposerAction(id = "file", icon = Icons.Filled.AttachFile, label = "File")),
            onPasteFiles = {
                pasteAttempts++
                // Report "consumed" so the default text paste is suppressed,
                // matching a clipboard that holds a file.
                true
            },
        )

        focusInput()
        pasteShortcut()

        assertEquals(1, pasteAttempts)
    }

    @Test
    fun `chat composer context menu shows paste for clipboard files`() {
        mockClipboardText(null)
        var pasteAttempts = 0
        pumpComposer(
            onPasteFiles = {
                pasteAttempts++
                true
            },
            onCanPasteFiles = { true },
        )

        compose.showTextContextMenu()

        compose.onNodeWithText("粘贴").assertExists()
        compose.onNodeWithText("Ctrl+V").assertExists()

        compose.onNodeWithText("粘贴").performClick()
        compose.waitForIdle()

        assertEquals(1, pasteAttempts)
    }

    @Test
    fun `chat composer context menu hides paste without text or files`() {
        mockClipboardText(null)
        pumpComposer(onPasteFiles = { false }, onCanPasteFiles = { false })

        compose.showTextContextMenu()

        compose.onNodeWithText("粘贴").assertDoesNotExist()
        compose.onNodeWithText("Ctrl+V").assertDoesNotExist()
    }

    @Test
    fun `chat composer context-menu paste is routed before async probe completes`() {
        mockClipboardText("readable music component")
        var pasteAttempts = 0
        val pendingProbe = CompletableDeferred<Boolean>()
        pumpComposer(
            onPasteFiles = {
                pasteAttempts++
                true
            },
            onCanPasteFiles = { pendingProbe.await() },
        )

        compose.showTextContextMenu()

        compose.onNodeWithText("粘贴").assertExists()
        compose.onNodeWithText("粘贴").performClick()
        compose.waitForIdle()

        assertEquals(1, pasteAttempts)
        pendingProbe.complete(false)
        compose.waitForIdle()
    }

    @Test
    fun `chat composer sends focused input on Enter`() {
        val controller = TextEditingController("hello")
        val submissions = mutableListOf<String>()
        pumpComposer(controller = controller, onSubmitted = { submissions.add(it) })

        focusInput()
        compose.textField().performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()

        assertEquals(listOf("hello"), submissions)
        assertEquals("hello", controller.text)
    }

    @Test
    fun `chat composer inserts newline on Shift Enter`() {
        val controller = TextEditingController("hello")
        val submissions = mutableListOf<String>()
        val changes = mutableListOf<String>()
        pumpComposer(controller = controller, onSubmitted = { submissions.add(it) }, onChanged = { changes.add(it) })

        focusInput()
        controller.selection = TextRange(5)
        compose.textField().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) } }
        compose.waitForIdle()

        assertTrue(submissions.isEmpty())
        assertEquals(listOf("hello\n"), changes)
        assertEquals("hello\n", controller.text)
        assertEquals(6, controller.selection.start)
    }

    @Test
    fun `chat composer inserts newline on Control Enter`() {
        val controller = TextEditingController("hello")
        val submissions = mutableListOf<String>()
        pumpComposer(controller = controller, onSubmitted = { submissions.add(it) })

        focusInput()
        controller.selection = TextRange(1, 4)
        compose.textField().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Enter) } }
        compose.waitForIdle()

        assertTrue(submissions.isEmpty())
        assertEquals("h\no", controller.text)
        assertEquals(2, controller.selection.start)
    }

    @Test
    fun `chat composer reports suggestion navigation keys`() {
        val navigation = mutableListOf<ComposerSuggestionNavigation>()
        val actions = mutableListOf<ComposerSuggestionAction>()
        pumpComposer(
            suggestionShortcutsEnabled = { true },
            onSuggestionNavigationPressed = {
                navigation.add(it)
                true
            },
            onSuggestionActionPressed = {
                actions.add(it)
                true
            },
        )

        focusInput()
        compose.textField().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionUp)
            pressKey(Key.Tab)
        }
        compose.waitForIdle()

        compose.textField().assertIsFocused()
        assertEquals(listOf(ComposerSuggestionNavigation.next, ComposerSuggestionNavigation.previous), navigation)
        assertEquals(listOf(ComposerSuggestionAction.confirm), actions)
    }

    @Test
    fun `chat composer leaves horizontal arrows for cursor movement`() {
        val controller = TextEditingController("hello")
        val navigation = mutableListOf<ComposerSuggestionNavigation>()
        pumpComposer(
            controller = controller,
            suggestionShortcutsEnabled = { true },
            onSuggestionNavigationPressed = {
                navigation.add(it)
                true
            },
        )

        focusInput()
        controller.selection = TextRange(3)
        compose.textField().performKeyInput {
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionRight)
        }
        compose.waitForIdle()

        assertTrue(navigation.isEmpty())
    }

    @Test
    fun `chat composer keeps input focused when suggestions enable`() {
        val controller = TextEditingController("@")
        var suggestionsEnabled by mutableStateOf(false)
        pumpComposer(
            controller = controller,
            suggestionShortcutsEnabled = { suggestionsEnabled },
            onSuggestionNavigationPressed = { true },
            onSuggestionActionPressed = { true },
        )

        focusInput()
        compose.textField().assertIsFocused()

        suggestionsEnabled = true
        compose.waitForIdle()
        compose.textField().assertIsFocused()

        compose.textField().performTextReplacement("@a")
        compose.waitForIdle()
        assertEquals("@a", controller.text)
    }

    @Test
    fun `chat composer applies input formatters before text editing`() {
        val controller = TextEditingController("hello")
        val backspaceReplacement =
            TextInputFormatter { oldValue, newValue ->
                if (oldValue.text == "hello" && newValue.text == "hell") TextFieldValue("handled", TextRange(7)) else newValue
            }
        pumpComposer(controller = controller, inputFormatters = listOf(backspaceReplacement))

        focusInput()
        controller.selection = TextRange(5)
        compose.textField().performKeyInput { pressKey(Key.Backspace) }
        compose.waitForIdle()

        assertEquals("handled", controller.text)
    }

    @Test
    fun `paste does not type clipboard text when a file is staged`() {
        // A copied file's name on the clipboard as text.
        mockClipboardText("/Users/me/report.pdf")
        val controller = TextEditingController()
        // Returns true => paste was consumed as an attachment.
        pumpComposer(controller = controller, onPasteFiles = { true })

        focusInput()
        pasteShortcut()

        assertEquals("", controller.text)
    }

    @Test
    fun `paste still inserts text when no file is staged`() {
        mockClipboardText("hello world")
        val controller = TextEditingController()
        // Returns false => nothing staged, fall back to default text paste.
        pumpComposer(controller = controller, onPasteFiles = { false })

        focusInput()
        pasteShortcut()

        assertEquals("hello world", controller.text)
    }
}
