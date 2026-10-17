package com.gangchat.client.platform.webrtc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager
import java.util.Collections
import java.util.WeakHashMap
import livekit.org.webrtc.CapturerObserver
import livekit.org.webrtc.SurfaceTextureHelper
import livekit.org.webrtc.ThreadUtils
import livekit.org.webrtc.VideoCapturer
import livekit.org.webrtc.VideoFrame
import livekit.org.webrtc.VideoSink

/**
 * A copy of ScreenCapturerAndroid that captures the screen content while
 * being aware of the device orientation: the virtual display follows
 * portrait/landscape so a rotated phone is never letterboxed.
 *
 * Port of flutter_webrtc's `OrientationAwareScreenCapturer` with the Gang
 * Chat fork's changes: the screen-capture foreground service stops with the
 * capturer (and when the projection cannot be created), and the active
 * [MediaProjection] is exposed so Android playback capture can share the same
 * one-shot consent session as the screen video.
 *
 * @param mediaProjectionPermissionResultData the result data of the
 *   MediaProjection permission activity; the caller validated RESULT_OK.
 * @param mediaProjectionCallback application logic for events such as the
 *   user revoking the capture permission.
 */
class OrientationAwareScreenCapturer(
    private val mediaProjectionPermissionResultData: Intent,
    private val mediaProjectionCallback: MediaProjection.Callback,
) : VideoCapturer, VideoSink {
    private var width = 0
    private var height = 0
    private var oldWidth = 0
    private var oldHeight = 0
    private var virtualDisplay: VirtualDisplay? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var capturerObserver: CapturerObserver? = null
    private var numCapturedFrames: Long = 0

    @Volatile
    private var mediaProjection: MediaProjection? = null
    private var isDisposed = false
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var windowManager: WindowManager? = null
    private var applicationContext: Context? = null
    private var isPortrait = false

    /** The size the capture was started with (the display's real size). */
    @Volatile
    var captureWidth: Int = 0
        private set

    @Volatile
    var captureHeight: Int = 0
        private set

    override fun onFrame(frame: VideoFrame) {
        checkNotDisposed()
        isPortrait = isDeviceOrientationPortrait()
        val max = maxOf(height, width)
        val min = minOf(height, width)
        if (isPortrait) {
            changeCaptureFormat(min, max, 15)
        } else {
            changeCaptureFormat(max, min, 15)
        }
        capturerObserver?.onFrameCaptured(frame)
    }

    @Suppress("DEPRECATION")
    private fun isDeviceOrientationPortrait(): Boolean {
        val display = requireNotNull(windowManager).defaultDisplay
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        return metrics.heightPixels > metrics.widthPixels
    }

    private fun checkNotDisposed() {
        if (isDisposed) throw RuntimeException("capturer is disposed.")
    }

    @Synchronized
    override fun initialize(
        surfaceTextureHelper: SurfaceTextureHelper?,
        applicationContext: Context,
        capturerObserver: CapturerObserver?,
    ) {
        checkNotDisposed()
        if (capturerObserver == null) throw RuntimeException("capturerObserver not set.")
        this.capturerObserver = capturerObserver
        if (surfaceTextureHelper == null) throw RuntimeException("surfaceTextureHelper not set.")
        this.surfaceTextureHelper = surfaceTextureHelper
        this.applicationContext = applicationContext.applicationContext
        windowManager = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mediaProjectionManager =
            applicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    @Synchronized
    override fun startCapture(width: Int, height: Int, ignoredFramerate: Int) {
        isPortrait = isDeviceOrientationPortrait()
        if (isPortrait) {
            this.width = width
            this.height = height
        } else {
            this.height = width
            this.width = height
        }
        captureWidth = width
        captureHeight = height

        val projection =
            try {
                requireNotNull(mediaProjectionManager)
                    .getMediaProjection(Activity.RESULT_OK, mediaProjectionPermissionResultData)
            } catch (error: RuntimeException) {
                applicationContext?.let(ScreenCaptureForegroundService::stop)
                throw error
            } ?: run {
                applicationContext?.let(ScreenCaptureForegroundService::stop)
                throw RuntimeException("MediaProjection could not be created.")
            }
        mediaProjection = projection

        // Let MediaProjection callback use the SurfaceTextureHelper thread.
        val helper = requireNotNull(surfaceTextureHelper)
        projection.registerCallback(mediaProjectionCallback, helper.handler)

        createVirtualDisplay()
        capturerObserver?.onCapturerStarted(true)
        helper.startListening(this)
        activeCapturers.add(this)
    }

    @Synchronized
    override fun stopCapture() {
        checkNotDisposed()
        val helper = surfaceTextureHelper ?: return
        ThreadUtils.invokeAtFrontUninterruptibly(helper.handler) {
            helper.stopListening()
            capturerObserver?.onCapturerStopped()
            virtualDisplay?.release()
            virtualDisplay = null
            val projection = mediaProjection
            if (projection != null) {
                // Unregister the callback before stopping, otherwise the callback
                // recursively calls this method.
                projection.unregisterCallback(mediaProjectionCallback)
                projection.stop()
                mediaProjection = null
            }
            activeCapturers.remove(this)
            applicationContext?.let(ScreenCaptureForegroundService::stop)
        }
    }

    @Synchronized
    override fun dispose() {
        isDisposed = true
        activeCapturers.remove(this)
        applicationContext?.let(ScreenCaptureForegroundService::stop)
    }

    /**
     * Changes the output video format. Used to follow the display when the
     * captured screen rotates.
     */
    @Synchronized
    override fun changeCaptureFormat(width: Int, height: Int, ignoredFramerate: Int) {
        checkNotDisposed()
        if (oldWidth == width && oldHeight == height) return
        oldWidth = width
        oldHeight = height
        val helper = surfaceTextureHelper ?: return

        if (oldHeight > oldWidth) {
            ThreadUtils.invokeAtFrontUninterruptibly(helper.handler) {
                val display = virtualDisplay
                if (display != null) {
                    display.surface = Surface(helper.surfaceTexture)
                    helper.setTextureSize(oldWidth, oldHeight)
                    display.resize(oldWidth, oldHeight, VIRTUAL_DISPLAY_DPI)
                }
            }
        }

        if (oldWidth > oldHeight) {
            helper.setTextureSize(oldWidth, oldHeight)
            virtualDisplay?.surface = Surface(helper.surfaceTexture)
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    ThreadUtils.invokeAtFrontUninterruptibly(helper.handler) {
                        virtualDisplay?.resize(oldWidth, oldHeight, VIRTUAL_DISPLAY_DPI)
                    }
                },
                700,
            )
        }
    }

    private fun createVirtualDisplay() {
        val helper = requireNotNull(surfaceTextureHelper)
        helper.setTextureSize(width, height)
        helper.surfaceTexture.setDefaultBufferSize(width, height)
        virtualDisplay =
            requireNotNull(mediaProjection).createVirtualDisplay(
                "WebRTC_ScreenCapture",
                width,
                height,
                VIRTUAL_DISPLAY_DPI,
                DISPLAY_FLAGS,
                Surface(helper.surfaceTexture),
                null,
                null,
            )
    }

    override fun isScreencast(): Boolean = true

    fun getNumCapturedFrames(): Long = numCapturedFrames

    /**
     * Returns the active projection so Android playback capture can share the
     * same one-shot consent session as the screen video.
     */
    fun getMediaProjection(): MediaProjection? = mediaProjection

    companion object {
        private const val DISPLAY_FLAGS =
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION

        // DPI for VirtualDisplay, does not seem to matter for us.
        private const val VIRTUAL_DISPLAY_DPI = 400

        private val activeCapturers: MutableSet<OrientationAwareScreenCapturer> =
            Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

        /**
         * The fork's `GetUserMediaImpl.getActiveScreenMediaProjection()`: the
         * projection of a running screen capture, if any.
         */
        fun activeScreenMediaProjection(): MediaProjection? =
            synchronized(activeCapturers) {
                activeCapturers.firstNotNullOfOrNull { it.mediaProjection }
            }
    }
}
