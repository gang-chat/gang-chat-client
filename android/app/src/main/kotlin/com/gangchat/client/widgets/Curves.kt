package com.gangchat.client.widgets

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/** Flutter's `Curves`, as Compose [Easing]s (same control points). */
object Curves {
    val linear: Easing = Easing { it }
    val decelerate: Easing = Easing { t -> 1f - (1f - t) * (1f - t) }
    val ease: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
    val easeIn: Easing = CubicBezierEasing(0.42f, 0.0f, 1.0f, 1.0f)
    val easeOut: Easing = CubicBezierEasing(0.0f, 0.0f, 0.58f, 1.0f)
    val easeInOut: Easing = CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f)
    val fastOutSlowIn: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val slowMiddle: Easing = CubicBezierEasing(0.15f, 0.85f, 0.85f, 0.15f)
    val easeInSine: Easing = CubicBezierEasing(0.47f, 0.0f, 0.745f, 0.715f)
    val easeInQuad: Easing = CubicBezierEasing(0.55f, 0.085f, 0.68f, 0.53f)
    val easeInCubic: Easing = CubicBezierEasing(0.55f, 0.055f, 0.675f, 0.19f)
    val easeInQuart: Easing = CubicBezierEasing(0.895f, 0.03f, 0.685f, 0.22f)
    val easeInExpo: Easing = CubicBezierEasing(0.95f, 0.05f, 0.795f, 0.035f)
    val easeInBack: Easing = CubicBezierEasing(0.6f, -0.28f, 0.735f, 0.045f)
    val easeOutSine: Easing = CubicBezierEasing(0.39f, 0.575f, 0.565f, 1.0f)
    val easeOutQuad: Easing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
    val easeOutCubic: Easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1.0f)
    val easeOutQuart: Easing = CubicBezierEasing(0.165f, 0.84f, 0.44f, 1.0f)
    val easeOutQuint: Easing = CubicBezierEasing(0.23f, 1.0f, 0.32f, 1.0f)
    val easeOutExpo: Easing = CubicBezierEasing(0.19f, 1.0f, 0.22f, 1.0f)
    val easeOutBack: Easing = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)
    val easeInOutSine: Easing = CubicBezierEasing(0.445f, 0.05f, 0.55f, 0.95f)
    val easeInOutQuad: Easing = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)
    val easeInOutCubic: Easing = CubicBezierEasing(0.645f, 0.045f, 0.355f, 1.0f)
    val easeInOutQuart: Easing = CubicBezierEasing(0.77f, 0.0f, 0.175f, 1.0f)
    val easeInOutExpo: Easing = CubicBezierEasing(1.0f, 0.0f, 0.0f, 1.0f)
    val easeInOutBack: Easing = CubicBezierEasing(0.68f, -0.55f, 0.265f, 1.55f)
    val linearToEaseOut: Easing = CubicBezierEasing(0.35f, 0.91f, 0.33f, 0.97f)
    val easeInToLinear: Easing = CubicBezierEasing(0.67f, 0.03f, 0.65f, 0.09f)
    val fastLinearToSlowEaseIn: Easing = CubicBezierEasing(0.18f, 1.0f, 0.04f, 1.0f)
}
