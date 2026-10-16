package com.gangchat.client.shell

/**
 * Android's standard large-screen boundary. Keeping this in one platform
 * module prevents phone/tablet checks from drifting between features.
 */
const val androidTabletShortestSide: Double = 600.0

/**
 * Dart's `isAndroidTabletLogicalSize(Size logicalSize)` with the logical
 * (dp) size spelled out, because `:core` has no geometry type. `:app` adds a
 * `DpSize` overload (see `AppOrientationController.kt`).
 */
fun isAndroidTabletLogicalSize(width: Double, height: Double): Boolean {
    return minOf(width.let(Math::abs), height.let(Math::abs)) >= androidTabletShortestSide
}
