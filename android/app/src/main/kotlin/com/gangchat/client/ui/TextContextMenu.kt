package com.gangchat.client.ui

import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuData
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.gangchat.client.widgets.FocusNode
import com.gangchat.client.widgets.LocalMediaQuery
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.UndoHistoryController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.listen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

private val _contextMenuScreenPadding = 8.dp
private val _contextMenuWidth = 196.dp
private val _contextMenuMinItemHeight = 32.dp
private val _contextMenuHorizontalPadding = 10.dp

/** Flutter's `TextSelectionToolbar.kToolbarContentDistanceBelow` (handle size 22 − 2). */
private val _kToolbarContentDistanceBelow = 20.dp

/** Test tag of the menu panel (Dart's `ValueKey('text-context-menu-panel')`). */
const val textContextMenuPanelTag = "text-context-menu-panel"

/** Test tag of the section divider (Dart's `ValueKey('text-context-menu-divider')`). */
const val textContextMenuDividerTag = "text-context-menu-divider"

private object _ContextMenuPanelBounds {
    var coordinates: LayoutCoordinates? = null
    var hostView: View? = null
}

/**
 * Whether [globalPosition] (main-window dp, the coordinate space of
 * `RenderBoxHandle`) lies on the open text context menu panel.
 */
fun isTextContextMenuPanelHit(globalPosition: DpOffset, density: androidx.compose.ui.unit.Density): Boolean {
    val coords = _ContextMenuPanelBounds.coordinates ?: return false
    if (!coords.isAttached) return false
    val view = _ContextMenuPanelBounds.hostView ?: return false
    val location = IntArray(2)
    view.getLocationOnScreen(location)
    val screen =
        with(density) { Offset(globalPosition.x.toPx() + location[0], globalPosition.y.toPx() + location[1]) }
    val topLeft = coords.localToScreen(Offset.Zero)
    val size = coords.size
    return screen.x >= topLeft.x && screen.y >= topLeft.y && screen.x <= topLeft.x + size.width && screen.y <= topLeft.y + size.height
}

/**
 * Flutter's `TextFieldEditingShortcuts` (Android branch): Ctrl+Y redoes, and
 * the text field's undo/redo shortcuts (Ctrl+Z, Ctrl+Shift+Z) go through
 * [undoController] like Flutter's `UndoTextIntent`/`RedoTextIntent`. The
 * desktop secondary-click selection protection is not ported (no mouse
 * right-click on Android).
 */
@Composable
fun TextFieldEditingShortcuts(
    undoController: UndoHistoryController,
    modifier: Modifier = Modifier,
    controller: TextEditingController? = null,
    focusNode: FocusNode? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) return@onPreviewKeyEvent false
            when {
                event.key == Key.Y && !event.isShiftPressed -> {
                    undoController.redo()
                    true
                }
                event.key == Key.Z && event.isShiftPressed -> {
                    undoController.redo()
                    true
                }
                event.key == Key.Z -> {
                    undoController.undo()
                    true
                }
                else -> false
            }
        },
        propagateMinConstraints = true,
    ) {
        content()
    }
}

/**
 * Dart's `buildTextFieldContextMenu(...)` arguments: which actions the kit's
 * text context menu offers for a field. Install it around a text field with
 * [TextFieldContextMenuScope].
 */
class TextFieldContextMenu(
    val undoController: UndoHistoryController? = null,
    val tapRegionGroupId: Any? = null,
    val onOpenChanged: ((Boolean) -> Unit)? = null,
    val onActionPressed: (() -> Unit)? = null,
    val canPasteNonText: (suspend () -> Boolean)? = null,
    val readOnly: Boolean = false,
    val showReadOnlySelectAll: Boolean = true,
    val allowCut: Boolean = true,
    val allowCopy: Boolean = true,
)

/**
 * The paste override a composer installs for its input (Dart's
 * `Actions(PasteTextIntent: ...)`): receives the field's default text paste
 * and decides whether to run it.
 */
val LocalPasteTextAction = compositionLocalOf<((defaultPaste: () -> Unit) -> Unit)?> { null }

/** What the context menu needs from the field it belongs to. */
internal class TextContextMenuField(
    val controller: TextEditingController,
    /** Inserts the clipboard text at the selection as a user edit. */
    val pasteClipboardText: () -> Unit,
    /** Copies the selected text to the clipboard. */
    val copySelection: () -> Unit,
)

