package com.gangchat.client.widgets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import com.gangchat.client.foundation.unawaited
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

/** Flutter's `Route<T>`. */
abstract class Route<T> {
    internal val completer = CompletableDeferred<T?>()

    var navigator: NavigatorState? = null
        internal set

    /** Completes with the pop result (Flutter's `route.popped` / push future). */
    val popped: Deferred<T?> get() = completer

    val isCurrent: Boolean get() = navigator?.history?.lastOrNull() === this

    val isActive: Boolean get() = navigator?.history?.contains(this) == true

    internal var overlayEntries: List<OverlayEntry> = emptyList()

    internal abstract fun createOverlayEntries(): List<OverlayEntry>

    internal open fun onPush() {}

    /** Starts removal; [NavigatorState] calls [NavigatorState.finalizeRoute] when done. */
    internal open fun onPop() {
        navigator?.finalizeRoute(this)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun complete(result: Any?) {
        if (!completer.isCompleted) completer.complete(result as T?)
    }
}

/** A registration made by [PopScope]. */
class PopEntry internal constructor() {
    var canPop: Boolean by mutableStateOf(true)
    var onPopInvokedWithResult: ((didPop: Boolean, result: Any?) -> Unit)? = null
}

/**
 * Flutter's `ModalRoute`: a barrier entry plus a page entry, with an enter /
 * exit transition driven by [animationValue] (0 -> 1 on push, back on pop).
 */
abstract class ModalRoute<T> : Route<T>() {
    abstract val opaque: Boolean
    open val maintainState: Boolean = true
    abstract val barrierColor: Color?
    abstract val barrierDismissible: Boolean

    /** Enter duration in milliseconds. */
    abstract val transitionDuration: Int

    open val reverseTransitionDuration: Int get() = transitionDuration

    /** Curve applied to the barrier color animation (Flutter's `barrierCurve`). */
    open val barrierCurve: androidx.compose.animation.core.Easing get() = Curves.ease

    private val animation = Animatable(0f)
    private var exiting by mutableStateOf(false)
    internal val popEntries = mutableStateListOf<PopEntry>()

    /** Current transition progress (Flutter's `animation.value`). */
    val animationValue: Float get() = animation.value

    /** Whether a [PopScope] in this route currently blocks popping. */
    val blocksPop: Boolean get() = popEntries.any { !it.canPop }

    @Composable
    abstract fun buildPage(context: BuildContext)

    /** Wraps the page in its transition; [animation] goes 0 -> 1 while entering. */
    @Composable
    open fun buildTransitions(context: BuildContext, animation: Float, content: @Composable () -> Unit) {
        content()
    }

    override fun createOverlayEntries(): List<OverlayEntry> {
        val barrier = OverlayEntry { _ -> Barrier() }
        val page = OverlayEntry(maintainState = maintainState) { context -> Page(context) }
        return listOf(barrier, page)
    }

    override fun onPop() {
        exiting = true
    }

    internal fun invokePopCallbacks(didPop: Boolean, result: Any?) {
        for (entry in popEntries.toList()) entry.onPopInvokedWithResult?.invoke(didPop, result)
    }

    @Composable
    private fun Barrier() {
        val color = barrierColor
        val progress = animation.value
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (color != null && color.alpha > 0f) {
                        Modifier.background(lerp(Color.Transparent, color, barrierCurve.transform(progress.coerceIn(0f, 1f))))
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (opaque) {
                        Modifier
                    } else {
                        Modifier.pointerInput(barrierDismissible) {
                            // Flutter's ModalBarrier absorbs every pointer and
                            // dismisses on tap when allowed.
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                val up = waitForUpOrCancellationCompat()
                                if (up && barrierDismissible && !exiting) {
                                    navigator?.let { navigator -> unawaited { navigator.maybePop() } }
                                }
                            }
                        }
                    },
                ),
        )
    }

    @Composable
    private fun Page(context: BuildContext) {
        val pageEntry = overlayEntries.lastOrNull()
        LaunchedEffect(exiting) {
            if (!exiting) {
                animation.animateTo(1f, tween(transitionDuration, easing = Curves.linear))
                // Like Flutter, an opaque route hides the routes below once its
                // entrance completes.
                pageEntry?.opaque = opaque
            } else {
                pageEntry?.opaque = false
                animation.animateTo(0f, tween(reverseTransitionDuration, easing = Curves.linear))
                navigator?.finalizeRoute(this@ModalRoute)
            }
        }
        CompositionLocalProvider(LocalModalRoute provides this) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (exiting) Modifier.ignorePointer() else Modifier),
            ) {
                buildTransitions(context, animation.value) { buildPage(context) }
            }
        }
    }
}

