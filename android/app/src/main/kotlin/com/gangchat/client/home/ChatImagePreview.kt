package com.gangchat.client.home

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.MediaCacheController
import com.gangchat.client.app.MediaCacheRequest
import com.gangchat.client.app.userFacingErrorMessage
import com.gangchat.client.foundation.Completer
import com.gangchat.client.foundation.complete
import com.gangchat.client.foundation.future
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.shell.AndroidDisplayRotationReader
import com.gangchat.client.shell.AndroidDisplayRotationService
import com.gangchat.client.shell.AppOrientationController
import com.gangchat.client.shell.FullScreenMediaOrientation
import com.gangchat.client.shell.counterDisplayRotationQuarterTurns
import com.gangchat.client.shell.isAndroidTabletLogicalSize
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.FileImage
import com.gangchat.client.ui.MediaCacheScope
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.decodeImageFileSize
import com.gangchat.client.ui.showFloatingErrorNotice
import com.gangchat.client.ui.showFloatingSuccessNotice
import com.gangchat.client.widgets.BoxFit
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.CompositionTimer
import com.gangchat.client.widgets.FutureBuilder
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.MediaQuery
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.Orientation
import com.gangchat.client.widgets.PageRouteBuilder
import com.gangchat.client.widgets.PopScope
import com.gangchat.client.widgets.SafeArea
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.applyBoxFitDestination
import com.gangchat.client.widgets.inscribeCentered
import com.gangchat.client.widgets.kTouchSlop
import com.gangchat.client.widgets.rememberBuildContext
import com.gangchat.client.widgets.startTimer
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

/** Captures the UI behind an Android image preview (Dart: the scope's `RepaintBoundary.toImage`). */
typealias ImagePreviewBackdropCapture = suspend () -> ImagePreviewBackdropSnapshot?

private class _ChatImagePreviewActionsData(val actions: ChatImagePreviewActions, val captureBackdrop: ImagePreviewBackdropCapture)

private val _LocalChatImagePreviewActions = staticCompositionLocalOf<_ChatImagePreviewActionsData?> { null }

/**
 * Provides the image-preview actions to the subtree and records it (Dart's
 * `RepaintBoundary`) so an Android phone preview can freeze it as its
 * backdrop.
 */
@Composable
fun ChatImagePreviewActionsScope(actions: ChatImagePreviewActions, modifier: Modifier = Modifier, child: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val capture: ImagePreviewBackdropCapture = remember(layer, density) { { _captureBackdrop(layer, density.density) } }
    CompositionLocalProvider(_LocalChatImagePreviewActions provides _ChatImagePreviewActionsData(actions, capture)) {
        Box(
            modifier.drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
            propagateMinConstraints = true,
        ) {
            child()
        }
    }
}

private suspend fun _captureBackdrop(layer: GraphicsLayer, devicePixelRatio: Float): ImagePreviewBackdropSnapshot? {
    if (layer.size.width <= 0 || layer.size.height <= 0) return null
    return try {
        var image = layer.toImageBitmap()
        val logicalSize = DpSize((layer.size.width / devicePixelRatio).dp, (layer.size.height / devicePixelRatio).dp)
        // Like Dart's `toImage(pixelRatio: min(devicePixelRatio, 2.0))`.
        if (devicePixelRatio > 2f) {
            val scale = 2f / devicePixelRatio
            val source = image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
            image =
                Bitmap.createScaledBitmap(
                    source,
                    max(1, (source.width * scale).roundToInt()),
                    max(1, (source.height * scale).roundToInt()),
                    true,
                ).asImageBitmap()
        }
        ImagePreviewBackdropSnapshot(image = image, logicalSize = logicalSize)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        // A snapshot failure must not break image preview navigation.
        null
    }
}

/** Dart's static `ChatImagePreviewActionsScope.maybeOf` / `captureBackdrop`. */
object ChatImagePreviewActionsScope {
    fun maybeOf(context: BuildContext): ChatImagePreviewActions? = context[_LocalChatImagePreviewActions]?.actions

    suspend fun captureBackdrop(context: BuildContext): ImagePreviewBackdropSnapshot? =
        context[_LocalChatImagePreviewActions]?.captureBackdrop?.invoke()
}

