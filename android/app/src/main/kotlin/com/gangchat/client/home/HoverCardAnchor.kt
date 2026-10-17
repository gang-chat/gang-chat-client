package com.gangchat.client.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.ui.AnchoredPanel
import com.gangchat.client.ui.ReadOnlySelectableText
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.CompositionTimer
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.LocalTapRegionRegistry
import com.gangchat.client.widgets.MediaQuery
import com.gangchat.client.widgets.OverlayPortal
import com.gangchat.client.widgets.OverlayPortalController
import com.gangchat.client.widgets.PointerDownEvent
import com.gangchat.client.widgets.PointerRouteEvent
import com.gangchat.client.widgets.RenderBoxHandle
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.TapRegionRegistry
import com.gangchat.client.widgets.TextPainter
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.kTouchSlop
import com.gangchat.client.widgets.rememberBuildContext
import com.gangchat.client.widgets.renderBox
import com.gangchat.client.widgets.startTimer
import com.gangchat.client.widgets.tapRegion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

val hoverCardDefaultWidth: Dp = 248.dp
val hoverCardDefaultGap: Dp = 10.dp

typealias HoverCardBuilder = @Composable (context: BuildContext) -> Unit

/** The system insets a card must stay inside: Android's view padding (Dart: zero on desktop). */
fun hoverCardOverlaySafeInsets(context: BuildContext): EdgeInsets = MediaQuery.viewPaddingOf(context)

fun hoverInfoCardWidth(
    context: BuildContext,
    message: String,
    style: TextStyle? = null,
    horizontalPadding: Dp = 24.dp,
    minWidth: Dp = 132.dp,
    maxWidth: Dp = 360.dp,
): Dp {
    val editableTextLayoutSlack = 18.dp
    val textStyle = style ?: UiTypography.body.copyWith(fontSize = 12.0, height = 1.38)
    val lines = message.trimEnd().split('\n')
    var longestLineWidth = 0.0
    for (line in lines) {
        val painter = TextPainter(if (line.isEmpty()) " " else line, textStyle, maxLines = 1).layout(context)
        longestLineWidth = max(longestLineWidth, painter.width)
    }
    return (longestLineWidth.dp + horizontalPadding + editableTextLayoutSlack).coerceIn(minWidth, maxWidth)
}

/** Dart's `HoverCardTapRegionScope` inherited data. */
class HoverCardTapRegionScopeData(val tapRegionGroup: Any, val onOverlayActivityChanged: ((Boolean) -> Unit)? = null)

private val LocalHoverCardTapRegionScope = compositionLocalOf<HoverCardTapRegionScopeData?> { null }

@Composable
fun HoverCardTapRegionScope(
    tapRegionGroup: Any,
    onOverlayActivityChanged: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalHoverCardTapRegionScope provides HoverCardTapRegionScopeData(tapRegionGroup, onOverlayActivityChanged),
        content = content,
    )
}

object HoverCardTapRegionScope {
    fun maybeOf(context: BuildContext): HoverCardTapRegionScopeData? = context[LocalHoverCardTapRegionScope]

    val current: HoverCardTapRegionScopeData? @Composable get() = LocalHoverCardTapRegionScope.current
}

/**
 * Selectable identity/detail text that keeps its owning hover card open while
 * the shared text context menu or Android selection handles are active.
 *
 * User, room, and song cards use the same wrapper so context-menu copy and
 * touch selection cannot drift between card types. [copyStartOffset] only
 * drove Dart's desktop right-click selection, which is not ported.
 */
@Composable
fun HoverCardSelectableText(
    value: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") copyStartOffset: Int = 0,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Start,
) {
    val hoverScope = LocalHoverCardTapRegionScope.current
    ReadOnlySelectableText(
        value = value,
        style = style,
        modifier = modifier,
        maxLines = maxLines,
        textAlign = textAlign,
        showSelectAllInContextMenu = false,
        contextMenuTapRegionGroupId = hoverScope?.tapRegionGroup,
        onContextMenuOpenChanged = hoverScope?.onOverlayActivityChanged,
    )
}

/**
 * Shared hover/tap shell for small profile cards anchored to an avatar.
 *
 * On Android a tap pins the card open (a second tap or an outside tap closes
 * it) and a hold does nothing (it is reserved for context menus); a mouse
 * hover still opens it like the desktop build. Nested anchors inside a card
 * form a chain: opening one closes its siblings, tapping an earlier card
 * closes the later ones.
 */
