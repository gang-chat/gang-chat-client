package com.gangchat.client.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.gangchat.client.foundation.ChangeNotifier
import com.gangchat.client.widgets.Curves
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.FocusNode
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.TextInputFormatter
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.listen
import com.gangchat.client.widgets.padding
import kotlinx.coroutines.launch

enum class ComposerPanelType { list, static }

enum class ComposerSuggestionNavigation { previous, next }

enum class ComposerSuggestionAction { confirm }

/**
 * Lets an owner drive the composer's open panel imperatively — chiefly to
 * retract a panel (e.g. the voice recorder) once its action completes, so the
 * composer collapses back to the input without the user tapping again.
 */
class ChatComposerController : ChangeNotifier() {
    var closeRequested: Boolean = false
        private set
    var focusRequested: Boolean = false
        private set

    /** Asks the attached [ChatComposer] to close whatever panel is open. */
    fun closePanel() {
        closeRequested = true
        notifyListeners()
    }

    fun requestInputFocus() {
        focusRequested = true
        notifyListeners()
    }

    internal fun _consumeCloseRequest() {
        closeRequested = false
    }

    internal fun _consumeFocusRequest() {
        focusRequested = false
    }
}

enum class ComposerActionAlignment { leading, trailing }

class ComposerAction(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val tooltip: String? = null,
    val panel: ComposerPanel? = null,
    val onPressed: (() -> Unit)? = null,
    val tone: ButtonTone = ButtonTone.neutral,
    val selected: Boolean = false,
    /**
     * Where the action sits on the button row below the input. Trailing actions
     * (typically send) are pinned to the far right.
     */
    val alignment: ComposerActionAlignment = ComposerActionAlignment.leading,
) {
    val opensPanel: Boolean get() = panel != null
}

/** A panel an action opens above the composer: a wrapping list of items, or a fixed-height child. */
class ComposerPanel private constructor(
    val type: ComposerPanelType,
    val itemCount: Int,
    val itemBuilder: (@Composable (index: Int) -> Unit)?,
    val child: (@Composable () -> Unit)?,
    val height: Dp,
    val padding: EdgeInsets,
) {
    companion object {
        fun list(
            itemCount: Int,
            itemBuilder: @Composable (index: Int) -> Unit,
            height: Dp = 220.dp,
            padding: EdgeInsets = EdgeInsets.fromLTRB(12.dp, 12.dp, 12.dp, 14.dp),
        ) = ComposerPanel(ComposerPanelType.list, itemCount, itemBuilder, null, height, padding)

        fun static(
            child: @Composable () -> Unit,
            height: Dp = 124.dp,
            padding: EdgeInsets = EdgeInsets.fromLTRB(12.dp, 12.dp, 12.dp, 14.dp),
        ) = ComposerPanel(ComposerPanelType.static, 0, null, child, height, padding)
    }
}

/**
 * The chat input: a rounded composer with leading action buttons, the text
 * field and trailing (send) actions; actions with a [ComposerPanel] open it
 * above the composer. Hardware Enter sends, Shift/Ctrl+Enter inserts a
 * newline.
 */
