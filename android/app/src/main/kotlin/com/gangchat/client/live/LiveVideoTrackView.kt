package com.gangchat.client.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.gangchat.client.foundation.VisibleForTesting
import io.livekit.android.renderer.TextureViewRenderer
import livekit.org.webrtc.RendererCommon

enum class LiveVideoTrackFit { cover, contain }

typealias LiveVideoTrackRendererBuilder =
    @Composable (track: LiveVideoTrack, fit: LiveVideoTrackFit, shouldMirror: Boolean) -> Unit

@VisibleForTesting
var liveVideoTrackRendererForTest: LiveVideoTrackRendererBuilder? = null

@VisibleForTesting
fun resetLiveVideoTrackRendererForTest() {
    liveVideoTrackRendererForTest = null
}

/**
 * Renders a live camera or screen-share track (livekit_client's
 * `VideoTrackRenderer`): a LiveKit [TextureViewRenderer] filling the
 * available space, letterboxed for [LiveVideoTrackFit.contain] and cropped
 * for [LiveVideoTrackFit.cover].
 */
@Composable
fun LiveVideoTrackView(
    track: LiveVideoTrack,
    modifier: Modifier = Modifier,
    fit: LiveVideoTrackFit = LiveVideoTrackFit.contain,
    mirrored: Boolean = false,
) {
    // Mirror state is owned explicitly by the app. Screen shares never mirror,
    // and camera rendering does not depend on the SDK's platform-specific
    // automatic policy.
    val shouldMirror = !track.isScreenShare && mirrored
    val testRenderer = liveVideoTrackRendererForTest
    if (testRenderer != null) {
        Box(modifier) { testRenderer(track, fit, shouldMirror) }
        return
    }

    val videoTrack = track.track as? AndroidVideoTrack
    if (videoTrack == null) {
        Box(modifier)
        return
    }
    val scalingType =
        when (fit) {
            LiveVideoTrackFit.cover -> RendererCommon.ScalingType.SCALE_ASPECT_FILL
            LiveVideoTrackFit.contain -> RendererCommon.ScalingType.SCALE_ASPECT_FIT
        }
    // A different track gets a fresh renderer bound to it.
    key(videoTrack) {
        AndroidView(
            factory = { context ->
                TextureViewRenderer(context).apply {
                    init(videoTrack.eglBase.eglBaseContext, null)
                    videoTrack.track.addRenderer(this)
                }
            },
            modifier = modifier.fillMaxSize(),
            update = { renderer ->
                renderer.setScalingType(scalingType)
                renderer.setMirror(shouldMirror)
            },
            onRelease = { renderer ->
                videoTrack.track.removeRenderer(renderer)
                renderer.release()
            },
        )
    }
}