/**
 * Callbacks for the full-screen image preview overlay, bundled so they can
 * travel from the chat pane down to each image bubble without threading
 * several separate parameters through every intermediate widget. The home
 * shell wires the implementations (download, save-as, clipboard,
 * save-sticker).
 *
 * [onDownload] saves the image at a URL straight to Downloads; [onSaveAs]
 * saves it through a save-location picker; [onCopyToClipboard] copies the
 * image bytes. [onSaveSticker] / [onSaveRoomSticker] add a sticker message's
 * sticker to the personal / room pack (null when not applicable). All throw
 * on failure ([ImagePreviewActionCancelled] for a user cancel).
 */
class ChatImagePreviewActions(
    val onDownload: suspend (url: String, suggestedName: String) -> Unit,
    val onSaveAs: suspend (url: String, suggestedName: String) -> Unit,
    val onCopyToClipboard: suspend (url: String) -> Unit,
    val onSaveSticker: (suspend (message: Message, attachment: MessageAttachment) -> Unit)? = null,
    val onSaveRoomSticker: (suspend (message: Message, attachment: MessageAttachment) -> Unit)? = null,
    /** Optional disk cache used for preview rendering and image actions. */
    val mediaCache: MediaCacheController? = null,
) {
    companion object {
        /** A no-op bundle for tests and previews where the actions aren't exercised. */
        fun disabled(): ChatImagePreviewActions =
            ChatImagePreviewActions(onDownload = { _, _ -> }, onSaveAs = { _, _ -> }, onCopyToClipboard = { _ -> })
    }
}

/**
 * Thrown by a preview action when the user cancels (e.g. dismisses the
 * save-as picker). The overlay treats it as a silent no-op.
 */
class ImagePreviewActionCancelled : Exception()

/**
 * A frozen copy of the UI behind an Android image preview. [logicalSize]
 * preserves the portrait layout dimensions independently of later device
 * orientation changes.
 */
class ImagePreviewBackdropSnapshot(val image: ImageBitmap, val logicalSize: DpSize) {
    /**
     * Dart disposed the engine image here. The bitmap is left to the garbage
     * collector: the route may still draw it during its last frame.
     */
    fun dispose() {}
}

/** Dart's `({Message message, MessageAttachment attachment})` sticker source record. */
class ChatImagePreviewStickerSource(val message: Message, val attachment: MessageAttachment)

/**
 * Opens the full-screen image preview as an overlay route. [imageUrl] is the
 * already-resolved absolute URL to display. [suggestedName] seeds the
 * download/save-as filename. When [stickerSource] is non-null and
 * [ChatImagePreviewActions.onSaveSticker] is provided, an "添加到我的表情包"
 * action is shown.
 *
 * Android phones freeze the UI behind the preview (so a landscape rotation
 * never relays out the page underneath) and allow landscape while it is
 * open; Android tablets use a translucent route with no orientation lock.
 */
suspend fun showChatImagePreview(
    context: BuildContext,
    imageUrl: String,
    suggestedName: String,
    actions: ChatImagePreviewActions,
    stickerSource: ChatImagePreviewStickerSource? = null,
    showActionBar: Boolean = true,
    forceSquare: Boolean = false,
    orientationController: AppOrientationController? = null,
    backdropCapture: ImagePreviewBackdropCapture? = null,
    displayRotationReader: AndroidDisplayRotationReader? = null,
) {
    val useAndroidPhonePreview = !isAndroidTabletLogicalSize(MediaQuery.sizeOf(context))
    val navigator = Navigator.of(context, rootNavigator = true)
    var frozenBackdrop: ImagePreviewBackdropSnapshot? = null
    if (useAndroidPhonePreview) {
        frozenBackdrop = if (backdropCapture != null) backdropCapture() else ChatImagePreviewActionsScope.captureBackdrop(context)
    }
    if (!context.mounted) {
        frozenBackdrop?.dispose()
        return
    }
    val hasFrozenAndroidBackdrop = useAndroidPhonePreview && frozenBackdrop != null
    val previewOrientationController = if (hasFrozenAndroidBackdrop) orientationController ?: AppOrientationController() else null
    val activeDisplayRotationReader: AndroidDisplayRotationReader? =
        if (hasFrozenAndroidBackdrop) displayRotationReader ?: AndroidDisplayRotationService()::currentQuarterTurns else null
    val backdrop = frozenBackdrop
    try {
        navigator.push(
            PageRouteBuilder<Unit>(
                opaque = hasFrozenAndroidBackdrop,
                barrierColor = if (hasFrozenAndroidBackdrop) null else Color(0xD1000000),
                barrierDismissible = !hasFrozenAndroidBackdrop,
                transitionDuration = if (hasFrozenAndroidBackdrop) 0 else 140,
                reverseDuration = if (hasFrozenAndroidBackdrop) 0 else 120,
                pageBuilder = { _, _, _ ->
                    val overlay: @Composable () -> Unit = {
                        _ImagePreviewOverlay(
                            imageUrl = imageUrl,
                            suggestedName = suggestedName,
                            actions = actions,
                            stickerSource = stickerSource,
                            showActionBar = showActionBar,
                            forceSquare = forceSquare,
                            frozenBackdrop = backdrop,
                            orientationController = previewOrientationController,
                            displayRotationReader = activeDisplayRotationReader,
                        )
                    }
                    if (!hasFrozenAndroidBackdrop) {
                        overlay()
                    } else {
                        FullScreenMediaOrientation(controller = previewOrientationController) { overlay() }
                    }
                },
                transitionsBuilder = { _, animation, _, child ->
                    if (hasFrozenAndroidBackdrop) {
                        child()
                    } else {
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = animation.coerceIn(0f, 1f) }) { child() }
                    }
                },
            ),
        ).await()
    } finally {
        frozenBackdrop?.dispose()
    }
}

