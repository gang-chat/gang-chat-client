package com.gangchat.client

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
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
        val processStopped = AtomicBoolean(false)
        val stopProcess = Runnable {
            if (processStopped.compareAndSet(false, true)) {
                // Keep this foreground service alive while Flutter sends the
                // terminal live-leave request. Calling stopSelf() before the
                // method-channel round trip makes the process immediately
                // reclaimable on aggressive Android builds (notably OPPO),
                // leaving the remote participant and screen share stale.
                stopSelf()
                Process.killProcess(Process.myPid())
            }
        }

        // Removing the task is an explicit exit, unlike merely putting the app
        // in the background. Let Flutter send its authenticated live-leave and
        // offline requests first so other clients update immediately. If the
        // engine is unavailable or blocked, the timeout still closes the
        // process and LiveKit's participant_left webhook remains the fallback.
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(stopProcess, taskRemovalGraceMillis)
        if (!AndroidSystemBridge.notifyTaskRemoved { stopProcess.run() }) {
            stopProcess.run()
        }
    }

    companion object {
        private const val taskRemovalGraceMillis = 3000L
    }
}