internal class _MenuSession(val dataProvider: TextContextMenuDataProvider) : TextContextMenuSession {
    private val closed = CompletableDeferred<Unit>()

    override fun close() {
        closed.complete(Unit)
    }

    suspend fun awaitClose() = closed.await()
}

/** Shows the text field's toolbar/right-click menu as the kit's panel. */
internal class _UiTextContextMenuProvider : TextContextMenuProvider {
    var session by mutableStateOf<_MenuSession?>(null)

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        val newSession = _MenuSession(dataProvider)
        session = newSession
        _openSessions.add(newSession)
        try {
            newSession.awaitClose()
        } finally {
            _openSessions.remove(newSession)
            if (session === newSession) session = null
        }
    }
}

private val _openSessions = LinkedHashSet<_MenuSession>()

/** Flutter's `ContextMenuController.removeAny()`: closes any open text context menu. */
fun removeAnyTextContextMenu() {
    for (session in _openSessions.toList()) session.close()
}

/**
 * Installs the kit's text context menu (Dart's `contextMenuBuilder:
 * buildTextFieldContextMenu`) for the text field in [content], as the Android
 * selection toolbar: anchored above the selection (below when there is no
 * room), inside the safe area.
 */
@Composable
internal fun TextFieldContextMenuScope(
    menu: TextFieldContextMenu?,
    field: TextContextMenuField,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val provider = remember { _UiTextContextMenuProvider() }
    val hostCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val pasteAction = LocalPasteTextAction.current
    CompositionLocalProvider(
        LocalTextContextMenuToolbarProvider provides provider,
        LocalTextContextMenuDropdownProvider provides provider,
    ) {
        Box(modifier.onGloballyPositioned { hostCoordinates[0] = it }, propagateMinConstraints = true) {
            content()
            val session = provider.session
            if (session != null && menu != null) {
                androidx.compose.runtime.key(session) {
                    _TextFieldContextMenuPopup(session, menu, field, pasteAction) { hostCoordinates[0] }
                }
            }
        }
    }
}

@Composable
private fun _TextFieldContextMenuPopup(
    session: _MenuSession,
    menu: TextFieldContextMenu,
    field: TextContextMenuField,
    pasteAction: ((defaultPaste: () -> Unit) -> Unit)?,
    hostCoordinates: () -> LayoutCoordinates?,
) {
    val density = LocalDensity.current
    val paddingTop = LocalMediaQuery.current.padding.top
    val host = hostCoordinates()
    if (host == null || !host.isAttached) return
    val bounds = session.dataProvider.contentBounds(host)
    val topLeft = host.localToWindow(bounds.topLeft)
    val bottomRight = host.localToWindow(bounds.bottomRight)
    val centerX = (topLeft.x + bottomRight.x) / 2f
    val provider =
        _TextSelectionToolbarPositionProvider(
            primaryAnchor = Offset(centerX, topLeft.y),
            secondaryAnchor = Offset(centerX, bottomRight.y),
            screenPaddingPx = with(density) { _contextMenuScreenPadding.toPx() },
            paddingAbovePx = with(density) { (paddingTop + _contextMenuScreenPadding).toPx() },
            gapAbovePx = with(density) { 8.dp.toPx() },
            distanceBelowPx = with(density) { _kToolbarContentDistanceBelow.toPx() },
        )
    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        _TextFieldContextMenuPanel(session, menu, field, pasteAction)
    }
}

/** Flutter's `TextSelectionToolbarLayoutDelegate` inside the screen padding. */
private class _TextSelectionToolbarPositionProvider(
    val primaryAnchor: Offset,
    val secondaryAnchor: Offset,
    val screenPaddingPx: Float,
    val paddingAbovePx: Float,
    val gapAbovePx: Float,
    val distanceBelowPx: Float,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val areaWidth = windowSize.width - screenPaddingPx * 2
        val anchorAbove = Offset(primaryAnchor.x - screenPaddingPx, primaryAnchor.y - gapAbovePx - paddingAbovePx)
        val anchorBelow = Offset(secondaryAnchor.x - screenPaddingPx, secondaryAnchor.y + distanceBelowPx - paddingAbovePx)
        val childWidth = popupContentSize.width.toFloat()
        val childHeight = popupContentSize.height.toFloat()
        val fitsAbove = anchorAbove.y >= childHeight
        val anchor = if (fitsAbove) anchorAbove else anchorBelow
        val x =
            when {
                anchor.x - childWidth / 2f < 0f -> 0f
                anchor.x + childWidth / 2f > areaWidth -> areaWidth - childWidth
                else -> anchor.x - childWidth / 2f
            }
        val y = if (fitsAbove) max(0f, anchor.y - childHeight) else anchor.y
        return IntOffset((x + screenPaddingPx).toInt(), (y + paddingAbovePx).toInt())
    }
}

