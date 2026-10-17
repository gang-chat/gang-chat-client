package com.gangchat.client.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.remember

/**
 * Flutter's `State<T extends StatefulWidget>`, so stateful widgets port line
 * by line:
 *
 * ```
 * // Dart: class Counter extends StatefulWidget { final String label; ... }
 * @Composable
 * fun Counter(label: String, modifier: Modifier = Modifier) =
 *     StatefulWidget(CounterWidget(label, modifier)) { _CounterState() }
 *
 * class CounterWidget(val label: String, val modifier: Modifier)
 *
 * private class _CounterState : ViewState<CounterWidget>() {
 *     var count = 0
 *     override fun initState() { ... }
 *     fun increment() = setState { count += 1 }
 *     @Composable override fun build() {
 *         Text("${widget.label}: $count", Modifier.clickable { increment() })
 *     }
 * }
 * ```
 *
 * Semantics follow Flutter: [initState] runs once before the first [build];
 * [didUpdateWidget] runs whenever the parent recomposes with a new widget
 * object; [setState] rebuilds; [dispose] runs when the state leaves the
 * composition; [mounted] and [context] work as in Flutter. Fields are plain
 * Kotlin properties: only [setState] (or a listened `Listenable`) triggers a
 * rebuild, exactly like Flutter.
 */
abstract class ViewState<W : Any> : RememberObserver {
    // The widget lives in snapshot state (never equal, so every new widget
    // object counts as a change) and reading it also reads [revision]. A
    // build() that reads `widget` is therefore invalidated directly when the
    // parent passes a new widget or setState runs. Relying on the host's call
    // to build() is not enough: the Compose compiler infers a state class with
    // no fields of its own as stable and skips build() for an unchanged
    // receiver.
    private val currentWidget = mutableStateOf<W?>(null, neverEqualPolicy())
    private var initialized = false
    private var disposed = false
    internal val revision = mutableIntStateOf(0)

    /** Flutter's `widget`: the latest configuration from the parent. */
    val widget: W
        get() {
            revision.intValue
            return currentWidget.value ?: throw IllegalStateException("ViewState has no widget yet")
        }

    /** Flutter's `context`. */
    lateinit var context: BuildContext
        private set

    /** Flutter's `mounted`. */
    val mounted: Boolean get() = initialized && !disposed

    open fun initState() {}

    /** Called after [initState] (Flutter's first `didChangeDependencies`). */
    open fun didChangeDependencies() {}

    open fun didUpdateWidget(oldWidget: W) {}

    open fun dispose() {}

    /** Flutter's `setState(fn)`: runs [fn] and schedules a rebuild. No-op after dispose. */
    fun setState(fn: () -> Unit = {}) {
        if (disposed) return
        fn()
        if (initialized) revision.intValue += 1
    }

    @Composable
    abstract fun build()

    internal fun update(widget: W, context: BuildContext) {
        this.context = context
        val old = Snapshot.withoutReadObservation { currentWidget.value }
        currentWidget.value = widget
        if (!initialized) {
            initialized = true
            initState()
            didChangeDependencies()
        } else if (old != null && old !== widget) {
            didUpdateWidget(old)
        }
    }

    private fun teardown() {
        if (disposed || !initialized) {
            disposed = true
            return
        }
        disposed = true
        dispose()
    }

    override fun onRemembered() {}

    override fun onForgotten() = teardown()

    override fun onAbandoned() = teardown()
}

/**
 * Hosts a [ViewState] for [widget] (Flutter's `StatefulElement`): creates the
 * state once per position (use Compose `key(...)` where Flutter used a `Key`),
 * forwards new widget objects, and rebuilds on `setState`.
 */
@Composable
fun <W : Any, S : ViewState<W>> StatefulWidget(widget: W, createState: () -> S) {
    val context = rememberBuildContext()
    val state = remember { createState() }
    state.update(widget, context)
    // Reading the revision subscribes this scope to setState().
    state.revision.intValue
    state.build()
}