/** Flutter's `PopupRoute`: a non-opaque modal route. */
abstract class PopupRoute<T> : ModalRoute<T>() {
    override val opaque: Boolean get() = false
    override val maintainState: Boolean get() = true
}

/**
 * Flutter's `DialogRoute` as used by Material `showDialog`: a dismissible
 * black54 barrier, a 150 ms ease-out fade, and the dialog laid out inside the
 * safe area.
 */
class DialogRoute<T>(
    override val barrierDismissible: Boolean = true,
    override val barrierColor: Color? = Color(0x8A000000),
    private val useSafeArea: Boolean = true,
    private val builder: @Composable (BuildContext) -> Unit,
) : PopupRoute<T>() {
    override val transitionDuration: Int get() = 150

    @Composable
    override fun buildPage(context: BuildContext) {
        if (useSafeArea) {
            SafeArea { builder(rememberBuildContext()) }
        } else {
            builder(context)
        }
    }

    @Composable
    override fun buildTransitions(context: BuildContext, animation: Float, content: @Composable () -> Unit) {
        val opacity = Curves.easeOut.transform(animation.coerceIn(0f, 1f))
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = opacity }) { content() }
    }
}

/** Flutter's `PageRouteBuilder`. */
class PageRouteBuilder<T>(
    private val pageBuilder: @Composable (context: BuildContext, animation: Float, secondaryAnimation: Float) -> Unit,
    private val transitionsBuilder: (
        @Composable (context: BuildContext, animation: Float, secondaryAnimation: Float, child: @Composable () -> Unit) -> Unit
    )? = null,
    override val transitionDuration: Int = 300,
    private val reverseDuration: Int = 300,
    override val opaque: Boolean = true,
    override val barrierDismissible: Boolean = false,
    override val barrierColor: Color? = null,
    override val maintainState: Boolean = true,
) : ModalRoute<T>() {
    override val reverseTransitionDuration: Int get() = reverseDuration

    @Composable
    override fun buildPage(context: BuildContext) {
        pageBuilder(context, animationValue, 0f)
    }

    @Composable
    override fun buildTransitions(context: BuildContext, animation: Float, content: @Composable () -> Unit) {
        val builder = transitionsBuilder
        if (builder == null) content() else builder(context, animation, 0f, content)
    }
}

/** The root page route ([Navigator]'s home): opaque, no transition. */
internal class HomeRoute(private val home: @Composable (BuildContext) -> Unit) : ModalRoute<Unit>() {
    override val opaque: Boolean get() = true
    override val barrierColor: Color? get() = null
    override val barrierDismissible: Boolean get() = false
    override val transitionDuration: Int get() = 0

    @Composable
    override fun buildPage(context: BuildContext) = home(context)

    override fun createOverlayEntries(): List<OverlayEntry> =
        listOf(OverlayEntry(opaque = true, maintainState = true) { context -> HomePage(context) })

    @Composable
    private fun HomePage(context: BuildContext) {
        CompositionLocalProvider(LocalModalRoute provides this) { buildPage(context) }
    }
}

val LocalNavigator = staticCompositionLocalOf<NavigatorState?> { null }

val LocalModalRoute = staticCompositionLocalOf<ModalRoute<*>?> { null }

/** Flutter's `NavigatorState` (one root navigator, as the app uses). */
class NavigatorState internal constructor() {
    val overlay = OverlayState()
    internal val history = mutableStateListOf<Route<*>>()

    /** Flutter's `push(route)`; await the result with `.await()`. */
    fun <T> push(route: Route<T>): Deferred<T?> {
        val lastEntry = history.lastOrNull()?.overlayEntries?.lastOrNull()
        route.navigator = this
        history.add(route)
        route.overlayEntries = route.createOverlayEntries()
        overlay.insertAll(route.overlayEntries, above = lastEntry)
        route.onPush()
        return route.popped
    }

    /** Flutter's `pop(result)`: pops the current route. */
    fun pop(result: Any? = null) {
        val route = history.lastOrNull() ?: return
        if (history.size <= 1) return
        history.removeAt(history.lastIndex)
        (route as? ModalRoute<*>)?.invokePopCallbacks(true, result)
        route.complete(result)
        route.onPop()
    }

