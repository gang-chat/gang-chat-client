package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.FocusNode
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.UndoHistoryController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.padding
import kotlin.math.min

/**
 * Selectable, non-editable text with the kit's text context menu (copy /
 * select all), sized like plain text. [fieldKey] becomes the field's test tag
 * (Dart's `fieldKey`).
 *
 * The desktop-only `secondaryClickSelection` (mouse right-click) is not
 * ported.
 */
@Composable
fun ReadOnlySelectableText(
    value: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fieldKey: String? = null,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Start,
    showSelectAllInContextMenu: Boolean = true,
    contextMenuTapRegionGroupId: Any? = null,
    onContextMenuOpenChanged: ((Boolean) -> Unit)? = null,
) = StatefulWidget(
    ReadOnlySelectableTextWidget(
        value, style, fieldKey, maxLines, textAlign, showSelectAllInContextMenu, contextMenuTapRegionGroupId,
        onContextMenuOpenChanged, modifier,
    ),
) { _ReadOnlySelectableTextState() }

class ReadOnlySelectableTextWidget(
    val value: String,
    val style: TextStyle,
    val fieldKey: String?,
    val maxLines: Int,
    val textAlign: TextAlign,
    val showSelectAllInContextMenu: Boolean,
    val contextMenuTapRegionGroupId: Any?,
    val onContextMenuOpenChanged: ((Boolean) -> Unit)?,
    val modifier: Modifier,
)

private class _ReadOnlySelectableTextState : ViewState<ReadOnlySelectableTextWidget>() {
    lateinit var _controller: TextEditingController
    private val _focusNode = FocusNode()
    private val _undoController = UndoHistoryController()

    override fun initState() {
        _controller = TextEditingController(text = widget.value)
    }

    override fun didUpdateWidget(oldWidget: ReadOnlySelectableTextWidget) {
        if (oldWidget.value == widget.value) return
        val selection = _controller.selection
        _controller.value = TextFieldValue(widget.value, _clampTextSelection(selection, widget.value.length))
    }

    override fun dispose() {
        _controller.dispose()
        _focusNode.dispose()
        _undoController.dispose()
    }

    @Composable
    override fun build() {
        TextFieldEditingShortcuts(
            controller = _controller,
            focusNode = _focusNode,
            undoController = _undoController,
            modifier = widget.modifier,
        ) {
            _EditableTextField(
                controller = _controller,
                focusNode = _focusNode,
                undoController = _undoController,
                style = uiTheme().textTheme.bodyLarge.merge(widget.style),
                readOnly = true,
                showCursor = false,
                minLines = 1,
                maxLines = widget.maxLines,
                textAlign = widget.textAlign,
                contextMenu =
                    TextFieldContextMenu(
                        readOnly = true,
                        showReadOnlySelectAll = widget.showSelectAllInContextMenu,
                        tapRegionGroupId = widget.contextMenuTapRegionGroupId,
                        onOpenChanged = widget.onContextMenuOpenChanged,
                    ),
                modifier = Modifier.fillMaxWidth().then(widget.fieldKey?.let { Modifier.testTag(it) } ?: Modifier),
            ) { innerTextField -> innerTextField() }
        }
    }
}

/** A read-only, selectable value in a bordered box (Dart's `ReadOnlyTextBox`). */
@Composable
fun ReadOnlyTextBox(
    value: String,
    modifier: Modifier = Modifier,
    fieldKey: String? = null,
    /** Null grows without limit. */
    maxLines: Int? = 1,
    style: TextStyle? = null,
    backgroundColor: Color = UiColors.background,
    borderColor: Color = UiColors.border,
    padding: EdgeInsets = EdgeInsets.fromLTRB(13.dp, 11.dp, 13.dp, 11.dp),
) = StatefulWidget(ReadOnlyTextBoxWidget(value, fieldKey, maxLines, style, backgroundColor, borderColor, padding, modifier)) {
    _ReadOnlyTextBoxState()
}

class ReadOnlyTextBoxWidget(
    val value: String,
    val fieldKey: String?,
    val maxLines: Int?,
    val style: TextStyle?,
    val backgroundColor: Color,
    val borderColor: Color,
    val padding: EdgeInsets,
    val modifier: Modifier,
)

private class _ReadOnlyTextBoxState : ViewState<ReadOnlyTextBoxWidget>() {
    lateinit var _controller: TextEditingController
    private val _focusNode = FocusNode()
    private val _undoController = UndoHistoryController()

    override fun initState() {
        _controller = TextEditingController(text = widget.value)
    }

    override fun didUpdateWidget(oldWidget: ReadOnlyTextBoxWidget) {
        if (oldWidget.value == widget.value) return
        val selection = _controller.selection
        _controller.value = TextFieldValue(widget.value, _clampTextSelection(selection, widget.value.length))
    }

    override fun dispose() {
        _controller.dispose()
        _focusNode.dispose()
        _undoController.dispose()
    }

    @Composable
    override fun build() {
        val style = widget.style ?: UiTypography.body.copyWith(color = UiColors.textSecondary, fontWeight = FontWeight.W500)
        Box(
            widget.modifier.decoration(widget.backgroundColor, RoundedCornerShape(UiRadii.md), widget.borderColor),
            propagateMinConstraints = true,
        ) {
            TextFieldEditingShortcuts(controller = _controller, focusNode = _focusNode, undoController = _undoController) {
                _EditableTextField(
                    controller = _controller,
                    focusNode = _focusNode,
                    undoController = _undoController,
                    style = uiTheme().textTheme.bodyLarge.merge(style),
                    readOnly = true,
                    showCursor = false,
                    minLines = 1,
                    maxLines = widget.maxLines,
                    contextMenu = TextFieldContextMenu(readOnly = true),
                    modifier = Modifier.fillMaxWidth().then(widget.fieldKey?.let { Modifier.testTag(it) } ?: Modifier),
                ) { innerTextField ->
                    Box(Modifier.padding(widget.padding), contentAlignment = Alignment.CenterStart) { innerTextField() }
                }
            }
        }
    }
}

private fun _clampTextSelection(selection: TextRange?, length: Int): TextRange {
    if (selection == null) return TextRange(length)
    return TextRange(min(selection.start, length), min(selection.end, length))
}