private class _TextFieldContextMenuWidget(
    val session: _MenuSession,
    val menu: TextFieldContextMenu,
    val field: TextContextMenuField,
    val pasteAction: ((defaultPaste: () -> Unit) -> Unit)?,
)

@Composable
private fun _TextFieldContextMenuPanel(
    session: _MenuSession,
    menu: TextFieldContextMenu,
    field: TextContextMenuField,
    pasteAction: ((defaultPaste: () -> Unit) -> Unit)?,
) = StatefulWidget(_TextFieldContextMenuWidget(session, menu, field, pasteAction)) { _TextFieldContextMenuState() }

private class _TextFieldContextMenuState : ViewState<_TextFieldContextMenuWidget>() {
    private var _openNotified = false
    private var _canPasteNonText = false
    private var _canPasteNonTextGeneration = 0
    private var scope: CoroutineScope? = null

    private val _undoController: UndoHistoryController? get() = widget.menu.undoController

    private val _handleMenuStateChanged: () -> Unit = {
        if (mounted) setState()
        _refreshCanPasteNonText()
    }

    override fun initState() {
        _undoController?.addListener(_handleMenuStateChanged)
    }

    override fun didUpdateWidget(oldWidget: _TextFieldContextMenuWidget) {
        if (oldWidget.menu.undoController !== _undoController) {
            oldWidget.menu.undoController?.removeListener(_handleMenuStateChanged)
            _undoController?.addListener(_handleMenuStateChanged)
        }
        if (oldWidget.menu.canPasteNonText !== widget.menu.canPasteNonText) _refreshCanPasteNonText()
    }

    override fun dispose() {
        if (_openNotified) {
            val onOpenChanged = widget.menu.onOpenChanged
            // Flutter reports the close after the frame.
            if (onOpenChanged != null) android.os.Handler(android.os.Looper.getMainLooper()).post { onOpenChanged(false) }
        }
        _undoController?.removeListener(_handleMenuStateChanged)
        _canPasteNonTextGeneration++
    }

    private fun _refreshCanPasteNonText() {
        val checker = widget.menu.canPasteNonText
        val generation = ++_canPasteNonTextGeneration
        if (checker == null) {
            if (_canPasteNonText && mounted) setState { _canPasteNonText = false }
            return
        }
        val scope = scope ?: return
        scope.launch {
            val value =
                try {
                    checker()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    if (!mounted || generation != _canPasteNonTextGeneration) return@launch
                    if (!_canPasteNonText) return@launch
                    setState { _canPasteNonText = false }
                    return@launch
                }
            if (!mounted || generation != _canPasteNonTextGeneration) return@launch
            if (_canPasteNonText == value) return@launch
            setState { _canPasteNonText = value }
        }
    }

    @Composable
    override fun build() {
        val newScope = rememberCoroutineScope()
        if (scope == null) {
            scope = newScope
            _refreshCanPasteNonText()
        }
        LaunchedEffect(Unit) {
            // Flutter notifies after the first frame.
            if (!mounted || _openNotified) return@LaunchedEffect
            _openNotified = true
            widget.menu.onOpenChanged?.invoke(true)
        }
        // Rebuild when the field's text or selection changes.
        listen(widget.field.controller)
        val data = widget.session.dataProvider.data()
        val sections = _sectionsFor(data)
        if (sections.isEmpty()) return

        val view = LocalView.current
        DisposableEffect(Unit) {
            _ContextMenuPanelBounds.hostView = view
            onDispose {
                if (_ContextMenuPanelBounds.hostView === view) {
                    _ContextMenuPanelBounds.hostView = null
                    _ContextMenuPanelBounds.coordinates = null
                }
            }
        }
        _ContextMenuPanel(
            sections = sections,
            modifier =
                Modifier
                    .onGloballyPositioned { _ContextMenuPanelBounds.coordinates = it }
                    .testTag(textContextMenuPanelTag),
        )
    }

