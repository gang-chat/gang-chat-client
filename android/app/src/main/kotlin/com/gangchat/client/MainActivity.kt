package com.gangchat.client

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.gangchat.client.foundation.AppCoroutines
import com.gangchat.client.platform.AndroidAudioRoute
import com.gangchat.client.platform.AndroidMusicPreviewPlayer
import com.gangchat.client.platform.AndroidSystemBridge
import com.gangchat.client.platform.AndroidUpdateInstaller
import com.gangchat.client.platform.FileSelector
import com.gangchat.client.platform.GangChatAppVisibility
import com.gangchat.client.platform.GangChatPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The single activity. It hosts the Compose UI root ([prepareGangApp]) and
 * routes Android callbacks (new intents, activity and permission results,
 * resume, visibility) to the platform services, as the Flutter host's
 * activity did for its MethodChannels.
 *
 * Starts with `LaunchTheme` (manifest) and switches to `NormalTheme`; draws
 * edge-to-edge; is portrait-only on phones (manifest) until
 * [GangChatStartup.main] applies the `AppOrientationController` default
 * (tablets rotate freely). The launch screen stays up until the UI root is
 * ready, as the Flutter splash stayed until the first frame.
 */
class MainActivity : ComponentActivity() {
    private var appContent: (@Composable () -> Unit)? by mutableStateOf(null)
    private var uiReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.NormalTheme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        GangChatPlatform.attachActivity(this)
        AndroidAudioRoute.applyVolumeControlStream(this)
        AndroidSystemBridge.instance.onActivityCreated(this)

        keepLaunchScreenUntilReady()
        setContent { appContent?.invoke() }
        lifecycleScope.launch {
            try {
                val config = GangChatStartup.main()
                appContent = prepareGangApp(config)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Like an uncaught error in Flutter's main(): reported, and
                // the launch screen stays.
                AppCoroutines.uncaughtErrorHandler(error)
                return@launch
            }
            uiReady = true
            window.decorView.invalidate()
        }
    }

    private fun keepLaunchScreenUntilReady() {
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!uiReady) return false
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )
    }

    @Deprecated("Routes the platform services' startActivityForResult requests.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (AndroidSystemBridge.instance.onActivityResult(requestCode, resultCode, data)) return
        if (FileSelector.onActivityResult(requestCode, resultCode, data)) return
        AndroidUpdateInstaller.instance.onActivityResult(requestCode)
    }

    @Deprecated("Routes the platform services' permission requests.")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        AndroidSystemBridge.instance.onRequestPermissionsResult(requestCode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AndroidSystemBridge.instance.handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        AndroidUpdateInstaller.instance.onResume()
    }

    override fun onStart() {
        super.onStart()
        GangChatAppVisibility.isForeground = true
    }

    override fun onStop() {
        GangChatAppVisibility.isForeground = false
        super.onStop()
    }

    override fun onDestroy() {
        AndroidMusicPreviewPlayer.instance.dispose()
        GangChatPlatform.detachActivity(this)
        super.onDestroy()
    }

    companion object {
        /** Intent extra carrying the room id of a tapped message notification. */
        const val notificationRoomIdExtra = "gang_chat_notification_room_id"

        /** Intent extra set on the "update ready" notification's launch intent. */
        const val pendingUpdateInstallExtra = "gang_chat_pending_update_install"
    }
}
