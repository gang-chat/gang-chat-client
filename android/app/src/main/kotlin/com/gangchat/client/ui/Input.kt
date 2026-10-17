package com.gangchat.client.ui

import android.content.ClipData
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.BoxShadow
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.FocusNode
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.PointerDownEvent
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.TextInputFormatter
import com.gangchat.client.widgets.TextPainter
import com.gangchat.client.widgets.UndoHistory
import com.gangchat.client.widgets.UndoHistoryController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.applyTextInputFormatters
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.focusNode
import com.gangchat.client.widgets.listen
import com.gangchat.client.widgets.padding
import com.gangchat.client.widgets.tapRegion
import kotlinx.coroutines.launch
import kotlin.math.max

private val _inputHorizontalPadding = 12.dp
private val _inputIconSize = 17.dp
private val _inputClearButtonSize = 26.dp
private val _inputClearIconSize = 15.dp

/** Dart's `Input` static members. */
object Input {
    val defaultHeight: Dp = 40.dp
}

/**
 * The kit's text field: a dark well with a lime focus ring. [height] is the
 * collapsed (single-line) height of the field inside its 1 dp border.
 *
 * Android-specific behaviour kept from Dart: text and icons are centred
 * without the desktop optical offsets, unbounded multi-line fields get no
 * extra bottom padding, and the text context menu is the kit's panel shown
 * as the selection toolbar.
 */
@Composable
fun Input(
    modifier: Modifier = Modifier,
    controller: TextEditingController? = null,
    focusNode: FocusNode? = null,
    hintText: String = "输入消息",
    enabled: Boolean = true,
    obscureText: Boolean = false,
    /** Flutter `AutofillHints` names (`username`, `password`, `email`, ...). */
    autofillHints: List<String>? = null,
    keyboardType: KeyboardType? = null,
    textInputAction: ImeAction? = null,
    prefixIcon: ImageVector? = null,
    suffix: (@Composable () -> Unit)? = null,
    showClearButton: Boolean = false,
    clearTooltip: String = "清空搜索",
    minLines: Int = 1,
    /** Null grows without limit. */
    maxLines: Int? = 5,
    onSubmitted: ((String) -> Unit)? = null,
    onChanged: ((String) -> Unit)? = null,
    inputFormatters: List<TextInputFormatter>? = null,
    style: TextStyle = UiTypography.body,
    hintStyle: TextStyle = TextStyle(color = UiColors.textMuted),
    textAlign: TextAlign = TextAlign.Start,
    onTapOutside: ((PointerDownEvent) -> Unit)? = null,
    tapRegionGroupId: Any? = null,
    height: Dp = Input.defaultHeight,
    undoController: UndoHistoryController? = null,
    canPasteNonText: (suspend () -> Boolean)? = null,
    enableInteractiveSelection: Boolean = true,
    /**
     * Renders only the text field, without the well background and border, for
     * hosts (like the chat composer) that draw their own container.
     */
    bare: Boolean = false,
) = StatefulWidget(
    InputWidget(
        controller, focusNode, hintText, enabled, obscureText, autofillHints, keyboardType, textInputAction,
        prefixIcon, suffix, showClearButton, clearTooltip, minLines, maxLines, onSubmitted, onChanged,
        inputFormatters, style, hintStyle, textAlign, onTapOutside, tapRegionGroupId, height, undoController,
        canPasteNonText, enableInteractiveSelection, bare, modifier,
    ),
) { _InputState() }

class InputWidget(
    val controller: TextEditingController?,
    val focusNode: FocusNode?,
    val hintText: String,
    val enabled: Boolean,
    val obscureText: Boolean,
    val autofillHints: List<String>?,
    val keyboardType: KeyboardType?,
    val textInputAction: ImeAction?,
    val prefixIcon: ImageVector?,
    val suffix: (@Composable () -> Unit)?,
    val showClearButton: Boolean,
    val clearTooltip: String,
    val minLines: Int,
    val maxLines: Int?,
    val onSubmitted: ((String) -> Unit)?,
    val onChanged: ((String) -> Unit)?,
    val inputFormatters: List<TextInputFormatter>?,
    val style: TextStyle,
    val hintStyle: TextStyle,
    val textAlign: TextAlign,
    val onTapOutside: ((PointerDownEvent) -> Unit)?,
    val tapRegionGroupId: Any?,
    val height: Dp,
    val undoController: UndoHistoryController?,
    val canPasteNonText: (suspend () -> Boolean)?,
    val enableInteractiveSelection: Boolean,
    val bare: Boolean,
    val modifier: Modifier,
)

