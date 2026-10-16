package com.gangchat.client.platform

import android.content.Context
import androidx.activity.ComponentActivity

/**
 * The Android context the platform services act on, standing in for the
 * Flutter engine's plugin bindings.
 *
 * [GangChatApplication][com.gangchat.client.GangChatApplication] installs the
 * application context at process start; `MainActivity` attaches itself while
 * it exists, so services that need an activity (system pickers, permission
 * prompts, orientation, system bars) can find it. Everything runs on the main
 * thread, like the MethodChannel handlers did.
 */
object GangChatPlatform {
    @Volatile
    private var applicationContext: Context? = null

    /** The activity currently hosting the app UI, if any. */
    var activity: ComponentActivity? = null
        private set

    val isInstalled: Boolean get() = applicationContext != null

    /** The application context; throws [MissingPluginException] before [install]. */
    val context: Context
        get() = applicationContext
            ?: throw MissingPluginException("Gang Chat platform services are not installed")

    fun install(context: Context) {
        applicationContext = context.applicationContext ?: context
    }

    fun attachActivity(activity: ComponentActivity) {
        this.activity = activity
    }

    fun detachActivity(activity: ComponentActivity) {
        if (this.activity === activity) this.activity = null
    }

    /**
     * The hosting activity; throws the error a Flutter plugin reported when it
     * had no activity binding.
     */
    fun requireActivity(): ComponentActivity =
        activity ?: throw PlatformException("no_activity", "No activity is available.")
}