/** Test tag of the preview image (or its placeholder) (Dart's `ValueKey('chat-image-preview-url-image')`). */
const val chatImagePreviewImageTag = "chat-image-preview-url-image"

/** Test tag of the gesture viewer (Dart's `ValueKey('chat-image-preview-viewer')`). */
const val chatImagePreviewViewerTag = "chat-image-preview-viewer"

/** Test tag of the zoom/pan layer (Dart's `ValueKey('chat-image-preview-image-transform')`). */
const val chatImagePreviewTransformTag = "chat-image-preview-image-transform"

/** Test tag of the frozen backdrop image (Dart's `ValueKey('chat-image-preview-frozen-backdrop')`). */
const val chatImagePreviewFrozenBackdropTag = "chat-image-preview-frozen-backdrop"

/** Test tag of the frozen backdrop frame (Dart's `ValueKey('chat-image-preview-frozen-backdrop-frame')`). */
const val chatImagePreviewFrozenBackdropFrameTag = "chat-image-preview-frozen-backdrop-frame"

/** Test tag of the backdrop rotation box (Dart's `'chat-image-preview-frozen-backdrop-rotation-$quarterTurns'`). */
fun chatImagePreviewFrozenBackdropRotationTag(quarterTurns: Int) = "chat-image-preview-frozen-backdrop-rotation-$quarterTurns"

@Composable
private fun _ImagePreviewOverlay(
    imageUrl: String,
    suggestedName: String,
    actions: ChatImagePreviewActions,
    stickerSource: ChatImagePreviewStickerSource?,
    showActionBar: Boolean,
    forceSquare: Boolean,
    frozenBackdrop: ImagePreviewBackdropSnapshot?,
    orientationController: AppOrientationController?,
    displayRotationReader: AndroidDisplayRotationReader?,
) = StatefulWidget(
    _ImagePreviewOverlayWidget(
        imageUrl, suggestedName, actions, stickerSource, showActionBar, forceSquare, frozenBackdrop, orientationController,
        displayRotationReader,
    ),
) { _ImagePreviewOverlayState() }

private class _ImagePreviewOverlayWidget(
    val imageUrl: String,
    val suggestedName: String,
    val actions: ChatImagePreviewActions,
    val stickerSource: ChatImagePreviewStickerSource?,
    val showActionBar: Boolean,
    val forceSquare: Boolean,
    val frozenBackdrop: ImagePreviewBackdropSnapshot?,
    val orientationController: AppOrientationController?,
    val displayRotationReader: AndroidDisplayRotationReader?,
)

private enum class _PreviewAction { download, saveAs, copy, saveSticker, saveRoomSticker }

private class _PreviewImageData(val file: File, val size: Size)

/** Scale and translation (in dp) of the preview image (Dart's `Matrix4`). */
private data class _PreviewImageTransform(val scale: Float = 1f, val offset: Offset = Offset.Zero)

private class _ImagePreviewOverlayState : ViewState<_ImagePreviewOverlayWidget>() {
    private var _imageTransform by mutableStateOf(_PreviewImageTransform())
    private var _previewImageData: Deferred<_PreviewImageData>? = null
    private var _previewImageDataKey: String? = null
    private var _gestureStartScale: Float? = null
    private var _gestureStartContentFocal: Offset? = null
    private var _busy: _PreviewAction? = null
    private var _closing = false
    private var _routeCanPop = false
    private var _controlsVisible = true
    private var _controlsHideTimer: CompositionTimer? = null
    private var _portraitViewportCompleter: Completer<Unit>? = null
    private var _lastAndroidPreviewOrientation: Orientation? = null
    private var _lastViewportSize: DpSize? = null
    private var _backdropQuarterTurns = 0
    private var _displayRotationReadGeneration = 0
    private var _scope: CoroutineScope? = null
    private val _focusRequester = FocusRequester()