    private fun TextContextMenuItem.action(): () -> Unit = { onClick(widget.session) }

    private fun _sectionsFor(data: TextContextMenuData): List<_ContextMenuSection> {
        val platformActions = LinkedHashMap<Any, () -> Unit>()
        for (component in data.components) {
            if (component is TextContextMenuItem) platformActions[component.key] = component.action()
        }
        val field = widget.field
        return textContextMenuSections(
            menu = widget.menu,
            value = field.controller.value,
            platformActions = platformActions,
            canPasteNonText = _canPasteNonText,
            pasteIntent = { _pasteIntentAction(platformActions[TextContextMenuKeys.PasteKey]) },
            selectAll = { field.controller.selection = TextRange(0, field.controller.text.length) },
            copySelection = field.copySelection,
        ).map { section -> _ContextMenuSection(section.map { _entry(it.label, it.kind, it.action) }) }
    }

    /** Dart's `Actions.maybeInvoke(context, PasteTextIntent)`. */
    private fun _pasteIntentAction(defaultItemAction: (() -> Unit)?) {
        val defaultPaste: () -> Unit = defaultItemAction ?: widget.field.pasteClipboardText
        val override = widget.pasteAction
        if (override != null) override(defaultPaste) else defaultPaste()
    }

    private fun _entry(label: String, shortcut: _ShortcutKind, action: (() -> Unit)?): _ContextMenuItemData {
        val keepAndroidSelectionMenu = shortcut == _ShortcutKind.selectAll
        return _ContextMenuItemData(
            label = label,
            shortcut = _shortcutLabel(shortcut),
            onPressed =
                if (action == null) {
                    null
                } else {
                    {
                        widget.menu.onActionPressed?.invoke()
                        action()
                        if (!keepAndroidSelectionMenu) widget.session.close()
                    }
                },
        )
    }
}

/** One entry of the text context menu, before it is bound to the open menu. */
internal class TextContextMenuEntry(val label: String, val kind: _ShortcutKind, val action: (() -> Unit)?) {
    val shortcut: String get() = _shortcutLabel(kind)
}

/**
 * Dart's `_sectionsFor` / `_readOnlySectionsFor`: the entries the kit's text
 * menu shows for [value], given the actions Compose offers for the field
 * ([platformActions], keyed by `TextContextMenuKeys`: cut/copy/paste only
 * when possible, paste only with text on the clipboard) and whether the
 * non-text paste probe succeeded.
 */
