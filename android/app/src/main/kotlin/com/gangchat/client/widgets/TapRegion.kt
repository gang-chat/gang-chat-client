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

/** The pointer-down event a [TapRegion] reports (Flutter's `PointerDownEvent`), in window dp. */
class PointerDownEvent(val position: DpOffset, val kind: PointerType)

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

    internal fun dispatchDown(windowPosition: Offset, kind: PointerType, density: Density) {
        val event = with(density) { PointerDownEvent(DpOffset(windowPosition.x.toDp(), windowPosition.y.toDp()), kind) }
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
            if (inside) region.onTapInside?.invoke(event) else region.onTapOutside?.invoke(event)
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
                            if (!change.changedToDownIgnoreConsumed()) continue
                            val coords = holder[0]
                            val windowPosition =
                                if (coords != null && coords.isAttached) coords.localToWindow(change.position) else change.position
                            registry.dispatchDown(windowPosition, change.type, density)
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
    DisposableEffect(registry, region) {
        registry?.register(region)
        onDispose { registry?.unregister(region) }
    }
    return this.onGloballyPositioned { region.coordinates = it }
}
