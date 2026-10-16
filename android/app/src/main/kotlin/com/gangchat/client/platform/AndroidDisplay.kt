package com.gangchat.client.platform

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Display queries the Flutter app made through the engine or the
 * `gang_chat/display_orientation` channel.
 */
object AndroidDisplay {
    /** `Surface.ROTATION_*` of the display showing the app (the channel's `getDisplayRotation`). */
    fun getDisplayRotation(): Int {
        val activity = GangChatPlatform.activity
        if (activity != null) return currentDisplayRotation(activity)
        val displays = GangChatPlatform.context.getSystemService(DisplayManager::class.java)
        return displays?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayRotation(activity: ComponentActivity): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                return activity.display?.rotation ?: Surface.ROTATION_0
            } catch (_: UnsupportedOperationException) {
                // The context is not bound to a display; use the window's.
            }
        }
        return activity.windowManager.defaultDisplay.rotation
    }

    /**
     * The app window's logical size in dp (Flutter's
     * `PlatformDispatcher.views.first.physicalSize / devicePixelRatio`).
     * Returns [DpSize.Zero] when no window exists yet.
     */
    @Suppress("DEPRECATION")
    fun logicalWindowSize(): DpSize {
        val activity = GangChatPlatform.activity
        val context: Context = activity ?: if (GangChatPlatform.isInstalled) GangChatPlatform.context else return DpSize.Zero
        val density = context.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = activity.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            return DpSize((bounds.width() / density).dp, (bounds.height() / density).dp)
        }
        val decor = activity?.window?.peekDecorView()
        if (decor != null && decor.width > 0 && decor.height > 0) {
            return DpSize((decor.width / density).dp, (decor.height / density).dp)
        }
        val metrics = context.resources.displayMetrics
        return DpSize((metrics.widthPixels / density).dp, (metrics.heightPixels / density).dp)
    }
}

/**
 * The `gang_chat/android_audio_route` channel: hardware volume keys and the
 * system audio mode while the music box plays during a live session.
 */
object AndroidAudioRoute {
    private var musicBoxActive = false

    /**
     * Binds the volume keys to the media stream while the music box is
     * audible and makes the matching audio mode authoritative.
     *
     * flutter_webrtc / LiveKit update AudioSwitch's desired mode at runtime,
     * but some OEM implementations (including OPPO) do not apply that mode to
     * AudioManager until a later device-route cycle. This runs on Android's
     * main thread, so make the active system mode authoritative now.
     */
    fun setMusicBoxActive(active: Boolean, liveSessionActive: Boolean) {
        musicBoxActive = active
        GangChatPlatform.activity?.let(::applyVolumeControlStream)
        val audioManager = GangChatPlatform.context.getSystemService(AudioManager::class.java)
        audioManager.mode =
            when {
                active -> AudioManager.MODE_NORMAL
                liveSessionActive -> AudioManager.MODE_IN_COMMUNICATION
                else -> AudioManager.MODE_NORMAL
            }
    }

    /** Applies the current volume-key binding to [activity] (also on activity creation). */
    fun applyVolumeControlStream(activity: ComponentActivity) {
        activity.volumeControlStream =
            if (musicBoxActive) AudioManager.STREAM_MUSIC else AudioManager.USE_DEFAULT_STREAM_TYPE
    }
}