private class _InputState : ViewState<InputWidget>() {
    private var _localController: TextEditingController? = null
    private var _localFocusNode: FocusNode? = null
    private var _localUndoController: UndoHistoryController? = null
    private var _lastControllerText = ""
    private var _hovered = false

    private val _effectiveController: TextEditingController get() = widget.controller ?: _localController!!
    private val _effectiveFocusNode: FocusNode get() = widget.focusNode ?: _localFocusNode!!
    private val _effectiveUndoController: UndoHistoryController get() = widget.undoController ?: _localUndoController!!

    private val _isSearchInput: Boolean
        get() {
            val hintText = widget.hintText.trim()
            val clearTooltip = widget.clearTooltip.trim()
            return widget.prefixIcon == Icons.Filled.Search ||
                hintText.contains("搜索") ||
                (widget.showClearButton && clearTooltip.contains("搜索"))
        }

    private val _effectiveMinLines: Int get() = if (widget.obscureText || _isSearchInput) 1 else widget.minLines
    private val _effectiveMaxLines: Int? get() = if (widget.obscureText || _isSearchInput) 1 else widget.maxLines

    private val _effectiveTextInputAction: ImeAction?
        get() {
            if (widget.textInputAction != null) return widget.textInputAction
            return if (_isSearchInput) ImeAction.Search else null
        }

    private val _handleTextChanged: () -> Unit = {
        val text = _effectiveController.text
        if (_lastControllerText != text) {
            _lastControllerText = text
            setState()
        }
    }

    private val _handleFocusChanged: () -> Unit = { setState() }

    override fun initState() {
        _localController = if (widget.controller == null) TextEditingController() else null
        _localFocusNode = if (widget.focusNode == null) FocusNode() else null
        _localUndoController = if (widget.undoController == null) UndoHistoryController() else null
        _lastControllerText = _effectiveController.text
        _effectiveController.addListener(_handleTextChanged)
        _effectiveFocusNode.addListener(_handleFocusChanged)
    }

