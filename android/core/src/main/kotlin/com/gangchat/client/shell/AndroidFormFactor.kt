package com.gangchat.client.shell

import com.gangchat.client.foundation.Size

/**
 * Android's standard large-screen boundary. Keeping this in one platform
 * module prevents phone/tablet checks from drifting between features.
 */
const val androidTabletShortestSide: Double = 600.0

fun isAndroidTabletLogicalSize(logicalSize: Size): Boolean =
    logicalSize.shortestSide >= androidTabletShortestSide

/** [isAndroidTabletLogicalSize] with the logical (dp) size spelled out. */
fun isAndroidTabletLogicalSize(width: Double, height: Double): Boolean =
    isAndroidTabletLogicalSize(Size(width, height))