internal fun textContextMenuSections(
    menu: TextFieldContextMenu,
    value: TextFieldValue,
    platformActions: Map<Any, () -> Unit>,
    canPasteNonText: Boolean,
    pasteIntent: () -> Unit,
    selectAll: () -> Unit,
    copySelection: () -> Unit,
): List<List<TextContextMenuEntry>> {
    val selected = !value.selection.collapsed
    val selectAllFallback: (() -> Unit)? =
        run {
            if (value.text.isEmpty()) return@run null
            if (value.selection.min == 0 && value.selection.max == value.text.length) return@run null
            selectAll
        }
    if (menu.readOnly) {
        val editItems = mutableListOf<TextContextMenuEntry>()
        if (selected) {
            editItems.add(TextContextMenuEntry("复制", _ShortcutKind.copy, platformActions[TextContextMenuKeys.CopyKey] ?: copySelection))
        }
        if (menu.showReadOnlySelectAll) {
            val selectAllAction = platformActions[TextContextMenuKeys.SelectAllKey] ?: selectAllFallback
            if (selectAllAction != null) editItems.add(TextContextMenuEntry("全选", _ShortcutKind.selectAll, selectAllAction))
        }
        return if (editItems.isNotEmpty()) listOf(editItems) else emptyList()
    }

    val editItems = mutableListOf<TextContextMenuEntry>()
    val pasteItem = platformActions[TextContextMenuKeys.PasteKey]
    // Compose only offers paste when the clipboard holds text (Flutter's
    // `ClipboardStatus.pasteable`).
    val canPasteText = pasteItem != null
    val canPaste = canPasteText || (canPasteNonText && menu.canPasteNonText != null)
    // Always route editable composer paste through the paste action when it
    // provides a non-text checker. On Android the system menu can be tapped
    // before the asynchronous clipboard probe finishes; invoking the native
    // paste button directly in that window inserts the readable fallback text
    // and bypasses structured song/playlist restoration. The composer action
    // falls back to the normal text paste when nothing was consumed.
    val pasteAction: (() -> Unit)? = if (menu.canPasteNonText != null) pasteIntent else pasteItem
    val selectAllAction = platformActions[TextContextMenuKeys.SelectAllKey] ?: selectAllFallback

    if (selected) {
        if (menu.allowCut) editItems.add(TextContextMenuEntry("剪切", _ShortcutKind.cut, platformActions[TextContextMenuKeys.CutKey]))
        if (menu.allowCopy) editItems.add(TextContextMenuEntry("复制", _ShortcutKind.copy, platformActions[TextContextMenuKeys.CopyKey]))
        if (canPaste) editItems.add(TextContextMenuEntry("粘贴", _ShortcutKind.paste, pasteAction))
        if (selectAllAction != null) editItems.add(TextContextMenuEntry("全选", _ShortcutKind.selectAll, selectAllAction))
    } else {
        if (canPaste) editItems.add(TextContextMenuEntry("粘贴", _ShortcutKind.paste, pasteAction))
        if (selectAllAction != null) editItems.add(TextContextMenuEntry("全选", _ShortcutKind.selectAll, selectAllAction))
    }

    val historyItems = mutableListOf<TextContextMenuEntry>()
    val undoController = menu.undoController
    if (undoController != null) {
        if (undoController.value.canUndo) historyItems.add(TextContextMenuEntry("撤销", _ShortcutKind.undo) { undoController.undo() })
        if (undoController.value.canRedo) historyItems.add(TextContextMenuEntry("重做", _ShortcutKind.redo) { undoController.redo() })
    }

    return buildList {
        if (editItems.isNotEmpty()) add(editItems)
        if (historyItems.isNotEmpty()) add(historyItems)
    }
}

@Composable
private fun _ContextMenuPanel(sections: List<_ContextMenuSection>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(UiRadii.lg - 2.dp)
    Box(
        modifier
            .width(_contextMenuWidth)
            .boxShadow(UiShadows.popover, shape)
            .decoration(UiColors.surfaceRaised, shape, UiColors.borderStrong)
            .clip(shape)
            .padding(5.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            for ((sectionIndex, section) in sections.withIndex()) {
                if (sectionIndex > 0) _ContextMenuDivider()
                for (item in section.items) _ContextMenuItem(item)
            }
        }
    }
}

@Composable
private fun _ContextMenuItem(data: _ContextMenuItemData) {
    val enabled = data.onPressed != null
    val foreground = if (enabled) UiColors.text else UiColors.textMuted
    val shortcutColor = if (enabled) UiColors.textSecondary else UiColors.textMuted.copy(alpha = 0.68f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadii.sm))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = { data.onPressed?.invoke() },
            )
            .heightIn(min = _contextMenuMinItemHeight)
            .padding(horizontal = _contextMenuHorizontalPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            data.label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = UiTypography.body.copyWith(color = foreground, fontSize = 13.0, height = 1.2),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            data.shortcut,
            maxLines = 1,
            style = UiTypography.label.copyWith(color = shortcutColor, fontSize = 12.0, height = 1.2),
        )
    }
}

@Composable
private fun _ContextMenuDivider() {
    Box(
        Modifier
            .testTag(textContextMenuDividerTag)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(1.dp)
            .decoration(UiColors.borderStrong),
    )
}

private class _ContextMenuSection(val items: List<_ContextMenuItemData>)

private class _ContextMenuItemData(val label: String, val shortcut: String, val onPressed: (() -> Unit)?)

internal enum class _ShortcutKind { cut, copy, paste, selectAll, undo, redo }

private fun _shortcutLabel(shortcut: _ShortcutKind): String =
    when (shortcut) {
        _ShortcutKind.cut -> "Ctrl+X"
        _ShortcutKind.copy -> "Ctrl+C"
        _ShortcutKind.paste -> "Ctrl+V"
        _ShortcutKind.selectAll -> "Ctrl+A"
        _ShortcutKind.undo -> "Ctrl+Z"
        _ShortcutKind.redo -> "Ctrl+Y"
    }