    override fun didUpdateWidget(oldWidget: InputWidget) {
        val oldController = oldWidget.controller ?: _localController
        if (oldWidget.controller !== widget.controller) {
            oldController?.removeListener(_handleTextChanged)
            if (oldWidget.controller != null && widget.controller == null) {
                _localController = TextEditingController()
            } else if (oldWidget.controller == null && widget.controller != null) {
                _localController?.dispose()
                _localController = null
            }
            _lastControllerText = _effectiveController.text
            _effectiveController.addListener(_handleTextChanged)
        }

        val oldFocusNode = oldWidget.focusNode ?: _localFocusNode
        if (oldWidget.focusNode !== widget.focusNode) {
            oldFocusNode?.removeListener(_handleFocusChanged)
            if (oldWidget.focusNode != null && widget.focusNode == null) {
                _localFocusNode = FocusNode()
            } else if (oldWidget.focusNode == null && widget.focusNode != null) {
                _localFocusNode?.dispose()
                _localFocusNode = null
            }
            _effectiveFocusNode.addListener(_handleFocusChanged)
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
        _effectiveController.removeListener(_handleTextChanged)
        _effectiveFocusNode.removeListener(_handleFocusChanged)
        _localController?.dispose()
        _localFocusNode?.dispose()
        _localUndoController?.dispose()
    }

    private fun _handleHoverChanged(hovered: Boolean) {
        if (_hovered == hovered) return
        _hovered = hovered
        if (_effectiveFocusNode.hasFocus) return
        setState()
    }

    private fun _clearText() {
        if (_effectiveController.text.isEmpty()) return
        _effectiveController.clear()
        widget.onChanged?.invoke("")
        _effectiveFocusNode.requestFocus()
    }

    @Composable
    override fun build() {
        val focused = _effectiveFocusNode.hasFocus
        val overlaysClearButton =
            widget.suffix == null && widget.showClearButton && widget.enabled && widget.height < Input.defaultHeight
        val canClear = overlaysClearButton && _effectiveController.text.isNotEmpty()
        val suffix = if (overlaysClearButton) null else _effectiveSuffix()
        val contentPadding = _contentPaddingFor(reserveClearButton = overlaysClearButton)
        val enabled = widget.enabled

        val textField: @Composable () -> Unit = {
            _EditableTextField(
                controller = _effectiveController,
                focusNode = _effectiveFocusNode,
                undoController = _effectiveUndoController,
                style = uiTheme().textTheme.bodyLarge.merge(widget.style),
                enabled = enabled,
                obscureText = widget.obscureText,
                autofillHints = widget.autofillHints,
                keyboardType = widget.keyboardType,
                textInputAction = _effectiveTextInputAction,
                minLines = _effectiveMinLines,
                maxLines = _effectiveMaxLines,
                onSubmitted = widget.onSubmitted,
                onChanged = widget.onChanged,
                inputFormatters = widget.inputFormatters,
                textAlign = widget.textAlign,
                contextMenu =
                    if (widget.enableInteractiveSelection) {
                        TextFieldContextMenu(
                            undoController = _effectiveUndoController,
                            canPasteNonText = widget.canPasteNonText,
                            tapRegionGroupId = widget.tapRegionGroupId,
                            allowCut = !widget.obscureText,
                            allowCopy = !widget.obscureText,
                        )
                    } else {
                        null
                    },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .tapRegion(
                            enabled = focused && widget.onTapOutside != null,
                            groupId = widget.tapRegionGroupId,
                            onTapOutside = widget.onTapOutside,
                        ),
            ) { innerTextField ->
                _InputDecorator(
                    prefixIcon = widget.prefixIcon,
                    suffix = suffix,
                    contentPadding = contentPadding,
                    hintText = if (_effectiveController.text.isEmpty()) widget.hintText else null,
                    hintStyle = uiTheme().textTheme.bodyLarge.merge(widget.style).merge(widget.hintStyle),
                    innerTextField = innerTextField,
                )
            }
        }

        val content: @Composable () -> Unit = {
            TextFieldEditingShortcuts(
                controller = _effectiveController,
                focusNode = _effectiveFocusNode,
                undoController = _effectiveUndoController,
                modifier = Modifier.fillMaxWidth().heightIn(min = widget.height),
            ) {
                if (overlaysClearButton) {
                    Box {
                        textField()
                        if (canClear) {
                            Box(Modifier.matchParentSize()) {
                                Box(
                                    Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = _inputHorizontalPadding)
                                        .width(_inputClearButtonSize)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    _InputClearButton(tooltip = widget.clearTooltip, onPressed = ::_clearText)
                                }
                            }
                        }
                    }
                } else {
                    textField()
                }
            }
        }

        if (widget.bare) {
            Box(widget.modifier, propagateMinConstraints = true) { content() }
            return
        }
        val background = if (enabled) UiColors.fieldFill else UiColors.disabledSurface
        val borderColor =
            when {
                !enabled -> UiColors.disabledBorder
                focused -> UiColors.accentStrong
                _hovered -> UiColors.fieldBorderHover
                else -> UiColors.fieldBorder
            }
        val duration = UiMotion.fast.inWholeMilliseconds.toInt()
        val animatedBackground by animateColorAsState(background, tween(duration, easing = UiMotion.curve), label = "inputBackground")
        val animatedBorder by animateColorAsState(borderColor, tween(duration, easing = UiMotion.curve), label = "inputBorder")
        val ring by animateFloatAsState(if (focused && enabled) 1f else 0f, tween(duration, easing = UiMotion.curve), label = "inputRing")
        val shape = RoundedCornerShape(UiRadii.md)
        Box(
            widget.modifier
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.none { it.type == PointerType.Mouse }) continue
                            when (event.type) {
                                PointerEventType.Enter -> _handleHoverChanged(true)
                                PointerEventType.Exit -> _handleHoverChanged(false)
                                else -> Unit
                            }
                        }
                    }
                }
                .boxShadow(
                    if (ring > 0f) listOf(BoxShadow(color = UiColors.accentStrong.copy(alpha = 0.2f), spreadRadius = 3.dp * ring)) else emptyList(),
                    shape,
                )
                .decoration(animatedBackground, shape, animatedBorder)
                // Flutter's Container insets its child by the border width.
                .padding(1.dp)
                .clip(RoundedCornerShape(UiRadii.md)),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }

    private fun _effectiveSuffix(): (@Composable () -> Unit)? {
        val canClear = widget.showClearButton && widget.enabled && _effectiveController.text.isNotEmpty()
        if (!canClear) return widget.suffix
        return { _InputClearButton(tooltip = widget.clearTooltip, onPressed = ::_clearText) }
    }

    private fun _contentPaddingFor(reserveClearButton: Boolean): EdgeInsets {
        // Flutter's line is exactly fontSize × height. Android grows lines that
        // use the CJK fallback font past that (fallback line spacing), so pad
        // for the taller of a Latin and a CJK line; the field's minimum height
        // keeps it at [InputWidget.height] either way.
        val lineHeight =
            max(
                TextPainter(" ", widget.style).layout(context).preferredLineHeight,
                TextPainter("中", widget.style).layout(context).preferredLineHeight,
            )
        val verticalPadding = max(0.0, (widget.height.value - lineHeight) / 2)
        // Android text metrics are centred without the desktop face
        // compensation (Dart's `_verticalOffsetFor` is 0 on Android), and
        // unbounded fields get no extra bottom padding there.
        return EdgeInsets.fromLTRB(
            _inputHorizontalPadding,
            verticalPadding.dp,
            _inputHorizontalPadding + (if (reserveClearButton) UiSpacing.sm + _inputClearButtonSize else 0.dp),
            verticalPadding.dp,
        )
    }
}