@Composable
fun ChatComposer(
    actions: List<ComposerAction>,
    modifier: Modifier = Modifier,
    controller: TextEditingController? = null,
    /** Optional imperative handle for closing the open panel from outside. */
    composerController: ChatComposerController? = null,
    hintText: String = "输入消息",
    minLines: Int = 1,
    maxLines: Int = 5,
    onSubmitted: ((String) -> Unit)? = null,
    onChanged: ((String) -> Unit)? = null,
    suggestionShortcutsEnabled: Boolean = false,
    onSuggestionNavigationPressed: ((ComposerSuggestionNavigation) -> Boolean)? = null,
    onSuggestionActionPressed: ((ComposerSuggestionAction) -> Boolean)? = null,
    inputFormatters: List<TextInputFormatter>? = null,
    /**
     * Invoked when the user pastes into the composer. Should stage any clipboard
     * files/image as attachments and return true when it consumed the paste, so
     * the composer can skip the default text paste.
     */
    onPasteFiles: (suspend () -> Boolean)? = null,
    /**
     * Checks whether the clipboard currently contains files/images that
     * [onPasteFiles] can consume. Used to keep the context-menu paste action
     * visible for non-text clipboard contents without showing it for an empty
     * clipboard.
     */
    onCanPasteFiles: (suspend () -> Boolean)? = null,
    /**
     * Optional strip rendered above the input, used to show files staged for
     * the next message. Null leaves the composer unchanged.
     */
    attachments: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
) = StatefulWidget(
    ChatComposerWidget(
        actions, controller, composerController, hintText, minLines, maxLines, onSubmitted, onChanged,
        suggestionShortcutsEnabled, onSuggestionNavigationPressed, onSuggestionActionPressed, inputFormatters,
        onPasteFiles, onCanPasteFiles, attachments, header, modifier,
    ),
) { _ChatComposerState() }

class ChatComposerWidget(
    val actions: List<ComposerAction>,
    val controller: TextEditingController?,
    val composerController: ChatComposerController?,
    val hintText: String,
    val minLines: Int,
    val maxLines: Int,
    val onSubmitted: ((String) -> Unit)?,
    val onChanged: ((String) -> Unit)?,
    val suggestionShortcutsEnabled: Boolean,
    val onSuggestionNavigationPressed: ((ComposerSuggestionNavigation) -> Boolean)?,
    val onSuggestionActionPressed: ((ComposerSuggestionAction) -> Boolean)?,
    val inputFormatters: List<TextInputFormatter>?,
    val onPasteFiles: (suspend () -> Boolean)?,
    val onCanPasteFiles: (suspend () -> Boolean)?,
    val attachments: (@Composable () -> Unit)?,
    val header: (@Composable () -> Unit)?,
    val modifier: Modifier,
)

private class _ChatComposerState : ViewState<ChatComposerWidget>() {
    private val _inputFocusNode = FocusNode()
    private var _localController: TextEditingController? = null
    private var _openActionId: String? = null

    private val _effectiveController: TextEditingController get() = widget.controller ?: _localController!!

    private val _openAction: ComposerAction?
        get() {
            val id = _openActionId ?: return null
            return widget.actions.firstOrNull { it.id == id && it.panel != null }
        }

    private val _handleComposerControllerChanged: () -> Unit = handler@{
        val controller = widget.composerController ?: return@handler
        val closeRequested = controller.closeRequested
        val focusRequested = controller.focusRequested
        if (!closeRequested && !focusRequested) return@handler
        if (closeRequested) controller._consumeCloseRequest()
        if (focusRequested) controller._consumeFocusRequest()
        if (closeRequested && _openActionId != null) setState { _openActionId = null }
        if (focusRequested) _inputFocusNode.requestFocus()
    }

    override fun initState() {
        _localController = if (widget.controller == null) TextEditingController() else null
        widget.composerController?.addListener(_handleComposerControllerChanged)
    }

    override fun didUpdateWidget(oldWidget: ChatComposerWidget) {
        if (oldWidget.composerController !== widget.composerController) {
            oldWidget.composerController?.removeListener(_handleComposerControllerChanged)
            widget.composerController?.addListener(_handleComposerControllerChanged)
        }
        if (oldWidget.controller === widget.controller) return

        if (widget.controller == null) {
            _localController = TextEditingController().also { local -> oldWidget.controller?.value?.let { local.value = it } }
            return
        }

        _localController?.dispose()
        _localController = null
    }

    override fun dispose() {
        widget.composerController?.removeListener(_handleComposerControllerChanged)
        _localController?.dispose()
        _inputFocusNode.dispose()
    }

