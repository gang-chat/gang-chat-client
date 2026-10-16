package com.gangchat.client

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import java.util.UUID

data class GangChatPushRegistration(
    val provider: String,
    val installationId: String,
    val token: String,
    val enabled: Boolean,
) {
    fun asMap(): Map<String, Any> =
        mapOf(
            "provider" to provider,
            "installationId" to installationId,
            "token" to token,
            "enabled" to enabled,
        )
}

object GangChatPushRegistrationProvider {
    private const val provider = "fcm"
    private const val installationIdKey = "push_installation_id"
    private const val cachedTokenKey = "push_fcm_token"

    fun current(
        context: Context,
        callback: (Result<GangChatPushRegistration?>) -> Unit,
    ) {
        val applicationContext = context.applicationContext
        if (FirebaseApp.getApps(applicationContext).isEmpty()) {
            callback(Result.success(null))
            return
        }
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                callback(
                    Result.failure(
                        task.exception ?: IllegalStateException("FCM token unavailable"),
                    ),
                )
                return@addOnCompleteListener
            }
            val token = task.result?.trim().orEmpty()
            if (token.isEmpty()) {
                callback(Result.success(null))
                return@addOnCompleteListener
            }
            cacheToken(applicationContext, token)
            callback(
                Result.success(
                    GangChatPushRegistration(
                        provider = provider,
                        installationId = installationId(applicationContext),
                        token = token,
                        enabled = notificationPreferenceEnabled(applicationContext),
                    ),
                ),
            )
        }
    }

    fun onTokenRefreshed(context: Context, token: String) {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return
        cacheToken(context, cleanToken)
        AndroidSystemBridge.notifyPushTokenChanged(
            mapOf(
                "provider" to provider,
                "installationId" to installationId(context),
                "token" to cleanToken,
                "enabled" to notificationPreferenceEnabled(context),
            ),
        )
    }

    fun cached(context: Context): GangChatPushRegistration? {
        val token =
            preferences(context).getString(cachedTokenKey, null)?.trim().orEmpty()
        if (token.isEmpty()) return null
        return GangChatPushRegistration(
            provider = provider,
            installationId = installationId(context),
            token = token,
            enabled = notificationPreferenceEnabled(context),
        )
    }

    private fun installationId(context: Context): String {
        val preferences = preferences(context)
        val existing = preferences.getString(installationIdKey, null)?.trim()
        if (!existing.isNullOrEmpty()) return existing
        val generated = UUID.randomUUID().toString()
        preferences.edit().putString(installationIdKey, generated).apply()
        return generated
    }

    private fun cacheToken(context: Context, token: String) {
        preferences(context).edit().putString(cachedTokenKey, token).apply()
    }

    private fun notificationPreferenceEnabled(context: Context): Boolean =
        preferences(context).getBoolean(
            AndroidSystemBridge.notificationsEnabledKey,
            true,
        )

    private fun preferences(context: Context) =
        context.getSharedPreferences(
            AndroidSystemBridge.preferencesName,
            Context.MODE_PRIVATE,
        )
}