@Composable
fun HoverCardAnchor(
    cardBuilder: HoverCardBuilder,
    modifier: Modifier = Modifier,
    onBeforeOpen: (suspend () -> Unit)? = null,
    onVisibilityChanged: ((Boolean) -> Unit)? = null,
    resetKey: Any? = null,
    cardWidth: Dp = hoverCardDefaultWidth,
    gap: Dp = hoverCardDefaultGap,
    closeDelay: Duration = 120.milliseconds,
    child: @Composable () -> Unit,
) = StatefulWidget(
    HoverCardAnchorWidget(child, cardBuilder, onBeforeOpen, onVisibilityChanged, resetKey, cardWidth, gap, closeDelay, modifier),
) { _HoverCardAnchorState() }

class HoverCardAnchorWidget(
    val child: @Composable () -> Unit,
    val cardBuilder: HoverCardBuilder,
    val onBeforeOpen: (suspend () -> Unit)?,
    val onVisibilityChanged: ((Boolean) -> Unit)?,
    val resetKey: Any?,
    val cardWidth: Dp,
    val gap: Dp,
    val closeDelay: Duration,
    val modifier: Modifier,
)

private class _HoverCardChainScopeData(
    val anchor: _HoverCardAnchorState,
    val coordinator: _HoverCardCoordinator,
    val tapRegionGroup: Any,
)

private val _LocalHoverCardChainScope = compositionLocalOf<_HoverCardChainScopeData?> { null }

private fun DpRect.containsPoint(point: DpOffset): Boolean =
    point.x >= left && point.x < right && point.y >= top && point.y < bottom

private class _HoverCardAnchorState : ViewState<HoverCardAnchorWidget>() {
    private val _anchorKey = RenderBoxHandle()
    private val _cardKey = RenderBoxHandle()
    private val _portal = OverlayPortalController()
    private val _rootTapRegionGroup = Any()
    private val _overlayActivityToken = Any()

    private val _coordinator = _HoverCardCoordinator(::_handleDescendantActivityChanged)
    private var _parentCoordinator: _HoverCardCoordinator? = null
    private var _parentAnchor: _HoverCardAnchorState? = null
    private var _inheritedTapRegionGroup: Any? = null
    private var _overAnchor = false
    private var _overCard = false
    private var _pinned = false
    private var _portalVisible = false
    private var _advertisedActiveToParent = false
    private var _deferredOutsidePointer: Long? = null
    private var _deferredOutsideStart: DpOffset? = null
    private var _deferredOutsideMoved = false
    private var _deferredRegistry: TapRegionRegistry? = null
    private var _closeTimer: CompositionTimer? = null
    private var _openFuture: Any? = null
    private var _scope: CoroutineScope? = null
    private var _inBuild = false
    private val _pendingPortalMutations = mutableListOf<() -> Unit>()
    private val _deferredRoute: (PointerRouteEvent) -> Unit = { event -> _handleDeferredOutsidePointer(event) }

    private val _tapRegionGroup: Any get() = _inheritedTapRegionGroup ?: _rootTapRegionGroup

    private val _wantsOpen: Boolean get() = _pinned || _overAnchor || _overCard || _coordinator.hasActiveDescendants

    private val _keepsParentOpen: Boolean get() = _wantsOpen || _portalVisible || _openFuture != null

    // Dart's didChangeDependencies: the enclosing card chain and tap-region scope.
    private fun _syncDependencies() {
        val chainScope = context[_LocalHoverCardChainScope]
        val nextParent = chainScope?.coordinator
        if (nextParent !== _parentCoordinator) {
            if (_advertisedActiveToParent) {
                _parentCoordinator?.setDescendantActive(this, false)
                _advertisedActiveToParent = false
            }
            _parentCoordinator?.releaseOpenChild(this)
            _parentCoordinator = nextParent
        }
        _parentAnchor = chainScope?.anchor
        val tapRegionScope = context[LocalHoverCardTapRegionScope]
        _inheritedTapRegionGroup = chainScope?.tapRegionGroup ?: tapRegionScope?.tapRegionGroup
        _syncParentActivity()
    }

    override fun didUpdateWidget(oldWidget: HoverCardAnchorWidget) {
        _inBuild = true
        if (oldWidget.resetKey == widget.resetKey) return
        _dismiss()
    }

    override fun dispose() {
        if (_advertisedActiveToParent) {
            _parentCoordinator?.setDescendantActive(this, false)
            _advertisedActiveToParent = false
        }
        _parentCoordinator?.releaseOpenChild(this)
        _clearDeferredOutsidePointerRoute()
        _closeTimer?.cancel()
        if (_portalVisible) widget.onVisibilityChanged?.invoke(false)
    }