    /** Flutter's `HardwareKeyboard` handler: Enter sends, Shift/Ctrl+Enter inserts a newline. */
    private fun _handleKeyboardEvent(key: Key, shift: Boolean, control: Boolean, alt: Boolean, meta: Boolean): Boolean {
        if (!_inputFocusNode.hasFocus) return false
        if (!_isEnterKey(key) || alt || meta) return false

        if (shift || control) {
            _insertNewline()
            return true
        }

        val onSubmitted = widget.onSubmitted ?: return false
        onSubmitted(_effectiveController.text)
        return true
    }

    private fun _isEnterKey(key: Key): Boolean = key == Key.Enter || key == Key.NumPadEnter

    private fun _insertNewline() {
        val controller = _effectiveController
        val value = controller.value
        val text = value.text
        val selection = value.selection
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(0, text.length)
        val nextText = text.substring(0, start) + "\n" + text.substring(end)
        controller.value = value.copy(text = nextText, selection = TextRange(start + 1), composition = null)
        widget.onChanged?.invoke(nextText)
    }

    private fun _handleAction(action: ComposerAction) {
        if (action.panel == null) {
            if (_openActionId != null) setState { _openActionId = null }
            action.onPressed?.invoke()
            return
        }

        setState { _openActionId = if (_openActionId == action.id) null else action.id }
        action.onPressed?.invoke()
    }

    /** Suggestion shortcuts (Tab confirms, ↑/↓ navigate) while [ChatComposerWidget.suggestionShortcutsEnabled]. */
    private fun _handleSuggestionKey(key: Key): Boolean {
        if (!widget.suggestionShortcutsEnabled) return false
        when (key) {
            Key.Tab -> widget.onSuggestionActionPressed?.invoke(ComposerSuggestionAction.confirm)
            Key.DirectionUp -> widget.onSuggestionNavigationPressed?.invoke(ComposerSuggestionNavigation.previous)
            Key.DirectionDown -> widget.onSuggestionNavigationPressed?.invoke(ComposerSuggestionNavigation.next)
            else -> return false
        }
        _inputFocusNode.requestFocus()
        return true
    }

    @Composable
    override fun build() {
        val scope = rememberCoroutineScope()
        listen(_inputFocusNode)
        val openAction = _openAction
        val openPanel = openAction?.panel
        val leading = widget.actions.filter { it.alignment == ComposerActionAlignment.leading }
        val trailing = widget.actions.filter { it.alignment == ComposerActionAlignment.trailing }

        // Routes the input's paste through onPasteFiles so a copied file becomes
        // an attachment instead of having its filename typed into the field.
        // Without an onPasteFiles handler the input keeps the default paste.
        val onPasteFiles = widget.onPasteFiles
        val pasteAction: ((() -> Unit) -> Unit)? =
            if (onPasteFiles == null) {
                null
            } else {
                { fallback -> scope.launch { if (!onPasteFiles()) fallback() } }
            }

        val input: @Composable () -> Unit = {
            CompositionLocalProvider(LocalPasteTextAction provides pasteAction) {
                Box(
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        _handleKeyboardEvent(event.key, event.isShiftPressed, event.isCtrlPressed, event.isAltPressed, event.isMetaPressed) ||
                            _handleSuggestionKey(event.key)
                    },
                ) {
                    Input(
                        controller = _effectiveController,
                        focusNode = _inputFocusNode,
                        hintText = widget.hintText,
                        minLines = widget.minLines,
                        maxLines = widget.maxLines,
                        inputFormatters = widget.inputFormatters,
                        canPasteNonText = widget.onCanPasteFiles,
                        onSubmitted = widget.onSubmitted,
                        onChanged = widget.onChanged,
                        bare = true,
                    )
                }
            }
        }