    private val _canSaveSticker: Boolean get() = widget.stickerSource != null && widget.actions.onSaveSticker != null

    private val _canSaveRoomSticker: Boolean get() = widget.stickerSource != null && widget.actions.onSaveRoomSticker != null

    // Android (like Dart's Android/Windows path) hides the controls after a
    // pause and shows them again on interaction.
    private val _usesImmersiveControls: Boolean get() = true

    private val _isPortraitViewport: Boolean
        get() {
            val size = MediaQuery.sizeOf(context)
            return size.height >= size.width
        }

    override fun initState() {
        unawaited { _refreshBackdropRotation() }
    }

    // Dart's WidgetsBindingObserver.didChangeMetrics.
    private fun _didChangeMetrics() {
        if (mounted && _isPortraitViewport) {
            val completer = _portraitViewportCompleter
            if (completer != null && !completer.isCompleted) completer.complete()
        }
        unawaited { _refreshBackdropRotation() }
    }

    private suspend fun _refreshBackdropRotation() {
        val reader = widget.displayRotationReader ?: return
        val generation = ++_displayRotationReadGeneration
        try {
            val displayQuarterTurns = reader() ?: 0
            if (!mounted || generation != _displayRotationReadGeneration) return
            val nextQuarterTurns = counterDisplayRotationQuarterTurns(displayQuarterTurns)
            if (_backdropQuarterTurns == nextQuarterTurns) return
            setState { _backdropQuarterTurns = nextQuarterTurns }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Preserve the last valid counter-rotation if the platform is
            // temporarily unavailable during Android's display transition.
        }
    }

    // Dart's didChangeDependencies (orientation) and _ensurePreviewImageData.
    private fun _syncDependencies() {
        val orientation = MediaQuery.orientationOf(context)
        val previousOrientation = _lastAndroidPreviewOrientation
        _lastAndroidPreviewOrientation = orientation
        if (previousOrientation != null && previousOrientation != orientation) {
            _resetImageTransform()
            if (!_closing) {
                _controlsVisible = true
                _scheduleControlsAutoHide()
            }
        }
        _ensurePreviewImageData()
    }

    private fun _ensurePreviewImageData() {
        val cache = widget.actions.mediaCache ?: MediaCacheScope.of(context)
        val key = listOf(widget.imageUrl, widget.suggestedName, System.identityHashCode(cache).toString()).joinToString("\n")
        if (_previewImageDataKey == key) return
        _previewImageDataKey = key
        _previewImageData = future { _loadPreviewImageData(cache) }
        _resetImageTransform()
    }

    private fun _resetImageTransform() {
        _imageTransform = _PreviewImageTransform()
        _gestureStartScale = null
        _gestureStartContentFocal = null
    }

    private fun _scheduleControlsAutoHide() {
        if (!_usesImmersiveControls || _closing) return
        _controlsHideTimer?.cancel()
        _controlsHideTimer =
            _scope?.startTimer(_controlsAutoHideDelay) {
                if (!mounted || _closing || !_controlsVisible) return@startTimer
                setState { _controlsVisible = false }
            }
    }

    private fun _handlePreviewInteraction() {
        if (!_usesImmersiveControls || _closing) return
        if (!_controlsVisible) setState { _controlsVisible = true }
        _scheduleControlsAutoHide()
    }

    private suspend fun _loadPreviewImageData(cache: MediaCacheController): _PreviewImageData {
        val request =
            MediaCacheRequest.tryFromUrl(url = widget.imageUrl, filename = widget.suggestedName)
                ?: throw IllegalStateException("图片地址无效")
        val file = cache.getOrDownload(request = request)
        val size = decodeImageFileSize(file)
        return _PreviewImageData(file = file, size = size)
    }

    private suspend fun _run(action: _PreviewAction, task: suspend () -> Unit, successMessage: String) {
        if (_busy != null) return
        setState { _busy = action }
        try {
            task()
            if (!mounted) return
            showFloatingSuccessNotice(context, successMessage)
        } catch (error: ImagePreviewActionCancelled) {
            // User cancelled (e.g. closed the save-as picker); leave the
            // overlay as it was without showing success or error.
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            showFloatingErrorNotice(context, userFacingErrorMessage(error, fallback = "图片操作失败"))
        } finally {
            if (mounted) setState { _busy = null }
        }
    }

