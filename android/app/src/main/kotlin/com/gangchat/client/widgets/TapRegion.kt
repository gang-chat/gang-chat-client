package com.gangchat.client.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset

/**
 * The pointer-down event a [TapRegion] reports (Flutter's `PointerDownEvent`),
 * in window dp. [pointer] identifies the pointer for [TapRegionRegistry.addPointerRoute].
 */
class PointerDownEvent(val position: DpOffset, val kind: PointerType, val pointer: Long = 0L)

/** A later event of a routed pointer (Flutter's `PointerMoveEvent` / `PointerUpEvent`). */
class PointerRouteEvent(val pointer: Long, val position: DpOffset, val isUp: Boolean)

/**
 * Flutter's `TapRegionSurface` (installed by `MaterialApp`): watches every
 * pointer down inside it and tells each registered [TapRegion] whether the
 * tap landed inside its group or outside it. `UiTheme` installs one at the
 * app root.
 */
class TapRegionRegistry internal constructor() {
    internal class Region(
        var groupId: Any?,
        var enabled: Boolean,
        var onTapOutside: ((PointerDownEvent) -> Unit)?,
        var onTapInside: ((PointerDownEvent) -> Unit)?,
    ) {
        var coordinates: LayoutCoordinates? = null

        /**
         * The route the region was built in. Like Flutter's `TapRegion`
         * (`ModalRoute.isCurrentOf`), a region whose route is covered by
         * another route (a dialog, say) gets no outside taps.
         */
        var route: Route<*>? = null

        val onTapOutsideIfCurrent: ((PointerDownEvent) -> Unit)?
            get() = if (route?.isCurrent == false) null else onTapOutside

        fun contains(windowPosition: Offset): Boolean {
            val coords = coordinates ?: return false
            if (!coords.isAttached) return false
            return coords.boundsInWindow().contains(windowPosition)
        }
    }

    private val regions = mutableListOf<Region>()
    private val globalPointerDownListeners = mutableListOf<(PointerDownEvent) -> Unit>()

    internal fun register(region: Region) {
        regions.add(region)
    }

    internal fun unregister(region: Region) {
        regions.remove(region)
    }

    /** Observes every pointer down on the surface (Flutter's global pointer route). */
    fun addGlobalPointerDownListener(listener: (PointerDownEvent) -> Unit) {
        globalPointerDownListeners.add(listener)
    }

    fun removeGlobalPointerDownListener(listener: (PointerDownEvent) -> Unit) {
        globalPointerDownListeners.remove(listener)
    }

    private val pointerRoutes = mutableMapOf<Long, MutableList<(PointerRouteEvent) -> Unit>>()

    /**
     * Flutter's `GestureBinding.pointerRouter.addRoute(pointer, route)`:
     * [route] receives [pointer]'s later moves and its up (or cancel) anywhere
     * on the surface, whoever handles them.
     */
    fun addPointerRoute(pointer: Long, route: (PointerRouteEvent) -> Unit) {
        pointerRoutes.getOrPut(pointer) { mutableListOf() }.add(route)
    }

    fun removePointerRoute(pointer: Long, route: (PointerRouteEvent) -> Unit) {
        val routes = pointerRoutes[pointer] ?: return
        routes.remove(route)
        if (routes.isEmpty()) pointerRoutes.remove(pointer)
    }

    internal fun dispatchRoute(pointer: Long, windowPosition: Offset, isUp: Boolean, density: Density) {
        val routes = pointerRoutes[pointer] ?: return
        val event = with(density) { PointerRouteEvent(pointer, DpOffset(windowPosition.x.toDp(), windowPosition.y.toDp()), isUp) }
        for (route in routes.toList()) route(event)
    }

    internal fun dispatchDown(windowPosition: Offset, kind: PointerType, density: Density, pointer: Long = 0L) {
        val event = with(density) { PointerDownEvent(DpOffset(windowPosition.x.toDp(), windowPosition.y.toDp()), kind, pointer) }
        for (listener in globalPointerDownListeners.toList()) listener(event)
        val active = regions.filter { it.enabled }
        val hitGroups = HashSet<Any>()
        val hitRegions = HashSet<Region>()
        for (region in active) {
            if (region.contains(windowPosition)) {
                hitRegions.add(region)
                region.groupId?.let { hitGroups.add(it) }
            }
        }
        for (region in active) {
            val inside = region in hitRegions || (region.groupId != null && region.groupId in hitGroups)
            if (inside) region.onTapInside?.invoke(event) else region.onTapOutsideIfCurrent?.invoke(event)
        }
    }
}

val LocalTapRegionRegistry = staticCompositionLocalOf<TapRegionRegistry?> { null }

/** Flutter's `TapRegionSurface`. */
@Composable
fun TapRegionSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val registry = remember { TapRegionRegistry() }
    val density = LocalDensity.current
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        modifier
            .onGloballyPositioned { holder[0] = it }
            .pointerInput(registry) {
                awaitPointerEventScope {
                    while (true) {
                        // The initial pass runs before any descendant can consume.
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        for (change in event.changes) {
                            val coords = holder[0]
                            val windowPosition =
                                if (coords != null && coords.isAttached) coords.localToWindow(change.position) else change.position
                            if (change.changedToDownIgnoreConsumed()) {
                                registry.dispatchDown(windowPosition, change.type, density, change.id.value)
                            } else if (change.pressed || change.previousPressed) {
                                registry.dispatchRoute(change.id.value, windowPosition, isUp = !change.pressed, density = density)
                            }
                        }
                    }
                }
            },
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(LocalTapRegionRegistry provides registry, content = content)
    }
}

/**
 * Flutter's `TapRegion`: reports pointer downs outside (or inside) the
 * region. Regions sharing a [groupId] count as one region. Requires a
 * [TapRegionSurface] ancestor; without one it never fires.
 */
@Composable
fun TapRegion(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    groupId: Any? = null,
    onTapOutside: ((PointerDownEvent) -> Unit)? = null,
    onTapInside: ((PointerDownEvent) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier.tapRegion(enabled, groupId, onTapOutside, onTapInside), propagateMinConstraints = true) { content() }
}

/** Modifier form of [TapRegion]. */
@Composable
fun Modifier.tapRegion(
    enabled: Boolean = true,
    groupId: Any? = null,
    onTapOutside: ((PointerDownEvent) -> Unit)? = null,
    onTapInside: ((PointerDownEvent) -> Unit)? = null,
): Modifier {
    val registry = LocalTapRegionRegistry.current
    val region = remember { TapRegionRegistry.Region(groupId, enabled, onTapOutside, onTapInside) }
    region.groupId = groupId
    region.enabled = enabled
    region.onTapOutside = onTapOutside
    region.onTapInside = onTapInside
    region.route = LocalModalRoute.current
    DisposableEffect(registry, region) {
        registry?.register(region)
        onDispose { registry?.unregister(region) }
    }
    return this.onGloballyPositioned { region.coordinates = it }
}
