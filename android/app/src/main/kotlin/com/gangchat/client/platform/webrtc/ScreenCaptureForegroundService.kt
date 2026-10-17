package com.gangchat.client.platform.webrtc

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Keeps Android screen capture compliant with the MediaProjection foreground
 * service requirement. It starts only after the user granted the projection
 * consent and stops together with the capturer.
 *
 * Port of the Gang Chat flutter_webrtc fork's `ScreenCaptureForegroundService`
 * (same channel, notification id and text).
 */
class ScreenCaptureForegroundService : Service() {
    fun interface StartCallback {
        fun onResult(started: Boolean)
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var foregroundServiceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            ) {
                foregroundServiceTypes = foregroundServiceTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, notification, foregroundServiceTypes)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foreground = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        foreground = false
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "屏幕共享", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Gang Chat 正在共享屏幕"
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(Intent.ACTION_MAIN).setPackage(packageName)
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Gang Chat")
            .setContentText("正在共享屏幕")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "gang_chat_screen_capture"
        const val NOTIFICATION_ID = 1101

        @Volatile
        private var foreground = false

        val isForeground: Boolean get() = foreground

        fun start(context: Context, callback: StartCallback) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ScreenCaptureForegroundService::class.java),
            )
            waitUntilForeground(callback, 0)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCaptureForegroundService::class.java))
        }

        private fun waitUntilForeground(callback: StartCallback, attempt: Int) {
            if (foreground) {
                callback.onResult(true)
                return
            }
            if (attempt >= 100) {
                callback.onResult(false)
                return
            }
            Handler(Looper.getMainLooper()).postDelayed({ waitUntilForeground(callback, attempt + 1) }, 20)
        }
    }
}