    /** Flutter's `maybePop(result)`: honors [PopScope]; false when nothing to pop. */
    suspend fun maybePop(result: Any? = null): Boolean {
        val route = history.lastOrNull() ?: return false
        if (route is ModalRoute<*> && route.blocksPop) {
            route.invokePopCallbacks(false, result)
            return true
        }
        if (history.size <= 1) return false
        pop(result)
        return true
    }

    fun canPop(): Boolean = history.size > 1

    fun popUntil(predicate: (Route<*>) -> Boolean) {
        while (history.size > 1 && !predicate(history.last())) pop()
    }

    /** Removes [route] without completing a transition (Flutter's `removeRoute`). */
    fun removeRoute(route: Route<*>, result: Any? = null) {
        if (!history.remove(route)) return
        route.complete(result)
        finalizeRoute(route)
    }

    internal fun finalizeRoute(route: Route<*>) {
        for (entry in route.overlayEntries) entry.remove()
        route.overlayEntries = emptyList()
    }

    /** True when system back should be routed through [maybePop]. */
    internal val handlesBack: Boolean
        get() = history.size > 1 || (history.lastOrNull() as? ModalRoute<*>)?.blocksPop == true

    companion object {
        /** Flutter's `Navigator.of(context)`. */
        fun of(context: BuildContext, rootNavigator: Boolean = false): NavigatorState =
            context[LocalNavigator] ?: throw IllegalStateException("No Navigator in context")

        fun maybeOf(context: BuildContext, rootNavigator: Boolean = false): NavigatorState? = context[LocalNavigator]

        /** Flutter's static `Navigator.pop(context, result)`. */
        fun pop(context: BuildContext, result: Any? = null) = of(context).pop(result)

        /** Flutter's static `Navigator.maybePop(context, result)`. */
        suspend fun maybePop(context: BuildContext, result: Any? = null): Boolean = of(context).maybePop(result)
    }
}

/** Alias so ports can write `Navigator.of(context)` like Dart. */
typealias Navigator = NavigatorState

/**
 * The root navigator (what `MaterialApp(home:)` creates): an [Overlay] whose
 * first route is [home]. System back pops routes and honors [PopScope]; with
 * nothing to pop it falls through to the activity's default behavior.
 */
@Composable
fun RootNavigator(home: @Composable (BuildContext) -> Unit) {
    val navigator = remember { NavigatorState() }
    remember { navigator.push(HomeRoute(home)) }
    CompositionLocalProvider(LocalNavigator provides navigator) {
        BackHandler(enabled = navigator.handlesBack) {
            unawaited { navigator.maybePop() }
        }
        Overlay(navigator.overlay)
    }
}

/**
 * Flutter's `PopScope`: with [canPop] false, back gestures and `maybePop` call
 * [onPopInvokedWithResult] with `didPop = false` instead of popping.
 */
@Composable
fun PopScope(
    canPop: Boolean = true,
    onPopInvokedWithResult: ((didPop: Boolean, result: Any?) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val route = LocalModalRoute.current
    val entry = remember { PopEntry() }
    entry.canPop = canPop
    entry.onPopInvokedWithResult = onPopInvokedWithResult
    DisposableEffect(route, entry) {
        route?.popEntries?.add(entry)
        onDispose { route?.popEntries?.remove(entry) }
    }
    content()
}

/**
 * Flutter's Material `showDialog`: pushes a [DialogRoute] on the root
 * navigator and suspends until it is popped, returning the pop result.
 */
suspend fun <T> showDialog(
    context: BuildContext,
    barrierDismissible: Boolean = true,
    barrierColor: Color? = Color(0x8A000000),
    useSafeArea: Boolean = true,
    useRootNavigator: Boolean = true,
    builder: @Composable (BuildContext) -> Unit,
): T? =
    Navigator.of(context, rootNavigator = useRootNavigator)
        .push(DialogRoute<T>(barrierDismissible, barrierColor, useSafeArea, builder))
        .await()

/**
 * Flutter's `AbsorbPointer`: the subtree gets no pointer input, and the region
 * swallows touches (it is a hit target, widened to the 48dp minimum touch size
 * when smaller). For Flutter's `IgnorePointer`, where touches must pass through:
 * add nothing over purely visual content (nodes without pointer input are
 * already invisible to hit testing), and use [unplaced] for hidden interactive
 * content.
 */
fun Modifier.ignorePointer(ignoring: Boolean = true): Modifier =
    if (!ignoring) {
        this
    } else {
        this.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitForUpOrCancellationCompat(): Boolean {
    val up = waitForUpOrCancellation()
    up?.consume()
    return up != null
}
