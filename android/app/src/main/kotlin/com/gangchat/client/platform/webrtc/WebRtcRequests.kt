package com.gangchat.client.platform.webrtc

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.gangchat.client.platform.GangChatPlatform
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred

/**
 * A failure flutter_webrtc's Dart side reported as a plain string
 * (`throw 'Unable to getUserMedia: ...'`). `toString()` is exactly that text,
 * which the app's error display matches on.
 */
class WebRtcPlatformError(override val message: String) : Exception(message) {
    override fun toString(): String = message
}

/**
 * The activity round trips flutter_webrtc's Android plugin made for media
 * capture: the runtime permission prompt of `getUserMedia` and the
 * MediaProjection consent of `getDisplayMedia`. They use the hosting
 * activity's result registry, so no activity callback needs routing.
 */
object WebRtcRequests {
    private val nextKey = AtomicInteger()

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * `GetUserMediaImpl.requestPermissions`: resolves with the granted subset
     * of [permissions], prompting for the missing ones.
     */
    suspend fun requestPermissions(permissions: List<String>): List<String> {
        val context = GangChatPlatform.context
        val missing = permissions.filterNot { granted(context, it) }
        if (missing.isEmpty()) return permissions
        val activity = GangChatPlatform.activity ?: return permissions.filter { granted(context, it) }
        val result = CompletableDeferred<Map<String, Boolean>>()
        var launcher: ActivityResultLauncher<Array<String>>? = null
        launcher =
            activity.activityResultRegistry.register(
                "gang_chat_webrtc_permissions_${nextKey.incrementAndGet()}",
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { grants ->
                launcher?.unregister()
                result.complete(grants)
            }
        launcher.launch(missing.toTypedArray())
        result.await()
        return permissions.filter { granted(context, it) }
    }

    /**
     * `getUserMedia` permission handling: every requested permission must be
     * granted, else the call fails with the plugin's `NotAllowedError`.
     */
    suspend fun ensureUserMediaPermissions(permissions: List<String>) {
        val grantedPermissions = requestPermissions(permissions)
        if (!grantedPermissions.containsAll(permissions)) {
            // According to step 10 Permission Failure of the getUserMedia()
            // algorithm, if the user has denied permission, fail "with a new
            // DOMException object whose name attribute has the value
            // NotAllowedError."
            throw WebRtcPlatformError("Unable to getUserMedia: DOMException, NotAllowedError")
        }
    }

    /**
     * `screenRequestPermissions`: asks for MediaProjection consent and returns
     * its result data. Android 14 treats each consent token as single-use, so
     * every share asks again.
     */
    suspend fun requestScreenCapture(): Intent {
        val activity =
            GangChatPlatform.activity
                ?: throw WebRtcPlatformError(
                    "Unable to getDisplayMedia: User didn't give permission to capture the screen.",
                )
        val projectionManager =
            activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val result = CompletableDeferred<ActivityResult>()
        var launcher: ActivityResultLauncher<Intent>? = null
        launcher =
            activity.activityResultRegistry.register(
                "gang_chat_screen_capture_${nextKey.incrementAndGet()}",
                ActivityResultContracts.StartActivityForResult(),
            ) { activityResult ->
                launcher?.unregister()
                result.complete(activityResult)
            }
        launcher.launch(projectionManager.createScreenCaptureIntent())
        val activityResult = result.await()
        val data = activityResult.data
        if (activityResult.resultCode != android.app.Activity.RESULT_OK || data == null) {
            throw WebRtcPlatformError(
                "Unable to getDisplayMedia: User didn't give permission to capture the screen.",
            )
        }
        return data
    }

    /**
     * `getDisplayMediaAfterForegroundService`: the MediaProjection foreground
     * service must be running before the projection is created.
     */
    suspend fun startScreenCaptureForegroundService() {
        val started = CompletableDeferred<Boolean>()
        ScreenCaptureForegroundService.start(GangChatPlatform.context) { started.complete(it) }
        if (!started.await()) {
            throw WebRtcPlatformError(
                "Unable to getDisplayMedia: The media-projection foreground service did not start.",
            )
        }
    }
}
