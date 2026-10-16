package com.gangchat.client.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.TextFieldValue
import com.gangchat.client.foundation.ChangeNotifier
import com.gangchat.client.foundation.ValueNotifier
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope

/** Flutter's `UndoHistoryValue`. */
@Immutable
data class UndoHistoryValue(val canUndo: Boolean = false, val canRedo: Boolean = false) {
    companion object {
        val empty = UndoHistoryValue()
    }
}

/**
 * Flutter's `UndoHistoryController`: exposes whether undo/redo is possible
 * ([value]) and asks the attached [UndoHistory] to undo or redo. [onUndo] and
 * [onRedo] notify their listeners when [undo] / [redo] are requested, like
 * Flutter.
 */
class UndoHistoryController(value: UndoHistoryValue = UndoHistoryValue.empty) : ValueNotifier<UndoHistoryValue>(value) {
    class Signal : ChangeNotifier() {
        internal fun fire() = notifyListeners()
    }

    val onUndo = Signal()
    val onRedo = Signal()

    fun undo() {
        if (!value.canUndo) return
        onUndo.fire()
    }

    fun redo() {
        if (!value.canRedo) return
        onRedo.fire()
    }

    override fun dispose() {
        onUndo.dispose()
        onRedo.dispose()
        super.dispose()
    }
}

/**
 * Flutter's `UndoHistory` as `EditableText` installs it: records the text
 * field's committed values (throttled to 500 ms) while it has focus, and
 * restores them when [controller] asks to undo or redo.
 */
@Composable
fun UndoHistory(value: TextEditingController, focusNode: FocusNode?, controller: UndoHistoryController) {
    val state = remember { UndoHistoryState() }
    state.scope = rememberCoroutineScope()
    DisposableEffect(value, focusNode, controller) {
        state.attach(value, focusNode, controller)
        onDispose { state.detach() }
    }
}

private class UndoStack<T> {
    private val list = mutableListOf<T>()
    private var index = -1

    val currentValue: T? get() = if (list.isEmpty()) null else list[index]
    val canUndo: Boolean get() = list.isNotEmpty() && index > 0
    val canRedo: Boolean get() = list.isNotEmpty() && index < list.size - 1

    fun push(value: T) {
        if (list.isEmpty()) {
            index = 0
            list.add(value)
            return
        }
        if (value == currentValue) return
        // Anything undone is dropped before adding the new state.
        if (index != list.size - 1) {
            while (list.size > index + 1) list.removeAt(list.size - 1)
        }
        list.add(value)
        index = list.size - 1
    }

    fun undo(): T? {
        if (list.isEmpty()) return null
        if (index != 0) index -= 1
        return currentValue
    }

    fun redo(): T? {
        if (list.isEmpty()) return null
        if (index < list.size - 1) index += 1
        return currentValue
    }
}

private class UndoHistoryState {
    var scope: CoroutineScope? = null
    private var value: TextEditingController? = null
    private var focusNode: FocusNode? = null
    private var controller: UndoHistoryController? = null
    private val stack = UndoStack<TextFieldValue>()
    private var lastValue: TextFieldValue? = null
    private var ignoreChanges = false
    private var throttleTimer: CompositionTimer? = null
    private var pendingPush: TextFieldValue? = null

    private val onValueChanged: () -> Unit = { push() }
    private val onFocusChanged: () -> Unit = { handleFocus() }
    private val onUndo: () -> Unit = { undo() }
    private val onRedo: () -> Unit = { redo() }

    fun attach(value: TextEditingController, focusNode: FocusNode?, controller: UndoHistoryController) {
        this.value = value
        this.focusNode = focusNode
        this.controller = controller
        value.addListener(onValueChanged)
        focusNode?.addListener(onFocusChanged)
        controller.onUndo.addListener(onUndo)
        controller.onRedo.addListener(onRedo)
        handleFocus()
    }

    fun detach() {
        value?.removeListener(onValueChanged)
        focusNode?.removeListener(onFocusChanged)
        controller?.onUndo?.removeListener(onUndo)
        controller?.onRedo?.removeListener(onRedo)
        throttleTimer?.cancel()
        throttleTimer = null
        value = null
        focusNode = null
        controller = null
    }

    private fun handleFocus() {
        if (focusNode?.hasFocus != true) return
        push()
    }

    private fun shouldChangeUndoStack(oldValue: TextFieldValue?, newValue: TextFieldValue): Boolean {
        if (oldValue == null) return true
        // Android keyboards put words in composing regions; Flutter coalesces
        // them instead of skipping composing values on Android.
        return oldValue.text != newValue.text || oldValue.composition != newValue.composition
    }

    private fun push() {
        val current = value?.value ?: return
        if (current == lastValue) return
        if (ignoreChanges) return
        if (!shouldChangeUndoStack(lastValue, current)) return
        lastValue = current
        throttledPush(current)
    }

    // Flutter's `_throttle`: the first change starts a 500 ms timer and the
    // latest value seen when it fires is pushed.
    private fun throttledPush(current: TextFieldValue) {
        pendingPush = current
        if (throttleTimer?.isActive == true) return
        val scope = scope ?: return
        throttleTimer =
            scope.startTimer(500.milliseconds) {
                throttleTimer = null
                pendingPush?.let { stack.push(it) }
                updateState()
            }
    }

    private fun undo() {
        if (stack.currentValue == null) return
        val timer = throttleTimer
        if (timer?.isActive == true) {
            timer.cancel()
            throttleTimer = null
            update(stack.currentValue)
        } else {
            update(stack.undo())
        }
        updateState()
    }

    private fun redo() {
        update(stack.redo())
        updateState()
    }

    private fun updateState() {
        controller?.value = UndoHistoryValue(canUndo = stack.canUndo, canRedo = stack.canRedo)
    }

    private fun update(nextValue: TextFieldValue?) {
        if (nextValue == null) return
        if (nextValue == lastValue) return
        lastValue = nextValue
        ignoreChanges = true
        value?.value = nextValue
        ignoreChanges = false
    }
}
