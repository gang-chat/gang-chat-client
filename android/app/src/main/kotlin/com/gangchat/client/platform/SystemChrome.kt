package com.gangchat.client.platform

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Flutter's `DeviceOrientation` (same order). */
enum class DeviceOrientation { portraitUp, landscapeLeft, portraitDown, landscapeRight }

/** Flutter's `SystemUiMode`. */
enum class SystemUiMode { leanBack, immersive, immersiveSticky, edgeToEdge, manual }

/** Flutter's `SystemUiOverlay`. */
enum class SystemUiOverlay { top, bottom }

/** Flutter's `Brightness` for system bar icons. */
enum class SystemIconBrightness { dark, light }

/**
 * Flutter's `SystemUiOverlayStyle`: `null` fields leave the current value
 * unchanged. Colors are ARGB ints.
 */
data class SystemUiOverlayStyle(
    val systemNavigationBarColor: Int? = null,
    val systemNavigationBarDividerColor: Int? = null,
    val systemStatusBarContrastEnforced: Boolean? = null,
    val statusBarColor: Int? = null,
    val statusBarIconBrightness: SystemIconBrightness? = null,
    val systemNavigationBarIconBrightness: SystemIconBrightness? = null,
    val systemNavigationBarContrastEnforced: Boolean? = null,
)

/**
 * Flutter's `SystemChrome` on Android, acting on the activity attached to
 * [GangChatPlatform]. Without an activity the calls do nothing (the Flutter
 * engine had no platform plugin then either).
 */
object SystemChrome {
    /** Flutter's `SystemChrome.setPreferredOrientations`. */
    suspend fun setPreferredOrientations(orientations: List<DeviceOrientation>) {
        val activity = GangChatPlatform.activity ?: return
        activity.requestedOrientation = androidOrientationFor(orientations)
    }

    /**
     * Flutter's `SystemChrome.setEnabledSystemUIMode`. `manual` shows exactly
     * [overlays]; the immersive modes hide both bars (sticky: a swipe shows
     * them transiently); `edgeToEdge` shows both bars over the content.
     */
    suspend fun setEnabledSystemUIMode(mode: SystemUiMode, overlays: List<SystemUiOverlay>? = null) {
        val activity = GangChatPlatform.activity ?: return
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        when (mode) {
            SystemUiMode.manual -> {
                val shown = overlays ?: SystemUiOverlay.entries
                val hidden = SystemUiOverlay.entries - shown.toSet()
                controller.systemBarsBehavior =
                    if (shown.isEmpty()) {
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                    }
                for (overlay in shown) controller.show(insetsTypeFor(overlay))
                for (overlay in hidden) controller.hide(insetsTypeFor(overlay))
            }
            SystemUiMode.leanBack, SystemUiMode.immersive -> {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
            SystemUiMode.immersiveSticky -> {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
            SystemUiMode.edgeToEdge -> {
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    /** Flutter's `SystemChrome.setSystemUIOverlayStyle` (AnnotatedRegion). */
    fun setSystemUIOverlayStyle(style: SystemUiOverlayStyle) {
        val activity = GangChatPlatform.activity ?: return
        applyOverlayStyle(activity, style)
    }

    @Suppress("DEPRECATION")
    private fun applyOverlayStyle(activity: Activity, style: SystemUiOverlayStyle) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
            )
        }
        when (style.statusBarIconBrightness) {
            SystemIconBrightness.dark -> controller.isAppearanceLightStatusBars = true
            SystemIconBrightness.light -> controller.isAppearanceLightStatusBars = false
            null -> Unit
        }
        if (style.statusBarColor != null && Build.VERSION.SDK_INT < 35) {
            window.statusBarColor = style.statusBarColor
        }
        if (style.systemStatusBarContrastEnforced != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = style.systemStatusBarContrastEnforced
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (style.systemNavigationBarIconBrightness) {
                SystemIconBrightness.dark -> controller.isAppearanceLightNavigationBars = true
                SystemIconBrightness.light -> controller.isAppearanceLightNavigationBars = false
                null -> Unit
            }
            if (style.systemNavigationBarColor != null && Build.VERSION.SDK_INT < 35) {
                window.navigationBarColor = style.systemNavigationBarColor
            }
        }
        if (style.systemNavigationBarDividerColor != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            Build.VERSION.SDK_INT < 35
        ) {
            window.navigationBarDividerColor = style.systemNavigationBarDividerColor
        }
        if (style.systemNavigationBarContrastEnforced != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = style.systemNavigationBarContrastEnforced
        }
    }

    private fun insetsTypeFor(overlay: SystemUiOverlay): Int =
        when (overlay) {
            SystemUiOverlay.top -> WindowInsetsCompat.Type.statusBars()
            SystemUiOverlay.bottom -> WindowInsetsCompat.Type.navigationBars()
        }

    /**
     * The Flutter engine's mapping from a set of orientations to an Android
     * `screenOrientation` (`PlatformChannel.decodeOrientations`).
     */
    fun androidOrientationFor(orientations: List<DeviceOrientation>): Int {
        var requestedOrientation = 0x00
        var firstRequestedOrientation = 0x00
        for (orientation in orientations) {
            requestedOrientation =
                requestedOrientation or
                when (orientation) {
                    DeviceOrientation.portraitUp -> 0x01
                    DeviceOrientation.portraitDown -> 0x04
                    DeviceOrientation.landscapeLeft -> 0x02
                    DeviceOrientation.landscapeRight -> 0x08
                }
            if (firstRequestedOrientation == 0x00) firstRequestedOrientation = requestedOrientation
        }
        return when (requestedOrientation) {
            0x00 -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            0x01 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            0x02 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            0x04 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            0x05 -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            0x08 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            0x0a -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            0x0b -> ActivityInfo.SCREEN_ORIENTATION_USER
            0x0f -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            // Android can't describe these cases, so just default to whatever
            // the first specified value was.
            0x03, 0x06, 0x07, 0x09, 0x0c, 0x0d, 0x0e ->
                when (firstRequestedOrientation) {
                    0x01 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    0x02 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    0x04 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    0x08 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}
