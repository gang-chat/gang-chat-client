package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.CompositionTimer
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.isTouchLike
import com.gangchat.client.widgets.kLongPressTimeout
import com.gangchat.client.widgets.kTouchSlop
import com.gangchat.client.widgets.startTimer
import kotlinx.coroutines.CoroutineScope

/**
 * A primary-button tap target that observes pointer events without competing
 * with ancestors' gestures.
 *
 * This is useful for navigation cards embedded in scrollable/selectable
 * surfaces where an ancestor recognizer may consume the first ordinary tap.
 * Touch drags, long presses, secondary clicks, and multi-touch are rejected so
 * scrolling and context-menu gestures keep their normal behavior.
 */
@Composable
fun UiPointerTapRegion(
    onTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
    disableSelection: Boolean = false,
    child: @Composable () -> Unit,
) = StatefulWidget(UiPointerTapRegionWidget(child, onTap, disableSelection, modifier)) { _UiPointerTapRegionState() }

class UiPointerTapRegionWidget(
    val child: @Composable () -> Unit,
    val onTap: (() -> Unit)?,
    val disableSelection: Boolean,
    val modifier: Modifier,
)

private class _UiPointerTapRegionState : ViewState<UiPointerTapRegionWidget>() {
    private var _pointer: PointerId? = null
    private var _origin: Offset? = null
    private var _longPressTimer: CompositionTimer? = null
    private var _touchLike = false
    private var _cancelled = false
    private var scope: CoroutineScope? = null

    private fun _handlePointerDown(id: PointerId, position: Offset, type: PointerType, primary: Boolean) {
        if (widget.onTap == null || !primary) return
        if (_pointer != null) {
            _clearTracking()
            return
        }
        _pointer = id
        _origin = position
        _touchLike = type.isTouchLike()
        _cancelled = false
        if (_touchLike) {
            _longPressTimer = scope?.startTimer(kLongPressTimeout) { if (_pointer == id) _cancelled = true }
        }
    }

    private fun _handlePointerMove(id: PointerId, position: Offset, slopPx: Float) {
        if (id != _pointer || !_touchLike) return
        val origin = _origin
        if (origin != null && (position - origin).getDistanceSquared() > slopPx * slopPx) _cancelled = true
    }

    private fun _handlePointerUp(id: PointerId, inside: Boolean) {
        if (id != _pointer) return
        val shouldTap = !_cancelled && inside
        _clearTracking()
        if (shouldTap) widget.onTap?.invoke()
    }

    private fun _handlePointerCancel(id: PointerId) {
        if (id == _pointer) _clearTracking()
    }

    private fun _clearTracking() {
        _longPressTimer?.cancel()
        _longPressTimer = null
        _pointer = null
        _origin = null
        _touchLike = false
        _cancelled = false
    }

    override fun didUpdateWidget(oldWidget: UiPointerTapRegionWidget) {
        if (oldWidget.onTap != null && widget.onTap == null) _clearTracking()
    }

    override fun dispose() {
        _longPressTimer?.cancel()
    }

    @Composable
    override fun build() {
        scope = rememberCoroutineScope()
        val result: @Composable () -> Unit = {
            Box(
                widget.modifier
                    .semantics {
                        role = Role.Button
                        if (widget.onTap == null) disabled()
                        widget.onTap?.let { tap ->
                            onClick {
                                tap()
                                true
                            }
                        }
                    }
                    .pointerInput(this) {
                        val slopPx = kTouchSlop.dp.toPx()
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                when (event.type) {
                                    PointerEventType.Press ->
                                        for (change in event.changes) {
                                            if (!change.pressed || change.previousPressed) continue
                                            val primary = change.type != PointerType.Mouse || event.buttons.isPrimaryPressed
                                            _handlePointerDown(change.id, change.position, change.type, primary)
                                        }
                                    PointerEventType.Move -> for (change in event.changes) _handlePointerMove(change.id, change.position, slopPx)
                                    PointerEventType.Release ->
                                        for (change in event.changes) {
                                            if (change.pressed || !change.previousPressed) continue
                                            if (change.isConsumed) {
                                                _handlePointerCancel(change.id)
                                            } else {
                                                val p = change.position
                                                _handlePointerUp(change.id, p.x >= 0 && p.y >= 0 && p.x <= size.width && p.y <= size.height)
                                            }
                                        }
                                    else -> Unit
                                }
                            }
                        }
                    },
                propagateMinConstraints = true,
            ) {
                widget.child()
            }
        }
        if (widget.disableSelection) DisableSelection { result() } else result()
    }
}

/**
 * Observes an Android touch/stylus hold without joining the gesture arena.
 *
 * This is intentionally a pointer-event helper instead of a recognizer. It
 * lets an outer application context-menu target observe a hold even when an
 * inner editable text control owns the native long-press gesture.
 *
 * Positions are window coordinates in dp. The hold timer runs on [scope]
 * (use the composition's `rememberCoroutineScope()`).
 */
class UiAndroidLongPressTracker(
    val onLongPress: (DpOffset) -> Unit,
    private val scope: CoroutineScope,
) {
    private var _timer: CompositionTimer? = null
    private var _pointer: PointerId? = null
    private var _origin: DpOffset? = null

    fun handlePointerDown(pointer: PointerId, position: DpOffset, kind: PointerType, enabled: Boolean) {
        if (_pointer != null) {
            // A second contact means a multi-touch gesture, never a context-menu
            // hold. Do not transfer tracking to the newer pointer.
            cancel()
            return
        }
        cancel()
        if (!enabled || !kind.isTouchLike()) return
        _pointer = pointer
        _origin = position
        _timer =
            scope.startTimer(kLongPressTimeout) {
                val origin = _origin
                if (_pointer == null || origin == null) return@startTimer
                _timer = null
                onLongPress(origin)
            }
    }

    fun handlePointerMove(pointer: PointerId, position: DpOffset) {
        if (pointer != _pointer) return
        val origin = _origin ?: return
        val dx = (position.x - origin.x).value
        val dy = (position.y - origin.y).value
        if (kotlin.math.sqrt(dx * dx + dy * dy) <= kTouchSlop) return
        cancel()
    }

    fun handlePointerUp(pointer: PointerId) {
        if (pointer == _pointer) cancel()
    }

    fun handlePointerCancel(pointer: PointerId) {
        if (pointer == _pointer) cancel()
    }

    fun cancel() {
        _timer?.cancel()
        _timer = null
        _pointer = null
        _origin = null
    }
}

/** Converts a pointer position (px, window) to window dp. */
internal fun Density.toWindowDp(position: Offset): DpOffset = DpOffset(position.x.toDp(), position.y.toDp())
