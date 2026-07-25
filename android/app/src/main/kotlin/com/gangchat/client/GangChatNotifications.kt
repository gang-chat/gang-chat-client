package com.gangchat.client

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object GangChatNotifications {
    const val backgroundChannelId = "gang_chat_background"
    const val messageChannelId = "gang_chat_messages"
    const val backgroundNotificationId = 1001
    private const val summaryNotificationId = 1002
    private const val messageNotificationIdBase = 20_000
    private const val messageGroup = "gang_chat_room_messages"
    private const val lastMessageIdPrefix = "last_notified_message_id:"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    backgroundChannelId,
                    "后台在线",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "保持 Gang Chat 在后台在线"
                    setShowBadge(false)
                },
                NotificationChannel(
                    messageChannelId,
                    "房间消息",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "显示通知策略为“全部”的房间消息"
                    enableVibration(true)
                    setShowBadge(true)
                },
            ),
        )
    }

    fun backgroundNotification(context: Context): Notification {
        ensureChannels(context)
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(context, 0, launchIntent, pendingIntentFlags())
        return NotificationCompat.Builder(context, backgroundChannelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Gang Chat")
            .setContentText("正在后台保持在线")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }

    fun showRoomMessage(
        context: Context,
        roomId: String,
        roomName: String,
        sender: String,
        body: String,
        unreadCount: Int,
        messageId: String = "",
    ) {
        if (!notificationsAllowed(context) || roomId.isBlank()) return
        if (!rememberMessageNotification(context, roomId, messageId)) return
        ensureChannels(context)
        val intent =
            (context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, MainActivity::class.java)).apply {
                putExtra(MainActivity.notificationRoomIdExtra, roomId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                roomId.hashCode() and Int.MAX_VALUE,
                intent,
                pendingIntentFlags(),
            )
        val cleanSender = sender.trim().ifEmpty { "新消息" }
        val cleanBody = body.trim().ifEmpty { "收到一条新消息" }
        val content = "$cleanSender：$cleanBody"
        val notification =
            NotificationCompat.Builder(context, messageChannelId)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(roomName.trim().ifEmpty { "Gang Chat" })
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setGroup(messageGroup)
                .setNumber(unreadCount.coerceAtLeast(1))
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                .build()
        NotificationManagerCompat.from(context)
            .notify(roomNotificationId(roomId), notification)
        syncBadge(context, unreadCount)
    }

    fun cancelRoom(context: Context, roomId: String) {
        NotificationManagerCompat.from(context).cancel(roomNotificationId(roomId))
    }

    fun syncBadge(context: Context, unreadCount: Int) {
        val manager = NotificationManagerCompat.from(context)
        if (unreadCount <= 0) {
            manager.cancel(summaryNotificationId)
            return
        }
        if (!notificationsAllowed(context)) return
        ensureChannels(context)
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(context, 1, launchIntent, pendingIntentFlags())
        val summary =
            NotificationCompat.Builder(context, messageChannelId)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("Gang Chat")
                .setContentText("$unreadCount 条未读消息")
                .setContentIntent(pendingIntent)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setGroup(messageGroup)
                .setGroupSummary(true)
                .setNumber(unreadCount)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        manager.notify(summaryNotificationId, summary)
    }

    fun cancelMessages(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val systemManager = context.getSystemService(NotificationManager::class.java)
            systemManager.activeNotifications
                .filter { it.notification.channelId == messageChannelId }
                .forEach { manager.cancel(it.id) }
        } else {
            manager.cancel(summaryNotificationId)
        }
    }

    fun notificationsAllowed(context: Context): Boolean {
        val preferenceEnabled =
            context
                .getSharedPreferences(AndroidSystemBridge.preferencesName, Context.MODE_PRIVATE)
                .getBoolean(AndroidSystemBridge.notificationsEnabledKey, true)
        if (!preferenceEnabled) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun roomNotificationId(roomId: String): Int =
        messageNotificationIdBase + ((roomId.hashCode() and Int.MAX_VALUE) % 100_000)

    @Synchronized
    private fun rememberMessageNotification(
        context: Context,
        roomId: String,
        messageId: String,
    ): Boolean {
        val cleanMessageId = messageId.trim()
        if (cleanMessageId.isEmpty()) return true
        val preferences =
            context.getSharedPreferences(
                AndroidSystemBridge.preferencesName,
                Context.MODE_PRIVATE,
            )
        val key = lastMessageIdPrefix + roomId
        if (preferences.getString(key, null) == cleanMessageId) return false
        preferences.edit().putString(key, cleanMessageId).apply()
        return true
    }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