        val focused = _inputFocusNode.hasFocus
        val border by animateColorAsState(
            if (focused) UiColors.accentBorder else UiColors.border,
            tween(UiMotion.fast.inWholeMilliseconds.toInt(), easing = UiMotion.curve),
            label = "composerBorder",
        )
        val shape = RoundedCornerShape(UiRadii.lg + 4.dp)
        Column(widget.modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(tween(UiMotion.standard.inWholeMilliseconds.toInt(), easing = Curves.easeOutCubic), alignment = Alignment.BottomCenter),
            ) {
                if (openPanel != null) {
                    key(openAction.id) {
                        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) { _ComposerPanelFrame(panel = openPanel) }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .boxShadow(UiShadows.raised, shape)
                    .decoration(UiColors.surface, shape, border)
                    // Flutter's Container insets its child by the border width.
                    .padding(1.dp)
                    .clip(RoundedCornerShape(UiRadii.lg + 3.dp))
                    // _ComposerContent: scrolling inside the composer stays inside it.
                    .nestedScroll(_absorbNestedScroll),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    val header = widget.header
                    if (header != null) {
                        Box(Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp, end = 10.dp)) { header() }
                    }
                    val attachments = widget.attachments
                    if (attachments != null) {
                        Box(Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp, end = 10.dp)) { attachments() }
                    }
                    Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.Bottom) {
                        for (action in leading) {
                            _ComposerActionButton(
                                action = action,
                                selected = action.selected || action.id == _openActionId,
                                onAction = ::_handleAction,
                            )
                        }
                        if (leading.isNotEmpty()) {
                            Box(
                                Modifier
                                    .padding(start = 4.dp, end = 2.dp, bottom = 10.dp)
                                    .size(1.dp, 20.dp)
                                    .decoration(UiColors.borderStrong),
                            )
                        }
                        Box(Modifier.weight(1f)) { input() }
                        for (action in trailing) {
                            _ComposerActionButton(
                                action = action,
                                selected = action.selected || action.id == _openActionId,
                                onAction = ::_handleAction,
                                prominent = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Flutter's `NotificationListener<ScrollNotification>(onNotification: (_) => true)`. */
private val _absorbNestedScroll =
    object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
    }

@Composable
private fun _ComposerActionButton(
    action: ComposerAction,
    selected: Boolean,
    onAction: (ComposerAction) -> Unit,
    /** Trailing actions (send) render as a filled round button. */
    prominent: Boolean = false,
) {
    if (prominent) {
        val primary = action.tone == ButtonTone.primary
        Box(Modifier.padding(start = 4.dp)) {
            PressableSurface(
                width = 40.dp,
                height = 40.dp,
                tooltip = action.tooltip ?: action.label,
                selected = selected,
                onPressed = { onAction(action) },
                backgroundColor = if (primary) UiColors.accentStrong else UiColors.surfaceRaised,
                selectedBackgroundColor = if (primary) UiColors.accentStrong else UiColors.selected,
                borderColor = Color.Transparent,
                selectedBorderColor = Color.Transparent,
                borderRadius = UiRadii.pill,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(action.icon, size = 19.dp, color = if (primary) UiColors.onAccent else UiColors.textSecondary)
                }
            }
        }
        return
    }
    ButtonIcon(
        tooltip = action.tooltip ?: action.label,
        icon = { Icon(action.icon) },
        tone = action.tone,
        selected = selected,
        ghost = true,
        size = 40.dp,
        onPressed = { onAction(action) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun _ComposerPanelFrame(panel: ComposerPanel) {
    val shape = RoundedCornerShape(UiRadii.lg)
    Box(
        Modifier
            .fillMaxWidth()
            .boxShadow(UiShadows.popover, shape)
            .decoration(UiColors.surfaceRaised, shape, UiColors.borderStrong),
    ) {
        when (panel.type) {
            ComposerPanelType.list ->
                Box(Modifier.fillMaxWidth().heightIn(max = panel.height).padding(panel.padding)) {
                    Box(Modifier.verticalScroll(rememberScrollState())) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val builder = panel.itemBuilder
                            if (builder != null) for (index in 0 until panel.itemCount) builder(index)
                        }
                    }
                }
            ComposerPanelType.static ->
                Box(Modifier.fillMaxWidth().height(panel.height).padding(panel.padding), propagateMinConstraints = true) {
                    panel.child?.invoke()
                }
        }
    }
}
