package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.FocusNode
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.UndoHistoryController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration

/** A labelled form field (Dart's `TextInput`): a 12 dp padded well with an optional label above. */
@Composable
fun TextInput(
    modifier: Modifier = Modifier,
    controller: TextEditingController? = null,
    label: String? = null,
    hint: String? = null,
    prefixIcon: ImageVector? = null,
    suffix: (@Composable () -> Unit)? = null,
    obscureText: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 1,
    onSubmitted: ((String) -> Unit)? = null,
    undoController: UndoHistoryController? = null,
) = StatefulWidget(
    TextInputWidget(controller, label, hint, prefixIcon, suffix, obscureText, minLines, maxLines, onSubmitted, undoController, modifier),
) { _TextInputState() }

class TextInputWidget(
    val controller: TextEditingController?,
    val label: String?,
    val hint: String?,
    val prefixIcon: ImageVector?,
    val suffix: (@Composable () -> Unit)?,
    val obscureText: Boolean,
    val minLines: Int,
    val maxLines: Int,
    val onSubmitted: ((String) -> Unit)?,
    val undoController: UndoHistoryController?,
    val modifier: Modifier,
)

private class _TextInputState : ViewState<TextInputWidget>() {
    private var _localController: TextEditingController? = null
    private val _focusNode = FocusNode()
    private var _localUndoController: UndoHistoryController? = null

    private val _effectiveController: TextEditingController get() = widget.controller ?: _localController!!
    private val _effectiveUndoController: UndoHistoryController get() = widget.undoController ?: _localUndoController!!

    override fun initState() {
        _localController = if (widget.controller == null) TextEditingController() else null
        _localUndoController = if (widget.undoController == null) UndoHistoryController() else null
    }

    override fun didUpdateWidget(oldWidget: TextInputWidget) {
        if (oldWidget.controller !== widget.controller) {
            if (oldWidget.controller == null && widget.controller != null) {
                _localController?.dispose()
                _localController = null
            } else if (oldWidget.controller != null && widget.controller == null) {
                _localController = TextEditingController()
            }
        }
        if (oldWidget.undoController !== widget.undoController) {
            if (oldWidget.undoController == null && widget.undoController != null) {
                _localUndoController?.dispose()
                _localUndoController = null
            } else if (oldWidget.undoController != null && widget.undoController == null) {
                _localUndoController = UndoHistoryController()
            }
        }
    }

    override fun dispose() {
        _localController?.dispose()
        _focusNode.dispose()
        _localUndoController?.dispose()
    }

    @Composable
    override fun build() {
        val field: @Composable (Modifier) -> Unit = { fieldModifier ->
            Row(
                fieldModifier
                    .fillMaxWidth()
                    .decoration(UiColors.fieldFill, RoundedCornerShape(UiRadii.md), UiColors.fieldBorder),
                verticalAlignment = if (widget.maxLines > 1) Alignment.Top else Alignment.CenterVertically,
            ) {
                val prefixIcon = widget.prefixIcon
                if (prefixIcon != null) {
                    Box(Modifier.padding(start = 12.dp, top = 12.dp)) {
                        Icon(prefixIcon, size = 18.dp, color = UiColors.textMuted)
                    }
                }
                TextFieldEditingShortcuts(
                    controller = _effectiveController,
                    focusNode = _focusNode,
                    undoController = _effectiveUndoController,
                    modifier = Modifier.weight(1f),
                ) {
                    val style = uiTheme().textTheme.bodyLarge.merge(UiTypography.body)
                    _EditableTextField(
                        controller = _effectiveController,
                        focusNode = _focusNode,
                        undoController = _effectiveUndoController,
                        style = style,
                        obscureText = widget.obscureText,
                        minLines = widget.minLines,
                        maxLines = widget.maxLines,
                        onSubmitted = widget.onSubmitted,
                        contextMenu =
                            TextFieldContextMenu(
                                undoController = _effectiveUndoController,
                                allowCut = !widget.obscureText,
                                allowCopy = !widget.obscureText,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    ) { innerTextField ->
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                            val hint = widget.hint
                            if (hint != null && _effectiveController.text.isEmpty()) {
                                Text(hint, style = style.copyWith(color = UiColors.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            innerTextField()
                        }
                    }
                }
                val suffix = widget.suffix
                if (suffix != null) {
                    Box(Modifier.padding(end = 6.dp)) { suffix() }
                }
            }
        }

        val label = widget.label
        if (label == null) {
            field(widget.modifier)
            return
        }
        Column(widget.modifier) {
            Text(label, style = UiTypography.label)
            Spacer(Modifier.height(7.dp))
            field(Modifier)
        }
    }
}
