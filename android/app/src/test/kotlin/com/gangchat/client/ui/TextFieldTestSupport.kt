package com.gangchat.client.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider

/** Flutter test's `_mockClipboardText(text)`: sets (or clears) the clipboard text. */
fun mockClipboardText(text: String?) {
    val clipboard = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
    if (text == null) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(ClipData.newPlainText("", text))
}

/** The current clipboard text. */
fun clipboardText(): String? {
    val clipboard = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}

/** The (single) editable text field. */
fun ComposeContentTestRule.textField(): SemanticsNodeInteraction = onNode(hasSetTextAction())

/** Any text field, read-only ones included (Flutter's `find.byType(TextField)`). */
val isTextField = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)

/** Compose's selection handles (their semantics key is internal, so match by name). */
val isSelectionHandle = SemanticsMatcher("is selection handle") { node -> node.config.any { it.key.name == "SelectionHandleInfo" } }

/**
 * Dart test's `_showInputContextMenu`: focuses the field, applies [selection]
 * and shows the text toolbar (Flutter's `EditableTextState.showToolbar()`).
 * The field's long-click semantics action enters selection mode, which shows
 * the toolbar for the current selection without moving it. A real long press
 * first: until the first touch selection gesture in the test JVM, Compose's
 * legacy field does not show its toolbar or handles for programmatic
 * selection.
 */
fun ComposeContentTestRule.showTextContextMenu(selection: TextRange? = null, field: SemanticsMatcher = hasSetTextAction()) {
    onNode(field).performClick()
    waitForIdle()
    onNode(field).performTouchInput { longClick(Offset(left + 30.dp.toPx(), centerY)) }
    waitForIdle()
    if (selection != null) {
        onNode(field).performTextInputSelection(selection)
        waitForIdle()
    }
    onNode(field).performSemanticsAction(SemanticsActions.OnLongClick)
    waitForIdle()
    mainClock.advanceTimeBy(100)
}