    fun _enterAnchor() {
        _overAnchor = true
        _open()
        _syncParentActivity()
    }

    fun _exitAnchor() {
        _overAnchor = false
        _syncParentActivity()
        _scheduleClose()
    }

    fun _enterCard() {
        _overCard = true
        _closeTimer?.cancel()
        _syncParentActivity()
    }

    fun _exitCard() {
        _overCard = false
        _syncParentActivity()
        _scheduleClose()
    }

    private fun _open() {
        _closeTimer?.cancel()
        val beforeOpen = widget.onBeforeOpen
        if (beforeOpen == null) {
            _showPortal()
            return
        }
        if (_openFuture != null) return

        val future = Any()
        _openFuture = future
        _syncParentActivity()
        unawaited {
            try {
                beforeOpen()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
            if (!mounted || _openFuture !== future) return@unawaited
            _openFuture = null
            if (_wantsOpen) _showPortal()
            _syncParentActivity()
        }
    }

    private fun _showPortal() {
        _parentCoordinator?.showOnlyChild(this)
        if (_portalVisible && _portal.isShowing) return
        _setPortalVisible(true)
        _showPortalController()
        _syncParentActivity()
    }

    fun _pinOpen() {
        if (_pinned) {
            _pinned = false
            _closeTimer?.cancel()
            if (_overAnchor || _overCard) {
                _syncParentActivity()
            } else {
                _hidePortal()
            }
            return
        }
        _pinned = true
        _open()
        _syncParentActivity()
    }

    private fun _hidePortal() {
        _coordinator.dismissOpenChild()
        _setPortalVisible(false)
        _parentCoordinator?.releaseOpenChild(this)
        _hidePortalController()
        _syncParentActivity()
    }

    private fun _showPortalController() {
        _mutatePortalWhenAllowed {
            if (!mounted || !_portalVisible || _portal.isShowing) return@_mutatePortalWhenAllowed
            _portal.show()
        }
    }

    private fun _hidePortalController() {
        _mutatePortalWhenAllowed {
            if (!mounted || _portalVisible || !_portal.isShowing) return@_mutatePortalWhenAllowed
            _portal.hide()
        }
    }

    // Like Dart, the portal is not toggled in the middle of a build: such
    // mutations run once the composition has been applied.
    private fun _mutatePortalWhenAllowed(mutation: () -> Unit) {
        if (_inBuild) {
            _pendingPortalMutations.add(mutation)
            return
        }
        mutation()
    }

    fun _dismiss() {
        _pinned = false
        _overAnchor = false
        _overCard = false
        _closeTimer?.cancel()
        _hidePortal()
    }

    fun _dismissFromPeer() {
        _pinned = false
        _overAnchor = false
        _overCard = false
        _closeTimer?.cancel()
        _hidePortal()
    }

    private fun _scheduleClose() {
        if (_pinned) return
        _closeTimer?.cancel()
        _closeTimer =
            _scope?.startTimer(widget.closeDelay) {
                if (!mounted || _wantsOpen) return@startTimer
                _setPortalVisible(false)
                _hidePortalController()
                _syncParentActivity()
            }
    }

    private fun _setPortalVisible(visible: Boolean) {
        if (_portalVisible == visible) return
        _portalVisible = visible
        widget.onVisibilityChanged?.invoke(visible)
    }

    private fun _handleDescendantActivityChanged() {
        if (!mounted) return
        if (_coordinator.hasActiveDescendants) {
            _closeTimer?.cancel()
        } else {
            _scheduleClose()
        }
        _syncParentActivity()
    }

    fun _handleOverlayActivityChanged(active: Boolean) {
        _coordinator.setDescendantActive(_overlayActivityToken, active)
        if (active) {
            _closeTimer?.cancel()
        } else {
            _scheduleClose()
        }
        _syncParentActivity()
    }

    private fun _syncParentActivity() {
        val parent = _parentCoordinator ?: return
        val active = _keepsParentOpen
        if (active == _advertisedActiveToParent) return
        parent.setDescendantActive(this, active)
        _advertisedActiveToParent = active
    }

    fun _handleTapInside(event: PointerDownEvent) {
        if (_parentAnchor != null) return
        if (_openAnchorChainContains(event.position)) return

        val target = _deepestOpenCardAt(event.position)
        if (target == null) {
            if (_coordinator.hasActiveDescendants) return
            _dismiss()
            return
        }
        _dismissChainAfter(target)
    }

    fun _handleTapOutside(event: PointerDownEvent) {
        if (!_coordinator.hasActiveDescendants) {
            _dismiss()
            return
        }

        // Android text selection handles and the text toolbar live outside
        // the card (Compose shows them in their own windows). A pointer that
        // starts outside while they are active looks like an outside tap, so
        // wait for it to finish: a drag keeps the card mounted, while a
        // regular outside tap still dismisses the card with one interaction.
        _trackDeferredOutsidePointer(event)
    }

    private fun _trackDeferredOutsidePointer(event: PointerDownEvent) {
        _clearDeferredOutsidePointerRoute()
        val registry = context[LocalTapRegionRegistry] ?: run {
            _dismiss()
            return
        }
        _deferredOutsidePointer = event.pointer
        _deferredOutsideStart = event.position
        _deferredOutsideMoved = false
        _deferredRegistry = registry
        registry.addPointerRoute(event.pointer, _deferredRoute)
    }

    private fun _handleDeferredOutsidePointer(event: PointerRouteEvent) {
        val pointer = _deferredOutsidePointer
        if (pointer == null || event.pointer != pointer) return
        if (!event.isUp) {
            val start = _deferredOutsideStart
            if (start != null) {
                val dx = (event.position.x - start.x).value
                val dy = (event.position.y - start.y).value
                if (sqrt(dx * dx + dy * dy) >= kTouchSlop) _deferredOutsideMoved = true
            }
            return
        }

        val keepOpen = _deferredOutsideMoved
        _clearDeferredOutsidePointerRoute()
        if (!keepOpen && mounted && _portalVisible) _dismiss()
    }

    private fun _clearDeferredOutsidePointerRoute() {
        val pointer = _deferredOutsidePointer
        if (pointer != null) _deferredRegistry?.removePointerRoute(pointer, _deferredRoute)
        _deferredOutsidePointer = null
        _deferredOutsideStart = null
        _deferredOutsideMoved = false
        _deferredRegistry = null
    }

    fun _dismissChainAfter(target: _HoverCardAnchorState) {
        if (this === target) {
            _coordinator.dismissOpenChild()
            return
        }
        val child = _coordinator.openChild ?: return
        child._dismissChainAfter(target)
    }

    fun _deepestOpenCardAt(globalPosition: DpOffset): _HoverCardAnchorState? {
        val child = _coordinator.openChild?._deepestOpenCardAt(globalPosition)
        if (child != null) return child
        if (_portalVisible && _cardRectInGlobal()?.containsPoint(globalPosition) == true) return this
        return null
    }

    fun _openAnchorChainContains(globalPosition: DpOffset): Boolean {
        if (_anchorRectInGlobal()?.containsPoint(globalPosition) == true) return true
        return _coordinator.openChild?._openAnchorChainContains(globalPosition) ?: false
    }

    // The overlay fills the window, so overlay and window coordinates agree.
    private fun _anchorRectInOverlay(): DpRect? = _anchorRectInGlobal()

    private fun _anchorRectInGlobal(): DpRect? = if (_anchorKey.hasSize) _anchorKey.globalRect else null

    private fun _cardRectInGlobal(): DpRect? = if (_cardKey.hasSize) _cardKey.globalRect else null

    @Composable
    private fun _OverlayChild() {
        val overlayContext = rememberBuildContext()
        if (_anchorRectInOverlay() == null) return
        val safeInsets = hoverCardOverlaySafeInsets(overlayContext)
        val gap = widget.gap
        val cardWidth = widget.cardWidth
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Box(
                    Modifier
                        .tapRegion(groupId = _tapRegionGroup, onTapOutside = ::_handleTapOutside, onTapInside = ::_handleTapInside)
                        .mouseHover(onEnter = ::_enterCard, onExit = ::_exitCard),
                    propagateMinConstraints = true,
                ) {
                    AnchoredPanel(width = cardWidth, modifier = Modifier.renderBox(_cardKey)) {
                        CompositionLocalProvider(
                            _LocalHoverCardChainScope provides _HoverCardChainScopeData(this@_HoverCardAnchorState, _coordinator, _tapRegionGroup),
                            LocalHoverCardTapRegionScope provides HoverCardTapRegionScopeData(_tapRegionGroup, ::_handleOverlayActivityChanged),
                        ) {
                            widget.cardBuilder(rememberBuildContext())
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            // Read at layout time so the card follows the anchor's latest position.
            val anchorRect = _anchorRectInOverlay() ?: DpRect(0.dp, 0.dp, 0.dp, 0.dp)
            val size = androidx.compose.ui.unit.IntSize(constraints.maxWidth, constraints.maxHeight)
            val safeLeft = safeInsets.left.toPx()
            val safeTop = safeInsets.top.toPx()
            val safeRight = safeInsets.right.toPx()
            val safeBottom = safeInsets.bottom.toPx()
            val availableWidth = max(0f, size.width - safeLeft - safeRight)
            val availableHeight = max(0f, size.height - safeTop - safeBottom)
            val effectiveCardWidth = min(cardWidth.toPx(), availableWidth).roundToInt()
            val placeable =
                measurables.first().measure(
                    Constraints(minWidth = effectiveCardWidth, maxWidth = effectiveCardWidth, minHeight = 0, maxHeight = availableHeight.roundToInt()),
                )
            layout(size.width, size.height) {
                val childWidth = placeable.width.toFloat()
                val childHeight = placeable.height.toFloat()
                val minLeft = safeLeft.coerceIn(0f, size.width.toFloat())
                val safeRightEdge = max(minLeft, size.width - safeRight)
                val spaceRight = safeRightEdge - anchorRect.right.toPx() - gap.toPx()
                val placeRight = spaceRight >= childWidth
                val rawLeft = if (placeRight) anchorRect.right.toPx() + gap.toPx() else anchorRect.left.toPx() - gap.toPx() - childWidth
                val maxLeft = max(minLeft, safeRightEdge - childWidth)
                val left = rawLeft.coerceIn(minLeft, maxLeft)

                val minTop = safeTop.coerceIn(0f, size.height.toFloat())
                val safeBottomEdge = max(minTop, size.height - safeBottom)
                val maxTop = max(minTop, safeBottomEdge - childHeight)
                val top = anchorRect.top.toPx().coerceIn(minTop, maxTop)
                placeable.place(left.roundToInt(), top.roundToInt())
            }
        }
    }

    @Composable
    override fun build() {
        _inBuild = true
        _scope = rememberCoroutineScope()
        _syncDependencies()
        SideEffect {
            _inBuild = false
            if (_pendingPortalMutations.isNotEmpty()) {
                val mutations = _pendingPortalMutations.toList()
                _pendingPortalMutations.clear()
                for (mutation in mutations) mutation()
            }
        }
        OverlayPortal(controller = _portal, overlayChildBuilder = { _ -> _OverlayChild() }) {
            Box(
                widget.modifier
                    .tapRegion(groupId = _tapRegionGroup)
                    .pointerInput(Unit) {
                        // On Android a hold is reserved for the desktop-equivalent
                        // context menu. Registering it here also prevents a long
                        // hold from falling through to the tap-to-pin behavior
                        // when no context menu exists.
                        detectTapGestures(onTap = { _pinOpen() }, onLongPress = {})
                    }
                    .mouseHover(onEnter = ::_enterAnchor, onExit = ::_exitAnchor)
                    .renderBox(_anchorKey),
                propagateMinConstraints = true,
            ) {
                widget.child()
            }
        }
        _inBuild = false
    }
}

/** Flutter's `MouseRegion(onEnter:, onExit:)` for a mouse pointer. */
private fun Modifier.mouseHover(onEnter: () -> Unit, onExit: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.none { it.type == PointerType.Mouse }) continue
                when (event.type) {
                    PointerEventType.Enter -> onEnter()
                    PointerEventType.Exit -> onExit()
                    else -> Unit
                }
            }
        }
    }

private class _HoverCardCoordinator(val onChanged: () -> Unit) {
    private val _activeDescendants = LinkedHashSet<Any>()
    private var _openChild: _HoverCardAnchorState? = null

    val hasActiveDescendants: Boolean get() = _activeDescendants.isNotEmpty()
    val openChild: _HoverCardAnchorState? get() = _openChild

    fun setDescendantActive(token: Any, active: Boolean) {
        val changed = if (active) _activeDescendants.add(token) else _activeDescendants.remove(token)
        if (changed) onChanged()
    }

    fun showOnlyChild(child: _HoverCardAnchorState) {
        val previous = _openChild
        if (previous != null && previous !== child) previous._dismissFromPeer()
        _openChild = child
    }

    fun releaseOpenChild(child: _HoverCardAnchorState) {
        if (_openChild === child) _openChild = null
    }

    fun dismissOpenChild() {
        val child = _openChild ?: return
        _openChild = null
        child._dismissFromPeer()
    }
}
