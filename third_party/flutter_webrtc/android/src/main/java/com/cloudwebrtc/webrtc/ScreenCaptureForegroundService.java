package com.cloudwebrtc.webrtc;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/**
 * Keeps Android screen capture compliant with the MediaProjection foreground
 * service requirement. It starts only after the user granted the projection
 * consent and stops together with the capturer.
 */
public final class ScreenCaptureForegroundService extends Service {
    private static final String CHANNEL_ID = "gang_chat_screen_capture";
    private static final int NOTIFICATION_ID = 1101;

    public interface StartCallback {
        void onResult(boolean started);
    }

    private static volatile boolean foreground;

    public static void start(Context context, StartCallback callback) {
        ContextCompat.startForegroundService(
                context,
                new Intent(context, ScreenCaptureForegroundService.class));
        waitUntilForeground(callback, 0);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, ScreenCaptureForegroundService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int foregroundServiceTypes =
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED) {
                foregroundServiceTypes |=
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            }
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    foregroundServiceTypes);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        foreground = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        foreground = false;
        super.onDestroy();
    }

    private static void waitUntilForeground(StartCallback callback, int attempt) {
        if (foreground) {
            callback.onResult(true);
            return;
        }
        if (attempt >= 100) {
            callback.onResult(false);
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> waitUntilForeground(callback, attempt + 1),
                20);
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "屏幕共享",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Gang Chat 正在共享屏幕");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launchIntent == null) {
            launchIntent = new Intent(Intent.ACTION_MAIN).setPackage(getPackageName());
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("Gang Chat")
                .setContentText("正在共享屏幕")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
