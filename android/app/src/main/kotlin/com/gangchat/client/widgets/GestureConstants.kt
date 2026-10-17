package com.gangchat.client.widgets

import androidx.compose.ui.input.pointer.PointerType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Flutter's `kTouchSlop` (logical pixels = dp). */
const val kTouchSlop: Double = 18.0

/** Flutter's `kLongPressTimeout`. */
val kLongPressTimeout: Duration = 500.milliseconds

/** Flutter's `kDoubleTapTimeout`. */
val kDoubleTapTimeout: Duration = 300.milliseconds

/** Flutter's touch-like pointer kinds (touch, stylus, inverted stylus). */
fun PointerType.isTouchLike(): Boolean = this == PointerType.Touch || this == PointerType.Stylus || this == PointerType.Eraser