    private fun _close() {
        val scope = _scope ?: return
        scope.launch { _closeAsync() }
    }

    private suspend fun _closeAsync() {
        if (_closing) return
        _closing = true
        _controlsHideTimer?.cancel()

        val orientationController = widget.orientationController
        if (orientationController != null) {
            val restorePortrait = orientationController.restoresPortraitByDefault
            try {
                orientationController.restoreDefaultOrientation()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Keep the opaque preview in place even if the platform request
                // fails; it must never expose a landscape layout from the route
                // underneath.
            }
            if (restorePortrait && mounted && !_isPortraitViewport) {
                val completer = Completer<Unit>()
                _portraitViewportCompleter = completer
                completer.future.await()
            }
            _portraitViewportCompleter = null
        }

        if (!mounted) return
        if (orientationController != null) {
            setState { _routeCanPop = true }
            // Dart's `endOfFrame`: let the PopScope rebuild with canPop.
            withFrameNanos {}
            withFrameNanos {}
            if (!mounted) return
        }
        val popped = Navigator.of(context).maybePop()
        if (mounted && !popped) {
            setState {
                _closing = false
                _routeCanPop = false
                _controlsVisible = true
            }
            _scheduleControlsAutoHide()
        }
    }

    override fun dispose() {
        _displayRotationReadGeneration += 1
        _controlsHideTimer?.cancel()
        val completer = _portraitViewportCompleter
        if (completer != null && !completer.isCompleted) completer.complete()
    }

