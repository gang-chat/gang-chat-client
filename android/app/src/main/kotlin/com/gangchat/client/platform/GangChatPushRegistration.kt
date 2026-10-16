package com.gangchat.client.platform

import android.content.Context
import com.gangchat.client.foundation.VisibleForTesting
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

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

/**
 * FCM registration for offline room-message pushes. The installation id and
 * cached token live in the Flutter build's `gang_chat_android_preferences`
 * file, so an upgraded install keeps its server-side push registration.
 */
object GangChatPushRegistrationProvider {
    private const val provider = "fcm"
    private const val installationIdKey = "push_installation_id"
    private const val cachedTokenKey = "push_fcm_token"

    /**
     * Test hook replacing the Firebase token lookup. `null` (the default)
     * asks Firebase, and reports no registration when Firebase is not
     * configured in this build (no google-services.json).
     */
    @VisibleForTesting
    @Volatile
    var tokenSourceOverride: (suspend () -> String?)? = null

    /**
     * The current registration, or `null` when Firebase is unavailable or has
     * no token. Firebase failures surface as the exception Firebase reported.
     */
    suspend fun current(context: Context): GangChatPushRegistration? {
        val applicationContext = context.applicationContext ?: context
        val override = tokenSourceOverride
        val token =
            if (override != null) {
                override()
            } else {
                if (FirebaseApp.getApps(applicationContext).isEmpty()) return null
                firebaseToken()
            }
        val cleanToken = token?.trim().orEmpty()
        if (cleanToken.isEmpty()) return null
        cacheToken(applicationContext, cleanToken)
        return GangChatPushRegistration(
            provider = provider,
            installationId = installationId(applicationContext),
            token = cleanToken,
            enabled = notificationPreferenceEnabled(applicationContext),
        )
    }

    private suspend fun firebaseToken(): String? =
        suspendCancellableCoroutine { continuation ->
            // The Flutter host's token source (deprecated in newer Firebase
            // releases, still the one that matches existing server registrations).
            @Suppress("DEPRECATION")
            val token = FirebaseMessaging.getInstance().token
            token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    continuation.resumeWithException(
                        task.exception ?: IllegalStateException("FCM token unavailable"),
                    )
                    return@addOnCompleteListener
                }
                continuation.resume(task.result)
            }
        }

    fun onTokenRefreshed(context: Context, token: String) {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return
        cacheToken(context, cleanToken)
        AndroidSystemBridge.notifyPushTokenChanged(
            GangChatPushRegistration(
                provider = provider,
                installationId = installationId(context),
                token = cleanToken,
                enabled = notificationPreferenceEnabled(context),
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
