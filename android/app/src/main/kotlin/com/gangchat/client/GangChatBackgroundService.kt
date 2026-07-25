package com.gangchat.client

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Process
import androidx.core.content.ContextCompat

class GangChatBackgroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        val notification = GangChatNotifications.backgroundNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceTypes = 0
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                serviceTypes = serviceTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                serviceTypes =
                    serviceTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            }
            if (serviceTypes != 0) {
                startForeground(
                    GangChatNotifications.backgroundNotificationId,
                    notification,
                    serviceTypes,
                )
            } else {
                startForeground(GangChatNotifications.backgroundNotificationId, notification)
            }
        } else {
            startForeground(GangChatNotifications.backgroundNotificationId, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        // Removing the task is an explicit exit: closing the process also
        // closes SSE immediately, so the server no longer reports us online.
        Process.killProcess(Process.myPid())
    }
}