/**
 * Flutter's dense `InputDecorator` layout as `Input` configures it: the
 * prefix icon (12 dp left, 8 dp gap) and suffix replace the content padding
 * on their side and are centred vertically; the hint shows while empty.
 */
@Composable
private fun _InputDecorator(
    prefixIcon: ImageVector?,
    suffix: (@Composable () -> Unit)?,
    contentPadding: EdgeInsets,
    hintText: String?,
    hintStyle: TextStyle,
    innerTextField: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (prefixIcon != null) {
            Box(Modifier.padding(start = _inputHorizontalPadding, end = UiSpacing.sm)) {
                Icon(prefixIcon, size = _inputIconSize, color = UiColors.textMuted)
            }
        }
        Box(
            Modifier
                .weight(1f)
                .padding(
                    EdgeInsets(
                        left = if (prefixIcon != null) 0.dp else contentPadding.left,
                        top = contentPadding.top,
                        right = if (suffix != null) 0.dp else contentPadding.right,
                        bottom = contentPadding.bottom,
                    ),
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (hintText != null) {
                Text(hintText, style = hintStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            innerTextField()
        }
        if (suffix != null) {
            Box(Modifier.padding(start = UiSpacing.sm, end = _inputHorizontalPadding)) { suffix() }
        }
    }
}

@Composable
private fun _InputClearButton(tooltip: String, onPressed: () -> Unit, iconVerticalOffset: Dp = 0.dp) {
    UiTooltip(message = tooltip) {
        Box(
            Modifier
                .semantics {
                    role = Role.Button
                    contentDescription = tooltip
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onPressed)
                .size(_inputClearButtonSize),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                modifier = Modifier.offset(y = iconVerticalOffset),
                size = _inputClearIconSize,
                color = UiColors.textMuted,
            )
        }
    }
}

/**
 * The `TextField`/`EditableText` behaviour shared by [Input], [TextInput]
 * and the read-only text boxes: binds [controller] (the source of truth) and
 * [focusNode], runs [inputFormatters] and [onChanged] on user edits, records
 * undo history, maps keyboard actions to [onSubmitted] like Flutter's
 * `performAction`, and installs the kit's text context menu.
 */
@Composable
internal fun _EditableTextField(
    controller: TextEditingController,
    focusNode: FocusNode,
    undoController: UndoHistoryController,
    style: TextStyle,
    contextMenu: TextFieldContextMenu?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    showCursor: Boolean = true,
    obscureText: Boolean = false,
    autofillHints: List<String>? = null,
    keyboardType: KeyboardType? = null,
    textInputAction: ImeAction? = null,
    minLines: Int = 1,
    maxLines: Int? = 1,
    onSubmitted: ((String) -> Unit)? = null,
    onChanged: ((String) -> Unit)? = null,
    inputFormatters: List<TextInputFormatter>? = null,
    textAlign: TextAlign = TextAlign.Start,
    /**
     * Display-only styling of the controller's text (Dart's
     * `TextEditingController.buildTextSpan` override); ignored with
     * [obscureText].
     */
    visualTransformation: VisualTransformation = VisualTransformation.None,
    /** The laid-out text, for hit-testing styled spans (Dart's span recognizers). */
    onTextLayout: (TextLayoutResult) -> Unit = {},
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit,
) {
    listen(controller)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val focusManager = LocalFocusManager.current
    val pasteAction = LocalPasteTextAction.current
    UndoHistory(controller, focusNode, undoController)

    fun applyUserEdit(newValue: TextFieldValue) {
        val oldValue = controller.value
        val textChanged = oldValue.text != newValue.text
        val formatted = applyTextInputFormatters(inputFormatters, oldValue, newValue)
        controller.value = formatted
        if (textChanged) onChanged?.invoke(formatted.text)
    }

    val pasteClipboardText: () -> Unit = {
        scope.launch {
            val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            if (text.isNullOrEmpty()) return@launch
            val value = controller.value
            val start = value.selection.min.coerceIn(0, value.text.length)
            val end = value.selection.max.coerceIn(0, value.text.length)
            val nextText = value.text.replaceRange(start, end, text)
            applyUserEdit(TextFieldValue(nextText, TextRange(start + text.length)))
        }
    }
    val copySelection: () -> Unit = {
        val value = controller.value
        if (!value.selection.collapsed) {
            val selected = value.text.substring(value.selection.min, value.selection.max)
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", selected))) }
        }
    }

    fun finalizeEditing(action: ImeAction) {
        val multiline = maxLines == null || maxLines > 1
        val shouldUnfocus =
            when (action) {
                ImeAction.Done, ImeAction.Go, ImeAction.Search, ImeAction.Send, ImeAction.Next, ImeAction.Previous -> true
                ImeAction.Default -> !multiline
                else -> false
            }
        // Flutter's default onEditingComplete: finalize composing, then give up
        // focus or move it, depending on the action.
        val value = controller.value
        if (value.composition != null) controller.value = value.copy(composition = null)
        if (shouldUnfocus) {
            when (action) {
                ImeAction.Next -> focusManager.moveFocus(FocusDirection.Next)
                ImeAction.Previous -> focusManager.moveFocus(FocusDirection.Previous)
                else -> focusNode.unfocus()
            }
        }
        onSubmitted?.invoke(controller.text)
    }

    val singleLine = maxLines == 1
    val resolvedAction = textInputAction ?: if (singleLine) ImeAction.Done else ImeAction.Default
    val contentType = _contentTypeFor(autofillHints)

    TextFieldContextMenuScope(
        menu = contextMenu,
        field = TextContextMenuField(controller, pasteClipboardText, copySelection),
        modifier = modifier,
    ) {
        BasicTextField(
            value = controller.value,
            onValueChange = { applyUserEdit(it) },
            modifier =
                Modifier
                    .focusNode(focusNode)
                    .then(if (contentType != null) Modifier.semantics { this.contentType = contentType } else Modifier)
                    .onPreviewKeyEvent { event ->
                        // A composer's paste override also handles Ctrl+V (Flutter's
                        // PasteTextIntent shortcut).
                        if (pasteAction != null && !readOnly && event.type == KeyEventType.KeyDown &&
                            event.isCtrlPressed && event.key == Key.V
                        ) {
                            pasteAction(pasteClipboardText)
                            true
                        } else {
                            false
                        }
                    },
            enabled = enabled,
            readOnly = readOnly,
            textStyle = style.copy(textAlign = textAlign),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = keyboardType ?: if (obscureText) KeyboardType.Password else KeyboardType.Unspecified,
                    imeAction = resolvedAction,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = { finalizeEditing(ImeAction.Done) },
                    onGo = { finalizeEditing(ImeAction.Go) },
                    onNext = { finalizeEditing(ImeAction.Next) },
                    onPrevious = { finalizeEditing(ImeAction.Previous) },
                    onSearch = { finalizeEditing(ImeAction.Search) },
                    onSend = { finalizeEditing(ImeAction.Send) },
                ),
            singleLine = singleLine,
            maxLines = maxLines ?: Int.MAX_VALUE,
            minLines = minLines.coerceAtMost(maxLines ?: Int.MAX_VALUE),
            visualTransformation =
                when {
                    obscureText -> PasswordVisualTransformation()
                    visualTransformation !== VisualTransformation.None -> visualTransformation
                    // A controller that styles its own text (Dart's
                    // `TextEditingController.buildTextSpan` override).
                    controller.buildTextSpan(controller.text, style) != null ->
                        VisualTransformation { original ->
                            TransformedText(controller.buildTextSpan(original.text, style) ?: original, OffsetMapping.Identity)
                        }
                    else -> VisualTransformation.None
                },
            onTextLayout = onTextLayout,
            cursorBrush = SolidColor(if (showCursor) uiTheme().cursorColor else Color.Transparent),
            decorationBox = decorationBox,
        )
    }
}

/** Flutter `AutofillHints` names to Compose autofill content types. */
private fun _contentTypeFor(hints: List<String>?): ContentType? {
    if (hints.isNullOrEmpty()) return null
    val types =
        hints.mapNotNull { hint ->
            when (hint) {
                "username" -> ContentType.Username
                "password" -> ContentType.Password
                "newUsername" -> ContentType.NewUsername
                "newPassword" -> ContentType.NewPassword
                "email" -> ContentType.EmailAddress
                "telephoneNumber" -> ContentType.PhoneNumber
                "oneTimeCode" -> ContentType.SmsOtpCode
                "name" -> ContentType.PersonFullName
                else -> null
            }
        }
    if (types.isEmpty()) return null
    return types.reduce { acc, type -> acc + type }
}