    @Composable
    override fun build() {
        val scope = rememberCoroutineScope()
        val firstBuild = _scope == null
        _scope = scope
        _syncDependencies()
        if (firstBuild) _scheduleControlsAutoHide()
        val viewportSize = MediaQuery.sizeOf(context)
        LaunchedEffect(viewportSize) {
            if (_lastViewportSize != null && _lastViewportSize != viewportSize) _didChangeMetrics()
            _lastViewportSize = viewportSize
        }
        LaunchedEffect(Unit) { runCatching { _focusRequester.requestFocus() } }

        val showControls = !_closing && (!_usesImmersiveControls || _controlsVisible)
        val content: @Composable () -> Unit = {
            Box(
                Modifier
                    .fillMaxSize()
                    // Escape closes the preview (hardware keyboards).
                    .focusRequester(_focusRequester)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            _close()
                            true
                        } else {
                            false
                        }
                    }
                    .focusable()
                    // Dart's MouseRegion(onHover) + Listener(onPointerDown).
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                when (event.type) {
                                    PointerEventType.Press -> _handlePreviewInteraction()
                                    PointerEventType.Move, PointerEventType.Enter ->
                                        if (event.changes.any { it.type == PointerType.Mouse && !it.pressed }) _handlePreviewInteraction()
                                    else -> Unit
                                }
                            }
                        }
                    },
            ) {
                val backdrop = widget.frozenBackdrop
                if (backdrop != null) {
                    Box(Modifier.matchParentSize()) { _FrozenPortraitBackdrop(snapshot = backdrop, quarterTurns = _backdropQuarterTurns) }
                }
                // Tapping the backdrop (anywhere outside the image/controls)
                // dismisses the overlay.
                Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures(onTap = { _close() }) })
                Box(Modifier.matchParentSize()) { _buildImage() }
                if (_usesImmersiveControls && widget.showActionBar && showControls) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 14.dp)) {
                        SafeArea(top = false) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { _buildActionBar() }
                        }
                    }
                }
                if (showControls) {
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 14.dp)) {
                        SafeArea {
                            ButtonIcon(
                                icon = { Icon(Icons.Rounded.Close) },
                                tooltip = "关闭",
                                onPressed = ::_close,
                                backgroundColor = Color(0x66000000),
                            )
                        }
                    }
                }
            }
        }
        if (widget.orientationController == null) {
            content()
        } else {
            PopScope(
                canPop = _routeCanPop,
                onPopInvokedWithResult = { didPop, _ -> if (!didPop) _close() },
            ) {
                content()
            }
        }
    }

    @Composable
    private fun _buildImage() {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = Size(max(1f, maxWidth.value), max(1f, maxHeight.value))
            FutureBuilder(_previewImageData) { snapshot ->
                val data = snapshot.data
                if (data == null) {
                    _buildImagePlaceholder(error = snapshot.error)
                } else {
                    _buildLoadedImage(viewport, data)
                }
            }
        }
    }

    @Composable
    private fun _buildImagePlaceholder(error: Throwable?) {
        Box(
            Modifier
                .fillMaxSize()
                // Absorbs taps so tapping the placeholder does not close the preview.
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .testTag(chatImagePreviewImageTag),
        ) {
            if (error == null) _previewLoading() else _previewError()
        }
    }

    @Composable
    private fun _buildLoadedImage(viewport: Size, data: _PreviewImageData) {
        val contentRect = _previewContentRect(viewport, data.size)
        val density = LocalDensity.current.density
        val transform = _imageTransform
        // The gesture layer intentionally fills the whole preview area. If it
        // only covered the painted image, a dragged image could move out from
        // under the pointer and become difficult to grab again.
        Box(
            Modifier
                .fillMaxSize()
                .testTag(chatImagePreviewViewerTag)
                .pointerInput(viewport, contentRect) {
                    detectTapGestures(onTap = { position -> _handlePreviewTap(Offset(position.x / density, position.y / density), contentRect) })
                }
                .pointerInput(viewport, contentRect) {
                    val slop = kTouchSlop.dp.toPx()
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var started = false
                        var cumulativeZoom = 1f
                        var travelled = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            val zoom = event.calculateZoom()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val previous = event.calculateCentroid(useCurrent = false)
                            cumulativeZoom *= zoom
                            if (!started) {
                                travelled += centroid - previous
                                if (travelled.getDistance() > slop || pressed.size > 1 || abs(cumulativeZoom - 1f) > 0.01f) {
                                    started = true
                                    _handlePreviewScaleStart(Offset(previous.x / density, previous.y / density))
                                    cumulativeZoom = zoom
                                }
                            }
                            if (started) {
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                _handlePreviewScaleUpdate(Offset(centroid.x / density, centroid.y / density), cumulativeZoom, viewport, contentRect)
                            }
                        }
                        if (started) _handlePreviewScaleEnd(viewport, contentRect)
                    }
                }
                .pointerInput(viewport, contentRect) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            _handlePreviewScroll(delta, viewport, contentRect)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
                .clipToBounds(),
        ) {
            Box(
                Modifier
                    .testTag(chatImagePreviewTransformTag)
                    .requiredSize(viewport.width.dp, viewport.height.dp)
                    .graphicsLayer {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.offset.x * density
                        translationY = transform.offset.y * density
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            ) {
                _previewImage(data, contentRect)
            }
        }
    }

    private fun _handlePreviewTap(@Suppress("UNUSED_PARAMETER") position: Offset, @Suppress("UNUSED_PARAMETER") contentRect: Rect) {
        // Android: a tap on the image area reveals the controls instead of
        // closing (Dart closed on desktop when the tap missed the image).
        _handlePreviewInteraction()
    }

    private fun _handlePreviewScaleStart(localFocalPoint: Offset) {
        val transform = _imageTransform
        _gestureStartScale = transform.scale
        _gestureStartContentFocal = (localFocalPoint - transform.offset) / transform.scale
    }

    private fun _handlePreviewScaleUpdate(localFocalPoint: Offset, scale: Float, viewport: Size, contentRect: Rect) {
        val startScale = _gestureStartScale ?: _imageTransform.scale
        val contentFocal = _gestureStartContentFocal ?: _contentPointFor(localFocalPoint)
        val nextScale = (startScale * scale).coerceIn(_minImageScale, _maxImageScale)
        val nextTranslation = localFocalPoint - contentFocal * nextScale
        _setImageTransform(nextScale, nextTranslation, viewport, contentRect)
    }

    private fun _handlePreviewScaleEnd(viewport: Size, contentRect: Rect) {
        _gestureStartScale = null
        _gestureStartContentFocal = null
        _setImageTransform(_imageTransform.scale, _imageTransform.offset, viewport, contentRect)
    }

    /** [scrollDeltaY]: Compose's wheel delta (notches; positive scrolls down). */
    private fun _handlePreviewScroll(scrollDeltaY: Float, viewport: Size, contentRect: Rect) {
        if (scrollDeltaY == 0f) return
        val scrollSteps = _normalizedWheelSteps(scrollDeltaY)
        val scaleDelta = _wheelScaleBase.pow(-scrollSteps)
        val nextScale = (_imageTransform.scale * scaleDelta).coerceIn(_minImageScale, _maxImageScale)
        val imageCenter = _visibleContentRect(contentRect).center
        val nextTranslation = imageCenter - contentRect.center * nextScale
        _setImageTransform(nextScale, nextTranslation, viewport, contentRect)
    }

    // Dart normalised Flutter's pixel deltas (120 per notch); Compose already
    // reports notches, which Dart's small-delta branch maps to their sign.
    private fun _normalizedWheelSteps(deltaY: Float): Float {
        val magnitude = abs(deltaY)
        if (magnitude == 0f) return 0f
        if (magnitude >= 20f) return deltaY / 120f
        return sign(deltaY)
    }

    private fun _contentPointFor(viewportPoint: Offset): Offset {
        val transform = _imageTransform
        return (viewportPoint - transform.offset) / transform.scale
    }

    private fun _visibleContentRect(contentRect: Rect): Rect {
        val transform = _imageTransform
        val scale = transform.scale
        val offset = transform.offset
        return Rect(
            contentRect.left * scale + offset.x,
            contentRect.top * scale + offset.y,
            contentRect.right * scale + offset.x,
            contentRect.bottom * scale + offset.y,
        )
    }

    private fun _previewContentRect(viewport: Size, imageSize: Size): Rect {
        if (widget.forceSquare) {
            val side = _squarePreviewSide(viewport)
            return Rect(Offset((viewport.width - side) / 2, (viewport.height - side) / 2), Size(side, side))
        }
        if (imageSize.width <= 0f || imageSize.height <= 0f) return Rect(Offset.Zero, viewport)
        return inscribeCentered(applyBoxFitDestination(BoxFit.contain, imageSize, viewport), viewport)
    }

    private fun _setImageTransform(scale: Float, translation: Offset, viewport: Size, contentRect: Rect) {
        val clampedScale = scale.coerceIn(_minImageScale, _maxImageScale)
        val clampedX =
            _clampImageTranslationAxis(viewport.width, contentRect.left, contentRect.right, clampedScale, translation.x)
        val clampedY =
            _clampImageTranslationAxis(viewport.height, contentRect.top, contentRect.bottom, clampedScale, translation.y)
        val next = _PreviewImageTransform(scale = clampedScale, offset = Offset(clampedX, clampedY))
        if (_imageTransform == next) return
        _imageTransform = next
    }

    private fun _clampImageTranslationAxis(
        viewportExtent: Float,
        contentStart: Float,
        contentEnd: Float,
        scale: Float,
        translation: Float,
    ): Float {
        val scaledExtent = (contentEnd - contentStart) * scale
        if (scaledExtent <= viewportExtent) return (viewportExtent - scaledExtent) / 2 - contentStart * scale
        val minTranslation = viewportExtent - contentEnd * scale
        val maxTranslation = -contentStart * scale
        return translation.coerceIn(minTranslation, maxTranslation)
    }

    @Composable
    private fun _previewImage(data: _PreviewImageData, contentRect: Rect) {
        Box(Modifier.fillMaxSize()) {
            FileImage(
                file = data.file,
                modifier = Modifier.offset(contentRect.left.dp, contentRect.top.dp).testTag(chatImagePreviewImageTag),
                width = contentRect.width.dp,
                height = contentRect.height.dp,
                fit = if (widget.forceSquare) BoxFit.cover else BoxFit.contain,
            )
        }
    }

    private fun _squarePreviewSide(viewport: Size): Float {
        val availableSide = min(viewport.width, viewport.height)
        return max(160f, min(360f, availableSide * 0.72f))
    }

    @Composable
    private fun _previewLoading() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = UiColors.textSecondary,
                strokeWidth = 2.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Square,
            )
        }
    }

    @Composable
    private fun _previewError() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.BrokenImage, color = UiColors.textMuted, size = 48.dp)
        }
    }

    @Composable
    private fun _buildActionBar() {
        val busy = _busy
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            ButtonIcon(
                icon = { Icon(Icons.Rounded.Download) },
                tooltip = "下载",
                loading = busy == _PreviewAction.download,
                onPressed =
                    if (busy == null) {
                        {
                            unawaited {
                                _run(
                                    _PreviewAction.download,
                                    { widget.actions.onDownload(widget.imageUrl, widget.suggestedName) },
                                    successMessage = "已保存到下载文件夹",
                                )
                            }
                        }
                    } else {
                        null
                    },
                backgroundColor = Color(0x66000000),
            )
            Spacer(Modifier.width(10.dp))
            ButtonIcon(
                icon = { Icon(Icons.Rounded.SaveAlt) },
                tooltip = "另存为",
                loading = busy == _PreviewAction.saveAs,
                onPressed =
                    if (busy == null) {
                        {
                            unawaited {
                                _run(
                                    _PreviewAction.saveAs,
                                    { widget.actions.onSaveAs(widget.imageUrl, widget.suggestedName) },
                                    successMessage = "已保存",
                                )
                            }
                        }
                    } else {
                        null
                    },
                backgroundColor = Color(0x66000000),
            )
            Spacer(Modifier.width(10.dp))
            ButtonIcon(
                icon = { Icon(Icons.Rounded.ContentCopy) },
                tooltip = "复制到剪贴板",
                loading = busy == _PreviewAction.copy,
                onPressed =
                    if (busy == null) {
                        {
                            unawaited {
                                _run(_PreviewAction.copy, { widget.actions.onCopyToClipboard(widget.imageUrl) }, successMessage = "已复制到剪贴板")
                            }
                        }
                    } else {
                        null
                    },
                backgroundColor = Color(0x66000000),
            )
            if (_canSaveSticker) {
                Spacer(Modifier.width(10.dp))
                ButtonIcon(
                    icon = { Icon(Icons.Outlined.AddReaction) },
                    tooltip = "添加到我的表情包",
                    loading = busy == _PreviewAction.saveSticker,
                    onPressed =
                        if (busy == null) {
                            {
                                val source = widget.stickerSource!!
                                unawaited {
                                    _run(
                                        _PreviewAction.saveSticker,
                                        { widget.actions.onSaveSticker!!(source.message, source.attachment) },
                                        successMessage = "已添加到我的表情包",
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    backgroundColor = Color(0x66000000),
                )
            }
            if (_canSaveRoomSticker) {
                Spacer(Modifier.width(10.dp))
                ButtonIcon(
                    icon = { Icon(Icons.Outlined.LibraryAdd) },
                    tooltip = "添加到房间表情包",
                    loading = busy == _PreviewAction.saveRoomSticker,
                    onPressed =
                        if (busy == null) {
                            {
                                val source = widget.stickerSource!!
                                unawaited {
                                    _run(
                                        _PreviewAction.saveRoomSticker,
                                        { widget.actions.onSaveRoomSticker!!(source.message, source.attachment) },
                                        successMessage = "已添加到房间表情包",
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    backgroundColor = Color(0x66000000),
                )
            }
        }
    }

    companion object {
        private val _controlsAutoHideDelay = 3.seconds
        private const val _minImageScale = 0.25f
        private const val _maxImageScale = 5.0f
        private const val _wheelScaleBase = 1.25f
    }
}

@Composable
private fun _FrozenPortraitBackdrop(snapshot: ImagePreviewBackdropSnapshot, quarterTurns: Int) {
    val normalizedQuarterTurns = quarterTurns.mod(4)
    val logicalSize = snapshot.logicalSize
    val rotatedSize = if (normalizedQuarterTurns % 2 == 1) DpSize(logicalSize.height, logicalSize.width) else logicalSize
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.matchParentSize().clipToBounds(), contentAlignment = Alignment.Center) {
            Box(Modifier.requiredSize(rotatedSize).testTag(chatImagePreviewFrozenBackdropFrameTag)) {
                AnimatedContent(
                    targetState = normalizedQuarterTurns,
                    transitionSpec = {
                        fadeIn(tween(140, easing = com.gangchat.client.widgets.Curves.easeOutCubic)) togetherWith
                            fadeOut(tween(110, easing = com.gangchat.client.widgets.Curves.easeInCubic))
                    },
                    contentAlignment = Alignment.Center,
                    label = "frozenBackdropRotation",
                ) { turns ->
                    // Flutter's RotatedBox: the frame is laid out rotated, the
                    // image keeps its portrait logical size.
                    val frame = if (turns % 2 == 1) DpSize(logicalSize.height, logicalSize.width) else logicalSize
                    Box(
                        Modifier.requiredSize(frame).testTag(chatImagePreviewFrozenBackdropRotationTag(turns)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = snapshot.image,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            filterQuality = FilterQuality.Medium,
                            modifier = Modifier.requiredSize(logicalSize).rotate(turns * 90f).testTag(chatImagePreviewFrozenBackdropTag),
                        )
                    }
                }
            }
        }
        Box(Modifier.matchParentSize().background(Color(0xD1000000)))
    }
}
